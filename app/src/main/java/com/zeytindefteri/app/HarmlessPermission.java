package com.zeytindefteri.app;

/**
 * İzin basamağı (İP7): zararsız bir iş (yalnız ödeme günü hatırlatması) çiftçi bir kez "evet, bunu hep yap"
 * dediyse sormadan yapılır ve haber verilir. Kayıt, satış, ödeme ve silme hiçbir izinle onaysız olmaz:
 * bu sınıf onlar için hep false döner.
 */
final class HarmlessPermission {
    private HarmlessPermission() {
    }

    /** Sormadan yapılabilecek tek iş: ödeme günü hatırlatması. */
    static final String PAYMENT_REMINDER = ActionCatalog.HATIRLATMA;

    /** Bu iş, verilmiş izinle sormadan yapılabilir mi? */
    static boolean mayDoAlone(String action, boolean granted) {
        if (!PAYMENT_REMINDER.equals(action)) {
            return false;
        }
        return granted;
    }

    /** Haber verme metni (sormadan yapılınca). */
    static String told(String dayText) {
        return "Ödeme günü için " + dayText + " hatırlatma kurdum. İstemezsen Hatırlatmalarım'dan silebilirsin.";
    }
}
