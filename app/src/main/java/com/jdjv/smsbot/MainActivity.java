package com.jdjv.smsbot;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_PERM = 100;
    private static final String[] PERMS = {
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS
    };

    private ListView listView;
    private Button toggleBtn;
    private TextView statusTv;
    private ArrayAdapter<String> adapter;
    private final List<String> logs = new ArrayList<String>();

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent i) { refreshLogs(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));
        root.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("SmsBat");
        title.setTextSize(24);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        statusTv = new TextView(this);
        statusTv.setTextSize(16);
        statusTv.setGravity(Gravity.CENTER);
        statusTv.setPadding(0, 16, 0, 16);
        root.addView(statusTv);

        toggleBtn = new Button(this);
        root.addView(toggleBtn);

        TextView logLabel = new TextView(this);
        logLabel.setText("لاگ پیام‌ها:");
        logLabel.setTextColor(Color.LTGRAY);
        logLabel.setPadding(0, 24, 0, 8);
        root.addView(logLabel);

        listView = new ListView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        listView.setLayoutParams(lp);
        root.addView(listView);

        setContentView(root);

        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, logs);
        listView.setAdapter(adapter);

        checkPermissions();
        updateStatus();
        refreshLogs();

        toggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences prefs = getSharedPreferences("smsbot", MODE_PRIVATE);
                boolean cur = prefs.getBoolean("enabled", true);
                prefs.edit().putBoolean("enabled", !cur).apply();
                updateStatus();
                if (!cur) startService(new Intent(MainActivity.this, SmsBotService.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, new IntentFilter("com.jdjv.smsbot.NEW_MESSAGE"));
        refreshLogs();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    private void updateStatus() {
        boolean on = getSharedPreferences("smsbot", MODE_PRIVATE).getBoolean("enabled", true);
        statusTv.setText(on ? "بات: فعال" : "بات: غیرفعال");
        statusTv.setTextColor(on ? Color.parseColor("#4CAF50") : Color.RED);
        toggleBtn.setText(on ? "غیرفعال کردن بات" : "فعال کردن بات");
    }

    private void refreshLogs() {
        logs.clear();
        List<SmsLogger.Entry> entries = SmsLogger.load(this);
        for (SmsLogger.Entry e : entries) {
            String dir = e.incoming ? "از " + e.sender : "به " + e.sender;
            logs.add("[" + e.time + "] " + dir + "\n" + e.text);
        }
        adapter.notifyDataSetChanged();
    }

    private void checkPermissions() {
        List<String> missing = new ArrayList<String>();
        for (String p : PERMS) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                missing.add(p);
            }
        }
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), REQ_PERM);
        } else {
            startService(new Intent(this, SmsBotService.class));
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        boolean allOk = true;
        for (int r : results) {
            if (r != PackageManager.PERMISSION_GRANTED) { allOk = false; break; }
        }
        if (allOk) {
            startService(new Intent(this, SmsBotService.class));
            Toast.makeText(this, "دسترسی‌ها تایید شد", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "بات بدون دسترسی SMS کار نمی‌کند", Toast.LENGTH_LONG).show();
        }
    }
}
