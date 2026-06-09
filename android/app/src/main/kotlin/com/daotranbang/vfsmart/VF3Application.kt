package com.daotranbang.vfsmart

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.daotranbang.vfsmart.autolink.AutoLinkService
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VF3Application : Application() {

    // AutoLink monitoring should only run while the head unit has power (car on).
    // This receiver lives for the whole process, so it can start the service again
    // after it has been stopped on power loss.
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    Log.i(TAG, "Power connected — starting AutoLinkService")
                    AutoLinkService.start(context)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    Log.i(TAG, "Power disconnected — stopping AutoLinkService")
                    AutoLinkService.stop(context)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Crashlytics auto-initializes via its ContentProvider; here we attach
        // diagnostics so reports tell us which of the two target devices crashed
        // (S20+ vs the armeabi-v7a head unit) and the ABI.
        FirebaseCrashlytics.getInstance().apply {
            setCustomKey("device_model", Build.MODEL)
            setCustomKey("primary_abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
            setCustomKey("android_api", Build.VERSION.SDK_INT)
        }

        registerReceiver(powerReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        })
        // Start immediately if the device is already on power at launch.
        if (isOnPower()) {
            Log.i(TAG, "Already on power at startup — starting AutoLinkService")
            AutoLinkService.start(this)
        }
    }

    private fun isOnPower(): Boolean {
        val plugged = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        return plugged == BatteryManager.BATTERY_PLUGGED_AC ||
               plugged == BatteryManager.BATTERY_PLUGGED_USB ||
               plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS
    }

    companion object { private const val TAG = "VF3App" }
}
