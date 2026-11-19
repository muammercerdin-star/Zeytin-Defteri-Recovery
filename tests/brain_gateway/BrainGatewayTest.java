package com.zeytindefteri.app;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tek kapı (İP3 Oturum 2):
 * 1. İnternet yardımcısı (CloudBrain) eski koşulla aynı cümlelerde devreye girer, cevabı aynı denetimden geçer,
 *    kararı en fazla formu doldurur ve onay belgesi doğurmaz.
 * 2. Elek "Sesle doldur" eski çözümle (ElekVoiceEntry.parse + düzeltme sözü atma) her cümlede aynı sonucu verir.
 * 3. Aynı elek cümlesi asistandan (ses ya da yazı) ve Elek ekranından aynı alanlarla anlaşılır.
 */
public class BrainGatewayTest {
    static int n = 0;
    static int bad = 0;
    static final List<String> G = Arrays.asList("Girelli", "Kuyu dibi");

    static void check(boolean ok, String m) {
        n++;
        if (!ok) {
            bad++;
            if (bad <= 40) {
                System.out.println("FAIL " + m);
            }
        }
    }

    static String ok(String result) {
        return "{\"ok\":true,\"result\":" + result + "}";
    }

    /** Taşımadan önceki Elek diyaloğu ön temizliği (ElekVoiceDialog.forParse, commit f5ccb3a): dondurulmuş kopya. */
    static String oldForParse(String raw) {
        return raw == null ? "" : raw.replaceAll("(?iu)\\b(de[gğ]i[sş]tir|g[uü]ncelle)\\p{L}*", " ");
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        List<String> corpus = BrainEquivalenceTest.corpus(root);
        List<String> withEdits = new ArrayList<>(corpus);
        withEdits.add("3 numarayı 100 olarak değiştir");
        withEdits.add("double'ı 25 kilo olarak güncelle");
        withEdits.add("2 numara 80 kilo değiştirdim");
        cloudWants(corpus);
        cloudDecides();
        elekScreen(withEdits);
        sameEverywhere(corpus);
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " BrainGateway (tek kapı)");
        if (bad > 0) {
            System.exit(1);
        }
    }

    /** Eski koşul: pick==null && cmd.kind!=NAVIGATION && bahçe var && special(raw)==null && eligible(raw, names). */
    static void cloudWants(List<String> corpus) {
        int yes = 0;
        for (String raw : corpus) {
            ZeytinAssistantCommand cmd = ZeytinAssistantCommand.parse(raw);
            boolean old = cmd.kind != ZeytinAssistantCommand.Kind.NAVIGATION && ZeytinAssistantText.special(raw) == null
                    && AssistantCloud.eligible(raw, G);
            boolean now = CloudBrain.wants(raw, cmd, G);
            check(old == now, "internet yardımcısı koşulu aynı: «" + raw + "» eski " + old + " yeni " + now);
            check(!CloudBrain.wants(raw, cmd, new ArrayList<String>()), "bahçesi olmayana sorulmaz: «" + raw + "»");
            if (now) {
                yes++;
            }
        }
        check(yes > 50, "yardımcıya gidebilecek cümleler sınandı: " + yes);
    }

    static void cloudDecides() {
        String[][] cases = {
                {"{\"islem\":\"iscilik\",\"bahce\":\"Kuyu dibi\",\"gun\":-1,\"miktar\":4,\"birim\":\"saat\",\"tutar\":null}", "Dün Kuyu dibinde damlamayı açtık 4 saat"},
                {"{\"islem\":\"ilaclama\",\"bahce\":\"Girelli\",\"gun\":0,\"miktar\":3,\"birim\":\"kg\",\"tutar\":450}", "Bugün bahçeye ilaç vurduk kükürt 3 kilo 450 lira gitti"},
                {"{\"islem\":\"budama\",\"bahce\":\"Girelli\",\"gun\":0,\"miktar\":30,\"birim\":\"agac\",\"tutar\":1500}", "Girelli'de 30 ağaç budattık 1500 lira"},
                {"{\"islem\":\"gubre\",\"bahce\":\"Kuyu dibi\",\"gun\":-2,\"miktar\":20,\"birim\":\"kg\",\"tutar\":null}", "evvelsi gün Kuyu dibine 20 kilo gübre attık"},
                {"bozuk cevap", "ağaçların altını temizledik"},
        };
        BrainContext ctx = BrainContext.recordsOnly(G);
        for (String[] c : cases) {
            final String response = ok(c[0]);
            CloudBrain brain = new CloudBrain((raw, gardens) -> response);
            Decision d = brain.decide(new Brain.Input(c[1]), ctx);
            AssistantCloud.Result old = AssistantCloud.accept(response, c[1], G);
            AssistantCloud.Result now = d.payload(AssistantCloud.Result.class);
            if (old == null) {
                check(!d.understood() && now == null, "denetimden geçmeyen cevap anlaşılmadı: «" + c[1] + "»");
                continue;
            }
            check(now != null && now.garden == old.garden && now.entry.type == old.entry.type
                    && now.entry.quantity == old.entry.quantity && now.entry.cost == old.entry.cost
                    && now.entry.dayOffset == old.entry.dayOffset && now.entry.unit.equals(old.entry.unit),
                    "aynı denetim, aynı sonuç: «" + c[1] + "»");
            check(d.is(ActionCatalog.ISLEM) && "soru".equals(d.variant) && d.brain.equals("internet"), "işlem kararı, sorarak: " + d);
            check(d.confidence > 0 && d.confidence < 1, "güven kuraldan düşük: " + d.confidence);
            check(!d.reason.isEmpty(), "gerekçe var");
            check(ActionGate.allowed(d) == ActionCatalog.Level.FORMU_DOLDUR && !ActionGate.mayActAlone(d), "yardımcı en fazla formu doldurur");
            check(!ActionGate.mayWrite(ActionCatalog.ISLEM, null), "yardımcının kararı onay belgesi değildir");
        }
        Decision none = new CloudBrain((raw, gardens) -> null).decide(new Brain.Input("ağaçları temizledik"), ctx);
        check(!none.understood(), "internet yoksa anlaşılmadı");
    }

    /** Elek "Sesle doldur": eski çözüm ↔ kapıdan geçen karar, her cümlede. */
    static void elekScreen(List<String> corpus) {
        Brain gate = new ElekRules();
        for (String raw : corpus) {
            ElekVoiceEntry old = ElekVoiceEntry.parse(oldForParse(raw));
            Decision d = gate.decide(new Brain.Input(raw), BrainContext.elekScreen());
            ElekVoiceEntry now = d.payload(ElekVoiceEntry.class);
            if (old == null) {
                check(now == null && !d.understood(), "Elek ekranında elek değil: «" + raw + "» → " + d);
                continue;
            }
            check(now != null && d.is(ActionCatalog.ELEK), "Elek ekranında elek: «" + raw + "»");
            check(ElekRules.fields(old).equals(d.fields), "aynı alanlar: «" + raw + "» eski " + ElekRules.fields(old) + " yeni " + d.fields);
            check(ActionGate.allowed(d) == ActionCatalog.Level.FORMU_DOLDUR, "elek en fazla formu doldurur");
        }
        RuleBrain rules = new RuleBrain();
        Decision viaRules = rules.decide(new Brain.Input("3 numarayı 100 olarak değiştir"), BrainContext.elekScreen());
        check(viaRules.is(ActionCatalog.ELEK) && "100".equals(viaRules.fields.get("3no")), "kural beyni de Elek ekranında aynı parçayı kullanır: " + viaRules);
    }

    /** Asistan (ses ve yazı aynı cümle olarak gelir) ↔ Elek ekranı: elek cümlesi aynı alanlarla. */
    static void sameEverywhere(List<String> corpus) {
        RuleBrain rules = new RuleBrain();
        Brain elek = new ElekRules();
        int compared = 0;
        for (String raw : corpus) {
            Decision assistant = rules.decide(new Brain.Input(raw), BrainContext.full(true, AssistantPersona.decode("")::handle));
            if (!assistant.is(ActionCatalog.ELEK)) {
                continue;
            }
            if (!oldForParse(raw).equals(raw)) {
                continue;   // düzeltme sözü: yalnız Elek ekranında anlamlı
            }
            Decision screen = elek.decide(new Brain.Input(raw), BrainContext.elekScreen());
            compared++;
            check(screen.is(ActionCatalog.ELEK) && screen.fields.equals(assistant.fields),
                    "asistan ve Elek aynı: «" + raw + "» asistan " + assistant.fields + " Elek " + screen.fields);
        }
        check(compared > 50, "asistan ↔ Elek karşılaştırması: " + compared + " cümle");
        System.out.println("Asistan ↔ Elek ekranı: " + compared + " elek cümlesi karşılaştırıldı");
    }
}
