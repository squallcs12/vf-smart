package com.daotranbang.vfsmart.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.daotranbang.vfsmart.data.model.DeviceConfig
import javax.inject.Singleton

/**
 * Secure storage for device configuration using EncryptedSharedPreferences
 * Stores: Device IP, API key, Device name, MAC address
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

        @Volatile
        private var INSTANCE: SecurePreferences? = null

        fun getInstance(context: Context): SecurePreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecurePreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Save device configuration
     */
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

    /**
     * Get device configuration
     * @return DeviceConfig if configured, null otherwise
     */
    fun getDeviceConfig(): DeviceConfig? {
        if (!isConfigured()) return null

        val ip = encryptedPrefs.getString(KEY_DEVICE_IP, null) ?: return null
        val apiKey = encryptedPrefs.getString(KEY_API_KEY, null) ?: return null
        val name = encryptedPrefs.getString(KEY_DEVICE_NAME, "VF3-Smart") ?: "VF3-Smart"
        val mac = encryptedPrefs.getString(KEY_MAC_ADDRESS, null)

        return DeviceConfig(
            deviceIp = ip,
            apiKey = apiKey,
            deviceName = name,
            mac = mac
        )
    }

    /**
     * Get device IP address
     */
    fun getDeviceIp(): String? {
        return encryptedPrefs.getString(KEY_DEVICE_IP, null)
    }

    /**
     * Get API key
     */
    fun getApiKey(): String? {
        return encryptedPrefs.getString(KEY_API_KEY, null)
    }

    /**
     * Check if device is configured
     */
    fun isConfigured(): Boolean {
        return encryptedPrefs.getBoolean(KEY_IS_CONFIGURED, false)
    }

    /**
     * Clear all stored data
     */
    fun clear() {
        encryptedPrefs.edit().clear().apply()
    }

    /**
     * Update device IP (e.g., after DHCP change)
     */
    fun updateDeviceIp(newIp: String) {
        encryptedPrefs.edit().putString(KEY_DEVICE_IP, newIp).apply()
    }
}
