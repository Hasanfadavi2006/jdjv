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
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class JobVisionActivity extends Activity {

    private static final String EMAIL    = "emtcompanyiran@gmail.com";
    private static final String PASSWORD = "@Mahdi1365";

    private static final String LOGIN_URL = "https://account.jobvision.ir/Employer";
    private static final String PANEL_URL = "https://employer.jobvision.ir";

    private static final int STATE_IDLE       = 0;
    private static final int STATE_LOGIN      = 1;
    private static final int STATE_DASHBOARD  = 2;
    private static final int STATE_APPLICANTS = 3;
    private static final int STATE_RESUME     = 4;
    private static final int STATE_SEND_SMS   = 5;

    private int state = STATE_IDLE;
    private int currentIndex = 0;

    private WebView webView;
    private TextView logTv;
    private Button startBtn, toggleWebBtn;
    private ScrollView logScroll;
    private EditText phoneEdit;
    private boolean webViewVisible = false;
    private String companyPhone = "";

    private final List<Applicant> applicants = new ArrayList<Applicant>();
    private final List<String> resumeUrls   = new ArrayList<String>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashHandler.install(this);
        try {
            buildUI();
            setupWebView();
        } catch (Throwable t) {
            // لاگ دستی اگه حتی buildUI کرش کرد
            CrashHandler.install(this);
            throw new RuntimeException("onCreate crash: " + t.getMessage(), t);
        }
    }

    // ─── UI ─────────────────────────────────────────────────────────────────
    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0d1117"));
        root.setPadding(20, 20, 20, 20);

        TextView title = new TextView(this);
        title.setText("JobVision Bot");
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        phoneEdit = new EditText(this);
        phoneEdit.setHint("شماره تماس شرکت برای SMS (مثلاً 02112345678)");
        phoneEdit.setHintTextColor(Color.GRAY);
        phoneEdit.setTextColor(Color.WHITE);
        phoneEdit.setBackgroundColor(Color.parseColor("#161b22"));
        phoneEdit.setPadding(16, 16, 16, 16);
        lp(phoneEdit, 0);
        root.addView(phoneEdit);

        startBtn = new Button(this);
        startBtn.setText("شروع اسکن و ارسال SMS");
        startBtn.setBackgroundColor(Color.parseColor("#238636"));
        startBtn.setTextColor(Color.WHITE);
        lp(startBtn, 8);
        root.addView(startBtn);

        toggleWebBtn = new Button(this);
        toggleWebBtn.setText("نمایش مرورگر");
        toggleWebBtn.setBackgroundColor(Color.parseColor("#444"));
        toggleWebBtn.setTextColor(Color.WHITE);
        lp(toggleWebBtn, 4);
        root.addView(toggleWebBtn);

        logScroll = new ScrollView(this);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        slp.setMargins(0, 8, 0, 0);
        logScroll.setLayoutParams(slp);
        logTv = new TextView(this);
        logTv.setTextColor(Color.parseColor("#58a6ff"));
        logTv.setTextSize(11);
        logTv.setPadding(8, 8, 8, 8);
        logTv.setBackgroundColor(Color.parseColor("#161b22"));
        logScroll.addView(logTv);
        root.addView(logScroll);

        // WebView با ارتفاع صفر (مخفی) - با دکمه قابل نمایشه
        webView = new WebView(this);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0);
        webView.setLayoutParams(wlp);
        root.addView(webView);

        setContentView(root);

        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                companyPhone = phoneEdit.getText().toString().trim();
                if (companyPhone.isEmpty()) {
                    Toast.makeText(JobVisionActivity.this,
                        "لطفاً شماره تماس شرکت رو وارد کن", Toast.LENGTH_SHORT).show();
                    return;
                }
                startScan();
            }
        });

        toggleWebBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                webViewVisible = !webViewVisible;
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    webViewVisible ? 600 : 0);
                webView.setLayoutParams(p);
                toggleWebBtn.setText(webViewVisible ? "مخفی کردن مرورگر" : "نمایش مرورگر");
            }
        });
    }

    private void lp(View v, int topMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, topMargin, 0, 0);
        v.setLayoutParams(p);
    }

    // ─── WebView ─────────────────────────────────────────────────────────────
    private void setupWebView() {
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setJavaScriptCanOpenWindowsAutomatically(true);
        ws.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 12; SM-F936B) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/112.0.0.0 Mobile Safari/537.36");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new JsInterface(this), "Android");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, final String url) {
                log("صفحه: " + url);
                handler.postDelayed(new Runnable() {
                    @Override public void run() { handlePage(url); }
                }, 2500);
            }
        });
    }

    // ─── شروع ───────────────────────────────────────────────────────────────
    private void startScan() {
        applicants.clear();
        resumeUrls.clear();
        currentIndex = 0;
        startBtn.setEnabled(false);
        logTv.setText("");
        state = STATE_LOGIN;
        log("در حال باز کردن صفحه لاگین...");
        webView.loadUrl(LOGIN_URL);
    }

    // ─── مدیریت صفحات ────────────────────────────────────────────────────────
    private void handlePage(String url) {
        if (url.contains("account.jobvision.ir") && state == STATE_LOGIN) {
            doLogin();

        } else if (url.contains("employer.jobvision.ir") && state == STATE_LOGIN) {
            // لاگین موفق - رفتیم به پنل
            state = STATE_DASHBOARD;
            log("لاگین موفق! در حال رفتن به لیست درخواست‌ها...");
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    goToApplicants();
                }
            }, 1500);

        } else if (state == STATE_DASHBOARD && url.contains("employer.jobvision.ir")) {
            goToApplicants();

        } else if (state == STATE_APPLICANTS) {
            extractApplicantLinks();

        } else if (state == STATE_RESUME) {
            extractResume(url);
        }
    }

    // ─── لاگین ───────────────────────────────────────────────────────────────
    private void doLogin() {
        log("در حال پر کردن فرم لاگین...");
        // تلاش با چند سلکتور مختلف برای سازگاری با سایت
        String js =
            "(function() {" +
            "  var filled = false;" +
            "  var selectors = [" +
            "    {e: 'input[type=email]', p: 'input[type=password]'}," +
            "    {e: 'input[name=Email]', p: 'input[name=Password]'}," +
            "    {e: 'input[id*=email]', p: 'input[id*=password]'}," +
            "    {e: 'input[id*=Email]', p: 'input[id*=Password]'}," +
            "    {e: 'input[placeholder*=ایمیل]', p: 'input[placeholder*=رمز]'}," +
            "    {e: 'input[placeholder*=email]', p: 'input[type=password]'}" +
            "  ];" +
            "  for (var i = 0; i < selectors.length; i++) {" +
            "    var eEl = document.querySelector(selectors[i].e);" +
            "    var pEl = document.querySelector(selectors[i].p);" +
            "    if (eEl && pEl) {" +
            "      var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;" +
            "      setter.call(eEl, '" + EMAIL + "'); eEl.dispatchEvent(new Event('input',{bubbles:true}));" +
            "      setter.call(pEl, '" + PASSWORD + "'); pEl.dispatchEvent(new Event('input',{bubbles:true}));" +
            "      Android.onLog('فیلدها پر شدند (selector ' + i + ')');" +
            "      filled = true; break;" +
            "    }" +
            "  }" +
            "  if (!filled) {" +
            "    Android.onLog('فرم پیدا نشد - inputs روی صفحه: ' + document.querySelectorAll('input').length);" +
            "    return;" +
            "  }" +
            "  setTimeout(function() {" +
            "    var btn = document.querySelector('button[type=submit]') ||" +
            "              document.querySelector('input[type=submit]') ||" +
            "              document.querySelector('button.btn-primary') ||" +
            "              document.querySelector('button.login') ||" +
            "              document.querySelector('button:last-of-type');" +
            "    if (btn) { btn.click(); Android.onLog('دکمه submit کلیک شد'); }" +
            "    else { Android.onLog('دکمه submit پیدا نشد'); }" +
            "  }, 1000);" +
            "})();";
        webView.evaluateJavascript(js, null);
        state = STATE_DASHBOARD; // منتظر redirect
    }

    // ─── رفتن به لیست درخواست‌ها ─────────────────────────────────────────────
    private void goToApplicants() {
        state = STATE_APPLICANTS;
        // URL های احتمالی پنل کارفرما در jobvision
        log("رفتن به صفحه درخواست‌ها...");
        webView.loadUrl(PANEL_URL + "/applicants");
    }

    // ─── استخراج لینک رزومه‌ها ──────────────────────────────────────────────
    private void extractApplicantLinks() {
        log("در حال جمع‌آوری لینک رزومه‌ها...");
        String js =
            "(function() {" +
            "  var urls = [];" +
            "  var all = document.querySelectorAll('a[href]');" +
            "  for (var i = 0; i < all.length; i++) {" +
            "    var h = all[i].href + '';" +
            "    if (h.indexOf('resume') !== -1 || h.indexOf('applicant') !== -1" +
            "     || h.indexOf('karjoo') !== -1 || h.indexOf('candidate') !== -1) {" +
            "      if (urls.indexOf(h) === -1) urls.push(h);" +
            "    }" +
            "  }" +
            // دکمه‌های «مشاهده رزومه» که data-id دارند
            "  var btns = document.querySelectorAll('[data-id],[data-resume-id],[data-user-id]');" +
            "  for (var j = 0; j < btns.length; j++) {" +
            "    var id = btns[j].getAttribute('data-id') || btns[j].getAttribute('data-resume-id') || btns[j].getAttribute('data-user-id');" +
            "    var u = '" + PANEL_URL + "/resume/' + id;" +
            "    if (id && urls.indexOf(u) === -1) urls.push(u);" +
            "  }" +
            "  Android.onLog('لینک رزومه پیدا شد: ' + urls.length);" +
            "  Android.onDataExtracted(JSON.stringify({type:'resume_urls', data:urls}));" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    // ─── استخراج اطلاعات رزومه ──────────────────────────────────────────────
    private void extractResume(final String url) {
        log("خواندن رزومه " + (currentIndex + 1) + " از " + resumeUrls.size() + "...");
        String js =
            "(function() {" +
            "  var txt = document.body.innerText || '';" +
            "  var html = document.body.innerHTML || '';" +
            // شماره موبایل
            "  var m = txt.match(/09[0-9]{9}/) || html.match(/09[0-9]{9}/);" +
            "  var phone = m ? m[0] : '';" +
            // اسم کامل
            "  var nameEl = document.querySelector('h1,h2,.fullname,.name,[class*=name],[class*=Name],.candidate-name,.karjoo-name,.resume-header h2');" +
            "  var name = nameEl ? nameEl.innerText.trim() : '';" +
            "  if (!name) {" +
            "    var titleParts = document.title.split('|');" +
            "    name = titleParts[0].trim();" +
            "  }" +
            // جنسیت
            "  var g = 'unknown';" +
            "  if (/خانم|زن|female|دختر/i.test(txt)) g = 'female';" +
            "  else if (/آقا|مرد|male|پسر/i.test(txt)) g = 'male';" +
            // جنسیت از آیکون یا فیلد مشخص
            "  var gEl = document.querySelector('[class*=gender],[data-gender]');" +
            "  if (gEl) {" +
            "    var gTxt = (gEl.innerText + gEl.getAttribute('data-gender')).toLowerCase();" +
            "    if (/female|زن|خانم/.test(gTxt)) g = 'female';" +
            "    else if (/male|مرد|آقا/.test(gTxt)) g = 'male';" +
            "  }" +
            // عنوان شغلی (آخرین سابقه کاری)
            "  var jobEl = document.querySelector('.job-title,.position,[class*=position],[class*=job],[class*=Job]');" +
            "  var job = jobEl ? jobEl.innerText.trim() : 'موقعیت شغلی';" +
            "  Android.onDataExtracted(JSON.stringify({" +
            "    type:'resume', name:name, phone:phone, gender:g, job:job, url:'" + url + "'" +
            "  }));" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    // ─── دریافت داده از JS ──────────────────────────────────────────────────
    public void onJsonReceived(final String raw) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject obj = new JSONObject(raw);
                    String type = obj.getString("type");
                    if ("resume_urls".equals(type)) {
                        handleResumeUrls(obj.getJSONArray("data"));
                    } else if ("resume".equals(type)) {
                        handleResume(obj);
                    }
                } catch (Exception e) {
                    log("خطا JSON: " + e.getMessage());
                }
            }
        });
    }

    private void handleResumeUrls(JSONArray arr) throws Exception {
        resumeUrls.clear();
        for (int i = 0; i < arr.length(); i++) resumeUrls.add(arr.getString(i));
        log("تعداد رزومه: " + resumeUrls.size());
        if (resumeUrls.isEmpty()) {
            log("رزومه‌ای پیدا نشد. اپ رو با مرورگر نمایش بده و بررسی کن.");
            startBtn.setEnabled(true);
            return;
        }
        state = STATE_RESUME;
        currentIndex = 0;
        loadNextResume();
    }

    private void handleResume(JSONObject obj) throws Exception {
        String name  = obj.optString("name", "کاربر");
        String phone = obj.optString("phone", "");
        String gender = obj.optString("gender", "unknown");
        String job    = obj.optString("job", "موقعیت شغلی");

        if (name.isEmpty()) name = "کاربر";
        Applicant a = new Applicant(name, phone, gender, job,
            currentIndex < resumeUrls.size() ? resumeUrls.get(currentIndex) : "");
        applicants.add(a);

        String info = phone.isEmpty() ? "(شماره ندارد)" : phone;
        log((currentIndex + 1) + ". " + name + " | " + info + " | " + gender);

        currentIndex++;
        if (currentIndex < resumeUrls.size()) {
            handler.postDelayed(new Runnable() {
                @Override public void run() { loadNextResume(); }
            }, 2000);
        } else {
            state = STATE_SEND_SMS;
            askBeforeSend();
        }
    }

    private void loadNextResume() {
        if (currentIndex < resumeUrls.size()) {
            webView.loadUrl(resumeUrls.get(currentIndex));
        }
    }

    // ─── تایید ارسال ─────────────────────────────────────────────────────────
    private void askBeforeSend() {
        int cnt = 0;
        for (Applicant a : applicants) if (!a.phone.isEmpty()) cnt++;
        final int total = cnt;
        StringBuilder preview = new StringBuilder();
        int shown = 0;
        for (Applicant a : applicants) {
            if (!a.phone.isEmpty() && shown < 3) {
                preview.append("• ").append(a.name).append(" → ").append(a.phone).append("\n");
                shown++;
            }
        }
        new AlertDialog.Builder(this)
            .setTitle("ارسال SMS به " + total + " نفر")
            .setMessage(preview.toString() + "\nآیا ارسال شود؟")
            .setPositiveButton("بله", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) { sendAll(); }
            })
            .setNegativeButton("لغو", null)
            .show();
    }

    // ─── ارسال SMS ──────────────────────────────────────────────────────────
    private void sendAll() {
        log("\n=== شروع ارسال ===");
        final SmsManager sm = SmsManager.getDefault();
        int delay = 0;
        for (final Applicant a : applicants) {
            if (a.phone.isEmpty()) continue;
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    try {
                        String txt = a.buildSms(companyPhone);
                        sm.sendTextMessage(a.phone, null, txt, null, null);
                        a.smsSent = true;
                        log("✓ " + a.phone + " (" + a.name + ")");
                    } catch (Exception e) {
                        log("✗ " + a.phone + ": " + e.getMessage());
                    }
                }
            }, delay);
            delay += 3000;
        }
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                int s = 0; for (Applicant a : applicants) if (a.smsSent) s++;
                log("=== تمام شد: " + s + " SMS ارسال شد ===");
                startBtn.setEnabled(true);
            }
        }, delay + 500);
    }

    // ─── لاگ ─────────────────────────────────────────────────────────────────
    public void appendLog(final String msg) { log(msg); }

    private void log(final String msg) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                logTv.append(msg + "\n");
                logScroll.post(new Runnable() {
                    @Override public void run() { logScroll.fullScroll(ScrollView.FOCUS_DOWN); }
                });
            }
        });
    }
}
