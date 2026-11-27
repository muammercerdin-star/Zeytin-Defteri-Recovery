package com.zeytindefteri.app;

import android.content.Context;

/**
 * Bir sıra yoklama turunu Android'de yürütür: defteri (telefonda) + ağ (SiraBulut) + yerel bildirim
 * (SiraBildirimci). Çekirdek karar mantığı SiraYoklama'dadır ve host'ta test edilmiştir; burası yalnız
 * gerçek uçları bağlar. AlertCenter'ın arka plan turundan ve ekran öne gelince çağrılır.
 */
final class SiraTarama {

    private SiraTarama() {
    }

    /** Arka plan iş parçacığında çağrılır (ağ açık). Takip edilen sıra yoksa hiçbir şey yapmaz, ağ açmaz. */
    static void tara(Context context) {
        Context app = context.getApplicationContext();
        SiraDefteri defteri = new SiraDefteri(new SiraDepo(app));
        if (defteri.tokenlar().isEmpty()) {
            return;
        }
        SiraBildirimci bildirimci = new SiraBildirimci(app);
        SiraYoklama.tur(defteri, SiraBulut::durum, bildirimci);
    }
}
