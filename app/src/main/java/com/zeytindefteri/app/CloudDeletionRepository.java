package com.zeytindefteri.app;

import android.database.Cursor;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Çoklu cihazlarda silinen kayıtların yeniden dirilmesini engeller.
 * Silme bilgisi önce users/{uid}/tombstones altında kalıcı hale gelir,
 * sonra asıl bulut belgesi temizlenir. Diğer cihazlar tombstone'u çekip
 * aynı yerel kaydı siler.
 */
public class CloudDeletionRepository {

    public interface Callback {
        void onComplete();
        void onError(Exception error);
    }

    private static final String CURSOR_TYPE = "__tombstones__";
    private static final String CURSOR_ID = "global";

    private final DatabaseHelper databaseHelper;
    private final FirebaseFirestore firestore;

    public CloudDeletionRepository(DatabaseHelper databaseHelper) {
        this.databaseHelper = databaseHelper;
        this.firestore = FirebaseFirestore.getInstance();
    }

    private CollectionReference tombstonesRef(String uid) {
        return firestore.collection("users").document(uid).collection("tombstones");
    }

    public void pushPending(String uid, Callback callback) {
        uid = safe(uid);
        if (uid.isEmpty()) {
            if (callback != null) callback.onComplete();
            return;
        }

        final String ownerUid = uid;
        java.util.ArrayList<Map<String, Object>> pendingRows = new java.util.ArrayList<>();
        Cursor c = null;
        try {
            c = databaseHelper.getPendingCloudDeletions(ownerUid);
            while (c.moveToNext()) {
                String type = value(c, "entity_type");
                String cloudId = value(c, "cloud_id");
                if (type.isEmpty() || cloudId.isEmpty()) continue;
                Map<String, Object> row = new HashMap<>();
                row.put("entity_type", type);
                row.put("cloud_id", cloudId);
                row.put("garden_cloud_id", value(c, "garden_cloud_id"));
                row.put("deleted_at", longValue(c, "deleted_at"));
                pendingRows.add(row);
            }
        } finally {
            if (c != null) c.close();
        }

        if (pendingRows.isEmpty()) {
            if (callback != null) callback.onComplete();
            return;
        }

        AtomicInteger remaining = new AtomicInteger(pendingRows.size());
        final Exception[] firstError = new Exception[1];

        for (Map<String, Object> row : pendingRows) {
            final String type = safeObj(row.get("entity_type"));
            final String cloudId = safeObj(row.get("cloud_id"));
            final String gardenCloudId = safeObj(row.get("garden_cloud_id"));
            final long deletedAt = row.get("deleted_at") instanceof Number
                    ? ((Number) row.get("deleted_at")).longValue() : System.currentTimeMillis();

            Map<String, Object> data = new HashMap<>();
            data.put("owner_uid", ownerUid);
            data.put("entity_type", type);
            data.put("cloud_id", cloudId);
            data.put("garden_cloud_id", gardenCloudId);
            data.put("deleted_at", deletedAt);

            String tombstoneId = type + "__" + cloudId;
            tombstonesRef(ownerUid).document(tombstoneId)
                    .set(data, SetOptions.merge())
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            databaseHelper.markCloudDeletionUploaded(ownerUid, type, cloudId);
                            deleteCloudDocument(ownerUid, type, cloudId, gardenCloudId);
                        } else if (firstError[0] == null) {
                            firstError[0] = task.getException();
                        }

                        if (remaining.decrementAndGet() == 0 && callback != null) {
                            if (firstError[0] != null) callback.onError(firstError[0]);
                            else callback.onComplete();
                        }
                    });
        }
    }

    public void pullAndApply(String uid, Callback callback) {
        uid = safe(uid);
        if (uid.isEmpty()) {
            if (callback != null) callback.onComplete();
            return;
        }

        final String ownerUid = uid;
        final long cursor = databaseHelper.getCloudRemoteCursor(ownerUid, CURSOR_TYPE, CURSOR_ID);

        Query query = tombstonesRef(ownerUid);
        if (cursor > 0) {
            query = query.whereGreaterThan("deleted_at", cursor).orderBy("deleted_at", Query.Direction.ASCENDING);
        }

        query.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                if (callback != null) {
                    Exception e = task.getException();
                    callback.onError(e != null ? e : new Exception("Silme kayıtları okunamadı"));
                }
                return;
            }

            long maxDeletedAt = cursor;
            for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                String type = safeObj(doc.get("entity_type"));
                String cloudId = safeObj(doc.get("cloud_id"));
                long deletedAt = numberLong(doc, "deleted_at");
                if (!type.isEmpty() && !cloudId.isEmpty()) {
                    databaseHelper.applyRemoteDeletion(ownerUid, type, cloudId, deletedAt);
                    if (deletedAt > maxDeletedAt) maxDeletedAt = deletedAt;
                }
            }

            if (maxDeletedAt > cursor) {
                databaseHelper.markCloudRemoteApplied(ownerUid, CURSOR_TYPE, CURSOR_ID, maxDeletedAt);
            }
            if (callback != null) callback.onComplete();
        });
    }

    private void deleteCloudDocument(String uid, String type, String cloudId, String gardenCloudId) {
        DocumentReference ref = null;
        if ("gardens".equals(type)) {
            deleteGardenSubcollections(uid, cloudId);
            ref = firestore.collection("users").document(uid).collection("gardens").document(cloudId);
        } else if ("operations".equals(type)) {
            ref = child(uid, gardenCloudId, "operations", cloudId);
        } else if ("harvests".equals(type)) {
            ref = child(uid, gardenCloudId, "harvests", cloudId);
        } else if ("elek_records".equals(type)) {
            ref = child(uid, gardenCloudId, "elek_records", cloudId);
        } else if ("sales".equals(type)) {
            ref = child(uid, gardenCloudId, "sales", cloudId);
        } else if ("brine_batches".equals(type)) {
            ref = child(uid, gardenCloudId, "brine_batches", cloudId);
        } else if ("garden_notes".equals(type)) {
            ref = child(uid, gardenCloudId, "notes", cloudId);
        } else if ("inventory_items".equals(type)) {
            ref = top(uid, "inventory_items", cloudId);
        } else if ("receivables".equals(type)) {
            ref = top(uid, "receivables", cloudId);
        } else if ("reminders".equals(type)) {
            ref = top(uid, "reminders", cloudId);
        }

        if (ref != null) ref.delete();
    }

    private void deleteGardenSubcollections(String uid, String gardenCloudId) {
        String[] collections = {
                "operations", "harvests", "elek_records", "sales", "brine_batches", "notes"
        };
        for (String collection : collections) {
            firestore.collection("users").document(uid).collection("gardens")
                    .document(gardenCloudId).collection(collection)
                    .get().addOnSuccessListener(snapshot -> {
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            document.getReference().delete();
                        }
                    });
        }
    }

    private DocumentReference child(String uid, String gardenCloudId, String collection, String cloudId) {
        if (safe(gardenCloudId).isEmpty()) return null;
        return firestore.collection("users").document(uid).collection("gardens")
                .document(gardenCloudId).collection(collection).document(cloudId);
    }

    private DocumentReference top(String uid, String collection, String cloudId) {
        return firestore.collection("users").document(uid).collection(collection).document(cloudId);
    }

    private static long numberLong(DocumentSnapshot d, String key) {
        Object v = d.get(key);
        return v instanceof Number ? ((Number) v).longValue() : 0L;
    }

    private static String safeObj(Object value) {
        return value == null ? "" : safe(String.valueOf(value));
    }

    private static String value(Cursor c, String n) {
        int i = c.getColumnIndex(n);
        return i < 0 || c.isNull(i) ? "" : safe(c.getString(i));
    }

    private static long longValue(Cursor c, String n) {
        int i = c.getColumnIndex(n);
        return i < 0 || c.isNull(i) ? 0L : c.getLong(i);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}