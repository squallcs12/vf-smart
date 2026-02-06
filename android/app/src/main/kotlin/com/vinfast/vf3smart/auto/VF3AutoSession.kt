package com.vinfast.vf3smart.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import com.vinfast.vf3smart.data.repository.VF3Repository

class VF3AutoSession(private val repository: VF3Repository) : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        return StatusScreen(carContext, repository)
    }
}
