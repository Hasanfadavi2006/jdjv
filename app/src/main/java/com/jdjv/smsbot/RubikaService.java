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
            ApiLogger.log(this, "RUBIKA", "سرویس شروع شد — getChatsUpdates mode");
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
                Log.e(TAG, "foreground err: " + e);
            }
        } else {
            Notification notif = new Notification.Builder(this)
                .setContentTitle("SmsBat").setContentText("روبیکا فعال")
                .setSmallIcon(android.R.drawable.ic_menu_send).build();
            startForeground(7001, notif);
        }
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

        if (auth.isEmpty()) {
            ApiLogger.log(this, "RUBIKA_TICK", "poll#" + pollCount + " auth=EMPTY");
            return;
        }

        PrivateKey pk = loadPrivateKey(prefs);
        String state = prefs.getString("rubika_state", "0");
        boolean isFirstState = "0".equals(state);

        ApiLogger.log(this, "RUBIKA_TICK", "poll#" + pollCount + " state=" + state.substring(0, Math.min(6, state.length())));
        pollCount++;

        try {
            JSONObject resp = RubikaClient.getChatsUpdates(this, auth, pk, state);

            String status = resp.optString("status", "");
            if (!"OK".equals(status)) {
                ApiLogger.log(this, "RUBIKA_UPD_ERR", "status=" + status + " det=" + resp.optString("status_det", ""));
                return;
            }

            JSONObject data = resp.optJSONObject("data");
            if (data == null) {
                ApiLogger.log(this, "RUBIKA_UPD_ERR", "no data in response");
                return;
            }

            String newState = data.optString("state", "");
            String dataStatus = data.optString("status", "");
            ApiLogger.log(this, "RUBIKA_UPD", "dataStatus=" + dataStatus + " newState=" + newState.substring(0, Math.min(8, newState.length())));

            if (!newState.isEmpty() && !"0".equals(newState)) {
                prefs.edit().putString("rubika_state", newState).apply();
            }

            if ("NoUpdates".equals(dataStatus)) return;

            JSONArray chatUpdates = data.optJSONArray("chat_updates");
            if (chatUpdates == null || chatUpdates.length() == 0) {
                // Log all data keys so we can debug unexpected formats
                StringBuilder keys = new StringBuilder();
                java.util.Iterator<String> kit = data.keys();
                while (kit.hasNext()) { if (keys.length() > 0) keys.append(","); keys.append(kit.next()); }
                ApiLogger.log(this, "RUBIKA_UPD_KEYS", "data keys=" + keys);
                return;
            }

            ApiLogger.log(this, "RUBIKA_GOT", "chat_updates count=" + chatUpdates.length());
            String myGuid = prefs.getString("rubika_my_guid", "");

            for (int i = 0; i < chatUpdates.length(); i++) {
                JSONObject update = chatUpdates.getJSONObject(i);
                String chatGuid = update.optString("object_guid", "");
                if (chatGuid.isEmpty()) continue;

                JSONObject msgUpdate = update.optJSONObject("message_update");
                if (msgUpdate == null) continue;

                String msgUpdateType = msgUpdate.optString("type", "");
                if (!"NewMessage".equals(msgUpdateType)) continue;

                JSONObject msg = msgUpdate.optJSONObject("message");
                if (msg == null) continue;

                String chatName = prefs.getString("rubika_group_name_" + chatGuid, "");
                if (chatName.isEmpty()) {
                    String fetched = RubikaClient.getObjectTitle(this, auth, pk, chatGuid);
                    chatName = (fetched != null && !fetched.isEmpty()) ? fetched : chatGuid.substring(0, Math.min(8, chatGuid.length()));
                    prefs.edit().putString("rubika_group_name_" + chatGuid, chatName).apply();
                }

                long msgId = msg.optLong("message_id", 0);
                String senderGuid = msg.optString("author_object_guid", msg.optString("from_object_guid", ""));
                boolean isSelf = !myGuid.isEmpty() && myGuid.equals(senderGuid);
                String senderName = msg.optString("author_title", senderGuid);
                String msgType = msg.optString("type", "Text");
                String text = msg.optString("text", "").trim();

                ApiLogger.log(this, "RUBIKA_MSG", chatName + " | " + senderName
                    + " [" + msgType + "]: " + (text.isEmpty() ? "(media)" : text));

                final String fAuth = auth;
                final PrivateKey fPk = pk;
                final JSONObject fMsg = msg;
                final String fChatName = chatName;
                final String fChatGuid = chatGuid;
                final long fMsgId = msgId;
                new Thread(new Runnable() {
                    @Override public void run() {
                        saveMessage(fAuth, fPk, fMsg, fChatName, fChatGuid, fMsgId);
                    }
                }).start();

                if (isFirstState || isSelf || text.isEmpty()) continue;

                final String fText = text;
                final String fSender = senderName;
                ClaudeApiClient.getRubikaReply(this, fChatGuid, fSender, fText,
                    new ClaudeApiClient.Callback() {
                        @Override public void onReply(String reply, double costUsd) {
                            try {
                                RubikaClient.sendMessage(RubikaService.this, fAuth, fPk,
                                    fChatGuid, reply, fMsgId);
                                ApiLogger.log(RubikaService.this, "RUBIKA_SENT",
                                    fChatName + " -> " + reply);
                            } catch (Exception e) {
                                ApiLogger.log(RubikaService.this, "RUBIKA_SEND_ERR", e.getMessage());
                            }
                        }
                        @Override public void onError(String error) {
                            ApiLogger.log(RubikaService.this, "RUBIKA_CLAUDE_ERR", error);
                        }
                    });
            }

        } catch (Exception e) {
            ApiLogger.log(this, "RUBIKA_UPD_EX", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void saveMessage(String auth, PrivateKey pk, JSONObject msg,
                              String chatName, String guid, long msgId) {
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
                    ApiLogger.log(this, "RUBIKA_SAVED", chatName + " text -> " + f.getAbsolutePath());
                }
            } else {
                JSONObject fi = msg.optJSONObject("file_inline");
                if (fi != null) {
                    String dcId = fi.optString("dc_id", "");
                    String fileId = fi.optString("file_id", "");
                    String hash = fi.optString("access_hash_rec", "");
                    long size = fi.optLong("file_size", fi.optLong("size", 0));
                    String fileName = fi.optString("file_name", "");
                    if (fileName.isEmpty()) {
                        String mime = fi.optString("mime", "");
                        String ext = mime.contains("/") ? mime.split("/")[1] : type.toLowerCase();
                        fileName = type.toLowerCase() + "_" + msgId + "." + ext;
                    }
                    if (!dcId.isEmpty() && !fileId.isEmpty() && !hash.isEmpty()) {
                        byte[] data = RubikaClient.downloadFile(auth, fileId, dcId, hash, size);
                        java.io.File outFile = new java.io.File(dir, fileName);
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile);
                        fos.write(data);
                        fos.close();
                        ApiLogger.log(this, "RUBIKA_SAVED", chatName + " " + type + " -> " + outFile.getAbsolutePath());
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
