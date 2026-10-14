package com.zeytindefteri.app;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Sezon Raporum" ekranının saf (Android'siz) kuralları. Rakamlar DatabaseHelper'ın mevcut
 * sezon/sahip filtreli toplamlarından gelir; burada yalnızca gösterim ve uyarı mantığı vardır.
 */
public final class SeasonReportModel {
    private SeasonReportModel() {}

    public static final class Data {
        public int season;
        public double expenses;        // getTotalExpenses (işlem + hasat + salamura masrafı)
        public double harvestKg;       // getTotalHarvestKg
        public double elekKg;          // Elek arşivi toplamı
        public double elekRevenue;     // Elek tutarı (tahmini)
        public double salesRevenue;    // getTotalSalesRevenue (gerçek satışlar)
        public double soldKg;          // getTotalSoldKg
        public int realSaleCount;      // getRealSaleCount
        public double received;        // getTotalAmountReceived
        public double openReceivable;  // getTotalOpenReceivableBalance
        public double brineInKg, brineReadyKg, brineCost;
        public int brineBatches;
        public final List<Garden> gardens = new ArrayList<>();
    }

    public static final class Garden {
        public String name = "";
        public double elekKg, operationCost, harvestKg;
    }

    public enum Tone { NORMAL, WARNING, MUTED, POSITIVE, NEGATIVE }

    public static final class Tile {
        public final String label, value, note; public final Tone tone;
        Tile(String label, String value, String note, Tone tone) { this.label = label; this.value = value; this.note = note; this.tone = tone; }
    }

    public static final class Bar {
        public final String label, value; public final int percent; public final Tone tone;
        Bar(String label, String value, int percent, Tone tone) { this.label = label; this.value = value; this.percent = percent; this.tone = tone; }
    }

    private static final Locale TR = new Locale("tr", "TR");

    public static String tl(double v) {
        NumberFormat nf = NumberFormat.getNumberInstance(TR);
        nf.setMaximumFractionDigits(0);
        String s = nf.format(Math.abs(v)) + " TL";
        return v < -0.5 ? "−" + s : s;
    }

    public static String kg(double v) {
        NumberFormat nf = NumberFormat.getNumberInstance(TR);
        nf.setMaximumFractionDigits(1);
        return nf.format(Math.max(0, v)) + " kg";
    }

    public static List<Tile> tiles(Data d) {
        List<Tile> out = new ArrayList<>();
        out.add(new Tile("Masraf", tl(d.expenses), "masraf kayıtları", Tone.NORMAL));
        out.add(new Tile("Hasat", kg(d.harvestKg), "hasat kayıtları", Tone.NORMAL));
        out.add(new Tile("Elek", kg(d.elekKg), d.elekRevenue > 0 ? "tahmini " + tl(d.elekRevenue) : "elek kayıtları", Tone.NORMAL));
        boolean noSales = d.realSaleCount <= 0 && d.salesRevenue <= 0;
        out.add(new Tile("Satış", tl(d.salesRevenue), noSales ? "satış kaydı yok" : d.realSaleCount + " gerçek satış",
                noSales ? Tone.WARNING : Tone.NORMAL));
        return out;
    }

    /** Hasat → Elek → Satış kilo zinciri; çubuklar en büyük değere göre ölçeklenir (en az %2). */
    public static List<Bar> chain(Data d) {
        double max = Math.max(d.harvestKg, Math.max(d.elekKg, d.soldKg));
        List<Bar> out = new ArrayList<>();
        out.add(new Bar("Hasat", kg(d.harvestKg), percent(d.harvestKg, max), Tone.NORMAL));
        out.add(new Bar("Elek", kg(d.elekKg), percent(d.elekKg, max), Tone.POSITIVE));
        out.add(new Bar("Satış", kg(d.soldKg), percent(d.soldKg, max), d.soldKg <= 0 ? Tone.WARNING : Tone.NORMAL));
        return out;
    }

    static int percent(double v, double max) {
        if (max <= 0) return 2;
        return Math.max(2, Math.min(100, (int) Math.round(100.0 * Math.max(0, v) / max)));
    }

    /** Eksik halka uyarısı: {başlık, açıklama, eylem}; uyarı yoksa null. */
    public static String[] missingLink(Data d) {
        if (d.harvestKg <= 0 && d.elekKg <= 0 && d.soldKg <= 0) {
            return new String[]{"Henüz hasat veya elek kaydı yok",
                    "Hasat ve elek kayıtlarını girdikçe sezonun nerede olduğu burada görünür.", "Elek kaydı ekle"};
        }
        if (d.elekKg > 0 && d.soldKg <= 0 && d.realSaleCount <= 0) {
            return new String[]{"Eksik halka: satış kaydı",
                    kg(d.elekKg) + " elendi ama hiç satış girilmedi. Elek tutarı tahminidir; "
                            + "sattığın elekleri satışa dönüştürünce gelire ve net sonuca eklenir.", "Elekleri satışa dönüştür"};
        }
        return null;
    }

    /** Hasat elekten azsa neden farklı olabileceğini açıklayan not; gerek yoksa boş. */
    public static String harvestNote(Data d) {
        if (d.elekKg > d.harvestKg + 0.5) {
            return "Hasat " + kg(d.harvestKg) + ", elek " + kg(d.elekKg)
                    + ": elek kayıtları hasat kaydı girilmeden de tutulabildiği için ikisi farklı olabilir.";
        }
        return "";
    }

    public static Tile net(Data d) {
        double net = d.salesRevenue - d.expenses;
        boolean noSales = d.realSaleCount <= 0 && d.salesRevenue <= 0;
        if (noSales) return new Tile("Net sonuç", tl(net), "satış girilmediği için eksik", Tone.MUTED);
        return new Tile("Net sonuç", tl(net), "satış geliri − masraf", net < 0 ? Tone.NEGATIVE : Tone.POSITIVE);
    }

    public static String gardenMeta(Garden g) {
        List<String> parts = new ArrayList<>();
        if (g.elekKg > 0) parts.add(kg(g.elekKg) + " elek");
        if (g.harvestKg > 0) parts.add(kg(g.harvestKg) + " hasat");
        if (g.operationCost > 0) parts.add(tl(g.operationCost) + " işlem");
        return parts.isEmpty() ? "Bu sezon kayıt yok" : join(parts);
    }

    private static String join(List<String> parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) { if (b.length() > 0) b.append(" · "); b.append(p); }
        return b.toString();
    }
}
