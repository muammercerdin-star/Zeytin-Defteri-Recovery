package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bulut yedeği dizini (saf, Android'siz). Her telefon yalnız kendi bölmesine yazar; böylece yeni ya da
 * ikinci telefon başka telefonun yedeğini ezemez. Dizin belgesi ("index") hangi telefonda hangi yedeğin
 * olduğunu tutar:
 *
 *   users/{uid}/tezgah_backup/index    devices: { a1b2c3…: {created, parts, bytes, hash, summary, name, rows} }
 *   users/{uid}/tezgah_backup/da1b2c3…_p0 …  o telefonun yedek parçaları
 *   users/{uid}/tezgah_backup/meta     en son yedek (eski sürüm uyumu)
 *   users/{uid}/tezgah_backup/part_0 … ilk sürümün yedeği (bölmesiz; "eski yedek" olarak listelenir)
 *
 * En fazla {@link #MAX_DEVICES} telefon bölmesi tutulur; fazlası olursa en eski yedek silinir.
 */
public final class TezgahCloudIndex {
    public static final int MAX_DEVICES = 5;
    /** İlk sürümün bölmesiz yedeği. */
    public static final String LEGACY = "legacy";

    private TezgahCloudIndex() {}

    public static class Entry {
        public String device = "", name = "", summary = "", hash = "";
        public long created, bytes;
        public int parts, rows;
        /** Bu telefonun yedeği mi? */
        public boolean mine;

        /** Bu yedeğin parça belgesinin adı. */
        public String partDoc(int i) { return TezgahCloudIndex.partDoc(device, i); }
    }

    public static String partDoc(String device, int i) {
        return device == null || device.isEmpty() || LEGACY.equals(device) ? "part_" + i : "d" + device + "_p" + i;
    }

    /** Cihaz kimliği belge adına girer: yalnız küçük harf/rakam, en fazla 16. */
    public static String cleanId(String raw) {
        StringBuilder b = new StringBuilder();
        if (raw != null) for (char c : raw.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) b.append(c);
            if (b.length() == 16) break;
        }
        return b.toString();
    }

    /** Dizin belgesindeki "devices" haritasını okur (en yeni yedek başta). */
    public static List<Entry> parse(Map<String, Object> devices, String myDevice) {
        List<Entry> out = new ArrayList<>();
        if (devices == null) return out;
        for (Map.Entry<String, Object> e : devices.entrySet()) {
            if (!(e.getValue() instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) e.getValue();
            Entry x = new Entry();
            x.device = e.getKey();
            x.created = num(m.get("created"));
            x.parts = (int) num(m.get("parts"));
            x.bytes = num(m.get("bytes"));
            x.rows = (int) num(m.get("rows"));
            x.hash = str(m.get("hash"));
            x.summary = str(m.get("summary"));
            x.name = str(m.get("name"));
            x.mine = x.device.equals(myDevice);
            if (x.parts > 0) out.add(x);
        }
        sort(out);
        return out;
    }

    /** Bölmesiz eski "meta" belgesi dizinde yoksa listeye "eski yedek" olarak eklenir. */
    public static void addLegacy(List<Entry> list, Map<String, Object> meta) {
        if (meta == null || !str(meta.get("device")).isEmpty()) return; // yeni sürümün meta'sı: zaten dizinde
        for (Entry e : list) if (LEGACY.equals(e.device)) return;
        Entry x = new Entry();
        x.device = LEGACY;
        x.created = num(meta.get("created"));
        x.parts = (int) num(meta.get("parts"));
        x.bytes = num(meta.get("bytes"));
        x.hash = str(meta.get("hash"));
        x.summary = str(meta.get("summary"));
        x.name = "Önceki yedek";
        if (x.parts > 0) { list.add(x); sort(list); }
    }

    public static Entry latest(List<Entry> list) { return list == null || list.isEmpty() ? null : list.get(0); }

    public static Entry mine(List<Entry> list) {
        if (list != null) for (Entry e : list) if (e.mine) return e;
        return null;
    }

    /**
     * Başka telefonun, bu telefonun son bildiği andan (son yedek ya da geri yükleme) daha yeni ve farklı bir
     * yedeği var mı? Varsa onu döndürür (uyarı: "başka telefonda daha yeni yedek var").
     */
    public static Entry newerElsewhere(List<Entry> list, long myKnownAt, String myHash) {
        if (list == null) return null;
        for (Entry e : list) {
            if (e.mine || LEGACY.equals(e.device)) continue;
            if (e.created > myKnownAt && !e.hash.equals(myHash)) return e;
        }
        return null;
    }

    /**
     * Bu telefon yazınca toplam bölme sayısı {@link #MAX_DEVICES}'ı geçmesin: diğer telefonlardan en yeni
     * MAX_DEVICES-1 tanesi kalır, daha eskileri silinecekler listesine girer (eski yedek de sayılır).
     */
    public static List<Entry> evict(List<Entry> list, String myDevice) {
        List<Entry> others = new ArrayList<>();
        if (list != null) for (Entry e : list) if (!e.device.equals(myDevice)) others.add(e);
        sort(others);
        List<Entry> out = new ArrayList<>();
        for (int i = MAX_DEVICES - 1; i < others.size(); i++) out.add(others.get(i));
        return out;
    }

    /** Dizin belgesine yazılacak bu telefonun satırı. */
    public static Map<String, Object> row(long created, int parts, long bytes, String hash, String summary, String name, int rows) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("created", created);
        m.put("parts", (long) parts);
        m.put("bytes", bytes);
        m.put("hash", hash);
        m.put("summary", summary);
        m.put("name", name == null ? "" : name);
        m.put("rows", (long) rows);
        return m;
    }

    /** Listede gösterilecek ad: "Bu telefon", "Samsung SM-A515F", "Önceki yedek". */
    public static String label(Entry e) {
        if (e.mine) return "Bu telefon";
        if (e.name == null || e.name.trim().isEmpty()) return "Başka telefon";
        return e.name.trim();
    }

    private static void sort(List<Entry> list) {
        Collections.sort(list, (a, b) -> Long.compare(b.created, a.created));
    }

    private static long num(Object o) { return o instanceof Number ? ((Number) o).longValue() : 0; }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
}
