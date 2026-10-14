package com.zeytindefteri.app;

/** Bölge raporu elek fiyat kutusu: 5 üretici eşiği, biçim ve değişim satırı. Gerçek RegionGradePrice ile çalışır. */
public class RegionGradePriceTest {
    static int checks = 0;
    static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    static void eq(String actual, String expected, String what) { check(expected.equals(actual), what + ": beklenen '" + expected + "', gelen '" + actual + "'"); }

    public static void main(String[] args) {
        check(RegionGradePrice.threshold() == 5, "eşik 5 değil");
        // Eşik altında fiyat asla görünmez (tek/iki üreticinin fiyatı ortaya çıkmasın)
        for (int pc = 0; pc < 5; pc++) {
            check(!RegionGradePrice.visible(pc), pc + " üreticide görünür");
            eq(RegionGradePrice.priceText(pc, 120, 118), "—", pc + " üreticide fiyat");
            eq(RegionGradePrice.footText(pc, true, 4.2), pc + "/5 üretici", pc + " üreticide alt satır");
            check(RegionGradePrice.trendDirection(pc, true, 9) == 0, "eşik altında trend rengi");
        }
        eq(RegionGradePrice.footText(-3, false, 0), "0/5 üretici", "negatif sayı");
        // Eşik ve üstü: medyan, yoksa ortalama, yoksa —
        check(RegionGradePrice.visible(5) && RegionGradePrice.visible(12), "5+ üreticide gizli");
        eq(RegionGradePrice.priceText(5, 120, 118), "120", "tam sayı medyan");
        eq(RegionGradePrice.priceText(7, 100.5, 0), "100,5", "kesirli medyan (Türkçe virgül)");
        eq(RegionGradePrice.priceText(7, 87.456, 0), "87,46", "iki basamağa yuvarlama");
        eq(RegionGradePrice.priceText(9, 1250, 0), "1.250", "binlik ayırıcı");
        eq(RegionGradePrice.priceText(6, 0, 80), "80", "medyan yoksa ortalama");
        eq(RegionGradePrice.priceText(6, 0, 0), "—", "veri yok");
        eq(RegionGradePrice.priceText(6, Double.NaN, Double.NaN), "—", "geçersiz sayı");
        // Değişim satırı
        eq(RegionGradePrice.footText(5, false, 0), "trend yok", "trend yok");
        eq(RegionGradePrice.footText(5, true, 4.2), "▲ 4,2% · 7 gün", "yükseliş");
        eq(RegionGradePrice.footText(5, true, -3.25), "▼ 3,25% · 7 gün", "düşüş");
        eq(RegionGradePrice.footText(5, true, 0), "0% · 7 gün", "yatay");
        check(RegionGradePrice.trendDirection(5, true, 4.2) == 1 && RegionGradePrice.trendDirection(5, true, -3) == -1
                && RegionGradePrice.trendDirection(5, true, 1) == 0 && RegionGradePrice.trendDirection(5, false, 9) == 0, "trend rengi");
        System.out.println("RegionGradePriceTest: " + checks + " kontrol geçti");
    }
}
