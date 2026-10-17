package com.zeytindefteri.app;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.zeytindefteri.app.ZeytinAssistantCommand.Kind;

/**
 * Asistanın "anlama" katmanı (tuval: "Sesli asistan · öneri · yarım anlama / konu kartları / anladığım etiketleri /
 * etiketi düzelt / kurallar"). Saf Java; masaüstünde test edilir (tests/assistant_hints).
 *
 * Yalnızca kalıba uymayan sorularda devreye girer: bugünkü kalıplar ve cevaplar değişmez. Yapay zekâ yok,
 * internet gerekmez; asistan yine yalnızca okur ve rakam tahmin etmez. Önerilen her soru, panelde
 * gösterilmeden önce bugünkü çözümleyiciden geçirilir; beklenen türe düşmeyen öneri gösterilmez.
 */
public final class ZeytinAssistantHints {
    private ZeytinAssistantHints() {}

    public enum Topic {
        IRRIGATION("Sulama", "son sulama, kaç kez", "sulamayı", "sulama", "Sulama aç",
                "\\b(su|suy\\w*|sula\\w*|damla\\w*)\\b"),
        CARE("İlaç ve gübre", "son ilaçlama, gübre", "ilaçlamayı ya da gübreyi", "ilaçlama ya da gübre", "İlaçlama aç",
                "\\b(ilac\\w*|zirai|gubre\\w*)\\b"),
        HARVEST("Hasat ve elek", "kaç kilo, hangi elek", "hasadı", "hasat ya da elek", "Hasat aç",
                "\\b(hasa[td]\\w*|topla\\w*|elek\\w*|numara\\w*|double|duble|yaglik\\w*|salamura\\w*|rekolte\\w*)\\b"),
        MONEY("Para ve satış", "masraf, gelir, alıcı", "parayı ve satışı", "masraf ya da satış", "Satış aç",
                "\\b(masraf\\w*|harca\\w*|para\\w*|gider\\w*|gelir\\w*|sat\\w*|alici\\w*|kazan\\w*|lira\\w*|zarar\\w*|kar|kari|karim)\\b"),
        WEATHER("Hava", "yağmur, rüzgâr, sıcaklık", "havayı", "", "",
                "\\b(hava\\w*|yagmur\\w*|yagis\\w*|yagacak\\w*|yagar|ruzgar\\w*|nem\\w*|derece\\w*|sicak\\w*|soguk\\w*|firtina\\w*)\\b"),
        MARKET("Piyasa", "bölge fiyatı", "piyasayı", "", "",
                "\\b(piyasa\\w*|fiyat\\w*|kaca|tuccar\\w*|borsa\\w*)\\b|kac para|kac lira");

        final String label, sub, accusative, missing, open;
        final Pattern pattern;
        Topic(String label, String sub, String accusative, String missing, String open, String regex) {
            this.label = label; this.sub = sub; this.accusative = accusative; this.missing = missing; this.open = open;
            this.pattern = Pattern.compile(regex);
        }
        /** Hava ve piyasa yerel kayıt gerektirmez; diğerleri için bu sezon kayıt olmalı. */
        boolean needsRecords() { return !missing.isEmpty(); }
    }

    /** Öneri kalıbı: dönem ve bahçe eklenebilir mi, çözümleyicide hangi türe düşmeli. */
    static final class Template {
        final String base; final Kind kind; final boolean period, garden;
        Template(String base, Kind kind, boolean period, boolean garden) {
            this.base = base; this.kind = kind; this.period = period; this.garden = garden;
        }
    }

    static List<Template> templates(Topic topic) {
        switch (topic) {
            case IRRIGATION: return Arrays.asList(
                    new Template("son sulama ne zaman?", Kind.IRRIGATION, false, true),
                    new Template("kaç kez sulama yaptım?", Kind.IRRIGATION, true, true),
                    new Template("sulama masrafı ne kadar?", Kind.EXPENSE, true, true));
            case CARE: return Arrays.asList(
                    new Template("son ilaçlama ne zaman?", Kind.SPRAY, false, true),
                    new Template("son gübreleme ne zaman?", Kind.FERTILIZER, false, true),
                    new Template("kaç kez ilaçlama yaptım?", Kind.SPRAY, true, true));
            case HARVEST: return Arrays.asList(
                    new Template("kaç kilo hasat yaptım?", Kind.HARVEST, true, true),
                    new Template("elek dağılımı nasıl?", Kind.ELEK, true, true),
                    new Template("salamurada kaç kilo var?", Kind.BRINE, false, true));
            case MONEY: return Arrays.asList(
                    new Template("masrafım kaç?", Kind.EXPENSE, true, true),
                    new Template("satış gelirim kaç?", Kind.REVENUE, true, true),
                    new Template("son alıcı kim?", Kind.REVENUE, false, true));
            case WEATHER: return Arrays.asList(
                    new Template("hava nasıl?", Kind.WEATHER, false, true),
                    new Template("yarın yağmur var mı?", Kind.WEATHER, false, true),
                    new Template("yarın rüzgâr kaç?", Kind.WEATHER, false, true));
            default: return Arrays.asList(
                    new Template("piyasa nasıl?", Kind.MARKET, false, false),
                    new Template("2 numara kaç para?", Kind.MARKET, false, false),
                    new Template("benim 2 numara satış fiyatım", Kind.MARKET, false, false));
        }
    }

    static final int MAX_SUGGESTIONS = 3;
    private static final Locale TR = new Locale("tr", "TR");

    /** Panelde gösterilecek yardım: ya yakın sorular (A) ya konu kartları (B). */
    static final class Hint {
        final boolean cards;
        final String header, message;
        final List<String> questions;
        final List<Topic> topics;
        Hint(boolean cards, String header, String message, List<String> questions, List<Topic> topics) {
            this.cards = cards; this.header = header; this.message = message;
            this.questions = Collections.unmodifiableList(questions); this.topics = Collections.unmodifiableList(topics);
        }
    }

    /** "Ne sorabilirim?", "Neler biliyorsun?", "Yardım". */
    static boolean asksHelp(String normalized) {
        String s = normalized == null ? "" : normalized;
        return s.matches(".*\\b(ne|neler|hangi \\w+)\\b.*\\b(sorabilirim|sorayim|sorabilir miyim|soracagim)\\b.*")
                || s.matches(".*\\b(neler|ne) (biliyorsun|yapabilirsin|yapabiliyorsun)\\b.*")
                || s.matches("(yardim|yardim et|yardim eder misin|nasil kullanilir|nasil kullanacagim)");
    }

    /** Sorudaki konular, ilk geçtiği sıraya göre. */
    static List<Topic> topics(String normalized) {
        String s = normalized == null ? "" : normalized;
        List<Topic> found = new ArrayList<>();
        final Map<Topic, Integer> at = new EnumMap<>(Topic.class);
        for (Topic t : Topic.values()) {
            Matcher m = t.pattern.matcher(s);
            if (m.find()) { found.add(t); at.put(t, m.start()); }
        }
        Collections.sort(found, (a, b) -> Integer.compare(at.get(a), at.get(b)));
        return found;
    }

    /** Sorunun kendi yazımıyla, konuyu ele veren sözcükler ("suyu", "ilacı"); en çok üç. */
    static List<String> words(String raw, List<Topic> topics) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String token : raw.trim().split("\\s+")) {
            String clean = token.replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "");
            String key = ZeytinAssistantCommand.normalize(clean);
            if (key.isEmpty()) continue;
            for (Topic t : topics) {
                if (t.pattern.matcher(key).find() && !out.contains(clean)) { out.add(clean); break; }
            }
            if (out.size() == 3) break;
        }
        return out;
    }

    /**
     * Kalıba uymayan soru için yardım.
     * @param raw        sorunun kendisi
     * @param gardenName soruda adı açıkça geçen bahçe (yoksa boş): önerilere aynen taşınır
     * @param recorded   bu sezon kaydı olan konular; kaydı olmayan konunun sorusu önerilmez
     */
    static Hint hint(String raw, String gardenName, Set<Topic> recorded) {
        String s = ZeytinAssistantCommand.normalize(raw);
        if (asksHelp(s)) return cards("Kayıtların, hava ve piyasa hakkında sorabilirsin. Bir konu seç:");
        List<Topic> topics = topics(s);
        if (topics.isEmpty()) return cards("Bunu bilmiyorum. Kayıtların, hava ve piyasa hakkında sorabilirsin:");
        String garden = gardenName == null ? "" : gardenName.trim();
        String period = ZeytinAssistantPeriod.parse(s).name;
        List<String> quoted = new ArrayList<>();
        for (String w : words(raw, topics)) quoted.add("“" + w + "”");
        if (!garden.isEmpty()) quoted.add("“" + garden + "”");
        String heard = joinTr(quoted);

        List<Topic> usable = new ArrayList<>();
        for (Topic t : topics) if (!t.needsRecords() || (recorded != null && recorded.contains(t))) usable.add(t);
        if (usable.isEmpty()) {
            Topic first = topics.get(0);
            String msg = (heard.isEmpty() ? "" : "Sorunda " + heard + " geçiyor ama ") + "henüz " + first.missing
                    + " kaydın yok. “" + first.open + "” dersen ekleyebilirsin.";
            return new Hint(false, "BUNU MU DEMEK İSTEDİN?", capitalize(msg), Collections.singletonList(first.open), topics);
        }
        List<String> questions = suggestions(usable, period, garden);
        String lead = heard.isEmpty() ? "" : "Sorunda " + heard + " geçiyor. ";
        String ask = usable.size() == 1 ? capitalize(usable.get(0).accusative) + " soruyorsan birine dokun:" : "Şunlardan birine dokun:";
        return new Hint(false, "BUNU MU DEMEK İSTEDİN?", lead + ask, questions, usable);
    }

    /** Konu kartına dokunulduğunda o konunun soruları. */
    static Hint topic(Topic topic, Set<Topic> recorded) {
        if (topic.needsRecords() && (recorded == null || !recorded.contains(topic))) {
            return new Hint(false, topic.label.toUpperCase(TR), "Henüz " + topic.missing + " kaydın yok. “" + topic.open
                    + "” dersen ekleyebilirsin.", Collections.singletonList(topic.open), Collections.singletonList(topic));
        }
        return new Hint(false, topic.label.toUpperCase(TR), "Şunlardan birine dokun:",
                suggestions(Collections.singletonList(topic), "", ""), Collections.singletonList(topic));
    }

    static Hint cards(String message) {
        return new Hint(true, "KONULAR", message, Collections.<String>emptyList(), Arrays.asList(Topic.values()));
    }

    /** Tek konu: o konunun üç sorusu. Birden çok konu: önce her konudan birer, sonra sırayla tamamlanır. */
    static List<String> suggestions(List<Topic> topics, String period, String garden) {
        List<String> out = new ArrayList<>();
        int round = 0; boolean added = true;
        while (out.size() < MAX_SUGGESTIONS && added) {
            added = false;
            for (Topic t : topics) {
                List<Template> list = templates(t);
                if (round >= list.size()) continue;
                Template tpl = list.get(round);
                String q = compose(tpl, period, garden);
                added = true;
                if (!out.contains(q) && ZeytinAssistantCommand.parse(q).kind == tpl.kind) out.add(q);
                if (out.size() == MAX_SUGGESTIONS) break;
            }
            round++;
        }
        return out;
    }

    /** "Bu ay Girelli için kaç kez sulama yaptım?" — dönem önce, bahçe "… için" ile (Türkçe ek uyumu gerekmez). */
    static String compose(Template t, String period, String garden) {
        StringBuilder b = new StringBuilder();
        if (t.period) b.append(period == null || period.isEmpty() ? "bu sezon" : ZeytinAssistantPeriod.parse(period).label()).append(' ');
        if (t.garden && garden != null && !garden.trim().isEmpty()) b.append(garden.trim()).append(" için ");
        b.append(t.base);
        return capitalize(b.toString());
    }

    // ------------------------------------------------------------------ Anladığım etiketleri

    enum TagType { TOPIC, GARDEN, PERIOD }
    static final class Tag {
        final TagType type; final String text; final boolean editable;
        Tag(TagType type, String text, boolean editable) { this.type = type; this.text = text; this.editable = editable; }
    }

    /** Cevabın üstündeki etiketler: konu · bahçe · zaman. Ekran açma, anlaşılmayan soru ve karşılaştırmada yok. */
    static List<Tag> understood(ZeytinAssistantCommand cmd, String gardenName, boolean all, int gardenCount) {
        List<Tag> out = new ArrayList<>();
        if (cmd == null || cmd.kind == Kind.UNKNOWN || cmd.kind == Kind.NAVIGATION || cmd.kind == Kind.COMPARISON) return out;
        out.add(new Tag(TagType.TOPIC, topicLabel(cmd), false));
        if (gardenScoped(cmd)) {
            // "Toplam sulama" gibi sorularda cevap yine tek bahçeden hesaplanır: etiket cevabı doğru anlatmalı.
            String name = everyGarden(cmd, all) ? "Tüm bahçeler" : gardenName == null ? "" : gardenName.trim();
            if (!name.isEmpty()) out.add(new Tag(TagType.GARDEN, name, gardenCount > 1));
        }
        if (periodic(cmd)) out.add(new Tag(TagType.PERIOD, periodLabel(cmd.period), true));
        return out;
    }

    static String topicLabel(ZeytinAssistantCommand cmd) {
        switch (cmd.kind) {
            case IRRIGATION: return "Sulama";
            case FERTILIZER: return "Gübre";
            case SPRAY: return "İlaçlama";
            case HARVEST: return "Hasat";
            case EXPENSE: return cmd.opType.isEmpty() ? "Masraf" : (cmd.opType.equals("Gübre") ? "Gübreleme" : cmd.opType) + " masrafı";
            case REVENUE: return "Satış geliri";
            case SOLD: return "Satış";
            case ELEK: return "Elek";
            case BRINE: return "Salamura";
            case READY: return "Hazır ürün";
            case MARKET: return "Piyasa";
            case WEATHER: return "Hava";
            case SUMMARY: return "Sezon özeti";
            case AREA: return "Alan";
            case LOCATION: return "Konum";
            case WATER_TYPE: return "Sulama tipi";
            case NOTE: return "Bahçe notu";
            case ACTIVE: return "Seçili bahçe";
            case TREES: return "Ağaç sayısı";
            case AGE: return "Bahçe yaşı";
            case VARIETY: return "Çeşit";
            case OPERATION: return cmd.opType.isEmpty() ? "İşlemler" : cmd.opType;
            case TODAY: return "Bugün";
            default: return "";
        }
    }

    /** ZeytinAssistantData'daki bahçe sıralaması dalı: cevap tek bir bahçenin değil, bahçelerin karşılaştırması. */
    static boolean rankingAnswer(ZeytinAssistantCommand cmd) {
        return cmd.ranking && cmd.text.contains("bahce") && (cmd.kind == Kind.HARVEST || cmd.kind == Kind.EXPENSE
                || cmd.kind == Kind.REVENUE || cmd.kind == Kind.SOLD || cmd.kind == Kind.IRRIGATION);
    }

    static boolean gardenScoped(ZeytinAssistantCommand cmd) {
        switch (cmd.kind) {
            case MARKET: case COMPARISON: case ACTIVE: case NAVIGATION: case UNKNOWN: return false;
            default: return !rankingAnswer(cmd);
        }
    }

    /** Cevabı döneme göre değişen sorular (dönem etiketi yalnızca bunlarda). */
    static boolean periodic(ZeytinAssistantCommand cmd) {
        switch (cmd.kind) {
            case IRRIGATION: case FERTILIZER: case SPRAY: case OPERATION: case HARVEST: case EXPENSE: case REVENUE:
            case SOLD: case ELEK: case BRINE: case READY: case SUMMARY: return true;
            default: return false;
        }
    }

    /** "Tüm bahçeler" seçeneği yalnızca toplamı tüm bahçelerden hesaplanabilen cevaplarda (ZeytinAssistantData.totals). */
    static boolean supportsAll(ZeytinAssistantCommand cmd) {
        Kind k = cmd.kind;
        boolean totalsKind = k == Kind.HARVEST || k == Kind.EXPENSE || k == Kind.REVENUE || k == Kind.SOLD
                || k == Kind.ELEK || k == Kind.BRINE || k == Kind.READY || k == Kind.SUMMARY;
        if (!totalsKind || rankingAnswer(cmd)) return false;
        if (cmd.text.contains("en buyuk masraf")) return false;
        boolean sale = k == Kind.REVENUE || k == Kind.SOLD;
        if (sale && (cmd.text.contains("kime") || cmd.text.contains("alici") || cmd.text.contains("kaca") || cmd.text.contains("hangi elek"))) return false;
        if (cmd.latest && (k == Kind.HARVEST || k == Kind.REVENUE || k == Kind.SOLD || k == Kind.EXPENSE)) return false;
        return true;
    }

    /** Cevap gerçekten tüm bahçelerden mi hesaplandı (ZeytinAssistantData "all" bayrağını yalnızca toplamlarda kullanır)? */
    static boolean everyGarden(ZeytinAssistantCommand cmd, boolean all) { return all && supportsAll(cmd); }

    /** Zaman seçenekleri (anahtar, etiket). Boş anahtar: bu sezonun tamamı. */
    static final String[][] PERIODS = {
            {"bugun", "Bugün"}, {"dun", "Dün"}, {"bu hafta", "Bu hafta"}, {"gecen hafta", "Geçen hafta"},
            {"bu ay", "Bu ay"}, {"gecen ay", "Geçen ay"}, {"", "Bu sezon"},
    };
    /** Tasarımdaki beş seçenek; şu anki dönem bunların dışındaysa (dün, geçen hafta) o da kendi sırasında eklenir. */
    static List<String[]> periodOptions(ZeytinAssistantPeriod current) {
        String now = current == null ? "" : current.name;
        List<String[]> out = new ArrayList<>();
        for (String[] p : PERIODS) {
            boolean base = !p[0].equals("dun") && !p[0].equals("gecen hafta");
            if (base || p[0].equals(now)) out.add(p);
        }
        return out;
    }

    static String periodLabel(ZeytinAssistantPeriod period) {
        String key = period == null ? "" : period.name;
        for (String[] p : PERIODS) if (p[0].equals(key)) return p[1];
        return "Bu sezon";
    }

    // ------------------------------------------------------------------ yardımcılar

    static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s == null ? "" : s;
        return s.substring(0, 1).toUpperCase(TR) + s.substring(1);
    }

    /** “a”, “b” ve “c” */
    static String joinTr(List<String> items) {
        if (items.isEmpty()) return "";
        if (items.size() == 1) return items.get(0);
        return String.join(", ", items.subList(0, items.size() - 1)) + " ve " + items.get(items.size() - 1);
    }
}
