package com.daotranbang.vfsmart.ui.components

import android.graphics.Bitmap
import android.view.TextureView
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * Frames are sampled at [fps] (default 3) to keep the GPU/CPU cool. Each reading
 * is also pushed to [onReading] so a ViewModel can expose it as a StateFlow.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun RtspTrafficLightView(
    url: String,
    modifier: Modifier = Modifier,
    fps: Int = 3,
    showBoxes: Boolean = true,
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

    Box(modifier) {
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

/** Box colour by class: lights match their colour, count boxes are dimmer. */
private fun colorForClass(cls: Int): Color = when (cls) {
    0 -> Color(0xFF4CAF50)        // Green
    1 -> Color(0xFF81C784)        // Green count
    2 -> Color(0xFFEF5350)        // Red
    3 -> Color(0xFFE57373)        // Red count
    else -> Color.Yellow
}
