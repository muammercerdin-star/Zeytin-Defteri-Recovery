package com.zeytindefteri.app;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;
public class TezgahZTest {
  static int n;
  static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  static void eq(double a,double b,String m){ ok(Math.abs(a-b)<1e-6, m+": "+a+" != "+b); }
  public static void main(String[] a){
    SQLiteDatabase db=new SQLiteDatabase(a[0],a[1]);
    // Eski tablo (z_id sütunu yok) -> ensure sütunu ekler
    db.execSQL("CREATE TABLE tezgah_purchases (id INTEGER PRIMARY KEY AUTOINCREMENT, owner_uid TEXT DEFAULT '', record_date TEXT NOT NULL, seller_name TEXT DEFAULT '', seller_phone TEXT DEFAULT '', total_kg REAL DEFAULT 0, total_kasa INTEGER DEFAULT 0, gross_total REAL DEFAULT 0, amount_paid REAL DEFAULT 0, data TEXT DEFAULT '', created_at INTEGER DEFAULT 0)");
    TezgahModel m=new TezgahModel(); m.add(0,2,54); m.add(1,4,108); m.setPrice(0,130); m.setPrice(1,100);
    long old=TezgahPurchases.add(db,"t","2026-10-19","Eski","",m,0,100);
    ok(TezgahPurchases.hasColumn(db,"tezgah_purchases","z_id"),"z_id sütunu eklendi");
    long p1=TezgahPurchases.add(db,"t","2026-10-20","A","",m,m.total(),200);
    TezgahPurchases.add(db,"x","2026-10-20","Başka","",m,0,250);
    ok(TezgahPurchases.open(db,"t").size()==2,"açık dönem: dünkü + bugünkü");
    ok(TezgahZ.close(db,"bos",null,1)==-1,"alımsız gün sonu alınmaz");
    TezgahClosing c=TezgahClosing.fromPurchases(TezgahPurchases.open(db,"t"));
    c.setDelivered(1,198); c.rate=1.5; c.advance=10000;
    TezgahClosing.save(db,"t",TezgahZ.OPEN_KEY,c,5);
    long z1=TezgahZ.close(db,"t",c,1000);
    ok(z1>0,"Z-1 alındı");
    ok(TezgahPurchases.open(db,"t").isEmpty(),"açık dönem boşaldı");
    ok(TezgahPurchases.open(db,"x").size()==1,"başka hesabın açık alımı etkilenmez");
    ok(TezgahPurchases.forZ(db,"t",z1).size()==2,"Z'nin alımları");
    ok(!TezgahPurchases.delete(db,"t",p1),"Z'li alım silinmez (kilitli)");
    ok(TezgahPurchases.setPaid(db,"t",old,1),"Z'li alımda borç ödemesi işlenir");
    ok(!TezgahClosing.load(db,"t",TezgahZ.OPEN_KEY,TezgahClosing.fromPurchases(new ArrayList<>())),"açık akşam hesabı temizlendi");
    TezgahZ.Record r=TezgahZ.get(db,"t",z1);
    ok(r.number==1 && r.label().equals("Z-0001"),"numara "+r.label());
    ok(r.count==2 && r.kasa==12,"sayılar"); eq(r.kg,300,"kg"); eq(r.gross,2*(50*130+100*100),"tutar");
    eq(r.firstAt,100,"dönem başı"); eq(r.lastAt,200,"dönem sonu"); eq(r.rate,1.5,"oran"); eq(r.advance,10000,"avans");
    TezgahClosing back=TezgahZ.closingOf(r,TezgahPurchases.forZ(db,"t",z1));
    eq(back.delivered[1],198,"teslim saklandı"); eq(back.fireKg(),2,"fire"); eq(back.traderOwes(),c.traderOwes(),"tüccar ödeyecek aynı");
    ok(TezgahZ.get(db,"x",z1)==null,"başka hesap Z'yi görmez");
    long p3=TezgahPurchases.add(db,"t","2026-10-20","B","",m,0,300);
    ok(TezgahPurchases.delete(db,"t",p3),"açık dönemde silinir");
    TezgahPurchases.add(db,"t","2026-10-20","C","",m,0,400);
    long z2=TezgahZ.close(db,"t",null,2000);
    ok(TezgahZ.get(db,"t",z2).number==2,"Z-2");
    eq(TezgahZ.closingOf(TezgahZ.get(db,"t",z2),TezgahPurchases.forZ(db,"t",z2)).fireKg(),0,"hesapsız Z: fire 0");
    List<TezgahZ.Record> list=TezgahZ.list(db,"t",10);
    ok(list.size()==2 && list.get(0).number==2,"liste yeniden eskiye");
    ok(TezgahZ.close(db,"x",null,3000)>0 && TezgahZ.list(db,"x",5).get(0).number==1,"numara hesap başına");
    System.out.println("PASS: "+n+" gün sonu (Z) kontrolü");
  }
}
