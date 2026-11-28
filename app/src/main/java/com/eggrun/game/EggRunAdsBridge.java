package com.eggrun.game;

import android.app.Activity;
import android.webkit.JavascriptInterface;

/** Puente WebView -> Android. En JavaScript: window.AndroidAds. Los métodos llegan en un hilo del WebView, se pasan al hilo principal. */
public class EggRunAdsBridge {
    private final Activity activity;
    private final AdsManager ads;

    EggRunAdsBridge(Activity activity, AdsManager ads) {
        this.activity = activity;
        this.ads = ads;
    }

    /** Solo al pulsar «REVIVIR CON ANUNCIO». */
    @JavascriptInterface
    public void showRewardedAd() {
        activity.runOnUiThread(ads::requestRewarded);
    }

    /** action = "motivo:número" (p. ej. "level:3"). Se responde con window.eggInterstitialDone(action, mostrado). */
    @JavascriptInterface
    public void showInterstitialAd(final String action) {
        activity.runOnUiThread(() -> ads.requestInterstitial(action));
    }
}
