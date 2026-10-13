import com.zeytindefteri.app.*;
import com.zeytindefteri.app.ZeytinAssistantCommand.*;
import android.content.Context;
import java.util.*;
public class AssistantDataTest {
 static int checks;
 static void check(boolean pass,String name){if(!pass)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
 static String ask(ZeytinAssistantData d,String text,long id,boolean all){List<Garden> gardens=d.gardens();Garden selected=null;for(Garden g:gardens)if(g.id==id)selected=g;return d.answer(ZeytinAssistantCommand.parse(text),selected,gardens,all);}
 static void garden(long id,String owner,String name){DatabaseHelper.add("gardens","id",id,"owner",owner,"name",name,"tree_count",120L,"age",14L,"variety","Gemlik","province","Manisa","district","Alaşehir","neighborhood","Girelli");}
 public static void main(String[] args){
  garden(1,"A","Girelli Zeytinliği");garden(2,"A","Hacıaliler");garden(3,"B","Başka kullanıcı");
  DatabaseHelper.add("harvests","owner","A","season",2026,"garden_id",1L,"total_kg",190.,"labor_cost",100.,"transport_cost",20.,"other_cost",5.);
  DatabaseHelper.add("harvests","owner","A","season",2026,"garden_id",2L,"total_kg",80.,"labor_cost",0.,"transport_cost",0.,"other_cost",0.);
  DatabaseHelper.add("harvests","owner","B","season",2026,"garden_id",3L,"total_kg",999.,"labor_cost",0.,"transport_cost",0.,"other_cost",0.);
  DatabaseHelper.add("harvests","owner","A","season",2025,"garden_id",1L,"total_kg",22.,"labor_cost",0.,"transport_cost",0.,"other_cost",0.);
  DatabaseHelper.add("operations","owner","A","season",2026,"garden_id",1L,"type","Gübre","operation_date","2026-08-01","cost",200.);
  DatabaseHelper.add("sales","owner","A","season",2026,"garden_id",1L,"id",1L,"sale_kind","SALE","total_kg",50.,"gross_total",5000.,"transport_cost",100.,"deductions",50.,"market_consent",1.,"region","Akhisar");
  DatabaseHelper.add("sales","owner","A","season",2026,"garden_id",1L,"id",2L,"sale_kind","QUOTE","total_kg",900.,"gross_total",90000.,"transport_cost",0.,"deductions",0.,"market_consent",1.,"region","Akhisar");
  DatabaseHelper.add("sale_grades","owner","A","season",2026,"garden_id",1L,"grade","2","kg",50.,"price_per_kg",100.);
  DatabaseHelper.add("elek","owner","A","season",2026,"garden_id",1L,"id",1L);
  DatabaseHelper.add("grades","owner","A","season",2026,"garden_id",1L,"grade","2","kg",60.);
  DatabaseHelper.add("brine","owner","A","season",2026,"garden_id",1L,"olive_kg",40.,"ready_kg",30.,"salt_cost",10.,"packaging_cost",20.,"other_cost",5.,"event_cost",15.);
  try(ZeytinAssistantData d=new ZeytinAssistantData(new Context())){
   check(ask(d,"Bu sezon kaç kilo hasat yaptım",1,false).contains("190 kg"),"actual harvest values");
   check(ask(d,"Toplam hasadım kaç",1,true).contains("270 kg"),"report totals exclude other owner and season");
   check(ask(d,"Masrafım kaç",1,false).contains("375 TL"),"operation + harvest + brine expenses");
   check(ask(d,"Satış gelirim kaç",1,false).contains("4.850 TL"),"net recorded revenue excludes quotes");
   check(ask(d,"Kaç kilo sattım",1,false).contains("50 kg"),"sold kg excludes quotes");
   check(ask(d,"2 numara kaç kilo",1,false).contains("60 kg"),"elek actual grade");
   check(ask(d,"Yağlık kaç kilo",1,false).contains("bulunamadı"),"missing grade not fabricated zero");
   check(ask(d,"Hazır ürün kaç kilo",1,false).contains("30 kg"),"ready brine value");
   check(ask(d,"Salamurada kaç kilo ürün var",1,false).contains("kalan stok değildir"),"input weight not misrepresented as current stock");
   check(ask(d,"Akhisar’da 2 numara kaç para",1,false).contains("100 TL/kg"),"own consenting market records");
   check(ask(d,"Piyasa nasıl",0,false).contains("100 TL/kg"),"market works without active garden");
   check(ask(d,"İzmir’de 2 numara kaç para",1,false).contains("yeterli piyasa verisi yok"),"unknown region no fallback");
   check(ask(d,"Son sulama ne zaman",1,false).contains("bulamadım"),"no irrigation record");
   check(ask(d,"Son gübreleme ne zaman",1,false).contains("son gübreleme"),"existing Gubre type");
   check(ask(d,"Hasat kaç",2,false).contains("80 kg"),"garden switch");
   check(ask(d,"Bu sezon durumum nasıl",1,true).contains("270 kg"),"real summary");
   DatabaseHelper.season=2025;check(!d.unchanged(),"season change invalidates request");
  }
  try(ZeytinAssistantData d=new ZeytinAssistantData(new Context())){check(ask(d,"Hasat kaç",1,false).contains("22 kg"),"selected previous season");DatabaseHelper.owner="B";check(!d.unchanged(),"owner change invalidates request");}
  DatabaseHelper.season=2026;
  try(ZeytinAssistantData d=new ZeytinAssistantData(new Context())){check(d.gardens().size()==1 && d.gardens().get(0).id==3,"second user only owns third garden");}
  WeatherRepository.WeatherForecast f=new WeatherRepository.WeatherForecast();f.current=new WeatherRepository.WeatherSnapshot();f.current.temperatureC=26;f.current.humidity=58;f.current.windKmh=12;f.current.condition="Açık";
  WeatherRepository.DailyForecast day=new WeatherRepository.DailyForecast();day.dateIso="2026-09-06";day.precipitationProbability=75;f.daily.add(day);
  check(ZeytinAssistantWeather.answer(ZeytinAssistantCommand.parse("Nem kaç"),"Girelli",f,"2026-09-06").contains("%58"),"actual humidity");
  check(ZeytinAssistantWeather.answer(ZeytinAssistantCommand.parse("Yarın yağmur var mı"),"Girelli",f,"2026-09-06").contains("%75"),"tomorrow actual probability");
  check(ZeytinAssistantWeather.answer(ZeytinAssistantCommand.parse("Yarın ilaç atayım mı"),"Girelli",f,"2026-09-06").contains("verisi bulunamadı"),"no invented tomorrow wind");
  check(ZeytinAssistantWeather.answer(ZeytinAssistantCommand.parse("Hava nasıl"),"Girelli",null,"2026-09-06").contains("alınamadı"),"weather failure");
  day.precipitationProbability=Double.NaN;
  check(ZeytinAssistantWeather.answer(ZeytinAssistantCommand.parse("Yarın yağmur var mı"),"Girelli",f,"2026-09-06").contains("verisi bulunamadı"),"missing rain remains explicit");
  System.out.println(checks+" adapter checks passed (test doubles, not device database)");
 }
}
