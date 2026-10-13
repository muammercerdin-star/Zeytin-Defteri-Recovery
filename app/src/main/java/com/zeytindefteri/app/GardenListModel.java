package com.zeytindefteri.app;

import java.text.Collator;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Bahçelerim ekranı için saf hesaplamalar: verim, kg başına maliyet, son kayıt, sıralama.
 * Android, ağ veya veritabanı kullanmaz. "Kayıt bekliyor" eşiği GardenTodayAdvisor ile aynıdır
 * ve yalnızca kayıt hatırlatmasıdır; sulama/gübreleme takvimi değildir.
 */
public final class GardenListModel {
    private GardenListModel() {}

    private static final Locale TR = new Locale("tr", "TR");

    public enum Sort { ACTIVE, NAME, YIELD, STALE }

    public static final class Item {
        public long id;
        public String name = "Bahçe", variety = "", photoUri = "";
        public String lastOperation = "", lastOperationDate = "", lastHarvestDate = "";
        public int trees, age;
        public double harvestKg, cost;
    }

    public static final class Activity {
        public final String label;
        public final long days;
        Activity(String label, long days) { this.label = label; this.days = days; }
    }

    // ------------------------------------------------------------------
    // Tarih
    // ------------------------------------------------------------------
    public static long daysSince(String raw, Calendar now) {
        Long day = parseDay(raw);
        if (day == null) return -1;
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        long diff = Math.round((c.getTimeInMillis() - day) / 86400000.0);
        return diff < 0 ? -1 : diff;
    }

    private static Long parseDay(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String value = raw.trim();
        if (value.length() > 10 && value.charAt(4) == '-') value = value.substring(0, 10);
        for (String pattern : new String[]{"yyyy-MM-dd", "dd.MM.yyyy", "dd/MM/yyyy"}) {
            SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
            f.setLenient(false);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            ParsePosition p = new ParsePosition(0);
            Date d = f.parse(value, p);
            if (d != null && p.getIndex() == value.length()) return d.getTime();
        }
        return null;
    }

    private static String ago(long days) {
        return days == 0 ? "bugün" : days + " gün önce";
    }

    /** Son işlem ile son hasattan hangisi daha yeniyse onu verir. */
    public static Activity lastActivity(Item item, Calendar now) {
        long op = daysSince(item.lastOperationDate, now);
        long harvest = daysSince(item.lastHarvestDate, now);
        String type = item.lastOperation == null ? "" : item.lastOperation.trim();
        if (harvest >= 0 && (op < 0 || harvest < op)) return new Activity("Hasat · " + ago(harvest), harvest);
        if (op >= 0) return new Activity((type.isEmpty() ? "İşlem" : type) + " · " + ago(op), op);
        if (!type.isEmpty()) return new Activity(type, -1);
        return new Activity("Kayıt yok", -1);
    }

    public static boolean isStale(Item item, Calendar now) {
        long days = lastActivity(item, now).days;
        return days < 0 || days >= GardenTodayAdvisor.RECORD_REVIEW_DAYS;
    }

    public static int staleCount(List<Item> items, Calendar now) {
        int count = 0;
        for (Item i : items) if (isStale(i, now)) count++;
        return count;
    }

    // ------------------------------------------------------------------
    // Oranlar
    // ------------------------------------------------------------------
    public static double yieldPerTree(double harvestKg, int trees) {
        return trees > 0 && harvestKg > 0 ? harvestKg / trees : Double.NaN;
    }

    public static double costPerKg(double cost, double harvestKg) {
        return harvestKg > 0 && cost > 0 ? cost / harvestKg : Double.NaN;
    }

    public static int totalTrees(List<Item> items) {
        int total = 0;
        for (Item i : items) total += Math.max(0, i.trees);
        return total;
    }

    public static double totalHarvest(List<Item> items) {
        double total = 0;
        for (Item i : items) total += Math.max(0, i.harvestKg);
        return total;
    }

    public static double totalCost(List<Item> items) {
        double total = 0;
        for (Item i : items) total += Math.max(0, i.cost);
        return total;
    }

    public static String formatYield(double value) {
        if (Double.isNaN(value)) return "—";
        NumberFormat nf = NumberFormat.getNumberInstance(TR);
        nf.setMaximumFractionDigits(1);
        return nf.format(value) + " kg/ağaç";
    }

    public static String formatCostPerKg(double value) {
        if (Double.isNaN(value)) return "—";
        NumberFormat nf = NumberFormat.getNumberInstance(TR);
        nf.setMinimumFractionDigits(value < 100 ? 2 : 0);
        nf.setMaximumFractionDigits(value < 100 ? 2 : 0);
        return nf.format(value) + " TL/kg";
    }

    public static String staleText(int count) {
        return count == 1 ? "1 bahçe kayıt bekliyor" : count + " bahçe kayıt bekliyor";
    }

    // ------------------------------------------------------------------
    // Sıralama
    // ------------------------------------------------------------------
    public static List<Item> sort(List<Item> items, Sort mode, final long activeId, final Calendar now) {
        List<Item> out = new ArrayList<>(items);
        final Collator collator = Collator.getInstance(TR);
        final Comparator<Item> byName = (a, b) -> collator.compare(a.name, b.name);
        Comparator<Item> comparator;
        switch (mode) {
            case NAME:
                comparator = byName;
                break;
            case YIELD:
                comparator = (a, b) -> {
                    double ya = yieldPerTree(a.harvestKg, a.trees), yb = yieldPerTree(b.harvestKg, b.trees);
                    if (Double.isNaN(ya) && Double.isNaN(yb)) return byName.compare(a, b);
                    if (Double.isNaN(ya)) return 1;
                    if (Double.isNaN(yb)) return -1;
                    int c = Double.compare(yb, ya);
                    return c != 0 ? c : byName.compare(a, b);
                };
                break;
            case STALE:
                comparator = (a, b) -> {
                    boolean sa = isStale(a, now), sb = isStale(b, now);
                    if (sa != sb) return sa ? -1 : 1;
                    long da = lastActivity(a, now).days, db = lastActivity(b, now).days;
                    long ka = da < 0 ? Long.MAX_VALUE : da, kb = db < 0 ? Long.MAX_VALUE : db;
                    int c = Long.compare(kb, ka);
                    return c != 0 ? c : byName.compare(a, b);
                };
                break;
            default:
                comparator = (a, b) -> {
                    boolean aa = a.id == activeId, ab = b.id == activeId;
                    if (aa != ab) return aa ? -1 : 1;
                    return byName.compare(a, b);
                };
        }
        Collections.sort(out, comparator);
        return out;
    }

    public static Sort parseSort(String value) {
        if (value != null) {
            for (Sort s : Sort.values()) if (s.name().equals(value)) return s;
        }
        return Sort.ACTIVE;
    }
}
