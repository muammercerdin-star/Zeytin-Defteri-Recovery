package com.zeytindefteri.app;

import java.util.Arrays;
import java.util.List;

public class BuyerPayPlanTest {
    static int n, fail;
    static void ok(boolean c, String w) { n++; if (!c) { fail++; System.out.println("FAIL " + w); } }
    static void eq(double a, double b, String w) { ok(Math.abs(a - b) < 0.005, w + ": " + a + " != " + b); }

    public static void main(String[] a) {
        // Ekran görüntüsündeki durum: 1 alıcı, 4 satış, 10.340 TL (tarih sırası karışık girilmiş)
        List<BuyerPayPlan.Open> open = Arrays.asList(
                new BuyerPayPlan.Open(14, "2026-11-08", 2600),
                new BuyerPayPlan.Open(9, "2026-10-28", 2585),
                new BuyerPayPlan.Open(11, "2026-11-02", 2655),
                new BuyerPayPlan.Open(12, "2026-11-02", 2500));
        eq(BuyerPayPlan.total(open), 10340, "toplam borç");

        BuyerPayPlan.Result all = BuyerPayPlan.plan(open, 10340);
        ok(all.parts.size() == 4 && all.closedCount() == 4, "tamamı: 4 satış kapanır");
        eq(all.leftAfter, 0, "borç kalmaz");
        ok(BuyerPayPlan.note(all, 4).equals("Bütün borç kapanacak (4 satış)."), "not: " + BuyerPayPlan.note(all, 4));

        BuyerPayPlan.Result part = BuyerPayPlan.plan(open, 6000);
        ok(part.parts.get(0).saleId == 9, "en eski satış önce (28 Ekim)");
        ok(part.parts.get(1).saleId == 11 && part.parts.get(2).saleId == 12, "aynı gün: önce girilen önce");
        ok(part.closedCount() == 2, "6.000 TL: iki satış kapanır");
        eq(part.parts.get(2).amount, 760, "üçüncüye kalan pay");
        ok(!part.parts.get(2).closes, "üçüncü kısmi");
        eq(part.leftAfter, 4340, "kalan borç");
        ok(BuyerPayPlan.note(part, 4).equals("En eski 2 satış kapanacak. 4.340 TL borç kalacak."), "not: " + BuyerPayPlan.note(part, 4));

        BuyerPayPlan.Result small = BuyerPayPlan.plan(open, 1000);
        ok(small.parts.size() == 1 && small.closedCount() == 0, "küçük ödeme tek satışa");
        ok(BuyerPayPlan.note(small, 4).startsWith("En eski satışa işlenecek."), "küçük ödeme notu");

        BuyerPayPlan.Result over = BuyerPayPlan.plan(open, 12000);
        eq(over.excess, 1660, "fazla yazılan");
        ok(BuyerPayPlan.note(over, 4).startsWith("Toplam borçtan 1.660 TL fazla"), "fazla notu: " + BuyerPayPlan.note(over, 4));

        ok(BuyerPayPlan.plan(open, 0).empty(), "sıfır tutar boş");
        ok(BuyerPayPlan.note(BuyerPayPlan.plan(open, 0), 4).equals("Alınan tutarı yaz."), "boş not");
        eq(BuyerPayPlan.plan(Arrays.asList(new BuyerPayPlan.Open(1, "2026-11-01", 0.004)), 50).excess, 50, "kapalı satış sayılmaz");

        // Kuruş yuvarlaması: üç satışa 100,01 dağıtımında toplam bozulmaz
        List<BuyerPayPlan.Open> cents = Arrays.asList(new BuyerPayPlan.Open(1, "a", 33.34),
                new BuyerPayPlan.Open(2, "b", 33.33), new BuyerPayPlan.Open(3, "c", 33.34));
        BuyerPayPlan.Result c = BuyerPayPlan.plan(cents, 100.01);
        double sum = 0;
        for (BuyerPayPlan.Part p : c.parts) sum += p.amount;
        eq(sum, 100.01, "kuruşlar doğru");
        ok(c.closedCount() == 3, "kuruşla hepsi kapanır");

        System.out.println(n - fail + "/" + n + " geçti");
        if (fail > 0) System.exit(1);
    }
}
