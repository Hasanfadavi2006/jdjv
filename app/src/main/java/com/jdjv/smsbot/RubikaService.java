package com.jdjv.smsbot;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

public class RubikaService extends Service {

    private static final String TAG = "RubikaService";
    private static final long POLL_INTERVAL = 30_000L; // هر ۳۰ ثانیه
    private volatile boolean running = false;
    private Thread pollThread;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!running) {
            running = true;
            pollThread = new Thread(new Runnable() {
                @Override public void run() { pollLoop(); }
            });
            pollThread.setDaemon(true);
            pollThread.start();
            Log.d(TAG, "سرویس روبیکا شروع شد");
            ApiLogger.log(this, "RUBIKA", "سرویس شروع به کار کرد");
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (pollThread != null) pollThread.interrupt();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void pollLoop() {
        while (running) {
            try {
                poll();
            } catch (Exception e) {
                ApiLogger.log(this, "RUBIKA_ERR", e.getMessage());
            }
            try { Thread.sleep(POLL_INTERVAL); } catch (InterruptedException e) { break; }
        }
    }

    private void poll() {
        SharedPreferences prefs = getSharedPreferences("smsbot", Context.MODE_PRIVATE);
        String auth = prefs.getString("rubika_auth", "");
        String groupsCsv = prefs.getString("rubika_groups", "");

        if (auth.isEmpty() || groupsCsv.isEmpty()) return;
        if (!prefs.getBoolean("enabled", true)) return;

        String[] guids = groupsCsv.split(",");
        for (String guid : guids) {
            if (guid.trim().isEmpty()) continue;
            pollGroup(auth, guid.trim());
        }
    }

    private void pollGroup(String auth, String guid) {
        SharedPreferences prefs = getSharedPreferences("smsbot", Context.MODE_PRIVATE);
        String lastKey = "rubika_last_" + guid;
        long lastMsgId = prefs.getLong(lastKey, 0);

        try {
            JSONObject resp = RubikaClient.getMessages(this, auth, guid, lastMsgId);

            JSONArray messages = null;
            if (resp.has("data")) {
                JSONObject d = resp.optJSONObject("data");
                if (d != null) messages = d.optJSONArray("messages");
            }
            if (messages == null) messages = resp.optJSONArray("messages");
            if (messages == null || messages.length() == 0) return;

            String myGuid = prefs.getString("rubika_my_guid", "");
            long newLastId = lastMsgId;

            for (int i = 0; i < messages.length(); i++) {
                JSONObject msg = messages.getJSONObject(i);
                long msgId = msg.optLong("message_id", 0);
                if (msgId <= lastMsgId) continue;
                if (msgId > newLastId) newLastId = msgId;

                // فقط پیام‌های متنی
                String text = msg.optString("text", "").trim();
                if (text.isEmpty()) continue;

                // پیام از خودم نباشه
                String senderGuid = msg.optString("author_object_guid",
                    msg.optString("from_object_guid", ""));
                if (!myGuid.isEmpty() && myGuid.equals(senderGuid)) continue;

                // نام فرستنده
                String senderName = msg.optString("author_title", senderGuid);

                ApiLogger.log(this, "RUBIKA_MSG", guid + " | " + senderName + ": " + text);

                // ارسال به Claude
                final String fAuth = auth;
                final String fGuid = guid;
                final long fMsgId = msgId;
                final String fText = text;
                final String fSender = senderName;

                ClaudeApiClient.getRubikaReply(this, fGuid, fSender, fText,
                    new ClaudeApiClient.Callback() {
                        @Override public void onReply(String reply, double costUsd) {
                            try {
                                RubikaClient.sendMessage(RubikaService.this, fAuth, fGuid,
                                    reply, fMsgId);
                                ApiLogger.log(RubikaService.this, "RUBIKA_SENT",
                                    fGuid + " -> " + reply);
                            } catch (Exception e) {
                                ApiLogger.log(RubikaService.this, "RUBIKA_SEND_ERR", e.getMessage());
                            }
                        }
                        @Override public void onError(String error) {
                            ApiLogger.log(RubikaService.this, "RUBIKA_CLAUDE_ERR", error);
                        }
                    });
            }

            if (newLastId > lastMsgId) {
                prefs.edit().putLong(lastKey, newLastId).apply();
            }

        } catch (Exception e) {
            ApiLogger.log(this, "RUBIKA_POLL_ERR", guid + ": " + e.getMessage());
        }
    }
}
