package com.daotranbang.vfsmart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.daotranbang.vfsmart.autolink.AutoLinkService
import com.daotranbang.vfsmart.ui.MainActivity

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Start the AutoLink monitor service (always allowed from BroadcastReceiver)
        AutoLinkService.start(context)

        // Launch the main UI via root shell — bypasses Android 10+ background activity restriction
        val component = "${context.packageName}/${MainActivity::class.java.name}"
        Thread {
            try {
                val process = Runtime.getRuntime().exec("su")
                val writer = process.outputStream.bufferedWriter()
                writer.write("am start -n $component\n")
                writer.flush()
                writer.close()
                process.waitFor()
            } catch (_: Exception) {}
        }.start()
    }
}
