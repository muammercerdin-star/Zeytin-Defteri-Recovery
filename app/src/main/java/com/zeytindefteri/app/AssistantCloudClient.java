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
    private static final int CONNECT_MS = 3000, READ_MS = 7000, MAX_RESPONSE = 8192;
    private static final String PREFS = "zd_cloud_ai";

    private AssistantCloudClient() {}

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
        if (!configured()) return null;
        String token = idToken();
        if (token == null || token.isEmpty()) return null;
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
            if (connection.getResponseCode() != 200) return null;
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[2048];
                int k;
                while ((k = in.read(chunk)) != -1) {
                    if (buf.size() + k > MAX_RESPONSE) return null;
                    buf.write(chunk, 0, k);
                }
                return new String(buf.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Firebase kimlik belgesi (arka planda bekleyerek; en fazla 3 sn). */
    private static String idToken() {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) return null;
            final String[] out = new String[1];
            final CountDownLatch done = new CountDownLatch(1);
            user.getIdToken(false).addOnCompleteListener(task -> {
                try { if (task.isSuccessful() && task.getResult() != null) out[0] = task.getResult().getToken(); }
                catch (RuntimeException ignored) { }
                done.countDown();
            });
            return done.await(3, TimeUnit.SECONDS) ? out[0] : null;
        } catch (Exception e) {
            return null;
        }
    }
}
