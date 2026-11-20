package com.zeytindefteri.app;

import java.util.Arrays;
import java.util.List;

/** Tahsilat (eğitim turu 1): para / borç sözüne bağlı; masraf cümleleri tahsilat sanılmaz; tutar uydurulmaz. */
public class CollectionEntryTest {
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
        return new LearnedBrain(new LearnedTerms.Memory(), new RuleBrain()).decide(new Brain.Input(raw),
                BrainContext.full(true, AssistantPersona.decode("")::handle));
    }

    public static void main(String[] a) {
        String[][] yes = {
                {"Ali Usta'dan 2000 lira aldım", "alici=Ali Usta;tutar=2000"},
                {"Ali Usta'dan iki bin lira aldım", "alici=Ali Usta;tutar=2000"},
                {"Mehmet'ten 1500 TL aldık", "alici=Mehmet;tutar=1500"},
                {"Hasan borcunun yarısını ödedi", "alici=Hasan;oran=yarim"},
                {"Hasan Abi 500 lira verdi", "alici=Hasan Abi;tutar=500"},
                {"Ayşe Hanım 3000 lira gönderdi", "alici=Ayşe Hanım;tutar=3000"},
        };
        for (String[] y : yes) {
            Decision d = decide(y[0]);
            check(d.is(ActionCatalog.TAHSILAT), "tahsilat: " + y[0] + " → " + d);
            eq(d.fieldsText(), y[1], "alanlar: " + y[0]);
        }
        String[] no = {"yağ aldık", "sürgün aldık", "ilaç aldık", "bakır aldık", "fidan aldık", "bin beş yüz liralık ilaç aldım",
                "Bayi'den 500 liralık gübre aldım", "Bayi'den 500 lira ilaç aldık", "Mazot aldım 500 lira",
                "Hasan'dan ne kadar alacağım var", "Ali Usta'dan 2000 lira aldım mı", "Ali Usta'dan 2000 lira aldım?",
                "2000 lira aldım", "Ali Usta parasını cuma verecek", "yağhaneden 120 kilo yağ aldık", "Ali Usta'ya 2000 lira verdim",
                "Hasan borcunu ödedi", "Kim bana borçlu?"};
        for (String s : no) {
            check(CollectionEntry.parse(s) == null, "tahsilat değil: " + s);
            check(!decide(s).is(ActionCatalog.TAHSILAT), "beyin tahsilat demez: " + s);
        }
        CollectionEntry half = CollectionEntry.parse("Hasan borcunun yarısını ödedi");
        eq(half.amountFor(3000), 1500.0, "yarısı açık borçtan");
        eq(half.amountFor(1001), 500.5, "yarısı kuruşa yuvarlı");
        eq(half.amountFor(0), 0.0, "açık borç yoksa tutar uydurulmaz");
        CollectionEntry fixed = CollectionEntry.parse("Ali Usta'dan 2000 lira aldım");
        eq(fixed.amountFor(5000), 2000.0, "söylenen tutar");
        eq(fixed.amountFor(0), 0.0, "borç yoksa açılmaz");
        check(CollectionEntry.warning(2000, 5000).isEmpty(), "borçtan küçük: uyarı yok");
        check(CollectionEntry.warning(2000, 1500).contains("toplam borç"), "borçtan büyük: uyarı");
        List<String> names = Arrays.asList("Hasan", "Hasan Abi", "Ali Usta", "Hasanbey", "Ayşe Hanım");
        eq(CollectionEntry.matches(names, "Hasan").toString(), "[0, 1]", "aynı adda iki alıcı: sorulur");
        eq(CollectionEntry.matches(names, "Hasan Abi").toString(), "[1]", "tam ad tek alıcı");
        eq(CollectionEntry.matches(names, "ali usta").toString(), "[2]", "büyük/küçük harf fark etmez");
        eq(CollectionEntry.matches(names, "Mehmet").toString(), "[]", "rehberde yok: hiçbir şey kaydedilmez");
        eq(CollectionEntry.matches(names, "Ali").toString(), "[2]", "tek kelime: ilk kelimesi aynı alıcı");
        ActionCatalog.Action t = ActionCatalog.get(ActionCatalog.TAHSILAT);
        check(t.kind.alwaysConfirm, "tahsilat (ödeme) her zaman onaylı");
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " CollectionEntry");
        if (bad > 0) {
            System.exit(1);
        }
    }
}
