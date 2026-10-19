package com.zeytindefteri.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sohbette "… yazıyor" göstergesi.
 *
 * Her kullanıcının tek bir belgesi vardır: social_typing/{uid} = {user_id, user_name, post_type, until}.
 * - Yazmaya başlayınca belge yazılır; yazmaya devam ettikçe en fazla {@link #PING_MS} ms'de bir tazelenir
 *   (her harfte değil: okuma/yazma maliyeti).
 * - Gönderince, kutu boşalınca ya da ekrandan çıkınca belge silinir.
 * - İnternet kesilirse belge "until" zamanı geçince ({@link #TTL_MS}) kendiliğinden geçersiz sayılır.
 * Yazılan metin asla gönderilmez. Dinleme yalnız Sohbet ekranı açıkken sürer.
 */
public final class ChatTypingRepository {
    public static final long PING_MS = 5000;
    public static final long TTL_MS = 10000;
    static final String COLLECTION = "social_typing";
    private static final String PREFS = "zd_chat";
    private static final String KEY_SHARE = "share_typing";

    public static final class Typer {
        public String uid = "", name = "", postType = "";
    }

    public interface Listener { void changed(List<Typer> typers); }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Typer> live = new HashMap<>();
    private final Map<String, Long> until = new HashMap<>();
    private ListenerRegistration registration;
    private Listener listener;
    private java.util.Set<String> hidden = new java.util.HashSet<>();
    private long lastPing;
    private boolean wrote;
    private String cachedName;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            publish();
            if (registration != null) main.postDelayed(this, 2000);
        }
    };

    public ChatTypingRepository(Context context) { this.context = context.getApplicationContext(); }

    // ------------------------------------------------------------------ ayar

    public static boolean sharing(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SHARE, true);
    }

    public static void setSharing(Context c, boolean on) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SHARE, on).apply();
    }

    // ------------------------------------------------------------------ dinleme

    /** Sohbet ekranı açılınca. hiddenUids: engellenen kullanıcılar (gösterilmez). */
    public void start(java.util.Set<String> hiddenUids, Listener l) {
        stop();
        listener = l;
        hidden = hiddenUids == null ? new java.util.HashSet<>() : hiddenUids;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        final String me = user.getUid();
        try {
            registration = FirebaseFirestore.getInstance().collection(COLLECTION)
                    .whereGreaterThan("until", System.currentTimeMillis() - 2000)
                    .limit(20)
                    .addSnapshotListener((snapshot, error) -> {
                        if (error != null || snapshot == null) return;
                        live.clear();
                        until.clear();
                        for (DocumentSnapshot d : snapshot.getDocuments()) {
                            String uid = d.getId();
                            if (uid.equals(me)) continue;
                            Typer t = new Typer();
                            t.uid = uid;
                            Object n = d.get("user_name");
                            t.name = n instanceof String ? (String) n : "Bir üretici";
                            Object p = d.get("post_type");
                            t.postType = p instanceof String ? (String) p : "";
                            Object u = d.get("until");
                            long end = u instanceof Number ? ((Number) u).longValue() : 0L;
                            live.put(uid, t);
                            until.put(uid, end);
                        }
                        publish();
                    });
            main.postDelayed(tick, 2000);
        } catch (RuntimeException ignored) {
            registration = null;
        }
    }

    public void stop() {
        main.removeCallbacks(tick);
        if (registration != null) registration.remove();
        registration = null;
        live.clear();
        until.clear();
        if (listener != null) listener.changed(new ArrayList<>());
        listener = null;
    }

    private void publish() {
        if (listener == null) return;
        List<Typer> out = visible(new ArrayList<>(live.values()), until, hidden, System.currentTimeMillis());
        listener.changed(out);
    }

    /** Süresi geçmemiş ve engellenmemiş yazanlar (ad sırasıyla). Android'e bağlı değil, test edilir. */
    static List<Typer> visible(List<Typer> all, Map<String, Long> until, java.util.Set<String> hidden, long now) {
        List<Typer> out = new ArrayList<>();
        for (Typer t : all) {
            Long end = until.get(t.uid);
            if (end == null || end <= now) continue;
            if (hidden != null && hidden.contains(t.uid)) continue;
            out.add(t);
        }
        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    /** Göstergede yazılacak cümle; kimse yoksa boş. activeType dışındaki kanal etiketle belirtilir. */
    public static String label(List<Typer> typers, String activeType) {
        if (typers == null || typers.isEmpty()) return "";
        if (typers.size() == 1) {
            Typer t = typers.get(0);
            boolean sameChannel = t.postType.isEmpty() || "ALL".equals(t.postType) || "ALL".equals(activeType)
                    || t.postType.equals(activeType);
            String where = sameChannel ? "" : " (" + channel(t.postType) + ")";
            return firstName(t.name) + where + " yazıyor";
        }
        if (typers.size() == 2) return firstName(typers.get(0).name) + " ve " + firstName(typers.get(1).name) + " yazıyor";
        return typers.size() + " kişi yazıyor";
    }

    static String firstName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return "Bir üretici";
        int space = n.indexOf(' ');
        return space > 0 ? n.substring(0, space) : n;
    }

    static String channel(String type) {
        switch (type == null ? "" : type) {
            case "MARKET": return "#Piyasa";
            case "HARVEST": return "#Hasat";
            case "PEST": return "#Hastalık";
            case "IRRIGATION": return "#Sulama";
            case "SALE": return "#Alım-satım";
            default: return "#Genel";
        }
    }

    // ------------------------------------------------------------------ yazma

    /** Mesaj kutusunda yazılırken çağrılır; en fazla PING_MS'de bir buluta gider. */
    public void typing(String postType) {
        if (!sharing(context)) return;
        long now = System.currentTimeMillis();
        if (wrote && now - lastPing < PING_MS) return;
        final FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        lastPing = now;
        wrote = true;
        final String type = postType == null ? "GENERAL" : postType;
        if (cachedName != null) { write(user.getUid(), cachedName, type); return; }
        // Ad, kuraldaki request.auth.token.name ile aynı olmalı: oturum belgesinden okunur.
        user.getIdToken(false).addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) return;
            Object n = task.getResult().getClaims().get("name");
            cachedName = n instanceof String && !((String) n).isEmpty() ? (String) n : "Üretici";
            write(user.getUid(), cachedName, type);
        });
    }

    private void write(String uid, String name, String type) {
        Map<String, Object> data = new HashMap<>();
        data.put("user_id", uid);
        data.put("user_name", name);
        data.put("post_type", type);
        data.put("until", System.currentTimeMillis() + TTL_MS);
        try { FirebaseFirestore.getInstance().collection(COLLECTION).document(uid).set(data); }
        catch (RuntimeException ignored) { }
    }

    /** Gönderince, kutu boşalınca ya da ekrandan çıkınca. */
    public void clear() {
        if (!wrote) return;
        wrote = false;
        lastPing = 0;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        try { FirebaseFirestore.getInstance().collection(COLLECTION).document(user.getUid()).delete(); }
        catch (RuntimeException ignored) { }
    }
}
