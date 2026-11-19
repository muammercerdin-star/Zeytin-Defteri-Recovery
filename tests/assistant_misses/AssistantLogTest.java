package com.zeytindefteri.app;

import java.util.List;

/** Genişletilmiş cümle kaydı (İP1): anlaşılan + eylem, anlaşılmayan, düzeltme; son 300; telefonda. */
public class AssistantLogTest {
    static int n = 0;
    static int bad = 0;

    static void check(boolean ok, String m) {
        n++;
        if (!ok) {
            bad++;
            System.out.println("FAIL " + m);
        }
    }

    public static void main(String[] a) {
        String st = AssistantMisses.log("", AssistantMisses.UNDERSTOOD, "Bugün 3 numara 120 kilo", "ELEK", "1 Kas");
        st = AssistantMisses.log(st, AssistantMisses.MISSED, "bugün bakırladık", "", "1 Kas");
        st = AssistantMisses.log(st, AssistantMisses.CORRECTED, "Elek formu", "3 Numara", "1 Kas");
        List<String[]> l = AssistantMisses.decodeLog(st);
        check(l.size() == 3, "üç tür birlikte: " + st);
        check(l.get(0)[1].equals("duzeltme") && l.get(0)[3].equals("3 Numara"), "en yenisi başta, düzeltilen alan");
        check(l.get(2)[1].equals("anlasilan") && l.get(2)[3].equals("ELEK"), "anlaşılan cümlenin eylemi");

        st = AssistantMisses.log(st, AssistantMisses.UNDERSTOOD, "bugün 3 numara 120 kilo.", "ELEK", "2 Kas");
        l = AssistantMisses.decodeLog(st);
        check(l.size() == 3 && l.get(0)[0].equals("2 Kas"), "aynı anlaşılan cümle tekrar etmez, başa taşınır");
        st = AssistantMisses.log(st, AssistantMisses.MISSED, "Bugün 3 numara 120 kilo", "", "2 Kas");
        check(AssistantMisses.decodeLog(st).size() == 4, "aynı cümle başka türle ayrı satır");
        st = AssistantMisses.log(st, AssistantMisses.CORRECTED, "Elek formu", "3 Numara", "2 Kas");
        check(AssistantMisses.countLog(st, AssistantMisses.CORRECTED) == 2, "düzeltme her seferinde yeni satır");

        check(AssistantMisses.log(st, "baska", "x cümle", "", "2 Kas").equals(st), "bilinmeyen tür girmez");
        check(AssistantMisses.log(st, AssistantMisses.MISSED, "anlamadığın cümleleri göster", "", "2 Kas").equals(st), "komut girmez");
        check(AssistantMisses.log(st, AssistantMisses.MISSED, "   ", "", "2 Kas").equals(st), "boş cümle girmez");
        String tabbed = AssistantMisses.log("", AssistantMisses.UNDERSTOOD, "a\tb\nc", "E\tL", "1\tKas");
        check(AssistantMisses.decodeLog(tabbed).size() == 1 && tabbed.split("\n").length == 1, "sekme/satır sonu bozmaz: " + tabbed);

        String big = "";
        for (int i = 0; i < 320; i++) {
            big = AssistantMisses.log(big, AssistantMisses.UNDERSTOOD, "cümle " + i + " deneme", "ISLEM", "3 Kas");
        }
        List<String[]> bl = AssistantMisses.decodeLog(big);
        check(bl.size() == AssistantMisses.LOG_MAX && AssistantMisses.LOG_MAX == 300, "en fazla 300: " + bl.size());
        check(bl.get(0)[2].equals("cümle 319 deneme"), "en yenisi kalır");

        String cleared = AssistantMisses.clearMissedFromLog(st);
        check(AssistantMisses.countLog(cleared, AssistantMisses.MISSED) == 0, "anlaşılmayanlar silinir");
        check(AssistantMisses.countLog(cleared, AssistantMisses.UNDERSTOOD) == 1, "anlaşılan kalır");
        check(AssistantMisses.countLog(cleared, AssistantMisses.CORRECTED) == 2, "düzeltme kalır");

        // Eski "anlamadıklarım" listesi (gün TAB cümle) ayrı kalır; kayıt satırı listeye karışmaz.
        String misses = AssistantMisses.add("", "Traktörün lastiği patladı", "1 Kas");
        check(AssistantMisses.decodeLog(misses).isEmpty(), "eski liste kayıt sanılmaz");
        check(AssistantMisses.decode(misses).size() == 1 && AssistantMisses.MAX == 30, "eski liste aynen: en fazla 30");
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " AssistantLog");
        if (bad > 0) {
            System.exit(1);
        }
    }
}
