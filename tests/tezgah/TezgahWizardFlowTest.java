package com.zeytindefteri.app;
import java.util.Arrays;
import java.util.List;
public class TezgahWizardFlowTest {
  static int n;
  static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  public static void main(String[] a){
    TezgahModel m=new TezgahModel();
    ok(TezgahWizardFlow.start(m)==0,"boş tezgahta Double ile başlar");
    m.add(0,2,60); m.add(1,1,30);
    ok(TezgahWizardFlow.start(m)==2,"devamda tartımı olmayan ilk numara");
    ok(TezgahWizardFlow.next(5)==TezgahWizardFlow.SELLER,"Yağlık'tan sonra satan sorulur");
    ok(TezgahWizardFlow.next(TezgahWizardFlow.SELLER)==TezgahWizardFlow.DONE,"satandan ödemeye");
    ok(TezgahWizardFlow.next(TezgahWizardFlow.DONE)==TezgahWizardFlow.DONE,"sonda kalır");
    ok(TezgahWizardFlow.back(TezgahWizardFlow.SELLER)==5,"satandan geri Yağlık'a");
    ok(TezgahWizardFlow.back(0)==0,"Double'dan geri yok");
    ok(TezgahWizardFlow.progress(0).equals("1 / 7"),"adım yazısı");
    ok(TezgahWizardFlow.progress(TezgahWizardFlow.SELLER).equals("Son adım"),"satan son adım");
    ok(TezgahWizardFlow.sellerOrWalkIn(" ").equals("Münferit satıcı"),"boş ad münferit");
    ok(TezgahWizardFlow.sellerOrWalkIn(" Veli ").equals("Veli"),"ad kırpılır");
    List<String> r=TezgahWizardFlow.recent(Arrays.asList("Veli","","veli","Münferit satıcı","Ali","Hasan","Ayşe","Can"),4);
    ok(r.equals(Arrays.asList("Veli","Ali","Hasan","Ayşe")),"son satıcılar tekrarsız, münferitsiz: "+r);
    long now=10_000_000L;
    ok(TezgahWizardFlow.slipOpenLeft(now,now)==TezgahWizardFlow.SLIP_OPEN_MS,"yeni fiş açık");
    ok(TezgahWizardFlow.slipOpenLeft(now-60_000,now)==TezgahWizardFlow.SLIP_OPEN_MS-60_000,"1 dk sonra kalan süre");
    ok(TezgahWizardFlow.slipOpenLeft(now-TezgahWizardFlow.SLIP_OPEN_MS,now)==0,"3 dk dolunca kapanır");
    ok(TezgahWizardFlow.slipOpenLeft(now-3_600_000,now)==0,"eski fiş kapalı");
    ok(TezgahWizardFlow.slipOpenLeft(0,now)==0,"zamanı bilinmeyen fiş kapalı");
    ok(TezgahWizardFlow.slipOpenLeft(now+5_000,now)==TezgahWizardFlow.SLIP_OPEN_MS,"ileri tarihli kayıt yeni sayılır");
    System.out.println("PASS: "+n+" tartım sihirbazı kontrolü");
  }
}
