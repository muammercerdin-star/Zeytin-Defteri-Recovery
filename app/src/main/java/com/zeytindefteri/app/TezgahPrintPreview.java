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
 * Açılışta fiş, üstteki yazıcı ağzından akıcı biçimde çıkar, sonda yavaşlar (ReceiptPrint); yalnız ilk açılışta,
 * ayar değişince yeniden basılmaz. Telefonda animasyonlar kapalıysa doğrudan görünür; fişe dokununca baskı biter.
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
    /** Fiş bir kez basıldı mı (58/80 mm, Türkçe, QR değişince yeniden basılmaz). */
    private boolean printed;
    /** Ekrana sığdırma: fiş bu oranda küçültülür ki pencere kaydırılmadan tamamı görünsün. */
    private float fit = 1f;
    private int fitPass;
    private static final float MIN_FIT = 0.55f;
    private ScrollView scroll;
    private android.animation.ValueAnimator printAnimator;

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
        scroll = new ScrollView(a);
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
        printed = !animationsOn();
        dialog.setOnDismissListener(d -> { if (printAnimator != null) printAnimator.cancel(); });
        render();
        dialog.show();
    }

    private boolean animationsOn() {
        try {
            return android.provider.Settings.Global.getFloat(a.getContentResolver(),
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        } catch (Exception e) {
            return true;
        }
    }

    private void render() {
        // Baskı sürerken ayar değişirse baskı tamamlanır, yeni önizleme hemen çizilir.
        if (printAnimator != null && printAnimator.isRunning()) printAnimator.end();
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
        int paperW = Math.round(Math.min(screen, dp(w80 ? 340 : 260)) * fit);
        LinearLayout paper = new LinearLayout(a);
        paper.setOrientation(LinearLayout.VERTICAL);
        paper.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(10);
        paper.setPadding(pad, dp(14), pad, dp(14));
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
        // Yazıcı ağzı + fiş (yırtık alt kenarlı). Fiş, ağzın hemen altındaki kesik alandan çıkar.
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(paperW + dp(20), dp(20));
        slp.gravity = Gravity.CENTER_HORIZONTAL;
        slp.topMargin = dp(12);
        box.addView(printerSlot(), slp);
        LinearLayout ticket = new LinearLayout(a);
        ticket.setOrientation(LinearLayout.VERTICAL);
        float pr = dp(4);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(PAPER);
        pbg.setCornerRadii(new float[]{0, 0, 0, 0, pr, pr, pr, pr});
        paper.setBackground(pbg);
        ticket.addView(paper, new LinearLayout.LayoutParams(-1, -2));
        ticket.addView(tornEdge(), new LinearLayout.LayoutParams(-1, dp(7)));
        android.widget.FrameLayout clip = new android.widget.FrameLayout(a);
        clip.setClipChildren(true);
        clip.addView(ticket, new android.widget.FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(paperW, -2);
        pp.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(clip, pp);
        final int tailFrom = box.getChildCount();
        if (!tr) {
            TextView note = text("Türkçe karakter kapalı: ş, ğ, ı → s, g, i basılır.", 12, MUTED, false);
            note.setGravity(Gravity.CENTER);
            box.addView(note, margins(8));
        }

        // Ayarlar (dokununca önizleme yenilenir)
        LinearLayout opts = new LinearLayout(a);
        opts.addView(chip(w80 ? "80 mm" : "58 mm", true, () -> {
            TezgahPrinter.setWidth(a, w80 ? TezgahPrintFormat.WIDTH_58 : TezgahPrintFormat.WIDTH_80); refit(); }),
                new LinearLayout.LayoutParams(0, dp(44), 1));
        opts.addView(chip(tr ? "Türkçe ✓" : "Türkçe ✗", tr, () -> { TezgahPrinter.setTurkish(a, !tr); refit(); }), weighted());
        if (qrPayload != null) {
            boolean on = TezgahPrinter.printQr(a);
            opts.addView(chip(on ? "QR ✓" : "QR ✗", on, () -> { TezgahPrinter.setPrintQr(a, !on); refit(); }), weighted());
        }
        box.addView(opts, margins(14));
        String name = TezgahPrinter.printerName(a);
        TextView printer = text("Yazıcı: " + (name.isEmpty() ? "seçilmedi (Yazdır'da sorulur)" : name) + "  ›", 14, GREEN, true);
        printer.setPadding(0, dp(12), 0, dp(4));
        printer.setOnClickListener(v -> TezgahPrinter.choose(a, this::render));
        box.addView(printer);

        // Vazgeç ve Yazdır yan yana: ekranda yer kazanılır, fiş daha büyük sığar.
        LinearLayout buttons = new LinearLayout(a);
        TextView cancel = text("Vazgeç", 15, MUTED, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(round(CARD, LINE, 18));
        cancel.setOnClickListener(v -> dialog.dismiss());
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, dp(56), 1));
        TextView go = text("Yazdır", 19, BG, true);
        go.setGravity(Gravity.CENTER);
        go.setBackground(round(GREEN, 0, 18));
        go.setContentDescription("Fişi yazıcıya gönder");
        go.setOnClickListener(v -> { dialog.dismiss(); if (onPrint != null) onPrint.run(); });
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(0, dp(56), 2);
        gp.setMarginStart(dp(10));
        buttons.addView(go, gp);
        box.addView(buttons, margins(10));

        // İlk baskıdan önce fiş ve alttakiler gizli: sığdırma ölçüsü alınırken tam fiş bir an görünmesin.
        final android.view.View[] tail = new android.view.View[Math.max(0, box.getChildCount() - tailFrom)];
        for (int i = 0; i < tail.length; i++) tail[i] = box.getChildAt(tailFrom + i);
        if (!printed) {
            ticket.setVisibility(android.view.View.INVISIBLE);
            for (android.view.View v : tail) v.setAlpha(0f);
        }
        box.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                box.getViewTreeObserver().removeOnPreDrawListener(this);
                if (!box.isAttachedToWindow() || clip.getParent() != box) return true;   // eski çizimin dinleyicisi
                if (scroll.getHeight() <= 0) {           // pencere boyu henüz belli değil: bir sonraki karede ölç
                    box.getViewTreeObserver().addOnPreDrawListener(this);
                    return true;
                }
                float next = fitFor(scroll.getHeight(), box.getHeight(), clip.getHeight());
                if (fitPass < 2 && next < fit - 0.005f) {
                    fitPass++;
                    fit = next;
                    render();                 // küçültülmüş fişle yeniden çiz
                    return false;
                }
                if (!printed) {
                    printed = true;
                    startPrint(clip, ticket, tail);
                }
                return true;
            }
        });
    }

    /** Ayar (58/80 mm, Türkçe, QR) değişince fiş boyu değişir: sığdırma baştan hesaplanır. */
    private void refit() {
        fit = 1f;
        fitPass = 0;
        render();
    }

    /**
     * Pencereye sığdırma oranı: içerik pencereden uzunsa, fişin dışındaki her şey sabit kalır,
     * fiş kalan yüksekliğe sığacak kadar küçülür (en az MIN_FIT). Sığıyorsa oran değişmez.
     */
    private float fitFor(int viewport, int content, int paperH) {
        if (viewport <= 0 || paperH <= 0 || content <= viewport) return fit;
        float room = viewport - (content - paperH) - dp(4);
        return Math.max(MIN_FIT, fit * Math.min(1f, room / paperH));
    }

    // ===================== Yazıcıdan çıkan fiş =====================

    /** Koyu yazıcı gövdesi, ortasında fişin çıktığı ince yarık ve küçük yeşil "hazır" ışığı. */
    private android.view.View printerSlot() {
        android.widget.FrameLayout slot = new android.widget.FrameLayout(a);
        slot.setBackground(round(Color.parseColor("#14262F"), Color.parseColor("#2C4450"), 10));
        android.view.View slit = new android.view.View(a);
        slit.setBackground(round(Color.BLACK, 0, 2));
        android.widget.FrameLayout.LayoutParams sl = new android.widget.FrameLayout.LayoutParams(-1, dp(4), Gravity.BOTTOM);
        sl.setMargins(dp(8), 0, dp(8), dp(3));
        slot.addView(slit, sl);
        android.view.View led = new android.view.View(a);
        led.setBackground(round(GREEN, 0, 3));
        android.widget.FrameLayout.LayoutParams ll = new android.widget.FrameLayout.LayoutParams(dp(6), dp(6), Gravity.TOP | Gravity.END);
        ll.setMargins(0, dp(4), dp(12), 0);
        slot.addView(led, ll);
        slot.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return slot;
    }

    /** Fişin altındaki yırtık (zikzak) kenar: kâğıt yazıcıdan koparılmış gibi. */
    private android.view.View tornEdge() {
        android.view.View edge = new android.view.View(a) {
            private final android.graphics.Paint fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            private final android.graphics.Path path = new android.graphics.Path();
            @Override protected void onDraw(android.graphics.Canvas c) {
                fill.setColor(PAPER);
                float w = getWidth(), h = getHeight();
                int n = Math.max(1, Math.round(w / dp(10)));
                float step = w / n;
                path.reset();
                path.moveTo(0, 0);
                for (int i = 0; i < n; i++) {
                    path.lineTo(i * step + step / 2f, h);
                    path.lineTo((i + 1) * step, 0);
                }
                path.close();
                c.drawPath(path, fill);
            }
        };
        edge.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return edge;
    }

    /** Fiş ağızdan akıcı biçimde iner, ara ara hafif titreşim; bitince alttaki ayarlar ve Yazdır belirir. */
    private void startPrint(android.view.View clip, LinearLayout ticket, android.view.View[] tail) {
        final float h = ticket.getHeight();
        final int steps = ReceiptPrint.steps(h / density);
        ticket.setTranslationY(-h);
        ticket.setVisibility(android.view.View.VISIBLE);
        // Fiş bir kez çizilip kaydırılır (her karede yeniden çizilmez): takılmasız akış.
        ticket.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
        printAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        printAnimator.setDuration(ReceiptPrint.durationMs(h / density));
        printAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
        final int[] last = {0};
        printAnimator.addUpdateListener(an -> {
            float t = (float) an.getAnimatedValue();
            ticket.setTranslationY(-h * (1f - ReceiptPrint.progress(t, steps)));
            int step = ReceiptPrint.stepIndex(t, steps);
            if (step != last[0]) {
                last[0] = step;
                if (step % 2 == 0) clip.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            }
        });
        printAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator an) {
                ticket.setTranslationY(0f);
                ticket.setLayerType(android.view.View.LAYER_TYPE_NONE, null);
                clip.setOnClickListener(null);
                clip.setClickable(false);
                for (android.view.View v : tail) v.animate().alpha(1f).setDuration(220).start();
            }
        });
        // Beklemek istemeyen fişe dokunur, baskı hemen biter.
        clip.setOnClickListener(v -> { if (printAnimator != null) printAnimator.end(); });
        printAnimator.start();
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
