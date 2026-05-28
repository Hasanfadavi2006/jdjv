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
import android.view.ViewGroup;
import android.widget.BaseAdapter;
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

    private Button toggleBtn;
    private TextView statusTv;
    private BaseAdapter adapter;
    private final List<SmsLogger.Entry> smsEntries = new ArrayList<>();

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent i) { refreshLogs(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashHandler.install(this);

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

        Button crashBtn = new Button(this);
        crashBtn.setText("مشاهده لاگ خطا (Crash Log)");
        crashBtn.setBackgroundColor(Color.parseColor("#B00020"));
        crashBtn.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, 4, 0, 0);
        crashBtn.setLayoutParams(clp);
        crashBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new android.content.Intent(MainActivity.this, CrashLogActivity.class));
            }
        });
        root.addView(crashBtn);

        Button jobBtn = new Button(this);
        jobBtn.setText("JobVision - جمع‌آوری شماره و ارسال SMS");
        jobBtn.setBackgroundColor(Color.parseColor("#1976D2"));
        jobBtn.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams jlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        jlp.setMargins(0, 8, 0, 0);
        jobBtn.setLayoutParams(jlp);
        jobBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new android.content.Intent(MainActivity.this, JobVisionActivity.class));
            }
        });
        root.addView(jobBtn);

        Button claudeLogBtn = new Button(this);
        claudeLogBtn.setText("لاگ Claude API");
        claudeLogBtn.setBackgroundColor(Color.parseColor("#E65100"));
        claudeLogBtn.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp2.setMargins(0, 4, 0, 0);
        claudeLogBtn.setLayoutParams(clp2);
        claudeLogBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showClaudeLog();
            }
        });
        root.addView(claudeLogBtn);

        // ─── بخش اطلاعات شخصی ────────────────────────────────────────────────
        TextView infoLabel = new TextView(this);
        infoLabel.setText("اطلاعات من (برای جواب دادن شخصی‌تر):");
        infoLabel.setTextColor(Color.parseColor("#90CAF9"));
        infoLabel.setPadding(0, 20, 0, 4);
        root.addView(infoLabel);

        final android.widget.EditText infoEdit = new android.widget.EditText(this);
        infoEdit.setHint("مثال: مهندس هستم، اهل تهران، دوست دارم کوتاه جواب بدم...");
        infoEdit.setTextColor(Color.WHITE);
        infoEdit.setHintTextColor(Color.GRAY);
        infoEdit.setBackgroundColor(Color.parseColor("#1E1E1E"));
        infoEdit.setPadding(12, 12, 12, 12);
        infoEdit.setMinLines(3);
        infoEdit.setGravity(Gravity.TOP);
        infoEdit.setText(getSharedPreferences("smsbot", MODE_PRIVATE).getString("personal_info", ""));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        elp.setMargins(0, 0, 0, 0);
        infoEdit.setLayoutParams(elp);
        root.addView(infoEdit);

        Button saveInfoBtn = new Button(this);
        saveInfoBtn.setText("ذخیره اطلاعات");
        saveInfoBtn.setBackgroundColor(Color.parseColor("#2E7D32"));
        saveInfoBtn.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.setMargins(0, 4, 0, 0);
        saveInfoBtn.setLayoutParams(slp);
        saveInfoBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String info = infoEdit.getText().toString().trim();
                getSharedPreferences("smsbot", MODE_PRIVATE)
                    .edit().putString("personal_info", info).apply();
                Toast.makeText(MainActivity.this, "ذخیره شد", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(saveInfoBtn);

        TextView logLabel = new TextView(this);
        logLabel.setText("لاگ پیام‌ها:");
        logLabel.setTextColor(Color.LTGRAY);
        logLabel.setPadding(0, 24, 0, 8);
        root.addView(logLabel);

        final ListView listView = new ListView(this);
        listView.setDividerHeight(1);
        listView.setDivider(new android.graphics.drawable.ColorDrawable(Color.parseColor("#2A2A2A")));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        listView.setLayoutParams(lp);
        root.addView(listView);

        setContentView(root);

        adapter = new BaseAdapter() {
            @Override public int getCount() { return smsEntries.size(); }
            @Override public Object getItem(int i) { return smsEntries.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int pos, View v, ViewGroup parent) {
                LinearLayout row;
                TextView headerTv, bodyTv;
                if (v == null) {
                    row = new LinearLayout(MainActivity.this);
                    row.setOrientation(LinearLayout.VERTICAL);
                    row.setPadding(20, 10, 20, 10);
                    headerTv = new TextView(MainActivity.this);
                    headerTv.setTextSize(12);
                    headerTv.setTag("h");
                    bodyTv = new TextView(MainActivity.this);
                    bodyTv.setTextSize(15);
                    bodyTv.setTextColor(Color.WHITE);
                    bodyTv.setTag("b");
                    row.addView(headerTv);
                    row.addView(bodyTv);
                } else {
                    row = (LinearLayout) v;
                    headerTv = (TextView) row.getChildAt(0);
                    bodyTv = (TextView) row.getChildAt(1);
                }
                SmsLogger.Entry e = smsEntries.get(pos);
                String dir = e.incoming ? "از: " : "به: ";
                headerTv.setText("[" + e.time + "]  " + dir + e.sender);
                headerTv.setTextColor(e.incoming
                    ? Color.parseColor("#FFB74D")
                    : Color.parseColor("#81C784"));
                bodyTv.setText(e.text);
                return row;
            }
        };
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                android.content.Intent intent = new android.content.Intent(
                    MainActivity.this, ContactDetailActivity.class);
                intent.putExtra("sender", smsEntries.get(pos).sender);
                startActivity(intent);
            }
        });

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
        smsEntries.clear();
        smsEntries.addAll(SmsLogger.load(this));
        adapter.notifyDataSetChanged();
    }

    private void showClaudeLog() {
        String log = ApiLogger.read(this);
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("لاگ Claude API");

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.setPadding(16, 8, 16, 8);
        final TextView tv = new TextView(this);
        tv.setTextColor(Color.parseColor("#00E5FF"));
        tv.setTextSize(9);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setText(log.isEmpty() ? "لاگی موجود نیست." : log);
        sv.addView(tv);

        b.setView(sv);
        b.setPositiveButton("کپی", new android.content.DialogInterface.OnClickListener() {
            @Override public void onClick(android.content.DialogInterface d, int w) {
                android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("log", tv.getText()));
                Toast.makeText(MainActivity.this, "کپی شد", Toast.LENGTH_SHORT).show();
            }
        });
        b.setNegativeButton("پاک کردن", new android.content.DialogInterface.OnClickListener() {
            @Override public void onClick(android.content.DialogInterface d, int w) {
                ApiLogger.clear(MainActivity.this);
                Toast.makeText(MainActivity.this, "لاگ پاک شد", Toast.LENGTH_SHORT).show();
            }
        });
        b.setNeutralButton("بستن", null);
        b.show();
    }

    private void checkPermissions() {
        List<String> missing = new ArrayList<String>();
        for (String p : PERMS) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        }
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), REQ_PERM);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        boolean allOk = true;
        for (int r : results) {
            if (r != PackageManager.PERMISSION_GRANTED) { allOk = false; break; }
        }
        if (allOk) {
            Toast.makeText(this, "دسترسی‌ها تایید شد", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "بات بدون دسترسی SMS کار نمی‌کند", Toast.LENGTH_LONG).show();
        }
    }
}
