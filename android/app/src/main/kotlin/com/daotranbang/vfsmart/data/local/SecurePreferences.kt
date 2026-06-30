package com.daotranbang.vfsmart.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.daotranbang.vfsmart.data.model.DeviceConfig
import javax.inject.Singleton

/**
 * Secure storage using EncryptedSharedPreferences.
 *
 * Stores the ESP32 device IP + API key (for the HTTP command layer and the
 * ws://<ip>/ws car-status stream) and the RTSP camera stream URL.
 */
@Singleton
class SecurePreferences private constructor(context: Context) {
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        private const val PREFS_NAME = "vf3_secure_prefs"
        private const val KEY_DEVICE_IP = "device_ip"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_MAC_ADDRESS = "mac_address"
        private const val KEY_IS_CONFIGURED = "is_configured"
        private const val KEY_RTSP_URL = "rtsp_url"

        @Volatile
        private var INSTANCE: SecurePreferences? = null

        fun getInstance(context: Context): SecurePreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecurePreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /** Save device configuration (IP + API key). */
    fun saveDeviceConfig(config: DeviceConfig) {
        encryptedPrefs.edit().apply {
            putString(KEY_DEVICE_IP, config.deviceIp)
            putString(KEY_API_KEY, config.apiKey)
            putString(KEY_DEVICE_NAME, config.deviceName)
            config.mac?.let { putString(KEY_MAC_ADDRESS, it) }
            putBoolean(KEY_IS_CONFIGURED, true)
            apply()
        }
    }

    /** Get device configuration, or null if not yet configured. */
    fun getDeviceConfig(): DeviceConfig? {
        if (!isConfigured()) return null
        val ip = encryptedPrefs.getString(KEY_DEVICE_IP, null) ?: return null
        val apiKey = encryptedPrefs.getString(KEY_API_KEY, null) ?: return null
        val name = encryptedPrefs.getString(KEY_DEVICE_NAME, "VF3-Smart") ?: "VF3-Smart"
        val mac = encryptedPrefs.getString(KEY_MAC_ADDRESS, null)
        return DeviceConfig(deviceIp = ip, apiKey = apiKey, deviceName = name, mac = mac)
    }

    /** Get the device IP address, or null if not configured. */
    fun getDeviceIp(): String? = encryptedPrefs.getString(KEY_DEVICE_IP, null)

    /** Get the API key, or null if not configured. */
    fun getApiKey(): String? = encryptedPrefs.getString(KEY_API_KEY, null)

    /** True once a device IP + API key have been saved. */
    fun isConfigured(): Boolean = encryptedPrefs.getBoolean(KEY_IS_CONFIGURED, false)

    /** Update the device IP only (e.g. after a DHCP change). */
    fun updateDeviceIp(newIp: String) {
        encryptedPrefs.edit().putString(KEY_DEVICE_IP, newIp).apply()
    }

    fun saveRtspUrl(url: String) {
        encryptedPrefs.edit().putString(KEY_RTSP_URL, url).apply()
    }

    fun getRtspUrl(): String? = encryptedPrefs.getString(KEY_RTSP_URL, null)

    /** Clear all stored data */
    fun clear() {
        encryptedPrefs.edit().clear().apply()
    }
}
