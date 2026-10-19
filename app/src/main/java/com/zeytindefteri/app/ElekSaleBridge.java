package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

/**
 * Bir elek kaydından, aynı sınıf/kilo/fiyatlarla gerçek satış oluşturur.
 * - Satış, mevcut SaleRepository.addSaleWithGrades ile yazılır (alacak kaydı dahil aynı kurallar).
 * - Piyasa paylaşımı KAPALI başlar; tahmini elek fiyatları kendiliğinden piyasa verisine karışmaz.
 * - Bir elek yalnızca bir kez dönüştürülür (yerel id + bulut id ile kontrol). Satış silinirse tekrar dönüştürülebilir.
 */
public final class ElekSaleBridge {
    public static final long ALREADY_CONVERTED = -2;

    public static final class Pending {
        public long elekId;
        public String garden = "", date = "";
        public double kg, total;
    }

    private final DatabaseHelper databaseHelper;

    public ElekSaleBridge(DatabaseHelper databaseHelper) { this.databaseHelper = databaseHelper; }

    private String owner() { String o = databaseHelper.getActiveOwnerUid(); return o == null ? "" : o; }

    /** Bu eleğe bağlı bir satış var mı (yerel id ya da bulut id ile). */
    public boolean hasSale(long elekId) {
        return linkedSaleId(databaseHelper.getReadableDatabase(), elekId) > 0;
    }

    private long linkedSaleId(SQLiteDatabase db, long elekId) {
        if (elekId <= 0) return -1;
        String cloud = "";
        Cursor e = db.rawQuery("SELECT COALESCE(cloud_id,'') FROM elek_records WHERE id=?", new String[]{String.valueOf(elekId)});
        try { if (e.moveToFirst()) cloud = e.getString(0); } finally { e.close(); }
        Cursor c = db.rawQuery("SELECT id FROM sales WHERE COALESCE(owner_uid,'')=? AND (source_elek_id=?"
                        + (cloud.isEmpty() ? "" : " OR source_elek_cloud_id=?") + ") LIMIT 1",
                cloud.isEmpty() ? new String[]{owner(), String.valueOf(elekId)}
                        : new String[]{owner(), String.valueOf(elekId), cloud});
        try { return c.moveToFirst() ? c.getLong(0) : -1; } finally { c.close(); }
    }

    /** Bu eleğe bağlı satışın id'si; yoksa -1. */
    public long linkedSaleId(long elekId) {
        return linkedSaleId(databaseHelper.getReadableDatabase(), elekId);
    }

    /** Bu eleğe bağlı satışın alıcısı; satış ya da alıcı yoksa null. */
    public BuyerDirectory.Summary linkedBuyer(long elekId) {
        long saleId = linkedSaleId(elekId);
        return saleId > 0 ? BuyerDirectory.buyerForSale(databaseHelper.getReadableDatabase(), saleId) : null;
    }

    /** Bağlı satışın brüt tutarı ve alınan tutarı: {gross, received}; satış yoksa null. */
    public double[] linkedSaleTotals(long elekId) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        long saleId = linkedSaleId(db, elekId);
        if (saleId <= 0) return null;
        Cursor c = db.rawQuery("SELECT gross_total, amount_received FROM sales WHERE id=?", new String[]{String.valueOf(saleId)});
        try { return c.moveToFirst() ? new double[]{c.getDouble(0), c.getDouble(1)} : null; } finally { c.close(); }
    }

    /**
     * Satışı oluşturur. Dönüş: satış id, zaten dönüştürülmüşse {@link #ALREADY_CONVERTED}, hata -1.
     * Çağıran, başarılı sonuçta ProductionCloudRepository.syncSaleByLocalId ile senkronu tetikler.
     */
    public long createSale(long elekId, int pay, double partial, String buyerName) {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        String owner = owner();
        db.beginTransaction();
        try {
            if (linkedSaleId(db, elekId) > 0) return ALREADY_CONVERTED;
            long gardenId; String date, cloud, district, province, variety;
            Cursor r = db.rawQuery("SELECT er.garden_id, er.record_date, COALESCE(er.cloud_id,''), "
                            + "COALESCE(g.district,''), COALESCE(g.province,''), COALESCE(g.variety,'') "
                            + "FROM elek_records er INNER JOIN gardens g ON g.id=er.garden_id "
                            + "WHERE er.id=? AND COALESCE(g.owner_uid,'')=? "
                            + "AND (COALESCE(er.owner_uid,'')=? OR COALESCE(er.owner_uid,'')='')",
                    new String[]{String.valueOf(elekId), owner, owner});
            try {
                if (!r.moveToFirst()) return -1;
                gardenId = r.getLong(0); date = r.getString(1); cloud = r.getString(2);
                district = r.getString(3); province = r.getString(4); variety = r.getString(5);
            } finally { r.close(); }

            List<String> grades = new ArrayList<>(); List<Double> kgs = new ArrayList<>(); List<Double> prices = new ArrayList<>();
            double gross = 0;
            Cursor g = db.rawQuery("SELECT grade, kg, price_per_kg FROM elek_grades WHERE elek_id=? AND kg>0 ORDER BY id",
                    new String[]{String.valueOf(elekId)});
            try {
                while (g.moveToNext()) {
                    grades.add(g.getString(0)); kgs.add(g.getDouble(1)); prices.add(g.getDouble(2));
                    gross += g.getDouble(1) * g.getDouble(2);
                }
            } finally { g.close(); }
            if (grades.isEmpty()) return -1;
            if (!ElekSaleModel.partialError(pay, gross, partial).isEmpty()) return -1;

            Long buyerId = null;
            String buyer = buyerName == null ? "" : buyerName.trim();
            String region = ElekSaleModel.region(district, province);
            if (!buyer.isEmpty()) {
                // Aynı adlı alıcı rehberde varsa o kullanılır; her satışta yeni kopya açılmaz.
                long found = BuyerDirectory.findOrCreate(db, owner, buyer, "", region, "Elek kaydından oluşturuldu");
                if (found <= 0) return -1;
                buyerId = found;
            }
            String[] gradeArr = grades.toArray(new String[0]);
            double[] kgArr = new double[kgs.size()], priceArr = new double[prices.size()];
            for (int i = 0; i < kgArr.length; i++) { kgArr[i] = kgs.get(i); priceArr[i] = prices.get(i); }

            long saleId = new SaleRepository(databaseHelper).addSaleWithGrades(
                    (int) gardenId, buyerId, ElekSaleModel.saleDate(date), "SALE", region, variety, "SOFRALIK",
                    0, 0, ElekSaleModel.received(pay, gross, partial), "", "Elek kaydından oluşturuldu",
                    false, gradeArr, kgArr, priceArr);
            if (saleId <= 0) return -1;
            ContentValues link = new ContentValues();
            link.put("source_elek_id", elekId);
            link.put("source_elek_cloud_id", cloud);
            if (db.update("sales", link, "id=?", new String[]{String.valueOf(saleId)}) <= 0) return -1;
            db.setTransactionSuccessful();
            return saleId;
        } catch (RuntimeException e) {
            return -1;
        } finally {
            db.endTransaction();
        }
    }

    /** Sezonun satışa dönüştürülmemiş elek kayıtları (yeniden eskiye). */
    public List<Pending> pending(int year) {
        List<Pending> out = new ArrayList<>();
        Cursor c = null;
        try {
            c = new ElekRepository(databaseHelper).getArchiveRecords(year, -1, owner());
            while (c != null && c.moveToNext()) {
                long id = c.getLong(c.getColumnIndexOrThrow("record_id"));
                if (hasSale(id)) continue;
                Pending p = new Pending();
                p.elekId = id;
                p.garden = c.getString(c.getColumnIndexOrThrow("garden_name"));
                p.date = c.getString(c.getColumnIndexOrThrow("record_date"));
                p.kg = c.getDouble(c.getColumnIndexOrThrow("total_kg"));
                p.total = c.getDouble(c.getColumnIndexOrThrow("total_revenue"));
                if (p.kg > 0) out.add(p);
            }
        } finally {
            if (c != null) c.close();
        }
        return out;
    }
}
