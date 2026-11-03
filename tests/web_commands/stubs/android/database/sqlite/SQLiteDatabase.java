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
    private final Process process;
    private final java.io.BufferedReader reader;
    private final java.io.Writer writer;
    private final java.util.ArrayDeque<Boolean> transactions = new java.util.ArrayDeque<>();
    private boolean rollback;
    public SQLiteDatabase(String file, String helper) {
        try {
            process = new ProcessBuilder("python3", helper, file).start();
            reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            writer = new java.io.OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    private synchronized String run(String mode, String sql, Object[] args) {
        try {
            org.json.JSONObject request = new org.json.JSONObject();
            request.put("mode", mode);
            request.put("sql", sql);
            request.put("args", args == null ? new org.json.JSONArray() : new org.json.JSONArray(args));
            writer.write(request.toString() + "\n");
            writer.flush();
            org.json.JSONObject result = new org.json.JSONObject(reader.readLine());
            if (result.has("error")) throw new IllegalStateException(result.getString("error"));
            return result.getString("value");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
    public synchronized void beginTransaction() {
        if (transactions.isEmpty()) {
            run("exec", "BEGIN IMMEDIATE", null);
            rollback = false;
        }
        transactions.push(false);
    }
    public synchronized void setTransactionSuccessful() {
        transactions.pop();
        transactions.push(true);
    }
    public synchronized void endTransaction() {
        if (!transactions.pop()) rollback = true;
        if (transactions.isEmpty()) run("exec", rollback ? "ROLLBACK" : "COMMIT", null);
    }
    public synchronized boolean inTransaction() {
        return !transactions.isEmpty();
    }
    public void close() {
        process.destroy();
    }
}
