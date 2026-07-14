package com.daotranbang.vfsmart.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daotranbang.vfsmart.data.model.CarStatus
import com.daotranbang.vfsmart.data.network.ConnectionState
import com.daotranbang.vfsmart.data.repository.VF3Repository
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

    // Real-time car status from the ws://<ip>/ws stream
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
}
