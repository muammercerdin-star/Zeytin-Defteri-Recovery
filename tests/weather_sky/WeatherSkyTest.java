package com.zeytindefteri.app;

public final class WeatherSkyTest {
    static int n, bad;
    static void check(boolean ok, String m) { n++; if (!ok) { bad++; System.out.println("FAIL " + m); } }
    public static void main(String[] a) {
        check(WeatherSky.kind(0) == WeatherSky.Kind.CLEAR, "0 açık");
        check(WeatherSky.kind(2) == WeatherSky.Kind.PARTLY, "2 parçalı");
        check(WeatherSky.kind(3) == WeatherSky.Kind.CLOUDY, "3 kapalı (ekrandaki Kapalı)");
        check(WeatherSky.kind(45) == WeatherSky.Kind.FOG, "45 sis");
        check(WeatherSky.kind(53) == WeatherSky.Kind.DRIZZLE, "53 çisenti");
        check(WeatherSky.kind(63) == WeatherSky.Kind.RAIN && WeatherSky.kind(81) == WeatherSky.Kind.RAIN, "yağmur ve sağanak");
        check(WeatherSky.kind(73) == WeatherSky.Kind.SNOW && WeatherSky.kind(85) == WeatherSky.Kind.SNOW, "kar");
        check(WeatherSky.kind(95) == WeatherSky.Kind.STORM, "fırtına");
        check(WeatherSky.kind(-1) == WeatherSky.Kind.NONE && WeatherSky.kind(200) == WeatherSky.Kind.NONE, "bilinmeyen kod → animasyon yok");
        check(WeatherSky.particles(WeatherSky.Kind.RAIN, 65) > WeatherSky.particles(WeatherSky.Kind.RAIN, 61), "şiddetli yağmurda daha çok damla");
        check(WeatherSky.particles(WeatherSky.Kind.CLEAR, 0) == 0, "açıkta damla yok");
        check(WeatherSky.slant(40) > WeatherSky.slant(5) && WeatherSky.slant(500) <= 0.45f, "rüzgârla eğim, sınırlı");
        check(WeatherSky.slant(Double.NaN) > 0, "rüzgâr bilinmezse hafif eğim");
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " hava animasyonu");
        if (bad > 0) System.exit(1);
    }
}
