package com.vinfast.vf3smart.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vinfast.vf3smart.data.local.SecurePreferences
import com.vinfast.vf3smart.data.model.DeviceConfig
import com.vinfast.vf3smart.data.model.DeviceInfo
import com.vinfast.vf3smart.data.repository.VF3Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for device setup and configuration
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val repository: VF3Repository,
    private val securePreferences: SecurePreferences
) : ViewModel() {

    private val _discoveryState = MutableStateFlow<DiscoveryState>(DiscoveryState.Idle)
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()

    private val _configurationState = MutableStateFlow<ConfigurationState>(ConfigurationState.Idle)
    val configurationState: StateFlow<ConfigurationState> = _configurationState.asStateFlow()

    /**
     * Discover device via UDP broadcast
     */
    fun discoverDevice(timeoutMs: Long = 30000L) {
        viewModelScope.launch {
            _discoveryState.value = DiscoveryState.Discovering
            repository.discoverDevice(timeoutMs).fold(
                onSuccess = { deviceInfo ->
                    _discoveryState.value = DiscoveryState.Success(deviceInfo)
                },
                onFailure = { error ->
                    _discoveryState.value = DiscoveryState.Error(error.message ?: "Discovery failed")
                }
            )
        }
    }

    /**
     * Save device configuration and test connection
     */
    fun saveConfiguration(deviceIp: String, apiKey: String, deviceName: String = "VF3-Smart") {
        viewModelScope.launch {
            _configurationState.value = ConfigurationState.Saving

            // Validate inputs
            if (deviceIp.isBlank()) {
                _configurationState.value = ConfigurationState.Error("Device IP is required")
                return@launch
            }

            if (apiKey.length < 8) {
                _configurationState.value = ConfigurationState.Error("API key must be at least 8 characters")
                return@launch
            }

            // Save configuration
            val config = DeviceConfig(
                deviceIp = deviceIp.trim(),
                apiKey = apiKey.trim(),
                deviceName = deviceName.trim()
            )
            securePreferences.saveDeviceConfig(config)

            // Jump to success immediately as per request, skipping connection test
            _configurationState.value = ConfigurationState.Success
        }
    }

    /**
     * Check if device is already configured
     */
    fun isConfigured(): Boolean {
        return securePreferences.isConfigured()
    }

    /**
     * Reset discovery state
     */
    fun resetDiscovery() {
        _discoveryState.value = DiscoveryState.Idle
    }

    /**
     * Reset configuration state
     */
    fun resetConfiguration() {
        _configurationState.value = ConfigurationState.Idle
    }

    sealed class DiscoveryState {
        object Idle : DiscoveryState()
        object Discovering : DiscoveryState()
        data class Success(val deviceInfo: DeviceInfo) : DiscoveryState()
        data class Error(val message: String) : DiscoveryState()
    }

    sealed class ConfigurationState {
        object Idle : ConfigurationState()
        object Saving : ConfigurationState()
        object Testing : ConfigurationState()
        object Success : ConfigurationState()
        data class Error(val message: String) : ConfigurationState()
    }
}
