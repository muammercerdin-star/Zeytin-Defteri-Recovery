package com.zeytindefteri.app;

/** Sesle elek girişi: kiloların sınıflara doğru gitmesi, soru cümlelerinin giriş sanılmaması. */
public class ElekVoiceEntryTest {
    static int n = 0, bad = 0;
    static void check(boolean ok, String m) { n++; if (!ok) { bad++; System.out.println("FAIL " + m); } }
    static ElekVoiceEntry p(String s) { return ElekVoiceEntry.parse(s); }
    static boolean kg(ElekVoiceEntry e, double... v) {
        if (e == null) return false;
        for (int i = 0; i < 6; i++) if (Math.abs(e.kg[i] - v[i]) > 1e-9) return false;
        return true;
    }
    public static void main(String[] a) {
        check(kg(p("Bugün 3 numara 120 kilo"), 0, 0, 0, 120, 0, 0), "rakamla, sınıf önce");
        check(kg(p("Bugün 3 numara 120 kilo, 2 numara 80 kilo sattım"), 0, 0, 80, 120, 0, 0), "iki sınıf");
        check(p("Bugün 3 numara 120 kilo, 2 numara 80 kilo sattım").sold == Boolean.TRUE, "sattım → satış");
        check(p("Bugün 3 numara 120 kilo").sold == null, "satış söylenmedi → form varsayılanı");
        check(p("3 numara 120 kilo satmadım").sold == Boolean.FALSE, "satmadım");
        check(kg(p("üç numara yüz yirmi kilo"), 0, 0, 0, 120, 0, 0), "yazıyla sayı");
        check(kg(p("bir numara elli kilo iki numara otuz beş"), 0, 50, 35, 0, 0, 0), "yazıyla iki sınıf, kilo demeden");
        check(kg(p("120 kilo 3 numara, 80 kilo 2 numara"), 0, 0, 80, 120, 0, 0), "önce sayı");
        check(kg(p("duble yirmi kilo yağlık otuz beş buçuk"), 20, 0, 0, 0, 0, 35.5), "double, yağlık, buçuk");
        check(kg(p("Double 13 kilo 1 numara 82 2 numara 235 3 numara 352 4 numara 195 yağlık 72"), 13, 82, 235, 352, 195, 72), "altı sınıf");
        check(kg(p("3. numara 40 kg"), 0, 0, 0, 40, 0, 0), "3. numara, kg");
        check(kg(p("3numara 40kg"), 0, 0, 0, 40, 0, 0), "bitişik yazım");
        check(kg(p("birinci 60 kilo ikinci 40 kilo"), 0, 60, 40, 0, 0, 0), "birinci/ikinci");
        check(kg(p("3 numara 1.200 kilo"), 0, 0, 0, 1200, 0, 0), "binlik nokta");
        check(kg(p("2 numara bin iki yüz elli kilo"), 0, 0, 1250, 0, 0, 0), "bin iki yüz elli");
        check(kg(p("3 numara 12,5 kilo"), 0, 0, 0, 12.5, 0, 0), "virgüllü");
        ElekVoiceEntry b = p("3 numara 120 kilo Ahmet'e sattım");
        check(b != null && b.buyer.equals("Ahmet") && b.sold == Boolean.TRUE, "alıcı adı: " + (b == null ? null : b.buyer));
        ElekVoiceEntry b2 = p("2 numara 50 kilo Mehmet Yılmaz'a sattım");
        check(b2 != null && b2.buyer.equals("Mehmet Yılmaz"), "iki kelimelik alıcı: " + (b2 == null ? null : b2.buyer));
        // Sorular giriş değildir (asistanın cevap yolu çalışır)
        check(p("Bugün 3 numara kaç kilo?") == null, "soru: kaç");
        check(p("3 numara 120 kilo mu") == null, "soru: mu");
        check(p("Bu sezon 3 numara ne kadar") == null, "soru: ne");
        check(p("Son sulama ne zaman?") == null, "ilgisiz soru");
        check(p("Bugün elek kaydı ekle") == null, "sayı yok: ekran açma komutu kalır");
        check(p("3 numara") == null, "kilo yok");
        check(p("120 kilo") == null, "sınıf yok");
        check(p("3 numara 120 kilo sil") == null, "silme komutu giriş değil");
        check(p("Yarın yağmur yağacak mı") == null, "hava sorusu");
        check(p("") == null && p(null) == null, "boş");
        check(p("Bugün 3 numara 120 kilo").summary().equals("3 Numara 120 kg"), "özet");
        check(Math.abs(p("1 numara 10 kilo 2 numara 5").totalKg() - 15) < 1e-9, "toplam");
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " ElekVoiceEntry");
        if (bad > 0) System.exit(1);
    }
}
