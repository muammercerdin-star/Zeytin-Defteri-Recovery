package com.zeytindefteri.app;
import android.database.sqlite.SQLiteDatabase;
import java.util.List;
public class TezgahLedgerTest {
  static int n;
  static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  static void eq(double a,double b,String m){ ok(Math.abs(a-b)<0.005, m+": "+a+" != "+b); }
  public static void main(String[] a){
    // saf hesaplar
    double[] kg={120,80,0,0,0,10}; double[] pr={110,90,60,40,30,15};
    eq(TezgahLedger.gradeAmount(kg,pr),120*110+80*90+150,"numara numara tutar");
    eq(TezgahLedger.sum(kg),210,"toplam kilo");
    eq(TezgahLedger.times(1000,85.5),85500,"toplam × fiyat");
    eq(TezgahLedger.times(-5,10),0,"eksi kilo sayılmaz");
    String gd=TezgahLedger.gradeDetail(kg,pr).replace('\u00a0',' ').replace('\u202f',' ');
    ok(gd.startsWith("Double 120 kg × 110")&&gd.contains("1 No 80 kg"),"açıklama: "+gd);
    ok(!TezgahLedger.gradeDetail(kg,pr).contains("2 No"),"boş numara yazılmaz");
    ok(TezgahLedger.amountError(TezgahLedger.SALE,-500).isEmpty(),"alacak eksi olabilir (iade)");
    ok(!TezgahLedger.amountError(TezgahLedger.RECEIVED,-5).isEmpty(),"tahsilat eksi olamaz");
    ok(!TezgahLedger.amountError(TezgahLedger.SALE,0).isEmpty(),"sıfır olmaz");
    ok(!TezgahLedger.amountError(TezgahLedger.PAID,2e9).isEmpty(),"üst sınır");

    SQLiteDatabase db=new SQLiteDatabase(a[0],a[1]);
    ok(TezgahLedger.add(db,"t1","",TezgahLedger.SALE,100,0,0,"","","2026-10-28",0,1)==-1,"adsız olmaz");
    ok(TezgahLedger.add(db,"t1","Hüseyin",9,100,0,0,"","","2026-10-28",0,1)==-1,"bilinmeyen tür olmaz");
    ok(TezgahLedger.add(db,"t1","Aracı Hüseyin",TezgahLedger.SALE,250000,2500,TezgahLedger.METHOD_DAY,"Z-0002","","2026-10-26",2,100)>0,"Z alacağı");
    ok(TezgahLedger.add(db,"t1","aracı hüseyin",TezgahLedger.SALE,150000,1500,TezgahLedger.METHOD_DAY,"Z-0003","","2026-10-27",3,200)>0,"ikinci Z");
    ok(TezgahLedger.add(db,"t1","Aracı Hüseyin",TezgahLedger.RECEIVED,100000,0,0,"","nakit","2026-10-28",0,300)>0,"tahsilat");
    ok(TezgahLedger.add(db,"t1","Fabrika",TezgahLedger.SALE,-2000,0,TezgahLedger.METHOD_DAY,"","","2026-10-28",4,400)>0,"iade (avans fazla)");
    ok(TezgahLedger.add(db,"t1","Ahmet Tüccar",TezgahLedger.SALE,50000,500,TezgahLedger.METHOD_TOTAL,"500 kg × 100","","2026-10-28",0,500)>0,"kendi satışım");
    ok(TezgahLedger.add(db,"t1","Ahmet Tüccar",TezgahLedger.RECEIVED,50000,0,0,"","","2026-10-28",0,600)>0,"tamamı ödendi");
    ok(TezgahLedger.add(db,"t1","Fabrika",TezgahLedger.PAID,500,0,0,"","","2026-10-28",0,700)>0,"iadenin bir kısmını ödedim");
    TezgahLedger.add(db,"t2","Aracı Hüseyin",TezgahLedger.SALE,999,0,0,"","","2026-10-28",0,800);
    eq(TezgahLedger.balanceOf(db,"t1","ARACI HÜSEYİN"),300000,"Hüseyin'den 300 bin alacak");
    eq(TezgahLedger.balanceOf(db,"t1","Fabrika"),-1500,"fabrikaya 1.500 borç");
    eq(TezgahLedger.balanceOf(db,"t1","Ahmet Tüccar"),0,"Ahmet kapandı");
    ok(TezgahLedger.hasZ(db,"t1",3)&&!TezgahLedger.hasZ(db,"t1",9)&&!TezgahLedger.hasZ(db,"t2",3),"Z işlendi mi");
    List<TezgahLedger.Entry> all=TezgahLedger.entries(db,"t1");
    ok(all.size()==7,"yalnız bu hesabın satırları");
    List<TezgahLedger.Party> ps=TezgahLedger.parties(all);
    ok(ps.size()==3,"3 alıcı");
    ok(ps.get(0).key.equals("araci huseyin")&&ps.get(1).key.equals("fabrika")&&ps.get(2).key.equals("ahmet tuccar"),"sıra: büyük bakiye önce, kapalı sonda");
    eq(ps.get(0).kg,4000,"teslim kilo toplamı");
    double[] t=TezgahLedger.totals(all);
    eq(t[0],300000,"toplam alacak"); eq(t[1],1500,"toplam borç");
    ok(TezgahLedger.balanceLine("Hüseyin",300000).contains("alacağın var"),"bakiye satırı");
    ok(TezgahLedger.balanceLine("Fabrika",-1500).contains("borçlusun"),"borç satırı");
    String st=TezgahLedger.statement("Aracı Hüseyin",TezgahLedger.entriesOf(db,"t1","Aracı Hüseyin"),"Tezgah Ali");
    ok(st.contains("KALAN ALACAK: ")&&st.indexOf("2026-10-26")<st.indexOf("2026-10-28"),"ekstre eskiden yeniye: "+st);
    ok(st.contains("Tahsilat: −"),"tahsilat eksi");
    ok(TezgahLedger.title(all.get(3)).contains("iade"),"iade başlığı: "+TezgahLedger.title(all.get(3)));
    ok(!TezgahLedger.delete(db,"t2",all.get(0).id),"başkasının satırı silinmez");
    ok(TezgahLedger.delete(db,"t1",all.get(0).id),"silinir");
    eq(TezgahLedger.balanceOf(db,"t1","Fabrika"),-2000,"ödeme silinince borç geri döner");
    System.out.println("PASS: "+n+" cari hesap kontrolü");
  }
}
