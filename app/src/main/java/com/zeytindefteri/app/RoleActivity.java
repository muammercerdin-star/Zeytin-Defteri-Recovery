package com.zeytindefteri.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * "Zeytin Defteri'ni nasıl kullanacaksın?": Üreticiyim / Tezgâhçıyım / İkisi de.
 * Bir kez sorulur (yeni kurulumda bahçe eklemeden önce; eski kullanıcıya ana ekranda), Ayarlar'dan değişir.
 * Tezgâhçı uygulamayı Tezgâh ana ekranıyla açar, bahçe eklemek zorunda kalmaz. Seçim kullanıcıya özeldir.
 */
public final class RoleActivity extends Activity {
    public static final String EXTRA_FIRST_RUN = "first_run";
    /** Ana ekrandan ya da ayarlardan açıldı: üretici/ikisi de seçilince yalnız kapanır (ekran yeniden kurulmaz). */
    public static final String EXTRA_FROM_FEED = "from_feed";
    private static final String PREFS = "zd_role";

    private static final int BG = Color.parseColor("#050F16");
    private static final int CARD = Color.parseColor("#0F2230");
    private static final int LINE = Color.parseColor("#2A4452");
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.parseColor("#A9BAB6");
    private static final int GREEN = Color.parseColor("#8BE06A");
    private static final int GREEN_DIM = Color.parseColor("#1E3D2C");

    private String picked = UserRole.PRODUCER;
    private boolean firstRun, fromFeed;
    private final View[] cards = new View[3];
    private final String[] roles = {UserRole.PRODUCER, UserRole.TRADER, UserRole.BOTH};

    // ------------------------------------------------------------------ saklama (kullanıcıya özel)

    private static String uid(Context c) {
        DatabaseHelper h = new DatabaseHelper(c.getApplicationContext());
        try {
            String u = h.getActiveOwnerUid();
            return u == null ? "" : u;
        } catch (RuntimeException e) {
            return "";
        } finally {
            h.close();
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    public static String role(Context c) { return UserRole.parse(prefs(c).getString("role_" + uid(c), "")); }

    public static void setRole(Context c, String role) {
        prefs(c).edit().putString("role_" + uid(c), UserRole.parse(role)).apply();
    }

    public static String lastView(Context c) { return prefs(c).getString("view_" + uid(c), UserRole.VIEW_GARDEN); }

    public static void setLastView(Context c, String view) { prefs(c).edit().putString("view_" + uid(c), view).apply(); }

    public static void open(Context c, boolean firstRun) { open(c, firstRun, false); }

    public static void open(Context c, boolean firstRun, boolean fromFeed) {
        c.startActivity(new Intent(c, RoleActivity.class).putExtra(EXTRA_FIRST_RUN, firstRun).putExtra(EXTRA_FROM_FEED, fromFeed));
    }

    // ------------------------------------------------------------------ ekran

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!new SessionManager(this).hasSession()) { finish(); return; }
        firstRun = getIntent().getBooleanExtra(EXTRA_FIRST_RUN, false);
        fromFeed = getIntent().getBooleanExtra(EXTRA_FROM_FEED, false);
        String now = role(this);
        if (UserRole.chosen(now)) picked = now;
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(40), dp(20), dp(24));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -1));

        body.addView(text("Zeytin Defteri", 15, MUTED, true));
        TextView h = text("Zeytin Defteri'ni nasıl kullanacaksın?", 28, TEXT, true);
        h.setLineSpacing(0, 1.05f);
        body.addView(h, margins(-1, -2, 14));
        body.addView(text("Sana göre olanları öne çıkaralım. Sonra Ayarlar'dan istediğin zaman değiştirebilirsin.",
                15, MUTED, false), margins(-1, -2, 8));

        String[] titles = {"Üreticiyim", "Tezgâhçıyım", "İkisi de"};
        String[] subs = {"Bahçe, elek kilosu ve tutarı, satış, alacak, hava durumu",
                "Kantarda alım, alım fişi ve QR, gün sonu, kasa, tüccar cari hesabı",
                "Hem bahçem var hem tezgâh tutuyorum. Üstte Bahçe / Tezgâh geçişi olur."};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(18), dp(16), dp(18), dp(16));
            card.setMinimumHeight(dp(96));
            card.addView(text(titles[i], 20, TEXT, true));
            TextView s = text(subs[i], 14, MUTED, false);
            s.setLineSpacing(0, 1.1f);
            card.addView(s, margins(-1, -2, 6));
            card.setOnClickListener(v -> { picked = roles[k]; paint(); });
            cards[i] = card;
            body.addView(card, margins(-1, -2, i == 0 ? 22 : 12));
        }
        View spacer = new View(this);
        body.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView go = text("Devam", 18, BG, true);
        go.setGravity(Gravity.CENTER);
        go.setBackground(round(GREEN, 0, 18));
        go.setContentDescription("Seçimi kaydet ve devam et");
        go.setOnClickListener(v -> done());
        body.addView(go, margins(-1, dp(56), 24));
        setContentView(scroll);
        paint();
    }

    private void paint() {
        for (int i = 0; i < 3; i++) {
            boolean on = roles[i].equals(picked);
            cards[i].setBackground(round(on ? GREEN_DIM : CARD, on ? GREEN : LINE, 20));
            cards[i].setContentDescription(((TextView) ((LinearLayout) cards[i]).getChildAt(0)).getText() + (on ? ", seçili" : ""));
        }
    }

    private void done() {
        setRole(this, picked);
        if (firstRun && !UserRole.needsFirstGarden(picked)) {
            // Tezgâhçı: bahçe eklemeden doğrudan Tezgâh. İlk kurulum tamamlandı sayılır.
            getSharedPreferences("ZeytinDefteriPrefs", MODE_PRIVATE).edit()
                    .putBoolean("welcome_completed", true).putBoolean("onboarding_completed", true).apply();
        }
        if (firstRun && UserRole.needsFirstGarden(picked)) {
            setLastView(this, UserRole.VIEW_GARDEN);
            startActivity(new Intent(this, AddGardenActivity.class).putExtra("first_garden", true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
            return;
        }
        if (fromFeed && !UserRole.TRADER.equals(picked)) {
            setLastView(this, UserRole.VIEW_GARDEN);
            finish();
            return;
        }
        Intent next;
        if (UserRole.TRADER.equals(picked)) {
            setLastView(this, UserRole.VIEW_TEZGAH);
            next = new Intent(this, TezgahHomeActivity.class);
        } else {
            setLastView(this, UserRole.VIEW_GARDEN);
            next = new Intent(this, MainActivity.class).putExtra(TezgahHomeActivity.EXTRA_STAY_FEED, true);
        }
        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(next);
        finish();
    }

    private TextView text(String v, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(v);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int fill, int stroke, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(r));
        if (stroke != 0) g.setStroke(dp(2), stroke);
        return g;
    }

    private LinearLayout.LayoutParams margins(int w, int h, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.topMargin = dp(top);
        return lp;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
