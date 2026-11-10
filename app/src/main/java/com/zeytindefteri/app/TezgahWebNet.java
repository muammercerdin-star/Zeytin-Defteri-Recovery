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
    /** Doğrudan yol bu kadar sürede açılmazsa Supabase köprüsüne geçilir. */
    static final long DIRECT_GIVE_UP_MS = 5000;
    /** Köprü yolunun açılması için beklenen süre (Supabase ilk çağrıda biraz yavaş olabilir). */
    static final long BRIDGE_GIVE_UP_MS = 15000;

    /**
     * Doğrudan yol bu telefonda bir kez açılmadıysa sonraki bağlantılar baştan köprüden gider (uygulama
     * açık kaldıkça hatırlanır). Köprü: supabase.co üzerindeki tezgah-relay fonksiyonu; içerik uçtan uca şifreli.
     */
    static volatile boolean preferBridge;

    /** Köprü adresi: SUPABASE_URL + /functions/v1/tezgah-relay; Supabase yapılandırılmamışsa boş. */
    static String bridgeBase(String supabaseUrl) {
        String u = supabaseUrl == null ? "" : supabaseUrl.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (!u.startsWith("https://") || !u.endsWith(".supabase.co") || u.contains("REPLACE_ME")) return "";
        return u + "/functions/v1/tezgah-relay";
    }

    static String bridgeBase() {
        return bridgeBase(MarketBackendConfig.SUPABASE_URL);
    }

    static String directBase(String relayUrl) {
        String u = relayUrl == null ? "" : relayUrl.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    /** https://… → wss://… ve oda soketi yolu. */
    static String socketUrl(String httpsBase, String room) {
        return "wss" + httpsBase.substring(5) + "/v1/socket?room=" + room;
    }

    /** Bu deneme hangi yoldan: köprü tercih edildiyse ve adresi varsa köprü. */
    static boolean useBridge(boolean prefer, String bridge) {
        return prefer && bridge != null && !bridge.isEmpty();
    }

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
