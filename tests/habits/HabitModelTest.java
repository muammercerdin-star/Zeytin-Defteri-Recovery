package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sezilen alışkanlıklar (İP5): eşik (4/6 yüksek, 3/6 orta, altı yok), 180 gün unutma, silinen kaydın etkisi,
 * "Bu yanlış" (bir sonraki kayda kadar), 5 kez üst üste reddedilen türün kapanması, öğretilen/söylenenin üstünlüğü,
 * gerekçe metinleri (kısa, sakin; kişi adı / tutar içeren not gösterilmez), "+" menüsü satış satırı.
 */
public class HabitModelTest {
    static int n = 0;
    static int bad = 0;
    static final long DAY = HabitRules.DAY_MS;
    static final long NOW = 400L * DAY;

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

    /** i. satış (i büyüdükçe daha yeni): alıcı, sınıf 3, fiyat. */
    static HabitModel.Sale sale(int i, String buyer, double price) {
        Map<String, Double> prices = new LinkedHashMap<>();
        Map<String, Double> kilos = new LinkedHashMap<>();
        prices.put("3", price);
        kilos.put("3", 100.0);
        prices.put("1", 90.0);
        kilos.put("1", 20.0);
        return new HabitModel.Sale(NOW - (30 - i) * DAY, buyer, "Girelli", prices, kilos);
    }

    static List<HabitModel.Sale> sales(String... buyers) {
        List<HabitModel.Sale> out = new ArrayList<>();
        for (int i = 0; i < buyers.length; i++) {
            out.add(sale(i, buyers[i], 68));
        }
        return out;
    }

    static HabitModel model(List<HabitModel.Sale> s) {
        return new HabitModel(s, new ArrayList<>(), NOW, new HabitCorrections.Memory());
    }

    public static void main(String[] a) {
        empty();
        thresholds();
        forgetting();
        deletion();
        wrong();
        closing();
        taughtWins();
        operations();
        privacy();
        texts();
        saleLine();
        karne();
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " HabitModel");
        if (bad > 0) {
            System.exit(1);
        }
    }

    /** Alışkanlık boşken (kayıt yok ya da hep farklı): hiçbir alan dolmaz, çip yok, "+" menüsü bugünkü yazı. */
    static void empty() {
        HabitModel none = new HabitModel(new ArrayList<>(), new ArrayList<>(), NOW, null);
        check(none.buyer() == null && none.saleGarden() == null && none.operationGarden() == null, "boş: satış/bahçe yok");
        check(none.price("Ali Usta", "3") == null && none.grade("Ali Usta") == null && none.saleLine() == null, "boş: fiyat yok");
        for (String t : OperationKinds.TYPES) {
            check(none.product(t) == null && none.amount(t) == null, "boş: " + t);
        }
        check(none.amount("Ot biçme") == null && none.amount("İşçilik") == null, "boş: işçi / ot biçme");
        HabitModel varied = model(sales("A", "B", "C", "D", "E", "F"));
        check(varied.buyer() == null && varied.saleLine() == null, "hep farklı alıcı: öneri yok");
    }

    static void thresholds() {
        // En yeni sonda: son 6 = son altı eleman.
        HabitModel.Habit high = model(sales("Veli", "Ali Usta", "Ali Usta", "Hasan", "Ali Usta", "Ali Usta")).buyer();
        check(high != null && high.level == HabitModel.Level.HIGH && high.value.equals("Ali Usta"), "4/6 yüksek");
        eq(high.kind, "sezilen", "tür sezilen");
        eq(high.evidence(), "son 6 satışın 4'ü Ali Usta", "kanıt");
        HabitModel.Habit mid = model(sales("Veli", "Ali Usta", "Hasan", "Ali Usta", "Veli", "Ali Usta")).buyer();
        check(mid != null && mid.level == HabitModel.Level.MID, "3/6 orta");
        check(model(sales("Veli", "Ali Usta", "Hasan", "Ali Usta", "Veli", "Kemal")).buyer() == null, "2/6 kullanılmaz");
        check(model(sales("Ali Usta", "Ali Usta", "Ali Usta")).buyer().level == HabitModel.Level.MID, "3 kayıt hepsi aynı: orta");
        check(model(sales("Ali Usta", "Ali Usta", "Ali Usta", "Ali Usta")).buyer().level == HabitModel.Level.HIGH, "4 kayıt hepsi aynı: yüksek");
        check(model(sales("Ali Usta", "Ali Usta", "Ali Usta", "Ali Usta", "", "", "", "", "", "")).buyer() == null,
                "yalnız son 6 sayılır (eskiler etkilemez); alıcısız satış önerilmez");
        check(model(new ArrayList<>()).buyer() == null, "kayıt yok: bilgi yok");
        HabitModel.Habit tie = model(sales("Veli", "Veli", "Veli", "Ali Usta", "Ali Usta", "Ali Usta")).buyer();
        eq(tie.value, "Ali Usta", "eşitlikte en yeni");
    }

    static void forgetting() {
        List<HabitModel.Sale> old = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            old.add(new HabitModel.Sale(NOW - (181 + i) * DAY, "Ali Usta", "Girelli", null, null));
        }
        check(model(old).buyer() == null, "180 günden eski bilgi kullanılmaz");
        List<HabitModel.Sale> fresh = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            fresh.add(new HabitModel.Sale(NOW - (179 - i) * DAY, "Ali Usta", "Girelli", null, null));
        }
        check(model(fresh).buyer() != null, "179 gün: kullanılır");
    }

    static void deletion() {
        List<HabitModel.Sale> s = sales("Veli", "Ali Usta", "Ali Usta", "Ali Usta", "Ali Usta", "Ali Usta");
        eq(model(s).buyer().level, HabitModel.Level.HIGH, "5/6 yüksek");
        s.remove(5);
        s.remove(4);
        eq(model(s).buyer().level, HabitModel.Level.MID, "iki satış silinince orta (yeniden hesap)");
        s.remove(3);
        check(model(s).buyer() == null, "bir satış daha silinince bilgi düşer");
        List<HabitModel.Op> ops = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ops.add(new HabitModel.Op(NOW - (10 - i) * DAY, "İlaçlama", "Bakır", 500, "ml", "Girelli"));
        }
        HabitModel m = new HabitModel(null, ops, NOW, null);
        eq(m.product("İlaçlama").value, "Bakır", "en sık ilaç");
        ops.remove(0);
        check(new HabitModel(null, ops, NOW, null).product("İlaçlama").level == HabitModel.Level.MID, "ilaçlama kaydı silinince orta");
    }

    static void wrong() {
        HabitCorrections.Memory c = new HabitCorrections.Memory();
        List<HabitModel.Sale> s = sales("Ali Usta", "Ali Usta", "Ali Usta", "Ali Usta");
        check(new HabitModel(s, null, NOW, c).buyer() != null, "önce önerilir");
        c.reject("alici", "Ali Usta", NOW);
        check(new HabitModel(s, null, NOW, c).buyer() == null, "Bu yanlış: bir sonraki kayda kadar önerilmez");
        check(new HabitModel(s, null, NOW + DAY, c).buyer() == null, "ertesi gün de (kayıt yoksa) önerilmez");
        s.add(new HabitModel.Sale(NOW + 2 * DAY, "Ali Usta", "Girelli", null, null));
        check(new HabitModel(s, null, NOW + 2 * DAY, c).buyer() != null, "yeni kayıttan sonra yeniden önerilir");
        HabitCorrections.Memory c2 = new HabitCorrections.Memory();
        c2.reject("fiyat:Ali Usta:3", "68", NOW);
        HabitModel m = new HabitModel(sales("Ali Usta", "Ali Usta", "Ali Usta", "Ali Usta"), null, NOW, c2);
        check(m.price("Ali Usta", "3") == null && m.buyer() != null, "yalnız reddedilen değer kalkar, ötekiler durur");
    }

    static void closing() {
        HabitCorrections.Memory c = new HabitCorrections.Memory();
        for (int i = 0; i < 4; i++) {
            c.reject("alici", "X" + i, NOW);
        }
        check(!c.closed("alici"), "4 red: açık");
        c.accept("alici");
        eq(c.streak("alici"), 0, "kabul üst üste sayıyı sıfırlar");
        for (int i = 0; i < 5; i++) {
            c.reject("alici", "Y" + i, NOW);
        }
        check(c.closed("alici"), "5 kez üst üste: tür kapanır");
        List<HabitModel.Sale> s = sales("Ali Usta", "Ali Usta", "Ali Usta", "Ali Usta");
        s.add(new HabitModel.Sale(NOW + DAY, "Ali Usta", "Girelli", null, null));
        check(new HabitModel(s, null, NOW + DAY, c).buyer() == null, "kapanan tür yeni kayıtla da açılmaz");
        check(new HabitModel(s, null, NOW + DAY, c).saleGarden() != null, "başka tür etkilenmez");
    }

    static void taughtWins() {
        HabitModel.Habit h = model(sales("Ali Usta", "Ali Usta", "Ali Usta", "Ali Usta")).buyer();
        check(HabitModel.forEmpty("Veli", h) == null, "çiftçinin/cümlenin değeri varsa sezilen ne doldurur ne önerir");
        check(HabitModel.forEmpty("", h) == h && HabitModel.forEmpty(null, h) == h, "boş alana önerir");
        List<HabitModel.Op> ops = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ops.add(new HabitModel.Op(NOW - (10 - i) * DAY, "Diğer", "Ot biçme, 2 saat", 2, "saat", "Girelli"));
        }
        HabitModel m = new HabitModel(null, ops, NOW, null);
        // Öğretilen söz formu "Diğer + Ot biçme" notuyla açar: not alanı dolu, sezilen ürün notu bastırılamaz.
        check(HabitModel.forEmpty("Ot biçme", m.product("Diğer")) == null, "öğretilen not üstün");
        eq(m.amount("Ot biçme").value, "2 saat", "Diğer işinde (ot biçme) miktar işin adına göre");
    }

    static void operations() {
        List<HabitModel.Op> ops = new ArrayList<>();
        long t = NOW - 20 * DAY;
        String[] gardens = {"Girelli", "Kuyu dibi", "Girelli", "Girelli", "Girelli", "Alt"};
        for (int i = 0; i < 6; i++) {
            ops.add(new HabitModel.Op(t + i * DAY, "Gübre", "MKP 0-52-34", 18, "kg", gardens[i]));
        }
        HabitModel m = new HabitModel(null, ops, NOW, null);
        eq(m.product("Gübre").value, "MKP 0-52-34", "en sık gübre");
        eq(m.product("Gübre").reason(), "Geçen sefer de MKP 0-52-34 · son 6 gübre kaydının 6'sı", "gerekçe");
        eq(m.product("Gübre").evidence(), "son 6 gübre kaydının 6'sı MKP 0-52-34", "kanıt");
        HabitModel.Habit amount = m.amount("Gübre");
        check(amount.number == 18 && amount.unit.equals("kg"), "miktar 18 kg");
        eq(m.operationGarden().value, "Girelli", "en sık bahçe");
        check(m.product("Sulama") == null, "ürün yalnız ilaçlama ve gübrede");
        List<HabitModel.Op> work = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            work.add(new HabitModel.Op(t + i * DAY, "İşçilik", "", i < 4 ? 8 : 2, i < 5 ? "kişi" : "gün", "Girelli"));
        }
        HabitModel w = new HabitModel(null, work, NOW, null);
        HabitModel.Habit workers = w.amount("İşçilik");
        check(workers != null && workers.number == 8 && workers.unit.equals("kişi") && workers.what.equals("isci"),
                "işçi sayısı: yalnız 'kişi' birimiyle girilenler");
        eq(workers.window, 5, "gün birimli işçilik sayılmaz");
        List<HabitModel.Op> gub = new ArrayList<>();
        gub.add(new HabitModel.Op(t, "Gübreleme", "Üre", 10, "kg", "Girelli"));
        gub.add(new HabitModel.Op(t + DAY, "Gübre", "Üre", 10, "kg", "Girelli"));
        gub.add(new HabitModel.Op(t + 2 * DAY, "Gübre", "Üre", 10, "kg", "Girelli"));
        check(new HabitModel(null, gub, NOW, null).product("Gübre") != null, "eski 'Gübreleme' yazımı gübre sayılır");
        List<HabitModel.Op> exact = new ArrayList<>();
        String[] notes = {"Bakır", "bakır ", "Bakır sülfat", "Bakır", "Bakır,", "Bakır"};
        for (int i = 0; i < 6; i++) {
            exact.add(new HabitModel.Op(t + i * DAY, "İlaçlama", notes[i], 0, "", "Girelli"));
        }
        HabitModel.Habit p = new HabitModel(null, exact, NOW, null).product("İlaçlama");
        check(p.count == 3 && p.level == HabitModel.Level.MID, "yalnız tıpatıp aynı not sayılır (3/6)");
    }

    static void privacy() {
        List<HabitModel.Op> ops = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ops.add(new HabitModel.Op(NOW - (10 - i) * DAY, "İlaçlama", "Hasan abiden aldım", 0, "", "Girelli"));
        }
        HabitModel.Habit h = new HabitModel(null, ops, NOW, null).product("İlaçlama");
        check(h.hidden && !h.reason().contains("Hasan") && h.reason().contains("aynı not"), "kişi adlı not gerekçede yok: " + h.reason());
        eq(h.value, "Hasan abiden aldım", "ama form doldurulabilir");
        List<HabitModel.Op> money = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            money.add(new HabitModel.Op(NOW - (10 - i) * DAY, "Gübre", "Üre 450 TL", 0, "", "Girelli"));
        }
        check(new HabitModel(null, money, NOW, null).product("Gübre").hidden, "tutarlı not gerekçede yok");
        List<HabitModel.Sale> s = sales("Mehmet", "Mehmet", "Mehmet", "Mehmet");
        List<HabitModel.Op> named = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            named.add(new HabitModel.Op(NOW - (10 - i) * DAY, "Gübre", "Mehmet getirdi", 0, "", "Girelli"));
        }
        check(new HabitModel(s, named, NOW, null).product("Gübre").hidden, "alıcı adı geçen not gerekçede yok");
        List<HabitModel.Op> plain = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            plain.add(new HabitModel.Op(NOW - (10 - i) * DAY, "İlaçlama", "Bakır", 0, "", "Girelli"));
        }
        check(!new HabitModel(null, plain, NOW, null).product("İlaçlama").hidden, "sıradan not gösterilir");
    }

    static void texts() {
        HabitModel m = model(sales("Veli", "Ali Usta", "Ali Usta", "Hasan", "Ali Usta", "Ali Usta"));
        eq(m.buyer().reason(), "Geçen sefer de Ali Usta · son 6 satışın 4'ü", "kısa gerekçe");
        HabitModel.Habit price = m.price("Ali Usta", "3");
        eq(price.evidence(), "Ali Usta'ya son 4 satışın 4'ü 68 TL (3 No)", "fiyat kanıtı");
        check(price.number == 68, "fiyat sayısı");
        String[] creepy = {"izled", "hep ", "her zaman", "seni ", "gözl", "takip"};
        List<String> all = new ArrayList<>();
        all.add(m.buyer().reason());
        all.add(price.reason());
        all.add(m.saleGarden().reason());
        for (String r : all) {
            for (String w : creepy) {
                check(!r.toLowerCase(new java.util.Locale("tr", "TR")).contains(w), "ürkütücü dil yok: " + r);
            }
            check(r.length() <= 70, "kısa: " + r);
        }
        eq(HabitModel.dative("Ali Usta"), "Ali Usta'ya", "yönelme a");
        eq(HabitModel.dative("Mehmet"), "Mehmet'e", "yönelme e");
        eq(HabitModel.dative("Ayşe"), "Ayşe'ye", "yönelme ye");
        eq(HabitModel.dative("Kooperatif"), "Kooperatif'e", "yönelme");
        eq(HabitModel.gradeLabel("3"), "3 No", "sınıf adı");
    }

    /** Karne: türüne göre gösterilme, kabul, "Bu yanlış"; yalnız sayılar, bilinmeyen anahtar yok sayılır. */
    static void karne() {
        String k = "";
        k = AssistantScorecard.count(k, AssistantScorecard.suggestionKey(AssistantScorecard.SHOWN, "alici"), 1);
        k = AssistantScorecard.count(k, AssistantScorecard.suggestionKey(AssistantScorecard.SHOWN, "alici"), 1);
        k = AssistantScorecard.count(k, AssistantScorecard.suggestionKey(AssistantScorecard.ACCEPTED, "alici"), 1);
        k = AssistantScorecard.count(k, AssistantScorecard.suggestionKey(AssistantScorecard.WRONG, "urun"), 1);
        k = AssistantScorecard.count(k, AssistantScorecard.suggestionKey(AssistantScorecard.SHOWN, "urun"), 1);
        k = AssistantScorecard.count(k, "oneri_gosterilen.Ali Usta", 1);
        k = AssistantScorecard.count(k, "uydurma.alici", 1);
        eq(AssistantScorecard.get(k, "oneri_gosterilen.alici"), 2L, "gösterilen sayılır");
        eq(AssistantScorecard.get(k, "oneri_kabul.alici"), 1L, "kabul sayılır");
        eq(AssistantScorecard.get(k, "oneri_yanlis.urun"), 1L, "Bu yanlış sayılır");
        check(!k.contains("Ali Usta") && !k.contains("uydurma"), "karneye ad / bilinmeyen anahtar girmez: " + k);
        String shown = AssistantScorecard.show(k)[1];
        check(shown.contains("• Öneri · alıcı: 2 gösterildi, 1 kabul (%50), 0 «Bu yanlış»"), "karnede: " + shown);
        check(shown.contains("• Öneri · ilaç / gübre: 1 gösterildi, 0 kabul (%0), 1 «Bu yanlış»"), "karnede ilaç");
        check(!AssistantScorecard.show("")[1].contains("Öneri"), "öneri yoksa satır yok");
        eq(AssistantScorecard.suggestionKey("oneri_kabul", "yok"), "", "bilinmeyen tür");
    }

    static void saleLine() {
        eq(model(sales("Ali Usta", "Ali Usta", "Ali Usta", "Ali Usta")).saleLine(), "Ali Usta'ya satış · 3 No · 68 TL hazır",
                "yüksek güven: alıcı, sınıf, fiyat");
        check(model(sales("Veli", "Ali Usta", "Hasan", "Ali Usta", "Veli", "Ali Usta")).saleLine() == null, "orta güven: bugünkü yazı");
        check(model(new ArrayList<>()).saleLine() == null, "veri yok: bugünkü yazı");
        List<HabitModel.Sale> s = new ArrayList<>();
        double[] prices = {60, 62, 64, 66};
        for (int i = 0; i < 4; i++) {
            s.add(sale(i, "Ali Usta", prices[i]));
        }
        eq(model(s).saleLine(), "Ali Usta'ya satış · 3 No hazır", "fiyat değişkense fiyatsız");
    }
}
