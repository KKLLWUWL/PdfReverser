package com.lingxi.pdfreverser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

/**
 * PDF 倒序处理引擎：隐藏 WebView 承载 pdf-lib，结构级页面倒序。
 * 单例复用同一个 WebView，避免重复加载库。
 * 仅在页面 >1 时输出倒序文件；单页原样返回。
 */
public class PdfEngine {

    public interface Callback {
        void onDone(byte[] outBytes, int pageCount);
        void onError(String message);
    }

    private static volatile PdfEngine instance;
    private final Context app;
    private WebView webView;
    private Callback current;
    private final Handler main = new Handler(Looper.getMainLooper());

    private PdfEngine(Context context) {
        app = context.getApplicationContext();
    }

    public static synchronized PdfEngine get(Context context) {
        if (instance == null) instance = new PdfEngine(context);
        return instance;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView webView() {
        if (webView == null) {
            webView = new WebView(app);
            webView.setLayoutParams(new FrameLayout.LayoutParams(0, 0));
            webView.setBackgroundColor(0);
            WebSettings s = webView.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setAllowFileAccess(true);
            webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
            webView.loadUrl("file:///android_asset/www/reverser.html");
        }
        return webView;
    }

    /**
     * 处理 PDF：倒序（>1 页时），完成后回调。必须从主线程调用。
     */
    public void process(final byte[] input, final Callback cb) {
        current = cb;
        try {
            String b64 = Base64.encodeToString(input, Base64.NO_WRAP);
            webView().evaluateJavascript("window.__reverseBase64('" + b64 + "');", null);
        } catch (Throwable t) {
            fail(t.getMessage());
        }
    }

    private void success(final byte[] out, final int pageCount) {
        main.post(new Runnable() {
            @Override public void run() {
                Callback c = current;
                if (c != null) { current = null; c.onDone(out, pageCount); }
            }
        });
    }

    private void fail(final String msg) {
        main.post(new Runnable() {
            @Override public void run() {
                Callback c = current;
                if (c != null) { current = null; c.onError(msg); }
            }
        });
    }

    class Bridge {
        @JavascriptInterface
        public void onResult(String err, String outB64, int pageCount) {
            if (err != null && err.length() > 0) { fail(err); return; }
            if (pageCount <= 1) { success(null, pageCount); return; } // 单页原样
            try {
                byte[] out = Base64.decode(outB64, Base64.DEFAULT);
                success(out, pageCount);
            } catch (Throwable t) {
                fail("解码失败：" + t.getMessage());
            }
        }
    }
}
