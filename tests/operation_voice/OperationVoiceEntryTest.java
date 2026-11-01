package com.zeytindefteri.app;

/** Sesle bahçe işi: tür, tarih, miktar, tutar; soru cümlesi kayıt sayılmaz. */
public class OperationVoiceEntryTest {
    static int n = 0, bad = 0;
    static void check(boolean ok, String m) { n++; if (!ok) { bad++; System.out.println("FAIL " + m); } }
    static OperationVoiceEntry p(String s) { return OperationVoiceEntry.parse(s); }
    static String type(String s) { OperationVoiceEntry e = p(s); return e == null ? null : OperationKinds.TYPES[e.type]; }

    public static void main(String[] a) {
        check("İlaçlama".equals(type("Bugün Girelli'yi ilaçladım")), "ilaçladım");
        check("Sulama".equals(type("Dün 2 saat su verdim")), "su verdim");
        check("Sulama".equals(type("Hacılar'ı suladık")), "suladık");
        check("Gübre".equals(type("18 kilo gübre attık")), "gübre attık");
        check("Budama".equals(type("Bugün 40 ağaç budadım")), "budadım");
        check("Sinek tuzağı".equals(type("20 tane tuzak astım")), "tuzak astım");
        check("Toprak İşleme".equals(type("Bahçeyi sürdüm")), "sürdüm");
        check("Toprak İşleme".equals(type("Bugün çapaladık")), "çapaladık");
        check("İşçilik".equals(type("3 işçi çalıştı 1500 lira")), "işçi çalıştı");
        check("Mazot".equals(type("Mazot aldım 500 lira")), "mazot aldım");
        check("Nakliye".equals(type("Bugün nakliye yaptım 800 lira")), "nakliye");
        // sorular kayıt değil
        check(p("Son ilaçlama ne zaman?") == null, "soru: son ilaçlama ne zaman");
        check(p("son sulama ne zaman") == null, "soru (işaretsiz)");
        check(p("Bu sezon masrafım kaç") == null, "masraf sorusu");
        check(p("Hangi bahçe daha çok verdi") == null, "bahçe sorusu");
        check(p("3 numara 120 kilo sattım") == null, "elek cümlesi işlem değil");
        check(p("ilaçlama") == null, "yalnız tür adı kayıt değil");
        // tarih
        check(p("Bugün ilaçladım").dayOffset == 0, "bugün");
        check(p("Dün suladım").dayOffset == -1, "dün");
        check(p("Evvelsi gün gübre attım").dayOffset == -2, "evvelsi gün");
        // miktar / tutar
        OperationVoiceEntry e = p("Dün 2 saat su verdim");
        check(e.quantity == 2 && e.unit.equals("saat"), "2 saat");
        e = p("18 kilo gübre attık 900 lira");
        check(e.quantity == 18 && e.unit.equals("kg") && e.cost == 900, "18 kg, 900 lira");
        e = p("Mazot aldım 25 litre 1000 lira");
        check(e.quantity == 25 && e.unit.equals("L") && e.cost == 1000, "25 L mazot, 1000 lira");
        e = p("500 ml ilaç attım");
        check(e.quantity == 500 && e.unit.equals("ml"), "500 ml");
        e = p("Bugün iki yüz ağaç budadık");
        check(e.quantity == 200 && e.unit.equals("ağaç"), "yazıyla iki yüz ağaç");
        check(p("Bugün Girelli'yi ilaçladım 500 ml 300 lira").summary().equals("ilaçlama, bugün, 500 ml, 300 lira"), "okuma");
        // "lira" denmeden tutar
        e = p("Dün Girelliye 3 saat su saldık elektrik 350 tuttu");
        check(e != null && e.type == 0 && e.quantity == 3 && e.cost == 350, "350 tuttu");
        e = p("Bugün gübre serptik 50 kilo 1500'e patladı");
        check(e != null && e.type == 1 && e.quantity == 50 && e.cost == 1500, "1500'e patladı");
        e = p("Dün mazot aldık mazot parası 500");
        check(e != null && e.cost == 500, "mazot parası 500");
        e = p("Bugün kükürt attık");
        check(e != null && e.type == 2, "kükürt → ilaçlama");
        e = p("Dün Girelli'de 3 işçi çalıştı, yevmiye kişi başı 1200.");
        check(e != null && e.quantity == 3 && e.cost == 3600, "kişi başı 1200 × 3 işçi: " + (e == null ? "null" : e.cost));
        e = p("Bugün Kuyu dibine 10 tuzak astık");
        check(e != null && e.quantity == 10 && e.unit.equals("adet"), "10 tuzak");
        e = p("evelsi gün kuyudibine gübre attık 20 kilo");
        check(e != null && e.dayOffset == -2, "evelsi (yazım hatası)");
        OperationVoiceEntry a2 = OperationVoiceEntry.parseAnswer("500 verdik");
        check(a2.cost == 500 && a2.quantity == 0, "500 verdik → tutar, miktar değil");
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " OperationVoiceEntry");
        if (bad > 0) System.exit(1);
    }
}
