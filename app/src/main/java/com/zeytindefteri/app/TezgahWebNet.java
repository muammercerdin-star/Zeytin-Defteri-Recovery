package com.zeytindefteri.app;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Aktarıcı bağlantısının ağ ayarı. Bazı mobil ağlarda (ör. IPv6'sı olan 4.5G hatları) IPv6 yolu cevapsız kalır;
 * OkHttp 4 adresleri sırayla denediği için her IPv6 adresinde 15 sn bekleyip ancak sonra IPv4'e geçer ve
 * kullanıcı "bağlanılıyor"da takılır (Chrome iki yolu aynı anda denediğinden aynı adres orada açılır).
 * Çözüm: önce IPv4 adresleri, kısa bağlanma süresi; bağlantı açılmazsa istemci kendisi yeniden dener.
 */
final class TezgahWebNet {

    private TezgahWebNet() { }

    static final long CONNECT_TIMEOUT_S = 6;

    /** IPv4 adreslerini öne alır, kendi içlerindeki sırayı korur. */
    static List<InetAddress> ipv4First(List<InetAddress> all) {
        List<InetAddress> v4 = new ArrayList<>(), v6 = new ArrayList<>();
        if (all != null) for (InetAddress a : all) (a instanceof Inet4Address ? v4 : v6).add(a);
        v4.addAll(v6);
        return v4;
    }

    static final okhttp3.Dns IPV4_FIRST = host -> ipv4First(okhttp3.Dns.SYSTEM.lookup(host));

    /** Tüm aktarıcı istemcileri bu ayarla kurulur. */
    static okhttp3.OkHttpClient.Builder builder() {
        return new okhttp3.OkHttpClient.Builder().dns(IPV4_FIRST).connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS);
    }
}
