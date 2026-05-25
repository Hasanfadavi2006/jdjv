package com.jdjv.smsbot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// SmsReceiver is manifest-registered and wakes automatically.
// Nothing to start on boot.
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) { }
}
