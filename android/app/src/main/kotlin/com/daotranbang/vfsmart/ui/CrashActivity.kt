package com.daotranbang.vfsmart.ui

import android.app.AlertDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import kotlin.system.exitProcess

class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val error = intent.getStringExtra("error") ?: "Unknown error"

        AlertDialog.Builder(this)
            .setTitle("App Crashed")
            .setMessage(error)
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                finish()
                exitProcess(1)
            }
            .show()
    }
}