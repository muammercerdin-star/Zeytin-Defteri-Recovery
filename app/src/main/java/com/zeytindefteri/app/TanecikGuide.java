package com.zeytindefteri.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Tanecik başka bir ekranda (ör. Elek kaydı) gösterim yapar: pervanesiyle alandan alana uçar, eliyle gösterir,
 * gerekirse örnek değeri yazar ve büyük altyazıyla anlatır. Kaydet'e BASMAZ; yazdığını sonunda siler, ekranı kapatır
 * (ana sayfadaki tur kaldığı yerden sürer). Ekranın üstünde ince bir katmandır; dokunuşları engellemez.
 */
final class TanecikGuide {
    static final String EXTRA = "tanecik_tour";

    private static final int[] PROP = {
            R.drawable.tanecik_ucuyor_1, R.drawable.tanecik_ucuyor_2,
            R.drawable.tanecik_ucuyor_3, R.drawable.tanecik_ucuyor_4};

    private final Activity activity;
    private final List<TanecikTour.GuideStep> steps;
    private final FrameLayout layer;
    private final ImageView body;
    private final TanecikVoice voice;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<EditText> typed = new ArrayList<>();
    private final int w, h;
    private boolean flying, done;
    private int prop;

    TanecikGuide(Activity activity, List<TanecikTour.GuideStep> steps) {
        this.activity = activity;
        this.steps = steps;
        w = dp(64);
        h = Math.round(w * 270f / 240f);
        layer = new FrameLayout(activity);
        layer.setClickable(false);
        ((ViewGroup) activity.findViewById(android.R.id.content)).addView(layer, new FrameLayout.LayoutParams(-1, -1));
        body = new ImageView(activity);
        body.setImageResource(R.drawable.tanecik_duruyor);
        body.setPivotX(w / 2f);
        body.setPivotY(h);
        body.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        body.setVisibility(View.GONE);
        layer.addView(body, new FrameLayout.LayoutParams(w, h));
        voice = new TanecikVoice(activity, layer);

        TextView stop = new TextView(activity);
        stop.setText("✕ Turu bitir");
        stop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        stop.setTypeface(Typeface.DEFAULT_BOLD);
        stop.setTextColor(0xFF24341A);
        stop.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE6EEF3E8);
        bg.setCornerRadius(dp(20));
        stop.setBackground(bg);
        stop.setOnClickListener(v -> { TanecikTour.cancelRequested = true; finish(); });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        lp.bottomMargin = dp(18);
        lp.rightMargin = dp(14);
        layer.addView(stop, lp);
    }

    void start() {
        voice.prepare(() -> {
            if (done) return;
            body.setVisibility(View.VISIBLE);
            body.setTranslationX(layer.getWidth() - w - dp(10));
            body.setTranslationY(layer.getHeight());
            step(0);
        });
    }

    void release() {
        done = true;
        handler.removeCallbacksAndMessages(null);
        voice.release();
    }

    private void step(int i) {
        if (done || activity.isFinishing()) return;
        if (i >= steps.size()) { handler.postDelayed(this::finish, 500); return; }
        TanecikTour.GuideStep st = steps.get(i);
        int id = activity.getResources().getIdentifier(st.view, "id", activity.getPackageName());
        View v = id == 0 ? null : activity.findViewById(id);
        if (v == null || !v.isShown()) { step(i + 1); return; }
        ScrollView sv = scrollerOf(v);
        if (sv != null) {
            int top = offsetIn(v, sv);
            sv.smoothScrollTo(0, Math.max(0, top - Math.round(sv.getHeight() * 0.38f)));
        }
        handler.postDelayed(() -> flyTo(v, () -> pointAndSay(v, st, i)), sv != null ? 450 : 50);
    }

    private void pointAndSay(View v, TanecikTour.GuideStep st, int i) {
        if (done) return;
        body.setImageResource(R.drawable.tanecik_gosteriyor);
        v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(160)
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()).start();
        Runnable say = () -> voice.say(st.text, () -> handler.postDelayed(() -> step(i + 1), 250));
        if (!st.type.isEmpty() && v instanceof EditText) type((EditText) v, st.type, 0, say);
        else say.run();
    }

    /** Örnek değeri harf harf yazar (klavye açılmaz). */
    private void type(EditText e, String text, int n, Runnable then) {
        if (done) return;
        if (n == 0 && !typed.contains(e)) typed.add(e);
        e.setText(text.substring(0, n));
        if (n >= text.length()) { then.run(); return; }
        handler.postDelayed(() -> type(e, text, n + 1, then), 170);
    }

    /** Pervaneyle hedefin yanına uçar ve ona döner. */
    private void flyTo(View v, Runnable then) {
        int[] a = new int[2], b = new int[2];
        v.getLocationInWindow(a);
        layer.getLocationInWindow(b);
        float vx = a[0] - b[0], vy = a[1] - b[1];
        boolean rightSide = vx + v.getWidth() / 2f < layer.getWidth() / 2f;   // hedef soldaysa sağına konar
        float x = rightSide ? vx + v.getWidth() - w * 0.15f : vx - w * 0.85f;
        x = Math.max(0, Math.min(layer.getWidth() - w, x));
        float y = Math.max(dp(40), Math.min(layer.getHeight() - h - dp(60), vy + v.getHeight() / 2f - h * 0.45f));
        body.setScaleX(rightSide ? -1f : 1f);   // çizim sağa bakar; hedef soldaysa aynala
        flying = true;
        handler.post(propTick);
        body.animate().translationX(x).translationY(y).setDuration(520).withEndAction(() -> {
            flying = false;
            handler.removeCallbacks(propTick);
            then.run();
        }).start();
    }

    private final Runnable propTick = new Runnable() {
        @Override public void run() {
            if (!flying || done) return;
            prop = (prop + 1) % PROP.length;
            body.setImageResource(PROP[prop]);
            handler.postDelayed(this, 60);
        }
    };

    private void finish() {
        if (done) return;
        done = true;
        handler.removeCallbacksAndMessages(null);
        voice.stop();
        for (EditText e : typed) e.setText("");   // gösterim için yazılanı sil: kayıt oluşmaz
        activity.finish();
    }

    private static ScrollView scrollerOf(View v) {
        ViewParent p = v.getParent();
        while (p instanceof View) {
            if (p instanceof ScrollView) return (ScrollView) p;
            p = p.getParent();
        }
        return null;
    }

    private static int offsetIn(View v, View ancestor) {
        int y = 0;
        View cur = v;
        while (cur != null && cur != ancestor) {
            y += cur.getTop();
            ViewParent p = cur.getParent();
            cur = p instanceof View ? (View) p : null;
        }
        return y;
    }

    private int dp(int v) {
        return Math.round(v * activity.getResources().getDisplayMetrics().density);
    }
}
