package com.vinfast.vf3smart.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.vinfast.vf3smart.data.network.WebSocketManager
import com.vinfast.vf3smart.ui.components.ControlButton
import com.vinfast.vf3smart.ui.components.StatusCard
import com.vinfast.vf3smart.viewmodel.CarStatusViewModel
import com.vinfast.vf3smart.viewmodel.ControlViewModel

/**
 * Home screen - Main dashboard with real-time car status
 */
@Composable
fun HomeScreen(
    onNavigateToControls: () -> Unit,
    modifier: Modifier = Modifier,
    statusViewModel: CarStatusViewModel = hiltViewModel(),
    controlViewModel: ControlViewModel = hiltViewModel()
) {
    val carStatus by statusViewModel.carStatus.collectAsStateWithLifecycle()
    val connectionState by statusViewModel.connectionState.collectAsStateWithLifecycle()
    val operationState by controlViewModel.operationState.collectAsStateWithLifecycle()

    // Show snackbar for operations
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
                title = { Text("VF3 Smart") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // Connection indicator
                    ConnectionIndicator(connectionState)
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status cards grid
            if (carStatus != null) {
                StatusCardsGrid(
                    carStatus = carStatus!!,
                    modifier = Modifier.weight(1f)
                )
            } else {
                // Loading state
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Connecting to device...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Disconnected banner
            if (connectionState != WebSocketManager.ConnectionState.Connected) {
                DisconnectedBanner(connectionState)
            }

            // Quick actions
            QuickActions(
                carStatus = carStatus,
                connectionState = connectionState,
                onLock = { controlViewModel.lockCar() },
                onUnlock = { controlViewModel.unlockCar() },
                onCloseWindows = { controlViewModel.closeWindows() },
                onBeep = { controlViewModel.beepHorn() },
                isLoading = operationState is ControlViewModel.OperationState.Loading
            )

            // Navigate to detailed controls
            OutlinedButton(
                onClick = onNavigateToControls,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("More Controls")
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun ConnectionIndicator(
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
                Text(
                    text = "Live",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            WebSocketManager.ConnectionState.Disconnected -> {
                Icon(
                    Icons.Default.Cancel,
                    contentDescription = "Disconnected",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Offline",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            is WebSocketManager.ConnectionState.Error -> {
                Icon(
                    Icons.Default.Error,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Error",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DisconnectedBanner(
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
                        else -> "Controls are disabled. Reconnecting..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun StatusCardsGrid(
    carStatus: CarStatus,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Lock status
        item {
            val isLocked = carStatus.carLockState == "locked"
            StatusCard(
                title = "Car Lock",
                value = if (isLocked) "Locked" else "Unlocked",
                icon = {
                    Icon(
                        if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                }
            )
        }

        // Window status
        item {
            val windowsOpen = carStatus.windows.leftState == 2 || carStatus.windows.rightState == 2
            StatusCard(
                title = "Windows",
                value = if (windowsOpen) "Open" else "Closed",
                isWarning = windowsOpen && carStatus.carLockState == "locked",
                icon = {
                    Icon(
                        if (windowsOpen) Icons.Default.Warning else Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                }
            )
        }

        // Charging status
        item {
            val isCharging = carStatus.chargingStatus == 1
            StatusCard(
                title = "Charging",
                value = if (isCharging) "Charging" else "Not Charging",
                icon = {
                    Icon(
                        Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = if (isCharging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        // Lights status
        item {
            val lightsOn = carStatus.lights.normalLight == 1 || carStatus.lights.demiLight == 1
            val isNight = carStatus.time?.isNight == true
            StatusCard(
                title = "Lights",
                value = if (lightsOn) "On" else "Off",
                isWarning = isNight && !lightsOn,
                icon = {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = if (lightsOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        // Gear status
        item {
            val inDrive = carStatus.sensors.gearDrive == 1
            StatusCard(
                title = "Gear",
                value = if (inDrive) "Drive" else "Park/Other",
                icon = {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                }
            )
        }

        // Door status
        item {
            val doorsOpen = carStatus.doors.frontLeft == 1 ||
                    carStatus.doors.frontRight == 1 ||
                    carStatus.doors.trunk == 1
            StatusCard(
                title = "Doors",
                value = if (doorsOpen) "Open" else "Closed",
                isWarning = doorsOpen,
                icon = {
                    Icon(
                        if (doorsOpen) Icons.Default.Warning else Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun QuickActions(
    carStatus: CarStatus?,
    connectionState: WebSocketManager.ConnectionState,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onCloseWindows: () -> Unit,
    onBeep: () -> Unit,
    isLoading: Boolean
) {
    val isConnected = connectionState == WebSocketManager.ConnectionState.Connected
    val enabled = isConnected && !isLoading
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Lock/Unlock based on current state
                if (carStatus?.carLockState == "locked") {
                    ControlButton(
                        text = "Unlock",
                        onClick = onUnlock,
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        icon = { Icon(Icons.Default.LockOpen, contentDescription = null) }
                    )
                } else {
                    ControlButton(
                        text = "Lock",
                        onClick = onLock,
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        icon = { Icon(Icons.Default.Lock, contentDescription = null) }
                    )
                }

                // Beep horn
                ControlButton(
                    text = "Beep",
                    onClick = onBeep,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    icon = { Icon(Icons.Default.Notifications, contentDescription = null) }
                )
            }

            // Close windows button (if windows open or window close active)
            val showWindowsButton = carStatus?.let {
                it.windows.leftState == 2 || it.windows.rightState == 2 || it.windowCloseActive
            } ?: false

            if (showWindowsButton) {
                ControlButton(
                    text = if (carStatus?.windowCloseActive == true) {
                        "Windows Closing (${carStatus.windowCloseRemainingMs / 1000}s)"
                    } else {
                        "Close Windows"
                    },
                    onClick = onCloseWindows,
                    enabled = enabled,
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}
