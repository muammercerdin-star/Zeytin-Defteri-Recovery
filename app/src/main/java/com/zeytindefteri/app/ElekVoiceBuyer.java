package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sesle söylenen alıcı adını alıcı listesiyle eşleştirir (saf, Android'siz).
 * "Mehmet" → listede tek Mehmet varsa "Mehmet Yılmaz"; birden çok varsa hangisi diye sorulur;
 * yoksa yeni alıcı (satış kaydedilince BuyerDirectory.findOrCreate listeye ekler).
 */
public final class ElekVoiceBuyer {
    public enum Kind { EXISTING, NEW, AMBIGUOUS, NONE }

    public static final class Match {
        public final Kind kind; public final String name; public final List<String> candidates;
        Match(Kind kind, String name, List<String> candidates) { this.kind = kind; this.name = name; this.candidates = candidates; }
    }

    private static final Locale TR = new Locale("tr", "TR");
    private static final String[] FILLER = {"sattim", "sattik", "verdim", "verdik", "satiyorum", "satildi", "ona", "kime",
            "alici", "alicim", "adi", "adli", "isimli", "olan", "bugun", "zeytini", "zeytin", "bunu", "hepsini", "evet", "tamam"};

    private ElekVoiceBuyer() {}

    /** Söylenen addan dolgu kelimeleri ve "'a/'e" eklerini atar, baş harfleri büyütür: "mehmet yılmaz'a sattım" → "Mehmet Yılmaz". */
    public static String spokenName(String raw) {
        if (raw == null) return "";
        String t = raw.replaceAll("['’]\\p{L}*", " ").replaceAll("[^\\p{L}\\s]", " ").trim();
        StringBuilder b = new StringBuilder();
        for (String w : t.split("\\s+")) {
            if (w.isEmpty()) continue;
            String k = ElekVoiceEntry.normalize(w);
            boolean filler = false;
            for (String f : FILLER) if (f.equals(k)) { filler = true; break; }
            if (filler) continue;
            if (b.length() > 0) b.append(' ');
            String lower = w.toLowerCase(TR);
            b.append(lower.substring(0, 1).toUpperCase(TR)).append(lower.substring(1));
        }
        return b.toString();
    }

    public static Match match(String raw, List<String> buyers) {
        String name = spokenName(raw);
        if (name.isEmpty()) return new Match(Kind.NONE, "", new ArrayList<>());
        Match m = find(name, buyers);
        if (m.kind != Kind.NEW) return m;
        // "Mehmete" (kesme işaretsiz yazılmış ek): son kelimeden a/e/ya/ye atılınca listede varsa onu al.
        String[] w = name.split(" ");
        String last = w[w.length - 1];
        for (String suf : new String[]{"ya", "ye", "a", "e"}) {
            if (last.length() > suf.length() + 2 && last.toLowerCase(TR).endsWith(suf)) {
                w[w.length - 1] = last.substring(0, last.length() - suf.length());
                Match again = find(String.join(" ", w), buyers);
                if (again.kind == Kind.EXISTING || again.kind == Kind.AMBIGUOUS) return again;
                w[w.length - 1] = last;
            }
        }
        return m;
    }

    /** Aday listesinden ("Hangisi?" sorusuna cevap) seçer; tek eşleşme yoksa null. */
    public static String pick(String raw, List<String> candidates) {
        Match m = match(raw, candidates);
        return m.kind == Kind.EXISTING ? m.name : null;
    }

    private static Match find(String name, List<String> buyers) {
        String key = ElekVoiceEntry.normalize(name);
        List<String> hits = new ArrayList<>();
        if (buyers != null) {
            for (String b : buyers) {
                if (b == null || b.trim().isEmpty()) continue;
                if (ElekVoiceEntry.normalize(b).equals(key)) return new Match(Kind.EXISTING, b.trim(), single(b.trim()));
            }
            String[] said = key.split(" ");
            for (String b : buyers) {
                if (b == null || b.trim().isEmpty()) continue;
                String[] have = ElekVoiceEntry.normalize(b).split(" ");
                boolean all = true;
                for (String s : said) {
                    boolean found = false;
                    for (String h : have) if (h.equals(s) || (s.length() >= 3 && h.startsWith(s))) { found = true; break; }
                    if (!found) { all = false; break; }
                }
                if (all && !hits.contains(b.trim())) hits.add(b.trim());
            }
        }
        if (hits.size() == 1) return new Match(Kind.EXISTING, hits.get(0), hits);
        if (hits.size() > 1) return new Match(Kind.AMBIGUOUS, name, hits);
        return new Match(Kind.NEW, name, new ArrayList<>());
    }

    private static List<String> single(String s) { List<String> l = new ArrayList<>(); l.add(s); return l; }
}
