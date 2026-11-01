package com.zeytindefteri.app;

import java.util.Arrays;
import java.util.List;

/** Bahçe işi sohbeti: eksikleri insan gibi sorar, söyleneni tekrar sormaz, "evet" olmadan kaydetmez. */
public class OperationVoiceDialogTest {
    static int n = 0, bad = 0;
    static void check(boolean ok, String m) { n++; if (!ok) { bad++; System.out.println("FAIL " + m); } }
    static final List<String> G = Arrays.asList("Girelli Zeytinliği", "Hacılar Zeytinlik", "Dere Bahçe");

    public static void main(String[] a) {
        OperationVoiceDialog d = new OperationVoiceDialog(OperationVoiceEntry.parse("Bugün ilaçladım"), G, -1);
        OperationVoiceDialog.Reply r = d.start();
        check(r.speech.equals("Kolay gelsin! Hangi bahçeyi ilaçladın?"), "ilk soru: " + r.speech);
        r = d.handle("Girelli'yi");
        check(d.garden == 0 && r.speech.equals("Ne attın? İlacın adını söyle."), "bahçe → ne attın: " + r.speech);
        r = d.handle("Bakır attım");
        check(d.what.equals("Bakır") && r.speech.equals("Ne kadar kullandın?"), "ilaç adı: " + d.what + " / " + r.speech);
        r = d.handle("500 gram");
        check(d.entry.quantity == 500 && d.entry.unit.equals("g") && r.speech.equals("Masrafı ne kadar tuttu?"), "miktar: " + r.speech);
        r = d.handle("300 lira");
        check(d.entry.cost == 300 && r.speech.equals("Girelli Zeytinliği, bugün ilaçlama, Bakır, 500 gram, 300 lira. Kaydedeyim mi?"), "okuma: " + r.speech);
        check(r.action == OperationVoiceDialog.Action.ASK, "okuma kaydetmez");
        r = d.handle("şey");
        check(r.action == OperationVoiceDialog.Action.ASK && d.step() == OperationVoiceDialog.Step.CONFIRM, "belirsiz cevap kaydetmez");
        r = d.handle("evet");
        check(r.action == OperationVoiceDialog.Action.SAVE && r.speech.equals("Kaydettim. Eline sağlık."), "evet → kaydet");
        check(d.unitForSave().equals("g") && d.quantityForSave() == 500 && d.noteForSave().equals("Bakır (sesle)"), "kayıt alanları");

        // Söylenmiş olanı tekrar sormaz
        OperationVoiceDialog s2 = new OperationVoiceDialog(OperationVoiceEntry.parse("Dün 2 saat su verdim 150 lira"), G, 1);
        r = s2.start();
        check(r.speech.equals("Kolay gelsin! Hacılar Zeytinlik, dün sulama, 2 saat, 150 lira. Kaydedeyim mi?"), "hepsi söylenmiş → doğrudan okuma: " + r.speech);

        // Bilmiyorum / geç atlar; birimsiz sayı türün birimi
        OperationVoiceDialog s3 = new OperationVoiceDialog(OperationVoiceEntry.parse("Gübre attık"), G, 2);
        r = s3.start();
        check(r.speech.equals("Kolay gelsin! Hangi gübreyi attın?"), "gübre adı sorusu: " + r.speech);
        r = s3.handle("bilmiyorum");
        check(r.speech.equals("Ne kadar attın?"), "bilmiyorum → atlar: " + r.speech);
        r = s3.handle("18");
        check(s3.entry.quantity == 18 && s3.entry.unit.equals("kg"), "birimsiz 18 → kg");
        r = s3.handle("masraf yok");
        check(r.speech.equals("Dere Bahçe, bugün gübre, 18 kilo. Kaydedeyim mi?"), "masraf yok: " + r.speech);
        r = s3.handle("hayır");
        check(r.action == OperationVoiceDialog.Action.ASK && r.speech.startsWith("Neyi düzelteyim?"), "hayır → önce neyin düzeleceği sorulur, kaydetmez: " + r.speech);
        r = s3.handle("hayır");
        check(r.action == OperationVoiceDialog.Action.OPEN_FORM, "ikinci hayır → form açılır, kaydetmez");

        // Sesle düzeltme (kullanıcının örneği): yalnız söylenen alan değişir
        java.util.List<String> GG = Arrays.asList("Girelli", "Kuyu dibi");
        OperationVoiceDialog f = new OperationVoiceDialog(OperationVoiceEntry.parse("Dün Girelli'ye üç saat su verdik, elektrik de 350 lira."), GG, 0);
        r = f.start();
        check(r.speech.equals("Kolay gelsin! Girelli, dün sulama, 3 saat, 350 lira. Kaydedeyim mi?"), "örnek cümle: " + r.speech);
        r = f.handle("350 değil, 250 olacak");
        check(r.speech.equals("Düzelttim. Girelli, dün sulama, 3 saat, 250 lira. Kaydedeyim mi?") && f.entry.quantity == 3, "yalnız tutar: " + r.speech);
        r = f.handle("dün değil bugün");
        check(f.entry.dayOffset == 0 && f.entry.cost == 250, "dün değil bugün");
        r = f.handle("Girelli değil Kuyu dibi");
        check(f.garden == 1, "bahçe düzeltme: " + r.speech);
        r = f.handle("3 saat değil 4");
        check(f.entry.quantity == 4 && f.entry.unit.equals("saat") && f.entry.cost == 250, "miktar düzeltme");
        r = f.handle("evet");
        check(r.action == OperationVoiceDialog.Action.SAVE, "düzeltmeden sonra evet → kaydeder");
        // "250 olacak": hangisi belli değil → sorar
        OperationVoiceDialog f2 = new OperationVoiceDialog(OperationVoiceEntry.parse("Dün Girelliyi ilaçladık 2 litre 2000 tuttu"), GG, 0);
        f2.start(); f2.handle("Bakır");
        r = f2.handle("500 olacak");
        check(r.speech.equals("500 neyin yerine? Tutar mı, miktar mı?") && f2.entry.cost == 2000, "belirsiz sayı sorulur: " + r.speech);
        r = f2.handle("tutar");
        check(f2.entry.cost == 500 && f2.entry.quantity == 2, "tutar seçildi");
        // tür düzeltme: eski miktar silinir, yeniden sorulur
        OperationVoiceDialog f3 = new OperationVoiceDialog(OperationVoiceEntry.parse("Dün Girelli'de sulama yaptık 2 saat elektrik 200"), GG, 0);
        f3.start();
        r = f3.handle("sulama değil gübre");
        check(r.speech.equals("Tamam, gübre olarak düzelttim. Hangi gübreyi attın?") && f3.entry.quantity == 0, "tür düzeltme: " + r.speech);
        // masraf sorulurken önceki miktar/gün düzeltilir, aynı soruya dönülür
        OperationVoiceDialog f5 = new OperationVoiceDialog(OperationVoiceEntry.parse("Dün Kuyu dibinde damlamayı açtık 4 saat"), GG, 1);
        r = f5.start();
        check(r.speech.equals("Kolay gelsin! Masrafı ne kadar tuttu?"), "masraf sorusu: " + r.speech);
        r = f5.handle("4 saat değil 5 saat olacak");
        check(f5.entry.quantity == 5 && f5.entry.cost <= 0 && r.speech.equals("Düzelttim: Kuyu dibi, dün sulama, 5 saat. Masrafı ne kadar tuttu?"), "soru sırasında miktar düzeltme: " + r.speech);
        r = f5.handle("dün değil evvelsi gün");
        check(f5.entry.dayOffset == -2 && f5.step() == OperationVoiceDialog.Step.COST, "soru sırasında gün düzeltme");
        r = f5.handle("300");
        check(f5.entry.cost == 300 && f5.step() == OperationVoiceDialog.Step.CONFIRM, "sonra tutar cevabı");
        // tutar sorusuna "350 değil 250" → 250
        OperationVoiceDialog f4 = new OperationVoiceDialog(OperationVoiceEntry.parse("Bugün Girelli'yi suladım 2 saat"), GG, 0);
        f4.start();
        f4.handle("350 değil 250");
        check(f4.entry.cost == 250, "tutar sorusunda düzeltme: " + f4.entry.cost);

        // Tek bahçe: sormaz; anlaşılmayan bahçe yeniden sorulur
        OperationVoiceDialog s4 = new OperationVoiceDialog(OperationVoiceEntry.parse("Bugün 40 ağaç budadım"), Arrays.asList("Girelli"), -1);
        r = s4.start();
        check(r.speech.equals("Kolay gelsin! Masrafı ne kadar tuttu?"), "tek bahçe + ağaç söylenmiş: " + r.speech);
        OperationVoiceDialog s5 = new OperationVoiceDialog(OperationVoiceEntry.parse("Bugün suladım"), G, -1);
        s5.start();
        r = s5.handle("Ayvalık");
        check(s5.garden < 0 && r.speech.startsWith("Bahçeyi anlayamadım. Girelli Zeytinliği, Hacılar Zeytinlik, Dere Bahçe. Hangisi?"), "bilinmeyen bahçe: " + r.speech);
        r = s5.handle("ikinci");
        check(s5.garden == 1, "ikinci → Hacılar");
        r = s5.handle("üç saat");
        check(s5.entry.quantity == 3 && s5.entry.unit.equals("saat"), "üç saat");
        r = s5.handle("vazgeç");
        check(r.action == OperationVoiceDialog.Action.CANCEL, "vazgeç");

        // İlaçlama: ad + miktar tek cevapta
        OperationVoiceDialog s6 = new OperationVoiceDialog(OperationVoiceEntry.parse("Bugün Girelli'yi ilaçladım"), G, 0);
        s6.start();
        r = s6.handle("Deltametrin attım 250 ml");
        check(s6.what.equals("Deltametrin") && s6.entry.quantity == 250 && s6.entry.unit.equals("ml") && r.speech.equals("Masrafı ne kadar tuttu?"), "ad + miktar birlikte: " + s6.what + " / " + r.speech);
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " OperationVoiceDialog");
        if (bad > 0) System.exit(1);
    }
}
