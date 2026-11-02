package com.zeytindefteri.app;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Namaz vakitleri, bahçenin konumundan telefonda hesaplanır (internet ve ücretli servis yok).
 * Diyanet yöntemi: imsak 18°, yatsı 17° güneş açısı, ikindi gölge = 1 boy; Diyanet temkinleri
 * (güneş −7, öğle +5, ikindi +4, akşam +7 dk) ve dakikaya yukarı yuvarlama.
 * Alaşehir için yayınlanan Diyanet takvimiyle karşılaştırıldı: vakitler aynı ya da en fazla 1 dk farklı.
 * Saf sınıf (Android yok): test edilir.
 */
final class PrayerTimes {
    static final String[] NAMES = {"İmsak", "Güneş", "Öğle", "İkindi", "Akşam", "Yatsı"};
    /** "İmsak'a", "Öğle'ye" … (geri sayım cümlesi için yönelme eki). */
    static final String[] TO = {"İmsak'a", "Güneş'e", "Öğle'ye", "İkindi'ye", "Akşam'a", "Yatsı'ya"};
    private static final int[] TEMKIN = {0, -7, 5, 4, 7, 0};

    private PrayerTimes() {}

    /** Günün altı vakti, gece yarısından dakika olarak (yerel saat). Geçersiz konumda null. */
    static int[] day(int year, int month1, int dayOfMonth, double lat, double lon, double tzHours) {
        if (Double.isNaN(lat) || Double.isNaN(lon) || Math.abs(lat) > 64 || Math.abs(lon) > 180) return null;
        double jd = julian(year, month1, dayOfMonth) - lon / (15 * 24.0);
        double[] guess = {5, 6, 12, 13, 18, 18};
        double[] t = new double[6];
        for (int pass = 0; pass < 2; pass++) {
            t[0] = angleTime(jd, guess[0] / 24, 18, lat, true);
            t[1] = angleTime(jd, guess[1] / 24, 0.833, lat, true);
            t[2] = midDay(jd, guess[2] / 24);
            t[3] = asr(jd, guess[3] / 24, lat);
            t[4] = angleTime(jd, guess[4] / 24, 0.833, lat, false);
            t[5] = angleTime(jd, guess[5] / 24, 17, lat, false);
            for (int i = 0; i < 6; i++) if (Double.isNaN(t[i])) return null;
            System.arraycopy(t, 0, guess, 0, 6);
        }
        int[] out = new int[6];
        double shift = tzHours - lon / 15;
        for (int i = 0; i < 6; i++) {
            double minutes = (t[i] + shift) * 60 + TEMKIN[i];
            out[i] = (int) Math.ceil(minutes - 1e-6);
            out[i] = ((out[i] % 1440) + 1440) % 1440;
        }
        return out;
    }

    /** Takvim günü için saat dilimi farkı (yaz saati dahil) saat cinsinden. */
    static double tzHours(TimeZone zone, int year, int month1, int dayOfMonth) {
        Calendar c = Calendar.getInstance(zone);
        c.clear();
        c.set(year, month1 - 1, dayOfMonth, 12, 0);
        return zone.getOffset(c.getTimeInMillis()) / 3600000.0;
    }

    /** Şu anki durum: hangi vakit içindeyiz, sıradaki ne, kaç dakika kaldı, çubuğun doluluğu. */
    static final class Now {
        int current;        // 0..5; imsaktan önceyse 5 (dünkü yatsı)
        int next;           // 0..5; yatsıdan sonraysa 0 (yarınki imsak)
        int minutesLeft;
        float fraction;     // içinde bulunulan vaktin geçen kısmı (0..1)
    }

    /** @param today bugünün vakitleri  @param tomorrowImsak yarının imsakı (dk) @param yesterdayIsha dünün yatsısı (dk) */
    static Now now(int[] today, int tomorrowImsak, int yesterdayIsha, int nowMinutes) {
        Now n = new Now();
        int start, end;
        if (nowMinutes < today[0]) {
            n.current = 5; n.next = 0; start = yesterdayIsha - 1440; end = today[0];
        } else if (nowMinutes >= today[5]) {
            n.current = 5; n.next = 0; start = today[5]; end = tomorrowImsak + 1440;
        } else {
            int i = 0;
            while (i < 5 && nowMinutes >= today[i + 1]) i++;
            n.current = i; n.next = i + 1; start = today[i]; end = today[i + 1];
        }
        n.minutesLeft = Math.max(0, end - nowMinutes);
        n.fraction = end > start ? Math.max(0f, Math.min(1f, (nowMinutes - start) / (float) (end - start))) : 0f;
        return n;
    }

    static String clock(int minutes) {
        int m = ((minutes % 1440) + 1440) % 1440;
        return (m / 60 < 10 ? "0" : "") + m / 60 + ":" + (m % 60 < 10 ? "0" : "") + m % 60;
    }

    /** "İmsak'a 8 sa 23 dk", "Öğle'ye 45 dk", "Akşam'a 1 dk". */
    static String countdown(Now n) {
        int h = n.minutesLeft / 60, m = n.minutesLeft % 60;
        String left = h > 0 ? h + " sa" + (m > 0 ? " " + m + " dk" : "") : Math.max(1, m) + " dk";
        return TO[n.next] + " " + left;
    }

    // ---------------------------------------------------------------- güneş konumu

    private static double julian(int y, int m, int d) {
        if (m <= 2) { y -= 1; m += 12; }
        int a = y / 100, b = 2 - a + a / 4;
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524.5;
    }

    /** {sapma (derece), zaman denklemi (saat)} */
    private static double[] sun(double jd) {
        double d = jd - 2451545.0;
        double g = norm(357.529 + 0.98560028 * d, 360), q = norm(280.459 + 0.98564736 * d, 360);
        double l = norm(q + 1.915 * sin(g) + 0.020 * sin(2 * g), 360);
        double e = 23.439 - 0.00000036 * d;
        double ra = norm(Math.toDegrees(Math.atan2(cos(e) * sin(l), cos(l))) / 15, 24);
        double decl = Math.toDegrees(Math.asin(sin(e) * sin(l)));
        double eqt = q / 15 - ra;
        eqt = norm(eqt + 12, 24) - 12;
        return new double[]{decl, eqt};
    }

    private static double midDay(double jd, double t) { return norm(12 - sun(jd + t)[1], 24); }

    private static double angleTime(double jd, double t, double angle, double lat, boolean beforeNoon) {
        double decl = sun(jd + t)[0];
        double v = (-sin(angle) - sin(decl) * sin(lat)) / (cos(decl) * cos(lat));
        if (v < -1 || v > 1) return Double.NaN;          // kutup yakını: güneş bu açıya inmiyor
        double h = Math.toDegrees(Math.acos(v)) / 15;
        return midDay(jd, t) + (beforeNoon ? -h : h);
    }

    private static double asr(double jd, double t, double lat) {
        double decl = sun(jd + t)[0];
        double angle = -Math.toDegrees(Math.atan(1 / (1 + Math.tan(Math.toRadians(Math.abs(lat - decl))))));
        return angleTime(jd, t, angle, lat, false);
    }

    private static double sin(double deg) { return Math.sin(Math.toRadians(deg)); }
    private static double cos(double deg) { return Math.cos(Math.toRadians(deg)); }
    private static double norm(double v, double m) { v %= m; return v < 0 ? v + m : v; }
}
