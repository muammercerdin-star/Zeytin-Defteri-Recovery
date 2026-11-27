package com.zeytindefteri.app;

/**
 * Bir yoklama turunun çekirdeği: takip edilen her sıra token'ı için durumu çek, gerekiyorsa otomatik
 * haber ver ve gönderilen biti işaretle (aynı haber iki kez gitmez). Tamamen saf mantık; ağ ve bildirim
 * gösterimi arayüzlerle (Getirici, Haberci) dışarıdan verilir — böylece host'ta test edilir.
 *
 * GECE KURALI YOK: Sıram bildirimi operasyoneldir (fabrika gece de çalışır, sıra gece gelebilir). Bu,
 * uygulamanın genel "gece 22–05 bildirim yok" kuralına BİLİNÇLİ bir istisnadır (çiftçi onayladı).
 */
public final class SiraYoklama {

    /** Sıra durumunu getiren uç (gerçekte SiraBulut.durum; testte sahte). null = ulaşılamadı. */
    public interface Getirici {
        SiraDurumu durum(String token);
    }

    /** Bildirimi gösteren uç (gerçekte yerel bildirim; testte sahte). */
    public interface Haberci {
        void haber(String token, SiraBildirim.Karar karar);
    }

    /** Önünde kaç kişi kala "yaklaştı" denir (web ile aynı varsayılan). */
    public static final int ESIK = 3;

    private SiraYoklama() {
    }

    /** Bir tur: ulaşılamayan token'a dokunulmaz (geçici olabilir); karar gelirse haber verilip bit işaretlenir. */
    public static void tur(SiraDefteri defteri, Getirici getirici, Haberci haberci) {
        for (String token : defteri.tokenlar()) {
            SiraDurumu d = getirici.durum(token);
            if (d == null) {
                continue;
            }
            SiraBildirim.Karar k = SiraBildirim.karar(d, defteri.esik(token), ESIK);
            if (k.gonder) {
                haberci.haber(token, k);
                defteri.esikIsaretle(token, k.bit);
            }
        }
    }
}
