package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * Bildirim kutusu: telefonun ürettiği uyarılar (hava, para, takvim, tezgah) tek listede.
 * - Aynı anahtar (AlertRules.Alert.key) ikinci kez eklenmez: aynı don uyarısı iki kez gelmez.
 * - Yalnız bu telefonda; owner_uid sütunu olduğu için hesap silinince LocalAccountWipe temizler.
 * - 60 günden eski kayıtlar temizlenir.
 */
public final class NotificationInbox {
    static final String TABLE = "zd_notifications";
    static final long KEEP_MS = 60L * 24 * 60 * 60 * 1000;

    public static final class Item {
        public long id, createdAt;
        public String kind = "", key = "", title = "", body = "";
        public int level;
        public boolean read;
    }

    private NotificationInbox() {}

    public static void ensure(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "owner_uid TEXT DEFAULT '', "
                + "kind TEXT DEFAULT '', "
                + "nkey TEXT NOT NULL, "
                + "level INTEGER DEFAULT 1, "
                + "title TEXT DEFAULT '', "
                + "body TEXT DEFAULT '', "
                + "created_at INTEGER DEFAULT 0, "
                + "is_read INTEGER DEFAULT 0, "
                + "UNIQUE(owner_uid, nkey))");
    }

    /** Yeni ise ekler ve id döner; aynı anahtar varsa -1 (telefon bildirimi gösterilmez). */
    public static long add(SQLiteDatabase db, String owner, AlertRules.Alert a, long now) {
        if (a == null) return -1;
        ensure(db);
        String o = owner == null ? "" : owner;
        Cursor c = db.rawQuery("SELECT 1 FROM " + TABLE + " WHERE COALESCE(owner_uid,'')=? AND nkey=?", new String[]{o, a.key});
        try { if (c.moveToFirst()) return -1; } finally { c.close(); }
        ContentValues v = new ContentValues();
        v.put("owner_uid", o);
        v.put("kind", a.kind);
        v.put("nkey", a.key);
        v.put("level", a.level);
        v.put("title", a.title);
        v.put("body", a.body);
        v.put("created_at", now);
        v.put("is_read", 0);
        return db.insert(TABLE, null, v);
    }

    public static List<Item> list(SQLiteDatabase db, String owner, int limit) {
        ensure(db);
        List<Item> out = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT id, kind, nkey, level, title, body, created_at, is_read FROM " + TABLE
                        + " WHERE COALESCE(owner_uid,'')=? ORDER BY created_at DESC, id DESC LIMIT " + Math.max(1, limit),
                new String[]{owner == null ? "" : owner});
        try {
            while (c.moveToNext()) {
                Item i = new Item();
                i.id = c.getLong(0); i.kind = c.getString(1); i.key = c.getString(2); i.level = c.getInt(3);
                i.title = c.getString(4); i.body = c.getString(5); i.createdAt = c.getLong(6); i.read = c.getInt(7) != 0;
                out.add(i);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public static int unread(SQLiteDatabase db, String owner) {
        ensure(db);
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE + " WHERE COALESCE(owner_uid,'')=? AND is_read=0",
                new String[]{owner == null ? "" : owner});
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    public static void markAllRead(SQLiteDatabase db, String owner) {
        ensure(db);
        ContentValues v = new ContentValues();
        v.put("is_read", 1);
        db.update(TABLE, v, "COALESCE(owner_uid,'')=? AND is_read=0", new String[]{owner == null ? "" : owner});
    }

    public static void delete(SQLiteDatabase db, String owner, long id) {
        ensure(db);
        db.delete(TABLE, "id=? AND COALESCE(owner_uid,'')=?", new String[]{String.valueOf(id), owner == null ? "" : owner});
    }

    /** Eski kayıtları siler (anahtarları da gider; 60 gün sonra aynı anahtar zaten oluşmaz: tarih içerir). */
    public static void prune(SQLiteDatabase db, long now) {
        ensure(db);
        db.delete(TABLE, "created_at<?", new String[]{String.valueOf(now - KEEP_MS)});
    }
}
