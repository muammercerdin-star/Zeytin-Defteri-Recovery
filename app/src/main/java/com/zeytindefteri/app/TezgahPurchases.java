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

    /** Günün alımları (yeniden eskiye). */
    public static List<Purchase> forDay(SQLiteDatabase db, String owner, String date) {
        ensure(db);
        List<Purchase> out = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT id, record_date, seller_name, seller_phone, total_kg, total_kasa, gross_total, "
                        + "amount_paid, data, created_at FROM " + TABLE
                        + " WHERE COALESCE(owner_uid,'')=? AND record_date=? ORDER BY created_at DESC, id DESC",
                new String[]{owner == null ? "" : owner, date});
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
                out.add(p);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public static Day summary(SQLiteDatabase db, String owner, String date) {
        Day d = new Day();
        for (Purchase p : forDay(db, owner, date)) {
            d.count++;
            d.kasa += p.kasa;
            d.kg += p.kg;
            d.gross += p.gross;
            d.paid += p.paid;
        }
        return d;
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

    public static boolean delete(SQLiteDatabase db, String owner, long id) {
        ensure(db);
        return db.delete(TABLE, "id=? AND COALESCE(owner_uid,'')=?",
                new String[]{String.valueOf(id), owner == null ? "" : owner}) > 0;
    }

    /** Gün özeti satırı: "3 alım · 1.240 kg · 98.500 ₺ · 12.000 ₺ borç". */
    public static String dayLine(Day d) {
        if (d == null || d.count == 0) return "Bugün henüz alım yok";
        String s = d.count + " alım · " + TezgahModel.kg(d.kg) + " · " + TezgahModel.tl(d.gross);
        if (d.remaining() > 0.5) s += " · " + TezgahModel.tl(d.remaining()) + " borç";
        return s;
    }

    /**
     * Excel'in açtığı CSV (Türkçe Excel için ";" ayraç, virgüllü ondalık, başta UTF-8 BOM).
     * Satır başına bir alım; numara kiloları ayrı sütunlarda; en altta TOPLAM satırı.
     */
    public static String csv(List<Purchase> list) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("Tarih;Saat;Üretici;Telefon;Kasa;");
        for (String g : TezgahModel.LABELS) sb.append(g).append(" kg;");
        sb.append("Net kg;Ort. TL/kg;Tutar TL;Ödenen TL;Kalan TL\r\n");
        java.text.SimpleDateFormat hm = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US);
        double[] gradeSum = new double[TezgahModel.GRADES.length];
        int kasa = 0;
        double kg = 0, gross = 0, paid = 0;
        for (Purchase p : list) {
            double[] g = TezgahModel.decode(p.data).netArray();
            sb.append(cell(p.date)).append(';').append(hm.format(new java.util.Date(p.createdAt))).append(';')
                    .append(cell(p.seller)).append(';').append(cell(p.phone)).append(';').append(p.kasa).append(';');
            for (int i = 0; i < g.length; i++) { sb.append(dec(g[i])).append(';'); gradeSum[i] += g[i]; }
            sb.append(dec(p.kg)).append(';').append(dec(p.kg > 0 ? p.gross / p.kg : 0)).append(';')
                    .append(dec(p.gross)).append(';').append(dec(p.paid)).append(';').append(dec(p.remaining())).append("\r\n");
            kasa += p.kasa; kg += p.kg; gross += p.gross; paid += p.paid;
        }
        sb.append("TOPLAM;;").append(list.size()).append(" alım;;").append(kasa).append(';');
        for (double v : gradeSum) sb.append(dec(v)).append(';');
        sb.append(dec(kg)).append(';').append(dec(kg > 0 ? gross / kg : 0)).append(';').append(dec(gross)).append(';')
                .append(dec(paid)).append(';').append(dec(Math.max(0, gross - paid))).append("\r\n");
        return sb.toString();
    }

    /** "1234,5" (binlik ayırıcısız, en fazla 2 ondalık; Excel sayı olarak okur). */
    static String dec(double v) {
        double r = Math.round(v * 100) / 100.0;
        if (r == Math.rint(r)) return String.valueOf((long) r);
        return String.valueOf(r).replace('.', ',');
    }

    private static String cell(String v) {
        String s = v == null ? "" : v.replace('\n', ' ').replace('\r', ' ');
        if (s.indexOf(';') >= 0 || s.indexOf('"') >= 0) s = "\"" + s.replace("\"", "\"\"") + "\"";
        // Excel formül enjeksiyonu: =, +, -, @ ile başlayan metin formül sayılmasın.
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return s;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
