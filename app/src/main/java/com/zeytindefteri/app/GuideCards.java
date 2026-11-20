package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Duruma göre soru kartı (yönlendiren asistan; saf, Android'siz, testli). Karşılamada aynı anda en çok TEK kart:
 * öncelik ödeme günü, hasat, alışkanlık. Her kartın gerekçesi yazar; "Bu yanlış" ve "Sonra" düğmeleri vardır.
 *
 * - "Bu yanlış" üst üste GuideSettings.CARD_REJECT_LIMIT (3) kez denen kart türü kapanır; Öğrendiklerim'den açılır.
 * - "Sonra" kartı o gün için gizler.
 * - Hasat kartı yalnız Ekim–Ocak arasında ve o gün hasat kaydı yoksa çıkar.
 * - Gece (22:00–05:00) kart yok. Ürkütücü kişisel gözlem yazılmaz (creepy()).
 */
final class GuideCards {
    static final String PAYMENT = "odeme_gunu";
    static final String HARVEST = "hasat";
    static final String HABIT = "aliskanlik";
    /** Öncelik sırası. */
    static final String[] PRIORITY = {PAYMENT, HARVEST, HABIT};

    /** Kart türlerinin çiftçiye gösterilen adı (Öğrendiklerim'de "kapattığın kartlar"). */
    static String label(String type) {
        switch (type) {
            case PAYMENT: return "Ödeme günü kartı";
            case HARVEST: return "Hasat kartı";
            default: return "Alışkanlık önerisi kartı";
        }
    }

    /** Bir kart: tür, başlık, soru, gerekçe ve ana düğmenin yazısı ile ne yapacağı (asistana verilen cümle ya da iş). */
    static final class Card {
        final String type;
        final String title;
        final String text;
        final String reason;
        final String action;
        final String payload;

        Card(String type, String title, String text, String reason, String action, String payload) {
            this.type = type;
            this.title = title;
            this.text = text;
            this.reason = reason;
            this.action = action;
            this.payload = payload;
        }
    }

    /** Bir kart türünün durumu: üst üste red, kapalı mı, hangi gün "Sonra" denildi (yyyyMMdd; yoksa 0). */
    static final class State {
        int rejects;
        boolean closed;
        int snoozedDay;

        State(int rejects, boolean closed, int snoozedDay) {
            this.rejects = rejects;
            this.closed = closed;
            this.snoozedDay = snoozedDay;
        }

        static State empty() {
            return new State(0, false, 0);
        }
    }

    private GuideCards() {
    }

    /**
     * Gösterilecek tek kart; yoksa null. candidates: Android'in kayıtlardan hazırladığı adaylar (her türden en çok
     * biri). states: tür → durum. today: yyyyMMdd. harvestToday: bugün hasat kaydı var mı.
     */
    static Card pick(List<Card> candidates, Map<String, State> states, int hour, int month, int today, boolean harvestToday) {
        if (GuideSettings.night(hour) || candidates == null) {
            return null;
        }
        for (String type : PRIORITY) {
            for (Card c : candidates) {
                if (!c.type.equals(type) || !open(states, type, today) || creepy(c)) {
                    continue;
                }
                if (HARVEST.equals(type) && (harvestToday || !harvestSeason(month))) {
                    continue;
                }
                return c;
            }
        }
        return null;
    }

    static boolean harvestSeason(int month) {
        return GuideSettings.inMonths(month, GuideSettings.HARVEST_CARD_FROM, GuideSettings.HARVEST_CARD_TO);
    }

    private static boolean open(Map<String, State> states, String type, int today) {
        State s = states == null ? null : states.get(type);
        if (s == null) {
            return true;
        }
        return !s.closed && s.snoozedDay != today;
    }

    /** "Bu yanlış": üst üste sayılır; sınıra ulaşınca kart türü kapanır. */
    static State rejected(State s) {
        State n = s == null ? State.empty() : new State(s.rejects, s.closed, s.snoozedDay);
        n.rejects++;
        if (n.rejects >= GuideSettings.CARD_REJECT_LIMIT) {
            n.closed = true;
        }
        return n;
    }

    /** Kart kabul edildi (ana düğme): üst üste red sayısı sıfırlanır. */
    static State accepted(State s) {
        State n = s == null ? State.empty() : new State(s.rejects, s.closed, s.snoozedDay);
        n.rejects = 0;
        return n;
    }

    /** "Sonra": o gün gizlenir; red sayılmaz. */
    static State later(State s, int today) {
        State n = s == null ? State.empty() : new State(s.rejects, s.closed, s.snoozedDay);
        n.snoozedDay = today;
        return n;
    }

    /** Öğrendiklerim'den yeniden açma: kapalı değil, red sayısı sıfır. */
    static State reopened() {
        return State.empty();
    }

    /** Kapalı kart türleri (Öğrendiklerim'de listelenir). */
    static List<String> closed(Map<String, State> states) {
        List<String> out = new ArrayList<>();
        for (String type : PRIORITY) {
            State s = states == null ? null : states.get(type);
            if (s != null && s.closed) {
                out.add(type);
            }
        }
        return out;
    }

    /** Ürkütücü kişisel gözlem: saat / uyku / ev / konum / aile / sağlık sözü geçen kart gösterilmez. */
    static boolean creepy(Card c) {
        String s = TurkishText.normalize(c.title + " " + c.text + " " + c.reason);
        return s.matches(".*\\b(uyu\\p{L}*|evde\\p{L}*|evden|evine|konum\\p{L}*|nerede\\p{L}*|esin\\p{L}*|karin\\p{L}*|kocan\\p{L}*"
                + "|cocug\\p{L}*|hasta\\p{L}*|saat \\d+|gece\\p{L}*|telefon\\p{L}*|uygulamay\\p{L}*|actin\\p{L}*)\\b.*");
    }

    // ------------------------------------------------------------------ adaylar

    /** Ödeme günü gelen alacak: "Ali Usta'nın ödeme günü bugün. Parayı aldın mı?" */
    static Card payment(String buyer, String saleDay) {
        return new Card(PAYMENT, "Bugün ödeme günü", genitive(buyer) + " ödeme günü bugün. Parayı aldın mı?",
                "Neden: " + saleDay + " satışında ödeme günü bugüne yazılmıştı.", "Aldım", buyer);
    }

    /** Hasat mevsimi: "Bugün kaç kilo topladın?" */
    static Card harvest() {
        return new Card(HARVEST, "Hasat zamanı", "Bugün kaç kilo topladın?",
                "Neden: hasat mevsimi ve bugün hasat kaydı yok.", "Söyleyeyim", "HASAT");
    }

    /** Alışkanlıktan öneri: "Girelli'yi suladın mı?" — kanıtı ("son 6 sulamanın 4'ü Girelli'de") gerekçe olur. */
    static Card habit(String question, String evidence, String jobId) {
        return new Card(HABIT, "Hatırlatayım dedim", question, "Neden: " + evidence + ".", "Evet, girelim", jobId);
    }

    /** "Ali Usta" → "Ali Usta'nın", "Mehmet" → "Mehmet'in". */
    static String genitive(String name) {
        String low = name.toLowerCase(new java.util.Locale("tr", "TR"));
        String v = "ı";
        for (int i = low.length() - 1; i >= 0; i--) {
            char c = low.charAt(i);
            if ("aı".indexOf(c) >= 0) {
                v = "ı";
                break;
            }
            if ("ei".indexOf(c) >= 0) {
                v = "i";
                break;
            }
            if ("ou".indexOf(c) >= 0) {
                v = "u";
                break;
            }
            if ("öü".indexOf(c) >= 0) {
                v = "ü";
                break;
            }
        }
        boolean endsVowel = "aeıioöuü".indexOf(low.charAt(low.length() - 1)) >= 0;
        return name + "'" + (endsVowel ? "n" : "") + v + "n";
    }
}
