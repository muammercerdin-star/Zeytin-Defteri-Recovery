package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * Tezgah alımlarında üreticiye sonradan yapılan ödemeler (borcun bir kısmı ya da tamamı).
 *
 * - Ödeme alımın amount_paid alanına eklenir (Alım defteri, Z ekranı, rapor aynı rakamı görür).
 * - Her ödeme tezgah_payments'ta tarihiyle tutulur; alım anında ödenen para geçmişte "Alımda ödenen" satırıdır.
 * - Gün sonu (Z) alınmış alıma da ödeme yapılabilir: tartımlar kilitli kalır, yalnız ödeme işlenir.
 * - Yalnız bu telefonda; owner_uid sütunu olduğu için hesap silinince LocalAccountWipe temizler; yedeğe girer.
 */
public final class TezgahPayments {
    static final String TABLE = "tezgah_payments";

    private TezgahPayments() {}

    public static final class Payment {
        public double amount;
        public String date = "";
        /** true: alım anında ödenen (geçmiş tablosunda olmayan) kısım. */
        public boolean atPurchase;
    }

    static void ensure(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + " (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "purchase_id INTEGER NOT NULL, amount REAL NOT NULL, paid_on TEXT, created_at INTEGER DEFAULT 0, "
                + "owner_uid TEXT DEFAULT '')");
    }

    /** Ödeme kaydeder; tutar kalan borçla sınırlanır. Kalan yoksa ya da tutar sıfırsa false. */
    public static boolean pay(SQLiteDatabase db, String owner, long purchaseId, double amount, String today, long now) {
        TezgahPurchases.ensure(db);
        ensure(db);
        String o = owner == null ? "" : owner;
        Cursor c = db.rawQuery("SELECT gross_total, amount_paid FROM " + TezgahPurchases.TABLE
                + " WHERE id=? AND COALESCE(owner_uid,'')=?", new String[]{String.valueOf(purchaseId), o});
        double gross, paid;
        try {
            if (!c.moveToFirst()) return false;
            gross = c.getDouble(0);
            paid = c.getDouble(1);
        } finally {
            c.close();
        }
        double left = Math.max(0, gross - paid);
        double v = Math.min(amount, left);
        if (amount <= 0.009 || v <= 0.009) return false;
        db.beginTransaction();
        try {
            ContentValues row = new ContentValues();
            row.put("purchase_id", purchaseId);
            row.put("amount", Math.round(v * 100) / 100.0);
            row.put("paid_on", today == null ? "" : today);
            row.put("created_at", now);
            row.put("owner_uid", o);
            if (db.insert(TABLE, null, row) == -1) return false;
            ContentValues u = new ContentValues();
            u.put("amount_paid", Math.min(gross, Math.round((paid + v) * 100) / 100.0));
            if (db.update(TezgahPurchases.TABLE, u, "id=? AND COALESCE(owner_uid,'')=?",
                    new String[]{String.valueOf(purchaseId), o}) <= 0) return false;
            db.setTransactionSuccessful();
            return true;
        } catch (RuntimeException e) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    /** Ödeme geçmişi (eskiden yeniye); alım anında ödenen kısım başta. Toplamı her zaman alımın ödenen tutarıdır. */
    public static List<Payment> history(SQLiteDatabase db, String owner, TezgahPurchases.Purchase p) {
        List<Payment> out = new ArrayList<>();
        if (p == null) return out;
        ensure(db);
        double sum = 0;
        Cursor c = db.rawQuery("SELECT amount, COALESCE(paid_on,'') FROM " + TABLE
                        + " WHERE purchase_id=? AND COALESCE(owner_uid,'')=? ORDER BY created_at, id",
                new String[]{String.valueOf(p.id), owner == null ? "" : owner});
        try {
            while (c.moveToNext()) {
                Payment x = new Payment();
                x.amount = c.getDouble(0);
                x.date = c.getString(1);
                sum += x.amount;
                out.add(x);
            }
        } finally {
            c.close();
        }
        double first = p.paid - sum;
        if (first > 0.009) {
            Payment x = new Payment();
            x.amount = first;
            x.date = p.date;
            x.atPurchase = true;
            out.add(0, x);
        }
        return out;
    }

    static void deleteFor(SQLiteDatabase db, String owner, long purchaseId) {
        ensure(db);
        db.delete(TABLE, "purchase_id=? AND COALESCE(owner_uid,'')=?",
                new String[]{String.valueOf(purchaseId), owner == null ? "" : owner});
    }
}
