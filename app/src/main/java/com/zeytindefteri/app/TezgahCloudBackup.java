package com.zeytindefteri.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tezgah kayıtlarının otomatik bulut yedeği (Firebase, ücretsiz kota içinde).
 *
 * - Yedek: Ayarlar → Yedekle dosyasının aynısı (alımlar, ödemeler, akşam hesabı, Z, cari, kasa, sıkım, satış
 *   ödemeleri, fiyatlar, dara) gzip ile sıkıştırılıp users/{uid}/tezgah_backup altına parça parça yazılır.
 * - Yalnız değişiklik varsa ve kayıt varsa gönderilir (gün sonunda ve tezgâhtan çıkınca): günde birkaç yazma.
 * - Yeni telefonda tezgâh boşsa "bulutta yedeğin var, geri yükleyeyim mi?" sorulur.
 * - Erişim: firestore.rules → users/{uid}/{collection}/{doc} yalnız sahibine açık (yeni kural gerekmez).
 */
public final class TezgahCloudBackup {
    static final String COLL = "tezgah_backup";
    private static final String META = "meta";
    private static final String PREFS = "zd_cloud_backup";
    private static final long MIN_GAP_MS = 2 * 60 * 1000L;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    /** İnternetsizken Firestore yazmayı sıraya alır, onay gelene kadar bekler; takılı kalmasın diye üst süre. */
    private static volatile long runningSince;
    private static final long STUCK_MS = 3 * 60 * 1000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Done { void done(boolean ok, String message); }

    public static final class Meta {
        public long created, bytes;
        public int parts;
        public String summary = "", hash = "";
    }

    /** reached=false: buluta ulaşılamadı (internet yok); meta=null + reached=true: bulutta yedek yok. */
    public interface MetaCallback { void result(Meta meta, boolean reached); }

    private TezgahCloudBackup() {}

    // ------------------------------------------------------------------ durum

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String uid() {
        try {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            return u == null || u.getUid() == null ? "" : u.getUid();
        } catch (RuntimeException e) {
            return "";
        }
    }

    public static long lastAt(Context c) { return prefs(c).getLong("at_" + uid(), 0); }

    /** "☁ Yedeklendi · 21:05" / "☁ Yedek bekliyor (internet)" / "" (hiç kayıt yoksa). */
    public static String statusLine(Context c) {
        SharedPreferences p = prefs(c);
        String u = uid();
        String state = p.getString("state_" + u, "");
        long at = p.getLong("at_" + u, 0);
        if ("too_big".equals(state)) return "☁ Bulut yedeği çok büyük · Ayarlar → Yedekle ile dosyaya al";
        if ("pending".equals(state)) return "☁ Yedek bekliyor · internet gelince gönderilir";
        if (at <= 0) return "";
        boolean today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(at))
                .equals(new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date()));
        String when = new SimpleDateFormat(today ? "HH:mm" : "d MMM HH:mm", new Locale("tr", "TR")).format(new Date(at));
        return "☁ Buluta yedeklendi · " + when;
    }

    // ------------------------------------------------------------------ yerel yedek metni

    /** Ayarlar → Yedekle ile aynı ayarlar (fiyatlar, dara, toplam kasa). */
    static Map<String, Object> prefsMap(Context c, String owner) {
        SharedPreferences tp = c.getApplicationContext().getSharedPreferences("zd_tezgah", Context.MODE_PRIVATE);
        Map<String, Object> m = new LinkedHashMap<>();
        String prices = tp.getString("buy_prices_" + owner, "");
        if (!prices.isEmpty()) m.put("buy_prices", prices);
        if (tp.contains("tare")) m.put("tare", (double) tp.getFloat("tare", (float) TezgahModel.DEFAULT_TARE));
        int crates = tp.getInt(TezgahCratesActivity.TOTAL_KEY + owner, 0);
        if (crates > 0) m.put("crate_total", (long) crates);
        return m;
    }

    static void applyPrefs(Context c, String owner, TezgahBackup.Data d) {
        SharedPreferences.Editor e = c.getApplicationContext().getSharedPreferences("zd_tezgah", Context.MODE_PRIVATE).edit();
        Object prices = d.prefs.get("buy_prices");
        if (prices != null) e.putString("buy_prices_" + owner, String.valueOf(prices));
        Object tare = d.prefs.get("tare");
        if (tare instanceof Number) e.putFloat("tare", ((Number) tare).floatValue());
        Object crates = d.prefs.get("crate_total");
        if (crates instanceof Number) e.putInt(TezgahCratesActivity.TOTAL_KEY + owner, ((Number) crates).intValue());
        e.apply();
    }

    private static CollectionReference coll(String uid) {
        return FirebaseFirestore.getInstance().collection("users").document(uid).collection(COLL);
    }

    // ------------------------------------------------------------------ yedekle

    /** Değişiklik varsa yedekler. force=false iken en sık 2 dakikada bir denenir. done null olabilir (ana iş parçacığında çağrılır). */
    public static void backup(Context context, boolean force, Done done) {
        final Context app = context.getApplicationContext();
        final String uid = uid();
        if (uid.isEmpty()) { post(done, false, "Oturum yok"); return; }
        SharedPreferences p = prefs(app);
        long now = System.currentTimeMillis();
        if (!force && now - p.getLong("try_" + uid, 0) < MIN_GAP_MS) { post(done, true, ""); return; }
        if (RUNNING.get() && now - runningSince > STUCK_MS) RUNNING.set(false);
        if (!RUNNING.compareAndSet(false, true)) { post(done, true, ""); return; }
        runningSince = now;
        p.edit().putLong("try_" + uid, now).apply();
        WORKER.execute(() -> {
            try {
                runBackup(app, uid, done);
            } catch (RuntimeException e) {
                RUNNING.set(false);
                prefs(app).edit().putString("state_" + uid, "pending").apply();
                post(done, false, "Yedeklenemedi");
            }
        });
    }

    private static void runBackup(Context app, String uid, Done done) {
        DatabaseHelper h = new DatabaseHelper(app);
        String text;
        try {
            String owner = h.getActiveOwnerUid();
            if (owner == null || !owner.equals(uid)) { RUNNING.set(false); post(done, false, "Hesap eşleşmedi"); return; }
            text = TezgahBackup.export(h.getWritableDatabase(), owner, prefsMap(app, owner), System.currentTimeMillis());
        } finally {
            h.close();
        }
        TezgahBackup.Data d = TezgahBackup.parse(text);
        SharedPreferences p = prefs(app);
        String hash = TezgahCloudPack.hash(text);
        if (!TezgahCloudPack.hasRows(d)) { RUNNING.set(false); post(done, true, ""); return; }
        if (hash.equals(p.getString("hash_" + uid, ""))) {
            p.edit().putString("state_" + uid, "ok").apply();
            RUNNING.set(false);
            post(done, true, "Yedek güncel");
            return;
        }
        byte[] packed = TezgahCloudPack.gzip(text);
        if (packed.length > TezgahCloudPack.MAX_BYTES) {
            p.edit().putString("state_" + uid, "too_big").apply();
            RUNNING.set(false);
            post(done, false, "Yedek çok büyük");
            return;
        }
        final List<byte[]> parts = TezgahCloudPack.split(packed, TezgahCloudPack.PART_BYTES);
        final long created = System.currentTimeMillis();
        final String summary = TezgahBackup.describe(d);
        p.edit().putString("state_" + uid, "pending").apply();
        CollectionReference c = coll(uid);
        // Önceki yedeğin parça sayısı: fazlalık parçalar silinsin.
        c.document(META).get().continueWithTask(prev -> {
            int oldParts = 0;
            if (prev.isSuccessful() && prev.getResult() != null && prev.getResult().exists()) {
                Long n = prev.getResult().getLong("parts");
                oldParts = n == null ? 0 : n.intValue();
            }
            WriteBatch b = FirebaseFirestore.getInstance().batch();
            for (int i = 0; i < parts.size(); i++) {
                Map<String, Object> m = new HashMap<>();
                m.put("i", (long) i);
                m.put("data", Blob.fromBytes(parts.get(i)));
                m.put("created", created);
                b.set(c.document("part_" + i), m);
            }
            for (int i = parts.size(); i < oldParts; i++) b.delete(c.document("part_" + i));
            Map<String, Object> meta = new HashMap<>();
            meta.put("created", created);
            meta.put("parts", (long) parts.size());
            meta.put("bytes", (long) packed.length);
            meta.put("hash", hash);
            meta.put("summary", summary);
            meta.put("version", (long) TezgahBackup.VERSION);
            b.set(c.document(META), meta);
            return b.commit();
        }).addOnCompleteListener(t -> {
            SharedPreferences.Editor e = prefs(app).edit();
            if (t.isSuccessful()) {
                e.putString("hash_" + uid, hash).putLong("at_" + uid, created).putString("state_" + uid, "ok");
            } else {
                e.putString("state_" + uid, "pending");
            }
            e.apply();
            RUNNING.set(false);
            post(done, t.isSuccessful(), t.isSuccessful() ? "Buluta yedeklendi" : "İnternet yok; sonra denenecek");
        });
    }

    // ------------------------------------------------------------------ geri yükle

    /** Buluttaki son yedeğin bilgisi (yoksa null). */
    public static void fetchMeta(MetaCallback cb) {
        String uid = uid();
        if (uid.isEmpty()) { MAIN.post(() -> cb.result(null, false)); return; }
        coll(uid).document(META).get().addOnCompleteListener(t -> {
            Meta m = null;
            if (t.isSuccessful() && t.getResult() != null && t.getResult().exists()) {
                DocumentSnapshot s = t.getResult();
                m = new Meta();
                Long c = s.getLong("created"), p = s.getLong("parts"), b = s.getLong("bytes");
                m.created = c == null ? 0 : c;
                m.parts = p == null ? 0 : p.intValue();
                m.bytes = b == null ? 0 : b;
                m.summary = s.getString("summary") == null ? "" : s.getString("summary");
                m.hash = s.getString("hash") == null ? "" : s.getString("hash");
                if (m.parts <= 0) m = null;
            }
            final Meta out = m;
            cb.result(out, t.isSuccessful() && t.getResult() != null && !t.getResult().getMetadata().isFromCache());
        });
    }

    /** Buluttaki yedeği indirip bu hesabın yerel tezgah kayıtlarının yerine yazar. */
    public static void restore(Context context, Meta meta, Done done) {
        final Context app = context.getApplicationContext();
        final String uid = uid();
        if (uid.isEmpty() || meta == null || meta.parts <= 0) { post(done, false, "Bulut yedeği yok"); return; }
        CollectionReference c = coll(uid);
        List<Task<DocumentSnapshot>> gets = new ArrayList<>();
        for (int i = 0; i < meta.parts; i++) gets.add(c.document("part_" + i).get());
        Tasks.whenAllComplete(gets).addOnCompleteListener(all -> WORKER.execute(() -> {
            try {
                List<byte[]> parts = new ArrayList<>();
                for (Task<DocumentSnapshot> t : gets) {
                    if (!t.isSuccessful() || t.getResult() == null || !t.getResult().exists()) {
                        post(done, false, "Yedek indirilemedi; internetini kontrol et.");
                        return;
                    }
                    Blob blob = t.getResult().getBlob("data");
                    if (blob == null) { post(done, false, "Bulut yedeği eksik."); return; }
                    parts.add(blob.toBytes());
                }
                String text = TezgahCloudPack.gunzip(TezgahCloudPack.join(parts));
                TezgahBackup.Data d = TezgahBackup.parse(text);
                DatabaseHelper h = new DatabaseHelper(app);
                int n;
                try {
                    String owner = h.getActiveOwnerUid();
                    if (owner == null || !owner.equals(uid)) { post(done, false, "Hesap eşleşmedi"); return; }
                    n = TezgahBackup.restore(h.getWritableDatabase(), owner, d);
                    if (n >= 0) applyPrefs(app, owner, d);
                } finally {
                    h.close();
                }
                if (n < 0) { post(done, false, "Geri yüklenemedi; hiçbir kayıt değişmedi."); return; }
                prefs(app).edit().putString("hash_" + uid, TezgahCloudPack.hash(text))
                        .putLong("at_" + uid, meta.created).putString("state_" + uid, "ok").apply();
                post(done, true, "Geri yüklendi ✓ " + TezgahBackup.describe(d));
            } catch (RuntimeException e) {
                post(done, false, "Bulut yedeği okunamadı.");
            }
        }));
    }

    /** Bu telefonda yedeklenecek (tezgah/ödeme/sıkım) kayıt var mı? */
    public static boolean hasLocalRows(Context c) {
        DatabaseHelper h = new DatabaseHelper(c.getApplicationContext());
        try {
            String owner = h.getActiveOwnerUid();
            return TezgahCloudPack.hasRows(TezgahBackup.parse(TezgahBackup.export(h.getWritableDatabase(),
                    owner == null ? "" : owner, new LinkedHashMap<>(), 0)));
        } catch (RuntimeException e) {
            return true; // emin değilsek geri yükleme önermeyelim
        } finally {
            h.close();
        }
    }

    /** Yeni telefon: tezgâhta hiç kayıt yoksa ve bulutta yedek varsa bir kez sorulur (true: soruldu say). */
    public static boolean shouldOfferRestore(Context c) {
        String u = uid();
        return !u.isEmpty() && !prefs(c).getBoolean("offered_" + u, false);
    }

    public static void markOffered(Context c) { prefs(c).edit().putBoolean("offered_" + uid(), true).apply(); }

    private static void post(Done done, boolean ok, String message) {
        if (done == null) return;
        MAIN.post(() -> done.done(ok, message));
    }
}
