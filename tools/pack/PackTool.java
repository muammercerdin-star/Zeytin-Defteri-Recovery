package com.zeytindefteri.app;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Başlangıç sözlüğünden (res/raw/baslangic_sozleri.tsv) gömülü bilgi paketini (res/raw/bilgi_paketi.json) üretir.
 * Kökler uygulamanın kendi kodundan (LearnedTerms.verbRoot) hesaplanır. Her söz geçerli olmalı ve paket kontrol
 * bankasını bozmamalı; değilse dosya yazılmaz. Kullanım: PackTool <kök> [--check].
 */
public class PackTool {
    public static void main(String[] a) throws Exception {
        Path root = Paths.get(a[0]);
        boolean check = a.length > 1 && "--check".equals(a[1]);
        Path raw = root.resolve("app/src/main/res/raw");
        String text = generate(new String(Files.readAllBytes(raw.resolve("baslangic_sozleri.tsv")), StandardCharsets.UTF_8));
        KnowledgePack pack = KnowledgePack.read(text, KnowledgePack.PUBLIC_KEYS);
        if (pack == null) {
            fail("paket okunamadı");
        }
        String bank = new String(Files.readAllBytes(raw.resolve("kontrol_bankasi.tsv")), StandardCharsets.UTF_8);
        List<String> broken = PackGate.broken(pack, PackGate.sentences(bank));
        if (!broken.isEmpty()) {
            fail("paket kontrol bankasını bozuyor: " + broken);
        }
        Path out = raw.resolve("bilgi_paketi.json");
        if (check) {
            boolean same = Files.exists(out) && new String(Files.readAllBytes(out), StandardCharsets.UTF_8).equals(text);
            System.out.println((same ? "PASS" : "FAIL") + " bilgi_paketi.json başlangıç sözlüğüyle aynı (" + pack.terms.size() + " söz)");
            if (!same) {
                System.exit(1);
            }
            return;
        }
        Files.write(out, text.getBytes(StandardCharsets.UTF_8));
        System.out.println("yazıldı: " + out + " · " + pack.terms.size() + " söz · sürüm " + pack.version);
    }

    /** TSV → paket metni. "# sürüm: N", "# tarih: yyyy-MM-dd" başlıktan. */
    static String generate(String tsv) throws Exception {
        int version = 0;
        String date = "";
        List<String[]> rows = new ArrayList<>();
        for (String line : tsv.split("\n")) {
            String l = line.trim();
            if (l.startsWith("# sürüm:")) {
                version = Integer.parseInt(l.substring(8).trim());
            } else if (l.startsWith("# tarih:")) {
                date = l.substring(8).trim();
            }
            if (l.isEmpty() || l.startsWith("#")) {
                continue;
            }
            String[] c = line.split("\t");
            String[] row = c.length == 2 ? KnowledgePack.row(c[0].trim(), c[1].trim(), "") : null;
            if (row == null) {
                fail("geçersiz satır: " + line);
            }
            rows.add(row);
        }
        if (version <= 0 || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            fail("başlıkta sürüm ya da tarih yok");
        }
        return KnowledgePack.write(version, date, KnowledgePack.EMBEDDED, rows, 0, "") + "\n";
    }

    private static void fail(String why) {
        System.out.println("FAIL " + why);
        System.exit(1);
    }
}
