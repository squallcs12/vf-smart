package com.daotranbang.vfsmart.data.network

import android.util.Log
import com.daotranbang.vfsmart.data.local.SecurePreferences
import com.daotranbang.vfsmart.data.model.CarStatus
import com.daotranbang.vfsmart.data.model.Controls
import com.daotranbang.vfsmart.data.model.Doors
import com.daotranbang.vfsmart.data.model.Lights
import com.daotranbang.vfsmart.data.model.Proximity
import com.daotranbang.vfsmart.data.model.Seats
import com.daotranbang.vfsmart.data.model.Sensors
import com.daotranbang.vfsmart.data.model.TimeInfo
import com.daotranbang.vfsmart.data.model.Windows
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time car status over WebSocket.
 *
 * Endpoint: ws://<device-ip>/ws (served by the ESP32 webserver).
 *
 * Wire format — the same delta protocol the car uses:
 *   Full  (on connect + every 60 s heartbeat):
 *     "F|S:<s>|D:<d>|W:<w>|E:<e>|L:<l>|P:<p>|C:<c>|X:<x>"
 *   Delta (only changed groups):
 *     "U|S:<s>|L:<l>|..."
 *
 *   Group formats:
 *     S  brake,steering,voltage,gear
 *     D  fl,fr,trunk,locked
 *     W  left_state,right_state
 *     E  seat_flo,seat_fro,seatbelt_flo,seatbelt_fro
 *     L  demi,normal
 *     P  rear_l,rear_r
 *     C  brake_pressed,acc_power,cameras,car_lock,car_unlock
 *     X  charging,lock_state(0/1),lr,is_night
 *
 * Authentication: the socket streams nothing until the client proves it knows the
 * configured API key. Right after [onOpen] we send an auth frame as the first
 * message:
 *     {"auth":"<api_key>"}
 * The server replies {"auth":"ok"} (then begins streaming) or {"auth":"failed"}
 * and disconnects. We only surface [ConnectionState.Connected] once we've received
 * {"auth":"ok"} — a TCP-level open alone is not "connected" for our purposes.
 */
@Singleton
class WebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val securePreferences: SecurePreferences
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

    /** True once the current socket has received {"auth":"ok"} from the server. */
    private var authenticated = false

    companion object {
        private const val TAG = "WebSocketManager"
        private const val RECONNECT_DELAY_MS = 3000L
        private const val MAX_RECONNECT_DELAY_MS = 30000L
    }

    /**
     * Connect to the car's status WebSocket. Reconnects automatically on drop.
     * Calling again with a new IP re-points the connection.
     */
    fun connect(deviceIp: String, autoReconnect: Boolean = true) {
        if (deviceIp.isBlank()) {
            Log.w(TAG, "connect() called with blank IP — ignoring")
            return
        }
        // Tear down any existing socket before re-pointing.
        reconnectJob?.cancel()
        webSocket?.cancel()

        currentDeviceIp = deviceIp
        autoReconnectEnabled = autoReconnect
        reconnectAttempts = 0
        openSocket(deviceIp)
    }

    private fun openSocket(deviceIp: String) {
        val request = Request.Builder()
            .url("ws://$deviceIp/ws")
            .build()

        Log.d(TAG, "Connecting to ws://$deviceIp/ws")

        authenticated = false

        val wsClient = okHttpClient.newBuilder()
            .pingInterval(5, TimeUnit.SECONDS)
            .build()

        webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // TCP-level open only. The server streams nothing until we send the
                // auth frame and it replies {"auth":"ok"} — so we don't surface
                // Connected yet (see handleAuthResponse).
                Log.d(TAG, "WebSocket open — sending auth frame")
                val apiKey = securePreferences.getApiKey() ?: ""
                webSocket.send("{\"auth\":\"$apiKey\"}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = text.trim()
                if (!authenticated) {
                    handleAuthResponse(webSocket, msg)
                } else {
                    applyCarStatusPayload(msg)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                handleDrop()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                handleDrop()
            }
        })
    }

    /**
     * Handle the server's reply to our auth frame (the first message on a fresh
     * socket). `{"auth":"ok"}` promotes us to [ConnectionState.Connected] and the
     * next frames are status; `{"auth":"failed"}` means the saved API key is wrong —
     * the server closes the socket, which triggers the normal reconnect path.
     */
    private fun handleAuthResponse(webSocket: WebSocket, msg: String) {
        when {
            msg.contains("\"ok\"") -> {
                Log.d(TAG, "WebSocket authenticated")
                authenticated = true
                _connectionState.value = ConnectionState.Connected
                reconnectAttempts = 0
            }
            msg.contains("\"failed\"") -> {
                Log.e(TAG, "WebSocket auth failed — check API key")
                // Server will close; let handleDrop()/reconnect run its course.
                webSocket.cancel()
            }
            else -> Log.w(TAG, "Unexpected pre-auth message: $msg")
        }
    }

    private fun handleDrop() {
        authenticated = false
        _connectionState.value = ConnectionState.Disconnected
        _statusFlow.value = null
        if (autoReconnectEnabled && currentDeviceIp != null) {
            scheduleReconnect()
        }
    }

    /** Disconnect and stop auto-reconnecting. */
    fun disconnect() {
        Log.d(TAG, "Disconnecting WebSocket")
        autoReconnectEnabled = false
        authenticated = false
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
        _statusFlow.value = null
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectAttempts++
        val backoff = minOf(
            RECONNECT_DELAY_MS * (1 shl minOf(reconnectAttempts - 1, 5)),
            MAX_RECONNECT_DELAY_MS
        )
        Log.d(TAG, "Reconnecting in ${backoff}ms (attempt $reconnectAttempts)")
        reconnectJob = coroutineScope.launch {
            delay(backoff)
            currentDeviceIp?.let { openSocket(it) }
        }
    }

    // ── Car status delta parser ─────────────────────────────────────────────────

    /**
     * Applies a full ("F|...") or delta ("U|...") car status payload,
     * merging only the changed groups into [_statusFlow].
     */
    private fun applyCarStatusPayload(payload: String) {
        if (payload.isEmpty()) return
        val parts = payload.split('|')
        if (parts.isEmpty()) return

        val cur = _statusFlow.value

        var sensors   = cur?.sensors   ?: Sensors(0, 0, "0.00", 0)
        var doors     = cur?.doors     ?: Doors(0, 0, 0, 0)
        var windows   = cur?.windows   ?: Windows(0, 0)
        var seats     = cur?.seats     ?: Seats(0, 0, 0, 0)
        var lights    = cur?.lights    ?: Lights(0, 0)
        var proximity = cur?.proximity ?: Proximity(0, 0)
        var controls  = cur?.controls  ?: Controls(0, 0, 0, 0, 0, 0, 0, 0)
        var chargingStatus         = cur?.chargingStatus         ?: 0
        var carLockState           = cur?.carLockState           ?: "unlocked"
        var lightReminderEnabled   = cur?.lightReminderEnabled   ?: true
        var isNight                = cur?.time?.isNight          ?: false
        val tpms                   = cur?.tpms

        for (i in 1 until parts.size) {
            val entry = parts[i]
            val colon = entry.indexOf(':')
            if (colon < 0) continue
            val grp = entry.substring(0, colon)
            val v   = entry.substring(colon + 1).split(',')

            when (grp) {
                "S" -> sensors = Sensors(
                    brake          = v.getOrNull(0)?.toIntOrNull()   ?: sensors.brake,
                    steeringAngle  = v.getOrNull(1)?.toIntOrNull()   ?: sensors.steeringAngle,
                    batteryVoltage = v.getOrNull(2)                  ?: sensors.batteryVoltage,
                    gearDrive      = v.getOrNull(3)?.toIntOrNull()   ?: sensors.gearDrive
                )
                "D" -> doors = Doors(
                    frontLeft  = v.getOrNull(0)?.toIntOrNull() ?: doors.frontLeft,
                    frontRight = v.getOrNull(1)?.toIntOrNull() ?: doors.frontRight,
                    trunk      = v.getOrNull(2)?.toIntOrNull() ?: doors.trunk,
                    locked     = v.getOrNull(3)?.toIntOrNull() ?: doors.locked
                )
                "W" -> windows = Windows(
                    leftState  = v.getOrNull(0)?.toIntOrNull() ?: windows.leftState,
                    rightState = v.getOrNull(1)?.toIntOrNull() ?: windows.rightState
                )
                "E" -> seats = Seats(
                    frontLeftOccupied  = v.getOrNull(0)?.toIntOrNull() ?: seats.frontLeftOccupied,
                    frontRightOccupied = v.getOrNull(1)?.toIntOrNull() ?: seats.frontRightOccupied,
                    frontLeftSeatbelt  = v.getOrNull(2)?.toIntOrNull() ?: seats.frontLeftSeatbelt,
                    frontRightSeatbelt = v.getOrNull(3)?.toIntOrNull() ?: seats.frontRightSeatbelt
                )
                "L" -> lights = Lights(
                    demiLight   = v.getOrNull(0)?.toIntOrNull() ?: lights.demiLight,
                    normalLight = v.getOrNull(1)?.toIntOrNull() ?: lights.normalLight
                )
                "P" -> proximity = Proximity(
                    rearLeft  = v.getOrNull(0)?.toIntOrNull() ?: proximity.rearLeft,
                    rearRight = v.getOrNull(1)?.toIntOrNull() ?: proximity.rearRight
                )
                "C" -> controls = Controls(
                    brakePressed   = v.getOrNull(0)?.toIntOrNull() ?: controls.brakePressed,
                    accessoryPower = v.getOrNull(1)?.toIntOrNull() ?: controls.accessoryPower,
                    insideCameras  = v.getOrNull(2)?.toIntOrNull() ?: controls.insideCameras,
                    carLock        = v.getOrNull(3)?.toIntOrNull() ?: controls.carLock,
                    carUnlock      = v.getOrNull(4)?.toIntOrNull() ?: controls.carUnlock,
                    dashcam        = controls.dashcam,
                    odoScreen      = controls.odoScreen,
                    armrest        = controls.armrest
                )
                "X" -> {
                    chargingStatus         = v.getOrNull(0)?.toIntOrNull() ?: chargingStatus
                    carLockState           = if ((v.getOrNull(1)?.toIntOrNull() ?: 0) == 1) "locked" else "unlocked"
                    lightReminderEnabled   = (v.getOrNull(2)?.toIntOrNull() ?: 1) == 1
                    isNight                = (v.getOrNull(3)?.toIntOrNull() ?: 0) == 1
                }
            }
        }

        _statusFlow.value = CarStatus(
            sensors                = sensors,
            doors                  = doors,
            windows                = windows,
            seats                  = seats,
            lights                 = lights,
            proximity              = proximity,
            controls               = controls,
            chargingStatus         = chargingStatus,
            carLockState           = carLockState,
            lightReminderEnabled   = lightReminderEnabled,
            time                   = TimeInfo(
                synced      = true,
                currentTime = "",
                bootTime    = "",
                isNight     = isNight
            ),
            tpms = tpms
        )
    }

    /** Clean up all resources (called when the app process is torn down). */
    fun cleanup() {
        disconnect()
        coroutineScope.cancel()
    }
}
