package com.zeytindefteri.app;

/**
 * Yönlendiren asistanın ayarları tek yerde (sonra elle ayarlanabilsin diye): karşılama düğmelerinin puanı ve mevsim
 * aralıkları, kısa yol ve soru kartı sınırları, gece saatleri, yazı boyutları.
 */
final class GuideSettings {
    private GuideSettings() {
    }

    /** Karşılamadaki sabit düğme yeri sayısı ("Başka bir iş" ayrıca). */
    static final int HOME_SLOTS = 4;
    /** Kayıt sıklığına bakılan gün sayısı. */
    static final int HISTORY_DAYS = 60;
    /** Bir kaydın puanı (son 60 gün). */
    static final int RECORD_SCORE = 10;
    /** Mevsimindeki işe eklenen puan. */
    static final int SEASON_SCORE = 25;
    /** Kayıt yoksa ve puanlar eşitse sıra (çiftçinin istediği): İlaçlama, Hasat, Satış, Masraf; sonra diğerleri. */
    static final String[] DEFAULT_ORDER = {"ILACLAMA", "HASAT", "SATIS", "MASRAF", "SULAMA", "BUDAMA", "GUBRE", "TAHSILAT"};

    /** Mevsimler: iş, ilk ay, son ay (1–12; ilk > son ise yıl başını aşar: Ekim–Ocak). */
    static final Object[][] SEASONS = {
            {"HASAT", 10, 1},
            {"SATIS", 10, 2},
            {"SULAMA", 6, 9},
            {"ILACLAMA", 3, 6},
            {"BUDAMA", 2, 4},
            {"GUBRE", 2, 4},
    };

    /** Hasat kartı (soru kartı) hangi aylarda: Ekim–Ocak. */
    static final int HARVEST_CARD_FROM = 10;
    static final int HARVEST_CARD_TO = 1;

    /** Kısa yol cümlesi aynı iş türü için en çok kaç kez gösterilir. */
    static final int SHORTCUT_MAX_SHOWS = 3;
    /** "Bu yanlış" üst üste kaç kez denince kart türü kapanır. */
    static final int CARD_REJECT_LIMIT = 3;

    /** Gece: bu saatler arasında kart ve sesli soru yok (22:00–05:00). */
    static final int NIGHT_FROM = 22;
    static final int NIGHT_TO = 5;

    /**
     * Yeni ekranlarda yazı (sp) ve düğme (dp) boyutları. Cihaz denemesinde (21 Kasım) 28/19/60 panele sığmadı;
     * karşılama tek ekranda, kaydırmadan görünsün diye sıkılaştırıldı. Dokunma alanı 48 dp'nin altına inmez.
     */
    static final int QUESTION_SP = 22;
    static final int BUTTON_SP = 17;
    static final int BUTTON_DP = 52;
    static final int REASON_SP = 13;

    /** İkincil küçük düğmeler (Bu yanlış, Sonra, Başka bir iş, Neler yapabilirsin?). */
    static final int SMALL_SP = 15;
    static final int SMALL_DP = 48;

    /** Gece mi (22:00–05:00). */
    static boolean night(int hour) {
        return hour >= NIGHT_FROM || hour < NIGHT_TO;
    }

    /** Ay, aralıkta mı (Ekim–Ocak gibi yıl başını aşan aralık dahil). */
    static boolean inMonths(int month, int from, int to) {
        if (from <= to) {
            return month >= from && month <= to;
        }
        return month >= from || month <= to;
    }
}
