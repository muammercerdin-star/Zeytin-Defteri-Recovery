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

    /** Sırayla söylenecek sözler (ilk söz saate göre selam). month: 1–12. */
    public static List<String> lines(int hour, int month) {
        List<String> out = new ArrayList<>();
        out.add(greeting(hour));
        out.addAll(season(month));
        out.add("Ortadaki mikrofona bas, konuşarak kaydet. \"150 kilo topladım\" demen yeter.");
        out.add("Beni parmağınla tutup taşıyabilirsin. Gizlemek istersen üstüme basılı tut.");
        return out;
    }

    /** index'inci söz (döngüsel). */
    public static String line(int hour, int month, int index) {
        List<String> all = lines(hour, month);
        return all.get(Math.floorMod(index, all.size()));
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
