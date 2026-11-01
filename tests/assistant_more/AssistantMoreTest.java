package com.zeytindefteri.app;

import java.util.Arrays;
import java.util.List;

/** Sesle hasat ve alacak soruları. */
public class AssistantMoreTest {
    static int n = 0, bad = 0;
    static void check(boolean ok, String m) { n++; if (!ok) { bad++; System.out.println("FAIL " + m); } }
    public static void main(String[] a) {
        // ---- hasat
        HarvestVoiceEntry h = HarvestVoiceEntry.parse("Girelli'den 300 kilo topladık");
        check(h != null && h.total == 300 && !h.graded(), "toplam 300 kg");
        h = HarvestVoiceEntry.parse("Dün 3 numara 120, 2 numara 80 hasat ettik");
        check(h != null && h.kg[3] == 120 && h.kg[2] == 80 && h.total == 200 && h.dayOffset == -1, "boy boy hasat + dün");
        h = HarvestVoiceEntry.parse("Bugün 1,5 ton topladık");
        check(h != null && h.total == 1500, "ton");
        h = HarvestVoiceEntry.parse("Bugün beş yüz kilo zeytin çırptık");
        check(h != null && h.total == 500, "yazıyla, çırptık");
        check(HarvestVoiceEntry.parse("Bu sezon kaç kilo topladım?") == null, "soru değil");
        check(HarvestVoiceEntry.parse("bu sezon kac kilo topladim") == null, "soru (işaretsiz)");
        check(HarvestVoiceEntry.parse("3 numara 120 kilo sattım") == null, "satış hasat değil");
        check(HarvestVoiceEntry.parse("topladık") == null, "kilosuz kayıt değil");

        // ---- alacak
        List<AssistantReceivables.Entry> b = Arrays.asList(
                new AssistantReceivables.Entry("Ahmet Kaya", 4800, 2),
                new AssistantReceivables.Entry("Ahmet Demir", 0, 1),
                new AssistantReceivables.Entry("Mehmet Yılmaz", 7500, 1),
                new AssistantReceivables.Entry("Ali Veli", 1200, 1),
                new AssistantReceivables.Entry("Hasan Çelik", 300, 1));
        check(AssistantReceivables.asks("Ahmet'ten ne kadar alacağım var?"), "alacak sorusu");
        check(AssistantReceivables.asks("Kim bana borçlu?"), "kim borçlu");
        check(!AssistantReceivables.asks("Bu sezon satışım ne?"), "satış sorusu alacak değil");
        check(!AssistantReceivables.asks("Kime borcum var?"), "tüccar borcu üretici alacağı değil");
        String r = AssistantReceivables.answer("Kim bana borçlu?", b);
        check(r.startsWith("Toplam 13.800 lira alacağın var. Mehmet Yılmaz 7.500, Ahmet Kaya 4.800, Ali Veli 1.200.") && r.contains("1 kişi daha"), "toplam: " + r);
        r = AssistantReceivables.answer("Mehmet'ten ne kadar alacağım var?", b);
        check(r.equals("Mehmet Yılmaz sana 7.500 lira borçlu."), "kişi: " + r);
        r = AssistantReceivables.answer("Ahmet Kaya'dan alacağım ne kadar", b);
        check(r.equals("Ahmet Kaya sana 4.800 lira borçlu, 2 satıştan."), "tam ad: " + r);
        r = AssistantReceivables.answer("Ahmet'in borcu var mı", b);
        check(r.contains("Ahmet Kaya 4.800 lira") && r.contains("Ahmet Demir borcu yok"), "iki Ahmet: " + r);
        r = AssistantReceivables.answer("Ahmet Demir'den alacağım var mı", b);
        check(r.contains("açık alacağın yok"), "ödenmiş: " + r);
        r = AssistantReceivables.answer("Hüseyin'den alacağım var mı", b);
        check(r.startsWith("Hüseyin adında bir alıcın yok"), "listede yok: " + r);
        r = AssistantReceivables.answer("Alacaklarım", Arrays.asList(new AssistantReceivables.Entry("Ali", 0, 3)));
        check(r.startsWith("Açık alacağın yok"), "hiç alacak yok: " + r);
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " AssistantMore");
        if (bad > 0) System.exit(1);
    }
}
