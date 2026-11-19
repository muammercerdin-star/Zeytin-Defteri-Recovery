package com.zeytindefteri.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Çiftçinin "Bu yanlış" düzeltmeleri ve kapanan öneri türleri (İP5). Yalnız bu telefonda, ayrı dosyada
 * (zd_habits.db): Google yedeğine ve telefon aktarımına girmez (push_backup_rules, push_data_extraction_rules),
 * Yedekle dosyasına girmez (TezgahBackup tablolarında yok), hesap silinince silinir (AccountDeletion).
 * Sezilen alışkanlıkların kendisi burada değil: onlar her açılışta kayıtlardan yeniden hesaplanır (HabitModel).
 * Hata olursa sessiz: düzeltme yazılamazsa öneri bir sonraki açılışta yine gelir, uygulama bozulmaz.
 */
final class HabitDb extends SQLiteOpenHelper {
    static final String FILE = "zd_habits.db";
    private static final int VERSION = 1;
    private static HabitDb instance;

    private HabitDb(Context context) {
        super(context.getApplicationContext(), FILE, null, VERSION);
    }

    static synchronized HabitDb get(Context context) {
        if (instance == null) {
            instance = new HabitDb(context);
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS rejections (owner TEXT NOT NULL, what TEXT NOT NULL, value TEXT NOT NULL, "
                + "at INTEGER NOT NULL, PRIMARY KEY (owner, what, value))");
        db.execSQL("CREATE TABLE IF NOT EXISTS streaks (owner TEXT NOT NULL, category TEXT NOT NULL, "
                + "rejects INTEGER NOT NULL DEFAULT 0, closed INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (owner, category))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onCreate(db);
    }

    /** Bu hesabın düzeltmeleri. */
    static HabitCorrections.Store store(Context context, String owner) {
        return new OwnerStore(get(context), owner == null ? "" : owner);
    }

    /** Hesabın bütün düzeltmelerini siler (hesap silinince). */
    static void clear(Context context, String owner) {
        try {
            SQLiteDatabase db = get(context).getWritableDatabase();
            String[] args = {owner == null ? "" : owner};
            db.delete("rejections", "owner=?", args);
            db.delete("streaks", "owner=?", args);
        } catch (RuntimeException ignored) {
            // silinemezse sessiz
        }
    }

    private static final class OwnerStore implements HabitCorrections.Store {
        private final HabitDb helper;
        private final String owner;

        OwnerStore(HabitDb helper, String owner) {
            this.helper = helper;
            this.owner = owner;
        }

        @Override
        public long rejectedAt(String what, String value) {
            String[] args = {owner, what, value};
            try (Cursor c = helper.getReadableDatabase().query("rejections", new String[]{"at"},
                    "owner=? AND what=? AND value=?", args, null, null, null)) {
                return c.moveToFirst() ? c.getLong(0) : 0;
            } catch (RuntimeException e) {
                return 0;
            }
        }

        @Override
        public int streak(String category) {
            return (int) streakColumn(category, "rejects");
        }

        @Override
        public boolean closed(String category) {
            return streakColumn(category, "closed") > 0;
        }

        private long streakColumn(String category, String column) {
            String[] args = {owner, category};
            try (Cursor c = helper.getReadableDatabase().query("streaks", new String[]{column},
                    "owner=? AND category=?", args, null, null, null)) {
                return c.moveToFirst() ? c.getLong(0) : 0;
            } catch (RuntimeException e) {
                return 0;
            }
        }

        @Override
        public void reject(String what, String value, long now) {
            try {
                SQLiteDatabase db = helper.getWritableDatabase();
                ContentValues v = new ContentValues();
                v.put("owner", owner);
                v.put("what", what);
                v.put("value", value);
                v.put("at", now);
                db.insertWithOnConflict("rejections", null, v, SQLiteDatabase.CONFLICT_REPLACE);
                String category = HabitCorrections.category(what);
                int next = streak(category) + 1;
                ContentValues s = new ContentValues();
                s.put("owner", owner);
                s.put("category", category);
                s.put("rejects", next);
                s.put("closed", next >= HabitRules.CLOSE_AFTER || closed(category) ? 1 : 0);
                db.insertWithOnConflict("streaks", null, s, SQLiteDatabase.CONFLICT_REPLACE);
            } catch (RuntimeException ignored) {
                // yazılamadı
            }
        }

        @Override
        public void accept(String what) {
            try {
                String category = HabitCorrections.category(what);
                ContentValues s = new ContentValues();
                s.put("owner", owner);
                s.put("category", category);
                s.put("rejects", 0);
                s.put("closed", closed(category) ? 1 : 0);
                helper.getWritableDatabase().insertWithOnConflict("streaks", null, s, SQLiteDatabase.CONFLICT_REPLACE);
            } catch (RuntimeException ignored) {
                // yazılamadı
            }
        }
    }
}
