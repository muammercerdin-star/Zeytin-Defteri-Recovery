package com.zeytindefteri.app;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Çiftçinin açık düzeltmeleri (İP5): "Bu yanlış" denen değer ve kapanan öneri türleri. Yalnız telefonda
 * (HabitDb, zd_habits.db); Google yedeğine, telefon aktarımına ve Yedekle dosyasına girmez. Kayıtlardan hesaplanan
 * alışkanlıklar burada tutulmaz; burada yalnız çiftçinin "Bu yanlış" dediği şeyler vardır.
 */
final class HabitCorrections {
    private HabitCorrections() {
    }

    /** Öneri türleri (karne ve kapanma bu türlere göre). */
    static final String BUYER = "alici";
    static final String PRICE = "fiyat";
    static final String GRADE = "sinif";
    static final String GARDEN = "bahce";
    static final String PRODUCT = "urun";
    static final String AMOUNT = "miktar";
    static final String WORKERS = "isci";

    static final String[] CATEGORIES = {BUYER, PRICE, GRADE, GARDEN, PRODUCT, AMOUNT, WORKERS};

    /** "alici", "fiyat:Ali Usta:3" → tür ("alici", "fiyat"). */
    static String category(String what) {
        int colon = what == null ? -1 : what.indexOf(':');
        return colon < 0 ? (what == null ? "" : what) : what.substring(0, colon);
    }

    interface Store {
        /** "Bu yanlış" denen anın zamanı (ms); hiç denmediyse 0. */
        long rejectedAt(String what, String value);

        /** Üst üste kaç kez "Bu yanlış" dendi. */
        int streak(String category);

        boolean closed(String category);

        /** "Bu yanlış": değer bir sonraki kayda kadar önerilmez; tür 5 kez üst üste reddedilince kapanır. */
        void reject(String what, String value, long now);

        /** Öneri kaydedilen değerde kaldı: üst üste red sayısı sıfırlanır. */
        void accept(String what);
    }

    /** Bellekte (testler); telefondaki HabitDb aynı kuralı uygular. */
    static final class Memory implements Store {
        private final Map<String, Long> rejected = new HashMap<>();
        private final Map<String, Integer> streaks = new HashMap<>();
        private final Set<String> closed = new HashSet<>();

        @Override
        public long rejectedAt(String what, String value) {
            Long at = rejected.get(what + "\t" + value);
            return at == null ? 0 : at;
        }

        @Override
        public int streak(String category) {
            Integer n = streaks.get(category);
            return n == null ? 0 : n;
        }

        @Override
        public boolean closed(String category) {
            return closed.contains(category);
        }

        @Override
        public void reject(String what, String value, long now) {
            rejected.put(what + "\t" + value, now);
            String c = category(what);
            int n = streak(c) + 1;
            streaks.put(c, n);
            if (n >= HabitRules.CLOSE_AFTER) {
                closed.add(c);
            }
        }

        @Override
        public void accept(String what) {
            streaks.put(category(what), 0);
        }
    }
}
