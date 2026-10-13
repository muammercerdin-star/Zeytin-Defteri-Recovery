package com.zeytindefteri.app;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bahçe konumuna göre Open-Meteo üzerinden hava verisi getirir.
 *
 * Öncelik:
 *  1) Daha önce aynı bahçe/lokasyon için çözümlenmiş koordinat cache'i
 *  2) Mahalle/Köy + il ile Open-Meteo Geocoding
 *  3) İlçe + il
 *  4) İl
 *
 * Telefonun anlık konumu istenmez; hava, aktif bahçenin kayıtlı konumuna göredir.
 */
public class WeatherRepository {

    public interface Callback {
        void onSuccess(WeatherSnapshot weather);
        void onError(String message);
    }

    public interface ForecastCallback {
        void onSuccess(WeatherForecast forecast);
        void onError(String message);
    }

    public static class WeatherSnapshot {
        public double latitude;
        public double longitude;
        public double temperatureC;
        public double apparentTemperatureC = Double.NaN;
        public double windKmh;
        public double humidity = Double.NaN;
        public double precipitationProbability = Double.NaN;
        public int weatherCode;
        public boolean isDay;
        public String condition = "";
        public String icon = "☀";
        public String resolvedName = "";
    }

    public static class HourlyForecast {
        public String timeIso = "";
        public String hourLabel = "";
        public double temperatureC = Double.NaN;
        public double apparentTemperatureC = Double.NaN;
        public double windKmh = Double.NaN;
        public double humidity = Double.NaN;
        public double precipitationProbability = Double.NaN;
        public int weatherCode = -1;
        public boolean isDay = true;
        public String condition = "";
        public String icon = "☀";
    }

    public static class DailyForecast {
        public String dateIso = "";
        public double maxTemperatureC = Double.NaN;
        public double minTemperatureC = Double.NaN;
        public double precipitationProbability = Double.NaN;
        public int weatherCode = -1;
        public String condition = "";
        public String icon = "☀";
    }

    public static class WeatherForecast {
        public WeatherSnapshot current;
        public final List<HourlyForecast> hourly = new ArrayList<>();
        public final List<DailyForecast> daily = new ArrayList<>();
    }

    private final DatabaseHelper db;

    public WeatherRepository(DatabaseHelper db) {
        this.db = db;
    }

    public void loadForGarden(
            final long gardenId,
            final String province,
            final String district,
            final String neighborhood,
            final Callback callback
    ) {
        if (callback == null) return;
        final String p = safe(province);
        final String d = safe(district);
        final String n = safe(neighborhood);
        if (p.isEmpty() && d.isEmpty() && n.isEmpty()) {
            callback.onError("Bahçe konumu kayıtlı değil");
            return;
        }

        new Thread(() -> {
            try {
                GeoPoint geo = resolveGardenGeo(gardenId, p, d, n);
                if (geo == null) {
                    postError(callback, "Bahçe konumu hava servisiyle eşleştirilemedi");
                    return;
                }
                WeatherSnapshot weather = fetchCurrent(geo);
                if (weather == null) {
                    postError(callback, "Hava verisi alınamadı");
                    return;
                }
                postSuccess(callback, weather);
            } catch (Exception e) {
                postError(callback, "Hava verisi alınamadı");
            }
        }, "GardenWeather").start();
    }

    /** V5: Aktif bahçe için 24 saatlik film şeridi + 5 günlük tahmin. */
    public void loadForecastForGarden(
            final long gardenId,
            final String province,
            final String district,
            final String neighborhood,
            final ForecastCallback callback
    ) {
        if (callback == null) return;
        final String p = safe(province);
        final String d = safe(district);
        final String n = safe(neighborhood);
        if (p.isEmpty() && d.isEmpty() && n.isEmpty()) {
            callback.onError("Bahçe konumu kayıtlı değil");
            return;
        }

        new Thread(() -> {
            try {
                GeoPoint geo = resolveGardenGeo(gardenId, p, d, n);
                if (geo == null) {
                    callback.onError("Bahçe konumu hava servisiyle eşleştirilemedi");
                    return;
                }
                WeatherForecast forecast = fetchForecast(geo);
                if (forecast == null) {
                    callback.onError("Hava tahmini alınamadı");
                    return;
                }
                callback.onSuccess(forecast);
            } catch (Exception e) {
                callback.onError("Hava tahmini alınamadı");
            }
        }, "GardenForecast").start();
    }

    private GeoPoint resolveGardenGeo(long gardenId, String province, String district, String neighborhood) throws Exception {
        GeoPoint geo = getCachedGeo(gardenId, province, district, neighborhood);
        if (geo == null) {
            geo = resolveGeo(province, district, neighborhood);
            if (geo != null) saveGeoCache(gardenId, province, district, neighborhood, geo);
        }
        return geo;
    }

    private GeoPoint getCachedGeo(long gardenId, String province, String district, String neighborhood) {
        if (gardenId <= 0) return null;
        String prefix = "weather_geo:" + gardenId + ":";
        String signature = locationSignature(province, district, neighborhood);
        String cachedSignature = db.getSetting(prefix + "signature", "");
        if (!signature.equals(cachedSignature)) return null;

        String latText = db.getSetting(prefix + "lat", "");
        String lonText = db.getSetting(prefix + "lon", "");
        if (TextUtils.isEmpty(latText) || TextUtils.isEmpty(lonText)) return null;
        try {
            GeoPoint g = new GeoPoint();
            g.latitude = Double.parseDouble(latText);
            g.longitude = Double.parseDouble(lonText);
            g.name = db.getSetting(prefix + "name", "");
            return g;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void saveGeoCache(long gardenId, String province, String district, String neighborhood, GeoPoint geo) {
        if (gardenId <= 0 || geo == null) return;
        String prefix = "weather_geo:" + gardenId + ":";
        db.setSetting(prefix + "signature", locationSignature(province, district, neighborhood));
        db.setSetting(prefix + "lat", String.valueOf(geo.latitude));
        db.setSetting(prefix + "lon", String.valueOf(geo.longitude));
        db.setSetting(prefix + "name", safe(geo.name));
    }

    private GeoPoint resolveGeo(String province, String district, String neighborhood) throws Exception {
        if (!neighborhood.isEmpty()) {
            GeoPoint g = geocode(neighborhood, province);
            if (g != null) return g;
        }
        if (!district.isEmpty()) {
            GeoPoint g = geocode(district, province);
            if (g != null) return g;
        }
        if (!province.isEmpty()) {
            GeoPoint g = geocode(province, "");
            if (g != null) return g;
        }
        return null;
    }

    private GeoPoint geocode(String name, String admin1) throws Exception {
        String query = safe(name);
        if (query.isEmpty()) return null;
        if (!safe(admin1).isEmpty() && !query.equalsIgnoreCase(admin1)) query = query + ", " + admin1;

        String url = "https://geocoding-api.open-meteo.com/v1/search"
                + "?name=" + URLEncoder.encode(query, "UTF-8")
                + "&count=10&language=tr&format=json&countryCode=TR";
        JSONObject root = requestJson(url);
        JSONArray results = root.optJSONArray("results");
        if (results == null || results.length() == 0) return null;
        JSONObject best = results.optJSONObject(0);
        if (best == null) return null;

        GeoPoint geo = new GeoPoint();
        geo.latitude = best.optDouble("latitude", Double.NaN);
        geo.longitude = best.optDouble("longitude", Double.NaN);
        geo.name = best.optString("name", name);
        if (Double.isNaN(geo.latitude) || Double.isNaN(geo.longitude)) return null;
        return geo;
    }

    private WeatherSnapshot fetchCurrent(GeoPoint geo) throws Exception {
        String url = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.6f&longitude=%.6f"
                        + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m"
                        + "&temperature_unit=celsius&wind_speed_unit=kmh&timezone=auto",
                geo.latitude, geo.longitude
        );
        JSONObject root = requestJson(url);
        return parseCurrent(root, geo);
    }

    private WeatherForecast fetchForecast(GeoPoint geo) throws Exception {
        String url = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.6f&longitude=%.6f"
                        + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m"
                        + "&hourly=temperature_2m,apparent_temperature,relative_humidity_2m,precipitation_probability,weather_code,is_day,wind_speed_10m"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                        + "&forecast_days=5&temperature_unit=celsius&wind_speed_unit=kmh&timezone=auto",
                geo.latitude, geo.longitude
        );
        JSONObject root = requestJson(url);
        WeatherForecast out = new WeatherForecast();
        out.current = parseCurrent(root, geo);
        if (out.current == null) return null;

        JSONObject hourly = root.optJSONObject("hourly");
        if (hourly != null) {
            JSONArray times = hourly.optJSONArray("time");
            JSONArray temps = hourly.optJSONArray("temperature_2m");
            JSONArray apparent = hourly.optJSONArray("apparent_temperature");
            JSONArray humidity = hourly.optJSONArray("relative_humidity_2m");
            JSONArray rain = hourly.optJSONArray("precipitation_probability");
            JSONArray codes = hourly.optJSONArray("weather_code");
            JSONArray isDay = hourly.optJSONArray("is_day");
            JSONArray wind = hourly.optJSONArray("wind_speed_10m");
            String currentTime = root.optJSONObject("current") == null ? "" : root.optJSONObject("current").optString("time", "");
            String currentHour = currentTime.length() >= 13 ? currentTime.substring(0, 13) : currentTime;
            int start = 0;
            if (times != null && !currentHour.isEmpty()) {
                for (int i = 0; i < times.length(); i++) {
                    String t = times.optString(i, "");
                    if (t.length() >= 13 && t.substring(0, 13).compareTo(currentHour) >= 0) { start = i; break; }
                }
            }
            int count = times == null ? 0 : Math.min(times.length(), start + 24);
            for (int i = start; i < count; i++) {
                HourlyForecast h = new HourlyForecast();
                h.timeIso = times.optString(i, "");
                h.hourLabel = hourLabel(h.timeIso);
                h.temperatureC = optDouble(temps, i);
                h.apparentTemperatureC = optDouble(apparent, i);
                h.humidity = optDouble(humidity, i);
                h.precipitationProbability = optDouble(rain, i);
                h.windKmh = optDouble(wind, i);
                h.weatherCode = optInt(codes, i, -1);
                h.isDay = optInt(isDay, i, 1) == 1;
                h.condition = conditionText(h.weatherCode);
                h.icon = conditionIcon(h.weatherCode, h.isDay);
                out.hourly.add(h);
            }
        }

        JSONObject daily = root.optJSONObject("daily");
        if (daily != null) {
            JSONArray dates = daily.optJSONArray("time");
            JSONArray max = daily.optJSONArray("temperature_2m_max");
            JSONArray min = daily.optJSONArray("temperature_2m_min");
            JSONArray rain = daily.optJSONArray("precipitation_probability_max");
            JSONArray codes = daily.optJSONArray("weather_code");
            int count = dates == null ? 0 : Math.min(5, dates.length());
            for (int i = 0; i < count; i++) {
                DailyForecast d = new DailyForecast();
                d.dateIso = dates.optString(i, "");
                d.maxTemperatureC = optDouble(max, i);
                d.minTemperatureC = optDouble(min, i);
                d.precipitationProbability = optDouble(rain, i);
                d.weatherCode = optInt(codes, i, -1);
                d.condition = conditionText(d.weatherCode);
                d.icon = conditionIcon(d.weatherCode, true);
                out.daily.add(d);
            }
        }
        return out;
    }

    private WeatherSnapshot parseCurrent(JSONObject root, GeoPoint geo) {
        JSONObject current = root.optJSONObject("current");
        if (current == null) return null;
        WeatherSnapshot out = new WeatherSnapshot();
        out.latitude = geo.latitude;
        out.longitude = geo.longitude;
        out.resolvedName = safe(geo.name);
        out.temperatureC = current.optDouble("temperature_2m", Double.NaN);
        out.apparentTemperatureC = current.optDouble("apparent_temperature", Double.NaN);
        out.humidity = current.optDouble("relative_humidity_2m", Double.NaN);
        out.windKmh = current.optDouble("wind_speed_10m", Double.NaN);
        out.weatherCode = current.optInt("weather_code", -1);
        out.isDay = current.optInt("is_day", 1) == 1;
        out.condition = conditionText(out.weatherCode);
        out.icon = conditionIcon(out.weatherCode, out.isDay);
        if (Double.isNaN(out.temperatureC)) return null;
        return out;
    }

    private static double optDouble(JSONArray a, int index) {
        if (a == null || index < 0 || index >= a.length() || a.isNull(index)) return Double.NaN;
        return a.optDouble(index, Double.NaN);
    }

    private static int optInt(JSONArray a, int index, int fallback) {
        if (a == null || index < 0 || index >= a.length() || a.isNull(index)) return fallback;
        return a.optInt(index, fallback);
    }

    private static String hourLabel(String iso) {
        if (iso != null && iso.length() >= 16) return iso.substring(11, 16);
        return iso == null ? "" : iso;
    }

    private JSONObject requestJson(String urlText) throws Exception {
        HttpURLConnection connection = null;
        InputStream input = null;
        BufferedReader reader = null;
        try {
            connection = (HttpURLConnection) new URL(urlText).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "ZeytinDefteri/1.0");
            int code = connection.getResponseCode();
            input = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (input == null) throw new IllegalStateException("HTTP " + code);
            reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            return new JSONObject(sb.toString());
        } finally {
            try { if (reader != null) reader.close(); } catch (Exception ignored) { }
            try { if (input != null) input.close(); } catch (Exception ignored) { }
            if (connection != null) connection.disconnect();
        }
    }

    private void postSuccess(Callback callback, WeatherSnapshot weather) { callback.onSuccess(weather); }
    private void postError(Callback callback, String message) { callback.onError(message); }

    private static String conditionText(int code) {
        if (code == 0) return "Açık";
        if (code == 1) return "Çoğunlukla açık";
        if (code == 2) return "Parçalı bulutlu";
        if (code == 3) return "Kapalı";
        if (code == 45 || code == 48) return "Sisli";
        if (code == 51 || code == 53 || code == 55 || code == 56 || code == 57) return "Çisenti";
        if (code == 61 || code == 63 || code == 65 || code == 66 || code == 67) return "Yağmurlu";
        if (code == 71 || code == 73 || code == 75 || code == 77) return "Karlı";
        if (code == 80 || code == 81 || code == 82) return "Sağanak";
        if (code == 85 || code == 86) return "Kar sağanağı";
        if (code == 95 || code == 96 || code == 99) return "Gök gürültülü";
        return "Hava durumu";
    }

    private static String conditionIcon(int code, boolean isDay) {
        if (code == 0) return isDay ? "☀" : "☾";
        if (code == 1 || code == 2) return "⛅";
        if (code == 3) return "☁";
        if (code == 45 || code == 48) return "🌫";
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return "🌧";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "❄";
        if (code >= 95) return "⛈";
        return isDay ? "☀" : "☾";
    }

    private static String locationSignature(String province, String district, String neighborhood) {
        return safe(province).toLowerCase(new Locale("tr", "TR")) + "|"
                + safe(district).toLowerCase(new Locale("tr", "TR")) + "|"
                + safe(neighborhood).toLowerCase(new Locale("tr", "TR"));
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }

    private static class GeoPoint {
        double latitude;
        double longitude;
        String name = "";
    }
}
