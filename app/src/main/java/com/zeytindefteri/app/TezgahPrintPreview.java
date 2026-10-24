package com.zeytindefteri.app;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Baskı önizlemesi: fiş kâğıtta nasıl çıkacaksa öyle gösterilir (beyaz kâğıt, sabit genişlikli yazı,
 * Türkçe karakter kapalıysa sadeleşmiş hâli, altta QR). Kâğıt 58/80 mm, Türkçe karakter ve QR buradan
 * değiştirilir, önizleme hemen yenilenir. "Yazdır" ile yazıcıya gider.
 */
final class TezgahPrintPreview {
    private static final int SHEET = Color.parseColor("#0C1C27");
    private static final int CARD = Color.parseColor("#0F2230");
    private static final int LINE = Color.parseColor("#2A4452");
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.parseColor("#A9BAB6");
    private static final int GREEN = Color.parseColor("#8BE06A");
    private static final int GREEN_DIM = Color.parseColor("#1E3D2C");
    private static final int BG = Color.parseColor("#050F16");
    private static final int PAPER = Color.parseColor("#FBFAF6");
    private static final int INK = Color.parseColor("#1A1A1A");

    private final Activity a;
    private final TezgahPrinter.Layout layout;
    private final String qrPayload;
    private final Runnable onPrint;
    private final float density;
    private Dialog dialog;
    private LinearLayout box;

    private TezgahPrintPreview(Activity a, TezgahPrinter.Layout layout, String qrPayload, Runnable onPrint) {
        this.a = a; this.layout = layout; this.qrPayload = qrPayload; this.onPrint = onPrint;
        this.density = a.getResources().getDisplayMetrics().density;
    }

    static void show(Activity a, TezgahPrinter.Layout layout, String qrPayload, Runnable onPrint) {
        new TezgahPrintPreview(a, layout, qrPayload, onPrint).open();
    }

    private void open() {
        dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(a);
        box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(10), dp(18), dp(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SHEET);
        float r = dp(26);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        bg.setStroke(dp(1), LINE);
        scroll.setBackground(bg);
        scroll.addView(box);
        dialog.setContentView(scroll);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            w.setGravity(Gravity.BOTTOM);
        }
        render();
        dialog.show();
    }

    private void render() {
        box.removeAllViews();
        int width = TezgahPrinter.width(a);
        boolean w80 = width >= TezgahPrintFormat.WIDTH_80;
        boolean tr = TezgahPrinter.turkish(a);
        boolean qr = qrPayload != null && TezgahPrinter.printQr(a);

        // Başlık
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("Baskı önizleme", 20, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = text("✕", 17, TEXT, true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(round(BG, LINE, 22));
        close.setContentDescription("Kapat, yazdırma");
        close.setOnClickListener(v -> dialog.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        box.addView(head);

        // Kâğıt: 58 mm ≈ 260dp, 80 mm ≈ 340dp (ekrana sığacak kadar)
        int screen = a.getResources().getDisplayMetrics().widthPixels - dp(36);
        int paperW = Math.min(screen, dp(w80 ? 340 : 260));
        LinearLayout paper = new LinearLayout(a);
        paper.setOrientation(LinearLayout.VERTICAL);
        paper.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(10);
        paper.setPadding(pad, dp(14), pad, dp(22));
        paper.setBackground(round(PAPER, Color.parseColor("#D9D6CC"), 4));
        String body = layout.text(width);
        if (!tr) body = TezgahPrintFormat.fold(body);
        if (body.endsWith("\n")) body = body.substring(0, body.length() - 1);
        TextView receipt = new TextView(a);
        receipt.setTypeface(Typeface.MONOSPACE);
        receipt.setTextColor(INK);
        // Satır tam kâğıt genişliğine sığsın: sabit genişlikli harf ≈ 0,6 em.
        float px = (paperW - 2f * pad) / (width * 0.61f);
        receipt.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        receipt.setLineSpacing(0, 1.08f);
        SpannableString sp = new SpannableString(body);
        int firstEnd = body.indexOf('\n');
        if (firstEnd > 0) {
            sp.setSpan(new RelativeSizeSpan(2f), 0, firstEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); // yazıcıda çift en/boy
            sp.setSpan(new StyleSpan(Typeface.BOLD), 0, firstEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        receipt.setText(sp);
        receipt.setContentDescription("Fiş önizlemesi: " + body.replace('\n', ' '));
        paper.addView(receipt, new LinearLayout.LayoutParams(-1, -2));
        if (qr) {
            String caption = "Uretici okutsun: Elek > QR okut";
            TextView cap = new TextView(a);
            cap.setTypeface(Typeface.MONOSPACE);
            cap.setTextColor(INK);
            cap.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
            cap.setGravity(Gravity.CENTER);
            cap.setText(caption);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.topMargin = dp(10);
            paper.addView(cap, cp);
            int qrSize = Math.round((paperW - 2f * pad) * 2f / 3f); // yazıcıda da kâğıdın ~2/3'ü
            Bitmap bmp = TezgahQrUi.bitmap(qrPayload, qrSize);
            if (bmp != null) {
                ImageView img = new ImageView(a);
                img.setImageBitmap(bmp);
                img.setContentDescription("Fişin altına basılacak QR kodu");
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(qrSize, qrSize);
                ip.topMargin = dp(4);
                paper.addView(img, ip);
            }
        }
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(paperW, -2);
        pp.gravity = Gravity.CENTER_HORIZONTAL;
        pp.topMargin = dp(12);
        box.addView(paper, pp);
        if (!tr) {
            TextView note = text("Türkçe karakter kapalı: ş, ğ, ı → s, g, i basılır.", 12, MUTED, false);
            note.setGravity(Gravity.CENTER);
            box.addView(note, margins(8));
        }

        // Ayarlar (dokununca önizleme yenilenir)
        LinearLayout opts = new LinearLayout(a);
        opts.addView(chip(w80 ? "80 mm" : "58 mm", true, () -> {
            TezgahPrinter.setWidth(a, w80 ? TezgahPrintFormat.WIDTH_58 : TezgahPrintFormat.WIDTH_80); render(); }),
                new LinearLayout.LayoutParams(0, dp(44), 1));
        opts.addView(chip(tr ? "Türkçe ✓" : "Türkçe ✗", tr, () -> { TezgahPrinter.setTurkish(a, !tr); render(); }), weighted());
        if (qrPayload != null) {
            boolean on = TezgahPrinter.printQr(a);
            opts.addView(chip(on ? "QR ✓" : "QR ✗", on, () -> { TezgahPrinter.setPrintQr(a, !on); render(); }), weighted());
        }
        box.addView(opts, margins(14));
        String name = TezgahPrinter.printerName(a);
        TextView printer = text("Yazıcı: " + (name.isEmpty() ? "seçilmedi (Yazdır'da sorulur)" : name) + "  ›", 14, GREEN, true);
        printer.setPadding(0, dp(12), 0, dp(4));
        printer.setOnClickListener(v -> TezgahPrinter.choose(a, this::render));
        box.addView(printer);

        TextView go = text("Yazdır", 19, BG, true);
        go.setGravity(Gravity.CENTER);
        go.setBackground(round(GREEN, 0, 18));
        go.setContentDescription("Fişi yazıcıya gönder");
        go.setOnClickListener(v -> { dialog.dismiss(); if (onPrint != null) onPrint.run(); });
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, dp(56));
        gp.topMargin = dp(12);
        box.addView(go, gp);
        TextView cancel = text("Vazgeç", 15, MUTED, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setOnClickListener(v -> dialog.dismiss());
        box.addView(cancel, new LinearLayout.LayoutParams(-1, dp(46)));
    }

    private TextView chip(String label, boolean on, Runnable action) {
        TextView c = text(label, 14, on ? GREEN : TEXT, true);
        c.setGravity(Gravity.CENTER);
        c.setBackground(round(on ? GREEN_DIM : CARD, on ? GREEN : LINE, 14));
        c.setOnClickListener(v -> action.run());
        return c;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1);
        lp.setMarginStart(dp(8));
        return lp;
    }

    private TextView text(String v, int sp, int color, boolean bold) {
        TextView t = new TextView(a);
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

    private LinearLayout.LayoutParams margins(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        return lp;
    }

    private int dp(int v) { return Math.round(v * density); }
}
