package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Yağhane sıkım defteri: üreticinin hangi yağhanede kaç kg zeytinden kaç litre yağ aldığı.
 *
 * Üreticinin dili: "4 kilodan aldım" = 4 kg çiğ zeytinden 1 litre yağ. Sayı küçükse yağhane iyidir.
 *   kilodan            = zeytin kg ÷ yağ litre
 *   1 ton zeytinden    = 1000 ÷ kilodan litre
 * Yağ kilo olarak yazılırsa litreye çevrilir (1 litre zeytinyağı ≈ 0,916 kg).
 * Kayıtlar yalnız bu telefonda (oil_pressings, CREATE IF NOT EXISTS); owner_uid ile hesap silmede temizlenir, yedeğe girer.
 */
public final class OilPressings {
    static final String TABLE = "oil_pressings";
    public static final double KG_PER_LITRE = 0.916;
    public static final String FEE_NONE = "NONE", FEE_PER_KG = "PER_KG", FEE_SHARE = "SHARE";

    private OilPressings() {}

    public static final class Pressing {
        public long id;
        public String date = "", mill = "", feeType = FEE_NONE, note = "";
        public double oliveKg, oilLitre, feeValue, acid = -1;
        public long createdAt;

        public double kilodan() { return OilPressings.kilodan(oliveKg, oilLitre); }

        /** Sıkım ücreti TL (kg başı ücrette); yağ payında 0. */
        public double feeTl() { return FEE_PER_KG.equals(feeType) ? Math.max(0, feeValue) * oliveKg : 0; }

        /** Yağhanenin yağ payı litre (yağ payında); diğerinde 0. */
        public double shareLitre() { return FEE_SHARE.equals(feeType) ? oilLitre * Math.max(0, Math.min(100, feeValue)) / 100.0 : 0; }
    }

    /** Bir yağhanenin özeti (kendi kayıtlarından). */
    public static final class Mill {
        public String name = "";
        public int count;
        public double oliveKg, oilLitre;
        public double kilodan() { return OilPressings.kilodan(oliveKg, oilLitre); }
        public double perTon() { return perTon(kilodan()); }
        static double perTon(double k) { return k > 0 ? 1000.0 / k : 0; }
    }

    public static final class Season {
        public int count;
        public double oliveKg, oilLitre, feeTl;
        public double kilodan() { return OilPressings.kilodan(oliveKg, oilLitre); }
    }

    // ------------------------------------------------------------------ hesap

    public static double kilodan(double oliveKg, double oilLitre) {
        return oliveKg > 0 && oilLitre > 0 ? oliveKg / oilLitre : 0;
    }

    /** 1 ton zeytinden kaç litre. */
    public static double perTon(double kilodan) { return kilodan > 0 ? 1000.0 / kilodan : 0; }

    public static double litreFromKg(double kg) { return kg > 0 ? kg / KG_PER_LITRE : 0; }

    /** "4,2 kilodan" */
    public static String kilodanText(double k) {
        if (k <= 0) return "—";
        return String.format(new Locale("tr", "TR"), "%.1f", k) + " kilodan";
    }

    public static String litre(double v) {
        if (Math.abs(v - Math.round(v)) < 0.05) return String.format(new Locale("tr", "TR"), "%,d", Math.round(v)) + " lt";
        return String.format(new Locale("tr", "TR"), "%,.1f", v) + " lt";
    }

    /** Girdi hatası (boşsa geçerli). */
    public static String error(double oliveKg, double oilLitre, String mill) {
        if (mill == null || mill.trim().isEmpty()) return "Yağhane adını yaz.";
        if (oliveKg <= 0) return "Zeytin kilosunu yaz.";
        if (oilLitre <= 0) return "Çıkan yağı yaz.";
        double k = kilodan(oliveKg, oilLitre);
        if (k < 2 || k > 15) return "Bu sonuç olağan dışı (" + kilodanText(k) + "). Kiloyu ve litreyi kontrol et.";
        return "";
    }

    /** Yağhaneler: en iyiden (en küçük "kilodan") kötüye; ad Türkçe harf/büyük-küçük farkı gözetmeden birleşir. */
    public static List<Mill> mills(List<Pressing> list) {
        Map<String, Mill> map = new LinkedHashMap<>();
        for (Pressing p : list) {
            String key = TezgahSearch.fold(p.mill);
            if (key.isEmpty()) continue;
            Mill m = map.get(key);
            if (m == null) { m = new Mill(); m.name = p.mill.trim(); map.put(key, m); }
            m.count++;
            m.oliveKg += p.oliveKg;
            m.oilLitre += p.oilLitre;
        }
        List<Mill> out = new ArrayList<>(map.values());
        out.sort((a, b) -> Double.compare(a.kilodan(), b.kilodan()));
        return out;
    }

    /** En iyi yağhanenin ötekine göre 1 ton zeytinde kaç litre fazla verdiği. */
    public static double extraPerTon(Mill best, Mill other) {
        return Math.max(0, best.perTon() - other.perTon());
    }

    public static Season season(List<Pressing> list) {
        Season s = new Season();
        for (Pressing p : list) {
            s.count++;
            s.oliveKg += p.oliveKg;
            s.oilLitre += p.oilLitre;
            s.feeTl += p.feeTl();
        }
        return s;
    }

    /** Son kullanılan yağhane adları (tekrarsız, yeniden eskiye). */
    public static List<String> recentMills(List<Pressing> newestFirst, int limit) {
        List<String> out = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (Pressing p : newestFirst) {
            String k = TezgahSearch.fold(p.mill);
            if (k.isEmpty() || keys.contains(k)) continue;
            keys.add(k);
            out.add(p.mill.trim());
            if (out.size() >= limit) break;
        }
        return out;
    }

    // ------------------------------------------------------------------ kayıt

    static void ensure(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + " (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "owner_uid TEXT DEFAULT '', press_date TEXT NOT NULL, mill_name TEXT NOT NULL, "
                + "olive_kg REAL DEFAULT 0, oil_litre REAL DEFAULT 0, fee_type TEXT DEFAULT 'NONE', "
                + "fee_value REAL DEFAULT 0, acid REAL DEFAULT -1, note TEXT DEFAULT '', created_at INTEGER DEFAULT 0)");
    }

    /** Yeni kayıt (id <= 0) ya da düzenleme. Dönüş id, hata -1. */
    public static long save(SQLiteDatabase db, String owner, Pressing p, long now) {
        ensure(db);
        if (p.oliveKg <= 0 || p.oilLitre <= 0 || p.mill == null || p.mill.trim().isEmpty()) return -1;
        ContentValues v = new ContentValues();
        v.put("press_date", p.date == null ? "" : p.date);
        v.put("mill_name", p.mill.trim());
        v.put("olive_kg", Math.round(p.oliveKg * 10) / 10.0);
        v.put("oil_litre", Math.round(p.oilLitre * 10) / 10.0);
        v.put("fee_type", p.feeType == null ? FEE_NONE : p.feeType);
        v.put("fee_value", Math.max(0, p.feeValue));
        v.put("acid", p.acid);
        v.put("note", p.note == null ? "" : p.note.trim());
        String o = owner == null ? "" : owner;
        if (p.id > 0) {
            return db.update(TABLE, v, "id=? AND COALESCE(owner_uid,'')=?", new String[]{String.valueOf(p.id), o}) > 0 ? p.id : -1;
        }
        v.put("owner_uid", o);
        v.put("created_at", now);
        return db.insert(TABLE, null, v);
    }

    public static boolean delete(SQLiteDatabase db, String owner, long id) {
        ensure(db);
        return db.delete(TABLE, "id=? AND COALESCE(owner_uid,'')=?", new String[]{String.valueOf(id), owner == null ? "" : owner}) > 0;
    }

    /** Bir yılın kayıtları (yeniden eskiye). year <= 0: hepsi. */
    public static List<Pressing> list(SQLiteDatabase db, String owner, int year) {
        ensure(db);
        String where = "COALESCE(owner_uid,'')=?" + (year > 0 ? " AND substr(press_date,1,4)=?" : "");
        String[] args = year > 0 ? new String[]{owner == null ? "" : owner, String.valueOf(year)} : new String[]{owner == null ? "" : owner};
        List<Pressing> out = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT id, press_date, mill_name, olive_kg, oil_litre, fee_type, fee_value, acid, note, created_at FROM "
                + TABLE + " WHERE " + where + " ORDER BY press_date DESC, created_at DESC, id DESC", args);
        try {
            while (c.moveToNext()) {
                Pressing p = new Pressing();
                p.id = c.getLong(0);
                p.date = nz(c.getString(1));
                p.mill = nz(c.getString(2));
                p.oliveKg = c.getDouble(3);
                p.oilLitre = c.getDouble(4);
                p.feeType = nz(c.getString(5));
                p.feeValue = c.getDouble(6);
                p.acid = c.isNull(7) ? -1 : c.getDouble(7);
                p.note = nz(c.getString(8));
                p.createdAt = c.getLong(9);
                out.add(p);
            }
        } finally {
            c.close();
        }
        return out;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
