package com.jdjv.smsbot;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class ClaudeApiClient {

    private static final String API_KEY = new StringBuilder()
        .append("sk-ant-api03-cNbqK2cOqLQUV4g8K")
        .append("Caf-KxCbusvOcho4LRRFW7uzAsPryz40u9DROMljt")
        .append("IVs4btio6NYiWqmlRxD_CPD1YD1g-rhQJgAAA")
        .toString();
    private static final String API_URL  = "https://api.anthropic.com/v1/messages";
    private static final String MODEL    = "claude-haiku-4-5-20251001";
    private static final String STAMP    = "​"; // zero-width space روی پیام‌های بات
    private static final int    HISTORY  = 50;

    public interface Callback {
        void onReply(String reply);
        void onError(String error);
    }

    public static void getReply(final Context ctx, final String sender,
                                final String newMessage, final Callback cb) {
        attemptRequest(ctx, sender, newMessage, cb, 2);
    }

    // ─── خواندن تاریخچه SMS از گوشی ──────────────────────────────────────────
    private static JSONArray buildMessages(Context ctx, String sender, String newMessage) throws Exception {
        List<String> bodies   = new ArrayList<>();
        List<Boolean> incomings = new ArrayList<>();

        try {
            Uri uri = Uri.parse("content://sms/");
            String alt = altPhone(sender);

            Cursor c = ctx.getContentResolver().query(
                uri,
                new String[]{"body", "type", "date"},
                "address=? OR address=?",
                new String[]{sender, alt},
                "date DESC"
            );

            if (c != null) {
                int fetched = 0;
                while (c.moveToNext() && fetched < HISTORY * 3) {
                    int type = c.getInt(1); // 1=inbox 2=sent
                    if (type != 1 && type != 2) { continue; }
                    String body = c.getString(0);
                    if (body == null || body.trim().isEmpty()) continue;
                    // حذف stamp از پیام‌های بات
                    if (body.startsWith(STAMP)) body = body.substring(1);
                    if (body.trim().isEmpty()) continue;
                    bodies.add(body.trim());
                    incomings.add(type == 1);
                    fetched++;
                }
                c.close();
            }
        } catch (Exception e) {
            ApiLogger.log(ctx, "SMS_DB", "خطا در خواندن DB گوشی: " + e.getMessage());
        }

        // ترتیب زمانی (قدیمی‌ترین اول)
        Collections.reverse(bodies);
        Collections.reverse(incomings);

        // اگه آخرین پیام همین newMessage بود، حذفش کن تا تکراری نشه
        if (!bodies.isEmpty()
                && Boolean.TRUE.equals(incomings.get(incomings.size() - 1))
                && bodies.get(bodies.size() - 1).equals(newMessage)) {
            bodies.remove(bodies.size() - 1);
            incomings.remove(incomings.size() - 1);
        }

        // آخرین HISTORY پیام
        int start = Math.max(0, bodies.size() - HISTORY);

        JSONArray messages = new JSONArray();
        String lastRole = null;
        for (int i = start; i < bodies.size(); i++) {
            String role = incomings.get(i) ? "user" : "assistant";
            if (role.equals(lastRole)) continue; // alternating اجباری
            JSONObject m = new JSONObject();
            m.put("role", role);
            m.put("content", bodies.get(i));
            messages.put(m);
            lastRole = role;
        }

        // اضافه کردن پیام جاری
        if (!"user".equals(lastRole)) {
            JSONObject cur = new JSONObject();
            cur.put("role", "user");
            cur.put("content", newMessage);
            messages.put(cur);
        }

        // اگه آرایه خالی بود یا اولی assistant
        if (messages.length() == 0 ||
            !"user".equals(messages.getJSONObject(0).getString("role"))) {
            messages = new JSONArray();
            JSONObject cur = new JSONObject();
            cur.put("role", "user");
            cur.put("content", newMessage);
            messages.put(cur);
        }

        return messages;
    }

    // تبدیل فرمت شماره: +98... ↔ 0...
    private static String altPhone(String phone) {
        if (phone == null) return "";
        if (phone.startsWith("+98")) return "0" + phone.substring(3);
        if (phone.startsWith("0") && phone.length() >= 10) return "+98" + phone.substring(1);
        return phone;
    }

    // ─── ارسال درخواست به Claude با retry ────────────────────────────────────
    private static void attemptRequest(final Context ctx, final String sender,
                                       final String newMessage, final Callback cb,
                                       final int retriesLeft) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    JSONArray messages = buildMessages(ctx, sender, newMessage);
                    ApiLogger.log(ctx, "HIST", messages.length() + " پیام از گوشی برای " + sender);
                    ApiLogger.log(ctx, "REQ",  "ارسال به Claude (تلاش " + (3 - retriesLeft) + ")");

                    JSONObject body = new JSONObject();
                    body.put("model", MODEL);
                    body.put("max_tokens", 400);
                    body.put("system",
                        "تو حسن فدوی هستی — یه آدم واقعی ایرانی. " +
                        "داری از گوشیت SMS جواب می‌دی. " +
                        "هرگز نگو که هوش مصنوعی یا دستیار هستی. اگه کسی پرسید بگو خودتی. " +
                        "تاریخچه مکالمه با این شخص رو خوندی و بر اساس اون جواب بده. " +
                        "لحن طبیعی، صمیمی و مثل یه آدم معمولی که داره SMS می‌زنه. " +
                        "جواب‌ها کوتاه باشن (۱-۳ جمله). " +
                        "اگه موضوعی در مکالمه قبلی مطرح شده بود، بهش اشاره کن. " +
                        "فارسی جواب بده مگه طرف انگلیسی بنویسه.");
                    body.put("messages", messages);

                    URL url = new URL(API_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("x-api-key", API_KEY);
                    conn.setRequestProperty("anthropic-version", "2023-06-01");
                    conn.setRequestProperty("content-type", "application/json");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);

                    byte[] bytes = body.toString().getBytes("UTF-8");
                    OutputStream os = conn.getOutputStream();
                    os.write(bytes);
                    os.close();

                    int code = conn.getResponseCode();
                    ApiLogger.log(ctx, "HTTP", "status: " + code);

                    InputStream is = (code == 200) ? conn.getInputStream() : conn.getErrorStream();
                    BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    if (code == 200) {
                        JSONObject resp = new JSONObject(sb.toString());
                        String reply = resp.getJSONArray("content")
                                          .getJSONObject(0)
                                          .getString("text").trim();
                        ApiLogger.log(ctx, "REPLY", reply);
                        cb.onReply(reply);

                    } else if ((code >= 500 || code == 529) && retriesLeft > 0) {
                        ApiLogger.log(ctx, "RETRY", "خطا " + code + " — retry (" + retriesLeft + ")");
                        try { Thread.sleep(3000); } catch (Exception ignored) {}
                        attemptRequest(ctx, sender, newMessage, cb, retriesLeft - 1);

                    } else {
                        String err = sb.toString();
                        if (err.length() > 200) err = err.substring(0, 200);
                        ApiLogger.log(ctx, "ERR", code + ": " + err);
                        cb.onError("API " + code);
                    }

                } catch (java.net.UnknownHostException | java.net.SocketTimeoutException |
                         java.net.ConnectException e) {
                    if (retriesLeft > 0) {
                        ApiLogger.log(ctx, "RETRY", "شبکه: " + e.getMessage() + " — retry (" + retriesLeft + ")");
                        try { Thread.sleep(4000); } catch (Exception ignored) {}
                        attemptRequest(ctx, sender, newMessage, cb, retriesLeft - 1);
                    } else {
                        ApiLogger.log(ctx, "EXC", "شبکه قطع: " + e.getMessage());
                        cb.onError("شبکه قطع است");
                    }
                } catch (Exception e) {
                    ApiLogger.log(ctx, "EXC", e.getClass().getSimpleName() + ": " + e.getMessage());
                    cb.onError(e.getMessage());
                }
            }
        }).start();
    }
}
