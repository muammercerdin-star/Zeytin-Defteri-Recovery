package com.zeytindefteri.app;

/**
 * Fişin (Tezgâh baskı önizlemesi) "yazıcıdan çıkma" animasyonunun saf hesapları (Android'e bağlı değil, test edilir).
 * Kâğıt yazıcı ağzından akıcı biçimde iner, sonda yavaşlar; ara ara hafif titreşim tıkı verilir.
 */
public final class ReceiptPrint {

    private ReceiptPrint() { }

    /** En az / en çok baskı süresi (ms). Uzun fiş biraz daha uzun basılır ama sabırsızlandırmaz. */
    public static final long MIN_MS = 1000;
    public static final long MAX_MS = 1700;
    /** Damganın vurulması (ms). */
    public static final long STAMP_MS = 260;

    /** Fişin boyuna göre baskı süresi: her 100 dp için ~150 ms, sınırlar içinde. */
    public static long durationMs(float heightDp) {
        long ms = (long) (600 + Math.max(0f, heightDp) * 1.4f);
        return Math.max(MIN_MS, Math.min(MAX_MS, ms));
    }

    /** Kaç itişte basılacağı: kabaca her 40 dp bir satır, 8–22 arası. */
    public static int steps(float heightDp) {
        int s = Math.round(Math.max(0f, heightDp) / 40f);
        return Math.max(8, Math.min(22, s));
    }

    /**
     * Zaman (0..1) -> kâğıdın çıkmış oranı (0..1). Akıcı ve kesintisiz: kâğıt sabit hızla akar,
     * sonda yumuşakça yavaşlayıp durur (duraklama yok; ekranda takılma gibi görünmesin).
     * steps yalnız titreşim tıkları içindir, harekete etki etmez.
     */
    public static float progress(float t, int steps) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        // İlk %70 doğrusal, son %30'da hız sıfıra iner (iki parça, hız sürekli).
        final float k = 0.70f;
        final float v = 1f / (k + (1f - k) / 2f);          // doğrusal bölümün hızı
        if (t <= k) return v * t;
        float u = (t - k) / (1f - k);                      // 0..1
        return Math.min(1f, v * k + v * (1f - k) * (u - u * u / 2f));
    }

    /** Hangi adımdayız (titreşim tıkı için): 0..steps. */
    public static int stepIndex(float t, int steps) {
        if (t <= 0f) return 0;
        if (t >= 1f) return Math.max(1, steps);
        return (int) Math.floor(t * Math.max(1, steps));
    }

    /** Baskı bitince vurulan damga: satış yoksa KAYDEDİLDİ, tamamı alındıysa ÖDENDİ, değilse ALACAK. */
    public static String stamp(double[] sale) {
        if (sale == null || sale.length < 2) return "KAYDEDİLDİ";
        double gross = sale[0], received = sale[1];
        if (gross > 0.009 && received >= gross - 0.009) return "ÖDENDİ";
        return "ALACAK";
    }

    /** Fişin altındaki süs barkodu: kayıt numarasından üretilir, her fişte farklı ama sabit. Çubuk genişlikleri 1..3. */
    public static int[] barcode(long recordId, int bars) {
        int n = Math.max(0, bars);
        int[] w = new int[n];
        long x = recordId * 6364136223846793005L + 1442695040888963407L;
        for (int i = 0; i < n; i++) {
            x ^= (x << 13); x ^= (x >>> 7); x ^= (x << 17);
            w[i] = 1 + (int) ((x >>> 33) % 3);
        }
        return w;
    }

    /** Fiş numarası: "No 000123" biçiminde. */
    public static String number(long recordId) {
        long id = Math.max(0, recordId);
        String s = String.valueOf(id);
        StringBuilder b = new StringBuilder("No ");
        for (int i = s.length(); i < 6; i++) b.append('0');
        return b.append(s).toString();
    }
}
