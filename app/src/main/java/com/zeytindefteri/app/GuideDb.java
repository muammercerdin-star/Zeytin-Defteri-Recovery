package com.zeytindefteri.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * Yönlendiren asistanın sayıları (yalnız bu telefonda, ayrı dosyada: zd_guide.db): soru kartı durumları ("Bu yanlış",
 * "Sonra", kapalı türler), kısa yolun iş türü başına kaç kez gösterildiği ve karnedeki "Yönlendirme" sayıları.
 * Google yedeğine ve telefon aktarımına girmez (push_backup_rules, push_data_extraction_rules), Yedekle dosyasına
 * girmez, hesap silinince silinir (AccountDeletion). Hata olursa sessiz: sayı yazılamazsa uygulama bozulmaz.
 */
final class GuideDb extends SQLiteOpenHelper {
    static final String FILE = "zd_guide.db";
    private static final int VERSION = 1;
    private static GuideDb instance;

    private GuideDb(Context context) {
        super(context.getApplicationContext(), FILE, null, VERSION);
    }

    static synchronized GuideDb get(Context context) {
        if (instance == null) {
            instance = new GuideDb(context);
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS cards (owner TEXT NOT NULL, type TEXT NOT NULL, rejects INTEGER NOT NULL DEFAULT 0, "
                + "closed INTEGER NOT NULL DEFAULT 0, snoozed INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (owner, type))");
        db.execSQL("CREATE TABLE IF NOT EXISTS counts (owner TEXT NOT NULL, key TEXT NOT NULL, n INTEGER NOT NULL DEFAULT 0, "
                + "PRIMARY KEY (owner, key))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onCreate(db);
    }

    /** Kart türlerinin durumu. */
    static Map<String, GuideCards.State> states(Context context, String owner) {
        Map<String, GuideCards.State> out = new HashMap<>();
        try (Cursor c = get(context).getReadableDatabase().rawQuery(
                "SELECT type, rejects, closed, snoozed FROM cards WHERE owner=?", new String[]{owner(owner)})) {
            while (c.moveToNext()) {
                out.put(c.getString(0), new GuideCards.State(c.getInt(1), c.getInt(2) != 0, c.getInt(3)));
            }
        } catch (RuntimeException ignored) {
            out.clear();
        }
        return out;
    }

    static void save(Context context, String owner, String type, GuideCards.State s) {
        try {
            ContentValues v = new ContentValues();
            v.put("owner", owner(owner));
            v.put("type", type);
            v.put("rejects", s.rejects);
            v.put("closed", s.closed ? 1 : 0);
            v.put("snoozed", s.snoozedDay);
            get(context).getWritableDatabase().insertWithOnConflict("cards", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (RuntimeException ignored) {
            // yazılamazsa sessiz
        }
    }

    /** Sayaç (karne ve kısa yol): "kisa_yol:SULAMA", "kart_goster:odeme_gunu"... */
    static long count(Context context, String owner, String key) {
        try (Cursor c = get(context).getReadableDatabase().rawQuery(
                "SELECT n FROM counts WHERE owner=? AND key=?", new String[]{owner(owner), key})) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    static void add(Context context, String owner, String key) {
        try {
            SQLiteDatabase db = get(context).getWritableDatabase();
            db.execSQL("INSERT OR IGNORE INTO counts (owner, key, n) VALUES (?, ?, 0)", new Object[]{owner(owner), key});
            db.execSQL("UPDATE counts SET n = n + 1 WHERE owner=? AND key=?", new Object[]{owner(owner), key});
        } catch (RuntimeException ignored) {
            // yazılamazsa sessiz
        }
    }

    /** Karne için bütün sayaçlar. */
    static Map<String, Long> counts(Context context, String owner) {
        Map<String, Long> out = new HashMap<>();
        try (Cursor c = get(context).getReadableDatabase().rawQuery(
                "SELECT key, n FROM counts WHERE owner=?", new String[]{owner(owner)})) {
            while (c.moveToNext()) {
                out.put(c.getString(0), c.getLong(1));
            }
        } catch (RuntimeException ignored) {
            out.clear();
        }
        return out;
    }

    /** Hesabın bütün sayılarını siler (hesap silinince). */
    static void clear(Context context, String owner) {
        try {
            SQLiteDatabase db = get(context).getWritableDatabase();
            String[] args = {owner(owner)};
            db.delete("cards", "owner=?", args);
            db.delete("counts", "owner=?", args);
        } catch (RuntimeException ignored) {
            // silinemezse sessiz
        }
    }

    private static String owner(String owner) {
        return owner == null ? "" : owner;
    }
}
