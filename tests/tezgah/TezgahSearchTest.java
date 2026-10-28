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
    // Üreticiye göre tek kart
    for(int i=0;i<all.size();i++) all.get(i).createdAt=100+i;
    all.add(p("Münferit satıcı","",10,600,0)); all.get(5).createdAt=50;
    List<TezgahSearch.Group> bp=TezgahSearch.byProducer(all,false);
    ok(bp.size()==5,"Ali iki fişle tek kart, münferitler ayrı, adsız ayrı: "+bp.size());
    TezgahSearch.Group ali=null; for(TezgahSearch.Group x:bp) if(x.key.equals("ali ozdemir")) ali=x;
    ok(ali!=null&&ali.count==2&&ali.items.size()==2,"Ali'nin 2 fişi");
    ok(ali.items.get(0).createdAt==103,"fişler yeniden eskiye");
    ok(Math.abs(ali.remaining-1655)<0.01&&Math.abs(ali.gross-18200)<0.01,"Ali toplamları");
    ok(bp.get(0).lastAt==104,"en son alım yapan önce");
    List<TezgahSearch.Group> bd=TezgahSearch.byProducer(TezgahSearch.filter(all,"",true),true);
    ok(bd.get(0).name.equals("ŞÜKRÜ IŞIK")&&bd.get(1).key.equals("ali ozdemir"),"borçlularda en çok borç önce");
    int walk=0; for(TezgahSearch.Group x:bp) if(x.name.equals("Münferit satıcı")) { walk++; ok(x.count==1&&x.key.isEmpty(),"münferit tek fiş"); }
    ok(walk==2,"iki münferit ayrı kalır");
    // Önceki borç (fişte güncel borç)
    java.util.List<TezgahPurchases.Purchase> h=new java.util.ArrayList<>();
    TezgahPurchases.Purchase a1=p("Mehmet Afşar","",61,3960,0); a1.id=1; a1.createdAt=100; h.add(a1);
    TezgahPurchases.Purchase a2=p("mehmet afsar","",30,2000,500); a2.id=2; a2.createdAt=200; h.add(a2);
    TezgahPurchases.Purchase a3=p("Mehmet Afşar","",10,1000,0); a3.id=3; a3.createdAt=300; h.add(a3);
    TezgahPurchases.Purchase b1=p("Ali","",10,999,0); b1.id=4; b1.createdAt=150; h.add(b1);
    ok(Math.abs(TezgahSearch.previousDebt(h,"Mehmet Afşar",200,2)-3960)<0.01,"2. fişte önceki borç yalnız 1. fiş");
    ok(Math.abs(TezgahSearch.previousDebt(h,"Mehmet Afşar",300,3)-5460)<0.01,"3. fişte önceki borç 3960+1500");
    ok(TezgahSearch.previousDebt(h,"Mehmet Afşar",100,1)==0,"ilk fişte önceki borç yok");
    ok(Math.abs(TezgahSearch.previousDebt(h,"MEHMET AFŞAR",Long.MAX_VALUE,-1)-6460)<0.01,"kayıttan önce: bütün borç");
    ok(TezgahSearch.previousDebt(h,"Münferit satıcı",Long.MAX_VALUE,-1)==0&&TezgahSearch.previousDebt(h,"",999,9)==0,"münferit/adsız 0");
    TezgahModel fm=new TezgahModel(); fm.add(0,1,22); fm.setPrice(0,100);
    String f2=fm.receipt("Zeytin alım fişi","Satan","Mehmet Afşar","28.10.2026",0,5000);
    ok(f2.contains("Önceki borç: 5.000")&&f2.contains("GÜNCEL BORÇ: 7.000"),"fişte güncel borç: "+f2);
    ok(!fm.receipt("Zeytin alım fişi","Satan","X","",0,0).contains("Önceki"),"önceki borç yoksa yazılmaz");
    String pr=TezgahPrintFormat.receipt(fm,"T","Mehmet","d",0,5000,32);
    ok(pr.contains("GÜNCEL BORÇ")&&pr.contains("7.000"),"basılı fişte güncel borç");
    System.out.println("PASS: "+n+" alım defteri arama kontrolü");
  }
}
