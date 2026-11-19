package com.zeytindefteri.app;

import java.util.List;

/** Öğrenilen sözler (İP4): kök, aday söz, öğret → anla, unut → anlama, vazgeç, komutlar, onay kuralı. */
public class LearnedTermsTest {
    static int n = 0;
    static int bad = 0;

    static void check(boolean ok, String m) {
        n++;
        if (!ok) {
            bad++;
            System.out.println("FAIL " + m);
        }
    }

    static void eq(String got, String want, String m) {
        check(want.equals(got), m + ": «" + got + "» beklenen «" + want + "»");
    }

    static Decision decide(LearnedBrain b, String raw) {
        return b.decide(new Brain.Input(raw), BrainContext.full(true, AssistantPersona.decode("")::handle));
    }

    public static void main(String[] a) {
        roots();
        candidates();
        teachAndUse();
        commands();
        texts();
        chatNeverTaught();
        outcomesUnchanged();
        backup();
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " LearnedTerms");
        if (bad > 0) {
            System.exit(1);
        }
    }

    static void roots() {
        for (String w : new String[]{"bakırladık", "bakırladım", "Bakırladı", "bakırlıyoruz", "bakırlayacağız", "bakırlamıştık", "bakırladığı", "bakırlama"}) {
            eq(LearnedTerms.verbRoot(w), "bakirl", "aynı kök: " + w);
        }
        eq(LearnedTerms.verbRoot("bakır"), "", "fiil değil (ilaç adı)");
        eq(LearnedTerms.verbRoot("Girelli'yi"), "", "bahçe adı fiil değil");
        eq(LearnedTerms.verbRoot("budadık"), "bud", "budadık");
        eq(LearnedTerms.verbRoot("temizledik"), "temizl", "temizledik");
        eq(LearnedTerms.verbRoot("ab"), "", "çok kısa");
        eq(LearnedTerms.verbRoot(null), "", "boş");
    }

    static void candidates() {
        eq(LearnedTerms.candidate("bugün bakırladık"), "bakırladık", "son fiil");
        eq(LearnedTerms.candidate("dün Girelli'yi bakırladık 300 lira"), "bakırladık", "bahçe ve tutarla");
        check(LearnedTerms.candidate("bakır attık") == null, "genel fiil (attık) sorulmaz");
        check(LearnedTerms.candidate("Bugün pazara gittim") == null, "genel fiil (gittim) sorulmaz");
        check(LearnedTerms.candidate("Traktörün radyosunu aç") == null, "fiilsiz komut sorulmaz");
        check(LearnedTerms.candidate("") == null, "boş");
        LearnedTerms.Memory m = new LearnedTerms.Memory();
        eq(LearnedTerms.teachable("bugün bakırladık", m), "bakırladık", "sorulur");
        m.decline(LearnedTerms.sentenceKey("Bugün bakırladık."));
        check(LearnedTerms.teachable("bugün bakırladık", m) == null, "aynı cümle için vazgeç: bir daha sorulmaz");
        eq(LearnedTerms.teachable("dün bakırladık", m), "bakırladık", "başka cümlede yine sorulabilir");
        m.put(LearnedTerms.teach("bakırladık", LearnedTerms.CHOICES.get(0), LearnedTerms.SOURCE_VOICE, 1));
        check(LearnedTerms.teachable("dün bakırladık", m) == null, "öğrenilen söz sorulmaz");
    }

    static void teachAndUse() {
        LearnedTerms.Memory m = new LearnedTerms.Memory();
        RuleBrain rules = new RuleBrain();
        LearnedBrain b = new LearnedBrain(m, rules);
        Decision before = decide(b, "bugün bakırladık");
        check(!before.understood(), "sözlük boşken anlaşılmaz: " + before);
        LearnedTerms.Choice ilac = LearnedTerms.choiceFor("ilaçlama");
        check(ilac != null && ilac.value.equals("İlaçlama"), "düğme/ses cevabı: ilaçlama");
        m.put(LearnedTerms.teach("bakırladık", ilac, LearnedTerms.SOURCE_VOICE, 1000));
        LearnedTerms.Term t = m.get("bakirl");
        check(t != null && t.kind.equals(LearnedTerms.TAUGHT) && t.source.equals("ses") && t.created == 1000 && t.uses == 0, "öğretilen söz kaydı");
        Decision after = decide(b, "bugün bakırladık");
        check(after.is(ActionCatalog.ISLEM) && "İlaçlama".equals(after.fields.get("tur")), "öğretilince anlaşılır: " + after);
        check(after.brain.equals(LearnedBrain.NAME) && after.reason.contains("bakırladık"), "gerekçede öğrenilen söz: " + after.reason);
        check(LearnedBrain.term(after) != null && LearnedBrain.payload(after, OperationVoiceEntry.class) != null, "yükte işlem ve söz");
        check(m.get("bakirl").uses == 1, "kullanım sayılır");
        check(ActionGate.allowed(after).ordinal() <= ActionCatalog.Level.FORMU_DOLDUR.ordinal() && !ActionGate.mayActAlone(after),
                "öğrenilen söz de onay kuralını delemez");
        Decision dated = decide(b, "dün Girelli'yi bakırladık 300 lira");
        check("-1".equals(dated.fields.get("gun")) && "300".equals(dated.fields.get("tutar")), "gün ve tutar korunur: " + dated);
        Decision elek = decide(b, "Bugün 3 numara 120 kilo");
        check(elek.is(ActionCatalog.ELEK) && elek.brain.equals(RuleBrain.NAME), "öğrenilen söz yoksa kurallar: " + elek);
        for (int i = 0; i < 5; i++) {
            decide(b, "bakırladık");
        }
        check(m.get("bakirl") != null, "öğretilen söz kendiliğinden unutulmaz");
        check(m.remove("bakirl") && !decide(b, "bugün bakırladık").understood(), "unutunca anlaşılmaz");
        m.put(LearnedTerms.teach("silktik", LearnedTerms.choiceFor("hasat"), LearnedTerms.SOURCE_TEXT, 1));
        Decision harvest = decide(b, "bugün Girelli'de silktik");
        check(harvest.is(ActionCatalog.HASAT), "hasat öğretilir: " + harvest);
        check(LearnedTerms.choiceFor("şey") == null, "seçim değil");
    }

    static void commands() {
        LearnedTerms.Memory m = new LearnedTerms.Memory();
        LearnedBrain b = new LearnedBrain(m, new RuleBrain());
        check(decide(b, "öğrendiklerini söyle").is(ActionCatalog.SOZLER_GOSTER), "öğrendiklerini söyle");
        check(decide(b, "neler öğrendin").is(ActionCatalog.SOZLER_GOSTER), "neler öğrendin");
        check(!decide(b, "bakırladığı unut").is(ActionCatalog.SOZ_UNUT), "sözlükte yoksa unut komutu değil");
        check(!decide(b, "adımı unut").is(ActionCatalog.SOZ_UNUT), "adımı unut sohbete kalır");
        m.put(LearnedTerms.teach("bakırladık", LearnedTerms.CHOICES.get(0), LearnedTerms.SOURCE_VOICE, 1));
        Decision forget = decide(b, "bakırladığı unut");
        check(forget.is(ActionCatalog.SOZ_UNUT) && "bakirladigi".equals(forget.payload(String.class)), "bakırladığı unut: " + forget.payload);
        check(decide(b, "bakırladık sözünü unut").is(ActionCatalog.SOZ_UNUT), "sözünü unut");
        check(!decide(b, "unutma").is(ActionCatalog.SOZ_UNUT), "unutma komut değil");
        check(!ActionCatalog.alwaysConfirm(ActionCatalog.SOZ_UNUT), "asistanın kendi notu: onaysız silinir (AGENTS.md notu)");
    }

    static void texts() {
        List<LearnedTerms.Term> none = new LearnedTerms.Memory().all();
        check(LearnedTerms.listText(none)[0].startsWith("Henüz öğrendiğim bir söz yok"), "boş liste");
        LearnedTerms.Memory m = new LearnedTerms.Memory();
        m.put(LearnedTerms.teach("bakırladık", LearnedTerms.CHOICES.get(0), LearnedTerms.SOURCE_CLOUD, 1));
        String[] shown = LearnedTerms.listText(m.all());
        check(shown[1].contains("Bakırladık → ilaçlama") && shown[1].contains("internet yardımcısından, onaylı"), "liste: " + shown[1]);
        check(shown[1].contains("yalnız bu telefonda"), "gizlilik notu");
        eq(LearnedTerms.told(m.get("bakirl")), "Bakırladık'ı ilaçlama diye öğrendim.", "geri bildirim");
        eq(LearnedTerms.accusative("temizledik"), "i", "ünlü uyumu i");
        eq(LearnedTerms.accusative("kuruttuk"), "u", "ünlü uyumu u");
        eq(LearnedTerms.accusative("sürdük"), "ü", "ünlü uyumu ü");
        eq(LearnedTerms.accusative("bakırla"), "yı", "ünlüyle biten");
    }

    static final java.util.List<String> GARDENS = java.util.Arrays.asList("Girelli", "Karşıyaka");

    static Decision record(String raw) {
        RuleBrain rules = new RuleBrain();
        return rules.recordStage(new Brain.Input(raw), BrainContext.recordsOnly(GARDENS), ZeytinAssistantCommand.parse(raw));
    }

    /** Sohbet cümlesinde öğretme sorusu çıkmaz (cihazdan önce yakalanan hata: "çok yoruldum" → "Yoruldum ne işi?"). */
    static void chatNeverTaught() {
        LearnedTerms.Memory empty = new LearnedTerms.Memory();
        String[] chat = {"çok yoruldum", "canım sıkıldı", "dün maç izledim", "başım ağrıdı", "torunum doğdu",
                "annem hastalandı", "hava soğudu", "bugün yağmur yağdı", "oğlum evlendi", "kızım okulu bitirdi",
                "dizim ağrıyor", "uykum kaçtı", "çok üzüldüm", "bugün çok sevindim", "bugün yoruldum", "dün dinlendik",
                "bugün üşüdüm", "sabah uyandım", "dün akşam güldük", "bugün hastalandım", "çay içtik", "seni özledim",
                "ödendi", "Üretici", "randıman", "tartım", "bugün ödendi", "dün silindi", "sonra ödeyeceğim",
                "1500'e patladı", "fiyat bugün yükseldi"};
        for (String s : chat) {
            Decision d = record(s);
            check(LearnedBrain.teachQuestion(s, d, empty, GARDENS) == null, "sohbette öğretme sorusu yok: " + s);
        }
        // İş fiili hâlâ sorulur
        Decision d = record("bugün bakırladık");
        eq(LearnedBrain.teachQuestion("bugün bakırladık", d, empty, GARDENS), "bakırladık", "iş fiili sorulur");
        for (String s : new String[]{"Girelli'yi bakırladık", "dün sabah bakırladık", "Karşıyaka'yı bakırladık",
                "biz bugün bakırladık", "bugün bakırlıyoruz", "yarın bakırlayacağız"}) {
            check(LearnedBrain.teachQuestion(s, record(s), empty, GARDENS) != null, "iş cümlesi biçimi sorulur: " + s);
        }
    }

    /** Asistanın anlaşılmayan cümledeki yürütmesi (sözlük boşken): eskiden sohbet/özel cevap olan hiçbir cümle değişmez. */
    static String outcome(String raw, Decision d, LearnedTerms.Store store, boolean withTeach) {
        if (d.understood() && !d.is(ActionCatalog.OZEL_CEVAP)) {
            return "anlasildi";
        }
        if (LearnedBrain.payload(d, String.class) != null) {
            return "ozel";
        }
        if (withTeach && LearnedBrain.teachQuestion(raw, d, store, GARDENS) != null) {
            return "ogret";
        }
        return "sohbet".equals(d.variant) ? "sohbet" : "ipucu";
    }

    static void outcomesUnchanged() {
        String bank = System.getProperty("bank", "");
        if (bank.isEmpty()) {
            check(false, "cümle bankası yolu verilmedi (-Dbank)");
            return;
        }
        java.util.List<String> lines;
        try {
            lines = java.nio.file.Files.readAllLines(java.nio.file.Paths.get(bank), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            check(false, "cümle bankası okunamadı: " + e);
            return;
        }
        LearnedTerms.Memory empty = new LearnedTerms.Memory();
        int total = 0;
        int changed = 0;
        int newTeach = 0;
        for (String line : lines) {
            if (line.startsWith("#") || line.trim().isEmpty()) {
                continue;
            }
            String raw = line.split("\t", -1)[0];
            Decision d = record(raw);
            String before = outcome(raw, d, empty, false);
            String after = outcome(raw, d, empty, true);
            total++;
            if (before.equals(after)) {
                continue;
            }
            // Tek izinli fark: eskiden ipucu kartı gösterilen iş cümlesine öğretme sorusu (İP4'ün işi).
            if (before.equals("ipucu") && after.equals("ogret")) {
                newTeach++;
                continue;
            }
            // Sohbet sanılan iş cümlesi ("bugün bakırladık"): yalnız iş biçimindeyse ve görünür listede.
            if (before.equals("sohbet") && after.equals("ogret") && LearnedTerms.workShaped(raw, GARDENS)) {
                newTeach++;
                System.out.println("  sohbet → öğretme: " + raw);
                continue;
            }
            changed++;
            check(false, "yürütme değişti: «" + raw + "» " + before + " → " + after);
        }
        check(total > 200 && changed == 0, "cümle bankası yürütme: " + total + " cümle, " + changed + " izinsiz fark");
        System.out.println("Yürütme karşılaştırması: " + total + " cümle · izinsiz fark " + changed + " · ipucu→öğretme " + newTeach);
    }

    /** Ayarlar → Yedekle: dışa yazma, geri yüklemede telefondaki söz korunur, bozuk/uydurma satır atlanır. */
    static void backup() {
        LearnedTerms.Memory phone = new LearnedTerms.Memory();
        phone.put(LearnedTerms.teach("bakırladık", LearnedTerms.CHOICES.get(0), LearnedTerms.SOURCE_VOICE, 10));
        phone.put(LearnedTerms.teach("kükürtledik", LearnedTerms.CHOICES.get(0), LearnedTerms.SOURCE_TEXT, 20));
        String text = LearnedTerms.exportText(phone.all());
        check(text.split("\n").length == 2, "iki söz yazıldı");
        eq(LearnedTerms.exportText(new LearnedTerms.Memory().all()), "", "boş sözlük → boş metin");

        LearnedTerms.Memory fresh = new LearnedTerms.Memory();
        check(LearnedTerms.importText(text, fresh) == 2, "yeni telefona iki söz");
        LearnedTerms.Term k = fresh.get(LearnedTerms.verbRoot("kükürtledik"));
        check(k != null && k.value.equals("İlaçlama") && k.source.equals(LearnedTerms.SOURCE_TEXT) && k.created == 20, "alanlar korunur");

        LearnedTerms.Memory other = new LearnedTerms.Memory();
        other.put(LearnedTerms.teach("bakırladık", LearnedTerms.CHOICES.get(1), LearnedTerms.SOURCE_VOICE, 99));
        check(LearnedTerms.importText(text, other) == 1, "aynı söz telefonda varsa eklenmez");
        eq(other.get("bakirl").value, "Gübre", "telefondaki anlam kalır");

        String forged = "bakirl\tbakırladık\tSATIS\t\t\togretilen\tses\t1\t0\n"
                + "xyz\tbakırladık\tISLEM\ttur\tİlaçlama\togretilen\tses\t1\t0\n"
                + "bozuk satır\n"
                + "bud\tbudadık\tISLEM\ttur\tİlaçlama\togretilen\tses\tabc\t0\n";
        check(LearnedTerms.importText(forged, new LearnedTerms.Memory()) == 0, "uydurma/bozuk satır alınmaz");
        check(LearnedTerms.importText(null, fresh) == 0, "boş yedek");

    }
}
