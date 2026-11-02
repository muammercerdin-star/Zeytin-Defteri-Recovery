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
 * Siri tarzı kenar ışığı (sade): kenarda ince, renkleri dönen tek çizgi + içeri doğru sönen çok hafif ışıltı;
 * asistan dinlerken, düşünürken ve konuşurken ekranın kenarlarında yumuşak, dönen
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
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private android.graphics.LinearGradient hazeTop, hazeBottom, hazeLeft, hazeRight;
    private float hazeSize; private int hazeW, hazeH;

    private void buildHaze(int w, int h, float haze, int edge) {
        android.graphics.Shader.TileMode cl = android.graphics.Shader.TileMode.CLAMP;
        hazeTop = new android.graphics.LinearGradient(0, 0, 0, haze, edge, 0x0056c26a, cl);
        hazeBottom = new android.graphics.LinearGradient(0, h, 0, h - haze, edge, 0x0056c26a, cl);
        hazeLeft = new android.graphics.LinearGradient(0, 0, haze, 0, edge, 0x0056c26a, cl);
        hazeRight = new android.graphics.LinearGradient(w, 0, w - haze, 0, edge, 0x0056c26a, cl);
        hazeSize = haze; hazeW = w; hazeH = h;
    }

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
            case LISTENING: return 0.35f + 0.45f * (now - lastRms < 250 ? rms : 0f);
            case PROCESSING: return 0.45f;
            case ANSWERING: return speaking ? 0.45f : 0.15f;
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
        float pulse = state == State.ANSWERING && speaking ? 0.08f * (float) Math.abs(Math.sin(t * 5f))
                : state == State.PROCESSING ? 0.06f * (float) Math.sin(t * 4f) : 0f;
        float a = Math.max(0f, Math.min(1f, intensity + pulse));

        if (a > 0.01f) {
            // 1) İçeri doğru sönen çok hafif yeşil ışıltı (bant değil, tek yumuşak geçiş)
            float haze = 16 * density * (1f + 0.4f * level);
            // Gölgelendiriciler yalnız boyut değişince kurulur; parlaklık boya saydamlığıyla verilir (kare başına nesne yok).
            if (hazeTop == null || Math.abs(hazeSize - haze) > density || hazeW != w || hazeH != h) buildHaze(w, h, haze, 0x3756c26a);
            fill.setAlpha((int) (255 * a));
            fill.setShader(hazeTop);    c.drawRect(0, 0, w, haze, fill);
            fill.setShader(hazeBottom); c.drawRect(0, h - haze, w, h, fill);
            fill.setShader(hazeLeft);   c.drawRect(0, 0, haze, h, fill);
            fill.setShader(hazeRight);  c.drawRect(w - haze, 0, w, h, fill);
            fill.setShader(null);
            // 2) Kenarda ince, renkleri dönen tek çizgi
            if (sweep == null || sweepW != w || sweepH != h) {
                sweep = new SweepGradient(w / 2f, h / 2f,
                        new int[]{0xff7fcf72, 0xff38aaa0, 0xffd6c25a, 0xff56c26a, 0xff2f8f55, 0xff7fcf72}, null);
                sweepW = w; sweepH = h;
            }
            float speed = state == State.PROCESSING ? 120f : state == State.LISTENING ? 50f + 100f * level : 40f;
            rotate.setRotate((t * speed) % 360f, w / 2f, h / 2f);
            sweep.setLocalMatrix(rotate);
            paint.setShader(sweep);
            float sw = 2.5f * density * (1f + 0.5f * level);
            paint.setStrokeWidth(sw);
            paint.setAlpha((int) (210 * a));
            float corner = 22 * density;
            rect.set(sw / 2f, sw / 2f, w - sw / 2f, h - sw / 2f);
            c.drawRoundRect(rect, corner, corner, paint);
            paint.setShader(null);
        }
        boolean animate = a > 0.01f || want > 0f;
        if (animate && isAttachedToWindow() && getWindowVisibility() == VISIBLE) postInvalidateDelayed(32);
    }
}
