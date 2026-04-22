package com.daotranbang.vfsmart.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daotranbang.vfsmart.data.model.CarStatus
import com.daotranbang.vfsmart.data.network.WebSocketManager
import com.daotranbang.vfsmart.ui.components.ControlButton
import com.daotranbang.vfsmart.ui.components.StatusCard
import com.daotranbang.vfsmart.viewmodel.CarStatusViewModel
import com.daotranbang.vfsmart.viewmodel.ControlViewModel

@Composable
fun HomeScreen(
    onNavigateToControls: () -> Unit = {},
    onNavigateToDebug: () -> Unit = {},
    onNavigateToSetup: () -> Unit = {},
    onNavigateToApps: () -> Unit = {},
    onNavigateToMirror: () -> Unit = {},
    modifier: Modifier = Modifier,
    statusViewModel: CarStatusViewModel = hiltViewModel(),
    controlViewModel: ControlViewModel = hiltViewModel()
) {
    val carStatus       by statusViewModel.carStatus.collectAsStateWithLifecycle()
    val connectionState by statusViewModel.connectionState.collectAsStateWithLifecycle()
    val operationState  by controlViewModel.operationState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (!isNotificationListenerGranted(context)) {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        FullContent(
            carStatus           = carStatus,
            connectionState     = connectionState,
            operationState      = operationState,
            controlViewModel    = controlViewModel,
            onNavigateToControls = onNavigateToControls,
            onNavigateToDebug   = onNavigateToDebug,
            onNavigateToSetup   = onNavigateToSetup,
            onNavigateToApps    = onNavigateToApps
        )

        SmallFloatingActionButton(
            onClick        = onNavigateToMirror,
            modifier       = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector        = Icons.Default.Fullscreen,
                contentDescription = "Enter mirror mode",
                modifier           = Modifier.size(18.dp)
            )
        }
    }
}

// ── Full mode: scrollable layout with header + quick controls ─────────────────

@Composable
private fun FullContent(
    carStatus: CarStatus?,
    connectionState: WebSocketManager.ConnectionState,
    operationState: ControlViewModel.OperationState,
    controlViewModel: ControlViewModel,
    onNavigateToControls: () -> Unit,
    onNavigateToDebug: () -> Unit,
    onNavigateToSetup: () -> Unit,
    onNavigateToApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
        modifier = modifier,
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("VF3 Smart") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    ConnectionIndicator(connectionState)
                    IconButton(onClick = onNavigateToApps) {
                        Icon(Icons.Default.Apps, contentDescription = "All Apps")
                    }
                    IconButton(onClick = {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                        )
                    }) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = "Android Settings")
                    }
                    IconButton(onClick = onNavigateToSetup) {
                        Icon(Icons.Default.Settings, contentDescription = "Setup")
                    }
                    IconButton(onClick = onNavigateToDebug) {
                        Icon(Icons.Default.BugReport, contentDescription = "Debug",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        val isConnected = connectionState == WebSocketManager.ConnectionState.Connected
        val isLoading   = operationState is ControlViewModel.OperationState.Loading
        val enabled     = isConnected && !isLoading

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isConnected) {
                DisconnectedBanner(connectionState)
            }

            if (!isNotificationListenerGranted(context)) {
                NotificationAccessBanner()
            }

            StatusGrid(carStatus = carStatus, columns = 2, modifier = Modifier.fillMaxWidth())

            QuickActionsCard(
                carStatus     = carStatus,
                enabled       = enabled,
                onLock        = { controlViewModel.lockCar() },
                onUnlock      = { controlViewModel.unlockCar() },
                onBeep        = { controlViewModel.beepHorn() },
                onCloseWindows = { controlViewModel.closeWindows() }
            )

            OutlinedButton(
                onClick  = onNavigateToControls,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("More Controls")
            }

            // Extra space so FAB doesn't overlap last item
            Spacer(Modifier.height(56.dp))
        }
    }
}

// ── Status grid ───────────────────────────────────────────────────────────────

@Composable
private fun StatusGrid(
    carStatus: CarStatus?,
    columns: Int,
    modifier: Modifier = Modifier
) {
    val isLocked    = carStatus?.carLockState == "locked"
    val windowsOpen = carStatus != null &&
            (carStatus.windows.leftState == 2 || carStatus.windows.rightState == 2)
    val isCharging  = carStatus?.chargingStatus == 1
    val lightsOn    = carStatus != null &&
            (carStatus.lights.normalLight == 1 || carStatus.lights.demiLight == 1)
    val isNight     = carStatus?.time?.isNight == true
    val inDrive     = carStatus?.sensors?.gearDrive == 1
    val doorsOpen   = carStatus != null && (carStatus.doors.frontLeft == 1 ||
            carStatus.doors.frontRight == 1 || carStatus.doors.trunk == 1)

    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    val cards: List<@Composable RowScope.() -> Unit> = listOf(
        {
            StatusCard(
                title = "Car Lock",
                value = if (carStatus == null) "--" else if (isLocked) "Locked" else "Unlocked",
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = {
                    Icon(if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null, modifier = Modifier.size(28.dp),
                        tint = if (carStatus == null) dim else LocalContentColor.current)
                }
            )
        },
        {
            StatusCard(
                title = "Windows",
                value = if (carStatus == null) "--" else if (windowsOpen) "Open" else "Closed",
                isWarning = windowsOpen && isLocked,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = {
                    Icon(if (windowsOpen) Icons.Default.Warning else Icons.Default.Check,
                        contentDescription = null, modifier = Modifier.size(28.dp),
                        tint = if (carStatus == null) dim else LocalContentColor.current)
                }
            )
        },
        {
            StatusCard(
                title = "Charging",
                value = if (carStatus == null) "--" else if (isCharging) "Charging" else "Not Charging",
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = {
                    Icon(Icons.Default.BatteryChargingFull, contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = when {
                            carStatus == null -> dim
                            isCharging        -> MaterialTheme.colorScheme.primary
                            else              -> dim
                        })
                }
            )
        },
        {
            StatusCard(
                title = "Lights",
                value = if (carStatus == null) "--" else if (lightsOn) "On" else "Off",
                isWarning = isNight && !lightsOn,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = {
                    Icon(Icons.Default.Lightbulb, contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = when {
                            carStatus == null -> dim
                            lightsOn          -> MaterialTheme.colorScheme.primary
                            else              -> dim
                        })
                }
            )
        },
        {
            StatusCard(
                title = "Gear",
                value = if (carStatus == null) "--" else if (inDrive) "Drive" else "Park",
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = if (carStatus == null) dim else LocalContentColor.current)
                }
            )
        },
        {
            StatusCard(
                title = "Doors",
                value = if (carStatus == null) "--" else if (doorsOpen) "Open" else "Closed",
                isWarning = doorsOpen,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = {
                    Icon(if (doorsOpen) Icons.Default.Warning else Icons.Default.Check,
                        contentDescription = null, modifier = Modifier.size(28.dp),
                        tint = if (carStatus == null) dim else LocalContentColor.current)
                }
            )
        }
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cards.chunked(columns).forEach { rowCards ->
            Row(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowCards.forEach { card -> card() }
            }
        }
    }
}

// ── Quick actions card ────────────────────────────────────────────────────────

@Composable
private fun QuickActionsCard(
    carStatus: CarStatus?,
    enabled: Boolean,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onBeep: () -> Unit,
    onCloseWindows: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Quick Actions", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ControlButton(text = "Lock", onClick = onLock,
                    modifier = Modifier.weight(1f), enabled = enabled,
                    icon = { Icon(Icons.Default.Lock, contentDescription = null) })
                ControlButton(text = "Unlock", onClick = onUnlock,
                    modifier = Modifier.weight(1f), enabled = enabled,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    icon = { Icon(Icons.Default.LockOpen, contentDescription = null) })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ControlButton(text = "Beep", onClick = onBeep,
                    modifier = Modifier.weight(1f), enabled = enabled,
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    icon = { Icon(Icons.Default.Notifications, contentDescription = null) })
                val windowLabel = if (carStatus?.windowCloseActive == true)
                    "Closing (${carStatus.windowCloseRemainingMs / 1000}s)" else "Close Windows"
                ControlButton(text = windowLabel, onClick = onCloseWindows,
                    modifier = Modifier.weight(1f), enabled = enabled,
                    icon = { Icon(Icons.Default.Close, contentDescription = null) })
            }
        }
    }
}

// ── Shared components ─────────────────────────────────────────────────────────

@Composable
private fun ConnectionIndicator(
    connectionState: WebSocketManager.ConnectionState,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.padding(end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically) {
        val (icon, label, color) = when (connectionState) {
            WebSocketManager.ConnectionState.Connected ->
                Triple(Icons.Default.CheckCircle, "Live", MaterialTheme.colorScheme.primary)
            WebSocketManager.ConnectionState.Disconnected ->
                Triple(Icons.Default.Cancel, "Offline", MaterialTheme.colorScheme.error)
            is WebSocketManager.ConnectionState.Error ->
                Triple(Icons.Default.Error, "Error", MaterialTheme.colorScheme.error)
        }
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun DisconnectedBanner(
    connectionState: WebSocketManager.ConnectionState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (connectionState is WebSocketManager.ConnectionState.Error)
                    Icons.Default.Error else Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Not Connected", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    text = when (connectionState) {
                        is WebSocketManager.ConnectionState.Error -> connectionState.message
                        else -> "Controls disabled. Reconnecting..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

private fun isNotificationListenerGranted(context: android.content.Context): Boolean {
    val flat = android.provider.Settings.Secure.getString(
        context.contentResolver, "enabled_notification_listeners"
    )
    return flat?.contains(context.packageName) == true
}

@Composable
private fun NotificationAccessBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Notifications, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Navigation Access Required", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("Grant notification access to show turn-by-turn directions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
            }
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) { Text("Grant") }
        }
    }
}
