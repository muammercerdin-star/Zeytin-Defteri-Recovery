package com.zeytindefteri.app;

/**
 * Kullanım şekli (saf, Android'siz): üretici, tezgâhçı ya da ikisi de. Ana ekranın nereden açılacağına karar verir.
 * Boş (""): henüz sorulmadı (eski kullanıcı ya da yeni kurulum) — bir kez sorulur.
 */
public final class UserRole {
    public static final String PRODUCER = "producer", TRADER = "trader", BOTH = "both";
    public static final String VIEW_GARDEN = "garden", VIEW_TEZGAH = "tezgah";

    private UserRole() {}

    public static String parse(String raw) {
        String r = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.US);
        return PRODUCER.equals(r) || TRADER.equals(r) || BOTH.equals(r) ? r : "";
    }

    public static boolean chosen(String role) { return !parse(role).isEmpty(); }

    /** Tezgâh ekranları ana yere çıkar mı (tezgâhçı ya da ikisi de)? */
    public static boolean hasTezgah(String role) {
        String r = parse(role);
        return TRADER.equals(r) || BOTH.equals(r);
    }

    /** Bahçe/üretici ekranları kullanılır mı (üretici, ikisi de ya da henüz seçilmedi)? */
    public static boolean hasGarden(String role) { return !TRADER.equals(parse(role)); }

    /**
     * Uygulama açılınca Tezgâh ana ekranı mı açılsın? Tezgâhçı: evet. İkisi de: en son Tezgâh'ta kaldıysa.
     * explicitTarget: başka bir ekran özellikle istendiyse (ayarlar, sohbet, bildirim) hiç yönlendirilmez.
     */
    public static boolean opensTezgah(String role, String lastView, boolean explicitTarget) {
        if (explicitTarget) return false;
        String r = parse(role);
        if (TRADER.equals(r)) return true;
        return BOTH.equals(r) && VIEW_TEZGAH.equals(lastView);
    }

    /** Yeni kullanıcı ilk açılışta bahçe eklemek zorunda mı? Tezgâhçı değil. */
    public static boolean needsFirstGarden(String role) { return hasGarden(role); }

    public static String label(String role) {
        switch (parse(role)) {
            case PRODUCER: return "Üretici";
            case TRADER: return "Tezgâhçı";
            case BOTH: return "Üretici ve tezgâhçı";
            default: return "Seçilmedi";
        }
    }
}
