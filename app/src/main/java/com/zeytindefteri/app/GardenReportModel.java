package com.zeytindefteri.app;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Bahçe Karnesi görünümünün saf (Android'siz) metin ve oran kuralları.
 * Hesaplar GardenAnalyticsSnapshot'tadır; burada yalnızca gösterim vardır.
 */
public final class GardenReportModel {
    private GardenReportModel() {}

    public enum Tone { GOOD, BAD, NEUTRAL, MUTED }

    private static final Locale TR = new Locale("tr", "TR");

    private static NumberFormat nf() {
        NumberFormat f = NumberFormat.getNumberInstance(TR);
        f.setMinimumFractionDigits(0);
        f.setMaximumFractionDigits(2);
        return f;
    }

    /** Değer + birim; null/sonsuz → "—". */
    public static String format(Double value, String unit) {
        if (value == null || value.isNaN() || value.isInfinite()) return "—";
        return nf().format(value) + (unit == null || unit.isEmpty() ? "" : " " + unit);
    }

    /** Karşılaştırma değişimi: "↑ %12", "↓ %3,5", "%0"; veri yoksa "—". */
    public static String change(Double percent) {
        if (percent == null || percent.isNaN() || percent.isInfinite()) return "—";
        if (percent > 0) return "↑ %" + nf().format(percent);
        if (percent < 0) return "↓ %" + nf().format(-percent);
        return "%0";
    }

    /**
     * Değişimin rengi. Masraf ve kg başı maliyette artış kötüdür; diğerlerinde artış iyidir.
     * Boy kiloları gibi yönü yorumlanmayan satırlar NEUTRAL.
     */
    public static Tone changeTone(Double percent, Boolean higherIsBetter) {
        if (percent == null || percent.isNaN() || percent.isInfinite()) return Tone.MUTED;
        if (higherIsBetter == null || percent == 0) return Tone.NEUTRAL;
        boolean up = percent > 0;
        return up == higherIsBetter ? Tone.GOOD : Tone.BAD;
    }

    /** Net sonucun rengi: satış yoksa soluk, negatifse kötü, aksi halde iyi. */
    public static Tone netTone(double net, int saleRecords) {
        if (saleRecords <= 0) return Tone.MUTED;
        return net < 0 ? Tone.BAD : Tone.GOOD;
    }

    /** Boyun toplam içindeki payı (yuvarlanmış yüzde). */
    public static int share(double kg, double total) {
        if (total <= 0 || kg <= 0) return 0;
        return (int) Math.round(100.0 * kg / total);
    }

    /** Çubuk uzunluğu: en büyük boya göre; kilo varsa en az %3 görünür. */
    public static int bar(double kg, double max) {
        if (kg <= 0 || max <= 0) return 0;
        return Math.max(3, Math.min(100, (int) Math.round(100.0 * kg / max)));
    }

    /** Tahsil edilen payı (0–100); ikisi de sıfırsa -1 (çubuk gösterilmez). */
    public static int collectedPercent(double collected, double receivable) {
        double total = Math.max(0, collected) + Math.max(0, receivable);
        if (total <= 0) return -1;
        return (int) Math.round(100.0 * Math.max(0, collected) / total);
    }

    /** Hasat–elek uyumsuzluk metni (emoji ve uyarı işareti yok; ikon görünümde). */
    public static String mismatch(double harvest, double elek, double differenceKg, double excess, double missing) {
        String base = "Hasat toplamı " + format(harvest, "kg") + ", elek kayıtları toplamı " + format(elek, "kg") + ". ";
        return differenceKg > 0
                ? base + "Elek kayıtları hasattan " + format(excess, "kg") + " fazla. Kayıtlarını kontrol et."
                : base + "Elek kayıtları hasattan " + format(missing, "kg") + " eksik. Kayıtlarını kontrol et.";
    }
}
