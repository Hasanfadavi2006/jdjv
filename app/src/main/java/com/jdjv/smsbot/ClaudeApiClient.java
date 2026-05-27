package com.jdjv.smsbot;

import android.content.Context;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class ClaudeApiClient {

    private static final String API_KEY = new StringBuilder()
        .append("sk-ant-api03-cNbqK2cOqLQUV4g8K")
        .append("Caf-KxCbusvOcho4LRRFW7uzAsPryz40u9DROMljt")
        .append("IVs4btio6NYiWqmlRxD_CPD1YD1g-rhQJgAAA")
        .toString();
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL   = "claude-haiku-4-5-20251001";
    private static final int    HISTORY = 10; // تعداد پیام‌های قبلی

    public interface Callback {
        void onReply(String reply);
        void onError(String error);
    }

    public static void getReply(final Context ctx, final String sender,
                                final String newMessage, final Callback cb) {
        attemptRequest(ctx, sender, newMessage, cb, 2);
    }

    private static void attemptRequest(final Context ctx, final String sender,
                                       final String newMessage, final Callback cb,
                                       final int retriesLeft) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    ApiLogger.log(ctx, "IN", "از " + sender + ": " + newMessage);

                    // ─ گرفتن تاریخچه این شماره (ترتیب زمانی)
                    List<SmsLogger.Entry> all = SmsLogger.load(ctx);
                    List<SmsLogger.Entry> hist = new ArrayList<>();
                    for (int i = all.size() - 1; i >= 0; i--) {
                        if (all.get(i).sender.equals(sender)) hist.add(all.get(i));
                    }
                    int start = Math.max(0, hist.size() - HISTORY);
                    List<SmsLogger.Entry> window = hist.subList(start, hist.size());
                    ApiLogger.log(ctx, "HIST", "تاریخچه: " + window.size() + " پیام از " + sender);

                    // ─ ساخت آرایه messages
                    JSONArray messages = new JSONArray();
                    String lastRole = null;
                    for (SmsLogger.Entry e : window) {
                        String role = e.incoming ? "user" : "assistant";
                        if (role.equals(lastRole)) continue;
                        JSONObject m = new JSONObject();
                        m.put("role", role);
                        m.put("content", e.text);
                        messages.put(m);
                        lastRole = role;
                    }
                    if (!"user".equals(lastRole)) {
                        JSONObject cur = new JSONObject();
                        cur.put("role", "user");
                        cur.put("content", newMessage);
                        messages.put(cur);
                    }
                    if (messages.length() == 0 ||
                        !"user".equals(messages.getJSONObject(0).getString("role"))) {
                        JSONArray fixed = new JSONArray();
                        JSONObject cur = new JSONObject();
                        cur.put("role", "user");
                        cur.put("content", newMessage);
                        fixed.put(cur);
                        messages = fixed;
                    }

                    ApiLogger.log(ctx, "REQ", "ارسال به Claude — " + messages.length() + " پیام در context (تلاش " + (3 - retriesLeft) + ")");

                    // ─ ساخت body
                    JSONObject body = new JSONObject();
                    body.put("model", MODEL);
                    body.put("max_tokens", 300);
                    body.put("system",
                        "تو یه دستیار هوشمند SMS هستی. " +
                        "مکالمه قبلی رو کامل بخون و باهاش ادامه بده. " +
                        "اگه در مکالمه سوالی بود که جواب درستی نگرفت یا کاربر پیام مبهم مثل 'کمک' یا '؟' فرستاد، " +
                        "ادامه همون مکالمه رو بده و سوال بی‌جواب رو جواب بده. " +
                        "جواب‌هات کوتاه (۲-۳ جمله) و مناسب SMS باشن. " +
                        "فارسی جواب بده مگه طرف انگلیسی بنویسه.");
                    body.put("messages", messages);

                    // ─ HTTP request
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
                    ApiLogger.log(ctx, "HTTP", "status code: " + code);

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
                                          .getString("text")
                                          .trim();
                        ApiLogger.log(ctx, "REPLY", "جواب Claude: " + reply);
                        cb.onReply(reply);
                    } else if ((code == 529 || code == 503 || code == 502 || code >= 500) && retriesLeft > 0) {
                        // خطای سرور — retry بعد از ۳ ثانیه
                        ApiLogger.log(ctx, "RETRY", "خطا " + code + " — تلاش مجدد (" + retriesLeft + " مانده)");
                        try { Thread.sleep(3000); } catch (Exception ignored) {}
                        attemptRequest(ctx, sender, newMessage, cb, retriesLeft - 1);
                    } else {
                        String err = sb.toString();
                        if (err.length() > 200) err = err.substring(0, 200);
                        ApiLogger.log(ctx, "ERR", "API خطا " + code + ": " + err);
                        cb.onError("API " + code + ": " + err);
                    }

                } catch (java.net.UnknownHostException | java.net.SocketTimeoutException | java.net.ConnectException e) {
                    // خطای شبکه — retry
                    if (retriesLeft > 0) {
                        ApiLogger.log(ctx, "RETRY", "خطای شبکه: " + e.getMessage() + " — تلاش مجدد (" + retriesLeft + " مانده)");
                        try { Thread.sleep(4000); } catch (Exception ignored) {}
                        attemptRequest(ctx, sender, newMessage, cb, retriesLeft - 1);
                    } else {
                        ApiLogger.log(ctx, "EXC", "شبکه قطع است (همه تلاش‌ها شکست خورد): " + e.getMessage());
                        cb.onError("شبکه: " + e.getMessage());
                    }
                } catch (Exception e) {
                    ApiLogger.log(ctx, "EXC", e.getClass().getSimpleName() + ": " + e.getMessage());
                    cb.onError("خطا: " + e.getMessage());
                }
            }
        }).start();
    }
}
