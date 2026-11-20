package com.zeytindefteri.app;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Bilgi paketi (İP8; saf, testli). Yeterince çiftçinin öğrettiği sözler herkese bu paketle gelir.
 *
 * Biçim (JSON, sürümlü): {"bicim":1, "surum":N, "tarih":"yyyy-MM-dd", "kaynak":"gomulu"|"indirilen",
 * "sozler":[{"kok","soz","eylem","tur","not","bolge"}...], "ozet":"<SHA-256>", "anahtar":1|2, "imza":"<onaltılık>"}.
 * Özet ve imza, sabit sıralı metin üzerinden alınır (canonical()).
 *
 * Doğrulama: özet tutmalı; indirilen paketin ayrıca iki açık anahtar yuvasından biriyle ECDSA P-256 (SHA256withECDSA)
 * imzası tutmalı. İP8-A'da iki yuva da BOŞ: indirilen hiçbir paket kabul edilmez (gömülü paket uygulamanın kendi
 * imzasıyla korunur, özeti yeter). Her söz: kök sözle tutmalı, anlam bir öğretme seçimine karşılık gelmeli, genel fiil
 * olmamalı. Bozuk, imzası tutmayan ya da cümle bankasını bozan paket (PackGate) uygulanmaz; bir öncekiyle devam edilir.
 */
final class KnowledgePack {
    static final int FORMAT = 1;
    static final String KIND = "paket";
    static final String EMBEDDED = "gomulu";
    static final String DOWNLOADED = "indirilen";

    /**
     * İndirilen paketlerin imzasını doğrulayan açık anahtarlar (X.509 DER, onaltılık). İki yuva: anahtar değişiminde
     * eskisi ve yenisi birlikte durur. İP8-A'da ikisi de boş; İP8-B'de doldurulur (docs/ortak-dongu-anahtar.md).
     */
    static final String[] PUBLIC_KEYS = {"", ""};

    final int version;
    final String date;
    final String source;
    final List<LearnedTerms.Term> terms;
    final String digest;

    private KnowledgePack(int version, String date, String source, List<LearnedTerms.Term> terms, String digest) {
        this.version = version;
        this.date = date;
        this.source = source;
        this.terms = Collections.unmodifiableList(terms);
        this.digest = digest;
    }

    /** Doğrulanmış paket ya da null (bozuk, özeti / imzası tutmuyor, sözlerinden biri geçersiz). keys: açık anahtarlar. */
    static KnowledgePack read(String json, String[] keys) {
        try {
            JSONObject o = new JSONObject(json);
            if (o.getInt("bicim") != FORMAT) {
                return null;
            }
            int version = o.getInt("surum");
            String date = o.getString("tarih");
            String source = o.getString("kaynak");
            JSONArray words = o.getJSONArray("sozler");
            List<String[]> rows = new ArrayList<>();
            for (int i = 0; i < words.length(); i++) {
                JSONObject w = words.getJSONObject(i);
                rows.add(new String[]{w.getString("kok"), w.getString("soz"), w.getString("eylem"), w.getString("tur"),
                        w.optString("not", ""), w.optString("bolge", "")});
            }
            String canonical = canonical(version, date, source, rows);
            String digest = sha256(canonical);
            if (!digest.equals(o.getString("ozet"))) {
                return null;
            }
            if (!EMBEDDED.equals(source)) {
                if (!DOWNLOADED.equals(source) || !signed(canonical, o.optInt("anahtar", 0), o.optString("imza", ""), keys)) {
                    return null;
                }
            }
            List<LearnedTerms.Term> terms = new ArrayList<>();
            for (String[] r : rows) {
                LearnedTerms.Term t = term(r);
                if (t == null) {
                    return null;
                }
                terms.add(t);
            }
            return new KnowledgePack(version, date, source, terms, digest);
        } catch (Exception e) {
            return null;
        }
    }

    /** Paket sözü: kök sözden çıkmalı, söz temizleyicinin söz kuralına uymalı, anlam bir seçim olmalı, genel olmamalı. */
    private static LearnedTerms.Term term(String[] r) {
        String word = ContributionCleaner.word(r[1]);
        if (word.isEmpty() || !LearnedTerms.verbRoot(word).equals(r[0]) || LearnedTerms.generic(r[0])) {
            return null;
        }
        String field = ActionCatalog.HASAT.equals(r[2]) ? "" : "tur";
        LearnedTerms.Term t = new LearnedTerms.Term(r[0], word, r[2], field, r[3], r[4], KIND, KIND, 0, 0);
        return LearnedTerms.choiceOf(t) == null ? null : t;
    }

    /** Özet ve imzanın alındığı sabit metin. */
    static String canonical(int version, String date, String source, List<String[]> rows) {
        StringBuilder b = new StringBuilder("zd-bilgi-paketi|").append(FORMAT).append('|').append(version).append('|')
                .append(date).append('|').append(source);
        for (String[] r : rows) {
            b.append('\n').append(String.join("\t", r));
        }
        return b.toString();
    }

    static boolean signed(String canonical, int slot, String signatureHex, String[] keys) {
        if (keys == null || slot < 1 || slot > keys.length) {
            return false;
        }
        String key = keys[slot - 1];
        if (key == null || key.isEmpty() || signatureHex.isEmpty()) {
            return false;
        }
        try {
            PublicKey pub = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(fromHex(key)));
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initVerify(pub);
            s.update(canonical.getBytes(StandardCharsets.UTF_8));
            return s.verify(fromHex(signatureHex));
        } catch (Exception e) {
            return false;
        }
    }

    static String sha256(String text) throws Exception {
        return CommunityQueue.hex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    static byte[] fromHex(String h) {
        if (h.length() % 2 != 0 || !h.matches("[0-9a-fA-F]*")) {
            throw new IllegalArgumentException("onaltılık değil");
        }
        byte[] b = new byte[h.length() / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        }
        return b;
    }

    /** Paket metni üretir (araç ve testler için; imza ayrıca eklenir). */
    static String write(int version, String date, String source, List<String[]> rows, int slot, String signatureHex) throws Exception {
        JSONObject o = new JSONObject();
        o.put("bicim", FORMAT);
        o.put("surum", version);
        o.put("tarih", date);
        o.put("kaynak", source);
        JSONArray words = new JSONArray();
        for (String[] r : rows) {
            JSONObject w = new JSONObject();
            w.put("kok", r[0]);
            w.put("soz", r[1]);
            w.put("eylem", r[2]);
            w.put("tur", r[3]);
            w.put("not", r[4]);
            w.put("bolge", r[5]);
            words.put(w);
        }
        o.put("sozler", words);
        o.put("ozet", sha256(canonical(version, date, source, rows)));
        if (slot > 0) {
            o.put("anahtar", slot);
            o.put("imza", signatureHex);
        }
        return o.toString(1);
    }

    /** "bakırladık" + düğme adı ("ilaçlama") → paket satırı (kök, söz, eylem, tür, not, bölge); olmazsa null. */
    static String[] row(String word, String meaning, String region) {
        LearnedTerms.Choice c = LearnedTerms.choiceFor(meaning);
        String w = ContributionCleaner.word(word);
        String root = LearnedTerms.verbRoot(w);
        if (c == null || w.isEmpty() || root.isEmpty()) {
            return null;
        }
        return new String[]{root, w, c.action, c.value, c.note, region == null ? "" : region};
    }
}
