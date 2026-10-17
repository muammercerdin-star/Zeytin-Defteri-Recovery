package com.zeytindefteri.app;

import java.util.*;

/** Asistan paneli metin kuralları: okunur tarih, kaynak etiketi, hazır sorular. Gerçek ZeytinAssistantText ile. */
public class AssistantTextTest {
    static int checks = 0;
    static void check(boolean ok, String m) { checks++; if (!ok) throw new AssertionError(m); }
    static void eq(Object a, Object e, String m) { check(Objects.equals(a, e), m + ": beklenen «" + e + "», gelen «" + a + "»"); }

    public static void main(String[] args) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Europe/Istanbul"));
        c.clear(); c.set(2026, Calendar.OCTOBER, 17, 12, 0);
        long now = c.getTimeInMillis();
        eq(ZeytinAssistantText.humanDates("Girelli: kayıtlı son sulama 2026-10-01.", now),
                "Girelli: kayıtlı son sulama 1 Ekim (16 gün önce).", "geçmiş tarih");
        eq(ZeytinAssistantText.humanDates("2026-10-17", now), "bugün", "bugün");
        eq(ZeytinAssistantText.humanDates("2026-10-16", now), "dün (16 Ekim)", "dün");
        eq(ZeytinAssistantText.humanDates("2026-10-18: en yüksek 24°C", now), "yarın (18 Ekim): en yüksek 24°C", "yarın");
        eq(ZeytinAssistantText.humanDates("2026-10-20", now), "20 Ekim", "ileri tarih göreli yazılmaz");
        eq(ZeytinAssistantText.humanDates("2026-08-01", now), "1 Ağustos", "60 günden eski: yalnızca tarih");
        eq(ZeytinAssistantText.humanDates("2025-03-05", now), "5 Mart 2025", "başka yıl");
        eq(ZeytinAssistantText.humanDates("01.10.2026 ve 2/10/2026", now), "1 Ekim (16 gün önce) ve 2 Ekim (15 gün önce)", "noktalı/eğik çizgili");
        eq(ZeytinAssistantText.humanDates("2026-02-31", now), "2026-02-31", "geçersiz tarih korunur");
        eq(ZeytinAssistantText.humanDates("2026-13-01", now), "2026-13-01", "geçersiz ay korunur");
        String numbers = "Toplam 1250 kg, 85,5 TL/kg, yağış %40, 3.250 TL, 12.5 kg";
        eq(ZeytinAssistantText.humanDates(numbers, now), numbers, "başka sayılara dokunulmaz");
        eq(ZeytinAssistantText.humanDates(null, now), "", "null");
        // Aralık/Ocak geçişi: yıl farkı doğru.
        c.clear(); c.set(2027, Calendar.JANUARY, 2, 9, 0);
        eq(ZeytinAssistantText.humanDates("2026-12-30", c.getTimeInMillis()), "30 Aralık 2026 (3 gün önce)", "yıl geçişi");

        eq(ZeytinAssistantText.source(ZeytinAssistantCommand.Kind.IRRIGATION, false, "Girelli"), "KAYITLARINDAN · GİRELLİ", "bahçe kaydı (Türkçe büyük harf)");
        eq(ZeytinAssistantText.source(ZeytinAssistantCommand.Kind.EXPENSE, true, "Girelli"), "KAYITLARINDAN · TÜM BAHÇELER", "toplam");
        eq(ZeytinAssistantText.source(ZeytinAssistantCommand.Kind.WEATHER, false, "Afşar"), "HAVA TAHMİNİ · AFŞAR", "hava");
        eq(ZeytinAssistantText.source(ZeytinAssistantCommand.Kind.MARKET, false, "x"), "KENDİ SATIŞ KAYITLARIN", "piyasa yalnızca kendi satışların (bölge fiyatı gibi görünmesin)");
        eq(ZeytinAssistantText.source(ZeytinAssistantCommand.Kind.UNKNOWN, false, ""), "ZEYTİN ASİSTANI", "bilinmeyen");
        eq(ZeytinAssistantText.source(ZeytinAssistantCommand.Kind.HARVEST, false, ""), "KAYITLARINDAN", "bahçesiz");

        List<String> f = ZeytinAssistantText.followUps(ZeytinAssistantCommand.Kind.IRRIGATION, "son sulama ne zaman");
        check(f.size() == 3, "üç öneri: " + f);
        for (String q : f) check(ZeytinAssistantCommand.parse(q).kind != ZeytinAssistantCommand.Kind.IRRIGATION, "aynı konu önerildi: " + q);
        check(!ZeytinAssistantText.followUps(ZeytinAssistantCommand.Kind.EXPENSE, "Bu sezon masrafım kaç?").contains("Bu sezon masrafım kaç?"), "sorulan soru tekrar önerildi");
        check(ZeytinAssistantText.followUps(ZeytinAssistantCommand.Kind.UNKNOWN, "xyz").size() == 3, "bilinmeyen soruda öneri var");
        // Hazır ve örnek soruların hepsi asistanın gerçekten anladığı sorular olmalı.
        for (String[] group : ZeytinAssistantText.EXAMPLES)
            for (int i = 1; i < group.length; i++)
                check(ZeytinAssistantCommand.parse(group[i]).kind != ZeytinAssistantCommand.Kind.UNKNOWN, "örnek soru anlaşılmıyor: " + group[i]);
        for (String q : ZeytinAssistantText.followUps(ZeytinAssistantCommand.Kind.UNKNOWN, ""))
            check(ZeytinAssistantCommand.parse(q).kind != ZeytinAssistantCommand.Kind.UNKNOWN, "öneri anlaşılmıyor: " + q);
        System.out.println("AssistantTextTest: " + checks + " kontrol geçti");
    }
}
