package com.zeytindefteri.app;

import java.util.*;

/** Host-only checks for "Sezon Raporum" display rules. */
public final class SeasonReportModelTest {
    private static int checks;
    private static void eq(Object a, Object e, String l) { if (!Objects.equals(a, e)) throw new AssertionError(l + ": " + a + " != " + e); checks++; }
    public static void main(String[] args) {
        // Yayıncının ekranındaki gerçek durum.
        SeasonReportModel.Data d = new SeasonReportModel.Data();
        d.season = 2026; d.expenses = 6610; d.harvestKg = 190; d.elekKg = 1247; d.elekRevenue = 67837;
        d.brineInKg = 70;
        List<SeasonReportModel.Tile> t = SeasonReportModel.tiles(d);
        eq(t.get(0).value, "6.610 TL", "expenses");
        eq(t.get(1).value, "190 kg", "harvest");
        eq(t.get(2).value, "1.247 kg", "elek");
        eq(t.get(2).note, "tahmini 67.837 TL", "elek estimate note");
        eq(t.get(3).value, "0 TL", "sales");
        eq(t.get(3).tone, SeasonReportModel.Tone.WARNING, "no sales warns");
        eq(t.get(3).note, "satış kaydı yok", "no sales note");

        List<SeasonReportModel.Bar> chain = SeasonReportModel.chain(d);
        eq(chain.get(0).percent, 15, "harvest bar scaled to elek");
        eq(chain.get(1).percent, 100, "elek bar full");
        eq(chain.get(2).percent, 2, "sold bar minimum");
        eq(chain.get(2).tone, SeasonReportModel.Tone.WARNING, "sold warning");

        String[] miss = SeasonReportModel.missingLink(d);
        eq(miss[0], "Eksik halka: satış kaydı", "missing title");
        eq(miss[1].startsWith("1.247 kg elendi ama hiç satış girilmedi."), true, "missing text");
        eq(miss[2], "+ Satış kaydı ekle", "missing action");
        eq(SeasonReportModel.harvestNote(d).startsWith("Hasat 190 kg, elek 1.247 kg"), true, "harvest note");

        SeasonReportModel.Tile net = SeasonReportModel.net(d);
        eq(net.value, "−6.610 TL", "net value with minus sign");
        eq(net.tone, SeasonReportModel.Tone.MUTED, "no sales: not shown as loss");
        eq(net.note, "satış girilmediği için eksik", "net note");

        // Satış girildikten sonra.
        d.salesRevenue = 50000; d.soldKg = 900; d.realSaleCount = 3;
        eq(SeasonReportModel.missingLink(d), null, "no missing link with sales");
        eq(SeasonReportModel.net(d).tone, SeasonReportModel.Tone.POSITIVE, "positive net");
        eq(SeasonReportModel.net(d).value, "43.390 TL", "net positive value");
        eq(SeasonReportModel.tiles(d).get(3).note, "3 gerçek satış", "sales count note");
        d.salesRevenue = 1000;
        eq(SeasonReportModel.net(d).tone, SeasonReportModel.Tone.NEGATIVE, "real loss is red");

        SeasonReportModel.Data empty = new SeasonReportModel.Data();
        eq(SeasonReportModel.missingLink(empty)[0], "Henüz hasat veya elek kaydı yok", "empty state");
        eq(SeasonReportModel.harvestNote(empty), "", "no harvest note");
        eq(SeasonReportModel.chain(empty).get(0).percent, 2, "empty bars minimum");
        SeasonReportModel.Data harvestMore = new SeasonReportModel.Data(); harvestMore.harvestKg = 500; harvestMore.elekKg = 400;
        eq(SeasonReportModel.harvestNote(harvestMore), "", "note only when elek exceeds harvest");

        SeasonReportModel.Garden g = new SeasonReportModel.Garden();
        eq(SeasonReportModel.gardenMeta(g), "Bu sezon kayıt yok", "empty garden");
        g.elekKg = 98; g.operationCost = 1250.4; g.harvestKg = 0;
        eq(SeasonReportModel.gardenMeta(g), "98 kg elek · 1.250 TL işlem", "garden meta");
        eq(SeasonReportModel.kg(12.25), "12,2 kg", "kg one decimal (half-even)");
        eq(SeasonReportModel.tl(-0.2), "0 TL", "tiny negative not shown as minus");
        System.out.println("PASS: " + checks + " season report checks (host only).");
    }
}
