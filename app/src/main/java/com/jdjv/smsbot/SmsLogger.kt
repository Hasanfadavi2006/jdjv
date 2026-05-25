package com.jdjv.smsbot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object SmsLogger {

    data class Entry(val sender: String, val text: String, val time: String, val incoming: Boolean)

    private const val KEY = "log"
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun save(context: Context, sender: String, text: String, incoming: Boolean) {
        val prefs = context.getSharedPreferences("smsbot", Context.MODE_PRIVATE)
        val arr = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrDefault(JSONArray())
        val obj = JSONObject().apply {
            put("sender", sender)
            put("text", text)
            put("time", fmt.format(Date()))
            put("incoming", incoming)
        }
        arr.put(obj)
        // نگه‌داری حداکثر ۲۰۰ پیام
        val keep = JSONArray()
        val start = maxOf(0, arr.length() - 200)
        for (i in start until arr.length()) keep.put(arr.get(i))
        prefs.edit().putString(KEY, keep.toString()).apply()
    }

    fun load(context: Context): List<Entry> {
        val prefs = context.getSharedPreferences("smsbot", Context.MODE_PRIVATE)
        val arr = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Entry(o.getString("sender"), o.getString("text"), o.getString("time"), o.getBoolean("incoming"))
        }.reversed()
    }
}
