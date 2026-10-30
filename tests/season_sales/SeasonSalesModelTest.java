package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.List;

/** SeasonSalesModel: kaynak etiketi, özet, çift kayıt işareti, açıklama metni. */
public class SeasonSalesModelTest {
    static int checks = 0, failed = 0;
    static void check(boolean ok, String m) { checks++; if (!ok) { failed++; System.out.println("FAIL " + m); } }

    static SeasonSalesModel.Line line(String date, String garden, double net, long elek, boolean exists) {
        SeasonSalesModel.Line l = new SeasonSalesModel.Line();
        l.date = date; l.garden = garden; l.net = net; l.sourceElekId = elek; l.elekExists = exists; l.kg = 100;
        return l;
    }

    public static void main(String[] a) {
        List<SeasonSalesModel.Line> ls = new ArrayList<>();
        ls.add(line("2026-10-29", "Girelli", 47115, 5, true));      // elekten
        ls.add(line("2026-10-20", "Girelli", 5000, 0, false));      // elle
        ls.add(line("2026-10-29", "girelli ", 47115, 0, false));    // elle, elek satışıyla aynı → çift olabilir
        ls.add(line("2026-10-10", "Hacılar", 3600, 9, false));      // eleği silinmiş
        SeasonSalesModel.markDuplicates(ls);
        check(ls.get(0).kind() == SeasonSalesModel.Kind.ELEK, "elekten");
        check(ls.get(1).kind() == SeasonSalesModel.Kind.MANUAL && !ls.get(1).possibleDuplicate, "elle, çift değil");
        check(ls.get(2).possibleDuplicate, "aynı gün/bahçe/tutar elle satış çift işaretlenir (büyük-küçük harf, boşluk)");
        check(ls.get(3).kind() == SeasonSalesModel.Kind.ELEK_DELETED, "eleği silinmiş");
        check(!ls.get(0).possibleDuplicate && !ls.get(3).possibleDuplicate, "elek satışları çift sayılmaz");
        SeasonSalesModel.Summary s = SeasonSalesModel.summarize(ls);
        check(Math.abs(s.total - 102830) < 0.01, "toplam");
        check(s.elekCount == 1 && Math.abs(s.elek - 47115) < 0.01, "elek toplamı");
        check(s.manualCount == 2 && Math.abs(s.manual - 52115) < 0.01, "elle toplamı");
        check(s.elekDeletedCount == 1 && Math.abs(s.elekDeleted - 3600) < 0.01, "silinmiş elek toplamı");
        check(s.duplicateCount == 1, "çift sayısı");
        String e = SeasonSalesModel.explain(s);
        check(e.contains("1 satış elekten: 47.115 TL"), "açıklama elek satırı: " + e);
        check(e.contains("2 satış elle girildi: 52.115 TL"), "açıklama elle satırı");
        check(e.contains("eleği silinmiş: 3.600 TL"), "açıklama silinmiş satırı");
        check(e.contains("yalnız elek kayıtlarını sayar"), "fark açıklaması");
        check(e.contains("Çift girilmiş olabilir"), "çift uyarısı");
        check(SeasonSalesModel.tag(ls.get(2)).equals("Elle girildi · çift olabilir"), "çift etiketi");
        check(SeasonSalesModel.tag(ls.get(0)).equals("Elekten"), "elek etiketi");

        // Yalnız elekten satış: fark açıklaması ve çift uyarısı yok.
        List<SeasonSalesModel.Line> only = new ArrayList<>();
        only.add(line("2026-10-29", "Girelli", 1000, 5, true));
        SeasonSalesModel.markDuplicates(only);
        String e2 = SeasonSalesModel.explain(SeasonSalesModel.summarize(only));
        check(!e2.contains("yalnız elek") && !e2.contains("Çift"), "yalnız elek: ek uyarı yok");
        check(SeasonSalesModel.explain(SeasonSalesModel.summarize(new ArrayList<>())).startsWith("Bu sezon satış kaydı yok"), "boş liste");

        // Farklı tutar ya da farklı gün çift değildir.
        List<SeasonSalesModel.Line> diff = new ArrayList<>();
        diff.add(line("2026-10-29", "Girelli", 1000, 5, true));
        diff.add(line("2026-10-29", "Girelli", 1200, 0, false));
        diff.add(line("2026-10-28", "Girelli", 1000, 0, false));
        SeasonSalesModel.markDuplicates(diff);
        check(!diff.get(1).possibleDuplicate && !diff.get(2).possibleDuplicate, "farklı tutar/gün çift değil");

        check(SeasonSalesModel.date("2026-10-29").equals("29 Ekim 2026"), "tarih");
        check(SeasonSalesModel.date("29.10.2026").equals("29.10.2026"), "tanınmayan tarih aynen");
        check(SeasonSalesModel.date(null).equals(""), "boş tarih");
        check(SeasonSalesModel.kg(949).equals("949 kg"), "kg");

        System.out.println((failed == 0 ? "PASS " : "FAIL ") + (checks - failed) + "/" + checks + " SeasonSalesModel");
        if (failed > 0) System.exit(1);
    }
}
