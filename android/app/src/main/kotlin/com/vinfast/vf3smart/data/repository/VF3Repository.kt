package com.vinfast.vf3smart.data.repository

import android.util.Log
import com.vinfast.vf3smart.data.local.SecurePreferences
import com.vinfast.vf3smart.data.model.*
import com.vinfast.vf3smart.data.network.UdpDiscoveryService
import com.vinfast.vf3smart.data.network.VF3ApiService
import com.vinfast.vf3smart.data.network.WebSocketManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository pattern - single source of truth for VF3-Smart data
 * Handles API calls, WebSocket connections, and device discovery
 */
@Singleton
class VF3Repository @Inject constructor(
    private val apiService: VF3ApiService,
    private val webSocketManager: WebSocketManager,
    private val udpDiscoveryService: UdpDiscoveryService,
    private val securePreferences: SecurePreferences,
    private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "VF3Repository"
    }

    // Real-time status from WebSocket
    val carStatus: StateFlow<CarStatus?> = webSocketManager.statusFlow
    val connectionState: StateFlow<WebSocketManager.ConnectionState> =
        webSocketManager.connectionState

    /**
     * Connect to WebSocket for real-time updates
     */
    fun connectWebSocket() {
        val deviceIp = securePreferences.getDeviceIp()
        if (deviceIp != null) {
            webSocketManager.connect(deviceIp, autoReconnect = true)
        } else {
            Log.w(TAG, "Cannot connect WebSocket: device IP not configured")
        }
    }

    /**
     * Disconnect WebSocket
     */
    fun disconnectWebSocket() {
        webSocketManager.disconnect()
    }

    /**
     * Discover VF3-Smart device on local network via UDP
     * @param timeoutMs Discovery timeout in milliseconds
     */
    suspend fun discoverDevice(timeoutMs: Long = 30000L): Result<DeviceInfo> {
        return udpDiscoveryService.discoverDevice(timeoutMs)
    }

    /**
     * Get current car status (HTTP fallback if WebSocket is not available)
     */
    suspend fun getCarStatus(): Result<CarStatus> = safeApiCall {
        apiService.getCarStatus()
    }

    /**
     * Lock the car
     */
    suspend fun lockCar(): Result<LockResponse> = safeApiCall {
        apiService.lockCar()
    }

    /**
     * Unlock the car
     */
    suspend fun unlockCar(): Result<LockResponse> = safeApiCall {
        apiService.unlockCar()
    }

    /**
     * Toggle accessory power
     */
    suspend fun toggleAccessoryPower(): Result<ControlResponse> = safeApiCall {
        apiService.controlAccessoryPower("toggle")
    }

    /**
     * Set accessory power
     * @param on true to turn on, false to turn off
     */
    suspend fun setAccessoryPower(on: Boolean): Result<ControlResponse> = safeApiCall {
        apiService.controlAccessoryPower(if (on) "on" else "off")
    }

    /**
     * Toggle inside cameras
     */
    suspend fun toggleInsideCameras(): Result<ControlResponse> = safeApiCall {
        apiService.controlInsideCameras("toggle")
    }

    /**
     * Set inside cameras
     * @param on true to turn on, false to turn off
     */
    suspend fun setInsideCameras(on: Boolean): Result<ControlResponse> = safeApiCall {
        apiService.controlInsideCameras(if (on) "on" else "off")
    }

    /**
     * Start closing windows (30-second timer)
     */
    suspend fun closeWindows(): Result<WindowResponse> = safeApiCall {
        apiService.closeWindows()
    }

    /**
     * Stop window operation immediately
     */
    suspend fun stopWindows(): Result<WindowResponse> = safeApiCall {
        apiService.stopWindows()
    }

    /**
     * Control window down operation
     * @param side "left", "right", or "both"
     * @param on true to roll down, false to stop
     */
    suspend fun controlWindowDown(side: String, on: Boolean): Result<WindowResponse> = safeApiCall {
        apiService.controlWindowsDown(side, if (on) "on" else "off")
    }

    /**
     * Beep horn/buzzer
     * @param durationMs Duration in milliseconds (default: 500ms)
     */
    suspend fun beepHorn(durationMs: Int = 500): Result<BuzzerResponse> = safeApiCall {
        apiService.controlBuzzer("beep", durationMs)
    }

    /**
     * Turn buzzer on/off
     * @param on true to turn on, false to turn off
     */
    suspend fun setBuzzer(on: Boolean): Result<BuzzerResponse> = safeApiCall {
        apiService.controlBuzzer(if (on) "on" else "off")
    }

    /**
     * Toggle light reminder
     */
    suspend fun toggleLightReminder(): Result<ControlResponse> = safeApiCall {
        apiService.controlLightReminder("toggle")
    }

    /**
     * Set light reminder
     * @param enabled true to enable, false to disable
     */
    suspend fun setLightReminder(enabled: Boolean): Result<ControlResponse> = safeApiCall {
        apiService.controlLightReminder(if (enabled) "on" else "off")
    }

    /**
     * Unlock charger port
     */
    suspend fun unlockCharger(): Result<ChargerResponse> = safeApiCall {
        apiService.unlockCharger()
    }

    /**
     * Open side mirrors
     */
    suspend fun openSideMirrors(): Result<ControlResponse> = safeApiCall {
        apiService.controlSideMirrors("open")
    }

    /**
     * Close side mirrors
     */
    suspend fun closeSideMirrors(): Result<ControlResponse> = safeApiCall {
        apiService.controlSideMirrors("close")
    }

    /**
     * Toggle ODO screen
     */
    suspend fun toggleOdoScreen(): Result<ControlResponse> = safeApiCall {
        apiService.controlOdoScreen("toggle")
    }

    /**
     * Set ODO screen
     * @param on true to turn on, false to turn off
     */
    suspend fun setOdoScreen(on: Boolean): Result<ControlResponse> = safeApiCall {
        apiService.controlOdoScreen(if (on) "on" else "off")
    }

    /**
     * Toggle armrest
     */
    suspend fun toggleArmrest(): Result<ControlResponse> = safeApiCall {
        apiService.controlArmrest("toggle")
    }

    /**
     * Set armrest
     * @param on true to turn on, false to turn off
     */
    suspend fun setArmrest(on: Boolean): Result<ControlResponse> = safeApiCall {
        apiService.controlArmrest(if (on) "on" else "off")
    }

    /**
     * Toggle dashcam
     */
    suspend fun toggleDashcam(): Result<ControlResponse> = safeApiCall {
        apiService.controlDashcam("toggle")
    }

    /**
     * Set dashcam
     * @param on true to turn on, false to turn off
     */
    suspend fun setDashcam(on: Boolean): Result<ControlResponse> = safeApiCall {
        apiService.controlDashcam(if (on) "on" else "off")
    }

    /**
     * Test connection to device
     * @return true if connection successful, false otherwise
     */
    suspend fun testConnection(): Boolean {
        return getCarStatus().isSuccess
    }

    /**
     * Safe API call wrapper with error handling
     */
    private suspend fun <T> safeApiCall(
        apiCall: suspend () -> T
    ): Result<T> = withContext(ioDispatcher) {
        try {
            Result.success(apiCall())
        } catch (e: IOException) {
            Log.e(TAG, "Network error", e)
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: HttpException) {
            Log.e(TAG, "HTTP error: ${e.code()}", e)
            val errorMessage = when (e.code()) {
                401 -> "Unauthorized - Invalid API key"
                404 -> "Endpoint not found"
                500 -> "Server error"
                else -> "HTTP ${e.code()}: ${e.message()}"
            }
            Result.failure(Exception(errorMessage))
        } catch (e: Exception) {
            Log.e(TAG, "Unknown error", e)
            Result.failure(Exception("Error: ${e.message}"))
        }
    }
}
