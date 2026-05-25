package com.jdjv.smsbot

object BotRules {

    data class Rule(val keywords: List<String>, val reply: String)

    private val rules = listOf(
        Rule(listOf("سلام", "درود", "hello", "hi"),
            "سلام! من یه بات هستم. چطور می‌تونم کمکت کنم؟"),

        Rule(listOf("ساعت", "وقت", "چند", "time"),
            "الان ساعت ${currentTime()} هست."),

        Rule(listOf("اسمت", "اسم", "کی هستی", "who"),
            "من SmsBat هستم، یه بات خودکار اس‌ام‌اس."),

        Rule(listOf("کمک", "help", "راهنما"),
            "می‌تونی بپرسی: سلام، ساعت، وقت آزاد، اسمت چیه"),

        Rule(listOf("ممنون", "مرسی", "thanks", "خوبی"),
            "خواهش می‌کنم! کار دیگه‌ای هست؟"),

        Rule(listOf("خداحافظ", "بای", "bye"),
            "خداحافظ! موفق باشی.")
    )

    fun getReply(message: String): String {
        val lower = message.trim().lowercase()
        for (rule in rules) {
            if (rule.keywords.any { lower.contains(it) }) {
                return rule.reply
            }
        }
        return "پیامت رو گرفتم: \"$message\"\nمتاسفانه جواب این رو نمی‌دونم. برای کمک بنویس: کمک"
    }

    private fun currentTime(): String {
        val cal = java.util.Calendar.getInstance()
        return String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    }
}
