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
        // Yayıncının ekranı: hasat 190 kg, masraf 6.610 TL, satış 4.250 TL, elek 1.247 kg.
        List<HomeSummaryModel.Metric> m = HomeSummaryModel.season(190, 1247, 6610, 4250);
        eq(m.size(), 4, "four metrics");
        eq(m.get(0).label + "=" + m.get(0).value, "Hasat=190 kg", "harvest");
        eq(m.get(1).label + "=" + m.get(1).value, "Elek=1.247 kg", "elek added");
        eq(m.get(2).value, "6.610 TL", "expenses");
        eq(m.get(2).tone, HomeSummaryModel.Tone.NORMAL, "expenses not alarm red");
        eq(m.get(3).tone, HomeSummaryModel.Tone.POSITIVE, "sales positive");
        eq(HomeSummaryModel.season(0, 0, 0, 0).get(3).tone, HomeSummaryModel.Tone.WARNING, "no sales warns");

        List<HomeSummaryModel.GradePrice> all = Arrays.asList(
                g("Double", 10, 1200, 2), g("1 No", 0, 0, 0), g("2 No", 235, 14100, 11),
                g("3 No", 120, 6000, 5), g("4 No", 50, 0, 1), g("Yağlık", 5, 50, 1));
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
