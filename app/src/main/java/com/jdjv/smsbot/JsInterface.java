package com.jdjv.smsbot;

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
}
