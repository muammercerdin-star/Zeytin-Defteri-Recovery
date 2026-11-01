package com.zeytindefteri.app;

import android.content.Context;
import android.database.Cursor;
import java.text.*;
import java.util.*;
import com.zeytindefteri.app.ZeytinAssistantCommand.Garden;

/** Read-only adapter over existing owner/season-scoped DatabaseHelper APIs. No raw SQL. */
public final class ZeytinAssistantData implements AutoCloseable {
    private final DatabaseHelper db;
    public final String owner;
    public final int season;
    public ZeytinAssistantData(Context context) {
        db=new DatabaseHelper(context.getApplicationContext()); owner=db.getActiveOwnerUid();season=db.getActiveSeasonYear();
    }
    public boolean unchanged(){return owner.equals(db.getActiveOwnerUid()) && season==db.getActiveSeasonYear();}
    public List<Garden> gardens() {
        List<Garden> list=new ArrayList<>();
        try(Cursor c=db.getGardens()) {
            while(c.moveToNext()) {
                Garden g=new Garden(id(c,"id"),str(c,"name"));
                g.trees=nullableLong(c,"tree_count");g.age=nullableLong(c,"age");g.variety=str(c,"variety");
                g.area=optional(c,"area_decare");g.waterType=optional(c,"irrigation_type");g.note=optional(c,"notes");
                g.province=str(c,"province");g.district=str(c,"district");g.neighborhood=str(c,"neighborhood");list.add(g);
            }
        }
        return list;
    }
    public String answer(ZeytinAssistantCommand cmd,Garden garden,List<Garden> owned,boolean all) {
        if(cmd.kind==ZeytinAssistantCommand.Kind.MARKET)return market(cmd,owned);
        if(cmd.kind==ZeytinAssistantCommand.Kind.COMPARISON)return compare(cmd,owned);
        if(cmd.ranking && cmd.text.contains("bahce") && (cmd.kind==ZeytinAssistantCommand.Kind.HARVEST || cmd.kind==ZeytinAssistantCommand.Kind.EXPENSE || cmd.kind==ZeytinAssistantCommand.Kind.REVENUE || cmd.kind==ZeytinAssistantCommand.Kind.SOLD || cmd.kind==ZeytinAssistantCommand.Kind.IRRIGATION))return ranking(cmd,owned);
        if(cmd.kind==ZeytinAssistantCommand.Kind.OPERATION && !cmd.opType.isEmpty())
            return cmd.count||cmd.period.limited()?operations(cmd,garden,cmd.opType):last(garden,cmd.opType);
        if(cmd.kind==ZeytinAssistantCommand.Kind.OPERATION)return operations(cmd,garden,"");
        if(cmd.text.contains("en buyuk masraf"))return biggestExpense(cmd,garden);
        if((cmd.kind==ZeytinAssistantCommand.Kind.REVENUE || cmd.kind==ZeytinAssistantCommand.Kind.SOLD) && (cmd.text.contains("kime") || cmd.text.contains("alici")))return latest(cmd,garden);
        if(cmd.latest && (cmd.kind==ZeytinAssistantCommand.Kind.HARVEST || cmd.kind==ZeytinAssistantCommand.Kind.REVENUE || cmd.kind==ZeytinAssistantCommand.Kind.SOLD || cmd.kind==ZeytinAssistantCommand.Kind.EXPENSE))return latest(cmd,garden);
        if((cmd.kind==ZeytinAssistantCommand.Kind.REVENUE || cmd.kind==ZeytinAssistantCommand.Kind.SOLD) && (cmd.text.contains("kaca") || cmd.text.contains("hangi elek")))return saleDistribution(cmd,garden);

        String scope=(all ? "Tüm bahçelerin" : garden.name)+", "+season+" sezonu"+(cmd.period.limited()?", "+cmd.period.label():"")+": ";
        switch(cmd.kind) {
            case AREA:return garden.area.isEmpty()?"Alan kaydı bulunamadı.":garden.name+": "+garden.area+" dekar kayıtlı.";
            case LOCATION:String place=(garden.province+" "+garden.district+" "+garden.neighborhood).trim();return place.isEmpty()?"Konum kaydı bulunamadı.":garden.name+": "+place;
            case WATER_TYPE:return garden.waterType.isEmpty()?garden.name+": sulama tipi kaydı bulunamadı.":garden.name+": sulama tipi "+garden.waterType+".";
            case NOTE:return garden.note.isEmpty()?garden.name+": bahçe notu bulunamadı.":garden.name+" notu: "+garden.note;
            case ACTIVE:return "Şu anda aktif bahçen "+garden.name+".";
            case TREES:return garden.trees==null ? "Ağaç sayısı kaydı bulunamadı." : garden.name+" için "+garden.trees+" ağaç kayıtlı.";
            case AGE:return garden.age==null ? "Yaş kaydı bulunamadı." : garden.name+" için kayıtlı yaş "+garden.age+".";
            case VARIETY:return garden.variety.trim().isEmpty()?"Çeşit kaydı bulunamadı.":garden.name+" için kayıtlı çeşit "+garden.variety+".";
            case IRRIGATION:return cmd.count||cmd.period.limited()?operations(cmd,garden,"Sulama"):last(garden,"Sulama");
            case FERTILIZER:return cmd.count||cmd.period.limited()?operations(cmd,garden,"Gübre"):last(garden,"Gübre");
            case SPRAY:return cmd.count||cmd.period.limited()?operations(cmd,garden,"İlaçlama"):last(garden,"İlaçlama");
            case MARKET:return market(cmd,owned);
            default:break;
        }
        List<Garden> selected=all?owned:Collections.singletonList(garden);
        if(cmd.kind==ZeytinAssistantCommand.Kind.EXPENSE && !cmd.opType.isEmpty())return typedExpense(cmd,selected,scope);
        Totals t=totals(selected,all,cmd.period);
        switch(cmd.kind) {
            case HARVEST:return scope+value(t.harvestRows,t.harvest,"kg hasat");
            case EXPENSE:return scope+value(t.expenseRows,t.expenses,"TL masraf");
            case REVENUE:return scope+value(t.saleRows,t.revenue,"TL satış geliri (nakliye ve kesintiler sonrası)");
            case SOLD:return scope+value(t.saleRows,t.sold,"kg satış");
            case BRINE:return scope+(t.brineRows==0?"salamura kaydı bulunamadı.":number(t.brine)+" kg salamuraya giriş kayıtlı. Bu değer kalan stok değildir.");
            case READY:return scope+value(t.brineRows,t.ready,"kg hazır ürün");
            case ELEK:
                if(t.grades.isEmpty())return scope+"elek kaydı bulunamadı.";
                if(cmd.text.contains("en cok"))return scope+largest(t.grades);
                if(cmd.text.contains("toplam elek")){double sum=0;for(double n:t.grades.values())sum+=n;return scope+number(sum)+" kg elek kayıtlı.";}
                if(!cmd.grade.isEmpty())return scope+(t.grades.containsKey(cmd.grade)?number(t.grades.get(cmd.grade))+" kg "+gradeName(cmd.grade)+" kayıtlı.":gradeName(cmd.grade)+" için elek kaydı bulunamadı.");
                StringBuilder distribution=new StringBuilder(scope);
                for(Map.Entry<String,Double> entry:t.grades.entrySet())distribution.append(gradeName(entry.getKey())).append(": ").append(number(entry.getValue())).append(" kg; ");
                return distribution.substring(0,distribution.length()-2)+".";
            case SUMMARY:
                String summary=scope+"hasat "+brief(t.harvestRows,t.harvest,"kg")+", masraf "+brief(t.expenseRows,t.expenses,"TL")+", satış geliri "+brief(t.saleRows,t.revenue,"TL")+".";
                if(t.saleRows>0 && t.expenseRows>0)summary+=" Kayıtlı gelir eksi masraf "+number(t.revenue-t.expenses)+" TL. Bu, kayıtların tamamlığına bağlıdır.";
                return summary;
            default:return "Bu soruyu henüz cevaplayamıyorum.";
        }
    }
    /**
     * Bu sezon kaydı olan konular (yakın soru önerileri için): kaydı olmayan konunun sorusu önerilmez.
     * Hava ve piyasa yerel kayıt istemez. Mevcut sahip + sezon kapsamındaki okuma fonksiyonları; yeni SQL yok.
     */
    public Set<ZeytinAssistantHints.Topic> recordedTopics(List<Garden> owned){
        Set<ZeytinAssistantHints.Topic> out=EnumSet.of(ZeytinAssistantHints.Topic.WEATHER,ZeytinAssistantHints.Topic.MARKET);
        for(Garden g:owned)try(Cursor c=db.getGardenOperations(g.id)){while(c.moveToNext()){
            String t=type(str(c,"type"));
            if(t.equals("sulama"))out.add(ZeytinAssistantHints.Topic.IRRIGATION);
            else if(t.equals("gubre")||t.equals("ilaclama"))out.add(ZeytinAssistantHints.Topic.CARE);
        }}
        Totals t=totals(owned,true,ZeytinAssistantPeriod.parse(""));
        if(t.harvestRows>0||t.brineRows>0||!t.grades.isEmpty())out.add(ZeytinAssistantHints.Topic.HARVEST);
        if(t.expenseRows>0||t.saleRows>0)out.add(ZeytinAssistantHints.Topic.MONEY);
        return out;
    }
    private String last(Garden garden,String type) {
        Long latest=null; boolean found=false; long now=date(today());
        try(Cursor c=db.getGardenOperations(garden.id)) {
            while(c.moveToNext()) {
                String stored=ZeytinAssistantCommand.normalize(str(c,"type"));
                if(!stored.equals(ZeytinAssistantCommand.normalize(type)) && !(type.equals("Gübre") && stored.equals("gubreleme")))continue;
                found=true;Long d=date(str(c,"operation_date"));
                if(d!=null && d<=now && (latest==null || d>latest))latest=d;
            }
        }
        String label=type.equals("Gübre")?"gübreleme":type.toLowerCase(new Locale("tr","TR"));
        if(latest==null)return garden.name+", "+season+" sezonu için "+(found?label+" kaydının tarihi doğrulanamadı.":label+" kaydı bulamadım.");
        long days=(now-latest)/86400000L;
        SimpleDateFormat iso=new SimpleDateFormat("yyyy-MM-dd",Locale.US);iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        return garden.name+", "+season+" sezonunda son "+label+" "+(days==0?"bugün":iso.format(new Date(latest)))+" yapılmış.";
    }
    private static final class Totals {
        int harvestRows,expenseRows,saleRows,brineRows;
        double harvest,expenses,revenue,sold,brine,ready;
        Map<String,Double> grades=new LinkedHashMap<>();
    }
    private Totals totals(List<Garden> gardens,boolean all,ZeytinAssistantPeriod period) {
        Totals t=new Totals();Set<Long> ids=new HashSet<>();
        for(Garden g:gardens) {
            ids.add(g.id);
            try(Cursor c=db.getGardenOperations(g.id)){while(c.moveToNext()){if(!inPeriod(c,"operation_date",period))continue;t.expenseRows++;t.expenses+=num(c,"cost");}}
            try(Cursor c=db.getGardenHarvests(g.id)){while(c.moveToNext()){
                if(!inPeriod(c,"harvest_date",period))continue;
                t.harvestRows++;t.expenseRows++;t.harvest+=num(c,"total_kg");
                t.expenses+=num(c,"labor_cost")+num(c,"transport_cost")+num(c,"other_cost");}}
            try(Cursor c=db.getGardenSales(g.id)){while(c.moveToNext())if("SALE".equals(str(c,"sale_kind"))){
                if(!inPeriod(c,"sale_date",period))continue;
                t.saleRows++;t.sold+=num(c,"total_kg");t.revenue+=num(c,"gross_total")-num(c,"transport_cost")-num(c,"deductions");}}
            try(Cursor c=db.getGardenElekRecords(g.id)){while(c.moveToNext()){
                if(!inPeriod(c,"record_date",period))continue;
                try(Cursor grades=db.getElekGrades(id(c,"id"))){while(grades.moveToNext()){
                    String grade=str(grades,"grade");Double old=t.grades.get(grade);
                    t.grades.put(grade,(old==null?0:old)+num(grades,"kg"));}}}}
        }
        // Existing owner + season scope; apply selected garden in memory, not a new SQL rule.
        try(Cursor c=db.getBrineBatches()){while(c.moveToNext())if(all || ids.contains(id(c,"garden_id"))){
            if(inPeriod(c,"start_date",period)){t.brineRows++;t.expenseRows++;t.brine+=num(c,"olive_kg");t.ready+=num(c,"ready_kg");
            t.expenses+=num(c,"salt_cost")+num(c,"packaging_cost")+num(c,"other_cost");}
            if(!period.limited())t.expenses+=num(c,"event_cost");
            else try(Cursor events=db.getBrineEvents(id(c,"id"))){while(events.moveToNext())if(inPeriod(events,"event_date",period)){t.expenseRows++;t.expenses+=num(events,"cost");}}}}
        return t;
    }
    /**
     * "Benim 1 numara satış fiyatım": kullanıcının kendi gerçek satışları (SALE). Bu kendi verisi olduğu için
     * piyasa paylaşım izni aranmaz; bölge süzgeci uygulanmaz. Elek söylenmediyse her elek için ortanca.
     */
    private String ownPrices(ZeytinAssistantCommand command,List<Garden> owned){
        Map<String,List<Double>> byGrade=new LinkedHashMap<>();
        for(String g:new String[]{"DOUBLE","1","2","3","4","YAGLIK"})byGrade.put(g,new ArrayList<>());
        SaleRepository sales=new SaleRepository(db);
        for(Garden g:owned)try(Cursor c=db.getGardenSales(g.id)){while(c.moveToNext()){
            if(!"SALE".equals(str(c,"sale_kind")) || !inPeriod(c,"sale_date",command.period))continue;
            try(Cursor lines=sales.getSaleGrades(id(c,"id"))){while(lines.moveToNext()){
                double price=num(lines,"price_per_kg");String grade=str(lines,"grade");
                if(num(lines,"kg")>0 && price>0 && byGrade.containsKey(grade))byGrade.get(grade).add(price);
            }}
        }}
        String scope=season+" sezonu"+(command.period.limited()?", "+command.period.label():"")+", kendi satışların: ";
        if(!command.grade.isEmpty()){
            List<Double> p=byGrade.get(command.grade);
            if(p==null||p.isEmpty())return scope+gradeName(command.grade)+" için satış kaydı bulunamadı.";
            return scope+gradeName(command.grade)+" ortanca fiyat "+number(median(p))+" TL/kg ("+p.size()+" satır).";
        }
        List<String> parts=new ArrayList<>();
        for(Map.Entry<String,List<Double>> e:byGrade.entrySet())if(!e.getValue().isEmpty())parts.add(gradeName(e.getKey())+" "+number(median(e.getValue())));
        return parts.isEmpty()?scope+"elek fiyatlı satış kaydı bulunamadı.":scope+"ortanca fiyatlar "+String.join(", ",parts)+" TL/kg.";
    }
    private static double median(List<Double> values){List<Double> v=new ArrayList<>(values);Collections.sort(v);int n=v.size();return n%2==1?v.get(n/2):(v.get(n/2-1)+v.get(n/2))/2;}
    private String market(ZeytinAssistantCommand command,List<Garden> owned) {
        // Preserve market consent/SALE/positive kg & price rules, but never scan other owners' sales.
        if(command.text.contains("fark"))return "Piyasa geneliyle karşılaştırılabilir güncel veri bulunamadı. Kendi satış fiyatını sorabilirsin.";
        if(ZeytinAssistantCommand.ownSales(command.text))return ownPrices(command,owned);
        String grade=command.grade.isEmpty()?"2":command.grade;
        List<Double> prices=new ArrayList<>(); Set<String> regions=new LinkedHashSet<>();
        SaleRepository sales=new SaleRepository(db);
        for(Garden g:owned)try(Cursor c=db.getGardenSales(g.id)){while(c.moveToNext()){
            if(!"SALE".equals(str(c,"sale_kind")) || num(c,"market_consent")!=1 || !inPeriod(c,"sale_date",command.period))continue;
            String region=str(c,"region");regions.add(region);
        }}
        String regionFilter="";
        for(String region:regions)if(!region.isEmpty() && command.text.contains(ZeytinAssistantCommand.normalize(region))){
            if(!regionFilter.isEmpty() && !regionFilter.equals(region))return "Tek bir piyasa bölgesi söyle.";
            regionFilter=region;
        }
        // Explicit location with no matching own-market records must not fall back to every region.
        boolean namedPlace=command.text.matches(".*\\b[a-z]+ (da|de|dan|den)\\b.*")
                || command.text.matches("^[a-z]{3,}(da|de) .*" )
                || command.text.matches("^[a-z]+ (1|2|3|4|bir|iki|uc|dort) .*" );
        if(regionFilter.isEmpty() && namedPlace)return "Bu filtre için yeterli piyasa verisi yok.";
        for(Garden g:owned)try(Cursor c=db.getGardenSales(g.id)){while(c.moveToNext()){
            if(!"SALE".equals(str(c,"sale_kind")) || num(c,"market_consent")!=1 || !inPeriod(c,"sale_date",command.period))continue;
            if(!regionFilter.isEmpty() && !regionFilter.equals(str(c,"region")))continue;
            try(Cursor lines=sales.getSaleGrades(id(c,"id"))){while(lines.moveToNext()){
                double price=num(lines,"price_per_kg");
                if(grade.equals(str(lines,"grade")) && num(lines,"kg")>0 && price>0)prices.add(price);
            }}
        }}
        if(prices.isEmpty())return "Bu filtre için yeterli piyasa verisi yok.";
        Collections.sort(prices);int n=prices.size();double median=n%2==1?prices.get(n/2):(prices.get(n/2-1)+prices.get(n/2))/2;
        return season+" sezonunda kendi piyasa paylaşım izinli satışların, "+(regionFilter.isEmpty()?"tüm kayıtlı bölgeler":regionFilter)+": "+gradeName(grade)+" ortanca fiyat "+number(median)+" TL/kg ("+n+" satır). Güncel piyasa geneli veya fiyat garantisi değildir.";
    }
    private static String optional(Cursor c,String key){try{return str(c,key);}catch(IllegalArgumentException e){return "";}}
    private boolean inPeriod(Cursor c,String key,ZeytinAssistantPeriod period){return !period.limited() || period.includes(optional(c,key),System.currentTimeMillis());}
    private static String join(List<String> values){StringBuilder b=new StringBuilder();for(String value:values){if(b.length()>0)b.append(", ");b.append(value);}return b.toString();}
    private static String largest(Map<String,Double> values){
        double max=-1;String result="";
        for(Map.Entry<String,Double> e:values.entrySet())if(e.getValue()>max){max=e.getValue();result=gradeName(e.getKey());}else if(e.getValue()==max)result+=" / "+gradeName(e.getKey());
        return result+": "+number(max)+" kg (en yüksek kayıtlı miktar).";
    }
    private static String type(String value){String n=ZeytinAssistantCommand.normalize(value);return n.equals("gubreleme")?"gubre":n;}
    private static final class Entry { long day,id;double kg,cost;String label,date; }
    private List<Entry> entries(Garden garden,ZeytinAssistantPeriod period,String source){
        List<Entry> list=new ArrayList<>();
        try(Cursor c=source.equals("harvest")?db.getGardenHarvests(garden.id):source.equals("sale")?db.getGardenSales(garden.id):db.getGardenOperations(garden.id)){
            while(c.moveToNext()){
                if(source.equals("sale")&&!str(c,"sale_kind").equals("SALE"))continue;
                String date=optional(c,source.equals("harvest")?"harvest_date":source.equals("sale")?"sale_date":"operation_date");
                Long day=date(date);if(day==null || day>date(today()) || !period.includes(date,System.currentTimeMillis()))continue;
                Entry e=new Entry();e.day=day;e.date=date;e.id=id(c,"id");
                e.label=source.equals("operation")?str(c,"type"):source.equals("harvest")?"Hasat":"Satış";
                e.kg=source.equals("operation")?0:num(c,"total_kg");
                e.cost=source.equals("operation")?num(c,"cost"):source.equals("harvest")?num(c,"labor_cost")+num(c,"transport_cost")+num(c,"other_cost"):num(c,"gross_total")-num(c,"transport_cost")-num(c,"deductions");list.add(e);
            }
        }
        Collections.sort(list,(a,b)->{int order=Long.compare(b.day,a.day);return order==0?Long.compare(b.id,a.id):order;});return list;
    }
    private static String today(){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setTimeZone(TimeZone.getTimeZone("Europe/Istanbul"));return f.format(new Date());}
    private String operations(ZeytinAssistantCommand cmd,Garden garden,String wanted){
        List<Entry> list=entries(garden,cmd.period,"operation");if(!wanted.isEmpty())list.removeIf(e->!type(e.label).equals(type(wanted)));
        String scope=garden.name+", "+season+" sezonu"+(cmd.period.limited()?", "+cmd.period.label():"")+": ";
        if(wanted.isEmpty() && cmd.period.limited() && cmd.text.contains("ne yapt")){
            Totals t=totals(Collections.singletonList(garden),false,cmd.period);
            if(list.isEmpty() && t.harvestRows==0 && t.saleRows==0 && t.brineRows==0)return scope+"kayıt bulunamadı.";
            return scope+list.size()+" işlem kaydı; hasat "+brief(t.harvestRows,t.harvest,"kg")+", masraf "+brief(t.expenseRows,t.expenses,"TL")+", satış "+brief(t.saleRows,t.revenue,"TL")+".";
        }
        if(list.isEmpty())return scope+(wanted.isEmpty()?"işlem":wanted.equals("Gübre")?"gübreleme":wanted.toLowerCase(new Locale("tr","TR")))+" kaydı bulunamadı.";
        if(cmd.count || (cmd.period.limited()&&!cmd.latest))return scope+list.size()+" "+(wanted.isEmpty()?"işlem":wanted)+" kaydı. Son kayıt: "+list.get(0).label+", "+list.get(0).date+".";
        Entry e=list.get(0);return scope+"son işlem "+e.label+", "+e.date+".";
    }
    private List<Entry> expenses(Garden garden,ZeytinAssistantPeriod period){
        List<Entry> list=entries(garden,period,"operation");list.addAll(entries(garden,period,"harvest"));
        try(Cursor c=db.getBrineBatches()){while(c.moveToNext())if(id(c,"garden_id")==garden.id){
            Long day=date(optional(c,"start_date"));
            if(day!=null && day<=date(today()) && inPeriod(c,"start_date",period)){Entry e=new Entry();e.day=day;e.id=id(c,"id");e.date=str(c,"start_date");e.label="Salamura";e.cost=num(c,"salt_cost")+num(c,"packaging_cost")+num(c,"other_cost");list.add(e);}
            try(Cursor events=db.getBrineEvents(id(c,"id"))){while(events.moveToNext()){
                Long d=date(optional(events,"event_date"));if(d==null||d>date(today())||!inPeriod(events,"event_date",period))continue;
                Entry e=new Entry();e.day=d;e.date=str(events,"event_date");e.label="Salamura işlemi";e.cost=num(events,"cost");list.add(e);
            }}
        }}list.removeIf(e->e.cost<=0);Collections.sort(list,(a,b)->Long.compare(b.day,a.day));return list;
    }
    /** "Mazot masrafı", "gübre masrafı": yalnızca o türdeki işlem kayıtlarının maliyeti. */
    private String typedExpense(ZeytinAssistantCommand cmd,List<Garden> gardens,String scope){
        int rows=0;double sum=0;
        for(Garden g:gardens)for(Entry e:entries(g,cmd.period,"operation"))if(type(e.label).equals(type(cmd.opType))){rows++;sum+=e.cost;}
        String label=cmd.opType.equals("Gübre")?"gübreleme":cmd.opType.toLowerCase(new Locale("tr","TR"));
        return rows==0?scope+label+" masrafı kaydı bulunamadı.":scope+label+" masrafı "+number(sum)+" TL ("+rows+" kayıt).";
    }
    private String biggestExpense(ZeytinAssistantCommand cmd,Garden garden){
        List<Entry> list=expenses(garden,cmd.period);if(list.isEmpty())return "Masraf kaydı bulunamadı.";
        Entry max=list.get(0);for(Entry e:list)if(e.cost>max.cost)max=e;
        return garden.name+", "+season+" sezonu: en büyük kayıtlı masraf "+max.label+", "+max.date+", "+number(max.cost)+" TL.";
    }
    private String latest(ZeytinAssistantCommand cmd,Garden garden){
        boolean harvest=cmd.kind==ZeytinAssistantCommand.Kind.HARVEST, expense=cmd.kind==ZeytinAssistantCommand.Kind.EXPENSE;
        List<Entry> list=entries(garden,cmd.period,harvest?"harvest":expense?"operation":"sale");
        if(expense)list=expenses(garden,cmd.period);
        if(list.isEmpty())return garden.name+": belirtilen dönem için doğrulanabilir tarihli kayıt bulunamadı.";
        Entry e=list.get(0);
        if(!harvest&&!expense&&(cmd.text.contains("alici")||cmd.text.contains("kime"))){
            try(Cursor c=new SaleRepository(db).getSale(e.id)){if(c.moveToNext()){String value=optional(c,cmd.text.contains("telefon")?"buyer_phone":"buyer_name");return value.isEmpty()?"Alıcı bilgisi bulunamadı.":garden.name+", son satış alıcısı: "+value;}}
            return "Alıcı bilgisi bulunamadı.";
        }
        if(!harvest&&!expense&&cmd.text.contains("ne satt")){
            StringBuilder lines=new StringBuilder();try(Cursor c=new SaleRepository(db).getSaleGrades(e.id)){while(c.moveToNext())lines.append(gradeName(str(c,"grade"))).append(": ").append(number(num(c,"kg"))).append(" kg; ");}
            return garden.name+", son satış "+e.date+": "+(lines.length()==0?"elek ayrıntısı bulunamadı.":lines.toString());
        }
        return garden.name+", "+season+" sezonu: son "+e.label+" "+e.date+", "+(expense?number(e.cost)+" TL":number(e.kg)+" kg"+(harvest?"":", net "+number(e.cost)+" TL"))+".";
    }
    private String saleDistribution(ZeytinAssistantCommand cmd,Garden garden){
        Map<String,Double> kg=new LinkedHashMap<>();double weight=0,amount=0;
        SaleRepository sales=new SaleRepository(db);
        for(Entry e:entries(garden,cmd.period,"sale"))try(Cursor c=sales.getSaleGrades(e.id)){while(c.moveToNext()){
            String grade=str(c,"grade");double n=num(c,"kg"),p=num(c,"price_per_kg");if(n<=0)continue;
            kg.put(grade,kg.getOrDefault(grade,0d)+n);if(cmd.grade.isEmpty()||cmd.grade.equals(grade)){weight+=n;amount+=n*p;}
        }}
        if(kg.isEmpty()||weight<=0)return "Satış fiyatı kaydı bulunamadı.";
        if(cmd.text.contains("hangi elek"))return largest(kg);
        return garden.name+", "+season+" sezonunda kayıtlı satışlarının ağırlıklı birim fiyatı "+number(amount/weight)+" TL/kg. Nakliye ve kesintiler öncesidir.";
    }
    private String ranking(ZeytinAssistantCommand cmd,List<Garden> owned){
        if(cmd.kind==ZeytinAssistantCommand.Kind.IRRIGATION){
            Garden winner=null;Entry chosen=null;List<String> missing=new ArrayList<>();boolean oldest=cmd.text.contains("uzun suredir");
            for(Garden g:owned){List<Entry> list=entries(g,cmd.period,"operation");list.removeIf(e->!type(e.label).equals("sulama"));if(list.isEmpty()){missing.add(g.name);continue;}Entry e=list.get(0);if(chosen==null||(oldest?e.day<chosen.day:e.day>chosen.day)){chosen=e;winner=g;}}
            return (chosen==null?"Sulama kaydı bulunamadı.":winner.name+": kayıtlı son sulama "+chosen.date+".")+(missing.isEmpty()?"":" Sulama kaydı bulunmayan: "+join(missing))+" Bu yalnız kayıt karşılaştırmasıdır; sulama kararı değildir.";
        }
        double max=Double.NEGATIVE_INFINITY;String winner="";String unit=cmd.kind==ZeytinAssistantCommand.Kind.HARVEST||cmd.kind==ZeytinAssistantCommand.Kind.SOLD?"kg":"TL";
        for(Garden g:owned){Totals t=totals(Collections.singletonList(g),false,cmd.period);int rows=cmd.kind==ZeytinAssistantCommand.Kind.HARVEST?t.harvestRows:cmd.kind==ZeytinAssistantCommand.Kind.EXPENSE?t.expenseRows:t.saleRows;if(rows==0)continue;
            double n=cmd.kind==ZeytinAssistantCommand.Kind.HARVEST?t.harvest:cmd.kind==ZeytinAssistantCommand.Kind.EXPENSE?t.expenses:cmd.kind==ZeytinAssistantCommand.Kind.SOLD?t.sold:t.revenue;
            if(n>max){max=n;winner=g.name;}else if(n==max)winner+=" / "+g.name;
        }
        return winner.isEmpty()?"Karşılaştırılabilir kayıt bulunamadı.":season+" sezonu"+(cmd.period.limited()?", "+cmd.period.label():"")+": "+winner+" en yüksek kayıtlı değere sahip: "+number(max)+" "+unit+".";
    }
    private String compare(ZeytinAssistantCommand cmd,List<Garden> owned){
        String[] parts=cmd.text.split("\\bile\\b");if(parts.length!=2)return "Karşılaştırmak istediğin iki bahçeyi 'Girelli ile Afşar' şeklinde söyle.";
        ZeytinAssistantCommand.Resolution a=ZeytinAssistantCommand.resolve(parts[0],owned,0,false),b=ZeytinAssistantCommand.resolve(parts[1],owned,0,false);
        if(!a.explicit||!b.explicit||a.garden==null||b.garden==null||a.garden.id==b.garden.id)return "İki farklı bahçeyi kesin olarak eşleştiremedim. Tam adlarını söyle.";
        StringBuilder out=new StringBuilder(season+" sezonu: ");
        for(Garden g:Arrays.asList(a.garden,b.garden)){Totals t=totals(Collections.singletonList(g),false,cmd.period);out.append(g.name).append(": hasat ").append(brief(t.harvestRows,t.harvest,"kg")).append(", masraf ").append(brief(t.expenseRows,t.expenses,"TL")).append(", satış ").append(brief(t.saleRows,t.revenue,"TL")).append(". ");}return out.toString().trim();
    }
    public String advice(Garden garden,WeatherRepository.WeatherForecast forecast){
        GardenTodayAdvisor.Scope scope=new GardenTodayAdvisor.Scope(owner,season,garden.id);
        List<GardenTodayAdvisor.Record> records=new ArrayList<>();
        for(Entry e:entries(garden,ZeytinAssistantPeriod.parse(""),"operation"))records.add(new GardenTodayAdvisor.Record(scope,e.label,e.date));
        for(Entry e:entries(garden,ZeytinAssistantPeriod.parse(""),"harvest"))records.add(new GardenTodayAdvisor.Record(scope,"Hasat",e.date));
        List<GardenTodayAdvisor.Weather> weather=new ArrayList<>();
        if(forecast!=null){
            if(forecast.current!=null){WeatherRepository.WeatherSnapshot v=forecast.current;GardenTodayAdvisor.Weather w=new GardenTodayAdvisor.Weather(scope,"Şimdi");w.max=v.temperatureC;w.min=v.temperatureC;w.wind=v.windKmh;w.humidity=v.humidity;weather.add(w);}
            for(WeatherRepository.HourlyForecast v:forecast.hourly){GardenTodayAdvisor.Weather w=new GardenTodayAdvisor.Weather(scope,v.timeIso);w.max=v.temperatureC;w.min=v.temperatureC;w.wind=v.windKmh;w.rain=v.precipitationProbability;weather.add(w);}
            for(WeatherRepository.DailyForecast v:forecast.daily){GardenTodayAdvisor.Weather w=new GardenTodayAdvisor.Weather(scope,v.dateIso);w.max=v.maxTemperatureC;w.min=v.minTemperatureC;w.rain=v.precipitationProbability;weather.add(w);}
        }
        StringBuilder out=new StringBuilder(garden.name+": ");for(GardenTodayAdvisor.Recommendation r:new GardenTodayAdvisor().evaluate(scope,today(),records,true,weather))out.append(r.title).append(". ").append(r.message).append(" ");return out.toString();
    }
    public static Long date(String value){
        for(String pattern:new String[]{"yyyy-MM-dd","dd.MM.yyyy","dd/MM/yyyy"}){
            SimpleDateFormat f=new SimpleDateFormat(pattern,Locale.US);f.setLenient(false);f.setTimeZone(TimeZone.getTimeZone("UTC"));
            ParsePosition pos=new ParsePosition(0);Date d=f.parse(value.trim(),pos);
            if(d!=null && pos.getIndex()==value.trim().length())return d.getTime();
        }return null;
    }
    private static String value(int rows,double value,String unit){return rows==0?"kayıt bulunamadı.":number(value)+" "+unit+" kayıtlı.";}
    private static String brief(int rows,double value,String unit){return rows==0?"kayıt bulunamadı":number(value)+" "+unit;}
    public static String number(double value){NumberFormat n=NumberFormat.getNumberInstance(new Locale("tr","TR"));n.setMaximumFractionDigits(2);return n.format(value);}
    private static String gradeName(String grade){return grade.equals("YAGLIK")?"yağlık":grade.equals("DOUBLE")?"Double":grade+" numara";}
    private static String str(Cursor c,String name){int i=c.getColumnIndexOrThrow(name);return c.isNull(i)?"":c.getString(i);}
    private static Long nullableLong(Cursor c,String name){int i=c.getColumnIndexOrThrow(name);return c.isNull(i)?null:c.getLong(i);}
    private static long id(Cursor c,String name){Long n=nullableLong(c,name);return n==null?0:n;}
    private static double num(Cursor c,String name){int i=c.getColumnIndexOrThrow(name);double value=c.isNull(i)?0:c.getDouble(i);if(Double.isNaN(value)||Double.isInfinite(value))throw new IllegalStateException("Invalid numeric record");return value;}
    @Override public void close(){db.close();}
}
