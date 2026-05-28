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
    private static final String BOT_STAMP = "​"; // zero-width space — جلوگیری از حلقه

    @Override
    public void onReceive(final Context context, Intent intent) {
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

        final String text = body.toString();
        final String from = sender;

        // پیام خود بات (zero-width space) → نادیده بگیر
        if (text.contains(BOT_STAMP)) return;

        // پیام تکراری در ۳ ثانیه → نادیده بگیر
        long lastSent = prefs.getLong("last_sent_time", 0);
        String lastReply = prefs.getString("last_sent_text", "");
        if (System.currentTimeMillis() - lastSent < 3000 && text.equals(lastReply)) return;

        Log.d(TAG, "پیام از " + from + ": " + text);
        ApiLogger.log(context, "SMS", "دریافت از " + from + ": " + text);
        SmsLogger.save(context, from, text, true);
        context.sendBroadcast(new Intent("com.jdjv.smsbot.NEW_MESSAGE"));

        // ─ Claude API رو async صدا بزن ─
        final PendingResult pending = goAsync();
        ClaudeApiClient.getReply(context, from, text, new ClaudeApiClient.Callback() {
            @Override
            public void onReply(String reply, double costUsd) {
                ApiLogger.log(context, "OK", "جواب ارسال به " + from + ": " + reply);
                sendReply(context, from, reply, text, costUsd);
                pending.finish();
            }
            @Override
            public void onError(String error) {
                Log.e(TAG, "Claude خطا: " + error);
                ApiLogger.log(context, "FALLBACK", "fallback به BotRules — خطا: " + error);
                String reply = BotRules.getReply(text);
                sendReplyNoHistory(context, from, reply, text);
                pending.finish();
            }
        });
    }

    private void sendReply(Context context, String to, String reply, String originalText, double costUsd) {
        doSend(context, to, reply, originalText, true, costUsd);
    }

    private void sendReplyNoHistory(Context context, String to, String reply, String originalText) {
        doSend(context, to, reply, originalText, false, 0.0);
    }

    private void doSend(Context context, String to, String reply, String originalText, boolean saveHistory, double costUsd) {
        String stamped = BOT_STAMP + reply;
        try {
            SmsManager sm = SmsManager.getDefault();
            ArrayList<String> parts = sm.divideMessage(stamped);
            sm.sendMultipartTextMessage(to, null, parts, null, null);

            SharedPreferences prefs = context.getSharedPreferences("smsbot", Context.MODE_PRIVATE);
            prefs.edit()
                .putLong("last_sent_time", System.currentTimeMillis())
                .putString("last_sent_text", originalText)
                .apply();

            if (saveHistory) {
                SmsLogger.save(context, to, reply, false, costUsd);
            }
            context.sendBroadcast(new Intent("com.jdjv.smsbot.NEW_MESSAGE"));
        } catch (Exception e) {
            Log.e(TAG, "خطا در ارسال: " + e.getMessage());
        }
    }
}
