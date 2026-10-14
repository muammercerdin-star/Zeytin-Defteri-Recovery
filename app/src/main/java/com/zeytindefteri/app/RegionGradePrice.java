package com.zeytindefteri.app;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Bölge raporunda bir eleğin fiyat kutusu. Saf Java; masaüstünde test edilir (tests/league_region).
 *
 * Gizlilik: bir elekte en az {@link MarketBackendConfig#PUBLIC_MARKET_MIN_PRODUCERS} farklı üretici yoksa
 * fiyat gösterilmez. Daha azında medyan tek tek üreticilerin fiyatını ortaya çıkarır.
 */
final class RegionGradePrice {
    static final String NO_VALUE = "—";

    private RegionGradePrice() {}

    static int threshold() {
        return MarketBackendConfig.PUBLIC_MARKET_MIN_PRODUCERS;
    }

    static boolean visible(int producerCount) {
        return producerCount >= threshold();
    }

    /** Büyük yazılan fiyat: medyan, yoksa ortalama; eşik altında ya da veri yoksa "—". */
    static String priceText(int producerCount, double medianPrice, double averagePrice) {
        if (!visible(producerCount)) return NO_VALUE;
        double value = medianPrice > 0d ? medianPrice : averagePrice;
        return value > 0d && !Double.isInfinite(value) && !Double.isNaN(value) ? format(value) : NO_VALUE;
    }

    /** Fiyatın altındaki satır: eşik altında "n/5 üretici", üstünde son 7 gün değişimi ya da "trend yok". */
    static String footText(int producerCount, boolean trendAvailable, double trendPct) {
        if (!visible(producerCount)) return Math.max(0, producerCount) + "/" + threshold() + " üretici";
        if (!trendAvailable || Double.isNaN(trendPct) || Double.isInfinite(trendPct)) return "trend yok";
        String arrow = trendPct > 0.004d ? "▲ " : trendPct < -0.004d ? "▼ " : "";
        return arrow + format(Math.abs(trendPct)) + "% · 7 gün";
    }

    /** -1 düşüş, 0 yatay/yok, 1 yükseliş (renk için). */
    static int trendDirection(int producerCount, boolean trendAvailable, double trendPct) {
        if (!visible(producerCount) || !trendAvailable) return 0;
        if (trendPct >= 1.5d) return 1;
        if (trendPct <= -1.5d) return -1;
        return 0;
    }

    static String format(double value) {
        NumberFormat number = NumberFormat.getNumberInstance(Locale.forLanguageTag("tr-TR"));
        number.setMaximumFractionDigits(2);
        number.setMinimumFractionDigits(0);
        return number.format(value);
    }
}
