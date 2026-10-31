package com.zeytindefteri.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * Sesli elek küresi: yeşil, içinde yavaşça dolaşan açık bulutlar (ChatGPT'nin sesli küresi gibi).
 * Dinlerken sesin şiddetiyle büyür, konuşurken nefes alır gibi atar, beklerken çok yavaş döner.
 * Kare başına nesne üretmez (gölgelendiriciler birim yarıçapta bir kez kurulur).
 */
public final class ElekVoiceOrbView extends View {
    public enum Mode { IDLE, LISTENING, THINKING, SPEAKING }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final Shader base = new RadialGradient(-0.25f, -0.35f, 1.45f,
            new int[]{0xffd9f7c4, 0xff7fcf72, 0xff2f8f55, 0xff0f4a3c}, new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP);
    private final Shader cloud = new RadialGradient(0, 0, 1, new int[]{0xb8f2ffe6, 0x55e6ffd8, 0x00e6ffd8},
            new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
    private final Shader deep = new RadialGradient(0, 0, 1, new int[]{0x7016503a, 0x0016503a}, null, Shader.TileMode.CLAMP);
    private final Shader halo = new RadialGradient(0, 0, 1, new int[]{0x6656c26a, 0x2256c26a, 0x0056c26a},
            new float[]{0.55f, 0.75f, 1f}, Shader.TileMode.CLAMP);
    private Mode mode = Mode.IDLE;
    private float rms, level;
    private long lastRms;
    private boolean small;

    public ElekVoiceOrbView(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }

    /** Düğme içindeki küçük süs küresi: hale çizmez. */
    public ElekVoiceOrbView compact() { small = true; return this; }

    public void mode(Mode m) { if (mode != m) { mode = m; if (m != Mode.LISTENING) rms = 0; invalidate(); } }

    /** SpeechRecognizer rms (yaklaşık -2..10 dB). */
    public void rms(float db) { rms = Math.max(0f, Math.min(1f, (db + 2f) / 12f)); lastRms = SystemClock.uptimeMillis(); invalidate(); }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        long now = SystemClock.uptimeMillis();
        float t = now / 1000f;
        float want = mode == Mode.LISTENING && now - lastRms < 250 ? rms : 0f;
        level += (want - level) * 0.2f;
        float pulse;
        switch (mode) {
            case LISTENING: pulse = 0.015f * (float) Math.sin(t * 2.2f) + 0.10f * level; break;
            case SPEAKING:  pulse = 0.035f * (float) Math.abs(Math.sin(t * 5.5f)) + 0.012f * (float) Math.sin(t * 9f); break;
            case THINKING:  pulse = 0.02f * (float) Math.sin(t * 6f); break;
            default:        pulse = 0.012f * (float) Math.sin(t * 1.4f);
        }
        float speed = mode == Mode.IDLE ? 0.35f : mode == Mode.SPEAKING ? 1.1f : mode == Mode.THINKING ? 1.6f : 0.8f + level;
        float size = Math.min(getWidth(), getHeight()) / 2f;
        float r = size * (small ? 0.92f : 0.78f) * (1f + pulse);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;

        if (!small && mode != Mode.IDLE) {          // dış ışıma
            c.save(); c.translate(cx, cy); float hr = r * (1.22f + 0.25f * level + (mode == Mode.SPEAKING ? 0.05f : 0)); c.scale(hr, hr);
            paint.setShader(halo); c.drawCircle(0, 0, 1, paint); c.restore();
        }
        c.save();
        clip.reset(); clip.addCircle(cx, cy, r, Path.Direction.CW); c.clipPath(clip);
        c.translate(cx, cy); c.scale(r, r);
        paint.setShader(base); c.drawCircle(0, 0, 1, paint);
        // koyu girdap
        c.save(); c.translate((float) Math.cos(t * speed * 0.7f) * 0.45f, (float) Math.sin(t * speed * 0.9f) * 0.4f + 0.35f); c.scale(0.9f, 0.7f);
        paint.setShader(deep); c.drawCircle(0, 0, 1, paint); c.restore();
        // açık bulutlar
        for (int i = 0; i < 3; i++) {
            float a = t * speed * (0.6f + 0.25f * i) + i * 2.1f;
            float x = (float) Math.cos(a) * (0.38f + 0.06f * i), y = (float) Math.sin(a * 1.3f) * 0.32f - 0.12f;
            float s = 0.62f + 0.12f * (float) Math.sin(a * 0.7f + i) + 0.25f * level;
            c.save(); c.translate(x, y); c.scale(s * 1.25f, s * 0.8f); c.rotate(i * 37 + t * 8);
            paint.setShader(cloud); c.drawCircle(0, 0, 1, paint); c.restore();
        }
        c.restore();
        paint.setShader(null);
        if (isAttachedToWindow() && getWindowVisibility() == VISIBLE) postInvalidateDelayed(small ? 50 : 30);
    }
}
