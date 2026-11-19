package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Asistanın karnesi (İP1; saf, Android'siz, testli). Yalnız sayı tutar, cümle metni girmez; yalnız bu telefonda
 * saklanır, hiçbir yere gönderilmez. "Karneni göster" denince asistan ekranında okunur; yeni ekran yoktur.
 *
 * Sayaçlar: anlaşılan / anlaşılmayan cümle, sesle doldurulan / Kaydet'ten önce düzeltilen alan, kayıt ekranının
 * açılışından Kaydet'e geçen süre (son SPAN_MAX kayıt; ortanca gösterilir).
 * Saklanan biçim: "anlasilan=3;anlasilmayan=1;doldurulan=8;duzeltilen=2;sure=12000,9000".
 */
final class AssistantScorecard {
    static final String UNDERSTOOD = "anlasilan";
    static final String MISSED = "anlasilmayan";
    static final String FILLED = "doldurulan";
    static final String CORRECTED = "duzeltilen";
    static final String SPANS = "sure";
    /** Ortanca için tutulan son kayıt süresi sayısı. */
    static final int SPAN_MAX = 51;
    /** Ekran açık unutulmuşsa (30 dakikadan uzun) süre sayılmaz. */
    static final long SPAN_LIMIT_MS = 30L * 60L * 1000L;

    private static final String[] COUNTERS = {UNDERSTOOD, MISSED, FILLED, CORRECTED};

    private AssistantScorecard() {
    }

    // ============================================================ SAKLAMA

    static Map<String, String> decode(String stored) {
        Map<String, String> out = new LinkedHashMap<>();
        if (stored == null || stored.isEmpty()) {
            return out;
        }
        for (String part : stored.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            out.put(part.substring(0, eq), part.substring(eq + 1));
        }
        return out;
    }

    static String encode(Map<String, String> values) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            if (b.length() > 0) {
                b.append(';');
            }
            b.append(e.getKey()).append('=').append(e.getValue());
        }
        return b.toString();
    }

    static long get(String stored, String key) {
        String v = decode(stored).get(key);
        if (v == null) {
            return 0;
        }
        try {
            return Math.max(0, Long.parseLong(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Sayacı artırır; bilinmeyen anahtar ya da eksi değer yok sayılır. */
    static String count(String stored, String key, long delta) {
        String keep = stored == null ? "" : stored;
        boolean known = false;
        for (String c : COUNTERS) {
            if (c.equals(key)) {
                known = true;
            }
        }
        if (!known || delta <= 0) {
            return keep;
        }
        Map<String, String> values = decode(keep);
        long next = get(keep, key) + delta;
        values.put(key, String.valueOf(next));
        return encode(values);
    }

    /** Kayıt süresi ekler (milisaniye); sıfır, eksi ya da SPAN_LIMIT_MS'den uzun süre sayılmaz. */
    static String span(String stored, long ms) {
        String keep = stored == null ? "" : stored;
        if (ms <= 0 || ms > SPAN_LIMIT_MS) {
            return keep;
        }
        List<Long> spans = spans(keep);
        spans.add(ms);
        while (spans.size() > SPAN_MAX) {
            spans.remove(0);
        }
        StringBuilder b = new StringBuilder();
        for (Long s : spans) {
            if (b.length() > 0) {
                b.append(',');
            }
            b.append(s);
        }
        Map<String, String> values = decode(keep);
        values.put(SPANS, b.toString());
        return encode(values);
    }

    static List<Long> spans(String stored) {
        List<Long> out = new ArrayList<>();
        String v = decode(stored).get(SPANS);
        if (v == null || v.isEmpty()) {
            return out;
        }
        for (String s : v.split(",")) {
            try {
                out.add(Long.parseLong(s));
            } catch (NumberFormatException ignored) {
                // bozuk parça atlanır
            }
        }
        return out;
    }

    /** Kayıt süresinin ortancası (ms); kayıt yoksa 0. */
    static long medianMs(String stored) {
        List<Long> s = spans(stored);
        if (s.isEmpty()) {
            return 0;
        }
        Collections.sort(s);
        int mid = s.size() / 2;
        if (s.size() % 2 == 1) {
            return s.get(mid);
        }
        return (s.get(mid - 1) + s.get(mid)) / 2;
    }

    // ============================================================ KOMUT VE GÖSTERİM

    /** "Karneni göster", "karnen nasıl", "karneyi aç". Silme sözü komut sayılmaz. */
    static boolean asksShow(String raw) {
        String s = ElekVoiceEntry.normalize(raw == null ? "" : raw);
        if (!s.matches(".*\\b(karne|karnen|karneni|karneyi|karnemi|karnesi|karnesini)\\b.*")) {
            return false;
        }
        return !s.matches(".*\\b(sil\\p{L}*|temizle\\p{L}*|sifirla\\p{L}*)\\b.*");
    }

    /**
     * "Konuşma kaydını sil", "karneni sil", "cümle kaydını temizle": telefondaki cümle kaydı, karne ve
     * anlamadıklarım listesi birlikte silinir (KVKK: kullanıcı cihazdaki kaydı kendisi silebilir).
     */
    static boolean asksClearAll(String raw) {
        String s = ElekVoiceEntry.normalize(raw == null ? "" : raw);
        boolean clear = s.matches(".*\\b(sil\\p{L}*|temizle\\p{L}*|sifirla\\p{L}*|unut)\\b.*");
        if (!clear) {
            return false;
        }
        boolean card = s.matches(".*\\b(karne|karnen|karneni|karneyi|karnemi|karnesi|karnesini)\\b.*");
        boolean log = s.matches(".*\\b(konusma|konusmalar\\p{L}*|cumle|cumleler\\p{L}*)\\b.*")
                && s.matches(".*\\b(kayd\\p{L}*|kayit\\p{L}*|gecmis\\p{L}*)\\b.*");
        return card || log;
    }

    /** {okunacak kısa söz, ekranda gösterilecek metin}. */
    static String[] show(String stored) {
        long ok = get(stored, UNDERSTOOD);
        long miss = get(stored, MISSED);
        long filled = get(stored, FILLED);
        long fixed = get(stored, CORRECTED);
        int saves = spans(stored).size();
        if (ok + miss == 0 && filled == 0 && saves == 0) {
            String empty = "Karnem henüz boş. Benimle konuştukça ve kayıt girdikçe dolacak.";
            return new String[]{empty, empty};
        }
        StringBuilder b = new StringBuilder("Karnem\n");
        b.append("\n• Anlaşılan cümle: ").append(ok).append(" / ").append(ok + miss).append(percent(ok, ok + miss));
        b.append("\n• Anlaşılmayan cümle: ").append(miss);
        b.append("\n• Sesle doldurulan alan: ").append(filled);
        b.append("\n• Kaydetmeden önce düzeltilen alan: ").append(fixed).append(percent(fixed, filled));
        if (saves > 0) {
            b.append("\n• Kayıt süresi (ortanca): ").append(seconds(medianMs(stored))).append(" sn · son ").append(saves).append(" kayıt");
        } else {
            b.append("\n• Kayıt süresi: henüz kayıt yok");
        }
        b.append("\n\nYalnız sayılar tutulur, cümlelerin karneye girmez. Bu bilgiler yalnız bu telefonda.");
        String speech = "Karnemi ekrana yazdım.";
        if (ok + miss > 0) {
            speech = "Karnemi ekrana yazdım. " + (ok + miss) + " cümlenin " + ok + " tanesini anladım.";
        }
        return new String[]{speech, b.toString()};
    }

    private static String percent(long part, long whole) {
        if (whole <= 0) {
            return "";
        }
        long p = Math.round(100.0 * part / whole);
        return " (%" + p + ")";
    }

    private static String seconds(long ms) {
        long s = Math.round(ms / 1000.0);
        return String.valueOf(s);
    }

    // ============================================================ SESLE DOLDURULAN FORM

    /**
     * Sesle doldurulan alanların ilk değerleri. Kaydet anında kaydedilen değerlerle karşılaştırılır: farklı olan
     * alan "düzeltilen" sayılır (kuruş altı fark yok sayılır).
     */
    static final class FormFill {
        private final Map<String, Double> filled = new LinkedHashMap<>();

        /** Sesle yazılan alan; sıfır ya da eksi değer doldurulmuş sayılmaz ve önceki değeri kaldırır. */
        void put(String field, double value) {
            if (!(value > 0)) {
                filled.remove(field);
                return;
            }
            filled.put(field, value);
        }

        void clear() {
            filled.clear();
        }

        int size() {
            return filled.size();
        }

        /** Kaydedilen değerlere göre düzeltilen alanların adları (doldurulma sırasıyla); kaydedilmeyen alan düzeltilmiştir. */
        List<String> corrected(Map<String, Double> saved) {
            List<String> out = new ArrayList<>();
            for (Map.Entry<String, Double> e : filled.entrySet()) {
                Double now = saved == null ? null : saved.get(e.getKey());
                boolean same = now != null && Math.abs(now - e.getValue()) < 0.005;
                if (!same) {
                    out.add(e.getKey());
                }
            }
            return out;
        }
    }
}
