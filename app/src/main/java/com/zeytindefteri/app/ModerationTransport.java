package com.zeytindefteri.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Moderasyon servisine giden HTTPS isteği. Saf Java'dır (Firebase ve org.json yok), masaüstünde test edilir.
 *
 * Firebase oturum belgesi (ID token) yalnızca doğrulanmış bir Supabase proje adresine gider:
 * - adres boş, "REPLACE_ME" içeren, HTTPS olmayan ya da *.supabase.co dışındaki bir adresse istek açılmaz;
 * - kullanıcı bilgisi, sorgu, parça veya 443 dışı port içeren adres reddedilir;
 * - yönlendirme izlenmez, 3xx yanıtı hata sayılır (istek başka sunucuya kaçmaz).
 * Belge hiçbir zaman kayda yazılmaz ve hata mesajlarına konmaz.
 */
final class ModerationTransport {
    static final String PATH = "/functions/v1/moderation-api";
    static final int MAX_REQUEST = 8192;
    static final int MAX_RESPONSE = 1024 * 1024;

    interface Opener { HttpURLConnection open(URL url) throws IOException; }
    interface Parser { Object parse(int status, String body) throws ModerationBackendException; }

    private ModerationTransport() {}

    /** Geçerli bir Supabase proje kökü için moderasyon adresi; değilse null. */
    static URL endpoint(String base) {
        if (base == null) return null;
        String value = base.trim();
        if (value.isEmpty() || value.toUpperCase(Locale.ROOT).contains("REPLACE_ME")) return null;
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            String path = uri.getRawPath();
            if (!"https".equals(uri.getScheme()) || host == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !(path == null || path.isEmpty() || "/".equals(path))) return null;
            String lower = host.toLowerCase(Locale.ROOT);
            if (!lower.equals(host) || !lower.endsWith(".supabase.co")) return null;
            String project = lower.substring(0, lower.length() - ".supabase.co".length());
            if (!project.matches("[a-z0-9][a-z0-9-]{0,62}")) return null;
            return new URL("https://" + lower + PATH);
        } catch (Exception e) {
            return null;
        }
    }

    static boolean isConfigured(String base) {
        return endpoint(base) != null;
    }

    static Object send(String base, String payload, String token, int timeout, Opener opener, Parser parser)
            throws ModerationBackendException {
        URL endpoint = endpoint(base);
        // Yapılandırma geçersizse bağlantı açılmaz; belge hiçbir yere gönderilmez.
        if (endpoint == null) throw new ModerationBackendException(ModerationBackendException.Code.UNAVAILABLE);
        if (token == null || token.isEmpty()) throw new ModerationBackendException(ModerationBackendException.Code.UNAUTHENTICATED);
        byte[] bytes = (payload == null ? "" : payload).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_REQUEST) throw new ModerationBackendException(ModerationBackendException.Code.INVALID_ARGUMENT);
        HttpURLConnection connection = null;
        try {
            connection = opener.open(endpoint);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(timeout);
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) throw new ModerationBackendException(ModerationBackendException.Code.UNAVAILABLE);
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String response = "";
            if (stream != null) {
                try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] chunk = new byte[4096];
                    int n;
                    while ((n = in.read(chunk)) != -1) {
                        if (out.size() + n > MAX_RESPONSE) throw new ModerationBackendException(ModerationBackendException.Code.INTERNAL);
                        out.write(chunk, 0, n);
                    }
                    response = new String(out.toByteArray(), StandardCharsets.UTF_8);
                }
            }
            return parser.parse(status, response);
        } catch (ModerationBackendException e) {
            throw e;
        } catch (IOException e) {
            // Ağ hatası: kullanıcıya "şu anda bağlanılamıyor" gösterilir; ayrıntı (ve belge) kaydedilmez.
            throw new ModerationBackendException(ModerationBackendException.Code.UNAVAILABLE);
        } catch (RuntimeException e) {
            throw new ModerationBackendException(ModerationBackendException.Code.INTERNAL);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
