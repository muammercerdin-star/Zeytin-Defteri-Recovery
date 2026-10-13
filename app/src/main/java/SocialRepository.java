package com.zeytindefteri.app;

import android.database.Cursor;


/**
 * ================================================================
 * SOCIAL REPOSITORY
 * ================================================================
 *
 * Sosyal gönderilerle ilgili temel veri işlemlerini yönetir.
 *
 * Bu sınıfın görevi:
 *
 * - Gönderi eklemek
 * - Gerçek satış gönderisi eklemek
 * - Son gönderileri getirmek
 * - İlçeye göre gönderileri getirmek
 * - Gönderi türüne göre filtrelemek
 * - Gönderi kaydetmek / kayıttan çıkarmak
 * - Kaydedilmiş gönderileri getirmek
 * - Gönderiyi pasife almak
 *
 *
 * NOT:
 *
 * Reaction işlemleri:
 *      ReactionManager.java
 *
 * Yorum işlemleri:
 *      CommentManager.java
 *
 * Fotoğraf / video işlemleri:
 *      PostMediaManager.java
 *
 * Akış oluşturma:
 *      FeedManager.java
 *
 * tarafından yönetilecek.
 *
 * ================================================================
 */
public class SocialRepository {


    // ============================================================
    // DATABASE
    // ============================================================

    private final DatabaseHelper databaseHelper;


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public SocialRepository(
            DatabaseHelper databaseHelper
    ) {


        if (databaseHelper == null) {


            throw new IllegalArgumentException(
                    "DatabaseHelper null olamaz."
            );
        }


        this.databaseHelper =
                databaseHelper;
    }


    // ============================================================
    // NORMAL GÖNDERİ EKLE
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


        // ========================================================
        // TEMEL KONTROLLER
        // ========================================================

        if (safe(
                userName
        ).isEmpty()) {


            return -1;
        }


        if (safe(
                content
        ).isEmpty()) {


            return -1;
        }


        // ========================================================
        // DATABASE
        // ========================================================

        return databaseHelper.addPost(
                safe(
                        userId
                ),
                safe(
                        userName
                ),
                safe(
                        province
                ),
                safe(
                        district
                ),
                safe(
                        neighborhood
                ),
                normalizeId(
                        gardenId
                ),
                safe(
                        gardenName
                ),
                safe(
                        variety
                ),
                normalizePostType(
                        postType
                ),
                safe(
                        content
                ),
                safe(
                        photoUri
                )
        );
    }


    // ============================================================
    // GERÇEK SATIŞ GÖNDERİSİ
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


        // ========================================================
        // KULLANICI
        // ========================================================

        if (safe(
                userName
        ).isEmpty()) {


            return -1;
        }


        // ========================================================
        // SATIŞ
        // ========================================================

        if (saleId <= 0) {


            return -1;
        }


        // ========================================================
        // DEĞERLER
        // ========================================================

        double safeKg =
                Math.max(
                        kg,
                        0
                );


        double safePrice =
                Math.max(
                        pricePerKg,
                        0
                );


        // ========================================================
        // DATABASE
        // ========================================================

        return databaseHelper.addRealSalePost(
                safe(
                        userId
                ),
                safe(
                        userName
                ),
                safe(
                        province
                ),
                safe(
                        district
                ),
                safe(
                        neighborhood
                ),
                normalizeId(
                        gardenId
                ),
                safe(
                        gardenName
                ),
                safe(
                        variety
                ),
                saleId,
                safe(
                        grade
                ),
                safeKg,
                safePrice,
                safe(
                        content
                )
        );
    }


    // ============================================================
    // SON GÖNDERİLER
    // ============================================================

    public Cursor getLatestPosts(
            int limit
    ) {


        return databaseHelper.getLatestPosts(
                normalizeLimit(
                        limit
                )
        );
    }


    // ============================================================
    // İLÇEYE GÖRE GÖNDERİLER
    // ============================================================

    public Cursor getPostsByDistrict(
            String district,
            int limit
    ) {


        district =
                safe(
                        district
                );


        // İlçe boşsa genel akışı döndür.
        if (district.isEmpty()) {


            return getLatestPosts(
                    limit
            );
        }


        return databaseHelper.getPostsByDistrict(
                district,
                normalizeLimit(
                        limit
                )
        );
    }


    // ============================================================
    // GÖNDERİ TÜRÜNE GÖRE
    // ============================================================

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


        return databaseHelper.getPostsByType(
                normalizePostType(
                        postType
                ),
                normalizeLimit(
                        limit
                )
        );
    }


    // ============================================================
    // PİYASA GÖNDERİLERİ
    // ============================================================

    public Cursor getMarketPosts(
            int limit
    ) {


        return getPostsByType(
                "MARKET",
                limit
        );
    }


    // ============================================================
    // SATIŞ GÖNDERİLERİ
    // ============================================================

    public Cursor getSalePosts(
            int limit
    ) {


        return getPostsByType(
                "SALE",
                limit
        );
    }


    // ============================================================
    // SORU GÖNDERİLERİ
    // ============================================================

    public Cursor getQuestionPosts(
            int limit
    ) {


        return getPostsByType(
                "QUESTION",
                limit
        );
    }


    // ============================================================
    // BAHÇE GÖNDERİLERİ
    // ============================================================

    public Cursor getGardenPosts(
            int limit
    ) {


        return getPostsByType(
                "GARDEN",
                limit
        );
    }


    // ============================================================
    // HASAT GÖNDERİLERİ
    // ============================================================

    public Cursor getHarvestPosts(
            int limit
    ) {


        return getPostsByType(
                "HARVEST",
                limit
        );
    }


    // ============================================================
    // SULAMA GÖNDERİLERİ
    // ============================================================

    public Cursor getIrrigationPosts(
            int limit
    ) {


        return getPostsByType(
                "IRRIGATION",
                limit
        );
    }


    // ============================================================
    // GÜBRE GÖNDERİLERİ
    // ============================================================

    public Cursor getFertilizerPosts(
            int limit
    ) {


        return getPostsByType(
                "FERTILIZER",
                limit
        );
    }


    // ============================================================
    // İLAÇLAMA GÖNDERİLERİ
    // ============================================================

    public Cursor getSprayPosts(
            int limit
    ) {


        return getPostsByType(
                "SPRAY",
                limit
        );
    }


    // ============================================================
    // ZARARLI / HASTALIK
    // ============================================================

    public Cursor getPestPosts(
            int limit
    ) {


        return getPostsByType(
                "PEST",
                limit
        );
    }


    // ============================================================
    // GENEL GÖNDERİLER
    // ============================================================

    public Cursor getGeneralPosts(
            int limit
    ) {


        return getPostsByType(
                "GENERAL",
                limit
        );
    }


    // ============================================================
    // GÖNDERİ KAYDET / KAYITTAN ÇIKAR
    // ============================================================

    public boolean toggleSave(
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


        return databaseHelper.togglePostSave(
                postId,
                userKey
        );
    }


    // ============================================================
    // GÖNDERİ KAYITLI MI?
    // ============================================================

    public boolean isSaved(
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


        return databaseHelper.isPostSavedByUser(
                postId,
                userKey
        );
    }


    // ============================================================
    // KAYDEDİLEN GÖNDERİLER
    // ============================================================

    public Cursor getSavedPosts(
            String userKey,
            int limit
    ) {


        userKey =
                safe(
                        userKey
                );


        return databaseHelper.getSavedPosts(
                userKey,
                normalizeLimit(
                        limit
                )
        );
    }


    // ============================================================
    // GÖNDERİ SİL
    //
    // DatabaseHelper fiziksel DELETE yapmıyor.
    // is_active = 0 yapıyor.
    //
    // Böylece gönderi tamamen kaybolmadan pasife alınmış oluyor.
    // ============================================================

    public boolean deletePost(
            long postId
    ) {


        if (postId <= 0) {


            return false;
        }


        int affectedRows =
                databaseHelper.deletePost(
                        postId
                );


        return affectedRows > 0;
    }


    // ============================================================
    // TEPKİ TOPLAMI
    //
    // FeedManager gibi sınıflar kullanabilir.
    // Reaction değiştirme işi ReactionManager'da kalacak.
    // ============================================================

    public int getReactionCount(
            long postId
    ) {


        if (postId <= 0) {


            return 0;
        }


        return databaseHelper.getPostTotalReactionCount(
                postId
        );
    }


    // ============================================================
    // TEK TEPKİ SAYISI
    // ============================================================

    public int getReactionCount(
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


        if (reactionType.isEmpty()) {


            return 0;
        }


        return databaseHelper.getPostReactionCount(
                postId,
                reactionType
        );
    }


    // ============================================================
    // KULLANICININ TEPKİSİ
    // ============================================================

    public String getUserReaction(
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


        String result =
                databaseHelper.getPostReactionByUser(
                        postId,
                        userKey
                );


        result =
                safe(
                        result
                )
                        .toUpperCase();


        if (result.isEmpty()) {


            return "NONE";
        }


        return result;
    }


    // ============================================================
    // POST TYPE NORMALIZE
    // ============================================================

    private String normalizePostType(
            String postType
    ) {


        postType =
                safe(
                        postType
                )
                        .toUpperCase();


        switch (postType) {


            case "QUESTION":

            case "GARDEN":

            case "PEST":

            case "IRRIGATION":

            case "FERTILIZER":

            case "SPRAY":

            case "HARVEST":

            case "MARKET":

            case "SALE":

            case "GENERAL":


                return postType;


            default:


                return "GENERAL";
        }
    }


    // ============================================================
    // LIMIT
    // ============================================================

    private int normalizeLimit(
            int limit
    ) {


        if (limit <= 0) {


            return 20;
        }


        /*
         * Yanlışlıkla binlerce gönderinin aynı anda
         * çekilmesini engelliyoruz.
         */
        return Math.min(
                limit,
                100
        );
    }


    // ============================================================
    // NULLABLE ID
    // ============================================================

    private Long normalizeId(
            Long value
    ) {


        if (value == null
                ||
                value <= 0) {


            return null;
        }


        return value;
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