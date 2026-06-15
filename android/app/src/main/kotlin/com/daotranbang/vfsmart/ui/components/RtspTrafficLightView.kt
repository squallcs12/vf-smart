package com.daotranbang.vfsmart.ui.components

import android.graphics.Bitmap
import android.view.TextureView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import com.daotranbang.vfsmart.vision.TrafficLightAnalyzer
import com.daotranbang.vfsmart.vision.TrafficLightDetector
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// Frame size pulled off the TextureView for inference (16:9, small = fast).
private const val ANALYSIS_W = 640
private const val ANALYSIS_H = 360

/**
 * Live RTSP surface (Media3 ExoPlayer on a [TextureView]) that runs the custom
 * [TrafficLightDetector] / [TrafficLightAnalyzer] on sampled frames and draws an
 * overlay: bounding boxes plus a RED/GREEN state badge with the OCR'd seconds.
 *
 * Frames are sampled at [fps] (default 1, matching the camera's stream rate). Each reading
 * is also pushed to [onReading] so a ViewModel can expose it as a StateFlow.
 *
 * When [cropToLight] is true, the surface zooms onto the detected traffic light (the
 * union of the light + countdown boxes, with a little context padding) so only the
 * light fills the view instead of the whole frame. The zoom animates smoothly and
 * falls back to the full frame whenever nothing is detected.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun RtspTrafficLightView(
    url: String,
    modifier: Modifier = Modifier,
    fps: Int = 1,
    showBoxes: Boolean = true,
    cropToLight: Boolean = false,
    onReading: (TrafficLightAnalyzer.Reading) -> Unit = {}
) {
    val context = LocalContext.current
    val latestOnReading by rememberUpdatedState(onReading)

    var reading by remember { mutableStateOf(TrafficLightAnalyzer.Reading.EMPTY) }
    val textureView = remember { TextureView(context) }

    val exoPlayer = remember(url) {
        val mediaSource = RtspMediaSource.Factory()
            .setForceUseRtpTcp(true)
            .createMediaSource(MediaItem.fromUri(url))
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(mediaSource)
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            setVideoTextureView(textureView)
            prepare()
        }
    }
    DisposableEffect(url) {
        onDispose { exoPlayer.release() }
    }

    // Sampling loop: grab a frame, analyse it, surface the reading.
    LaunchedEffect(url, fps) {
        val intervalMs = (1000L / fps.coerceAtLeast(1))
        val buffer = Bitmap.createBitmap(ANALYSIS_W, ANALYSIS_H, Bitmap.Config.ARGB_8888)
        while (isActive) {
            if (textureView.isAvailable) {
                // getBitmap fills our reusable buffer on the UI thread; analyse() then
                // copies pixels off it, so reuse is safe once analyse() returns.
                val frame = runCatching { textureView.getBitmap(buffer) }.getOrNull()
                if (frame != null) {
                    val r = runCatching { TrafficLightAnalyzer.analyze(context, frame) }.getOrNull()
                    if (r != null) {
                        reading = r
                        latestOnReading(r)
                    }
                }
            }
            delay(intervalMs)
        }
    }

    // Target crop rect (normalised, full-frame when no detection / cropping off).
    val targetCropRect = if (cropToLight) cropRectFor(reading) else FULL_FRAME
    // Animate each edge so 1 fps detection jitter doesn't snap the zoom around.
    val cropLeftFraction by animateFloatAsState(targetCropRect.left, label = "cropLeft")
    val cropTopFraction by animateFloatAsState(targetCropRect.top, label = "cropTop")
    val cropRightFraction by animateFloatAsState(targetCropRect.right, label = "cropRight")
    val cropBottomFraction by animateFloatAsState(targetCropRect.bottom, label = "cropBottom")

    Box(modifier) {
        // Inner content carries the zoom transform; the badge below stays unzoomed.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clipToBounds()
                .graphicsLayer {
                    val cropWidthFraction = (cropRightFraction - cropLeftFraction).coerceAtLeast(0.001f)
                    val cropHeightFraction = (cropBottomFraction - cropTopFraction).coerceAtLeast(0.001f)
                    val cropCenterXFraction = (cropLeftFraction + cropRightFraction) / 2f
                    val cropCenterYFraction = (cropTopFraction + cropBottomFraction) / 2f
                    // Uniform scale so the whole crop fits the view (letterboxed on black).
                    val zoomScale = minOf(1f / cropWidthFraction, 1f / cropHeightFraction)
                    transformOrigin = TransformOrigin(cropCenterXFraction, cropCenterYFraction)
                    scaleX = zoomScale
                    scaleY = zoomScale
                    // The pivot pixel stays put under scaling — slide it to the centre.
                    translationX = (0.5f - cropCenterXFraction) * size.width
                    translationY = (0.5f - cropCenterYFraction) * size.height
                }
        ) {
            AndroidView(
                modifier = Modifier.matchParentSize(),
                factory = { textureView }
            )

            if (showBoxes) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    for (b in reading.boxes) {
                        val color = colorForClass(b.cls)
                        drawRect(
                            color = color,
                            topLeft = Offset(b.left * size.width, b.top * size.height),
                            size = Size(
                                (b.right - b.left) * size.width,
                                (b.bottom - b.top) * size.height
                            ),
                            style = Stroke(width = 3f)
                        )
                    }
                }
            }
        }

        StateBadge(
            reading = reading,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        )
    }
}

@Composable
private fun StateBadge(reading: TrafficLightAnalyzer.Reading, modifier: Modifier = Modifier) {
    val (label, color) = when (reading.state) {
        TrafficLightDetector.State.RED -> "ĐỎ" to Color(0xFFEF5350)
        TrafficLightDetector.State.GREEN -> "XANH" to Color(0xFF4CAF50)
        TrafficLightDetector.State.NONE -> return
    }
    val text = reading.seconds?.let { "$label  ${it}s" } ?: label
    Text(
        text = text,
        color = Color.White,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

// The whole frame — used when cropping is off or nothing is detected.
private val FULL_FRAME = Rect(0f, 0f, 1f, 1f)

// Context padding added around the detected light, as a fraction of its size.
private const val CROP_PAD = 0.35f

// Detection class indices, mirrored from TrafficLightDetector / data.yaml order.
private const val CLASS_GREEN_LIGHT = 0
private const val CLASS_GREEN_COUNT = 1
private const val CLASS_RED_LIGHT = 2
private const val CLASS_RED_COUNT = 3

/**
 * Bounding rect (normalised) to zoom onto, chosen by priority:
 * red-with-countdown → red → green-with-countdown → green. Within each colour the
 * highest-scoring light box wins, and its matching countdown box is folded into the
 * crop so the number stays visible. Returns [FULL_FRAME] when there's nothing to
 * zoom onto so the view shows the plain stream.
 */
private fun cropRectFor(reading: TrafficLightAnalyzer.Reading): Rect {
    val boxes = reading.boxes
    if (boxes.isEmpty()) return FULL_FRAME

    fun bestOf(targetClass: Int) =
        boxes.filter { it.cls == targetClass }.maxByOrNull { it.score }

    val bestRedLight = bestOf(CLASS_RED_LIGHT)
    val bestRedCount = bestOf(CLASS_RED_COUNT)
    val bestGreenLight = bestOf(CLASS_GREEN_LIGHT)
    val bestGreenCount = bestOf(CLASS_GREEN_COUNT)

    val chosenBoxes = when {
        bestRedLight != null && bestRedCount != null -> listOf(bestRedLight, bestRedCount)
        bestRedLight != null -> listOf(bestRedLight)
        bestGreenLight != null && bestGreenCount != null -> listOf(bestGreenLight, bestGreenCount)
        bestGreenLight != null -> listOf(bestGreenLight)
        else -> return FULL_FRAME
    }

    var unionLeft = 1f; var unionTop = 1f; var unionRight = 0f; var unionBottom = 0f
    for (box in chosenBoxes) {
        unionLeft = minOf(unionLeft, box.left); unionTop = minOf(unionTop, box.top)
        unionRight = maxOf(unionRight, box.right); unionBottom = maxOf(unionBottom, box.bottom)
    }
    if (unionRight <= unionLeft || unionBottom <= unionTop) return FULL_FRAME

    val horizontalPadding = (unionRight - unionLeft) * CROP_PAD
    val verticalPadding = (unionBottom - unionTop) * CROP_PAD
    return Rect(
        (unionLeft - horizontalPadding).coerceIn(0f, 1f),
        (unionTop - verticalPadding).coerceIn(0f, 1f),
        (unionRight + horizontalPadding).coerceIn(0f, 1f),
        (unionBottom + verticalPadding).coerceIn(0f, 1f)
    )
}

/** Box colour by class: lights match their colour, count boxes are dimmer. */
private fun colorForClass(cls: Int): Color = when (cls) {
    0 -> Color(0xFF4CAF50)        // Green
    1 -> Color(0xFF81C784)        // Green count
    2 -> Color(0xFFEF5350)        // Red
    3 -> Color(0xFFE57373)        // Red count
    else -> Color.Yellow
}
