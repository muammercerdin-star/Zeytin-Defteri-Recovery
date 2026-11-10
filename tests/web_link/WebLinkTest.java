package com.zeytindefteri.app;

/** Bilgisayar bağlantısının ekrandaki dili: satır, takılma, öneri, ad, QR tanıma. */
public final class WebLinkTest {
    static int n, bad;
    static void ok(boolean c, String m) { n++; if (!c) { bad++; System.out.println("FAIL " + m); } }

    public static void main(String[] a) {
        ok(!TezgahWebLink.stuck(true, false, 5000), "5 sn: hâlâ bağlanıyor");
        ok(TezgahWebLink.stuck(true, false, 20000), "20 sn: bağlanamadı");
        ok(!TezgahWebLink.stuck(true, true, 99000), "bağlıysa takılı değil");
        ok(!TezgahWebLink.stuck(false, false, 99000), "oturum yoksa takılı değil");

        ok(TezgahWebLink.row(0, false, false, false, "", 0).title.equals("Bilgisayara bağlan"), "ilk kez");
        ok(TezgahWebLink.row(0, false, true, false, "Bağlanıyor", 0).title.equals("Bağlanıyor…"), "okundu");
        TezgahWebLink.Row st = TezgahWebLink.row(0, false, true, true, "Aktarıcıya ulaşılamıyor · HTTP 403", 0);
        ok(st.warn && st.title.startsWith("Bağlanamadı") && st.sub.contains("operatör"), "takıldı: sebep görünür");
        ok(TezgahWebLink.row(0, true, true, false, "Bilgisayar bağlı", 0).title.equals("Bilgisayar bağlı ✓"), "bağlı");
        ok(TezgahWebLink.row(2, false, false, false, "", 2).title.equals("2 bilgisayar bağlı ✓"), "kayıtlılar bağlı");
        ok(TezgahWebLink.row(0, false, false, false, "", 1).title.startsWith("Kayıtlı bilgisayarlar"), "kayıtlı");
        ok(TezgahWebLink.row(0, false, true, true, "", 0).sub.equals("Bilgisayar sayfası cevap vermedi"), "boş tanı");

        ok(TezgahWebLink.advice("Aktarıcıya ulaşılamıyor").contains("Wi-Fi"), "ulaşılamıyor → Wi-Fi öner");
        ok(TezgahWebLink.advice("Bilgisayar ekranı kapandı/arka plana alındı").contains("öne getir"), "sayfa kapandı");
        ok(TezgahWebLink.advice("Bilgisayar bağlantısı bekleniyor").contains("QR görünüyor"), "cevap yok");
        ok(TezgahWebLink.advice("Cihaz kaydedilemedi.").contains("Durum: Cihaz kaydedilemedi."), "ham durum da yazılır");
        ok(!TezgahWebLink.advice("Bağlanıyor").contains("Durum:"), "anlamsız durum yazılmaz");

        ok(TezgahWebLink.computerName(0).equals("Bilgisayar"), "ilk ad");
        ok(TezgahWebLink.computerName(1).equals("Bilgisayar 2"), "ikinci ad");

        ok(TezgahWebLink.looksLikePairing("ZD1._VFSCnrjmQZHn50R7aQ-ke7_3AGpiEeFW1rS6kIlT6MRWQ0Og-OlHbCt3znoT80B"), "gerçek QR");
        ok(TezgahWebLink.looksLikePairing("{\"t\":\"ZD_WEB_PAIR\"}"), "eski QR");
        ok(!TezgahWebLink.looksLikePairing("ZDQR1|elek|..."), "Elek QR'ı değil");
        ok(!TezgahWebLink.looksLikePairing(null) && !TezgahWebLink.looksLikePairing("https://example.com"), "alakasız QR");
        ok(TezgahWebLink.connectedToast(false).endsWith("Yalnız görüntüleme") && TezgahWebLink.connectedToast(true).endsWith("Tam kullanım"), "bağlandı mesajı");
        System.out.println("WebLinkTest: " + (n - bad) + "/" + n);
        if (bad > 0) System.exit(1);
    }
}
