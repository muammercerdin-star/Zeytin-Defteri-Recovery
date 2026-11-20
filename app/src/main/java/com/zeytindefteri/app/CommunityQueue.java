package com.zeytindefteri.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Ortak döngü gönderim kuyruğu (İP8; saf, Android'siz, testli). Temizlenmiş katkılar telefonda bekler.
 *
 * - Yalnız izin açıkken girer; aynı söz-anlam bir kez gönderilir.
 * - "Unut" denen söz: gönderilmediyse kuyruktan düşer, gönderildiyse geri çekme isteği olur.
 * - İzin kapatılınca: bekleyenler düşer, gönderilmişler için geri çekme isteği sıraya girer.
 * - Takma ad: telefondaki gizli anahtardan sezon başına türetilir (HMAC-SHA256), her 1 Ağustos'ta döner; kişiyle
 *   bağlanamaz, gizli anahtar telefondan çıkmaz.
 * - Geri çekme: her katkıya rastgele silme belgesi; sunucuya yalnız özeti (SHA-256) gider, belge geri çekmede gider.
 * Telefonda ayrı dosyada (CommunityDb, zd_community.db): yedeğe, aktarıma, Yedekle dosyasına girmez.
 */
final class CommunityQueue {
    enum State { WAITING, SENT, WITHDRAW }

    /** Kuyruktaki bir katkı. */
    static final class Item {
        final ContributionCleaner.Contribution c;
        final State state;
        /** Rastgele silme belgesi (geri çekmek için; sunucu yalnız özetini bilir). */
        final String token;

        Item(ContributionCleaner.Contribution c, State state, String token) {
            this.c = c;
            this.state = state;
            this.token = token;
        }

        Item with(State s) {
            return new Item(c, s, token);
        }
    }

    interface Store {
        List<Item> all();

        void put(Item item);

        void remove(String key);
    }

    /** Bellekte (testler); telefondaki CommunityDb aynı arayüzü uygular. */
    static final class Memory implements Store {
        private final List<Item> items = new ArrayList<>();

        @Override
        public List<Item> all() {
            return new ArrayList<>(items);
        }

        @Override
        public void put(Item item) {
            remove(item.c.key());
            items.add(item);
        }

        @Override
        public void remove(String key) {
            items.removeIf(i -> i.c.key().equals(key));
        }
    }

    private final Store store;
    private final SecureRandom random;

    CommunityQueue(Store store, SecureRandom random) {
        this.store = store;
        this.random = random;
    }

    /** Öğretilen söz (temizlenmiş). İzin kapalıysa ya da temizlenemediyse hiçbir şey olmaz. */
    boolean offer(ContributionCleaner.Contribution c, boolean consent) {
        if (!consent || c == null) {
            return false;
        }
        for (Item i : store.all()) {
            if (i.c.key().equals(c.key())) {
                return false;
            }
        }
        store.put(new Item(c, State.WAITING, newToken()));
        return true;
    }

    /** "Unut": bu kökün katkıları. Gönderilmediyse düşer; gönderildiyse geri çekme isteği. */
    void forget(String root) {
        for (Item i : store.all()) {
            if (!i.c.root.equals(root)) {
                continue;
            }
            if (i.state == State.WAITING) {
                store.remove(i.c.key());
            } else if (i.state == State.SENT) {
                store.put(i.with(State.WITHDRAW));
            }
        }
    }

    /** İzin kapandı: yeni gönderim durur; bekleyenler düşer, gönderilmişler geri çekilecek. */
    void consentOff() {
        for (Item i : store.all()) {
            if (i.state == State.WAITING) {
                store.remove(i.c.key());
            } else if (i.state == State.SENT) {
                store.put(i.with(State.WITHDRAW));
            }
        }
    }

    List<Item> waiting() {
        return byState(State.WAITING);
    }

    List<Item> withdrawals() {
        return byState(State.WITHDRAW);
    }

    void sent(Item i) {
        store.put(i.with(State.SENT));
    }

    /** Geri çekme sunucuda yapıldı: kuyruktan tamamen düşer. */
    void withdrawn(Item i) {
        store.remove(i.c.key());
    }

    private List<Item> byState(State s) {
        List<Item> out = new ArrayList<>();
        for (Item i : store.all()) {
            if (i.state == s) {
                out.add(i);
            }
        }
        return out;
    }

    private String newToken() {
        byte[] b = new byte[16];
        random.nextBytes(b);
        return hex(b);
    }

    // ------------------------------------------------------------ takma ad ve özet

    /** Sezon: 1 Ağustos'ta döner ("2026" = 1 Ağustos 2026 – 31 Temmuz 2027). */
    static String season(Calendar now) {
        int year = now.get(Calendar.YEAR);
        return String.valueOf(now.get(Calendar.MONTH) >= Calendar.AUGUST ? year : year - 1);
    }

    /** Sezonluk takma ad: HMAC-SHA256(gizli anahtar, "zd-ortak|" + sezon), ilk 32 onaltılık hane. */
    static String pseudonym(byte[] secret, String season) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] out = mac.doFinal(("zd-ortak|" + season).getBytes(StandardCharsets.UTF_8));
            return hex(out).substring(0, 32);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Silme belgesinin özeti (sunucu yalnız bunu tutar). */
    static String digest(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return hex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) {
            s.append(String.format("%02x", x & 0xff));
        }
        return s.toString();
    }
}
