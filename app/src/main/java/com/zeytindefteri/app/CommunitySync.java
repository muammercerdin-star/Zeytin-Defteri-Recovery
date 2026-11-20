package com.zeytindefteri.app;

import android.content.Context;
import android.database.Cursor;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Ortak döngünün telefondaki kapısı (İP8). Öğretilen söz izin AÇIKSA temizlenip kuyruğa girer; "Unut" kuyruktan
 * düşürür ya da geri çekme isteği yapar. Gönderim yalnız Türkiye'deki sunucuya ve yalnız adres doluysa: İP8-A'da adres
 * yoktur, flush() ağa hiç çıkmaz. İzin ekranı İP8-B'ye kadar Ayarlar'da GÖRÜNMEZ (SETTINGS_VISIBLE).
 */
final class CommunitySync {
    private CommunitySync() {
    }

    /** "Uygulamanın gelişmesine anonim katkı" Ayarlar'da görünür mü: İP8-B'ye kadar hayır. */
    static final boolean SETTINGS_VISIBLE = false;

    /** Öğretildi: izin açıksa temizlenmiş katkı kuyruğa girer (aynı söz-anlam bir kez). */
    static void taught(Context context, String owner, LearnedTerms.Term term) {
        try {
            if (!CommunityDb.consent(context, owner)) {
                return;
            }
            ContributionCleaner.Contribution c = ContributionCleaner.clean(term, localNames(context),
                    setting(context, "user_province"), MarketBackendConfig.buildValue("VERSION_NAME", ""));
            new CommunityQueue(CommunityDb.store(context, owner), new SecureRandom()).offer(c, true);
        } catch (RuntimeException ignored) {
            // katkı hazırlanamazsa öğretme yine olur
        }
    }

    /** "Unut": gönderilmediyse düşer, gönderildiyse geri çekme isteği (izin kapalı olsa da). */
    static void forgot(Context context, String owner, String root) {
        try {
            new CommunityQueue(CommunityDb.store(context, owner), new SecureRandom()).forget(root);
        } catch (RuntimeException ignored) {
            // kuyruk okunamazsa sessiz
        }
    }

    /** İzni değiştirir (İP8-B'de Ayarlar'dan). Kapatınca bekleyenler düşer, gönderilmişler geri çekilir. */
    static void setConsent(Context context, String owner, boolean on) {
        CommunityDb.setConsent(context, owner, on);
        if (!on) {
            new CommunityQueue(CommunityDb.store(context, owner), new SecureRandom()).consentOff();
        }
    }

    /** Kuyruğu göndermeyi dener. Adres yoksa (İP8-A) hiçbir şey yapmaz; ağa çıkmaz. Ana iş parçacığında çağrılmaz. */
    static int flush(Context context, String owner) {
        String url = CommunitySender.url();
        if (!CommunitySender.configured(url)) {
            return 0;
        }
        CommunityQueue queue = new CommunityQueue(CommunityDb.store(context, owner), new SecureRandom());
        String pseudonym = CommunityQueue.pseudonym(CommunityDb.secret(context), CommunityQueue.season(Calendar.getInstance()));
        return CommunitySender.flush(url, queue, CommunityDb.consent(context, owner), pseudonym, CommunitySync::post);
    }

    /** HTTPS gönderimi (yalnız configured() doğruyken çağrılır). */
    private static boolean post(String address, String json) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = connection.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** Telefondaki bahçe, alıcı ve kullanıcı adları: yalnız temizleyicide karşılaştırma için (dışarı çıkmaz). */
    private static List<String> localNames(Context context) {
        List<String> names = new ArrayList<>();
        DatabaseHelper db = new DatabaseHelper(context.getApplicationContext());
        try {
            String owner = db.getActiveOwnerUid();
            String cond = owner.isEmpty() ? "(owner_uid IS NULL OR TRIM(owner_uid) = '')" : "owner_uid = ?";
            String[] args = owner.isEmpty() ? null : new String[]{owner};
            for (String table : new String[]{"gardens", "buyers"}) {
                try (Cursor c = db.getReadableDatabase().rawQuery("SELECT name FROM " + table + " WHERE " + cond, args)) {
                    while (c.moveToNext()) {
                        names.add(c.getString(0));
                    }
                } catch (RuntimeException ignored) {
                    // tablo okunamazsa diğerleri
                }
            }
            SessionManager session = new SessionManager(context);
            names.add(session.getUserName());
            names.add(session.getDisplayName());
        } finally {
            db.close();
        }
        return names;
    }

    private static String setting(Context context, String key) {
        DatabaseHelper db = new DatabaseHelper(context.getApplicationContext());
        try {
            return db.getSetting(key, "");
        } finally {
            db.close();
        }
    }
}
