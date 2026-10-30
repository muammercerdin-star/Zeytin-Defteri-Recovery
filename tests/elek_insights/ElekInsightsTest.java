package com.zeytindefteri.app;

import java.util.*;

/** Host-only checks for the Elek summary card helpers. */
public final class ElekInsightsTest {
    private static int checks;
    private static void eq(Object a, Object e, String l) { if (!Objects.equals(a, e)) throw new AssertionError(l + ": " + a + " != " + e); checks++; }
    public static void main(String[] args) {
        double[] none = {0, 0, 0, 0, 0, 0};
        eq(ElekInsights.total(none), 0.0, "empty total");
        eq(Arrays.toString(ElekInsights.shares(none)), "[0, 0, 0, 0, 0, 0]", "empty shares");
        eq(ElekInsights.legend(none), "Henüz miktar girilmedi", "empty legend");
        eq(ElekInsights.accessibleLegend(none), "Henüz miktar girilmedi.", "empty a11y");

        double[] kg = {120, 100, 0, 0, 0, 2.5};
        eq(ElekInsights.total(kg), 222.5, "total");
        int[] s = ElekInsights.shares(kg);
        eq(s[0] + s[1] + s[2] + s[3] + s[4] + s[5], 100, "shares sum to 100");
        eq(s[0], 54, "double share");
        eq(s[1], 45, "first share");
        eq(s[5], 1, "oil share");
        eq(ElekInsights.legend(kg), "Double %54  ·  1 %45  ·  Yağlık %1", "legend");
        eq(ElekInsights.accessibleLegend(kg), "Sınıf dağılımı: Double yüzde 54, 1 Numara yüzde 45, Yağlık yüzde 1.", "a11y legend");

        double[] thirds = {1, 1, 1, 0, 0, 0};
        int[] t = ElekInsights.shares(thirds);
        eq(t[0] + t[1] + t[2], 100, "rounding repaired");
        eq(ElekInsights.shares(new double[]{-5, 10, 0, 0, 0, 0})[1], 100, "negative ignored");

        eq(ElekInsights.saveHint(0, true, "Edremit", "13 Ekim 2026"), "Kaydetmek için en az bir sınıfa kilo gir.", "hint empty");
        eq(ElekInsights.saveHint(10, false, "", "13 Ekim 2026"), "Kaydetmeden önce bahçe seç.", "hint no garden");
        eq(ElekInsights.saveHint(10, true, "Edremit", "13 Ekim 2026"), "Edremit · 13 Ekim 2026 olarak kaydedilecek.", "hint ready");
        eq(ElekInsights.gardenLabel(5, 5), "Bahçe · aktif", "active label");
        eq(ElekInsights.gardenLabel(5, 7), "Bahçe", "other garden");
        eq(ElekInsights.gardenLabel(0, 0), "Bahçe", "none selected");
        eq(ElekInsights.topGrade(new double[]{3, 15, 25, 40, 10, 5}), "3 Numara", "top grade");
        eq(ElekInsights.topGrade(new double[]{10, 10, 0, 0, 0, 0}), "Double", "tie keeps upper grade");
        eq(ElekInsights.topGrade(none), "—", "no top grade");
        eq(ElekInsights.formatDate("2026-10-13", false), "13 Ekim 2026", "long date");
        eq(ElekInsights.formatDate("2026-10-13", true), "13 Ekim", "short date: full month, no year");
        eq(ElekInsights.formatDate("2026-05-01", true), "1 Mayıs", "short may");
        eq(ElekInsights.formatDate("13.10.2026", false), "13.10.2026", "unknown format passthrough");
        eq(ElekInsights.formatDate(null, true), "", "null date");
        eq(ElekInsights.formatPerKg(4910, 98), "50,1 TL/kg", "per kg from screenshot");
        eq(ElekInsights.formatPerKg(100, 0), "— TL/kg", "no kg");
        eq(ElekInsights.recordCountOverline(12), "12 KAYIT", "overline");
        String share = ElekInsights.receiptShareText("Girelli Zeytinliği", "13 Ekim 2026",
                new double[]{3, 15, 0, 40, 0, 2.5},
                new String[]{"120 TL", "80 TL", "60 TL", "40 TL", "20 TL", "10 TL"},
                new String[]{"360 TL", "1.200 TL", "—", "1.600 TL", "—", "25 TL"},
                "60,5 kg", "3.185 TL", "52,6 TL/kg", "  Kasa 3 ");
        eq(share.startsWith("Zeytin Defteri · Elek makbuzu\nGirelli Zeytinliği · 13 Ekim 2026\n\n"), true, "share header");
        eq(share.contains("Double: 3 kg × 120 TL = 360 TL\n"), true, "share line");
        eq(share.contains("Yağlık: 2,5 kg × 10 TL = 25 TL\n"), true, "share decimal kg");
        eq(share.contains("2 Numara"), false, "empty grades skipped");
        eq(share.contains("\nToplam: 60,5 kg · 3.185 TL (ort. 52,6 TL/kg)"), true, "share total");
        eq(share.endsWith("\nNot: Kasa 3"), true, "share note trimmed");
        eq(ElekInsights.receiptShareText("A", "B", new double[]{1, 0, 0, 0, 0, 0},
                new String[]{"1 TL", "", "", "", "", ""}, new String[]{"1 TL", "", "", "", "", ""},
                "1 kg", "1 TL", "", "").contains("Not:"), false, "no empty note");
        eq(ElekInsights.shortWeekday("Salı"), "SAL", "weekday sal");
        eq(ElekInsights.shortWeekday("Cuma"), "CUM", "weekday cuma");
        eq(ElekInsights.shortWeekday("Cumartesi"), "CMT", "weekday cumartesi differs from cuma");
        eq(ElekInsights.shortWeekday("Çarşamba"), "ÇAR", "weekday çarşamba");
        eq(ElekInsights.shortWeekday(""), "", "weekday unknown");
        eq(ElekInsights.monthHeader("2026-10-13"), "EKİM 2026", "month header dotted İ");
        eq(ElekInsights.monthHeader("2026-08-01"), "AĞUSTOS 2026", "month header ğ");
        eq(ElekInsights.monthHeader("13.10.2026"), "", "month header unknown");
        System.out.println("PASS: " + checks + " elek insight checks (host only).");
    }
}
