package com.jdjv.smsbot;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class SmsBotService extends Service {

    private static final int NOTIF_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        Intent mainIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notif = new Notification.Builder(this)
            .setContentTitle("SmsBat فعال است")
            .setContentText("در حال دریافت و پاسخ به اس‌ام‌اس...")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(pi)
            .build();

        startForeground(NOTIF_ID, notif);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
