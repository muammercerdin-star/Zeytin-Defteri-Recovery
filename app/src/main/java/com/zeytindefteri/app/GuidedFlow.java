package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Yönlendiren asistan (saf, Android'siz, testli). Konuşmayı Zeytin başlatır ("Bugün bahçede ne yaptın?"), düğmelerle
 * sorarak tamamlar. YENİ KAYIT YOLU YOKTUR: her düğme bugünkü yola bir cümle verir.
 *
 * - İlaçlama, Sulama, Hasat... düğmesi asistana "bugün ilaçladık" gibi bir başlangıç cümlesi verir; sonraki soruları
 *   bugünkü sesli diyaloglar (OperationVoiceDialog, VoiceRecordDialog) sorar, düğmeler yalnız onların cevabını verir.
 * - Satış, Tahsilat ve Masraf için önce birkaç ön soru sorulur (alıcı, kilo...); cevaplardan kurulan cümle bugünkü
 *   satış (İP7 uygulayıcısı), tahsilat ya da işlem yoluna gider. Kayıt her zaman çiftçinin onayıyla.
 * - Düğmeyle yapılan işten sonra kayıttan bir kısa yol cümlesi kurulur; beyin onu aynı işe çevirmiyorsa önerilmez.
 */
final class GuidedFlow {
    private static final Locale TR = new Locale("tr", "TR");

    /** Ön sorudaki seçeneklerin kaynağı: alıcılar, sayı (söyleyerek/yazarak), masraf türleri. */
    enum Choices { BUYERS, NUMBER, EXPENSES }

    /** Ön soru: katalogdaki alan sorusu ve seçenek kaynağı. */
    static final class Ask {
        final String action;
        final String field;
        final String variant;
        final Choices choices;
        final boolean optional;

        Ask(String action, String field, String variant, Choices choices, boolean optional) {
            this.action = action;
            this.field = field;
            this.variant = variant;
            this.choices = choices;
            this.optional = optional;
        }

        /** Soru metni katalogdan (ActionCatalog.question). */
        String question() {
            return ActionCatalog.question(action, field, variant);
        }
    }

    /** Karşılamadaki bir iş düğmesi. */
    static final class Job {
        final String id;
        final String label;
        /** Bugünkü yola verilen cümle (ön sorusu olmayan işler). */
        final String starter;
        /** Katalogdaki eylem. */
        final String action;
        /** İşin türü (İşlem için "Sulama"); yoksa "". */
        final String type;
        final List<Ask> asks;

        Job(String id, String label, String starter, String action, String type, Ask... asks) {
            this.id = id;
            this.label = label;
            this.starter = starter;
            this.action = action;
            this.type = type;
            this.asks = Collections.unmodifiableList(Arrays.asList(asks));
        }
    }

    /** Masraf düğmesinin seçenekleri ve gittikleri cümle ("Başka": çiftçi söyler). */
    static final String[][] EXPENSES = {
            {"Mazot", "bugün mazot aldık"},
            {"İşçilik", "bugün işçi çalıştı"},
            {"İlaç", "bugün ilaç aldık"},
            {"Gübre", "bugün gübre attık"},
            {"Başka", ""},
    };

    private static final Map<String, Job> JOBS = new LinkedHashMap<>();

    static {
        add(new Job("ILACLAMA", "İlaçlama", "bugün ilaçladık", ActionCatalog.ISLEM, "İlaçlama"));
        add(new Job("HASAT", "Hasat", "bugün zeytin topladık", ActionCatalog.HASAT, ""));
        add(new Job("SATIS", "Satış", "", ActionCatalog.SATIS, "",
                new Ask(ActionCatalog.SATIS, "alici", "", Choices.BUYERS, false),
                new Ask(ActionCatalog.SATIS, "kilo", "", Choices.NUMBER, false),
                new Ask(ActionCatalog.SATIS, "fiyat", "", Choices.NUMBER, true)));
        add(new Job("MASRAF", "Masraf", "", ActionCatalog.ISLEM, "",
                new Ask(ActionCatalog.ISLEM, "tur", "masraf", Choices.EXPENSES, false)));
        add(new Job("SULAMA", "Sulama", "bugün suladık", ActionCatalog.ISLEM, "Sulama"));
        add(new Job("BUDAMA", "Budama", "bugün budadık", ActionCatalog.ISLEM, "Budama"));
        add(new Job("GUBRE", "Gübre", "bugün gübre attık", ActionCatalog.ISLEM, "Gübre"));
        add(new Job("TAHSILAT", "Para aldım", "", ActionCatalog.TAHSILAT, "",
                new Ask(ActionCatalog.TAHSILAT, "alici", "", Choices.BUYERS, false),
                new Ask(ActionCatalog.TAHSILAT, "tutar", "", Choices.NUMBER, false)));
        add(new Job("TOPRAK", "Toprak işleme", "bugün toprağı sürdük", ActionCatalog.ISLEM, "Toprak İşleme"));
        add(new Job("SALAMURA", "Salamura", "salamura kurduk", ActionCatalog.KAYIT_BRINE, ""));
    }

    private GuidedFlow() {
    }

    private static void add(Job j) {
        JOBS.put(j.id, j);
    }

    static Job job(String id) {
        return JOBS.get(id);
    }

    static List<Job> all() {
        return new ArrayList<>(JOBS.values());
    }

    /**
     * Karşılamadaki dört düğme: son 60 günün kayıt sayısı (counts: iş kimliği → kayıt sayısı) ve mevsim puanı; eşitlikte
     * GuideSettings.DEFAULT_ORDER. Kayıt yoksa ve mevsim de yoksa İlaçlama, Hasat, Satış, Masraf.
     */
    static List<Job> home(Map<String, Integer> counts, int month) {
        List<String> order = Arrays.asList(GuideSettings.DEFAULT_ORDER);
        List<Job> jobs = new ArrayList<>();
        for (String id : order) {
            jobs.add(JOBS.get(id));
        }
        jobs.sort((a, b) -> {
            int byScore = Integer.compare(score(b.id, counts, month), score(a.id, counts, month));
            return byScore != 0 ? byScore : Integer.compare(order.indexOf(a.id), order.indexOf(b.id));
        });
        return new ArrayList<>(jobs.subList(0, GuideSettings.HOME_SLOTS));
    }

    /** "Başka bir iş": karşılamada olmayan bütün işler, sabit sırayla. */
    static List<Job> others(List<Job> home) {
        List<Job> out = new ArrayList<>();
        for (Job j : JOBS.values()) {
            if (!home.contains(j)) {
                out.add(j);
            }
        }
        return out;
    }

    static int score(String id, Map<String, Integer> counts, int month) {
        Integer n = counts == null ? null : counts.get(id);
        int s = (n == null ? 0 : n) * GuideSettings.RECORD_SCORE;
        for (Object[] season : GuideSettings.SEASONS) {
            if (season[0].equals(id) && GuideSettings.inMonths(month, (Integer) season[1], (Integer) season[2])) {
                s += GuideSettings.SEASON_SCORE;
            }
        }
        return s;
    }

    /** Kaydın türünden (İşlem türü) karşılama düğmesinin kimliği; yoksa "". */
    static String jobOfType(String action, String type) {
        if (ActionCatalog.ISLEM.equals(action)) {
            for (Job j : JOBS.values()) {
                if (j.action.equals(action) && j.type.equals(type)) {
                    return j.id;
                }
            }
            return "Mazot".equals(type) || "İşçilik".equals(type) || "Nakliye".equals(type) ? "MASRAF" : "";
        }
        for (Job j : JOBS.values()) {
            if (j.action.equals(action)) {
                return j.id;
            }
        }
        return "";
    }

    /** Sesli diyaloğun sorduğu alan için düğmeler: bahçeler; ürün ve miktarda alışkanlık önce; isteğe bağlıysa "Geç". */
    static List<String> stepChoices(String field, List<String> gardens, String habitValue, boolean optional) {
        List<String> out = new ArrayList<>();
        if ("bahce".equals(field) && gardens != null) {
            out.addAll(gardens);
        }
        if (habitValue != null && !habitValue.isEmpty() && !out.contains(habitValue)) {
            out.add(0, habitValue);
        }
        if (optional) {
            out.add(SKIP);
        }
        return out;
    }

    /** "Geç" düğmesi (sesli diyalogda "geç" demekle aynı). */
    static final String SKIP = "Geç";

    /** İlerleme yazısı: "2 / 3". */
    static String progress(int step, int total) {
        return Math.max(1, Math.min(step, total)) + " / " + Math.max(1, total);
    }

    /**
     * Ön soruların cevaplarından bugünkü yola gidecek cümle: "Ali Usta'ya 300 kilo sattım kilosu 140 liradan",
     * "Ali Usta'dan 2000 lira aldım", masrafta seçilen türün cümlesi. Eksik zorunlu cevap varsa "".
     */
    static String sentence(Job job, Map<String, String> answers) {
        if (!job.starter.isEmpty()) {
            return job.starter;
        }
        switch (job.id) {
            case "SATIS": {
                String buyer = answers.getOrDefault("alici", "");
                String kilo = number(answers.get("kilo"));
                if (buyer.isEmpty() || kilo.isEmpty()) {
                    return "";
                }
                String price = number(answers.get("fiyat"));
                String tail = price.isEmpty() ? "" : " kilosu " + price + " liradan";
                return HabitModel.dative(buyer) + " " + kilo + " kilo sattım" + tail;
            }
            case "TAHSILAT": {
                String buyer = answers.getOrDefault("alici", "");
                String amount = number(answers.get("tutar"));
                if (buyer.isEmpty() || amount.isEmpty()) {
                    return "";
                }
                return ablative(buyer) + " " + amount + " lira aldım";
            }
            case "MASRAF": {
                String kind = answers.getOrDefault("tur", "");
                for (String[] e : EXPENSES) {
                    if (e[0].equals(kind)) {
                        return e[1];
                    }
                }
                return "";
            }
            default:
                return "";
        }
    }

    /** Söylenen ya da yazılan sayı: "300", "üç yüz" → "300"; anlaşılmazsa "". */
    static String number(String said) {
        if (said == null || said.trim().isEmpty()) {
            return "";
        }
        for (TurkishText.Quantity q : TurkishText.quantities(said.trim())) {
            if (q.value > 0) {
                return ElekVoiceDialog.num(q.value);
            }
        }
        return "";
    }

    /** "Ali Usta" → "Ali Usta'dan", "Mehmet" → "Mehmet'ten". */
    static String ablative(String name) {
        return name + "'" + consonant(name, "d", "t") + vowel(name, "a", "e") + "n";
    }

    /** "Girelli" → "Girelli'de", "Kuyu Dibi" → "Kuyu Dibi'nde" değil "Kuyu Dibi'de" (özel ad: kesmeyle). */
    static String locative(String name) {
        return name + "'" + consonant(name, "d", "t") + vowel(name, "a", "e");
    }

    private static String consonant(String name, String soft, String hard) {
        String low = name.toLowerCase(TR);
        char last = low.isEmpty() ? 'a' : low.charAt(low.length() - 1);
        return "fstkçşhp".indexOf(last) >= 0 ? hard : soft;
    }

    private static String vowel(String name, String back, String front) {
        String low = name.toLowerCase(TR);
        for (int i = low.length() - 1; i >= 0; i--) {
            char c = low.charAt(i);
            if ("aıou".indexOf(c) >= 0) {
                return back;
            }
            if ("eiöü".indexOf(c) >= 0) {
                return front;
            }
        }
        return back;
    }

    // ------------------------------------------------------------------ kısa yol

    /**
     * Kayıttan kısa yol cümlesi ("Bir dahaki sefere sadece şunu demen yeter"). İşlem: "Girelli'de 3 saat suladık";
     * hasat: "Girelli'den 300 kilo topladık". Kurulamazsa "". Gösterilmeden önce verified() ile beyinden geçirilir.
     */
    static String shortcut(String action, String type, String garden, double quantity, String unit) {
        String where = garden == null || garden.isEmpty() ? "bugün" : locative(garden);
        String amount = quantity > 0 && unit != null && !unit.isEmpty() ? ElekVoiceDialog.num(quantity) + " " + unitWord(unit) + " " : "";
        if (ActionCatalog.HASAT.equals(action)) {
            String from = garden == null || garden.isEmpty() ? "bugün" : ablative(garden);
            return from + " " + (quantity > 0 ? ElekVoiceDialog.num(quantity) + " kilo " : "zeytin ") + "topladık";
        }
        if (!ActionCatalog.ISLEM.equals(action)) {
            return "";
        }
        String verb = verb(type);
        if (verb.isEmpty()) {
            return "";
        }
        return where + " " + amount + verb;
    }

    private static String verb(String type) {
        switch (type == null ? "" : type) {
            case "Sulama": return "suladık";
            case "İlaçlama": return "ilaçladık";
            case "Budama": return "budadık";
            case "Gübre": return "gübre attık";
            case "Toprak İşleme": return "toprağı sürdük";
            case "İşçilik": return "işçi çalıştı";
            case "Mazot": return "mazot aldık";
            default: return "";
        }
    }

    private static String unitWord(String unit) {
        switch (unit) {
            case "kg": return "kilo";
            case "L": return "litre";
            case "g": return "gram";
            case "ml": return "ml";
            default: return unit;
        }
    }

    /** Beyin bu cümleyi gerçekten aynı işe çeviriyor mu (aynı eylem, aynı tür, miktar söylendiyse aynı miktar). */
    static boolean verified(String sentence, String action, String type, double quantity) {
        if (sentence == null || sentence.isEmpty()) {
            return false;
        }
        Decision d = new RuleBrain().decide(new Brain.Input(sentence), BrainContext.full(true, AssistantPersona.decode("")::handle));
        if (!d.is(action)) {
            return false;
        }
        if (type != null && !type.isEmpty() && !type.equals(d.fields.get("tur"))) {
            return false;
        }
        if (quantity > 0 && ActionCatalog.ISLEM.equals(action)) {
            return ElekVoiceDialog.num(quantity).equals(d.fields.get("miktar"));
        }
        if (quantity > 0 && ActionCatalog.HASAT.equals(action)) {
            return ElekVoiceDialog.num(quantity).equals(d.fields.get("toplam"));
        }
        return true;
    }

    /** Bu iş türü için kısa yol gösterilebilir mi (en çok GuideSettings.SHORTCUT_MAX_SHOWS kez). */
    static boolean shortcutAllowed(int shownBefore) {
        return shownBefore < GuideSettings.SHORTCUT_MAX_SHOWS;
    }

    // ------------------------------------------------------------------ "Neler yapabilirsin?" ve alıştırma

    /**
     * Çiftçinin kendi bahçe ve alıcı adlarıyla 4 örnek cümle; her biri beyinde anlaşılıyor (yoksa genel örnek).
     */
    static List<String> examples(List<String> gardens, List<String> buyers) {
        String g = gardens == null || gardens.isEmpty() ? "" : gardens.get(0);
        String b = buyers == null || buyers.isEmpty() ? "" : buyers.get(0);
        List<String[]> wanted = new ArrayList<>();
        wanted.add(new String[]{g.isEmpty() ? "bugün 3 saat suladık" : locative(g) + " 3 saat suladık", ActionCatalog.ISLEM});
        wanted.add(new String[]{g.isEmpty() ? "bugün 300 kilo topladık" : ablative(g) + " 300 kilo topladık", ActionCatalog.HASAT});
        wanted.add(new String[]{b.isEmpty() ? "Ali Usta'ya 300 kilo sattım" : HabitModel.dative(b) + " 300 kilo sattım", ActionCatalog.SATIS});
        wanted.add(new String[]{"Kim bana borçlu?", ActionCatalog.ALACAK_SORU});
        List<String> out = new ArrayList<>();
        for (String[] w : wanted) {
            Decision d = new RuleBrain().decide(new Brain.Input(w[0]), BrainContext.full(true, AssistantPersona.decode("")::handle));
            out.add(d.is(w[1]) ? w[0] : fallback(w[1]));
        }
        return out;
    }

    private static String fallback(String action) {
        switch (action) {
            case ActionCatalog.ISLEM: return "bugün 3 saat suladık";
            case ActionCatalog.HASAT: return "bugün 300 kilo topladık";
            case ActionCatalog.SATIS: return "Ali Usta'ya 300 kilo sattım";
            default: return "Kim bana borçlu?";
        }
    }

    /** Alıştırmada söylenecek cümle (Tanecik turunun sonu). */
    static final String PRACTICE = "Bugün suladık";

    /**
     * Alıştırmanın cevabı: anladığını gösterir, KAYIT YAPMAZ. Anlamadıysa yeniden denemeye çağırır.
     */
    static String practiceReply(Decision d) {
        if (d == null || !d.understood()) {
            return "Tam anlayamadım. Bir daha dene: \"" + PRACTICE + "\" de.";
        }
        if (d.is(ActionCatalog.ISLEM)) {
            String type = d.fields.getOrDefault("tur", "");
            return "Anladım: " + type.toLowerCase(TR) + ", bugün. Bu bir alıştırmaydı; hiçbir şey kaydetmedim.";
        }
        return "Anladım. Bu bir alıştırmaydı; hiçbir şey kaydetmedim.";
    }
}
