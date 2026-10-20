package com.zeytindefteri.app;
public class GardenDeletionTest {
  static int n; static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  public static void main(String[] a){
    ok(GardenDeletion.nameMatches(" Edremit ","edremit"),"büyük/küçük ve boşluk");
    ok(GardenDeletion.nameMatches("İZMİR BAHÇE","izmir bahçe"),"Türkçe İ/i");
    ok(!GardenDeletion.nameMatches("Edremi","Edremit"),"eksik ad");
    ok(!GardenDeletion.nameMatches("",""),"boş ad eşleşmez");
    ok(!GardenDeletion.nameMatches(null,"x"),"null");
    ok(GardenDeletion.summary(new int[]{12,3,5,4}).equals("12 işlem · 3 hasat · 5 elek · 4 satış"),"özet");
    ok(GardenDeletion.summary(new int[]{0,0,2,0}).equals("2 elek"),"yalnız olan");
    ok(GardenDeletion.summary(new int[]{0,0,0,0}).equals("kayıt yok"),"boş");
    System.out.println("PASS: "+n+" bahçe silme kontrolü");
  }
}
