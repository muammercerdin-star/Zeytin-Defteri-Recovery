package com.zeytindefteri.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Zeytin Defteri - Meydan veri yöneticisi
 *
 * Bu sınıf:
 * - Yerel posts tablosundan Meydan akışını okur.
 * - Bölge, kategori ve zaman filtrelerini destekler.
 * - Cevapsız soruları bulabilir.
 * - Bugünkü / bölgesel mesaj sayılarını hesaplayabilir.
 * - Eski FeedManager API'sini korur.
 *
 * NOT:
 * Firestore senkronizasyonunu bu sınıf yapmaz.
 * SocialCloudRepository buluttan gelen kayıtları yerel posts tablosuna taşır.
 */
public class FeedManager {

    // ============================================================
    // ANA FİLTRELER
    // ============================================================

    public static final String FILTER_NEARBY = "NEARBY";
    public static final String FILTER_FOLLOWING = "FOLLOWING";
    public static final String FILTER_MARKET = "MARKET";
    public static final String FILTER_ALL = "ALL";


    // ============================================================
    // MEYDAN KATEGORİLERİ
    // ============================================================

    public static final String TYPE_ALL = "ALL";
    public static final String TYPE_GENERAL = "GENERAL";
    public static final String TYPE_MARKET = "MARKET";
    public static final String TYPE_HARVEST = "HARVEST";
    public static final String TYPE_PEST = "PEST";
    public static final String TYPE_SPRAY = "SPRAY";
    public static final String TYPE_IRRIGATION = "IRRIGATION";
    public static final String TYPE_FERTILIZER = "FERTILIZER";
    public static final String TYPE_SALE = "SALE";
    public static final String TYPE_QUESTION = "QUESTION";
    public static final String TYPE_GARDEN = "GARDEN";


    // ============================================================
    // DATABASE
    // ============================================================

    private final DatabaseHelper databaseHelper;


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public FeedManager(DatabaseHelper databaseHelper) {

        if (databaseHelper == null) {
            throw new IllegalArgumentException(
                    "DatabaseHelper null olamaz."
            );
        }

        this.databaseHelper = databaseHelper;
    }


    // ============================================================
    // POST MODEL
    // ============================================================

    public static class PostItem {

        public long id;

        public String userId;
        public String userName;

        public String province;
        public String district;
        public String neighborhood;

        public long gardenId;
        public String gardenName;

        public String variety;

        public String postType;
        public String content;

        /**
         * Eski kayıtlarla uyumluluk için modelde tutuluyor.
         * Yeni Meydan arayüzü fotoğraf göstermez.
         */
        public String photoUri;

        public int usefulCount;
        public int notUsefulCount;
        public int commentCount;

        public boolean realSale;

        public long saleId;
        public String saleGrade;
        public double saleKg;
        public double salePrice;

        public boolean active;

        public long createdAt;
        public long updatedAt;


        public PostItem() {

            id = 0;

            userId = "";
            userName = "Üretici";

            province = "";
            district = "";
            neighborhood = "";

            gardenId = 0;
            gardenName = "";

            variety = "";

            postType = TYPE_GENERAL;
            content = "";
            photoUri = "";

            usefulCount = 0;
            notUsefulCount = 0;
            commentCount = 0;

            realSale = false;

            saleId = 0;
            saleGrade = "";
            saleKg = 0;
            salePrice = 0;

            active = true;

            createdAt = 0;
            updatedAt = 0;
        }
    }


    // ============================================================
    // MEYDAN ÖZET MODELİ
    // ============================================================

    public static class MeydanSummary {

        public int totalToday;
        public int totalRepliesToday;
        public int localToday;

        public int marketToday;
        public int harvestToday;
        public int pestToday;
        public int irrigationToday;
        public int saleToday;
        public int generalToday;

        public String topTopic;
        public int topTopicCount;

        public PostItem unansweredQuestion;

        public MeydanSummary() {

            totalToday = 0;
            totalRepliesToday = 0;
            localToday = 0;

            marketToday = 0;
            harvestToday = 0;
            pestToday = 0;
            irrigationToday = 0;
            saleToday = 0;
            generalToday = 0;

            topTopic = "#Genel";
            topTopicCount = 0;

            unansweredQuestion = null;
        }
    }


    // ============================================================
    // ANA FEED
    // ============================================================

    public List<PostItem> getFeed(
            String filter,
            int limit
    ) {

        return getFeed(
                filter,
                "",
                limit
        );
    }


    // ============================================================
    // FEED + DISTRICT
    // ============================================================

    public List<PostItem> getFeed(
            String filter,
            String district,
            int limit
    ) {

        ArrayList<PostItem> posts =
                new ArrayList<>();

        limit = normalizeLimit(limit);

        filter = normalizeFilter(filter);

        district = safe(district);

        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();

        Cursor cursor = null;

        try {

            // ====================================================
            // MARKET
            // ====================================================

            if (FILTER_MARKET.equals(filter)) {

                cursor = db.rawQuery(

                        "SELECT * " +
                                "FROM posts " +
                                "WHERE is_active = 1 " +
                                "AND (" +
                                "post_type IN ('MARKET', 'SALE') " +
                                "OR is_real_sale = 1" +
                                ") " +
                                "ORDER BY created_at DESC, id DESC " +
                                "LIMIT ?",

                        new String[]{
                                String.valueOf(limit)
                        }
                );


            // ====================================================
            // NEARBY
            // ====================================================

            } else if (
                    FILTER_NEARBY.equals(filter)
                            &&
                            !district.isEmpty()
            ) {

                cursor = db.rawQuery(

                        "SELECT * " +
                                "FROM posts " +
                                "WHERE is_active = 1 " +
                                "AND district = ? COLLATE NOCASE " +
                                "ORDER BY created_at DESC, id DESC " +
                                "LIMIT ?",

                        new String[]{
                                district,
                                String.valueOf(limit)
                        }
                );


            // ====================================================
            // FOLLOWING
            //
            // Takip sistemi henüz kalıcı veri modeline sahip değil.
            // Eski davranış korunuyor: normal akış.
            // ====================================================

            } else {

                cursor = db.rawQuery(

                        "SELECT * " +
                                "FROM posts " +
                                "WHERE is_active = 1 " +
                                "ORDER BY created_at DESC, id DESC " +
                                "LIMIT ?",

                        new String[]{
                                String.valueOf(limit)
                        }
                );
            }


            while (
                    cursor != null
                            &&
                            cursor.moveToNext()
            ) {

                posts.add(
                        cursorToPostItem(cursor)
                );
            }

        } catch (Exception e) {

            e.printStackTrace();

        } finally {

            close(cursor);
        }

        return posts;
    }


    // ============================================================
    // LATEST POSTS
    // ============================================================

    /** Apply personal blocks before the display limit, preserving other producers. */
    public List<PostItem> getLatestVisiblePosts(int limit, java.util.Set<String> blockedUids) {
        if(blockedUids==null || blockedUids.isEmpty())return getLatestPosts(limit);
        ArrayList<PostItem> result=new ArrayList<>();
        int visibleLimit=normalizeLimit(limit);
        try(Cursor cursor=databaseHelper.getReadableDatabase().rawQuery(
                "SELECT * FROM posts WHERE is_active = 1 ORDER BY created_at DESC, id DESC",null)) {
            while(cursor.moveToNext()) {
                PostItem item=cursorToPostItem(cursor);
                if(blockedUids.contains(item.userId))continue;
                result.add(item);if(result.size()>=visibleLimit)break;
            }
        }
        return result;
    }

    public List<PostItem> getLatestPosts(
            int limit
    ) {

        return getFeed(
                FILTER_ALL,
                "",
                limit
        );
    }


    // ============================================================
    // MARKET POSTS
    // ============================================================

    public List<PostItem> getMarketPosts(
            int limit
    ) {

        return getFeed(
                FILTER_MARKET,
                "",
                limit
        );
    }


    // ============================================================
    // NEARBY POSTS
    // ============================================================

    public List<PostItem> getNearbyPosts(
            String district,
            int limit
    ) {

        return getFeed(
                FILTER_NEARBY,
                district,
                limit
        );
    }


    // ============================================================
    // FOLLOWING POSTS
    // ============================================================

    public List<PostItem> getFollowingPosts(
            int limit
    ) {

        return getFeed(
                FILTER_FOLLOWING,
                "",
                limit
        );
    }


    // ============================================================
    // KATEGORİ AKIŞI
    // ============================================================

    public List<PostItem> getPostsByType(
            String type,
            int limit
    ) {

        return getPostsByType(
                type,
                "",
                limit
        );
    }


    public List<PostItem> getPostsByType(
            String type,
            String district,
            int limit
    ) {

        ArrayList<PostItem> posts =
                new ArrayList<>();

        type = normalizePostType(type);
        district = safe(district);

        limit = normalizeLimit(limit);

        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();

        Cursor cursor = null;

        try {

            // ====================================================
            // ALL
            // ====================================================

            if (TYPE_ALL.equals(type)) {

                if (district.isEmpty()) {

                    cursor = db.rawQuery(

                            "SELECT * " +
                                    "FROM posts " +
                                    "WHERE is_active = 1 " +
                                    "ORDER BY created_at DESC, id DESC " +
                                    "LIMIT ?",

                            new String[]{
                                    String.valueOf(limit)
                            }
                    );

                } else {

                    cursor = db.rawQuery(

                            "SELECT * " +
                                    "FROM posts " +
                                    "WHERE is_active = 1 " +
                                    "AND district = ? COLLATE NOCASE " +
                                    "ORDER BY created_at DESC, id DESC " +
                                    "LIMIT ?",

                            new String[]{
                                    district,
                                    String.valueOf(limit)
                            }
                    );
                }


            // ====================================================
            // PİYASA
            // ====================================================

            } else if (TYPE_MARKET.equals(type)) {

                if (district.isEmpty()) {

                    cursor = db.rawQuery(

                            "SELECT * " +
                                    "FROM posts " +
                                    "WHERE is_active = 1 " +
                                    "AND (" +
                                    "post_type = 'MARKET' " +
                                    "OR is_real_sale = 1" +
                                    ") " +
                                    "ORDER BY created_at DESC, id DESC " +
                                    "LIMIT ?",

                            new String[]{
                                    String.valueOf(limit)
                            }
                    );

                } else {

                    cursor = db.rawQuery(

                            "SELECT * " +
                                    "FROM posts " +
                                    "WHERE is_active = 1 " +
                                    "AND district = ? COLLATE NOCASE " +
                                    "AND (" +
                                    "post_type = 'MARKET' " +
                                    "OR is_real_sale = 1" +
                                    ") " +
                                    "ORDER BY created_at DESC, id DESC " +
                                    "LIMIT ?",

                            new String[]{
                                    district,
                                    String.valueOf(limit)
                            }
                    );
                }


            // ====================================================
            // HASTALIK
            // ====================================================

            } else if (TYPE_PEST.equals(type)) {

                if (district.isEmpty()) {

                    cursor = db.rawQuery(

                            "SELECT * " +
                                    "FROM posts " +
                                    "WHERE is_active = 1 " +
                                    "AND post_type IN ('PEST', 'SPRAY') " +
                                    "ORDER BY created_at DESC, id DESC " +
                                    "LIMIT ?",

                            new String[]{
                                    String.valueOf(limit)
                            }
                    );

                } else {

                    cursor = db.rawQuery(

                            "SELECT * " +
                                    "FROM posts " +
                                    "WHERE is_active = 1 " +
                                    "AND district = ? COLLATE NOCASE " +
                                    "AND post_type IN ('PEST', 'SPRAY') " +
                                    "ORDER BY created_at DESC, id DESC " +
                                    "LIMIT ?",

                            new String[]{
                                    district,
                                    String.valueOf(limit)
                            }
                    );
                }


            // ====================================================
            // ALIM-SATIM
            // ====================================================

            } else if (TYPE_SALE.equals(type)) {

                if (district.isEmpty()) {

                    cursor = db.rawQuery(

                            "SELECT * " +
                                    "FROM posts " +
                                    "WHERE is_active = 1 " +
                                    "AND (" +
                                    "post_type = 'SALE' " +
                                    "OR is_real_sale = 1" +
                                    ") " +
                                    "ORDER BY created_at DESC, id DESC " +
                                    "LIMIT ?",

                            new String[]{
                                    String.valueOf(limit)
                            }
                    );

                } else {

                    cursor = db.rawQuery(

                            "SELECT * " +
                                    "FROM posts " +
                                    "WHERE is_active = 1 " +
                                    "AND district = ? COLLATE NOCASE " +
                                    "AND (" +
                                    "post_type = 'SALE' " +
                                    "OR is_real_sale = 1" +
                                    ") " +
                                    "ORDER BY created_at DESC, id DESC " +
                                    "LIMIT ?",

                            new String[]{
                                    district,
                                    String.valueOf(limit)
                            }
                    );
                }


            // ====================================================
            // DİĞER TEK KATEGORİLER
            // ====================================================

            } else {

                if (district.isEmpty()) {

                    cursor = db.rawQuery(

                            "SELECT * " +
                                    "FROM posts " +
                                    "WHERE is_active = 1 " +
                                    "AND post_type = ? COLLATE NOCASE " +
                                    "ORDER BY created_at DESC, id DESC " +
                                    "LIMIT ?",

                            new String[]{
                                    type,
                                    String.valueOf(limit)
                            }
                    );

                } else {

                    cursor = db.rawQuery(

                            "SELECT * " +
                                    "FROM posts " +
                                    "WHERE is_active = 1 " +
                                    "AND district = ? COLLATE NOCASE " +
                                    "AND post_type = ? COLLATE NOCASE " +
                                    "ORDER BY created_at DESC, id DESC " +
                                    "LIMIT ?",

                            new String[]{
                                    district,
                                    type,
                                    String.valueOf(limit)
                            }
                    );
                }
            }


            while (
                    cursor != null
                            &&
                            cursor.moveToNext()
            ) {

                posts.add(
                        cursorToPostItem(cursor)
                );
            }

        } catch (Exception e) {

            e.printStackTrace();

        } finally {

            close(cursor);
        }

        return posts;
    }


    // ============================================================
    // BELİRLİ TARİHTEN SONRAKİ MESAJLAR
    // ============================================================

    public List<PostItem> getPostsSince(
            long sinceMillis,
            int limit
    ) {

        ArrayList<PostItem> posts =
                new ArrayList<>();

        limit = normalizeLimit(limit);

        if (sinceMillis < 0) {
            sinceMillis = 0;
        }

        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();

        Cursor cursor = null;

        try {

            cursor = db.rawQuery(

                    "SELECT * " +
                            "FROM posts " +
                            "WHERE is_active = 1 " +
                            "AND created_at >= ? " +
                            "ORDER BY created_at DESC, id DESC " +
                            "LIMIT ?",

                    new String[]{
                            String.valueOf(sinceMillis),
                            String.valueOf(limit)
                    }
            );

            while (
                    cursor != null
                            &&
                            cursor.moveToNext()
            ) {

                posts.add(
                        cursorToPostItem(cursor)
                );
            }

        } catch (Exception e) {

            e.printStackTrace();

        } finally {

            close(cursor);
        }

        return posts;
    }


    // ============================================================
    // BÖLGE + TARİH
    // ============================================================

    public List<PostItem> getDistrictPostsSince(
            String district,
            long sinceMillis,
            int limit
    ) {

        ArrayList<PostItem> posts =
                new ArrayList<>();

        district = safe(district);

        limit = normalizeLimit(limit);

        if (district.isEmpty()) {
            return posts;
        }

        if (sinceMillis < 0) {
            sinceMillis = 0;
        }

        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();

        Cursor cursor = null;

        try {

            cursor = db.rawQuery(

                    "SELECT * " +
                            "FROM posts " +
                            "WHERE is_active = 1 " +
                            "AND district = ? COLLATE NOCASE " +
                            "AND created_at >= ? " +
                            "ORDER BY created_at DESC, id DESC " +
                            "LIMIT ?",

                    new String[]{
                            district,
                            String.valueOf(sinceMillis),
                            String.valueOf(limit)
                    }
            );

            while (
                    cursor != null
                            &&
                            cursor.moveToNext()
            ) {

                posts.add(
                        cursorToPostItem(cursor)
                );
            }

        } catch (Exception e) {

            e.printStackTrace();

        } finally {

            close(cursor);
        }

        return posts;
    }


    // ============================================================
    // CEVAP BEKLEYEN SORULAR
    // ============================================================

    public List<PostItem> getUnansweredQuestions(
            int limit
    ) {

        ArrayList<PostItem> posts =
                new ArrayList<>();

        limit = normalizeLimit(limit);

        /*
         * Sadece QUESTION tipi değil,
         * kullanıcıların serbest yazdığı soru cümleleri de olabilir.
         *
         * SQL'de önce comment_count = 0 kayıtlarını daraltıyoruz,
         * son soru benzerliği kontrolünü Java tarafında yapıyoruz.
         */

        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();

        Cursor cursor = null;

        try {

            cursor = db.rawQuery(

                    "SELECT * " +
                            "FROM posts " +
                            "WHERE is_active = 1 " +
                            "AND comment_count <= 0 " +
                            "ORDER BY created_at DESC, id DESC " +
                            "LIMIT ?",

                    new String[]{
                            String.valueOf(
                                    Math.max(
                                            limit * 5,
                                            30
                                    )
                            )
                    }
            );

            while (
                    cursor != null
                            &&
                            cursor.moveToNext()
            ) {

                PostItem item =
                        cursorToPostItem(cursor);

                if (isQuestionLike(item)) {

                    posts.add(item);

                    if (posts.size() >= limit) {
                        break;
                    }
                }
            }

        } catch (Exception e) {

            e.printStackTrace();

        } finally {

            close(cursor);
        }

        return posts;
    }


    // ============================================================
    // TEK CEVAPSIZ SORU
    // ============================================================

    public PostItem getLatestUnansweredQuestion() {

        List<PostItem> items =
                getUnansweredQuestions(1);

        if (items.isEmpty()) {
            return null;
        }

        return items.get(0);
    }


    // ============================================================
    // BUGÜN / 24 SAAT ÖZETİ
    // ============================================================

    public MeydanSummary getTodaySummary(
            String localDistrict
    ) {

        MeydanSummary summary =
                new MeydanSummary();

        long cutoff =
                System.currentTimeMillis()
                        - 24L
                        * 60L
                        * 60L
                        * 1000L;

        String district =
                safe(localDistrict);

        List<PostItem> items =
                getPostsSince(
                        cutoff,
                        500
                );

        for (PostItem item : items) {

            if (item == null) {
                continue;
            }

            summary.totalToday++;

            summary.totalRepliesToday +=
                    Math.max(
                            0,
                            item.commentCount
                    );

            if (
                    !district.isEmpty()
                            &&
                            district.equalsIgnoreCase(
                                    safe(item.district)
                            )
            ) {
                summary.localToday++;
            }

            String type =
                    normalizePostType(
                            item.postType
                    );

            if (
                    item.realSale
                            ||
                            TYPE_MARKET.equals(type)
            ) {

                summary.marketToday++;

            } else if (
                    TYPE_HARVEST.equals(type)
            ) {

                summary.harvestToday++;

            } else if (
                    TYPE_PEST.equals(type)
                            ||
                            TYPE_SPRAY.equals(type)
            ) {

                summary.pestToday++;

            } else if (
                    TYPE_IRRIGATION.equals(type)
            ) {

                summary.irrigationToday++;

            } else if (
                    TYPE_SALE.equals(type)
            ) {

                summary.saleToday++;

            } else {

                summary.generalToday++;
            }
        }

        calculateTopTopic(summary);

        summary.unansweredQuestion =
                getLatestUnansweredQuestion();

        return summary;
    }


    // ============================================================
    // BÖLGEDE SON 24 SAAT MESAJ SAYISI
    // ============================================================

    public int getDistrictTodayCount(
            String district
    ) {

        district = safe(district);

        if (district.isEmpty()) {
            return 0;
        }

        long cutoff =
                System.currentTimeMillis()
                        - 24L
                        * 60L
                        * 60L
                        * 1000L;

        return getDistrictPostCountSince(
                district,
                cutoff
        );
    }


    // ============================================================
    // BÖLGE + ZAMAN SAYACI
    // ============================================================

    public int getDistrictPostCountSince(
            String district,
            long sinceMillis
    ) {

        district = safe(district);

        if (district.isEmpty()) {
            return 0;
        }

        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();

        Cursor cursor = null;

        try {

            cursor = db.rawQuery(

                    "SELECT COUNT(*) " +
                            "FROM posts " +
                            "WHERE is_active = 1 " +
                            "AND district = ? COLLATE NOCASE " +
                            "AND created_at >= ?",

                    new String[]{
                            district,
                            String.valueOf(
                                    Math.max(
                                            0,
                                            sinceMillis
                                    )
                            )
                    }
            );

            if (
                    cursor != null
                            &&
                            cursor.moveToFirst()
            ) {

                return cursor.getInt(0);
            }

        } catch (Exception e) {

            e.printStackTrace();

        } finally {

            close(cursor);
        }

        return 0;
    }


    // ============================================================
    // POST COUNT
    // ============================================================

    public int getActivePostCount() {

        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();

        Cursor cursor = null;

        try {

            cursor = db.rawQuery(

                    "SELECT COUNT(*) " +
                            "FROM posts " +
                            "WHERE is_active = 1",

                    null
            );

            if (
                    cursor != null
                            &&
                            cursor.moveToFirst()
            ) {

                return cursor.getInt(0);
            }

        } catch (Exception e) {

            e.printStackTrace();

        } finally {

            close(cursor);
        }

        return 0;
    }


    // ============================================================
    // MARKET POST COUNT
    // ============================================================

    public int getMarketPostCount() {

        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();

        Cursor cursor = null;

        try {

            cursor = db.rawQuery(

                    "SELECT COUNT(*) " +
                            "FROM posts " +
                            "WHERE is_active = 1 " +
                            "AND (" +
                            "post_type IN ('MARKET', 'SALE') " +
                            "OR is_real_sale = 1" +
                            ")",

                    null
            );

            if (
                    cursor != null
                            &&
                            cursor.moveToFirst()
            ) {

                return cursor.getInt(0);
            }

        } catch (Exception e) {

            e.printStackTrace();

        } finally {

            close(cursor);
        }

        return 0;
    }


    // ============================================================
    // KATEGORİ SAYISI
    // ============================================================

    public int getPostCountByType(
            String type
    ) {

        type = normalizePostType(type);

        if (TYPE_ALL.equals(type)) {
            return getActivePostCount();
        }

        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();

        Cursor cursor = null;

        try {

            if (TYPE_MARKET.equals(type)) {

                cursor = db.rawQuery(

                        "SELECT COUNT(*) " +
                                "FROM posts " +
                                "WHERE is_active = 1 " +
                                "AND (" +
                                "post_type = 'MARKET' " +
                                "OR is_real_sale = 1" +
                                ")",

                        null
                );

            } else if (TYPE_PEST.equals(type)) {

                cursor = db.rawQuery(

                        "SELECT COUNT(*) " +
                                "FROM posts " +
                                "WHERE is_active = 1 " +
                                "AND post_type IN ('PEST', 'SPRAY')",

                        null
                );

            } else if (TYPE_SALE.equals(type)) {

                cursor = db.rawQuery(

                        "SELECT COUNT(*) " +
                                "FROM posts " +
                                "WHERE is_active = 1 " +
                                "AND (" +
                                "post_type = 'SALE' " +
                                "OR is_real_sale = 1" +
                                ")",

                        null
                );

            } else {

                cursor = db.rawQuery(

                        "SELECT COUNT(*) " +
                                "FROM posts " +
                                "WHERE is_active = 1 " +
                                "AND post_type = ? COLLATE NOCASE",

                        new String[]{
                                type
                        }
                );
            }

            if (
                    cursor != null
                            &&
                            cursor.moveToFirst()
            ) {

                return cursor.getInt(0);
            }

        } catch (Exception e) {

            e.printStackTrace();

        } finally {

            close(cursor);
        }

        return 0;
    }


    // ============================================================
    // POST ID İLE GETİR
    // ============================================================

    public PostItem getPostById(
            long postId
    ) {

        if (postId <= 0) {
            return null;
        }

        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();

        Cursor cursor = null;

        try {

            cursor = db.rawQuery(

                    "SELECT * " +
                            "FROM posts " +
                            "WHERE id = ? " +
                            "LIMIT 1",

                    new String[]{
                            String.valueOf(postId)
                    }
            );

            if (
                    cursor != null
                            &&
                            cursor.moveToFirst()
            ) {

                return cursorToPostItem(
                        cursor
                );
            }

        } catch (Exception e) {

            e.printStackTrace();

        } finally {

            close(cursor);
        }

        return null;
    }


    // ============================================================
    // TOP TOPIC
    // ============================================================

    private void calculateTopTopic(
            MeydanSummary summary
    ) {

        summary.topTopic =
                "#Genel";

        summary.topTopicCount =
                summary.generalToday;


        if (
                summary.marketToday
                        >
                        summary.topTopicCount
        ) {

            summary.topTopic =
                    "#Piyasa";

            summary.topTopicCount =
                    summary.marketToday;
        }


        if (
                summary.harvestToday
                        >
                        summary.topTopicCount
        ) {

            summary.topTopic =
                    "#Hasat";

            summary.topTopicCount =
                    summary.harvestToday;
        }


        if (
                summary.pestToday
                        >
                        summary.topTopicCount
        ) {

            summary.topTopic =
                    "#Hastalık";

            summary.topTopicCount =
                    summary.pestToday;
        }


        if (
                summary.irrigationToday
                        >
                        summary.topTopicCount
        ) {

            summary.topTopic =
                    "#Sulama";

            summary.topTopicCount =
                    summary.irrigationToday;
        }


        if (
                summary.saleToday
                        >
                        summary.topTopicCount
        ) {

            summary.topTopic =
                    "#Alım-Satım";

            summary.topTopicCount =
                    summary.saleToday;
        }
    }


    // ============================================================
    // SORU BENZERLİĞİ
    // ============================================================

    public boolean isQuestionLike(
            PostItem item
    ) {

        if (item == null) {
            return false;
        }

        String type =
                normalizePostType(
                        item.postType
                );

        if (TYPE_QUESTION.equals(type)) {
            return true;
        }

        String content =
                safe(item.content)
                        .toLowerCase(
                                new Locale(
                                        "tr",
                                        "TR"
                                )
                        );

        if (content.isEmpty()) {
            return false;
        }

        return content.contains("?")
                || content.contains("var mı")
                || content.contains("bilen var")
                || content.contains("gören var")
                || content.contains("başladı mı")
                || content.contains("kaç para")
                || content.contains("ne kadar")
                || content.contains("ne durumda")
                || content.contains("ne oldu")
                || content.contains("ne öner")
                || content.contains("ne yap")
                || content.contains("kim alıyor")
                || content.contains("alan var mı")
                || content.contains("fiyat bilen")
                || content.contains("bilgisi olan")
                || content.contains("sizce");
    }


    // ============================================================
    // CURSOR -> POST
    // ============================================================

    private PostItem cursorToPostItem(
            Cursor cursor
    ) {

        PostItem item =
                new PostItem();


        item.id =
                getLong(
                        cursor,
                        "id",
                        0
                );


        item.userId =
                getString(
                        cursor,
                        "user_id",
                        ""
                );


        item.userName =
                getString(
                        cursor,
                        "user_name",
                        "Üretici"
                );


        item.province =
                getString(
                        cursor,
                        "province",
                        ""
                );


        item.district =
                getString(
                        cursor,
                        "district",
                        ""
                );


        item.neighborhood =
                getString(
                        cursor,
                        "neighborhood",
                        ""
                );


        item.gardenId =
                getLong(
                        cursor,
                        "garden_id",
                        0
                );


        item.gardenName =
                getString(
                        cursor,
                        "garden_name",
                        ""
                );


        item.variety =
                getString(
                        cursor,
                        "variety",
                        ""
                );


        item.postType =
                normalizePostType(
                        getString(
                                cursor,
                                "post_type",
                                TYPE_GENERAL
                        )
                );


        item.content =
                getString(
                        cursor,
                        "content",
                        ""
                );


        item.photoUri =
                getString(
                        cursor,
                        "photo_uri",
                        ""
                );


        item.usefulCount =
                getInt(
                        cursor,
                        "useful_count",
                        0
                );


        item.notUsefulCount =
                getInt(
                        cursor,
                        "not_useful_count",
                        0
                );


        item.commentCount =
                getInt(
                        cursor,
                        "comment_count",
                        0
                );


        item.realSale =
                getInt(
                        cursor,
                        "is_real_sale",
                        0
                ) == 1;


        item.saleId =
                getLong(
                        cursor,
                        "sale_id",
                        0
                );


        item.saleGrade =
                getString(
                        cursor,
                        "sale_grade",
                        ""
                );


        item.saleKg =
                getDouble(
                        cursor,
                        "sale_kg",
                        0
                );


        item.salePrice =
                getDouble(
                        cursor,
                        "sale_price",
                        0
                );


        item.active =
                getInt(
                        cursor,
                        "is_active",
                        1
                ) == 1;


        item.createdAt =
                getLong(
                        cursor,
                        "created_at",
                        0
                );


        item.updatedAt =
                getLong(
                        cursor,
                        "updated_at",
                        0
                );


        return item;
    }


    // ============================================================
    // FILTER NORMALIZE
    // ============================================================

    private String normalizeFilter(
            String filter
    ) {

        filter =
                safe(filter)
                        .toUpperCase(
                                Locale.ROOT
                        );

        switch (filter) {

            case FILTER_NEARBY:
            case FILTER_FOLLOWING:
            case FILTER_MARKET:
            case FILTER_ALL:

                return filter;

            default:

                return FILTER_ALL;
        }
    }


    // ============================================================
    // POST TYPE NORMALIZE
    // ============================================================

    private String normalizePostType(
            String type
    ) {

        type =
                safe(type)
                        .toUpperCase(
                                Locale.ROOT
                        );

        switch (type) {

            case TYPE_ALL:
            case TYPE_GENERAL:
            case TYPE_MARKET:
            case TYPE_HARVEST:
            case TYPE_PEST:
            case TYPE_SPRAY:
            case TYPE_IRRIGATION:
            case TYPE_FERTILIZER:
            case TYPE_SALE:
            case TYPE_QUESTION:
            case TYPE_GARDEN:

                return type;

            default:

                return TYPE_GENERAL;
        }
    }


    // ============================================================
    // LIMIT NORMALIZE
    // ============================================================

    private int normalizeLimit(
            int limit
    ) {

        if (limit <= 0) {
            return 10;
        }

        /*
         * Meydan yanlışlıkla binlerce View oluşturmamalı.
         * Büyük veri gerektiğinde sayfalama yapılmalı.
         */
        return Math.min(
                limit,
                500
        );
    }


    // ============================================================
    // CURSOR STRING
    // ============================================================

    private String getString(
            Cursor cursor,
            String column,
            String defaultValue
    ) {

        int index =
                cursor.getColumnIndex(
                        column
                );

        if (
                index < 0
                        ||
                        cursor.isNull(index)
        ) {

            return defaultValue;
        }

        String value =
                cursor.getString(index);

        if (value == null) {
            return defaultValue;
        }

        return value;
    }


    // ============================================================
    // CURSOR INT
    // ============================================================

    private int getInt(
            Cursor cursor,
            String column,
            int defaultValue
    ) {

        int index =
                cursor.getColumnIndex(
                        column
                );

        if (
                index < 0
                        ||
                        cursor.isNull(index)
        ) {

            return defaultValue;
        }

        return cursor.getInt(index);
    }


    // ============================================================
    // CURSOR LONG
    // ============================================================

    private long getLong(
            Cursor cursor,
            String column,
            long defaultValue
    ) {

        int index =
                cursor.getColumnIndex(
                        column
                );

        if (
                index < 0
                        ||
                        cursor.isNull(index)
        ) {

            return defaultValue;
        }

        return cursor.getLong(index);
    }


    // ============================================================
    // CURSOR DOUBLE
    // ============================================================

    private double getDouble(
            Cursor cursor,
            String column,
            double defaultValue
    ) {

        int index =
                cursor.getColumnIndex(
                        column
                );

        if (
                index < 0
                        ||
                        cursor.isNull(index)
        ) {

            return defaultValue;
        }

        return cursor.getDouble(index);
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


    // ============================================================
    // CURSOR CLOSE
    // ============================================================

    private void close(
            Cursor cursor
    ) {

        if (cursor == null) {
            return;
        }

        try {
            cursor.close();
        } catch (Exception ignored) {
        }
    }
}