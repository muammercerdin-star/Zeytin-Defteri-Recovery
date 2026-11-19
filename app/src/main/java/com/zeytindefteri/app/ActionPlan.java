package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tek cümleden sıralı eylem listesi (İP7; saf, Android'siz, testli).
 * "Ali Usta'ya 300 kilo sattım, parasını haftaya verecek" → satış (Ali Usta, 300 kg) + para alınmadı (alacak) +
 * haftaya ödeme günü hatırlatması. Yalnız "sattım / verdim" ile "parası sonra / verecek / veresiye" birlikteyse
 * çok adımlıdır; tek eylemli cümleler buraya düşmez (RuleBrain yalnız başka kayıt yolu tutmayan cümlede sorar).
 *
 * Plan hiçbir şey yazmaz: ActionRunner formu görünür doldurur, tek "Kaydedeyim mi?" onayıyla yazar (RunnerFlow).
 * Bilinmeyen bilgi uydurulmaz: alıcı, kilo, sınıf ya da fiyat söylenmediyse boş kalır, formda sorulur.
 */
final class ActionPlan {
    static final String SALE = ActionCatalog.SATIS;
    static final String RECEIVABLE = "ALACAK";
    static final String REMINDER = ActionCatalog.HATIRLATMA;
    static final String VARIANT = "cok_adim";

    private static final Locale TR = new Locale("tr", "TR");
    private static final String[] DAYS = {"pazar", "pazartesi", "sali", "carsamba", "persembe", "cuma", "cumartesi"};

    final List<String> steps;
    /** Alıcının adı ("Ali Usta"); söylenmediyse "". */
    final String buyer;
    final double kilos;
    /** Sınıf kodu (GradeFormModel.CODES: "DOUBLE", "3"...); söylenmediyse "". */
    final String grade;
    /** Kilo başı fiyat (TL); söylenmediyse 0. */
    final double price;
    /** Ödeme günü sözü, yazıldığı gibi sadeleşmiş: "haftaya", "yarin", "3 gun sonra", "cuma", "ayin 15", "ay sonu"; yoksa "". */
    final String due;

    private ActionPlan(String buyer, double kilos, String grade, double price, String due) {
        this.buyer = buyer;
        this.kilos = kilos;
        this.grade = grade;
        this.price = price;
        this.due = due;
        List<String> s = new ArrayList<>();
        s.add(SALE);
        s.add(RECEIVABLE);
        if (!due.isEmpty()) {
            s.add(REMINDER);
        }
        this.steps = Collections.unmodifiableList(s);
    }

    /** Çok adımlı satış cümlesi mi; değilse null. */
    static ActionPlan parse(String raw) {
        String s = TurkishText.normalize(raw);
        if (!s.matches(".*\\b(sattim|sattik|verdim|verdik)\\b.*")) {
            return null;
        }
        if (!laterPayment(s) || paidNow(s)) {
            return null;
        }
        return new ActionPlan(buyer(raw), kilos(raw), grade(s), price(raw), due(s));
    }

    /** "parasını haftaya verecek", "parası sonra", "veresiye", "borcuna yazdım", "ödemesini cuma yapacak". */
    static boolean laterPayment(String s) {
        boolean money = s.matches(".*\\b(parasini|parasi|odemesini|odemeyi|odemesi)\\b.*");
        boolean later = s.matches(".*\\b(verecek|verecekmis|odeyecek|getirecek|yollayacak|gonderecek|yapacak|sonra)\\b.*");
        boolean credit = s.matches(".*\\b(veresiye|borcuna yaz\\p{L}*|hesabina yaz\\p{L}*)\\b.*");
        return (money && later) || credit;
    }

    /** "peşin", "parasını aldım": para alınmış, alacak yok. */
    private static boolean paidNow(String s) {
        return s.matches(".*\\b(pesin|pesinen|parasini aldim|parasini aldik|parayi aldim|nakit aldim)\\b.*");
    }

    /** "Ali Usta'ya" → "Ali Usta" (önündeki büyük harfli kelimeyle). Kesme işaretsiz addan alıcı uydurulmaz. */
    static String buyer(String raw) {
        String[] w = raw.trim().split("\\s+");
        for (int i = 0; i < w.length; i++) {
            String word = w[i].replaceAll("[,.;:!?]+$", "");
            int apo = Math.max(word.indexOf('\''), word.indexOf('’'));
            if (apo <= 0) {
                continue;
            }
            String suffix = word.substring(apo + 1).toLowerCase(TR);
            if (!suffix.matches("(y?a|y?e)")) {
                continue;
            }
            String name = word.substring(0, apo);
            if (!Character.isUpperCase(name.charAt(0))) {
                continue;
            }
            if (i > 0 && w[i - 1].length() > 1 && Character.isUpperCase(w[i - 1].charAt(0)) && !w[i - 1].endsWith(",")) {
                name = w[i - 1] + " " + name;
            }
            return name;
        }
        return "";
    }

    private static double kilos(String raw) {
        for (TurkishText.Quantity q : TurkishText.quantities(raw)) {
            if ("kg".equals(q.unit)) {
                return q.value;
            }
        }
        return 0;
    }

    /** Kilo başı fiyat: "68 liradan", "kilosu 68 lira". Toplam tutar ("20 bin liraya") fiyat sayılmaz. */
    private static double price(String raw) {
        String[] t = TurkishText.words(raw);
        for (TurkishText.Quantity q : TurkishText.quantities(raw)) {
            if (TurkishText.perUnitPrice(q.word)) {
                return q.value;
            }
            boolean perKilo = q.start > 0 && t[q.start - 1].matches("(kilosu|kilosunu|kiloyu)");
            if ("lira".equals(q.unit) && perKilo) {
                return q.value;
            }
        }
        return 0;
    }

    /** "3 numara", "üç numaradan", "double", "yağlık". */
    static String grade(String s) {
        if (s.matches(".*\\b(double|dabil|dubil)\\b.*")) {
            return "DOUBLE";
        }
        if (s.matches(".*\\b(yaglik|yaglikl\\p{L}*)\\b.*")) {
            return "YAGLIK";
        }
        String[] t = s.split(" ");
        for (int i = 0; i + 1 < t.length; i++) {
            if (!t[i + 1].matches("(numara|numaradan|numarayi|numarasi|numarasindan|no|nodan)")) {
                continue;
            }
            int[] end = new int[1];
            double v = TurkishText.number(t, i, end);
            if (v >= 1 && v <= 4 && v == Math.floor(v)) {
                return String.valueOf((int) v);
            }
        }
        return "";
    }

    /** Ödeme günü sözü (sadeleşmiş); yoksa "". */
    static String due(String s) {
        java.util.regex.Matcher days = java.util.regex.Pattern.compile("\\b(\\d+|bir|iki|uc|dort|bes|on|onbes) gun sonra\\b").matcher(s);
        if (days.find()) {
            int n = (int) TurkishText.number(days.group(1));
            return n > 0 ? n + " gun sonra" : "";
        }
        java.util.regex.Matcher dayOfMonth = java.util.regex.Pattern.compile("\\bayin (\\d{1,2})\\p{L}*").matcher(s);
        if (dayOfMonth.find()) {
            return "ayin " + Integer.parseInt(dayOfMonth.group(1));
        }
        if (s.matches(".*\\bay (sonu|sonunda|sonuna)\\b.*")) {
            return "ay sonu";
        }
        if (s.matches(".*\\b(haftaya|gelecek hafta|haftaya kadar)\\b.*")) {
            return "haftaya";
        }
        if (s.matches(".*\\b(obur gun|ertesi gun)\\b.*")) {
            return "obur gun";
        }
        if (s.matches(".*\\b(yarin|yarina)\\b.*")) {
            return "yarin";
        }
        for (String day : DAYS) {
            if (s.matches(".*\\b" + day + "(ya|ye|a|e|gunu|ye kadar|ya kadar)?\\b.*")) {
                return day;
            }
        }
        return "";
    }

    /** Ödeme günü (yyyy-MM-dd), bugüne göre; söz yoksa "". Geçmiş gün verilmez (bugün de değil). */
    String dueDate(Calendar today) {
        if (due.isEmpty()) {
            return "";
        }
        Calendar c = (Calendar) today.clone();
        if (due.equals("haftaya")) {
            c.add(Calendar.DAY_OF_YEAR, 7);
        } else if (due.equals("yarin")) {
            c.add(Calendar.DAY_OF_YEAR, 1);
        } else if (due.equals("obur gun")) {
            c.add(Calendar.DAY_OF_YEAR, 2);
        } else if (due.endsWith(" gun sonra")) {
            c.add(Calendar.DAY_OF_YEAR, Integer.parseInt(due.substring(0, due.indexOf(' '))));
        } else if (due.equals("ay sonu")) {
            c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH));
            if (!c.after(today)) {
                c.add(Calendar.MONTH, 1);
                c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH));
            }
        } else if (due.startsWith("ayin ")) {
            int day = Integer.parseInt(due.substring(5));
            if (day < 1 || day > 31) {
                return "";
            }
            c.set(Calendar.DAY_OF_MONTH, Math.min(day, c.getActualMaximum(Calendar.DAY_OF_MONTH)));
            if (!c.after(today)) {
                c.add(Calendar.MONTH, 1);
                c.set(Calendar.DAY_OF_MONTH, Math.min(day, c.getActualMaximum(Calendar.DAY_OF_MONTH)));
            }
        } else {
            int want = java.util.Arrays.asList(DAYS).indexOf(due) + 1;
            if (want <= 0) {
                return "";
            }
            do {
                c.add(Calendar.DAY_OF_YEAR, 1);
            } while (c.get(Calendar.DAY_OF_WEEK) != want);
        }
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /** Karar alanları (cümle bankası ve karne için): alıcı, kilo, sınıf, fiyat, ödeme, vade, adımlar. */
    Map<String, String> fields() {
        Map<String, String> f = new LinkedHashMap<>();
        if (!buyer.isEmpty()) {
            f.put("alici", buyer);
        }
        if (kilos > 0) {
            f.put("kilo", ElekVoiceDialog.num(kilos));
        }
        if (!grade.isEmpty()) {
            f.put("sinif", grade);
        }
        if (price > 0) {
            f.put("fiyat", ElekVoiceDialog.num(price));
        }
        f.put("odeme", "alinmadi");
        if (!due.isEmpty()) {
            f.put("vade", due);
        }
        f.put("adimlar", String.join(",", steps));
        return f;
    }

    /** Eksik bilgiler (formda sorulur), katalogdaki kısa soru sırasıyla. */
    List<String> missing(double habitPrice, String habitGrade) {
        List<String> m = new ArrayList<>();
        if (buyer.isEmpty()) {
            m.add("Kime sattın?");
        }
        if (grade.isEmpty() && habitGrade.isEmpty()) {
            m.add("Hangi numara?");
        }
        if (kilos <= 0) {
            m.add("Kaç kilo?");
        }
        if (price <= 0 && habitPrice <= 0) {
            m.add("Kilosu kaç liradan?");
        }
        return m;
    }

    /** Okunan özet: "Ali Usta'ya 300 kilo 3 No, kilosu 68 liradan satış; para alınmadı; 26 Kasım'da ödeme hatırlatması." */
    static String summary(String buyer, double kilos, String gradeLabel, double price, String dueDate) {
        StringBuilder b = new StringBuilder();
        if (!buyer.isEmpty()) {
            b.append(HabitModel.dative(buyer)).append(' ');
        }
        b.append(ElekVoiceDialog.num(kilos)).append(" kilo");
        if (!gradeLabel.isEmpty()) {
            b.append(' ').append(gradeLabel);
        }
        if (price > 0) {
            b.append(", kilosu ").append(ElekVoiceDialog.num(price)).append(" liradan");
            b.append(" (").append(ElekSaleModel.tl(kilos * price)).append(')');
        }
        b.append(" satış. Para alınmadı.");
        if (!dueDate.isEmpty()) {
            b.append(' ').append(dayText(dueDate)).append(" ödeme günü hatırlatması.");
        }
        return b.toString();
    }

    /** "2026-11-26" → "26 Kasım'da". */
    static String dayText(String iso) {
        String[] months = {"Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"};
        String[] at = {"'ta", "'ta", "'ta", "'da", "'ta", "'da", "'da", "'ta", "'de", "'de", "'da", "'ta"};
        int m = Integer.parseInt(iso.substring(5, 7)) - 1;
        int d = Integer.parseInt(iso.substring(8, 10));
        return d + " " + months[m] + at[m];
    }

    /** "Ali Usta" → "Ali Usta'dan", "Mehmet" → "Mehmet'ten" (hatırlatma notu). */
    static String ablative(String name) {
        String n = name.trim();
        if (n.isEmpty()) {
            return n;
        }
        String low = n.toLowerCase(TR);
        char last = low.charAt(low.length() - 1);
        char vowel = 'a';
        for (int i = low.length() - 1; i >= 0; i--) {
            if ("aeıioöuü".indexOf(low.charAt(i)) >= 0) {
                vowel = low.charAt(i);
                break;
            }
        }
        boolean front = "eiöü".indexOf(vowel) >= 0;
        boolean hard = "fstkçşhp".indexOf(last) >= 0;
        return n + "'" + (hard ? "t" : "d") + (front ? "en" : "an");
    }
}
