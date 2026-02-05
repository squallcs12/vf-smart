package com.vinfast.vf3smart.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vinfast.vf3smart.data.model.CarStatus
import com.vinfast.vf3smart.data.network.WebSocketManager
import com.vinfast.vf3smart.data.repository.VF3Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for real-time car status monitoring
 */
@HiltViewModel
class CarStatusViewModel @Inject constructor(
    private val repository: VF3Repository
) : ViewModel() {

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
    }
}
