package com.daotranbang.vfsmart.autolink

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.daotranbang.vfsmart.ui.MainActivity

class AutoLinkAccessibilityService : AccessibilityService() {

    private enum class State { IDLE, FINDING_DEVICE, FINDING_START_NOW }

    private var state = State.IDLE
    private var clickCount = 0
    private var lastClickKeyCode = 0
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager get() = getSystemService(AudioManager::class.java)

    private val timeoutRunnable = Runnable {
        Log.w(TAG, "startConnecting timed out — no device or 'Start now' found")
        state = State.IDLE
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
            val root = rootInActiveWindow
            val node = (root?.findAccessibilityNodeInfosByText("Bắt đầu ngay")?.firstOrNull()
                ?: root?.findAccessibilityNodeInfosByText("Start now")?.firstOrNull())
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
                }, 1000)
            } else {
                Log.v(TAG, "'Bắt đầu ngay' not yet visible — retrying in ${POLL_INTERVAL_MS}ms")
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
            @Suppress("DEPRECATION") root?.recycle()
        }
    }

    private val singleClickRunnable = Runnable {
        val now = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, lastClickKeyCode, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, lastClickKeyCode, 0))
        clickCount = 0
    }

    override fun onServiceConnected() {
        instance = this
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            packageNames = arrayOf(AUTOLINK_PACKAGE)
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Both FINDING_DEVICE and FINDING_START_NOW are handled by their poll runnables.
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo) {
        if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    clickCount++
                    lastClickKeyCode = event.keyCode
                    when (clickCount) {
                        1 -> handler.postDelayed(singleClickRunnable, DOUBLE_CLICK_MS)
                        2 -> {
                            handler.removeCallbacks(singleClickRunnable)
                            clickCount = 0
                            AutoLinkService.triggerLaunch(this, skipCheck = true)
                        }
                    }
                }
                return true // always consume; single click re-dispatched after timeout
            }
        }
        return false
    }

    fun startConnecting() {
        Log.d(TAG, "startConnecting — polling for DIRECT-phonelink-112391")
        state = State.FINDING_DEVICE
        handler.removeCallbacks(timeoutRunnable)
        handler.removeCallbacks(pollForDeviceRunnable)
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)
        handler.post(pollForDeviceRunnable)
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onInterrupt() {
        state = State.IDLE
        clickCount = 0
        handler.removeCallbacksAndMessages(null)
        handler.removeCallbacks(pollForDeviceRunnable)
        handler.removeCallbacks(pollForStartNowRunnable)
        releaseWakeLock()
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
        private const val DOUBLE_CLICK_MS = 1000L
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
