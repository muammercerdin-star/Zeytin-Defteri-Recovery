package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Alıcıları ADİL karşılaştırma: bir alıcıya yapılan her satış, yalnız kendine benzeyen satışlarla kıyaslanır.
 *
 * Neden: sezon toplamıyla karşılaştırma iki şeyi karıştırıyordu.
 *  - Elek dağılımı zeytinin BAHÇESİNE bağlı (Hacıaliler ile Girelli'nin iri/ufak oranı zaten farklı).
 *  - Sınıf fiyatı TARİHE bağlı (piyasa günden güne oynar; ekim başı ile kasım aynı kefeye girmemeli).
 *
 * Kurallar:
 *  - Fiyat (sınıf başına TL/kg): alıcının satışı, ±{@link #PRICE_DAYS} gün içinde BAŞKA alıcılara yapılan
 *    satışlarla, aynı sınıfta karşılaştırılır (bahçe fark etmez; sınıf fiyatı bahçeye pek bağlı değil).
 *  - Elek dağılımı ve kg başı: alıcının satışı, AYNI BAHÇEDEN ±{@link #ELEK_DAYS} gün içinde başka alıcılara
 *    yapılan satışlarla karşılaştırılır. "Aynı zeytine kim kilo başı ne verdi" sorusunun cevabı budur.
 *  - Her eşleşme alıcının satış kilosuyla ağırlıklandırılır. Eşleşme yoksa sonuç yoktur (uydurma hüküm yok).
 *
 * Saf sınıf (Android yok): test edilir.
 */
public final class BuyerFairCompare {
    public static final int PRICE_DAYS = 3, ELEK_DAYS = 7;
    /** Hüküm için iki tarafta da eşleşen en az kilo; azında fark tesadüf olabilir. */
    public static final double MIN_KG = 50;
    static final int GRADES = 6;

    private BuyerFairCompare() {}

    /** Bir satış: sınıf kiloları ve satır tutarları (sınıfsız/karışık satırlar dahil edilmez). */
    public static final class Sale {
        public long buyerId, gardenId;
        public int day;                      // 1970'ten gün (epochDay)
        public final double[] kg = new double[GRADES], tl = new double[GRADES];

        public double totalKg() { double t = 0; for (double v : kg) t += v; return t; }
        public double totalTl() { double t = 0; for (double v : tl) t += v; return t; }
        double price(int g) { return kg[g] > 0 && tl[g] > 0 ? tl[g] / kg[g] : 0; }
    }

    /** Sınıf fiyatı karşılaştırması (bir satır = bir sınıf). */
    public static final class PriceRow {
        public int grade;
        /** Eşleşen satışlarda alıcının ve aynı günlerdeki diğer alıcıların kg ağırlıklı ortalaması; eşleşme yoksa 0. */
        public double buyerAvg, othersAvg;
        /** Eşleşme olmasa da alıcının sezon ortalaması (bilgi için). */
        public double buyerSeasonAvg;
        public int matchedSales;
        public double matchedKg;
        public boolean matched() { return matchedSales > 0 && buyerAvg > 0 && othersAvg > 0; }
    }

    public static final class Prices {
        public final List<PriceRow> rows = new ArrayList<>();
        /** Fiyatı en az bir sınıfta eşleşen alıcı satışı sayısı. */
        public int matchedSales;
    }

    /** Elek dağılımı ve kg başı (aynı bahçe, yakın tarih). */
    public static final class Elek {
        /** Eşleşen satışlarda alıcının ve diğerlerinin sınıf yüzdeleri (kg ağırlıklı); eşleşme yoksa 0. */
        public final double[] buyerShare = new double[GRADES], othersShare = new double[GRADES];
        public double buyerPerKg, othersPerKg;
        public int matchedSales;
        public double matchedKg, othersKg;

        public boolean enough() { return matchedSales > 0 && matchedKg >= MIN_KG && othersKg >= MIN_KG; }
        public double topDiff() { return buyerShare[0] + buyerShare[1] - othersShare[0] - othersShare[1]; }
        public double perKgDiff() { return buyerPerKg - othersPerKg; }
    }

    public static Prices prices(List<Sale> all, long buyerId) {
        Prices out = new Prices();
        double[] bKg = new double[GRADES], bTl = new double[GRADES], oWeighted = new double[GRADES];
        double[] seasonKg = new double[GRADES], seasonTl = new double[GRADES];
        int[] count = new int[GRADES];
        for (Sale t : all) {
            if (t.buyerId != buyerId) continue;
            boolean any = false;
            for (int g = 0; g < GRADES; g++) {
                if (t.price(g) <= 0) continue;
                seasonKg[g] += t.kg[g];
                seasonTl[g] += t.tl[g];
                double oKg = 0, oTl = 0;
                for (Sale o : all) {
                    if (o.buyerId == buyerId || Math.abs(o.day - t.day) > PRICE_DAYS || o.price(g) <= 0) continue;
                    oKg += o.kg[g];
                    oTl += o.tl[g];
                }
                if (oKg <= 0) continue;
                bKg[g] += t.kg[g];
                bTl[g] += t.tl[g];
                oWeighted[g] += t.kg[g] * (oTl / oKg);   // aynı günlerin diğer-alıcı fiyatı, bu satışın kilosuyla
                count[g]++;
                any = true;
            }
            if (any) out.matchedSales++;
        }
        for (int g = 0; g < GRADES; g++) {
            if (seasonKg[g] <= 0) continue;
            PriceRow r = new PriceRow();
            r.grade = g;
            r.buyerSeasonAvg = seasonTl[g] / seasonKg[g];
            if (bKg[g] > 0) {
                r.buyerAvg = bTl[g] / bKg[g];
                r.othersAvg = oWeighted[g] / bKg[g];
                r.matchedSales = count[g];
                r.matchedKg = bKg[g];
            }
            out.rows.add(r);
        }
        return out;
    }

    public static Elek elek(List<Sale> all, long buyerId) {
        Elek out = new Elek();
        double[] bKg = new double[GRADES], oShareWeighted = new double[GRADES];
        double bTl = 0, oPerKgWeighted = 0, weight = 0;
        for (Sale t : all) {
            double tKg = t.totalKg();
            if (t.buyerId != buyerId || tKg <= 0 || t.gardenId <= 0) continue;
            double[] oKg = new double[GRADES];
            double oTotal = 0, oTl = 0;
            for (Sale o : all) {
                if (o.buyerId == buyerId || o.gardenId != t.gardenId || Math.abs(o.day - t.day) > ELEK_DAYS) continue;
                double k = o.totalKg();
                if (k <= 0) continue;
                for (int g = 0; g < GRADES; g++) oKg[g] += o.kg[g];
                oTotal += k;
                oTl += o.totalTl();
            }
            if (oTotal <= 0) continue;
            out.matchedSales++;
            out.othersKg += oTotal;
            weight += tKg;
            for (int g = 0; g < GRADES; g++) {
                bKg[g] += t.kg[g];
                oShareWeighted[g] += tKg * (oKg[g] / oTotal);
            }
            bTl += t.totalTl();
            oPerKgWeighted += tKg * (oTl / oTotal);
        }
        out.matchedKg = weight;
        if (weight > 0) {
            for (int g = 0; g < GRADES; g++) {
                out.buyerShare[g] = bKg[g] * 100.0 / weight;
                out.othersShare[g] = oShareWeighted[g] * 100.0 / weight;
            }
            out.buyerPerKg = bTl / weight;
            out.othersPerKg = oPerKgWeighted / weight;
        }
        return out;
    }

    /** Elek kartının hüküm cümlesi; eşleşme/veri azsa açıklayıcı cümle. */
    public static String elekVerdict(Elek e, String buyerName) {
        if (e == null || e.matchedSales == 0) {
            return "Adil karşılaştırma için " + buyerName + "'e ve başka bir alıcıya aynı bahçeden, aynı hafta satış lazım.";
        }
        if (!e.enough()) {
            return "Eşleşen satış az (" + kg(e.matchedKg) + " kg). Karşılaştırma için iki tarafta da en az "
                    + Math.round(MIN_KG) + " kg gerekir.";
        }
        StringBuilder s = new StringBuilder();
        double perKg = e.perKgDiff();
        if (Math.abs(perKg) < 0.5) s.append("Aynı zeytine kilo başı diğer alıcılarınla benzer verdi.");
        else s.append("Aynı zeytine kilo başı ").append(BuyerDirectory.oneDecimal(Math.abs(perKg)))
                .append(" TL ").append(perKg > 0 ? "fazla" : "az").append(" verdi.");
        long points = Math.round(Math.abs(e.topDiff()));
        if (points >= 3) {
            s.append(" Double + 1 Numara oranı ").append(points).append(" puan ")
                    .append(e.topDiff() > 0 ? "yüksek (eleği iri çıkarıyor)." : "düşük (eleği ufak çıkarıyor).");
        }
        return s.toString();
    }

    /** "Aynı bahçe, ±7 gün · 3 satış eşleşti · 320 kg". */
    public static String elekBasis(Elek e) {
        if (e == null || e.matchedSales == 0) return "Aynı bahçe, ±" + ELEK_DAYS + " gün · eşleşen satış yok";
        return "Aynı bahçe, ±" + ELEK_DAYS + " gün · " + e.matchedSales + " satış eşleşti · " + kg(e.matchedKg) + " kg";
    }

    public static String priceBasis(Prices p) {
        if (p == null || p.matchedSales == 0) return "Aynı günlerde (±" + PRICE_DAYS + " gün) başka alıcıya satış yok";
        return "Aynı günlerde (±" + PRICE_DAYS + " gün) yaptığın satışlarla · " + p.matchedSales + " satış eşleşti";
    }

    /** "2026-10-19" → epochDay; okunamazsa Integer.MIN_VALUE. java.time minSdk 24'te yok, bu yüzden elle. */
    public static int epochDay(String iso) {
        if (iso == null || iso.length() < 10 || iso.charAt(4) != '-' || iso.charAt(7) != '-') return Integer.MIN_VALUE;
        try {
            int y = Integer.parseInt(iso.substring(0, 4)), m = Integer.parseInt(iso.substring(5, 7)),
                    d = Integer.parseInt(iso.substring(8, 10));
            if (m < 1 || m > 12 || d < 1 || d > 31) return Integer.MIN_VALUE;
            // Howard Hinnant'ın days_from_civil algoritması.
            y -= m <= 2 ? 1 : 0;
            int era = (y >= 0 ? y : y - 399) / 400;
            int yoe = y - era * 400;
            int doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
            int doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
            return era * 146097 + doe - 719468;
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    static String kg(double v) {
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(new java.util.Locale("tr", "TR"));
        nf.setMaximumFractionDigits(0);
        return nf.format(Math.max(0, v));
    }
}
