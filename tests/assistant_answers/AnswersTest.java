import com.zeytindefteri.app.*;
import com.zeytindefteri.app.ZeytinAssistantCommand.*;
import android.content.Context;
import java.util.*;

/**
 * Gerçekçi soru bataryası: çiftçinin sorabileceği farklı söyleyişler, bilinen kayıtlarla, elle hesaplanmış
 * doğru değerlere karşı. Tarihler bugüne göre üretilir (her gün çalışır). Test doubles; cihaz veritabanı değildir.
 */
public class AnswersTest {
 static int checks; static final List<String> fails=new ArrayList<>();
 static void check(boolean ok,String m){checks++;if(!ok)fails.add(m);}
 static long seq=100;
 static String day(int offset){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("Europe/Istanbul"));c.add(Calendar.DAY_OF_MONTH,offset);
  java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setTimeZone(c.getTimeZone());return f.format(c.getTime());}
 static void garden(long id,String name,long trees,long age,String variety,String district,String area,String water,String notes){
  DatabaseHelper.add("gardens","id",id,"owner","A","name",name,"tree_count",trees,"age",age,"variety",variety,"province","Manisa","district",district,"neighborhood","Girelli","area_decare",area,"irrigation_type",water,"notes",notes);}
 static void op(long g,String type,String date,double cost){DatabaseHelper.add("operations","id",++seq,"owner","A","season",2026,"garden_id",g,"type",type,"operation_date",date,"cost",cost);}
 static ZeytinAssistantData data; static List<Garden> gardens;
 static ZeytinAssistantCommand cmd(String q){return ZeytinAssistantCommand.parse(q);}
 /** Kontrolcünün yaptığı gibi: bahçe çözümü + cevap + okunur tarih. */
 static String ask(String q){
  ZeytinAssistantCommand c=cmd(q);Resolution r=ZeytinAssistantCommand.resolve(q,gardens,1,c.kind==Kind.MARKET);
  if(c.kind==Kind.UNKNOWN)return ZeytinAssistantText.unknownReply(q);
  boolean all=c.total && !r.explicit && c.kind!=Kind.ACTIVE;
  if(!r.error.isEmpty() && !(all && r.error.startsWith("Önce")) && c.kind!=Kind.MARKET && c.kind!=Kind.COMPARISON && !c.crossGarden)return "ÇÖZÜM HATASI: "+r.error;
  return ZeytinAssistantText.humanDates(data.answer(c,r.garden,gardens,all),System.currentTimeMillis());
 }
 static void has(String q,String... parts){String a=ask(q);for(String p:parts)check(a.contains(p),q+" → «"+a+"» içinde «"+p+"» yok");}
 static void kind(String q,Kind k){check(cmd(q).kind==k,q+" → tür "+cmd(q).kind+", beklenen "+k);}
 public static void main(String[] args){
  // Sezon içi tarihler bugüne göre (sezon filtresi test doubles'ta "season" alanıyla yapılır).
  garden(1,"Girelli Zeytinliği",250L,15L,"Gemlik","Alaşehir","12","Damla","Kuzey parsel");
  garden(2,"Afşar Bahçesi",120L,8L,"Domat","Salihli","6","Salma","");
  op(1,"Sulama",day(-7),300);op(1,"Sulama",day(-27),250);op(1,"Gübre",day(-46),1500);op(1,"İlaçlama",day(-12),800);op(1,"Mazot",day(-6),900);
  op(2,"Sulama",day(-3),200);op(2,"Budama",day(-200),0);
  DatabaseHelper.add("harvests","id",++seq,"owner","A","season",2026,"garden_id",1L,"harvest_date",day(-5),"total_kg",1200.,"labor_cost",3000.,"transport_cost",500.,"other_cost",0.);
  DatabaseHelper.add("harvests","id",++seq,"owner","A","season",2026,"garden_id",2L,"harvest_date",day(-2),"total_kg",800.,"labor_cost",1600.,"transport_cost",0.,"other_cost",0.);
  DatabaseHelper.add("sales","owner","A","season",2026,"garden_id",1L,"id",1L,"sale_date",day(-4),"sale_kind","SALE","total_kg",500.,"gross_total",46000.,"transport_cost",1000.,"deductions",500.,"market_consent",0.,"region","Alaşehir");
  DatabaseHelper.add("sales","owner","A","season",2026,"garden_id",2L,"id",2L,"sale_date",day(-1),"sale_kind","QUOTE","total_kg",400.,"gross_total",40000.,"transport_cost",0.,"deductions",0.,"market_consent",1.,"region","Salihli");
  DatabaseHelper.add("sale_detail","id",1L,"owner","A","season",2026,"garden_id",1L,"buyer_name","Yılmaz Zeytincilik","buyer_phone","");
  DatabaseHelper.add("sale_grades","id",++seq,"owner","A","season",2026,"garden_id",1L,"grade","1","kg",300.,"price_per_kg",100.);
  DatabaseHelper.add("sale_grades","id",++seq,"owner","A","season",2026,"garden_id",1L,"grade","2","kg",200.,"price_per_kg",80.);
  DatabaseHelper.add("elek","owner","A","season",2026,"garden_id",1L,"id",1L,"record_date",day(-5));
  for(Object[] r:new Object[][]{{"DOUBLE",150.},{"1",400.},{"2",350.},{"YAGLIK",100.}})DatabaseHelper.add("grades","id",++seq,"owner","A","season",2026,"garden_id",1L,"grade",r[0],"kg",r[1]);
  DatabaseHelper.add("brine","owner","A","season",2026,"garden_id",1L,"id",1L,"start_date",day(-3),"olive_kg",300.,"ready_kg",0.,"salt_cost",200.,"packaging_cost",0.,"other_cost",0.,"event_cost",50.);
  DatabaseHelper.add("brine_events","id",++seq,"owner","A","season",2026,"garden_id",1L,"event_date",day(-1),"cost",50.);

  // Tür hataları (gerçekte bulunanlar)
  kind("Yarın yağmur yağacak mı?",Kind.WEATHER);        // "yağacak" içindeki "ağaç" ağaç sorusu sanılıyordu
  kind("Bugün yağacak mı",Kind.WEATHER);
  kind("Önemli bir şey var mı",Kind.TODAY);              // "önemli" içindeki "nem" hava sorusu sanılmasın
  check(cmd("Havale ne zaman gelecek").kind!=Kind.WEATHER,"havale hava sanıldı");
  kind("Kaç ağaç var",Kind.TREES);
  kind("Sinek tuzağı astık mı",Kind.OPERATION);
  kind("Gübre masrafım ne kadar",Kind.EXPENSE);           // son gübre tarihi değil masraf
  kind("Kar zarar ne durumda",Kind.SUMMARY);

  try(ZeytinAssistantData d=new ZeytinAssistantData(new Context())){
   data=d;gardens=d.gardens();
   // Sayılar (elle hesap): Girelli masraf = işlem 300+250+1500+800+900 + hasat 3000+500 + salamura 200+50 = 7.500
   has("Masrafım kaç","7.500 TL");has("Bu sezon masrafım kaç?","7.500 TL");
   has("Toplam masraf ne kadar","9.300 TL");                       // + Afşar 200+1600
   has("Afşar'ın masrafı ne kadar","Afşar Bahçesi","1.800 TL");
   has("Toplam satışım ne?","44.500 TL");                          // 46.000-1.000-500; QUOTE hariç
   has("Kaç kilo sattım","500 kg");
   has("Kaça sattım","92 TL/kg");                                  // 46.000/500
   has("Toplam hasadım kaç","2.000 kg");has("Afşar kaç kilo çıktı","800 kg");has("Ne kadar zeytin topladık","1.200 kg");
   has("2 numara kaç kilo","350 kg");has("Double kaç kilo","150 kg");has("4 numara kaç kilo","bulunamadı");
   has("Hazır ürün kaç kilo","0 kg");has("Salamura stoğum","300 kg","kalan stok değildir");
   // Son işlemler: tarih + kaç gün önce
   has("Son sulama ne zaman?","(7 gün önce)");has("Suyu ne zaman verdik","(7 gün önce)");has("Kaç gün önce suladık","(7 gün önce)");
   has("Son gübre ne zaman?","(46 gün önce)");has("Son ilaçlama ne zaman?","(12 gün önce)");
   has("Afşar'ın son sulaması ne zaman","Afşar Bahçesi","(3 gün önce)");has("Girelli'de son ilaç","(12 gün önce)");
   check(!ask("Son sulama ne zaman?").contains("önce) (")&&!ask("Son sulama ne zaman?").matches(".*\\(7 gün önce\\).*\\(7 gün önce\\).*"),"'gün önce' iki kez yazıldı");
   // Bulunan hatalar
   has("Bahçem kaç yaşında","kayıtlı yaş 15");has("Ağaçlarım kaç yaşında","kayıtlı yaş 15");has("Zeytinliğim kaç yaşında","kayıtlı yaş 15");
   has("Budama yaptık mı","budama kaydı");has("Afşar son budama","son budama");has("Bu sezon kaç kez budama yaptık","budama kaydı bulunamadı");
   has("Sinek tuzağı astık mı","sinek tuzağı kaydı");
   has("Mazot masrafı","mazot masrafı 900 TL");has("Gübre masrafım ne kadar","gübreleme masrafı 1.500 TL");has("İlaç masrafı","ilaçlama masrafı 800 TL");
   has("Kime sattım","Yılmaz Zeytincilik");has("Son alıcı kim","Yılmaz Zeytincilik");
   has("Benim 1 numara satış fiyatım ne","kendi satışların","100 TL/kg");      // paylaşım izni kapalı olsa da kendi verisi
   has("Benim satış fiyatlarım ne","1 numara 100, 2 numara 80 TL/kg");
   check(!ask("Hangi bahçe daha çok verdi").contains(", :") && ask("Hangi bahçe daha çok verdi").contains("Girelli Zeytinliği"),"sıralama biçimi");
   check(ask("Elek sonucu ne").endsWith("yağlık: 100 kg."),"elek dağılımı sonu: "+ask("Elek sonucu ne"));
   has("Sulama tipi ne","Girelli Zeytinliği","Damla");has("Bahçe notu ne","Girelli Zeytinliği","Kuzey parsel");
   has("Kar zarar ne durumda","gelir eksi masraf 35.200 TL");
   has("Bugün ne yaptım","bugün:");check(!ask("Dün ne yaptık").contains("dun:"),"dönem adı ASCII kaldı");
   check(!ask("Girelli ile Afşar karşılaştır").endsWith(" "),"karşılaştırma sonunda boşluk");
   // Anlaşılmayan istekler
   has("Merhaba","Merhaba!");has("Teşekkürler","Rica ederim");
   has("Sulamayı sil","silmez");has("Kaç litre ilaç atayım","dozu");has("Zeytinim hasta mı","teşhisi");
  }
  // Hava: bilinen tahminle
  WeatherRepository.WeatherForecast f=new WeatherRepository.WeatherForecast();
  f.current=new WeatherRepository.WeatherSnapshot();f.current.temperatureC=21.4;f.current.humidity=63;f.current.windKmh=14;f.current.condition="Parçalı bulutlu";
  for(int i=0;i<6;i++){WeatherRepository.DailyForecast dd=new WeatherRepository.DailyForecast();dd.dateIso=day(i);dd.maxTemperatureC=22+i;dd.minTemperatureC=11+i;dd.precipitationProbability=i==1?70:10*i;f.daily.add(dd);}
  for(int h=0;h<24;h+=3){WeatherRepository.HourlyForecast hh=new WeatherRepository.HourlyForecast();hh.timeIso=day(1)+"T"+(h<10?"0":"")+h+":00";hh.windKmh=8+h;hh.temperatureC=15;hh.precipitationProbability=50;f.hourly.add(hh);}
  String[][] weather={{"Hava nasıl","21°C"},{"Nem kaç","%63"},{"Rüzgar kaç","14 km/sa"},{"Yarın yağmur yağacak mı?","%70"},{"Yarın hava nasıl","en yüksek 23°C"},
   {"Yarın rüzgâr kaç","29 km/sa"},{"Yarın ilaç atayım mı","uygunluk kararı değildir"},{"Bugün en yüksek sıcaklık","22°C"},{"5 günlük hava","yarın ("},{"Dün yağmur yağdı mı","geçmiş tarihli"}};
  for(String[] w:weather){ZeytinAssistantCommand c=cmd(w[0]);String a=c.kind==Kind.WEATHER?ZeytinAssistantText.humanDates(ZeytinAssistantWeather.answer(c,"Girelli",f,day(1)),System.currentTimeMillis()):"TÜR "+c.kind;
   check(a.contains(w[1]),w[0]+" → «"+a+"» içinde «"+w[1]+"» yok");}
  for(String m:fails)System.out.println("HATA: "+m);
  System.out.println("AnswersTest: "+checks+" kontrol, "+fails.size()+" hata");
  if(!fails.isEmpty())System.exit(1);
 }
}
