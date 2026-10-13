import com.zeytindefteri.app.ZeytinAssistantCommand;
import com.zeytindefteri.app.ZeytinAssistantCommand.*;
import java.util.*;
public class AssistantCommandTest {
    static int count;
    static void check(boolean condition,String name){if(!condition)throw new AssertionError(name);count++;System.out.println("PASS "+name);}
    static void kind(String text,Kind kind){check(ZeytinAssistantCommand.parse(text).kind==kind,text);}
    public static void main(String[] args){
        kind("Elek aç",Kind.NAVIGATION);kind("Piyasayı aç",Kind.NAVIGATION);
        kind("Girelli’yi en son ne zaman suladım?",Kind.IRRIGATION);kind("Son gübreleme ne zaman?",Kind.FERTILIZER);
        kind("Bu sezon kaç kilo hasat yaptım?",Kind.HARVEST);kind("Bu sezon masrafım kaç?",Kind.EXPENSE);
        kind("Satış gelirim kaç?",Kind.REVENUE);kind("2 numara kaç kilo?",Kind.ELEK);
        kind("Girelli’nin havası nasıl?",Kind.WEATHER);kind("Yarın yağmur var mı?",Kind.WEATHER);
        kind("Şu an hangi bahçe seçili?",Kind.ACTIVE);kind("Bana bir şarkı söyle",Kind.UNKNOWN);
        kind("Girelli’de kaç ağaç var?",Kind.TREES);kind("Girelli kaç yaşında?",Kind.AGE);kind("Girelli’nin çeşidi ne?",Kind.VARIETY);
        kind("En son ne zaman gübre attım?",Kind.FERTILIZER);kind("En son ne zaman ilaçlama yaptım?",Kind.SPRAY);
        kind("Kaç kilo sattım?",Kind.SOLD);kind("Toplam satışım ne kadar?",Kind.REVENUE);
        kind("Yağlık kaç kilo?",Kind.ELEK);kind("Elek dağılımım nasıl?",Kind.ELEK);
        kind("Salamurada kaç kilo ürün var?",Kind.BRINE);kind("Hazır ürün kaç kilo?",Kind.READY);
        kind("Piyasa nasıl?",Kind.MARKET);kind("Akhisar’da 2 numara kaç para?",Kind.MARKET);
        kind("Nem kaç?",Kind.WEATHER);kind("Rüzgâr kaç?",Kind.WEATHER);
        kind("Bana sezon özeti ver",Kind.SUMMARY);kind("Bu sezon durumum nasıl?",Kind.SUMMARY);
        kind("Yarın ilaç atayım mı?",Kind.WEATHER);kind("Gübre dozu kaç gram?",Kind.UNKNOWN);
        for(String nav:Arrays.asList("Eleği aç","Hasat aç","Satış aç","Sulama ekle","Gübreleme aç","İlaçlama aç","Salamura aç","Bahçelerimi aç","Meydanı aç","Raporları aç","Ana sayfayı aç"))kind(nav,Kind.NAVIGATION);
        List<Garden> gardens=Arrays.asList(new Garden(1,"Girelli Zeytinliği"),new Garden(2,"Hacıaliler Zeytinlik"),new Garden(3,"Çelebilerin evinin yanı"));
        check(ZeytinAssistantCommand.resolve("Girelli’yi en son ne zaman suladım",gardens,2,false).garden.id==1,"explicit garden overrides active");
        check(ZeytinAssistantCommand.resolve("HACIALILER masrafı kaç",gardens,1,false).garden.id==2,"Turkish normalization");
        check(ZeytinAssistantCommand.resolve("Çelebilerin evinin yanı kaç ağaç",gardens,1,false).garden.id==3,"multiword name");
        check(ZeytinAssistantCommand.resolve("Son sulama ne zaman",gardens,2,false).garden.id==2,"active garden fallback");
        check(ZeytinAssistantCommand.resolve("Bilinmeyen’in havası nasıl",gardens,1,false).garden==null,"unknown explicit garden rejected");
        check(ZeytinAssistantCommand.resolve("Girelli havası",Arrays.asList(gardens.get(0),new Garden(4,"Girelli Bahçesi")),1,false).garden==null,"ambiguous alias rejected");
        check(ZeytinAssistantCommand.parse("iki numara kaç kilo").grade.equals("2"),"spoken grade");
        check(ZeytinAssistantCommand.parse("Toplam hasadım kaç").total,"report total scope");
        check(!ZeytinAssistantCommand.parse("Bu sezon kaç kilo hasat yaptım").total,"default active scope");
        check(ZeytinAssistantCommand.parse("Yarın yağmur var mı").tomorrow,"tomorrow flag");
        check(ZeytinAssistantCommand.resolve("Girelli son sulama",Arrays.asList(new Garden(5,"Girelli Kuzey"),new Garden(6,"Girelli Güney")),5,false).garden==null,"similar garden names require clarification");
        check(ZeytinAssistantCommand.resolve("Girelli Kuzey son sulama",Arrays.asList(new Garden(5,"Girelli Kuzey"),new Garden(6,"Girelli Güney")),6,false).garden.id==5,"full name resolves ambiguity");
        check(ZeytinAssistantCommand.resolve("Kayıtsız en son ne zaman suladım",gardens,1,false).garden==null,"unknown unpunctuated name cannot use active garden");
        System.out.println(count+" checks passed");
    }
}
