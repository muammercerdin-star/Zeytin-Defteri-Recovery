package com.zeytindefteri.app;

/**
 * Meydan moderasyon kararları (firestore.rules ile uyumlu). Android'e bağlı değil, test edilir.
 *
 * Katmanlar: (1) küfür telefonda engellenir (ProfanityFilter); (2) yeni üyenin ilk {@link #NEW_MEMBER_POSTS}
 * gönderisi PENDING gider, moderatör onaylayınca herkese görünür; (3) {@link #AUTO_HIDE_REPORTS} farklı şikâyet
 * alan içerik moderatör bakana kadar gizlenir; (4) moderatör gizler (HIDDEN) ya da onaylar (ACTIVE, sayaç 0);
 * (5) susturulan kullanıcı süre bitene kadar yazamaz (kural sunucuda da uygular).
 */
public final class MeydanModeration {
    public static final int NEW_MEMBER_POSTS = 3;
    public static final int AUTO_HIDE_REPORTS = 3;
    public static final String ACTIVE = "ACTIVE", PENDING = "PENDING", HIDDEN = "HIDDEN";

    private MeydanModeration() {}

    /** Bulut belgesindeki durum; alan yoksa (eski gönderiler) ACTIVE. */
    public static String status(Object raw) {
        if (raw instanceof String) {
            String s = (String) raw;
            if (PENDING.equals(s) || HIDDEN.equals(s)) return s;
        }
        return ACTIVE;
    }

    public static int reports(Object raw) {
        return raw instanceof Number ? Math.max(0, ((Number) raw).intValue()) : 0;
    }

    /** Akışta görünür mü? Onay bekleyen yalnız sahibine; gizlenen ve çok şikâyet alan kimseye görünmez. */
    public static boolean visible(String status, int reports, boolean isOwner) {
        if (HIDDEN.equals(status)) return false;
        if (reports >= AUTO_HIDE_REPORTS) return false;
        if (PENDING.equals(status)) return isOwner;
        return true;
    }

    /** Yanıtlar onaya düşmez; yalnız gizlenme ve şikâyet eşiği uygulanır. */
    public static boolean commentVisible(String status, int reports) {
        return !HIDDEN.equals(status) && reports < AUTO_HIDE_REPORTS;
    }

    /** Yeni gönderinin durumu: moderatör ya da güvenilir üye (yeterli geçmiş gönderi) ACTIVE, yeni üye PENDING. */
    public static String newPostStatus(int previousPosts, boolean moderator) {
        return moderator || previousPosts >= NEW_MEMBER_POSTS ? ACTIVE : PENDING;
    }

    /** Sahibinin gördüğü etiket; boşsa etiket yok. */
    public static String ownerLabel(String status) {
        return PENDING.equals(status) ? "Onay bekliyor" : "";
    }

    /** Susturma metni (until: ms). Susturma yoksa ya da bittiyse boş. */
    public static String muteMessage(long until, long now) {
        if (until <= now) return "";
        long hours = (until - now + 3_599_999L) / 3_600_000L;
        if (until - now > 3650L * 24 * 3600 * 1000) return "Meydan'da yazman moderatör tarafından kapatıldı.";
        if (hours < 24) return "Moderatör seni " + hours + " saatliğine susturdu. Bu süre bitince yazabilirsin.";
        return "Moderatör seni " + ((hours + 23) / 24) + " günlüğüne susturdu. Bu süre bitince yazabilirsin.";
    }

    /** Susturma süreleri (moderatör seçenekleri). */
    public static final String[] MUTE_LABELS = {"1 gün", "1 hafta", "1 ay", "Süresiz"};
    public static final long[] MUTE_MS = {24L * 3600 * 1000, 7L * 24 * 3600 * 1000, 30L * 24 * 3600 * 1000,
            100L * 365 * 24 * 3600 * 1000};
}
