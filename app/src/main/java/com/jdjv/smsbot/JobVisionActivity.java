package com.jdjv.smsbot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JobVisionActivity extends Activity {

    private static final String EMAIL    = "emtcompanyiran@gmail.com";
    private static final String PASSWORD = "@Mahdi1365";
    private static final String BASE_URL = "https://jobvision.ir";

    // مرحله‌های اتوماسیون
    private static final int STATE_IDLE         = 0;
    private static final int STATE_LOGIN        = 1;
    private static final int STATE_JOBS_LIST    = 2;
    private static final int STATE_APPLICANTS   = 3;
    private static final int STATE_RESUME       = 4;
    private static final int STATE_SEND_SMS     = 5;
    private static final int STATE_DONE         = 6;

    private int state = STATE_IDLE;
    private int currentApplicantIndex = 0;
    private int currentJobIndex = 0;

    private WebView webView;
    private TextView logTv;
    private Button startBtn;
    private ScrollView scrollView;
    private String companyPhone = "02100000000";

    private final List<Applicant> applicants = new ArrayList<Applicant>();
    private final List<String> jobUrls = new ArrayList<String>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0d1117"));
        root.setPadding(24, 24, 24, 24);

        TextView title = new TextView(this);
        title.setText("JobVision Bot");
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        // فیلد شماره تماس شرکت
        final EditText phoneEdit = new EditText(this);
        phoneEdit.setHint("شماره تماس شرکت (برای درج در SMS)");
        phoneEdit.setHintTextColor(Color.GRAY);
        phoneEdit.setTextColor(Color.WHITE);
        phoneEdit.setBackgroundColor(Color.parseColor("#161b22"));
        phoneEdit.setPadding(16, 16, 16, 16);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ep.setMargins(0, 16, 0, 8);
        phoneEdit.setLayoutParams(ep);
        root.addView(phoneEdit);

        startBtn = new Button(this);
        startBtn.setText("شروع اسکن و ارسال SMS");
        startBtn.setBackgroundColor(Color.parseColor("#238636"));
        startBtn.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.setMargins(0, 0, 0, 16);
        startBtn.setLayoutParams(bp);
        root.addView(startBtn);

        scrollView = new ScrollView(this);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollView.setLayoutParams(sp);
        logTv = new TextView(this);
        logTv.setTextColor(Color.parseColor("#58a6ff"));
        logTv.setTextSize(12);
        logTv.setPadding(8, 8, 8, 8);
        logTv.setBackgroundColor(Color.parseColor("#161b22"));
        scrollView.addView(logTv);
        root.addView(scrollView);

        // WebView مخفی
        webView = new WebView(this);
        webView.setVisibility(View.GONE);
        root.addView(webView);

        setContentView(root);
        setupWebView();

        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String ph = phoneEdit.getText().toString().trim();
                if (!ph.isEmpty()) companyPhone = ph;
                startScan();
            }
        });
    }

    private void setupWebView() {
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 12; SM-F936B) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36");

        webView.addJavascriptInterface(new JsInterface(this), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                appendLog("صفحه بارگذاری شد: " + url);
                handler.postDelayed(new Runnable() {
                    @Override public void run() { handlePage(url); }
                }, 2000); // کمی صبر تا JS اجرا بشه
            }
        });
    }

    private void startScan() {
        applicants.clear();
        jobUrls.clear();
        currentApplicantIndex = 0;
        currentJobIndex = 0;
        startBtn.setEnabled(false);
        logTv.setText("");
        state = STATE_LOGIN;
        appendLog("در حال باز کردن JobVision...");
        webView.loadUrl(BASE_URL + "/employer/login");
    }

    private void handlePage(String url) {
        if (state == STATE_LOGIN) {
            doLogin();
        } else if (state == STATE_JOBS_LIST) {
            if (url.contains("/employer") && !url.contains("/login")) {
                extractJobUrls();
            }
        } else if (state == STATE_APPLICANTS) {
            extractApplicants();
        } else if (state == STATE_RESUME) {
            extractResumeData(url);
        }
    }

    // ─── لاگین ─────────────────────────────────────────────────────────────
    private void doLogin() {
        appendLog("در حال لاگین...");
        String js =
            "(function() {" +
            "  var emailInputs = document.querySelectorAll('input[type=email], input[name=email], input[id*=email]');" +
            "  var passInputs  = document.querySelectorAll('input[type=password]');" +
            "  if (emailInputs.length === 0 || passInputs.length === 0) {" +
            "    Android.onLog('فرم لاگین پیدا نشد');" +
            "    return;" +
            "  }" +
            "  var emailEl = emailInputs[0]; var passEl = passInputs[0];" +
            "  var nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;" +
            "  nativeInputValueSetter.call(emailEl, '" + EMAIL + "');" +
            "  emailEl.dispatchEvent(new Event('input', {bubbles:true}));" +
            "  nativeInputValueSetter.call(passEl, '" + PASSWORD + "');" +
            "  passEl.dispatchEvent(new Event('input', {bubbles:true}));" +
            "  Android.onLog('فیلدها پر شدند');" +
            "  setTimeout(function() {" +
            "    var btn = document.querySelector('button[type=submit], input[type=submit], button.login-btn, button.submit');" +
            "    if (btn) { btn.click(); Android.onLog('دکمه کلیک شد'); }" +
            "    else { Android.onLog('دکمه submit پیدا نشد'); }" +
            "  }, 800);" +
            "})();";
        webView.evaluateJavascript(js, null);
        state = STATE_JOBS_LIST;
    }

    // ─── لیست آگهی‌ها ───────────────────────────────────────────────────────
    private void extractJobUrls() {
        appendLog("در حال جمع‌آوری آگهی‌ها...");
        String js =
            "(function() {" +
            "  var links = document.querySelectorAll('a[href]');" +
            "  var jobs = [];" +
            "  for (var i=0; i<links.length; i++) {" +
            "    var h = links[i].href;" +
            "    if (h.indexOf('/employer/job') !== -1 || h.indexOf('/employer/ad') !== -1" +
            "        || h.indexOf('applicants') !== -1 || h.indexOf('resume') !== -1) {" +
            "      jobs.push(h);" +
            "    }" +
            "  }" +
            "  Android.onDataExtracted(JSON.stringify({type:'jobs', data:jobs}));" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    // ─── لیست کارجویان برای یه آگهی ────────────────────────────────────────
    private void extractApplicants() {
        appendLog("در حال جمع‌آوری کارجویان...");
        String js =
            "(function() {" +
            "  var items = document.querySelectorAll('a[href*=resume], a[href*=applicant], a[href*=karjoo], .resume-item a, .applicant-row a');" +
            "  var urls = [];" +
            "  for (var i=0; i<items.length; i++) urls.push(items[i].href);" +
            // اگه لینک مستقیم نبود، هر کارت رو هم چک کن
            "  if (urls.length === 0) {" +
            "    var cards = document.querySelectorAll('[data-id], [data-user-id]');" +
            "    for (var j=0; j<cards.length; j++) {" +
            "      var id = cards[j].getAttribute('data-id') || cards[j].getAttribute('data-user-id');" +
            "      if (id) urls.push('" + BASE_URL + "/employer/resume/' + id);" +
            "    }" +
            "  }" +
            "  Android.onDataExtracted(JSON.stringify({type:'applicant_urls', data:urls}));" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    // ─── استخراج اطلاعات از رزومه ──────────────────────────────────────────
    private void extractResumeData(final String url) {
        appendLog("در حال خواندن رزومه...");
        String js =
            "(function() {" +
            "  var body = document.body.innerText + ' ' + document.body.innerHTML;" +

            // شماره موبایل
            "  var phoneMatch = body.match(/09[0-9]{9}/);" +
            "  var phone = phoneMatch ? phoneMatch[0] : '';" +

            // اسم
            "  var nameEl = document.querySelector('h1, h2, .name, .fullname, [class*=name], .resume-name, .karjoo-name');" +
            "  var name = nameEl ? nameEl.innerText.trim() : '';" +

            // جنسیت
            "  var genderText = body.toLowerCase();" +
            "  var gender = 'unknown';" +
            "  if (genderText.indexOf('خانم') !== -1 || genderText.indexOf('زن') !== -1 || genderText.indexOf('female') !== -1) gender = 'female';" +
            "  else if (genderText.indexOf('آقا') !== -1 || genderText.indexOf('مرد') !== -1 || genderText.indexOf('male') !== -1) gender = 'male';" +

            // عنوان شغلی (از تیتل صفحه یا متن)
            "  var jobTitle = document.title || '';" +
            "  var jobEl = document.querySelector('.job-title, .position, [class*=position], [class*=job-name]');" +
            "  if (jobEl) jobTitle = jobEl.innerText.trim();" +

            "  Android.onDataExtracted(JSON.stringify({" +
            "    type:'resume'," +
            "    name: name," +
            "    phone: phone," +
            "    gender: gender," +
            "    jobTitle: jobTitle," +
            "    url: '" + url + "'" +
            "  }));" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    // ─── دریافت داده از JS ──────────────────────────────────────────────────
    public void onJsonReceived(final String json) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject obj = new JSONObject(json);
                    String type = obj.getString("type");

                    if ("jobs".equals(type)) {
                        handleJobsList(obj.getJSONArray("data"));
                    } else if ("applicant_urls".equals(type)) {
                        handleApplicantUrls(obj.getJSONArray("data"));
                    } else if ("resume".equals(type)) {
                        handleResume(obj);
                    }
                } catch (Exception e) {
                    appendLog("خطا در پردازش JSON: " + e.getMessage());
                }
            }
        });
    }

    private void handleJobsList(JSONArray jobs) throws Exception {
        appendLog("تعداد آگهی پیدا شد: " + jobs.length());
        jobUrls.clear();
        for (int i = 0; i < jobs.length(); i++) {
            String u = jobs.getString(i);
            if (!jobUrls.contains(u)) jobUrls.add(u);
        }
        if (jobUrls.isEmpty()) {
            // سعی کن مستقیم به صفحه درخواست‌ها بری
            appendLog("دسترسی مستقیم به درخواست‌ها...");
            state = STATE_APPLICANTS;
            webView.loadUrl(BASE_URL + "/employer/applicants");
        } else {
            state = STATE_APPLICANTS;
            webView.loadUrl(jobUrls.get(0) + "/applicants");
        }
    }

    private void handleApplicantUrls(JSONArray urls) throws Exception {
        appendLog("تعداد رزومه پیدا شد: " + urls.length());
        List<String> resumeUrls = new ArrayList<String>();
        for (int i = 0; i < urls.length(); i++) {
            String u = urls.getString(i);
            if (!resumeUrls.contains(u)) resumeUrls.add(u);
        }
        if (resumeUrls.isEmpty()) {
            appendLog("رزومه‌ای پیدا نشد.");
            state = STATE_DONE;
            onDone();
            return;
        }
        // ذخیره URL ها به عنوان کارجویان موقت و شروع خواندن
        for (String u : resumeUrls) {
            applicants.add(new Applicant("", "", "unknown", "موقعیت شغلی", u));
        }
        state = STATE_RESUME;
        currentApplicantIndex = 0;
        loadNextResume();
    }

    private void handleResume(JSONObject obj) throws Exception {
        String name  = obj.optString("name", "");
        String phone = obj.optString("phone", "");
        String gender = obj.optString("gender", "unknown");
        String jobTitle = obj.optString("jobTitle", "موقعیت شغلی");

        if (currentApplicantIndex < applicants.size()) {
            Applicant a = applicants.get(currentApplicantIndex);
            a.name = name.isEmpty() ? "کاربر" : name;
            a.phone = phone;
            a.gender = gender;
            a.jobTitle = jobTitle;

            if (!phone.isEmpty()) {
                appendLog("رزومه: " + a.name + " | " + phone + " | " + gender);
            } else {
                appendLog("شماره پیدا نشد برای: " + a.name);
            }
        }

        currentApplicantIndex++;
        if (currentApplicantIndex < applicants.size()) {
            handler.postDelayed(new Runnable() {
                @Override public void run() { loadNextResume(); }
            }, 1500);
        } else {
            state = STATE_SEND_SMS;
            askBeforeSend();
        }
    }

    private void loadNextResume() {
        if (currentApplicantIndex < applicants.size()) {
            appendLog("باز کردن رزومه " + (currentApplicantIndex + 1) + " از " + applicants.size());
            webView.loadUrl(applicants.get(currentApplicantIndex).profileUrl);
        }
    }

    // ─── تایید قبل از ارسال ─────────────────────────────────────────────────
    private void askBeforeSend() {
        int withPhone = 0;
        for (Applicant a : applicants) if (!a.phone.isEmpty()) withPhone++;

        final int count = withPhone;
        new AlertDialog.Builder(this)
            .setTitle("ارسال SMS")
            .setMessage("تعداد " + count + " نفر شماره دارند.\nآیا اس‌ام‌اس ارسال شود؟")
            .setPositiveButton("بله، ارسال کن", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    sendSmsToAll();
                }
            })
            .setNegativeButton("لغو", null)
            .show();
    }

    // ─── ارسال SMS ──────────────────────────────────────────────────────────
    private void sendSmsToAll() {
        appendLog("\n=== شروع ارسال SMS ===");
        final SmsManager sm = SmsManager.getDefault();
        int delay = 0;

        for (final Applicant a : applicants) {
            if (a.phone.isEmpty()) continue;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        String text = a.buildSms(companyPhone);
                        sm.sendTextMessage(a.phone, null, text, null, null);
                        a.smsSent = true;
                        appendLog("SMS ارسال شد → " + a.phone + " (" + a.name + ")");
                    } catch (Exception e) {
                        appendLog("خطا در ارسال به " + a.phone + ": " + e.getMessage());
                    }
                }
            }, delay);
            delay += 3000; // ۳ ثانیه بین هر SMS
        }

        handler.postDelayed(new Runnable() {
            @Override public void run() { onDone(); }
        }, delay + 1000);
    }

    private void onDone() {
        int sent = 0;
        for (Applicant a : applicants) if (a.smsSent) sent++;
        appendLog("\n=== تمام شد ===");
        appendLog("SMS ارسال شده: " + sent + " نفر");
        appendLog("شماره پیدا نشد: " + (applicants.size() - sent) + " نفر");
        startBtn.setEnabled(true);
    }

    // ─── لاگ ────────────────────────────────────────────────────────────────
    public void appendLog(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logTv.append("\n" + msg);
                scrollView.post(new Runnable() {
                    @Override public void run() {
                        scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                });
            }
        });
    }
}
