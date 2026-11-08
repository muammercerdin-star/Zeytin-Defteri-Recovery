package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Tanecik'in söyledikleri: saate ve zeytin takvimine göre kısa, işe yarar sözler.
 * İnternet ve yapay zekâ yok; sabit cümleler. Her dokunuşta sıradaki söz gelir.
 *
 * Saf sınıf (Android yok): test edilir.
 */
public final class TanecikLines {
    private TanecikLines() {}

    /** 22:00–05:00 arası Tanecik uyur. */
    public static boolean sleeping(int hour) {
        return hour >= 22 || hour < 5;
    }

    /** Uyurken dokununca. */
    public static String sleepy() {
        return "Zzz… Geç oldu, yarın görüşürüz.";
    }

    /** Uyurken dokununca çıkan balon: "Uyandır" düğmesiyle. */
    public static Line sleepyLine() {
        return new Line("sleepy", sleepy(), GO_WAKE, "Uyandır ›", "", false);
    }

    /** Gece uyandırılınca. */
    public static Line wokeUp() {
        return new Line("woke", "Haa? Uyandım! Gece gece çalışıyorsun ha. Bana dokun, konuşalım.", "", "", "", false);
    }

    /** Gece uyanıkken bir süre dokunulmazsa yatarken. */
    public static Line goodnight() {
        return new Line("goodnight", "Uykum geldi… İyi geceler!", "", "", "", false);
    }

    /** Gece uyandırılınca bu kadar uyanık kalır; her dokunuş süreyi uzatır. */
    public static final long AWAKE_MS = 5 * 60 * 1000L;

    /** Tanecik'in bildiği o anki durum. Bilinmeyen alan NaN / -1 / boş kalır, o söz atlanır. */
    public static final class Facts {
        public double tempC = Double.NaN, windKmh = Double.NaN;
        /** Önümüzdeki 24 saatte en yüksek yağış ihtimali (%). */
        public double rainChance = Double.NaN;
        /** Şu an yağıyor mu (hava kodu yağmur/sağanak/fırtına). */
        public boolean raining;
        /** Seçili bahçede son hasattan bu yana gün; kayıt yoksa -1. */
        public long daysSinceHarvest = -1;
        public boolean hasGarden;
        public double receivableTotal;
        public int receivableCount;
        /** Elek raporunda aynı zeytine en çok veren alıcı ve kilo başı farkı (yoksa boş). */
        public String bestBuyer = "";
        public double bestBuyerPerKg;
        /** Bugünkü satış (sezonun satışlarından). */
        public double todayKg, todayTl;
        /** Bu sezonun toplam satışı. */
        public double seasonKg, seasonTl;
        /** En eski açık alacağın satışından bu yana gün ve alıcısı (-1: yok). */
        public long oldestReceivableDays = -1;
        public String oldestReceivableBuyer = "";
        /** Yarınki yağış ihtimali (%); bilinmiyorsa NaN. */
        public double tomorrowRain = Double.NaN;
    }

    /** Eylem: balondaki düğme ne açar. GO_ASK: soruyu asistana sorar (Line.question). */
    public static final String GO_ELEK = "ELEK", GO_WEATHER = "WEATHER", GO_BUYERS = "BUYERS", GO_REPORT = "REPORT",
            GO_ASK = "ASK", GO_WAKE = "WAKE";

    /** Alacak bu kadar gündür bekliyorsa Tanecik kendiliğinden uyarır. */
    static final int OLD_RECEIVABLE_DAYS = 14;

    public static final class Line {
        /** Kalıcı kimlik: günlük hafıza bununla tutulur. */
        public final String id;
        public final String text, action, actionLabel;
        /** GO_ASK için asistana gidecek soru (asistanın kurallarının anladığı bir cümle). */
        public final String question;
        /** Önemli: Tanecik başında "!" taşır, dokununca önce bunu söyler. */
        public final boolean important;

        Line(String text) { this("", text, "", "", "", false); }

        Line(String text, String action, String actionLabel) { this("", text, action, actionLabel, "", false); }

        Line(String id, String text, String action, String actionLabel, String question, boolean important) {
            this.id = id.isEmpty() ? "t" + Integer.toHexString(text.hashCode()) : id;
            this.text = text;
            this.action = action;
            this.actionLabel = actionLabel;
            this.question = question;
            this.important = important;
        }
    }

    private static Line ask(String id, String text, String question, String label) {
        return new Line(id, text, GO_ASK, label, question, false);
    }

    /**
     * Sırayla söylenecek sözler. Önce acil olan (yağmur, rüzgâr, bekleyen alacak), sonra selam, sonra kullanıcının
     * kendi verisinden hatırlatmalar, en sonda mevsim öğüdü, asistana sorulacak sorular ve ipuçları. month: 1–12.
     * Önerilen her soru asistanın kurallarıyla anlaşılır (tests/maskot'ta çözümleyiciyle denenir).
     */
    public static List<Line> smart(Facts f, int hour, int month) {
        if (f == null) f = new Facts();
        List<Line> out = new ArrayList<>();
        boolean harvest = month >= 10;
        if (f.raining) {
            out.add(new Line("rain_now", "Yağmur yağıyor. Şemsiyemi aldım! Toplanan zeytini ıslak bırakma.",
                    GO_WEATHER, "Hava durumu ›", "", true));
        } else if (f.rainChance >= 60) {
            out.add(new Line("rain_24h", "24 saat içinde yağmur ihtimali %" + Math.round(f.rainChance)
                    + ". Hasadı ve ilaçlamayı ona göre planla.", GO_WEATHER, "Hava durumu ›", "", true));
        } else if (harvest && f.tomorrowRain >= 60) {
            out.add(new Line("rain_tomorrow", "Yarın yağmur ihtimali %" + Math.round(f.tomorrowRain)
                    + ". Toplanacak zeytin varsa bugünü değerlendir.", GO_ASK, "Sor: yarın yağar mı? ›",
                    "yarın yağmur yağacak mı?", true));
        }
        if (f.windKmh >= 25) {
            out.add(new Line("wind", "Rüzgâr kuvvetli (" + Math.round(f.windKmh) + " km/s). İlaç rüzgârla savrulur; bugün ilaçlama yapma.",
                    GO_WEATHER, "Hava durumu ›", "", true));
        }
        if (f.oldestReceivableDays >= OLD_RECEIVABLE_DAYS && !f.oldestReceivableBuyer.isEmpty()) {
            out.add(new Line("recv_old", f.oldestReceivableBuyer + "'in parası " + f.oldestReceivableDays
                    + " gündür bekliyor. Bir arasan iyi olur.", GO_BUYERS, "Alıcılarım ›", "", true));
        }
        out.add(new Line("greet", greeting(hour), "", "", "", false));
        if (cold(f)) {
            out.add(new Line("cold", "Hava soğuk, " + Math.round(f.tempC) + " derece. Atkımı taktım; sen de sıkı giyin!",
                    "", "", "", false));
        }
        if (f.todayKg > 0) {
            out.add(ask("today_sales", "Bugün " + kgText(f.todayKg) + " kg sattın, " + money(f.todayTl) + ". Eline sağlık!",
                    "bugün ne kadar sattım?", "Ayrıntı ›"));
        } else if (f.hasGarden && harvest) {
            if (f.daysSinceHarvest == 0) {
                out.add(new Line("harvest_today", "Bugünkü hasadı yazmışsın, eline sağlık!", "", "", "", false));
            } else if (f.daysSinceHarvest >= 3 && hour >= 8 && hour < 21) {
                out.add(new Line("harvest_gap", f.daysSinceHarvest + " gündür hasat kaydı yok. Topladıysan Elek'e yaz, kaybolmasın.",
                        GO_ELEK, "Elek'e git ›", "", false));
            }
        }
        if (f.receivableCount > 0 && f.receivableTotal > 0.009) {
            out.add(new Line("recv", (f.receivableCount == 1 ? "Bir alıcıda " : f.receivableCount + " alıcıda toplam ")
                    + money(f.receivableTotal) + " alacağın var. Unutma, takip et.", GO_ASK, "Kimden? ›",
                    "kimden ne kadar alacağım var?", false));
        }
        if (!f.bestBuyer.isEmpty() && f.bestBuyerPerKg >= 0.5) {
            out.add(new Line("best_buyer", "Aynı zeytine en çok " + f.bestBuyer + " veriyor; kilo başı "
                    + oneDecimal(f.bestBuyerPerKg) + " TL fazla.", GO_REPORT, "Elek raporu ›", "", false));
        }
        if (f.seasonKg > 0) {
            out.add(ask("season_sum", "Bu sezon " + kgText(f.seasonKg) + " kg zeytin sattın, toplam " + money(f.seasonTl) + ".",
                    "bu sezon durumum ne?", "Özetle ›"));
        }
        List<String> seasonLines = season(month);
        for (int i = 0; i < seasonLines.size(); i++) {
            String s = seasonLines.get(i);
            out.add(s.startsWith("Ayarlar'da Alıcılarım'a bak")
                    ? new Line("season_" + i, s, GO_REPORT, "Elek raporu ›", "", false)
                    : new Line("season_" + i, s, "", "", "", false));
        }
        out.add(ask("ask_today", "Bana soru sorabilirsin. Mesela: \"Bugün bahçede ne var?\"", "bugün bahçede ne var?", "Sor ›"));
        out.add(ask("ask_price", "Piyasayı merak ediyorsan sor: \"2 numara fiyatı ne kadar?\"", "2 numara fiyatı ne kadar?", "Sor ›"));
        out.add(ask("ask_expense", "Masrafını bilmek ister misin? \"Bu sezon masraf ne kadar?\" de.", "bu sezon masraf ne kadar?", "Sor ›"));
        out.add(new Line("tip_mic", "Ortadaki mikrofona bas, konuşarak kaydet. \"150 kilo topladım\" demen yeter.", "", "", "", false));
        out.add(new Line("tip_drag", "Beni parmağınla tutup taşıyabilirsin. Gizlemek istersen üstüme basılı tut.", "", "", "", false));
        return out;
    }

    /**
     * Hangi söz söylensin (günlük hafıza): önce bugün söylenmemiş önemli söz; sonra sıradan, bugün söylenmemiş
     * ilk söz (sıra index'ten başlar); hepsi söylendiyse sıradaki.
     */
    public static Line choose(List<Line> lines, java.util.Set<String> shownToday, int index) {
        if (lines.isEmpty()) return new Line(greeting(12));
        for (Line l : lines) if (l.important && !shownToday.contains(l.id)) return l;
        int n = lines.size();
        for (int k = 0; k < n; k++) {
            Line l = lines.get(Math.floorMod(index + k, n));
            if (!shownToday.contains(l.id)) return l;
        }
        return lines.get(Math.floorMod(index, n));
    }

    /** Bugün söylenmemiş önemli bir söz var mı (başta "!" işareti için). */
    public static boolean hasNews(List<Line> lines, java.util.Set<String> shownToday) {
        for (Line l : lines) if (l.important && !shownToday.contains(l.id)) return true;
        return false;
    }

    static String kgText(double v) {
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(new java.util.Locale("tr", "TR"));
        nf.setMaximumFractionDigits(0);
        return nf.format(Math.max(0, Math.round(v)));
    }

    /** İlk tanışma (bir kez). */
    public static String intro() {
        return "Merhaba, ben Tanecik! Bahçende ve defterinde sana yardım edeceğim. Bana dokun, konuşayım.";
    }

    /** Hava koduna göre yağış var mı (WMO: çisenti, yağmur, sağanak, fırtına). */
    public static boolean rainCode(int code) {
        return (code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95;
    }

    /** Sırayla söylenecek sözler, veri olmadan (ilk söz saate göre selam). month: 1–12. */
    public static List<String> lines(int hour, int month) {
        List<String> out = new ArrayList<>();
        for (Line l : smart(null, hour, month)) out.add(l.text);
        return out;
    }

    /** index'inci söz (döngüsel). */
    public static String line(int hour, int month, int index) {
        List<String> all = lines(hour, month);
        return all.get(Math.floorMod(index, all.size()));
    }

    /** Atkı: 10 derece ve altı. */
    public static boolean cold(Facts f) {
        return f != null && !Double.isNaN(f.tempC) && f.tempC <= 10;
    }

    /** "10.340 TL": alacak tam lira söylenir (yuvarlanmaz). */
    static String money(double v) {
        double r = Math.round(v);
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(new java.util.Locale("tr", "TR"));
        nf.setMaximumFractionDigits(0);
        return nf.format(Math.max(0, r)) + " TL";
    }

    static String oneDecimal(double v) {
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(new java.util.Locale("tr", "TR"));
        nf.setMaximumFractionDigits(1);
        return nf.format(Math.max(0, v));
    }

    static String greeting(int hour) {
        if (hour >= 5 && hour < 11) return "Günaydın! Bugün bahçeye çıkıyor musun? Akşam eleği yazmayı unutma.";
        if (hour >= 11 && hour < 15) return "Kolay gelsin! Öğle molasında bir çay iç, sonra kayıtlara bakarız.";
        if (hour >= 15 && hour < 19) return "İyi günler! Bugün sattığın zeytini Elek'e yazdın mı?";
        return "İyi akşamlar! Günün kaydını yaptıysan dinlenme vakti.";
    }

    static List<String> season(int month) {
        List<String> out = new ArrayList<>();
        if (month >= 10) {
            out.add("Hasat zamanı! Her elek kaydı aynı zamanda satış kaydı; ayrıca yazman gerekmez.");
            out.add("Ayarlar'da Alıcılarım'a bak: \"Elek raporu\" aynı zeytine kimin daha çok verdiğini söyler.");
        } else if (month <= 3) {
            out.add("Budama zamanı. İşçi ve masrafı deftere yazarsan sezon sonunda kârını görürsün.");
        } else if (month <= 5) {
            out.add("Çiçek zamanı. İlaçlamadan önce ana sayfadaki hava kartına bak; rüzgârlı günü seçme.");
        } else if (month == 6) {
            out.add("Tane tutuyor. Sulama ve gübre masrafını yazmayı unutma.");
        } else if (month <= 8) {
            out.add("Çekirdek sertleşiyor. Sıcakta sulamayı ve masrafları deftere işle.");
        } else {
            out.add("Yağlanma zamanı, hasat yaklaşıyor. Kasaları, çuvalları hazırla.");
        }
        return out;
    }
}
