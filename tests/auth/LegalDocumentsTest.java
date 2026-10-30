package com.zeytindefteri.app;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Host-only checks for the legal document configuration and texts. No Android/Firebase runtime. */
public final class LegalDocumentsTest {
    private static int checks;
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
    private static String raw(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("app/src/main/res/raw/" + name)), StandardCharsets.UTF_8);
    }
    public static void main(String[] args) throws Exception {
        // Registration gate is bound to two real preconditions.
        check(!LegalDocuments.readyForRegistration(false, ""), "draft+no name closed");
        check(!LegalDocuments.readyForRegistration(true, ""), "approved but no name closed");
        check(!LegalDocuments.readyForRegistration(true, "   "), "blank name closed");
        check(!LegalDocuments.readyForRegistration(false, "Ad Soyad"), "name but not approved closed");
        check(LegalDocuments.readyForRegistration(true, "Ad Soyad"), "approved+name open");
        check(LegalDocuments.registrationBlockReason(true, "Ad Soyad").isEmpty(), "no reason when open");
        check(!LegalDocuments.registrationBlockReason(false, "").isEmpty(), "reason when closed");
        // Current repository state must stay closed until the publisher verifies identity and review.
        // Publisher decision 13.10.2026: controller named, documents in force as v1.0.
        // 14.10.2026 v1.1: market contribution texts match the app. v1.2: in-app account deletion.
        check(LegalDocuments.readyForRegistration(), "registration open");
        check(LegalDocuments.registrationBlockReason().isEmpty(), "no block reason shown");
        check(!LegalDocuments.isDraft(), "no draft banner");
        check("Muammer Çerdin".equals(LegalDocuments.CONTROLLER_NAME), "controller name");
        check(AuthRegistrationPolicy.documentsReady(), "policy follows legal config");
        check("muammercerdin@gmail.com".equals(LegalDocuments.CONTACT_EMAIL), "contact email");

        String[] files = {"legal_kvkk.txt", "legal_privacy.txt", "legal_terms.txt"};
        for (String file : files) {
            String filled = LegalDocuments.fill(raw(file));
            check(!filled.contains("{{"), file + " placeholders filled");
            check(filled.contains(LegalDocuments.CONTACT_EMAIL), file + " contact email");
            check(filled.contains("Muammer Çerdin") && !filled.contains(LegalDocuments.CONTROLLER_PENDING), file + " controller name filled");
            check(LegalDocuments.fill(raw(file), "").contains(LegalDocuments.CONTROLLER_PENDING), file + " empty name is marked pending");
            check(filled.contains("Sürüm " + LegalDocuments.VERSION), file + " shows current version");
            check("1.8".equals(LegalDocuments.VERSION), "version 1.8");
            String named = LegalDocuments.fill(raw(file), "Ad Soyad");
            check(named.contains("Ad Soyad") && !named.contains(LegalDocuments.CONTROLLER_PENDING), file + " name substitution");
            // No fabricated contact data or absolute guarantees.
            check(!filled.matches("(?s).*\\+90.*|.*0\\s?5\\d{2}\\s?\\d{3}.*"), file + " no phone number");
            check(!filled.toLowerCase(java.util.Locale.forLanguageTag("tr")).contains("hiçbir sorumluluk"), file + " no blanket disclaimer");
        }
        String kvkk = raw("legal_kvkk.txt");
        for (String section : new String[]{"Veri sorumlusu", "veri kategorileri", "İşleme amaçları", "Hukuki sebepler",
                "Toplama yöntemi", "Veri alıcıları", "Yurt dışına aktarım", "m. 11", "Başvuru yöntemi"}) {
            check(kvkk.contains(section), "KVKK section: " + section);
        }
        check(kvkk.contains("açık rıza beyanı değildir"), "KVKK notice is not consent");
        String privacy = raw("legal_privacy.txt");
        for (String section : new String[]{"Firebase Authentication", "Supabase", "Bahçe ve sezon", "Meydan",
                "Bildirimler", "Analitik", "Saklama ve silme", "Üçüncü taraf"}) {
            check(privacy.contains(section), "Privacy section: " + section);
        }
        check(privacy.contains("Ayarlar > \"Hesabımı Sil\"") && privacy.contains("Silinmiş kullanıcı"), "in-app deletion disclosed");
        String terms = raw("legal_terms.txt");
        for (String section : new String[]{"amacı", "Hesap kullanımı", "Kullanıcı sorumlulukları", "Yasaklı kullanım",
                "topluluk kuralları", "askıya", "Hizmet değişiklikleri", "Sorumluluk"}) {
            check(terms.contains(section), "Terms section: " + section);
        }
        check(terms.contains("ortadan kaldırmaz"), "liability clause keeps mandatory rights");
        check(LegalDocuments.deletionBody().contains("silinmesini talep"), "deletion mail body");
        System.out.println("PASS: " + checks + " legal document checks (host only).");
    }
}
