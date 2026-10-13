package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.Calendar;
import java.util.UUID;


public class BrineRepository {


    // ============================================================
    // DATABASE
    // ============================================================

    private final DatabaseHelper databaseHelper;


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public BrineRepository(
            DatabaseHelper databaseHelper
    ) {


        this.databaseHelper =
                databaseHelper;
    }


    // ============================================================
    // ADD BRINE BATCH
    // ============================================================

    public long addBrineBatch(
            Integer gardenId,
            String batchName,
            String startDate,
            String expectedReadyDate,
            String variety,
            String grade,
            double containerLiter,
            double oliveKg,
            double saltKg,
            double waterLiter,
            double saltCost,
            double packagingCost,
            double otherCost,
            String note
    ) {


        if (safe(
                batchName
        ).isEmpty()) {


            return -1;
        }


        if (safe(
                startDate
        ).isEmpty()) {


            return -1;
        }


        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        ContentValues values =
                new ContentValues();


        if (gardenId != null
                &&
                gardenId > 0) {


            values.put(
                    "garden_id",
                    gardenId
            );
        }


        values.put(
                "batch_name",
                safe(
                        batchName
                )
        );


        values.put(
                "start_date",
                safe(
                        startDate
                )
        );


        values.put(
                "expected_ready_date",
                safe(
                        expectedReadyDate
                )
        );


        values.put(
                "variety",
                safe(
                        variety
                )
        );


        values.put(
                "grade",
                safe(
                        grade
                )
        );


        values.put(
                "container_liter",
                Math.max(
                        containerLiter,
                        0
                )
        );


        values.put(
                "olive_kg",
                Math.max(
                        oliveKg,
                        0
                )
        );


        values.put(
                "salt_kg",
                Math.max(
                        saltKg,
                        0
                )
        );


        values.put(
                "water_liter",
                Math.max(
                        waterLiter,
                        0
                )
        );


        values.put(
                "salt_cost",
                Math.max(
                        saltCost,
                        0
                )
        );


        values.put(
                "packaging_cost",
                Math.max(
                        packagingCost,
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
                "status",
                "ACTIVE"
        );


        values.put(
                "ready_kg",
                0
        );


        values.put(
                "note",
                safe(
                        note
                )
        );


        long now =
                System.currentTimeMillis();


        values.put(
                "created_at",
                now
        );


        values.put(
                "updated_at",
                now
        );

        String ownerUid = databaseHelper.getActiveOwnerUid();
        values.put("season_year", yearOf(startDate));
        values.put("owner_uid", ownerUid);
        values.put("cloud_id", ownerUid.isEmpty() ? "" : UUID.randomUUID().toString());


        return db.insert(
                "brine_batches",
                null,
                values
        );
    }


    // ============================================================
    // SHORT ADD BRINE BATCH
    // ============================================================

    public long addBrineBatch(
            Integer gardenId,
            String batchName,
            String startDate,
            String variety,
            String grade,
            double containerLiter,
            double oliveKg,
            double saltKg,
            double waterLiter,
            double otherCost,
            String note
    ) {


        return addBrineBatch(
                gardenId,
                batchName,
                startDate,
                "",
                variety,
                grade,
                containerLiter,
                oliveKg,
                saltKg,
                waterLiter,
                0,
                0,
                otherCost,
                note
        );
    }


    // ============================================================
    // ADD BRINE EVENT
    // ============================================================

    public long addBrineEvent(
            long batchId,
            String eventDate,
            String eventType,
            double amount,
            String unit,
            double cost,
            String note
    ) {


        if (batchId <= 0) {


            return -1;
        }


        if (safe(
                eventDate
        ).isEmpty()) {


            return -1;
        }


        if (safe(
                eventType
        ).isEmpty()) {


            return -1;
        }


        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "batch_id",
                batchId
        );


        values.put(
                "event_date",
                safe(
                        eventDate
                )
        );


        values.put(
                "event_type",
                safe(
                        eventType
                )
        );


        values.put(
                "amount",
                Math.max(
                        amount,
                        0
                )
        );


        values.put(
                "unit",
                safe(
                        unit
                )
        );


        values.put(
                "cost",
                Math.max(
                        cost,
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


        long result =
                db.insert(
                        "brine_events",
                        null,
                        values
                );


        if (result != -1) {


            touchBatch(
                    db,
                    batchId
            );
        }


        return result;
    }


    // ============================================================
    // GET ALL BATCHES
    // ============================================================

    public Cursor getBrineBatches() {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT " +

                        "b.*, " +

                        "g.name AS garden_name, " +

                        "(" +

                        "SELECT MAX(e.event_date) " +

                        "FROM brine_events e " +

                        "WHERE e.batch_id = b.id" +

                        ") AS last_event_date, " +

                        "(" +

                        "SELECT COALESCE(SUM(e.cost), 0) " +

                        "FROM brine_events e " +

                        "WHERE e.batch_id = b.id" +

                        ") AS event_cost " +

                        "FROM brine_batches b " +

                        "LEFT JOIN gardens g " +
                        "ON g.id = b.garden_id " +

                        "ORDER BY " +

                        "CASE b.status " +

                        "WHEN 'ACTIVE' THEN 0 " +

                        "WHEN 'READY' THEN 1 " +

                        "WHEN 'CLOSED' THEN 2 " +

                        "ELSE 3 END, " +

                        "b.created_at DESC",

                null
        );
    }


    // ============================================================
    // GET SINGLE BATCH
    // ============================================================

    public Cursor getBrineBatch(
            long batchId
    ) {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT " +

                        "b.*, " +

                        "g.name AS garden_name " +

                        "FROM brine_batches b " +

                        "LEFT JOIN gardens g " +
                        "ON g.id = b.garden_id " +

                        "WHERE b.id = ?",

                new String[]{
                        String.valueOf(
                                batchId
                        )
                }
        );
    }


    // ============================================================
    // GET EVENTS
    // ============================================================

    public Cursor getBrineEvents(
            long batchId
    ) {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT * " +

                        "FROM brine_events " +

                        "WHERE batch_id = ? " +

                        "ORDER BY created_at DESC",

                new String[]{
                        String.valueOf(
                                batchId
                        )
                }
        );
    }


    // ============================================================
    // UPDATE STATUS
    // ============================================================

    public int updateBrineStatus(
            long batchId,
            String status
    ) {


        if (batchId <= 0) {


            return 0;
        }


        status =
                safe(
                        status
                )
                        .toUpperCase();


        if (!"ACTIVE".equals(
                status
        )
                &&
                !"READY".equals(
                        status
                )
                &&
                !"CLOSED".equals(
                        status
                )) {


            return 0;
        }


        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "status",
                status
        );


        values.put(
                "updated_at",
                System.currentTimeMillis()
        );


        return db.update(

                "brine_batches",

                values,

                "id = ?",

                new String[]{
                        String.valueOf(
                                batchId
                        )
                }
        );
    }


    // ============================================================
    // UPDATE READY KG
    // ============================================================

    public int updateBrineReadyKg(
            long batchId,
            double readyKg
    ) {


        if (batchId <= 0) {


            return 0;
        }


        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "ready_kg",
                Math.max(
                        readyKg,
                        0
                )
        );


        values.put(
                "updated_at",
                System.currentTimeMillis()
        );


        return db.update(

                "brine_batches",

                values,

                "id = ?",

                new String[]{
                        String.valueOf(
                                batchId
                        )
                }
        );
    }


    // ============================================================
    // BATCH TOTAL COST
    // ============================================================

    public double getBrineBatchTotalCost(
            long batchId
    ) {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT " +

                                    "COALESCE(b.salt_cost, 0) + " +

                                    "COALESCE(b.packaging_cost, 0) + " +

                                    "COALESCE(b.other_cost, 0) + " +

                                    "COALESCE((" +

                                    "SELECT SUM(e.cost) " +

                                    "FROM brine_events e " +

                                    "WHERE e.batch_id = b.id" +

                                    "), 0) AS total_cost " +

                                    "FROM brine_batches b " +

                                    "WHERE b.id = ?",

                            new String[]{
                                    String.valueOf(
                                            batchId
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
    // TOTAL BRINE COST
    // ============================================================

    public double getTotalBrineCost() {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT " +

                                    "COALESCE((" +

                                    "SELECT SUM(" +

                                    "COALESCE(salt_cost, 0) + " +

                                    "COALESCE(packaging_cost, 0) + " +

                                    "COALESCE(other_cost, 0)" +

                                    ") " +

                                    "FROM brine_batches" +

                                    "), 0) + " +

                                    "COALESCE((" +

                                    "SELECT SUM(cost) " +

                                    "FROM brine_events" +

                                    "), 0)",

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
    // TOTAL OLIVE KG
    // ============================================================

    public double getTotalBrineOliveKg() {


        return getDoubleValue(

                "SELECT COALESCE(" +
                        "SUM(olive_kg), 0" +
                        ") " +
                        "FROM brine_batches"
        );
    }


    // ============================================================
    // TOTAL READY KG
    // ============================================================

    public double getTotalReadyBrineKg() {


        return getDoubleValue(

                "SELECT COALESCE(" +
                        "SUM(ready_kg), 0" +
                        ") " +
                        "FROM brine_batches"
        );
    }


    // ============================================================
    // ACTIVE BATCH COUNT
    // ============================================================

    public int getActiveBrineBatchCount() {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT COUNT(*) " +

                                    "FROM brine_batches " +

                                    "WHERE status = 'ACTIVE'",

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
    // READY BATCH COUNT
    // ============================================================

    public int getReadyBrineBatchCount() {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT COUNT(*) " +

                                    "FROM brine_batches " +

                                    "WHERE status = 'READY'",

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
    // GARDEN BATCHES
    // ============================================================

    public Cursor getGardenBrineBatches(
            long gardenId
    ) {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT " +

                        "b.*, " +

                        "g.name AS garden_name " +

                        "FROM brine_batches b " +

                        "LEFT JOIN gardens g " +
                        "ON g.id = b.garden_id " +

                        "WHERE b.garden_id = ? " +

                        "ORDER BY b.created_at DESC",

                new String[]{
                        String.valueOf(
                                gardenId
                        )
                }
        );
    }


    // ============================================================
    // DELETE EVENT
    // ============================================================

    public boolean deleteBrineEvent(
            long eventId
    ) {


        if (eventId <= 0) {


            return false;
        }


        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        Cursor cursor =
                null;


        db.beginTransaction();


        try {


            long batchId =
                    -1;


            cursor =
                    db.rawQuery(

                            "SELECT batch_id " +

                                    "FROM brine_events " +

                                    "WHERE id = ? " +

                                    "LIMIT 1",

                            new String[]{
                                    String.valueOf(
                                            eventId
                                    )
                            }
                    );


            if (cursor.moveToFirst()) {


                batchId =
                        cursor.getLong(
                                0
                        );
            }


            cursor.close();


            cursor =
                    null;


            int deleted =
                    db.delete(

                            "brine_events",

                            "id = ?",

                            new String[]{
                                    String.valueOf(
                                            eventId
                                    )
                            }
                    );


            if (deleted <= 0) {


                return false;
            }


            if (batchId > 0) {


                touchBatch(
                        db,
                        batchId
                );
            }


            db.setTransactionSuccessful();


            return true;


        } catch (Exception e) {


            e.printStackTrace();


            return false;


        } finally {


            if (cursor != null) {


                cursor.close();
            }


            db.endTransaction();
        }
    }


    // ============================================================
    // DELETE BATCH
    // ============================================================

    public boolean deleteBrineBatch(
            long batchId
    ) {


        if (batchId <= 0) {


            return false;
        }


        databaseHelper.recordCloudDeletion("brine_batches", batchId);

        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        int deleted =
                db.delete(

                        "brine_batches",

                        "id = ?",

                        new String[]{
                                String.valueOf(
                                        batchId
                                )
                        }
                );


        return deleted > 0;
    }


    // ============================================================
    // TOUCH BATCH
    // ============================================================

    private void touchBatch(
            SQLiteDatabase db,
            long batchId
    ) {


        ContentValues values =
                new ContentValues();


        values.put(
                "updated_at",
                System.currentTimeMillis()
        );


        db.update(

                "brine_batches",

                values,

                "id = ?",

                new String[]{
                        String.valueOf(
                                batchId
                        )
                }
        );
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
    // SAFE STRING
    // ============================================================

    private String safe(
            String value
    ) {


        if (value == null) {


            return "";
        }


        return value.trim();
    }


    private int yearOf(String value) {
        String date = safe(value);
        if (date.length() >= 4) {
            try {
                if (date.length() >= 10 && date.charAt(4) == '-') {
                    return Integer.parseInt(date.substring(0, 4));
                }
                return Integer.parseInt(date.substring(date.length() - 4));
            } catch (Exception ignored) { }
        }
        return Calendar.getInstance().get(Calendar.YEAR);
    }
}
