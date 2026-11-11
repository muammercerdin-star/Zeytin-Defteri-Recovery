package com.zeytindefteri.app;

public class TanecikDismissTest {
    static int n, fail;
    static void ok(boolean c, String w) { n++; if (!c) { fail++; System.out.println("FAIL " + w); } }
    public static void main(String[] a) {
        float d = 3f;                          // xxhdpi
        float cx = TanecikDismiss.centerX(1080);
        float cy = TanecikDismiss.centerY(2200, d);
        ok(cx == 540f, "ortada");
        ok(cy == 2200 - 72 * 3, "alt menünün 72 dp üstü");
        ok(TanecikDismiss.over(cx, cy, cx, cy, d), "tam üstünde");
        ok(TanecikDismiss.over(cx + 60 * d, cy, cx, cy, d), "kenarda (60 dp) yakalar");
        ok(!TanecikDismiss.over(cx + 70 * d, cy, cx, cy, d), "70 dp uzakta yakalamaz");
        ok(!TanecikDismiss.over(cx, cy - 300 * d, cx, cy, d), "yukarıda yakalamaz");
        // Tanecik en alta indiğinde (ortası zeminin ~30 dp üstü) X'e erişilebilmeli.
        float h = Math.round(58 * 270f / 240f) * d;
        float lowestCenter = 2200 - h + 2 + h / 2f;
        ok(TanecikDismiss.over(cx, lowestCenter, cx, cy, d), "en alttan erişilir");
        ok(TanecikDismiss.CLOSED_TOAST.contains("Ayarlar"), "geri açma yolu söylenir");
        System.out.println(n - fail + "/" + n + " geçti");
        if (fail > 0) System.exit(1);
    }
}
