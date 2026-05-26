package com.jdjv.smsbot;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

public class CrashLogActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(16, 16, 16, 16);

        TextView title = new TextView(this);
        title.setText("Crash Log");
        title.setTextColor(Color.RED);
        title.setTextSize(18);
        root.addView(title);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        Button copyBtn = new Button(this);
        copyBtn.setText("کپی");
        copyBtn.setTextSize(12);
        btnRow.addView(copyBtn);

        Button clearBtn = new Button(this);
        clearBtn.setText("پاک کردن");
        clearBtn.setTextSize(12);
        btnRow.addView(clearBtn);

        root.addView(btnRow);

        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scroll.setLayoutParams(slp);

        final TextView logTv = new TextView(this);
        logTv.setTextColor(Color.parseColor("#00FF00"));
        logTv.setTextSize(10);
        logTv.setTypeface(android.graphics.Typeface.MONOSPACE);
        logTv.setPadding(8, 8, 8, 8);
        logTv.setText(CrashHandler.readLog(this));
        scroll.addView(logTv);
        root.addView(scroll);

        setContentView(root);

        copyBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("crash", logTv.getText()));
                Toast.makeText(CrashLogActivity.this, "کپی شد", Toast.LENGTH_SHORT).show();
            }
        });

        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                CrashHandler.clearLog(CrashLogActivity.this);
                logTv.setText("لاگ پاک شد.");
            }
        });
    }
}
