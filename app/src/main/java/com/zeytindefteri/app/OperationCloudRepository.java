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
import java.util.concurrent.atomic.AtomicInteger;

public class OperationCloudRepository {

    public interface RestoreCallback {
        void onComplete(int restoredCount);
        void onError(Exception error);
    }

    private final FirebaseFirestore firestore;
    private final DatabaseHelper databaseHelper;

    public OperationCloudRepository(
            DatabaseHelper databaseHelper
    ) {
        this.databaseHelper = databaseHelper;
        this.firestore = FirebaseFirestore.getInstance();
    }

    private CollectionReference operationsRef(
            String uid,
            String gardenCloudId
    ) {
        return firestore
                .collection("users")
                .document(safe(uid))
                .collection("gardens")
                .document(safe(gardenCloudId))
                .collection("operations");
    }

    public void syncAllLocalOperations(
            String uid
    ) {

        String safeUid = safe(uid);

        if (safeUid.isEmpty()) {
            return;
        }

        databaseHelper.ensureOperationCloudIdentitiesForOwner(
                safeUid
        );

        Cursor cursor = null;

        try {

            cursor = databaseHelper.getOperationsForOwner(
                    safeUid
            );

            while (cursor.moveToNext()) {
                syncCursorOperation(
                        safeUid,
                        cursor
                );
            }

        } finally {

            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void syncOperationByLocalId(
            String uid,
            long localOperationId
    ) {

        String safeUid = safe(uid);

        if (safeUid.isEmpty()
                || localOperationId <= 0) {
            return;
        }

        databaseHelper.ensureOperationCloudIdentitiesForOwner(
                safeUid
        );

        Cursor cursor = null;

        try {

            cursor = databaseHelper.getOperationForCloudSync(
                    safeUid,
                    localOperationId
            );

            if (cursor.moveToFirst()) {
                syncCursorOperation(
                        safeUid,
                        cursor
                );
            }

        } finally {

            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void syncCursorOperation(
            String uid,
            Cursor cursor
    ) {

        String gardenCloudId =
                value(
                        cursor,
                        "garden_cloud_id"
                );

        String operationCloudId =
                value(
                        cursor,
                        "cloud_id"
                );

        if (gardenCloudId.isEmpty()
                || operationCloudId.isEmpty()) {
            return;
        }

        Map<String, Object> data =
                new HashMap<>();

        data.put(
                "cloud_id",
                operationCloudId
        );

        data.put(
                "owner_uid",
                uid
        );

        data.put(
                "garden_cloud_id",
                gardenCloudId
        );

        data.put(
                "type",
                value(
                        cursor,
                        "type"
                )
        );

        data.put(
                "operation_date",
                value(
                        cursor,
                        "operation_date"
                )
        );

        data.put(
                "quantity",
                doubleValue(
                        cursor,
                        "quantity"
                )
        );

        data.put(
                "unit",
                value(
                        cursor,
                        "unit"
                )
        );

        data.put(
                "cost",
                doubleValue(
                        cursor,
                        "cost"
                )
        );

        data.put(
                "note",
                value(
                        cursor,
                        "note"
                )
        );

        data.put(
                "season_year",
                intValue(
                        cursor,
                        "season_year"
                )
        );

        data.put(
                "created_at",
                longValue(
                        cursor,
                        "created_at"
                )
        );

        data.put(
                "updated_at",
                Math.max(
                        longValue(
                                cursor,
                                "updated_at"
                        ),
                        longValue(
                                cursor,
                                "created_at"
                        )
                )
        );

        final long localUpdatedAt = Math.max(
                longValue(cursor, "updated_at"),
                longValue(cursor, "created_at")
        );

        if (!databaseHelper.shouldUploadCloudRecord(uid, "operations", operationCloudId, localUpdatedAt)) {
            return;
        }

        final String finalOperationCloudId = operationCloudId;
        operationsRef(
                uid,
                gardenCloudId
        )
                .document(
                        operationCloudId
                )
                .set(
                        data,
                        SetOptions.merge()
                )
                .addOnCompleteListener(CloudWrites.listener())
                .addOnSuccessListener(unused ->
                        databaseHelper.markCloudUploadComplete(
                                uid, "operations", finalOperationCloudId, localUpdatedAt));
    }

    public void restoreAllOperations(
            String uid,
            RestoreCallback callback
    ) {

        String safeUid =
                safe(uid);

        if (safeUid.isEmpty()) {

            if (callback != null) {
                callback.onComplete(0);
            }

            return;
        }

        Cursor gardenCursor = null;
        java.util.ArrayList<String> gardenCloudIds =
                new java.util.ArrayList<>();

        try {

            gardenCursor =
                    databaseHelper.getGardensForOwner(
                            safeUid
                    );

            while (gardenCursor.moveToNext()) {

                String gardenCloudId =
                        value(
                                gardenCursor,
                                "cloud_id"
                        );

                if (!gardenCloudId.isEmpty()) {
                    gardenCloudIds.add(
                            gardenCloudId
                    );
                }
            }

        } finally {

            if (gardenCursor != null) {
                gardenCursor.close();
            }
        }

        if (gardenCloudIds.isEmpty()) {

            if (callback != null) {
                callback.onComplete(0);
            }

            return;
        }

        AtomicInteger pending =
                new AtomicInteger(
                        gardenCloudIds.size()
                );

        AtomicInteger restored =
                new AtomicInteger(
                        0
                );

        java.util.concurrent.atomic.AtomicBoolean failed =
                new java.util.concurrent.atomic.AtomicBoolean(
                        false
                );

        for (String gardenCloudId : gardenCloudIds) {

            final long pullCursor = databaseHelper.getCloudRemoteCursor(
                    safeUid, "__pull_operations__", gardenCloudId);

            Query query = operationsRef(
                    safeUid,
                    gardenCloudId
            );

            if (pullCursor > 0) {
                query = query.whereGreaterThan("updated_at", pullCursor)
                        .orderBy("updated_at", Query.Direction.ASCENDING);
            }

            Task<QuerySnapshot> task = query.get();

            task.addOnSuccessListener(
                    querySnapshot -> {

                        long maxRemoteUpdatedAt = pullCursor;

                        for (DocumentSnapshot document :
                                querySnapshot.getDocuments()) {

                            String operationCloudId =
                                    safe(
                                            document.getString(
                                                    "cloud_id"
                                            )
                                    );

                            if (operationCloudId.isEmpty()) {
                                operationCloudId =
                                        document.getId();
                            }

                            if (databaseHelper.hasCloudDeletionTombstone(
                                    safeUid, "operations", operationCloudId)) {
                                continue;
                            }

                            long documentUpdatedAt = numberLong(document, "updated_at");
                            if (documentUpdatedAt > maxRemoteUpdatedAt) {
                                maxRemoteUpdatedAt = documentUpdatedAt;
                            }

                            long result =
                                    databaseHelper.upsertOperationFromCloud(
                                            safeUid,
                                            gardenCloudId,
                                            operationCloudId,
                                            safe(
                                                    document.getString(
                                                            "type"
                                                    )
                                            ),
                                            safe(
                                                    document.getString(
                                                            "operation_date"
                                                    )
                                            ),
                                            numberDouble(
                                                    document,
                                                    "quantity"
                                            ),
                                            safe(
                                                    document.getString(
                                                            "unit"
                                                    )
                                            ),
                                            numberDouble(
                                                    document,
                                                    "cost"
                                            ),
                                            safe(
                                                    document.getString(
                                                            "note"
                                                    )
                                            ),
                                            numberInt(
                                                    document,
                                                    "season_year"
                                            ),
                                            numberLong(
                                                    document,
                                                    "created_at"
                                            ),
                                            numberLong(
                                                    document,
                                                    "updated_at"
                                            )
                                    );

                            if (result > 0) {
                                restored.incrementAndGet();
                                databaseHelper.markCloudRemoteApplied(
                                        safeUid, "operations", operationCloudId,
                                        numberLong(document, "updated_at"));
                            }
                        }

                        if (maxRemoteUpdatedAt > pullCursor) {
                            databaseHelper.markCloudRemoteApplied(
                                    safeUid, "__pull_operations__", gardenCloudId, maxRemoteUpdatedAt);
                        }

                        finishOneRestore(
                                pending,
                                restored,
                                failed,
                                callback
                        );
                    }
            );

            task.addOnFailureListener(
                    error -> {

                        if (failed.compareAndSet(
                                false,
                                true
                        )) {

                            if (callback != null) {
                                callback.onError(
                                        error
                                );
                            }
                        }

                        finishOneRestore(
                                pending,
                                restored,
                                failed,
                                null
                        );
                    }
            );
        }
    }

    private void finishOneRestore(
            AtomicInteger pending,
            AtomicInteger restored,
            java.util.concurrent.atomic.AtomicBoolean failed,
            RestoreCallback callback
    ) {

        if (pending.decrementAndGet() == 0
                && !failed.get()
                && callback != null) {

            callback.onComplete(
                    restored.get()
            );
        }
    }

    private int numberInt(
            DocumentSnapshot document,
            String key
    ) {

        Number value =
                (Number) document.get(
                        key
                );

        return value == null
                ? 0
                : value.intValue();
    }

    private long numberLong(
            DocumentSnapshot document,
            String key
    ) {

        Number value =
                (Number) document.get(
                        key
                );

        return value == null
                ? 0L
                : value.longValue();
    }

    private double numberDouble(
            DocumentSnapshot document,
            String key
    ) {

        Number value =
                (Number) document.get(
                        key
                );

        return value == null
                ? 0d
                : value.doubleValue();
    }

    private String value(
            Cursor cursor,
            String column
    ) {

        int index =
                cursor.getColumnIndex(
                        column
                );

        return index < 0
                ? ""
                : safe(
                        cursor.getString(
                                index
                        )
                );
    }

    private int intValue(
            Cursor cursor,
            String column
    ) {

        int index =
                cursor.getColumnIndex(
                        column
                );

        return index < 0
                ? 0
                : cursor.getInt(
                        index
                );
    }

    private long longValue(
            Cursor cursor,
            String column
    ) {

        int index =
                cursor.getColumnIndex(
                        column
                );

        return index < 0
                ? 0L
                : cursor.getLong(
                        index
                );
    }

    private double doubleValue(
            Cursor cursor,
            String column
    ) {

        int index =
                cursor.getColumnIndex(
                        column
                );

        return index < 0
                ? 0d
                : cursor.getDouble(
                        index
                );
    }

    private String safe(
            String value
    ) {

        return value == null
                ? ""
                : value.trim();
    }
}