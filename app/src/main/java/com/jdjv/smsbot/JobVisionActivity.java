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

        // ردیف دکمه‌های مرورگر + کپی لاگ
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 4, 0, 0);
        btnRow.setLayoutParams(rowLp);

        toggleWebBtn = new Button(this);
        toggleWebBtn.setText("نمایش مرورگر");
        toggleWebBtn.setBackgroundColor(Color.parseColor("#444444"));
        toggleWebBtn.setTextColor(Color.WHITE);
        toggleWebBtn.setTextSize(12);
        LinearLayout.LayoutParams twLp = new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        twLp.setMargins(0, 0, 4, 0);
        toggleWebBtn.setLayoutParams(twLp);
        btnRow.addView(toggleWebBtn);

        final Button copyLogBtn = new Button(this);
        copyLogBtn.setText("کپی لاگ");
        copyLogBtn.setBackgroundColor(Color.parseColor("#1565C0"));
        copyLogBtn.setTextColor(Color.WHITE);
        copyLogBtn.setTextSize(12);
        LinearLayout.LayoutParams clLp = new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        copyLogBtn.setLayoutParams(clLp);
        btnRow.addView(copyLogBtn);
        root.addView(btnRow);

        // WebView - تمام صفحه وقتی نمایش داده می‌شه
        webView = new WebView(this);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0);
        webView.setLayoutParams(wlp);
        root.addView(webView);

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
                // وقتی مرورگر نمایش داده می‌شه تمام صفحه می‌شه
                LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    webViewVisible ? LinearLayout.LayoutParams.MATCH_PARENT : 0);
                webView.setLayoutParams(wp);
                LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    webViewVisible ? 0 : 0, webViewVisible ? 0f : 1f);
                logScroll.setLayoutParams(lp2);
                toggleWebBtn.setText(webViewVisible ? "مخفی کردن مرورگر" : "نمایش مرورگر");
            }
        });

        copyLogBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("log",
                    logTv.getText().toString()));
                Toast.makeText(JobVisionActivity.this, "لاگ کپی شد", Toast.LENGTH_SHORT).show();
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
                }, 4000);
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

    // ─── اسکن محتوای صفحه و لاگ کردن ─────────────────────────────────────────
    private void logPageContent() {
        String js =
            "(function() {" +
            "  var out = [];" +
            "  out.push('─── محتوای صفحه ───');" +
            "  out.push('عنوان: ' + document.title);" +
            "  out.push('URL: ' + location.href);" +
            // هدینگ‌ها
            "  var hs = document.querySelectorAll('h1,h2,h3');" +
            "  for(var i=0;i<Math.min(hs.length,5);i++) out.push('H: '+hs[i].innerText.trim().substring(0,60));" +
            // inputها
            "  var ins = document.querySelectorAll('input:not([type=hidden])');" +
            "  for(var i=0;i<ins.length;i++) out.push('INPUT['+i+']: type='+ins[i].type+' ph=\"'+ins[i].placeholder+'\" id='+ins[i].id);" +
            // دکمه‌ها
            "  var btns = document.querySelectorAll('button,input[type=submit],[role=button]');" +
            "  for(var i=0;i<Math.min(btns.length,8);i++) out.push('BTN['+i+']: \"'+btns[i].innerText.trim().substring(0,40)+'\" type='+btns[i].getAttribute('type'));" +
            // لینک‌های مهم
            "  var links = document.querySelectorAll('a[href]');" +
            "  var linkCount=0;" +
            "  for(var i=0;i<links.length && linkCount<5;i++){" +
            "    var h=links[i].href;" +
            "    if(h.indexOf('jobvision')!==-1){out.push('LINK: '+h.substring(0,80));linkCount++;}" +
            "  }" +
            "  out.push('─────────────────');" +
            "  Android.onLog(out.join('\\n'));" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    // ─── مدیریت صفحات ────────────────────────────────────────────────────────
    private void handlePage(String url) {
        logPageContent();
        if (url.contains("account.jobvision.ir") && state == STATE_LOGIN) {
            doLogin();

        } else if (state == STATE_LOGIN &&
                   (url.contains("employer.jobvision.ir") || url.startsWith("https://jobvision.ir"))) {
            state = STATE_DASHBOARD;
            log("لاگین موفق! رفتن به لیست آگهی‌ها...");
            handler.postDelayed(new Runnable() {
                @Override public void run() { webView.loadUrl(PANEL_URL + "/jobs"); }
            }, 1500);

        } else if (state == STATE_DASHBOARD && url.contains("employer.jobvision.ir/jobs")) {
            // صفحه لیست آگهی‌ها — کلیک روی اولین "مدیریت رزومه ها"
            state = STATE_APPLICANTS;
            handler.postDelayed(new Runnable() {
                @Override public void run() { clickFirstJobBtn(); }
            }, 2500);

        } else if (state == STATE_APPLICANTS && url.contains("employer.jobvision.ir")) {
            // صفحه متقاضیان یک آگهی مشخص
            log("صفحه متقاضیان: " + url);
            state = STATE_RESUME;
            handler.postDelayed(new Runnable() {
                @Override public void run() { extractApplicantLinks(); }
            }, 2500);

        } else if (state == STATE_RESUME && url.contains("employer.jobvision.ir") &&
                   !url.contains("/jobs") && !url.equals(PANEL_URL + "/")) {
            extractResume(url);
        }
    }

    // ─── کلیک روی اولین "مدیریت رزومه ها" ──────────────────────────────────
    private void clickFirstJobBtn() {
        log("کلیک روی مدیریت رزومه ها...");
        String js =
            "(function(){" +
            "  var all=document.querySelectorAll('*');" +
            "  for(var k=0;k<all.length;k++){" +
            "    var t=(all[k].innerText||'').trim();" +
            "    if(t==='مدیریت رزومه ها'){" +
            "      var r=all[k].getBoundingClientRect();" +
            "      if(r.width>10&&r.height>10){" +
            "        var dpr=window.devicePixelRatio||1;" +
            "        Android.onLog('tap مدیریت رزومه ها: ('+Math.round((r.left+r.width/2)*dpr)+','+Math.round((r.top+r.height/2)*dpr)+')');" +
            "        Android.tapAt((r.left+r.width/2)*dpr,(r.top+r.height/2)*dpr);" +
            "        return;" +
            "      }" +
            "    }" +
            "  }" +
            "  Android.onLog('دکمه مدیریت رزومه ها پیدا نشد');" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    // ─── لاگین کامل با MutationObserver ─────────────────────────────────────
    private void doLogin() {
        log("شروع اتوماسیون لاگین...");
        String js =
            "(function() {" +
            "  if(window._botRunning) return;" +
            "  window._botRunning = true;" +
            "  var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;" +

            // ─ تابع پر کردن input با React events ─
            "  function fillInput(el, val) {" +
            "    el.focus();" +
            "    setter.call(el, val);" +
            "    ['input','change','blur'].forEach(function(e){" +
            "      el.dispatchEvent(new Event(e,{bubbles:true,cancelable:true}));" +
            "    });" +
            "  }" +

            // ─ tap واقعی Android از طریق JavascriptInterface ─
            "  function tapElement(el) {" +
            "    el.scrollIntoView({block:'center'});" +
            "    setTimeout(function(){" +
            "      var rect = el.getBoundingClientRect();" +
            "      var dpr = window.devicePixelRatio || 1;" +
            "      var x = (rect.left + rect.width/2) * dpr;" +
            "      var y = (rect.top + rect.height/2) * dpr;" +
            "      Android.onLog('dpr='+dpr+' css=('+Math.round(rect.left+rect.width/2)+','+Math.round(rect.top+rect.height/2)+') px=('+Math.round(x)+','+Math.round(y)+')');" +
            "      Android.tapAt(x, y);" +
            "    }, 300);" +
            "  }" +

            // ─ پیدا کردن عنصر با متن و tap کردن ─
            "  function clickByText(texts) {" +
            "    var best = null;" +
            "    var all = document.querySelectorAll('button,[role=button],a,div,span');" +
            "    for(var k=0;k<all.length;k++){" +
            "      var t=(all[k].innerText||'').trim();" +
            "      for(var m=0;m<texts.length;m++){" +
            "        if(t===texts[m]){" +
            "          var r=all[k].getBoundingClientRect();" +
            "          if(r.width>0 && r.height>0){ best=all[k]; break; }" +
            "        }" +
            "      }" +
            "      if(best) break;" +
            "    }" +
            "    if(best){" +
            "      Android.onLog('tap: '+best.tagName+' \"'+(best.innerText||'').trim()+'\"');" +
            "      tapElement(best);" +
            "      return true;" +
            "    }" +
            "    return false;" +
            "  }" +

            // ─ مرحله ۱: پر کردن ایمیل ─
            "  function step1() {" +
            "    var inputs = document.querySelectorAll('input:not([type=hidden]):not([type=checkbox])');" +
            "    Android.onLog('step1: inputs=' + inputs.length);" +
            "    if(inputs.length === 0){ setTimeout(step1, 1500); return; }" +
            "    fillInput(inputs[0], '" + EMAIL + "');" +
            "    Android.onLog('ایمیل وارد شد');" +
            // بعد از کلیک ادامه، منتظر بشو تا فیلد پسورد ظاهر بشه
            "    setTimeout(function(){" +
            "      clickByText(['ادامه','Continue','Next','بعدی']);" +
            "      watchForPassword();" +
            "    }, 1500);" +
            "  }" +

            // ─ مرحله ۲: منتظر ظاهر شدن پسورد (MutationObserver) ─
            "  function watchForPassword() {" +
            "    Android.onLog('منتظر فیلد پسورد...');" +
            "    var tries = 0;" +
            "    var interval = setInterval(function(){" +
            "      tries++;" +
            "      var passEl = document.querySelector('input[type=password]');" +
            "      if(passEl){" +
            "        clearInterval(interval);" +
            "        Android.onLog('فیلد پسورد ظاهر شد');" +
            "        step2(passEl);" +
            "      } else if(tries === 6){" +
            "        Android.onLog('تلاش مجدد کلیک ادامه...');" +
            "        clickByText(['ادامه','Continue','Next','بعدی']);" +
            "      } else if(tries > 30){" +
            "        clearInterval(interval);" +
            "        Android.onLog('پسورد ظاهر نشد - URL: '+location.href);" +
            "      }" +
            "    }, 500);" +  // هر ۵۰۰ms چک کن
            "  }" +

            // ─ مرحله ۲: پر کردن پسورد ─
            "  function step2(passEl) {" +
            "    fillInput(passEl, '" + PASSWORD + "');" +
            "    Android.onLog('پسورد وارد شد');" +
            "    setTimeout(function(){" +
            "      if(!clickByText(['ورود','Login','Sign in','تایید','وارد شوید'])){" +
            "        var b=document.querySelector('button,input[type=submit]');" +
            "        if(b){tapElement(b);Android.onLog('tap fallback: '+b.innerText);}" +
            "        else Android.onLog('دکمه ورود پیدا نشد');" +
            "      }" +
            "    }, 1000);" +
            "  }" +

            "  step1();" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    // این متد دیگه استفاده نمی‌شه — لاگین مستقیم /jobs رو لود می‌کنه
    private void goToApplicants() { webView.loadUrl(PANEL_URL + "/jobs"); }

    // ─── استخراج لینک آگهی‌ها از صفحه /jobs ──────────────────────────────────
    private void extractApplicantLinks() {
        log("جمع‌آوری لینک آگهی‌ها از: " + webView.getUrl());
        String js =
            "(function() {" +
            "  var urls = [];" +
            "  var all = document.querySelectorAll('a[href]');" +
            "  for (var i = 0; i < all.length; i++) {" +
            "    var h = all[i].href + '';" +
            "    if (h.indexOf('employer.jobvision.ir/jobs/') !== -1" +
            "     || h.indexOf('employer.jobvision.ir/applicant') !== -1" +
            "     || h.indexOf('employer.jobvision.ir/resume') !== -1) {" +
            "      if (urls.indexOf(h) === -1) urls.push(h);" +
            "    }" +
            "  }" +
            // data-id
            "  var btns = document.querySelectorAll('[data-id],[data-job-id],[data-resume-id]');" +
            "  for (var j = 0; j < btns.length; j++) {" +
            "    var id = btns[j].getAttribute('data-id') || btns[j].getAttribute('data-job-id');" +
            "    if (id) {" +
            "      var u = 'https://employer.jobvision.ir/jobs/' + id;" +
            "      if (urls.indexOf(u) === -1) urls.push(u);" +
            "    }" +
            "  }" +
            "  Android.onLog('لینک‌های آگهی: ' + urls.length + ' | ' + urls.slice(0,5).join(' | '));" +
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

    public WebView getWebView() { return webView; }

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
