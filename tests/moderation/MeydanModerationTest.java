package com.zeytindefteri.app;
public class MeydanModerationTest {
  static int n; static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  public static void main(String[] a){
    ok(MeydanModeration.status(null).equals("ACTIVE"),"eski gönderi ACTIVE");
    ok(MeydanModeration.status("PENDING").equals("PENDING") && MeydanModeration.status("garip").equals("ACTIVE"),"durum okuma");
    ok(MeydanModeration.reports(3L)==3 && MeydanModeration.reports("x")==0 && MeydanModeration.reports(-2)==0,"sayaç okuma");
    ok(MeydanModeration.visible("ACTIVE",0,false),"normal mesaj görünür");
    ok(!MeydanModeration.visible("PENDING",0,false) && MeydanModeration.visible("PENDING",0,true),"onay bekleyen yalnız sahibine");
    ok(!MeydanModeration.visible("HIDDEN",0,true),"gizlenen sahibine de görünmez");
    ok(!MeydanModeration.visible("ACTIVE",3,false) && MeydanModeration.visible("ACTIVE",2,false),"3 şikâyette gizlenir");
    ok(MeydanModeration.commentVisible("ACTIVE",2) && !MeydanModeration.commentVisible("HIDDEN",0) && !MeydanModeration.commentVisible("ACTIVE",3),"yanıtlar");
    ok(MeydanModeration.newPostStatus(0,false).equals("PENDING") && MeydanModeration.newPostStatus(2,false).equals("PENDING"),"yeni üye onaya");
    ok(MeydanModeration.newPostStatus(3,false).equals("ACTIVE"),"güvenilir üye");
    ok(MeydanModeration.newPostStatus(0,true).equals("ACTIVE"),"moderatör onaysız");
    ok(MeydanModeration.ownerLabel("PENDING").equals("Onay bekliyor") && MeydanModeration.ownerLabel("ACTIVE").isEmpty(),"etiket");
    long now=1_000_000_000L;
    ok(MeydanModeration.muteMessage(now-1,now).isEmpty(),"bitmiş susturma");
    ok(MeydanModeration.muteMessage(now+3*3600_000L,now).contains("3 saatliğine"),"saatlik");
    ok(MeydanModeration.muteMessage(now+MeydanModeration.MUTE_MS[1],now).contains("7 günlüğüne"),"haftalık");
    ok(MeydanModeration.muteMessage(now+MeydanModeration.MUTE_MS[3],now).contains("kapatıldı"),"süresiz");
    System.out.println("PASS: "+n+" moderasyon kararı kontrolü");
  }
}
