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
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Calendar;
import java.util.List;

/**
 * Alıcıdan toplu "Ödeme aldım": tutar alıcının toplam borcuyla dolu gelir; para en eski satıştan başlayarak
 * dağıtılır (BuyerPayPlan) ve her satışa ayrı ödeme olarak işlenir (SalePayments.recordPlan, tek işlem).
 * Görünüm tek satış penceresiyle (PaymentSheet) aynıdır.
 */
public final class BuyerPaymentSheet {

    private final Activity activity;
    private final DatabaseHelper helper;
    private final String owner;
    private final String buyer;
    private final List<BuyerPayPlan.Open> open;
    private final Runnable onSaved;
    private final float density;
    private int method;
    private TextView[] methodChips;
    private EditText amount;
    private TextView note;

    private BuyerPaymentSheet(Activity activity, DatabaseHelper helper, String owner, String buyer,
                              List<BuyerPayPlan.Open> open, Runnable onSaved) {
        this.activity = activity;
        this.helper = helper;
        this.owner = owner;
        this.buyer = buyer == null ? "" : buyer;
        this.open = open;
        this.onSaved = onSaved;
        this.density = activity.getResources().getDisplayMetrics().density;
    }

    /** Açık borç yoksa açılmaz (false). */
    public static boolean show(Activity activity, DatabaseHelper helper, long buyerId, String buyerName, Runnable onSaved) {
        String owner = helper.getActiveOwnerUid();
        if (owner == null) owner = "";
        List<BuyerPayPlan.Open> open;
        try {
            open = BuyerDirectory.openSales(helper.getReadableDatabase(), owner, buyerId);
        } catch (RuntimeException e) {
            return false;
        }
        if (BuyerPayPlan.total(open) <= 0.009) return false;
        new BuyerPaymentSheet(activity, helper, owner, buyerName, open, onSaved).open();
        return true;
    }

    private void open() {
        int bg = GreenTheme.color(R.color.zd_bg);
        int card = GreenTheme.color(R.color.zd_card);
        int border = GreenTheme.color(R.color.zd_border);
        int text = GreenTheme.color(R.color.zd_text);
        int muted = GreenTheme.color(R.color.zd_muted);
        int green = GreenTheme.color(R.color.zd_green);
        int warn = GreenTheme.color(R.color.zd_warning);
        double total = BuyerPayPlan.total(open);
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
        String sub = buyer.isEmpty() ? "Alıcının bütün borcu" : buyer + " · bütün borç";
        titles.addView(label(sub, 13, muted, false));
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = label("✕", 16, text, true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(round(bg, border, 22));
        close.setContentDescription("Kapat");
        close.setOnClickListener(v -> sheet.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        box.addView(head, margins(12));

        LinearLayout stats = new LinearLayout(activity);
        stats.addView(stat("Açık satış", String.valueOf(open.size()), bg, border, text), new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, -2, 1.4f);
        sp.leftMargin = dp(8);
        stats.addView(stat("Toplam borç", ElekSaleModel.tl(total), GreenTheme.color(R.color.zd_warning_soft), warn, warn), sp);
        box.addView(stats, margins(14));

        box.addView(label("Bugün alınan tutar", 13, text, true), margins(16));
        LinearLayout field = new LinearLayout(activity);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(dp(16), 0, dp(16), 0);
        field.setBackground(round(bg, green, 14));
        amount = new EditText(activity);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setText(SalePayStatus.plain(total));
        amount.setTextSize(24);
        amount.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        amount.setTextColor(text);
        amount.setBackground(null);
        amount.setSingleLine(true);
        amount.setContentDescription("Bugün alınan tutar, TL");
        field.addView(amount, new LinearLayout.LayoutParams(0, dp(58), 1));
        field.addView(label("TL", 15, muted, true));
        box.addView(field, margins(6));

        HorizontalScrollView quickScroll = new HorizontalScrollView(activity);
        quickScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout quick = new LinearLayout(activity);
        quickScroll.addView(quick);
        addQuick(quick, "Tamamı", total, true);
        double[] q = SalePayStatus.quickAmounts(total);
        for (double v : q) {
            if (v > 0.009 && v < total - 0.009) addQuick(quick, ElekSaleModel.tl(v), v, false);
        }
        box.addView(quickScroll, margins(10));

        box.addView(label("Ödeme şekli", 13, text, true), margins(16));
        LinearLayout methods = new LinearLayout(activity);
        methodChips = new TextView[SalePayStatus.METHOD_LABELS.length];
        for (int i = 0; i < methodChips.length; i++) {
            final int index = i;
            TextView chip = label(SalePayStatus.METHOD_LABELS[i], 13, text, true);
            chip.setGravity(Gravity.CENTER);
            chip.setOnClickListener(v -> {
                method = index;
                renderMethods();
            });
            methodChips[i] = chip;
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, dp(46), 1);
            if (i > 0) mp.leftMargin = dp(8);
            methods.addView(chip, mp);
        }
        renderMethods();
        box.addView(methods, margins(6));

        note = label("", 13, muted, false);
        note.setLineSpacing(0, 1.15f);
        box.addView(note, margins(12));
        amount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { renderNote(); }
        });
        renderNote();

        TextView save = label("Kaydet", 16, Color.WHITE, true);
        save.setGravity(Gravity.CENTER);
        save.setBackground(round(green, green, 16));
        save.setOnClickListener(v -> save(sheet));
        LinearLayout.LayoutParams svp = new LinearLayout.LayoutParams(-1, dp(56));
        svp.topMargin = dp(16);
        box.addView(save, svp);

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
        int green = GreenTheme.color(R.color.zd_green);
        TextView chip = label(text, 12, green, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), 0, dp(14), 0);
        int fill = first ? GreenTheme.color(R.color.zd_green_soft) : Color.TRANSPARENT;
        int stroke = first ? green : GreenTheme.color(R.color.zd_border);
        chip.setBackground(round(fill, stroke, 20));
        chip.setOnClickListener(v -> {
            amount.setText(SalePayStatus.plain(value));
            amount.setSelection(amount.getText().length());
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(40));
        if (row.getChildCount() > 0) p.leftMargin = dp(8);
        row.addView(chip, p);
    }

    private void renderMethods() {
        int green = GreenTheme.color(R.color.zd_green);
        int border = GreenTheme.color(R.color.zd_border);
        int soft = GreenTheme.color(R.color.zd_text_soft);
        for (int i = 0; i < methodChips.length; i++) {
            boolean on = i == method;
            methodChips[i].setTextColor(on ? green : soft);
            int fill = on ? GreenTheme.color(R.color.zd_green_soft) : Color.TRANSPARENT;
            methodChips[i].setBackground(round(fill, on ? green : border, 14));
            methodChips[i].setContentDescription(SalePayStatus.METHOD_LABELS[i] + (on ? ", seçili" : ""));
        }
    }

    private double entered() {
        // "2.000", "1.234,5", "12,5" ve "12.5" doğru okunur (Tezgah ile aynı kural).
        return Math.max(0, TezgahModel.parse(amount.getText().toString()));
    }

    private void renderNote() {
        BuyerPayPlan.Result r = BuyerPayPlan.plan(open, entered());
        note.setText(BuyerPayPlan.note(r, open.size()));
    }

    private void save(Dialog sheet) {
        BuyerPayPlan.Result plan = BuyerPayPlan.plan(open, entered());
        if (plan.empty()) {
            amount.setError("Tutar gir");
            amount.requestFocus();
            return;
        }
        if (plan.excess > 0.009) {
            amount.setError("Toplam borç " + ElekSaleModel.tl(BuyerPayPlan.total(open)) + "; daha fazlası yazılamaz");
            amount.requestFocus();
            return;
        }
        String today = SalePayStatus.ymd(Calendar.getInstance());
        boolean ok = SalePayments.recordPlan(helper.getWritableDatabase(), owner, plan,
                SalePayStatus.METHODS[method], today, System.currentTimeMillis());
        if (!ok) {
            Toast.makeText(activity, "Ödeme kaydedilemedi. Tekrar dene.", Toast.LENGTH_LONG).show();
            return;
        }
        for (BuyerPayPlan.Part p : plan.parts) {
            if (p.closes) PaymentReminders.close(activity, helper, owner, p.saleId);
        }
        sync(plan);
        sheet.dismiss();
        String msg = plan.leftAfter <= 0.009
                ? "Ödeme kaydedildi. " + buyerLabel() + "borcu kapandı."
                : "Ödeme kaydedildi. Kalan borç " + ElekSaleModel.tl(plan.leftAfter) + ".";
        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
        if (onSaved != null) onSaved.run();
    }

    private String buyerLabel() {
        return buyer.isEmpty() ? "" : buyer + " ";
    }

    /** Satış ve alacak toplamları buluta gider (çevrimdışıysa sonraki genel senkron dener). */
    private void sync(BuyerPayPlan.Result plan) {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) return;
            ProductionCloudRepository sales = new ProductionCloudRepository(helper);
            for (BuyerPayPlan.Part p : plan.parts) sales.syncSaleByLocalId(user.getUid(), p.saleId);
            new SupportCloudRepository(helper).syncAllLocal(user.getUid());
        } catch (RuntimeException ignored) {
            // Sonraki genel senkronda gider.
        }
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

    private int dp(int v) {
        return Math.round(v * density);
    }
}
