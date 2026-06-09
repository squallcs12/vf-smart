package com.daotranbang.vfsmart.autolink

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.daotranbang.vfsmart.ui.MainActivity

class AutoLinkAccessibilityService : AccessibilityService() {

    private enum class State { IDLE, SWITCHING_TO_USB }

    private var state = State.IDLE
    private val handler = Handler(Looper.getMainLooper())
    private var onConnected: (() -> Unit)? = null
    private var usbSwitchRetries = 0

    private val timeoutRunnable = Runnable {
        Log.w(TAG, "startConnecting timed out — USB toggle never appeared")
        state = State.IDLE
    }

    // Once AutoLink Pro opens, just switch it to USB mode. `action_button_usb` is shown
    // while in WiFi mode (tap it to go USB); `action_button_wifi` is shown when USB mode
    // is already active (nothing to do). No device picking, no "Start now" dialog.
    private val pollForUsbToggleRunnable = object : Runnable {
        override fun run() {
            if (state != State.SWITCHING_TO_USB) return
            val root = rootInActiveWindow
            val usbBtn = root?.findAccessibilityNodeInfosByViewId(
                "com.link.autolink.pro:id/action_button_usb"
            )?.firstOrNull()
            if (usbBtn != null) {
                Log.d(TAG, "USB toggle found — clicking to switch to USB mode")
                clickNodeOrParent(usbBtn)
                @Suppress("DEPRECATION") usbBtn.recycle()
                finishConnection()
                return
            }
            val wifiBtn = root?.findAccessibilityNodeInfosByViewId(
                "com.link.autolink.pro:id/action_button_wifi"
            )?.firstOrNull()
            if (wifiBtn != null) {
                Log.d(TAG, "WiFi toggle visible — already in USB mode, nothing to do")
                @Suppress("DEPRECATION") wifiBtn.recycle()
                finishConnection()
            } else if (usbSwitchRetries++ < USB_SWITCH_MAX_RETRIES) {
                Log.v(TAG, "mode buttons not yet visible — retry $usbSwitchRetries/${USB_SWITCH_MAX_RETRIES}")
                handler.postDelayed(this, POLL_INTERVAL_MS)
            } else {
                Log.w(TAG, "mode buttons not found after retries — finishing anyway")
                finishConnection()
            }
        }
    }

    private fun finishConnection() {
        state = State.IDLE
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed({
            Log.i(TAG, "AutoLink connection complete — returning to MainActivity")
            startActivity(Intent(this@AutoLinkAccessibilityService, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            onConnected?.invoke()
            onConnected = null
        }, 1000)
    }

    override fun onServiceConnected() {
        instance = this
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageNames = arrayOf(AUTOLINK_PACKAGE)
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = POLL_INTERVAL_MS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // The USB-mode switch is handled by pollForUsbToggleRunnable.
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo) {
        if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = false

    fun startConnecting(onConnected: (() -> Unit)? = null) {
        this.onConnected = onConnected
        usbSwitchRetries = 0
        Log.d(TAG, "startConnecting — switching AutoLink to USB mode")
        state = State.SWITCHING_TO_USB
        handler.removeCallbacks(timeoutRunnable)
        handler.removeCallbacks(pollForUsbToggleRunnable)
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)
        handler.post(pollForUsbToggleRunnable)
    }

    override fun onInterrupt() {
        state = State.IDLE
        handler.removeCallbacksAndMessages(null)
        handler.removeCallbacks(pollForUsbToggleRunnable)
        onConnected = null
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
        private const val TIMEOUT_MS = 30_000L
        private const val POLL_INTERVAL_MS = 500L
        private const val USB_SWITCH_MAX_RETRIES = 6

        fun enableViaRoot(context: Context) {
            if (instance != null) return
            // Play policy: do not activate the accessibility service until the user
            // has seen the prominent disclosure and agreed (see AccessibilityDisclosure).
            if (!AccessibilityDisclosure.isAccepted(context)) {
                Log.i(TAG, "Accessibility disclosure not yet accepted — skipping enable")
                return
            }
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
