package com.zeytindefteri.app;

import java.util.*;

/** Host-only checks for the Home "Zeytin Ligi" card states. */
public final class HomeLeagueModelTest {
    private static int checks;
    private static void eq(Object a, Object e, String l) { if (!Objects.equals(a, e)) throw new AssertionError(l + ": " + a + " != " + e); checks++; }
    private static HomeLeagueModel.Input in(String region) { HomeLeagueModel.Input i = new HomeLeagueModel.Input(); i.regionName = region; return i; }
    public static void main(String[] args) {
        eq(HomeLeagueModel.build(null), null, "null input");
        eq(HomeLeagueModel.build(in("")), null, "unknown region hidden");
        eq(HomeLeagueModel.build(in("Bölge belirtilmemiş")), null, "placeholder region hidden");

        // Bugünkü durum (ekran görüntüsü): yayın yok, Alaşehir'de 1 üretici.
        HomeLeagueModel.Input w = in("Alaşehir / Manisa"); w.localKg = 1247; w.regionProducers = 1;
        HomeLeagueModel.Card c = HomeLeagueModel.build(w);
        eq(c.state, HomeLeagueModel.State.WAITING, "waiting state");
        eq(c.overline, "ZEYTİN LİGİ · ALAŞEHİR", "overline uppercase tr");
        eq(c.title, "Alaşehir kupası sahipsiz", "waiting title");
        eq(c.sub, "Yarış bölgede 4 üretici sıralamaya girince başlar", "waiting rule");
        eq(c.invite, true, "invite shown");
        eq(c.progressPercent, 25, "1 of 4");
        eq(c.progressText.startsWith("Bölgede şu an 1 üretici"), true, "producer count text");
        eq(c.sub.contains("daha"), false, "no fabricated 'x more' count");
        HomeLeagueModel.Input unknown = in("Alaşehir / Manisa");
        eq(HomeLeagueModel.build(unknown).progressPercent, -1, "no bar when count unknown");

        // Ön puan asla sıra gibi gösterilmez: yayın yokken myRank yok sayılır.
        HomeLeagueModel.Input fake = in("Alaşehir / Manisa"); fake.myRank = 1; fake.published = false;
        eq(HomeLeagueModel.build(fake).state, HomeLeagueModel.State.WAITING, "unpublished rank ignored");

        HomeLeagueModel.Input lead = in("Edremit / Balıkesir"); lead.published = true; lead.raceStarted = true; lead.myRank = 1; lead.myScore = 80; lead.topScores = new double[]{80, 70, 60};
        c = HomeLeagueModel.build(lead);
        eq(c.state, HomeLeagueModel.State.LEADER, "leader");
        eq(c.title, "Edremit kupası şu an sende", "leader title");
        eq(c.invite, false, "no invite when racing");

        HomeLeagueModel.Input second = in("Edremit / Balıkesir"); second.published = true; second.raceStarted = true; second.myRank = 2; second.myScore = 72.55; second.topScores = new double[]{80, 72.55, 60};
        c = HomeLeagueModel.build(second);
        eq(c.state, HomeLeagueModel.State.RANKED, "ranked");
        eq(c.title, "Edremit'te 2. sıradasın", "locative te");
        eq(c.sub, "Kürsüdesin · liderliğe 7,5 puan", "gap to leader");

        HomeLeagueModel.Input fifth = in("Alaşehir / Manisa"); fifth.published = true; fifth.raceStarted = true; fifth.myRank = 5; fifth.myScore = 58; fifth.topScores = new double[]{80, 70, 60.4, 59, 58};
        c = HomeLeagueModel.build(fifth);
        eq(c.title, "Alaşehir'de 5. sıradasın", "locative de");
        eq(c.sub, "Kürsüye 2,4 puan kaldı", "gap to podium");
        HomeLeagueModel.Input tie = in("Gemlik / Bursa"); tie.published = true; tie.raceStarted = true; tie.myRank = 4; tie.myScore = 60; tie.topScores = new double[]{80, 70, 60, 60};
        eq(HomeLeagueModel.build(tie).sub, "Kürsüye çok yakınsın", "tie with third");

        HomeLeagueModel.Input miss = in("Alaşehir / Manisa"); miss.published = true; miss.raceStarted = true; miss.localKg = 1247;
        c = HomeLeagueModel.build(miss);
        eq(c.state, HomeLeagueModel.State.MISSING, "missing");
        eq(c.kgOk, true, "100 kg met");
        eq(c.checklist, true, "checklist");
        eq(c.sub, "Bir satışı \"piyasa verisine dahil et\" ile kaydet", "missing sale");
        miss.localKg = 40;
        eq(HomeLeagueModel.build(miss).sub, "Sezonda en az 100 kg elek kaydı gerekli", "missing kg");

        eq(HomeLeagueModel.locative("Ayvalık"), "Ayvalık'ta", "ta");
        eq(HomeLeagueModel.locative("Salihli"), "Salihli'de", "de");
        eq(HomeLeagueModel.locative("Gemlik"), "Gemlik'te", "te");
        eq(HomeLeagueModel.locative("Akhisar"), "Akhisar'da", "da");
        eq(HomeLeagueModel.locative("Burhaniye"), "Burhaniye'de", "vowel ending");
        String invite = HomeLeagueModel.inviteText("Alaşehir / Manisa");
        eq(invite.contains("Alaşehir kupası henüz sahipsiz"), true, "invite names cup");
        eq(invite.contains("http"), false, "no fabricated link");
        // Kalıcı son durum: anında çizim için; 24 saatten eskiyse sıra atılır.
        long now = 1_800_000_000_000L;
        String enc = HomeLeagueModel.encode(fifth, now - 3600_000L);
        HomeLeagueModel.Input dec = HomeLeagueModel.decode(enc, now);
        eq(HomeLeagueModel.signature(HomeLeagueModel.build(dec)), HomeLeagueModel.signature(HomeLeagueModel.build(fifth)), "round trip keeps the same card");
        HomeLeagueModel.Input old = HomeLeagueModel.decode(HomeLeagueModel.encode(fifth, now - 2 * 86400000L), now);
        eq(old.myRank, 0, "stale saved rank dropped");
        eq(HomeLeagueModel.build(old).state, HomeLeagueModel.State.WAITING, "stale state falls back to waiting");
        eq(HomeLeagueModel.decode("", now), null, "empty");
        eq(HomeLeagueModel.decode("v1\tbroken", now), null, "broken");
        eq(HomeLeagueModel.decode("v1\tA / B\tx\t0\t0\t0\t0\t-1\t\t1", now), null, "bad number");
        HomeLeagueModel.Input tab = in("Ala\tşehir / Manisa");
        eq(HomeLeagueModel.decode(HomeLeagueModel.encode(tab, now), now).regionName, "Ala şehir / Manisa", "tab sanitised");
        eq(HomeLeagueModel.signature(null), "none", "no card signature");
        eq(HomeLeagueModel.signature(HomeLeagueModel.build(w)).equals(HomeLeagueModel.signature(HomeLeagueModel.build(lead))), false, "different states differ");
        System.out.println("PASS: " + checks + " home league card checks (host only).");
    }
}
