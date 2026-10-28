package com.zeytindefteri.app;
import android.database.sqlite.SQLiteDatabase;
import java.util.List;
public class TezgahCratesTest {
  static int n;
  static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  public static void main(String[] a){
    // saf kurallar
    ok(TezgahCrates.key("  Volkan   AFŞAR ").equals("volkan afsar"),"ad anahtarı Türkçe harfsiz, tek boşluk");
    ok(!TezgahCrates.validParty(""),"boş ad olmaz");
    ok(!TezgahCrates.validParty("münferit SATICI"),"münferit satıcıya kasa yazılmaz");
    ok(TezgahCrates.validParty("Ali"),"ad geçerli");
    ok(!TezgahCrates.countError(0).isEmpty(),"0 kasa olmaz");
    ok(!TezgahCrates.countError(TezgahCrates.MAX_COUNT+1).isEmpty(),"üst sınır");
    ok(TezgahCrates.countError(40).isEmpty(),"40 kasa geçerli");
    ok(TezgahCrates.suggestReturned(20,12)==12,"tartılan kasa kadar geri gelir");
    ok(TezgahCrates.suggestReturned(5,12)==5,"bakiyeyi aşmaz");
    ok(TezgahCrates.suggestReturned(0,12)==0,"kasam onda değilse 0");
    ok(TezgahCrates.balanceLine("Veli",3).equals("Veli · 3 kasan onda"),"bakiye satırı");
    ok(TezgahCrates.balanceLine("Veli",-2).equals("Veli · 2 kasası sende"),"eksi bakiye satırı");
    ok(TezgahCrates.reminder("Veli",3,"Ali Tüccar").contains("3 boş kasamız"),"hatırlatma metni");

    SQLiteDatabase db=new SQLiteDatabase(a[0],a[1]);
    String today="2026-10-28";
    ok(TezgahCrates.add(db,"t1","Münferit satıcı",TezgahCrates.GAVE,5,today,0,"",1)==-1,"münferite kayıt yok");
    ok(TezgahCrates.add(db,"t1","Volkan",TezgahCrates.GAVE,0,today,0,"",1)==-1,"0 adet kayıt yok");
    ok(TezgahCrates.add(db,"t1","Volkan",9,3,today,0,"",1)==-1,"bilinmeyen yön kayıt yok");
    ok(TezgahCrates.add(db,"t1","Volkan Afşar",TezgahCrates.GAVE,50,"2026-10-23",0,"hasat",100)>0,"verdim");
    ok(TezgahCrates.add(db,"t1","volkan  afsar",TezgahCrates.RETURNED,30,today,0,"",200)>0,"geri geldi (ad farklı yazılmış)");
    ok(TezgahCrates.add(db,"t1","Hasan",TezgahCrates.GAVE,10,today,0,"",300)>0,"Hasan'a verdim");
    ok(TezgahCrates.add(db,"t1","Tüccar Mehmet",TezgahCrates.RETURNED,8,today,0,"",400)>0,"tüccarın kasası geldi");
    long linked=TezgahCrates.add(db,"t1","Hasan",TezgahCrates.RETURNED,4,today,77,"",500);
    ok(linked>0,"alımdan geri gelen");
    TezgahCrates.add(db,"t2","Volkan Afşar",TezgahCrates.GAVE,999,today,0,"",600);
    ok(TezgahCrates.balanceOf(db,"t1","VOLKAN AFŞAR")==20,"Volkan'da 20 kasa");
    ok(TezgahCrates.balanceOf(db,"t1","Hasan")==6,"Hasan'da 6 kasa");
    ok(TezgahCrates.balanceOf(db,"t1","Münferit satıcı")==0,"münferit bakiye 0");
    List<TezgahCrates.Move> all=TezgahCrates.moves(db,"t1");
    ok(all.size()==5,"yalnız bu hesabın hareketleri: "+all.size());
    ok(all.get(0).id==linked,"yeniden eskiye");
    List<TezgahCrates.Party> ps=TezgahCrates.parties(all);
    ok(ps.size()==3,"3 kişi");
    ok(ps.get(0).name.equals("volkan  afsar".trim().replaceAll("\\s+"," ")) || ps.get(0).key.equals("volkan afsar"),"en çok kasası dışarıda olan başta");
    ok(ps.get(0).balance()==20 && ps.get(1).balance()==6,"sıra: çoktan aza");
    ok(ps.get(2).balance()==-8,"kasası bende olan sonra");
    ok(ps.get(0).lastDate.equals(today),"son hareket günü");
    TezgahCrates.Summary s=TezgahCrates.summary(all,today);
    ok(s.out==26,"dışarıda 26 kasa: "+s.out);
    ok(s.people==2,"2 kişide");
    ok(s.held==8,"bende 8 başkasının kasası");
    ok(s.todayGave==10 && s.todayReturned==42,"bugün 10 verildi, 42 geldi: "+s.todayGave+"/"+s.todayReturned);
    ok(s.inHand(100)==74,"elde 74");
    ok(s.inHand(0)==-1,"toplam bilinmiyorsa elde yok");
    ok(s.inHand(10)==0,"elde eksiye düşmez");
    TezgahCrates.deleteFor(db,"t1",77);
    ok(TezgahCrates.balanceOf(db,"t1","Hasan")==10,"alım silinince bağlı kasa hareketi silinir");
    ok(!TezgahCrates.delete(db,"t2",all.get(1).id),"başkasının hareketi silinmez");
    ok(TezgahCrates.delete(db,"t1",all.get(1).id),"hareket silinir");
    ok(TezgahCrates.movesOf(db,"t1","Tüccar Mehmet").isEmpty(),"silindi");
    System.out.println("PASS: "+n+" kasa defteri kontrolü");
  }
}
