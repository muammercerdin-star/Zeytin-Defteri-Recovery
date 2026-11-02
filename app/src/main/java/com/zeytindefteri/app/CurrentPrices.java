package com.zeytindefteri.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Güncel elek fiyatı: bölgedeki herkesin son satışlarının ortadaki fiyatı (sunucu: market_region_current_prices).
 * Elek formunun fiyat zinciri, her elek için ayrı:
 *   1) bölge son 3 gün → 7 gün → 30 gün (sunucu en kısa dolu pencereyi seçer)
 *   2) senin son 30 gündeki son 3 satışının ortadaki fiyatı
 *   3) fiyat ayarın
 * Saf sınıf (Android yok): kartta ve formda görünen metinler burada üretilir ve test edilir.
 */
final class CurrentPrices {
    static final String[] GRADES = {"Double", "1 No", "2 No", "3 No", "4 No", "Yağlık"};
    static final int NONE = 0, REGION = 1, OWN = 2, SETTINGS = 3;
    /** Önbellekteki bölge fiyatı bu kadar eskiyse kullanılmaz (çevrimdışı açılışta da işe yarasın diye 12 saat). */
    static final long CACHE_MS = 12L * 60L * 60L * 1000L;

    private CurrentPrices() {}

    static final class Entry {
        double price;
        int windowDays;
        int saleCount;
        int producerCount;
        String lastSale = "";
    }

    static final class Choice {
        final double[] price = new double[6];
        final int[] source = new int[6];
        final Entry[] region = new Entry[6];
    }

    /** "DOUBLE", "1", "1 No", "YAGLIK", "Yağlık" → 0..5; tanınmazsa -1. */
    static int index(String grade) {
        if (grade == null) return -1;
        String g = grade.trim().toLowerCase(new Locale("tr", "TR")).replace("ğ", "g").replace("ı", "i");
        switch (g) {
            case "double": return 0;
            case "1": case "1 no": return 1;
            case "2": case "2 no": return 2;
            case "3": case "3 no": return 3;
            case "4": case "4 no": return 4;
            case "yaglik": return 5;
            default: return -1;
        }
    }

    /** Sunucu cevabı (JSON dizi) → elek sırasıyla 6 kayıt; olmayan elek null. Bozuk cevap → hepsi null. */
    static Entry[] parse(String json) {
        Entry[] out = new Entry[6];
        if (json == null || json.trim().isEmpty()) return out;
        try {
            JSONArray rows = new JSONArray(json);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                int at = index(row.optString("grade", ""));
                double price = row.optDouble("price", 0d);
                if (at < 0 || !(price > 0d) || price > 100000d) continue;
                Entry e = new Entry();
                e.price = price;
                e.windowDays = Math.max(0, row.optInt("window_days", 0));
                e.saleCount = Math.max(0, row.optInt("sale_count", 0));
                e.producerCount = Math.max(0, row.optInt("producer_count", 0));
                e.lastSale = row.isNull("last_sale_date") ? "" : row.optString("last_sale_date", "");
                out[at] = e;
            }
        } catch (Exception ignored) {
            return new Entry[6];
        }
        return out;
    }

    static boolean any(Entry[] region) {
        if (region == null) return false;
        for (Entry e : region) if (e != null) return true;
        return false;
    }

    static double median(List<Double> values) {
        List<Double> v = new ArrayList<>();
        if (values != null) for (Double d : values) if (d != null && d > 0d) v.add(d);
        if (v.isEmpty()) return 0d;
        Collections.sort(v);
        int n = v.size();
        return n % 2 == 1 ? v.get(n / 2) : (v.get(n / 2 - 1) + v.get(n / 2)) / 2d;
    }

    /** Her elek için zincirdeki ilk dolu fiyat. own/settings 6 elemanlı; 0 = yok. */
    static Choice choose(Entry[] region, double[] own, double[] settings) {
        Choice c = new Choice();
        for (int i = 0; i < 6; i++) {
            Entry r = region == null ? null : region[i];
            double o = own == null ? 0d : own[i];
            double s = settings == null ? 0d : settings[i];
            if (r != null && r.price > 0d) { c.price[i] = r.price; c.source[i] = REGION; c.region[i] = r; }
            else if (o > 0d) { c.price[i] = o; c.source[i] = OWN; }
            else if (s > 0d) { c.price[i] = s; c.source[i] = SETTINGS; }
            else c.source[i] = NONE;
        }
        return c;
    }

    static String money(double v) {
        long whole = Math.round(v);
        if (Math.abs(v - whole) < 0.005) return whole + " TL";
        return String.format(new Locale("tr", "TR"), "%.2f", v).replaceAll(",?0+$", "") + " TL";
    }

    static String window(int days) {
        return days <= 0 ? "" : "son " + days + " gün";
    }

    /** Elek formunun fiyat satırının altındaki kısa not. Hiç bölge fiyatı yoksa null (not gizlenir). */
    static String note(Choice c, String regionName) {
        if (c == null) return null;
        int minW = Integer.MAX_VALUE, maxW = 0, regionCount = 0;
        List<String> own = new ArrayList<>(), settings = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            if (c.source[i] == REGION) {
                regionCount++;
                minW = Math.min(minW, c.region[i].windowDays);
                maxW = Math.max(maxW, c.region[i].windowDays);
            } else if (c.source[i] == OWN) own.add(GRADES[i]);
            else if (c.source[i] == SETTINGS) settings.add(GRADES[i]);
        }
        if (regionCount == 0) return null;
        String place = regionName == null || regionName.trim().isEmpty() ? "Bölge" : regionName.trim();
        StringBuilder sb = new StringBuilder("Fiyatlar: ").append(place).append(" piyasası · ");
        sb.append(minW == maxW ? window(minW) : "son " + minW + "–" + maxW + " gün");
        if (!own.isEmpty()) sb.append(" · ").append(join(own)).append(": son satışların");
        if (!settings.isEmpty()) sb.append(" · ").append(join(settings)).append(": fiyat ayarın");
        return sb.append("  ›").toString();
    }

    /** Ayrıntı penceresinde her elek için bir satır. */
    static String detail(Choice c) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (c == null || c.source[i] == NONE) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(GRADES[i]).append(": ").append(money(c.price[i])).append(" · ");
            if (c.source[i] == REGION) {
                Entry e = c.region[i];
                sb.append("bölge, ").append(window(e.windowDays)).append(", ").append(e.saleCount).append(" satış");
            } else if (c.source[i] == OWN) sb.append("senin son satışların");
            else sb.append("fiyat ayarın");
        }
        return sb.toString();
    }

    /** Piyasa kartındaki satır: "Güncel fiyat · son 3 gün: 125 TL (4 satış)". Veri yoksa "". */
    static String marketLine(Entry e) {
        if (e == null || !(e.price > 0d)) return "";
        return "Güncel fiyat · " + window(e.windowDays) + ": " + money(e.price)
                + " (" + e.saleCount + " satış)";
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) { if (sb.length() > 0) sb.append(", "); sb.append(p); }
        return sb.toString();
    }
}
