package com.zeytindefteri.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Birebir aynılık (İP3): RuleBrain, taşımadan önceki asistan zinciriyle (LegacyRouter, commit f5ccb3a) her cümlede
 * ve her konuşma durumunda aynı kararı vermeli: eylem + alanlar + yürütme değişkesi. Tek fark bile varsa KALIR.
 *
 * Cümleler: cümle bankasının tamamı + testlerdeki bütün metinler (binlerce; sorular, elek, işlem, Tezgâh, sohbet...).
 * Durumlar: açık soru yok / açık işlem sorusu / açık kayıt sorusu × internet izni bekleniyor mu × bahçe var mı.
 */
public class BrainEquivalenceTest {

    public static void main(String[] args) throws IOException {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        List<String> sentences = corpus(root);
        RuleBrain brain = new RuleBrain();
        boolean[][] dialogs = {{true, true}, {false, true}, {true, false}};   // {opDialogNull, recDialogNull}
        long compared = 0;
        int diffs = 0;
        int bank = bankSize(root);
        for (String raw : sentences) {
            for (boolean[] dialog : dialogs) {
                for (boolean pending : new boolean[]{false, true}) {
                    for (boolean gardens : new boolean[]{true, false}) {
                        LegacyRouter.Ctx old = new LegacyRouter.Ctx();
                        old.opDialogNull = dialog[0];
                        old.recDialogNull = dialog[1];
                        old.pendingCloud = pending;
                        old.gardensEmpty = !gardens;
                        String[] want = LegacyRouter.route(raw, old);
                        boolean close = dialog[0] && dialog[1];
                        boolean commands = dialog[1];
                        BrainContext ctx = BrainContext.of(close, commands, pending, gardens, AssistantPersona.decode("")::handle);
                        Decision got = brain.decide(new Brain.Input(raw), ctx);
                        compared++;
                        boolean same = want[0].equals(got.action) && want[1].equals(got.fieldsText()) && want[2].equals(got.variant);
                        if (!same) {
                            diffs++;
                            if (diffs <= 40) {
                                System.out.println("FARK «" + raw + "» durum[op=" + !dialog[0] + " kayıt=" + !dialog[1] + " izin=" + pending
                                        + " bahçe=" + gardens + "] eski " + String.join(" ", want) + " · yeni " + got);
                            }
                        }
                    }
                }
            }
        }
        System.out.println(sentences.size() + " cümle (" + bank + " cümle bankası) · " + compared + " karşılaştırma · " + diffs + " fark");
        if (diffs > 0) {
            System.out.println("FAIL birebir aynılık");
            System.exit(1);
        }
        System.out.println("PASS birebir aynılık: RuleBrain eski zincirle her cümlede aynı");
    }

    static int bankSize(Path root) throws IOException {
        int n = 0;
        for (String line : Files.readAllLines(root.resolve("tests/sentence_bank/cumleler.tsv"), StandardCharsets.UTF_8)) {
            if (!line.isEmpty() && !line.startsWith("#") && !line.startsWith("cumle\t")) {
                n++;
            }
        }
        return n;
    }

    static List<String> corpus(Path root) throws IOException {
        Set<String> out = new LinkedHashSet<>();
        for (String line : Files.readAllLines(root.resolve("tests/sentence_bank/cumleler.tsv"), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("cumle\t")) {
                continue;
            }
            out.add(line.split("\t", -1)[0]);
        }
        Pattern literal = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root.resolve("tests"))) {
            walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("brain_equivalence"))
                    .sorted()
                    .forEach(files::add);
        }
        for (Path f : files) {
            String text = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
            Matcher m = literal.matcher(text);
            while (m.find()) {
                String s = m.group(1);
                if (s.contains("\\") || s.length() < 2 || s.length() > 200) {
                    continue;
                }
                out.add(s);
            }
        }
        return new ArrayList<>(out);
    }
}
