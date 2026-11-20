package com.zeytindefteri.app;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Gönderim kuyruğu ve ağ kapalı kuralı (İP8-A): izin kapalıyken hiçbir şey kuyruğa girmez; aynı söz-anlam bir kez;
 * "Unut" kuyruktan düşürür ya da geri çekme ister; izin kapanınca bekleyenler düşer, gönderilmişler geri çekilir;
 * takma ad sezonluk döner ve kişiye bağlanamaz; sunucuya silme belgesinin yalnız özeti gider.
 * AĞ KAPALI: adres boşken, izin açık ve kuyruk doluyken bile tek bir ağ çağrısı yapılmaz.
 */
public class CommunityQueueTest {
    static int n = 0;
    static int bad = 0;

    static void check(boolean ok, String m) {
        n++;
        if (!ok) {
            bad++;
            System.out.println("FAIL " + m);
        }
    }

    static void eq(Object got, Object want, String m) {
        check(want.equals(got), m + ": «" + got + "» beklenen «" + want + "»");
    }

    /** Sayaçlı sahte ağ: her çağrı kaydedilir. */
    static final class CountingTransport implements CommunitySender.Transport {
        final List<String> calls = new ArrayList<>();
        boolean accept = true;

        @Override
        public boolean post(String url, String json) {
            calls.add(url + " " + json);
            return accept;
        }
    }

    static ContributionCleaner.Contribution contribution(String word, String meaning) {
        LearnedTerms.Term t = LearnedTerms.teach(word, LearnedTerms.choiceFor(meaning), LearnedTerms.SOURCE_VOICE, 1);
        return ContributionCleaner.clean(t, new ArrayList<>(), "Manisa", "1.7.0");
    }

    static CommunityQueue full(CommunityQueue.Memory store) {
        CommunityQueue q = new CommunityQueue(store, new SecureRandom());
        q.offer(contribution("bakırladık", "ilaçlama"), true);
        q.offer(contribution("suvardık", "sulama"), true);
        q.offer(contribution("silktik", "hasat"), true);
        return q;
    }

    public static void main(String[] a) {
        consentAndOnce();
        forgetAndWithdraw();
        pseudonym();
        payload();
        networkClosed();
        sendingWhenConfigured();
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " CommunityQueue");
        if (bad > 0) {
            System.exit(1);
        }
    }

    static void consentAndOnce() {
        CommunityQueue.Memory store = new CommunityQueue.Memory();
        CommunityQueue q = new CommunityQueue(store, new SecureRandom());
        check(!q.offer(contribution("bakırladık", "ilaçlama"), false) && store.all().isEmpty(), "izin kapalı: kuyruğa girmez");
        check(q.offer(contribution("bakırladık", "ilaçlama"), true), "izin açık: girer");
        check(!q.offer(contribution("bakırladık", "ilaçlama"), true), "aynı söz-anlam ikinci kez girmez");
        check(q.offer(contribution("bakırladık", "gübre"), true), "aynı söz başka anlam ayrı katkı");
        check(!q.offer(null, true), "temizlenemeyen söz girmez");
        eq(q.waiting().size(), 2, "iki katkı bekliyor");
    }

    static void forgetAndWithdraw() {
        CommunityQueue.Memory store = new CommunityQueue.Memory();
        CommunityQueue q = full(store);
        q.forget("bakirl");
        eq(store.all().size(), 2, "gönderilmemiş söz unutulunca düşer");
        check(q.withdrawals().isEmpty(), "gönderilmemişse geri çekme isteği gerekmez");
        CommunityQueue.Item first = q.waiting().get(0);
        q.sent(first);
        q.forget(first.c.root);
        eq(q.withdrawals().size(), 1, "gönderilmiş söz unutulunca geri çekme isteği");
        CommunityQueue.Memory s2 = new CommunityQueue.Memory();
        CommunityQueue q2 = full(s2);
        q2.sent(q2.waiting().get(0));
        q2.consentOff();
        check(q2.waiting().isEmpty(), "izin kapandı: bekleyenler düştü");
        eq(q2.withdrawals().size(), 1, "izin kapandı: gönderilmiş olan geri çekilecek");
        check(!q2.offer(contribution("devşirdik", "hasat"), false), "izin kapalıyken yeni gönderim yok");
    }

    static Calendar day(int y, int m, int d) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Europe/Istanbul"));
        c.clear();
        c.set(y, m - 1, d, 12, 0, 0);
        return c;
    }

    static void pseudonym() {
        eq(CommunityQueue.season(day(2026, 7, 31)), "2025", "31 Temmuz: önceki sezon");
        eq(CommunityQueue.season(day(2026, 8, 1)), "2026", "1 Ağustos: yeni sezon");
        eq(CommunityQueue.season(day(2027, 1, 15)), "2026", "ocak: aynı sezon");
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        String a = CommunityQueue.pseudonym(secret, "2026");
        eq(a, CommunityQueue.pseudonym(secret, "2026"), "aynı sezonda aynı takma ad");
        check(!a.equals(CommunityQueue.pseudonym(secret, "2027")), "1 Ağustos'ta döner");
        byte[] other = secret.clone();
        other[0] ^= 1;
        check(!a.equals(CommunityQueue.pseudonym(other, "2026")), "başka telefon başka takma ad");
        eq(a.length(), 32, "32 onaltılık hane");
        check(!a.contains(CommunityQueue.hex(secret).substring(0, 8)), "gizli anahtar takma adda görünmez");
    }

    static void payload() {
        CommunityQueue q = full(new CommunityQueue.Memory());
        CommunityQueue.Item i = q.waiting().get(0);
        String json = CommunitySender.submitJson(i, "abc");
        eq(json.replaceAll("\"silme_ozeti\":\"[0-9a-f]{64}\"", "\"silme_ozeti\":\"…\""),
                "{\"kok\":\"bakirl\",\"soz\":\"bakırladık\",\"eylem\":\"ISLEM\",\"tur\":\"İlaçlama\",\"not\":\"\",\"il\":\"Manisa\","
                        + "\"surum\":\"1.7.0\",\"takma_ad\":\"abc\",\"silme_ozeti\":\"…\"}", "gönderilen katkı: yalnız bu alanlar");
        check(!json.contains(i.token), "silme belgesinin kendisi gitmez, yalnız özeti");
        check(json.contains(CommunityQueue.digest(i.token)), "özet");
        eq(CommunitySender.withdrawJson(i), "{\"silme_belgesi\":\"" + i.token + "\"}", "geri çekme: yalnız silme belgesi");
        eq(i.token.length(), 32, "rastgele 16 bayt belge");
    }

    /** İzin açık, kuyruk dolu, geri çekmeler bekliyor: adres boşken TEK ağ çağrısı yok. */
    static void networkClosed() {
        eq(CommunitySender.url(), "", "İP8-A: adres yok (BuildConfig alanı tanımlı değil)");
        check(!CommunitySender.configured(CommunitySender.url()), "adres boşken yapılandırılmamış");
        CommunityQueue.Memory store = new CommunityQueue.Memory();
        CommunityQueue q = full(store);
        CommunityQueue.Item first = q.waiting().get(0);
        q.sent(first);
        q.forget(first.c.root);
        CountingTransport net = new CountingTransport();
        for (String url : new String[]{"", "   ", null, "http://ornek.invalid", "https://REPLACE_ME", "ftp://x", "https://"}) {
            eq(CommunitySender.flush(url == null ? null : url.trim(), q, true, "abc", net), 0, "gönderim yok: «" + url + "»");
        }
        eq(net.calls.size(), 0, "AĞ KAPALI: izin açık ve kuyruk doluyken bile hiçbir ağ çağrısı yapılmadı");
        check(!q.waiting().isEmpty() && !q.withdrawals().isEmpty(), "kuyruk olduğu gibi bekliyor");
    }

    /** Yalnız sahte ağla: adres olsaydı sıra geri çekme, sonra gönderim; izin kapalıyken yalnız geri çekme. */
    static void sendingWhenConfigured() {
        String fake = "https://ornek.invalid";
        CommunityQueue.Memory store = new CommunityQueue.Memory();
        CommunityQueue q = full(store);
        CommunityQueue.Item first = q.waiting().get(0);
        q.sent(first);
        q.forget(first.c.root);
        CountingTransport net = new CountingTransport();
        eq(CommunitySender.flush(fake, q, true, "abc", net), 3, "bir geri çekme + iki gönderim");
        check(net.calls.get(0).startsWith(fake + CommunitySender.WITHDRAW), "önce geri çekme");
        check(net.calls.get(1).startsWith(fake + CommunitySender.SUBMIT), "sonra gönderim");
        check(q.waiting().isEmpty() && q.withdrawals().isEmpty(), "kuyruk boşaldı");
        CountingTransport again = new CountingTransport();
        eq(CommunitySender.flush(fake, q, true, "abc", again), 0, "aynı katkı ikinci kez gönderilmez");
        CommunityQueue.Memory s2 = new CommunityQueue.Memory();
        CommunityQueue q2 = full(s2);
        q2.sent(q2.waiting().get(0));
        q2.consentOff();
        CountingTransport off = new CountingTransport();
        CommunitySender.flush(fake, q2, false, "abc", off);
        eq(off.calls.size(), 1, "izin kapalı: yalnız geri çekme");
        check(off.calls.get(0).contains(CommunitySender.WITHDRAW), "geri çekme isteği");
        CountingTransport refuse = new CountingTransport();
        refuse.accept = false;
        CommunityQueue q3 = full(new CommunityQueue.Memory());
        eq(CommunitySender.flush(fake, q3, true, "abc", refuse), 0, "sunucu reddederse");
        eq(q3.waiting().size(), 3, "reddedilen katkı kuyrukta bekler");
    }
}
