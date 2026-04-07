package com.vinfast.vf3smart.navigation

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Listens for Google Maps navigation notifications and exposes the current
 * turn instruction + distance via [navigationState].
 *
 * The user must grant notification access in Settings > Notification Access.
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
        val active = activeNotifications
        Log.w(TAG, "Active notification count: ${active?.size ?: 0}")
        active?.forEach { sbn ->
            Log.w(TAG, "  pkg=${sbn.packageName} title=${sbn.notification.extras.getString(Notification.EXTRA_TITLE)} text=${sbn.notification.extras.getString(Notification.EXTRA_TEXT)}")
            processNotification(sbn)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "=== LISTENER DISCONNECTED ===")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.w(TAG, "Notification posted: pkg=${sbn.packageName} title=${sbn.notification.extras.getString(Notification.EXTRA_TITLE)}")
        processNotification(sbn)
    }

    private fun processNotification(sbn: StatusBarNotification) {
        if (sbn.packageName != MAPS_PACKAGE) return

        val extras = sbn.notification.extras
        // Maps stores these as CharSequence, getString() returns null
        val distance = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?.takeIf { it.isNotBlank() }
        val maneuver = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?.takeIf { it.isNotBlank() }

        Log.w(TAG, "Maps nav — distance=$distance maneuver=$maneuver")

        if (maneuver == null && distance == null) return

        _navigationState.value = NavigationState(
            isActive = true,
            maneuver = (maneuver ?: "").uppercase(),
            distance = distance ?: "",
            direction = parseDirection(maneuver ?: "")
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == MAPS_PACKAGE) {
            _navigationState.value = NavigationState()
        }
    }

    private fun parseDirection(maneuver: String): NavigationState.Direction {
        val lower = maneuver.lowercase()
        return when {
            "u-turn" in lower || "uturn" in lower -> NavigationState.Direction.U_TURN
            "left" in lower                        -> NavigationState.Direction.LEFT
            "right" in lower                       -> NavigationState.Direction.RIGHT
            "roundabout" in lower || "rotary" in lower -> NavigationState.Direction.ROUNDABOUT
            else                                   -> NavigationState.Direction.STRAIGHT
        }
    }
}
