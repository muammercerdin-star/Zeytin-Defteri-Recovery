package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.List;

/**
 * Akşam hesabı: tezgahçının gün sonunda büyük tüccarla hesaplaşması.
 *
 * Tezgahçı tüccar adına üreticilerden alır, akşam günün toplamını tüccara teslim eder, kilo başı komisyon alır.
 * Tüccar tezgahın eleğini kabul eder; fark yalnız kilodadır (fire).
 *   Fire            = alınan net kg − tüccarın kantarındaki kg (numara numara)
 *   Komisyon        = teslim kg × komisyon (₺/kg)
 *   Tüccar ödeyecek = üreticilere tutar + komisyon − sabah alınan avans   (eksiyse tezgahçı iade eder)
 * Kayıt yalnız bu telefonda (tezgah_closings), günde bir tane; hesap silinince LocalAccountWipe temizler.
 */
public final class TezgahClosing {
    static final String TABLE = "tezgah_closings";
    public static final int N = TezgahModel.GRADES.length;

    // Girdiler
    public final double[] bought = new double[N];     // alınan net kg (alım defterinden)
    public final double[] boughtValue = new double[N]; // numara numara üreticilere tutar
    public final double[] delivered = new double[N];  // tüccarın kantarında çıkan kg
    /** Teslim kilosu elle girildi mi? Girilmeyen numara her zaman güncel alınan kiloyu izler (yeni alımlar eklense de). */
    public final boolean[] edited = new boolean[N];
    public double producerTotal, producerPaid;
    public double rate, advance;
    public int purchases;

    /** Günün alımlarından doldurur; teslim kiloları başlangıçta alınanla aynıdır (fire 0). */
    public static TezgahClosing fromPurchases(List<TezgahPurchases.Purchase> list) {
        TezgahClosing c = new TezgahClosing();
        for (TezgahPurchases.Purchase p : list) {
            TezgahModel m = TezgahModel.decode(p.data);
            for (int g = 0; g < N; g++) {
                c.bought[g] += m.gradeNet(g);
                c.boughtValue[g] += m.gradeAmount(g);
            }
            c.producerTotal += p.gross;
            c.producerPaid += p.paid;
            c.purchases++;
        }
        for (int g = 0; g < N; g++) {
            c.bought[g] = r1(c.bought[g]);
            c.delivered[g] = c.bought[g];
        }
        return c;
    }

    /** Kullanıcının girdiği teslim kilosu; alınanla aynıysa "elle girilmedi" sayılır. */
    public void setDelivered(int g, double kg) {
        delivered[g] = Math.max(0, r1(kg));
        edited[g] = Math.abs(delivered[g] - bought[g]) >= 0.05;
    }

    /** Kutu boşaltıldı: alınan kiloya dön. */
    public void clearDelivered(int g) {
        delivered[g] = bought[g];
        edited[g] = false;
    }

    public double boughtKg() { double s = 0; for (double v : bought) s += v; return r1(s); }

    public double deliveredKg() { double s = 0; for (double v : delivered) s += v; return r1(s); }

    public double fire(int g) { return r1(bought[g] - delivered[g]); }

    public double fireKg() { return r1(boughtKg() - deliveredKg()); }

    /** Fire yüzdesi (alınana göre); eksi = tüccarın kantarı fazla çıktı. */
    public double firePercent() { double b = boughtKg(); return b <= 0 ? 0 : fireKg() * 100.0 / b; }

    /** Fireye düşen değer: numaranın ortalama alış fiyatıyla. */
    public double fireValue() {
        double s = 0;
        for (int g = 0; g < N; g++) {
            if (bought[g] > 0) s += fire(g) * (boughtValue[g] / bought[g]);
        }
        return r2(s);
    }

    public double commission() { return r2(deliveredKg() * Math.max(0, rate)); }

    /** Tüccarın tezgahçıya ödeyeceği (eksiyse tezgahçı iade eder). */
    public double traderOwes() { return r2(producerTotal + commission() - Math.max(0, advance)); }

    /** Tezgahçının üreticilere hâlâ borcu. */
    public double producerDue() { return r2(Math.max(0, producerTotal - producerPaid)); }

    /** WhatsApp'a gidecek hesap özeti. */
    public String summary(String dateLabel, String traderName) {
        StringBuilder sb = new StringBuilder();
        sb.append("🫒 Akşam hesabı · ").append(dateLabel).append('\n');
        if (traderName != null && !traderName.isEmpty()) sb.append("Tezgah: ").append(traderName).append('\n');
        sb.append('\n').append("Numara: alınan → teslim (fire)\n");
        for (int g = 0; g < N; g++) {
            if (bought[g] <= 0 && delivered[g] <= 0) continue;
            sb.append(TezgahModel.LABELS[g]).append(": ").append(TezgahModel.kg(bought[g])).append(" → ")
                    .append(TezgahModel.kg(delivered[g]));
            if (Math.abs(fire(g)) >= 0.05) sb.append(" (").append(sign(fire(g))).append(')');
            sb.append('\n');
        }
        sb.append('\n');
        sb.append("Alınan: ").append(TezgahModel.kg(boughtKg())).append(" · ").append(purchases).append(" üretici\n");
        sb.append("Teslim: ").append(TezgahModel.kg(deliveredKg())).append('\n');
        sb.append("Fire: ").append(TezgahModel.kg(fireKg())).append(" (%").append(oneDecimal(firePercent())).append(")");
        if (Math.abs(fireValue()) >= 1) sb.append(" ≈ ").append(TezgahModel.tl(fireValue()));
        sb.append("\n\n");
        sb.append("Üreticilere tutar: ").append(TezgahModel.tl(producerTotal)).append('\n');
        if (rate > 0) {
            sb.append("Komisyon: ").append(TezgahModel.kg(deliveredKg())).append(" × ").append(TezgahModel.money(rate))
                    .append(" = ").append(TezgahModel.tl(commission())).append('\n');
        }
        if (advance > 0) sb.append("Alınan avans: −").append(TezgahModel.tl(advance)).append('\n');
        double owes = traderOwes();
        sb.append(owes >= 0 ? "TÜCCAR ÖDEYECEK: " : "TEZGAH İADE EDECEK: ").append(TezgahModel.tl(Math.abs(owes))).append('\n');
        sb.append("\nZeytin Defteri ile hazırlandı");
        return sb.toString();
    }

    private static String sign(double v) {
        return (v > 0 ? "−" : "+") + TezgahModel.kg(Math.abs(v));
    }

    static String oneDecimal(double v) {
        return String.valueOf(Math.round(v * 10) / 10.0).replace('.', ',');
    }

    // ------------------------------------------------------------------ saklama

    public static void ensure(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "owner_uid TEXT DEFAULT '', "
                + "record_date TEXT NOT NULL, "
                + "delivered TEXT DEFAULT '', "
                + "rate REAL DEFAULT 0, "
                + "advance REAL DEFAULT 0, "
                + "updated_at INTEGER DEFAULT 0, "
                + "UNIQUE(owner_uid, record_date))");
    }

    /** Kayıtlı teslim kiloları/komisyon/avans varsa üstüne yazar. Kayıt varsa true. */
    public static boolean load(SQLiteDatabase db, String owner, String date, TezgahClosing c) {
        ensure(db);
        Cursor q = db.rawQuery("SELECT delivered, rate, advance FROM " + TABLE
                + " WHERE COALESCE(owner_uid,'')=? AND record_date=?", new String[]{owner == null ? "" : owner, date});
        try {
            if (!q.moveToFirst()) return false;
            String raw = q.getString(0) == null ? "" : q.getString(0);
            // v2: yalnız elle girilen numaralar yazılı, boş = alınanı izle. Eski biçim (her numara dolu, kendiliğinden
            // kaydedilmiş olabilir) güvenilmez: yok sayılır, teslim = alınan.
            if (raw.startsWith("v2|")) {
                String[] d = raw.substring(3).split(";", -1);
                if (d.length == N) {
                    for (int g = 0; g < N; g++) {
                        if (d[g].isEmpty()) continue;
                        try { c.setDelivered(g, Double.parseDouble(d[g])); } catch (NumberFormatException ignored) { }
                    }
                }
            }
            c.rate = Math.max(0, q.getDouble(1));
            c.advance = Math.max(0, q.getDouble(2));
            return true;
        } finally {
            q.close();
        }
    }

    public static boolean save(SQLiteDatabase db, String owner, String date, TezgahClosing c, long now) {
        ensure(db);
        StringBuilder d = new StringBuilder("v2|");
        for (int g = 0; g < N; g++) { if (g > 0) d.append(';'); if (c.edited[g]) d.append(c.delivered[g]); }
        String o = owner == null ? "" : owner;
        ContentValues v = new ContentValues();
        v.put("delivered", d.toString());
        v.put("rate", Math.max(0, c.rate));
        v.put("advance", Math.max(0, c.advance));
        v.put("updated_at", now);
        if (db.update(TABLE, v, "COALESCE(owner_uid,'')=? AND record_date=?", new String[]{o, date}) > 0) return true;
        v.put("owner_uid", o);
        v.put("record_date", date);
        return db.insert(TABLE, null, v) > 0;
    }

    private static double r1(double v) { return Math.round(v * 10) / 10.0; }

    private static double r2(double v) { return Math.round(v * 100) / 100.0; }
}
