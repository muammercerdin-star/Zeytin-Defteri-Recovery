package com.zeytindefteri.app;

import static com.zeytindefteri.app.LeagueNameAsk.Kind.*;

public class LeagueNameAskTest {
    static int n, fail;
    static void eq(Object want, Object got, String what) {
        n++;
        if (!String.valueOf(want).equals(String.valueOf(got))) { fail++; System.out.println("FAIL " + what + ": " + got + " != " + want); }
    }
    static LeagueNameAsk.Kind d(boolean demo, boolean in, boolean contrib, boolean shown, boolean first, boolean top3, int rank, boolean asked) {
        return LeagueNameAsk.decide(demo, in, contrib, shown, first, top3, rank, asked);
    }
    public static void main(String[] a) {
        // İlk açılış: bir kez sorulur
        eq(FIRST, d(false, true, true, false, false, false, 0, false), "ilk açılış");
        eq(FIRST, d(false, true, true, false, false, false, 7, false), "ilk açılış, 7. sırada");
        eq(NONE, d(false, true, true, false, true, false, 7, false), "ilk soru soruldu, ilk 3'te değil");
        // İlk 3: bir kez
        eq(TOP3, d(false, true, true, false, true, false, 2, false), "ilk 3'e girdi");
        eq(TOP3, d(false, true, true, false, false, false, 1, false), "ilk açılışta zaten 1.");
        eq(NONE, d(false, true, true, false, true, true, 1, false), "ilk 3 sorusu soruldu");
        eq(NONE, d(false, true, true, false, true, false, 4, false), "4. sıra");
        // Asla sorulmayanlar
        eq(NONE, d(false, true, true, true, false, false, 1, false), "ad zaten açık");
        eq(NONE, d(true, true, true, false, false, false, 1, false), "tanıtım verisi");
        eq(NONE, d(false, false, true, false, false, false, 1, false), "oturum yok");
        eq(NONE, d(false, true, false, false, false, false, 1, false), "katkı kapalı");
        eq(NONE, d(false, true, true, false, false, false, 1, true), "bu ekranda soruldu");
        // Metinler: varsayılan yok, iki seçenek, kilo/tutar gizli, ayarlardan değişir
        eq("Adımla görüneyim", LeagueNameAsk.YES, "evet düğmesi");
        eq("Anonim kalayım", LeagueNameAsk.NO, "hayır düğmesi");
        eq(true, LeagueNameAsk.body(FIRST).contains("Kilon ve toplam tutarın kimseye GÖSTERİLMEZ"), "kilo gizli");
        eq(true, LeagueNameAsk.body(FIRST).contains("Ayarlar"), "ayarlardan değişir");
        eq(true, LeagueNameAsk.body(TOP3).startsWith("Tebrikler!"), "gurur metni");
        eq("Alaşehir bölgesinde 2.'sin! 🥈", LeagueNameAsk.title(TOP3, 2, "Alaşehir"), "ilk 3 başlığı");
        eq("Bölgende 1.'sin! 🥇", LeagueNameAsk.title(TOP3, 1, ""), "ilçesiz başlık");
        eq("Ligde adınla görünmek ister misin?", LeagueNameAsk.title(FIRST, 0, "Alaşehir"), "ilk soru başlığı");
        System.out.println(n - fail + "/" + n + " geçti");
        if (fail > 0) System.exit(1);
    }
}
