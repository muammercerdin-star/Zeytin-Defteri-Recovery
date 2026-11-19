package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Öğrendiklerim ekranı (İP6): öğret → ekranda gör → sil → artık anlaşılmıyor; ekrandaki her satır asistanın gerçekten
 * kullandığı bir bilgi (kullanılamayan söz gösterilmez); tarih ve kullanım metinleri; boş ekran; teknik terim yok.
 */
public class LearnedScreenTest {
    static int n = 0;
    static int bad = 0;
    static final TimeZone IST = TimeZone.getTimeZone("Europe/Istanbul");

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

    static long at(int year, int month, int day, int hour) {
        Calendar c = Calendar.getInstance(IST, new Locale("tr", "TR"));
        c.clear();
        c.set(year, month - 1, day, hour, 0, 0);
        return c.getTimeInMillis();
    }

    static Decision decide(LearnedBrain b, String raw) {
        return b.decide(new Brain.Input(raw), BrainContext.full(true, AssistantPersona.decode("")::handle));
    }

    public static void main(String[] a) {
        empty();
        chain();
        everyRowIsUsed();
        dates();
        texts();
        forgetAll();
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " Öğrendiklerim");
        if (bad > 0) {
            System.exit(1);
        }
    }

    static void empty() {
        List<LearnedScreen.Row> rows = LearnedScreen.taught(new ArrayList<>(), at(2027, 2, 1, 10), IST);
        check(rows.isEmpty(), "sözlük boş: satır yok");
        eq(LearnedScreen.EMPTY, "Henüz bir şey öğrenmedim. Anlamadığım bir sözü bana öğrettiğinde burada görünür.", "boş açıklama");
        check(LearnedScreen.noticed(new ArrayList<>()).isEmpty() && LearnedScreen.noticed(null).isEmpty(),
                "fark ettiklerim: İP5 yokken boş, uydurma satır yok");
        eq(LearnedScreen.spokenSummary(0), LearnedScreen.EMPTY, "asistan: boşken nazik açıklama");
    }

    /** Öğret → ekranda gör → kullan → sil → artık anlaşılmıyor. */
    static void chain() {
        LearnedTerms.Memory store = new LearnedTerms.Memory();
        LearnedBrain brain = new LearnedBrain(store, new RuleBrain());
        check(!decide(brain, "bugün bakırladık").understood(), "öğretmeden anlaşılmaz");
        store.put(LearnedTerms.teach("bakırladık", LearnedTerms.choiceFor("ilaçlama"), LearnedTerms.SOURCE_VOICE, at(2027, 1, 12, 9)));
        long now = at(2027, 2, 3, 18);
        List<LearnedScreen.Row> rows = LearnedScreen.taught(store.all(), now, IST);
        eq(rows.size(), 1, "öğretilen söz ekranda");
        eq(rows.get(0).title, "Bakırladık = ilaçlama", "başlık");
        eq(rows.get(0).detail, "12 Ocak'ta öğrendim · henüz kullanmadım", "açıklama (kullanılmadan)");
        for (int i = 0; i < 4; i++) {
            check(decide(brain, "bugün Girelli'yi bakırladık").is(ActionCatalog.ISLEM), "kullanılır " + (i + 1));
        }
        rows = LearnedScreen.taught(store.all(), now, IST);
        eq(rows.get(0).detail, "12 Ocak'ta öğrendim · 4 kez kullandım", "ekrandaki sayı gerçek kullanım");
        eq(rows.get(0).deleteSpoken, "Bakırladık sözünü sil", "Sil düğmesinin okunuşu");
        check(store.remove(rows.get(0).key), "Sil: satırın anahtarıyla silinir");
        check(LearnedScreen.taught(store.all(), now, IST).isEmpty(), "silinince ekrandan kalkar");
        check(!decide(brain, "bugün bakırladık").understood(), "silinen söz bir daha kullanılmaz");
        check(LearnedTerms.teachable("bugün bakırladık", store) != null, "silinen söz yeniden sorulabilir (öğretilebilir)");
        eq(LearnedScreen.forgotten("bakırladık"), "Tamam, “Bakırladık” sözünü unuttum.", "silince söylenen");
    }

    /** Ekrandaki her satır asistanın gerçekten kullandığı bir söz; kullanılamayan söz gösterilmez. */
    static void everyRowIsUsed() {
        LearnedTerms.Memory store = new LearnedTerms.Memory();
        String[][] taught = {{"bakırladık", "ilaçlama"}, {"çapaladık", "gübre"}, {"silktik", "hasat"}, {"kuruttuk", "sulama"},
                {"kestik", "budama"}};
        long t = at(2027, 1, 5, 8);
        for (String[] w : taught) {
            LearnedTerms.Term term = LearnedTerms.teach(w[0], LearnedTerms.choiceFor(w[1]), LearnedTerms.SOURCE_TEXT, t++);
            if (!term.term.isEmpty()) {
                store.put(term);
            }
        }
        // Bozuk / kullanılamayan söz: anlamı hiçbir seçime karşılık gelmiyor (örn. elle bozulmuş dosya).
        store.put(new LearnedTerms.Term("zurnal", "zurnaladık", ActionCatalog.ISLEM, "tur", "Mazot", LearnedTerms.TAUGHT,
                LearnedTerms.SOURCE_VOICE, t, 0));
        LearnedBrain brain = new LearnedBrain(store, new RuleBrain());
        List<LearnedScreen.Row> rows = LearnedScreen.taught(store.all(), at(2027, 2, 1, 10), IST);
        check(rows.size() == store.all().size() - 1, "kullanılamayan söz ekranda yok (" + rows.size() + " satır)");
        for (LearnedScreen.Row row : rows) {
            LearnedTerms.Term term = store.get(row.key);
            check(term != null, "satırın sözü sözlükte: " + row.title);
            Decision d = decide(brain, "bugün " + term.word);
            LearnedTerms.Term used = LearnedBrain.term(d);
            check(d.understood() && d.brain.equals(LearnedBrain.NAME) && used != null && used.term.equals(row.key),
                    "satır gerçekten kullanılıyor: " + row.title + " → " + d);
        }
        Decision mazot = decide(brain, "bugün zurnaladık");
        check(!LearnedBrain.NAME.equals(mazot.brain), "gösterilmeyen söz kullanılmıyor da: " + mazot);
        for (int i = 1; i < rows.size(); i++) {
            check(store.get(rows.get(i - 1).key).created >= store.get(rows.get(i).key).created, "en yeni üstte");
        }
    }

    static void dates() {
        long now = at(2027, 6, 15, 12);
        eq(LearnedScreen.learnedOn(at(2027, 6, 15, 7), now, IST), "Bugün öğrendim", "bugün");
        eq(LearnedScreen.learnedOn(at(2027, 6, 14, 23), now, IST), "Dün öğrendim", "dün");
        eq(LearnedScreen.learnedOn(at(2027, 1, 1, 0), at(2027, 1, 2, 0), IST), "Dün öğrendim", "dün (yıl başı)");
        eq(LearnedScreen.learnedOn(at(2026, 12, 31, 22), at(2027, 1, 1, 9), IST), "Dün öğrendim", "dün (yıl dönümü)");
        String[] months = {"1 Ocak'ta", "1 Şubat'ta", "1 Mart'ta", "1 Nisan'da", "1 Mayıs'ta", "1 Haziran'da",
                "1 Temmuz'da", "1 Ağustos'ta", "1 Eylül'de", "1 Ekim'de", "1 Kasım'da", "1 Aralık'ta"};
        for (int m = 1; m <= 12; m++) {
            eq(LearnedScreen.learnedOn(at(2027, m, 1, 10), at(2027, 12, 31, 10), IST), months[m - 1] + " öğrendim", "ay eki " + m);
        }
        eq(LearnedScreen.learnedOn(at(2026, 3, 3, 10), now, IST), "3 Mart 2026'da öğrendim", "geçen yıl");
        eq(LearnedScreen.yearAt(2025), "'te", "2025'te");
        eq(LearnedScreen.yearAt(2027), "'de", "2027'de");
        eq(LearnedScreen.yearAt(2029), "'da", "2029'da");
        eq(LearnedScreen.yearAt(2030), "'da", "2030'da");
        eq(LearnedScreen.yearAt(2040), "'ta", "2040'ta");
        eq(LearnedScreen.yearAt(2070), "'te", "2070'te");
        eq(LearnedScreen.yearAt(2000), "'de", "2000'de");
        eq(LearnedScreen.learnedOn(0, now, IST), "Öğrendim", "tarihi bilinmeyen söz için uydurma tarih yok");
    }

    static void texts() {
        eq(LearnedScreen.usesText(0), "henüz kullanmadım", "kullanılmadı");
        eq(LearnedScreen.usesText(1), "1 kez kullandım", "1 kez");
        eq(LearnedScreen.spokenSummary(3), "3 söz öğrendim. Hepsini Öğrendiklerim sayfasında açtım; istediğini oradan silebilirsin.",
                "asistan özeti");
        List<LearnedScreen.Noticed> noticed = new ArrayList<>();
        noticed.add(new LearnedScreen.Noticed("alici", "Alıcı", "Ali Usta", "son 6 satışın 4'ü", true));
        noticed.add(new LearnedScreen.Noticed("bahce", "Bahçe", "Girelli", "son 6 işlemin 3'ü", false));
        List<LearnedScreen.Row> rows = LearnedScreen.noticed(noticed);
        eq(rows.get(0).title, "Alıcı: Ali Usta", "fark edilen başlık");
        eq(rows.get(0).detail, "son 6 satışın 4'ü · eminim", "yüksek güven = eminim");
        eq(rows.get(1).detail, "son 6 işlemin 3'ü · pek emin değilim", "orta güven = pek emin değilim");
        String[] shown = {LearnedScreen.TITLE, LearnedScreen.INTRO, LearnedScreen.TAUGHT, LearnedScreen.NOTICED, LearnedScreen.EMPTY,
                LearnedScreen.NOTICED_EMPTY, LearnedScreen.FORGET_ALL, LearnedScreen.FORGET_ALL_ASK, LearnedScreen.SURE,
                LearnedScreen.NOT_SURE, LearnedScreen.spokenSummary(2), rows.get(0).detail};
        String[] technical = {"güven", "puan", "sezilen", "veri", "model", "algoritma", "kayıt tablosu", "%"};
        for (String s : shown) {
            for (String w : technical) {
                check(!s.toLowerCase(new Locale("tr", "TR")).contains(w), "teknik terim yok: «" + w + "» in " + s);
            }
        }
        check(LearnedScreen.FORGET_ALL_ASK.contains("geri alamazsın"), "Hepsini unut uyarısı");
    }

    static void forgetAll() {
        LearnedTerms.Memory store = new LearnedTerms.Memory();
        store.put(LearnedTerms.teach("bakırladık", LearnedTerms.choiceFor("ilaçlama"), LearnedTerms.SOURCE_VOICE, 1));
        store.put(LearnedTerms.teach("çapaladık", LearnedTerms.choiceFor("gübre"), LearnedTerms.SOURCE_VOICE, 2));
        for (LearnedTerms.Term t : store.all()) {
            store.remove(t.term);
        }
        LearnedBrain brain = new LearnedBrain(store, new RuleBrain());
        check(store.all().isEmpty() && LearnedScreen.taught(store.all(), 10, IST).isEmpty(), "Hepsini unut: ekran boş");
        check(!LearnedBrain.NAME.equals(decide(brain, "bugün bakırladık").brain), "Hepsini unut: hiçbiri kullanılmaz");
    }
}
