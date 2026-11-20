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
        // İP4: öğrenilen sözler beyni sözlük boşken kurallarla birebir aynı karar vermeli.
        LearnedBrain empty = new LearnedBrain(new LearnedTerms.Memory(), brain);
        int learnedDiffs = 0;
        int newCommand = 0;
        int noteAdded = 0;
        int planned = 0;
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
                        Decision viaLearned = empty.decide(new Brain.Input(raw), BrainContext.of(close, commands, pending, gardens, AssistantPersona.decode("")::handle));
                        boolean sameLearned = want[0].equals(viaLearned.action) && want[1].equals(viaLearned.fieldsText()) && want[2].equals(viaLearned.variant);
                        // Tek bilinçli fark: İP4'ün yeni komutu "öğrendiklerini söyle" (sözlük boşken "henüz yok" der).
                        if (!sameLearned && commands && LearnedTerms.asksList(raw) && viaLearned.is(ActionCatalog.SOZLER_GOSTER)) {
                            sameLearned = true;
                            newCommand++;
                        }
                        // İP5: "Diğer" işinin adı yeni "not" alanında ("ot biçtik" → tur=Diğer;not=Ot biçme); geri kalanı aynı.
                        if (!sameLearned && onlyNote(want, viaLearned)) {
                            sameLearned = true;
                        }
                        if (!sameLearned && newPlan(want, viaLearned)) {
                            sameLearned = true;
                        }
                        if (!sameLearned) {
                            learnedDiffs++;
                            if (learnedDiffs <= 20) {
                                System.out.println("FARK (boş sözlük) «" + raw + "» eski " + String.join(" ", want) + " · yeni " + viaLearned);
                            }
                        }
                        boolean same = want[0].equals(got.action) && want[1].equals(got.fieldsText()) && want[2].equals(got.variant);
                        if (!same && onlyNote(want, got)) {
                            same = true;
                            noteAdded++;
                        }
                        if (!same && newPlan(want, got)) {
                            same = true;
                            planned++;
                        }
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
        System.out.println("Öğrenilen sözler beyni, boş sözlük: " + compared + " karşılaştırma · " + learnedDiffs + " fark"
                + " (yeni komut \"öğrendiklerini söyle\": " + newCommand + ")");
        System.out.println("İP7 + eğitim turu 1 bilinçli fark: kayıt yolu tutmayan cümle satış / tahsilat / yağhane / hatırlatma oldu: " + planned
                + " karşılaştırma");
        System.out.println("İP5 bilinçli fark: Diğer işine not alanı eklendi (\"ot biçtik\" → not=Ot biçme): " + noteAdded + " karşılaştırma");
        if (diffs > 0 || learnedDiffs > 0) {
            System.out.println("FAIL birebir aynılık");
            System.exit(1);
        }
        System.out.println("PASS birebir aynılık: RuleBrain eski zincirle her cümlede aynı");
    }

    /** Tek fark İP5'in "Diğer" notu mu: eylem ve değişke aynı, alanlardan ";not=…" çıkınca eskisiyle aynı. */
    static boolean onlyNote(String[] want, Decision got) {
        String note = got.fields.get("not");
        if (note == null || !"Diğer".equals(got.fields.get("tur"))) {
            return false;
        }
        String without = got.fieldsText().replace(";not=" + note, "");
        return want[0].equals(got.action) && want[1].equals(without) && want[2].equals(got.variant);
    }

    /**
     * İP7: eskiden kayıt değildi (soru ya da anlaşılmadı) ve şimdi çok adımlı satış. Eskiden kayda giden bir cümle
     * (elek, işlem, hasat...) asla buraya dönmez: o fark sayılır.
     */
    static boolean newPlan(String[] want, Decision got) {
        boolean wasRecord = want[0].equals(ActionCatalog.ELEK) || want[0].equals(ActionCatalog.ISLEM)
                || want[0].equals(ActionCatalog.HASAT) || want[0].startsWith("KAYIT_") || want[0].equals(ActionCatalog.ELEK_AC)
                || want[0].equals(ActionCatalog.ALACAK_SORU);
        boolean plan = ActionPlan.VARIANT.equals(got.variant) || ActionPlan.SINGLE.equals(got.variant);   // eğitim turu 1: tek adım
        if (!wasRecord && got.is(ActionCatalog.SATIS) && plan) {
            return true;
        }
        // Eğitim turu 1: eskiden kayıt değildi ve şimdi yeni kayıt eylemi (tahsilat, yağhane, hatırlatma).
        boolean newAction = got.is(ActionCatalog.TAHSILAT) || got.action.equals("YAGHANE") || got.is(ActionCatalog.HATIRLATMA);
        return !wasRecord && newAction;
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
