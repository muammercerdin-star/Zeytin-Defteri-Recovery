package com.zeytindefteri.app;

import java.util.*;

/** Host-only checks for the Home lower section rules. */
public final class HomeSummaryModelTest {
    private static int checks;
    private static void eq(Object a, Object e, String l) { if (!Objects.equals(a, e)) throw new AssertionError(l + ": " + a + " != " + e); checks++; }
    private static HomeSummaryModel.GradePrice g(String label, double kg, double revenue, int records) {
        HomeSummaryModel.GradePrice x = new HomeSummaryModel.GradePrice(); x.label = label; x.kg = kg; x.revenue = revenue; x.records = records; return x;
    }
    public static void main(String[] args) {
        // Hasat elek dahil (1.307 kg), masraf 22.960 TL, satış 62.825 TL; ayrı "Elek" sütunu yok.
        List<HomeSummaryModel.Metric> m = HomeSummaryModel.season(1307, 22960, 62825);
        eq(m.size(), 3, "three metrics");
        eq(m.get(0).label + "=" + m.get(0).value, "Hasat=1.307 kg", "harvest includes elek");
        eq(m.get(1).value, "22.960 TL", "expenses");
        eq(m.get(1).tone, HomeSummaryModel.Tone.NORMAL, "expenses not alarm red");
        eq(m.get(2).tone, HomeSummaryModel.Tone.POSITIVE, "sales positive");
        eq(HomeSummaryModel.season(0, 0, 0).get(2).tone, HomeSummaryModel.Tone.WARNING, "no sales warns");
        for (HomeSummaryModel.Metric x : m) if ("Elek".equals(x.label)) throw new AssertionError("no separate elek column");

        eq(HomeSummaryModel.compareLine(38.8, 35), "Bölge 35 · +3,8 TL", "above region");
        eq(HomeSummaryModel.compareLine(60, 66.5), "Bölge 66,5 · −6,5 TL", "below region");
        eq(HomeSummaryModel.compareLine(35.2, 35), "Bölge 35 · aynı", "same");
        eq(HomeSummaryModel.compareLine(38.8, 0), "", "no region data");
        eq(HomeSummaryModel.compareTone(38.8, 35), 1, "tone up");
        eq(HomeSummaryModel.compareTone(30, 35), -1, "tone down");
        eq(HomeSummaryModel.priceNumber(38.79), "38,8", "number only");

        List<HomeSummaryModel.GradePrice> all = Arrays.asList(
                g("Double", 10, 1200, 2), g("1 No", 0, 0, 0), g("2 No", 235, 14100, 11),
                g("3 No", 120, 6000, 5), g("4 No", 50, 0, 1), g("Yağlık", 5, 50, 1));
        List<HomeSummaryModel.GradePrice> rows = HomeSummaryModel.withData(all);
        eq(rows.size(), 4, "graded rows with price (1 No empty, 4 No without revenue skipped)");
        eq(rows.get(0).label, "Double", "elek order kept");
        List<HomeSummaryModel.GradePrice> top = HomeSummaryModel.top(all, 2);
        eq(top.size(), 2, "top two");
        eq(top.get(0).label, "2 No", "largest kg first");
        eq(top.get(1).label, "3 No", "second");
        eq(HomeSummaryModel.priceLine(top.get(0)), "60 TL/kg", "2 No price from screenshot");
        eq(HomeSummaryModel.detailLine(top.get(0)), "11 elek kaydı · 235 kg", "honest label: elek records, not sales");
        eq(HomeSummaryModel.top(Arrays.asList(g("4 No", 50, 0, 1)), 2).size(), 0, "grades without price skipped");
        eq(HomeSummaryModel.price(52.55), "52,5 TL/kg", "one decimal (half-even)");
        eq(HomeSummaryModel.OPERATION_TYPES[1], "Gübre", "operation type matches NewOperationActivity (was 'Gübreleme', never matched)");
        System.out.println("PASS: " + checks + " home lower section checks (host only).");
    }
}
