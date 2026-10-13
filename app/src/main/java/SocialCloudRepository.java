package com.zeytindefteri.app;

import android.content.Context;
import android.database.Cursor;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * ================================================================
 * SOCIAL CLOUD REPOSITORY
 * ================================================================
 *
 * Meydan'ın Firebase / Firestore köprüsüdür.
 *
 * Mimari:
 *
 * - SQLite hızlı yerel önbellektir.
 * - Firestore ortak sosyal veri kaynağıdır.
 * - Kullanıcının gönderisi önce mevcut yerel sistemde oluşur.
 * - Ardından bu sınıf gönderiyi Firestore'a yayınlar.
 * - Diğer kullanıcıların gönderileri Firestore'dan alınarak
 *   mevcut SQLite posts tablosuna aktarılır.
 *
 *
 * FINAL MEYDAN KURALI:
 *
 * Meydan artık metin odaklıdır.
 *
 * Bu nedenle yeni Meydan mesajlarında:
 *
 * - fotoğraf yüklenmez
 * - video yüklenmez
 * - Firebase Storage kullanılmaz
 *
 *
 * ÖNEMLİ:
 *
 * Eski Firestore belgelerindeki:
 *
 * - photo_uri
 * - media_urls
 *
 * alanlarını silmiyoruz.
 *
 * Böylece geçmiş veriler zarar görmez.
 *
 *
 * Bu sınıf:
 *
 * - DatabaseHelper şemasını değiştirmez
 * - migration yapmaz
 * - Firebase projesini değiştirmez
 * - mevcut social_posts koleksiyonunu korur
 *
 * ================================================================
 */
public class SocialCloudRepository {


    // ============================================================
    // CALLBACK
    // ============================================================

    public interface Callback {

        void onComplete(
                boolean success
        );
    }


    // ============================================================
    // DATABASE
    // ============================================================

    private boolean closed;
    private int nextCallback;
    private final java.util.Map<Integer, Callback> callbacks = new java.util.HashMap<>();
    private final java.util.Set<Long> pendingWrites = new java.util.HashSet<>();
    private final DatabaseHelper databaseHelper;


    // ============================================================
    // FIREBASE
    // ============================================================

    private final FirebaseAuth auth;

    private final FirebaseFirestore firestore;


    // ============================================================
    // COLLECTION
    // ============================================================

    private static final String COLLECTION_POSTS =
            "social_posts";


    // ============================================================
    // LIMIT
    // ============================================================

    private static final int SYNC_LIMIT =
            250;


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public SocialCloudRepository(
            Context context,
            DatabaseHelper databaseHelper
    ) {

        if (databaseHelper == null) {

            throw new IllegalArgumentException(
                    "DatabaseHelper null olamaz."
            );
        }


        this.databaseHelper =
                databaseHelper;


        this.auth =
                FirebaseAuth.getInstance();


        this.firestore =
                FirebaseFirestore.getInstance();
    }


    // ============================================================
    // LOCAL POST -> FIRESTORE
    // ============================================================

    public void publishLocalPost(
            long postId,
            Callback callback
    ) {
        if (closed) return;
        final int callbackKey = ++nextCallback;
        if (callback != null) callbacks.put(callbackKey, callback);

        FirebaseUser user =
                getCurrentUser();


        if (
                user == null
                        ||
                        postId <= 0
        ) {

            finishStored(callbackKey,
                    false
            );

            return;
        }


        Cursor post =
                null;


        try {

            post =
                    databaseHelper.getPostById(
                            postId
                    );


            if (
                    post == null
                            ||
                            !post.moveToFirst()
            ) {

                finishStored(callbackKey,
                        false
                );

                return;
            }


            // ====================================================
            // CLOUD ID
            // ====================================================

            String cloudId =
                    safe(
                            str(
                                    post,
                                    "cloud_id"
                            )
                    );


            if (cloudId.isEmpty()) {

                cloudId =
                        UUID.randomUUID()
                                .toString();
            }


            // ====================================================
            // OWNER
            // ====================================================

            /*
             * Firestore'a gönderilen user_id konusunda Firebase
             * oturumunu gerçek kimlik kaynağı olarak kullanıyoruz.
             *
             * Yerel kayıtta user_id varsa ve mevcut mimaride zaten
             * Firebase UID ise aynı değer olacaktır.
             *
             * Böylece başka bir kullanıcının uid'si istemeden
             * Firestore'a yazılamaz.
             */
            String firebaseUid =
                    safe(
                            user.getUid()
                    );


            if (firebaseUid.isEmpty()) {

                finishStored(callbackKey,
                        false
                );

                return;
            }


            // ====================================================
            // DATA
            // ====================================================

            if (!firebaseUid.equals(str(post, "user_id"))) {
                finishStored(callbackKey, false); return;
            }
            android.content.ContentValues local = new android.content.ContentValues();
            local.put("cloud_id", cloudId);
            local.put("sync_state", 1);
            databaseHelper.getWritableDatabase().update("posts", local, "id = ?",
                    new String[]{String.valueOf(postId)});
            final long localVersion = number(post, "updated_at");

            Map<String, Object> data =
                    new HashMap<>();


            data.put(
                    "cloud_id",
                    cloudId
            );


            data.put(
                    "user_id",
                    firebaseUid
            );


            data.put(
                    "user_name",
                    safeOr(
                            str(
                                    post,
                                    "user_name"
                            ),
                            getFirebaseDisplayName(
                                    user
                            )
                    )
            );


            data.put(
                    "province",
                    str(
                            post,
                            "province"
                    )
            );


            data.put(
                    "district",
                    str(
                            post,
                            "district"
                    )
            );


            data.put(
                    "neighborhood",
                    str(
                            post,
                            "neighborhood"
                    )
            );


            data.put(
                    "garden_name",
                    str(
                            post,
                            "garden_name"
                    )
            );


            data.put(
                    "variety",
                    str(
                            post,
                            "variety"
                    )
            );


            data.put(
                    "post_type",
                    safeOr(
                            str(
                                    post,
                                    "post_type"
                            ),
                            "GENERAL"
                    )
            );


            data.put(
                    "content",
                    str(
                            post,
                            "content"
                    )
            );


            // ====================================================
            // REAL SALE DATA
            // ====================================================

            data.put(
                    "is_real_sale",
                    integer(
                            post,
                            "is_real_sale"
                    )
            );


            data.put(
                    "sale_grade",
                    str(
                            post,
                            "sale_grade"
                    )
            );


            data.put(
                    "sale_kg",
                    decimal(
                            post,
                            "sale_kg"
                    )
            );


            data.put(
                    "sale_price",
                    decimal(
                            post,
                            "sale_price"
                    )
            );


            // ====================================================
            // TIME
            // ====================================================

            long createdAt =
                    number(
                            post,
                            "created_at"
                    );


            if (createdAt <= 0) {

                createdAt =
                        System.currentTimeMillis();
            }


            data.put(
                    "created_at",
                    createdAt
            );


            data.put(
                    "updated_at",
                    System.currentTimeMillis()
            );


            // ====================================================
            // TEXT ONLY MEYDAN
            // ====================================================

            /*
             * Yeni Meydan sürümünde medya YÜKLEMİYORUZ.
             *
             * Burada özellikle:
             *
             * data.put("photo_uri", "")
             * data.put("media_urls", ...)
             *
             * yapmıyoruz.
             *
             * Çünkü set(...), mevcut cloudId üzerinde belgeyi
             * yeniden yazarsa eski medya alanlarının yanlışlıkla
             * silinmesini istemiyoruz.
             *
             * Bunun yerine merge kullanıyoruz.
             */


            // ====================================================
            // FIRESTORE WRITE
            // ====================================================

            firestore
                    .collection(
                            COLLECTION_POSTS
                    )
                    .document(
                            cloudId
                    )
                    .set(
                            data,
                            com.google.firebase.firestore
                                    .SetOptions
                                    .merge()
                    )
                    .addOnSuccessListener(
                            unused -> {
                        if (!closed) {
                            android.content.ContentValues synced = new android.content.ContentValues();
                            synced.put("sync_state", 2);
                            databaseHelper.getWritableDatabase().update("posts", synced,
                                    "id = ? AND updated_at = ?", new String[]{String.valueOf(postId),String.valueOf(localVersion)});
                            finishStored(callbackKey, true);
                        }
                    }
                    )
                    .addOnFailureListener(
                            error -> {

                                error.printStackTrace();


                                finishStored(callbackKey,
                                        false
                                );
                            }
                    );


        } catch (Exception e) {

            e.printStackTrace();


            finishStored(callbackKey,
                    false
            );


        } finally {

            closeCursor(
                    post
            );
        }
    }


    // ============================================================
    // FIRESTORE -> SQLITE
    // ============================================================

    public void syncLatestPosts(
            Callback callback
    ) {
        if (closed) return;
        final int callbackKey = ++nextCallback;
        if (callback != null) callbacks.put(callbackKey, callback);

        FirebaseUser user =
                getCurrentUser();


        if (user == null) {

            finishStored(callbackKey,
                    false
            );

            return;
        }


        try {

            firestore
                    .collection(
                            COLLECTION_POSTS
                    )
                    .orderBy(
                            "created_at",
                            Query.Direction.DESCENDING
                    )
                    .limit(
                            SYNC_LIMIT
                    )
                    .get()
                    .addOnSuccessListener(
                            snapshot -> {

                                try {

                                    if (closed) return;
                                    for (
                                            DocumentSnapshot document
                                            :
                                            snapshot.getDocuments()
                                    ) {

                                        importCloudPost(
                                                document
                                        );
                                    }


                                    finishStored(callbackKey,
                                            true
                                    );


                                } catch (Exception e) {

                                    e.printStackTrace();


                                    finishStored(callbackKey,
                                            false
                                    );
                                }
                            }
                    )
                    .addOnFailureListener(
                            error -> {

                                error.printStackTrace();


                                finishStored(callbackKey,
                                        false
                                );
                            }
                    );


        } catch (Exception e) {

            e.printStackTrace();


            finishStored(callbackKey,
                    false
            );
        }
    }


    // ============================================================
    // IMPORT ONE CLOUD POST
    // ============================================================

    private void importCloudPost(
            DocumentSnapshot document
    ) {

        if (document == null) {

            return;
        }


        String cloudId =
                value(
                        document,
                        "cloud_id",
                        document.getId()
                );


        if (safe(cloudId).isEmpty()) {

            return;
        }


        long localId =
                databaseHelper.upsertPostFromCloud(

                        cloudId,

                        value(
                                document,
                                "user_id",
                                ""
                        ),

                        value(
                                document,
                                "user_name",
                                "Üretici"
                        ),

                        value(
                                document,
                                "province",
                                ""
                        ),

                        value(
                                document,
                                "district",
                                ""
                        ),

                        value(
                                document,
                                "neighborhood",
                                ""
                        ),

                        value(
                                document,
                                "garden_name",
                                ""
                        ),

                        value(
                                document,
                                "variety",
                                ""
                        ),

                        value(
                                document,
                                "post_type",
                                "GENERAL"
                        ),

                        value(
                                document,
                                "content",
                                ""
                        ),

                        /*
                         * Eski gönderilerin fotoğraf alanını
                         * okumaya devam ediyoruz.
                         *
                         * Böylece eski veriler kaybolmaz.
                         */
                        value(
                                document,
                                "photo_uri",
                                ""
                        ),

                        intValue(
                                document,
                                "is_real_sale"
                        ),

                        value(
                                document,
                                "sale_grade",
                                ""
                        ),

                        doubleValue(
                                document,
                                "sale_kg"
                        ),

                        doubleValue(
                                document,
                                "sale_price"
                        ),

                        longValue(
                                document,
                                "created_at"
                        ),

                        longValue(
                                document,
                                "updated_at"
                        )
                );


        // ========================================================
        // LEGACY MEDIA
        // ========================================================

        /*
         * Yeni Meydan medya üretmese bile eski gönderilerin
         * media_urls listesini SQLite tarafında koruyoruz.
         */

        if (localId > 0) {

            List<String> urls =
                    stringList(
                            document.get(
                                    "media_urls"
                            )
                    );


            if (!urls.isEmpty()) {

                databaseHelper
                        .replaceCloudPostMedia(
                                localId,
                                urls
                        );
            }
        }
    }


    // ============================================================
    // CURRENT USER
    // ============================================================

    private FirebaseUser getCurrentUser() {

        try {

            return auth.getCurrentUser();

        } catch (Exception ignored) {

            return null;
        }
    }


    // ============================================================
    // FIREBASE DISPLAY NAME
    // ============================================================

    private String getFirebaseDisplayName(
            FirebaseUser user
    ) {

        if (user == null) {

            return "Üretici";
        }


        String displayName =
                safe(
                        user.getDisplayName()
                );


        if (!displayName.isEmpty()) {

            return displayName;
        }


        return "Üretici";
    }


    // ============================================================
    // CURSOR STRING
    // ============================================================

    private static String str(
            Cursor cursor,
            String name
    ) {

        if (cursor == null) {

            return "";
        }


        int index =
                cursor.getColumnIndex(
                        name
                );


        if (
                index < 0
                        ||
                        cursor.isNull(
                                index
                        )
        ) {

            return "";
        }


        String result =
                cursor.getString(
                        index
                );


        return result == null
                ?
                ""
                :
                result;
    }


    // ============================================================
    // CURSOR LONG
    // ============================================================

    private static long number(
            Cursor cursor,
            String name
    ) {

        if (cursor == null) {

            return 0;
        }


        int index =
                cursor.getColumnIndex(
                        name
                );


        if (
                index < 0
                        ||
                        cursor.isNull(
                                index
                        )
        ) {

            return 0;
        }


        return cursor.getLong(
                index
        );
    }


    // ============================================================
    // CURSOR INT
    // ============================================================

    private static int integer(
            Cursor cursor,
            String name
    ) {

        return (int) number(
                cursor,
                name
        );
    }


    // ============================================================
    // CURSOR DOUBLE
    // ============================================================

    private static double decimal(
            Cursor cursor,
            String name
    ) {

        if (cursor == null) {

            return 0;
        }


        int index =
                cursor.getColumnIndex(
                        name
                );


        if (
                index < 0
                        ||
                        cursor.isNull(
                                index
                        )
        ) {

            return 0;
        }


        return cursor.getDouble(
                index
        );
    }


    // ============================================================
    // FIRESTORE STRING
    // ============================================================

    private static String value(
            DocumentSnapshot document,
            String key,
            String fallback
    ) {

        if (document == null) {

            return fallback;
        }


        Object value =
                document.get(
                        key
                );


        if (value == null) {

            return fallback;
        }


        return String.valueOf(
                value
        );
    }


    // ============================================================
    // FIRESTORE LONG
    // ============================================================

    private static long longValue(
            DocumentSnapshot document,
            String key
    ) {

        if (document == null) {

            return 0;
        }


        Object raw =
                document.get(
                        key
                );


        if (raw instanceof Number) {

            return (
                    (Number) raw
            ).longValue();
        }


        if (raw != null) {

            try {

                return Long.parseLong(
                        String.valueOf(
                                raw
                        )
                );

            } catch (Exception ignored) {
            }
        }


        return 0;
    }


    // ============================================================
    // FIRESTORE INT
    // ============================================================

    private static int intValue(
            DocumentSnapshot document,
            String key
    ) {

        return (int) longValue(
                document,
                key
        );
    }


    // ============================================================
    // FIRESTORE DOUBLE
    // ============================================================

    private static double doubleValue(
            DocumentSnapshot document,
            String key
    ) {

        if (document == null) {

            return 0;
        }


        Object raw =
                document.get(
                        key
                );


        if (raw instanceof Number) {

            return (
                    (Number) raw
            ).doubleValue();
        }


        if (raw != null) {

            try {

                return Double.parseDouble(
                        String.valueOf(
                                raw
                        )
                );

            } catch (Exception ignored) {
            }
        }


        return 0;
    }


    // ============================================================
    // FIRESTORE STRING LIST
    // ============================================================

    private static List<String> stringList(
            Object raw
    ) {

        java.util.ArrayList<String> output =
                new java.util.ArrayList<>();


        if (!(raw instanceof List)) {

            return output;
        }


        for (
                Object item
                :
                (List<?>) raw
        ) {

            if (item == null) {

                continue;
            }


            String value =
                    safe(
                            String.valueOf(
                                    item
                            )
                    );


            if (!value.isEmpty()) {

                output.add(
                        value
                );
            }
        }


        return output;
    }


    // ============================================================
    // SAFE
    // ============================================================

    private static String safe(
            String value
    ) {

        if (value == null) {

            return "";
        }


        return value.trim();
    }


    // ============================================================
    // SAFE OR
    // ============================================================

    private static String safeOr(
            String value,
            String fallback
    ) {

        String result =
                safe(
                        value
                );


        if (!result.isEmpty()) {

            return result;
        }


        String fallbackResult =
                safe(
                        fallback
                );


        return fallbackResult.isEmpty()
                ?
                "Üretici"
                :
                fallbackResult;
    }


    // ============================================================
    // CURSOR CLOSE
    // ============================================================

    private static void closeCursor(
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


    // ============================================================
    // CALLBACK
    // ============================================================

    private static void finish(
            Callback callback,
            boolean success
    ) {

        if (callback != null) {

            callback.onComplete(
                    success
            );
        }
    }

    public long createTextPost(String content, String category) {
        FirebaseUser user = getCurrentUser();
        String text = safe(content);
        if (closed || user == null || text.length() < 3 || text.length() > 2000) return -1;
        String type = safe(category);
        if (!java.util.Arrays.asList("MARKET","PEST","HARVEST","IRRIGATION","SALE").contains(type)) type = "GENERAL";
        String[] location = activeGardenPostLocation(user.getUid());
        long id = databaseHelper.addPost(user.getUid(), getFirebaseDisplayName(user),
                location[0], location[1], location[2],
                null, "", "", type, text, "");
        if (id > 0) publishLocalPost(id, null);
        return id;
    }
    /** Snapshot the selected garden's location only for a newly created quick post. */
    private String[] activeGardenPostLocation(String uid) {
        String[] location = {"", "", ""};
        long gardenId;
        try {
            gardenId = Long.parseLong(databaseHelper.getSetting("home_active_garden_id:" + uid, "0"));
        } catch (NumberFormatException ignored) { return location; }
        if (gardenId <= 0) return location;
        // Never borrow another account's garden or fall back to unscoped profile settings.
        try (Cursor garden = databaseHelper.getReadableDatabase().rawQuery(
                "SELECT province, district, neighborhood FROM gardens WHERE id = ? AND owner_uid = ? LIMIT 1",
                new String[]{String.valueOf(gardenId), uid})) {
            if (garden.moveToFirst()) {
                location[0] = safe(str(garden, "province"));
                location[1] = safe(str(garden, "district"));
                location[2] = safe(str(garden, "neighborhood"));
            }
        }
        return location;
    }

    public void retryPendingPosts() {
        FirebaseUser user = getCurrentUser();
        if (closed || user == null) return;
        try (Cursor c = databaseHelper.getReadableDatabase().rawQuery(
                "SELECT id FROM posts WHERE user_id = ? AND sync_state = 1 AND is_active = 1 ORDER BY id LIMIT 50",
                new String[]{user.getUid()})) {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                if (pendingWrites.add(id)) publishLocalPost(id, success -> {
                    pendingWrites.remove(id);
                    if (success && !closed) retryPendingPosts();
                });
            }
        }
    }
    private void finishStored(int key, boolean success) {
        Callback callback = callbacks.remove(key);
        if (!closed && callback != null) callback.onComplete(success);
    }
    public void close() { closed = true; callbacks.clear(); }

    /** Fetch a single notification target, without downloading the full feed. */
    public void fetchPost(String cloudId, Callback callback) {
        if (closed || getCurrentUser() == null || !ZeytinMessagingService.validId(cloudId)) {
            if (callback != null) callback.onComplete(false); return;
        }
        final int key = ++nextCallback;
        if (callback != null) callbacks.put(key, callback);
        firestore.collection(COLLECTION_POSTS).document(cloudId).get().addOnSuccessListener(doc -> {
            if (closed) return;
            try {
                if (!doc.exists()) { finishStored(key, false); return; }
                importCloudPost(doc); finishStored(key, true);
            } catch (RuntimeException error) { finishStored(key, false); }
        }).addOnFailureListener(error -> finishStored(key, false));
    }
}
