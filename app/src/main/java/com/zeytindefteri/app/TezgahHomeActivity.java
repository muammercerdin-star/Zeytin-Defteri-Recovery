package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Tezgâhçının ana ekranı: büyük "Tartım başlat", bugünün özeti ve akşam hesabı, alım defteri / cari hesap / kasa.
 * Tezgâhçı uygulamayı buradan açar. "İkisi de" seçen kullanıcıda üstte Bahçe / Tezgâh geçişi vardır.
 * Alt menü: Tezgâh · Defter · Cari · Kasa · Daha fazla (ayarlar, bildirimler, yedek, sohbet, kullanım şekli).
 */
public final class TezgahHomeActivity extends Activity {
    /** MainActivity'ye: tezgâha yönlendirme, doğrudan bahçe/üretici ekranında kal. */
    public static final String EXTRA_STAY_FEED = "stay_feed";

    private static final int BG = Color.parseColor("#050F16");
    private static final int CARD = Color.parseColor("#0F2230");
    private static final int LINE = Color.parseColor("#2A4452");
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.parseColor("#A9BAB6");
    private static final int GREEN = Color.parseColor("#8BE06A");
    private static final int AMBER = Color.parseColor("#F2C35B");
    private static final Locale TR = new Locale("tr", "TR");

    private DatabaseHelper db;
    private String owner = "";
    private LinearLayout body;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!new SessionManager(this).hasSession()) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
            return;
        }
        db = new DatabaseHelper(this);
        owner = db.getActiveOwnerUid();
        if (owner == null) owner = "";
        RoleActivity.setLastView(this, UserRole.VIEW_TEZGAH);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(14), dp(16), dp(20));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(nav(), new LinearLayout.LayoutParams(-1, dp(68)));
        setContentView(root);
        render();
        offerCloudRestore();
    }

    /** Yeni telefon: tezgâhta hiç kayıt yoksa ve bulutta yedek varsa bir kez sorulur. */
    private void offerCloudRestore() {
        if (!TezgahCloudBackup.shouldOfferRestore(this) || TezgahCloudBackup.hasLocalRows(this)) return;
        TezgahCloudBackup.fetchMeta((meta, reached) -> {
            if (isFinishing() || isDestroyed()) return;
            if (!reached) return;               // internet yok: bir dahaki açılışta tekrar bakılır
            TezgahCloudBackup.markOffered(this);
            if (meta == null) return;           // bulutta yedek yok
            new AlertDialog.Builder(this)
                    .setTitle("Bulutta yedeğin var")
                    .setMessage(new SimpleDateFormat("d MMMM yyyy HH:mm", TR).format(new Date(meta.created))
                            + " tarihli tezgâh yedeği bulundu:\n" + meta.summary + "\n\nBu telefona geri yükleyeyim mi?")
                    .setPositiveButton("Geri yükle", (d, w) -> TezgahCloudBackup.restore(this, meta, (ok, msg) -> {
                        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show();
                        if (ok && !isFinishing()) render();
                    }))
                    .setNegativeButton("Şimdi değil", null)
                    .show();
        });
    }

    @Override protected void onRestart() {
        super.onRestart();
        if (body != null) render();
    }

    @Override protected void onDestroy() {
        if (db != null) db.close();
        super.onDestroy();
    }

    private static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }

    private void render() {
        body.removeAllViews();
        if (UserRole.BOTH.equals(RoleActivity.role(this))) body.addView(viewSwitch(), margins(-1, -2, 0));

        TezgahPurchases.Day d;
        List<TezgahPurchases.Purchase> all;
        double[] ledger;
        TezgahCrates.Day crates;
        try {
            android.database.sqlite.SQLiteDatabase w = db.getWritableDatabase();
            d = TezgahPurchases.openSummary(w, owner);
            all = TezgahPurchases.all(w, owner, 100000);
            ledger = TezgahLedger.totals(TezgahLedger.entries(w, owner));
            crates = TezgahCrates.day(TezgahCrates.moves(w, owner), null, today());
        } catch (RuntimeException e) {
            d = new TezgahPurchases.Day();
            all = new ArrayList<>();
            ledger = new double[]{0, 0};
            crates = new TezgahCrates.Day();
        }

        body.addView(text(new SimpleDateFormat("d MMMM EEEE", TR).format(new Date()), 13, MUTED, false), margins(-1, -2, 14));
        body.addView(text("Tezgâh", 24, TEXT, true));
        String cloud = TezgahCloudBackup.statusLine(this);
        if (!cloud.isEmpty()) {
            TextView cl = text(cloud, 12, cloud.contains("bekliyor") || cloud.contains("büyük") ? AMBER : MUTED, false);
            cl.setOnClickListener(v -> TezgahCloudBackup.backup(this, true, (ok, msg) -> {
                if (!msg.isEmpty()) android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
                if (!isFinishing()) render();
            }));
            cl.setContentDescription(cloud + ". Şimdi yedeklemek için dokun.");
            body.addView(cl, margins(-1, -2, 2));
        }

        TextView start = text(d.count > 0 ? "▶  Tartıma devam et" : "▶  Tartım başlat", 21, BG, true);
        start.setGravity(Gravity.CENTER);
        start.setBackground(round(GREEN, 0, 20));
        start.setContentDescription("Tezgâhı aç: kantarda tartım");
        // Doğrudan tartım sihirbazı açılır (Double → … → Yağlık → satan → ödeme).
        start.setOnClickListener(v -> startActivity(new Intent(this, TezgahActivity.class)
                .putExtra(TezgahActivity.EXTRA_WIZARD, true)));
        body.addView(start, margins(-1, dp(64), 14));

        // Bugün (açık dönem)
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(14));
        card.setBackground(round(CARD, LINE, 20));
        TextView k = text("BUGÜN · AÇIK DÖNEM", 12, MUTED, true);
        k.setLetterSpacing(0.08f);
        card.addView(k);
        LinearLayout r1 = new LinearLayout(this);
        r1.addView(stat("Alım", d.count + " üretici", TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        r1.addView(stat("Net", TezgahModel.kg(d.kg), TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(r1, margins(-1, -2, 10));
        LinearLayout r2 = new LinearLayout(this);
        r2.addView(stat("Tutar", TezgahModel.tl(d.gross), TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        r2.addView(stat("Üreticiye borç", TezgahModel.tl(d.remaining()), d.remaining() > 0.5 ? AMBER : GREEN),
                new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(r2, margins(-1, -2, 10));
        TextView z = text("Akşam hesabı · gün sonu (Z)   ›", 15, d.count > 0 ? AMBER : GREEN, true);
        z.setGravity(Gravity.CENTER_VERTICAL);
        z.setPadding(dp(12), 0, dp(12), 0);
        z.setBackground(round(Color.TRANSPARENT, d.count > 0 ? AMBER : LINE, 14));
        z.setContentDescription("Alım defteri, akşam hesabı ve gün sonu");
        z.setOnClickListener(v -> startActivity(new Intent(this, TezgahDayActivity.class)));
        card.addView(z, margins(-1, dp(48), 12));
        body.addView(card, margins(-1, -2, 12));

        // Alım defteri · cari · kasa
        double debt = TezgahSearch.totalRemaining(all);
        int debtors = 0;
        for (TezgahSearch.Group g : TezgahSearch.byProducer(TezgahSearch.filter(all, "", true), true)) debtors++;
        body.addView(row("Alım defteri", debtors > 0 ? debtors + " borçlu · ara, ekstre, toplu ödeme" : "Ara, ekstre, toplu ödeme",
                debt > 0.5 ? TezgahModel.tl(debt) : "›", debt > 0.5,
                v -> startActivity(new Intent(this, TezgahDayActivity.class))), margins(-1, -2, 10));
        body.addView(row("Cari hesap · tüccar", "Tahsilat, satış, hesap dökümü",
                ledger[0] > 0.5 ? TezgahModel.tl(ledger[0]) : ledger[1] > 0.5 ? "borç " + TezgahModel.tl(ledger[1]) : "›",
                ledger[0] > 0.5 || ledger[1] > 0.5, v -> TezgahLedgerActivity.open(this, null)), margins(-1, -2, 8));
        boolean anyCrate = crates.carried != 0 || crates.in > 0 || crates.out > 0;
        int held = crates.held();
        body.addView(row("Kasa · aracıyla", anyCrate ? "Bugün gelen " + crates.in + " · giden " + crates.out : "Gelen / giden kasayı yaz",
                anyCrate ? (held >= 0 ? "sende " + held : "onda " + (-held)) : "›", anyCrate && held != 0,
                v -> TezgahCratesActivity.open(this, null)), margins(-1, -2, 8));
    }

    /** İkisi de: Bahçe / Tezgâh geçişi. */
    private View viewSwitch() {
        LinearLayout seg = new LinearLayout(this);
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        seg.setBackground(round(CARD, LINE, 16));
        TextView garden = text("Bahçe", 15, MUTED, true);
        garden.setGravity(Gravity.CENTER);
        garden.setContentDescription("Bahçe ekranına geç");
        garden.setOnClickListener(v -> {
            RoleActivity.setLastView(this, UserRole.VIEW_GARDEN);
            startActivity(new Intent(this, MainActivity.class).putExtra(EXTRA_STAY_FEED, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
        });
        seg.addView(garden, new LinearLayout.LayoutParams(0, dp(40), 1));
        TextView tez = text("Tezgâh", 15, BG, true);
        tez.setGravity(Gravity.CENTER);
        tez.setBackground(round(GREEN, 0, 12));
        tez.setContentDescription("Tezgâh, seçili");
        seg.addView(tez, new LinearLayout.LayoutParams(0, dp(40), 1));
        return seg;
    }

    private View stat(String label, String value, int color) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.addView(text(label, 12, MUTED, false));
        TextView v = text(value, 19, color, true);
        v.setSingleLine(true);
        c.addView(v);
        c.setContentDescription(label + " " + value);
        return c;
    }

    private View row(String title, String sub, String value, boolean warn, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.setMinimumHeight(dp(60));
        row.setBackground(round(CARD, LINE, 16));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(title, 16, TEXT, true));
        TextView s = text(sub, 12, MUTED, false);
        s.setSingleLine(true);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(s);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = text(value, 15, warn ? AMBER : GREEN, true);
        v.setPadding(dp(8), 0, 0, 0);
        row.addView(v);
        row.setContentDescription(title + ", " + sub + (value.equals("›") ? "" : ", " + value) + ". Açmak için dokun.");
        row.setOnClickListener(click);
        return row;
    }

    // ------------------------------------------------------------------ alt menü

    private View nav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0A1A24"));
        bg.setStroke(dp(1), Color.parseColor("#1B3341"));
        nav.setBackground(bg);
        nav.addView(navItem(R.drawable.ic_elek_nav, "Tezgâh", true, null), new LinearLayout.LayoutParams(0, -1, 1));
        nav.addView(navItem(R.drawable.ic_archive_document, "Defter", false,
                v -> startActivity(new Intent(this, TezgahDayActivity.class))), new LinearLayout.LayoutParams(0, -1, 1));
        nav.addView(navItem(R.drawable.ic_settings_users, "Cari", false,
                v -> TezgahLedgerActivity.open(this, null)), new LinearLayout.LayoutParams(0, -1, 1));
        nav.addView(navItem(R.drawable.ic_op_cloud, "Kasa", false,
                v -> TezgahCratesActivity.open(this, null)), new LinearLayout.LayoutParams(0, -1, 1));
        nav.addView(navItem(R.drawable.ic_settings_nav, "Daha fazla", false, v -> more()), new LinearLayout.LayoutParams(0, -1, 1));
        return nav;
    }

    private View navItem(int icon, String label, boolean on, View.OnClickListener click) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        ImageView i = new ImageView(this);
        i.setImageResource(icon);
        i.setColorFilter(on ? GREEN : MUTED);
        c.addView(i, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView t = text(label, 11, on ? GREEN : MUTED, on);
        t.setGravity(Gravity.CENTER);
        c.addView(t, margins(-2, -2, 3));
        c.setContentDescription(label + (on ? ", seçili" : ""));
        if (click != null) c.setOnClickListener(click);
        return c;
    }

    /** Daha fazla: yalnız tezgâh işleri (kullanım şekli, yedek, fiş yazıcısı); üretici ekranına geçiren şey yok. */
    private void more() {
        final String[] items = {"Kullanım şekli · " + UserRole.label(RoleActivity.role(this)), "Yedekle / Geri yükle", "Fiş yazıcısı"};
        new AlertDialog.Builder(this)
                .setItems(items, (dlg, which) -> {
                    switch (which) {
                        case 0: RoleActivity.open(this, false); break;
                        case 1: startActivity(new Intent(this, BackupActivity.class)); break;
                        default: TezgahPrinter.settings(this); break;
                    }
                })
                .show();
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
        if (stroke != 0) g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }

    private LinearLayout.LayoutParams margins(int w, int h, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.topMargin = dp(top);
        return lp;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
