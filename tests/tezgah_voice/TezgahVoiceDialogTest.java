package com.zeytindefteri.app;

import java.util.Arrays;

/** Tezgâhta sesli tartım: kilo → kasa, dara, net; numara geçişi; satan; ödeme; "evet" olmadan kaydetmeme. */
public class TezgahVoiceDialogTest {
    static int n = 0, bad = 0;
    static void check(boolean ok, String m) { n++; if (!ok) { bad++; System.out.println("FAIL " + m); } else System.out.println("PASS " + m); }

    static TezgahModel model() {
        TezgahModel m = new TezgahModel();
        double[] p = {130, 100, 70, 40, 30, 20};
        for (int g = 0; g < 6; g++) m.setPrice(g, p[g]);
        return m;
    }

    public static void main(String[] a) {
        // Kullanıcının örnekleri: 15 → 1 kasa dara 2 net 13; 55 → 2 kasa dara 4 net 51.
        check(TezgahVoiceDialog.estimateKasa(15, 2) == 1, "15 kg → 1 kasa");
        check(TezgahVoiceDialog.estimateKasa(55, 2) == 2, "55 kg → 2 kasa");
        check(TezgahVoiceDialog.estimateKasa(40, 2) == 2, "40 kg tek kasaya sığmaz → 2 kasa");
        check(TezgahVoiceDialog.estimateKasa(80, 2) == 3, "80 kg → 3 kasa");

        TezgahVoiceDialog d = new TezgahVoiceDialog(model(), "", Arrays.asList("Mehmet Yılmaz", "Ahmet Kaya", "Ahmet Demir"));
        check(d.greeting().equals("Double kantarda. Kaç kilo?"), "karşılama: " + d.greeting());
        TezgahVoiceDialog.Reply r = d.handle("15");
        check(r.action == TezgahVoiceDialog.Action.ADD && r.grade == 0 && r.kasa == 1 && r.kg == 15, "15 → Double 1 kasa");
        check(r.speech.equals("15 kilo, 1 kasa, dara 2, net 13. Başka?"), "15 cevabı: " + r.speech);
        r = d.handle("yok");
        check(d.grade() == 1 && r.speech.equals("1 numara, kaç kilo?"), "yok → 1 numara: " + r.speech);
        r = d.handle("55 kilo");
        check(r.grade == 1 && r.kasa == 2 && r.speech.equals("55 kilo, 2 kasa, dara 4, net 51. Başka?"), "55 cevabı: " + r.speech);
        r = d.handle("elli beş iki kasa");
        check(r.kasa == 2 && r.kg == 55, "yazıyla 'elli beş iki kasa' = 55 kg 2 kasa (57 değil)");
        r = d.handle("3 kasa");
        check(r.action == TezgahVoiceDialog.Action.ADD && r.removeIndex == 2 && r.kasa == 3 && r.kg == 55
                && r.speech.startsWith("Düzelttim. 55 kilo, 3 kasa, dara 6, net 49"), "yalnız '3 kasa' son tartımı düzeltir: " + r.speech);
        r = d.handle("geri al");
        check(r.action == TezgahVoiceDialog.Action.UNDO && r.removeIndex == 2 && d.model().items().size() == 2, "geri al son tartımı siler");
        r = d.handle("1 kasa 40");
        check(d.phase() == TezgahVoiceDialog.Phase.KASA_CHECK && r.speech.contains("2 kasa yapayım mı"), "1 kasada 40 kg uyarısı: " + r.speech);
        r = d.handle("evet");
        check(r.action == TezgahVoiceDialog.Action.ADD && r.kasa == 2 && r.kg == 40, "evet → önerilen kasa");
        r = d.handle("3 numara 80");
        check(r.grade == 3 && r.kasa == 3 && r.speech.startsWith("3 numara: 80 kilo, 3 kasa, dara 6, net 74"), "numara atlama: " + r.speech);
        r = d.handle("kantarda 2 kilo");
        check(r.action == TezgahVoiceDialog.Action.NONE && d.model().items().size() == 4, "dara kadar kilo eklenmez: " + r.speech);
        r = d.handle("oku");
        check(r.speech.startsWith("Double 13 kilo, 1 numara 87 kilo, 3 numara 74 kilo. Toplam net 174 kilo, 8 kasa"), "oku: " + r.speech);
        r = d.handle("bitti");
        check(d.phase() == TezgahVoiceDialog.Phase.SELLER && r.speech.endsWith("Kimden aldın?"), "bitti → kimden: " + r.speech);
        // 13*130 + 87*100 + 74*40 = 1690 + 8700 + 2960 = 13350
        check(r.speech.contains("13350 lira"), "toplam tutar: " + r.speech);
        check(r.action != TezgahVoiceDialog.Action.SAVE, "bitti KAYDETMEZ");
        r = d.handle("Ahmet'ten aldım");
        check(d.phase() == TezgahVoiceDialog.Phase.SELLER_PICK && r.speech.startsWith("Hangisi?"), "iki Ahmet → hangisi: " + r.speech);
        r = d.handle("Kaya");
        check(r.action == TezgahVoiceDialog.Action.SELLER && d.seller.equals("Ahmet Kaya") && d.phase() == TezgahVoiceDialog.Phase.PAY, "Ahmet Kaya seçildi: " + r.speech);
        check(r.speech.contains("Tamamı mı, bir kısmı mı, sonra mı?"), "ödeme sorusu");
        r = d.handle("bir kısmını");
        check(d.phase() == TezgahVoiceDialog.Phase.PAY_AMOUNT && r.speech.equals("Ne kadar ödedin?"), "bir kısmı → tutar sorar");
        r = d.handle("beş bin");
        check(d.phase() == TezgahVoiceDialog.Phase.CONFIRM && d.paid() == 5000 && r.speech.contains("5000 lira ödendi, 8350 lira borç kalıyor")
                && r.speech.endsWith("Kaydedip fişi hazırlayayım mı?"), "kısmi ödeme okuması: " + r.speech);
        r = d.handle("şey");
        check(r.action == TezgahVoiceDialog.Action.NONE && d.phase() == TezgahVoiceDialog.Phase.CONFIRM, "belirsiz cevap kaydetmez");
        r = d.handle("evet");
        check(r.action == TezgahVoiceDialog.Action.SAVE && !r.listen && d.phase() == TezgahVoiceDialog.Phase.DONE, "evet → kaydet");

        // Yeni üretici, tamamı ödendi
        TezgahVoiceDialog e = new TezgahVoiceDialog(model(), "", Arrays.asList("Mehmet Yılmaz"));
        e.handle("Double 27"); e.handle("bitti");
        r = e.handle("Hasan Çelik");
        check(e.sellerNew && e.seller.equals("Hasan Çelik") && r.speech.startsWith("Hasan Çelik listende yok"), "yeni üretici: " + r.speech);
        r = e.handle("tamamını ödedim");
        check(e.paid() == 25 * 130 && r.speech.contains("Tamamı ödendi"), "tamamı: " + r.speech);
        r = e.handle("hayır");
        check(e.phase() == TezgahVoiceDialog.Phase.WEIGH && r.action == TezgahVoiceDialog.Action.NONE, "hayır → kaydetmez, tartıma döner");

        // Listedeki üretici eksiz/ekli; ödeme sonra
        TezgahVoiceDialog f = new TezgahVoiceDialog(model(), "", Arrays.asList("Mehmet Yılmaz"));
        f.handle("2 numara 100"); f.handle("bitti");
        r = f.handle("Mehmetten aldım");
        check(f.seller.equals("Mehmet Yılmaz") && !f.sellerNew, "Mehmetten → Mehmet Yılmaz: " + f.seller);
        r = f.handle("sonra ödeyeceğim");
        check(f.paid() == 0 && r.speech.contains("Ödeme sonra"), "sonra: " + r.speech);

        // Münferit + borç uyarısı; kısmi tutar doğrudan söylenir
        TezgahVoiceDialog g = new TezgahVoiceDialog(model(), "", Arrays.asList());
        g.handle("Yağlık 54"); g.handle("bitti"); g.handle("münferit");
        r = g.handle("500 lira verdim");
        check(g.paid() == 500 && r.speech.contains("Ad yok"), "münferit borç uyarısı: " + r.speech);

        // Ekranda satan zaten yazılıysa sormaz; Yağlık'tan sonra 'yok' bitirir; vazgeç
        TezgahVoiceDialog h = new TezgahVoiceDialog(model(), "Ali Veli", Arrays.asList());
        h.handle("yağlık 30");
        r = h.handle("yok");
        check(h.phase() == TezgahVoiceDialog.Phase.PAY && r.speech.contains("Satan Ali Veli"), "satan biliniyorsa doğrudan ödeme: " + r.speech);
        r = h.handle("vazgeç");
        check(r.action == TezgahVoiceDialog.Action.CANCEL && !r.listen, "vazgeç kapatır");

        // Fiyatsız: ödeme sorulmaz
        TezgahVoiceDialog z = new TezgahVoiceDialog(new TezgahModel(), "", Arrays.asList());
        z.handle("27"); z.handle("bitti");
        r = z.handle("münferit");
        check(z.phase() == TezgahVoiceDialog.Phase.CONFIRM, "fiyat yoksa ödeme sorulmaz: " + r.speech);

        // Ayrıştırma
        TezgahVoiceDialog.Said s = TezgahVoiceDialog.parse("1 numara 40, 2 kasa");
        check(s.grade == 1 && s.kg == 40 && s.kasa == 2, "1 numara 40, 2 kasa");
        s = TezgahVoiceDialog.parse("2 kasa 55 kg");
        check(s.grade == -1 && s.kg == 55 && s.kasa == 2, "2 kasa 55 kg");
        s = TezgahVoiceDialog.parse("yüz yirmi kilo dört tane kasa");
        check(s.kg == 120 && s.kasa == 4, "yüz yirmi kilo dört tane kasa");
        s = TezgahVoiceDialog.parse("kasasız 30");
        check(s.kg == 30 && s.kasa == 0, "kasasız");

        System.out.println("TezgahVoiceDialogTest: " + (n - bad) + "/" + n);
        if (bad > 0) System.exit(1);
    }
}
