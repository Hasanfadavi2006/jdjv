package com.jdjv.smsbot;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

public class ContactDetailActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String sender = getIntent().getStringExtra("sender");
        if (sender == null) { finish(); return; }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));
        root.setPadding(0, 48, 0, 0);

        // Title
        TextView title = new TextView(this);
        title.setText(sender);
        title.setTextSize(16);
        title.setTextColor(Color.parseColor("#90CAF9"));
        title.setGravity(Gravity.CENTER);
        title.setPadding(16, 8, 16, 8);
        root.addView(title);

        // Copy button
        Button copyBtn = new Button(this);
        copyBtn.setText("کپی همه");
        copyBtn.setBackgroundColor(Color.parseColor("#1976D2"));
        copyBtn.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams cbp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cbp.setMargins(16, 4, 16, 8);
        copyBtn.setLayoutParams(cbp);
        root.addView(copyBtn);

        // Scrollable content
        ScrollView sv = new ScrollView(this);
        LinearLayout.LayoutParams svp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        sv.setLayoutParams(svp);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(16, 8, 16, 24);
        sv.addView(content);
        root.addView(sv);

        final StringBuilder fullText = new StringBuilder();
        fullText.append("=== تاریخچه پیام‌ها با ").append(sender).append(" ===\n\n");

        // ─ SMS history ─
        addSectionHeader(content, "─── تاریخچه پیام‌ها ───", "#90CAF9");

        List<SmsLogger.Entry> entries = SmsLogger.loadBySender(this, sender);
        if (entries.isEmpty()) {
            addLine(content, "پیامی در اپ ثبت نشده.", Color.GRAY, 13, false);
        } else {
            for (SmsLogger.Entry e : entries) {
                String prefix = e.incoming
                    ? "[" + e.time + "] طرف مقابل: "
                    : "[" + e.time + "] من (حسن): ";
                int color = e.incoming
                    ? Color.parseColor("#FFB74D")
                    : Color.parseColor("#81C784");
                addLine(content, prefix + e.text, color, 14, false);
                fullText.append(prefix).append(e.text).append("\n");
            }
        }

        // ─ Divider ─
        addDivider(content);

        // ─ Claude context ─
        addSectionHeader(content, "─── آنچه Claude آخرین بار خواند ───", "#CE93D8");

        String ctxLog = ClaudeApiClient.readContactContext(this, sender);
        if (ctxLog.isEmpty()) {
            addLine(content, "هنوز هیچ پیامی برای این شخص پردازش نشده.", Color.GRAY, 13, false);
        } else {
            for (String line : ctxLog.split("\n", -1)) {
                int color;
                if (line.contains("[طرف مقابل]")) {
                    color = Color.parseColor("#FFB74D");
                } else if (line.contains("[من - حسن")) {
                    color = Color.parseColor("#81C784");
                } else if (line.contains("[پیام جدید")) {
                    color = Color.parseColor("#FF8A65");
                } else {
                    color = Color.parseColor("#00E5FF");
                }
                addLine(content, line, color, 12, true);
            }
        }
        fullText.append("\n=== آنچه Claude خواند ===\n").append(ctxLog);

        setContentView(root);

        copyBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("log", fullText.toString()));
                Toast.makeText(ContactDetailActivity.this, "کپی شد", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addSectionHeader(LinearLayout parent, String text, String colorHex) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor(colorHex));
        tv.setTextSize(13);
        tv.setPadding(0, 12, 0, 6);
        parent.addView(tv);
    }

    private void addLine(LinearLayout parent, String text, int color, float size, boolean mono) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(size);
        tv.setPadding(0, 2, 0, 2);
        if (mono) tv.setTypeface(Typeface.MONOSPACE);
        parent.addView(tv);
    }

    private void addDivider(LinearLayout parent) {
        View v = new View(this);
        v.setBackgroundColor(Color.parseColor("#333333"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(0, 16, 0, 16);
        v.setLayoutParams(lp);
        parent.addView(v);
    }
}
