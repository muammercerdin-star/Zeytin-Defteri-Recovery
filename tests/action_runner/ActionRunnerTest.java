package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * Görünür uygulayıcı ve çok adımlı eylem (İP7): çok eylemli cümle ayrıştırma, tek eylemlinin değişmemesi,
 * onaysız yazma olmadığı, tek onayla hepsinin yazıldığı, Geri al'ın hepsini geri aldığı, yarım akışın iz bırakmadığı,
 * izin basamağı (kayıt/satış/ödeme/silme asla izinle onaysız değil), karne.
 */
public class ActionRunnerTest {
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

    /** 19 Kasım 2026 Perşembe, 10:00. */
    static Calendar thursday() {
        Calendar c = Calendar.getInstance(IST, new Locale("tr", "TR"));
        c.clear();
        c.set(2026, Calendar.NOVEMBER, 19, 10, 0, 0);
        return c;
    }

    static Decision decide(String raw) {
        LearnedBrain b = new LearnedBrain(new LearnedTerms.Memory(), new RuleBrain());
        return b.decide(new Brain.Input(raw), BrainContext.full(true, AssistantPersona.decode("")::handle));
    }

    public static void main(String[] a) {
        parsing();
        singleUnchanged();
        singleSale();
        dates();
        noWriteWithoutConfirm();
        confirmWritesAll();
        undoAll();
        halfFlowLeavesNothing();
        allOrNothing();
        permission();
        texts();
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " ActionRunner (İP7)");
        if (bad > 0) {
            System.exit(1);
        }
    }

    static void parsing() {
        ActionPlan p = ActionPlan.parse("Ali Usta'ya 300 kilo sattım, parasını haftaya verecek");
        check(p != null, "örnek cümle çok adımlı");
        eq(p.steps.toString(), "[SATIS, ALACAK, HATIRLATMA]", "sıralı eylemler");
        eq(p.buyer, "Ali Usta", "alıcı");
        check(p.kilos == 300, "300 kilo");
        eq(p.due, "haftaya", "ödeme günü sözü");
        check(p.price == 0 && p.grade.isEmpty(), "söylenmeyen fiyat ve sınıf uydurulmaz");
        Decision d = decide("Ali Usta'ya 300 kilo sattım, parasını haftaya verecek");
        check(d.is(ActionCatalog.SATIS) && ActionPlan.VARIANT.equals(d.variant), "beyin: çok adımlı satış " + d);
        check(LearnedBrain.payload(d, ActionPlan.class) != null, "beyin: plan yükte");
        eq(AssistantOutcome.records(d, false), AssistantOutcome.Kind.MULTI_STEP, "asistan: görünür uygulayıcı");
        check(ActionGate.allowed(d) == ActionCatalog.Level.FORMU_DOLDUR && !ActionGate.mayActAlone(d), "satış formu doldurur, kendiliğinden yazmaz");
        ActionPlan noDue = ActionPlan.parse("Kemal'e 120 kilo sattım parası sonra");
        eq(noDue.steps.toString(), "[SATIS, ALACAK]", "ödeme günü yoksa hatırlatma yok");
        ActionPlan priced = ActionPlan.parse("Hasan Abi'ye 250 kilo sattık, kilosu 70 lira, parasını cuma verecek");
        check(priced.price == 70 && priced.due.equals("cuma") && priced.buyer.equals("Hasan Abi"), "kilosu 70 lira, cuma");
        check(ActionPlan.parse("Mehmet'e 100 kilo sattım 68 liradan parasını yarın getirecek").price == 68, "68 liradan");
        eq(ActionPlan.parse("ali usta'ya 300 kilo sattım parasını haftaya verecek").buyer, "", "küçük harfli addan alıcı uydurulmaz");
        eq(ActionPlan.parse("Mustafa'ya 200 kilo veresiye sattım").steps.toString(), "[SATIS, ALACAK]", "veresiye");
    }

    /** Tek eylemli cümleler değişmez: planlanmaz, eski yollarında kalır. */
    static void singleUnchanged() {
        String[] notPlan = {"Ali Usta'ya 300 kilo sattım", "Ali Usta'ya 300 kilo sattım, parasını peşin aldım",
                "Ali Usta parasını cuma verecek", "300 kilo sattım", "bugün ne kadar sattım", "Ali Usta'ya ne kadar sattım"};
        for (String s : notPlan) {
            check(ActionPlan.parse(s) == null, "çok adımlı değil: " + s);
            check(!ActionPlan.VARIANT.equals(decide(s).variant), "beyin çok adımlı yapmaz: " + s);
        }
        Decision elek = decide("Kemal'e 1 numara 120 kilo sattım parası sonra");
        check(elek.is(ActionCatalog.ELEK), "elek satışı cümlesi elekte kalır: " + elek);
        Decision op = decide("bugün Girelli'yi ilaçladık");
        check(op.is(ActionCatalog.ISLEM), "işlem cümlesi değişmez");
    }

    /** Eğitim turu 1: tek adımlı satış. Ödeme söylenmediyse boş (formda sorulur); peşinse alındı; soru soru kalır. */
    static void singleSale() {
        ActionPlan p = ActionPlan.single("Ali Usta'ya 300 kilo sattım");
        check(p != null && p.single(), "tek adımlı satış");
        eq(p.steps.toString(), "[SATIS]", "yalnız satış adımı");
        eq(p.payment, "", "ödeme söylenmedi: varsayılmaz");
        eq(decide("Ali Usta'ya 300 kilo sattım").fieldsText(), "alici=Ali Usta;kilo=300;adimlar=SATIS", "beyin: tek adım");
        eq(decide("Ali Usta'ya 300 kilo sattım").variant, ActionPlan.SINGLE, "değişke");
        eq(ActionPlan.single("Ali Usta'ya 300 kilo sattım, parasını peşin aldım").payment, ActionPlan.PAID, "peşin: alındı");
        eq(ActionPlan.parseAny("Ali Usta'ya 300 kilo sattım").payment, "", "satış formu tek adımlıyı da açar");
        eq(ActionPlan.parseAny("Ali Usta'ya 300 kilo sattım, parasını haftaya verecek").payment, ActionPlan.UNPAID, "çok adımlı önce");
        String[] notSale = {"bu sene kaç kilo sattım", "ne kadar sattım", "Ali Usta'ya ne kadar sattım", "300 kilo sattım",
                "Ali Usta'ya 300 kilo sattım mı", "Ali Usta'ya kaç kilo sattım?", "ali usta'ya 300 kilo sattım"};
        for (String s : notSale) {
            check(ActionPlan.single(s) == null, "tek adımlı satış değil: " + s);
        }
        check(decide("Ali Usta'ya 300 kilo double sattım").is(ActionCatalog.ELEK), "sınıf söylenen satış Elek'te kalır");
        check(ActionPlan.single("Mustafa'ya 200 kilo sattım") != null, "'Mu' ile başlayan ad soru eki sanılmaz");
        eq(ActionPlan.summary("Ali Usta", 300, "", 0, "", ""), "Ali Usta'ya 300 kilo satış.", "özet: ödeme yazılmaz (sorulacak)");
        eq(ActionPlan.summary("Ali Usta", 300, "", 0, "", ActionPlan.PAID), "Ali Usta'ya 300 kilo satış. Para peşin alındı.", "özet: peşin");
        FakeDb db = new FakeDb();
        RunnerFlow f = new RunnerFlow(p, false);
        check(db.rows.isEmpty(), "doldururken hiçbir şey yazılmaz (tek adım)");
        f.filled();
        check(f.confirm(db), "tek onayla yazılır");
        eq(db.log.toString(), "[writeSale]", "yalnız satış yazıldı (hatırlatma yok)");
        eq(f.journal().size(), 1, "geri al kuyruğunda tek adım");
        check(f.undo(db) && !db.rows.contains("sale:100"), "Geri al satışı siler");
        RunnerFlow g = new RunnerFlow(p, false);
        g.cancel();
        check(!g.confirm(new FakeDb()), "Hayır: hiçbir şey yazılmaz");
    }

    static void dates() {
        Calendar thu = thursday();
        String[][] cases = {{"haftaya", "2026-11-26"}, {"yarin", "2026-11-20"}, {"obur gun", "2026-11-21"},
                {"cuma", "2026-11-20"}, {"persembe", "2026-11-26"}, {"pazartesi", "2026-11-23"}, {"ayin 15", "2026-12-15"},
                {"ayin 25", "2026-11-25"}, {"ay sonu", "2026-11-30"}, {"10 gun sonra", "2026-11-29"}};
        for (String[] c : cases) {
            ActionPlan p = ActionPlan.parse("Ali Usta'ya 300 kilo sattım, parasını " + phrase(c[0]) + " verecek");
            check(p != null, "plan: " + c[0]);
            eq(p.due, c[0], "söz: " + c[0]);
            eq(p.dueDate(thu), c[1], "gün: " + c[0]);
        }
        eq(ActionPlan.dayText("2026-11-26"), "26 Kasım'da", "okunan gün");
        eq(ActionPlan.dayText("2026-09-01"), "1 Eylül'de", "eylül");
    }

    static String phrase(String due) {
        switch (due) {
            case "yarin": return "yarın";
            case "obur gun": return "öbür gün";
            case "persembe": return "perşembe";
            case "ayin 15": return "ayın 15'inde";
            case "ayin 25": return "ayın 25'inde";
            case "ay sonu": return "ay sonunda";
            case "10 gun sonra": return "10 gün sonra";
            default: return due;
        }
    }

    /** Sahte veritabanı: yazılan her şey burada görünür. */
    static final class FakeDb implements RunnerFlow.Writer {
        final Set<String> rows = new LinkedHashSet<>();
        final List<String> log = new ArrayList<>();
        long next = 100;
        boolean formIncomplete;
        boolean saleFails;
        boolean reminderFails;
        boolean buyerNew = true;

        @Override
        public long writeSale() {
            log.add("writeSale");
            if (formIncomplete) {
                return 0;
            }
            if (saleFails) {
                if (buyerNew) {
                    rows.add("buyer");
                }
                return -1;
            }
            long id = next++;
            rows.add("sale:" + id);
            rows.add("receivable:" + id);
            if (buyerNew) {
                rows.add("buyer");
            }
            return id;
        }

        @Override
        public long writeReminder(long saleId) {
            log.add("writeReminder");
            if (reminderFails) {
                return -1;
            }
            long id = next++;
            rows.add("reminder:" + id);
            return id;
        }

        @Override
        public boolean deleteSale(long saleId) {
            log.add("deleteSale");
            boolean had = rows.remove("sale:" + saleId);
            rows.remove("receivable:" + saleId);
            return had;
        }

        @Override
        public boolean deleteReminder(long reminderId) {
            log.add("deleteReminder");
            return rows.remove("reminder:" + reminderId);
        }

        @Override
        public void deleteNewBuyer() {
            log.add("deleteNewBuyer");
            if (buyerNew) {
                rows.remove("buyer");
            }
        }
    }

    static ActionPlan example() {
        return ActionPlan.parse("Ali Usta'ya 300 kilo sattım, parasını haftaya verecek");
    }

    static void noWriteWithoutConfirm() {
        FakeDb db = new FakeDb();
        RunnerFlow f = new RunnerFlow(example(), true);
        check(!f.confirm(db), "doldururken onay yok: yazılmaz");
        check(db.log.isEmpty() && db.rows.isEmpty(), "doldururken hiçbir şey yazılmadı");
        f.filled();
        eq(f.state(), RunnerFlow.State.CONFIRMING, "Kaydedeyim mi?");
        check(db.rows.isEmpty() && f.journal().isEmpty(), "soru sorulurken de yazılmadı");
        for (String act : new String[]{ActionCatalog.SATIS, ActionCatalog.TAHSILAT, ActionCatalog.ISLEM, ActionCatalog.ELEK,
                ActionCatalog.HASAT}) {
            check(ActionCatalog.alwaysConfirm(act), "katalog: hep onaylı " + act);
        }
    }

    static void confirmWritesAll() {
        FakeDb db = new FakeDb();
        RunnerFlow f = new RunnerFlow(example(), true);
        f.filled();
        check(f.confirm(db), "tek onay");
        eq(f.state(), RunnerFlow.State.SAVED, "kaydedildi");
        eq(db.log.toString(), "[writeSale, writeReminder]", "sıra: satış (alacağıyla), sonra hatırlatma");
        check(db.rows.contains("sale:100") && db.rows.contains("receivable:100") && db.rows.contains("reminder:101"),
                "üç kayıt: satış, alacak, hatırlatma " + db.rows);
        eq(f.journal().size(), 3, "geri al kuyruğunda üç adım");
        check(!f.confirm(db), "ikinci onay ikinci kez yazmaz");
        eq(db.log.size(), 2, "çift kayıt yok");
        FakeDb noDue = new FakeDb();
        RunnerFlow g = new RunnerFlow(ActionPlan.parse("Kemal'e 120 kilo sattım parası sonra"), false);
        g.filled();
        check(g.confirm(noDue) && !noDue.log.contains("writeReminder"), "ödeme günü yoksa hatırlatma yazılmaz");
        FakeDb incomplete = new FakeDb();
        incomplete.formIncomplete = true;
        RunnerFlow h = new RunnerFlow(example(), true);
        h.filled();
        check(!h.confirm(incomplete) && h.state() == RunnerFlow.State.CONFIRMING && incomplete.rows.isEmpty(),
                "form eksikse hiçbir şey yazılmaz, soru sürer");
        incomplete.formIncomplete = false;
        check(h.confirm(incomplete) && incomplete.rows.size() == 4, "düzeltince aynı onayla yazılır");
    }

    static void undoAll() {
        FakeDb db = new FakeDb();
        RunnerFlow f = new RunnerFlow(example(), true);
        f.filled();
        f.confirm(db);
        db.log.clear();
        check(f.undo(db), "Geri al");
        eq(db.log.toString(), "[deleteReminder, deleteSale, deleteNewBuyer]", "ters sırayla, hepsi");
        check(db.rows.isEmpty(), "hiçbiri kalmadı (yeni alıcı da) " + db.rows);
        eq(f.state(), RunnerFlow.State.UNDONE, "geri alındı");
        check(!f.undo(db), "ikinci Geri al yok");
        FakeDb old = new FakeDb();
        old.buyerNew = false;
        old.rows.add("buyer-eski");
        RunnerFlow g = new RunnerFlow(example(), true);
        g.filled();
        g.confirm(old);
        g.undo(old);
        check(old.rows.contains("buyer-eski") && old.rows.size() == 1, "rehberde önceden olan alıcıya dokunulmaz");
        check(!new RunnerFlow(example(), true).undo(new FakeDb()), "onaysız akışta geri alınacak bir şey yok");
    }

    /** Yarım kalan akış (Durdur, Hayır, geri): hiçbir şey yazılmaz. */
    static void halfFlowLeavesNothing() {
        FakeDb db = new FakeDb();
        RunnerFlow filling = new RunnerFlow(example(), true);
        filling.cancel();
        eq(filling.state(), RunnerFlow.State.CANCELLED, "doldururken durduruldu");
        check(!filling.confirm(db), "vazgeçilen akış sonradan onaylanamaz");
        RunnerFlow asking = new RunnerFlow(example(), true);
        asking.filled();
        asking.cancel();
        check(!asking.confirm(db) && !asking.undo(db), "Hayır: yazma da geri alma da yok");
        check(db.log.isEmpty() && db.rows.isEmpty(), "yarım akış iz bırakmadı");
    }

    static void allOrNothing() {
        FakeDb db = new FakeDb();
        db.reminderFails = true;
        RunnerFlow f = new RunnerFlow(example(), true);
        f.filled();
        check(!f.confirm(db), "hatırlatma yazılamadı");
        eq(f.state(), RunnerFlow.State.FAILED, "başarısız");
        check(db.rows.isEmpty(), "satış ve alacak da geri alındı: ya hepsi ya hiçbiri " + db.rows);
        FakeDb sale = new FakeDb();
        sale.saleFails = true;
        RunnerFlow g = new RunnerFlow(example(), true);
        g.filled();
        check(!g.confirm(sale) && sale.rows.isEmpty() && !sale.log.contains("writeReminder"), "satış yazılamazsa hiçbir şey kalmaz");
    }

    static void permission() {
        String[] never = {ActionCatalog.SATIS, ActionCatalog.TAHSILAT, ActionCatalog.ELEK, ActionCatalog.ISLEM, ActionCatalog.HASAT,
                "SIL", "KAYIT_BAHCE"};
        for (String act : never) {
            check(!HarmlessPermission.mayDoAlone(act, true), "izinle bile onaysız değil: " + act);
        }
        check(HarmlessPermission.mayDoAlone(ActionCatalog.HATIRLATMA, true), "izin verildiyse hatırlatma sormadan");
        check(!HarmlessPermission.mayDoAlone(ActionCatalog.HATIRLATMA, false), "izin yoksa hatırlatma da sorulur");
        check(HarmlessPermission.told("26 Kasım'da").contains("haber") || HarmlessPermission.told("26 Kasım'da").contains("kurdum"),
                "sormadan yapılınca haber verilir");
        eq(ActionCatalog.get(ActionCatalog.HATIRLATMA).level, ActionCatalog.Level.IZINLE_KENDISI, "katalog: hatırlatma izinle kendisi");
    }

    static void texts() {
        eq(ActionPlan.summary("Ali Usta", 300, "3 No", 68, "2026-11-26"),
                "Ali Usta'ya 300 kilo 3 No, kilosu 68 liradan (20.400 TL) satış. Para alınmadı. 26 Kasım'da ödeme günü hatırlatması.",
                "özet");
        eq(ActionPlan.ablative("Ali Usta"), "Ali Usta'dan", "ayrılma");
        eq(ActionPlan.ablative("Mehmet"), "Mehmet'ten", "ayrılma sert");
        eq(ActionPlan.ablative("Ayşe"), "Ayşe'den", "ayrılma ünlü");
        String k = "";
        k = AssistantScorecard.count(k, AssistantScorecard.MULTI_STARTED, 1);
        k = AssistantScorecard.count(k, AssistantScorecard.MULTI_STARTED, 1);
        k = AssistantScorecard.count(k, AssistantScorecard.MULTI_CONFIRMED, 1);
        k = AssistantScorecard.count(k, AssistantScorecard.MULTI_UNDONE, 1);
        eq(AssistantScorecard.multiSteps(k), "\n• Çok adımlı iş: 2 başladı, 1 onaylandı (%50), 1 geri alındı", "karne satırı");
        check(AssistantScorecard.show(k)[1].contains("Çok adımlı iş"), "karnede görünür");
        check(AssistantScorecard.multiSteps("").isEmpty(), "hiç yoksa satır yok");
    }
}
