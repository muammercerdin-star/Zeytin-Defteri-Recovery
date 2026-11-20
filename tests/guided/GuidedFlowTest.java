package com.zeytindefteri.app;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Yönlendiren asistan veri katmanı: düğmelerin aktığı sorular katalogla tutarlı; her düğmenin cümlesi bugünkü yolda
 * doğru işe gider (yeni kayıt yolu yok); karşılama sıralaması; kısa yolların beyinde anlaşıldığı; alıştırma kayıt yazmaz.
 */
public class GuidedFlowTest {
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

    static Decision decide(String raw) {
        return new RuleBrain().decide(new Brain.Input(raw), BrainContext.full(true, AssistantPersona.decode("")::handle));
    }

    public static void main(String[] a) {
        catalog();
        starters();
        ordering();
        shortcuts();
        examplesAndPractice();
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " GuidedFlow");
        if (bad > 0) {
            System.exit(1);
        }
    }

    /** Her düğmenin ön sorusu katalogda var olan bir alanın sorusu; boş soru yok. */
    static void catalog() {
        for (GuidedFlow.Job j : GuidedFlow.all()) {
            check(ActionCatalog.get(j.action) != null, "katalogda eylem: " + j.id);
            check(j.starter.isEmpty() != j.asks.isEmpty(), "ya başlangıç cümlesi ya ön sorular: " + j.id);
            for (GuidedFlow.Ask ask : j.asks) {
                check(ActionCatalog.get(ask.action).fields.contains(ask.field), "katalogda alan: " + j.id + "/" + ask.field);
                check(!ask.question().isEmpty(), "soru katalogdan geliyor: " + j.id + "/" + ask.field);
            }
        }
        eq(GuidedFlow.job("MASRAF").asks.get(0).question(), "Ne için harcadın?", "masraf sorusu");
        eq(GuidedFlow.job("SATIS").asks.get(0).question(), "Kime sattın?", "satış: alıcı sorusu");
    }

    /** Her düğmenin cümlesi bugünkü beyinde doğru işe gider: ayrı kayıt yolu yazılmadı. */
    static void starters() {
        for (GuidedFlow.Job j : GuidedFlow.all()) {
            if (j.starter.isEmpty()) {
                continue;
            }
            Decision d = decide(j.starter);
            check(d.is(j.action), "başlangıç cümlesi işe gider: " + j.starter + " → " + d);
            if (!j.type.isEmpty()) {
                eq(d.fields.get("tur"), j.type, "tür: " + j.starter);
            }
        }
        for (String[] e : GuidedFlow.EXPENSES) {
            if (e[1].isEmpty()) {
                continue;
            }
            Decision d = decide(e[1]);
            check(d.is(ActionCatalog.ISLEM), "masraf seçeneği işlem kaydına gider: " + e[0] + " → " + d);
        }
        Map<String, String> sale = new HashMap<>();
        sale.put("alici", "Ali Usta");
        sale.put("kilo", "üç yüz");
        eq(GuidedFlow.sentence(GuidedFlow.job("SATIS"), sale), "Ali Usta'ya 300 kilo sattım", "satış cümlesi");
        Decision s = decide(GuidedFlow.sentence(GuidedFlow.job("SATIS"), sale));
        check(s.is(ActionCatalog.SATIS) && ActionPlan.SINGLE.equals(s.variant), "satış İP7 uygulayıcısına (tek adım) gider");
        sale.put("fiyat", "140");
        eq(decide(GuidedFlow.sentence(GuidedFlow.job("SATIS"), sale)).fields.get("fiyat"), "140", "fiyat da gider");
        Map<String, String> pay = new HashMap<>();
        pay.put("alici", "Mehmet");
        pay.put("tutar", "2000");
        eq(GuidedFlow.sentence(GuidedFlow.job("TAHSILAT"), pay), "Mehmet'ten 2000 lira aldım", "tahsilat cümlesi");
        check(decide(GuidedFlow.sentence(GuidedFlow.job("TAHSILAT"), pay)).is(ActionCatalog.TAHSILAT), "tahsilat yoluna gider");
        pay.remove("tutar");
        eq(GuidedFlow.sentence(GuidedFlow.job("TAHSILAT"), pay), "", "eksik zorunlu cevap: cümle yok");
        Map<String, String> other = new HashMap<>();
        other.put("tur", "Başka");
        eq(GuidedFlow.sentence(GuidedFlow.job("MASRAF"), other), "", "masrafta 'Başka': çiftçi söyler");
        eq(GuidedFlow.ablative("Ali Usta"), "Ali Usta'dan", "ayrılma");
        eq(GuidedFlow.locative("Girelli"), "Girelli'de", "bulunma");
        eq(GuidedFlow.locative("Karşıyaka"), "Karşıyaka'da", "bulunma (kalın)");
        eq(GuidedFlow.ablative("Kuyu Dibi"), "Kuyu Dibi'nden".replace("nden", "den"), "ayrılma (ince)");
        eq(GuidedFlow.stepChoices("bahce", Arrays.asList("Girelli", "Kuyu"), "", false).toString(), "[Girelli, Kuyu]", "bahçe düğmeleri");
        eq(GuidedFlow.stepChoices("not", null, "Bakır", true).toString(), "[Bakır, Geç]", "alışkanlık en üstte, isteğe bağlıda Geç");
        eq(GuidedFlow.progress(2, 3), "2 / 3", "ilerleme");
    }

    /** Dört yer sabit; içleri sıklık ve mevsimle değişir; kayıt yoksa İlaçlama, Hasat, Satış, Masraf. */
    static void ordering() {
        eq(ids(GuidedFlow.home(new HashMap<>(), 5)), "[ILACLAMA, HASAT, SATIS, MASRAF]", "kayıt yok, mayıs");
        eq(GuidedFlow.home(new HashMap<>(), 11).size(), 4, "hep dört düğme");
        Map<String, Integer> counts = new HashMap<>();
        counts.put("SULAMA", 9);
        counts.put("ILACLAMA", 1);
        List<GuidedFlow.Job> july = GuidedFlow.home(counts, 7);
        eq(july.get(0).id, "SULAMA", "temmuzda çok sulayan: Sulama önde");
        eq(ids(GuidedFlow.home(new HashMap<>(), 11)).substring(0, 14), "[HASAT, SATIS,", "kasımda hasat ve satış önde");
        List<GuidedFlow.Job> home = GuidedFlow.home(new HashMap<>(), 5);
        List<GuidedFlow.Job> rest = GuidedFlow.others(home);
        eq(home.size() + rest.size(), GuidedFlow.all().size(), "Başka bir iş: kalan bütün işler");
        check(GuideSettings.inMonths(1, GuideSettings.HARVEST_CARD_FROM, GuideSettings.HARVEST_CARD_TO), "ocak hasat aralığında");
        check(!GuideSettings.inMonths(3, GuideSettings.HARVEST_CARD_FROM, GuideSettings.HARVEST_CARD_TO), "mart değil");
        eq(GuidedFlow.jobOfType(ActionCatalog.ISLEM, "Sulama"), "SULAMA", "kayıt türünden düğme");
        eq(GuidedFlow.jobOfType(ActionCatalog.ISLEM, "Mazot"), "MASRAF", "mazot masraf düğmesi");
        eq(GuidedFlow.jobOfType(ActionCatalog.HASAT, ""), "HASAT", "hasat");
    }

    /** Kısa yol cümleleri beyinde gerçekten aynı işe çevriliyor; anlaşılmayan önerilmez; en çok 3 kez. */
    static void shortcuts() {
        Object[][] cases = {
                {ActionCatalog.ISLEM, "Sulama", "Girelli", 3.0, "saat", "Girelli'de 3 saat suladık"},
                {ActionCatalog.ISLEM, "İlaçlama", "Girelli", 2.0, "kg", "Girelli'de 2 kilo ilaçladık"},
                {ActionCatalog.ISLEM, "Budama", "Kuyu", 0.0, "", "Kuyu'da budadık"},
                {ActionCatalog.ISLEM, "Gübre", "", 0.0, "", "bugün gübre attık"},
                {ActionCatalog.ISLEM, "Mazot", "", 40.0, "L", "bugün 40 litre mazot aldık"},
                {ActionCatalog.HASAT, "", "Girelli", 300.0, "kg", "Girelli'den 300 kilo topladık"},
        };
        for (Object[] c : cases) {
            String s = GuidedFlow.shortcut((String) c[0], (String) c[1], (String) c[2], (Double) c[3], (String) c[4]);
            eq(s, c[5], "kısa yol");
            check(GuidedFlow.verified(s, (String) c[0], (String) c[1], (Double) c[3]), "kısa yol beyinde anlaşılıyor: " + s);
        }
        check(!GuidedFlow.verified("Girelli'de 3 saat suladık", ActionCatalog.ISLEM, "İlaçlama", 3), "yanlış türe giden cümle önerilmez");
        check(!GuidedFlow.verified("Girelli'de suladık", ActionCatalog.ISLEM, "Sulama", 3), "miktarı kaybeden cümle önerilmez");
        check(!GuidedFlow.verified("", ActionCatalog.ISLEM, "Sulama", 0), "boş cümle önerilmez");
        eq(GuidedFlow.shortcut(ActionCatalog.ISLEM, "Diğer", "Girelli", 0, ""), "", "kurulamayan kısa yol önerilmez");
        check(GuidedFlow.shortcutAllowed(0) && GuidedFlow.shortcutAllowed(2) && !GuidedFlow.shortcutAllowed(3), "en çok 3 kez");
    }

    /** "Neler yapabilirsin?" örnekleri anlaşılıyor; alıştırma kayıt yazmaz. */
    static void examplesAndPractice() {
        List<String> ex = GuidedFlow.examples(Arrays.asList("Girelli"), Arrays.asList("Ali Usta"));
        eq(ex.size(), 4, "dört örnek");
        eq(ex.get(0), "Girelli'de 3 saat suladık", "kendi bahçe adıyla");
        eq(ex.get(2), "Ali Usta'ya 300 kilo sattım", "kendi alıcı adıyla");
        for (String e : ex) {
            check(decide(e).understood(), "örnek anlaşılıyor: " + e);
        }
        List<String> empty = GuidedFlow.examples(null, null);
        eq(empty.get(0), "bugün 3 saat suladık", "adlar yoksa genel örnek");
        Decision d = decide(GuidedFlow.PRACTICE);
        check(d.is(ActionCatalog.ISLEM), "alıştırma cümlesi anlaşılıyor");
        String reply = GuidedFlow.practiceReply(d);
        check(reply.startsWith("Anladım: sulama, bugün.") && reply.contains("hiçbir şey kaydetmedim"), "alıştırma cevabı: " + reply);
        check(GuidedFlow.practiceReply(decide("abuk sabuk")).contains("Bir daha dene"), "anlamadıysa yeniden dene");

        // Karne: "Yönlendirme" satırı türleri toplar, boşken sıfır yazar.
        Map<String, Long> c = new java.util.HashMap<>();
        c.put("kart_goster:odeme_gunu", 3L);
        c.put("kart_goster:hasat", 2L);
        c.put("kart_kabul:odeme_gunu", 1L);
        c.put("kart_red:hasat", 2L);
        c.put("dugme_is:ILACLAMA", 4L);
        c.put("dugme_is:SATIS", 1L);
        c.put("kisa_yol:ILACLAMA", 2L);
        c.put("kisa_yoldan_sonra_konusarak", 1L);
        c.put("sahne_oynatildi:GOSTER", 5L);
        c.put("sahne_oynatildi:OGRET", 1L);
        c.put("sahne_bitti:GOSTER", 4L);
        c.put("sahne_durdu:OGRET", 1L);
        String k = GuidedFlow.karne(c);
        check(k.startsWith("YÖNLENDİRME"), "karne başlığı");
        check(k.contains("Kart: 5 gösterildi · 1 kabul · 2 red"), "karne kartlar: " + k);
        check(k.contains("Düğmeyle başlayan iş: 5"), "karne düğme işi");
        check(k.contains("Kısa yoldan sonra konuşarak: 1"), "karne kısa yol");
        check(k.contains("Sahne: 6 oynatıldı · 4 sonuna kadar · 1 durduruldu"), "karne sahneler");
        check(GuidedFlow.karne(null).contains("Kart: 0 gösterildi · 0 kabul · 0 red"), "boş karne");
    }

    static String ids(List<GuidedFlow.Job> jobs) {
        StringBuilder b = new StringBuilder("[");
        for (GuidedFlow.Job j : jobs) {
            if (b.length() > 1) {
                b.append(", ");
            }
            b.append(j.id);
        }
        return b.append(']').toString();
    }
}
