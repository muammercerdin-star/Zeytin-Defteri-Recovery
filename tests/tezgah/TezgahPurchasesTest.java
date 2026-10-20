package com.zeytindefteri.app;
import android.database.sqlite.SQLiteDatabase;
import java.util.List;
public class TezgahPurchasesTest {
  static int n;
  static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  static void eq(double a,double b,String m){ ok(Math.abs(a-b)<1e-6, m+": "+a+" != "+b); }
  public static void main(String[] a){
    SQLiteDatabase db=new SQLiteDatabase(a[0],a[1]);
    TezgahModel m=new TezgahModel(); m.add(0,10,258); m.add(1,5,130); m.setPrice(0,100); m.setPrice(1,90);
    ok(TezgahPurchases.add(db,"t1","2026-10-20","Ahmet","0532",new TezgahModel(),0,1)==-1,"boş alım kaydedilmez");
    long id1=TezgahPurchases.add(db,"t1","2026-10-20","Ahmet Yılmaz","0532",m,10000,100);
    ok(id1>0,"alım kaydı");
    long id2=TezgahPurchases.add(db,"t1","2026-10-20","Veli","",m,999999,200);
    long other=TezgahPurchases.add(db,"t2","2026-10-20","Başka","",m,0,300);
    TezgahPurchases.add(db,"t1","2026-10-19","Dün","",m,0,50);
    List<TezgahPurchases.Purchase> day=TezgahPurchases.forDay(db,"t1","2026-10-20");
    ok(day.size()==2,"yalnız bu hesabın bugünkü alımları");
    ok(day.get(0).seller.equals("Veli"),"yeniden eskiye");
    eq(day.get(0).paid,day.get(0).gross,"ödenen toplamı aşmaz");
    eq(day.get(1).kg,238+120,"net kilo");
    ok(day.get(1).kasa==15,"kasa");
    eq(TezgahModel.decode(day.get(1).data).total(),m.total(),"fiş verisi geri gelir");
    TezgahPurchases.Day d=TezgahPurchases.summary(db,"t1","2026-10-20");
    ok(d.count==2,"gün sayısı"); eq(d.kg,716,"gün kilo"); eq(d.remaining(),m.total()-10000,"gün borç");
    ok(TezgahPurchases.dayLine(d).startsWith("2 alım · 716"),"gün satırı "+TezgahPurchases.dayLine(d));
    ok(TezgahPurchases.dayLine(new TezgahPurchases.Day()).equals("Bugün henüz alım yok"),"boş gün");
    ok(TezgahPurchases.setPaid(db,"t1",id1,m.total()),"sonradan ödeme");
    eq(TezgahPurchases.summary(db,"t1","2026-10-20").remaining(),0,"borç kapandı");
    ok(!TezgahPurchases.setPaid(db,"t1",other,1),"başkasının kaydı değişmez");
    ok(!TezgahPurchases.delete(db,"t1",other),"başkasının kaydı silinmez");
    ok(TezgahPurchases.delete(db,"t1",id2),"silme");
    ok(TezgahPurchases.forDay(db,"t1","2026-10-20").size()==1,"silindi");
    System.out.println("PASS: "+n+" tüccar alım kaydı kontrolü");
  }
}
