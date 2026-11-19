package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * "Öğrendiklerim" (İP6): uygulamanın çiftçi hakkında öğrendiği her şey görülür ve silinir. Ayarlar → Uygulama →
 * Öğrendiklerim ya da asistana "öğrendiklerini göster". İçerik LearnedScreen'den (saf, testli) gelir; burada yalnız
 * çizilir. Silinen söz asistanda hemen etkisini kaybeder (aynı sözlük, her seferinde dosyadan okunur).
 * "Fark ettiklerim" bölümü İP5 (kayıtlardan sezilen alışkanlıklar) bağlanana dek boştur.
 */
public final class LearnedActivity extends Activity {
    private int bg;
    private int card;
    private int text;
    private int muted;
    private int danger;
    private LinearLayout body;
    private LearnedTerms.Store store;

    /** Asistandan ya da Ayarlar'dan açar. */
    static void open(Context context) {
        Intent intent = new Intent(context, LearnedActivity.class);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!new SessionManager(this).hasSession()) {
            finish();
            return;
        }
        bg = GreenTheme.color(R.color.zd_bg);
        card = GreenTheme.color(R.color.zd_card);
        text = GreenTheme.color(R.color.zd_text);
        muted = GreenTheme.color(R.color.zd_muted);
        danger = GreenTheme.color(R.color.zd_danger);
        DatabaseHelper db = new DatabaseHelper(this);
        String owner;
        try {
            owner = db.getActiveOwnerUid();
        } finally {
            db.close();
        }
        // Asistanla aynı sözlük (ZeytinAssistantController.learned: aynı hesap).
        store = LearnedTermsDb.store(this, owner == null ? "" : owner);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(6), dp(16), dp(28));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (body != null) {
            render();
        }
    }

    private void render() {
        body.removeAllViews();
        body.addView(header());
        TextView intro = label(LearnedScreen.INTRO, 16, muted, false);
        intro.setPadding(dp(4), 0, dp(4), dp(8));
        body.addView(intro);
        List<LearnedTerms.Term> terms = store.all();
        List<LearnedScreen.Row> taught = LearnedScreen.taught(terms, System.currentTimeMillis(), TimeZone.getDefault());
        // İP5 bağlanana dek fark edilen alışkanlık yok: bölüm boş ve hazır.
        List<LearnedScreen.Row> noticed = LearnedScreen.noticed(new ArrayList<>());
        List<View> taughtViews = new ArrayList<>();
        if (taught.isEmpty()) {
            taughtViews.add(note(LearnedScreen.EMPTY));
        }
        for (int i = 0; i < taught.size(); i++) {
            if (i > 0) {
                taughtViews.add(SettingsView.separator(this));
            }
            taughtViews.add(row(taught.get(i), true));
        }
        body.addView(SettingsView.section(this, LearnedScreen.TAUGHT.toUpperCase(new java.util.Locale("tr", "TR")),
                taughtViews.toArray(new View[0])));
        List<View> noticedViews = new ArrayList<>();
        if (noticed.isEmpty()) {
            noticedViews.add(note(LearnedScreen.NOTICED_EMPTY));
        }
        for (int i = 0; i < noticed.size(); i++) {
            if (i > 0) {
                noticedViews.add(SettingsView.separator(this));
            }
            noticedViews.add(row(noticed.get(i), false));
        }
        body.addView(SettingsView.section(this, LearnedScreen.NOTICED.toUpperCase(new java.util.Locale("tr", "TR")),
                noticedViews.toArray(new View[0])));
        if (!terms.isEmpty()) {
            body.addView(forgetAllButton());
        }
    }

    private View header() {
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setMinimumHeight(dp(56));
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_elek_back_chevron);
        back.setColorFilter(text);
        back.setPadding(dp(11), dp(11), dp(11), dp(11));
        back.setContentDescription("Geri");
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(44), dp(48));
        bp.setMarginStart(-dp(10));
        head.addView(back, bp);
        head.addView(label(LearnedScreen.TITLE, 24, text, true), new LinearLayout.LayoutParams(0, -2, 1));
        return head;
    }

    /** Bir satır: büyük başlık, altında tek satır açıklama, sağda büyük "Sil". */
    private View row(LearnedScreen.Row r, boolean taught) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(72));
        row.setPadding(dp(14), dp(10), dp(8), dp(10));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(label(r.title, 19, text, true));
        TextView detail = label(r.detail, 15, muted, false);
        detail.setPadding(0, dp(3), 0, 0);
        copy.addView(detail);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        if (!taught) {
            // Fark edilenlerin silinmesi İP5 ile gelir; o zamana dek bu bölümde satır yok.
            return row;
        }
        TextView delete = label("Sil", 17, danger, true);
        delete.setGravity(Gravity.CENTER);
        delete.setMinWidth(dp(64));
        delete.setMinHeight(dp(48));
        delete.setContentDescription(r.deleteSpoken);
        delete.setOnClickListener(v -> forget(r.key));
        row.addView(delete, new LinearLayout.LayoutParams(-2, -2));
        return row;
    }

    /** Tek söz: onaysız silinir (asistanın kendi notu, çiftçinin açık isteği; AGENTS.md notu). */
    private void forget(String key) {
        LearnedTerms.Term t = store.get(key);
        if (t == null) {
            render();
            return;
        }
        store.remove(key);
        Toast.makeText(this, LearnedScreen.forgotten(t.word), Toast.LENGTH_SHORT).show();
        render();
    }

    private View forgetAllButton() {
        TextView all = label(LearnedScreen.FORGET_ALL, 17, danger, true);
        all.setGravity(Gravity.CENTER);
        all.setMinHeight(dp(56));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(card);
        shape.setCornerRadius(dp(12));
        shape.setStroke(dp(1), danger);
        all.setBackground(shape);
        all.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage(LearnedScreen.FORGET_ALL_ASK)
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton(LearnedScreen.FORGET_ALL, (d, w) -> forgetAll())
                .show());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(18), 0, 0);
        all.setLayoutParams(lp);
        return all;
    }

    /** Hepsini unut (onaylı): öğretilen bütün sözler; kullanılamayanlar da. */
    private void forgetAll() {
        for (LearnedTerms.Term t : store.all()) {
            store.remove(t.term);
        }
        Toast.makeText(this, "Tamam, hepsini unuttum.", Toast.LENGTH_SHORT).show();
        render();
    }

    private View note(String message) {
        TextView t = label(message, 17, text, false);
        t.setPadding(dp(14), dp(14), dp(14), dp(14));
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
