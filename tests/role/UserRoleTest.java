package com.zeytindefteri.app;
public class UserRoleTest {
  static int n;
  static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  public static void main(String[] a){
    ok(UserRole.parse(" TRADER ").equals("trader"),"büyük harf/boşluk");
    ok(UserRole.parse("x").isEmpty()&&UserRole.parse(null).isEmpty(),"bilinmeyen boş");
    ok(!UserRole.chosen("")&&UserRole.chosen("both"),"seçildi mi");
    ok(UserRole.opensTezgah("trader","garden",false),"tezgâhçı hep tezgâhta açılır");
    ok(!UserRole.opensTezgah("trader","tezgah",true),"özel ekran istenince yönlendirilmez");
    ok(UserRole.opensTezgah("both","tezgah",false)&&!UserRole.opensTezgah("both","garden",false),"ikisi de: en son kaldığı yer");
    ok(!UserRole.opensTezgah("both","",false),"ikisi de, ilk: bahçe");
    ok(!UserRole.opensTezgah("producer","tezgah",false)&&!UserRole.opensTezgah("","tezgah",false),"üretici ve seçilmemiş: bahçe");
    ok(UserRole.hasTezgah("both")&&UserRole.hasTezgah("trader")&&!UserRole.hasTezgah("producer"),"tezgâh var mı");
    ok(UserRole.hasGarden("")&&UserRole.hasGarden("both")&&!UserRole.hasGarden("trader"),"bahçe var mı");
    ok(!UserRole.needsFirstGarden("trader")&&UserRole.needsFirstGarden("producer"),"tezgâhçıya bahçe zorunlu değil");
    ok(UserRole.label("both").equals("Üretici ve tezgâhçı")&&UserRole.label("").equals("Seçilmedi"),"etiket");
    System.out.println("PASS: "+n+" kullanım şekli kontrolü");
  }
}
