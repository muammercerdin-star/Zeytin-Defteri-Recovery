package com.zeytindefteri.app;

/** Sesli elek konuşması: yazma, düzeltme, geri alma, onay olmadan kaydetmeme. */
public class ElekVoiceDialogTest {
    static int n = 0, bad = 0;
    static void check(boolean ok, String m) { n++; if (!ok) { bad++; System.out.println("FAIL " + m); } }
    static final double[] PRICES = {130, 100, 70, 40, 30, 20};
    public static void main(String[] a) {
        ElekVoiceDialog d = new ElekVoiceDialog(new double[6], PRICES, false);
        ElekVoiceDialog.Reply r = d.handle("3 numara 120 kilo");
        check(r.action == ElekVoiceDialog.Action.FILL && d.kg[3] == 120 && r.listen, "ilk kilo yazılır");
        check(r.speech.equals("Tamam, 3 numara 120 kilo. Diğerlerini bekliyorum."), "ilk cevap: " + r.speech);
        r = d.handle("2 numara 80, double 20");
        check(d.kg[2] == 80 && d.kg[0] == 20 && r.speech.equals("Aldım, double 20, 2 numara 80. Başka?"), "ikinci cevap: " + r.speech);
        r = d.handle("3 numara yanlış 100 olacak");
        check(r.speech.equals("Yazdım, 3 numara 100. Bitince bitti demen yeterli."), "üçüncü cevap değişir: " + r.speech);
        r = d.handle("geri al");
        r = d.handle("3 numara yanlış 100 olacak");
        check(d.kg[3] == 100, "düzeltme eskisinin yerine geçer (toplanmaz)");
        r = d.handle("geri al");
        check(d.kg[3] == 120 && r.action == ElekVoiceDialog.Action.FILL, "geri al");
        r = d.handle("oku");
        check(r.speech.contains("Double 20, 2 numara 80, 3 numara 120") && r.speech.contains("Toplam 220 kilo"), "oku: " + r.speech);
        check(r.speech.contains("13.000 lira"), "tutar fiyatlarla: " + r.speech);   // 20*130+80*70+120*40 = 2600+5600+4800
        r = d.handle("3 numara kaç");
        check(r.action == ElekVoiceDialog.Action.NONE && r.speech.startsWith("Burada sadece kilo"), "soru: " + r.speech);
        r = d.handle("bla bla");
        check(r.speech.contains("anlayamadım") || r.speech.startsWith("Anlayamadım"), "anlaşılmadı: " + r.speech);
        r = d.handle("Ahmet'e sattım, bitti");
        check(d.phase() == ElekVoiceDialog.Phase.CONFIRM && d.isSold() && d.buyer.equals("Ahmet"), "satış + alıcı + bitti → onay");
        check(r.speech.contains("Alıcı Ahmet") && r.speech.endsWith("Kaydedip fişi hazırlayayım mı?"), "onay sorusu: " + r.speech);
        check(r.action != ElekVoiceDialog.Action.SAVE, "bitti demek KAYDETMEZ");
        r = d.handle("şey");
        check(r.action == ElekVoiceDialog.Action.NONE && d.phase() == ElekVoiceDialog.Phase.CONFIRM, "onayda belirsiz cevap: tekrar sorar");
        r = d.handle("4 numara 10");
        check(d.kg[4] == 10 && d.phase() == ElekVoiceDialog.Phase.CONFIRM && r.speech.startsWith("Düzelttim."), "onayda kilo söylenirse düzeltip yeniden sorar");
        r = d.handle("Evet");
        check(r.action == ElekVoiceDialog.Action.SAVE && !r.listen && d.phase() == ElekVoiceDialog.Phase.DONE, "evet → kaydet");

        ElekVoiceDialog h = new ElekVoiceDialog(new double[6], PRICES, true);
        h.handle("1 numara 50"); r = h.handle("bitti");
        check(r.speech.endsWith("Kime sattın?"), "formda satış açıksa önce alıcı sorulur: " + r.speech);
        r = h.handle("geç");
        check(r.speech.endsWith("Kaydedip fişi hazırlayayım mı?"), "alıcısız da fiş sorulur");
        r = h.handle("hayır");
        check(h.phase() == ElekVoiceDialog.Phase.LISTENING && r.listen, "hayır → dinlemeye döner");
        r = h.handle("satmadım bitti");
        check(!h.isSold() && r.speech.endsWith("Kaydedeyim mi?"), "satmadım → fiş yok");
        r = h.handle("vazgeç");
        check(r.action == ElekVoiceDialog.Action.CANCEL && !r.listen, "vazgeç");

        ElekVoiceDialog e = new ElekVoiceDialog(new double[6], PRICES, false);
        r = e.handle("bitti");
        check(r.action == ElekVoiceDialog.Action.NONE && e.phase() == ElekVoiceDialog.Phase.LISTENING, "kilo yokken bitti: onay sorulmaz");
        r = e.handle("evet");
        check(r.action != ElekVoiceDialog.Action.SAVE, "onay sorulmadan evet kaydetmez");
        e.handle("3 numara 30"); e.handle("2 numara 20"); r = e.handle("3 numarayı sil");
        check(e.kg[3] == 0 && e.kg[2] == 20 && r.speech.startsWith("Sildim."), "sınıf silme: " + r.speech);
        r = e.handle("kaydet");
        check(e.phase() == ElekVoiceDialog.Phase.CONFIRM && r.action != ElekVoiceDialog.Action.SAVE, "kaydet de önce onay sorar");

        ElekVoiceDialog f = new ElekVoiceDialog(new double[]{0, 0, 0, 50, 0, 0}, PRICES, false);
        f.handle("2 numara 10"); r = f.handle("oku");
        check(r.speech.contains("2 numara 10, 3 numara 50"), "formdaki mevcut kilolarla başlar");
        check(new ElekVoiceDialog(null, null, false).handle("").listen, "boş ses: dinlemeye devam");
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " ElekVoiceDialog");
        if (bad > 0) System.exit(1);
    }
}
