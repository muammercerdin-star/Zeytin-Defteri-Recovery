package com.zeytindefteri.app;

import java.util.Arrays;
import java.util.List;

/** Sohbetle kayıt: hasat, salamura, bahçe, Meydan gönderisi. */
public class VoiceRecordDialogTest {
    static int n = 0, bad = 0;
    static void check(boolean ok, String m) { n++; if (!ok) { bad++; System.out.println("FAIL " + m); } }
    static final List<String> G = Arrays.asList("Girelli Zeytinliği", "Hacılar Zeytinlik");
    public static void main(String[] a) {
        // tanıma
        check(VoiceRecordDialog.detect("Hasat gireceğim") == VoiceRecordDialog.Kind.HARVEST, "hasat gireceğim");
        check(VoiceRecordDialog.detect("Girelli'den 300 kilo topladık") == VoiceRecordDialog.Kind.HARVEST, "topladık");
        check(VoiceRecordDialog.detect("50 kilo salamura bastım") == VoiceRecordDialog.Kind.BRINE, "salamura bastım");
        check(VoiceRecordDialog.detect("Yeni bahçe ekle") == VoiceRecordDialog.Kind.GARDEN, "bahçe ekle");
        check(VoiceRecordDialog.detect("Meydan'a yaz: yağmur başladı") == VoiceRecordDialog.Kind.POST, "meydana yaz");
        check(VoiceRecordDialog.detect("Salamura stoğum") == null, "stok sorusu başlatmaz");
        check(VoiceRecordDialog.detect("Bu sezon kaç kilo topladım?") == null, "hasat sorusu başlatmaz");
        check(VoiceRecordDialog.detect("Hangi bahçe daha çok verdi") == null, "bahçe sorusu başlatmaz");

        // hasat: ilk cümlede söylenen sorulmaz
        VoiceRecordDialog h = new VoiceRecordDialog(VoiceRecordDialog.Kind.HARVEST, G);
        h.prefill("Girelli'den 300 kilo topladık", 0);
        VoiceRecordDialog.Reply r = h.start();
        check(r.speech.equals("Kolay gelsin! İşçilik masrafı oldu mu? Kaç lira?"), "hasat ilk soru: " + r.speech);
        r = h.handle("yok");
        check(r.speech.equals("Nakliye tuttu mu? Kaç lira?"), "işçilik yok → nakliye: " + r.speech);
        r = h.handle("500 lira");
        check(r.speech.equals("Girelli Zeytinliği, bugün 300 kilo hasat, nakliye 500 lira. Formu açayım mı, kontrol edip kaydedersin."), "hasat okuma: " + r.speech);
        r = h.handle("evet");
        check(r.action == VoiceRecordDialog.Action.OPEN_FORM && h.done(), "evet → form");
        check(h.number("kg") == 300 && h.number("transport") == 500 && h.gardenIndex() == 0, "hasat değerleri");

        VoiceRecordDialog h2 = new VoiceRecordDialog(VoiceRecordDialog.Kind.HARVEST, G);
        h2.prefill("Hasat gireceğim", -1);
        r = h2.start();
        check(r.speech.equals("Kolay gelsin! Hangi bahçeden topladın?"), "bahçe sorusu: " + r.speech);
        r = h2.handle("Hacılar'dan");
        check(h2.gardenIndex() == 1 && r.speech.equals("Kaç kilo topladın?"), "Hacılar'dan: " + r.speech);
        r = h2.handle("3 numara 120, 2 numara 80");
        check(h2.number("kg") > 0 && h2.grades() != null && h2.grades()[3] == 120, "boy boy kilo: " + h2.number("kg"));

        // salamura
        VoiceRecordDialog b = new VoiceRecordDialog(VoiceRecordDialog.Kind.BRINE, G);
        b.prefill("50 kilo salamura bastım", -1);
        r = b.start();
        check(r.speech.equals("Kolay gelsin! Hangi zeytin? Gemlik mi, Domat mı?"), "salamura cinsi: " + r.speech);
        r = b.handle("Gemlik");
        r = b.handle("5 kilo");
        r = b.handle("bilmiyorum");
        check(r.speech.equals("50 kilo Gemlik salamura, 5 kilo tuz. Formu açayım mı, kontrol edip kaydedersin."), "salamura okuma: " + r.speech);
        check(b.number("olive") == 50 && b.number("salt") == 5 && b.text("variety").equals("Gemlik"), "salamura değerleri");

        // bahçe
        VoiceRecordDialog g = new VoiceRecordDialog(VoiceRecordDialog.Kind.GARDEN, G);
        g.prefill("Yeni bahçe ekle", -1);
        r = g.start();
        check(r.speech.equals("Hayırlı olsun! Bahçenin adı ne olsun?"), "bahçe adı: " + r.speech);
        r = g.handle("dere bahçe");
        check(g.text("name").equals("Dere Bahçe") && r.speech.equals("Kaç ağaç var?"), "ad: " + g.text("name"));
        r = g.handle("120 ağaç");
        r = g.handle("geç");
        r = g.handle("8 dönüm");
        check(r.speech.equals("Yeni bahçe: Dere Bahçe, 120 ağaç, 8 dönüm. Formu açayım mı, kontrol edip kaydedersin."), "bahçe okuma: " + r.speech);

        // gönderi: metin ilk cümlede
        VoiceRecordDialog p = new VoiceRecordDialog(VoiceRecordDialog.Kind.POST, G);
        p.prefill("Meydan'a yaz: bizim köyde yağmur başladı", -1);
        r = p.start();
        check(r.speech.equals("Tamam. Şunu paylaşacağım: Bizim köyde yağmur başladı. Paylaşım ekranını açayım mı?"), "gönderi: " + r.speech);
        r = p.handle("hayır");
        check(r.action == VoiceRecordDialog.Action.OPEN_FORM, "hayır → yine ekran (düzeltmek için), kendiliğinden paylaşmaz");
        VoiceRecordDialog p2 = new VoiceRecordDialog(VoiceRecordDialog.Kind.POST, G);
        p2.prefill("Meydan'a paylaş", -1);
        r = p2.start();
        check(r.speech.equals("Tamam. Ne yazayım? Söyle, ben yazayım."), "gönderi metni sorulur: " + r.speech);
        r = p2.handle("vazgeç");
        check(r.action == VoiceRecordDialog.Action.CANCEL, "vazgeç");
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " VoiceRecordDialog");
        if (bad > 0) System.exit(1);
    }
}
