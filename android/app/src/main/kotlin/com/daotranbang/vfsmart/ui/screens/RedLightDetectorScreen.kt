package com.daotranbang.vfsmart.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daotranbang.vfsmart.R
import com.daotranbang.vfsmart.vision.TrafficLightDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface DetectState {
    data object Idle : DetectState
    data object Detecting : DetectState
    data class Success(val result: TrafficLightDetector.Result) : DetectState
    data class Failure(val message: String) : DetectState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedLightDetectorScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var state by remember { mutableStateOf<DetectState>(DetectState.Idle) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            imageUri = uri
            preview = null
            state = DetectState.Detecting
        }
    }

    // Load the preview bitmap and run traffic-light detection on each new image.
    LaunchedEffect(imageUri) {
        val uri = imageUri ?: return@LaunchedEffect
        try {
            val bmp = withContext(Dispatchers.IO) { loadBitmap(context, uri) }
            preview = bmp.asImageBitmap()
            val result = withContext(Dispatchers.Default) {
                TrafficLightDetector.detect(context, bmp)
            }
            state = DetectState.Success(result)
        } catch (e: Exception) {
            state = DetectState.Failure(e.message ?: e.javaClass.simpleName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.red_light_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Instructions
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = stringResource(R.string.red_light_instructions),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            // Pick / replace image
            Button(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (imageUri == null) stringResource(R.string.red_light_pick_image)
                    else stringResource(R.string.red_light_pick_another)
                )
            }

            // Selected image preview, with detected boxes overlaid. The Box uses the
            // image's own aspect ratio so the bitmap fills it with no letterboxing —
            // detections (normalised 0..1) then map straight onto the overlay.
            preview?.let { bmp ->
                val aspect = (bmp.width.toFloat() / bmp.height.toFloat()).coerceIn(0.4f, 3f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Image(
                        bitmap = bmp,
                        contentDescription = stringResource(R.string.red_light_image_cd),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.matchParentSize()
                    )
                    (state as? DetectState.Success)?.let { s ->
                        DetectionOverlay(
                            boxes = s.result.boxes,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
            }

            // Result
            when (val s = state) {
                DetectState.Idle -> Unit

                DetectState.Detecting -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(
                            text = stringResource(R.string.red_light_detecting),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                is DetectState.Success -> ResultCard(s.result)

                is DetectState.Failure -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.red_light_error, s.message),
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: TrafficLightDetector.Result) {
    val (label, color) = when (result.state) {
        TrafficLightDetector.State.RED ->
            stringResource(R.string.traffic_light_state_red) to androidx.compose.ui.graphics.Color(0xFFEF5350)
        TrafficLightDetector.State.NONE ->
            stringResource(R.string.traffic_light_state_none) to MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )

            if (result.state != TrafficLightDetector.State.NONE) {
                Text(
                    text = stringResource(
                        R.string.traffic_light_confidence,
                        (result.confidence * 100).toInt()
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Count-box presence — whether the "Red count" class was found.
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                CountChip(
                    label = stringResource(R.string.tl_photo_red_count),
                    present = result.hasRedCount
                )
            }

            Text(
                text = stringResource(R.string.tl_photo_boxes, result.boxes.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CountChip(label: String, present: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (present) stringResource(R.string.tl_photo_yes)
                   else stringResource(R.string.tl_photo_no),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (present) androidx.compose.ui.graphics.Color(0xFF4CAF50)
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Draws the model's detected boxes (normalised coords) over the preview image. */
@Composable
private fun DetectionOverlay(
    boxes: List<TrafficLightDetector.Box>,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier = modifier) {
        for (b in boxes) {
            val color = boxColor(b.cls)
            val left = b.left * size.width
            val top = b.top * size.height
            val w = (b.right - b.left) * size.width
            val h = (b.bottom - b.top) * size.height
            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(w, h),
                style = Stroke(width = 3.dp.toPx())
            )
            // Class + confidence tag above the box.
            val tag = "${boxLabel(b.cls)} ${(b.score * 100).toInt()}%"
            val measured = measurer.measure(
                tag,
                style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            )
            val tagW = measured.size.width.toFloat() + 8f
            val tagH = measured.size.height.toFloat()
            val tagTop = (top - tagH).coerceAtLeast(0f)
            drawRect(color = color, topLeft = Offset(left, tagTop), size = Size(tagW, tagH))
            drawText(measured, topLeft = Offset(left + 4f, tagTop))
        }
    }
}

private fun boxColor(cls: Int): Color = when (cls) {
    2 -> Color(0xFFEF5350)   // Red
    3 -> Color(0xFFE57373)   // Red count
    else -> Color.Yellow
}

private fun boxLabel(cls: Int): String = when (cls) {
    2 -> "Red"
    3 -> "R.count"
    else -> "?"
}

/** Decode a content [uri] into a Bitmap, applying EXIF orientation. */
private fun loadBitmap(context: android.content.Context, uri: Uri): Bitmap {
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
