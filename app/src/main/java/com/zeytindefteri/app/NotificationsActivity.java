package com.zeytindefteri.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Bildirim kutusu: telefonun ürettiği uyarılar (hava, alacak, gün sonu, takvim) ve Hatırlatmalar'a geçiş.
 * Açılınca hepsi okundu sayılır (zildeki sayı sıfırlanır); okunmamışlar yeşil noktalı görünür.
 */
public final class NotificationsActivity extends Activity {
    private DatabaseHelper db;
    private String owner = "";
    private LinearLayout body;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!new SessionManager(this).hasSession()) { finish(); return; }
        db = new DatabaseHelper(this);
        owner = db.getActiveOwnerUid();
        if (owner == null) owner = "";
        int bg = GreenTheme.color(R.color.zd_bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(6), dp(16), dp(24));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        askPermissionOnce();
        AlertCenter.onResume(this);
    }

    @Override protected void onResume() {
        super.onResume();
        if (body != null) render();
    }

    @Override protected void onDestroy() {
        if (db != null) db.close();
        super.onDestroy();
    }

    private void render() {
        body.removeAllViews();
        int text = GreenTheme.color(R.color.zd_text), muted = GreenTheme.color(R.color.zd_muted);
        int green = GreenTheme.color(R.color.zd_green), card = GreenTheme.color(R.color.zd_card);
        int border = GreenTheme.color(R.color.zd_border);

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
        head.addView(text("Bildirimler", 22, text, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView settings = text("Ayarlar", 14, green, true);
        settings.setPadding(dp(12), dp(10), 0, dp(10));
        settings.setContentDescription("Bildirim ayarları");
        settings.setOnClickListener(v -> showSettings());
        head.addView(settings);
        body.addView(head);

        // Hatırlatmalar (kullanıcının kendi kurduğu) ayrı sayfada kalır.
        LinearLayout rem = new LinearLayout(this);
        rem.setGravity(Gravity.CENTER_VERTICAL);
        rem.setPadding(dp(14), dp(12), dp(12), dp(12));
        rem.setBackground(round(card, border, 16));
        LinearLayout rc = new LinearLayout(this);
        rc.setOrientation(LinearLayout.VERTICAL);
        rc.addView(text("⏰  Hatırlatmalarım", 16, text, true));
        rc.addView(text("Kendi kurduğun ilaçlama, sulama, ödeme hatırlatmaları", 13, muted, false));
        rem.addView(rc, new LinearLayout.LayoutParams(0, -2, 1));
        rem.addView(text("›", 22, green, true));
        rem.setOnClickListener(v -> startActivity(new Intent(this, RemindersActivity.class)));
        body.addView(rem, margins(8));

        List<NotificationInbox.Item> items;
        try {
            items = NotificationInbox.list(db.getWritableDatabase(), owner, 100);
            NotificationInbox.markAllRead(db.getWritableDatabase(), owner);
        } catch (RuntimeException e) {
            items = new java.util.ArrayList<>();
        }
        if (items.isEmpty()) {
            TextView empty = text("Henüz bildirim yok.\n\nDon, hasatta yağmur ve kuvvetli rüzgâr; 14 günü geçen alacaklar; "
                    + "tezgahta alınmamış gün sonu ve ayın zeytin işi burada görünür.", 15, muted, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(36), dp(12), dp(12));
            body.addView(empty);
            return;
        }
        String lastGroup = "";
        for (NotificationInbox.Item it : items) {
            String group = group(it.createdAt);
            if (!group.equals(lastGroup)) {
                TextView g = text(group.toUpperCase(new Locale("tr", "TR")), 12, muted, true);
                g.setLetterSpacing(0.08f);
                body.addView(g, margins(18));
                lastGroup = group;
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            int accent = it.level >= 3 ? GreenTheme.color(R.color.zd_danger)
                    : it.level == 2 ? GreenTheme.color(R.color.zd_warning) : green;
            row.setBackground(round(card, it.read ? border : accent, 16));
            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = text(it.title, 16, text, true);
            top.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            top.addView(text(new SimpleDateFormat("HH:mm", Locale.US).format(new Date(it.createdAt)), 12, muted, false));
            row.addView(top);
            TextView b = text(it.body, 14, GreenTheme.color(R.color.zd_text_soft), false);
            b.setLineSpacing(0, 1.15f);
            row.addView(b, margins(4));
            final String kind = it.kind;
            row.setOnClickListener(v -> openTarget(kind));
            body.addView(row, margins(8));
        }
    }

    /** Bildirime dokununca ilgili ekran: alacak → Alıcılarım, gün sonu → Tezgah defteri. */
    private void openTarget(String kind) {
        try {
            if (AlertRules.MONEY.equals(kind)) startActivity(new Intent(this, BuyersActivity.class));
            else if (AlertRules.TEZGAH.equals(kind)) startActivity(new Intent(this, TezgahDayActivity.class));
        } catch (RuntimeException ignored) { }
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        for (int i = 0; i < AlertCenter.KINDS.length; i++) {
            final String kind = AlertCenter.KINDS[i];
            android.widget.Switch s = new android.widget.Switch(this);
            s.setText(AlertCenter.KIND_LABELS[i]);
            s.setTextSize(16);
            s.setChecked(AlertCenter.enabled(this, kind));
            s.setOnCheckedChangeListener((v, on) -> AlertCenter.setEnabled(this, kind, on));
            box.addView(s, new LinearLayout.LayoutParams(-1, dp(44)));
            TextView hint = new TextView(this);
            hint.setText(AlertCenter.KIND_HINTS[i]);
            hint.setTextSize(12);
            hint.setPadding(0, 0, 0, dp(10));
            box.addView(hint);
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Bildirim ayarları")
                .setView(box)
                .setPositiveButton("Tamam", null)
                .show();
    }

    /** Android 13+ bildirim izni: bir kez sorulur. */
    private void askPermissionOnce() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") == android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        android.content.SharedPreferences p = getSharedPreferences(AlertCenter.PREFS, MODE_PRIVATE);
        if (p.getBoolean("asked_permission", false)) return;
        p.edit().putBoolean("asked_permission", true).apply();
        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 77);
    }

    private static String group(long t) {
        Calendar c = Calendar.getInstance();
        Calendar d = Calendar.getInstance();
        d.setTimeInMillis(t);
        if (c.get(Calendar.YEAR) == d.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == d.get(Calendar.DAY_OF_YEAR)) return "Bugün";
        c.add(Calendar.DAY_OF_YEAR, -1);
        if (c.get(Calendar.YEAR) == d.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == d.get(Calendar.DAY_OF_YEAR)) return "Dün";
        return new SimpleDateFormat("d MMMM", new Locale("tr", "TR")).format(new Date(t));
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
        g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }

    private LinearLayout.LayoutParams margins(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        return lp;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
