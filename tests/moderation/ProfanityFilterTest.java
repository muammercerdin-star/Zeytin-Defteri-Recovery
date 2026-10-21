package com.zeytindefteri.app;
public class ProfanityFilterTest {
  static int n;
  static void bad(String s){ if(ProfanityFilter.isClean(s)) throw new AssertionError("yakalanmalıydı: "+s); n++; }
  static void ok(String s){ ProfanityFilter.Result r=ProfanityFilter.check(s); if(!r.clean) throw new AssertionError("yanlış alarm: "+s+" -> "+r.word); n++; }
  public static void main(String[] a){
    bad("amk"); bad("AMK bu ne fiyat"); bad("aq"); bad("s.i.k"); bad("siiiik"); bad("S1K"); bad("s i k t i r");
    bad("siktir git"); bad("orospu çocuğu"); bad("0r0spu"); bad("pezevenk"); bad("kahpe"); bad("yavşak"); bad("yavsak");
    bad("şerefsiz adam"); bad("serefsizler"); bad("ibne"); bad("göt"); bad("götveren"); bad("piç"); bad("yarrak"); bad("y4rr4k");
    bad("amına koyim"); bad("aminakoyim"); bad("ananı"); bad("anasını"); bad("sen mal mısın"); bad("mal herif"); bad("o.ç");
    ok("merhaba arkadaşlar herkese hayırlı satışlar"); ok("Teşekkür ederim"); ok("bugün sıkıntı var mı");
    ok("yol çok sıkışık"); ok("şikayet ettim"); ok("zeytini götürdüm"); ok("götürmek lazım"); ok("Âmin"); ok("amin");
    ok("malı tezgaha götürdüm"); ok("mal sahibi geldi"); ok("bu sene mal az"); ok("Sıkı tutun"); ok("anan nasıl");
    ok("Double 130 TL, 1 numara 100"); ok("ilaçlama yaptım"); ok("kasa başına 25 kg"); ok("gotik"); ok(""); ok(null);
    ok("Pazartesi Alaşehir'de piyasa nasıl?"); ok("Sıkma yağı"); ok("pikap"); ok("kahve içelim");
    if(!ProfanityFilter.message().contains("uygunsuz")) throw new AssertionError("mesaj");
    System.out.println("PASS: "+n+" küfür filtresi kontrolü");
  }
}
