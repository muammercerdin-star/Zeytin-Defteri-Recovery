package com.zeytindefteri.app;

import java.util.List;

public class TanecikLinesTest {
    static int checks;
    static void ok(boolean c, String m) { checks++; if (!c) throw new AssertionError(m); }

    public static void main(String[] a) {
        ok(TanecikLines.sleeping(22) && TanecikLines.sleeping(0) && TanecikLines.sleeping(4), "gece uyur");
        ok(!TanecikLines.sleeping(5) && !TanecikLines.sleeping(21), "gündüz uyumaz");
        ok(TanecikLines.greeting(7).startsWith("Günaydın"), "sabah");
        ok(TanecikLines.greeting(13).startsWith("Kolay gelsin"), "öğle");
        ok(TanecikLines.greeting(17).startsWith("İyi günler"), "ikindi");
        ok(TanecikLines.greeting(20).startsWith("İyi akşamlar"), "akşam");
        for (int m = 1; m <= 12; m++) {
            List<String> s = TanecikLines.season(m);
            ok(!s.isEmpty(), "her ayın sözü var: " + m);
            for (int h = 0; h < 24; h++) for (String l : TanecikLines.lines(h, m)) {
                ok(!l.isEmpty() && l.length() <= 110, "balona sığar (" + l.length() + "): " + l);
            }
        }
        ok(TanecikLines.season(11).get(0).startsWith("Hasat"), "kasım hasat");
        ok(TanecikLines.season(2).get(0).startsWith("Budama"), "şubat budama");
        ok(TanecikLines.season(9).get(0).startsWith("Yağlanma"), "eylül yağlanma");
        int n = TanecikLines.lines(10, 11).size();
        ok(TanecikLines.line(10, 11, 0).equals(TanecikLines.line(10, 11, n)), "döngüsel");
        ok(TanecikLines.line(10, 11, -1).equals(TanecikLines.line(10, 11, n - 1)), "eksi sıra bozulmaz");
        ok(TanecikLines.line(10, 11, 0).startsWith("Günaydın"), "ilk söz selam");
        System.out.println("PASS: " + checks + " Tanecik sözü kontrolü");
    }
}
