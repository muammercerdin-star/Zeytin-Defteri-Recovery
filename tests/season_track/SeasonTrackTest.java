package com.zeytindefteri.app;

import java.util.Calendar;

public final class SeasonTrackTest {
    static int n, bad;
    static void check(boolean ok, String m) { n++; if (!ok) { bad++; System.out.println("FAIL " + m); } }
    static Calendar day(int y, int m, int d) { Calendar c = Calendar.getInstance(); c.clear(); c.set(y, m, d, 12, 0); return c; }
    public static void main(String[] a) {
        HomeDashboardModel.PhaseProgress p = HomeDashboardModel.phaseProgress(day(2026, Calendar.NOVEMBER, 2));
        check(p.week == 5, "2 Kasım hasadın 5. haftası: " + p.week);
        check(p.weeksLeft == 9, "2 Kasım'dan 31 Aralık'a ~9 hafta: " + p.weeksLeft);
        check(p.fraction > 0.35f && p.fraction < 0.37f, "ilerleme ~%36: " + p.fraction);
        check(HomeDashboardModel.phaseProgressText(5, p).equals("Hasadın 5. haftası · yaklaşık 9 hafta kaldı"),
                HomeDashboardModel.phaseProgressText(5, p));
        HomeDashboardModel.PhaseProgress first = HomeDashboardModel.phaseProgress(day(2026, Calendar.OCTOBER, 1));
        check(first.week == 1 && first.fraction > 0f, "dönemin ilk günü 1. hafta");
        HomeDashboardModel.PhaseProgress last = HomeDashboardModel.phaseProgress(day(2026, Calendar.DECEMBER, 31));
        check(last.weeksLeft == 0 && last.fraction == 1f, "son gün");
        check(HomeDashboardModel.phaseProgressText(5, last).endsWith("son günler"), "son günler metni");
        HomeDashboardModel.PhaseProgress june = HomeDashboardModel.phaseProgress(day(2026, Calendar.JUNE, 25));
        check(june.weeksLeft == 1 && HomeDashboardModel.phaseProgressText(2, june).equals("Meyve tutumunun 4. haftası · bir haftadan az kaldı"),
                HomeDashboardModel.phaseProgressText(2, june));
        HomeDashboardModel.PhaseProgress feb = HomeDashboardModel.phaseProgress(day(2028, Calendar.FEBRUARY, 29));
        check(feb.week == 9 && HomeDashboardModel.phaseProgressText(0, feb).startsWith("Budamanın 9. haftası"), "artık yıl şubat");
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " zeytin takvimi");
        if (bad > 0) System.exit(1);
    }
}
