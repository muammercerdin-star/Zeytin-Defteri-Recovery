package com.zeytindefteri.app;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tezgah Modu'nun saf (Android'siz) hesabı.
 *
 * Tezgahta zeytin önce elekten geçer; sonra her numaranın kasaları kantara konur. Bir tartımda birkaç kasa
 * üst üste tartılabilir: "10 kasa · 258 kg". Her kasa için dara (varsayılan 2 kg) düşülür: net = kilo − kasa × dara.
 * Numara toplamları × fiyat = tutar. Yarım kalan tezgah {@link #encode()} ile saklanır, {@link #decode} ile geri gelir.
 */
public final class TezgahModel {
    public static final String[] GRADES = {"DOUBLE", "1", "2", "3", "4", "YAGLIK"};
    public static final String[] LABELS = {"Double", "1 Numara", "2 Numara", "3 Numara", "4 Numara", "Yağlık"};
    public static final String[] SHORT = {"Double", "1", "2", "3", "4", "Yağlık"};
    public static final double DEFAULT_TARE = 2.0;
    public static final double MAX_TARE = 10.0;
    public static final int MAX_KASA = 200;
    public static final double MAX_KG = 20000;

    private static final Locale TR = new Locale("tr", "TR");

    /** Bir kantar tartımı: hangi numara, kaç kasa, kantarda okunan kilo. */
    public static final class Weighing {
        public final int grade;
        public final int kasa;
        public final double kg;

        public Weighing(int grade, int kasa, double kg) {
            this.grade = grade;
            this.kasa = kasa;
            this.kg = kg;
        }
    }

    private final List<Weighing> items = new ArrayList<>();
    private final double[] prices = new double[GRADES.length];
    private double tare = DEFAULT_TARE;

    // ------------------------------------------------------------------ kurallar

    /** Net kilo: kantar − kasa × dara (eksiye düşmez). */
    public static double net(int kasa, double kg, double tare) {
        return Math.max(0, kg - Math.max(0, kasa) * Math.max(0, tare));
    }

    /** Tartım eklenebilir mi? Hata yoksa boş metin. */
    public static String addError(int kasa, double kg, double tare) {
        if (!(kg > 0)) return "Kantardaki kiloyu yaz.";
        if (kg > MAX_KG) return "Kilo çok büyük görünüyor; kontrol et.";
        if (kasa < 0 || kasa > MAX_KASA) return "Kasa sayısı 0–" + MAX_KASA + " arası olmalı.";
        if (kasa > 0 && kg <= kasa * tare) {
            return kasa + " kasanın darası " + kg(kasa * tare) + "; kantar kilosu bundan büyük olmalı.";
        }
        return "";
    }

    /** Bir kasaya sığan en fazla zeytin (kg). Kasa başı kantar kilosu bunu + darayı geçerse uyarılır. */
    public static final double MAX_NET_PER_KASA = 30;
    /** Tipik dolu kasa (kg zeytin); önerilen kasa sayısı hesabında kullanılır. */
    public static final double TYPICAL_NET_PER_KASA = 25;

    /** Birden çok kasada, sonuncusu hariç her kasa en az bu kadar dolu sayılır (son kasa yarım olabilir). */
    public static final double MIN_FULL_NET_PER_KASA = 20;

    /**
     * Kasa sayısı kiloya uymuyorsa önerilen kasa sayısı; uyuyorsa 0 (dökme tartımda da 0).
     * Fazla: kasa başına kantar &gt; 30 kg + dara (1 kasa 38 kg → 2).
     * Az: birden çok kasada kilo, (kasa − 1) dolu kasaya bile yetmiyor (2 kasa 16 kg → 1).
     */
    public static int suggestedKasa(int kasa, double kg, double tare) {
        if (kasa <= 0 || !(kg > 0)) return 0;
        double t = Math.max(0, tare);
        double maxGross = MAX_NET_PER_KASA + t;
        int typical = (int) Math.round(kg / (TYPICAL_NET_PER_KASA + t));
        if (kg / kasa > maxGross + 1e-9) {
            int fit = (int) Math.ceil(kg / maxGross - 1e-9);
            return Math.min(MAX_KASA, Math.max(fit, typical));
        }
        if (kasa > 1 && kg < (kasa - 1) * (MIN_FULL_NET_PER_KASA + t) - 1e-9) {
            int fit = (int) Math.ceil(kg / maxGross - 1e-9);
            int s = Math.max(1, Math.max(fit, typical));
            return s < kasa ? s : 0;
        }
        return 0;
    }

    /** Kasa çok mu (kiloya göre az kilo) yoksa az mı? true: kasa sayısı fazla girilmiş. */
    public static boolean tooManyKasa(int kasa, double kg, double tare) {
        int s = suggestedKasa(kasa, kg, tare);
        return s > 0 && s < kasa;
    }

    /** Kasa uyarısı metni; sorun yoksa boş. */
    public static String kasaWarning(int kasa, double kg, double tare) {
        int s = suggestedKasa(kasa, kg, tare);
        if (s == 0) return "";
        if (s < kasa) {
            return kasa + " kasa için " + kg(kg) + " az: kasa başına " + kg(kg / kasa) + " düşüyor (bir kasa 25–30 kg alır). "
                    + "Kasa sayısı " + s + " olmasın?";
        }
        return "Kasa başına " + kg(kg / kasa) + " düşüyor; bir kasaya en fazla " + kg(MAX_NET_PER_KASA)
                + " sığar. Kasa sayısı " + s + " olmasın?";
    }

    /** Ekrandaki kısa uyarı: "⚠ 1 kasaya 38 kg fazla. 2 kasa mı?" / "⚠ 2 kasa için 16 kg az. 1 kasa mı?" */
    public static String kasaHint(int kasa, double kg, double tare) {
        int s = suggestedKasa(kasa, kg, tare);
        if (s == 0) return "";
        return s < kasa ? "⚠ " + kasa + " kasa için " + kg(kg) + " az. " + s + " kasa mı?"
                : "⚠ " + kasa + " kasaya " + kg(kg) + " fazla. " + s + " kasa mı?";
    }

    // ------------------------------------------------------------------ düzenleme

    public boolean add(int grade, int kasa, double kg) {
        if (grade < 0 || grade >= GRADES.length || !addError(kasa, kg, tare).isEmpty()) return false;
        items.add(new Weighing(grade, kasa, round1(kg)));
        return true;
    }

    public boolean remove(int index) {
        if (index < 0 || index >= items.size()) return false;
        items.remove(index);
        return true;
    }

    public void clear() { items.clear(); }

    public List<Weighing> items() { return new ArrayList<>(items); }

    public boolean isEmpty() { return items.isEmpty(); }

    public double tare() { return tare; }

    public void setTare(double value) {
        if (value >= 0 && value <= MAX_TARE) tare = round1(value);
    }

    public double price(int grade) { return prices[grade]; }

    public void setPrice(int grade, double value) {
        if (grade >= 0 && grade < prices.length && value >= 0) prices[grade] = value;
    }

    // ------------------------------------------------------------------ toplamlar

    public double gradeNet(int grade) {
        double sum = 0;
        for (Weighing w : items) if (w.grade == grade) sum += net(w.kasa, w.kg, tare);
        return round1(sum);
    }

    public int gradeKasa(int grade) {
        int sum = 0;
        for (Weighing w : items) if (w.grade == grade) sum += w.kasa;
        return sum;
    }

    public int gradeCount(int grade) {
        int n = 0;
        for (Weighing w : items) if (w.grade == grade) n++;
        return n;
    }

    public double gradeAmount(int grade) { return gradeNet(grade) * prices[grade]; }

    public double totalNet() {
        double sum = 0;
        for (int g = 0; g < GRADES.length; g++) sum += gradeNet(g);
        return round1(sum);
    }

    public double totalGross() {
        double sum = 0;
        for (Weighing w : items) sum += w.kg;
        return round1(sum);
    }

    public int totalKasa() {
        int sum = 0;
        for (Weighing w : items) sum += w.kasa;
        return sum;
    }

    public double totalTare() { return round1(totalKasa() * tare); }

    public double total() {
        double sum = 0;
        for (int g = 0; g < GRADES.length; g++) sum += gradeAmount(g);
        return Math.round(sum * 100) / 100.0;
    }

    /** Ortalama kilo fiyatı: toplam tutar / net kilo (tartım yoksa 0). */
    public double averagePrice() {
        double n = totalNet();
        return n <= 0 ? 0 : total() / n;
    }

    /** Numara payı (yüzde, 0–100). */
    public double share(int grade) {
        double all = totalNet();
        return all <= 0 ? 0 : gradeNet(grade) * 100.0 / all;
    }

    /** Elek kaydı için numara sırasıyla net kilolar. */
    public double[] netArray() {
        double[] out = new double[GRADES.length];
        for (int g = 0; g < out.length; g++) out[g] = gradeNet(g);
        return out;
    }

    public double[] priceArray() { return prices.clone(); }

    /** Fiyatı girilmemiş ama kilosu olan numaralar (bitirmeden önce uyarı için). */
    public List<String> missingPrices() {
        List<String> out = new ArrayList<>();
        for (int g = 0; g < GRADES.length; g++) if (gradeNet(g) > 0 && prices[g] <= 0) out.add(LABELS[g]);
        return out;
    }

    /** Tartımı olup fiyatı girilmemiş ilk numara; yoksa -1. */
    public int firstMissingPrice() {
        for (int g = 0; g < GRADES.length; g++) if (gradeNet(g) > 0 && prices[g] <= 0) return g;
        return -1;
    }

    // ------------------------------------------------------------------ metinler

    /** Tartım satırı: "10 kasa · 258 kg → net 238 kg". */
    public String line(Weighing w) {
        String head = w.kasa > 0 ? w.kasa + " kasa · " + kg(w.kg) : "Dökme · " + kg(w.kg);
        return head + "  →  net " + kg(net(w.kasa, w.kg, tare));
    }

    /**
     * WhatsApp'a gönderilecek fiş metni.
     * @param title "Zeytin alım fişi" ya da "Zeytin satış fişi"
     * @param partyLabel "Satan" / "Alıcı"
     */
    public String receipt(String title, String partyLabel, String party, String date, double paid) {
        return receipt(title, partyLabel, party, date, paid, 0);
    }

    /**
     * previousDebt: aynı üreticinin bu fişten önceki alımlarından kalan borç. Varsa fişin sonunda
     * "Önceki borç" ve bu fişle birlikte "GÜNCEL BORÇ" yazılır.
     */
    public String receipt(String title, String partyLabel, String party, String date, double paid, double previousDebt) {
        StringBuilder sb = new StringBuilder();
        sb.append("🫒 ").append(title).append('\n');
        if (date != null && !date.isEmpty()) sb.append("Tarih: ").append(date).append('\n');
        if (party != null && !party.trim().isEmpty()) sb.append(partyLabel).append(": ").append(party.trim()).append('\n');
        sb.append('\n');
        for (int g = 0; g < GRADES.length; g++) {
            double n = gradeNet(g);
            if (n <= 0) continue;
            sb.append(LABELS[g]).append(": ").append(kg(n));
            if (prices[g] > 0) sb.append(" × ").append(money(prices[g])).append(" = ").append(tl(gradeAmount(g)));
            sb.append('\n');
        }
        sb.append('\n');
        sb.append("Kasa: ").append(totalKasa()).append(" · Brüt: ").append(kg(totalGross()))
                .append(" · Dara: ").append(kg(totalTare())).append('\n');
        sb.append("Net: ").append(kg(totalNet())).append('\n');
        sb.append("TOPLAM: ").append(tl(total())).append('\n');
        if (averagePrice() > 0) sb.append("Ortalama: ").append(money(averagePrice())).append("/kg").append('\n');
        double t = total();
        double debt = Math.max(0, t - Math.max(0, paid));
        if (paid > 0.009 && paid < t - 0.009) {
            sb.append("Ödenen: ").append(tl(paid)).append(" · Borç: ").append(tl(debt)).append('\n');
        } else if (paid >= t - 0.009 && t > 0) {
            sb.append("Ödeme: tamamı ödendi\n");
        } else {
            sb.append("Ödeme yapılmadı · Borç: ").append(tl(debt)).append('\n');
        }
        if (previousDebt > 0.5) {
            sb.append("Önceki borç: ").append(tl(previousDebt)).append('\n');
            sb.append("GÜNCEL BORÇ: ").append(tl(previousDebt + debt)).append('\n');
        }
        sb.append("\nBilgi fişidir; müstahsil makbuzu ya da fatura yerine geçmez.");
        sb.append("\nZeytin Defteri ile hazırlandı");
        return sb.toString();
    }

    // ------------------------------------------------------------------ saklama

    /** "v1|dara|p0;p1;…|g,kasa,kg;g,kasa,kg" — yarım kalan tezgah için. */
    public String encode() {
        StringBuilder sb = new StringBuilder("v1|").append(tare).append('|');
        for (int g = 0; g < prices.length; g++) {
            if (g > 0) sb.append(';');
            sb.append(prices[g]);
        }
        sb.append('|');
        for (int i = 0; i < items.size(); i++) {
            Weighing w = items.get(i);
            if (i > 0) sb.append(';');
            sb.append(w.grade).append(',').append(w.kasa).append(',').append(w.kg);
        }
        return sb.toString();
    }

    /** Bozuk/eski metinde boş tezgah döner (hata atmaz). */
    public static TezgahModel decode(String text) {
        TezgahModel m = new TezgahModel();
        if (text == null || !text.startsWith("v1|")) return m;
        try {
            String[] parts = text.split("\\|", -1);
            if (parts.length < 4) return m;
            m.setTare(Double.parseDouble(parts[1]));
            String[] ps = parts[2].split(";");
            for (int g = 0; g < ps.length && g < m.prices.length; g++) {
                if (!ps[g].isEmpty()) m.setPrice(g, Double.parseDouble(ps[g]));
            }
            if (!parts[3].isEmpty()) {
                for (String row : parts[3].split(";")) {
                    String[] f = row.split(",");
                    if (f.length != 3) continue;
                    int grade = Integer.parseInt(f[0]);
                    int kasa = Integer.parseInt(f[1]);
                    double kg = Double.parseDouble(f[2]);
                    if (grade >= 0 && grade < GRADES.length && kasa >= 0 && kg > 0) {
                        m.items.add(new Weighing(grade, kasa, kg));
                    }
                }
            }
        } catch (RuntimeException e) {
            return new TezgahModel();
        }
        return m;
    }

    /**
     * WhatsApp bağlantısı için telefon: "0532 123 45 67" → "905321234567". Türkiye cep numarası
     * değilse ve ülke kodu da yoksa boş döner (o zaman paylaşım menüsü açılır).
     */
    public static String waPhone(String raw) {
        if (raw == null) return "";
        StringBuilder d = new StringBuilder();
        for (char c : raw.toCharArray()) if (c >= '0' && c <= '9') d.append(c);
        String s = d.toString();
        if (s.startsWith("00")) s = s.substring(2);
        if (s.length() == 11 && s.startsWith("05")) return "9" + s;
        if (s.length() == 10 && s.startsWith("5")) return "90" + s;
        if (s.length() == 12 && s.startsWith("905")) return s;
        if (raw.trim().startsWith("+") && s.length() >= 8 && s.length() <= 15) return s;
        return "";
    }

    // ------------------------------------------------------------------ biçim

    /** "1.234,5" / "1234.5" / "258" → sayı; okunamazsa 0. */
    public static double parse(String raw) {
        if (raw == null) return 0;
        String s = raw.trim().replace(" ", "").replace("kg", "").replace("₺", "").replace("TL", "");
        if (s.isEmpty()) return 0;
        int comma = s.lastIndexOf(','), dot = s.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            // Hangisi sonda ise o ondalık ayırıcıdır.
            if (comma > dot) s = s.replace(".", "").replace(',', '.');
            else s = s.replace(",", "");
        } else if (comma >= 0) {
            s = s.replace(',', '.');
        } else if (dot >= 0 && s.indexOf('.') != dot) {
            s = s.replace(".", "");
        } else if (dot >= 0 && s.length() - dot - 1 == 3 && dot > 0) {
            // "1.250" Türkçede bin ayırıcısıdır.
            s = s.replace(".", "");
        }
        try {
            double v = Double.parseDouble(s);
            return Double.isNaN(v) || Double.isInfinite(v) ? 0 : v;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String kg(double v) {
        NumberFormat nf = NumberFormat.getNumberInstance(TR);
        nf.setMaximumFractionDigits(1);
        nf.setMinimumFractionDigits(0);
        return nf.format(v) + " kg";
    }

    public static String tl(double v) {
        NumberFormat nf = NumberFormat.getNumberInstance(TR);
        nf.setMaximumFractionDigits(0);
        return nf.format(Math.round(v)) + " ₺";
    }

    /** Kilo fiyatı: "92,5 ₺". */
    public static String money(double v) {
        NumberFormat nf = NumberFormat.getNumberInstance(TR);
        nf.setMaximumFractionDigits(2);
        nf.setMinimumFractionDigits(0);
        nf.setRoundingMode(java.math.RoundingMode.HALF_UP);
        return nf.format(v) + " ₺";
    }

    public static String percent(double v) {
        return "%" + Math.round(v);
    }

    private static double round1(double v) { return Math.round(v * 10) / 10.0; }
}
