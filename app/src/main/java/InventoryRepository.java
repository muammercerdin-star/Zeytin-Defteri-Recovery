package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;


public class InventoryRepository {


    // ============================================================
    // DATABASE
    // ============================================================

    private final DatabaseHelper databaseHelper;


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public InventoryRepository(
            DatabaseHelper databaseHelper
    ) {


        this.databaseHelper =
                databaseHelper;
    }


    // ============================================================
    // ADD INVENTORY ITEM
    // ============================================================

    public long addInventoryItem(
            String name,
            String category,
            String unit,
            double currentQty,
            double minQty,
            double unitCost,
            String note
    ) {


        name =
                safe(
                        name
                );


        if (name.isEmpty()) {


            return -1;
        }


        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "name",
                name
        );


        values.put(
                "category",
                safe(
                        category
                )
        );


        values.put(
                "unit",
                safe(
                        unit
                )
        );


        values.put(
                "current_qty",
                Math.max(
                        currentQty,
                        0
                )
        );


        values.put(
                "min_qty",
                Math.max(
                        minQty,
                        0
                )
        );


        values.put(
                "unit_cost",
                Math.max(
                        unitCost,
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
                "inventory_items",
                null,
                values
        );
    }


    // ============================================================
    // GET INVENTORY ITEMS
    // ============================================================

    public Cursor getInventoryItems() {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT * " +

                        "FROM inventory_items " +

                        "ORDER BY name COLLATE NOCASE ASC",

                null
        );
    }


    // ============================================================
    // GET INVENTORY ITEM
    // ============================================================

    public Cursor getInventoryItem(
            long itemId
    ) {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT * " +

                        "FROM inventory_items " +

                        "WHERE id = ? " +

                        "LIMIT 1",

                new String[]{
                        String.valueOf(
                                itemId
                        )
                }
        );
    }


    // ============================================================
    // UPDATE INVENTORY ITEM
    // ============================================================

    public int updateInventoryItem(
            long itemId,
            String name,
            String category,
            String unit,
            double minQty,
            double unitCost,
            String note
    ) {


        if (itemId <= 0) {


            return 0;
        }


        name =
                safe(
                        name
                );


        if (name.isEmpty()) {


            return 0;
        }


        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "name",
                name
        );


        values.put(
                "category",
                safe(
                        category
                )
        );


        values.put(
                "unit",
                safe(
                        unit
                )
        );


        values.put(
                "min_qty",
                Math.max(
                        minQty,
                        0
                )
        );


        values.put(
                "unit_cost",
                Math.max(
                        unitCost,
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

                "inventory_items",

                values,

                "id = ?",

                new String[]{
                        String.valueOf(
                                itemId
                        )
                }
        );
    }


    // ============================================================
    // ADD INVENTORY MOVEMENT
    // ============================================================

    public long addInventoryMovement(
            long itemId,
            String movementDate,
            String movementType,
            double quantity,
            double unitCost,
            Long operationId,
            String note
    ) {


        if (itemId <= 0) {


            return -1;
        }


        movementDate =
                safe(
                        movementDate
                );


        movementType =
                safe(
                        movementType
                )
                        .toUpperCase();


        if (movementDate.isEmpty()) {


            return -1;
        }


        if (!"IN".equals(
                movementType
        )
                &&
                !"OUT".equals(
                        movementType
                )) {


            return -1;
        }


        quantity =
                Math.max(
                        quantity,
                        0
                );


        if (quantity <= 0) {


            return -1;
        }


        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        long movementId =
                -1;


        db.beginTransaction();


        try {


            // ====================================================
            // STOK KONTROLÜ
            // ====================================================

            if ("OUT".equals(
                    movementType
            )) {


                double currentQty =
                        getCurrentQuantity(
                                db,
                                itemId
                        );


                if (currentQty < quantity) {


                    return -1;
                }
            }


            // ====================================================
            // HAREKET KAYDI
            // ====================================================

            ContentValues values =
                    new ContentValues();


            values.put(
                    "item_id",
                    itemId
            );


            values.put(
                    "movement_date",
                    movementDate
            );


            values.put(
                    "movement_type",
                    movementType
            );


            values.put(
                    "quantity",
                    quantity
            );


            values.put(
                    "unit_cost",
                    Math.max(
                            unitCost,
                            0
                    )
            );


            if (operationId != null
                    &&
                    operationId > 0) {


                values.put(
                        "operation_id",
                        operationId
                );
            }


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


            movementId =
                    db.insert(
                            "inventory_movements",
                            null,
                            values
                    );


            if (movementId == -1) {


                throw new RuntimeException(
                        "Stok hareketi kaydedilemedi"
                );
            }


            // ====================================================
            // STOK MİKTARINI GÜNCELLE
            // ====================================================

            double signedQuantity;


            if ("OUT".equals(
                    movementType
            )) {


                signedQuantity =
                        -quantity;


            } else {


                signedQuantity =
                        quantity;
            }


            db.execSQL(

                    "UPDATE inventory_items " +

                            "SET current_qty = current_qty + ?, " +

                            "updated_at = ? " +

                            "WHERE id = ?",

                    new Object[]{

                            signedQuantity,

                            System.currentTimeMillis(),

                            itemId
                    }
            );


            db.setTransactionSuccessful();


        } catch (Exception e) {


            e.printStackTrace();


            movementId =
                    -1;


        } finally {


            db.endTransaction();
        }


        return movementId;
    }


    // ============================================================
    // GET ITEM MOVEMENTS
    // ============================================================

    public Cursor getInventoryMovements(
            long itemId
    ) {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT " +

                        "m.*, " +

                        "i.name AS item_name " +

                        "FROM inventory_movements m " +

                        "INNER JOIN inventory_items i " +
                        "ON i.id = m.item_id " +

                        "WHERE m.item_id = ? " +

                        "ORDER BY m.created_at DESC",

                new String[]{
                        String.valueOf(
                                itemId
                        )
                }
        );
    }


    // ============================================================
    // GET ALL MOVEMENTS
    // ============================================================

    public Cursor getAllInventoryMovements(
            int limit
    ) {


        if (limit <= 0) {


            limit =
                    50;
        }


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT " +

                        "m.*, " +

                        "i.name AS item_name, " +

                        "i.unit AS item_unit " +

                        "FROM inventory_movements m " +

                        "INNER JOIN inventory_items i " +
                        "ON i.id = m.item_id " +

                        "ORDER BY m.created_at DESC " +

                        "LIMIT ?",

                new String[]{
                        String.valueOf(
                                limit
                        )
                }
        );
    }


    // ============================================================
    // LOW STOCK ITEMS
    // ============================================================

    public Cursor getLowStockItems() {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT * " +

                        "FROM inventory_items " +

                        "WHERE min_qty > 0 " +

                        "AND current_qty <= min_qty " +

                        "ORDER BY " +

                        "(current_qty - min_qty) ASC, " +

                        "name COLLATE NOCASE ASC",

                null
        );
    }


    // ============================================================
    // LOW STOCK COUNT
    // ============================================================

    public int getLowStockCount() {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT COUNT(*) " +

                                    "FROM inventory_items " +

                                    "WHERE min_qty > 0 " +

                                    "AND current_qty <= min_qty",

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
    // TOTAL INVENTORY VALUE
    // ============================================================

    public double getTotalInventoryValue() {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT COALESCE(" +

                                    "SUM(current_qty * unit_cost), " +

                                    "0" +

                                    ") " +

                                    "FROM inventory_items",

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
    // ITEM CURRENT QUANTITY
    // ============================================================

    public double getCurrentQuantity(
            long itemId
    ) {


        if (itemId <= 0) {


            return 0;
        }


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return getCurrentQuantity(
                db,
                itemId
        );
    }


    // ============================================================
    // INTERNAL CURRENT QUANTITY
    // ============================================================

    private double getCurrentQuantity(
            SQLiteDatabase db,
            long itemId
    ) {


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT current_qty " +

                                    "FROM inventory_items " +

                                    "WHERE id = ? " +

                                    "LIMIT 1",

                            new String[]{
                                    String.valueOf(
                                            itemId
                                    )
                            }
                    );


            if (cursor.moveToFirst()) {


                return Math.max(
                        cursor.getDouble(
                                0
                        ),
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
    // UPDATE CURRENT QUANTITY DIRECTLY
    // ============================================================

    public int updateCurrentQuantity(
            long itemId,
            double quantity
    ) {


        if (itemId <= 0) {


            return 0;
        }


        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "current_qty",
                Math.max(
                        quantity,
                        0
                )
        );


        values.put(
                "updated_at",
                System.currentTimeMillis()
        );


        return db.update(

                "inventory_items",

                values,

                "id = ?",

                new String[]{
                        String.valueOf(
                                itemId
                        )
                }
        );
    }


    // ============================================================
    // DELETE MOVEMENT
    // ============================================================

    public boolean deleteInventoryMovement(
            long movementId
    ) {


        if (movementId <= 0) {


            return false;
        }


        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        Cursor cursor =
                null;


        db.beginTransaction();


        try {


            long itemId =
                    -1;


            String movementType =
                    "";


            double quantity =
                    0;


            cursor =
                    db.rawQuery(

                            "SELECT " +

                                    "item_id, " +

                                    "movement_type, " +

                                    "quantity " +

                                    "FROM inventory_movements " +

                                    "WHERE id = ? " +

                                    "LIMIT 1",

                            new String[]{
                                    String.valueOf(
                                            movementId
                                    )
                            }
                    );


            if (!cursor.moveToFirst()) {


                return false;
            }


            itemId =
                    cursor.getLong(
                            0
                    );


            movementType =
                    safe(
                            cursor.getString(
                                    1
                            )
                    )
                            .toUpperCase();


            quantity =
                    Math.max(
                            cursor.getDouble(
                                    2
                            ),
                            0
                    );


            cursor.close();


            cursor =
                    null;


            int deleted =
                    db.delete(

                            "inventory_movements",

                            "id = ?",

                            new String[]{
                                    String.valueOf(
                                            movementId
                                    )
                            }
                    );


            if (deleted <= 0) {


                return false;
            }


            /*
             * Hareketi siliyorsak stok etkisini de geri alıyoruz.
             *
             * IN 10 silinirse:
             * mevcut stoktan 10 çıkar.
             *
             * OUT 10 silinirse:
             * mevcut stoğa 10 geri ekle.
             */
            double reversal;


            if ("OUT".equals(
                    movementType
            )) {


                reversal =
                        quantity;


            } else {


                reversal =
                        -quantity;
            }


            db.execSQL(

                    "UPDATE inventory_items " +

                            "SET current_qty = MAX(" +

                            "current_qty + ?, " +

                            "0" +

                            "), " +

                            "updated_at = ? " +

                            "WHERE id = ?",

                    new Object[]{

                            reversal,

                            System.currentTimeMillis(),

                            itemId
                    }
            );


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
    // DELETE INVENTORY ITEM
    // ============================================================

    public boolean deleteInventoryItem(
            long itemId
    ) {


        if (itemId <= 0) {


            return false;
        }


        databaseHelper.recordCloudDeletion("inventory_items", itemId);

        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        int deleted =
                db.delete(

                        "inventory_items",

                        "id = ?",

                        new String[]{
                                String.valueOf(
                                        itemId
                                )
                        }
                );


        return deleted > 0;
    }


    // ============================================================
    // SEARCH INVENTORY
    // ============================================================

    public Cursor searchInventoryItems(
            String query
    ) {


        query =
                safe(
                        query
                );


        if (query.isEmpty()) {


            return getInventoryItems();
        }


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        String pattern =
                "%"
                        + query
                        + "%";


        return db.rawQuery(

                "SELECT * " +

                        "FROM inventory_items " +

                        "WHERE name LIKE ? " +

                        "OR category LIKE ? " +

                        "OR note LIKE ? " +

                        "ORDER BY name COLLATE NOCASE ASC",

                new String[]{

                        pattern,

                        pattern,

                        pattern
                }
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