package com.zeytindefteri.app;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Hasat ve satış formlarının ortak saf (Android'siz) kuralları: boylar, Türkçe sayı okuma,
 * satır tutarı ve satıştaki ödeme seçeneği.
 */
public final class GradeFormModel {
    private GradeFormModel() {}

    /** Veritabanına yazılan boy kodları; sıra formdaki satır sırasıdır. */
    public static final String[] CODES = {"DOUBLE", "1", "2", "3", "4", "YAGLIK"};
    public static final String[] LABELS = {"Double", "1 No", "2 No", "3 No", "4 No", "Yağlık"};

    private static final Locale TR = new Locale("tr", "TR");

    /**
     * Türkçe sayı girişi (negatifler 0 sayılır):
     * "2.400" → 2400, "2.400,50" → 2400.5, "8,5" → 8.5, "8.5" → 8.5, "1.234.567" → 1234567,
     * "TL"/"kg" ve boşluklar yok sayılır, okunamayan değer 0.
     */
    public static double parse(String raw) {
        if (raw == null) return 0;
        String value = raw.replace(" ", "").replace(" ", "")
                .replace("TL", "").replace("tl", "").replace("kg", "").trim();
        if (value.isEmpty()) return 0;
        if (value.contains(".") && value.contains(",")) {
            value = value.replace(".", "").replace(",", ".");
        } else if (value.contains(",")) {
            value = value.replace(",", ".");
        } else if (value.contains(".")) {
            int first = value.indexOf('.'), last = value.lastIndexOf('.');
            if (first != last) value = value.replace(".", "");
            else if (value.length() - last - 1 == 3 && last > 0) value = value.replace(".", "");
        }
        try {
            double d = Double.parseDouble(value);
            return Double.isNaN(d) || Double.isInfinite(d) || d < 0 ? 0 : d;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String number(double value) {
        NumberFormat f = NumberFormat.getNumberInstance(TR);
        f.setMinimumFractionDigits(0);
        f.setMaximumFractionDigits(2);
        return f.format(value);
    }

    public static String money(double value) {
        return number(value) + " TL";
    }

    public static String kg(double value) {
        return number(value) + " kg";
    }

    /** Satır tutarı: kilo ve fiyat varsa tutar, yoksa "—". */
    public static String lineValue(double kg, double price) {
        return kg > 0 && price > 0 ? money(kg * price) : "—";
    }

    /** Satış ödemesi: tamamı alındı, bir kısmı alındı, henüz alınmadı. */
    public enum Payment { FULL, PARTIAL, NONE }

    /** Ödeme seçeneğine göre kaydedilecek tahsilat. Fiyat teklifinde her zaman 0. */
    public static double received(Payment payment, double net, double entered, boolean realSale) {
        if (!realSale) return 0;
        switch (payment) {
            case FULL: return Math.max(0, net);
            case NONE: return 0;
            default: return Math.max(0, entered);
        }
    }

    /** Kayıtlı tahsilattan ödeme seçeneği (formu geri yüklerken). */
    public static Payment paymentFor(double received, double net) {
        if (received <= 0.009) return Payment.NONE;
        if (net > 0 && received >= net - 0.01) return Payment.FULL;
        return Payment.PARTIAL;
    }
}
