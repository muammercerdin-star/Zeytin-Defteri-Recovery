package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Preserves backend order and cursor; no client timestamp sorting or cursor decoding. */
final class ModerationPage {
    static final String[] STATES = {"OPEN", "REVIEWED", "ACTIONED", "REJECTED"};
    static final String[] LABELS = {"Açık", "İncelendi", "İşlem Yapıldı", "Reddedildi"};
    String status = "OPEN";
    Object cursor;
    final LinkedHashMap<String, Map<String, Object>> rows = new LinkedHashMap<>();
    void reset(String state) { status = state; cursor = null; rows.clear(); }
    Map<String, Object> request(boolean more) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", status); data.put("limit", 25);
        if (more && cursor != null) data.put("cursor", cursor);
        return data;
    }
    @SuppressWarnings("unchecked")
    void accept(Object response) {
        if (!(response instanceof Map)) throw new IllegalArgumentException("Invalid page");
        Map<?, ?> data = (Map<?, ?>) response;
        if (!(data.get("reports") instanceof List) || !data.containsKey("next_cursor"))
            throw new IllegalArgumentException("Invalid page");
        List<Map<String, Object>> pending = new ArrayList<>();
        for (Object value : (List<?>) data.get("reports")) {
            if (!(value instanceof Map)) throw new IllegalArgumentException("Invalid report");
            Map<String, Object> row = (Map<String, Object>) value;
            if (string(row,"reporter_uid").isEmpty() || string(row,"report_id").isEmpty()
                    || !status.equals(row.get("status"))) throw new IllegalArgumentException("Invalid report");
            pending.add(new LinkedHashMap<>(row));
        }
        for (Map<String, Object> row : pending) rows.put(key(row), row);
        cursor = data.get("next_cursor");
    }
    static String key(Map<String, Object> row) { return string(row,"reporter_uid") + "/" + string(row,"report_id"); }
    static String string(Map<String, Object> row, String key) {
        Object value = row.get(key); return value instanceof String ? (String) value : "";
    }
    /** Şikâyetin içerik hedefi var mı (kullanıcı şikâyetinde kaldırılacak içerik yoktur). */
    static boolean hasContent(Map<String, Object> row) {
        String type = string(row, "target_type");
        return "POST".equals(type) || "COMMENT".equals(type) || "REPLY".equals(type);
    }
    /** Yalnızca şikâyet kimliği gönderilir; kaldırılacak içerik sunucuda şikâyet kaydından bulunur. */
    static Map<String, Object> content(Map<String, Object> row) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reporter_uid", row.get("reporter_uid")); data.put("report_id", row.get("report_id"));
        return data;
    }
    static Map<String, Object> remove(Map<String, Object> row) {
        Map<String, Object> data = content(row);
        data.put("expected_status", row.get("status"));
        return data;
    }
    /** Sunucunun içerik yanıtından ekranda gösterilecek metin. */
    static String contentText(Object response) {
        if (!(response instanceof Map)) return "İçerik okunamadı.";
        Map<?, ?> data = (Map<?, ?>) response;
        if (!Boolean.TRUE.equals(data.get("exists"))) return "İçerik bulunamadı (yazan kişi kalıcı olarak silmiş olabilir).";
        Object text = data.get("content"), author = data.get("author_name");
        String body = text instanceof String && !((String) text).trim().isEmpty() ? ((String) text).trim() : "(metin yok)";
        String who = author instanceof String && !((String) author).isEmpty() ? (String) author : "Üretici";
        String state = Boolean.TRUE.equals(data.get("moderated")) ? "Moderatör tarafından kaldırıldı"
                : Boolean.TRUE.equals(data.get("is_active")) ? "Yayında" : "Yazan kişi tarafından silindi";
        return "Yazan: " + who + "\nDurum: " + state + "\n\n" + body;
    }
    /** Kaldırma düğmesi yalnızca içerik bulutta varsa ve hâlâ yayındaysa açılır. */
    static boolean removable(Object response) {
        if (!(response instanceof Map)) return false;
        Map<?, ?> data = (Map<?, ?>) response;
        return Boolean.TRUE.equals(data.get("exists")) && Boolean.TRUE.equals(data.get("is_active"));
    }
    static Map<String, Object> update(Map<String, Object> row, String next) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reporter_uid", row.get("reporter_uid")); data.put("report_id", row.get("report_id"));
        data.put("status", next); data.put("expected_status", row.get("status"));
        return data;
    }
}
