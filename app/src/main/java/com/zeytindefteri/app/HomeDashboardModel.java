package com.zeytindefteri.app;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Ana sayfa (Saha Defteri) için saf hesaplamalar. Android, ağ veya veritabanı kullanmaz.
 *
 * Hava satırları GardenTodayAdvisor ile aynı dikkat eşiklerini kullanır ve karar vermez:
 * "ilaçlama uygun / uygun değil" gibi tarımsal sonuçlar üretmez, yalnızca koşulu gösterir.
 */
public final class HomeDashboardModel {
    private HomeDashboardModel() {}

    // ------------------------------------------------------------------
    // Genel zeytin takvimi (bölgeye göre değişir; yalnızca yön gösterir)
    // ------------------------------------------------------------------
    public static final String[] PHASE_NAMES = {"Budama", "Çiçek", "Tutum", "Çekirdek", "Yağlanma", "Hasat"};
    public static final String[] PHASE_MONTHS = {"Oca–Mar", "Nis–May", "Haz", "Tem–Ağu", "Eyl", "Eki–Ara"};

    /** @param month Calendar.MONTH değeri (0 = Ocak). */
    public static int seasonPhase(int month) {
        if (month <= 2) return 0;
        if (month <= 4) return 1;
        if (month == 5) return 2;
        if (month <= 7) return 3;
        if (month == 8) return 4;
        return 5;
    }

    public static String phaseTitle(int phase) {
        switch (phase) {
            case 0: return "Budama dönemi";
            case 1: return "Çiçeklenme dönemi";
            case 2: return "Meyve tutumu";
            case 3: return "Çekirdek sertleşmesi";
            case 4: return "Yağlanma dönemi";
            default: return "Hasat dönemi";
        }
    }

    public static String lastHarvestText(long days) {
        if (days < 0) return "Bu sezon hasat kaydı yok";
        if (days == 0) return "Son hasat bugün";
        return "Son hasat " + days + " gün önce";
    }

    // ------------------------------------------------------------------
    // Selamlama
    // ------------------------------------------------------------------
    /** Görünen adın ilk kelimesi; ilk kelime yalnızca baş harfse ("M.") tüm adı kullanır. */
    public static String displayFirstName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "Üretici";
        String trimmed = fullName.trim().replaceAll("\\s+", " ");
        String first = trimmed.split(" ")[0];
        boolean initial = first.endsWith(".") || first.codePointCount(0, first.length()) <= 1;
        if (!initial) return first;
        return trimmed.contains(" ") ? trimmed : (first.endsWith(".") ? first.substring(0, first.length() - 1) : first);
    }

    public static String initials(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "Ü";
        StringBuilder out = new StringBuilder();
        for (String part : fullName.trim().split("\\s+")) {
            String letters = part.replaceAll("[^\\p{L}]", "");
            if (letters.isEmpty()) continue;
            out.append(letters.substring(0, 1).toUpperCase(new Locale("tr", "TR")));
            if (out.length() == 2) break;
        }
        return out.length() == 0 ? "Ü" : out.toString();
    }

    private static final String[] DAY_NAMES = {"Pazar", "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi"};
    private static final String[] DAY_SHORT = {"Paz", "Pzt", "Sal", "Çar", "Per", "Cum", "Cmt"};
    private static final String[] MONTH_NAMES = {"Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
            "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"};

    /** "Salı · 13 Ekim" */
    public static String dateLine(Calendar now) {
        return DAY_NAMES[now.get(Calendar.DAY_OF_WEEK) - 1] + " · " + now.get(Calendar.DAY_OF_MONTH)
                + " " + MONTH_NAMES[now.get(Calendar.MONTH)];
    }

    // ------------------------------------------------------------------
    // Tarih etiketleri: "2026-10-14T07:00" -> "Yarın 07:00"
    // ------------------------------------------------------------------
    public static String whenLabel(String iso, Calendar now) {
        if (iso == null || iso.trim().isEmpty()) return "";
        String value = iso.trim().replace(' ', 'T');
        String datePart = value.length() >= 10 ? value.substring(0, 10) : value;
        String timePart = value.length() >= 16 && value.charAt(10) == 'T' ? value.substring(11, 16) : "";
        Long day = parseDay(datePart);
        if (day == null) return iso;
        long today = startOfDay(now);
        long diff = Math.round((day - today) / 86400000.0);
        String name;
        if (diff == 0) name = "Bugün";
        else if (diff == 1) name = "Yarın";
        else if (diff > 1 && diff < 7) {
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            c.setTimeInMillis(day);
            name = DAY_NAMES[c.get(Calendar.DAY_OF_WEEK) - 1];
        } else name = datePart;
        return timePart.isEmpty() ? name : name + " " + timePart;
    }

    /** Kısa gün adı: bugün için "Bugün", diğerleri "Çar". */
    public static String shortDay(String isoDate, Calendar now) {
        Long day = parseDay(isoDate == null ? "" : isoDate.trim());
        if (day == null) return "";
        long diff = Math.round((day - startOfDay(now)) / 86400000.0);
        if (diff == 0) return "Bugün";
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(day);
        return DAY_SHORT[c.get(Calendar.DAY_OF_WEEK) - 1];
    }

    private static long startOfDay(Calendar now) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        return c.getTimeInMillis();
    }

    private static Long parseDay(String value) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setLenient(false);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        ParsePosition p = new ParsePosition(0);
        Date d = f.parse(value, p);
        return d != null && p.getIndex() == value.length() ? d.getTime() : null;
    }

    // ------------------------------------------------------------------
    // Hava koşulu satırları
    // ------------------------------------------------------------------
    public enum Tone { NORMAL, ATTENTION, CRITICAL, INFO }

    public static final class Hour {
        public final String iso;
        public final double rain, wind, temperature;
        public Hour(String iso, double rain, double wind, double temperature) {
            this.iso = iso; this.rain = rain; this.wind = wind; this.temperature = temperature;
        }
    }

    public static final class Day {
        public final String iso;
        public final double rain, max, min;
        public Day(String iso, double rain, double max, double min) {
            this.iso = iso; this.rain = rain; this.max = max; this.min = min;
        }
    }

    public static final class Condition {
        public final String title, value, note;
        public final Tone tone;
        Condition(String title, String value, String note, Tone tone) {
            this.title = title; this.value = value; this.note = note; this.tone = tone;
        }
    }

    /**
     * En fazla üç satır: yağış, rüzgâr ve (sıcaklık uyarısı varsa o, yoksa) en kuru gün.
     * hours: şimdiden sonraki saatlik tahmin (WeatherRepository 24 saat verir; en fazla 48 saat okunur).
     */
    public static List<Condition> conditions(double currentWind, List<Hour> hours, List<Day> days, Calendar now) {
        List<Condition> out = new ArrayList<>();
        int limit = Math.min(hours.size(), 48);

        Hour wet = null;
        double maxRain = Double.NaN;
        for (int i = 0; i < limit; i++) {
            Hour h = hours.get(i);
            if (!valid(h.rain)) continue;
            if (Double.isNaN(maxRain) || h.rain > maxRain) maxRain = h.rain;
            if (wet == null && h.rain >= GardenTodayAdvisor.RAIN_PERCENT && h.rain <= 100) wet = h;
        }
        if (wet != null) {
            out.add(new Condition("Yağış", whenLabel(wet.iso, now) + " · %" + round(wet.rain),
                    "Yağış ihtimali yüksek. Sulama ve ilaçlama öncesi koşulları kontrol et.", Tone.ATTENTION));
        } else if (valid(maxRain)) {
            out.add(new Condition("Yağış", (limit >= 24 ? "24 saatte" : limit + " saatte") + " en fazla %" + round(maxRain),
                    "Belirgin yağış beklenmiyor.", Tone.NORMAL));
        }

        double maxWind = valid(currentWind) ? currentWind : Double.NaN;
        Hour windy = null;
        for (int i = 0; i < Math.min(limit, 24); i++) {
            Hour h = hours.get(i);
            if (!valid(h.wind)) continue;
            if (Double.isNaN(maxWind) || h.wind > maxWind) maxWind = h.wind;
            if (windy == null && h.wind >= GardenTodayAdvisor.WIND_KMH) windy = h;
        }
        if (windy != null) {
            boolean critical = windy.wind >= GardenTodayAdvisor.CRITICAL_WIND_KMH;
            out.add(new Condition("Rüzgâr", whenLabel(windy.iso, now) + " · " + round(windy.wind) + " km/s",
                    "Rüzgâr kuvvetli. İlaçlama öncesi koşulları kontrol et.", critical ? Tone.CRITICAL : Tone.ATTENTION));
        } else if (valid(currentWind)) {
            out.add(new Condition("Rüzgâr", "Şimdi " + round(currentWind) + " km/s",
                    "Dikkat eşiğinin (" + round(GardenTodayAdvisor.WIND_KMH) + " km/s) altında.", Tone.NORMAL));
        }

        Condition temperature = null;
        for (Day d : days) {
            if (valid(d.min) && d.min <= GardenTodayAdvisor.COLD_C) {
                temperature = new Condition("Soğuk", whenLabel(d.iso, now) + " · " + round(d.min) + "°",
                        "Soğuk hava. Bahçedeki koşulları kontrol et.",
                        d.min <= GardenTodayAdvisor.CRITICAL_COLD_C ? Tone.CRITICAL : Tone.ATTENTION);
                break;
            }
            if (valid(d.max) && d.max >= GardenTodayAdvisor.HOT_C) {
                temperature = new Condition("Sıcak", whenLabel(d.iso, now) + " · " + round(d.max) + "°",
                        "Yüksek sıcaklık. Bahçedeki koşulları kontrol et.",
                        d.max >= GardenTodayAdvisor.CRITICAL_HOT_C ? Tone.CRITICAL : Tone.ATTENTION);
                break;
            }
        }
        if (temperature != null) {
            out.add(temperature);
        } else {
            Day driest = null;
            for (Day d : days) {
                if (valid(d.rain) && (driest == null || d.rain < driest.rain)) driest = d;
            }
            if (driest != null && days.size() > 1) {
                out.add(new Condition("En kuru gün", whenLabel(driest.iso, now) + " · %" + round(driest.rain),
                        "Önümüzdeki günlerin en düşük yağış ihtimali.", Tone.INFO));
            }
        }
        return out;
    }

    /** Yağış çubuğu yüksekliği (0–maxPx), en az 3 px görünür kalır. */
    public static int rainBar(double percent, int maxPx) {
        if (!valid(percent)) return 3;
        double clamped = Math.max(0, Math.min(100, percent));
        return Math.max(3, (int) Math.round(maxPx * clamped / 100.0));
    }

    /** Öneri başlığındaki baştaki simgeyi atar: "🌧 Yağış olasılığı yüksek" -> "Yağış olasılığı yüksek". */
    public static String plainTitle(String title) {
        if (title == null) return "";
        int i = 0;
        while (i < title.length()) {
            int cp = title.codePointAt(i);
            if (Character.isLetterOrDigit(cp)) break;
            i += Character.charCount(cp);
        }
        return title.substring(i).trim();
    }

    public static String tagFor(String recommendationKey) {
        if ("history".equals(recommendationKey)) return "KAYIT";
        if ("empty".equals(recommendationKey)) return "BAŞLANGIÇ";
        return "HAVA";
    }

    private static boolean valid(double d) {
        return !Double.isNaN(d) && !Double.isInfinite(d);
    }

    private static String round(double d) {
        return String.valueOf(Math.round(d));
    }
}
