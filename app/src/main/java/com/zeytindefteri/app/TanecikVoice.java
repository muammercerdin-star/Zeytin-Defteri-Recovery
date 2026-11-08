package com.zeytindefteri.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * Tanecik'in sesi ve büyük sarı altyazısı (tur ve gösterimler için).
 *
 * Telefonun Türkçe sesi konuşurken o an söylenen kelime öbeği ekranın ortasında büyük harflerle belirir
 * (reels tarzı). Android 8+ "hangi kelimeyi söylüyorum" bildirimiyle tam eşzamanlı; daha eski telefonda ya da
 * Türkçe ses yoksa öbekler okuma hızına göre sırayla gösterilir. İnternet yok, ücret yok.
 */
final class TanecikVoice {
    /** Biraz hızlı: tanıtım tempolu olsun ama anlaşılır kalsın. */
    static final float RATE = 1.12f;

    private final Activity activity;
    private final TextView caption;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean ready, failed, speaking;
    private List<TanecikCaptions.Chunk> chunks;
    private int shown = -1;
    private Runnable pendingDone;
    private String utterance = "";

    TanecikVoice(Activity activity, FrameLayout layer) {
        this.activity = activity;
        caption = new TextView(activity);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        caption.setTypeface(Typeface.DEFAULT_BOLD);
        caption.setTextColor(0xFFFFD54A);
        caption.setGravity(Gravity.CENTER);
        caption.setShadowLayer(14f, 0f, 3f, Color.BLACK);
        caption.setAllCaps(false);
        caption.setVisibility(View.GONE);
        caption.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int pad = Math.round(16 * activity.getResources().getDisplayMetrics().density);
        caption.setPadding(pad, 0, pad, 0);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        layer.addView(caption, lp);
        layer.post(() -> caption.setTranslationY(layer.getHeight() * 0.34f));
    }

    boolean speaking() { return speaking; }

    /** Hazırlar; hazır olunca (ya da Türkçe ses yoksa) then çalışır. */
    void prepare(Runnable then) {
        if (tts != null || failed) { if (then != null) then.run(); return; }
        tts = new TextToSpeech(activity.getApplicationContext(), st -> handler.post(() -> {
            int lang = TextToSpeech.LANG_NOT_SUPPORTED;
            try { if (st == TextToSpeech.SUCCESS) lang = tts.setLanguage(new Locale("tr", "TR")); }
            catch (RuntimeException ignored) { }
            ready = st == TextToSpeech.SUCCESS && lang >= 0;
            failed = !ready;
            if (ready) {
                try { tts.setSpeechRate(RATE); } catch (RuntimeException ignored) { }
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { }
                    @Override public void onDone(String id) { handler.post(() -> finished(id)); }
                    @Override public void onError(String id) { handler.post(() -> finished(id)); }
                    @Override public void onRangeStart(String id, int start, int end, int frame) {
                        handler.post(() -> { if (id.equals(utterance)) showAt(start); });
                    }
                });
            }
            if (then != null) then.run();
        }));
    }

    /** Söyler ve altyazısını gösterir; bitince done (durdurulursa çağrılmaz). */
    void say(String text, Runnable done) {
        stop();
        String spoken = TanecikCaptions.spoken(text);
        chunks = TanecikCaptions.chunks(spoken);
        shown = -1;
        pendingDone = done;
        speaking = true;
        utterance = "tanecik-" + System.nanoTime();
        boolean voiced = ready && tts != null
                && tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, utterance) != TextToSpeech.ERROR;
        boolean exact = voiced && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
        if (!exact) {
            // Kelime bildirimi yok: öbekleri okuma hızına göre sırayla göster.
            final String id = utterance;
            long at = 0;
            for (int i = 0; i < chunks.size(); i++) {
                final int k = i;
                handler.postDelayed(() -> { if (id.equals(utterance)) showChunk(k); }, at);
                at += TanecikCaptions.chunkMs(chunks.get(i).text, voiced ? RATE : 1f);
            }
            if (!voiced) handler.postDelayed(() -> finished(id), at + 250);
        } else {
            showChunk(0);
        }
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);
        utterance = "";
        pendingDone = null;
        speaking = false;
        if (tts != null) { try { tts.stop(); } catch (RuntimeException ignored) { } }
        caption.setVisibility(View.GONE);
    }

    void release() {
        stop();
        if (tts != null) { try { tts.shutdown(); } catch (RuntimeException ignored) { } tts = null; }
    }

    private void finished(String id) {
        if (!id.equals(utterance)) return;
        speaking = false;
        utterance = "";
        handler.postDelayed(() -> caption.setVisibility(View.GONE), 250);
        Runnable done = pendingDone;
        pendingDone = null;
        if (done != null) done.run();
    }

    private void showAt(int offset) {
        if (chunks == null) return;
        showChunk(TanecikCaptions.indexAt(chunks, offset));
    }

    private void showChunk(int i) {
        if (chunks == null || i < 0 || i >= chunks.size() || i == shown) return;
        shown = i;
        caption.setText(chunks.get(i).text);
        caption.setVisibility(View.VISIBLE);
        caption.setScaleX(1.18f);
        caption.setScaleY(1.18f);
        caption.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
    }
}
