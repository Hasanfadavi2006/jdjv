package com.jdjv.smsbot;

import android.content.Context;
import android.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;

public class RubikaClient {

    private static final String CHARS =
        "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

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
        byte[] iv  = Arrays.copyOfRange(key, 0, 16);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        byte[] enc = cipher.doFinal(data.getBytes("UTF-8"));
        return Base64.encodeToString(enc, Base64.NO_WRAP);
    }

    public static String decrypt(String auth, String encData) throws Exception {
        String result = tryAllDecryptions(auth, encData, null);
        if (result != null) return result;
        throw new Exception("decrypt failed for all variants");
    }

    static String tryAllDecryptions(String auth, String encData, Context ctx) {
        byte[] ciphertext;
        try { ciphertext = Base64.decode(encData.trim(), Base64.DEFAULT); }
        catch (Exception e) { return null; }

        // ─ همه key های ممکن ─
        byte[][] keys = buildKeyVariants(auth);

        for (byte[] key : keys) {
            if (key == null || (key.length != 16 && key.length != 24 && key.length != 32)) continue;

            // IV ثابت = 16 بایت اول کلید
            String r = tryPKCS5(key, Arrays.copyOfRange(key, 0, 16), ciphertext, ctx, "fixedIV");
            if (r != null) return r;

            // IV از 16 بایت اول سیفرتکست
            if (ciphertext.length > 16) {
                r = tryPKCS5(key, Arrays.copyOfRange(ciphertext, 0, 16),
                    Arrays.copyOfRange(ciphertext, 16, ciphertext.length), ctx, "prependIV");
                if (r != null) return r;
            }

            // IV صفر
            r = tryPKCS5(key, new byte[16], ciphertext, ctx, "zeroIV");
            if (r != null) return r;

            // NoPadding — برای padding غیر استاندارد
            r = tryNoPadding(key, Arrays.copyOfRange(key, 0, 16), ciphertext, ctx, "noPad-fixedIV");
            if (r != null) return r;
            r = tryNoPadding(key, new byte[16], ciphertext, ctx, "noPad-zeroIV");
            if (r != null) return r;
        }

        if (ctx != null) ApiLogger.log(ctx, "RUBIKA_DEC_FAIL",
            "auth8=" + auth.substring(0, Math.min(8, auth.length()))
            + " cipherLen=" + ciphertext.length
            + " allVariantsFailed");
        return null;
    }

    private static byte[][] buildKeyVariants(String auth) {
        try {
            byte[] ck32 = createKey(auth).getBytes("UTF-8");                        // createKey 32B
            byte[] ck16 = Arrays.copyOfRange(ck32, 0, 16);                          // createKey 16B
            byte[] ck24 = Arrays.copyOfRange(ck32, 0, 24);                          // createKey 24B
            byte[] raw32 = auth.getBytes("UTF-8");                                  // raw 32B
            byte[] raw16 = Arrays.copyOfRange(raw32, 0, 16);                        // raw 16B

            // double-createKey
            byte[] dck32 = createKey(createKey(auth)).getBytes("UTF-8");            // double 32B
            byte[] dck16 = Arrays.copyOfRange(dck32, 0, 16);

            // MD5(auth) = 16B
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] md5raw = md5.digest(auth.getBytes("UTF-8"));                     // MD5 raw
            byte[] md5ck  = md5.digest(createKey(auth).getBytes("UTF-8"));          // MD5 createKey

            // SHA-256(auth) = 32B
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] sha256raw = sha.digest(auth.getBytes("UTF-8"));                  // SHA-256 raw
            byte[] sha256ck  = sha.digest(createKey(auth).getBytes("UTF-8"));       // SHA-256 createKey
            byte[] sha256raw16 = Arrays.copyOfRange(sha256raw, 0, 16);
            byte[] sha256ck16  = Arrays.copyOfRange(sha256ck, 0, 16);

            // SHA-1(auth) = 20B → ناسازگار با AES، برش به 16B
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] sha1raw16 = Arrays.copyOfRange(sha1.digest(auth.getBytes("UTF-8")), 0, 16);
            byte[] sha1ck16  = Arrays.copyOfRange(sha1.digest(createKey(auth).getBytes("UTF-8")), 0, 16);

            return new byte[][] {
                ck32, ck24, ck16,
                raw32, raw16,
                dck32, dck16,
                md5raw, md5ck,
                sha256raw, sha256ck, sha256raw16, sha256ck16,
                sha1raw16, sha1ck16
            };
        } catch (Exception e) {
            return new byte[0][];
        }
    }

    private static String tryPKCS5(byte[] key, byte[] iv, byte[] enc, Context ctx, String label) {
        if (iv.length != 16) return null;
        try {
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            String result = new String(c.doFinal(enc), "UTF-8").trim();
            if (result.startsWith("{") || result.startsWith("[")) {
                if (ctx != null) ApiLogger.log(ctx, "RUBIKA_DEC_OK",
                    label + " keyLen=" + key.length + " -> " + result.substring(0, Math.min(80, result.length())));
                return result;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String tryNoPadding(byte[] key, byte[] iv, byte[] enc, Context ctx, String label) {
        if (iv.length != 16 || enc.length % 16 != 0) return null;
        try {
            Cipher c = Cipher.getInstance("AES/CBC/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] raw = c.doFinal(enc);
            // بررسی اینکه آیا با { شروع میشه
            if (raw.length > 0 && raw[0] == 0x7B) { // '{'
                String result = new String(raw, "UTF-8").trim();
                // trim padding bytes از انتها
                int end = result.lastIndexOf('}');
                if (end > 0) result = result.substring(0, end + 1);
                if (ctx != null) ApiLogger.log(ctx, "RUBIKA_DEC_OK",
                    "NoPad-" + label + " keyLen=" + key.length + " -> " + result.substring(0, Math.min(80, result.length())));
                return result;
            }
        } catch (Exception ignored) {}
        return null;
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
            // auth اول ۸ کاراکترش لاگ میشه برای debug
            ApiLogger.log(ctx, "RUBIKA_RAW",
                method + " auth=" + auth.substring(0, 8) + "... -> " + code
                + ": " + raw.substring(0, Math.min(300, raw.length())));
        }

        JSONObject resp = new JSONObject(raw);
        if (resp.has("data_enc")) {
            String encData = resp.getString("data_enc");
            String dec = tryAllDecryptions(auth, encData, ctx);
            if (dec != null) {
                try { return new JSONObject(dec); } catch (Exception ignored) {}
            }
            if (ctx != null) ApiLogger.log(ctx, "RUBIKA_DEC_FAIL",
                "auth=" + auth + " enc=" + encData);
            return resp;
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
