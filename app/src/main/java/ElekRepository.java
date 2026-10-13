package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.Calendar;
import java.util.UUID;


public class ElekRepository {


    // ============================================================
    // DATABASE
    // ============================================================

    private final DatabaseHelper databaseHelper;


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public ElekRepository(
            DatabaseHelper databaseHelper
    ) {


        this.databaseHelper =
                databaseHelper;
    }


    // ============================================================
    // ADD ELEK RECORD
    // ============================================================

    public long addElekRecord(
            long gardenId,
            String recordDate,
            String[] grades,
            double[] kilograms,
            double[] prices,
            String note
    ) {


        if (gardenId <= 0) {


            return -1;
        }


        recordDate =
                safe(
                        recordDate
                );


        if (recordDate.isEmpty()) {


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


        double totalKg =
                0;


        double estimatedTotal =
                0;


        for (
                int i = 0;
                i < grades.length;
                i++
        ) {


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


            totalKg +=
                    kg;


            estimatedTotal +=
                    kg * price;
        }


        if (totalKg <= 0) {


            return -1;
        }


        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        long elekId =
                -1;


        db.beginTransaction();


        try {


            // ====================================================
            // ANA ELEK KAYDI
            // ====================================================

            ContentValues record =
                    new ContentValues();


            record.put(
                    "garden_id",
                    gardenId
            );


            record.put(
                    "record_date",
                    recordDate
            );


            record.put(
                    "total_kg",
                    totalKg
            );


            record.put(
                    "estimated_total",
                    estimatedTotal
            );


            record.put(
                    "note",
                    safe(
                            note
                    )
            );


            long now = System.currentTimeMillis();
            String ownerUid = databaseHelper.getActiveOwnerUid();
            record.put("created_at", now);
            record.put("updated_at", now);
            record.put("season_year", yearOf(recordDate));
            record.put("owner_uid", ownerUid);
            record.put("cloud_id", ownerUid.isEmpty() ? "" : UUID.randomUUID().toString());


            elekId =
                    db.insert(
                            "elek_records",
                            null,
                            record
                    );


            if (elekId == -1) {


                throw new RuntimeException(
                        "Elek kaydı oluşturulamadı"
                );
            }


            // ====================================================
            // ELEK SATIRLARI
            // ====================================================

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


                ContentValues line =
                        new ContentValues();


                line.put(
                        "elek_id",
                        elekId
                );


                line.put(
                        "grade",
                        grade
                );


                line.put(
                        "kg",
                        kg
                );


                line.put(
                        "price_per_kg",
                        price
                );


                line.put(
                        "line_total",
                        kg * price
                );


                long result =
                        db.insert(
                                "elek_grades",
                                null,
                                line
                        );


                if (result == -1) {


                    throw new RuntimeException(
                            "Elek satırı kaydedilemedi"
                    );
                }
            }


            db.setTransactionSuccessful();


        } catch (Exception e) {


            e.printStackTrace();


            elekId =
                    -1;


        } finally {


            db.endTransaction();
        }


        return elekId;
    }


    // ============================================================
    // GET ALL ELEK RECORDS
    // ============================================================

    public Cursor getElekRecords() {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT " +

                        "e.*," +

                        "g.name AS garden_name " +

                        "FROM elek_records e " +

                        "INNER JOIN gardens g " +
                        "ON g.id = e.garden_id " +

                        "ORDER BY e.created_at DESC",

                null
        );
    }


    // ============================================================
    // GET GARDEN ELEK RECORDS
    // ============================================================

    public Cursor getGardenElekRecords(
            long gardenId
    ) {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT " +

                        "e.*," +

                        "g.name AS garden_name " +

                        "FROM elek_records e " +

                        "INNER JOIN gardens g " +
                        "ON g.id = e.garden_id " +

                        "WHERE e.garden_id = ? " +

                        "ORDER BY e.created_at DESC",

                new String[]{
                        String.valueOf(
                                gardenId
                        )
                }
        );
    }


    // ============================================================
    // GET SINGLE ELEK RECORD
    // ============================================================

    public Cursor getElekRecord(
            long elekId
    ) {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT " +

                        "e.*," +

                        "g.name AS garden_name " +

                        "FROM elek_records e " +

                        "INNER JOIN gardens g " +
                        "ON g.id = e.garden_id " +

                        "WHERE e.id = ?",

                new String[]{
                        String.valueOf(
                                elekId
                        )
                }
        );
    }


    // ============================================================
    // GET ELEK GRADES
    // ============================================================

    public Cursor getElekGrades(
            long elekId
    ) {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        return db.rawQuery(

                "SELECT * " +

                        "FROM elek_grades " +

                        "WHERE elek_id = ? " +

                        "ORDER BY CASE grade " +

                        "WHEN 'DOUBLE' THEN 0 " +

                        "WHEN '1' THEN 1 " +

                        "WHEN '2' THEN 2 " +

                        "WHEN '3' THEN 3 " +

                        "WHEN '4' THEN 4 " +

                        "WHEN 'YAGLIK' THEN 5 " +

                        "ELSE 6 END",

                new String[]{
                        String.valueOf(
                                elekId
                        )
                }
        );
    }


    // ============================================================
    // TOTAL ELEK KG
    // ============================================================

    public double getTotalElekKg() {


        return getDoubleValue(

                "SELECT COALESCE(SUM(total_kg), 0) " +

                        "FROM elek_records"
        );
    }


    // ============================================================
    // TOTAL ELEK ESTIMATED VALUE
    // ============================================================

    public double getTotalElekEstimatedValue() {


        return getDoubleValue(

                "SELECT COALESCE(SUM(estimated_total), 0) " +

                        "FROM elek_records"
        );
    }


    // ============================================================
    // TOTAL KG BY GRADE
    // ============================================================

    public double getTotalElekKgByGrade(
            String grade
    ) {


        grade =
                safe(
                        grade
                );


        if (grade.isEmpty()) {


            return 0;
        }


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT COALESCE(SUM(kg), 0) " +

                                    "FROM elek_grades " +

                                    "WHERE grade = ?",

                            new String[]{
                                    grade
                            }
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
    // TOTAL VALUE BY GRADE
    // ============================================================

    public double getTotalElekValueByGrade(
            String grade
    ) {


        grade =
                safe(
                        grade
                );


        if (grade.isEmpty()) {


            return 0;
        }


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT COALESCE(SUM(line_total), 0) " +

                                    "FROM elek_grades " +

                                    "WHERE grade = ?",

                            new String[]{
                                    grade
                            }
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
    // GARDEN TOTAL ELEK KG
    // ============================================================

    public double getGardenTotalElekKg(
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

                            "SELECT COALESCE(SUM(total_kg), 0) " +

                                    "FROM elek_records " +

                                    "WHERE garden_id = ?",

                            new String[]{
                                    String.valueOf(
                                            gardenId
                                    )
                            }
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
    // ELEK RECORD COUNT
    // ============================================================

    public int getElekRecordCount() {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(

                            "SELECT COUNT(*) " +

                                    "FROM elek_records",

                            null
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
    // UPDATE ELEK RECORD NOTE
    // ============================================================

    public int updateElekRecordNote(
            long elekId,
            String note
    ) {


        if (elekId <= 0) {


            return 0;
        }


        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        ContentValues values =
                new ContentValues();


        values.put(
                "note",
                safe(
                        note
                )
        );

        values.put("updated_at", System.currentTimeMillis());


        return db.update(

                "elek_records",

                values,

                "id = ?",

                new String[]{
                        String.valueOf(
                                elekId
                        )
                }
        );
    }


    // ============================================================
    // DELETE ELEK RECORD
    // ============================================================

    public int deleteElekRecord(
            long elekId
    ) {


        if (elekId <= 0) {


            return 0;
        }


        databaseHelper.recordCloudDeletion("elek_records", elekId);

        SQLiteDatabase db =
                databaseHelper.getWritableDatabase();


        /*
         * elek_grades tablosu
         * ON DELETE CASCADE kullandığı için
         * ana kayıt silinince satırlar da silinir.
         */
        return db.delete(

                "elek_records",

                "id = ?",

                new String[]{
                        String.valueOf(
                                elekId
                        )
                }
        );
    }


    // ============================================================
    // ELEK SEASON ARCHIVE
    // ============================================================

    public Cursor getElekSeasonArchive(int year) {
        return getArchiveSummary(year, databaseHelper.getActiveOwnerUid());
    }

    // Shared by the archive, its season summary and the league. No separate totals.
    public Cursor getArchiveRecords(int year, long gardenId, String ownerUid) {
        return archiveQuery(year, gardenId, ownerUid, true);
    }

    public Cursor getArchiveSummary(int year, String ownerUid) {
        return archiveQuery(year, -1, ownerUid, false);
    }

    private Cursor archiveQuery(int year, long gardenId, String ownerUid, boolean records) {
        String[] grades = {"DOUBLE", "1", "2", "3", "4", "YAGLIK"};
        String[] columns = {"double_kg", "grade1_kg", "grade2_kg", "grade3_kg", "grade4_kg", "oil_kg"};
        StringBuilder sql = new StringBuilder("SELECT COALESCE(SUM(eg.kg),0) AS total_kg, "
                + "COALESCE(SUM(eg.line_total),0) AS total_revenue, COUNT(DISTINCT er.id) AS record_count");
        for (int i = 0; i < grades.length; i++) {
            sql.append(", COALESCE(SUM(CASE WHEN eg.grade='").append(grades[i])
                    .append("' THEN eg.kg ELSE 0 END),0) AS ").append(columns[i]);
        }
        if (records) sql.append(", er.id AS record_id, er.record_date, COALESCE(g.name,'-') AS garden_name, "
                + "COALESCE(g.district,'') AS district, COALESCE(g.province,'') AS province");
        sql.append(" FROM elek_records er INNER JOIN gardens g ON g.id=er.garden_id "
                + "LEFT JOIN elek_grades eg ON eg.elek_id=er.id "
                + "WHERE COALESCE(g.owner_uid,'')=? "
                + "AND (COALESCE(er.owner_uid,'')=? OR COALESCE(er.owner_uid,'')='') "
                + "AND (er.season_year=? OR (COALESCE(er.season_year,0)=0 AND "
                + "(substr(TRIM(er.record_date),1,4)=? OR substr(TRIM(er.record_date),-4)=?))) ");
        java.util.ArrayList<String> args = new java.util.ArrayList<>();
        args.add(safe(ownerUid)); args.add(safe(ownerUid));
        args.add(String.valueOf(year)); args.add(String.valueOf(year)); args.add(String.valueOf(year));
        if (gardenId != -1) { sql.append("AND er.garden_id=? "); args.add(String.valueOf(gardenId)); }
        if (records) sql.append("GROUP BY er.id ORDER BY er.record_date DESC, er.id DESC");
        return databaseHelper.getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]));
    }

    // ============================================================
    // DOUBLE HELPER
    // ============================================================

    private double getDoubleValue(
            String sql
    ) {


        SQLiteDatabase db =
                databaseHelper.getReadableDatabase();


        Cursor cursor =
                null;


        try {


            cursor =
                    db.rawQuery(
                            sql,
                            null
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
        return Calendar.getInstance().get(Calendar.YEAR);
    }
}
