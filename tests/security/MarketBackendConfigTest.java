package com.zeytindefteri.app;

/**
 * MarketBackendConfig değerleri BuildConfig'ten (local.properties) okur; yoksa yer tutucuya düşer.
 * Üç ayrı çalıştırmayla denenir (run.sh): BuildConfig yok / boş değerli BuildConfig / dolu BuildConfig.
 * Testteki adres ve anahtar uydurma deneme değerleridir, gerçek bir projeye ait değildir.
 */
public class MarketBackendConfigTest {
    static int checks = 0;
    static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "";
        if ("none".equals(mode) || "empty".equals(mode)) {
            check(MarketBackendConfig.SUPABASE_URL.equals("https://REPLACE_ME.supabase.co"), mode + ": adres yer tutucu değil");
            check(MarketBackendConfig.SUPABASE_PUBLISHABLE_KEY.equals("sb_publishable_REPLACE_ME"), mode + ": anahtar yer tutucu değil");
            check(!MarketBackendConfig.isConfigured(), mode + ": yapılandırılmış sayıldı");
            check(!ModerationTransport.isConfigured(MarketBackendConfig.SUPABASE_URL), mode + ": moderasyon adresi geçerli sayıldı");
        } else if ("filled".equals(mode)) {
            check(MarketBackendConfig.SUPABASE_URL.equals("https://abcdefghijklmnopqrst.supabase.co"), "adres BuildConfig'ten okunmadı");
            check(MarketBackendConfig.SUPABASE_PUBLISHABLE_KEY.equals("sb_publishable_TEST_ONLY_not_a_real_key"), "anahtar okunmadı");
            check(MarketBackendConfig.isConfigured(), "dolu değerlerle yapılandırılmamış sayıldı");
            check(MarketBackendConfig.rpcUrl("market_snapshot").equals("https://abcdefghijklmnopqrst.supabase.co/rest/v1/rpc/market_snapshot"), "rpc adresi yanlış");
            check(ModerationTransport.isConfigured(MarketBackendConfig.SUPABASE_URL), "moderasyon adresi kabul edilmedi");
        } else {
            throw new IllegalArgumentException("mod: none | empty | filled");
        }
        check(MarketBackendConfig.PUBLIC_MARKET_MIN_PRODUCERS == 5, "gizlilik eşiği değişmiş");
        System.out.println("MarketBackendConfigTest(" + mode + "): " + checks + " kontrol geçti");
    }
}
