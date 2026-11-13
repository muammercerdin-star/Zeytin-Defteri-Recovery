package com.zeytindefteri.app;

public class ElekDateFixTest {
    static int n, fail;
    static void ok(boolean c, String w) { n++; if (!c) { fail++; System.out.println("FAIL " + w); } }

    public static void main(String[] a) {
        String today = "2026-11-13";
        ok(ElekDateFix.valid("2026-11-12"), "geçerli gün");
        ok(!ElekDateFix.valid("2026-02-29") && ElekDateFix.valid("2028-02-29"), "artık yıl");
        ok(!ElekDateFix.valid("2026-04-31") && !ElekDateFix.valid("13.11.2026") && !ElekDateFix.valid(null), "geçersizler");
        ok(ElekDateFix.problem("2026-11-13", "2026-11-12", today) == null, "dünü seçmek serbest");
        ok("İleri bir tarih seçilemez.".equals(ElekDateFix.problem("2026-11-13", "2026-11-14", today)), "gelecek yasak");
        ok("Tarih zaten bu.".equals(ElekDateFix.problem("2026-11-13", "2026-11-13", today)), "aynı gün");
        ok("Geçersiz tarih.".equals(ElekDateFix.problem("2026-11-13", "x", today)), "geçersiz");
        ok(ElekDateFix.year("2025-12-30") == 2025, "yıl");
        String plain = ElekDateFix.confirm("13 Kasım 2026", "12 Kasım 2026", false, "2026-11-13", "2026-11-12");
        ok(plain.equals("13 Kasım 2026  →  12 Kasım 2026"), "satışsız onay: " + plain);
        String sale = ElekDateFix.confirm("13 Kasım 2026", "12 Kasım 2026", true, "2026-11-13", "2026-11-12");
        ok(sale.contains("Bağlı satışın tarihi de değişir; tutar ve ödemeler aynı kalır."), "satışlı onay");
        String season = ElekDateFix.confirm("2 Ocak 2026", "30 Aralık 2025", true, "2026-01-02", "2025-12-30");
        ok(season.contains("Kayıt 2025 sezonuna taşınır."), "sezon değişimi uyarısı");
        ok(ElekSaleModel.saleDate("2026-11-12").equals("12.11.2026"), "satış biçimi gg.aa.yyyy");
        System.out.println(n - fail + "/" + n + " geçti");
        if (fail > 0) System.exit(1);
    }
}
