package com.daotranbang.vfsmart.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.daotranbang.vfsmart.R
import com.daotranbang.vfsmart.ui.components.ControlButton
import com.daotranbang.vfsmart.util.playLightReminder
import com.daotranbang.vfsmart.viewmodel.DebugViewModel

@Composable
fun DebugScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    debugViewModel: DebugViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text(stringResource(R.string.debug_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    titleContentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.debug_voice_warnings),
                        style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.debug_voice_warnings_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ControlButton(
                            text = stringResource(R.string.debug_test_close_windows),
                            onClick = { debugViewModel.testCloseWindows() },
                            icon = { Icon(Icons.Default.VolumeUp, contentDescription = null) }
                        )
                        Text(text = "Vui lòng đóng cửa sổ (x2)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ControlButton(
                            text = stringResource(R.string.debug_test_light_reminder),
                            onClick = { playLightReminder(context) },
                            icon = { Icon(Icons.Default.VolumeUp, contentDescription = null) }
                        )
                        Text(text = "Âm thanh nhắc bật đèn",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.debug_info),
                        style = MaterialTheme.typography.titleLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.debug_tts_engine),
                            style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.debug_tts_value),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
