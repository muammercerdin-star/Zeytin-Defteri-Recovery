package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Moderatör kuyruğu (ücretsiz plan; yetki firestore.rules'ta: zd_moderators/{uid} ya da moderation_admin).
 * - Onay bekleyenler: yeni üyelerin ilk gönderileri → Onayla / Gizle.
 * - Şikâyet edilenler (mod_flags OPEN): içerik + şikâyet sayısı → Sorun yok / Gizle / Kullanıcıyı sustur.
 */
public final class MeydanModerationActivity extends Activity {
    private FirebaseFirestore fs;
    private String me = "";
    private LinearLayout body, pendingBox, flagBox;
    private final SimpleDateFormat dt = new SimpleDateFormat("d MMM HH:mm", new Locale("tr", "TR"));

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null || !ChatGuard.isModerator(this)) { finish(); return; }
        me = u.getUid();
        fs = FirebaseFirestore.getInstance();
        int bg = GreenTheme.color(R.color.zd_bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(6), dp(16), dp(24));
        scroll.addView(body);
        setContentView(scroll);
        build();
        load();
    }

    private void build() {
        int text = GreenTheme.color(R.color.zd_text), muted = GreenTheme.color(R.color.zd_muted);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setMinimumHeight(dp(52));
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_elek_back_chevron);
        back.setColorFilter(text);
        back.setPadding(dp(11), dp(11), dp(11), dp(11));
        back.setContentDescription("Geri");
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(40), dp(44));
        bp.setMarginStart(-dp(10));
        head.addView(back, bp);
        head.addView(text("🛡  Moderasyon", 22, text, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView refresh = text("Yenile", 14, GreenTheme.color(R.color.zd_green), true);
        refresh.setPadding(dp(12), dp(10), 0, dp(10));
        refresh.setOnClickListener(v -> load());
        head.addView(refresh);
        body.addView(head);
        body.addView(text("Yeni üyelerin ilk " + MeydanModeration.NEW_MEMBER_POSTS + " mesajı onayınla görünür. "
                + MeydanModeration.AUTO_HIDE_REPORTS + " şikâyet alan içerik sen bakana kadar gizlidir. "
                + "Küfür telefonda zaten engellenir.", 13, muted, false), margins(2));
        body.addView(section("ONAY BEKLEYENLER"), margins(18));
        pendingBox = column();
        body.addView(pendingBox);
        body.addView(section("ŞİKÂYET EDİLENLER"), margins(22));
        flagBox = column();
        body.addView(flagBox);
    }

    private void load() {
        pendingBox.removeAllViews();
        flagBox.removeAllViews();
        pendingBox.addView(note("Yükleniyor…"));
        flagBox.addView(note("Yükleniyor…"));
        fs.collection("social_posts").whereEqualTo("status", MeydanModeration.PENDING).limit(50).get()
                .addOnSuccessListener(q -> {
                    pendingBox.removeAllViews();
                    int n = 0;
                    for (QueryDocumentSnapshot d : q) {
                        Object active = d.get("is_active");
                        if (active instanceof Number && ((Number) active).intValue() == 0) continue; // sahibi silmiş
                        n++;
                        pendingBox.addView(pendingCard(d), margins(8));
                    }
                    if (n == 0) pendingBox.addView(note("Onay bekleyen mesaj yok ✓"));
                })
                .addOnFailureListener(e -> { pendingBox.removeAllViews(); pendingBox.addView(note("Okunamadı. Bağlantını kontrol et.")); });
        fs.collection("mod_flags").whereEqualTo("status", "OPEN").limit(50).get()
                .addOnSuccessListener(q -> {
                    flagBox.removeAllViews();
                    if (q.isEmpty()) { flagBox.addView(note("Açık şikâyet yok ✓")); return; }
                    for (QueryDocumentSnapshot f : q) flagCard(f);
                })
                .addOnFailureListener(e -> { flagBox.removeAllViews(); flagBox.addView(note("Okunamadı. Moderatör yetkini kontrol et.")); });
    }

    // ------------------------------------------------------------------ onay bekleyen

    private LinearLayout pendingCard(DocumentSnapshot d) {
        LinearLayout c = card();
        c.addView(meta(str(d, "user_name"), str(d, "district").isEmpty() ? str(d, "province") : str(d, "district"), d.get("created_at")));
        c.addView(content(str(d, "content")), margins(6));
        LinearLayout actions = row();
        actions.addView(button("Onayla", true, () -> moderate(d.getReference(), MeydanModeration.ACTIVE, null, c)), weight());
        actions.addView(button("Gizle", false, () -> moderate(d.getReference(), MeydanModeration.HIDDEN, null, c)), weightGap());
        actions.addView(button("Sustur", false, () -> askMute(str(d, "user_id"), str(d, "user_name"))), weightGap());
        c.addView(actions, margins(10));
        return c;
    }

    // ------------------------------------------------------------------ şikâyet

    private void flagCard(QueryDocumentSnapshot f) {
        final LinearLayout c = card();
        flagBox.addView(c, margins(8));
        final String type = str(f, "target_type"), postId = str(f, "post_cloud_id"), commentId = str(f, "comment_cloud_id");
        long count = f.get("count") instanceof Number ? ((Number) f.get("count")).longValue() : 1;
        TextView badge = text("⚑ " + count + " şikâyet · " + reasonLabel(str(f, "last_reason")) + ("COMMENT".equals(type) ? " · yanıt" : ""),
                13, GreenTheme.color(R.color.zd_warning), true);
        c.addView(badge);
        c.addView(note("İçerik yükleniyor…"));
        final DocumentReference target = "COMMENT".equals(type)
                ? fs.collection("social_posts").document(postId).collection("comments").document(commentId)
                : fs.collection("social_posts").document(postId);
        target.get().addOnSuccessListener(d -> {
            c.removeViewAt(1);
            if (d == null || !d.exists()) {
                c.addView(note("İçerik silinmiş."), margins(4));
                LinearLayout a = row();
                a.addView(button("Kapat", true, () -> resolve(f.getReference(), c)), weight());
                c.addView(a, margins(8));
                return;
            }
            c.addView(meta(str(d, "user_name"), "", d.get("created_at")), margins(4));
            c.addView(content(str(d, "content")), margins(6));
            if (MeydanModeration.HIDDEN.equals(MeydanModeration.status(d.get("status")))) {
                c.addView(note("Şu an gizli."), margins(4));
            }
            LinearLayout a = row();
            a.addView(button("Sorun yok", true, () -> moderate(target, MeydanModeration.ACTIVE, f.getReference(), c)), weight());
            a.addView(button("Gizle", false, () -> moderate(target, MeydanModeration.HIDDEN, f.getReference(), c)), weightGap());
            a.addView(button("Sustur", false, () -> askMute(str(d, "user_id"), str(d, "user_name"))), weightGap());
            c.addView(a, margins(10));
        }).addOnFailureListener(e -> { c.removeViewAt(1); c.addView(note("İçerik okunamadı.")); });
    }

    /** Onay/sorun yok: ACTIVE + sayaç 0. Gizle: HIDDEN. Şikâyet varsa kapatılır. */
    private void moderate(DocumentReference ref, String status, DocumentReference flag, LinearLayout card) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", status);
        if (MeydanModeration.ACTIVE.equals(status)) m.put("report_count", 0);
        m.put("moderated_by", me);
        m.put("moderated_at", System.currentTimeMillis());
        com.google.firebase.firestore.WriteBatch b = fs.batch().update(ref, m);
        if (flag != null) {
            Map<String, Object> r = new HashMap<>();
            r.put("status", "RESOLVED");
            r.put("updated_at", System.currentTimeMillis());
            b.update(flag, r);
        }
        b.commit().addOnSuccessListener(x -> {
            card.setAlpha(0.45f);
            card.setEnabled(false);
            toast(MeydanModeration.HIDDEN.equals(status) ? "Gizlendi." : "Onaylandı, herkese görünür.");
            ((LinearLayout) card.getParent()).removeView(card);
        }).addOnFailureListener(e -> toast("İşlem yapılamadı. Yetkini ve bağlantını kontrol et."));
    }

    private void resolve(DocumentReference flag, LinearLayout card) {
        Map<String, Object> r = new HashMap<>();
        r.put("status", "RESOLVED");
        r.put("updated_at", System.currentTimeMillis());
        flag.update(r).addOnSuccessListener(x -> ((LinearLayout) card.getParent()).removeView(card))
                .addOnFailureListener(e -> toast("Kapatılamadı."));
    }

    private void askMute(String uid, String name) {
        if (uid.isEmpty() || uid.equals(me)) { toast("Bu kullanıcı susturulamaz."); return; }
        new AlertDialog.Builder(this)
                .setTitle((name.isEmpty() ? "Kullanıcı" : name) + " susturulsun mu?")
                .setItems(MeydanModeration.MUTE_LABELS, (d, i) -> {
                    Map<String, Object> m = new HashMap<>();
                    long now = System.currentTimeMillis();
                    m.put("until", now + MeydanModeration.MUTE_MS[i]);
                    m.put("reason", "Topluluk kurallarına aykırı içerik");
                    m.put("by", me);
                    m.put("created_at", now);
                    m.put("name", name.length() > 200 ? name.substring(0, 200) : name);
                    fs.collection("social_mutes").document(uid).set(m)
                            .addOnSuccessListener(x -> toast((name.isEmpty() ? "Kullanıcı" : name) + " " + MeydanModeration.MUTE_LABELS[i].toLowerCase(new Locale("tr", "TR")) + " susturuldu."))
                            .addOnFailureListener(e -> toast("Susturulamadı."));
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    // ------------------------------------------------------------------ görünüm

    private static String reasonLabel(String r) {
        switch (r) {
            case "SPAM": return "spam";
            case "ABUSE": return "hakaret";
            case "MISLEADING": return "yanıltıcı";
            case "INAPPROPRIATE": return "uygunsuz";
            case "FRAUD": return "dolandırıcılık";
            default: return "diğer";
        }
    }

    private TextView meta(String name, String place, Object created) {
        String when = created instanceof Number ? dt.format(new Date(((Number) created).longValue())) : "";
        String s = (name.isEmpty() ? "Üretici" : name) + (place.isEmpty() ? "" : " · " + place) + (when.isEmpty() ? "" : " · " + when);
        return text(s, 14, GreenTheme.color(R.color.zd_text), true);
    }

    private TextView content(String s) {
        TextView t = text(s.isEmpty() ? "(boş)" : s, 15, GreenTheme.color(R.color.zd_text_soft), false);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    private TextView button(String label, boolean primary, Runnable r) {
        int green = GreenTheme.color(R.color.zd_green);
        TextView b = text(label, 14, primary ? GreenTheme.color(R.color.zd_bg) : green, true);
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(primary ? green : 0);
        g.setStroke(Math.max(1, dp(1)), green);
        b.setBackground(g);
        b.setMinHeight(dp(42));
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private LinearLayout card() {
        LinearLayout c = column();
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable g = new GradientDrawable();
        g.setColor(GreenTheme.color(R.color.zd_card));
        g.setCornerRadius(dp(16));
        g.setStroke(Math.max(1, dp(1)), GreenTheme.color(R.color.zd_border));
        c.setBackground(g);
        return c;
    }

    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }

    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setGravity(Gravity.CENTER_VERTICAL); return l; }

    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -2, 1); }

    private LinearLayout.LayoutParams weightGap() { LinearLayout.LayoutParams p = weight(); p.setMarginStart(dp(8)); return p; }

    private TextView section(String s) {
        TextView t = text(s, 12, GreenTheme.color(R.color.zd_muted), true);
        t.setLetterSpacing(0.08f);
        return t;
    }

    private TextView note(String s) { return text(s, 14, GreenTheme.color(R.color.zd_muted), false); }

    private TextView text(String v, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(v);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout.LayoutParams margins(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        return lp;
    }

    private static String str(DocumentSnapshot d, String k) {
        Object v = d.get(k);
        return v instanceof String ? ((String) v).trim() : "";
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
