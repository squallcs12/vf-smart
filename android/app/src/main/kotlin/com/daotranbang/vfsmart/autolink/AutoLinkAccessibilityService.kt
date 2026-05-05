package com.daotranbang.vfsmart.autolink

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.daotranbang.vfsmart.ui.MainActivity

class AutoLinkAccessibilityService : AccessibilityService() {

    private enum class State { IDLE, CHECKING_MODE, CLICKING_WIFI_TOGGLE, REFRESHING_SCAN, FINDING_DEVICE, FINDING_START_NOW, SWITCHING_TO_USB }

    private var state = State.IDLE
    private val handler = Handler(Looper.getMainLooper())
    private var onStartNowClicked: (() -> Unit)? = null
    private var usbSwitchRetries = 0
    private var scanRefreshCount = 0

    private val timeoutRunnable = Runnable {
        Log.w(TAG, "startConnecting timed out — no device or 'Start now' found")
        state = State.IDLE
        setWindowScanEnabled(false)
    }

    private val pollForModeCheckRunnable = object : Runnable {
        override fun run() {
            if (state != State.CHECKING_MODE) return
            val root = rootInActiveWindow ?: run {
                handler.postDelayed(this, POLL_INTERVAL_MS)
                return
            }
            val wifiBtn = root.findAccessibilityNodeInfosByViewId(
                "com.link.autolink.pro:id/action_button_wifi"
            )?.firstOrNull()
            val usbBtn = root.findAccessibilityNodeInfosByViewId(
                "com.link.autolink.pro:id/action_button_usb"
            )?.firstOrNull()
            when {
                wifiBtn != null -> {
                    // USB mode active — switch to WiFi directly
                    Log.d(TAG, "USB mode active — switching to WiFi")
                    clickNodeOrParent(wifiBtn)
                    @Suppress("DEPRECATION") wifiBtn.recycle()
                    @Suppress("DEPRECATION") usbBtn?.recycle()
                    handler.postDelayed({
                        state = State.FINDING_DEVICE
                        handler.post(pollForDeviceRunnable)
                    }, 1000)
                }
                usbBtn != null -> {
                    // WiFi mode already active — switch to USB first, then back to WiFi
                    Log.d(TAG, "WiFi already active — toggling USB first, then back to WiFi")
                    clickNodeOrParent(usbBtn)
                    @Suppress("DEPRECATION") usbBtn.recycle()
                    state = State.CLICKING_WIFI_TOGGLE
                    handler.postDelayed({ handler.post(pollForWifiToggleRunnable) }, 1000)
                }
                else -> {
                    Log.v(TAG, "mode buttons not yet visible — retrying in ${POLL_INTERVAL_MS}ms")
                    handler.postDelayed(this, POLL_INTERVAL_MS)
                }
            }
        }
    }

    private val pollForWifiToggleRunnable = object : Runnable {
        override fun run() {
            if (state != State.CLICKING_WIFI_TOGGLE) return
            val root = rootInActiveWindow
            val node = root?.findAccessibilityNodeInfosByViewId(
                "com.link.autolink.pro:id/action_button_wifi"
            )?.firstOrNull()
            if (node != null) {
                Log.d(TAG, "WiFi toggle found — clicking to switch to WiFi mode")
                clickNodeOrParent(node)
                @Suppress("DEPRECATION") node.recycle()
                handler.postDelayed({
                    state = State.FINDING_DEVICE
                    handler.post(pollForDeviceRunnable)
                }, 1000)
            } else {
                Log.v(TAG, "WiFi toggle not yet visible — retrying in ${POLL_INTERVAL_MS}ms")
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    private val pollForUsbToggleRunnable = object : Runnable {
        override fun run() {
            if (state != State.SWITCHING_TO_USB) return
            val root = rootInActiveWindow
            val node = root?.findAccessibilityNodeInfosByViewId(
                "com.link.autolink.pro:id/action_button_usb"
            )?.firstOrNull()
            if (node != null) {
                Log.d(TAG, "USB toggle found — clicking to switch back to USB mode")
                clickNodeOrParent(node)
                @Suppress("DEPRECATION") node.recycle()
                finishConnection()
            } else if (usbSwitchRetries++ < USB_SWITCH_MAX_RETRIES) {
                Log.v(TAG, "USB toggle not yet visible — retry $usbSwitchRetries/${USB_SWITCH_MAX_RETRIES}")
                handler.postDelayed(this, POLL_INTERVAL_MS)
            } else {
                Log.w(TAG, "USB toggle not found after retries — skipping USB switch")
                finishConnection()
            }
        }
    }

    private fun finishConnection() {
        state = State.IDLE
        setWindowScanEnabled(false)
        handler.postDelayed({
            Log.i(TAG, "AutoLink connection complete — returning to MainActivity")
            startActivity(Intent(this@AutoLinkAccessibilityService, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            releaseWakeLock()
            onStartNowClicked?.invoke()
            onStartNowClicked = null
        }, 1000)
    }

    private val pollForRefreshScanRunnable: Runnable = object : Runnable {
        override fun run() {
            if (state != State.REFRESHING_SCAN) return
            val root = rootInActiveWindow ?: run {
                handler.postDelayed(this, POLL_INTERVAL_MS)
                return
            }
            val usbBtn = root.findAccessibilityNodeInfosByViewId(
                "com.link.autolink.pro:id/action_button_usb"
            )?.firstOrNull()
            if (usbBtn != null) {
                Log.d(TAG, "refresh scan — clicking USB (refresh $scanRefreshCount/$MAX_SCAN_REFRESHES)")
                clickNodeOrParent(usbBtn)
                @Suppress("DEPRECATION") usbBtn.recycle()
                state = State.CLICKING_WIFI_TOGGLE
                handler.postDelayed({ handler.post(pollForWifiToggleRunnable) }, 1000)
            } else {
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    private val pollForDeviceRunnable = object : Runnable {
        override fun run() {
            if (state != State.FINDING_DEVICE) return
            val root = rootInActiveWindow
            val deviceNode = root?.findAccessibilityNodeInfosByText("DIRECT-phonelink-112391")?.firstOrNull()
            if (deviceNode != null) {
                Log.d(TAG, "device node found — clicking DIRECT-phonelink-112391")
                state = State.FINDING_START_NOW
                clickNodeOrParent(deviceNode)
                @Suppress("DEPRECATION") deviceNode.recycle()
                handler.post(pollForStartNowRunnable)
            } else {
                val cantFind = root?.findAccessibilityNodeInfosByText("Can't find any devices")?.firstOrNull()
                if (cantFind != null && scanRefreshCount < MAX_SCAN_REFRESHES) {
                    scanRefreshCount++
                    Log.w(TAG, "\"Can't find any devices\" — USB→WiFi refresh (attempt $scanRefreshCount/$MAX_SCAN_REFRESHES)")
                    @Suppress("DEPRECATION") cantFind.recycle()
                    state = State.REFRESHING_SCAN
                    handler.post(pollForRefreshScanRunnable)
                } else {
                    @Suppress("DEPRECATION") cantFind?.recycle()
                    Log.v(TAG, "device node not yet visible — retrying in ${POLL_INTERVAL_MS}ms")
                    handler.postDelayed(this, POLL_INTERVAL_MS)
                }
            }
        }
    }

    private val pollForStartNowRunnable = object : Runnable {
        override fun run() {
            if (state != State.FINDING_START_NOW) return
            val node = findStartNowNode()
            if (node != null) {
                Log.d(TAG, "'Bắt đầu ngay' found — clicking, then switching back to USB")
                clickNodeOrParent(node)
                @Suppress("DEPRECATION") node.recycle()
                handler.removeCallbacks(timeoutRunnable)
                usbSwitchRetries = 0
                state = State.SWITCHING_TO_USB
                handler.postDelayed({ handler.post(pollForUsbToggleRunnable) }, 1000)
            } else {
                Log.v(TAG, "'Bắt đầu ngay' not yet visible — retrying in ${POLL_INTERVAL_MS}ms")
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    private fun findStartNowNode(): AccessibilityNodeInfo? {
        // Search all windows (covers system dialogs from com.android.systemui)
        for (window in windows ?: emptyList()) {
            val root = window.root ?: continue
            val node = root.findAccessibilityNodeInfosByText("Bắt đầu ngay")?.firstOrNull()
                ?: root.findAccessibilityNodeInfosByText("Start now")?.firstOrNull()
            @Suppress("DEPRECATION") root.recycle()
            if (node != null) return node
        }
        // Fallback to active window
        val root = rootInActiveWindow ?: return null
        val node = root.findAccessibilityNodeInfosByText("Bắt đầu ngay")?.firstOrNull()
            ?: root.findAccessibilityNodeInfosByText("Start now")?.firstOrNull()
        @Suppress("DEPRECATION") root.recycle()
        return node
    }


    override fun onServiceConnected() {
        instance = this
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageNames = arrayOf(AUTOLINK_PACKAGE, "com.android.systemui")
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = POLL_INTERVAL_MS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Both FINDING_DEVICE and FINDING_START_NOW are handled by their poll runnables.
    }

    private fun setWindowScanEnabled(enabled: Boolean) {
        serviceInfo = serviceInfo?.apply {
            flags = if (enabled)
                flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            else
                flags and AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS.inv()
        }
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo) {
        if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = false

    fun startConnecting(onStartNowClicked: (() -> Unit)? = null) {
        this.onStartNowClicked = onStartNowClicked
        scanRefreshCount = 0
        Log.d(TAG, "startConnecting — checking mode then polling for DIRECT-phonelink-112391")
        state = State.CHECKING_MODE
        handler.removeCallbacks(timeoutRunnable)
        handler.removeCallbacks(pollForModeCheckRunnable)
        handler.removeCallbacks(pollForWifiToggleRunnable)
        handler.removeCallbacks(pollForRefreshScanRunnable)
        handler.removeCallbacks(pollForDeviceRunnable)
        handler.removeCallbacks(pollForStartNowRunnable)
        handler.removeCallbacks(pollForUsbToggleRunnable)
        setWindowScanEnabled(true)
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)
        handler.post(pollForModeCheckRunnable)
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onInterrupt() {
        state = State.IDLE
        setWindowScanEnabled(false)
        handler.removeCallbacksAndMessages(null)
        handler.removeCallbacks(pollForModeCheckRunnable)
        handler.removeCallbacks(pollForWifiToggleRunnable)
        handler.removeCallbacks(pollForRefreshScanRunnable)
        handler.removeCallbacks(pollForDeviceRunnable)
        handler.removeCallbacks(pollForStartNowRunnable)
        handler.removeCallbacks(pollForUsbToggleRunnable)
        releaseWakeLock()
        onStartNowClicked = null
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AutoLinkA11y"
        const val AUTOLINK_PACKAGE = "com.link.autolink.pro"
        var instance: AutoLinkAccessibilityService? = null
        var wakeLock: PowerManager.WakeLock? = null
        private const val TIMEOUT_MS = 30_000L
        private const val POLL_INTERVAL_MS = 500L
        private const val USB_SWITCH_MAX_RETRIES = 6
        private const val MAX_SCAN_REFRESHES = 2

        fun enableViaRoot(context: Context) {
            if (instance != null) return
            val component = "${context.packageName}/${AutoLinkAccessibilityService::class.java.name}"
            Thread {
                try {
                    val process = Runtime.getRuntime().exec("su")
                    val writer = process.outputStream.bufferedWriter()
                    writer.write("settings put secure enabled_accessibility_services $component\n")
                    writer.write("settings put secure accessibility_enabled 1\n")
                    writer.flush()
                    writer.close()
                    process.waitFor()
                } catch (_: Exception) {}
            }.start()
        }
    }
}
