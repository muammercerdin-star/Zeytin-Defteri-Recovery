package com.zeytindefteri.app;

/** Elek fişi baskı animasyonu: adım adım ilerleme, süre sınırları, damga ve barkod. */
public final class ReceiptPrintTest {
    static int n, bad;
    static void ok(boolean c, String m) { n++; if (!c) { bad++; System.out.println("FAIL " + m); } }

    public static void main(String[] a) {
        // İlerleme: uçlar sabit, monoton, adım adım (durak var)
        for (int steps : new int[]{8, 14, 22}) {
            ok(ReceiptPrint.progress(0f, steps) == 0f, "başta 0");
            ok(ReceiptPrint.progress(1f, steps) == 1f, "sonda 1");
            ok(ReceiptPrint.progress(-1f, steps) == 0f && ReceiptPrint.progress(2f, steps) == 1f, "sınır dışı");
            float prev = 0f, maxJump = 0f;
            for (int i = 1; i <= 1000; i++) {
                float p = ReceiptPrint.progress(i / 1000f, steps);
                ok(p >= prev, "monoton " + steps + " @" + i);
                if (i < 990) ok(p > prev, "duraklama yok (akıcı) @" + i);
                maxJump = Math.max(maxJump, p - prev);
                prev = p;
            }
            ok(maxJump < 0.0016f, "sıçrama yok (" + maxJump + ")");
            // Sonda yavaşlar: son %5'te çıkan pay, ortadaki %5'ten az
            float end = 1f - ReceiptPrint.progress(0.95f, steps);
            float mid = ReceiptPrint.progress(0.50f, steps) - ReceiptPrint.progress(0.45f, steps);
            ok(end < mid * 0.3f, "sonda yumuşak duruş");
            ok(Math.abs(ReceiptPrint.progress(0.5f, steps) - ReceiptPrint.progress(0.5f, 99)) < 1e-6f, "adım sayısı hareketi değiştirmez");
            ok(ReceiptPrint.stepIndex(0.999f, steps) == steps - 1 && ReceiptPrint.stepIndex(1f, steps) == steps, "adım sayacı");
        }
        // Süre ve adım sınırları
        ok(ReceiptPrint.durationMs(0) == ReceiptPrint.MIN_MS, "kısa fiş en az");
        ok(ReceiptPrint.durationMs(5000) == ReceiptPrint.MAX_MS, "uzun fiş en çok 1,7 sn");
        ok(ReceiptPrint.durationMs(600) > ReceiptPrint.durationMs(400), "uzun fiş biraz daha uzun");
        ok(ReceiptPrint.steps(100) == 8 && ReceiptPrint.steps(5000) == 22 && ReceiptPrint.steps(560) == 14, "adım sayısı");
        // Damga
        ok(ReceiptPrint.stamp(null).equals("KAYDEDİLDİ"), "satış yok");
        ok(ReceiptPrint.stamp(new double[]{10000, 10000}).equals("ÖDENDİ"), "tamamı alındı");
        ok(ReceiptPrint.stamp(new double[]{10000, 2000}).equals("ALACAK"), "kısmi");
        ok(ReceiptPrint.stamp(new double[]{10000, 0}).equals("ALACAK"), "hiç alınmadı");
        ok(ReceiptPrint.stamp(new double[]{0, 0}).equals("ALACAK"), "tutarsız satış ödendi sayılmaz");
        // Barkod: sabit, kayda göre değişir, genişlik 1..3
        int[] b1 = ReceiptPrint.barcode(42, 44), b2 = ReceiptPrint.barcode(42, 44), b3 = ReceiptPrint.barcode(43, 44);
        ok(java.util.Arrays.equals(b1, b2), "aynı kayıt aynı barkod");
        ok(!java.util.Arrays.equals(b1, b3), "farklı kayıt farklı barkod");
        boolean range = true; for (int w : b1) range &= w >= 1 && w <= 3;
        ok(range && b1.length == 44, "çubuk genişlikleri");
        ok(ReceiptPrint.barcode(1, 0).length == 0, "boş barkod");
        ok(ReceiptPrint.number(123).equals("No 000123") && ReceiptPrint.number(1234567).equals("No 1234567")
                && ReceiptPrint.number(-5).equals("No 000000"), "fiş numarası");
        System.out.println("ReceiptPrintTest: " + (n - bad) + "/" + n);
        if (bad > 0) System.exit(1);
    }
}
