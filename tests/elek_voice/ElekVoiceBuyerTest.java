package com.zeytindefteri.app;

import java.util.Arrays;
import java.util.List;

/** "Kime sattın?": listedeki alıcıyla eşleşme, birden çoksa hangisi, yoksa yeni alıcı. */
public class ElekVoiceBuyerTest {
    static int n = 0, bad = 0;
    static void check(boolean ok, String m) { n++; if (!ok) { bad++; System.out.println("FAIL " + m); } }
    static final List<String> LIST = Arrays.asList("Mehmet Yılmaz", "Mehmet Kaya", "Özer Ağırbaş", "Tüccar Ali");
    static final double[] P = {130, 100, 70, 40, 30, 20};
    public static void main(String[] a) {
        check(ElekVoiceBuyer.spokenName("mehmet yılmaz'a sattım").equals("Mehmet Yılmaz"), "ad temizlenir: " + ElekVoiceBuyer.spokenName("mehmet yılmaz'a sattım"));
        check(ElekVoiceBuyer.spokenName("ismail").equals("İsmail"), "Türkçe büyük İ");
        check(ElekVoiceBuyer.match("Özer", LIST).name.equals("Özer Ağırbaş"), "tek ad → listedeki tam ad");
        check(ElekVoiceBuyer.match("ozer agirbas", LIST).kind == ElekVoiceBuyer.Kind.EXISTING, "Türkçe harfsiz de eşleşir");
        check(ElekVoiceBuyer.match("Mehmet", LIST).kind == ElekVoiceBuyer.Kind.AMBIGUOUS, "iki Mehmet → hangisi");
        check(ElekVoiceBuyer.match("Mehmet Yılmaz", LIST).name.equals("Mehmet Yılmaz"), "tam ad");
        check(ElekVoiceBuyer.match("Ali'ye sattım", LIST).name.equals("Tüccar Ali"), "soyadsız/lakaplı");
        check(ElekVoiceBuyer.match("Özere", LIST).name.equals("Özer Ağırbaş"), "kesmesiz ek: Özere → Özer");
        ElekVoiceBuyer.Match nw = ElekVoiceBuyer.match("Hasan Demir", LIST);
        check(nw.kind == ElekVoiceBuyer.Kind.NEW && nw.name.equals("Hasan Demir"), "listede yok → yeni");
        check(ElekVoiceBuyer.match("sattım", LIST).kind == ElekVoiceBuyer.Kind.NONE, "ad yok");

        // Konuşma: bitti → kime sattın → yeni alıcı → onay → evet
        ElekVoiceDialog d = new ElekVoiceDialog(new double[6], P, true, LIST);
        d.handle("3 numara 120");
        ElekVoiceDialog.Reply r = d.handle("bitti");
        check(d.phase() == ElekVoiceDialog.Phase.ASK_BUYER && r.speech.endsWith("Kime sattın?"), "bitti → kime sattın: " + r.speech);
        r = d.handle("Hasan Demir");
        check(d.phase() == ElekVoiceDialog.Phase.CONFIRM && d.buyer.equals("Hasan Demir") && d.buyerNew, "yeni alıcı");
        check(r.speech.startsWith("Hasan Demir listende yok, yeni alıcı olarak ekleyeceğim.") && r.speech.contains("Alıcı Hasan Demir, yeni alıcı."), "yeni alıcı cümlesi: " + r.speech);
        r = d.handle("evet");
        check(r.action == ElekVoiceDialog.Action.SAVE, "evet → kaydet");

        ElekVoiceDialog e = new ElekVoiceDialog(new double[6], P, true, LIST);
        e.handle("2 numara 50"); e.handle("bitti");
        r = e.handle("Mehmet");
        check(e.phase() == ElekVoiceDialog.Phase.ASK_BUYER && r.speech.equals("Hangisi? Mehmet Yılmaz mı, Mehmet Kaya mı?"), "hangisi: " + r.speech);
        r = e.handle("Kaya");
        check(e.buyer.equals("Mehmet Kaya") && !e.buyerNew && e.phase() == ElekVoiceDialog.Phase.CONFIRM, "Kaya → Mehmet Kaya");

        ElekVoiceDialog f = new ElekVoiceDialog(new double[6], P, true, LIST);
        f.handle("1 numara 10"); f.handle("bitti"); r = f.handle("geç");
        check(f.buyer.isEmpty() && f.phase() == ElekVoiceDialog.Phase.CONFIRM && r.speech.startsWith("Alıcısız"), "geç → alıcısız");

        ElekVoiceDialog g = new ElekVoiceDialog(new double[6], P, true, LIST);
        r = g.handle("3 numara 40 kilo Özer'e sattım bitti");
        check(g.buyer.equals("Özer Ağırbaş") && g.phase() == ElekVoiceDialog.Phase.CONFIRM, "cümlede söylenen alıcı listedekiyle aynı yazılır, tekrar sorulmaz: " + g.buyer + " / " + r.speech);

        ElekVoiceDialog h = new ElekVoiceDialog(new double[6], P, false, LIST);
        h.handle("1 numara 10"); r = h.handle("bitti");
        check(h.phase() == ElekVoiceDialog.Phase.CONFIRM && r.speech.endsWith("Kaydedeyim mi?"), "satış yoksa alıcı sorulmaz");
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " ElekVoiceBuyer");
        if (bad > 0) System.exit(1);
    }
}
