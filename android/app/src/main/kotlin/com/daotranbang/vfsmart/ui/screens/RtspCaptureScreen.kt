package com.daotranbang.vfsmart.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daotranbang.vfsmart.R
import com.daotranbang.vfsmart.viewmodel.RtspCaptureViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RtspCaptureScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RtspCaptureViewModel = hiltViewModel()
) {
    val rtspUrl by viewModel.rtspUrl.collectAsStateWithLifecycle()
    val recordState by viewModel.recordState.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current

    var urlInput by rememberSaveable(rtspUrl) { mutableStateOf(rtspUrl) }

    val isRecording = recordState is RtspCaptureViewModel.RecordState.Recording
    val isSaving = recordState is RtspCaptureViewModel.RecordState.Saving
    val isScanning = scanState is RtspCaptureViewModel.ScanState.Scanning

    LaunchedEffect(recordState) {
        when (val s = recordState) {
            is RtspCaptureViewModel.RecordState.Saved -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.acknowledgeResult()
            }
            is RtspCaptureViewModel.RecordState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.acknowledgeResult()
            }
            else -> {}
        }
    }

    val foundMsg = stringResource(R.string.rtsp_detect_found, "")
    val notFoundMsg = stringResource(R.string.rtsp_detect_not_found)
    LaunchedEffect(scanState) {
        when (val s = scanState) {
            is RtspCaptureViewModel.ScanState.Found -> {
                snackbarHostState.showSnackbar(foundMsg + s.ip)
                viewModel.acknowledgeScan()
            }
            is RtspCaptureViewModel.ScanState.NotFound -> {
                snackbarHostState.showSnackbar(notFoundMsg)
                viewModel.acknowledgeScan()
            }
            else -> {}
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rtsp_capture_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (isRecording) {
                        val blink by remember { derivedStateOf { elapsedSeconds % 2 == 0L } }
                        Icon(
                            Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = if (blink) Color(0xFFEF5350) else Color.Transparent,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(14.dp)
                        )
                        val h = elapsedSeconds / 3600
                        val m = (elapsedSeconds % 3600) / 60
                        val s = elapsedSeconds % 60
                        Text(
                            text = "%02d:%02d:%02d".format(h, m, s),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFEF5350),
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // URL input — hidden while recording
            AnimatedVisibility(visible = !isRecording && !isSaving) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text(stringResource(R.string.rtsp_url_label)) },
                        placeholder = { Text("rtsp://admin:password@192.168.1.x:554/...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboard?.hide()
                            viewModel.setRtspUrl(urlInput)
                        })
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                keyboard?.hide()
                                viewModel.detectCamera()
                            },
                            enabled = !isScanning,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.rtsp_detecting))
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.rtsp_detect))
                            }
                        }
                        Button(
                            onClick = {
                                keyboard?.hide()
                                viewModel.setRtspUrl(urlInput)
                            }
                        ) {
                            Text(stringResource(R.string.rtsp_apply))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Status card
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = null,
                        tint = when {
                            isRecording -> Color(0xFFEF5350)
                            isSaving   -> MaterialTheme.colorScheme.primary
                            else       -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Column {
                        Text(
                            text = when {
                                isRecording -> "Đang quay…"
                                isSaving   -> stringResource(R.string.rtsp_saving)
                                else       -> "Sẵn sàng"
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (urlInput.isNotBlank()) {
                            Text(
                                text = urlInput,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Action button
            when {
                isSaving -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(stringResource(R.string.rtsp_saving))
                    }
                }
                isRecording -> {
                    Button(
                        onClick = { viewModel.stopRecording() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.rtsp_stop_recording))
                    }
                }
                else -> {
                    Button(
                        onClick = { viewModel.startRecording() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF5350)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = urlInput.isNotBlank()
                    ) {
                        Icon(Icons.Default.FiberManualRecord, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.rtsp_start_recording))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
