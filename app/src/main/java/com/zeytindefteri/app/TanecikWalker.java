package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.BounceInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Random;

/**
 * Tanecik ana sayfada gezer (Shimeji gibi, ama yalnız uygulamanın içinde; "üstte göster" izni yok).
 *
 * - Alt menünün üst kenarında yürür, durur, el sallar, düşünür.
 * - Dokununca kısa bir söz söyler ({@link TanecikLines}); ağzı oynar.
 * - Parmakla tutulup taşınabilir; bırakınca düşüp seker.
 * - Basılı tutunca gizlenebilir; Ayarlar → Tanecik'ten geri açılır.
 * - 22:00–05:00 arası köşede uyur.
 * - Telefonda animasyonlar kapalıysa (erişilebilirlik) yürümez, köşede durur.
 * - Ekran okuyucu atlar (yalnız süs).
 */
final class TanecikWalker {
    private static final String PREFS = "tanecik", KEY_ON = "enabled", KEY_LINE = "line_index";
    private static final int WALK_FRAME_MS = 200;

    static boolean enabled(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, true);
    }

    static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply();
    }

    private final Activity activity;
    private final FrameLayout layer;
    private final View floorRef;
    private final ImageView body;
    private final TextView bubble;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final int w, h, slop, longPressMs;

    private boolean active, attached, sleeping, walkFrame, dragging, longPressed, facingRight = true;
    private float downX, downY, grabDx, grabDy;
    private long downAt;

    TanecikWalker(Activity activity, FrameLayout layer, View floorRef) {
        this.activity = activity;
        this.layer = layer;
        this.floorRef = floorRef;
        w = dp(58);
        h = Math.round(w * 270f / 240f);
        slop = ViewConfiguration.get(activity).getScaledTouchSlop();
        longPressMs = ViewConfiguration.getLongPressTimeout() + 150;

        body = new ImageView(activity);
        body.setImageResource(R.drawable.tanecik_duruyor);
        body.setScaleType(ImageView.ScaleType.FIT_CENTER);
        body.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        body.setVisibility(View.GONE);
        body.setOnTouchListener(this::onTouch);

        bubble = new TextView(activity);
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        bubble.setTextColor(0xFF24341A);
        bubble.setLineSpacing(0, 1.12f);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setMaxWidth(dp(250));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFEEF3E8);
        bg.setCornerRadius(dp(16));
        bubble.setBackground(bg);
        bubble.setElevation(dp(6));
        bubble.setVisibility(View.GONE);
        bubble.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        bubble.setOnClickListener(v -> hideBubble());

        // Klavye açılınca ya da alt menü yer değiştirince ayaklar yine menünün üstünde kalsın.
        floorRef.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (attached && !dragging && t != ot) body.setTranslationY(floorY());
        });
    }

    /** Ana sayfa görünürken true; başka sekme, rapor ya da arka plan iken false. */
    void setActive(boolean on) {
        boolean want = on && enabled(activity);
        if (want == active) return;
        active = want;
        if (want) start();
        else stop();
    }

    void release() {
        stop();
        if (attached) {
            layer.removeView(body);
            layer.removeView(bubble);
            attached = false;
        }
    }

    // ------------------------------------------------------------ yaşam döngüsü

    private void start() {
        if (!attached) {
            layer.addView(body, new FrameLayout.LayoutParams(w, h));
            layer.addView(bubble, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT));
            attached = true;
        }
        // Yerleşim bitmeden zemin bilinmez.
        layer.post(() -> {
            if (!active || floorRef.getTop() <= 0 || layer.getWidth() <= 0) {
                if (active) handler.postDelayed(this::startAfterLayout, 300);
                return;
            }
            startAfterLayout();
        });
    }

    private void startAfterLayout() {
        if (!active) return;
        if (floorRef.getTop() <= 0 || layer.getWidth() <= 0) { handler.postDelayed(this::startAfterLayout, 300); return; }
        sleeping = TanecikLines.sleeping(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
        body.setVisibility(View.VISIBLE);
        body.setTranslationY(floorY());
        if (sleeping || !motionAllowed()) {
            body.setTranslationX(maxX() - dp(4));
            face(true);
            body.setImageResource(sleeping ? R.drawable.tanecik_uyuyor : R.drawable.tanecik_duruyor);
            return;
        }
        // Soldan yürüyerek girer.
        body.setTranslationX(-w);
        handler.postDelayed(() -> walkTo(randomX()), 900);
    }

    private void stop() {
        handler.removeCallbacksAndMessages(null);
        body.animate().cancel();
        body.setVisibility(View.GONE);
        bubble.setVisibility(View.GONE);
        dragging = false;
    }

    // ------------------------------------------------------------ davranış

    private void walkTo(float targetX) {
        if (!active || dragging) return;
        float from = body.getTranslationX();
        float dx = targetX - from;
        if (Math.abs(dx) < dp(8)) { idle(); return; }
        face(dx > 0);
        long duration = (long) (Math.abs(dx) / dp(38) * 1000);   // ~38 dp/sn: ağır ağır
        handler.removeCallbacks(walkTick);
        handler.post(walkTick);
        body.animate().translationX(targetX).setDuration(duration).setInterpolator(new LinearInterpolator())
                .withEndAction(() -> {
                    handler.removeCallbacks(walkTick);
                    body.setTranslationY(floorY());
                    idle();
                }).start();
    }

    private final Runnable walkTick = new Runnable() {
        @Override public void run() {
            if (!active || dragging) return;
            walkFrame = !walkFrame;
            body.setImageResource(walkFrame ? R.drawable.tanecik_yuruyor_1 : R.drawable.tanecik_yuruyor_2);
            body.setTranslationY(floorY() - (walkFrame ? dp(2) : 0));   // adımda hafif sekme
            handler.postDelayed(this, WALK_FRAME_MS);
        }
    };

    private void idle() {
        if (!active || dragging) return;
        body.setImageResource(R.drawable.tanecik_duruyor);
        handler.postDelayed(this::nextAction, 3500 + random.nextInt(5000));
    }

    private void nextAction() {
        if (!active || dragging || bubble.getVisibility() == View.VISIBLE) return;
        int r = random.nextInt(100);
        if (r < 55) walkTo(randomX());
        else if (r < 75) pose(R.drawable.tanecik_merhaba, 1600);
        else if (r < 90) pose(R.drawable.tanecik_dusunuyor, 2200);
        else idle();
    }

    private void pose(int res, long ms) {
        body.setImageResource(res);
        handler.postDelayed(this::idle, ms);
    }

    private void speak() {
        handler.removeCallbacksAndMessages(null);
        body.animate().cancel();
        body.setTranslationY(floorY());
        Calendar now = Calendar.getInstance();
        String text;
        if (sleeping) {
            text = TanecikLines.sleepy();
        } else {
            android.content.SharedPreferences p = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            int i = p.getInt(KEY_LINE, 0);
            text = TanecikLines.line(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MONTH) + 1, i);
            p.edit().putInt(KEY_LINE, i + 1).apply();
            final long until = SystemClock.uptimeMillis() + 1800;
            handler.post(new Runnable() {
                boolean open;
                @Override public void run() {
                    if (!active) return;
                    if (SystemClock.uptimeMillis() >= until) { body.setImageResource(R.drawable.tanecik_merhaba); return; }
                    open = !open;
                    body.setImageResource(open ? R.drawable.tanecik_konusuyor : R.drawable.tanecik_konusuyor_kapali);
                    handler.postDelayed(this, open ? 170 : 130);
                }
            });
        }
        showBubble(text);
        handler.postDelayed(() -> {
            hideBubble();
            if (sleeping) body.setImageResource(R.drawable.tanecik_uyuyor);
            else if (motionAllowed()) idle();
            else body.setImageResource(R.drawable.tanecik_duruyor);
        }, 5200);
    }

    private void showBubble(String text) {
        bubble.setText(text);
        bubble.setVisibility(View.VISIBLE);
        bubble.measure(View.MeasureSpec.makeMeasureSpec(dp(250), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        float bw = bubble.getMeasuredWidth(), bh = bubble.getMeasuredHeight();
        float x = body.getTranslationX() + w / 2f - bw / 2f;
        x = Math.max(dp(8), Math.min(layer.getWidth() - bw - dp(8), x));
        bubble.setTranslationX(x);
        bubble.setTranslationY(Math.max(dp(8), floorY() - bh + dp(4)));
    }

    private void hideBubble() {
        bubble.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------ dokunma: dokun, taşı, basılı tut

    private final Runnable longPressCheck = () -> {
        if (dragging) return;
        longPressed = true;
        askHide();
    };

    private boolean onTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX(); downY = e.getRawY(); downAt = SystemClock.uptimeMillis();
                grabDx = downX - body.getTranslationX(); grabDy = downY - body.getTranslationY();
                dragging = false; longPressed = false;
                handler.postDelayed(longPressCheck, longPressMs);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (longPressed) return true;
                if (!dragging && Math.hypot(e.getRawX() - downX, e.getRawY() - downY) > slop) {
                    dragging = true;
                    handler.removeCallbacksAndMessages(null);
                    body.animate().cancel();
                    hideBubble();
                    body.setImageResource(R.drawable.tanecik_konusuyor);   // "aaa!" havada
                }
                if (dragging) {
                    body.setTranslationX(Math.max(-w / 3f, Math.min(maxX() + w / 3f, e.getRawX() - grabDx)));
                    body.setTranslationY(Math.max(0, Math.min(floorY(), e.getRawY() - grabDy)));
                }
                return true;
            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPressCheck);
                if (longPressed) return true;
                if (dragging) drop();
                else if (SystemClock.uptimeMillis() - downAt < longPressMs) speak();
                return true;
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPressCheck);
                if (dragging) drop();
                return true;
            default:
                return false;
        }
    }

    private void drop() {
        float fall = floorY() - body.getTranslationY();
        float x = Math.max(0, Math.min(maxX(), body.getTranslationX()));
        Runnable landed = () -> {
            dragging = false;
            if (!active) return;
            if (sleeping) { body.setImageResource(R.drawable.tanecik_uyuyor); return; }
            body.setImageResource(R.drawable.tanecik_sevinc);
            handler.postDelayed(motionAllowed() ? this::idle : () -> body.setImageResource(R.drawable.tanecik_duruyor), 900);
        };
        if (!motionAllowed() || fall < dp(4)) {
            body.setTranslationX(x);
            body.setTranslationY(floorY());
            landed.run();
            return;
        }
        long duration = (long) Math.min(900, 250 + fall / activity.getResources().getDisplayMetrics().density * 1.6);
        body.animate().translationX(x).translationY(floorY()).setDuration(duration)
                .setInterpolator(new BounceInterpolator()).withEndAction(landed).start();
    }

    private void askHide() {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle("Tanecik gizlensin mi?")
                .setMessage("İstediğin zaman Ayarlar → Tanecik'ten geri açabilirsin.")
                .setPositiveButton("Gizle", (d, which) -> {
                    setEnabled(activity, false);
                    setActive(false);
                    Toast.makeText(activity, "Tanecik gizlendi.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Kalsın", null)
                .show();
    }

    // ------------------------------------------------------------ yardımcılar

    private float floorY() {
        // Ayaklar alt menünün üst kenarına basar.
        return floorRef.getTop() - h + dp(2);
    }

    private float maxX() {
        return Math.max(0, layer.getWidth() - w);
    }

    private float randomX() {
        float min = dp(8), max = maxX() - dp(8);
        return max <= min ? min : min + random.nextFloat() * (max - min);
    }

    private void face(boolean right) {
        facingRight = right;
        body.setScaleX(right ? 1f : -1f);
    }

    /** Telefonda "animasyonları kapat" açıksa Tanecik yürümez. */
    private boolean motionAllowed() {
        try {
            return Settings.Global.getFloat(activity.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private int dp(int v) {
        return Math.round(v * activity.getResources().getDisplayMetrics().density);
    }
}
