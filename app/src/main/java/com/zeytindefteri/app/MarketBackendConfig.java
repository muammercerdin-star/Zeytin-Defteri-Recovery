package com.zeytindefteri.app;

/**
 * Anonim Piyasa Supabase istemci ayarları.
 *
 * Yalnız istemciye açık Project URL + publishable key kullanılır.
 * Supabase secret/service-role anahtarları APK içine KESİNLİKLE konmaz.
 *
 * Gerçek değerler repoda TUTULMAZ. Derleme sırasında proje kökündeki local.properties
 * (git'e gitmez) dosyasından okunur ve BuildConfig'e yazılır (app/build.gradle):
 *   SUPABASE_URL=https://<proje>.supabase.co
 *   SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
 * Değerler yoksa (ya da BuildConfig bulunamazsa, ör. masaüstü testlerinde) yer tutucular
 * kullanılır; isConfigured() false döner ve uygulama Supabase'e hiç istek göndermez.
 */
public final class MarketBackendConfig {

    static final String PLACEHOLDER_URL = "https://REPLACE_ME.supabase.co";
    static final String PLACEHOLDER_KEY = "sb_publishable_REPLACE_ME";

    public static final String SUPABASE_URL =
            buildValue("SUPABASE_URL", PLACEHOLDER_URL);

    public static final String SUPABASE_PUBLISHABLE_KEY =
            buildValue("SUPABASE_PUBLISHABLE_KEY", PLACEHOLDER_KEY);

    /** Production privacy floor. Counts distinct producers, never sale rows. */
    public static final int PUBLIC_MARKET_MIN_PRODUCERS = 5;

    private MarketBackendConfig() {
    }

    public static boolean isConfigured() {
        String url = safe(SUPABASE_URL);
        String key = safe(SUPABASE_PUBLISHABLE_KEY);

        return url.startsWith("https://")
                && url.endsWith(".supabase.co")
                && !url.contains("REPLACE_ME")
                && key.startsWith("sb_publishable_")
                && !key.contains("REPLACE_ME");
    }

    public static String rpcUrl(String functionName) {
        String base = safe(SUPABASE_URL);
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/rest/v1/rpc/" + safe(functionName);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * BuildConfig alanını okur. Sınıf doğrudan referans verilmez; böylece bu dosya testlerde
     * BuildConfig olmadan da derlenir. (Kod küçültme açılırsa proguard-rules.pro BuildConfig'i korur.)
     */
    static String buildValue(String field, String fallback) {
        try {
            Object value = Class.forName("com.zeytindefteri.app.BuildConfig").getField(field).get(null);
            String text = value instanceof String ? ((String) value).trim() : "";
            return text.isEmpty() ? fallback : text;
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
