package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Sohbet küfür filtresi (telefonda, gönderilmeden önce). Android'e bağlı değil, test edilir.
 *
 * Kandırma yollarını yakalar: büyük harf, Türkçe harfsiz yazım (ş→s, ü→u…), rakam/simge (0→o, 1→i, @→a, $→s),
 * harf tekrarı ("siiiik"), harf arası nokta/simge ("s.i.k"), harfleri boşlukla ayırma ("s i k").
 * Yanlış alarmı önlemek için "ı" ayrı tutulur (sıkıntı, sıkışık masumdur), kısa kökler yalnız tam kelime
 * olarak aranır (göt ≠ götürmek), uzun kökler kelime başında aranır (orospu…, siktir…).
 * Kesin değildir; atlatılan içerik şikâyet ve moderasyonla yakalanır.
 */
public final class ProfanityFilter {
    private ProfanityFilter() {}

    private static final Locale TR = new Locale("tr", "TR");

    /** Tam kelime olarak eşleşenler (normalleştirilmiş, tekrar harfleri tekilleştirilmiş). */
    private static final Set<String> EXACT = new HashSet<>(Arrays.asList(
            "amk", "aq", "amq", "amcik", "amcig", "sik", "sikik", "sikim", "sikin", "siker", "sikis", "sikmek",
            "got", "gotu", "gote", "goten", "gotun", "pic", "picler", "oc", "yarak", "yarag", "yarak",
            "kaltak", "pust", "puste", "mal", "dangalak", "surtuk", "amina", "aminako", "amini"));

    /** Kelime başında aranan kökler. */
    private static final String[] PREFIX = {
            "orospu", "orsp", "pezeven", "kahpe", "yavsak", "gavat", "ibne", "serefsiz", "siktir", "sikerim",
            "sikeyim", "sikiyim", "siktig", "yarak", "dalyarak", "amcik", "aminakoy", "aminak",
            "anani", "ananiz", "anasini", "avradini", "gotveren", "gotunu", "gotune", "gotlek", "kaltag",
            "surtug", "amsalak", "picin", "piclik"};

    /** Masum kelimeler (kök eşleşse bile izin verilir; tekrar harfleri tekilleştirilmiş halde). */
    private static final Set<String> SAFE = new HashSet<>(Arrays.asList("amin", "amen", "gotik", "picaso", "sike", "anan"));

    /** Hakaret olarak "mal" yalnız hitapta yakalanır ("mal mısın", "sen malsın"); ticari "mal" serbest. */
    private static final Set<String> MAL_CONTEXT = new HashSet<>(Arrays.asList("misin", "misiniz", "sen", "malsin", "herif", "adam"));

    public static final class Result {
        public final boolean clean;
        public final String word;
        Result(boolean clean, String word) { this.clean = clean; this.word = word; }
    }

    public static boolean isClean(String text) { return check(text).clean; }

    public static Result check(String text) {
        if (text == null || text.trim().isEmpty()) return new Result(true, "");
        List<String> tokens = tokens(text);
        // Harfleri boşlukla ayrılmış yazım: "s i k t i r" → "siktir"
        List<String> joined = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        for (String t : tokens) {
            if (t.length() == 1) { run.append(t); continue; }
            if (run.length() >= 2) joined.add(run.toString());
            run.setLength(0);
        }
        if (run.length() >= 2) joined.add(run.toString());

        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            String hit = hit(t);
            if (hit == null) continue;
            if (hit.equals("mal")) {
                String prev = i > 0 ? tokens.get(i - 1) : "", next = i + 1 < tokens.size() ? tokens.get(i + 1) : "";
                if (!MAL_CONTEXT.contains(prev) && !MAL_CONTEXT.contains(next)) continue;
            }
            return new Result(false, hit);
        }
        for (String j : joined) {
            String hit = hit(j);
            if (hit != null && !hit.equals("mal")) return new Result(false, hit);
        }
        return new Result(true, "");
    }

    private static String hit(String token) {
        String h = hitExact(token, true);
        if (h != null || token.indexOf('ı') < 0) return h;
        // "ı" ile yazılmış küfürler (amına, ananı, anasını): ı → i ile tekrar bak, ama "sik" ailesi hariç;
        // çünkü sıkıntı, sıkışık, sıkı gibi masum kelimeler yalnız ı ile ayrışır.
        return hitExact(token.replace('ı', 'i'), false);
    }

    private static String hitExact(String token, boolean withSik) {
        if (token.isEmpty() || SAFE.contains(token)) return null;
        if (EXACT.contains(token) && (withSik || !token.startsWith("sik"))) return token;
        for (String p : PREFIX) {
            if (!withSik && p.startsWith("sik")) continue;
            if (token.startsWith(p)) return p;
        }
        return null;
    }

    /** Normalleştirilmiş kelimeler. */
    static List<String> tokens(String text) {
        String s = text.toLowerCase(TR);
        StringBuilder b = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case 'ş': b.append('s'); break;
                case 'ğ': b.append('g'); break;
                case 'ü': case 'û': b.append('u'); break;
                case 'ö': b.append('o'); break;
                case 'ç': b.append('c'); break;
                case 'â': b.append('a'); break;
                case 'î': b.append('i'); break;
                case '0': b.append('o'); break;
                case '1': case '!': case '|': b.append('i'); break;
                case '3': case '€': b.append('e'); break;
                case '4': case '@': b.append('a'); break;
                case '5': case '$': b.append('s'); break;
                case '7': b.append('t'); break;
                case '8': b.append('b'); break;
                default: b.append(ch);
            }
        }
        List<String> out = new ArrayList<>();
        for (String raw : b.toString().split("\\s+")) {
            StringBuilder w = new StringBuilder();
            char last = 0;
            for (char ch : raw.toCharArray()) {
                if (!Character.isLetter(ch)) continue;      // "s.i.k", "s-i-k", "s*k" içindeki simgeler atılır
                if (ch == last) continue;                    // "siiiik" → "sik"
                w.append(ch);
                last = ch;
            }
            if (w.length() > 0) out.add(w.toString());
        }
        return out;
    }

    /** Kullanıcıya gösterilecek açıklama (hangi kelime olduğu söylenmez). */
    public static String message() {
        return "Mesajında uygunsuz bir ifade var. Meydan herkese açık; lütfen düzenleyip tekrar gönder.";
    }
}
