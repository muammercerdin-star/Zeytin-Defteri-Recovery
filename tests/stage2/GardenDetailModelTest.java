package com.zeytindefteri.app;

/** GardenDetailModel ve OperationKinds için ana makine testi (Android gerekmez). */
public final class GardenDetailModelTest {
    private static int checks = 0;

    static void eq(Object expected, Object actual, String what) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new AssertionError(what + ": beklenen <" + expected + "> gelen <" + actual + ">");
    }

    public static void main(String[] a) {
        eq("Ayvalık · Girelli / Edremit / Balıkesir", GardenDetailModel.subtitle("Ayvalık", "Girelli", "Edremit", "Balıkesir"), "tam alt başlık");
        eq("Çeşit belirtilmedi · Edremit / Balıkesir", GardenDetailModel.subtitle(" ", null, "Edremit", "Balıkesir"), "çeşitsiz");
        eq("Memecik", GardenDetailModel.subtitle("Memecik", "", null, " "), "konumsuz");
        eq("—", GardenDetailModel.count(0), "ağaç 0");
        eq("120", GardenDetailModel.count(120), "ağaç");
        eq("—", GardenDetailModel.number(0), "dekar 0");
        eq("12,5", GardenDetailModel.number(12.5), "dekar ondalık");
        eq("—", GardenDetailModel.irrigation(null), "sulama boş");

        long d1 = GardenDetailModel.epochDay("14.10.2026");
        eq(GardenDetailModel.civilToDays(2026, 10, 14), d1, "tarih çözümleme");
        eq(0L, GardenDetailModel.civilToDays(1970, 1, 1), "epoch");
        eq(366L, GardenDetailModel.civilToDays(1971, 1, 2), "epoch+366");
        eq(-1L, GardenDetailModel.epochDay("2026-10-14"), "yanlış biçim");
        eq(-1L, GardenDetailModel.epochDay("32.01.2026"), "geçersiz gün");
        eq(-1L, GardenDetailModel.epochDay(null), "boş tarih");
        eq(d1, GardenDetailModel.epochDay("14.10.2026 09:30"), "saatli tarih");
        eq(1L, GardenDetailModel.epochDay("01.03.2024") - GardenDetailModel.epochDay("29.02.2024"), "artık yıl");

        eq("", GardenDetailModel.staleWarning(d1 - 29, d1), "29 gün uyarı yok");
        eq("Son işlem 30 gün önce. Kayıtlarını ve bahçedeki durumu kontrol et.", GardenDetailModel.staleWarning(d1 - 30, d1), "30 gün uyarı");
        eq("", GardenDetailModel.staleWarning(-1, d1), "işlem yok");
        eq("", GardenDetailModel.staleWarning(d1 + 3, d1), "gelecek tarih");

        eq("14.10.2026 · 2,5 ton · kuyu", GardenDetailModel.operationMeta("14.10.2026", 2.5, "ton", " kuyu "), "meta tam");
        eq("14.10.2026", GardenDetailModel.operationMeta("14.10.2026", 0, "ton", ""), "meta miktarsız");
        eq("", GardenDetailModel.operationMeta(null, 0, null, null), "meta boş");

        String[][] icons = {{"Sulama", "water"}, {"Gübre", "leaf"}, {"Gübreleme", "leaf"}, {"İlaçlama", "flask"},
                {"Budama", "scissors"}, {"Sinek tuzağı", "trap"}, {"Toprak İşleme", "soil"}, {"İşçilik", "worker"},
                {"Mazot", "fuel"}, {"Nakliye", "truck"}, {"Diğer", "more"}, {"Hasat", "basket"}, {"Satış", "tag"},
                {"SULAMA", "water"}, {"bilinmeyen", "note"}, {null, "note"}};
        for (String[] p : icons) eq(p[1], OperationKinds.iconKey(p[0]), "ikon " + p[0]);
        for (String t : OperationKinds.TYPES) {
            if ("note".equals(OperationKinds.iconKey(t))) throw new AssertionError("form türünün ikonu yok: " + t);
            checks++;
        }
        eq(OperationKinds.Tone.WATER, OperationKinds.tone("Sulama"), "ton sulama");
        eq(OperationKinds.Tone.AMBER, OperationKinds.tone("İlaçlama"), "ton ilaç");
        eq("İşlem", OperationKinds.label("  "), "boş ad");
        System.out.println("GardenDetailModelTest: " + checks + " kontrol geçti");
    }
}
