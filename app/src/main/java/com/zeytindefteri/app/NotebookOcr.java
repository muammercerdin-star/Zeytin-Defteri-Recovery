package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Defter fotoğrafından okunan yazıyı (telefonda, ML Kit) elek satırlarına çevirir. Saf Java, Android'siz.
 *
 * Beklenen satır: "[sınıf] kilo x fiyat [=] tutar", örn. "1- 43 x 100 4.300", "Y-5 x20 = 100".
 * Sınıf: 0 / D / Double = Double, 1–4 = Numara, Y / Yağlık = Yağlık. Sınıf yazılmamışsa sıradaki sınıf verilir.
 * Her satır kendi içinde denetlenir: kilo × fiyat, defterde yazan tutarı tutuyor mu? El yazısı yanlış okunabildiği için
 * sonuç hiçbir zaman doğrudan kaydedilmez; kullanıcı kontrol ekranında görür ve düzeltir.
 */
public final class NotebookOcr {
    public static final int GRADES = 6;
    public static final int OK = 0, MISMATCH = 1, UNCHECKED = 2, EMPTY = 3;

    private NotebookOcr() {}

    /** ML Kit'ten gelen bir yazı parçası ve ekrandaki yeri. */
    public static final class Piece {
        public final String text;
        public final float left, top, bottom;
        public Piece(String text, float left, float top, float bottom) {
            this.text = text == null ? "" : text; this.left = left; this.top = top; this.bottom = bottom;
        }
        float centerY() { return (top + bottom) / 2f; }
        float height() { return Math.max(1f, bottom - top); }
    }

    public static final class Row {
        public double kg, price, amount;
        public boolean hasKg, hasPrice, hasAmount;
        public String raw = "";

        public int status() { return NotebookOcr.status(hasKg ? kg : 0, hasPrice ? price : 0, hasAmount, amount); }

        /** Tutara göre kilo önerisi (yarım kiloya yuvarlanınca tam tutuyorsa); yoksa NaN. */
        public double suggestedKg() {
            if (!hasAmount || !hasPrice || price <= 0 || status() != MISMATCH) return Double.NaN;
            double k = amount / price, r = Math.round(k * 2) / 2.0;
            return r > 0 && Math.abs(k - r) < 0.011 ? r : Double.NaN;
        }
    }

    public static final class Result {
        public final Row[] rows = new Row[GRADES];
        public double totalKg, totalAmount;
        public boolean hasTotalKg, hasTotalAmount;
        public final List<String> lines = new ArrayList<>();

        public int filled() { int n = 0; for (Row r : rows) if (r != null && r.hasKg) n++; return n; }

        public double sumKg() { double s = 0; for (Row r : rows) if (r != null && r.hasKg) s += r.kg; return s; }

        /** Okunan kilo × fiyatların toplamı. */
        public double sumAmount() {
            double s = 0;
            for (Row r : rows) if (r != null && r.hasKg && r.hasPrice) s += r.kg * r.price;
            return s;
        }
    }

    /** Durum: kilo × fiyat, defterdeki tutarı tutuyor mu? */
    public static int status(double kg, double price, boolean hasAmount, double amount) {
        if (kg <= 0 && (!hasAmount || amount <= 0)) return EMPTY;
        if (kg <= 0 || price <= 0 || !hasAmount) return UNCHECKED;
        return close(kg * price, amount) ? OK : MISMATCH;
    }

    static boolean close(double a, double b) { return Math.abs(a - b) <= Math.max(1.0, Math.abs(b) * 0.005); }

    // ------------------------------------------------------------------ satırlar

    /** Parçaları ekrandaki yüksekliklerine göre satırlara dizer (soldan sağa birleştirir). */
    public static List<String> lines(List<Piece> pieces) {
        List<String> out = new ArrayList<>();
        if (pieces == null || pieces.isEmpty()) return out;
        List<Piece> ps = new ArrayList<>(pieces);
        Collections.sort(ps, (a, b) -> Float.compare(a.centerY(), b.centerY()));
        List<List<Piece>> rows = new ArrayList<>();
        List<Piece> cur = null;
        float curY = 0, curH = 0;
        for (Piece p : ps) {
            if (p.text.trim().isEmpty()) continue;
            if (cur != null && Math.abs(p.centerY() - curY) < 0.55f * Math.max(curH, p.height())) {
                cur.add(p);
                curY = (curY * (cur.size() - 1) + p.centerY()) / cur.size();
                curH = Math.max(curH, p.height());
            } else {
                cur = new ArrayList<>();
                cur.add(p);
                rows.add(cur);
                curY = p.centerY();
                curH = p.height();
            }
        }
        for (List<Piece> r : rows) {
            Collections.sort(r, (a, b) -> Float.compare(a.left, b.left));
            StringBuilder b = new StringBuilder();
            for (Piece p : r) { if (b.length() > 0) b.append(' '); b.append(p.text.trim()); }
            out.add(b.toString());
        }
        return out;
    }

    // ------------------------------------------------------------------ çözümleme

    private static final Pattern LABEL = Pattern.compile(
            "^\\s*(double|dbl|dub|d|yağlık|yaglik|yağ|yag|y|[0-4])\\s*(?:\\.?\\s*(?:numara|nu|no|n)\\b)?\\.?\\s*[-–—_:.)/|]+\\s*");
    private static final Pattern LABEL_WORD = Pattern.compile(
            "^\\s*(double|dbl|yağlık|yaglik|[1-4]\\s*(?:numara|no))\\b\\s*");
    private static final String NUM = "(\\d+(?:[.,]\\d+)*)";
    private static final Pattern CORE = Pattern.compile(
            NUM + "\\s*(?:kg)?\\s*x\\s*" + NUM + "(?:\\s*(?:tl|₺))?\\s*(?:[=:]+\\s*)?(?:" + NUM + ")?");
    private static final Pattern ANY_NUM = Pattern.compile(NUM);
    /** Yalnız kilo yazılan satır: "0 | - 15 kg", "2-25 kp", "y - 5". Birim el yazısında kp/ug/4p diye okunabilir. */
    private static final Pattern KG_ONLY = Pattern.compile("^[\\s\\-–—_|:]*" + NUM + "\\s*([a-zçğışöü0-9]{0,3})\\.?\\s*$");
    private static final Pattern UNIT = Pattern.compile("k[gpqy9ğ]?|[u4][gpqy9ğ]|kilo|kğ");

    public static Result parse(List<String> rawLines) {
        Result res = new Result();
        if (rawLines == null) return res;
        int next = 0;
        List<String> rest = new ArrayList<>();
        boolean seenRow = false;
        for (String raw : rawLines) {
            if (raw == null) continue;
            res.lines.add(raw);
            String s = normalize(raw);
            int grade = -1;
            Matcher lm = LABEL.matcher(s);
            Matcher lw = LABEL_WORD.matcher(s);
            String body = s;
            if (lm.find() && !decimalNotLabel(s, lm) && looksLikeAnyRow(s.substring(lm.end()))) {
                grade = gradeOf(lm.group(1));
                body = s.substring(lm.end());
            } else if (lw.find() && looksLikeAnyRow(s.substring(lw.end()))) {
                grade = gradeOf(lw.group(1));
                body = s.substring(lw.end());
            }
            Matcher cm = CORE.matcher(body);
            Row row = new Row();
            row.raw = raw.trim();
            if (cm.find()) {
                pick(row, cm.group(1), cm.group(2), cm.group(3));
            } else {
                // Yalnız kilo: sınıf yazılmışsa ya da kilo birimi varsa satır sayılır (tarih/telefon karışmasın).
                Matcher km = KG_ONLY.matcher(body);
                boolean unit = km.matches() && UNIT.matcher(km.group(2)).matches();
                boolean bare = km.matches() && km.group(2).isEmpty();
                if (!km.matches() || !(unit || (grade >= 0 && (bare || km.group(2).length() <= 3)))) {
                    if (seenRow) rest.add(s);
                    continue;
                }
                double[] k = candidates(km.group(1));
                if (k.length == 0 || Double.isNaN(k[0]) || k[0] <= 0 || k[0] > 100000) {
                    if (seenRow) rest.add(s);
                    continue;
                }
                row.kg = k[0];
                row.hasKg = true;
            }
            if (grade < 0 || grade >= GRADES || res.rows[grade] != null) {
                while (next < GRADES && res.rows[next] != null) next++;
                if (next >= GRADES) continue;
                grade = next;
            }
            res.rows[grade] = row;
            next = Math.max(next, grade + 1);
            seenRow = true;
        }
        readTotals(res, rest);
        return res;
    }

    private static boolean looksLikeRowAfter(String body) { return CORE.matcher(body).find(); }

    private static boolean looksLikeAnyRow(String body) { return looksLikeRowAfter(body) || KG_ONLY.matcher(body).matches(); }

    /** "1.5 x 100": baştaki 1 sınıf değil, 1,5 kilodur (ayıraçtan hemen sonra rakam geliyor). */
    private static boolean decimalNotLabel(String s, Matcher lm) {
        String m = lm.group(0);
        char sep = m.trim().isEmpty() ? ' ' : m.trim().charAt(m.trim().length() - 1);
        return (sep == '.' || sep == ',') && !m.endsWith(" ")
                && lm.end() < s.length() && Character.isDigit(s.charAt(lm.end()));
    }

    static int gradeOf(String label) {
        String l = label.replaceAll("\\s+", "");
        if (l.startsWith("d") || l.equals("0")) return 0;
        if (l.startsWith("y")) return 5;
        char c = l.charAt(0);
        if (c >= '1' && c <= '4') return c - '0';
        return -1;
    }

    /** Okumada sık karışan harfleri sayıya çevirir; × ve * işaretlerini x yapar. */
    static String normalize(String raw) {
        String s = raw.toLowerCase(Locale.forLanguageTag("tr-TR"));
        s = s.replace('×', 'x').replace('*', 'x').replace('✕', 'x').replace('х', 'x');
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean prevD = b.length() > 0 && isDigitish(b.charAt(b.length() - 1));
            boolean nextD = i + 1 < s.length() && (isDigitish(s.charAt(i + 1))
                    || (i + 2 < s.length() && "oölıi".indexOf(s.charAt(i + 1)) >= 0 && isDigitish(s.charAt(i + 2))));
            if ((prevD || nextD) && (c == 'o' || c == 'ö')) { b.append('0'); continue; }
            if ((prevD || nextD) && (c == 'l' || c == 'ı' || c == 'i')) { b.append('1'); continue; }
            b.append(c);
        }
        // "5 . 5" gibi boşluklu ayırıcılar
        return b.toString().replaceAll("(\\d)\\s*([.,])\\s*(\\d)", "$1$2$3");
    }

    private static boolean isDigitish(char c) { return c >= '0' && c <= '9'; }

    /** Bir sayının olası değerleri: ondalık okuma ve (ayıraçtan sonra 3 hane varsa) binlik okuma. */
    static double[] candidates(String t) {
        if (t == null) return new double[0];
        boolean thousandsShape = t.matches("\\d{1,3}([.,]\\d{3})+");
        double grouped = parseDigits(t.replaceAll("[.,]", ""));
        int last = Math.max(t.lastIndexOf('.'), t.lastIndexOf(','));
        double decimal;
        if (last < 0) decimal = parseDigits(t);
        else decimal = parseDigits(t.substring(0, last).replaceAll("[.,]", "") + "." + t.substring(last + 1));
        if (last < 0) return new double[]{decimal};
        return thousandsShape ? new double[]{decimal, grouped} : new double[]{decimal};
    }

    private static double parseDigits(String s) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return Double.NaN; }
    }

    /** Kilo/fiyat/tutar için, birbirini tutan okumayı seçer; tutmuyorsa en olası okumayı. */
    private static void pick(Row row, String kgT, String priceT, String amountT) {
        double[] ks = candidates(kgT), ps = candidates(priceT), as = candidates(amountT);
        row.hasKg = ks.length > 0 && !Double.isNaN(ks[0]);
        row.hasPrice = ps.length > 0 && !Double.isNaN(ps[0]);
        row.hasAmount = as.length > 0 && !Double.isNaN(as[0]);
        if (row.hasKg) row.kg = ks[0];
        if (row.hasPrice) row.price = ps[0];
        if (row.hasAmount) row.amount = as[as.length - 1]; // tutar: binlik okuma öncelikli
        if (!row.hasKg || !row.hasPrice || !row.hasAmount) return;
        for (int ai = as.length - 1; ai >= 0; ai--) for (double k : ks) for (double p : ps) {
            double a = as[ai]; // tutarda binlik okuma önce denenir
            if (k > 0 && p > 0 && close(k * p, a)) { row.kg = k; row.price = p; row.amount = a; return; }
        }
    }

    /** Satırlardan sonraki yazılardan toplam kilo ve toplam tutarı bulur. */
    private static void readTotals(Result res, List<String> rest) {
        double sumKg = res.sumKg();
        double sumAmt = 0;
        for (Row r : res.rows) if (r != null && r.hasAmount) sumAmt += r.amount;
        if (sumAmt <= 0) sumAmt = res.sumAmount();
        List<Double> nums = new ArrayList<>();
        for (String s : rest) {
            Matcher m = ANY_NUM.matcher(s);
            while (m.find()) for (double v : candidates(m.group(1))) if (!Double.isNaN(v)) nums.add(v);
        }
        if (nums.isEmpty()) return;
        for (double v : nums) {
            if (!res.hasTotalAmount && sumAmt > 0 && close(v, sumAmt)) { res.totalAmount = v; res.hasTotalAmount = true; }
            else if (!res.hasTotalKg && sumKg > 0 && Math.abs(v - sumKg) < 0.051) { res.totalKg = v; res.hasTotalKg = true; }
        }
        if (!res.hasTotalAmount) {
            double max = 0;
            for (double v : nums) max = Math.max(max, v);
            // Toplam tutar en az en büyük satır tutarından büyük olmalı; değilse toplam yazılmamış say.
            double biggest = 0;
            for (Row r : res.rows) if (r != null && r.hasAmount) biggest = Math.max(biggest, r.amount);
            if (biggest > 0 && max > biggest) { res.totalAmount = max; res.hasTotalAmount = true; }
        }
    }
}
