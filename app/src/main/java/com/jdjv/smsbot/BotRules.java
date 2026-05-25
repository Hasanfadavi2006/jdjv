package com.jdjv.smsbot;

import java.util.Calendar;

public class BotRules {

    private static final String[][] RULES = {
        {"سلام|درود|hello|hi",       "سلام! من SmsBat هستم. چطور می‌تونم کمکت کنم؟"},
        {"ساعت|وقت|time",            null},   // dynamic
        {"اسمت|کی هستی|who",         "من SmsBat هستم، یه بات خودکار اس‌ام‌اس."},
        {"کمک|help|راهنما",          "می‌تونی بپرسی: سلام، ساعت، اسمت چیه، ممنون"},
        {"ممنون|مرسی|thanks|خوبی",   "خواهش می‌کنم! کار دیگه‌ای هست؟"},
        {"خداحافظ|بای|bye",          "خداحافظ! موفق باشی."},
    };

    public static String getReply(String message) {
        String lower = message.trim().toLowerCase();
        for (String[] rule : RULES) {
            String[] keywords = rule[0].split("\\|");
            for (String kw : keywords) {
                if (lower.contains(kw)) {
                    if (rule[1] == null) {
                        Calendar cal = Calendar.getInstance();
                        return String.format("الان ساعت %02d:%02d هست.",
                            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
                    }
                    return rule[1];
                }
            }
        }
        return "پیامت رو گرفتم: \"" + message + "\"\nبرای راهنما بنویس: کمک";
    }
}
