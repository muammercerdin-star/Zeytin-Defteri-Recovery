package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Alıcı rehberi ("Alıcılarım"). Alıcılar mevcut buyers tablosunda, sahibine (owner_uid) göre tutulur.
 * - Aynı ad (büyük/küçük harf, fazla boşluk ve Türkçe harf farkı gözetmeden) tek alıcıdır: findOrCreate.
 * - Liste ve karne yalnızca bu kullanıcının kendi satışlarından hesaplanır; hiçbir şey paylaşılmaz.
 * - Ad karşılaştırması (normalize) Android'e bağlı değildir; host testleriyle doğrulanır.
 */
public final class BuyerDirectory {
    private BuyerDirectory() {}

    static final Locale TR = new Locale("tr", "TR");
    /** Satış ve elek sınıf anahtarları, makbuzdaki sırayla. */
    static final String[] GRADES = {"DOUBLE", "1", "2", "3", "4", "YAGLIK"};
    static final String[] GRADE_LABELS = {"Double", "1 Numara", "2 Numara", "3 Numara", "4 Numara", "Yağlık"};

    public static final class Summary {
        public long id;
        public String name = "", phone = "", region = "";
        public int saleCount;
        public double kg, gross, receivable;
        public String lastSaleDate = "";
    }

    public static final class SaleLine {
        public long saleId;
        public String date = "", garden = "";
        public double kg, gross, open;
        /** Elekten oluşmuş ama elek kaydı sonradan silinmiş satış. */
        public boolean elekDeleted;
    }

    /** Karşılaştırma satırı: bu alıcının ve diğer alıcıların kg ağırlıklı ortalama fiyatı (yoksa 0). */
    public static final class PriceRow {
        public String label = "";
        public double buyerAvg, othersAvg;
    }

    /** Ad karşılaştırma anahtarı: kenar boşlukları atılır, iç boşluklar teke iner, Türkçe küçük harf. */
    public static String normalize(String name) {
        if (name == null) return "";
        String t = name.trim().replaceAll("\\s+", " ");
        return t.toLowerCase(TR);
    }

    /** Görünen ad: fazla boşluklar temizlenir, yazılan büyük/küçük harf korunur. */
    public static String clean(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }

    private static String owner(String owner) { return owner == null ? "" : owner; }

    /** Aynı adlı alıcının id'si, yoksa -1. */
    public static long find(SQLiteDatabase db, String owner, String name) {
        String key = normalize(name);
        if (key.isEmpty()) return -1;
        Cursor c = db.rawQuery("SELECT id, name FROM buyers WHERE COALESCE(owner_uid,'')=? ORDER BY id",
                new String[]{owner(owner)});
        try {
            while (c.moveToNext()) if (key.equals(normalize(c.getString(1)))) return c.getLong(0);
        } finally { c.close(); }
        return -1;
    }

    /**
     * Var olan alıcıyı döndürür; yoksa oluşturur. Var olanın boş telefon/bölge alanları verilenle doldurulur,
     * dolu alanların üzerine yazılmaz. Ad boşsa -1.
     */
    public static long findOrCreate(SQLiteDatabase db, String owner, String name, String phone, String region, String note) {
        String display = clean(name);
        if (display.isEmpty()) return -1;
        long id = find(db, owner, display);
        String p = clean(phone), r = clean(region);
        if (id > 0) {
            if (!p.isEmpty()) db.execSQL("UPDATE buyers SET phone=? WHERE id=? AND COALESCE(phone,'')=''",
                    new Object[]{p, id});
            if (!r.isEmpty()) db.execSQL("UPDATE buyers SET region=? WHERE id=? AND COALESCE(region,'')=''",
                    new Object[]{r, id});
            return id;
        }
        ContentValues v = new ContentValues();
        v.put("name", display);
        v.put("phone", p);
        v.put("region", r);
        v.put("note", note == null ? "" : note);
        v.put("created_at", System.currentTimeMillis());
        v.put("owner_uid", owner(owner));
        return db.insert("buyers", null, v);
    }

    /** Alıcı bilgilerini günceller (ad değişirse aynı adlı başka alıcı varsa reddedilir). */
    public static boolean update(SQLiteDatabase db, String owner, long id, String name, String phone, String region) {
        String display = clean(name);
        if (id <= 0 || display.isEmpty()) return false;
        long same = find(db, owner, display);
        if (same > 0 && same != id) return false;
        ContentValues v = new ContentValues();
        v.put("name", display);
        v.put("phone", clean(phone));
        v.put("region", clean(region));
        return db.update("buyers", v, "id=? AND COALESCE(owner_uid,'')=?",
                new String[]{String.valueOf(id), owner(owner)}) > 0;
    }

    /**
     * Daha önce elle yazılmış aynı adlı alıcıları tek kayıtta birleştirir (en eski id kalır).
     * Satışlar ve açık alacakların karşı taraf adı korunur; yalnızca buyer_id bağlantısı toplanır.
     * Dönüş: silinen kopya sayısı.
     */
    public static int mergeDuplicates(SQLiteDatabase db, String owner) {
        Map<String, Long> keep = new HashMap<>();
        List<long[]> moves = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT id, name FROM buyers WHERE COALESCE(owner_uid,'')=? ORDER BY id",
                new String[]{owner(owner)});
        try {
            while (c.moveToNext()) {
                String key = normalize(c.getString(1));
                if (key.isEmpty()) continue;
                Long first = keep.get(key);
                if (first == null) keep.put(key, c.getLong(0));
                else moves.add(new long[]{c.getLong(0), first});
            }
        } finally { c.close(); }
        if (moves.isEmpty()) return 0;
        int removed = 0;
        db.beginTransaction();
        try {
            for (long[] m : moves) {
                db.execSQL("UPDATE sales SET buyer_id=? WHERE buyer_id=? AND COALESCE(owner_uid,'')=?",
                        new Object[]{m[1], m[0], owner(owner)});
                db.execSQL("UPDATE buyers SET phone=(SELECT phone FROM buyers WHERE id=?) "
                        + "WHERE id=? AND COALESCE(phone,'')=''", new Object[]{m[0], m[1]});
                db.execSQL("UPDATE buyers SET region=(SELECT region FROM buyers WHERE id=?) "
                        + "WHERE id=? AND COALESCE(region,'')=''", new Object[]{m[0], m[1]});
                removed += db.delete("buyers", "id=? AND NOT EXISTS (SELECT 1 FROM sales WHERE buyer_id=?)",
                        new String[]{String.valueOf(m[0]), String.valueOf(m[0])});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return removed;
    }

    /** Bir satışın alıcı adı; alıcısız satışta boş metin. */
    public static String nameForSale(SQLiteDatabase db, long saleId) {
        if (saleId <= 0) return "";
        Cursor c = db.rawQuery("SELECT COALESCE(b.name,'') FROM sales s LEFT JOIN buyers b ON b.id=s.buyer_id WHERE s.id=?",
                new String[]{String.valueOf(saleId)});
        try { return c.moveToFirst() ? c.getString(0) : ""; } finally { c.close(); }
    }

    /** Bir satışın alıcısı; alıcısız satışta null. */
    public static Summary buyerForSale(SQLiteDatabase db, long saleId) {
        if (saleId <= 0) return null;
        Cursor c = db.rawQuery("SELECT b.id, b.name, COALESCE(b.phone,''), COALESCE(b.region,'') "
                + "FROM sales s INNER JOIN buyers b ON b.id=s.buyer_id WHERE s.id=?", new String[]{String.valueOf(saleId)});
        try {
            if (!c.moveToFirst()) return null;
            Summary s = new Summary();
            s.id = c.getLong(0); s.name = c.getString(1); s.phone = c.getString(2); s.region = c.getString(3);
            return s;
        } finally { c.close(); }
    }

    /** sale_date gg.aa.yyyy ya da yyyy-aa-gg olabilir; sıralama ve gösterim için yyyy-aa-gg'ye çevrilir. */
    static final String ISO_DATE =
            "(CASE WHEN substr(s.sale_date,5,1)='-' THEN substr(s.sale_date,1,10) "
                    + "WHEN length(s.sale_date)>=10 THEN substr(s.sale_date,7,4)||'-'||substr(s.sale_date,4,2)||'-'||substr(s.sale_date,1,2) "
                    + "ELSE COALESCE(s.sale_date,'') END)";

    private static final String OPEN_SUM =
            "COALESCE((SELECT SUM(MAX(0, r.amount - r.total_received)) FROM receivables r "
                    + "WHERE r.related_sale_id = s.id AND COALESCE(r.status,'OPEN') <> 'CLOSED' "
                    + "AND COALESCE(r.owner_uid,'') = s.owner_uid), 0)";

    /**
     * Rehberdeki bütün alıcılar: bu sezonun satış sayısı, kilosu, brüt tutarı ve kalan alacağı ile.
     * Sıra: bu sezon satış tutarı yüksekten düşüğe, sonra son satış tarihi, sonra ad.
     */
    public static List<Summary> list(SQLiteDatabase db, String owner, int season) {
        Map<Long, Summary> out = new LinkedHashMap<>();
        Cursor b = db.rawQuery("SELECT id, name, COALESCE(phone,''), COALESCE(region,'') FROM buyers "
                + "WHERE COALESCE(owner_uid,'')=?", new String[]{owner(owner)});
        try {
            while (b.moveToNext()) {
                Summary s = new Summary();
                s.id = b.getLong(0); s.name = b.getString(1); s.phone = b.getString(2); s.region = b.getString(3);
                out.put(s.id, s);
            }
        } finally { b.close(); }
        Cursor c = db.rawQuery("SELECT s.buyer_id, MAX(" + ISO_DATE + "), "
                + "SUM(CASE WHEN s.season_year=? THEN 1 ELSE 0 END), "
                + "SUM(CASE WHEN s.season_year=? THEN s.total_kg ELSE 0 END), "
                + "SUM(CASE WHEN s.season_year=? THEN s.gross_total ELSE 0 END), "
                + "SUM(" + OPEN_SUM + ") "
                + "FROM sales s WHERE s.buyer_id IS NOT NULL AND COALESCE(s.owner_uid,'')=? "
                + "AND COALESCE(s.sale_kind,'SALE')='SALE' GROUP BY s.buyer_id",
                new String[]{String.valueOf(season), String.valueOf(season), String.valueOf(season), owner(owner)});
        try {
            while (c.moveToNext()) {
                Summary s = out.get(c.getLong(0));
                if (s == null) continue;
                s.lastSaleDate = c.isNull(1) ? "" : c.getString(1);
                s.saleCount = c.getInt(2);
                s.kg = c.getDouble(3);
                s.gross = c.getDouble(4);
                s.receivable = c.getDouble(5);
            }
        } finally { c.close(); }
        List<Summary> list = new ArrayList<>(out.values());
        list.sort((x, y) -> {
            int g = Double.compare(y.gross, x.gross);
            if (g != 0) return g;
            int d = y.lastSaleDate.compareTo(x.lastSaleDate);
            if (d != 0) return d;
            return x.name.compareToIgnoreCase(y.name);
        });
        return list;
    }

    /** Seçim penceresi sırası: en son satış yapılan önce (sezon gözetmeden), satışı olmayanlar adına göre. */
    public static List<Summary> recentFirst(SQLiteDatabase db, String owner, int season) {
        List<Summary> list = list(db, owner, season);
        list.sort((x, y) -> {
            int d = y.lastSaleDate.compareTo(x.lastSaleDate);
            if (d != 0) return d;
            return x.name.compareToIgnoreCase(y.name);
        });
        return list;
    }

    /** Aramaya uyan alıcılar: ad ya da bölgede geçen. */
    public static List<Summary> filter(List<Summary> all, String query) {
        String q = normalize(query);
        if (q.isEmpty()) return new ArrayList<>(all);
        List<Summary> out = new ArrayList<>();
        for (Summary s : all) {
            if (normalize(s.name).contains(q) || normalize(s.region).contains(q)) out.add(s);
        }
        return out;
    }

    /** Bu alıcının sezon satışları, yeniden eskiye. */
    public static List<SaleLine> sales(SQLiteDatabase db, String owner, long buyerId, int season) {
        List<SaleLine> out = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT s.id, " + ISO_DATE + ", COALESCE(g.name,''), s.total_kg, s.gross_total, " + OPEN_SUM + ", "
                + "(COALESCE(s.source_elek_id,0) > 0 AND NOT EXISTS (SELECT 1 FROM elek_records e WHERE e.id=s.source_elek_id "
                + "OR (COALESCE(s.source_elek_cloud_id,'')<>'' AND e.cloud_id=s.source_elek_cloud_id))) "
                + "FROM sales s LEFT JOIN gardens g ON g.id=s.garden_id "
                + "WHERE s.buyer_id=? AND COALESCE(s.owner_uid,'')=? AND s.season_year=? "
                + "AND COALESCE(s.sale_kind,'SALE')='SALE' ORDER BY " + ISO_DATE + " DESC, s.id DESC",
                new String[]{String.valueOf(buyerId), owner(owner), String.valueOf(season)});
        try {
            while (c.moveToNext()) {
                SaleLine l = new SaleLine();
                l.saleId = c.getLong(0); l.date = c.getString(1); l.garden = c.getString(2);
                l.kg = c.getDouble(3); l.gross = c.getDouble(4); l.open = c.getDouble(5);
                l.elekDeleted = c.getInt(6) == 1;
                out.add(l);
            }
        } finally { c.close(); }
        return out;
    }

    /** Elek sınıfına göre bu alıcının ve diğer alıcıların bu sezon kg ağırlıklı ortalama fiyatı. */
    public static List<PriceRow> priceComparison(SQLiteDatabase db, String owner, long buyerId, int season) {
        double[] bKg = new double[GRADES.length], bTl = new double[GRADES.length];
        double[] oKg = new double[GRADES.length], oTl = new double[GRADES.length];
        Cursor c = db.rawQuery("SELECT s.buyer_id, sg.grade, SUM(sg.kg), SUM(sg.kg * sg.price_per_kg) "
                + "FROM sale_grades sg INNER JOIN sales s ON s.id=sg.sale_id "
                + "WHERE COALESCE(s.owner_uid,'')=? AND s.season_year=? AND s.buyer_id IS NOT NULL "
                + "AND COALESCE(s.sale_kind,'SALE')='SALE' AND sg.kg>0 AND sg.price_per_kg>0 "
                + "GROUP BY s.buyer_id, sg.grade", new String[]{owner(owner), String.valueOf(season)});
        try {
            while (c.moveToNext()) {
                int i = gradeIndex(c.getString(1));
                if (i < 0) continue;
                if (c.getLong(0) == buyerId) { bKg[i] += c.getDouble(2); bTl[i] += c.getDouble(3); }
                else { oKg[i] += c.getDouble(2); oTl[i] += c.getDouble(3); }
            }
        } finally { c.close(); }
        List<PriceRow> rows = new ArrayList<>();
        for (int i = 0; i < GRADES.length; i++) {
            if (bKg[i] <= 0 && oKg[i] <= 0) continue;
            PriceRow r = new PriceRow();
            r.label = GRADE_LABELS[i];
            r.buyerAvg = bKg[i] > 0 ? bTl[i] / bKg[i] : 0;
            r.othersAvg = oKg[i] > 0 ? oTl[i] / oKg[i] : 0;
            rows.add(r);
        }
        return rows;
    }

    /**
     * Elek performansı: bu alıcıya verilen zeytinin sınıflara dağılımı (kg) ve kg başı ortalama.
     * Halk arasında "kimin eleği iri çıkarıyor" diye konuşulan şeyin kullanıcının kendi kayıtlarından hesabı.
     */
    public static final class ElekStats {
        public final double[] kg = new double[6];
        public double totalKg, totalTl;

        public double share(int i) { return totalKg > 0 ? kg[i] * 100.0 / totalKg : 0; }
        /** İri: Double + 1 Numara yüzdesi. */
        public double topShare() { return totalKg > 0 ? (kg[0] + kg[1]) * 100.0 / totalKg : 0; }
        /** Ufak: 4 Numara + Yağlık yüzdesi. */
        public double lowShare() { return totalKg > 0 ? (kg[4] + kg[5]) * 100.0 / totalKg : 0; }
        public double avgPerKg() { return totalKg > 0 ? totalTl / totalKg : 0; }
        void add(ElekStats o) {
            for (int i = 0; i < kg.length; i++) kg[i] += o.kg[i];
            totalKg += o.totalKg; totalTl += o.totalTl;
        }
    }

    /** Karşılaştırma için en az bu kadar kilo gerekir; azında fark tesadüf olabilir. */
    public static final double MIN_COMPARE_KG = 50;

    /** Sezonda her alıcının elek dağılımı (alıcı id → istatistik). */
    public static Map<Long, ElekStats> elekStats(SQLiteDatabase db, String owner, int season) {
        Map<Long, ElekStats> out = new HashMap<>();
        Cursor c = db.rawQuery("SELECT s.buyer_id, sg.grade, SUM(sg.kg), SUM(sg.kg * sg.price_per_kg) "
                + "FROM sale_grades sg INNER JOIN sales s ON s.id=sg.sale_id "
                + "WHERE COALESCE(s.owner_uid,'')=? AND s.season_year=? AND s.buyer_id IS NOT NULL "
                + "AND COALESCE(s.sale_kind,'SALE')='SALE' AND sg.kg>0 "
                + "GROUP BY s.buyer_id, sg.grade", new String[]{owner(owner), String.valueOf(season)});
        try {
            while (c.moveToNext()) {
                int i = gradeIndex(c.getString(1));
                if (i < 0) continue;
                ElekStats st = out.get(c.getLong(0));
                if (st == null) { st = new ElekStats(); out.put(c.getLong(0), st); }
                st.kg[i] += c.getDouble(2);
                st.totalKg += c.getDouble(2);
                st.totalTl += Math.max(0, c.getDouble(3));
            }
        } finally { c.close(); }
        return out;
    }

    /** Bu alıcı dışındaki bütün alıcıların toplam dağılımı. */
    public static ElekStats others(Map<Long, ElekStats> all, long buyerId) {
        ElekStats o = new ElekStats();
        for (Map.Entry<Long, ElekStats> e : all.entrySet()) if (e.getKey() != buyerId) o.add(e.getValue());
        return o;
    }

    /** Listede alıcının altındaki kısa satır: "İri %25 · Ufak %22 · 61 TL/kg". */
    public static String elekLine(ElekStats s) {
        if (s == null || s.totalKg <= 0) return "";
        return "İri %" + pct(s.topShare()) + " · Ufak %" + pct(s.lowShare()) + " · " + oneDecimal(s.avgPerKg()) + " TL/kg";
    }

    /**
     * Diğer alıcılarla karşılaştırma cümlesi; veri azsa boş.
     * Fark 3 puandan küçükse "benzer" sayılır.
     */
    public static String elekVerdict(ElekStats buyer, ElekStats others) {
        if (buyer == null || others == null || buyer.totalKg < MIN_COMPARE_KG || others.totalKg < MIN_COMPARE_KG) return "";
        double diff = buyer.topShare() - others.topShare();
        long points = Math.round(Math.abs(diff));
        if (points < 3) return "Double + 1 Numara oranı diğer alıcılarınla benzer.";
        return "Double + 1 Numara oranı diğer alıcılarından " + points + " puan " + (diff > 0 ? "yüksek." : "düşük.");
    }

    static String pct(double v) { return String.valueOf(Math.round(Math.max(0, v))); }

    static String oneDecimal(double v) {
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(new Locale("tr", "TR"));
        nf.setMaximumFractionDigits(1);
        nf.setMinimumFractionDigits(0);
        return nf.format(Math.max(0, v));
    }

    private static final String[] MONTHS = {"Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
            "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"};

    /** "2026-10-19" → "19 Ekim"; okunamayan tarih olduğu gibi döner. */
    public static String shortDate(String iso) {
        if (iso == null) return "";
        String t = iso.trim();
        if (t.length() < 10 || t.charAt(4) != '-' || t.charAt(7) != '-') return t;
        try {
            int m = Integer.parseInt(t.substring(5, 7));
            int d = Integer.parseInt(t.substring(8, 10));
            if (m < 1 || m > 12 || d < 1 || d > 31) return t;
            return d + " " + MONTHS[m - 1];
        } catch (NumberFormatException e) {
            return t;
        }
    }

    static int gradeIndex(String grade) {
        String g = grade == null ? "" : grade.trim().toUpperCase(Locale.ROOT);
        if (g.equals("OIL") || g.equals("YAĞLIK") || g.equals("YAGLIK")) g = "YAGLIK";
        for (int i = 0; i < GRADES.length; i++) if (GRADES[i].equals(g)) return i;
        return -1;
    }
}
