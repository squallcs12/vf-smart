package com.daotranbang.vfsmart.vision

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * On-device OCR that reads the countdown number shown on a traffic light
 * ("đèn đỏ") from a still image.
 *
 * Uses ML Kit's bundled Latin text recognizer — runs fully offline, needs no
 * API key, and works on the head unit without network access.
 *
 * Heuristic: a traffic-light countdown is a 1–3 digit number rendered large.
 * Among all purely-numeric text elements we pick the one with the biggest
 * bounding box (the prominent display on the lamp), and treat its value as the
 * remaining seconds. All detected numbers are returned too, for transparency.
 */
object RedLightDetector {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    data class Result(
        /** Best guess at the seconds shown on the light, or null if none found. */
        val seconds: Int?,
        /** Every distinct number the OCR read, ordered by display size desc. */
        val allNumbers: List<Int>,
        /** Raw recognized text (for debugging / display). */
        val rawText: String
    )

    /**
     * Recognise the countdown in [uri]. Suspends until ML Kit finishes.
     * [InputImage.fromFilePath] applies EXIF rotation so portrait photos work.
     */
    suspend fun detect(context: Context, uri: Uri): Result {
        val image = InputImage.fromFilePath(context, uri)
        val text = recognizer.processAwait(image)
        return parse(text)
    }

    private suspend fun com.google.mlkit.vision.text.TextRecognizer.processAwait(
        image: InputImage
    ): Text = suspendCancellableCoroutine { cont ->
        process(image)
            .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    }

    private data class Candidate(val value: Int, val area: Int)

    private fun parse(text: Text): Result {
        val candidates = mutableListOf<Candidate>()
        for (block in text.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val raw = element.text.trim()
                    // Pure 1–3 digit token, e.g. "58", "9", "13".
                    if (raw.length in 1..3 && raw.all { it.isDigit() }) {
                        val box = element.boundingBox
                        val area = if (box != null) box.width() * box.height() else 0
                        candidates.add(Candidate(raw.toInt(), area))
                    }
                }
            }
        }

        val ordered = candidates.sortedByDescending { it.area }
        return Result(
            seconds = ordered.firstOrNull()?.value,
            allNumbers = ordered.map { it.value }.distinct(),
            rawText = text.text
        )
    }
}
