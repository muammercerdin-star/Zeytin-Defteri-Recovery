import com.zeytindefteri.app.GardenTodayAdvisor;
import com.zeytindefteri.app.GardenTodayAdvisor.*;
import com.zeytindefteri.app.GardenTodayAdvisor.Record;
import java.util.*;
public class GardenTodayAdvisorTest {
    static final GardenTodayAdvisor engine = new GardenTodayAdvisor();
    static final Scope a = new Scope("ownerA", 2026, 1), b = new Scope("ownerA", 2026, 2);
    static final String today = "2026-09-05";
    static int passed;
    static List<Recommendation> run(Scope s, List<Record> records, List<Weather> weather) {
        return engine.evaluate(s, today, records, true, weather);
    }
    static void check(boolean value, String test) {
        if (!value) throw new AssertionError(test); passed++; System.out.println("PASS " + test);
    }
    static boolean contains(List<Recommendation> values, String text) {
        for (Recommendation r : values) if ((r.title + r.message).contains(text)) return true;
        return false;
    }
    static boolean key(List<Recommendation> values, String key) {
        for (Recommendation r : values) if (r.key.equals(key)) return true;
        return false;
    }
    static Weather weather(Scope s) { return new Weather(s,"2026-09-05 18:00 saatlik tahmin"); }
    public static void main(String[] args) {
        List<Record> records = Arrays.asList(new Record(a,"Sulama","03.09.2026"));
        check(contains(run(a,records,Collections.emptyList()),"Son sulama: 2 gün önce"), "1 last irrigation two calendar days ago");
        check(contains(run(a,Collections.emptyList(),Collections.emptyList()),"Sulama kaydı bulunmuyor"), "2 missing irrigation");
        Weather rain = weather(a); rain.rain=75;
        check(contains(run(a,records,Arrays.asList(rain)),"%75"),"3 real rain probability");
        Weather wind = weather(a); wind.wind=30;
        check(contains(run(a,records,Arrays.asList(wind)),"30 km/sa"),"4 strong wind");
        check(!key(run(a,records,Collections.emptyList()),"rain"),"5 API failure produces no weather alert");
        check(!contains(run(b,records,Arrays.asList(rain)),"2 gün önce") && !key(run(b,records,Arrays.asList(rain)),"rain"),"6 garden switch rejects old records and weather");
        Scope oldSeason = new Scope("ownerA",2025,1);
        check(!contains(run(oldSeason,records,Arrays.asList(rain)),"2 gün önce"),"7 season switch");
        List<Record> multi = Arrays.asList(records.get(0),new Record(b,"Sulama","2026-08-01"));
        check(contains(run(b,multi,Collections.emptyList()),"35 gün önce"),"8 multiple gardens select correct date");
        check(!contains(run(new Scope("ownerB",2026,1),multi,Arrays.asList(rain)),"2 gün önce"),"9 different owner cannot see records");
        check(contains(run(a,records,Collections.emptyList()),"2 gün önce"),"10 offline local history");
        check(contains(run(a,Collections.emptyList(),Collections.emptyList()),"Hasat kaydı bulunmuyor"),"11 new garden no invented dates");
        Weather extremes = weather(a); extremes.wind=65; extremes.max=43; extremes.min=-6; extremes.rain=90;
        List<Recommendation> capped=run(a,Collections.emptyList(),Arrays.asList(extremes));
        check(capped.size()==3 && capped.get(0).level==Level.CRITICAL && capped.get(2).level==Level.CRITICAL,"12 five candidates only top three");
        List<Record> unordered = Arrays.asList(new Record(a,"Sulama","2026-08-01"),new Record(a,"Sulama","2026-09-04"));
        check(contains(run(a,unordered,Collections.emptyList()),"1 gün önce"),"latest operation date not creation order");
        check(contains(run(a,Arrays.asList(new Record(a,"Gübre","2026-08-18")),Collections.emptyList()),"Son gübreleme: 18 gün önce"),"existing Gubre storage label");
        check(contains(run(a,Arrays.asList(new Record(a,"Sulama","2026-09-06")),Collections.emptyList()),"tarihini kontrol et"),"future record not treated as today");
        check(contains(run(a,Arrays.asList(new Record(a,"Sulama","31.02.2026")),Collections.emptyList()),"tarihini kontrol et"),"invalid date rejected");
        Weather invalid=weather(a); invalid.rain=101; invalid.wind=Double.NaN; invalid.max=Double.POSITIVE_INFINITY;
        check(run(a,records,Arrays.asList(invalid)).size()==1,"missing or invalid weather not invented");
        Weather mild=weather(a); mild.wind=24.9; mild.rain=59.9; mild.max=34.9; mild.min=0.1;
        check(run(a,records,Arrays.asList(mild)).size()==1,"thresholds below boundary");
        check(!contains(run(a,Arrays.asList(new Record(a,"Sulama","2026-07-01")),Collections.emptyList()),"Sulama zamanı"),"old record gives no farming decision");
        check(contains(engine.evaluate(a,today,records,false,Collections.emptyList()),"okunamadı"),"read failure is not empty history");
        check(run(new Scope("ownerA",2026,0),records,Arrays.asList(rain)).get(0).action==Action.ADD_GARDEN,"no garden");
        System.out.println(passed + " assertions passed");
    }
}
