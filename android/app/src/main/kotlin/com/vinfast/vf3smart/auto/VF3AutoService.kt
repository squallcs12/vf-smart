package com.vinfast.vf3smart.auto

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.vinfast.vf3smart.BuildConfig

/**
 * Android Auto CarAppService for VF3 Smart
 *
 * Provides real-time status monitoring and car controls in Android Auto projection mode.
 * Includes lock/unlock and window control actions for personal use.
 *
 * Note: Hilt injection not needed here. Dependencies injected in StatusScreen via manual lookup.
 */
class VF3AutoService : CarAppService() {

    /**
     * Create host validator for Android Auto connection
     * In debug mode, allows all hosts for testing
     * In release mode, uses Android Auto allowlist
     */
    override fun createHostValidator(): HostValidator {
        return if (BuildConfig.DEBUG) {
            // Allow all hosts in debug mode for Android Auto Desktop Head Unit testing
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            // Use official Android Auto hosts in production
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    /**
     * Create session when Android Auto connects
     * Each connection gets a new session instance
     */
    override fun onCreateSession(): Session {
        return VF3AutoSession()
    }
}
