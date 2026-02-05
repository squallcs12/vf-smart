package com.vinfast.vf3smart.data.network

import android.util.Log
import com.google.gson.Gson
import com.vinfast.vf3smart.data.model.CarStatus
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

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                _connectionState.value = ConnectionState.Connected
                reconnectAttempts = 0
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val status = gson.fromJson(text, CarStatus::class.java)
                    _statusFlow.value = status
                    Log.v(TAG, "Received status update: locked=${status.carLockState}")
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

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
}
