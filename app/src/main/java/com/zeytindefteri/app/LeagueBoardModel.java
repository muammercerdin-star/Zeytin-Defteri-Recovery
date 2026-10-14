package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Zeytin Ligi ekranının saf (Android'siz) gösterim kuralları.
 *
 * Gizlilik: bölge kilo/fiyat/baskın elek bilgisi yalnızca sunucu yayımlamışsa
 * VE bölgede en az {@link #MIN_PRODUCERS} farklı üretici varsa gösterilir.
 * Az üreticili bir bölgede bir üretici, toplamdan kendi verisini çıkararak
 * diğerlerinin satışını hesaplayabilir; bu yüzden istemci sunucunun eşiğine
 * körü körüne güvenmez.
 */
public final class LeagueBoardModel {
    private LeagueBoardModel() {}

    /** Bölgesel anonim özetin açılması için gereken farklı üretici sayısı. */
    public static final int MIN_PRODUCERS = MarketBackendConfig.PUBLIC_MARKET_MIN_PRODUCERS;
    /**
     * Top 20'nin yayımlanması için sunucuda gereken sıralanabilir üretici sayısı
     * (functions/league/policy.js → rankRegion: cohort.length < 4 ise yayın yok).
     */
    public static final int MIN_RANKED = 4;
    private static final Locale TR = new Locale("tr", "TR");

    public static boolean publishable(boolean serverPublished, int producers) {
        return serverPublished && producers >= MIN_PRODUCERS;
    }

    /** "Edremit / Balıkesir" → {"Edremit", "Balıkesir"}; ayraç yoksa il boş. */
    public static String[] splitRegion(String name) {
        String v = name == null ? "" : name.trim();
        int slash = v.indexOf('/');
        if (slash < 0) slash = v.indexOf(',');
        if (slash < 0) return new String[]{v, ""};
        return new String[]{v.substring(0, slash).trim(), v.substring(slash + 1).trim()};
    }

    /**
     * Bölge rozetleri: ilçenin baş harfi; aynı harfle başlayan birden çok ilçe
     * varsa onlar için ilk iki harf (Ayvalık → AY, Akhisar → AK).
     */
    public static List<String> badges(List<String> districts) {
        Map<String, Integer> firstCount = new HashMap<>();
        List<String> firsts = new ArrayList<>();
        for (String d : districts) {
            String f = letters(d, 1);
            firsts.add(f);
            firstCount.put(f, firstCount.containsKey(f) ? firstCount.get(f) + 1 : 1);
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < districts.size(); i++) {
            String f = firsts.get(i);
            out.add(f.isEmpty() ? "?" : firstCount.get(f) > 1 ? letters(districts.get(i), 2) : f);
        }
        return out;
    }

    private static String letters(String value, int count) {
        String v = value == null ? "" : value.trim();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < v.length() && b.length() < count; i++) {
            char c = v.charAt(i);
            if (Character.isLetter(c)) b.append(c);
        }
        return b.toString().toUpperCase(TR);
    }

    /** Kartta üretici satırı: açık bölgede "7 üretici", eşik altında "2 / 5 üretici". */
    public static String countText(int producers, boolean open) {
        int c = Math.max(0, producers);
        return open ? c + " üretici" : Math.min(c, MIN_PRODUCERS) + " / " + MIN_PRODUCERS + " üretici";
    }

    public static String needText(int producers) {
        int need = MIN_PRODUCERS - Math.max(0, producers);
        return need <= 0 ? "" : need + " üretici daha gerekli";
    }

    public static int progressPercent(int producers) {
        return Math.max(0, Math.min(100, Math.round(100f * Math.max(0, producers) / MIN_PRODUCERS)));
    }

    /** Kartın üst şeridindeki durum etiketi. */
    public static String tag(boolean hasTop, boolean open) {
        if (hasTop) return "TOP 20";
        return open ? "ANONİM" : "EŞİK";
    }

    public static String statusLine(int regionCount, boolean anyTop) {
        if (regionCount <= 0) return "Bu sezon için henüz bölge yok";
        return regionCount + " bölge • " + (anyTop
                ? "Top 20 yayında"
                : "Bölge rakamları " + MIN_PRODUCERS + " üreticiyle açılır");
    }

    public static String emptyTitle(String district) {
        String d = district == null || district.trim().isEmpty() ? "Bölge" : district.trim();
        return d + " kupası henüz sahipsiz";
    }

    public static String emptyBody() {
        return "Bölgede en az " + MIN_RANKED + " üretici sıralama şartlarını sağlayınca "
                + "Top 20 açılır ve ilk üç kürsüye çıkar.";
    }

    /** Kürsü sırası: ortada 1., solda 2., sağda 3. Eksik sıralar atlanır. */
    public static int[] podiumOrder(int available) {
        if (available >= 3) return new int[]{2, 1, 3};
        if (available == 2) return new int[]{2, 1};
        if (available == 1) return new int[]{1};
        return new int[0];
    }
}
