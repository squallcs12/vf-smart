package com.daotranbang.vfsmart.autolink

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.daotranbang.vfsmart.R
import com.daotranbang.vfsmart.ui.MainActivity

class AutoLinkService : Service() {

    private val wifiManager get() = applicationContext.getSystemService(WifiManager::class.java)

    private lateinit var triggerReceiver: BroadcastReceiver
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback
    private var lastAutoLinkNetwork: Network? = null
    private var lastLaunchTime = 0L
    private var wifiScanReceiver: BroadcastReceiver? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerTriggerReceiver()
        registerNetworkCallback()
    }

    private fun registerTriggerReceiver() {
        triggerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == UiModeManager.ACTION_ENTER_CAR_MODE) launchAutoLink()
            }
        }
        registerReceiver(triggerReceiver, IntentFilter().apply {
            addAction(UiModeManager.ACTION_ENTER_CAR_MODE)
        })
    }

    private fun registerNetworkCallback() {
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (currentSsid()?.startsWith("DIRECT-", ignoreCase = true) == true) {
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
        lastAutoLinkNetwork != null ||
                currentSsid()?.startsWith("DIRECT-", ignoreCase = true) == true

    private fun launchAutoLink(skipCheck: Boolean = false) {
        val now = System.currentTimeMillis()
        if (now - lastLaunchTime < DEBOUNCE_MS) return
        if (!skipCheck && isAutoLinkConnected()) return

        val launchIntent = packageManager.getLaunchIntentForPackage(AUTOLINK_PACKAGE)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
            ?: return

        lastLaunchTime = now
        enableWifiAndFindDevice {
            wakeScreen()
            startActivity(launchIntent)
            val accessibility = AutoLinkAccessibilityService.instance
            if (accessibility != null) {
                accessibility.startConnecting()
            } else {
                handler.postDelayed({
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    })
                }, RETURN_DELAY_MS)
            }
        }
    }

    private fun enableWifiAndFindDevice(onFound: () -> Unit) {
        stopWifiScan()
        if (wifiManager.isWifiEnabled) {
            startWifiScan(onFound)
        } else {
            enableWifiViaRoot()
            pollUntilWifiEnabled(onFound)
        }
    }

    private fun pollUntilWifiEnabled(onFound: () -> Unit, attempt: Int = 0) {
        if (wifiManager.isWifiEnabled) {
            startWifiScan(onFound)
        } else if (attempt < 10) {
            handler.postDelayed({ pollUntilWifiEnabled(onFound, attempt + 1) }, 1000)
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
    private fun startWifiScan(onFound: () -> Unit) {
        val scanTimeout = Runnable { stopWifiScan() }
        handler.postDelayed(scanTimeout, SCAN_TIMEOUT_MS)

        wifiScanReceiver = object : BroadcastReceiver() {
            @Suppress("DEPRECATION", "MissingPermission")
            override fun onReceive(context: Context, intent: Intent) {
                val found = wifiManager.scanResults.any {
                    it.SSID.startsWith("direct-connect-", ignoreCase = true)
                }
                if (found) {
                    handler.removeCallbacks(scanTimeout)
                    stopWifiScan()
                    onFound()
                } else {
                    wifiManager.startScan()
                }
            }
        }
        registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        wifiManager.startScan()
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
                NotificationChannel(CHANNEL_ID, "AutoLink Monitor", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VF3 Smart")
            .setContentText("AutoLink Pro monitor active")
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
        stopWifiScan()
        unregisterReceiver(triggerReceiver)
        getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        private const val TAG = "AutoLinkService"
        private const val CHANNEL_ID = "autolink_monitor"
        private const val NOTIFICATION_ID = 2001
        private const val AUTOLINK_PACKAGE = "com.link.autolink.pro"
        private const val RETURN_DELAY_MS = 2000L
        private const val DEBOUNCE_MS = 5000L
        private const val WAKE_TIMEOUT_MS = 35_000L
        private const val SCAN_TIMEOUT_MS = 60_000L
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
