package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hasat + elek + satış + salamura için özel kullanıcı bulut katmanı.
 * Bulut yolu bahçe altında tutulur:
 * users/{uid}/gardens/{gardenCloudId}/{collection}/{recordCloudId}
 */
public class ProductionCloudRepository {

    public interface RestoreCallback {
        void onComplete(int restoredCount);
        void onError(Exception error);
    }

    private final DatabaseHelper databaseHelper;
    private final FirebaseFirestore firestore;

    public ProductionCloudRepository(DatabaseHelper databaseHelper) {
        this.databaseHelper = databaseHelper;
        this.firestore = FirebaseFirestore.getInstance();
    }

    public void syncAllLocal(String uid) {
        uid = safe(uid);
        if (uid.isEmpty()) return;

        ensureIdentities(uid);

        syncTable(uid, "harvests", "harvests");
        syncTable(uid, "elek_records", "elek_records");
        syncTable(uid, "sales", "sales");
        syncTable(uid, "brine_batches", "brine_batches");
    }

    /**
     * Kullanıcı anonim bölgesel Piyasa katkısını açtığında mevcut bütün Elek
     * kayıtlarını değişmemiş olsalar bile bir kez yeniden yazar. Bu, Cloud
     * Function tarafındaki anonimleştirme tetikleyicisinin eski kayıtları da
     * görmesini sağlar. Ham kayıtlar yine yalnız users/{uid}/... altında kalır.
     */
    public void syncAllElekForMarket(String uid) {
        uid = safe(uid);
        if (uid.isEmpty()) return;

        ensureIdentities(uid);

        Cursor c = null;
        try {
            c = cloudRows(uid, "elek_records", -1);
            while (c.moveToNext()) {
                syncCursor(uid, "elek_records", "elek_records", c, true);
            }
        } finally {
            if (c != null) c.close();
        }
    }

    public void syncHarvestByLocalId(String uid, long id) {
        syncOne(uid, "harvests", "harvests", id);
    }

    public void syncElekByLocalId(String uid, long id) {
        syncOne(uid, "elek_records", "elek_records", id);
    }

    public void syncSaleByLocalId(String uid, long id) {
        syncOne(uid, "sales", "sales", id);
    }

    public void syncBrineByLocalId(String uid, long id) {
        syncOne(uid, "brine_batches", "brine_batches", id);
    }

    private void syncOne(String uid, String table, String cloudCollection, long id) {
        uid = safe(uid);

        if (uid.isEmpty() || id <= 0) {
            return;
        }

        ensureIdentities(uid);

        Cursor c = null;

        try {
            c = cloudRows(uid, table, id);

            if (c.moveToFirst()) {
                syncCursor(uid, table, cloudCollection, c, false);
            }

        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    private void syncTable(String uid, String table, String cloudCollection) {
        Cursor c = null;

        try {
            c = cloudRows(uid, table, -1);

            while (c.moveToNext()) {
                syncCursor(uid, table, cloudCollection, c, false);
            }

        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    private Cursor cloudRows(String uid, String table, long id) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();

        String whereId = id > 0
                ? " AND r.id = ?"
                : "";

        String[] args = id > 0
                ? new String[]{
                        uid,
                        uid,
                        String.valueOf(id)
                }
                : new String[]{
                        uid,
                        uid
                };

        return db.rawQuery(
                "SELECT r.*, g.cloud_id AS garden_cloud_id " +
                        "FROM " + table + " r " +
                        "INNER JOIN gardens g ON g.id = r.garden_id " +
                        "WHERE r.owner_uid = ? " +
                        "AND g.owner_uid = ? " +
                        "AND g.cloud_id IS NOT NULL " +
                        "AND g.cloud_id <> ''" +
                        whereId,
                args
        );
    }

    private void syncCursor(
            String uid,
            String table,
            String cloudCollection,
            Cursor c,
            boolean forceMarketRefresh
    ) {

        String gardenCloudId = value(c, "garden_cloud_id");
        String cloudId = value(c, "cloud_id");

        if (gardenCloudId.isEmpty() || cloudId.isEmpty()) {
            return;
        }

        Map<String, Object> data;

        if ("harvests".equals(table)) {

            data = harvestMap(c);

        } else if ("elek_records".equals(table)) {

            data = elekMap(c);

        } else if ("sales".equals(table)) {

            data = saleMap(c);

        } else {

            data = brineMap(c);
        }

        data.put("cloud_id", cloudId);
        data.put("owner_uid", uid);
        data.put("garden_cloud_id", gardenCloudId);

        if (forceMarketRefresh && "elek_records".equals(table)) {
            data.put("market_share_touch", System.currentTimeMillis());
        }

        Object updatedObj = data.get("updated_at");

        final long localUpdatedAt =
                updatedObj instanceof Number
                        ? ((Number) updatedObj).longValue()
                        : 0L;

        if (!forceMarketRefresh && !databaseHelper.shouldUploadCloudRecord(
                uid,
                table,
                cloudId,
                localUpdatedAt
        )) {
            return;
        }

        final String finalCloudId = cloudId;

        childRef(
                uid,
                gardenCloudId,
                cloudCollection
        )
                .document(cloudId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused ->
                        databaseHelper.markCloudUploadComplete(
                                uid,
                                table,
                                finalCloudId,
                                localUpdatedAt
                        )
                );
    }

    private Map<String, Object> harvestMap(Cursor c) {

        Map<String, Object> m = new HashMap<>();

        long id = longValue(c, "id");

        m.put("harvest_date", value(c, "harvest_date"));
        m.put("season_year", intValue(c, "season_year"));
        m.put("total_kg", doubleValue(c, "total_kg"));
        m.put("labor_cost", doubleValue(c, "labor_cost"));
        m.put("transport_cost", doubleValue(c, "transport_cost"));
        m.put("other_cost", doubleValue(c, "other_cost"));
        m.put("note", value(c, "note"));
        m.put("created_at", longValue(c, "created_at"));
        m.put("updated_at", maxTimestamp(c));

        m.put(
                "grades",
                rowsAsMaps(
                        "SELECT grade, kg, reference_price_per_kg, reference_price_date " +
                                "FROM harvest_grades " +
                                "WHERE harvest_id = ? " +
                                "ORDER BY id",
                        new String[]{
                                String.valueOf(id)
                        }
                )
        );

        return m;
    }

    private Map<String, Object> elekMap(Cursor c) {

        Map<String, Object> m = new HashMap<>();

        long id = longValue(c, "id");

        m.put("record_date", value(c, "record_date"));
        m.put("season_year", intValue(c, "season_year"));
        m.put("total_kg", doubleValue(c, "total_kg"));
        m.put("estimated_total", doubleValue(c, "estimated_total"));
        m.put("note", value(c, "note"));
        m.put("created_at", longValue(c, "created_at"));
        m.put("updated_at", maxTimestamp(c));

        m.put(
                "grades",
                rowsAsMaps(
                        "SELECT grade, kg, price_per_kg, line_total " +
                                "FROM elek_grades " +
                                "WHERE elek_id = ? " +
                                "ORDER BY id",
                        new String[]{
                                String.valueOf(id)
                        }
                )
        );

        return m;
    }

    private String saleSourceElekCloudId(Cursor c) {
        int col = c.getColumnIndex("source_elek_cloud_id");
        String cloud = col >= 0 && !c.isNull(col) ? c.getString(col) : "";
        if (cloud != null && !cloud.trim().isEmpty()) return cloud.trim();
        int idCol = c.getColumnIndex("source_elek_id");
        long elekId = idCol >= 0 && !c.isNull(idCol) ? c.getLong(idCol) : 0;
        if (elekId <= 0) return "";
        Cursor e = null;
        try {
            e = databaseHelper.getReadableDatabase().rawQuery(
                    "SELECT COALESCE(cloud_id,'') FROM elek_records WHERE id=?", new String[]{String.valueOf(elekId)});
            return e.moveToFirst() ? e.getString(0) : "";
        } catch (RuntimeException ignored) {
            return "";
        } finally {
            if (e != null) e.close();
        }
    }

    private Map<String, Object> saleMap(Cursor c) {

        Map<String, Object> m = new HashMap<>();

        long id = longValue(c, "id");

        m.put("sale_date", value(c, "sale_date"));
        m.put("season_year", intValue(c, "season_year"));
        m.put("sale_kind", value(c, "sale_kind"));
        m.put("region", value(c, "region"));
        m.put("variety", value(c, "variety"));
        m.put("product_type", value(c, "product_type"));
        m.put("total_kg", doubleValue(c, "total_kg"));
        m.put("gross_total", doubleValue(c, "gross_total"));
        m.put("transport_cost", doubleValue(c, "transport_cost"));
        m.put("deductions", doubleValue(c, "deductions"));
        m.put("amount_received", doubleValue(c, "amount_received"));
        m.put("due_date", value(c, "due_date"));
        m.put("note", value(c, "note"));
        m.put("market_consent", intValue(c, "market_consent"));
        m.put("market_sync_status", value(c, "market_sync_status"));
        // Elekten oluşturulduysa kaynağın bulut kimliği (cihazlar arası çift dönüşümü önler).
        m.put("source_elek_cloud_id", saleSourceElekCloudId(c));
        m.put("created_at", longValue(c, "created_at"));
        m.put("updated_at", maxTimestamp(c));

        m.put(
                "grades",
                rowsAsMaps(
                        "SELECT grade, kg, price_per_kg, line_total " +
                                "FROM sale_grades " +
                                "WHERE sale_id = ? " +
                                "ORDER BY id",
                        new String[]{
                                String.valueOf(id)
                        }
                )
        );

        long buyerId = longValue(c, "buyer_id");

        if (buyerId > 0) {

            Cursor b = null;

            try {

                b = databaseHelper
                        .getReadableDatabase()
                        .rawQuery(
                                "SELECT name, phone, region, note " +
                                        "FROM buyers " +
                                        "WHERE id = ? " +
                                        "LIMIT 1",
                                new String[]{
                                        String.valueOf(buyerId)
                                }
                        );

                if (b.moveToFirst()) {

                    Map<String, Object> buyer = new HashMap<>();

                    buyer.put("name", value(b, "name"));
                    buyer.put("phone", value(b, "phone"));
                    buyer.put("region", value(b, "region"));
                    buyer.put("note", value(b, "note"));

                    m.put("buyer", buyer);
                }

            } finally {

                if (b != null) {
                    b.close();
                }
            }
        }

        return m;
    }

    private Map<String, Object> brineMap(Cursor c) {

        Map<String, Object> m = new HashMap<>();

        long id = longValue(c, "id");

        String[] fields = {
                "batch_name",
                "start_date",
                "expected_ready_date",
                "variety",
                "grade",
                "status",
                "note"
        };

        for (String f : fields) {
            m.put(f, value(c, f));
        }

        String[] nums = {
                "container_liter",
                "olive_kg",
                "salt_kg",
                "water_liter",
                "salt_cost",
                "packaging_cost",
                "other_cost",
                "ready_kg"
        };

        for (String f : nums) {
            m.put(f, doubleValue(c, f));
        }

        m.put("created_at", longValue(c, "created_at"));
        m.put("updated_at", maxTimestamp(c));
        m.put("season_year", intValue(c, "season_year"));

        m.put(
                "events",
                rowsAsMaps(
                        "SELECT event_date, event_type, amount, unit, cost, note, created_at " +
                                "FROM brine_events " +
                                "WHERE batch_id = ? " +
                                "ORDER BY id",
                        new String[]{
                                String.valueOf(id)
                        }
                )
        );

        return m;
    }

    private List<Map<String, Object>> rowsAsMaps(
            String sql,
            String[] args
    ) {

        List<Map<String, Object>> rows =
                new ArrayList<>();

        Cursor c = null;

        try {

            c = databaseHelper
                    .getReadableDatabase()
                    .rawQuery(sql, args);

            String[] names =
                    c.getColumnNames();

            while (c.moveToNext()) {

                Map<String, Object> row =
                        new HashMap<>();

                for (String name : names) {

                    int i =
                            c.getColumnIndex(name);

                    if (i < 0 || c.isNull(i)) {

                        row.put(name, "");
                        continue;
                    }

                    switch (c.getType(i)) {

                        case Cursor.FIELD_TYPE_INTEGER:

                            row.put(
                                    name,
                                    c.getLong(i)
                            );

                            break;

                        case Cursor.FIELD_TYPE_FLOAT:

                            row.put(
                                    name,
                                    c.getDouble(i)
                            );

                            break;

                        default:

                            row.put(
                                    name,
                                    c.getString(i)
                            );

                            break;
                    }
                }

                rows.add(row);
            }

        } finally {

            if (c != null) {
                c.close();
            }
        }

        return rows;
    }

    public void restoreAll(
            String uid,
            RestoreCallback callback
    ) {

        uid = safe(uid);

        if (uid.isEmpty()) {

            if (callback != null) {
                callback.onComplete(0);
            }

            return;
        }

        ensureIdentities(uid);

        List<GardenKey> gardens =
                gardenKeys(uid);

        if (gardens.isEmpty()) {

            if (callback != null) {
                callback.onComplete(0);
            }

            return;
        }

        AtomicInteger pending =
                new AtomicInteger(
                        gardens.size() * 4
                );

        AtomicInteger restored =
                new AtomicInteger(0);

        final Exception[] firstError =
                new Exception[1];

        for (GardenKey g : gardens) {

            fetchCollection(
                    uid,
                    g,
                    "harvests",
                    pending,
                    restored,
                    firstError,
                    callback
            );

            fetchCollection(
                    uid,
                    g,
                    "elek_records",
                    pending,
                    restored,
                    firstError,
                    callback
            );

            fetchCollection(
                    uid,
                    g,
                    "sales",
                    pending,
                    restored,
                    firstError,
                    callback
            );

            fetchCollection(
                    uid,
                    g,
                    "brine_batches",
                    pending,
                    restored,
                    firstError,
                    callback
            );
        }
    }

    private void fetchCollection(
            final String uid,
            final GardenKey garden,
            final String collection,
            final AtomicInteger pending,
            final AtomicInteger restored,
            final Exception[] firstError,
            final RestoreCallback callback
    ) {

        final String cursorType =
                "__pull_" +
                        collection +
                        "__";

        final long pullCursor =
                databaseHelper.getCloudRemoteCursor(
                        uid,
                        cursorType,
                        garden.cloudId
                );

        Query query =
                childRef(
                        uid,
                        garden.cloudId,
                        collection
                );

        if (pullCursor > 0) {

            query =
                    query
                            .whereGreaterThan(
                                    "updated_at",
                                    pullCursor
                            )
                            .orderBy(
                                    "updated_at",
                                    Query.Direction.ASCENDING
                            );
        }

        query
                .get()
                .addOnCompleteListener(task -> {

                    long maxRemoteUpdatedAt =
                            pullCursor;

                    if (
                            task.isSuccessful()
                                    &&
                            task.getResult() != null
                    ) {

                        QuerySnapshot snapshot =
                                task.getResult();

                        for (
                                DocumentSnapshot doc :
                                snapshot.getDocuments()
                        ) {

                            long documentUpdatedAt =
                                    numberLong(
                                            doc,
                                            "updated_at"
                                    );

                            if (
                                    documentUpdatedAt
                                            >
                                    maxRemoteUpdatedAt
                            ) {

                                maxRemoteUpdatedAt =
                                        documentUpdatedAt;
                            }

                            try {

                                if (
                                        "harvests"
                                                .equals(collection)
                                ) {

                                    restoreHarvest(
                                            uid,
                                            garden.localId,
                                            doc
                                    );

                                } else if (
                                        "elek_records"
                                                .equals(collection)
                                ) {

                                    restoreElek(
                                            uid,
                                            garden.localId,
                                            doc
                                    );

                                } else if (
                                        "sales"
                                                .equals(collection)
                                ) {

                                    restoreSale(
                                            uid,
                                            garden.localId,
                                            doc
                                    );

                                } else {

                                    restoreBrine(
                                            uid,
                                            garden.localId,
                                            doc
                                    );
                                }

                                restored
                                        .incrementAndGet();

                            } catch (Exception e) {

                                if (firstError[0] == null) {
                                    firstError[0] = e;
                                }
                            }
                        }

                    } else if (
                            firstError[0] == null
                    ) {

                        firstError[0] =
                                task.getException() != null
                                        ? task.getException()
                                        : new Exception(
                                                "Bulut verisi okunamadı"
                                        );
                    }

                    if (
                            maxRemoteUpdatedAt
                                    >
                            pullCursor
                    ) {

                        databaseHelper
                                .markCloudRemoteApplied(
                                        uid,
                                        cursorType,
                                        garden.cloudId,
                                        maxRemoteUpdatedAt
                                );
                    }

                    if (
                            pending.decrementAndGet() == 0
                                    &&
                            callback != null
                    ) {

                        if (
                                firstError[0] != null
                                        &&
                                restored.get() == 0
                        ) {

                            callback.onError(
                                    firstError[0]
                            );

                        } else {

                            callback.onComplete(
                                    restored.get()
                            );
                        }
                    }
                });
    }

    private void restoreHarvest(
            String uid,
            long gardenId,
            DocumentSnapshot d
    ) {

        if (!shouldApplyRemote(
                "harvests",
                uid,
                d
        )) {
            return;
        }

        SQLiteDatabase db =
                databaseHelper
                        .getWritableDatabase();

        long id =
                upsertParent(
                        db,
                        "harvests",
                        uid,
                        gardenId,
                        d,
                        harvestValues(d)
                );

        db.delete(
                "harvest_grades",
                "harvest_id = ?",
                new String[]{
                        String.valueOf(id)
                }
        );

        insertMapRows(
                db,
                "harvest_grades",
                "harvest_id",
                id,
                listMaps(
                        d,
                        "grades"
                )
        );

        databaseHelper
                .markCloudRemoteApplied(
                        uid,
                        "harvests",
                        d.getId(),
                        numberLong(
                                d,
                                "updated_at"
                        )
                );
    }

    private void restoreElek(
            String uid,
            long gardenId,
            DocumentSnapshot d
    ) {

        if (!shouldApplyRemote(
                "elek_records",
                uid,
                d
        )) {
            return;
        }

        SQLiteDatabase db =
                databaseHelper
                        .getWritableDatabase();

        long id =
                upsertParent(
                        db,
                        "elek_records",
                        uid,
                        gardenId,
                        d,
                        elekValues(d)
                );

        db.delete(
                "elek_grades",
                "elek_id = ?",
                new String[]{
                        String.valueOf(id)
                }
        );

        insertMapRows(
                db,
                "elek_grades",
                "elek_id",
                id,
                listMaps(
                        d,
                        "grades"
                )
        );

        databaseHelper
                .markCloudRemoteApplied(
                        uid,
                        "elek_records",
                        d.getId(),
                        numberLong(
                                d,
                                "updated_at"
                        )
                );
    }

    private void restoreSale(
            String uid,
            long gardenId,
            DocumentSnapshot d
    ) {

        if (!shouldApplyRemote(
                "sales",
                uid,
                d
        )) {
            return;
        }

        SQLiteDatabase db =
                databaseHelper
                        .getWritableDatabase();

        ContentValues v =
                saleValues(d);

        Map<String, Object> buyer =
                mapValue(
                        d.get("buyer")
                );

        if (
                !buyer.isEmpty()
                        &&
                !safeObj(
                        buyer.get("name")
                ).isEmpty()
        ) {

            ContentValues b =
                    new ContentValues();

            b.put(
                    "name",
                    safeObj(
                            buyer.get("name")
                    )
            );

            b.put(
                    "phone",
                    safeObj(
                            buyer.get("phone")
                    )
            );

            b.put(
                    "region",
                    safeObj(
                            buyer.get("region")
                    )
            );

            b.put(
                    "note",
                    safeObj(
                            buyer.get("note")
                    )
            );

            b.put(
                    "created_at",
                    System.currentTimeMillis()
            );

            b.put("owner_uid", uid);

            long buyerId =
                    db.insert(
                            "buyers",
                            null,
                            b
                    );

            if (buyerId > 0) {

                v.put(
                        "buyer_id",
                        buyerId
                );
            }
        }

        long id =
                upsertParent(
                        db,
                        "sales",
                        uid,
                        gardenId,
                        d,
                        v
                );

        db.delete(
                "sale_grades",
                "sale_id = ?",
                new String[]{
                        String.valueOf(id)
                }
        );

        insertMapRows(
                db,
                "sale_grades",
                "sale_id",
                id,
                listMaps(
                        d,
                        "grades"
                )
        );

        databaseHelper
                .markCloudRemoteApplied(
                        uid,
                        "sales",
                        d.getId(),
                        numberLong(
                                d,
                                "updated_at"
                        )
                );
    }

    private void restoreBrine(
            String uid,
            long gardenId,
            DocumentSnapshot d
    ) {

        if (!shouldApplyRemote(
                "brine_batches",
                uid,
                d
        )) {
            return;
        }

        SQLiteDatabase db =
                databaseHelper
                        .getWritableDatabase();

        long id =
                upsertParent(
                        db,
                        "brine_batches",
                        uid,
                        gardenId,
                        d,
                        brineValues(d)
                );

        db.delete(
                "brine_events",
                "batch_id = ?",
                new String[]{
                        String.valueOf(id)
                }
        );

        insertMapRows(
                db,
                "brine_events",
                "batch_id",
                id,
                listMaps(
                        d,
                        "events"
                )
        );

        databaseHelper
                .markCloudRemoteApplied(
                        uid,
                        "brine_batches",
                        d.getId(),
                        numberLong(
                                d,
                                "updated_at"
                        )
                );
    }

    private boolean shouldApplyRemote(
            String table,
            String uid,
            DocumentSnapshot d
    ) {

        String cloudId =
                safe(
                        d.getId()
                );

        if (cloudId.isEmpty()) {
            return false;
        }

        if (
                databaseHelper
                        .hasCloudDeletionTombstone(
                                uid,
                                table,
                                cloudId
                        )
        ) {
            return false;
        }

        long remoteUpdatedAt =
                numberLong(
                        d,
                        "updated_at"
                );

        if (remoteUpdatedAt <= 0) {

            remoteUpdatedAt =
                    numberLong(
                            d,
                            "created_at"
                    );
        }

        Cursor c = null;

        try {

            c = databaseHelper
                    .getReadableDatabase()
                    .rawQuery(
                            "SELECT updated_at, created_at " +
                                    "FROM " + table + " " +
                                    "WHERE owner_uid = ? " +
                                    "AND cloud_id = ? " +
                                    "LIMIT 1",
                            new String[]{
                                    uid,
                                    cloudId
                            }
                    );

            if (!c.moveToFirst()) {
                return true;
            }

            long localUpdatedAt =
                    Math.max(
                            c.getLong(0),
                            c.getLong(1)
                    );

            return remoteUpdatedAt
                    >=
                    localUpdatedAt;

        } finally {

            if (c != null) {
                c.close();
            }
        }
    }

    private long upsertParent(
            SQLiteDatabase db,
            String table,
            String uid,
            long gardenId,
            DocumentSnapshot d,
            ContentValues v
    ) {

        String cloudId =
                safe(
                        d.getId()
                );

        v.put(
                "garden_id",
                gardenId
        );

        v.put(
                "owner_uid",
                uid
        );

        v.put(
                "cloud_id",
                cloudId
        );

        Cursor c = null;

        try {

            c = db.rawQuery(
                    "SELECT id " +
                            "FROM " + table + " " +
                            "WHERE owner_uid = ? " +
                            "AND cloud_id = ? " +
                            "LIMIT 1",
                    new String[]{
                            uid,
                            cloudId
                    }
            );

            if (c.moveToFirst()) {

                long id =
                        c.getLong(0);

                db.update(
                        table,
                        v,
                        "id = ?",
                        new String[]{
                                String.valueOf(id)
                        }
                );

                return id;
            }

        } finally {

            if (c != null) {
                c.close();
            }
        }

        return db.insert(
                table,
                null,
                v
        );
    }

    private ContentValues harvestValues(
            DocumentSnapshot d
    ) {

        ContentValues v =
                commonTimes(d);

        put(
                v,
                "harvest_date",
                d
        );

        Long harvestSeason = d.getLong("season_year");
        v.put("season_year", harvestSeason == null ? 0 : harvestSeason.intValue());

        putD(
                v,
                "total_kg",
                d
        );

        putD(
                v,
                "labor_cost",
                d
        );

        putD(
                v,
                "transport_cost",
                d
        );

        putD(
                v,
                "other_cost",
                d
        );

        put(
                v,
                "note",
                d
        );

        return v;
    }

    private ContentValues elekValues(
            DocumentSnapshot d
    ) {

        ContentValues v =
                commonTimes(d);

        put(
                v,
                "record_date",
                d
        );

        Long elekSeason = d.getLong("season_year");
        v.put("season_year", elekSeason == null ? 0 : elekSeason.intValue());

        putD(
                v,
                "total_kg",
                d
        );

        putD(
                v,
                "estimated_total",
                d
        );

        put(
                v,
                "note",
                d
        );

        return v;
    }

    private ContentValues saleValues(
            DocumentSnapshot d
    ) {

        ContentValues v =
                commonTimes(d);

        Long saleSeason = d.getLong("season_year");
        v.put("season_year", saleSeason == null ? 0 : saleSeason.intValue());

        String[] ss = {
                "sale_date",
                "sale_kind",
                "region",
                "variety",
                "product_type",
                "due_date",
                "note",
                "market_sync_status",
                "source_elek_cloud_id"
        };

        for (String f : ss) {
            put(
                    v,
                    f,
                    d
            );
        }

        String[] ds = {
                "total_kg",
                "gross_total",
                "transport_cost",
                "deductions",
                "amount_received"
        };

        for (String f : ds) {
            putD(
                    v,
                    f,
                    d
            );
        }

        Long consent =
                d.getLong(
                        "market_consent"
                );

        v.put(
                "market_consent",
                consent == null
                        ? 0
                        : consent.intValue()
        );

        return v;
    }

    private ContentValues brineValues(
            DocumentSnapshot d
    ) {

        ContentValues v =
                commonTimes(d);

        Long brineSeason = d.getLong("season_year");
        v.put("season_year", brineSeason == null ? 0 : brineSeason.intValue());

        String[] ss = {
                "batch_name",
                "start_date",
                "expected_ready_date",
                "variety",
                "grade",
                "status",
                "note"
        };

        for (String f : ss) {
            put(
                    v,
                    f,
                    d
            );
        }

        String[] ds = {
                "container_liter",
                "olive_kg",
                "salt_kg",
                "water_liter",
                "salt_cost",
                "packaging_cost",
                "other_cost",
                "ready_kg"
        };

        for (String f : ds) {
            putD(
                    v,
                    f,
                    d
            );
        }

        return v;
    }

    private ContentValues commonTimes(
            DocumentSnapshot d
    ) {

        ContentValues v =
                new ContentValues();

        Long c =
                d.getLong(
                        "created_at"
                );

        Long u =
                d.getLong(
                        "updated_at"
                );

        v.put(
                "created_at",
                c == null
                        ? System.currentTimeMillis()
                        : c
        );

        v.put(
                "updated_at",
                u == null
                        ? 0
                        : u
        );

        return v;
    }

    private void insertMapRows(
            SQLiteDatabase db,
            String table,
            String fk,
            long parentId,
            List<Map<String, Object>> rows
    ) {

        for (
                Map<String, Object> row :
                rows
        ) {

            ContentValues v =
                    new ContentValues();

            v.put(
                    fk,
                    parentId
            );

            for (
                    Map.Entry<String, Object> e :
                    row.entrySet()
            ) {

                Object x =
                        e.getValue();

                if (x instanceof Number) {

                    v.put(
                            e.getKey(),
                            ((Number) x)
                                    .doubleValue()
                    );

                } else {

                    v.put(
                            e.getKey(),
                            safeObj(x)
                    );
                }
            }

            db.insert(
                    table,
                    null,
                    v
            );
        }
    }

    private void ensureIdentities(
            String uid
    ) {

        SQLiteDatabase db =
                databaseHelper
                        .getWritableDatabase();

        String[] tables = {
                "harvests",
                "elek_records",
                "sales",
                "brine_batches"
        };

        for (
                String table :
                tables
        ) {

            db.execSQL(
                    "UPDATE " + table + " " +
                            "SET owner_uid = ? " +
                            "WHERE (owner_uid IS NULL OR owner_uid = '') " +
                            "AND garden_id IN (" +
                            "SELECT id FROM gardens WHERE owner_uid = ?" +
                            ")",
                    new Object[]{
                            uid,
                            uid
                    }
            );

            Cursor c = null;

            try {

                c = db.rawQuery(
                        "SELECT id " +
                                "FROM " + table + " " +
                                "WHERE owner_uid = ? " +
                                "AND (cloud_id IS NULL OR cloud_id = '')",
                        new String[]{
                                uid
                        }
                );

                while (c.moveToNext()) {

                    ContentValues v =
                            new ContentValues();

                    v.put(
                            "cloud_id",
                            UUID.randomUUID()
                                    .toString()
                    );

                    v.put(
                            "updated_at",
                            System.currentTimeMillis()
                    );

                    db.update(
                            table,
                            v,
                            "id = ?",
                            new String[]{
                                    String.valueOf(
                                            c.getLong(0)
                                    )
                            }
                    );
                }

            } finally {

                if (c != null) {
                    c.close();
                }
            }
        }

        db.execSQL(
                "UPDATE buyers SET owner_uid = ? " +
                        "WHERE (owner_uid IS NULL OR TRIM(owner_uid) = '') " +
                        "AND id IN (SELECT buyer_id FROM sales WHERE owner_uid = ? AND buyer_id IS NOT NULL)",
                new Object[]{uid, uid}
        );
    }

    private List<GardenKey> gardenKeys(
            String uid
    ) {

        List<GardenKey> out =
                new ArrayList<>();

        Cursor c = null;

        try {

            c = databaseHelper
                    .getReadableDatabase()
                    .rawQuery(
                            "SELECT id, cloud_id " +
                                    "FROM gardens " +
                                    "WHERE owner_uid = ? " +
                                    "AND cloud_id IS NOT NULL " +
                                    "AND cloud_id <> ''",
                            new String[]{
                                    uid
                            }
                    );

            while (c.moveToNext()) {

                out.add(
                        new GardenKey(
                                c.getLong(0),
                                c.getString(1)
                        )
                );
            }

        } finally {

            if (c != null) {
                c.close();
            }
        }

        return out;
    }

    private CollectionReference childRef(
            String uid,
            String gardenCloudId,
            String collection
    ) {

        return firestore
                .collection("users")
                .document(uid)
                .collection("gardens")
                .document(gardenCloudId)
                .collection(collection);
    }

    private static class GardenKey {

        final long localId;
        final String cloudId;

        GardenKey(
                long localId,
                String cloudId
        ) {

            this.localId =
                    localId;

            this.cloudId =
                    cloudId;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listMaps(
            DocumentSnapshot d,
            String field
    ) {

        Object x =
                d.get(field);

        if (x instanceof List) {

            return (List<Map<String, Object>>) x;
        }

        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(
            Object x
    ) {

        if (x instanceof Map) {

            return (Map<String, Object>) x;
        }

        return new HashMap<>();
    }

    private long numberLong(
            DocumentSnapshot document,
            String key
    ) {

        Object value =
                document.get(key);

        if (!(value instanceof Number)) {
            return 0L;
        }

        return ((Number) value)
                .longValue();
    }

    private static String safeObj(
            Object x
    ) {

        return x == null
                ? ""
                : String.valueOf(x)
                .trim();
    }

    private static String safe(
            String s
    ) {

        return s == null
                ? ""
                : s.trim();
    }

    private static String value(
            Cursor c,
            String name
    ) {

        int i =
                c.getColumnIndex(name);

        return i < 0 || c.isNull(i)
                ? ""
                : safe(
                        c.getString(i)
                );
    }

    private static long longValue(
            Cursor c,
            String name
    ) {

        int i =
                c.getColumnIndex(name);

        return i < 0 || c.isNull(i)
                ? 0L
                : c.getLong(i);
    }

    private static int intValue(
            Cursor c,
            String name
    ) {

        return (int) longValue(
                c,
                name
        );
    }

    private static double doubleValue(
            Cursor c,
            String name
    ) {

        int i =
                c.getColumnIndex(name);

        return i < 0 || c.isNull(i)
                ? 0D
                : c.getDouble(i);
    }

    private static long maxTimestamp(
            Cursor c
    ) {

        return Math.max(
                longValue(
                        c,
                        "updated_at"
                ),
                longValue(
                        c,
                        "created_at"
                )
        );
    }

    private static void put(
            ContentValues v,
            String f,
            DocumentSnapshot d
    ) {

        v.put(
                f,
                safe(
                        d.getString(f)
                )
        );
    }

    private static void putD(
            ContentValues v,
            String f,
            DocumentSnapshot d
    ) {

        Double x =
                d.getDouble(f);

        v.put(
                f,
                x == null
                        ? 0D
                        : x
        );
    }
}
