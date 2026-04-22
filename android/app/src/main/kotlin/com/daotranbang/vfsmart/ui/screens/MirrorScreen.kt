package com.daotranbang.vfsmart.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daotranbang.vfsmart.R
import com.daotranbang.vfsmart.data.model.CarStatus
import com.daotranbang.vfsmart.data.model.TpmsData
import com.daotranbang.vfsmart.data.model.TpmsTire
import com.daotranbang.vfsmart.data.network.WebSocketManager
import com.daotranbang.vfsmart.navigation.NavigationNotificationService
import com.daotranbang.vfsmart.navigation.NavigationState
import com.daotranbang.vfsmart.viewmodel.CarStatusViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

// ── ODO colour palette ────────────────────────────────────────────────────────
private val OdoBg       = Color(0xFF0A0A0A)
private val OdoDivider  = Color(0xFF3A3A3A)
private val OdoLabel    = Color(0xFF909090)
private val OdoInactive = Color(0xFF686868)
private val OdoNormal   = Color(0xFFF0F0F0)
private val OdoGood     = Color(0xFF4CAF50)
private val OdoWarning  = Color(0xFFFFB300)
private val OdoAlert    = Color(0xFFEF5350)

// ── Public entry point ────────────────────────────────────────────────────────

@Composable
fun MirrorScreen(
    onNavigateBack: () -> Unit,
    statusViewModel: CarStatusViewModel = hiltViewModel()
) {
    val carStatus       by statusViewModel.carStatus.collectAsStateWithLifecycle()
    val connectionState by statusViewModel.connectionState.collectAsStateWithLifecycle()
    val navigationState by NavigationNotificationService.navigationState.collectAsStateWithLifecycle()

    // Trip clock: reset each time screen comes to foreground
    var foregroundTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { foregroundTime = System.currentTimeMillis() }
    var tripTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); tripTick++ }
    }
    val tripText = remember(tripTick, foregroundTime) {
        val secs = (System.currentTimeMillis() - foregroundTime) / 1000
        val h = secs / 3600
        val m = (secs % 3600) / 60
        "${h}:${m.toString().padStart(2, '0')}"
    }

    // Hide system bars; restore when leaving
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MirrorContent(
            carStatus       = carStatus,
            connectionState = connectionState,
            navigationState = navigationState,
            tripText        = tripText
        )

        SmallFloatingActionButton(
            onClick          = onNavigateBack,
            modifier         = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            containerColor   = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ) {
            Icon(
                imageVector     = Icons.Default.FullscreenExit,
                contentDescription = "Exit mirror mode",
                modifier        = Modifier.size(18.dp)
            )
        }
    }
}

// ── Mirror layout: ODO instrument-cluster style ───────────────────────────────

@Composable
private fun MirrorContent(
    carStatus: CarStatus?,
    connectionState: WebSocketManager.ConnectionState,
    navigationState: NavigationState,
    tripText: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(OdoBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            var sharedSpeedLimit by remember { mutableStateOf<Int?>(null) }

            Column(modifier = Modifier.weight(1f)) {
                // Row 1: Location | Navigation | GPS Speed | Trip
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    OdoLocationCell(modifier = Modifier.weight(1f))
                    OdoVerticalDivider()
                    OdoNavCell(navigationState = navigationState, modifier = Modifier.weight(1f))
                    OdoVerticalDivider()
                    OdoGpsSpeedCell(speedLimit = sharedSpeedLimit, modifier = Modifier.weight(1f))
                    OdoVerticalDivider()
                    OdoTripCell(tripText = tripText, modifier = Modifier.weight(1f))
                }

                OdoHorizontalDivider()

                // Row 2: Charging | TPMS | Speed Limit | Clock
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

            // Bottom status bar
            OdoStatusBar(carStatus = carStatus)
        }

        ConnectionDot(
            connectionState = connectionState,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        )
    }
}

// ── Generic ODO cell ──────────────────────────────────────────────────────────

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
            Icon(imageVector = icon, contentDescription = null, tint = color,
                modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(10.dp))
        }
        Text(text = value, color = color, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Text(text = label, color = OdoLabel, fontSize = 10.sp, fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp, textAlign = TextAlign.Center)
    }
}

// ── Location cell ─────────────────────────────────────────────────────────────

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
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(loc: android.location.Location) { location = loc }
                override fun onProviderDisabled(provider: String) {}
                override fun onProviderEnabled(provider: String) {}
                @Suppress("DEPRECATION")
                override fun onStatusChanged(provider: String, status: Int, extras: android.os.Bundle?) {}
            }
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
        Icon(imageVector = Icons.Default.LocationOn, contentDescription = null,
            tint = OdoInactive, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            text = province?.uppercase() ?: if (!hasData) "LOCATING..." else "--",
            color = OdoLabel, fontSize = 10.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center, letterSpacing = 2.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = district ?: "--", color = OdoInactive, fontSize = 11.sp,
            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, letterSpacing = 0.5.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = street ?: "--", color = if (hasData) OdoNormal else OdoInactive,
            fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            letterSpacing = 0.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

// ── Weather helpers ───────────────────────────────────────────────────────────

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
            val json     = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
            val current  = json.getJSONObject("current_weather").getInt("weathercode")
            val codes    = json.getJSONObject("hourly").getJSONArray("weathercode")
            val nextIdx  = (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) + 1) % 24
            val nextHour = codes.getInt(nextIdx)
            WeatherData(current, nextHour)
        } catch (e: Exception) {
            null
        }
    }

private fun weatherIconFor(code: Int?, isNight: Boolean): ImageVector = when {
    code == null     -> if (isNight) Icons.Default.NightsStay else Icons.Default.WbSunny
    code == 0        -> if (isNight) Icons.Default.NightsStay else Icons.Default.WbSunny
    code in 1..3     -> Icons.Default.WbCloudy
    code in 45..48   -> Icons.Default.Cloud
    code in 51..67   -> Icons.Default.WaterDrop
    code in 71..77   -> Icons.Default.AcUnit
    code in 80..82   -> Icons.Default.WaterDrop
    code >= 95       -> Icons.Default.Bolt
    else             -> Icons.Default.Cloud
}

// ── Clock cell ────────────────────────────────────────────────────────────────

@SuppressLint("MissingPermission")
@Composable
private fun OdoClockCell(modifier: Modifier = Modifier) {
    // Reuse a single Calendar instance to avoid per-second allocations
    val cal = remember { java.util.Calendar.getInstance() }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); nowMs = System.currentTimeMillis() }
    }
    cal.timeInMillis = nowMs

    val context = LocalContext.current
    var weather  by remember { mutableStateOf<WeatherData?>(null) }
    var location by remember { mutableStateOf<android.location.Location?>(null) }

    val hasPerm = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    DisposableEffect(hasPerm) {
        if (!hasPerm) { onDispose {} } else {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(loc: android.location.Location) { location = loc }
                override fun onProviderDisabled(provider: String) {}
                override fun onProviderEnabled(provider: String) {}
                @Suppress("DEPRECATION")
                override fun onStatusChanged(provider: String, status: Int, extras: android.os.Bundle?) {}
            }
            val seed = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (seed != null) location = seed
            try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60_000L, 500f, listener) } catch (_: Exception) {}
            try { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60_000L, 500f, listener) } catch (_: Exception) {}
            onDispose { lm.removeUpdates(listener) }
        }
    }

    // Fetch weather immediately, then every 30 minutes
    LaunchedEffect(Unit) {
        while (true) {
            val loc = location
            if (loc != null) {
                val result = fetchWeather(loc.latitude, loc.longitude)
                if (result != null) weather = result
            }
            delay(30 * 60 * 1000L)
        }
    }

    val hour        = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val minute      = cal.get(java.util.Calendar.MINUTE)
    val isNight     = hour < 6 || hour >= 18
    val nextIsNight = (hour + 1) % 24 < 6 || (hour + 1) % 24 >= 18
    val time        = String.format("%02d:%02d", hour, minute)
    val currentIcon = weatherIconFor(weather?.current, isNight)
    val nextIcon    = weatherIconFor(weather?.nextHour, nextIsNight)

    Column(
        modifier = modifier.fillMaxSize().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
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
        Text(text = time, color = OdoNormal, fontSize = 52.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, letterSpacing = 2.sp)
        Text(text = "CLOCK", color = OdoLabel, fontSize = 10.sp,
            fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
    }
}

// ── GPS speed cell ────────────────────────────────────────────────────────────

@SuppressLint("MissingPermission")
@Composable
private fun OdoGpsSpeedCell(
    speedLimit: Int? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    fun hasPerm() = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    var permGranted by remember { mutableStateOf(hasPerm()) }
    var speedKmh    by remember { mutableFloatStateOf(0f) }
    var hasData     by remember { mutableStateOf(false) }

    DisposableEffect(permGranted) {
        if (!permGranted) { onDispose {} } else {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(loc: android.location.Location) {
                    if (loc.hasSpeed()) { speedKmh = loc.speed * 3.6f; hasData = true }
                }
                override fun onProviderDisabled(provider: String) {}
                override fun onProviderEnabled(provider: String) {}
                @Suppress("DEPRECATION")
                override fun onStatusChanged(provider: String, status: Int, extras: android.os.Bundle?) {}
            }
            val seed = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (seed != null && seed.hasSpeed()) { speedKmh = seed.speed * 3.6f; hasData = true }
            try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener) } catch (_: Exception) {}
            onDispose { lm.removeUpdates(listener) }
        }
    }

    val speed         = speedKmh.toInt()
    val overLimit     = speedLimit != null && speed >= speedLimit
    val overLimitPlus5 = speedLimit != null && speed >= speedLimit + 5
    val speedColor = when {
        !hasData       -> OdoInactive
        overLimitPlus5 -> OdoAlert
        overLimit      -> OdoWarning
        else           -> OdoNormal
    }
    val speedFontSize = if (speed >= 100) 64.sp else 80.sp

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = if (hasData) "$speed" else "--", color = speedColor,
            fontSize = speedFontSize, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Text(text = "KM/H", color = OdoLabel, fontSize = 10.sp,
            fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
    }
}

// ── Trip cell ─────────────────────────────────────────────────────────────────

@SuppressLint("MissingPermission")
@Composable
private fun OdoTripCell(
    tripText: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    fun hasPerm() = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    var permGranted by remember { mutableStateOf(hasPerm()) }
    var hasGps      by remember { mutableStateOf(false) }
    var speedKmh    by remember { mutableFloatStateOf(0f) }

    DisposableEffect(permGranted) {
        if (!permGranted) { onDispose {} } else {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(loc: android.location.Location) {
                    val spd = if (loc.hasSpeed()) loc.speed * 3.6f else -1f
                    android.util.Log.d("VF3TripCell", "location update: hasSpeed=${loc.hasSpeed()} speed=$spd km/h provider=${loc.provider}")
                    if (loc.hasSpeed()) { speedKmh = loc.speed * 3.6f; hasGps = true }
                }
                override fun onProviderDisabled(provider: String) {}
                override fun onProviderEnabled(provider: String) {}
                @Suppress("DEPRECATION")
                override fun onStatusChanged(provider: String, status: Int, extras: android.os.Bundle?) {}
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
    var hasMovedOnce  by remember { mutableStateOf(false) }
    var countdownSecs by remember { mutableIntStateOf(15) }
    var countdownActive by remember { mutableStateOf(false) }

    LaunchedEffect(isStopped) {
        android.util.Log.d("VF3TripCell", "isStopped=$isStopped hasGps=$hasGps speedKmh=$speedKmh hasMovedOnce=$hasMovedOnce")
        if (speedKmh >= 1f) {
            hasMovedOnce = true
            countdownActive = false
            countdownSecs = 15
        } else if (isStopped && hasMovedOnce) {
            countdownActive = true
            countdownSecs = 15
            while (countdownSecs > 0) {
                delay(1000)
                if (!countdownActive) break
                countdownSecs--
            }
            countdownActive = false
        }
    }

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (countdownActive && countdownSecs > 0) {
            Icon(imageVector = Icons.Default.Timer, contentDescription = null,
                tint = OdoWarning, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(text = "$countdownSecs", color = OdoWarning, fontSize = 28.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Text(text = "STOPPED", color = OdoLabel, fontSize = 10.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
        } else {
            Text(text = "TRIP", color = OdoLabel, fontSize = 10.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val parts = tripText.split(":")
                Text(text = parts[0], color = OdoNormal, fontSize = 28.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(text = ":", color = if (colonVisible) OdoNormal else Color.Transparent,
                    fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(text = parts.getOrElse(1) { "00" }, color = OdoNormal, fontSize = 28.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}

// ── Navigation cell ───────────────────────────────────────────────────────────

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
        Icon(imageVector = icon, contentDescription = null, tint = color,
            modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(10.dp))
        Text(text = if (navigationState.isActive) navigationState.distance else "--",
            color = color, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (navigationState.isActive) navigationState.maneuver else "NO NAVIGATION",
            color = OdoLabel, fontSize = 10.sp, fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp, textAlign = TextAlign.Center
        )
    }
}

// ── TPMS cell ─────────────────────────────────────────────────────────────────

@Composable
private fun OdoTpmsCell(
    tpms: TpmsData?,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxHeight().background(OdoBg).padding(10.dp)) {
        val sideW    = maxWidth * 0.20f
        val frontPad = maxHeight * 0.10f
        val rearPad  = maxHeight * 0.07f

        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.width(sideW).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween) {
                OdoTireNumber(tpms?.fl, modifier = Modifier.padding(top = frontPad))
                OdoTireNumber(tpms?.rl, modifier = Modifier.padding(bottom = rearPad))
            }
            Image(painter = painterResource(R.drawable.ic_vf3_top), contentDescription = "VF3",
                contentScale = ContentScale.Fit,
                modifier = Modifier.weight(1f).fillMaxHeight())
            Column(modifier = Modifier.width(sideW).fillMaxHeight(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween) {
                OdoTireNumber(tpms?.fr, modifier = Modifier.padding(top = frontPad))
                OdoTireNumber(tpms?.rr, modifier = Modifier.padding(bottom = rearPad))
            }
        }
    }
}

@Composable
private fun OdoTireNumber(tire: TpmsTire?, modifier: Modifier = Modifier) {
    val color = when {
        tire == null || !tire.valid || tire.stale -> OdoInactive
        tire.alarm                                -> OdoAlert
        tire.pressureKpa < 1.8f                  -> OdoWarning
        else                                      -> OdoGood
    }
    val valueText = if (tire != null && tire.valid && !tire.stale)
        String.format("%.1f", tire.pressureKpa)
    else "_._"

    Text(text = valueText, color = color, fontSize = 22.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 0.sp, modifier = modifier)
}

// ── Charging / nearby stations cell ──────────────────────────────────────────

private const val CHARGER_KML_URL =
    "https://www.google.com/maps/d/kml?mid=1iIZ3L3KEKU0fg5XsIQ6hbRl7NVY8JNA&forcekml=1"
private const val KML_CACHE_FILE = "charger_kml.json"

private data class NearbyStation(val name: String, val distanceM: Double)

private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r    = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a    = Math.sin(dLat / 2).let { it * it } +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
        Math.sin(dLon / 2).let { it * it }
    return r * 2 * Math.asin(Math.sqrt(a))
}

/**
 * SAX-parse a KML stream and write stations to [outputFile] as line-delimited text:
 *   name|lat|lon
 * Never holds more than one station in memory at a time.
 */
private fun parseKmlToFile(kmlStream: java.io.InputStream, outputFile: java.io.File) {
    var inPlacemark   = false
    var inName        = false
    var inCoordinates = false
    val nameBuilder   = StringBuilder()
    val coordBuilder  = StringBuilder()
    var count         = 0

    val writer = outputFile.bufferedWriter()
    try {
        javax.xml.parsers.SAXParserFactory.newInstance().newSAXParser()
            .parse(kmlStream, object : org.xml.sax.helpers.DefaultHandler() {
                override fun startElement(uri: String, localName: String, qName: String,
                                          attrs: org.xml.sax.Attributes) {
                    when (qName) {
                        "Placemark"   -> { inPlacemark = true; nameBuilder.clear(); coordBuilder.clear() }
                        "name"        -> if (inPlacemark) { inName = true; nameBuilder.clear() }
                        "coordinates" -> if (inPlacemark) { inCoordinates = true; coordBuilder.clear() }
                    }
                }
                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (inName)        nameBuilder.append(ch, start, length)
                    if (inCoordinates) coordBuilder.append(ch, start, length)
                }
                override fun endElement(uri: String, localName: String, qName: String) {
                    when (qName) {
                        "name"        -> inName = false
                        "coordinates" -> {
                            inCoordinates = false
                            if (inPlacemark && count < 500) {
                                val name  = nameBuilder.toString().trim()
                                val raw   = coordBuilder.toString().trim()
                                val comma1 = raw.indexOf(',')
                                val comma2 = if (comma1 >= 0) raw.indexOf(',', comma1 + 1) else -1
                                val lon = raw.substring(0, comma1.coerceAtLeast(0)).toDoubleOrNull()
                                val lat = if (comma1 >= 0) {
                                    val end = if (comma2 > comma1) comma2 else raw.length
                                    raw.substring(comma1 + 1, end).trim().toDoubleOrNull()
                                } else null
                                if (lon != null && lat != null && name.isNotBlank()) {
                                    writer.write("${name.replace("|", " ")}|$lat|$lon\n")
                                    count++
                                }
                            }
                        }
                        "Placemark"   -> inPlacemark = false
                    }
                }
            })
        android.util.Log.d("VF3Charging", "KML parsed: $count stations written")
    } finally {
        writer.close()
    }
}

/**
 * Download fresh KML from network and write to the cache file via a temp file
 * (atomic rename), so the reader never sees a partial file.
 * No data is held in memory after this returns.
 */
private fun refreshKmlFromNetwork(context: Context) {
    try {
        val conn = (java.net.URL(CHARGER_KML_URL).openConnection()
                as java.net.HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("User-Agent", "VF3Smart/1.0")
        }
        val tmp  = java.io.File(context.cacheDir, "$KML_CACHE_FILE.tmp")
        val dest = java.io.File(context.cacheDir, KML_CACHE_FILE)
        conn.inputStream.use { parseKmlToFile(it, tmp) }
        if (tmp.length() > 0) tmp.renameTo(dest)
        else tmp.delete()
    } catch (e: Exception) {
        android.util.Log.e("VF3Charging", "KML network refresh failed", e)
    }
}

/**
 * Stream through the cache file line by line and return only the closest station.
 * O(1) memory regardless of how many stations are in the file.
 */
private fun findClosestStation(context: Context, userLat: Double, userLon: Double): NearbyStation? {
    val file = java.io.File(context.cacheDir, KML_CACHE_FILE)
    if (!file.exists()) return null
    var closest: NearbyStation? = null
    var minDist = Double.MAX_VALUE
    file.bufferedReader().useLines { lines ->
        lines.forEach { line ->
            val i1 = line.indexOf('|')
            if (i1 < 1) return@forEach
            val i2 = line.indexOf('|', i1 + 1)
            if (i2 < 0) return@forEach
            val stLat = line.substring(i1 + 1, i2).toDoubleOrNull() ?: return@forEach
            val stLon = line.substring(i2 + 1).toDoubleOrNull() ?: return@forEach
            val dist  = haversineM(userLat, userLon, stLat, stLon)
            if (dist < minDist) { minDist = dist; closest = NearbyStation(line.substring(0, i1), dist) }
        }
    }
    return closest
}

@SuppressLint("MissingPermission")
@Composable
private fun OdoChargingCell(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var closest    by remember { mutableStateOf<NearbyStation?>(null) }
    var statusText by remember { mutableStateOf("LOCATING...") }

    fun hasPerm() = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    var permGranted   by remember { mutableStateOf(hasPerm()) }
    var foregroundKey by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { permGranted = hasPerm(); foregroundKey++ }

    // Refresh cache file from network in background — no data kept in RAM
    LaunchedEffect(foregroundKey) {
        kotlinx.coroutines.withContext(Dispatchers.IO) { refreshKmlFromNetwork(context) }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> if (results.values.any { it }) permGranted = true }

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
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(loc: android.location.Location) { location = loc }
                override fun onProviderDisabled(provider: String) {}
                override fun onProviderEnabled(provider: String) {}
                @Suppress("DEPRECATION")
                override fun onStatusChanged(provider: String, status: Int, extras: android.os.Bundle?) {}
            }
            statusText = "LOCATING..."
            val seed = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            if (seed != null) location = seed else statusText = "NO GPS SIGNAL"
            try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,    30_000L, 100f, listener) } catch (_: Exception) {}
            try { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30_000L, 100f, listener) } catch (_: Exception) {}
            onDispose { lm.removeUpdates(listener) }
        }
    }

    // Recalculate closest station every 5 minutes using the latest known location.
    // Only the single winning NearbyStation is ever held in memory.
    LaunchedEffect(Unit) {
        while (true) {
            val loc = location
            if (loc != null) {
                statusText = "SEARCHING..."
                val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    findClosestStation(context, loc.latitude, loc.longitude)
                }
                closest = result
                statusText = when {
                    result != null -> "NEARBY"
                    java.io.File(context.cacheDir, KML_CACHE_FILE).exists() -> "NONE NEARBY"
                    else -> "LOADING..."
                }
            }
            delay(5 * 60 * 1000L)
        }
    }

    Column(modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(imageVector = Icons.Filled.Bolt, contentDescription = null,
            tint = if (closest != null) OdoGood else OdoInactive,
            modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(10.dp))
        if (closest == null) {
            Text(text = statusText, color = OdoInactive, fontSize = 10.sp,
                letterSpacing = 1.sp, textAlign = TextAlign.Center)
        } else {
            val station = closest!!
            Text(text = station.name.uppercase(), color = OdoNormal, fontSize = 12.sp,
                letterSpacing = 0.5.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
            val distText = if (station.distanceM < 1000)
                "${station.distanceM.toInt()} M"
            else
                String.format(java.util.Locale.US, "%.1f KM", station.distanceM / 1000)
            Text(text = distText, color = OdoGood, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

// ── Speed limit cell ──────────────────────────────────────────────────────────

private suspend fun fetchSpeedLimit(lat: Double, lon: Double): Int? =
    kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val query   = "[out:json][timeout:5];way(around:30,${lat},${lon})[highway][maxspeed];out tags 1;"
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url     = java.net.URL("https://overpass-api.de/api/interpreter?data=$encoded")
            val conn    = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 8_000; readTimeout = 8_000
                setRequestProperty("User-Agent", "VF3Smart/1.0")
            }
            val json     = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
            val elements = json.getJSONArray("elements")
            if (elements.length() == 0) return@withContext null
            val tags = elements.getJSONObject(0).getJSONObject("tags")
            val raw  = tags.optString("maxspeed", "").trim()
            val num  = Regex("(\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull()
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
    ) == PackageManager.PERMISSION_GRANTED

    var permGranted by remember { mutableStateOf(hasPerm()) }
    var location    by remember { mutableStateOf<android.location.Location?>(null) }
    var speedLimit  by remember { mutableStateOf<Int?>(null) }
    var querying    by remember { mutableStateOf(false) }

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
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(loc: android.location.Location) { location = loc }
                override fun onProviderDisabled(provider: String) {}
                override fun onProviderEnabled(provider: String) {}
                @Suppress("DEPRECATION")
                override fun onStatusChanged(provider: String, status: Int, extras: android.os.Bundle?) {}
            }
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
        querying   = true
        speedLimit = fetchSpeedLimit(loc.latitude, loc.longitude)
        querying   = false
        onSpeedLimitChanged(speedLimit)
    }

    val valueText = speedLimit?.toString() ?: if (querying) "..." else "--"

    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxHeight().aspectRatio(1f).padding(10.dp)
                .background(Color(0xFFE57373), CircleShape)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().padding(18.dp)
                    .background(Color(0xFFFFF3F3), CircleShape)
            ) {
                Text(text = valueText, color = Color(0xFF5D4040),
                    fontSize = if (speedLimit != null && speedLimit!! >= 100) 64.sp else 80.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 0.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

// ── Bottom status bar ─────────────────────────────────────────────────────────

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

    Row(modifier = modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically) {
        StatusBarItem(icon = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
            label = if (carStatus == null) "--" else if (isLocked) "LOCKED" else "UNLOCKED",
            color = if (carStatus == null) OdoInactive else if (isLocked) OdoGood else OdoWarning,
            modifier = Modifier.weight(1f))
        StatusBarDivider()
        StatusBarItem(icon = if (doorsOpen) Icons.Default.Warning else Icons.Default.Check,
            label = if (carStatus == null) "--" else if (doorsOpen) "OPEN" else "CLOSED",
            color = if (carStatus == null) OdoInactive else if (doorsOpen) OdoAlert else OdoGood,
            modifier = Modifier.weight(1f))
        StatusBarDivider()
        StatusBarItem(icon = Icons.Default.DirectionsCar,
            label = if (carStatus == null) "--" else if (inDrive) "DRIVE" else "PARK",
            color = if (carStatus == null) OdoInactive else if (inDrive) OdoNormal else OdoInactive,
            modifier = Modifier.weight(1f))
        StatusBarDivider()
        StatusBarItem(icon = Icons.Default.Lightbulb,
            label = if (carStatus == null) "--" else if (lightsOn) "ON" else "OFF",
            color = when {
                carStatus == null -> OdoInactive
                lightsOn          -> OdoNormal
                isNight           -> OdoAlert
                else              -> OdoInactive
            },
            modifier = Modifier.weight(1f))
        StatusBarDivider()
        StatusBarItem(icon = Icons.Default.BatteryFull,
            label = battVoltage,
            color = if (carStatus == null) OdoInactive else OdoNormal,
            modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatusBarItem(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text = label, color = color, fontSize = 10.sp,
            fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
    }
}

@Composable
private fun StatusBarDivider() {
    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(OdoDivider))
}

@Composable
private fun OdoVerticalDivider() {
    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(OdoDivider))
}

@Composable
private fun OdoHorizontalDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(OdoDivider))
}

// ── Connection dot overlay ────────────────────────────────────────────────────

@Composable
private fun ConnectionDot(
    connectionState: WebSocketManager.ConnectionState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(8.dp).background(
        color = when (connectionState) {
            WebSocketManager.ConnectionState.Connected -> Color(0xFF4CAF50)
            else -> Color(0xFFEF5350)
        },
        shape = CircleShape
    ))
}
