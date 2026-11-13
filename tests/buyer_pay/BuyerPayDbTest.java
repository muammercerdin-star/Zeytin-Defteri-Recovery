package com.zeytindefteri.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.List;

/** Gerçek SQLite ile: alıcının açık satışları (bütün sezonlar), toplu ödemenin satışa ve alacağa işlenmesi. */
public class BuyerPayDbTest {
    static int n;
    static void ok(boolean c, String m) { if (!c) throw new AssertionError(m); n++; }
    static void eq(double a, double b, String m) { ok(Math.abs(a - b) < 1e-6, m + ": " + a + " != " + b); }

    public static void main(String[] a) {
        SQLiteDatabase db = new SQLiteDatabase(a[0], a[1]);
        db.execSQL("CREATE TABLE buyers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, owner_uid TEXT DEFAULT '')");
        db.execSQL("CREATE TABLE sales (id INTEGER PRIMARY KEY AUTOINCREMENT, garden_id INTEGER, buyer_id INTEGER, sale_date TEXT, "
                + "sale_kind TEXT DEFAULT 'SALE', total_kg REAL DEFAULT 0, gross_total REAL DEFAULT 0, transport_cost REAL DEFAULT 0, "
                + "deductions REAL DEFAULT 0, amount_received REAL DEFAULT 0, due_date TEXT, season_year INTEGER DEFAULT 0, "
                + "updated_at INTEGER DEFAULT 0, owner_uid TEXT DEFAULT '')");
        db.execSQL("CREATE TABLE receivables (id INTEGER PRIMARY KEY AUTOINCREMENT, party_name TEXT, related_sale_id INTEGER, "
                + "amount REAL DEFAULT 0, total_received REAL DEFAULT 0, due_date TEXT, status TEXT DEFAULT 'OPEN', "
                + "updated_at INTEGER DEFAULT 0, owner_uid TEXT DEFAULT '')");
        db.execSQL("INSERT INTO buyers(name,owner_uid) VALUES('Ali Kaya','u1')");
        db.execSQL("INSERT INTO buyers(name,owner_uid) VALUES('Hasan Bey','u1')");
        // Ali Kaya: geçen sezondan 1.000, bu sezon 2 satış (biri kısmi ödenmiş), biri tamamen ödenmiş
        sale(db, 1, "15.12.2025", 2025, 1000, 0);
        sale(db, 1, "2026-11-02", 2026, 3000, 500);
        sale(db, 1, "28.10.2026", 2026, 2000, 0);
        sale(db, 1, "2026-10-20", 2026, 1500, 1500);
        // Başka alıcı ve başka hesap karışmaz
        sale(db, 2, "2026-10-01", 2026, 9999, 0);
        db.execSQL("INSERT INTO sales(garden_id,buyer_id,sale_date,gross_total,season_year,owner_uid) VALUES(9,1,'2026-10-01',777,2026,'u2')");
        db.execSQL("INSERT INTO receivables(party_name,related_sale_id,amount,owner_uid) VALUES('x',6,777,'u2')");

        List<BuyerPayPlan.Open> open = BuyerDirectory.openSales(db, "u1", 1);
        ok(open.size() == 3, "3 açık satış (geçen sezon dahil, ödenmiş hariç): " + open.size());
        eq(BuyerPayPlan.total(open), 1000 + 2500 + 2000, "toplam borç");

        BuyerPayPlan.Result plan = BuyerPayPlan.plan(open, 3500);
        ok(plan.parts.get(0).saleId == 1 && plan.parts.get(1).saleId == 3, "en eski: geçen sezon, sonra 28 Ekim (gg.aa.yyyy de doğru sıralanır)");
        ok(SalePayments.recordPlan(db, "u1", plan, "TRANSFER", "2026-11-13", 9L), "toplu kayıt");
        eq(SalePayments.sale(db, "u1", 1).remaining(), 0, "geçen sezon kapandı");
        eq(SalePayments.sale(db, "u1", 3).remaining(), 0, "28 Ekim kapandı");
        eq(SalePayments.sale(db, "u1", 2).remaining(), 2000, "2 Kasım'a 500 işlendi");
        Cursor c = db.rawQuery("SELECT status, total_received FROM receivables WHERE related_sale_id=3", null);
        c.moveToFirst();
        ok("PAID".equals(c.getString(0)), "alacak da kapandı");
        eq(c.getDouble(1), 2000, "alacak tahsilatı");
        List<SalePayments.Payment> h = SalePayments.history(db, "u1", SalePayments.sale(db, "u1", 2));
        ok(h.get(h.size() - 1).method.equals("TRANSFER"), "ödeme geçmişine şekliyle yazıldı");
        eq(BuyerPayPlan.total(BuyerDirectory.openSales(db, "u1", 1)), 2000, "kalan borç");

        BuyerPayPlan.Result over = BuyerPayPlan.plan(BuyerDirectory.openSales(db, "u1", 1), 5000);
        ok(!SalePayments.recordPlan(db, "u1", over, "CASH", "2026-11-13", 10L), "borçtan fazlası kaydedilmez");
        ok(!SalePayments.recordPlan(db, "u1", BuyerPayPlan.plan(open, 0), "CASH", "2026-11-13", 10L), "boş ödeme kaydedilmez");
        eq(BuyerPayPlan.total(BuyerDirectory.openSales(db, "u1", 2)), 9999, "diğer alıcının borcu yerinde");
        ok(BuyerDirectory.openSales(db, "u2", 1).size() == 1, "başka hesap ayrı");
        System.out.println("PASS: " + n + " toplu tahsilat kontrolü");
    }

    static void sale(SQLiteDatabase db, int buyer, String date, int season, double gross, double received) {
        db.execSQL("INSERT INTO sales(garden_id,buyer_id,sale_date,gross_total,amount_received,season_year,owner_uid) "
                + "VALUES(3," + buyer + ",'" + date + "'," + gross + "," + received + "," + season + ",'u1')");
        db.execSQL("INSERT INTO receivables(party_name,related_sale_id,amount,total_received,status,owner_uid) "
                + "VALUES('a',(SELECT MAX(id) FROM sales)," + gross + "," + received + ",'"
                + (received >= gross ? "PAID" : "OPEN") + "','u1')");
    }
}
