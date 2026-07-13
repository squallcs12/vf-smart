package com.daotranbang.vfsmart.autolink

import android.util.Log

/**
 * Keeps the phone screen on and unlocked for the whole Android Auto session, so the car
 * display never shows a blanked or locked screen. MediaProjection keeps capturing even
 * after the display times out, so without this the mirror shows the lock screen once the
 * phone sleeps. Driven by the `CarConnection` observer in `AutoLinkService`
 * (connect → [keepAwake], disconnect → [release]).
 *
 * Rooted head unit only (the S20+): uses `su` to wake the display, dismiss a non-secure
 * keyguard, and enable "stay awake while plugged in". On a non-rooted device the `su`
 * call fails and this is a no-op. A secure lock (PIN/pattern) still can't be auto-cleared.
 */
object ScreenAwakeController {

    private const val TAG = "ScreenAwake"

    /** Wake + unlock now, and keep the screen on for the mirroring session. */
    fun keepAwake() = runSu(
        "keepAwake",
        "svc power stayon true",   // no display timeout while plugged in
        "input keyevent 224",      // KEYCODE_WAKEUP — turn the screen on if it was off
        "wm dismiss-keyguard",     // clear a non-secure keyguard
    )

    /** Restore the normal display-timeout behavior when mirroring stops. */
    fun release() = runSu(
        "release",
        "svc power stayon false",
    )

    private fun runSu(label: String, vararg commands: String) {
        Thread {
            try {
                val process = Runtime.getRuntime().exec("su")
                process.outputStream.bufferedWriter().use { w ->
                    commands.forEach { w.write(it); w.write("\n") }
                    w.write("exit\n")
                    w.flush()
                }
                val ok = process.waitFor() == 0
                Log.i(TAG, if (ok) "$label ok" else "$label — su returned non-zero")
            } catch (e: Exception) {
                Log.i(TAG, "$label — root unavailable: ${e.message}")
            }
        }.start()
    }
}
