package com.zeytindefteri.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;

import java.util.Random;

/**
 * Hava kartının arkasındaki hafif animasyon: yağmurda yağmur yağar (rüzgârla eğik), çisentide ince damlalar,
 * fırtınada sağanak + ara ara şimşek, karda kar taneleri, bulutluda süzülen bulutlar, açıkta güneş ışıltısı
 * (gece yıldızlar), siste sürüklenen pus. Yazılar okunur kalsın diye soluktur.
 * Pil: ~30 kare/sn, ekranda görünmüyorsa durur; kare başına nesne üretmez. Telefonda animasyonlar kapalıysa
 * tek bir durgun kare çizer. Dokunuşları altındaki karta bırakır.
 */
public final class WeatherSkyView extends View {
    private static final int MAX = 80;
    private final float d;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint soft = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flash = new Paint();
    private final Shader cloud = new RadialGradient(0, 0, 1, new int[]{0x2EFFFFFF, 0x14FFFFFF, 0x00FFFFFF},
            new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
    private final Shader sun = new RadialGradient(0, 0, 1, new int[]{0x55FFD36B, 0x22FFC24A, 0x00FFC24A},
            new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
    private final Shader fog = new RadialGradient(0, 0, 1, new int[]{0x22DDE6EA, 0x00DDE6EA}, null, Shader.TileMode.CLAMP);
    private final float[] px = new float[MAX], py = new float[MAX], ps = new float[MAX], pl = new float[MAX];
    private final Random random = new Random(7);
    private final Rect visible = new Rect();
    private WeatherSky.Kind kind = WeatherSky.Kind.NONE;
    private boolean day = true;
    private int count;
    private float slant;
    private long last;
    private long nextFlash, flashAt = -1;
    private final boolean animate;

    public WeatherSkyView(Context context) {
        super(context);
        d = context.getResources().getDisplayMetrics().density;
        line.setStrokeCap(Paint.Cap.ROUND);
        flash.setColor(0xFFFFFFFF);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        float scale = 1f;
        try { scale = Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f); }
        catch (RuntimeException ignored) { }
        animate = scale > 0f;
    }

    /** Güncel hava geldiğinde çağrılır. */
    public void setWeather(int code, boolean isDay, double windKmh) {
        WeatherSky.Kind k = WeatherSky.kind(code);
        boolean same = k == kind && isDay == day;
        kind = k; day = isDay;
        slant = WeatherSky.slant(windKmh);
        count = Math.min(MAX, WeatherSky.particles(k, code));
        if (!same) seed();
        invalidate();
    }

    private void seed() {
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        for (int i = 0; i < MAX; i++) {
            px[i] = random.nextFloat() * w;
            py[i] = random.nextFloat() * h;
            ps[i] = 0.6f + random.nextFloat() * 0.8f;   // hız çarpanı (derinlik)
            pl[i] = random.nextFloat();                  // kar: salınım evresi, yıldız: parıltı evresi
        }
        nextFlash = SystemClock.uptimeMillis() + 2500;
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        seed();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0 || kind == WeatherSky.Kind.NONE) return;
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0f : Math.min(0.05f, (now - last) / 1000f);
        last = now;
        if (!animate) dt = 0f;
        float t = now / 1000f;

        switch (kind) {
            case CLEAR:
                if (day) drawSun(c, w, t); else drawStars(c, w, h, t);
                break;
            case PARTLY:
                if (day) drawSun(c, w, t); else drawStars(c, w, h, t);
                drawClouds(c, w, h, t, 2);
                break;
            case CLOUDY:
                drawClouds(c, w, h, t, 3);
                break;
            case FOG:
                drawFog(c, w, h, t);
                break;
            case DRIZZLE:
                drawClouds(c, w, h, t, 2);
                drawRain(c, w, h, dt, 8f * d, 0x4D9CCFF0, 1.2f * d, 420f * d);
                break;
            case RAIN:
                drawClouds(c, w, h, t, 2);
                drawRain(c, w, h, dt, 15f * d, 0x5C9CCFF0, 1.5f * d, 700f * d);
                break;
            case STORM:
                drawClouds(c, w, h, t, 3);
                drawRain(c, w, h, dt, 18f * d, 0x669CCFF0, 1.6f * d, 900f * d);
                drawFlash(c, w, h, now);
                break;
            case SNOW:
                drawSnow(c, w, h, dt, t);
                break;
            default:
                break;
        }

        if (!animate) return;
        if (getGlobalVisibleRect(visible) && getWindowVisibility() == VISIBLE) postInvalidateDelayed(33);
        else { last = 0; postInvalidateDelayed(600); }   // ekranda değilken çizmeden bekle
    }

    private void drawRain(Canvas c, int w, int h, float dt, float len, int color, float width, float speed) {
        line.setColor(color);
        line.setStrokeWidth(width);
        float dx = slant * len;
        for (int i = 0; i < count; i++) {
            float v = speed * ps[i];
            py[i] += v * dt;
            px[i] += v * slant * dt;
            if (py[i] - len > h || px[i] - dx > w) {
                py[i] = -len - random.nextFloat() * h * 0.3f;
                px[i] = random.nextFloat() * (w + h * slant) - h * slant;
            }
            line.setAlpha((int) (Color.alpha(color) * (0.55f + 0.45f * (ps[i] - 0.6f) / 0.8f)));
            c.drawLine(px[i], py[i], px[i] - dx, py[i] - len * ps[i], line);
        }
    }

    private void drawSnow(Canvas c, int w, int h, float dt, float t) {
        dot.setColor(0xCCF4F7F4);
        for (int i = 0; i < count; i++) {
            py[i] += 38f * d * ps[i] * dt;
            float sway = (float) Math.sin(t * 1.3f + pl[i] * 6.28f) * 10f * d;
            if (py[i] > h + 4 * d) { py[i] = -4 * d; px[i] = random.nextFloat() * w; }
            dot.setAlpha((int) (110 + 90 * (ps[i] - 0.6f) / 0.8f));
            c.drawCircle(px[i] + sway, py[i], (1.2f + 1.6f * ps[i]) * d, dot);
        }
    }

    private void drawSun(Canvas c, int w, float t) {
        float r = 90 * d * (1f + 0.06f * (float) Math.sin(t * 0.9f));
        c.save();
        c.translate(w - 46 * d, 36 * d);
        c.scale(r, r);
        soft.setShader(sun);
        soft.setAlpha(255);
        c.drawCircle(0, 0, 1, soft);
        c.restore();
        soft.setShader(null);
        // yavaş dönen, çok soluk ışınlar
        line.setColor(0x1FFFD36B);
        line.setStrokeWidth(2f * d);
        float cx = w - 46 * d, cy = 36 * d;
        for (int i = 0; i < 8; i++) {
            double a = t * 0.25 + i * Math.PI / 4;
            float r0 = 34 * d, r1 = 62 * d + 6 * d * (float) Math.sin(t * 1.4 + i);
            c.drawLine(cx + (float) Math.cos(a) * r0, cy + (float) Math.sin(a) * r0,
                    cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1, line);
        }
    }

    /**
     * Gece yıldızları yalnız kartın sağ üst şeridinde: derece, "Saatlik ›" ve satırların üstüne düşüp
     * nokta işareti gibi görünmesinler.
     */
    private void drawStars(Canvas c, int w, int h, float t) {
        dot.setColor(0xFFF4F7F4);
        int n = 12;
        float top = 6 * d, band = 20 * d;
        for (int i = 0; i < n; i++) {
            float x = (0.42f + pl[i] * 0.54f) * w;
            float y = top + ((ps[i] - 0.6f) / 0.8f) * band;
            float tw = 0.5f + 0.5f * (float) Math.sin(t * (1.2f + pl[i]) + i);
            dot.setAlpha((int) (30 + 100 * tw));
            c.drawCircle(x, y, (0.6f + 0.6f * tw) * d, dot);
        }
    }

    private void drawClouds(Canvas c, int w, int h, float t, int n) {
        soft.setShader(cloud);
        for (int i = 0; i < n; i++) {
            float speed = (10f + 5f * i) * d;              // sn başına birkaç dp: yavaş süzülme
            float span = w + 260 * d;
            float x = ((t * speed + i * span / n) % span) - 130 * d;
            float y = (26 + 34 * i) * d;
            float r = (70 + 18 * i) * d;
            c.save();
            c.translate(x, y);
            c.scale(r * 1.6f, r * 0.55f);
            c.drawCircle(0, 0, 1, soft);
            c.restore();
        }
        soft.setShader(null);
    }

    private void drawFog(Canvas c, int w, int h, float t) {
        soft.setShader(fog);
        for (int i = 0; i < 4; i++) {
            float x = w / 2f + (float) Math.sin(t * 0.18f + i * 1.7f) * w * 0.35f;
            float y = h * (0.15f + 0.22f * i);
            c.save();
            c.translate(x, y);
            c.scale(w * 0.75f, 34 * d);
            c.drawCircle(0, 0, 1, soft);
            c.restore();
        }
        soft.setShader(null);
    }

    /** Şimşek: 4-8 sn arayla kısa, çift çakan soluk beyaz parlama. */
    private void drawFlash(Canvas c, int w, int h, long now) {
        if (!animate) return;
        if (flashAt < 0 && now >= nextFlash) { flashAt = now; nextFlash = now + 4000 + random.nextInt(4000); }
        if (flashAt < 0) return;
        long e = now - flashAt;
        float a = e < 80 ? 1f : e < 160 ? 0.2f : e < 240 ? 0.7f : Math.max(0f, 1f - (e - 240) / 350f) * 0.7f;
        if (e > 600) { flashAt = -1; return; }
        flash.setAlpha((int) (70 * a));
        c.drawRect(0, 0, w, h, flash);
    }

    private static final class Color {
        static int alpha(int c) { return c >>> 24; }
    }
}
