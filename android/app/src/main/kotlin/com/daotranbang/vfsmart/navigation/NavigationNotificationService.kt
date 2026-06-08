package com.daotranbang.vfsmart.navigation

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
import com.daotranbang.vfsmart.autolink.AutoLinkService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Listens for Google Maps navigation notifications and exposes the current
 * turn instruction + distance via [navigationState].
 *
 * Direction is derived from the large icon bitmap (turn arrow) since Maps
 * does not encode left/right/straight in any text extra.
 */
class NavigationNotificationService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private val relaunchRunnable = Runnable {
        if (!autoLinkMirroringActive) {
            Log.i(TAG, "Mirroring still inactive after grace period — triggering relaunch")
            AutoLinkService.triggerLaunch(this, skipCheck = false)
        } else {
            Log.d(TAG, "Mirroring restored within grace period — relaunch cancelled")
        }
    }

    companion object {
        private val _navigationState = MutableStateFlow(NavigationState())
        val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

        private const val MAPS_PACKAGE      = "com.google.android.apps.maps"
        private const val AUTOLINK_PACKAGE  = "com.link.autolink.pro"
        private const val TAG               = "NavNotifService"
        private const val RELAUNCH_GRACE_MS = 3_000L

        @Volatile @JvmField var autoLinkMirroringActive = false
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.w(TAG, "=== LISTENER CONNECTED ===")
        autoLinkMirroringActive = false
        activeNotifications?.forEach { sbn ->
            processNotification(sbn)
            if (sbn.packageName == AUTOLINK_PACKAGE && isAutoLinkMirroring(sbn)) {
                autoLinkMirroringActive = true
                Log.i(TAG, "AutoLink Mirroring notification already active on connect")
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        handler.removeCallbacks(relaunchRunnable)
        Log.w(TAG, "=== LISTENER DISCONNECTED ===")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        processNotification(sbn)
        if (sbn.packageName == AUTOLINK_PACKAGE && isAutoLinkMirroring(sbn)) {
            autoLinkMirroringActive = true
            handler.removeCallbacks(relaunchRunnable)
            Log.i(TAG, "AutoLink Mirroring notification posted")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == MAPS_PACKAGE) _navigationState.value = NavigationState()
        if (sbn.packageName == AUTOLINK_PACKAGE && autoLinkMirroringActive && isAutoLinkMirroring(sbn)) {
            autoLinkMirroringActive = false
            Log.i(TAG, "AutoLink Mirroring notification removed — scheduling relaunch check in ${RELAUNCH_GRACE_MS}ms")
            handler.removeCallbacks(relaunchRunnable)
            handler.postDelayed(relaunchRunnable, RELAUNCH_GRACE_MS)
        }
    }

    private fun isAutoLinkMirroring(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val body  = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        return title.contains("Mirroring", ignoreCase = true) ||
               body.contains("Mirroring", ignoreCase = true)
    }

    private fun processNotification(sbn: StatusBarNotification) {
        if (sbn.packageName != MAPS_PACKAGE) return

        val extras = sbn.notification.extras
        // Maps stores these as CharSequence — getString() always returns null
        val titleText  = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val bodyText   = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val subText    = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim() ?: ""
        val tickerText = sbn.notification.tickerText?.toString()?.trim() ?: ""

        // EXTRA_TITLE is usually the distance ("300 m") but some Maps versions
        // put the maneuver there. Pick whichever field looks like a distance.
        val distance = listOf(titleText, bodyText, subText)
            .firstOrNull { it.matches(Regex("""^\d.*""")) }
            ?.takeIf { it.isNotBlank() }

        // Try all text fields for maneuver keywords; ticker often has the full instruction.
        val allText = "$tickerText $titleText $subText $bodyText"
        val direction = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            directionFromIcon(sbn.notification.getLargeIcon())
        } else null) ?: NavDirectionParser.parse(allText)

        Log.w(TAG, "Maps nav — title=$titleText body=$bodyText sub=$subText ticker=$tickerText direction=$direction")

        if (distance == null) return

        _navigationState.value = NavigationState(
            isActive = true,
            maneuver = NavDirectionParser.label(direction),
            distance = distance,
            direction = direction
        )
    }

    // ── Icon analysis ─────────────────────────────────────────────────────────
    // Maps turn icons are white arrows on a coloured background.
    // Compare the fraction of bright pixels in the left vs right half to
    // determine which way the arrow points.

    @RequiresApi(Build.VERSION_CODES.M)
    private fun directionFromIcon(icon: Icon?): NavigationState.Direction? {
        if (icon == null) return null
        return try {
            val bitmap = iconToBitmap(icon) ?: return null
            val w = bitmap.width
            val h = bitmap.height
            val mid = w / 2

            // Sample the TOP third — that's where the arrowhead points.
            // Count all non-transparent pixels; no brightness filter because
            // Maps uses coloured arrows (blue/green) that aren't "bright" by luma.
            var leftCount = 0
            var rightCount = 0
            for (y in 0 until h / 3) {
                for (x in 0 until w) {
                    if (Color.alpha(bitmap.getPixel(x, y)) > 32) {
                        if (x < mid) leftCount++ else rightCount++
                    }
                }
            }
            bitmap.recycle()

            val total = (leftCount + rightCount).toFloat()
            Log.w(TAG, "Icon top-third — left=$leftCount right=$rightCount total=$total")
            if (total < 20) return null   // icon is nearly fully transparent in top section

            val leftRatio = leftCount / total
            when {
                leftRatio > 0.60f -> NavigationState.Direction.LEFT
                leftRatio < 0.40f -> NavigationState.Direction.RIGHT
                else              -> NavigationState.Direction.STRAIGHT
            }
        } catch (e: Exception) {
            Log.w(TAG, "Icon analysis failed: ${e.message}")
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun iconToBitmap(icon: Icon): Bitmap? {
        val drawable = icon.loadDrawable(this) ?: return null
        if (drawable is BitmapDrawable) return drawable.bitmap
        // Cap at 128×128 and use RGB_565 (2 bytes/px vs 4) to keep allocation small
        val raw = drawable.intrinsicWidth.coerceAtLeast(64)
        val scale = if (raw > 128) 128f / raw else 1f
        val w = (drawable.intrinsicWidth.coerceAtLeast(64) * scale).toInt()
        val h = (drawable.intrinsicHeight.coerceAtLeast(64) * scale).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bmp
    }

}
