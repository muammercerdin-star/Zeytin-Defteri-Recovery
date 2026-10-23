package com.zeytindefteri.app;
import android.database.sqlite.SQLiteDatabase;
import java.util.List;
public class SalePaymentsTest {
  static int n;
  static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  static void eq(double a,double b,String m){ ok(Math.abs(a-b)<1e-6, m+": "+a+" != "+b); }
  public static void main(String[] a){
    SQLiteDatabase db=new SQLiteDatabase(a[0],a[1]);
    db.execSQL("CREATE TABLE buyers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, owner_uid TEXT DEFAULT '')");
    db.execSQL("CREATE TABLE sales (id INTEGER PRIMARY KEY AUTOINCREMENT, garden_id INTEGER, buyer_id INTEGER, sale_date TEXT, gross_total REAL DEFAULT 0, transport_cost REAL DEFAULT 0, deductions REAL DEFAULT 0, amount_received REAL DEFAULT 0, due_date TEXT, updated_at INTEGER DEFAULT 0, owner_uid TEXT DEFAULT '')");
    db.execSQL("CREATE TABLE receivables (id INTEGER PRIMARY KEY AUTOINCREMENT, party_name TEXT, related_sale_id INTEGER, amount REAL DEFAULT 0, total_received REAL DEFAULT 0, due_date TEXT, status TEXT DEFAULT 'OPEN', updated_at INTEGER DEFAULT 0, owner_uid TEXT DEFAULT '')");
    db.execSQL("INSERT INTO buyers(name,owner_uid) VALUES('Mehmet Bey','u1')");
    db.execSQL("INSERT INTO sales(garden_id,buyer_id,sale_date,gross_total,amount_received,owner_uid) VALUES(3,1,'2026-10-20',5210,2000,'u1')");
    db.execSQL("INSERT INTO receivables(party_name,related_sale_id,amount,total_received,owner_uid) VALUES('Mehmet Bey',1,5210,2000,'u1')");
    db.execSQL("INSERT INTO sales(garden_id,sale_date,gross_total,amount_received,owner_uid) VALUES(3,'2026-10-19',6370,0,'u1')");
    db.execSQL("INSERT INTO receivables(party_name,related_sale_id,amount,total_received,owner_uid) VALUES('Hasan',2,6370,0,'u1')");
    db.execSQL("INSERT INTO sales(garden_id,sale_date,gross_total,amount_received,owner_uid) VALUES(9,'2026-10-19',999,0,'u2')");
    db.execSQL("INSERT INTO receivables(party_name,related_sale_id,amount,total_received,owner_uid) VALUES('Başka',3,999,0,'u2')");
    String t="2026-10-23";
    SalePayments.Info i=SalePayments.sale(db,"u1",1);
    ok(i!=null&&i.buyer.equals("Mehmet Bey"),"satış ve alıcı");
    eq(i.remaining(),3210,"kalan");
    ok(i.label(t).equals("Kısmi · 3.210 TL kaldı"),"etiket "+i.label(t));
    ok(SalePayments.sale(db,"u1",3)==null,"başkasının satışı görünmez");
    List<SalePayments.Payment> h=SalePayments.history(db,"u1",i);
    ok(h.size()==1&&h.get(0).method.isEmpty(),"satışta alınan ilk satır");
    eq(h.get(0).amount,2000,"ilk satır tutarı");
    ok(SalePayments.setDue(db,"u1",2,"2026-10-21",1L),"söz günü");
    ok(SalePayments.sale(db,"u1",2).label(t).equals("Gecikti · 2 gün"),"gecikti");
    ok(SalePayments.sale(db,"u1",2).due.equals("21.10.2026"),"satış formu biçiminde saklanır");
    android.database.Cursor rd=db.rawQuery("SELECT due_date FROM receivables WHERE related_sale_id=2",null); rd.moveToFirst();
    ok("21.10.2026".equals(rd.getString(0)),"alacağa da yazılır");
    ok(!SalePayments.setDue(db,"u1",3,"2026-10-21",1L),"başkasının satışına gün yazılmaz");
    SalePayments.Outstanding o=SalePayments.outstanding(db,"u1",t);
    eq(o.total,3210+6370,"toplam alacak"); ok(o.count==2&&o.buyers==2,"satış/alıcı sayısı");
    ok(o.overdueCount==1,"gecikmiş sayısı"); eq(o.overdueTotal,6370,"gecikmiş tutar");
    ok(SalePayments.record(db,"u1",1,1000,"TRANSFER",t,5L),"kısmi ödeme");
    i=SalePayments.sale(db,"u1",1); eq(i.received,3000,"alınan arttı");
    h=SalePayments.history(db,"u1",i); ok(h.size()==2&&h.get(1).method.equals("TRANSFER"),"geçmiş 2 satır");
    ok(SalePayments.record(db,"u1",1,999999,"CASH",t,6L),"fazla tutar kalanla sınırlı");
    i=SalePayments.sale(db,"u1",1); eq(i.received,5210,"tamamı alındı"); ok(i.label(t).equals("Ödendi"),"ödendi");
    h=SalePayments.history(db,"u1",i); eq(h.get(2).amount,2210,"son ödeme kalan kadar");
    ok(!SalePayments.record(db,"u1",1,10,"CASH",t,7L),"kapanmış satışa ödeme yok");
    ok(!SalePayments.record(db,"u1",3,10,"CASH",t,7L),"başkasının satışı");
    ok(!SalePayments.record(db,"u1",2,0,"CASH",t,7L),"sıfır tutar");
    o=SalePayments.outstanding(db,"u1",t);
    eq(o.total,6370,"kapanan alacak düştü"); ok(o.count==1,"tek açık alacak");
    ok(db.rawQuery("SELECT status FROM receivables WHERE related_sale_id=1",null).moveToFirst(),"alacak satırı");
    android.database.Cursor c=db.rawQuery("SELECT status, total_received FROM receivables WHERE related_sale_id=1",null); c.moveToFirst();
    ok("PAID".equals(c.getString(0)),"alacak PAID"); eq(c.getDouble(1),5210,"alacak tahsilatı");
    ok(SalePayments.record(db,"u1",2,6370,"weird",t,8L),"bilinmeyen yöntem nakit sayılır");
    h=SalePayments.history(db,"u1",SalePayments.sale(db,"u1",2)); ok(h.get(0).method.equals("CASH"),"nakit");
    System.out.println("PASS: "+n+" tahsilat kaydı kontrolü");
  }
}
