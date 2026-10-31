package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asistana söylenen elek cümlesini kilolara çevirir (saf, Android'siz).
 * "Bugün 3 numara 120 kilo, 2 numara 80 kilo sattım" → 3 No = 120, 2 No = 80, satıldı.
 * "120 kilo üç numara" (önce sayı), "duble yirmi kilo", "yağlık otuz beş buçuk" da anlaşılır.
 *
 * Asistan kaydı kendisi KAYDETMEZ: elek formu dolu açılır, kullanıcı kontrol edip Kaydet'e basar
 * (ses tanıma "120"yi "20" duyabilir; satış fişi başkasına gider).
 * Soru cümlesi ("3 numara kaç kilo?") giriş sayılmaz; asistanın soru cevaplama yolu aynen çalışır.
 */
public final class ElekVoiceEntry {
    /** Formdaki sırayla: Double, 1 No, 2 No, 3 No, 4 No, Yağlık. */
    public static final String[] LABELS = {"Double", "1 Numara", "2 Numara", "3 Numara", "4 Numara", "Yağlık"};

    public final double[] kg = new double[6];
    /** true: "sattım", false: "satmadım", null: söylenmedi (form varsayılanı kalır). */
    public Boolean sold;
    /** "Ahmet'e sattım" → "Ahmet"; söylenmediyse "". */
    public String buyer = "";

    private ElekVoiceEntry() {}

    public double totalKg() { double t = 0; for (double v : kg) t += v; return t; }

    /** Kullanıcıya gösterilecek özet: "3 Numara 120 kg · 2 Numara 80 kg". */
    public String summary() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < kg.length; i++) {
            if (kg[i] <= 0) continue;
            if (b.length() > 0) b.append(" · ");
            b.append(LABELS[i]).append(' ').append(fmt(kg[i])).append(" kg");
        }
        return b.toString();
    }

    private static final Locale TR = new Locale("tr", "TR");
    private static final String[] UNITS = {"sifir", "bir", "iki", "uc", "dort", "bes", "alti", "yedi", "sekiz", "dokuz"};
    private static final String[] TENS = {"", "on", "yirmi", "otuz", "kirk", "elli", "altmis", "yetmis", "seksen", "doksan"};
    private static final String[] ORDINALS = {"birinci", "ikinci", "ucuncu", "dorduncu"};
    private static final Pattern QUESTION = Pattern.compile(
            "(^|\\s)(kac|ne|neler|nedir|nasil|hangi|hangisi|mi|mu|misin|musun|midir|mudur|mıdır)(\\s|$)");
    private static final Pattern BUYER = Pattern.compile(
            "([A-ZÇĞİÖŞÜ][\\p{L}]+(?:\\s+[A-ZÇĞİÖŞÜ][\\p{L}]+)?)['’](?:y)?[aeAE]\\b");

    /** Cümle bir elek girişi değilse null. */
    public static ElekVoiceEntry parse(String raw) {
        if (raw == null) return null;
        String s = normalize(raw);
        if (s.isEmpty() || raw.contains("?") || QUESTION.matcher(s).find()) return null;
        if (s.matches(".*\\b(sil|guncelle|degistir|iptal)\\b.*")) return null;

        // 1) Belirteçlere ayır: sınıf (G) ve sayı (N)
        String[] t = s.split(" ");
        List<double[]> items = new ArrayList<>();       // {tip(0=sınıf,1=sayı), değer}
        for (int i = 0; i < t.length; i++) {
            String w = t[i];
            if (w.isEmpty()) continue;
            int g = gradeWord(w);
            if (g >= 0) { items.add(new double[]{0, g}); continue; }
            Matcher glued = Pattern.compile("^([1-4])(numara|no|nolu)$").matcher(w);   // "3numara"
            if (glued.find()) { items.add(new double[]{0, Integer.parseInt(glued.group(1))}); continue; }
            int[] end = new int[1];
            double v = number(t, i, end);
            if (!Double.isNaN(v)) {
                boolean single = end[0] == i + 1;
                String next = end[0] < t.length ? t[end[0]] : "";
                if (single && (next.equals("numara") || next.equals("no") || next.equals("nolu") || next.equals("numarali"))
                        && v >= 1 && v <= 4 && v == Math.floor(v)) {
                    items.add(new double[]{0, v}); i = end[0];         // "3 numara" → sınıf
                } else {
                    items.add(new double[]{1, v}); i = end[0] - 1;
                }
            }
        }
        // 2) Sınıf-sayı eşleştirme: cümle sayıyla başlıyorsa "120 kilo 3 numara", yoksa "3 numara 120 kilo".
        int first = -1;
        for (int i = 0; i < items.size(); i++) if (items.get(i)[0] == 0) { first = i; break; }
        if (first < 0) return null;
        boolean numberFirst = first > 0 && items.get(first - 1)[0] == 1;
        ElekVoiceEntry e = new ElekVoiceEntry();
        boolean any = false;
        boolean[] used = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i)[0] != 0) continue;
            int g = (int) items.get(i)[1];
            int j = numberFirst ? i - 1 : i + 1;
            if (j >= 0 && j < items.size() && items.get(j)[0] == 1 && !used[j]) {
                double v = items.get(j)[1];
                if (v > 0 && v <= 100000) { e.kg[g] += v; used[j] = true; any = true; }
            }
        }
        if (!any) return null;
        if (s.matches(".*\\b(satmadim|satmadik|satilmadi|satmayacagim)\\b.*")) e.sold = false;
        else if (s.matches(".*\\b(sattim|sattik|satildi|sattigim|satiyorum|sat|verdim|verdik)\\b.*")) e.sold = true;
        Matcher b = BUYER.matcher(raw);
        while (b.find()) {
            int after = b.end();
            String rest = raw.substring(after).toLowerCase(TR);
            if (rest.matches("^\\s*(\\d|bir|iki|üç|uc|dört|beş|sat|ver).*") || rest.matches("^\\s*$") || rest.contains("sat")) {
                e.buyer = b.group(1).trim();
            }
        }
        return e;
    }

    /** Sınıf adı: 0 Double, 1-4 numara sıra adları, 5 Yağlık; değilse -1. */
    private static int gradeWord(String w) {
        if (w.equals("double") || w.equals("duble") || w.equals("dubl") || w.equals("dabil") || w.equals("dobl") || w.equals("dubleyi")) return 0;
        if (w.startsWith("yaglik") || w.equals("yagli") || w.equals("yaglisi")) return 5;
        for (int i = 0; i < ORDINALS.length; i++) if (w.equals(ORDINALS[i])) return i + 1;
        return -1;
    }

    /** t[i]'den başlayan sayı (rakam ya da yazıyla); end[0] = sayıdan sonraki belirteç. Sayı değilse NaN. */
    static double number(String[] t, int i, int[] end) {
        String w = t[i];
        if (w.matches("\\d{1,3}(\\.\\d{3})+")) { end[0] = i + 1; return withHalf(Double.parseDouble(w.replace(".", "")), t, end); }
        if (w.matches("\\d+([.,]\\d+)?")) { end[0] = i + 1; return withHalf(Double.parseDouble(w.replace(',', '.')), t, end); }
        double total = 0, cur = 0; int j = i; boolean seen = false;
        while (j < t.length) {
            String x = t[j]; int u = index(UNITS, x), k = index(TENS, x);
            if (u > 0) { cur += u; seen = true; }
            else if (k > 0) { cur += 10 * k; seen = true; }
            else if (x.equals("yuz")) { cur = (cur == 0 ? 1 : cur) * 100; seen = true; }
            else if (x.equals("bin")) { total += (cur == 0 ? 1 : cur) * 1000; cur = 0; seen = true; }
            else break;
            j++;
        }
        if (!seen) return Double.NaN;
        end[0] = j;
        return withHalf(total + cur, t, end);
    }

    private static double withHalf(double v, String[] t, int[] end) {
        // "yirmi buçuk", "120 kilo buçuk" değil: yalnız hemen arkasındaki "buçuk"
        if (end[0] < t.length && t[end[0]].equals("bucuk")) { end[0]++; return v + 0.5; }
        return v;
    }

    private static int index(String[] arr, String w) { for (int i = 0; i < arr.length; i++) if (arr[i].equals(w)) return i; return -1; }

    static String normalize(String raw) {
        String s = raw.toLowerCase(TR)
                .replace('ı', 'i').replace('ç', 'c').replace('ş', 's').replace('ğ', 'g').replace('ö', 'o').replace('ü', 'u')
                .replace('â', 'a').replace('î', 'i').replace('û', 'u');
        s = s.replaceAll("(\\d)\\s*(kg|kilo)\\b", "$1 kilo");
        s = s.replaceAll("(\\d)\\.(?=\\s*(numara|no)\\b)", "$1");      // "3. numara"
        s = s.replaceAll("['’]\\p{L}*", " ");                          // ekler: "Ahmet'e"
        s = s.replaceAll("[^a-z0-9.,]+", " ").replaceAll("(?<!\\d)[.,]|[.,](?!\\d)", " ");
        return s.trim().replaceAll("\\s+", " ");
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v).replace('.', ',');
    }
}
