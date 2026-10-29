package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Uygulama içinden hesap silme (Google Play "app account deletion" şartı).
 *
 * Akış: uyarı + "SİL" onayı → piyasa katkılarını geri çek (Supabase) → sunucu fonksiyonu deleteAccount
 * (Firestore, Storage, Auth; functions/account) → bu telefondaki veriyi sil → oturumu kapat → giriş ekranı.
 * Sunucu, son 10 dakikada giriş yapılmamışsa reddeder; kullanıcı yeniden girişe yönlendirilir.
 * Misafir oturumunda bulutta hesap yoktur; yalnız bu telefondaki misafir verisi silinir.
 * Bir adım başarısız olursa sonraki adımlara geçilmez; sunucu silmesi tekrar çağrılabilir (idempotent).
 */
public final class AccountDeletion {
    private AccountDeletion() {}

    /** Sunucudaki onay sözcüğüyle aynı (functions/account/service.js CONFIRM_WORD). */
    static final String CONFIRM_WORD = "SİL";
    private static final String REGION = "europe-west1";
    private static final Locale TR = new Locale("tr", "TR");

    public static void start(Activity activity) {
        SessionManager session = new SessionManager(activity);
        FirebaseUser user = currentUser();
        if (session.isGuest() || user == null) {
            confirmGuestWipe(activity, session);
            return;
        }
        confirm(activity, user);
    }

    // ------------------------------------------------------------ onay

    private static void confirm(Activity activity, FirebaseUser user) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 20);
        box.setPadding(pad, dp(activity, 4), pad, 0);
        TextView info = new TextView(activity);
        info.setText("Hesabın ve bu hesaba ait tüm veriler kalıcı olarak silinir:\n"
                + "• bahçeler, işlemler, hasat, elek, satış ve salamura kayıtların ile bulut yedekleri\n"
                + "• Meydan gönderilerin ve fotoğrafların; diğer gönderilere yazdığın yorumlar \"Silinmiş kullanıcı\" olur\n"
                + "• anonim piyasa katkıların ve Zeytin Ligi kayıtların\n"
                + "• engelleme listen ve verdiğin şikayetler\n\n"
                + "Bu işlem geri alınamaz. Onaylamak için aşağıya " + CONFIRM_WORD + " yaz.");
        info.setTextColor(GreenTheme.color(R.color.zd_text_soft));
        info.setTextSize(14);
        box.addView(info);
        EditText input = new EditText(activity);
        input.setHint(CONFIRM_WORD);
        input.setSingleLine(true);
        box.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Hesabını sil")
                .setView(box)
                .setNegativeButton("Vazgeç", null)
                .setNeutralButton("E-posta ile talep et", (d, w) -> LegalDocumentActivity.requestDeletion(activity))
                .setPositiveButton("Hesabımı sil", null)
                .create();
        dialog.setOnShowListener(d -> {
            android.widget.Button delete = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            delete.setTextColor(GreenTheme.color(R.color.zd_danger));
            delete.setEnabled(false);
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                    delete.setEnabled(confirmed(s.toString()));
                }
                @Override public void afterTextChanged(Editable e) { }
            });
            delete.setOnClickListener(v -> {
                if (!confirmed(input.getText().toString())) return;
                dialog.dismiss();
                run(activity, user);
            });
        });
        dialog.show();
    }

    /** Sunucu sınırı 10 dakika; saat farkı payı için telefonda 9 dakika kullanılır. */
    static boolean recentlySignedIn(long lastSignInMs, long nowMs) {
        return lastSignInMs > 0 && nowMs - lastSignInMs <= 9 * 60 * 1000L;
    }

    /** "SİL", "sil", " Sil " kabul; Türkçe büyük harf kuralıyla karşılaştırılır. */
    static boolean confirmed(String text) {
        return text != null && CONFIRM_WORD.equals(text.trim().toUpperCase(TR));
    }

    // ------------------------------------------------------------ silme

    private static void run(Activity activity, FirebaseUser user) {
        // Sunucu son 10 dakikada giriş ister; hiçbir şeye dokunmadan önce telefonda da kontrol edilir
        // (piyasa katkılarının boşuna geri çekilmemesi için). Asıl kontrol sunucudadır.
        if (!recentlySignedIn(user.getMetadata() == null ? 0 : user.getMetadata().getLastSignInTimestamp(),
                System.currentTimeMillis())) {
            recentLoginRequired(activity);
            return;
        }
        AlertDialog progress = new AlertDialog.Builder(activity)
                .setTitle("Hesabın siliniyor")
                .setMessage("Lütfen bekle ve uygulamayı kapatma…")
                .setCancelable(false)
                .create();
        progress.show();
        final String uid = user.getUid();
        DatabaseHelper db = new DatabaseHelper(activity);

        // 1) Bu telefondan gönderilmiş anonim piyasa katkılarını geri çek (Supabase).
        new MarketCloudRepository(db, uid).setContributionOptIn(false, marketError -> {
            if (marketError != null) {
                db.close();
                progress.dismiss();
                failure(activity, "Piyasa katkıların geri çekilemedi. İnternet bağlantını kontrol edip tekrar dene. "
                        + "Hiçbir veri silinmedi.");
                return;
            }
            // 2) Sunucu: Firestore, Storage ve giriş hesabı.
            user.getIdToken(false).addOnCompleteListener(task -> {
                if (!task.isSuccessful() || task.getResult() == null || task.getResult().getToken() == null) {
                    db.close();
                    progress.dismiss();
                    failure(activity, "Oturum doğrulanamadı. İnternet bağlantını kontrol edip tekrar dene. Hiçbir veri silinmedi.");
                    return;
                }
                String token = task.getResult().getToken();
                new Thread(() -> {
                    String result = callDeleteAccount(token);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (!"ok".equals(result)) {
                            db.close();
                            progress.dismiss();
                            if ("recent-login-required".equals(result)) recentLoginRequired(activity);
                            else failure(activity, "Hesap silinemedi. Piyasa katkıların geri çekildi; diğer verilerin "
                                    + "yerinde. İnternet bağlantını kontrol edip tekrar dene. Sorun sürerse "
                                    + "\"E-posta ile talep et\" seçeneğini kullan.");
                            return;
                        }
                        // 3) Bu telefondaki veri ve ayarlar, ardından oturum.
                        try {
                            LocalAccountWipe.wipe(db.getWritableDatabase(), uid);
                        } finally {
                            db.close();
                        }
                        clearPreferences(activity, uid);
                        TezgahRescue.wipe(activity, uid);
                        progress.dismiss();
                        finishSignedOut(activity, "Hesabın ve verilerin silindi.");
                    });
                }).start();
            });
        });
    }

    /**
     * Firebase callable protokolü (firebase-functions SDK eklemeden): POST {"data":{...}},
     * Authorization: Bearer <ID token>. Dönüş: "ok", "recent-login-required" veya "error".
     */
    static String callDeleteAccount(String idToken) {
        HttpURLConnection connection = null;
        try {
            String project = FirebaseApp.getInstance().getOptions().getProjectId();
            URL url = new URL("https://" + REGION + "-" + project + ".cloudfunctions.net/deleteAccount");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(560000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Authorization", "Bearer " + idToken);
            byte[] body = new JSONObject().put("data", new JSONObject().put("confirm", CONFIRM_WORD))
                    .toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) { out.write(body); }
            int code = connection.getResponseCode();
            String text = read(code >= 400 ? connection.getErrorStream() : connection.getInputStream());
            return parseResult(code, text);
        } catch (Exception e) {
            return "error";
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Callable yanıtı: başarı {"result":{"deleted":true}}, hata {"error":{"status":..,"message":..}}. */
    static String parseResult(int httpCode, String body) {
        try {
            JSONObject json = new JSONObject(body == null ? "{}" : body);
            if (httpCode == 200 && json.has("result") && json.getJSONObject("result").optBoolean("deleted")) return "ok";
            JSONObject error = json.optJSONObject("error");
            if (error != null && "FAILED_PRECONDITION".equals(error.optString("status"))
                    && "recent-login-required".equals(error.optString("message"))) return "recent-login-required";
        } catch (Exception ignored) {
            // Anlaşılmayan yanıt başarı sayılmaz.
        }
        return "error";
    }

    private static String read(InputStream in) throws java.io.IOException {
        if (in == null) return "";
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = stream.read(buffer)) > 0) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------ sonuçlar

    private static void recentLoginRequired(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("Yeniden giriş gerekli")
                .setMessage("Güvenliğin için hesabını silmeden önce yeniden giriş yapman gerekiyor. "
                        + "Çıkış yapıp tekrar giriş yap, ardından Ayarlar > Hesabımı sil adımını tekrarla. "
                        + "Hiçbir veri silinmedi.")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Çıkış yap", (d, w) -> finishSignedOut(activity, null))
                .show();
    }

    private static void failure(Activity activity, String message) {
        new AlertDialog.Builder(activity)
                .setTitle("Hesap silinemedi")
                .setMessage(message)
                .setPositiveButton("Tamam", null)
                .show();
    }

    private static void confirmGuestWipe(Activity activity, SessionManager session) {
        new AlertDialog.Builder(activity)
                .setTitle("Misafir verilerini sil")
                .setMessage("Misafir oturumunda bulutta bir hesap yoktur. Bu telefondaki misafir kayıtların "
                        + "(bahçeler, işlemler, hasat, elek, satış) kalıcı olarak silinir. Bu işlem geri alınamaz.")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Sil", (d, w) -> {
                    DatabaseHelper db = new DatabaseHelper(activity);
                    try {
                        LocalAccountWipe.wipe(db.getWritableDatabase(), "");
                    } finally {
                        db.close();
                    }
                    TezgahRescue.wipe(activity, "");
                    finishSignedOut(activity, "Bu telefondaki misafir verilerin silindi.");
                })
                .show();
    }

    /** Oturumu kapatır, görev yığınını temizleyip giriş ekranını açar. */
    private static void finishSignedOut(Activity activity, String toast) {
        try { FirebaseAuth.getInstance().signOut(); } catch (Exception ignored) { }
        new SessionManager(activity).logout();
        if (toast != null) Toast.makeText(activity.getApplicationContext(), toast, Toast.LENGTH_LONG).show();
        activity.startActivity(new Intent(activity, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity.finish();
    }

    /** Hesaba bağlı paylaşılan tercih anahtarlarını (anahtarında uid geçen) tüm tercih dosyalarından siler. */
    static void clearPreferences(Context context, String uid) {
        if (uid == null || uid.isEmpty()) return;
        File dir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
        String[] files = dir.list();
        if (files == null) return;
        for (String file : files) {
            if (!file.endsWith(".xml")) continue;
            SharedPreferences prefs = context.getSharedPreferences(file.substring(0, file.length() - 4), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            boolean changed = false;
            for (String key : prefs.getAll().keySet()) {
                if (key.contains(uid)) { editor.remove(key); changed = true; }
            }
            if (changed) editor.commit();
        }
    }

    private static FirebaseUser currentUser() {
        try {
            return FirebaseAuth.getInstance().getCurrentUser();
        } catch (Exception e) {
            return null;
        }
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
