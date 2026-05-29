package com.jdjv.smsbot;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RubikaLoginActivity extends Activity {

    private EditText phoneEdit;
    private EditText otpEdit;
    private Button sendCodeBtn;
    private Button verifyBtn;
    private TextView statusTv;
    private LinearLayout otpLayout;
    private LinearLayout groupLayout;
    private ListView groupList;

    private String tmpAuth;
    private String phoneHash;
    private String authToken;

    private final List<String[]> groups = new ArrayList<>(); // [guid, title]
    private final Set<String> selectedGuids = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));
        root.setPadding(32, 32, 32, 32);
        sv.addView(root);
        setContentView(sv);

        TextView title = new TextView(this);
        title.setText("ورود به روبیکا");
        title.setTextSize(22);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        statusTv = new TextView(this);
        statusTv.setTextSize(13);
        statusTv.setTextColor(Color.parseColor("#90CAF9"));
        statusTv.setPadding(0, 12, 0, 4);
        statusTv.setGravity(Gravity.CENTER);
        root.addView(statusTv);

        // ─ شماره تلفن ─
        TextView phoneLabel = new TextView(this);
        phoneLabel.setText("شماره موبایل (مثال: 09123456789):");
        phoneLabel.setTextColor(Color.LTGRAY);
        phoneLabel.setPadding(0, 20, 0, 4);
        root.addView(phoneLabel);

        phoneEdit = new EditText(this);
        phoneEdit.setHint("09xxxxxxxxx");
        phoneEdit.setTextColor(Color.WHITE);
        phoneEdit.setHintTextColor(Color.GRAY);
        phoneEdit.setBackgroundColor(Color.parseColor("#1E1E1E"));
        phoneEdit.setPadding(12, 12, 12, 12);
        phoneEdit.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        String savedPhone = getPrefs().getString("rubika_phone_input", "");
        if (!savedPhone.isEmpty()) phoneEdit.setText(savedPhone);
        root.addView(phoneEdit);

        sendCodeBtn = new Button(this);
        sendCodeBtn.setText("دریافت کد تأیید");
        sendCodeBtn.setBackgroundColor(Color.parseColor("#1565C0"));
        sendCodeBtn.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.setMargins(0, 8, 0, 0);
        sendCodeBtn.setLayoutParams(bp);
        root.addView(sendCodeBtn);

        // ─ OTP ─
        otpLayout = new LinearLayout(this);
        otpLayout.setOrientation(LinearLayout.VERTICAL);
        otpLayout.setVisibility(View.GONE);
        root.addView(otpLayout);

        TextView otpLabel = new TextView(this);
        otpLabel.setText("کد تأیید دریافت‌شده:");
        otpLabel.setTextColor(Color.LTGRAY);
        otpLabel.setPadding(0, 20, 0, 4);
        otpLayout.addView(otpLabel);

        otpEdit = new EditText(this);
        otpEdit.setHint("------");
        otpEdit.setTextColor(Color.WHITE);
        otpEdit.setHintTextColor(Color.GRAY);
        otpEdit.setBackgroundColor(Color.parseColor("#1E1E1E"));
        otpEdit.setPadding(12, 12, 12, 12);
        otpEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        otpLayout.addView(otpEdit);

        verifyBtn = new Button(this);
        verifyBtn.setText("تأیید و ورود");
        verifyBtn.setBackgroundColor(Color.parseColor("#2E7D32"));
        verifyBtn.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams vbp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        vbp.setMargins(0, 8, 0, 0);
        verifyBtn.setLayoutParams(vbp);
        otpLayout.addView(verifyBtn);

        // ─ انتخاب گروه‌ها ─
        groupLayout = new LinearLayout(this);
        groupLayout.setOrientation(LinearLayout.VERTICAL);
        groupLayout.setVisibility(View.GONE);
        root.addView(groupLayout);

        TextView groupLabel = new TextView(this);
        groupLabel.setText("گروه‌هایی که باید مانیتور شوند:");
        groupLabel.setTextColor(Color.parseColor("#90CAF9"));
        groupLabel.setPadding(0, 20, 0, 8);
        groupLayout.addView(groupLabel);

        groupList = new ListView(this);
        groupList.setBackgroundColor(Color.parseColor("#1A1A1A"));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 600);
        groupList.setLayoutParams(glp);
        groupLayout.addView(groupList);

        Button showLogBtn = new Button(this);
        showLogBtn.setText("نمایش لاگ روبیکا");
        showLogBtn.setBackgroundColor(Color.parseColor("#263238"));
        showLogBtn.setTextColor(Color.parseColor("#80CBC4"));
        LinearLayout.LayoutParams slbp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slbp.setMargins(0, 8, 0, 0);
        showLogBtn.setLayoutParams(slbp);
        showLogBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showRubikaLog(); }
        });
        root.addView(showLogBtn);

        Button saveGroupsBtn = new Button(this);
        saveGroupsBtn.setText("ذخیره و شروع سرویس");
        saveGroupsBtn.setBackgroundColor(Color.parseColor("#E65100"));
        saveGroupsBtn.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams sgbp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sgbp.setMargins(0, 8, 0, 0);
        saveGroupsBtn.setLayoutParams(sgbp);
        groupLayout.addView(saveGroupsBtn);

        // ─ اگه قبلاً لاگین شده ─
        String existingAuth = getPrefs().getString("rubika_auth", "");
        if (!existingAuth.isEmpty()) {
            authToken = existingAuth;
            statusTv.setText("قبلاً وارد شدی. در حال بارگذاری گروه‌ها...");
            loadGroups(existingAuth, loadPrivateKey());
        }

        sendCodeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String phone = phoneEdit.getText().toString().trim();
                if (phone.isEmpty()) {
                    Toast.makeText(RubikaLoginActivity.this, "شماره را وارد کنید", Toast.LENGTH_SHORT).show();
                    return;
                }
                String normalized = normalizePhone(phone);
                getPrefs().edit().putString("rubika_phone_input", phone).apply();
                doSendCode(normalized);
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

        saveGroupsBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (selectedGuids.isEmpty()) {
                    Toast.makeText(RubikaLoginActivity.this, "حداقل یک گروه انتخاب کنید", Toast.LENGTH_SHORT).show();
                    return;
                }
                saveSelectedGroups();
                startRubikaService();
                Toast.makeText(RubikaLoginActivity.this, "سرویس روبیکا شروع شد", Toast.LENGTH_SHORT).show();
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
                    String status = resp.optString("status", "");
                    if ("SendPassKey".equals(status) || resp.has("phone_code_hash") ||
                            "OK".equals(status) || resp.has("data")) {
                        if (resp.has("data")) {
                            JSONObject d = resp.optJSONObject("data");
                            if (d != null) phoneHash = d.optString("phone_code_hash", "");
                        }
                        if (phoneHash == null || phoneHash.isEmpty()) {
                            phoneHash = resp.optString("phone_code_hash", "nohash");
                        }
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                statusTv.setText("کد به شماره ارسال شد.");
                                otpLayout.setVisibility(View.VISIBLE);
                                sendCodeBtn.setEnabled(true);
                            }
                        });
                    } else {
                        final String err = resp.toString();
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                statusTv.setText("خطا: " + err.substring(0, Math.min(100, err.length())));
                                sendCodeBtn.setEnabled(true);
                            }
                        });
                    }
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
                    // Generate RSA key pair for secure auth exchange
                    KeyPair kp = RubikaClient.generateKeyPair();
                    String pem = RubikaClient.exportPublicKeyPem(kp.getPublic());

                    // Persist private key so RubikaService can sign future requests
                    String privKeyB64 = Base64.encodeToString(kp.getPrivate().getEncoded(), Base64.NO_WRAP);
                    getPrefs().edit().putString("rubika_private_key", privKeyB64).apply();

                    JSONObject resp = RubikaClient.signIn(
                        RubikaLoginActivity.this, tmpAuth, phone, phoneHash, otp, pem);

                    ApiLogger.log(RubikaLoginActivity.this, "RUBIKA_SIGNIN_RESP",
                        resp.toString().substring(0, Math.min(200, resp.toString().length())));

                    // Extract and RSA-OAEP decrypt real auth from server response
                    String auth = null;
                    String myGuid = null;
                    if (resp.has("data")) {
                        JSONObject d = resp.optJSONObject("data");
                        if (d != null) {
                            String encAuth = d.optString("auth", null);
                            if (encAuth != null && !encAuth.isEmpty()) {
                                try {
                                    auth = RubikaClient.decryptRSAOAEP(kp.getPrivate(), encAuth);
                                    ApiLogger.log(RubikaLoginActivity.this, "RUBIKA_AUTH_DEC",
                                        "auth len=" + auth.length());
                                } catch (Exception e) {
                                    ApiLogger.log(RubikaLoginActivity.this, "RUBIKA_RSA_ERR",
                                        e.getMessage() + " | using raw");
                                    auth = encAuth;
                                }
                            }
                            JSONObject account = d.optJSONObject("account");
                            if (account != null) myGuid = account.optString("object_guid", null);
                        }
                    }
                    if (auth == null && resp.has("auth")) auth = resp.getString("auth");
                    if (auth == null) auth = tmpAuth;

                    final String finalAuth = auth;
                    final String finalGuid = myGuid;
                    final PrivateKey finalPk = kp.getPrivate();

                    getPrefs().edit()
                        .putString("rubika_auth", finalAuth)
                        .putString("rubika_phone", phone)
                        .putString("rubika_my_guid", finalGuid != null ? finalGuid : "")
                        .apply();

                    authToken = finalAuth;

                    // Must registerDevice to activate session before any authenticated calls
                    try {
                        JSONObject regResp = RubikaClient.registerDevice(
                            RubikaLoginActivity.this, finalAuth, finalPk);
                        ApiLogger.log(RubikaLoginActivity.this, "RUBIKA_REG",
                            regResp.toString().substring(0, Math.min(80, regResp.toString().length())));
                    } catch (Exception re) {
                        ApiLogger.log(RubikaLoginActivity.this, "RUBIKA_REG_ERR", re.getMessage());
                    }

                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            statusTv.setText("ورود موفق! در حال بارگذاری گروه‌ها...");
                            verifyBtn.setEnabled(true);
                            otpLayout.setVisibility(View.GONE);
                            loadGroups(finalAuth, finalPk);
                        }
                    });

                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            statusTv.setText("خطا در تأیید: " + e.getMessage());
                            verifyBtn.setEnabled(true);
                        }
                    });
                }
            }
        }).start();
    }

    private void loadGroups(final String auth, final PrivateKey pk) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject resp = RubikaClient.getChats(RubikaLoginActivity.this, auth, pk);
                    groups.clear();

                    JSONArray chats = null;
                    if (resp.has("data")) {
                        JSONObject d = resp.optJSONObject("data");
                        if (d != null) chats = d.optJSONArray("chat_updates");
                        if (chats == null && d != null) chats = d.optJSONArray("chats");
                    }
                    if (chats == null) chats = resp.optJSONArray("chats");
                    if (chats == null) chats = resp.optJSONArray("chat_updates");

                    if (chats != null) {
                        for (int i = 0; i < chats.length(); i++) {
                            JSONObject chat = chats.getJSONObject(i);
                            JSONObject obj = chat.optJSONObject("object_data");
                            if (obj == null) obj = chat;
                            String guid = obj.optString("object_guid", "");
                            String titleStr = obj.optString("title", obj.optString("first_name", guid));
                            if (!guid.isEmpty() && (guid.startsWith("g0") || guid.startsWith("c0"))) {
                                groups.add(new String[]{guid, titleStr});
                            }
                        }
                    }

                    String saved = getPrefs().getString("rubika_groups", "");
                    if (!saved.isEmpty()) {
                        for (String g : saved.split(",")) {
                            if (!g.isEmpty()) selectedGuids.add(g);
                        }
                    }

                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (groups.isEmpty()) {
                                statusTv.setText("هیچ گروهی پیدا نشد.");
                            } else {
                                statusTv.setText(groups.size() + " گروه پیدا شد");
                            }
                            setupGroupAdapter();
                            groupLayout.setVisibility(View.VISIBLE);
                        }
                    });

                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            statusTv.setText("خطا در بارگذاری گروه‌ها: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void setupGroupAdapter() {
        groupList.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return groups.size(); }
            @Override public Object getItem(int i) { return groups.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int pos, View v, ViewGroup parent) {
                CheckBox cb;
                if (v instanceof CheckBox) {
                    cb = (CheckBox) v;
                } else {
                    cb = new CheckBox(RubikaLoginActivity.this);
                    cb.setTextColor(Color.WHITE);
                    cb.setTextSize(14);
                    cb.setPadding(16, 12, 16, 12);
                }
                String[] g = groups.get(pos);
                cb.setTag(g[0]);
                cb.setText(g[1] + "\n" + g[0]);
                cb.setChecked(selectedGuids.contains(g[0]));
                cb.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        String guid = (String) view.getTag();
                        if (((CheckBox) view).isChecked()) selectedGuids.add(guid);
                        else selectedGuids.remove(guid);
                    }
                });
                return cb;
            }
        });
    }

    private void saveSelectedGroups() {
        StringBuilder sb = new StringBuilder();
        for (String g : selectedGuids) {
            if (sb.length() > 0) sb.append(",");
            sb.append(g);
        }
        getPrefs().edit().putString("rubika_groups", sb.toString()).apply();
    }

    private void startRubikaService() {
        Intent i = new Intent(this, RubikaService.class);
        startService(i);
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
        } catch (Exception e) {
            ApiLogger.log(this, "RUBIKA_KEY_ERR", e.getMessage());
            return null;
        }
    }

    private void showRubikaLog() {
        String log = ApiLogger.read(this);
        StringBuilder sb = new StringBuilder();
        for (String line : log.split("\n")) {
            if (line.contains("RUBIKA") || line.contains("rubika")) sb.append(line).append("\n");
        }
        String filtered = sb.length() > 0 ? sb.toString() : log;

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("لاگ روبیکا");
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.setPadding(16, 8, 16, 8);
        final TextView tv = new TextView(this);
        tv.setTextColor(Color.parseColor("#80CBC4"));
        tv.setTextSize(8);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setText(filtered.isEmpty() ? "لاگی موجود نیست." : filtered);
        sv.addView(tv);
        b.setView(sv);
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
