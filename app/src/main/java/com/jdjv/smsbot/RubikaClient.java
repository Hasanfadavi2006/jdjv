package com.jdjv.smsbot;

import android.content.Context;
import android.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.OAEPParameterSpec;
import java.io.*;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.*;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * کلاینت روبیکا — پروتکل API v6 (rubpy-compatible)
 * Key: passphrase(auth), IV: zero, Field: tmp_session / input / sign
 */
public class RubikaClient {

    private static final JSONObject CLIENT_INFO;
    static {
        JSONObject c = new JSONObject();
        try {
            c.put("app_name", "Main");
            c.put("app_version", "3.8.2");
            c.put("platform", "Android");
            c.put("package", "app.rbmain.a");
            c.put("lang_code", "fa");
        } catch (Exception ignored) {}
        CLIENT_INFO = c;
    }

    // ─── key derivation ───────────────────────────────────────────────────────

    /** passphrase: rearrange 4 chunks, then shift each char +9 in a-z */
    public static String passphrase(String auth) {
        if (auth.length() != 32) throw new IllegalArgumentException("auth must be 32 chars");
        String c0 = auth.substring(0, 8), c1 = auth.substring(8, 16);
        String c2 = auth.substring(16, 24), c3 = auth.substring(24, 32);
        String rearranged = c2 + c0 + c3 + c1;
        StringBuilder sb = new StringBuilder(32);
        for (char c : rearranged.toCharArray()) {
            sb.append((char) (((c - 'a' + 9) % 26) + 'a'));
        }
        return sb.toString();
    }

    /** decode_auth: self-inverse substitution for lower/upper/digit */
    public static String decodeAuth(String auth) {
        StringBuilder sb = new StringBuilder(auth.length());
        for (char c : auth.toCharArray()) {
            if (c >= 'a' && c <= 'z')      sb.append((char) (((32 - (c - 'a')) % 26) + 'a'));
            else if (c >= 'A' && c <= 'Z') sb.append((char) (((29 - (c - 'A')) % 26) + 'A'));
            else if (c >= '0' && c <= '9') sb.append((char) (((13 - (c - '0')) % 10) + '0'));
            else sb.append(c);
        }
        return sb.toString();
    }

    // ─── AES encrypt/decrypt ──────────────────────────────────────────────────

    public static String encrypt(String auth, String data) throws Exception {
        byte[] key = passphrase(auth).getBytes("UTF-8");
        byte[] iv  = new byte[16]; // zero IV
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return Base64.encodeToString(c.doFinal(data.getBytes("UTF-8")), Base64.NO_WRAP);
    }

    public static String decrypt(String auth, String encData) throws Exception {
        byte[] key = passphrase(auth).getBytes("UTF-8");
        byte[] iv  = new byte[16];
        byte[] ct  = Base64.decode(encData, Base64.DEFAULT);
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return new String(c.doFinal(ct), "UTF-8");
    }

    // ─── RSA ─────────────────────────────────────────────────────────────────

    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);
        return kpg.generateKeyPair();
    }

    /** Export RSA public key as PKCS#1 PEM (not X.509 SPKI) */
    public static String exportPublicKeyPem(PublicKey pub) throws Exception {
        RSAPublicKey rsa = (RSAPublicKey) pub;
        byte[] pkcs1 = buildPKCS1PublicKey(rsa.getModulus(), rsa.getPublicExponent());
        String b64 = Base64.encodeToString(pkcs1, Base64.NO_WRAP);
        // Chunk into 64-char lines like OpenSSL PEM
        StringBuilder pem = new StringBuilder("-----BEGIN RSA PUBLIC KEY-----\n");
        for (int i = 0; i < b64.length(); i += 64) {
            pem.append(b64, i, Math.min(i + 64, b64.length())).append('\n');
        }
        pem.append("-----END RSA PUBLIC KEY-----\n");
        return pem.toString();
    }

    private static byte[] buildPKCS1PublicKey(BigInteger modulus, BigInteger exponent) throws Exception {
        byte[] mod = toUnsignedByteArray(modulus);
        byte[] exp = toUnsignedByteArray(exponent);
        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        writeDerInteger(inner, mod);
        writeDerInteger(inner, exp);
        byte[] seq = inner.toByteArray();
        ByteArrayOutputStream outer = new ByteArrayOutputStream();
        outer.write(0x30); // SEQUENCE
        writeDerLength(outer, seq.length);
        outer.write(seq);
        return outer.toByteArray();
    }

    private static byte[] toUnsignedByteArray(BigInteger n) {
        byte[] b = n.toByteArray();
        if (b[0] == 0 && b.length > 1) return Arrays.copyOfRange(b, 1, b.length);
        return b;
    }

    private static void writeDerInteger(OutputStream os, byte[] val) throws IOException {
        os.write(0x02); // INTEGER tag
        // If high bit set, prepend 0x00
        boolean needPad = (val[0] & 0x80) != 0;
        int len = val.length + (needPad ? 1 : 0);
        writeDerLength(os, len);
        if (needPad) os.write(0x00);
        os.write(val);
    }

    private static void writeDerLength(OutputStream os, int len) throws IOException {
        if (len < 128) {
            os.write(len);
        } else if (len < 256) {
            os.write(0x81); os.write(len);
        } else {
            os.write(0x82); os.write((len >> 8) & 0xFF); os.write(len & 0xFF);
        }
    }

    /** RSA-OAEP decrypt (SHA-1) to get real auth from signIn response */
    public static String decryptRSAOAEP(PrivateKey privateKey, String encData) throws Exception {
        byte[] enc = Base64.decode(encData, Base64.DEFAULT);
        Cipher c = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
        c.init(Cipher.DECRYPT_MODE, privateKey);
        return new String(c.doFinal(enc), "UTF-8");
    }

    /** RSA PKCS1v15 SHA256 sign for authenticated calls */
    public static String signData(PrivateKey privateKey, String data) throws Exception {
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey);
        sig.update(data.getBytes("UTF-8"));
        return Base64.encodeToString(sig.sign(), Base64.NO_WRAP);
    }

    // ─── auth helpers ─────────────────────────────────────────────────────────

    public static String randomAuth() {
        String chars = "abcdefghijklmnopqrstuvwxyz";
        SecureRandom rand = new SecureRandom();
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) sb.append(chars.charAt(rand.nextInt(chars.length())));
        return sb.toString();
    }

    private static String getEndpoint() {
        return "https://messengerg2c" + (1 + (int)(Math.random() * 23)) + ".iranlms.ir/";
    }

    // ─── API call ─────────────────────────────────────────────────────────────

    /**
     * Send API request.
     * @param auth      the user's session auth (32 chars)
     * @param tmpSession true for unauthenticated calls (sendCode, signIn); false for authenticated
     * @param method    API method name
     * @param input     inner input params
     * @param privateKey RSA private key for signing authenticated calls (null for tmp_session calls)
     */
    public static JSONObject callApi(Context ctx, String auth, boolean tmpSession,
                                     String method, JSONObject input,
                                     PrivateKey privateKey) throws Exception {
        // Build inner encrypted payload
        JSONObject innerData = new JSONObject();
        innerData.put("client", CLIENT_INFO);
        innerData.put("method", method);
        innerData.put("input", input);

        String dataEnc = encrypt(auth, innerData.toString());

        // Build outer request body
        JSONObject body = new JSONObject();
        body.put("api_version", "6");
        if (tmpSession) {
            body.put("tmp_session", auth);
        } else {
            body.put("auth", decodeAuth(auth));
            if (privateKey != null) {
                body.put("sign", signData(privateKey, dataEnc));
            }
        }
        body.put("data_enc", dataEnc);

        URL url = new URL(getEndpoint());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("User-Agent", "okhttp/3.12.1");
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
            try {
                String dec = decrypt(auth, resp.getString("data_enc"));
                if (ctx != null) ApiLogger.log(ctx, "RUBIKA_DEC", dec.substring(0, Math.min(150, dec.length())));
                return new JSONObject(dec);
            } catch (Exception e) {
                if (ctx != null) ApiLogger.log(ctx, "RUBIKA_DEC_FAIL", e.getMessage()
                    + " auth=" + auth + " enc=" + resp.getString("data_enc").substring(0, 40));
            }
        }
        return resp;
    }

    // ─── Authentication ───────────────────────────────────────────────────────

    public static JSONObject sendCode(Context ctx, String phone, String tmpAuth) throws Exception {
        JSONObject input = new JSONObject();
        input.put("phone_number", phone);
        input.put("send_type", "SMS");
        return callApi(ctx, tmpAuth, true, "sendCode", input, null);
    }

    /**
     * signIn — includes RSA public key so server can encrypt the real auth with it.
     * Returns raw JSON; caller must RSA-decrypt result["data"]["auth"] for real auth.
     */
    public static JSONObject signIn(Context ctx, String tmpAuth, String phone,
                                    String hash, String otp, String publicKeyPem) throws Exception {
        JSONObject input = new JSONObject();
        input.put("phone_number", phone);
        input.put("phone_code_hash", hash);
        input.put("phone_code", otp);
        if (publicKeyPem != null && !publicKeyPem.isEmpty()) {
            // encode public key as rubpy does: decode_auth(base64(pem_bytes))
            String b64Pem = Base64.encodeToString(publicKeyPem.getBytes("UTF-8"), Base64.NO_WRAP);
            input.put("public_key", decodeAuth(b64Pem));
        }
        return callApi(ctx, tmpAuth, true, "signIn", input, null);
    }

    // ─── Authenticated calls ──────────────────────────────────────────────────

    public static JSONObject registerDevice(Context ctx, String auth, PrivateKey pk) throws Exception {        JSONObject input = new JSONObject();
        input.put("token", "");
        input.put("lang_code", "fa");
        input.put("token_type", "Firebase");
        input.put("app_version", "MA_3.8.2");
        input.put("system_version", "SDK 22");
        input.put("device_model", "samsungSM-G925F");
        input.put("device_hash", "23121"); // '2' + digits from 'okhttp/3.12.1'
        return callApi(ctx, auth, false, "registerDevice", input, pk);
    }

    /** Get display title for any chat type (group/channel/user). Returns null on failure. */
    public static String getObjectTitle(Context ctx, String auth, PrivateKey pk, String guid) {
        try {
            JSONObject input = new JSONObject();
            String method, dataKey, subKey;
            if (guid.startsWith("g0")) {
                input.put("group_guid", guid);
                method = "getGroupInfo"; dataKey = "group"; subKey = "title";
            } else if (guid.startsWith("c0")) {
                input.put("channel_guid", guid);
                method = "getChannelInfo"; dataKey = "channel"; subKey = "title";
            } else {
                // u0, s0, etc. — private/bot chats
                input.put("user_guid", guid);
                method = "getUserInfo"; dataKey = "user"; subKey = null;
            }
            JSONObject resp = callApi(ctx, auth, false, method, input, pk);
            JSONObject d = resp.optJSONObject("data");
            if (d != null) {
                JSONObject obj = d.optJSONObject(dataKey);
                if (obj != null) {
                    if (subKey != null) return obj.optString(subKey, null);
                    // user: first_name + last_name
                    String fn = obj.optString("first_name", "").trim();
                    String ln = obj.optString("last_name", "").trim();
                    String name = (fn + " " + ln).trim();
                    return name.isEmpty() ? obj.optString("username", null) : name;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Forward a message (any type: text/image/video/voice/file) to another chat. */
    public static JSONObject forwardMessages(Context ctx, String auth, PrivateKey pk,
                                              String fromGuid, String messageId,
                                              String toGuid) throws Exception {
        JSONObject input = new JSONObject();
        input.put("from_object_guid", fromGuid);
        JSONArray ids = new JSONArray();
        ids.put(messageId);
        input.put("message_ids", ids);
        input.put("to_object_guid", toGuid);
        input.put("rnd", (long)(Math.random() * 2_147_483_647L));
        return callApi(ctx, auth, false, "forwardMessages", input, pk);
    }

    /** @deprecated use getObjectTitle */
    public static String getGroupTitle(Context ctx, String auth, PrivateKey pk, String guid) {
        return getObjectTitle(ctx, auth, pk, guid);
    }

    public static JSONObject getChats(Context ctx, String auth, PrivateKey pk) throws Exception {
        JSONObject input = new JSONObject();
        input.put("start_id", JSONObject.NULL);
        return callApi(ctx, auth, false, "getChats", input, pk);
    }

    public static JSONObject getMessages(Context ctx, String auth, PrivateKey pk,
                                         String guid, long minId) throws Exception {
        JSONObject input = new JSONObject();
        input.put("object_guid", guid);
        input.put("min_id", minId);
        input.put("limit", 20);
        input.put("sort", "FromMin");
        return callApi(ctx, auth, false, "getMessages", input, pk);
    }

    public static JSONObject sendMessage(Context ctx, String auth, PrivateKey pk,
                                          String guid, String text, long replyToId) throws Exception {
        JSONObject input = new JSONObject();
        input.put("object_guid", guid);
        input.put("text", text);
        input.put("rnd", (long)(Math.random() * 2_147_483_647L));
        if (replyToId > 0) input.put("reply_to_message_id", replyToId);
        return callApi(ctx, auth, false, "sendMessage", input, pk);
    }
}
