package com.daotranbang.vfsmart

import android.app.Application
import android.content.Intent
import com.daotranbang.vfsmart.ui.CrashActivity
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VF3Application : Application() {
    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val message = buildString {
                    append(throwable.javaClass.simpleName)
                    append(": ")
                    append(throwable.message ?: "No message")
                    // Skip stack trace on OOM — stackTraceToString() itself can throw OOM
                    if (throwable !is OutOfMemoryError) {
                        append("\n\n")
                        val stack = try { throwable.stackTraceToString().take(500) }
                                    catch (_: Throwable) { "Stack trace unavailable" }
                        append(stack)
                    }
                }
                val intent = Intent(this, CrashActivity::class.java).apply {
                    putExtra("error", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(intent)
            } catch (_: Exception) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
