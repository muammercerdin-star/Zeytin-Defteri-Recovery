package com.zeytindefteri.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ekrandaki sonuç aynı mı (İP5)? Sözlük ve alışkanlık boşken asistanın her cümlede ekranda yaptığı iş
 * (form, soru, sohbet cevabı, ipucu, öğretme sorusu, ekran açma, cevap) İP4 sonundaki (cea13a1) hâliyle aynı olmalı.
 *
 * Cümle asistandaki gibi yürür: önce konuşma aşaması (sohbet, komutlar), sonra kayıt aşaması ve AssistantOutcome.
 * İnternet yardımcısı yok sayılır (çalışma anına bağlı). Dondurulmuş sonuçlar: baseline.tsv (cea13a1'de üretildi).
 * Bilinçli değişiklikler intended.tsv'de cümle cümle yazılıdır; başka her fark testi düşürür.
 *
 * Kullanım: OutcomeTest <kök> [--write]   (--write yalnız taban üretmek içindir).
 */
public class OutcomeTest {
    static final List<String> GARDENS = Arrays.asList("Girelli", "Kuyu dibi", "Alt bahçe");

    public static void main(String[] args) throws IOException {
        Path root = Paths.get(args[0]);
        Path dir = root.resolve("tests/assistant_outcome");
        boolean write = args.length > 1 && "--write".equals(args[1]);
        List<String> sentences = BrainEquivalenceTest.corpus(root);
        Map<String, String> now = new LinkedHashMap<>();
        for (String raw : sentences) {
            for (boolean gardens : new boolean[]{true, false}) {
                now.put(key(raw, gardens), outcome(raw, gardens));
            }
        }
        if (write) {
            List<String> lines = new ArrayList<>();
            for (Map.Entry<String, String> e : now.entrySet()) {
                lines.add(e.getKey() + "\t" + e.getValue());
            }
            Files.write(dir.resolve("baseline.tsv"), lines, StandardCharsets.UTF_8);
            System.out.println("Taban yazıldı: " + lines.size() + " satır");
            return;
        }
        Map<String, String> base = read(dir.resolve("baseline.tsv"));
        Map<String, String> intended = read(dir.resolve("intended.tsv"));
        int diffs = 0;
        int changed = 0;
        int compared = 0;
        for (Map.Entry<String, String> e : base.entrySet()) {
            String got = now.get(e.getKey());
            if (got == null) {
                continue;   // cümle artık testlerde yok
            }
            compared++;
            if (got.equals(e.getValue())) {
                continue;
            }
            String want = intended.get(e.getKey());
            if (want != null && want.equals(got)) {
                changed++;
                continue;
            }
            diffs++;
            if (diffs <= 30) {
                System.out.println("FARK " + e.getKey().replace('\t', ' ') + "\n   eski: " + e.getValue() + "\n   yeni: " + got);
            }
        }
        int stale = 0;
        for (Map.Entry<String, String> e : intended.entrySet()) {
            if (!e.getValue().equals(now.get(e.getKey()))) {
                stale++;
                if (stale <= 10) {
                    System.out.println("BEKLENEN DEĞİŞİKLİK TUTMADI " + e.getKey().replace('\t', ' ') + "\n   beklenen: " + e.getValue()
                            + "\n   şimdi: " + now.get(e.getKey()));
                }
            }
        }
        System.out.println(compared + " ekran sonucu karşılaştırıldı · " + diffs + " fark · " + changed + " bilinçli değişiklik (intended.tsv)");
        if (diffs > 0 || stale > 0) {
            System.out.println("FAIL ekran sonucu eskisiyle aynı değil");
            System.exit(1);
        }
        System.out.println("PASS ekran sonucu: sözlük ve alışkanlık boşken İP4 sonundakiyle aynı");
    }

    static String key(String raw, boolean gardens) {
        return raw.replace('\t', ' ').replace('\n', ' ') + "\t" + (gardens ? "bahçeli" : "bahçesiz");
    }

    /** Asistanın bu cümlede ekranda yaptığı iş, tek satır. */
    static String outcome(String raw, boolean hasGardens) {
        LearnedBrain brain = new LearnedBrain(new LearnedTerms.Memory(), new RuleBrain());
        AssistantPersona persona = AssistantPersona.decode("");
        BrainContext talkContext = BrainContext.assistant(true, true, false, persona::handle);
        Decision talk = brain.conversationStage(new Brain.Input(raw), talkContext);
        if (talk != null) {
            // Sohbet cevabının kendisi rastgele seçilir (aynı selama farklı söz): yalnız eylem karşılaştırılır.
            String said = talk.is(ActionCatalog.SOHBET) ? null : talk.payload(String.class);
            return "KONUŞMA " + talk.action + (said == null ? "" : " · " + flat(said));
        }
        List<String> names = hasGardens ? GARDENS : Collections.<String>emptyList();
        ZeytinAssistantCommand cmd = ZeytinAssistantCommand.parse(raw);
        Decision d = brain.recordStage(new Brain.Input(raw), BrainContext.recordsOnly(names), cmd);
        AssistantOutcome.Kind first = AssistantOutcome.records(d, false);
        if (first != AssistantOutcome.Kind.LATER) {
            return first + " · " + d.action + " " + d.fieldsText() + detail(d);
        }
        String teach = LearnedBrain.teachQuestion(raw, d, new LearnedTerms.Memory(), names);
        boolean navigation = cmd.kind == ZeytinAssistantCommand.Kind.NAVIGATION;
        boolean market = cmd.kind == ZeytinAssistantCommand.Kind.MARKET && !ZeytinAssistantCommand.ownSales(cmd.text);
        AssistantOutcome.Kind kind = AssistantOutcome.rest(d, teach, navigation, market);
        switch (kind) {
            case SPECIAL:
                return "SPECIAL · " + flat(LearnedBrain.payload(d, String.class));
            case TEACH:
                return "TEACH · " + teach;
            case CHAT:
                return "CHAT · " + flat(persona.fallback(raw));
            case HINT: {
                ZeytinAssistantHints.Hint h = ZeytinAssistantHints.hint(raw, "", Collections.<ZeytinAssistantHints.Topic>emptySet());
                return "HINT · " + h.cards + " · " + flat(h.header) + " · " + flat(h.message) + " · " + h.questions + " · " + h.topics;
            }
            case NAVIGATE:
                return "NAVIGATE · " + cmd.target;
            default:
                return kind + " · " + d.action + " " + d.fieldsText();
        }
    }

    private static String detail(Decision d) {
        OperationVoiceEntry op = LearnedBrain.payload(d, OperationVoiceEntry.class);
        if (op != null) {
            return " · " + op.summary();
        }
        ElekVoiceEntry elek = LearnedBrain.payload(d, ElekVoiceEntry.class);
        if (elek != null) {
            return " · " + elek.summary();
        }
        VoiceRecordDialog.Kind rec = LearnedBrain.payload(d, VoiceRecordDialog.Kind.class);
        return rec == null ? "" : " · " + rec;
    }

    private static String flat(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ');
    }

    private static Map<String, String> read(Path p) throws IOException {
        Map<String, String> m = new LinkedHashMap<>();
        if (!Files.exists(p)) {
            return m;
        }
        for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] c = line.split("\t", 3);
            if (c.length == 3) {
                m.put(c[0] + "\t" + c[1], c[2]);
            }
        }
        return m;
    }
}
