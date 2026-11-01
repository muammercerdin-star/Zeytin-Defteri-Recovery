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
        check(r.action == OperationVoiceDialog.Action.OPEN_FORM, "hayır → form açılır, kaydetmez");

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
