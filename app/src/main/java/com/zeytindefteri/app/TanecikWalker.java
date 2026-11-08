package com.zeytindefteri.app;

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
import android.view.animation.BounceInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Tanecik ana sayfada gezer (Shimeji gibi, ama yalnız uygulamanın içinde; "üstte göster" izni yok).
 *
 * - Alt menünün üst kenarında yürür, durur, el sallar, düşünür, göz kırpar.
 * - Dokununca kullanıcının verisine bakarak konuşur ({@link TanecikLines#smart}): yağmur/rüzgâr uyarısı,
 *   hasat hatırlatması, alacak, elek raporu. Balonda "Dinle" (telefonun Türkçe sesi) ve ilgili yere giden düğme.
 * - Soğukta atkı, yağmurda şemsiye.
 * - Parmakla tutulup taşınabilir; bırakınca düşüp seker. Basılı tutunca gizlenebilir (Ayarlar → Tanecik).
 * - 22:00–05:00 arası köşede uyur. Animasyonlar kapalıysa yürümez. Ekran okuyucu karakteri atlar.
 * - İlk açılışta kendini bir kez tanıtır.
 */
final class TanecikWalker {
    private static final String PREFS = "tanecik", KEY_ON = "enabled", KEY_LINE = "line_index", KEY_INTRO = "intro_done";
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
    private final Consumer<String> onAction;
    /** Hareket eden kap: gövde + kıyafet birlikte döner/taşınır. */
    private final FrameLayout actor;
    private final ImageView body, outfit;
    private final LinearLayout bubble;
    private final TextView bubbleText, listen, go;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final int w, h, slop, longPressMs;

    private boolean active, attached, sleeping, walkFrame, dragging, longPressed, idling;
    private float downX, downY, grabDx, grabDy;
    private long downAt;
    private String bubbleAction = "";

    private TextToSpeech tts;
    private boolean ttsReady, ttsFailed, talking;

    TanecikWalker(Activity activity, FrameLayout layer, View floorRef,
                  Supplier<TanecikLines.Facts> facts, Consumer<String> onAction) {
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
        body = new ImageView(activity);
        body.setImageResource(R.drawable.tanecik_duruyor);
        body.setScaleType(ImageView.ScaleType.FIT_CENTER);
        outfit = new ImageView(activity);
        outfit.setScaleType(ImageView.ScaleType.FIT_CENTER);
        outfit.setVisibility(View.GONE);
        actor.addView(body, new FrameLayout.LayoutParams(-1, -1));
        actor.addView(outfit, new FrameLayout.LayoutParams(-1, -1));

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
        bubbleText.setMaxWidth(dp(250));
        bubbleText.setOnClickListener(v -> hideBubble());
        bubble.addView(bubbleText);
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        listen = bubbleButton("🔊 Dinle");
        listen.setContentDescription("Tanecik'in sözünü sesli dinle");
        listen.setOnClickListener(v -> toggleTalk());
        go = bubbleButton("");
        go.setOnClickListener(v -> {
            String action = bubbleAction;
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
            if (attached && !dragging && t != ot) actor.setTranslationY(floorY());
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
        dressFor(readFacts());
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
        actor.animate().cancel();
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
        float dx = targetX - actor.getTranslationX();
        if (Math.abs(dx) < dp(8)) { then.run(); return; }
        face(dx > 0);
        long duration = (long) (Math.abs(dx) / dp(38) * 1000);   // ~38 dp/sn: ağır ağır
        handler.removeCallbacks(walkTick);
        handler.post(walkTick);
        actor.animate().translationX(targetX).setDuration(duration).setInterpolator(new LinearInterpolator())
                .withEndAction(() -> {
                    handler.removeCallbacks(walkTick);
                    actor.setTranslationY(floorY());
                    then.run();
                }).start();
    }

    private final Runnable walkTick = new Runnable() {
        @Override public void run() {
            if (!active || dragging) return;
            walkFrame = !walkFrame;
            body.setImageResource(walkFrame ? R.drawable.tanecik_yuruyor_1 : R.drawable.tanecik_yuruyor_2);
            actor.setTranslationY(floorY() - (walkFrame ? dp(2) : 0));   // adımda hafif sekme
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
        if (r < 55) walkTo(randomX(), this::idle);
        else if (r < 75) pose(R.drawable.tanecik_merhaba, 1600);
        else if (r < 90) pose(R.drawable.tanecik_dusunuyor, 2200);
        else idle();
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
        p.edit().putInt(KEY_LINE, i + 1).apply();
        say(lines.get(Math.floorMod(i, lines.size())));
    }

    private void say(TanecikLines.Line line) {
        handler.removeCallbacksAndMessages(null);
        actor.animate().cancel();
        actor.setTranslationY(floorY());
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
        go.setText(line.actionLabel == null ? "" : line.actionLabel);
        go.setVisibility(bubbleAction.isEmpty() ? View.GONE : View.VISIBLE);
        listen.setText("🔊 Dinle");
        listen.setVisibility(ttsFailed ? View.GONE : View.VISIBLE);
        bubble.setVisibility(View.VISIBLE);
        bubble.measure(View.MeasureSpec.makeMeasureSpec(dp(280), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        float bw = bubble.getMeasuredWidth(), bh = bubble.getMeasuredHeight();
        float x = actor.getTranslationX() + w / 2f - bw / 2f;
        x = Math.max(dp(8), Math.min(layer.getWidth() - bw - dp(8), x));
        bubble.setTranslationX(x);
        bubble.setTranslationY(Math.max(dp(8), floorY() - bh + dp(4)));
        // Ekran okuyucu kullanan, balondaki sözü duysun.
        bubble.announceForAccessibility(line.text);
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
                    handler.removeCallbacksAndMessages(null);
                    actor.animate().cancel();
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
        float fall = floorY() - actor.getTranslationY();
        float x = Math.max(0, Math.min(maxX(), actor.getTranslationX()));
        Runnable landed = () -> {
            dragging = false;
            if (!active) return;
            if (sleeping) { body.setImageResource(R.drawable.tanecik_uyuyor); return; }
            body.setImageResource(R.drawable.tanecik_sevinc);
            handler.postDelayed(this::resumeIdle, 900);
        };
        if (!motionAllowed() || fall < dp(4)) {
            actor.setTranslationX(x);
            actor.setTranslationY(floorY());
            landed.run();
            return;
        }
        long duration = (long) Math.min(900, 250 + fall / activity.getResources().getDisplayMetrics().density * 1.6);
        actor.animate().translationX(x).translationY(floorY()).setDuration(duration)
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
