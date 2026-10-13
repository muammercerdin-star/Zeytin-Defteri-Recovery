import com.zeytindefteri.app.*;
import com.zeytindefteri.app.ZeytinAssistantCommand.*;
import android.content.Context;
import java.util.*;
import java.text.*;
public class FinalDataTest {
 static int checks;
 static void has(String s,String expected){if(!s.contains(expected))throw new AssertionError(expected+" absent: "+s);checks++;}
 static void truth(boolean b,String name){if(!b)throw new AssertionError(name);checks++;}
 static String day(int offset){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("Europe/Istanbul"));c.add(Calendar.DATE,offset);SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setTimeZone(c.getTimeZone());return f.format(c.getTime());}
 static void garden(long id,String owner,String name){DatabaseHelper.add("gardens","id",id,"owner",owner,"name",name,"tree_count",120L,"age",14L,"variety","Gemlik","province","Manisa","district","Alaşehir","neighborhood","Girelli","area_decare",8d,"irrigation_type","Damla","notes","Kuyu var");}
 static void operation(long id,long garden,String owner,String type,String date,double cost){DatabaseHelper.add("operations","id",id,"garden_id",garden,"owner",owner,"season",2026,"type",type,"operation_date",date,"cost",cost);}
 static void harvest(long id,long garden,String date,double kg){DatabaseHelper.add("harvests","id",id,"garden_id",garden,"owner","A","season",2026,"harvest_date",date,"total_kg",kg,"labor_cost",40d,"transport_cost",10d,"other_cost",0d);}
 static void sale(long id,long garden,String date){DatabaseHelper.add("sales","id",id,"garden_id",garden,"owner","A","season",2026,"sale_date",date,"sale_kind","SALE","total_kg",100d,"gross_total",5000d,"transport_cost",100d,"deductions",50d,"market_consent",1d,"region","Akhisar");}
 static String answer(ZeytinAssistantData d,String s,long id){List<Garden> gardens=d.gardens();Garden g=null;for(Garden v:gardens)if(v.id==id)g=v;return d.answer(ZeytinAssistantCommand.parse(s),g,gardens,false);}
 public static void main(String[] args){
 DatabaseHelper.tables.clear();DatabaseHelper.owner="A";DatabaseHelper.season=2026;
 garden(1,"A","Girelli Zeytinliği");garden(2,"A","Afşar Zeytinliği");garden(3,"B","Başka Kullanıcı");
 operation(1,1,"A","Sulama",day(0),25);operation(2,1,"A","Gübre",day(-1),60);operation(3,2,"A","Sulama",day(-2),500);operation(4,3,"B","Sulama",day(0),99999);
 harvest(1,1,day(0),200);harvest(2,2,day(0),350);sale(1,1,day(0));
 DatabaseHelper.add("sale_grades","garden_id",1L,"owner","A","season",2026,"grade","2","kg",100d,"price_per_kg",50d);
 DatabaseHelper.add("sale_detail","garden_id",1L,"owner","A","season",2026,"buyer_name","Ali","buyer_phone","05550000000");
 try(ZeytinAssistantData d=new ZeytinAssistantData(new Context())){
 has(answer(d,"Kaç dekar?",1),"8.0 dekar");has(answer(d,"Bahçe nerede?",1),"Manisa");has(answer(d,"Sulama tipi ne?",1),"Damla");has(answer(d,"Bahçe notu var mı?",1),"Kuyu");
 has(answer(d,"Bugün kaç kez suladım?",1),"1 Sulama");has(answer(d,"Dün işlem yaptım mı?",1),"Gübre");has(answer(d,"Son işlem ne?",1),"Sulama");
 has(answer(d,"Bugün kaç kilo topladım?",1),"200 kg");has(answer(d,"Bugün masrafım kaç?",1),"75 TL");has(answer(d,"Son hasat kaç kilo?",1),"200 kg");
 has(answer(d,"En büyük masraf ne?",1),"60 TL");has(answer(d,"Son satış kaç para?",1),"4.850 TL");has(answer(d,"Son alıcı kim?",1),"Ali");has(answer(d,"Son alıcının telefonu ne?",1),"05550000000");
 has(answer(d,"Ben malı kaça sattım?",1),"50 TL/kg");has(answer(d,"En çok hangi elek sattım?",1),"2 numara");has(answer(d,"En son ne sattım?",1),"2 numara");
 has(answer(d,"En çok hasat hangi bahçeden?",1),"Afşar");has(answer(d,"Hangi bahçeyi en son suladım?",1),"Girelli");has(answer(d,"Hangi bahçeyi uzun süredir sulamadım?",1),"Afşar");
 has(answer(d,"Girelli ile Afşar’ı karşılaştır",1),"350 kg");
 has(d.advice(d.gardens().get(0),null),"Son sulama");
 truth(!answer(d,"En fazla masraf hangi bahçede?",1).contains("99.999"),"owner isolation");
 has(answer(d,"Son satış kaç kilo?",2),"kayıt bulunamadı");
 DatabaseHelper.season=2025;truth(!d.unchanged(),"season invalidation");DatabaseHelper.season=2026;
 DatabaseHelper.owner="B";truth(!d.unchanged(),"owner invalidation");DatabaseHelper.owner="A";
 }
 try(ZeytinAssistantData d=new ZeytinAssistantData(new Context())){DatabaseHelper.season=2025;has(answer(d,"Toplam hasat kaç?",1),"kayıt bulunamadı");DatabaseHelper.season=2026;}
 long now=ZeytinAssistantData.date("2026-09-06")+12*3600000L;
 ZeytinAssistantPeriod week=ZeytinAssistantPeriod.parse("bu hafta");truth(week.includes("2026-08-31",now),"week starts Monday");truth(!week.includes("2026-09-07",now),"week end exclusive");
 truth(ZeytinAssistantPeriod.parse("gecen ay").includes("2026-08-31",now),"previous month");truth(!ZeytinAssistantPeriod.parse("gecen ay").includes("2026-09-01",now),"month boundary");truth(!week.includes("invalid",now),"bad date excluded");
 WeatherRepository.WeatherForecast weather=new WeatherRepository.WeatherForecast();WeatherRepository.DailyForecast daily=new WeatherRepository.DailyForecast();daily.dateIso=day(0);daily.maxTemperatureC=31;daily.minTemperatureC=16;daily.precipitationProbability=72;weather.daily.add(daily);
 has(ZeytinAssistantWeather.answer(ZeytinAssistantCommand.parse("Bugün yağmur var mı?"),"Girelli",weather,day(1)),"%72");
 has(ZeytinAssistantWeather.answer(ZeytinAssistantCommand.parse("Bugün en düşük kaç?"),"Girelli",weather,day(1)),"16°C");
 has(ZeytinAssistantWeather.answer(ZeytinAssistantCommand.parse("5 günlük hava nasıl?"),"Girelli",weather,day(1)),"31°C");
 daily.precipitationProbability=Double.NaN;has(ZeytinAssistantWeather.answer(ZeytinAssistantCommand.parse("Bugün yağmur var mı?"),"Girelli",weather,day(1)),"verisi bulunamadı");
 System.out.println(checks+" FINAL data/period checks passed (fixtures, not device)");
 }
}
