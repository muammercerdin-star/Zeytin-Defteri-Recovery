package com.zeytindefteri.app;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Grup C: moderasyon istemcisi geçersiz hedefe token göndermez. Gerçek ModerationTransport sınıfıyla çalışır. */
public class ModerationTransportTest {
    static int checks = 0;
    static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }

    static final String TOKEN = "eyJ.test-token.imza";
    static final String VALID = "https://abcdefghijklmnopqrst.supabase.co";

    /** Açılan bağlantıları ve gönderilen başlıkları kaydeden sahte bağlantı. */
    static final class Fake extends HttpURLConnection {
        final Map<String, String> headers = new LinkedHashMap<>();
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        int status = 200; String response = "{\"data\":{}}"; IOException failOnWrite; boolean redirectsFollowed = true;
        Fake(URL url) { super(url); }
        @Override public void setRequestProperty(String k, String v) { headers.put(k, v); }
        @Override public void setInstanceFollowRedirects(boolean f) { redirectsFollowed = f; }
        @Override public OutputStream getOutputStream() throws IOException { if (failOnWrite != null) throw failOnWrite; return body; }
        @Override public int getResponseCode() { return status; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(response.getBytes()); }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(response.getBytes()); }
        @Override public void disconnect() {}
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() {}
    }

    static final List<Fake> opened = new ArrayList<>();
    static Fake next;
    static final ModerationTransport.Opener OPENER = url -> { Fake f = next != null ? next : new Fake(url); next = null; opened.add(f); return f; };
    static final ModerationTransport.Parser PARSER = (status, body) -> {
        if (status < 200 || status >= 300) throw new ModerationBackendException(ModerationBackendException.Code.INTERNAL);
        return "ok:" + body;
    };

    static ModerationBackendException.Code failure(String base, String token) {
        try { ModerationTransport.send(base, "{\"action\":\"moderationListReports\"}", token, 5000, OPENER, PARSER); return null; }
        catch (ModerationBackendException e) { return e.getCode(); }
    }

    static void rejectsWithoutConnecting(String base, String why) {
        int before = opened.size();
        check(ModerationTransport.endpoint(base) == null, why + ": adres kabul edildi");
        check(!ModerationTransport.isConfigured(base), why + ": yapılandırılmış sayıldı");
        check(failure(base, TOKEN) == ModerationBackendException.Code.UNAVAILABLE, why + ": UNAVAILABLE değil");
        check(opened.size() == before, why + ": bağlantı açıldı (token gönderilebilirdi)");
    }

    public static void main(String[] args) throws Exception {
        // C1 boş adres, C2 REPLACE_ME, C3 geçersiz URL, C4 HTTPS olmayan, C5 eksik/yanlış yapılandırma, yanlış sunucu
        rejectsWithoutConnecting(null, "null");
        rejectsWithoutConnecting("", "boş");
        rejectsWithoutConnecting("   ", "yalnız boşluk");
        rejectsWithoutConnecting("https://REPLACE_ME.supabase.co", "REPLACE_ME");
        rejectsWithoutConnecting("https://replace_me.supabase.co", "replace_me küçük harf");
        rejectsWithoutConnecting("ht!tp://bozuk adres", "geçersiz URL");
        rejectsWithoutConnecting("supabase.co", "şemasız");
        rejectsWithoutConnecting("http://abcdefghijklmnopqrst.supabase.co", "HTTP");
        rejectsWithoutConnecting("ftp://abcdefghijklmnopqrst.supabase.co", "FTP");
        rejectsWithoutConnecting("https://attacker.example.com", "başka alan adı");
        rejectsWithoutConnecting("https://attacker.example.com/#.supabase.co", "parça ile kandırma");
        rejectsWithoutConnecting("https://attacker.example.com/x.supabase.co", "yol ile kandırma");
        rejectsWithoutConnecting("https://abcdefghijklmnopqrst.supabase.co.attacker.com", "sonek kandırma");
        rejectsWithoutConnecting("https://user:pass@abcdefghijklmnopqrst.supabase.co", "kullanıcı bilgisi");
        rejectsWithoutConnecting("https://abcdefghijklmnopqrst.supabase.co:8443", "farklı port");
        rejectsWithoutConnecting("https://abcdefghijklmnopqrst.supabase.co/rest/v1", "yol içeren kök");
        rejectsWithoutConnecting("https://abcdefghijklmnopqrst.supabase.co?x=1", "sorgu");
        rejectsWithoutConnecting("https://supabase.co", "proje adı yok");
        rejectsWithoutConnecting("https://a.b.supabase.co", "çok seviyeli alan");
        rejectsWithoutConnecting("https://ABCDEFGHIJKLMNOPQRST.supabase.co", "büyük harfli alan");

        // C6 geçerli yapılandırma: doğru adrese, doğru başlıklarla, yönlendirme izlenmeden gider
        check(ModerationTransport.endpoint(VALID).toString().equals(VALID + "/functions/v1/moderation-api"), "uç nokta yanlış");
        check(ModerationTransport.endpoint(VALID + "/").toString().equals(VALID + "/functions/v1/moderation-api"), "sondaki / kabul edilmedi");
        check(ModerationTransport.endpoint(" " + VALID + " ") != null, "boşluklu geçerli adres reddedildi");
        Object result = ModerationTransport.send(VALID, "{\"a\":1}", TOKEN, 5000, OPENER, PARSER);
        Fake sent = opened.get(opened.size() - 1);
        check(("ok:{\"data\":{}}").equals(result), "yanıt ayrıştırılmadı");
        check(sent.getURL().toString().equals(VALID + "/functions/v1/moderation-api"), "yanlış adrese gitti: " + sent.getURL());
        check(("Bearer " + TOKEN).equals(sent.headers.get("Authorization")), "Authorization başlığı yok");
        check(!sent.redirectsFollowed, "yönlendirme izleniyor");
        check(sent.body.toString("UTF-8").equals("{\"a\":1}"), "gövde yanlış");

        // Token yoksa bağlantı açılmaz
        int before = opened.size();
        check(failure(VALID, "") == ModerationBackendException.Code.UNAUTHENTICATED, "boş token kabul edildi");
        check(failure(VALID, null) == ModerationBackendException.Code.UNAUTHENTICATED, "null token kabul edildi");
        check(opened.size() == before, "token yokken bağlantı açıldı");

        // C7 ağ bağlantısı hatası: UNAVAILABLE (kullanıcıya "şu anda bağlanılamıyor"); mesajda token yok
        next = new Fake(new URL(VALID + "/functions/v1/moderation-api"));
        next.failOnWrite = new IOException("ağ yok " + TOKEN);
        try { ModerationTransport.send(VALID, "{}", TOKEN, 5000, OPENER, PARSER); check(false, "ağ hatası yutuldu"); }
        catch (ModerationBackendException e) {
            check(e.getCode() == ModerationBackendException.Code.UNAVAILABLE, "ağ hatası UNAVAILABLE değil: " + e.getCode());
            check(!String.valueOf(e.getMessage()).contains(TOKEN), "hata mesajında token var");
            check(e.getCause() == null, "ağ hatasının ayrıntısı (token içerebilir) zincire eklendi");
        }
        try { ModerationTransport.send(VALID, "{}", TOKEN, 5000, url -> { throw new IOException("DNS"); }, PARSER); check(false, "açılış hatası yutuldu"); }
        catch (ModerationBackendException e) { check(e.getCode() == ModerationBackendException.Code.UNAVAILABLE, "açılış hatası UNAVAILABLE değil"); }

        // Yanıt başka sunucuya yönlendirirse (3xx) izlenmez, hata sayılır
        next = new Fake(new URL(VALID + "/functions/v1/moderation-api"));
        next.status = 302;
        check(failure(VALID, TOKEN) == ModerationBackendException.Code.UNAVAILABLE, "yönlendirme hata sayılmadı");

        // Büyük istek gönderilmez
        before = opened.size();
        StringBuilder big = new StringBuilder(); for (int i = 0; i < 9000; i++) big.append('x');
        try { ModerationTransport.send(VALID, big.toString(), TOKEN, 5000, OPENER, PARSER); check(false, "büyük istek gönderildi"); }
        catch (ModerationBackendException e) { check(e.getCode() == ModerationBackendException.Code.INVALID_ARGUMENT, "büyük istek kodu yanlış"); }
        check(opened.size() == before, "büyük istekte bağlantı açıldı");

        // Kullanıcıya gösterilecek mesaj
        check(ModerationBackendException.UNAVAILABLE_MESSAGE.equals(
                "Moderasyon servisine şu anda bağlanılamıyor. Lütfen daha sonra tekrar deneyin."), "kullanıcı mesajı değişmiş");
        // Repodaki yer tutucu yapılandırma geçersiz sayılır
        check(!ModerationTransport.isConfigured(MarketBackendConfig.SUPABASE_URL), "REPLACE_ME yapılandırması geçerli sayıldı");

        System.out.println("ModerationTransportTest: " + checks + " kontrol geçti");
    }
}
