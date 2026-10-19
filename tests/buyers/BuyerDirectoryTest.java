package com.zeytindefteri.app;

import android.database.sqlite.SQLiteDatabase;
import java.util.List;

/** Alıcı rehberi: ad eşleştirme, bul-ya-da-oluştur, birleştirme, liste/karne SQL'i gerçek SQLite üzerinde. */
public final class BuyerDirectoryTest {
    private static int checks;

    private static void ok(boolean c, String m) {
        if (!c) throw new AssertionError(m);
        checks++;
    }

    private static boolean near(double a, double b) { return Math.abs(a - b) < 0.01; }

    public static void main(String[] args) {
        String file = args[0], helper = args[1];
        SQLiteDatabase db = new SQLiteDatabase(file, helper);
        db.execSQL("CREATE TABLE gardens (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, owner_uid TEXT DEFAULT '')");
        db.execSQL("CREATE TABLE buyers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT, region TEXT, note TEXT, created_at INTEGER NOT NULL, owner_uid TEXT DEFAULT '')");
        db.execSQL("CREATE TABLE sales (id INTEGER PRIMARY KEY AUTOINCREMENT, garden_id INTEGER NOT NULL, buyer_id INTEGER, sale_date TEXT NOT NULL, sale_kind TEXT DEFAULT 'SALE', total_kg REAL DEFAULT 0, gross_total REAL DEFAULT 0, amount_received REAL DEFAULT 0, season_year INTEGER DEFAULT 0, owner_uid TEXT DEFAULT '')");
        db.execSQL("CREATE TABLE sale_grades (id INTEGER PRIMARY KEY AUTOINCREMENT, sale_id INTEGER NOT NULL, grade TEXT NOT NULL, kg REAL DEFAULT 0, price_per_kg REAL DEFAULT 0, line_total REAL DEFAULT 0)");
        db.execSQL("CREATE TABLE receivables (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, related_sale_id INTEGER, amount REAL DEFAULT 0, total_received REAL DEFAULT 0, status TEXT DEFAULT 'OPEN', created_at INTEGER NOT NULL, owner_uid TEXT DEFAULT '')");
        db.execSQL("INSERT INTO gardens(name, owner_uid) VALUES('Girelli', 'u1'), ('Edremit', 'u1'), ('Başka', 'u2')");

        // --- Ad eşleştirme (Android'e bağlı olmayan kısım)
        ok(BuyerDirectory.normalize("  Hasan   USTA ").equals("hasan usta"), "boşluk ve büyük harf");
        ok(BuyerDirectory.normalize("İSMAİL IŞIK").equals("ismail ışık"), "Türkçe İ/I küçük harf");
        ok(BuyerDirectory.clean("  Hasan   Usta ").equals("Hasan Usta"), "görünen ad temizliği");
        ok(BuyerDirectory.normalize(null).isEmpty(), "boş ad");
        ok(BuyerDirectory.shortDate("2026-10-19").equals("19 Ekim"), "kısa tarih");
        ok(BuyerDirectory.shortDate("2026-02-05").equals("5 Şubat"), "kısa tarih şubat");
        ok(BuyerDirectory.shortDate("bozuk").equals("bozuk"), "okunamayan tarih");
        ok(BuyerDirectory.gradeIndex("YAGLIK") == 5 && BuyerDirectory.gradeIndex("OIL") == 5 && BuyerDirectory.gradeIndex("DOUBLE") == 0, "sınıf anahtarı");

        // --- Bul ya da oluştur: aynı ad tek alıcı, sahibine göre ayrı
        long hasan = BuyerDirectory.findOrCreate(db, "u1", "Hasan Usta", "", "Edremit", "test");
        ok(hasan > 0, "alıcı oluştu");
        ok(BuyerDirectory.findOrCreate(db, "u1", "  hasan   usta", "0532 000 00 00", "", "test") == hasan, "aynı ad aynı alıcı");
        ok(BuyerDirectory.findOrCreate(db, "u1", "HASAN USTA", "0555", "Havran", "test") == hasan, "büyük harf aynı alıcı");
        ok(BuyerDirectory.findOrCreate(db, "u2", "Hasan Usta", "", "", "test") != hasan, "başka kullanıcının alıcısı ayrı");
        ok(BuyerDirectory.findOrCreate(db, "u1", "   ", "", "", "test") == -1, "boş ad reddedilir");
        BuyerDirectory.Summary h = byId(BuyerDirectory.list(db, "u1", 2026), hasan);
        ok(h.phone.equals("0532 000 00 00"), "boş telefon dolduruldu, sonra üzerine yazılmadı");
        ok(h.region.equals("Edremit"), "dolu bölge korunur");
        ok(BuyerDirectory.find(db, "u1", "yılmaz zeytincilik") == -1, "olmayan alıcı");

        long yilmaz = BuyerDirectory.findOrCreate(db, "u1", "Yılmaz Zeytincilik", "", "Havran", "test");
        long kemal = BuyerDirectory.findOrCreate(db, "u1", "Kemal Tezgah", "", "Burhaniye", "test");
        ok(!BuyerDirectory.update(db, "u1", kemal, "hasan usta", "", ""), "başka alıcının adına güncellenemez");
        ok(BuyerDirectory.update(db, "u1", kemal, "Kemal Tezgahı", "0544", "Burhaniye"), "güncelleme");

        // --- Eski elle yazılmış kopyalar: doğrudan tabloya (eski kodun yaptığı gibi) ekle, sonra birleştir
        db.execSQL("INSERT INTO buyers(name, phone, region, created_at, owner_uid) VALUES('hasan usta ', '', '', 1, 'u1')");
        long dup = lastId(db, "buyers");
        // Satışlar: gg.aa.yyyy ve ISO tarihler karışık; biri geçen sezon, biri teklif (sayılmaz)
        long s1 = sale(db, 1, hasan, "19.10.2026", 107, 6370, 6370, 2026);
        long s2 = sale(db, 2, dup, "12.10.2026", 620, 37130, 37130, 2026);
        long s3 = sale(db, 2, hasan, "2026-10-05", 513, 30800, 26300, 2026);
        long s4 = sale(db, 2, yilmaz, "08.10.2026", 420, 25900, 25900, 2026);
        sale(db, 1, kemal, "20.11.2025", 300, 15000, 15000, 2025);
        db.execSQL("INSERT INTO sales(garden_id, buyer_id, sale_date, sale_kind, total_kg, gross_total, season_year, owner_uid) "
                + "VALUES(1, " + yilmaz + ", '09.10.2026', 'OFFER', 999, 99999, 2026, 'u1')");
        db.execSQL("INSERT INTO receivables(title, related_sale_id, amount, total_received, status, created_at, owner_uid) "
                + "VALUES('Satış alacağı', " + s3 + ", 30800, 26300, 'OPEN', 1, 'u1')");
        db.execSQL("INSERT INTO receivables(title, related_sale_id, amount, total_received, status, created_at, owner_uid) "
                + "VALUES('Satış alacağı', " + s1 + ", 6370, 6370, 'PAID', 1, 'u1')");
        // Sınıf satırları (fiyat karşılaştırması)
        grade(db, s1, "DOUBLE", 3, 130); grade(db, s1, "2", 39, 70);
        grade(db, s2, "DOUBLE", 20, 128); grade(db, s2, "2", 300, 72);
        grade(db, s4, "DOUBLE", 10, 120); grade(db, s4, "2", 200, 68); grade(db, s4, "3", 100, 44);

        int removed = BuyerDirectory.mergeDuplicates(db, "u1");
        ok(removed == 1, "bir kopya silindi");
        ok(BuyerDirectory.find(db, "u1", "Hasan Usta") == hasan, "en eski kayıt kaldı");
        ok(BuyerDirectory.nameForSale(db, s2).equals("Hasan Usta"), "kopyanın satışı asıl alıcıya taşındı");
        ok(BuyerDirectory.mergeDuplicates(db, "u1") == 0, "ikinci birleştirme boş");

        // --- Liste: sezon toplamları, alacak, sıralama
        List<BuyerDirectory.Summary> list = BuyerDirectory.list(db, "u1", 2026);
        ok(list.size() == 3, "u1 için 3 alıcı");
        BuyerDirectory.Summary first = list.get(0);
        ok(first.id == hasan, "en çok satış yapılan önce");
        ok(first.saleCount == 3 && near(first.kg, 1240) && near(first.gross, 74300), "Hasan sezon toplamı");
        ok(near(first.receivable, 4500), "Hasan alacağı (ödenmiş satış sayılmaz)");
        ok(first.lastSaleDate.equals("2026-10-19"), "son satış ISO'ya çevrildi: " + first.lastSaleDate);
        BuyerDirectory.Summary y = byId(list, yilmaz);
        ok(y.saleCount == 1 && near(y.gross, 25900), "teklif satış sayılmaz");
        BuyerDirectory.Summary k = byId(list, kemal);
        ok(k.saleCount == 0 && near(k.gross, 0) && k.lastSaleDate.equals("2025-11-20"), "geçen sezon satışı bu sezona girmez");
        ok(list.get(2).id == kemal, "bu sezon satışı olmayan sonda");
        ok(BuyerDirectory.recentFirst(db, "u1", 2026).get(0).id == hasan, "seçim penceresi: en son satış önce");
        ok(BuyerDirectory.filter(list, "havran").size() == 1, "bölgeye göre arama");
        ok(BuyerDirectory.filter(list, "USTA").size() == 1, "ada göre arama");
        ok(BuyerDirectory.list(db, "u2", 2026).size() == 1, "u2 yalnız kendi alıcısını görür");

        // --- Karne: satışlar ve fiyat karşılaştırması
        List<BuyerDirectory.SaleLine> sales = BuyerDirectory.sales(db, "u1", hasan, 2026);
        ok(sales.size() == 3, "3 satış");
        ok(sales.get(0).date.equals("2026-10-19") && sales.get(2).date.equals("2026-10-05"), "yeniden eskiye");
        ok(near(sales.get(2).open, 4500) && near(sales.get(0).open, 0), "satış bazında kalan");
        ok(sales.get(0).garden.equals("Girelli"), "bahçe adı");
        List<BuyerDirectory.PriceRow> prices = BuyerDirectory.priceComparison(db, "u1", hasan, 2026);
        BuyerDirectory.PriceRow dbl = row(prices, "Double"), two = row(prices, "2 Numara"), three = row(prices, "3 Numara");
        ok(near(dbl.buyerAvg, (3 * 130 + 20 * 128) / 23.0) && near(dbl.othersAvg, 120), "Double ağırlıklı ortalama");
        ok(near(two.buyerAvg, (39 * 70 + 300 * 72) / 339.0) && near(two.othersAvg, 68), "2 No ağırlıklı ortalama");
        ok(near(three.buyerAvg, 0) && near(three.othersAvg, 44), "yalnız diğerlerinde olan sınıf");
        BuyerDirectory.Summary forSale = BuyerDirectory.buyerForSale(db, s1);
        ok(forSale != null && forSale.id == hasan && forSale.phone.equals("0532 000 00 00"), "satışın alıcısı");
        ok(BuyerDirectory.buyerForSale(db, 99999) == null, "olmayan satış");

        // --- Elek performansı (ayrı kullanıcı u3, temiz veri)
        db.execSQL("INSERT INTO buyers(name, created_at, owner_uid) VALUES('Ali', 1, 'u3'), ('Veli', 1, 'u3'), ('Az', 1, 'u3')");
        long ali = BuyerDirectory.find(db, "u3", "Ali"), veli = BuyerDirectory.find(db, "u3", "Veli"), az = BuyerDirectory.find(db, "u3", "Az");
        long a1 = sale3(db, ali, 2026, "SALE");
        grade(db, a1, "DOUBLE", 10, 130); grade(db, a1, "1", 30, 100); grade(db, a1, "2", 40, 70); grade(db, a1, "4", 15, 30); grade(db, a1, "YAGLIK", 5, 20);
        long v1 = sale3(db, veli, 2026, "SALE");
        grade(db, v1, "1", 20, 100); grade(db, v1, "2", 30, 70); grade(db, v1, "3", 30, 40); grade(db, v1, "4", 20, 30);
        long old = sale3(db, veli, 2025, "SALE");
        grade(db, old, "DOUBLE", 500, 130);
        long offer = sale3(db, veli, 2026, "OFFER");
        grade(db, offer, "DOUBLE", 500, 130);
        long a2 = sale3(db, az, 2026, "SALE");
        grade(db, a2, "DOUBLE", 20, 130);
        java.util.Map<Long, BuyerDirectory.ElekStats> st = BuyerDirectory.elekStats(db, "u3", 2026);
        BuyerDirectory.ElekStats sa = st.get(ali), sv = st.get(veli);
        ok(near(sa.totalKg, 100) && near(sa.topShare(), 40) && near(sa.lowShare(), 20), "Ali dağılımı");
        ok(near(sa.avgPerKg(), (1300 + 3000 + 2800 + 450 + 100) / 100.0), "Ali kg başı ortalama");
        ok(near(sv.totalKg, 100) && near(sv.topShare(), 20), "geçen sezon ve teklif sayılmaz");
        ok(BuyerDirectory.elekLine(sa).equals("İri %40 · Ufak %20 · 76,5 TL/kg"), "liste satırı: " + BuyerDirectory.elekLine(sa));
        ok(BuyerDirectory.elekLine(null).isEmpty(), "satışsız alıcıda satır yok");
        BuyerDirectory.ElekStats othersOfAli = BuyerDirectory.others(st, ali);
        ok(near(othersOfAli.totalKg, 120), "diğerleri toplamı");
        ok(BuyerDirectory.elekVerdict(sa, othersOfAli).equals("Double + 1 Numara oranı diğer alıcılarından 7 puan yüksek."),
                "karşılaştırma: " + BuyerDirectory.elekVerdict(sa, othersOfAli));
        ok(BuyerDirectory.elekVerdict(st.get(az), BuyerDirectory.others(st, az)).isEmpty(), "az kiloda hüküm yok");
        ok(BuyerDirectory.elekVerdict(sv, BuyerDirectory.others(st, veli)).contains("düşük"), "düşük iri oranı");
        ok(BuyerDirectory.elekStats(db, "u1", 2026).get(ali) == null, "başka kullanıcının verisi karışmaz");

        System.out.println("PASS: " + checks + " alıcı rehberi kontrolü (gerçek SQLite)");
    }

    private static long sale(SQLiteDatabase db, int garden, long buyer, String date, double kg, double gross, double received, int season) {
        db.execSQL("INSERT INTO sales(garden_id, buyer_id, sale_date, total_kg, gross_total, amount_received, season_year, owner_uid) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, 'u1')", new Object[]{garden, buyer, date, kg, gross, received, season});
        return lastId(db, "sales");
    }

    private static long sale3(SQLiteDatabase db, long buyer, int season, String kind) {
        db.execSQL("INSERT INTO sales(garden_id, buyer_id, sale_date, sale_kind, season_year, owner_uid) VALUES(1, ?, '2026-10-19', ?, ?, 'u3')",
                new Object[]{buyer, kind, season});
        return lastId(db, "sales");
    }

    private static void grade(SQLiteDatabase db, long sale, String grade, double kg, double price) {
        db.execSQL("INSERT INTO sale_grades(sale_id, grade, kg, price_per_kg, line_total) VALUES(?, ?, ?, ?, ?)",
                new Object[]{sale, grade, kg, price, kg * price});
    }

    private static long lastId(SQLiteDatabase db, String table) {
        android.database.Cursor c = db.rawQuery("SELECT MAX(id) FROM " + table, new String[0]);
        c.moveToFirst();
        return c.getLong(0);
    }

    private static BuyerDirectory.Summary byId(List<BuyerDirectory.Summary> list, long id) {
        for (BuyerDirectory.Summary s : list) if (s.id == id) return s;
        throw new AssertionError("alıcı yok: " + id);
    }

    private static BuyerDirectory.PriceRow row(List<BuyerDirectory.PriceRow> rows, String label) {
        for (BuyerDirectory.PriceRow r : rows) if (r.label.equals(label)) return r;
        throw new AssertionError("satır yok: " + label);
    }
}
