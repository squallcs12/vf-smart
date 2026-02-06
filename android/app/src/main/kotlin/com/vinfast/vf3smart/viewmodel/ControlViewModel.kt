package com.vinfast.vf3smart.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vinfast.vf3smart.data.repository.VF3Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for car control operations
 */
@HiltViewModel
class ControlViewModel @Inject constructor(
    private val repository: VF3Repository
) : ViewModel() {

    private val _operationState = MutableStateFlow<OperationState>(OperationState.Idle)
    val operationState: StateFlow<OperationState> = _operationState.asStateFlow()

    /**
     * Lock the car
     */
    fun lockCar() {
        executeOperation("Locking car...") {
            repository.lockCar()
        }
    }

    /**
     * Unlock the car
     */
    fun unlockCar() {
        executeOperation("Unlocking car...") {
            repository.unlockCar()
        }
    }

    /**
     * Toggle accessory power
     */
    fun toggleAccessoryPower() {
        executeOperation("Toggling accessory power...") {
            repository.toggleAccessoryPower()
        }
    }

    /**
     * Toggle inside cameras
     */
    fun toggleInsideCameras() {
        executeOperation("Toggling cameras...") {
            repository.toggleInsideCameras()
        }
    }

    /**
     * Close windows (30-second timer)
     */
    fun closeWindows() {
        executeOperation("Closing windows...") {
            repository.closeWindows()
        }
    }

    /**
     * Stop window operation
     */
    fun stopWindows() {
        executeOperation("Stopping windows...") {
            repository.stopWindows()
        }
    }

    /**
     * Control window down
     * @param side "left", "right", or "both"
     * @param on true to roll down, false to stop
     */
    fun controlWindowDown(side: String, on: Boolean) {
        val action = if (on) "Rolling down" else "Stopping"
        executeOperation("$action $side window...") {
            repository.controlWindowDown(side, on)
        }
    }

    /**
     * Beep horn
     * @param durationMs Duration in milliseconds
     */
    fun beepHorn(durationMs: Int = 500) {
        executeOperation("Beeping horn...") {
            repository.beepHorn(durationMs)
        }
    }

    /**
     * Toggle light reminder
     */
    fun toggleLightReminder() {
        executeOperation("Toggling light reminder...") {
            repository.toggleLightReminder()
        }
    }

    /**
     * Unlock charger port
     */
    fun unlockCharger() {
        executeOperation("Unlocking charger...") {
            repository.unlockCharger()
        }
    }

    fun openLeftWindow() {
        executeOperation("Opening left window...") {
            repository.controlWindowDown("left", true)
        }
    }

    fun closeLeftWindow() {
        executeOperation("Closing left window...") {
            repository.controlWindowDown("left", false)
        }
    }

    fun openRightWindow() {
        executeOperation("Opening right window...") {
            repository.controlWindowDown("right", true)
        }
    }

    fun closeRightWindow() {
        executeOperation("Closing right window...") {
            repository.controlWindowDown("right", false)
        }
    }

    /**
     * Reset operation state
     */
    fun resetOperationState() {
        _operationState.value = OperationState.Idle
    }

    /**
     * Execute operation with loading state and error handling
     */
    private fun executeOperation(
        loadingMessage: String,
        operation: suspend () -> Result<Any>
    ) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading(loadingMessage)

            operation().fold(
                onSuccess = { result ->
                    _operationState.value = OperationState.Success(result.toString())
                },
                onFailure = { error ->
                    _operationState.value = OperationState.Error(error.message ?: "Operation failed")
                }
            )
        }
    }

    sealed class OperationState {
        object Idle : OperationState()
        data class Loading(val message: String) : OperationState()
        data class Success(val message: String) : OperationState()
        data class Error(val message: String) : OperationState()
    }
}
