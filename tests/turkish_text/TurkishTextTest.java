package com.zeytindefteri.app;

import java.util.List;

/** TurkishText (İP2): tek sadeleştirici, tek sayı okuyucu, sayı + birim. */
public class TurkishTextTest {
    static int n = 0;
    static int bad = 0;

    static void check(boolean ok, String m) {
        n++;
        if (!ok) {
            bad++;
            System.out.println("FAIL " + m);
        }
    }

    static void eq(String got, String want, String m) {
        check(want.equals(got), m + ": «" + got + "» beklenen «" + want + "»");
    }

    static void num(double got, double want, String m) {
        check(Math.abs(got - want) < 1e-9, m + ": " + got + " beklenen " + want);
    }

    public static void main(String[] a) {
        folding();
        normalizing();
        numbers();
        units();
        quantities();
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " TurkishText");
        if (bad > 0) {
            System.exit(1);
        }
    }

    static void folding() {
        eq(TurkishText.fold("İzmit"), "izmit", "İ → i");
        eq(TurkishText.fold("İZMİT"), "izmit", "büyük İZMİT");
        eq(TurkishText.fold("ısı"), "isi", "ı → i");
        eq(TurkishText.fold("ISI"), "isi", "büyük I Türkçede ı'dır");
        eq(TurkishText.fold("Iğdır"), "igdir", "Iğdır");
        eq(TurkishText.fold("IĞDIR"), "igdir", "IĞDIR");
        eq(TurkishText.fold("Çağşüö Ğ Ş Ü Ö Ç"), "cagsuo g s u o c", "çğşüö");
        eq(TurkishText.fold("kâr, hâlâ, îmâ, Üsküdar"), "kar, hala, ima, uskudar", "şapkalı harfler");
        eq(TurkishText.fold("Gubre"), "gubre", "karaktersiz yazım aynı kalır");
        eq(TurkishText.fold(null), "", "null");
        check(TurkishText.fold("gübre").equals(TurkishText.fold("GUBRE")), "Türkçe ve karaktersiz aynı anahtar");
    }

    static void normalizing() {
        eq(TurkishText.normalize("  Bugün   İZMİT'te,  ilaçladık! "), "bugun izmit te ilacladik", "noktalama, boşluk, ek");
        eq(TurkishText.normalize("Girelli'den 300 kilo"), "girelli den 300 kilo", "ekli bahçe adı");
        eq(TurkishText.normalize("Akhisar’da fiyat"), "akhisar da fiyat", "eğik kesme işareti");
        eq(TurkishText.normalize("1500'e patladı"), "1500 patladi", "sayının eki düşer");
        eq(TurkishText.normalize("1.500 lira, 2,5 kilo."), "1.500 lira 2,5 kilo", "sayının içindeki . ve , kalır");
        eq(TurkishText.normalize("120kg 3. numara"), "120 kilo 3 numara", "kg ve sıra noktası");
        eq(TurkishText.normalize("'piyasada' yok"), "piyasada yok", "tırnak içi kelime kalır");
        eq(TurkishText.normalize("ısı kaç derece?"), "isi kac derece", "soru işareti");
        eq(TurkishText.normalize(null), "", "null");
        eq(TurkishText.normalize("?!."), "", "yalnız noktalama");
        check(TurkishText.words("").length == 0, "boş metin kelimesiz");
        eq(String.join("|", TurkishText.words("Iğdır'a 3 kasa")), "igdir|a|3|kasa", "kelimeler");
    }

    static void numbers() {
        num(TurkishText.number("120"), 120, "rakam");
        num(TurkishText.number("on iki"), 12, "on iki");
        num(TurkishText.number("bin beş yüz"), 1500, "bin beş yüz");
        num(TurkishText.number("25 bin"), 25000, "25 bin");
        num(TurkishText.number("25 bin 500"), 25500, "25 bin 500");
        num(TurkishText.number("1,5 milyon"), 1500000, "1,5 milyon");
        num(TurkishText.number("3"), 3, "tek rakam aynı");
        num(TurkishText.number("iki bin beş yüz elli"), 2550, "iki bin beş yüz elli");
        num(TurkishText.number("yüz yirmi"), 120, "yüz yirmi");
        num(TurkishText.number("iki buçuk"), 2.5, "iki buçuk");
        num(TurkishText.number("otuz beş buçuk"), 35.5, "otuz beş buçuk");
        num(TurkishText.number("1.500"), 1500, "1.500 binlik");
        num(TurkishText.number("12.500.000"), 12500000, "çok binlikli");
        num(TurkishText.number("1,5"), 1.5, "1,5 ondalık");
        num(TurkishText.number("2.5"), 2.5, "2.5 ondalık (3 hane değil)");
        num(TurkishText.number("on iki buçuk"), 12.5, "on iki buçuk");
        num(TurkishText.number("ON İKİ"), 12, "büyük harf");
        check(Double.isNaN(TurkishText.number("on iki kilo")), "sayı dışı kelime varsa tamamı sayı değil");
        check(Double.isNaN(TurkishText.number("kilo")), "kelime sayı değil");
        check(Double.isNaN(TurkishText.number("")), "boş");
        String[] t = TurkishText.words("elli beş iki kasa");
        int[] end = new int[1];
        num(TurkishText.number(t, 0, end), 55, "elli beş iki: önce 55");
        check(end[0] == 2, "55'ten sonra 'iki' yeni sayı");
        num(TurkishText.number(t, 2, end), 2, "sonra 2");
        List<Double> all = TurkishText.numbers("üç işçi 1500'e, 2.500 lira ve 12,5 litre");
        check(all.size() == 4, "dört sayı: " + all);
        num(all.get(0), 3, "üç");
        num(all.get(1), 1500, "1500'e");
        num(all.get(2), 2500, "2.500");
        num(all.get(3), 12.5, "12,5");
    }

    static void units() {
        eq(TurkishText.unit("lira"), "lira", "lira");
        eq(TurkishText.unit("tl"), "lira", "tl");
        eq(TurkishText.unit("liradan"), "lira", "liradan");
        eq(TurkishText.unit("kilo"), "kg", "kilo");
        eq(TurkishText.unit("kg"), "kg", "kg");
        eq(TurkishText.unit("kiloyu"), "kg", "ekli kilo");
        eq(TurkishText.unit("kisi"), "kişi", "kişi");
        eq(TurkishText.unit("isci"), "kişi", "işçi");
        eq(TurkishText.unit("saat"), "saat", "saat");
        eq(TurkishText.unit("gram"), "g", "gram");
        eq(TurkishText.unit("litre"), "L", "litre");
        eq(TurkishText.unit("kasa"), "kasa", "kasa");
        eq(TurkishText.unit("cuval"), "çuval", "çuval");
        eq(TurkishText.unit("numara"), "", "numara birim değil");
        eq(TurkishText.unit(null), "", "null");
        check(TurkishText.perUnitPrice("liradan") && TurkishText.perUnitPrice("tlden"), "kilo başı fiyat sözü");
        check(!TurkishText.perUnitPrice("lira"), "lira toplamdır");
    }

    static void quantities() {
        TurkishText.Quantity tl = TurkishText.quantity("50 TL");
        TurkishText.Quantity kg = TurkishText.quantity("50 kilo");
        check(tl != null && tl.value == 50 && tl.unit.equals("lira"), "50 TL → lira: " + tl);
        check(kg != null && kg.value == 50 && kg.unit.equals("kg"), "50 kilo → kg: " + kg);
        check(!tl.unit.equals(kg.unit), "50 TL ile 50 kilo ayrı");
        List<TurkishText.Quantity> q = TurkishText.quantities("double 2 kilo 220 lira");
        check(q.size() == 2, "iki çift: " + q);
        check(q.get(0).value == 2 && q.get(0).unit.equals("kg"), "2 kilo");
        check(q.get(1).value == 220 && q.get(1).unit.equals("lira"), "220 lira");
        List<TurkishText.Quantity> w = TurkishText.quantities("on iki işçi çalıştı bin beş yüz lira");
        check(w.size() == 2 && w.get(0).value == 12 && w.get(0).unit.equals("kişi"), "on iki işçi: " + w);
        check(w.get(1).value == 1500 && w.get(1).unit.equals("lira"), "bin beş yüz lira");
        TurkishText.Quantity bare = TurkishText.quantity("3 numara");
        check(bare != null && bare.unit.isEmpty() && bare.value == 3, "birimsiz sayı");
        check(TurkishText.quantity("kilo yok") == null, "sayı yoksa null");
        List<TurkishText.Quantity> mix = TurkishText.quantities("2 saat sulama 3 çuval gübre 250 gram ilaç");
        check(mix.size() == 3, "üç çift: " + mix);
        eq(mix.get(0).unit + "," + mix.get(1).unit + "," + mix.get(2).unit, "saat,çuval,g", "birimler");
        check(TurkishText.quantity("110 liradan").word.equals("liradan"), "birimin söylendiği hâl");
    }
}
