package com.daotranbang.vfsmart.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daotranbang.vfsmart.data.model.TpmsSensorAssignments
import com.daotranbang.vfsmart.data.repository.VF3Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * TPMS calibration.
 *
 * Reset/swap are fire-and-forget BLE commands — the ESP32 has no webserver, so
 * there is no read-back of sensor-ID assignments. Live tire pressures still flow
 * in via the car status (TPMS) characteristic. [assignments] stays null.
 */
@HiltViewModel
class TpmsCalibrationViewModel @Inject constructor(
    private val repository: VF3Repository
) : ViewModel() {

    sealed class State {
        object Idle    : State()
        object Loading : State()
        data class Success(val message: String, val sensors: TpmsSensorAssignments?) : State()
        data class Error(val message: String) : State()
    }

    private val _state       = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _assignments = MutableStateFlow<TpmsSensorAssignments?>(null)
    val assignments: StateFlow<TpmsSensorAssignments?> = _assignments.asStateFlow()

    /** No read-back over BLE — kept so the refresh action has something to call. */
    fun load() {
        _state.value = State.Idle
    }

    fun reset() {
        viewModelScope.launch {
            _state.value = State.Loading
            repository.tpmsReset().fold(
                onSuccess = {
                    _state.value = State.Success(
                        "All assignments cleared. Drive near each tire to re-learn.", null
                    )
                },
                onFailure = {
                    _state.value = State.Error(it.message ?: "Reset failed")
                }
            )
        }
    }

    fun swap(posA: String, posB: String) {
        viewModelScope.launch {
            _state.value = State.Loading
            repository.tpmsSwap(posA, posB).fold(
                onSuccess = {
                    _state.value = State.Success(
                        "${posA.uppercase()} ↔ ${posB.uppercase()} swapped", null
                    )
                },
                onFailure = {
                    _state.value = State.Error(it.message ?: "Swap failed")
                }
            )
        }
    }

    fun clearMessage() {
        if (_state.value !is State.Loading) _state.value = State.Idle
    }
}
