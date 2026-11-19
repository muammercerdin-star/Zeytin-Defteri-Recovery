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
}
