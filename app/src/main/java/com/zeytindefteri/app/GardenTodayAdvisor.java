package com.zeytindefteri.app;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.*;

/** Pure, offline V1 status rules. No Android, network, database writes or agronomic decisions. */
public final class GardenTodayAdvisor {
    // UI attention thresholds only: NOT application safety limits or crop damage thresholds.
    public static final double RAIN_PERCENT = 60, WIND_KMH = 25, HOT_C = 35, COLD_C = 0;
    public static final double CRITICAL_WIND_KMH = 60, CRITICAL_HOT_C = 42, CRITICAL_COLD_C = -5;
    // Record-review reminder only; never an irrigation/fertilizer/spray schedule.
    public static final int RECORD_REVIEW_DAYS = 30, MAX_CARDS = 3;
    public enum Level { CRITICAL, WARNING, INFO, NORMAL }
    public enum Action { WEATHER, HISTORY, ADD_GARDEN }
    public static final class Scope {
        public final String owner;
        public final int season;
        public final long garden;
        public Scope(String owner, int season, long garden) {
            this.owner = owner == null ? "" : owner; this.season = season; this.garden = garden;
        }
        public boolean matches(Scope other) {
            return other != null && owner.equals(other.owner) && season == other.season && garden == other.garden;
        }
    }
    public static final class Record {
        public final Scope scope;
        public final String type, date;
        public Record(Scope scope, String type, String date) { this.scope=scope; this.type=type; this.date=date; }
    }
    public static final class Weather {
        public final Scope scope;
        public final String when;
        public double max = Double.NaN, min = Double.NaN, wind = Double.NaN,
                rain = Double.NaN, humidity = Double.NaN;
        public Weather(Scope scope, String when) { this.scope=scope; this.when=when; }
    }
    public static final class Recommendation {
        public final String key, title, message;
        public final Level level;
        public final Action action;
        Recommendation(String key, String title, String message, Level level, Action action) {
            this.key=key; this.title=title; this.message=message; this.level=level; this.action=action;
        }
    }
    public List<Recommendation> evaluate(Scope scope, String today, List<Record> records,
                                         boolean historyAvailable, List<Weather> weather) {
        List<Recommendation> result = new ArrayList<>();
        if (scope.garden <= 0) {
            result.add(new Recommendation("empty", "🌱 Bahçeni ekle", "Günlük takip için bir bahçe kaydet.", Level.INFO, Action.ADD_GARDEN));
            return result;
        }
        Map<String, Recommendation> alerts = new LinkedHashMap<>();
        for (Weather w : weather) {
            if (!scope.matches(w.scope)) continue;
            if (valid(w.wind) && w.wind >= WIND_KMH)
                put(alerts, "wind", "💨 Rüzgâr kuvvetli", w.when + ": " + number(w.wind) + " km/sa. İlaçlama öncesi koşulları kontrol et.",
                        w.wind >= CRITICAL_WIND_KMH ? Level.CRITICAL : Level.WARNING);
            if (valid(w.max) && w.max >= HOT_C)
                put(alerts, "hot", "☀ Yüksek sıcaklık", w.when + ": " + number(w.max) + "°C. Bahçedeki koşulları kontrol et.",
                        w.max >= CRITICAL_HOT_C ? Level.CRITICAL : Level.WARNING);
            if (valid(w.min) && w.min <= COLD_C)
                put(alerts, "cold", "❄ Soğuk hava", w.when + ": " + number(w.min) + "°C. Bahçedeki koşulları kontrol et.",
                        w.min <= CRITICAL_COLD_C ? Level.CRITICAL : Level.WARNING);
            if (valid(w.rain) && w.rain >= RAIN_PERCENT && w.rain <= 100)
                put(alerts, "rain", "🌧 Yağış olasılığı yüksek", w.when + ": yağış ihtimali %" + number(w.rain) + ". Sulama planını kontrol et.", Level.WARNING);
        }
        result.addAll(alerts.values());
        if (!historyAvailable) {
            result.add(new Recommendation("history", "📋 İşlem geçmişi", "Yerel kayıtlar okunamadı. İşlem kayıtlarını yeniden açmayı deneyebilirsin.", Level.INFO, Action.HISTORY));
        } else {
            String[] types = {"Sulama", "Gübreleme", "İlaçlama", "Hasat"};
            StringBuilder message = new StringBuilder(scope.season + " sezonu");
            boolean stale = false, missing = false;
            Long now = day(today);
            for (String type : types) {
                Long latest = null;
                boolean unusable = false;
                for (Record r : records) {
                    if (!scope.matches(r.scope) || r.type == null || !canonicalType(r.type).equalsIgnoreCase(type)) continue;
                    Long date = day(r.date);
                    if (date == null || now == null || date > now) { unusable = true; continue; }
                    if (latest == null || date > latest) latest = date;
                }
                message.append("\n");
                if (latest != null && now != null) {
                    long days = (now - latest) / 86400000L;
                    message.append("Son ").append(type.toLowerCase(new Locale("tr", "TR"))).append(": ")
                            .append(days == 0 ? "bugün" : days + " gün önce").append(".");
                    stale |= days >= RECORD_REVIEW_DAYS;
                } else {
                    missing = true;
                    message.append(type).append(unusable ? " kaydının tarihini kontrol et." : " kaydı bulunmuyor.");
                }
            }
            if (stale) message.append("\nKayıtlarını ve bahçedeki durumu kontrol et.");
            result.add(new Recommendation("history", "📋 İşlem geçmişi", message.toString(), stale || missing ? Level.INFO : Level.NORMAL, Action.HISTORY));
        }
        // Show measured humidity as context only; do not infer plant water needs from air humidity.
        for (Weather w : weather) {
            if (scope.matches(w.scope) && "Şimdi".equals(w.when) && valid(w.max)) {
                String message = "Şimdi: " + number(w.max) + "°C";
                if (valid(w.wind)) message += " • Rüzgâr " + number(w.wind) + " km/sa";
                if (valid(w.humidity) && w.humidity >= 0 && w.humidity <= 100) message += " • Nem %" + number(w.humidity);
                result.add(new Recommendation("current", "🌤 Bahçenin havası", message, Level.NORMAL, Action.WEATHER));
                break;
            }
        }
        Collections.sort(result, (a,b) -> Integer.compare(a.level.ordinal(), b.level.ordinal()));
        return new ArrayList<>(result.subList(0, Math.min(MAX_CARDS, result.size())));
    }
    private static void put(Map<String, Recommendation> alerts, String key, String title, String message, Level level) {
        Recommendation old = alerts.get(key);
        if (old == null || level.ordinal() < old.level.ordinal())
            alerts.put(key, new Recommendation(key, title, message, level, Action.WEATHER));
    }
    private static String canonicalType(String type) {
        return "Gübre".equalsIgnoreCase(type.trim()) ? "Gübreleme" : type.trim();
    }
    private static boolean valid(double d) { return !Double.isNaN(d) && !Double.isInfinite(d); }
    private static String number(double d) { return String.valueOf(Math.round(d)); }
    private static Long day(String value) {
        if (value == null) return null;
        for (String pattern : new String[]{"yyyy-MM-dd", "dd.MM.yyyy", "dd/MM/yyyy"}) {
            SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
            f.setLenient(false); f.setTimeZone(TimeZone.getTimeZone("UTC"));
            ParsePosition p = new ParsePosition(0);
            Date d = f.parse(value.trim(), p);
            if (d != null && p.getIndex() == value.trim().length()) return d.getTime();
        }
        return null;
    }
}
