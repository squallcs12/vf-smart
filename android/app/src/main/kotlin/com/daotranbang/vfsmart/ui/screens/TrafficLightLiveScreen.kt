package com.daotranbang.vfsmart.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daotranbang.vfsmart.R
import com.daotranbang.vfsmart.ui.components.RtspTrafficLightView
import com.daotranbang.vfsmart.vision.TrafficLightDetector
import com.daotranbang.vfsmart.viewmodel.TrafficLightViewModel

/**
 * Pick a video from the library and run the traffic-light detector on it, showing
 * the result cropped onto the light exactly the way the ODO speed cell renders it
 * when the car is stopped (`cropToLight = true`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrafficLightLiveScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrafficLightViewModel = hiltViewModel()
) {
    val reading by viewModel.reading.collectAsStateWithLifecycle()

    var videoUri by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.Saver(
        save = { it?.toString() },
        restore = { (it as? String)?.let(Uri::parse) }
    )) { mutableStateOf<Uri?>(null) }

    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) videoUri = uri }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.traffic_light_live_title)) },
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { pickVideo.launch("video/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (videoUri == null) R.string.traffic_light_pick_video
                        else R.string.traffic_light_pick_other_video
                    )
                )
            }

            val uri = videoUri
            if (uri == null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.traffic_light_no_video),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            // Cropped detection — same rendering as the ODO speed cell when stopped.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            ) {
                RtspTrafficLightView(
                    url = uri.toString(),
                    rtsp = false,
                    cropToLight = true,
                    onReading = viewModel::onReading,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Plain-text readout of the StateFlow.
            ReadingCard(reading)
        }
    }
}

@Composable
private fun ReadingCard(reading: com.daotranbang.vfsmart.vision.TrafficLightAnalyzer.Reading) {
    val (label, color) = when (reading.state) {
        TrafficLightDetector.State.RED ->
            stringResource(R.string.traffic_light_state_red) to Color(0xFFEF5350)
        TrafficLightDetector.State.GREEN ->
            stringResource(R.string.traffic_light_state_green) to Color(0xFF4CAF50)
        TrafficLightDetector.State.NONE ->
            stringResource(R.string.traffic_light_state_none) to Color(0xFF9E9E9E)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Traffic, contentDescription = null, tint = color)
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            if (reading.seconds != null) {
                Text(
                    text = stringResource(R.string.traffic_light_seconds, reading.seconds),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (reading.state != TrafficLightDetector.State.NONE) {
                Text(
                    text = stringResource(
                        R.string.traffic_light_confidence,
                        (reading.confidence * 100).toInt()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
