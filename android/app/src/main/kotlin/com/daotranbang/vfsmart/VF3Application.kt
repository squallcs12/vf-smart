package com.daotranbang.vfsmart

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.daotranbang.vfsmart.autolink.AccessibilityDisclosure
import com.daotranbang.vfsmart.autolink.AutoLinkService
import com.daotranbang.vfsmart.autolink.RootPermissionGranter
import com.daotranbang.vfsmart.data.ImouScanner
import com.daotranbang.vfsmart.data.local.SecurePreferences
import com.daotranbang.vfsmart.data.repository.VF3Repository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltAndroidApp
class VF3Application : Application() {

    /** Hilt entry point so the Application (not an @AndroidEntryPoint) can reach the repo. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RepositoryEntryPoint {
        fun repository(): VF3Repository
    }

    private val repository: VF3Repository by lazy {
        EntryPoints.get(this, RepositoryEntryPoint::class.java).repository()
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var cameraScanJob: Job? = null
    private var autoLinkStartJob: Job? = null

    /**
     * The one-shot root permission grant (runtime perms, notification listener,
     * accessibility, screen-capture). Started at process creation so it runs before
     * anything — including the power-on AutoLink start and MainActivity — depends on
     * it. Resolves to `true` if root was available and the grants ran. Awaited by
     * [scheduleAutoLinkStart] and by MainActivity so notification access is in place
     * before the AutoLink flow begins, even on the very first run.
     */
    lateinit var permissionGrant: Deferred<Boolean>
        private set

    // AutoLink monitoring should only run while the head unit has power (car on).
    // This receiver lives for the whole process, so it can start the service again
    // after it has been stopped on power loss.
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    Log.i(TAG, "Power connected — AutoLinkService starts in ${AUTOLINK_START_DELAY_MS / 1000}s")
                    scheduleAutoLinkStart(context)
                    startCameraDetection(context)
                    repository.connectIfConfigured()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    Log.i(TAG, "Power disconnected — stopping AutoLinkService")
                    autoLinkStartJob?.cancel()
                    AutoLinkService.stop(context)
                    cameraScanJob?.cancel()
                }
            }
        }
    }

    /**
     * On power-up the car's systems (WiFi, AutoLink Pro, head unit) need a moment to
     * come up, so wait [AUTOLINK_START_DELAY_MS] before kicking off the AutoLink launch
     * flow. The job is cancelled if power drops during the wait (see [powerReceiver]).
     */
    private fun scheduleAutoLinkStart(context: Context) {
        autoLinkStartJob?.cancel()
        autoLinkStartJob = appScope.launch {
            delay(AUTOLINK_START_DELAY_MS)
            // Don't start until the permission grant has settled — the AutoLink flow
            // needs notification access + accessibility to work. Usually already done
            // by the time the delay elapses; this only blocks if root is slow.
            permissionGrant.await()
            Log.i(TAG, "AutoLink start delay elapsed — starting AutoLinkService")
            AutoLinkService.start(context)
        }
    }

    /**
     * On power-up the camera may not have joined the network yet, so scan for the
     * Imou camera once every 30 s for up to 5 minutes, stopping as soon as it is
     * found (its IP is saved as the RTSP URL).
     */
    private fun startCameraDetection(context: Context) {
        cameraScanJob?.cancel()
        cameraScanJob = appScope.launch {
            val prefs = SecurePreferences.getInstance(context)
            repeat(CAMERA_SCAN_ATTEMPTS) { attempt ->
                val ip = ImouScanner.scan()
                if (ip != null) {
                    val url = ImouScanner.buildRtspUrl(ip, prefs.getRtspUrl() ?: "")
                    prefs.saveRtspUrl(url)
                    Log.i(TAG, "Imou camera found at $ip — saved RTSP URL")
                    return@launch
                }
                Log.d(TAG, "Imou scan ${attempt + 1}/$CAMERA_SCAN_ATTEMPTS — not found")
                delay(CAMERA_SCAN_INTERVAL_MS)
            }
            Log.i(TAG, "Imou scan finished — no camera found within 5 minutes")
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Grant every permission via root as the very first thing the process does, so
        // notification access (and the rest) is in place before any AutoLink start —
        // including the power-on path, which can fire before MainActivity ever opens.
        // On a non-rooted device this resolves to false and the in-app prompts take over.
        //
        // One-shot: the grants persist, so once root has succeeded on a prior start we
        // skip the su session on every later launch. The flag only sticks on success,
        // so a non-rooted device (or a transient root failure) keeps retrying each start.
        permissionGrant = appScope.async {
            if (rootGrantDone()) return@async true
            val granted = RootPermissionGranter.grantAll(
                this@VF3Application, RootPermissionGranter.requiredRuntimePermissions()
            )
            if (granted) {
                AccessibilityDisclosure.setAccepted(this@VF3Application, true)
                markRootGrantDone()
            }
            granted
        }

        // Crashlytics auto-initializes via its ContentProvider; here we attach
        // diagnostics so reports tell us which of the two target devices crashed
        // (S20+ vs the armeabi-v7a head unit) and the ABI.
        FirebaseCrashlytics.getInstance().apply {
            setCustomKey("device_model", Build.MODEL)
            setCustomKey("primary_abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
            setCustomKey("android_api", Build.VERSION.SDK_INT)
        }

        registerReceiver(powerReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        })
        // Open the car-status WebSocket if a device has already been set up.
        repository.connectIfConfigured()
        // Start immediately if the device is already on power at launch.
        if (isOnPower()) {
            Log.i(TAG, "Already on power at startup — AutoLinkService starts in ${AUTOLINK_START_DELAY_MS / 1000}s")
            scheduleAutoLinkStart(this)
            startCameraDetection(this)
        }
    }

    /** True once the root permission grant has succeeded on some earlier start. */
    private fun rootGrantDone(): Boolean =
        getSharedPreferences(PREFS_ROOT_GRANT, MODE_PRIVATE).getBoolean(KEY_ROOT_GRANTED, false)

    private fun markRootGrantDone() =
        getSharedPreferences(PREFS_ROOT_GRANT, MODE_PRIVATE)
            .edit().putBoolean(KEY_ROOT_GRANTED, true).apply()

    private fun isOnPower(): Boolean {
        val plugged = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        return plugged == BatteryManager.BATTERY_PLUGGED_AC ||
               plugged == BatteryManager.BATTERY_PLUGGED_USB ||
               plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS
    }

    companion object {
        private const val TAG = "VF3App"
        // Persisted one-shot flag: root grant only needs to run until it succeeds once.
        private const val PREFS_ROOT_GRANT = "root_grant"
        private const val KEY_ROOT_GRANTED = "granted"
        // Wait for the car's systems (WiFi/AutoLink/head unit) to come up after power-on.
        private const val AUTOLINK_START_DELAY_MS = 30_000L
        // Scan once every 30 s for 5 minutes → 10 attempts.
        private const val CAMERA_SCAN_INTERVAL_MS = 30_000L
        private const val CAMERA_SCAN_ATTEMPTS = 10
    }
}
