package com.zeytindefteri.app;
import java.util.*;
public class TezgahVoiceTest {
  static int n;
  static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  static TezgahPurchases.Purchase p(long id,String s,String date,double kg,double gross,double paid,long at){
    TezgahPurchases.Purchase x=new TezgahPurchases.Purchase(); x.id=id; x.seller=s; x.date=date; x.kg=kg; x.gross=gross; x.paid=paid; x.createdAt=at; return x; }
  static String t(String s){ return s.replace(' ',' ').replace(' ',' '); }
  public static void main(String[] a){
    List<TezgahPurchases.Purchase> all=new ArrayList<>();
    all.add(p(1,"Volkan Afşar","2026-10-28",67,5290,0,100));
    all.add(p(2,"Mehmet Afşar","2026-10-28",61,3960,3960,200));
    all.add(p(3,"Volkan Afşar","2026-10-27",20,1000,500,50));
    all.add(p(4,"Volkan Yılmaz","2026-10-28",10,700,0,300));
    all.add(p(5,"Ali Veli","2026-10-28",5,300,0,400));
    all.add(p(6,"Münferit satıcı","2026-10-28",8,500,500,500));
    List<TezgahLedger.Entry> led=new ArrayList<>();
    TezgahLedger.Entry e=new TezgahLedger.Entry(); e.party="Hüseyin Tüccar"; e.kind=TezgahLedger.SALE; e.amount=9820; e.createdAt=1; led.add(e);
    List<TezgahCrates.Move> cr=new ArrayList<>();
    TezgahCrates.Move m=new TezgahCrates.Move(); m.party="Hüseyin"; m.kind=TezgahCrates.RETURNED; m.count=100; m.date="2026-10-28"; cr.add(m);
    TezgahCrates.Move m2=new TezgahCrates.Move(); m2.party="Hüseyin"; m2.kind=TezgahCrates.GAVE; m2.count=60; m2.date="2026-10-28"; cr.add(m2);
    String T="2026-10-28",Y="2026-10-27";
    // anlama
    ok(TezgahVoice.kind("Bugün kaç kilo aldım?",all)==TezgahVoice.Kind.DAY,"bugün");
    ok(TezgahVoice.kind("bugün kaç alım yaptım",all)==TezgahVoice.Kind.DAY,"'alım' Ali sanılmaz");
    ok(TezgahVoice.kind("Mehmet Afşar'ın borcu ne kadar",all)==TezgahVoice.Kind.PRODUCER,"üretici borcu");
    ok(TezgahVoice.kind("üreticilere toplam borcum ne",all)==TezgahVoice.Kind.DEBT_TOTAL,"toplam borç");
    ok(TezgahVoice.kind("tüccardan alacağım ne kadar",all)==TezgahVoice.Kind.TRADER,"tüccar");
    ok(TezgahVoice.kind("kasa durumu ne",all)==TezgahVoice.Kind.CRATES,"kasa");
    ok(TezgahVoice.kind("son alım kimdendi",all)==TezgahVoice.Kind.LAST,"son alım");
    ok(TezgahVoice.kind("",all)==TezgahVoice.Kind.HELP&&TezgahVoice.kind("ne sorabilirim",all)==TezgahVoice.Kind.HELP,"yardım");
    ok(TezgahVoice.kind("yarın yağmur var mı",all)==TezgahVoice.Kind.UNKNOWN,"anlamadı");
    // isim eşleştirme
    ok(TezgahVoice.matchSellers("mehmetin borcu",all).equals(Arrays.asList("Mehmet Afşar")),"ad + ek");
    ok(TezgahVoice.matchSellers("Volkan'ın borcu",all).size()==2,"iki Volkan: hangisi sorulur");
    ok(TezgahVoice.matchSellers("volkan afşara ne kadar borcum var",all).equals(Arrays.asList("Volkan Afşar")),"ad soyad + ek");
    ok(TezgahVoice.matchSellers("afşar borcu",all).isEmpty(),"yalnız soyadla eşleşmez");
    ok(TezgahVoice.matchSellers("münferit satıcının borcu",all).isEmpty(),"münferit ad sayılmaz");
    // cevaplar
    String d=t(TezgahVoice.answer("Bugün kaç kilo aldım?",all,led,cr,T,Y).text);
    ok(d.startsWith("Bugün 5 alım yaptın: 151 kg, tutar 10.750 ₺")&&d.contains("borç 6.290 ₺"),"günün özeti: "+d);
    String dy=t(TezgahVoice.answer("dün ne kadar aldım",all,led,cr,T,Y).text);
    ok(dy.startsWith("Dün 1 alım yaptın: 20 kg"),"dün: "+dy);
    ok(TezgahVoice.answer("bugün kaç kilo aldım",all,led,cr,"2026-11-01",Y).text.contains("henüz alım yok"),"alım yok");
    String pr=t(TezgahVoice.answer("Volkan Afşar'ın borcu ne kadar",all,led,cr,T,Y).text);
    ok(pr.contains("toplam borcun 5.790 ₺ (2 fiş)")&&pr.contains("2 alım: 87 kg"),"üretici: "+pr);
    ok(t(TezgahVoice.answer("mehmetin borcu",all,led,cr,T,Y).text).contains("borcun yok"),"ödemiş üretici");
    ok(TezgahVoice.answer("volkanın borcu",all,led,cr,T,Y).text.contains("birden fazla"),"belirsiz ad");
    String dt=t(TezgahVoice.answer("üreticilere toplam borcum",all,led,cr,T,Y).text);
    ok(dt.contains("toplam 6.790 ₺")&&dt.indexOf("Volkan Afşar")<dt.indexOf("Volkan Yılmaz"),"toplam borç sırayla: "+dt);
    ok(t(TezgahVoice.answer("tüccardan alacağım",all,led,cr,T,Y).text).contains("9.820 ₺"),"tüccar alacağı");
    String k=TezgahVoice.answer("kasa durumu",all,led,cr,T,Y).text;
    ok(k.contains("100 kasa geldi, 60 kasa gitti")&&k.contains("40 kasası sende"),"kasa: "+k);
    ok(TezgahVoice.answer("son alım",all,led,cr,T,Y).text.contains("Münferit satıcı"),"son alım");
    TezgahVoice.Answer h=TezgahVoice.answer("hava nasıl",all,led,cr,T,Y);
    ok(h.kind==TezgahVoice.Kind.HELP&&h.text.contains("Bunu anlayamadım"),"anlamayınca örnekler");
    ok(t(TezgahVoice.answer("bugün kaç kilo",all,led,cr,T,Y).speech()).contains("lira")&&!TezgahVoice.answer("bugün kaç kilo",all,led,cr,T,Y).speech().contains("₺"),"sesli okumada lira");
    ok(TezgahVoice.answer("kasa",new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),T,Y).text.contains("henüz kayıt yok"),"boş kasa");
    System.out.println("PASS: "+n+" tezgâh sorusu kontrolü");
  }
}
