package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Yalnız telefonda duran kayıtların yedeği: tezgah alımları, ödemeleri, akşam hesapları, gün sonu (Z) raporları
 * Elek satışlarının ödeme geçmişi ve yağhane sıkımları; ayrıca tezgah fiyatları/darası.
 *
 * Dosya sade metindir (kütüphane gerekmez, sürümden bağımsız):
 *   ZEYTIN-DEFTERI-YEDEK 1
 *   @created 1761330000000
 *   @tables 8                       (yedekteki tablo ve satır sayısı: yarım kalmış dosya böyle anlaşılır)
 *   @rows 120
 *   #TABLE tezgah_purchases
 *   #COLS id  record_date  …        (sekmeyle ayrılır)
 *   I12  S2026-10-24  D73.5  N      (I tam sayı, D ondalık, S metin, N boş; \t \n \\ kaçışlı)
 *   #PREF tare  S2
 *
 * Eksik dosya reddedilir: hiç tablo yoksa, bir tablonun sütun satırı yoksa ya da @tables/@rows tutmuyorsa
 * (yarıda kesilmiş indirme, yanlış kopyalanmış metin) mevcut kayıtlara dokunulmaz. @tables/@rows satırları
 * eski uygulamalarda yok sayılır; onlarsız eski yedekler de okunur.
 *
 * Geri yükleme bu hesabın yedekte bulunan tablolardaki satırlarını yedektekiyle DEĞİŞTİRİR (yedekte hiç
 * bulunmayan tablo, örneğin eski sürüm yedeğinde olmayan kasa defteri, olduğu gibi kalır) (id'ler korunur, böylece
 * alım ↔ Z ↔ ödeme bağları bozulmaz). owner_uid her zaman şu anki hesap yapılır. Bulut verisine dokunmaz.
 */
public final class TezgahBackup {
    public static final String MAGIC = "ZEYTIN-DEFTERI-YEDEK";
    public static final int VERSION = 1;
    /** Sıra önemli: geri yüklemede bağlı tablolar sonra yazılır. */
    public static final String[] TABLES = {"tezgah_z", "tezgah_purchases", "tezgah_payments", "tezgah_closings", "sale_payments", "oil_pressings", "tezgah_crates", "tezgah_ledger"};

    private TezgahBackup() {}

    public static final class Data {
        public long created;
        public final Map<String, List<String>> cols = new LinkedHashMap<>();
        public final Map<String, List<Object[]>> rows = new LinkedHashMap<>();
        public final Map<String, Object> prefs = new LinkedHashMap<>();
        /** Dosyadaki bütün tablo/satır sayısı (bilinmeyen tablolar dahil). */
        int tablesSeen, rowsSeen;

        public int rowCount(String table) { List<Object[]> r = rows.get(table); return r == null ? 0 : r.size(); }

        public int purchases() { return rowCount("tezgah_purchases"); }
    }

    static void ensureAll(SQLiteDatabase db) {
        TezgahZ.ensure(db);          // alımlar ve akşam hesabı tablolarını da kurar
        TezgahPurchases.ensure(db);
        TezgahPayments.ensure(db);
        SalePayments.ensure(db);
        OilPressings.ensure(db);
        TezgahCrates.ensure(db);
        TezgahLedger.ensure(db);
    }

    // ------------------------------------------------------------------ dışa aktar

    /** Bu hesabın yerel kayıtlarını metne çevirir. prefs: tezgah ayarları (fiyatlar, dara). */
    public static String export(SQLiteDatabase db, String owner, Map<String, Object> prefs, long now) {
        ensureAll(db);
        String o = owner == null ? "" : owner;
        StringBuilder b = new StringBuilder();
        b.append(MAGIC).append(' ').append(VERSION).append('\n');
        b.append("@created ").append(now).append('\n');
        StringBuilder head = b;
        b = new StringBuilder();
        int tables = 0, rowsOut = 0;
        for (String t : TABLES) {
            List<String> cols = columns(db, t);
            cols.remove("owner_uid");
            if (cols.isEmpty()) continue;
            b.append("#TABLE ").append(t).append('\n');
            tables++;
            b.append("#COLS ").append(String.join("\t", cols)).append('\n');
            StringBuilder sql = new StringBuilder("SELECT ");
            for (int i = 0; i < cols.size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append("typeof(").append(cols.get(i)).append("), ").append(cols.get(i));
            }
            sql.append(" FROM ").append(t).append(" WHERE COALESCE(owner_uid,'')=? ORDER BY id");
            Cursor c = db.rawQuery(sql.toString(), new String[]{o});
            try {
                while (c.moveToNext()) {
                    for (int i = 0; i < cols.size(); i++) {
                        if (i > 0) b.append('\t');
                        String type = c.getString(i * 2);
                        String val = c.isNull(i * 2 + 1) ? null : c.getString(i * 2 + 1);
                        b.append(cell(type, val));
                    }
                    b.append('\n');
                    rowsOut++;
                }
            } finally {
                c.close();
            }
        }
        if (prefs != null) {
            for (Map.Entry<String, Object> e : prefs.entrySet()) {
                Object v = e.getValue();
                b.append("#PREF ").append(escape(e.getKey())).append('\t').append(encode(v)).append('\n');
            }
        }
        // Sayılar tablolardan önce: eski sürümler bu satırları (tablo dışında oldukları için) yok sayar.
        head.append("@tables ").append(tables).append('\n').append("@rows ").append(rowsOut).append('\n');
        return head.append(b).toString();
    }

    static String cell(String sqlType, String value) {
        if (value == null || "null".equals(sqlType)) return "N";
        if ("integer".equals(sqlType)) return "I" + value;
        if ("real".equals(sqlType)) return "D" + value;
        return "S" + escape(value);
    }

    static String encode(Object v) {
        if (v == null) return "N";
        if (v instanceof Integer || v instanceof Long) return "I" + v;
        if (v instanceof Float || v instanceof Double) return "D" + v;
        return "S" + escape(String.valueOf(v));
    }

    static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\') b.append("\\\\");
            else if (ch == '\t') b.append("\\t");
            else if (ch == '\n') b.append("\\n");
            else if (ch == '\r') b.append("\\r");
            else b.append(ch);
        }
        return b.toString();
    }

    static String unescape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                b.append(n == 't' ? '\t' : n == 'n' ? '\n' : n == 'r' ? '\r' : n);
            } else {
                b.append(ch);
            }
        }
        return b.toString();
    }

    static Object decode(String cell) {
        if (cell == null || cell.isEmpty() || cell.equals("N")) return null;
        char k = cell.charAt(0);
        String v = cell.substring(1);
        try {
            if (k == 'I') return Long.parseLong(v);
            if (k == 'D') return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bozuk sayı: " + cell);
        }
        if (k == 'S') return unescape(v);
        throw new IllegalArgumentException("Bilinmeyen hücre: " + cell);
    }

    // ------------------------------------------------------------------ oku

    /** Yedek metnini okur; Zeytin Defteri yedeği değilse ya da bozuksa IllegalArgumentException. */
    public static Data parse(String text) {
        if (text == null) throw new IllegalArgumentException("Dosya boş.");
        String[] lines = text.replace("\r\n", "\n").split("\n", -1);
        if (lines.length == 0 || !lines[0].startsWith(MAGIC + " ")) {
            throw new IllegalArgumentException("Bu dosya bir Zeytin Defteri yedeği değil.");
        }
        int version;
        try { version = Integer.parseInt(lines[0].substring(MAGIC.length() + 1).trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Yedek sürümü okunamadı."); }
        if (version > VERSION) throw new IllegalArgumentException("Bu yedek uygulamanın daha yeni bir sürümüyle alınmış; önce uygulamayı güncelle.");
        Data d = new Data();
        String table = null;
        List<String> cols = null;
        boolean skipping = false;
        int wantTables = -1, wantRows = -1;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) continue;
            if (line.startsWith("@created ")) {
                try { d.created = Long.parseLong(line.substring(9).trim()); } catch (NumberFormatException ignored) { }
            } else if (line.startsWith("@tables ") && d.tablesSeen == 0) {
                wantTables = count(line.substring(8));
            } else if (line.startsWith("@rows ") && d.tablesSeen == 0) {
                wantRows = count(line.substring(6));
            } else if (line.startsWith("#TABLE ")) {
                d.tablesSeen++;
                if (table != null && cols == null) throw new IllegalArgumentException(HALF);
                table = line.substring(7).trim();
                skipping = !known(table);
                if (skipping) { table = null; cols = null; continue; } // bilinmeyen tablo atlanır
                cols = null;
                d.rows.put(table, new ArrayList<>());
            } else if (line.startsWith("#COLS ")) {
                if (table == null) continue;
                cols = new ArrayList<>();
                for (String c : line.substring(6).split("\t", -1)) {
                    if (!c.matches("[a-z_][a-z0-9_]*")) throw new IllegalArgumentException("Bozuk sütun adı.");
                    cols.add(c);
                }
                d.cols.put(table, cols);
            } else if (line.startsWith("#PREF ")) {
                String[] kv = line.substring(6).split("\t", -1);
                if (kv.length == 2) d.prefs.put(unescape(kv[0]), decode(kv[1]));
            } else if (skipping) {
                d.rowsSeen++; // bilinmeyen tablonun satırı: yazılmaz ama sayılır
            } else if (table != null && cols != null) {
                d.rowsSeen++;
                String[] cells = line.split("\t", -1);
                if (cells.length != cols.size()) throw new IllegalArgumentException("Bozuk satır (" + (i + 1) + ").");
                Object[] row = new Object[cells.length];
                for (int k = 0; k < cells.length; k++) row[k] = decode(cells[k]);
                d.rows.get(table).add(row);
            } else if (table != null) {
                throw new IllegalArgumentException(HALF); // sütun satırından önce kayıt
            }
        }
        if (table != null && cols == null) throw new IllegalArgumentException(HALF);
        if (d.cols.isEmpty()) {
            throw new IllegalArgumentException("Bu yedek dosyası eksik: içinde hiç kayıt bölümü yok. "
                    + "Telefondaki kayıtlara dokunulmadı.");
        }
        if ((wantTables >= 0 && wantTables != d.tablesSeen) || (wantRows >= 0 && wantRows != d.rowsSeen)) {
            throw new IllegalArgumentException("Bu yedek dosyası yarım kalmış"
                    + (wantRows >= 0 ? " (" + wantRows + " kayıttan " + d.rowsSeen + " tanesi var)" : "")
                    + ". Telefondaki kayıtlara dokunulmadı; yedeğin tam halini seç.");
        }
        return d;
    }

    private static final String HALF = "Bu yedek dosyası yarım ya da bozuk. Telefondaki kayıtlara dokunulmadı.";

    private static int count(String raw) {
        try { return Math.max(0, Integer.parseInt(raw.trim())); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(HALF); }
    }

    private static boolean known(String table) {
        for (String t : TABLES) if (t.equals(table)) return true;
        return false;
    }

    // ------------------------------------------------------------------ geri yükle

    /**
     * Bu hesabın yerel kayıtlarını yedektekiyle değiştirir (tek işlem; hata olursa hiçbir şey değişmez).
     * Dönüş: yazılan satır sayısı; başarısızsa -1.
     */
    public static int restore(SQLiteDatabase db, String owner, Data d) {
        ensureAll(db);
        String o = owner == null ? "" : owner;
        int written = 0;
        db.beginTransaction();
        try {
            for (String t : TABLES) {
                List<String> cols = d.cols.get(t);
                List<Object[]> rows = d.rows.get(t);
                if (cols == null || rows == null) continue; // yedekte olmayan tablo: telefondaki hali kalır
                List<String> have = columns(db, t);
                db.delete(t, "COALESCE(owner_uid,'')=?", new String[]{o});
                for (Object[] row : rows) {
                    ContentValues v = new ContentValues();
                    for (int k = 0; k < cols.size(); k++) {
                        String c = cols.get(k);
                        if (!have.contains(c) || c.equals("owner_uid")) continue; // eski/yeni sürüm farkı: bilinmeyen sütun atlanır
                        put(v, c, row[k]);
                    }
                    v.put("owner_uid", o);
                    if (db.insert(t, null, v) == -1) return -1;
                    written++;
                }
            }
            db.setTransactionSuccessful();
            return written;
        } catch (RuntimeException e) {
            return -1;
        } finally {
            db.endTransaction();
        }
    }

    static void put(ContentValues v, String c, Object x) {
        if (x == null) v.putNull(c);
        else if (x instanceof Long) v.put(c, (Long) x);
        else if (x instanceof Double) v.put(c, (Double) x);
        else v.put(c, String.valueOf(x));
    }

    static List<String> columns(SQLiteDatabase db, String table) {
        List<String> out = new ArrayList<>();
        Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        try {
            while (c.moveToNext()) out.add(c.getString(1));
        } finally {
            c.close();
        }
        return out;
    }

    /** "12 alım · 2 gün sonu · 3 ödeme" gibi kısa içerik özeti. */
    public static String describe(Data d) {
        StringBuilder b = new StringBuilder();
        b.append(d.purchases()).append(" alım");
        if (d.rowCount("tezgah_z") > 0) b.append(" · ").append(d.rowCount("tezgah_z")).append(" gün sonu");
        int pays = d.rowCount("tezgah_payments") + d.rowCount("sale_payments");
        if (pays > 0) b.append(" · ").append(pays).append(" ödeme");
        if (d.rowCount("oil_pressings") > 0) b.append(" · ").append(d.rowCount("oil_pressings")).append(" sıkım");
        if (d.rowCount("tezgah_ledger") > 0) b.append(" · ").append(d.rowCount("tezgah_ledger")).append(" cari satırı");
        if (d.rowCount("tezgah_crates") > 0) b.append(" · ").append(d.rowCount("tezgah_crates")).append(" kasa hareketi");
        return b.toString();
    }
}
