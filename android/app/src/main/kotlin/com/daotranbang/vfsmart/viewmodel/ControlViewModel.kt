package com.daotranbang.vfsmart.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daotranbang.vfsmart.data.repository.VF3Repository
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

    /**
     * Hold-to-open a single window, like a physical window switch: roll the
     * window down while the button is held, stop the moment it's released.
     * One window at a time — never both at once (heavy motor load on the car).
     * Bypasses [executeOperation] so rapid press/release doesn't spam the
     * loading/success operation state.
     * @param side "left" or "right"
     * @param pressed true on press (start rolling down), false on release (stop)
     */
    fun holdOpenWindow(side: String, pressed: Boolean) {
        viewModelScope.launch {
            repository.controlWindowDown(side, pressed)
        }
    }

    /**
     * Hold-to-close a single window: roll the window up while the button is
     * held, stop the moment it's released. One window at a time.
     * @param side "left" or "right"
     * @param pressed true on press (start rolling up), false on release (stop)
     */
    fun holdCloseWindow(side: String, pressed: Boolean) {
        viewModelScope.launch {
            repository.controlWindowUp(side, pressed)
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
