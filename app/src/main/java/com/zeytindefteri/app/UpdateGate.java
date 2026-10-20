package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Zorunlu / isteğe bağlı güncelleme.
 *
 * Ayar dosyası GitHub Pages'te (ücretsiz, girişsiz okunur, Firebase kotası harcamaz):
 *   {"min_version": 1, "latest_version": 1, "message": "…"}
 * - Uygulamanın sürüm numarası (versionCode) min_version'dan küçükse: kapatılamaz "Güncelleme gerekli" ekranı.
 * - latest_version'dan küçükse: 3 günde bir "Yeni sürüm var" penceresi (Sonra denebilir).
 * İnternet yoksa son bilinen ayar kullanılır; hiç okunamadıysa uygulama normal açılır (kimse kilitlenmez).
 */
public final class UpdateGate {
    static final String CONFIG_URL =
            "https://muammercerdin-star.github.io/Zeytin-Defteri-Recovery/zeytin-defteri/app-config.json";
    private static final String PREFS = "zd_update";
    private static final long FETCH_EVERY_MS = 30L * 60 * 1000;
    private static final long NUDGE_EVERY_MS = 3L * 24 * 60 * 60 * 1000;

    public static final int NONE = 0, OPTIONAL = 1, REQUIRED = 2;

    private UpdateGate() {}

    /** Karar: Android'e bağlı değil, test edilir. Ayar okunamadıysa (0) hiçbir şey yapılmaz. */
    static int decide(int versionCode, int minVersion, int latestVersion) {
        if (versionCode <= 0) return NONE;
        if (minVersion > 0 && versionCode < minVersion) return REQUIRED;
        if (latestVersion > 0 && versionCode < latestVersion) return OPTIONAL;
        return NONE;
    }

    static int currentVersion() { return BuildConfig.VERSION_CODE; }

    /** Her ekran öne geldiğinde (ZeytinApplication). Ağ en fazla 30 dakikada bir. */
    public static void onResume(final Activity activity) {
        if (activity instanceof UpdateRequiredActivity) return;
        final SharedPreferences p = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        apply(activity, p);
        long now = System.currentTimeMillis();
        if (now - p.getLong("fetched_at", 0) < FETCH_EVERY_MS) return;
        p.edit().putLong("fetched_at", now).apply();
        final Context app = activity.getApplicationContext();
        new Thread(() -> {
            JSONObject json = fetch();
            if (json == null) return;
            p.edit().putInt("min", json.optInt("min_version", 0))
                    .putInt("latest", json.optInt("latest_version", 0))
                    .putString("message", json.optString("message", ""))
                    .apply();
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed()) apply(activity, p);
            });
        }, "zd-update-check").start();
    }

    private static void apply(Activity activity, SharedPreferences p) {
        int decision = decide(currentVersion(), p.getInt("min", 0), p.getInt("latest", 0));
        String message = p.getString("message", "");
        if (decision == REQUIRED) {
            Intent i = new Intent(activity, UpdateRequiredActivity.class)
                    .putExtra(UpdateRequiredActivity.EXTRA_MESSAGE, message)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(i);
            activity.finish();
        } else if (decision == OPTIONAL) {
            long now = System.currentTimeMillis();
            if (now - p.getLong("nudged_at", 0) < NUDGE_EVERY_MS) return;
            p.edit().putLong("nudged_at", now).apply();
            try {
                new AlertDialog.Builder(activity)
                        .setTitle("Yeni sürüm var")
                        .setMessage(message.isEmpty() ? "Zeytin Defteri'nin yeni sürümü yayında. Güncellemeni öneririz." : message)
                        .setPositiveButton("Güncelle", (d, w) -> openStore(activity))
                        .setNegativeButton("Sonra", null)
                        .show();
            } catch (RuntimeException ignored) { }
        }
    }

    static void openStore(Activity activity) {
        String pkg = activity.getPackageName();
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)));
        } catch (RuntimeException e) {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
            } catch (RuntimeException ignored) { }
        }
    }

    private static JSONObject fetch() {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(CONFIG_URL + "?t=" + (System.currentTimeMillis() / 600000)).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            c.setUseCaches(false);
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[2048];
                int n;
                while ((n = in.read(buf)) > 0 && out.size() < 16384) out.write(buf, 0, n);
                return new JSONObject(out.toString("UTF-8"));
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
