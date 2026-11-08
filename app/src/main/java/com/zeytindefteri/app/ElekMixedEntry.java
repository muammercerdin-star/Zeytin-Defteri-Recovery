package com.zeytindefteri.app;

import java.util.Locale;

/**
 * Sınıfsız ("toplam") elek kaydı: üretici yalnız toplam kiloyu ve aldığı parayı biliyor
 * (ör. sezon ortasında kuran biri, eski satışlarını "150 kg, 8.000 TL" diye hatırlıyor).
 *
 * Saklama: elek_records'a normal kayıt + elek_grades'e tek satır, grade = {@link #GRADE}.
 * Böylece toplamları kullanan her yer (hasat, satış, sezon raporu, bahçe karnesi, tahsilat) doğru çalışır.
 * Sınıfa bakan hesaplar (bölge elek fiyatları, piyasa katkısı, lig kalitesi) bu satırı tanımaz ve atlar:
 * sınıfı bilinmeyen fiyat, "1 numara fiyatı" gibi bölge ortalamalarına karışmamalı.
 *
 * Saf sınıf (Android yok): test edilir.
 */
public final class ElekMixedEntry {
    /** elek_grades / sale_grades'deki kod. Piyasa API'sindeki elek listesinde YOKTUR (bilerek). */
    public static final String GRADE = "KARISIK";
    public static final String LABEL = "Karışık";
    /** Zeytinde gerçekçi kilo fiyatı aralığı; dışı büyük ihtimalle yazım hatası (fazla/eksik sıfır). */
    static final double MIN_PRICE = 1, MAX_PRICE = 2000;
    static final double MAX_KG = 1000000;

    private ElekMixedEntry() {}

    public static boolean isMixed(String grade) {
        return grade != null && GRADE.equalsIgnoreCase(grade.trim());
    }

    /** Kilo fiyatı; kilo yoksa 0. */
    public static double pricePerKg(double kg, double total) {
        if (!(kg > 0) || !(total > 0) || Double.isInfinite(kg) || Double.isInfinite(total)) return 0;
        return total / kg;
    }

    /** Kaydı engelleyen hata; yoksa "". */
    public static String error(double kg, double total) {
        if (!(kg > 0)) return "Toplam kiloyu yaz.";
        if (kg > MAX_KG) return "Kilo çok büyük görünüyor. Kontrol et.";
        if (!(total > 0)) return "Aldığın (ya da alacağın) toplam tutarı yaz.";
        double price = pricePerKg(kg, total);
        if (price < MIN_PRICE || price > MAX_PRICE) {
            return "Kilo fiyatı " + money(price) + " TL/kg çıkıyor. Kiloyu ve tutarı kontrol et.";
        }
        return "";
    }

    /** "150 kg · 8.000,00 TL → kilo fiyatı 53,33 TL/kg": üretici, yazdığının nasıl okunduğunu görür. */
    public static String priceLine(double kg, double total) {
        double price = pricePerKg(kg, total);
        if (price <= 0) return "Kilo fiyatı kendiliğinden hesaplanır.";
        return kgText(kg) + " kg · " + money(total) + " TL → kilo fiyatı " + money(price) + " TL/kg";
    }

    /** addElekRecord için tek satırlık sınıf/kilo/fiyat dizileri. */
    public static String[] grades() {
        return new String[]{GRADE};
    }

    public static double[] kilograms(double kg) {
        return new double[]{kg};
    }

    public static double[] prices(double kg, double total) {
        return new double[]{pricePerKg(kg, total)};
    }

    /** Liste/kart etiketi: "Karışık · 150 kg · 53,33 TL/kg". */
    public static String summary(double kg, double total) {
        StringBuilder out = new StringBuilder(LABEL).append(" · ").append(kgText(kg)).append(" kg");
        double price = pricePerKg(kg, total);
        if (price > 0) out.append(" · ").append(money(price)).append(" TL/kg");
        return out.toString();
    }

    /**
     * Türkçe yazılmış tutar/kilo: "8.000" → 8000, "8.000,50" → 8000.5, "53,5" → 53.5, "8000" → 8000.
     * Tek nokta ve ardından tam üç rakam binlik ayıracı sayılır ("8.000"); "53.5" ondalıktır.
     * Okunamazsa ya da negatifse 0.
     */
    public static double parse(String raw) {
        if (raw == null) return 0;
        String s = raw.trim().replace(" ", "").replace(" ", "").replace("TL", "").replace("₺", "");
        if (s.isEmpty()) return 0;
        boolean dot = s.indexOf('.') >= 0, comma = s.indexOf(',') >= 0;
        if (dot && comma) {
            s = s.replace(".", "").replace(',', '.');
        } else if (comma) {
            s = s.replace(',', '.');
        } else if (dot && s.matches("\\d{1,3}(\\.\\d{3})+")) {
            s = s.replace(".", "");
        }
        if (!s.matches("\\d+(\\.\\d+)?")) return 0;
        try {
            double v = Double.parseDouble(s);
            return Double.isInfinite(v) || v < 0 ? 0 : v;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String money(double value) {
        java.text.DecimalFormatSymbols s = new java.text.DecimalFormatSymbols(new Locale("tr", "TR"));
        s.setGroupingSeparator('.');
        s.setDecimalSeparator(',');
        return new java.text.DecimalFormat("#,##0.00", s).format(value);
    }

    static String kgText(double value) {
        java.text.DecimalFormatSymbols s = new java.text.DecimalFormatSymbols(new Locale("tr", "TR"));
        s.setGroupingSeparator('.');
        s.setDecimalSeparator(',');
        return new java.text.DecimalFormat("#,##0.##", s).format(value);
    }
}
