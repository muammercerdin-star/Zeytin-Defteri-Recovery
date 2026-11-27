package com.zeytindefteri.app;

import org.json.JSONObject;

/** SiraDurumu saf model testleri: çözme, adım, önünde metni, haber eşiği (web ile aynı), randıman. */
public final class SiraDurumuTest {

    static int n = 0;
    static int bad = 0;

    static void check(boolean ok, String ad) {
        n++;
        if (!ok) {
            bad++;
            System.out.println("FAIL " + ad);
        }
    }

    static void esit(Object a, Object b, String ad) {
        check(a == null ? b == null : a.equals(b), ad + ": " + a + " beklenen " + b);
    }

    static JSONObject taban(String durum, int onunde) {
        JSONObject o = new JSONObject();
        o.put("fabrika", "Girelli Yağhanesi");
        o.put("kod", "4822");
        o.put("sira_no", 12);
        o.put("durum", durum);
        o.put("onunde", onunde);
        o.put("kasa", 35);
        o.put("kg", 700);
        o.put("gelis", "22:14");
        o.put("guncel", "22:30");
        return o;
    }

    public static void main(String[] args) {
        // Çözme: sırada
        JSONObject s = taban("sirada", 11);
        s.put("tahmini", "yarın 14:00");
        s.put("yag_kg", JSONObject.NULL);
        SiraDurumu d = SiraDurumu.coz(s.toString());
        esit(d.fabrika, "Girelli Yağhanesi", "fabrika çözüldü");
        esit(d.kod, "4822", "kod");
        esit(d.siraNo, 12, "sıra no");
        esit(d.onunde, 11, "önünde");
        esit(d.kg, 700.0, "kg");
        esit(d.gelis, "22:14", "geliş");
        check(d.yagKg == null, "yag_kg null okunur");
        esit(d.adim(), SiraDurumu.ADIM_SIRADA, "adım sırada=1");
        check(d.aktif() && !d.tamam(), "sırada aktif");
        esit(d.gosterilenTahmin(), "yarın 14:00", "sırada tahmin gösterilir");
        esit(d.onundeMetni(), "Önünde 11 kişi", "önünde metni");

        // İşleniyor
        JSONObject i = taban("isleniyor", 0);
        i.put("biter", "yarın 15:00");
        SiraDurumu di = SiraDurumu.coz(i);
        esit(di.adim(), SiraDurumu.ADIM_ISLENIYOR, "adım işleniyor=2");
        esit(di.gosterilenTahmin(), "yarın 15:00", "işleniyorsa bitiş gösterilir");
        esit(di.onundeMetni(), "", "işleniyorsa önünde metni boş");
        check(di.aktif(), "işleniyor aktif");

        // Bitti + yağ → randıman
        JSONObject b = taban("bitti", 0);
        b.put("yag_kg", 112);
        SiraDurumu db = SiraDurumu.coz(b);
        esit(db.adim(), SiraDurumu.ADIM_BITTI, "adım bitti=3");
        check(db.tamam() && !db.aktif(), "bitti tamam");
        check(db.yagKg != null && Math.abs(db.yagKg - 112) < 0.001, "yağ kg okunur");
        check(db.randiman() != null && Math.abs(db.randiman() - 16.0) < 0.001, "randıman 112/700=%16");

        // İptal
        SiraDurumu dp = SiraDurumu.coz(taban("iptal", 0));
        esit(dp.adim(), SiraDurumu.ADIM_IPTAL, "adım iptal=-1");
        check(dp.tamam(), "iptal tamam");
        check(dp.randiman() == null, "iptalde randıman yok");

        // Önünde 0 sırada
        SiraDurumu d0 = SiraDurumu.coz(taban("sirada", 0));
        esit(d0.onundeMetni(), "Sıradaki sensin.", "önünde 0 metni");

        // Haber eşiği (esik=3) — web tetiklemesiyle aynı
        esit(SiraDurumu.coz(taban("sirada", 5)).haberEsigi(3), SiraDurumu.HABER_YOK, "önünde 5: haber yok");
        esit(SiraDurumu.coz(taban("sirada", 3)).haberEsigi(3), SiraDurumu.HABER_YAKLASTI, "önünde 3: yaklaştı");
        esit(SiraDurumu.coz(taban("sirada", 1)).haberEsigi(3), SiraDurumu.HABER_YAKLASTI, "önünde 1: yaklaştı");
        esit(SiraDurumu.coz(taban("sirada", 0)).haberEsigi(3), SiraDurumu.HABER_SIRA_SENDE, "önünde 0: sıra sende");
        esit(SiraDurumu.coz(taban("isleniyor", 0)).haberEsigi(3), SiraDurumu.HABER_SIRA_SENDE, "işleniyor: sıra sende");
        esit(SiraDurumu.coz(taban("bitti", 0)).haberEsigi(3), SiraDurumu.HABER_YOK, "bitti: haber yok");

        // Eksik alanlar güvenli varsayılan
        SiraDurumu bos = SiraDurumu.coz(new JSONObject().toString());
        esit(bos.durum, "sirada", "durum yoksa sirada");
        esit(bos.gosterilenTahmin(), "", "tahmin yoksa boş");
        check(bos.yagKg == null, "yag yoksa null");

        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " SiraDurumu");
        System.exit(bad == 0 ? 0 : 1);
    }
}
