package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * "Ahmet'ten ne kadar alacağım var?", "Kim bana borçlu?", "Alacaklarım": alıcı listesindeki açık alacaklardan
 * kısa sesli cevap (saf, Android'siz, testli). Rakam yalnız kayıtlardan gelir; isim listede yoksa bunu söyler.
 */
public final class AssistantReceivables {
    public static final class Entry {
        public final String name; public final double open; public final int sales;
        public Entry(String name, double open, int sales) { this.name = name; this.open = open; this.sales = sales; }
    }

    private AssistantReceivables() {}

    /** Alacak sorusu mu? ("alacak", "borçlu", "borcu var", "kim ödemedi", "kimden para"). */
    public static boolean asks(String raw) {
        String s = ElekVoiceEntry.normalize(raw == null ? "" : raw);
        if (s.matches(".*\\b(alacag\\p{L}*|alacak\\p{L}*|borclu\\p{L}*|borcu|borclari|odemedi\\p{L}*|kimden para|tahsil\\p{L}*)\\b.*")) {
            // Tezgâh (tüccar) tarafının borcu "borcum" — üretici alacağı değil.
            return !s.matches(".*\\b(borcum|borclarim|odeyecegim|vereceg\\p{L}*)\\b.*");
        }
        return false;
    }

    /** Normalleştirilmiş (aksansız) dolgu sözleri: ad dışındaki her şey. */
    private static final String FILLER = "(ne|kadar|kac|lira|tl|alacag\\p{L}*|alacak\\p{L}*|var|mi|mu|borclu\\p{L}*|borcu|borclari|"
            + "bana|benim|kim|kimler|hangi|alici\\p{L}*|toplam|odedi\\p{L}*|odemedi\\p{L}*|tahsil\\p{L}*|kaldi|para|parasi|"
            + "simdi|daha|hala|bu|sezon|olan|listesi|goster|soyle)";

    /** Cümleden adı çıkarır: dolgu sözleri atılır, "'ten/'dan" ekleri silinir, asıl yazım (büyük/küçük, Türkçe harf) korunur. */
    static String nameIn(String raw) {
        StringBuilder b = new StringBuilder();
        for (String w : (raw == null ? "" : raw).split("[\\s,.!]+")) {
            if (w.isEmpty()) continue;
            String bare = w.replaceAll("['’]\\p{L}*$", "");
            String k = ElekVoiceEntry.normalize(bare);
            if (k.isEmpty() || k.matches(FILLER)) continue;
            if (b.length() > 0) b.append(' ');
            b.append(bare);
        }
        return ElekVoiceBuyer.spokenName(b.toString());
    }

    public static String answer(String raw, List<Entry> buyers) {
        List<Entry> open = new ArrayList<>();
        List<String> names = new ArrayList<>();
        double total = 0;
        if (buyers != null) for (Entry e : buyers) {
            names.add(e.name);
            if (e.open > 0.5) { open.add(e); total += e.open; }
        }
        String said = nameIn(raw);
        if (!said.isEmpty()) {
            ElekVoiceBuyer.Match m = ElekVoiceBuyer.match(said, names);
            if (m.kind == ElekVoiceBuyer.Kind.NEW) {
                // "Ahmetten": ek atılınca listede varsa o
                String[] w = said.split(" ");
                String last = w[w.length - 1];
                for (String suf : new String[]{"dan", "den", "tan", "ten"}) {
                    if (last.length() > suf.length() + 2 && last.toLowerCase(new java.util.Locale("tr", "TR")).endsWith(suf)) {
                        w[w.length - 1] = last.substring(0, last.length() - suf.length());
                        ElekVoiceBuyer.Match again = ElekVoiceBuyer.match(String.join(" ", w), names);
                        if (again.kind != ElekVoiceBuyer.Kind.NEW) m = again;
                        break;
                    }
                }
            }
            if (m.kind == ElekVoiceBuyer.Kind.EXISTING) {
                Entry e = find(buyers, m.name);
                if (e == null || e.open <= 0.5) return e != null ? e.name + " ile açık alacağın yok, hepsi ödenmiş." : m.name + " ile açık alacağın yok.";
                // Ek yok ("Kaya'dan / Veli'den" uyumu karışmasın): "Ahmet Kaya sana 4.800 lira borçlu."
                return e.name + " sana " + money(e.open) + " lira borçlu" + (e.sales > 1 ? ", " + e.sales + " satıştan." : ".");
            }
            if (m.kind == ElekVoiceBuyer.Kind.AMBIGUOUS) {
                StringBuilder b = new StringBuilder();
                for (String c : m.candidates) {
                    Entry e = find(buyers, c);
                    if (b.length() > 0) b.append(", ");
                    b.append(c).append(' ').append(e == null || e.open <= 0.5 ? "borcu yok" : money(e.open) + " lira");
                }
                return b.append('.').toString();
            }
            if (m.kind == ElekVoiceBuyer.Kind.NEW && said.split(" ").length <= 3) {
                return said + " adında bir alıcın yok. Alıcılar listesinde göremedim.";
            }
        }
        if (open.isEmpty()) return "Açık alacağın yok. Bütün satışların ödenmiş.";
        Collections.sort(open, (x, y) -> Double.compare(y.open, x.open));
        StringBuilder b = new StringBuilder("Toplam " + money(total) + " lira alacağın var. ");
        int n = Math.min(3, open.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) b.append(", ");
            b.append(open.get(i).name).append(' ').append(money(open.get(i).open));
        }
        b.append('.');
        if (open.size() > n) b.append(" Ve ").append(open.size() - n).append(" kişi daha.");
        return b.toString();
    }

    private static Entry find(List<Entry> list, String name) {
        if (list == null) return null;
        for (Entry e : list) if (e.name.equalsIgnoreCase(name)) return e;
        return null;
    }

    static String money(double v) { return ElekVoiceDialog.money(Math.round(v)); }
}
