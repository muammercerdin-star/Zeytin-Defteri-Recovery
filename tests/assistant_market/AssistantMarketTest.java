package com.zeytindefteri.app;

import java.util.*;

/** Asistanın bölge piyasası cevabı. Gerçek ZeytinAssistantMarket + RegionGradePrice + MarketBackendConfig ile. */
public class AssistantMarketTest {
    static int checks = 0;
    static void check(boolean ok, String m) { checks++; if (!ok) throw new AssertionError(m); }
    static void eq(Object a, Object e, String m) { check(Objects.equals(a, e), m + ": beklenen «" + e + "», gelen «" + a + "»"); }
    static ZeytinAssistantMarket.Grade g(String n, int p, double med, boolean trend, double pct) { return new ZeytinAssistantMarket.Grade(n, p, med, 0, trend, pct); }
    static String norm(String s) { return ZeytinAssistantCommand.normalize(s); }

    public static void main(String[] args) {
        eq(ZeytinAssistantMarket.grade("DOUBLE"), "Double", "double");
        eq(ZeytinAssistantMarket.grade("1"), "1 No", "1 no");
        eq(ZeytinAssistantMarket.grade("YAGLIK"), "Yağlık", "yağlık");
        eq(ZeytinAssistantMarket.grade(""), "", "elek yok");
        // Soru gerçekten piyasa sorusu olarak anlaşılıyor ve elek doğru çıkıyor.
        for (String q : new String[]{"1 numara kaç para?", "Alaşehir'de double fiyatı ne?", "piyasa nasıl", "Bölgede elek fiyatı ne?"})
            eq(ZeytinAssistantCommand.parse(q).kind, ZeytinAssistantCommand.Kind.MARKET, "piyasa sorusu: " + q);
        eq(ZeytinAssistantMarket.grade(ZeytinAssistantCommand.parse("1 numara kaç para?").grade), "1 No", "elek çıkarımı");
        eq(ZeytinAssistantMarket.grade(ZeytinAssistantCommand.parse("Alaşehir'de double fiyatı ne?").grade), "Double", "double çıkarımı");

        check(ZeytinAssistantMarket.ownSales(norm("Benim 1 numara satış fiyatım ne?")), "kendi satışı");
        check(ZeytinAssistantMarket.ownSales(norm("Kaça sattım?")), "kaça sattım");
        check(!ZeytinAssistantMarket.ownSales(norm("1 numara kaç para?")), "bölge sorusu kendi satışı sanıldı");

        Map<String, String> regions = new LinkedHashMap<>();
        regions.put("alasehir-manisa", "Alaşehir / Manisa");
        regions.put("edremit-balikesir", "Edremit / Balıkesir");
        eq(ZeytinAssistantMarket.regionFor(norm("Alaşehir'de 1 numara kaç para?"), regions), "alasehir-manisa", "söylenen bölge");
        eq(ZeytinAssistantMarket.regionFor(norm("edremit double fiyatı"), regions), "edremit-balikesir", "eksiz bölge adı");
        eq(ZeytinAssistantMarket.regionFor(norm("1 numara kaç para?"), regions), "", "yer söylenmedi → kendi bölgesi");
        eq(ZeytinAssistantMarket.regionFor(norm("Piyasada 1 numara ne?"), regions), "", "'piyasada' yer sayılmaz");
        eq(ZeytinAssistantMarket.regionFor(norm("Bölgemde double kaç para"), regions), "", "'bölgemde' yer sayılmaz");
        check(ZeytinAssistantMarket.regionFor(norm("Salihli'de 1 numara kaç para?"), regions) == null, "bilinmeyen bölge kendi bölgesine düşmemeli");

        int floor = MarketBackendConfig.PUBLIC_MARKET_MIN_PRODUCERS;
        List<ZeytinAssistantMarket.Grade> grades = Arrays.asList(
                g("Double", 6, 120, false, 0), g("1 No", 7, 100, true, 3.2), g("2 No", 5, 80.5, true, -0.2), g("3 No", 4, 77, true, 9), g("Yağlık", 0, 0, false, 0));
        String closed = ZeytinAssistantMarket.answer("Alaşehir / Manisa", 3, false, grades, "1 No");
        check(closed.startsWith("Alaşehir bölgesinde fiyatlar henüz paylaşılmıyor") && closed.contains("şu an 3") && !closed.contains("100"), "yayımlanmamış bölge: " + closed);
        check(!ZeytinAssistantMarket.answer("Alaşehir / Manisa", 9, false, grades, "").contains("120"), "sunucu yayımlamadıysa fiyat yok");
        String one = ZeytinAssistantMarket.answer("Alaşehir / Manisa", 9, true, grades, "1 No");
        check(one.startsWith("Alaşehir, 1 No: bu sezon medyan 100 TL/kg (7 üretici). Son 7 günde %3,2 arttı."), "elek cevabı: " + one);
        check(one.contains("fiyat garantisi değildir"), "uyarı yok");
        check(ZeytinAssistantMarket.answer("Alaşehir / Manisa", 9, true, grades, "2 No").contains("80,5 TL/kg (5 üretici). Son 7 günde yatay."), "yatay trend");
        String few = ZeytinAssistantMarket.answer("Alaşehir / Manisa", 9, true, grades, "3 No");
        check(few.contains("bu elekte fiyat paylaşılmıyor") && few.contains("şu an 4") && !few.contains("77") && !few.contains("%9"), "eşik altı elek fiyatı açığa çıktı: " + few);
        check(ZeytinAssistantMarket.answer("Alaşehir / Manisa", 9, true, grades, "4 No").contains("şu an 0"), "raporda olmayan elek");
        String all = ZeytinAssistantMarket.answer("Alaşehir / Manisa", 9, true, grades, "");
        check(all.startsWith("Alaşehir, bu sezon medyan fiyatlar: Double 120, 1 No 100, 2 No 80,5 TL/kg. Diğer eleklerde yeterli üretici yok."), "tüm elekler: " + all);
        check(!all.contains("77"), "eşik altı elek listede");
        List<ZeytinAssistantMarket.Grade> none = Arrays.asList(g("1 No", 2, 90, false, 0));
        check(ZeytinAssistantMarket.answer("Edremit / Balıkesir", 6, true, none, "").contains("hiçbir elekte " + floor + " üreticiye ulaşılmadı"), "hiç görünür elek yok");
        check(!ZeytinAssistantMarket.answer("Edremit / Balıkesir", 6, true, none, "").contains("90"), "gizli fiyat açığa çıktı");
        check(ZeytinAssistantMarket.answer("", 9, true, grades, "1 No").startsWith("Bölgen, 1 No"), "adsız bölge");
        check(!ZeytinAssistantMarket.answer("X", 9, true, Arrays.asList(g("1 No", 9, Double.NaN, true, Double.NaN)), "1 No").contains("NaN"), "NaN yazıldı");
        check(ZeytinAssistantMarket.noRegion().contains("Elek kaydı"), "bölge yok metni");
        System.out.println("AssistantMarketTest: " + checks + " kontrol geçti");
    }
}
