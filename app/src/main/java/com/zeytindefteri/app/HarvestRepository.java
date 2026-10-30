package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;


public class HarvestRepository {


    // ============================================================
    // DATABASE
    // ============================================================

    private final DatabaseHelper databaseHelper;


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public HarvestRepository(
            DatabaseHelper databaseHelper
    ) {


        this.databaseHelper =
                databaseHelper;
    }


    // ============================================================
    // ADD HARVEST
    // ============================================================

    public long addHarvest(
            int gardenId,
            String harvestDate,
            double totalKg,
            double laborCost,
            double transportCost,
            double otherCost,
            String note
    ) {


        if (gardenId <= 0) {


            return -1;
        }


        harvestDate =
                safe(
                        harvestDate
                );


        if (harvestDate.isEmpty()) {


            return -1;
        }


        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "garden_id",
                gardenId
        );


        values.put(
                "harvest_date",
                harvestDate
        );


        values.put(
                "total_kg",
                Math.max(
                        totalKg,
                        0
                )
        );


        values.put(
                "labor_cost",
                Math.max(
                        laborCost,
                        0
                )
        );


        values.put(
                "transport_cost",
                Math.max(
                        transportCost,
                        0
                )
        );


        values.put(
                "other_cost",
                Math.max(
                        otherCost,
                        0
                )
        );


        values.put(
                "note",
                safe(
                        note
                )
        );


        values.put(
                "created_at",
                System.currentTimeMillis()
        );


        return db.insert(
                "harvests",
                null,
                values
        );
    }


    // ============================================================
    // ADD HARVEST GRADE
    // ============================================================

    public long addHarvestGrade(
            long harvestId,
            String grade,
            double kg,
            double referencePricePerKg,
            String referencePriceDate
    ) {


        if (harvestId <= 0) {


            return -1;
        }


        grade =
                safe(
                        grade
                );


        if (grade.isEmpty()) {


            return -1;
        }


        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "harvest_id",
                harvestId
        );


        values.put(
                "grade",
                grade
        );


        values.put(
                "kg",
                Math.max(
                        kg,
                        0
                )
        );


        values.put(
                "reference_price_per_kg",
                Math.max(
                        referencePricePerKg,
                        0
                )
        );


        values.put(
                "reference_price_date",
                safe(
                        referencePriceDate
                )
        );


        long result = db.insert(
                "harvest_grades",
                null,
                values
        );

        if (result > 0) {
            ContentValues touch = new ContentValues();
            touch.put("updated_at", System.currentTimeMillis());
            db.update("harvests", touch, "id=?", new String[]{String.valueOf(harvestId)});
        }

        return result;
    }


    // ============================================================
    // ADD HARVEST GRADE - KISA VERSİYON
    // ============================================================

    public long addHarvestGrade(
            long harvestId,
            String grade,
            double kg
    ) {


        return addHarvestGrade(
                harvestId,
                grade,
                kg,
                0,
                ""
        );
    }


    // ============================================================
    // GET HARVEST
    // ============================================================

    public Cursor getHarvest(
            long harvestId
    ) {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT " +

                        "h.*," +

                        "g.name AS garden_name " +

                        "FROM harvests h " +

                        "INNER JOIN gardens g " +
                        "ON g.id = h.garden_id " +

                        "WHERE h.id = ?",

                new String[]{
                        String.valueOf(
                                harvestId
                        )
                }
        );
    }


    // ============================================================
    // GET GARDEN HARVESTS
    // ============================================================

    public Cursor getGardenHarvests(
            long gardenId
    ) {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT " +

                        "h.*," +

                        "g.name AS garden_name " +

                        "FROM harvests h " +

                        "INNER JOIN gardens g " +
                        "ON g.id = h.garden_id " +

                        "WHERE h.garden_id = ? " +

                        "ORDER BY h.created_at DESC",

                new String[]{
                        String.valueOf(
                                gardenId
                        )
                }
        );
    }


    // ============================================================
    // GET ALL HARVESTS
    // ============================================================

    public Cursor getHarvests() {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT " +

                        "h.*," +

                        "g.name AS garden_name " +

                        "FROM harvests h " +

                        "INNER JOIN gardens g " +
                        "ON g.id = h.garden_id " +

                        "ORDER BY h.created_at DESC",

                null
        );
    }


    // ============================================================
    // GET HARVEST GRADES
    // ============================================================

    public Cursor getHarvestGrades(
            long harvestId
    ) {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT * " +

                        "FROM harvest_grades " +

                        "WHERE harvest_id = ? " +

                        "ORDER BY CASE grade " +

                        "WHEN 'DOUBLE' THEN 0 " +
                        "WHEN '1' THEN 1 " +
                        "WHEN '2' THEN 2 " +
                        "WHEN '3' THEN 3 " +
                        "WHEN '4' THEN 4 " +
                        "WHEN 'YAGLIK' THEN 5 " +

                        "ELSE 6 END",

                new String[]{
                        String.valueOf(
                                harvestId
                        )
                }
        );
    }


    // ============================================================
    // TOTAL HARVEST KG
    // ============================================================

    public double getTotalHarvestKg() {


        return getDoubleValue(

                "SELECT COALESCE(SUM(total_kg), 0) " +
                        "FROM harvests"
        );
    }


    // ============================================================
    // GARDEN TOTAL HARVEST KG
    // ============================================================

    public double getGardenTotalHarvestKg(
            long gardenId
    ) {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT COALESCE(SUM(total_kg), 0) " +

                                    "FROM harvests " +

                                    "WHERE garden_id = ?",

                            new String[]{
                                    String.valueOf(
                                            gardenId
                                    )
                            }
                    );


            if (cursor.moveToFirst()) {


                return cursor.getDouble(
                        0
                );
            }


        } finally {


            if (cursor != null) {


                cursor.close();
            }
        }


        return 0;
    }


    // ============================================================
    // TOTAL HARVEST COST
    // ============================================================

    public double getTotalHarvestCost() {


        return getDoubleValue(

                "SELECT COALESCE(" +

                        "SUM(" +

                        "COALESCE(labor_cost, 0) + " +

                        "COALESCE(transport_cost, 0) + " +

                        "COALESCE(other_cost, 0)" +

                        "), 0) " +

                        "FROM harvests"
        );
    }


    // ============================================================
    // GARDEN HARVEST COST
    // ============================================================

    public double getGardenHarvestCost(
            long gardenId
    ) {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT COALESCE(" +

                                    "SUM(" +

                                    "COALESCE(labor_cost, 0) + " +

                                    "COALESCE(transport_cost, 0) + " +

                                    "COALESCE(other_cost, 0)" +

                                    "), 0) " +

                                    "FROM harvests " +

                                    "WHERE garden_id = ?",

                            new String[]{
                                    String.valueOf(
                                            gardenId
                                    )
                            }
                    );


            if (cursor.moveToFirst()) {


                return cursor.getDouble(
                        0
                );
            }


        } finally {


            if (cursor != null) {


                cursor.close();
            }
        }


        return 0;
    }


    // ============================================================
    // LAST HARVEST REFERENCE PRICE
    // ============================================================

    public double getLastHarvestReferencePrice(
            String grade
    ) {


        grade =
                safe(
                        grade
                );


        if (grade.isEmpty()) {


            return 0;
        }


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT hg.reference_price_per_kg " +

                                    "FROM harvest_grades hg " +

                                    "INNER JOIN harvests h " +
                                    "ON h.id = hg.harvest_id " +

                                    "WHERE hg.grade = ? " +

                                    "AND hg.reference_price_per_kg > 0 " +

                                    "ORDER BY h.created_at DESC " +

                                    "LIMIT 1",

                            new String[]{
                                    grade
                            }
                    );


            if (cursor.moveToFirst()) {


                return cursor.getDouble(
                        0
                );
            }


        } finally {


            if (cursor != null) {


                cursor.close();
            }
        }


        return 0;
    }


    // ============================================================
    // UPDATE HARVEST
    // ============================================================

    public int updateHarvest(
            long harvestId,
            int gardenId,
            String harvestDate,
            double totalKg,
            double laborCost,
            double transportCost,
            double otherCost,
            String note
    ) {


        if (harvestId <= 0
                ||
                gardenId <= 0) {


            return 0;
        }


        harvestDate =
                safe(
                        harvestDate
                );


        if (harvestDate.isEmpty()) {


            return 0;
        }


        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "garden_id",
                gardenId
        );


        values.put(
                "harvest_date",
                harvestDate
        );


        values.put(
                "total_kg",
                Math.max(
                        totalKg,
                        0
                )
        );


        values.put(
                "labor_cost",
                Math.max(
                        laborCost,
                        0
                )
        );


        values.put(
                "transport_cost",
                Math.max(
                        transportCost,
                        0
                )
        );


        values.put(
                "other_cost",
                Math.max(
                        otherCost,
                        0
                )
        );


        values.put(
                "note",
                safe(
                        note
                )
        );

        values.put("updated_at", System.currentTimeMillis());


        return db.update(

                "harvests",

                values,

                "id = ?",

                new String[]{
                        String.valueOf(
                                harvestId
                        )
                }
        );
    }


    // ============================================================
    // DELETE HARVEST
    // ============================================================

    public int deleteHarvest(
            long harvestId
    ) {


        if (harvestId <= 0) {


            return 0;
        }


        databaseHelper.recordCloudDeletion("harvests", harvestId);

        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        /*
         * harvest_grades FOREIGN KEY + ON DELETE CASCADE
         * olduğu için hasat silinince elek dağılımı da silinir.
         */
        return db.delete(

                "harvests",

                "id = ?",

                new String[]{
                        String.valueOf(
                                harvestId
                        )
                }
        );
    }


    // ============================================================
    // BU SEZONUN HASATLARI (silme listesi) + HASATI MASRAF HAREKETİYLE SİL
    // ============================================================

    /** Bahçenin aktif sezondaki hasat kayıtları (yalnız bu hesabın bahçesi), en yeni başta. */
    public Cursor getSeasonHarvests(long gardenId) {
        String owner = databaseHelper.getActiveOwnerUid();
        String o = owner == null ? "" : owner.trim();
        String year = String.valueOf(databaseHelper.getActiveSeasonYear());
        return databaseHelper.getReadableDatabase().rawQuery(
                "SELECT h.id, h.harvest_date, h.total_kg, h.labor_cost, h.transport_cost, h.other_cost, h.note "
                        + "FROM harvests h INNER JOIN gardens g ON g.id = h.garden_id "
                        + "WHERE h.garden_id = ? AND TRIM(COALESCE(g.owner_uid, '')) = ? "
                        + "AND (COALESCE(h.season_year, 0) = CAST(? AS INTEGER) OR (COALESCE(h.season_year, 0) = 0 AND "
                        + "(substr(TRIM(h.harvest_date), -4) = ? OR substr(TRIM(h.harvest_date), 1, 4) = ?))) "
                        + "ORDER BY h.created_at DESC, h.id DESC",
                new String[]{String.valueOf(gardenId), o, year, year, year});
    }

    /**
     * Hasadı ve kaydedilirken oluşturulan "Hasat" işlem hareketini (işçilik/nakliye masrafı) birlikte siler;
     * ikisi de buluttan silinmek üzere işaretlenir. Boy satırları (harvest_grades) CASCADE ile gider.
     * Elek kayıtlarına dokunulmaz. Dönüş: silinen hasat sayısı (0: bulunamadı / başka hesabın).
     */
    public int deleteHarvestWithOperation(long harvestId) {
        if (harvestId <= 0) return 0;
        String owner = databaseHelper.getActiveOwnerUid();
        String o = owner == null ? "" : owner.trim();
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        long gardenId, createdAt;
        String date;
        double kg;
        try (Cursor c = db.rawQuery("SELECT h.garden_id, h.harvest_date, h.total_kg, h.created_at FROM harvests h "
                + "INNER JOIN gardens g ON g.id = h.garden_id WHERE h.id = ? AND TRIM(COALESCE(g.owner_uid, '')) = ? LIMIT 1",
                new String[]{String.valueOf(harvestId), o})) {
            if (!c.moveToFirst()) return 0;
            gardenId = c.getLong(0);
            date = c.getString(1) == null ? "" : c.getString(1).trim();
            kg = c.getDouble(2);
            createdAt = c.getLong(3);
        }
        // Aynı bahçe, aynı gün, aynı kilo "Hasat" hareketi; birden çok varsa kayıt anına en yakını.
        long operationId = -1;
        try (Cursor c = db.rawQuery("SELECT id FROM operations WHERE garden_id = ? AND type = 'Hasat' "
                + "AND TRIM(operation_date) = ? AND ABS(COALESCE(quantity, 0) - ?) < 0.01 "
                + "ORDER BY ABS(COALESCE(created_at, 0) - ?) LIMIT 1",
                new String[]{String.valueOf(gardenId), date, String.valueOf(kg), String.valueOf(createdAt)})) {
            if (c.moveToFirst()) operationId = c.getLong(0);
        }
        int deleted;
        db.beginTransaction();
        try {
            databaseHelper.recordCloudDeletion("harvests", harvestId);
            if (operationId > 0) databaseHelper.recordCloudDeletion("operations", operationId);
            deleted = db.delete("harvests", "id = ?", new String[]{String.valueOf(harvestId)});
            if (operationId > 0) db.delete("operations", "id = ?", new String[]{String.valueOf(operationId)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return deleted;
    }


    // ============================================================
    // DELETE HARVEST GRADE
    // ============================================================

    public int deleteHarvestGrade(
            long harvestGradeId
    ) {


        if (harvestGradeId <= 0) {


            return 0;
        }


        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();

        long parentHarvestId = -1;
        Cursor parentCursor = null;
        try {
            parentCursor = db.rawQuery(
                    "SELECT harvest_id FROM harvest_grades WHERE id=? LIMIT 1",
                    new String[]{String.valueOf(harvestGradeId)});
            if (parentCursor.moveToFirst()) parentHarvestId = parentCursor.getLong(0);
        } finally {
            if (parentCursor != null) parentCursor.close();
        }

        int deleted = db.delete(

                "harvest_grades",

                "id = ?",

                new String[]{
                        String.valueOf(
                                harvestGradeId
                        )
                }
        );

        if (deleted > 0 && parentHarvestId > 0) {
            ContentValues touch = new ContentValues();
            touch.put("updated_at", System.currentTimeMillis());
            db.update("harvests", touch, "id=?", new String[]{String.valueOf(parentHarvestId)});
        }

        return deleted;
    }


    // ============================================================
    // HARVEST COUNT
    // ============================================================

    public int getHarvestCount() {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT COUNT(*) " +
                                    "FROM harvests",

                            null
                    );


            if (cursor.moveToFirst()) {


                return cursor.getInt(
                        0
                );
            }


        } finally {


            if (cursor != null) {


                cursor.close();
            }
        }


        return 0;
    }


    // ============================================================
    // GARDEN HARVEST COUNT
    // ============================================================

    public int getGardenHarvestCount(
            long gardenId
    ) {


        if (gardenId <= 0) {


            return 0;
        }


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT COUNT(*) " +

                                    "FROM harvests " +

                                    "WHERE garden_id = ?",

                            new String[]{
                                    String.valueOf(
                                            gardenId
                                    )
                            }
                    );


            if (cursor.moveToFirst()) {


                return cursor.getInt(
                        0
                );
            }


        } finally {


            if (cursor != null) {


                cursor.close();
            }
        }


        return 0;
    }


    // ============================================================
    // TOTAL KG BY GRADE
    // ============================================================

    public double getTotalHarvestKgByGrade(
            String grade
    ) {


        grade =
                safe(
                        grade
                );


        if (grade.isEmpty()) {


            return 0;
        }


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT COALESCE(SUM(kg), 0) " +

                                    "FROM harvest_grades " +

                                    "WHERE grade = ?",

                            new String[]{
                                    grade
                            }
                    );


            if (cursor.moveToFirst()) {


                return cursor.getDouble(
                        0
                );
            }


        } finally {


            if (cursor != null) {


                cursor.close();
            }
        }


        return 0;
    }


    // ============================================================
    // DOUBLE HELPER
    // ============================================================

    private double getDoubleValue(
            String sql
    ) {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(
                            sql,
                            null
                    );


            if (cursor.moveToFirst()) {


                return cursor.getDouble(
                        0
                );
            }


        } finally {


            if (cursor != null) {


                cursor.close();
            }
        }


        return 0;
    }


    // ============================================================
    // SAFE
    // ============================================================

    private String safe(
            String value
    ) {


        if (value == null) {


            return "";
        }


        return value.trim();
    }
}