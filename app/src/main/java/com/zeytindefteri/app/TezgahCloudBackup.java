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
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
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
 * - Her telefon kendi bölmesine yazar (TezgahCloudIndex): ikinci/yeni telefon eski telefonun yedeğini ezmez.
 *   Geri yüklerken hangi telefonun yedeği istendiği seçilir; başka telefonda daha yeni yedek varsa uyarılır.
 * - Erişim: firestore.rules → users/{uid}/{collection}/{doc} yalnız sahibine açık (yeni kural gerekmez).
 */
public final class TezgahCloudBackup {
    static final String COLL = "tezgah_backup";
    private static final String META = "meta";
    private static final String INDEX = "index";
    /** Cihaz kimliği ayrı dosyada: Android'in otomatik yedeği bu dosyayı yeni telefona taşımaz (backup kuralları). */
    private static final String DEVICE_PREFS = "zd_device";
    private static final String PREFS = "zd_cloud_backup";
    private static final long MIN_GAP_MS = 2 * 60 * 1000L;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    /** İnternetsizken Firestore yazmayı sıraya alır, onay gelene kadar bekler; takılı kalmasın diye üst süre. */
    private static volatile long runningSince;
    private static final long STUCK_MS = 3 * 60 * 1000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Done { void done(boolean ok, String message); }

    /** Buluttaki bir yedek (bir telefonun bölmesi). */
    public static final class Meta extends TezgahCloudIndex.Entry { }

    /** reached=false: buluta ulaşılamadı (internet yok); meta=null + reached=true: bulutta yedek yok. */
    public interface MetaCallback { void result(Meta meta, boolean reached); }

    /** Buluttaki bütün yedekler, en yeni başta (boş liste: yedek yok). */
    public interface ListCallback { void result(List<Meta> all, boolean reached); }

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

    /** Bu kurulumun kalıcı kimliği (rastgele; telefon bilgisi içermez). */
    static String deviceId(Context c) {
        SharedPreferences p = c.getApplicationContext().getSharedPreferences(DEVICE_PREFS, Context.MODE_PRIVATE);
        String id = TezgahCloudIndex.cleanId(p.getString("id", ""));
        if (id.length() < 8) {
            id = TezgahCloudIndex.cleanId(java.util.UUID.randomUUID().toString());
            p.edit().putString("id", id).apply();
        }
        return id;
    }

    /** Listede görünecek telefon adı ("Samsung SM-A515F"). */
    private static String deviceName() {
        String man = android.os.Build.MANUFACTURER == null ? "" : android.os.Build.MANUFACTURER.trim();
        String model = android.os.Build.MODEL == null ? "" : android.os.Build.MODEL.trim();
        if (!man.isEmpty()) man = man.substring(0, 1).toUpperCase(Locale.ROOT) + man.substring(1);
        String n = model.toLowerCase(Locale.ROOT).startsWith(man.toLowerCase(Locale.ROOT)) ? model : (man + " " + model).trim();
        return n.length() > 40 ? n.substring(0, 40) : n;
    }

    /** "Başka telefonda daha yeni yedek var" uyarısı (yoksa ""). */
    public static String otherNewerLine(Context c) {
        String v = prefs(c).getString("other_" + uid(), "");
        int bar = v.lastIndexOf('|');
        if (bar <= 0) return "";
        long at;
        try { at = Long.parseLong(v.substring(bar + 1)); } catch (NumberFormatException e) { return ""; }
        if (at <= lastAt(c)) return "";
        return "⚠ " + v.substring(0, bar) + " telefonunda daha yeni yedek var ("
                + new SimpleDateFormat("d MMM HH:mm", new Locale("tr", "TR")).format(new Date(at))
                + "). İki telefonun kayıtları birleşmez; doğru telefonu seç.";
    }

    private static void rememberOther(Context app, String uid, TezgahCloudIndex.Entry other) {
        prefs(app).edit().putString("other_" + uid, other == null ? ""
                : TezgahCloudIndex.label(other).replace('|', ' ') + "|" + other.created).apply();
    }

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
        final CollectionReference c = coll(uid);
        final String dev = deviceId(app);
        final String name = deviceName();
        final int rows = rowTotal(d);
        final long knownAt = p.getLong("at_" + uid, 0);
        final String knownHash = p.getString("hash_" + uid, "");
        // Dizini oku: bu telefonun eski parça sayısı (fazlası silinir), sınırı aşan eski bölmeler, başka telefondaki yeni yedek.
        final Task<DocumentSnapshot> idxGet = c.document(INDEX).get(), metaGet = c.document(META).get();
        Tasks.whenAllComplete(idxGet, metaGet).continueWithTask(prev -> {
            if (!idxGet.isSuccessful() || !metaGet.isSuccessful()) {
                Exception x = idxGet.getException() != null ? idxGet.getException() : metaGet.getException();
                throw x == null ? new IllegalStateException("dizin okunamadı") : x;
            }
            Map<String, Object> devices = devicesOf(idxGet.getResult());
            List<TezgahCloudIndex.Entry> list = TezgahCloudIndex.parse(devices, dev);
            // İlk sürümün bölmesiz yedeği: meta ezilmeden önce dizine "Önceki yedek" olarak taşınır (parçaları part_N).
            Map<String, Object> legacyRow = null;
            if (metaGet.getResult() != null && metaGet.getResult().exists()) {
                int before = list.size();
                TezgahCloudIndex.addLegacy(list, metaGet.getResult().getData());
                if (list.size() > before) {
                    TezgahCloudIndex.Entry lg = null;
                    for (TezgahCloudIndex.Entry e : list) if (TezgahCloudIndex.LEGACY.equals(e.device)) lg = e;
                    if (lg != null) legacyRow = TezgahCloudIndex.row(lg.created, lg.parts, lg.bytes, lg.hash, lg.summary, lg.name, 0);
                }
            }
            rememberOther(app, uid, TezgahCloudIndex.newerElsewhere(list, knownAt, knownHash));
            TezgahCloudIndex.Entry mine = TezgahCloudIndex.mine(list);
            int oldParts = mine == null ? 0 : mine.parts;
            WriteBatch b = FirebaseFirestore.getInstance().batch();
            for (int i = 0; i < parts.size(); i++) {
                Map<String, Object> m = new HashMap<>();
                m.put("i", (long) i);
                m.put("data", Blob.fromBytes(parts.get(i)));
                m.put("created", created);
                m.put("device", dev);
                b.set(c.document(TezgahCloudIndex.partDoc(dev, i)), m);
            }
            for (int i = parts.size(); i < oldParts; i++) b.delete(c.document(TezgahCloudIndex.partDoc(dev, i)));
            Map<String, Object> devMap = new HashMap<>();
            devMap.put(dev, TezgahCloudIndex.row(created, parts.size(), packed.length, hash, summary, name, rows));
            if (legacyRow != null) devMap.put(TezgahCloudIndex.LEGACY, legacyRow);
            // Sınır: en fazla 5 bölme; en eskiler (parçalarıyla) silinir.
            for (TezgahCloudIndex.Entry old : TezgahCloudIndex.evict(list, dev)) {
                devMap.put(old.device, FieldValue.delete());
                for (int i = 0; i < old.parts && i < 20; i++) b.delete(c.document(old.partDoc(i)));
            }
            Map<String, Object> index = new HashMap<>();
            index.put("devices", devMap);
            b.set(c.document(INDEX), index, SetOptions.merge());
            Map<String, Object> meta = new HashMap<>();
            meta.put("created", created);
            meta.put("parts", (long) parts.size());
            meta.put("bytes", (long) packed.length);
            meta.put("hash", hash);
            meta.put("summary", summary);
            meta.put("version", (long) TezgahBackup.VERSION);
            meta.put("device", dev);
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

    /** Buluttaki en son yedek (yoksa null). */
    public static void fetchMeta(Context context, MetaCallback cb) {
        fetchAll(context, (all, reached) -> cb.result(all.isEmpty() ? null : all.get(0), reached));
    }

    /** Buluttaki bütün telefonların yedekleri (dizin + ilk sürümün bölmesiz yedeği), en yeni başta. */
    public static void fetchAll(Context context, ListCallback cb) {
        final String dev = deviceId(context);
        String uid = uid();
        if (uid.isEmpty()) { MAIN.post(() -> cb.result(new ArrayList<>(), false)); return; }
        CollectionReference c = coll(uid);
        Task<DocumentSnapshot> idx = c.document(INDEX).get(), old = c.document(META).get();
        Tasks.whenAllComplete(idx, old).addOnCompleteListener(done -> {
            boolean reached = idx.isSuccessful() && idx.getResult() != null && !idx.getResult().getMetadata().isFromCache();
            List<TezgahCloudIndex.Entry> list = TezgahCloudIndex.parse(
                    idx.isSuccessful() ? devicesOf(idx.getResult()) : null, dev);
            if (old.isSuccessful() && old.getResult() != null && old.getResult().exists()) {
                TezgahCloudIndex.addLegacy(list, old.getResult().getData());
            }
            List<Meta> out = new ArrayList<>();
            for (TezgahCloudIndex.Entry e : list) out.add(copy(e));
            cb.result(out, reached);
        });
    }

    private static Meta copy(TezgahCloudIndex.Entry e) {
        Meta m = new Meta();
        m.device = e.device; m.name = e.name; m.summary = e.summary; m.hash = e.hash;
        m.created = e.created; m.bytes = e.bytes; m.parts = e.parts; m.rows = e.rows; m.mine = e.mine;
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> devicesOf(DocumentSnapshot s) {
        if (s == null || !s.exists()) return null;
        Object v = s.get("devices");
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    private static int rowTotal(TezgahBackup.Data d) {
        int n = 0;
        for (String t : TezgahBackup.TABLES) n += d.rowCount(t);
        return n;
    }

    /** Buluttaki yedeği indirip bu hesabın yerel tezgah kayıtlarının yerine yazar. */
    public static void restore(Context context, Meta meta, Done done) {
        final Context app = context.getApplicationContext();
        final String uid = uid();
        if (uid.isEmpty() || meta == null || meta.parts <= 0) { post(done, false, "Bulut yedeği yok"); return; }
        CollectionReference c = coll(uid);
        List<Task<DocumentSnapshot>> gets = new ArrayList<>();
        for (int i = 0; i < meta.parts; i++) gets.add(c.document(meta.partDoc(i)).get());
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
                    TezgahRescue.save(app, h.getWritableDatabase(), owner); // yanlış yedekse "geri al" için
                    n = TezgahBackup.restore(h.getWritableDatabase(), owner, d);
                    if (n >= 0) applyPrefs(app, owner, d);
                } finally {
                    h.close();
                }
                if (n < 0) { post(done, false, "Geri yüklenemedi; hiçbir kayıt değişmedi."); return; }
                // Geri yüklenen yedek bu telefonun bildiği son hal olur; bir sonraki yedek bu telefonun bölmesine gider.
                prefs(app).edit().putString("hash_" + uid, TezgahCloudPack.hash(text))
                        .putLong("at_" + uid, meta.created).putString("state_" + uid, "ok")
                        .putString("other_" + uid, "").apply();
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
