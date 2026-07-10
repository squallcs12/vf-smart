package com.daotranbang.vfsmart.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daotranbang.vfsmart.R
import com.daotranbang.vfsmart.data.network.ConnectionState
import com.daotranbang.vfsmart.ui.components.ControlButton
import com.daotranbang.vfsmart.ui.components.OutlinedControlButton
import com.daotranbang.vfsmart.ui.components.ToggleControlButton
import com.daotranbang.vfsmart.util.playLightReminder
import com.daotranbang.vfsmart.viewmodel.CarStatusViewModel
import com.daotranbang.vfsmart.viewmodel.ControlViewModel

// Compact metrics so the whole dashboard fits the landscape height (~300dp of
// usable content below the app bar) with no scrolling.
private val ButtonHeight = 34.dp
private val CardPadding  = 8.dp
private val GapSmall     = 4.dp
private val Gap          = 8.dp
// Material's default 24dp button padding truncates labels in the narrow columns.
private val CompactPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)

/**
 * Combined status + control dashboard, laid out for the car's landscape display.
 *
 * The old separate "status" (home) and "controls" screens are merged per feature:
 * each card shows a feature's live status as a colored badge in its header with
 * that feature's controls in the body (windows status + window buttons, lock
 * status + lock/unlock, charging status + charger unlock, …). Status-only items
 * (doors, gear) share a compact read-only card. Cards flow into three columns
 * sized so the whole screen fits the landscape height without scrolling.
 */
@Composable
fun HomeScreen(
    onNavigateToDebug: () -> Unit = {},
    onNavigateToMirror: () -> Unit = {},
    onNavigateToSetup: () -> Unit = {},
    onNavigateToCamera: () -> Unit = {},
    onNavigateToRedLight: () -> Unit = {},
    onNavigateToTrafficLight: () -> Unit = {},
    onNavigateToRtspCapture: () -> Unit = {},
    modifier: Modifier = Modifier,
    statusViewModel: CarStatusViewModel = hiltViewModel(),
    controlViewModel: ControlViewModel = hiltViewModel()
) {
    val carStatus       by statusViewModel.carStatus.collectAsStateWithLifecycle()
    val connectionState by statusViewModel.connectionState.collectAsStateWithLifecycle()
    val operationState  by controlViewModel.operationState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    // Notification-listener access reads Settings.Secure, a synchronous binder call to
    // system_server. Never do that in the composition body — it would run on every
    // recomposition (i.e. every /ws frame) on the UI thread and can stall for seconds
    // when system_server is busy. Query once here and re-check only on resume (e.g. when
    // returning from the grant screen), caching the result in state the UI reads.
    var notificationAccessGranted by remember {
        mutableStateOf(isNotificationListenerGranted(context))
    }
    LifecycleResumeEffect(Unit) {
        notificationAccessGranted = isNotificationListenerGranted(context)
        onPauseOrDispose { }
    }
    LaunchedEffect(notificationAccessGranted) {
        if (!notificationAccessGranted) {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

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
        modifier = modifier.fillMaxSize(),
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // Connection state lives here only — no banner below, to keep
                    // the dashboard on one screen.
                    ConnectionIndicator(connectionState)
                    IconButton(onClick = onNavigateToSetup) {
                        Icon(Icons.Default.Settings,
                            contentDescription = stringResource(R.string.setup_title))
                    }
                    IconButton(onClick = onNavigateToMirror) {
                        Icon(Icons.Default.Fullscreen,
                            contentDescription = stringResource(R.string.mirror_mode_cd))
                    }
                    IconButton(onClick = { playLightReminder(context) }) {
                        Icon(Icons.Default.VolumeUp,
                            contentDescription = stringResource(R.string.light_reminder_test_cd))
                    }
                    IconButton(onClick = onNavigateToDebug) {
                        Icon(Icons.Default.BugReport,
                            contentDescription = stringResource(R.string.debug_cd),
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        val isConnected = connectionState == ConnectionState.Connected
        val isLoading   = operationState is ControlViewModel.OperationState.Loading
        val enabled     = isConnected && !isLoading

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(Gap),
            verticalArrangement = Arrangement.spacedBy(Gap)
        ) {
            if (!notificationAccessGranted) {
                NotificationAccessBanner()
            }

            // Landscape three-column card flow sized to fit without scrolling.
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Gap)
            ) {
                // Pass each section only the group/primitives it renders, not the whole
                // CarStatus. In WebSocketManager a nested group (windows, doors, …) is
                // only reallocated when its own delta arrives, and unrelated scalars keep
                // their value — so a section receiving just its inputs is skipped by
                // Compose on frames that didn't touch them, instead of recomposing on
                // every /ws frame.
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Gap)
                ) {
                    LockSection(carStatus?.carLockState, enabled, controlViewModel)
                    ChargingSection(carStatus?.chargingStatus, enabled, controlViewModel)
                    VehicleStatusSection(carStatus?.doors, carStatus?.sensors?.gearDrive)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Gap)
                ) {
                    WindowsSection(
                        windows                = carStatus?.windows,
                        isLocked               = carStatus?.carLockState == "locked",
                        windowCloseActive      = carStatus?.windowCloseActive == true,
                        windowCloseRemainingMs = carStatus?.windowCloseRemainingMs ?: 0L,
                        enabled                = enabled,
                        controlViewModel       = controlViewModel
                    )
                    AudioSection(enabled, controlViewModel)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Gap)
                ) {
                    LightsSection(
                        lights               = carStatus?.lights,
                        isNight              = carStatus?.time?.isNight == true,
                        lightReminderEnabled = carStatus?.lightReminderEnabled == true,
                        enabled              = enabled,
                        controlViewModel     = controlViewModel
                    )
                    AccessoriesSection(carStatus?.controls, enabled, controlViewModel)
                    UtilitiesSection(
                        onNavigateToCamera       = onNavigateToCamera,
                        onNavigateToRtspCapture  = onNavigateToRtspCapture,
                        onNavigateToRedLight     = onNavigateToRedLight,
                        onNavigateToTrafficLight = onNavigateToTrafficLight
                    )
                }
            }
        }
    }
}

// ── Merged feature sections (status badge + controls) ─────────────────────────

/** Car lock: locked/unlocked status badge + lock/unlock buttons. */
@Composable
private fun LockSection(
    lockState: String?,
    enabled: Boolean,
    controlViewModel: ControlViewModel
) {
    val isLocked = lockState == "locked"
    FeatureCard(
        title  = stringResource(R.string.card_car_lock),
        icon   = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
        status = when {
            lockState == null -> "--"
            isLocked          -> stringResource(R.string.car_locked)
            else              -> stringResource(R.string.car_unlocked)
        },
        statusColor = when {
            lockState == null -> MaterialTheme.colorScheme.onSurfaceVariant
            isLocked          -> MaterialTheme.colorScheme.primary
            else              -> MaterialTheme.colorScheme.error
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GapSmall)
        ) {
            ControlButton(
                text = stringResource(R.string.action_lock),
                onClick = { controlViewModel.lockCar() },
                modifier = Modifier.weight(1f).height(ButtonHeight),
                enabled = enabled,
                contentPadding = CompactPadding
            )
            OutlinedControlButton(
                text = stringResource(R.string.action_unlock),
                onClick = { controlViewModel.unlockCar() },
                modifier = Modifier.weight(1f).height(ButtonHeight),
                enabled = enabled,
                contentPadding = CompactPadding
            )
        }
    }
}

/** Windows: open/closed badge + per-side states + open/close + auto-close-all. */
@Composable
private fun WindowsSection(
    windows: com.daotranbang.vfsmart.data.model.Windows?,
    isLocked: Boolean,
    windowCloseActive: Boolean,
    windowCloseRemainingMs: Long,
    enabled: Boolean,
    controlViewModel: ControlViewModel
) {
    val windowsOpen = windows != null && (windows.leftState == 2 || windows.rightState == 2)
    FeatureCard(
        title  = stringResource(R.string.section_windows),
        icon   = Icons.Default.Window,
        status = when {
            windows == null -> "--"
            windowsOpen     -> stringResource(R.string.windows_open)
            else            -> stringResource(R.string.windows_closed)
        },
        statusColor = when {
            windows == null         -> MaterialTheme.colorScheme.onSurfaceVariant
            windowsOpen && isLocked -> MaterialTheme.colorScheme.error
            windowsOpen             -> MaterialTheme.colorScheme.tertiary
            else                    -> MaterialTheme.colorScheme.primary
        }
    ) {
        if (windows != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.window_left_state,
                        windowStateText(windows.leftState)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.window_right_state,
                        windowStateText(windows.rightState)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GapSmall)
        ) {
            ControlButton(
                text = stringResource(R.string.btn_close_left),
                onClick = { controlViewModel.closeLeftWindow() },
                modifier = Modifier.weight(1f).height(ButtonHeight),
                enabled = enabled,
                contentPadding = CompactPadding
            )
            ControlButton(
                text = stringResource(R.string.btn_close_right),
                onClick = { controlViewModel.closeRightWindow() },
                modifier = Modifier.weight(1f).height(ButtonHeight),
                enabled = enabled,
                contentPadding = CompactPadding
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GapSmall)
        ) {
            ControlButton(
                text = stringResource(R.string.btn_open_left),
                onClick = { controlViewModel.openLeftWindow() },
                modifier = Modifier.weight(1f).height(ButtonHeight),
                enabled = enabled,
                contentPadding = CompactPadding
            )
            ControlButton(
                text = stringResource(R.string.btn_open_right),
                onClick = { controlViewModel.openRightWindow() },
                modifier = Modifier.weight(1f).height(ButtonHeight),
                enabled = enabled,
                contentPadding = CompactPadding
            )
        }
        // Auto-close all windows (was a Home quick action) with live countdown.
        val closeAllLabel = if (windowCloseActive)
            stringResource(R.string.action_closing_windows, windowCloseRemainingMs / 1000)
        else
            stringResource(R.string.action_close_windows)
        ControlButton(
            text = closeAllLabel,
            onClick = { controlViewModel.closeWindows() },
            modifier = Modifier.fillMaxWidth().height(ButtonHeight),
            enabled = enabled,
            contentPadding = CompactPadding,
            icon = { Icon(Icons.Default.Close, contentDescription = null,
                modifier = Modifier.size(16.dp)) }
        )
    }
}

/** Charging: charging/not-charging badge + charger-port unlock. */
@Composable
private fun ChargingSection(
    chargingStatus: Int?,
    enabled: Boolean,
    controlViewModel: ControlViewModel
) {
    val isCharging = chargingStatus == 1
    FeatureCard(
        title  = stringResource(R.string.section_charging),
        icon   = Icons.Default.BatteryChargingFull,
        status = when {
            chargingStatus == null -> "--"
            isCharging             -> stringResource(R.string.charging_active)
            else                   -> stringResource(R.string.not_charging)
        },
        statusColor = when {
            chargingStatus == null -> MaterialTheme.colorScheme.onSurfaceVariant
            isCharging             -> MaterialTheme.colorScheme.primary
            else                   -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        ControlButton(
            text = stringResource(R.string.btn_unlock_charger),
            onClick = { controlViewModel.unlockCharger() },
            modifier = Modifier.fillMaxWidth().height(ButtonHeight),
            enabled = enabled,
            contentPadding = CompactPadding,
            icon = { Icon(Icons.Default.Lock, contentDescription = null,
                modifier = Modifier.size(16.dp)) }
        )
    }
}

/** Lights: on/off badge (alert when off at night) + light-reminder toggle. */
@Composable
private fun LightsSection(
    lights: com.daotranbang.vfsmart.data.model.Lights?,
    isNight: Boolean,
    lightReminderEnabled: Boolean,
    enabled: Boolean,
    controlViewModel: ControlViewModel
) {
    val lightsOn = lights != null && (lights.normalLight == 1 || lights.demiLight == 1)
    FeatureCard(
        title  = stringResource(R.string.card_lights),
        icon   = Icons.Default.Lightbulb,
        status = when {
            lights == null -> "--"
            lightsOn       -> stringResource(R.string.lights_on)
            else           -> stringResource(R.string.lights_off)
        },
        statusColor = when {
            lights == null -> MaterialTheme.colorScheme.onSurfaceVariant
            lightsOn       -> MaterialTheme.colorScheme.primary
            isNight        -> MaterialTheme.colorScheme.error
            else           -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        ToggleControlButton(
            text = stringResource(R.string.light_reminder),
            isOn = lightReminderEnabled,
            onToggle = { controlViewModel.toggleLightReminder() },
            modifier = Modifier.height(ButtonHeight),
            enabled = enabled,
            contentPadding = CompactPadding
        )
    }
}

/** Status-only items with no controls: doors + gear. */
@Composable
private fun VehicleStatusSection(
    doors: com.daotranbang.vfsmart.data.model.Doors?,
    gearDrive: Int?
) {
    val doorsOpen = doors != null &&
            (doors.frontLeft == 1 || doors.frontRight == 1 || doors.trunk == 1)
    val inDrive   = gearDrive == 1

    FeatureCard(
        title = stringResource(R.string.section_vehicle_status),
        icon  = Icons.Default.DirectionsCar
    ) {
        StatusRow(
            label = stringResource(R.string.card_doors),
            value = when {
                doors == null -> "--"
                doorsOpen     -> stringResource(R.string.doors_open)
                else          -> stringResource(R.string.doors_closed)
            },
            valueColor = when {
                doors == null -> MaterialTheme.colorScheme.onSurfaceVariant
                doorsOpen     -> MaterialTheme.colorScheme.error
                else          -> MaterialTheme.colorScheme.primary
            },
            icon = if (doorsOpen) Icons.Default.Warning else Icons.Default.Check
        )
        StatusRow(
            label = stringResource(R.string.card_gear),
            value = when {
                gearDrive == null -> "--"
                inDrive           -> stringResource(R.string.gear_drive)
                else              -> stringResource(R.string.gear_park)
            },
            valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = Icons.Default.DirectionsCar
        )
    }
}

/** Accessories: accessory power + inside cameras toggles (state is the toggle). */
@Composable
private fun AccessoriesSection(
    controls: com.daotranbang.vfsmart.data.model.Controls?,
    enabled: Boolean,
    controlViewModel: ControlViewModel
) {
    FeatureCard(
        title = stringResource(R.string.section_accessories),
        icon  = Icons.Default.PowerSettingsNew
    ) {
        val accessoryPowerOn = controls?.accessoryPower == 1
        ToggleControlButton(
            text = stringResource(R.string.accessory_power),
            isOn = accessoryPowerOn,
            onToggle = { controlViewModel.toggleAccessoryPower() },
            modifier = Modifier.height(ButtonHeight),
            enabled = enabled,
            contentPadding = CompactPadding
        )
        val camerasOn = controls?.insideCameras == 1
        ToggleControlButton(
            text = stringResource(R.string.inside_cameras),
            isOn = camerasOn,
            onToggle = { controlViewModel.toggleInsideCameras() },
            modifier = Modifier.height(ButtonHeight),
            enabled = enabled,
            contentPadding = CompactPadding
        )
    }
}

/** Horn/buzzer: duration badge in the header, slider + beep in the body. */
@Composable
private fun AudioSection(
    enabled: Boolean,
    controlViewModel: ControlViewModel
) {
    var buzzerDuration by remember { mutableStateOf(500) }
    FeatureCard(
        title  = stringResource(R.string.section_audio),
        icon   = Icons.Default.Notifications,
        status = "${buzzerDuration}ms",
        statusColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Slider(
            value = buzzerDuration.toFloat(),
            onValueChange = { buzzerDuration = it.toInt() },
            valueRange = 100f..2000f,
            steps = 18,
            modifier = Modifier.fillMaxWidth().height(24.dp)
        )
        ControlButton(
            text = stringResource(R.string.btn_beep_horn),
            onClick = { controlViewModel.beepHorn(buzzerDuration) },
            modifier = Modifier.fillMaxWidth().height(ButtonHeight),
            enabled = enabled,
            contentPadding = CompactPadding,
            icon = { Icon(Icons.Default.VolumeUp, contentDescription = null,
                modifier = Modifier.size(16.dp)) }
        )
    }
}

/** Screens without live car state, as one row of icon shortcuts. */
@Composable
private fun UtilitiesSection(
    onNavigateToCamera: () -> Unit,
    onNavigateToRtspCapture: () -> Unit,
    onNavigateToRedLight: () -> Unit,
    onNavigateToTrafficLight: () -> Unit
) {
    FeatureCard(
        title = stringResource(R.string.section_red_light_tools),
        icon  = Icons.Default.Traffic
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            UtilityIcon(Icons.Default.Videocam,
                stringResource(R.string.btn_view_camera), onNavigateToCamera)
            UtilityIcon(Icons.Default.VideoCall,
                stringResource(R.string.btn_rtsp_capture), onNavigateToRtspCapture)
            UtilityIcon(Icons.Default.Timer,
                stringResource(R.string.btn_red_light_detector), onNavigateToRedLight)
            UtilityIcon(Icons.Default.Traffic,
                stringResource(R.string.btn_traffic_light_live), onNavigateToTrafficLight)
        }
    }
}

@Composable
private fun UtilityIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(icon, contentDescription = contentDescription,
            modifier = Modifier.size(20.dp))
    }
}

// ── Building blocks ───────────────────────────────────────────────────────────

/**
 * Card that fuses a feature's status and controls: header row = icon + title +
 * optional colored status badge; body = the feature's controls.
 */
@Composable
private fun FeatureCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    status: String? = null,
    statusColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(GapSmall)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GapSmall)
            ) {
                Icon(icon, contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (status != null) statusColor else MaterialTheme.colorScheme.primary)
                Text(text = title, style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
                if (status != null) {
                    Text(text = status,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor)
                }
            }
            content()
        }
    }
}

/** Read-only status line inside a [FeatureCard] (for items with no controls). */
@Composable
private fun StatusRow(
    label: String,
    value: String,
    valueColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GapSmall)
    ) {
        Icon(icon, contentDescription = null,
            modifier = Modifier.size(16.dp), tint = valueColor)
        Text(text = label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun windowStateText(state: Int): String = when (state) {
    1    -> stringResource(R.string.window_state_closed)
    2    -> stringResource(R.string.window_state_open)
    else -> stringResource(R.string.window_state_unknown)
}

// ── Shared components ─────────────────────────────────────────────────────────

@Composable
private fun ConnectionIndicator(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.padding(end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically) {
        val (icon, label, color) = when (connectionState) {
            ConnectionState.Connected ->
                Triple(Icons.Default.CheckCircle,
                    stringResource(R.string.connected),
                    MaterialTheme.colorScheme.primary)
            ConnectionState.Disconnected ->
                Triple(Icons.Default.Cancel,
                    stringResource(R.string.offline),
                    MaterialTheme.colorScheme.error)
        }
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
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
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Notifications, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.nav_access_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f))
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) { Text(stringResource(R.string.nav_access_grant)) }
        }
    }
}
