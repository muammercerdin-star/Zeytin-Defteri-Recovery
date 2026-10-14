package com.zeytindefteri.app;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Bahçe detayı ekranının saf (Android'siz) metin kuralları.
 * Rakamlar GardenAnalyticsRepository'den (Bahçe Karnesi ile aynı kaynak) gelir.
 */
public final class GardenDetailModel {
    private GardenDetailModel() {}

    /** Bu kadar gün işlem girilmemişse "kontrol et" uyarısı gösterilir. */
    public static final int STALE_DAYS = 30;

    private static final Locale TR = new Locale("tr", "TR");

    /** Başlık altı: "Çeşit · Mahalle / İlçe / İl"; eksik parçalar atlanır. */
    public static String subtitle(String variety, String neighborhood, String district, String province) {
        StringBuilder location = new StringBuilder();
        for (String part : new String[]{neighborhood, district, province}) {
            if (blank(part)) continue;
            if (location.length() > 0) location.append(" / ");
            location.append(part.trim());
        }
        String v = blank(variety) ? "Çeşit belirtilmedi" : variety.trim();
        return location.length() == 0 ? v : v + " · " + location;
    }

    public static String irrigation(String type) {
        return blank(type) ? "—" : type.trim();
    }

    /** Ağaç ve yaş gibi tam sayılar; 0 veya negatifse "—". */
    public static String count(int value) {
        return value > 0 ? String.valueOf(value) : "—";
    }

    public static String number(double value) {
        if (value <= 0 || Double.isNaN(value) || Double.isInfinite(value)) return "—";
        NumberFormat nf = NumberFormat.getNumberInstance(TR);
        nf.setMaximumFractionDigits(2);
        return nf.format(value);
    }

    /**
     * Tarihi gün sayısına çevirir (1970'ten itibaren). Uygulamanın biçimi "dd.MM.yyyy"dir; eski kayıtlarda
     * görülen "yyyy-MM-dd" de okunur (sezon sorgusu ikisini de kabul eder). Okunamazsa -1.
     */
    public static long epochDay(String date) {
        int[] ymd = parts(date);
        return ymd == null ? -1 : civilToDays(ymd[0], ymd[1], ymd[2]);
    }

    /** {yıl, ay, gün}; okunamazsa null. Sonda saat varsa yok sayılır. */
    public static int[] parts(String date) {
        if (blank(date)) return null;
        String v = date.trim();
        int space = v.indexOf(' ');
        if (space > 0) v = v.substring(0, space);
        try {
            int y, m, d;
            String[] p = v.split("\\.");
            if (p.length == 3) {
                d = Integer.parseInt(p[0]); m = Integer.parseInt(p[1]); y = Integer.parseInt(p[2]);
            } else {
                p = v.split("-");
                if (p.length != 3 || p[0].length() != 4) return null;
                y = Integer.parseInt(p[0]); m = Integer.parseInt(p[1]); d = Integer.parseInt(p[2]);
            }
            if (m < 1 || m > 12 || d < 1 || d > 31 || y < 1900 || y > 9999) return null;
            return new int[]{y, m, d};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Proleptik Gregoryen takvimde gün numarası (Howard Hinnant algoritması). */
    static long civilToDays(int y, int m, int d) {
        y -= m <= 2 ? 1 : 0;
        long era = (y >= 0 ? y : y - 399) / 400;
        long yoe = y - era * 400;
        long doy = (153L * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    /**
     * Son işlemden bu yana geçen süre uyarısı; gerek yoksa boş metin.
     * İşlem hiç yoksa uyarı verilmez (boş liste metni zaten bunu söyler).
     * Gelecek tarihli kayıtlar uyarı üretmez.
     */
    public static String staleWarning(long lastOperationDay, long today) {
        if (lastOperationDay < 0 || today < 0) return "";
        long days = today - lastOperationDay;
        if (days < STALE_DAYS) return "";
        return "Son işlem " + days + " gün önce. Kayıtlarını ve bahçedeki durumu kontrol et.";
    }

    /** İşlem satırının alt bilgisi: "tarih · miktar birim · not". */
    public static String operationMeta(String date, double quantity, String unit, String note) {
        StringBuilder b = new StringBuilder();
        if (!blank(date)) b.append(date.trim());
        if (quantity > 0) {
            if (b.length() > 0) b.append(" · ");
            NumberFormat nf = NumberFormat.getNumberInstance(TR);
            nf.setMaximumFractionDigits(2);
            b.append(nf.format(quantity));
            if (!blank(unit)) b.append(' ').append(unit.trim());
        }
        if (!blank(note)) {
            if (b.length() > 0) b.append(" · ");
            b.append(note.trim());
        }
        return b.toString();
    }

    static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
