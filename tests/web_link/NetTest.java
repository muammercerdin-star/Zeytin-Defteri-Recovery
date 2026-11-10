package com.zeytindefteri.app;
import java.net.InetAddress;
import java.util.*;
/** Aktarıcı ağ ayarı: IPv4 önce, sıra korunur; kısa bağlanma süresi; istemci bekçisi. */
public final class NetTest {
    public static void main(String[] a) throws Exception {
        List<InetAddress> in = Arrays.asList(InetAddress.getByName("2606:4700::6810:1"), InetAddress.getByName("104.16.0.1"),
                InetAddress.getByName("2606:4700::6810:2"), InetAddress.getByName("104.16.0.2"));
        List<InetAddress> out = TezgahWebNet.ipv4First(in);
        check(out.get(0).getHostAddress().equals("104.16.0.1") && out.get(1).getHostAddress().equals("104.16.0.2"), "IPv4 önce, sıra korunur");
        check(out.size() == 4 && out.get(2).getHostAddress().contains(":"), "IPv6 sonra, kaybolmaz");
        check(TezgahWebNet.ipv4First(null).isEmpty(), "boş liste");
        check(TezgahWebNet.CONNECT_TIMEOUT_S <= 8, "kısa bağlanma süresi");
        String c = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("app/src/main/java/com/zeytindefteri/app/TezgahWebClient.java")), "UTF-8");
        check(c.contains("TezgahWebNet.builder()") && c.contains("connectTries++;") && c.contains("connect();return;}"), "istemci IPv4-önce ve bekçi kullanır");
        for (String f : new String[]{"TezgahWebReadyService.java", "TezgahWebService.java"}) {
            String s = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("app/src/main/java/com/zeytindefteri/app/" + f)), "UTF-8");
            check(s.contains("TezgahWebNet.builder()") && !s.contains("new OkHttpClient.Builder()") && !s.contains("new okhttp3.OkHttpClient.Builder()"), f + " aynı ağ ayarı");
        }
        check(TezgahWebNet.bridgeBase("https://abcd.supabase.co/").equals("https://abcd.supabase.co/functions/v1/tezgah-relay"), "köprü adresi");
        check(TezgahWebNet.bridgeBase("https://REPLACE_ME.supabase.co").isEmpty() && TezgahWebNet.bridgeBase("").isEmpty() && TezgahWebNet.bridgeBase("http://abcd.supabase.co").isEmpty(), "yapılandırılmamış köprü yok");
        check(TezgahWebNet.socketUrl("https://abcd.supabase.co/functions/v1/tezgah-relay", "ROOM").equals("wss://abcd.supabase.co/functions/v1/tezgah-relay/v1/socket?room=ROOM"), "köprü soketi");
        check(TezgahWebNet.socketUrl(TezgahWebNet.directBase("https://tezgah-relay.x.workers.dev/"), "R").equals("wss://tezgah-relay.x.workers.dev/v1/socket?room=R"), "doğrudan soket");
        check(!TezgahWebNet.useBridge(false, "https://a.supabase.co/functions/v1/tezgah-relay") && TezgahWebNet.useBridge(true, "https://a") && !TezgahWebNet.useBridge(true, ""), "yol seçimi");
        check(TezgahWebNet.DIRECT_GIVE_UP_MS <= 6000 && TezgahWebNet.BRIDGE_GIVE_UP_MS >= 10000, "süreler");
        check(c.contains("TezgahWebNet.preferBridge=true") && c.contains("Yedek yol kurulmamış"), "istemci köprüye geçer");
        String rs = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("app/src/main/java/com/zeytindefteri/app/TezgahWebReadyService.java")), "UTF-8");
        check(rs.contains("TezgahWebNet.useBridge(") && rs.contains("TezgahWebNet.preferBridge = true"), "hazır servis köprüye geçer");
        String sv = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("app/src/main/java/com/zeytindefteri/app/TezgahWebService.java")), "UTF-8");
        check(sv.contains("registerDevice(registration, bridge"), "cihaz kaydı köprüden de");
        System.out.println("NetTest: ok");
    }
    static void check(boolean c, String m) { if (!c) { System.out.println("FAIL " + m); System.exit(1); } }
}
