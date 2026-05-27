package com.jdjv.smsbot;

import android.content.Context;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ApiLogger {

    private static final String FILE = "api_log.txt";
    private static final int MAX_BYTES = 30000;

    public static synchronized void log(Context ctx, String tag, String msg) {
        try {
            String time = new SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
            String line = "[" + time + "] [" + tag + "] " + msg + "\n";

            File f = new File(ctx.getApplicationContext().getFilesDir(), FILE);
            String old = "";
            if (f.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(f));
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = br.readLine()) != null) sb.append(l).append("\n");
                br.close();
                old = sb.toString();
                if (old.length() > MAX_BYTES) old = old.substring(old.length() - MAX_BYTES);
            }
            FileWriter fw = new FileWriter(f, false);
            fw.write(old + line);
            fw.close();
        } catch (Exception ignored) {}
    }

    public static String read(Context ctx) {
        try {
            File f = new File(ctx.getApplicationContext().getFilesDir(), FILE);
            if (!f.exists()) return "هیچ لاگی ثبت نشده.";
            BufferedReader br = new BufferedReader(new FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            br.close();
            return sb.toString();
        } catch (Exception e) {
            return "خطا در خواندن لاگ: " + e.getMessage();
        }
    }

    public static void clear(Context ctx) {
        new File(ctx.getApplicationContext().getFilesDir(), FILE).delete();
    }
}
