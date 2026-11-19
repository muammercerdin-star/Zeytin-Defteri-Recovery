package com.zeytindefteri.app;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * İP5 Adım 0: öğretme düğmelerine yeni işler. Toprak İşleme, Sinek tuzağı, İşçilik (tür olarak var) ve
 * Ot biçme / Temizlik, Aşılama ("Diğer" + not, kararla). Her iş için öğret → kullan → unut; iki "Diğer" işi karışmaz;
 * yedek dosyasına not yazılır ve geri yüklenir; eski sürüm (cea13a1) bu satırları sessizce atlar; sıra kayıtlara göre.
 */
public class NewJobsTest {
    static int n = 0;
    static int bad = 0;

    static void check(boolean ok, String m) {
        n++;
        if (!ok) {
            bad++;
            System.out.println("FAIL " + m);
        }
    }

    static void eq(Object got, Object want, String m) {
        check(want.equals(got), m + ": «" + got + "» beklenen «" + want + "»");
    }

    static final List<String> GARDENS = Arrays.asList("Girelli", "Kuyu dibi");

    static Decision decide(LearnedBrain b, String raw) {
        return b.decide(new Brain.Input(raw), BrainContext.full(true, AssistantPersona.decode("")::handle));
    }

    public static void main(String[] a) {
        choices();
        eachJob();
        twoOthers();
        rules();
        backup();
        oldVersion();
        order();
        dialog();
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " NewJobs (İP5 Adım 0)");
        if (bad > 0) {
            System.exit(1);
        }
    }

    static void choices() {
        eq(LearnedTerms.CHOICES.size(), 10, "10 iş düğmesi (Başka ve Vazgeç ayrıca)");
        String[][] spoken = {
                {"Toprak İşleme", "Toprak İşleme"}, {"toprak işleme", "Toprak İşleme"}, {"sinek tuzağı", "Sinek tuzağı"},
                {"tuzak", "Sinek tuzağı"}, {"işçilik", "İşçilik"}, {"Ot biçme / Temizlik", "Ot biçme / Temizlik"},
                {"ot biçme", "Ot biçme / Temizlik"}, {"temizlik", "Ot biçme / Temizlik"}, {"aşılama", "Aşılama"},
                {"ilaçlama", "İlaçlama"}, {"hasat", "Hasat"}};
        for (String[] s : spoken) {
            LearnedTerms.Choice c = LearnedTerms.choiceFor(s[0]);
            eq(c == null ? "yok" : c.label, s[1], "sesli cevap «" + s[0] + "»");
        }
        check(LearnedTerms.choiceFor("vazgeç") == null && LearnedTerms.choiceFor("başka") == null, "Vazgeç / Başka seçim değil");
        LearnedTerms.Choice ot = LearnedTerms.choiceFor("ot biçme");
        check(ot.value.equals("Diğer") && ot.note.equals("Ot biçme"), "Ot biçme: Diğer + not");
        LearnedTerms.Choice asi = LearnedTerms.choiceFor("aşılama");
        check(asi.value.equals("Diğer") && asi.note.equals("Aşılama"), "Aşılama: Diğer + not");
        for (LearnedTerms.Choice c : LearnedTerms.CHOICES) {
            if (!c.value.isEmpty()) {
                check(Arrays.asList(OperationKinds.TYPES).contains(c.value), "düğmenin türü listede: " + c.value);
            }
        }
    }

    /** Öğret → kullan → unut (her yeni iş). */
    static void eachJob() {
        String[][] jobs = {
                {"diskaradık", "Toprak İşleme", "bugün diskaradık", "Toprak İşleme", ""},
                {"feromonladık", "Sinek tuzağı", "bugün Girelli'yi feromonladık", "Sinek tuzağı", ""},
                {"ırgatladık", "İşçilik", "dün ırgatladık 1500 lira", "İşçilik", ""},
                {"tırpanladık", "Ot biçme / Temizlik", "bugün tırpanladık", "Diğer", "Ot biçme"},
                {"aşıladık", "Aşılama", "bugün aşıladık", "Diğer", "Aşılama"}};
        for (String[] j : jobs) {
            LearnedTerms.Memory m = new LearnedTerms.Memory();
            LearnedBrain b = new LearnedBrain(m, new RuleBrain());
            Decision before = decide(b, j[2]);
            check(!before.understood(), j[0] + ": sözlük boşken anlaşılmaz");
            eq(LearnedBrain.teachQuestion(j[2], before, m, GARDENS), j[0], j[0] + ": öğretme sorusu açılır");
            LearnedTerms.Choice c = LearnedTerms.choiceFor(j[1]);
            m.put(LearnedTerms.teach(j[0], c, LearnedTerms.SOURCE_VOICE, 1));
            Decision after = decide(b, j[2]);
            check(after.is(ActionCatalog.ISLEM), j[0] + ": öğretilince işlem");
            eq(after.fields.get("tur"), j[3], j[0] + ": form türü");
            eq(String.valueOf(after.fields.get("not") == null ? "" : after.fields.get("not")), j[4], j[0] + ": not");
            OperationVoiceEntry op = LearnedBrain.payload(after, OperationVoiceEntry.class);
            check(op != null && OperationKinds.TYPES[op.type].equals(j[3]) && op.note.equals(j[4]), j[0] + ": form girdisi");
            check(AssistantOutcome.records(after, true) == AssistantOutcome.Kind.OPERATION_FORM, j[0] + ": öğretme penceresinden form dolu açılır");
            check(after.reason.contains(j[0]), j[0] + ": gerekçede öğrenilen söz");
            eq(LearnedTerms.told(m.get(LearnedTerms.verbRoot(j[0]))), LearnedTerms.capital(j[0]) + "'" + LearnedTerms.accusative(j[0])
                    + " " + c.spoken + " diye öğrendim.", j[0] + ": geri bildirim");
            Decision forget = decide(b, j[0].replaceAll("k$", "ğı") + " unut");
            check(forget.is(ActionCatalog.SOZ_UNUT), j[0] + ": unut komutu");
            m.remove(LearnedTerms.verbRoot(j[0]));
            check(!decide(b, j[2]).understood(), j[0] + ": unutunca anlaşılmaz");
        }
    }

    /** İki "Diğer" işi notla ayrılır, birbirine karışmaz. */
    static void twoOthers() {
        LearnedTerms.Memory m = new LearnedTerms.Memory();
        LearnedBrain b = new LearnedBrain(m, new RuleBrain());
        m.put(LearnedTerms.teach("tırpanladık", LearnedTerms.choiceFor("ot biçme"), LearnedTerms.SOURCE_VOICE, 1));
        m.put(LearnedTerms.teach("aşıladık", LearnedTerms.choiceFor("aşılama"), LearnedTerms.SOURCE_VOICE, 2));
        eq(decide(b, "bugün tırpanladık").fields.get("not"), "Ot biçme", "ot biçme kendi notuyla");
        eq(decide(b, "bugün aşıladık").fields.get("not"), "Aşılama", "aşılama kendi notuyla");
        LearnedTerms.Term ot = m.get(LearnedTerms.verbRoot("tırpanladık"));
        LearnedTerms.Term asi = m.get(LearnedTerms.verbRoot("aşıladık"));
        eq(LearnedTerms.choiceOf(ot).label, "Ot biçme / Temizlik", "choiceOf ot biçme");
        eq(LearnedTerms.choiceOf(asi).label, "Aşılama", "choiceOf aşılama");
        eq(ot.meaning(), "ot biçme", "anlamı okunur");
        Decision d = decide(b, "bugün aşıladık");
        check(!LearnedBrain.means(d, ot) && LearnedBrain.means(d, asi), "aşılama kararı ot biçme sözüyle eşleşmez");
        String[] list = LearnedTerms.listText(m.all());
        check(list[1].contains("Tırpanladık → ot biçme") && list[1].contains("Aşıladık → aşılama"), "liste: " + list[1]);
    }

    /** Kuralların söyleyişleri (düğmelerin kurallara verdiği sözler) ve eski cümleler. */
    static void rules() {
        for (LearnedTerms.Choice c : LearnedTerms.CHOICES) {
            Decision d = decide(new LearnedBrain(new LearnedTerms.Memory(), new RuleBrain()), "bugün " + c.phrase);
            if (c.action.equals(ActionCatalog.HASAT)) {
                check(d.is(ActionCatalog.HASAT), "kurallar anlar: " + c.phrase);
                continue;
            }
            check(d.is(ActionCatalog.ISLEM) && c.value.equals(d.fields.get("tur")), "kurallar anlar: " + c.phrase + " → " + d);
            eq(d.fields.get("not") == null ? "" : d.fields.get("not"), c.note, "not: " + c.phrase);
        }
        eq(OperationVoiceEntry.noteOf(TurkishText.normalize("kalem aşısı yaptık")), "Aşılama", "kalem aşısı");
        eq(OperationVoiceEntry.noteOf(TurkishText.normalize("otları biçtik")), "Ot biçme", "otları biçtik");
        eq(OperationVoiceEntry.noteOf(TurkishText.normalize("tırpanladık")), "", "tırpanladık kurallarda yok (öğretilir)");
        eq(OperationVoiceEntry.noteOf(TurkishText.normalize("aşıladık")), "", "aşıladık kurallarda yok (öğretilir)");
        OperationVoiceEntry e = OperationVoiceEntry.parse("bugün otları biçtik 3 saat");
        eq(e.summary(), "ot biçme, bugün, 3 saat", "okunan özet işin adıyla");
        check(OperationVoiceEntry.parse("ot biçme ne zaman") == null, "soru kayıt sayılmaz");
        check(OperationVoiceEntry.parse("Girelli'ye 15 sinek tuzağı astık").quantity == 15, "15 sinek tuzağı");
        check(OperationVoiceEntry.parse("sinek tuzağı kaydı") == null, "sinek tuzağı kaydı soru kalır");
    }

    static void backup() {
        LearnedTerms.Memory phone = new LearnedTerms.Memory();
        phone.put(LearnedTerms.teach("bakırladık", LearnedTerms.choiceFor("ilaçlama"), LearnedTerms.SOURCE_VOICE, 10));
        phone.put(LearnedTerms.teach("tırpanladık", LearnedTerms.choiceFor("ot biçme"), LearnedTerms.SOURCE_VOICE, 20));
        phone.put(LearnedTerms.teach("aşıladık", LearnedTerms.choiceFor("aşılama"), LearnedTerms.SOURCE_TEXT, 30));
        phone.put(LearnedTerms.teach("diskaradık", LearnedTerms.choiceFor("toprak işleme"), LearnedTerms.SOURCE_VOICE, 40));
        String text = LearnedTerms.exportText(phone.all());
        String[] lines = text.split("\n");
        eq(lines.length, 4, "dört söz yazıldı");
        eq(lines[0].split("\t", -1).length, 9, "notsuz söz 9 sütun (eskisi gibi)");
        eq(lines[1].split("\t", -1).length, 10, "Diğer işi 10 sütun (not)");
        check(lines[1].endsWith("\tOt biçme") && lines[2].endsWith("\tAşılama"), "not yedekte");
        LearnedTerms.Memory fresh = new LearnedTerms.Memory();
        eq(LearnedTerms.importText(text, fresh), 4, "yeni sürüm dördünü de alır");
        eq(fresh.get(LearnedTerms.verbRoot("tırpanladık")).note, "Ot biçme", "not geri gelir (ot biçme)");
        eq(fresh.get(LearnedTerms.verbRoot("aşıladık")).note, "Aşılama", "not geri gelir (aşılama)");
        eq(LearnedTerms.exportText(fresh.all()), text, "yedek → geri yükle → yedek aynı");
        String forged = "tirpanl\ttırpanladık\tISLEM\ttur\tDiğer\togretilen\tses\t1\t0\tBaşka iş\n"
                + "tirpanl\ttırpanladık\tISLEM\ttur\tDiğer\togretilen\tses\t1\t0\n"
                + "tirpanl\ttırpanladık\tISLEM\ttur\tİlaçlama\togretilen\tses\t1\t0\tOt biçme\n";
        eq(LearnedTerms.importText(forged, new LearnedTerms.Memory()), 0, "uydurma not / notsuz Diğer / yanlış eşleşme alınmaz");
    }

    /** Eski sürüm (cea13a1'deki LearnedTerms, olduğu gibi derlendi): notlu satırları sessizce atlar, ötekileri alır. */
    static void oldVersion() {
        LearnedTerms.Memory phone = new LearnedTerms.Memory();
        phone.put(LearnedTerms.teach("bakırladık", LearnedTerms.choiceFor("ilaçlama"), LearnedTerms.SOURCE_VOICE, 10));
        phone.put(LearnedTerms.teach("tırpanladık", LearnedTerms.choiceFor("ot biçme"), LearnedTerms.SOURCE_VOICE, 20));
        phone.put(LearnedTerms.teach("aşıladık", LearnedTerms.choiceFor("aşılama"), LearnedTerms.SOURCE_TEXT, 30));
        phone.put(LearnedTerms.teach("diskaradık", LearnedTerms.choiceFor("toprak işleme"), LearnedTerms.SOURCE_VOICE, 40));
        String text = LearnedTerms.exportText(phone.all());
        LearnedTermsOld.Memory old = new LearnedTermsOld.Memory();
        int got;
        try {
            got = LearnedTermsOld.importText(text, old);
        } catch (RuntimeException e) {
            got = -1;
        }
        eq(got, 1, "eski sürüm hata vermeden yalnız tanıdığı sözü alır");
        check(old.get("bakirl") != null, "eski sürüm: bakırladık alındı");
        check(old.get(LearnedTerms.verbRoot("tırpanladık")) == null && old.get(LearnedTerms.verbRoot("aşıladık")) == null,
                "eski sürüm: Diğer işleri sessizce atlandı");
        check(old.get(LearnedTerms.verbRoot("diskaradık")) == null, "eski sürüm: tanımadığı yeni iş (Toprak İşleme) atlandı");
        // Eski sürümün yedeği yeni sürüme olduğu gibi girer.
        LearnedTermsOld.Memory before = new LearnedTermsOld.Memory();
        before.put(LearnedTermsOld.teach("bakırladık", LearnedTermsOld.CHOICES.get(0), LearnedTermsOld.SOURCE_VOICE, 5));
        LearnedTerms.Memory now = new LearnedTerms.Memory();
        eq(LearnedTerms.importText(LearnedTermsOld.exportText(before.all()), now), 1, "eski yedek yeni sürüme girer");
    }

    /** Düğme sırası: çiftçinin kayıtlarında en sık iş üstte, eşitlikte sabit sıra. */
    static void order() {
        List<LearnedTerms.Choice> fixed = LearnedTerms.ordered(new HashMap<>());
        eq(fixed.get(0).label, "İlaçlama", "kayıt yokken sabit sıra");
        eq(fixed.get(9).label, "Aşılama", "kayıt yokken son düğme");
        Map<String, Integer> counts = new HashMap<>();
        counts.put("Sulama", 7);
        counts.put("Ot biçme", 3);
        counts.put("Hasat", 3);
        counts.put("Budama", 1);
        List<LearnedTerms.Choice> o = LearnedTerms.ordered(counts);
        eq(o.get(0).label, "Sulama", "en sık iş üstte");
        eq(o.get(1).label, "Hasat", "eşitlikte sabit sıra (Hasat, Ot biçme'den önce)");
        eq(o.get(2).label, "Ot biçme / Temizlik", "eşit ikinci");
        eq(o.get(3).label, "Budama", "sonra Budama");
        eq(o.get(4).label, "İlaçlama", "kaydı olmayanlar sabit sırada");
        eq(o.size(), 10, "hiçbir düğme kaybolmaz");
        eq(LearnedTerms.workKey("Diğer", "Ot biçme, 2 saat (sesle)"), "Ot biçme", "Diğer + not → iş");
        eq(LearnedTerms.workKey("Diğer", "Aşılama"), "Aşılama", "Diğer + aşılama");
        eq(LearnedTerms.workKey("Diğer", "çit tamiri"), "", "düğmesi olmayan Diğer işi sayılmaz");
        eq(LearnedTerms.workKey("Gübreleme", ""), "Gübre", "eski gübreleme yazımı");
        eq(LearnedTerms.workKey("Mazot", ""), "", "düğmesi olmayan tür sayılmaz");
        eq(LearnedTerms.workKey("İşçilik", "3 kişi"), "İşçilik", "işçilik");
    }

    /** Sesli kayıt soruları: işin adı okunur, kayıt notu işin adıyla başlar, tür Diğer kalır. */
    static void dialog() {
        OperationVoiceEntry e = OperationVoiceEntry.parse("bugün otları biçtik");
        OperationVoiceDialog d = new OperationVoiceDialog(e, GARDENS, 0);
        d.start();
        check(d.summary().contains("ot biçme") && !d.summary().contains("diğer"), "okunan özet: " + d.summary());
        eq(d.type(), "Diğer", "kayıtta tür Diğer");
        check(d.noteForSave().startsWith("Ot biçme"), "not işin adıyla başlar: " + d.noteForSave());
        OperationVoiceEntry s = OperationVoiceEntry.parse("bugün Girelli'yi suladık 2 saat");
        check(s.note.isEmpty() && new OperationVoiceDialog(s, GARDENS, 0).noteForSave().isEmpty(), "diğer türlerde not eklenmez");
    }
}
