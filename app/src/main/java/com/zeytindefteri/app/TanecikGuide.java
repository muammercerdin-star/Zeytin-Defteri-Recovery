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
 *
 * Doldurma kipi (İP7, ActionRunner): aynı uçma ve harf harf yazma; ama yazdığını SİLMEZ, ekranı KAPATMAZ, Kaydet'e
 * yine BASMAZ; bitince onDone. Adım türü "@tap" ise hedefi dokunmak için doldurucuya verir (tapper; turda yok, Tanecik
 * kendisi hiçbir düğmeye basmaz). Hızlı modda uçmaz,
 * anında yazar ve alan alan konuşmaz. Gece (22:00–05:00) Tanecik konuşmaz.
 *
 * Sahne kipi (yönlendiren asistan): TanecikScenes'teki bir sahneyi oynatır. Ne yaptığını söyler, adımı gösterir
 * ("Adım 3 / 4"), hedefi bir kaplamayla parlatır (ekranın iskeleti değişmez), büyük "Dur" düğmesi hep vardır. Sahne
 * oynatılmadan önce SceneCheck'ten geçer; Kaydet / Sil / ödeme düğmelerine dokunmaz. Bitince ekran açık kalır; yarıda
 * kalırsa yazdığını siler (iz bırakmaz). Gece konuşmaz, yalnız ekranda gösterir; hızlı modda animasyonsuz oynar.
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
    /** Doldurma kipi: bitince çağrılır (null = tur kipi). */
    private final Runnable onDone;
    /** Doldurma kipi: çiftçi "Durdur" dedi. */
    private final Runnable onStop;
    private final boolean fast;
    /** Gece sessiz: doldurma kipinde (İP7) ve sahne kipinde (scene() ayarlar); turda bugünkü gibi değil. */
    private boolean quiet;
    /** Doldurma kipi: "@tap" adımında hedefi kime vereceği (izinli düğmeler orada seçilir); turda null. */
    private final java.util.function.Consumer<View> tapper;
    /** Sahne kipi: oynatılan sahne (diğer kiplerde null), adım yazısı ve parlatma kaplaması. */
    private TanecikScenes.Scene scene;
    private TextView progress;
    /** Sahne kipi: Tanecik'in o an söylediği söz, büyük yazıyla (gece yalnız bu görünür; ses yok). */
    private TextView line;
    private View glow;
    /** Sahne kipi: bitti (true: sonuna kadar izlendi, false: Dur). */
    private java.util.function.Consumer<Boolean> onSceneEnd;

    TanecikGuide(Activity activity, List<TanecikTour.GuideStep> steps) {
        this(activity, steps, false, null, null, null);
    }

    /**
     * Sahne kipi. Sahne SceneCheck'ten geçmiyorsa ya da ZARARSIZ İŞ izinsizse null döner (oynatılmaz). answer: sahnede
     * okunacak bugünkü cevap ({cevap} yerine). onEnd: sahne bitince (true) ya da Dur denince (false).
     */
    static TanecikGuide scene(Activity activity, TanecikScenes.Scene scene, String answer, boolean permitted, boolean fast,
                              java.util.function.Consumer<Boolean> onEnd) {
        if (!SceneCheck.allowed(scene, permitted)) {
            return null;
        }
        List<TanecikTour.GuideStep> steps = new ArrayList<>();
        boolean opened = false;
        for (TanecikScenes.Step st : scene.steps) {
            if (st.kind == TanecikScenes.Kind.OPEN) {
                opened = true;
                continue;
            }
            if (!opened || st.kind == TanecikScenes.Kind.STOP) {
                continue;   // açılıştan önceki söz asistan panelinde söylenir; STOP sahnenin sonu
            }
            String text = st.text.replace(TanecikScenes.ANSWER, answer == null || answer.isEmpty() ? scene.title : answer);
            steps.add(new TanecikTour.GuideStep(st.target, text, sceneType(st)));
        }
        TanecikGuide g = new TanecikGuide(activity, steps, fast, null, null, null);
        g.scene = scene;
        g.onSceneEnd = onEnd;
        g.quiet = !SceneCheck.speaks(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY));
        g.sceneLayer();
        // Ekran kapanınca (sahne yarıda kalsa da) Tanecik'in sesi bırakılır.
        g.layer.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { }
            @Override public void onViewDetachedFromWindow(View v) { g.release(); }
        });
        return g;
    }

    private static String sceneType(TanecikScenes.Step st) {
        switch (st.kind) {
            case TYPE: return st.value;
            case SELECT: return "@tap";
            case HIGHLIGHT: return "@glow";
            default: return "";
        }
    }

    /** Sahne kipinin büyük "Dur" düğmesi ve adım yazısı (yaşlı kullanıcı: en az 60 dp, 19 sp). */
    private void sceneLayer() {
        TextView stop = (TextView) layer.getChildAt(layer.getChildCount() - 1);
        stop.setText("■ Dur");
        stop.setTextSize(TypedValue.COMPLEX_UNIT_SP, GuideSettings.BUTTON_SP);
        stop.setTextColor(0xFFFFFFFF);
        stop.setMinHeight(dp(GuideSettings.BUTTON_DP));
        stop.setGravity(Gravity.CENTER);
        stop.setPadding(dp(24), dp(8), dp(24), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF8A3B12);
        bg.setCornerRadius(dp(30));
        stop.setBackground(bg);
        stop.setContentDescription("Dur: Tanecik göstermeyi bıraksın");
        progress = new TextView(activity);
        progress.setTextSize(TypedValue.COMPLEX_UNIT_SP, GuideSettings.REASON_SP);
        progress.setTypeface(Typeface.DEFAULT_BOLD);
        progress.setTextColor(0xFF24341A);
        progress.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable pb = new GradientDrawable();
        pb.setColor(0xE6EEF3E8);
        pb.setCornerRadius(dp(16));
        progress.setBackground(pb);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        lp.bottomMargin = dp(30);
        lp.leftMargin = dp(14);
        layer.addView(progress, lp);
        line = new TextView(activity);
        line.setTextSize(TypedValue.COMPLEX_UNIT_SP, GuideSettings.BUTTON_SP);
        line.setTypeface(Typeface.DEFAULT_BOLD);
        line.setTextColor(0xFF1F2414);
        line.setPadding(dp(16), dp(12), dp(16), dp(12));
        line.setVisibility(View.GONE);
        line.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        GradientDrawable lb = new GradientDrawable();
        lb.setColor(0xF2FFFFFF);
        lb.setCornerRadius(dp(18));
        line.setBackground(lb);
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        ll.bottomMargin = dp(GuideSettings.BUTTON_DP + 48);
        ll.leftMargin = dp(14);
        ll.rightMargin = dp(14);
        layer.addView(line, ll);
    }

    /** Doldurma kipi (onDone dolu) ya da tur kipi (onDone null). fast: animasyonsuz, alan alan konuşmadan. */
    TanecikGuide(Activity activity, List<TanecikTour.GuideStep> steps, boolean fast, Runnable onDone, Runnable onStop,
                 java.util.function.Consumer<View> tapper) {
        this.activity = activity;
        this.tapper = tapper;
        this.steps = steps;
        this.fast = fast;
        this.onDone = onDone;
        this.onStop = onStop;
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        this.quiet = onDone != null && TanecikLines.sleeping(hour);
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
        voice = new TanecikVoice(activity, layer).fast();

        TextView stop = new TextView(activity);
        stop.setText(onDone == null ? "✕ Turu bitir" : "✕ Durdur");
        stop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        stop.setTypeface(Typeface.DEFAULT_BOLD);
        stop.setTextColor(0xFF24341A);
        stop.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE6EEF3E8);
        bg.setCornerRadius(dp(20));
        stop.setBackground(bg);
        stop.setOnClickListener(v -> stopPressed());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        lp.bottomMargin = dp(18);
        lp.rightMargin = dp(14);
        layer.addView(stop, lp);
    }

    private void stopPressed() {
        if (scene != null) {
            endScene(false);
            return;
        }
        if (onDone == null) {
            TanecikTour.cancelRequested = true;
            finish();
            return;
        }
        done = true;
        handler.removeCallbacksAndMessages(null);
        voice.stop();
        removeLayer();
        if (onStop != null) {
            onStop.run();
        }
    }

    private void removeLayer() {
        if (layer.getParent() instanceof ViewGroup) {
            ((ViewGroup) layer.getParent()).removeView(layer);
        }
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
        if (i >= steps.size()) { handler.postDelayed(this::finish, 300); return; }
        TanecikTour.GuideStep st = steps.get(i);
        if (progress != null) progress.setText("Adım " + (i + 1) + " / " + steps.size());
        if (scene != null && st.view.isEmpty()) {
            // Sahnede hedefsiz söz: Tanecik yerinde durup söyler (gece yalnız altyazı).
            sceneSay(st.text, () -> handler.postDelayed(() -> step(i + 1), 120));
            return;
        }
        View v = target(st.view);
        if (v == null || !v.isShown()) {
            if (scene != null) {
                // Sahnede hedef henüz yoksa (veri yükleniyor): söz yine söylenir, cevap kaybolmaz.
                sceneSay(st.text, () -> handler.postDelayed(() -> step(i + 1), 120));
                return;
            }
            step(i + 1);
            return;
        }
        if (fast) {
            fastStep(v, st, i);
            return;
        }
        ScrollView sv = scrollerOf(v);
        if (sv != null) {
            int top = offsetIn(v, sv);
            sv.smoothScrollTo(0, Math.max(0, top - Math.round(sv.getHeight() * 0.38f)));
        }
        handler.postDelayed(() -> flyTo(v, () -> pointAndSay(v, st, i)), sv != null ? 350 : 40);
    }

    private void pointAndSay(View v, TanecikTour.GuideStep st, int i) {
        if (done) return;
        body.setImageResource(R.drawable.tanecik_gosteriyor);
        v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(160)
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()).start();
        Runnable next = () -> handler.postDelayed(() -> step(i + 1), 120);
        Runnable say = quiet ? next : () -> voice.say(st.text, next);
        if ("@tap".equals(st.type)) {
            tap(v);
            say.run();
            return;
        }
        if ("@glow".equals(st.type)) {
            glowOn(v);
            sceneSay(st.text, next);
            return;
        }
        if (!st.type.isEmpty() && v instanceof EditText) type((EditText) v, st.type, 0, say);
        else say.run();
    }

    private void tap(View v) {
        if (tapper != null) {
            tapper.accept(v);
        }
    }

    /** Hızlı mod: uçmadan, anında yazar ya da dokunur; alan alan konuşmaz. Sahnede parlatır ve altyazıyı gösterir. */
    private void fastStep(View v, TanecikTour.GuideStep st, int i) {
        if ("@glow".equals(st.type)) {
            glowOn(v);
            sceneSay(st.text, () -> handler.post(() -> step(i + 1)));
            return;
        }
        if ("@tap".equals(st.type)) {
            tap(v);
        } else if (!st.type.isEmpty() && v instanceof EditText) {
            ((EditText) v).setText(st.type);
        }
        handler.post(() -> step(i + 1));
    }

    /** Hedef: "tur:" / "sahne:" ile başlıyorsa etiket (programla kurulan ekranlar), değilse görünüm adı (R.id). */
    private View target(String name) {
        if (name.startsWith("tur:") || name.startsWith("sahne:")) return activity.getWindow().getDecorView().findViewWithTag(name);
        int id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
        return id == 0 ? null : activity.findViewById(id);
    }

    /** Örnek değeri harf harf yazar (klavye açılmaz). */
    private void type(EditText e, String text, int n, Runnable then) {
        if (done) return;
        if (n == 0 && !typed.contains(e)) typed.add(e);
        e.setText(text.substring(0, n));
        if (n >= text.length()) { then.run(); return; }
        handler.postDelayed(() -> type(e, text, n + 1, then), 110);
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
        body.animate().translationX(x).translationY(y).setDuration(380).withEndAction(() -> {
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

    /** Sahnede söz: büyük yazıyla gösterilir; gündüz okunur, gece yalnız yazı (okuma süresi kadar bekler). */
    private void sceneSay(String text, Runnable next) {
        if (line != null) {
            line.setText(text);
            line.setVisibility(View.VISIBLE);
        }
        if (quiet) {
            handler.postDelayed(next, TanecikTour.readMs(text));
            return;
        }
        voice.say(text, next);
    }

    /** Parlatma: hedefin çevresine kalın bir çerçeve kaplaması (ekranın kendisine dokunulmaz). */
    private void glowOn(View v) {
        if (glow != null) layer.removeView(glow);
        int[] a = new int[2], b = new int[2];
        v.getLocationInWindow(a);
        layer.getLocationInWindow(b);
        glow = new View(activity);
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x22E0A33A);
        d.setStroke(dp(4), 0xFFE0A33A);
        d.setCornerRadius(dp(16));
        glow.setBackground(d);
        glow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(v.getWidth() + dp(12), v.getHeight() + dp(12));
        lp.leftMargin = a[0] - b[0] - dp(6);
        lp.topMargin = a[1] - b[1] - dp(6);
        layer.addView(glow, 0, lp);
    }

    /** Sahne bitti ya da durduruldu: kaplama kalkar, yazılan silinir, ekran açık kalır. */
    private void endScene(boolean completed) {
        if (done) return;
        done = true;
        handler.removeCallbacksAndMessages(null);
        voice.stop();
        for (EditText e : typed) e.setText("");
        removeLayer();
        if (onSceneEnd != null) onSceneEnd.accept(completed);
    }

    private void finish() {
        if (scene != null) {
            endScene(true);
            return;
        }
        if (done) return;
        if (onDone != null) {
            // Doldurma kipi: yazılanlar formda kalır, ekran açık kalır (kaydetmek çiftçinin onayıyla).
            done = true;
            handler.removeCallbacksAndMessages(null);
            removeLayer();
            onDone.run();
            return;
        }
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
