package com.vinfast.vf3smart.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

/**
 * Android Auto session for VF3 Smart app
 *
 * Manages the lifecycle of the Android Auto connection.
 * Each session provides status monitoring and control screen.
 *
 * Note: Cannot use @AndroidEntryPoint here as Session is not a supported Hilt entry point.
 * Dependencies are injected manually in StatusScreen via Application context.
 */
class VF3AutoSession : Session() {

    /**
     * Create the initial screen when session starts
     * Returns the status screen as the main/only screen
     */
    override fun onCreateScreen(intent: Intent): Screen {
        return StatusScreen(carContext)
    }
}
