package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.UUID;


public class SaleRepository {


    // ============================================================
    // DATABASE
    // ============================================================

    private final DatabaseHelper databaseHelper;


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public SaleRepository(
            DatabaseHelper databaseHelper
    ) {


        this.databaseHelper =
                databaseHelper;
    }


    // ============================================================
    // ADD SALE
    // ============================================================

    public long addSale(
            int gardenId,
            Long buyerId,
            String saleDate,
            String saleKind,
            String region,
            String variety,
            String productType,
            double transportCost,
            double deductions,
            double amountReceived,
            String dueDate,
            String note,
            boolean marketConsent
    ) {


        // ========================================================
        // KONTROLLER
        // ========================================================

        if (gardenId <= 0) {


            return -1;
        }


        if (safe(
                saleDate
        ).isEmpty()) {


            return -1;
        }


        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();

        String ownerUid = databaseHelper.getActiveOwnerUid();
        if (!recordBelongsToOwner(db, "gardens", gardenId, ownerUid)) {
            return -1;
        }
        if (buyerId != null && buyerId > 0 &&
                !recordBelongsToOwner(db, "buyers", buyerId, ownerUid)) {
            return -1;
        }


        ContentValues values =
                new ContentValues();


        // ========================================================
        // BAHÇE
        // ========================================================

        values.put(
                "garden_id",
                gardenId
        );


        // ========================================================
        // ALICI
        // ========================================================

        if (buyerId != null
                &&
                buyerId > 0) {


            values.put(
                    "buyer_id",
                    buyerId
            );
        }


        // ========================================================
        // TARİH
        // ========================================================

        values.put(
                "sale_date",
                safe(
                        saleDate
                )
        );


        // ========================================================
        // SATIŞ TÜRÜ
        // ========================================================

        String finalSaleKind =
                safe(
                        saleKind
                );


        if (finalSaleKind.isEmpty()) {


            finalSaleKind =
                    "SALE";
        }


        values.put(
                "sale_kind",
                finalSaleKind
        );


        // ========================================================
        // BÖLGE
        // ========================================================

        values.put(
                "region",
                safe(
                        region
                )
        );


        // ========================================================
        // ÇEŞİT
        // ========================================================

        values.put(
                "variety",
                safe(
                        variety
                )
        );


        // ========================================================
        // ÜRÜN TÜRÜ
        // ========================================================

        String finalProductType =
                safe(
                        productType
                );


        if (finalProductType.isEmpty()) {


            finalProductType =
                    "SOFRALIK";
        }


        values.put(
                "product_type",
                finalProductType
        );


        // ========================================================
        // MALİYETLER
        // ========================================================

        values.put(
                "transport_cost",
                Math.max(
                        transportCost,
                        0
                )
        );


        values.put(
                "deductions",
                Math.max(
                        deductions,
                        0
                )
        );


        // ========================================================
        // TAHSİLAT
        // ========================================================

        values.put(
                "amount_received",
                Math.max(
                        amountReceived,
                        0
                )
        );


        // ========================================================
        // VADE
        // ========================================================

        values.put(
                "due_date",
                safe(
                        dueDate
                )
        );


        // ========================================================
        // NOT
        // ========================================================

        values.put(
                "note",
                safe(
                        note
                )
        );


        // ========================================================
        // PİYASA ONAYI
        // ========================================================

        values.put(
                "market_consent",
                marketConsent
                        ? 1
                        : 0
        );


        // ========================================================
        // SENKRON DURUMU
        // ========================================================

        values.put(
                "market_sync_status",
                "PENDING"
        );


        // ========================================================
        // BAŞLANGIÇ TOPLAMLARI
        // ========================================================

        values.put(
                "total_kg",
                0
        );


        values.put(
                "gross_total",
                0
        );


        // ========================================================
        // OLUŞTURMA TARİHİ
        // ========================================================

        long now = System.currentTimeMillis();

        values.put("created_at", now);
        values.put("updated_at", now);
        values.put("season_year", yearOf(saleDate));
        values.put("owner_uid", ownerUid);
        values.put("cloud_id", ownerUid.isEmpty() ? "" : UUID.randomUUID().toString());


        // ========================================================
        // INSERT
        // ========================================================

        return db.insert(
                "sales",
                null,
                values
        );
    }


    // ============================================================
    // ADD SALE GRADE
    // ============================================================

    public long addSaleGrade(
            long saleId,
            String grade,
            double kg,
            double pricePerKg
    ) {


        if (saleId <= 0) {


            return -1;
        }


        grade =
                safe(
                        grade
                );


        if (grade.isEmpty()) {


            return -1;
        }


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


        if (safeKg <= 0) {


            return -1;
        }


        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "sale_id",
                saleId
        );


        values.put(
                "grade",
                grade
        );


        values.put(
                "kg",
                safeKg
        );


        values.put(
                "price_per_kg",
                safePrice
        );


        values.put(
                "line_total",
                safeKg
                        *
                        safePrice
        );


        long result =
                db.insert(
                        "sale_grades",
                        null,
                        values
                );


        if (result != -1) {


            updateSaleTotals(
                    saleId
            );
        }


        return result;
    }


    // ============================================================
    // ADD SALE WITH GRADES
    //
    // SATIŞ + ELEK KALEMLERİNİ TEK TRANSACTION İÇİNDE KAYDEDER.
    // ============================================================

    public long addSaleWithGrades(
            int gardenId,
            Long buyerId,
            String saleDate,
            String saleKind,
            String region,
            String variety,
            String productType,
            double transportCost,
            double deductions,
            double amountReceived,
            String dueDate,
            String note,
            boolean marketConsent,
            String[] grades,
            double[] kilograms,
            double[] prices
    ) {


        // ========================================================
        // GİRİŞ KONTROLÜ
        // ========================================================

        if (gardenId <= 0) {


            return -1;
        }


        if (safe(
                saleDate
        ).isEmpty()) {


            return -1;
        }


        if (grades == null
                ||
                kilograms == null
                ||
                prices == null) {


            return -1;
        }


        if (grades.length == 0
                ||
                grades.length != kilograms.length
                ||
                grades.length != prices.length) {


            return -1;
        }


        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();

        String ownerUid = databaseHelper.getActiveOwnerUid();
        if (!recordBelongsToOwner(db, "gardens", gardenId, ownerUid)) {
            return -1;
        }
        if (buyerId != null && buyerId > 0 &&
                !recordBelongsToOwner(db, "buyers", buyerId, ownerUid)) {
            return -1;
        }


        long saleId =
                -1;


        db.beginTransaction();


        try {


            // ====================================================
            // SATIŞ ANA KAYDI
            // ====================================================

            ContentValues saleValues =
                    new ContentValues();


            saleValues.put(
                    "garden_id",
                    gardenId
            );


            if (buyerId != null
                    &&
                    buyerId > 0) {


                saleValues.put(
                        "buyer_id",
                        buyerId
                );
            }


            saleValues.put(
                    "sale_date",
                    safe(
                            saleDate
                    )
            );


            saleValues.put(
                    "sale_kind",
                    safe(
                            saleKind
                    ).isEmpty()
                            ? "SALE"
                            : saleKind.trim()
            );


            saleValues.put(
                    "region",
                    safe(
                            region
                    )
            );


            saleValues.put(
                    "variety",
                    safe(
                            variety
                    )
            );


            saleValues.put(
                    "product_type",
                    safe(
                            productType
                    ).isEmpty()
                            ? "SOFRALIK"
                            : productType.trim()
            );


            saleValues.put(
                    "total_kg",
                    0
            );


            saleValues.put(
                    "gross_total",
                    0
            );


            saleValues.put(
                    "transport_cost",
                    Math.max(
                            transportCost,
                            0
                    )
            );


            saleValues.put(
                    "deductions",
                    Math.max(
                            deductions,
                            0
                    )
            );


            saleValues.put(
                    "amount_received",
                    Math.max(
                            amountReceived,
                            0
                    )
            );


            saleValues.put(
                    "due_date",
                    safe(
                            dueDate
                    )
            );


            saleValues.put(
                    "note",
                    safe(
                            note
                    )
            );


            saleValues.put(
                    "market_consent",
                    marketConsent
                            ? 1
                            : 0
            );


            saleValues.put(
                    "market_sync_status",
                    "PENDING"
            );


            long now = System.currentTimeMillis();

            saleValues.put("created_at", now);
            saleValues.put("updated_at", now);
            saleValues.put("season_year", yearOf(saleDate));
            saleValues.put("owner_uid", ownerUid);
            saleValues.put("cloud_id", ownerUid.isEmpty() ? "" : UUID.randomUUID().toString());


            saleId =
                    db.insert(
                            "sales",
                            null,
                            saleValues
                    );


            if (saleId == -1) {


                throw new RuntimeException(
                        "Satış kaydı oluşturulamadı"
                );
            }


            // ====================================================
            // ELEK / SINIF SATIRLARI
            // ====================================================

            double totalKg =
                    0;


            double grossTotal =
                    0;


            int insertedGradeCount =
                    0;


            for (
                    int i = 0;
                    i < grades.length;
                    i++
            ) {


                String grade =
                        safe(
                                grades[i]
                        );


                double kg =
                        Math.max(
                                kilograms[i],
                                0
                        );


                double price =
                        Math.max(
                                prices[i],
                                0
                        );


                if (grade.isEmpty()
                        ||
                        kg <= 0) {


                    continue;
                }


                double lineTotal =
                        kg
                                *
                                price;


                ContentValues gradeValues =
                        new ContentValues();


                gradeValues.put(
                        "sale_id",
                        saleId
                );


                gradeValues.put(
                        "grade",
                        grade
                );


                gradeValues.put(
                        "kg",
                        kg
                );


                gradeValues.put(
                        "price_per_kg",
                        price
                );


                gradeValues.put(
                        "line_total",
                        lineTotal
                );


                long lineResult =
                        db.insert(
                                "sale_grades",
                                null,
                                gradeValues
                        );


                if (lineResult == -1) {


                    throw new RuntimeException(
                            "Satış kalemi kaydedilemedi"
                    );
                }


                totalKg +=
                        kg;


                grossTotal +=
                        lineTotal;


                insertedGradeCount++;
            }


            // ====================================================
            // BOŞ SATIŞ OLMASIN
            // ====================================================

            if (insertedGradeCount == 0
                    ||
                    totalKg <= 0) {


                throw new RuntimeException(
                        "Satışta geçerli ürün bulunamadı"
                );
            }


            // ====================================================
            // ANA SATIŞ TOPLAMLARINI GÜNCELLE
            // ====================================================

            ContentValues totalValues =
                    new ContentValues();


            totalValues.put(
                    "total_kg",
                    totalKg
            );


            totalValues.put(
                    "gross_total",
                    grossTotal
            );

            double netTotal = Math.max(
                    0,
                    grossTotal - Math.max(transportCost, 0) - Math.max(deductions, 0)
            );
            double safeReceived = Math.min(Math.max(amountReceived, 0), netTotal);
            totalValues.put("amount_received", safeReceived);
            totalValues.put("updated_at", System.currentTimeMillis());


            int updated =
                    db.update(
                            "sales",
                            totalValues,
                            "id = ?",
                            new String[]{
                                    String.valueOf(
                                            saleId
                                    )
                            }
                    );


            if (updated <= 0) {


                throw new RuntimeException(
                        "Satış toplamları güncellenemedi"
                );
            }

            // Gerçek ve kısmen tahsil edilmiş satış, raporlanabilir bir alacak oluşturur.
            double remaining = Math.max(0, netTotal - safeReceived);
            if ("SALE".equalsIgnoreCase(safe(saleKind)) && remaining > 0.009d) {
                ContentValues receivable = new ContentValues();
                receivable.put("title", "Satış alacağı");
                receivable.put("party_name", buyerName(db, buyerId));
                receivable.put("related_sale_id", saleId);
                receivable.put("amount", netTotal);
                receivable.put("total_received", safeReceived);
                receivable.put("due_date", safe(dueDate));
                receivable.put("status", "OPEN");
                receivable.put("note", safe(note));
                receivable.put("created_at", now);
                receivable.put("updated_at", now);
                receivable.put("owner_uid", ownerUid);
                receivable.put("cloud_id", ownerUid.isEmpty() ? "" : UUID.randomUUID().toString());

                if (db.insert("receivables", null, receivable) == -1) {
                    throw new RuntimeException("Satış alacağı kaydedilemedi");
                }
            }


            db.setTransactionSuccessful();


        } catch (Exception e) {


            e.printStackTrace();


            saleId =
                    -1;


        } finally {


            db.endTransaction();
        }


        return saleId;
    }


    // ============================================================
    // UPDATE SALE TOTALS
    // ============================================================

    private void updateSaleTotals(
            long saleId
    ) {


        if (saleId <= 0) {


            return;
        }


        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        if (!isActiveSale(
                db,
                saleId
        )) {
            return;
        }


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT " +

                                    "COALESCE(SUM(kg), 0) AS total_kg, " +

                                    "COALESCE(SUM(line_total), 0) AS gross_total " +

                                    "FROM sale_grades " +

                                    "WHERE sale_id = ?",

                            new String[]{
                                    String.valueOf(
                                            saleId
                                    )
                            }
                    );


            if (!cursor.moveToFirst()) {


                return;
            }


            double totalKg =
                    cursor.getDouble(
                            cursor.getColumnIndexOrThrow(
                                    "total_kg"
                            )
                    );


            double grossTotal =
                    cursor.getDouble(
                            cursor.getColumnIndexOrThrow(
                                    "gross_total"
                            )
                    );


            ContentValues values =
                    new ContentValues();


            values.put(
                    "total_kg",
                    totalKg
            );


            values.put(
                    "gross_total",
                    grossTotal
            );

            values.put("updated_at", System.currentTimeMillis());


            db.update(
                    "sales",
                    values,
                    "id = ?",
                    new String[]{
                            String.valueOf(
                                    saleId
                            )
                    }
            );


        } finally {


            if (cursor != null) {


                cursor.close();
            }
        }
    }


    // ============================================================
    // GET SALE
    // ============================================================

    public Cursor getSale(
            long saleId
    ) {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT " +

                        "s.*, " +

                        "g.name AS garden_name, " +

                        "b.name AS buyer_name, " +

                        "b.phone AS buyer_phone " +

                        "FROM sales s " +

                        "INNER JOIN gardens g " +
                        "ON g.id = s.garden_id " +

                        "LEFT JOIN buyers b " +
                        "ON b.id = s.buyer_id " +

                        "WHERE s.id = ? AND " + activeSaleCondition("s"),

                saleArgs(
                        String.valueOf(
                                saleId
                        )
                )
        );
    }


    // ============================================================
    // GET SALE GRADES
    // ============================================================

    public Cursor getSaleGrades(
            long saleId
    ) {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT sg.* " +

                        "FROM sale_grades sg " +

                        "INNER JOIN sales s " +
                        "ON s.id = sg.sale_id " +

                        "WHERE sg.sale_id = ? AND " + activeSaleCondition("s") + " " +

                        "ORDER BY CASE grade " +

                        "WHEN 'DOUBLE' THEN 0 " +

                        "WHEN '1' THEN 1 " +

                        "WHEN '2' THEN 2 " +

                        "WHEN '3' THEN 3 " +

                        "WHEN '4' THEN 4 " +

                        "WHEN 'YAGLIK' THEN 5 " +

                        "ELSE 6 END",

                saleArgs(
                        String.valueOf(
                                saleId
                        )
                )
        );
    }


    // ============================================================
    // GET GARDEN SALES
    // ============================================================

    public Cursor getGardenSales(
            long gardenId
    ) {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT " +

                        "s.*, " +

                        "b.name AS buyer_name " +

                        "FROM sales s " +

                        "LEFT JOIN buyers b " +
                        "ON b.id = s.buyer_id " +

                        "WHERE s.garden_id = ? AND " + activeSaleCondition("s") + " " +

                        "ORDER BY s.created_at DESC",

                saleArgs(
                        String.valueOf(
                                gardenId
                        )
                )
        );
    }


    // ============================================================
    // GET ALL SALES
    // ============================================================

    public Cursor getSales() {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT " +

                        "s.*, " +

                        "g.name AS garden_name, " +

                        "b.name AS buyer_name " +

                        "FROM sales s " +

                        "INNER JOIN gardens g " +
                        "ON g.id = s.garden_id " +

                        "LEFT JOIN buyers b " +
                        "ON b.id = s.buyer_id " +

                        "WHERE " + activeSaleCondition("s") + " " +

                        "ORDER BY s.created_at DESC",

                saleArgs()
        );
    }


    // ============================================================
    // GET REAL SALES
    // ============================================================

    public Cursor getRealSales() {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT " +

                        "s.*, " +

                        "g.name AS garden_name, " +

                        "b.name AS buyer_name " +

                        "FROM sales s " +

                        "INNER JOIN gardens g " +
                        "ON g.id = s.garden_id " +

                        "LEFT JOIN buyers b " +
                        "ON b.id = s.buyer_id " +

                        "WHERE s.sale_kind = 'SALE' AND " + activeSaleCondition("s") + " " +

                        "ORDER BY s.created_at DESC",

                saleArgs()
        );
    }


    // ============================================================
    // TOTAL SALES REVENUE
    //
    // Brüt toplam
    // - nakliye
    // - kesinti
    // ============================================================

    public double getTotalSalesRevenue() {


        return getDoubleValue(

                "SELECT COALESCE(" +

                        "SUM(" +

                        "COALESCE(s.gross_total, 0) " +

                        "- COALESCE(s.transport_cost, 0) " +

                        "- COALESCE(s.deductions, 0)" +

                        "), 0) " +

                        "FROM sales s " +

                        "WHERE s.sale_kind = 'SALE' AND " + activeSaleCondition("s"),
                saleArgs()
        );
    }


    // ============================================================
    // TOTAL GROSS SALES
    // ============================================================

    public double getTotalGrossSales() {


        return getDoubleValue(

                "SELECT COALESCE(" +

                        "SUM(s.gross_total), 0" +

                        ") " +

                        "FROM sales s " +

                        "WHERE s.sale_kind = 'SALE' AND " + activeSaleCondition("s"),
                saleArgs()
        );
    }


    // ============================================================
    // TOTAL SOLD KG
    // ============================================================

    public double getTotalSoldKg() {


        return getDoubleValue(

                "SELECT COALESCE(" +

                        "SUM(s.total_kg), 0" +

                        ") " +

                        "FROM sales s " +

                        "WHERE s.sale_kind = 'SALE' AND " + activeSaleCondition("s"),
                saleArgs()
        );
    }


    // ============================================================
    // TOTAL AMOUNT RECEIVED
    // ============================================================

    public double getTotalAmountReceived() {


        return getDoubleValue(

                "SELECT COALESCE(" +

                        "SUM(s.amount_received), 0" +

                        ") " +

                        "FROM sales s " +

                        "WHERE s.sale_kind = 'SALE' AND " + activeSaleCondition("s"),
                saleArgs()
        );
    }


    // ============================================================
    // REAL SALE COUNT
    // ============================================================

    public int getRealSaleCount() {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT COUNT(*) " +

                                    "FROM sales s " +

                                    "WHERE s.sale_kind = 'SALE' AND " + activeSaleCondition("s"),

                            saleArgs()
                    );


            if (cursor.moveToFirst()) {


                return cursor.getInt(
                        0
                );
            }


        } finally {


            if (cursor != null) {


                cursor.close();
            }
        }


        return 0;
    }


    // ============================================================
    // GARDEN SALES REVENUE
    // ============================================================

    public double getGardenSalesRevenue(
            long gardenId
    ) {


        if (gardenId <= 0) {


            return 0;
        }


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT COALESCE(" +

                                    "SUM(" +

                                    "COALESCE(s.gross_total, 0) " +

                                    "- COALESCE(s.transport_cost, 0) " +

                                    "- COALESCE(s.deductions, 0)" +

                                    "), 0) " +

                                    "FROM sales s " +

                                    "WHERE s.garden_id = ? " +

                                    "AND s.sale_kind = 'SALE' AND " + activeSaleCondition("s"),

                            saleArgs(
                                    String.valueOf(
                                            gardenId
                                    )
                            )
                    );


            if (cursor.moveToFirst()) {


                return cursor.getDouble(
                        0
                );
            }


        } finally {


            if (cursor != null) {


                cursor.close();
            }
        }


        return 0;
    }


    // ============================================================
    // GARDEN SOLD KG
    // ============================================================

    public double getGardenSoldKg(
            long gardenId
    ) {


        if (gardenId <= 0) {


            return 0;
        }


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT COALESCE(" +

                                    "SUM(s.total_kg), 0" +

                                    ") " +

                                    "FROM sales s " +

                                    "WHERE s.garden_id = ? " +

                                    "AND s.sale_kind = 'SALE' AND " + activeSaleCondition("s"),

                            saleArgs(
                                    String.valueOf(
                                            gardenId
                                    )
                            )
                    );


            if (cursor.moveToFirst()) {


                return cursor.getDouble(
                        0
                );
            }


        } finally {


            if (cursor != null) {


                cursor.close();
            }
        }


        return 0;
    }


    // ============================================================
    // DELETE SALE
    // ============================================================

    public boolean deleteSale(
            long saleId
    ) {


        if (saleId <= 0) {


            return false;
        }


        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        if (!isActiveSale(
                db,
                saleId
        )) {
            return false;
        }


        databaseHelper.recordCloudDeletion("sales", saleId);


        int deleted =
                db.delete(
                        "sales",
                        "id = ?",
                        new String[]{
                                String.valueOf(
                                        saleId
                                )
                        }
                );


        return deleted > 0;
    }


    // ============================================================
    // DELETE SALE GRADE
    // ============================================================

    public boolean deleteSaleGrade(
            long saleGradeId,
            long saleId
    ) {


        if (saleGradeId <= 0
                ||
                saleId <= 0) {


            return false;
        }


        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        if (!isActiveSale(
                db,
                saleId
        )) {
            return false;
        }


        int deleted =
                db.delete(
                        "sale_grades",
                        "id = ? AND sale_id = ?",
                        new String[]{

                                String.valueOf(
                                        saleGradeId
                                ),

                                String.valueOf(
                                        saleId
                                )
                        }
                );


        if (deleted > 0) {


            updateSaleTotals(
                    saleId
            );


            return true;
        }


        return false;
    }


    // ============================================================
    // UPDATE RECEIVED AMOUNT
    // ============================================================

    public boolean updateAmountReceived(
            long saleId,
            double amountReceived
    ) {


        if (saleId <= 0) {


            return false;
        }


        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        if (!isActiveSale(
                db,
                saleId
        )) {
            return false;
        }


        ContentValues values =
                new ContentValues();


        values.put(
                "amount_received",
                Math.max(
                        amountReceived,
                        0
                )
        );

        values.put("updated_at", System.currentTimeMillis());


        int updated =
                db.update(
                        "sales",
                        values,
                        "id = ?",
                        new String[]{
                                String.valueOf(
                                        saleId
                                )
                        }
                );


        return updated > 0;
    }


    // ============================================================
    // UPDATE MARKET SYNC STATUS
    // ============================================================

    public boolean updateMarketSyncStatus(
            long saleId,
            String status
    ) {


        if (saleId <= 0) {


            return false;
        }


        status =
                safe(
                        status
                );


        if (status.isEmpty()) {


            return false;
        }


        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        if (!isActiveSale(
                db,
                saleId
        )) {
            return false;
        }


        ContentValues values =
                new ContentValues();


        values.put(
                "market_sync_status",
                status
        );

        values.put("updated_at", System.currentTimeMillis());


        int updated =
                db.update(
                        "sales",
                        values,
                        "id = ?",
                        new String[]{
                                String.valueOf(
                                        saleId
                                )
                        }
                );


        return updated > 0;
    }


    // ============================================================
    // DOUBLE HELPER
    // ============================================================

    private double getDoubleValue(
            String sql
    ) {


        return getDoubleValue(
                sql,
                null
        );
    }


    private double getDoubleValue(
            String sql,
            String[] args
    ) {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(
                            sql,
                            args
                    );


            if (cursor.moveToFirst()) {


                return cursor.getDouble(
                        0
                );
            }


        } finally {


            if (cursor != null) {


                cursor.close();
            }
        }


        return 0;
    }


    // ============================================================
    // SAFE
    // ============================================================

    private String safe(
            String value
    ) {


        if (value == null) {


            return "";
        }


        return value.trim();
    }


    private int yearOf(String value) {
        String date = safe(value);
        if (date.length() >= 4) {
            try {
                if (date.length() >= 10 && date.charAt(4) == '-') {
                    return Integer.parseInt(date.substring(0, 4));
                }
                return Integer.parseInt(date.substring(date.length() - 4));
            } catch (Exception ignored) { }
        }
        return java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
    }


    private String buyerName(SQLiteDatabase db, Long buyerId) {
        if (buyerId == null || buyerId <= 0) return "";
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT name FROM buyers " +
                            "WHERE id = ? AND COALESCE(owner_uid, '') = ? LIMIT 1",
                    new String[]{
                            String.valueOf(buyerId),
                            safe(databaseHelper.getActiveOwnerUid())
                    }
            );
            return cursor.moveToFirst() ? safe(cursor.getString(0)) : "";
        } finally {
            if (cursor != null) cursor.close();
        }
    }


    private String activeSaleCondition(String alias) {
        int year =
                databaseHelper.getActiveSeasonYear();

        return "COALESCE(" + alias + ".owner_uid, '') = ? AND (" +
                "COALESCE(" + alias + ".season_year, 0) = " + year +
                " OR (COALESCE(" + alias + ".season_year, 0) = 0 AND (" +
                "substr(TRIM(" + alias + ".sale_date), -4) = '" + year + "' OR " +
                "substr(TRIM(" + alias + ".sale_date), 1, 4) = '" + year + "')))";
    }


    private String[] saleArgs(String... leadingArgs) {
        int leadingCount =
                leadingArgs == null
                        ? 0
                        : leadingArgs.length;

        String[] args =
                new String[leadingCount + 1];

        if (leadingCount > 0) {
            System.arraycopy(
                    leadingArgs,
                    0,
                    args,
                    0,
                    leadingCount
            );
        }

        args[leadingCount] =
                safe(
                        databaseHelper.getActiveOwnerUid()
                );

        return args;
    }


    private boolean isActiveSale(
            SQLiteDatabase db,
            long saleId
    ) {
        Cursor cursor =
                null;

        try {
            cursor = db.rawQuery(
                    "SELECT 1 FROM sales s " +
                            "WHERE s.id = ? AND " + activeSaleCondition("s") +
                            " LIMIT 1",
                    saleArgs(
                            String.valueOf(
                                    saleId
                            )
                    )
            );

            return cursor.moveToFirst();

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }


    private boolean recordBelongsToOwner(
            SQLiteDatabase db,
            String table,
            long id,
            String ownerUid
    ) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT 1 FROM " + table +
                            " WHERE id = ? AND COALESCE(owner_uid, '') = ? LIMIT 1",
                    new String[]{String.valueOf(id), safe(ownerUid)}
            );
            return cursor.moveToFirst();
        } finally {
            if (cursor != null) cursor.close();
        }
    }
}
