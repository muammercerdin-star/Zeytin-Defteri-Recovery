package com.zeytindefteri.app;
import android.database.sqlite.SQLiteDatabase;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
public class TezgahBackupTest {
  static int n;
  static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  static void eq(double a,double b,String m){ ok(Math.abs(a-b)<1e-6, m+": "+a+" != "+b); }
  public static void main(String[] a){
    SQLiteDatabase db=new SQLiteDatabase(a[0],a[1]);
    TezgahModel m=new TezgahModel(); m.add(0,2,60); m.add(3,1,30); m.setPrice(0,130); m.setPrice(3,40);
    long p1=TezgahPurchases.add(db,"u1","2026-10-24","Ali Özdemir","0532",m,1000,100);
    long p2=TezgahPurchases.add(db,"u1","2026-10-24","Veli\tTab \\ Ters","",m,0,200);
    long other=TezgahPurchases.add(db,"u2","2026-10-24","Başka","",m,0,300);
    double gross=TezgahPurchases.forDay(db,"u1","2026-10-24").get(0).gross;
    // Kısmi ödemeler
    ok(TezgahPayments.pay(db,"u1",p1,500,"2026-10-26",400),"kısmi ödeme");
    ok(!TezgahPayments.pay(db,"u2",p1,10,"2026-10-26",401),"başkasının alımına ödeme yok");
    ok(!TezgahPayments.pay(db,"u1",p1,0,"2026-10-26",402),"sıfır tutar");
    TezgahPurchases.Purchase x=find(TezgahPurchases.all(db,"u1",100),p1);
    eq(x.paid,1500,"ödenen arttı");
    List<TezgahPayments.Payment> h=TezgahPayments.history(db,"u1",x);
    ok(h.size()==2&&h.get(0).atPurchase,"alımda ödenen + sonraki ödeme");
    eq(h.get(0).amount,1000,"alımda ödenen"); eq(h.get(1).amount,500,"sonraki");
    ok(TezgahPayments.pay(db,"u1",p1,999999,"2026-10-27",403),"fazlası kalanla sınırlı");
    x=find(TezgahPurchases.all(db,"u1",100),p1); eq(x.paid,gross,"borç kapandı");
    ok(!TezgahPayments.pay(db,"u1",p1,5,"2026-10-27",404),"kapalı borca ödeme yok");
    ok(TezgahPayments.pay(db,"u1",p2,100,"2026-10-27",405),"ikinci alıma ödeme");
    OilPressings.Pressing op=new OilPressings.Pressing(); op.mill="Edremit Yağhanesi"; op.date="2026-10-24"; op.oliveKg=1000; op.oilLitre=240;
    ok(OilPressings.save(db,"u1",op,500L)>0,"sıkım kaydı");
    ok(TezgahCrates.add(db,"u1","Ali Özdemir",TezgahCrates.GAVE,30,"2026-10-24",p1,"",600L)>0,"kasa hareketi");
    // Yedek
    Map<String,Object> prefs=new LinkedHashMap<>(); prefs.put("tare",2.0); prefs.put("buy_prices",m.encode()); prefs.put("crate_total",500L);
    String text=TezgahBackup.export(db,"u1",prefs,999L);
    ok(text.startsWith("ZEYTIN-DEFTERI-YEDEK 1\n"),"başlık");
    ok(!text.contains("Başka"),"başka hesabın kaydı yedeğe girmez");
    TezgahBackup.Data d=TezgahBackup.parse(text);
    ok(d.purchases()==2,"2 alım yedekte"); ok(d.rowCount("tezgah_payments")==3,"3 ödeme yedekte");
    ok(d.created==999L,"tarih");
    ok(String.valueOf(d.prefs.get("buy_prices")).equals(m.encode()),"fiyatlar yedekte");
    eq((Double)d.prefs.get("tare"),2.0,"dara yedekte");
    ok(((Number)d.prefs.get("crate_total")).intValue()==500,"toplam kasa yedekte");
    ok(d.rowCount("tezgah_crates")==1&&TezgahBackup.describe(d).contains("1 kasa hareketi"),"kasa hareketi yedekte");
    ok(TezgahBackup.describe(d).startsWith("2 alım")&&TezgahBackup.describe(d).contains("1 sıkım"),"özet "+TezgahBackup.describe(d));
    // Telefon "kaybolur": u1 kayıtları silinir, sonra geri yüklenir
    db.execSQL("DELETE FROM tezgah_purchases WHERE owner_uid='u1'");
    db.execSQL("DELETE FROM tezgah_payments WHERE owner_uid='u1'");
    db.execSQL("DELETE FROM oil_pressings WHERE owner_uid='u1'");
    db.execSQL("DELETE FROM tezgah_crates WHERE owner_uid='u1'");
    ok(TezgahPurchases.all(db,"u1",100).isEmpty(),"kayıtlar gitti");
    int w=TezgahBackup.restore(db,"u1",d);
    ok(w==7,"7 satır geri yazıldı: "+w);
    ok(TezgahCrates.balanceOf(db,"u1","ali özdemir")==30,"kasa bakiyesi geri geldi");
    ok(OilPressings.list(db,"u1",2026).size()==1,"sıkım da geri geldi");
    List<TezgahPurchases.Purchase> back=TezgahPurchases.all(db,"u1",100);
    ok(back.size()==2,"alımlar geri geldi");
    TezgahPurchases.Purchase y=find(back,p2);
    ok(y!=null&&y.seller.equals("Veli\tTab \\ Ters"),"kaçışlı metin aynen geri geldi");
    eq(find(back,p1).paid,gross,"ödenen aynen");
    ok(TezgahPayments.history(db,"u1",find(back,p1)).size()==3,"ödeme geçmişi bağlı (id korundu)");
    ok(TezgahModel.decode(find(back,p1).data).gradeNet(3)==m.gradeNet(3),"tartım verisi aynen");
    ok(find(TezgahPurchases.all(db,"u2",100),other)!=null,"başka hesabın kaydına dokunulmaz");
    // Tekrar geri yükleme çoğaltmaz (değiştirir)
    ok(TezgahBackup.restore(db,"u1",d)==7,"ikinci kez"); ok(TezgahPurchases.all(db,"u1",100).size()==2,"çoğalmadı");
    // Başka hesaba taşıma: owner_uid yeni hesap olur
    db.execSQL("DELETE FROM tezgah_purchases WHERE owner_uid='u1'");
    db.execSQL("DELETE FROM tezgah_payments WHERE owner_uid='u1'");
    db.execSQL("DELETE FROM oil_pressings WHERE owner_uid='u1'");
    db.execSQL("DELETE FROM tezgah_crates WHERE owner_uid='u1'");
    ok(TezgahBackup.restore(db,"u3",d)==7,"yeni hesaba"); ok(TezgahPurchases.all(db,"u3",100).size()==2,"yeni hesapta görünür");
    ok(TezgahBackup.unescape(TezgahBackup.escape("a\nb\tc\\d\r")).equals("a\nb\tc\\d\r"),"kaçış gidip gelir");
    ok(!TezgahBackup.escape("a\nb").contains("\n"),"satır sonu kaçışlanır");
    // Bozuk dosyalar
    bad("merhaba"); bad("ZEYTIN-DEFTERI-YEDEK 9\n"); bad("ZEYTIN-DEFTERI-YEDEK 1\n#TABLE tezgah_z\n#COLS id\tz_no\nI1\n");
    ok(TezgahBackup.parse("ZEYTIN-DEFTERI-YEDEK 1\n#TABLE kotu_tablo\n#COLS id\nI1\n").rows.isEmpty(),"bilinmeyen tablo atlanır");
    System.out.println("PASS: "+n+" ödeme ve yedek kontrolü");
  }
  static void bad(String s){ try{ TezgahBackup.parse(s); throw new AssertionError("bozuk kabul edildi: "+s);}catch(IllegalArgumentException e){ n++; } }
  static TezgahPurchases.Purchase find(List<TezgahPurchases.Purchase> l,long id){ for(TezgahPurchases.Purchase p:l) if(p.id==id) return p; return null; }
}
