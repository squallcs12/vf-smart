package com.vinfast.vf3smart.data.model

import com.google.gson.annotations.SerializedName

/**
 * Device information received from UDP discovery broadcast
 * Port: 8888, Format: JSON
 */
data class DeviceInfo(
    @SerializedName("device")
    val device: String,

    @SerializedName("type")
    val type: String,

    @SerializedName("ip")
    val ip: String,

    @SerializedName("mac")
    val mac: String,

    @SerializedName("hostname")
    val hostname: String
) {
    fun isVF3Smart(): Boolean {
        return device == "VF3-Smart" && type == "car-control"
    }
}

/**
 * Device configuration stored locally in EncryptedSharedPreferences
 */
data class DeviceConfig(
    val deviceIp: String,
    val apiKey: String,
    val deviceName: String = "VF3-Smart",
    val mac: String? = null
)
