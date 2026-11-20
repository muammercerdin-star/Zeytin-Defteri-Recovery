package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Soru kartı: tek kart, öncelik, gece kuralı, reddedilen kartın kapanması, "Sonra", hasat mevsimi, ürkütücü gözlem yok. */
public class GuideCardsTest {
    static int n = 0;
    static int bad = 0;

    static void check(boolean ok, String m) {
        n++;
        if (!ok) {
            bad++;
            System.out.println("FAIL " + m);
        }
    }

    static String type(GuideCards.Card c) {
        return c == null ? "yok" : c.type;
    }

    public static void main(String[] a) {
        List<GuideCards.Card> all = new ArrayList<>();
        all.add(GuideCards.habit("Girelli'yi suladın mı?", "son 6 sulamanın 4'ü Girelli'de", "SULAMA"));
        all.add(GuideCards.harvest());
        all.add(GuideCards.payment("Ali Usta", "13 Kasım"));
        Map<String, GuideCards.State> st = new HashMap<>();
        int today = 20261120;
        check(type(GuideCards.pick(all, st, 10, 11, today, false)).equals(GuideCards.PAYMENT), "öncelik: ödeme günü");
        check(GuideCards.pick(all, st, 10, 11, today, false).text.equals("Ali Usta'nın ödeme günü bugün. Parayı aldın mı?"), "ödeme metni");
        check(GuideCards.pick(all, st, 10, 11, today, false).reason.startsWith("Neden: "), "gerekçe yazar");
        // Gece kuralı: 22:00–05:00 hiç kart yok.
        for (int h : new int[]{22, 23, 0, 3, 4}) {
            check(GuideCards.pick(all, st, h, 11, today, false) == null, "gece kart yok: " + h);
        }
        check(GuideCards.pick(all, st, 5, 11, today, false) != null, "05:00'te kart var");
        check(GuideCards.pick(all, st, 21, 11, today, false) != null, "21:00'de kart var");
        // "Sonra": o gün gizler, ertesi gün yine çıkar.
        st.put(GuideCards.PAYMENT, GuideCards.later(st.get(GuideCards.PAYMENT), today));
        check(type(GuideCards.pick(all, st, 10, 11, today, false)).equals(GuideCards.HARVEST), "Sonra: bugün sıradaki kart");
        check(type(GuideCards.pick(all, st, 10, 11, today + 1, false)).equals(GuideCards.PAYMENT), "Sonra: ertesi gün yine");
        check(st.get(GuideCards.PAYMENT).rejects == 0, "Sonra red sayılmaz");
        // Hasat kartı: Ekim–Ocak ve o gün hasat kaydı yoksa.
        check(type(GuideCards.pick(all, st, 10, 11, today, true)).equals(GuideCards.HABIT), "bugün hasat girildiyse hasat kartı yok");
        check(type(GuideCards.pick(all, st, 10, 3, today, false)).equals(GuideCards.HABIT), "mart: hasat kartı yok");
        check(type(GuideCards.pick(all, st, 10, 1, today, false)).equals(GuideCards.HARVEST), "ocak: hasat kartı var");
        // Reddedilen kart türü: üst üste 3 "Bu yanlış" → kapanır; araya kabul girerse sayı sıfırlanır.
        GuideCards.State s = GuideCards.State.empty();
        s = GuideCards.rejected(s);
        s = GuideCards.rejected(s);
        check(!s.closed, "2 red: açık");
        s = GuideCards.accepted(s);
        s = GuideCards.rejected(s);
        s = GuideCards.rejected(s);
        check(!s.closed, "araya kabul girdi: hâlâ açık");
        s = GuideCards.rejected(s);
        check(s.closed, "üst üste 3 red: kapandı");
        Map<String, GuideCards.State> closed = new HashMap<>();
        closed.put(GuideCards.PAYMENT, s);
        check(type(GuideCards.pick(all, closed, 10, 11, today + 5, false)).equals(GuideCards.HARVEST), "kapalı tür hiç çıkmaz");
        check(GuideCards.closed(closed).contains(GuideCards.PAYMENT), "Öğrendiklerim'de kapalı olarak listelenir");
        closed.put(GuideCards.PAYMENT, GuideCards.reopened());
        check(type(GuideCards.pick(all, closed, 10, 11, today + 5, false)).equals(GuideCards.PAYMENT), "yeniden açılınca çıkar");
        // Tek kart: aynı anda en çok bir.
        check(GuideCards.pick(all, new HashMap<>(), 10, 11, today, false) != null, "tek kart döner");
        // Ürkütücü kişisel gözlem yazılmaz.
        GuideCards.Card creepy = GuideCards.habit("Dün gece saat 23'te uygulamayı açtın, uyuyamadın mı?", "telefon", "SULAMA");
        List<GuideCards.Card> only = new ArrayList<>();
        only.add(creepy);
        check(GuideCards.pick(only, new HashMap<>(), 10, 5, today, false) == null, "ürkütücü kart gösterilmez");
        for (GuideCards.Card c : all) {
            check(!GuideCards.creepy(c), "hazır kartlar ürkütücü değil: " + c.type);
        }
        check(GuideCards.genitive("Mehmet").equals("Mehmet'in") && GuideCards.genitive("Ayşe").equals("Ayşe'nin"), "tamlayan eki");
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " GuideCards");
        if (bad > 0) {
            System.exit(1);
        }
    }
}
