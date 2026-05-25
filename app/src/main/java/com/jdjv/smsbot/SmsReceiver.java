package com.jdjv.smsbot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;
import java.util.ArrayList;

public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsBat";
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
    // هر پیام بات با این شروع می‌شه تا حلقه تشخیص داده بشه
    private static final String BOT_STAMP = "​"; // zero-width space نامرئی

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!SMS_RECEIVED.equals(intent.getAction())) return;

        SharedPreferences prefs = context.getSharedPreferences("smsbot", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("enabled", true)) return;

        Object[] pdus = (Object[]) intent.getExtras().get("pdus");
        String format = intent.getStringExtra("format");
        if (pdus == null || pdus.length == 0) return;

        String sender = null;
        StringBuilder body = new StringBuilder();
        for (Object pdu : pdus) {
            SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdu, format);
            if (sender == null) sender = msg.getDisplayOriginatingAddress();
            body.append(msg.getMessageBody());
        }

        String text = body.toString();

        // اگه پیام از خود بات بود (شامل zero-width space) → حلقه رو متوقف کن
        if (text.contains(BOT_STAMP)) {
            Log.d(TAG, "پیام بات تشخیص داده شد، جواب نمی‌ده");
            return;
        }

        // اگه پیام در ۳ ثانیه گذشته ارسال شده بود → جلوگیری از حلقه
        long lastSent = prefs.getLong("last_sent_time", 0);
        String lastReply = prefs.getString("last_sent_text", "");
        if (System.currentTimeMillis() - lastSent < 3000 && text.equals(lastReply)) {
            Log.d(TAG, "پیام تکراری اخیر، جواب نمی‌ده");
            return;
        }

        Log.d(TAG, "پیام از " + sender + ": " + text);
        SmsLogger.save(context, sender, text, true);

        // جواب بات + کاراکتر نامرئی برای شناسایی
        String reply = BOT_STAMP + BotRules.getReply(text);
        try {
            SmsManager sm = SmsManager.getDefault();
            ArrayList<String> parts = sm.divideMessage(reply);
            sm.sendMultipartTextMessage(sender, null, parts, null, null);

            // ذخیره زمان و متن آخرین ارسال
            prefs.edit()
                .putLong("last_sent_time", System.currentTimeMillis())
                .putString("last_sent_text", text)
                .apply();

            SmsLogger.save(context, sender, reply.substring(1), false); // بدون stamp ذخیره کن
            Log.d(TAG, "جواب ارسال شد: " + reply);
        } catch (Exception e) {
            Log.e(TAG, "خطا در ارسال: " + e.getMessage());
        }

        context.sendBroadcast(new Intent("com.jdjv.smsbot.NEW_MESSAGE"));
    }
}
