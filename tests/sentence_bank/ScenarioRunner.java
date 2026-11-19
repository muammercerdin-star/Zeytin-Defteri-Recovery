package com.zeytindefteri.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Öğretme senaryoları (İP4): her senaryo boş sözlükle başlar; öğret / unut / vazgeç / cümle / sor adımları sırayla.
 * Asistanın kendi parçaları kullanılır: LearnedTerms (öğretme, sorulacak söz), LearnedBrain (karar, unut komutu).
 */
public final class ScenarioRunner {
    private ScenarioRunner() {
    }

    public static void main(String[] args) throws IOException {
        String path = args.length > 0 ? args[0] : "tests/sentence_bank/senaryolar.tsv";
        List<String> lines = Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8);
        String current = "";
        LearnedTerms.Store store = new LearnedTerms.Memory();
        int steps = 0;
        int bad = 0;
        int scenarios = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty() || line.startsWith("#") || line.startsWith("senaryo\t")) {
                continue;
            }
            String[] c = line.split("\t", -1);
            if (c.length != 6) {
                System.out.println("FAIL satır " + (i + 1) + ": 6 sütun olmalı");
                bad++;
                continue;
            }
            if (!c[0].equals(current)) {
                current = c[0];
                store = new LearnedTerms.Memory();
                scenarios++;
            }
            steps++;
            String got = step(c[1], c[2], store);
            String want = c[3] + " " + c[4];
            if (!got.equals(want)) {
                bad++;
                System.out.println("FAIL " + c[0] + " satır " + (i + 1) + " «" + c[2] + "» beklenen " + want + " · bugün " + got);
            }
        }
        System.out.println(scenarios + " senaryo · " + steps + " adım · " + (steps - bad) + " doğru");
        if (bad > 0) {
            System.exit(1);
        }
        System.out.println("PASS öğretme senaryoları");
    }

    static String step(String kind, String text, LearnedTerms.Store store) {
        switch (kind) {
            case "ogret": {
                String[] p = text.split("\\|");
                LearnedTerms.Choice choice = LearnedTerms.choiceFor(p[1]);
                store.put(LearnedTerms.teach(p[0], choice, LearnedTerms.SOURCE_VOICE, 1));
                return "- -";
            }
            case "vazgec":
                store.decline(LearnedTerms.sentenceKey(text));
                return "- -";
            case "unut": {
                SentenceBankRunner.Outcome o = SentenceBankRunner.route(text, store);
                String target = LearnedTerms.forgetTarget(text);
                store.remove(LearnedTerms.verbRoot(target == null ? "" : target));
                return o.action + " " + o.fields;
            }
            case "sor": {
                SentenceBankRunner.Outcome o = SentenceBankRunner.route(text, store);
                String word = o.understood() ? null : LearnedTerms.teachable(text, store);
                return word == null ? "SORMA -" : "SOR " + word;
            }
            default: {
                SentenceBankRunner.Outcome o = SentenceBankRunner.route(text, store);
                return o.action + " " + o.fields;
            }
        }
    }
}
