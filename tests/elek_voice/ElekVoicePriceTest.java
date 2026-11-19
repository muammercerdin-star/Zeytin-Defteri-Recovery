package com.zeytindefteri.app;

/** İP2: elek cümlesinde lira → kilo başı fiyat; lira yoksa eski davranış. */
public class ElekVoicePriceTest {
    static int n = 0;
    static int bad = 0;

    static void check(boolean ok, String m) {
        n++;
        if (!ok) {
            bad++;
            System.out.println("FAIL " + m);
        }
    }

    static String kg(ElekVoiceEntry e) {
        return e == null ? "null" : java.util.Arrays.toString(e.kg);
    }

    static String price(ElekVoiceEntry e) {
        return e == null ? "null" : java.util.Arrays.toString(e.price);
    }

    public static void main(String[] a) {
        ElekVoiceEntry e = ElekVoiceEntry.parse("double 2 kilo 220 lira");
        check(e != null && e.kg[0] == 2 && e.price[0] == 110, "double 2 kilo 220 lira → 2 kg, 110 TL/kg: " + kg(e) + price(e));
        check(e.summary().equals("Double 2 kg (110 TL/kg)"), "özet fiyatı söyler: " + e.summary());
        e = ElekVoiceEntry.parse("Double iki kilo iki yüz yirmi TL");
        check(e != null && e.kg[0] == 2 && e.price[0] == 110, "yazıyla ve TL: " + kg(e) + price(e));
        e = ElekVoiceEntry.parse("3 numara 120 kilo 110 liradan sattım");
        check(e != null && e.kg[3] == 120 && e.price[3] == 110 && Boolean.TRUE.equals(e.sold), "liradan = kilo başı: " + price(e));
        e = ElekVoiceEntry.parse("double 2 kilo 220 lira 1 numara 3 kilo 300 lira");
        check(e != null && e.price[0] == 110 && e.price[1] == 100, "her sınıfın kendi tutarı: " + price(e));
        e = ElekVoiceEntry.parse("3 numara 120 kilo 2 numara 80 kilo 13.200 lira");
        check(e != null && e.kg[3] == 120 && e.kg[2] == 80 && e.price[3] == 0 && e.price[2] == 0, "tek toplam belirsiz: fiyat yazılmaz: " + price(e));
        e = ElekVoiceEntry.parse("double 3 kilo 100 lira");
        check(e != null && Math.abs(e.price[0] - 33.33) < 0.001, "kuruşa yuvarlanır: " + price(e));
        e = ElekVoiceEntry.parse("double 220 lira");
        check(e == null, "kilosuz lira kilo sayılmaz: " + kg(e));
        // İP2 düzeltme: bölünce kilo fiyatı mantıksız kalırsa lira kilo fiyatıdır; "kilosu" sözü kilo fiyatıdır
        e = ElekVoiceEntry.parse("3 numara 120 kilo 110 lira");
        check(e != null && e.price[3] == 110, "120 kilo 110 lira → kilosu 110: " + price(e));
        e = ElekVoiceEntry.parse("double 150 kilo 140 lira");
        check(e != null && e.price[0] == 140, "150 kilo 140 lira → kilosu 140: " + price(e));
        e = ElekVoiceEntry.parse("double 50 kilo kilosu 140 lira");
        check(e != null && e.price[0] == 140, "kilosu 140 lira: " + price(e));
        e = ElekVoiceEntry.parse("double 50 kilo 140 lira kilosu");
        check(e != null && e.price[0] == 140, "140 lira kilosu: " + price(e));
        e = ElekVoiceEntry.parse("double 50 kilo kilo başı 140 lira");
        check(e != null && e.kg[0] == 50 && e.price[0] == 140, "kilo başı 140 lira: " + kg(e) + price(e));
        e = ElekVoiceEntry.parse("1 numara 200 kilo 25 bin lira");
        check(e != null && e.kg[1] == 200 && e.price[1] == 125, "25 bin lira toplam → 125: " + price(e));
        e = ElekVoiceEntry.parse("3 numara 120 kilo 1 lira");
        check(e != null && e.price[3] == 0, "mantıksız fiyat yazılmaz: " + price(e));
        check(ElekVoiceEntry.priceFromTotal(220, 2) == 110, "priceFromTotal toplam");
        check(ElekVoiceEntry.priceFromTotal(110, 120) == 110, "priceFromTotal kilo fiyatı");
        // Lira yoksa bugünkü davranış aynı
        e = ElekVoiceEntry.parse("Bugün 3 numara 120 kilo, 2 numara 80 kilo sattım");
        check(e != null && e.kg[3] == 120 && e.kg[2] == 80 && e.price[3] == 0 && Boolean.TRUE.equals(e.sold), "lirasız eski davranış");
        e = ElekVoiceEntry.parse("120 kilo üç numara");
        check(e != null && e.kg[3] == 120 && e.price[3] == 0, "önce sayı, lirasız");
        e = ElekVoiceEntry.parse("120 kilo 3 numara 110 liradan");
        check(e != null && e.kg[3] == 120 && e.price[3] == 110, "önce sayı, kilo başı fiyat: " + price(e));

        // Sesli elek (Sesle doldur): fiyat forma gider, geri al fiyatı da geri alır
        ElekVoiceDialog d = new ElekVoiceDialog(new double[6], new double[]{150, 120, 100, 90, 80, 40}, false);
        ElekVoiceDialog.Reply r = d.handle("double 2 kilo 220 lira");
        check(r.action == ElekVoiceDialog.Action.FILL, "doldurur");
        check(d.kg[0] == 2 && d.prices()[0] == 110 && d.prices()[1] == 120, "yalnız söylenen sınıfın fiyatı değişir");
        check(r.speech.contains("kilosu 110 lira"), "fiyatı söyler: " + r.speech);
        check(d.amount() == 220, "tutar 220: " + d.amount());
        d.handle("geri al");
        check(d.kg[0] == 0 && d.prices()[0] == 150, "geri al fiyatı da geri alır: " + d.prices()[0]);
        ElekVoiceDialog plain = new ElekVoiceDialog(new double[6], new double[]{150, 120, 100, 90, 80, 40}, false);
        plain.handle("double 2 kilo");
        check(plain.prices()[0] == 150 && plain.amount() == 300, "lirasız: form fiyatı kalır");
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " ElekVoicePrice");
        if (bad > 0) {
            System.exit(1);
        }
    }
}
