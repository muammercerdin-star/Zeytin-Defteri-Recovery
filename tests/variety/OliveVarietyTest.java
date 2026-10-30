package com.zeytindefteri.app;
public class OliveVarietyTest {
  static int n;
  static void eq(String a,String b,String m){ if(!a.equals(b)) throw new AssertionError(m+": '"+a+"' != '"+b+"'"); n++; }
  public static void main(String[] x){
    eq(OliveVariety.normalize("Gemlik"),"Gemlik","gemlik");
    eq(OliveVariety.normalize("trilye"),"Gemlik","trilye");
    eq(OliveVariety.normalize("TRİLYA"),"Gemlik","büyük harf trilya");
    eq(OliveVariety.normalize("Gemlik siyahı"),"Gemlik","gemlik siyahı");
    eq(OliveVariety.normalize("gemlik zeytini 2"),"Gemlik","ekli yazım");
    eq(OliveVariety.normalize("Gemlik / Trilye"),"Gemlik","iki isim");
    eq(OliveVariety.normalize("Edremit"),"Ayvalık","edremit = ayvalık");
    eq(OliveVariety.normalize("ada zeytini"),"Ayvalık","ada zeytini");
    eq(OliveVariety.normalize("Edremit yağlık"),"Ayvalık","edremit yağlık");
    eq(OliveVariety.normalize("taşarası"),"Memecik","taşarası");
    eq(OliveVariety.normalize("domat"),"Domat","domat");
    eq(OliveVariety.normalize("celebi"),"Çelebi","çelebi düzeltilir");
    eq(OliveVariety.normalize("nizip"),"Nizip Yağlık","nizip");
    eq(OliveVariety.normalize("  "),"","boş");
    eq(OliveVariety.normalize("istanbul karası"),"İstanbul Karası","tanınmayan: baş harfler (Türkçe İ)");
    eq(OliveVariety.apiKey("Trilye"),"gemlik","api anahtarı");
    eq(OliveVariety.apiKey("Edremit"),"ayvalik","api ayvalık");
    eq(OliveVariety.apiKey(""),"","api boş");
    eq(OliveVariety.label("ayvalik"),"Ayvalık","etiket");
    eq(OliveVariety.label(""),"Belirtilmemiş","etiket boş");
    eq(OliveVariety.label("nizipyaglik"),"Nizip Yağlık","etiket iki kelime");
    if(!OliveVariety.CHOICES.get(OliveVariety.CHOICES.size()-1).equals(OliveVariety.OTHER)) throw new AssertionError("Diğer sonda"); n++;
    System.out.println("PASS: "+n+" zeytin cinsi kontrolü");
  }
}
