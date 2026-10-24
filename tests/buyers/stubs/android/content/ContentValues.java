package android.content;
import java.util.LinkedHashMap;
import java.util.Map;
public class ContentValues {
    public final Map<String, Object> values = new LinkedHashMap<>();
    public void put(String k, String v) { values.put(k, v); }
    public void put(String k, Long v) { values.put(k, v); }
    public void put(String k, Integer v) { values.put(k, v); }
    public void put(String k, Double v) { values.put(k, v); }
    public void putNull(String k) { values.put(k, null); }
    public int size() { return values.size(); }
    public String getAsString(String k) { Object v = values.get(k); return v == null ? null : String.valueOf(v); }
}
