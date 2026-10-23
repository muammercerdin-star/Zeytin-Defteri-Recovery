package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Satış tahsilatları: "Ödeme aldım" kaydı, ödeme geçmişi, söz verilen gün ve "Alacaklarım" özeti.
 *
 * - Tahsilat iki yere birden işlenir: sales.amount_received ve bağlı receivables.total_received/status
 *   (Alıcılarım, sezon raporu ve bildirimler aynı rakamı görür).
 * - Ödeme geçmişi sale_payments tablosunda, yalnız bu telefonda tutulur (CREATE IF NOT EXISTS,
 *   veritabanı sürümü değişmez). owner_uid sütunu olduğu için hesap silinince LocalAccountWipe siler.
 *   Toplamlar satış/alacak satırlarında olduğu için buluta yine gider.
 */
public final class SalePayments {
    static final String TABLE = "sale_payments";

    private SalePayments() {}

    public static final class Info {
        public long saleId, gardenId;
        public String buyer = "", saleDate = "", due = "";
        public double net, received;

        public double remaining() { return SalePayStatus.remaining(net, received); }
        public int kind(String today) { return SalePayStatus.kind(true, net, received, due, today); }
        public String label(String today) { return SalePayStatus.label(kind(today), net, received, due, today); }
    }

    public static final class Payment {
        public double amount;
        public String method = "", date = "";
    }

    public static final class Outstanding {
        public double total, overdueTotal;
        public int count, buyers, overdueCount;
    }

    static void ensure(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + " (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "sale_id INTEGER NOT NULL, amount REAL NOT NULL, method TEXT DEFAULT 'CASH', paid_on TEXT, "
                + "created_at INTEGER DEFAULT 0, owner_uid TEXT DEFAULT '')");
    }

    /** Satışın tahsilat bilgisi; satış bu hesabın değilse null. */
    public static Info sale(SQLiteDatabase db, String owner, long saleId) {
        if (saleId <= 0) return null;
        Cursor c = db.rawQuery("SELECT s.id, s.garden_id, COALESCE(b.name,''), COALESCE(s.sale_date,''), "
                        + "COALESCE(s.due_date,''), COALESCE(s.gross_total,0)-COALESCE(s.transport_cost,0)-COALESCE(s.deductions,0), "
                        + "COALESCE(s.amount_received,0) FROM sales s LEFT JOIN buyers b ON b.id=s.buyer_id "
                        + "WHERE s.id=? AND COALESCE(s.owner_uid,'')=?",
                new String[]{String.valueOf(saleId), safe(owner)});
        try {
            if (!c.moveToFirst()) return null;
            Info i = new Info();
            i.saleId = c.getLong(0);
            i.gardenId = c.getLong(1);
            i.buyer = nz(c.getString(2));
            i.saleDate = nz(c.getString(3));
            i.due = nz(c.getString(4));
            i.net = Math.max(0, c.getDouble(5));
            i.received = Math.max(0, c.getDouble(6));
            return i;
        } finally {
            c.close();
        }
    }

    /**
     * Ödeme geçmişi (eskiden yeniye). Satış oluşurken alınan para geçmiş tablosunda yoksa
     * başa "Satışta alınan" satırı eklenir; böylece geçmiş toplamı her zaman alınan tutara eşittir.
     */
    public static List<Payment> history(SQLiteDatabase db, String owner, Info info) {
        List<Payment> out = new ArrayList<>();
        if (info == null) return out;
        ensure(db);
        double sum = 0;
        Cursor c = db.rawQuery("SELECT amount, COALESCE(method,''), COALESCE(paid_on,'') FROM " + TABLE
                        + " WHERE sale_id=? AND COALESCE(owner_uid,'')=? ORDER BY created_at, id",
                new String[]{String.valueOf(info.saleId), safe(owner)});
        try {
            while (c.moveToNext()) {
                Payment p = new Payment();
                p.amount = c.getDouble(0);
                p.method = c.getString(1);
                p.date = c.getString(2);
                sum += p.amount;
                out.add(p);
            }
        } finally {
            c.close();
        }
        double first = info.received - sum;
        if (first > 0.009) {
            Payment p = new Payment();
            p.amount = first;
            p.method = "";
            p.date = info.saleDate;
            out.add(0, p);
        }
        return out;
    }

    /**
     * Ödeme kaydeder. Tutar kalanla sınırlanır; kalan yoksa ya da tutar sıfırsa false.
     * Dönüş true ise satış ve alacak güncellenmiştir.
     */
    public static boolean record(SQLiteDatabase db, String owner, long saleId, double amount, String method,
                                 String today, long now) {
        Info info = sale(db, owner, saleId);
        if (info == null || amount <= 0.009) return false;
        double pay = Math.min(amount, info.remaining());
        if (pay <= 0.009) return false;
        ensure(db);
        double received = Math.min(info.net, info.received + pay);
        db.beginTransaction();
        try {
            ContentValues row = new ContentValues();
            row.put("sale_id", saleId);
            row.put("amount", pay);
            row.put("method", known(method) ? method : SalePayStatus.METHODS[0]);
            row.put("paid_on", nz(today));
            row.put("created_at", now);
            row.put("owner_uid", safe(owner));
            if (db.insert(TABLE, null, row) == -1) return false;
            ContentValues s = new ContentValues();
            s.put("amount_received", received);
            s.put("updated_at", now);
            if (db.update("sales", s, "id=? AND COALESCE(owner_uid,'')=?",
                    new String[]{String.valueOf(saleId), safe(owner)}) <= 0) return false;
            db.execSQL("UPDATE receivables SET total_received=MIN(amount, COALESCE(total_received,0)+?), "
                            + "status=CASE WHEN COALESCE(total_received,0)+?>=amount-0.009 THEN 'PAID' ELSE 'OPEN' END, "
                            + "updated_at=? WHERE related_sale_id=? AND COALESCE(owner_uid,'')=?",
                    new Object[]{pay, pay, now, saleId, safe(owner)});
            db.setTransactionSuccessful();
            return true;
        } catch (RuntimeException e) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    /** Söz verilen günü satışa ve alacağına yazar ("" = gün yok; yyyy-MM-dd ya da dd.MM.yyyy kabul edilir). */
    public static boolean setDue(SQLiteDatabase db, String owner, long saleId, String due, long now) {
        if (saleId <= 0) return false;
        // Satış formuyla (SaleActivity) aynı biçimde saklanır: dd.MM.yyyy.
        String iso = SalePayStatus.iso(due);
        String d = SalePayStatus.valid(iso) ? iso.substring(8, 10) + "." + iso.substring(5, 7) + "." + iso.substring(0, 4) : "";
        ContentValues s = new ContentValues();
        s.put("due_date", d);
        s.put("updated_at", now);
        int n = db.update("sales", s, "id=? AND COALESCE(owner_uid,'')=?",
                new String[]{String.valueOf(saleId), safe(owner)});
        if (n <= 0) return false;
        ContentValues r = new ContentValues();
        r.put("due_date", d);
        r.put("updated_at", now);
        db.update("receivables", r, "related_sale_id=? AND COALESCE(owner_uid,'')=?",
                new String[]{String.valueOf(saleId), safe(owner)});
        return true;
    }

    /** Açık alacakların özeti (Alacaklarım kartı). */
    public static Outstanding outstanding(SQLiteDatabase db, String owner, String today) {
        Outstanding o = new Outstanding();
        Set<String> buyers = new HashSet<>();
        Cursor c = db.rawQuery("SELECT COALESCE(party_name,''), amount-COALESCE(total_received,0), COALESCE(due_date,'') "
                        + "FROM receivables WHERE COALESCE(owner_uid,'')=? AND COALESCE(status,'OPEN')<>'PAID' "
                        + "AND amount-COALESCE(total_received,0)>=1",
                new String[]{safe(owner)});
        try {
            while (c.moveToNext()) {
                double left = c.getDouble(1);
                o.total += left;
                o.count++;
                String who = nz(c.getString(0)).trim().toLowerCase(java.util.Locale.ROOT);
                buyers.add(who.isEmpty() ? "?" : who);
                if (SalePayStatus.overdueDays(c.getString(2), today) > 0) {
                    o.overdueCount++;
                    o.overdueTotal += left;
                }
            }
        } finally {
            c.close();
        }
        o.buyers = buyers.size();
        return o;
    }

    private static boolean known(String method) {
        for (String m : SalePayStatus.METHODS) if (m.equals(method)) return true;
        return false;
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static String nz(String s) { return s == null ? "" : s; }
}
