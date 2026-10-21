package com.zeytindefteri.app;
import java.util.*;
public class AlertRulesTest {
  static int n; static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  static List<AlertRules.Day> days(double... v){ List<AlertRules.Day> l=new ArrayList<>(); for(int i=0;i<v.length;i+=3) l.add(new AlertRules.Day("2026-10-"+(21+i/3),v[i],v[i+1],v[i+2])); return l; }
  static List<AlertRules.Hour> calm(String day,double wind,double rain){ List<AlertRules.Hour> l=new ArrayList<>(); for(int h=0;h<24;h++) l.add(new AlertRules.Hour(day+"T"+(h<10?"0":"")+h+":00",wind,rain)); return l; }
  public static void main(String[] a){
    List<AlertRules.Hour> none=new ArrayList<>();
    ok(AlertRules.weather(1,"Girelli","2026-10-21",9,10,days(10,22,0, 12,24,10, 13,25,0),none)==null,"normal hava: uyarı yok");
    AlertRules.Alert f=AlertRules.weather(1,"Girelli","2026-10-21",9,10,days(10,22,0, 1,14,0, 5,15,0),none);
    ok(f!=null && f.level==3 && f.key.equals("frost:1:2026-10-22"),"don: "+f);
    ok(f.body.startsWith("Bu gece (sabaha karşı) Girelli'de sıcaklık 1°C"),"don metni "+f.body);
    AlertRules.Alert f2=AlertRules.weather(1,"","2026-10-21",9,10,days(10,22,0, 5,14,0, -2,9,0),none);
    ok(f2.body.startsWith("Yarın gece Bahçende"),"yarın gece don, adsız bahçe "+f2.body);
    AlertRules.Alert r=AlertRules.weather(2,"Edremit","2026-10-21",9,11,days(10,22,0, 12,20,85, 13,25,0),none);
    ok(r!=null && r.key.equals("rain:2:2026-10-22") && r.body.contains("%85"),"hasatta yağmur "+r);
    ok(AlertRules.weather(2,"E","2026-10-21",9,7,days(10,22,0, 12,20,85, 13,25,0),none)==null,"temmuzda yağmur uyarısı yok");
    AlertRules.Alert both=AlertRules.weather(3,"X","2026-10-21",9,11,days(10,22,0, 0,10,90, 13,25,0),none);
    ok(both.level==3,"don + yağmur: en önemlisi (don) seçilir");
    List<AlertRules.Hour> storm=calm("2026-10-21",15,0); storm.set(17,new AlertRules.Hour("2026-10-21T17:00",52,0));
    AlertRules.Alert s=AlertRules.weather(4,"X","2026-10-21",9,10,days(10,22,0, 12,20,0),storm);
    ok(s!=null && s.title.startsWith("💨") && s.body.startsWith("Bugün saat 17:00 rüzgâr 52"),"fırtına "+s);
    List<AlertRules.Hour> spring=calm("2026-04-21",20,0); spring.addAll(calm("2026-04-22",6,5));
    List<AlertRules.Day> sd=new ArrayList<>(); sd.add(new AlertRules.Day("2026-04-21",10,20,0)); sd.add(new AlertRules.Day("2026-04-22",10,20,5));
    AlertRules.Alert sp=AlertRules.weather(5,"X","2026-04-21",9,4,sd,spring);
    ok(sp!=null && sp.level==1 && sp.key.startsWith("spray:5:2026w"),"ilaçlama uygun "+sp);
    List<AlertRules.Hour> windy=calm("2026-04-21",20,0); windy.addAll(calm("2026-04-22",14,5));
    ok(AlertRules.weather(5,"X","2026-04-21",9,4,sd,windy)==null,"rüzgârlı sabah: ilaçlama önerilmez");
    ok(AlertRules.weather(5,"X","2026-11-21",9,11,new ArrayList<>(),none)==null,"veri yoksa sessiz");
    ok(AlertRules.calendar(2026,10).key.equals("cal:2026-10") && AlertRules.calendar(2026,10).title.contains("Hasat"),"ekim takvimi");
    ok(AlertRules.calendar(2026,1).key.equals("cal:2026-01"),"ocak anahtarı");
    ok(AlertRules.calendar(2026,5)==null,"mayıs mesajı yok");
    AlertRules.Alert d=AlertRules.receivable(7,"Hasan Usta",4600,20,"2026-10-21");
    ok(d!=null && d.body.equals("Hasan Usta'dan 4.600 ₺ alacağın 20 gündür bekliyor."),"alacak "+d);
    ok(AlertRules.receivable(7,"H",4600,10,"2026-10-21")==null,"14 günden yeni alacak sessiz");
    ok(AlertRules.receivable(7,"H",0.5,30,"2026-10-21")==null,"ödenmiş sessiz");
    ok(AlertRules.receivable(7,"H",100,30,"2026-10-22").key.equals(AlertRules.receivable(7,"H",100,31,"2026-10-23").key),"aynı hafta tek anahtar");
    ok(AlertRules.dayEnd(5,529,21,"2026-10-21").key.equals("zgun:2026-10-21"),"gün sonu hatırlatma");
    ok(AlertRules.dayEnd(5,529,18,"2026-10-21")==null,"akşam 20'den önce yok");
    ok(AlertRules.dayEnd(0,0,22,"2026-10-21")==null,"açık alım yoksa yok");
    System.out.println("PASS: "+n+" bildirim kuralı kontrolü");
  }
}
