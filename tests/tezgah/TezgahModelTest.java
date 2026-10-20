package com.zeytindefteri.app;
public class TezgahModelTest {
  static int n;
  static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  static void eq(double a,double b,String m){ ok(Math.abs(a-b)<1e-6, m+": "+a+" != "+b); }
  public static void main(String[] x){
    eq(TezgahModel.net(10,258,2),238,"10 kasa 258 kg -> 238");
    eq(TezgahModel.net(0,500,2),500,"dökme");
    eq(TezgahModel.net(5,8,2),0,"eksiye düşmez");
    ok(TezgahModel.addError(10,258,2).isEmpty(),"geçerli tartım");
    ok(!TezgahModel.addError(10,0,2).isEmpty(),"kilo yok");
    ok(!TezgahModel.addError(10,20,2).isEmpty(),"dara kilodan büyük");
    ok(!TezgahModel.addError(-1,20,2).isEmpty(),"eksi kasa");
    ok(!TezgahModel.addError(1,30000,2).isEmpty(),"aşırı kilo");

    TezgahModel m=new TezgahModel();
    ok(m.isEmpty(),"boş başlar");
    eq(m.tare(),2,"varsayılan dara 2");
    ok(m.add(0,10,258),"double tartım");
    ok(m.add(0,8,205.5),"double tartım 2");
    ok(m.add(1,12,310),"1 numara");
    ok(!m.add(1,12,20),"hatalı eklenmez");
    ok(!m.add(9,1,30),"geçersiz numara");
    eq(m.gradeNet(0),238+189.5,"double net");
    eq(m.gradeKasa(0),18,"double kasa");
    ok(m.gradeCount(0)==2,"double tartım sayısı");
    eq(m.gradeNet(1),286,"1 numara net");
    eq(m.totalNet(),713.5,"toplam net");
    eq(m.totalGross(),773.5,"brüt");
    ok(m.totalKasa()==30,"toplam kasa");
    eq(m.totalTare(),60,"toplam dara");
    m.setPrice(0,100); m.setPrice(1,90);
    eq(m.total(),427.5*100+286*90,"toplam tutar");
    eq(Math.round(m.share(0)),60,"double payı");
    ok(m.missingPrices().isEmpty(),"eksik fiyat yok");
    m.add(5,2,60);
    ok(m.missingPrices().size()==1 && m.missingPrices().get(0).equals("Yağlık"),"yağlık fiyatsız");
    double[] net=m.netArray(); eq(net[5],56,"yağlık net"); eq(net[2],0,"2 numara yok");

    m.setTare(1.5); eq(m.gradeNet(0),427.5+18*0.5,"dara değişince yeniden hesap");
    m.setTare(-1); eq(m.tare(),1.5,"eksi dara reddedilir");
    m.setTare(50); eq(m.tare(),1.5,"aşırı dara reddedilir");

    TezgahModel back=TezgahModel.decode(m.encode());
    eq(back.totalNet(),m.totalNet(),"saklama: net");
    eq(back.total(),m.total(),"saklama: tutar");
    eq(back.tare(),1.5,"saklama: dara");
    ok(back.items().size()==4,"saklama: tartımlar");
    ok(TezgahModel.decode("bozuk").isEmpty(),"bozuk metin boş");
    ok(TezgahModel.decode("v1|x|1|0,1,2").isEmpty(),"bozuk sayı boş");
    ok(TezgahModel.decode(null).isEmpty(),"null boş");

    ok(m.remove(3),"silme"); ok(!m.remove(9),"olmayan silinmez"); ok(m.items().size()==3,"3 kaldı");

    eq(TezgahModel.parse("258"),258,"tam");
    eq(TezgahModel.parse("25,5"),25.5,"virgül");
    eq(TezgahModel.parse("25.5"),25.5,"nokta");
    eq(TezgahModel.parse("1.250"),1250,"bin ayırıcı");
    eq(TezgahModel.parse("1.250,5"),1250.5,"tr tam");
    eq(TezgahModel.parse("1,250.5"),1250.5,"en tam");
    eq(TezgahModel.parse("abc"),0,"okunamaz");
    eq(TezgahModel.parse(" 92,5 ₺"),92.5,"birimli");
    ok(TezgahModel.kg(1234.5).equals("1.234,5 kg"),"kg biçimi "+TezgahModel.kg(1234.5));
    ok(TezgahModel.tl(48350).equals("48.350 ₺"),"tl biçimi");
    ok(TezgahModel.percent(37.6).equals("%38"),"yüzde");

    TezgahModel r=new TezgahModel(); r.add(0,10,258); r.setPrice(0,100);
    String fis=r.receipt("Zeytin alım fişi","Satan","Ahmet Yılmaz","20.10.2026",10000);
    ok(fis.contains("Satan: Ahmet Yılmaz"),"fiş: taraf");
    ok(fis.contains("Double: 238 kg × 100 ₺ = 23.800 ₺"),"fiş: satır\n"+fis);
    ok(fis.contains("Kalan: 13.800"),"fiş: kalan");
    ok(r.receipt("x","Alıcı","","",23800).contains("tamamı ödendi"),"fiş: tamamı");
    ok(r.receipt("x","Alıcı","","",0).contains("henüz yapılmadı"),"fiş: ödenmedi");
    ok(r.line(r.items().get(0)).equals("10 kasa · 258 kg  →  net 238 kg"),"satır metni");
    ok(TezgahModel.waPhone("0532 123 45 67").equals("905321234567"),"wa 0'lı");
    ok(TezgahModel.waPhone("532-123-4567").equals("905321234567"),"wa 10 hane");
    ok(TezgahModel.waPhone("+90 532 123 45 67").equals("905321234567"),"wa +90");
    ok(TezgahModel.waPhone("+49 151 2345678").equals("491512345678"),"wa yurt dışı");
    ok(TezgahModel.waPhone("0266 123 45 67").isEmpty(),"sabit hat yok");
    ok(TezgahModel.waPhone("").isEmpty(),"boş");
    ok(TezgahModel.suggestedKasa(1,38,2)==2,"1 kasa 38 kg -> 2");
    ok(TezgahModel.suggestedKasa(1,32,2)==0,"1 kasa 32 kg sığar (30+dara)");
    ok(TezgahModel.suggestedKasa(1,258,2)==10,"1 kasa 258 kg -> 10");
    ok(TezgahModel.suggestedKasa(10,258,2)==0,"10 kasa 258 kg normal");
    ok(TezgahModel.suggestedKasa(8,320,2)==12,"8 kasa 320 kg -> 12");
    ok(TezgahModel.suggestedKasa(0,500,2)==0,"dökme uyarılmaz");
    ok(TezgahModel.suggestedKasa(1,90000,2)==TezgahModel.MAX_KASA,"üst sınır");
    ok(TezgahModel.kasaWarning(1,38,2).contains("Kasa sayısı 2"),"uyarı metni "+TezgahModel.kasaWarning(1,38,2));
    ok(TezgahModel.kasaWarning(2,38,2).isEmpty(),"uyarı yok");
    TezgahModel av=new TezgahModel();
    eq(av.averagePrice(),0,"boşta ortalama 0");
    av.add(0,1,8); av.add(1,1,20); av.add(5,1,10); av.setPrice(0,130); av.setPrice(1,100); av.setPrice(5,20);
    eq(av.averagePrice(),(6*130+18*100+8*20)/32.0,"ortalama kg fiyatı");
    ok(av.receipt("x","Satan","","",0).contains("Ortalama: 85,63\u00A0₺/kg"),"fişte ortalama\n"+av.receipt("x","Satan","","",0));
    ok(TezgahModel.suggestedKasa(2,16,2)==1,"2 kasa 16 kg -> 1");
    ok(TezgahModel.tooManyKasa(2,16,2),"kasa fazla girilmiş");
    ok(TezgahModel.kasaHint(2,16,2).equals("⚠ 2 kasa için 16\u00A0kg az. 1 kasa mı?"),"kısa uyarı "+TezgahModel.kasaHint(2,16,2));
    ok(TezgahModel.kasaWarning(2,16,2).contains("Kasa sayısı 1"),"uzun uyarı");
    ok(TezgahModel.suggestedKasa(10,200,2)==0,"10 kasa 200 kg normal (son kasa yarım)");
    ok(TezgahModel.suggestedKasa(10,150,2)==6,"10 kasa 150 kg -> 6: "+TezgahModel.suggestedKasa(10,150,2));
    ok(TezgahModel.suggestedKasa(1,5,2)==0,"1 kasa 5 kg yarım kasa normal");
    ok(TezgahModel.suggestedKasa(2,24,2)==0,"2 kasa 24 kg sınırda normal");
    ok(TezgahModel.kasaHint(1,38,2).equals("⚠ 1 kasaya 38\u00A0kg fazla. 2 kasa mı?"),"fazla uyarı korunur");
    System.out.println("PASS: "+n+" tezgah hesabı kontrolü");
  }
}
