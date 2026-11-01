package com.zeytindefteri.app;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Asistanla sohbetle kayıt: hasat, salamura, bahçe ekleme, Meydan gönderisi (saf, Android'siz, testli).
 * Karşısında insan varmış gibi eksikleri sorar, söyleneni tekrar sormaz, "bilmiyorum / geç" ile atlar,
 * sonunda okur ve "evet" denince formu her şey yazılı açar (kayıt formun kendi doğrulamasıyla, Kaydet'le).
 * Bahçe işi (ilaçlama, sulama…) OperationVoiceDialog'dadır; elek kendi sesli formundadır.
 */
public final class VoiceRecordDialog {
    public enum Kind { HARVEST, BRINE, GARDEN, POST }
    public enum Action { ASK, OPEN_FORM, CANCEL }
    enum SlotType { GARDEN, NUMBER, TEXT }

    public static final class Reply {
        public final String speech; public final Action action;
        Reply(String speech, Action action) { this.speech = speech; this.action = action; }
    }

    private static final class Slot {
        final String key, question, unit; final SlotType type; final boolean optional;
        Slot(String key, SlotType type, String question, String unit, boolean optional) {
            this.key = key; this.type = type; this.question = question; this.unit = unit; this.optional = optional;
        }
    }

    private static final Locale TR = new Locale("tr", "TR");
    public final Kind kind;
    private final List<String> gardens;
    private final Slot[] slots;
    /** Toplanan değerler: anahtar → sayı (Double) ya da yazı (String); bahçe "garden" → sıra (Integer). */
    public final Map<String, Object> values = new LinkedHashMap<>();
    private final java.util.Set<String> asked = new java.util.HashSet<>();
    private int current = -1;
    private boolean confirming, done;

    public VoiceRecordDialog(Kind kind, List<String> gardens) {
        this.kind = kind;
        this.gardens = gardens;
        switch (kind) {
            case HARVEST: slots = new Slot[]{
                    new Slot("garden", SlotType.GARDEN, "Hangi bahçeden topladın?", "", false),
                    new Slot("kg", SlotType.NUMBER, "Kaç kilo topladın?", "kilo", false),
                    new Slot("labor", SlotType.NUMBER, "İşçilik masrafı oldu mu? Kaç lira?", "lira", true),
                    new Slot("transport", SlotType.NUMBER, "Nakliye tuttu mu? Kaç lira?", "lira", true)};
                break;
            case BRINE: slots = new Slot[]{
                    new Slot("olive", SlotType.NUMBER, "Kaç kilo zeytin bastın?", "kilo", false),
                    new Slot("variety", SlotType.TEXT, "Hangi zeytin? Gemlik mi, Domat mı?", "", true),
                    new Slot("salt", SlotType.NUMBER, "Kaç kilo tuz attın?", "kilo tuz", true),
                    new Slot("water", SlotType.NUMBER, "Kaç litre su koydun?", "litre su", true)};
                break;
            case GARDEN: slots = new Slot[]{
                    new Slot("name", SlotType.TEXT, "Bahçenin adı ne olsun?", "", false),
                    new Slot("trees", SlotType.NUMBER, "Kaç ağaç var?", "ağaç", true),
                    new Slot("variety", SlotType.TEXT, "Hangi zeytin? Gemlik, Domat, Ayvalık?", "", true),
                    new Slot("area", SlotType.NUMBER, "Kaç dönüm?", "dönüm", true)};
                break;
            default: slots = new Slot[]{
                    new Slot("text", SlotType.TEXT, "Ne yazayım? Söyle, ben yazayım.", "", false)};
        }
        if (gardens != null && gardens.size() == 1) values.put("garden", 0);
    }

    // ------------------------------------------------------------ tanıma

    /** Cümle bu kayıtlardan birini başlatıyor mu? Soru cümleleri ("salamura stoğum ne?") başlatmaz. */
    public static Kind detect(String raw) {
        if (raw == null || raw.contains("?")) return null;
        String s = ElekVoiceEntry.normalize(raw);
        if (s.matches(".*(^|\\s)(kac|ne|nedir|nasil|hangi|hangisi|mi|mu|zaman|stok\\p{L}*|durum\\p{L}*|hazir mi)(\\s|$).*")) return null;
        if (s.matches(".*\\b(meydan\\p{L}*|sohbet\\p{L}*|gonderi\\p{L}*|paylas\\p{L}*)\\b.*")
                && s.matches(".*\\b(yaz\\p{L}*|paylas\\p{L}*|gonder\\p{L}*|at)\\b.*")) return Kind.POST;
        if (s.matches(".*\\b(yeni bahce|bahce ekle\\p{L}*|bahce kaydet\\p{L}*|bahce ac\\p{L}*|bahce aldim|bahce olustur\\p{L}*)\\b.*")) return Kind.GARDEN;
        if (s.matches(".*\\b(salamura\\p{L}*|kavanoz\\p{L}*|bidon\\p{L}*)\\b.*")
                && s.matches(".*\\b(bastim|bastik|kurdum|kurduk|yaptim|yaptik|koydum|koyduk|basacagim|kuracagim|yeni|ekle\\p{L}*|gir\\p{L}*)\\b.*")) return Kind.BRINE;
        if (s.matches(".*\\b(hasat\\p{L}*|topladik|topladim|cirptik|cirptim|silkeledik)\\b.*")
                && s.matches(".*\\b(gir\\p{L}*|kaydet\\p{L}*|ekle\\p{L}*|yaz\\p{L}*|topladik|topladim|cirptik|cirptim|silkeledik|ettik|yaptik)\\b.*")) return Kind.HARVEST;
        return null;
    }

    /** İlk cümlede söylenmiş olanlar (ör. "Girelli'den 300 kilo topladık", "50 kilo salamura bastım"). */
    public void prefill(String raw, int gardenIndex) {
        if (gardenIndex >= 0) values.put("garden", gardenIndex);
        String s = ElekVoiceEntry.normalize(raw == null ? "" : raw);
        if (kind == Kind.HARVEST) {
            HarvestVoiceEntry h = HarvestVoiceEntry.parse(raw);
            if (h != null) { values.put("kg", h.total); if (h.graded()) values.put("grades", h.kg.clone()); values.put("day", h.dayOffset); }
        } else if (kind == Kind.BRINE) {
            OperationVoiceEntry e = OperationVoiceEntry.parseAnswer(raw);
            if (e.quantity > 0 && (e.unit.isEmpty() || e.unit.equals("kg"))) values.put("olive", e.quantity);
        } else if (kind == Kind.POST) {
            // "Meydan'a yaz: yağmur başladı" → "yaz"dan sonrası metindir
            String text = raw.replaceFirst("(?iu)^.*?\\b(yaz|paylaş|gönder)\\p{L}*\\s*[:,]?\\s*", "").trim();
            if (text.length() >= 3 && !text.equalsIgnoreCase(raw.trim())) values.put("text", cap(text));
        } else if (kind == Kind.GARDEN) {
            OperationVoiceEntry e = OperationVoiceEntry.parseAnswer(raw);
            if (e.quantity > 0 && "ağaç".equals(e.unit)) values.put("trees", e.quantity);
        }
        if (s.isEmpty()) return;
    }

    // ------------------------------------------------------------ sohbet

    public Reply start() {
        Reply r = next();
        String hi = kind == Kind.POST ? "Tamam. " : kind == Kind.GARDEN ? "Hayırlı olsun! " : "Kolay gelsin! ";
        return new Reply(hi + r.speech, r.action);
    }

    public boolean done() { return done; }

    public Reply handle(String raw) {
        String s = ElekVoiceEntry.normalize(raw == null ? "" : raw);
        if (s.isEmpty()) return ask("Duyamadım. " + (confirming ? "Formu açayım mı?" : slots[current].question));
        if (has(s, "vazgec|vazgectim|iptal|bosver")) { done = true; return new Reply("Tamam, bıraktım.", Action.CANCEL); }
        if (confirming) {
            if (has(s, "hayir|degil|yanlis|dur|bekle")) {
                // Baştan değil: formu yine açar, kullanıcı oradan düzeltir.
                done = true; return new Reply("Tamam, formu açıyorum, oradan düzeltirsin.", Action.OPEN_FORM);
            }
            if (has(s, "evet|tamam|olur|ac|dogru|aynen|he|kaydet")) { done = true; return new Reply(openLine(), Action.OPEN_FORM); }
            return ask(kind == Kind.POST ? "Paylaşım ekranını açayım mı? Evet ya da hayır de." : "Formu açayım mı? Evet ya da hayır de.");
        }
        Slot slot = slots[current];
        asked.add(slot.key);
        boolean skip = has(s, "bilmiyorum|hatirlamiyorum|gec|yok|atla|sonra|bos ver|olmadi|tutmadi");
        if (skip && slot.optional) return next();
        switch (slot.type) {
            case GARDEN: {
                int g = matchGarden(raw);
                if (g < 0) return ask("Bahçeyi anlayamadım. " + gardenList());
                values.put("garden", g);
                break;
            }
            case NUMBER: {
                OperationVoiceEntry e = OperationVoiceEntry.parseAnswer(raw);
                double v = e.quantity > 0 ? e.quantity : e.cost;
                if (slot.key.equals("kg") && e.unit.equals("ton")) v *= 1000;
                if (v <= 0) return ask("Sayıyı anlayamadım. " + slot.question + (slot.optional ? " Bilmiyorsan geç de." : ""));
                values.put(slot.key, v);
                if (slot.key.equals("kg")) {
                    HarvestVoiceEntry h = HarvestVoiceEntry.parse("toplandı " + raw);
                    if (h != null && h.graded()) values.put("grades", h.kg.clone());
                }
                break;
            }
            default: {
                String text = raw.trim();
                if (text.isEmpty()) return ask(slot.question);
                values.put(slot.key, slot.key.equals("text") ? cap(text) : ElekVoiceBuyer.spokenName(text));
            }
        }
        return next();
    }

    private Reply next() {
        for (int i = 0; i < slots.length; i++) {
            Slot sl = slots[i];
            if (values.containsKey(sl.key) || asked.contains(sl.key)) continue;
            if (sl.type == SlotType.GARDEN && (gardens == null || gardens.isEmpty())) continue;
            current = i;
            return ask(sl.question);
        }
        confirming = true;
        return ask(summary() + (kind == Kind.POST ? " Paylaşım ekranını açayım mı?" : " Formu açayım mı, kontrol edip kaydedersin."));
    }

    public String summary() {
        StringBuilder b = new StringBuilder();
        switch (kind) {
            case HARVEST:
                if (values.containsKey("garden")) b.append(gardens.get((Integer) values.get("garden"))).append(", ");
                b.append(day()).append(' ').append(num("kg")).append(" kilo hasat");
                if (values.containsKey("labor")) b.append(", işçilik ").append(money("labor")).append(" lira");
                if (values.containsKey("transport")) b.append(", nakliye ").append(money("transport")).append(" lira");
                break;
            case BRINE:
                b.append(num("olive")).append(" kilo ");
                if (values.containsKey("variety")) b.append(values.get("variety")).append(' ');
                b.append("salamura");
                if (values.containsKey("salt")) b.append(", ").append(num("salt")).append(" kilo tuz");
                if (values.containsKey("water")) b.append(", ").append(num("water")).append(" litre su");
                break;
            case GARDEN:
                b.append("Yeni bahçe: ").append(values.get("name"));
                if (values.containsKey("trees")) b.append(", ").append(num("trees")).append(" ağaç");
                if (values.containsKey("variety")) b.append(", ").append(values.get("variety"));
                if (values.containsKey("area")) b.append(", ").append(num("area")).append(" dönüm");
                break;
            default:
                b.append("Şunu paylaşacağım: ").append(values.get("text"));
        }
        return b.append('.').toString();
    }

    private String openLine() {
        switch (kind) {
            case POST: return "Paylaşım ekranını açıyorum. Göz at, Paylaş'a bas.";
            case GARDEN: return "Bahçe formunu açıyorum. Konumunu da seçip kaydet.";
            default: return "Formu açıyorum, hepsi yazılı. Kontrol edip Kaydet'e bas.";
        }
    }

    public double number(String key) { Object v = values.get(key); return v instanceof Double ? (Double) v : 0; }
    public String text(String key) { Object v = values.get(key); return v instanceof String ? (String) v : ""; }
    public int gardenIndex() { Object v = values.get("garden"); return v instanceof Integer ? (Integer) v : -1; }
    public int dayOffset() { Object v = values.get("day"); return v instanceof Integer ? (Integer) v : 0; }
    public double[] grades() { Object v = values.get("grades"); return v instanceof double[] ? (double[]) v : null; }

    private String day() { int d = dayOffset(); return d == 0 ? "bugün" : d == -1 ? "dün" : "evvelsi gün"; }
    private String num(String k) { return ElekVoiceDialog.num(number(k)); }
    private String money(String k) { return ElekVoiceDialog.money(number(k)); }

    private int matchGarden(String raw) {
        if (gardens == null) return -1;
        String said = ElekVoiceBuyer.spokenName(raw.replaceAll("(?iu)\\b(bahçe\\p{L}*|bahce\\p{L}*|olan|tarla\\p{L}*|topladık|topladım)\\b", " "));
        ElekVoiceBuyer.Match m = ElekVoiceBuyer.match(said, gardens);
        if (m.kind == ElekVoiceBuyer.Kind.EXISTING) return gardens.indexOf(m.name);
        String[] w = said.split(" ");
        if (w.length > 0) {
            String last = w[w.length - 1];
            for (String suf : new String[]{"dan", "den", "tan", "ten", "yı", "yi", "ya", "ye", "ı", "i", "a", "e", "da", "de"}) {
                if (last.length() > suf.length() + 2 && last.toLowerCase(TR).endsWith(suf)) {
                    w[w.length - 1] = last.substring(0, last.length() - suf.length());
                    ElekVoiceBuyer.Match again = ElekVoiceBuyer.match(String.join(" ", w), gardens);
                    if (again.kind == ElekVoiceBuyer.Kind.EXISTING) return gardens.indexOf(again.name);
                    w[w.length - 1] = last;
                }
            }
        }
        String[] order = {"birinci|ilk", "ikinci", "ucuncu", "dorduncu"};
        String s = ElekVoiceEntry.normalize(raw);
        for (int i = 0; i < order.length && i < gardens.size(); i++) if (has(s, order[i])) return i;
        return -1;
    }

    private String gardenList() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < gardens.size() && i < 4; i++) { if (i > 0) b.append(", "); b.append(gardens.get(i)); }
        return b.append(". Hangisi?").toString();
    }

    private static String cap(String t) { return t.isEmpty() ? t : t.substring(0, 1).toUpperCase(TR) + t.substring(1); }
    private static boolean has(String s, String words) { return s.matches(".*\\b(" + words + ")\\b.*"); }
    private static Reply ask(String speech) { return new Reply(speech, Action.ASK); }
}
