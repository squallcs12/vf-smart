package com.daotranbang.vfsmart.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daotranbang.vfsmart.autolink.LightReminderSession
import com.daotranbang.vfsmart.data.model.CarStatus
import com.daotranbang.vfsmart.data.network.ConnectionState
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
    }

    // Track previous car lock state to detect lock events
    private var previousLockState: String? = null

    // Real-time car status from BLE
    val carStatus: StateFlow<CarStatus?> = repository.carStatus.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // Car connection state (ws://<ip>/ws stream)
    val connectionState: StateFlow<ConnectionState> =
        repository.connectionState.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConnectionState.Disconnected
        )

    init {
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
     * Check if headlights should be on but aren't (nighttime + driving + lights off).
     * Mirrors ESP32 light_reminder.cpp conditions, but the phone voice plays at most
     * once per Android Auto session (see [LightReminderSession]).
     */
    private fun checkLightReminder(status: CarStatus) {
        // Guard: reminder must be enabled
        if (!status.lightReminderEnabled) return
        // Guard: time must be synced + nighttime
        if (status.time?.synced != true || !status.time.isNight) return
        // Guard: must be in Drive
        if (status.sensors.gearDrive != 1) return
        // Guard: normal light must be off
        if (status.lights.normalLight != 0) return

        // All conditions met — play once per session.
        if (!LightReminderSession.claim()) return

        Log.d(TAG, "Light reminder: headlights off while driving at night")
        voiceWarningManager.warnLightsOff()
    }

    // Note: do NOT shut down VoiceWarningManager here — it is an app-scoped @Singleton
    // shared with DebugViewModel and AutoLinkService. Tearing it down when this
    // screen-scoped ViewModel is cleared would kill TTS for the whole app.
}
