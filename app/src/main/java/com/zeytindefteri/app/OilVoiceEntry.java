package com.zeytindefteri.app;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Yağhane cümlesi (eğitim turu 1; saf, Android'siz, testli).
 * "fabrikaya 40 kasa götürdük" → kasa=40 · "yağhaneden 120 kilo yağ aldık" → yag=120 (kilo; litre söylenirse birim=litre).
 *
 * Yağhane (fabrika) sözü ile zeytin bağlamı (kasa, kilo zeytin, yağ) birlikte gerekir; "fabrika" tek başına yetmez.
 * Kasa kiloya çevrilmez: formdaki nota "40 kasa" yazılır, zeytin kilosu ve yağhane adı formda sorulur. Hiçbir şey
 * yazmaz: yağhane ekranının sıkım formu dolu açılır, kayıt çiftçinin Kaydet'iyle yapılır.
 */
final class OilVoiceEntry {
    static final String LITRE = "litre";

    private static final String MILL = "(fabrika|yaghane|degirmen)\\p{L}*";
    private static final String BRING = "(goturduk|goturdum|verdik|verdim|biraktik|biraktim|teslim ettik|teslim ettim|"
            + "tasidik|tasidim|yolladik|yolladim|gonderdik|gonderdim)";
    private static final String GOT = "(aldik|aldim|cikti|geldi|cikardik|cikardi)";
    private static final String QUESTION = ".*\\b(kac|ne kadar|ne zaman|nereye|hangi|mi|mu|midir|mudur)\\b.*";

    /** Söylenen kasa sayısı (kiloya çevrilmez); yoksa 0. */
    final double crates;
    /** Söylenen zeytin kilosu; yoksa 0. */
    final double oliveKg;
    /** Söylenen yağ miktarı; yoksa 0. Birimi oilUnit. */
    final double oil;
    /** "" (kilo) ya da LITRE. */
    final String oilUnit;
    /** Söylenen yağhane adı ("Akhisar yağhanesine" → "Akhisar"); yoksa "". */
    final String mill;

    private OilVoiceEntry(double crates, double oliveKg, double oil, String oilUnit, String mill) {
        this.crates = crates;
        this.oliveKg = oliveKg;
        this.oil = oil;
        this.oilUnit = oilUnit;
        this.mill = mill;
    }

    /** Yağhane cümlesi mi; değilse null. */
    static OilVoiceEntry parse(String raw) {
        if (raw == null || raw.contains("?")) {
            return null;
        }
        String s = TurkishText.normalize(raw);
        if (s.matches(QUESTION) || !s.matches(".*\\b" + MILL + "\\b.*")) {
            return null;
        }
        boolean bring = s.matches(".*\\b" + BRING + "\\b.*");
        boolean got = s.matches(".*\\byag\\p{L}* " + GOT + "\\b.*") || s.matches(".*\\byag\\b.*\\b" + GOT + "\\b.*");
        if (!bring && !got) {
            return null;
        }
        double crates = 0;
        double kilos = 0;
        double litres = 0;
        double kilosBeforeOil = 0;
        String[] t = TurkishText.words(raw);
        for (TurkishText.Quantity q : TurkishText.quantities(raw)) {
            String next = q.end < t.length ? t[q.end] : "";
            boolean oilNext = next.matches("yag\\p{L}*") && !next.matches("yaglik\\p{L}*");
            if (TurkishText.CRATE.equals(q.unit)) {
                crates = q.value;
            } else if (TurkishText.KILO.equals(q.unit) && oilNext) {
                kilosBeforeOil = q.value;
            } else if (TurkishText.KILO.equals(q.unit)) {
                kilos = q.value;
            } else if (TurkishText.LITRE.equals(q.unit)) {
                litres = q.value;
            }
        }
        double oil = litres > 0 ? litres : kilosBeforeOil;
        String unit = litres > 0 ? LITRE : "";
        if (got && oil <= 0) {
            return null;
        }
        if (!got && crates <= 0 && kilos <= 0) {
            return null;
        }
        return new OilVoiceEntry(crates, kilos, got ? oil : 0, got ? unit : "", mill(raw));
    }

    /** "Akhisar yağhanesine", "Kemal'in fabrikasına" → "Akhisar" / "Kemal"; yoksa "". */
    static String mill(String raw) {
        String[] w = raw.trim().split("\\s+");
        for (int i = 1; i < w.length; i++) {
            String n = TurkishText.normalize(w[i]);
            if (!n.matches(MILL)) {
                continue;
            }
            String prev = w[i - 1].replaceAll("[,.;:!]+$", "");
            int apo = Math.max(prev.indexOf('\''), prev.indexOf('’'));
            String name = apo > 0 ? prev.substring(0, apo) : prev;
            if (!name.isEmpty() && Character.isUpperCase(name.charAt(0)) && name.matches("\\p{L}+")) {
                return name;
            }
        }
        return "";
    }

    /** Karar alanları: kasa, zeytin, yag, birim, yaghane. */
    Map<String, String> fields() {
        Map<String, String> f = new LinkedHashMap<>();
        if (crates > 0) {
            f.put("kasa", ElekVoiceDialog.num(crates));
        }
        if (oliveKg > 0) {
            f.put("zeytin", ElekVoiceDialog.num(oliveKg));
        }
        if (oil > 0) {
            f.put("yag", ElekVoiceDialog.num(oil));
        }
        if (!oilUnit.isEmpty()) {
            f.put("birim", oilUnit);
        }
        if (!mill.isEmpty()) {
            f.put("yaghane", mill);
        }
        return f;
    }

    /** Formun notu: kasa kiloya çevrilmeden "40 kasa"; yoksa "". */
    String note() {
        return crates > 0 ? ElekVoiceDialog.num(crates) + " kasa" : "";
    }

    /** Yağ henüz çıkmadı mı (zeytin götürüldü, yağ söylenmedi): "Yağ çıkınca hatırlatayım mı?" önerilir. */
    boolean waitingOil() {
        return oil <= 0;
    }

    /** Okunan / gösterilen özet: "40 kasa zeytin", "120 kilo yağ". */
    String summary() {
        StringBuilder b = new StringBuilder();
        if (crates > 0) {
            b.append(ElekVoiceDialog.num(crates)).append(" kasa zeytin");
        } else if (oliveKg > 0) {
            b.append(ElekVoiceDialog.num(oliveKg)).append(" kilo zeytin");
        }
        if (oil > 0) {
            if (b.length() > 0) {
                b.append(", ");
            }
            b.append(ElekVoiceDialog.num(oil)).append(LITRE.equals(oilUnit) ? " litre yağ" : " kilo yağ");
        }
        return b.toString();
    }
}
