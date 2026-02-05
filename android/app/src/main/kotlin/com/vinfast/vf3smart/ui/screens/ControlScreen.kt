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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinfast.vf3smart.data.model.CarStatus
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
    val operationState by controlViewModel.operationState.collectAsStateWithLifecycle()

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
                )
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
                        enabled = !isLoading,
                        icon = { Icon(Icons.Default.Lock, contentDescription = null) }
                    )

                    OutlinedControlButton(
                        text = "Unlock",
                        onClick = { controlViewModel.unlockCar() },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading,
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
                ControlButton(
                    text = if (carStatus?.windowCloseActive == true) {
                        "Closing... (${carStatus!!.windowCloseRemainingMs / 1000}s remaining)"
                    } else {
                        "Close Windows (30s)"
                    },
                    onClick = { controlViewModel.closeWindows() },
                    enabled = !isLoading && carStatus?.windowCloseActive != true
                )

                if (carStatus?.windowCloseActive == true) {
                    OutlinedControlButton(
                        text = "Stop",
                        onClick = { controlViewModel.stopWindows() },
                        enabled = !isLoading,
                        icon = { Icon(Icons.Default.Stop, contentDescription = null) }
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "Manual Window Control",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                var leftWindowDown by remember { mutableStateOf(false) }
                var rightWindowDown by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ToggleControlButton(
                        text = "Left Down",
                        isOn = leftWindowDown,
                        onToggle = {
                            leftWindowDown = !leftWindowDown
                            controlViewModel.controlWindowDown("left", leftWindowDown)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    )

                    ToggleControlButton(
                        text = "Right Down",
                        isOn = rightWindowDown,
                        onToggle = {
                            rightWindowDown = !rightWindowDown
                            controlViewModel.controlWindowDown("right", rightWindowDown)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    )
                }

                ControlButton(
                    text = "Both Windows Down",
                    onClick = {
                        leftWindowDown = true
                        rightWindowDown = true
                        controlViewModel.controlWindowDown("both", true)
                    },
                    enabled = !isLoading && !leftWindowDown && !rightWindowDown,
                    containerColor = MaterialTheme.colorScheme.secondary
                )

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
                    enabled = !isLoading
                )

                val camerasOn = carStatus?.controls?.insideCameras == 1
                ToggleControlButton(
                    text = "Inside Cameras",
                    isOn = camerasOn,
                    onToggle = { controlViewModel.toggleInsideCameras() },
                    enabled = !isLoading
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
                    enabled = !isLoading,
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
                    enabled = !isLoading,
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
                    enabled = !isLoading
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
