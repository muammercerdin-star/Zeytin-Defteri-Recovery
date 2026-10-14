package com.zeytindefteri.app;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * İşlem geçmişi ekranının saf (Android'siz) kuralları: tür filtresi, tarih sıralaması, ay grupları
 * ve satır metinleri. Kayıtlar DatabaseHelper.getOperationsFiltered'dan gelir.
 */
public final class OperationHistoryModel {
    private OperationHistoryModel() {}

    public static final String ALL = "Tümü";

    /** Filtre seçenekleri: Tümü + işlem ekle türleri + hasat hareketi ("Diğer" en sonda). */
    public static List<String> filters() {
        List<String> out = new ArrayList<>();
        out.add(ALL);
        for (String t : OperationKinds.TYPES) if (!"Diğer".equals(t)) out.add(t);
        out.add("Hasat");
        out.add("Diğer");
        return out;
    }

    private static final Locale TR = new Locale("tr", "TR");
    private static final String[] MONTHS = {"OCAK", "ŞUBAT", "MART", "NİSAN", "MAYIS", "HAZİRAN",
            "TEMMUZ", "AĞUSTOS", "EYLÜL", "EKİM", "KASIM", "ARALIK"};

    /**
     * Satır filtreye uyuyor mu? Büyük/küçük harf ve Türkçe karakterler gözetilir; eski "Gübreleme"
     * kayıtları "Gübre" filtresine, eski "Sinek Tuzağı" yazımı "Sinek tuzağı" filtresine düşer.
     */
    public static boolean matches(String filter, String type) {
        if (filter == null || ALL.equals(filter)) return true;
        return normalize(filter).equals(normalize(type));
    }

    static String normalize(String type) {
        String t = type == null ? "" : type.trim().toLowerCase(TR);
        return "gübreleme".equals(t) ? "gübre" : t;
    }

    public static final class Row {
        public final long id;
        public final String type, garden, date, unit, note;
        public final double quantity, cost;
        final int order;

        public Row(long id, String type, String garden, String date, double quantity, String unit,
                   double cost, String note, int order) {
            this.id = id; this.type = type == null ? "" : type; this.garden = garden == null ? "" : garden;
            this.date = date == null ? "" : date; this.quantity = quantity; this.unit = unit == null ? "" : unit;
            this.cost = cost; this.note = note == null ? "" : note; this.order = order;
        }
    }

    /**
     * İşlem tarihine göre yeniden eskiye sıralar. Aynı gündeki ve tarihi okunamayan kayıtlar
     * veritabanından geldiği sırayı (en yeni kayıt önce) korur; okunamayan tarihler en sona gider.
     */
    public static void sortNewestFirst(List<Row> rows) {
        Collections.sort(rows, (a, b) -> {
            long da = GardenDetailModel.epochDay(a.date), db = GardenDetailModel.epochDay(b.date);
            if (da != db) return Long.compare(db, da);
            return Integer.compare(a.order, b.order);
        });
    }

    /** Ay başlığı: "EKİM 2026"; tarih okunamazsa "TARİHSİZ". */
    public static String monthLabel(String date) {
        int[] ymd = GardenDetailModel.parts(date);
        return ymd == null ? "TARİHSİZ" : MONTHS[ymd[1] - 1] + " " + ymd[0];
    }

    /** Satır alt metni: "Bahçe · tarih · miktar birim"; bahçe filtresi seçiliyse bahçe adı yazılmaz. */
    public static String meta(Row r, boolean showGarden) {
        StringBuilder b = new StringBuilder();
        if (showGarden && !r.garden.trim().isEmpty()) b.append(r.garden.trim());
        if (!r.date.trim().isEmpty()) { if (b.length() > 0) b.append(" · "); b.append(r.date.trim()); }
        if (r.quantity > 0) {
            if (b.length() > 0) b.append(" · ");
            b.append(number(r.quantity));
            if (!r.unit.trim().isEmpty()) b.append(' ').append(r.unit.trim());
        }
        return b.toString();
    }

    public static String money(double v) {
        return number(v) + " TL";
    }

    /** Masraf yoksa satırda tutar gösterilmez. */
    public static String cost(double v) {
        return v > 0 ? money(v) : "";
    }

    /** Ay başlığının sağı: "3 işlem · 1.250 TL". */
    public static String groupSummary(int count, double total) {
        return count + " işlem" + (total > 0 ? " · " + money(total) : "");
    }

    static String number(double v) {
        NumberFormat f = NumberFormat.getNumberInstance(TR);
        f.setMinimumFractionDigits(0);
        f.setMaximumFractionDigits(2);
        return f.format(v);
    }
}
