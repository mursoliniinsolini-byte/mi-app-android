package com.eggrun.game;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import com.google.android.libraries.ads.mobile.sdk.MobileAds;
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig;
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration;
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo;
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdPreloader;

import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final String TAG = "EggRunAds";

    // ID de aplicación de AdMob de Egg Run.
    private static final String ADMOB_APP_ID = "ca-app-pub-8132496575036547~5002610198";

    // Unidades reales creadas en AdMob.
    private static final String LIVE_INTERSTITIAL_ID = "ca-app-pub-8132496575036547/2184875162";
    private static final String LIVE_REWARDED_ID = "ca-app-pub-8132496575036547/9245233848";

    // IMPORTANTE: mantener true durante desarrollo. Cambiar a false solo al preparar la publicación.
    private static final boolean USE_TEST_ADS = true;
    private static final String TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712";
    private static final String TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917";
    private static final String INTERSTITIAL_ID = USE_TEST_ADS ? TEST_INTERSTITIAL_ID : LIVE_INTERSTITIAL_ID;
    private static final String REWARDED_ID = USE_TEST_ADS ? TEST_REWARDED_ID : LIVE_REWARDED_ID;
    private static final long INTERSTITIAL_COOLDOWN_MS = 90_000L;

    private WebView web;
    private long lastInterstitialShownAt = 0L;
    private boolean adsInitialized = false;

    /** Puente JavaScript: el HTML solo solicita anuncios; Android los presenta con el SDK nativo. */
    public final class AdsBridge {
        @JavascriptInterface
        public void showRewarded() {
            runOnUiThread(() -> showRewardedInternal());
        }

        @JavascriptInterface
        public void showInterstitial() {
            runOnUiThread(() -> showInterstitialInternal());
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0d0a1f"));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        web.addJavascriptInterface(new AdsBridge(), "EggAds");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");

        initializeAds();
    }

    private void initializeAds() {
        // GMA Next-Gen requiere inicialización en un hilo de fondo antes de solicitar anuncios.
        new Thread(() -> {
            try {
                MobileAds.initialize(
                    getApplicationContext(),
                    new InitializationConfig.Builder(ADMOB_APP_ID).build(),
                    initializationStatus -> runOnUiThread(() -> {
                        adsInitialized = true;
                        startAdPreloading();
                        Log.i(TAG, "GMA Next-Gen SDK inicializado.");
                    })
                );
            } catch (Exception e) {
                Log.e(TAG, "No se pudo inicializar AdMob", e);
            }
        }, "EggRun-AdMob-Init").start();
    }

    private void startAdPreloading() {
        if (!adsInitialized) return;

        PreloadCallback interstitialCallback = new PreloadCallback() {
            @Override public void onAdFailedToPreload(@NonNull String preloadId, @NonNull LoadAdError error) {
                Log.w(TAG, "No se pudo precargar intersticial: " + error.getMessage());
            }
            @Override public void onAdsExhausted(@NonNull String preloadId) {
                Log.d(TAG, "Sin intersticial precargado por el momento.");
            }
            @Override public void onAdPreloaded(@NonNull String preloadId, @NonNull ResponseInfo responseInfo) {
                Log.d(TAG, "Intersticial listo.");
            }
        };
        PreloadCallback rewardedCallback = new PreloadCallback() {
            @Override public void onAdFailedToPreload(@NonNull String preloadId, @NonNull LoadAdError error) {
                Log.w(TAG, "No se pudo precargar recompensado: " + error.getMessage());
            }
            @Override public void onAdsExhausted(@NonNull String preloadId) {
                Log.d(TAG, "Sin recompensado precargado por el momento.");
            }
            @Override public void onAdPreloaded(@NonNull String preloadId, @NonNull ResponseInfo responseInfo) {
                Log.d(TAG, "Recompensado listo.");
            }
        };

        InterstitialAdPreloader.start(
            INTERSTITIAL_ID,
            new PreloadConfiguration(new AdRequest.Builder(INTERSTITIAL_ID).build()),
            interstitialCallback
        );
        RewardedAdPreloader.start(
            REWARDED_ID,
            new PreloadConfiguration(new AdRequest.Builder(REWARDED_ID).build()),
            rewardedCallback
        );
    }

    private void showInterstitialInternal() {
        if (!adsInitialized) {
            notifyInterstitialFinished();
            return;
        }
        long now = System.currentTimeMillis();
        if (lastInterstitialShownAt != 0L && now - lastInterstitialShownAt < INTERSTITIAL_COOLDOWN_MS) {
            notifyInterstitialFinished();
            return;
        }

        InterstitialAd ad = InterstitialAdPreloader.pollAd(INTERSTITIAL_ID);
        if (ad == null) {
            // Si todavía no hay anuncio cargado, no bloqueamos el reinicio de la partida.
            notifyInterstitialFinished();
            return;
        }

        ad.setAdEventCallback(new InterstitialAdEventCallback() {
            @Override public void onAdShowedFullScreenContent() {
                lastInterstitialShownAt = System.currentTimeMillis();
            }
            @Override public void onAdDismissedFullScreenContent() {
                runOnUiThread(() -> notifyInterstitialFinished());
            }
            @Override public void onAdFailedToShowFullScreenContent(@NonNull FullScreenContentError error) {
                Log.w(TAG, "Falló la presentación del intersticial: " + error.getMessage());
                runOnUiThread(() -> notifyInterstitialFinished());
            }
        });
        try {
            ad.show(this);
        } catch (Exception e) {
            Log.e(TAG, "Error al mostrar intersticial", e);
            notifyInterstitialFinished();
        }
    }

    private void showRewardedInternal() {
        if (!adsInitialized) {
            notifyRewardedResult(false);
            return;
        }
        RewardedAd ad = RewardedAdPreloader.pollAd(REWARDED_ID);
        if (ad == null) {
            notifyRewardedResult(false);
            return;
        }

        final AtomicBoolean rewardEarned = new AtomicBoolean(false);
        ad.setAdEventCallback(new RewardedAdEventCallback() {
            @Override public void onAdDismissedFullScreenContent() {
                runOnUiThread(() -> notifyRewardedResult(rewardEarned.get()));
            }
            @Override public void onAdFailedToShowFullScreenContent(@NonNull FullScreenContentError error) {
                Log.w(TAG, "Falló la presentación del recompensado: " + error.getMessage());
                runOnUiThread(() -> notifyRewardedResult(false));
            }
        });
        try {
            ad.show(this, rewardItem -> rewardEarned.set(true));
        } catch (Exception e) {
            Log.e(TAG, "Error al mostrar recompensado", e);
            notifyRewardedResult(false);
        }
    }

    private void notifyInterstitialFinished() {
        if (web != null) web.evaluateJavascript(
            "window.eggInterstitialFinished && window.eggInterstitialFinished();", null);
    }

    private void notifyRewardedResult(boolean earned) {
        if (web != null) web.evaluateJavascript(
            "window.eggRewardResult && window.eggRewardResult(" + earned + ");", null);
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) immersive();
    }
    @Override protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }
    @Override protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
