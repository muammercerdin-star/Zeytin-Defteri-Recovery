package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;


public class GardenRepository {


    // ============================================================
    // DATABASE
    // ============================================================

    private final DatabaseHelper databaseHelper;


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public GardenRepository(
            DatabaseHelper databaseHelper
    ) {


        this.databaseHelper =
                databaseHelper;
    }


    // ============================================================
    // ADD GARDEN
    // ============================================================

    public long addGarden(
            String name,
            int treeCount,
            int age,
            String variety,
            double areaDecare,
            String province,
            String district,
            String neighborhood,
            String irrigationType,
            String notes,
            String photoUri
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
                "tree_count",
                Math.max(
                        treeCount,
                        0
                )
        );


        values.put(
                "age",
                Math.max(
                        age,
                        0
                )
        );


        values.put(
                "variety",
                safe(
                        variety
                )
        );


        values.put(
                "area_decare",
                Math.max(
                        areaDecare,
                        0
                )
        );


        values.put(
                "province",
                safe(
                        province
                )
        );


        values.put(
                "district",
                safe(
                        district
                )
        );


        values.put(
                "neighborhood",
                safe(
                        neighborhood
                )
        );


        values.put(
                "irrigation_type",
                safe(
                        irrigationType
                )
        );


        values.put(
                "notes",
                safe(
                        notes
                )
        );


        values.put(
                "photo_uri",
                safe(
                        photoUri
                )
        );


        values.put(
                "created_at",
                System.currentTimeMillis()
        );


        return db.insert(
                "gardens",
                null,
                values
        );
    }


    // ============================================================
    // ADD GARDEN - KISA
    // ============================================================

    public long addGarden(
            String name,
            int treeCount,
            int age,
            String variety
    ) {


        return addGarden(
                name,
                treeCount,
                age,
                variety,
                0,
                "",
                "",
                "",
                "",
                "",
                ""
        );
    }


    // ============================================================
    // GET ALL GARDENS
    // ============================================================

    public Cursor getGardens() {

        return databaseHelper.getGardens();
    }


    // ============================================================
    // GET GARDEN
    // ============================================================

    public Cursor getGarden(
            long gardenId
    ) {


        if (gardenId <= 0) {


            return emptyGardenCursor();
        }


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT * " +
                        "FROM gardens " +
                        "WHERE id = ?",

                new String[]{
                        String.valueOf(
                                gardenId
                        )
                }
        );
    }


    // ============================================================
    // GET GARDEN BY ID
    // ============================================================

    public Cursor getGardenById(
            long gardenId
    ) {


        return getGarden(
                gardenId
        );
    }


    // ============================================================
    // GARDEN COUNT
    // ============================================================

    public int getGardenCount() {

        return databaseHelper.getGardenCount();
    }


    // ============================================================
    // GARDEN SUMMARIES
    // ============================================================

    public Cursor getGardenSummaries() {

        return databaseHelper.getGardenSummaries();
    }


    // ============================================================
    // HESAP + BULUT
    // ============================================================

    public long addGardenForOwner(
            String ownerUid,
            String cloudId,
            String name,
            int treeCount,
            int age,
            String variety,
            double areaDecare,
            String province,
            String district,
            String neighborhood,
            String irrigationType,
            String notes,
            String photoUri
    ) {

        return databaseHelper.addGardenForOwner(
                ownerUid,
                cloudId,
                name,
                treeCount,
                age,
                variety,
                areaDecare,
                province,
                district,
                neighborhood,
                irrigationType,
                notes,
                photoUri
        );
    }


    public int getGardenCountForOwner(String ownerUid) {
        return databaseHelper.getGardenCountForOwner(ownerUid);
    }


    public int getUnownedGardenCount() {
        return databaseHelper.getUnownedGardenCount();
    }


    public int claimUnownedGardens(String ownerUid) {
        return databaseHelper.claimUnownedGardens(ownerUid);
    }


    public Cursor getGardensForOwner(String ownerUid) {
        return databaseHelper.getGardensForOwner(ownerUid);
    }


    public Cursor getGardenByCloudId(String ownerUid, String cloudId) {
        return databaseHelper.getGardenByCloudId(ownerUid, cloudId);
    }


    public long upsertGardenFromCloud(
            String ownerUid,
            String cloudId,
            String name,
            int treeCount,
            int age,
            String variety,
            double areaDecare,
            String province,
            String district,
            String neighborhood,
            String irrigationType,
            String notes,
            String photoUri,
            long createdAt,
            long updatedAt
    ) {

        return databaseHelper.upsertGardenFromCloud(
                ownerUid,
                cloudId,
                name,
                treeCount,
                age,
                variety,
                areaDecare,
                province,
                district,
                neighborhood,
                irrigationType,
                notes,
                photoUri,
                createdAt,
                updatedAt
        );
    }


    // ============================================================
    // UPDATE GARDEN
    // ============================================================

    public int updateGarden(long gardenId, String name, int treeCount, int age,
            String variety, double areaDecare, String province, String district,
            String neighborhood, String irrigationType, String notes, String photoUri) {
        return databaseHelper.updateGarden(gardenId, name, treeCount, age, variety,
                areaDecare, province, district, neighborhood, irrigationType, notes, photoUri) ? 1 : 0;
    }


    // ============================================================
    // DELETE GARDEN
    // ============================================================

    public int deleteGarden(
            long gardenId
    ) {


        if (gardenId <= 0) {


            return 0;
        }


        databaseHelper.recordCloudDeletion("gardens", gardenId);

        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        return db.delete(

                "gardens",

                "id = ?",

                new String[]{
                        String.valueOf(
                                gardenId
                        )
                }
        );
    }


    // ============================================================
    // EMPTY CURSOR
    // ============================================================

    private Cursor emptyGardenCursor() {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT * " +
                        "FROM gardens " +
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