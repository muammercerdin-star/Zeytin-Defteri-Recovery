package com.zeytindefteri.app;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Sunucunun ürettiği paket telefonun okuduğu biçimle birebir aynı mı? (ortak.paket_uret → KnowledgePack.read) */
public class SqlPackTest {
    static int bad = 0;

    static void check(boolean ok, String m) {
        System.out.println((ok ? "PASS " : "FAIL ") + m);
        if (!ok) {
            bad++;
        }
    }

    public static void main(String[] a) throws Exception {
        JSONObject o = new JSONObject(new String(Files.readAllBytes(Paths.get(a[0])), StandardCharsets.UTF_8).trim());
        JSONArray words = o.getJSONArray("sozler");
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < words.length(); i++) {
            JSONObject w = words.getJSONObject(i);
            rows.add(new String[]{w.getString("kok"), w.getString("soz"), w.getString("eylem"), w.getString("tur"),
                    w.getString("not"), w.getString("bolge")});
        }
        check(rows.size() == 2, "sunucu paketi: 2 onaylı söz (" + rows.size() + ")");
        String canonical = KnowledgePack.canonical(o.getInt("surum"), o.getString("tarih"), o.getString("kaynak"), rows);
        check(canonical.equals(o.getString("imzalanacak")), "imzalanacak metin telefondaki canonical() ile aynı");
        check(KnowledgePack.sha256(canonical).equals(o.getString("ozet")), "SQL'in SHA-256 özeti telefonunkiyle aynı");
        check(KnowledgePack.read(o.toString(), KnowledgePack.PUBLIC_KEYS) == null, "imzasız sunucu paketi telefonda reddedilir");
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair k = g.generateKeyPair();
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(k.getPrivate());
        s.update(o.getString("imzalanacak").getBytes(StandardCharsets.UTF_8));
        String signed = KnowledgePack.write(o.getInt("surum"), o.getString("tarih"), o.getString("kaynak"), rows, 1,
                CommunityQueue.hex(s.sign()));
        String[] keys = {CommunityQueue.hex(k.getPublic().getEncoded()), ""};
        KnowledgePack p = KnowledgePack.read(signed, keys);
        check(p != null && p.terms.size() == 2, "test anahtarıyla imzalanan sunucu paketi telefonda okunur");
        check(p != null && p.source.equals(KnowledgePack.DOWNLOADED), "kaynak: indirilen");
        if (bad > 0) {
            System.exit(1);
        }
    }
}
