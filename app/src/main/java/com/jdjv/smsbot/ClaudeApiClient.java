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
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    // ─ گرفتن تاریخچه این شماره (ترتیب زمانی)
                    List<SmsLogger.Entry> all = SmsLogger.load(ctx);
                    List<SmsLogger.Entry> hist = new ArrayList<>();
                    // load() برعکس برمی‌گردونه، پس برعکس می‌چرخیم
                    for (int i = all.size() - 1; i >= 0; i--) {
                        if (all.get(i).sender.equals(sender)) hist.add(all.get(i));
                    }
                    // آخرین HISTORY تا
                    int start = Math.max(0, hist.size() - HISTORY);
                    List<SmsLogger.Entry> window = hist.subList(start, hist.size());

                    // ─ ساخت آرایه messages با چک alternating
                    JSONArray messages = new JSONArray();
                    String lastRole = null;
                    for (SmsLogger.Entry e : window) {
                        String role = e.incoming ? "user" : "assistant";
                        if (role.equals(lastRole)) continue; // از تکراری شدن جلوگیری
                        JSONObject m = new JSONObject();
                        m.put("role", role);
                        m.put("content", e.text);
                        messages.put(m);
                        lastRole = role;
                    }

                    // اضافه کردن پیام جدید
                    if (!"user".equals(lastRole)) {
                        JSONObject cur = new JSONObject();
                        cur.put("role", "user");
                        cur.put("content", newMessage);
                        messages.put(cur);
                    }

                    // اگه messages خالی یا اولی assistant بود، مطمئن می‌شیم user اول باشه
                    if (messages.length() == 0 ||
                        !"user".equals(messages.getJSONObject(0).getString("role"))) {
                        JSONArray fixed = new JSONArray();
                        JSONObject cur = new JSONObject();
                        cur.put("role", "user");
                        cur.put("content", newMessage);
                        fixed.put(cur);
                        messages = fixed;
                    }

                    // ─ ساخت body
                    JSONObject body = new JSONObject();
                    body.put("model", MODEL);
                    body.put("max_tokens", 300);
                    body.put("system",
                        "تو یه دستیار هوشمند SMS هستی. " +
                        "جواب‌هات باید کوتاه و مناسب SMS باشن (حداکثر ۲-۳ جمله). " +
                        "اگه طرف فارسی بنویسه فارسی جواب بده، اگه انگلیسی بنویسه انگلیسی. " +
                        "صمیمی و طبیعی باش.");
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
                        cb.onReply(reply);
                    } else {
                        String err = sb.toString();
                        if (err.length() > 120) err = err.substring(0, 120);
                        cb.onError("API " + code + ": " + err);
                    }

                } catch (Exception e) {
                    cb.onError("خطا: " + e.getMessage());
                }
            }
        }).start();
    }
}
