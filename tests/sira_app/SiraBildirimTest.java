package com.zeytindefteri.app;

import org.json.JSONObject;

/** SiraBildirim karar testleri: eşik, bir-kez-gönder, mesaj metinleri (web ile aynı). */
public final class SiraBildirimTest {

    static int n = 0;
    static int bad = 0;

    static void check(boolean ok, String ad) {
        n++;
        if (!ok) {
            bad++;
            System.out.println("FAIL " + ad);
        }
    }

    static SiraDurumu durum(String d, int onunde) {
        JSONObject o = new JSONObject();
        o.put("kod", "4822");
        o.put("durum", d);
        o.put("onunde", onunde);
        return SiraDurumu.coz(o);
    }

    public static void main(String[] args) {
        // Önünde 5, esik 3 → haber yok
        check(!SiraBildirim.karar(durum("sirada", 5), 0, 3).gonder, "önünde 5: gönderme");

        // Önünde 3, hiç gönderilmemiş → yaklaştı (bit 1)
        SiraBildirim.Karar k1 = SiraBildirim.karar(durum("sirada", 3), 0, 3);
        check(k1.gonder && k1.bit == 1, "önünde 3: yaklaştı bit 1");
        check(k1.baslik.equals("Sıran yaklaştı"), "yaklaştı başlığı");
        check(k1.metin.contains("Önünde 3 kişi") && k1.metin.contains("kod 4822"), "yaklaştı metni önünde + kod");

        // Aynı eşik gönderilmişse tekrar yok
        check(!SiraBildirim.karar(durum("sirada", 3), 1, 3).gonder, "yaklaştı zaten gönderildi: yok");

        // Önünde 0 → sıra sende (bit 2)
        SiraBildirim.Karar k2 = SiraBildirim.karar(durum("sirada", 0), 0, 3);
        check(k2.gonder && k2.bit == 2, "önünde 0: sıra sende bit 2");
        check(k2.baslik.equals("Sıra sende") && k2.metin.contains("kod 4822"), "sıra sende metni");

        // İşleniyor, yaklaştı gönderilmiş ama sıra sende değil → gönder
        check(SiraBildirim.karar(durum("isleniyor", 0), 1, 3).gonder, "işleniyor: sıra sende gider");
        // İşleniyor, ikisi de gönderilmiş → yok
        check(!SiraBildirim.karar(durum("isleniyor", 0), 3, 3).gonder, "ikisi gönderilmiş: yok");

        // Bitti → yok
        check(!SiraBildirim.karar(durum("bitti", 0), 0, 3).gonder, "bitti: gönderme");

        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " SiraBildirim");
        System.exit(bad == 0 ? 0 : 1);
    }
}
