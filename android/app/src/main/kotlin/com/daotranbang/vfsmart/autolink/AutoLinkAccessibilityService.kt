package com.daotranbang.vfsmart.autolink

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.daotranbang.vfsmart.R
import com.daotranbang.vfsmart.navigation.NavigationNotificationService
import com.daotranbang.vfsmart.ui.MainActivity

class AutoLinkAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var onConnected: (() -> Unit)? = null
    private var mirroringWaitPolls = 0

    // After launching AutoLink Pro, stay on it and poll until its "Mirroring" status-bar
    // notification appears (tracked by NavigationNotificationService). Only then return to
    // our app, so mirroring is confirmed live before we take the foreground.
    private val waitForMirroringRunnable = object : Runnable {
        override fun run() {
            if (NavigationNotificationService.autoLinkMirroringActive) {
                Log.i(TAG, "Mirroring notification seen — returning to app")
                finishConnection()
            } else if (mirroringWaitPolls++ < MIRRORING_WAIT_MAX_POLLS) {
                handler.postDelayed(this, POLL_INTERVAL_MS)
            } else {
                Log.w(TAG, "Mirroring notification not seen within timeout — returning anyway")
                finishConnection()
            }
        }
    }

    private fun finishConnection() {
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
        // No UI automation needed — AutoLink Pro is always in USB mode.
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = false

    fun startConnecting(onConnected: (() -> Unit)? = null) {
        this.onConnected = onConnected
        mirroringWaitPolls = 0
        // AutoLink Pro is always in USB mode and auto-mirrors on launch, so there's no
        // toggle to drive — just wait on AutoLink until its "Mirroring" notification shows,
        // then return to our app.
        Log.d(TAG, "startConnecting — waiting for AutoLink Mirroring notification")
        handler.removeCallbacks(waitForMirroringRunnable)
        handler.post(waitForMirroringRunnable)
    }

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
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
        private const val POLL_INTERVAL_MS = 500L
        // Max polls (×POLL_INTERVAL_MS) to wait for the Mirroring notification ≈ 30 s.
        private const val MIRRORING_WAIT_MAX_POLLS = 60

        /** The flattened component name of this accessibility service. */
        private fun component(context: Context): String =
            ComponentName(context, AutoLinkAccessibilityService::class.java).flattenToString()

        /** Whether this accessibility service is currently enabled in system settings. */
        fun isServiceEnabled(context: Context): Boolean {
            if (instance != null) return true
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val self = component(context)
            return enabled.split(':').any { it.equals(self, ignoreCase = true) }
        }

        /** Opens the system Accessibility settings so the user can enable the service manually. */
        fun openAccessibilitySettings(context: Context) {
            try {
                // Toast floats over the Settings screen, telling the user what to tap.
                Toast.makeText(context, R.string.a11y_enable_hint, Toast.LENGTH_LONG).show()
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not open accessibility settings", e)
            }
        }

        /**
         * Tries to enable the service silently via root (rooted head unit). On a
         * non-rooted device the `su` call fails; [onRootUnavailable] is then invoked
         * on the main thread so the caller can fall back to a manual enable flow.
         */
        fun enableViaRoot(context: Context, onRootUnavailable: (() -> Unit)? = null) {
            if (instance != null) return
            // Play policy: do not activate the accessibility service until the user
            // has seen the prominent disclosure and agreed (see AccessibilityDisclosure).
            if (!AccessibilityDisclosure.isAccepted(context)) {
                Log.i(TAG, "Accessibility disclosure not yet accepted — skipping enable")
                return
            }
            val component = component(context)
            Thread {
                var rooted = false
                try {
                    val process = Runtime.getRuntime().exec("su")
                    val writer = process.outputStream.bufferedWriter()
                    writer.write("settings put secure enabled_accessibility_services $component\n")
                    writer.write("settings put secure accessibility_enabled 1\n")
                    writer.flush()
                    writer.close()
                    rooted = process.waitFor() == 0
                } catch (_: Exception) {}
                if (!rooted && onRootUnavailable != null) {
                    Handler(Looper.getMainLooper()).post(onRootUnavailable)
                }
            }.start()
        }
    }
}
