package com.zeytindefteri.app;

/** Registration UI rules. Existing account sign-in remains Firebase's decision. */
final class AuthRegistrationPolicy {
    private AuthRegistrationPolicy() {}

    static boolean validPassword(String password) {
        if (password == null || password.codePointCount(0, password.length()) < 8) return false;
        boolean digit = false, upper = false;
        for (int i = 0; i < password.length(); i++) {
            digit |= Character.isDigit(password.charAt(i));
            upper |= Character.isUpperCase(password.charAt(i));
        }
        return digit && upper;
    }

    static int strength(String password) {
        if (password == null || password.isEmpty()) return 0;
        if (validPassword(password)) return password.codePointCount(0, password.length()) >= 12 ? 4 : 3;
        return password.codePointCount(0, password.length()) >= 8 ? 2 : 1;
    }

    static boolean documentsReady() {
        // Kayıt; veri sorumlusunun adı girilmiş ve belgeler yayıncı tarafından yürürlüğe
        // alınmışsa açıktır (LegalDocuments). Bu, sunucu tarafı bir kısıt değildir.
        return LegalDocuments.readyForRegistration();
    }

    static boolean canRegister(boolean accepted) {
        return documentsReady() && accepted;
    }
}
