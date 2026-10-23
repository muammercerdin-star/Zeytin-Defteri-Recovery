package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Telefonun kendi ürettiği bildirimlerin kuralları (sunucu yok, ücretsiz). Android'e bağlı değil, test edilir.
 *
 * Her uyarının bir tekrar-engel anahtarı vardır (ör. "frost:12:2026-10-24"); bildirim kutusu aynı anahtarı
 * ikinci kez eklemez. Hava uyarısında bahçe başına günde en fazla bir (en önemli) uyarı seçilir.
 */
public final class AlertRules {
    private AlertRules() {}

    public static final String WEATHER = "weather", MONEY = "money", CALENDAR = "calendar", TEZGAH = "tezgah";

    /** Önem: 3 acil (don), 2 önemli (yağmur, fırtına), 1 bilgi (ilaçlama uygun, takvim). */
    public static final class Alert {
        public final String kind, key, title, body;
        public final int level;

        public Alert(String kind, String key, int level, String title, String body) {
            this.kind = kind; this.key = key; this.level = level; this.title = title; this.body = body;
        }

        @Override public String toString() { return key + " | " + title + " | " + body; }
    }

    /** Günlük tahmin: yyyy-MM-dd, en düşük/en yüksek °C, en yüksek yağış olasılığı %. */
    public static final class Day {
        public final String date;
        public final double min, max, rain;
        public Day(String date, double min, double max, double rain) { this.date = date; this.min = min; this.max = max; this.rain = rain; }
    }

    /** Saatlik tahmin: yyyy-MM-ddTHH:mm, rüzgâr km/sa, yağış olasılığı %. */
    public static final class Hour {
        public final String time;
        public final double wind, rain;
        public Hour(String time, double wind, double rain) { this.time = time; this.wind = wind; this.rain = rain; }
    }

    public static final double FROST_C = 2;
    public static final double HARVEST_RAIN = 60;
    public static final double STORM_KMH = 40;
    public static final double SPRAY_WIND = 10;
    public static final double SPRAY_RAIN = 20;

    // ------------------------------------------------------------------ hava

    /**
     * Bir bahçe için hava uyarısı (en fazla bir tane, en önemlisi). today: yyyy-MM-dd, nowHour: 0–23, month: 1–12.
     * days[0] bugün; hours bugünden itibaren saatlik.
     */
    public static Alert weather(long gardenId, String garden, String today, int nowHour, int month,
                                List<Day> days, List<Hour> hours) {
        List<Alert> all = new ArrayList<>();
        String where = garden == null || garden.trim().isEmpty() ? "Bahçende" : garden.trim() + "'de";
        // Don: günlük en düşük sıcaklık sabaha karşı ölçülür. days[1] = bu gece (yarın sabaha karşı), days[2] = yarın gece.
        for (int i = 1; i <= 2 && i < days.size(); i++) {
            Day d = days.get(i);
            if (!Double.isNaN(d.min) && d.min <= FROST_C) {
                String when = i == 1 ? "Bu gece (sabaha karşı)" : "Yarın gece";
                all.add(new Alert(WEATHER, "frost:" + gardenId + ":" + d.date, 3, "🥶 Don riski · " + garden(garden),
                        when + " " + where + " sıcaklık " + deg(d.min) + "'ye düşüyor. Genç ağaçları ve fidanları koru; "
                                + "toplanmış zeytini açıkta bırakma."));
                break;
            }
        }
        // Hasatta yağmur: yarın yağış olasılığı yüksek (hasat ayları).
        boolean harvest = month >= 10 || month <= 1;
        if (harvest && days.size() > 1) {
            Day t = days.get(1);
            if (!Double.isNaN(t.rain) && t.rain >= HARVEST_RAIN) {
                all.add(new Alert(WEATHER, "rain:" + gardenId + ":" + t.date, 2, "🌧️ Yarın yağmur · " + garden(garden),
                        "Yarın %" + Math.round(t.rain) + " yağış bekleniyor. Toplamayı bugüne al; toplanan zeytini çuvalda "
                                + "bekletme, kuru yerde sakla."));
            }
        }
        // Kuvvetli rüzgâr: önümüzdeki 24 saatte.
        Hour gust = null;
        for (int i = 0; i < Math.min(24, hours.size()); i++) {
            Hour h = hours.get(i);
            if (!Double.isNaN(h.wind) && h.wind >= STORM_KMH && (gust == null || h.wind > gust.wind)) gust = h;
        }
        if (gust != null) {
            all.add(new Alert(WEATHER, "storm:" + gardenId + ":" + gust.time.substring(0, Math.min(10, gust.time.length())), 2,
                    "💨 Kuvvetli rüzgâr · " + garden(garden),
                    hourLabel(gust.time, today) + " rüzgâr " + Math.round(gust.wind) + " km/sa'e çıkıyor. İlaçlama ve hasat "
                            + "örtüsü için uygun değil."));
        }
        // İlaçlama için uygun sabah: yarın 06–11 arası rüzgâr ≤ 10, yağış ≤ %20 (ilaçlama ayları). Haftada bir anahtar.
        boolean spraySeason = (month >= 3 && month <= 6) || month == 9;
        if (spraySeason && days.size() > 1) {
            String tomorrow = days.get(1).date;
            int ok = 0, seen = 0;
            for (Hour h : hours) {
                if (!h.time.startsWith(tomorrow)) continue;
                int hh = hour(h.time);
                if (hh < 6 || hh > 11) continue;
                seen++;
                if (!Double.isNaN(h.wind) && h.wind <= SPRAY_WIND && !Double.isNaN(h.rain) && h.rain <= SPRAY_RAIN) ok++;
            }
            if (seen >= 4 && ok == seen) {
                all.add(new Alert(WEATHER, "spray:" + gardenId + ":" + weekKey(tomorrow), 1, "🌿 İlaçlama için uygun · " + garden(garden),
                        "Yarın sabah rüzgâr sakin, yağış beklenmiyor. İlaçlama planladıysan iyi bir zaman."));
            }
        }
        Alert best = null;
        for (Alert a : all) if (best == null || a.level > best.level) best = a;
        return best;
    }

    // ------------------------------------------------------------------ takvim

    /** Ayın zeytin işi (her ay bir kez; anahtar ay). Mesaj yoksa null. */
    public static Alert calendar(int year, int month) {
        String title, body;
        switch (month) {
            case 1: title = "✂️ Budama dönemi"; body = "Ocak–Mart budama zamanı. Kuru ve hastalıklı dalları temizle, ağacın içine ışık girsin."; break;
            case 2: title = "🌱 Gübreleme hazırlığı"; body = "Kış gübrelemesi için toprağın tavını bekle; azotlu gübre bahar başında verilir."; break;
            case 3: title = "🌱 Bahar gübrelemesi"; body = "Mart, azotlu gübre ve bakır ilaçlaması (halkalı leke) için uygun dönem."; break;
            case 4: title = "🌸 Çiçeklenme yaklaşıyor"; body = "Çiçek öncesi bakır ilaçlaması ve sulama planını gözden geçir."; break;
            case 6: title = "💧 Sulama ve tutum"; body = "Meyve tutumu döneminde susuz bırakma; zeytin güvesini takip et."; break;
            case 7: title = "🪰 Zeytin sineği takibi"; body = "Tuzak kontrolüne başla; sinek yoğunluğu artarsa ilaçlamayı planla."; break;
            case 9: title = "🫒 Yağlanma dönemi"; body = "Zeytin sineği en tehlikeli dönemde. Hasat ekipmanını (sırık, branda, kasa) hazırla."; break;
            case 10: title = "🧺 Hasat başlıyor"; body = "Sofralık hasat dönemi. Elek ve satış kayıtlarını Zeytin Defteri'ne gir, Zeytin Ligi'nde bölgeni gör."; break;
            case 12: title = "📊 Sezonu kapat"; body = "Hasat bitiyor: sezon raporuna bak, alacaklarını topla, budama planını yap."; break;
            default: return null;
        }
        return new Alert(CALENDAR, "cal:" + year + "-" + (month < 10 ? "0" : "") + month, 1, title, body);
    }

    // ------------------------------------------------------------------ para

    /** Açık alacak; dayAge: kaç gündür bekliyor. 14 günü geçen alacak için haftada bir hatırlatma. */
    public static Alert receivable(long id, String party, double remaining, int dayAge, String today) {
        if (remaining < 1 || dayAge < 14) return null;
        String who = party == null || party.trim().isEmpty() ? "Bir alıcı" : party.trim();
        return new Alert(MONEY, "due:" + id + ":" + weekKey(today), 2, "💰 Alacak bekliyor · " + who,
                who + "'dan " + tl(remaining) + " alacağın " + dayAge + " gündür bekliyor.");
    }

    /**
     * Söz verilen günü olan alacak: gün gelene kadar sessiz (o gün "Hatırlat" alarmı çalar),
     * gün geçince haftada bir "gecikti" uyarısı. Gün yoksa eski 14 gün kuralı.
     */
    public static Alert receivable(long id, String party, double remaining, int dayAge, String due, String today) {
        if (!SalePayStatus.valid(SalePayStatus.iso(due))) return receivable(id, party, remaining, dayAge, today);
        int late = SalePayStatus.overdueDays(due, today);
        if (remaining < 1 || late <= 0) return null;
        String who = party == null || party.trim().isEmpty() ? "Bir alıcı" : party.trim();
        return new Alert(MONEY, "late:" + id + ":" + weekKey(today), 3, "⏰ Ödeme gecikti · " + who,
                who + "'dan " + tl(remaining) + " alacağın, söz verilen günden (" + SalePayStatus.shortDate(due)
                        + ") " + late + " gün geçti.");
    }

    /** Tezgah: akşam 20'den sonra gün sonu alınmamış açık alımlar varsa günde bir kez. */
    public static Alert dayEnd(int openCount, double openKg, int nowHour, String today) {
        if (openCount <= 0 || nowHour < 20) return null;
        return new Alert(TEZGAH, "zgun:" + today, 2, "🧾 Gün sonu alınmadı",
                openCount + " alım (" + kg(openKg) + ") açıkta. Akşam hesabını yapıp gün sonunu (Z) almayı unutma.");
    }

    // ------------------------------------------------------------------ yardımcılar

    static String garden(String g) { return g == null || g.trim().isEmpty() ? "Bahçe" : g.trim(); }

    static String deg(double c) { return Math.round(c) + "°C"; }

    static int hour(String iso) {
        int t = iso.indexOf('T');
        try { return t > 0 && iso.length() >= t + 3 ? Integer.parseInt(iso.substring(t + 1, t + 3)) : -1; }
        catch (NumberFormatException e) { return -1; }
    }

    static String hourLabel(String iso, String today) {
        int h = hour(iso);
        String day = iso.startsWith(today) ? "Bugün" : "Yarın";
        return day + (h >= 0 ? " saat " + (h < 10 ? "0" : "") + h + ":00" : "");
    }

    /** Hafta anahtarı: yılın günü / 7 (aynı haftada tekrar etmesin). */
    static String weekKey(String isoDate) {
        try {
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.clear();
            c.set(Integer.parseInt(isoDate.substring(0, 4)), Integer.parseInt(isoDate.substring(5, 7)) - 1,
                    Integer.parseInt(isoDate.substring(8, 10)));
            return isoDate.substring(0, 4) + "w" + ((c.get(java.util.Calendar.DAY_OF_YEAR) - 1) / 7);
        } catch (RuntimeException e) {
            return isoDate;
        }
    }

    static String tl(double v) {
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(new Locale("tr", "TR"));
        nf.setMaximumFractionDigits(0);
        return nf.format(Math.round(v)) + " ₺";
    }

    static String kg(double v) {
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(new Locale("tr", "TR"));
        nf.setMaximumFractionDigits(1);
        return nf.format(v) + " kg";
    }
}
