package com.daotranbang.vfsmart.data.network

import android.util.Log
import com.google.gson.Gson
import com.daotranbang.vfsmart.data.model.CarStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebSocket manager for real-time car status updates
 *
 * Endpoint: ws://<device-ip>/ws
 * Protocol: WebSocket (RFC 6455)
 * Update frequency: Every 1 second (from ESP32)
 * No authentication required (read-only)
 */
@Singleton
class WebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _statusFlow = MutableStateFlow<CarStatus?>(null)
    val statusFlow: StateFlow<CarStatus?> = _statusFlow.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var currentDeviceIp: String? = null
    private var autoReconnectEnabled = true
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 10

    // Track previous status for change detection
    private var previousStatus: CarStatus? = null

    companion object {
        private const val TAG = "WebSocketManager"
        private const val RECONNECT_DELAY_MS = 5000L
        private const val MAX_RECONNECT_DELAY_MS = 60000L
    }

    /**
     * Connect to WebSocket server
     * @param deviceIp IP address of ESP32 device
     * @param autoReconnect Enable automatic reconnection on failure
     */
    fun connect(deviceIp: String, autoReconnect: Boolean = true) {
        currentDeviceIp = deviceIp
        autoReconnectEnabled = autoReconnect
        reconnectAttempts = 0

        val request = Request.Builder()
            .url("ws://$deviceIp/ws")
            .build()

        Log.d(TAG, "Connecting to WebSocket: ws://$deviceIp/ws")

        // Create WebSocket client with ping/pong enabled
        val wsClient = okHttpClient.newBuilder()
            .pingInterval(2, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                _connectionState.value = ConnectionState.Connected
                reconnectAttempts = 0
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val status = gson.fromJson(text, CarStatus::class.java)
                    _statusFlow.value = status

                    // Debug: Log what actually changed
                    logStatusChanges(previousStatus, status)
                    previousStatus = status
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse status JSON", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                _connectionState.value = ConnectionState.Error(t.message ?: "Unknown error")

                if (autoReconnectEnabled && currentDeviceIp != null && reconnectAttempts < maxReconnectAttempts) {
                    scheduleReconnect()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: code=$code, reason=$reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: code=$code, reason=$reason")
                _connectionState.value = ConnectionState.Disconnected

                if (autoReconnectEnabled && currentDeviceIp != null && reconnectAttempts < maxReconnectAttempts) {
                    scheduleReconnect()
                }
            }
        })
    }

    /**
     * Disconnect WebSocket and stop auto-reconnection
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting WebSocket")
        autoReconnectEnabled = false
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Schedule reconnection with exponential backoff
     */
    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectAttempts++

        // Exponential backoff: 5s, 10s, 20s, 40s, up to 60s
        val delay = minOf(
            RECONNECT_DELAY_MS * (1 shl (reconnectAttempts - 1)),
            MAX_RECONNECT_DELAY_MS
        )

        Log.d(TAG, "Scheduling reconnection in ${delay}ms (attempt $reconnectAttempts/$maxReconnectAttempts)")

        reconnectJob = coroutineScope.launch {
            delay(delay)
            currentDeviceIp?.let { ip ->
                Log.d(TAG, "Attempting reconnection...")
                connect(ip, autoReconnectEnabled)
            }
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        disconnect()
        coroutineScope.cancel()
    }

    /**
     * Log status changes for debugging
     */
    private fun logStatusChanges(prev: CarStatus?, current: CarStatus) {
        if (prev == null) {
            Log.d(TAG, "Initial status received: locked=${current.carLockState}")
            return
        }

        val changes = mutableListOf<String>()

        // Check sensors
        if (prev.sensors.brake != current.sensors.brake) {
            changes.add("brake: ${prev.sensors.brake} -> ${current.sensors.brake}")
        }
        if (prev.sensors.steeringAngle != current.sensors.steeringAngle) {
            changes.add("steering: ${prev.sensors.steeringAngle} -> ${current.sensors.steeringAngle}")
        }
        if (prev.sensors.gearDrive != current.sensors.gearDrive) {
            changes.add("gear: ${prev.sensors.gearDrive} -> ${current.sensors.gearDrive}")
        }

        // Check doors
        if (prev.doors.frontLeft != current.doors.frontLeft) {
            changes.add("door_FL: ${prev.doors.frontLeft} -> ${current.doors.frontLeft}")
        }
        if (prev.doors.frontRight != current.doors.frontRight) {
            changes.add("door_FR: ${prev.doors.frontRight} -> ${current.doors.frontRight}")
        }
        if (prev.doors.trunk != current.doors.trunk) {
            changes.add("trunk: ${prev.doors.trunk} -> ${current.doors.trunk}")
        }
        if (prev.doors.locked != current.doors.locked) {
            changes.add("doors_locked: ${prev.doors.locked} -> ${current.doors.locked}")
        }

        // Check windows
        if (prev.windows.leftState != current.windows.leftState) {
            changes.add("window_left: ${prev.windows.leftState} -> ${current.windows.leftState}")
        }
        if (prev.windows.rightState != current.windows.rightState) {
            changes.add("window_right: ${prev.windows.rightState} -> ${current.windows.rightState}")
        }

        // Check seats
        if (prev.seats.frontLeftOccupied != current.seats.frontLeftOccupied) {
            changes.add("seat_FL: ${prev.seats.frontLeftOccupied} -> ${current.seats.frontLeftOccupied}")
        }
        if (prev.seats.frontRightOccupied != current.seats.frontRightOccupied) {
            changes.add("seat_FR: ${prev.seats.frontRightOccupied} -> ${current.seats.frontRightOccupied}")
        }
        if (prev.seats.frontLeftSeatbelt != current.seats.frontLeftSeatbelt) {
            changes.add("seatbelt_FL: ${prev.seats.frontLeftSeatbelt} -> ${current.seats.frontLeftSeatbelt}")
        }
        if (prev.seats.frontRightSeatbelt != current.seats.frontRightSeatbelt) {
            changes.add("seatbelt_FR: ${prev.seats.frontRightSeatbelt} -> ${current.seats.frontRightSeatbelt}")
        }

        // Check lights
        if (prev.lights.demiLight != current.lights.demiLight) {
            changes.add("demi_light: ${prev.lights.demiLight} -> ${current.lights.demiLight}")
        }
        if (prev.lights.normalLight != current.lights.normalLight) {
            changes.add("normal_light: ${prev.lights.normalLight} -> ${current.lights.normalLight}")
        }

        // Check proximity
        if (prev.proximity.rearLeft != current.proximity.rearLeft) {
            changes.add("proximity_rear_L: ${prev.proximity.rearLeft} -> ${current.proximity.rearLeft}")
        }
        if (prev.proximity.rearRight != current.proximity.rearRight) {
            changes.add("proximity_rear_R: ${prev.proximity.rearRight} -> ${current.proximity.rearRight}")
        }

        // Check controls
        if (prev.controls.brakePressed != current.controls.brakePressed) {
            changes.add("brake_pressed: ${prev.controls.brakePressed} -> ${current.controls.brakePressed}")
        }
        if (prev.controls.accessoryPower != current.controls.accessoryPower) {
            changes.add("accessory_power: ${prev.controls.accessoryPower} -> ${current.controls.accessoryPower}")
        }
        if (prev.controls.insideCameras != current.controls.insideCameras) {
            changes.add("inside_cameras: ${prev.controls.insideCameras} -> ${current.controls.insideCameras}")
        }
        if (prev.controls.carLock != current.controls.carLock) {
            changes.add("car_lock: ${prev.controls.carLock} -> ${current.controls.carLock}")
        }
        if (prev.controls.carUnlock != current.controls.carUnlock) {
            changes.add("car_unlock: ${prev.controls.carUnlock} -> ${current.controls.carUnlock}")
        }
        if (prev.controls.dashcam != current.controls.dashcam) {
            changes.add("dashcam: ${prev.controls.dashcam} -> ${current.controls.dashcam}")
        }
        if (prev.controls.odoScreen != current.controls.odoScreen) {
            changes.add("odo_screen: ${prev.controls.odoScreen} -> ${current.controls.odoScreen}")
        }
        if (prev.controls.armrest != current.controls.armrest) {
            changes.add("armrest: ${prev.controls.armrest} -> ${current.controls.armrest}")
        }

        // Check top-level fields
        if (prev.chargingStatus != current.chargingStatus) {
            changes.add("charging: ${prev.chargingStatus} -> ${current.chargingStatus}")
        }
        if (prev.carLockState != current.carLockState) {
            changes.add("lock_state: ${prev.carLockState} -> ${current.carLockState}")
        }
        if (prev.windowCloseActive != current.windowCloseActive) {
            changes.add("window_close_active: ${prev.windowCloseActive} -> ${current.windowCloseActive}")
        }
        if (prev.lightReminderEnabled != current.lightReminderEnabled) {
            changes.add("light_reminder: ${prev.lightReminderEnabled} -> ${current.lightReminderEnabled}")
        }

        // Log only if there are changes
        if (changes.isNotEmpty()) {
            Log.d(TAG, "STATUS CHANGES: ${changes.joinToString(", ")}")
        }
    }

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
}
