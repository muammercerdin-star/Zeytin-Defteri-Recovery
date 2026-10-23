package com.zeytindefteri.app;

import android.app.Activity;
import android.app.Dialog;
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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Calendar;
import java.util.List;

/**
 * "Ödeme aldım" alt penceresi: toplam / alınan / kalan, bugün alınan tutar, hazır tutarlar,
 * ödeme şekli, ödeme geçmişi. Kayıt SalePayments.record ile satışa ve alacağa birlikte işlenir.
 */
public final class PaymentSheet {
    private final Activity activity;
    private final DatabaseHelper helper;
    private final String owner;
    private final SalePayments.Info info;
    private final Runnable onSaved;
    private final float density;
    private int method = 0;
    private TextView[] methodChips;
    private EditText amount;
    private TextView note;

    private PaymentSheet(Activity activity, DatabaseHelper helper, String owner, SalePayments.Info info, Runnable onSaved) {
        this.activity = activity;
        this.helper = helper;
        this.owner = owner;
        this.info = info;
        this.onSaved = onSaved;
        this.density = activity.getResources().getDisplayMetrics().density;
    }

    /** Satış bulunamazsa ya da kalan yoksa pencere açılmaz (false). */
    public static boolean show(Activity activity, DatabaseHelper helper, long saleId, Runnable onSaved) {
        String owner = helper.getActiveOwnerUid();
        if (owner == null) owner = "";
        SalePayments.Info info = SalePayments.sale(helper.getReadableDatabase(), owner, saleId);
        if (info == null) return false;
        new PaymentSheet(activity, helper, owner, info, onSaved).open();
        return true;
    }

    private void open() {
        int bg = GreenTheme.color(R.color.zd_bg), card = GreenTheme.color(R.color.zd_card);
        int border = GreenTheme.color(R.color.zd_border), text = GreenTheme.color(R.color.zd_text);
        int muted = GreenTheme.color(R.color.zd_muted), green = GreenTheme.color(R.color.zd_green);
        int warn = GreenTheme.color(R.color.zd_warning);
        final Dialog sheet = new Dialog(activity);
        sheet.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(10), dp(18), dp(22));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(card);
        shape.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
        box.setBackground(shape);
        scroll.addView(box);

        View handle = new View(activity);
        handle.setBackground(round(border, border, 3));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(40), dp(5));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(handle, hp);

        LinearLayout head = new LinearLayout(activity);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(label("Ödeme aldım", 20, text, true));
        String sub = SalePayStatus.dayMonth(info.saleDate);
        if (!info.buyer.isEmpty()) sub = sub.isEmpty() ? info.buyer : sub + " · " + info.buyer;
        if (!sub.isEmpty()) titles.addView(label(sub, 13, muted, false));
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = label("✕", 16, text, true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(round(bg, border, 22));
        close.setContentDescription("Kapat");
        close.setOnClickListener(v -> sheet.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        box.addView(head, margins(12));

        LinearLayout stats = new LinearLayout(activity);
        stats.addView(stat("Toplam", ElekSaleModel.tl(info.net), bg, border, text), new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, -2, 1);
        sp.leftMargin = dp(8);
        stats.addView(stat("Alınan", ElekSaleModel.tl(info.received), GreenTheme.color(R.color.zd_green_soft), green, green), sp);
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(0, -2, 1);
        sp2.leftMargin = dp(8);
        stats.addView(stat("Kalan", ElekSaleModel.tl(info.remaining()), GreenTheme.color(R.color.zd_warning_soft), warn, warn), sp2);
        box.addView(stats, margins(14));

        List<SalePayments.Payment> history = SalePayments.history(helper.getReadableDatabase(), owner, info);
        boolean open = info.remaining() >= 0.01;
        if (open) {
            box.addView(label("Bugün alınan tutar", 13, text, true), margins(16));
            LinearLayout field = new LinearLayout(activity);
            field.setGravity(Gravity.CENTER_VERTICAL);
            field.setPadding(dp(16), 0, dp(16), 0);
            field.setBackground(round(bg, green, 14));
            amount = new EditText(activity);
            amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            amount.setText(SalePayStatus.plain(info.remaining()));
            amount.setTextSize(24);
            amount.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            amount.setTextColor(text);
            amount.setBackground(null);
            amount.setSingleLine(true);
            amount.setContentDescription("Bugün alınan tutar, TL");
            field.addView(amount, new LinearLayout.LayoutParams(0, dp(58), 1));
            field.addView(label("TL", 15, muted, true));
            box.addView(field, margins(6));

            LinearLayout quick = new LinearLayout(activity);
            double[] q = SalePayStatus.quickAmounts(info.remaining());
            addQuick(quick, "Kalanın tamamı", info.remaining(), true);
            for (double v : q) addQuick(quick, ElekSaleModel.tl(v), v, false);
            box.addView(quick, margins(8));

            box.addView(label("Nasıl ödedi?", 13, text, true), margins(16));
            LinearLayout methods = new LinearLayout(activity);
            methodChips = new TextView[SalePayStatus.METHOD_LABELS.length];
            for (int i = 0; i < methodChips.length; i++) {
                final int m = i;
                TextView chip = label(SalePayStatus.METHOD_LABELS[i], 13, text, true);
                chip.setGravity(Gravity.CENTER);
                chip.setOnClickListener(v -> { method = m; renderMethods(); });
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(44), 1);
                if (i > 0) cp.leftMargin = dp(8);
                methods.addView(chip, cp);
                methodChips[i] = chip;
            }
            box.addView(methods, margins(6));
            renderMethods();
        }

        if (!history.isEmpty()) {
            TextView h = label("ÖDEME GEÇMİŞİ", 11, muted, true);
            h.setLetterSpacing(0.08f);
            box.addView(h, margins(16));
            for (SalePayments.Payment p : history) {
                LinearLayout row = new LinearLayout(activity);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(dp(34));
                String when = SalePayStatus.shortDate(p.date);
                String what = SalePayStatus.methodLabel(p.method);
                row.addView(label(when.isEmpty() ? what : when + " · " + what, 13, text, false),
                        new LinearLayout.LayoutParams(0, -2, 1));
                row.addView(label(ElekSaleModel.tl(p.amount), 13, text, true));
                box.addView(row);
            }
        }

        if (open) {
            note = label("", 12, green, false);
            note.setPadding(dp(12), dp(10), dp(12), dp(10));
            note.setBackground(round(GreenTheme.color(R.color.zd_green_soft), GreenTheme.color(R.color.zd_green_soft), 12));
            box.addView(note, margins(14));
            amount.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void afterTextChanged(Editable s) { renderNote(); }
            });
            renderNote();

            TextView save = label("Ödemeyi kaydet", 16, bg, true);
            save.setGravity(Gravity.CENTER);
            save.setBackground(round(green, green, 26));
            save.setOnClickListener(v -> save(sheet));
            LinearLayout.LayoutParams svp = new LinearLayout.LayoutParams(-1, dp(54));
            svp.topMargin = dp(14);
            box.addView(save, svp);
        } else {
            TextView done = label("Bu satışın parası tamamen alındı.", 14, green, true);
            done.setGravity(Gravity.CENTER);
            done.setPadding(0, dp(18), 0, dp(6));
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

    private void addQuick(LinearLayout row, String text, double value, boolean first) {
        TextView chip = label(text, 12, GreenTheme.color(R.color.zd_green), true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), 0, dp(14), 0);
        chip.setBackground(round(first ? GreenTheme.color(R.color.zd_green_soft) : Color.TRANSPARENT,
                first ? GreenTheme.color(R.color.zd_green) : GreenTheme.color(R.color.zd_border), 20));
        chip.setOnClickListener(v -> {
            amount.setText(SalePayStatus.plain(value));
            amount.setSelection(amount.getText().length());
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(40));
        if (row.getChildCount() > 0) p.leftMargin = dp(8);
        row.addView(chip, p);
    }

    private void renderMethods() {
        int green = GreenTheme.color(R.color.zd_green), border = GreenTheme.color(R.color.zd_border);
        int soft = GreenTheme.color(R.color.zd_text_soft);
        for (int i = 0; i < methodChips.length; i++) {
            boolean on = i == method;
            methodChips[i].setTextColor(on ? green : soft);
            methodChips[i].setBackground(round(on ? GreenTheme.color(R.color.zd_green_soft) : Color.TRANSPARENT,
                    on ? green : border, 14));
            methodChips[i].setContentDescription(SalePayStatus.METHOD_LABELS[i] + (on ? ", seçili" : ""));
        }
    }

    private double entered() {
        // "2.000", "1.234,5", "12,5" ve "12.5" doğru okunur (Tezgah ile aynı kural).
        return Math.max(0, TezgahModel.parse(amount.getText().toString()));
    }

    private void renderNote() {
        double v = entered();
        if (v <= 0.009) {
            note.setText("Alınan tutarı yaz.");
        } else if (v >= info.remaining() - 0.009) {
            note.setText("Bu ödemeyle kayıt \"Ödendi\" olarak işaretlenecek.");
        } else {
            note.setText("Bu ödemeden sonra " + ElekSaleModel.tl(info.remaining() - v) + " kalacak.");
        }
    }

    private void save(Dialog sheet) {
        double v = entered();
        if (v <= 0.009) { amount.setError("Tutar gir"); amount.requestFocus(); return; }
        if (v > info.remaining() + 0.009) {
            amount.setError("Kalan " + ElekSaleModel.tl(info.remaining()) + "; daha fazlası yazılamaz");
            amount.requestFocus();
            return;
        }
        String today = SalePayStatus.ymd(Calendar.getInstance());
        boolean ok = SalePayments.record(helper.getWritableDatabase(), owner, info.saleId, v,
                SalePayStatus.METHODS[method], today, System.currentTimeMillis());
        if (!ok) { Toast.makeText(activity, "Ödeme kaydedilemedi. Tekrar dene.", Toast.LENGTH_LONG).show(); return; }
        boolean paid = v >= info.remaining() - 0.009;
        if (paid) PaymentReminders.close(activity, helper, owner, info.saleId);
        sync();
        sheet.dismiss();
        Toast.makeText(activity, paid ? "Ödeme kaydedildi. Satış ödendi." : "Ödeme kaydedildi.", Toast.LENGTH_SHORT).show();
        if (onSaved != null) onSaved.run();
    }

    /** Satış ve alacak toplamları buluta gider (çevrimdışıysa sonraki genel senkron dener). */
    private void sync() {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) return;
            new ProductionCloudRepository(helper).syncSaleByLocalId(user.getUid(), info.saleId);
            new SupportCloudRepository(helper).syncAllLocal(user.getUid());
        } catch (RuntimeException ignored) { }
    }

    private LinearLayout stat(String title, String value, int fill, int stroke, int valueColor) {
        LinearLayout s = new LinearLayout(activity);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setPadding(dp(12), dp(10), dp(12), dp(10));
        s.setBackground(round(fill, stroke, 14));
        s.addView(label(title, 11, GreenTheme.color(R.color.zd_muted), false));
        TextView v = label(value, 15, valueColor, true);
        v.setSingleLine(true);
        s.addView(v);
        s.setContentDescription(title + " " + value);
        return s;
    }

    private TextView label(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(activity);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
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
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(top);
        return p;
    }

    private int dp(int v) { return Math.round(v * density); }
}
