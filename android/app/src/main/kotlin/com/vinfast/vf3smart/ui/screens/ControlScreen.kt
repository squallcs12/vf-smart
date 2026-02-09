package com.vinfast.vf3smart.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinfast.vf3smart.R
import com.vinfast.vf3smart.data.model.CarStatus
import com.vinfast.vf3smart.data.network.WebSocketManager
import com.vinfast.vf3smart.ui.components.ControlButton
import com.vinfast.vf3smart.ui.components.OutlinedControlButton
import com.vinfast.vf3smart.ui.components.ToggleControlButton
import com.vinfast.vf3smart.viewmodel.CarStatusViewModel
import com.vinfast.vf3smart.viewmodel.ControlViewModel

/**
 * Control screen with detailed controls for all car functions
 */
@Composable
fun ControlScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    statusViewModel: CarStatusViewModel = hiltViewModel(),
    controlViewModel: ControlViewModel = hiltViewModel()
) {
    val carStatus by statusViewModel.carStatus.collectAsStateWithLifecycle()
    val connectionState by statusViewModel.connectionState.collectAsStateWithLifecycle()
    val operationState by controlViewModel.operationState.collectAsStateWithLifecycle()

    val isConnected = connectionState == WebSocketManager.ConnectionState.Connected

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(operationState) {
        when (val state = operationState) {
            is ControlViewModel.OperationState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                controlViewModel.resetOperationState()
            }
            is ControlViewModel.OperationState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                controlViewModel.resetOperationState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Controls") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // Connection indicator
                    ConnectionStatusIndicator(connectionState)
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val isLoading = operationState is ControlViewModel.OperationState.Loading
            val enabled = isConnected && !isLoading

            // Disconnected banner
            if (!isConnected) {
                DisconnectedWarningBanner(connectionState)
            }

            // Security section
            ControlSection(
                title = "Security",
                icon = Icons.Default.Lock
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ControlButton(
                        text = "Lock",
                        onClick = { controlViewModel.lockCar() },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        icon = { Icon(Icons.Default.Lock, contentDescription = null) }
                    )

                    OutlinedControlButton(
                        text = "Unlock",
                        onClick = { controlViewModel.unlockCar() },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        icon = { Icon(Icons.Default.LockOpen, contentDescription = null) }
                    )
                }

                if (carStatus != null) {
                    Text(
                        text = "Current state: ${carStatus!!.carLockState.uppercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Windows section
            ControlSection(
                title = "Windows",
                icon = Icons.Default.Window
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ControlButton(
                        text = "Close Left",
                        onClick = { controlViewModel.closeLeftWindow() },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        icon = { Icon(painterResource(id = R.drawable.ic_window_left_up), contentDescription = null) }
                    )

                    ControlButton(
                        text = "Close Right",
                        onClick = { controlViewModel.closeRightWindow() },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        icon = { Icon(painterResource(id = R.drawable.ic_window_right_up), contentDescription = null) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ControlButton(
                        text = "Open Left",
                        onClick = { controlViewModel.openLeftWindow() },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        icon = { Icon(painterResource(id = R.drawable.ic_window_left_down), contentDescription = null) }
                    )

                    ControlButton(
                        text = "Open Right",
                        onClick = { controlViewModel.openRightWindow() },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        icon = { Icon(painterResource(id = R.drawable.ic_window_right_down), contentDescription = null) }
                    )
                }

                if (carStatus != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Left: ${getWindowStateText(carStatus!!.windows.leftState)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Right: ${getWindowStateText(carStatus!!.windows.rightState)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Accessories section
            ControlSection(
                title = "Accessories",
                icon = Icons.Default.PowerSettingsNew
            ) {
                val accessoryPowerOn = carStatus?.controls?.accessoryPower == 1
                ToggleControlButton(
                    text = "Accessory Power",
                    isOn = accessoryPowerOn,
                    onToggle = { controlViewModel.toggleAccessoryPower() },
                    enabled = enabled
                )

                val camerasOn = carStatus?.controls?.insideCameras == 1
                ToggleControlButton(
                    text = "Inside Cameras",
                    isOn = camerasOn,
                    onToggle = { controlViewModel.toggleInsideCameras() },
                    enabled = enabled
                )
            }

            // Audio section
            ControlSection(
                title = "Audio / Buzzer",
                icon = Icons.Default.Notifications
            ) {
                var buzzerDuration by remember { mutableStateOf(500) }

                Text(
                    text = "Beep Duration: ${buzzerDuration}ms",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Slider(
                    value = buzzerDuration.toFloat(),
                    onValueChange = { buzzerDuration = it.toInt() },
                    valueRange = 100f..2000f,
                    steps = 18,
                    modifier = Modifier.fillMaxWidth()
                )

                ControlButton(
                    text = "Beep Horn",
                    onClick = { controlViewModel.beepHorn(buzzerDuration) },
                    enabled = enabled,
                    icon = { Icon(Icons.Default.VolumeUp, contentDescription = null) }
                )
            }

            // Charging section
            ControlSection(
                title = "Charging",
                icon = Icons.Default.BatteryChargingFull
            ) {
                ControlButton(
                    text = "Unlock Charger Port",
                    onClick = { controlViewModel.unlockCharger() },
                    enabled = enabled,
                    icon = { Icon(Icons.Default.Lock, contentDescription = null) }
                )

                if (carStatus != null) {
                    Text(
                        text = if (carStatus!!.chargingStatus == 1) {
                            "Status: Currently charging"
                        } else {
                            "Status: Not charging"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Settings section
            ControlSection(
                title = "Settings",
                icon = Icons.Default.Settings
            ) {
                val lightReminderEnabled = carStatus?.lightReminderEnabled == true
                ToggleControlButton(
                    text = "Light Reminder",
                    isOn = lightReminderEnabled,
                    onToggle = { controlViewModel.toggleLightReminder() },
                    enabled = enabled
                )

                Text(
                    text = "Reminds you to turn on headlights at night when in Drive mode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ControlSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            content()
        }
    }
}

private fun getWindowStateText(state: Int): String = when (state) {
    0 -> "Unknown"
    1 -> "Closed"
    2 -> "Open"
    else -> "Unknown"
}

@Composable
private fun ConnectionStatusIndicator(
    connectionState: WebSocketManager.ConnectionState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (connectionState) {
            WebSocketManager.ConnectionState.Connected -> {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Connected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            WebSocketManager.ConnectionState.Disconnected -> {
                Icon(
                    Icons.Default.Cancel,
                    contentDescription = "Disconnected",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
            is WebSocketManager.ConnectionState.Error -> {
                Icon(
                    Icons.Default.Error,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun DisconnectedWarningBanner(
    connectionState: WebSocketManager.ConnectionState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (connectionState is WebSocketManager.ConnectionState.Error) {
                    Icons.Default.Error
                } else {
                    Icons.Default.CloudOff
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Device Disconnected",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = when (connectionState) {
                        is WebSocketManager.ConnectionState.Error -> connectionState.message
                        else -> "All controls are disabled. Reconnecting..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}
