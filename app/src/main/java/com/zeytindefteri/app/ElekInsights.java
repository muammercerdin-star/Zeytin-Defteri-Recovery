package com.zeytindefteri.app;

/**
 * Elek kaydı ekranının özet kartı için saf hesaplamalar (dağılım payları, açıklama, kaydet ipucu).
 * Tutar/ortalama hesapları ElekActivity.calculateAll içinde kalır; burası yalnızca gösterimdir.
 */
public final class ElekInsights {
    private ElekInsights() {}

    public static final String[] GRADE_NAMES = {"Double", "1 Numara", "2 Numara", "3 Numara", "4 Numara", "Yağlık"};
    public static final String[] GRADE_SHORT = {"Double", "1", "2", "3", "4", "Yağlık"};

    public static double total(double[] kg) {
        double sum = 0;
        for (double v : kg) if (v > 0) sum += v;
        return sum;
    }

    /** Her sınıfın toplam içindeki yüzdesi (yuvarlanmış, toplam 100'e tamamlanır). Toplam 0 ise hepsi 0. */
    public static int[] shares(double[] kg) {
        int[] out = new int[kg.length];
        double sum = total(kg);
        if (sum <= 0) return out;
        int assigned = 0, largest = -1;
        for (int i = 0; i < kg.length; i++) {
            double v = Math.max(0, kg[i]);
            out[i] = (int) Math.round(100.0 * v / sum);
            assigned += out[i];
            if (v > 0 && (largest < 0 || v > kg[largest])) largest = i;
        }
        if (largest >= 0) out[largest] += 100 - assigned;
        return out;
    }

    /** "Double %40 · 1 %35 · Yağlık %25"; miktar yoksa "Henüz miktar girilmedi". */
    public static String legend(double[] kg) {
        int[] s = shares(kg);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < kg.length && i < GRADE_SHORT.length; i++) {
            if (kg[i] <= 0) continue;
            if (out.length() > 0) out.append("  ·  ");
            out.append(GRADE_SHORT[i]).append(" %").append(s[i]);
        }
        return out.length() == 0 ? "Henüz miktar girilmedi" : out.toString();
    }

    /** Ekran okuyucu için: "Sınıf dağılımı: Double yüzde 40, 1 Numara yüzde 35." */
    public static String accessibleLegend(double[] kg) {
        int[] s = shares(kg);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < kg.length && i < GRADE_NAMES.length; i++) {
            if (kg[i] <= 0) continue;
            out.append(out.length() == 0 ? "Sınıf dağılımı: " : ", ");
            out.append(GRADE_NAMES[i]).append(" yüzde ").append(s[i]);
        }
        return out.length() == 0 ? "Henüz miktar girilmedi." : out.append('.').toString();
    }

    /** Kaydet butonunun altındaki ipucu. */
    public static String saveHint(double totalKg, boolean gardenSelected, String garden, String date) {
        if (totalKg <= 0) return "Kaydetmek için en az bir sınıfa kilo gir.";
        if (!gardenSelected) return "Kaydetmeden önce bahçe seç.";
        return garden + " · " + date + " olarak kaydedilecek.";
    }

    /** Ana Sayfa'daki aktif bahçe seçiliyse "Bahçe · aktif". */
    public static String gardenLabel(long selectedId, long activeId) {
        return selectedId > 0 && selectedId == activeId ? "Bahçe · aktif" : "Bahçe";
    }
}
