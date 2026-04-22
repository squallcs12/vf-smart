package com.daotranbang.vfsmart

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Process
import com.daotranbang.vfsmart.ui.MainActivity
import dagger.hilt.android.HiltAndroidApp
import kotlin.system.exitProcess

@HiltAndroidApp
class VF3Application : Application() {
    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val restartIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                val pending = PendingIntent.getActivity(
                    this, 0, restartIntent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarm.set(AlarmManager.RTC, System.currentTimeMillis() + 1_000, pending)
            } catch (_: Exception) {
                defaultHandler?.uncaughtException(thread, throwable)
            } finally {
                Process.killProcess(Process.myPid())
                exitProcess(1)
            }
        }
    }
}
