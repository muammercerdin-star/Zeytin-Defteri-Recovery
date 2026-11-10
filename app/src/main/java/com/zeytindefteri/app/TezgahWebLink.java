package com.zeytindefteri.app;

/**
 * Bilgisayar bağlantısının ekrandaki dili (Android'e bağlı değil, test edilir): Tezgâh ana sayfasındaki satır,
 * "takıldı" kararı, bağlanamayınca ne yapılacağını söyleyen öneri, varsayılan bilgisayar adı.
 * Amaç WhatsApp Web sadeliği: okut → bağlanıyor → bağlandı ✓; olmazsa sessiz bekleme yok, sebep + çözüm.
 */
public final class TezgahWebLink {

    private TezgahWebLink() { }

    /** Okutulduktan bu kadar sonra hâlâ bağlanmadıysa "bağlanamadı" sayılır (normalde birkaç saniye). */
    public static final long STUCK_MS = 20000;

    public static final class Row {
        public final String title, sub;
        public final boolean warn;
        Row(String title, String sub, boolean warn) { this.title = title; this.sub = sub; this.warn = warn; }
    }

    public static boolean stuck(boolean running, boolean ready, long sinceMs) {
        return running && !ready && sinceMs >= STUCK_MS;
    }

    /** Tezgâh ana sayfasındaki bilgisayar satırı. */
    public static Row row(int readyCount, boolean ready, boolean running, boolean stuck, String diagnosis, int savedDevices) {
        if (readyCount > 0) return new Row(readyCount + " bilgisayar bağlı ✓", "Dokun: yetki, çıkış, yeni bilgisayar", false);
        if (ready) return new Row("Bilgisayar bağlı ✓", "Dokun: yetki, çıkış, yeni bilgisayar", false);
        if (running && stuck) return new Row("Bağlanamadı · dokun, yeniden okut", shortReason(diagnosis), true);
        if (running) return new Row("Bağlanıyor…", "QR okundu · birkaç saniye sürer", false);
        if (savedDevices > 0) return new Row("Kayıtlı bilgisayarlar ›", "Bilgisayarda sayfayı aç, kendisi bağlanır · yeni için dokun", false);
        return new Row("Bilgisayara bağlan", "Bilgisayarda zeytin-tezgah.pages.dev'i aç, QR'ı okut", false);
    }

    /** Satırın altına sığacak kısa sebep. */
    static String shortReason(String diagnosis) {
        String d = diagnosis == null ? "" : diagnosis.trim();
        if (d.isEmpty() || d.equals("Bağlanıyor") || d.startsWith("Bilgisayar bağlantısı bekleniyor")) return "Bilgisayar sayfası cevap vermedi";
        if (d.startsWith("Aktarıcıya ulaşılamıyor") || d.equals("Aktarıcıya bağlanılıyor")) return "İnternet / operatör bağlantıyı kesiyor";
        if (d.startsWith("Aktarıcıya bağlandı · bilgisayarın")) return "Bilgisayardaki sayfa telefonu görmedi";
        if (d.startsWith("Bilgisayar ekranı kapandı")) return "Bilgisayardaki sayfa kapandı ya da arka planda";
        return d.length() > 60 ? d.substring(0, 57) + "…" : d;
    }

    /** "Bağlanamadı" penceresindeki açıklama: ne oldu + ne yapmalı. */
    public static String advice(String diagnosis) {
        String d = diagnosis == null ? "" : diagnosis.trim();
        StringBuilder b = new StringBuilder();
        if (d.startsWith("Aktarıcıya ulaşılamıyor") || d.equals("Aktarıcıya bağlanılıyor")) {
            b.append("Telefon bağlantı sunucusuna ulaşamadı.\n\n")
             .append("• Telefonu Wi-Fi'ye bağlayıp tekrar dene.\n")
             .append("• Mobil veride operatör bu bağlantıyı kesiyor olabilir.");
        } else if (d.startsWith("Bilgisayar ekranı kapandı")) {
            b.append("Bilgisayardaki sayfa kapandı ya da arka plana alındı.\n\n")
             .append("• Sayfayı öne getir; 5 dakika içinde kendiliğinden devam eder.");
        } else {
            b.append("QR okundu ama bilgisayardaki sayfa cevap vermedi.\n\n")
             .append("• Bilgisayarda sayfa açık ve QR görünüyor olmalı.\n")
             .append("• Sayfadaki QR yenilendiyse yenisini okut.\n")
             .append("• Olmazsa telefonu Wi-Fi'ye bağlayıp tekrar dene.");
        }
        if (!d.isEmpty() && !d.equals("Bağlanıyor")) b.append("\n\nDurum: ").append(d);
        return b.toString();
    }

    /**
     * Bağlantı hatasının türü (içerik/anahtar içermez): DNS mi, zaman aşımı mı, sıfırlama mı, güvenli bağlantı mı.
     * Operatör engelini ayırt etmeye yarar: DNS ya da sıfırlama genelde engel, zaman aşımı zayıf çekim.
     */
    public static String failureKind(Throwable error) {
        for (Throwable e = error; e != null; e = e.getCause() == e ? null : e.getCause()) {
            String n = e.getClass().getSimpleName();
            String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase(java.util.Locale.ROOT);
            if (n.equals("UnknownHostException")) return "adres bulunamadı (DNS)";
            if (n.equals("SocketTimeoutException") || n.equals("InterruptedIOException") || m.contains("timeout")) return "zaman aşımı";
            if (n.startsWith("SSL") || m.contains("handshake") || m.contains("certificate")) return "güvenli bağlantı kurulamadı (TLS)";
            if (m.contains("reset")) return "bağlantı sıfırlandı";
            if (n.equals("ConnectException") || m.contains("refused")) return "bağlantı reddedildi";
            if (n.equals("EOFException") || m.contains("closed")) return "bağlantı yarıda kapandı";
        }
        return error == null ? "bilinmeyen hata" : error.getClass().getSimpleName();
    }

    /** Bilgisayara ad yazdırılmaz: "Bilgisayar", sonra "Bilgisayar 2", "Bilgisayar 3"… */
    public static String computerName(int savedDevices) {
        return savedDevices <= 0 ? "Bilgisayar" : "Bilgisayar " + (savedDevices + 1);
    }

    /** Okunan QR bilgisayar bağlantı QR'ı mı (kısa "ZD1." biçimi ya da eski uzun biçim)? */
    public static boolean looksLikePairing(String raw) {
        if (raw == null) return false;
        String t = raw.trim();
        return t.startsWith("ZD1.") || t.contains("ZD_WEB_PAIR");
    }

    public static String connectedToast(boolean full) {
        return "Bilgisayar bağlandı ✓ · " + (full ? "Tam kullanım" : "Yalnız görüntüleme");
    }
}
