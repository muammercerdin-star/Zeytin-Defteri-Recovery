package com.zeytindefteri.app;

import android.database.Cursor;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GardenCloudRepository {

    public interface RestoreCallback {
        void onComplete(int restoredCount);
        void onError(Exception error);
    }

    private final FirebaseFirestore firestore;
    private final DatabaseHelper databaseHelper;
    private final GardenRepository gardenRepository;

    public GardenCloudRepository(
            DatabaseHelper databaseHelper
    ) {
        this.databaseHelper = databaseHelper;
        this.gardenRepository = new GardenRepository(databaseHelper);
        this.firestore = FirebaseFirestore.getInstance();
    }

    private CollectionReference gardensRef(String uid) {
        return firestore
                .collection("users")
                .document(safe(uid))
                .collection("gardens");
    }

    public void syncAllLocalGardens(
            String uid
    ) {
        String safeUid = safe(uid);
        if (safeUid.isEmpty()) {
            return;
        }

        Cursor cursor = null;
        try {
            cursor = gardenRepository.getGardensForOwner(safeUid);
            while (cursor.moveToNext()) {
                syncCursorGarden(safeUid, cursor);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void syncGardenByLocalId(
            String uid,
            long localGardenId
    ) {
        String safeUid = safe(uid);
        if (safeUid.isEmpty() || localGardenId <= 0) {
            return;
        }

        Cursor cursor = null;
        try {
            cursor = databaseHelper.getGarden(localGardenId);
            if (!cursor.moveToFirst()) {
                return;
            }

            String ownerUid = value(cursor, "owner_uid");
            if (!safeUid.equals(ownerUid)) {
                return;
            }

            syncCursorGarden(safeUid, cursor);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void syncCursorGarden(
            String uid,
            Cursor cursor
    ) {
        long localId = longValue(cursor, "id");
        String cloudId = value(cursor, "cloud_id");

        if (cloudId.isEmpty()) {
            cloudId = UUID.randomUUID().toString();
            databaseHelper.ensureGardenCloudIdentity(
                    localId,
                    uid,
                    cloudId
            );
        }

        Map<String, Object> data = new HashMap<>();
        data.put("cloud_id", cloudId);
        data.put("owner_uid", uid);
        data.put("name", value(cursor, "name"));
        data.put("tree_count", intValue(cursor, "tree_count"));
        data.put("age", intValue(cursor, "age"));
        data.put("variety", value(cursor, "variety"));
        data.put("area_decare", doubleValue(cursor, "area_decare"));
        data.put("province", value(cursor, "province"));
        data.put("district", value(cursor, "district"));
        data.put("neighborhood", value(cursor, "neighborhood"));
        data.put("irrigation_type", value(cursor, "irrigation_type"));
        data.put("notes", value(cursor, "notes"));
        data.put("photo_uri", value(cursor, "photo_uri"));
        data.put("created_at", longValue(cursor, "created_at"));
        data.put("updated_at", Math.max(
                longValue(cursor, "updated_at"),
                longValue(cursor, "created_at")
        ));

        final long localUpdatedAt = Math.max(
                longValue(cursor, "updated_at"),
                longValue(cursor, "created_at")
        );

        if (!databaseHelper.shouldUploadCloudRecord(uid, "gardens", cloudId, localUpdatedAt)) {
            return;
        }

        final String finalCloudId = cloudId;
        gardensRef(uid)
                .document(cloudId)
                .set(data, SetOptions.merge())
                .addOnCompleteListener(CloudWrites.listener())
                .addOnSuccessListener(unused ->
                        databaseHelper.markCloudUploadComplete(uid, "gardens", finalCloudId, localUpdatedAt));
    }

    public void restoreGardens(
            String uid,
            RestoreCallback callback
    ) {
        String safeUid = safe(uid);

        if (safeUid.isEmpty()) {
            if (callback != null) {
                callback.onComplete(0);
            }
            return;
        }

        final long pullCursor = databaseHelper.getCloudRemoteCursor(
                safeUid, "__pull_gardens__", "all");
        Query query = gardensRef(safeUid);
        if (pullCursor > 0) {
            query = query.whereGreaterThan("updated_at", pullCursor)
                    .orderBy("updated_at", Query.Direction.ASCENDING);
        }
        Task<QuerySnapshot> task = query.get();

        task.addOnSuccessListener(querySnapshot -> {
            int restored = 0;
            long maxRemoteUpdatedAt = pullCursor;

            for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                String cloudId = safe(document.getString("cloud_id"));
                if (cloudId.isEmpty()) {
                    cloudId = document.getId();
                }

                if (databaseHelper.hasCloudDeletionTombstone(safeUid, "gardens", cloudId)) {
                    continue;
                }

                String name = safe(document.getString("name"));
                if (name.isEmpty()) {
                    continue;
                }

                long documentUpdatedAt = numberLong(document, "updated_at");
                if (documentUpdatedAt > maxRemoteUpdatedAt) {
                    maxRemoteUpdatedAt = documentUpdatedAt;
                }

                long result = gardenRepository.upsertGardenFromCloud(
                        safeUid,
                        cloudId,
                        name,
                        numberInt(document, "tree_count"),
                        numberInt(document, "age"),
                        safe(document.getString("variety")),
                        numberDouble(document, "area_decare"),
                        safe(document.getString("province")),
                        safe(document.getString("district")),
                        safe(document.getString("neighborhood")),
                        safe(document.getString("irrigation_type")),
                        safe(document.getString("notes")),
                        safe(document.getString("photo_uri")),
                        numberLong(document, "created_at"),
                        numberLong(document, "updated_at")
                );

                if (result > 0) {
                    restored++;
                    databaseHelper.markCloudRemoteApplied(
                            safeUid, "gardens", cloudId, numberLong(document, "updated_at"));
                }
            }

            if (maxRemoteUpdatedAt > pullCursor) {
                databaseHelper.markCloudRemoteApplied(
                        safeUid, "__pull_gardens__", "all", maxRemoteUpdatedAt);
            }

            if (callback != null) {
                callback.onComplete(restored);
            }
        });

        task.addOnFailureListener(error -> {
            if (callback != null) {
                callback.onError(error);
            }
        });
    }

    private int numberInt(DocumentSnapshot document, String key) {
        Number value = (Number) document.get(key);
        return value == null ? 0 : value.intValue();
    }

    private long numberLong(DocumentSnapshot document, String key) {
        Number value = (Number) document.get(key);
        return value == null ? 0L : value.longValue();
    }

    private double numberDouble(DocumentSnapshot document, String key) {
        Number value = (Number) document.get(key);
        return value == null ? 0d : value.doubleValue();
    }

    private String value(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 ? "" : safe(cursor.getString(index));
    }

    private int intValue(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 ? 0 : cursor.getInt(index);
    }

    private long longValue(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 ? 0L : cursor.getLong(index);
    }

    private double doubleValue(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 ? 0d : cursor.getDouble(index);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}