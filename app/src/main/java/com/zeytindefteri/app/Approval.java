package com.zeytindefteri.app;

/**
 * Çiftçinin onay belgesi (İP3). Kayıt, satış, ödeme ve silme yazan kod yalnız bununla çalışır.
 *
 * Belge bir beynin kararından (Decision) ÜRETİLEMEZ: kurucusu gizlidir ve tek yolu, çiftçinin sesli sorularda
 * (işlem soruları, Elek "Sesle doldur") özetten sonra "evet" dediği bir konuşmadır. Ne kadar emin olursa olsun hiçbir beyin kendi başına onay belgesi yazamaz.
 */
final class Approval {
    /** Onaylanan eylemin katalog kimliği. */
    final String action;
    /** Onayın nereden geldiği (rapor ve test için). */
    final String source;

    private Approval(String action, String source) {
        this.action = action;
        this.source = source;
    }

    /** İşlem soruları: çiftçi özetten sonra "evet/kaydet" dedi. Demediyse null. */
    static Approval operationConfirmed(OperationVoiceDialog dialog) {
        if (dialog == null || !dialog.confirmed()) {
            return null;
        }
        return new Approval(ActionCatalog.ISLEM, "işlem sorularında sesli onay");
    }

    /** Elek "Sesle doldur": çiftçi özetten sonra "evet" dedi. Demediyse null. */
    static Approval elekConfirmed(ElekVoiceDialog dialog) {
        if (dialog == null || !dialog.confirmed()) {
            return null;
        }
        return new Approval(ActionCatalog.ELEK, "elek sesli doldurmada sesli onay");
    }

    /** Bu belge verilen eylemi kapsıyor mu. */
    boolean covers(String id) {
        return action.equals(id);
    }
}
