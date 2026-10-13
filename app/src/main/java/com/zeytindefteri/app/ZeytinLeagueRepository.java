package com.zeytindefteri.app;

import android.database.Cursor;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Local archive reader plus bounded, read-only server snapshot adapter. No public writes. */
public final class ZeytinLeagueRepository {
    public static final class Region {
        public String id, name;
        public int count;
        public double kg, amount;
        public String dominant = "Belirsiz";
        /** True when this card came from the shared Supabase region directory. */
        public boolean shared;
        /** True when anonymous aggregate metrics passed the server privacy threshold. */
        public boolean aggregatePublished;
        /** True only for the signed-in user's own on-device preview card. */
        public boolean localPreview;
        public final List<ZeytinLeagueScore.Producer> top = new ArrayList<>();
    }
    public static final class Result {
        public ZeytinLeagueScore.Producer local;
        public ZeytinLeagueScore.Producer mine;
        public final List<Region> regions = new ArrayList<>();
        public boolean published, more;
        public long publishedAtMs;
        public String snapshot = "", status = "Yerel önizleme • Bölgesel sıralama henüz yayımlanmadı.";
        public DocumentSnapshot last;
    }
    public interface Callback { void complete(Result result); }

    /** Run on worker thread. Owner is captured before launch to avoid cross-account reads. */
    public Result local(DatabaseHelper db, int year, String uid) {
        Result result = new Result();
        ZeytinLeagueScore.Producer p = new ZeytinLeagueScore.Producer();
        p.id = uid; p.name = "Sen";
        Map<String, Double> weights = new java.util.TreeMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        String[] cols = {"double_kg", "grade1_kg", "grade2_kg", "grade3_kg", "grade4_kg", "oil_kg"};
        try (Cursor c = new ElekRepository(db).getArchiveRecords(year, -1, uid)) {
            while (c.moveToNext()) {
                double kg = value(c, "total_kg");
                p.kg += kg; p.amount += value(c, "total_revenue"); p.records++;
                for (int i = 0; i < 6; i++) p.grades[i] += value(c, cols[i]);
                String district = c.getString(c.getColumnIndexOrThrow("district")).trim();
                String province = c.getString(c.getColumnIndexOrThrow("province")).trim();
                String label = district.isEmpty() ? province : district + (province.isEmpty() ? "" : " / " + province);
                if (label.isEmpty()) label = "Bölge belirtilmemiş";
                String key = regionKey(label);
                labels.put(key, label); weights.put(key, weights.getOrDefault(key, 0d) + kg);
            }
        }
        String primary = ""; double max = -1;
        for (Map.Entry<String, Double> e : weights.entrySet()) if (e.getValue() > max) { primary = e.getKey(); max = e.getValue(); }
        p.region = primary;
        ZeytinLeagueScore.calculate(p, null, null);
        result.local = p;
        if (p.records > 0) {
            Region r = new Region(); r.id = primary; r.name = labels.get(primary);
            r.count = p.eligible ? 1 : 0; r.kg = p.kg; r.amount = p.amount; r.dominant = p.dominant();
            r.localPreview = true;
            // A single private device cannot establish a regional position.
            result.regions.add(r);
        }
        return result;
    }

    /** Read at most 50 region summaries, each containing <=20 server-ranked entries.
     * Personal rank is one private document, even when outside Top 20.
     */
    public void published(int year, String uid, Result fallback, Callback cb) {
        fallback.status = fallback.local == null ? "Yerel kayıtlar okunamadı • Doğrulanmış sonuç bekleniyor."
                : "Yerel önizleme • Bölgesel sıralama henüz yayımlanmadı.";
        if (uid.isEmpty() || FirebaseAuth.getInstance().getCurrentUser() == null
                || !uid.equals(FirebaseAuth.getInstance().getCurrentUser().getUid())) { cb.complete(fallback); return; }
        FirebaseFirestore.getInstance().collection("zeytin_leagues").document(String.valueOf(year))
                .get(Source.SERVER).addOnSuccessListener(d -> {
                    Object pointer = d.get("current_snapshot");
                    String version = pointer instanceof String ? (String) pointer : null;
                    if (!d.exists() || version == null || !version.matches("[A-Za-z0-9_-]{1,100}")) { cb.complete(fallback); return; }
                    FirebaseFirestore.getInstance().document(path(year, version)).get(Source.SERVER)
                            .addOnSuccessListener(meta -> {
                                Object published = meta.get("published_at");
                                com.google.firebase.Timestamp stamp = published instanceof com.google.firebase.Timestamp ? (com.google.firebase.Timestamp) published : null;
                                if (!Boolean.TRUE.equals(meta.get("complete")) || !Long.valueOf(1).equals(meta.get("policy_version"))
                                        || stamp == null || !fresh(stamp.toDate().getTime(), System.currentTimeMillis())) {
                                    fallback.status = "Güncel bölgesel sonuç yok • Yerel önizleme"; cb.complete(fallback); return;
                                }
                                Result live = new Result(); live.local = fallback.local; live.snapshot = version;
                                live.publishedAtMs = stamp.toDate().getTime();
                                FirebaseFirestore.getInstance().document(path(year, version) + "/members/" + uid).get(Source.SERVER)
                                        .addOnSuccessListener(own -> {
                                            try { if (own.exists()) live.mine = producer(own.getData()); }
                                            catch (RuntimeException e) { fallback.status = "Lig verisi doğrulanamadı • Yerel önizleme"; cb.complete(fallback); return; }
                                            page(year, live, null, ok -> { if (ok.published) cb.complete(ok); else { fallback.status = ok.status + " • Yerel önizleme"; cb.complete(fallback); } });
                                        }).addOnFailureListener(e -> { fallback.status = "Bölgesel sonuç alınamadı • Yerel önizleme"; cb.complete(fallback); });
                            }).addOnFailureListener(e -> { fallback.status = "Bölgesel bağlantı kullanılamıyor • Yerel önizleme"; cb.complete(fallback); });
                }).addOnFailureListener(e -> { fallback.status = "Bölgesel bağlantı kullanılamıyor • Yerel önizleme"; cb.complete(fallback); });
    }

    public void page(int year, Result result, DocumentSnapshot after, Callback cb) {
        com.google.firebase.firestore.Query q = FirebaseFirestore.getInstance().collection(path(year, result.snapshot) + "/regions")
                .orderBy(com.google.firebase.firestore.FieldPath.documentId()).limit(50);
        if (after != null) q = q.startAfter(after);
        q.get(Source.SERVER).addOnSuccessListener(s -> {
            try {
                if (!fresh(result.publishedAtMs, System.currentTimeMillis())) {
                    result.published = false; result.status = "Güncel bölgesel sonuç yok"; cb.complete(result); return;
                }
                List<Region> next = new ArrayList<>();
                java.util.HashSet<String> allIds = new java.util.HashSet<>();
                java.util.HashSet<String> regionIds = new java.util.HashSet<>();
                for (Region existing : result.regions) {
                    regionIds.add(existing.id);
                    for (ZeytinLeagueScore.Producer p : existing.top) allIds.add(p.id);
                }
                for (DocumentSnapshot d : s.getDocuments()) {
                    Region r = new Region(); r.id = d.getId(); r.name = d.getString("name");
                    if (!regionIds.add(r.id)) throw new IllegalArgumentException();
                    if (r.name == null || r.name.length() > 150) throw new IllegalArgumentException();
                    r.count = integer(d.getData(), "producer_count"); r.kg = number(d.getData(), "kg"); r.amount = number(d.getData(), "amount");
                    r.dominant = d.getString("dominant");
                    r.shared = true;
                    r.aggregatePublished = true;
                    Object rows = d.get("top");
                    if (!(rows instanceof List) || ((List<?>) rows).size() > 20) throw new IllegalArgumentException();
                    java.util.HashSet<String> ids = new java.util.HashSet<>(); double previous = 101;
                    for (Object row : (List<?>) rows) {
                        if (!(row instanceof Map)) throw new IllegalArgumentException();
                        ZeytinLeagueScore.Producer p = producer((Map<String, Object>) row);
                        if (!ids.add(p.id) || !allIds.add(p.id) || p.rank != r.top.size() + 1 || p.score > previous || !r.id.equals(p.region)) throw new IllegalArgumentException();
                        r.top.add(p); previous = p.score;
                    }
                    if (r.top.size() != Math.min(20, r.count)) throw new IllegalArgumentException();
                    if (result.mine != null && r.id.equals(result.mine.region)) {
                        if (result.mine.rank > r.count) throw new IllegalArgumentException();
                        if (result.mine.rank <= 20) {
                            ZeytinLeagueScore.Producer row = r.top.get(result.mine.rank - 1);
                            if (!row.id.equals(result.mine.id) || Math.abs(row.score - result.mine.score) > .000001
                                    || Math.abs(row.kg - result.mine.kg) > .01
                                    || Math.abs(row.amount - result.mine.amount) > .01) throw new IllegalArgumentException();
                        }
                    }
                    next.add(r);
                }
                result.regions.addAll(next); result.more = s.size() == 50;
                result.last = s.isEmpty() ? after : s.getDocuments().get(s.size() - 1);
                result.published = true; result.status = "Sunucuda yayımlanan sonuçlar • Doğrulanmış kayıtlar";
                cb.complete(result);
            } catch (RuntimeException e) { result.status = "Lig verisi doğrulanamadı"; cb.complete(result); }
        }).addOnFailureListener(e -> { result.status = "Bölgeler yüklenemedi; tekrar deneyebilirsin."; cb.complete(result); });
    }
    /** Observe only the small active-generation pointer, never all producer records. */
    public com.google.firebase.firestore.ListenerRegistration watch(int year, java.util.function.Consumer<String> changed) {
        return FirebaseFirestore.getInstance().collection("zeytin_leagues").document(String.valueOf(year))
                .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE, (snapshot, error) -> {
                    if (error != null) { changed.accept(null); return; }
                    if (snapshot == null || snapshot.getMetadata().isFromCache()) { changed.accept(null); return; }
                    Object value = snapshot.get("current_snapshot");
                    changed.accept(value instanceof String ? (String) value : "");
                });
    }
    private static String path(int year, String version) { return "zeytin_leagues/" + year + "/snapshots/" + version; }
    public static boolean fresh(long publishedAt, long now) {
        return publishedAt > 0 && Math.abs(now - publishedAt) <= 86400000L;
    }
    private static ZeytinLeagueScore.Producer producer(Map<String, Object> m) {
        ZeytinLeagueScore.Producer p = new ZeytinLeagueScore.Producer();
        p.id = string(m, "producer_id"); p.region = string(m, "region_id");
        p.rank = integer(m, "rank"); p.kg = number(m, "kg"); p.amount = number(m, "amount"); p.score = number(m, "score");
        if (p.rank < 1 || p.kg < ZeytinLeagueScore.MIN_KG || p.score > 100) throw new IllegalArgumentException();
        Object values = m.get("grades"); if (!(values instanceof List) || ((List<?>) values).size() != 6) throw new IllegalArgumentException();
        double sum = 0;
        for (int i = 0; i < 6; i++) {
            Object n = ((List<?>) values).get(i); if (!(n instanceof Number)) throw new IllegalArgumentException();
            p.grades[i] = ((Number)n).doubleValue();
            if (!ZeytinLeagueScore.finite(p.grades[i]) || p.grades[i] < 0) throw new IllegalArgumentException();
            sum += p.grades[i];
        }
        if (Math.abs(sum - p.kg) > Math.max(.01, p.kg * .000001)) throw new IllegalArgumentException();
        p.name = "Üretici " + p.rank; p.eligible = true; return p;
    }
    private static String string(Map<String, Object> m, String k) {
        Object v = m.get(k); if (!(v instanceof String) || ((String)v).isEmpty() || ((String)v).length() > 150) throw new IllegalArgumentException(); return (String)v;
    }
    private static int integer(Map<String, Object> m, String k) {
        double d = number(m, k); if (d != Math.floor(d) || d > Integer.MAX_VALUE) throw new IllegalArgumentException(); return (int)d;
    }
    private static double number(Map<String, Object> m, String k) {
        Object v = m.get(k); if (!(v instanceof Number)) throw new IllegalArgumentException();
        double n = ((Number)v).doubleValue(); if (!ZeytinLeagueScore.finite(n) || n < 0) throw new IllegalArgumentException(); return n;
    }
    private static double value(Cursor c, String column) { return c.getDouble(c.getColumnIndexOrThrow(column)); }
    public static String regionKey(String label) {
        // Match the server's province_district key for complete regions.
        // Incomplete local locations retain their private fallback identity.
        String[] parts = label.split("/", -1);
        if (parts.length == 2) {
            String district = regionPart(parts[0]), province = regionPart(parts[1]);
            if (!district.isEmpty() && !province.isEmpty()) return province + "_" + district;
        }
        String normalized = Normalizer.normalize(label.trim().replaceAll("\\s+", " ").toLowerCase(new Locale("tr", "TR")), Normalizer.Form.NFC);
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder();
            for (byte b : hash) key.append(String.format(Locale.ROOT, "%02x", b & 255));
            return key.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String regionPart(String value) {
        String lower = value.trim().toLowerCase(new Locale("tr", "TR")).replace('ı', 'i');
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

}
