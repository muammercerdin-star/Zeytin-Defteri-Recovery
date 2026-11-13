package com.zeytindefteri.app;

/**
 * Elek fişinde tarih düzeltme kuralları (Android'e bağlı değil, test edilir).
 * Elek kaydı yyyy-aa-gg, bağlı satış gg.aa.yyyy saklar; ikisi birlikte değişir, sezon yılı da tarihle gider.
 */
public final class ElekDateFix {

    private ElekDateFix() { }

    /** yyyy-aa-gg mi ve gerçek bir gün mü? */
    public static boolean valid(String iso) {
        if (iso == null || !iso.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        int y = Integer.parseInt(iso.substring(0, 4));
        int m = Integer.parseInt(iso.substring(5, 7));
        int d = Integer.parseInt(iso.substring(8, 10));
        if (y < 2000 || y > 2100 || m < 1 || m > 12 || d < 1) return false;
        int[] days = {31, (y % 4 == 0 && (y % 100 != 0 || y % 400 == 0)) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        return d <= days[m - 1];
    }

    /** Hata metni; uygunsa null. Gelecek gün yazılamaz. */
    public static String problem(String oldIso, String newIso, String todayIso) {
        if (!valid(newIso)) return "Geçersiz tarih.";
        if (valid(todayIso) && newIso.compareTo(todayIso) > 0) return "İleri bir tarih seçilemez.";
        if (newIso.equals(oldIso)) return "Tarih zaten bu.";
        return null;
    }

    public static int year(String iso) {
        return valid(iso) ? Integer.parseInt(iso.substring(0, 4)) : 0;
    }

    /** Onay penceresindeki açıklama. */
    public static String confirm(String oldText, String newText, boolean hasSale, String oldIso, String newIso) {
        StringBuilder b = new StringBuilder();
        b.append(oldText).append("  →  ").append(newText);
        if (hasSale) b.append("\n\nBağlı satışın tarihi de değişir; tutar ve ödemeler aynı kalır.");
        int from = year(oldIso);
        int to = year(newIso);
        if (from > 0 && to > 0 && from != to) b.append("\n\nKayıt ").append(to).append(" sezonuna taşınır.");
        return b.toString();
    }
}
