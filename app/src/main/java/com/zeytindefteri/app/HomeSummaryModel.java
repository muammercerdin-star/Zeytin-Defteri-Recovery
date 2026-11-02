package com.zeytindefteri.app;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Ana Sayfa alt bölümü (hızlı işlemler, "Bu Sezon", "Senin elek fiyatların") için saf kurallar.
 * "Senin elek fiyatların" yalnızca kullanıcının kendi elek kayıtlarıdır; piyasa diye sunulmaz.
 */
public final class HomeSummaryModel {
    private HomeSummaryModel() {}

    /** NewOperationActivity'nin tür listesiyle birebir aynı (eşleşme equalsIgnoreCase ile yapılır). */
    /** İşlem ekle ekranıyla aynı liste (tek kaynak: OperationKinds). */
    public static final String[] OPERATION_TYPES = OperationKinds.TYPES;
    public static final String[] GRADE_CODES = {"DOUBLE", "1", "2", "3", "4", "YAGLIK"};
    public static final String[] GRADE_LABELS = {"Double", "1 No", "2 No", "3 No", "4 No", "Yağlık"};
    private static final Locale TR = new Locale("tr", "TR");

    public enum Tone { NORMAL, POSITIVE, WARNING }

    public static final class Metric {
        public final String label, value; public final Tone tone;
        Metric(String label, String value, Tone tone) { this.label = label; this.value = value; this.tone = tone; }
    }

    public static final class GradePrice {
        public String label = "";
        public double kg, revenue;
        public int records;
        public double average() { return kg > 0 ? revenue / kg : 0; }
    }

    /**
     * Hasat · Masraf · Satış. Hasat elek kayıtlarını da içerir (ayrı "Elek" sütunu yok: tek kayıt).
     * Masraf nötr (alarm rengi değil); satış yoksa uyarı tonu.
     */
    public static List<Metric> season(double harvestKg, double expenses, double revenue) {
        List<Metric> out = new ArrayList<>();
        out.add(new Metric("Hasat", kg(harvestKg), Tone.NORMAL));
        out.add(new Metric("Masraf", tl(expenses), Tone.NORMAL));
        out.add(new Metric("Satış", tl(revenue), revenue > 0 ? Tone.POSITIVE : Tone.WARNING));
        return out;
    }

    /** Kilosu en çok olan sınıflar (en fazla n); fiyatı olmayan sınıflar atlanır. */
    public static List<GradePrice> top(List<GradePrice> all, int n) {
        List<GradePrice> list = new ArrayList<>();
        for (GradePrice g : all) if (g != null && g.kg > 0 && g.revenue > 0) list.add(g);
        Collections.sort(list, (a, b) -> Double.compare(b.kg, a.kg));
        return list.size() > n ? new ArrayList<>(list.subList(0, n)) : list;
    }

    /** Fiyatlı elek kaydı olan bütün sınıflar, elek sırasıyla (Double, 1 No … Yağlık). */
    public static List<GradePrice> withData(List<GradePrice> all) {
        List<GradePrice> list = new ArrayList<>();
        for (GradePrice g : all) if (g != null && g.kg > 0 && g.revenue > 0) list.add(g);
        return list;
    }

    /**
     * Bölgenin güncel fiyatıyla karşılaştırma: "Bölge 35 · +3,8 TL" (senin ortalaman yüksekse +, düşükse −).
     * Bölge fiyatı yoksa "". Fark 0,5 TL'den azsa "Bölge 35 · aynı".
     */
    public static String compareLine(double own, double region) {
        if (!(region > 0) || !(own > 0)) return "";
        NumberFormat nf = NumberFormat.getNumberInstance(TR);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(1);
        double diff = own - region;
        String head = "Bölge " + nf.format(region);
        if (Math.abs(diff) < 0.5) return head + " · aynı";
        return head + " · " + (diff > 0 ? "+" : "−") + nf.format(Math.abs(diff)) + " TL";
    }

    /** -1 bölgenin altında, 0 aynı / bilinmiyor, 1 üstünde (renk için). */
    public static int compareTone(double own, double region) {
        if (!(region > 0) || !(own > 0) || Math.abs(own - region) < 0.5) return 0;
        return own > region ? 1 : -1;
    }

    /** Sadece rakam: "38,8" (birim ayrı yazılır). */
    public static String priceNumber(double v) {
        NumberFormat nf = NumberFormat.getNumberInstance(TR);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(1);
        return nf.format(Math.max(0, v));
    }

    public static String priceLine(GradePrice g) { return price(g.average()); }

    public static String detailLine(GradePrice g) {
        return g.records + " elek kaydı · " + kg(g.kg);
    }

    public static String emptyPrices() {
        return "Fiyatlı elek kaydı girdikçe sınıf sınıf ortalaman burada görünür.";
    }

    public static String price(double v) {
        NumberFormat nf = NumberFormat.getNumberInstance(TR);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(1);
        return nf.format(Math.max(0, v)) + " TL/kg";
    }

    public static String kg(double v) {
        NumberFormat nf = NumberFormat.getNumberInstance(TR);
        nf.setMaximumFractionDigits(1);
        return nf.format(Math.max(0, v)) + " kg";
    }

    public static String tl(double v) {
        NumberFormat nf = NumberFormat.getNumberInstance(TR);
        nf.setMaximumFractionDigits(0);
        return nf.format(Math.max(0, v)) + " TL";
    }
}
