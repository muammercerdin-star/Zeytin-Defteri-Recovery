package com.zeytindefteri.app;

/**
 * Anonim Piyasa Supabase istemci ayarları.
 *
 * Bu dosyada yalnız istemciye açık Project URL + publishable key bulunur.
 * Supabase secret/service-role anahtarları APK içine KESİNLİKLE konmaz.
 */
public final class MarketBackendConfig {

    public static final String SUPABASE_URL =
            "https://REPLACE_ME.supabase.co";

    public static final String SUPABASE_PUBLISHABLE_KEY =
            "sb_publishable_REPLACE_ME";

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
}
