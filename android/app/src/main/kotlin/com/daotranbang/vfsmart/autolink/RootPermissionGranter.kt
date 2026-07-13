package com.daotranbang.vfsmart.autolink

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.daotranbang.vfsmart.navigation.NavigationNotificationService

/**
 * Grants every permission the app needs in one `su` session on the rooted head
 * unit — so no permission dialog, accessibility disclosure, or notification-access
 * screen ever appears there. On a non-rooted device (e.g. the S20+) the `su` call
 * fails and the caller falls back to the normal in-app prompts.
 *
 * Covers:
 *  - dangerous runtime permissions (`pm grant`)
 *  - notification-listener access for Google Maps directions (`cmd notification allow_listener`)
 *  - the AutoLink accessibility service (`settings put secure …`)
 *  - PROJECT_MEDIA screen-capture consent for the AutoLink Pro mirroring app (`appops set`)
 */
object RootPermissionGranter {

    private const val TAG = "RootPermGranter"

    /**
     * Dangerous runtime permissions the app needs, by SDK level. Shared by the
     * root `pm grant` path ([grantAll]) and MainActivity's in-app request path so
     * the two never drift. Car comms are over WiFi (HTTP + WebSocket), so no
     * Bluetooth permissions are required.
     */
    fun requiredRuntimePermissions(): List<String> = buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Runs all grant commands via root. [runtimePermissions] is the SDK-dependent
     * list of dangerous permissions to `pm grant` (passed in so it stays in sync
     * with the runtime-request path in MainActivity).
     *
     * Returns true if root was available and the commands ran. Must be called off
     * the main thread — `su` blocks.
     */
    fun grantAll(context: Context, runtimePermissions: List<String>): Boolean {
        val pkg = context.packageName
        val a11y = ComponentName(context, AutoLinkAccessibilityService::class.java).flattenToString()
        val notif = ComponentName(context, NavigationNotificationService::class.java).flattenToString()

        val commands = buildList {
            // Dangerous runtime permissions — granted immediately, no system dialog.
            runtimePermissions.forEach { add("pm grant $pkg $it") }
            // Notification listener (reads Google Maps turn-by-turn for the ODO screen).
            add("cmd notification allow_listener $notif")
            // AutoLink UI-automation accessibility service.
            add("settings put secure enabled_accessibility_services $a11y")
            add("settings put secure accessibility_enabled 1")
            // Screen-capture consent so the mirroring app never shows "Start now".
            add("appops set ${AutoLinkAccessibilityService.AUTOLINK_PACKAGE} PROJECT_MEDIA allow")
        }

        return try {
            val process = Runtime.getRuntime().exec("su")
            process.outputStream.bufferedWriter().use { w ->
                commands.forEach { w.write(it); w.write("\n") }
                w.write("exit\n")
                w.flush()
            }
            val ok = process.waitFor() == 0
            Log.i(TAG, if (ok) "granted all permissions via root" else "su returned non-zero")
            ok
        } catch (e: Exception) {
            Log.i(TAG, "root unavailable — falling back to in-app prompts: ${e.message}")
            false
        }
    }
}