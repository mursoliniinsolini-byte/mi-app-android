package com.eggrun.game;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class MainActivity extends Activity {
    private WebView web;
    private AdsManager ads;

    /** Puente de pagos: aquí se conecta Google Play Billing. Mientras available() sea false, el juego usa su modo de prueba. */
    public class PayBridge {
        @android.webkit.JavascriptInterface public boolean available() { return false; }
        @android.webkit.JavascriptInterface public void buy(String productId) { /* TODO: BillingClient.launchBillingFlow y luego web.evaluateJavascript("eggPayResult('"+productId+"',true)", null) */ }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (android.os.Build.VERSION.SDK_INT >= 28) { getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES; }
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0d0a1f"));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        web.addJavascriptInterface(new PayBridge(), "EggPay");
        ads = new AdsManager(this, web);
        web.addJavascriptInterface(new EggRunAdsBridge(this, ads), "AndroidAds");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
        ads.init();
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) immersive(); }
    @Override protected void onPause() { super.onPause(); web.onPause(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }
    @Override protected void onDestroy() { ads.destroy(); web.destroy(); super.onDestroy(); }
}
