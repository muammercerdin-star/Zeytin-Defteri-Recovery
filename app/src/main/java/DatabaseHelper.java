package com.zeytindefteri.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.UUID;


public class DatabaseHelper extends SQLiteOpenHelper {


    private final Context appContext;


    // ============================================================
    // DATABASE
    // ============================================================

    private static final String DB_NAME =
            "zeytin_defteri.db";


    /*
     * 16'dan 17'ye çıkarıldı.
     *
     * Sebep:
     * Meydan yanıtları (post_comments) tablosuna Firestore
     * senkronizasyonu için cloud_id desteği eklendi.
     */
    private static final int DB_VERSION =
            20; // 20: satışın hangi elekten oluştuğu (sales.source_elek_id / source_elek_cloud_id)


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public DatabaseHelper(
            Context context
    ) {

        super(
                context,
                DB_NAME,
                null,
                DB_VERSION
        );

        appContext =
                context.getApplicationContext();
    }


    public String getActiveOwnerUid() {

        SessionManager sessionManager =
                new SessionManager(
                        appContext
                );

        return safe(
                sessionManager.getUserUid()
        );
    }


    public int getActiveSeasonYear() {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        String ownerKey = getActiveOwnerUid().isEmpty() ? "guest" : getActiveOwnerUid();
        String stored = getSetting("active_season_year:" + ownerKey, String.valueOf(currentYear));
        try {
            int year = Integer.parseInt(stored);
            return year >= 2000 && year <= currentYear + 2 ? year : currentYear;
        } catch (Exception ignored) {
            return currentYear;
        }
    }


    public void setActiveSeasonYear(int year) {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        if (year < 2000 || year > currentYear + 2) return;
        String ownerKey = getActiveOwnerUid().isEmpty() ? "guest" : getActiveOwnerUid();
        setSetting("active_season_year:" + ownerKey, String.valueOf(year));
    }


    // ============================================================
    // FOREIGN KEY
    // ============================================================

    @Override
    public void onConfigure(
            SQLiteDatabase db
    ) {

        super.onConfigure(
                db
        );


        db.setForeignKeyConstraintsEnabled(
                true
        );
    }


    // ============================================================
    // DATABASE CREATE
    // ============================================================

    @Override
    public void onCreate(
            SQLiteDatabase db
    ) {


        DatabaseSchema.createGardensTable(
                db
        );


        DatabaseSchema.createOperationsTable(
                db
        );


        DatabaseSchema.createHarvestTables(
                db
        );


        DatabaseSchema.createElekTables(
                db
        );


        DatabaseSchema.createBuyerAndSalesTables(
                db
        );


        DatabaseSchema.createBrineTables(
                db
        );


        DatabaseSchema.createInventoryTables(
                db
        );


        DatabaseSchema.createReceivablesTable(
                db
        );


        DatabaseSchema.createRemindersTable(
                db
        );


        DatabaseSchema.createGardenNotesTable(
                db
        );


        DatabaseSchema.createSettingsTable(
                db
        );


        DatabaseSchema.createSyncTables(
                db
        );


        DatabaseSchema.createSocialTables(
                db
        );


        DatabaseSchema.createSocialInteractionTables(
                db
        );


        DatabaseSchema.createIndexes(
                db
        );
        ensureCommentSyncSchema(db);
        ensureElekSaleLinkSchema(db);
    }


    // ============================================================
    // DATABASE UPGRADE
    // ============================================================

    @Override
    public void onUpgrade(
            SQLiteDatabase db,
            int oldVersion,
            int newVersion
    ) {


        // ========================================================
        // VERSION 1 -> 2
        // ========================================================

        if (oldVersion < 2) {


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "gardens",
                    "area_decare",
                    "REAL DEFAULT 0"
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "gardens",
                    "province",
                    "TEXT"
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "gardens",
                    "district",
                    "TEXT"
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "gardens",
                    "neighborhood",
                    "TEXT"
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "gardens",
                    "irrigation_type",
                    "TEXT"
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "gardens",
                    "notes",
                    "TEXT"
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "gardens",
                    "photo_uri",
                    "TEXT"
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "gardens",
                    "created_at",
                    "INTEGER DEFAULT 0"
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "operations",
                    "season_year",
                    "INTEGER DEFAULT 0"
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "operations",
                    "updated_at",
                    "INTEGER DEFAULT 0"
            );


            DatabaseSchema.createHarvestTables(
                    db
            );


            DatabaseSchema.createBuyerAndSalesTables(
                    db
            );


            DatabaseSchema.createBrineTables(
                    db
            );


            DatabaseSchema.createInventoryTables(
                    db
            );


            DatabaseSchema.createReceivablesTable(
                    db
            );


            DatabaseSchema.createRemindersTable(
                    db
            );


            DatabaseSchema.createGardenNotesTable(
                    db
            );


            DatabaseSchema.createSettingsTable(
                    db
            );
        }


        // ========================================================
        // VERSION 2 -> 3
        // ========================================================

        if (oldVersion < 3) {


            DatabaseSchema.createHarvestTables(
                    db
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "harvest_grades",
                    "reference_price_per_kg",
                    "REAL DEFAULT 0"
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "harvest_grades",
                    "reference_price_date",
                    "TEXT"
            );
        }


        // ========================================================
        // VERSION 3 -> 4
        // ========================================================

        if (oldVersion < 4) {


            DatabaseSchema.createBrineTables(
                    db
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "brine_batches",
                    "expected_ready_date",
                    "TEXT"
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "brine_batches",
                    "salt_cost",
                    "REAL DEFAULT 0"
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "brine_batches",
                    "packaging_cost",
                    "REAL DEFAULT 0"
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "brine_batches",
                    "updated_at",
                    "INTEGER DEFAULT 0"
            );
        }


        // ========================================================
        // VERSION 4 -> 5
        // ========================================================

        if (oldVersion < 5) {


            DatabaseSchema.createElekTables(
                    db
            );
        }


        // ========================================================
        // VERSION 5 -> 6
        // ========================================================

        if (oldVersion < 6) {


            DatabaseSchema.createSocialTables(
                    db
            );
        }


        // ========================================================
        // VERSION 6 -> 7
        // ========================================================

        if (oldVersion < 7) {


            DatabaseSchema.createSocialTables(
                    db
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "posts",
                    "not_useful_count",
                    "INTEGER DEFAULT 0"
            );


            DatabaseSchema.createSocialInteractionTables(
                    db
            );
        }


        // ========================================================
        // VERSION 7 -> 8
        // SOSYAL ŞEMA ONARIMI
        // ========================================================

        if (oldVersion < 8) {


            DatabaseSchema.createSocialTables(
                    db
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "posts",
                    "useful_count",
                    "INTEGER DEFAULT 0"
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "posts",
                    "not_useful_count",
                    "INTEGER DEFAULT 0"
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "posts",
                    "comment_count",
                    "INTEGER DEFAULT 0"
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "posts",
                    "photo_uri",
                    "TEXT"
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "posts",
                    "is_real_sale",
                    "INTEGER DEFAULT 0"
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "posts",
                    "sale_id",
                    "INTEGER"
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "posts",
                    "sale_grade",
                    "TEXT"
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "posts",
                    "sale_kg",
                    "REAL DEFAULT 0"
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "posts",
                    "sale_price",
                    "REAL DEFAULT 0"
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "posts",
                    "is_active",
                    "INTEGER DEFAULT 1"
            );


            DatabaseSchema.addColumnIfMissing(
                    db,
                    "posts",
                    "updated_at",
                    "INTEGER DEFAULT 0"
            );


            DatabaseSchema.createSocialInteractionTables(
                    db
            );
        }


        // ========================================================
        // VERSION 8 -> 9
        // YENİ TEPKİ SİSTEMİ
        // ========================================================

        if (oldVersion < 9) {


            DatabaseSchema.createSocialTables(
                    db
            );


            DatabaseSchema.createSocialInteractionTables(
                    db
            );


            /*
             * Eski "Faydalı" kayıtları yeni LIKE sistemine geçir.
             */
            db.execSQL(
                    "UPDATE post_reactions " +
                            "SET reaction_type = 'LIKE' " +
                            "WHERE reaction_type = 'USEFUL'"
            );


            /*
             * Eski "Faydalı değil" mantığını kaldırıyoruz.
             */
            db.execSQL(
                    "DELETE FROM post_reactions " +
                            "WHERE reaction_type = 'NOT_USEFUL'"
            );


            /*
             * Eski sayaç artık kullanılmıyor.
             */
            db.execSQL(
                    "UPDATE posts " +
                            "SET not_useful_count = 0"
            );


            /*
             * LIKE sayısını useful_count ile senkronla.
             * Eski ekranlardan biri hâlâ useful_count okursa bozulmasın.
             */
            db.execSQL(
                    "UPDATE posts " +
                            "SET useful_count = (" +
                            "SELECT COUNT(*) " +
                            "FROM post_reactions pr " +
                            "WHERE pr.post_id = posts.id " +
                            "AND pr.reaction_type = 'LIKE'" +
                            ")"
            );
        }


        // ========================================================
        // VERSION 9 -> 10
        // HESAP SAHİPLİĞİ + BULUT KİMLİĞİ
        // ========================================================

        if (oldVersion < 10) {

            DatabaseSchema.addColumnIfMissing(
                    db,
                    "gardens",
                    "owner_uid",
                    "TEXT DEFAULT ''"
            );

            DatabaseSchema.addColumnIfMissing(
                    db,
                    "gardens",
                    "cloud_id",
                    "TEXT DEFAULT ''"
            );

            DatabaseSchema.addColumnIfMissing(
                    db,
                    "gardens",
                    "updated_at",
                    "INTEGER DEFAULT 0"
            );

            db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_gardens_owner_uid " +
                            "ON gardens(owner_uid)"
            );

            db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_gardens_cloud_id " +
                            "ON gardens(cloud_id) " +
                            "WHERE cloud_id IS NOT NULL AND cloud_id <> ''"
            );
        }


        // ========================================================
        // VERSION 10 -> 11
        // İŞLEM SAHİPLİĞİ + BULUT KİMLİĞİ
        // ========================================================

        if (oldVersion < 11) {

            DatabaseSchema.addColumnIfMissing(
                    db,
                    "operations",
                    "owner_uid",
                    "TEXT DEFAULT ''"
            );

            DatabaseSchema.addColumnIfMissing(
                    db,
                    "operations",
                    "cloud_id",
                    "TEXT DEFAULT ''"
            );

            db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_operations_owner_uid " +
                            "ON operations(owner_uid)"
            );

            db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_operations_cloud_id " +
                            "ON operations(cloud_id) " +
                            "WHERE cloud_id IS NOT NULL AND cloud_id <> ''"
            );
        }


        // ========================================================
        // VERSION 11 -> 12
        // HASAT + ELEK + SATIŞ + SALAMURA BULUT KİMLİĞİ
        // ========================================================

        if (oldVersion < 12) {

            DatabaseSchema.addColumnIfMissing(db, "harvests", "updated_at", "INTEGER DEFAULT 0");
            DatabaseSchema.addColumnIfMissing(db, "harvests", "owner_uid", "TEXT DEFAULT ''");
            DatabaseSchema.addColumnIfMissing(db, "harvests", "cloud_id", "TEXT DEFAULT ''");

            DatabaseSchema.addColumnIfMissing(db, "elek_records", "updated_at", "INTEGER DEFAULT 0");
            DatabaseSchema.addColumnIfMissing(db, "elek_records", "owner_uid", "TEXT DEFAULT ''");
            DatabaseSchema.addColumnIfMissing(db, "elek_records", "cloud_id", "TEXT DEFAULT ''");

            DatabaseSchema.addColumnIfMissing(db, "sales", "updated_at", "INTEGER DEFAULT 0");
            DatabaseSchema.addColumnIfMissing(db, "sales", "owner_uid", "TEXT DEFAULT ''");
            DatabaseSchema.addColumnIfMissing(db, "sales", "cloud_id", "TEXT DEFAULT ''");

            DatabaseSchema.addColumnIfMissing(db, "brine_batches", "owner_uid", "TEXT DEFAULT ''");
            DatabaseSchema.addColumnIfMissing(db, "brine_batches", "cloud_id", "TEXT DEFAULT ''");

            db.execSQL("CREATE INDEX IF NOT EXISTS idx_harvests_owner_uid ON harvests(owner_uid)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_harvests_cloud_id ON harvests(cloud_id) WHERE cloud_id IS NOT NULL AND cloud_id <> ''");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_elek_records_owner_uid ON elek_records(owner_uid)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_elek_records_cloud_id ON elek_records(cloud_id) WHERE cloud_id IS NOT NULL AND cloud_id <> ''");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_sales_owner_uid ON sales(owner_uid)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_sales_cloud_id ON sales(cloud_id) WHERE cloud_id IS NOT NULL AND cloud_id <> ''");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_brine_batches_owner_uid ON brine_batches(owner_uid)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_brine_batches_cloud_id ON brine_batches(cloud_id) WHERE cloud_id IS NOT NULL AND cloud_id <> ''");
        }


        // ========================================================
        // VERSION 12 -> 13
        // STOK + ALACAK + HATIRLATICI + BAHÇE NOTU BULUT KİMLİĞİ
        // ========================================================

        if (oldVersion < 13) {

            DatabaseSchema.addColumnIfMissing(db, "inventory_items", "owner_uid", "TEXT DEFAULT ''");
            DatabaseSchema.addColumnIfMissing(db, "inventory_items", "cloud_id", "TEXT DEFAULT ''");

            DatabaseSchema.addColumnIfMissing(db, "inventory_movements", "owner_uid", "TEXT DEFAULT ''");
            DatabaseSchema.addColumnIfMissing(db, "inventory_movements", "cloud_id", "TEXT DEFAULT ''");
            DatabaseSchema.addColumnIfMissing(db, "inventory_movements", "updated_at", "INTEGER DEFAULT 0");

            DatabaseSchema.addColumnIfMissing(db, "receivables", "owner_uid", "TEXT DEFAULT ''");
            DatabaseSchema.addColumnIfMissing(db, "receivables", "cloud_id", "TEXT DEFAULT ''");
            DatabaseSchema.addColumnIfMissing(db, "receivables", "updated_at", "INTEGER DEFAULT 0");

            DatabaseSchema.addColumnIfMissing(db, "reminders", "owner_uid", "TEXT DEFAULT ''");
            DatabaseSchema.addColumnIfMissing(db, "reminders", "cloud_id", "TEXT DEFAULT ''");
            DatabaseSchema.addColumnIfMissing(db, "reminders", "updated_at", "INTEGER DEFAULT 0");

            DatabaseSchema.addColumnIfMissing(db, "garden_notes", "owner_uid", "TEXT DEFAULT ''");
            DatabaseSchema.addColumnIfMissing(db, "garden_notes", "cloud_id", "TEXT DEFAULT ''");
            DatabaseSchema.addColumnIfMissing(db, "garden_notes", "updated_at", "INTEGER DEFAULT 0");

            db.execSQL("CREATE INDEX IF NOT EXISTS idx_inventory_items_owner_uid ON inventory_items(owner_uid)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_inventory_items_cloud_id ON inventory_items(cloud_id) WHERE cloud_id IS NOT NULL AND cloud_id <> ''");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_inventory_movements_owner_uid ON inventory_movements(owner_uid)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_inventory_movements_cloud_id ON inventory_movements(cloud_id) WHERE cloud_id IS NOT NULL AND cloud_id <> ''");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_receivables_owner_uid ON receivables(owner_uid)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_receivables_cloud_id ON receivables(cloud_id) WHERE cloud_id IS NOT NULL AND cloud_id <> ''");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_reminders_owner_uid ON reminders(owner_uid)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_reminders_cloud_id ON reminders(cloud_id) WHERE cloud_id IS NOT NULL AND cloud_id <> ''");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_garden_notes_owner_uid ON garden_notes(owner_uid)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_garden_notes_cloud_id ON garden_notes(cloud_id) WHERE cloud_id IS NOT NULL AND cloud_id <> ''");
        }


        // ========================================================
        // VERSION 13 -> 14
        // FINAL ÇOKLU CİHAZ SENKRONİZASYON ALTYAPISI
        // ========================================================

        if (oldVersion < 14) {
            DatabaseSchema.createSyncTables(db);
        }

        // ========================================================
        // VERSION 14 -> 15
        // SOSYAL AKIŞ FIRESTORE BULUT KİMLİĞİ
        // ========================================================
        if (oldVersion < 15) {
            DatabaseSchema.addColumnIfMissing(db, "posts", "cloud_id", "TEXT");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_posts_cloud_id ON posts(cloud_id)");
        }

        // VERSION 15 -> 16: hesap ve sezon kapsamı bütün üretim kayıtlarında tutulur.
        if (oldVersion < 16) {
            DatabaseSchema.addColumnIfMissing(db, "buyers", "owner_uid", "TEXT DEFAULT ''");
            DatabaseSchema.addColumnIfMissing(db, "harvests", "season_year", "INTEGER DEFAULT 0");
            DatabaseSchema.addColumnIfMissing(db, "elek_records", "season_year", "INTEGER DEFAULT 0");
            DatabaseSchema.addColumnIfMissing(db, "sales", "season_year", "INTEGER DEFAULT 0");
            DatabaseSchema.addColumnIfMissing(db, "brine_batches", "season_year", "INTEGER DEFAULT 0");

            backfillSeasonYear(db, "operations", "operation_date");
            backfillSeasonYear(db, "harvests", "harvest_date");
            backfillSeasonYear(db, "elek_records", "record_date");
            backfillSeasonYear(db, "sales", "sale_date");
            backfillSeasonYear(db, "brine_batches", "start_date");

            db.execSQL(
                    "UPDATE buyers SET owner_uid = COALESCE((" +
                            "SELECT s.owner_uid FROM sales s " +
                            "WHERE s.buyer_id = buyers.id AND s.owner_uid <> '' LIMIT 1" +
                            "), '') WHERE owner_uid IS NULL OR TRIM(owner_uid) = ''"
            );
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_buyers_owner_uid ON buyers(owner_uid)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_operations_owner_season ON operations(owner_uid,season_year)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_harvests_owner_season ON harvests(owner_uid,season_year)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_elek_owner_season ON elek_records(owner_uid,season_year)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_sales_owner_season ON sales(owner_uid,season_year)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_brine_owner_season ON brine_batches(owner_uid,season_year)");
        }


        // ========================================================
        // VERSION 16 -> 17
        // MEYDAN YANITLARI FIRESTORE KİMLİĞİ
        // ========================================================

        if (oldVersion < 17) {

            DatabaseSchema.addColumnIfMissing(
                    db,
                    "post_comments",
                    "cloud_id",
                    "TEXT DEFAULT ''"
            );

            db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_post_comments_cloud_id " +
                            "ON post_comments(cloud_id) " +
                            "WHERE cloud_id IS NOT NULL AND cloud_id <> ''"
            );

            /*
             * Mevcut yerel yorumları SİLMİYORUZ.
             * Daha önce oluşturulmuş yorumlara bulut kimliği veriyoruz.
             */
            Cursor commentCursor = null;

            try {

                commentCursor = db.rawQuery(
                        "SELECT id FROM post_comments " +
                                "WHERE cloud_id IS NULL OR TRIM(cloud_id) = ''",
                        null
                );

                while (commentCursor.moveToNext()) {

                    ContentValues values =
                            new ContentValues();

                    values.put(
                            "cloud_id",
                            UUID.randomUUID().toString()
                    );

                    db.update(
                            "post_comments",
                            values,
                            "id = ?",
                            new String[]{
                                    String.valueOf(
                                            commentCursor.getLong(0)
                                    )
                            }
                    );
                }

            } finally {

                if (commentCursor != null) {
                    commentCursor.close();
                }
            }
        }


        // ========================================================
        // SON GÜVENLİK KONTROLÜ
        // ========================================================

        DatabaseSchema.createGardensTable(
                db
        );


        DatabaseSchema.createOperationsTable(
                db
        );


        DatabaseSchema.createHarvestTables(
                db
        );


        DatabaseSchema.createElekTables(
                db
        );


        DatabaseSchema.createBuyerAndSalesTables(
                db
        );


        DatabaseSchema.createBrineTables(
                db
        );


        DatabaseSchema.createInventoryTables(
                db
        );


        DatabaseSchema.createReceivablesTable(
                db
        );


        DatabaseSchema.createRemindersTable(
                db
        );


        DatabaseSchema.createGardenNotesTable(
                db
        );


        DatabaseSchema.createSettingsTable(
                db
        );


        DatabaseSchema.createSyncTables(
                db
        );


        DatabaseSchema.createSocialTables(
                db
        );


        DatabaseSchema.createSocialInteractionTables(
                db
        );


        DatabaseSchema.createIndexes(
                db
        );
        ensureCommentSyncSchema(db);
        ensureElekSaleLinkSchema(db);
    }


    // ============================================================
    // BAHÇELER
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

        SQLiteDatabase db =
                getWritableDatabase();

        ContentValues values =
                new ContentValues();

        long now =
                System.currentTimeMillis();

        values.put("name", safe(name));
        values.put("tree_count", Math.max(treeCount, 0));
        values.put("age", Math.max(age, 0));
        values.put("variety", safe(variety));
        values.put("area_decare", Math.max(areaDecare, 0));
        values.put("province", safe(province));
        values.put("district", safe(district));
        values.put("neighborhood", safe(neighborhood));
        values.put("irrigation_type", safe(irrigationType));
        values.put("notes", safe(notes));
        values.put("photo_uri", safe(photoUri));
        values.put("created_at", now);
        values.put("updated_at", now);
        values.put("owner_uid", safe(ownerUid));
        values.put("cloud_id", safe(cloudId));

        return db.insert(
                "gardens",
                null,
                values
        );
    }


    public int getGardenCountForOwner(
            String ownerUid
    ) {

        String uid =
                safe(ownerUid);

        if (uid.isEmpty()) {
            return 0;
        }

        SQLiteDatabase db =
                getReadableDatabase();

        Cursor cursor = null;

        try {

            cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM gardens WHERE owner_uid = ?",
                    new String[]{uid}
            );

            return cursor.moveToFirst()
                    ? cursor.getInt(0)
                    : 0;

        } finally {

            if (cursor != null) {
                cursor.close();
            }
        }
    }


    public int getUnownedGardenCount() {

        SQLiteDatabase db =
                getReadableDatabase();

        Cursor cursor = null;

        try {

            cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM gardens " +
                            "WHERE owner_uid IS NULL OR TRIM(owner_uid) = ''",
                    null
            );

            return cursor.moveToFirst()
                    ? cursor.getInt(0)
                    : 0;

        } finally {

            if (cursor != null) {
                cursor.close();
            }
        }
    }


    public int claimUnownedGardens(
            String ownerUid
    ) {

        String uid =
                safe(ownerUid);

        if (uid.isEmpty()) {
            return 0;
        }

        SQLiteDatabase db =
                getWritableDatabase();

        Cursor cursor = null;
        int changed = 0;

        db.beginTransaction();

        try {

            cursor = db.rawQuery(
                    "SELECT id, cloud_id FROM gardens " +
                            "WHERE owner_uid IS NULL OR TRIM(owner_uid) = ''",
                    null
            );

            while (cursor.moveToNext()) {

                long id =
                        cursor.getLong(0);

                String cloudId =
                        safe(cursor.getString(1));

                if (cloudId.isEmpty()) {
                    cloudId = java.util.UUID.randomUUID().toString();
                }

                ContentValues values =
                        new ContentValues();

                values.put("owner_uid", uid);
                values.put("cloud_id", cloudId);
                values.put("updated_at", System.currentTimeMillis());

                changed += db.update(
                        "gardens",
                        values,
                        "id = ?",
                        new String[]{String.valueOf(id)}
                );
            }

            db.setTransactionSuccessful();

        } finally {

            if (cursor != null) {
                cursor.close();
            }

            db.endTransaction();
        }

        return changed;
    }


    public Cursor getGardensForOwner(
            String ownerUid
    ) {

        String uid =
                safe(ownerUid);

        SQLiteDatabase db =
                getReadableDatabase();

        return db.rawQuery(
                "SELECT * FROM gardens " +
                        "WHERE owner_uid = ? " +
                        "ORDER BY name COLLATE NOCASE ASC",
                new String[]{uid}
        );
    }


    public Cursor getGardenByCloudId(
            String ownerUid,
            String cloudId
    ) {

        SQLiteDatabase db =
                getReadableDatabase();

        return db.rawQuery(
                "SELECT * FROM gardens " +
                        "WHERE owner_uid = ? AND cloud_id = ? LIMIT 1",
                new String[]{safe(ownerUid), safe(cloudId)}
        );
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

        String uid = safe(ownerUid);
        String cid = safe(cloudId);

        if (uid.isEmpty() || cid.isEmpty()) {
            return -1;
        }

        SQLiteDatabase db =
                getWritableDatabase();

        ContentValues values =
                new ContentValues();

        values.put("owner_uid", uid);
        values.put("cloud_id", cid);
        values.put("name", safe(name));
        values.put("tree_count", Math.max(treeCount, 0));
        values.put("age", Math.max(age, 0));
        values.put("variety", safe(variety));
        values.put("area_decare", Math.max(areaDecare, 0));
        values.put("province", safe(province));
        values.put("district", safe(district));
        values.put("neighborhood", safe(neighborhood));
        values.put("irrigation_type", safe(irrigationType));
        values.put("notes", safe(notes));
        values.put("photo_uri", safe(photoUri));
        values.put("created_at", createdAt > 0 ? createdAt : System.currentTimeMillis());
        values.put("updated_at", updatedAt > 0 ? updatedAt : System.currentTimeMillis());

        Cursor cursor = null;

        try {

            cursor = db.rawQuery(
                    "SELECT id, updated_at FROM gardens WHERE owner_uid = ? AND cloud_id = ? LIMIT 1",
                    new String[]{uid, cid}
            );

            if (cursor.moveToFirst()) {

                long localId = cursor.getLong(0);
                long localUpdatedAt = cursor.getLong(1);
                long remoteUpdatedAt = updatedAt > 0 ? updatedAt : createdAt;

                // Çoklu cihaz çakışması: daha yeni yerel değişiklik korunur.
                if (localUpdatedAt > remoteUpdatedAt) {
                    return localId;
                }

                db.update(
                        "gardens",
                        values,
                        "id = ?",
                        new String[]{String.valueOf(localId)}
                );

                return localId;
            }

        } finally {

            if (cursor != null) {
                cursor.close();
            }
        }

        return db.insert(
                "gardens",
                null,
                values
        );
    }


    public int ensureGardenCloudIdentity(
            long gardenId,
            String ownerUid,
            String cloudId
    ) {

        if (gardenId <= 0) {
            return 0;
        }

        ContentValues values =
                new ContentValues();

        values.put("owner_uid", safe(ownerUid));
        values.put("cloud_id", safe(cloudId));
        values.put("updated_at", System.currentTimeMillis());

        return getWritableDatabase().update(
                "gardens",
                values,
                "id = ?",
                new String[]{String.valueOf(gardenId)}
        );
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


        SQLiteDatabase db =
                getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "name",
                safe(
                        name
                )
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
                "gardens",
                null,
                values
        );
    }


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
    // GET GARDENS
    // ============================================================

    public Cursor getGardens() {


        SQLiteDatabase db =
                getReadableDatabase();


        String ownerUid =
                getActiveOwnerUid();

        if (!ownerUid.isEmpty()) {

            return db.rawQuery(
                    "SELECT * FROM gardens " +
                            "WHERE owner_uid = ? " +
                            "ORDER BY name COLLATE NOCASE ASC",
                    new String[]{ownerUid}
            );
        }

        return db.rawQuery(
                "SELECT * FROM gardens " +
                        "WHERE owner_uid IS NULL OR TRIM(owner_uid) = '' " +
                        "ORDER BY name COLLATE NOCASE ASC",
                null
        );
    }


    public Cursor getGarden(
            long gardenId
    ) {


        SQLiteDatabase db =
                getReadableDatabase();


        ArrayList<String> args =
                new ArrayList<>();

        args.add(
                String.valueOf(
                        gardenId
                )
        );

        if (!getActiveOwnerUid().isEmpty()) {
            args.add(
                    getActiveOwnerUid()
            );
        }


        return db.rawQuery(
                "SELECT * " +
                        "FROM gardens " +
                        "WHERE id = ? AND " + activeOwnerCondition("owner_uid"),

                args.toArray(
                        new String[0]
                )
        );
    }


    /** Local edit: only user fields change; identity and child rows are untouched. */
    public boolean updateGarden(long gardenId, String name, int treeCount, int age,
            String variety, double areaDecare, String province, String district,
            String neighborhood, String irrigationType, String notes, String photoUri) {
        if (gardenId <= 0 || safe(name).isEmpty() || treeCount < 0 || age < 0
                || Double.isNaN(areaDecare) || Double.isInfinite(areaDecare) || areaDecare < 0) {
            return false;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try (Cursor garden = getGarden(gardenId)) {
            if (!garden.moveToFirst()) return false;
            long previousUpdatedAt = garden.getLong(garden.getColumnIndexOrThrow("updated_at"));
            ContentValues values = new ContentValues();
            values.put("name", safe(name));
            values.put("tree_count", treeCount);
            values.put("age", age);
            values.put("variety", safe(variety));
            values.put("area_decare", areaDecare);
            values.put("province", safe(province));
            values.put("district", safe(district));
            values.put("neighborhood", safe(neighborhood));
            values.put("irrigation_type", safe(irrigationType));
            values.put("notes", safe(notes));
            values.put("photo_uri", safe(photoUri));
            values.put("updated_at", Math.max(System.currentTimeMillis(), previousUpdatedAt + 1));
            ArrayList<String> args = new ArrayList<>();
            args.add(String.valueOf(gardenId));
            if (!getActiveOwnerUid().isEmpty()) args.add(getActiveOwnerUid());
            boolean changed = db.update("gardens", values,
                    "id = ? AND " + activeOwnerCondition("owner_uid"),
                    args.toArray(new String[0])) == 1;
            if (changed) db.setTransactionSuccessful();
            return changed;
        } finally {
            db.endTransaction();
        }
    }

    public Cursor getGardenById(
            long gardenId
    ) {


        return getGarden(
                gardenId
        );
    }


    public int getGardenCount() {


        SQLiteDatabase db =
                getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    getActiveOwnerUid().isEmpty()
                            ? db.rawQuery(
                            "SELECT COUNT(*) FROM gardens " +
                                    "WHERE owner_uid IS NULL OR TRIM(owner_uid) = ''",
                            null
                    )
                            : db.rawQuery(
                            "SELECT COUNT(*) FROM gardens WHERE owner_uid = ?",
                            new String[]{getActiveOwnerUid()}
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
    // GARDEN SUMMARIES
    // ============================================================

    public Cursor getGardenSummaries() {


        SQLiteDatabase db =
                getReadableDatabase();


        return db.rawQuery(

                "SELECT " +

                        "g.id," +
                        "g.name," +
                        "g.tree_count," +
                        "g.age," +
                        "g.variety," +
                        "g.photo_uri," +

                        "(" +
                        "SELECT o.type " +
                        "FROM operations o " +
                        "WHERE o.garden_id = g.id " +
                        "AND " + activeSeasonCondition("o", "operation_date") + " " +
                        "ORDER BY o.created_at DESC " +
                        "LIMIT 1" +
                        ") AS last_operation," +
                        "(" +
                        "SELECT o3.operation_date " +
                        "FROM operations o3 " +
                        "WHERE o3.garden_id = g.id " +
                        "AND " + activeSeasonCondition("o3", "operation_date") + " " +
                        "ORDER BY o3.created_at DESC " +
                        "LIMIT 1" +
                        ") AS last_operation_date," +
                        "(" +
                        "SELECT h2.harvest_date " +
                        "FROM harvests h2 " +
                        "WHERE h2.garden_id = g.id " +
                        "AND " + activeSeasonCondition("h2", "harvest_date") + " " +
                        "ORDER BY h2.created_at DESC " +
                        "LIMIT 1" +
                        ") AS last_harvest_date," +

                        "(" +
                        "SELECT COALESCE(SUM(o2.cost), 0) " +
                        "FROM operations o2 " +
                        "WHERE o2.garden_id = g.id " +
                        "AND " + activeSeasonCondition("o2", "operation_date") +
                        ") AS total_cost," +

                        "(" +
                        "SELECT COALESCE(SUM(h.total_kg), 0) " +
                        "FROM harvests h " +
                        "WHERE h.garden_id = g.id " +
                        "AND " + activeSeasonCondition("h", "harvest_date") +
                        ") AS total_harvest " +

                        "FROM gardens g " +

                        (getActiveOwnerUid().isEmpty()
                                ? "WHERE g.owner_uid IS NULL OR TRIM(g.owner_uid) = '' "
                                : "WHERE g.owner_uid = ? ") +

                        "ORDER BY g.name COLLATE NOCASE ASC",

                getActiveOwnerUid().isEmpty()
                        ? null
                        : new String[]{getActiveOwnerUid()}
        );
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


        SQLiteDatabase db =
                getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "garden_id",
                gardenId
        );


        values.put(
                "type",
                safe(
                        type
                )
        );


        values.put(
                "operation_date",
                safe(
                        date
                )
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

        values.put("season_year", yearOfDate(date));


        String ownerUid =
                getGardenOwnerUid(
                        gardenId
                );


        values.put(
                "owner_uid",
                ownerUid
        );


        if (!ownerUid.isEmpty()) {

            values.put(
                    "cloud_id",
                    java.util.UUID.randomUUID().toString()
            );
        }


        return db.insert(
                "operations",
                null,
                values
        );
    }


    // ============================================================
    // OPERATIONS
    // ============================================================

    public Cursor getLatestOperations(
            int limit
    ) {

        if (limit <= 0) {
            limit = 10;
        }

        String uid = getActiveOwnerUid();

        SQLiteDatabase db =
                getReadableDatabase();

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
                        "o.created_at," +
                        "g.name AS garden_name " +
                        "FROM operations o " +
                        "INNER JOIN gardens g ON g.id = o.garden_id ";

        String[] args;

        if (uid.isEmpty()) {
            sql += "WHERE (g.owner_uid IS NULL OR TRIM(g.owner_uid) = '') ";
            args = new String[]{String.valueOf(limit)};
        } else {
            sql += "WHERE g.owner_uid = ? ";
            args = new String[]{uid, String.valueOf(limit)};
        }

        sql += "AND " + activeSeasonCondition("o", "operation_date") + " ";
        sql += "ORDER BY o.created_at DESC LIMIT ?";

        return db.rawQuery(
                sql,
                args
        );
    }


    public Cursor getGardenOperations(
            long gardenId
    ) {
        ArrayList<String> args = new ArrayList<>();
        args.add(String.valueOf(gardenId));
        if (!getActiveOwnerUid().isEmpty()) args.add(getActiveOwnerUid());

        return getReadableDatabase().rawQuery(
                "SELECT o.* FROM operations o " +
                        "INNER JOIN gardens g ON g.id = o.garden_id " +
                        "WHERE o.garden_id = ? AND " + activeOwnerCondition("g.owner_uid") +
                        " AND " + activeSeasonCondition("o", "operation_date") +
                        " ORDER BY o.created_at DESC",
                args.toArray(new String[0])
        );
    }


    /** Garden detail/history: parent owner + explicit child owner + active season.
     * Blank legacy child owners inherit the owned parent, as in ElekRepository.
     * A nonpositive limit returns the complete season history.
     */
    public Cursor getGardenOperations(long gardenId, int limit) {
        ArrayList<String> args = new ArrayList<>();
        args.add(String.valueOf(gardenId));
        if (!getActiveOwnerUid().isEmpty()) args.add(getActiveOwnerUid());
        args.add(getActiveOwnerUid());
        String sql = "SELECT o.* FROM operations o " +
                "INNER JOIN gardens g ON g.id = o.garden_id " +
                "WHERE o.garden_id = ? AND " + activeOwnerCondition("g.owner_uid") +
                " AND (COALESCE(o.owner_uid,'') = ? OR COALESCE(o.owner_uid,'') = '')" +
                " AND " + activeSeasonCondition("o", "operation_date") +
                " ORDER BY o.created_at DESC, o.id DESC";
        if (limit > 0) {
            sql += " LIMIT ?";
            args.add(String.valueOf(limit));
        }
        return getReadableDatabase().rawQuery(sql, args.toArray(new String[0]));
    }

    public double getOperationCostTotal() {

        String uid = getActiveOwnerUid();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;

        try {

            String sql =
                    "SELECT COALESCE(SUM(o.cost), 0) " +
                            "FROM operations o " +
                            "INNER JOIN gardens g ON g.id = o.garden_id " +
                            "WHERE o.type <> 'Hasat' " +
                            "AND " + activeSeasonCondition("o", "operation_date") + " ";

            String[] args = null;

            if (uid.isEmpty()) {
                sql += "AND (g.owner_uid IS NULL OR TRIM(g.owner_uid) = '')";
            } else {
                sql += "AND g.owner_uid = ?";
                args = new String[]{uid};
            }

            cursor = db.rawQuery(
                    sql,
                    args
            );

            if (cursor.moveToFirst()) {
                return cursor.getDouble(0);
            }

        } finally {

            if (cursor != null) {
                cursor.close();
            }
        }

        return 0;
    }


    // ============================================================
    // OPERATION CLOUD IDENTITY
    // ============================================================

    private String getGardenOwnerUid(
            long gardenId
    ) {

        if (gardenId <= 0) {
            return "";
        }

        Cursor cursor = null;

        try {

            cursor = getReadableDatabase().rawQuery(
                    "SELECT owner_uid FROM gardens WHERE id = ? LIMIT 1",
                    new String[]{String.valueOf(gardenId)}
            );

            if (cursor.moveToFirst()) {
                return safe(cursor.getString(0));
            }

        } finally {

            if (cursor != null) {
                cursor.close();
            }
        }

        return "";
    }


    public int ensureOperationCloudIdentitiesForOwner(
            String ownerUid
    ) {

        String uid = safe(ownerUid);

        if (uid.isEmpty()) {
            return 0;
        }

        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor = null;
        int changed = 0;

        db.beginTransaction();

        try {

            cursor = db.rawQuery(
                    "SELECT o.id, o.cloud_id " +
                            "FROM operations o " +
                            "INNER JOIN gardens g ON g.id = o.garden_id " +
                            "WHERE g.owner_uid = ?",
                    new String[]{uid}
            );

            while (cursor.moveToNext()) {

                long operationId = cursor.getLong(0);
                String cloudId = safe(cursor.getString(1));

                ContentValues values = new ContentValues();
                values.put("owner_uid", uid);

                if (cloudId.isEmpty()) {
                    values.put(
                            "cloud_id",
                            java.util.UUID.randomUUID().toString()
                    );
                }

                changed += db.update(
                        "operations",
                        values,
                        "id = ?",
                        new String[]{String.valueOf(operationId)}
                );
            }

            db.setTransactionSuccessful();

        } finally {

            if (cursor != null) {
                cursor.close();
            }

            db.endTransaction();
        }

        return changed;
    }


    public Cursor getOperationsForOwner(
            String ownerUid
    ) {

        return getReadableDatabase().rawQuery(
                "SELECT o.*, g.cloud_id AS garden_cloud_id " +
                        "FROM operations o " +
                        "INNER JOIN gardens g ON g.id = o.garden_id " +
                        "WHERE g.owner_uid = ? " +
                        "ORDER BY o.created_at ASC",
                new String[]{safe(ownerUid)}
        );
    }


    public Cursor getOperationForCloudSync(
            String ownerUid,
            long operationId
    ) {

        return getReadableDatabase().rawQuery(
                "SELECT o.*, g.cloud_id AS garden_cloud_id " +
                        "FROM operations o " +
                        "INNER JOIN gardens g ON g.id = o.garden_id " +
                        "WHERE g.owner_uid = ? AND o.id = ? LIMIT 1",
                new String[]{
                        safe(ownerUid),
                        String.valueOf(operationId)
                }
        );
    }


    public long upsertOperationFromCloud(
            String ownerUid,
            String gardenCloudId,
            String operationCloudId,
            String type,
            String date,
            double quantity,
            String unit,
            double cost,
            String note,
            int seasonYear,
            long createdAt,
            long updatedAt
    ) {

        String uid = safe(ownerUid);
        String gardenCid = safe(gardenCloudId);
        String operationCid = safe(operationCloudId);

        if (uid.isEmpty()
                || gardenCid.isEmpty()
                || operationCid.isEmpty()) {
            return -1;
        }

        SQLiteDatabase db = getWritableDatabase();
        Cursor gardenCursor = null;
        long localGardenId = -1;

        try {

            gardenCursor = db.rawQuery(
                    "SELECT id FROM gardens " +
                            "WHERE owner_uid = ? AND cloud_id = ? LIMIT 1",
                    new String[]{uid, gardenCid}
            );

            if (gardenCursor.moveToFirst()) {
                localGardenId = gardenCursor.getLong(0);
            }

        } finally {

            if (gardenCursor != null) {
                gardenCursor.close();
            }
        }

        if (localGardenId <= 0) {
            return -1;
        }

        long safeCreatedAt =
                createdAt > 0
                        ? createdAt
                        : System.currentTimeMillis();

        long safeUpdatedAt =
                updatedAt > 0
                        ? updatedAt
                        : safeCreatedAt;

        ContentValues values = new ContentValues();
        values.put("garden_id", localGardenId);
        values.put("type", safe(type));
        values.put("operation_date", safe(date));
        values.put("quantity", Math.max(quantity, 0));
        values.put("unit", safe(unit));
        values.put("cost", Math.max(cost, 0));
        values.put("note", safe(note));
        values.put("season_year", Math.max(seasonYear, 0));
        values.put("created_at", safeCreatedAt);
        values.put("updated_at", safeUpdatedAt);
        values.put("owner_uid", uid);
        values.put("cloud_id", operationCid);

        Cursor cursor = null;

        try {

            cursor = db.rawQuery(
                    "SELECT id, updated_at FROM operations " +
                            "WHERE owner_uid = ? AND cloud_id = ? LIMIT 1",
                    new String[]{uid, operationCid}
            );

            if (cursor.moveToFirst()) {

                long localOperationId = cursor.getLong(0);
                long localUpdatedAt = cursor.getLong(1);

                if (localUpdatedAt > safeUpdatedAt) {
                    return localOperationId;
                }

                db.update(
                        "operations",
                        values,
                        "id = ?",
                        new String[]{String.valueOf(localOperationId)}
                );

                return localOperationId;
            }

        } finally {

            if (cursor != null) {
                cursor.close();
            }
        }

        return db.insert(
                "operations",
                null,
                values
        );
    }


    // ============================================================
    // HARVEST
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


        SQLiteDatabase db =
                getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "garden_id",
                gardenId
        );


        values.put(
                "harvest_date",
                safe(
                        harvestDate
                )
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


        long now = System.currentTimeMillis();
        String ownerUid = getGardenOwnerUid(gardenId);
        values.put("created_at", now);
        values.put("updated_at", now);
        values.put("season_year", yearOfDate(harvestDate));
        values.put("owner_uid", ownerUid);
        values.put("cloud_id", ownerUid.isEmpty() ? "" : UUID.randomUUID().toString());


        return db.insert(
                "harvests",
                null,
                values
        );
    }


    public long addHarvestGrade(
            long harvestId,
            String grade,
            double kg,
            double referencePricePerKg,
            String referencePriceDate
    ) {


        SQLiteDatabase db =
                getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "harvest_id",
                harvestId
        );


        values.put(
                "grade",
                safe(
                        grade
                )
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


        return db.insert(
                "harvest_grades",
                null,
                values
        );
    }


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


    public Cursor getGardenHarvests(
            long gardenId
    ) {
        ArrayList<String> args = new ArrayList<>();
        args.add(String.valueOf(gardenId));
        if (!getActiveOwnerUid().isEmpty()) args.add(getActiveOwnerUid());
        return getReadableDatabase().rawQuery(
                "SELECT h.* FROM harvests h " +
                        "INNER JOIN gardens g ON g.id = h.garden_id " +
                        "WHERE h.garden_id = ? AND " + activeOwnerCondition("g.owner_uid") +
                        " AND " + activeSeasonCondition("h", "harvest_date") +
                        " ORDER BY h.created_at DESC",
                args.toArray(new String[0])
        );
    }


    public double getTotalHarvestKg() {
        return getDoubleValue(
                "SELECT COALESCE(SUM(h.total_kg), 0) FROM harvests h " +
                        "INNER JOIN gardens g ON g.id = h.garden_id " +
                        "WHERE " + activeOwnerCondition("g.owner_uid") +
                        " AND " + activeSeasonCondition("h", "harvest_date"),
                activeOwnerArgs()
        );
    }


    public double getTotalHarvestCost() {


        return getDoubleValue(
                "SELECT COALESCE(" +
                        "SUM(h.labor_cost + h.transport_cost + h.other_cost), 0) " +
                        "FROM harvests h " +
                        "INNER JOIN gardens g ON g.id = h.garden_id " +
                        "WHERE " + activeOwnerCondition("g.owner_uid") +
                        " AND " + activeSeasonCondition("h", "harvest_date"),
                activeOwnerArgs()
        );
    }


    public double getLastHarvestReferencePrice(
            String grade
    ) {


        SQLiteDatabase db =
                getReadableDatabase();


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
                                    safe(
                                            grade
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
    // ELEK
    // ============================================================

    public long addElekRecord(
            long gardenId,
            String recordDate,
            String[] grades,
            double[] kilograms,
            double[] prices,
            String note
    ) {


        if (gardenId <= 0) {


            return -1;
        }


        if (safe(
                recordDate
        ).isEmpty()) {


            return -1;
        }


        if (grades == null
                ||
                kilograms == null
                ||
                prices == null) {


            return -1;
        }


        if (grades.length == 0
                ||
                grades.length != kilograms.length
                ||
                grades.length != prices.length) {


            return -1;
        }


        double totalKg =
                0;


        double estimatedTotal =
                0;


        for (
                int i = 0;
                i < grades.length;
                i++
        ) {


            double kg =
                    Math.max(
                            kilograms[i],
                            0
                    );


            double price =
                    Math.max(
                            prices[i],
                            0
                    );


            totalKg +=
                    kg;


            estimatedTotal +=
                    kg * price;
        }


        if (totalKg <= 0) {


            return -1;
        }


        SQLiteDatabase db =
                getWritableDatabase();


        long elekId =
                -1;


        db.beginTransaction();


        try {


            ContentValues record =
                    new ContentValues();


            record.put(
                    "garden_id",
                    gardenId
            );


            record.put(
                    "record_date",
                    recordDate.trim()
            );


            record.put(
                    "total_kg",
                    totalKg
            );


            record.put(
                    "estimated_total",
                    estimatedTotal
            );


            record.put(
                    "note",
                    safe(
                            note
                    )
            );


            long now = System.currentTimeMillis();
            String ownerUid = getGardenOwnerUid(gardenId);
            record.put("created_at", now);
            record.put("updated_at", now);
            record.put("season_year", yearOfDate(recordDate));
            record.put("owner_uid", ownerUid);
            record.put("cloud_id", ownerUid.isEmpty() ? "" : UUID.randomUUID().toString());


            elekId =
                    db.insert(
                            "elek_records",
                            null,
                            record
                    );


            if (elekId == -1) {


                throw new RuntimeException(
                        "Elek kaydı oluşturulamadı"
                );
            }


            for (
                    int i = 0;
                    i < grades.length;
                    i++
            ) {


                String grade =
                        safe(
                                grades[i]
                        );


                double kg =
                        Math.max(
                                kilograms[i],
                                0
                        );


                double price =
                        Math.max(
                                prices[i],
                                0
                        );


                if (grade.isEmpty()
                        ||
                        kg <= 0) {


                    continue;
                }


                ContentValues line =
                        new ContentValues();


                line.put(
                        "elek_id",
                        elekId
                );


                line.put(
                        "grade",
                        grade
                );


                line.put(
                        "kg",
                        kg
                );


                line.put(
                        "price_per_kg",
                        price
                );


                line.put(
                        "line_total",
                        kg * price
                );


                long lineResult =
                        db.insert(
                                "elek_grades",
                                null,
                                line
                        );


                if (lineResult == -1) {


                    throw new RuntimeException(
                            "Elek satırı kaydedilemedi"
                    );
                }
            }


            db.setTransactionSuccessful();


        } catch (Exception e) {


            e.printStackTrace();


            elekId =
                    -1;


        } finally {


            db.endTransaction();
        }


        return elekId;
    }


    public Cursor getElekRecords() {


        SQLiteDatabase db =
                getReadableDatabase();


        return db.rawQuery(
                "SELECT e.*, g.name AS garden_name FROM elek_records e " +
                        "INNER JOIN gardens g ON g.id = e.garden_id " +
                        "WHERE " + activeOwnerCondition("g.owner_uid") +
                        " AND " + activeSeasonCondition("e", "record_date") +
                        " ORDER BY e.created_at DESC",
                activeOwnerArgs()
        );
    }


    public Cursor getGardenElekRecords(
            long gardenId
    ) {


        SQLiteDatabase db =
                getReadableDatabase();


        ArrayList<String> args = new ArrayList<>();
        args.add(String.valueOf(gardenId));
        if (!getActiveOwnerUid().isEmpty()) args.add(getActiveOwnerUid());
        return db.rawQuery(
                "SELECT e.*, g.name AS garden_name FROM elek_records e " +
                        "INNER JOIN gardens g ON g.id = e.garden_id " +
                        "WHERE e.garden_id = ? AND " + activeOwnerCondition("g.owner_uid") +
                        " AND " + activeSeasonCondition("e", "record_date") +
                        " ORDER BY e.created_at DESC",
                args.toArray(new String[0])
        );
    }


    public Cursor getElekRecord(
            long elekId
    ) {


        SQLiteDatabase db =
                getReadableDatabase();


        ArrayList<String> args =
                new ArrayList<>();

        args.add(
                String.valueOf(
                        elekId
                )
        );

        if (!getActiveOwnerUid().isEmpty()) {
            args.add(
                    getActiveOwnerUid()
            );
        }


        return db.rawQuery(

                "SELECT e.*, g.name AS garden_name " +

                        "FROM elek_records e " +

                        "INNER JOIN gardens g " +
                        "ON g.id = e.garden_id " +

                        "WHERE e.id = ? AND " + activeOwnerCondition("g.owner_uid") +
                        " AND " + activeSeasonCondition("e", "record_date"),

                args.toArray(
                        new String[0]
                )
        );
    }


    public Cursor getElekGrades(
            long elekId
    ) {


        SQLiteDatabase db =
                getReadableDatabase();


        ArrayList<String> args =
                new ArrayList<>();

        args.add(
                String.valueOf(
                        elekId
                )
        );

        if (!getActiveOwnerUid().isEmpty()) {
            args.add(
                    getActiveOwnerUid()
            );
        }


        return db.rawQuery(

                "SELECT eg.* " +

                        "FROM elek_grades eg " +

                        "INNER JOIN elek_records e " +
                        "ON e.id = eg.elek_id " +

                        "INNER JOIN gardens g " +
                        "ON g.id = e.garden_id " +

                        "WHERE eg.elek_id = ? AND " + activeOwnerCondition("g.owner_uid") +
                        " AND " + activeSeasonCondition("e", "record_date") + " " +

                        "ORDER BY CASE grade " +

                        "WHEN 'DOUBLE' THEN 0 " +
                        "WHEN '1' THEN 1 " +
                        "WHEN '2' THEN 2 " +
                        "WHEN '3' THEN 3 " +
                        "WHEN '4' THEN 4 " +
                        "WHEN 'YAGLIK' THEN 5 " +

                        "ELSE 6 END",

                args.toArray(
                        new String[0]
                )
        );
    }


    public double getTotalElekKg() {
        return getDoubleValue(
                "SELECT COALESCE(SUM(e.total_kg), 0) FROM elek_records e " +
                        "INNER JOIN gardens g ON g.id = e.garden_id " +
                        "WHERE " + activeOwnerCondition("g.owner_uid") +
                        " AND " + activeSeasonCondition("e", "record_date"),
                activeOwnerArgs()
        );
    }


    public double getTotalElekEstimatedValue() {


        return getDoubleValue(
                "SELECT COALESCE(SUM(e.estimated_total), 0) FROM elek_records e " +
                        "INNER JOIN gardens g ON g.id = e.garden_id " +
                        "WHERE " + activeOwnerCondition("g.owner_uid") +
                        " AND " + activeSeasonCondition("e", "record_date"),
                activeOwnerArgs()
        );
    }


    public double getTotalElekKgByGrade(
            String grade
    ) {


        SQLiteDatabase db =
                getReadableDatabase();


        Cursor cursor =
                null;


        try {


            ArrayList<String> args = new ArrayList<>();
            args.add(safe(grade));
            if (!getActiveOwnerUid().isEmpty()) args.add(getActiveOwnerUid());
            cursor = db.rawQuery(
                    "SELECT COALESCE(SUM(eg.kg), 0) FROM elek_grades eg " +
                            "INNER JOIN elek_records e ON e.id = eg.elek_id " +
                            "INNER JOIN gardens g ON g.id = e.garden_id " +
                            "WHERE eg.grade = ? AND " + activeOwnerCondition("g.owner_uid") +
                            " AND " + activeSeasonCondition("e", "record_date"),
                    args.toArray(new String[0])
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
    // BUYER
    // ============================================================

    public long addBuyer(
            String name,
            String phone,
            String region,
            String note
    ) {


        if (safe(
                name
        ).isEmpty()) {


            return -1;
        }


        SQLiteDatabase db =
                getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "name",
                name.trim()
        );


        values.put(
                "phone",
                safe(
                        phone
                )
        );


        values.put(
                "region",
                safe(
                        region
                )
        );


        values.put(
                "note",
                safe(
                        note
                )
        );


        values.put("created_at", System.currentTimeMillis());
        values.put("owner_uid", getActiveOwnerUid());


        return db.insert(
                "buyers",
                null,
                values
        );
    }


    public void deleteBuyerIfUnused(long buyerId) {
        if (buyerId <= 0) return;

        SQLiteDatabase db = getWritableDatabase();
        String ownerUid = getActiveOwnerUid();
        db.delete(
                "buyers",
                "id = ? AND COALESCE(owner_uid, '') = ? " +
                        "AND NOT EXISTS (SELECT 1 FROM sales WHERE buyer_id = ?)",
                new String[]{
                        String.valueOf(buyerId),
                        ownerUid,
                        String.valueOf(buyerId)
                }
        );
    }


    // ============================================================
    // SALE
    // ============================================================

    public long addSale(
            int gardenId,
            Long buyerId,
            String saleDate,
            String saleKind,
            String region,
            String variety,
            String productType,
            double transportCost,
            double deductions,
            double amountReceived,
            String dueDate,
            String note,
            boolean marketConsent
    ) {


        SQLiteDatabase db =
                getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "garden_id",
                gardenId
        );


        if (buyerId != null
                &&
                buyerId > 0) {


            values.put(
                    "buyer_id",
                    buyerId
            );
        }


        values.put(
                "sale_date",
                safe(
                        saleDate
                )
        );


        values.put(
                "sale_kind",
                safe(
                        saleKind
                ).isEmpty()
                        ? "SALE"
                        : saleKind.trim()
        );


        values.put(
                "region",
                safe(
                        region
                )
        );


        values.put(
                "variety",
                safe(
                        variety
                )
        );


        values.put(
                "product_type",
                safe(
                        productType
                ).isEmpty()
                        ? "SOFRALIK"
                        : productType.trim()
        );


        values.put(
                "transport_cost",
                Math.max(
                        transportCost,
                        0
                )
        );


        values.put(
                "deductions",
                Math.max(
                        deductions,
                        0
                )
        );


        values.put(
                "amount_received",
                Math.max(
                        amountReceived,
                        0
                )
        );


        values.put(
                "due_date",
                safe(
                        dueDate
                )
        );


        values.put(
                "note",
                safe(
                        note
                )
        );


        values.put(
                "market_consent",
                marketConsent
                        ? 1
                        : 0
        );


        values.put(
                "market_sync_status",
                "PENDING"
        );


        long now = System.currentTimeMillis();
        String ownerUid = getGardenOwnerUid(gardenId);
        values.put("created_at", now);
        values.put("updated_at", now);
        values.put("season_year", yearOfDate(saleDate));
        values.put("owner_uid", ownerUid);
        values.put("cloud_id", ownerUid.isEmpty() ? "" : UUID.randomUUID().toString());


        return db.insert(
                "sales",
                null,
                values
        );
    }


    public long addSaleGrade(
            long saleId,
            String grade,
            double kg,
            double pricePerKg
    ) {


        if (saleId <= 0) {


            return -1;
        }


        SQLiteDatabase db =
                getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "sale_id",
                saleId
        );


        values.put(
                "grade",
                safe(
                        grade
                )
        );


        values.put(
                "kg",
                Math.max(
                        kg,
                        0
                )
        );


        values.put(
                "price_per_kg",
                Math.max(
                        pricePerKg,
                        0
                )
        );


        values.put(
                "line_total",
                Math.max(
                        kg,
                        0
                )
                        *
                        Math.max(
                                pricePerKg,
                                0
                        )
        );


        long result =
                db.insert(
                        "sale_grades",
                        null,
                        values
                );


        if (result != -1) {


            updateSaleTotals(
                    saleId
            );
        }


        return result;
    }


    private void updateSaleTotals(
            long saleId
    ) {


        SQLiteDatabase db =
                getWritableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT " +

                                    "COALESCE(SUM(kg), 0), " +

                                    "COALESCE(SUM(line_total), 0) " +

                                    "FROM sale_grades " +

                                    "WHERE sale_id = ?",

                            new String[]{
                                    String.valueOf(
                                            saleId
                                    )
                            }
                    );


            if (cursor.moveToFirst()) {


                ContentValues values =
                        new ContentValues();


                values.put(
                        "total_kg",
                        cursor.getDouble(
                                0
                        )
                );


                values.put(
                        "gross_total",
                        cursor.getDouble(
                                1
                        )
                );


                db.update(
                        "sales",
                        values,
                        "id = ?",

                        new String[]{
                                String.valueOf(
                                        saleId
                                )
                        }
                );
            }


        } finally {


            if (cursor != null) {


                cursor.close();
            }
        }
    }


    public Cursor getGardenSales(
            long gardenId
    ) {


        SQLiteDatabase db =
                getReadableDatabase();


        ArrayList<String> args = new ArrayList<>();
        args.add(String.valueOf(gardenId));
        if (!getActiveOwnerUid().isEmpty()) args.add(getActiveOwnerUid());
        return db.rawQuery(
                "SELECT s.* FROM sales s " +
                        "INNER JOIN gardens g ON g.id = s.garden_id " +
                        "WHERE s.garden_id = ? AND " + activeOwnerCondition("g.owner_uid") +
                        " AND " + activeSeasonCondition("s", "sale_date") +
                        " ORDER BY s.created_at DESC",
                args.toArray(new String[0])
        );
    }


    public double getTotalSalesRevenue() {


        return getDoubleValue(
                "SELECT COALESCE(" +
                        "SUM(s.gross_total - s.transport_cost - s.deductions), 0) " +
                        "FROM sales s " +
                        "INNER JOIN gardens g ON g.id = s.garden_id " +
                        "WHERE s.sale_kind = 'SALE' AND " + activeOwnerCondition("g.owner_uid") +
                        " AND " + activeSeasonCondition("s", "sale_date"),
                activeOwnerArgs()
        );
    }


    // ============================================================
    // MARKET
    // ============================================================

    public Cursor getLocalMarketByGrade(
            String grade
    ) {


        SQLiteDatabase db =
                getReadableDatabase();


        return db.rawQuery(

                "SELECT " +

                        "sales.region AS region," +

                        "COUNT(DISTINCT sales.id) AS sale_count," +

                        "SUM(sale_grades.kg) AS total_kg," +

                        "AVG(sale_grades.price_per_kg) AS avg_price," +

                        "MIN(sale_grades.price_per_kg) AS min_price," +

                        "MAX(sale_grades.price_per_kg) AS max_price " +

                        "FROM sale_grades " +

                        "INNER JOIN sales " +
                        "ON sales.id = sale_grades.sale_id " +

                        "WHERE sale_grades.grade = ? " +

                        "AND sales.sale_kind = 'SALE' " +

                        "AND sales.market_consent = 1 " +

                        "AND sale_grades.kg > 0 " +

                        "AND sale_grades.price_per_kg > 0 " +

                        "GROUP BY sales.region " +

                        "ORDER BY total_kg DESC",

                new String[]{
                        safe(
                                grade
                        )
                }
        );
    }


    // ============================================================
    // BRINE
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


        SQLiteDatabase db =
                getWritableDatabase();


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

        String ownerUid = gardenId != null && gardenId > 0
                ? getGardenOwnerUid(gardenId)
                : getActiveOwnerUid();
        values.put("season_year", yearOfDate(startDate));
        values.put("owner_uid", ownerUid);
        values.put("cloud_id", ownerUid.isEmpty() ? "" : UUID.randomUUID().toString());


        return db.insert(
                "brine_batches",
                null,
                values
        );
    }


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


    public long addBrineEvent(
            long batchId,
            String eventDate,
            String eventType,
            double amount,
            String unit,
            double cost,
            String note
    ) {


        if (!isActiveOwnerSeasonRecord(
                "brine_batches",
                batchId,
                "start_date"
        )) {
            return -1;
        }


        SQLiteDatabase db =
                getWritableDatabase();


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


            ContentValues update =
                    new ContentValues();


            update.put(
                    "updated_at",
                    System.currentTimeMillis()
            );


            db.update(
                    "brine_batches",
                    update,
                    "id = ?",

                    new String[]{
                            String.valueOf(
                                    batchId
                            )
                    }
            );
        }


        return result;
    }


    public Cursor getBrineBatches() {


        SQLiteDatabase db =
                getReadableDatabase();


        return db.rawQuery(

                "SELECT " +

                        "b.*," +

                        "g.name AS garden_name," +

                        "(" +

                        "SELECT MAX(e.event_date) " +

                        "FROM brine_events e " +

                        "WHERE e.batch_id = b.id" +

                        ") AS last_event_date," +

                        "(" +

                        "SELECT COALESCE(SUM(e.cost), 0) " +

                        "FROM brine_events e " +

                        "WHERE e.batch_id = b.id" +

                        ") AS event_cost " +

                        "FROM brine_batches b " +

                        "LEFT JOIN gardens g " +
                        "ON g.id = b.garden_id " +

                        "WHERE " + activeOwnerCondition("b.owner_uid") + " " +
                        "AND " + activeSeasonCondition("b", "start_date") + " " +

                        "ORDER BY " +

                        "CASE b.status " +

                        "WHEN 'ACTIVE' THEN 0 " +

                        "WHEN 'READY' THEN 1 " +

                        "WHEN 'CLOSED' THEN 2 " +

                        "ELSE 3 END," +

                        "b.created_at DESC",

                activeOwnerArgs()
        );
    }


    public Cursor getBrineBatch(
            long batchId
    ) {


        SQLiteDatabase db =
                getReadableDatabase();


        ArrayList<String> args =
                new ArrayList<>();

        args.add(
                String.valueOf(
                        batchId
                )
        );

        if (!getActiveOwnerUid().isEmpty()) {
            args.add(
                    getActiveOwnerUid()
            );
        }


        return db.rawQuery(

                "SELECT b.*, g.name AS garden_name " +

                        "FROM brine_batches b " +

                        "LEFT JOIN gardens g " +
                        "ON g.id = b.garden_id " +

                        "WHERE b.id = ? AND " + activeOwnerCondition("b.owner_uid") +
                        " AND " + activeSeasonCondition("b", "start_date"),

                args.toArray(
                        new String[0]
                )
        );
    }


    public Cursor getBrineEvents(
            long batchId
    ) {


        SQLiteDatabase db =
                getReadableDatabase();


        ArrayList<String> args =
                new ArrayList<>();

        args.add(
                String.valueOf(
                        batchId
                )
        );

        if (!getActiveOwnerUid().isEmpty()) {
            args.add(
                    getActiveOwnerUid()
            );
        }


        return db.rawQuery(

                "SELECT e.* " +

                        "FROM brine_events e " +

                        "INNER JOIN brine_batches b " +
                        "ON b.id = e.batch_id " +

                        "WHERE e.batch_id = ? AND " + activeOwnerCondition("b.owner_uid") +
                        " AND " + activeSeasonCondition("b", "start_date") + " " +

                        "ORDER BY e.created_at DESC",

                args.toArray(
                        new String[0]
                )
        );
    }


    public int updateBrineStatus(
            long batchId,
            String status
    ) {


        if (!isActiveOwnerSeasonRecord(
                "brine_batches",
                batchId,
                "start_date"
        )) {
            return 0;
        }


        SQLiteDatabase db =
                getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "status",
                safe(
                        status
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


    public int updateBrineReadyKg(
            long batchId,
            double readyKg
    ) {


        if (!isActiveOwnerSeasonRecord(
                "brine_batches",
                batchId,
                "start_date"
        )) {
            return 0;
        }


        SQLiteDatabase db =
                getWritableDatabase();


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


    public double getBrineBatchTotalCost(
            long batchId
    ) {


        SQLiteDatabase db =
                getReadableDatabase();


        Cursor cursor =
                null;


        ArrayList<String> args =
                new ArrayList<>();

        args.add(
                String.valueOf(
                        batchId
                )
        );

        if (!getActiveOwnerUid().isEmpty()) {
            args.add(
                    getActiveOwnerUid()
            );
        }


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

                                    "WHERE b.id = ? AND " + activeOwnerCondition("b.owner_uid") +
                                    " AND " + activeSeasonCondition("b", "start_date"),

                            args.toArray(
                                    new String[0]
                            )
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


    public double getTotalBrineCost() {
        return getDoubleValue(
                "SELECT COALESCE(SUM(" +
                        "COALESCE(b.salt_cost, 0) + COALESCE(b.packaging_cost, 0) + " +
                        "COALESCE(b.other_cost, 0) + COALESCE((" +
                        "SELECT SUM(e.cost) FROM brine_events e WHERE e.batch_id = b.id" +
                        "), 0)), 0) FROM brine_batches b " +
                        "WHERE " + activeOwnerCondition("b.owner_uid") +
                        " AND " + activeSeasonCondition("b", "start_date"),
                activeOwnerArgs()
        );
    }


    public double getTotalBrineOliveKg() {


        return getDoubleValue(
                "SELECT COALESCE(SUM(b.olive_kg), 0) FROM brine_batches b " +
                        "WHERE " + activeOwnerCondition("b.owner_uid") +
                        " AND " + activeSeasonCondition("b", "start_date"),
                activeOwnerArgs()
        );
    }


    public double getTotalReadyBrineKg() {


        return getDoubleValue(
                "SELECT COALESCE(SUM(b.ready_kg), 0) FROM brine_batches b " +
                        "WHERE " + activeOwnerCondition("b.owner_uid") +
                        " AND " + activeSeasonCondition("b", "start_date"),
                activeOwnerArgs()
        );
    }


    public int getActiveBrineBatchCount() {


        SQLiteDatabase db =
                getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM brine_batches b " +
                            "WHERE b.status = 'ACTIVE' AND " + activeOwnerCondition("b.owner_uid") +
                            " AND " + activeSeasonCondition("b", "start_date"),
                    activeOwnerArgs()
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
    // INVENTORY
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


        SQLiteDatabase db =
                getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "name",
                safe(
                        name
                )
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

        String ownerUid = getActiveOwnerUid();
        values.put("owner_uid", ownerUid);
        values.put("cloud_id", ownerUid.isEmpty() ? "" : UUID.randomUUID().toString());


        return db.insert(
                "inventory_items",
                null,
                values
        );
    }


    public Cursor getInventoryItems() {


        SQLiteDatabase db =
                getReadableDatabase();


        String ownerUid = getActiveOwnerUid();

        return db.rawQuery(

                "SELECT * FROM inventory_items " +
                        "WHERE owner_uid = ? " +
                        "ORDER BY name COLLATE NOCASE ASC",

                new String[]{ownerUid}
        );
    }


    public long addInventoryMovement(
            long itemId,
            String movementDate,
            String movementType,
            double quantity,
            double unitCost,
            Long operationId,
            String note
    ) {


        SQLiteDatabase db =
                getWritableDatabase();


        long movementId =
                -1;


        db.beginTransaction();


        try {


            ContentValues values =
                    new ContentValues();


            values.put(
                    "item_id",
                    itemId
            );


            values.put(
                    "movement_date",
                    safe(
                            movementDate
                    )
                    );


            values.put(
                    "movement_type",
                    safe(
                            movementType
                    )
            );


            values.put(
                    "quantity",
                    Math.max(
                            quantity,
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


            long now = System.currentTimeMillis();

            values.put(
                    "created_at",
                    now
            );

            values.put("updated_at", now);
            String ownerUid = getActiveOwnerUid();
            values.put("owner_uid", ownerUid);
            values.put("cloud_id", ownerUid.isEmpty() ? "" : UUID.randomUUID().toString());


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


            double signedQuantity;


            if ("OUT".equalsIgnoreCase(
                    movementType
            )) {


                signedQuantity =
                        -Math.max(
                                quantity,
                                0
                        );


            } else {


                signedQuantity =
                        Math.max(
                                quantity,
                                0
                        );
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
    // RECEIVABLES
    // ============================================================

    public long addReceivable(
            String title,
            String partyName,
            Long relatedSaleId,
            double amount,
            double totalReceived,
            String dueDate,
            String note
    ) {


        SQLiteDatabase db =
                getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "title",
                safe(
                        title
                )
        );


        values.put(
                "party_name",
                safe(
                        partyName
                )
        );


        if (relatedSaleId != null
                &&
                relatedSaleId > 0) {


            values.put(
                    "related_sale_id",
                    relatedSaleId
            );
        }


        values.put(
                "amount",
                Math.max(
                        amount,
                        0
                )
        );


        values.put(
                "total_received",
                Math.max(
                        totalReceived,
                        0
                )
        );


        values.put(
                "due_date",
                safe(
                        dueDate
                )
        );


        values.put(
                "status",
                totalReceived >= amount
                        ? "PAID"
                        : "OPEN"
        );


        values.put(
                "note",
                safe(
                        note
                )
        );


        long now = System.currentTimeMillis();

        values.put(
                "created_at",
                now
        );

        values.put("updated_at", now);
        String ownerUid = getActiveOwnerUid();
        values.put("owner_uid", ownerUid);
        values.put("cloud_id", ownerUid.isEmpty() ? "" : UUID.randomUUID().toString());


        return db.insert(
                "receivables",
                null,
                values
        );
    }


    public Cursor getReceivables() {


        SQLiteDatabase db =
                getReadableDatabase();


        String ownerUid = getActiveOwnerUid();

        return db.rawQuery(

                "SELECT * FROM receivables " +
                        "WHERE owner_uid = ? " +
                        "ORDER BY CASE status " +
                        "WHEN 'OPEN' THEN 0 " +
                        "WHEN 'PAID' THEN 1 " +
                        "ELSE 2 END," +
                        "created_at DESC",

                new String[]{ownerUid}
        );
    }


    // ============================================================
    // REMINDERS
    // ============================================================

    public long addReminder(
            Integer gardenId,
            String title,
            String reminderType,
            long dueAt,
            String repeatRule,
            String note
    ) {


        SQLiteDatabase db =
                getWritableDatabase();


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
                "title",
                safe(
                        title
                )
        );


        values.put(
                "reminder_type",
                safe(
                        reminderType
                )
        );


        values.put(
                "due_at",
                dueAt
        );


        values.put(
                "is_done",
                0
        );


        values.put(
                "repeat_rule",
                safe(
                        repeatRule
                )
        );


        values.put(
                "note",
                safe(
                        note
                )
        );


        long now = System.currentTimeMillis();

        values.put(
                "created_at",
                now
        );

        values.put("updated_at", now);
        String ownerUid = getActiveOwnerUid();
        values.put("owner_uid", ownerUid);
        values.put("cloud_id", ownerUid.isEmpty() ? "" : UUID.randomUUID().toString());


        return db.insert(
                "reminders",
                null,
                values
        );
    }


    public Cursor getPendingReminders(
            int limit
    ) {


        if (limit <= 0) {


            limit =
                    10;
        }


        SQLiteDatabase db =
                getReadableDatabase();


        return db.rawQuery(

                "SELECT reminders.*, gardens.name AS garden_name " +

                        "FROM reminders " +

                        "LEFT JOIN gardens " +
                        "ON gardens.id = reminders.garden_id " +

                        "WHERE reminders.is_done = 0 " +
                        "AND reminders.owner_uid = ? " +

                        "ORDER BY reminders.due_at ASC " +

                        "LIMIT ?",

                new String[]{
                        getActiveOwnerUid(),
                        String.valueOf(
                                limit
                        )
                }
        );
    }


    public int markReminderDone(
            long reminderId
    ) {


        SQLiteDatabase db =
                getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "is_done",
                1
        );
        values.put("updated_at", System.currentTimeMillis());


        return db.update(
                "reminders",
                values,
                "id = ? AND owner_uid = ?",

                new String[]{
                        String.valueOf(reminderId),
                        getActiveOwnerUid()
                }
        );
    }


    // ============================================================
    // GARDEN NOTE
    // ============================================================

    public long addGardenNote(long gardenId, String noteDate, String note, String photoUri) {
        if (gardenId <= 0 || safe(note).isEmpty() || safe(noteDate).isEmpty()) return -1;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try (Cursor garden = getGarden(gardenId)) {
            if (!garden.moveToFirst()) return -1;
            ContentValues values = new ContentValues();
            values.put("garden_id", gardenId);
            values.put("note_date", safe(noteDate));
            values.put("note", safe(note));
            values.put("photo_uri", safe(photoUri));
            long now = System.currentTimeMillis();
            values.put("created_at", now);
            values.put("updated_at", now);
            String ownerUid = getActiveOwnerUid();
            values.put("owner_uid", ownerUid);
            values.put("cloud_id", ownerUid.isEmpty() ? "" : UUID.randomUUID().toString());
            long id = db.insert("garden_notes", null, values);
            if (id != -1) db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    public Cursor getGardenNotes(long gardenId) {
        ArrayList<String> args = new ArrayList<>();
        args.add(String.valueOf(gardenId));
        if (!getActiveOwnerUid().isEmpty()) {
            args.add(getActiveOwnerUid());
            args.add(getActiveOwnerUid());
        }
        return getReadableDatabase().rawQuery(
                "SELECT n.* FROM garden_notes n INNER JOIN gardens g ON g.id = n.garden_id " +
                "WHERE n.garden_id = ? AND " + activeOwnerCondition("g.owner_uid") +
                " AND " + activeOwnerCondition("n.owner_uid") +
                " ORDER BY n.created_at DESC, n.id DESC", args.toArray(new String[0]));
    }


    // ============================================================
    // SETTINGS
    // ============================================================

    public void setSetting(
            String key,
            String value
    ) {


        SQLiteDatabase db =
                getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "setting_key",
                safe(
                        key
                )
        );


        values.put(
                "setting_value",
                safe(
                        value
                )
        );


        db.insertWithOnConflict(
                "app_settings",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }


    public String getSetting(
            String key,
            String defaultValue
    ) {


        SQLiteDatabase db =
                getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT setting_value " +

                                    "FROM app_settings " +

                                    "WHERE setting_key = ?",

                            new String[]{
                                    safe(
                                            key
                                    )
                            }
                    );


            if (cursor.moveToFirst()) {


                String result =
                        cursor.getString(
                                0
                        );


                return result == null
                        ? defaultValue
                        : result;
            }


        } finally {


            if (cursor != null) {


                cursor.close();
            }
        }


        return defaultValue;
    }


    // ============================================================
    // ADD POST
    // ============================================================

    public long addPost(
            String userId,
            String userName,
            String province,
            String district,
            String neighborhood,
            Long gardenId,
            String gardenName,
            String variety,
            String postType,
            String content,
            String photoUri
    ) {


        userName =
                safe(
                        userName
                );


        content =
                safe(
                        content
                );


        if (userName.isEmpty()
                ||
                content.isEmpty()) {


            return -1;
        }


        SQLiteDatabase db =
                getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "cloud_id",
                UUID.randomUUID().toString()
        );

        values.put(
                "user_id",
                safe(
                        userId
                )
        );


        values.put(
                "user_name",
                userName
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


        if (gardenId != null
                &&
                gardenId > 0) {


            values.put(
                    "garden_id",
                    gardenId
            );
        }


        values.put(
                "garden_name",
                safe(
                        gardenName
                )
        );


        values.put(
                "variety",
                safe(
                        variety
                )
        );


        values.put(
                "post_type",
                safe(
                        postType
                ).isEmpty()
                        ? "GENERAL"
                        : postType.trim()
        );


        values.put(
                "content",
                content
        );


        values.put(
                "photo_uri",
                safe(
                        photoUri
                )
        );


        values.put(
                "useful_count",
                0
        );


        values.put(
                "not_useful_count",
                0
        );


        values.put(
                "comment_count",
                0
        );


        values.put(
                "is_real_sale",
                0
        );


        values.put(
                "is_active",
                1
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
        values.put("sync_state", 1);


        return db.insert(
                "posts",
                null,
                values
        );
    }


    // ============================================================
    // SOCIAL CLOUD BRIDGE
    // ============================================================

    public Cursor getPostById(long postId) {
        return getReadableDatabase().query(
                "posts", null, "id = ?", new String[]{String.valueOf(postId)},
                null, null, null, "1"
        );
    }

    public long upsertPostFromCloud(
            String cloudId, String userId, String userName,
            String province, String district, String neighborhood,
            String gardenName, String variety, String postType,
            String content, String photoUri, int isRealSale,
            String saleGrade, double saleKg, double salePrice,
            long createdAt, long updatedAt
    ) {
        cloudId = safe(cloudId);
        if (cloudId.isEmpty() || safe(content).isEmpty()) return -1;

        SQLiteDatabase db = getWritableDatabase();
        Long existingId = null;
        Cursor c = null;
        try {
            c = db.query("posts", new String[]{"id"}, "cloud_id = ?",
                    new String[]{cloudId}, null, null, null, "1");
            if (c.moveToFirst()) existingId = c.getLong(0);
        } finally {
            if (c != null) c.close();
        }

        ContentValues v = new ContentValues();
        v.put("cloud_id", cloudId);
        v.put("user_id", safe(userId));
        v.put("user_name", safe(userName).isEmpty() ? "Üretici" : safe(userName));
        v.put("province", safe(province));
        v.put("district", safe(district));
        v.put("neighborhood", safe(neighborhood));
        v.put("garden_name", safe(gardenName));
        v.put("variety", safe(variety));
        v.put("post_type", safe(postType).isEmpty() ? "GENERAL" : safe(postType));
        v.put("content", safe(content));
        v.put("photo_uri", safe(photoUri));
        v.put("is_real_sale", isRealSale == 1 ? 1 : 0);
        v.put("sale_grade", safe(saleGrade));
        v.put("sale_kg", Math.max(0, saleKg));
        v.put("sale_price", Math.max(0, salePrice));
        v.put("is_active", 1);
        v.put("created_at", createdAt > 0 ? createdAt : System.currentTimeMillis());
        v.put("updated_at", updatedAt > 0 ? updatedAt : System.currentTimeMillis());

        if (existingId != null) {
            db.update("posts", v, "id = ?", new String[]{String.valueOf(existingId)});
            return existingId;
        }
        return db.insert("posts", null, v);
    }

    public void replaceCloudPostMedia(long postId, java.util.List<String> urls) {
        if (postId <= 0) return;
        SQLiteDatabase db = getWritableDatabase();
        db.delete("post_media", "post_id = ?", new String[]{String.valueOf(postId)});
        if (urls == null) return;
        int order = 0;
        for (String url : urls) {
            if (safe(url).isEmpty()) continue;
            ContentValues v = new ContentValues();
            v.put("post_id", postId);
            v.put("media_type", "IMAGE");
            v.put("media_uri", url);
            v.put("sort_order", order++);
            v.put("created_at", System.currentTimeMillis());
            db.insert("post_media", null, v);
        }
    }

    // ============================================================
    // REAL SALE POST
    // ============================================================

    public long addRealSalePost(
            String userId,
            String userName,
            String province,
            String district,
            String neighborhood,
            Long gardenId,
            String gardenName,
            String variety,
            long saleId,
            String grade,
            double kg,
            double pricePerKg,
            String content
    ) {


        if (safe(
                userName
        ).isEmpty()) {


            return -1;
        }


        SQLiteDatabase db =
                getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "cloud_id",
                UUID.randomUUID().toString()
        );

        values.put(
                "user_id",
                safe(
                        userId
                )
        );


        values.put(
                "user_name",
                safe(
                        userName
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


        if (gardenId != null
                &&
                gardenId > 0) {


            values.put(
                    "garden_id",
                    gardenId
            );
        }


        values.put(
                "garden_name",
                safe(
                        gardenName
                )
        );


        values.put(
                "variety",
                safe(
                        variety
                )
        );


        values.put(
                "post_type",
                "SALE"
        );


        values.put(
                "content",
                safe(
                        content
                )
        );


        values.put(
                "useful_count",
                0
        );


        values.put(
                "not_useful_count",
                0
        );


        values.put(
                "comment_count",
                0
        );


        values.put(
                "is_real_sale",
                1
        );


        values.put(
                "sale_id",
                saleId
        );


        values.put(
                "sale_grade",
                safe(
                        grade
                )
        );


        values.put(
                "sale_kg",
                Math.max(
                        kg,
                        0
                )
        );


        values.put(
                "sale_price",
                Math.max(
                        pricePerKg,
                        0
                )
        );


        values.put(
                "is_active",
                1
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
                "posts",
                null,
                values
        );
    }


    // ============================================================
    // POSTS
    // ============================================================

    public Cursor getLatestPosts(
            int limit
    ) {


        if (limit <= 0) {


            limit =
                    20;
        }


        SQLiteDatabase db =
                getReadableDatabase();


        return db.rawQuery(

                "SELECT * " +

                        "FROM posts " +

                        "WHERE is_active = 1 " +

                        "ORDER BY created_at DESC " +

                        "LIMIT ?",

                new String[]{
                        String.valueOf(
                                limit
                        )
                }
        );
    }


    public Cursor getPostsByDistrict(
            String district,
            int limit
    ) {


        district =
                safe(
                        district
                );


        if (district.isEmpty()) {


            return getLatestPosts(
                    limit
            );
        }


        if (limit <= 0) {


            limit =
                    20;
        }


        SQLiteDatabase db =
                getReadableDatabase();


        return db.rawQuery(

                "SELECT * " +

                        "FROM posts " +

                        "WHERE is_active = 1 " +

                        "AND district = ? COLLATE NOCASE " +

                        "ORDER BY created_at DESC " +

                        "LIMIT ?",

                new String[]{

                        district,

                        String.valueOf(
                                limit
                        )
                }
        );
    }


    public Cursor getPostsByType(
            String postType,
            int limit
    ) {


        postType =
                safe(
                        postType
                );


        if (postType.isEmpty()) {


            return getLatestPosts(
                    limit
            );
        }


        if (limit <= 0) {


            limit =
                    20;
        }


        SQLiteDatabase db =
                getReadableDatabase();


        return db.rawQuery(

                "SELECT * " +

                        "FROM posts " +

                        "WHERE is_active = 1 " +

                        "AND post_type = ? " +

                        "ORDER BY created_at DESC " +

                        "LIMIT ?",

                new String[]{

                        postType,

                        String.valueOf(
                                limit
                        )
                }
        );
    }


    // ============================================================
    // REACTION RESULT
    // ============================================================

    public static class ReactionResult {


        public final boolean success;


        /*
         * NONE
         * LIKE
         * LOVE
         * CARE
         * HAHA
         * WOW
         * SAD
         * ANGRY
         */
        public final String reactionType;


        /*
         * Gönderinin toplam reaction sayısı.
         */
        public final int totalCount;


        public ReactionResult(
                boolean success,
                String reactionType,
                int totalCount
        ) {


            this.success =
                    success;


            this.reactionType =
                    reactionType;


            this.totalCount =
                    totalCount;
        }
    }


    // ============================================================
    // VALID REACTION
    // ============================================================

    private boolean isValidReaction(
            String reaction
    ) {


        reaction =
                safe(
                        reaction
                )
                        .toUpperCase();


        switch (reaction) {


            case "LIKE":

            case "LOVE":

            case "CARE":

            case "HAHA":

            case "WOW":

            case "SAD":

            case "ANGRY":


                return true;


            default:


                return false;
        }
    }


    // ============================================================
    // TOGGLE POST REACTION
    // ============================================================

    public ReactionResult togglePostReaction(
            long postId,
            String userKey,
            String requestedReaction
    ) {


        if (postId <= 0) {


            return new ReactionResult(
                    false,
                    "NONE",
                    0
            );
        }


        userKey =
                safe(
                        userKey
                );


        requestedReaction =
                safe(
                        requestedReaction
                )
                        .toUpperCase();


        if (userKey.isEmpty()
                ||
                !isValidReaction(
                        requestedReaction
                )) {


            return new ReactionResult(
                    false,
                    "NONE",
                    0
            );
        }


        SQLiteDatabase db =
                getWritableDatabase();


        Cursor cursor =
                null;


        db.beginTransaction();


        try {


            // ====================================================
            // MEVCUT REACTION
            // ====================================================

            String currentReaction =
                    "NONE";


            cursor =
                    db.rawQuery(

                            "SELECT reaction_type " +

                                    "FROM post_reactions " +

                                    "WHERE post_id = ? " +

                                    "AND user_key = ? " +

                                    "LIMIT 1",

                            new String[]{

                                    String.valueOf(
                                            postId
                                    ),

                                    userKey
                            }
                    );


            if (cursor.moveToFirst()) {


                currentReaction =
                        safe(
                                cursor.getString(
                                        0
                                )
                        )
                                .toUpperCase();
            }


            cursor.close();


            cursor =
                    null;


            String finalReaction;


            // ====================================================
            // AYNI REACTION'A TEKRAR BASILDI
            // ====================================================

            if (requestedReaction.equals(
                    currentReaction
            )) {


                int deleted =
                        db.delete(

                                "post_reactions",

                                "post_id = ? AND user_key = ?",

                                new String[]{

                                        String.valueOf(
                                                postId
                                        ),

                                        userKey
                                }
                        );


                if (deleted <= 0) {


                    throw new RuntimeException(
                            "Reaction kaldırılamadı"
                    );
                }


                finalReaction =
                        "NONE";


            } else {


                // =================================================
                // YENİ REACTION / REACTION DEĞİŞTİR
                // =================================================

                long now =
                        System.currentTimeMillis();


                ContentValues values =
                        new ContentValues();


                values.put(
                        "post_id",
                        postId
                );


                values.put(
                        "user_key",
                        userKey
                );


                values.put(
                        "reaction_type",
                        requestedReaction
                );


                values.put(
                        "created_at",
                        now
                );


                values.put(
                        "updated_at",
                        now
                );


                long result =
                        db.insertWithOnConflict(

                                "post_reactions",

                                null,

                                values,

                                SQLiteDatabase.CONFLICT_REPLACE
                        );


                if (result == -1) {


                    throw new RuntimeException(
                            "Reaction kaydedilemedi"
                    );
                }


                finalReaction =
                        requestedReaction;
            }


            // ====================================================
            // TOPLAM
            // ====================================================

            int totalCount =
                    getTotalReactionCount(
                            db,
                            postId
                    );


            // ====================================================
            // LEGACY LIKE COUNT
            // ====================================================

            int likeCount =
                    getReactionCount(
                            db,
                            postId,
                            "LIKE"
                    );


            ContentValues postValues =
                    new ContentValues();


            postValues.put(
                    "useful_count",
                    likeCount
            );


            postValues.put(
                    "not_useful_count",
                    0
            );


            postValues.put(
                    "updated_at",
                    System.currentTimeMillis()
            );


            db.update(
                    "posts",
                    postValues,
                    "id = ?",

                    new String[]{
                            String.valueOf(
                                    postId
                            )
                    }
            );


            db.setTransactionSuccessful();


            return new ReactionResult(
                    true,
                    finalReaction,
                    totalCount
            );


        } catch (Exception e) {


            e.printStackTrace();


            return new ReactionResult(
                    false,
                    "NONE",
                    0
            );


        } finally {


            if (cursor != null) {


                cursor.close();
            }


            db.endTransaction();
        }
    }


    // ============================================================
    // INTERNAL REACTION COUNT
    // ============================================================

    private int getReactionCount(
            SQLiteDatabase db,
            long postId,
            String reactionType
    ) {


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT COUNT(*) " +

                                    "FROM post_reactions " +

                                    "WHERE post_id = ? " +

                                    "AND reaction_type = ?",

                            new String[]{

                                    String.valueOf(
                                            postId
                                    ),

                                    reactionType
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
    // INTERNAL TOTAL REACTION COUNT
    // ============================================================

    private int getTotalReactionCount(
            SQLiteDatabase db,
            long postId
    ) {


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT COUNT(*) " +

                                    "FROM post_reactions " +

                                    "WHERE post_id = ? " +

                                    "AND reaction_type IN (" +

                                    "'LIKE'," +

                                    "'LOVE'," +

                                    "'CARE'," +

                                    "'HAHA'," +

                                    "'WOW'," +

                                    "'SAD'," +

                                    "'ANGRY'" +

                                    ")",

                            new String[]{
                                    String.valueOf(
                                            postId
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
    // PUBLIC TOTAL REACTION COUNT
    // ============================================================

    public int getPostTotalReactionCount(
            long postId
    ) {


        if (postId <= 0) {


            return 0;
        }


        SQLiteDatabase db =
                getReadableDatabase();


        return getTotalReactionCount(
                db,
                postId
        );
    }


    // ============================================================
    // PUBLIC SINGLE REACTION COUNT
    // ============================================================

    public int getPostReactionCount(
            long postId,
            String reactionType
    ) {


        if (postId <= 0) {


            return 0;
        }


        reactionType =
                safe(
                        reactionType
                )
                        .toUpperCase();


        if (!isValidReaction(
                reactionType
        )) {


            return 0;
        }


        SQLiteDatabase db =
                getReadableDatabase();


        return getReactionCount(
                db,
                postId,
                reactionType
        );
    }


    // ============================================================
    // USER REACTION
    // ============================================================

    public String getPostReactionByUser(
            long postId,
            String userKey
    ) {


        if (postId <= 0) {


            return "NONE";
        }


        userKey =
                safe(
                        userKey
                );


        if (userKey.isEmpty()) {


            return "NONE";
        }


        SQLiteDatabase db =
                getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT reaction_type " +

                                    "FROM post_reactions " +

                                    "WHERE post_id = ? " +

                                    "AND user_key = ? " +

                                    "LIMIT 1",

                            new String[]{

                                    String.valueOf(
                                            postId
                                    ),

                                    userKey
                            }
                    );


            if (cursor.moveToFirst()) {


                String reaction =
                        safe(
                                cursor.getString(
                                        0
                                )
                        )
                                .toUpperCase();


                if (isValidReaction(
                        reaction
                )) {


                    return reaction;
                }
            }


        } finally {


            if (cursor != null) {


                cursor.close();
            }
        }


        return "NONE";
    }


    // ============================================================
    // SAVE TOGGLE
    // ============================================================

    public boolean togglePostSave(
            long postId,
            String userKey
    ) {


        if (postId <= 0) {


            return false;
        }


        userKey =
                safe(
                        userKey
                );


        if (userKey.isEmpty()) {


            return false;
        }


        SQLiteDatabase db =
                getWritableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT id " +

                                    "FROM post_saves " +

                                    "WHERE post_id = ? " +

                                    "AND user_key = ? " +

                                    "LIMIT 1",

                            new String[]{

                                    String.valueOf(
                                            postId
                                    ),

                                    userKey
                            }
                    );


            boolean alreadySaved =
                    cursor.moveToFirst();


            cursor.close();


            cursor =
                    null;


            if (alreadySaved) {


                db.delete(

                        "post_saves",

                        "post_id = ? AND user_key = ?",

                        new String[]{

                                String.valueOf(
                                        postId
                                ),

                                userKey
                        }
                );


                return false;
            }


            ContentValues values =
                    new ContentValues();


            values.put(
                    "post_id",
                    postId
            );


            values.put(
                    "user_key",
                    userKey
            );


            values.put(
                    "created_at",
                    System.currentTimeMillis()
            );


            long result =
                    db.insert(
                            "post_saves",
                            null,
                            values
                    );


            return result != -1;


        } finally {


            if (cursor != null) {


                cursor.close();
            }
        }
    }


    public boolean isPostSavedByUser(
            long postId,
            String userKey
    ) {


        if (postId <= 0) {


            return false;
        }


        userKey =
                safe(
                        userKey
                );


        if (userKey.isEmpty()) {


            return false;
        }


        SQLiteDatabase db =
                getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT 1 " +

                                    "FROM post_saves " +

                                    "WHERE post_id = ? " +

                                    "AND user_key = ? " +

                                    "LIMIT 1",

                            new String[]{

                                    String.valueOf(
                                            postId
                                    ),

                                    userKey
                            }
                    );


            return cursor.moveToFirst();


        } finally {


            if (cursor != null) {


                cursor.close();
            }
        }
    }


    public Cursor getSavedPosts(
            String userKey,
            int limit
    ) {


        if (limit <= 0) {


            limit =
                    20;
        }


        SQLiteDatabase db =
                getReadableDatabase();


        return db.rawQuery(

                "SELECT p.* " +

                        "FROM post_saves s " +

                        "INNER JOIN posts p " +
                        "ON p.id = s.post_id " +

                        "WHERE s.user_key = ? " +

                        "AND p.is_active = 1 " +

                        "ORDER BY s.created_at DESC " +

                        "LIMIT ?",

                new String[]{

                        safe(
                                userKey
                        ),

                        String.valueOf(
                                limit
                        )
                }
        );
    }


    // ============================================================
    // COMMENTS
    // ============================================================

    public long addPostComment(long postId, String userKey, String userName, String content) {
        return addPostComment(postId, userKey, userName, content, "");
    }

    public long addPostComment(
            long postId,
            String userKey,
            String userName,
            String content,
            String parentCloudId
    ) {

        if (postId <= 0) {
            return -1;
        }

        userKey =
                safe(userKey);

        userName =
                safe(userName);

        content =
                safe(content);

        if (userName.isEmpty()
                || content.isEmpty()) {

            return -1;
        }

        SQLiteDatabase db =
                getWritableDatabase();

        db.beginTransaction();

        try {

            /*
             * Yorum yapılacak gönderi gerçekten
             * yerel posts tablosunda var mı?
             */
            Cursor postCursor = null;

            try {

                postCursor = db.rawQuery(
                        "SELECT id FROM posts " +
                                "WHERE id = ? AND is_active = 1 LIMIT 1",
                        new String[]{
                                String.valueOf(postId)
                        }
                );

                if (!postCursor.moveToFirst()) {
                    return -1;
                }

            } finally {

                if (postCursor != null) {
                    postCursor.close();
                }
            }


            ContentValues values =
                    new ContentValues();

            String cloudId = UUID.randomUUID().toString();
            values.put("cloud_id", cloudId);
            parentCloudId = safe(parentCloudId);
            values.put("parent_comment_id", parentCloudId);
            values.put("root_comment_id", cloudId);
            values.put("reply_to_uid", "");
            values.put("reply_to_name", "");
            if (!parentCloudId.isEmpty()) {
                try (Cursor parent = db.rawQuery(
                        "SELECT * FROM post_comments WHERE cloud_id = ? AND post_id = ? AND is_active = 1",
                        new String[]{parentCloudId, String.valueOf(postId)})) {
                    if (!parent.moveToFirst()) return -1;
                    String rootId = parent.getString(parent.getColumnIndexOrThrow("root_comment_id"));
                    values.put("root_comment_id", safe(rootId).isEmpty() ? parentCloudId : rootId);
                    values.put("reply_to_uid", parent.getString(parent.getColumnIndexOrThrow("user_key")));
                    values.put("reply_to_name", parent.getString(parent.getColumnIndexOrThrow("user_name")));
                }
            }

            values.put(
                    "post_id",
                    postId
            );

            values.put(
                    "user_key",
                    userKey
            );

            values.put(
                    "user_name",
                    userName
            );

            values.put(
                    "content",
                    content
            );

            values.put(
                    "is_active",
                    1
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
        values.put("sync_state", 1);

            long commentId =
                    db.insert(
                            "post_comments",
                            null,
                            values
                    );

            if (commentId == -1) {

                throw new RuntimeException(
                        "Yanıt kaydedilemedi"
                );
            }

            updatePostCommentCount(
                    db,
                    postId
            );

            db.setTransactionSuccessful();

            return commentId;

        } catch (Exception e) {

            e.printStackTrace();

            return -1;

        } finally {

            db.endTransaction();
        }
    }


    private void updatePostCommentCount(
            SQLiteDatabase db,
            long postId
    ) {


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT COUNT(*) " +

                                    "FROM post_comments " +

                                    "WHERE post_id = ? " +

                                    "AND is_active = 1",

                            new String[]{
                                    String.valueOf(
                                            postId
                                    )
                            }
                    );


            int count =
                    0;


            if (cursor.moveToFirst()) {


                count =
                        cursor.getInt(
                                0
                        );
            }


            ContentValues values =
                    new ContentValues();


            values.put(
                    "comment_count",
                    count
            );


            values.put(
                    "updated_at",
                    System.currentTimeMillis()
            );


            db.update(
                    "posts",
                    values,
                    "id = ?",

                    new String[]{
                            String.valueOf(
                                    postId
                            )
                    }
            );


        } finally {


            if (cursor != null) {


                cursor.close();
            }
        }
    }


    public Cursor getPostComments(
            long postId
    ) {


        SQLiteDatabase db =
                getReadableDatabase();


        return db.rawQuery(

                "SELECT * " +

                        "FROM post_comments " +

                        "WHERE post_id = ? " +

                        "AND is_active = 1 " +

                        "ORDER BY created_at ASC",

                new String[]{
                        String.valueOf(
                                postId
                        )
                }
        );
    }


    public boolean deletePostComment(
            long commentId,
            String userKey
    ) {


        if (commentId <= 0) {


            return false;
        }


        SQLiteDatabase db =
                getWritableDatabase();


        Cursor cursor =
                null;


        db.beginTransaction();


        try {


            cursor =
                    db.rawQuery(

                            "SELECT post_id, user_key " +

                                    "FROM post_comments " +

                                    "WHERE id = ? " +

                                    "LIMIT 1",

                            new String[]{
                                    String.valueOf(
                                            commentId
                                    )
                            }
                    );


            if (!cursor.moveToFirst()) {


                return false;
            }


            long postId =
                    cursor.getLong(
                            0
                    );


            String ownerKey =
                    safe(
                            cursor.getString(
                                    1
                            )
                    );


            if (!ownerKey.equals(
                    safe(
                            userKey
                    )
            )) {


                return false;
            }


            ContentValues values =
                    new ContentValues();


            values.put(
                    "is_active",
                    0
            );


            values.put(
                    "updated_at",
                    System.currentTimeMillis()
            );
        values.put("sync_state", 1);


            int result =
                    db.update(
                            "post_comments",
                            values,
                            "id = ?",

                            new String[]{
                                    String.valueOf(
                                            commentId
                                    )
                            }
                    );


            if (result <= 0) {


                return false;
            }


            updatePostCommentCount(
                    db,
                    postId
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
    // COMMENT BY ID
    // ============================================================

    public Cursor getPostCommentById(
            long commentId
    ) {

        return getReadableDatabase().rawQuery(
                "SELECT " +
                        "c.*, " +
                        "p.cloud_id AS post_cloud_id " +
                        "FROM post_comments c " +
                        "INNER JOIN posts p ON p.id = c.post_id " +
                        "WHERE c.id = ? " +
                        "LIMIT 1",
                new String[]{
                        String.valueOf(commentId)
                }
        );
    }


    // ============================================================
    // COMMENT CLOUD ID
    // ============================================================

    public Cursor getPostCommentByCloudId(
            String cloudId
    ) {

        return getReadableDatabase().rawQuery(
                "SELECT " +
                        "c.*, " +
                        "p.cloud_id AS post_cloud_id " +
                        "FROM post_comments c " +
                        "INNER JOIN posts p ON p.id = c.post_id " +
                        "WHERE c.cloud_id = ? " +
                        "LIMIT 1",
                new String[]{
                        safe(cloudId)
                }
        );
    }


    // ============================================================
    // POST CLOUD ID
    // ============================================================

    public String getPostCloudId(
            long postId
    ) {

        if (postId <= 0) {
            return "";
        }

        Cursor cursor = null;

        try {

            cursor =
                    getReadableDatabase().rawQuery(
                            "SELECT cloud_id FROM posts " +
                                    "WHERE id = ? LIMIT 1",
                            new String[]{
                                    String.valueOf(postId)
                            }
                    );

            if (cursor.moveToFirst()) {
                return safe(
                        cursor.getString(0)
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
    // LOCAL POST ID BY CLOUD ID
    // ============================================================

    public long getLocalPostIdByCloudId(
            String cloudId
    ) {

        cloudId =
                safe(cloudId);

        if (cloudId.isEmpty()) {
            return -1;
        }

        Cursor cursor = null;

        try {

            cursor =
                    getReadableDatabase().rawQuery(
                            "SELECT id FROM posts " +
                                    "WHERE cloud_id = ? LIMIT 1",
                            new String[]{
                                    cloudId
                            }
                    );

            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }

        } finally {

            if (cursor != null) {
                cursor.close();
            }
        }

        return -1;
    }


    // ============================================================
    // COMMENT CLOUD UPSERT
    // ============================================================

    public long upsertPostCommentFromCloud(String postCloudId, String commentCloudId,
            String userKey, String userName, String content, int isActive, long createdAt, long updatedAt) {
        return upsertPostCommentFromCloud(postCloudId, commentCloudId, userKey, userName,
                content, isActive, createdAt, updatedAt, "", commentCloudId, "", "");
    }

    public long upsertPostCommentFromCloud(
            String postCloudId,
            String commentCloudId,
            String userKey,
            String userName,
            String content,
            int isActive,
            long createdAt,
            long updatedAt,
            String parentId, String rootId, String replyUid, String replyName
    ) {

        postCloudId =
                safe(postCloudId);

        commentCloudId =
                safe(commentCloudId);

        userKey =
                safe(userKey);

        userName =
                safe(userName);

        content =
                safe(content);

        if (postCloudId.isEmpty()
                || commentCloudId.isEmpty()) {

            return -1;
        }

        long localPostId =
                getLocalPostIdByCloudId(
                        postCloudId
                );

        if (localPostId <= 0) {

            /*
             * Ana gönderi henüz cihaza inmedi.
             * Önce post sync tamamlanmalı.
             */
            return -1;
        }

        SQLiteDatabase db =
                getWritableDatabase();

        Cursor cursor = null;

        Long existingId =
                null;

        long existingUpdatedAt =
                0;

        try {

            cursor =
                    db.rawQuery(
                            "SELECT id, updated_at " +
                                    "FROM post_comments " +
                                    "WHERE cloud_id = ? LIMIT 1",
                            new String[]{
                                    commentCloudId
                            }
                    );

            if (cursor.moveToFirst()) {

                existingId =
                        cursor.getLong(0);

                existingUpdatedAt =
                        cursor.getLong(1);
            }

        } finally {

            if (cursor != null) {
                cursor.close();
            }
        }


        long safeCreatedAt =
                createdAt > 0
                        ? createdAt
                        : System.currentTimeMillis();

        long safeUpdatedAt =
                updatedAt > 0
                        ? updatedAt
                        : safeCreatedAt;


        /*
         * Daha yeni yerel kayıt varsa eski remote veriyle ezme.
         */



        ContentValues values =
                new ContentValues();

        values.put(
                "cloud_id",
                commentCloudId
        );

        values.put(
                "post_id",
                localPostId
        );

        values.put(
                "user_key",
                userKey
        );

        values.put(
                "user_name",
                userName.isEmpty()
                        ? "Üretici"
                        : userName
        );

        values.put(
                "content",
                content
        );

        values.put(
                "is_active",
                isActive == 1
                        ? 1
                        : 0
        );

        values.put(
                "created_at",
                safeCreatedAt
        );

        values.put(
                "updated_at",
                safeUpdatedAt
        );


        values.put("parent_comment_id", safe(parentId));
        values.put("root_comment_id", safe(rootId).isEmpty() ? commentCloudId : safe(rootId));
        values.put("reply_to_uid", safe(replyUid));
        values.put("reply_to_name", safe(replyName));
        long localCommentId;

        if (existingId != null) {

            db.update(
                    "post_comments",
                    values,
                    "id = ?",
                    new String[]{
                            String.valueOf(existingId)
                    }
            );

            localCommentId =
                    existingId;

        } else {

            localCommentId =
                    db.insert(
                            "post_comments",
                            null,
                            values
                    );
        }


        if (localCommentId > 0) {

            updatePostCommentCount(
                    db,
                    localPostId
            );
        }

        return localCommentId;
    }


    // ============================================================
    // COMMENT CLOUD DELETE APPLY
    // ============================================================

    public boolean applyCloudCommentDeletion(
            String commentCloudId,
            long updatedAt
    ) {

        commentCloudId =
                safe(commentCloudId);

        if (commentCloudId.isEmpty()) {

            return false;
        }

        SQLiteDatabase db =
                getWritableDatabase();

        Cursor cursor = null;

        db.beginTransaction();

        try {

            cursor =
                    db.rawQuery(
                            "SELECT id, post_id, updated_at " +
                                    "FROM post_comments " +
                                    "WHERE cloud_id = ? LIMIT 1",
                            new String[]{
                                    commentCloudId
                            }
                    );

            if (!cursor.moveToFirst()) {

                return false;
            }

            long localCommentId =
                    cursor.getLong(0);

            long postId =
                    cursor.getLong(1);

            long localUpdatedAt =
                    cursor.getLong(2);

            if (updatedAt > 0
                    && localUpdatedAt > updatedAt) {

                return false;
            }

            ContentValues values =
                    new ContentValues();

            values.put(
                    "is_active",
                    0
            );

            values.put(
                    "updated_at",
                    updatedAt > 0
                            ? updatedAt
                            : System.currentTimeMillis()
            );

            int result =
                    db.update(
                            "post_comments",
                            values,
                            "id = ?",
                            new String[]{
                                    String.valueOf(localCommentId)
                            }
                    );

            if (result <= 0) {

                return false;
            }

            updatePostCommentCount(
                    db,
                    postId
            );

            db.setTransactionSuccessful();

            return true;

        } finally {

            if (cursor != null) {
                cursor.close();
            }

            db.endTransaction();
        }
    }


    // ============================================================
    // POST MEDIA
    // ============================================================

    public long addPostMedia(
            long postId,
            String mediaType,
            String mediaUri,
            int sortOrder
    ) {


        if (postId <= 0) {


            return -1;
        }


        mediaType =
                safe(
                        mediaType
                )
                        .toUpperCase();


        mediaUri =
                safe(
                        mediaUri
                );


        if (mediaUri.isEmpty()) {


            return -1;
        }


        if (!"IMAGE".equals(
                mediaType
        )
                &&
                !"VIDEO".equals(
                        mediaType
                )) {


            return -1;
        }


        SQLiteDatabase db =
                getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "post_id",
                postId
        );


        values.put(
                "media_type",
                mediaType
        );


        values.put(
                "media_uri",
                mediaUri
        );


        values.put(
                "sort_order",
                Math.max(
                        sortOrder,
                        0
                )
        );


        values.put(
                "created_at",
                System.currentTimeMillis()
        );


        return db.insert(
                "post_media",
                null,
                values
        );
    }


    public Cursor getPostMedia(
            long postId
    ) {


        SQLiteDatabase db =
                getReadableDatabase();


        return db.rawQuery(

                "SELECT * " +

                        "FROM post_media " +

                        "WHERE post_id = ? " +

                        "ORDER BY sort_order ASC, id ASC",

                new String[]{
                        String.valueOf(
                                postId
                        )
                }
        );
    }


    public int deletePostMedia(
            long mediaId
    ) {


        SQLiteDatabase db =
                getWritableDatabase();


        return db.delete(
                "post_media",
                "id = ?",

                new String[]{
                        String.valueOf(
                                mediaId
                        )
                }
        );
    }


    // ============================================================
    // DELETE POST
    // ============================================================

    /**
     * Gönderiyi telefonda gizler. Buluta yüklenmiş bir gönderiyse silme "bekliyor" (sync_state=1)
     * olarak işaretlenir; SocialCloudRepository bunu buluta is_active=0 olarak yazar. Böylece gönderi
     * diğer üreticilerden de kalkar ve sonraki senkronda geri gelmez.
     */
    public int deletePost(
            long postId
    ) {
        SQLiteDatabase db = getWritableDatabase();
        String cloudId = "";
        try (Cursor c = db.query("posts", new String[]{"cloud_id"}, "id = ?",
                new String[]{String.valueOf(postId)}, null, null, null, "1")) {
            if (c.moveToFirst()) cloudId = safe(c.getString(0));
        }
        ContentValues values = new ContentValues();
        values.put("is_active", 0);
        values.put("updated_at", System.currentTimeMillis());
        values.put("sync_state", cloudId.isEmpty() ? 2 : 1);
        return db.update("posts", values, "id = ?", new String[]{String.valueOf(postId)});
    }

    /**
     * Bu gönderide buluta henüz yazılmamış yerel değişiklik (yeni mesaj, düzenleme ya da silme) var mı?
     * Varsa buluttan gelen kopya yerel kaydın üzerine yazılmaz; yazılırsa updated_at değişir ve
     * gönderim onayı kaydı "senkron" yapamaz (mesaj "Gönderiliyor…" olarak takılı kalır).
     */
    public boolean isPostLocalChangePending(String cloudId) {
        if (safe(cloudId).isEmpty()) return false;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM posts WHERE cloud_id = ? AND sync_state = 1 LIMIT 1",
                new String[]{safe(cloudId)})) {
            return c.moveToFirst();
        }
    }

    /**
     * Bulutta silinmiş (is_active=0 ya da hiç yok; moderatör kaldırması dahil) gönderileri telefonda
     * kapatır. Bekleyen yerel değişiklik de kapanır: bulut zaten kapalıysa silme isteği tamamlanmıştır,
     * kapalı gönderiye yazma ise kurallarca reddedilir ve sonsuza dek yeniden denenirdi.
     */
    public int deactivatePostsFromCloud(java.util.Collection<String> cloudIds) {
        if (cloudIds == null || cloudIds.isEmpty()) return 0;
        SQLiteDatabase db = getWritableDatabase();
        int changed = 0;
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("is_active", 0);
            values.put("sync_state", 2);
            for (String cloudId : cloudIds) {
                if (safe(cloudId).isEmpty()) continue;
                changed += db.update("posts", values, "cloud_id = ? AND (is_active = 1 OR sync_state = 1)",
                        new String[]{safe(cloudId)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return changed;
    }

    /** Eşleştirme için telefondaki etkin, bulut kimliği olan gönderiler. */
    public java.util.List<MeydanPostSync.LocalPost> getActiveCloudPostsForSync() {
        java.util.List<MeydanPostSync.LocalPost> out = new java.util.ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT cloud_id, created_at, COALESCE(sync_state, 2) FROM posts "
                        + "WHERE is_active = 1 AND TRIM(COALESCE(cloud_id, '')) <> ''", null)) {
            while (c.moveToNext()) out.add(new MeydanPostSync.LocalPost(c.getString(0), c.getLong(1), c.getInt(2)));
        }
        return out;
    }

    /** Silmesi buluta yazılmayı bekleyen kendi gönderilerinin yerel kimlikleri. */
    public java.util.List<Long> getPendingPostDeletionIds(String userId) {
        java.util.List<Long> out = new java.util.ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id FROM posts WHERE user_id = ? AND is_active = 0 AND sync_state = 1 "
                        + "AND TRIM(COALESCE(cloud_id, '')) <> '' ORDER BY id LIMIT 50", new String[]{safe(userId)})) {
            while (c.moveToNext()) out.add(c.getLong(0));
        }
        return out;
    }


    // ============================================================
    // REPORT
    // ============================================================

    public double getTotalCost() {


        return getTotalExpenses();
    }


    public double getTotalExpenses() {


        return getOperationCostTotal()
                +
                getTotalHarvestCost()
                +
                getTotalBrineCost();
    }


    public double getNetProfit() {


        return getTotalSalesRevenue()
                -
                getTotalExpenses();
    }


    public Cursor getOperationsFiltered(
            Integer gardenId,
            String type
    ) {


        SQLiteDatabase db =
                getReadableDatabase();


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
                        "o.created_at," +

                        "g.name AS garden_name " +

                        "FROM operations o " +

                        "INNER JOIN gardens g " +
                        "ON g.id = o.garden_id " +

                        "WHERE 1 = 1";


        ArrayList<String> args =
                new ArrayList<>();

        sql += " AND " + activeOwnerCondition("g.owner_uid");
        if (!getActiveOwnerUid().isEmpty()) args.add(getActiveOwnerUid());
        // Bahçe geçmişiyle (getGardenOperations) aynı kural: açıkça başka sahibe ait işlem kaydı gösterilmez;
        // sahibi boş eski kayıtlar bahçenin sahibini izler.
        sql += " AND (COALESCE(o.owner_uid,'') = ? OR COALESCE(o.owner_uid,'') = '')";
        args.add(getActiveOwnerUid());
        sql += " AND " + activeSeasonCondition("o", "operation_date");


        if (gardenId != null) {


            sql +=
                    " AND o.garden_id = ?";


            args.add(
                    String.valueOf(
                            gardenId
                    )
            );
        }


        if (!safe(
                type
        ).isEmpty()) {


            sql +=
                    " AND o.type = ?";


            args.add(
                    type.trim()
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


    public int getRealSaleCount() {


        SQLiteDatabase db =
                getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM sales s " +
                            "INNER JOIN gardens g ON g.id = s.garden_id " +
                            "WHERE s.sale_kind = 'SALE' AND " + activeOwnerCondition("g.owner_uid") +
                            " AND " + activeSeasonCondition("s", "sale_date"),
                    activeOwnerArgs()
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


    public double getTotalSoldKg() {


        return getDoubleValue(
                "SELECT COALESCE(SUM(s.total_kg), 0) FROM sales s " +
                        "INNER JOIN gardens g ON g.id = s.garden_id " +
                        "WHERE s.sale_kind = 'SALE' AND " + activeOwnerCondition("g.owner_uid") +
                        " AND " + activeSeasonCondition("s", "sale_date"),
                activeOwnerArgs()
        );
    }


    public double getTotalAmountReceived() {


        return getDoubleValue(
                "SELECT COALESCE(SUM(s.amount_received), 0) FROM sales s " +
                        "INNER JOIN gardens g ON g.id = s.garden_id " +
                        "WHERE s.sale_kind = 'SALE' AND " + activeOwnerCondition("g.owner_uid") +
                        " AND " + activeSeasonCondition("s", "sale_date"),
                activeOwnerArgs()
        );
    }


    public double getTotalOpenReceivableBalance() {


        return getDoubleValue(
                "SELECT COALESCE(SUM(" +
                        "CASE " +
                        "WHEN r.amount > r.total_received " +
                        "THEN r.amount - r.total_received " +
                        "ELSE 0 " +
                        "END" +
                        "), 0) " +
                        "FROM receivables r " +
                        "LEFT JOIN sales s ON s.id = r.related_sale_id " +
                        "WHERE r.status = 'OPEN' AND " + activeOwnerCondition("r.owner_uid") +
                        " AND ((s.id IS NOT NULL AND " + activeSeasonCondition("s", "sale_date") + ")" +
                        " OR (s.id IS NULL AND CAST(strftime('%Y', r.created_at / 1000, 'unixepoch') AS INTEGER) = " +
                        getActiveSeasonYear() + "))",
                activeOwnerArgs()
        );
    }


    public Cursor getGardenReportSummaries() {


        SQLiteDatabase db =
                getReadableDatabase();


        return db.rawQuery(

                "SELECT " +

                        "g.id," +
                        "g.name," +
                        "g.tree_count," +
                        "g.variety," +

                        "(" +
                        "SELECT COALESCE(SUM(h.total_kg), 0) " +
                        "FROM harvests h " +
                        "WHERE h.garden_id = g.id " +
                        "AND " + activeSeasonCondition("h", "harvest_date") +
                        ") AS harvest_kg," +

                        "(" +
                        "SELECT COALESCE(SUM(o.cost), 0) " +
                        "FROM operations o " +
                        "WHERE o.garden_id = g.id " +
                        "AND o.type <> 'Hasat' " +
                        "AND " + activeSeasonCondition("o", "operation_date") +
                        ") AS operation_cost," +

                        "(" +
                        "SELECT COALESCE(SUM(" +
                        "h2.labor_cost + " +
                        "h2.transport_cost + " +
                        "h2.other_cost" +
                        "), 0) " +
                        "FROM harvests h2 " +
                        "WHERE h2.garden_id = g.id " +
                        "AND " + activeSeasonCondition("h2", "harvest_date") +
                        ") AS harvest_cost," +

                        "(" +
                        "SELECT COALESCE(SUM(" +
                        "b.salt_cost + " +
                        "b.packaging_cost + " +
                        "b.other_cost" +
                        "), 0) " +
                        "FROM brine_batches b " +
                        "WHERE b.garden_id = g.id " +
                        "AND " + activeSeasonCondition("b", "start_date") +
                        ") AS brine_base_cost," +

                        "(" +
                        "SELECT COALESCE(SUM(e.cost), 0) " +
                        "FROM brine_events e " +
                        "INNER JOIN brine_batches b2 " +
                        "ON b2.id = e.batch_id " +
                        "WHERE b2.garden_id = g.id " +
                        "AND " + activeSeasonCondition("b2", "start_date") +
                        ") AS brine_event_cost," +

                        "(" +
                        "SELECT COALESCE(SUM(" +
                        "s.gross_total - " +
                        "s.transport_cost - " +
                        "s.deductions" +
                        "), 0) " +
                        "FROM sales s " +
                        "WHERE s.garden_id = g.id " +
                        "AND s.sale_kind = 'SALE' " +
                        "AND " + activeSeasonCondition("s", "sale_date") +
                        ") AS sales_revenue," +

                        "(" +
                        "SELECT COALESCE(SUM(s2.total_kg), 0) " +
                        "FROM sales s2 " +
                        "WHERE s2.garden_id = g.id " +
                        "AND s2.sale_kind = 'SALE' " +
                        "AND " + activeSeasonCondition("s2", "sale_date") +
                        ") AS sold_kg " +

                        "FROM gardens g " +

                        "WHERE " + activeOwnerCondition("g.owner_uid") + " " +

                        "ORDER BY g.name COLLATE NOCASE ASC",

                activeOwnerArgs()
        );
    }


    // ============================================================
    // ELEK SEASON
    // ============================================================

    public Cursor getElekSeasonArchive(int year) {
        return new ElekRepository(this).getArchiveSummary(year, getActiveOwnerUid());
    }


    // ============================================================
    // FINAL CLOUD SYNC HELPERS
    // ============================================================

    public long getCloudRemoteCursor(String ownerUid, String entityType, String cloudId) {
        String uid = safe(ownerUid);
        String type = safe(entityType);
        String cid = safe(cloudId);
        if (uid.isEmpty() || type.isEmpty() || cid.isEmpty()) return 0;
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT last_remote_at FROM sync_state " +
                            "WHERE owner_uid=? AND entity_type=? AND cloud_id=? LIMIT 1",
                    new String[]{uid, type, cid});
            return c.moveToFirst() ? c.getLong(0) : 0;
        } finally {
            if (c != null) c.close();
        }
    }

    public boolean shouldUploadCloudRecord(String ownerUid, String entityType,
                                           String cloudId, long localUpdatedAt) {
        String uid = safe(ownerUid);
        String type = safe(entityType);
        String cid = safe(cloudId);
        if (uid.isEmpty() || type.isEmpty() || cid.isEmpty()) return false;

        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT last_uploaded_at FROM sync_state " +
                            "WHERE owner_uid=? AND entity_type=? AND cloud_id=? LIMIT 1",
                    new String[]{uid, type, cid});
            if (!c.moveToFirst()) return true;
            return localUpdatedAt > c.getLong(0);
        } finally {
            if (c != null) c.close();
        }
    }

    public void markCloudUploadComplete(String ownerUid, String entityType,
                                        String cloudId, long updatedAt) {
        upsertSyncState(ownerUid, entityType, cloudId, updatedAt, -1);
    }

    public void markCloudRemoteApplied(String ownerUid, String entityType,
                                       String cloudId, long updatedAt) {
        upsertSyncState(ownerUid, entityType, cloudId, updatedAt, updatedAt);
    }

    private void upsertSyncState(String ownerUid, String entityType, String cloudId,
                                 long uploadedAt, long remoteAt) {
        String uid = safe(ownerUid);
        String type = safe(entityType);
        String cid = safe(cloudId);
        if (uid.isEmpty() || type.isEmpty() || cid.isEmpty()) return;

        SQLiteDatabase db = getWritableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery(
                    "SELECT last_uploaded_at,last_remote_at FROM sync_state " +
                            "WHERE owner_uid=? AND entity_type=? AND cloud_id=? LIMIT 1",
                    new String[]{uid, type, cid});
            long oldUploaded = 0;
            long oldRemote = 0;
            if (c.moveToFirst()) {
                oldUploaded = c.getLong(0);
                oldRemote = c.getLong(1);
            }
            ContentValues v = new ContentValues();
            v.put("owner_uid", uid);
            v.put("entity_type", type);
            v.put("cloud_id", cid);
            v.put("last_uploaded_at", uploadedAt >= 0 ? Math.max(oldUploaded, uploadedAt) : oldUploaded);
            v.put("last_remote_at", remoteAt >= 0 ? Math.max(oldRemote, remoteAt) : oldRemote);
            db.insertWithOnConflict("sync_state", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        } finally {
            if (c != null) c.close();
        }
    }

    public void recordCloudDeletion(String table, long localId) {
        if (localId <= 0) return;
        String safeTable = safe(table);
        if (!isSyncDeleteTable(safeTable)) return;

        SQLiteDatabase db = getWritableDatabase();
        Cursor c = null;
        try {
            String sql;
            if ("gardens".equals(safeTable)) {
                sql = "SELECT owner_uid,cloud_id,'' AS garden_cloud_id FROM gardens WHERE id=? LIMIT 1";
            } else if ("inventory_items".equals(safeTable) || "receivables".equals(safeTable)
                    || "reminders".equals(safeTable)) {
                sql = "SELECT owner_uid,cloud_id,'' AS garden_cloud_id FROM " + safeTable + " WHERE id=? LIMIT 1";
            } else if ("inventory_movements".equals(safeTable)) {
                sql = "SELECT owner_uid,cloud_id,'' AS garden_cloud_id FROM inventory_movements WHERE id=? LIMIT 1";
            } else {
                sql = "SELECT r.owner_uid,r.cloud_id,g.cloud_id AS garden_cloud_id FROM " + safeTable + " r " +
                        "LEFT JOIN gardens g ON g.id=r.garden_id WHERE r.id=? LIMIT 1";
            }
            c = db.rawQuery(sql, new String[]{String.valueOf(localId)});
            if (!c.moveToFirst()) return;
            String uid = safe(c.getString(0));
            String cid = safe(c.getString(1));
            String gardenCid = safe(c.getString(2));
            if (uid.isEmpty() || cid.isEmpty()) return;

            ContentValues v = new ContentValues();
            v.put("owner_uid", uid);
            v.put("entity_type", safeTable);
            v.put("cloud_id", cid);
            v.put("garden_cloud_id", gardenCid);
            v.put("deleted_at", System.currentTimeMillis());
            v.put("uploaded", 0);
            db.insertWithOnConflict("sync_tombstones", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        } finally {
            if (c != null) c.close();
        }
    }

    private boolean isSyncDeleteTable(String table) {
        return "gardens".equals(table) || "operations".equals(table)
                || "harvests".equals(table) || "elek_records".equals(table)
                || "sales".equals(table) || "brine_batches".equals(table)
                || "inventory_items".equals(table) || "inventory_movements".equals(table)
                || "receivables".equals(table) || "reminders".equals(table)
                || "garden_notes".equals(table);
    }

    public boolean hasCloudDeletionTombstone(String ownerUid, String entityType, String cloudId) {
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT 1 FROM sync_tombstones WHERE owner_uid=? AND entity_type=? AND cloud_id=? LIMIT 1",
                    new String[]{safe(ownerUid), safe(entityType), safe(cloudId)});
            return c.moveToFirst();
        } finally {
            if (c != null) c.close();
        }
    }

    public Cursor getPendingCloudDeletions(String ownerUid) {
        return getReadableDatabase().rawQuery(
                "SELECT * FROM sync_tombstones WHERE owner_uid=? AND uploaded=0 ORDER BY deleted_at ASC",
                new String[]{safe(ownerUid)});
    }

    public void markCloudDeletionUploaded(String ownerUid, String entityType, String cloudId) {
        ContentValues v = new ContentValues();
        v.put("uploaded", 1);
        getWritableDatabase().update("sync_tombstones", v,
                "owner_uid=? AND entity_type=? AND cloud_id=?",
                new String[]{safe(ownerUid), safe(entityType), safe(cloudId)});
    }

    public void applyRemoteDeletion(String ownerUid, String entityType, String cloudId, long deletedAt) {
        String uid = safe(ownerUid);
        String type = safe(entityType);
        String cid = safe(cloudId);
        if (uid.isEmpty() || type.isEmpty() || cid.isEmpty()) return;

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Cursor c = null;
            long localId = -1;
            try {
                c = db.rawQuery("SELECT id FROM " + type + " WHERE owner_uid=? AND cloud_id=? LIMIT 1",
                        new String[]{uid, cid});
                if (c.moveToFirst()) localId = c.getLong(0);
            } catch (Exception ignored) {
                // Bilinmeyen/eski entity türü güvenle yok sayılır.
            } finally {
                if (c != null) c.close();
            }
            if (localId > 0) {
                db.delete(type, "id=?", new String[]{String.valueOf(localId)});
            }

            ContentValues v = new ContentValues();
            v.put("owner_uid", uid);
            v.put("entity_type", type);
            v.put("cloud_id", cid);
            v.put("garden_cloud_id", "");
            v.put("deleted_at", deletedAt > 0 ? deletedAt : System.currentTimeMillis());
            v.put("uploaded", 1);
            db.insertWithOnConflict("sync_tombstones", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            db.delete("sync_state", "owner_uid=? AND entity_type=? AND cloud_id=?",
                    new String[]{uid, type, cid});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }


    private static void backfillSeasonYear(
            SQLiteDatabase db,
            String table,
            String dateColumn
    ) {
        db.execSQL(
                "UPDATE " + table + " SET season_year = CASE " +
                        "WHEN substr(TRIM(" + dateColumn + "), 5, 1) = '-' " +
                        "THEN CAST(substr(TRIM(" + dateColumn + "), 1, 4) AS INTEGER) " +
                        "ELSE CAST(substr(TRIM(" + dateColumn + "), -4) AS INTEGER) END " +
                        "WHERE COALESCE(season_year, 0) = 0"
        );
    }


    private int yearOfDate(String value) {
        String date = safe(value);
        if (date.length() >= 4) {
            try {
                if (date.length() >= 10 && date.charAt(4) == '-') {
                    return Integer.parseInt(date.substring(0, 4));
                }
                return Integer.parseInt(date.substring(date.length() - 4));
            } catch (Exception ignored) { }
        }
        return getActiveSeasonYear();
    }


    private String activeOwnerCondition(String qualifiedColumn) {
        return getActiveOwnerUid().isEmpty()
                ? "(" + qualifiedColumn + " IS NULL OR TRIM(" + qualifiedColumn + ") = '')"
                : qualifiedColumn + " = ?";
    }


    private String[] activeOwnerArgs() {
        return getActiveOwnerUid().isEmpty()
                ? null
                : new String[]{getActiveOwnerUid()};
    }


    private boolean isActiveOwnerSeasonRecord(
            String table,
            long recordId,
            String dateColumn
    ) {
        if (recordId <= 0) {
            return false;
        }

        SQLiteDatabase db =
                getReadableDatabase();

        ArrayList<String> args =
                new ArrayList<>();

        args.add(
                String.valueOf(
                        recordId
                )
        );

        if (!getActiveOwnerUid().isEmpty()) {
            args.add(
                    getActiveOwnerUid()
            );
        }

        Cursor cursor =
                null;

        try {
            cursor = db.rawQuery(
                    "SELECT 1 FROM " + table +
                            " WHERE id = ? AND " + activeOwnerCondition(table + ".owner_uid") +
                            " AND " + activeSeasonCondition(table, dateColumn) +
                            " LIMIT 1",
                    args.toArray(
                            new String[0]
                    )
            );

            return cursor.moveToFirst();

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }


    private String activeSeasonCondition(String alias, String dateColumn) {
        int year = getActiveSeasonYear();
        String season = alias + ".season_year";
        String date = alias + "." + dateColumn;
        return "(COALESCE(" + season + ", 0) = " + year +
                " OR (COALESCE(" + season + ", 0) = 0 AND (" +
                "substr(TRIM(" + date + "), -4) = '" + year + "' OR " +
                "substr(TRIM(" + date + "), 1, 4) = '" + year + "')))";
    }


    // ============================================================
    // DOUBLE HELPER
    // ============================================================

    private double getDoubleValue(
            String sql
    ) {

        return getDoubleValue(sql, null);
    }


    private double getDoubleValue(
            String sql,
            String[] args
    ) {


        SQLiteDatabase db =
                getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(
                            sql,
                            args
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

    /**
     * v20: Elekten oluşturulan satışın kaynağı. Yerel id aynı cihazda, cloud id cihazlar
     * arasında aynı eleğin ikinci kez satışa dönüştürülmesini engeller. İdempotent.
     */
    static void ensureElekSaleLinkSchema(SQLiteDatabase db) {
        DatabaseSchema.addColumnIfMissing(db, "sales", "source_elek_id", "INTEGER DEFAULT 0");
        DatabaseSchema.addColumnIfMissing(db, "sales", "source_elek_cloud_id", "TEXT DEFAULT ''");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sales_source_elek ON sales(source_elek_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sales_source_elek_cloud ON sales(source_elek_cloud_id)");
    }

    private static void ensureCommentSyncSchema(SQLiteDatabase db) {
        DatabaseSchema.addColumnIfMissing(db, "post_comments", "parent_comment_id", "TEXT DEFAULT ''");
        DatabaseSchema.addColumnIfMissing(db, "post_comments", "root_comment_id", "TEXT DEFAULT ''");
        DatabaseSchema.addColumnIfMissing(db, "post_comments", "reply_to_uid", "TEXT DEFAULT ''");
        DatabaseSchema.addColumnIfMissing(db, "post_comments", "reply_to_name", "TEXT DEFAULT ''");

        DatabaseSchema.addColumnIfMissing(db, "post_comments", "cloud_id", "TEXT DEFAULT ''");
        DatabaseSchema.addColumnIfMissing(db, "post_comments", "sync_state", "INTEGER DEFAULT 0");
        DatabaseSchema.addColumnIfMissing(db, "posts", "sync_state", "INTEGER DEFAULT 2");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_post_comments_cloud_id ON post_comments(cloud_id) WHERE cloud_id IS NOT NULL AND cloud_id <> ''");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_comments_pending ON post_comments(user_key,sync_state)");
        try (Cursor c = db.rawQuery("SELECT id FROM post_comments WHERE cloud_id IS NULL OR TRIM(cloud_id) = ''", null)) {
            while (c.moveToNext()) {
                ContentValues v = new ContentValues();
                v.put("cloud_id", UUID.randomUUID().toString());
                db.update("post_comments", v, "id = ?", new String[]{c.getString(0)});
            }
        }
    }
}
