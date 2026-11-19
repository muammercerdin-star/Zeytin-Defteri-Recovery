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
    /**
     * Söylenen kilo başı fiyat (TL/kg), sınıf sırasıyla; 0 = söylenmedi (formdaki fiyat kalır).
     * "double 2 kilo 220 lira" → 110 (toplam ÷ kilo); "3 numara 120 kilo 110 liradan" → 110 (kilo başı).
     */
    public final double[] price = new double[6];
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
            if (price[i] > 0) b.append(" (").append(fmt(price[i])).append(" TL/kg)");
        }
        return b.toString();
    }

    private static final Locale TR = new Locale("tr", "TR");
    private static final String[] ORDINALS = {"birinci", "ikinci", "ucuncu", "dorduncu"};
    private static final Pattern QUESTION = Pattern.compile(
            "(^|\\s)(kac|ne|neler|nedir|nasil|hangi|hangisi|mi|mu|misin|musun|midir|mudur|mıdır)(\\s|$)");
    private static final Pattern BUYER = Pattern.compile(
            "([A-ZÇĞİÖŞÜ][\\p{L}]+(?:\\s+[A-ZÇĞİÖŞÜ][\\p{L}]+)?)['’](?:y)?[aeAE]\\b");

    /** Cümle bir elek girişi değilse null. */
    public static ElekVoiceEntry parse(String raw) {
        if (raw == null) return null;
        String s = TurkishText.normalize(raw);
        if (s.isEmpty() || raw.contains("?") || QUESTION.matcher(s).find()) return null;
        if (s.matches(".*\\b(sil|guncelle|degistir|iptal)\\b.*")) return null;

        // 1) Belirteçlere ayır: sınıf (G) ve sayı (N)
        String[] t = s.split(" ");
        List<double[]> items = new ArrayList<>();       // {tip(0=sınıf,1=sayı), değer, para(0 yok, 1 toplam lira, 2 kilo başı)}
        for (int i = 0; i < t.length; i++) {
            String w = t[i];
            if (w.isEmpty()) continue;
            int g = gradeWord(w);
            if (g >= 0) { items.add(new double[]{0, g}); continue; }
            Matcher glued = Pattern.compile("^([1-4])(numara\\p{L}*|no|nolu)$").matcher(w);   // "3numara", "3numaradan"
            if (glued.find()) { items.add(new double[]{0, Integer.parseInt(glued.group(1))}); continue; }
            int[] end = new int[1];
            double v = TurkishText.number(t, i, end);
            if (!Double.isNaN(v)) {
                boolean single = end[0] == i + 1;
                String next = end[0] < t.length ? t[end[0]] : "";
                // "3 numara", "3 numaradan / numaraya / numarayı" (ekli hâller de sınıf)
                if (single && (next.startsWith("numara") || next.equals("no") || next.equals("nolu"))
                        && v >= 1 && v <= 4 && v == Math.floor(v)) {
                    items.add(new double[]{0, v}); i = end[0];         // "3 numara" → sınıf
                } else {
                    double m = money(next);
                    // "kilosu 140 lira", "kilo başı 140 lira", "140 lira kilosu": toplam değil, kilo fiyatı.
                    if (m == 1 && perKiloAround(t, i, end[0])) {
                        m = 2;
                    }
                    items.add(new double[]{1, v, m});
                    i = end[0] - 1;
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
        int pairs = 0;
        List<double[]> totals = new ArrayList<>();      // {sınıf, toplam lira}: kilo başına bölünecek
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i)[0] != 0) continue;
            int g = (int) items.get(i)[1];
            int j = numberFirst ? i - 1 : i + 1;
            // Lira sözü kilo değildir: "double 220 lira" kilo sayılmaz.
            if (j >= 0 && j < items.size() && isKilo(items.get(j)) && !used[j]) {
                double v = items.get(j)[1];
                if (v > 0 && v <= 100000) {
                    e.kg[g] += v;
                    used[j] = true;
                    any = true;
                    pairs++;
                    int k = numberFirst ? i + 1 : j + 1;
                    readPrice(items, k, used, g, e, totals);
                }
            }
        }
        if (!any) return null;
        // Toplam lira ("2 kilo 220 lira") ancak her sınıfın kendi tutarı söylendiyse kiloya bölünür;
        // "3 numara 120, 2 numara 80, 13.200 lira" gibi tek toplam hangi sınıfın olduğu belirsiz: fiyat yazılmaz.
        if (totals.size() == pairs) {
            for (double[] t2 : totals) {
                int g = (int) t2[0];
                e.price[g] = priceFromTotal(t2[1], e.kg[g]);
            }
        }
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

    /** Kilo başı fiyatın altı mantıksız sayılır: "120 kilo 110 lira" toplam olamaz (0,92 TL/kg), kilo fiyatıdır. */
    static final double MIN_PRICE_PER_KG = 10;

    /**
     * "2 kilo 220 lira" → 110 (toplam ÷ kilo). Bölünce kilo fiyatı mantıksız kalırsa ("120 kilo 110 lira" → 0,92)
     * söylenen lira kilo fiyatıdır → 110. İkisi de olmuyorsa fiyat yazılmaz (0).
     */
    static double priceFromTotal(double lira, double kg) {
        if (kg <= 0 || lira <= 0) {
            return 0;
        }
        double per = Math.round(lira / kg * 100.0) / 100.0;
        if (per >= MIN_PRICE_PER_KG && per <= 100000) {
            return per;
        }
        if (per < MIN_PRICE_PER_KG && lira >= MIN_PRICE_PER_KG && lira <= 100000) {
            return lira;
        }
        return 0;
    }

    /** Sayının hemen önünde ya da liranın arkasında "kilosu / kilo başı / kilogramı" var mı. */
    private static boolean perKiloAround(String[] t, int start, int end) {
        String before = start > 0 ? t[start - 1] : "";
        String before2 = start > 1 ? t[start - 2] : "";
        String after = end + 1 < t.length ? t[end + 1] : "";
        String after2 = end + 2 < t.length ? t[end + 2] : "";
        if (before.matches("kilosu|kilosunu|kilogrami|kgsi|kilosuna")) {
            return true;
        }
        if (before.equals("basi") && before2.matches("kilo|kg")) {
            return true;
        }
        if (after.matches("kilosu|kilogrami|kgsi")) {
            return true;
        }
        return after.matches("kilo|kg") && after2.matches("basi|basina");
    }

    /** Sayının yanındaki söz para mı: 1 toplam ("220 lira"), 2 kilo başı ("110 liradan"), 0 değil. */
    private static double money(String next) {
        if (!TurkishText.unit(next).equals(TurkishText.LIRA)) return 0;
        return TurkishText.perUnitPrice(next) ? 2 : 1;
    }

    private static boolean isKilo(double[] item) {
        return item[0] == 1 && (item.length < 3 || item[2] == 0);
    }

    /** Kilonun hemen arkasındaki lira: kilo başıysa doğrudan fiyat, toplamsa sonra kiloya bölünmek üzere saklanır. */
    private static void readPrice(List<double[]> items, int k, boolean[] used, int g, ElekVoiceEntry e, List<double[]> totals) {
        if (k < 0 || k >= items.size() || used[k]) return;
        double[] it = items.get(k);
        if (it[0] != 1 || it.length < 3 || it[2] == 0) return;
        if (it[1] <= 0 || it[1] > 10000000) return;
        used[k] = true;
        if (it[2] == 2) {
            e.price[g] = it[1];
            return;
        }
        totals.add(new double[]{g, it[1]});
    }

    /** Cümlede geçen satış bilgisi (kilo olmasa da): {sold} ve alıcı. "Ahmet'e sattım, bitti" gibi. */
    public static Boolean soldIn(String raw) {
        String s = TurkishText.normalize(raw == null ? "" : raw);
        if (s.matches(".*\\b(satmadim|satmadik|satilmadi|satmayacagim)\\b.*")) return false;
        if (s.matches(".*\\b(sattim|sattik|satildi|sattigim|satiyorum|sat|verdim|verdik)\\b.*")) return true;
        return null;
    }

    public static String buyerIn(String raw) {
        if (raw == null) return "";
        String out = "";
        Matcher b = BUYER.matcher(raw);
        while (b.find()) {
            String rest = raw.substring(b.end()).toLowerCase(TR);
            if (rest.contains("sat") || rest.contains("ver")) out = b.group(1).trim();
        }
        return out;
    }

    /** Cümlede adı geçen sınıflar (sıra: Double..Yağlık). "3 numarayı sil" → [3]. */
    public static boolean[] gradesIn(String raw) {
        boolean[] out = new boolean[6];
        String s = TurkishText.normalize(raw == null ? "" : raw);
        String[] t = s.split(" ");
        for (int i = 0; i < t.length; i++) {
            int g = gradeWord(t[i]);
            if (g >= 0) { out[g] = true; continue; }
            Matcher glued = Pattern.compile("^([1-4])(numara\\p{L}*|no|nolu)$").matcher(t[i]);
            if (glued.find()) { out[Integer.parseInt(glued.group(1))] = true; continue; }
            String next = i + 1 < t.length ? t[i + 1] : "";
            if (next.startsWith("numara") || next.equals("no") || next.equals("nolu")) {
                int[] end = new int[1]; double v = TurkishText.number(t, i, end);
                if (!Double.isNaN(v) && end[0] == i + 1 && v >= 1 && v <= 4 && v == Math.floor(v)) out[(int) v] = true;
            }
        }
        return out;
    }

    /** Sınıf adı: 0 Double, 1-4 numara sıra adları, 5 Yağlık; değilse -1. */
    static int gradeWord(String w) {
        if (w.equals("double") || w.equals("duble") || w.equals("dubl") || w.equals("dabil") || w.equals("dobl") || w.equals("dubleyi")) return 0;
        if (w.startsWith("yaglik") || w.equals("yagli") || w.equals("yaglisi")) return 5;
        for (int i = 0; i < ORDINALS.length; i++) if (w.equals(ORDINALS[i])) return i + 1;
        return -1;
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v).replace('.', ',');
    }
}
