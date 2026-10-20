package com.zeytindefteri.app;
public class TezgahQrTest {
  static int n;
  static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  static void eq(double a,double b,String m){ ok(Math.abs(a-b)<1e-6, m+": "+a+" != "+b); }
  public static void main(String[] x){
    TezgahModel m=new TezgahModel();
    m.add(0,1,5); m.add(1,1,28); m.add(2,2,46.5); m.add(5,1,5);
    m.setPrice(0,130); m.setPrice(1,100); m.setPrice(2,70.5); m.setPrice(5,20);
    String q=TezgahQr.encode(m,"2026-10-20","Hasan | Usta\n",5000);
    ok(q.startsWith("ZD1|2026-10-20|Hasan Usta|"),"başlık ve temiz ad: "+q);
    ok(q.length()<120,"kısa QR metni: "+q.length());
    TezgahQr.Slip s=TezgahQr.decode(q);
    ok(s!=null,"çözülür");
    ok(s.date.equals("2026-10-20"),"tarih");
    ok(s.buyer.equals("Hasan Usta"),"alıcı");
    eq(s.kg[0],3,"double kg"); eq(s.kg[1],26,"1 kg"); eq(s.kg[2],42.5,"2 kg (ondalık)"); eq(s.kg[3],0,"3 yok"); eq(s.kg[5],3,"yağlık");
    eq(s.price[2],70.5,"ondalık fiyat");
    eq(s.total(),m.total(),"tutar aynı");
    eq(s.totalKg(),m.totalNet(),"kilo aynı");
    ok(s.kasa==5,"kasa"); eq(s.tare,2,"dara"); eq(s.paid,5000,"ödenen");
    ok(s.payChoice()==ElekSaleModel.PAY_PART,"kısmi ödeme");
    ok(TezgahQr.decode(TezgahQr.encode(m,"2026-10-20","",0)).payChoice()==ElekSaleModel.PAY_NONE,"ödenmedi");
    ok(TezgahQr.decode(TezgahQr.encode(m,"2026-10-20","",999999)).payChoice()==ElekSaleModel.PAY_ALL,"tamamı (fazlası kırpılır)");
    ok(TezgahQr.decode("https://example.com")==null,"başka QR");
    ok(TezgahQr.decode("ZD1|2026-10-20|x|1,2|1,2|1|2|0")==null,"eksik numara");
    ok(TezgahQr.decode("ZD1|20-10-2026|x|1,0,0,0,0,0|1,0,0,0,0,0|1|2|0")==null,"bozuk tarih");
    ok(TezgahQr.decode("ZD1|2026-10-20|x|0,0,0,0,0,0|1,0,0,0,0,0|1|2|0")==null,"kilosuz");
    ok(TezgahQr.decode("ZD1|2026-10-20|x|-1,5,0,0,0,0|1,0,0,0,0,0|1|2|0")==null,"eksi kilo");
    ok(TezgahQr.decode("ZD1|2026-10-20|x|a,5,0,0,0,0|1,0,0,0,0,0|1|2|0")==null,"harf");
    ok(TezgahQr.decode(null)==null,"null");
    StringBuilder longName=new StringBuilder(); for(int i=0;i<100;i++) longName.append('a');
    ok(TezgahQr.decode(TezgahQr.encode(m,"2026-10-20",longName.toString(),0)).buyer.length()==60,"ad kısaltılır");
    ok(TezgahQr.field(26).equals("26"),"alan tam"); ok(TezgahQr.field(42.5).equals("42,5"),"alan ondalık"); ok(TezgahQr.field(0).isEmpty(),"alan boş");
    System.out.println("PASS: "+n+" QR fiş kontrolü");
  }
}
