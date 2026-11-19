package com.zeytindefteri.app;

import android.content.Context;
import android.database.Cursor;
import java.util.HashMap;
import java.util.Map;

/**
 * Öğretme penceresinin düğme sırası için çiftçinin kendi kayıtlarındaki iş sayıları (İP5). Yalnız okunur:
 * işlem türleri ("Diğer" işlerinde not) ve hasat sayısı. Hiçbir şey yazılmaz, saklanmaz; her soruda yeniden sayılır.
 */
final class WorkCounts {
    private WorkCounts() {
    }

    /** Choice.key() → kayıt sayısı. Okunamazsa boş (düğmeler sabit sırada). */
    static Map<String, Integer> load(Context context, String owner) {
        Map<String, Integer> counts = new HashMap<>();
        DatabaseHelper db = new DatabaseHelper(context.getApplicationContext());
        try {
            try (Cursor c = db.getOperationsForOwner(owner)) {
                int type = c.getColumnIndex("type");
                int note = c.getColumnIndex("note");
                while (c.moveToNext()) {
                    String key = LearnedTerms.workKey(c.getString(type), note < 0 ? "" : c.getString(note));
                    if (!key.isEmpty()) {
                        counts.merge(key, 1, Integer::sum);
                    }
                }
            }
            String sql = "SELECT COUNT(*) FROM harvests h INNER JOIN gardens g ON g.id = h.garden_id WHERE g.owner_uid = ?";
            try (Cursor c = db.getReadableDatabase().rawQuery(sql, new String[]{owner == null ? "" : owner})) {
                if (c.moveToFirst()) {
                    counts.put("Hasat", c.getInt(0));
                }
            }
        } catch (RuntimeException e) {
            counts.clear();
        } finally {
            db.close();
        }
        return counts;
    }
}
