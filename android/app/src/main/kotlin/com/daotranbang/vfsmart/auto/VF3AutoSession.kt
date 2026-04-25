package com.daotranbang.vfsmart.auto

import android.content.Intent
import android.util.Log
import androidx.car.app.Screen
import androidx.car.app.Session
import com.daotranbang.vfsmart.data.repository.VF3Repository

class VF3AutoSession(private val repository: VF3Repository) : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        Log.d(TAG, "onCreateScreen — Android Auto UI starting, intent=$intent")
        return StatusScreen(carContext, repository)
    }

    companion object {
        private const val TAG = "VF3Auto"
    }
}
