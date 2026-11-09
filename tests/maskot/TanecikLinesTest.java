package com.zeytindefteri.app;

import java.util.List;

public class TanecikLinesTest {
    static int checks;
    static void ok(boolean c, String m) { checks++; if (!c) throw new AssertionError(m); }

    static java.util.List<String> texts(List<TanecikLines.Line> l) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (TanecikLines.Line x : l) out.add(x.text);
        return out;
    }

    static TanecikLines.Line find(List<TanecikLines.Line> l, String part) {
        for (TanecikLines.Line x : l) if (x.text.contains(part)) return x;
        return null;
    }

    /** Her alanı dolu durum: bütün sözler üretilsin. */
    static TanecikLines.Facts full() {
        TanecikLines.Facts f = new TanecikLines.Facts();
        f.raining = false; f.rainChance = 10; f.tomorrowRain = 70; f.windKmh = 30; f.tempC = 8; f.hasGarden = true;
        f.daysSinceHarvest = 4; f.receivableCount = 2; f.receivableTotal = 1000; f.bestBuyer = "Özer"; f.bestBuyerPerKg = 3;
        f.todayKg = 0; f.seasonKg = 900; f.seasonTl = 40000; f.oldestReceivableDays = 20; f.oldestReceivableBuyer = "Ali";
        return f;
    }

    static boolean has(List<TanecikLines.Line> l, String part) { return find(l, part) != null; }

    public static void main(String[] a) {
        ok(TanecikLines.sleeping(22) && TanecikLines.sleeping(0) && TanecikLines.sleeping(4), "gece uyur");
        ok(!TanecikLines.sleeping(5) && !TanecikLines.sleeping(21), "gündüz uyumaz");
        ok(TanecikLines.greeting(7).startsWith("Günaydın"), "sabah");
        ok(TanecikLines.greeting(13).startsWith("Kolay gelsin"), "öğle");
        ok(TanecikLines.greeting(17).startsWith("İyi günler"), "ikindi");
        ok(TanecikLines.greeting(20).startsWith("İyi akşamlar"), "akşam");
        for (int m = 1; m <= 12; m++) {
            List<String> s = TanecikLines.season(m);
            ok(!s.isEmpty(), "her ayın sözü var: " + m);
            for (int h = 0; h < 24; h++) for (String l : TanecikLines.lines(h, m)) {
                ok(!l.isEmpty() && l.length() <= 110, "balona sığar (" + l.length() + "): " + l);
            }
        }
        ok(TanecikLines.season(11).get(0).startsWith("Hasat"), "kasım hasat");
        ok(TanecikLines.season(2).get(0).startsWith("Budama"), "şubat budama");
        ok(TanecikLines.season(9).get(0).startsWith("Yağlanma"), "eylül yağlanma");
        int n = TanecikLines.lines(10, 11).size();
        ok(TanecikLines.line(10, 11, 0).equals(TanecikLines.line(10, 11, n)), "döngüsel");
        ok(TanecikLines.line(10, 11, -1).equals(TanecikLines.line(10, 11, n - 1)), "eksi sıra bozulmaz");
        ok(TanecikLines.line(10, 11, 0).startsWith("Günaydın"), "ilk söz selam");
        // --- Akıllı sözler (veriye göre)
        TanecikLines.Facts f = new TanecikLines.Facts();
        ok(texts(TanecikLines.smart(f, 10, 11)).equals(TanecikLines.lines(10, 11)), "veri yoksa genel sözler");
        f.raining = true;
        List<TanecikLines.Line> l = TanecikLines.smart(f, 10, 11);
        ok(l.get(0).action.equals(TanecikLines.GO_WEATHER) && l.get(0).text.startsWith("Yağmur yağıyor"), "yağmur önce söylenir");
        f = new TanecikLines.Facts(); f.rainChance = 70;
        ok(TanecikLines.smart(f, 10, 11).get(0).text.startsWith("24 saat içinde yağmur ihtimali %70."), "yağış ihtimali");
        f.rainChance = 40;
        ok(TanecikLines.smart(f, 10, 11).get(0).text.startsWith("Günaydın"), "düşük ihtimalde uyarı yok");
        f = new TanecikLines.Facts(); f.windKmh = 31;
        ok(TanecikLines.smart(f, 10, 11).get(0).text.startsWith("Rüzgâr kuvvetli (31 km/s)"), "rüzgâr");
        f = new TanecikLines.Facts(); f.tempC = 9;
        ok(TanecikLines.cold(f) && has(TanecikLines.smart(f, 20, 11), "Hava soğuk, 9 derece. Atkımı taktım"), "soğuk: atkı");
        f.tempC = 11;
        ok(!TanecikLines.cold(f) && !has(TanecikLines.smart(f, 20, 11), "Atkımı"), "ılıkta atkı yok");
        ok(!TanecikLines.cold(new TanecikLines.Facts()), "sıcaklık bilinmiyorsa atkı yok");
        f = new TanecikLines.Facts(); f.hasGarden = true; f.daysSinceHarvest = 4;
        l = TanecikLines.smart(f, 10, 11);
        TanecikLines.Line h = find(l, "4 gündür hasat kaydı yok");
        ok(h != null && h.action.equals(TanecikLines.GO_ELEK) && h.actionLabel.equals("Elek'e git ›"), "hasat hatırlatması");
        ok(find(TanecikLines.smart(f, 10, 6), "gündür hasat") == null, "hasat dışı ayda hatırlatma yok");
        ok(find(TanecikLines.smart(f, 23, 11), "gündür hasat") == null, "gece hatırlatma yok");
        f.daysSinceHarvest = 0;
        ok(has(TanecikLines.smart(f, 18, 11), "Bugünkü hasadı yazmışsın, eline sağlık!"), "bugün yazmış");
        f = new TanecikLines.Facts(); f.receivableCount = 1; f.receivableTotal = 10340;
        h = find(TanecikLines.smart(f, 10, 11), "alacağın var");
        ok(h != null && h.text.equals("Bir alıcıda 10.340 TL alacağın var. Unutma, takip et.") && h.action.equals(TanecikLines.GO_ASK)
                && h.question.equals("kimden ne kadar alacağım var?"), h == null ? "yok" : h.text);
        f.receivableCount = 3; f.receivableTotal = 12500.4;
        ok(has(TanecikLines.smart(f, 10, 11), "3 alıcıda toplam 12.500 TL alacağın var."), "çok alıcı");
        f = new TanecikLines.Facts(); f.bestBuyer = "Özer Ağırbaş"; f.bestBuyerPerKg = 5.06;
        h = find(TanecikLines.smart(f, 10, 11), "Aynı zeytine en çok");
        ok(h != null && h.text.equals("Aynı zeytine en çok Özer Ağırbaş veriyor; kilo başı 5,1 TL fazla.") && h.action.equals(TanecikLines.GO_REPORT), h == null ? "yok" : h.text);
        f.bestBuyerPerKg = 0.2;
        ok(find(TanecikLines.smart(f, 10, 11), "Aynı zeytine en çok") == null, "küçük farkta söylenmez");
        ok(find(TanecikLines.smart(null, 10, 11), "Elek raporu\"") .action.equals(TanecikLines.GO_REPORT), "rapor öğüdünde düğme");
        ok(TanecikLines.rainCode(61) && TanecikLines.rainCode(81) && TanecikLines.rainCode(95) && !TanecikLines.rainCode(3) && !TanecikLines.rainCode(45), "hava kodu");
        ok(TanecikLines.intro().startsWith("Merhaba, ben Tanecik!"), "tanışma");

        // --- Akıllanma: yeni bilgiler, önemli sözler, günlük hafıza, asistana soru
        f = new TanecikLines.Facts();
        f.todayKg = 157; f.todayTl = 6015; f.hasGarden = true; f.daysSinceHarvest = 0;
        l = TanecikLines.smart(f, 18, 11);
        h = find(l, "Bugün 157 kg sattın");
        ok(h != null && h.text.equals("Bugün 157 kg sattın, 6.015 TL. Eline sağlık!") && h.question.equals("bugün ne kadar sattım?"), "bugünkü satış");
        ok(find(l, "Bugünkü hasadı") == null, "satış varsa ayrıca hasat övgüsü yok");
        f = new TanecikLines.Facts(); f.seasonKg = 1418; f.seasonTl = 65520;
        ok(has(TanecikLines.smart(f, 10, 11), "Bu sezon 1.418 kg zeytin sattın, toplam 65.520 TL."), "sezon toplamı");
        f = new TanecikLines.Facts(); f.oldestReceivableDays = 18; f.oldestReceivableBuyer = "Ali Özdemir";
        l = TanecikLines.smart(f, 10, 11);
        ok(l.get(0).id.equals("recv_old") && l.get(0).important
                && l.get(0).text.equals("Ali Özdemir'in parası 18 gündür bekliyor. Bir arasan iyi olur."), l.get(0).text);
        f.oldestReceivableDays = 9;
        ok(find(TanecikLines.smart(f, 10, 11), "gündür bekliyor") == null, "kısa beklemede uyarı yok");
        f = new TanecikLines.Facts(); f.tomorrowRain = 75;
        h = TanecikLines.smart(f, 10, 11).get(0);
        ok(h.id.equals("rain_tomorrow") && h.important && h.question.equals("yarın yağmur yağacak mı?"), "yarın yağmur");
        ok(TanecikLines.smart(f, 10, 7).get(0).id.equals("greet"), "hasat dışında yarın uyarısı yok");
        f.rainChance = 80;
        ok(TanecikLines.smart(f, 10, 11).get(0).id.equals("rain_24h"), "24 saat uyarısı yarınınkinin yerine geçer");

        // Günlük hafıza
        f = new TanecikLines.Facts(); f.windKmh = 30; f.receivableCount = 1; f.receivableTotal = 500;
        l = TanecikLines.smart(f, 10, 11);
        java.util.Set<String> shown = new java.util.HashSet<>();
        ok(TanecikLines.hasNews(l, shown), "önemli söz varken '!'");
        TanecikLines.Line first = TanecikLines.choose(l, shown, 5);
        ok(first.id.equals("wind"), "önemli söz sıradan önce: " + first.id);
        shown.add(first.id);
        ok(!TanecikLines.hasNews(l, shown), "söylenince '!' kalkar");
        TanecikLines.Line next = TanecikLines.choose(l, shown, 0);
        ok(!next.id.equals("wind"), "aynı gün tekrar etmez");
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < l.size(); i++) { TanecikLines.Line x = TanecikLines.choose(l, shown, i); shown.add(x.id); seen.add(x.id); }
        ok(shown.size() == l.size(), "gün içinde hepsi birer kez söylenir: " + shown.size() + "/" + l.size());
        ok(TanecikLines.choose(l, shown, 2) == l.get(2), "hepsi söylendiyse sıradaki");
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (TanecikLines.Line x : TanecikLines.smart(full(), 10, 11)) ok(ids.add(x.id), "kimlik tekil: " + x.id);

        // Önerilen her soru asistanın kurallarıyla anlaşılır (bulut modele gerek yok).
        int asked = 0;
        for (int m = 1; m <= 12; m++) for (TanecikLines.Line x : TanecikLines.smart(full(), 10, m)) {
            if (!TanecikLines.GO_ASK.equals(x.action)) continue;
            asked++;
            boolean understood = AssistantReceivables.asks(x.question)
                    || ZeytinAssistantCommand.parse(x.question).kind != ZeytinAssistantCommand.Kind.UNKNOWN;
            ok(understood && !x.actionLabel.isEmpty(), "asistan anlamıyor: " + x.question);
        }
        ok(asked > 0, "en az bir soru önerilir");

        // --- Gece uyandırma
        TanecikLines.Line z = TanecikLines.sleepyLine();
        ok(z.action.equals(TanecikLines.GO_WAKE) && z.actionLabel.equals("Uyandır ›") && z.text.startsWith("Zzz"), "uyurken uyandır düğmesi");
        ok(TanecikLines.wokeUp().text.startsWith("Haa? Uyandım!") && TanecikLines.wokeUp().action.isEmpty(), "uyanınca");
        ok(TanecikLines.goodnight().text.equals("Uykum geldi… İyi geceler!"), "yatarken");
        ok(TanecikLines.AWAKE_MS == 300000L, "5 dakika uyanık");
        ok(!z.important && !TanecikLines.wokeUp().important, "uyku sözleri '!' getirmez");

        // --- Tanıtım turu (v2: menüye basar, Elek gösterimi, altyazı)
        List<TanecikTour.Step> steps = TanecikTour.steps();
        ok(steps.size() >= 8, "tur adımları");
        int reports = 0, guides = 0, taps = 0;
        java.util.Set<String> sections = new java.util.HashSet<>(), pointed = new java.util.HashSet<>();
        for (int i = 0; i < steps.size(); i++) {
            TanecikTour.Step st = steps.get(i);
            sections.add(st.section);
            ok(st.section.equals("HOME") || st.section.equals("ELEK") || st.section.equals("GARDENS")
                    || st.section.equals("REPORTS") || st.section.equals("MEYDAN"), "bölüm: " + st.section);
            ok(st.tap.isEmpty() || st.tap.equals(TanecikTour.NAV + st.section), "basılan düğme bölümle aynı: " + st.tap);
            if (!st.tap.isEmpty()) taps++;
            ok(st.tag.isEmpty() || st.tag.equals(TanecikTour.TAG_TAKVIM) || st.tag.equals(TanecikTour.TAG_HAVA)
                    || st.tag.equals(TanecikTour.TAG_MIKROFON) || st.tag.startsWith(TanecikTour.NAV), "hedef: " + st.tag);
            if (st.tag.startsWith(TanecikTour.NAV)) { pointed.add(st.tag); ok(st.tap.isEmpty(), "gösterilen düğmeye basılmaz"); }
            ok(!st.text.isEmpty() && st.text.length() <= 90, "kısa ve hızlı söz (" + st.text.length() + "): " + st.text);
            if (TanecikTour.DO_REPORT.equals(st.after)) reports++;
            if (TanecikTour.DO_ELEK.equals(st.after)) { guides++; ok(st.section.equals("ELEK"), "gösterim Elek'ten açılır"); }
            if (!st.after.isEmpty()) ok(i < steps.size() - 1, "ekran açan adım son olamaz (dönüş adımı lazım)");
        }
        ok(reports == 1 && guides == 1 && taps >= 3, "rapor, Elek gösterimi ve menüye basma var");
        ok(sections.contains("GARDENS") && sections.contains("ELEK"), "başka menülere geçer");
        ok(sections.contains("REPORTS") && sections.contains("MEYDAN"), "Zeytin Ligi ve Sohbet ekranı açılır");
        ok(pointed.contains(TanecikTour.NAV + "PROFILE") && !sections.contains("PROFILE"),
                "Tezgâh modu Ayarlar düğmesi gösterilerek anlatılır (Ayarlar'da hesap bilgisi var, açılmaz)");
        boolean tz = false; for (TanecikTour.Step st : steps) tz |= st.text.contains("Tezgâh");
        ok(tz, "Tezgâh modundan bahseder");
        ok(steps.get(0).text.startsWith("Selam! Ben Tanecik.") && steps.get(steps.size() - 1).text.startsWith("Hepsi bu!"), "giriş ve kapanış");
        for (TanecikTour.GuideStep g : TanecikTour.elekGuide()) ok(!g.view.isEmpty() && g.text.length() <= 70, "gösterim adımı: " + g.text);
        ok(TanecikTour.elekGuide().get(1).view.equals("etKg2") && TanecikTour.elekGuide().get(1).type.equals("150"), "kilo yazılır");
        ok(TanecikTour.elekGuide().get(TanecikTour.elekGuide().size() - 1).view.equals("btnSaveElek"), "sonda Kaydet gösterilir (basılmaz)");
        long est = TanecikTour.estimateMs();
        System.out.println("Tahmini tur süresi: " + est / 1000 + " sn");
        ok(est >= 60_000 && est <= 90_000, "tur 1–1,5 dakika sürer: " + est / 1000 + " sn");
        ok(TanecikTour.TOUR_RATE > 1.12f , "turda ses normalden hızlı");
        ok(TanecikTour.WALK_DP_S >= 3 * 38, "turda yürüme en az 3 kat hızlı");

        // --- Altyazı öbekleri
        String sp = TanecikCaptions.spoken("Rüzgâr kuvvetli (31 km/s), yağmur %70, kilo başı 5,1 TL.");
        ok(sp.equals("Rüzgâr kuvvetli (31 kilometre), yağmur yüzde 70, kilo başı 5,1 lira."), sp);
        List<TanecikCaptions.Chunk> ch = TanecikCaptions.chunks("Selam! Ben Tanecik. Zeytin Defteri'ni hızlıca gezelim!");
        StringBuilder joined = new StringBuilder();
        for (TanecikCaptions.Chunk c : ch) { joined.append(c.text).append('|'); ok(c.text.split(" ").length <= 3, "en çok 3 kelime: " + c.text); }
        ok(joined.toString().equals("Selam!|Ben Tanecik.|Zeytin Defteri'ni hızlıca|gezelim!|"), joined.toString());
        String src = "Selam! Ben Tanecik. Zeytin Defteri'ni hızlıca gezelim!";
        for (TanecikCaptions.Chunk c : ch) ok(src.startsWith(c.text, c.start), "başlangıç yeri doğru: " + c.text);
        ok(TanecikCaptions.indexAt(ch, src.indexOf("Tanecik")) == 1 && TanecikCaptions.indexAt(ch, 0) == 0
                && TanecikCaptions.indexAt(ch, src.length() - 2) == 3, "söylenen kelimenin öbeği");
        ok(TanecikCaptions.chunkMs("gezelim!", 1f) > TanecikCaptions.chunkMs("gezelim", 1f), "cümle sonunda kısa durak");
        ok(TanecikCaptions.chunkMs("Ben Tanecik", 1.12f) < TanecikCaptions.chunkMs("Ben Tanecik", 1f), "hızlı seste kısa");
        ok(TanecikCaptions.chunks("").isEmpty() && TanecikCaptions.chunks("  a  ").get(0).text.equals("a"), "boşluklar");
        ok(TanecikLines.introLine().action.equals(TanecikLines.GO_TOUR) && TanecikLines.introLine().actionLabel.equals("Gezdir ›"), "tanışmada Gezdir");
        ok(has(TanecikLines.smart(null, 10, 11), "gezdireyim mi"), "turu öneren söz");

        System.out.println("PASS: " + checks + " Tanecik sözü kontrolü");
    }
}
