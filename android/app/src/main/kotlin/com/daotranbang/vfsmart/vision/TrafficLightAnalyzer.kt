package com.daotranbang.vfsmart.vision

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Combines the two on-device models into one traffic-light [Reading]:
 *
 *  1. [TrafficLightDetector] finds the lit colour (RED/GREEN) and any countdown
 *     boxes on the full frame.
 *  2. If the active light has a countdown box, that box is cropped and handed to
 *     [RedLightDetector] (the digit OCR model) to read the remaining **seconds**.
 *
 * Step 2 is best-effort: if `digits.tflite` isn't bundled yet, [Reading.seconds]
 * is simply null and the rest of the reading is unaffected.
 *
 * All work runs on [Dispatchers.Default]; the caller owns [frame] (never recycled).
 */
object TrafficLightAnalyzer {

    data class Reading(
        val state: TrafficLightDetector.State,
        val confidence: Float,
        /** Remaining seconds from OCR of the active count box, or null if unread. */
        val seconds: Int?,
        /** True if a countdown box was present for the active colour. */
        val hasCount: Boolean,
        /** Detections in original-frame normalised [0,1] coords, for the overlay. */
        val boxes: List<TrafficLightDetector.Box>
    ) {
        companion object {
            val EMPTY = Reading(TrafficLightDetector.State.NONE, 0f, null, false, emptyList())
        }
    }

    // Padding added around a count box before OCR, as a fraction of its size.
    private const val CROP_PAD = 0.15f

    // Set once the digit model (digits.tflite) is found to be absent, so we stop
    // attempting OCR every frame instead of throwing/catching repeatedly.
    @Volatile private var digitModelMissing = false

    suspend fun analyze(context: Context, frame: Bitmap): Reading = withContext(Dispatchers.Default) {
        val det = TrafficLightDetector.detect(context, frame)
        if (det.state == TrafficLightDetector.State.NONE) {
            return@withContext Reading(det.state, det.confidence, null, false, det.boxes)
        }

        // The count box that belongs to the lit colour.
        val countCls = if (det.state == TrafficLightDetector.State.RED)
            CLS_RED_COUNT else CLS_GREEN_COUNT
        val countBox = det.boxes.filter { it.cls == countCls }.maxByOrNull { it.score }

        // Skip OCR entirely once we know the digit model isn't bundled.
        val seconds = if (digitModelMissing) null
            else countBox?.let { ocrCount(context, frame, it) }

        Reading(
            state = det.state,
            confidence = det.confidence,
            seconds = seconds,
            hasCount = countBox != null,
            boxes = det.boxes
        )
    }

    /** Crop [box] (normalised) out of [frame] and OCR the digits inside it. */
    private fun ocrCount(context: Context, frame: Bitmap, box: TrafficLightDetector.Box): Int? {
        val crop = cropNormalized(frame, box.left, box.top, box.right, box.bottom) ?: return null
        return try {
            RedLightDetector.detect(context, crop).seconds
        } catch (e: IllegalStateException) {
            // digits.tflite not bundled — latch the flag so we never retry, and
            // keep the rest of the reading (RED/GREEN state) working.
            digitModelMissing = true
            null
        } finally {
            crop.recycle()
        }
    }

    /** Crop a padded, clamped sub-bitmap from [src] given normalised box edges. */
    private fun cropNormalized(
        src: Bitmap, l: Float, t: Float, r: Float, b: Float
    ): Bitmap? {
        val padX = (r - l) * CROP_PAD
        val padY = (b - t) * CROP_PAD
        val left = ((l - padX) * src.width).roundToInt().coerceIn(0, src.width - 1)
        val top = ((t - padY) * src.height).roundToInt().coerceIn(0, src.height - 1)
        val right = ((r + padX) * src.width).roundToInt().coerceIn(left + 1, src.width)
        val bottom = ((b + padY) * src.height).roundToInt().coerceIn(top + 1, src.height)
        val w = right - left
        val h = bottom - top
        if (w < 8 || h < 8) return null  // too small to read reliably
        return Bitmap.createBitmap(src, left, top, w, h)
    }

    // Class indices mirrored from TrafficLightDetector / data.yaml order.
    private const val CLS_GREEN_COUNT = 1
    private const val CLS_RED_COUNT = 3
}
