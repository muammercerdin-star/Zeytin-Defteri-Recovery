import com.zeytindefteri.app.*;
import com.zeytindefteri.app.ZeytinAssistantCommand.*;
import java.util.*;
public class FinalCommandTest {
 static int count;
 static void family(Kind expected,String... phrases){for(String s:phrases){Kind got=ZeytinAssistantCommand.parse(s).kind;if(got!=expected)throw new AssertionError(s+": "+got+" != "+expected);count++;}}
 public static void main(String[] args){
 family(Kind.NAVIGATION,"Elek aç","Eleği aç","Elek ekranına git","Elek kayıtlarını göster","Hasat aç","Hasadı göster","Hasat ekranına git","Satış aç","Satışlara git","Sulama aç","Sulama ekle","Gübreleme aç","Gübre ekle","İlaçlama aç","İlaç ekle","Salamura aç","Bahçelerimi aç","Bahçeleri göster","Piyasayı aç","Fiyatları aç","Meydanı aç","Üretici akışına git","Raporları aç","Ana sayfaya dön","Girelli’ye sulama ekle","Bugün 200 kilo hasat ekle","Girelli’ye 250 kilo hasat kaydı aç","2 numara satış aç");
 family(Kind.ACTIVE,"Şu an hangi bahçe seçili?","Aktif bahçe hangisi?");
 family(Kind.TREES,"Girelli’de kaç ağaç var?");family(Kind.AGE,"Girelli kaç yaşında?");family(Kind.VARIETY,"Girelli’nin çeşidi ne?");family(Kind.AREA,"Kaç dekar?");family(Kind.LOCATION,"Bahçe nerede?");family(Kind.WATER_TYPE,"Sulama tipi ne?");family(Kind.NOTE,"Bahçe notu var mı?");
 family(Kind.SUMMARY,"Girelli ne durumda?","Girelli’yi özetle.","Bu sezon durumum nasıl?","Sezon özeti ver.","Bahçe ne alemde?","Bu sene ne yaptık?");
 family(Kind.OPERATION,"En son ne yaptık?","Son işlem ne?","Bahçeye en son ne yaptım?","Girelli’de en son ne yaptık?","En son hangi işlem yapılmış?","En son ne attık?","Bu hafta ne yaptık?","Bugün ne yaptık?","Dün işlem yaptım mı?");
 family(Kind.IRRIGATION,"Son sulama ne zaman?","En son ne zaman suladım?","En son ne zaman su verdik?","Kaç gün önce suladık?","Bu sezon kaç kez suladım?","Bu ay kaç kez suladım?","Hangi bahçeyi en son suladım?","Hangi bahçeyi uzun süredir sulamadım?","En son suyu ne zaman verdik?");
 family(Kind.FERTILIZER,"Son gübreleme ne zaman?","En son ne zaman gübre attım?","Bu sezon kaç kez gübreleme yaptım?","Bu ay gübre attım mı?");
 family(Kind.SPRAY,"Son ilaçlama ne zaman?","En son ne zaman ilaç attım?","Bu sezon kaç kez ilaçlama yaptım?","Bu ay ilaçlama var mı?");
 family(Kind.HARVEST,"Bu sezon kaç kilo topladım?","Bu sene ne kadar zeytin topladık?","Toplam hasat kaç?","Girelli’den kaç kilo çıktı?","Bu ay kaç kilo hasat yaptım?","Bu hafta kaç kilo topladım?","Son hasat ne zaman?","Son hasat kaç kilo?","En çok hasat hangi bahçeden?","Hangi bahçe daha çok verdi?","En çok hangi bahçeden ürün aldım?","Hangi bahçe daha çok hasat verdi?");
 family(Kind.EXPENSE,"Bu sezon masrafım kaç?","Bu sene ne kadar harcadım?","Cebimden ne kadar çıktı?","Ne kadar para gömdük?","Toplam gider kaç?","Girelli ne kadar masraf yaptı?","Bu ay ne kadar harcadım?","Bu hafta masraf kaç?","En çok masraf hangi bahçede?","En büyük masraf ne?","Son masraf neydi?","En fazla masraf hangi bahçede?");
 family(Kind.REVENUE,"Toplam satışım kaç?","Satış gelirim kaç?","Bu sezon ne kadar kazandım?","Bu ay kaç liralık satış yaptım?","Son satış ne zaman?","Son satış kaç para?","Son satış kaç kilo?","Son alıcı kim?","Son alıcının telefonu ne?","Hangi bahçeden daha çok satış yaptım?");
 family(Kind.SOLD,"Kaç kilo sattım?","En son ne sattım?","En çok hangi elek sattım?","En son kime sattım?","Ben malı kaça sattım?","Malı kaça verdik?","Geçen ay ne kadar sattım?");
 family(Kind.ELEK,"Double kaç kilo?","1 numara kaç kilo?","2 numara kaç kilo?","3 numara kaç kilo?","4 numara kaç kilo?","Yağlık kaç kilo?","Elek dağılımı nasıl?","En çok hangi elek çıktı?","Girelli’nin elek sonucu ne?","Toplam elek kaç kilo?","2 numaradan ne kadar çıktı?");
 family(Kind.BRINE,"Salamurada kaç kilo var?","Salamura ne durumda?","Stokta ne kadar var?","Ne kadar salamuraya ayırdım?");family(Kind.READY,"Hazır ürün kaç kilo?");
 family(Kind.MARKET,"Piyasa nasıl?","2 numara kaç para?","1 numara kaç para?","Double kaç para?","Yağlık kaç para?","Akhisar’da fiyat ne?","Benim fiyatımla piyasa arasında fark var mı?","Piyasada mal kaç para?");
 family(Kind.WEATHER,"Hava nasıl?","Bugün kaç derece?","Yarın kaç derece?","Yarın yağmur var mı?","Bugün yağmur var mı?","Yağış ihtimali kaç?","Nem kaç?","Rüzgâr kaç?","Rüzgâr güçlü mü?","Bugün en yüksek kaç?","Bugün en düşük kaç?","Yarın en yüksek kaç?","5 günlük hava nasıl?","Bu hafta yağmur var mı?");
 family(Kind.TODAY,"Bugün ne yapmam lazım?","Bugün bahçede ne var?","Girelli için bugün ne var?","Bugün ne öneriyorsun?");family(Kind.COMPARISON,"Girelli ile Afşar’ı karşılaştır.");
 family(Kind.UNKNOWN,"Traktörün radyosunu aç","Kayıtları sil","200 kilo hasat kaydet","Gübre dozu kaç gram?");
 List<Garden> gardens=Arrays.asList(new Garden(1,"Girelli Zeytinliği"),new Garden(2,"Afşar Zeytinliği"));
 for(String s:new String[]{"Bu ay masrafım kaç?","Dün işlem yaptım mı?","Geçen ay ne kadar sattım?","Son gübreleme ne zaman?"}){Resolution r=ZeytinAssistantCommand.resolve(s,gardens,1,false);if(r.garden==null||r.garden.id!=1)throw new AssertionError("scope "+s+" "+r.error);count++;}
 for(String s:new String[]{"Bilinmeyen kaç dekar?","Bilinmeyen ne durumda?","Bilinmeyen sulama ekle","Bilinmeyen elek aç"}){Resolution r=ZeytinAssistantCommand.resolve(s,gardens,1,false);if(r.error.isEmpty())throw new AssertionError("unknown garden fallback: "+s);count++;}
 System.out.println(count+" FINAL parser/scope checks passed");
 }
}
