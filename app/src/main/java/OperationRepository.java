package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;


public class OperationRepository {


    // ============================================================
    // DATABASE
    // ============================================================

    private final DatabaseHelper databaseHelper;


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public OperationRepository(
            DatabaseHelper databaseHelper
    ) {


        this.databaseHelper =
                databaseHelper;
    }


    // ============================================================
    // ADD OPERATION
    // ============================================================

    public long addOperation(
            int gardenId,
            String type,
            String date,
            double quantity,
            String unit,
            double cost,
            String note
    ) {


        if (gardenId <= 0) {


            return -1;
        }


        type =
                safe(
                        type
                );


        date =
                safe(
                        date
                );


        if (type.isEmpty()
                ||
                date.isEmpty()) {


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
                "type",
                type
        );


        values.put(
                "operation_date",
                date
        );


        values.put(
                "quantity",
                Math.max(
                        quantity,
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


        return db.insert(
                "operations",
                null,
                values
        );
    }


    // ============================================================
    // GET LATEST OPERATIONS
    // ============================================================

    public Cursor getLatestOperations(
            int limit
    ) {


        if (limit <= 0) {


            limit =
                    10;
        }


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT " +

                        "o.id," +

                        "o.garden_id," +

                        "o.type," +

                        "o.operation_date," +

                        "o.quantity," +

                        "o.unit," +

                        "o.cost," +

                        "o.note," +

                        "o.season_year," +

                        "o.created_at," +

                        "o.updated_at," +

                        "g.name AS garden_name " +

                        "FROM operations o " +

                        "INNER JOIN gardens g " +
                        "ON g.id = o.garden_id " +

                        "ORDER BY o.created_at DESC " +

                        "LIMIT ?",

                new String[]{
                        String.valueOf(
                                limit
                        )
                }
        );
    }


    // ============================================================
    // GARDEN OPERATIONS
    // ============================================================

    public Cursor getGardenOperations(
            long gardenId
    ) {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT * " +

                        "FROM operations " +

                        "WHERE garden_id = ? " +

                        "ORDER BY created_at DESC",

                new String[]{
                        String.valueOf(
                                gardenId
                        )
                }
        );
    }


    // ============================================================
    // GET OPERATION
    // ============================================================

    public Cursor getOperation(
            long operationId
    ) {


        if (operationId <= 0) {


            return emptyOperationCursor();
        }


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT " +

                        "o.*," +

                        "g.name AS garden_name " +

                        "FROM operations o " +

                        "INNER JOIN gardens g " +
                        "ON g.id = o.garden_id " +

                        "WHERE o.id = ?",

                new String[]{
                        String.valueOf(
                                operationId
                        )
                }
        );
    }


    // ============================================================
    // FILTERED OPERATIONS
    // ============================================================

    public Cursor getOperationsFiltered(
            Integer gardenId,
            String type
    ) {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        String sql =
                "SELECT " +

                        "o.id," +

                        "o.garden_id," +

                        "o.type," +

                        "o.operation_date," +

                        "o.quantity," +

                        "o.unit," +

                        "o.cost," +

                        "o.note," +

                        "o.season_year," +

                        "o.created_at," +

                        "o.updated_at," +

                        "g.name AS garden_name " +

                        "FROM operations o " +

                        "INNER JOIN gardens g " +
                        "ON g.id = o.garden_id " +

                        "WHERE 1 = 1";


        java.util.ArrayList<String> args =
                new java.util.ArrayList<>();


        if (gardenId != null
                &&
                gardenId > 0) {


            sql +=
                    " AND o.garden_id = ?";


            args.add(
                    String.valueOf(
                            gardenId
                    )
            );
        }


        type =
                safe(
                        type
                );


        if (!type.isEmpty()) {


            sql +=
                    " AND o.type = ?";


            args.add(
                    type
            );
        }


        sql +=
                " ORDER BY o.created_at DESC";


        return db.rawQuery(
                sql,
                args.toArray(
                        new String[0]
                )
        );
    }


    // ============================================================
    // TOTAL OPERATION COST
    // ============================================================

    public double getOperationCostTotal() {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT COALESCE(SUM(cost), 0) " +

                                    "FROM operations " +

                                    "WHERE type <> 'Hasat'",

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
    // UPDATE OPERATION
    // ============================================================

    public int updateOperation(
            long operationId,
            int gardenId,
            String type,
            String date,
            double quantity,
            String unit,
            double cost,
            String note
    ) {


        if (operationId <= 0
                ||
                gardenId <= 0) {


            return 0;
        }


        type =
                safe(
                        type
                );


        date =
                safe(
                        date
                );


        if (type.isEmpty()
                ||
                date.isEmpty()) {


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
                "type",
                type
        );


        values.put(
                "operation_date",
                date
        );


        values.put(
                "quantity",
                Math.max(
                        quantity,
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
                "updated_at",
                System.currentTimeMillis()
        );


        return db.update(

                "operations",

                values,

                "id = ?",

                new String[]{
                        String.valueOf(
                                operationId
                        )
                }
        );
    }


    // ============================================================
    // DELETE OPERATION
    // ============================================================

    public int deleteOperation(
            long operationId
    ) {


        if (operationId <= 0) {


            return 0;
        }


        databaseHelper.recordCloudDeletion("operations", operationId);

        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        return db.delete(

                "operations",

                "id = ?",

                new String[]{
                        String.valueOf(
                                operationId
                        )
                }
        );
    }


    // ============================================================
    // COUNT
    // ============================================================

    public int getOperationCount() {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT COUNT(*) " +
                                    "FROM operations",

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
    // GARDEN OPERATION COUNT
    // ============================================================

    public int getGardenOperationCount(
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

                                    "FROM operations " +

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
    // LAST OPERATION TYPE
    // ============================================================

    public String getLastOperationType(
            long gardenId
    ) {


        if (gardenId <= 0) {


            return "";
        }


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT type " +

                                    "FROM operations " +

                                    "WHERE garden_id = ? " +

                                    "ORDER BY created_at DESC " +

                                    "LIMIT 1",

                            new String[]{
                                    String.valueOf(
                                            gardenId
                                    )
                            }
                    );


            if (cursor.moveToFirst()) {


                String result =
                        cursor.getString(
                                0
                        );


                return safe(
                        result
                );
            }


        } finally {


            if (cursor != null) {


                cursor.close();
            }
        }


        return "";
    }


    // ============================================================
    // EMPTY CURSOR
    // ============================================================

    private Cursor emptyOperationCursor() {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT * " +
                        "FROM operations " +
                        "WHERE 1 = 0",

                null
        );
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
}