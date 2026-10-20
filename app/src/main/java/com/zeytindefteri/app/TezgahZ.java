package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * Gün sonu (Z raporu), POS cihazlarındaki gibi.
 *
 * "Gün sonu al" denince açık dönemin bütün alımları sıradaki Z numarasına bağlanır ve kilitlenir
 * (silinemez; yalnız sonradan borç ödemesi işlenir). Akşam hesabı (teslim kiloları, komisyon, avans)
 * Z kaydına kopyalanır; tezgah sıfırdan yeni döneme başlar. Z numarası hesap başına 1, 2, 3… artar.
 * Kayıtlar yalnız bu telefonda (tezgah_z); hesap silinince LocalAccountWipe temizler.
 */
public final class TezgahZ {
    static final String TABLE = "tezgah_z";
    /** Açık dönemin akşam hesabı tezgah_closings'te bu anahtarla tutulur. */
    public static final String OPEN_KEY = "open";

    public static final class Record {
        public long id, closedAt, firstAt, lastAt;
        public int number, count, kasa;
        public double kg, gross, paid, delivered, rate, advance;
        public String closing = "";

        public String label() { return "Z-" + pad(number); }
    }

    private TezgahZ() {}

    public static void ensure(SQLiteDatabase db) {
        TezgahPurchases.ensure(db);
        TezgahClosing.ensure(db);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "owner_uid TEXT DEFAULT '', "
                + "z_no INTEGER NOT NULL, "
                + "closed_at INTEGER DEFAULT 0, "
                + "first_at INTEGER DEFAULT 0, "
                + "last_at INTEGER DEFAULT 0, "
                + "purchase_count INTEGER DEFAULT 0, "
                + "total_kasa INTEGER DEFAULT 0, "
                + "total_kg REAL DEFAULT 0, "
                + "gross_total REAL DEFAULT 0, "
                + "amount_paid REAL DEFAULT 0, "
                + "delivered_kg REAL DEFAULT 0, "
                + "rate REAL DEFAULT 0, "
                + "advance REAL DEFAULT 0, "
                + "closing TEXT DEFAULT '', "
                + "UNIQUE(owner_uid, z_no))");
    }

    /**
     * Açık dönemi kapatır. closing: ekrandaki akşam hesabı (null ise teslim = alınan, komisyon/avans 0).
     * Dönüş: yeni Z kaydının id'si; açık alım yoksa ya da hata olursa -1. Tek işlemde: yarıda kalırsa hiçbir şey değişmez.
     */
    public static long close(SQLiteDatabase db, String owner, TezgahClosing closing, long now) {
        ensure(db);
        String o = owner == null ? "" : owner;
        List<TezgahPurchases.Purchase> open = TezgahPurchases.open(db, o);
        if (open.isEmpty()) return -1;
        TezgahClosing c = closing != null ? closing : TezgahClosing.fromPurchases(open);
        TezgahPurchases.Day d = TezgahPurchases.sum(open);
        long first = Long.MAX_VALUE, last = 0;
        for (TezgahPurchases.Purchase p : open) { first = Math.min(first, p.createdAt); last = Math.max(last, p.createdAt); }

        db.beginTransaction();
        try {
            int number = nextNumber(db, o);
            ContentValues v = new ContentValues();
            v.put("owner_uid", o);
            v.put("z_no", number);
            v.put("closed_at", now);
            v.put("first_at", first);
            v.put("last_at", last);
            v.put("purchase_count", d.count);
            v.put("total_kasa", d.kasa);
            v.put("total_kg", Math.round(d.kg * 10) / 10.0);
            v.put("gross_total", Math.round(d.gross * 100) / 100.0);
            v.put("amount_paid", Math.round(d.paid * 100) / 100.0);
            v.put("delivered_kg", c.deliveredKg());
            v.put("rate", c.rate);
            v.put("advance", c.advance);
            v.put("closing", encodeClosing(c));
            long id = db.insert(TABLE, null, v);
            if (id <= 0) return -1;
            ContentValues z = new ContentValues();
            z.put("z_id", id);
            int moved = db.update(TezgahPurchases.TABLE, z, "COALESCE(owner_uid,'')=? AND COALESCE(z_id,0)=0", new String[]{o});
            if (moved != open.size()) return -1;
            db.delete(TezgahClosing.TABLE, "COALESCE(owner_uid,'')=? AND record_date=?", new String[]{o, OPEN_KEY});
            db.setTransactionSuccessful();
            return id;
        } catch (RuntimeException e) {
            return -1;
        } finally {
            db.endTransaction();
        }
    }

    static int nextNumber(SQLiteDatabase db, String owner) {
        Cursor c = db.rawQuery("SELECT COALESCE(MAX(z_no),0) FROM " + TABLE + " WHERE COALESCE(owner_uid,'')=?", new String[]{owner});
        try { return (c.moveToFirst() ? c.getInt(0) : 0) + 1; } finally { c.close(); }
    }

    /** Gün sonları (yeniden eskiye). */
    public static List<Record> list(SQLiteDatabase db, String owner, int limit) {
        ensure(db);
        return query(db, "WHERE COALESCE(owner_uid,'')=? ORDER BY z_no DESC LIMIT " + Math.max(1, limit),
                new String[]{owner == null ? "" : owner});
    }

    public static Record get(SQLiteDatabase db, String owner, long id) {
        ensure(db);
        List<Record> r = query(db, "WHERE COALESCE(owner_uid,'')=? AND id=?", new String[]{owner == null ? "" : owner, String.valueOf(id)});
        return r.isEmpty() ? null : r.get(0);
    }

    private static List<Record> query(SQLiteDatabase db, String where, String[] args) {
        List<Record> out = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT id, z_no, closed_at, first_at, last_at, purchase_count, total_kasa, total_kg, gross_total, "
                + "amount_paid, delivered_kg, rate, advance, closing FROM " + TABLE + " " + where, args);
        try {
            while (c.moveToNext()) {
                Record r = new Record();
                r.id = c.getLong(0); r.number = c.getInt(1); r.closedAt = c.getLong(2); r.firstAt = c.getLong(3);
                r.lastAt = c.getLong(4); r.count = c.getInt(5); r.kasa = c.getInt(6); r.kg = c.getDouble(7);
                r.gross = c.getDouble(8); r.paid = c.getDouble(9); r.delivered = c.getDouble(10); r.rate = c.getDouble(11);
                r.advance = c.getDouble(12); r.closing = c.getString(13) == null ? "" : c.getString(13);
                out.add(r);
            }
        } finally {
            c.close();
        }
        return out;
    }

    /** Z'nin akşam hesabı: o Z'nin alımlarından + kaydedilen teslim/komisyon/avans. */
    public static TezgahClosing closingOf(Record r, List<TezgahPurchases.Purchase> list) {
        TezgahClosing c = TezgahClosing.fromPurchases(list);
        decodeClosing(r.closing, c);
        return c;
    }

    /** "v2|d0;d1;…|oran|avans" (teslimde boş = alınanla aynı). */
    static String encodeClosing(TezgahClosing c) {
        StringBuilder b = new StringBuilder("v2|");
        for (int g = 0; g < TezgahClosing.N; g++) { if (g > 0) b.append(';'); if (c.edited[g]) b.append(c.delivered[g]); }
        return b.append('|').append(c.rate).append('|').append(c.advance).toString();
    }

    static void decodeClosing(String raw, TezgahClosing c) {
        if (raw == null || !raw.startsWith("v2|")) return;
        String[] f = raw.substring(3).split("\\|", -1);
        if (f.length != 3) return;
        String[] d = f[0].split(";", -1);
        if (d.length == TezgahClosing.N) {
            for (int g = 0; g < TezgahClosing.N; g++) {
                if (d[g].isEmpty()) continue;
                try { c.setDelivered(g, Double.parseDouble(d[g])); } catch (NumberFormatException ignored) { }
            }
        }
        try { c.rate = Math.max(0, Double.parseDouble(f[1])); } catch (NumberFormatException ignored) { }
        try { c.advance = Math.max(0, Double.parseDouble(f[2])); } catch (NumberFormatException ignored) { }
    }

    static String pad(int n) {
        String s = String.valueOf(n);
        while (s.length() < 4) s = "0" + s;
        return s;
    }
}
