package com.zeytindefteri.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;

/**
 * Açılış animasyonu (obilet'teki gibi kısa, kodla çizilen; video dosyası yok, uygulama büyümez):
 *  1) Dönen yeşil yay içinde işler sırayla belirir: ağaç → sulama → hasat → elek.
 *  2) Halka küçülüp zeytin logosuna dönüşür.
 *  3) Logo sola kayar, "Zeytin Defteri" soldan sağa yazılır, altında slogan belirir.
 * Toplam ~1,6 sn; dokununca atlanır. Telefonda animasyonlar kapalıysa hiç oynamaz.
 * Bitiş yalnız ekran çizilirken tetiklenir: arka plana alınırsa yönlendirme geri dönünce yapılır.
 */
public final class SplashIntroView extends View {
    static final long ICONS_END = 800, MORPH_END = 1050, TEXT_END = 1450, TOTAL = 1650;

    private final Runnable done;
    private boolean finished;
    private long start = -1;
    private final float d;
    private final Drawable[] icons;
    private final Drawable logo;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint slogan = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private static final String NAME = "Zeytin Defteri", SLOGAN = "Üretenin, paylaşanın, yanındayız.";

    /** Telefonda "animasyonları kapat" açıksa oynatma. */
    static boolean enabled(Context c) {
        try {
            return Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        } catch (RuntimeException e) {
            return true;
        }
    }

    public SplashIntroView(Context context, Runnable onDone) {
        super(context);
        done = onDone;
        d = context.getResources().getDisplayMetrics().density;
        setBackgroundColor(GreenTheme.color(R.color.zd_bg));
        int[] ids = {R.drawable.ic_op_tree, R.drawable.ic_op_drop, R.drawable.ic_op_basket, R.drawable.ic_op_elek};
        icons = new Drawable[ids.length];
        for (int i = 0; i < ids.length; i++) {
            icons[i] = context.getDrawable(ids[i]).mutate();
            icons[i].setTint(0xFFF4F7F4);
        }
        logo = context.getDrawable(R.drawable.ic_olive_logo).mutate();
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeCap(Paint.Cap.ROUND);
        ring.setStrokeWidth(2.5f * d);
        ring.setColor(GreenTheme.color(R.color.zd_green));
        fill.setColor(0xFF163626);
        title.setColor(GreenTheme.color(R.color.zd_text));
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setTextSize(30 * d);
        slogan.setColor(GreenTheme.color(R.color.zd_muted));
        slogan.setTextSize(13 * d);
        setContentDescription("Zeytin Defteri açılıyor");
        setClickable(true);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) finish();
        return true;
    }

    private void finish() {
        if (finished) return;
        finished = true;
        if (done != null) post(done);
    }

    private static float clamp(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
    /** Yumuşak başlayıp yumuşak biten geçiş. */
    private static float ease(float t) { t = clamp(t); return t * t * (3 - 2 * t); }
    /** Hafif taşan "pop" (0→1, ortada 1,1). */
    private static float pop(float t) { t = clamp(t); float s = 1.70158f; t -= 1; return t * t * ((s + 1) * t + s) + 1; }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        long now = SystemClock.uptimeMillis();
        if (start < 0) start = now;
        long t = now - start;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float R = 38 * d, logoR = 24 * d;

        float titleW = title.measureText(NAME);
        float gap = 12 * d;
        float groupW = logoR * 2 + gap + titleW;
        float logoFinalX = cx - groupW / 2f + logoR;

        if (t < MORPH_END) {
            // 1) Dönen yay + içinde sırayla değişen iş ikonları
            float m = ease((t - ICONS_END) / (float) (MORPH_END - ICONS_END)); // 0..1 halka küçülmesi
            float r = R + (logoR - R) * m;
            float sweep = 90f + 270f * clamp(t / (float) ICONS_END);           // yay büyüyerek kapanır
            float rot = (t / 1000f) * 360f * 1.2f - 90f;
            arc.set(cx - r, cy - r, cx + r, cy + r);
            if (m > 0f) { fill.setAlpha((int) (255 * m)); c.drawCircle(cx, cy, r, fill); }
            ring.setAlpha((int) (255 * (1f - m)));
            c.drawArc(arc, rot, sweep, false, ring);

            if (t < ICONS_END + 120) {
                long slot = ICONS_END / icons.length;
                int i = (int) Math.min(icons.length - 1, t / slot);
                float local = (t - i * slot) / (float) slot;
                float s = pop(local * 2.2f) * (1f - ease((t - ICONS_END) / 120f));
                int half = (int) (16 * d * s);
                Drawable icon = icons[i];
                icon.setAlpha((int) (255 * clamp(local * 4f)));
                icon.setBounds((int) cx - half, (int) cy - half, (int) cx + half, (int) cy + half);
                if (half > 0) icon.draw(c);
            }
            if (m > 0f) drawLogo(c, cx, cy, logoR, m);
        } else {
            // 3) Logo sola kayar, ad soldan sağa yazılır
            float k = ease((t - MORPH_END) / (float) (TEXT_END - MORPH_END));
            float x = cx + (logoFinalX - cx) * k;
            c.drawCircle(x, cy, logoR, fill);
            drawLogo(c, x, cy, logoR, 1f);

            float textX = logoFinalX + logoR + gap;
            float baseline = cy - (title.descent() + title.ascent()) / 2f;
            c.save();
            c.clipRect(textX, 0, textX + titleW * k, getHeight());
            title.setAlpha((int) (255 * clamp(k * 1.5f)));
            c.drawText(NAME, textX, baseline, title);
            c.restore();

            float sk = ease((t - TEXT_END + 150) / 250f);
            if (sk > 0f) {
                slogan.setAlpha((int) (255 * sk));
                float sw = slogan.measureText(SLOGAN);
                c.drawText(SLOGAN, cx - sw / 2f, cy + logoR + 30 * d + (1f - sk) * 8 * d, slogan);
            }
        }

        if (t >= TOTAL) { finish(); return; }
        if (!finished) postInvalidateOnAnimation();
    }

    private void drawLogo(Canvas c, float x, float y, float r, float alpha) {
        int half = (int) (r * 0.78f * pop(alpha));
        logo.setAlpha((int) (255 * clamp(alpha)));
        logo.setBounds((int) x - half, (int) y - half, (int) x + half, (int) y + half);
        if (half > 0) logo.draw(c);
    }
}
