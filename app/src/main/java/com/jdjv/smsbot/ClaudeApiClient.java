package com.jdjv.smsbot;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class ClaudeApiClient {

    private static final String API_KEY = new StringBuilder()
        .append("sk-ant-api03-cNbqK2cOqLQUV4g8K")
        .append("Caf-KxCbusvOcho4LRRFW7uzAsPryz40u9DROMljt")
        .append("IVs4btio6NYiWqmlRxD_CPD1YD1g-rhQJgAAA")
        .toString();
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL   = "claude-opus-4-7";
    private static final String STAMP   = "​";
    private static final int    HISTORY = 50;
    // قیمت claude-opus (دلار به ازای یک میلیون توکن)
    private static final double PRICE_IN_PER_MTOK  = 15.0;
    private static final double PRICE_OUT_PER_MTOK = 75.0;

    public interface Callback {
        void onReply(String reply, double costUsd);
        void onError(String error);
    }

    public interface BalanceCallback {
        void onResult(String text);
    }

    public static void getBalance(final Context ctx, final BalanceCallback cb) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    URL url = new URL("https://api.anthropic.com/v1/organizations/me");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("x-api-key", API_KEY);
                    conn.setRequestProperty("anthropic-version", "2023-06-01");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);

                    int code = conn.getResponseCode();
                    InputStream is = (code == 200) ? conn.getInputStream() : conn.getErrorStream();
                    BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    if (code == 200) {
                        JSONObject resp = new JSONObject(sb.toString());
                        // تلاش برای پیدا کردن فیلد موجودی در پاسخ
                        if (resp.has("credit_balance")) {
                            cb.onResult("$" + String.format("%.4f", resp.getDouble("credit_balance")));
                        } else if (resp.has("credits")) {
                            cb.onResult("$" + String.format("%.4f", resp.getDouble("credits")));
                        } else {
                            // API موجودی برنمی‌گردونه — باید از console بررسی کرد
                            cb.onResult("console.anthropic.com");
                        }
                    } else {
                        cb.onResult("console.anthropic.com");
                    }
                } catch (Exception e) {
                    cb.onResult("console.anthropic.com");
                }
            }
        }).start();
    }

    public static void getReply(final Context ctx, final String sender,
                                final String newMessage, final Callback cb) {
        attemptRequest(ctx, sender, newMessage, cb, 2);
    }

    // ─── خواندن تاریخچه از گوشی ─────────────────────────────────────────────
    private static JSONArray buildMessages(Context ctx, String sender, String newMessage,
                                           StringBuilder ctxLog, StringBuilder styleCtx) throws Exception {
        List<String> bodies    = new ArrayList<>();
        List<Boolean> incomings = new ArrayList<>();
        List<Long> dates       = new ArrayList<>();

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
                    int type = c.getInt(1);
                    if (type != 1 && type != 2) continue;
                    String body = c.getString(0);
                    if (body == null || body.trim().isEmpty()) continue;
                    if (body.startsWith(STAMP)) body = body.substring(1);
                    if (body.trim().isEmpty()) continue;
                    bodies.add(body.trim());
                    incomings.add(type == 1);
                    dates.add(c.getLong(2));
                    fetched++;
                }
                c.close();
            }
        } catch (Exception e) {
            ApiLogger.log(ctx, "SMS_DB", "خطا: " + e.getMessage());
        }

        Collections.reverse(bodies);
        Collections.reverse(incomings);
        Collections.reverse(dates);

        // حذف تکراری اگه newMessage آخرین پیام باشه
        if (!bodies.isEmpty()
                && Boolean.TRUE.equals(incomings.get(incomings.size() - 1))
                && bodies.get(bodies.size() - 1).equals(newMessage)) {
            bodies.remove(bodies.size() - 1);
            incomings.remove(incomings.size() - 1);
            dates.remove(dates.size() - 1);
        }

        int start = Math.max(0, bodies.size() - HISTORY);
        long now = System.currentTimeMillis();
        long ONE_HOUR = 3_600_000L;

        // ─ لاگ context با رنگ‌گذاری ─
        ctxLog.append("── context ارسالی به Claude ──\n");
        JSONArray messages = new JSONArray();
        String lastRole = null;
        for (int i = start; i < bodies.size(); i++) {
            boolean inc = incomings.get(i);
            String role  = inc ? "user" : "assistant";
            String label = inc ? "  [طرف مقابل]" : "  [من - حسن ]";
            boolean isOld = (now - dates.get(i)) > ONE_HOUR;

            if (isOld) {
                // پیام قدیمی‌تر از ۱ ساعت — فقط برای تشخیص سبک، نه ادامه موضوع
                styleCtx.append(label).append(": ").append(bodies.get(i)).append("\n");
                ctxLog.append("  [قدیمی-سبک]").append(label).append(": ").append(bodies.get(i)).append("\n");
            } else {
                ctxLog.append(label).append(": ").append(bodies.get(i)).append("\n");
                if (!role.equals(lastRole)) {
                    JSONObject m = new JSONObject();
                    m.put("role", role);
                    m.put("content", bodies.get(i));
                    messages.put(m);
                    lastRole = role;
                }
            }
        }
        ctxLog.append("  [پیام جدید ]: ").append(newMessage).append("\n");
        ctxLog.append("──────────────────────────────\n");

        if (!"user".equals(lastRole)) {
            JSONObject cur = new JSONObject();
            cur.put("role", "user");
            cur.put("content", newMessage);
            messages.put(cur);
        }
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

    private static String altPhone(String phone) {
        if (phone == null) return "";
        if (phone.startsWith("+98")) return "0" + phone.substring(3);
        if (phone.startsWith("0") && phone.length() >= 10) return "+98" + phone.substring(1);
        return phone;
    }

    // ─── ذخیره/خواندن context به ازای هر شخص ──────────────────────────────────
    private static String normalizePhone(String phone) {
        if (phone == null) return "unknown";
        if (phone.startsWith("0") && phone.length() >= 10) return "+98" + phone.substring(1);
        return phone;
    }

    static void saveContactContext(Context ctx, String sender, String context) {
        String filename = "ctx_" + normalizePhone(sender).replaceAll("[^0-9]", "") + ".txt";
        try {
            java.io.FileWriter fw = new java.io.FileWriter(
                new java.io.File(ctx.getApplicationContext().getFilesDir(), filename), false);
            fw.write(context);
            fw.close();
        } catch (Exception ignored) {}
    }

    public static String readContactContext(Context ctx, String sender) {
        for (String s : new String[]{sender, altPhone(sender)}) {
            String filename = "ctx_" + normalizePhone(s).replaceAll("[^0-9]", "") + ".txt";
            try {
                java.io.File f = new java.io.File(ctx.getApplicationContext().getFilesDir(), filename);
                if (!f.exists()) continue;
                BufferedReader br = new BufferedReader(new InputStreamReader(
                    new java.io.FileInputStream(f), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
                br.close();
                return sb.toString();
            } catch (Exception ignored) {}
        }
        return "";
    }

    // ─── ارسال به Claude با retry ─────────────────────────────────────────────
    private static void attemptRequest(final Context ctx, final String sender,
                                       final String newMessage, final Callback cb,
                                       final int retriesLeft) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    StringBuilder ctxLog = new StringBuilder();
                    StringBuilder styleCtx = new StringBuilder();
                    JSONArray messages = buildMessages(ctx, sender, newMessage, ctxLog, styleCtx);
                    saveContactContext(ctx, sender, ctxLog.toString());
                    ApiLogger.log(ctx, "HIST", messages.length() + " پیام از گوشی برای " + sender);
                    ApiLogger.log(ctx, "CTX", ctxLog.toString());
                    ApiLogger.log(ctx, "REQ", "ارسال به Claude " + MODEL + " (تلاش " + (3 - retriesLeft) + ")");

                    // ─ اطلاعات شخصی کاربر ─
                    String personalInfo = ctx.getSharedPreferences("smsbot", Context.MODE_PRIVATE)
                        .getString("personal_info", "");

                    // ─ تاریخ و ساعت واقعی ─
                    String nowDate = new SimpleDateFormat("EEEE, yyyy-MM-dd", Locale.ENGLISH).format(new Date());
                    String nowTime = new SimpleDateFormat("HH:mm", Locale.ENGLISH).format(new Date());

                    // ─ system prompt ─
                    StringBuilder sys = new StringBuilder();
                    sys.append("تو حسن فدوی هستی — یه آدم واقعی ایرانی. داری از گوشیت SMS جواب می‌دی.\n");
                    sys.append("امروز: ").append(nowDate).append("، ساعت: ").append(nowTime).append(".\n");
                    if (!personalInfo.isEmpty()) {
                        sys.append("اطلاعات بیشتر درباره خودت: ").append(personalInfo).append("\n");
                    }
                    if (styleCtx.length() > 0) {
                        sys.append("سابقه مکالمات قدیمی با این شخص (بیشتر از یک ساعت پیش — موضوعشون تموم شده):\n");
                        sys.append("فقط از این پیام‌ها سبک و لحن مکالمه رو بفهم (رسمی/غیررسمی/صمیمی)، نه موضوعشون رو ادامه بده:\n");
                        sys.append(styleCtx.toString()).append("\n");
                    }
                    sys.append("قوانین:\n");
                    sys.append("- هرگز نگو هوش مصنوعی یا دستیار هستی. اگه پرسیدن بگو خودتی.\n");
                    sys.append("- تاریخچه مکالمه با این شخص رو خوندی؛ لحن و موضوع رو ادامه بده.\n");
                    sys.append("- جواب‌ها کوتاه و طبیعی مثل SMS واقعی (۱-۳ جمله).\n");
                    sys.append("- اگه سوالی درباره تاریخ/ساعت/اخبار پرسیدن، از اطلاعات بالا استفاده کن.\n");
                    sys.append("- اگه سوالی داری که جوابش رو نمی‌دونی، طبیعی بگو نمی‌دونم.\n");
                    sys.append("- فارسی جواب بده مگه طرف انگلیسی بنویسه.\n");
                    sys.append("- با هر شخص بر اساس رابطه‌ای که در پیام‌های قبلی بوده جواب بده.");

                    JSONObject body = new JSONObject();
                    body.put("model", MODEL);
                    body.put("max_tokens", 400);
                    body.put("system", sys.toString());
                    body.put("messages", messages);

                    URL url = new URL(API_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("x-api-key", API_KEY);
                    conn.setRequestProperty("anthropic-version", "2023-06-01");
                    conn.setRequestProperty("content-type", "application/json");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(20000);
                    conn.setReadTimeout(40000);

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
                        // محاسبه هزینه از توکن‌های مصرف‌شده
                        double costUsd = 0;
                        JSONObject usage = resp.optJSONObject("usage");
                        if (usage != null) {
                            int inTok  = usage.optInt("input_tokens", 0);
                            int outTok = usage.optInt("output_tokens", 0);
                            costUsd = (inTok * PRICE_IN_PER_MTOK + outTok * PRICE_OUT_PER_MTOK) / 1_000_000.0;
                            ApiLogger.log(ctx, "COST", "in=" + inTok + " out=" + outTok
                                + " => $" + String.format("%.6f", costUsd));
                        }
                        ApiLogger.log(ctx, "REPLY", reply);
                        cb.onReply(reply, costUsd);

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
                        ApiLogger.log(ctx, "RETRY", "شبکه: " + e.getMessage() + " (" + retriesLeft + ")");
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
