package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

/** Explicit online moderation reports only; never mutates social content or local data. */
public final class SocialReportManager {
    private static final String[] LABELS = {"Spam / reklam", "Hakaret veya rahatsız edici içerik",
            "Yanlış / yanıltıcı bilgi", "Uygunsuz içerik", "Dolandırıcılık şüphesi", "Diğer"};
    private static final String[] REASONS = {"SPAM", "ABUSE", "MISLEADING", "INAPPROPRIATE", "FRAUD", "OTHER"};
    private SocialReportManager() {}

    public static boolean canReport(String ownerUid) {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            return user != null && !user.isAnonymous() && SocialReportTarget.validId(ownerUid)
                    && !user.getUid().equals(ownerUid);
        } catch (RuntimeException e) { return false; }
    }

    public static void showMenu(Activity activity, String type, String targetId, String postId, String ownerUid) {
        if (!canReport(ownerUid)) return;
        new AlertDialog.Builder(activity).setTitle("Şikâyet")
                .setItems(new String[]{"Şikâyet Et", "Kullanıcıyı Şikâyet Et"}, (d, index) ->
                        showReasons(activity, type, targetId, postId, ownerUid, index == 1))
                .setNegativeButton("Vazgeç", null).show();
    }

    public static void showReasons(Activity activity, String type, String targetId,
                                   String postId, String ownerUid, boolean reportUser) {
        if (!canReport(ownerUid)) { message(activity, "Şikâyet göndermek için hesabınızla giriş yapın."); return; }
        final SocialReportTarget target;
        try { target = new SocialReportTarget(type, targetId, postId, ownerUid, reportUser); }
        catch (IllegalArgumentException e) {
            message(activity, "Bu içerik henüz bulutta doğrulanamıyor. Bağlantınızı kontrol edip tekrar deneyin."); return;
        }
        FirebaseUser reportingUser = FirebaseAuth.getInstance().getCurrentUser();
        if (reportingUser == null) return;
        String reporter = reportingUser.getUid();
        int[] selected = {-1};
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(reportUser ? "Kullanıcıyı Şikâyet Et" : "Şikâyet Et")
                .setSingleChoiceItems(LABELS, -1, (d, which) -> {
                    selected[0] = which;
                    ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                }).setNegativeButton("Vazgeç", null).setPositiveButton("Gönder", null).create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (selected[0] < 0) return;
                dialog.dismiss();
                submit(activity, reporter, target, REASONS[selected[0]]);
            });
        });
        dialog.show();
    }

    private static void submit(Activity activity, String reporter, SocialReportTarget target, String reason) {
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current == null || !reporter.equals(current.getUid()) || !canReport(target.ownerUid)) {
            message(activity, "Oturum değişti. Lütfen tekrar giriş yapın."); return;
        }
        AlertDialog progress = new AlertDialog.Builder(activity).setMessage("Şikâyet gönderiliyor…")
                .setCancelable(false).create();
        progress.show();
        Handler handler = new Handler(Looper.getMainLooper());
        boolean[] finished = {false};
        Runnable timeout = () -> {
            if (finished[0]) return;
            finished[0] = true;
            if (!activity.isDestroyed()) progress.dismiss();
            message(activity, "Gönderim sonucu doğrulanamadı. Bağlantınızı kontrol edip tekrar deneyin; aynı şikâyet çoğaltılmaz.");
        };
        handler.postDelayed(timeout, 20000);
        try {
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            DocumentReference ref = firestore.collection("reports").document(reporter)
                    .collection("items").document(target.key());
            Map<String, Object> data = new HashMap<>();
            data.put("reporter_uid", reporter);
            data.put("reported_uid", target.ownerUid);
            data.put("target_type", target.type);
            data.put("target_id", target.targetId);
            data.put("post_cloud_id", target.postId);
            data.put("comment_cloud_id", target.commentId);
            data.put("reason", reason);
            data.put("created_at", FieldValue.serverTimestamp());
            data.put("status", "OPEN");
            // Transactions require the server: no offline success, no queued content deletion.
            firestore.runTransaction(transaction -> {
                if (transaction.get(ref).exists()) return false;
                transaction.set(ref, data);
                return true;
            }).addOnCompleteListener(task -> {
                handler.removeCallbacks(timeout);
                if (finished[0]) return;
                finished[0] = true;
                if (!activity.isDestroyed()) progress.dismiss();
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user == null || !reporter.equals(user.getUid())) return;
                if (!task.isSuccessful()) {
                    message(activity, "Şikâyet gönderilemedi. İnternet bağlantınızı kontrol edin; içerik kaldırılmış da olabilir.");
                } else if (Boolean.TRUE.equals(task.getResult())) {
                    flagContent(firestore, reporter, target, reason);
                    message(activity, "Şikâyetiniz alındı. İnceleme için teşekkür ederiz.");
                } else {
                    message(activity, "Bu hedefi daha önce şikâyet ettiniz.");
                }
            });
        } catch (RuntimeException e) {
            handler.removeCallbacks(timeout); finished[0] = true;
            if (!activity.isDestroyed()) progress.dismiss();
            message(activity, "Şikâyet gönderilemedi. Lütfen tekrar deneyin.");
        }
    }

    /**
     * Moderasyon kuyruğu: içeriğin şikâyet sayacı 1 artar, mod_flags kaydı açılır/güncellenir (kişi başı bir kez;
     * firestore.rules). 3 farklı şikâyette içerik moderatör bakana kadar herkesten gizlenir. Kullanıcı şikâyeti
     * (USER) yalnız eski rapor akışına gider. Hata sessizdir: asıl şikâyet kaydı zaten alınmıştır.
     */
    private static void flagContent(FirebaseFirestore fs, String reporter, SocialReportTarget target, String reason) {
        if ("USER".equals(target.type)) return;
        boolean post = "POST".equals(target.type);
        String flagId = post ? "P_" + target.postId : "C_" + target.postId + "_" + target.commentId;
        try {
            DocumentReference flag = fs.collection("mod_flags").document(flagId);
            DocumentReference me = flag.collection("reporters").document(reporter);
            DocumentReference content = post
                    ? fs.collection("social_posts").document(target.postId)
                    : fs.collection("social_posts").document(target.postId).collection("comments").document(target.commentId);
            Map<String, Object> mine = new HashMap<>();
            mine.put("created_at", FieldValue.serverTimestamp());
            mine.put("reason", reason);
            Map<String, Object> f = new HashMap<>();
            f.put("target_type", post ? "POST" : "COMMENT");
            f.put("post_cloud_id", target.postId);
            f.put("comment_cloud_id", target.commentId);
            f.put("reported_uid", target.ownerUid);
            f.put("preview", "");
            f.put("count", FieldValue.increment(1));
            f.put("status", "OPEN");
            f.put("updated_at", System.currentTimeMillis());
            f.put("last_reason", reason);
            fs.batch()
                    .set(me, mine)
                    .set(flag, f, com.google.firebase.firestore.SetOptions.merge())
                    .update(content, "report_count", FieldValue.increment(1))
                    .commit();
        } catch (RuntimeException ignored) {
            // Kuyruk kaydı yazılamazsa asıl şikâyet yine moderasyona ulaşır.
        }
    }

    private static void message(Activity activity, String text) {
        if (!activity.isFinishing() && !activity.isDestroyed()) Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
    }
}
