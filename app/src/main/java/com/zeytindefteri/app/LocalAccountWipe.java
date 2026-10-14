package com.zeytindefteri.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * Bir hesabın bu telefondaki verisini siler (hesap silme sonrası, ya da misafir verisi için).
 *
 * Aynı telefonda başka hesapların verisi de bulunabileceği için veritabanının tamamı silinmez:
 * - owner_uid sütunu olan her tablodan yalnız bu hesabın satırları silinir. Tablolar şemadan okunur;
 *   yeni bir sahipli tablo eklenirse otomatik kapsanır.
 * - Bahçeye bağlı alt kayıtlar (sahip alanı boş eski kayıtlar dahil) yabancı anahtar CASCADE ile gider;
 *   bu yüzden önce bahçeler silinir. DatabaseHelper.onConfigure yabancı anahtarları açar.
 * - Meydan önbelleğinde kişinin gönderi/yorum/tepki/kaydetmeleri ve hesaba bağlı ayar anahtarları silinir.
 * Tek işlemde yapılır: yarıda kalırsa hiçbir şey silinmez.
 */
public final class LocalAccountWipe {
    private LocalAccountWipe() {}

    /** Silinen satır sayısını döndürür. uid boşsa misafir (sahipsiz) veri silinir. */
    public static int wipe(SQLiteDatabase db, String uid) {
        String owner = uid == null ? "" : uid.trim();
        String userKey = "firebase:" + owner;
        int deleted = 0;
        db.beginTransaction();
        try {
            List<String> owned = ownerTables(db);
            if (owned.remove("gardens")) deleted += db.delete("gardens", "COALESCE(owner_uid,'') = ?", new String[]{owner});
            for (String table : owned) {
                // Sahip alanı boş eski alt kayıtlar bahçenin sahibine aittir (DatabaseHelper kuralı):
                // başka bir hesabın bahçesine bağlı satır, sahip alanı eşleşse bile silinmez.
                if (hasColumn(db, table, "garden_id")) {
                    deleted += db.delete(table, "COALESCE(owner_uid,'') = ? AND (garden_id IS NULL OR garden_id NOT IN "
                            + "(SELECT id FROM gardens WHERE COALESCE(owner_uid,'') <> ?))", new String[]{owner, owner});
                } else {
                    deleted += db.delete(table, "COALESCE(owner_uid,'') = ?", new String[]{owner});
                }
            }
            if (!owner.isEmpty()) {
                if (hasTable(db, "posts")) deleted += db.delete("posts", "COALESCE(user_id,'') = ?", new String[]{owner});
                for (String table : new String[]{"post_comments", "post_reactions", "post_saves"}) {
                    if (hasTable(db, table)) {
                        deleted += db.delete(table, "COALESCE(user_key,'') IN (?, ?)", new String[]{owner, userKey});
                    }
                }
                if (hasTable(db, "app_settings")) {
                    deleted += db.delete("app_settings", "instr(setting_key, ?) > 0", new String[]{owner});
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return deleted;
    }

    /** owner_uid sütunu olan tablolar (sqlite_ dahili tabloları hariç). */
    static List<String> ownerTables(SQLiteDatabase db) {
        List<String> tables = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name", null)) {
            while (c.moveToNext()) tables.add(c.getString(0));
        }
        List<String> owned = new ArrayList<>();
        for (String table : tables) if (hasColumn(db, table, "owner_uid")) owned.add(table);
        return owned;
    }

    private static boolean hasColumn(SQLiteDatabase db, String table, String column) {
        // pragma_table_info() SQLite 3.16 ister; Android 7.0 (minSdk 24) 3.9 ile gelir. Tablo adı
        // sqlite_master'dan geldiği için güvenlidir; yine de tırnaklanır.
        try (Cursor c = db.rawQuery("PRAGMA table_info(\"" + table.replace("\"", "\"\"") + "\")", null)) {
            int name = c.getColumnIndex("name");
            while (c.moveToNext()) {
                if (column.equals(c.getString(name))) return true;
            }
        }
        return false;
    }

    private static boolean hasTable(SQLiteDatabase db, String name) {
        try (Cursor c = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", new String[]{name})) {
            return c.moveToFirst();
        }
    }
}
