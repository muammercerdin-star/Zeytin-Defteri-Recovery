package com.zeytindefteri.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.os.SystemClock;
import android.view.View;

import com.zeytindefteri.app.ZeytinAssistantConversation.State;

/**
 * Siri tarzı kenar ışığı: asistan dinlerken, düşünürken ve konuşurken ekranın kenarlarında yumuşak, dönen
 * zeytin yeşili / turkuaz / altın bir ışık. Beklerken söner. Dinlerken sesin şiddetiyle güçlenir.
 * Dokunulmaz (altındaki görünümlere dokunuş geçer); kare başına nesne üretmez.
 */
public final class AssistantEdgeGlowView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix rotate = new Matrix();
    private SweepGradient sweep;
    private int sweepW, sweepH;
    private State state = State.IDLE;
    private boolean speaking;
    private float intensity, level, rms;
    private long lastRms;
    private final float density;

    public AssistantEdgeGlowView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.STROKE);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void state(State s, boolean isSpeaking) {
        state = s; speaking = isSpeaking;
        if (s != State.LISTENING) rms = 0;
        invalidate();
    }

    /** SpeechRecognizer rms (yaklaşık -2..10 dB). */
    public void rms(float db) { rms = Math.max(0f, Math.min(1f, (db + 2f) / 12f)); lastRms = SystemClock.uptimeMillis(); invalidate(); }

    private float target(long now) {
        switch (state) {
            case LISTENING: return 0.45f + 0.55f * (now - lastRms < 250 ? rms : 0f);
            case PROCESSING: return 0.55f;
            case ANSWERING: return speaking ? 0.6f : 0.25f;
            default: return 0f;   // beklerken söner
        }
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        long now = SystemClock.uptimeMillis();
        float t = now / 1000f;
        float want = target(now);
        intensity += (want - intensity) * 0.12f;
        level += ((state == State.LISTENING && now - lastRms < 250 ? rms : 0f) - level) * 0.25f;
        float pulse = state == State.ANSWERING && speaking ? 0.12f * (float) Math.abs(Math.sin(t * 5f))
                : state == State.PROCESSING ? 0.08f * (float) Math.sin(t * 4f) : 0f;
        float a = Math.max(0f, Math.min(1f, intensity + pulse));

        if (a > 0.01f) {
            if (sweep == null || sweepW != w || sweepH != h) {
                sweep = new SweepGradient(w / 2f, h / 2f,
                        new int[]{0xff7fcf72, 0xff38aaa0, 0xffd6c25a, 0xff56c26a, 0xff2f8f55, 0xff7fcf72}, null);
                sweepW = w; sweepH = h;
            }
            float speed = state == State.PROCESSING ? 140f : state == State.LISTENING ? 60f + 120f * level : 45f;
            rotate.setRotate((t * speed) % 360f, w / 2f, h / 2f);
            sweep.setLocalMatrix(rotate);
            paint.setShader(sweep);
            float corner = 28 * density;
            // Dıştan içe: geniş soluk, orta, ince parlak çizgi (yumuşak ışıma etkisi)
            float[] widths = {22, 12, 5, 2};
            int[] alphas = {38, 70, 140, 230};
            for (int i = 0; i < widths.length; i++) {
                float sw = widths[i] * density * (1f + 0.35f * level);
                paint.setStrokeWidth(sw);
                paint.setAlpha((int) (alphas[i] * a));
                rect.set(sw / 2f, sw / 2f, w - sw / 2f, h - sw / 2f);
                c.drawRoundRect(rect, corner, corner, paint);
            }
            paint.setShader(null);
        }
        boolean animate = a > 0.01f || want > 0f;
        if (animate && isAttachedToWindow() && getWindowVisibility() == VISIBLE) postInvalidateDelayed(32);
    }
}
