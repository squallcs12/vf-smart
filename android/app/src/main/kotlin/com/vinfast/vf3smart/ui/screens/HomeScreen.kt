package com.vinfast.vf3smart.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.vinfast.vf3smart.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.vinfast.vf3smart.data.model.CarStatus
import com.vinfast.vf3smart.data.model.TpmsData
import com.vinfast.vf3smart.data.model.TpmsTire
import com.vinfast.vf3smart.data.network.WebSocketManager
import com.vinfast.vf3smart.navigation.NavigationNotificationService
import com.vinfast.vf3smart.navigation.NavigationState
import com.vinfast.vf3smart.ui.components.ControlButton
import com.vinfast.vf3smart.ui.components.StatusCard
import com.vinfast.vf3smart.viewmodel.CarStatusViewModel
import com.vinfast.vf3smart.viewmodel.ControlViewModel

// ODO colour palette
private val OdoBg        = Color(0xFF0A0A0A)
private val OdoDivider   = Color(0xFF1C1C1C)
private val OdoLabel     = Color(0xFF909090)
private val OdoInactive  = Color(0xFF686868)
private val OdoNormal    = Color(0xFFF0F0F0)
private val OdoGood      = Color(0xFF4CAF50)
private val OdoWarning   = Color(0xFFFFB300)
private val OdoAlert     = Color(0xFFEF5350)

@Composable
fun HomeScreen(
    onNavigateToControls: () -> Unit = {},
    onNavigateToDebug: () -> Unit = {},
    onNavigateToSetup: () -> Unit = {},
    modifier: Modifier = Modifier,
    statusViewModel: CarStatusViewModel = hiltViewModel(),
    controlViewModel: ControlViewModel = hiltViewModel()
) {
    var mirrorMode by rememberSaveable { mutableStateOf(true) }

    val carStatus by statusViewModel.carStatus.collectAsStateWithLifecycle()
    val connectionState by statusViewModel.connectionState.collectAsStateWithLifecycle()
    val operationState by controlViewModel.operationState.collectAsStateWithLifecycle()
    val navigationState by NavigationNotificationService.navigationState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (!isNotificationListenerGranted(context)) {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    // Trip clock: reset on foreground, tick every second
    var foregroundTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        foregroundTime = System.currentTimeMillis()
    }
    var tripTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tripTick++
        }
    }
    val tripText = remember(tripTick, foregroundTime) {
        val secs = (System.currentTimeMillis() - foregroundTime) / 1000
        val h = secs / 3600
        val m = (secs % 3600) / 60
        "${h}:${m.toString().padStart(2, '0')}"
    }

    // Hide system bars in mirror mode for true full-screen display
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        if (mirrorMode) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (mirrorMode) {
            MirrorContent(
                carStatus = carStatus,
                connectionState = connectionState,
                navigationState = navigationState,
                tripText = tripText
            )
        } else {
            FullContent(
                carStatus = carStatus,
                connectionState = connectionState,
                operationState = operationState,
                controlViewModel = controlViewModel,
                onNavigateToControls = onNavigateToControls,
                onNavigateToDebug = onNavigateToDebug,
                onNavigateToSetup = onNavigateToSetup
            )
        }

        // Mode toggle — bottom-right corner, always on top
        SmallFloatingActionButton(
            onClick = { mirrorMode = !mirrorMode },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            containerColor = if (mirrorMode)
                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = if (mirrorMode) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = if (mirrorMode) "Exit mirror mode" else "Enter mirror mode",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── Mirror mode: ODO instrument-cluster style, read-only ─────────────────────
//   Designed for 9-inch 1080p landscape screen (~960×540dp at xhdpi)

@Composable
private fun MirrorContent(
    carStatus: CarStatus?,
    connectionState: WebSocketManager.ConnectionState,
    navigationState: NavigationState,
    tripText: String,
    modifier: Modifier = Modifier
) {
    // ── Derived values ────────────────────────────────────────────────
    val clockText = carStatus?.time?.currentTime
        ?.substringAfter(" ")?.take(5) ?: "--:--"
    val isNight   = carStatus?.time?.isNight == true

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OdoBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Row 1: Clock | Navigation | Trip ───────────────────────
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                OdoClockCell(
                    time = clockText,
                    isNight = isNight,
                    hasData = carStatus != null,
                    modifier = Modifier.weight(1f)
                )
                OdoVerticalDivider()
                OdoNavCell(
                    navigationState = navigationState,
                    modifier = Modifier.weight(1f)
                )
                OdoVerticalDivider()
                OdoCell(
                    label = "TRIP",
                    value = tripText,
                    color = OdoNormal,
                    modifier = Modifier.weight(1f)
                )
            }

            OdoHorizontalDivider()

            // ── Row 2: MAP | TPMS | TBD ────────────────────────────────
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                OdoChargingCell(modifier = Modifier.weight(1f))
                OdoVerticalDivider()
                OdoTpmsCell(
                    tpms = carStatus?.tpms,
                    modifier = Modifier.weight(1f)
                )
                OdoVerticalDivider()
                OdoEmptyCell(modifier = Modifier.weight(1f))
            }
        }

        ConnectionDot(
            connectionState = connectionState,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        )
    }
}

@Composable
private fun OdoCell(
    label: String,
    value: String,
    color: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(10.dp))
        }
        Text(
            text = value,
            color = color,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = OdoLabel,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )
    }
}


@Composable
private fun OdoClockCell(
    time: String,
    isNight: Boolean,
    hasData: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (hasData) OdoNormal else OdoInactive
    val icon  = if (isNight) Icons.Default.NightsStay else Icons.Default.WbSunny

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(10.dp))
        Text(time, color = color, fontSize = 28.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Text("CLOCK", color = OdoLabel, fontSize = 10.sp,
            fontWeight = FontWeight.Medium, letterSpacing = 2.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun OdoNavCell(
    navigationState: NavigationState,
    modifier: Modifier = Modifier
) {
    val icon = when (navigationState.direction) {
        NavigationState.Direction.LEFT       -> Icons.Default.ArrowBack
        NavigationState.Direction.RIGHT      -> Icons.Default.ArrowForward
        NavigationState.Direction.U_TURN     -> Icons.Default.ArrowDownward
        NavigationState.Direction.ROUNDABOUT -> Icons.Default.Loop
        NavigationState.Direction.STRAIGHT   -> Icons.Default.ArrowUpward
    }
    val color = if (navigationState.isActive) OdoNormal else OdoInactive

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (navigationState.isActive) navigationState.distance else "--",
            color = color,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (navigationState.isActive) navigationState.maneuver else "NO NAVIGATION",
            color = OdoLabel,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OdoTpmsCell(
    tpms: TpmsData?,
    modifier: Modifier = Modifier
) {
    val cellColor = when {
        tpms == null      -> OdoInactive
        tpms.anyAlarm     -> OdoAlert
        tpms.anyLow       -> OdoWarning
        tpms.allValid     -> OdoGood
        else              -> OdoInactive
    }

    Box(modifier = modifier.fillMaxHeight().background(OdoBg)) {
        Image(
            painter = painterResource(R.drawable.ic_vf3_top),
            contentDescription = "VF3",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        // Numbers aligned to tire positions
        OdoTireNumber(tpms?.fl, cellColor,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 36.dp, top = 48.dp))
        OdoTireNumber(tpms?.fr, cellColor,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 36.dp, top = 48.dp))
        OdoTireNumber(tpms?.rl, cellColor,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 36.dp, bottom = 23.dp))
        OdoTireNumber(tpms?.rr, cellColor,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 36.dp, bottom = 23.dp))
    }
}

// Tire number for ODO mirror mode — no label, no unit
@Composable
private fun OdoTireNumber(
    tire: TpmsTire?,
    color: Color,
    modifier: Modifier = Modifier
) {
    val tireColor = when {
        tire == null || !tire.valid || tire.stale -> OdoInactive
        tire.alarm                                -> OdoAlert
        tire.pressureKpa < 180f                  -> OdoWarning
        else                                      -> color
    }
    val valueText = if (tire != null && tire.valid && !tire.stale)
        String.format("%.1f", tire.pressureKpa / 100f)
    else "_._"

    Text(
        text = valueText,
        color = tireColor,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
        modifier = modifier
    )
}

// TpmsTireValue used in calibration screen (with kPa unit)
@Composable
private fun TpmsTireValue(
    position: String,
    tire: TpmsTire?,
    color: Color
) {
    val tireColor = when {
        tire == null || !tire.valid || tire.stale -> OdoInactive
        tire.alarm                                -> OdoAlert
        tire.pressureKpa < 180f                  -> OdoWarning
        else                                      -> color
    }
    val valueText = if (tire != null && tire.valid && !tire.stale)
        String.format("%.0f", tire.pressureKpa)
    else "_._"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.defaultMinSize(minWidth = 52.dp)
    ) {
        Text(text = position, color = OdoLabel, fontSize = 9.sp,
            letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
        Text(text = valueText, color = tireColor, fontSize = 20.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
        Text(text = "kPa", color = OdoLabel, fontSize = 8.sp, letterSpacing = 1.sp)
    }
}

// ── Nearby charging stations (Google Places API) ─────────────────────────────

private const val PLACES_API_KEY = "AIzaSyCbWBCfilzapl8xTfZc8KpTMGhfX055rQg"

private data class NearbyStation(val name: String, val distanceM: Double)

private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2).let { it * it } +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
        Math.sin(dLon / 2).let { it * it }
    return r * 2 * Math.asin(Math.sqrt(a))
}

private suspend fun fetchNearbyChargers(lat: Double, lon: Double): List<NearbyStation> =
    withContext(Dispatchers.IO) {
        try {
            val body = """
                {
                  "includedTypes": ["electric_vehicle_charging_station"],
                  "maxResultCount": 2,
                  "rankPreference": "DISTANCE",
                  "locationRestriction": {
                    "circle": {
                      "center": { "latitude": $lat, "longitude": $lon },
                      "radius": 5000.0
                    }
                  }
                }
            """.trimIndent().toByteArray()
            val conn = (java.net.URL("https://places.googleapis.com/v1/places:searchNearby")
                .openConnection() as java.net.HttpURLConnection).also {
                it.requestMethod = "POST"
                it.setRequestProperty("Content-Type", "application/json")
                it.setRequestProperty("X-Goog-Api-Key", PLACES_API_KEY)
                it.setRequestProperty("X-Goog-FieldMask", "places.displayName,places.location")
                it.connectTimeout = 8000; it.readTimeout = 10000
                it.doOutput = true
                it.outputStream.write(body)
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val json = stream.bufferedReader().readText()
            android.util.Log.d("VF3Charging", "Places ($code): $json")
            if (code !in 200..299) return@withContext emptyList()
            val places = org.json.JSONObject(json).optJSONArray("places") ?: return@withContext emptyList()
            (0 until places.length()).map { i ->
                val place = places.getJSONObject(i)
                val name = place.optJSONObject("displayName")?.optString("text")
                    ?.takeIf { it.isNotBlank() } ?: "Charging Station"
                val loc = place.getJSONObject("location")
                NearbyStation(name, haversineM(lat, lon, loc.getDouble("latitude"), loc.getDouble("longitude")))
            }
        } catch (e: Exception) {
            android.util.Log.e("VF3Charging", "fetchNearbyChargers failed", e)
            emptyList()
        }
    }

@Composable
private fun OdoChargingCell(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var stations by remember { mutableStateOf<List<NearbyStation>>(emptyList()) }
    var statusText by remember { mutableStateOf("LOCATING...") }
    var refreshKey by remember { mutableIntStateOf(0) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> if (results.values.any { it }) refreshKey++ }

    LaunchedEffect(refreshKey) {
        val hasPerm = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPerm) {
            statusText = "NO PERMISSION"
            permLauncher.launch(arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ))
            return@LaunchedEffect
        }

        statusText = "LOCATING..."
        @SuppressLint("MissingPermission")
        val location = withContext(Dispatchers.IO) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        }

        if (location == null) { statusText = "NO GPS SIGNAL"; return@LaunchedEffect }

        statusText = "SEARCHING..."
        val result = fetchNearbyChargers(location.latitude, location.longitude)
        stations = result
        statusText = if (result.isEmpty()) "NONE NEARBY" else "CHARGING"
    }

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Bolt,
            contentDescription = null,
            tint = if (stations.isNotEmpty()) OdoGood else OdoInactive,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(10.dp))
        if (stations.isEmpty()) {
            Text(
                text = statusText,
                color = OdoInactive,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
        } else {
            stations.forEachIndexed { i, station ->
                if (i > 0) Spacer(Modifier.height(12.dp))
                Text(
                    text = station.name.uppercase(),
                    color = OdoNormal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .basicMarquee()
                )
                val distText = if (station.distanceM < 1000)
                    "${station.distanceM.toInt()} M"
                else
                    String.format(java.util.Locale.US, "%.1f KM", station.distanceM / 1000)
                Text(
                    text = distText,
                    color = OdoGood,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(text = "CHARGING", color = OdoLabel, fontSize = 10.sp, letterSpacing = 2.sp)
    }
}

@Composable
private fun OdoEmptyCell(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxHeight())
}

@Composable
private fun OdoVerticalDivider() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(OdoDivider)
    )
}

@Composable
private fun OdoHorizontalDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(OdoDivider)
    )
}

// ── Full mode: scrollable layout with header + all quick controls ─────────────

@Composable
private fun FullContent(
    carStatus: CarStatus?,
    connectionState: WebSocketManager.ConnectionState,
    operationState: ControlViewModel.OperationState,
    controlViewModel: ControlViewModel,
    onNavigateToControls: () -> Unit,
    onNavigateToDebug: () -> Unit,
    onNavigateToSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    ConnectionIndicator(connectionState)
                    IconButton(onClick = onNavigateToSetup) {
                        Icon(Icons.Default.Settings, contentDescription = "Setup")
                    }
                    IconButton(onClick = onNavigateToDebug) {
                        Icon(
                            Icons.Default.BugReport,
                            contentDescription = "Debug",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        val context = LocalContext.current
        val isConnected = connectionState == WebSocketManager.ConnectionState.Connected
        val isLoading = operationState is ControlViewModel.OperationState.Loading
        val enabled = isConnected && !isLoading

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Disconnected banner
            if (!isConnected) {
                DisconnectedBanner(connectionState)
            }

            // Notification access banner (needed for nav directions)
            if (!isNotificationListenerGranted(context)) {
                NotificationAccessBanner()
            }

            // Status grid — 2 columns
            StatusGrid(
                carStatus = carStatus,
                columns = 2,
                modifier = Modifier.fillMaxWidth()
            )

            // Quick actions
            QuickActionsCard(
                carStatus = carStatus,
                enabled = enabled,
                onLock = { controlViewModel.lockCar() },
                onUnlock = { controlViewModel.unlockCar() },
                onBeep = { controlViewModel.beepHorn() },
                onCloseWindows = { controlViewModel.closeWindows() }
            )

            // More controls
            OutlinedButton(
                onClick = onNavigateToControls,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
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

// ── Status grid (shared, columns configurable) ────────────────────────────────

@Composable
private fun StatusGrid(
    carStatus: CarStatus?,
    columns: Int,
    modifier: Modifier = Modifier
) {
    val isLocked   = carStatus?.carLockState == "locked"
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
                    Icon(
                        if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null, modifier = Modifier.size(28.dp),
                        tint = if (carStatus == null) dim else LocalContentColor.current
                    )
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
                    Icon(
                        if (windowsOpen) Icons.Default.Warning else Icons.Default.Check,
                        contentDescription = null, modifier = Modifier.size(28.dp),
                        tint = if (carStatus == null) dim else LocalContentColor.current
                    )
                }
            )
        },
        {
            StatusCard(
                title = "Charging",
                value = if (carStatus == null) "--" else if (isCharging) "Charging" else "Not Charging",
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = {
                    Icon(
                        Icons.Default.BatteryChargingFull,
                        contentDescription = null, modifier = Modifier.size(28.dp),
                        tint = when {
                            carStatus == null -> dim
                            isCharging -> MaterialTheme.colorScheme.primary
                            else -> dim
                        }
                    )
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
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = null, modifier = Modifier.size(28.dp),
                        tint = when {
                            carStatus == null -> dim
                            lightsOn -> MaterialTheme.colorScheme.primary
                            else -> dim
                        }
                    )
                }
            )
        },
        {
            StatusCard(
                title = "Gear",
                value = if (carStatus == null) "--" else if (inDrive) "Drive" else "Park",
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = null, modifier = Modifier.size(28.dp),
                        tint = if (carStatus == null) dim else LocalContentColor.current
                    )
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
                    Icon(
                        if (doorsOpen) Icons.Default.Warning else Icons.Default.Check,
                        contentDescription = null, modifier = Modifier.size(28.dp),
                        tint = if (carStatus == null) dim else LocalContentColor.current
                    )
                }
            )
        }
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        cards.chunked(columns).forEach { rowCards ->
            Row(
                modifier = if (columns == 3)
                    Modifier.weight(1f).fillMaxWidth()
                else
                    Modifier.fillMaxWidth().height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowCards.forEach { card -> card() }
            }
        }
    }
}

// ── Quick actions card (full mode only) ───────────────────────────────────────

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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Quick Actions",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ControlButton(
                    text = "Lock", onClick = onLock,
                    modifier = Modifier.weight(1f), enabled = enabled,
                    icon = { Icon(Icons.Default.Lock, contentDescription = null) }
                )
                ControlButton(
                    text = "Unlock", onClick = onUnlock,
                    modifier = Modifier.weight(1f), enabled = enabled,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    icon = { Icon(Icons.Default.LockOpen, contentDescription = null) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ControlButton(
                    text = "Beep", onClick = onBeep,
                    modifier = Modifier.weight(1f), enabled = enabled,
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    icon = { Icon(Icons.Default.Notifications, contentDescription = null) }
                )
                val windowLabel = if (carStatus?.windowCloseActive == true)
                    "Closing (${carStatus.windowCloseRemainingMs / 1000}s)" else "Close Windows"
                ControlButton(
                    text = windowLabel, onClick = onCloseWindows,
                    modifier = Modifier.weight(1f), enabled = enabled,
                    icon = { Icon(Icons.Default.Close, contentDescription = null) }
                )
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
    Row(
        modifier = modifier.padding(end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (connectionState is WebSocketManager.ConnectionState.Error)
                    Icons.Default.Error else Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Device Disconnected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Navigation Access Required",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "Grant notification access to show turn-by-turn directions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) {
                Text("Grant")
            }
        }
    }
}

@Composable
private fun ConnectionDot(
    connectionState: WebSocketManager.ConnectionState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(8.dp)
            .background(
                color = when (connectionState) {
                    WebSocketManager.ConnectionState.Connected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                },
                shape = CircleShape
            )
    )
}
