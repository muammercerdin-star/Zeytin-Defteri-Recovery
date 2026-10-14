package com.zeytindefteri.app;

import java.util.List;

/** GardenFormModel için ana makine testi (Android gerekmez). */
public final class GardenFormModelTest {
    private static int checks = 0;

    static void eq(Object expected, Object actual, String what) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new AssertionError(what + ": beklenen <" + expected + "> gelen <" + actual + ">");
    }

    public static void main(String[] a) {
        GardenFormModel.Mode F = GardenFormModel.Mode.FIRST, A = GardenFormModel.Mode.ADD, E = GardenFormModel.Mode.EDIT;
        eq("İlk bahçeni oluşturalım", GardenFormModel.title(F), "ilk başlık");
        eq("Bahçeyi düzenle", GardenFormModel.title(E), "düzenle başlık");
        eq("Bahçemi Kaydet", GardenFormModel.saveLabel(F), "ilk düğme");
        eq("Bahçeyi Kaydet", GardenFormModel.saveLabel(A), "ekle düğme");
        eq("Değişiklikleri kaydet", GardenFormModel.saveLabel(E), "düzenle düğme");

        List<String> base = GardenFormModel.irrigationOptions("");
        eq(8, base.size(), "8 sulama seçeneği");
        eq(0, GardenFormModel.irrigationIndex(base, ""), "boş → Belirtilmedi");
        eq(1, GardenFormModel.irrigationIndex(base, "damla sulama"), "büyük/küçük harf");
        List<String> legacy = GardenFormModel.irrigationOptions("Karık");
        eq(9, legacy.size(), "eski serbest metin eklendi");
        eq(8, GardenFormModel.irrigationIndex(legacy, "Karık"), "eski değer seçili");
        eq("", GardenFormModel.irrigationToStore("Belirtilmedi"), "Belirtilmedi boş yazılır");
        eq("Yağmurlama", GardenFormModel.irrigationToStore("Yağmurlama"), "değer yazılır");

        eq(0, GardenFormModel.parseCount(""), "boş sayı 0");
        eq(120, GardenFormModel.parseCount(" 120 "), "sayı");
        eq(-1, GardenFormModel.parseCount("-3"), "negatif hata");
        eq(-1, GardenFormModel.parseCount("12a"), "harf hata");
        eq(8.5, GardenFormModel.parseArea("8,5"), "dekar virgül");
        eq(8.5, GardenFormModel.parseArea("8.5"), "dekar nokta");
        eq(0.0, GardenFormModel.parseArea(""), "dekar boş");
        eq(-1.0, GardenFormModel.parseArea("-2"), "dekar negatif");
        eq(-1.0, GardenFormModel.parseArea("NaN"), "dekar NaN");
        eq(true, GardenFormModel.requiresTreeAndAge(F), "ilk bahçede ağaç/yaş zorunlu");
        eq(false, GardenFormModel.requiresTreeAndAge(A), "eklemede zorunlu değil");

        GardenFormModel.LocationCheck OK = GardenFormModel.LocationCheck.OK, REQ = GardenFormModel.LocationCheck.PICK_REQUIRED,
                AGAIN = GardenFormModel.LocationCheck.PICK_AGAIN;
        eq(REQ, GardenFormModel.checkLocation(F, "", "", "", 0, 0, 0, false), "ilk bahçe konumsuz");
        eq(OK, GardenFormModel.checkLocation(F, "Balıkesir", "Edremit", "Girelli", 10, 20, 30, false), "ilk bahçe seçili");
        eq(OK, GardenFormModel.checkLocation(A, "", "", "", 0, 0, 0, false), "eklemede boş konum serbest");
        eq(AGAIN, GardenFormModel.checkLocation(A, "Balıkesir", "", "", 0, 0, 0, false), "yarım konum");
        eq(OK, GardenFormModel.checkLocation(E, "Balıkesir", "Edremit", "", 0, 0, 0, true), "düzenlemede değişmeyen eski konum");
        eq(AGAIN, GardenFormModel.checkLocation(E, "Balıkesir", "Edremit", "", 0, 0, 0, false), "düzenlemede değişmiş kimliksiz konum");
        eq(OK, GardenFormModel.checkLocation(E, "Balıkesir", "Edremit", "Girelli", 1, 2, 3, false), "düzenlemede yeni seçim");

        eq("Girelli", GardenFormModel.locationTitle("Balıkesir", "Edremit", "Girelli"), "konum başlık");
        eq("Edremit / Balıkesir", GardenFormModel.locationSubtitle("Balıkesir", "Edremit", "Girelli"), "konum alt");
        eq("Edremit", GardenFormModel.locationTitle("Balıkesir", "Edremit", ""), "mahallesiz başlık");
        eq("Balıkesir", GardenFormModel.locationSubtitle("Balıkesir", "Edremit", ""), "mahallesiz alt");
        eq("Balıkesir", GardenFormModel.locationTitle("Balıkesir", "", ""), "yalnız il");
        eq("", GardenFormModel.locationSubtitle("Balıkesir", "", ""), "yalnız il alt boş");
        eq("Konum seç", GardenFormModel.locationTitle("", null, " "), "konumsuz başlık");
        eq("İl, ilçe ve mahalle / köy seç", GardenFormModel.locationSubtitle("", "", ""), "konumsuz alt");

        eq("120 ağaç · 14 yaş · Domat\nBalıkesir / Edremit / Girelli",
                GardenFormModel.summary(120, 14, "Domat", "Balıkesir", "Edremit", "Girelli"), "özet");
        eq("", GardenFormModel.summary(0, 0, "", "", "", ""), "boş özet");
        System.out.println("GardenFormModelTest: " + checks + " kontrol geçti");
    }
}
