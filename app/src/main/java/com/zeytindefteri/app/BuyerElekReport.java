package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.List;

/**
 * "Elek raporu": alıcı karşılaştırmasını köylünün konuştuğu dille anlatan metin.
 *
 * Yapay zekâ yok, internet yok: sabit kurallar. Rakamlar {@link BuyerFairCompare}'den gelir
 * (aynı bahçe ±7 gün; sınıf fiyatı ±3 gün). Kurallar:
 *  - Kilo başı fark → "100 kg'da yaklaşık X TL" ve "karşılaştırılan N kg'da yaklaşık Y TL".
 *  - Fark elekten mi fiyattan mı: sınıf fiyatları ortalama 1 TL'den az ayrışıyorsa "fark elekten geliyor".
 *  - Az eşleşmede (5 satıştan az) "yön gösterir, kesin değil" uyarısı.
 *  - Eşleşme yoksa hüküm yok; nedeni söylenir.
 *
 * Saf sınıf (Android yok): test edilir.
 */
public final class BuyerElekReport {
    /** Bu kadar satış eşleşmeden sonuç "kesin" gibi söylenmez. */
    static final int SURE_SALES = 5;
    /** Kilo başı bu kadar farkın altı "benzer" sayılır. */
    static final double SAME_PER_KG = 0.5;
    /** Sınıf fiyatlarında ortalama bu kadar farkın altı "yakın" sayılır. */
    static final double SAME_GRADE_PRICE = 1.0;

    private BuyerElekReport() {}

    /** Rapora girecek alıcı. */
    public static final class Buyer {
        public long id;
        public String name = "";
        public double receivable;

        public Buyer(long id, String name, double receivable) {
            this.id = id;
            this.name = name == null ? "" : name;
            this.receivable = receivable;
        }
    }

    /** Bir alıcının bölümü. tone: +1 iyi (yeşil), -1 kötü (uyarı), 0 nötr/yetersiz. */
    public static final class Section {
        public String title = "";
        public int tone;
        public boolean compared;
        public final List<String> lines = new ArrayList<>();
        /** Sıralama için: kilo başı fark (karşılaştırma yoksa NaN). */
        double perKg = Double.NaN;
    }

    public static final class Report {
        public String headline = "";
        public final List<Section> sections = new ArrayList<>();
        public String tip = "";

        /** Sesli okuma ve paylaşım için tek metin. */
        public String plainText() {
            StringBuilder s = new StringBuilder(headline);
            for (Section sec : sections) {
                s.append("\n\n").append(sec.title).append('.');
                for (String l : sec.lines) s.append(' ').append(l);
            }
            if (!tip.isEmpty()) s.append("\n\n").append(tip);
            return s.toString();
        }
    }

    public static Report build(List<BuyerFairCompare.Sale> sales, List<Buyer> buyers) {
        Report r = new Report();
        List<Section> compared = new ArrayList<>(), rest = new ArrayList<>();
        for (Buyer b : buyers) {
            if (!hasSales(sales, b.id)) continue;
            Section s = section(sales, b);
            (s.compared ? compared : rest).add(s);
        }
        // Önce en çok veren, sonra en az veren; karşılaştırılamayanlar en sonda.
        compared.sort((x, y) -> Double.compare(y.perKg, x.perKg));
        r.sections.addAll(compared);
        r.sections.addAll(rest);
        r.headline = headline(compared, rest.size());
        if (!r.sections.isEmpty()) {
            r.tip = "En sağlam deneme: aynı gün aynı bahçeden topladığın zeytini ikiye böl, yarısını bir alıcıya, "
                    + "yarısını öbürüne ver. Defter ikisini kendiliğinden karşılaştırır.";
        }
        return r;
    }

    static Section section(List<BuyerFairCompare.Sale> sales, Buyer b) {
        Section s = new Section();
        BuyerFairCompare.Elek e = BuyerFairCompare.elek(sales, b.id);
        if (e.matchedSales == 0 || !e.enough()) {
            s.title = b.name + ": karşılaştırılamadı";
            s.lines.add(e.matchedSales == 0
                    ? "Aynı bahçeden, aynı hafta başka bir alıcıya satışın olmadığı için " + b.name + " ile kıyas yapılamadı."
                    : "Eşleşen satış çok az (" + BuyerFairCompare.kg(e.matchedKg) + " kg). Kıyas için iki tarafta da en az "
                    + Math.round(BuyerFairCompare.MIN_KG) + " kg gerekir.");
            addReceivable(s, b);
            return s;
        }
        s.compared = true;
        double perKg = e.perKgDiff();
        s.perKg = perKg;
        boolean same = Math.abs(perKg) < SAME_PER_KG;
        s.tone = same ? 0 : perKg > 0 ? 1 : -1;
        String amount = BuyerDirectory.oneDecimal(Math.abs(perKg)) + " TL";
        s.title = same ? b.name + ": diğerleriyle aynı" : b.name + ": kilo başı " + amount + (perKg > 0 ? " fazla" : " az");

        // 1) Ne kadar para?
        if (same) {
            s.lines.add("Aynı bahçeden, aynı hafta başka alıcılara verdiğin zeytinle karşılaştırınca " + b.name
                    + " kilo başı hemen hemen aynı parayı veriyor.");
        } else {
            s.lines.add("Aynı bahçeden, aynı hafta başka alıcılara verdiğin zeytinle karşılaştırınca " + b.name
                    + " kilo başı " + amount + (perKg > 0 ? " fazla" : " az") + " veriyor. 100 kilo zeytinde bu yaklaşık "
                    + money(Math.abs(perKg) * 100) + " eder.");
        }

        // 2) Elek iri mi ufak mı?
        double top = e.buyerShare[0] + e.buyerShare[1], topO = e.othersShare[0] + e.othersShare[1];
        double low = e.buyerShare[4] + e.buyerShare[5], lowO = e.othersShare[4] + e.othersShare[5];
        double score = (top - topO) - (low - lowO);
        String detail = "Double ile 1 Numara %" + BuyerDirectory.pct(top) + " çıkıyor (diğerlerinde %" + BuyerDirectory.pct(topO)
                + "), 4 Numara ile Yağlık %" + BuyerDirectory.pct(low) + " (diğerlerinde %" + BuyerDirectory.pct(lowO) + ").";
        int elekSide = score >= 4 ? 1 : score <= -4 ? -1 : 0;
        if (elekSide > 0) s.lines.add("Eleği iri çıkarıyor: " + detail);
        else if (elekSide < 0) s.lines.add("Eleği ufak çıkarıyor: " + detail);
        else s.lines.add("Eleği diğerlerine benziyor: " + detail);

        // 3) Sınıf fiyatı mı, elek mi?
        BuyerFairCompare.Prices p = BuyerFairCompare.prices(sales, b.id);
        double gapKg = 0, gap = 0;
        List<String> ahead = new ArrayList<>(), behind = new ArrayList<>();
        for (BuyerFairCompare.PriceRow row : p.rows) {
            if (!row.matched()) continue;
            double d = row.buyerAvg - row.othersAvg;
            gap += d * row.matchedKg;
            gapKg += row.matchedKg;
            String part = locative(BuyerDirectory.GRADE_LABELS[row.grade]) + " " + BuyerDirectory.oneDecimal(Math.abs(d)) + " TL";
            if (d >= SAME_PER_KG) ahead.add(part);
            else if (d <= -SAME_PER_KG) behind.add(part);
        }
        if (gapKg > 0) {
            double avgGap = gap / gapKg;
            if (Math.abs(avgGap) < SAME_GRADE_PRICE) {
                s.lines.add(elekSide != 0 && !same
                        ? "Sınıf fiyatları diğerlerine yakın; yani fark daha çok elekten geliyor."
                        : "Sınıf fiyatları da diğerlerine yakın.");
            } else if (avgGap > 0) {
                if (same) s.lines.add("Sınıf fiyatlarında önde: " + list(ahead, 3) + " fazla veriyor.");
                else if (perKg > 0) s.lines.add("Sınıf fiyatlarında da önde: " + list(ahead, 3) + " fazla veriyor.");
                else s.lines.add("Sınıf fiyatlarında ise önde (" + list(ahead, 3) + " fazla veriyor); ama elek bunu yiyor, kilo başı yine az kalıyor.");
            } else {
                if (same) s.lines.add("Sınıf fiyatlarında geride: " + list(behind, 3) + " az veriyor.");
                else if (perKg < 0) s.lines.add("Sınıf fiyatlarında da geride: " + list(behind, 3) + " az veriyor.");
                else s.lines.add("Sınıf fiyatlarında ise geride (" + list(behind, 3) + " az veriyor); yani fazlası elekten geliyor.");
            }
        }

        // 4) Bu sezon toplam etkisi
        if (!same) {
            s.lines.add("Karşılaştırılan " + BuyerFairCompare.kg(e.matchedKg) + " kiloda yaklaşık "
                    + money(Math.abs(perKg) * e.matchedKg) + (perKg > 0 ? " fazla aldın." : " az aldın."));
        }
        addReceivable(s, b);

        // 5) Ne kadar güvenilir?
        s.lines.add(e.matchedSales < SURE_SALES
                ? "Yalnız " + e.matchedSales + " satış eşleşti; sonuç yön gösterir, kesin değil."
                : e.matchedSales + " satış eşleşti.");
        return s;
    }

    static String headline(List<Section> compared, int notCompared) {
        if (compared.isEmpty()) {
            return notCompared == 0 ? "Bu sezon alıcılara satış kaydı yok."
                    : "Henüz karşılaştırma yapacak kadar eşleşen satış yok. Aynı bahçenin zeytinini aynı hafta iki ayrı alıcıya "
                    + "verince defter kimin daha çok verdiğini söyler.";
        }
        Section best = compared.get(0), worst = compared.get(compared.size() - 1);
        String bestName = name(best);
        if (compared.size() == 1) {
            if (best.tone > 0) return "Aynı zeytine " + bestName + " diğer alıcılarından daha çok para veriyor.";
            if (best.tone < 0) return "Aynı zeytine " + bestName + " diğer alıcılarından daha az para veriyor.";
            return "Aynı zeytine " + bestName + " diğer alıcılarınla aynı parayı veriyor.";
        }
        if (best.tone <= 0 && worst.tone >= 0) return "Alıcıların aynı zeytine hemen hemen aynı parayı veriyor.";
        StringBuilder h = new StringBuilder();
        if (best.tone > 0) h.append("Aynı zeytine en çok parayı ").append(bestName).append(" veriyor.");
        if (worst.tone < 0) {
            if (h.length() > 0) h.append(' ');
            h.append("En az veren ").append(name(worst)).append('.');
        }
        return h.toString();
    }

    /** "Yağlık" → "Yağlık'ta", "1 Numara" → "1 Numara'da", "Double" → "Double'da". */
    static String locative(String label) {
        return label + (label.endsWith("k") ? "'ta" : "'da");
    }

    private static String name(Section s) {
        int colon = s.title.indexOf(':');
        return colon > 0 ? s.title.substring(0, colon) : s.title;
    }

    private static boolean hasSales(List<BuyerFairCompare.Sale> sales, long buyerId) {
        for (BuyerFairCompare.Sale s : sales) if (s.buyerId == buyerId && s.totalKg() > 0) return true;
        return false;
    }

    private static void addReceivable(Section s, Buyer b) {
        if (b.receivable > 0.009) s.lines.add("Sende " + money(b.receivable) + " alacağı var.");
    }

    /** "Double'da 1,3 TL, 1 Numara'da 1,5 TL ve 2 Numara'da 1,5 TL". */
    static String list(List<String> parts, int max) {
        List<String> p = parts.size() > max ? parts.subList(0, max) : parts;
        if (p.isEmpty()) return "bazı sınıflarda";
        if (p.size() == 1) return p.get(0);
        return String.join(", ", p.subList(0, p.size() - 1)) + " ve " + p.get(p.size() - 1);
    }

    /** Yuvarlanmış tutar: "3.260 TL" (onluk), 100'ün altı birlik. */
    static String money(double v) {
        double r = v >= 100 ? Math.round(v / 10.0) * 10.0 : Math.round(v);
        return BuyerFairCompare.kg(r) + " TL";
    }
}
