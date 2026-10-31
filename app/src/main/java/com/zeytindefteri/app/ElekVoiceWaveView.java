package com.zeytindefteri.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

/**
 * Sesli elek dalgası: ana asistandaki yeşil çubuklarla aynı görünüm (19 yuvarlak çubuk, ortası yüksek).
 * Dinlerken mikrofon şiddetiyle, asistan konuşurken akan bir dalga gibi, düşünürken soldan sağa kayan bir
 * nabızla hareket eder; beklerken alçak ve soluk durur. Çerçevesiz: formun üstünde yalnız çubuklar görünür.
 */
public final class ElekVoiceWaveView extends View {
    public enum Mode { IDLE, LISTENING, THINKING, SPEAKING }
    private static final float[] WEIGHTS = {0.20f, 0.32f, 0.48f, 0.66f, 0.42f, 0.82f, 0.58f, 0.92f, 0.70f, 1f, 0.70f, 0.92f, 0.58f, 0.82f, 0.42f, 0.66f, 0.48f, 0.32f, 0.20f};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Mode mode = Mode.IDLE;
    private boolean onGreen;

    /** Yeşil düğmenin içinde: beyaz çubuklar, gölgesiz. */
    public ElekVoiceWaveView onGreen() { onGreen = true; return this; }
    private float target, level;
    private long lastRms;

    public ElekVoiceWaveView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        shadow.setColor(0x99000000);
    }

    public void mode(Mode m) { if (mode != m) { mode = m; if (m != Mode.LISTENING) target = 0; invalidate(); } }

    public void rms(float db) { target = ZeytinAssistantConversation.level(db); lastRms = SystemClock.uptimeMillis(); invalidate(); }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        long now = SystemClock.uptimeMillis();
        float t = now / 1000f;
        if (mode == Mode.LISTENING && now - lastRms > 160) target *= 0.8f;
        level += (target - level) * 0.35f;
        float u = getResources().getDisplayMetrics().density;
        float w = 6 * u, gap = 5 * u;
        float start = (getWidth() - (w * WEIGHTS.length + gap * (WEIGHTS.length - 1))) / 2f;
        float span = Math.max(0, getHeight() - 10 * u);
        int green = GreenTheme.color(R.color.zd_green);
        for (int i = 0; i < WEIGHTS.length; i++) {
            float k;
            switch (mode) {
                case LISTENING: k = level * WEIGHTS[i]; break;
                case SPEAKING:  k = (0.35f + 0.35f * (float) Math.sin(t * 7.5f - i * 0.55f) * (float) Math.sin(t * 2.3f + i * 0.21f)) * WEIGHTS[i] + 0.12f; break;
                case THINKING: { float d = Math.abs(((t * 1.6f) % 1f) * WEIGHTS.length - i); k = Math.max(0, 1 - d / 3f) * 0.5f; break; }
                default:        k = 0;
            }
            k = Math.max(0, Math.min(1, k));
            float h = 7 * u + k * span;
            float x = start + i * (w + gap), y = (getHeight() - h) / 2f;
            if (!onGreen) c.drawRoundRect(x, y + 1.5f * u, x + w, y + h + 1.5f * u, w / 2, w / 2, shadow);   // her zeminde seçilsin
            paint.setColor(onGreen ? 0xFFFFFFFF : mode == Mode.IDLE ? GreenTheme.color(R.color.zd_muted) : green);
            paint.setAlpha(mode == Mode.IDLE ? (onGreen ? 110 : 150) : (int) (255 * (0.55f + 0.45f * WEIGHTS[i])));
            c.drawRoundRect(x, y, x + w, y + h, w / 2, w / 2, paint);
        }
        if (mode != Mode.IDLE && isAttachedToWindow() && getWindowVisibility() == VISIBLE) postInvalidateDelayed(33);
    }
}
