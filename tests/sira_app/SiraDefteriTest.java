package com.zeytindefteri.app;

import java.util.HashMap;
import java.util.Map;

/** SiraDefteri saf mantık testleri: ekle/sil, tekillik, biçim, eşik biti, kalıcılık (sahte depo). */
public final class SiraDefteriTest {

    static int n = 0;
    static int bad = 0;

    static void check(boolean ok, String ad) {
        n++;
        if (!ok) {
            bad++;
            System.out.println("FAIL " + ad);
        }
    }

    /** Sahte depo: SharedPreferences yerine bellekteki harita. */
    static final class SahteDepo implements SiraDefteri.Depo {
        final Map<String, String> m = new HashMap<>();

        public String oku(String anahtar) {
            return m.get(anahtar);
        }

        public void yaz(String anahtar, String deger) {
            m.put(anahtar, deger);
        }
    }

    static String token(char c) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 22; i++) {
            b.append(c);
        }
        return b.toString();
    }

    public static void main(String[] args) {
        SahteDepo depo = new SahteDepo();
        SiraDefteri d = new SiraDefteri(depo);

        String a = token('a');
        String b = token('b');

        check(d.tokenlar().isEmpty(), "başta boş");
        check(d.ekle(a), "geçerli token eklenir");
        check(!d.ekle(a), "aynı token tekrar eklenmez");
        check(!d.ekle("kisa"), "geçersiz (22 değil) eklenmez");
        check(d.var(a) && !d.var(b), "var() doğru");
        check(d.tokenlar().size() == 1, "bir token var");

        check(d.ekle(b), "ikinci token");
        check(d.tokenlar().get(0).equals(b), "en yeni başta");

        check(d.esik(a) == 0, "eşik başta 0");
        d.esikIsaretle(a, 1);
        check(d.esik(a) == 1, "yaklaştı biti");
        d.esikIsaretle(a, 2);
        check(d.esik(a) == 3, "ikisi birden (OR)");
        check(d.esik(b) == 0, "öteki token etkilenmez");

        // Kalıcılık: aynı depodan yeni örnek veriyi görür
        SiraDefteri d2 = new SiraDefteri(depo);
        check(d2.tokenlar().size() == 2, "kalıcı: yeni örnek iki token görür");
        check(d2.esik(a) == 3, "kalıcı: eşik saklandı");

        d.sil(a);
        check(!d.var(a) && d.var(b), "silme çalışır");
        check(d.esik(a) == 0, "silinen token eşiği yok");

        // Bozuk depo içeriği çökmemeli
        depo.yaz(SiraDefteri.ANAHTAR, "{bozuk json");
        SiraDefteri d3 = new SiraDefteri(depo);
        check(d3.tokenlar().isEmpty(), "bozuk içerik güvenle boş sayılır");
        check(d3.ekle(a), "bozuktan sonra ekleme çalışır");

        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " SiraDefteri");
        System.exit(bad == 0 ? 0 : 1);
    }
}
