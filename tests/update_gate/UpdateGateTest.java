package com.zeytindefteri.app;
public class UpdateGateTest {
  static int n; static void eq(int a,int b,String m){ if(a!=b) throw new AssertionError(m+": "+a+" != "+b); n++; }
  public static void main(String[] x){
    eq(UpdateGate.decide(1,0,0),UpdateGate.NONE,"ayar okunamadı -> kilit yok");
    eq(UpdateGate.decide(1,1,1),UpdateGate.NONE,"güncel");
    eq(UpdateGate.decide(1,1,2),UpdateGate.OPTIONAL,"yeni sürüm var");
    eq(UpdateGate.decide(1,2,2),UpdateGate.REQUIRED,"zorunlu");
    eq(UpdateGate.decide(3,2,5),UpdateGate.OPTIONAL,"min üstü, latest altı");
    eq(UpdateGate.decide(5,2,5),UpdateGate.NONE,"en yeni");
    eq(UpdateGate.decide(0,9,9),UpdateGate.NONE,"sürüm bilinmiyor -> kilit yok");
    System.out.println("PASS: "+n+" güncelleme kararı kontrolü");
  }
}
