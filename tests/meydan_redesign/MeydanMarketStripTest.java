package com.zeytindefteri.app;

/** Sohbet piyasa şeridi: bölge adı, eşik, fiyat metni. Gerçek MeydanMarketStrip ile çalışır. */
public class MeydanMarketStripTest {
    static int checks = 0;
    static void check(boolean ok, String m) { checks++; if (!ok) throw new AssertionError(m); }
    static void eq(String a, String e, String m) { check(e.equals(a), m + ": beklenen '" + e + "', gelen '" + a + "'"); }

    public static void main(String[] args) {
        eq(MeydanMarketStrip.shortRegion("Alaşehir / Manisa"), "Alaşehir", "ilçe");
        eq(MeydanMarketStrip.shortRegion("  Edremit/Balıkesir "), "Edremit", "boşluksuz ayraç");
        eq(MeydanMarketStrip.shortRegion("Manisa"), "Manisa", "yalnız il");
        eq(MeydanMarketStrip.shortRegion(null), "", "null");
        MeydanMarketStrip s = MeydanMarketStrip.of("", true, 9, true, 100);
        eq(s.title, "Bölge piyasası", "bölge yok başlık");
        check(!s.showsPrice && s.subtitle.contains("Elek kaydı"), "bölge yok alt satır");
        s = MeydanMarketStrip.of("Alaşehir / Manisa", false, 9, true, 100);
        eq(s.title, "Alaşehir piyasası", "bağlantı yok başlık");
        eq(s.subtitle, "Bölge verisi şu an alınamadı", "bağlantı yok");
        check(!s.showsPrice, "bağlantı yokken fiyat");
        // Eşik altında asla fiyat yok (sunucu yayımlamış dese bile)
        for (int pc = 0; pc < 5; pc++) {
            s = MeydanMarketStrip.of("Alaşehir / Manisa", true, pc, true, 81);
            check(!s.showsPrice && !s.subtitle.contains("81"), pc + " üreticide fiyat göründü");
            eq(s.subtitle, "Fiyatlar 5 üreticiyle açılır · şu an " + pc + "/5", pc + " üretici alt satır");
        }
        s = MeydanMarketStrip.of("Alaşehir / Manisa", true, 9, false, 81);
        check(!s.showsPrice, "yayımlanmamışta fiyat");
        eq(s.subtitle, "Fiyatlar 5 üreticiyle açılır · şu an 5/5", "yayımlanmamış ama 5+");
        s = MeydanMarketStrip.of("Alaşehir / Manisa", true, 7, true, 0);
        check(!s.showsPrice, "fiyat 0 iken fiyat");
        s = MeydanMarketStrip.of("Edremit / Balıkesir", true, 7, true, 120.5);
        check(s.showsPrice, "yayımlanmış bölgede fiyat yok");
        eq(s.title, "Edremit piyasası · 1 No", "yayımlanmış başlık");
        eq(s.subtitle, "Ortalama 120,5 TL/kg · 7 üretici", "yayımlanmış alt satır");
        s = MeydanMarketStrip.of("Edremit / Balıkesir", true, -2, true, 120);
        check(!s.showsPrice && s.subtitle.endsWith("0/5"), "negatif sayı");
        eq(MeydanMarketStrip.loading("Alaşehir / Manisa").subtitle, "Bölge verisi yükleniyor…", "yükleniyor");
        System.out.println("MeydanMarketStripTest: " + checks + " kontrol geçti");
    }
}
