package com.daotranbang.vfsmart.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * On-device traffic-light state reader backed by a **custom YOLO model**
 * (Ultralytics YOLOv11) exported to TensorFlow Lite.
 *
 * The model has four classes, in this exact `data.yaml` order:
 *
 * | Index | Label         | Meaning                                |
 * |-------|---------------|----------------------------------------|
 * | 0     | `Green`       | the green light is lit                 |
 * | 1     | `Green count` | a green countdown number is displayed  |
 * | 2     | `Red`         | the red light is lit                   |
 * | 3     | `Red count`   | a red countdown number is displayed    |
 *
 * Although the model can see green, this reader **only cares about red**: it
 * reports whether the red light is lit ([State.RED] vs [State.NONE]) plus whether
 * a red countdown box is present. Green detections are dropped. It does **not**
 * read the countdown digits — that would need a second OCR pass on the count box
 * (see [RedLightDetector]).
 *
 * Designed to run on live RTSP frames: [detect] takes a [Bitmap] and is fully offline.
 * It runs the model once **per tile** ([TILE_ROWS]×[TILE_COLS] = 16 inferences per
 * frame); to keep that fast the tiles are inferred **in parallel** across a pool of
 * CPU interpreters (see [POOL_SIZE]). The caller should still sample frames slowly
 * rather than analysing every frame.
 *
 * ## Wiring the model
 *  - Trained on the Roboflow "vietnam-traffic-light-f0zoa" YOLOv11 export
 *    (v2: 4×4 tiling), so inference tiles to match — see [TILE_ROWS]/[TILE_COLS].
 *  - `yolo export model=best.pt format=tflite half=True imgsz=640`
 *  - Dropped at `app/src/main/assets/traffic_light.tflite`.
 */
object TrafficLightDetector {

    private const val MODEL_ASSET = "traffic_light.tflite"
    private const val NUM_CLASSES = 4
    private const val CONF_THRESHOLD = 0.40f
    private const val IOU_THRESHOLD = 0.45f

    // The model is trained on a 4×4 tiling of the source frames (Roboflow v2), so
    // inference must tile the same way: a far/small light is ~11 px in a whole-frame
    // 640 downscale (undetectable) but ~4× larger inside a 1/4×1/4 tile. Each tile is
    // run through the model and its boxes are mapped back to full-frame coordinates.
    private const val TILE_ROWS = 4
    private const val TILE_COLS = 4

    // Class indices — must match data.yaml order. (Green classes 0/1 exist in the
    // model but are intentionally ignored; only red is reported.)
    private const val CLS_RED = 2
    private const val CLS_RED_COUNT = 3

    enum class State { RED, NONE }

    data class Box(
        val left: Float, val top: Float, val right: Float, val bottom: Float,
        val score: Float, val cls: Int
    )

    data class Result(
        /** [State.RED] if the red light is lit, else [State.NONE]. */
        val state: State,
        /** Confidence of the red-light box that decided [state] (0 when NONE). */
        val confidence: Float,
        /** A red countdown number box is visible. */
        val hasRedCount: Boolean,
        /** Kept red detections only (normalised 0..1 coords), e.g. to draw an overlay. */
        val boxes: List<Box>
    ) {
        companion object {
            val EMPTY = Result(State.NONE, 0f, false, emptyList())
        }
    }

    // Parallelism for tiled inference. Interpreter.run() is not thread-safe, so each
    // concurrent worker borrows its own interpreter from [pool]. Sized to the device's
    // cores (capped to bound memory — each interpreter holds its own tensor arena).
    private val POOL_SIZE = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)

    @Volatile private var interpreters: List<Interpreter>? = null
    private val pool = LinkedBlockingQueue<Interpreter>()
    @Volatile private var executor: ExecutorService? = null
    private val initLock = Any()

    /** True once the model has been loaded (so callers can show a "no model" hint). */
    val isLoaded: Boolean get() = interpreters != null

    /**
     * Run detection on [frame] by splitting it into a [TILE_ROWS]×[TILE_COLS] grid,
     * detecting in each tile, and merging the results in full-frame coordinates.
     * Caller owns [frame] (we never recycle it).
     * Throws [IllegalStateException] if the model asset is missing.
     */
    fun detect(context: Context, frame: Bitmap): Result {
        ensureReady(context.applicationContext)

        val tileW = frame.width / TILE_COLS
        val tileH = frame.height / TILE_ROWS
        // Frame too small to tile meaningfully — fall back to whole-frame detection.
        if (tileW < 1 || tileH < 1) {
            val interp = pool.take()
            try {
                return summarise(nms(detectBoxesIn(interp, frame)))
            } finally {
                pool.put(interp)
            }
        }

        // Pre-crop all tiles on this thread (cheap pixel copies, no concurrent reads of
        // [frame]); then infer them in parallel, each worker borrowing an interpreter.
        val tasks = ArrayList<Callable<List<Box>>>(TILE_ROWS * TILE_COLS)
        for (ry in 0 until TILE_ROWS) {
            for (cx in 0 until TILE_COLS) {
                val left = cx * tileW
                val top = ry * tileH
                // The last row/column extends to the edge so no remainder pixels are lost.
                val w = if (cx == TILE_COLS - 1) frame.width - left else tileW
                val h = if (ry == TILE_ROWS - 1) frame.height - top else tileH
                val tile = Bitmap.createBitmap(frame, left, top, w, h)

                // Tile→frame mapping for this tile's normalised boxes.
                val ox = left.toFloat() / frame.width
                val oy = top.toFloat() / frame.height
                val fw = w.toFloat() / frame.width
                val fh = h.toFloat() / frame.height

                tasks.add(Callable {
                    val interp = pool.take()
                    val boxes = try {
                        detectBoxesIn(interp, tile)
                    } finally {
                        pool.put(interp)
                        tile.recycle()
                    }
                    boxes.map { b ->
                        b.copy(
                            left = ox + b.left * fw,
                            top = oy + b.top * fh,
                            right = ox + b.right * fw,
                            bottom = oy + b.bottom * fh
                        )
                    }
                })
            }
        }

        val all = ArrayList<Box>()
        for (future in executor!!.invokeAll(tasks)) all.addAll(future.get())
        // Global NMS removes duplicates of the same light found in adjacent tiles.
        return summarise(nms(all))
    }

    /** Release the interpreter pool and worker threads. Call when detection stops. */
    fun close() {
        synchronized(initLock) {
            executor?.shutdownNow()
            executor = null
            interpreters?.forEach { it.close() }
            interpreters = null
            pool.clear()
        }
    }

    // --- Interpreter lifecycle -------------------------------------------------

    /**
     * Lazily build the interpreter [pool] and worker [executor]. All [POOL_SIZE]
     * interpreters share one read-only model buffer and run on CPU (XNNPACK) so the
     * 16 tiles can be inferred in parallel — a single GPU delegate would serialise
     * them, and multiple GPU delegates are memory-heavy and unstable.
     */
    private fun ensureReady(context: Context) {
        if (interpreters != null) return
        synchronized(initLock) {
            if (interpreters != null) return
            val model = try {
                loadModelFile(context, MODEL_ASSET)
            } catch (e: IOException) {
                throw IllegalStateException("Chưa có model '$MODEL_ASSET' trong assets/")
            }
            val built = ArrayList<Interpreter>(POOL_SIZE)
            repeat(POOL_SIZE) {
                // 2 threads/interpreter × POOL_SIZE workers ≈ one thread per core.
                val interp = Interpreter(model, Interpreter.Options().setNumThreads(2))
                built.add(interp)
                pool.put(interp)
            }
            executor = Executors.newFixedThreadPool(POOL_SIZE)
            interpreters = built
        }
    }

    // --- Inference -------------------------------------------------------------

    /**
     * Run the model on a single [bitmap] (a whole frame or one tile) and return the
     * decoded boxes in that bitmap's normalised [0,1] coords. No NMS or summarise —
     * the caller merges across tiles and applies global NMS.
     */
    private fun detectBoxesIn(interp: Interpreter, bitmap: Bitmap): List<Box> {
        val inT = interp.getInputTensor(0)
        val inShape = inT.shape()            // [1, H, W, 3]
        val h = inShape[1]
        val w = inShape[2]
        val inQ = inT.quantizationParams()
        val input = preprocess(bitmap, w, h, inT.dataType(), inQ.scale, inQ.zeroPoint)

        val outT = interp.getOutputTensor(0)
        val outShape = outT.shape()          // [1, 4+NC, anchors] or [1, anchors, 4+NC]
        val outType = outT.dataType()
        val outQ = outT.quantizationParams()
        val outElems = outShape.fold(1) { acc, d -> acc * d }
        val outBuf = ByteBuffer
            .allocateDirect(outElems * outType.byteSize())
            .order(ByteOrder.nativeOrder())

        interp.run(input, outBuf)

        val flat = readOutput(outBuf, outType, outQ.scale, outQ.zeroPoint, outElems)
        // Boxes come out in letterboxed-model space; map them back to [bitmap]'s
        // own normalised coords (the caller then maps tile→frame and runs NMS).
        return decode(flat, outShape).map { unletterbox(it, bitmap.width, bitmap.height, w, h) }
    }

    /**
     * Convert a box in [0,1] letterboxed-model space back to [0,1] coordinates of
     * the original [srcW]×[srcH] frame, undoing the centred grey padding.
     */
    private fun unletterbox(b: Box, srcW: Int, srcH: Int, modelW: Int, modelH: Int): Box {
        val ratio = min(modelW.toFloat() / srcW, modelH.toFloat() / srcH)
        val newW = srcW * ratio
        val newH = srcH * ratio
        val padX = (modelW - newW) / 2f
        val padY = (modelH - newH) / 2f
        fun fx(x: Float) = ((x * modelW - padX) / newW).coerceIn(0f, 1f)
        fun fy(y: Float) = ((y * modelH - padY) / newH).coerceIn(0f, 1f)
        return b.copy(left = fx(b.left), top = fy(b.top), right = fx(b.right), bottom = fy(b.bottom))
    }

    /** Collapse the kept boxes into a single traffic-light reading (red only). */
    private fun summarise(kept: List<Box>): Result {
        if (kept.isEmpty()) return Result.EMPTY

        // Green is ignored entirely — keep only red light / red countdown boxes.
        val redBoxes = kept.filter { it.cls == CLS_RED || it.cls == CLS_RED_COUNT }
        // The lit light = the highest-scoring red box across the frame.
        val light = redBoxes.filter { it.cls == CLS_RED }.maxByOrNull { it.score }

        return Result(
            state = if (light != null) State.RED else State.NONE,
            confidence = light?.score ?: 0f,
            hasRedCount = redBoxes.any { it.cls == CLS_RED_COUNT },
            boxes = redBoxes
        )
    }

    /** Letterbox to the model's input size and pack into the input tensor buffer. */
    private fun preprocess(
        src: Bitmap, w: Int, h: Int, type: DataType, scale: Float, zeroPoint: Int
    ): ByteBuffer {
        val ratio = min(w.toFloat() / src.width, h.toFloat() / src.height)
        val newW = (src.width * ratio).roundToInt().coerceAtLeast(1)
        val newH = (src.height * ratio).roundToInt().coerceAtLeast(1)
        val resized = Bitmap.createScaledBitmap(src, newW, newH, true)

        val canvasBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(canvasBmp).apply {
            drawColor(Color.rgb(114, 114, 114))  // YOLO grey padding
            drawBitmap(resized, (w - newW) / 2f, (h - newH) / 2f, null)
        }
        if (resized != src) resized.recycle()

        val bytesPerChannel = if (type == DataType.FLOAT32) 4 else 1
        val buf = ByteBuffer
            .allocateDirect(w * h * 3 * bytesPerChannel)
            .order(ByteOrder.nativeOrder())

        val pixels = IntArray(w * h)
        canvasBmp.getPixels(pixels, 0, w, 0, 0, w, h)
        canvasBmp.recycle()

        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (type == DataType.FLOAT32) {
                buf.putFloat(r / 255f)
                buf.putFloat(g / 255f)
                buf.putFloat(b / 255f)
            } else {
                buf.put(quantize(r / 255f, scale, zeroPoint, type))
                buf.put(quantize(g / 255f, scale, zeroPoint, type))
                buf.put(quantize(b / 255f, scale, zeroPoint, type))
            }
        }
        buf.rewind()
        return buf
    }

    private fun quantize(v: Float, scale: Float, zeroPoint: Int, type: DataType): Byte {
        val q = (v / scale).roundToInt() + zeroPoint
        val clamped = if (type == DataType.UINT8) q.coerceIn(0, 255) else q.coerceIn(-128, 127)
        return clamped.toByte()
    }

    private fun readOutput(
        buf: ByteBuffer, type: DataType, scale: Float, zeroPoint: Int, n: Int
    ): FloatArray {
        buf.rewind()
        val out = FloatArray(n)
        when (type) {
            DataType.FLOAT32 -> for (i in 0 until n) out[i] = buf.float
            DataType.UINT8 -> for (i in 0 until n) out[i] = ((buf.get().toInt() and 0xFF) - zeroPoint) * scale
            DataType.INT8 -> for (i in 0 until n) out[i] = (buf.get().toInt() - zeroPoint) * scale
            else -> throw IllegalStateException("Unsupported output type $type")
        }
        return out
    }

    /** Decode raw YOLO output (anchor-free, no objectness) into detections. */
    private fun decode(flat: FloatArray, shape: IntArray): List<Box> {
        val attrs = 4 + NUM_CLASSES
        val transposed: Boolean
        val anchors: Int
        when (attrs) {
            shape[1] -> { transposed = true; anchors = shape[2] }   // [1, attrs, anchors]
            shape[2] -> { transposed = false; anchors = shape[1] }  // [1, anchors, attrs]
            else -> throw IllegalStateException("Unexpected output shape ${shape.joinToString()}")
        }

        fun at(anchor: Int, attr: Int): Float =
            if (transposed) flat[attr * anchors + anchor] else flat[anchor * attrs + attr]

        val dets = ArrayList<Box>()
        for (i in 0 until anchors) {
            var bestCls = 0
            var bestScore = at(i, 4)
            for (c in 1 until NUM_CLASSES) {
                val s = at(i, 4 + c)
                if (s > bestScore) { bestScore = s; bestCls = c }
            }
            if (bestScore < CONF_THRESHOLD) continue

            // Ultralytics TFLite boxes are normalised cx,cy,w,h in [0,1].
            val cx = at(i, 0); val cy = at(i, 1)
            val bw = at(i, 2); val bh = at(i, 3)
            dets.add(Box(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2, bestScore, bestCls))
        }
        return dets
    }

    /** Greedy class-aware non-maximum suppression (suppress within the same class). */
    private fun nms(dets: List<Box>): List<Box> {
        val pool = dets.sortedByDescending { it.score }.toMutableList()
        val keep = ArrayList<Box>()
        while (pool.isNotEmpty()) {
            val best = pool.removeAt(0)
            keep.add(best)
            val it = pool.iterator()
            while (it.hasNext()) {
                val other = it.next()
                if (other.cls == best.cls && iou(best, other) > IOU_THRESHOLD) it.remove()
            }
        }
        return keep
    }

    private fun iou(a: Box, b: Box): Float {
        val x1 = max(a.left, b.left)
        val y1 = max(a.top, b.top)
        val x2 = min(a.right, b.right)
        val y2 = min(a.bottom, b.bottom)
        val inter = max(0f, x2 - x1) * max(0f, y2 - y1)
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        val union = areaA + areaB - inter
        return if (union <= 0f) 0f else inter / union
    }

    // --- IO helpers ------------------------------------------------------------

    private fun loadModelFile(context: Context, asset: String): MappedByteBuffer {
        context.assets.openFd(asset).use { fd ->
            FileInputStream(fd.fileDescriptor).use { input ->
                return input.channel.map(
                    FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength
                )
            }
        }
    }
}
