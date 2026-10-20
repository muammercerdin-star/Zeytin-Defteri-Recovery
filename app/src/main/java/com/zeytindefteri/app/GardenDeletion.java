package com.zeytindefteri.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * Bahçeyi tüm kayıtlarıyla siler.
 *
 * - Bahçeye bağlı işlem, hasat, elek, satış, salamura ve notlar veritabanında ON DELETE CASCADE ile gider;
 *   buluttaki kopyaları bahçenin silme işaretiyle temizlenir (CloudDeletionRepository alt koleksiyonları siler).
 * - Satışlara bağlı alacaklar CASCADE ile gitmez (SET NULL): sahipsiz borç kalmasın diye önce onlar silinir.
 * - Son bahçe silinmez (uygulama en az bir bahçeyle çalışır); başka hesabın bahçesine dokunulmaz.
 */
public final class GardenDeletion {
    private GardenDeletion() {}

    /** Silinecek kayıtların sayısı: {işlem, hasat, elek, satış}. */
    public static int[] counts(SQLiteDatabase db, long gardenId) {
        String[] tables = {"operations", "harvests", "elek_records", "sales"};
        int[] out = new int[tables.length];
        for (int i = 0; i < tables.length; i++) out[i] = count(db, "SELECT COUNT(*) FROM " + tables[i] + " WHERE garden_id=?", gardenId);
        return out;
    }

    /** Bu hesabın bahçe sayısı. */
    public static int gardenCount(SQLiteDatabase db, String owner) {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM gardens WHERE COALESCE(owner_uid,'')=?", new String[]{owner == null ? "" : owner});
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    /** Ekrandaki özet: "12 işlem · 3 hasat · 5 elek · 4 satış" (boşsa "kayıt yok"). */
    public static String summary(int[] c) {
        String[] names = {"işlem", "hasat", "elek", "satış"};
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < c.length; i++) {
            if (c[i] <= 0) continue;
            if (b.length() > 0) b.append(" · ");
            b.append(c[i]).append(' ').append(names[i]);
        }
        return b.length() == 0 ? "kayıt yok" : b.toString();
    }

    /** Onay için yazılan ad bahçe adıyla eşleşiyor mu (büyük/küçük harf ve baş/son boşluk önemsiz). */
    public static boolean nameMatches(String typed, String gardenName) {
        if (typed == null || gardenName == null) return false;
        String a = typed.trim(), b = gardenName.trim();
        return !b.isEmpty() && a.toLowerCase(new java.util.Locale("tr", "TR")).equals(b.toLowerCase(new java.util.Locale("tr", "TR")));
    }

    /** Sonuç: 1 silindi, 0 silinemedi, -2 son bahçe (silinmez). */
    public static int delete(DatabaseHelper helper, long gardenId) {
        if (gardenId <= 0) return 0;
        String owner = helper.getActiveOwnerUid();
        SQLiteDatabase db = helper.getWritableDatabase();
        if (!ownedBy(db, gardenId, owner)) return 0;
        if (gardenCount(db, owner) <= 1) return -2;
        List<Long> receivables = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT r.id FROM receivables r INNER JOIN sales s ON s.id=r.related_sale_id "
                + "WHERE s.garden_id=? AND COALESCE(r.owner_uid,'')=?", new String[]{String.valueOf(gardenId), owner == null ? "" : owner});
        try { while (c.moveToNext()) receivables.add(c.getLong(0)); } finally { c.close(); }
        db.beginTransaction();
        try {
            for (long id : receivables) {
                helper.recordCloudDeletion("receivables", id);
                db.delete("receivables", "id=?", new String[]{String.valueOf(id)});
            }
            if (new GardenRepository(helper).deleteGarden(gardenId) <= 0) return 0;
            db.setTransactionSuccessful();
            return 1;
        } catch (RuntimeException e) {
            return 0;
        } finally {
            db.endTransaction();
        }
    }

    private static boolean ownedBy(SQLiteDatabase db, long gardenId, String owner) {
        Cursor c = db.rawQuery("SELECT 1 FROM gardens WHERE id=? AND COALESCE(owner_uid,'')=?",
                new String[]{String.valueOf(gardenId), owner == null ? "" : owner});
        try { return c.moveToFirst(); } finally { c.close(); }
    }

    private static int count(SQLiteDatabase db, String sql, long gardenId) {
        try {
            Cursor c = db.rawQuery(sql, new String[]{String.valueOf(gardenId)});
            try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
