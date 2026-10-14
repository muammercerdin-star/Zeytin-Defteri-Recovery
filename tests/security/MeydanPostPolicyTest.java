package com.zeytindefteri.app;

import java.util.HashMap;
import java.util.Map;

/** Gönderinin istemci tarafı kuralı (firestore.rules social_posts ile aynı). Gerçek MeydanPostPolicy sınıfıyla çalışır. */
public class MeydanPostPolicyTest {
    static int checks = 0;
    static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }

    static String repeat(char c, int n) { StringBuilder b = new StringBuilder(); for (int i = 0; i < n; i++) b.append(c); return b.toString(); }

    public static void main(String[] args) {
        // Metin: boş/boşluk RED, 5.000 sınırı
        check(!MeydanPostPolicy.isPublishableContent(null), "null kabul edildi");
        check(!MeydanPostPolicy.isPublishableContent(""), "boş kabul edildi");
        check(!MeydanPostPolicy.isPublishableContent("  \n\t\r "), "yalnız boşluk kabul edildi");
        check(MeydanPostPolicy.isPublishableContent("Sinek var."), "geçerli metin reddedildi");
        check(MeydanPostPolicy.isPublishableContent(repeat('z', 5000)), "5000 karakter reddedildi");
        check(!MeydanPostPolicy.isPublishableContent(repeat('z', 5001)), "5001 karakter kabul edildi");
        check(MeydanPostPolicy.MAX_CONTENT == 5000, "sınır 5000 değil");
        // Kuraldaki RE2 \\s yalnız ASCII boşlukları kapsar; istemci Unicode boşlukları da boş sayar (daha sıkı, hiç reddedilmez).
        check(!MeydanPostPolicy.isPublishableContent("  "), "Unicode boşluk kabul edildi");
        check(MeydanPostPolicy.isPublishableContent(" x"), "metin reddedildi");

        // Ad: oturum belgesindeki "name"; yoksa "Üretici"; kırpılmaz (kural tam eşitlik ister)
        Map<String, Object> claims = new HashMap<>();
        check(MeydanPostPolicy.authorNameFromClaims(null).equals("Üretici"), "claims null");
        check(MeydanPostPolicy.authorNameFromClaims(claims).equals("Üretici"), "name yok");
        claims.put("name", "");
        check(MeydanPostPolicy.authorNameFromClaims(claims).equals("Üretici"), "boş name");
        claims.put("name", 42L);
        check(MeydanPostPolicy.authorNameFromClaims(claims).equals("Üretici"), "metin olmayan name");
        claims.put("name", "Ali Çiftçi");
        check(MeydanPostPolicy.authorNameFromClaims(claims).equals("Ali Çiftçi"), "name okunmadı");
        claims.put("name", " Ali ");
        check(MeydanPostPolicy.authorNameFromClaims(claims).equals(" Ali "), "name kırpıldı (kural tam eşitlik ister)");

        System.out.println("MeydanPostPolicyTest: " + checks + " kontrol geçti");
    }
}
