package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Çiftçinin öğrettiği sözler — kişisel sözlük (İP4; saf, Android'siz, testli).
 *
 * "bugün bakırladık" kurallarla anlaşılmaz; çiftçi bir kez "İlaçlama" diye öğretince "bakırla-" kökü hep ilaçlama
 * olarak okunur ("bakırladım", "bakırlıyoruz", "bakırlayacağız"...). Söz yalnız bu telefonda tutulur, internete
 * gitmez. Öğretilen söz kendiliğinden unutulmaz; yalnız çiftçinin "Unut" demesiyle silinir.
 *
 * Kök: TurkishText ile sadeleşmiş kelimeden fiil eki atılır ("bakirladik" → "bakirla"), sondaki a/e de düşer
 * ("bakirl"): böylece "bakırlıyor" ile "bakırladık" aynı köke iner. Fiil eki olmayan kelime kök sayılmaz
 * ("bakır" ilaç adıdır, öğretilen "bakırla-" ile karışmaz).
 */
final class LearnedTerms {
    static final String TAUGHT = "ogretilen";
    static final String SOURCE_VOICE = "ses";
    static final String SOURCE_TEXT = "yazi";
    static final String SOURCE_CLOUD = "bulut-onayli";
    /** Öğrenilen söz ilk birkaç kullanımda "… diye öğrendim" diye haber verir (sonra susar; "Unut" komutu kalır). */
    static final int TELL_USES = 3;

    /** Fiil ekleri (sadeleşmiş yazımla): geçmiş, şimdiki, gelecek, öğrenilen geçmiş, mastar, ulaç. */
    private static final String VERB = "^([a-z]{3,}?)(y?[ae]c[ae][kg][a-z]*|[iu]?yor[a-z]*|m[iu]s[a-z]*|[dt][iu][a-z]*|m[ae]k?|m[ae]k[a-z]{0,3}|y?[iu]p)$";
    /** Her işte geçen genel fiiller ve fiile benzeyen sözler: öğretme sorusu bunlar için açılmaz. */
    private static final Set<String> GENERIC = new HashSet<>();

    static {
        String[] roots = {"et", "yap", "ol", "gel", "git", "al", "ver", "de", "at", "vur", "koy", "bak", "gor", "kal",
                "sat", "cal", "calis", "basl", "bit", "gec", "dur", "ist", "bil", "sor", "soyl", "anl", "konus", "yaz",
                "oku", "ac", "kapat", "sil", "kaydet", "goster", "unut", "ogren", "ara", "bul", "dinl", "sev", "ye",
                "ic", "getir", "gotur", "dok", "kon", "geld", "gitt", "tut", "ded", "dedi", "sim", "had", "san", "belk",
                "kad", "ced", "kar", "gun", "son", "ilk", "on", "ust", "alt", "kac", "ne", "nas"};
        Collections.addAll(GENERIC, roots);
    }

    /**
     * Kişisel / duygu / beden / aile fiilleri: sohbet cümlesinde iş sanılıp "… ne işi?" diye sorulmaz
     * ("bugün yoruldum", "annem hastalandı", "torunum doğdu").
     */
    private static final Set<String> PERSONAL = new HashSet<>();

    /** İş cümlesinin yanında durabilen sözler: zaman, kişi, sayı ("bugün bakırladık", "dün sabah biz bakırladık"). */
    private static final String FRAME_WORDS = "bugun|dun|yarin|sabah|aksam|ogle|ogleden|sonra|once|gece|erken|gec|"
            + "evvelsi|evvelki|onceki|gecen|hafta|haftaya|ay|bu|su|o|biz|ben|hep|beraber|birlikte|de|da|ki|ve|ile|"
            + "pazartesi|sali|carsamba|persembe|cuma|cumartesi|pazar|"
            // bahçe adından kesme işaretiyle ayrılan ek ("Girelli'yi" → girelli + yi)
            + "yi|yu|i|u|ye|ya|e|a|den|dan|ten|tan|nin|nun|in|un|yla|yle|la|le";

    /** Çekimli etken fiil: "bakırladık/-dım/-dı", "bakırlıyoruz", "bakırlayacağız", "bakırlamıştık". */
    private static final String WORK_FORM = ".*([dt][iu](k|m)?|[iu]?yor(um|uz)?|[ae]c[ae]g[iu]z|m[iu]st[iu]k)";

    /** Edilgen ("ödendi", "silindi") ve isim sanılan biçimler sorulmaz. */
    private static final String PASSIVE_FORM = ".*([iu]n|[iu]l|en|an)[dt][iu]";

    static {
        String[] words = {"yoruldum", "sikildim", "agridi", "uzuldum", "sevindim", "hastalandi", "dogdu", "evlendi",
                "korktum", "uyudum", "uyandim", "kizdim", "sinirlendim", "biktim", "acktim", "acikdim", "susadim",
                "usudum", "yattim", "dinlendik", "guldum", "agladim", "ozledim", "bayildim", "dustum", "uzuldu",
                "yoruldu", "hastalandim", "iyilesti", "iyilestim", "oldu", "olduk", "barisdik", "kustum", "darildim",
                "sikildi", "kizdi", "sevindi", "korktu", "uyudu", "agladi", "guldu", "dusundum", "eglendik", "anlamadim",
                "anlamadin", "anladin", "bilmedim", "duymadim", "duydun", "sustum", "bekledim", "sasirdim",
                // piyasa ve fiyat sözleri ("1500'e patladı")
                "patladi", "yukseldi", "artti", "azaldi", "dustu", "kalkti", "indi", "cikti", "firladi", "coktu"};
        for (String w : words) {
            String r = verbRoot(w);
            if (!r.isEmpty()) {
                PERSONAL.add(r);
            }
        }
    }

    /**
     * Sohbet sanılan cümle yine de iş cümlesi biçiminde mi: tek fiili kişisel değil ve yanındaki her söz zaman, kişi,
     * sayı ya da bahçe adı ("bugün bakırladık", "Girelli'yi bakırladık"). "çok yoruldum", "dün maç izledim" değil.
     */
    static boolean workShaped(String raw, List<String> gardens) {
        String word = candidate(raw);
        if (word == null) {
            return false;
        }
        String root = verbRoot(TurkishText.normalize(word));
        if (root.isEmpty() || PERSONAL.contains(root)) {
            return false;
        }
        String folded = TurkishText.fold(word).replaceAll("[^a-z]", "");
        if (!folded.matches(WORK_FORM) || folded.matches(PASSIVE_FORM)) {
            return false;
        }
        String[] words = TurkishText.words(raw);
        if (words.length < 2) {
            return false;
        }
        int verbs = 0;
        for (String w : words) {
            if (!verbRoot(w).isEmpty() && verbRoot(w).equals(root)) {
                verbs++;
                continue;
            }
            if (w.matches("\\d+([.,]\\d+)?") || w.matches(FRAME_WORDS)) {
                continue;
            }
            if (isGarden(w, gardens)) {
                continue;
            }
            return false;
        }
        return verbs == 1;
    }

    private static boolean isGarden(String w, List<String> gardens) {
        if (gardens == null) {
            return false;
        }
        for (String g : gardens) {
            String n = TurkishText.normalize(g);
            if (n.isEmpty() || n.contains(" ")) {
                continue;
            }
            if (w.equals(n) || (w.startsWith(n) && w.length() - n.length() <= 4)) {
                return true;
            }
        }
        return false;
    }

    /** Öğretilen bir söz. */
    static final class Term {
        /** Kök (sadeleşmiş, eksiz): "bakirl". Sözlüğün anahtarı. */
        final String term;
        /** Çiftçinin söylediği hâl (gösterim için): "bakırladık". */
        final String word;
        /** Anlam: eylem (ActionCatalog) + alan değeri ("ISLEM", "tur", "İlaçlama"); alan boş olabilir (HASAT). */
        final String action;
        final String field;
        final String value;
        /** Tür: öğretilen (çiftçi açıkça söyledi). */
        final String kind;
        /** Kaynak: ses | yazi | bulut-onayli. */
        final String source;
        final long created;
        final int uses;

        Term(String term, String word, String action, String field, String value, String kind, String source, long created, int uses) {
            this.term = term;
            this.word = word;
            this.action = action;
            this.field = field == null ? "" : field;
            this.value = value == null ? "" : value;
            this.kind = kind;
            this.source = source;
            this.created = created;
            this.uses = uses;
        }

        Term withUse() {
            return new Term(term, word, action, field, value, kind, source, created, uses + 1);
        }

        /** "ilaçlama", "hasat": cevapta okunan anlam. */
        String meaning() {
            Choice c = choiceOf(this);
            if (c != null) {
                return c.spoken;
            }
            return value.isEmpty() ? action.toLowerCase(java.util.Locale.ROOT) : value.toLowerCase(new java.util.Locale("tr", "TR"));
        }
    }

    /** Sözlük deposu: telefonda SQLite (LearnedTermsDb), testlerde bellek. */
    interface Store {
        List<Term> all();

        Term get(String term);

        void put(Term t);

        boolean remove(String term);

        void used(String term);

        /** "Vazgeç" denen cümle (sadeleşmiş): bir daha sorulmaz. */
        boolean declined(String sentence);

        void decline(String sentence);
    }

    /** Bellekte sözlük (testler, cümle bankası). */
    static final class Memory implements Store {
        private final Map<String, Term> terms = new LinkedHashMap<>();
        private final Set<String> declined = new HashSet<>();

        @Override
        public List<Term> all() {
            return new ArrayList<>(terms.values());
        }

        @Override
        public Term get(String term) {
            return terms.get(term);
        }

        @Override
        public void put(Term t) {
            terms.put(t.term, t);
        }

        @Override
        public boolean remove(String term) {
            return terms.remove(term) != null;
        }

        @Override
        public void used(String term) {
            Term t = terms.get(term);
            if (t != null) {
                terms.put(term, t.withUse());
            }
        }

        @Override
        public boolean declined(String sentence) {
            return declined.contains(sentence);
        }

        @Override
        public void decline(String sentence) {
            declined.add(sentence);
        }
    }

    /** Öğretme penceresindeki büyük düğmeler: anlamı ve kurallara çevrildiği söz. */
    static final class Choice {
        final String label;
        final String spoken;
        final String action;
        final String field;
        final String value;
        /** Öğrenilen sözün yerine kurallara verilen söz (kurallar bunu zaten anlar). */
        final String phrase;

        Choice(String label, String spoken, String action, String field, String value, String phrase) {
            this.label = label;
            this.spoken = spoken;
            this.action = action;
            this.field = field;
            this.value = value;
            this.phrase = phrase;
        }
    }

    static final List<Choice> CHOICES = Collections.unmodifiableList(java.util.Arrays.asList(
            new Choice("İlaçlama", "ilaçlama", ActionCatalog.ISLEM, "tur", "İlaçlama", "ilacladik"),
            new Choice("Gübre", "gübre", ActionCatalog.ISLEM, "tur", "Gübre", "gubre attik"),
            new Choice("Budama", "budama", ActionCatalog.ISLEM, "tur", "Budama", "budadik"),
            new Choice("Sulama", "sulama", ActionCatalog.ISLEM, "tur", "Sulama", "suladik"),
            new Choice("Hasat", "hasat", ActionCatalog.HASAT, "", "", "topladik")));

    private LearnedTerms() {
    }

    // ============================================================ KÖK

    /** Fiil kökü ("bakırladık" → "bakirl"); fiil eki yoksa ya da kök çok kısaysa "". */
    static String verbRoot(String word) {
        String w = TurkishText.fold(word == null ? "" : word).replaceAll("[^a-z]", "");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(VERB).matcher(w);
        if (!m.matches()) {
            return "";
        }
        String root = m.group(1);
        if (root.length() > 3 && (root.endsWith("a") || root.endsWith("e"))) {
            root = root.substring(0, root.length() - 1);
        }
        if (root.length() < 3) {
            return "";
        }
        return root;
    }

    /** Öğretmeye uygun söz: cümledeki SON fiil (Türkçede fiil sondadır), genel fiil değilse. Yoksa null. */
    static String candidate(String raw) {
        String[] words = TurkishText.words(raw);
        String[] original = originalWords(raw);
        for (int i = words.length - 1; i >= 0; i--) {
            String root = verbRoot(words[i]);
            if (root.isEmpty()) {
                continue;
            }
            if (GENERIC.contains(root)) {
                return null;
            }
            boolean aligned = i < original.length && verbRoot(original[i]).equals(root);
            return aligned ? original[i] : words[i];
        }
        return null;
    }

    /** Sorulmaya uygun mu: aday söz var, sözlükte değil ve bu cümle için "Vazgeç" denmedi. */
    static String teachable(String raw, Store store) {
        String word = candidate(raw);
        if (word == null) {
            return null;
        }
        if (store.declined(sentenceKey(raw))) {
            return null;
        }
        if (store.get(verbRoot(word)) != null) {
            return null;
        }
        return word;
    }

    /** Cümledeki öğrenilmiş söz (ilk bulunan) ve kelime sırası; yoksa null. */
    static Term find(String raw, Store store) {
        if (store.all().isEmpty()) {
            return null;
        }
        for (String w : TurkishText.words(raw)) {
            String root = verbRoot(w);
            if (root.isEmpty()) {
                continue;
            }
            Term t = store.get(root);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    /** Öğrenilen sözün yerine anlamının söylenişi konmuş cümle (kurallara verilir). */
    static String rewrite(String raw, Term t) {
        Choice c = choiceOf(t);
        if (c == null) {
            return raw;
        }
        StringBuilder b = new StringBuilder();
        for (String w : TurkishText.words(raw)) {
            if (b.length() > 0) {
                b.append(' ');
            }
            b.append(verbRoot(w).equals(t.term) ? c.phrase : w);
        }
        return b.toString();
    }

    static Choice choiceOf(Term t) {
        for (Choice c : CHOICES) {
            if (c.action.equals(t.action) && c.value.equals(t.value)) {
                return c;
            }
        }
        return null;
    }

    /** Düğme ya da sesli cevap ("ilaçlama", "gübre"...) → seçim; değilse null. */
    static Choice choiceFor(String answer) {
        String a = TurkishText.normalize(answer);
        for (Choice c : CHOICES) {
            String key = TurkishText.normalize(c.label);
            if (a.equals(key) || a.startsWith(key + " ") || a.endsWith(" " + key)) {
                return c;
            }
        }
        return null;
    }

    /** Yeni öğretilen söz. */
    static Term teach(String word, Choice c, String source, long now) {
        return new Term(verbRoot(word), word, c.action, c.field, c.value, TAUGHT, source, now, 0);
    }

    // ============================================================ YEDEK (Ayarlar → Yedekle dosyası)

    /** Yedek dosyasındaki ayar anahtarı. Yalnız elle alınan Yedekle dosyasına girer; bulut yedeğine girmez. */
    static final String BACKUP_KEY = "learned_terms";

    /** Sözlük → tek metin (her söz bir satır, alanlar sekmeyle). Söz yoksa "". */
    static String exportText(List<Term> terms) {
        StringBuilder b = new StringBuilder();
        if (terms == null) {
            return "";
        }
        for (Term t : terms) {
            String[] cells = {t.term, t.word, t.action, t.field, t.value, t.kind, t.source,
                    String.valueOf(t.created), String.valueOf(t.uses)};
            for (int i = 0; i < cells.length; i++) {
                if (i > 0) {
                    b.append('\t');
                }
                b.append(cell(cells[i]));
            }
            b.append('\n');
        }
        return b.toString();
    }

    /**
     * Yedekteki sözleri sözlüğe ekler; telefonda aynı söz varsa telefondaki kalır (üzerine yazılmaz).
     * Bozuk satır ya da tanınmayan anlam (seçeneklerde olmayan iş) atlanır. Eklenen söz sayısı.
     */
    static int importText(String text, Store store) {
        if (text == null || text.isEmpty() || store == null) {
            return 0;
        }
        int added = 0;
        for (String line : text.split("\n")) {
            Term t = parseLine(line);
            if (t == null) {
                continue;
            }
            if (store.get(t.term) != null) {
                continue;
            }
            store.put(t);
            added++;
        }
        return added;
    }

    private static Term parseLine(String line) {
        String[] c = line.split("\t", -1);
        if (c.length != 9) {
            return null;
        }
        for (int i = 0; i < c.length; i++) {
            c[i] = uncell(c[i]);
        }
        long created;
        int uses;
        try {
            created = Long.parseLong(c[7]);
            uses = Integer.parseInt(c[8]);
        } catch (NumberFormatException e) {
            return null;
        }
        // Kök yeniden hesaplanır: dosyada elle değiştirilmiş anahtar sözlüğe girmez.
        String root = verbRoot(c[1]);
        if (root.isEmpty() || !root.equals(c[0])) {
            return null;
        }
        String source = SOURCE_TEXT.equals(c[6]) || SOURCE_CLOUD.equals(c[6]) ? c[6] : SOURCE_VOICE;
        Term t = new Term(root, c[1], c[2], c[3], c[4], TAUGHT, source, created, Math.max(0, uses));
        Choice choice = choiceOf(t);
        if (choice == null || !choice.field.equals(t.field)) {
            return null;
        }
        return t;
    }

    private static String cell(String s) {
        String v = s == null ? "" : s;
        return v.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private static String uncell(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                b.append(n == 't' ? '\t' : n == 'n' ? '\n' : n);
            } else {
                b.append(ch);
            }
        }
        return b.toString();
    }

    /** "Vazgeç" kaydının anahtarı: sadeleşmiş cümle. */
    static String sentenceKey(String raw) {
        return TurkishText.normalize(raw);
    }

    // ============================================================ KOMUTLAR

    /** "öğrendiklerini söyle", "neler öğrendin", "öğrendiğin sözler". */
    static boolean asksList(String raw) {
        String s = TurkishText.normalize(raw);
        return s.matches(".*\\bogrendik(lerin|lerini)\\b.*") || s.matches(".*\\b(neler|ne) ogrendin\\b.*")
                || s.matches(".*\\bogrendigin soz\\p{L}*.*");
    }

    /** "bakırladığı unut", "bakırladık sözünü unut": unutulacak kelime; komut değilse null. */
    static String forgetTarget(String raw) {
        String[] w = TurkishText.words(raw);
        if (w.length < 2) {
            return null;
        }
        String last = w[w.length - 1];
        if (!last.matches("unut|unutsun|unutun|unutabilirsin")) {
            return null;
        }
        int i = w.length - 2;
        while (i >= 0 && w[i].matches("soz\\p{L}*|kelime\\p{L}*|laf\\p{L}*|bunu|onu|artik")) {
            i--;
        }
        if (i < 0) {
            return null;
        }
        return w[i];
    }

    /** Sözlüğün okunuşu ve ekrandaki listesi: {söz, ekran metni}. */
    static String[] listText(List<Term> terms) {
        if (terms.isEmpty()) {
            String empty = "Henüz öğrendiğim bir söz yok. Anlamadığım bir işi söylediğinde ne olduğunu sorarım.";
            return new String[]{empty, empty};
        }
        StringBuilder b = new StringBuilder("Öğrendiğim sözler\n");
        for (Term t : terms) {
            b.append("\n• ").append(capital(t.word)).append(" → ").append(t.meaning());
            b.append(" · ").append(sourceLabel(t.source)).append(" · ").append(t.uses).append(" kez");
        }
        b.append("\n\nBir sözü unutturmak için “bakırladığı unut” gibi söyle. Bu sözler yalnız bu telefonda.");
        String speech = terms.size() + " söz öğrendim. Listesini ekrana yazdım.";
        return new String[]{speech, b.toString()};
    }

    /** "Bakırladık'ı ilaçlama diye öğrendim." */
    static String told(Term t) {
        return capital(t.word) + "'" + accusative(t.word) + " " + t.meaning() + " diye öğrendim.";
    }

    /** Belirtme durumu eki, ünlü uyumuyla: bakırladık'ı, temizledik'i, budadık'ı, kuruttuk'u, sürdük'ü. */
    static String accusative(String word) {
        String w = word == null ? "" : word.toLowerCase(new java.util.Locale("tr", "TR"));
        char vowel = 'ı';
        for (int i = w.length() - 1; i >= 0; i--) {
            char c = w.charAt(i);
            if ("aı".indexOf(c) >= 0) {
                vowel = 'ı';
                break;
            }
            if ("ei".indexOf(c) >= 0) {
                vowel = 'i';
                break;
            }
            if ("ou".indexOf(c) >= 0) {
                vowel = 'u';
                break;
            }
            if ("öü".indexOf(c) >= 0) {
                vowel = 'ü';
                break;
            }
        }
        boolean endsWithVowel = !w.isEmpty() && "aıeioöuü".indexOf(w.charAt(w.length() - 1)) >= 0;
        return endsWithVowel ? "y" + vowel : String.valueOf(vowel);
    }

    static String sourceLabel(String source) {
        if (SOURCE_CLOUD.equals(source)) {
            return "internet yardımcısından, onaylı";
        }
        if (SOURCE_TEXT.equals(source)) {
            return "yazıyla";
        }
        return "sesle";
    }

    static String capital(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.substring(0, 1).toUpperCase(new java.util.Locale("tr", "TR")) + s.substring(1);
    }

    /** Cümlenin kelimeleri olduğu gibi (gösterim için; noktalama atılır, sayı/ek kalır). */
    private static String[] originalWords(String raw) {
        String s = raw == null ? "" : raw.trim();
        s = s.replaceAll("[\\p{Punct}&&[^'’]]+", " ").trim();
        if (s.isEmpty()) {
            return new String[0];
        }
        String[] parts = s.split("\\s+");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String[] sub = TurkishText.words(p);
            if (sub.length == 0) {
                continue;
            }
            String head = p.split("['’]", -1)[0];
            out.add(head.isEmpty() ? p : head);
            for (int k = 1; k < sub.length; k++) {
                out.add(sub[k]);
            }
        }
        return out.toArray(new String[0]);
    }
}
