package com.eggrun.game;

/**
 * Identificadores de AdMob. Pruebas y producción están separados.
 *
 * El entorno se elige en app/build.gradle (campo USE_TEST_ADS):
 *   - build "debug"   -> anuncios de PRUEBA  (assembleDebug)
 *   - build "release" -> anuncios REALES     (assembleRelease)
 * Para forzar uno en cualquier build, cambia FORCE por TEST o PRODUCTION.
 */
final class AdConfig {
    private AdConfig() {}

    enum Mode { AUTO, TEST, PRODUCTION }
    /** AUTO = según el tipo de build (debug = prueba, release = producción). */
    static final Mode FORCE = Mode.AUTO;

    // ---- PRODUCCIÓN (tus IDs reales) ----
    static final String APP_ID_PROD          = "ca-app-pub-8132496575036547~5002610198"; // también está en AndroidManifest.xml
    static final String INTERSTITIAL_PROD    = "ca-app-pub-8132496575036547/2184875162";
    static final String REWARDED_PROD        = "ca-app-pub-8132496575036547/9245233848";

    // ---- PRUEBA (IDs oficiales de Google) ----
    static final String INTERSTITIAL_TEST    = "ca-app-pub-3940256099942544/1033173712";
    static final String REWARDED_TEST        = "ca-app-pub-3940256099942544/5224354917";

    static boolean useTestAds() {
        switch (FORCE) {
            case TEST: return true;
            case PRODUCTION: return false;
            default: return BuildConfig.USE_TEST_ADS;
        }
    }
    static String interstitialId() { return useTestAds() ? INTERSTITIAL_TEST : INTERSTITIAL_PROD; }
    static String rewardedId()     { return useTestAds() ? REWARDED_TEST : REWARDED_PROD; }

    // ---- Reglas de frecuencia ----
    /** Tiempo mínimo entre dos intersticiales. */
    static final long MIN_GAP_BETWEEN_INTERSTITIALS_MS = 90_000L;
    /** Como máximo 1 intersticial por cada 2 acciones que podrían mostrarlo. */
    static final int ACTIONS_PER_INTERSTITIAL = 2;
    /** Reintento de carga tras un fallo. */
    static final long RELOAD_DELAY_MS = 30_000L;
}
