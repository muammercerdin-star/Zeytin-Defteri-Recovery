package com.zeytindefteri.app;

import java.util.*;

/** Host-only checks for the redesigned home screen model. No Android, network or database. */
public final class HomeDashboardModelTest {
    private static int checks;
    private static void eq(Object actual, Object expected, String label) {
        if (!Objects.equals(actual, expected)) throw new AssertionError(label + ": " + actual + " != " + expected);
        checks++;
    }
    private static void ok(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    private static Calendar day(int y, int m, int d, int h) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, d, h, 0);
        return c;
    }
    public static void main(String[] args) {
        // Season phases (Calendar months 0..11)
        int[] expected = {0, 0, 0, 1, 1, 2, 3, 3, 4, 5, 5, 5};
        for (int m = 0; m < 12; m++) eq(HomeDashboardModel.seasonPhase(m), expected[m], "phase " + m);
        eq(HomeDashboardModel.PHASE_NAMES.length, HomeDashboardModel.PHASE_MONTHS.length, "phase arrays");
        eq(HomeDashboardModel.phaseTitle(5), "Hasat dönemi", "harvest title");
        eq(HomeDashboardModel.lastHarvestText(-1), "Bu sezon hasat kaydı yok", "no harvest");
        eq(HomeDashboardModel.lastHarvestText(0), "Son hasat bugün", "today harvest");
        eq(HomeDashboardModel.lastHarvestText(40), "Son hasat 40 gün önce", "40 days");

        // Greeting names: initials never shown alone
        eq(HomeDashboardModel.displayFirstName("Muammer Çerdin"), "Muammer", "first name");
        eq(HomeDashboardModel.displayFirstName("M. Çerdin"), "M. Çerdin", "initial uses full name");
        eq(HomeDashboardModel.displayFirstName("M."), "M", "lone initial");
        eq(HomeDashboardModel.displayFirstName("  "), "Üretici", "blank");
        eq(HomeDashboardModel.displayFirstName(null), "Üretici", "null");
        eq(HomeDashboardModel.initials("muammer çerdin"), "MÇ", "initials tr");
        eq(HomeDashboardModel.initials("İsmail"), "İ", "single initial");
        eq(HomeDashboardModel.initials(""), "Ü", "empty initials");

        Calendar now = day(2026, 10, 13, 22);
        eq(HomeDashboardModel.dateLine(now), "Salı · 13 Ekim", "date line");
        eq(HomeDashboardModel.whenLabel("2026-10-13T15:00", now), "Bugün 15:00", "today hour");
        eq(HomeDashboardModel.whenLabel("2026-10-14T07:00", now), "Yarın 07:00", "tomorrow hour");
        eq(HomeDashboardModel.whenLabel("2026-10-16", now), "Cuma", "weekday");
        eq(HomeDashboardModel.whenLabel("2026-10-30", now), "2026-10-30", "far date stays iso");
        eq(HomeDashboardModel.whenLabel("bozuk", now), "bozuk", "invalid passthrough");
        eq(HomeDashboardModel.shortDay("2026-10-13", now), "Bugün", "short today");
        eq(HomeDashboardModel.shortDay("2026-10-15", now), "Per", "short thursday");

        // Conditions with the screenshot's real numbers (13.10.2026)
        List<HomeDashboardModel.Hour> hours = new ArrayList<>();
        for (int h = 23; h < 24 + 23; h++) {
            String iso = String.format(Locale.US, "2026-10-%02dT%02d:00", 13 + h / 24, h % 24);
            double rain = iso.equals("2026-10-14T07:00") ? 63 : 20;
            hours.add(new HomeDashboardModel.Hour(iso, rain, 6, 17));
        }
        List<HomeDashboardModel.Day> days = Arrays.asList(
                new HomeDashboardModel.Day("2026-10-13", 65, 26, 17),
                new HomeDashboardModel.Day("2026-10-14", 88, 19, 16),
                new HomeDashboardModel.Day("2026-10-15", 53, 18, 14),
                new HomeDashboardModel.Day("2026-10-16", 0, 17, 13),
                new HomeDashboardModel.Day("2026-10-17", 65, 18, 11));
        List<HomeDashboardModel.Condition> c = HomeDashboardModel.conditions(5, hours, days, now);
        eq(c.size(), 3, "three rows");
        eq(c.get(0).title, "Yağış", "rain title");
        eq(c.get(0).value, "Yarın 07:00 · %63", "rain value");
        eq(c.get(0).tone, HomeDashboardModel.Tone.ATTENTION, "rain tone");
        eq(c.get(1).value, "Şimdi 5 km/s", "wind calm");
        eq(c.get(1).tone, HomeDashboardModel.Tone.NORMAL, "wind tone");
        eq(c.get(2).title, "En kuru gün", "driest title");
        eq(c.get(2).value, "Cuma · %0", "driest value");
        for (HomeDashboardModel.Condition x : c) {
            String all = (x.title + " " + x.value + " " + x.note).toLowerCase(new Locale("tr"));
            ok(!all.contains("uygun değil") && !all.contains("uygundur") && !all.contains("yapabilirsin"),
                    "no agronomic decision: " + all);
        }

        // Wind attention and cold override driest-day row
        List<HomeDashboardModel.Hour> windy = new ArrayList<>();
        windy.add(new HomeDashboardModel.Hour("2026-10-13T23:00", 10, 30, 5));
        List<HomeDashboardModel.Day> cold = Arrays.asList(
                new HomeDashboardModel.Day("2026-10-14", 10, 8, -6),
                new HomeDashboardModel.Day("2026-10-15", 0, 9, 2));
        List<HomeDashboardModel.Condition> w = HomeDashboardModel.conditions(12, windy, cold, now);
        eq(w.get(0).value, "1 saatte en fazla %10", "short horizon rain");
        eq(w.get(1).tone, HomeDashboardModel.Tone.ATTENTION, "wind attention at 30");
        eq(w.get(1).value, "Bugün 23:00 · 30 km/s", "wind value");
        eq(w.get(2).title, "Soğuk", "cold row");
        eq(w.get(2).tone, HomeDashboardModel.Tone.CRITICAL, "critical cold");

        // Missing data never crashes and yields no fake rows
        eq(HomeDashboardModel.conditions(Double.NaN, new ArrayList<>(), new ArrayList<>(), now).size(), 0, "empty");

        eq(HomeDashboardModel.rainBar(0, 40), 3, "min bar");
        eq(HomeDashboardModel.rainBar(65, 40), 26, "65% bar");
        eq(HomeDashboardModel.rainBar(150, 40), 40, "clamped bar");
        eq(HomeDashboardModel.rainBar(Double.NaN, 40), 3, "nan bar");
        eq(HomeDashboardModel.plainTitle("🌧 Yağış olasılığı yüksek"), "Yağış olasılığı yüksek", "strip emoji");
        eq(HomeDashboardModel.plainTitle("📋 İşlem geçmişi"), "İşlem geçmişi", "strip emoji 2");
        eq(HomeDashboardModel.tagFor("history"), "KAYIT", "tag history");
        eq(HomeDashboardModel.tagFor("rain"), "HAVA", "tag rain");

        eq(HomeDashboardModel.gardenMeta("Gemlik", 100, 10), "Gemlik · 100 ağaç · 10 yaş", "garden meta");
        eq(HomeDashboardModel.gardenMeta("", 80, 0), "80 ağaç", "garden meta partial");
        eq(HomeDashboardModel.gardenMeta(null, 0, 0), "Bilgileri tamamlanmamış", "garden meta empty");
        eq(HomeDashboardModel.gardensOverline(2026, 3), "2026 SEZONU · 3 BAHÇE", "overline");

        // Home assistant shortcuts must map to commands the assistant understands.
        for (String q : new String[]{"Bugün bahçede ne var?", "Yarın yağmur yağacak mı?", "Son sulama ne zaman?"}) {
            ok(ZeytinAssistantCommand.parse(q).kind != ZeytinAssistantCommand.Kind.UNKNOWN, "assistant understands: " + q);
        }
        System.out.println("PASS: " + checks + " home dashboard checks (host only).");
    }
}
