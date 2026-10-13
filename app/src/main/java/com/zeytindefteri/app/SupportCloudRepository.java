package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stok + alacak + hatırlatıcı + bahçe notları için özel kullanıcı bulut katmanı.
 *
 * users/{uid}/inventory_items/{cloudId}
 * users/{uid}/receivables/{cloudId}
 * users/{uid}/reminders/{cloudId}
 * users/{uid}/gardens/{gardenCloudId}/notes/{cloudId}
 */
public class SupportCloudRepository {

    public interface RestoreCallback {
        void onComplete(int restoredCount);
        void onError(Exception error);
    }

    private final DatabaseHelper databaseHelper;
    private final FirebaseFirestore firestore;

    public SupportCloudRepository(DatabaseHelper databaseHelper) {
        this.databaseHelper = databaseHelper;
        this.firestore = FirebaseFirestore.getInstance();
    }

    public void syncAllLocal(String uid) {
        uid = safe(uid);
        if (uid.isEmpty()) return;
        ensureIdentities(uid);
        syncInventory(uid);
        syncSimple(uid, "receivables", "receivables");
        syncSimple(uid, "reminders", "reminders");
        syncNotes(uid);
    }

    public void restoreAll(String uid, RestoreCallback callback) {
        uid = safe(uid);
        if (uid.isEmpty()) {
            if (callback != null) callback.onComplete(0);
            return;
        }

        ensureIdentities(uid);
        List<GardenKey> gardens = gardenKeys(uid);
        int taskCount = 3 + gardens.size();
        AtomicInteger pending = new AtomicInteger(taskCount);
        AtomicInteger restored = new AtomicInteger(0);
        Exception[] firstError = new Exception[1];

        fetchTop(uid, "inventory_items", pending, restored, firstError, callback);
        fetchTop(uid, "receivables", pending, restored, firstError, callback);
        fetchTop(uid, "reminders", pending, restored, firstError, callback);

        for (GardenKey garden : gardens) {
            fetchNotes(uid, garden, pending, restored, firstError, callback);
        }
    }

    private void finishOne(AtomicInteger pending, AtomicInteger restored,
                           Exception[] firstError, RestoreCallback callback) {
        if (pending.decrementAndGet() != 0 || callback == null) return;
        if (firstError[0] != null) callback.onError(firstError[0]);
        else callback.onComplete(restored.get());
    }

    private void fetchTop(final String uid, final String collection,
                          final AtomicInteger pending, final AtomicInteger restored,
                          final Exception[] firstError, final RestoreCallback callback) {
        final String cursorType = "__pull_support_" + collection + "__";
        final long pullCursor = databaseHelper.getCloudRemoteCursor(uid, cursorType, "all");
        Query query = firestore.collection("users").document(uid).collection(collection);
        if (pullCursor > 0) {
            query = query.whereGreaterThan("updated_at", pullCursor)
                    .orderBy("updated_at", Query.Direction.ASCENDING);
        }
        query.get().addOnCompleteListener(task -> {
                    long maxRemoteUpdatedAt = pullCursor;
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            long documentUpdatedAt = lng(doc, "updated_at");
                            if (documentUpdatedAt > maxRemoteUpdatedAt) maxRemoteUpdatedAt = documentUpdatedAt;
                            try {
                                if ("inventory_items".equals(collection)) restoreInventory(uid, doc);
                                else if ("receivables".equals(collection)) restoreReceivable(uid, doc);
                                else restoreReminder(uid, doc);
                                restored.incrementAndGet();
                            } catch (Exception e) {
                                if (firstError[0] == null) firstError[0] = e;
                            }
                        }
                    } else if (task.getException() != null && firstError[0] == null) {
                        firstError[0] = task.getException();
                    }
                    if (maxRemoteUpdatedAt > pullCursor) {
                        databaseHelper.markCloudRemoteApplied(uid, cursorType, "all", maxRemoteUpdatedAt);
                    }
                    finishOne(pending, restored, firstError, callback);
                });
    }

    private void fetchNotes(final String uid, final GardenKey garden,
                            final AtomicInteger pending, final AtomicInteger restored,
                            final Exception[] firstError, final RestoreCallback callback) {
        final String cursorType = "__pull_support_notes__";
        final long pullCursor = databaseHelper.getCloudRemoteCursor(uid, cursorType, garden.cloudId);
        Query query = firestore.collection("users").document(uid)
                .collection("gardens").document(garden.cloudId)
                .collection("notes");
        if (pullCursor > 0) {
            query = query.whereGreaterThan("updated_at", pullCursor)
                    .orderBy("updated_at", Query.Direction.ASCENDING);
        }
        query.get().addOnCompleteListener(task -> {
                    long maxRemoteUpdatedAt = pullCursor;
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            long documentUpdatedAt = lng(doc, "updated_at");
                            if (documentUpdatedAt > maxRemoteUpdatedAt) maxRemoteUpdatedAt = documentUpdatedAt;
                            try {
                                restoreNote(uid, garden.localId, doc);
                                restored.incrementAndGet();
                            } catch (Exception e) {
                                if (firstError[0] == null) firstError[0] = e;
                            }
                        }
                    } else if (task.getException() != null && firstError[0] == null) {
                        firstError[0] = task.getException();
                    }
                    if (maxRemoteUpdatedAt > pullCursor) {
                        databaseHelper.markCloudRemoteApplied(uid, cursorType, garden.cloudId, maxRemoteUpdatedAt);
                    }
                    finishOne(pending, restored, firstError, callback);
                });
    }

    private void syncInventory(String uid) {
        Cursor c = null;
        try {
            c = databaseHelper.getReadableDatabase().rawQuery(
                    "SELECT * FROM inventory_items WHERE owner_uid=? AND cloud_id<>''",
                    new String[]{uid});
            while (c.moveToNext()) {
                String cloudId = value(c, "cloud_id");
                long localId = longValue(c, "id");
                Map<String, Object> m = new HashMap<>();
                m.put("cloud_id", cloudId);
                m.put("owner_uid", uid);
                m.put("name", value(c, "name"));
                m.put("category", value(c, "category"));
                m.put("unit", value(c, "unit"));
                m.put("current_qty", doubleValue(c, "current_qty"));
                m.put("min_qty", doubleValue(c, "min_qty"));
                m.put("unit_cost", doubleValue(c, "unit_cost"));
                m.put("note", value(c, "note"));
                m.put("created_at", longValue(c, "created_at"));
                m.put("updated_at", longValue(c, "updated_at"));
                m.put("movements", inventoryMovements(uid, localId));
                final long localUpdatedAt = longValue(c, "updated_at");
                if (!databaseHelper.shouldUploadCloudRecord(uid, "inventory_items", cloudId, localUpdatedAt)) {
                    continue;
                }
                final String finalCloudId = cloudId;
                firestore.collection("users").document(uid).collection("inventory_items")
                        .document(cloudId).set(m, SetOptions.merge())
                        .addOnSuccessListener(unused -> databaseHelper.markCloudUploadComplete(
                                uid, "inventory_items", finalCloudId, localUpdatedAt));
            }
        } finally {
            if (c != null) c.close();
        }
    }

    private List<Map<String, Object>> inventoryMovements(String uid, long itemId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Cursor c = null;
        try {
            c = databaseHelper.getReadableDatabase().rawQuery(
                    "SELECT m.*, o.cloud_id AS operation_cloud_id FROM inventory_movements m " +
                            "LEFT JOIN operations o ON o.id=m.operation_id " +
                            "WHERE m.item_id=? AND m.owner_uid=? ORDER BY m.id",
                    new String[]{String.valueOf(itemId), uid});
            while (c.moveToNext()) {
                Map<String, Object> m = new HashMap<>();
                m.put("cloud_id", value(c, "cloud_id"));
                m.put("movement_date", value(c, "movement_date"));
                m.put("movement_type", value(c, "movement_type"));
                m.put("quantity", doubleValue(c, "quantity"));
                m.put("unit_cost", doubleValue(c, "unit_cost"));
                m.put("operation_cloud_id", value(c, "operation_cloud_id"));
                m.put("note", value(c, "note"));
                m.put("created_at", longValue(c, "created_at"));
                m.put("updated_at", longValue(c, "updated_at"));
                rows.add(m);
            }
        } finally {
            if (c != null) c.close();
        }
        return rows;
    }

    private void syncSimple(String uid, String table, String collection) {
        Cursor c = null;
        try {
            c = databaseHelper.getReadableDatabase().rawQuery(
                    "SELECT * FROM " + table + " WHERE owner_uid=? AND cloud_id<>''",
                    new String[]{uid});
            while (c.moveToNext()) {
                String cloudId = value(c, "cloud_id");
                Map<String, Object> m = "receivables".equals(table)
                        ? receivableMap(uid, c) : reminderMap(uid, c);
                m.put("cloud_id", cloudId);
                m.put("owner_uid", uid);
                final long localUpdatedAt = longValue(c, "updated_at");
                if (!databaseHelper.shouldUploadCloudRecord(uid, table, cloudId, localUpdatedAt)) {
                    continue;
                }
                final String finalCloudId = cloudId;
                firestore.collection("users").document(uid).collection(collection)
                        .document(cloudId).set(m, SetOptions.merge())
                        .addOnSuccessListener(unused -> databaseHelper.markCloudUploadComplete(
                                uid, table, finalCloudId, localUpdatedAt));
            }
        } finally {
            if (c != null) c.close();
        }
    }

    private Map<String, Object> receivableMap(String uid, Cursor c) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", value(c, "title"));
        m.put("party_name", value(c, "party_name"));
        m.put("amount", doubleValue(c, "amount"));
        m.put("total_received", doubleValue(c, "total_received"));
        m.put("due_date", value(c, "due_date"));
        m.put("status", value(c, "status"));
        m.put("note", value(c, "note"));
        m.put("created_at", longValue(c, "created_at"));
        m.put("updated_at", longValue(c, "updated_at"));
        long saleId = longValue(c, "related_sale_id");
        m.put("related_sale_cloud_id", cloudIdFor("sales", saleId, uid));
        return m;
    }

    private Map<String, Object> reminderMap(String uid, Cursor c) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", value(c, "title"));
        m.put("reminder_type", value(c, "reminder_type"));
        m.put("due_at", longValue(c, "due_at"));
        m.put("is_done", intValue(c, "is_done"));
        m.put("repeat_rule", value(c, "repeat_rule"));
        m.put("note", value(c, "note"));
        m.put("created_at", longValue(c, "created_at"));
        m.put("updated_at", longValue(c, "updated_at"));
        long gardenId = longValue(c, "garden_id");
        m.put("garden_cloud_id", cloudIdFor("gardens", gardenId, uid));
        return m;
    }

    private void syncNotes(String uid) {
        Cursor c = null;
        try {
            c = databaseHelper.getReadableDatabase().rawQuery(
                    "SELECT n.*, g.cloud_id AS garden_cloud_id FROM garden_notes n " +
                            "INNER JOIN gardens g ON g.id=n.garden_id " +
                            "WHERE n.owner_uid=? AND g.owner_uid=? AND n.cloud_id<>'' AND g.cloud_id<>''",
                    new String[]{uid, uid});
            while (c.moveToNext()) {
                String cloudId = value(c, "cloud_id");
                String gardenCloudId = value(c, "garden_cloud_id");
                Map<String, Object> m = new HashMap<>();
                m.put("cloud_id", cloudId);
                m.put("owner_uid", uid);
                m.put("garden_cloud_id", gardenCloudId);
                m.put("note_date", value(c, "note_date"));
                m.put("note", value(c, "note"));
                m.put("photo_uri", value(c, "photo_uri"));
                m.put("created_at", longValue(c, "created_at"));
                m.put("updated_at", longValue(c, "updated_at"));
                final long localUpdatedAt = longValue(c, "updated_at");
                if (!databaseHelper.shouldUploadCloudRecord(uid, "garden_notes", cloudId, localUpdatedAt)) {
                    continue;
                }
                final String finalCloudId = cloudId;
                firestore.collection("users").document(uid).collection("gardens")
                        .document(gardenCloudId).collection("notes")
                        .document(cloudId).set(m, SetOptions.merge())
                        .addOnSuccessListener(unused -> databaseHelper.markCloudUploadComplete(
                                uid, "garden_notes", finalCloudId, localUpdatedAt));
            }
        } finally {
            if (c != null) c.close();
        }
    }

    private void ensureIdentities(String uid) {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        long now = System.currentTimeMillis();
        claimGlobal(db, "inventory_items", uid, now);
        claimGlobal(db, "inventory_movements", uid, now);
        claimGlobal(db, "receivables", uid, now);
        claimGlobal(db, "reminders", uid, now);
        claimGardenOwned(db, "garden_notes", uid, now);

        assignIds(db, "inventory_items", uid, now);
        assignIds(db, "inventory_movements", uid, now);
        assignIds(db, "receivables", uid, now);
        assignIds(db, "reminders", uid, now);
        assignIds(db, "garden_notes", uid, now);
    }

    private void claimGlobal(SQLiteDatabase db, String table, String uid, long now) {
        ContentValues v = new ContentValues();
        v.put("owner_uid", uid);
        v.put("updated_at", now);
        db.update(table, v, "owner_uid IS NULL OR owner_uid=''", null);
    }

    private void claimGardenOwned(SQLiteDatabase db, String table, String uid, long now) {
        db.execSQL("UPDATE " + table + " SET owner_uid=?, updated_at=? " +
                        "WHERE (owner_uid IS NULL OR owner_uid='') AND garden_id IN " +
                        "(SELECT id FROM gardens WHERE owner_uid=?)",
                new Object[]{uid, now, uid});
    }

    private void assignIds(SQLiteDatabase db, String table, String uid, long now) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT id FROM " + table + " WHERE owner_uid=? AND (cloud_id IS NULL OR cloud_id='')",
                    new String[]{uid});
            while (c.moveToNext()) {
                ContentValues v = new ContentValues();
                v.put("cloud_id", UUID.randomUUID().toString());
                v.put("updated_at", now);
                db.update(table, v, "id=?", new String[]{String.valueOf(c.getLong(0))});
            }
        } finally {
            if (c != null) c.close();
        }
    }

    private void restoreInventory(String uid, DocumentSnapshot doc) {
        if (!shouldApplyRemote("inventory_items", uid, doc)) return;
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        String cloudId = safe(doc.getId());
        long localId = findByCloud(db, "inventory_items", uid, cloudId);
        ContentValues v = new ContentValues();
        v.put("owner_uid", uid); v.put("cloud_id", cloudId);
        v.put("name", str(doc, "name")); v.put("category", str(doc, "category"));
        v.put("unit", str(doc, "unit")); v.put("current_qty", dbl(doc, "current_qty"));
        v.put("min_qty", dbl(doc, "min_qty")); v.put("unit_cost", dbl(doc, "unit_cost"));
        v.put("note", str(doc, "note")); v.put("created_at", lng(doc, "created_at"));
        v.put("updated_at", lng(doc, "updated_at"));
        if (localId > 0) db.update("inventory_items", v, "id=?", new String[]{String.valueOf(localId)});
        else localId = db.insert("inventory_items", null, v);
        if (localId <= 0) return;
        databaseHelper.markCloudRemoteApplied(uid, "inventory_items", cloudId, lng(doc, "updated_at"));

        Object obj = doc.get("movements");
        if (!(obj instanceof List)) return;
        for (Object rowObj : (List<?>) obj) {
            if (!(rowObj instanceof Map)) continue;
            Map<?, ?> row = (Map<?, ?>) rowObj;
            String movementCloudId = mapStr(row, "cloud_id");
            if (movementCloudId.isEmpty()) movementCloudId = UUID.randomUUID().toString();
            long movementId = findByCloud(db, "inventory_movements", uid, movementCloudId);
            ContentValues mv = new ContentValues();
            mv.put("item_id", localId); mv.put("owner_uid", uid); mv.put("cloud_id", movementCloudId);
            mv.put("movement_date", mapStr(row, "movement_date")); mv.put("movement_type", mapStr(row, "movement_type"));
            mv.put("quantity", mapDbl(row, "quantity")); mv.put("unit_cost", mapDbl(row, "unit_cost"));
            mv.put("note", mapStr(row, "note")); mv.put("created_at", mapLng(row, "created_at"));
            mv.put("updated_at", mapLng(row, "updated_at"));
            long opId = localIdForCloud("operations", uid, mapStr(row, "operation_cloud_id"));
            if (opId > 0) mv.put("operation_id", opId); else mv.putNull("operation_id");
            if (movementId > 0) db.update("inventory_movements", mv, "id=?", new String[]{String.valueOf(movementId)});
            else db.insert("inventory_movements", null, mv);
        }
    }

    private void restoreReceivable(String uid, DocumentSnapshot doc) {
        if (!shouldApplyRemote("receivables", uid, doc)) return;
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        String cloudId = doc.getId();
        long id = findByCloud(db, "receivables", uid, cloudId);
        ContentValues v = new ContentValues();
        v.put("owner_uid", uid); v.put("cloud_id", cloudId); v.put("title", str(doc, "title"));
        v.put("party_name", str(doc, "party_name")); v.put("amount", dbl(doc, "amount"));
        v.put("total_received", dbl(doc, "total_received")); v.put("due_date", str(doc, "due_date"));
        v.put("status", str(doc, "status")); v.put("note", str(doc, "note"));
        v.put("created_at", lng(doc, "created_at")); v.put("updated_at", lng(doc, "updated_at"));
        long saleId = localIdForCloud("sales", uid, str(doc, "related_sale_cloud_id"));
        if (saleId > 0) v.put("related_sale_id", saleId); else v.putNull("related_sale_id");
        if (id > 0) db.update("receivables", v, "id=?", new String[]{String.valueOf(id)});
        else db.insert("receivables", null, v);
        databaseHelper.markCloudRemoteApplied(uid, "receivables", cloudId, lng(doc, "updated_at"));
    }

    private void restoreReminder(String uid, DocumentSnapshot doc) {
        if (!shouldApplyRemote("reminders", uid, doc)) return;
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        String cloudId = doc.getId();
        long id = findByCloud(db, "reminders", uid, cloudId);
        ContentValues v = new ContentValues();
        v.put("owner_uid", uid); v.put("cloud_id", cloudId); v.put("title", str(doc, "title"));
        v.put("reminder_type", str(doc, "reminder_type")); v.put("due_at", lng(doc, "due_at"));
        v.put("is_done", (int) lng(doc, "is_done")); v.put("repeat_rule", str(doc, "repeat_rule"));
        v.put("note", str(doc, "note")); v.put("created_at", lng(doc, "created_at"));
        v.put("updated_at", lng(doc, "updated_at"));
        long gardenId = localIdForCloud("gardens", uid, str(doc, "garden_cloud_id"));
        if (gardenId > 0) v.put("garden_id", gardenId); else v.putNull("garden_id");
        if (id > 0) db.update("reminders", v, "id=?", new String[]{String.valueOf(id)});
        else db.insert("reminders", null, v);
        databaseHelper.markCloudRemoteApplied(uid, "reminders", cloudId, lng(doc, "updated_at"));
    }

    private void restoreNote(String uid, long gardenId, DocumentSnapshot doc) {
        if (!shouldApplyRemote("garden_notes", uid, doc)) return;
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        String cloudId = doc.getId();
        long id = findByCloud(db, "garden_notes", uid, cloudId);
        ContentValues v = new ContentValues();
        v.put("garden_id", gardenId); v.put("owner_uid", uid); v.put("cloud_id", cloudId);
        v.put("note_date", str(doc, "note_date")); v.put("note", str(doc, "note"));
        v.put("photo_uri", str(doc, "photo_uri")); v.put("created_at", lng(doc, "created_at"));
        v.put("updated_at", lng(doc, "updated_at"));
        if (id > 0) db.update("garden_notes", v, "id=?", new String[]{String.valueOf(id)});
        else db.insert("garden_notes", null, v);
        databaseHelper.markCloudRemoteApplied(uid, "garden_notes", cloudId, lng(doc, "updated_at"));
    }

    private boolean shouldApplyRemote(String table, String uid, DocumentSnapshot doc) {
        String cloudId = safe(doc.getId());
        if (cloudId.isEmpty()) return false;
        if (databaseHelper.hasCloudDeletionTombstone(uid, table, cloudId)) return false;
        long remoteUpdatedAt = lng(doc, "updated_at");
        if (remoteUpdatedAt <= 0) remoteUpdatedAt = lng(doc, "created_at");
        Cursor c = null;
        try {
            c = databaseHelper.getReadableDatabase().rawQuery(
                    "SELECT updated_at,created_at FROM " + table +
                            " WHERE owner_uid=? AND cloud_id=? LIMIT 1",
                    new String[]{uid, cloudId});
            if (!c.moveToFirst()) return true;
            long localUpdatedAt = Math.max(c.getLong(0), c.getLong(1));
            return remoteUpdatedAt >= localUpdatedAt;
        } finally {
            if (c != null) c.close();
        }
    }

    private List<GardenKey> gardenKeys(String uid) {
        List<GardenKey> list = new ArrayList<>();
        Cursor c = null;
        try {
            c = databaseHelper.getReadableDatabase().rawQuery(
                    "SELECT id,cloud_id FROM gardens WHERE owner_uid=? AND cloud_id<>''", new String[]{uid});
            while (c.moveToNext()) list.add(new GardenKey(c.getLong(0), c.getString(1)));
        } finally { if (c != null) c.close(); }
        return list;
    }

    private String cloudIdFor(String table, long localId, String uid) {
        if (localId <= 0) return "";
        Cursor c = null;
        try {
            c = databaseHelper.getReadableDatabase().rawQuery(
                    "SELECT cloud_id FROM " + table + " WHERE id=? AND owner_uid=? LIMIT 1",
                    new String[]{String.valueOf(localId), uid});
            return c.moveToFirst() ? safe(c.getString(0)) : "";
        } finally { if (c != null) c.close(); }
    }

    private long localIdForCloud(String table, String uid, String cloudId) {
        cloudId = safe(cloudId);
        if (cloudId.isEmpty()) return -1;
        Cursor c = null;
        try {
            c = databaseHelper.getReadableDatabase().rawQuery(
                    "SELECT id FROM " + table + " WHERE owner_uid=? AND cloud_id=? LIMIT 1",
                    new String[]{uid, cloudId});
            return c.moveToFirst() ? c.getLong(0) : -1;
        } finally { if (c != null) c.close(); }
    }

    private long findByCloud(SQLiteDatabase db, String table, String uid, String cloudId) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT id FROM " + table + " WHERE owner_uid=? AND cloud_id=? LIMIT 1",
                    new String[]{uid, cloudId});
            return c.moveToFirst() ? c.getLong(0) : -1;
        } finally { if (c != null) c.close(); }
    }

    private static String str(DocumentSnapshot d, String k) { Object o=d.get(k); return o==null?"":String.valueOf(o); }
    private static long lng(DocumentSnapshot d, String k) { Object o=d.get(k); return o instanceof Number?((Number)o).longValue():0; }
    private static double dbl(DocumentSnapshot d, String k) { Object o=d.get(k); return o instanceof Number?((Number)o).doubleValue():0; }
    private static String mapStr(Map<?,?> m,String k){Object o=m.get(k);return o==null?"":String.valueOf(o);}
    private static long mapLng(Map<?,?> m,String k){Object o=m.get(k);return o instanceof Number?((Number)o).longValue():0;}
    private static double mapDbl(Map<?,?> m,String k){Object o=m.get(k);return o instanceof Number?((Number)o).doubleValue():0;}
    private static String safe(String s){return s==null?"":s.trim();}
    private static String value(Cursor c,String n){int i=c.getColumnIndex(n);return i<0||c.isNull(i)?"":safe(c.getString(i));}
    private static long longValue(Cursor c,String n){int i=c.getColumnIndex(n);return i<0||c.isNull(i)?0:c.getLong(i);}
    private static int intValue(Cursor c,String n){int i=c.getColumnIndex(n);return i<0||c.isNull(i)?0:c.getInt(i);}
    private static double doubleValue(Cursor c,String n){int i=c.getColumnIndex(n);return i<0||c.isNull(i)?0:c.getDouble(i);}

    private static final class GardenKey {
        final long localId; final String cloudId;
        GardenKey(long localId, String cloudId){this.localId=localId;this.cloudId=cloudId;}
    }
}