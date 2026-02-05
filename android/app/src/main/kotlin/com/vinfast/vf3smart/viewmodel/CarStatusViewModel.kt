package com.vinfast.vf3smart.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vinfast.vf3smart.data.model.CarStatus
import com.vinfast.vf3smart.data.network.WebSocketManager
import com.vinfast.vf3smart.data.repository.VF3Repository
import com.vinfast.vf3smart.util.VoiceWarningManager
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
