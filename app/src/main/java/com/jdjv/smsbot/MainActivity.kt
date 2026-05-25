package com.jdjv.smsbot

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var toggleBtn: Button
    private lateinit var statusTv: TextView
    private lateinit var adapter: ArrayAdapter<String>
    private val logs = mutableListOf<String>()

    private val PERMS = arrayOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS
    )

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshLogs()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listView  = findViewById(R.id.listView)
        toggleBtn = findViewById(R.id.toggleBtn)
        statusTv  = findViewById(R.id.statusTv)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, logs)
        listView.adapter = adapter

        checkPermissions()
        updateStatus()
        refreshLogs()

        toggleBtn.setOnClickListener {
            val prefs = getSharedPreferences("smsbot", MODE_PRIVATE)
            val cur = prefs.getBoolean("enabled", true)
            prefs.edit().putBoolean("enabled", !cur).apply()
            updateStatus()
            if (!cur) startForegroundService(Intent(this, SmsBotService::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(receiver, IntentFilter("com.jdjv.smsbot.NEW_MESSAGE"),
            RECEIVER_NOT_EXPORTED)
        refreshLogs()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    private fun updateStatus() {
        val prefs = getSharedPreferences("smsbot", MODE_PRIVATE)
        val on = prefs.getBoolean("enabled", true)
        statusTv.text = if (on) "بات: فعال ✅" else "بات: غیرفعال ❌"
        toggleBtn.text = if (on) "غیرفعال کردن بات" else "فعال کردن بات"
    }

    private fun refreshLogs() {
        logs.clear()
        SmsLogger.load(this).forEach { e ->
            val dir = if (e.incoming) "📩 از ${e.sender}" else "📤 به ${e.sender}"
            logs.add("[${e.time}] $dir\n${e.text}")
        }
        adapter.notifyDataSetChanged()
    }

    private fun checkPermissions() {
        val missing = PERMS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        } else {
            startForegroundService(Intent(this, SmsBotService::class.java))
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.all { it == PackageManager.PERMISSION_GRANTED }) {
            startForegroundService(Intent(this, SmsBotService::class.java))
            Toast.makeText(this, "دسترسی‌ها تایید شد", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "بات بدون دسترسی SMS کار نمی‌کند", Toast.LENGTH_LONG).show()
        }
    }
}
