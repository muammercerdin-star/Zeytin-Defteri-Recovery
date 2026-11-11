package com.zeytindefteri.app;

/**
 * İşlem (masraf) silme onayının metni (Android'e bağlı değil, test edilir).
 * Hasat hareketi tek başına silinmez: bağlı hasat kaydıyla birlikte gider (kilo ve masraf kopmasın).
 */
public final class OperationDeleteText {

    private OperationDeleteText() { }

    public static String title(boolean linkedHarvest) {
        return linkedHarvest ? "Hasat kaydı silinsin mi?" : "İşlem silinsin mi?";
    }

    public static String message(String label, String garden, String date, double cost, boolean linkedHarvest) {
        StringBuilder b = new StringBuilder();
        b.append(clean(label));
        String g = clean(garden);
        String d = clean(date);
        if (!g.isEmpty()) b.append(" · ").append(g);
        if (!d.isEmpty()) b.append(" · ").append(d);
        if (cost > 0.009) {
            b.append("\n").append(OperationHistoryModel.money(cost)).append(" masraf toplamdan düşülür.");
        }
        if (linkedHarvest) {
            b.append("\n\nBu hareket bir hasat kaydına bağlı; hasat kaydı (kilolarıyla) birlikte silinir.")
             .append(" Elek kayıtlarına dokunulmaz.");
        }
        b.append("\n\nDiğer telefonlarından da silinir. Geri alınamaz.");
        return b.toString();
    }

    public static String done(boolean linkedHarvest) {
        return linkedHarvest ? "Hasat kaydı silindi" : "İşlem silindi";
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }
}
