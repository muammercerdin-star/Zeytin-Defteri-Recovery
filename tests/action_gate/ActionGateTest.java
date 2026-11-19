package com.zeytindefteri.app;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Onay kuralının delinemezliği (İP3): kayıt, satış, ödeme ve silme hiçbir beyin çıktısıyla onaysız yazılamaz.
 * "Kötü beyin": her eylemi güven 1, bütün alanlar dolu ve "kendisi yapsın" isteğiyle üretir.
 */
public class ActionGateTest {
    static int n = 0;
    static int bad = 0;

    static void check(boolean ok, String m) {
        n++;
        if (!ok) {
            bad++;
            System.out.println("FAIL " + m);
        }
    }

    /** Ne kadar emin olursa olsun her şeyi kendisi yapmak isteyen beyin. */
    static final class GreedyBrain implements Brain {
        final String action;

        GreedyBrain(String action) {
            this.action = action;
        }

        @Override
        public String name() {
            return "kotu";
        }

        @Override
        public Decision decide(Input input, BrainContext context) {
            Map<String, String> f = new LinkedHashMap<>();
            ActionCatalog.Action a = ActionCatalog.get(action);
            if (a != null) {
                for (String key : a.fields) {
                    f.put(key, "1");
                }
            }
            return new Decision(action, f, 1.0, "çok eminim", "", ActionCatalog.Level.IZINLE_KENDISI, null, name());
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        greedyBrains();
        ruleBrainOnCorpus(root);
        approvalCannotBeForged();
        approvalOnlyFromYes();
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " ActionGate (onay kuralı delinemez)");
        if (bad > 0) {
            System.exit(1);
        }
    }

    static void greedyBrains() {
        BrainContext ctx = BrainContext.full(true, AssistantPersona.decode("")::handle);
        int confirmed = 0;
        for (ActionCatalog.Action a : ActionCatalog.all()) {
            String id = a.family.isEmpty() ? a.id : a.family + "SPRAY";
            Decision d = new GreedyBrain(id).decide(new Brain.Input("ne olursa"), ctx);
            ActionCatalog.Level level = ActionGate.allowed(d);
            if (a.alwaysConfirm()) {
                confirmed++;
                check(level.ordinal() <= ActionCatalog.Level.FORMU_DOLDUR.ordinal(), "kötü beyin bile en fazla formu doldurur: " + id + " → " + level);
                check(!ActionGate.mayActAlone(d), "onaylı eylem sormadan yapılamaz: " + id);
                check(!ActionGate.mayWrite(id, null), "onay belgesiz yazılamaz: " + id);
            }
        }
        check(confirmed >= 9, "onaylı eylemler sınandı: " + confirmed);
        Decision unknown = new GreedyBrain("HER_SEYI_SIL").decide(new Brain.Input("x"), ctx);
        check(ActionGate.allowed(unknown) == ActionCatalog.Level.ONER, "katalogda olmayan eylem yalnız önerilir");
        check(!ActionGate.mayActAlone(unknown), "bilinmeyen eylem sormadan yapılamaz");
        check(ActionGate.allowed(null) == ActionCatalog.Level.ONER, "karar yoksa öneri");
        Decision humble = new Decision(ActionCatalog.SORU + "SPRAY", null, 1, "soru", "", ActionCatalog.Level.ONER, null, "x");
        check(ActionGate.allowed(humble) == ActionCatalog.Level.ONER, "beyin daha düşük basamak isterse o geçerli");
        Decision question = new Decision(ActionCatalog.SORU + "SPRAY", null, 1, "soru", "", ActionCatalog.Level.IZINLE_KENDISI, null, "x");
        check(ActionGate.mayActAlone(question), "yazmayan soru kendiliğinden cevaplanır (bugünkü gibi)");
    }

    static void ruleBrainOnCorpus(Path root) throws Exception {
        RuleBrain brain = new RuleBrain();
        int records = 0;
        for (String raw : BrainEquivalenceTest.corpus(root)) {
            for (boolean gardens : new boolean[]{true, false}) {
                Decision d = brain.decide(new Brain.Input(raw), BrainContext.full(gardens, AssistantPersona.decode("")::handle));
                if (ActionCatalog.alwaysConfirm(d.action)) {
                    records++;
                    if (ActionGate.allowed(d).ordinal() > ActionCatalog.Level.FORMU_DOLDUR.ordinal() || ActionGate.mayActAlone(d)) {
                        check(false, "kural beyni onaylı eylemi sormadan yaptırıyor: «" + raw + "» " + d);
                    }
                }
            }
        }
        check(records > 300, "kural beyninin kayıt kararları sınandı: " + records);
    }

    static void approvalCannotBeForged() {
        for (Constructor<?> c : Approval.class.getDeclaredConstructors()) {
            check(Modifier.isPrivate(c.getModifiers()), "onay belgesinin kurucusu gizli");
        }
        for (Method m : Approval.class.getDeclaredMethods()) {
            if (!Modifier.isStatic(m.getModifiers()) || m.getReturnType() != Approval.class) {
                continue;
            }
            boolean fromDialog = Arrays.equals(m.getParameterTypes(), new Class<?>[]{OperationVoiceDialog.class})
                    || Arrays.equals(m.getParameterTypes(), new Class<?>[]{ElekVoiceDialog.class});
            check(fromDialog, "onay belgesi yalnız sesli onaylı konuşmadan: " + m);
            for (Class<?> p : m.getParameterTypes()) {
                check(p != Decision.class && p != Brain.class, "beyin kararından onay belgesi üretilemez: " + m);
            }
        }
    }

    static OperationVoiceDialog dialogAtConfirm() {
        OperationVoiceEntry e = OperationVoiceEntry.parse("Bugün ilaçladım");
        OperationVoiceDialog d = new OperationVoiceDialog(e, Arrays.asList("Girelli Zeytinliği"), 0);
        d.start();
        d.handle("Bakır attım");
        d.handle("500 gram");
        d.handle("300 lira");
        return d;
    }

    static void approvalOnlyFromYes() {
        OperationVoiceDialog d = dialogAtConfirm();
        check(d.step() == OperationVoiceDialog.Step.CONFIRM, "özet okundu, onay bekleniyor");
        check(Approval.operationConfirmed(d) == null, "özet okunması onay değildir");
        OperationVoiceDialog.Reply forged = new OperationVoiceDialog.Reply("Kaydettim.", OperationVoiceDialog.Action.SAVE);
        check(forged.action == OperationVoiceDialog.Action.SAVE && Approval.operationConfirmed(d) == null, "sahte 'kaydet' cevabı onay doğurmaz");
        d.handle("şey");
        check(Approval.operationConfirmed(d) == null, "belirsiz cevap onay değildir");
        OperationVoiceDialog.Reply r = d.handle("evet");
        Approval ok = Approval.operationConfirmed(d);
        check(r.action == OperationVoiceDialog.Action.SAVE && ok != null, "çiftçinin 'evet'i onay belgesi doğurur");
        check(ActionGate.mayWrite(ActionCatalog.ISLEM, ok), "onaylı işlem yazılır");
        check(!ActionGate.mayWrite(ActionCatalog.ELEK, ok) && !ActionGate.mayWrite(ActionCatalog.SATIS, ok)
                && !ActionGate.mayWrite(ActionCatalog.TAHSILAT, ok), "işlem onayı başka eylemi kapsamaz");
        check(Approval.operationConfirmed(null) == null, "konuşma yoksa onay yok");

        OperationVoiceDialog no = dialogAtConfirm();
        no.handle("hayır");
        OperationVoiceDialog.Reply form = no.handle("hayır");
        check(form.action == OperationVoiceDialog.Action.OPEN_FORM && Approval.operationConfirmed(no) == null, "iki kez hayır: form açılır, onay yok");
    }
}
