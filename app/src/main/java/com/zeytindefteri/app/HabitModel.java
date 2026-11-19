package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Kayıtlardan sezilen alışkanlıklar (İP5; saf, Android'siz, testli). Her açılışta kayıtlardan yeniden hesaplanır,
 * hiçbir yere saklanmaz: kayıt silinince ondan sezilen de kendiliğinden düşer. Gizli izleme yok; yalnız girilen
 * kayıtlar (satış, işlem) ve çiftçinin açık "Bu yanlış" düzeltmeleri (HabitCorrections) kullanılır.
 *
 * Her bilgi aynı kalıptadır: ne, değer, kanıt ("son 6 satışın 4'ü Ali Usta"), güven, son görülme, tür = sezilen.
 * Güven (HabitRules): son 6 benzer kayıtta 4 ve üstü yüksek (alan dolar), 3 orta (yalnız öneri çipi), altı yok.
 * 180 gün görülmeyen bilgi kullanılmaz. Öğretilen ya da söylenen değer her zaman üstündür (forEmpty).
 * Kendiliğinden kayıt yok: model yalnız formu doldurur ya da önerir; Kaydet'e çiftçi basar.
 */
final class HabitModel {
    static final String KIND = "sezilen";

    enum Level { HIGH, MID, NONE }

    private static final Locale TR = new Locale("tr", "TR");

    /** Bir satış kaydı (yalnız gerçek satış; fiyat teklifi değil). */
    static final class Sale {
        final long created;
        final String buyer;
        final String garden;
        /** Sınıf kodu (GradeFormModel.CODES) → kilo başı fiyat; yalnız kilosu ve fiyatı olan sınıflar. */
        final Map<String, Double> prices;
        /** Sınıf kodu → kilo (satışın ana sınıfı için). */
        final Map<String, Double> kilos;

        Sale(long created, String buyer, String garden, Map<String, Double> prices, Map<String, Double> kilos) {
            this.created = created;
            this.buyer = clean(buyer);
            this.garden = clean(garden);
            this.prices = prices == null ? new LinkedHashMap<>() : prices;
            this.kilos = kilos == null ? new LinkedHashMap<>() : kilos;
        }
    }

    /** Bir işlem kaydı. */
    static final class Op {
        final long created;
        final String type;
        final String note;
        final double quantity;
        final String unit;
        final String garden;

        Op(long created, String type, String note, double quantity, String unit, String garden) {
            this.created = created;
            this.type = "Gübreleme".equalsIgnoreCase(clean(type)) ? "Gübre" : clean(type);
            this.note = clean(note);
            this.quantity = quantity;
            this.unit = clean(unit);
            this.garden = clean(garden);
        }

        /** Benzer kayıt anahtarı: tür; "Diğer" işlerinde işin adı ("Ot biçme"). */
        String work() {
            String key = LearnedTerms.workKey(type, note);
            return key.isEmpty() ? type : key;
        }
    }

    /** Sezilen bir bilgi. */
    static final class Habit {
        /** Ne: "alici", "fiyat:Ali Usta:3", "bahce:satis", "urun:İlaçlama", "miktar:Sulama", "isci". */
        final String what;
        final String value;
        /** Gösterilen değer: "Ali Usta", "68 TL (3 No)". */
        final String shown;
        final Level level;
        /** Bu değerin görüldüğü en yeni kayıt (ms). */
        final long lastSeen;
        final String kind = KIND;
        /** Miktar ve fiyat için sayı ve birim (yoksa 0, ""). */
        final double number;
        final String unit;
        /** En yeni benzer kayıt da bu değerde mi ("geçen sefer de"). */
        final boolean last;
        /** Kanıtın sayıları: son kaç kayda bakıldı, kaçında bu değer var; kayıtların adı ("satışın"). */
        final int window;
        final int count;
        final String noun;
        /** "4'ünde", "6'sında": "son 6 ilaçlama kaydının 4'ünde aynı not". */
    static String locativeOf(int n) {
        switch (n) {
            case 1: return "'inde";
            case 2: return "'sinde";
            case 3: return "'ünde";
            case 4: return "'ünde";
            case 5: return "'inde";
            case 6: return "'sında";
            default: return "'ında";
        }
    }

    /** Kişi adı ya da tutar içeren not: değer gerekçede gösterilmez. */
        final boolean hidden;

        Habit(String what, String value, String shown, Level level, long lastSeen, double number, String unit, boolean last,
              int window, int count, String noun, boolean hidden) {
            this.what = what;
            this.value = value;
            this.shown = shown;
            this.level = level;
            this.lastSeen = lastSeen;
            this.number = number;
            this.unit = unit;
            this.last = last;
            this.window = window;
            this.count = count;
            this.noun = noun;
            this.hidden = hidden;
        }

        Habit with(String shown, double number, String unit, boolean hidden) {
            return new Habit(what, value, shown, level, lastSeen, number, unit, last, window, count, noun, hidden);
        }

        String category() {
            return HabitCorrections.category(what);
        }

        /** noun "Ali Usta'ya|satışın" → "Ali Usta'ya " (kime yapılan satışlar). */
        private String scope() {
            int bar = noun.indexOf('|');
            return bar < 0 ? "" : noun.substring(0, bar) + " ";
        }

        private String plainNoun() {
            int bar = noun.indexOf('|');
            return bar < 0 ? noun : noun.substring(bar + 1);
        }

        /** Kanıt: "son 6 satışın 4'ü Ali Usta"; gizli notta "son 6 ilaçlama kaydının 4'ünde aynı not". */
        String evidence() {
            String head = scope() + "son " + window + " " + plainNoun() + " " + count;
            if (hidden) {
                return head + locativeOf(count) + " aynı not";
            }
            return head + ordinalOf(count) + " " + shown;
        }

        /** Formun yanındaki kısa, sakin gerekçe: "Geçen sefer de Ali Usta · son 6 satışın 4'ü". */
        String reason() {
            if (last && !hidden) {
                return "Geçen sefer de " + shown + " · " + scope() + "son " + window + " " + plainNoun() + " " + count + ordinalOf(count);
            }
            return capital(evidence());
        }
    }

    private final List<Sale> sales;
    private final List<Op> ops;
    private final long now;
    private final HabitCorrections.Store corrections;
    /** Alıcı adları: notlarda kişi adı var mı (gerekçede gösterilmez). */
    private final List<String> names = new ArrayList<>();

    /** sales / ops: en yeni önce ya da karışık (burada sıralanır). now: şimdiki zaman (ms). */
    HabitModel(List<Sale> sales, List<Op> ops, long now, HabitCorrections.Store corrections) {
        this.sales = newestFirst(sales == null ? new ArrayList<>() : new ArrayList<>(sales));
        this.ops = newestFirstOps(ops == null ? new ArrayList<>() : new ArrayList<>(ops));
        this.now = now;
        this.corrections = corrections == null ? new HabitCorrections.Memory() : corrections;
        for (Sale s : this.sales) {
            if (!s.buyer.isEmpty() && !names.contains(s.buyer)) {
                names.add(s.buyer);
            }
        }
    }

    // ============================================================ SATIŞ

    /** Satışta en sık alıcı. */
    Habit buyer() {
        List<String> values = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        for (Sale s : sales) {
            values.add(s.buyer);
            times.add(s.created);
        }
        return usable(share(HabitCorrections.BUYER, values, times, "satışın"));
    }

    /** Bu alıcıya son kilo başı fiyat (sınıfa göre). */
    Habit price(String buyer, String grade) {
        List<String> values = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        for (Sale s : sales) {
            Double p = s.prices.get(grade);
            if (!same(s.buyer, buyer) || p == null || p <= 0) {
                continue;
            }
            values.add(money(p));
            times.add(s.created);
        }
        String what = HabitCorrections.PRICE + ":" + clean(buyer) + ":" + grade;
        Habit h = share(what, values, times, dative(clean(buyer)) + "|satışın");
        if (h == null) {
            return null;
        }
        String shown = h.value + " TL (" + gradeLabel(grade) + ")";
        return usable(h.with(shown, number(h.value), "TL", false));
    }

    /** Bu alıcıya en çok satılan sınıf (ana sayfadaki "+" menüsünün satış satırı için). */
    Habit grade(String buyer) {
        List<String> values = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        for (Sale s : sales) {
            if (!same(s.buyer, buyer)) {
                continue;
            }
            values.add(mainGrade(s));
            times.add(s.created);
        }
        return usable(share(HabitCorrections.GRADE + ":" + clean(buyer), values, times, dative(clean(buyer)) + "|satışın"));
    }

    /** Satışta en sık bahçe. */
    Habit saleGarden() {
        List<String> values = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        for (Sale s : sales) {
            values.add(s.garden);
            times.add(s.created);
        }
        return usable(share(HabitCorrections.GARDEN + ":satis", values, times, "satışın"));
    }

    /**
     * "+" menüsündeki Satış satırının alt yazısı: yalnız alıcı YÜKSEK güvendeyse ("Ali Usta'ya satış · 3 No · 68 TL hazır").
     * Sınıf ve fiyat da yüksek güvendeyse eklenir. Değilse null (bugünkü yazı kalır).
     */
    String saleLine() {
        Habit b = buyer();
        if (b == null || b.level != Level.HIGH) {
            return null;
        }
        StringBuilder line = new StringBuilder(dative(b.value)).append(" satış");
        Habit g = grade(b.value);
        if (g != null && g.level == Level.HIGH) {
            line.append(" · ").append(gradeLabel(g.value));
            Habit p = price(b.value, g.value);
            if (p != null && p.level == Level.HIGH) {
                line.append(" · ").append(p.value).append(" TL");
            }
        }
        return line.append(" hazır").toString();
    }

    // ============================================================ İŞLEM

    /** İşlemde en sık bahçe. */
    Habit operationGarden() {
        List<String> values = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        for (Op o : ops) {
            values.add(o.garden);
            times.add(o.created);
        }
        return usable(share(HabitCorrections.GARDEN + ":islem", values, times, "işlemin"));
    }

    /** İlaçlama / gübrede en sık ilaç ya da gübre (not alanı; yalnız tıpatıp aynı notlar sayılır). */
    Habit product(String work) {
        if (!"İlaçlama".equals(work) && !"Gübre".equals(work)) {
            return null;
        }
        List<String> values = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        for (Op o : ops) {
            if (!o.work().equals(work)) {
                continue;
            }
            values.add(o.note);
            times.add(o.created);
        }
        String noun = work.toLowerCase(TR) + " kaydının";
        Habit h = share(HabitCorrections.PRODUCT + ":" + work, values, times, noun);
        if (h != null && sensitive(h.value)) {
            // Kişi adı ya da tutar içeren not: forma yazılabilir ama gerekçede gösterilmez.
            h = h.with(h.shown, 0, "", true);
        }
        return usable(h);
    }

    /**
     * Bu işte en sık miktar ve birim ("18 kg"). İşçilikte "kişi" birimiyle girilen işçi sayısı (kararla, hasat
     * formuna alan eklenmez). work: tür ya da "Diğer" işinin adı ("Ot biçme").
     */
    Habit amount(String work) {
        boolean workers = "İşçilik".equals(work);
        List<String> values = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        for (Op o : ops) {
            if (!o.work().equals(work)) {
                continue;
            }
            if (workers && !"kişi".equals(o.unit)) {
                continue;
            }
            values.add(o.quantity > 0 ? ElekVoiceDialog.num(o.quantity) + " " + o.unit : "");
            times.add(o.created);
        }
        String what = workers ? HabitCorrections.WORKERS : HabitCorrections.AMOUNT + ":" + work;
        String noun = work.toLowerCase(TR) + " kaydının";
        Habit h = share(what, values, times, noun);
        if (h == null) {
            return null;
        }
        int space = h.value.lastIndexOf(' ');
        double n = number(h.value.substring(0, space));
        String unit = h.value.substring(space + 1);
        return usable(h.with(h.shown, n, unit, false));
    }

    // ============================================================ ÖĞRETİLEN / SÖYLENEN ÜSTÜNDÜR

    /**
     * Alanda zaten bir değer varsa (cümleden, öğretilen sözden ya da çiftçiden) sezilen bilgi ne doldurur
     * ne önerir. Boşsa bilginin kendisi.
     */
    static Habit forEmpty(String current, Habit habit) {
        if (current != null && !current.trim().isEmpty()) {
            return null;
        }
        return habit;
    }

    // ============================================================ ORTAK

    /** Güveni ve düzeltmeleri uygular: kullanılmayacaksa null. */
    private Habit usable(Habit h) {
        if (h == null || h.level == Level.NONE) {
            return null;
        }
        if (now - h.lastSeen > HabitRules.STALE_DAYS * HabitRules.DAY_MS) {
            return null;
        }
        if (corrections.closed(h.category())) {
            return null;
        }
        long rejected = corrections.rejectedAt(h.what, h.value);
        if (rejected > 0 && newest(h) <= rejected) {
            // "Bu yanlış": bir sonraki kayda kadar önerilmez.
            return null;
        }
        return h;
    }

    /** Bu bilginin türünde en yeni kayıt (ms). */
    private long newest(Habit h) {
        String c = h.category();
        boolean sale = c.equals(HabitCorrections.BUYER) || c.equals(HabitCorrections.PRICE) || c.equals(HabitCorrections.GRADE)
                || h.what.equals(HabitCorrections.GARDEN + ":satis");
        if (sale) {
            return sales.isEmpty() ? 0 : sales.get(0).created;
        }
        return ops.isEmpty() ? 0 : ops.get(0).created;
    }

    /**
     * Son 6 değerde en sık olanı (eşitlikte en yeni). values / times en yeni önce. Boş değer ("alıcısız satış")
     * en sık çıkarsa bilgi yok. noun: "satışın", "ilaçlama kaydının".
     */
    private static Habit share(String what, List<String> values, List<Long> times, String noun) {
        int window = Math.min(values.size(), HabitRules.WINDOW);
        if (window == 0) {
            return null;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < window; i++) {
            counts.merge(values.get(i), 1, Integer::sum);
        }
        String best = null;
        int top = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > top) {
                best = e.getKey();
                top = e.getValue();
            }
        }
        if (best == null || best.isEmpty()) {
            return null;
        }
        long seen = 0;
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).equals(best)) {
                seen = times.get(i);
                break;
            }
        }
        Level level = top >= HabitRules.HIGH ? Level.HIGH : top >= HabitRules.MID ? Level.MID : Level.NONE;
        boolean last = values.get(0).equals(best);
        return new Habit(what, best, best, level, seen, 0, "", last, window, top, noun, false);
    }

    /** "4'ü", "6'sı": "son 6 satışın 4'ü". */
    static String ordinalOf(int n) {
        switch (n) {
            case 1: return "'i";
            case 2: return "'si";
            case 3: return "'ü";
            case 4: return "'ü";
            case 5: return "'i";
            case 6: return "'sı";
            default: return "'ı";
        }
    }

    /** Kişi adı ya da tutar içeren not: gerekçede gösterilmez. */
    boolean sensitive(String note) {
        String n = TurkishText.normalize(note);
        if (n.matches(".*\\d+ ?(tl|lira|liralik)\\b.*") || note.contains("₺")) {
            return true;
        }
        // Hitaplar ekleriyle: "Hasan abiden", "Ali Usta'ya", "Mehmet beyin".
        if (n.matches(".*\\b(usta\\p{L}*|abi(den|ye|nin|ler\\p{L}*|m|miz)?|amca\\p{L}*|hanim\\p{L}*|bey(den|e|in|ler)?|efendi\\p{L}*"
                + "|dayi\\p{L}*|teyze\\p{L}*|aga(dan|ya|nin)?)\\b.*")) {
            return true;
        }
        for (String name : names) {
            String first = TurkishText.normalize(name).split(" ")[0];
            if (first.length() >= 3 && n.matches(".*\\b" + java.util.regex.Pattern.quote(first) + "\\b.*")) {
                return true;
            }
        }
        return false;
    }

    /** "Ali Usta" → "Ali Usta'ya", "Mehmet" → "Mehmet'e", "Ayşe" → "Ayşe'ye". */
    static String dative(String name) {
        String n = clean(name);
        if (n.isEmpty()) {
            return n;
        }
        String low = n.toLowerCase(TR);
        char lastLetter = low.charAt(low.length() - 1);
        char vowel = 'a';
        for (int i = low.length() - 1; i >= 0; i--) {
            char c = low.charAt(i);
            if ("aeıioöuü".indexOf(c) >= 0) {
                vowel = c;
                break;
            }
        }
        boolean front = "eiöü".indexOf(vowel) >= 0;
        boolean endsVowel = "aeıioöuü".indexOf(lastLetter) >= 0;
        return n + "'" + (endsVowel ? "y" : "") + (front ? "e" : "a");
    }

    static String gradeLabel(String code) {
        for (int i = 0; i < GradeFormModel.CODES.length; i++) {
            if (GradeFormModel.CODES[i].equals(code)) {
                return GradeFormModel.LABELS[i];
            }
        }
        return code;
    }

    private static String mainGrade(Sale s) {
        String best = "";
        double most = 0;
        for (Map.Entry<String, Double> e : s.kilos.entrySet()) {
            if (e.getValue() > most) {
                best = e.getKey();
                most = e.getValue();
            }
        }
        return best;
    }

    private static String money(double v) {
        return ElekVoiceDialog.num(v);
    }

    private static double number(String text) {
        try {
            return Double.parseDouble(text.replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean same(String a, String b) {
        return clean(a).equalsIgnoreCase(clean(b)) && !clean(a).isEmpty();
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(TR) + s.substring(1);
    }

    private static List<Sale> newestFirst(List<Sale> list) {
        Collections.sort(list, (a, b) -> Long.compare(b.created, a.created));
        return list;
    }

    private static List<Op> newestFirstOps(List<Op> list) {
        Collections.sort(list, (a, b) -> Long.compare(b.created, a.created));
        return list;
    }
}
