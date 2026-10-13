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
        System.out.println("PASS: " + checks + " elek insight checks (host only).");
    }
}
