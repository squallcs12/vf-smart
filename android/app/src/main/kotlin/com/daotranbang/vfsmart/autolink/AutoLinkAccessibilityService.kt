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

    private enum class State { IDLE, CLICKING_HELP, FINDING_DEVICE, FINDING_START_NOW }

    private var state = State.IDLE
    private val handler = Handler(Looper.getMainLooper())
    private var onStartNowClicked: (() -> Unit)? = null
    var helpClickCount = 0

    private val timeoutRunnable = Runnable {
        Log.w(TAG, "startConnecting timed out — no device or 'Start now' found")
        state = State.IDLE
    }

    private val pollForHelpRunnable = object : Runnable {
        override fun run() {
            if (state != State.CLICKING_HELP) return
            val root = rootInActiveWindow
            val node = root?.let { findNodeByContentDesc(it, "Help") }
            if (node != null) {
                helpClickCount++
                Log.d(TAG, "Help button found — clicking ($helpClickCount/2)")
                clickNodeOrParent(node)
                @Suppress("DEPRECATION") node.recycle()
                handler.postDelayed({
                    Log.d(TAG, "pressing Back after Help click $helpClickCount")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    if (helpClickCount < 2) {
                        handler.postDelayed(this, POLL_INTERVAL_MS)
                    } else {
                        state = State.FINDING_DEVICE
                        handler.post(pollForDeviceRunnable)
                    }
                }, 1000)
            } else {
                Log.v(TAG, "Help button not yet visible — retrying in ${POLL_INTERVAL_MS}ms")
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    private val pollForDeviceRunnable = object : Runnable {
        override fun run() {
            if (state != State.FINDING_DEVICE) return
            val node = rootInActiveWindow
                ?.findAccessibilityNodeInfosByText("DIRECT-phonelink-112391")
                ?.firstOrNull()
            if (node != null) {
                Log.d(TAG, "device node found — clicking DIRECT-phonelink-112391")
                state = State.FINDING_START_NOW
                clickNodeOrParent(node)
                @Suppress("DEPRECATION") node.recycle()
                handler.post(pollForStartNowRunnable)
            } else {
                Log.v(TAG, "device node not yet visible — retrying in ${POLL_INTERVAL_MS}ms")
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    private val pollForStartNowRunnable = object : Runnable {
        override fun run() {
            if (state != State.FINDING_START_NOW) return
            val node = findStartNowNode()
            if (node != null) {
                Log.d(TAG, "'Bắt đầu ngay' found — clicking and returning to MainActivity")
                clickNodeOrParent(node)
                @Suppress("DEPRECATION") node.recycle()
                handler.removeCallbacks(timeoutRunnable)
                state = State.IDLE
                handler.postDelayed({
                    Log.i(TAG, "AutoLink connection complete — Android Auto started successfully")
                    startActivity(Intent(this@AutoLinkAccessibilityService, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    })
                    releaseWakeLock()
                    onStartNowClicked?.invoke()
                    onStartNowClicked = null
                }, 1000)
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
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            packageNames = arrayOf(AUTOLINK_PACKAGE, "com.android.systemui")
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Both FINDING_DEVICE and FINDING_START_NOW are handled by their poll runnables.
    }

    private fun findNodeByContentDesc(node: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString() == desc) return node
        for (i in 0 until node.childCount) {
            val found = findNodeByContentDesc(node.getChild(i) ?: continue, desc)
            if (found != null) return found
        }
        return null
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo) {
        if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = false

    fun startConnecting(onStartNowClicked: (() -> Unit)? = null) {
        this.onStartNowClicked = onStartNowClicked
        Log.d(TAG, "startConnecting — clicking Help×2 then polling for DIRECT-phonelink-112391")
        state = State.CLICKING_HELP
        handler.removeCallbacks(timeoutRunnable)
        handler.removeCallbacks(pollForHelpRunnable)
        handler.removeCallbacks(pollForDeviceRunnable)
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)
        handler.post(pollForHelpRunnable)
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onInterrupt() {
        state = State.IDLE
        helpClickCount = 0
        handler.removeCallbacksAndMessages(null)
        handler.removeCallbacks(pollForHelpRunnable)
        handler.removeCallbacks(pollForDeviceRunnable)
        handler.removeCallbacks(pollForStartNowRunnable)
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
