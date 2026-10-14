package com.zeytindefteri.app;

import java.util.Map;

/**
 * Meydan gönderisinin buluta yazılabilmesi için firestore.rules (social_posts) ile birebir aynı kurallar.
 * Saf Java'dır; masaüstünde test edilir (tests/security).
 *
 * - Metin boş olamaz (en az bir boşluk dışı karakter) ve en fazla {@link #MAX_CONTENT} karakter olabilir.
 * - Görünen ad, doğrulanmış Firebase oturum belgesindeki "name" alanıdır; yoksa "Üretici".
 *   Kural da aynı değeri bekler (postAuthorName), bu yüzden başka bir ad yazılamaz.
 */
final class MeydanPostPolicy {
    static final int MAX_CONTENT = 5000;
    static final String DEFAULT_AUTHOR = "Üretici";

    private MeydanPostPolicy() {}

    static boolean isPublishableContent(String content) {
        if (content == null || content.length() > MAX_CONTENT) return false;
        for (int i = 0; i < content.length(); i++) {
            if (!Character.isWhitespace(content.charAt(i))) return true;
        }
        return false;
    }

    /** Kuraldaki postAuthorName ile aynı: boş olmayan "name" metni, yoksa varsayılan ad. Kırpma yapılmaz. */
    static String authorName(Object nameClaim) {
        if (nameClaim instanceof String && !((String) nameClaim).isEmpty()) return (String) nameClaim;
        return DEFAULT_AUTHOR;
    }

    static String authorNameFromClaims(Map<String, Object> claims) {
        return authorName(claims == null ? null : claims.get("name"));
    }
}
