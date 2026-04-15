package com.daotranbang.vfsmart.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daotranbang.vfsmart.data.model.CarStatus
import com.daotranbang.vfsmart.data.network.WebSocketManager
import com.daotranbang.vfsmart.data.repository.VF3Repository
import com.daotranbang.vfsmart.util.VoiceWarningManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for real-time car status monitoring
 */
@HiltViewModel
class CarStatusViewModel @Inject constructor(
    private val repository: VF3Repository,
    private val voiceWarningManager: VoiceWarningManager
) : ViewModel() {

    companion object {
        private const val TAG = "CarStatusViewModel"
        private const val LIGHT_REMINDER_INTERVAL_MS = 30_000L
    }

    // Track previous car lock state to detect lock events
    private var previousLockState: String? = null

    // Track last light reminder time
    private var lastLightReminderTime = 0L

    // Real-time car status from WebSocket
    val carStatus: StateFlow<CarStatus?> = repository.carStatus.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // WebSocket connection state
    val connectionState: StateFlow<WebSocketManager.ConnectionState> =
        repository.connectionState.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = WebSocketManager.ConnectionState.Disconnected
        )

    init {
        // Connect WebSocket when ViewModel is created
        connectWebSocket()

        // Monitor car status for voice warnings
        monitorCarStatusForWarnings()
    }

    /**
     * Monitor car status changes and trigger voice warnings
     */
    private fun monitorCarStatusForWarnings() {
        viewModelScope.launch {
            carStatus.collect { status ->
                status?.let {
                    checkWindowWarning(it)
                    checkLightReminder(it)
                }
            }
        }
    }

    /**
     * Check if car is locked with windows open and trigger warning
     */
    private fun checkWindowWarning(status: CarStatus) {
        val currentLockState = status.carLockState

        // Detect transition from unlocked to locked
        val justLocked = previousLockState != "locked" && currentLockState == "locked"

        // Update previous state
        previousLockState = currentLockState

        // If car just locked and windows are open, trigger warning
        if (justLocked && status.windows.anyOpen) {
            Log.d(TAG, "Car locked with windows open - triggering voice warning")
            voiceWarningManager.warnWindowsOpen()
        }
    }

    /**
     * Check if headlights should be on but aren't (nighttime + driving + lights off)
     * Mirrors ESP32 light_reminder.cpp logic with 30-second interval
     */
    private fun checkLightReminder(status: CarStatus) {
        // Guard: reminder must be enabled
        if (!status.lightReminderEnabled) {
            lastLightReminderTime = 0L
            return
        }

        // Guard: time must be synced
        if (status.time?.synced != true) return

        // Guard: must be nighttime
        if (!status.time.isNight) {
            lastLightReminderTime = 0L
            return
        }

        // Guard: must be in Drive
        if (status.sensors.gearDrive != 1) {
            lastLightReminderTime = 0L
            return
        }

        // Guard: normal light must be off
        if (status.lights.normalLight != 0) {
            lastLightReminderTime = 0L
            return
        }

        // All conditions met - check interval
        val now = System.currentTimeMillis()
        if (now - lastLightReminderTime < LIGHT_REMINDER_INTERVAL_MS) return

        Log.d(TAG, "Light reminder: headlights off while driving at night")
        voiceWarningManager.warnLightsOff()
        lastLightReminderTime = now
    }

    /**
     * Connect WebSocket for real-time updates
     */
    fun connectWebSocket() {
        repository.connectWebSocket()
    }

    /**
     * Disconnect WebSocket
     */
    fun disconnectWebSocket() {
        repository.disconnectWebSocket()
    }

    override fun onCleared() {
        super.onCleared()
        disconnectWebSocket()
        voiceWarningManager.shutdown()
    }
}
