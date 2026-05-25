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
        Log.d(TAG, "پیام از " + sender + ": " + text);
        SmsLogger.save(context, sender, text, true);

        String reply = BotRules.getReply(text);
        try {
            SmsManager sm = SmsManager.getDefault();
            ArrayList<String> parts = sm.divideMessage(reply);
            sm.sendMultipartTextMessage(sender, null, parts, null, null);
            SmsLogger.save(context, sender, reply, false);
            Log.d(TAG, "جواب ارسال شد: " + reply);
        } catch (Exception e) {
            Log.e(TAG, "خطا در ارسال: " + e.getMessage());
        }

        context.sendBroadcast(new Intent("com.jdjv.smsbot.NEW_MESSAGE"));
    }
}
