package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cari hesap: tezgahçının aracı / tüccar / kendi alıcılarıyla para hesabı (günler boyu biriken alacak).
 *
 * Satır türleri:
 * - SALE      (alacak): akşam hesabındaki "tüccar ödeyecek" (gün sonunda, z_id ile) ya da kendi alıcıma satış.
 *             Tutar eksi olabilir: avans fazlaysa tezgah iade edecek demektir.
 * - RECEIVED  (tahsilat): alıcıdan gelen para.
 * - PAID      (ödeme): ben alıcıya para verdim (avans iadesi gibi); alacağı artırır.
 * Bakiye = Σ alacak − Σ tahsilat + Σ ödeme. Artı: alıcı bana borçlu. Eksi: ben ona borçluyum.
 * Yalnız bu telefonda; owner_uid sütunu olduğu için hesap silinince LocalAccountWipe temizler; yedeğe girer.
 */
public final class TezgahLedger {
    static final String TABLE = "tezgah_ledger";
    public static final int SALE = 1;
    public static final int RECEIVED = 2;
    public static final int PAID = 3;
    public static final double MAX_AMOUNT = 100_000_000d;

    /** Satış yöntemi (yalnız açıklama için saklanır). */
    public static final int METHOD_GRADES = 1, METHOD_TOTAL = 2, METHOD_COMMISSION = 3, METHOD_DAY = 4;

    private TezgahLedger() {}

    public static final class Entry {
        public long id, zId, createdAt;
        public String party = "", date = "", detail = "", note = "";
        public int kind, method;
        public double amount, kg;

        /** Bakiyeye etkisi. */
        public double signed() { return kind == RECEIVED ? -amount : amount; }
    }

    public static final class Party {
        public String name = "", key = "", lastDate = "";
        public double sales, received, paid, kg;
        public long lastAt;
        public int count;

        public double balance() { return r2(sales - received + paid); }
    }

    static void ensure(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + " (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "party TEXT NOT NULL, party_key TEXT NOT NULL, kind INTEGER NOT NULL, amount REAL NOT NULL, "
                + "kg REAL DEFAULT 0, method INTEGER DEFAULT 0, detail TEXT DEFAULT '', note TEXT DEFAULT '', "
                + "entry_date TEXT, z_id INTEGER DEFAULT 0, created_at INTEGER DEFAULT 0, owner_uid TEXT DEFAULT '')");
    }

    public static String key(String name) { return TezgahCrates.key(name); }

    public static boolean validParty(String name) { return TezgahCrates.validParty(name); }

    /** Tutar hatası; boşsa geçerli. SALE eksi olabilir (iade), tahsilat/ödeme artı olmalı. */
    public static String amountError(int kind, double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) return "Tutarı yaz.";
        if (Math.abs(amount) > MAX_AMOUNT) return "Tutar çok büyük.";
        if (kind == SALE ? Math.abs(amount) < 0.01 : amount < 0.01) return "Tutarı yaz.";
        return "";
    }

    public static long add(SQLiteDatabase db, String owner, String party, int kind, double amount, double kg, int method,
                           String detail, String note, String date, long zId, long now) {
        if (!validParty(party) || (kind != SALE && kind != RECEIVED && kind != PAID) || !amountError(kind, amount).isEmpty()) return -1;
        ensure(db);
        ContentValues v = new ContentValues();
        v.put("party", party.trim().replaceAll("\\s+", " "));
        v.put("party_key", key(party));
        v.put("kind", kind);
        v.put("amount", r2(amount));
        v.put("kg", Math.max(0, kg));
        v.put("method", method);
        v.put("detail", detail == null ? "" : detail);
        v.put("note", note == null ? "" : note.trim());
        v.put("entry_date", date == null ? "" : date);
        v.put("z_id", Math.max(0, zId));
        v.put("created_at", now);
        v.put("owner_uid", owner == null ? "" : owner);
        return db.insert(TABLE, null, v);
    }

    public static boolean delete(SQLiteDatabase db, String owner, long id) {
        ensure(db);
        return db.delete(TABLE, "id=? AND COALESCE(owner_uid,'')=?",
                new String[]{String.valueOf(id), owner == null ? "" : owner}) > 0;
    }

    /** Bu gün sonu (Z) cari hesaba işlendi mi (alacak satırı var mı)? */
    public static boolean hasZ(SQLiteDatabase db, String owner, long zId) {
        if (zId <= 0) return false;
        ensure(db);
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE + " WHERE COALESCE(owner_uid,'')=? AND z_id=? AND kind=?",
                new String[]{owner == null ? "" : owner, String.valueOf(zId), String.valueOf(SALE)});
        try { return c.moveToFirst() && c.getInt(0) > 0; } finally { c.close(); }
    }

    public static List<Entry> entries(SQLiteDatabase db, String owner) { return query(db, owner, null); }

    public static List<Entry> entriesOf(SQLiteDatabase db, String owner, String party) { return query(db, owner, key(party)); }

    private static List<Entry> query(SQLiteDatabase db, String owner, String partyKey) {
        ensure(db);
        List<Entry> out = new ArrayList<>();
        String o = owner == null ? "" : owner;
        String cols = "SELECT id, party, kind, amount, kg, method, detail, note, entry_date, z_id, created_at FROM " + TABLE;
        Cursor c = partyKey == null
                ? db.rawQuery(cols + " WHERE COALESCE(owner_uid,'')=? ORDER BY created_at DESC, id DESC", new String[]{o})
                : db.rawQuery(cols + " WHERE COALESCE(owner_uid,'')=? AND party_key=? ORDER BY created_at DESC, id DESC", new String[]{o, partyKey});
        try {
            while (c.moveToNext()) {
                Entry e = new Entry();
                e.id = c.getLong(0);
                e.party = nz(c.getString(1));
                e.kind = c.getInt(2);
                e.amount = c.getDouble(3);
                e.kg = c.getDouble(4);
                e.method = c.getInt(5);
                e.detail = nz(c.getString(6));
                e.note = nz(c.getString(7));
                e.date = nz(c.getString(8));
                e.zId = c.getLong(9);
                e.createdAt = c.getLong(10);
                out.add(e);
            }
        } finally {
            c.close();
        }
        return out;
    }

    /** Alıcı özetleri: önce açık hesaplar (bakiye büyüklüğüne göre), sonra kapananlar (en son hareket önce). */
    public static List<Party> parties(List<Entry> entries) {
        Map<String, Party> map = new LinkedHashMap<>();
        for (Entry e : entries) {
            String k = key(e.party);
            if (k.isEmpty()) continue;
            Party p = map.get(k);
            if (p == null) { p = new Party(); p.key = k; p.name = e.party; map.put(k, p); }
            p.count++;
            if (e.kind == SALE) { p.sales += e.amount; p.kg += e.kg; }
            else if (e.kind == RECEIVED) p.received += e.amount;
            else if (e.kind == PAID) p.paid += e.amount;
            if (e.createdAt > p.lastAt) { p.lastAt = e.createdAt; p.lastDate = e.date; }
            else if (p.lastDate.isEmpty()) p.lastDate = e.date;
        }
        List<Party> out = new ArrayList<>(map.values());
        Collections.sort(out, (a, b) -> {
            double aa = Math.abs(a.balance()), ab = Math.abs(b.balance());
            boolean za = aa < 0.5, zb = ab < 0.5;
            if (za != zb) return za ? 1 : -1;
            if (!za && Math.abs(aa - ab) > 0.005) return Double.compare(ab, aa);
            return Long.compare(b.lastAt, a.lastAt);
        });
        return out;
    }

    /** Toplam alacak (artı bakiyeler) ve toplam borç (eksi bakiyeler, artı sayı olarak). */
    public static double[] totals(List<Entry> entries) {
        double recv = 0, owe = 0;
        for (Party p : parties(entries)) {
            double b = p.balance();
            if (b > 0.5) recv += b; else if (b < -0.5) owe += -b;
        }
        return new double[]{r2(recv), r2(owe)};
    }

    public static double balanceOf(SQLiteDatabase db, String owner, String party) {
        if (!validParty(party)) return 0;
        double b = 0;
        for (Entry e : entriesOf(db, owner, party)) b += e.signed();
        return r2(b);
    }

    // ------------------------------------------------------------------ satış hesapları (saf)

    /** Numara numara: Σ kg × fiyat. */
    public static double gradeAmount(double[] kg, double[] price) {
        double t = 0;
        for (int g = 0; g < kg.length && g < price.length; g++) t += Math.max(0, kg[g]) * Math.max(0, price[g]);
        return r2(t);
    }

    public static double sum(double[] v) {
        double t = 0;
        for (double x : v) t += Math.max(0, x);
        return Math.round(t * 10) / 10.0;
    }

    /** Toplam kilo × tek fiyat ya da kilo × komisyon. */
    public static double times(double kg, double price) { return r2(Math.max(0, kg) * Math.max(0, price)); }

    /** "Double 120 kg × 110 ₺ · 1 No 80 kg × 90 ₺" (numara numara satış açıklaması). */
    public static String gradeDetail(double[] kg, double[] price) {
        StringBuilder b = new StringBuilder();
        for (int g = 0; g < kg.length && g < price.length; g++) {
            if (kg[g] <= 0) continue;
            if (b.length() > 0) b.append(" · ");
            b.append(gradeName(g)).append(' ').append(TezgahModel.kg(kg[g])).append(" × ").append(TezgahModel.money(price[g]));
        }
        return b.toString();
    }

    /** "Double", "1 No" … "Yağlık". */
    public static String gradeName(int g) {
        String s = TezgahModel.SHORT[g];
        return s.matches("\\d") ? s + " No" : s;
    }

    public static String methodLabel(int method) {
        switch (method) {
            case METHOD_GRADES: return "Numara numara satış";
            case METHOD_TOTAL: return "Toplu satış";
            case METHOD_COMMISSION: return "Komisyon";
            case METHOD_DAY: return "Akşam hesabı";
            default: return "Alacak";
        }
    }

    /** Satırın tek satırlık başlığı. */
    public static String title(Entry e) {
        if (e.kind == RECEIVED) return "Tahsilat";
        if (e.kind == PAID) return "Ödeme yaptım";
        return e.amount < 0 ? methodLabel(e.method) + " (iade)" : methodLabel(e.method);
    }

    /** "Hüseyin · 400.000 ₺ alacağın var" / "… sana 5.000 ₺ alacaklı" / "… hesap kapalı". */
    public static String balanceLine(String name, double balance) {
        String n = name == null || name.trim().isEmpty() ? "Alıcı" : name.trim();
        if (balance > 0.5) return n + " · " + TezgahModel.tl(balance) + " alacağın var";
        if (balance < -0.5) return n + " · sen ona " + TezgahModel.tl(-balance) + " borçlusun";
        return n + " · hesap kapalı";
    }

    /** WhatsApp için hesap dökümü (en çok son 15 satır, eskiden yeniye). */
    public static String statement(String name, List<Entry> list, String trader) {
        StringBuilder b = new StringBuilder();
        b.append("🫒 Cari hesap · ").append(name == null ? "" : name.trim()).append('\n');
        if (trader != null && !trader.trim().isEmpty()) b.append("Tezgah: ").append(trader.trim()).append('\n');
        b.append('\n');
        int from = Math.min(list.size(), 15);
        for (int i = from - 1; i >= 0; i--) {
            Entry e = list.get(i);
            b.append(e.date.isEmpty() ? "" : e.date + "  ").append(title(e)).append(": ")
                    .append(e.kind == RECEIVED ? "−" : e.amount < 0 ? "−" : "+").append(TezgahModel.tl(Math.abs(e.amount)));
            if (e.kg > 0) b.append(" (").append(TezgahModel.kg(e.kg)).append(')');
            b.append('\n');
        }
        if (list.size() > 15) b.append("… (").append(list.size() - 15).append(" eski satır)\n");
        double bal = 0;
        for (Entry e : list) bal += e.signed();
        bal = r2(bal);
        b.append('\n');
        b.append(bal > 0.5 ? "KALAN ALACAK: " + TezgahModel.tl(bal) : bal < -0.5 ? "TEZGAH BORÇLU: " + TezgahModel.tl(-bal) : "HESAP KAPALI ✓")
                .append('\n');
        b.append("\nBilgi amaçlıdır; mali belge yerine geçmez.\nZeytin Defteri ile hazırlandı");
        return b.toString();
    }

    static double r2(double v) { return Math.round(v * 100) / 100.0; }

    private static String nz(String s) { return s == null ? "" : s; }
}
