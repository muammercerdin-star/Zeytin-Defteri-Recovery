package com.zeytindefteri.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Zeytin Defteri SQLite şemasını tek yerde tutar.
 * DatabaseHelper bağlantı/upgrade ve veri API'sine odaklanır.
 */
final class DatabaseSchema {
    private DatabaseSchema() {}

    static void createGardensTable(
            SQLiteDatabase db
    ) {


        db.execSQL(
                "CREATE TABLE IF NOT EXISTS gardens (" +

                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +

                        "name TEXT NOT NULL," +

                        "tree_count INTEGER DEFAULT 0," +

                        "age INTEGER DEFAULT 0," +

                        "variety TEXT," +

                        "area_decare REAL DEFAULT 0," +

                        "province TEXT," +

                        "district TEXT," +

                        "neighborhood TEXT," +

                        "irrigation_type TEXT," +

                        "notes TEXT," +

                        "photo_uri TEXT," +

                        "created_at INTEGER DEFAULT 0," +

                        "owner_uid TEXT DEFAULT ''," +

                        "cloud_id TEXT DEFAULT ''," +

                        "updated_at INTEGER DEFAULT 0" +

                        ")"
        );
    }


    // ============================================================
    // OPERATIONS
    // ============================================================

    static void createOperationsTable(
            SQLiteDatabase db
    ) {


        db.execSQL(
                "CREATE TABLE IF NOT EXISTS operations (" +

                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +

                        "garden_id INTEGER NOT NULL," +

                        "type TEXT NOT NULL," +

                        "operation_date TEXT NOT NULL," +

                        "quantity REAL DEFAULT 0," +

                        "unit TEXT," +

                        "cost REAL DEFAULT 0," +

                        "note TEXT," +

                        "season_year INTEGER DEFAULT 0," +

                        "created_at INTEGER NOT NULL," +

                        "updated_at INTEGER DEFAULT 0," +

                        "owner_uid TEXT DEFAULT ''," +

                        "cloud_id TEXT DEFAULT ''," +

                        "FOREIGN KEY(garden_id) " +
                        "REFERENCES gardens(id) " +
                        "ON DELETE CASCADE" +

                        ")"
        );
    }


    // ============================================================
    // HARVEST
    // ============================================================

    static void createHarvestTables(
            SQLiteDatabase db
    ) {


        db.execSQL(
                "CREATE TABLE IF NOT EXISTS harvests (" +

                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +

                        "garden_id INTEGER NOT NULL," +

                        "harvest_date TEXT NOT NULL," +

                        "total_kg REAL DEFAULT 0," +

                        "labor_cost REAL DEFAULT 0," +

                        "transport_cost REAL DEFAULT 0," +

                        "other_cost REAL DEFAULT 0," +

                        "note TEXT," +

                        "created_at INTEGER NOT NULL," +

                        "season_year INTEGER DEFAULT 0," +

                        "updated_at INTEGER DEFAULT 0," +

                        "owner_uid TEXT DEFAULT ''," +

                        "cloud_id TEXT DEFAULT ''," +

                        "FOREIGN KEY(garden_id) " +
                        "REFERENCES gardens(id) " +
                        "ON DELETE CASCADE" +

                        ")"
        );


        db.execSQL(
                "CREATE TABLE IF NOT EXISTS harvest_grades (" +

                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +

                        "harvest_id INTEGER NOT NULL," +

                        "grade TEXT NOT NULL," +

                        "kg REAL DEFAULT 0," +

                        "reference_price_per_kg REAL DEFAULT 0," +

                        "reference_price_date TEXT," +

                        "FOREIGN KEY(harvest_id) " +
                        "REFERENCES harvests(id) " +
                        "ON DELETE CASCADE" +

                        ")"
        );
    }


    // ============================================================
    // ELEK
    // ============================================================

    static void createElekTables(
            SQLiteDatabase db
    ) {


        db.execSQL(
                "CREATE TABLE IF NOT EXISTS elek_records (" +

                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +

                        "garden_id INTEGER NOT NULL," +

                        "record_date TEXT NOT NULL," +

                        "total_kg REAL DEFAULT 0," +

                        "estimated_total REAL DEFAULT 0," +

                        "note TEXT," +

                        "created_at INTEGER NOT NULL," +

                        "season_year INTEGER DEFAULT 0," +

                        "updated_at INTEGER DEFAULT 0," +

                        "owner_uid TEXT DEFAULT ''," +

                        "cloud_id TEXT DEFAULT ''," +

                        "FOREIGN KEY(garden_id) " +
                        "REFERENCES gardens(id) " +
                        "ON DELETE CASCADE" +

                        ")"
        );


        db.execSQL(
                "CREATE TABLE IF NOT EXISTS elek_grades (" +

                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +

                        "elek_id INTEGER NOT NULL," +

                        "grade TEXT NOT NULL," +

                        "kg REAL DEFAULT 0," +

                        "price_per_kg REAL DEFAULT 0," +

                        "line_total REAL DEFAULT 0," +

                        "FOREIGN KEY(elek_id) " +
                        "REFERENCES elek_records(id) " +
                        "ON DELETE CASCADE" +

                        ")"
        );
    }


    // ============================================================
    // BUYERS + SALES
    // ============================================================

    static void createBuyerAndSalesTables(
            SQLiteDatabase db
    ) {


        db.execSQL(
                "CREATE TABLE IF NOT EXISTS buyers (" +

                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +

                        "name TEXT NOT NULL," +

                        "phone TEXT," +

                        "region TEXT," +

                        "note TEXT," +

                        "created_at INTEGER NOT NULL," +

                        "owner_uid TEXT DEFAULT ''" +

                        ")"
        );


        db.execSQL(
                "CREATE TABLE IF NOT EXISTS sales (" +

                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +

                        "garden_id INTEGER NOT NULL," +

                        "buyer_id INTEGER," +

                        "sale_date TEXT NOT NULL," +

                        "sale_kind TEXT DEFAULT 'SALE'," +

                        "region TEXT," +

                        "variety TEXT," +

                        "product_type TEXT DEFAULT 'SOFRALIK'," +

                        "total_kg REAL DEFAULT 0," +

                        "gross_total REAL DEFAULT 0," +

                        "transport_cost REAL DEFAULT 0," +

                        "deductions REAL DEFAULT 0," +

                        "amount_received REAL DEFAULT 0," +

                        "due_date TEXT," +

                        "note TEXT," +

                        "market_consent INTEGER DEFAULT 0," +

                        "market_sync_status TEXT DEFAULT 'PENDING'," +

                        "created_at INTEGER NOT NULL," +

                        "season_year INTEGER DEFAULT 0," +

                        "updated_at INTEGER DEFAULT 0," +

                        "owner_uid TEXT DEFAULT ''," +

                        "cloud_id TEXT DEFAULT ''," +

                        "FOREIGN KEY(garden_id) " +
                        "REFERENCES gardens(id) " +
                        "ON DELETE CASCADE," +

                        "FOREIGN KEY(buyer_id) " +
                        "REFERENCES buyers(id) " +
                        "ON DELETE SET NULL" +

                        ")"
        );


        db.execSQL(
                "CREATE TABLE IF NOT EXISTS sale_grades (" +

                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +

                        "sale_id INTEGER NOT NULL," +

                        "grade TEXT NOT NULL," +

                        "kg REAL DEFAULT 0," +

                        "price_per_kg REAL DEFAULT 0," +

                        "line_total REAL DEFAULT 0," +

                        "FOREIGN KEY(sale_id) " +
                        "REFERENCES sales(id) " +
                        "ON DELETE CASCADE" +

                        ")"
        );
    }


    // ============================================================
    // BRINE
    // ============================================================

    static void createBrineTables(
            SQLiteDatabase db
    ) {


        db.execSQL(
                "CREATE TABLE IF NOT EXISTS brine_batches (" +

                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +

                        "garden_id INTEGER," +

                        "batch_name TEXT NOT NULL," +

                        "start_date TEXT NOT NULL," +

                        "expected_ready_date TEXT," +

                        "variety TEXT," +

                        "grade TEXT," +

                        "container_liter REAL DEFAULT 0," +

                        "olive_kg REAL DEFAULT 0," +

                        "salt_kg REAL DEFAULT 0," +

                        "water_liter REAL DEFAULT 0," +

                        "salt_cost REAL DEFAULT 0," +

                        "packaging_cost REAL DEFAULT 0," +

                        "other_cost REAL DEFAULT 0," +

                        "status TEXT DEFAULT 'ACTIVE'," +

                        "ready_kg REAL DEFAULT 0," +

                        "note TEXT," +

                        "created_at INTEGER NOT NULL," +

                        "season_year INTEGER DEFAULT 0," +

                        "updated_at INTEGER DEFAULT 0," +

                        "owner_uid TEXT DEFAULT ''," +

                        "cloud_id TEXT DEFAULT ''," +

                        "FOREIGN KEY(garden_id) " +
                        "REFERENCES gardens(id) " +
                        "ON DELETE SET NULL" +

                        ")"
        );


        db.execSQL(
                "CREATE TABLE IF NOT EXISTS brine_events (" +

                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +

                        "batch_id INTEGER NOT NULL," +

                        "event_date TEXT NOT NULL," +

                        "event_type TEXT NOT NULL," +

                        "amount REAL DEFAULT 0," +

                        "unit TEXT," +

                        "cost REAL DEFAULT 0," +

                        "note TEXT," +

                        "created_at INTEGER NOT NULL," +

                        "FOREIGN KEY(batch_id) " +
                        "REFERENCES brine_batches(id) " +
                        "ON DELETE CASCADE" +

                        ")"
        );
    }


    // ============================================================
    // INVENTORY
    // ============================================================

    static void createInventoryTables(
            SQLiteDatabase db
    ) {


        db.execSQL(
                "CREATE TABLE IF NOT EXISTS inventory_items (" +

                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +

                        "name TEXT NOT NULL," +

                        "category TEXT," +

                        "unit TEXT," +

                        "current_qty REAL DEFAULT 0," +

                        "min_qty REAL DEFAULT 0," +

                        "unit_cost REAL DEFAULT 0," +

                        "note TEXT," +

                        "created_at INTEGER NOT NULL," +

                        "updated_at INTEGER DEFAULT 0," +

                        "owner_uid TEXT DEFAULT ''," +

                        "cloud_id TEXT DEFAULT ''" +

                        ")"
        );


        db.execSQL(
                "CREATE TABLE IF NOT EXISTS inventory_movements (" +

                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +

                        "item_id INTEGER NOT NULL," +

                        "movement_date TEXT NOT NULL," +

                        "movement_type TEXT NOT NULL," +

                        "quantity REAL DEFAULT 0," +

                        "unit_cost REAL DEFAULT 0," +

                        "operation_id INTEGER," +

                        "note TEXT," +

                        "created_at INTEGER NOT NULL," +

                        "updated_at INTEGER DEFAULT 0," +

                        "owner_uid TEXT DEFAULT ''," +

                        "cloud_id TEXT DEFAULT ''," +

                        "FOREIGN KEY(item_id) " +
                        "REFERENCES inventory_items(id) " +
                        "ON DELETE CASCADE," +

                        "FOREIGN KEY(operation_id) " +
                        "REFERENCES operations(id) " +
                        "ON DELETE SET NULL" +

                        ")"
        );
    }


    // ============================================================
    // RECEIVABLES
    // ============================================================

    static void createReceivablesTable(
            SQLiteDatabase db
    ) {


        db.execSQL(
                "CREATE TABLE IF NOT EXISTS receivables (" +

                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +

                        "title TEXT NOT NULL," +

                        "party_name TEXT," +

                        "related_sale_id INTEGER," +

                        "amount REAL DEFAULT 0," +

                        "total_received REAL DEFAULT 0," +

                        "due_date TEXT," +

                        "status TEXT DEFAULT 'OPEN'," +

                        "note TEXT," +

                        "created_at INTEGER NOT NULL," +

                        "updated_at INTEGER DEFAULT 0," +

                        "owner_uid TEXT DEFAULT ''," +

                        "cloud_id TEXT DEFAULT ''," +

                        "FOREIGN KEY(related_sale_id) " +
                        "REFERENCES sales(id) " +
                        "ON DELETE SET NULL" +

                        ")"
        );
    }


    // ============================================================
    // REMINDERS
    // ============================================================

    static void createRemindersTable(
            SQLiteDatabase db
    ) {


        db.execSQL(
                "CREATE TABLE IF NOT EXISTS reminders (" +

                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +

                        "garden_id INTEGER," +

                        "title TEXT NOT NULL," +

                        "reminder_type TEXT," +

                        "due_at INTEGER NOT NULL," +

                        "is_done INTEGER DEFAULT 0," +

                        "repeat_rule TEXT," +

                        "note TEXT," +

                        "created_at INTEGER NOT NULL," +

                        "updated_at INTEGER DEFAULT 0," +

                        "owner_uid TEXT DEFAULT ''," +

                        "cloud_id TEXT DEFAULT ''," +

                        "FOREIGN KEY(garden_id) " +
                        "REFERENCES gardens(id) " +
                        "ON DELETE CASCADE" +

                        ")"
        );
    }


    // ============================================================
    // GARDEN NOTES
    // ============================================================

    static void createGardenNotesTable(
            SQLiteDatabase db
    ) {


        db.execSQL(
                "CREATE TABLE IF NOT EXISTS garden_notes (" +

                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +

                        "garden_id INTEGER NOT NULL," +

                        "note_date TEXT NOT NULL," +

                        "note TEXT NOT NULL," +

                        "photo_uri TEXT," +

                        "created_at INTEGER NOT NULL," +

                        "updated_at INTEGER DEFAULT 0," +

                        "owner_uid TEXT DEFAULT ''," +

                        "cloud_id TEXT DEFAULT ''," +

                        "FOREIGN KEY(garden_id) " +
                        "REFERENCES gardens(id) " +
                        "ON DELETE CASCADE" +

                        ")"
        );
    }


    // ============================================================
    // SETTINGS
    // ============================================================

    static void createSettingsTable(
            SQLiteDatabase db
    ) {


        db.execSQL(
                "CREATE TABLE IF NOT EXISTS app_settings (" +

                        "setting_key TEXT PRIMARY KEY," +

                        "setting_value TEXT" +

                        ")"
        );
    }


    // ============================================================
    // POSTS
    // ============================================================

    // ============================================================
    // SYNC STATE + TOMBSTONES
    // ============================================================

    static void createSyncTables(SQLiteDatabase db) {

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS sync_state (" +
                        "owner_uid TEXT NOT NULL," +
                        "entity_type TEXT NOT NULL," +
                        "cloud_id TEXT NOT NULL," +
                        "last_uploaded_at INTEGER DEFAULT 0," +
                        "last_remote_at INTEGER DEFAULT 0," +
                        "PRIMARY KEY(owner_uid, entity_type, cloud_id)" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS sync_tombstones (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "owner_uid TEXT NOT NULL," +
                        "entity_type TEXT NOT NULL," +
                        "cloud_id TEXT NOT NULL," +
                        "garden_cloud_id TEXT DEFAULT ''," +
                        "deleted_at INTEGER NOT NULL," +
                        "uploaded INTEGER DEFAULT 0," +
                        "UNIQUE(owner_uid, entity_type, cloud_id)" +
                        ")"
        );

        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_sync_tombstones_owner " +
                        "ON sync_tombstones(owner_uid, uploaded, deleted_at)"
        );
    }


    static void createSocialTables(
            SQLiteDatabase db
    ) {


        db.execSQL(
                "CREATE TABLE IF NOT EXISTS posts (" +

                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +

                        "cloud_id TEXT," +

                        "user_id TEXT," +

                        "user_name TEXT NOT NULL," +

                        "province TEXT," +

                        "district TEXT," +

                        "neighborhood TEXT," +

                        "garden_id INTEGER," +

                        "garden_name TEXT," +

                        "variety TEXT," +

                        "post_type TEXT DEFAULT 'GENERAL'," +

                        "content TEXT NOT NULL," +

                        "photo_uri TEXT," +

                        /*
                         * useful_count sadece eski ekranlarla uyumluluk için.
                         * Yeni sistemde LIKE sayısını tutuyoruz.
                         */
                        "useful_count INTEGER DEFAULT 0," +

                        /*
                         * Artık aktif kullanılmıyor.
                         * Şimdilik eski veritabanı uyumluluğu için duruyor.
                         */
                        "not_useful_count INTEGER DEFAULT 0," +

                        "comment_count INTEGER DEFAULT 0," +

                        "is_real_sale INTEGER DEFAULT 0," +

                        "sale_id INTEGER," +

                        "sale_grade TEXT," +

                        "sale_kg REAL DEFAULT 0," +

                        "sale_price REAL DEFAULT 0," +

                        "is_active INTEGER DEFAULT 1," +

                        "created_at INTEGER NOT NULL," +

                        "updated_at INTEGER DEFAULT 0," +

                        "FOREIGN KEY(garden_id) " +
                        "REFERENCES gardens(id) " +
                        "ON DELETE SET NULL," +

                        "FOREIGN KEY(sale_id) " +
                        "REFERENCES sales(id) " +
                        "ON DELETE SET NULL" +

                        ")"
        );
    }


    // ============================================================
    // SOCIAL INTERACTIONS
    // ============================================================

    static void createSocialInteractionTables(
            SQLiteDatabase db
    ) {


        // ========================================================
        // REACTIONS
        //
        // LIKE
        // LOVE
        // CARE
        // HAHA
        // WOW
        // SAD
        // ANGRY
        //
        // UNIQUE(post_id, user_key)
        // Bir kullanıcı bir gönderide tek tepki bırakabilir.
        // ========================================================

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS post_reactions (" +

                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +

                        "post_id INTEGER NOT NULL," +

                        "user_key TEXT NOT NULL," +

                        "reaction_type TEXT NOT NULL," +

                        "created_at INTEGER NOT NULL," +

                        "updated_at INTEGER DEFAULT 0," +

                        "UNIQUE(post_id, user_key)," +

                        "FOREIGN KEY(post_id) " +
                        "REFERENCES posts(id) " +
                        "ON DELETE CASCADE" +

                        ")"
        );


        // ========================================================
        // COMMENTS
        // ========================================================

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS post_comments (" +

                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "cloud_id TEXT DEFAULT ''," +
                        "sync_state INTEGER DEFAULT 0," +

                        "post_id INTEGER NOT NULL," +

                        "user_key TEXT," +

                        "user_name TEXT NOT NULL," +

                        "content TEXT NOT NULL," +

                        "is_active INTEGER DEFAULT 1," +

                        "created_at INTEGER NOT NULL," +

                        "updated_at INTEGER DEFAULT 0," +

                        "FOREIGN KEY(post_id) " +
                        "REFERENCES posts(id) " +
                        "ON DELETE CASCADE" +

                        ")"
        );


        // ========================================================
        // SAVES
        // ========================================================

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS post_saves (" +

                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +

                        "post_id INTEGER NOT NULL," +

                        "user_key TEXT NOT NULL," +

                        "created_at INTEGER NOT NULL," +

                        "UNIQUE(post_id, user_key)," +

                        "FOREIGN KEY(post_id) " +
                        "REFERENCES posts(id) " +
                        "ON DELETE CASCADE" +

                        ")"
        );


        // ========================================================
        // MEDIA
        // ========================================================

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS post_media (" +

                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +

                        "post_id INTEGER NOT NULL," +

                        "media_type TEXT NOT NULL," +

                        "media_uri TEXT NOT NULL," +

                        "sort_order INTEGER DEFAULT 0," +

                        "created_at INTEGER NOT NULL," +

                        "FOREIGN KEY(post_id) " +
                        "REFERENCES posts(id) " +
                        "ON DELETE CASCADE" +

                        ")"
        );
    }


    // ============================================================
    // INDEXES
    // ============================================================

    static void createIndexes(
            SQLiteDatabase db
    ) {


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_gardens_owner_uid " +
                        "ON gardens(owner_uid)"
        );


        db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_gardens_cloud_id " +
                        "ON gardens(cloud_id) " +
                        "WHERE cloud_id IS NOT NULL AND cloud_id <> ''"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_operations_garden " +
                        "ON operations(garden_id)"
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


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_operations_created " +
                        "ON operations(created_at)"
        );

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_operations_owner_season ON operations(owner_uid,season_year)");


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_harvests_garden " +
                        "ON harvests(garden_id)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_harvests_created " +
                        "ON harvests(created_at)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_harvests_owner_uid " +
                        "ON harvests(owner_uid)"
        );

        db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_harvests_cloud_id " +
                        "ON harvests(cloud_id) " +
                        "WHERE cloud_id IS NOT NULL AND cloud_id <> ''"
        );

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_harvests_owner_season ON harvests(owner_uid,season_year)");


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_harvest_grades_grade " +
                        "ON harvest_grades(grade)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_elek_records_garden " +
                        "ON elek_records(garden_id)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_elek_records_date " +
                        "ON elek_records(record_date)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_elek_records_owner_uid " +
                        "ON elek_records(owner_uid)"
        );

        db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_elek_records_cloud_id " +
                        "ON elek_records(cloud_id) " +
                        "WHERE cloud_id IS NOT NULL AND cloud_id <> ''"
        );

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_elek_owner_season ON elek_records(owner_uid,season_year)");


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_elek_grades_record " +
                        "ON elek_grades(elek_id)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_elek_grades_grade " +
                        "ON elek_grades(grade)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_sales_garden " +
                        "ON sales(garden_id)"
        );

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_buyers_owner_uid ON buyers(owner_uid)");


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_sales_region " +
                        "ON sales(region)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_sales_created " +
                        "ON sales(created_at)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_sales_owner_uid " +
                        "ON sales(owner_uid)"
        );

        db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_sales_cloud_id " +
                        "ON sales(cloud_id) " +
                        "WHERE cloud_id IS NOT NULL AND cloud_id <> ''"
        );

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sales_owner_season ON sales(owner_uid,season_year)");


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_sale_grades_grade " +
                        "ON sale_grades(grade)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_brine_garden " +
                        "ON brine_batches(garden_id)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_brine_status " +
                        "ON brine_batches(status)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_brine_batches_owner_uid " +
                        "ON brine_batches(owner_uid)"
        );

        db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_brine_batches_cloud_id " +
                        "ON brine_batches(cloud_id) " +
                        "WHERE cloud_id IS NOT NULL AND cloud_id <> ''"
        );

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_brine_owner_season ON brine_batches(owner_uid,season_year)");


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_brine_events_batch " +
                        "ON brine_events(batch_id)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_inventory_movements_item " +
                        "ON inventory_movements(item_id)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_reminders_due " +
                        "ON reminders(due_at)"
        );


        db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_posts_cloud_id " +
                        "ON posts(cloud_id)"
        );

        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_posts_created " +
                        "ON posts(created_at)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_posts_user " +
                        "ON posts(user_id)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_posts_province " +
                        "ON posts(province)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_posts_district " +
                        "ON posts(district)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_posts_type " +
                        "ON posts(post_type)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_posts_real_sale " +
                        "ON posts(is_real_sale)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_post_reactions_post " +
                        "ON post_reactions(post_id)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_post_reactions_user " +
                        "ON post_reactions(user_key)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_post_reactions_type " +
                        "ON post_reactions(reaction_type)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_post_comments_post " +
                        "ON post_comments(post_id)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_post_comments_created " +
                        "ON post_comments(created_at)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_post_saves_user " +
                        "ON post_saves(user_key)"
        );


        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_post_media_post " +
                        "ON post_media(post_id)"
        );
    }


    // ============================================================
    // COLUMN MIGRATION
    // ============================================================

    static void addColumnIfMissing(
            SQLiteDatabase db,
            String table,
            String column,
            String definition
    ) {


        Cursor cursor =
                db.rawQuery(
                        "PRAGMA table_info(" +
                                table +
                                ")",
                        null
                );


        boolean exists =
                false;


        try {


            while (cursor.moveToNext()) {


                String current =
                        cursor.getString(
                                cursor.getColumnIndexOrThrow(
                                        "name"
                                )
                        );


                if (column.equals(
                        current
                )) {


                    exists =
                            true;


                    break;
                }
            }


        } finally {


            cursor.close();
        }


        if (!exists) {


            db.execSQL(
                    "ALTER TABLE " +
                            table +
                            " ADD COLUMN " +
                            column +
                            " " +
                            definition
            );
        }
    }


    // ============================================================
    // HESAP / BULUT DESTEK METOTLARI
    // ============================================================


}
