package com.jdjv.smsbot;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;

public class RubikaLoginActivity extends Activity {

    private EditText phoneEdit, otpEdit;
    private Button sendCodeBtn, verifyBtn, startBtn, logBtn;
    private TextView statusTv;
    private LinearLayout otpLayout, readyLayout;

    private String tmpAuth, phoneHash, authToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));
        root.setPadding(40, 48, 40, 40);
        sv.addView(root);
        setContentView(sv);

        TextView title = new TextView(this);
        title.setText("روبیکا");
        title.setTextSize(26);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        statusTv = new TextView(this);
        statusTv.setTextSize(13);
        statusTv.setTextColor(Color.parseColor("#90CAF9"));
        statusTv.setPadding(0, 16, 0, 8);
        statusTv.setGravity(Gravity.CENTER);
        root.addView(statusTv);

        // ─── شماره ───
        TextView phoneLabel = new TextView(this);
        phoneLabel.setText("شماره موبایل:");
        phoneLabel.setTextColor(Color.LTGRAY);
        phoneLabel.setPadding(0, 24, 0, 4);
        root.addView(phoneLabel);

        phoneEdit = new EditText(this);
        phoneEdit.setHint("09xxxxxxxxx");
        phoneEdit.setTextColor(Color.WHITE);
        phoneEdit.setHintTextColor(Color.GRAY);
        phoneEdit.setBackgroundColor(Color.parseColor("#1E1E1E"));
        phoneEdit.setPadding(16, 14, 16, 14);
        phoneEdit.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        String saved = getPrefs().getString("rubika_phone_input", "");
        if (!saved.isEmpty()) phoneEdit.setText(saved);
        root.addView(phoneEdit);

        sendCodeBtn = new Button(this);
        sendCodeBtn.setText("دریافت کد تأیید");
        sendCodeBtn.setBackgroundColor(Color.parseColor("#1565C0"));
        sendCodeBtn.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.setMargins(0, 10, 0, 0);
        sendCodeBtn.setLayoutParams(bp);
        root.addView(sendCodeBtn);

        // ─── OTP (مخفی تا کد بیاد) ───
        otpLayout = new LinearLayout(this);
        otpLayout.setOrientation(LinearLayout.VERTICAL);
        otpLayout.setVisibility(View.GONE);
        root.addView(otpLayout);

        TextView otpLabel = new TextView(this);
        otpLabel.setText("کد تأیید:");
        otpLabel.setTextColor(Color.LTGRAY);
        otpLabel.setPadding(0, 24, 0, 4);
        otpLayout.addView(otpLabel);

        otpEdit = new EditText(this);
        otpEdit.setHint("------");
        otpEdit.setTextColor(Color.WHITE);
        otpEdit.setHintTextColor(Color.GRAY);
        otpEdit.setBackgroundColor(Color.parseColor("#1E1E1E"));
        otpEdit.setPadding(16, 14, 16, 14);
        otpEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        otpLayout.addView(otpEdit);

        verifyBtn = new Button(this);
        verifyBtn.setText("تأیید و ورود");
        verifyBtn.setBackgroundColor(Color.parseColor("#2E7D32"));
        verifyBtn.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams vbp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        vbp.setMargins(0, 10, 0, 0);
        verifyBtn.setLayoutParams(vbp);
        otpLayout.addView(verifyBtn);

        // ─── بخش آماده (بعد از لاگین) ───
        readyLayout = new LinearLayout(this);
        readyLayout.setOrientation(LinearLayout.VERTICAL);
        readyLayout.setVisibility(View.GONE);
        root.addView(readyLayout);

        logBtn = new Button(this);
        logBtn.setText("نمایش لاگ");
        logBtn.setBackgroundColor(Color.parseColor("#263238"));
        logBtn.setTextColor(Color.parseColor("#80CBC4"));
        LinearLayout.LayoutParams lbp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lbp.setMargins(0, 16, 0, 0);
        logBtn.setLayoutParams(lbp);
        logBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showRubikaLog(); }
        });
        readyLayout.addView(logBtn);

        startBtn = new Button(this);
        startBtn.setText("شروع سرویس");
        startBtn.setBackgroundColor(Color.parseColor("#E65100"));
        startBtn.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sbp.setMargins(0, 10, 0, 0);
        startBtn.setLayoutParams(sbp);
        readyLayout.addView(startBtn);

        // اگه قبلاً لاگین شده
        String existingAuth = getPrefs().getString("rubika_auth", "");
        if (!existingAuth.isEmpty()) {
            authToken = existingAuth;
            statusTv.setText("در حال بارگذاری چت‌ها...");
            loadChats(existingAuth, loadPrivateKey());
        }

        sendCodeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String phone = phoneEdit.getText().toString().trim();
                if (phone.isEmpty()) {
                    Toast.makeText(RubikaLoginActivity.this, "شماره را وارد کنید", Toast.LENGTH_SHORT).show();
                    return;
                }
                getPrefs().edit().putString("rubika_phone_input", phone).apply();
                doSendCode(normalizePhone(phone));
            }
        });

        verifyBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String otp = otpEdit.getText().toString().trim();
                if (otp.isEmpty()) {
                    Toast.makeText(RubikaLoginActivity.this, "کد را وارد کنید", Toast.LENGTH_SHORT).show();
                    return;
                }
                doSignIn(otp);
            }
        });

        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!hasStoragePermission()) { requestStoragePermission(); return; }
                startRubikaService();
                Toast.makeText(RubikaLoginActivity.this, "سرویس روبیکا شروع شد ✅", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void doSendCode(final String phone) {
        statusTv.setText("در حال ارسال کد...");
        sendCodeBtn.setEnabled(false);
        tmpAuth = RubikaClient.randomAuth();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject resp = RubikaClient.sendCode(RubikaLoginActivity.this, phone, tmpAuth);
                    if (resp.has("data")) {
                        JSONObject d = resp.optJSONObject("data");
                        if (d != null) phoneHash = d.optString("phone_code_hash", "");
                    }
                    if (phoneHash == null || phoneHash.isEmpty())
                        phoneHash = resp.optString("phone_code_hash", "nohash");
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            statusTv.setText("کد ارسال شد.");
                            otpLayout.setVisibility(View.VISIBLE);
                            sendCodeBtn.setEnabled(true);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            statusTv.setText("خطا: " + e.getMessage());
                            sendCodeBtn.setEnabled(true);
                        }
                    });
                }
            }
        }).start();
    }

    private void doSignIn(final String otp) {
        statusTv.setText("در حال تأیید...");
        verifyBtn.setEnabled(false);
        final String phone = normalizePhone(phoneEdit.getText().toString().trim());
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    KeyPair kp = RubikaClient.generateKeyPair();
                    String pem = RubikaClient.exportPublicKeyPem(kp.getPublic());
                    String privKeyB64 = Base64.encodeToString(kp.getPrivate().getEncoded(), Base64.NO_WRAP);
                    getPrefs().edit().putString("rubika_private_key", privKeyB64).apply();

                    JSONObject resp = RubikaClient.signIn(RubikaLoginActivity.this, tmpAuth, phone, phoneHash, otp, pem);

                    String auth = null;
                    String myGuid = null;
                    if (resp.has("data")) {
                        JSONObject d = resp.optJSONObject("data");
                        if (d != null) {
                            String encAuth = d.optString("auth", null);
                            if (encAuth != null && !encAuth.isEmpty()) {
                                try { auth = RubikaClient.decryptRSAOAEP(kp.getPrivate(), encAuth); }
                                catch (Exception e2) { auth = encAuth; }
                            }
                            JSONObject userObj = d.optJSONObject("user");
                            if (userObj == null) userObj = d.optJSONObject("account");
                            if (userObj != null)
                                myGuid = userObj.optString("user_guid",
                                    userObj.optString("object_guid", null));
                        }
                    }
                    if (auth == null && resp.has("auth")) auth = resp.getString("auth");
                    if (auth == null) auth = tmpAuth;

                    final String finalAuth = auth;
                    final PrivateKey finalPk = kp.getPrivate();
                    getPrefs().edit()
                        .putString("rubika_auth", finalAuth)
                        .putString("rubika_phone", phone)
                        .putString("rubika_my_guid", myGuid != null ? myGuid : "")
                        .apply();
                    authToken = finalAuth;

                    try {
                        RubikaClient.registerDevice(RubikaLoginActivity.this, finalAuth, finalPk);
                    } catch (Exception ignored) {}

                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            verifyBtn.setEnabled(true);
                            otpLayout.setVisibility(View.GONE);
                            statusTv.setText("ورود موفق! در حال بارگذاری چت‌ها...");
                            loadChats(finalAuth, finalPk);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            statusTv.setText("خطا: " + e.getMessage());
                            verifyBtn.setEnabled(true);
                        }
                    });
                }
            }
        }).start();
    }

    private void loadChats(final String auth, final PrivateKey pk) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject resp = RubikaClient.getChats(RubikaLoginActivity.this, auth, pk);
                    JSONArray list = null;
                    if (resp.has("data")) {
                        JSONObject d = resp.optJSONObject("data");
                        if (d != null) {
                            list = d.optJSONArray("chats");
                            if (list == null) list = d.optJSONArray("chat_updates");
                        }
                    }
                    if (list == null) list = resp.optJSONArray("chats");
                    if (list == null) list = resp.optJSONArray("chat_updates");

                    StringBuilder sb = new StringBuilder();
                    int count = 0;
                    if (list != null) {
                        for (int i = 0; i < list.length(); i++) {
                            String guid = list.getJSONObject(i).optString("object_guid", "");
                            if (guid.isEmpty()) continue;
                            if (sb.length() > 0) sb.append(",");
                            sb.append(guid);
                            count++;
                        }
                    }
                    final int fCount = count;
                    getPrefs().edit().putString("rubika_groups", sb.toString()).apply();

                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            statusTv.setText("✅ " + fCount + " چت پیدا شد\nهمه پیام‌ها در /sdcard/Ai/Rubika/ ذخیره می‌شن");
                            readyLayout.setVisibility(View.VISIBLE);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            statusTv.setText("خطا در بارگذاری: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void startRubikaService() {
        startService(new Intent(this, RubikaService.class));
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                java.lang.reflect.Method m = Environment.class.getMethod("isExternalStorageManager");
                return Boolean.TRUE.equals(m.invoke(null));
            } catch (Exception e) { return true; }
        }
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivityForResult(new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
                    Uri.parse("package:" + getPackageName())), 101);
            } catch (Exception e) {
                try { startActivityForResult(
                    new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION"), 101);
                } catch (Exception e2) {
                    Toast.makeText(this, "لطفاً دسترسی فایل را در تنظیمات فعال کنید", Toast.LENGTH_LONG).show();
                }
            }
        } else {
            requestPermissions(new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == 100 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            startRubikaService();
            Toast.makeText(this, "سرویس روبیکا شروع شد ✅", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == 101 && hasStoragePermission()) {
            startRubikaService();
            Toast.makeText(this, "سرویس روبیکا شروع شد ✅", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences("smsbot", Context.MODE_PRIVATE);
    }

    private PrivateKey loadPrivateKey() {
        String b64 = getPrefs().getString("rubika_private_key", "");
        if (b64.isEmpty()) return null;
        try {
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (Exception e) { return null; }
    }

    private void showRubikaLog() {
        String log = ApiLogger.read(this);
        StringBuilder sb = new StringBuilder();
        for (String line : log.split("\n"))
            if (line.contains("RUBIKA") || line.contains("rubika")) sb.append(line).append("\n");
        String filtered = sb.length() > 0 ? sb.toString() : log;

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("لاگ روبیکا");
        android.widget.ScrollView scrollV = new android.widget.ScrollView(this);
        scrollV.setPadding(16, 8, 16, 8);
        final android.widget.TextView tv = new android.widget.TextView(this);
        tv.setTextColor(Color.parseColor("#80CBC4"));
        tv.setTextSize(8);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setText(filtered.isEmpty() ? "لاگی موجود نیست." : filtered);
        scrollV.addView(tv);
        b.setView(scrollV);
        b.setPositiveButton("کپی", new android.content.DialogInterface.OnClickListener() {
            @Override public void onClick(android.content.DialogInterface d, int w) {
                android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("log", tv.getText()));
                Toast.makeText(RubikaLoginActivity.this, "کپی شد", Toast.LENGTH_SHORT).show();
            }
        });
        b.setNeutralButton("بستن", null);
        b.show();
    }

    private static String normalizePhone(String phone) {
        if (phone == null) return "";
        phone = phone.trim().replaceAll("\\s+", "");
        if (phone.startsWith("0")) return "98" + phone.substring(1);
        if (phone.startsWith("+")) return phone.substring(1);
        if (!phone.startsWith("98")) return "98" + phone;
        return phone;
    }
}
