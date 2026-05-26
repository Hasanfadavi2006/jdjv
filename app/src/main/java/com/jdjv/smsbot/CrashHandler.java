package com.jdjv.smsbot;

import android.content.Context;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String FILE = "crash_log.txt";
    private final Context ctx;
    private final Thread.UncaughtExceptionHandler prev;

    public static void install(Context ctx) {
        CrashHandler h = new CrashHandler(ctx.getApplicationContext(),
            Thread.getDefaultUncaughtExceptionHandler());
        Thread.setDefaultUncaughtExceptionHandler(h);
    }

    private CrashHandler(Context ctx, Thread.UncaughtExceptionHandler prev) {
        this.ctx = ctx;
        this.prev = prev;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
            pw.println("=== CRASH " + time + " ===");
            pw.println("Thread: " + t.getName());
            e.printStackTrace(pw);
            pw.println();

            File f = new File(ctx.getFilesDir(), FILE);
            // نگه داشتن آخرین ۵ کرش
            String old = "";
            if (f.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(f));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
                br.close();
                old = sb.toString();
                // فقط ۱۰ هزار کاراکتر آخر رو نگه دار
                if (old.length() > 10000) old = old.substring(old.length() - 10000);
            }
            FileWriter fw = new FileWriter(f, false);
            fw.write(old + sw.toString());
            fw.close();
        } catch (Exception ignored) {}

        if (prev != null) prev.uncaughtException(t, e);
    }

    public static String readLog(Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(), "crash_log.txt");
            if (!f.exists()) return "هیچ کرشی ثبت نشده.";
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

    public static void clearLog(Context ctx) {
        new File(ctx.getFilesDir(), "crash_log.txt").delete();
    }
}
