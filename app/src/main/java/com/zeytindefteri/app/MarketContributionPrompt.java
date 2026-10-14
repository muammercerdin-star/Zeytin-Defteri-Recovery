package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Toast;

/**
 * "Piyasaya Anonim Katkı" izni için ortak onay penceresi (Ayarlar ve Zeytin Ligi kullanır).
 * Varsayılan kapalıdır; kapatınca bu cihazdan gönderilen katkılar sunucudan geri çekilir.
 */
final class MarketContributionPrompt {
    private MarketContributionPrompt() {}

    static MarketCloudRepository repository(Activity activity) {
        String uid = new SessionManager(activity).getUserUid();
        return new MarketCloudRepository(new DatabaseHelper(activity.getApplicationContext()), uid == null ? "" : uid);
    }

    static String stateLabel(Activity activity) {
        return repository(activity).isContributionOptedIn() ? "Açık" : "Kapalı";
    }

    static void show(Activity activity, Runnable onChanged) {
        MarketCloudRepository repo = repository(activity);
        boolean on = repo.isContributionOptedIn();
        String message = "Açarsan elek kayıtlarındaki elek sınıfı, kilogram, kilogram fiyatı, tarih ve bahçenin "
                + "il/ilçesi; adın ve hesap bilgin olmadan, geri döndürülemez bir üretici koduyla bölge "
                + "istatistiklerine katılır.\n\n"
                + "Bir bölgede en az " + MarketBackendConfig.PUBLIC_MARKET_MIN_PRODUCERS
                + " farklı üretici olmadan hiçbir rakam yayınlanmaz.\n\n"
                + "Kapatırsan bu cihazdan gönderilen katkıların sunucudan geri çekilir. "
                + "Kapalıyken de bölge istatistiklerini görmeye devam edersin.\n\n"
                + "Şu an: " + (on ? "Açık" : "Kapalı");
        new AlertDialog.Builder(activity)
                .setTitle("Piyasaya Anonim Katkı")
                .setMessage(message)
                .setPositiveButton(on ? "Kapat" : "Aç", (d, w) -> repo.setContributionOptIn(!on, error -> {
                    if (activity.isFinishing()) return;
                    String text;
                    if (error == null) text = on ? "Katkı kapatıldı; gönderilen katkılar geri çekildi." : "Anonim katkı açıldı.";
                    else text = on ? "Katkı kapatıldı. Geri çekme tamamlanamadı; bağlantı gelince yeniden denenecek."
                            : "Ayar kaydedilemedi.";
                    Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
                    if (onChanged != null) onChanged.run();
                }))
                .setNeutralButton("Gizlilik", (d, w) -> LegalDocumentActivity.open(activity, LegalDocuments.PRIVACY))
                .setNegativeButton("Vazgeç", null)
                .show();
    }
}
