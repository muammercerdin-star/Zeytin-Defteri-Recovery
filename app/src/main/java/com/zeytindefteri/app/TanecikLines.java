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
    }

    /** Eylem: balondaki düğme ne açar. */
    public static final String GO_ELEK = "ELEK", GO_WEATHER = "WEATHER", GO_BUYERS = "BUYERS", GO_REPORT = "REPORT";

    public static final class Line {
        public final String text, action, actionLabel;

        Line(String text) { this(text, "", ""); }

        Line(String text, String action, String actionLabel) {
            this.text = text;
            this.action = action;
            this.actionLabel = actionLabel;
        }
    }

    /**
     * Sırayla söylenecek sözler. Önce acil olan (yağmur, rüzgâr), sonra selam, sonra kullanıcının
     * kendi verisinden hatırlatmalar, en sonda mevsim öğüdü ve ipuçları. month: 1–12.
     */
    public static List<Line> smart(Facts f, int hour, int month) {
        if (f == null) f = new Facts();
        List<Line> out = new ArrayList<>();
        boolean harvest = month >= 10;
        if (f.raining) {
            out.add(new Line("Yağmur yağıyor. Şemsiyemi aldım! Toplanan zeytini ıslak bırakma.", GO_WEATHER, "Hava durumu ›"));
        } else if (f.rainChance >= 60) {
            out.add(new Line("24 saat içinde yağmur ihtimali %" + Math.round(f.rainChance)
                    + ". Hasadı ve ilaçlamayı ona göre planla.", GO_WEATHER, "Hava durumu ›"));
        }
        if (f.windKmh >= 25) {
            out.add(new Line("Rüzgâr kuvvetli (" + Math.round(f.windKmh) + " km/s). İlaç rüzgârla savrulur; bugün ilaçlama yapma.",
                    GO_WEATHER, "Hava durumu ›"));
        }
        out.add(new Line(greeting(hour)));
        if (cold(f)) {
            out.add(new Line("Hava soğuk, " + Math.round(f.tempC) + " derece. Atkımı taktım; sen de sıkı giyin!"));
        }
        if (f.hasGarden && harvest) {
            if (f.daysSinceHarvest == 0) {
                out.add(new Line("Bugünkü hasadı yazmışsın, eline sağlık!"));
            } else if (f.daysSinceHarvest >= 3 && hour >= 8 && hour < 21) {
                out.add(new Line(f.daysSinceHarvest + " gündür hasat kaydı yok. Topladıysan Elek'e yaz, kaybolmasın.",
                        GO_ELEK, "Elek'e git ›"));
            }
        }
        if (f.receivableCount > 0 && f.receivableTotal > 0.009) {
            out.add(new Line((f.receivableCount == 1 ? "Bir alıcıda " : f.receivableCount + " alıcıda toplam ")
                    + money(f.receivableTotal) + " alacağın var. Unutma, takip et.", GO_BUYERS, "Alıcılarım ›"));
        }
        if (!f.bestBuyer.isEmpty() && f.bestBuyerPerKg >= 0.5) {
            out.add(new Line("Aynı zeytine en çok " + f.bestBuyer + " veriyor; kilo başı "
                    + oneDecimal(f.bestBuyerPerKg) + " TL fazla.", GO_REPORT, "Elek raporu ›"));
        }
        for (String s : season(month)) {
            out.add(s.startsWith("Ayarlar'da Alıcılarım'a bak") ? new Line(s, GO_REPORT, "Elek raporu ›") : new Line(s));
        }
        out.add(new Line("Ortadaki mikrofona bas, konuşarak kaydet. \"150 kilo topladım\" demen yeter."));
        out.add(new Line("Beni parmağınla tutup taşıyabilirsin. Gizlemek istersen üstüme basılı tut."));
        return out;
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
