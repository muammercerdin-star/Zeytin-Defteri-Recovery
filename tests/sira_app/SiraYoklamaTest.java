package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/** SiraYoklama tur testleri: eşiğe inene haber, bir kez, ulaşılamayan atlanır, ilerleyince sıra sende. */
public final class SiraYoklamaTest {

    static int n = 0;
    static int bad = 0;

    static void check(boolean ok, String ad) {
        n++;
        if (!ok) {
            bad++;
            System.out.println("FAIL " + ad);
        }
    }

    static final class SahteDepo implements SiraDefteri.Depo {
        final Map<String, String> m = new HashMap<>();

        public String oku(String a) {
            return m.get(a);
        }

        public void yaz(String a, String d) {
            m.put(a, d);
        }
    }

    static SiraDurumu durum(String d, int onunde) {
        JSONObject o = new JSONObject();
        o.put("kod", "0000");
        o.put("durum", d);
        o.put("onunde", onunde);
        return SiraDurumu.coz(o);
    }

    static String token(char c) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 22; i++) {
            b.append(c);
        }
        return b.toString();
    }

    public static void main(String[] args) {
        SiraDefteri defteri = new SiraDefteri(new SahteDepo());
        String a = token('a');
        String b = token('b');
        defteri.ekle(a);
        defteri.ekle(b);

        // a: önünde 2 (yaklaştı), b: önünde 10 (yok)
        Map<String, SiraDurumu> dunya = new HashMap<>();
        dunya.put(a, durum("sirada", 2));
        dunya.put(b, durum("sirada", 10));
        SiraYoklama.Getirici g = dunya::get;

        List<String> haberler = new ArrayList<>();
        SiraYoklama.Haberci h = (token, karar) -> haberler.add(token + ":" + karar.baslik);

        SiraYoklama.tur(defteri, g, h);
        check(haberler.size() == 1 && haberler.get(0).equals(a + ":Sıran yaklaştı"), "yalnız a'ya yaklaştı");
        check(defteri.esik(a) == 1 && defteri.esik(b) == 0, "a biti işaretli, b değil");

        // İkinci tur: aynı eşik tekrar gitmez
        haberler.clear();
        SiraYoklama.tur(defteri, g, h);
        check(haberler.isEmpty(), "aynı eşik ikinci turda gitmez");

        // a ilerledi: önünde 0 → sıra sende
        dunya.put(a, durum("sirada", 0));
        haberler.clear();
        SiraYoklama.tur(defteri, g, h);
        check(haberler.size() == 1 && haberler.get(0).equals(a + ":Sıra sende"), "a'ya sıra sende");
        check(defteri.esik(a) == 3, "a iki bit");

        // Ulaşılamayan (null) atlanır, çökmez
        dunya.put(b, null);
        haberler.clear();
        SiraYoklama.tur(defteri, g, h);
        check(haberler.isEmpty(), "ulaşılamayan token sessizce atlanır");

        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " SiraYoklama");
        System.exit(bad == 0 ? 0 : 1);
    }
}
