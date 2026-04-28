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
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.car.app.connection.CarConnection
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Observer
import com.daotranbang.vfsmart.R
import com.daotranbang.vfsmart.navigation.NavigationNotificationService
import com.daotranbang.vfsmart.ui.MainActivity

class AutoLinkService : Service() {

    private var lastLaunchTime = 0L
    private var autoRetryCount = 0
    private var connectionCheckAttempt = 0
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

    private var savedBrightness = -1

    private fun dimScreen() {
        if (!Settings.System.canWrite(this)) {
            promptWriteSettings()
            return
        }
        savedBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 5)
        Log.i(TAG, "Screen dimmed to 5 (saved=$savedBrightness)")
    }

    private fun restoreBrightness() {
        if (savedBrightness < 0) return
        if (!Settings.System.canWrite(this)) return
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, savedBrightness)
        Log.i(TAG, "Screen brightness restored to $savedBrightness")
        savedBrightness = -1
    }

    private val carConnectionObserver = Observer<Int> { type ->
        val label = when (type) {
            CarConnection.CONNECTION_TYPE_PROJECTION -> "PROJECTION (Android Auto)"
            CarConnection.CONNECTION_TYPE_NATIVE     -> "NATIVE (Automotive OS)"
            else                                     -> "NOT_CONNECTED"
        }
        Log.i(TAG, "CarConnection state → $label")
        when (type) {
            CarConnection.CONNECTION_TYPE_PROJECTION -> {
                Log.i(TAG, "Android Auto connected — dimming screen, triggering AutoLink")
                dimScreen()
                launchAutoLink()
            }
            else -> {
                Log.i(TAG, "Android Auto disconnected — restoring brightness")
                restoreBrightness()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        grantWriteSettingsViaRoot()
        setupMediaSessionMonitor()
        registerCarConnectionObserver()
        registerCarModeReceiver()
    }

    private fun grantWriteSettingsViaRoot() {
        if (Settings.System.canWrite(this)) return
        Thread {
            try {
                val process = Runtime.getRuntime().exec("su")
                val writer = process.outputStream.bufferedWriter()
                writer.write("appops set $packageName WRITE_SETTINGS allow\n")
                writer.flush()
                writer.close()
                process.waitFor()
                if (Settings.System.canWrite(this)) {
                    Log.i(TAG, "WRITE_SETTINGS granted via root")
                } else {
                    Log.w(TAG, "Root ran but WRITE_SETTINGS still not granted")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Root grant failed: ${e.message}")
            }
        }.start()
    }

    private fun promptWriteSettings() {
        Log.i(TAG, "Prompting user to grant WRITE_SETTINGS")
        startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
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

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        AutoLinkAccessibilityService.wakeLock?.let { if (it.isHeld) it.release() }
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
        NavigationNotificationService.autoLinkMirroringActive

    private val connectionCheckRunnable = object : Runnable {
        override fun run() {
            if (NavigationNotificationService.autoLinkMirroringActive) {
                Log.i(TAG, "AutoLink Mirroring notification active ✓ (check ${connectionCheckAttempt + 1})")
                autoRetryCount = 0
            } else if (connectionCheckAttempt < 4) {
                connectionCheckAttempt++
                Log.v(TAG, "connection check $connectionCheckAttempt/5 — mirroring=false")
                handler.postDelayed(this, CONNECTION_CHECK_INTERVAL_MS)
            } else if (autoRetryCount < MAX_AUTO_RETRIES) {
                Log.w(TAG, "AutoLink not mirroring after 30s — retrying once")
                autoRetryCount++
                triggerLaunch(this@AutoLinkService, skipCheck = true, isRetry = true)
            } else {
                Log.e(TAG, "AutoLink connection failed after retry — giving up")
                autoRetryCount = 0
            }
        }
    }

    private fun scheduleConnectionCheck() {
        handler.removeCallbacks(connectionCheckRunnable)
        connectionCheckAttempt = 0
        handler.postDelayed(connectionCheckRunnable, CONNECTION_CHECK_INTERVAL_MS)
    }

    private fun launchAutoLink(skipCheck: Boolean = false, isRetry: Boolean = false) {
        val now = System.currentTimeMillis()
        Log.d(TAG, "launchAutoLink called — skipCheck=$skipCheck isRetry=$isRetry")
        if (!isRetry) autoRetryCount = 0

        if (now - lastLaunchTime < DEBOUNCE_MS) {
            Log.d(TAG, "launchAutoLink debounced — ${DEBOUNCE_MS - (now - lastLaunchTime)}ms remaining")
            return
        }
        if (!skipCheck && isAutoLinkConnected()) {
            Log.d(TAG, "launchAutoLink skipped — AutoLink is already mirroring")
            return
        }

        Log.i(TAG, "launching AutoLink Pro")
        lastLaunchTime = System.currentTimeMillis()
        AutoLinkAccessibilityService.instance?.helpClickCount = 0
        wakeScreen()
        startActivity(Intent().apply {
            setClassName(AUTOLINK_PACKAGE, AUTOLINK_MAIN_ACTIVITY)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        val accessibility = AutoLinkAccessibilityService.instance
        Log.d(TAG, "accessibility instance=$accessibility")
        if (accessibility != null) {
            accessibility.startConnecting(onStartNowClicked = { scheduleConnectionCheck() })
        } else {
            Log.w(TAG, "accessibility not available — returning to MainActivity after delay")
            handler.postDelayed({
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                })
            }, RETURN_DELAY_MS)
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
            launchAutoLink(
                skipCheck = intent.getBooleanExtra(EXTRA_SKIP_CHECK, false),
                isRetry = intent.getBooleanExtra(EXTRA_IS_RETRY, false)
            )
        }
        return START_STICKY
    }

    override fun onDestroy() {
        teardownMediaSessionMonitor()
        carConnection.type.removeObserver(carConnectionObserver)
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
        private const val RETURN_DELAY_MS = 2000L
        private const val DEBOUNCE_MS = 5000L
        private const val WAKE_TIMEOUT_MS = 35_000L
        private const val DOUBLE_PRESS_WINDOW_MS = 1000L
        const val ACTION_LAUNCH_AUTOLINK = "com.daotranbang.vfsmart.LAUNCH_AUTOLINK"
        const val EXTRA_SKIP_CHECK = "skip_check"
        const val EXTRA_IS_RETRY = "is_retry"
        private const val MAX_AUTO_RETRIES = 1
        private const val CONNECTION_CHECK_INTERVAL_MS = 6_000L

        fun triggerLaunch(context: Context, skipCheck: Boolean = false, isRetry: Boolean = false) {
            context.startService(Intent(context, AutoLinkService::class.java).apply {
                action = ACTION_LAUNCH_AUTOLINK
                putExtra(EXTRA_SKIP_CHECK, skipCheck)
                putExtra(EXTRA_IS_RETRY, isRetry)
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
