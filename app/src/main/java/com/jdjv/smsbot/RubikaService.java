package com.jdjv.smsbot;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;

public class RubikaService extends Service {

    private static final String TAG = "RubikaService";
    private static final long POLL_INTERVAL = 5_000L;
    private volatile boolean running = false;
    private Thread pollThread;
    private int pollCount = 0;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startRubikaForeground();
        if (!running || pollThread == null || !pollThread.isAlive()) {
            running = true;
            pollCount = 0;
            pollThread = new Thread(new Runnable() {
                @Override public void run() { pollLoop(); }
            });
            pollThread.setDaemon(true);
            pollThread.start();
            ApiLogger.log(this, "RUBIKA", "سرویس شروع شد");
        }
        return START_STICKY;
    }

    private void startRubikaForeground() {
        final String CHANNEL_ID = "rubika_svc";
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                Class<?> ncClass = Class.forName("android.app.NotificationChannel");
                Object nc = ncClass
                    .getConstructor(String.class, CharSequence.class, int.class)
                    .newInstance(CHANNEL_ID, "Rubika Bot", 2);
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                nm.getClass().getMethod("createNotificationChannel", ncClass).invoke(nm, nc);
                Notification.Builder b = (Notification.Builder) Notification.Builder.class
                    .getConstructor(Context.class, String.class)
                    .newInstance(this, CHANNEL_ID);
                b.setContentTitle("SmsBat").setContentText("روبیکا فعال")
                 .setSmallIcon(android.R.drawable.ic_menu_send);
                startForeground(7001, b.build());
            } catch (Exception e) {
                Log.e(TAG, "fg err: " + e);
            }
        } else {
            Notification n = new Notification.Builder(this)
                .setContentTitle("SmsBat").setContentText("روبیکا فعال")
                .setSmallIcon(android.R.drawable.ic_menu_send).build();
            startForeground(7001, n);
        }
    }

    @Override public void onDestroy() { running = false; if (pollThread != null) pollThread.interrupt(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }

    private void pollLoop() {
        while (running) {
            try { poll(); } catch (Exception e) { ApiLogger.log(this, "RUBIKA_ERR", e.getMessage()); }
            try { Thread.sleep(POLL_INTERVAL); } catch (InterruptedException e) { break; }
        }
    }

    private void poll() {
        SharedPreferences prefs = getSharedPreferences("smsbot", Context.MODE_PRIVATE);
        String auth = prefs.getString("rubika_auth", "");
        if (auth.isEmpty()) { ApiLogger.log(this, "RUBIKA_TICK", "auth=EMPTY"); return; }

        PrivateKey pk = loadPrivateKey(prefs);
        ApiLogger.log(this, "RUBIKA_TICK", "poll#" + pollCount);

        // Refresh chat list on first poll and every 60 polls
        if (pollCount % 60 == 0) {
            refreshChats(auth, pk, prefs);
        }
        pollCount++;

        String chatsCsv = prefs.getString("rubika_groups", "");
        if (chatsCsv.isEmpty()) {
            ApiLogger.log(this, "RUBIKA_WAIT", "no chats — refreshing");
            refreshChats(auth, pk, prefs);
            chatsCsv = prefs.getString("rubika_groups", "");
            if (chatsCsv.isEmpty()) return;
        }

        for (String guid : chatsCsv.split(",")) {
            guid = guid.trim();
            if (!guid.isEmpty()) pollChat(auth, pk, guid, prefs);
        }
    }

    private void refreshChats(String auth, PrivateKey pk, SharedPreferences prefs) {
        try {
            JSONObject resp = RubikaClient.getChats(this, auth, pk);
            JSONArray list = null;
            if (resp.has("data")) {
                JSONObject d = resp.optJSONObject("data");
                if (d != null) { list = d.optJSONArray("chats"); if (list == null) list = d.optJSONArray("chat_updates"); }
            }
            if (list == null) list = resp.optJSONArray("chats");
            if (list == null) return;

            java.util.LinkedHashSet<String> guids = new java.util.LinkedHashSet<String>();
            String existing = prefs.getString("rubika_groups", "");
            if (!existing.isEmpty()) for (String g : existing.split(",")) { String t = g.trim(); if (!t.isEmpty()) guids.add(t); }
            int added = 0;
            for (int i = 0; i < list.length(); i++) {
                String g = list.getJSONObject(i).optString("object_guid", "");
                if (!g.isEmpty() && guids.add(g)) added++;
            }
            StringBuilder sb = new StringBuilder();
            for (String g : guids) { if (sb.length() > 0) sb.append(","); sb.append(g); }
            String csv = sb.toString();
            if (!csv.isEmpty()) prefs.edit().putString("rubika_groups", csv).apply();
            ApiLogger.log(this, "RUBIKA_CHATS", "total=" + guids.size() + " new=" + added);
        } catch (Exception e) {
            ApiLogger.log(this, "RUBIKA_CHATS_ERR", e.getMessage());
        }
    }

    private void pollChat(String auth, PrivateKey pk, String guid, SharedPreferences prefs) {
        String lastKey = "rubika_last_" + guid;
        long lastMsgId = prefs.getLong(lastKey, 0);
        boolean seeding = (lastMsgId == 0);

        try {
            JSONObject resp = RubikaClient.getMessages(this, auth, pk, guid, lastMsgId);

            String apiStatus = resp.optString("status", "");
            if (!"OK".equals(apiStatus)) {
                ApiLogger.log(this, "RUBIKA_MSG_ERR", guid.substring(0, 8) + " " + apiStatus + "/" + resp.optString("status_det", ""));
                return;
            }

            JSONArray messages = null;
            if (resp.has("data")) {
                JSONObject d = resp.optJSONObject("data");
                if (d != null) messages = d.optJSONArray("messages");
            }
            if (messages == null || messages.length() == 0) return;

            String chatName = prefs.getString("rubika_group_name_" + guid, guid.substring(0, 8));
            String myGuid = prefs.getString("rubika_my_guid", "");
            long newLastId = lastMsgId;

            for (int i = 0; i < messages.length(); i++) {
                JSONObject msg = messages.getJSONObject(i);
                long msgId = msg.optLong("message_id", 0);
                if (msgId > newLastId) newLastId = msgId;

                // On first-ever poll for this chat: just seed position, no reply
                if (seeding) continue;
                if (msgId <= lastMsgId) continue;

                String senderGuid = msg.optString("author_object_guid", msg.optString("from_object_guid", ""));
                boolean isSelf = !myGuid.isEmpty() && myGuid.equals(senderGuid);
                String text = msg.optString("text", "").trim();
                String msgType = msg.optString("type", "Text");

                ApiLogger.log(this, "RUBIKA_MSG", chatName + " | " + senderGuid.substring(0, Math.min(6, senderGuid.length())) + " | " + (text.isEmpty() ? "(" + msgType + ")" : text));

                // Save
                final String fa = auth; final PrivateKey fp = pk; final JSONObject fm = msg;
                final String fc = chatName; final String fg = guid; final long fi = msgId;
                new Thread(new Runnable() { @Override public void run() { saveMessage(fa, fp, fm, fc, fg, fi); } }).start();

                // Claude reply for non-self text messages
                if (!isSelf && !text.isEmpty()) {
                    final String ft = text; final String fs = senderGuid;
                    ClaudeApiClient.getRubikaReply(this, guid, senderGuid, text,
                        new ClaudeApiClient.Callback() {
                            @Override public void onReply(String reply, double cost) {
                                try {
                                    RubikaClient.sendMessage(RubikaService.this, fa, fp, fg, reply, fi);
                                    ApiLogger.log(RubikaService.this, "RUBIKA_SENT", fc + " -> " + reply);
                                } catch (Exception e) {
                                    ApiLogger.log(RubikaService.this, "RUBIKA_SEND_ERR", e.getMessage());
                                }
                            }
                            @Override public void onError(String err) {
                                ApiLogger.log(RubikaService.this, "RUBIKA_CLAUDE_ERR", err);
                            }
                        });
                }
            }

            if (newLastId > lastMsgId) {
                prefs.edit().putLong(lastKey, newLastId).apply();
                if (seeding) ApiLogger.log(this, "RUBIKA_SEED", chatName + " pos=" + newLastId);
            }

        } catch (Exception e) {
            ApiLogger.log(this, "RUBIKA_POLL_ERR", guid.substring(0, 8) + ": " + e.getMessage());
        }
    }

    private void saveMessage(String auth, PrivateKey pk, JSONObject msg, String chatName, String guid, long msgId) {
        String type = msg.optString("type", "Text");
        try {
            String safeName = chatName.replaceAll("[^\\w\\u0600-\\u06FF]", "_");
            java.io.File appExternal = getExternalFilesDir("Rubika");
            if (appExternal == null) appExternal = new java.io.File(getFilesDir(), "Rubika");
            appExternal.mkdirs();
            java.io.File base = appExternal;
            java.io.File sdcard = new java.io.File("/sdcard/Ai/Rubika");
            if (!sdcard.exists()) sdcard.mkdirs();
            if (sdcard.canWrite()) base = sdcard;
            java.io.File dir = new java.io.File(base, safeName);
            dir.mkdirs();

            if ("Text".equals(type)) {
                String text = msg.optString("text", "").trim();
                if (!text.isEmpty()) {
                    java.io.File f = new java.io.File(dir, "messages.txt");
                    java.io.FileWriter fw = new java.io.FileWriter(f, true);
                    fw.write("[" + new java.util.Date() + "] " + text + "\n");
                    fw.close();
                    ApiLogger.log(this, "RUBIKA_SAVED", chatName + " -> " + f.getAbsolutePath());
                }
            } else {
                JSONObject fi = msg.optJSONObject("file_inline");
                if (fi != null) {
                    String dcId = fi.optString("dc_id", ""), fileId = fi.optString("file_id", ""), hash = fi.optString("access_hash_rec", "");
                    long size = fi.optLong("file_size", fi.optLong("size", 0));
                    String fileName = fi.optString("file_name", "");
                    if (fileName.isEmpty()) { String mime = fi.optString("mime", ""); String ext = mime.contains("/") ? mime.split("/")[1] : type.toLowerCase(); fileName = type.toLowerCase() + "_" + msgId + "." + ext; }
                    if (!dcId.isEmpty() && !fileId.isEmpty() && !hash.isEmpty()) {
                        byte[] data = RubikaClient.downloadFile(auth, fileId, dcId, hash, size);
                        java.io.File out = new java.io.File(dir, fileName);
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                        fos.write(data); fos.close();
                        ApiLogger.log(this, "RUBIKA_SAVED", chatName + " " + type + " -> " + out.getAbsolutePath());
                    }
                }
            }
        } catch (Exception e) {
            ApiLogger.log(this, "RUBIKA_SAVE_ERR", e.getMessage());
        }
    }

    private PrivateKey loadPrivateKey(SharedPreferences prefs) {
        String b64 = prefs.getString("rubika_private_key", "");
        if (b64.isEmpty()) return null;
        try {
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (Exception e) {
            ApiLogger.log(this, "RUBIKA_KEY_ERR", e.getMessage());
            return null;
        }
    }
}
