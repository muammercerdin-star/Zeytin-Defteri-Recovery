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

    // ------------------------------------------------------------------
    // Elek sekmesi (özet sayfası)
    // ------------------------------------------------------------------
    private static final String[] MONTHS = {"Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
            "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"};

    /** En çok kilo çıkan sınıfın adı; kayıt yoksa "—". Eşitlikte üst sınıf seçilir. */
    public static String topGrade(double[] kg) {
        int best = -1;
        for (int i = 0; i < kg.length && i < GRADE_NAMES.length; i++) {
            if (kg[i] > 0 && (best < 0 || kg[i] > kg[best])) best = i;
        }
        return best < 0 ? "—" : GRADE_NAMES[best];
    }

    /** "2026-10-13" -> "13 Ekim 2026" (shortMonth: "13 Eki"). Tanınmayan biçim olduğu gibi döner. */
    public static String formatDate(String iso, boolean shortMonth) {
        if (iso == null) return "";
        String v = iso.trim();
        try {
            if (v.length() >= 10 && v.charAt(4) == '-' && v.charAt(7) == '-') {
                int year = Integer.parseInt(v.substring(0, 4));
                int month = Integer.parseInt(v.substring(5, 7));
                int day = Integer.parseInt(v.substring(8, 10));
                if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                    String name = MONTHS[month - 1];
                    return shortMonth
                            ? day + " " + name.substring(0, Math.min(3, name.length()))
                            : day + " " + name + " " + year;
                }
            }
        } catch (NumberFormatException ignored) {
        }
        return v;
    }

    /** "50,1 TL/kg"; kilo yoksa "— TL/kg". */
    public static String formatPerKg(double revenue, double kg) {
        if (kg <= 0) return "— TL/kg";
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(new java.util.Locale("tr", "TR"));
        nf.setMinimumFractionDigits(1);
        nf.setMaximumFractionDigits(1);
        return nf.format(revenue / kg) + " TL/kg";
    }

    /**
     * Makbuzun paylaşım metni (WhatsApp vb.). Sadece kilo girilen sınıflar yazılır.
     * kg/price/total dizileri GRADE_NAMES sırasındadır; tutarlar hazır biçimli gelir.
     */
    public static String receiptShareText(String garden, String date, double[] kg, String[] priceText,
                                          String[] totalText, String totalKg, String totalTl,
                                          String average, String note) {
        StringBuilder out = new StringBuilder("Zeytin Defteri · Elek makbuzu\n");
        out.append(garden).append(" · ").append(date).append("\n\n");
        for (int i = 0; i < kg.length && i < GRADE_NAMES.length; i++) {
            if (kg[i] <= 0) continue;
            out.append(GRADE_NAMES[i]).append(": ")
                    .append(formatKgPlain(kg[i])).append(" kg × ")
                    .append(priceText[i]).append(" = ").append(totalText[i]).append('\n');
        }
        out.append("\nToplam: ").append(totalKg).append(" · ").append(totalTl);
        if (average != null && !average.isEmpty()) out.append(" (ort. ").append(average).append(')');
        if (note != null && !note.trim().isEmpty()) out.append("\nNot: ").append(note.trim());
        return out.toString();
    }

    private static String formatKgPlain(double value) {
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(new java.util.Locale("tr", "TR"));
        nf.setMaximumFractionDigits(2);
        return nf.format(value);
    }

    /** "Salı" -> "SAL"; Cuma/Cumartesi gibi çakışmalar için sabit kısaltmalar. */
    public static String shortWeekday(String dayName) {
        if (dayName == null) return "";
        switch (dayName.trim()) {
            case "Pazartesi": return "PZT";
            case "Salı": return "SAL";
            case "Çarşamba": return "ÇAR";
            case "Perşembe": return "PER";
            case "Cuma": return "CUM";
            case "Cumartesi": return "CMT";
            case "Pazar": return "PAZ";
            default: return "";
        }
    }

    /** "2026-10-13" -> "EKİM 2026" (ay başlığı); tanınmazsa boş. */
    public static String monthHeader(String iso) {
        if (iso == null || iso.length() < 7 || iso.charAt(4) != '-') return "";
        try {
            int year = Integer.parseInt(iso.substring(0, 4));
            int month = Integer.parseInt(iso.substring(5, 7));
            if (month < 1 || month > 12) return "";
            return MONTHS[month - 1].toUpperCase(new java.util.Locale("tr", "TR")) + " " + year;
        } catch (NumberFormatException e) {
            return "";
        }
    }

    /** "12 KAYIT" */
    public static String recordCountOverline(int count) {
        return count + " KAYIT";
    }

    /** Ana Sayfa'daki aktif bahçe seçiliyse "Bahçe · aktif". */
    public static String gardenLabel(long selectedId, long activeId) {
        return selectedId > 0 && selectedId == activeId ? "Bahçe · aktif" : "Bahçe";
    }
}
