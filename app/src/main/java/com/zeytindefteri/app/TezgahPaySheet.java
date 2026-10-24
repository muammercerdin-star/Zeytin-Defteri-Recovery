package com.zeytindefteri.app;

import android.app.Activity;
import android.app.Dialog;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Tezgah: üreticiye borç ödemesi penceresi (bir kısmı ya da tamamı). Toplam / ödenen / kalan, bugün verilen tutar,
 * hazır tutarlar, ödeme geçmişi. Kayıt TezgahPayments.pay ile; Z kilitli alımda da çalışır.
 */
public final class TezgahPaySheet {
    private static final int BG = Color.parseColor("#050F16");
    private static final int SHEET = Color.parseColor("#0C1C27");
    private static final int CARD = Color.parseColor("#0F2230");
    private static final int LINE = Color.parseColor("#2A4452");
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.parseColor("#A9BAB6");
    private static final int GREEN = Color.parseColor("#8BE06A");
    private static final int GREEN_DIM = Color.parseColor("#1E3D2C");
    private static final int AMBER = Color.parseColor("#F2C35B");

    private final Activity a;
    private final SQLiteDatabase db;
    private final String owner;
    private final TezgahPurchases.Purchase p;
    private final Runnable onSaved;
    private final float density;
    private EditText amount;
    private TextView note;

    private TezgahPaySheet(Activity a, SQLiteDatabase db, String owner, TezgahPurchases.Purchase p, Runnable onSaved) {
        this.a = a; this.db = db; this.owner = owner; this.p = p; this.onSaved = onSaved;
        this.density = a.getResources().getDisplayMetrics().density;
    }

    public static void show(Activity a, SQLiteDatabase db, String owner, TezgahPurchases.Purchase p, Runnable onSaved) {
        new TezgahPaySheet(a, db, owner, p, onSaved).open();
    }

    private void open() {
        final Dialog sheet = new Dialog(a);
        sheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(a);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(10), dp(20), dp(20));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SHEET);
        float r = dp(26);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        bg.setStroke(dp(1), LINE);
        scroll.setBackground(bg);
        scroll.addView(box);

        View handle = new View(a);
        handle.setBackground(round(LINE, 0, 3));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(44), dp(5));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(handle, hp);

        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(a);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Üreticiye ödeme", 21, TEXT, true));
        String when = p.createdAt > 0 ? new SimpleDateFormat("d MMM HH:mm", new Locale("tr", "TR")).format(new Date(p.createdAt)) : p.date;
        titles.addView(text((p.seller.isEmpty() ? "Adsız" : p.seller) + " · alım " + when + " · " + TezgahModel.kg(p.kg), 13, MUTED, false));
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = text("✕", 17, TEXT, true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(round(BG, LINE, 22));
        close.setContentDescription("Kapat");
        close.setOnClickListener(v -> sheet.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        box.addView(head, margins(12));

        LinearLayout stats = new LinearLayout(a);
        stats.addView(stat("Toplam", TezgahModel.tl(p.gross), CARD, LINE, TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams s2 = new LinearLayout.LayoutParams(0, -2, 1);
        s2.setMarginStart(dp(8));
        stats.addView(stat("Ödenen", TezgahModel.tl(p.paid), GREEN_DIM, GREEN, GREEN), s2);
        LinearLayout.LayoutParams s3 = new LinearLayout.LayoutParams(0, -2, 1);
        s3.setMarginStart(dp(8));
        stats.addView(stat("Kalan", TezgahModel.tl(p.remaining()), Color.parseColor("#3A3320"), AMBER, AMBER), s3);
        box.addView(stats, margins(14));

        boolean open = p.remaining() > 0.5;
        if (open) {
            box.addView(text("Bugün verilen tutar", 13, TEXT, true), margins(16));
            LinearLayout field = new LinearLayout(a);
            field.setGravity(Gravity.CENTER_VERTICAL);
            field.setPadding(dp(16), 0, dp(16), 0);
            field.setBackground(round(BG, GREEN, 14));
            amount = new EditText(a);
            amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            amount.setText(SalePayStatus.plain(p.remaining()));
            amount.setTextSize(26);
            amount.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            amount.setTextColor(TEXT);
            amount.setBackground(null);
            amount.setSingleLine(true);
            amount.setSelectAllOnFocus(true);
            amount.setContentDescription("Bugün verilen tutar, TL");
            field.addView(amount, new LinearLayout.LayoutParams(0, dp(60), 1));
            field.addView(text("₺", 18, MUTED, true));
            box.addView(field, margins(6));

            LinearLayout quick = new LinearLayout(a);
            addQuick(quick, "Kalanın tamamı", p.remaining(), true);
            for (double v : SalePayStatus.quickAmounts(p.remaining())) addQuick(quick, TezgahModel.tl(v), v, false);
            box.addView(quick, margins(8));
        }

        List<TezgahPayments.Payment> history = TezgahPayments.history(db, owner, p);
        if (!history.isEmpty()) {
            TextView h = text("ÖDEME GEÇMİŞİ", 11, MUTED, true);
            h.setLetterSpacing(0.08f);
            box.addView(h, margins(16));
            for (TezgahPayments.Payment x : history) {
                LinearLayout row = new LinearLayout(a);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(dp(34));
                String d = SalePayStatus.shortDate(x.date);
                row.addView(text((d.isEmpty() ? "" : d + " · ") + (x.atPurchase ? "alımda ödenen" : "sonradan ödeme"), 14, TEXT, false),
                        new LinearLayout.LayoutParams(0, -2, 1));
                row.addView(text(TezgahModel.tl(x.amount), 14, TEXT, true));
                box.addView(row);
            }
        }

        if (open) {
            note = text("", 13, GREEN, false);
            note.setPadding(dp(12), dp(10), dp(12), dp(10));
            note.setBackground(round(GREEN_DIM, GREEN_DIM, 12));
            box.addView(note, margins(14));
            amount.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int x, int y, int z) { }
                @Override public void onTextChanged(CharSequence s, int x, int y, int z) { }
                @Override public void afterTextChanged(Editable s) { renderNote(); }
            });
            renderNote();
            TextView save = text("Ödemeyi kaydet", 18, BG, true);
            save.setGravity(Gravity.CENTER);
            save.setBackground(round(GREEN, 0, 18));
            save.setOnClickListener(v -> save(sheet));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(56));
            sp.topMargin = dp(14);
            box.addView(save, sp);
        } else {
            TextView done = text("Bu alımın borcu kalmadı ✓", 15, GREEN, true);
            done.setGravity(Gravity.CENTER);
            done.setPadding(0, dp(18), 0, dp(4));
            box.addView(done);
        }

        sheet.setContentView(scroll);
        Window w = sheet.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        sheet.show();
    }

    private double entered() { return Math.max(0, TezgahModel.parse(amount.getText().toString())); }

    private void renderNote() {
        double v = entered();
        if (v <= 0.009) note.setText("Verilen tutarı yaz.");
        else if (v >= p.remaining() - 0.5) note.setText("Bu ödemeyle borç kapanır ✓");
        else note.setText("Bu ödemeden sonra " + TezgahModel.tl(p.remaining() - v) + " borç kalır.");
    }

    private void save(Dialog sheet) {
        double v = entered();
        if (v <= 0.009) { amount.setError("Tutar yaz"); return; }
        if (v > p.remaining() + 0.5) { amount.setError("Kalan " + TezgahModel.tl(p.remaining()) + "; fazlası yazılamaz"); return; }
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        boolean ok;
        try { ok = TezgahPayments.pay(db, owner, p.id, v, today, System.currentTimeMillis()); }
        catch (RuntimeException e) { ok = false; }
        if (!ok) { Toast.makeText(a, "Ödeme kaydedilemedi. Tekrar dene.", Toast.LENGTH_LONG).show(); return; }
        sheet.dismiss();
        boolean closed = v >= p.remaining() - 0.5;
        Toast.makeText(a, closed ? "Borç kapandı ✓" : "Ödeme kaydedildi · kalan " + TezgahModel.tl(p.remaining() - v),
                Toast.LENGTH_SHORT).show();
        if (onSaved != null) onSaved.run();
    }

    private void addQuick(LinearLayout row, String label, double value, boolean first) {
        TextView chip = text(label, 13, GREEN, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), 0, dp(14), 0);
        chip.setBackground(round(first ? GREEN_DIM : Color.TRANSPARENT, first ? GREEN : LINE, 20));
        chip.setOnClickListener(v -> { amount.setText(SalePayStatus.plain(value)); amount.setSelection(amount.getText().length()); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(42));
        if (row.getChildCount() > 0) lp.setMarginStart(dp(8));
        row.addView(chip, lp);
    }

    private LinearLayout stat(String title, String value, int fill, int stroke, int color) {
        LinearLayout s = new LinearLayout(a);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setPadding(dp(12), dp(10), dp(12), dp(10));
        s.setBackground(round(fill, stroke, 14));
        s.addView(text(title, 11, MUTED, false));
        TextView v = text(value, 15, color, true);
        v.setSingleLine(true);
        s.addView(v);
        s.setContentDescription(title + " " + value);
        return s;
    }

    private TextView text(String v, int sp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(v);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int fill, int stroke, int rdp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(rdp));
        if (stroke != 0) g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }

    private LinearLayout.LayoutParams margins(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        return lp;
    }

    private int dp(int v) { return Math.round(v * density); }
}
