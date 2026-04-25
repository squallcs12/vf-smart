package com.daotranbang.vfsmart.autolink

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.car.app.connection.CarConnection
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Observer
import com.daotranbang.vfsmart.R
import com.daotranbang.vfsmart.navigation.NavigationNotificationService
import com.daotranbang.vfsmart.ui.MainActivity

class AutoLinkService : Service() {

    private val wifiManager get() = applicationContext.getSystemService(WifiManager::class.java)

    private lateinit var networkCallback: ConnectivityManager.NetworkCallback
    private var lastAutoLinkNetwork: Network? = null
    private var lastLaunchTime = 0L
    private var wifiScanReceiver: BroadcastReceiver? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var carConnection: CarConnection

    // MediaController monitoring for double-press detection
    private val trackedControllers = mutableMapOf<MediaController, MediaController.Callback>()
    private var lastPausedTime = 0L
    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            updateTrackedControllers(controllers)
        }

    private fun buildControllerCallback(pkg: String) = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val s = state?.state ?: return
            val now = System.currentTimeMillis()
            Log.d(TAG, "[$pkg] playback state=$s lastPaused=${now - lastPausedTime}ms ago")
            when (s) {
                PlaybackState.STATE_PAUSED -> lastPausedTime = now
                PlaybackState.STATE_PLAYING -> {
                    if (lastPausedTime > 0 && now - lastPausedTime < DOUBLE_PRESS_WINDOW_MS) {
                        Log.i(TAG, "double-press detected via [$pkg] — triggering AutoLink")
                        lastPausedTime = 0
                        // Pause music again so it doesn't keep playing after the trigger
                        trackedControllers.keys
                            .firstOrNull { it.packageName == pkg }
                            ?.transportControls?.pause()
                        triggerLaunch(this@AutoLinkService, skipCheck = true)
                    }
                }
            }
        }
    }

    private fun updateTrackedControllers(controllers: List<MediaController>?) {
        val incoming = controllers ?: emptyList()

        // Unregister controllers that are no longer active
        val toRemove = trackedControllers.keys.filter { old ->
            incoming.none { it.sessionToken == old.sessionToken }
        }
        toRemove.forEach { c ->
            c.unregisterCallback(trackedControllers.getValue(c))
            trackedControllers.remove(c)
            Log.d(TAG, "stopped tracking [${c.packageName}]")
        }

        // Register newly appeared controllers
        incoming.forEach { c ->
            if (trackedControllers.none { it.key.sessionToken == c.sessionToken }) {
                val cb = buildControllerCallback(c.packageName)
                c.registerCallback(cb)
                trackedControllers[c] = cb
                Log.d(TAG, "tracking [${c.packageName}] state=${c.playbackState?.state}")
            }
        }
    }

    private fun setupMediaSessionMonitor() {
        val mgr = getSystemService(MediaSessionManager::class.java)
        val nlsComponent = ComponentName(this, NavigationNotificationService::class.java)
        try {
            val initial = mgr.getActiveSessions(nlsComponent)
            updateTrackedControllers(initial)
            mgr.addOnActiveSessionsChangedListener(sessionsChangedListener, nlsComponent, handler)
            Log.d(TAG, "MediaSession monitor active — tracking ${initial.size} sessions")
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification listener not granted — cannot monitor media sessions", e)
        }
    }

    private fun teardownMediaSessionMonitor() {
        for ((c, cb) in trackedControllers) c.unregisterCallback(cb)
        trackedControllers.clear()
        try {
            getSystemService(MediaSessionManager::class.java)
                .removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (_: Exception) {}
    }

    private val carConnectionObserver = Observer<Int> { type ->
        val label = when (type) {
            CarConnection.CONNECTION_TYPE_PROJECTION -> "PROJECTION (Android Auto)"
            CarConnection.CONNECTION_TYPE_NATIVE     -> "NATIVE (Automotive OS)"
            else                                     -> "NOT_CONNECTED"
        }
        Log.i(TAG, "CarConnection state → $label")
        if (type == CarConnection.CONNECTION_TYPE_PROJECTION) {
            Log.i(TAG, "Android Auto connected — triggering AutoLink")
            launchAutoLink()
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        setupMediaSessionMonitor()
        registerCarConnectionObserver()
        registerCarModeReceiver()
        registerNetworkCallback()
    }

    private fun registerCarConnectionObserver() {
        carConnection = CarConnection(this)
        carConnection.type.observeForever(carConnectionObserver)
        Log.d(TAG, "CarConnection observer registered")
    }

    private fun registerCarModeReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == UiModeManager.ACTION_ENTER_CAR_MODE) {
                    Log.i(TAG, "ACTION_ENTER_CAR_MODE received — triggering AutoLink")
                    launchAutoLink()
                }
            }
        }
        registerReceiver(receiver, IntentFilter(UiModeManager.ACTION_ENTER_CAR_MODE))
    }

    private fun registerNetworkCallback() {
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (currentSsid() == AUTOLINK_SSID) {
                    lastAutoLinkNetwork = network
                }
            }

            override fun onLost(network: Network) {
                if (network == lastAutoLinkNetwork) {
                    lastAutoLinkNetwork = null
                    launchAutoLink()
                }
            }
        }
        getSystemService(ConnectivityManager::class.java).registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
            networkCallback
        )
    }

    @Suppress("DEPRECATION")
    private fun currentSsid(): String? =
        applicationContext.getSystemService(WifiManager::class.java)
            ?.connectionInfo?.ssid
            ?.trim('"')
            ?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        val pm = getSystemService(PowerManager::class.java)
        val wl = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
            "VF3Smart:AutoLink"
        )
        wl.acquire(WAKE_TIMEOUT_MS)
        AutoLinkAccessibilityService.wakeLock = wl
    }

    private fun isAutoLinkConnected(): Boolean =
        lastAutoLinkNetwork != null || currentSsid() == AUTOLINK_SSID

    private fun launchAutoLink(skipCheck: Boolean = false) {
        val now = System.currentTimeMillis()
        Log.d(TAG, "launchAutoLink called — skipCheck=$skipCheck")

        if (now - lastLaunchTime < DEBOUNCE_MS) {
            Log.d(TAG, "launchAutoLink debounced — ${DEBOUNCE_MS - (now - lastLaunchTime)}ms remaining")
            return
        }
        val ssid = currentSsid()
        Log.d(TAG, "current SSID=$ssid lastAutoLinkNetwork=$lastAutoLinkNetwork")
        if (!skipCheck && isAutoLinkConnected()) {
            Log.d(TAG, "launchAutoLink skipped — already on $AUTOLINK_SSID")
            return
        }

        Log.i(TAG, "scanning WiFi for $AUTOLINK_SSID before launching (wifiEnabled=${wifiManager.isWifiEnabled})")
        scanForAutoLinkSsid(
            onFound = {
                Log.i(TAG, "$AUTOLINK_SSID visible — launching AutoLink Pro")
                lastLaunchTime = System.currentTimeMillis()
                val launchIntent = Intent().apply {
                    setClassName(AUTOLINK_PACKAGE, AUTOLINK_MAIN_ACTIVITY)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                wakeScreen()
                startActivity(launchIntent)
                val accessibility = AutoLinkAccessibilityService.instance
                Log.d(TAG, "accessibility instance=$accessibility")
                if (accessibility != null) {
                    accessibility.startConnecting()
                } else {
                    Log.w(TAG, "accessibility not available — returning to MainActivity after delay")
                    handler.postDelayed({
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                    }, RETURN_DELAY_MS)
                }
            },
            onNotFound = {
                Log.w(TAG, "$AUTOLINK_SSID not found in scan — skipping launch")
            }
        )
    }

    private fun scanForAutoLinkSsid(onFound: () -> Unit, onNotFound: () -> Unit) {
        stopWifiScan()
        if (!wifiManager.isWifiEnabled) {
            Log.d(TAG, "WiFi disabled — enabling via root then polling")
            enableWifiViaRoot()
            pollUntilWifiEnabled(onFound, onNotFound)
        } else {
            Log.d(TAG, "WiFi enabled — starting scan")
            startWifiScan(onFound, onNotFound)
        }
    }

    private fun pollUntilWifiEnabled(onFound: () -> Unit, onNotFound: () -> Unit, attempt: Int = 0) {
        if (wifiManager.isWifiEnabled) {
            Log.d(TAG, "WiFi enabled after $attempt polls — starting scan")
            startWifiScan(onFound, onNotFound)
        } else if (attempt < 10) {
            Log.d(TAG, "WiFi not yet enabled — poll attempt $attempt/10")
            handler.postDelayed({ pollUntilWifiEnabled(onFound, onNotFound, attempt + 1) }, 1000)
        } else {
            Log.e(TAG, "WiFi failed to enable after 10 attempts — aborting")
            onNotFound()
        }
    }

    private fun enableWifiViaRoot() {
        Thread {
            try {
                val process = Runtime.getRuntime().exec("su")
                val writer = process.outputStream.bufferedWriter()
                writer.write("svc wifi enable\n")
                writer.flush()
                writer.close()
                process.waitFor()
            } catch (_: Exception) {}
        }.start()
    }

    @Suppress("DEPRECATION", "MissingPermission")
    private fun startWifiScan(onFound: () -> Unit, onNotFound: () -> Unit) {
        val deadline = System.currentTimeMillis() + SCAN_WINDOW_MS
        var attempt = 0

        fun scheduleScan() {
            attempt++
            Log.d(TAG, "WiFi scan attempt $attempt — looking for $AUTOLINK_SSID")

            wifiScanReceiver = object : BroadcastReceiver() {
                @Suppress("DEPRECATION", "MissingPermission")
                override fun onReceive(context: Context, intent: Intent) {
                    stopWifiScan()
                    val results = wifiManager.scanResults
                    val found = results.any { it.SSID == AUTOLINK_SSID }
                    Log.d(TAG, "scan #$attempt: ${results.size} networks, $AUTOLINK_SSID found=$found")
                    when {
                        found -> onFound()
                        System.currentTimeMillis() < deadline -> {
                            Log.d(TAG, "not found — next scan in ${SCAN_INTERVAL_MS / 1000}s")
                            handler.postDelayed({ scheduleScan() }, SCAN_INTERVAL_MS)
                        }
                        else -> {
                            Log.w(TAG, "$AUTOLINK_SSID not found after ${SCAN_WINDOW_MS / 60_000}min — giving up")
                            onNotFound()
                        }
                    }
                }
            }
            registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
            wifiManager.startScan()
        }

        scheduleScan()
    }

    private fun stopWifiScan() {
        wifiScanReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
            wifiScanReceiver = null
        }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID,
                    getString(R.string.autolink_channel_name),
                    NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.autolink_notif_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_LAUNCH_AUTOLINK) {
            launchAutoLink(skipCheck = intent.getBooleanExtra(EXTRA_SKIP_CHECK, false))
        }
        return START_STICKY
    }

    override fun onDestroy() {
        teardownMediaSessionMonitor()
        stopWifiScan()
        carConnection.type.removeObserver(carConnectionObserver)
        getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        private const val TAG = "AutoLinkSvc"
        private const val CHANNEL_ID = "autolink_monitor"
        private const val NOTIFICATION_ID = 2001
        private const val AUTOLINK_PACKAGE       = "com.link.autolink.pro"
        private const val AUTOLINK_MAIN_ACTIVITY = "com.link.autolink.activity.MainActivity"
        private const val AUTOLINK_SSID           = "DIRECT-phonelink-112391"
        private const val RETURN_DELAY_MS = 2000L
        private const val DEBOUNCE_MS = 5000L
        private const val WAKE_TIMEOUT_MS = 35_000L
        private const val SCAN_INTERVAL_MS = 30_000L
        private const val SCAN_WINDOW_MS   = 30 * 60_000L
        private const val DOUBLE_PRESS_WINDOW_MS = 1000L
        const val ACTION_LAUNCH_AUTOLINK = "com.daotranbang.vfsmart.LAUNCH_AUTOLINK"
        const val EXTRA_SKIP_CHECK = "skip_check"

        fun triggerLaunch(context: Context, skipCheck: Boolean = false) {
            context.startService(Intent(context, AutoLinkService::class.java).apply {
                action = ACTION_LAUNCH_AUTOLINK
                putExtra(EXTRA_SKIP_CHECK, skipCheck)
            })
        }

        fun start(context: Context) {
            AutoLinkAccessibilityService.enableViaRoot(context)
            val intent = Intent(context, AutoLinkService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
