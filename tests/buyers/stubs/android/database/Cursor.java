package android.database;
import java.util.List;
public class Cursor {
    private final List<String[]> rows; private int pos = -1;
    public Cursor(List<String[]> rows) { this.rows = rows; }
    public boolean moveToNext() { if (pos + 1 >= rows.size()) { pos = rows.size(); return false; } pos++; return true; }
    public boolean moveToFirst() { pos = 0; return !rows.isEmpty(); }
    private String cell(int i) { return rows.get(pos)[i]; }
    public boolean isNull(int i) { return cell(i) == null; }
    public String getString(int i) { return cell(i); }
    public long getLong(int i) { String c = cell(i); return c == null ? 0 : (long) Double.parseDouble(c); }
    public int getInt(int i) { return (int) getLong(i); }
    public double getDouble(int i) { String c = cell(i); return c == null ? 0 : Double.parseDouble(c); }
    public void close() {}
}
