package com.zeytindefteri.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cümle bankası sınayıcısı (İP1; İP3'ten beri uygulamanın kendi beynini sınar). Her cümle asistanın beynine
 * (İP4: öğrenilen sözler → RuleBrain; sözlük boş), bahçesi olan
 * ve internet yardımcısı kapalı bir kullanıcının asistanına yeni söylenmiş gibi verilir (taze sohbet durumu);
 * karar cumleler.tsv'deki beklenenle karşılaştırılır. Test tarafında ayrı bir karar zinciri yoktur.
 * ELEK alanlarında kilo başı fiyat "<boy>_fiyat" (TL/kg) olarak yazılır (İP2).
 *
 * Kullanım:
 *   java ... SentenceBankRunner cumleler.tsv       → karşılaştırır, özet yazar; "dogru" bozulursa çıkış 1
 *   java ... SentenceBankRunner --dump < cumleler  → her satır için "cümle TAB eylem TAB alanlar" yazar
 */
public final class SentenceBankRunner {

    static final String MISS = ActionCatalog.ANLASILMADI;
    private static final RuleBrain RULES = new RuleBrain();

    private SentenceBankRunner() {
    }

    /** Bir cümlenin bugünkü sonucu: eylem ve alanlar ("anahtar=değer;..."; alan yoksa "-"). */
    static final class Outcome {
        final String action;
        final String fields;
        final Decision decision;

        Outcome(Decision d) {
            this.decision = d;
            this.action = d.action;
            this.fields = d.fieldsText();
        }

        boolean understood() {
            return !MISS.equals(action);
        }
    }

    /** Sözlük boşken (yeni kurulmuş telefon): öğrenilen sözler beyni kurallarla birebir aynı karar verir. */
    static Outcome route(String raw) {
        return route(raw, new LearnedTerms.Memory());
    }

    /** Verilen sözlükle: asistanın beyni (öğrenilen sözler → kurallar). */
    static Outcome route(String raw, LearnedTerms.Store store) {
        BrainContext context = BrainContext.full(true, AssistantPersona.decode("")::handle);
        return new Outcome(new LearnedBrain(store, RULES).decide(new Brain.Input(raw), context));
    }

    // ============================================================ BANKA

    static final class Row {
        final int line;
        final String sentence;
        final String action;
        final String fields;
        final String status;
        final String source;

        Row(int line, String[] c) {
            this.line = line;
            this.sentence = c[0];
            this.action = c[1];
            this.fields = c[2];
            this.status = c[3];
            this.source = c[4];
        }
    }

    static List<Row> read(String path, List<String> errors) throws IOException {
        List<Row> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<String> lines = Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty() || line.startsWith("#") || line.startsWith("cumle\t")) {
                continue;
            }
            String[] c = line.split("\t", -1);
            if (c.length != 5) {
                errors.add("satır " + (i + 1) + ": 5 sütun olmalı, " + c.length + " var");
                continue;
            }
            if (!c[3].equals("dogru") && !c[3].equals("beklenen_hata")) {
                errors.add("satır " + (i + 1) + ": durum dogru|beklenen_hata olmalı: " + c[3]);
                continue;
            }
            if (!seen.add(c[0])) {
                errors.add("satır " + (i + 1) + ": aynı cümle iki kez: " + c[0]);
                continue;
            }
            rows.add(new Row(i + 1, c));
        }
        return rows;
    }

    /** Karşılaştırma sonucu; özet ve çıkış kodu buradan. */
    static final class Report {
        int total;
        int correct;
        int expectedErrors;
        int understood;
        final List<String> broken = new ArrayList<>();
        final List<String> fixed = new ArrayList<>();

        String summary() {
            long pct = total == 0 ? 0 : Math.round(100.0 * understood / total);
            return total + " cümle · " + correct + " doğru · " + expectedErrors + " beklenen hata · anlaşılma %" + pct;
        }
    }

    static Report check(List<Row> rows) {
        Report r = new Report();
        for (Row row : rows) {
            Outcome got = route(row.sentence);
            boolean same = got.action.equals(row.action) && got.fields.equals(row.fields);
            r.total++;
            if (got.understood()) {
                r.understood++;
            }
            if (row.status.equals("dogru")) {
                r.correct++;
                if (!same) {
                    r.broken.add(describe(row, got));
                }
            } else {
                r.expectedErrors++;
                if (same) {
                    r.fixed.add(describe(row, got));
                }
            }
        }
        return r;
    }

    private static String describe(Row row, Outcome got) {
        return "satır " + row.line + " «" + row.sentence + "» beklenen " + row.action + " " + row.fields
                + " · bugün " + got.action + " " + got.fields + " (" + row.source + ")";
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 1 && args[0].equals("--dump")) {
            dump();
            return;
        }
        String path = args.length > 0 ? args[0] : "tests/sentence_bank/cumleler.tsv";
        List<String> errors = new ArrayList<>();
        List<Row> rows = read(path, errors);
        Report r = check(rows);
        for (String e : errors) {
            System.out.println("FAIL dosya " + e);
        }
        for (String b : r.broken) {
            System.out.println("FAIL bozuldu: " + b);
        }
        for (String x : r.fixed) {
            System.out.println("UYARI düzeldi (durumu dogru yap): " + x);
        }
        System.out.println(r.summary());
        boolean fail = !errors.isEmpty() || !r.broken.isEmpty() || rows.size() < 150;
        if (rows.size() < 150) {
            System.out.println("FAIL banka en az 150 cümle olmalı");
        }
        if (fail) {
            System.exit(1);
        }
        System.out.println("PASS cümle bankası");
    }

    private static void dump() throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }
            Outcome o = route(line);
            System.out.println(line + "\t" + o.action + "\t" + o.fields);
        }
    }
}
