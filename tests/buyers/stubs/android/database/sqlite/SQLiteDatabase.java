package android.database.sqlite;
import android.content.ContentValues;
import android.database.Cursor;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
/** Test stub'ı: her çağrı tests/buyers/sql_helper.py ile gerçek SQLite dosyasında çalışır. */
public class SQLiteDatabase {
    private final String file, helper;
    public SQLiteDatabase(String file, String helper) { this.file = file; this.helper = helper; }
    private static String enc(Object a) {
        if (a == null) return "\u001e";
        if (a instanceof Integer || a instanceof Long) return "I" + a;
        if (a instanceof Double || a instanceof Float) return "D" + a;
        return "S" + a;
    }
    private String run(String mode, String sql, Object[] args) {
        try {
            Process p = new ProcessBuilder("python3", helper, file, mode).start();
            StringBuilder in = new StringBuilder(sql);
            if (args != null) for (Object a : args) in.append('\u001d').append(enc(a));
            try (OutputStream o = p.getOutputStream()) { o.write(in.toString().getBytes(StandardCharsets.UTF_8)); }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream i = p.getInputStream()) { i.transferTo(out); }
            String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (p.waitFor() != 0) throw new RuntimeException("SQL hatası: " + err + "\n" + sql);
            return out.toString(StandardCharsets.UTF_8);
        } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); }
    }
    public Cursor rawQuery(String sql, String[] args) {
        String text = run("query", sql, args);
        List<String[]> rows = new ArrayList<>();
        if (!text.isEmpty()) for (String line : text.split("\n", -1)) {
            String[] cells = line.split("\u001f", -1);
            for (int i = 0; i < cells.length; i++) if (cells[i].equals("\u001e")) cells[i] = null;
            rows.add(cells);
        }
        return new Cursor(rows);
    }
    public void execSQL(String sql) { run("exec", sql, null); }
    public void execSQL(String sql, Object[] args) { run("exec", sql, args); }
    public long insert(String table, String nullHack, ContentValues v) {
        StringBuilder cols = new StringBuilder(), marks = new StringBuilder();
        List<Object> args = new ArrayList<>();
        for (Map.Entry<String, Object> e : v.values.entrySet()) {
            if (cols.length() > 0) { cols.append(','); marks.append(','); }
            cols.append(e.getKey()); marks.append('?'); args.add(e.getValue());
        }
        return Long.parseLong(run("insert", "INSERT INTO " + table + "(" + cols + ") VALUES(" + marks + ")", args.toArray()).trim());
    }
    public int update(String table, ContentValues v, String where, String[] whereArgs) {
        StringBuilder set = new StringBuilder();
        List<Object> args = new ArrayList<>();
        for (Map.Entry<String, Object> e : v.values.entrySet()) {
            if (set.length() > 0) set.append(',');
            set.append(e.getKey()).append("=?"); args.add(e.getValue());
        }
        if (whereArgs != null) for (String a : whereArgs) args.add(a);
        return Integer.parseInt(run("change", "UPDATE " + table + " SET " + set + " WHERE " + where, args.toArray()).trim());
    }
    public int delete(String table, String where, String[] whereArgs) {
        return Integer.parseInt(run("change", "DELETE FROM " + table + " WHERE " + where, whereArgs).trim());
    }
    public void beginTransaction() {}
    public void setTransactionSuccessful() {}
    public void endTransaction() {}
}
