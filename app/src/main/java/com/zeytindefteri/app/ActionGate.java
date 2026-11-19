package com.zeytindefteri.app;

/**
 * Onay kapısı (İP3; saf). Beynin kararı ile yürütme arasındaki tek kapı:
 *
 * 1. Basamak: kararın istediği basamak, katalogdaki basamakla kısılır; kayıt, satış, ödeme ve silme hiçbir koşulda
 *    "formu doldur"u geçemez (beynin güveni ne olursa olsun).
 * 2. Yazma: onaylı bir eylemi yazan kod, çiftçinin onay belgesi (Approval) olmadan çalışmaz. Belge kararlardan
 *    üretilemez; yalnız çiftçinin "evet"inden doğar.
 */
final class ActionGate {

    private ActionGate() {
    }

    /** Kararın yürütülebileceği en yüksek basamak. Bilinmeyen eylem yalnız önerilir. */
    static ActionCatalog.Level allowed(Decision d) {
        if (d == null) {
            return ActionCatalog.Level.ONER;
        }
        ActionCatalog.Action a = ActionCatalog.get(d.action);
        if (a == null) {
            return ActionCatalog.Level.ONER;
        }
        ActionCatalog.Level level = lower(d.requested, a.level);
        if (a.alwaysConfirm() && level == ActionCatalog.Level.IZINLE_KENDISI) {
            return ActionCatalog.Level.FORMU_DOLDUR;
        }
        return level;
    }

    /** Karar, çiftçiye sormadan yapılabilir mi (yalnız yazmayan eylemler: soru, ekran, sohbet, ayar). */
    static boolean mayActAlone(Decision d) {
        if (d == null || ActionCatalog.alwaysConfirm(d.action)) {
            return false;
        }
        return allowed(d) == ActionCatalog.Level.IZINLE_KENDISI;
    }

    /** Onaylı eylemi yazma izni: yalnız o eylemi kapsayan çiftçi onayıyla. */
    static boolean mayWrite(String action, Approval approval) {
        if (approval == null || action == null) {
            return false;
        }
        return approval.covers(action);
    }

    private static ActionCatalog.Level lower(ActionCatalog.Level a, ActionCatalog.Level b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }
}
