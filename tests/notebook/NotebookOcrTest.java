package com.zeytindefteri.app;
import java.util.*;
public class NotebookOcrTest {
  static int n;
  static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  static void eq(double a,double b,String m){ ok(Math.abs(a-b)<1e-6, m+": "+a+" != "+b); }
  static NotebookOcr.Result p(String... l){ return NotebookOcr.parse(Arrays.asList(l)); }
  static void row(NotebookOcr.Result r,int g,double kg,double pr,double amt,int st){
    NotebookOcr.Row x=r.rows[g]; ok(x!=null,"satır "+g+" yok");
    eq(x.kg,kg,"kg "+g); eq(x.price,pr,"fiyat "+g); eq(x.amount,amt,"tutar "+g); ok(x.status()==st,"durum "+g+"="+x.status());
  }
  public static void main(String[] a){
    // Kullanıcının defter sayfası (temiz okuma)
    NotebookOcr.Result r=p("MAREM GERDIN","0-5.5 x 130 715","1- 43 x 100 4.300","2-54x70 3.780","3-32 x 40 1.280","4-12 x 30 360","Y-5 x20 = 100","151.5 (69.5) 10.535");
    row(r,0,5.5,130,715,NotebookOcr.OK); row(r,1,43,100,4300,NotebookOcr.OK); row(r,2,54,70,3780,NotebookOcr.OK);
    row(r,3,32,40,1280,NotebookOcr.OK); row(r,4,12,30,360,NotebookOcr.OK); row(r,5,5,20,100,NotebookOcr.OK);
    ok(r.filled()==6,"6 satır"); eq(r.sumKg(),151.5,"toplam kg"); eq(r.sumAmount(),10535,"toplam tutar");
    ok(r.hasTotalAmount,"defter toplamı bulundu"); eq(r.totalAmount,10535,"defter toplamı"); ok(r.hasTotalKg,"toplam kg bulundu"); eq(r.totalKg,151.5,"defter kg");
    // Gerçekçi okuma hataları: × işareti, X, O/0 karışması, ondalık virgül, Y'nin 4 okunması, sınıfın kesik okunması
    r=p(")-5,5 X 13O 7I5","1- 43 × 1OO 4.3OO","2 - 54*70 3.780","3-32 x 40 1.280","4-12 x 30 360","4-5 X20= 100","151,5 10.535");
    row(r,0,5.5,130,715,NotebookOcr.OK); row(r,1,43,100,4300,NotebookOcr.OK); row(r,2,54,70,3780,NotebookOcr.OK);
    row(r,4,12,30,360,NotebookOcr.OK); row(r,5,5,20,100,NotebookOcr.OK);
    // Yanlış okunan rakam: hesap tutmaz, öneri çıkar
    r=p("1- 48 x 100 4.300");
    row(r,1,48,100,4300,NotebookOcr.MISMATCH); eq(r.rows[1].suggestedKg(),43,"tutara göre öneri");
    // Tutar yazılmamış: kontrol edilemez
    r=p("2-54 x 70"); row(r,2,54,70,0,NotebookOcr.UNCHECKED); ok(Double.isNaN(r.rows[2].suggestedKg()),"öneri yok");
    // Sınıfsız satırlar sırayla dağıtılır
    r=p("5.5 x 130 715","43 x 100 4.300"); row(r,0,5.5,130,715,NotebookOcr.OK); row(r,1,43,100,4300,NotebookOcr.OK);
    // "1.5 x 100 150": baştaki 1 sınıf değil
    r=p("1.5 x 100 150"); row(r,0,1.5,100,150,NotebookOcr.OK);
    // Kelime ile sınıf
    r=p("Double 3 x 130 390","Yağlık: 3 x 15 45","2 numara - 51 x 70 3.570");
    row(r,0,3,130,390,NotebookOcr.OK); row(r,5,3,15,45,NotebookOcr.OK); row(r,2,51,70,3570,NotebookOcr.OK);
    // Binlik kilo: 1.250 kg × 40 = 50.000
    r=p("3-1.250 x 40 50.000"); row(r,3,1250,40,50000,NotebookOcr.OK);
    // Ondalık kilo binlik değil: 1.250 × 40 = 50
    r=p("3-1.250 x 40 50"); row(r,3,1.25,40,50,NotebookOcr.OK);
    // Satır olmayan yazılar yok sayılır
    r=p("26 Ekim","Ahmet Usta","tel 0532 000 00 00"); ok(r.filled()==0,"satır yok"); ok(!r.hasTotalAmount,"toplam yok");
    // Satır dizme: aynı yükseklikteki parçalar soldan sağa
    List<NotebookOcr.Piece> ps=new ArrayList<>();
    ps.add(new NotebookOcr.Piece("4.300",400,105,135)); ps.add(new NotebookOcr.Piece("1- 43 x 100",20,100,130));
    ps.add(new NotebookOcr.Piece("0-5.5 x 130",22,50,80)); ps.add(new NotebookOcr.Piece("715",410,52,84));
    List<String> ls=NotebookOcr.lines(ps); ok(ls.size()==2,"2 satır: "+ls);
    ok(ls.get(0).equals("0-5.5 x 130 715"),"ilk satır "+ls.get(0)); ok(ls.get(1).equals("1- 43 x 100 4.300"),"ikinci "+ls.get(1));
    r=NotebookOcr.parse(ls); row(r,1,43,100,4300,NotebookOcr.OK);
    // Durum fonksiyonu (kontrol ekranında canlı)
    ok(NotebookOcr.status(0,100,false,0)==NotebookOcr.EMPTY,"boş");
    ok(NotebookOcr.status(10,100,true,1000)==NotebookOcr.OK,"tutar");
    ok(NotebookOcr.status(10,100,true,1200)==NotebookOcr.MISMATCH,"tutmaz");
    System.out.println("NotebookOcrTest: "+n+" kontrol geçti");
  }
}
