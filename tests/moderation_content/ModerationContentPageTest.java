package com.zeytindefteri.app;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** Moderasyon Merkezi içerik kaldırma: istek gövdeleri ve ekrandaki metin. Gerçek ModerationPage ile çalışır. */
public class ModerationContentPageTest {
    static int checks = 0;
    static void check(boolean ok, String m) { checks++; if (!ok) throw new AssertionError(m); }
    static Map<String, Object> row(String type, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reporter_uid", "A"); m.put("report_id", type + "|p|p"); m.put("status", status);
        m.put("target_type", type); m.put("post_cloud_id", "p"); m.put("reported_uid", "B");
        return m;
    }
    static Map<String, Object> content(Object exists, Object active, Object moderated, Object text, Object author) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("exists", exists); m.put("is_active", active); m.put("moderated", moderated); m.put("content", text); m.put("author_name", author);
        return m;
    }

    public static void main(String[] args) {
        for (String t : new String[]{"POST", "COMMENT", "REPLY"}) check(ModerationPage.hasContent(row(t, "OPEN")), t + " içerik hedefi");
        check(!ModerationPage.hasContent(row("USER", "OPEN")), "kullanıcı şikâyetinde kaldırma yok");
        check(!ModerationPage.hasContent(row("", "OPEN")), "bilinmeyen tür");

        // Sunucuya yalnızca şikâyet kimliği gider; hedef yolu, sahip ya da içerik gönderilmez.
        Map<String, Object> read = ModerationPage.content(row("POST", "OPEN"));
        check(read.keySet().equals(new HashSet<>(Arrays.asList("reporter_uid", "report_id"))), "okuma gövdesi: " + read.keySet());
        Map<String, Object> remove = ModerationPage.remove(row("REPLY", "REVIEWED"));
        check(remove.keySet().equals(new HashSet<>(Arrays.asList("reporter_uid", "report_id", "expected_status"))), "kaldırma gövdesi: " + remove.keySet());
        check("REVIEWED".equals(remove.get("expected_status")), "beklenen durum");

        String live = ModerationPage.contentText(content(true, true, false, "  argo mesaj ", "Bora"));
        check(live.contains("Yazan: Bora") && live.contains("Durum: Yayında") && live.endsWith("argo mesaj"), live);
        check(ModerationPage.removable(content(true, true, false, "x", "B")), "yayındaki içerik kaldırılabilir");
        check(!ModerationPage.removable(content(true, false, true, "x", "B")), "kaldırılmış içerik yeniden kaldırılmaz");
        check(ModerationPage.contentText(content(true, false, true, "x", "B")).contains("Moderatör tarafından kaldırıldı"), "moderatör durumu");
        check(ModerationPage.contentText(content(true, false, false, "x", "B")).contains("Yazan kişi tarafından silindi"), "sahip silmesi");
        check(!ModerationPage.removable(content(false, false, false, "", "")), "olmayan içerik");
        check(ModerationPage.contentText(content(false, false, false, "", "")).startsWith("İçerik bulunamadı"), "olmayan içerik metni");
        check(ModerationPage.contentText(content(true, true, false, "   ", null)).contains("Yazan: Üretici") , "adsız yazar");
        check(ModerationPage.contentText(content(true, true, false, "   ", null)).endsWith("(metin yok)"), "boş metin");
        // Beklenmeyen yanıt: kaldırma kapalı kalır.
        check(!ModerationPage.removable(null) && !ModerationPage.removable("x"), "geçersiz yanıt");
        check(!ModerationPage.removable(content("true", "true", false, "x", "B")), "metin 'true' boolean sayılmaz");
        System.out.println("ModerationContentPageTest: " + checks + " kontrol geçti");
    }
}
