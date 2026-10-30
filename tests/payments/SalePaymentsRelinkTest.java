package com.zeytindefteri.app;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.List;
/** Ödeme geçmişi satışın kalıcı bulut kimliğine bağlı: başka telefona taşınınca doğru satışta kalır. */
public class SalePaymentsRelinkTest {
  static int n;
  static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  static String one(SQLiteDatabase db,String sql){ Cursor c=db.rawQuery(sql,null); try{ return c.moveToFirst()?c.getString(0):null; } finally{ c.close(); } }
  static void schema(SQLiteDatabase db){
    db.execSQL("CREATE TABLE buyers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, owner_uid TEXT DEFAULT '')");
    db.execSQL("CREATE TABLE sales (id INTEGER PRIMARY KEY AUTOINCREMENT, garden_id INTEGER, buyer_id INTEGER, sale_date TEXT, gross_total REAL DEFAULT 0, transport_cost REAL DEFAULT 0, deductions REAL DEFAULT 0, amount_received REAL DEFAULT 0, due_date TEXT, updated_at INTEGER DEFAULT 0, owner_uid TEXT DEFAULT '', cloud_id TEXT DEFAULT '')");
    db.execSQL("CREATE TABLE receivables (id INTEGER PRIMARY KEY AUTOINCREMENT, party_name TEXT, related_sale_id INTEGER, amount REAL DEFAULT 0, total_received REAL DEFAULT 0, due_date TEXT, status TEXT DEFAULT 'OPEN', updated_at INTEGER DEFAULT 0, owner_uid TEXT DEFAULT '')");
  }
  public static void main(String[] a){
    // ---- Telefon A: eski sürüm tablosu (sale_cloud_id yok) + 1 ödeme
    SQLiteDatabase A=new SQLiteDatabase(a[0],a[2]);
    schema(A);
    A.execSQL("INSERT INTO sales(garden_id,sale_date,gross_total,amount_received,owner_uid,cloud_id) VALUES(1,'2026-10-10',1000,300,'u1','c-ali')");
    A.execSQL("INSERT INTO sales(garden_id,sale_date,gross_total,amount_received,owner_uid,cloud_id) VALUES(1,'2026-10-11',2000,0,'u1','')");
    A.execSQL("CREATE TABLE sale_payments (id INTEGER PRIMARY KEY AUTOINCREMENT, sale_id INTEGER NOT NULL, amount REAL NOT NULL, method TEXT DEFAULT 'CASH', paid_on TEXT, created_at INTEGER DEFAULT 0, owner_uid TEXT DEFAULT '')");
    A.execSQL("INSERT INTO sale_payments(sale_id,amount,method,paid_on,created_at,owner_uid) VALUES(1,300,'CASH','2026-10-12',1,'u1')");
    SalePayments.ensure(A);
    ok("c-ali".equals(one(A,"SELECT sale_cloud_id FROM sale_payments WHERE id=1")),"sütun eklenince mevcut ödeme bu telefonun satışına bağlanır");
    // kimliği olmayan satışa ödeme: satışa kimlik verilir, ödeme ona bağlanır
    ok(SalePayments.record(A,"u1",2,500,"TRANSFER","2026-10-13",2L),"ödeme");
    String c2=one(A,"SELECT cloud_id FROM sales WHERE id=2");
    ok(c2!=null&&c2.length()==36,"satışa kalıcı kimlik verildi: "+c2);
    ok(c2.equals(one(A,"SELECT sale_cloud_id FROM sale_payments WHERE sale_id=2")),"ödeme kalıcı kimliği taşır");
    // misafir (oturumsuz) satışa kimlik verilmez
    A.execSQL("INSERT INTO sales(garden_id,sale_date,gross_total,owner_uid) VALUES(1,'2026-10-14',100,'')");
    ok(SalePayments.record(A,"",3,50,"CASH","2026-10-14",3L),"misafir ödeme");
    String g=one(A,"SELECT COALESCE(cloud_id,'') FROM sales WHERE id=3");
    ok(g==null||g.isEmpty(),"misafir satışa kimlik verilmez: "+g);

    // ---- Telefon B: satışlar buluttan TERS sırayla geldi (numaralar yer değiştirdi), ödemeler yedekten aynen
    SQLiteDatabase B=new SQLiteDatabase(a[1],a[2]);
    schema(B);
    B.execSQL("INSERT INTO sales(garden_id,sale_date,gross_total,amount_received,owner_uid,cloud_id) VALUES(1,'2026-10-11',2000,500,'u1','"+c2+"')");
    B.execSQL("INSERT INTO sales(garden_id,sale_date,gross_total,amount_received,owner_uid,cloud_id) VALUES(1,'2026-10-10',1000,300,'u1','c-ali')");
    SalePayments.ensure(B);
    B.execSQL("INSERT INTO sale_payments(sale_id,amount,method,paid_on,created_at,owner_uid,sale_cloud_id) VALUES(1,300,'CASH','2026-10-12',1,'u1','c-ali')");
    B.execSQL("INSERT INTO sale_payments(sale_id,amount,method,paid_on,created_at,owner_uid,sale_cloud_id) VALUES(2,500,'TRANSFER','2026-10-13',2,'u1','"+c2+"')");
    // eski yedekten gelen kimliksiz satır: olduğu gibi kalır
    B.execSQL("INSERT INTO sale_payments(sale_id,amount,method,paid_on,created_at,owner_uid,sale_cloud_id) VALUES(1,7,'CASH','2026-10-01',0,'u1','')");
    SalePayments.Info ali=SalePayments.sale(B,"u1",2);
    List<SalePayments.Payment> h=SalePayments.history(B,"u1",ali);
    boolean has300=false, has500=false;
    for(SalePayments.Payment p:h){ if(Math.abs(p.amount-300)<1e-6&&"CASH".equals(p.method)) has300=true; if(Math.abs(p.amount-500)<1e-6) has500=true; }
    ok(has300&&!has500,"Ali'nin satışında kendi 300 TL'si var, başkasının 500 TL'si yok");
    ok("1".equals(one(B,"SELECT sale_id FROM sale_payments WHERE amount=500")),"500 TL kendi satışına (yeni numara 1) taşındı");
    ok("1".equals(one(B,"SELECT sale_id FROM sale_payments WHERE amount=7")),"kimliksiz eski satır değişmez");
    ok(SalePayments.relink(B,"u1")==0,"ikinci kez çalıştırmak bir şey değiştirmez");
    // başka hesabın satışına bağlanmaz
    B.execSQL("INSERT INTO sales(garden_id,sale_date,gross_total,owner_uid,cloud_id) VALUES(9,'2026-10-10',1,'u2','c-x')");
    B.execSQL("INSERT INTO sale_payments(sale_id,amount,method,paid_on,created_at,owner_uid,sale_cloud_id) VALUES(2,9,'CASH','2026-10-01',0,'u1','c-x')");
    SalePayments.relink(B,"u1");
    ok("2".equals(one(B,"SELECT sale_id FROM sale_payments WHERE amount=9")),"başka hesabın satışına bağlanmaz");
    System.out.println("PASS: "+n+" ödeme geçmişi kalıcı kimlik kontrolü");
  }
}
