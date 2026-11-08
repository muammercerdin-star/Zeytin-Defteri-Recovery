package com.zeytindefteri.app;

/** Toplam (sınıfsız) elek kaydı: sayı okuma, doğrulama, etiket ve fiyat/lig hesaplarından dışlanma. */
public final class ElekMixedEntryTest {
    static int n, bad;
    static void ok(boolean c, String m) { n++; if (!c) { bad++; System.out.println("FAIL " + m); } }
    static void near(double a, double b, String m) { ok(Math.abs(a - b) < 1e-9, m + " (" + a + " ≠ " + b + ")"); }

    public static void main(String[] a) {
        // Türkçe sayı yazımı
        near(ElekMixedEntry.parse("8.000"), 8000, "8.000 binlik ayıraç");
        near(ElekMixedEntry.parse("12.500"), 12500, "12.500");
        near(ElekMixedEntry.parse("1.250.000"), 1250000, "1.250.000");
        near(ElekMixedEntry.parse("8.000,50"), 8000.5, "8.000,50");
        near(ElekMixedEntry.parse("53,5"), 53.5, "53,5 ondalık");
        near(ElekMixedEntry.parse("53.5"), 53.5, "53.5 ondalık (üç rakam değil)");
        near(ElekMixedEntry.parse("150"), 150, "150");
        near(ElekMixedEntry.parse(" 8 000 TL"), 8000, "boşluk ve TL");
        near(ElekMixedEntry.parse("₺7500"), 7500, "₺ işareti");
        near(ElekMixedEntry.parse(""), 0, "boş");
        near(ElekMixedEntry.parse(null), 0, "null");
        near(ElekMixedEntry.parse("abc"), 0, "harf");
        near(ElekMixedEntry.parse("-5"), 0, "negatif");
        near(ElekMixedEntry.parse("1,2,3"), 0, "bozuk");

        // Kilo fiyatı
        near(ElekMixedEntry.pricePerKg(150, 8000), 8000.0 / 150, "150 kg 8000 TL");
        near(ElekMixedEntry.pricePerKg(0, 8000), 0, "kilo yok");
        near(ElekMixedEntry.pricePerKg(150, 0), 0, "tutar yok");
        double kg = 150, total = 8000;
        double[] p = ElekMixedEntry.prices(kg, total);
        ok(Math.abs(ElekMixedEntry.kilograms(kg)[0] * p[0] - total) < 0.005, "satır tutarı kuruşuna kadar toplamı verir");
        ok(ElekMixedEntry.grades().length == 1 && ElekMixedEntry.grades()[0].equals("KARISIK"), "tek satır KARISIK");

        // Doğrulama
        ok(ElekMixedEntry.error(150, 8000).isEmpty(), "geçerli kayıt");
        ok(ElekMixedEntry.error(0, 8000).contains("kilo"), "kilo zorunlu");
        ok(ElekMixedEntry.error(150, 0).contains("tutar"), "tutar zorunlu");
        ok(ElekMixedEntry.error(150, 8).contains("TL/kg"), "8 TL yazım hatası (0,05 TL/kg) yakalanır");
        ok(ElekMixedEntry.error(15, 8000000).contains("TL/kg"), "fazla sıfır yakalanır");
        ok(ElekMixedEntry.error(2000000, 80000000).contains("büyük"), "absürt kilo");
        ok(ElekMixedEntry.error(150, 150).isEmpty(), "1 TL/kg sınırda geçerli");

        // Etiketler
        ok(ElekMixedEntry.priceLine(150, 8000).equals("150 kg · 8.000,00 TL → kilo fiyatı 53,33 TL/kg"), "fiyat satırı: " + ElekMixedEntry.priceLine(150, 8000));
        ok(ElekMixedEntry.priceLine(0, 0).startsWith("Kilo fiyatı kendiliğinden"), "boş fiyat satırı");
        ok(ElekMixedEntry.summary(150, 8000).equals("Karışık · 150 kg · 53,33 TL/kg"), "özet: " + ElekMixedEntry.summary(150, 8000));
        ok(ElekMixedEntry.summary(1250.5, 0).equals("Karışık · 1.250,5 kg"), "tutarsız özet");
        ok(ElekMixedEntry.isMixed("KARISIK") && ElekMixedEntry.isMixed(" karisik ") && !ElekMixedEntry.isMixed("1") && !ElekMixedEntry.isMixed(null), "isMixed");

        // Fiyat hesapları karışık satırı tanımaz (bölge/kendi fiyatın sınıf tablosuna girmez)
        ok(CurrentPrices.index("KARISIK") == -1, "güncel fiyat: KARISIK sınıf değil");
        ok(CurrentPrices.index("DOUBLE") == 0 && CurrentPrices.index("YAGLIK") == 5, "güncel fiyat: bilinen sınıflar");

        // Lig: sınıf toplamı kiloya eşit olmalı; karışık kilo çıkarılınca geçerli kalır
        ZeytinLeagueScore.Producer graded = producer(300, 30000, new double[]{100, 100, 100, 0, 0, 0});
        ZeytinLeagueScore.calculate(graded, null, null);
        ok(graded.eligible, "yalnız sınıflı kilo ile lig geçerli");
        ZeytinLeagueScore.Producer withMixed = producer(450, 38000, new double[]{100, 100, 100, 0, 0, 0});
        ZeytinLeagueScore.calculate(withMixed, null, null);
        ok(!withMixed.eligible, "karışık kilo çıkarılmazsa lig önizlemesi geçersiz olurdu (düzeltmenin gerekçesi)");

        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " ElekMixedEntry");
        System.exit(bad == 0 ? 0 : 1);
    }

    static ZeytinLeagueScore.Producer producer(double kg, double amount, double[] grades) {
        ZeytinLeagueScore.Producer p = new ZeytinLeagueScore.Producer();
        p.id = "u"; p.name = "Sen"; p.kg = kg; p.amount = amount; p.records = 3;
        System.arraycopy(grades, 0, p.grades, 0, 6);
        return p;
    }
}
