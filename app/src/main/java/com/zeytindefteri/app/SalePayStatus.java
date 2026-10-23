package com.zeytindefteri.app;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Satışın tahsilat durumu (saf kurallar, Android'siz): liste etiketi, söz verilen gün ve hızlı tutarlar.
 *
 * Durumlar: satış yok → "Satılmadı"; kalan yok → "Ödendi"; söz günü geçti → "Gecikti · N gün";
 * bir kısmı alındı → "Kısmi · X TL kaldı"; hiç alınmadı → "Bekliyor · 25 Eki" (gün yoksa "Bekliyor").
 * Tarihler "yyyy-MM-dd" metnidir (sales.due_date ve receivables.due_date ile aynı biçim).
 */
public final class SalePayStatus {
    public static final int NO_SALE = 0, PAID = 1, WAITING = 2, PARTIAL = 3, OVERDUE = 4;

    /** "Ne zaman verecek?" seçenekleri; TARIH seçilince takvimden gün alınır. */
    public static final int DUE_TOMORROW = 0, DUE_TWO_DAYS = 1, DUE_WEEK = 2, DUE_PICK = 3;
    public static final String[] DUE_LABELS = {"Yarın", "2 gün", "1 hafta", "Tarih"};

    /** Ödeme şekilleri (sale_payments.method). */
    public static final String[] METHODS = {"CASH", "TRANSFER", "CHEQUE"};
    public static final String[] METHOD_LABELS = {"Nakit", "Havale/EFT", "Çek/Senet"};

    private static final String[] MONTHS = {"Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
            "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"};
    private static final String[] MONTHS_SHORT = {"Oca", "Şub", "Mar", "Nis", "May", "Haz",
            "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara"};
    private static final String[] DAYS = {"Pazar", "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi"};

    private SalePayStatus() {}

    public static double remaining(double net, double received) {
        double r = net - Math.max(0, received);
        return r > 0.009 ? r : 0;
    }

    public static int kind(boolean hasSale, double net, double received, String due, String today) {
        if (!hasSale) return NO_SALE;
        if (remaining(net, received) < 0.01) return PAID;
        if (overdueDays(due, today) > 0) return OVERDUE;
        return received > 0.009 ? PARTIAL : WAITING;
    }

    /** Listede görünen etiket. */
    public static String label(int kind, double net, double received, String due, String today) {
        switch (kind) {
            case NO_SALE: return "Satılmadı";
            case PAID: return "Ödendi";
            case OVERDUE: return "Gecikti · " + overdueDays(due, today) + " gün";
            case PARTIAL: return "Kısmi · " + ElekSaleModel.tl(remaining(net, received)) + " kaldı";
            default: return valid(iso(due)) ? "Bekliyor · " + shortDate(due) : "Bekliyor";
        }
    }

    /** Ekran okuyucu için uzun açıklama. */
    public static String spoken(int kind, double net, double received, String due, String today) {
        switch (kind) {
            case NO_SALE: return "Satış işaretlenmedi";
            case PAID: return "Ödeme tamamı alındı";
            case OVERDUE: return ElekSaleModel.tl(remaining(net, received)) + " bekleniyor, söz verilen gün "
                    + overdueDays(due, today) + " gün geçti";
            case PARTIAL: return "Bir kısmı alındı, " + ElekSaleModel.tl(remaining(net, received)) + " kaldı";
            default: return ElekSaleModel.tl(remaining(net, received)) + " bekleniyor"
                    + (valid(iso(due)) ? ", söz verilen gün " + longDate(due) : "");
        }
    }

    /** Söz verilen gün bugünden kaç gün önceydi; geçmediyse (ya da gün yoksa) 0. */
    public static int overdueDays(String due, String today) {
        due = iso(due); today = iso(today);
        if (!valid(due) || !valid(today)) return 0;
        long d = daysBetween(due, today);
        return d > 0 ? (int) d : 0;
    }

    /** b - a gün farkı (ikisi de yyyy-MM-dd). */
    public static long daysBetween(String a, String b) {
        return (epochDay(b) - epochDay(a));
    }

    /** Seçeneğe göre söz verilen gün; DUE_PICK için boş döner (takvimden seçilir). */
    public static String dueFor(int option, String today) {
        if (!valid(today)) return "";
        int add = option == DUE_TOMORROW ? 1 : option == DUE_TWO_DAYS ? 2 : option == DUE_WEEK ? 7 : -1;
        if (add < 0) return "";
        Calendar c = cal(today);
        c.add(Calendar.DAY_OF_MONTH, add);
        return ymd(c);
    }

    /** Kayıtlı gün hangi seçeneğe denk geliyor (yoksa DUE_PICK; gün boşsa -1). */
    public static int optionFor(String due, String today) {
        if (!valid(due)) return -1;
        for (int i = 0; i < DUE_PICK; i++) if (due.equals(dueFor(i, today))) return i;
        return DUE_PICK;
    }

    /** "25 Eki" */
    public static String shortDate(String ymd) {
        ymd = iso(ymd);
        if (!valid(ymd)) return "";
        Calendar c = cal(ymd);
        return c.get(Calendar.DAY_OF_MONTH) + " " + MONTHS_SHORT[c.get(Calendar.MONTH)];
    }

    /** "20 Ekim" */
    public static String dayMonth(String ymd) {
        ymd = iso(ymd);
        if (!valid(ymd)) return "";
        Calendar c = cal(ymd);
        return c.get(Calendar.DAY_OF_MONTH) + " " + MONTHS[c.get(Calendar.MONTH)];
    }

    /** Satış formu tarihleri dd.MM.yyyy; burada yyyy-MM-dd'ye çevrilir (başka biçim olduğu gibi döner). */
    public static String iso(String v) {
        String t = v == null ? "" : v.trim();
        if (t.length() == 10 && t.charAt(2) == '.' && t.charAt(5) == '.') {
            return t.substring(6, 10) + "-" + t.substring(3, 5) + "-" + t.substring(0, 2);
        }
        return t;
    }

    /** "25 Ekim 2026, Pazar" */
    public static String longDate(String ymd) {
        ymd = iso(ymd);
        if (!valid(ymd)) return "";
        Calendar c = cal(ymd);
        return c.get(Calendar.DAY_OF_MONTH) + " " + MONTHS[c.get(Calendar.MONTH)] + " " + c.get(Calendar.YEAR)
                + ", " + DAYS[c.get(Calendar.DAY_OF_WEEK) - 1];
    }

    /**
     * "Ödeme aldım" penceresindeki hazır tutarlar: kalandan küçük, en büyük iki yuvarlak tutar
     * (ör. kalan 3.210 → 1.000 ve 2.000). Kalan 500'den azsa boş.
     */
    public static double[] quickAmounts(double remaining) {
        double[] steps = {500, 1000, 2000, 5000, 10000, 20000, 50000};
        double first = 0, second = 0;
        for (double s : steps) {
            if (s < remaining - 0.009) { first = second; second = s; }
        }
        if (second == 0) return new double[0];
        if (first == 0) return new double[]{second};
        return new double[]{first, second};
    }

    /** Tutar alanına yazılacak sade metin (ondalık yoksa tam sayı). */
    public static String plain(double v) {
        long r = Math.round(v);
        if (Math.abs(v - r) < 0.005) return String.valueOf(r);
        return String.format(java.util.Locale.US, "%.2f", v).replace('.', ',');
    }

    public static String methodLabel(String method) {
        for (int i = 0; i < METHODS.length; i++) if (METHODS[i].equals(method)) return METHOD_LABELS[i];
        return "Satışta alınan";
    }

    public static boolean valid(String ymd) {
        if (ymd == null || ymd.length() != 10 || ymd.charAt(4) != '-' || ymd.charAt(7) != '-') return false;
        try {
            int y = Integer.parseInt(ymd.substring(0, 4)), m = Integer.parseInt(ymd.substring(5, 7)),
                    d = Integer.parseInt(ymd.substring(8, 10));
            return y > 1900 && m >= 1 && m <= 12 && d >= 1 && d <= 31;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static String ymd(Calendar c) {
        return String.format(java.util.Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private static Calendar cal(String ymd) {
        return new GregorianCalendar(Integer.parseInt(ymd.substring(0, 4)),
                Integer.parseInt(ymd.substring(5, 7)) - 1, Integer.parseInt(ymd.substring(8, 10)), 12, 0, 0);
    }

    private static long epochDay(String ymd) {
        Calendar c = cal(ymd);
        c.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        c.set(Integer.parseInt(ymd.substring(0, 4)), Integer.parseInt(ymd.substring(5, 7)) - 1,
                Integer.parseInt(ymd.substring(8, 10)), 12, 0, 0);
        return Math.floorDiv(c.getTimeInMillis(), 86400000L);
    }
}
