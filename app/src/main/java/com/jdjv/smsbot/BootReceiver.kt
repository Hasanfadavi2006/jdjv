package com.jdjv.smsbot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("smsbot", Context.MODE_PRIVATE)
            if (prefs.getBoolean("enabled", true)) {
                val svc = Intent(context, SmsBotService::class.java)
                context.startForegroundService(svc)
            }
        }
    }
}
