package com.daotranbang.vfsmart.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.daotranbang.vfsmart.R
import com.daotranbang.vfsmart.ui.components.ControlButton
import com.daotranbang.vfsmart.viewmodel.DebugViewModel

@Composable
fun DebugScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    debugViewModel: DebugViewModel = hiltViewModel()
) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error)
                    Column {
                        Text(stringResource(R.string.debug_mode_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(stringResource(R.string.debug_mode_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                            text = stringResource(R.string.debug_test_window),
                            onClick = { debugViewModel.testWindowWarning() },
                            icon = { Icon(Icons.Default.VolumeUp, contentDescription = null) }
                        )
                        Text(text = "Cửa sổ đang mở (x2)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ControlButton(
                            text = stringResource(R.string.debug_test_light),
                            onClick = { debugViewModel.testLightReminder() },
                            icon = { Icon(Icons.Default.VolumeUp, contentDescription = null) }
                        )
                        Text(text = "Bạn chưa bật đèn (x2)",
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
