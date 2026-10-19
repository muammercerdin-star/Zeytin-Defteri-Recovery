package com.zeytindefteri.app;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Elekten satış oluşturmanın saf (Android'siz) kuralları: tahsilat seçimi, tarih biçimi,
 * bölge, düğme/ipucu metinleri. Veritabanı işi ElekSaleBridge'dedir.
 */
public final class ElekSaleModel {
    private ElekSaleModel() {}

    public static final int PAY_ALL = 0, PAY_PART = 1, PAY_NONE = 2;
    public static final String[] PAY_LABELS = {"Tamamı", "Bir kısmı", "Henüz değil"};
    private static final Locale TR = new Locale("tr", "TR");

    /** Alınan tutar: tamamı=brüt, kısmi=0..brüt arası (sınırlı), henüz değil=0. */
    public static double received(int pay, double gross, double partial) {
        double g = Math.max(0, gross);
        if (pay == PAY_ALL) return g;
        if (pay == PAY_PART) return Math.max(0, Math.min(g, partial));
        return 0;
    }

    /** Kısmi tahsilatta geçerli tutar: sıfırdan büyük ve toplamdan küçük olmalı. Hata yoksa boş. */
    public static String partialError(int pay, double gross, double partial) {
        if (pay != PAY_PART) return "";
        if (!(partial > 0)) return "Alınan tutarı gir.";
        if (partial >= gross) return "Tutar toplamdan küçük olmalı; tamamını aldıysan \"Tamamı\"nı seç.";
        return "";
    }

    /** Satış formu dd.MM.yyyy bekler; elek kaydı ISO (yyyy-MM-dd) ya da eski dd.MM.yyyy olabilir. */
    public static String saleDate(String elekDate) {
        String v = elekDate == null ? "" : elekDate.trim();
        if (v.length() >= 10 && v.charAt(4) == '-' && v.charAt(7) == '-') {
            return v.substring(8, 10) + "." + v.substring(5, 7) + "." + v.substring(0, 4);
        }
        return v;
    }

    /** SaleActivity ile aynı kural: ilçe, yoksa il. */
    public static String region(String district, String province) {
        String d = district == null ? "" : district.trim();
        return !d.isEmpty() ? d : (province == null ? "" : province.trim());
    }

    public static String saveLabel(boolean sold) {
        return sold ? "Kaydet ve satışı oluştur" : "Elek Kaydını Kaydet";
    }

    public static String saveHint(boolean sold, double gross, String garden, String date) {
        if (!sold) return "Yalnızca elek kaydı oluşur; satışa sonra makbuzdan dönüştürebilirsin.";
        if (gross <= 0) return "Elek kaydı ve satış birlikte oluşur.";
        return "Elek kaydı + " + tl(gross) + " satış · " + garden + " · " + date;
    }

    public static String successToast(boolean saleCreated, boolean wanted) {
        if (!wanted) return "Elek kaydı kaydedildi ✓";
        return saleCreated ? "Elek kaydı ve satış kaydedildi ✓"
                : "Elek kaydedildi; satış oluşturulamadı. Makbuzdan \"Satışa dönüştür\" ile tekrar deneyebilirsin.";
    }

    public static String convertedLine(double gross, double received) {
        String pay = received <= 0.009 ? "henüz değil" : received >= gross - 0.009 ? "tamamı alındı" : tl(received) + " alındı";
        return tl(gross) + " · tahsilat: " + pay;
    }

    /** Makbuzdaki tahsilat durumu: "Tamamı alındı", "Henüz alınmadı" ya da "2.000 TL alındı". */
    public static String paymentStatus(double gross, double received) {
        if (received <= 0.009) return "Henüz alınmadı";
        if (received >= gross - 0.009) return "Tamamı alındı";
        return tl(received) + " alındı";
    }

    /** Alıcıda kalan alacak; tamamı alındıysa 0. */
    public static double remaining(double gross, double received) {
        double r = gross - Math.max(0, received);
        return r > 0.009 ? r : 0;
    }

    /** Paylaşılan makbuz metnine eklenen satış satırları. Alıcı adı boşsa "Belirtilmedi" yazar. */
    public static String receiptSaleText(String buyer, String region, String phone, double gross, double received) {
        String name = buyer == null || buyer.trim().isEmpty() ? "Belirtilmedi" : buyer.trim();
        StringBuilder sb = new StringBuilder("\nSatış kaydı");
        sb.append("\nAlıcı: ").append(name);
        if (region != null && !region.trim().isEmpty()) sb.append(" · ").append(region.trim());
        if (phone != null && !phone.trim().isEmpty()) sb.append("\nTelefon: ").append(phone.trim());
        sb.append("\nTahsilat: ").append(paymentStatus(gross, received));
        double rest = remaining(gross, received);
        if (rest > 0 && received > 0.009) sb.append("\nKalan: ").append(tl(rest));
        return sb.toString();
    }

    public static String bulkButton(int count) {
        return count <= 0 ? "Elek seç" : count + " eleği satışa dönüştür";
    }

    public static String tl(double v) {
        NumberFormat nf = NumberFormat.getNumberInstance(TR);
        nf.setMaximumFractionDigits(0);
        return nf.format(Math.max(0, v)) + " TL";
    }
}
