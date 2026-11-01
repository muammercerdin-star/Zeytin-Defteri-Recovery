package com.zeytindefteri.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * İnternet yardımcısı istemcisi (cloud/zeytin-ai). Arka planda çağrılır (ana iş parçacığında DEĞİL).
 * Adres local.properties'teki ZEYTIN_AI_URL'den gelir (gizli değildir); boşsa özellik tamamen kapalıdır.
 * Sunucu yalnız Firebase ile giriş yapmış kullanıcıyı kabul eder; uygulamada hiçbir anahtar yoktur.
 * Her hata (internet yok, kota doldu, gecikme) null döner: asistan kurallarla devam eder.
 */
final class AssistantCloudClient {
    /** Mobil ağda TLS el sıkışması ve modelin ilk (soğuk) açılışı birkaç saniye sürebilir. */
    private static final int CONNECT_MS = 10000, READ_MS = 20000, MAX_RESPONSE = 8192;
    private static final String PREFS = "zd_cloud_ai";

    private AssistantCloudClient() {}

    /** Son denemenin sonucu (teşhis): "internet yardımcısı durumu" ile okunur, hata olunca kısa bildirim. */
    static volatile String lastStatus = "Henüz denenmedi.";
    static volatile boolean lastOk;

    private static String fail(String why) { lastOk = false; lastStatus = why; return null; }

    static String url() {
        String u = MarketBackendConfig.buildValue("ZEYTIN_AI_URL", "").trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    static boolean configured() {
        String u = url();
        return u.startsWith("https://") && !u.contains("REPLACE_ME");
    }

    /** "" sorulmadı, "on" açık, "off" kapalı (kullanıcı başına, yalnız bu telefonda). */
    static String mode(Context c, String owner) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("m_" + owner, "");
    }

    static void setMode(Context c, String owner, boolean on) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("m_" + owner, on ? "on" : "off").apply();
    }

    static boolean online(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network n = cm.getActiveNetwork();
            NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Cümleyi sunucuya sorar; cevap gövdesi ya da null. En fazla ~10 sn sürer. */
    static String ask(String raw, List<String> gardens) {
        if (!configured()) return fail("Yardımcının adresi uygulamaya yazılmamış (ZEYTIN_AI_URL).");
        String token = idToken();
        if (token == null || token.isEmpty()) return null;   // nedeni idToken() yazdı
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url() + "/parse").openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_MS);
            connection.setReadTimeout(READ_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            byte[] bytes = AssistantCloud.requestBody(raw, gardens).getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
            catch (java.net.SocketTimeoutException e) { return fail("Sunucuya bağlanılamadı (bağlantı " + CONNECT_MS / 1000 + " sn'de kurulamadı)."); }
            long sent = android.os.SystemClock.elapsedRealtime();
            int code;
            try { code = connection.getResponseCode(); }
            catch (java.net.SocketTimeoutException e) { return fail("Sunucuya ulaşıldı ama yardımcı " + READ_MS / 1000 + " sn içinde cevap vermedi."); }
            long took = android.os.SystemClock.elapsedRealtime() - sent;
            if (code != 200) {
                String reason = "";
                try (InputStream err = connection.getErrorStream()) {
                    if (err != null) { byte[] b = new byte[512]; int k = err.read(b); if (k > 0) reason = new String(b, 0, k, StandardCharsets.UTF_8); }
                } catch (Exception ignored) { }
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"reason\"\\s*:\\s*\"(\\w+)\"").matcher(reason);
                String r = m.find() ? m.group(1) : "";
                return fail("Sunucu " + code + " döndü" + (r.isEmpty() ? "" : " (" + reasonText(r) + ")") + ".");
            }
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[2048];
                int k;
                while ((k = in.read(chunk)) != -1) {
                    if (buf.size() + k > MAX_RESPONSE) return fail("Sunucu cevabı çok uzun.");
                    buf.write(chunk, 0, k);
                }
                String body = new String(buf.toByteArray(), StandardCharsets.UTF_8);
                if (body.contains("\"ok\":true")) { lastOk = true; lastStatus = "Son deneme başarılı: yardımcı " + Math.max(1, Math.round(took / 1000.0)) + " sn'de cevap verdi."; }
                else {
                    java.util.regex.Matcher d = java.util.regex.Pattern.compile("\"diag\"\\s*:\\s*\"([^\"]{0,80})\"").matcher(body);
                    String diag = d.find() ? " [" + d.group(1) + "]" : "";
                    fail((body.contains("not_understood") ? "Yardımcı cümleyi bir bahçe işi olarak anlayamadı." : "Yardımcı geçersiz cevap verdi.") + diag);
                }
                return body;
            }
        } catch (java.net.SocketTimeoutException e) {
            return fail("Yardımcı zamanında cevap vermedi (zaman aşımı).");
        } catch (java.net.UnknownHostException e) {
            return fail("Sunucunun adresi bulunamadı (internet ya da DNS).");
        } catch (Exception e) {
            return fail("Bağlantı kurulamadı: " + e.getClass().getSimpleName() + ".");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Firebase kimlik belgesi (arka planda bekleyerek; en fazla 6 sn). */
    private static String idToken() {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) return fail("Uygulamada giriş yapılmamış görünüyor (Firebase).");
            final String[] out = new String[1];
            final String[] err = new String[1];
            final CountDownLatch done = new CountDownLatch(1);
            user.getIdToken(false).addOnCompleteListener(task -> {
                try {
                    if (task.isSuccessful() && task.getResult() != null) out[0] = task.getResult().getToken();
                    else err[0] = task.getException() == null ? "bilinmeyen" : task.getException().getClass().getSimpleName();
                } catch (RuntimeException e) { err[0] = e.getClass().getSimpleName(); }
                done.countDown();
            });
            if (!done.await(6, TimeUnit.SECONDS)) return fail("Giriş belgesi zamanında alınamadı.");
            if (out[0] == null || out[0].isEmpty()) return fail("Giriş belgesi alınamadı: " + err[0] + ".");
            return out[0];
        } catch (Exception e) {
            return fail("Giriş belgesi alınamadı: " + e.getClass().getSimpleName() + ".");
        }
    }

    static String reasonText(String r) {
        switch (r) {
            case "unauthenticated": return "giriş belgesi kabul edilmedi";
            case "rate_limited": return "dakikalık sınır doldu";
            case "no_model": return "Cloudflare'de AI bağlantısı yok";
            case "model_error": return "model hata verdi ya da günlük kota doldu";
            case "bad_text": return "cümle geçersiz";
            case "upstream": return "aktarıcı Cloudflare'e ulaşamadı";
            default: return r;
        }
    }
}
