package com.zeytindefteri.app;

public final class PrayerTimesTest {
    static int n, bad;
    static void check(boolean ok, String m) { n++; if (!ok) { bad++; System.out.println("FAIL " + m); } }
    static int min(String s) { return Integer.parseInt(s.substring(0, 2)) * 60 + Integer.parseInt(s.substring(3)); }

    public static void main(String[] a) {
        // Alaşehir (Manisa) için yayınlanan Diyanet takvimi (milligazete.com.tr, Kasım 2026)
        String[][] ref = {
                {"10", "25", "05:56 07:18 12:55 15:54 18:23 19:39"},
                {"11", "2", "06:03 07:26 12:55 15:47 18:13 19:31"},
                {"11", "10", "06:11 07:35 12:55 15:41 18:05 19:24"},
                {"11", "16", "06:17 07:41 12:56 15:37 18:00 19:20"},
                {"11", "25", "06:25 07:51 12:58 15:33 17:55 19:16"}};
        int worst = 0;
        for (String[] r : ref) {
            int[] t = PrayerTimes.day(2026, Integer.parseInt(r[0]), Integer.parseInt(r[1]), 38.35, 28.52, 3);
            String[] exp = r[2].split(" ");
            for (int i = 0; i < 6; i++) worst = Math.max(worst, Math.abs(t[i] - min(exp[i])));
        }
        check(worst <= 1, "Diyanet takvimiyle en fazla 1 dk fark: " + worst);
        int[] today = PrayerTimes.day(2026, 11, 2, 38.35, 28.52, 3);
        int[] tomorrow = PrayerTimes.day(2026, 11, 3, 38.35, 28.52, 3);
        int[] yesterday = PrayerTimes.day(2026, 11, 1, 38.35, 28.52, 3);
        PrayerTimes.Now late = PrayerTimes.now(today, tomorrow[0], yesterday[5], min("21:40"));
        check(late.current == 5 && late.next == 0, "yatsıdan sonra sıradaki yarının imsakı");
        check(late.minutesLeft == 1440 - min("21:40") + tomorrow[0], "kalan süre gece yarısını geçer");
        check(PrayerTimes.countdown(late).startsWith("İmsak'a 8 sa"), PrayerTimes.countdown(late));
        PrayerTimes.Now noon = PrayerTimes.now(today, tomorrow[0], yesterday[5], today[2] - 45);
        check(noon.current == 1 && noon.next == 2 && PrayerTimes.countdown(noon).equals("Öğle'ye 45 dk"), PrayerTimes.countdown(noon));
        PrayerTimes.Now dawn = PrayerTimes.now(today, tomorrow[0], yesterday[5], min("03:00"));
        check(dawn.current == 5 && dawn.next == 0 && dawn.fraction > 0.5f && dawn.fraction < 1f, "gece yarısından sonra dünün yatsısı içindeyiz");
        check(PrayerTimes.clock(363).equals("06:03") && PrayerTimes.clock(-1).equals("23:59") && PrayerTimes.clock(1440).equals("00:00"), "saat biçimi");
        check(PrayerTimes.TO[3].equals("İkindi'ye") && PrayerTimes.TO[5].equals("Yatsı'ya"), "ekler");
        check(PrayerTimes.day(2026, 6, 21, 70, 25, 3) == null, "kutup yakınında hesap yok");
        check(PrayerTimes.day(2026, 6, 21, Double.NaN, 25, 3) == null, "konum yoksa hesap yok");
        int[] summer = PrayerTimes.day(2026, 6, 21, 38.35, 28.52, 3);
        check(summer != null && summer[0] < summer[1] && summer[1] < summer[2] && summer[4] < summer[5], "yaz günü sıralı");
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " namaz vakitleri");
        if (bad > 0) System.exit(1);
    }
}
