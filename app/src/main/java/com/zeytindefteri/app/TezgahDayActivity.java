package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Alım defteri: tüccarın günlük alımları tablo görünümünde.
 * - Solda sabit "Üretici" sütunu, sağda yana kayan sütunlar (kasa, numara kiloları, net, ortalama, tutar, ödenen, kalan).
 * - En altta TOPLAM satırı; üstte gün özeti; ‹ › ile önceki günler.
 * - Satıra dokununca: QR göster, fişi gönder, kalan borcu ödedim, sil.
 * - "Rapor": alım tablosu + akşam hesabı tek sayfa (TezgahReportActivity), resim olarak gönderilir.
 * - Altta "Akşam hesabı": tüccara teslim, fire, komisyon, avans.
 */
public final class TezgahDayActivity extends Activity {
    private static final int BG = Color.parseColor("#050F16");
    private static final int CARD = Color.parseColor("#0F2230");
    private static final int ROW_ALT = Color.parseColor("#0B1A24");
    private static final int HEAD = Color.parseColor("#163142");
    private static final int LINE = Color.parseColor("#2A4452");
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.parseColor("#A9BAB6");
    private static final int GREEN = Color.parseColor("#8BE06A");
    private static final int GREEN_DIM = Color.parseColor("#1E3D2C");
    private static final int AMBER = Color.parseColor("#F2C35B");
    private static final Locale TR = new Locale("tr", "TR");

    private static final int ROW_H = 50, HEAD_H = 40, NAME_W = 132;
    private static final String[] COLS = {"Kasa", "Double", "1", "2", "3", "4", "Yağlık", "Net kg", "Ort ₺/kg", "Tutar", "Ödenen", "Kalan"};
    private static final int[] WIDTHS = {54, 64, 58, 58, 58, 58, 64, 78, 82, 96, 96, 92};

    private DatabaseHelper db;
    private String owner = "";
    private final Calendar day = Calendar.getInstance();
    private LinearLayout body;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!new SessionManager(this).hasSession()) { finish(); return; }
        db = new DatabaseHelper(this);
        owner = db.getActiveOwnerUid();
        if (owner == null) owner = "";
        if (state != null) day.setTimeInMillis(state.getLong("day", day.getTimeInMillis()));
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(4), dp(12), dp(24));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        render();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putLong("day", day.getTimeInMillis());
    }

    @Override protected void onDestroy() {
        if (db != null) db.close();
        super.onDestroy();
    }

    private String iso() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(day.getTime()); }

    private boolean isToday() {
        Calendar now = Calendar.getInstance();
        return now.get(Calendar.YEAR) == day.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR);
    }

    private List<TezgahPurchases.Purchase> load() {
        try { return TezgahPurchases.forDay(db.getWritableDatabase(), owner, iso()); }
        catch (RuntimeException e) { return new java.util.ArrayList<>(); }
    }

    // ================================================================== ekran

    private void render() {
        body.removeAllViews();
        final List<TezgahPurchases.Purchase> list = load();
        body.addView(header(list));
        body.addView(dateBar(), margins(-1, dp(48), 6));
        body.addView(summary(list), margins(-1, -2, 10));
        if (list.isEmpty()) {
            TextView empty = text(isToday() ? "Bugün henüz alım yok. Tezgahta bir alımı bitirince burada satır olarak görünür."
                    : "Bu gün alım yok.", 15, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(32), dp(16), dp(32));
            body.addView(empty);
            return;
        }
        body.addView(table(list), margins(-1, -2, 12));
        TextView hint = text("Satıra dokun: QR göster · fişi gönder · borcu kapat · sil. Tablo sağa kayar.", 12, MUTED, false);
        body.addView(hint, margins(-1, -2, 8));
        body.addView(closingCard(list), margins(-1, -2, 16));
    }

    // ================================================================== akşam hesabı

    private TezgahClosing closing;

    /** Günün kapanışı: kayıtlı teslim/komisyon/avans; yoksa teslim = alınan, komisyon son kullanılan. */
    private TezgahClosing loadClosing(List<TezgahPurchases.Purchase> list) {
        TezgahClosing c = TezgahClosing.fromPurchases(list);
        boolean saved = false;
        try { saved = TezgahClosing.load(db.getWritableDatabase(), owner, iso(), c); } catch (RuntimeException ignored) { }
        if (!saved) c.rate = getSharedPreferences("zd_tezgah", MODE_PRIVATE).getFloat("rate_" + owner, 0f);
        return c;
    }

    private void saveClosing() {
        if (closing == null) return;
        try {
            TezgahClosing.save(db.getWritableDatabase(), owner, iso(), closing, System.currentTimeMillis());
            getSharedPreferences("zd_tezgah", MODE_PRIVATE).edit().putFloat("rate_" + owner, (float) closing.rate).apply();
        } catch (RuntimeException ignored) {
            // Kaydedilemezse ekrandaki hesap yine doğru; bir sonraki değişiklikte tekrar denenir.
        }
    }

    /**
     * Tüccara teslim: numara numara tüccarın kantarındaki kilo (başta alınanla aynı), komisyon ve avans.
     * Fire, komisyon ve "tüccar ödeyecek" canlı hesaplanır; WhatsApp'tan tüccara gönderilir.
     */
    private View closingCard(final List<TezgahPurchases.Purchase> list) {
        closing = loadClosing(list);
        final TezgahClosing c = closing;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(14));
        box.setBackground(round(CARD, GREEN_DIM, 16));

        TextView head = text("AKŞAM HESABI · TÜCCARA TESLİM", 12, GREEN, true);
        head.setLetterSpacing(0.08f);
        box.addView(head);
        box.addView(text("Tüccarın kantarında çıkan kiloları yaz; fire ve hesap kendiliğinden çıkar.", 13, MUTED, false),
                margins(-1, -2, 4));

        // Sütun başlıkları
        LinearLayout hr = new LinearLayout(this);
        hr.addView(text("Numara", 12, MUTED, true), new LinearLayout.LayoutParams(0, -2, 1.1f));
        TextView h2 = text("Alınan", 12, MUTED, true); h2.setGravity(Gravity.END);
        hr.addView(h2, new LinearLayout.LayoutParams(0, -2, 0.9f));
        TextView h3 = text("Teslim", 12, MUTED, true); h3.setGravity(Gravity.CENTER);
        hr.addView(h3, new LinearLayout.LayoutParams(0, -2, 1.1f));
        TextView h4 = text("Fire", 12, MUTED, true); h4.setGravity(Gravity.END);
        hr.addView(h4, new LinearLayout.LayoutParams(0, -2, 0.8f));
        box.addView(hr, margins(-1, -2, 12));

        final TextView[] fires = new TextView[TezgahClosing.N];
        final LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        final Runnable refresh = () -> {
            for (int g = 0; g < TezgahClosing.N; g++) {
                if (fires[g] == null) continue;
                double f = c.fire(g);
                fires[g].setText(Math.abs(f) < 0.05 ? "—" : (f > 0 ? "−" : "+") + num(Math.abs(f)));
                fires[g].setTextColor(Math.abs(f) < 0.05 ? MUTED : (f > 0 ? AMBER : GREEN));
            }
            renderClosingResults(results, c);
        };
        for (int g = 0; g < TezgahClosing.N; g++) {
            if (c.bought[g] <= 0 && c.delivered[g] <= 0) continue;
            final int grade = g;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(48));
            row.addView(text(TezgahModel.LABELS[g], 15, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1.1f));
            TextView b = text(num(c.bought[g]), 15, TEXT, false);
            b.setGravity(Gravity.END);
            row.addView(b, new LinearLayout.LayoutParams(0, -2, 0.9f));
            final android.widget.EditText d = field(TezgahQr.field(c.delivered[g]), "0");
            d.setContentDescription(TezgahModel.LABELS[g] + " teslim kilosu");
            d.addTextChangedListener(watch(() -> {
                c.delivered[grade] = Math.max(0, TezgahModel.parse(d.getText().toString()));
                refresh.run();
                saveClosing();
            }));
            LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0, dp(42), 1.1f);
            inputLp.setMargins(dp(8), dp(3), dp(4), dp(3));
            row.addView(d, inputLp);
            TextView f = text("", 15, MUTED, true);
            f.setGravity(Gravity.END);
            fires[g] = f;
            row.addView(f, new LinearLayout.LayoutParams(0, -2, 0.8f));
            box.addView(row);
        }

        // Komisyon ve avans
        LinearLayout money = new LinearLayout(this);
        LinearLayout rc = new LinearLayout(this);
        rc.setOrientation(LinearLayout.VERTICAL);
        rc.addView(text("Komisyon (₺/kg)", 12, MUTED, true));
        final android.widget.EditText rate = field(c.rate > 0 ? TezgahQr.field(c.rate) : "", "0");
        rate.setContentDescription("Kilo başı komisyon, lira");
        rate.addTextChangedListener(watch(() -> { c.rate = Math.max(0, TezgahModel.parse(rate.getText().toString())); refresh.run(); saveClosing(); }));
        rc.addView(rate, new LinearLayout.LayoutParams(-1, dp(46)));
        money.addView(rc, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout ac = new LinearLayout(this);
        ac.setOrientation(LinearLayout.VERTICAL);
        ac.addView(text("Sabah alınan avans (₺)", 12, MUTED, true));
        final android.widget.EditText adv = field(c.advance > 0 ? TezgahQr.field(c.advance) : "", "0");
        adv.setContentDescription("Tüccardan alınan avans, lira");
        adv.addTextChangedListener(watch(() -> { c.advance = Math.max(0, TezgahModel.parse(adv.getText().toString())); refresh.run(); saveClosing(); }));
        ac.addView(adv, new LinearLayout.LayoutParams(-1, dp(46)));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, -2, 1);
        ap.setMarginStart(dp(10));
        money.addView(ac, ap);
        box.addView(money, margins(-1, -2, 12));

        box.addView(results, margins(-1, -2, 12));
        refresh.run();

        TextView send = text("Tüccara gönder (WhatsApp)", 16, BG, true);
        send.setGravity(Gravity.CENTER);
        send.setBackground(round(GREEN, 0, 16));
        send.setOnClickListener(v -> {
            saveClosing();
            String label = new SimpleDateFormat("d MMMM yyyy", TR).format(day.getTime());
            TezgahShare.shareText(this, c.summary(label, TezgahShare.traderName()), "");
        });
        box.addView(send, margins(-1, dp(52), 14));
        return box;
    }

    private void renderClosingResults(LinearLayout results, TezgahClosing c) {
        results.removeAllViews();
        resultRow(results, "Teslim", TezgahModel.kg(c.deliveredKg()), TEXT);
        String fire = TezgahModel.kg(c.fireKg()) + "  (%" + TezgahClosing.oneDecimal(c.firePercent()) + ")"
                + (Math.abs(c.fireValue()) >= 1 ? "  ≈ " + TezgahModel.tl(c.fireValue()) : "");
        resultRow(results, "Fire", fire, Math.abs(c.fireKg()) < 0.05 ? MUTED : (c.fireKg() > 0 ? AMBER : GREEN));
        resultRow(results, "Üreticilere tutar", TezgahModel.tl(c.producerTotal), TEXT);
        if (c.producerDue() > 0.5) resultRow(results, "  · üreticilere kalan borç", TezgahModel.tl(c.producerDue()), AMBER);
        resultRow(results, "Komisyon", c.rate > 0 ? TezgahModel.kg(c.deliveredKg()) + " × " + TezgahModel.money(c.rate)
                + " = " + TezgahModel.tl(c.commission()) : "—", c.rate > 0 ? GREEN : MUTED);
        if (c.advance > 0) resultRow(results, "Avans", "− " + TezgahModel.tl(c.advance), TEXT);
        View line = new View(this);
        line.setBackgroundColor(LINE);
        results.addView(line, margins(-1, Math.max(1, dp(1)), 6));
        double owes = c.traderOwes();
        LinearLayout total = new LinearLayout(this);
        total.setGravity(Gravity.CENTER_VERTICAL);
        total.addView(text(owes >= 0 ? "TÜCCAR ÖDEYECEK" : "TEZGAH İADE EDECEK", 13, MUTED, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        total.addView(text(TezgahModel.tl(Math.abs(owes)), 24, owes >= 0 ? GREEN : AMBER, true));
        results.addView(total, margins(-1, -2, 8));
    }

    private void resultRow(LinearLayout parent, String label, String value, int color) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(28));
        r.addView(text(label, 14, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = text(value, 15, color, true);
        v.setGravity(Gravity.END);
        r.addView(v);
        parent.addView(r);
    }

    private android.widget.EditText field(String value, String hint) {
        android.widget.EditText e = new android.widget.EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
        e.setSingleLine(true);
        e.setSelectAllOnFocus(true);
        e.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        e.setTextSize(16);
        e.setTypeface(Typeface.DEFAULT_BOLD);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setPadding(dp(10), 0, dp(10), 0);
        e.setBackground(round(BG, LINE, 10));
        return e;
    }

    private android.text.TextWatcher watch(Runnable r) {
        return new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) { r.run(); }
        };
    }

    private View header(List<TezgahPurchases.Purchase> list) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(52));
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_elek_back_chevron);
        back.setColorFilter(TEXT);
        back.setPadding(dp(11), dp(11), dp(11), dp(11));
        back.setContentDescription("Geri");
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(40), dp(44));
        bp.setMarginStart(-dp(10));
        row.addView(back, bp);
        row.addView(text("Alım defteri", 21, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView excel = text("Rapor", 14, list.isEmpty() ? MUTED : GREEN, true);
        excel.setGravity(Gravity.CENTER);
        excel.setPadding(dp(14), 0, dp(14), 0);
        excel.setBackground(round(Color.TRANSPARENT, list.isEmpty() ? LINE : GREEN, 20));
        excel.setContentDescription("Gün raporunu aç: alım tablosu ve akşam hesabı, resim olarak gönderilebilir");
        excel.setOnClickListener(v -> {
            if (list.isEmpty()) return;
            saveClosing();
            startActivity(new Intent(this, TezgahReportActivity.class).putExtra(TezgahReportActivity.EXTRA_DATE, iso()));
        });
        row.addView(excel, new LinearLayout.LayoutParams(-2, dp(40)));
        return row;
    }

    private View dateBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(round(CARD, LINE, 16));
        TextView prev = text("‹", 26, GREEN, true);
        prev.setGravity(Gravity.CENTER);
        prev.setContentDescription("Önceki gün");
        prev.setOnClickListener(v -> { day.add(Calendar.DAY_OF_YEAR, -1); render(); });
        bar.addView(prev, new LinearLayout.LayoutParams(dp(56), -1));
        String label = new SimpleDateFormat("d MMMM yyyy, EEEE", TR).format(day.getTime());
        TextView mid = text(isToday() ? "Bugün · " + label : label, 16, TEXT, true);
        mid.setGravity(Gravity.CENTER);
        bar.addView(mid, new LinearLayout.LayoutParams(0, -1, 1));
        boolean today = isToday();
        TextView next = text("›", 26, today ? LINE : GREEN, true);
        next.setGravity(Gravity.CENTER);
        next.setContentDescription("Sonraki gün");
        next.setEnabled(!today);
        next.setOnClickListener(v -> { if (!isToday()) { day.add(Calendar.DAY_OF_YEAR, 1); render(); } });
        bar.addView(next, new LinearLayout.LayoutParams(dp(56), -1));
        return bar;
    }

    private View summary(List<TezgahPurchases.Purchase> list) {
        int kasa = 0;
        double kg = 0, gross = 0, paid = 0;
        for (TezgahPurchases.Purchase p : list) { kasa += p.kasa; kg += p.kg; gross += p.gross; paid += p.paid; }
        double due = Math.max(0, gross - paid);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout r1 = new LinearLayout(this), r2 = new LinearLayout(this);
        r1.addView(tile("TUTAR", TezgahModel.tl(gross), GREEN), tileParams(false));
        r1.addView(tile("NET KİLO", TezgahModel.kg(kg), TEXT), tileParams(true));
        r2.addView(tile("ORTALAMA", kg > 0 ? TezgahModel.money(gross / kg) + "/kg" : "—", TEXT), tileParams(false));
        r2.addView(tile(due > 0.5 ? "BORÇ" : "ALIM", due > 0.5 ? TezgahModel.tl(due) : list.size() + " alım · " + kasa + " kasa",
                due > 0.5 ? AMBER : TEXT), tileParams(true));
        grid.addView(r1);
        grid.addView(r2, margins(-1, -2, 8));
        return grid;
    }

    private View tile(String label, String value, int color) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(14), dp(10), dp(12), dp(10));
        t.setBackground(round(CARD, LINE, 14));
        TextView l = text(label, 11, MUTED, true);
        l.setLetterSpacing(0.08f);
        t.addView(l);
        TextView v = text(value, 19, color, true);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(v, margins(-1, -2, 2));
        return t;
    }

    private LinearLayout.LayoutParams tileParams(boolean right) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        if (right) lp.setMarginStart(dp(8));
        return lp;
    }

    // ================================================================== tablo

    /** Solda sabit ad sütunu + sağda yana kayan sütunlar; satırlar sabit yükseklikte hizalı. */
    private View table(final List<TezgahPurchases.Purchase> list) {
        LinearLayout frame = new LinearLayout(this);
        frame.setBackground(round(CARD, LINE, 14));
        frame.setPadding(dp(1), dp(1), dp(1), dp(1));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);

        // Başlık
        left.addView(headCell("Üretici", NAME_W, Gravity.START | Gravity.CENTER_VERTICAL));
        LinearLayout head = new LinearLayout(this);
        for (int c = 0; c < COLS.length; c++) head.addView(headCell(COLS[c], WIDTHS[c], Gravity.END | Gravity.CENTER_VERTICAL));
        right.addView(head);

        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.US);
        int kasa = 0;
        double kg = 0, gross = 0, paid = 0;
        double[] gsum = new double[TezgahModel.GRADES.length];
        for (int i = 0; i < list.size(); i++) {
            final TezgahPurchases.Purchase p = list.get(i);
            int bg = i % 2 == 0 ? CARD : ROW_ALT;
            double[] g = TezgahModel.decode(p.data).netArray();
            for (int k = 0; k < g.length; k++) gsum[k] += g[k];
            kasa += p.kasa; kg += p.kg; gross += p.gross; paid += p.paid;

            LinearLayout nameCell = new LinearLayout(this);
            nameCell.setOrientation(LinearLayout.VERTICAL);
            nameCell.setGravity(Gravity.CENTER_VERTICAL);
            nameCell.setPadding(dp(10), 0, dp(6), 0);
            nameCell.setBackgroundColor(bg);
            TextView n = text(p.seller.isEmpty() ? "Adsız" : p.seller, 14, TEXT, true);
            n.setSingleLine(true);
            n.setEllipsize(TextUtils.TruncateAt.END);
            nameCell.addView(n);
            nameCell.addView(text(hm.format(new Date(p.createdAt)), 11, MUTED, false));
            nameCell.setOnClickListener(v -> actions(p));
            left.addView(nameCell, new LinearLayout.LayoutParams(dp(NAME_W), dp(ROW_H)));
            left.addView(rule(NAME_W));

            LinearLayout r = new LinearLayout(this);
            r.setBackgroundColor(bg);
            r.setOnClickListener(v -> actions(p));
            String[] vals = {
                    String.valueOf(p.kasa),
                    num(g[0]), num(g[1]), num(g[2]), num(g[3]), num(g[4]), num(g[5]),
                    num(p.kg),
                    p.kg > 0 ? price(p.gross / p.kg) : "—",
                    TezgahModel.tl(p.gross),
                    TezgahModel.tl(p.paid),
                    p.remaining() > 0.5 ? TezgahModel.tl(p.remaining()) : "—"};
            for (int c = 0; c < vals.length; c++) {
                int color = c == 9 ? GREEN : (c == 11 && p.remaining() > 0.5 ? AMBER : ("—".equals(vals[c]) ? MUTED : TEXT));
                r.addView(cell(vals[c], WIDTHS[c], color, c == 7 || c == 9));
            }
            right.addView(r, new LinearLayout.LayoutParams(-2, dp(ROW_H)));
            right.addView(rule(totalWidth()));
        }

        // TOPLAM satırı
        TextView tl = text("TOPLAM · " + list.size(), 14, BG, true);
        tl.setGravity(Gravity.CENTER_VERTICAL);
        tl.setPadding(dp(10), 0, dp(6), 0);
        tl.setBackgroundColor(GREEN);
        left.addView(tl, new LinearLayout.LayoutParams(dp(NAME_W), dp(ROW_H)));
        LinearLayout tr = new LinearLayout(this);
        tr.setBackgroundColor(GREEN);
        String[] tv = {
                String.valueOf(kasa),
                num(gsum[0]), num(gsum[1]), num(gsum[2]), num(gsum[3]), num(gsum[4]), num(gsum[5]),
                num(kg),
                kg > 0 ? price(gross / kg) : "—",
                TezgahModel.tl(gross),
                TezgahModel.tl(paid),
                gross - paid > 0.5 ? TezgahModel.tl(gross - paid) : "—"};
        for (int c = 0; c < tv.length; c++) tr.addView(cell(tv[c], WIDTHS[c], BG, true));
        right.addView(tr, new LinearLayout.LayoutParams(-2, dp(ROW_H)));

        frame.addView(left, new LinearLayout.LayoutParams(dp(NAME_W), -2));
        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        frame.addView(divider, new LinearLayout.LayoutParams(dp(2), -1));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(true);
        hs.addView(right);
        frame.addView(hs, new LinearLayout.LayoutParams(0, -2, 1));
        return frame;
    }

    private int totalWidth() {
        int w = 0;
        for (int v : WIDTHS) w += v;
        return w;
    }

    private TextView headCell(String label, int widthDp, int gravity) {
        TextView t = text(label, 12, MUTED, true);
        t.setGravity(gravity);
        t.setPadding(dp(10), 0, dp(10), 0);
        t.setBackgroundColor(HEAD);
        t.setSingleLine(true);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), dp(HEAD_H)));
        return t;
    }

    private TextView cell(String value, int widthDp, int color, boolean bold) {
        TextView t = text(value, 14, color, bold);
        t.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        t.setPadding(dp(6), 0, dp(10), 0);
        t.setSingleLine(true);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), -1));
        return t;
    }

    private View rule(int widthDp) {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), Math.max(1, dp(1) / 2)));
        return v;
    }

    /** Tabloda kilo: "238", "42,5", sıfırsa "—". */
    private static String num(double v) {
        if (v <= 0.04) return "—";
        String s = TezgahModel.kg(v);
        return s.substring(0, s.length() - 3);
    }

    private static String price(double v) {
        String s = TezgahModel.money(v);
        return s.substring(0, s.length() - 2);
    }

    // ================================================================== satır işlemleri

    private void actions(final TezgahPurchases.Purchase p) {
        final TezgahModel m = TezgahModel.decode(p.data);
        final String name = p.seller.isEmpty() ? "Adsız" : p.seller;
        final String receipt = m.receipt("Zeytin alım fişi", "Satan", p.seller, displayDate(p), p.paid);
        final String[] items = p.remaining() > 0.5
                ? new String[]{"QR göster", "Fişi gönder (WhatsApp)", "Kalan borcu ödedim (" + TezgahModel.tl(p.remaining()) + ")", "Sil"}
                : new String[]{"QR göster", "Fişi gönder (WhatsApp)", "Sil"};
        new AlertDialog.Builder(this)
                .setTitle(name + " · " + TezgahModel.kg(p.kg) + " · " + TezgahModel.tl(p.gross))
                .setItems(items, (d, i) -> {
                    String a = items[i];
                    if (a.startsWith("QR")) {
                        TezgahShare.showQr(this, m, p.date, p.seller, p.paid, receipt, p.phone);
                    } else if (a.startsWith("Fişi")) {
                        TezgahShare.shareText(this, receipt, p.phone);
                    } else if (a.startsWith("Kalan")) {
                        TezgahPurchases.setPaid(db.getWritableDatabase(), owner, p.id, p.gross);
                        toast("Borç kapandı ✓");
                        render();
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("Alım silinsin mi?")
                                .setMessage(name + " · " + TezgahModel.kg(p.kg) + " · " + TezgahModel.tl(p.gross))
                                .setPositiveButton("Sil", (x, y) -> {
                                    TezgahPurchases.delete(db.getWritableDatabase(), owner, p.id);
                                    render();
                                })
                                .setNegativeButton("Vazgeç", null)
                                .show();
                    }
                })
                .show();
    }

    private static String displayDate(TezgahPurchases.Purchase p) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(new Date(p.createdAt));
    }

    // ================================================================== yardımcılar

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }

    private LinearLayout.LayoutParams margins(int w, int h, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.topMargin = dp(topDp);
        return lp;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
