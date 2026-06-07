package com.daotranbang.vfsmart.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * On-device traffic-light countdown reader backed by a **custom YOLO model**
 * (Ultralytics YOLOv8/v11) exported to TensorFlow Lite.
 *
 * The model detects individual digits (classes "0".."9"). We run it on the
 * still image, keep the confident boxes, sort them left-to-right, and
 * concatenate them into the remaining-seconds number — fully offline, no API.
 *
 * ## Wiring your trained model
 *  1. Train on a Roboflow "YOLOv11 PyTorch TXT" digit dataset.
 *  2. `yolo export model=best.pt format=tflite int8=True imgsz=320`
 *  3. Drop the result at `app/src/main/assets/digits.tflite`.
 *
 * Both float32 and int8/uint8-quantized model I/O are handled automatically.
 * Class index → digit mapping assumes the `data.yaml` order is 0,1,…,9
 * (the default when classes are named "0".."9").
 */
object RedLightDetector {

    private const val MODEL_ASSET = "digits.tflite"
    private const val NUM_CLASSES = 10
    private const val CONF_THRESHOLD = 0.35f
    private const val IOU_THRESHOLD = 0.45f

    data class Result(
        /** Best guess at the seconds shown on the light, or null if none found. */
        val seconds: Int?,
        /** Every distinct number read (here: the single assembled value). */
        val allNumbers: List<Int>,
        /** Raw digit sequence, for debugging / display. */
        val rawText: String
    )

    private data class Det(
        val left: Float, val top: Float, val right: Float, val bottom: Float,
        val score: Float, val cls: Int
    )

    @Volatile private var interpreter: Interpreter? = null
    @Volatile private var gpuDelegate: GpuDelegate? = null
    private val initLock = Any()

    /** Recognise the countdown in [uri]. Runs off the main thread. */
    suspend fun detect(context: Context, uri: Uri): Result = withContext(Dispatchers.Default) {
        val interp = obtainInterpreter(context.applicationContext)
        val bitmap = withContext(Dispatchers.IO) { loadBitmap(context, uri) }
        runDetection(interp, bitmap)
    }

    // --- Interpreter lifecycle -------------------------------------------------

    private fun obtainInterpreter(context: Context): Interpreter {
        interpreter?.let { return it }
        return synchronized(initLock) {
            interpreter?.let { return it }
            val model = try {
                loadModelFile(context, MODEL_ASSET)
            } catch (e: IOException) {
                throw IllegalStateException("Chưa có model '$MODEL_ASSET' trong assets/")
            }
            val interp = buildInterpreter(model)
            interpreter = interp
            interp
        }
    }

    /** Try GPU (fast on the S20+'s Mali/NPU), fall back to multi-threaded CPU. */
    private fun buildInterpreter(model: MappedByteBuffer): Interpreter {
        try {
            val delegate = GpuDelegate()
            val interp = Interpreter(model, Interpreter.Options().addDelegate(delegate))
            gpuDelegate = delegate
            return interp
        } catch (t: Throwable) {
            gpuDelegate?.close()
            gpuDelegate = null
        }
        return Interpreter(model, Interpreter.Options().setNumThreads(4))
    }

    // --- Inference -------------------------------------------------------------

    private fun runDetection(interp: Interpreter, bitmap: Bitmap): Result {
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
        val kept = nms(decode(flat, outShape))

        // Left-to-right ordering yields the human-readable number.
        val digits = kept.sortedBy { it.left }
            .joinToString("") { it.cls.toString() }
        val seconds = digits.toIntOrNull()

        return Result(
            seconds = seconds,
            allNumbers = if (seconds != null) listOf(seconds) else emptyList(),
            rawText = digits
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
    private fun decode(flat: FloatArray, shape: IntArray): List<Det> {
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

        val dets = ArrayList<Det>()
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
            dets.add(Det(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2, bestScore, bestCls))
        }
        return dets
    }

    /** Greedy class-agnostic non-maximum suppression. */
    private fun nms(dets: List<Det>): List<Det> {
        val pool = dets.sortedByDescending { it.score }.toMutableList()
        val keep = ArrayList<Det>()
        while (pool.isNotEmpty()) {
            val best = pool.removeAt(0)
            keep.add(best)
            val it = pool.iterator()
            while (it.hasNext()) {
                if (iou(best, it.next()) > IOU_THRESHOLD) it.remove()
            }
        }
        return keep
    }

    private fun iou(a: Det, b: Det): Float {
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

    /** Decode a content [uri] into a software ARGB Bitmap (EXIF rotation applied). */
    private fun loadBitmap(context: Context, uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = false
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri).use { input ->
                BitmapFactory.decodeStream(input)
                    ?: throw IllegalStateException("Cannot decode image")
            }
        }
    }
}
