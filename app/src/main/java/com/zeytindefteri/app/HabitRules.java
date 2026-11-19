package com.zeytindefteri.app;

/**
 * Sezilen alışkanlıkların kuralları tek yerde (İP5). İlk değerlerdir; karneye göre ayarlanır, her değişiklik
 * testlerden (tests/habits) geçer.
 */
final class HabitRules {
    private HabitRules() {
    }

    /** Bakılan son benzer kayıt sayısı. */
    static final int WINDOW = 6;

    /** Son 6 benzer kayıtta bu kadar ve üstü aynıysa güven yüksek: alan dolu gelir. */
    static final int HIGH = 4;

    /** Bu kadarsa güven orta: yalnız öneri çipi, alan doldurulmaz. Altı kullanılmaz. */
    static final int MID = 3;

    /** Bu kadar gün görülmeyen sezilen bilgi kullanılmaz (yeni sezonda yeniden öğrenilir). */
    static final int STALE_DAYS = 180;

    /** Bir öneri türü üst üste bu kadar "Bu yanlış" denirse kendiliğinden kapanır. */
    static final int CLOSE_AFTER = 5;

    static final long DAY_MS = 24L * 60L * 60L * 1000L;
}
