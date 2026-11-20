package com.zeytindefteri.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Ortak döngü (İP8) yalnız telefonda: gönderim kuyruğu, "Uygulamanın gelişmesine anonim katkı" izni (varsayılan
 * KAPALI) ve takma adın türetildiği gizli anahtar. Ayrı dosya (zd_community.db): Google yedeğine ve telefon
 * aktarımına girmez (push_backup_rules, push_data_extraction_rules), Yedekle dosyasına girmez, hesap silinince
 * silinir (AccountDeletion). Gizli anahtar hiçbir yere gönderilmez; uygulama silinince kaybolur (yeni anahtar = yeni kişi).
 */
final class CommunityDb extends SQLiteOpenHelper {
    static final String FILE = "zd_community.db";
    private static final int VERSION = 1;
    private static CommunityDb instance;

    private CommunityDb(Context context) {
        super(context.getApplicationContext(), FILE, null, VERSION);
    }

    static synchronized CommunityDb get(Context context) {
        if (instance == null) {
            instance = new CommunityDb(context);
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS queue (owner TEXT NOT NULL, key TEXT NOT NULL, root TEXT NOT NULL, "
                + "word TEXT NOT NULL, action TEXT NOT NULL, type TEXT NOT NULL, note TEXT NOT NULL, province TEXT NOT NULL, "
                + "version TEXT NOT NULL, state TEXT NOT NULL, token TEXT NOT NULL, PRIMARY KEY (owner, key))");
        db.execSQL("CREATE TABLE IF NOT EXISTS consent (owner TEXT PRIMARY KEY, enabled INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS device (id INTEGER PRIMARY KEY CHECK (id = 1), secret TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onCreate(db);
    }

    /** İzin: varsayılan KAPALI. */
    static boolean consent(Context context, String owner) {
        try (Cursor c = get(context).getReadableDatabase().query("consent", new String[]{"enabled"}, "owner=?",
                new String[]{owner == null ? "" : owner}, null, null, null)) {
            return c.moveToFirst() && c.getInt(0) == 1;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static void setConsent(Context context, String owner, boolean on) {
        try {
            ContentValues v = new ContentValues();
            v.put("owner", owner == null ? "" : owner);
            v.put("enabled", on ? 1 : 0);
            get(context).getWritableDatabase().insertWithOnConflict("consent", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (RuntimeException ignored) {
            // yazılamazsa izin kapalı kalır
        }
    }

    /** Telefondaki gizli anahtar (32 bayt); yoksa üretilir. Dışarı çıkmaz. */
    static byte[] secret(Context context) {
        SQLiteDatabase db = get(context).getWritableDatabase();
        try (Cursor c = db.query("device", new String[]{"secret"}, "id=1", null, null, null, null)) {
            if (c.moveToFirst()) {
                return fromHex(c.getString(0));
            }
        }
        byte[] s = new byte[32];
        new SecureRandom().nextBytes(s);
        ContentValues v = new ContentValues();
        v.put("id", 1);
        v.put("secret", CommunityQueue.hex(s));
        db.insertWithOnConflict("device", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        return s;
    }

    /** Bu hesabın kuyruğu. */
    static CommunityQueue.Store store(Context context, String owner) {
        return new OwnerStore(get(context), owner == null ? "" : owner);
    }

    /** Hesap silinince: kuyruk ve izin. (Gizli anahtar telefona aittir, kişiye değil; uygulamayla silinir.) */
    static void clear(Context context, String owner) {
        try {
            String[] args = {owner == null ? "" : owner};
            SQLiteDatabase db = get(context).getWritableDatabase();
            db.delete("queue", "owner=?", args);
            db.delete("consent", "owner=?", args);
        } catch (RuntimeException ignored) {
            // silinemezse sessiz
        }
    }

    private static byte[] fromHex(String h) {
        byte[] b = new byte[h.length() / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        }
        return b;
    }

    private static final class OwnerStore implements CommunityQueue.Store {
        private final CommunityDb helper;
        private final String owner;

        OwnerStore(CommunityDb helper, String owner) {
            this.helper = helper;
            this.owner = owner;
        }

        @Override
        public List<CommunityQueue.Item> all() {
            List<CommunityQueue.Item> out = new ArrayList<>();
            String[] cols = {"root", "word", "action", "type", "note", "province", "version", "state", "token"};
            try (Cursor c = helper.getReadableDatabase().query("queue", cols, "owner=?", new String[]{owner}, null, null, null)) {
                while (c.moveToNext()) {
                    ContributionCleaner.Contribution k = new ContributionCleaner.Contribution(c.getString(0), c.getString(1),
                            c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getString(6));
                    out.add(new CommunityQueue.Item(k, CommunityQueue.State.valueOf(c.getString(7)), c.getString(8)));
                }
            } catch (RuntimeException e) {
                out.clear();
            }
            return out;
        }

        @Override
        public void put(CommunityQueue.Item i) {
            try {
                ContentValues v = new ContentValues();
                v.put("owner", owner);
                v.put("key", i.c.key());
                v.put("root", i.c.root);
                v.put("word", i.c.word);
                v.put("action", i.c.action);
                v.put("type", i.c.type);
                v.put("note", i.c.note);
                v.put("province", i.c.province);
                v.put("version", i.c.version);
                v.put("state", i.state.name());
                v.put("token", i.token);
                helper.getWritableDatabase().insertWithOnConflict("queue", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            } catch (RuntimeException ignored) {
                // yazılamazsa katkı bekletilmez
            }
        }

        @Override
        public void remove(String key) {
            try {
                helper.getWritableDatabase().delete("queue", "owner=? AND key=?", new String[]{owner, key});
            } catch (RuntimeException ignored) {
                // silinemezse sessiz
            }
        }
    }
}
