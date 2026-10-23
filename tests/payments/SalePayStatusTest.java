package com.zeytindefteri.app;
public class SalePayStatusTest {
  static int n;
  static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  static void eq(Object a,Object b,String m){ ok(a.equals(b), m+": '"+a+"' != '"+b+"'"); }
  public static void main(String[] a){
    String t="2026-10-23";
    ok(SalePayStatus.kind(false,100,0,"",t)==SalePayStatus.NO_SALE,"satış yok");
    eq(SalePayStatus.label(SalePayStatus.NO_SALE,0,0,"",t),"Satılmadı","satılmadı etiketi");
    ok(SalePayStatus.kind(true,4570,4570,"2026-10-01",t)==SalePayStatus.PAID,"tamamı alındı, gün geçse de ödendi");
    eq(SalePayStatus.label(SalePayStatus.PAID,1,1,"",t),"Ödendi","ödendi");
    ok(SalePayStatus.kind(true,4570,0,"2026-10-25",t)==SalePayStatus.WAITING,"bekliyor");
    eq(SalePayStatus.label(SalePayStatus.WAITING,4570,0,"2026-10-25",t),"Bekliyor · 25 Eki","bekliyor etiketi");
    eq(SalePayStatus.label(SalePayStatus.WAITING,4570,0,"",t),"Bekliyor","günsüz bekliyor");
    ok(SalePayStatus.kind(true,5210,2000,"",t)==SalePayStatus.PARTIAL,"kısmi");
    eq(SalePayStatus.label(SalePayStatus.PARTIAL,5210,2000,"",t),"Kısmi · 3.210 TL kaldı","kısmi etiketi");
    ok(SalePayStatus.kind(true,6370,0,"2026-10-21",t)==SalePayStatus.OVERDUE,"gecikti");
    eq(SalePayStatus.label(SalePayStatus.OVERDUE,6370,0,"2026-10-21",t),"Gecikti · 2 gün","gecikti etiketi");
    ok(SalePayStatus.kind(true,6370,100,"2026-10-21",t)==SalePayStatus.OVERDUE,"kısmi ama günü geçmiş");
    ok(SalePayStatus.kind(true,6370,0,t,t)==SalePayStatus.WAITING,"bugün söz günü: henüz gecikmedi");
    ok(SalePayStatus.overdueDays("2026-09-30","2026-10-02")==2,"ay geçişi");
    ok(SalePayStatus.overdueDays("2026-12-31","2027-01-01")==1,"yıl geçişi");
    ok(SalePayStatus.overdueDays("bozuk",t)==0,"bozuk tarih");
    eq(SalePayStatus.dueFor(SalePayStatus.DUE_TOMORROW,t),"2026-10-24","yarın");
    eq(SalePayStatus.dueFor(SalePayStatus.DUE_TWO_DAYS,t),"2026-10-25","2 gün");
    eq(SalePayStatus.dueFor(SalePayStatus.DUE_WEEK,"2026-12-28"),"2027-01-04","1 hafta yıl geçişi");
    eq(SalePayStatus.dueFor(SalePayStatus.DUE_PICK,t),"","tarih seçimi boş");
    ok(SalePayStatus.optionFor("2026-10-25",t)==SalePayStatus.DUE_TWO_DAYS,"seçenek geri bulunur");
    ok(SalePayStatus.optionFor("2026-11-15",t)==SalePayStatus.DUE_PICK,"özel gün");
    ok(SalePayStatus.optionFor("",t)==-1,"gün yok");
    ok(SalePayStatus.overdueDays("21.10.2026",t)==2,"eski dd.MM.yyyy biçimi");
    eq(SalePayStatus.dayMonth("20.10.2026"),"20 Ekim","gün ay");
    eq(SalePayStatus.label(SalePayStatus.WAITING,1,0,"25.10.2026",t),"Bekliyor · 25 Eki","eski biçim etiket");
    eq(SalePayStatus.longDate("2026-10-25"),"25 Ekim 2026, Pazar","uzun tarih");
    eq(SalePayStatus.shortDate("2026-01-05"),"5 Oca","kısa tarih");
    double[] q=SalePayStatus.quickAmounts(3210);
    ok(q.length==2&&q[0]==1000&&q[1]==2000,"hazır tutarlar 1.000/2.000");
    ok(SalePayStatus.quickAmounts(800).length==1,"tek hazır tutar");
    ok(SalePayStatus.quickAmounts(400).length==0,"küçük kalan");
    ok(SalePayStatus.quickAmounts(2000).length==2&&SalePayStatus.quickAmounts(2000)[1]==1000,"kalana eşit tutar önerilmez");
    eq(SalePayStatus.plain(3210),"3210","sade tutar");
    eq(SalePayStatus.plain(12.5),"12,50","ondalıklı");
    eq(SalePayStatus.methodLabel("TRANSFER"),"Havale/EFT","yöntem");
    eq(SalePayStatus.methodLabel(""),"Satışta alınan","ilk tahsilat");
    System.out.println("PASS: "+n+" tahsilat durumu kontrolü");
  }
}
