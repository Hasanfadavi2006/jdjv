package com.jdjv.smsbot;

import android.content.Context;
import android.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.util.Arrays; // SecureRandom still used in randomAuth()
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * کلاینت غیررسمی روبیکا — بر اساس reverse-engineering پروتکل
 * رمزنگاری: AES-CBC با key derivation اختصاصی (+42 shift در alphabet 62 کاراکتری)
 */
public class RubikaClient {

    private static final String CHARS =
        "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    // تبدیل auth key به AES key — هر کاراکتر +42 جابجا میشه در CHARS
    public static String createKey(String auth) {
        StringBuilder sb = new StringBuilder(auth.length());
        for (char c : auth.toCharArray()) {
            int idx = CHARS.indexOf(c);
            sb.append(idx >= 0 ? CHARS.charAt((idx + 42) % 62) : c);
        }
        return sb.toString();
    }

    public static String encrypt(String auth, String data) throws Exception {
        byte[] key = createKey(auth).getBytes("UTF-8");
        byte[] iv  = Arrays.copyOfRange(key, 0, 16); // IV = 16 bytes اول کلید (پروتکل روبیکا)
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        byte[] enc = cipher.doFinal(data.getBytes("UTF-8"));
        return Base64.encodeToString(enc, Base64.NO_WRAP);
    }

    public static String decrypt(String auth, String encData) throws Exception {
        byte[] key  = createKey(auth).getBytes("UTF-8");
        byte[] iv   = Arrays.copyOfRange(key, 0, 16); // IV = 16 bytes اول کلید
        byte[] enc  = Base64.decode(encData, Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return new String(cipher.doFinal(enc), "UTF-8");
    }

    public static String randomAuth() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom rand = new SecureRandom();
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) sb.append(chars.charAt(rand.nextInt(chars.length())));
        return sb.toString();
    }

    private static String getEndpoint() {
        return "https://messengerg2c" + (1 + (int)(Math.random() * 23)) + ".iranlms.ir/";
    }

    public static JSONObject callApi(Context ctx, String auth, String method,
                                     JSONObject params) throws Exception {
        JSONObject requestData = new JSONObject();
        requestData.put("method", method);
        requestData.put("data", params);

        String encrypted = encrypt(auth, requestData.toString());

        JSONObject body = new JSONObject();
        body.put("api_version", "6");
        body.put("auth", auth);
        body.put("data_enc", encrypted);

        URL url = new URL(getEndpoint());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("client-type", "android_c");
        conn.setRequestProperty("client-version", "3.11");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        byte[] bytes = body.toString().getBytes("UTF-8");
        OutputStream os = conn.getOutputStream();
        os.write(bytes);
        os.close();

        int code = conn.getResponseCode();
        InputStream is = (code == 200) ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        conn.disconnect();

        String raw = sb.toString();
        if (ctx != null) {
            ApiLogger.log(ctx, "RUBIKA_RAW",
                method + " -> " + code + ": " + raw.substring(0, Math.min(300, raw.length())));
        }

        JSONObject resp = new JSONObject(raw);
        if (resp.has("data_enc")) {
            String dec = decrypt(auth, resp.getString("data_enc"));
            return new JSONObject(dec);
        }
        return resp;
    }

    // ─── Authentication ───────────────────────────────────────────────────────

    public static JSONObject sendCode(Context ctx, String phone, String tmpAuth) throws Exception {
        JSONObject params = new JSONObject();
        params.put("phone_number", phone);
        params.put("send_type", "SMS");
        return callApi(ctx, tmpAuth, "sendCode", params);
    }

    public static JSONObject signIn(Context ctx, String tmpAuth, String phone,
                                    String hash, String otp) throws Exception {
        JSONObject params = new JSONObject();
        params.put("phone_number", phone);
        params.put("phone_code_hash", hash);
        params.put("phone_code", otp);
        return callApi(ctx, tmpAuth, "signIn", params);
    }

    // ─── Chats & Messages ─────────────────────────────────────────────────────

    public static JSONObject getChats(Context ctx, String auth) throws Exception {
        JSONObject params = new JSONObject();
        params.put("start_id", JSONObject.NULL);
        return callApi(ctx, auth, "getChats", params);
    }

    public static JSONObject getMessages(Context ctx, String auth, String guid,
                                         long minId) throws Exception {
        JSONObject params = new JSONObject();
        params.put("object_guid", guid);
        params.put("min_id", minId);
        params.put("limit", 20);
        params.put("sort", "FromMin");
        return callApi(ctx, auth, "getMessages", params);
    }

    public static JSONObject sendMessage(Context ctx, String auth, String guid,
                                         String text, long replyToId) throws Exception {
        JSONObject params = new JSONObject();
        params.put("object_guid", guid);
        params.put("text", text);
        params.put("rnd", (long)(Math.random() * 2_147_483_647L));
        if (replyToId > 0) params.put("reply_to_message_id", replyToId);
        return callApi(ctx, auth, "sendMessage", params);
    }
}
