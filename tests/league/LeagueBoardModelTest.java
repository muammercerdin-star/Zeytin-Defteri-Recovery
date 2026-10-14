package com.zeytindefteri.app;

import java.util.*;

/** Host-only checks for the Zeytin Ligi board display rules. */
public final class LeagueBoardModelTest {
    private static int checks;
    private static void eq(Object a, Object e, String l) { if (!Objects.equals(a, e)) throw new AssertionError(l + ": " + a + " != " + e); checks++; }
    public static void main(String[] args) {
        eq(LeagueBoardModel.MIN_PRODUCERS, 5, "floor");
        eq(LeagueBoardModel.publishable(true, 2), false, "2 producers hidden even if server published");
        eq(LeagueBoardModel.publishable(true, 4), false, "4 hidden");
        eq(LeagueBoardModel.publishable(true, 5), true, "5 open");
        eq(LeagueBoardModel.publishable(false, 9), false, "server denial wins");

        eq(Arrays.asList(LeagueBoardModel.splitRegion("Edremit / Balıkesir")), Arrays.asList("Edremit", "Balıkesir"), "split slash");
        eq(Arrays.asList(LeagueBoardModel.splitRegion("Gemlik, Bursa")), Arrays.asList("Gemlik", "Bursa"), "split comma");
        eq(Arrays.asList(LeagueBoardModel.splitRegion("Ayvalık")), Arrays.asList("Ayvalık", ""), "no province");
        eq(Arrays.asList(LeagueBoardModel.splitRegion(null)), Arrays.asList("", ""), "null");

        eq(LeagueBoardModel.badges(Arrays.asList("Alaşehir", "Edremit", "Gemlik", "Salihli")),
                Arrays.asList("A", "E", "G", "S"), "distinct initials");
        eq(LeagueBoardModel.badges(Arrays.asList("Ayvalık", "Akhisar", "Edremit")),
                Arrays.asList("AY", "AK", "E"), "collision uses two letters");
        eq(LeagueBoardModel.badges(Arrays.asList("İzmir", "ıslak")), Arrays.asList("İ", "I"), "turkish upper-case");
        eq(LeagueBoardModel.badges(Arrays.asList("")), Arrays.asList("?"), "empty name");

        eq(LeagueBoardModel.countText(2, false), "2 / 5 üretici", "closed count");
        eq(LeagueBoardModel.countText(9, false), "5 / 5 üretici", "closed count capped");
        eq(LeagueBoardModel.countText(7, true), "7 üretici", "open count");
        eq(LeagueBoardModel.needText(2), "3 üretici daha gerekli", "need");
        eq(LeagueBoardModel.needText(5), "", "no need");
        eq(LeagueBoardModel.progressPercent(1), 20, "progress 1");
        eq(LeagueBoardModel.progressPercent(8), 100, "progress capped");
        eq(LeagueBoardModel.progressPercent(-3), 0, "progress floor");

        eq(LeagueBoardModel.tag(true, false), "TOP 20", "top tag");
        eq(LeagueBoardModel.tag(false, true), "ANONİM", "open tag");
        eq(LeagueBoardModel.tag(false, false), "EŞİK", "threshold tag");
        eq(LeagueBoardModel.statusLine(4, false), "4 bölge • Bölge rakamları 5 üreticiyle açılır", "status waiting");
        eq(LeagueBoardModel.statusLine(4, true), "4 bölge • Top 20 yayında", "status top");
        eq(LeagueBoardModel.statusLine(0, false), "Bu sezon için henüz bölge yok", "status empty");
        eq(LeagueBoardModel.emptyTitle("Alaşehir"), "Alaşehir kupası henüz sahipsiz", "empty title");
        eq(LeagueBoardModel.emptyTitle(" "), "Bölge kupası henüz sahipsiz", "empty title fallback");
        eq(LeagueBoardModel.emptyBody().contains("en az 4 üretici"), true, "empty body names ranking minimum");
        eq(Arrays.toString(LeagueBoardModel.podiumOrder(3)), "[2, 1, 3]", "podium 3");
        eq(Arrays.toString(LeagueBoardModel.podiumOrder(2)), "[2, 1]", "podium 2");
        eq(Arrays.toString(LeagueBoardModel.podiumOrder(1)), "[1]", "podium 1");
        eq(LeagueBoardModel.podiumOrder(0).length, 0, "podium none");
        System.out.println("PASS: " + checks + " league board checks (host only).");
    }
}
