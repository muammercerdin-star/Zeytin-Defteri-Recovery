package com.zeytindefteri.app;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;
public class TezgahClosingTest {
  static int n;
  static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  static void eq(double a,double b,String m){ ok(Math.abs(a-b)<1e-6, m+": "+a+" != "+b); }
  public static void main(String[] a){
    SQLiteDatabase db=new SQLiteDatabase(a[0],a[1]);
    TezgahModel m1=new TezgahModel(); m1.add(0,1,12); m1.add(1,4,108); m1.setPrice(0,130); m1.setPrice(1,100);
    TezgahModel m2=new TezgahModel(); m2.add(1,2,54); m2.add(5,1,22); m2.setPrice(1,100); m2.setPrice(5,20);
    TezgahPurchases.add(db,"t","2026-10-20","A","",m1,m1.total(),1);
    TezgahPurchases.add(db,"t","2026-10-20","B","",m2,0,2);
    List<TezgahPurchases.Purchase> list=TezgahPurchases.forDay(db,"t","2026-10-20");
    TezgahClosing c=TezgahClosing.fromPurchases(list);
    eq(c.bought[0],10,"double alınan"); eq(c.bought[1],150,"1 numara alınan"); eq(c.bought[5],20,"yağlık");
    eq(c.boughtKg(),180,"toplam alınan"); eq(c.deliveredKg(),180,"teslim başta alınana eşit"); eq(c.fireKg(),0,"fire yok");
    ok(c.purchases==2,"alım sayısı");
    eq(c.producerTotal,10*130+150*100+20*20,"üretici tutarı"); eq(c.producerDue(),5000+400,"üreticiye kalan borç");
    c.setDelivered(1,147); c.setDelivered(5,20.5);
    ok(c.edited[1] && c.edited[5] && !c.edited[0],"elle girilenler işaretli");
    eq(c.fire(1),3,"1 numara fire"); eq(c.fire(5),-0.5,"yağlık fazla"); eq(c.fireKg(),2.5,"net fire");
    eq(Math.round(c.firePercent()*100)/100.0,1.39,"fire yüzdesi");
    eq(c.fireValue(),3*100-0.5*20,"fire değeri");
    c.rate=1.5; c.advance=10000;
    eq(c.commission(),177.5*1.5,"komisyon teslim kg üzerinden");
    eq(c.traderOwes(),16700+266.25-10000,"tüccar ödeyecek");
    String s=c.summary("20 Ekim 2026","Hasan");
    ok(s.contains("1 Numara: 150 kg → 147 kg (−3 kg)"),"satır\n"+s);
    ok(s.contains("Yağlık: 20 kg → 20,5 kg (+0,5 kg)"),"fazla satırı");
    ok(s.contains("Fire: 2,5 kg (%1,4)"),"fire özeti");
    ok(s.contains("TÜCCAR ÖDEYECEK: 6.966 ₺"),"ödenecek "+s);
    c.advance=50000; ok(c.summary("x","").contains("TEZGAH İADE EDECEK: 33.034"),"iade");
    ok(!TezgahClosing.load(db,"t","2026-10-20",TezgahClosing.fromPurchases(list)),"kayıt yok");
    ok(TezgahClosing.save(db,"t","2026-10-20",c,5),"kaydet");
    c.rate=2; ok(TezgahClosing.save(db,"t","2026-10-20",c,6),"güncelle (günde tek)");
    TezgahClosing back=TezgahClosing.fromPurchases(list);
    ok(TezgahClosing.load(db,"t","2026-10-20",back),"yükle");
    eq(back.delivered[1],147,"teslim geri gelir"); eq(back.rate,2,"oran güncel"); eq(back.advance,50000,"avans");
    ok(!TezgahClosing.load(db,"baska","2026-10-20",TezgahClosing.fromPurchases(list)),"başka hesap görmez");
    // Hata senaryosu: kapanış kaydedildikten sonra yeni alım gelir; dokunulmayan numaralar fire göstermemeli.
    TezgahModel m3=new TezgahModel(); m3.add(0,1,8); m3.add(2,2,64); m3.setPrice(0,130); m3.setPrice(2,70);
    TezgahPurchases.add(db,"t","2026-10-20","C","",m3,0,3);
    TezgahClosing later=TezgahClosing.fromPurchases(TezgahPurchases.forDay(db,"t","2026-10-20"));
    TezgahClosing.load(db,"t","2026-10-20",later);
    eq(later.bought[0],16,"yeni alımla double arttı"); eq(later.delivered[0],16,"dokunulmayan teslim alınanı izler"); eq(later.fire(0),0,"sahte fire yok");
    eq(later.delivered[2],60,"2 numara da izler"); eq(later.delivered[1],147,"elle girilen korunur");
    // Eski biçim (her numara dolu) yok sayılır.
    db.execSQL("UPDATE tezgah_closings SET delivered='10;107;0;0;0;20' WHERE owner_uid='t'");
    TezgahClosing old=TezgahClosing.fromPurchases(TezgahPurchases.forDay(db,"t","2026-10-20"));
    TezgahClosing.load(db,"t","2026-10-20",old);
    eq(old.fireKg(),0,"eski kendiliğinden kayıt sahte fire üretmez"); eq(old.rate,2,"oran korunur");
    old.setDelivered(1,150); ok(!old.edited[1],"alınana eşit girilirse elle sayılmaz");
    old.setDelivered(1,149); old.clearDelivered(1); eq(old.delivered[1],150,"kutu boşalınca alınana döner");
    System.out.println("PASS: "+n+" akşam hesabı kontrolü");
  }
}
