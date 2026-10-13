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
    static Map<String, Object> update(Map<String, Object> row, String next) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reporter_uid", row.get("reporter_uid")); data.put("report_id", row.get("report_id"));
        data.put("status", next); data.put("expected_status", row.get("status"));
        return data;
    }
}
