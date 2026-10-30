package com.zeytindefteri.app;

/**
 * Hukuki belgelerin tek yapılandırma noktası.
 *
 * Yeni kayıt yalnızca şu iki gerçek ön koşul sağlandığında açılır:
 *  1) CONTROLLER_NAME alanına veri sorumlusunun doğrulanmış ad soyadı yazılmış olmalı,
 *  2) LEGAL_REVIEW_APPROVED, metinler hukuki incelemeden geçtikten sonra true yapılmalı.
 * Bu sınıf Android bağımlılığı içermez; host testleriyle doğrulanır.
 */
final class LegalDocuments {
    private LegalDocuments() {}

    static final String KVKK = "kvkk";
    static final String PRIVACY = "privacy";
    static final String TERMS = "terms";

    static final String CONTACT_EMAIL = "muammercerdin@gmail.com";
    /** Veri sorumlusunun doğrulanmış ad soyadı. Doğrulanmadan doldurulmamalı. */
    static final String CONTROLLER_NAME = "Muammer Çerdin";
    static final String VERSION = "1.7";
    static final String DATE = "30 Ekim 2026";
    /**
     * Yayıncı onayı: 13.10.2026 tarihinde yayıncı (Muammer Çerdin) metinleri sürüm 1.0 olarak
     * yürürlüğe aldı. 14.10.2026 sürüm 1.1: piyasa katkısının varsayılan kapalı izinle çalıştığı
     * ve Zeytin Ligi'nin satıştaki paylaşım seçeneğine bağlı olduğu, uygulamadaki gerçek
     * davranışa uygun biçimde açıklandı (yayıncının 14.10.2026 talimatıyla). 14.10.2026 sürüm 1.2: Gizlilik
     * Politikası 10. bölüm, uygulamaya eklenen "Hesabımı Sil" (Play hesap silme şartı) davranışına göre
     * güncellendi; yayıncı 14.10.2026 tarihinde sürüm 1.2'yi onayladı. 28.10.2026 sürüm 1.6: tezgâh kayıtlarının
     * otomatik bulut yedeği (Gizlilik 2. bölüm, KVKK veri kategorileri) eklendi (yayıncının 28.10.2026 talimatıyla). Metinler bir hukukçu tarafından incelenmedi; bu bayrak yayıncı kararını gösterir.
     */
    static final boolean LEGAL_REVIEW_APPROVED = true;

    static final String CONTROLLER_PENDING =
            "Bireysel geliştirici (ad soyad yayın öncesinde eklenecektir)";

    static boolean controllerVerified() {
        return controllerVerified(CONTROLLER_NAME);
    }

    static boolean controllerVerified(String name) {
        return name != null && name.trim().length() > 1;
    }

    static boolean isDraft() {
        return !LEGAL_REVIEW_APPROVED;
    }

    static boolean readyForRegistration() {
        return readyForRegistration(LEGAL_REVIEW_APPROVED, CONTROLLER_NAME);
    }

    static boolean readyForRegistration(boolean reviewApproved, String controllerName) {
        return reviewApproved && controllerVerified(controllerName);
    }

    /** Kayıt kapalıysa kullanıcıya gösterilecek gerçek neden; açıksa boş metin. */
    static String registrationBlockReason() {
        return registrationBlockReason(LEGAL_REVIEW_APPROVED, CONTROLLER_NAME);
    }

    static String registrationBlockReason(boolean reviewApproved, String controllerName) {
        if (readyForRegistration(reviewApproved, controllerName)) return "";
        return "Hukuki metinler taslak; onaylanana kadar yeni kayıt kapalı. Mevcut hesabınla giriş yapabilirsin.";
    }

    static String fill(String text) {
        return fill(text, CONTROLLER_NAME);
    }

    static String fill(String text, String controllerName) {
        if (text == null) return "";
        String controller = controllerVerified(controllerName) ? controllerName.trim() : CONTROLLER_PENDING;
        return text.replace("{{VERI_SORUMLUSU}}", controller)
                .replace("{{EPOSTA}}", CONTACT_EMAIL)
                .replace("{{SURUM}}", VERSION)
                .replace("{{TARIH}}", DATE);
    }

    static boolean isKnown(String document) {
        return KVKK.equals(document) || PRIVACY.equals(document) || TERMS.equals(document);
    }

    static String title(String document) {
        if (KVKK.equals(document)) return "KVKK Aydınlatma Metni";
        if (TERMS.equals(document)) return "Kullanım Koşulları";
        return "Gizlilik Politikası";
    }

    static String deletionSubject() {
        return "Zeytin Defteri - Hesap ve veri silme talebi";
    }

    static String deletionBody() {
        return "Merhaba,\n\n"
                + "Zeytin Defteri hesabımın ve hesabımla ilişkili verilerin silinmesini talep ediyorum.\n\n"
                + "Hesapta kayıtlı e-posta adresim:\n"
                + "Ad soyad:\n\n"
                + "Bu talebi hesabımda kayıtlı e-posta adresinden gönderiyorum.\n";
    }
}
