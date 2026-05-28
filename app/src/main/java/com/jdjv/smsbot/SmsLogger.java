package com.jdjv.smsbot;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SmsLogger {

    public static class Entry {
        public final String sender, text, time;
        public final boolean incoming;
        public final double costUsd;
        public Entry(String sender, String text, String time, boolean incoming, double costUsd) {
            this.sender = sender; this.text = text;
            this.time = time; this.incoming = incoming;
            this.costUsd = costUsd;
        }
    }

    private static final String KEY = "log";
    private static final SimpleDateFormat FMT = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public static void save(Context ctx, String sender, String text, boolean incoming) {
        save(ctx, sender, text, incoming, 0.0);
    }

    public static void save(Context ctx, String sender, String text, boolean incoming, double costUsd) {
        SharedPreferences prefs = ctx.getSharedPreferences("smsbot", Context.MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            JSONObject obj = new JSONObject();
            obj.put("sender", sender);
            obj.put("text", text);
            obj.put("time", FMT.format(new Date()));
            obj.put("incoming", incoming);
            obj.put("cost", costUsd);
            arr.put(obj);
            JSONArray keep = new JSONArray();
            int start = Math.max(0, arr.length() - 200);
            for (int i = start; i < arr.length(); i++) keep.put(arr.get(i));
            prefs.edit().putString(KEY, keep.toString()).apply();
        } catch (Exception e) { }
    }

    // newest-first
    public static List<Entry> load(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("smsbot", Context.MODE_PRIVATE);
        List<Entry> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Entry(o.getString("sender"), o.getString("text"),
                    o.getString("time"), o.getBoolean("incoming"), o.optDouble("cost", 0.0)));
            }
        } catch (Exception e) { }
        return list;
    }

    // oldest-first برای صفحه جزئیات
    public static List<Entry> loadBySender(Context ctx, String sender) {
        String alt = altPhone(sender);
        SharedPreferences prefs = ctx.getSharedPreferences("smsbot", Context.MODE_PRIVATE);
        List<Entry> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String s = o.getString("sender");
                if (s.equals(sender) || s.equals(alt)) {
                    list.add(new Entry(s, o.getString("text"),
                        o.getString("time"), o.getBoolean("incoming"), o.optDouble("cost", 0.0)));
                }
            }
        } catch (Exception e) { }
        return list;
    }

    private static String altPhone(String phone) {
        if (phone == null) return "";
        if (phone.startsWith("+98")) return "0" + phone.substring(3);
        if (phone.startsWith("0") && phone.length() >= 10) return "+98" + phone.substring(1);
        return phone;
    }
}
