package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.List;

/** Tanıtım verisi reklamda doğru hikâyeyi anlatıyor mu (elek raporu, alacak, bugünkü satış). */
public class TanitimTest {
    static int checks;
    static void ok(boolean c, String m) { checks++; if (!c) throw new AssertionError(m); }

    public static void main(String[] a) {
        String[] names = {"Mehmet Bey", "Hasan Bey", "Kemal Usta"};
        List<BuyerFairCompare.Sale> sales = new ArrayList<>();
        double todayKg = 0, hasanOpen = 0;
        int oldestOpen = -1;
        for (TanitimVerisi.Row r : TanitimVerisi.rows()) {
            BuyerFairCompare.Sale s = new BuyerFairCompare.Sale();
            s.buyerId = java.util.Arrays.asList(names).indexOf(r.buyer) + 1;
            ok(s.buyerId > 0, "bilinmeyen alıcı: " + r.buyer);
            s.gardenId = r.garden;
            s.day = 20000 - r.daysAgo;
            double[] p = r.prices();
            double gross = 0;
            for (int i = 0; i < 6; i++) { s.kg[i] = r.kg[i]; s.tl[i] = r.kg[i] * p[i]; gross += s.tl[i]; }
            sales.add(s);
            if (r.daysAgo == 0) todayKg += s.totalKg();
            double open = ElekSaleModel.PAY_ALL == r.pay ? 0 : ElekSaleModel.PAY_NONE == r.pay ? gross : gross - r.partial;
            if (open > 0.009) oldestOpen = Math.max(oldestOpen, r.daysAgo);
            if (r.buyer.equals("Hasan Bey")) hasanOpen += open;
        }
        List<BuyerElekReport.Buyer> buyers = new ArrayList<>();
        for (int i = 0; i < names.length; i++) buyers.add(new BuyerElekReport.Buyer(i + 1, names[i], 0));
        BuyerElekReport.Report rep = BuyerElekReport.build(sales, buyers);
        System.out.println(rep.plainText());
        ok(rep.headline.startsWith("Aynı zeytine en çok parayı Mehmet Bey veriyor.") && rep.headline.contains("En az veren Hasan Bey"),
                "başlık: " + rep.headline);
        BuyerElekReport.Section m = rep.sections.get(0), h = rep.sections.get(rep.sections.size() - 1);
        ok(m.title.startsWith("Mehmet Bey: kilo başı") && m.title.endsWith("fazla") && m.tone > 0, m.title);
        ok(h.title.startsWith("Hasan Bey: kilo başı") && h.title.endsWith("az") && h.tone < 0, h.title);
        ok(m.lines.get(1).startsWith("Eleği iri çıkarıyor"), "Mehmet iri: " + m.lines.get(1));
        ok(h.lines.get(1).startsWith("Eleği ufak çıkarıyor"), "Hasan ufak: " + h.lines.get(1));
        ok(todayKg > 100, "bugün satış var (Tanecik 'Bugün ... sattın' der)");
        ok(oldestOpen == 18 && hasanOpen > 0, "Hasan Bey'in 18 gündür bekleyen parası (Tanecik '!' ile söyler)");

        // Zeytin Ligi ve Sohbet tanıtımı: uydurma adlar (ad + baş harf), gizlilik eşiği, tanıtım kullanıcısı 4.
        java.util.Set<String> ligAd = new java.util.HashSet<>();
        double prev = Double.MAX_VALUE;
        int me = -1, i = 0;
        for (TanitimVerisi.LigUretici u : TanitimVerisi.ligSiralama()) {
            i++;
            ok(ligAd.add(u.name), "ligde aynı ad iki kez: " + u.name);
            ok(u.score < prev, "lig puana göre sıralı");
            prev = u.score;
            if (u.name.equals(TanitimVerisi.KULLANICI)) me = i;
            else ok(u.name.matches("\\p{Lu}\\p{Ll}+ \\p{Lu}\\."), "uydurma ad (ad + baş harf): " + u.name);
            ok(u.grade >= 0 && u.grade < 6 && u.kg >= 100, "lig satırı geçerli");
        }
        ok(me == 4, "tanıtım kullanıcısı ligde 4.");
        for (TanitimVerisi.LigBolge b : TanitimVerisi.ligBolgeler()) ok(b.producers >= 5, "bölge gizlilik eşiği (5+ üretici): " + b.name);
        ok(TanitimVerisi.ligBolgeler()[0].id.equals(TanitimVerisi.LIG_BOLGE), "ilk bölge ligin bölgesi");
        for (TanitimVerisi.SohbetMesaj sm : TanitimVerisi.sohbet()) {
            ok(ligAd.contains(sm.author) || sm.author.matches("\\p{Lu}\\p{Ll}+ \\p{Lu}\\."), "sohbet yazarı uydurma: " + sm.author);
            ok(!sm.text.isEmpty() && sm.text.length() <= 120, "kısa sohbet mesajı");
        }
        System.out.println("PASS: " + checks + " tanıtım verisi kontrolü");
    }
}
