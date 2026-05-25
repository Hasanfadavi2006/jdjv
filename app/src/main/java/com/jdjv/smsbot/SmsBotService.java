package com.jdjv.smsbot;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

// Stub - SmsReceiver handles everything via manifest registration
public class SmsBotService extends Service {
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
