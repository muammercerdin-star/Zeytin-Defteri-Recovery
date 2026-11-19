package com.zeytindefteri.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Türkçe metnin TEK sadeleştiricisi ve TEK sayı okuyucusu (İP2; saf, Android'siz, testli).
 * Asistan, sesli elek/işlem/hasat, Tezgâh sesi ve internet yardımcısı aynı kuralla okur; aynı işin ikinci kodu yazılmaz.
 *
 * fold():      "İZMİT", "Iğdır", "ısı" → "izmit", "igdir", "isi" (Türkçe küçültme + aksansız yazım).
 * normalize(): fold, kesme işaretli ek ayrı kelime olur ("Ahmet'e" → "ahmet e"), sayının eki düşer
 *              ("1500'e" → "1500"), noktalama atılır,
 *              sayının içindeki . ve , kalır, tek boşluk.
 * number():    "120", "1.500", "1,5", "on iki", "bin beş yüz", "iki buçuk".
 * unit():      sayının yanındaki kelimenin anlamı: kilo, lira, kişi, saat, gram, litre, kasa, çuval...
 * quantity():  sayı + birim çifti ("2 kilo", "220 lira", "on iki işçi").
 */
public final class TurkishText {
    private static final Locale TR = new Locale("tr", "TR");
    private static final String[] UNITS = {"sifir", "bir", "iki", "uc", "dort", "bes", "alti", "yedi", "sekiz", "dokuz"};
    private static final String[] TENS = {"", "on", "yirmi", "otuz", "kirk", "elli", "altmis", "yetmis", "seksen", "doksan"};

    /** Birimler (unit() dönüşü). Form ve kayıtta görünen yazılışlarıdır. */
    public static final String LIRA = "lira";
    public static final String KILO = "kg";
    public static final String PERSON = "kişi";
    public static final String HOUR = "saat";
    public static final String GRAM = "g";
    public static final String LITRE = "L";
    public static final String CRATE = "kasa";
    public static final String SACK = "çuval";

    private TurkishText() {
    }

    // ============================================================ SADELEŞTİRME

    /** Türkçe küçük harf (İ→i, I→ı), sonra aksansız: ı→i, ç→c, ğ→g, ö→o, ş→s, ü→u, â/î/û→a/i/u. Boşluklara dokunmaz. */
    public static String fold(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.toLowerCase(TR);
        s = s.replace('ı', 'i');
        s = Normalizer.normalize(s, Normalizer.Form.NFD);
        return s.replaceAll("\\p{M}+", "");
    }

    /**
     * Anlama için sade metin: fold, kesme işaretli ek ayrı kelime ("Girelli'den" → "girelli den"; "Akhisar'da" gibi
     * yer sözleri böyle tanınır), sayının eki düşer ("1500'e patladı" → "1500 patladı"), "120kg"/"120 kg" → "120 kilo",
     * harf/rakam dışı her şey boşluk; yalnız sayının içindeki nokta ve virgül kalır ("1.500", "2,5"). Tek boşluk.
     */
    public static String normalize(String raw) {
        String s = fold(raw);
        s = s.replaceAll("(\\d)\\s*(kg|kilo)\\b", "$1 kilo");
        s = s.replaceAll("(?<=\\d)['’`]\\p{L}*", "");
        s = s.replaceAll("[^a-z0-9.,]+", " ");
        s = s.replaceAll("(?<!\\d)[.,]|[.,](?!\\d)", " ");
        return s.trim().replaceAll("\\s+", " ");
    }

    /** normalize() edilmiş metnin kelimeleri; boş metinde boş dizi. */
    public static String[] words(String raw) {
        String s = normalize(raw);
        if (s.isEmpty()) {
            return new String[0];
        }
        return s.split(" ");
    }

    // ============================================================ SAYI

    /**
     * t[i]'den başlayan sayı (rakam ya da yazıyla; kelimeler normalize() edilmiş olmalı); end[0] = sayıdan sonraki
     * kelime. Sayı değilse NaN. "1.500" binliktir (1500), "1,5" ve "2.5" ondalıktır; hemen arkadaki "buçuk" +0,5.
     */
    public static double number(String[] t, int i, int[] end) {
        String w = t[i];
        if (w.matches("\\d{1,3}(\\.\\d{3})+")) {
            end[0] = i + 1;
            return withHalf(Double.parseDouble(w.replace(".", "")), t, end);
        }
        if (w.matches("\\d+([.,]\\d+)?")) {
            end[0] = i + 1;
            double v = withHalf(Double.parseDouble(w.replace(',', '.')), t, end);
            return withScale(v, t, end);
        }
        double total = 0;
        double cur = 0;
        int j = i;
        boolean seen = false;
        while (j < t.length) {
            String x = t[j];
            int u = index(UNITS, x);
            int k = index(TENS, x);
            // "elli beş iki kasa": birler basamağı doluysa yeni birler yeni sayıdır (57 değil 55 + 2).
            if (u > 0) {
                if (cur % 10 != 0) {
                    break;
                }
                cur += u;
                seen = true;
            } else if (k > 0) {
                if (cur % 100 != 0) {
                    break;
                }
                cur += 10 * k;
                seen = true;
            } else if (x.equals("yuz")) {
                cur = (cur == 0 ? 1 : cur) * 100;
                seen = true;
            } else if (x.equals("bin")) {
                total += (cur == 0 ? 1 : cur) * 1000;
                cur = 0;
                seen = true;
            } else {
                break;
            }
            j++;
        }
        if (!seen) {
            return Double.NaN;
        }
        end[0] = j;
        return withHalf(total + cur, t, end);
    }

    /** Metnin tamamı bir sayıysa onu ("on iki" → 12, "1.500" → 1500, "iki buçuk" → 2,5); değilse NaN. */
    public static double number(String raw) {
        String[] t = words(raw);
        if (t.length == 0) {
            return Double.NaN;
        }
        int[] end = new int[1];
        double v = number(t, 0, end);
        if (Double.isNaN(v) || end[0] != t.length) {
            return Double.NaN;
        }
        return v;
    }

    /** Metindeki bütün sayılar sırayla ("üç", "1500'e", "2.500", "12,5"); sıfır ve eksi atlanır. */
    public static List<Double> numbers(String raw) {
        List<Double> out = new ArrayList<>();
        String[] t = words(raw);
        for (int i = 0; i < t.length; i++) {
            int[] end = new int[1];
            double v = number(t, i, end);
            if (Double.isNaN(v) || v <= 0) {
                continue;
            }
            out.add(v);
            i = Math.max(i, end[0] - 1);
        }
        return out;
    }

    /** Rakamdan sonra gelen "bin" / "milyon": "25 bin" → 25000, "1,5 milyon" → 1500000, "25 bin 500" → 25500. */
    private static double withScale(double v, String[] t, int[] end) {
        if (end[0] >= t.length) {
            return v;
        }
        String w = t[end[0]];
        if (w.equals("milyon")) {
            end[0]++;
            return v * 1000000;
        }
        if (!w.equals("bin")) {
            return v;
        }
        end[0]++;
        double out = v * 1000;
        // "25 bin 500 lira": arkadan gelen küçük sayı eklenir (rakamla ya da yazıyla).
        if (end[0] < t.length && t[end[0]].matches("\\d{1,3}")) {
            out += Double.parseDouble(t[end[0]]);
            end[0]++;
        }
        return out;
    }

    private static double withHalf(double v, String[] t, int[] end) {
        // "yirmi buçuk", "120 kilo buçuk" değil: yalnız hemen arkasındaki "buçuk"
        if (end[0] < t.length && t[end[0]].equals("bucuk")) {
            end[0]++;
            return v + 0.5;
        }
        return v;
    }

    private static int index(String[] arr, String w) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(w)) {
                return i;
            }
        }
        return -1;
    }

    // ============================================================ BİRİM

    /**
     * Sayının yanındaki kelimenin birimi (kelime normalize() edilmiş olmalı); birim değilse "".
     * "lira", "tl", "liradan" → lira · "kilo", "kg" → kg · "kişi", "işçi" → kişi · "saat" · "gram" → g ·
     * "litre", "lt" → L · "ml" · "kasa" · "çuval" · ton, km, sefer, gün, ağaç, dekar, adet, m³.
     */
    public static String unit(String w) {
        if (w == null) {
            return "";
        }
        if (w.matches("(lira\\p{L}*|tl|tlye|tllik|tlden|tlyi)")) {
            return LIRA;
        }
        if (w.matches("saat\\p{L}*")) {
            return HOUR;
        }
        if (w.matches("(kilo\\p{L}*|kg)")) {
            return KILO;
        }
        if (w.matches("(gram\\p{L}*|gr|g)")) {
            return GRAM;
        }
        if (w.matches("(litre\\p{L}*|lt|l)")) {
            return LITRE;
        }
        if (w.matches("(ml|mililitre\\p{L}*|militre\\p{L}*|cc)")) {
            return "ml";
        }
        if (w.matches("(ton\\p{L}*)")) {
            return "ton";
        }
        if (w.matches("(km|kilometre\\p{L}*)")) {
            return "km";
        }
        if (w.matches("(sefer\\p{L}*)")) {
            return "sefer";
        }
        if (w.matches("(gun\\p{L}*)")) {
            return "gün";
        }
        if (w.matches("(kisi\\p{L}*|isci\\p{L}*)")) {
            return PERSON;
        }
        if (w.matches("(agac\\p{L}*)")) {
            return "ağaç";
        }
        if (w.matches("(dekar\\p{L}*|donum\\p{L}*)")) {
            return "dekar";
        }
        if (w.matches("(adet\\p{L}*|tane\\p{L}*|tuzak\\p{L}*)")) {
            return "adet";
        }
        if (w.matches("(metrekup\\p{L}*|kup)")) {
            return "m³";
        }
        if (w.matches("(kasa|kasasi|kasalik|kasadan|kasaya)")) {
            return CRATE;
        }
        if (w.matches("(cuval\\p{L}*)")) {
            return SACK;
        }
        return "";
    }

    /** "110 liradan", "100 tlden": kilo/adet başı fiyat sözü (toplam değil). */
    public static boolean perUnitPrice(String w) {
        return w != null && w.matches("(liradan|tlden)");
    }

    /** Bir sayı ve yanındaki birim; start/end kelime sırası (end hariç, birim dahil). */
    public static final class Quantity {
        public final double value;
        public final String unit;
        /** Birim kelimesi olduğu gibi ("liradan", "kiloyu"); birim yoksa "". */
        public final String word;
        public final int start;
        public final int end;

        Quantity(double value, String unit, String word, int start, int end) {
            this.value = value;
            this.unit = unit;
            this.word = word;
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            String v = value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
            return unit.isEmpty() ? v : v + " " + unit;
        }
    }

    /** t[i]'deki sayı ve hemen arkasındaki birim (yoksa birim ""); sayı değilse null. */
    public static Quantity quantity(String[] t, int i) {
        int[] end = new int[1];
        double v = number(t, i, end);
        if (Double.isNaN(v)) {
            return null;
        }
        String next = end[0] < t.length ? t[end[0]] : "";
        String u = unit(next);
        if (u.isEmpty()) {
            return new Quantity(v, "", "", i, end[0]);
        }
        return new Quantity(v, u, next, i, end[0] + 1);
    }

    /** Metnin ilk sayı + birim çifti ("50 TL" → 50 lira, "50 kilo" → 50 kg); sayı yoksa null. */
    public static Quantity quantity(String raw) {
        List<Quantity> all = quantities(raw);
        return all.isEmpty() ? null : all.get(0);
    }

    /** Metindeki bütün sayı + birim çiftleri sırayla ("double 2 kilo 220 lira" → [2 kg, 220 lira]). */
    public static List<Quantity> quantities(String raw) {
        List<Quantity> out = new ArrayList<>();
        String[] t = words(raw);
        for (int i = 0; i < t.length; i++) {
            Quantity q = quantity(t, i);
            if (q == null) {
                continue;
            }
            out.add(q);
            i = q.end - 1;
        }
        return out;
    }
}
