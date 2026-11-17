package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Toast;

/**
 * "Zeytin Ligi'nde yer al" izni (Ayarlar ve Zeytin Ligi kullanır). Varsayılan KAPALI, açık rızaya dayanır.
 * "Piyasaya Anonim Katkı" yalnız toplu bölge istatistikleri içindir; bölge sıralamasında üretici başına
 * satır (sıra, puan, en çok çıkan numara, ortalama TL/kg) görünmesi bu ayrı izne bağlıdır.
 * Kapatınca sıralamadan çıkılır ve ligdeki ad da sunucudan silinir.
 */
final class LeagueJoinPrompt {
    private LeagueJoinPrompt() {}

    static String stateLabel(Activity activity) {
        return MarketContributionPrompt.repository(activity).leagueJoinedLocally() ? "Açık" : "Kapalı";
    }

    static String message(boolean contributionOn) {
        StringBuilder text = new StringBuilder();
        text.append("Açarsan bölgendeki Zeytin Ligi sıralamasında yer alırsın. ");
        text.append("Diğer Zeytin Defteri kullanıcıları sıranı, puanını, en çok çıkan elek numaranı ");
        text.append("ve ortalama kilogram fiyatını (TL/kg) görebilir.\n\n");
        text.append("Adın görünmez; \"Anonim üretici\" yazar. Adınla görünmek ayrı bir tercihtir. ");
        text.append("Kilon ve toplam tutarın GÖSTERİLMEZ.\n\n");
        text.append("Bu izin isteğe bağlıdır; vermesen de uygulamanın diğer bütün özelliklerini kullanırsın. ");
        text.append("İstediğin zaman kapatabilirsin; kapatınca sıralamadan çıkarsın ve ligdeki adın da silinir.");
        if (!contributionOn) {
            text.append("\n\nNot: Sıralama, Piyasaya Anonim Katkı ile gönderilen elek kayıtlarından hesaplanır; ");
            text.append("katkın kapalı olduğu için sıralamada görünmek için onu da açmalısın.");
        }
        return text.toString();
    }

    static void show(Activity activity, Runnable onChanged) {
        MarketCloudRepository repo = MarketContributionPrompt.repository(activity);
        boolean on = repo.leagueJoinedLocally();
        String text = message(repo.isContributionOptedIn()) + "\n\nŞu an: " + (on ? "Açık" : "Kapalı");
        new AlertDialog.Builder(activity)
                .setTitle("Zeytin Ligi'nde yer al")
                .setMessage(text)
                .setPositiveButton(on ? "Ligden ayrıl" : "Katıl", (d, w) -> apply(activity, repo, !on, onChanged))
                .setNegativeButton("Vazgeç", null)
                .setNeutralButton("KVKK metni", (d, w) -> LegalDocumentActivity.open(activity, LegalDocuments.KVKK))
                .show();
    }

    private static void apply(Activity activity, MarketCloudRepository repo, boolean join, Runnable onChanged) {
        repo.setLeagueParticipation(join, (joined, error) -> {
            if (activity.isFinishing()) return;
            String text;
            if (error != null) text = "Kaydedilemedi: internet bağlantını kontrol et.";
            else if (joined) text = "Zeytin Ligi sıralamasına katıldın.";
            else text = "Sıralamadan çıktın; ligdeki adın da silindi.";
            Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
            if (onChanged != null) onChanged.run();
        });
    }
}
