package com.daotranbang.vfsmart.data.repository

import android.util.Log
import com.daotranbang.vfsmart.data.local.SecurePreferences
import com.daotranbang.vfsmart.data.model.*
import com.daotranbang.vfsmart.data.network.ConnectionState
import com.daotranbang.vfsmart.data.network.UdpDiscoveryService
import com.daotranbang.vfsmart.data.network.VF3ApiService
import com.daotranbang.vfsmart.data.network.WebSocketManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository — single source of truth for VF3-Smart data.
 *
 * Transport:
 *  - Real-time status arrives on the ws://<ip>/ws stream (delta protocol) and is
 *    exposed here as [carStatus] / [connectionState] via [WebSocketManager].
 *  - Commands are HTTP POSTs to the ESP32 webserver (API-key auth) via
 *    [VF3ApiService]; the dynamic host comes from [SecurePreferences].
 *  - The device is found on the LAN with [UdpDiscoveryService] and persisted.
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

    // Real-time status from the WebSocket stream
    val carStatus: StateFlow<CarStatus?> = webSocketManager.statusFlow
    val connectionState: StateFlow<ConnectionState> = webSocketManager.connectionState

    // ── Connection lifecycle ─────────────────────────────────────────────────────

    /** Open the status WebSocket using the saved device IP, if configured. */
    fun connectIfConfigured() {
        val ip = securePreferences.getDeviceIp()
        if (ip.isNullOrBlank()) {
            Log.d(TAG, "connectIfConfigured() — no device configured yet")
            return
        }
        webSocketManager.connect(ip)
    }

    /** (Re)open the status WebSocket to an explicit IP (e.g. right after setup). */
    fun connect(deviceIp: String) = webSocketManager.connect(deviceIp)

    fun disconnect() = webSocketManager.disconnect()

    /** Discover the VF3-Smart device on the local network via UDP. */
    suspend fun discoverDevice(timeoutMs: Long = 30000L): Result<DeviceInfo> =
        udpDiscoveryService.discoverDevice(timeoutMs)

    // ── Status (HTTP fallback) ───────────────────────────────────────────────────

    /** One-shot status fetch (HTTP fallback when the WebSocket isn't streaming). */
    suspend fun getCarStatus(): Result<CarStatus> = safeApiCall { apiService.getCarStatus() }

    // ── Commands ───────────────────────────────────────────────────────────────

    suspend fun lockCar(): Result<LockResponse> = safeApiCall { apiService.lockCar() }

    suspend fun unlockCar(): Result<LockResponse> = safeApiCall { apiService.unlockCar() }

    suspend fun toggleAccessoryPower(): Result<ControlResponse> = safeApiCall {
        apiService.controlAccessoryPower("toggle")
    }

    suspend fun setAccessoryPower(on: Boolean): Result<ControlResponse> = safeApiCall {
        apiService.controlAccessoryPower(if (on) "on" else "off")
    }

    suspend fun toggleInsideCameras(): Result<ControlResponse> = safeApiCall {
        apiService.controlInsideCameras("toggle")
    }

    suspend fun setInsideCameras(on: Boolean): Result<ControlResponse> = safeApiCall {
        apiService.controlInsideCameras(if (on) "on" else "off")
    }

    suspend fun closeWindows(): Result<WindowResponse> = safeApiCall { apiService.closeWindows() }

    suspend fun stopWindows(): Result<WindowResponse> = safeApiCall { apiService.stopWindows() }

    /** @param side "left", "right", or "both" */
    suspend fun controlWindowDown(side: String, on: Boolean): Result<WindowResponse> = safeApiCall {
        apiService.controlWindowsDown(side, if (on) "on" else "off")
    }

    /** @param side "left", "right", or "both" */
    suspend fun controlWindowUp(side: String, on: Boolean): Result<WindowResponse> = safeApiCall {
        apiService.controlWindowsUp(side, if (on) "on" else "off")
    }

    suspend fun beepHorn(durationMs: Int = 500): Result<BuzzerResponse> = safeApiCall {
        apiService.controlBuzzer("beep", durationMs)
    }

    suspend fun setBuzzer(on: Boolean): Result<BuzzerResponse> = safeApiCall {
        apiService.controlBuzzer(if (on) "on" else "off")
    }

    suspend fun toggleLightReminder(): Result<ControlResponse> = safeApiCall {
        apiService.controlLightReminder("toggle")
    }

    suspend fun setLightReminder(enabled: Boolean): Result<ControlResponse> = safeApiCall {
        apiService.controlLightReminder(if (enabled) "on" else "off")
    }

    suspend fun unlockCharger(): Result<ChargerResponse> = safeApiCall { apiService.unlockCharger() }

    suspend fun openSideMirrors(): Result<ControlResponse> = safeApiCall {
        apiService.controlSideMirrors("open")
    }

    suspend fun closeSideMirrors(): Result<ControlResponse> = safeApiCall {
        apiService.controlSideMirrors("close")
    }

    /** Get current TPMS sensor-ID assignments. */
    suspend fun getTpmsCalibration(): Result<TpmsCalibrationResponse> = safeApiCall {
        apiService.getTpmsCalibration()
    }

    /** Reset all TPMS sensor assignments (drive near each tire to re-learn). */
    suspend fun tpmsReset(): Result<TpmsCalibrationResponse> = safeApiCall {
        apiService.tpmsCalibrate("reset")
    }

    /** Swap two TPMS tire positions (posA/posB: "fl", "fr", "rl", "rr"). */
    suspend fun tpmsSwap(posA: String, posB: String): Result<TpmsCalibrationResponse> = safeApiCall {
        apiService.tpmsCalibrate("swap", posA, posB)
    }

    // ── Transport ────────────────────────────────────────────────────────────────

    private suspend fun <T> safeApiCall(apiCall: suspend () -> T): Result<T> =
        withContext(ioDispatcher) {
            try {
                Result.success(apiCall())
            } catch (e: IOException) {
                Log.e(TAG, "Network error", e)
                Result.failure(Exception("Network error: ${e.message}"))
            } catch (e: HttpException) {
                Log.e(TAG, "HTTP error: ${e.code()}", e)
                val message = when (e.code()) {
                    401 -> "Unauthorized - Invalid API key"
                    404 -> "Endpoint not found"
                    500 -> "Server error"
                    else -> "HTTP ${e.code()}: ${e.message()}"
                }
                Result.failure(Exception(message))
            } catch (e: Exception) {
                Log.e(TAG, "Unknown error", e)
                Result.failure(Exception("Error: ${e.message}"))
            }
        }
}
