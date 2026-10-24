package com.zeytindefteri.app;
import android.database.sqlite.SQLiteDatabase;
import java.util.List;
public class OilPressingsTest {
  static int n;
  static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  static void eq(double a,double b,String m){ ok(Math.abs(a-b)<1e-6, m+": "+a+" != "+b); }
  static OilPressings.Pressing p(String mill,String date,double kg,double lt){ OilPressings.Pressing x=new OilPressings.Pressing(); x.mill=mill; x.date=date; x.oliveKg=kg; x.oilLitre=lt; return x; }
  public static void main(String[] a){
    eq(OilPressings.kilodan(1000,250),4,"4 kilodan");
    eq(OilPressings.perTon(4),250,"1 tondan 250 lt"); eq(OilPressings.perTon(5),200,"5 kilodan 200 lt");
    ok(OilPressings.kilodanText(4.24).equals("4,2 kilodan"),"metin "+OilPressings.kilodanText(4.24));
    ok(OilPressings.kilodanText(0).equals("—"),"boş");
    eq(OilPressings.litreFromKg(916),1000,"kilo litreye");
    ok(OilPressings.error(1000,250,"A").isEmpty(),"geçerli");
    ok(!OilPressings.error(1000,250," ").isEmpty(),"ad şart");
    ok(!OilPressings.error(0,250,"A").isEmpty()&&!OilPressings.error(1000,0,"A").isEmpty(),"kilo/litre şart");
    ok(OilPressings.error(1000,1000,"A").contains("olağan dışı"),"1 kilodan olmaz");
    ok(OilPressings.error(1000,50,"A").contains("olağan dışı"),"20 kilodan olmaz");
    OilPressings.Pressing f=p("A","2026-10-20",1200,300); f.feeType=OilPressings.FEE_PER_KG; f.feeValue=1.5;
    eq(f.feeTl(),1800,"kg başı ücret"); eq(f.shareLitre(),0,"pay yok");
    OilPressings.Pressing s=p("B","2026-10-21",1000,200); s.feeType=OilPressings.FEE_SHARE; s.feeValue=10;
    eq(s.shareLitre(),20,"yağ payı %10"); eq(s.feeTl(),0,"TL yok");
    java.util.List<OilPressings.Pressing> l=new java.util.ArrayList<>();
    l.add(p("B Yağhanesi","2026-10-22",1000,200)); l.add(p("a yağhanesi","2026-10-21",800,200)); l.add(p("A Yağhanesi","2026-10-20",1200,300)); l.add(p("b yaghanesi","2026-10-19",500,100));
    List<OilPressings.Mill> ms=OilPressings.mills(l);
    ok(ms.size()==2,"Türkçe/büyük-küçük birleşir");
    ok(ms.get(0).name.equals("a yağhanesi")&&ms.get(0).count==2,"en iyi önce: "+ms.get(0).name);
    eq(ms.get(0).kilodan(),4,"A ortalama (2000/500)"); eq(ms.get(1).kilodan(),5,"B ortalama (1500/300)");
    eq(OilPressings.extraPerTon(ms.get(0),ms.get(1)),50,"1 tonda 50 lt fazla");
    OilPressings.Season se=OilPressings.season(l); ok(se.count==4,"sezon sayısı"); eq(se.oliveKg,3500,"sezon zeytin"); eq(se.oilLitre,800,"sezon yağ");
    ok(OilPressings.recentMills(l,5).size()==2,"son yağhaneler tekrarsız");
    // veritabanı
    SQLiteDatabase db=new SQLiteDatabase(a[0],a[1]);
    long id=OilPressings.save(db,"u1",p("Edremit Yağhanesi","2026-10-24",1000,240),1L);
    ok(id>0,"kayıt");
    ok(OilPressings.save(db,"u1",p("X","2026-10-24",0,240),2L)==-1,"boş kilo kaydedilmez");
    OilPressings.save(db,"u2",p("Başka","2026-10-24",1000,240),3L);
    OilPressings.save(db,"u1",p("Eski","2025-11-02",1000,230),4L);
    List<OilPressings.Pressing> y=OilPressings.list(db,"u1",2026);
    ok(y.size()==1&&y.get(0).mill.equals("Edremit Yağhanesi"),"yıl ve hesap süzgeci");
    ok(OilPressings.list(db,"u1",0).size()==2,"bütün yıllar");
    OilPressings.Pressing e=y.get(0); e.oilLitre=250; e.acid=0.6; e.note="soğuk sıkım";
    ok(OilPressings.save(db,"u1",e,5L)==id,"düzenleme");
    OilPressings.Pressing back=OilPressings.list(db,"u1",2026).get(0);
    eq(back.oilLitre,250,"litre güncellendi"); eq(back.acid,0.6,"asit"); ok(back.note.equals("soğuk sıkım"),"not");
    ok(!OilPressings.delete(db,"u2",id),"başkası silemez"); ok(OilPressings.delete(db,"u1",id),"silme");
    ok(OilPressings.list(db,"u1",2026).isEmpty(),"silindi");
    System.out.println("PASS: "+n+" yağhane sıkım kontrolü");
  }
}
