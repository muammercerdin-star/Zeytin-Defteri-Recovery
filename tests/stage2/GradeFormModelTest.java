package com.zeytindefteri.app;

/** GradeFormModel için ana makine testi: Türkçe sayı okuma, tutar ve ödeme kuralı. */
public final class GradeFormModelTest {
    private static int checks = 0;

    static void eq(Object expected, Object actual, String what) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new AssertionError(what + ": beklenen <" + expected + "> gelen <" + actual + ">");
    }

    public static void main(String[] a) {
        eq(2400.0, GradeFormModel.parse("2.400"), "binlik nokta");
        eq(2400.5, GradeFormModel.parse("2.400,50"), "binlik + ondalık virgül");
        eq(8.5, GradeFormModel.parse("8,5"), "ondalık virgül");
        eq(8.5, GradeFormModel.parse("8.5"), "ondalık nokta");
        eq(2.25, GradeFormModel.parse("2.25"), "iki haneli ondalık");
        eq(1234567.0, GradeFormModel.parse("1.234.567"), "çok binlik");
        eq(150.0, GradeFormModel.parse("150 kg"), "kg eki");
        eq(85.0, GradeFormModel.parse("85 TL"), "TL eki");
        eq(1500.0, GradeFormModel.parse("1 500"), "bölünmez boşluk");
        eq(0.0, GradeFormModel.parse(""), "boş");
        eq(0.0, GradeFormModel.parse(null), "null");
        eq(0.0, GradeFormModel.parse("abc"), "harf");
        eq(0.0, GradeFormModel.parse("-5"), "negatif 0 sayılır");
        eq(0.0, GradeFormModel.parse("NaN"), "NaN");
        eq(0.0, GradeFormModel.parse("Infinity"), "sonsuz");

        // Hasattaki "son fiyat" önerisi number() ile yazılıp parse() ile okunur: gidiş-dönüş korunur.
        for (double v : new double[]{85, 92.5, 1250, 2400.75, 0.5}) {
            eq(v, GradeFormModel.parse(GradeFormModel.number(v)), "gidiş-dönüş " + v);
        }
        eq("2.400 TL", GradeFormModel.money(2400), "para biçimi");
        eq("12,5 kg", GradeFormModel.kg(12.5), "kilo biçimi");
        eq("—", GradeFormModel.lineValue(100, 0), "fiyatsız satır");
        eq("—", GradeFormModel.lineValue(0, 90), "kilosuz satır");
        eq("9.000 TL", GradeFormModel.lineValue(100, 90), "satır tutarı");

        GradeFormModel.Payment F = GradeFormModel.Payment.FULL, P = GradeFormModel.Payment.PARTIAL, N = GradeFormModel.Payment.NONE;
        eq(9000.0, GradeFormModel.received(F, 9000, 0, true), "tamamı alındı = net");
        eq(3000.0, GradeFormModel.received(P, 9000, 3000, true), "bir kısmı = girilen");
        eq(0.0, GradeFormModel.received(N, 9000, 3000, true), "henüz değil = 0 (girilen yok sayılır)");
        eq(0.0, GradeFormModel.received(F, 9000, 0, false), "teklifte tahsilat yok");
        eq(N, GradeFormModel.paymentFor(0, 9000), "0 → henüz değil");
        eq(F, GradeFormModel.paymentFor(9000, 9000), "net → tamamı");
        eq(P, GradeFormModel.paymentFor(100, 9000), "kısmi");
        eq(6, GradeFormModel.CODES.length, "boy sayısı");
        eq("YAGLIK", GradeFormModel.CODES[5], "yağlık kodu değişmedi");
        System.out.println("GradeFormModelTest: " + checks + " kontrol geçti");
    }
}
