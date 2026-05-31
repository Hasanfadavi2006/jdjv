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
import android.provider.Settings;
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
import android.widget.RadioButton;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class RubikaLoginActivity extends Activity {

    private EditText phoneEdit;
    private EditText otpEdit;
    private Button sendCodeBtn;
    private Button verifyBtn;
    private TextView statusTv;
    private LinearLayout otpLayout;
    private LinearLayout chatLayout;
    private ListView monitorList;   // checkboxes — which chats to watch
    private ListView forwardList;   // radio — where to forward
    private ListView saveList;      // radio — one chat to save everything from

    private String tmpAuth;
    private String phoneHash;
    private String authToken;

    // [guid, displayName]
    private final List<String[]> chats = new ArrayList<>();
    // GUIDs to monitor (default: all). Empty = none selected yet.
    private final Set<String> monitoredGuids = new HashSet<>();
    // Single destination for forwarding; empty = disabled
    private String forwardToGuid = "";
    // Single chat for save mode; empty = disabled
    private String saveGuid = "";

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

        // ─ بخش انتخاب چت‌ها ─
        chatLayout = new LinearLayout(this);
        chatLayout.setOrientation(LinearLayout.VERTICAL);
        chatLayout.setVisibility(View.GONE);
        root.addView(chatLayout);

        // -- مانیتور --
        TextView monitorLabel = new TextView(this);
        monitorLabel.setText("کدام چت‌ها مانیتور شوند؟ (دیفالت: همه)");
        monitorLabel.setTextColor(Color.parseColor("#90CAF9"));
        monitorLabel.setPadding(0, 20, 0, 8);
        chatLayout.addView(monitorLabel);

        monitorList = new ListView(this);
        monitorList.setBackgroundColor(Color.parseColor("#1A1A1A"));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 600);
        monitorList.setLayoutParams(mlp);
        chatLayout.addView(monitorList);

        // -- فوروارد --
        TextView forwardLabel = new TextView(this);
        forwardLabel.setText("پیام‌ها را به کجا فوروارد کنم؟ (اختیاری)");
        forwardLabel.setTextColor(Color.parseColor("#FFCC02"));
        forwardLabel.setPadding(0, 20, 0, 8);
        chatLayout.addView(forwardLabel);

        TextView forwardHint = new TextView(this);
        forwardHint.setText("یکی انتخاب کن — همه پیام‌های چت‌های انتخابی اونجا فوروارد می‌شن (متن، عکس، ویدیو، ویس)");
        forwardHint.setTextColor(Color.GRAY);
        forwardHint.setTextSize(11);
        forwardHint.setPadding(0, 0, 0, 6);
        chatLayout.addView(forwardHint);

        forwardList = new ListView(this);
        forwardList.setBackgroundColor(Color.parseColor("#1A1A1A"));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 400);
        forwardList.setLayoutParams(flp);
        chatLayout.addView(forwardList);

        // -- حالت ذخیره --
        TextView saveLabel = new TextView(this);
        saveLabel.setText("حالت ذخیره (تست) — یه چت انتخاب کن:");
        saveLabel.setTextColor(Color.parseColor("#A5D6A7"));
        saveLabel.setPadding(0, 20, 0, 4);
        chatLayout.addView(saveLabel);

        TextView saveHint = new TextView(this);
        saveHint.setText("هر پیامی (متن، عکس، ویدیو، ویس، فایل) ذخیره می‌شه + ریپلای «✅ ذخیره شد»\nمسیر: /sdcard/Ai/Rubika/");
        saveHint.setTextColor(Color.GRAY);
        saveHint.setTextSize(10);
        saveHint.setPadding(0, 0, 0, 6);
        chatLayout.addView(saveHint);

        saveList = new ListView(this);
        saveList.setBackgroundColor(Color.parseColor("#1A1A1A"));
        LinearLayout.LayoutParams slp2 = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 400);
        saveList.setLayoutParams(slp2);
        chatLayout.addView(saveList);

        // -- لاگ --
        Button showLogBtn = new Button(this);
        showLogBtn.setText("نمایش لاگ روبیکا");
        showLogBtn.setBackgroundColor(Color.parseColor("#263238"));
        showLogBtn.setTextColor(Color.parseColor("#80CBC4"));
        LinearLayout.LayoutParams slbp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slbp.setMargins(0, 12, 0, 0);
        showLogBtn.setLayoutParams(slbp);
        showLogBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showRubikaLog(); }
        });
        chatLayout.addView(showLogBtn);

        // -- ذخیره --
        Button saveBtn = new Button(this);
        saveBtn.setText("ذخیره و شروع سرویس");
        saveBtn.setBackgroundColor(Color.parseColor("#E65100"));
        saveBtn.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sbp.setMargins(0, 8, 0, 0);
        saveBtn.setLayoutParams(sbp);
        chatLayout.addView(saveBtn);

        // ─ اگه قبلاً لاگین شده ─
        String existingAuth = getPrefs().getString("rubika_auth", "");
        if (!existingAuth.isEmpty()) {
            authToken = existingAuth;
            forwardToGuid = getPrefs().getString("rubika_forward_to", "");
            saveGuid = getPrefs().getString("rubika_save_guid", "");
            statusTv.setText("قبلاً وارد شدی. در حال بارگذاری چت‌ها...");
            loadChats(existingAuth, loadPrivateKey());
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

        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (monitoredGuids.isEmpty()) {
                    Toast.makeText(RubikaLoginActivity.this, "حداقل یک چت انتخاب کنید", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!hasStoragePermission()) {
                    requestStoragePermission();
                    return;
                }
                saveSelections();
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
                    if ("OK".equals(status) || resp.has("data")) {
                        if (resp.has("data")) {
                            JSONObject d = resp.optJSONObject("data");
                            if (d != null) phoneHash = d.optString("phone_code_hash", "");
                        }
                        if (phoneHash == null || phoneHash.isEmpty())
                            phoneHash = resp.optString("phone_code_hash", "nohash");
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
                    KeyPair kp = RubikaClient.generateKeyPair();
                    String pem = RubikaClient.exportPublicKeyPem(kp.getPublic());

                    String privKeyB64 = Base64.encodeToString(kp.getPrivate().getEncoded(), Base64.NO_WRAP);
                    getPrefs().edit().putString("rubika_private_key", privKeyB64).apply();

                    JSONObject resp = RubikaClient.signIn(
                        RubikaLoginActivity.this, tmpAuth, phone, phoneHash, otp, pem);

                    ApiLogger.log(RubikaLoginActivity.this, "RUBIKA_SIGNIN_RESP",
                        resp.toString().substring(0, Math.min(200, resp.toString().length())));

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
                            statusTv.setText("ورود موفق! در حال بارگذاری چت‌ها...");
                            verifyBtn.setEnabled(true);
                            otpLayout.setVisibility(View.GONE);
                            loadChats(finalAuth, finalPk);
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

    private void loadChats(final String auth, final PrivateKey pk) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject resp = RubikaClient.getChats(RubikaLoginActivity.this, auth, pk);
                    chats.clear();

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

                    if (list != null) {
                        for (int i = 0; i < list.length(); i++) {
                            JSONObject chat = list.getJSONObject(i);
                            String guid = chat.optString("object_guid", "");
                            if (guid.isEmpty()) continue;
                            // Use cached name if available
                            String cachedName = getPrefs().getString("rubika_group_name_" + guid, "");
                            String displayName = !cachedName.isEmpty() ? cachedName : guid;
                            chats.add(new String[]{guid, displayName});
                        }
                    }

                    // Default: select all for monitoring
                    String savedMonitor = getPrefs().getString("rubika_groups", "");
                    if (savedMonitor.isEmpty()) {
                        for (String[] c : chats) monitoredGuids.add(c[0]);
                    } else {
                        for (String g : savedMonitor.split(",")) {
                            if (!g.isEmpty()) monitoredGuids.add(g);
                        }
                    }

                    // Load saved selections
                    forwardToGuid = getPrefs().getString("rubika_forward_to", "");
                    saveGuid = getPrefs().getString("rubika_save_guid", "");

                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            statusTv.setText(chats.size() + " چت پیدا شد");
                            setupAdapters();
                            chatLayout.setVisibility(View.VISIBLE);
                        }
                    });

                    // Resolve names async for chats still showing GUIDs
                    resolveNamesInBackground(auth, pk);

                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            statusTv.setText("خطا در بارگذاری چت‌ها: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void resolveNamesInBackground(final String auth, final PrivateKey pk) {
        final AtomicInteger idx = new AtomicInteger(0);
        final ExecutorService pool = Executors.newFixedThreadPool(4);
        for (int t = 0; t < 4; t++) {
            pool.execute(new Runnable() {
                @Override public void run() {
                    int i;
                    while ((i = idx.getAndIncrement()) < chats.size()) {
                        if (!chats.get(i)[1].equals(chats.get(i)[0])) continue;
                        final String guid = chats.get(i)[0];
                        String name = RubikaClient.getObjectTitle(
                            RubikaLoginActivity.this, auth, pk, guid);
                        if (name != null && !name.isEmpty()) {
                            chats.get(i)[1] = name;
                            getPrefs().edit().putString("rubika_group_name_" + guid, name).apply();
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    if (monitorList.getAdapter() != null)
                                        ((BaseAdapter) monitorList.getAdapter()).notifyDataSetChanged();
                                    if (forwardList.getAdapter() != null)
                                        ((BaseAdapter) forwardList.getAdapter()).notifyDataSetChanged();
                                    if (saveList.getAdapter() != null)
                                        ((BaseAdapter) saveList.getAdapter()).notifyDataSetChanged();
                                }
                            });
                        }
                    }
                }
            });
        }
        pool.shutdown();
    }

    private void setupAdapters() {
        // Monitor list — checkboxes, default all checked
        monitorList.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return chats.size(); }
            @Override public Object getItem(int i) { return chats.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int pos, View v, ViewGroup parent) {
                CheckBox cb = (v instanceof CheckBox) ? (CheckBox) v : new CheckBox(RubikaLoginActivity.this);
                cb.setTextColor(Color.WHITE);
                cb.setTextSize(12);
                cb.setPadding(16, 10, 16, 10);
                String[] c = chats.get(pos);
                cb.setTag(c[0]);
                cb.setText(c[1].equals(c[0]) ? c[0] : c[1] + "\n" + c[0]);
                cb.setChecked(monitoredGuids.contains(c[0]));
                cb.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        String guid = (String) view.getTag();
                        if (((CheckBox) view).isChecked()) monitoredGuids.add(guid);
                        else monitoredGuids.remove(guid);
                    }
                });
                return cb;
            }
        });

        // Save list — radio buttons, single selection (same pattern as forward)
        saveList.setAdapter(buildRadioAdapter(new RadioSelectCallback() {
            @Override public String getSelected() { return saveGuid; }
            @Override public void setSelected(String guid) {
                saveGuid = guid;
                ((BaseAdapter) saveList.getAdapter()).notifyDataSetChanged();
            }
        }));

        // Forward list — radio buttons, single selection
        forwardList.setAdapter(buildRadioAdapter(new RadioSelectCallback() {
            @Override public String getSelected() { return forwardToGuid; }
            @Override public void setSelected(String guid) {
                forwardToGuid = guid;
                ((BaseAdapter) forwardList.getAdapter()).notifyDataSetChanged();
            }
        }));
    }

    interface RadioSelectCallback {
        String getSelected();
        void setSelected(String guid);
    }

    private BaseAdapter buildRadioAdapter(final RadioSelectCallback cb) {
        return new BaseAdapter() {
            @Override public int getCount() { return chats.size() + 1; }
            @Override public Object getItem(int i) { return i == 0 ? null : chats.get(i - 1); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int pos, View v, ViewGroup parent) {
                RadioButton rb = (v instanceof RadioButton) ? (RadioButton) v
                    : new RadioButton(RubikaLoginActivity.this);
                rb.setTextColor(Color.WHITE);
                rb.setTextSize(12);
                rb.setPadding(16, 10, 16, 10);
                if (pos == 0) {
                    rb.setTag("");
                    rb.setText("— غیرفعال");
                    rb.setChecked(cb.getSelected().isEmpty());
                } else {
                    String[] c = chats.get(pos - 1);
                    rb.setTag(c[0]);
                    rb.setText(c[1].equals(c[0]) ? c[0] : c[1] + "\n" + c[0]);
                    rb.setChecked(c[0].equals(cb.getSelected()));
                }
                rb.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        cb.setSelected((String) view.getTag());
                    }
                });
                return rb;
            }
        };
    }

    private void saveSelections() {
        StringBuilder sb = new StringBuilder();
        SharedPreferences.Editor ed = getPrefs().edit();
        for (String g : monitoredGuids) {
            if (sb.length() > 0) sb.append(",");
            sb.append(g);
            // Update name cache from resolved list
            for (String[] c : chats) {
                if (c[0].equals(g) && !c[1].equals(c[0])) {
                    ed.putString("rubika_group_name_" + g, c[1]);
                    break;
                }
            }
        }
        ed.putString("rubika_groups", sb.toString());
        ed.putString("rubika_forward_to", forwardToGuid);
        ed.putString("rubika_save_guid", saveGuid);
        ed.apply();
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
                Intent i = new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
                    Uri.parse("package:" + getPackageName()));
                startActivityForResult(i, 101);
            } catch (Exception e) {
                try {
                    startActivityForResult(
                        new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION"), 101);
                } catch (Exception e2) {
                    Toast.makeText(this, "لطفاً دسترسی فایل را در تنظیمات فعال کنید", Toast.LENGTH_LONG).show();
                }
            }
        } else {
            requestPermissions(
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                             Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == 100) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                saveSelections();
                startRubikaService();
                Toast.makeText(this, "سرویس روبیکا شروع شد", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "دسترسی به حافظه لازم است", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == 101) {
            if (hasStoragePermission()) {
                saveSelections();
                startRubikaService();
                Toast.makeText(this, "سرویس روبیکا شروع شد", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "دسترسی به حافظه لازم است", Toast.LENGTH_LONG).show();
            }
        }
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
        android.widget.ScrollView scrollV = new android.widget.ScrollView(this);
        scrollV.setPadding(16, 8, 16, 8);
        final TextView tv = new TextView(this);
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
