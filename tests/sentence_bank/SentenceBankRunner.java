package com.zeytindefteri.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Cümle bankası sınayıcısı (İP1). Her cümleyi uygulamanın GERÇEK çözücülerinden, asistanın bugünkü sırasıyla
 * geçirir ve sonucu cumleler.tsv'deki beklenenle karşılaştırır. Uygulama koduna dokunmaz; yalnız ölçer.
 *
 * Sıra ZeytinAssistantController.submit() ve run() ile aynıdır (bahçesi olan kullanıcı, internet yardımcısı kapalı):
 * kapanış sözü → anlamadıklarım komutu → sohbet (AssistantPersona) → hasat kaydı → alacak sorusu → elek →
 * "elek gir" komutu → salamura/bahçe/gönderi kaydı → işlem → ZeytinAssistantCommand (soru, ekran açma, özel cevap).
 * Controller'da bu sıra değişirse burası da değişmelidir (İP3'te tek kapıya taşınacak).
 *
 * ELEK alanlarında kilo başı fiyat "<boy>_fiyat" (TL/kg) olarak yazılır (İP2).
 *
 * Kullanım:
 *   java ... SentenceBankRunner cumleler.tsv       → karşılaştırır, özet yazar; "dogru" bozulursa çıkış 1
 *   java ... SentenceBankRunner --dump < cumleler  → her satır için "cümle TAB eylem TAB alanlar" yazar
 */
public final class SentenceBankRunner {

    static final String MISS = "ANLASILMADI";

    private SentenceBankRunner() {
    }

    /** Bir cümlenin bugünkü sonucu: eylem ve alanlar ("anahtar=değer;..."; alan yoksa "-"). */
    static final class Outcome {
        final String action;
        final String fields;

        Outcome(String action, Map<String, String> fields) {
            this.action = action;
            this.fields = join(fields);
        }

        boolean understood() {
            return !MISS.equals(action);
        }
    }

    static Outcome route(String raw) {
        Map<String, String> f = new LinkedHashMap<>();
        String n = TurkishText.normalize(raw);
        if (n.matches(CLOSE)) {
            return new Outcome("KAPAT", f);
        }
        if (AssistantMisses.asksClear(raw)) {
            return new Outcome("ANLAMADIKLARI_SIL", f);
        }
        if (AssistantMisses.asksShow(raw)) {
            return new Outcome("ANLAMADIKLARI_GOSTER", f);
        }
        if (AssistantScorecard.asksShow(raw)) {
            return new Outcome("KARNE_GOSTER", f);
        }
        AssistantPersona persona = AssistantPersona.decode("");
        if (persona.handle(raw) != null) {
            return new Outcome("SOHBET", f);
        }
        VoiceRecordDialog.Kind record = VoiceRecordDialog.detect(raw);
        if (record == VoiceRecordDialog.Kind.HARVEST) {
            harvestFields(raw, f);
            return new Outcome("HASAT", f);
        }
        if (AssistantReceivables.asks(raw)) {
            return new Outcome("ALACAK_SORU", f);
        }
        ElekVoiceEntry elek = ElekVoiceEntry.parse(raw);
        if (elek != null) {
            elekFields(elek, f);
            return new Outcome("ELEK", f);
        }
        if (opensElek(n)) {
            return new Outcome("ELEK_AC", f);
        }
        if (record != null) {
            return new Outcome("KAYIT_" + record.name(), f);
        }
        OperationVoiceEntry op = OperationVoiceEntry.parse(raw);
        if (op != null) {
            operationFields(op, f);
            return new Outcome("ISLEM", f);
        }
        return command(raw, f);
    }

    /** ZeytinAssistantController.submit() içindeki kapanış sözü. */
    private static final String CLOSE = "(tesekkur\\p{L}*|sag ol\\p{L}*|sagol\\p{L}*|tamam bu kadar|bu kadar|yeter|kapat"
            + "|gorusuruz|hosca kal|eyvallah)( .*)?";

    /** ZeytinAssistantController.run() içindeki "elek gireceğim" kuralı. */
    private static boolean opensElek(String n) {
        if (!n.matches(".*\\belek\\b.*")) {
            return false;
        }
        boolean verb = n.matches(".*\\b(gir\\p{L}*|kaydet\\p{L}*|kaydi|ekle\\p{L}*|yaz\\p{L}*|yapacag\\p{L}*|baslat|basla\\p{L}*)\\b.*");
        boolean question = n.matches(".*(^|\\s)(kac|ne|mi|mu|hangi|nasil|son)(\\s|$).*");
        return verb && !question;
    }

    private static Outcome command(String raw, Map<String, String> f) {
        ZeytinAssistantCommand cmd = ZeytinAssistantCommand.parse(raw);
        if (cmd.kind == ZeytinAssistantCommand.Kind.UNKNOWN) {
            if (ZeytinAssistantText.special(raw) != null) {
                return new Outcome("OZEL_CEVAP", f);
            }
            return new Outcome(MISS, f);
        }
        if (cmd.kind == ZeytinAssistantCommand.Kind.NAVIGATION) {
            f.put("ekran", cmd.target);
            return new Outcome("EKRAN_AC", f);
        }
        if (!cmd.grade.isEmpty()) {
            f.put("boy", cmd.grade);
        }
        if (!cmd.opType.isEmpty()) {
            f.put("islem", cmd.opType);
        }
        return new Outcome("SORU_" + cmd.kind.name(), f);
    }

    private static final String[] GRADE_KEYS = {"double", "1no", "2no", "3no", "4no", "yaglik"};

    private static void elekFields(ElekVoiceEntry e, Map<String, String> f) {
        for (int i = 0; i < e.kg.length; i++) {
            if (e.kg[i] > 0) {
                f.put(GRADE_KEYS[i], num(e.kg[i]));
            }
            if (e.price[i] > 0) {
                f.put(GRADE_KEYS[i] + "_fiyat", num(e.price[i]));
            }
        }
        if (e.sold != null) {
            f.put("satis", e.sold ? "evet" : "hayir");
        }
        if (!e.buyer.isEmpty()) {
            f.put("alici", e.buyer);
        }
    }

    private static void harvestFields(String raw, Map<String, String> f) {
        HarvestVoiceEntry h = HarvestVoiceEntry.parse(raw);
        if (h == null) {
            return;
        }
        for (int i = 0; i < h.kg.length; i++) {
            if (h.kg[i] > 0) {
                f.put(GRADE_KEYS[i], num(h.kg[i]));
            }
        }
        f.put("toplam", num(h.total));
        if (h.dayOffset != 0) {
            f.put("gun", String.valueOf(h.dayOffset));
        }
    }

    private static void operationFields(OperationVoiceEntry e, Map<String, String> f) {
        f.put("tur", OperationKinds.TYPES[e.type]);
        if (e.dayOffset != 0) {
            f.put("gun", String.valueOf(e.dayOffset));
        }
        if (e.quantity > 0) {
            f.put("miktar", num(e.quantity));
            f.put("birim", e.unit);
        }
        if (e.cost > 0) {
            f.put("tutar", num(e.cost));
        }
    }

    static String num(double v) {
        if (v == Math.floor(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    static String join(Map<String, String> f) {
        if (f.isEmpty()) {
            return "-";
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : f.entrySet()) {
            if (b.length() > 0) {
                b.append(';');
            }
            b.append(e.getKey()).append('=').append(e.getValue());
        }
        return b.toString();
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

    static String lower(String s) {
        return s.toLowerCase(new Locale("tr", "TR"));
    }
}
