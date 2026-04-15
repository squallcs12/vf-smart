package com.daotranbang.vfsmart.navigation

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
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

    companion object {
        private val _navigationState = MutableStateFlow(NavigationState())
        val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

        private const val MAPS_PACKAGE = "com.google.android.apps.maps"
        private const val TAG = "NavNotifService"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.w(TAG, "=== LISTENER CONNECTED ===")
        activeNotifications?.forEach { processNotification(it) }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "=== LISTENER DISCONNECTED ===")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = processNotification(sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == MAPS_PACKAGE) _navigationState.value = NavigationState()
    }

    private fun processNotification(sbn: StatusBarNotification) {
        if (sbn.packageName != MAPS_PACKAGE) return

        val extras = sbn.notification.extras
        // Maps stores these as CharSequence — getString() always returns null
        val distance = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?.takeIf { it.isNotBlank() }
        val streetText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // Direction comes from the large icon (turn arrow bitmap).
        // Fall back to parsing the street text if icon analysis fails.
        val direction = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            directionFromIcon(sbn.notification.getLargeIcon())
        } else null) ?: parseDirectionFromText(streetText)

        Log.w(TAG, "Maps nav — distance=$distance street=$streetText direction=$direction")

        if (distance == null) return

        _navigationState.value = NavigationState(
            isActive = true,
            maneuver = directionLabel(direction),
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
        val w = drawable.intrinsicWidth.coerceAtLeast(64)
        val h = drawable.intrinsicHeight.coerceAtLeast(64)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bmp
    }

    // ── Text fallback ─────────────────────────────────────────────────────────

    private fun parseDirectionFromText(text: String): NavigationState.Direction {
        val lower = text.lowercase()
        return when {
            "u-turn" in lower || "uturn" in lower          -> NavigationState.Direction.U_TURN
            "left" in lower                                 -> NavigationState.Direction.LEFT
            "right" in lower                                -> NavigationState.Direction.RIGHT
            "roundabout" in lower || "rotary" in lower      -> NavigationState.Direction.ROUNDABOUT
            else                                            -> NavigationState.Direction.STRAIGHT
        }
    }

    private fun directionLabel(direction: NavigationState.Direction): String = when (direction) {
        NavigationState.Direction.LEFT       -> "TURN LEFT"
        NavigationState.Direction.RIGHT      -> "TURN RIGHT"
        NavigationState.Direction.U_TURN     -> "U-TURN"
        NavigationState.Direction.ROUNDABOUT -> "ROUNDABOUT"
        NavigationState.Direction.STRAIGHT   -> "CONTINUE"
    }
}
