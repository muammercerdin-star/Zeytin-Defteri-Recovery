package com.zeytindefteri.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Tanecik ana sayfada gezer (Shimeji gibi, ama yalnız uygulamanın içinde; "üstte göster" izni yok).
 *
 * - Alt menünün üst kenarında yürür, durur, el sallar, düşünür, göz kırpar, hoplar.
 * - Kartların üstüne zıplar, üstlerinde yürür, aşağı atlar. Sayfa kayınca kartla birlikte kayar;
 *   kart ekrandan çıkarsa düşer. Tutup bir kartın üstüne bırakılırsa oraya konar.
 * - Ara sıra kollarını kaldırıp sayfayı yukarı iter, bırakınca sayfa yerine yaylanır. Yalnız kullanıcı
 *   20 sn dokunmadıysa ve en çok 2 dakikada bir; ekrana dokunulduğu an bırakır.
 * - Dokununca kullanıcının verisine bakarak konuşur ({@link TanecikLines#smart}): yağmur/rüzgâr uyarısı,
 *   hasat hatırlatması, alacak, elek raporu. Balonda "Dinle" (telefonun Türkçe sesi) ve ilgili yere giden düğme.
 * - Soğukta atkı, yağmurda şemsiye.
 * - Parmakla tutulup taşınabilir; bırakınca düşüp seker. Basılı tutunca gizlenebilir (Ayarlar → Tanecik).
 * - 22:00–05:00 arası köşede uyur. Animasyonlar kapalıysa yürümez. Ekran okuyucu karakteri atlar.
 * - İlk açılışta kendini bir kez tanıtır.
 */
final class TanecikWalker {
    private static final String PREFS = "tanecik", KEY_ON = "enabled", KEY_LINE = "line_index", KEY_INTRO = "intro_done",
            KEY_SHOWN = "shown_today";
    private static final int WALK_FRAME_MS = 200;
    private static final long BUBBLE_MS = 7000;

    static boolean enabled(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, true);
    }

    static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply();
    }

    private final Activity activity;
    private final FrameLayout layer;
    private final View floorRef;
    private final Supplier<TanecikLines.Facts> facts;
    /** Ana sayfanın kayan sayfası (yoksa null): kartlar platform, itme bu sayfayı kaydırır. */
    private final Supplier<ScrollView> page;
    private final Consumer<String> onAction;
    /** Hareket eden kap: gövde + kıyafet birlikte döner/taşınır. */
    private final FrameLayout actor;
    private final ImageView body, outfit;
    /** Başındaki "!" : bugün söylenmemiş önemli bir şey var (yağmur, rüzgâr, bekleyen alacak). */
    private final TextView news;
    private final LinearLayout bubble;
    private final TextView bubbleText, listen, go;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final int w, h, slop, longPressMs;

    private boolean active, attached, sleeping, walkFrame, dragging, longPressed, idling;
    private float downX, downY, grabDx, grabDy;
    private long downAt;
    private String bubbleAction = "", bubbleQuestion = "";
    /** Üstünde durduğu kart; null: alt menünün üstü. */
    private View platform;
    private boolean jumping, pushing;
    private ValueAnimator jumpAnim, pushAnim;
    private long lastUserTouch = SystemClock.uptimeMillis(), lastPush;
    private final ViewTreeObserver.OnScrollChangedListener scrollWatch = this::onPageScrolled;
    private ViewTreeObserver watchedTree;

    private TextToSpeech tts;
    private boolean ttsReady, ttsFailed, talking;

    TanecikWalker(Activity activity, FrameLayout layer, View floorRef, Supplier<ScrollView> page,
                  Supplier<TanecikLines.Facts> facts, Consumer<String> onAction) {
        this.page = page;
        this.activity = activity;
        this.layer = layer;
        this.floorRef = floorRef;
        this.facts = facts;
        this.onAction = onAction;
        w = dp(58);
        h = Math.round(w * 270f / 240f);
        slop = ViewConfiguration.get(activity).getScaledTouchSlop();
        longPressMs = ViewConfiguration.getLongPressTimeout() + 150;

        actor = new FrameLayout(activity);
        actor.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        actor.setVisibility(View.GONE);
        actor.setOnTouchListener(this::onTouch);
        // Dönme/basılma noktası: yatayda orta, dikeyde ayak. (Biri elle verilince öbürü de verilmeli.)
        actor.setPivotX(w / 2f);
        actor.setPivotY(h);
        body = new ImageView(activity);
        body.setImageResource(R.drawable.tanecik_duruyor);
        body.setScaleType(ImageView.ScaleType.FIT_CENTER);
        outfit = new ImageView(activity);
        outfit.setScaleType(ImageView.ScaleType.FIT_CENTER);
        outfit.setVisibility(View.GONE);
        actor.addView(body, new FrameLayout.LayoutParams(-1, -1));
        actor.addView(outfit, new FrameLayout.LayoutParams(-1, -1));
        news = new TextView(activity);
        news.setText("!");
        news.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        news.setTypeface(Typeface.DEFAULT_BOLD);
        news.setTextColor(0xFF24341A);
        news.setGravity(Gravity.CENTER);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(0xFFF2C94C);
        news.setBackground(dot);
        news.setVisibility(View.GONE);
        actor.addView(news, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP | Gravity.START));

        bubble = new LinearLayout(activity);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFEEF3E8);
        bg.setCornerRadius(dp(16));
        bubble.setBackground(bg);
        bubble.setElevation(dp(6));
        bubble.setVisibility(View.GONE);
        bubbleText = new TextView(activity);
        bubbleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        bubbleText.setTextColor(0xFF24341A);
        bubbleText.setLineSpacing(0, 1.12f);
        bubbleText.setOnClickListener(v -> hideBubble());
        bubble.addView(bubbleText);
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        listen = bubbleButton("🔊 Dinle");
        listen.setContentDescription("Tanecik'in sözünü sesli dinle");
        listen.setOnClickListener(v -> toggleTalk());
        go = bubbleButton("");
        go.setOnClickListener(v -> {
            String action = TanecikLines.GO_ASK.equals(bubbleAction) ? "ASK|" + bubbleQuestion : bubbleAction;
            hideBubble();
            resumeIdle();
            if (!action.isEmpty() && onAction != null) onAction.accept(action);
        });
        buttons.addView(listen);
        View gap = new View(activity);
        buttons.addView(gap, new LinearLayout.LayoutParams(0, 1, 1));
        buttons.addView(go);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(4);
        bubble.addView(buttons, bp);

        // Klavye açılınca ya da alt menü yer değiştirince ayaklar yine menünün üstünde kalsın.
        floorRef.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (attached && !dragging && !jumping && platform == null && t != ot) actor.setTranslationY(floorY());
        });
    }

    private TextView bubbleButton(String label) {
        TextView t = new TextView(activity);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFF2E7D32);
        t.setPadding(dp(4), dp(8), dp(4), dp(8));
        t.setMinHeight(dp(40));
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
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
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (RuntimeException ignored) { }
            tts = null;
        }
        if (attached) {
            layer.removeView(actor);
            layer.removeView(bubble);
            attached = false;
        }
    }

    // ------------------------------------------------------------ yaşam döngüsü

    private void start() {
        if (!attached) {
            layer.addView(actor, new FrameLayout.LayoutParams(w, h));
            layer.addView(bubble, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT));
            attached = true;
        }
        handler.postDelayed(this::startAfterLayout, 150);   // yerleşim bitmeden zemin bilinmez
    }

    private void startAfterLayout() {
        if (!active) return;
        if (floorRef.getTop() <= 0 || layer.getWidth() <= 0) { handler.postDelayed(this::startAfterLayout, 300); return; }
        sleeping = TanecikLines.sleeping(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
        platform = null;
        watchScroll(true);
        dressFor(readFacts());
        refreshNews();
        actor.setVisibility(View.VISIBLE);
        actor.setTranslationY(floorY());
        if (sleeping || !motionAllowed()) {
            actor.setTranslationX(maxX() - dp(4));
            face(true);
            body.setImageResource(sleeping ? R.drawable.tanecik_uyuyor : R.drawable.tanecik_duruyor);
            if (!sleeping) maybeIntro();
            return;
        }
        // Soldan yürüyerek girer.
        actor.setTranslationX(-w);
        handler.postDelayed(() -> walkTo(randomX(), this::maybeIntroOrIdle), 900);
    }

    private void stop() {
        handler.removeCallbacksAndMessages(null);
        cancelMotion();
        watchScroll(false);
        platform = null;
        actor.setVisibility(View.GONE);
        bubble.setVisibility(View.GONE);
        stopTalk();
        dragging = false;
        idling = false;
    }

    // ------------------------------------------------------------ davranış

    private void walkTo(float targetX, Runnable then) {
        if (!active || dragging) return;
        idling = false;
        targetX = Math.max(minSurfaceX(), Math.min(maxSurfaceX(), targetX));
        float dx = targetX - actor.getTranslationX();
        if (Math.abs(dx) < dp(8)) { then.run(); return; }
        face(dx > 0);
        long duration = (long) (Math.abs(dx) / dp(38) * 1000);   // ~38 dp/sn: ağır ağır
        handler.removeCallbacks(walkTick);
        handler.post(walkTick);
        actor.animate().translationX(targetX).setDuration(duration).setInterpolator(new LinearInterpolator())
                .withEndAction(() -> {
                    handler.removeCallbacks(walkTick);
                    actor.setTranslationY(surfaceY());
                    then.run();
                }).start();
    }

    private final Runnable walkTick = new Runnable() {
        @Override public void run() {
            if (!active || dragging) return;
            walkFrame = !walkFrame;
            body.setImageResource(walkFrame ? R.drawable.tanecik_yuruyor_1 : R.drawable.tanecik_yuruyor_2);
            actor.setTranslationY(surfaceY() - (walkFrame ? dp(2) : 0));   // adımda hafif sekme
            handler.postDelayed(this, WALK_FRAME_MS);
        }
    };

    private void maybeIntroOrIdle() {
        if (!maybeIntro()) idle();
    }

    /** İlk kez: kendini tanıtır. Tanıttıysa true. */
    private boolean maybeIntro() {
        SharedPreferences p = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (p.getBoolean(KEY_INTRO, false) || sleeping) return false;
        p.edit().putBoolean(KEY_INTRO, true).apply();
        say(new TanecikLines.Line(TanecikLines.intro(), "", ""));
        return true;
    }

    private void idle() {
        if (!active || dragging) return;
        idling = true;
        // Haberi varsa ara sıra el sallayarak dikkat çeker.
        if (news.getVisibility() == View.VISIBLE && random.nextInt(3) == 0) {
            body.setImageResource(R.drawable.tanecik_merhaba);
            handler.postDelayed(this::idleStill, 1200);
            return;
        }
        idleStill();
    }

    private void idleStill() {
        if (!active || dragging) return;
        idling = true;
        body.setImageResource(R.drawable.tanecik_duruyor);
        handler.removeCallbacks(blink);
        handler.postDelayed(blink, 1800 + random.nextInt(2500));
        handler.postDelayed(this::nextAction, 3500 + random.nextInt(5000));
    }

    private final Runnable blink = new Runnable() {
        @Override public void run() {
            if (!active || !idling || dragging) return;
            body.setImageResource(R.drawable.tanecik_duruyor_kirp);
            handler.postDelayed(() -> { if (active && idling) body.setImageResource(R.drawable.tanecik_duruyor); }, 140);
            handler.postDelayed(this, 2200 + random.nextInt(3000));
        }
    };

    private void nextAction() {
        if (!active || dragging || bubble.getVisibility() == View.VISIBLE) return;
        idling = false;
        handler.removeCallbacks(blink);
        int r = random.nextInt(100);
        if (platform != null) {
            // Kartın üstünde: biraz gezinir, sonra aşağı atlar.
            if (r < 35) walkTo(randomX(), this::idle);
            else if (r < 60) flyTo(randomFloorX(), null, this::idle);   // pervaneyle süzülerek iner
            else if (r < 72) hop();
            else if (r < 86) pose(R.drawable.tanecik_merhaba, 1600);
            else pose(R.drawable.tanecik_dusunuyor, 2200);
            return;
        }
        if (r < 30) { walkTo(randomX(), this::idle); return; }
        if (r < 42) { hop(); return; }
        if (r < 62) {
            View target = pickPlatform();
            if (target != null) { jumpTo(randomXOn(target), target, this::idle); return; }
            walkTo(randomX(), this::idle);
            return;
        }
        if (r < 70 && canPush()) { push(); return; }
        if (r < 85) pose(R.drawable.tanecik_merhaba, 1600);
        else if (r < 95) pose(R.drawable.tanecik_dusunuyor, 2200);
        else idle();
    }

    // ------------------------------------------------------------ zıplama ve kartlar

    /** Yerinde hoplama. */
    private void hop() {
        jumpTo(actor.getTranslationX() + (random.nextBoolean() ? 1 : -1) * dp(14), platform, this::idle);
    }

    /** Kavisli zıplama; target null: alt menünün üstü. Varış yüksekliği her karede yeniden hesaplanır (sayfa kayabilir). */
    private void jumpTo(float targetX, View target, Runnable then) {
        if (!active || dragging) return;
        cancelMotion();
        idling = false;
        jumping = true;
        handler.removeCallbacks(blink);
        final float x0 = actor.getTranslationX(), y0 = actor.getTranslationY();
        final float x1 = Math.max(minXOn(target), Math.min(maxXOn(target), targetX));
        float y1Now = yOn(target);
        final float arc = Math.max(dp(36), (y0 - y1Now) > 0 ? dp(40) : dp(24));
        if (Math.abs(x1 - x0) > dp(4)) face(x1 > x0);
        body.setImageResource(R.drawable.tanecik_zipliyor);
        long duration = 520 + (long) Math.min(500, Math.abs(y0 - y1Now) / getDensity() * 2.2);
        jumpAnim = ValueAnimator.ofFloat(0f, 1f);
        jumpAnim.setDuration(duration);
        jumpAnim.setInterpolator(new LinearInterpolator());
        jumpAnim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            float y1 = yOn(target);
            actor.setTranslationX(x0 + (x1 - x0) * t);
            actor.setTranslationY(y0 + (y1 - y0) * t - arc * 4 * t * (1 - t)
                    - (y1 < y0 ? (y0 - y1) * 0.25f * 4 * t * (1 - t) : 0));
        });
        jumpAnim.addListener(new AnimatorListenerAdapter() {
            boolean cancelled;
            @Override public void onAnimationCancel(Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(Animator a) {
                jumping = false;
                if (cancelled || !active) return;
                if (target != null && !validPlatform(target)) { platform = null; fall(); return; }
                platform = target;
                actor.setTranslationY(surfaceY());
                land(then);
            }
        });
        jumpAnim.start();
    }

    /** Yere değince hafif basılma (ayaktan). */
    private void land(Runnable then) {
        body.setImageResource(R.drawable.tanecik_duruyor);
        actor.setScaleY(0.86f);
        actor.animate().scaleY(1f).setDuration(220).setInterpolator(new OvershootInterpolator(2f))
                .withEndAction(then).start();
    }

    /** Kart ekrandan çıktı ya da altından kaydı: pervanesini açıp süzülerek iner. */
    private void fall() {
        cancelMotion();
        platform = null;
        idling = false;
        float fallDist = floorY() - actor.getTranslationY();
        if (!motionAllowed() || fallDist < dp(4)) { actor.setTranslationY(floorY()); resumeIdle(); return; }
        flyTo(actor.getTranslationX(), null, this::resumeIdle);
    }

    private static final int[] PROP = {
            R.drawable.tanecik_ucuyor_1, R.drawable.tanecik_ucuyor_2,
            R.drawable.tanecik_ucuyor_3, R.drawable.tanecik_ucuyor_4};
    private int propFrame;

    /** Pervane: yapraklar döner (dört kare). */
    private final Runnable propTick = new Runnable() {
        @Override public void run() {
            if (!active || !jumping) return;
            propFrame = (propFrame + 1) % PROP.length;
            body.setImageResource(PROP[propFrame]);
            handler.postDelayed(this, 60);
        }
    };

    /**
     * Pervaneyle süzülerek iniş: önce hafifçe havalanır, sonra sallana sallana ağır ağır iner.
     * target null: alt menünün üstü. Varış yüksekliği her karede yeniden hesaplanır.
     */
    private void flyTo(float targetX, View target, Runnable then) {
        if (!active || dragging) return;
        cancelMotion();
        idling = false;
        jumping = true;
        handler.removeCallbacks(blink);
        final float x0 = actor.getTranslationX(), y0 = actor.getTranslationY();
        final float x1 = Math.max(minXOn(target), Math.min(maxXOn(target), targetX));
        float dist = Math.abs(yOn(target) - y0);
        if (Math.abs(x1 - x0) > dp(4)) face(x1 > x0);
        final float sway = dp(10), lift = dp(14);
        long duration = Math.min(3200, 900 + (long) (dist / getDensity() * 6));
        handler.removeCallbacks(propTick);
        handler.post(propTick);
        jumpAnim = ValueAnimator.ofFloat(0f, 1f);
        jumpAnim.setDuration(duration);
        jumpAnim.setInterpolator(new LinearInterpolator());
        jumpAnim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            float y1 = yOn(target);
            // İlk %15'te havalanma, sonra sabit hızla iniş; sona doğru yavaşlar.
            float down = t < 0.15f ? 0f : (float) Math.sin((t - 0.15f) / 0.85f * Math.PI / 2);
            float up = t < 0.15f ? lift * (t / 0.15f) : lift * (1 - down);
            actor.setTranslationX(x0 + (x1 - x0) * t + (float) Math.sin(t * Math.PI * 3) * sway * (1 - t));
            actor.setTranslationY(y0 + (y1 - y0) * down - up);
        });
        jumpAnim.addListener(new AnimatorListenerAdapter() {
            boolean cancelled;
            @Override public void onAnimationCancel(Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(Animator a) {
                jumping = false;
                handler.removeCallbacks(propTick);
                if (cancelled || !active) return;
                if (target != null && !validPlatform(target)) { platform = null; fall(); return; }
                platform = target;
                actor.setTranslationX(x1);
                actor.setTranslationY(surfaceY());
                land(then);
            }
        });
        jumpAnim.start();
    }

    private void onPageScrolled() {
        if (!attached || !active) return;
        if (platform != null && !jumping && !dragging) {
            if (!validPlatform(platform)) fall();
            else actor.setTranslationY(surfaceY());
        }
        if (bubble.getVisibility() == View.VISIBLE) placeBubble();
    }

    private void watchScroll(boolean on) {
        if (watchedTree != null && watchedTree.isAlive()) watchedTree.removeOnScrollChangedListener(scrollWatch);
        watchedTree = null;
        if (on) {
            watchedTree = layer.getViewTreeObserver();
            watchedTree.addOnScrollChangedListener(scrollWatch);
        }
    }

    /** Ana sayfadaki kartlar (arka planı olan, geniş ve en az 48 dp yüksek görünümler; iki kat derine kadar). */
    private List<View> platforms() {
        List<View> out = new ArrayList<>();
        ScrollView sv = page == null ? null : safePage();
        if (sv == null || sv.getChildCount() == 0 || !(sv.getChildAt(0) instanceof ViewGroup)) return out;
        collect((ViewGroup) sv.getChildAt(0), 0, out);
        return out;
    }

    private void collect(ViewGroup g, int depth, List<View> out) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE) continue;
            if (c.getBackground() != null && c.getWidth() >= layer.getWidth() * 0.5f && c.getHeight() >= dp(48)) out.add(c);
            else if (depth < 1 && c instanceof ViewGroup) collect((ViewGroup) c, depth + 1, out);
        }
    }

    private ScrollView safePage() {
        try { return page.get(); } catch (RuntimeException e) { return null; }
    }

    /** Ekranda üstüne çıkılabilecek bir kart (rastgele). */
    private View pickPlatform() {
        List<View> ok = new ArrayList<>();
        for (View v : platforms()) if (validPlatform(v) && topOf(v) > dp(150)) ok.add(v);
        return ok.isEmpty() ? null : ok.get(random.nextInt(ok.size()));
    }

    /** Kartın üst kenarı görünür ve alt menünün belirgin üstünde mi. */
    private boolean validPlatform(View v) {
        if (v == null || !v.isShown()) return false;
        float top = topOf(v);
        return top - h >= dp(4) && top <= floorY() + h - dp(40);
    }

    private float topOf(View v) {
        int[] a = new int[2], b = new int[2];
        v.getLocationInWindow(a);
        layer.getLocationInWindow(b);
        return a[1] - b[1];
    }

    private float leftOf(View v) {
        int[] a = new int[2], b = new int[2];
        v.getLocationInWindow(a);
        layer.getLocationInWindow(b);
        return a[0] - b[0];
    }

    private float yOn(View target) {
        return target == null ? floorY() : topOf(target) - h + dp(2);
    }

    private float surfaceY() {
        return yOn(platform);
    }

    private float minXOn(View target) {
        return target == null ? 0 : leftOf(target) - w * 0.25f;
    }

    private float maxXOn(View target) {
        return target == null ? maxX() : Math.min(maxX(), leftOf(target) + target.getWidth() - w * 0.75f);
    }

    private float minSurfaceX() { return minXOn(platform); }

    private float maxSurfaceX() { return maxXOn(platform); }

    private float randomXOn(View target) {
        float min = minXOn(target) + dp(8), max = maxXOn(target) - dp(8);
        return max <= min ? min : min + random.nextFloat() * (max - min);
    }

    private float randomFloorX() {
        // Aşağı atlarken çok uzağa değil, yakına.
        float x = actor.getTranslationX() + (random.nextBoolean() ? 1 : -1) * dp(30 + random.nextInt(60));
        return Math.max(dp(8), Math.min(maxX() - dp(8), x));
    }

    // ------------------------------------------------------------ sayfayı itme

    /** Kullanıcı ekrana dokundu: itme varsa hemen bırak (sayfa kullanıcının). */
    void userTouched() {
        lastUserTouch = SystemClock.uptimeMillis();
        if (pushing) {
            if (pushAnim != null) pushAnim.cancel();
            pushing = false;
            if (active && !dragging) resumeIdle();
        }
    }

    private boolean canPush() {
        long now = SystemClock.uptimeMillis();
        ScrollView sv = safePage();
        return sv != null && motionAllowed() && !sleeping && platform == null
                && now - lastUserTouch >= 20000 && now - lastPush >= 120000
                && (sv.canScrollVertically(1) || sv.canScrollVertically(-1));
    }

    /** Kollarını kaldırıp sayfayı yukarı iter, bırakınca sayfa yaylanarak yerine döner. */
    private void push() {
        final ScrollView sv = safePage();
        if (sv == null) { idle(); return; }
        cancelMotion();
        idling = false;
        pushing = true;
        lastPush = SystemClock.uptimeMillis();
        handler.removeCallbacks(blink);
        body.setImageResource(R.drawable.tanecik_itiyor);
        final int start = sv.getScrollY();
        final int dist = (sv.canScrollVertically(1) ? 1 : -1) * dp(110);
        pushAnim = ValueAnimator.ofFloat(0f, 1f, 1f, 0f);
        pushAnim.setDuration(2600);
        pushAnim.setInterpolator(new DecelerateInterpolator());
        pushAnim.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            sv.scrollTo(0, start + Math.round(dist * f));
            // İterken hafifçe uzanır.
            actor.setTranslationY(floorY() - dp(6) * f);
        });
        pushAnim.addListener(new AnimatorListenerAdapter() {
            boolean cancelled;
            @Override public void onAnimationCancel(Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(Animator a) {
                pushing = false;
                actor.setTranslationY(floorY());
                if (cancelled || !active) return;
                body.setImageResource(R.drawable.tanecik_sevinc);
                handler.postDelayed(TanecikWalker.this::idle, 900);
            }
        });
        pushAnim.start();
    }

    private void cancelMotion() {
        actor.animate().cancel();
        actor.setScaleY(1f);
        if (jumpAnim != null) jumpAnim.cancel();
        if (pushAnim != null) pushAnim.cancel();
        jumping = false;
        pushing = false;
        handler.removeCallbacks(walkTick);
        handler.removeCallbacks(propTick);
    }

    private void pose(int res, long ms) {
        body.setImageResource(res);
        handler.postDelayed(this::idle, ms);
    }

    /** Dokununca: sıradaki söz (veriye göre). */
    private void speak() {
        if (sleeping) {
            say(new TanecikLines.Line(TanecikLines.sleepy(), "", ""));
            return;
        }
        TanecikLines.Facts f = readFacts();
        dressFor(f);
        Calendar now = Calendar.getInstance();
        List<TanecikLines.Line> lines = TanecikLines.smart(f, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MONTH) + 1);
        SharedPreferences p = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int i = p.getInt(KEY_LINE, 0);
        java.util.Set<String> shown = shownToday();
        TanecikLines.Line line = TanecikLines.choose(lines, shown, i);
        int at = lines.indexOf(line);
        shown.add(line.id);
        p.edit().putInt(KEY_LINE, (at >= 0 ? at : i) + 1).putString(KEY_SHOWN, today() + "|" + String.join(",", shown)).apply();
        showNews(TanecikLines.hasNews(lines, shown));
        say(line);
    }

    /** Bugün söylenen sözlerin kimlikleri (gün değişince boşalır). */
    private java.util.Set<String> shownToday() {
        java.util.Set<String> out = new java.util.HashSet<>();
        String raw = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SHOWN, "");
        int bar = raw == null ? -1 : raw.indexOf('|');
        if (bar < 0 || !raw.substring(0, bar).equals(today())) return out;
        for (String id : raw.substring(bar + 1).split(",")) if (!id.isEmpty()) out.add(id);
        return out;
    }

    private static String today() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) + "-" + (c.get(Calendar.MONTH) + 1) + "-" + c.get(Calendar.DAY_OF_MONTH);
    }

    /** Önemli bir şey varsa başında "!" belirir (dokunmadan haber verir). */
    private void refreshNews() {
        if (sleeping) { showNews(false); return; }
        Calendar now = Calendar.getInstance();
        try {
            List<TanecikLines.Line> lines = TanecikLines.smart(readFacts(), now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MONTH) + 1);
            showNews(TanecikLines.hasNews(lines, shownToday()));
        } catch (RuntimeException e) {
            showNews(false);
        }
    }

    private void showNews(boolean on) {
        news.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    private void say(TanecikLines.Line line) {
        handler.removeCallbacksAndMessages(null);
        cancelMotion();
        actor.setTranslationY(surfaceY());
        idling = false;
        stopTalk();
        if (!sleeping) mouth(1800);
        showBubble(line);
        handler.postDelayed(this::autoHide, BUBBLE_MS);
    }

    private void autoHide() {
        if (talking) { handler.postDelayed(this::autoHide, 1000); return; }   // okurken kapanmaz
        hideBubble();
        resumeIdle();
    }

    private void resumeIdle() {
        handler.removeCallbacksAndMessages(null);
        if (!active) return;
        if (sleeping) body.setImageResource(R.drawable.tanecik_uyuyor);
        else if (motionAllowed()) idle();
        else body.setImageResource(R.drawable.tanecik_duruyor);
    }

    /** Ağız oynatma: ms boyunca (ms < 0: konuşma sürdükçe). */
    private void mouth(long ms) {
        final long until = ms < 0 ? Long.MAX_VALUE : SystemClock.uptimeMillis() + ms;
        if (mouthRun != null) handler.removeCallbacks(mouthRun);   // aynı anda iki ağız oynamasın
        mouthRun = new Runnable() {
            boolean open;
            @Override public void run() {
                if (!active || dragging) return;
                boolean done = ms < 0 ? !talking : SystemClock.uptimeMillis() >= until;
                if (done) { body.setImageResource(sleeping ? R.drawable.tanecik_uyuyor : R.drawable.tanecik_merhaba); return; }
                open = !open;
                body.setImageResource(open ? R.drawable.tanecik_konusuyor : R.drawable.tanecik_konusuyor_kapali);
                handler.postDelayed(this, open ? 170 : 130);
            }
        };
        handler.post(mouthRun);
    }

    private Runnable mouthRun;

    private void showBubble(TanecikLines.Line line) {
        bubbleText.setText(line.text);
        bubbleAction = line.action == null ? "" : line.action;
        bubbleQuestion = line.question == null ? "" : line.question;
        go.setText(line.actionLabel == null ? "" : line.actionLabel);
        go.setVisibility(bubbleAction.isEmpty() ? View.GONE : View.VISIBLE);
        listen.setText("🔊 Dinle");
        listen.setVisibility(ttsFailed ? View.GONE : View.VISIBLE);
        bubble.setVisibility(View.VISIBLE);
        placeBubble();
        // Ekran okuyucu kullanan, balondaki sözü duysun.
        bubble.announceForAccessibility(line.text);
    }

    /** Balon Tanecik'in başının üstünde; yer yoksa altında. */
    private void placeBubble() {
        // Genişlik sabit: en çok 280 dp, dar ekranda kenarlardan 8'er dp boşluk. Ölçüm ve yerleşim aynı genişlikle
        // yapılır; yoksa düğme satırı balonu ekran genişliğine açar ve balon kenardan taşar.
        int bwPx = Math.max(dp(160), Math.min(dp(280), layer.getWidth() - dp(16)));
        ViewGroup.LayoutParams lp = bubble.getLayoutParams();
        if (lp != null && lp.width != bwPx) {
            lp.width = bwPx;
            bubble.setLayoutParams(lp);
        }
        bubble.measure(View.MeasureSpec.makeMeasureSpec(bwPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        float bw = bwPx, bh = bubble.getMeasuredHeight();
        float x = actor.getTranslationX() + w / 2f - bw / 2f;
        x = Math.max(dp(8), Math.min(layer.getWidth() - bw - dp(8), x));
        float y = actor.getTranslationY() - bh + dp(4);
        if (y < dp(8)) y = Math.min(actor.getTranslationY() + h, floorY() + h - bh);
        bubble.setTranslationX(x);
        bubble.setTranslationY(y);
    }

    private void hideBubble() {
        bubble.setVisibility(View.GONE);
        stopTalk();
    }

    // ------------------------------------------------------------ kıyafet

    private TanecikLines.Facts readFacts() {
        try {
            TanecikLines.Facts f = facts == null ? null : facts.get();
            return f == null ? new TanecikLines.Facts() : f;
        } catch (RuntimeException e) {
            return new TanecikLines.Facts();
        }
    }

    private void dressFor(TanecikLines.Facts f) {
        if (f.raining) {
            outfit.setImageResource(R.drawable.tanecik_semsiye);
            outfit.setVisibility(View.VISIBLE);
        } else if (TanecikLines.cold(f)) {
            outfit.setImageResource(R.drawable.tanecik_atki);
            outfit.setVisibility(View.VISIBLE);
        } else {
            outfit.setVisibility(View.GONE);
        }
    }

    // ------------------------------------------------------------ sesli söyleme

    private void toggleTalk() {
        if (talking) { stopTalk(); return; }
        final String text = bubbleText.getText().toString();
        if (tts == null) {
            listen.setText("🔊 Hazırlanıyor…");
            tts = new TextToSpeech(activity.getApplicationContext(), st -> handler.post(() -> {
                int lang = TextToSpeech.LANG_NOT_SUPPORTED;
                try { if (st == TextToSpeech.SUCCESS) lang = tts.setLanguage(new Locale("tr", "TR")); }
                catch (RuntimeException ignored) { }
                ttsReady = st == TextToSpeech.SUCCESS && lang >= 0;
                if (!ttsReady) {
                    ttsFailed = true;
                    listen.setVisibility(View.GONE);
                    Toast.makeText(activity, "Telefonda Türkçe ses yok. Ayarlar → Metin okuma'dan indirebilirsin.", Toast.LENGTH_LONG).show();
                    return;
                }
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { }
                    @Override public void onDone(String id) { handler.post(() -> talkEnded()); }
                    @Override public void onError(String id) { handler.post(() -> talkEnded()); }
                });
                if (bubble.getVisibility() == View.VISIBLE) talk(text);
            }));
            return;
        }
        if (ttsReady) talk(text);
    }

    private void talk(String text) {
        if (tts == null || !active) return;
        String spoken = text.replace("%", "yüzde ").replace(" TL", " lira").replace("km/s", "kilometre")
                .replace("Zzz…", "");
        if (tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "tanecik-" + System.nanoTime()) != TextToSpeech.ERROR) {
            talking = true;
            listen.setText("■ Durdur");
            if (!sleeping) mouth(-1);
        }
    }

    private void talkEnded() {
        talking = false;
        listen.setText("🔊 Dinle");
    }

    private void stopTalk() {
        if (tts != null && talking) { try { tts.stop(); } catch (RuntimeException ignored) { } }
        talkEnded();
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
                grabDx = downX - actor.getTranslationX(); grabDy = downY - actor.getTranslationY();
                dragging = false; longPressed = false;
                handler.postDelayed(longPressCheck, longPressMs);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (longPressed) return true;
                if (!dragging && Math.hypot(e.getRawX() - downX, e.getRawY() - downY) > slop) {
                    dragging = true;
                    idling = false;
                    platform = null;
                    handler.removeCallbacksAndMessages(null);
                    cancelMotion();
                    hideBubble();
                    body.setImageResource(R.drawable.tanecik_konusuyor);   // "aaa!" havada
                }
                if (dragging) {
                    actor.setTranslationX(Math.max(-w / 3f, Math.min(maxX() + w / 3f, e.getRawX() - grabDx)));
                    actor.setTranslationY(Math.max(0, Math.min(floorY(), e.getRawY() - grabDy)));
                }
                return true;
            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPressCheck);
                if (longPressed) return true;
                if (dragging) drop();
                else if (!jumping && SystemClock.uptimeMillis() - downAt < longPressMs) speak();
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
        float x = Math.max(0, Math.min(maxX(), actor.getTranslationX()));
        // Bırakıldığı yerin altında bir kart varsa onun üstüne konar.
        float feet = actor.getTranslationY() + h - dp(2);
        float center = x + w / 2f;
        View landOn = null;
        float best = Float.MAX_VALUE;
        for (View v : platforms()) {
            float top = topOf(v), left = leftOf(v);
            if (!validPlatform(v) || center < left || center > left + v.getWidth() || top < feet - dp(6)) continue;
            if (top < best) { best = top; landOn = v; }
        }
        final View target = landOn;
        float targetY = yOn(target);
        float fall = targetY - actor.getTranslationY();
        Runnable landed = () -> {
            dragging = false;
            if (!active) return;
            platform = target;
            actor.setTranslationY(surfaceY());
            if (sleeping) { body.setImageResource(R.drawable.tanecik_uyuyor); return; }
            body.setImageResource(R.drawable.tanecik_sevinc);
            handler.postDelayed(this::resumeIdle, 900);
        };
        if (!motionAllowed() || fall < dp(4)) {
            actor.setTranslationX(x);
            actor.setTranslationY(targetY);
            landed.run();
            return;
        }
        if (fall > dp(60)) {
            // Yüksekten bırakıldı: pervaneyle süzülür.
            dragging = false;
            final boolean wasSleeping = sleeping;
            flyTo(x, target, () -> {
                if (!active) return;
                if (wasSleeping) { body.setImageResource(R.drawable.tanecik_uyuyor); return; }
                body.setImageResource(R.drawable.tanecik_sevinc);
                handler.postDelayed(this::resumeIdle, 900);
            });
            return;
        }
        long duration = (long) Math.min(900, 250 + fall / getDensity() * 1.6);
        actor.animate().translationX(x).translationY(targetY).setDuration(duration)
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

    private float getDensity() {
        return activity.getResources().getDisplayMetrics().density;
    }

    private float randomX() {
        float min = minSurfaceX() + dp(8), max = maxSurfaceX() - dp(8);
        return max <= min ? min : min + random.nextFloat() * (max - min);
    }

    private void face(boolean right) {
        actor.setScaleX(right ? 1f : -1f);
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
