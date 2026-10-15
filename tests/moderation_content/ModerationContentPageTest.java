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

        ModerationPage.Reported live = ModerationPage.reported(content(true, true, false, "  argo mesaj ", "Bora"));
        check(live.author.equals("Bora") && live.body.equals("argo mesaj") && live.badge().equals("YAYINDA"), "yayında: " + live.body);
        check(live.removable(), "yayındaki içerik kaldırılabilir");
        ModerationPage.Reported moderated = ModerationPage.reported(content(true, false, true, "x", "B"));
        check(!moderated.removable() && moderated.badge().equals("KALDIRILDI"), "kaldırılmış içerik yeniden kaldırılmaz");
        ModerationPage.Reported deleted = ModerationPage.reported(content(true, false, false, "x", "B"));
        check(!deleted.removable() && deleted.badge().equals("SAHİBİ SİLDİ"), "sahip silmesi");
        ModerationPage.Reported missing = ModerationPage.reported(content(false, false, false, "", ""));
        check(!missing.removable() && missing.body.startsWith("İçerik bulunamadı") && missing.badge().equals("BULUNAMADI"), "olmayan içerik");
        ModerationPage.Reported blank = ModerationPage.reported(content(true, true, false, "   ", null));
        check(blank.author.equals("Üretici") && blank.body.equals("(metin yok)"), "adsız yazar / boş metin");
        Map<String, Object> timed = content(true, true, false, "x", "B"); timed.put("created_at", 1700000000000L);
        check(ModerationPage.reported(timed).createdAt == 1700000000000L, "yazılma zamanı");
        timed.put("created_at", "1700000000000");
        check(ModerationPage.reported(timed).createdAt == 0, "metin zaman sayılmaz");
        // Beklenmeyen yanıt: kaldırma kapalı kalır.
        check(!ModerationPage.reported(null).removable() && !ModerationPage.reported("x").removable(), "geçersiz yanıt");
        check(!ModerationPage.reported(content("true", "true", false, "x", "B")).removable(), "metin 'true' boolean sayılmaz");
        System.out.println("ModerationContentPageTest: " + checks + " kontrol geçti");
    }
}
