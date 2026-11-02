package com.zeytindefteri.app;

import java.util.*;

/** Host-only checks for "Sezon Raporum" display rules. */
public final class SeasonReportModelTest {
    private static int checks;
    private static void eq(Object a, Object e, String l) { if (!Objects.equals(a, e)) throw new AssertionError(l + ": " + a + " != " + e); checks++; }
    public static void main(String[] args) {
        // Ekrandaki durum (2 Kasım 2026): yalnız elek girilmiş, hasat elek dahil 1.307 kg.
        SeasonReportModel.Data d = new SeasonReportModel.Data();
        d.season = 2026; d.expenses = 22960; d.harvestKg = 1307; d.elekKg = 1307; d.elekRevenue = 62825;
        d.salesRevenue = 62825; d.soldKg = 1307; d.realSaleCount = 17; d.received = 52485; d.openReceivable = 10340;
        List<SeasonReportModel.Tile> t = SeasonReportModel.tiles(d);
        eq(t.size(), 4, "four tiles");
        eq(t.get(0).label + "=" + t.get(0).value, "Hasat=1.307 kg", "harvest includes elek");
        eq(t.get(0).note, "elek kayıtları dahil", "harvest note");
        eq(t.get(1).value, "62.825 TL", "sales");
        eq(t.get(1).note, "17 gerçek satış", "sales count");
        eq(t.get(2).value, "22.960 TL", "expenses");
        eq(t.get(3).label + "=" + t.get(3).value, "Net kâr=39.865 TL", "net profit on top");
        eq(t.get(3).tone, SeasonReportModel.Tone.POSITIVE, "profit green");
        for (SeasonReportModel.Tile x : t) if ("Elek".equals(x.label)) throw new AssertionError("no duplicate elek tile");

        List<SeasonReportModel.Tile> k = SeasonReportModel.perKg(d);
        eq(k.size(), 3, "three per-kg rows");
        eq(k.get(0).value, "48,1 TL/kg", "average sale price");
        eq(k.get(1).value, "17,6 TL/kg", "cost per kg");
        eq(k.get(2).value, "30,5 TL/kg", "net per kg");
        eq(k.get(2).tone, SeasonReportModel.Tone.POSITIVE, "net per kg positive");

        eq(SeasonReportModel.collectedPercent(d), 84, "collected percent");
        eq(SeasonReportModel.collectedText(d), "Paranın %84'ü tahsil edildi · 10.340 TL alacak", "collected text");
        eq(SeasonReportModel.suffix(50) + SeasonReportModel.suffix(6) + SeasonReportModel.suffix(100) + SeasonReportModel.suffix(40)
                + SeasonReportModel.suffix(9) + SeasonReportModel.suffix(72), "sisıüıusi", "Turkish possessive after numbers");
        d.received = 62825; d.openReceivable = 0;
        eq(SeasonReportModel.collectedText(d), "Paranın tamamı tahsil edildi", "all collected");
        eq(SeasonReportModel.missingLink(d), null, "no missing link with sales");

        // Satış girilmemiş: elek var, satış yok.
        SeasonReportModel.Data n = new SeasonReportModel.Data();
        n.expenses = 6610; n.harvestKg = 1247; n.elekKg = 1247;
        eq(SeasonReportModel.tiles(n).get(1).tone, SeasonReportModel.Tone.WARNING, "no sales warns");
        eq(SeasonReportModel.tiles(n).get(3).tone, SeasonReportModel.Tone.MUTED, "no sales: net not shown as loss");
        eq(SeasonReportModel.perKg(n).size(), 1, "only cost per kg without sales");
        eq(SeasonReportModel.collectedText(n), null, "no collection line without sales");
        String[] miss = SeasonReportModel.missingLink(n);
        eq(miss[0], "Eksik halka: satış kaydı", "missing title");
        eq(miss[2], "Elekleri satışa dönüştür", "missing action converts elek records");
        n.salesRevenue = 1000; n.realSaleCount = 1; n.soldKg = 20;
        eq(SeasonReportModel.net(n).tone, SeasonReportModel.Tone.NEGATIVE, "real loss is red");
        eq(SeasonReportModel.perKg(n).get(2).value.startsWith("−"), true, "negative net per kg");

        SeasonReportModel.Data empty = new SeasonReportModel.Data();
        eq(SeasonReportModel.missingLink(empty)[0], "Henüz hasat veya elek kaydı yok", "empty state");
        eq(SeasonReportModel.perKg(empty).isEmpty(), true, "no per-kg rows without harvest");
        eq(SeasonReportModel.tiles(empty).get(0).note, "henüz kayıt yok", "empty harvest note");

        SeasonReportModel.Garden g = new SeasonReportModel.Garden();
        eq(SeasonReportModel.gardenMeta(g), "Bu sezon kayıt yok", "empty garden");
        g.elekKg = 98; g.operationCost = 1250.4; g.harvestKg = 98;
        eq(SeasonReportModel.gardenMeta(g), "98 kg hasat · 1.250 TL işlem", "garden meta: one harvest number");
        eq(SeasonReportModel.kg(12.25), "12,2 kg", "kg one decimal (half-even)");
        eq(SeasonReportModel.tl(-0.2), "0 TL", "tiny negative not shown as minus");
        System.out.println("PASS: " + checks + " season report checks (host only).");
    }
}
