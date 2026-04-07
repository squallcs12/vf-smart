package com.vinfast.vf3smart.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vinfast.vf3smart.data.model.TpmsSensorAssignments
import com.vinfast.vf3smart.data.repository.VF3Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = State.Loading
            repository.getTpmsCalibration().fold(
                onSuccess = { resp ->
                    _assignments.value = resp.sensors
                    _state.value = State.Idle
                },
                onFailure = {
                    _state.value = State.Error(it.message ?: "Failed to load")
                }
            )
        }
    }

    fun reset() {
        viewModelScope.launch {
            _state.value = State.Loading
            repository.tpmsReset().fold(
                onSuccess = { resp ->
                    _assignments.value = resp.sensors
                    _state.value = State.Success("All assignments cleared. Drive near each tire to re-learn.", resp.sensors)
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
                onSuccess = { resp ->
                    _assignments.value = resp.sensors
                    _state.value = State.Success(
                        "${posA.uppercase()} ↔ ${posB.uppercase()} swapped",
                        resp.sensors
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
