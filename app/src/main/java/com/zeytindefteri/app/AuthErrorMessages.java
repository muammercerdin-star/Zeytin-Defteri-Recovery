package com.zeytindefteri.app;

/** User-facing messages only: never expose raw provider exceptions or credentials. */
final class AuthErrorMessages {
    private AuthErrorMessages() {}

    static String message(String code, boolean network, boolean throttled, String operation) {
        if (network) return "İnternet bağlantını kontrol edip tekrar dene.";
        if (throttled || "ERROR_TOO_MANY_REQUESTS".equals(code)) {
            return "Çok fazla deneme yapıldı. Biraz bekleyip tekrar dene.";
        }
        if ("ERROR_OPERATION_NOT_ALLOWED".equals(code)
                || "ERROR_PASSWORD_LOGIN_DISABLED".equals(code)) {
            return "E-posta/şifre ile giriş şu anda kullanılamıyor. Google ile devam edebilirsin.";
        }
        if ("ERROR_USER_DISABLED".equals(code)) {
            return "Bu hesap devre dışı bırakılmış. Destek ile iletişime geç.";
        }
        if ("ERROR_INVALID_EMAIL".equals(code)) return "Geçerli bir e-posta adresi gir.";
        if ("register".equals(operation)) {
            if ("ERROR_WEAK_PASSWORD".equals(code)) {
                return "Şifre güvenlik koşullarını karşılamıyor. Daha uzun, büyük/küçük harf, rakam ve sembol içeren bir şifre dene.";
            }
            if ("ERROR_EMAIL_ALREADY_IN_USE".equals(code)
                    || "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL".equals(code)) {
                return "Bu e-posta ile kayıt tamamlanamadı. Mevcut giriş yöntemini veya Şifremi Unuttum seçeneğini kullan.";
            }
            return "Hesap oluşturulamadı. Lütfen tekrar dene.";
        }
        if ("reset".equals(operation)) {
            return "Şifre sıfırlama isteği tamamlanamadı. Lütfen tekrar dene.";
        }
        if ("ERROR_WRONG_PASSWORD".equals(code) || "ERROR_USER_NOT_FOUND".equals(code)
                || "ERROR_INVALID_CREDENTIAL".equals(code)
                || "ERROR_INVALID_LOGIN_CREDENTIALS".equals(code)) {
            return "E-posta veya şifre hatalı. Bilgilerini kontrol et.";
        }
        return "Giriş tamamlanamadı. Lütfen tekrar dene.";
    }
}
