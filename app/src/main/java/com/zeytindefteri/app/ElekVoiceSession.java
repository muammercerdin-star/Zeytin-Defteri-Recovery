package com.zeytindefteri.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Elek formunda sesli konuşma penceresi: asistan konuşur ("Diğer kiloları bekliyorum"), dinler, kiloları
 * forma yazar; "bitti" denince toplamı okur, "evet" denince kaydeder ve makbuzu açar.
 * Konuşma kuralları ElekVoiceDialog'da (saf, testli); burada yalnız mikrofon, ses ve ekran.
 * Ücretsiz: telefonun kendi ses tanıması ve Türkçe sesi kullanılır.
 */
final class ElekVoiceSession {
    interface Host {
        double[] kg();
        double[] prices();
        boolean soldOnSave();
        void fill(double[] kg, Boolean sold, String buyer);
        /** Formu kaydeder; kaydedilen elek kaydının id'si ya da -1. */
        long save();
        void openReceipt(long recordId);
    }

    static final int MIC_REQUEST = 7301;
    private final Activity a;
    private final Host host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private android.view.View overlay;   // ekranın kendi içinde yüzen küre (ayrı pencere değil: bazı telefonlarda görünmüyordu)
    private TextToSpeech tts;
    private boolean ttsReady, ttsFailed;
    private String pendingSpeech; private boolean pendingListen;
    private SpeechRecognizer recognizer;
    private ElekVoiceDialog logic;
    private TextView status;
    private ElekVoiceWaveView orb;   // asistanın ses dalgası (çerçevesiz)
    private int silent = 0;
    private Runnable afterSpeech;
    private boolean closed;

    private final android.view.ViewGroup button;   // "Sesle doldur" düğmesi: oturum boyunca ses dalgasına dönüşür

    ElekVoiceSession(Activity activity, Host host, android.view.ViewGroup button) { this.a = activity; this.host = host; this.button = button; }

    /** Düğmeye basılınca. İzin yoksa ister; izin gelince Activity yeniden start() çağırır. */
    void start() {
        if (a.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_REQUEST);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(a)) {
            Toast.makeText(a, "Bu telefonda ses tanıma bulunamadı. Kiloları elle yazabilirsin.", Toast.LENGTH_LONG).show();
            return;
        }
        close();   // açık bir oturum varsa (düğmeye tekrar basıldı) önce kapat
        closed = false; silent = 0;
        logic = new ElekVoiceDialog(host.kg(), host.prices(), host.soldOnSave());
        buildDialog();
        tts = new TextToSpeech(a.getApplicationContext(), st -> main.post(() -> {
            int lang = TextToSpeech.LANG_NOT_SUPPORTED;
            try { if (st == TextToSpeech.SUCCESS) lang = tts.setLanguage(new Locale("tr", "TR")); } catch (Exception ignored) { }
            ttsReady = st == TextToSpeech.SUCCESS && lang >= 0;
            ttsFailed = !ttsReady;
            if (ttsReady) {
                pickBestVoice();
                tts.setSpeechRate(0.95f);
                tts.setPitch(1.02f);
            }
            if (ttsReady) tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { main.post(ElekVoiceSession.this::spoken); }
                @Override public void onError(String id) { main.post(ElekVoiceSession.this::spoken); }
            });
            if (pendingSpeech != null) { String s = pendingSpeech; pendingSpeech = null; say(s, pendingListen); }
        }));
        say(ElekVoiceDialog.greeting(), true);
    }

    /** Telefondaki Türkçe seslerden en kalitelisini seçer (yüklü olan; eşitlikte internetsiz olan). */
    private void pickBestVoice() {
        try {
            android.speech.tts.Voice best = null; int bestScore = Integer.MIN_VALUE;
            java.util.Set<android.speech.tts.Voice> voices = tts.getVoices();
            if (voices == null) return;
            for (android.speech.tts.Voice v : voices) {
                if (v == null || v.getLocale() == null || !"tr".equals(v.getLocale().getLanguage())) continue;
                java.util.Set<String> f = v.getFeatures();
                if (f != null && f.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) continue;
                int score = v.getQuality() * 10 - v.getLatency() - (v.isNetworkConnectionRequired() ? 3 : 0);
                if (score > bestScore) { bestScore = score; best = v; }
            }
            if (best != null) tts.setVoice(best);
        } catch (RuntimeException ignored) { }
    }

    void onPermission(boolean granted) {
        if (granted) start();
        else Toast.makeText(a, "Sesle doldurmak için mikrofon izni gerekli. Kiloları elle de yazabilirsin.", Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------ konuş / dinle
    private void say(String text, boolean listenAfter) { say(text, listenAfter, null); }

    private void say(String text, boolean listenAfter, Runnable then) {
        if (closed) return;
        stopListening();
        orb.mode(ElekVoiceWaveView.Mode.SPEAKING);
        status.setText("");
        orb.setContentDescription("Asistan: " + text);
        if (!ttsReady) status.setText(text.length() > 70 ? text.substring(0, 68) + "…" : text);   // Türkçe ses yoksa yazıyla
        afterSpeech = () -> { if (then != null) then.run(); else if (listenAfter) listen(); else idle("Devam etmek için küreye dokun"); };
        if (ttsReady) {
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "elek-" + System.nanoTime()) == TextToSpeech.ERROR) spoken();
        } else if (!ttsFailed && tts != null) {
            pendingSpeech = text; pendingListen = listenAfter; afterSpeech = then != null ? () -> then.run() : afterSpeech;
        } else {
            main.postDelayed(this::spoken, Math.min(3500, 600 + text.length() * 45L));   // ses yoksa okunacak kadar bekle
        }
    }

    private void spoken() {
        if (closed) return;
        Runnable r = afterSpeech; afterSpeech = null;
        if (r != null) r.run();
    }

    private void listen() {
        if (closed) return;
        stopListening();
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(a);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle b) { orb.mode(ElekVoiceWaveView.Mode.LISTENING); status.setText("Dinliyorum"); }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float v) { orb.rms(v); }
                @Override public void onBufferReceived(byte[] b) { }
                @Override public void onEndOfSpeech() { orb.mode(ElekVoiceWaveView.Mode.THINKING); status.setText(""); }
                @Override public void onError(int e) { main.post(() -> heardNothing(e)); }
                @Override public void onResults(Bundle b) { main.post(() -> heardText(first(b))); }
                @Override public void onPartialResults(Bundle b) { }
                @Override public void onEvent(int t, Bundle b) { }
            });
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR");
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            recognizer.startListening(i);
        } catch (RuntimeException e) {
            idle("Mikrofon açılamadı, küreye dokun");
        }
    }

    private static String first(Bundle b) {
        ArrayList<String> r = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return r == null || r.isEmpty() || r.get(0) == null ? "" : r.get(0).trim();
    }

    private void heardText(String text) {
        if (closed) return;
        stopListening();
        if (text.isEmpty()) { heardNothing(SpeechRecognizer.ERROR_NO_MATCH); return; }
        silent = 0;
        ElekVoiceDialog.Reply r = logic.handle(text);
        if (r.action == ElekVoiceDialog.Action.FILL || r.action == ElekVoiceDialog.Action.CANCEL || r.action == ElekVoiceDialog.Action.SAVE) {
            host.fill(logic.kg.clone(), logic.sold, logic.buyer);
        }
        if (r.action == ElekVoiceDialog.Action.SAVE) {
            long id = host.save();
            if (id > 0) {
                String done = logic.isSold() ? "Kaydettim. Fiş hazır." : "Kaydettim.";
                say(r.speech + " " + done, false, () -> { close(); host.openReceipt(id); });
            } else {
                say("Kaydedemedim. Formdaki uyarıya bak, düzeltip yeniden dene.", false, this::close);
            }
            return;
        }
        if (r.action == ElekVoiceDialog.Action.CANCEL) { say(r.speech, false, this::close); return; }
        say(r.speech, r.listen);
    }

    private void heardNothing(int error) {
        if (closed) return;
        stopListening();
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { idle("Mikrofon izni yok"); return; }
        if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT || error == SpeechRecognizer.ERROR_SERVER) {
            say("Ses tanıma için internet gerekiyor olabilir. Bağlantını kontrol edip mikrofona dokun.", false);
            return;
        }
        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
            main.postDelayed(this::listen, 600);
            return;
        }
        silent++;
        if (silent <= 2) say(silent == 1 ? "Bekliyorum. Kilolarını söyleyebilirsin." : "Hâlâ buradayım. Bitirdiysen bitti de.", true);
        else idle("Devam etmek için küreye dokun");
    }

    private void stopListening() {
        if (recognizer != null) {
            try { recognizer.cancel(); recognizer.destroy(); } catch (RuntimeException ignored) { }
            recognizer = null;
        }
    }

    void close() {
        if (closed) return;
        closed = true;
        stopListening();
        if (tts != null) { try { tts.stop(); tts.shutdown(); } catch (RuntimeException ignored) { } tts = null; }
        ttsReady = false;
        if (overlay != null && overlay.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) overlay.getParent()).removeView(overlay);
        overlay = null;
        // Düğme eski hâline döner: küçük küre, "Sesle doldur", "Söyle, asistan yazsın".
        for (int i = 0; i < button.getChildCount(); i++) button.getChildAt(i).setVisibility(android.view.View.VISIBLE);
        button.setContentDescription("Sesle doldur: kiloları söyle, asistan forma yazsın");
        a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // ------------------------------------------------------------ pencere: yüzen küre
    private void idle(String hint) {
        if (orb != null) orb.mode(ElekVoiceWaveView.Mode.IDLE);
        if (status != null) status.setText(hint);
    }

    private void buildDialog() {
        // Düğmenin kendi içeriği gizlenir, yerine ses dalgası + durum + kapat gelir (formun üstünde hiçbir şey yüzmez).
        for (int i = 0; i < button.getChildCount(); i++) button.getChildAt(i).setVisibility(android.view.View.GONE);
        LinearLayout row = new LinearLayout(a); row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout col = new LinearLayout(a); col.setOrientation(LinearLayout.VERTICAL); col.setGravity(Gravity.CENTER_HORIZONTAL);
        orb = new ElekVoiceWaveView(a).onGreen();
        orb.setClickable(true); orb.setFocusable(true);
        orb.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        orb.setContentDescription("Sesli elek asistanı. Tekrar dinlemesi için dokun.");
        orb.setOnClickListener(v -> { if (recognizer == null && afterSpeech == null) { silent = 0; listen(); } });
        col.addView(orb, new LinearLayout.LayoutParams(-1, dp(38)));
        status = label("", 12, 0xE6FFFFFF, true); status.setGravity(Gravity.CENTER); status.setSingleLine(true);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(status, new LinearLayout.LayoutParams(-1, -2));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        android.widget.ImageView close = new android.widget.ImageView(a);
        close.setImageResource(R.drawable.ic_op_close); close.setColorFilter(0xFFFFFFFF);
        close.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable cb = new GradientDrawable(); cb.setShape(GradientDrawable.OVAL); cb.setColor(0x33000000);
        close.setBackground(cb); close.setClickable(true); close.setFocusable(true);
        close.setContentDescription("Sesli elek asistanını kapat");
        close.setOnClickListener(v -> close());
        LinearLayout.LayoutParams xp = new LinearLayout.LayoutParams(dp(36), dp(36)); xp.leftMargin = dp(8);
        row.addView(close, xp);
        overlay = row;
        button.addView(row, new LinearLayout.LayoutParams(-1, -1));
        button.setContentDescription("Sesli elek asistanı açık");
        a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);   // elek başında ekran kararmasın
    }


    private TextView label(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(a); t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int fill, int stroke, int r) {
        GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(r)); g.setStroke(dp(1), stroke); return g;
    }

    private int dp(int v) { return Math.round(v * a.getResources().getDisplayMetrics().density); }
}
