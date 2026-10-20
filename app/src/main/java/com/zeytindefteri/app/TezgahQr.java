package com.zeytindefteri.app;

import java.util.Locale;

/**
 * Tezgah fişinin QR metni: tüccar gösterir, üretici Elek ekranında okutur, form kendiliğinden dolar.
 *
 * Biçim (kısa tutulur ki QR seyrek ve kolay okunur olsun):
 *   ZD1|yyyy-MM-dd|alıcı adı|kgD,kg1,kg2,kg3,kg4,kgY|fD,f1,f2,f3,f4,fY|kasa|dara|ödenen
 * Kişisel bilgi olarak yalnız tüccarın görünen adı vardır; telefon numarası QR'a girmez.
 * Okurken her alan denetlenir; bozuk ya da başka bir QR'da null döner.
 */
public final class TezgahQr {
    static final String PREFIX = "ZD1";
    static final int MAX_LEN = 600;
    static final int MAX_NAME = 60;

    /** Okunan fiş. */
    public static final class Slip {
        public String date = "", buyer = "";
        public final double[] kg = new double[TezgahModel.GRADES.length];
        public final double[] price = new double[TezgahModel.GRADES.length];
        public int kasa;
        public double tare, paid;

        public double totalKg() {
            double s = 0;
            for (double v : kg) s += v;
            return Math.round(s * 10) / 10.0;
        }

        public double total() {
            double s = 0;
            for (int g = 0; g < kg.length; g++) s += kg[g] * price[g];
            return Math.round(s * 100) / 100.0;
        }

        /** ElekSaleModel tahsilat seçimi: tamamı / bir kısmı / henüz değil. */
        public int payChoice() {
            double t = total();
            if (paid <= 0.009) return ElekSaleModel.PAY_NONE;
            if (paid >= t - 0.009) return ElekSaleModel.PAY_ALL;
            return ElekSaleModel.PAY_PART;
        }
    }

    private TezgahQr() {}

    public static String encode(TezgahModel m, String date, String buyer, double paid) {
        StringBuilder sb = new StringBuilder(PREFIX).append('|')
                .append(date == null ? "" : date.trim()).append('|')
                .append(clean(buyer)).append('|');
        double[] kg = m.netArray();
        for (int g = 0; g < kg.length; g++) {
            if (g > 0) sb.append(',');
            sb.append(num(kg[g]));
        }
        sb.append('|');
        for (int g = 0; g < kg.length; g++) {
            if (g > 0) sb.append(',');
            sb.append(num(m.price(g)));
        }
        sb.append('|').append(m.totalKasa()).append('|').append(num(m.tare())).append('|')
                .append(num(Math.max(0, Math.min(m.total(), paid))));
        return sb.toString();
    }

    /** QR metnini çözer; Zeytin Defteri tezgah fişi değilse ya da bozuksa null. */
    public static Slip decode(String raw) {
        if (raw == null) return null;
        String text = raw.trim();
        if (text.length() > MAX_LEN || !text.startsWith(PREFIX + "|")) return null;
        String[] f = text.split("\\|", -1);
        if (f.length != 8) return null;
        try {
            Slip s = new Slip();
            if (!f[1].matches("\\d{4}-\\d{2}-\\d{2}")) return null;
            s.date = f[1];
            s.buyer = clean(f[2]);
            String[] kg = f[3].split(",", -1), pr = f[4].split(",", -1);
            if (kg.length != TezgahModel.GRADES.length || pr.length != TezgahModel.GRADES.length) return null;
            for (int g = 0; g < kg.length; g++) {
                s.kg[g] = Double.parseDouble(kg[g]);
                s.price[g] = Double.parseDouble(pr[g]);
                if (!(s.kg[g] >= 0) || s.kg[g] > TezgahModel.MAX_KG || !(s.price[g] >= 0) || s.price[g] > 100000) return null;
            }
            if (s.totalKg() <= 0) return null;
            s.kasa = Integer.parseInt(f[5]);
            s.tare = Double.parseDouble(f[6]);
            s.paid = Double.parseDouble(f[7]);
            if (s.kasa < 0 || !(s.tare >= 0) || !(s.paid >= 0)) return null;
            s.paid = Math.min(s.paid, s.total());
            return s;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Ad: ayraç ve satır sonu temizlenir, uzunluk sınırlanır. */
    static String clean(String name) {
        if (name == null) return "";
        String s = name.replace('|', ' ').replace('\n', ' ').replace('\r', ' ').trim().replaceAll("\\s+", " ");
        return s.length() > MAX_NAME ? s.substring(0, MAX_NAME).trim() : s;
    }

    /** Sayı: gereksiz ".0" olmadan, nokta ondalıkla (QR'da yer kazanmak için). */
    static String num(double v) {
        double r = Math.round(v * 100) / 100.0;
        if (r == Math.rint(r)) return String.valueOf((long) r);
        return String.format(Locale.US, "%s", r);
    }

    /** Elek formundaki kilo kutusuna yazılacak metin ("26", "12,5"). */
    public static String field(double v) {
        if (v <= 0) return "";
        return num(v).replace('.', ',');
    }
}
