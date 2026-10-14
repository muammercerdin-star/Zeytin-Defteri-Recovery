package com.zeytindefteri.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;

/**
 * Piyasa veri kaynağı.
 *
 * Uygulamadaki gerçek satış/fiyat kaydı Elek ekranında elek_records +
 * elek_grades tablolarına yazıldığı için Piyasa da aynı kaynağı kullanır.
 * Böylece Zeytin Ligi, Elek arşivi ve Piyasa birbirinden kopuk veri
 * kaynakları okumaz.
 *
 * Bölge, kaydın bağlı olduğu bahçenin district bilgisinden; district boşsa
 * province bilgisinden türetilir. Tüm sorgular aktif kullanıcı + aktif sezon
 * kapsamındadır ve kg/fiyatı sıfır olan satırlar piyasa hesabına alınmaz.
 */
public class MarketRepository {

    private final DatabaseHelper databaseHelper;

    public MarketRepository(DatabaseHelper databaseHelper) {
        this.databaseHelper = databaseHelper;
    }

    // ============================================================
    // LOCAL MARKET BY GRADE
    // ============================================================

    public Cursor getLocalMarketByGrade(String grade) {
        grade = safe(grade);

        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        String region = regionSql();
        Scope scope = scope();

        String sql =
                "SELECT " +
                        region + " AS region, " +
                        "COUNT(DISTINCT er.id) AS sale_count, " +
                        "COALESCE(SUM(eg.kg), 0) AS total_kg, " +
                        "COALESCE(AVG(eg.price_per_kg), 0) AS avg_price, " +
                        "COALESCE(MIN(eg.price_per_kg), 0) AS min_price, " +
                        "COALESCE(MAX(eg.price_per_kg), 0) AS max_price " +
                        baseFrom() +
                        "WHERE eg.grade = ? " +
                        validLineWhere() +
                        scope.where +
                        "AND " + region + " <> '' " +
                        "GROUP BY " + region + " " +
                        "ORDER BY total_kg DESC";

        ArrayList<String> args = new ArrayList<>();
        args.add(grade);
        args.addAll(scope.args);

        return db.rawQuery(sql, args.toArray(new String[0]));
    }

    // ============================================================
    // MARKET BY GRADE + REGION
    // ============================================================

    public Cursor getMarketByGradeAndRegion(String grade, String requestedRegion) {
        grade = safe(grade);
        requestedRegion = safe(requestedRegion);

        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        String region = regionSql();
        Scope scope = scope();

        String sql =
                "SELECT " +
                        region + " AS region, " +
                        "COUNT(DISTINCT er.id) AS sale_count, " +
                        "COALESCE(SUM(eg.kg), 0) AS total_kg, " +
                        "COALESCE(AVG(eg.price_per_kg), 0) AS avg_price, " +
                        "COALESCE(MIN(eg.price_per_kg), 0) AS min_price, " +
                        "COALESCE(MAX(eg.price_per_kg), 0) AS max_price " +
                        baseFrom() +
                        "WHERE eg.grade = ? " +
                        validLineWhere() +
                        scope.where +
                        "AND " + region + " = ? COLLATE NOCASE " +
                        "GROUP BY " + region;

        ArrayList<String> args = new ArrayList<>();
        args.add(grade);
        args.addAll(scope.args);
        args.add(requestedRegion);

        return db.rawQuery(sql, args.toArray(new String[0]));
    }

    // ============================================================
    // MARKET RAW RECORDS
    // ============================================================

    public Cursor getMarketSalesByGrade(String grade) {
        return getScopedElekRows(safe(grade), null);
    }

    public Cursor getMarketSalesByGradeAndRegion(String grade, String region) {
        return getScopedElekRows(safe(grade), safe(region));
    }

    // ============================================================
    // TOTAL KG
    // ============================================================

    public double getMarketTotalKg(String grade) {
        return scalarDouble(
                "COALESCE(SUM(eg.kg), 0)",
                safe(grade)
        );
    }

    // ============================================================
    // RECORD / SALE COUNT
    // ============================================================

    public int getMarketSaleCount(String grade) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        Scope scope = scope();

        String sql =
                "SELECT COUNT(DISTINCT er.id) " +
                        baseFrom() +
                        "WHERE eg.grade = ? " +
                        validLineWhere() +
                        scope.where;

        ArrayList<String> args = new ArrayList<>();
        args.add(safe(grade));
        args.addAll(scope.args);

        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, args.toArray(new String[0]));
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            if (cursor != null) cursor.close();
        }
    }


    // ============================================================
    // WEIGHTED AVERAGE PRICE
    // ============================================================

    public double getMarketWeightedAveragePrice(String grade) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        Scope scope = scope();

        String sql =
                "SELECT CASE " +
                        "WHEN COALESCE(SUM(eg.kg), 0) > 0 " +
                        "THEN SUM(eg.kg * eg.price_per_kg) / SUM(eg.kg) " +
                        "ELSE 0 END " +
                        baseFrom() +
                        "WHERE eg.grade = ? " +
                        validLineWhere() +
                        scope.where;

        ArrayList<String> args = new ArrayList<>();
        args.add(safe(grade));
        args.addAll(scope.args);

        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, args.toArray(new String[0]));
            return cursor.moveToFirst() ? cursor.getDouble(0) : 0d;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    // ============================================================
    // REGION COUNT
    // ============================================================

    public int getMarketRegionCount(String grade) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        Scope scope = scope();
        String region = regionSql();

        String sql =
                "SELECT COUNT(DISTINCT " + region + ") " +
                        baseFrom() +
                        "WHERE eg.grade = ? " +
                        validLineWhere() +
                        scope.where +
                        "AND " + region + " <> ''";

        ArrayList<String> args = new ArrayList<>();
        args.add(safe(grade));
        args.addAll(scope.args);

        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, args.toArray(new String[0]));
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    // ============================================================
    // SCOPED MARKET RAW RECORDS - ACTIVE OWNER + ACTIVE SEASON
    // ============================================================

    public Cursor getScopedMarketSalesByGrade(String grade) {
        return getScopedElekRows(safe(grade), null);
    }

    public Cursor getScopedMarketSalesByGradeAndRegion(String grade, String region) {
        return getScopedElekRows(safe(grade), safe(region));
    }

    /**
     * Elek kaydı zaten kullanıcının kendi satış/fiyat kaydıdır.
     * Ayrı sales tablosu veya market_consent şartı aranmaz.
     */
    public Cursor getScopedOwnSalesByGrade(String grade) {
        return getScopedElekRows(safe(grade), null);
    }

    // ============================================================
    // INTERNAL RAW ELEK QUERY
    // ============================================================

    private Cursor getScopedElekRows(String grade, String requestedRegion) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        Scope scope = scope();
        String region = regionSql();

        StringBuilder sql = new StringBuilder();
        ArrayList<String> args = new ArrayList<>();

        sql.append(
                "SELECT " +
                        "er.id AS sale_id, " +
                        "er.record_date AS sale_date, " +
                        region + " AS region, " +
                        "COALESCE(g.variety, '') AS variety, " +
                        "er.created_at AS created_at, " +
                        "er.season_year AS season_year, " +
                        "er.owner_uid AS owner_uid, " +
                        "eg.grade AS grade, " +
                        "eg.kg AS kg, " +
                        "eg.price_per_kg AS price_per_kg, " +
                        "eg.line_total AS line_total " +
                        baseFrom() +
                        "WHERE eg.grade = ? " +
                        validLineWhere() +
                        scope.where
        );

        args.add(grade);
        args.addAll(scope.args);

        if (requestedRegion != null && !requestedRegion.isEmpty()) {
            sql.append("AND ").append(region).append(" = ? COLLATE NOCASE ");
            args.add(requestedRegion);
        }

        sql.append("ORDER BY er.record_date DESC, er.created_at DESC, er.id DESC");

        return db.rawQuery(sql.toString(), args.toArray(new String[0]));
    }

    // ============================================================
    // SHARED SQL PARTS
    // ============================================================

    private String baseFrom() {
        return "FROM elek_grades eg " +
                "INNER JOIN elek_records er ON er.id = eg.elek_id " +
                "INNER JOIN gardens g ON g.id = er.garden_id ";
    }

    private String validLineWhere() {
        return "AND eg.kg > 0 " +
                "AND eg.price_per_kg > 0 ";
    }

    private String regionSql() {
        return "CASE " +
                "WHEN TRIM(COALESCE(g.district,'')) <> '' THEN TRIM(g.district) " +
                "WHEN TRIM(COALESCE(g.province,'')) <> '' THEN TRIM(g.province) " +
                "ELSE '' END";
    }

    private Scope scope() {
        Scope out = new Scope();

        String ownerUid = safe(databaseHelper.getActiveOwnerUid());
        int year = databaseHelper.getActiveSeasonYear();

        out.where =
                "AND COALESCE(g.owner_uid,'') = ? " +
                        "AND (COALESCE(er.owner_uid,'') = ? OR COALESCE(er.owner_uid,'') = '') " +
                        "AND (" +
                        "er.season_year = ? " +
                        "OR (COALESCE(er.season_year,0) = 0 AND (" +
                        "substr(TRIM(er.record_date),1,4) = ? " +
                        "OR substr(TRIM(er.record_date),-4) = ?" +
                        "))" +
                        ") ";

        out.args.add(ownerUid);
        out.args.add(ownerUid);
        out.args.add(String.valueOf(year));
        out.args.add(String.valueOf(year));
        out.args.add(String.valueOf(year));

        return out;
    }

    private double scalarDouble(String expression, String grade) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        Scope scope = scope();

        String sql =
                "SELECT " + expression + " " +
                        baseFrom() +
                        "WHERE eg.grade = ? " +
                        validLineWhere() +
                        scope.where;

        ArrayList<String> args = new ArrayList<>();
        args.add(grade);
        args.addAll(scope.args);

        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, args.toArray(new String[0]));
            return cursor.moveToFirst() ? cursor.getDouble(0) : 0d;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private static final class Scope {
        String where = "";
        final ArrayList<String> args = new ArrayList<>();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
