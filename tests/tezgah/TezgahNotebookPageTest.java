package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.List;

/** Defter görünümü modeli: sayfalama, eskiden yeniye, numara/T değerleri, toplu satış, Türkçe arama. */
public class TezgahNotebookPageTest {
    static int n;

    static void ok(boolean c, String m) {
        if (!c) {
            throw new AssertionError(m);
        }
        n++;
    }

    static void eq(double a, double b, String m) {
        ok(Math.abs(a - b) < 1e-6, m + ": " + a + " != " + b);
    }

    static void eqs(String a, String b, String m) {
        ok(a == null ? b == null : a.equals(b), m + ": \"" + a + "\" != \"" + b + "\"");
    }

    /** Numara ayrımlı alım: her numaraya net ve fiyat verir; T = net toplamı ve tutar toplamı. */
    static TezgahNotebookPage.Entry numbered(long id, String seller, String date, double[] net, double price) {
        double[] amount = new double[TezgahNotebookPage.GRADES];
        double totalKg = 0;
        double totalGross = 0;
        for (int g = 0; g < net.length; g++) {
            amount[g] = net[g] * price;
            totalKg += net[g];
            totalGross += amount[g];
        }
        return new TezgahNotebookPage.Entry(id, seller, date, 10, totalKg, totalGross, net, amount, true, false);
    }

    /** Toplu satış: numara ayrımı yok, yalnız T dolu. */
    static TezgahNotebookPage.Entry bulk(long id, String seller, String date, double kg, double gross) {
        return new TezgahNotebookPage.Entry(id, seller, date, 5, kg, gross, null, null, false, false);
    }

    static List<TezgahNotebookPage.Entry> many(int count) {
        List<TezgahNotebookPage.Entry> list = new ArrayList<>();
        // Yeniden eskiye gelir: en yeni (id büyük) başta.
        for (int i = count; i >= 1; i--) {
            list.add(bulk(i, "Satıcı " + i, "2026-11-25", i, i * 10));
        }
        return list;
    }

    public static void main(String[] args) {
        // -------- sayfalama: 0, 1, 20, 21
        ok(TezgahNotebookPage.build(new ArrayList<>(), "2026-11-25").size() == 1, "0 alım: boş tek sayfa");
        ok(TezgahNotebookPage.build(new ArrayList<>(), "2026-11-25").get(0).sections.isEmpty(), "0 alım: bölüm yok");
        ok(TezgahNotebookPage.build(many(1), "2026-11-25").size() == 1, "1 alım tek sayfa");
        List<TezgahNotebookPage> p20 = TezgahNotebookPage.build(many(20), "2026-11-25");
        ok(p20.size() == 1, "20 alım tek sayfa");
        ok(p20.get(0).sections.size() == 20, "20 bölüm");
        ok(p20.get(0).total == 1, "toplam sayfa 1");
        List<TezgahNotebookPage> p21 = TezgahNotebookPage.build(many(21), "2026-11-25");
        ok(p21.size() == 2, "21 alım iki sayfa");
        ok(p21.get(0).sections.size() == 20, "ilk sayfa 20 dolu");
        ok(p21.get(1).sections.size() == 1, "ikinci sayfa 1");
        ok(p21.get(0).total == 2 && p21.get(1).total == 2, "toplam sayfa 2");
        ok(p21.get(1).index == 1, "ikinci sayfa indeksi 1");

        // -------- eskiden yeniye: gelen yeniden eskiye (id 21..1), sayfalar eskiden yeniye (id 1..)
        ok(p21.get(0).sections.get(0).entry.id == 1, "ilk bölüm en eski alım");
        ok(p21.get(0).sections.get(19).entry.id == 20, "20. bölüm id 20");
        ok(p21.get(1).sections.get(0).entry.id == 21, "son sayfada en yeni");

        // -------- numara satırları ve T
        double[] net = {99, 19, 0, 113, 104, 54};
        TezgahNotebookPage.Entry e = numbered(1, "Ahmet Yılmaz", "2026-11-25", net, 110);
        TezgahNotebookPage.Section s = TezgahNotebookPage.build(java.util.Arrays.asList(e), "2026-11-25").get(0).sections.get(0);
        ok(s.filled(0), "D dolu");
        eq(s.rowKg(0), 99, "D kilo");
        eq(s.rowAmount(0), 99 * 110, "D tutar = kilo × fiyat");
        ok(s.filled(1), "1 dolu");
        ok(!s.filled(2), "2 boş (kilo 0)");
        eq(s.rowKg(3), 113, "3 kilo");
        ok(s.filled(5), "Y dolu");
        eq(s.totalKg(), 99 + 19 + 113 + 104 + 54, "T kilo = net toplamı");
        eq(s.totalAmount(), (99 + 19 + 113 + 104 + 54) * 110, "T tutar = tutar toplamı");

        // -------- T her zaman kayıttaki değer (yeniden hesapla farklıysa kayıt esas)
        TezgahNotebookPage.Entry edited = new TezgahNotebookPage.Entry(2, "Düzeltme", "2026-11-25", 10,
                500, 55000, new double[]{100, 0, 0, 0, 0, 0}, new double[]{11000, 0, 0, 0, 0, 0}, true, false);
        TezgahNotebookPage.Section es = TezgahNotebookPage.build(java.util.Arrays.asList(edited), "2026-11-25").get(0).sections.get(0);
        eq(es.totalKg(), 500, "T kilo kayıttan (numara toplamı 100 olsa da)");
        eq(es.totalAmount(), 55000, "T tutar kayıttan");

        // -------- toplu satışta yalnız T
        TezgahNotebookPage.Section bs = TezgahNotebookPage.build(java.util.Arrays.asList(bulk(3, "Toplu", "2026-11-25", 1240, 136400)), "2026-11-25").get(0).sections.get(0);
        for (int g = 0; g < TezgahNotebookPage.GRADES; g++) {
            ok(!bs.filled(g), "toplu satış numara " + g + " boş");
        }
        eq(bs.totalKg(), 1240, "toplu T kilo");
        eq(bs.totalAmount(), 136400, "toplu T tutar");

        // -------- başlık: GELEN KASA = kasa toplamı; GİDEN KASA / GELEN PARA boş
        List<TezgahNotebookPage.Entry> hdr = new ArrayList<>();
        hdr.add(numbered(1, "A", "2026-11-24", net, 100));
        hdr.add(bulk(2, "B", "2026-11-25", 100, 1000));
        TezgahNotebookPage hp = TezgahNotebookPage.build(hdr, "2026-11-25").get(0);
        ok(hp.gelenKasa == 15, "GELEN KASA kasa toplamı (10+5)");
        ok(hp.gidenKasa == null, "GİDEN KASA boş");
        ok(hp.gelenPara == null, "GELEN PARA boş");
        eqs(hp.dateText, "25 / 11 / 2026", "TARİH en yeni alımın günü");

        // -------- Türkçe arama
        List<TezgahNotebookPage.Entry> search = new ArrayList<>();
        search.add(bulk(1, "İsmail Şahin", "2026-11-25", 10, 100));
        search.add(bulk(2, "Mehmet Kaya", "2026-11-25", 10, 100));
        search.add(bulk(3, "ismail demir", "2026-11-25", 10, 100));
        // Model eskiden yeniye çevirir: bölümler [ismail demir(0), Mehmet(1), İsmail Şahin(2)].
        List<TezgahNotebookPage> sp = TezgahNotebookPage.build(search, "2026-11-25");
        List<int[]> h1 = TezgahNotebookPage.search(sp, "ismail");
        ok(h1.size() == 2, "ismail ↔ İsmail iki sonuç");
        ok(sp.get(0).sections.get(0).highlighted, "ismail demir işaretli");
        ok(sp.get(0).sections.get(2).highlighted, "İsmail Şahin işaretli");
        ok(!sp.get(0).sections.get(1).highlighted, "Mehmet işaretsiz");
        List<int[]> h2 = TezgahNotebookPage.search(sp, "ŞAHİN");
        ok(h2.size() == 1, "ŞAHİN ↔ Şahin bir sonuç");
        ok(sp.get(0).sections.get(2).highlighted, "Şahin işaretli (büyük harf sorgu)");
        ok(!sp.get(0).sections.get(0).highlighted, "ŞAHİN sonrası ismail demir işaretsiz");
        ok(TezgahNotebookPage.search(sp, "").isEmpty(), "boş sorgu eşleşmez");
        ok(!sp.get(0).sections.get(2).highlighted, "boş sorgu işaretleri siler");
        ok(TezgahNotebookPage.search(sp, "yokboyle").isEmpty(), "bulunamayan 0 sonuç");

        // -------- hücre biçimleri (Türkçe, birimsiz)
        eqs(TezgahNotebookPage.cellTl(31540), "31.540", "tutar Türkçe binlik");
        eqs(TezgahNotebookPage.cellKg(389), "389", "kilo tam sayı");
        eqs(TezgahNotebookPage.cellKg(2.5), "2,5", "kilo ondalık virgül");
        ok(!TezgahNotebookPage.cellTl(1000).contains("₺"), "hücrede ₺ yok");
        ok(!TezgahNotebookPage.cellKg(100).contains("kg"), "hücrede kg yok");
        eqs(TezgahNotebookPage.dateText("2026-03-01"), "01 / 03 / 2026", "tarih biçimi");
        eqs(TezgahNotebookPage.dateText("bozuk"), "bozuk", "bozuk tarih olduğu gibi");

        System.out.println("TezgahNotebookPageTest tamam: " + n + " kontrol");
    }
}
