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
        startFg();
        if (!running || pollThread == null || !pollThread.isAlive()) {
            running = true;
            pollCount = 0;
            pollThread = new Thread(new Runnable() {
                @Override public void run() { loop(); }
            });
            pollThread.setDaemon(true);
            pollThread.start();
            ApiLogger.log(this, "RUBIKA", "سرویس شروع شد");
        }
        return START_STICKY;
    }

    @Override public void onDestroy() { running = false; if (pollThread != null) pollThread.interrupt(); super.onDestroy(); }
    @Override public IBinder onBind(Intent i) { return null; }

    private void startFg() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                Class<?> nc = Class.forName("android.app.NotificationChannel");
                Object ch = nc.getConstructor(String.class, CharSequence.class, int.class)
                    .newInstance("rub", "Rubika", 2);
                ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .getClass().getMethod("createNotificationChannel", nc).invoke(getSystemService(NOTIFICATION_SERVICE), ch);
                Notification.Builder b = (Notification.Builder)
                    Notification.Builder.class.getConstructor(Context.class, String.class).newInstance(this, "rub");
                b.setContentTitle("SmsBat").setContentText("روبیکا").setSmallIcon(android.R.drawable.ic_menu_send);
                startForeground(7001, b.build());
            } else {
                startForeground(7001, new Notification.Builder(this)
                    .setContentTitle("SmsBat").setContentText("روبیکا")
                    .setSmallIcon(android.R.drawable.ic_menu_send).build());
            }
        } catch (Exception e) { Log.e(TAG, "fg:" + e); }
    }

    private void loop() {
        while (running) {
            try { poll(); } catch (Exception e) { ApiLogger.log(this, "RUBIKA_ERR", e.getMessage()); }
            try { Thread.sleep(POLL_INTERVAL); } catch (InterruptedException e) { break; }
        }
    }

    private void poll() throws Exception {
        SharedPreferences p = getSharedPreferences("smsbot", Context.MODE_PRIVATE);
        String auth = p.getString("rubika_auth", "");
        if (auth.isEmpty()) { ApiLogger.log(this, "RUBIKA_TICK", "auth=EMPTY"); return; }
        PrivateKey pk = loadPk(p);

        // Initialize state: look back 150 seconds so we catch recent messages
        long state = p.getLong("rubika_state_ts", 0);
        if (state == 0) {
            state = System.currentTimeMillis() / 1000L - 150;
            p.edit().putLong("rubika_state_ts", state).apply();
        }

        ApiLogger.log(this, "RUBIKA_TICK", "poll#" + pollCount + " state=" + state);
        pollCount++;

        JSONObject resp = RubikaClient.getChatsUpdates(this, auth, pk, String.valueOf(state));

        if (!"OK".equals(resp.optString("status", ""))) {
            ApiLogger.log(this, "RUBIKA_ERR", "status=" + resp.optString("status") + " " + resp.optString("status_det"));
            return;
        }

        JSONObject data = resp.optJSONObject("data");
        if (data == null) { ApiLogger.log(this, "RUBIKA_ERR", "data=null"); return; }

        // new_state is a Long in the response
        long newState = data.optLong("new_state", 0);
        String dataStatus = data.optString("status", "");
        ApiLogger.log(this, "RUBIKA_UPD", "dataStatus=" + dataStatus + " newState=" + newState);

        if (newState > 0 && newState != state) {
            p.edit().putLong("rubika_state_ts", newState).apply();
        }

        if ("OldState".equals(dataStatus)) {
            // OldState: server says our cursor is too old — advance to now
            p.edit().putLong("rubika_state_ts", System.currentTimeMillis() / 1000L - 10).apply();
            return;
        }

        JSONArray chats = data.optJSONArray("chats");
        if (chats == null || chats.length() == 0) return;

        ApiLogger.log(this, "RUBIKA_GOT", "updated chats=" + chats.length());
        String myGuid = p.getString("rubika_my_guid", "");

        for (int i = 0; i < chats.length(); i++) {
            JSONObject chat = chats.getJSONObject(i);
            String guid = chat.optString("object_guid", "");
            if (guid.isEmpty()) continue;

            int unseen = chat.optInt("count_unseen", 0);

            // Chat display name from abs_object.title
            String chatName = p.getString("rubika_group_name_" + guid, "");
            if (chatName.isEmpty()) {
                JSONObject abs = chat.optJSONObject("abs_object");
                if (abs != null) chatName = abs.optString("title", "");
                if (chatName.isEmpty()) chatName = guid.substring(0, Math.min(8, guid.length()));
                p.edit().putString("rubika_group_name_" + guid, chatName).apply();
            }

            ApiLogger.log(this, "RUBIKA_CHAT", chatName + " unseen=" + unseen);
            if (unseen <= 0) continue;

            // last_message is the most recent message in this chat
            JSONObject lastMsg = chat.optJSONObject("last_message");
            if (lastMsg != null) {
                handleMsg(auth, pk, guid, chatName, lastMsg, myGuid, p);
            }

            // If more than 1 unread, fetch the rest with getMessages
            if (unseen > 1) {
                long lastSeenId = 0;
                try {
                    String lsm = chat.optString("last_seen_my_mid", "0");
                    if (!lsm.isEmpty() && !"null".equals(lsm)) lastSeenId = Long.parseLong(lsm);
                } catch (Exception ignored) {}
                fetchUnseen(auth, pk, guid, chatName, lastSeenId, myGuid, p);
            }
        }
    }

    private void handleMsg(String auth, PrivateKey pk, String guid, String chatName,
                           JSONObject msg, String myGuid, SharedPreferences p) {
        try {
            // In getChatsUpdates, message_id is a String
            String msgIdStr = msg.optString("message_id", "0");
            long msgId = 0;
            try { msgId = Long.parseLong(msgIdStr); } catch (Exception ignored) {}

            // Skip already-processed messages
            String seenKey = "rubika_last_" + guid;
            long lastSeen = p.getLong(seenKey, 0);
            if (msgId > 0 && msgId <= lastSeen) return;
            if (msgId > lastSeen) p.edit().putLong(seenKey, msgId).apply();

            String senderGuid = msg.optString("author_object_guid", "");
            boolean isSelf = !myGuid.isEmpty() && myGuid.equals(senderGuid);
            String senderName = msg.optString("author_title", senderGuid);
            String type = msg.optString("type", "Text");
            String text = msg.optString("text", "").trim();

            ApiLogger.log(this, "RUBIKA_MSG", chatName + " | " + senderName + " | " + (text.isEmpty() ? "(" + type + ")" : text));

            // Save text message
            if ("Text".equals(type) && !text.isEmpty()) {
                saveText(chatName, text);
            }

            // Claude reply for non-self text messages
            if (!isSelf && !text.isEmpty()) {
                final String fa = auth, ft = text, fn = chatName, fg = guid;
                final PrivateKey fp = pk;
                final long fi = msgId;
                ClaudeApiClient.getRubikaReply(this, guid, senderName, text,
                    new ClaudeApiClient.Callback() {
                        @Override public void onReply(String reply, double cost) {
                            try {
                                RubikaClient.sendMessage(RubikaService.this, fa, fp, fg, reply, fi);
                                ApiLogger.log(RubikaService.this, "RUBIKA_SENT", fn + " -> " + reply);
                            } catch (Exception e) {
                                ApiLogger.log(RubikaService.this, "RUBIKA_SEND_ERR", e.getMessage());
                            }
                        }
                        @Override public void onError(String e) {
                            ApiLogger.log(RubikaService.this, "RUBIKA_CLAUDE_ERR", e);
                        }
                    });
            }
        } catch (Exception e) {
            ApiLogger.log(this, "RUBIKA_MSG_ERR", e.getMessage());
        }
    }

    private void fetchUnseen(String auth, PrivateKey pk, String guid, String chatName,
                              long afterMsgId, String myGuid, SharedPreferences p) {
        try {
            JSONObject resp = RubikaClient.getMessages(this, auth, pk, guid, afterMsgId);
            if (!"OK".equals(resp.optString("status", ""))) return;

            JSONArray messages = null;
            JSONObject d = resp.optJSONObject("data");
            if (d != null) messages = d.optJSONArray("messages");
            if (messages == null || messages.length() == 0) return;

            ApiLogger.log(this, "RUBIKA_FETCH", chatName + " msgs=" + messages.length());

            for (int i = 0; i < messages.length(); i++) {
                JSONObject msg = messages.getJSONObject(i);
                long msgId = msg.optLong("message_id", 0);
                long lastSeen = p.getLong("rubika_last_" + guid, 0);
                if (msgId <= lastSeen) continue;

                handleMsg(auth, pk, guid, chatName, msg, myGuid, p);
            }
        } catch (Exception e) {
            ApiLogger.log(this, "RUBIKA_FETCH_ERR", e.getMessage());
        }
    }

    private void saveText(String chatName, String text) {
        try {
            String safe = chatName.replaceAll("[^\\w\\u0600-\\u06FF]", "_");
            java.io.File base;
            java.io.File sd = new java.io.File("/sdcard/Ai/Rubika");
            if (!sd.exists()) sd.mkdirs();
            if (sd.canWrite()) {
                base = sd;
            } else {
                base = getExternalFilesDir("Rubika");
                if (base == null) base = new java.io.File(getFilesDir(), "Rubika");
                base.mkdirs();
            }
            java.io.File dir = new java.io.File(base, safe);
            dir.mkdirs();
            java.io.File f = new java.io.File(dir, "messages.txt");
            java.io.FileWriter fw = new java.io.FileWriter(f, true);
            fw.write("[" + new java.util.Date() + "] " + text + "\n");
            fw.close();
            ApiLogger.log(this, "RUBIKA_SAVED", f.getAbsolutePath());
        } catch (Exception e) {
            ApiLogger.log(this, "RUBIKA_SAVE_ERR", e.getMessage());
        }
    }

    private PrivateKey loadPk(SharedPreferences p) {
        try {
            byte[] b = Base64.decode(p.getString("rubika_private_key", ""), Base64.DEFAULT);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(b));
        } catch (Exception e) {
            ApiLogger.log(this, "RUBIKA_KEY_ERR", e.getMessage());
            return null;
        }
    }
}
