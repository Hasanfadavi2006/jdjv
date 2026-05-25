package com.jdjv.smsbot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = context.getSharedPreferences("smsbot", Context.MODE_PRIVATE)
        val botEnabled = prefs.getBoolean("enabled", true)
        if (!botEnabled) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].displayOriginatingAddress
        val body = messages.joinToString("") { it.messageBody }

        Log.d("SmsBat", "پیام از $sender: $body")

        // ذخیره در لاگ
        SmsLogger.save(context, sender, body, incoming = true)

        // ساخت جواب
        val reply = BotRules.getReply(body)

        // ارسال جواب
        try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(reply)
            smsManager.sendMultipartTextMessage(sender, null, parts, null, null)
            SmsLogger.save(context, sender, reply, incoming = false)
            Log.d("SmsBat", "جواب ارسال شد به $sender: $reply")
        } catch (e: Exception) {
            Log.e("SmsBat", "خطا در ارسال: ${e.message}")
        }

        // آپدیت UI
        val uiIntent = Intent("com.jdjv.smsbot.NEW_MESSAGE")
        context.sendBroadcast(uiIntent)
    }
}
