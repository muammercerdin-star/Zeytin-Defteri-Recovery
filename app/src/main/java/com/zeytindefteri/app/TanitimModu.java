package com.zeytindefteri.app;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Tanıtım modu: reklam filmi ve gösterim için UYDURMA verilerle çalışma.
 *
 * Güvenlik (neden buluta, piyasaya ya da yedeğe gitmez):
 *  - Bütün uydurma satırlar {@link #OWNER} sahibine yazılır; açıkken DatabaseHelper.getActiveOwnerUid() bunu döner,
 *    böylece ekranlar yalnız uydurma veriyi gösterir, gerçek kayıtlar görünmez ve değişmez.
 *  - Bulut eşitlemesi, piyasa katkısı ve alacak eşitlemesi satırı Google hesabının uid'iyle seçer
 *    ("WHERE owner_uid = uid"); uydurma satırlar bu koşula hiç uymaz. Yedek, sahip ≠ uid olunca "Hesap eşleşmedi" der.
 *  - Sahibi boş satırlar kullanıcıya bağlanır; uydurma satırların sahibi hiçbir zaman boş değildir.
 *  - Silme ham SQL ile yapılır: bulut silme kaydı (tombstone) oluşmaz.
 */
final class TanitimModu {
    static final String OWNER = "tanitim-demo";
    /** Tanıtımda selamlamada görünen uydurma ad. */
    static final String NAME = "Ahmet Yılmaz";
    private static final String PREFS = "tanitim_modu", KEY_ON = "on";
    private static volatile Boolean cache;

    private TanitimModu() {}

    static boolean active(Context c) {
        Boolean v = cache;
        if (v == null) {
            v = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false);
            cache = v;
        }
        return v;
    }

    /** Açar: önce uydurma verileri yükler. Kapatır: uydurma verileri siler. */
    static void setActive(Context c, boolean on) {
        Context app = c.getApplicationContext();
        if (on) {
            cache = true;
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, true).commit();
            DatabaseHelper h = new DatabaseHelper(app);
            try {
                seed(h);
            } finally {
                h.close();
            }
        } else {
            DatabaseHelper h = new DatabaseHelper(app);
            try {
                clear(h.getWritableDatabase());
            } finally {
                h.close();
            }
            cache = false;
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, false).commit();
        }
    }

    /** Bütün uydurma satırları siler; silinen ana satır sayısını döner. */
    static int clear(SQLiteDatabase db) {
        String[] o = {OWNER};
        int n = 0;
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM elek_grades WHERE elek_id IN (SELECT id FROM elek_records WHERE owner_uid=?)", o);
            db.execSQL("DELETE FROM sale_grades WHERE sale_id IN (SELECT id FROM sales WHERE owner_uid=?)", o);
            db.execSQL("DELETE FROM harvest_grades WHERE harvest_id IN (SELECT id FROM harvests WHERE owner_uid=?)", o);
            db.execSQL("DELETE FROM brine_events WHERE batch_id IN (SELECT id FROM brine_batches WHERE owner_uid=?)", o);
            for (String t : new String[]{"receivables", "sales", "elek_records", "harvests", "operations", "brine_batches",
                    "inventory_movements", "inventory_items", "reminders", "garden_notes", "buyers", "gardens",
                    "sync_state", "sync_tombstones"}) {
                n += db.delete(t, "owner_uid=?", o);
            }
            db.delete("app_settings", "setting_key LIKE ?", new String[]{"%" + OWNER + "%"});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return n;
    }

    /** Uydurma bahçeler, alıcılar ve son üç haftanın elek/satış/alacak kayıtları (önce eskileri siler). */
    static void seed(DatabaseHelper h) {
        SQLiteDatabase db = h.getWritableDatabase();
        clear(db);
        GardenRepository gardens = new GardenRepository(h);
        long g1 = gardens.addGardenForOwner(OWNER, "", TanitimVerisi.GARDEN_1, 220, 15, "Gemlik", 12,
                "Manisa", "Alaşehir", "Pınarlı", "Damla", "Tanıtım verisi", "");
        long g2 = gardens.addGardenForOwner(OWNER, "", TanitimVerisi.GARDEN_2, 140, 9, "Gemlik", 8,
                "Manisa", "Alaşehir", "Yeniköy", "Yağmur", "Tanıtım verisi", "");
        if (g1 <= 0 || g2 <= 0) return;
        ElekSaleBridge bridge = new ElekSaleBridge(h);
        for (TanitimVerisi.Row r : TanitimVerisi.rows()) {
            long elekId = h.addElekRecord(r.garden == 1 ? g1 : g2, daysAgo(r.daysAgo), BuyerDirectory.GRADES, r.kg,
                    r.prices(), "Tanıtım verisi");
            if (elekId > 0) bridge.createSale(elekId, r.pay, r.partial, r.buyer);
        }
    }

    static String daysAgo(int days) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -days);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }
}
