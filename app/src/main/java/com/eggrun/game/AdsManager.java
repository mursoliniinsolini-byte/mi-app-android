package com.eggrun.game;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

/**
 * Administra TODOS los anuncios del juego (AdMob real). Se usa solo desde el hilo principal.
 *
 * Intersticial: lo pide el juego (JS) con una "acción" (p. ej. "level:3"). Siempre se responde a JS
 * exactamente una vez con eggInterstitialDone(acción, mostrado) para que el juego continúe, haya o no anuncio.
 * Recompensado: lo pide el juego solo al pulsar «REVIVIR CON ANUNCIO». Resultado: earned | closed | unavailable | error.
 */
final class AdsManager {
    private static final String TAG = "EggRunAds";

    private final Activity activity;
    private final WebView web;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private InterstitialAd interstitial;
    private boolean interstitialLoading;
    private RewardedAd rewarded;
    private boolean rewardedLoading;

    /** Hay un anuncio a pantalla completa en curso (cualquier tipo). */
    private boolean fullscreenShowing;
    /** Acción del juego que debe continuar cuando se cierre el intersticial. */
    private String pendingAction;
    private boolean rewardEarned;
    private boolean rewardInProgress;

    private boolean interstitialEverShown;
    private long lastInterstitialAt;
    private int actionsSinceInterstitial;
    private boolean destroyed;

    AdsManager(Activity activity, WebView web) {
        this.activity = activity;
        this.web = web;
    }

    /** Inicializa el SDK (en segundo plano) y precarga un intersticial y un recompensado. */
    void init() {
        new Thread(() -> MobileAds.initialize(activity, status -> activity.runOnUiThread(() -> {
            loadInterstitial();
            loadRewarded();
        }))).start();
    }

    void destroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
    }

    // ------------------------------------------------------------------ INTERSTICIAL

    private void loadInterstitial() {
        if (destroyed || interstitial != null || interstitialLoading) return;
        interstitialLoading = true;
        InterstitialAd.load(activity, AdConfig.interstitialId(), new AdRequest.Builder().build(),
            new InterstitialAdLoadCallback() {
                @Override public void onAdLoaded(InterstitialAd ad) {
                    interstitialLoading = false;
                    interstitial = ad;
                }
                @Override public void onAdFailedToLoad(LoadAdError error) {
                    Log.w(TAG, "Intersticial no cargó: " + error.getMessage());
                    interstitialLoading = false;
                    interstitial = null;
                    handler.postDelayed(AdsManager.this::loadInterstitial, AdConfig.RELOAD_DELAY_MS);
                }
            });
    }

    /** Pide un intersticial. Hilo principal. Siempre acaba llamando a eggInterstitialDone. */
    void requestInterstitial(String rawAction) {
        final String action = sanitize(rawAction);
        // Si ya hay un anuncio en curso o pendiente se prioriza ese: no se acumulan.
        if (fullscreenShowing || pendingAction != null || rewardInProgress) { notifyInterstitialDone(action, false); return; }

        actionsSinceInterstitial++;
        long now = System.currentTimeMillis();
        boolean gapOk = !interstitialEverShown || now - lastInterstitialAt >= AdConfig.MIN_GAP_BETWEEN_INTERSTITIALS_MS;
        boolean cadenceOk = !interstitialEverShown || actionsSinceInterstitial >= AdConfig.ACTIONS_PER_INTERSTITIAL;
        if (!gapOk || !cadenceOk) { notifyInterstitialDone(action, false); return; }

        final InterstitialAd ad = interstitial;
        if (ad == null) { loadInterstitial(); notifyInterstitialDone(action, false); return; }

        interstitial = null;
        pendingAction = action;
        fullscreenShowing = true;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdShowedFullScreenContent() {
                interstitialEverShown = true;
                lastInterstitialAt = System.currentTimeMillis();
                actionsSinceInterstitial = 0;
                js("window.eggInterstitialShowing&&window.eggInterstitialShowing('" + action + "')");
            }
            @Override public void onAdDismissedFullScreenContent() { finishInterstitial(true); }
            @Override public void onAdFailedToShowFullScreenContent(AdError error) {
                Log.w(TAG, "Intersticial falló al mostrarse: " + error.getMessage());
                finishInterstitial(false);
            }
        });
        try {
            ad.show(activity);
        } catch (RuntimeException e) {
            Log.w(TAG, "show() lanzó excepción", e);
            finishInterstitial(false);
        }
    }

    /** Cierra el ciclo del intersticial: continúa la acción pendiente en el juego y precarga el siguiente. */
    private void finishInterstitial(boolean shown) {
        String action = pendingAction;
        pendingAction = null;
        fullscreenShowing = false;
        loadInterstitial();
        if (action != null) notifyInterstitialDone(action, shown);
    }

    private void notifyInterstitialDone(String action, boolean shown) {
        js("window.eggInterstitialDone&&window.eggInterstitialDone('" + action + "'," + shown + ")");
    }

    // ------------------------------------------------------------------ RECOMPENSADO

    private void loadRewarded() {
        if (destroyed || rewarded != null || rewardedLoading) return;
        rewardedLoading = true;
        RewardedAd.load(activity, AdConfig.rewardedId(), new AdRequest.Builder().build(),
            new RewardedAdLoadCallback() {
                @Override public void onAdLoaded(RewardedAd ad) {
                    rewardedLoading = false;
                    rewarded = ad;
                }
                @Override public void onAdFailedToLoad(LoadAdError error) {
                    Log.w(TAG, "Recompensado no cargó: " + error.getMessage());
                    rewardedLoading = false;
                    rewarded = null;
                    handler.postDelayed(AdsManager.this::loadRewarded, AdConfig.RELOAD_DELAY_MS);
                }
            });
    }

    /** Pide el anuncio recompensado (solo al pulsar «REVIVIR CON ANUNCIO»). Hilo principal. */
    void requestRewarded() {
        if (fullscreenShowing || rewardInProgress || pendingAction != null) { rewardResult("busy"); return; }
        final RewardedAd ad = rewarded;
        if (ad == null) { loadRewarded(); rewardResult("unavailable"); return; }

        rewarded = null;
        rewardInProgress = true;
        rewardEarned = false;
        fullscreenShowing = true;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdShowedFullScreenContent() {
                js("window.eggRewardShowing&&window.eggRewardShowing()");
            }
            @Override public void onAdDismissedFullScreenContent() {
                // Solo se revive si AdMob confirmó la recompensa antes de cerrarse.
                finishRewarded(rewardEarned ? "earned" : "closed");
            }
            @Override public void onAdFailedToShowFullScreenContent(AdError error) {
                Log.w(TAG, "Recompensado falló al mostrarse: " + error.getMessage());
                finishRewarded("error");
            }
        });
        try {
            ad.show(activity, new OnUserEarnedRewardListener() {
                @Override public void onUserEarnedReward(RewardItem rewardItem) { rewardEarned = true; }
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "show() lanzó excepción", e);
            finishRewarded("error");
        }
    }

    private void finishRewarded(String result) {
        rewardInProgress = false;
        fullscreenShowing = false;
        rewardEarned = false;
        loadRewarded();
        rewardResult(result);
    }

    private void rewardResult(String result) {
        js("window.eggRewardResult&&window.eggRewardResult('" + result + "')");
    }

    // ------------------------------------------------------------------ utilidades

    private void js(final String script) {
        if (destroyed) return;
        activity.runOnUiThread(() -> web.evaluateJavascript(script, null));
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        return s.replaceAll("[^A-Za-z0-9:_\\-]", "");
    }
}
