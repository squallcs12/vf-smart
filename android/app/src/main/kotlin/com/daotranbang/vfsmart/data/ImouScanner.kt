package com.daotranbang.vfsmart.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * Scans the local subnet(s) — including the device's own hotspot AP network —
 * for an Imou (Dahua) camera. An open RTSP port 554 identifies the camera.
 *
 * Shared by the manual "detect" button (`RtspCaptureViewModel`) and the
 * automatic scan that runs when the head unit gains power (`VF3Application`).
 */
object ImouScanner {

    /** Returns the IP of the first host exposing RTSP/554, or null if none found. */
    suspend fun scan(): String? = coroutineScope {
        for (prefix in localSubnets()) {
            val rtspHosts = (1..254).map { host ->
                async {
                    val ip = "$prefix.$host"
                    if (isPortOpen(ip, 554, 300)) ip else null
                }
            }.awaitAll().filterNotNull()

            rtspHosts.firstOrNull()?.let { return@coroutineScope it }
        }
        null
    }

    /** Splices [ip] into the standard Imou path, keeping existing user:pass@ if present. */
    fun buildRtspUrl(ip: String, existing: String): String {
        val cred = Regex("rtsp://([^@/\\s]+)@").find(existing)?.groupValues?.get(1) ?: "admin:"
        return "rtsp://$cred@$ip:554/cam/realmonitor?channel=1&subtype=0"
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean =
        try {
            Socket().use { it.connect(InetSocketAddress(ip, port), timeoutMs) }
            true
        } catch (e: Exception) {
            false
        }

    /** /24 prefixes for every up, non-loopback private IPv4 interface (Wi-Fi + hotspot AP). */
    private fun localSubnets(): List<String> {
        val prefixes = LinkedHashSet<String>()
        try {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr !is Inet4Address || addr.isLoopbackAddress) continue
                    val parts = addr.hostAddress?.split(".") ?: continue
                    if (parts.size != 4) continue
                    val a = parts[0].toIntOrNull() ?: continue
                    val b = parts[1].toIntOrNull() ?: continue
                    val isPrivate = a == 10 ||
                        (a == 172 && b in 16..31) ||
                        (a == 192 && b == 168)
                    if (isPrivate) prefixes.add("${parts[0]}.${parts[1]}.${parts[2]}")
                }
            }
        } catch (e: Exception) {
            // fall through with whatever we collected
        }
        return prefixes.toList()
    }
}
