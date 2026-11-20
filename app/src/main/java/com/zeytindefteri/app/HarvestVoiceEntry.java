package com.zeytindefteri.app;

/**
 * "Girelli'den 300 kilo topladık", "Bugün 3 numara 120, 2 numara 80 hasat ettik": hasat formu için
 * (saf, testli). Boy söylenmişse boy boy kilo; söylenmemişse yalnız toplam (formda boylara ayrılır).
 * Soru ("Bu sezon kaç kilo topladım?") kayıt değildir → null. Form dolu açılır, kaydetmez.
 */
public final class HarvestVoiceEntry {
    public final double[] kg = new double[6];
    public double total;
    public int dayOffset;

    private HarvestVoiceEntry() {}

    public static HarvestVoiceEntry parse(String raw) {
        if (raw == null || raw.contains("?")) return null;
        String s = TurkishText.normalize(raw);
        // Eğitim turu 1: "silktik", "topluyoruz" da hasat sözü.
        if (!s.matches(".*\\b(topla\\p{L}*|toplu(yoruz|yorum|yor)|hasat\\p{L}*|cirp\\p{L}*|silkele\\p{L}*|silk(tik|tim|ti)|devsir\\p{L}*)\\b.*")) return null;
        if (s.matches(".*(^|\\s)(kac|ne|nedir|nasil|hangi|hangisi|mi|mu|zaman)(\\s|$).*")) return null;
        if (s.matches(".*\\b(sattim|sattik|satildi|verdim)\\b.*")) return null;   // satış: elek/satış formu
        HarvestVoiceEntry h = new HarvestVoiceEntry();
        if (s.matches(".*\\b(evvelsi|evvelki|onceki gun)\\b.*")) h.dayOffset = -2;
        else if (s.matches(".*\\bdun\\b.*")) h.dayOffset = -1;
        ElekVoiceEntry graded = ElekVoiceEntry.parse(raw.replaceAll("(?iu)\\b(topla|toplu|hasat|çırp|cirp|silkele|silkt|devşir)\\p{L}*", " "));
        if (graded != null) {
            for (int i = 0; i < 6; i++) { h.kg[i] = graded.kg[i]; h.total += graded.kg[i]; }
            return h.total > 0 ? h : null;
        }
        String[] t = s.split(" ");
        for (int i = 0; i < t.length; i++) {
            int[] end = new int[1];
            double v = TurkishText.number(t, i, end);
            if (Double.isNaN(v) || v <= 0) continue;
            String next = end[0] < t.length ? t[end[0]] : "";
            String unit = TurkishText.unit(next);
            if (unit.equals(TurkishText.KILO)) { h.total = v; break; }
            if (unit.equals("ton")) { h.total = v * 1000; break; }
            i = Math.max(i, end[0] - 1);
        }
        return h.total > 0 ? h : null;
    }

    public boolean graded() { for (double v : kg) if (v > 0) return true; return false; }
}
