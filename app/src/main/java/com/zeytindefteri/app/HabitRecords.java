package com.zeytindefteri.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Alışkanlıkların okunduğu kayıtlar (İP5): etkin hesabın satışları (yalnız gerçek satış) ve işlemleri. Yalnız okur,
 * hiçbir şey yazmaz; sonuç her seferinde kayıtlardan yeniden hesaplanır (HabitModel), saklanmaz, sunucuya gitmez.
 */
final class HabitRecords {
    private HabitRecords() {
    }

    /** Son kaç kayda bakılır (model yalnız son 6 benzerini kullanır; benzerleri bulmak için geniş pencere). */
    private static final int LIMIT = 400;

    /** Etkin hesabın alışkanlık modeli. Okunamazsa boş model (öneri yok, form bugünkü gibi). */
    static HabitModel load(Context context) {
        DatabaseHelper helper = new DatabaseHelper(context.getApplicationContext());
        String owner = helper.getActiveOwnerUid();
        List<HabitModel.Sale> sales = new ArrayList<>();
        List<HabitModel.Op> ops = new ArrayList<>();
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            sales = sales(db, owner);
            ops = operations(db, owner);
        } catch (RuntimeException e) {
            sales.clear();
            ops.clear();
        } finally {
            helper.close();
        }
        return new HabitModel(sales, ops, System.currentTimeMillis(), HabitDb.store(context, owner));
    }

    /** Etkin hesabın düzeltme deposu ("Bu yanlış"). */
    static HabitCorrections.Store corrections(Context context) {
        DatabaseHelper helper = new DatabaseHelper(context.getApplicationContext());
        try {
            return HabitDb.store(context, helper.getActiveOwnerUid());
        } finally {
            helper.close();
        }
    }

    private static String ownerCondition(String owner) {
        return owner.isEmpty() ? "(g.owner_uid IS NULL OR TRIM(g.owner_uid) = '')" : "g.owner_uid = ?";
    }

    private static String[] ownerArgs(String owner) {
        return owner.isEmpty() ? null : new String[]{owner};
    }

    private static List<HabitModel.Sale> sales(SQLiteDatabase db, String owner) {
        Map<Long, Object[]> rows = new LinkedHashMap<>();
        String sql = "SELECT s.id, s.created_at, COALESCE((SELECT b.name FROM buyers b WHERE b.id = s.buyer_id), ''), "
                + "COALESCE(g.name, '') FROM sales s INNER JOIN gardens g ON g.id = s.garden_id "
                + "WHERE s.sale_kind = 'SALE' AND " + ownerCondition(owner) + " ORDER BY s.created_at DESC LIMIT " + LIMIT;
        try (Cursor c = db.rawQuery(sql, ownerArgs(owner))) {
            while (c.moveToNext()) {
                rows.put(c.getLong(0), new Object[]{c.getLong(1), c.getString(2), c.getString(3)});
            }
        }
        Map<Long, Map<String, Double>> prices = new LinkedHashMap<>();
        Map<Long, Map<String, Double>> kilos = new LinkedHashMap<>();
        if (!rows.isEmpty()) {
            StringBuilder ids = new StringBuilder();
            for (Long id : rows.keySet()) {
                if (ids.length() > 0) {
                    ids.append(',');
                }
                ids.append(id);
            }
            String grades = "SELECT sale_id, grade, COALESCE(kg, 0), COALESCE(price_per_kg, 0) FROM sale_grades WHERE sale_id IN ("
                    + ids + ")";
            try (Cursor c = db.rawQuery(grades, null)) {
                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    String grade = c.getString(1);
                    double kg = c.getDouble(2);
                    double price = c.getDouble(3);
                    if (grade == null || kg <= 0) {
                        continue;
                    }
                    kilos.computeIfAbsent(id, k -> new LinkedHashMap<>()).put(grade, kg);
                    if (price > 0) {
                        prices.computeIfAbsent(id, k -> new LinkedHashMap<>()).put(grade, price);
                    }
                }
            }
        }
        List<HabitModel.Sale> out = new ArrayList<>();
        for (Map.Entry<Long, Object[]> e : rows.entrySet()) {
            Object[] r = e.getValue();
            out.add(new HabitModel.Sale((Long) r[0], (String) r[1], (String) r[2], prices.get(e.getKey()), kilos.get(e.getKey())));
        }
        return out;
    }

    private static List<HabitModel.Op> operations(SQLiteDatabase db, String owner) {
        List<HabitModel.Op> out = new ArrayList<>();
        String sql = "SELECT o.created_at, o.type, COALESCE(o.note, ''), COALESCE(o.quantity, 0), COALESCE(o.unit, ''), "
                + "COALESCE(g.name, '') FROM operations o INNER JOIN gardens g ON g.id = o.garden_id WHERE "
                + ownerCondition(owner) + " ORDER BY o.created_at DESC LIMIT " + LIMIT;
        try (Cursor c = db.rawQuery(sql, ownerArgs(owner))) {
            while (c.moveToNext()) {
                out.add(new HabitModel.Op(c.getLong(0), c.getString(1), c.getString(2), c.getDouble(3), c.getString(4),
                        c.getString(5)));
            }
        }
        return out;
    }
}
