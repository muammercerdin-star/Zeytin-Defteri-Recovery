package com.zeytindefteri.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Meydan'a yazmadan önce telefonda yapılan denetim ve moderasyon durumunun önbelleği.
 *
 * - Küfür: gönderilmez, kutuda uyarı çıkar (ProfanityFilter).
 * - Susturma: social_mutes/{uid} süresi bitene kadar gönderilmez (kural sunucuda da reddeder).
 * - Moderatör: zd_moderators/{uid} ya da moderation_admin yetkisi; moderatörün mesajı onaya düşmez,
 *   Sohbet'te onay/şikâyet kuyruğu görünür.
 * - Yeni üye: ilk 3 gönderisi PENDING gider (ilk seferinde bir kez bilgi verilir).
 * Durumlar ekran açılınca {@link #refresh} ile buluttan tazelenir; bulut okunamazsa son bilinen değer kullanılır.
 */
public final class ChatGuard {
    private static final String PREFS = "zd_moderation";

    private ChatGuard() {}

    /** Gönderilebilir mi? Değilse kutuda/ekranda nedeni gösterir. */
    public static boolean allow(Activity activity, EditText input, String text) {
        ProfanityFilter.Result r = ProfanityFilter.check(text);
        if (!r.clean) {
            if (input != null) input.setError(ProfanityFilter.message());
            else Toast.makeText(activity, ProfanityFilter.message(), Toast.LENGTH_LONG).show();
            return false;
        }
        String uid = uid();
        String mute = MeydanModeration.muteMessage(prefs(activity).getLong("mute_until_" + uid, 0), System.currentTimeMillis());
        if (!mute.isEmpty()) {
            Toast.makeText(activity, mute, Toast.LENGTH_LONG).show();
            return false;
        }
        // Yeni üyeye bir kez: ilk gönderileri onaydan sonra görünür.
        if (activity instanceof ProducerFeedActivity && !isModerator(activity)
                && ownPostCount(activity, uid) < MeydanModeration.NEW_MEMBER_POSTS
                && !prefs(activity).getBoolean("pending_told_" + uid, false)) {
            prefs(activity).edit().putBoolean("pending_told_" + uid, true).apply();
            Toast.makeText(activity, "Hoş geldin! Yeni üyelerin ilk " + MeydanModeration.NEW_MEMBER_POSTS
                    + " mesajı moderatör onayından sonra herkese görünür.", Toast.LENGTH_LONG).show();
        }
        return true;
    }

    /** Moderatörlük ve susturma bilgisini buluttan tazeler (Sohbet açılınca). */
    public static void refresh(Context context) {
        final String uid = uid();
        if (uid.isEmpty()) return;
        final SharedPreferences p = prefs(context);
        try {
            FirebaseFirestore fs = FirebaseFirestore.getInstance();
            fs.collection("zd_moderators").document(uid).get()
                    .addOnSuccessListener(d -> p.edit().putBoolean("mod_" + uid, d != null && d.exists()).apply());
            fs.collection("social_mutes").document(uid).get()
                    .addOnSuccessListener(d -> {
                        Object until = d == null || !d.exists() ? null : d.get("until");
                        p.edit().putLong("mute_until_" + uid, until instanceof Number ? ((Number) until).longValue() : 0).apply();
                    });
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u != null) {
                u.getIdToken(false).addOnSuccessListener(t -> {
                    Object c = t.getClaims().get("moderation_admin");
                    p.edit().putBoolean("claim_mod_" + uid, Boolean.TRUE.equals(c)).apply();
                });
            }
        } catch (RuntimeException ignored) {
            // Çevrimdışı: son bilinen değerler kullanılır; kurallar sunucuda yine uygular.
        }
    }

    public static boolean isModerator(Context context) {
        String uid = uid();
        SharedPreferences p = prefs(context);
        return !uid.isEmpty() && (p.getBoolean("mod_" + uid, false) || p.getBoolean("claim_mod_" + uid, false));
    }

    /** Yeni gönderinin bulut durumu; aynı gönderi için bir kez belirlenir ve saklanır (sonraki yazımlarda gönderilmez). */
    public static String firstStatus(Context context, String cloudId) {
        SharedPreferences p = prefs(context);
        if (p.contains("st_" + cloudId)) return null;
        String uid = uid();
        String s = MeydanModeration.newPostStatus(ownPostCount(context, uid) - 1, isModerator(context));
        p.edit().putString("st_" + cloudId, s).apply();
        return s;
    }

    /** Kendi gönderisinin bilinen durumu (etiket için). */
    public static String knownStatus(Context context, String cloudId) {
        return prefs(context).getString("st_" + cloudId, MeydanModeration.ACTIVE);
    }

    public static void rememberStatus(Context context, String cloudId, String status) {
        if (cloudId == null || cloudId.isEmpty()) return;
        prefs(context).edit().putString("st_" + cloudId, status).apply();
    }

    /** Bu hesabın telefondaki etkin gönderi sayısı (yeni gönderi dahil). */
    static int ownPostCount(Context context, String uid) {
        if (uid == null || uid.isEmpty()) return 0;
        DatabaseHelper h = new DatabaseHelper(context);
        try {
            Cursor c = h.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM posts WHERE user_id=? AND is_active=1", new String[]{uid});
            try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
        } catch (RuntimeException e) {
            return 0;
        } finally {
            h.close();
        }
    }

    private static String uid() {
        try {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            return u == null ? "" : u.getUid();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
