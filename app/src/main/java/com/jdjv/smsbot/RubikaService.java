package com.jdjv.smsbot;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
    private static final long POLL_INTERVAL = 30_000L;
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
        String chatsCsv = prefs.getString("rubika_groups", "");

        if (auth.isEmpty() || chatsCsv.isEmpty()) return;
        if (!prefs.getBoolean("enabled", true)) return;

        String saveGuidCfg = prefs.getString("rubika_save_guid", "");
        String forwardToCfg = prefs.getString("rubika_forward_to", "");
        ApiLogger.log(this, "RUBIKA_CFG",
            "saveGuid=" + (saveGuidCfg.isEmpty() ? "EMPTY" : saveGuidCfg)
            + " forwardTo=" + (forwardToCfg.isEmpty() ? "EMPTY" : forwardToCfg)
            + " groups=" + chatsCsv.split(",").length);

        PrivateKey pk = loadPrivateKey(prefs);

        for (String guid : chatsCsv.split(",")) {
            if (guid.trim().isEmpty()) continue;
            pollChat(auth, pk, guid.trim(), prefs);
        }
    }

    private void pollChat(String auth, PrivateKey pk, String guid, SharedPreferences prefs) {
        String lastKey = "rubika_last_" + guid;
        long lastMsgId = prefs.getLong(lastKey, 0);
        boolean firstPoll = (lastMsgId == 0);

        // Resolve and cache chat name
        String chatName = prefs.getString("rubika_group_name_" + guid, "");
        if (chatName.isEmpty()) {
            String fetched = RubikaClient.getObjectTitle(this, auth, pk, guid);
            chatName = (fetched != null && !fetched.isEmpty()) ? fetched : guid;
            prefs.edit().putString("rubika_group_name_" + guid, chatName).apply();
        }

        try {
            JSONObject resp = RubikaClient.getMessages(this, auth, pk, guid, lastMsgId);

            JSONArray messages = null;
            if (resp.has("data")) {
                JSONObject d = resp.optJSONObject("data");
                if (d != null) messages = d.optJSONArray("messages");
            }
            if (messages == null) messages = resp.optJSONArray("messages");
            if (messages == null || messages.length() == 0) return;

            String myGuid = prefs.getString("rubika_my_guid", "");
            String forwardTo = prefs.getString("rubika_forward_to", "");
            boolean isForwardDest = !forwardTo.isEmpty() && forwardTo.equals(guid);
            long newLastId = lastMsgId;
            final String fChatName = chatName;

            for (int i = 0; i < messages.length(); i++) {
                JSONObject msg = messages.getJSONObject(i);
                long msgId = msg.optLong("message_id", 0);
                if (msgId <= lastMsgId) continue;
                if (msgId > newLastId) newLastId = msgId;

                // On first poll: just advance cursor, don't process old history
                if (firstPoll) continue;

                String senderGuid = msg.optString("author_object_guid",
                    msg.optString("from_object_guid", ""));
                if (!myGuid.isEmpty() && myGuid.equals(senderGuid)) continue;

                String senderName = msg.optString("author_title", senderGuid);
                String msgType = msg.optString("type", "Text");
                String text = msg.optString("text", "").trim();
                final String fMsgIdStr = String.valueOf(msgId);
                final long fMsgId = msgId;

                ApiLogger.log(this, "RUBIKA_MSG", fChatName + " | " + senderName
                    + " [" + msgType + "]: " + (text.isEmpty() ? "(media)" : text));

                // Forward disabled for now

                // ── Save mode: download + reply "ذخیره شد" ──
                String saveGuid = prefs.getString("rubika_save_guid", "");
                if (!saveGuid.isEmpty() && saveGuid.equals(guid)) {
                    final String fAuth2 = auth;
                    final PrivateKey fPk2 = pk;
                    final String fGuid2 = guid;
                    final long fMsgId2 = msgId;
                    final JSONObject fMsg = msg;
                    final String fChatName2 = fChatName;
                    new Thread(new Runnable() {
                        @Override public void run() {
                            saveMessage(fAuth2, fPk2, fMsg, fChatName2, fGuid2, fMsgId2);
                        }
                    }).start();
                    continue; // skip Claude reply for save-mode chat
                }

                // ── Claude reply: text only, not in forward-destination, not forwarded msgs ──
                if (text.isEmpty()) continue;
                if (isForwardDest) continue;
                if (msg.has("forwarded_from_object_guid")) continue;

                final String fAuth = auth;
                final PrivateKey fPk = pk;
                final String fGuid = guid;
                final String fText = text;
                final String fSender = senderName;

                ClaudeApiClient.getRubikaReply(this, fGuid, fSender, fText,
                    new ClaudeApiClient.Callback() {
                        @Override public void onReply(String reply, double costUsd) {
                            try {
                                RubikaClient.sendMessage(RubikaService.this, fAuth, fPk,
                                    fGuid, reply, fMsgId);
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

            if (newLastId > lastMsgId) {
                prefs.edit().putLong(lastKey, newLastId).apply();
            }

        } catch (Exception e) {
            ApiLogger.log(this, "RUBIKA_POLL_ERR", guid + ": " + e.getMessage());
        }
    }

    private void saveMessage(String auth, PrivateKey pk, JSONObject msg,
                              String chatName, String guid, long msgId) {
        String type = msg.optString("type", "Text");
        ApiLogger.log(this, "RUBIKA_SAVE_TRY", chatName + " type=" + type + " id=" + msgId);
        try {
            String safeName = chatName.replaceAll("[^\\w\\u0600-\\u06FF]", "_");

            // Try /sdcard/Ai/Rubika/ first, fall back to app-private storage
            java.io.File base = new java.io.File("/sdcard/Ai/Rubika");
            if (!base.exists()) base.mkdirs();
            if (!base.canWrite()) {
                base = new java.io.File(
                    android.os.Environment.getExternalStorageDirectory(), "Ai/Rubika");
                if (!base.exists()) base.mkdirs();
            }
            if (!base.canWrite()) {
                base = getExternalFilesDir("Rubika");
                if (base == null) base = new java.io.File(getFilesDir(), "Rubika");
                base.mkdirs();
            }

            java.io.File dir = new java.io.File(base, safeName);
            dir.mkdirs();
            ApiLogger.log(this, "RUBIKA_SAVE_DIR",
                dir.getAbsolutePath() + " | exists=" + dir.exists() + " canWrite=" + dir.canWrite());

            boolean saved = false;
            String savedDesc = "";

            if ("Text".equals(type)) {
                String text = msg.optString("text", "").trim();
                if (!text.isEmpty()) {
                    java.io.File f = new java.io.File(dir, "messages.txt");
                    java.io.FileWriter fw = new java.io.FileWriter(f, true);
                    fw.write("[" + new java.util.Date() + "] " + text + "\n");
                    fw.close();
                    saved = true;
                    savedDesc = "متن";
                    ApiLogger.log(this, "RUBIKA_SAVE_OK", f.getAbsolutePath());
                } else {
                    ApiLogger.log(this, "RUBIKA_SAVE_SKIP", "text empty");
                }
            } else {
                JSONObject fi = msg.optJSONObject("file_inline");
                if (fi == null) {
                    ApiLogger.log(this, "RUBIKA_SAVE_SKIP", "no file_inline for type=" + type);
                } else {
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
                    ApiLogger.log(this, "RUBIKA_SAVE_FILE",
                        "dc=" + dcId + " id=" + fileId + " size=" + size + " name=" + fileName);
                    if (!dcId.isEmpty() && !fileId.isEmpty() && !hash.isEmpty()) {
                        byte[] data = RubikaClient.downloadFile(auth, fileId, dcId, hash, size);
                        java.io.File outFile = new java.io.File(dir, fileName);
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile);
                        fos.write(data);
                        fos.close();
                        saved = true;
                        savedDesc = type + " (" + (data.length / 1024) + "KB) → " + fileName;
                        ApiLogger.log(this, "RUBIKA_SAVE_OK", outFile.getAbsolutePath());
                    } else {
                        ApiLogger.log(this, "RUBIKA_SAVE_SKIP",
                            "missing fields dc=" + dcId + " id=" + fileId);
                    }
                }
            }

            if (saved) {
                RubikaClient.sendMessage(this, auth, pk, guid, "✅ ذخیره شد: " + savedDesc, msgId);
            } else {
                ApiLogger.log(this, "RUBIKA_SAVE_FAIL", "nothing saved for type=" + type);
            }
        } catch (Exception e) {
            ApiLogger.log(this, "RUBIKA_SAVE_ERR", e.getMessage() + " | " + e.getClass().getSimpleName());
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
