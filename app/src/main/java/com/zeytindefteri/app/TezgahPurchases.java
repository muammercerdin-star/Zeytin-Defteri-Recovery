package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * Tüccarın tezgahta yaptığı alımlar ("Alıyorum" rolü).
 *
 * - Yalnız bu telefonda tutulur; buluta, Zeytin Ligi'ne ve piyasa fiyatlarına KARIŞMAZ
 *   (üreticinin kendi satışlarıyla çift sayılmasın diye).
 * - Tablo ilk kullanımda oluşturulur (CREATE IF NOT EXISTS); veritabanı sürümü değişmez.
 * - owner_uid sütunu olduğu için hesap silinince LocalAccountWipe bu satırları da otomatik siler.
 * - Numara satırları TezgahModel.encode() metni olarak saklanır; fiş yeniden üretilebilir.
 */
public final class TezgahPurchases {
    static final String TABLE = "tezgah_purchases";

    public static final class Purchase {
        public long id;
        public String date = "", seller = "", phone = "", data = "";
        public double kg, gross, paid;
        public int kasa;
        public long createdAt;
        /** Gün sonu (Z) kaydı; 0 = açık dönem. Z'si olan alım kilitlidir (silinmez). */
        public long zId;

        public double remaining() { return Math.max(0, gross - paid); }
    }

    /** Gün özeti: alım sayısı, net kilo, kasa, tutar, ödenen. */
    public static final class Day {
        public int count, kasa;
        public double kg, gross, paid;

        public double remaining() { return Math.max(0, gross - paid); }
    }

    private TezgahPurchases() {}

    public static void ensure(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "owner_uid TEXT DEFAULT '', "
                + "record_date TEXT NOT NULL, "
                + "seller_name TEXT DEFAULT '', "
                + "seller_phone TEXT DEFAULT '', "
                + "total_kg REAL DEFAULT 0, "
                + "total_kasa INTEGER DEFAULT 0, "
                + "gross_total REAL DEFAULT 0, "
                + "amount_paid REAL DEFAULT 0, "
                + "data TEXT DEFAULT '', "
                + "created_at INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tezgah_owner_date ON " + TABLE + "(owner_uid, record_date)");
        if (!hasColumn(db, TABLE, "z_id")) db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN z_id INTEGER DEFAULT 0");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tezgah_owner_z ON " + TABLE + "(owner_uid, z_id)");
    }

    static boolean hasColumn(SQLiteDatabase db, String table, String column) {
        Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        try {
            while (c.moveToNext()) if (column.equals(c.getString(1))) return true;
            return false;
        } finally {
            c.close();
        }
    }

    /** Alımı kaydeder; id ya da hata -1. date: yyyy-MM-dd. */
    public static long add(SQLiteDatabase db, String owner, String date, String seller, String phone,
                           TezgahModel model, double paid, long now) {
        if (model == null || model.totalNet() <= 0 || date == null || date.trim().isEmpty()) return -1;
        ensure(db);
        double gross = model.total();
        ContentValues v = new ContentValues();
        v.put("owner_uid", owner == null ? "" : owner);
        v.put("record_date", date.trim());
        v.put("seller_name", seller == null ? "" : seller.trim());
        v.put("seller_phone", phone == null ? "" : phone.trim());
        v.put("total_kg", model.totalNet());
        v.put("total_kasa", model.totalKasa());
        v.put("gross_total", gross);
        v.put("amount_paid", Math.max(0, Math.min(gross, paid)));
        v.put("data", model.encode());
        v.put("created_at", now);
        return db.insert(TABLE, null, v);
    }

    private static final String COLS = "SELECT id, record_date, seller_name, seller_phone, total_kg, total_kasa, gross_total, "
            + "amount_paid, data, created_at, COALESCE(z_id,0) FROM " + TABLE;

    /** Günün alımları (yeniden eskiye), açık ya da kapanmış. */
    public static List<Purchase> forDay(SQLiteDatabase db, String owner, String date) {
        ensure(db);
        return query(db, COLS + " WHERE COALESCE(owner_uid,'')=? AND record_date=? ORDER BY created_at DESC, id DESC",
                new String[]{owner == null ? "" : owner, date});
    }

    /** Açık dönemin alımları: henüz gün sonu alınmamış (tarih fark etmez), yeniden eskiye. */
    public static List<Purchase> open(SQLiteDatabase db, String owner) {
        ensure(db);
        return query(db, COLS + " WHERE COALESCE(owner_uid,'')=? AND COALESCE(z_id,0)=0 ORDER BY created_at DESC, id DESC",
                new String[]{owner == null ? "" : owner});
    }

    /** Bütün alımlar (açık dönem + tüm Z'ler), yeniden eskiye; en çok limit kadar. Arama bunu kullanır. */
    public static List<Purchase> all(SQLiteDatabase db, String owner, int limit) {
        ensure(db);
        return query(db, COLS + " WHERE COALESCE(owner_uid,'')=? ORDER BY created_at DESC, id DESC LIMIT " + Math.max(1, limit),
                new String[]{owner == null ? "" : owner});
    }

    /** Bir gün sonu (Z) raporunun alımları. */
    public static List<Purchase> forZ(SQLiteDatabase db, String owner, long zId) {
        ensure(db);
        return query(db, COLS + " WHERE COALESCE(owner_uid,'')=? AND z_id=? ORDER BY created_at DESC, id DESC",
                new String[]{owner == null ? "" : owner, String.valueOf(zId)});
    }

    private static List<Purchase> query(SQLiteDatabase db, String sql, String[] args) {
        List<Purchase> out = new ArrayList<>();
        Cursor c = db.rawQuery(sql, args);
        try {
            while (c.moveToNext()) {
                Purchase p = new Purchase();
                p.id = c.getLong(0);
                p.date = c.getString(1);
                p.seller = nz(c.getString(2));
                p.phone = nz(c.getString(3));
                p.kg = c.getDouble(4);
                p.kasa = c.getInt(5);
                p.gross = c.getDouble(6);
                p.paid = c.getDouble(7);
                p.data = nz(c.getString(8));
                p.createdAt = c.getLong(9);
                p.zId = c.getLong(10);
                out.add(p);
            }
        } finally {
            c.close();
        }
        return out;
    }

    /** Açık dönem özeti. */
    public static Day openSummary(SQLiteDatabase db, String owner) { return sum(open(db, owner)); }

    public static Day sum(List<Purchase> list) {
        Day d = new Day();
        for (Purchase p : list) {
            d.count++;
            d.kasa += p.kasa;
            d.kg += p.kg;
            d.gross += p.gross;
            d.paid += p.paid;
        }
        return d;
    }

    public static Day summary(SQLiteDatabase db, String owner, String date) {
        return sum(forDay(db, owner, date));
    }

    /** Sonradan yapılan ödeme: ödenen tutar toplamı aşmaz. */
    public static boolean setPaid(SQLiteDatabase db, String owner, long id, double paid) {
        ensure(db);
        Cursor c = db.rawQuery("SELECT gross_total FROM " + TABLE + " WHERE id=? AND COALESCE(owner_uid,'')=?",
                new String[]{String.valueOf(id), owner == null ? "" : owner});
        double gross;
        try {
            if (!c.moveToFirst()) return false;
            gross = c.getDouble(0);
        } finally {
            c.close();
        }
        ContentValues v = new ContentValues();
        v.put("amount_paid", Math.max(0, Math.min(gross, paid)));
        return db.update(TABLE, v, "id=? AND COALESCE(owner_uid,'')=?",
                new String[]{String.valueOf(id), owner == null ? "" : owner}) > 0;
    }

    /** Yalnız açık dönemdeki alım silinir; gün sonu alınmış (Z'li) alım kilitlidir. */
    public static boolean delete(SQLiteDatabase db, String owner, long id) {
        ensure(db);
        return db.delete(TABLE, "id=? AND COALESCE(owner_uid,'')=? AND COALESCE(z_id,0)=0",
                new String[]{String.valueOf(id), owner == null ? "" : owner}) > 0;
    }

    /** Gün özeti satırı: "3 alım · 1.240 kg · 98.500 ₺ · 12.000 ₺ borç". */
    public static String dayLine(Day d) {
        if (d == null || d.count == 0) return "Henüz alım yok";
        String s = d.count + " alım · " + TezgahModel.kg(d.kg) + " · " + TezgahModel.tl(d.gross);
        if (d.remaining() > 0.5) s += " · " + TezgahModel.tl(d.remaining()) + " borç";
        return s;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
