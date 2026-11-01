package com.zeytindefteri.app;

/** Anlamadığı cümleler listesi: yalnız telefonda, tekrar etmez, en fazla 30, göster/sil komutları. */
public class AssistantMissesTest {
    static int n = 0, bad = 0;
    static void check(boolean ok, String m) { n++; if (!ok) { bad++; System.out.println("FAIL " + m); } }
    public static void main(String[] a) {
        String st = AssistantMisses.add("", "Zeytini ne zaman sıksam iyi olur acep", "1 Kas");
        st = AssistantMisses.add(st, "Traktörün lastiği patladı", "1 Kas");
        st = AssistantMisses.add(st, "zeytini ne zaman sıksam iyi olur acep?", "2 Kas");
        java.util.List<String[]> l = AssistantMisses.decode(st);
        check(l.size() == 2 && l.get(0)[1].endsWith("acep?") && l.get(0)[0].equals("2 Kas"), "tekrar eden cümle başa taşınır: " + st);
        for (int i = 0; i < 40; i++) st = AssistantMisses.add(st, "cümle " + i + " deneme", "3 Kas");
        check(AssistantMisses.decode(st).size() == AssistantMisses.MAX, "en fazla 30");
        String[] sh = AssistantMisses.show(st);
        check(sh[0].contains("30 cümle") && sh[1].contains("son 10 cümle (toplam 30)") && sh[1].contains("cümle 39 deneme"), "gösterim: " + sh[1].substring(0, 60));
        check(AssistantMisses.show("")[0].startsWith("Şimdilik"), "boş liste");
        check(AssistantMisses.asksShow("Anlamadığın cümleleri göster") && AssistantMisses.asksShow("neleri anlamadın"), "göster komutu");
        check(AssistantMisses.asksClear("anlamadıklarını sil") && !AssistantMisses.asksShow("anlamadıklarını sil"), "sil komutu");
        check(!AssistantMisses.asksShow("anlamıyorsun sen") && !AssistantMisses.asksShow("ben anlamadım"), "kızgınlık/anlamadım komut sanılmaz");
        check(AssistantMisses.add(st, "anlamadığın cümleleri göster", "3 Kas").equals(st), "komutun kendisi listeye girmez");
        check(AssistantMisses.add("", "a\tb\nc", "1 Kas").split("\n").length == 1, "sekme/satır sonu bozmaz");
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " AssistantMisses");
        if (bad > 0) System.exit(1);
    }
}
