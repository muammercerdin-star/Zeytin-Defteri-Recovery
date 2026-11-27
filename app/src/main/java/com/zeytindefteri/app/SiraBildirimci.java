package com.zeytindefteri.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

/**
 * Sıram yerel bildirimi (sunucusuz, ücretsiz). SiraYoklama bir haber kararı verince gösterir; tıklanınca
 * SiramActivity (detay) açılır. Kişisel veri taşımaz (yalnız başlık + kısa metin + kod).
 *
 * GECE KURALI YOK: Sıram bildirimi operasyoneldir (fabrika gece de çalışır, sıra gece gelebilir) ve bu,
 * uygulamanın genel "gece 22–05 bildirim göndermez" kuralına çiftçinin onayıyla konmuş bilinçli bir istisnadır.
 */
final class SiraBildirimci implements SiraYoklama.Haberci {

    static final String CHANNEL = "zeytin_sira";

    private final Context app;

    SiraBildirimci(Context c) {
        this.app = c.getApplicationContext();
    }

    static void createChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Yağhane Sıram", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Sıran yaklaşınca ve sıra sana gelince haber verir.");
        nm.createNotificationChannel(ch);
    }

    @Override
    public void haber(String token, SiraBildirim.Karar karar) {
        if (Build.VERSION.SDK_INT >= 33
                && app.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || !nm.areNotificationsEnabled()) {
            return;
        }
        createChannel(app);
        Intent open = new Intent(app, SiramActivity.class);
        open.setData(Uri.parse("zeytin-sira://" + token));
        open.putExtra(SiramActivity.EXTRA_TOKEN, token);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(app, token.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(app, CHANNEL)
                : new Notification.Builder(app);
        b.setSmallIcon(R.drawable.ic_bell_line);
        b.setContentTitle(karar.baslik);
        b.setContentText(karar.metin);
        b.setStyle(new Notification.BigTextStyle().bigText(karar.metin));
        b.setContentIntent(pending);
        b.setAutoCancel(true);
        b.setCategory(Notification.CATEGORY_REMINDER);
        b.setVisibility(Notification.VISIBILITY_PRIVATE);
        try {
            nm.notify("zd-sira-" + token, 0, b.build());
        } catch (SecurityException ignored) {
            // İzin çalışma anında geri alınmış olabilir; sıra ekranı yine güncel durumu gösterir.
        }
    }
}
