package com.zeytindefteri.app;

import java.util.List;
import java.util.Locale;

/**
 * Asistanla bahçe işi sohbeti (saf, Android'siz, testli). Karşısında insan varmış gibi eksikleri sorar:
 * "Bugün ilaçladım" → "Kolay gelsin! Hangi bahçeyi ilaçladın?" → "Ne attın?" → "Ne kadar kullandın?" →
 * "Masrafı ne kadar tuttu?" → okur → "Kaydedeyim mi?" → "evet" → kaydet. Söylenmiş olanı tekrar sormaz;
 * "bilmiyorum / geç" denirse o soruyu atlar. "Evet" denmeden asla kaydetmez.
 */
public final class OperationVoiceDialog {
    public enum Step { GARDEN, WHAT, QUANTITY, COST, CONFIRM, DONE }
    public enum Action { ASK, SAVE, OPEN_FORM, CANCEL }

    public static final class Reply {
        public final String speech; public final Action action;
        Reply(String speech, Action action) { this.speech = speech; this.action = action; }
    }

    private static final Locale TR = new Locale("tr", "TR");
    private final List<String> gardens;
    public final OperationVoiceEntry entry;
    /** Seçilen bahçenin listedeki sırası; -1 seçilmedi. */
    public int garden = -1;
    /** İlacın / gübrenin adı (not alanına yazılır). */
    public String what = "";
    private Step step;
    private boolean askedWhat, askedQuantity, askedCost;

    /** gardens: bahçe adları; gardenIndex: cümlede adı geçen bahçe (yoksa -1). Tek bahçe varsa o seçilir. */
    public OperationVoiceDialog(OperationVoiceEntry entry, List<String> gardens, int gardenIndex) {
        this.entry = entry;
        this.gardens = gardens;
        this.garden = gardenIndex >= 0 ? gardenIndex : (gardens != null && gardens.size() == 1 ? 0 : -1);
    }

    public Step step() { return step; }
    String type() { return OperationKinds.TYPES[entry.type]; }

    /** İlk soru (sohbetin başı). */
    public Reply start() {
        Reply r = next();
        return new Reply(greet() + r.speech, r.action);
    }

    private String greet() {
        switch (type()) {
            case "Mazot": case "Nakliye": return "Tamam. ";
            default: return "Kolay gelsin! ";
        }
    }

    public Reply handle(String raw) {
        String s = ElekVoiceEntry.normalize(raw == null ? "" : raw);
        if (s.isEmpty()) return ask("Duyamadım. " + question(step));
        if (has(s, "vazgec|vazgectim|iptal|bosver kaydetme|kaydetme")) { step = Step.DONE; return new Reply("Tamam, kaydetmedim.", Action.CANCEL); }
        if (has(s, "formu ac|form ac|elle yazarim|elle girerim")) { step = Step.DONE; return new Reply("Tamam, formu açıyorum.", Action.OPEN_FORM); }
        switch (step) {
            case GARDEN: {
                int g = matchGarden(raw);
                if (g < 0) return ask("Bahçeyi anlayamadım. " + gardenList());
                garden = g;
                return next();
            }
            case WHAT: {
                askedWhat = true;
                if (!skip(s)) {
                    // "Bakır attım, 500 gram": ad + belki miktar
                    OperationVoiceEntry extra = OperationVoiceEntry.parseAnswer(raw);
                    if (extra.quantity > 0 && entry.quantity <= 0) { entry.quantity = extra.quantity; entry.unit = extra.unit.isEmpty() ? defaultUnit() : extra.unit; }
                    if (extra.cost > 0 && entry.cost <= 0) entry.cost = extra.cost;
                    what = productName(raw);
                }
                return next();
            }
            case QUANTITY: {
                askedQuantity = true;
                if (!skip(s)) {
                    OperationVoiceEntry extra = OperationVoiceEntry.parseAnswer(raw);
                    if (extra.quantity <= 0 && extra.cost <= 0) return ask("Sayıyı anlayamadım. " + question(Step.QUANTITY) + " Bilmiyorsan geç de.");
                    if (extra.quantity > 0) { entry.quantity = extra.quantity; entry.unit = extra.unit.isEmpty() ? defaultUnit() : extra.unit; }
                    if (extra.cost > 0) entry.cost = extra.cost;
                }
                return next();
            }
            case COST: {
                askedCost = true;
                if (!skip(s)) {
                    OperationVoiceEntry extra = OperationVoiceEntry.parseAnswer(raw);
                    double v = extra.cost > 0 ? extra.cost : extra.quantity;
                    if (v <= 0) return ask("Tutarı anlayamadım. Kaç lira tuttu? Masraf yoksa yok de.");
                    entry.cost = v;
                }
                return next();
            }
            case CONFIRM: {
                if (has(s, "hayir|degil|yanlis|dur|bekle|olmaz|duzelt\\w*")) {
                    step = Step.DONE;
                    return new Reply("Tamam, formu açıyorum, oradan düzeltirsin.", Action.OPEN_FORM);
                }
                if (has(s, "evet|kaydet|tamam|olur|dogru|aynen|he|onayla")) {
                    step = Step.DONE;
                    return new Reply("Kaydettim. Eline sağlık.", Action.SAVE);
                }
                return ask("Kaydedeyim mi? Evet ya da hayır de.");
            }
            default: return new Reply("", Action.CANCEL);
        }
    }

    /** Sıradaki eksik soru; hepsi tamamsa okuma + onay. */
    private Reply next() {
        if (garden < 0 && gardens != null && !gardens.isEmpty()) { step = Step.GARDEN; return ask(question(Step.GARDEN)); }
        if (!askedWhat && what.isEmpty() && wantsWhat()) { step = Step.WHAT; return ask(question(Step.WHAT)); }
        if (!askedQuantity && entry.quantity <= 0) { step = Step.QUANTITY; return ask(question(Step.QUANTITY)); }
        if (!askedCost && entry.cost <= 0) { step = Step.COST; return ask(question(Step.COST)); }
        step = Step.CONFIRM;
        return ask(summary() + " Kaydedeyim mi?");
    }

    private boolean wantsWhat() { return type().equals("İlaçlama") || type().equals("Gübre"); }

    String question(Step st) {
        String t = type();
        switch (st) {
            case GARDEN:
                switch (t) {
                    case "Sulama": return "Hangi bahçeyi suladın?";
                    case "Gübre": return "Hangi bahçeye gübre attın?";
                    case "İlaçlama": return "Hangi bahçeyi ilaçladın?";
                    case "Budama": return "Hangi bahçeyi budadın?";
                    case "Sinek tuzağı": return "Hangi bahçeye tuzak astın?";
                    case "Toprak İşleme": return "Hangi bahçeyi sürdün?";
                    case "İşçilik": return "Hangi bahçede çalıştılar?";
                    default: return "Hangi bahçe için?";
                }
            case WHAT: return t.equals("Gübre") ? "Hangi gübreyi attın?" : "Ne attın? İlacın adını söyle.";
            case QUANTITY:
                switch (t) {
                    case "Sulama": return "Kaç saat suladın?";
                    case "Gübre": return "Ne kadar attın?";
                    case "İlaçlama": return "Ne kadar kullandın?";
                    case "Budama": return "Kaç ağaç budadın?";
                    case "Sinek tuzağı": return "Kaç tuzak astın?";
                    case "Toprak İşleme": return "Kaç dekar sürdün?";
                    case "İşçilik": return "Kaç işçi çalıştı?";
                    case "Mazot": return "Kaç litre aldın?";
                    case "Nakliye": return "Kaç sefer yaptın?";
                    default: return "Ne kadar?";
                }
            case COST: return t.equals("İşçilik") ? "Kaç lira yevmiye verdin?" : "Masrafı ne kadar tuttu?";
            case CONFIRM: return "Kaydedeyim mi?";
            default: return "";
        }
    }

    /** "Girelli, bugün ilaçlama, Bakır, 500 gram, 300 lira." */
    public String summary() {
        StringBuilder b = new StringBuilder();
        if (garden >= 0 && gardens != null && garden < gardens.size()) b.append(gardens.get(garden)).append(", ");
        b.append(entry.dayOffset == 0 ? "bugün" : entry.dayOffset == -1 ? "dün" : "evvelsi gün").append(' ')
                .append(type().toLowerCase(TR));
        if (!what.isEmpty()) b.append(", ").append(what);
        if (entry.quantity > 0) b.append(", ").append(ElekVoiceDialog.num(entry.quantity)).append(' ').append(unitWord(entry.unit));
        if (entry.cost > 0) b.append(", ").append(ElekVoiceDialog.money(entry.cost)).append(" lira");
        return b.append('.').toString();
    }

    /** Kayda yazılacak birim: türün birimlerindeyse o; değilse türün ilk birimi. */
    public String unitForSave() {
        for (String u : OperationKinds.form(type()).units) if (u.equalsIgnoreCase(entry.unit)) return u;
        return OperationKinds.form(type()).units[0];
    }

    /** Not: ilacın/gübrenin adı; birim türde yoksa miktar da nota. */
    public String noteForSave() {
        StringBuilder b = new StringBuilder(what);
        boolean fits = false;
        for (String u : OperationKinds.form(type()).units) if (u.equalsIgnoreCase(entry.unit)) fits = true;
        if (entry.quantity > 0 && !fits) { if (b.length() > 0) b.append(", "); b.append(ElekVoiceDialog.num(entry.quantity)).append(' ').append(entry.unit); }
        if (b.length() > 0) b.append(" (sesle)");
        return b.toString();
    }

    public double quantityForSave() {
        for (String u : OperationKinds.form(type()).units) if (u.equalsIgnoreCase(entry.unit)) return entry.quantity;
        return 0;
    }

    private String defaultUnit() { return OperationKinds.form(type()).units[0]; }

    private static String unitWord(String u) {
        switch (u) {
            case "kg": return "kilo"; case "g": return "gram"; case "L": return "litre"; case "ml": return "mililitre";
            case "m³": return "metreküp"; default: return u;
        }
    }

    private int matchGarden(String raw) {
        if (gardens == null) return -1;
        String said = ElekVoiceBuyer.spokenName(raw.replaceAll("(?iu)\\b(bahçe\\p{L}*|bahce\\p{L}*|olan|tarla\\p{L}*)\\b", " "));
        ElekVoiceBuyer.Match m = ElekVoiceBuyer.match(said, gardens);
        if (m.kind == ElekVoiceBuyer.Kind.EXISTING) return gardens.indexOf(m.name);
        // "Girelliyi / Girelli'ye": ek atılmış hâliyle dene
        String[] w = said.split(" ");
        if (w.length > 0) {
            String last = w[w.length - 1];
            for (String suf : new String[]{"yı", "yi", "yu", "yü", "ya", "ye", "ı", "i", "u", "ü", "a", "e", "da", "de", "ta", "te"}) {
                if (last.length() > suf.length() + 2 && last.toLowerCase(TR).endsWith(suf)) {
                    w[w.length - 1] = last.substring(0, last.length() - suf.length());
                    ElekVoiceBuyer.Match again = ElekVoiceBuyer.match(String.join(" ", w), gardens);
                    if (again.kind == ElekVoiceBuyer.Kind.EXISTING) return gardens.indexOf(again.name);
                    w[w.length - 1] = last;
                }
            }
        }
        // "birinci / ikinci"
        String[] order = {"birinci|ilk", "ikinci", "ucuncu", "dorduncu"};
        String s = ElekVoiceEntry.normalize(raw);
        for (int i = 0; i < order.length && i < gardens.size(); i++) if (has(s, order[i])) return i;
        return -1;
    }

    private String gardenList() {
        if (gardens == null || gardens.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < gardens.size() && i < 4; i++) { if (i > 0) b.append(", "); b.append(gardens.get(i)); }
        return b.append(". Hangisi?").toString();
    }

    /** "Bakır attım 500 gram" → "Bakır"; sayı, birim ve fiil sözleri atılır. */
    static String productName(String raw) {
        StringBuilder b = new StringBuilder();
        for (String w : raw.split("[\\s,.]+")) {
            if (w.isEmpty()) continue;
            String k = ElekVoiceEntry.normalize(w);
            if (k.isEmpty() || k.matches("\\d+([.,]\\d+)?") || !OperationVoiceEntry.unit(k).isEmpty()) continue;
            if (k.matches("(attim|attik|verdim|verdik|kullandim|kullandik|sikdim|siktim|attim|actim|serptim|serptik|koydum|"
                    + "ilac|ilaci|ilacini|gubre|gubreyi|gubresi|adi|onu|bunu|ondan|bir|iki|uc|dort|bes|alti|yedi|sekiz|dokuz|on|yirmi|otuz|kirk|elli|"
                    + "altmis|yetmis|seksen|doksan|yuz|bin|bucuk|tane|kadar|biraz|ve|ile)")) continue;
            if (b.length() > 0) b.append(' ');
            b.append(w.substring(0, 1).toUpperCase(TR)).append(w.substring(1).toLowerCase(TR));
        }
        return b.toString();
    }

    private static boolean skip(String s) { return has(s, "bilmiyorum|hatirlamiyorum|gec|yok|bos ver|bosver|atla|sonra|onemli degil"); }

    private static boolean has(String s, String words) { return s.matches(".*\\b(" + words + ")\\b.*"); }

    private static Reply ask(String speech) { return new Reply(speech, Action.ASK); }
}
