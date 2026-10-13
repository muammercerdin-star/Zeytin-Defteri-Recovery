package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Pure scoring policy v1. Local scores are previews, never public leaderboard writes. */
public final class ZeytinLeagueScore {
    public static final String[] GRADES = {"Double", "1 No", "2 No", "3 No", "4 No", "Yağlık"};
    public static final double MIN_KG = 100;
    private static final double[] QUALITY = {100, 90, 75, 55, 30, 10};

    public static final class Producer {
        public String id = "", name = "", region = "";
        public double kg, amount, quality, price = 50, volume, score;
        public final double[] grades = new double[6];
        public int records, rank;
        public boolean eligible, benchmarkReady, outlier;
        public double average() { return kg > 0 ? amount / kg : 0; }
        public String dominant() {
            int best = 0;
            for (int i = 1; i < 6; i++) if (grades[i] > grades[best]) best = i;
            return grades[best] > 0 ? GRADES[best] : "Belirsiz";
        }
    }

    /** Regional/season peer medians per grade, excluding the producer being scored.
     * Each median needs at least three independent verified producers, not three records.
     */
    public static void calculate(Producer p, double[] medians, int[] peerCounts) {
        p.quality = 0; p.price = 50; p.volume = 0; p.score = 0;
        p.rank = 0; p.outlier = false; p.benchmarkReady = false;
        double sum = 0, expected = 0;
        boolean valid = finite(p.kg) && finite(p.amount) && p.kg > 0 && p.amount > 0;
        boolean ready = medians != null && peerCounts != null && medians.length == 6 && peerCounts.length == 6;
        for (int i = 0; i < 6; i++) {
            valid &= finite(p.grades[i]) && p.grades[i] >= 0;
            sum += p.grades[i]; p.quality += p.grades[i] * QUALITY[i];
            if (p.grades[i] > 0 && ready) {
                if (!finite(medians[i]) || medians[i] <= 0 || peerCounts[i] < 3) ready = false;
                else expected += p.grades[i] * medians[i];
            }
        }
        valid &= Math.abs(sum - p.kg) <= Math.max(0.01, p.kg * 0.000001);
        if (!valid) { p.eligible = false; return; }
        p.quality = clamp(p.quality / p.kg);
        p.benchmarkReady = ready && expected > 0;
        if (p.benchmarkReady) {
            double ratio = p.amount / expected;
            p.outlier = ratio < 0.25 || ratio > 3;
            p.price = clamp(100 * (ratio - 0.5));
        }
        p.volume = clamp(100 * Math.log1p(p.kg) / Math.log1p(10000));
        p.score = clamp(0.50 * p.quality + 0.35 * p.price + 0.15 * p.volume);
        p.eligible = p.kg >= MIN_KG && p.records > 0 && !p.outlier;
    }

    /** Deterministic ranking; caller must aggregate one row per producer first. */
    public static List<Producer> rank(List<Producer> producers) {
        List<Producer> ranked = new ArrayList<>();
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (Producer p : producers) {
            if (!ids.add(p.id)) throw new IllegalArgumentException("Duplicate producer");
            p.rank = 0;
            if (p.eligible) ranked.add(p);
        }
        Collections.sort(ranked, Comparator.comparingDouble((Producer p) -> p.score).reversed()
                .thenComparing(Comparator.comparingDouble((Producer p) -> p.quality).reversed())
                .thenComparing(p -> p.id));
        for (int i = 0; i < ranked.size(); i++) ranked.get(i).rank = i + 1;
        return ranked;
    }

    /** Same interpolated quartile / 1.5 IQR approach as MarketActivity, isolated for testing.
     * Input must contain one verified grade-price observation per peer producer.
     */
    public static double robustMedian(List<Double> prices) {
        List<Double> sorted = new ArrayList<>();
        for (Double n : prices) if (n != null && finite(n) && n > 0) sorted.add(n);
        Collections.sort(sorted);
        if (sorted.size() < 3) return 0;
        double q1 = percentile(sorted, .25), q3 = percentile(sorted, .75);
        double spread = q3 - q1;
        List<Double> clean = new ArrayList<>();
        for (double v : sorted) if (v >= q1 - 1.5 * spread && v <= q3 + 1.5 * spread) clean.add(v);
        return clean.size() >= 3 ? percentile(clean, .5) : 0;
    }
    private static double percentile(List<Double> v, double q) {
        double x = (v.size() - 1) * q; int lo = (int) Math.floor(x), hi = (int) Math.ceil(x);
        return v.get(lo) + (v.get(hi) - v.get(lo)) * (x - lo);
    }
    public static boolean finite(double n) { return !Double.isNaN(n) && !Double.isInfinite(n); }
    private static double clamp(double n) { return Math.max(0, Math.min(100, n)); }
    private ZeytinLeagueScore() { }
}
