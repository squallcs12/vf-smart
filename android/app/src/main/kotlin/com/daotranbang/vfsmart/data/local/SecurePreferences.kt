package com.daotranbang.vfsmart.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.inject.Singleton

/**
 * Secure storage using EncryptedSharedPreferences.
 *
 * Car communication is BLE-only (no IP/API key to store). The only persisted
 * setting is the RTSP camera stream URL.
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
        private const val KEY_RTSP_URL = "rtsp_url"

        @Volatile
        private var INSTANCE: SecurePreferences? = null

        fun getInstance(context: Context): SecurePreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecurePreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
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
