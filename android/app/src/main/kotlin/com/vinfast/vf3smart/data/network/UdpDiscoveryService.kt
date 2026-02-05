package com.vinfast.vf3smart.data.network

import android.util.Log
import com.google.gson.Gson
import com.vinfast.vf3smart.data.model.DeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UDP device discovery service for finding VF3-Smart devices on local network
 *
 * Protocol:
 * - Port: 8888 (UDP broadcast)
 * - Device broadcasts every 10 seconds
 * - JSON format: {"device":"VF3-Smart","type":"car-control","ip":"...","mac":"...","hostname":"..."}
 * - Send confirmation to stop broadcasting: {"command":"confirm"}
 */
@Singleton
class UdpDiscoveryService @Inject constructor(
    private val gson: Gson
) {
    companion object {
        private const val TAG = "UdpDiscoveryService"
        private const val UDP_PORT = 8888
        private const val DISCOVERY_TIMEOUT_MS = 30000L
        private const val BUFFER_SIZE = 1024
    }

    /**
     * Discover VF3-Smart device on local network
     * Listens for UDP broadcasts on port 8888
     *
     * @param timeoutMs Timeout in milliseconds (default: 30 seconds)
     * @return Result with DeviceInfo if found, or error if timeout/failure
     */
    suspend fun discoverDevice(timeoutMs: Long = DISCOVERY_TIMEOUT_MS): Result<DeviceInfo> =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Starting device discovery (timeout: ${timeoutMs}ms)...")

            val result: Result<DeviceInfo>? = withTimeoutOrNull(timeoutMs) {
                try {
                    val socket = DatagramSocket(UDP_PORT).apply {
                        broadcast = true
                        soTimeout = 5000 // 5-second socket timeout for receive()
                    }

                    val buffer = ByteArray(BUFFER_SIZE)
                    val packet = DatagramPacket(buffer, buffer.size)

                    while (true) {
                        try {
                            socket.receive(packet)
                            val message = String(packet.data, 0, packet.length)
                            Log.d(TAG, "Received UDP message: $message")

                            val deviceInfo = try {
                                gson.fromJson(message, DeviceInfo::class.java)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse device info: $message", e)
                                continue
                            }

                            if (deviceInfo.isVF3Smart()) {
                                Log.d(TAG, "Found VF3-Smart device: ${deviceInfo.ip} (${deviceInfo.mac})")

                                // Send confirmation to stop broadcasting
                                sendConfirmation(socket, deviceInfo.ip)

                                socket.close()
                                return@withTimeoutOrNull Result.success(deviceInfo)
                            } else {
                                Log.d(TAG, "Ignoring non-VF3Smart device: ${deviceInfo.device}")
                            }
                        } catch (e: java.net.SocketTimeoutException) {
                            // Socket timeout is expected, continue listening
                            continue
                        }
                    }
                    @Suppress("UNREACHABLE_CODE")
                    Result.failure<DeviceInfo>(Exception("Loop ended unexpectedly"))
                } catch (e: Exception) {
                    Log.e(TAG, "Discovery error", e)
                    return@withTimeoutOrNull Result.failure<DeviceInfo>(e)
                }
            }

            result ?: Result.failure(Exception("Discovery timeout after ${timeoutMs}ms"))
        }

    /**
     * Send confirmation message to device to stop broadcasting
     * Format: {"command":"confirm"}
     */
    private fun sendConfirmation(socket: DatagramSocket, deviceIp: String) {
        try {
            val confirmJson = """{"command":"confirm"}"""
            val confirmData = confirmJson.toByteArray()
            val confirmPacket = DatagramPacket(
                confirmData,
                confirmData.size,
                InetAddress.getByName(deviceIp),
                UDP_PORT
            )
            socket.send(confirmPacket)
            Log.d(TAG, "Sent confirmation to $deviceIp to stop broadcasting")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send confirmation", e)
        }
    }
}
