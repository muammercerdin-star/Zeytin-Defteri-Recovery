package com.zeytindefteri.app;

/**
 * Otomatik bildirim kararı: bir sıranın şu anki durumu + o token için daha önce gönderilmiş haber biti
 * verilince, şimdi haber verilmeli mi, hangi mesajla? Saf mantık (Android yok), host'ta test edilir.
 *
 * Web tarafındaki tetikleme ve mesajlarla BİREBİR aynı: her eşik bir kez (yaklaştı=bit 1, sıra sende=bit 2),
 * metinler aynı. Böylece çiftçi web'den de app'ten de aynı bildirimi görür. Gece kuralı burada yoktur
 * (fabrika gece çalışır) — gece kararı çağıran katmanda değil, hiç uygulanmaz.
 */
public final class SiraBildirim {

    public static final class Karar {
        public final boolean gonder;
        public final int bit;
        public final String baslik;
        public final String metin;

        Karar(boolean gonder, int bit, String baslik, String metin) {
            this.gonder = gonder;
            this.bit = bit;
            this.baslik = baslik;
            this.metin = metin;
        }
    }

    private static final Karar YOK = new Karar(false, 0, "", "");

    private SiraBildirim() {
    }

    public static Karar karar(SiraDurumu d, int gonderilmisBit, int esik) {
        int hedef = d.haberEsigi(esik);
        if (hedef == SiraDurumu.HABER_YOK) {
            return YOK;
        }
        int bit = hedef == SiraDurumu.HABER_SIRA_SENDE ? 2 : 1;
        if ((gonderilmisBit & bit) != 0) {
            return YOK;
        }
        if (hedef == SiraDurumu.HABER_SIRA_SENDE) {
            return new Karar(true, bit, "Sıra sende", "Sıra sana geldi. (kod " + d.kod + ")");
        }
        return new Karar(true, bit, "Sıran yaklaştı", "Önünde " + d.onunde + " kişi kaldı, yola çıkabilirsin. (kod " + d.kod + ")");
    }
}
