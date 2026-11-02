package com.zeytindefteri.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Güncel fiyatın telefondaki tarafı: bölge fiyatının kısa süreli önbelleği (internetsiz açılışta da
 * öneri gelsin diye) ve kişinin kendi son satışları. Önbellekte yalnız elek başına fiyat durur.
 */
final class CurrentPriceStore {
    private static final String PREFS = "zd_current_prices";
    /** Kendi satışların bu kadar günden eskiyse "güncel" sayılmaz. */
    static final int OWN_DAYS = 30;

    private CurrentPriceStore() {}

    static String cached(Context c, String regionKey) {
        if (c == null || regionKey == null || regionKey.isEmpty()) return null;
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long at = p.getLong("t_" + regionKey, 0L);
        long age = System.currentTimeMillis() - at;
        if (at <= 0L || age < 0L || age > CurrentPrices.CACHE_MS) return null;
        return p.getString("j_" + regionKey, null);
    }

    static void save(Context c, String regionKey, String json) {
        if (c == null || regionKey == null || regionKey.isEmpty() || json == null) return;
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("j_" + regionKey, json)
                .putLong("t_" + regionKey, System.currentTimeMillis())
                .apply();
    }

    /** Hesap silinirken / çıkışta temizlik için. */
    static void clear(Context c) {
        if (c != null) c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    /** {ilçe, il}; bahçe bulunamazsa {"", ""}. */
    static String[] gardenRegion(SQLiteDatabase db, long gardenId) {
        String[] out = {"", ""};
        if (db == null || gardenId <= 0) return out;
        try (Cursor c = db.rawQuery("SELECT COALESCE(district,''), COALESCE(province,'') FROM gardens WHERE id = ?",
                new String[]{String.valueOf(gardenId)})) {
            if (c.moveToFirst()) { out[0] = c.getString(0).trim(); out[1] = c.getString(1).trim(); }
        } catch (RuntimeException ignored) { }
        return out;
    }

    /** Her elek için son {@value #OWN_DAYS} gündeki son 3 satışının ortadaki fiyatı; yoksa 0. */
    static double[] ownRecent(SQLiteDatabase db, String uid) {
        double[] out = new double[6];
        if (db == null) return out;
        String owner = uid == null ? "" : uid;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String from = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(new Date(System.currentTimeMillis() - OWN_DAYS * 86400000L));
        List<List<Double>> picked = new ArrayList<>();
        for (int i = 0; i < 6; i++) picked.add(new ArrayList<>());
        try (Cursor c = db.rawQuery(
                "SELECT eg.grade, eg.price_per_kg FROM elek_grades eg " +
                        "INNER JOIN elek_records er ON er.id = eg.elek_id " +
                        "WHERE (COALESCE(er.owner_uid,'') = ? OR COALESCE(er.owner_uid,'') = '') " +
                        "AND eg.kg > 0 AND eg.price_per_kg > 0 " +
                        "AND TRIM(er.record_date) >= ? AND TRIM(er.record_date) <= ? " +
                        "ORDER BY TRIM(er.record_date) DESC, er.id DESC LIMIT 300",
                new String[]{owner, from, today})) {
            while (c.moveToNext()) {
                int at = CurrentPrices.index(c.getString(0));
                if (at < 0 || picked.get(at).size() >= 3) continue;
                picked.get(at).add(c.getDouble(1));
            }
        } catch (RuntimeException ignored) { }
        for (int i = 0; i < 6; i++) out[i] = CurrentPrices.median(picked.get(i));
        return out;
    }
}
