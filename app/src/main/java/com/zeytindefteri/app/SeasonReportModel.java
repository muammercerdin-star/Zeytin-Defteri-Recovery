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
        public double harvestKg;       // getSeasonHarvestKgWithElek (elek dahil, gün başına büyüğü)
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

    /**
     * Üstteki 4 kutu: Hasat (elek dahil) · Satış · Masraf · Net kâr. Tek kayıt düzeninde elek, hasat ve
     * satış aynı zeytin; ayrı "Elek" kutusu aynı rakamı tekrar ederdi.
     */
    public static List<Tile> tiles(Data d) {
        List<Tile> out = new ArrayList<>();
        out.add(new Tile("Hasat", kg(d.harvestKg), d.harvestKg > 0 ? "elek kayıtları dahil" : "henüz kayıt yok", Tone.NORMAL));
        boolean noSales = d.realSaleCount <= 0 && d.salesRevenue <= 0;
        out.add(new Tile("Satış", tl(d.salesRevenue), noSales ? "satış kaydı yok" : d.realSaleCount + " gerçek satış",
                noSales ? Tone.WARNING : Tone.NORMAL));
        out.add(new Tile("Masraf", tl(d.expenses), "işlem, hasat, salamura", Tone.NORMAL));
        Tile net = net(d);
        out.add(new Tile("Net kâr", net.value, net.note, net.tone));
        return out;
    }

    /**
     * Kilo başına: ortalama satış fiyatı, kilo başı masraf, kilo başı net. Elle hesaplanması zor ama
     * çiftçinin en çok baktığı rakamlar. Hasat yoksa boş liste.
     */
    public static List<Tile> perKg(Data d) {
        List<Tile> out = new ArrayList<>();
        if (d.harvestKg <= 0) return out;
        double sold = d.soldKg > 0 ? d.soldKg : d.harvestKg;
        if (d.salesRevenue > 0) out.add(new Tile("Ortalama satış fiyatın", perKgText(d.salesRevenue / sold), sold == d.soldKg
                ? kg(sold) + " satıştan" : "hasat kilosuna göre", Tone.NORMAL));
        out.add(new Tile("Kilo başı masrafın", perKgText(d.expenses / d.harvestKg), "toplam masraf ÷ hasat", Tone.NORMAL));
        if (d.salesRevenue > 0) {
            double net = (d.salesRevenue - d.expenses) / d.harvestKg;
            out.add(new Tile("Kilo başı net kârın", perKgText(net), "satış − masraf, kilo başına",
                    net < 0 ? Tone.NEGATIVE : Tone.POSITIVE));
        }
        return out;
    }

    static String perKgText(double v) {
        NumberFormat nf = NumberFormat.getNumberInstance(TR);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(1);
        return (v < -0.05 ? "−" : "") + nf.format(Math.abs(v)) + " TL/kg";
    }

    /** Tahsilat: 0..100 ve "Paranın %84'ü tahsil edildi · 10.340 TL alacak". Satış yoksa null. */
    public static int collectedPercent(Data d) {
        double total = d.received + d.openReceivable;
        if (total <= 0) return -1;
        return (int) Math.round(100.0 * Math.max(0, d.received) / total);
    }

    public static String collectedText(Data d) {
        int p = collectedPercent(d);
        if (p < 0) return null;
        if (d.openReceivable <= 0.5) return "Paranın tamamı tahsil edildi";
        return "Paranın %" + p + "'" + suffix(p) + " tahsil edildi · " + tl(d.openReceivable) + " alacak";
    }

    /** "%84'ü", "%50'si", "%100'ü", "%6'sı" (sayının okunuşuna göre iyelik eki). */
    static String suffix(int p) {
        int last = p % 10, tens = (p / 10) % 10;
        if (p == 0) return "ı";
        if (last == 0) {
            if (p % 100 == 0) return "ü";                    // yüz, bin
            switch (tens) { case 1: case 3: return "u"; case 2: case 5: return "si"; case 4: return "ı"; case 6: return "ı"; case 7: return "i"; case 8: return "i"; case 9: return "ı"; default: return "ü"; }
        }
        switch (last) { case 1: case 5: case 8: return "i"; case 2: case 7: return "si"; case 3: case 4: return "ü"; case 6: return "sı"; case 9: return "u"; default: return "ü"; }
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

    public static Tile net(Data d) {
        double net = d.salesRevenue - d.expenses;
        boolean noSales = d.realSaleCount <= 0 && d.salesRevenue <= 0;
        if (noSales) return new Tile("Net sonuç", tl(net), "satış girilmediği için eksik", Tone.MUTED);
        return new Tile("Net sonuç", tl(net), "satış geliri − masraf", net < 0 ? Tone.NEGATIVE : Tone.POSITIVE);
    }

    public static String gardenMeta(Garden g) {
        List<String> parts = new ArrayList<>();
        double kgTotal = Math.max(g.harvestKg, g.elekKg);   // harvestKg zaten elek dahil; eski veride elek büyükse o
        if (kgTotal > 0) parts.add(kg(kgTotal) + " hasat");
        if (g.operationCost > 0) parts.add(tl(g.operationCost) + " işlem");
        return parts.isEmpty() ? "Bu sezon kayıt yok" : join(parts);
    }

    private static String join(List<String> parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) { if (b.length() > 0) b.append(" · "); b.append(p); }
        return b.toString();
    }
}
