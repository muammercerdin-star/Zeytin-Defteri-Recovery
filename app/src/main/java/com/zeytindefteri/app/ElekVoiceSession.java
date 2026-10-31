package com.zeytindefteri.app;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
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
    private Dialog dialog;
    private TextToSpeech tts;
    private boolean ttsReady, ttsFailed;
    private String pendingSpeech; private boolean pendingListen;
    private SpeechRecognizer recognizer;
    private ElekVoiceDialog logic;
    private TextView status, heard, reply, micButton;
    private AssistantVoiceWaveView wave;
    private LinearLayout log;
    private int silent = 0;
    private Runnable afterSpeech;
    private boolean closed;

    ElekVoiceSession(Activity activity, Host host) { this.a = activity; this.host = host; }

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
        closed = false; silent = 0;
        logic = new ElekVoiceDialog(host.kg(), host.prices(), host.soldOnSave());
        buildDialog();
        tts = new TextToSpeech(a.getApplicationContext(), st -> main.post(() -> {
            int lang = TextToSpeech.LANG_NOT_SUPPORTED;
            try { if (st == TextToSpeech.SUCCESS) lang = tts.setLanguage(new Locale("tr", "TR")); } catch (Exception ignored) { }
            ttsReady = st == TextToSpeech.SUCCESS && lang >= 0;
            ttsFailed = !ttsReady;
            if (ttsReady) tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { main.post(ElekVoiceSession.this::spoken); }
                @Override public void onError(String id) { main.post(ElekVoiceSession.this::spoken); }
            });
            if (pendingSpeech != null) { String s = pendingSpeech; pendingSpeech = null; say(s, pendingListen); }
        }));
        say(ElekVoiceDialog.greeting(), true);
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
        reply.setText(text);
        status.setText("Konuşuyor…");
        wave.listening(false);
        afterSpeech = () -> { if (then != null) then.run(); else if (listenAfter) listen(); else status.setText("Mikrofona dokunarak devam edebilirsin."); };
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
                @Override public void onReadyForSpeech(Bundle b) { status.setText("Dinliyorum…"); wave.listening(true); }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float v) { wave.rms(v); }
                @Override public void onBufferReceived(byte[] b) { }
                @Override public void onEndOfSpeech() { status.setText("Anlıyorum…"); wave.listening(false); }
                @Override public void onError(int e) { main.post(() -> heardNothing(e)); }
                @Override public void onResults(Bundle b) { main.post(() -> heardText(first(b))); }
                @Override public void onPartialResults(Bundle b) { String p = first(b); if (!p.isEmpty()) heard.setText("“" + p + "”"); }
                @Override public void onEvent(int t, Bundle b) { }
            });
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR");
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            recognizer.startListening(i);
            micButton.setText("■  Dinlemeyi durdur");
        } catch (RuntimeException e) {
            status.setText("Mikrofon açılamadı. Tekrar dene.");
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
        heard.setText("“" + text + "”");
        addLog("Sen", text);
        ElekVoiceDialog.Reply r = logic.handle(text);
        if (r.action == ElekVoiceDialog.Action.FILL || r.action == ElekVoiceDialog.Action.CANCEL || r.action == ElekVoiceDialog.Action.SAVE) {
            host.fill(logic.kg.clone(), logic.sold, logic.buyer);
        }
        addLog("Asistan", r.speech);
        if (r.action == ElekVoiceDialog.Action.SAVE) {
            long id = host.save();
            if (id > 0) {
                String done = logic.isSold() ? "Kaydettim. Fiş hazır." : "Kaydettim.";
                addLog("Asistan", done);
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
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { status.setText("Mikrofon izni yok."); return; }
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
        else { status.setText("Sessiz kaldın, dinlemeyi durdurdum. Devam etmek için mikrofona dokun."); micButton.setText("🎙  Tekrar dinle"); }
    }

    private void stopListening() {
        if (recognizer != null) {
            try { recognizer.cancel(); recognizer.destroy(); } catch (RuntimeException ignored) { }
            recognizer = null;
        }
        if (wave != null) wave.listening(false);
        if (micButton != null) micButton.setText("🎙  Tekrar dinle");
    }

    void close() {
        if (closed) return;
        closed = true;
        stopListening();
        if (tts != null) { try { tts.stop(); tts.shutdown(); } catch (RuntimeException ignored) { } tts = null; }
        ttsReady = false;
        if (dialog != null && dialog.isShowing()) { try { dialog.dismiss(); } catch (RuntimeException ignored) { } }
    }

    // ------------------------------------------------------------ pencere
    private void buildDialog() {
        int card = GreenTheme.color(R.color.zd_card), border = GreenTheme.color(R.color.zd_border);
        int text = GreenTheme.color(R.color.zd_text), muted = GreenTheme.color(R.color.zd_muted), green = GreenTheme.color(R.color.zd_green);
        LinearLayout root = new LinearLayout(a); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(18));
        root.setBackground(round(card, border, 24));

        TextView title = label("🎙  Sesli elek", 18, text, true); root.addView(title);
        status = label("Hazırlanıyor…", 13, muted, false); status.setPadding(0, dp(2), 0, 0); root.addView(status);
        wave = new AssistantVoiceWaveView(a);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-1, dp(44)); wp.topMargin = dp(10); root.addView(wave, wp);
        heard = label("", 17, text, true); heard.setGravity(Gravity.CENTER); heard.setPadding(0, dp(6), 0, 0); root.addView(heard);
        reply = label("", 16, green, true); reply.setPadding(dp(14), dp(12), dp(14), dp(12));
        reply.setBackground(round(GreenTheme.color(R.color.zd_green_soft), GreenTheme.color(R.color.zd_green_soft), 16));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2); rp.topMargin = dp(12); root.addView(reply, rp);

        ScrollView sv = new ScrollView(a);
        log = new LinearLayout(a); log.setOrientation(LinearLayout.VERTICAL); sv.addView(log);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(110)); lp.topMargin = dp(10); root.addView(sv, lp);

        TextView hint = label("“3 numara 120 kilo” · “geri al” · “oku” · “Ahmet'e sattım” · “bitti”", 12, muted, false);
        hint.setPadding(0, dp(8), 0, 0); root.addView(hint);

        LinearLayout row = new LinearLayout(a); row.setGravity(Gravity.CENTER);
        micButton = button("🎙  Tekrar dinle", green, card);
        micButton.setOnClickListener(v -> { if (recognizer != null) stopListening(); else { silent = 0; listen(); } });
        TextView done = button("Bitti", text, card);
        done.setOnClickListener(v -> heardText("bitti"));
        TextView cancel = button("Kapat", muted, card);
        cancel.setOnClickListener(v -> close());
        row.addView(micButton, new LinearLayout.LayoutParams(0, dp(48), 1.4f));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(48), 1f); bp.leftMargin = dp(8);
        row.addView(done, bp);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(48), 1f); cp.leftMargin = dp(8);
        row.addView(cancel, cp);
        LinearLayout.LayoutParams rowp = new LinearLayout.LayoutParams(-1, -2); rowp.topMargin = dp(12); root.addView(row, rowp);

        dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(d -> close());
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);   // elek başında ekran kararmasın
        }
        dialog.show();
    }

    private void addLog(String who, String line) {
        TextView t = label(who + ": " + line, 13, GreenTheme.color("Sen".equals(who) ? R.color.zd_text : R.color.zd_muted), "Sen".equals(who));
        t.setPadding(0, dp(3), 0, dp(3));
        log.addView(t);
        if (log.getParent() instanceof ScrollView) ((ScrollView) log.getParent()).post(() -> ((ScrollView) log.getParent()).fullScroll(ScrollView.FOCUS_DOWN));
    }

    private TextView label(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(a); t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView button(String s, int color, int bg) {
        TextView b = label(s, 14, color, true); b.setGravity(Gravity.CENTER);
        b.setBackground(round(bg, GreenTheme.color(R.color.zd_border), 16));
        b.setClickable(true); b.setFocusable(true); b.setContentDescription(s.replace("🎙", "").trim());
        return b;
    }

    private GradientDrawable round(int fill, int stroke, int r) {
        GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(r)); g.setStroke(dp(1), stroke); return g;
    }

    private int dp(int v) { return Math.round(v * a.getResources().getDisplayMetrics().density); }
}
