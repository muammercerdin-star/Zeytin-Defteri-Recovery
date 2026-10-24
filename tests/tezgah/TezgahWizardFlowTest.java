package com.zeytindefteri.app;
import java.util.Arrays;
import java.util.List;
public class TezgahWizardFlowTest {
  static int n;
  static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  public static void main(String[] a){
    TezgahModel m=new TezgahModel();
    ok(TezgahWizardFlow.start("",m)==TezgahWizardFlow.SELLER,"satan yoksa önce satan sorulur");
    ok(TezgahWizardFlow.start("  ",m)==TezgahWizardFlow.SELLER,"boşluk satan sayılmaz");
    ok(TezgahWizardFlow.start("Ahmet",m)==0,"satan varsa Double");
    m.add(0,2,60); m.add(1,1,30);
    ok(TezgahWizardFlow.start("Ahmet",m)==2,"devamda tartımı olmayan ilk numara");
    ok(TezgahWizardFlow.next(TezgahWizardFlow.SELLER)==0,"satandan Double'a");
    ok(TezgahWizardFlow.next(5)==TezgahWizardFlow.DONE,"Yağlık'tan ödemeye");
    ok(TezgahWizardFlow.next(TezgahWizardFlow.DONE)==TezgahWizardFlow.DONE,"sonda kalır");
    ok(TezgahWizardFlow.back(0)==TezgahWizardFlow.SELLER,"Double'dan geri satana");
    ok(TezgahWizardFlow.back(TezgahWizardFlow.SELLER)==TezgahWizardFlow.SELLER,"satandan geri yok");
    ok(TezgahWizardFlow.progress(0).equals("1 / 6"),"adım yazısı");
    ok(TezgahWizardFlow.progress(TezgahWizardFlow.SELLER).isEmpty(),"satan adımında yazı yok");
    ok(TezgahWizardFlow.sellerOrWalkIn(" ").equals("Münferit satıcı"),"boş ad münferit");
    ok(TezgahWizardFlow.sellerOrWalkIn(" Veli ").equals("Veli"),"ad kırpılır");
    List<String> r=TezgahWizardFlow.recent(Arrays.asList("Veli","","veli","Münferit satıcı","Ali","Hasan","Ayşe","Can"),4);
    ok(r.equals(Arrays.asList("Veli","Ali","Hasan","Ayşe")),"son satıcılar tekrarsız, münferitsiz: "+r);
    System.out.println("PASS: "+n+" tartım sihirbazı kontrolü");
  }
}
