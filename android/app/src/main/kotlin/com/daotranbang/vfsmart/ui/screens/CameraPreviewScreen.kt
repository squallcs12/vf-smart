package com.daotranbang.vfsmart.ui.screens

import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.daotranbang.vfsmart.R
import com.daotranbang.vfsmart.camera.AudioSwb
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong

private val CamBg      = Color(0xFF0A0A0A)
private val CamLabel   = Color(0xFF4A4A4A)
private val CamAlert   = Color(0xFFEF5350)
private val CamGood    = Color(0xFF4CAF50)
private val CamNormal  = Color(0xFFD0D0D0)

// PAL → NTSC → safe fallback
private val PROBE_SIZES = listOf(720 to 576, 720 to 480, 640 to 480)

@Composable
fun CameraPreviewScreen(onNavigateBack: () -> Unit) {
    var formatLabel  by remember { mutableStateOf("") }
    var hasSignal    by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Updated atomically from the camera callback thread, polled by a coroutine
    val lastFrameMillis = remember { AtomicLong(0L) }

    // Signal watchdog — checks every second, marks lost after 3 s without a frame
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            val t = lastFrameMillis.get()
            hasSignal = t > 0L && System.currentTimeMillis() - t < 3_000
        }
    }

    // Hold a stable reference to the camera so we can release it on exit
    val cameraHolder = remember { mutableStateOf<Camera?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            cameraHolder.value?.apply { stopPreview(); release() }
            cameraHolder.value = null
            AudioSwb.disable()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CamBg)
    ) {
        // ── Camera preview ────────────────────────────────────────────────
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {

                        override fun onSurfaceTextureAvailable(
                            surface: SurfaceTexture, w: Int, h: Int
                        ) {
                            try {
                                // Enable LCDC AV-IN hardware routing — this is what tells
                                // the display controller to decode CVBS into our surface.
                                // The system AUX app does this via ioctl 0x5404 on /dev/AUDIOSWB.
                                AudioSwb.enable()

                                val cam = Camera.open(0)
                                cameraHolder.value = cam

                                val params = cam.parameters
                                val supported = params.supportedPreviewSizes
                                    ?.map { it.width to it.height }.orEmpty()

                                val chosen = PROBE_SIZES.firstOrNull { it in supported }
                                    ?: supported.firstOrNull()
                                    ?: (640 to 480)

                                params.setPreviewSize(chosen.first, chosen.second)

                                // Tell the CVBS decoder to lock to PAL (50 Hz).
                                // On Rockchip CVBS camera drivers the antibanding value
                                // maps to the TV decoder's line standard:
                                //   50hz → PAL (Vietnam / most of Asia)
                                //   60hz → NTSC
                                val supportedAb = params.supportedAntibanding ?: emptyList()
                                when {
                                    "50hz" in supportedAb -> params.antibanding = "50hz"
                                    "auto" in supportedAb -> params.antibanding = "auto"
                                }

                                cam.parameters = params
                                cam.setPreviewTexture(surface)
                                cam.startPreview()

                                val abLabel = when (params.antibanding) {
                                    "50hz" -> "PAL/50Hz"
                                    "60hz" -> "NTSC/60Hz"
                                    else   -> "AUTO"
                                }
                                formatLabel = when {
                                    chosen.second == 576 -> "PAL  720×576"
                                    chosen.second == 480 && chosen.first == 720 -> "NTSC  720×480  $abLabel"
                                    else -> "${chosen.first}×${chosen.second}  $abLabel"
                                }
                                errorMessage = null
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "camera open failed"
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(
                            surface: SurfaceTexture, w: Int, h: Int
                        ) {}

                        override fun onSurfaceTextureDestroyed(
                            surface: SurfaceTexture
                        ): Boolean {
                            cameraHolder.value?.apply { stopPreview(); release() }
                            cameraHolder.value = null
                            AudioSwb.disable()
                            return true
                        }

                        // Called every time the camera delivers a new frame to the surface
                        // — no buffer allocation, just stamp the time
                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                            lastFrameMillis.set(System.currentTimeMillis())
                        }
                    }
                }
            }
        )

        // ── No-signal overlay ─────────────────────────────────────────────
        if (!hasSignal && errorMessage == null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.camera_no_signal),
                    color = CamAlert,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )
                if (formatLabel.isNotEmpty()) {
                    Text(
                        text = formatLabel,
                        color = CamLabel,
                        fontSize = 11.sp,
                        letterSpacing = 2.sp
                    )
                }
            }
        }

        // ── Error overlay ─────────────────────────────────────────────────
        errorMessage?.let { err ->
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.camera_error),
                    color = CamAlert,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = err,
                    color = CamLabel,
                    fontSize = 10.sp
                )
            }
        }

        // ── Format badge (top-right, shown only when receiving signal) ────
        if (hasSignal && formatLabel.isNotEmpty()) {
            Text(
                text = formatLabel,
                color = CamGood,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            )
        }

        // ── Back button (top-left, subtle) ────────────────────────────────
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = CamNormal.copy(alpha = 0.5f)
            )
        }
    }
}
