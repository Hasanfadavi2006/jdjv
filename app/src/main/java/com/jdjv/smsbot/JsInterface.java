package com.jdjv.smsbot;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.webkit.JavascriptInterface;

public class JsInterface {

    private final JobVisionActivity activity;

    public JsInterface(JobVisionActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void onDataExtracted(String json) {
        activity.onJsonReceived(json);
    }

    @JavascriptInterface
    public void onLog(String msg) {
        activity.appendLog(msg);
    }

    // لمس واقعی Android روی WebView — React نمی‌تونه تشخیص بده برنامه‌ایه
    @JavascriptInterface
    public void tapAt(final float x, final float y) {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                long now = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(
                    now, now, MotionEvent.ACTION_DOWN, x, y, 0);
                MotionEvent up = MotionEvent.obtain(
                    now, now + 80, MotionEvent.ACTION_UP, x, y, 0);
                activity.getWebView().dispatchTouchEvent(down);
                activity.getWebView().dispatchTouchEvent(up);
                down.recycle();
                up.recycle();
                activity.appendLog("tap واقعی: x=" + (int)x + " y=" + (int)y);
            }
        });
    }
}
