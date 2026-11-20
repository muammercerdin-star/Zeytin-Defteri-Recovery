package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tahsilat cümlesi (eğitim turu 1; saf, Android'siz, testli).
 * "Ali Usta'dan 2000 lira aldım" → alıcı Ali Usta, 2000 TL · "Hasan borcunun yarısını ödedi" → alıcı Hasan, oran yarım.
 *
 * Kural para / tutar / borç sözüne bağlıdır: "yağ aldık", "sürgün aldık", "ilaç aldık", "500 liralık gübre aldım"
 * tahsilat değildir (alacak değil, masraf). Alınan para "lira / para" sözünden hemen sonra "aldım" ile, ödeyen
 * kişi ise "lira / para / borcunun yarısını" sözünden hemen sonra "ödedi / verdi" ile söylenir.
 *
 * Hiçbir şey yazmaz: asistan Alıcılar'daki "Ödeme aldım" penceresini bu tutarla açar, kayıt çiftçinin Kaydet'iyle
 * yapılır. Alıcı rehberde yoksa ya da açık borcu yoksa hiçbir şey kaydedilmez; aynı adda birden çok alıcı varsa
 * sorulur. Oranlardan şimdilik yalnız "yarısı" tanınır; açık borç bilinmeden tutar uydurulmaz.
 */
final class CollectionEntry {
    static final String HALF = "yarim";

    private static final Locale TR = new Locale("tr", "TR");
    private static final String MONEY = "(lira|lirayi|tl|tlyi|para|parayi|parasini)";
    private static final String TAKEN = "(aldim|aldik|tahsil ettim|tahsil ettik)";
    private static final String PAID = "(odedi|verdi|gonderdi|yolladi|getirdi)";
    private static final String HALF_WORDS = "borcunun (yarisini|yarisi)";
    private static final String QUESTION = ".*\\b(kac|ne kadar|ne zaman|kim|mi|mu|miyim|misin|musun|midir|mudur)\\b.*";

    /** Alıcının söylenen adı ("Ali Usta"). */
    final String buyer;
    /** Söylenen tutar (TL); oranla söylendiyse 0. */
    final double amount;
    /** HALF ya da "". */
    final String ratio;

    private CollectionEntry(String buyer, double amount, String ratio) {
        this.buyer = buyer;
        this.amount = amount;
        this.ratio = ratio;
    }

    /** Tahsilat cümlesi mi; değilse null. */
    static CollectionEntry parse(String raw) {
        if (raw == null || raw.contains("?")) {
            return null;
        }
        String s = TurkishText.normalize(raw);
        if (s.matches(QUESTION)) {
            return null;
        }
        boolean taken = s.matches(".*\\b" + MONEY + " " + TAKEN + "\\b.*");
        boolean paid = s.matches(".*\\b(" + MONEY + "|" + HALF_WORDS + ") " + PAID + "\\b.*");
        if (!taken && !paid) {
            return null;
        }
        String ratio = s.matches(".*\\b" + HALF_WORDS + " " + PAID + "\\b.*") ? HALF : "";
        double amount = ratio.isEmpty() ? amount(raw) : 0;
        if (ratio.isEmpty() && amount <= 0) {
            return null;
        }
        String buyer = taken ? from(raw) : subject(raw);
        if (buyer.isEmpty()) {
            return null;
        }
        return new CollectionEntry(buyer, amount, ratio);
    }

    /** Söylenen tutar: "2000 lira", "iki bin lira" (liralık değil: o alınan malın değeridir). */
    private static double amount(String raw) {
        for (TurkishText.Quantity q : TurkishText.quantities(raw)) {
            if (TurkishText.LIRA.equals(q.unit) && !q.word.startsWith("liralik")) {
                return q.value;
            }
        }
        return 0;
    }

    /** "Ali Usta'dan" → "Ali Usta" (büyük harfli ad + kesme işareti + -dan/-den/-tan/-ten). */
    static String from(String raw) {
        String[] w = raw.trim().split("\\s+");
        for (int i = 0; i < w.length; i++) {
            String word = w[i].replaceAll("[,.;:!?]+$", "");
            int apo = Math.max(word.indexOf('\''), word.indexOf('’'));
            if (apo <= 0) {
                continue;
            }
            String suffix = word.substring(apo + 1).toLowerCase(TR);
            if (!suffix.matches("(dan|den|tan|ten)")) {
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

    /** "Hasan borcunun…", "Hasan Abi 500 lira verdi" → cümlenin başındaki büyük harfli ad (en çok iki kelime). */
    static String subject(String raw) {
        String[] w = raw.trim().split("\\s+");
        List<String> name = new ArrayList<>();
        for (String x : w) {
            String word = x.replaceAll("[,.;:!]+$", "");
            if (word.isEmpty() || !Character.isUpperCase(word.charAt(0)) || word.contains("'") || word.contains("’")) {
                break;
            }
            if (!word.matches("\\p{L}+")) {
                break;
            }
            name.add(word);
            if (name.size() == 2) {
                break;
            }
        }
        return String.join(" ", name);
    }

    /** Karar alanları: alici, tutar ya da oran. */
    Map<String, String> fields() {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("alici", buyer);
        if (amount > 0) {
            f.put("tutar", ElekVoiceDialog.num(amount));
        }
        if (!ratio.isEmpty()) {
            f.put("oran", ratio);
        }
        return f;
    }

    /** Pencereye yazılacak tutar: söylenen ya da açık borcun yarısı (kuruşa yuvarlı). Açık borç yoksa 0. */
    double amountFor(double openTotal) {
        if (openTotal <= 0.009) {
            return 0;
        }
        if (HALF.equals(ratio)) {
            return Math.round(openTotal * 50.0) / 100.0;
        }
        return amount;
    }

    /**
     * Rehberde bu ada uyan alıcıların sırası. Tek kelimelik ad ("Hasan") aynı adla başlayan herkese uyar ("Hasan",
     * "Hasan Abi"): birden çoksa sorulur. İki kelimelik ad ("Ali Usta") yalnız aynı ada uyar.
     */
    static List<Integer> matches(List<String> names, String spoken) {
        List<Integer> out = new ArrayList<>();
        String key = key(spoken);
        if (key.isEmpty()) {
            return out;
        }
        boolean oneWord = !key.contains(" ");
        for (int i = 0; i < names.size(); i++) {
            String n = key(names.get(i));
            boolean same = n.equals(key);
            boolean firstWord = oneWord && n.startsWith(key + " ");
            if (same || firstWord) {
                out.add(i);
            }
        }
        return out;
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(TR);
    }

    /** Söylenen tutar borçtan büyükse uyarı; değilse "". */
    static String warning(double amount, double openTotal) {
        if (amount <= openTotal + 0.009) {
            return "";
        }
        return "Söylediğin " + ElekSaleModel.tl(amount) + ", toplam borç " + ElekSaleModel.tl(openTotal)
                + ". Daha fazlası yazılamaz; tutarı düzelt.";
    }
}
