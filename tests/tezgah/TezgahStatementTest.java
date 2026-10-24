package com.zeytindefteri.app;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
public class TezgahStatementTest {
  static int n;
  static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  static void eq(double a,double b,String m){ ok(Math.abs(a-b)<1e-6, m+": "+a+" != "+b); }
  static TezgahPurchases.Purchase p(long id,String s,String ph,double gross,double paid){
    TezgahModel m=new TezgahModel(); m.add(0,1,27); m.add(3,2,54); m.setPrice(0,100); m.setPrice(3,40);
    TezgahPurchases.Purchase x=new TezgahPurchases.Purchase(); x.id=id; x.seller=s; x.phone=ph; x.kg=75; x.kasa=3;
    x.gross=gross; x.paid=paid; x.data=m.encode(); x.date="2026-10-2"+id; return x; }
  public static void main(String[] a){
    // yeniden eskiye (TezgahPurchases.all sırası)
    List<TezgahPurchases.Purchase> all=new ArrayList<>(Arrays.asList(
      p(4,"ALİ ÖZDEMİR","0533",3000,0), p(3,"Veli","",5000,5000), p(2,"ali ozdemir","",4000,1000), p(1,"Ali Özdemir","0532",2000,2000),
      p(5,"Münferit satıcı","",100,0)));
    TezgahStatement s=TezgahStatement.of(all,"Ali Özdemir");
    ok(s.purchases.size()==3,"Türkçe/büyük-küçük fark etmeden 3 alım");
    ok(s.purchases.get(0).id==1&&s.purchases.get(2).id==4,"eskiden yeniye");
    eq(s.gross,9000,"toplam"); eq(s.paid,3000,"ödenen"); eq(s.remaining(),6000,"borç");
    ok(s.debtCount()==2,"borçlu alım sayısı"); ok(s.phone.equals("0533"),"en yeni telefon");
    ok(s.kasa==9,"kasa"); eq(s.gradeKg[0],3*25,"Double kilo (27-2 dara)");
    List<long[]> plan=s.allocate(3500);
    ok(plan.size()==2,"iki alıma dağıtılır");
    ok(plan.get(0)[0]==2&&plan.get(0)[1]==300000,"önce en eski borç (3.000)");
    ok(plan.get(1)[0]==4&&plan.get(1)[1]==50000,"kalan 500 sonrakine");
    ok(s.allocate(99999).size()==2,"fazla para dağıtılmaz");
    String t=s.text("Tezgah Ahmet",null,"24.10.2026");
    ok(t.contains("KALAN BORÇ: 6.000")&&t.contains("Üretici: Ali Özdemir"),"metin: "+t);
    ok(!TezgahStatement.hasStatement("Münferit satıcı")&&!TezgahStatement.hasStatement(" "),"münferit/adsız ekstre yok");
    ok(TezgahStatement.of(all,"Münferit satıcı").purchases.isEmpty(),"münferit toplanmaz");
    System.out.println("PASS: "+n+" üretici ekstresi kontrolü");
  }
}
