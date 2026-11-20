package com.zeytindefteri.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Görünür uygulayıcı (İP7). Çok adımlı satış cümlesini satış formunda GÖRÜNÜR doldurur: Tanecik alana uçar ve
 * harf harf yazar (TanecikGuide, doldurma kipi), eksik bilgiyi katalogdaki kısa soruyla sorar; sonunda özeti okur ve
 * "Kaydedeyim mi?" der (ses + büyük düğmeler). Tek onayla satış + alacak + ödeme hatırlatması yazılır; hepsi tek
 * "Geri al" ile birlikte geri alınır (RunnerFlow). Onaydan önce hiçbir şey yazılmaz: yarıda bırakılan akış iz bırakmaz.
 *
 * Ekranın iskeleti sabit: onay kartı formun üstüne biner, düğmelerin yerini değiştirmez.
 * Hızlı mod (Ayarlar): animasyonsuz doldurur; onay ve Geri al aynı. Gece (22:00–05:00) Tanecik konuşmaz.
 */
final class ActionRunner {
    static final String PREFS = "zd_runner";
    private static final String KEY_FAST = "fast";
    private static final String KEY_AUTO_REMINDER = "auto_payment_reminder";
    /** Onaydan sonra Geri al'ın ekranda kaldığı süre. */
    static final int UNDO_SECONDS = 20;

    /** Satış formunun ActionRunner'a açtığı yüz. */
    interface Form {
        /** Formu doğrulayıp satışı (alacağıyla) yazar: satış no; form eksikse 0 (hiçbir şey yazılmadı); hata -1. */
        long saveForRunner();

        /** Bu kayıtta yeni açılan alıcı (rehberde yoksa); yoksa 0. */
        long newBuyerId();

        long gardenId();

        /** Formdaki net satış tutarı (TL). */
        double netTotal();

        String buyerName();

        /** Formdaki satırlar: kilo, sınıf adı, fiyat (özet için). */
        double kilos();

        String gradeLabel();

        double price();

        void deleteBuyerIfUnused(long buyerId);

        String owner();

        /** Akış bitti (kaydedildi ya da vazgeçildi): ekranı kapatır. */
        void close(boolean saved);
    }

    // ------------------------------------------------------------ ayarlar

    static boolean fast(Context c) {
        return prefs(c).getBoolean(KEY_FAST, false);
    }

    static void setFast(Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_FAST, on).apply();
    }

    /** Çiftçi "evet, bunu hep yap" dedi mi (yalnız ödeme günü hatırlatması). */
    static boolean autoReminder(Context c) {
        return prefs(c).getBoolean(KEY_AUTO_REMINDER, false);
    }

    static void setAutoReminder(Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_AUTO_REMINDER, on).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------ akış

    private final Activity activity;
    private final Form form;
    private final ActionPlan plan;
    private final String dueIso;
    private final RunnerFlow flow;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final boolean quiet;
    private TanecikGuide guide;
    private TanecikVoice voice;
    private FrameLayout layer;
    private LinearLayout card;
    private boolean askAlways;
    /** Ödeme durumu: plan söylediyse o (UNPAID / PAID); söylenmediyse "" ve onaydan önce "Parasını aldın mı?" sorulur. */
    private String payment;

    ActionRunner(Activity activity, Form form, ActionPlan plan) {
        this.activity = activity;
        this.form = form;
        this.plan = plan;
        this.dueIso = plan.dueDate(Calendar.getInstance());
        this.flow = new RunnerFlow(plan, !dueIso.isEmpty());
        this.quiet = TanecikLines.sleeping(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
        this.payment = plan.payment;
    }

    RunnerFlow.State state() {
        return flow.state();
    }

    /** Doldurmaya başlar. habitPrice / habitGrade: alışkanlıktan (yalnız yüksek güven), yoksa 0 / "". */
    void start(double habitPrice, String habitGrade) {
        AssistantMeter.multiStep(activity, form.owner(), AssistantScorecard.MULTI_STARTED);
        String grade = plan.grade.isEmpty() ? habitGrade : plan.grade;
        double price = plan.price > 0 ? plan.price : habitPrice;
        List<TanecikTour.GuideStep> steps = new ArrayList<>();
        if (!plan.buyer.isEmpty()) {
            steps.add(new TanecikTour.GuideStep("etBuyerName", "Alıcı: " + plan.buyer + ".", plan.buyer));
        } else {
            steps.add(new TanecikTour.GuideStep("etBuyerName", "Kime sattın? Alıcıyı buraya seç.", ""));
        }
        String kgView = "etKg" + suffix(grade.isEmpty() ? "3" : grade);
        if (grade.isEmpty()) {
            steps.add(new TanecikTour.GuideStep(kgView, "Hangi numara? Kiloyu o numaranın satırına yaz.", ""));
        } else if (plan.kilos > 0) {
            steps.add(new TanecikTour.GuideStep(kgView, ElekVoiceDialog.num(plan.kilos) + " kilo, " + HabitModel.gradeLabel(grade) + ".",
                    TezgahQr.field(plan.kilos)));
        } else {
            steps.add(new TanecikTour.GuideStep(kgView, "Kaç kilo?", ""));
        }
        if (!grade.isEmpty()) {
            String priceView = "etPrice" + suffix(grade);
            if (price > 0) {
                String why = plan.price > 0 ? "" : " Geçen satışlardaki gibi.";
                steps.add(new TanecikTour.GuideStep(priceView, "Kilosu " + ElekVoiceDialog.num(price) + " lira." + why,
                        TezgahQr.field(price)));
            } else {
                steps.add(new TanecikTour.GuideStep(priceView, "Kilosu kaç liradan? Buraya yaz.", ""));
            }
        }
        if (ActionPlan.UNPAID.equals(payment)) {
            steps.add(new TanecikTour.GuideStep("chipPayNone", "Para alınmadı.", "@tap"));
        } else if (ActionPlan.PAID.equals(payment)) {
            steps.add(new TanecikTour.GuideStep("chipPayFull", "Para peşin alındı.", "@tap"));
        }
        if (!dueIso.isEmpty()) {
            steps.add(new TanecikTour.GuideStep("etDueDate", "Ödeme günü " + ActionPlan.dayText(dueIso) + ".", dotted(dueIso)));
        }
        guide = new TanecikGuide(activity, steps, fast(activity), this::filled, this::cancel, this::tap);
        guide.start();
    }

    /** Doldurucunun dokunabildiği düğmeler yalnız ödeme durumu: "Alınmadı" ya da "Tamamı" (peşin). Kaydet'e asla dokunulmaz. */
    private void tap(View v) {
        if (v == null) {
            return;
        }
        if (v.getId() == R.id.chipPayNone || v.getId() == R.id.chipPayFull) {
            v.performClick();
        }
    }

    /** Doldurma bitti: özet + "Kaydedeyim mi?". */
    private void filled() {
        flow.filled();
        showConfirm();
    }

    /** Vazgeçildi (Durdur, Hayır, geri): hiçbir şey yazılmaz. */
    void cancel() {
        boolean was = flow.state() == RunnerFlow.State.FILLING || flow.state() == RunnerFlow.State.CONFIRMING;
        flow.cancel();
        if (was) {
            release();
            form.close(false);
        }
    }

    /** Ekran kapandı (geri tuşu): akış vazgeçildi sayılır, hiçbir şey yazılmaz, ekran zaten kapanıyor. */
    void abandon() {
        flow.cancel();
        release();
    }

    void release() {
        handler.removeCallbacksAndMessages(null);
        if (guide != null) {
            guide.release();
        }
        if (voice != null) {
            voice.release();
        }
    }

    // ------------------------------------------------------------ onay kartı

    private void showConfirm() {
        ensureLayer();
        card.removeAllViews();
        if (payment.isEmpty()) {
            askPayment();
            return;
        }
        card.addView(text("Kaydedeyim mi?", 22, true));
        String summary = ActionPlan.summary(form.buyerName(), form.kilos(), form.gradeLabel(), form.price(), dueIso, payment);
        TextView body = text(summary, 18, false);
        body.setPadding(0, dp(6), 0, dp(4));
        card.addView(body);
        List<String> missing = missingNow();
        if (!missing.isEmpty()) {
            TextView ask = text("Eksik: " + String.join(" ", missing), 17, true);
            ask.setTextColor(GreenTheme.color(R.color.zd_warning));
            card.addView(ask);
        }
        if (!dueIso.isEmpty() && !autoReminder(activity)) {
            card.addView(alwaysToggle());
        }
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setPadding(0, dp(10), 0, 0);
        buttons.addView(button("Hayır", false, v -> cancel()), new LinearLayout.LayoutParams(0, dp(58), 1));
        View gap = new View(activity);
        buttons.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        buttons.addView(button("Evet, kaydet", true, v -> confirm()), new LinearLayout.LayoutParams(0, dp(58), 1.4f));
        card.addView(buttons);
        say(missing.isEmpty() ? summary + " Kaydedeyim mi?" : String.join(" ", missing));
    }

    /**
     * Tek adımlı satışta ödeme söylenmediyse onaydan önce sorulur; peşin ya da veresiye varsayılmaz. Cevap yalnız
     * formdaki ödeme düğmesini seçer; kayıt yine "Kaydedeyim mi?" onayıyla yapılır.
     */
    private void askPayment() {
        card.addView(text("Parasını aldın mı?", 22, true));
        String summary = ActionPlan.summary(form.buyerName(), form.kilos(), form.gradeLabel(), form.price(), dueIso, "");
        TextView body = text(summary, 18, false);
        body.setPadding(0, dp(6), 0, dp(4));
        card.addView(body);
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setPadding(0, dp(10), 0, 0);
        buttons.addView(button("Almadım", false, v -> answerPayment(ActionPlan.UNPAID)), new LinearLayout.LayoutParams(0, dp(58), 1));
        View gap = new View(activity);
        buttons.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        buttons.addView(button("Aldım, peşin", true, v -> answerPayment(ActionPlan.PAID)), new LinearLayout.LayoutParams(0, dp(58), 1));
        card.addView(buttons);
        TextView stop = text("Vazgeç", 17, false);
        stop.setMinHeight(dp(48));
        stop.setGravity(Gravity.CENTER);
        stop.setOnClickListener(v -> cancel());
        card.addView(stop);
        say(summary + " Parasını aldın mı?");
    }

    private void answerPayment(String answer) {
        int chip = ActionPlan.PAID.equals(answer) ? R.id.chipPayFull : R.id.chipPayNone;
        tap(activity.findViewById(chip));
        payment = answer;
        showConfirm();
    }

    /** Formda hâlâ eksik olan (sorulacak) bilgiler. */
    private List<String> missingNow() {
        List<String> m = new ArrayList<>();
        if (form.buyerName().trim().isEmpty()) {
            m.add("Kime sattın?");
        }
        if (form.kilos() <= 0) {
            m.add("Kaç kilo?");
        }
        if (form.price() <= 0) {
            m.add("Kilosu kaç liradan?");
        }
        return m;
    }

    /** "Evet, bunu hep yap": yalnız ödeme günü hatırlatması için izin (satış yine hep onaylı). */
    private View alwaysToggle() {
        TextView t = text("☐  Ödeme günü hatırlatmasını bundan sonra hep sormadan kur", 16, false);
        t.setMinHeight(dp(48));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setOnClickListener(v -> {
            askAlways = !askAlways;
            t.setText((askAlways ? "☑" : "☐") + "  Ödeme günü hatırlatmasını bundan sonra hep sormadan kur");
        });
        return t;
    }

    private void confirm() {
        if (flow.state() != RunnerFlow.State.CONFIRMING) {
            return;
        }
        boolean ok = flow.confirm(writer());
        if (flow.state() == RunnerFlow.State.CONFIRMING) {
            // Form eksik: hiçbir şey yazılmadı, çiftçi düzeltip yeniden "Evet" der.
            say("Bir bilgi eksik. Kırmızı alanı düzeltip yeniden Evet de.");
            showConfirm();
            return;
        }
        if (!ok) {
            say("Kaydedemedim, hiçbir şey yazılmadı.");
            release();
            form.close(false);
            return;
        }
        if (askAlways) {
            setAutoReminder(activity, true);
        }
        AssistantMeter.multiStep(activity, form.owner(), AssistantScorecard.MULTI_CONFIRMED);
        showUndo();
    }

    /** Kaydedildi: hepsini birlikte geri alma süresi. */
    private void showUndo() {
        card.removeAllViews();
        StringBuilder done = new StringBuilder(ActionPlan.PAID.equals(payment) ? "Kaydedildi: satış, para peşin alındı"
                : "Kaydedildi: satış, para alınmadı (alacak)");
        if (!dueIso.isEmpty()) {
            done.append(", ").append(ActionPlan.dayText(dueIso)).append(" ödeme hatırlatması");
        }
        done.append('.');
        card.addView(text(done.toString(), 19, true));
        TextView left = text("Yanlışsa " + UNDO_SECONDS + " saniye içinde hepsini geri alabilirsin.", 16, false);
        card.addView(left);
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setPadding(0, dp(10), 0, 0);
        buttons.addView(button("Geri al", false, v -> undo()), new LinearLayout.LayoutParams(0, dp(58), 1));
        View gap = new View(activity);
        buttons.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        buttons.addView(button("Tamam", true, v -> finishSaved()), new LinearLayout.LayoutParams(0, dp(58), 1));
        card.addView(buttons);
        say(done + " Yanlışsa Geri al de.");
        for (int s = UNDO_SECONDS - 1; s >= 0; s--) {
            final int sec = s;
            handler.postDelayed(() -> left.setText("Yanlışsa " + sec + " saniye içinde hepsini geri alabilirsin."),
                    (UNDO_SECONDS - s) * 1000L);
        }
        handler.postDelayed(this::finishSaved, UNDO_SECONDS * 1000L + 300);
    }

    private void undo() {
        boolean ok = flow.undo(writer());
        syncDeletions();
        AssistantMeter.multiStep(activity, form.owner(), AssistantScorecard.MULTI_UNDONE);
        android.widget.Toast.makeText(activity, ok ? "Geri alındı: hiçbiri kalmadı." : "Bir kısmı geri alınamadı; Satışlar'dan kontrol et.",
                android.widget.Toast.LENGTH_LONG).show();
        release();
        form.close(false);
    }

    /**
     * Geri alınan satışın silinmesi buluta hemen gitsin (Satışlar ekranındaki silmeyle aynı); internet yoksa
     * silme kaydı telefonda kalır, sonraki eşitlemede gider.
     */
    private void syncDeletions() {
        try {
            com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                return;
            }
            DatabaseHelper db = new DatabaseHelper(activity.getApplicationContext());
            new PrivateCloudSyncManager(db).sync(user.getUid(), null);
        } catch (RuntimeException ignored) {
            // eşitleme olmadı: silme kaydı sonraki eşitlemede gider
        }
    }

    private void finishSaved() {
        if (flow.state() != RunnerFlow.State.SAVED) {
            return;
        }
        release();
        form.close(true);
    }

    private RunnerFlow.Writer writer() {
        return new RunnerFlow.Writer() {
            private long buyer;

            @Override
            public long writeSale() {
                long id = form.saveForRunner();
                buyer = form.newBuyerId();
                return id;
            }

            @Override
            public long writeReminder(long saleId) {
                return PaymentReminders.addForSale(activity, form.owner(), saleId, form.gardenId(), form.buyerName(),
                        form.netTotal(), dueIso);
            }

            @Override
            public boolean deleteSale(long saleId) {
                DatabaseHelper db = new DatabaseHelper(activity);
                try {
                    return new ElekSaleBridge(db).deleteSaleWithReceivables(saleId);
                } finally {
                    db.close();
                }
            }

            @Override
            public boolean deleteReminder(long reminderId) {
                return PaymentReminders.remove(activity, reminderId);
            }

            @Override
            public void deleteNewBuyer() {
                long id = buyer > 0 ? buyer : form.newBuyerId();
                if (id > 0) {
                    form.deleteBuyerIfUnused(id);
                }
            }
        };
    }

    // ------------------------------------------------------------ görünüm

    private void ensureLayer() {
        if (layer != null) {
            return;
        }
        layer = new FrameLayout(activity);
        ((ViewGroup) activity.findViewById(android.R.id.content)).addView(layer, new FrameLayout.LayoutParams(-1, -1));
        card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(GreenTheme.color(R.color.zd_card));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(2), GreenTheme.color(R.color.zd_green));
        card.setBackground(bg);
        card.setElevation(dp(8));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lp.setMargins(dp(10), 0, dp(10), dp(12));
        layer.addView(card, lp);
        voice = new TanecikVoice(activity, layer).fast();
        voice.prepare(() -> { });
    }

    private void say(String speech) {
        if (quiet || voice == null) {
            return;
        }
        voice.say(speech, () -> { });
    }

    private TextView text(String value, int size, boolean bold) {
        TextView t = new TextView(activity);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(GreenTheme.color(R.color.zd_text));
        if (bold) {
            t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return t;
    }

    private TextView button(String label, boolean primary, View.OnClickListener click) {
        TextView b = text(label, 18, true);
        b.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        int green = GreenTheme.color(R.color.zd_green);
        bg.setColor(primary ? green : GreenTheme.color(R.color.zd_card));
        bg.setStroke(dp(2), green);
        b.setBackground(bg);
        if (primary) {
            b.setTextColor(0xFFFFFFFF);
        }
        b.setOnClickListener(click);
        return b;
    }

    /** "DOUBLE" → "Double", "3" → "3", "YAGLIK" → "Oil" (formdaki alan adları: etKgDouble, etKg3, etKgOil). */
    private static String suffix(String grade) {
        if ("DOUBLE".equals(grade)) {
            return "Double";
        }
        if ("YAGLIK".equals(grade)) {
            return "Oil";
        }
        return grade;
    }

    /** "2026-11-26" → "26.11.2026" (formun tarih biçimi). */
    private static String dotted(String iso) {
        return iso.substring(8, 10) + "." + iso.substring(5, 7) + "." + iso.substring(0, 4);
    }

    private int dp(int v) {
        return Math.round(v * activity.getResources().getDisplayMetrics().density);
    }
}
