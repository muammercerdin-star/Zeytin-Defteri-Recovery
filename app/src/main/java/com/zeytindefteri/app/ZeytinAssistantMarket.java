package com.zeytindefteri.app;

import java.util.*;

/**
 * Asistanın piyasa cevabı: Supabase'teki anonim bölge raporundan (Zeytin Ligi bölge kartıyla aynı veri).
 * Saf Java; masaüstünde test edilir (tests/assistant_market).
 *
 * Gizlilik: bölgede ya da elekte {@link MarketBackendConfig#PUBLIC_MARKET_MIN_PRODUCERS} farklı üretici
 * yoksa fiyat söylenmez (MarketCloudRepository zaten temizler; burada ikinci kez denetlenir).
 */
final class ZeytinAssistantMarket {
    private ZeytinAssistantMarket() {}

    static final String[] GRADES = {"Double", "1 No", "2 No", "3 No", "4 No", "Yağlık"};

    static final class Grade {
        final String name; final int producers; final double median, average; final boolean trend; final double trendPct;
        Grade(String name, int producers, double median, double average, boolean trend, double trendPct) {
            this.name = name == null ? "" : name; this.producers = Math.max(0, producers);
            this.median = median; this.average = average; this.trend = trend; this.trendPct = trendPct;
        }
        double price() { double v = median > 0 ? median : average; return valid(v) ? v : 0; }
        boolean visible() { return RegionGradePrice.visible(producers) && price() > 0; }
    }

    /** Komuttaki elek ("DOUBLE", "1".."4", "YAGLIK") → rapordaki ad; boş: elek söylenmedi. */
    static String grade(String commandGrade) {
        if (commandGrade == null) return "";
        switch (commandGrade) {
            case "DOUBLE": return "Double";
            case "YAGLIK": return "Yağlık";
            case "1": case "2": case "3": case "4": return commandGrade + " No";
            default: return "";
        }
    }

    /** Kullanıcı kendi satış fiyatını mı soruyor (bölge değil)? */
    static boolean ownSales(String normalized) { return ZeytinAssistantCommand.ownSales(normalized); }

    private static final Set<String> NOT_PLACES = new HashSet<>(Arrays.asList(
            "piyasa", "bolge", "bolgem", "sezon", "hafta", "elek", "bura", "sura", "ora", "simdi", "zeytin", "fiyat",
            "numara", "double", "duble", "yaglik", "son", "bu", "gecen", "ay", "gun", "pazar", "hal", "bahce", "bolgen",
            "piyasan", "ben", "bugun", "yarin", "sene", "yil", "kilo"));

    /**
     * Sorudaki bölge: kayıtlı bölge adlarından biri geçiyorsa o; "…'de / …'da" ile bilinmeyen bir yer
     * söylendiyse null (sessizce kendi bölgesine düşmesin); hiç yer söylenmediyse boş (kendi bölgesi).
     * normalize kesme işaretini boşluğa çevirir ("Salihli'de" → "salihli de"); ek ayrı da yazılmış olabilir.
     * @param regions bölge kimliği → ad ("Alaşehir / Manisa")
     */
    static String regionFor(String normalized, Map<String, String> regions) {
        String s = " " + (normalized == null ? "" : normalized) + " ";
        String best = "", bestName = "";
        for (Map.Entry<String, String> e : regions.entrySet()) {
            String name = ZeytinAssistantCommand.normalize(MeydanMarketStrip.shortRegion(e.getValue()));
            if (name.length() < 3) continue;
            if (s.matches(".* " + java.util.regex.Pattern.quote(name) + "[a-z]* .*") && name.length() > bestName.length()) {
                best = e.getKey(); bestName = name;
            }
        }
        if (!best.isEmpty()) return best;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b([a-z]{3,}?) ?(da|de|ta|te|dan|den|daki|deki|nin|in)\\b").matcher(s);
        while (m.find()) if (!NOT_PLACES.contains(m.group(1))) return null;
        return "";
    }

    static String noRegion() {
        return "Bölgen henüz belli değil. Elek kaydı girdiğinde bölgenin anonim fiyatlarını söyleyebilirim. "
                + "Kendi satış fiyatını sormak için “benim 1 numara satış fiyatım” diyebilirsin.";
    }

    static String unknownRegion() {
        return "Söylediğin bölge için anonim fiyat verisi bulamadım. Bölge adını Zeytin Ligi’ndeki gibi söyleyebilir "
                + "ya da kendi bölgeni sormak için yalnızca “1 numara kaç para?” diyebilirsin.";
    }

    /**
     * @param region          bölge adı ("Alaşehir / Manisa")
     * @param regionProducers bölgedeki farklı üretici sayısı
     * @param regionPublished sunucu bölgeyi yayımladı mı
     * @param asked           sorulan elek (rapordaki ad) ya da boş
     */
    static String answer(String region, int regionProducers, boolean regionPublished, List<Grade> grades, String asked) {
        String place = MeydanMarketStrip.shortRegion(region);
        if (place.isEmpty()) place = "Bölgen";
        int floor = MarketBackendConfig.PUBLIC_MARKET_MIN_PRODUCERS;
        if (!regionPublished || regionProducers < floor) {
            return place + " bölgesinde fiyatlar henüz paylaşılmıyor: gizlilik için en az " + floor
                    + " üretici gerekiyor, şu an " + Math.min(Math.max(0, regionProducers), floor) + ".";
        }
        String tail = " Anonim bölge verisidir; fiyat garantisi değildir.";
        if (asked != null && !asked.isEmpty()) {
            Grade g = find(grades, asked);
            if (g == null || !g.visible()) {
                int n = g == null ? 0 : Math.min(g.producers, floor);
                return place + ", " + asked + ": bu elekte fiyat paylaşılmıyor; en az " + floor + " üretici gerekiyor, şu an " + n + "." + tail;
            }
            return place + ", " + asked + ": bu sezon medyan " + RegionGradePrice.format(g.price()) + " TL/kg ("
                    + g.producers + " üretici)." + trend(g) + tail;
        }
        List<String> shown = new ArrayList<>();
        int hidden = 0;
        for (String name : GRADES) {
            Grade g = find(grades, name);
            if (g != null && g.visible()) shown.add(name + " " + RegionGradePrice.format(g.price()));
            else hidden++;
        }
        if (shown.isEmpty()) {
            return place + " bölgesinde hiçbir elekte " + floor + " üreticiye ulaşılmadı; fiyat söyleyemiyorum." + tail;
        }
        return place + ", bu sezon medyan fiyatlar: " + String.join(", ", shown) + " TL/kg."
                + (hidden > 0 ? " Diğer eleklerde yeterli üretici yok." : "") + tail;
    }

    private static String trend(Grade g) {
        if (!g.trend || !valid(g.trendPct)) return "";
        double abs = Math.abs(g.trendPct);
        if (abs < 0.5) return " Son 7 günde yatay.";
        return " Son 7 günde %" + RegionGradePrice.format(abs) + (g.trendPct > 0 ? " arttı." : " azaldı.");
    }

    private static Grade find(List<Grade> grades, String name) {
        if (grades == null) return null;
        for (Grade g : grades) if (g != null && g.name.equalsIgnoreCase(name)) return g;
        return null;
    }

    private static boolean valid(double v) { return !Double.isNaN(v) && !Double.isInfinite(v) && v > -1e9; }
}
