package com.zeytindefteri.app;

/**
 * Hava kodundan (Open-Meteo / WMO) kartın arka plan animasyonunun türü. Saf sınıf; test edilir.
 * 0 açık · 1-2 parçalı bulutlu · 3 kapalı · 45/48 sis · 51-57 çisenti · 61-67, 80-82 yağmur
 * 71-77, 85-86 kar · 95-99 gök gürültülü fırtına.
 */
final class WeatherSky {
    enum Kind { NONE, CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

    private WeatherSky() {}

    static Kind kind(int code) {
        if (code == 0) return Kind.CLEAR;
        if (code == 1 || code == 2) return Kind.PARTLY;
        if (code == 3) return Kind.CLOUDY;
        if (code == 45 || code == 48) return Kind.FOG;
        if (code >= 51 && code <= 57) return Kind.DRIZZLE;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return Kind.RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return Kind.SNOW;
        if (code >= 95 && code <= 99) return Kind.STORM;
        return Kind.NONE;
    }

    /** Yağmur/kar tanesi sayısı (kart genişliği 360dp için); şiddetli kodlarda daha çok. */
    static int particles(Kind kind, int code) {
        switch (kind) {
            case DRIZZLE: return 26;
            case RAIN: return code == 65 || code == 67 || code == 82 ? 70 : code == 61 || code == 80 ? 34 : 50;
            case STORM: return 70;
            case SNOW: return code == 75 || code == 86 ? 60 : 36;
            default: return 0;
        }
    }

    /** Rüzgârla yağmurun eğimi (x / y oranı): sakin havada hafif, 40 km/s'de belirgin. */
    static float slant(double windKmh) {
        if (Double.isNaN(windKmh) || windKmh < 0) return 0.12f;
        return (float) Math.min(0.45, 0.08 + windKmh / 100.0);
    }

    /** Ekran okuyucu için kısa açıklama. */
    static String describe(Kind kind) {
        switch (kind) {
            case CLEAR: return "açık hava";
            case PARTLY: return "parçalı bulutlu";
            case CLOUDY: return "kapalı";
            case FOG: return "sisli";
            case DRIZZLE: return "çiseliyor";
            case RAIN: return "yağmurlu";
            case SNOW: return "karlı";
            case STORM: return "gök gürültülü fırtına";
            default: return "";
        }
    }
}
