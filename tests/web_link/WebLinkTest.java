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

        ok(TezgahWebLink.row(0, false, true, true, "Aktarıcıya bağlanılıyor", 0).sub.contains("operatör"), "bağlanılamadıysa operatör");
        ok(TezgahWebLink.advice("Aktarıcıya bağlanılıyor").contains("Wi-Fi"), "bağlanılamadıysa Wi-Fi öner");
        ok(TezgahWebLink.row(0, false, true, true, "Aktarıcıya bağlandı · bilgisayarın cevabı bekleniyor", 0).sub.contains("sayfa"), "aktarıcı tamam, sayfa görmedi");
        ok(TezgahWebLink.failureKind(new java.net.UnknownHostException("tezgah-relay")).contains("DNS"), "DNS");
        ok(TezgahWebLink.failureKind(new java.net.SocketTimeoutException("connect timed out")).equals("zaman aşımı"), "zaman aşımı");
        ok(TezgahWebLink.failureKind(new java.io.IOException("x", new java.net.SocketException("Connection reset"))).equals("bağlantı sıfırlandı"), "sıfırlama (iç hata)");
        ok(TezgahWebLink.failureKind(new javax.net.ssl.SSLHandshakeException("Handshake failed")).contains("TLS"), "TLS");
        ok(TezgahWebLink.failureKind(new java.net.ConnectException("Connection refused")).equals("bağlantı reddedildi"), "red");
        ok(TezgahWebLink.failureKind(null).equals("bilinmeyen hata"), "boş");
        ok(TezgahWebLink.advice("Aktarıcıya ulaşılamıyor · adres bulunamadı (DNS)").contains("Durum: Aktarıcıya ulaşılamıyor · adres bulunamadı (DNS)"), "tür pencerede yazar");
        ok(TezgahWebLink.row(0, false, true, true, "Aktarıcıya bağlanılıyor · 3. deneme", 0).sub.contains("operatör"), "denemeli bağlanılıyor");
        ok(TezgahWebLink.row(0, false, true, true, "Yedek yol kurulmamış · Supabase tezgah-relay · HTTP 404", 0).sub.contains("Supabase"), "köprü yok");
        ok(TezgahWebLink.advice("Yedek yol kurulmamış · HTTP 404").contains("Verify JWT"), "köprü kurulumu öner");
        ok(TezgahWebLink.row(0, false, true, true, "Aktarıcıya yedek yoldan bağlanılıyor · 2. deneme", 0).sub.equals("Yedek yol da açılmadı"), "köprü de açılmadı");
        ok(TezgahWebLink.row(0, false, true, true, "Aktarıcıya bağlandı (yedek yol) · bilgisayarın cevabı bekleniyor", 0).sub.contains("sayfa"), "köprü açık, sayfa görmedi");
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
