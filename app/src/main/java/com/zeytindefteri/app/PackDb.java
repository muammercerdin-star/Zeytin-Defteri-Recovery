package com.zeytindefteri.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Bilgi paketi telefonda (İP8). Gömülü başlangıç paketi (res/raw/bilgi_paketi.json) ve İP8-B'de indirilecek
 * paketler buradan okunur; çiftçinin "Herkesten öğrendiklerim"den kapattığı sözler ayrı dosyada (zd_pack.db):
 * Google yedeğine, telefon aktarımına ve Yedekle dosyasına girmez, hesap silinince silinir.
 * Paket uygulanmadan önce doğrulanır ve gömülü kontrol bankasından geçer (PackGate); geçmezse paket kullanılmaz,
 * bir öncekiyle (yoksa paketsiz) devam edilir. Bu pakette ağdan indirme YOK.
 */
final class PackDb extends SQLiteOpenHelper {
    static final String FILE = "zd_pack.db";
    private static final int VERSION = 1;
    private static PackDb instance;
    /** Doğrulanmış ve bankadan geçmiş paket (uygulama açıkken bir kez hesaplanır). */
    private static volatile KnowledgePack loaded;
    private static volatile boolean tried;

    private PackDb(Context context) {
        super(context.getApplicationContext(), FILE, null, VERSION);
    }

    static synchronized PackDb get(Context context) {
        if (instance == null) {
            instance = new PackDb(context);
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS disabled (owner TEXT NOT NULL, root TEXT NOT NULL, PRIMARY KEY (owner, root))");
        db.execSQL("CREATE TABLE IF NOT EXISTS applied (id INTEGER PRIMARY KEY CHECK (id = 1), json TEXT NOT NULL)");
        // Bankadan geçti mi: paket özeti + uygulama sürümüne göre (kurallar sürümle değişir), her açılışta yeniden koşmasın.
        db.execSQL("CREATE TABLE IF NOT EXISTS verified (digest TEXT NOT NULL, app TEXT NOT NULL, ok INTEGER NOT NULL, "
                + "PRIMARY KEY (digest, app))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onCreate(db);
    }

    /** Paketi arka planda hazırlar (asistan açılırken); ana iş parçacığını bekletmez. */
    static void warm(Context context) {
        final Context app = context.getApplicationContext();
        new Thread(() -> pack(app), "zd-paket").start();
    }

    /** Kullanılan paket: indirilmiş (İP8-B) ya da gömülü; doğrulanamaz ya da bankayı bozarsa null (paketsiz). */
    static synchronized KnowledgePack pack(Context context) {
        if (tried) {
            return loaded;
        }
        tried = true;
        try {
            KnowledgePack embedded = KnowledgePack.read(raw(context, R.raw.bilgi_paketi), KnowledgePack.PUBLIC_KEYS);
            KnowledgePack chosen = passes(context, embedded) ? embedded : null;
            String stored = appliedJson(context);
            KnowledgePack downloaded = stored == null ? null : KnowledgePack.read(stored, KnowledgePack.PUBLIC_KEYS);
            if (downloaded != null && (chosen == null || downloaded.version > chosen.version) && passes(context, downloaded)) {
                chosen = downloaded;
            }
            loaded = chosen;
        } catch (RuntimeException e) {
            loaded = null;
        }
        return loaded;
    }

    /** Bankadan geçer mi (sonuç paket özeti + uygulama sürümüyle saklanır). */
    private static boolean passes(Context context, KnowledgePack p) {
        if (p == null) {
            return false;
        }
        String app = MarketBackendConfig.buildValue("VERSION_NAME", "");
        SQLiteDatabase db = get(context).getWritableDatabase();
        try (Cursor c = db.query("verified", new String[]{"ok"}, "digest=? AND app=?", new String[]{p.digest, app}, null, null, null)) {
            if (c.moveToFirst()) {
                return c.getInt(0) == 1;
            }
        }
        boolean ok = PackGate.broken(p, PackGate.sentences(raw(context, R.raw.kontrol_bankasi))).isEmpty();
        ContentValues v = new ContentValues();
        v.put("digest", p.digest);
        v.put("app", app);
        v.put("ok", ok ? 1 : 0);
        db.insertWithOnConflict("verified", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        return ok;
    }

    /** Paket sözleri; paket henüz hazır değilse (arka planda doğrulanıyor) ya da yoksa boş. Bekletmez. */
    static List<LearnedTerms.Term> terms(Context context) {
        KnowledgePack p = tried ? loaded : null;
        return p == null ? Collections.<LearnedTerms.Term>emptyList() : p.terms;
    }

    /**
     * İP8-B: indirilen paketi dener. İmzası, özeti, sürümü ve bankası tutarsa saklanır ve kullanılır; tutmazsa şimdiki
     * paketle devam edilir (bir öncekine dönüş). İP8-A'da çağıran yok ve açık anahtar yuvaları boş: hep reddeder.
     */
    static synchronized boolean tryApply(Context context, String json) {
        KnowledgePack current = pack(context);
        KnowledgePack candidate = KnowledgePack.read(json, KnowledgePack.PUBLIC_KEYS);
        List<String> bank = PackGate.sentences(raw(context, R.raw.kontrol_bankasi));
        KnowledgePack chosen = PackGate.choose(current, candidate, bank);
        if (chosen == null || chosen == current) {
            return false;
        }
        ContentValues v = new ContentValues();
        v.put("id", 1);
        v.put("json", json);
        get(context).getWritableDatabase().insertWithOnConflict("applied", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        loaded = chosen;
        return true;
    }

    /** Çiftçinin kapattığı paket sözü mü (her seferinde dosyadan: kapatınca hemen etkili). */
    static boolean disabled(Context context, String owner, String root) {
        try (Cursor c = get(context).getReadableDatabase().query("disabled", new String[]{"root"}, "owner=? AND root=?",
                new String[]{owner == null ? "" : owner, root}, null, null, null)) {
            return c.moveToFirst();
        } catch (RuntimeException e) {
            return false;
        }
    }

    static void setDisabled(Context context, String owner, String root, boolean off) {
        try {
            SQLiteDatabase db = get(context).getWritableDatabase();
            String o = owner == null ? "" : owner;
            if (off) {
                ContentValues v = new ContentValues();
                v.put("owner", o);
                v.put("root", root);
                db.insertWithOnConflict("disabled", null, v, SQLiteDatabase.CONFLICT_IGNORE);
            } else {
                db.delete("disabled", "owner=? AND root=?", new String[]{o, root});
            }
        } catch (RuntimeException ignored) {
            // yazılamazsa söz açık kalır
        }
    }

    /** Asistanın sözlüğü: önce öğretilenler, sonra paketin açık sözleri. */
    static PackedStore store(Context context, String owner) {
        Context app = context.getApplicationContext();
        String o = owner == null ? "" : owner;
        return new PackedStore(LearnedTermsDb.store(app, o), () -> terms(app), root -> disabled(app, o, root));
    }

    /** Hesap silinince: kapattığı paket sözleri. */
    static void clear(Context context, String owner) {
        try {
            get(context).getWritableDatabase().delete("disabled", "owner=?", new String[]{owner == null ? "" : owner});
        } catch (RuntimeException ignored) {
            // silinemezse sessiz
        }
    }

    private static String appliedJson(Context context) {
        try (Cursor c = get(context).getReadableDatabase().query("applied", new String[]{"json"}, "id=1", null, null, null, null)) {
            return c.moveToFirst() ? c.getString(0) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String raw(Context context, int id) {
        try (InputStream in = context.getResources().openRawResource(id)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

}
