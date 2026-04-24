package com.daotranbang.vfsmart.ui.screens

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daotranbang.vfsmart.R
import com.daotranbang.vfsmart.data.model.CarStatus
import com.daotranbang.vfsmart.navigation.VF3GattServer
import com.daotranbang.vfsmart.ui.components.ControlButton
import com.daotranbang.vfsmart.ui.components.OutlinedControlButton
import com.daotranbang.vfsmart.ui.components.ToggleControlButton
import com.daotranbang.vfsmart.viewmodel.CarStatusViewModel
import com.daotranbang.vfsmart.viewmodel.ControlViewModel

@Composable
fun ControlScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTpmsCalibration: () -> Unit = {},
    modifier: Modifier = Modifier,
    statusViewModel: CarStatusViewModel = hiltViewModel(),
    controlViewModel: ControlViewModel = hiltViewModel()
) {
    val carStatus by statusViewModel.carStatus.collectAsStateWithLifecycle()
    val connectionState by statusViewModel.connectionState.collectAsStateWithLifecycle()
    val operationState by controlViewModel.operationState.collectAsStateWithLifecycle()

    val isConnected = connectionState == VF3GattServer.BleConnectionState.Connected

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
                title = { Text(stringResource(R.string.controls_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
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

            if (!isConnected) {
                DisconnectedWarningBanner(connectionState)
            }

            // Security section
            ControlSection(
                title = stringResource(R.string.section_security),
                icon = Icons.Default.Lock
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ControlButton(
                        text = stringResource(R.string.action_lock),
                        onClick = { controlViewModel.lockCar() },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        icon = { Icon(Icons.Default.Lock, contentDescription = null) }
                    )
                    OutlinedControlButton(
                        text = stringResource(R.string.action_unlock),
                        onClick = { controlViewModel.unlockCar() },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        icon = { Icon(Icons.Default.LockOpen, contentDescription = null) }
                    )
                }
                if (carStatus != null) {
                    Text(
                        text = stringResource(R.string.current_lock_state,
                            carStatus!!.carLockState.uppercase()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Windows section
            ControlSection(
                title = stringResource(R.string.section_windows),
                icon = Icons.Default.Window
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ControlButton(
                        text = stringResource(R.string.btn_close_left),
                        onClick = { controlViewModel.closeLeftWindow() },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        icon = { Icon(painterResource(id = R.drawable.ic_window_left_up), contentDescription = null) }
                    )
                    ControlButton(
                        text = stringResource(R.string.btn_close_right),
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
                        text = stringResource(R.string.btn_open_left),
                        onClick = { controlViewModel.openLeftWindow() },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        icon = { Icon(painterResource(id = R.drawable.ic_window_left_down), contentDescription = null) }
                    )
                    ControlButton(
                        text = stringResource(R.string.btn_open_right),
                        onClick = { controlViewModel.openRightWindow() },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        icon = { Icon(painterResource(id = R.drawable.ic_window_right_down), contentDescription = null) }
                    )
                }
                if (carStatus != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.window_left_state,
                                windowStateText(carStatus!!.windows.leftState)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.window_right_state,
                                windowStateText(carStatus!!.windows.rightState)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Accessories section
            ControlSection(
                title = stringResource(R.string.section_accessories),
                icon = Icons.Default.PowerSettingsNew
            ) {
                val accessoryPowerOn = carStatus?.controls?.accessoryPower == 1
                ToggleControlButton(
                    text = stringResource(R.string.accessory_power),
                    isOn = accessoryPowerOn,
                    onToggle = { controlViewModel.toggleAccessoryPower() },
                    enabled = enabled
                )
                val camerasOn = carStatus?.controls?.insideCameras == 1
                ToggleControlButton(
                    text = stringResource(R.string.inside_cameras),
                    isOn = camerasOn,
                    onToggle = { controlViewModel.toggleInsideCameras() },
                    enabled = enabled
                )
            }

            // Audio section
            ControlSection(
                title = stringResource(R.string.section_audio),
                icon = Icons.Default.Notifications
            ) {
                var buzzerDuration by remember { mutableStateOf(500) }
                Text(
                    text = stringResource(R.string.beep_duration_label, buzzerDuration),
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
                    text = stringResource(R.string.btn_beep_horn),
                    onClick = { controlViewModel.beepHorn(buzzerDuration) },
                    enabled = enabled,
                    icon = { Icon(Icons.Default.VolumeUp, contentDescription = null) }
                )
            }

            // Charging section
            ControlSection(
                title = stringResource(R.string.section_charging),
                icon = Icons.Default.BatteryChargingFull
            ) {
                ControlButton(
                    text = stringResource(R.string.btn_unlock_charger),
                    onClick = { controlViewModel.unlockCharger() },
                    enabled = enabled,
                    icon = { Icon(Icons.Default.Lock, contentDescription = null) }
                )
                if (carStatus != null) {
                    Text(
                        text = if (carStatus!!.chargingStatus == 1)
                            stringResource(R.string.status_charging)
                        else
                            stringResource(R.string.status_not_charging),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Settings section
            ControlSection(
                title = stringResource(R.string.section_settings),
                icon = Icons.Default.Settings
            ) {
                val lightReminderEnabled = carStatus?.lightReminderEnabled == true
                ToggleControlButton(
                    text = stringResource(R.string.light_reminder),
                    isOn = lightReminderEnabled,
                    onToggle = { controlViewModel.toggleLightReminder() },
                    enabled = enabled
                )
                Text(
                    text = stringResource(R.string.light_reminder_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // TPMS Calibration
            OutlinedButton(
                onClick  = onNavigateToTpmsCalibration,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Settings, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tpms_calibration))
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
                Icon(icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Text(text = title, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
private fun windowStateText(state: Int): String = when (state) {
    1    -> stringResource(R.string.window_state_closed)
    2    -> stringResource(R.string.window_state_open)
    else -> stringResource(R.string.window_state_unknown)
}

@Composable
private fun ConnectionStatusIndicator(
    connectionState: VF3GattServer.BleConnectionState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (connectionState) {
            VF3GattServer.BleConnectionState.Connected ->
                Icon(Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.connected),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp))
            VF3GattServer.BleConnectionState.Disconnected ->
                Icon(Icons.Default.Cancel,
                    contentDescription = stringResource(R.string.offline),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun DisconnectedWarningBanner(
    connectionState: VF3GattServer.BleConnectionState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CloudOff, contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.device_disconnected_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
                Text(stringResource(R.string.device_disconnected_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
            }
        }
    }
}
