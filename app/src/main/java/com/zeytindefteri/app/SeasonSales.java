package com.zeytindefteri.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Sezonun TEK satış toplamı: Tanecik ve asistan aynı rakamı söylesin, Elek kartıyla tutsun.
 *
 * Toplam = elek kayıtları (Elek kartındaki sorgunun aynısı, brüt) + elekten gelmeyen, elle girilmiş satışlar.
 * Elekten oluşmuş satış (source_elek_id ya da source_elek_cloud_id dolu) iki kez sayılmaz.
 * Alıcı seçilmemiş elek kaydı da sayılır (Alıcılarım'dan toplamak bunları kaçırıyordu).
 */
public final class SeasonSales {

    private SeasonSales() { }

    public static final class Totals {
        public double kg, tl;
        public double elekKg, elekTl;
        public double manualKg, manualTl;
    }

    /** Sezon toplamı (bütün bahçeler). Okunamazsa sıfırlar. */
    public static Totals season(ElekRepository elek, SQLiteDatabase rdb, String owner, int season) {
        Totals t = new Totals();
        String own = owner == null ? "" : owner;
        Cursor c = null;
        try {
            c = elek.getArchiveSummary(season, own);
            if (c != null && c.moveToFirst()) {
                t.elekKg = c.getDouble(c.getColumnIndexOrThrow("total_kg"));
                t.elekTl = c.getDouble(c.getColumnIndexOrThrow("total_revenue"));
            }
        } finally {
            if (c != null) c.close();
        }
        Cursor s = rdb.rawQuery("SELECT COALESCE(SUM(s.total_kg),0), COALESCE(SUM(s.gross_total),0) FROM sales s "
                + "WHERE COALESCE(s.owner_uid,'')=? AND s.season_year=? AND COALESCE(s.sale_kind,'SALE')='SALE' "
                + "AND COALESCE(s.source_elek_id,0)=0 AND COALESCE(s.source_elek_cloud_id,'')=''",
                new String[]{own, String.valueOf(season)});
        try {
            if (s.moveToFirst()) {
                t.manualKg = s.getDouble(0);
                t.manualTl = s.getDouble(1);
            }
        } finally {
            s.close();
        }
        combine(t);
        return t;
    }

    static void combine(Totals t) {
        t.kg = t.elekKg + t.manualKg;
        t.tl = t.elekTl + t.manualTl;
    }

    /** Elekten oluşmuş satış mı (iki kez sayılmasın)? */
    public static boolean fromElek(long sourceElekId, String sourceElekCloudId) {
        return sourceElekId > 0 || (sourceElekCloudId != null && !sourceElekCloudId.trim().isEmpty());
    }
}
