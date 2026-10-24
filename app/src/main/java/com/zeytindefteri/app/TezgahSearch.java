package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Alım defterinde arama (saf, Android'siz): ad, telefon ya da tutar; "sadece borçlular" süzgeci;
 * aynı üreticinin alımlarını toplayan özet. Türkçe harfler sadeleştirilir (Ş=s, ı=i…), büyük/küçük fark etmez.
 */
public final class TezgahSearch {
    private TezgahSearch() {}

    public static final class Group {
        public String name = "";
        public int count;
        public double gross, remaining;
    }

    /** Türkçe harfleri ve büyük/küçük farkını kaldırır. */
    public static String fold(String v) {
        if (v == null) return "";
        String t = v.trim().toLowerCase(new Locale("tr", "TR"));
        StringBuilder b = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            switch (c) {
                case 'ı': case 'î': c = 'i'; break;
                case 'ş': c = 's'; break;
                case 'ğ': c = 'g'; break;
                case 'ü': case 'û': c = 'u'; break;
                case 'ö': c = 'o'; break;
                case 'ç': c = 'c'; break;
                case 'â': c = 'a'; break;
                default: break;
            }
            b.append(c);
        }
        return b.toString().replaceAll("\\s+", " ");
    }

    static String digits(String v) {
        if (v == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < v.length(); i++) if (Character.isDigit(v.charAt(i))) b.append(v.charAt(i));
        return b.toString();
    }

    /**
     * Sorgu ada, telefona ya da tutara uyuyor mu. Rakamsa: telefon içinde, ya da toplam/kalan/kilo
     * tam sayısı bu rakamlarla başlıyorsa ("1655" → 1.655 TL borç). Boş sorgu her şeye uyar.
     */
    public static boolean matches(TezgahPurchases.Purchase p, String query) {
        String q = fold(query);
        if (q.isEmpty()) return true;
        if (fold(p.seller).contains(q)) return true;
        String d = digits(q);
        if (d.length() >= 2 && d.length() == q.replace(".", "").replace(",", "").replace(" ", "").length()) {
            if (digits(p.phone).contains(d)) return true;
            if (String.valueOf(Math.round(p.gross)).startsWith(d)) return true;
            if (p.remaining() > 0.5 && String.valueOf(Math.round(p.remaining())).startsWith(d)) return true;
            if (String.valueOf(Math.round(p.kg)).equals(d)) return true;
        }
        return false;
    }

    public static List<TezgahPurchases.Purchase> filter(List<TezgahPurchases.Purchase> all, String query, boolean debtOnly) {
        List<TezgahPurchases.Purchase> out = new ArrayList<>();
        if (all == null) return out;
        for (TezgahPurchases.Purchase p : all) {
            if (debtOnly && p.remaining() <= 0.5) continue;
            if (matches(p, query)) out.add(p);
        }
        return out;
    }

    /** Aynı ada sahip alımların özeti (adsız/münferit hariç), en çok alımı olan önce. */
    public static List<Group> groups(List<TezgahPurchases.Purchase> list) {
        Map<String, Group> map = new LinkedHashMap<>();
        for (TezgahPurchases.Purchase p : list) {
            String key = fold(p.seller);
            if (key.isEmpty() || key.equals(fold(TezgahWizardFlow.WALK_IN))) continue;
            Group g = map.get(key);
            if (g == null) { g = new Group(); g.name = p.seller.trim(); map.put(key, g); }
            g.count++;
            g.gross += p.gross;
            g.remaining += p.remaining();
        }
        List<Group> out = new ArrayList<>(map.values());
        out.sort((a, b) -> b.count != a.count ? b.count - a.count : Double.compare(b.remaining, a.remaining));
        return out;
    }

    public static double totalRemaining(List<TezgahPurchases.Purchase> list) {
        double t = 0;
        for (TezgahPurchases.Purchase p : list) t += p.remaining();
        return t;
    }
}
