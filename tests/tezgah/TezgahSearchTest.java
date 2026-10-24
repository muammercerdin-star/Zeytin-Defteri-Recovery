package com.zeytindefteri.app;
import java.util.ArrayList;
import java.util.List;
public class TezgahSearchTest {
  static int n;
  static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  static TezgahPurchases.Purchase p(String s,String ph,double kg,double gross,double paid){
    TezgahPurchases.Purchase x=new TezgahPurchases.Purchase(); x.seller=s; x.phone=ph; x.kg=kg; x.gross=gross; x.paid=paid; return x; }
  public static void main(String[] a){
    List<TezgahPurchases.Purchase> all=new ArrayList<>();
    all.add(p("Ali Özdemir","0532 111 22 33",211,13200,11545));
    all.add(p("Münferit satıcı","",73,4825,4825));
    all.add(p("ŞÜKRÜ IŞIK","",50,3000,0));
    all.add(p("ali özdemir","",100,5000,5000));
    all.add(p("","05551234567",30,1800,1000));
    ok(TezgahSearch.filter(all,"ali",false).size()==2,"ad, büyük/küçük harf fark etmez");
    ok(TezgahSearch.filter(all,"ozdemir",false).size()==2,"Türkçe harfsiz yazınca bulur");
    ok(TezgahSearch.filter(all,"şükrü ışık",false).size()==1,"Türkçe harfle");
    ok(TezgahSearch.filter(all,"sukru isik",false).size()==1,"Türkçe harfsiz");
    ok(TezgahSearch.filter(all,"1655",false).size()==1,"kalan borç tutarıyla");
    ok(TezgahSearch.filter(all,"13200",false).size()==1,"toplam tutarla");
    ok(TezgahSearch.filter(all,"13.200",false).size()==1,"noktalı tutarla");
    ok(TezgahSearch.filter(all,"1234567",false).size()==1,"telefonla");
    ok(TezgahSearch.filter(all,"",true).size()==3,"sadece borçlular");
    ok(TezgahSearch.filter(all,"ali",true).size()==1,"borçlu ve ad");
    ok(TezgahSearch.filter(all,"",false).size()==5,"boş arama hepsi");
    ok(TezgahSearch.filter(all,"zzz",false).isEmpty(),"yok");
    List<TezgahSearch.Group> g=TezgahSearch.groups(TezgahSearch.filter(all,"",false));
    ok(g.get(0).count==2&&Math.abs(g.get(0).remaining-1655)<0.01,"aynı üretici toplanır: "+g.get(0).name);
    for(TezgahSearch.Group x:g) ok(!x.name.equals("Münferit satıcı")&&!x.name.isEmpty(),"münferit/adsız gruplanmaz");
    ok(Math.abs(TezgahSearch.totalRemaining(TezgahSearch.filter(all,"",true))-(1655+3000+800))<0.01,"toplam borç");
    System.out.println("PASS: "+n+" alım defteri arama kontrolü");
  }
}
