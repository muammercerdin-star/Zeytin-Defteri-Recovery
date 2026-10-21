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
    private final java.util.Set<Long> pendingDeletes = new java.util.HashSet<>();
    private final java.util.Set<String> verifying = new java.util.HashSet<>();
    private com.google.firebase.firestore.ListenerRegistration liveRegistration;
    private boolean liveSawServer;
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

    // Canlı akış penceresi. Eski mesajlar telefonda kalır; buluttan her açılışta yalnız son 60 gönderi
    // okunur (250 iken 10 bin kullanıcıda okuma maliyetinin çoğu buradaydı).
    private static final int SYNC_LIMIT =
            60;

    /** Bir eşleştirmede sunucudan doğrulanacak en fazla eksik gönderi. */
    private static final int RECONCILE_BATCH =
            25;


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    /** Moderasyon durumu önbelleği için (ChatGuard); null olabilir. */
    private final Context appContext;

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
        this.appContext = context == null ? null : context.getApplicationContext();


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
            // firestore.rules ile aynı sınır: boş ya da 5.000 karakterden uzun metin buluta yazılamaz.
            // Böyle bir gönderi telefonda kalır; ağa hiç istek gitmez.
            if (!isPublishableContent(str(post, "content"))) {
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


            // user_name aşağıda, doğrulanmış oturum belgesindeki addan eklenir (firestore.rules
            // postAuthorName ile aynı kaynak). Yerel kayıttaki ad buluta yazılmaz.


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

            // Doğrulama işareti sunucunun işidir; kurallar istemciden yalnızca 0 kabul eder.
            data.put(
                    "is_real_sale",
                    0
            );

            // Moderasyon: yeni üyenin ilk gönderileri onaya düşer (PENDING). Durum yalnız ilk yazımda gönderilir;
            // sonra moderatör değiştirir ve kurallar yazarın bu alanı değiştirmesine izin vermez.
            if (appContext != null) {
                String status = com.zeytindefteri.app.ChatGuard.firstStatus(appContext, cloudId);
                if (status != null) data.put("status", status);
            }


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

            final String targetCloudId = cloudId;
            // Firestore aynı önbellekteki oturum belgesini kullanır; ad buradan okunursa
            // kuraldaki request.auth.token.name ile aynı olur.
            user.getIdToken(false).addOnCompleteListener(tokenTask -> {
                if (closed) return;
                if (!tokenTask.isSuccessful() || tokenTask.getResult() == null) {
                    finishStored(callbackKey, false);
                    return;
                }
                data.put("user_name", authorNameFromClaims(tokenTask.getResult().getClaims()));
                firestore
                        .collection(
                                COLLECTION_POSTS
                        )
                        .document(
                                targetCloudId
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
            });


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
                                    reconcileWindow(snapshot.getDocuments(),
                                            snapshot.getMetadata().isFromCache(), null);


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

        // Bulutta silinmiş gönderi telefonda da kapanır. Telefonda buluta henüz yazılmamış değişikliği
        // olan gönderi (silinmiş ya da gönderilmekte olan) ezilmez: eskiden upsert is_active=1 yazıp
        // silinen gönderiyi diriltiyordu; canlı akış ise gönderilen mesajın onaylanmamış yankısını
        // okuyup updated_at'i değiştiriyor, onay kaydı eşleyemiyor ve mesaj "Gönderiliyor…" kalıyordu.
        // Moderasyon: gizlenen, 3+ şikâyet alan ya da başkasının onay bekleyen gönderisi telefonda gösterilmez.
        String modStatus = com.zeytindefteri.app.MeydanModeration.status(document.get("status"));
        int modReports = com.zeytindefteri.app.MeydanModeration.reports(document.get("report_count"));
        String me = auth.getCurrentUser() == null ? "" : auth.getCurrentUser().getUid();
        boolean mine = !me.isEmpty() && me.equals(value(document, "user_id", ""));
        if (mine && appContext != null) com.zeytindefteri.app.ChatGuard.rememberStatus(appContext, cloudId, modStatus);
        if (!com.zeytindefteri.app.MeydanModeration.visible(modStatus, modReports, mine)) {
            databaseHelper.deactivatePostsFromCloud(java.util.Collections.singletonList(cloudId));
            return;
        }
        MeydanPostSync.Action action = MeydanPostSync.importAction(
                MeydanPostSync.cloudActive(document.get("is_active")),
                databaseHelper.isPostLocalChangePending(cloudId));
        if (action == MeydanPostSync.Action.DEACTIVATE) {
            databaseHelper.deactivatePostsFromCloud(java.util.Collections.singletonList(cloudId));
            return;
        }
        if (action == MeydanPostSync.Action.SKIP) {
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

    private static boolean isPublishableContent(String content) {
        return MeydanPostPolicy.isPublishableContent(content);
    }

    private static String authorNameFromClaims(Map<String, Object> claims) {
        return MeydanPostPolicy.authorNameFromClaims(claims);
    }

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
        return createTextPost(content, category, null);
    }

    /** onSent: buluta yazma bitti (başarılı ya da değil); arayüz "Gönderiliyor…" etiketini yeniler. */
    public long createTextPost(String content, String category, Callback onSent) {
        FirebaseUser user = getCurrentUser();
        String text = safe(content);
        if (closed || user == null || text.length() < 3 || text.length() > 2000) return -1;
        String type = safe(category);
        if (!java.util.Arrays.asList("MARKET","PEST","HARVEST","IRRIGATION","SALE").contains(type)) type = "GENERAL";
        String[] location = activeGardenPostLocation(user.getUid());
        long id = databaseHelper.addPost(user.getUid(), getFirebaseDisplayName(user),
                location[0], location[1], location[2],
                null, "", "", type, text, "");
        if (id > 0) publishLocalPost(id, onSent);
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
                    // Sıradakilere yalnızca bu gönderi gerçekten senkron olduysa geç; olmadıysa aynı
                    // gönderiyi tekrar tekrar yazan bir döngü oluşur. Kalanlar sonraki açılışta denenir.
                    if (success && !closed && !isPostPending(id)) retryPendingPosts();
                });
            }
        }
        for (Long id : databaseHelper.getPendingPostDeletionIds(user.getUid())) {
            if (id != null) publishPostDeletion(id, null);
        }
    }

    // ============================================================
    // DELETE -> FIRESTORE
    // ============================================================

    /**
     * Telefonda silinen kendi gönderisini bulutta is_active=0 yapar (belge silinmez; yorumlar ve
     * bildirim fonksiyonu kapalı gönderiyi zaten atlar). Başarısızsa sync_state=1 kalır ve
     * retryPendingPosts sonraki açılışta yeniden dener.
     */
    public void publishPostDeletion(long postId, Callback callback) {
        FirebaseUser user = getCurrentUser();
        if (closed || user == null || postId <= 0 || !pendingDeletes.add(postId)) {
            complete(callback, false);
            return;
        }
        String cloudId = "";
        String owner = "";
        try (Cursor c = databaseHelper.getReadableDatabase().rawQuery(
                "SELECT cloud_id, user_id FROM posts WHERE id = ? AND is_active = 0 AND sync_state = 1 LIMIT 1",
                new String[]{String.valueOf(postId)})) {
            if (c.moveToFirst()) {
                cloudId = safe(c.getString(0));
                owner = safe(c.getString(1));
            }
        }
        if (cloudId.isEmpty() || !owner.equals(user.getUid())) {
            // Bulutta karşılığı yok ya da başkasının gönderisi (yalnızca bu telefonda gizlenir).
            if (cloudId.isEmpty()) markPostSynced(postId);
            pendingDeletes.remove(postId);
            complete(callback, cloudId.isEmpty());
            return;
        }
        final int key = ++nextCallback;
        if (callback != null) callbacks.put(key, callback);
        Map<String, Object> change = new HashMap<>();
        change.put("is_active", 0);
        change.put("updated_at", System.currentTimeMillis());
        try {
            firestore.collection(COLLECTION_POSTS).document(cloudId).update(change)
                    .addOnSuccessListener(ignored -> {
                        pendingDeletes.remove(postId);
                        markPostSynced(postId);
                        finishStored(key, true);
                    })
                    .addOnFailureListener(error -> {
                        pendingDeletes.remove(postId);
                        boolean gone = error instanceof com.google.firebase.firestore.FirebaseFirestoreException
                                && ((com.google.firebase.firestore.FirebaseFirestoreException) error).getCode()
                                == com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND;
                        // Belge bulutta hiç yoksa silinecek bir şey kalmamıştır.
                        if (gone) markPostSynced(postId);
                        finishStored(key, gone);
                    });
        } catch (RuntimeException error) {
            pendingDeletes.remove(postId);
            finishStored(key, false);
        }
    }

    private boolean isPostPending(long postId) {
        try (Cursor c = databaseHelper.getReadableDatabase().rawQuery(
                "SELECT 1 FROM posts WHERE id = ? AND sync_state = 1 LIMIT 1",
                new String[]{String.valueOf(postId)})) {
            return c.moveToFirst();
        }
    }

    private void markPostSynced(long postId) {
        android.content.ContentValues values = new android.content.ContentValues();
        values.put("sync_state", 2);
        databaseHelper.getWritableDatabase().update("posts", values, "id = ?",
                new String[]{String.valueOf(postId)});
    }

    // ============================================================
    // LIVE FEED
    // ============================================================

    /**
     * Sohbet ekranı açıkken en yeni gönderileri canlı dinler. Her değişiklikte yalnızca eklenen /
     * değişen belgeler yazılır; sunucudan gelen tam görüntüde pencereden düşen silinmiş gönderiler
     * telefonda kapatılır. onChange ana iş parçacığında çağrılır.
     */
    public void startLiveSync(Callback onChange) {
        stopLiveSync();
        liveSawServer = false;
        if (closed || getCurrentUser() == null) {
            complete(onChange, false);
            return;
        }
        try {
            liveRegistration = firestore.collection(COLLECTION_POSTS)
                    .orderBy("created_at", Query.Direction.DESCENDING)
                    .limit(SYNC_LIMIT)
                    // Metadata değişiklikleri de gelsin: önbellekten ilk görüntü ile sunucu görüntüsü
                    // aynı olsa bile "sunucudan geldi" anı bildirilir ve eşleştirme çalışır.
                    .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE, (snapshot, error) -> {
                        if (closed || liveRegistration == null) return;
                        if (error != null || snapshot == null) {
                            complete(onChange, false);
                            return;
                        }
                        try {
                            List<com.google.firebase.firestore.DocumentChange> changes =
                                    snapshot.getDocumentChanges(com.google.firebase.firestore.MetadataChanges.EXCLUDE);
                            boolean fromServer = !snapshot.getMetadata().isFromCache();
                            boolean firstServer = fromServer && !liveSawServer;
                            if (fromServer) liveSawServer = true;
                            // Yalnızca yazma onayı gibi metadata olayları: veri aynı, ekranı yenilemeye gerek yok.
                            if (changes.isEmpty() && !firstServer) return;
                            for (com.google.firebase.firestore.DocumentChange change : changes) {
                                if (change.getType() != com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                    importCloudPost(change.getDocument());
                                }
                            }
                            if (fromServer) reconcileWindow(snapshot.getDocuments(), false, onChange);
                            complete(onChange, true);
                        } catch (RuntimeException failure) {
                            complete(onChange, false);
                        }
                    });
        } catch (RuntimeException error) {
            liveRegistration = null;
            complete(onChange, false);
        }
    }

    public void stopLiveSync() {
        com.google.firebase.firestore.ListenerRegistration registration = liveRegistration;
        liveRegistration = null;
        if (registration != null) registration.remove();
    }

    /**
     * Sorgu penceresinde artık bulunmayan gönderileri tek tek sunucudan doğrular: belge yoksa ya da
     * is_active=0 ise telefonda kapatır, hâlâ etkinse (ör. created_at alanı olmayan eski belge
     * sorguya hiç girmez) olduğu gibi içeri alır. Böylece pencere hesabındaki bir boşluk etkin bir
     * gönderiyi yanlışlıkla gizleyemez.
     */
    private void reconcileWindow(List<DocumentSnapshot> documents, boolean fromCache, Callback onChanged) {
        if (closed || fromCache || documents == null) return;
        java.util.Set<String> ids = new java.util.HashSet<>();
        long oldest = Long.MAX_VALUE;
        for (DocumentSnapshot document : documents) {
            ids.add(document.getId());
            String declared = value(document, "cloud_id", "");
            if (!declared.isEmpty()) ids.add(declared);
            Object created = document.get("created_at");
            if (created instanceof Number) oldest = Math.min(oldest, ((Number) created).longValue());
        }
        if (oldest == Long.MAX_VALUE) oldest = 0L;
        List<String> missing = MeydanPostSync.missingFromCloud(databaseHelper.getActiveCloudPostsForSync(),
                ids, documents.size(), SYNC_LIMIT, oldest, false);
        int started = 0;
        for (String cloudId : missing) {
            if (started >= RECONCILE_BATCH) break;
            if (!ZeytinMessagingService.validId(cloudId) || !verifying.add(cloudId)) continue;
            started++;
            try {
                firestore.collection(COLLECTION_POSTS).document(cloudId)
                        .get(com.google.firebase.firestore.Source.SERVER)
                        .addOnSuccessListener(doc -> {
                            verifying.remove(cloudId);
                            if (closed) return;
                            if (doc == null || !doc.exists()) {
                                databaseHelper.deactivatePostsFromCloud(java.util.Collections.singletonList(cloudId));
                            } else {
                                importCloudPost(doc);
                            }
                            complete(onChanged, true);
                        })
                        // Ağ yoksa karar verilmez; gönderi bir sonraki eşleştirmeye kalır.
                        .addOnFailureListener(error -> verifying.remove(cloudId));
            } catch (RuntimeException error) {
                verifying.remove(cloudId);
            }
        }
    }

    private void complete(Callback callback, boolean success) {
        if (!closed && callback != null) callback.onComplete(success);
    }
    private void finishStored(int key, boolean success) {
        Callback callback = callbacks.remove(key);
        if (!closed && callback != null) callback.onComplete(success);
    }
    public void close() { closed = true; stopLiveSync(); callbacks.clear(); }

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
