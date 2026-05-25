package com.jdjv.smsbot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (context.getSharedPreferences("smsbot", Context.MODE_PRIVATE)
                    .getBoolean("enabled", true)) {
                context.startService(new Intent(context, SmsBotService.class));
            }
        }
    }
}
