package com.daotranbang.vfsmart.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
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
import com.daotranbang.vfsmart.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import com.daotranbang.vfsmart.data.model.CarStatus
import com.daotranbang.vfsmart.data.model.TpmsData
import com.daotranbang.vfsmart.data.model.TpmsTire
import com.daotranbang.vfsmart.data.network.WebSocketManager
import com.daotranbang.vfsmart.navigation.NavigationNotificationService
import com.daotranbang.vfsmart.navigation.NavigationState
import com.daotranbang.vfsmart.ui.components.ControlButton
import com.daotranbang.vfsmart.ui.components.StatusCard
import com.daotranbang.vfsmart.viewmodel.CarStatusViewModel
import com.daotranbang.vfsmart.viewmodel.ControlViewModel

// ODO colour palette
private val OdoBg        = Color(0xFF0A0A0A)
private val OdoDivider   = Color(0xFF3A3A3A)
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

        // Mode toggle — bottom-right, sits over the status bar
        SmallFloatingActionButton(
            onClick = { mirrorMode = !mirrorMode },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
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
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OdoBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── 2×4 grid ──────────────────────────────────────────────
            var sharedSpeedLimit by remember { mutableStateOf<Int?>(null) }

            Column(modifier = Modifier.weight(1f)) {
                // ── Row 1: Location | Navigation | GPS Speed | Trip ───
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    OdoLocationCell(modifier = Modifier.weight(1f))
                    OdoVerticalDivider()
                    OdoNavCell(navigationState = navigationState, modifier = Modifier.weight(1f))
                    OdoVerticalDivider()
                    OdoGpsSpeedCell(
                        speedLimit = sharedSpeedLimit,
                        modifier = Modifier.weight(1f)
                    )
                    OdoVerticalDivider()
                    OdoTripCell(tripText = tripText, modifier = Modifier.weight(1f))
                }

                OdoHorizontalDivider()

                // ── Row 2: Charging | TPMS | Speed Limit | Clock ─────
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    OdoChargingCell(modifier = Modifier.weight(1f))
                    OdoVerticalDivider()
                    OdoTpmsCell(tpms = carStatus?.tpms, modifier = Modifier.weight(1f))
                    OdoVerticalDivider()
                    OdoSpeedLimitCell(
                        onSpeedLimitChanged = { sharedSpeedLimit = it },
                        modifier = Modifier.weight(1f)
                    )
                    OdoVerticalDivider()
                    OdoClockCell(modifier = Modifier.weight(1f))
                }
            }

            OdoHorizontalDivider()

            // ── Bottom status bar: Lock | Doors | Gear | Lights | Battery
            OdoStatusBar(carStatus = carStatus)
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


@SuppressLint("MissingPermission")
@Composable
private fun OdoLocationCell(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    fun hasPerm() = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    var permGranted by remember { mutableStateOf(hasPerm()) }
    var location    by remember { mutableStateOf<android.location.Location?>(null) }
    var street      by remember { mutableStateOf<String?>(null) }
    var district    by remember { mutableStateOf<String?>(null) }
    var province    by remember { mutableStateOf<String?>(null) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> if (results.values.any { it }) permGranted = true }

    DisposableEffect(permGranted) {
        if (!permGranted) {
            permLauncher.launch(arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ))
            onDispose {}
        } else {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener = android.location.LocationListener { loc -> location = loc }
            val seed = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            if (seed != null) location = seed
            try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30_000L, 100f, listener) } catch (_: Exception) {}
            try { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30_000L, 100f, listener) } catch (_: Exception) {}
            onDispose { lm.removeUpdates(listener) }
        }
    }

    LaunchedEffect(location) {
        val loc = location ?: return@LaunchedEffect
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                if (!android.location.Geocoder.isPresent()) return@withContext
                @Suppress("DEPRECATION")
                val addresses = android.location.Geocoder(context, java.util.Locale.getDefault())
                    .getFromLocation(loc.latitude, loc.longitude, 1)
                val addr = addresses?.firstOrNull() ?: return@withContext
                street   = addr.thoroughfare
                district = addr.subAdminArea ?: addr.subLocality ?: addr.locality
                province = addr.adminArea
            } catch (e: Exception) {
                android.util.Log.w("VF3Location", "Geocode failed: ${e.message}")
            }
        }
    }

    val hasData = location != null
    Column(
        modifier = modifier.fillMaxHeight().padding(top = 16.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = OdoInactive,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = province?.uppercase() ?: if (!hasData) "LOCATING..." else "--",
            color = OdoLabel,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            letterSpacing = 2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = district ?: "--",
            color = OdoInactive,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = street ?: "--",
            color = if (hasData) OdoNormal else OdoInactive,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = 0.5.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

private data class WeatherData(val current: Int, val nextHour: Int)

private suspend fun fetchWeather(lat: Double, lon: Double): WeatherData? =
    kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current_weather=true&hourly=weathercode&forecast_days=1"
            )
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 10_000
                setRequestProperty("User-Agent", "VF3Smart/1.0")
            }
            val json    = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
            val current = json.getJSONObject("current_weather").getInt("weathercode")
            val codes   = json.getJSONObject("hourly").getJSONArray("weathercode")
            val nextIdx = (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) + 1) % 24
            val nextHour = codes.getInt(nextIdx)
            WeatherData(current, nextHour)
        } catch (e: Exception) {
            null  // silent fail
        }
    }

// WMO weather code → Material icon
private fun weatherIconFor(code: Int?, isNight: Boolean): ImageVector = when {
    code == null          -> if (isNight) Icons.Default.NightsStay else Icons.Default.WbSunny
    code == 0             -> if (isNight) Icons.Default.NightsStay else Icons.Default.WbSunny  // clear
    code in 1..3          -> Icons.Default.WbCloudy    // partly cloudy
    code in 45..48        -> Icons.Default.Cloud        // fog
    code in 51..67        -> Icons.Default.WaterDrop    // drizzle / rain
    code in 71..77        -> Icons.Default.AcUnit       // snow
    code in 80..82        -> Icons.Default.WaterDrop    // rain showers
    code >= 95            -> Icons.Default.Bolt         // thunderstorm
    else                  -> Icons.Default.Cloud
}

@SuppressLint("MissingPermission")
@Composable
private fun OdoClockCell(modifier: Modifier = Modifier) {
    // Clock tick
    var now by remember { mutableStateOf(java.util.Calendar.getInstance()) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); now = java.util.Calendar.getInstance() }
    }

    // Weather via Open-Meteo (no API key required)
    val context = LocalContext.current
    var weather         by remember { mutableStateOf<WeatherData?>(null) }
    var location        by remember { mutableStateOf<android.location.Location?>(null) }
    var lastFetchTime   by remember { mutableStateOf(0L) }

    val hasPerm = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    DisposableEffect(hasPerm) {
        if (!hasPerm) { onDispose {} } else {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener = android.location.LocationListener { loc -> location = loc }
            val seed = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (seed != null) location = seed
            try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60_000L, 500f, listener) } catch (_: Exception) {}
            try { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60_000L, 500f, listener) } catch (_: Exception) {}
            onDispose { lm.removeUpdates(listener) }
        }
    }

    LaunchedEffect(location) {
        val loc = location ?: return@LaunchedEffect
        val elapsed = System.currentTimeMillis() - lastFetchTime
        val interval = if (weather != null) 30 * 60 * 1000L else 5 * 60 * 1000L
        if (elapsed < interval) return@LaunchedEffect
        lastFetchTime = System.currentTimeMillis()
        val result = fetchWeather(loc.latitude, loc.longitude)
        if (result != null) weather = result  // keep last good value on failure
    }

    val hour         = now.get(java.util.Calendar.HOUR_OF_DAY)
    val minute       = now.get(java.util.Calendar.MINUTE)
    val isNight      = hour < 6 || hour >= 18
    val nextIsNight  = (hour + 1) % 24 < 6 || (hour + 1) % 24 >= 18
    val time         = String.format("%02d:%02d", hour, minute)
    val currentIcon  = weatherIconFor(weather?.current, isNight)
    val nextIcon     = weatherIconFor(weather?.nextHour, nextIsNight)

    Column(
        modifier = modifier.fillMaxSize().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Weather row — top
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(currentIcon, contentDescription = "now", tint = OdoNormal, modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null,
                tint = OdoInactive, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Icon(nextIcon, contentDescription = "+1h", tint = OdoInactive, modifier = Modifier.size(36.dp))
        }

        // Time — middle, fills remaining space
        Text(
            text = time,
            color = OdoNormal,
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = 2.sp
        )

        // Label — bottom
        Text(
            text = "CLOCK",
            color = OdoLabel,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun OdoGpsSpeedCell(
    speedLimit: Int? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    fun hasPerm() = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    var permGranted by remember { mutableStateOf(hasPerm()) }
    var speedKmh    by remember { mutableFloatStateOf(0f) }
    var hasData     by remember { mutableStateOf(false) }

    DisposableEffect(permGranted) {
        if (!permGranted) { onDispose {} } else {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener = android.location.LocationListener { loc ->
                if (loc.hasSpeed()) { speedKmh = loc.speed * 3.6f; hasData = true }
            }
            val seed = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (seed != null && seed.hasSpeed()) { speedKmh = seed.speed * 3.6f; hasData = true }
            try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener) } catch (_: Exception) {}
            onDispose { lm.removeUpdates(listener) }
        }
    }

    val speed = speedKmh.toInt()
    val overLimit       = speedLimit != null && speed >= speedLimit
    val overLimitPlus5  = speedLimit != null && speed >= speedLimit + 5
    val speedColor = when {
        !hasData        -> OdoInactive
        overLimitPlus5  -> OdoAlert
        overLimit       -> OdoWarning
        else            -> OdoNormal
    }
    val speedFontSize = if (speed >= 100) 64.sp else 80.sp

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (hasData) "$speed" else "--",
            color = speedColor,
            fontSize = speedFontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "KM/H",
            color = OdoLabel,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun OdoTripCell(
    tripText: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    fun hasPerm() = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    var permGranted by remember { mutableStateOf(hasPerm()) }
    var hasGps     by remember { mutableStateOf(false) }
    var speedKmh   by remember { mutableFloatStateOf(0f) }

    DisposableEffect(permGranted) {
        if (!permGranted) { onDispose {} } else {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener = android.location.LocationListener { loc ->
                val spd = if (loc.hasSpeed()) loc.speed * 3.6f else -1f
                android.util.Log.d("VF3TripCell", "location update: hasSpeed=${loc.hasSpeed()} speed=$spd km/h provider=${loc.provider}")
                if (loc.hasSpeed()) { speedKmh = loc.speed * 3.6f; hasGps = true }
            }
            val seed = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            android.util.Log.d("VF3TripCell", "seed location: $seed hasSpeed=${seed?.hasSpeed()} speed=${seed?.speed}")
            if (seed != null && seed.hasSpeed()) { speedKmh = seed.speed * 3.6f; hasGps = true }
            try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener) } catch (_: Exception) {}
            onDispose { lm.removeUpdates(listener) }
        }
    }

    var colonVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); colonVisible = !colonVisible }
    }

    val isStopped = hasGps && speedKmh < 1f
    var hasMovedOnce by remember { mutableStateOf(false) }
    var countdownSecs by remember { mutableIntStateOf(15) }
    var countdownActive by remember { mutableStateOf(false) }

    LaunchedEffect(isStopped) {
        android.util.Log.d("VF3TripCell", "isStopped=$isStopped hasGps=$hasGps speedKmh=$speedKmh hasMovedOnce=$hasMovedOnce")
        if (speedKmh >= 1f) {
            android.util.Log.d("VF3TripCell", "hasMovedOnce set to true (speedKmh=$speedKmh)")
            hasMovedOnce = true
        }
        if (isStopped && hasMovedOnce) {
            android.util.Log.d("VF3TripCell", "countdown START")
            countdownSecs = 15
            countdownActive = true
            while (countdownSecs > 0) {
                delay(1000)
                countdownSecs--
                android.util.Log.d("VF3TripCell", "countdown tick: $countdownSecs")
            }
            android.util.Log.d("VF3TripCell", "countdown DONE")
            countdownActive = false
        } else {
            android.util.Log.d("VF3TripCell", "countdown RESET (car moved or no prior movement)")
            countdownActive = false
            countdownSecs = 15
        }
    }

    val showCountdown = countdownActive

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (showCountdown) {
            Text(
                text = "RED LIGHT",
                color = OdoLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "$countdownSecs",
                color = OdoAlert,
                fontSize = 112.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp
            )
        } else {
            Text(
                text = "TRIP",
                color = OdoLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val parts = tripText.split(":")
                Text(
                    text = parts[0],
                    color = OdoNormal,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = ":",
                    color = if (colonVisible) OdoNormal else Color.Transparent,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = parts.getOrElse(1) { "00" },
                    color = OdoNormal,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
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
    BoxWithConstraints(modifier = modifier.fillMaxHeight().background(OdoBg).padding(10.dp)) {
        val sideW = maxWidth * 0.20f
        val frontPad = maxHeight * 0.10f
        val rearPad  = maxHeight * 0.07f

        Row(modifier = Modifier.fillMaxSize()) {
            // Left column: FL (front) + RL (rear)
            Column(
                modifier = Modifier.width(sideW).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                OdoTireNumber(tpms?.fl, modifier = Modifier.padding(top = frontPad))
                OdoTireNumber(tpms?.rl, modifier = Modifier.padding(bottom = rearPad))
            }

            // Center: car image fills remaining width
            Image(
                painter = painterResource(R.drawable.ic_vf3_top),
                contentDescription = "VF3",
                contentScale = ContentScale.Fit,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )

            // Right column: FR (front) + RR (rear)
            Column(
                modifier = Modifier.width(sideW).fillMaxHeight(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                OdoTireNumber(tpms?.fr, modifier = Modifier.padding(top = frontPad))
                OdoTireNumber(tpms?.rr, modifier = Modifier.padding(bottom = rearPad))
            }
        }
    }
}

// Tire number for ODO mirror mode — color driven by each tire's own pressure value
@Composable
private fun OdoTireNumber(
    tire: TpmsTire?,
    modifier: Modifier = Modifier
) {
    val color = when {
        tire == null || !tire.valid || tire.stale -> OdoInactive
        tire.alarm                                -> OdoAlert
        tire.pressureKpa < 1.8f                  -> OdoWarning
        else                                      -> OdoGood
    }
    val valueText = if (tire != null && tire.valid && !tire.stale)
        String.format("%.1f", tire.pressureKpa)
    else "_._"

    Text(
        text = valueText,
        color = color,
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
        tire.pressureKpa < 1.8f                  -> OdoWarning
        else                                      -> color
    }
    val valueText = if (tire != null && tire.valid && !tire.stale)
        String.format("%.1f", tire.pressureKpa)
    else "_._"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.defaultMinSize(minWidth = 52.dp)
    ) {
        Text(text = position, color = OdoLabel, fontSize = 9.sp,
            letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
        Text(text = valueText, color = tireColor, fontSize = 20.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
    }
}

// ── Nearby charging stations (Google My Maps KML) ────────────────────────────

private const val CHARGER_KML_URL =
    "https://www.google.com/maps/d/kml?mid=1iIZ3L3KEKU0fg5XsIQ6hbRl7NVY8JNA&forcekml=1"
private const val KML_CACHE_FILE = "charger_kml.json"

private data class NearbyStation(val name: String, val distanceM: Double)

/** L1 in-memory cache — survives recompositions, cleared on process death. */
private var kmlMemCache: List<Pair<String, Pair<Double, Double>>>? = null

private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2).let { it * it } +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
        Math.sin(dLon / 2).let { it * it }
    return r * 2 * Math.asin(Math.sqrt(a))
}

private fun parseKml(kml: String): List<Pair<String, Pair<Double, Double>>> {
    val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        .newDocumentBuilder().parse(kml.byteInputStream())
    val placemarks = doc.getElementsByTagName("Placemark")
    val result = mutableListOf<Pair<String, Pair<Double, Double>>>()
    for (i in 0 until placemarks.length) {
        val pm = placemarks.item(i) as org.w3c.dom.Element
        val name = pm.getElementsByTagName("name").item(0)
            ?.textContent?.trim().takeIf { !it.isNullOrBlank() } ?: continue
        val coordText = pm.getElementsByTagName("coordinates").item(0)
            ?.textContent?.trim() ?: continue
        // KML coordinates: lon,lat[,alt]
        val parts = coordText.split(",")
        val lon = parts.getOrNull(0)?.trim()?.toDoubleOrNull() ?: continue
        val lat = parts.getOrNull(1)?.trim()?.toDoubleOrNull() ?: continue
        result.add(name to (lat to lon))
    }
    return result
}

private fun readKmlFileCache(context: Context): List<Pair<String, Pair<Double, Double>>>? =
    try {
        val file = java.io.File(context.cacheDir, KML_CACHE_FILE)
        if (!file.exists()) null
        else {
            val arr = org.json.JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                obj.getString("name") to (obj.getDouble("lat") to obj.getDouble("lon"))
            }
        }
    } catch (e: Exception) {
        android.util.Log.w("VF3Charging", "File cache read failed", e)
        null
    }

private fun writeKmlFileCache(context: Context, stations: List<Pair<String, Pair<Double, Double>>>) {
    try {
        val arr = org.json.JSONArray()
        stations.forEach { (name, coords) ->
            arr.put(org.json.JSONObject().apply {
                put("name", name)
                put("lat", coords.first)
                put("lon", coords.second)
            })
        }
        java.io.File(context.cacheDir, KML_CACHE_FILE).writeText(arr.toString())
    } catch (e: Exception) {
        android.util.Log.w("VF3Charging", "File cache write failed", e)
    }
}

/**
 * Emits station list immediately from cache (memory → file), then emits again
 * after fetching fresh KML from the network and updating both caches.
 */
private fun kmlStationsFlow(context: Context) = flow {
    // L1: memory
    val mem = kmlMemCache
    if (mem != null) {
        emit(mem)
    } else {
        // L2: file
        readKmlFileCache(context)?.also { cached ->
            kmlMemCache = cached
            emit(cached)
        }
    }
    // Always async-refresh from network
    try {
        val conn = (java.net.URL(CHARGER_KML_URL).openConnection()
                as java.net.HttpURLConnection).also {
            it.connectTimeout = 10_000
            it.readTimeout = 15_000
            it.setRequestProperty("User-Agent", "VF3Smart/1.0")
        }
        val fresh = parseKml(conn.inputStream.bufferedReader().readText())
        if (fresh.isNotEmpty()) {
            kmlMemCache = fresh
            writeKmlFileCache(context, fresh)
            android.util.Log.d("VF3Charging", "KML refreshed: ${fresh.size} stations")
            emit(fresh)
        }
    } catch (e: Exception) {
        android.util.Log.e("VF3Charging", "KML network refresh failed", e)
    }
}.flowOn(Dispatchers.IO)

private fun sortNearby(all: List<Pair<String, Pair<Double, Double>>>, lat: Double, lon: Double) =
    all.map { (name, coords) -> NearbyStation(name, haversineM(lat, lon, coords.first, coords.second)) }
        .sortedBy { it.distanceM }
        .take(1)

@SuppressLint("MissingPermission")
@Composable
private fun OdoChargingCell(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var stations by remember { mutableStateOf<List<NearbyStation>>(emptyList()) }
    var statusText by remember { mutableStateOf("LOCATING...") }

    fun hasPerm() = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    var permGranted by remember { mutableStateOf(hasPerm()) }

    // KML data as Compose state so location effect reacts to it
    var kmlData by remember { mutableStateOf(kmlMemCache ?: emptyList()) }

    // On foreground: re-check permission + refresh KML from network
    var foregroundKey by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        permGranted = hasPerm()
        foregroundKey++
    }

    // KML refresh: only on foreground — emits cache first, then network update
    LaunchedEffect(foregroundKey) {
        kmlStationsFlow(context).collect { fresh -> kmlData = fresh }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> if (results.values.any { it }) permGranted = true }

    // Live location updates — register/unregister with permission state
    var location by remember { mutableStateOf<android.location.Location?>(null) }
    DisposableEffect(permGranted) {
        if (!permGranted) {
            statusText = "NO PERMISSION"
            permLauncher.launch(arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ))
            onDispose {}
        } else {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener = android.location.LocationListener { loc -> location = loc }
            statusText = "LOCATING..."
            val seed = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            if (seed != null) location = seed else statusText = "NO GPS SIGNAL"
            try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,     30_000L, 100f, listener) } catch (_: Exception) {}
            try { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,  30_000L, 100f, listener) } catch (_: Exception) {}
            onDispose { lm.removeUpdates(listener) }
        }
    }

    // Re-sort on location change using cached KML — no network call
    LaunchedEffect(location, kmlData) {
        val loc = location ?: return@LaunchedEffect
        if (kmlData.isEmpty()) { statusText = "SEARCHING..."; return@LaunchedEffect }
        val result = sortNearby(kmlData, loc.latitude, loc.longitude)
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
            val station = stations.first()
            Text(
                text = station.name.uppercase(),
                color = OdoNormal,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
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
}

private suspend fun fetchSpeedLimit(lat: Double, lon: Double): Int? =
    kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val query = "[out:json][timeout:5];way(around:30,${lat},${lon})[highway][maxspeed];out tags 1;"
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = java.net.URL("https://overpass-api.de/api/interpreter?data=$encoded")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout    = 8_000
                setRequestProperty("User-Agent", "VF3Smart/1.0")
            }
            val json = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
            val elements = json.getJSONArray("elements")
            if (elements.length() == 0) return@withContext null
            val tags = elements.getJSONObject(0).getJSONObject("tags")
            val raw = tags.optString("maxspeed", "").trim()
            val num = Regex("(\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@withContext null
            if (raw.contains("mph", ignoreCase = true)) (num * 1.60934).toInt() else num
        } catch (e: Exception) {
            android.util.Log.w("VF3SpeedLimit", "Fetch failed: ${e.message}")
            null
        }
    }

@SuppressLint("MissingPermission")
@Composable
private fun OdoSpeedLimitCell(
    onSpeedLimitChanged: (Int?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    fun hasPerm() = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    var permGranted by remember { mutableStateOf(hasPerm()) }
    var location   by remember { mutableStateOf<android.location.Location?>(null) }
    var speedLimit by remember { mutableStateOf<Int?>(null) }
    var querying   by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> if (results.values.any { it }) permGranted = true }

    DisposableEffect(permGranted) {
        if (!permGranted) {
            permLauncher.launch(arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ))
            onDispose {}
        } else {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener = android.location.LocationListener { loc -> location = loc }
            val seed = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            if (seed != null) location = seed
            try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30_000L, 100f, listener) } catch (_: Exception) {}
            try { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30_000L, 100f, listener) } catch (_: Exception) {}
            onDispose { lm.removeUpdates(listener) }
        }
    }

    LaunchedEffect(location) {
        val loc = location ?: return@LaunchedEffect
        querying = true
        speedLimit = fetchSpeedLimit(loc.latitude, loc.longitude)
        querying = false
        onSpeedLimitChanged(speedLimit)
    }

    val valueText = speedLimit?.toString() ?: if (querying) "..." else "--"

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize()
    ) {
        // Outer pastel red ring — aspectRatio(1f) ensures a perfect circle
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .padding(10.dp)
                .background(Color(0xFFE57373), CircleShape)
        ) {
            // Pastel white interior — padding controls ring thickness
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
                    .background(Color(0xFFFFF3F3), CircleShape)
            ) {
                Text(
                    text = valueText,
                    color = Color(0xFF5D4040),
                    fontSize = if (speedLimit != null && speedLimit!! >= 100) 64.sp else 80.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ── ODO bottom status bar ─────────────────────────────────────────────────────

@Composable
private fun OdoStatusBar(carStatus: CarStatus?, modifier: Modifier = Modifier) {
    val isLocked    = carStatus?.carLockState == "locked"
    val doorsOpen   = carStatus != null &&
            (carStatus.doors.frontLeft == 1 || carStatus.doors.frontRight == 1 || carStatus.doors.trunk == 1)
    val inDrive     = carStatus?.sensors?.gearDrive == 1
    val lightsOn    = carStatus != null &&
            (carStatus.lights.normalLight == 1 || carStatus.lights.demiLight == 1)
    val isNight     = carStatus?.time?.isNight == true
    val battVoltage = carStatus?.sensors?.batteryVoltage?.let { "${it}V" } ?: "--"

    Row(
        modifier = modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusBarItem(
            icon = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
            label = if (carStatus == null) "--" else if (isLocked) "LOCKED" else "UNLOCKED",
            color = if (carStatus == null) OdoInactive else if (isLocked) OdoGood else OdoWarning,
            modifier = Modifier.weight(1f)
        )
        StatusBarDivider()
        StatusBarItem(
            icon = if (doorsOpen) Icons.Default.Warning else Icons.Default.Check,
            label = if (carStatus == null) "--" else if (doorsOpen) "OPEN" else "CLOSED",
            color = if (carStatus == null) OdoInactive else if (doorsOpen) OdoAlert else OdoGood,
            modifier = Modifier.weight(1f)
        )
        StatusBarDivider()
        StatusBarItem(
            icon = Icons.Default.DirectionsCar,
            label = if (carStatus == null) "--" else if (inDrive) "DRIVE" else "PARK",
            color = if (carStatus == null) OdoInactive else if (inDrive) OdoNormal else OdoInactive,
            modifier = Modifier.weight(1f)
        )
        StatusBarDivider()
        StatusBarItem(
            icon = Icons.Default.Lightbulb,
            label = if (carStatus == null) "--" else if (lightsOn) "ON" else "OFF",
            color = when {
                carStatus == null -> OdoInactive
                lightsOn          -> OdoNormal
                isNight           -> OdoAlert
                else              -> OdoInactive
            },
            modifier = Modifier.weight(1f)
        )
        StatusBarDivider()
        StatusBarItem(
            icon = Icons.Default.BatteryFull,
            label = battVoltage,
            color = if (carStatus == null) OdoInactive else OdoNormal,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatusBarItem(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun StatusBarDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(OdoDivider)
    )
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
