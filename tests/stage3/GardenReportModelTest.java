package com.zeytindefteri.app;

/** GardenReportModel için ana makine testi. */
public final class GardenReportModelTest {
    private static int checks = 0;

    static void eq(Object expected, Object actual, String what) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new AssertionError(what + ": beklenen <" + expected + "> gelen <" + actual + ">");
    }

    public static void main(String[] a) {
        eq("1.250,5 kg", GardenReportModel.format(1250.5, "kg"), "biçim");
        eq("120", GardenReportModel.format(120.0, ""), "birimsiz");
        eq("—", GardenReportModel.format(null, "kg"), "null");
        eq("—", GardenReportModel.format(Double.NaN, "kg"), "NaN");
        eq("↑ %12", GardenReportModel.change(12.0), "artış");
        eq("↓ %3,5", GardenReportModel.change(-3.5), "düşüş");
        eq("%0", GardenReportModel.change(0.0), "değişim yok");
        eq("—", GardenReportModel.change(null), "veri yok");

        GardenReportModel.Tone G = GardenReportModel.Tone.GOOD, B = GardenReportModel.Tone.BAD,
                N = GardenReportModel.Tone.NEUTRAL, M = GardenReportModel.Tone.MUTED;
        eq(G, GardenReportModel.changeTone(10.0, true), "hasat artışı iyi");
        eq(B, GardenReportModel.changeTone(10.0, false), "masraf artışı kötü");
        eq(G, GardenReportModel.changeTone(-5.0, false), "maliyet düşüşü iyi");
        eq(B, GardenReportModel.changeTone(-5.0, true), "gelir düşüşü kötü");
        eq(N, GardenReportModel.changeTone(7.0, null), "boy kilosu yön yorumlanmaz");
        eq(N, GardenReportModel.changeTone(0.0, true), "sıfır nötr");
        eq(M, GardenReportModel.changeTone(null, true), "veri yok soluk");

        eq(M, GardenReportModel.netTone(-500, 0), "satış yokken net soluk");
        eq(B, GardenReportModel.netTone(-500, 2), "negatif net");
        eq(G, GardenReportModel.netTone(800, 2), "pozitif net");

        eq(25, GardenReportModel.share(25, 100), "pay");
        eq(0, GardenReportModel.share(25, 0), "toplam sıfır");
        eq(100, GardenReportModel.bar(80, 80), "en büyük boy tam");
        eq(3, GardenReportModel.bar(1, 1000), "küçük boy en az %3");
        eq(0, GardenReportModel.bar(0, 1000), "kilosuz boy boş");
        eq(75, GardenReportModel.collectedPercent(750, 250), "tahsil oranı");
        eq(-1, GardenReportModel.collectedPercent(0, 0), "satış yoksa çubuk yok");
        eq(100, GardenReportModel.collectedPercent(500, -3), "negatif alacak sıfır sayılır");

        eq("Hasat toplamı 100 kg, elek kayıtları toplamı 120 kg. Elek kayıtları hasattan 20 kg fazla. Kayıtlarını kontrol et.",
                GardenReportModel.mismatch(100, 120, 20, 20, 0), "elek fazla");
        eq("Hasat toplamı 120 kg, elek kayıtları toplamı 100 kg. Elek kayıtları hasattan 20 kg eksik. Kayıtlarını kontrol et.",
                GardenReportModel.mismatch(120, 100, -20, 0, 20), "elek eksik");
        System.out.println("GardenReportModelTest: " + checks + " kontrol geçti");
    }
}
