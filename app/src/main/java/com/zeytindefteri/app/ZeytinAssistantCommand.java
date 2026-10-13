package com.zeytindefteri.app;

import java.text.Normalizer;
import java.util.*;

/** Deterministic Turkish V1 grammar, shared by voice and typed input. No LLM. */
public final class ZeytinAssistantCommand {
    public enum Kind { NAVIGATION, ACTIVE, TREES, AGE, VARIETY, IRRIGATION, FERTILIZER, SPRAY,
        HARVEST, EXPENSE, REVENUE, SOLD, ELEK, BRINE, READY, MARKET, WEATHER, SUMMARY, AREA, LOCATION, WATER_TYPE, NOTE, OPERATION, TODAY, COMPARISON, UNKNOWN }
    public final Kind kind;
    public final String text, target, grade;
    public final boolean total, tomorrow, farmingQuestion;
    public final ZeytinAssistantPeriod period;
    public final boolean latest, count, ranking, crossGarden;
    private ZeytinAssistantCommand(Kind kind, String text, String target, String grade) {
        this.kind=kind; this.text=text; this.target=target; this.grade=grade;
        period=ZeytinAssistantPeriod.parse(text);
        latest=text.contains("son ");
        count=has(text,"kac kez","kac defa","attim mi","var mi","yaptim mi");
        ranking=has(text,"en cok","en fazla","hangi bahce","daha cok","uzun suredir");
        crossGarden=ranking && text.contains("bahce") && text.matches("^(hangi|en cok|en fazla).*" );
        total=text.contains("toplam") || kind==Kind.SUMMARY;
        tomorrow=text.contains("yarin");
        farmingQuestion=text.contains("atayim") || text.contains("yapayim") || text.contains("doz") || text.contains("hastalik");
    }
    public static String normalize(String s) {
        if (s==null) return "";
        s=s.toLowerCase(new Locale("tr","TR")).replace('ı','i');
        return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }
    private static boolean has(String s,String... words) { for(String w:words) if(s.contains(w))return true; return false; }
    public static ZeytinAssistantCommand parse(String raw) {
        String s=language(normalize(raw)), target="", grade="";
        if (s.contains("yaglik")) grade="YAGLIK";
        else if(has(s,"double","duble"))grade="DOUBLE";
        else {
            String[] names={"bir","iki","uc","dort"};
            for(int i=1;i<=4;i++) if(s.matches(".*\\b("+i+"|"+names[i-1]+") (numara|no).*")) grade=String.valueOf(i);
        }
        Kind k=Kind.UNKNOWN;
        // Read-only V1: explicit destructive/mutation/medical commands never fall through to totals.
        if(s.matches(".*\\b(sil|kaydet|guncelle|doz|teshis)\\b.*") || has(s,"kac litre","kac gram")) return new ZeytinAssistantCommand(k,s,target,grade);
        if (s.matches(".*\\b(ac|ekle|goster|git|don)(abilir misin|ar misin)?\\b.*")) {
            if(has(s,"elek","elegi"))target="ELEK";
            else if(has(s,"hasat","hasadi"))target="HARVEST";
            else if(has(s,"satis"))target="SALE";
            else if(has(s,"sulama"))target="Sulama";
            else if(has(s,"gubre"))target="Gübre";
            else if(has(s,"ilac"))target="İlaçlama";
            else if(has(s,"salamura","stok"))target="BRINE";
            else if(has(s,"bahce"))target="GARDENS";
            else if(has(s,"piyasa","fiyat"))target="MARKET";
            else if(has(s,"meydan","uretici akis"))target="MEYDAN";
            else if(has(s,"rapor"))target="REPORTS";
            else if(has(s,"ana sayfa","anasayfa"))target="HOME";
            if(!target.isEmpty())k=Kind.NAVIGATION;
        } else if(has(s,"karsilastir"))k=Kind.COMPARISON;
        else if(has(s,"ne yapmam lazim","bugun ne var","bugun bahcede ne var","bugun ne oner","icin bugun"))k=Kind.TODAY;
        else if(has(s,"dekar","donum"))k=Kind.AREA;
        else if(has(s,"nerede","konumu"))k=Kind.LOCATION;
        else if(has(s,"sulama tipi","sulama yontemi"))k=Kind.WATER_TYPE;
        else if(has(s,"bahce not","notu"))k=Kind.NOTE;
        else if(has(s,"hangi bahce secili","aktif bahce","bahce secili","su an hangi bahce")) k=Kind.ACTIVE;
        else if(has(s,"atayim","yapayim") && has(s,"ilac","sulama"))k=Kind.WEATHER;
        else if(has(s,"agac"))k=Kind.TREES;
        else if(has(s,"yasinda","yasi"))k=Kind.AGE;
        else if(has(s,"cesidi","cesit"))k=Kind.VARIETY;
        else if(has(s,"sulad","sulama","su verd","suyu ne zaman verd"))k=Kind.IRRIGATION;
        else if(has(s,"gubre"))k=Kind.FERTILIZER;
        else if(has(s,"ilac"))k=Kind.SPRAY;
        else if(has(s,"ozet","durumum","ne durumda","ne alemde") && !has(s,"salamura","stok"))k=Kind.SUMMARY;
        else if(has(s,"masraf","harca","gider","cebimden","para gom"))k=Kind.EXPENSE;
        else if(has(s,"satis gelir","toplam satis","satisim","kazand","liralik satis","satis yapt","son satis","son alici","alicinin"))k=Kind.REVENUE;
        else if(has(s,"satt","satilan","kime sat","mali kaca","mali ne"))k=Kind.SOLD;
        else if(has(s,"hasat","toplad","hasadim","urun al","daha cok verdi"))k=Kind.HARVEST;
        else if(has(s,"hazir urun"))k=Kind.READY;
        else if(has(s,"salamura","stok"))k=Kind.BRINE;
        else if(has(s,"piyasa","kac para","fiyat"))k=Kind.MARKET;
        else if(has(s,"hava","yagmur","yagis","ruzgar","nem","derece","en yuksek","en dusuk"))k=Kind.WEATHER;
        else if(has(s,"elek","numara","yaglik","double","duble"))k=Kind.ELEK;
        if(k==Kind.UNKNOWN && has(s,"bu yil","bu sezon") && has(s,"ne yapt"))k=Kind.SUMMARY;
        if(k==Kind.UNKNOWN && has(s,"islem","ne yapt","ne attik"))k=Kind.OPERATION;
        if(k==Kind.UNKNOWN && s.contains("kilo cikti") && grade.isEmpty())k=Kind.HARVEST;
        return new ZeytinAssistantCommand(k,s,target,grade);
    }
    /** Small phrase/stem vocabulary shared across intent families, not full-sentence matching. */
    private static String language(String s){
        return s.replace("bu sene","bu yil").replace("numaradan","numara")
            .replace("hasadi","hasati").replace("satislara","satis")
            .replace("su verdik","sulama yaptik").replace("suyu ne zaman verdik","sulama ne zaman")
            .replace("en son ne attik","en son islem")
            .replace("ne kadar zeytin topladik","ne kadar hasat topladik");
    }
    public static final class Garden {
        public long id;
        public String name="", province="", district="", neighborhood="", variety="";
        public Long trees, age;
        public String area="",waterType="",note="";
        public Garden(long id,String name){this.id=id;this.name=name;}
    }
    public static final class Resolution {
        public Garden garden;
        public String error="";
        public boolean explicit;
    }
    public static Resolution resolve(String raw,List<Garden> gardens,long active, boolean market) {
        Resolution out=new Resolution(); String query=normalize(raw);
        List<Garden> matches=new ArrayList<>();
        int bestLength=0;
        for(Garden garden:gardens) {
            String core=normalize(garden.name).replaceAll("\\b(zeytinligi|zeytinlik|bahcesi|bahce|zeytinlikleri)\\b", "").trim();
            if(core.isEmpty())core=normalize(garden.name);
            // Exact core phrase plus common Turkish suffixes. No fuzzy cross-garden guessing.
            String expression=".*\\b"+java.util.regex.Pattern.quote(core)+"(nin|in|yi|i|de|da|den|dan|nde|nda|nden|ndan)?\\b.*";
            if(query.matches(expression)) {
                if(core.length()>bestLength){matches.clear();bestLength=core.length();}
                if(core.length()==bestLength)matches.add(garden);
            }
        }
        if(matches.isEmpty()) {
            for(Garden garden:gardens) {
                String first=normalize(garden.name).split(" ")[0];
                if(first.length()>3 && query.matches(".*\\b"+java.util.regex.Pattern.quote(first)+"(nin|yi|de|den)?\\b.*"))matches.add(garden);
            }
        }
        if(matches.size()>1) {out.error="Bu adla birden fazla bahçe buldum. Bahçenin tam adını söyle veya aktif bahçeyi seç.";return out;}
        if(matches.size()==1){out.garden=matches.get(0);out.explicit=true;return out;}
        // Unknown explicit proper names must not silently resolve to the active garden.
        if(!market && (raw.matches(".*[’'].*") || query.matches(".*\\b[a-z]+(nin|den|dan) (havasi|masrafi|son|bu sezon).*"))) {
            out.error="Söylediğin bahçeyi kayıtlarında eşleştiremedim. Kayıtlı tam adını söyle.";return out;
        }
        if(!market && query.matches(".+ (havasi|kac agac|kac yasinda).*")) {
            String prefix=query.replaceFirst(" (havasi|kac agac|kac yasinda).*", "");
            if(!prefix.matches("(bahcenin|bahcemin|aktif bahcenin|bahcemde|bu bahcede)")) {
                out.error="Söylediğin bahçeyi eşleştiremedim. Kayıtlı tam adını söyle.";return out;
            }
        }
        if(!market) {
            java.util.regex.Matcher prefix=java.util.regex.Pattern.compile("^(.+?) (en son|son sulama|son gubre|toplam|bu sezon|masrafi|havasi|kac yasinda|kac agac)\\b").matcher(query);
            if(prefix.find() && !prefix.group(1).matches("(bugun|dun|bu hafta|gecen hafta|bu ay|gecen ay|bu sezon|bu sene|ben|benim|bu|bu yil|bu bahceyi|bu bahcede|bahcemin|bahcemde|bu sezon|aktif bahcenin)")) {
                out.error="Söylediğin bahçeyi eşleştiremedim. Kayıtlı tam adını söyle.";return out;
            }
        }
        if(!market){
            java.util.regex.Matcher extra=java.util.regex.Pattern.compile("^(.+?) (kac dekar|kac donum|nerede|sulama tipi|notu|ne durumda|ne alemde|ne kadar masraf|kac kilo cikti)\\b").matcher(query);
            if(extra.find() && !extra.group(1).matches("(bahce|bahcem|bahcemin|bu bahce|aktif bahce|salamura|stok|ben|benim)")){
                out.error="Söylediğin bahçeyi eşleştiremedim. Kayıtlı tam adını söyle.";return out;
            }
            java.util.regex.Matcher form=java.util.regex.Pattern.compile("^(.+?) (sulama|gubreleme|ilaclama|hasat|satis|elek) (ekle|ac)\\b").matcher(query);
            if(form.find() && !form.group(1).matches("(bugun|bu bahceye|aktif bahceye|[1-4] numara|double|yaglik|bugun [0-9]+ kilo|[0-9]+ kilo)")){
                out.error="Kayıt formu için bahçeyi eşleştiremedim. Tam adını söyle.";return out;
            }
        }
        for(Garden garden:gardens)if(garden.id==active){out.garden=garden;return out;}
        out.error="Önce ana sayfadan aktif bir bahçe seç.";return out;
    }
}
