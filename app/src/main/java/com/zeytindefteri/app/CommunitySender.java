package com.zeytindefteri.app;

import java.util.Map;

/**
 * Ortak döngü göndericisi (İP8; saf, Android'siz, testli). Kural: adres boşsa ağ HİÇ açılmaz.
 * Adres yalnız Türkiye'deki sunucu olacaktır; İP8-A'da hiçbir adres yoktur (BuildConfig alanı bile yoktur,
 * okuyucu "" döner). configured() yanlışken flush() bağlantı katmanına (Transport) hiç dokunmaz.
 */
final class CommunitySender {
    private CommunitySender() {
    }

    /** Gönderme yolları (İP8-B'de sunucuyla eşlenir). */
    static final String SUBMIT = "/katki";
    static final String WITHDRAW = "/geri-cek";

    /** Ağ katmanı (Android'de HTTPS; testte sayaçlı sahte). true: sunucu kabul etti. */
    interface Transport {
        boolean post(String url, String json);
    }

    /** Sunucu adresi: İP8-A'da yok (BuildConfig alanı tanımlı değil) → "". */
    static String url() {
        String u = MarketBackendConfig.buildValue("ZEYTIN_COMMUNITY_URL", "").trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    /** Adres geçerli mi: yalnız https, sahibi belli, yer tutucu değil. Boşsa false (ağ kapalı). */
    static boolean configured(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        return url.startsWith("https://") && url.length() > "https://".length() && !url.contains("REPLACE_ME")
                && !url.contains("@") && !url.contains(" ");
    }

    /**
     * Kuyruğu boşaltmayı dener: önce geri çekmeler (izin kapalı olsa da), sonra izin açıksa bekleyenler.
     * Adres yoksa HİÇBİR ŞEY yapmaz ve bağlantı katmanına dokunmaz. Dönüş: sunucunun kabul ettiği istek sayısı.
     */
    static int flush(String url, CommunityQueue queue, boolean consent, String pseudonym, Transport transport) {
        if (!configured(url) || transport == null) {
            return 0;
        }
        int done = 0;
        for (CommunityQueue.Item i : queue.withdrawals()) {
            if (!transport.post(url + WITHDRAW, withdrawJson(i))) {
                return done;
            }
            queue.withdrawn(i);
            done++;
        }
        if (!consent) {
            return done;
        }
        for (CommunityQueue.Item i : queue.waiting()) {
            if (!transport.post(url + SUBMIT, submitJson(i, pseudonym))) {
                return done;
            }
            queue.sent(i);
            done++;
        }
        return done;
    }

    /** Gönderilen katkı: temizleyicinin yedi alanı + sezonluk takma ad + silme belgesinin özeti. Başka alan yok. */
    static String submitJson(CommunityQueue.Item i, String pseudonym) {
        StringBuilder b = new StringBuilder("{");
        for (Map.Entry<String, String> e : i.c.fields().entrySet()) {
            field(b, e.getKey(), e.getValue());
        }
        field(b, "takma_ad", pseudonym);
        field(b, "silme_ozeti", CommunityQueue.digest(i.token));
        return b.append('}').toString();
    }

    /** Geri çekme: yalnız silme belgesi. */
    static String withdrawJson(CommunityQueue.Item i) {
        StringBuilder b = new StringBuilder("{");
        field(b, "silme_belgesi", i.token);
        return b.append('}').toString();
    }

    private static void field(StringBuilder b, String key, String value) {
        if (b.length() > 1) {
            b.append(',');
        }
        b.append('"').append(key).append("\":\"").append(escape(value)).append('"');
    }

    private static String escape(String v) {
        StringBuilder s = new StringBuilder();
        for (char c : (v == null ? "" : v).toCharArray()) {
            if (c == '"' || c == '\\') {
                s.append('\\').append(c);
            } else if (c < 0x20) {
                s.append(String.format("\\u%04x", (int) c));
            } else {
                s.append(c);
            }
        }
        return s.toString();
    }
}
