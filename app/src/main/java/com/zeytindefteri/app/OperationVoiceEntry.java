package com.zeytindefteri.app;

import java.util.regex.Pattern;

/**
 * Asistana söylenen bahçe işi cümlesi → işlem formu (saf, Android'siz, testli).
 *
 * "Bugün Girelli'yi ilaçladım", "Dün 2 saat su verdim", "18 kilo gübre attık", "Mazot aldım 500 lira",
 * "3 işçi çalıştı 1500 lira". Tür, tarih (bugün / dün / evvelsi gün), miktar + birim ve tutar çıkarılır;
 * bahçe adını asistan kendi eşleştirir. Soru cümlesi ("Son ilaçlama ne zaman?") kayıt sayılmaz → null.
 * Kayıt hiçbir zaman kendiliğinden yapılmaz: form dolu açılır, kullanıcı Kaydet'e basar.
 */
public final class OperationVoiceEntry {
    /** OperationKinds.TYPES içindeki sıra. */
    public int type = -1;
    /** 0 bugün, -1 dün, -2 evvelsi gün. */
    public int dayOffset;
    public double quantity;
    public String unit = "";
    public double cost;

    private static final Pattern QUESTION = Pattern.compile(
            "(^|\\s)(kac|ne|nedir|nasil|hangi|hangisi|mi|mu|misin|musun|zaman|ne zaman|kaldi|gecti)(\\s|$)");
    /** Geçmiş zaman / yapıldı sözü: kayıt cümlesi olduğunu gösterir. */
    private static final Pattern DONE = Pattern.compile(
            "\\b\\p{L}*(dim|dik|tim|tik|dum|duk|tum|tuk|dum|dim|di|ti|du|tu|ldi|ndi)\\b|\\b(yaptim|yaptik|yapildi|attim|attik|verdim|verdik|"
                    + "aldim|aldik|astim|astik|koydum|koyduk|doldurdum|calisti|calistilar|tuttum|tuttuk|bitti|bitirdim)\\b");

    private OperationVoiceEntry() {}

    /** Kayıt cümlesi değilse null. */
    public static OperationVoiceEntry parse(String raw) {
        if (raw == null || raw.contains("?")) return null;
        String s = ElekVoiceEntry.normalize(raw);
        if (s.isEmpty() || QUESTION.matcher(s).find()) return null;
        int type = typeOf(s);
        if (type < 0) return null;
        if (!DONE.matcher(s).find() && !s.matches(".*\\b(bugun|dun|evvelsi|onceki)\\b.*")) return null;
        OperationVoiceEntry e = new OperationVoiceEntry();
        e.type = type;
        if (s.matches(".*\\b(evvelsi|evvelki|onceki gun|obur gun)\\b.*")) e.dayOffset = -2;
        else if (s.matches(".*\\bdun\\b.*")) e.dayOffset = -1;
        amounts(s.split(" "), e);
        return e;
    }

    /** Tür: söz kökünden. Sıra önemli: "ilaç" sulamadan önce ("ilaçlı su"), "mazot" nakliyeden önce. */
    static int typeOf(String s) {
        if (s.matches(".*\\b(ilac\\p{L}*|ilacla\\p{L}*|spreyle\\p{L}*)\\b.*")) return 2;
        if (s.matches(".*\\b(gubre\\p{L}*)\\b.*")) return 1;
        if (s.matches(".*\\b(sula\\p{L}*|damlama\\p{L}*)\\b.*") || s.matches(".*\\bsu (verdim|verdik|saldim|saldik|actim|actik|verildi)\\b.*")) return 0;
        if (s.matches(".*\\b(buda\\p{L}*)\\b.*")) return 3;
        if (s.matches(".*\\b(tuzak\\p{L}*)\\b.*")) return 4;
        if (s.matches(".*\\b(surdum|surduk|surduruldu|capala\\p{L}*|freze\\p{L}*|toprak isle\\p{L}*|toprak islemesi|diskaro\\p{L}*|kultivator\\p{L}*)\\b.*")) return 5;
        if (s.matches(".*\\b(isci\\p{L}*|yevmiye\\p{L}*|iscilik)\\b.*")) return 6;
        if (s.matches(".*\\b(mazot\\p{L}*|yakit\\p{L}*|motorin\\p{L}*)\\b.*")) return 7;
        if (s.matches(".*\\b(nakliye\\p{L}*|tasidim|tasidik|tasittim|kamyon\\p{L}*|traktorle tasi\\p{L}*)\\b.*")) return 8;
        return -1;
    }

    /** "2 saat", "18 kilo", "500 ml", "25 litre", "3 işçi", "500 lira". */
    private static void amounts(String[] t, OperationVoiceEntry e) {
        for (int i = 0; i < t.length; i++) {
            int[] end = new int[1];
            double v = ElekVoiceEntry.number(t, i, end);
            if (Double.isNaN(v) || v <= 0) continue;
            String next = end[0] < t.length ? t[end[0]] : "";
            String u = unit(next);
            if (u.equals("lira")) { if (e.cost <= 0) e.cost = v; }
            else if (!u.isEmpty() && e.quantity <= 0) { e.quantity = v; e.unit = u; }
            i = Math.max(i, end[0] - 1);
        }
    }

    static String unit(String w) {
        if (w.matches("(lira\\p{L}*|tl|tlye|tllik)")) return "lira";
        if (w.matches("saat\\p{L}*")) return "saat";
        if (w.matches("(kilo\\p{L}*|kg)")) return "kg";
        if (w.matches("(gram\\p{L}*|gr|g)")) return "g";
        if (w.matches("(litre\\p{L}*|lt|l)")) return "L";
        if (w.matches("(ml|mililitre\\p{L}*|militre\\p{L}*|cc)")) return "ml";
        if (w.matches("(ton\\p{L}*)")) return "ton";
        if (w.matches("(km|kilometre\\p{L}*)")) return "km";
        if (w.matches("(sefer\\p{L}*)")) return "sefer";
        if (w.matches("(gun\\p{L}*)")) return "gün";
        if (w.matches("(kisi\\p{L}*|isci\\p{L}*)")) return "kişi";
        if (w.matches("(agac\\p{L}*)")) return "ağaç";
        if (w.matches("(dekar\\p{L}*|donum\\p{L}*)")) return "dekar";
        if (w.matches("(adet\\p{L}*|tane\\p{L}*)")) return "adet";
        if (w.matches("(ton\\p{L}*)")) return "ton";
        if (w.matches("(metrekup\\p{L}*|kup)")) return "m³";
        return "";
    }

    /** Okuma: "ilaçlama, bugün, 500 ml, 300 lira". */
    public String summary() {
        StringBuilder b = new StringBuilder(OperationKinds.TYPES[type].toLowerCase(new java.util.Locale("tr", "TR")));
        b.append(", ").append(dayOffset == 0 ? "bugün" : dayOffset == -1 ? "dün" : "evvelsi gün");
        if (quantity > 0) b.append(", ").append(ElekVoiceDialog.num(quantity)).append(' ').append(unit);
        if (cost > 0) b.append(", ").append(ElekVoiceDialog.num(cost)).append(" lira");
        return b.toString();
    }
}
