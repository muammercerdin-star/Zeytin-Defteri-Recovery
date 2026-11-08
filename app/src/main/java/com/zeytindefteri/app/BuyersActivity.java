package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Alıcılarım: alıcı rehberi listesi ve tek alıcının karnesi (EXTRA_BUYER_ID verilirse).
 * Bütün rakamlar bu kullanıcının kendi satışlarından gelir; hiçbir şey paylaşılmaz.
 */
public final class BuyersActivity extends Activity {
    public static final String EXTRA_BUYER_ID = "buyer_id";
    /** Alıcılarım → "Elek raporu gör": karşılaştırmanın sade dille anlatımı. */
    static final String EXTRA_REPORT = "elek_report";
    /** Tanecik turundan açıldı: rapor kendiliğinden okunur, bitince tura dönülür. */
    static final String EXTRA_TOUR = "tanecik_tour";
    private boolean tourMode, tourStarted, tourSpoke;
    private TanecikVoice tourVoice;
    private boolean reportMode;
    private android.speech.tts.TextToSpeech tts;
    private boolean ttsReady, speaking;
    private TextView listenButton;
    /** Tanecik (maskot): raporun başında durur, sesli okurken ağzı oynar. */
    private android.widget.ImageView mascot;
    private int mascotRest = R.drawable.tanecik_merhaba;
    private boolean mouthOpen;
    private final android.os.Handler mouthHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable mouthTick = new Runnable() {
        @Override public void run() {
            if (!speaking || mascot == null) return;
            mouthOpen = !mouthOpen;
            mascot.setImageResource(mouthOpen ? R.drawable.tanecik_konusuyor : R.drawable.tanecik_konusuyor_kapali);
            mouthHandler.postDelayed(this, mouthOpen ? 170 : 130);
        }
    };

    private DatabaseHelper databaseHelper;
    private String owner = "";
    private long buyerId;
    private LinearLayout body;
    private String query = "";

    public static void open(Activity from, long buyerId) {
        Intent i = new Intent(from, BuyersActivity.class);
        if (buyerId > 0) i.putExtra(EXTRA_BUYER_ID, buyerId);
        from.startActivity(i);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!new SessionManager(this).hasSession()) { finish(); return; }
        databaseHelper = new DatabaseHelper(this);
        owner = databaseHelper.getActiveOwnerUid();
        buyerId = getIntent().getLongExtra(EXTRA_BUYER_ID, 0);
        reportMode = getIntent().getBooleanExtra(EXTRA_REPORT, false);
        tourMode = reportMode && getIntent().getBooleanExtra(EXTRA_TOUR, false);
        getWindow().setStatusBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().setNavigationBarColor(GreenTheme.color(R.color.zd_bg));
        if (buyerId <= 0 && !reportMode) {
            try { BuyerDirectory.mergeDuplicates(databaseHelper.getWritableDatabase(), owner); }
            catch (RuntimeException ignored) { /* Birleştirme yapılamazsa liste yine gösterilir. */ }
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(GreenTheme.color(R.color.zd_bg));
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(12), dp(16), dp(24));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        if (body != null) render();
    }

    @Override protected void onPause() {
        if (tourVoice != null) tourVoice.stop();
        stopSpeaking();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (tourVoice != null) { tourVoice.release(); tourVoice = null; }
        mouthHandler.removeCallbacksAndMessages(null);
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (RuntimeException ignored) { }
            tts = null;
        }
        if (databaseHelper != null) databaseHelper.close();
        super.onDestroy();
    }

    private void render() {
        body.removeAllViews();
        try {
            if (reportMode) renderReport(); else if (buyerId > 0) renderDetail(); else renderList();
        } catch (RuntimeException e) {
            body.addView(text("Alıcı bilgileri okunamadı.", 16, GreenTheme.color(R.color.zd_text_soft), false));
        }
    }

    // ------------------------------------------------------------ LİSTE

    private void renderList() {
        int season = databaseHelper.getActiveSeasonYear();
        List<BuyerDirectory.Summary> all = BuyerDirectory.list(databaseHelper.getReadableDatabase(), owner, season);
        final java.util.Map<Long, BuyerDirectory.ElekStats> elek = safeElekStats(season);

        LinearLayout head = header("Alıcılarım", null);
        TextView add = pill("+ Ekle", true);
        add.setContentDescription("Yeni alıcı ekle");
        add.setOnClickListener(v -> BuyerPicker.askNewBuyer(this, databaseHelper, query, name -> render()));
        head.addView(add, new LinearLayout.LayoutParams(-2, dp(44)));
        body.addView(head);

        double gross = 0, kg = 0, open = 0; int sales = 0, openCount = 0;
        for (BuyerDirectory.Summary s : all) {
            gross += s.gross; kg += s.kg; sales += s.saleCount;
            if (s.receivable > 0.009) { open += s.receivable; openCount++; }
        }
        LinearLayout summary = card(GreenTheme.color(R.color.zd_green_soft), GreenTheme.color(R.color.zd_green_mid_dim));
        LinearLayout cols = new LinearLayout(this);
        cols.addView(stat(season + " satışı", ElekSaleModel.tl(gross), sales + " satış · " + kg(kg),
                GreenTheme.color(R.color.zd_text)), new LinearLayout.LayoutParams(0, -2, 1));
        cols.addView(stat("Alacağın", ElekSaleModel.tl(open), openCount == 0 ? "açık alacak yok" : openCount + " alıcıda",
                GreenTheme.color(R.color.zd_warning)), new LinearLayout.LayoutParams(0, -2, 1));
        summary.addView(cols);
        body.addView(summary, gap(14));

        if (sales > 0) body.addView(reportEntry(), gap(12));

        if (all.isEmpty()) {
            body.addView(mascotView(R.drawable.tanecik_merhaba, 96), centered(96, 18));
            TextView empty = text("Henüz alıcı yok. Elek kaydında \"Bu zeytini sattım\" ile ya da yukarıdaki + Ekle ile alıcılarını kaydedebilirsin.",
                    16, GreenTheme.color(R.color.zd_text_soft), false);
            empty.setLineSpacing(0, 1.2f);
            body.addView(empty, gap(18));
            return;
        }

        final EditText search = new EditText(this);
        search.setHint("Alıcı ara (ad ya da yer)");
        search.setSingleLine(true);
        search.setText(query);
        search.setTextSize(16);
        search.setTextColor(GreenTheme.color(R.color.zd_text));
        search.setHintTextColor(GreenTheme.color(R.color.zd_muted));
        search.setBackground(BuyerPicker.round(this, GreenTheme.color(R.color.zd_surface), GreenTheme.color(R.color.zd_border), 16));
        search.setPadding(dp(16), 0, dp(16), 0);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(50));
        sp.topMargin = dp(14);
        body.addView(search, sp);

        final LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        body.addView(rows);
        final Runnable fill = () -> {
            rows.removeAllViews();
            List<BuyerDirectory.Summary> shown = BuyerDirectory.filter(all, query);
            List<BuyerDirectory.Summary> active = new ArrayList<>(), idle = new ArrayList<>();
            for (BuyerDirectory.Summary s : shown) (s.saleCount > 0 ? active : idle).add(s);
            if (shown.isEmpty()) rows.addView(text("Aramaya uyan alıcı yok.", 15, GreenTheme.color(R.color.zd_muted), false), gap(14));
            for (BuyerDirectory.Summary s : active) rows.addView(listRow(s, elek.get(s.id)), gap(10));
            if (!idle.isEmpty()) {
                TextView cap = text("BU SEZON SATIŞ YOK", 13, GreenTheme.color(R.color.zd_muted), true);
                cap.setLetterSpacing(0.06f);
                rows.addView(cap, gap(16));
                for (BuyerDirectory.Summary s : idle) rows.addView(listRow(s, null), gap(8));
            }
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { query = s.toString(); fill.run(); }
        });
        fill.run();

        TextView note = text("Alıcı rehberin yalnızca sende durur; kimseyle paylaşılmaz.", 14, GreenTheme.color(R.color.zd_muted), false);
        note.setGravity(Gravity.CENTER);
        body.addView(note, gap(20));
    }

    private View listRow(BuyerDirectory.Summary s, BuyerDirectory.ElekStats elek) {
        // Kart: üstte avatar/ad/tutar, altta (satış varsa) tam genişlik sınıf çubuğu ve yüzdeler.
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setMinimumHeight(dp(64));
        card.setBackground(BuyerPicker.round(this, GreenTheme.color(R.color.zd_card), GreenTheme.color(R.color.zd_border), 18));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView avatar = text(BuyerPicker.initial(s.name), 17, s.saleCount > 0 ? GreenTheme.color(R.color.zd_green) : GreenTheme.color(R.color.zd_muted), true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(BuyerPicker.round(this, s.saleCount > 0 ? GreenTheme.color(R.color.zd_green_chip) : GreenTheme.color(R.color.zd_surface), 0, 22));
        row.addView(avatar, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, dp(8), 0);
        copy.addView(text(s.name, 17, GreenTheme.color(R.color.zd_text), true));
        String sub = s.saleCount > 0
                ? join(s.region, s.saleCount + "\u00A0satış\u00A0· " + kg(s.kg))
                : join(s.region, s.lastSaleDate.isEmpty() ? "henüz satış yok" : "son satış " + BuyerDirectory.shortDate(s.lastSaleDate));
        copy.addView(text(sub, 14, GreenTheme.color(R.color.zd_muted), false));
        if (s.receivable > 0.009) copy.addView(text(ElekSaleModel.tl(s.receivable) + " alacak", 14, GreenTheme.color(R.color.zd_warning), true));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        boolean hasElek = elek != null && elek.totalKg > 0;
        if (s.gross > 0) {
            LinearLayout money = new LinearLayout(this);
            money.setOrientation(LinearLayout.VERTICAL);
            money.setGravity(Gravity.END);
            money.addView(text(ElekSaleModel.tl(s.gross), 16, GreenTheme.color(R.color.zd_text), true));
            if (hasElek) money.addView(text(BuyerDirectory.oneDecimal(elek.avgPerKg()) + "\u00A0TL/kg", 13, GreenTheme.color(R.color.zd_muted), false));
            row.addView(money);
        }
        card.addView(row);
        String legend = hasElek ? ElekInsights.legend(elek.kg) : "";
        if (hasElek) {
            // Elek performansı: Elek ekranındaki gibi sınıf yüzdeleri.
            card.addView(gradeBar(elek, 6), sized(-1, 6, 12));
            TextView line = text(legend, 13, GreenTheme.color(R.color.zd_text_soft), false);
            line.setPadding(0, dp(6), 0, 0);
            line.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            card.addView(line);
        }
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(s.name + ", " + sub + (s.receivable > 0.009 ? ", " + ElekSaleModel.tl(s.receivable) + " alacak" : "")
                + (hasElek ? ", " + ElekInsights.accessibleLegend(elek.kg) : ""));
        card.setOnClickListener(v -> open(this, s.id));
        return card;
    }

    // ------------------------------------------------------------ ELEK RAPORU

    /** Listedeki giriş kartı. */
    private View reportEntry() {
        LinearLayout c = card(GreenTheme.color(R.color.zd_card), GreenTheme.color(R.color.zd_border));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(mascotView(R.drawable.tanecik_merhaba, 48), new LinearLayout.LayoutParams(dp(48), dp(54)));
        copy.setPadding(dp(10), 0, 0, 0);
        copy.addView(text("Elek raporu gör", 17, GreenTheme.color(R.color.zd_text), true));
        copy.addView(text("Aynı zeytine kim daha çok veriyor? Sade dille anlatır.", 14, GreenTheme.color(R.color.zd_muted), false));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text("›", 28, GreenTheme.color(R.color.zd_green), true));
        c.addView(row);
        c.setMinimumHeight(dp(64));
        c.setClickable(true);
        c.setFocusable(true);
        c.setContentDescription("Elek raporu gör. Aynı zeytine kim daha çok veriyor, sade dille anlatır.");
        c.setOnClickListener(v -> startActivity(new Intent(this, BuyersActivity.class).putExtra(EXTRA_REPORT, true)));
        return c;
    }

    private void renderReport() {
        int season = databaseHelper.getActiveSeasonYear();
        body.addView(header("Elek raporu", season + " sezonu · senin kayıtlarından"));
        List<BuyerDirectory.Summary> all = BuyerDirectory.list(databaseHelper.getReadableDatabase(), owner, season);
        List<BuyerElekReport.Buyer> buyers = new ArrayList<>();
        for (BuyerDirectory.Summary s : all) if (s.saleCount > 0) buyers.add(new BuyerElekReport.Buyer(s.id, s.name, s.receivable));
        final BuyerElekReport.Report report = BuyerElekReport.build(safeFairSales(season), buyers);
        int ink = GreenTheme.color(R.color.zd_text), muted = GreenTheme.color(R.color.zd_muted);

        LinearLayout top = card(GreenTheme.color(R.color.zd_green_soft), GreenTheme.color(R.color.zd_green_mid_dim));
        mascotRest = mascotPose(report);
        LinearLayout lead = new LinearLayout(this);
        lead.setGravity(Gravity.CENTER_VERTICAL);
        mascot = mascotView(mascotRest, 88);
        lead.addView(mascot, new LinearLayout.LayoutParams(dp(88), dp(99)));
        TextView headline = text(report.headline, 19, ink, true);
        headline.setLineSpacing(0, 1.15f);
        headline.setPadding(dp(12), 0, 0, 0);
        lead.addView(headline, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(lead);
        listenButton = pill("🔊  Sesli dinle", true);
        listenButton.setContentDescription("Raporu sesli dinle");
        listenButton.setOnClickListener(v -> toggleSpeak(report.plainText()));
        top.addView(listenButton, sized(-1, 48, 14));
        body.addView(top, gap(14));
        if (tourMode) {
            TextView back = pill("Tura dön ›", false);
            back.setOnClickListener(v -> finish());
            body.addView(back, sized(-1, 48, 10));
            // Tanecik başlığı ve en iyi alıcının satırını okur; bitince tura döner (ses yoksa 12 sn sonra).
            StringBuilder say = new StringBuilder(report.headline);
            if (!report.sections.isEmpty() && report.sections.get(0).compared) {
                BuyerElekReport.Section first = report.sections.get(0);
                say.append(' ').append(first.title).append('.');
                if (!first.lines.isEmpty()) say.append(' ').append(first.lines.get(0));
            }
            final String text = say.toString();
            if (!tourStarted) {
                tourStarted = true;
                // Turda: büyük sarı altyazıyla, biraz hızlı ses (TanecikVoice); bitince tura döner.
                android.widget.FrameLayout over = new android.widget.FrameLayout(this);
                ((android.view.ViewGroup) findViewById(android.R.id.content)).addView(over,
                        new android.widget.FrameLayout.LayoutParams(-1, -1));
                tourVoice = new TanecikVoice(this, over);
                tourVoice.prepare(() -> mouthHandler.postDelayed(() -> {
                    if (isFinishing()) return;
                    setSpeaking(true);
                    tourVoice.say(text, () -> setSpeaking(false));
                }, 500));
            }
        }

        for (BuyerElekReport.Section sec : report.sections) {
            LinearLayout box = card(GreenTheme.color(R.color.zd_card), GreenTheme.color(R.color.zd_border));
            int color = sec.tone > 0 ? GreenTheme.color(R.color.zd_green) : sec.tone < 0 ? GreenTheme.color(R.color.zd_warning) : ink;
            box.addView(text(sec.title, 17, sec.compared ? color : muted, true));
            for (String line : sec.lines) {
                TextView t = text(line, 16, GreenTheme.color(R.color.zd_text_soft), false);
                t.setLineSpacing(0, 1.2f);
                box.addView(t, gap(8));
            }
            body.addView(box, gap(12));
        }
        if (!report.tip.isEmpty()) {
            TextView tip = text(report.tip, 15, muted, false);
            tip.setLineSpacing(0, 1.2f);
            body.addView(tip, gap(16));
        }
        TextView how = text("Nasıl hesaplanır: bir alıcıya verdiğin zeytin, aynı bahçeden " + BuyerFairCompare.ELEK_DAYS
                + " gün içinde başka alıcılara verdiğinle karşılaştırılır. Sınıf fiyatları aynı günlerin (±" + BuyerFairCompare.PRICE_DAYS
                + " gün) satışlarıyla kıyaslanır. Sınıfsız (karışık) satışlar girmez. Hesap telefonunda yapılır, kimseye gönderilmez.",
                13, muted, false);
        how.setLineSpacing(0, 1.15f);
        body.addView(how, gap(14));
    }

    /** Rapora göre duruş: iyi alıcı bulunduysa sevinç, kıyas yoksa düşünüyor, diğer hallerde selam. */
    static int mascotPose(BuyerElekReport.Report report) {
        boolean anyCompared = false, anyGood = false;
        for (BuyerElekReport.Section s : report.sections) {
            if (s.compared) anyCompared = true;
            if (s.compared && s.tone > 0) anyGood = true;
        }
        if (anyGood) return R.drawable.tanecik_sevinc;
        if (!anyCompared) return R.drawable.tanecik_dusunuyor;
        return R.drawable.tanecik_merhaba;
    }

    /** Süs görseli: ekran okuyucu atlar (bilgi metinde var). */
    private android.widget.ImageView mascotView(int res, int widthDp) {
        android.widget.ImageView v = new android.widget.ImageView(this);
        v.setImageResource(res);
        v.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        v.setAdjustViewBounds(true);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }

    private LinearLayout.LayoutParams centered(int widthDp, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(widthDp), dp(Math.round(widthDp * 270f / 240f)));
        p.gravity = Gravity.CENTER_HORIZONTAL;
        p.topMargin = dp(topDp);
        return p;
    }

    private void toggleSpeak(String text) {
        if (speaking) { stopSpeaking(); return; }
        if (tts == null) {
            Toast.makeText(this, "Ses hazırlanıyor…", Toast.LENGTH_SHORT).show();
            final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
            tts = new android.speech.tts.TextToSpeech(getApplicationContext(), st -> main.post(() -> {
                int lang = android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED;
                try { if (st == android.speech.tts.TextToSpeech.SUCCESS) lang = tts.setLanguage(new Locale("tr", "TR")); }
                catch (RuntimeException ignored) { }
                ttsReady = st == android.speech.tts.TextToSpeech.SUCCESS && lang >= 0;
                if (!ttsReady) {
                    Toast.makeText(this, "Telefonda Türkçe ses yok. Ayarlar → Metin okuma'dan Türkçe sesi indirebilirsin.", Toast.LENGTH_LONG).show();
                    return;
                }
                tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                    @Override public void onStart(String id) { }
                    @Override public void onDone(String id) { main.post(() -> setSpeaking(false)); }
                    @Override public void onError(String id) { main.post(() -> setSpeaking(false)); }
                });
                speak(text);
            }));
            return;
        }
        if (ttsReady) speak(text);
    }

    private void speak(String text) {
        if (tts == null || isFinishing()) return;
        // Okurken "%" ve "TL" düzgün söylensin.
        String spoken = text.replace("%", "yüzde ").replace(" TL", " lira").replace(" kg", " kilo").replace("±", "artı eksi ");
        if (tts.speak(spoken, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "rapor-" + System.nanoTime())
                != android.speech.tts.TextToSpeech.ERROR) setSpeaking(true);
    }

    private void stopSpeaking() {
        if (tts != null) { try { tts.stop(); } catch (RuntimeException ignored) { } }
        setSpeaking(false);
    }

    private void setSpeaking(boolean on) {
        boolean was = speaking;
        speaking = on;
        if (on) tourSpoke = true;
        if (tourMode && was && !on && tourSpoke) mouthHandler.postDelayed(() -> { if (!isFinishing()) finish(); }, 1500);
        mouthHandler.removeCallbacks(mouthTick);
        if (mascot != null) {
            if (on && !was) mouthHandler.post(mouthTick);
            else if (!on) mascot.setImageResource(mascotRest);
        }
        if (listenButton != null) {
            listenButton.setText(on ? "■  Durdur" : "🔊  Sesli dinle");
            listenButton.setContentDescription(on ? "Okumayı durdur" : "Raporu sesli dinle");
        }
    }

    // ------------------------------------------------------------ KARNE

    private void renderDetail() {
        int season = databaseHelper.getActiveSeasonYear();
        BuyerDirectory.Summary s = null;
        for (BuyerDirectory.Summary x : BuyerDirectory.list(databaseHelper.getReadableDatabase(), owner, season)) {
            if (x.id == buyerId) { s = x; break; }
        }
        if (s == null) { finish(); return; }
        final BuyerDirectory.Summary buyer = s;

        LinearLayout head = header(buyer.name, join(buyer.region, buyer.phone));
        if (!buyer.phone.isEmpty()) {
            TextView call = pill("Ara", false);
            call.setContentDescription(buyer.name + " ara");
            call.setOnClickListener(v -> {
                try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + buyer.phone.replace(" ", "")))); }
                catch (RuntimeException e) { Toast.makeText(this, "Arama açılamadı.", Toast.LENGTH_SHORT).show(); }
            });
            head.addView(call, new LinearLayout.LayoutParams(-2, dp(44)));
        }
        body.addView(head);

        LinearLayout stats = card(GreenTheme.color(R.color.zd_card), GreenTheme.color(R.color.zd_border));
        LinearLayout cols = new LinearLayout(this);
        cols.addView(stat("Satış", String.valueOf(buyer.saleCount), "", GreenTheme.color(R.color.zd_text)), new LinearLayout.LayoutParams(0, -2, 1));
        cols.addView(stat("Zeytin", kg(buyer.kg), "", GreenTheme.color(R.color.zd_text)), new LinearLayout.LayoutParams(0, -2, 1));
        cols.addView(stat("Tutar", ElekSaleModel.tl(buyer.gross), "", GreenTheme.color(R.color.zd_text)), new LinearLayout.LayoutParams(0, -2, 1.3f));
        stats.addView(cols);
        TextView seasonNote = text(season + " sezonu", 13, GreenTheme.color(R.color.zd_muted), false);
        seasonNote.setPadding(0, dp(8), 0, 0);
        stats.addView(seasonNote);
        body.addView(stats, gap(14));

        if (buyer.receivable > 0.009) {
            LinearLayout due = card(GreenTheme.color(R.color.zd_warning_soft), GreenTheme.color(R.color.zd_warning));
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.addView(text("ALACAĞIN", 14, GreenTheme.color(R.color.zd_warning), true), new LinearLayout.LayoutParams(0, -2, 1));
            line.addView(text(ElekSaleModel.tl(buyer.receivable), 22, GreenTheme.color(R.color.zd_warning), true));
            due.addView(line);
            body.addView(due, gap(12));
        }

        java.util.Map<Long, BuyerDirectory.ElekStats> elek = safeElekStats(season);
        BuyerDirectory.ElekStats mine = elek.get(buyerId);
        // Adil karşılaştırma: her satış yalnız aynı bahçe/yakın tarihli satışlarla kıyaslanır (BuyerFairCompare).
        List<BuyerFairCompare.Sale> fair = safeFairSales(season);
        if (mine != null && mine.totalKg > 0) body.addView(elekCard(buyer, mine, BuyerFairCompare.elek(fair, buyerId)), gap(12));

        BuyerFairCompare.Prices prices = BuyerFairCompare.prices(fair, buyerId);
        boolean comparable = prices.matchedSales > 0;
        if (!prices.rows.isEmpty()) {
            LinearLayout box = card(GreenTheme.color(R.color.zd_card), GreenTheme.color(R.color.zd_border));
            box.addView(text(comparable ? "Kim daha iyi fiyat verdi?" : "Ortalama fiyatları", 17, GreenTheme.color(R.color.zd_text), true));
            box.addView(text(BuyerFairCompare.priceBasis(prices), 14, GreenTheme.color(R.color.zd_muted), false));
            box.addView(priceLine("Elek", buyer.name, comparable ? "Diğerleri" : "", GreenTheme.color(R.color.zd_muted), GreenTheme.color(R.color.zd_muted), false), gap(10));
            for (BuyerFairCompare.PriceRow r : prices.rows) {
                int better = GreenTheme.color(R.color.zd_green), worse = GreenTheme.color(R.color.zd_warning), plain = GreenTheme.color(R.color.zd_text);
                String mark = "";
                int color = plain;
                String mineText, othersText;
                if (r.matched()) {
                    double diff = r.buyerAvg - r.othersAvg;
                    if (diff >= 0.5) { mark = " ▲"; color = better; }
                    else if (diff <= -0.5) { mark = " ▼"; color = worse; }
                    mineText = price(r.buyerAvg) + mark;
                    othersText = price(r.othersAvg);
                } else {
                    // Aynı günlerde başka alıcı yok: sezon ortalaması bilgi olarak, karşılaştırmasız.
                    mineText = price(r.buyerSeasonAvg);
                    othersText = comparable ? "—" : "";
                }
                box.addView(priceLine(BuyerDirectory.GRADE_LABELS[r.grade], mineText, othersText, color, plain, true), gap(8));
            }
            if (comparable) {
                TextView note = text("Diğerleri: aynı günlerde (±" + BuyerFairCompare.PRICE_DAYS
                        + " gün) başka alıcılara aynı sınıfa aldığın fiyat. Eşleşmeyen satışlar karşılaştırmaya girmez.",
                        13, GreenTheme.color(R.color.zd_muted), false);
                note.setLineSpacing(0, 1.15f);
                box.addView(note, gap(10));
            }
            body.addView(box, gap(12));
        }

        List<BuyerDirectory.SaleLine> sales = BuyerDirectory.sales(databaseHelper.getReadableDatabase(), owner, buyerId, season);
        if (!sales.isEmpty()) {
            body.addView(text("Satışlar", 17, GreenTheme.color(R.color.zd_text), true), gap(16));
            LinearLayout list = card(GreenTheme.color(R.color.zd_card), GreenTheme.color(R.color.zd_border));
            for (int i = 0; i < sales.size(); i++) {
                BuyerDirectory.SaleLine l = sales.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(10), 0, dp(10));
                LinearLayout copy = new LinearLayout(this);
                copy.setOrientation(LinearLayout.VERTICAL);
                copy.addView(text(BuyerDirectory.shortDate(l.date) + " · " + kg(l.kg), 16, GreenTheme.color(R.color.zd_text), true));
                String sub = l.open > 0.009 ? join(l.garden, ElekSaleModel.tl(l.open) + " kaldı") : join(l.garden, "tamamı alındı");
                copy.addView(text(sub, 14, l.open > 0.009 ? GreenTheme.color(R.color.zd_warning) : GreenTheme.color(R.color.zd_muted), l.open > 0.009));
                if (l.elekDeleted) copy.addView(text("Elek kaydı silinmiş", 13, GreenTheme.color(R.color.zd_muted), true));
                row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
                row.addView(text(ElekSaleModel.tl(l.gross), 16, GreenTheme.color(R.color.zd_text), true));
                row.addView(text("  ⋮", 18, GreenTheme.color(R.color.zd_muted), true));
                row.setClickable(true);
                row.setFocusable(true);
                row.setContentDescription(BuyerDirectory.shortDate(l.date) + ", " + kg(l.kg) + ", " + ElekSaleModel.tl(l.gross)
                        + ", " + sub + (l.elekDeleted ? ", elek kaydı silinmiş" : "") + ". Seçenekler");
                row.setOnClickListener(v -> askDeleteSale(l));
                list.addView(row);
                if (i < sales.size() - 1) {
                    View rule = new View(this);
                    rule.setBackgroundColor(GreenTheme.color(R.color.zd_border));
                    list.addView(rule, new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))));
                }
            }
            body.addView(list, gap(8));
        } else {
            body.addView(text("Bu sezon bu alıcıya satış kaydı yok.", 15, GreenTheme.color(R.color.zd_muted), false), gap(16));
        }

        TextView edit = pill("Bilgileri düzenle", false);
        edit.setOnClickListener(v -> editBuyer(buyer));
        body.addView(edit, sized(-1, 48, 18));
    }

    /** Yanlış ya da eleği silinmiş satışı alacağıyla birlikte silme. */
    private void askDeleteSale(BuyerDirectory.SaleLine l) {
        String msg = BuyerDirectory.shortDate(l.date) + " · " + kg(l.kg) + " · " + ElekSaleModel.tl(l.gross)
                + (l.open > 0.009 ? "\nAlacak: " + ElekSaleModel.tl(l.open) : "")
                + (l.elekDeleted ? "\n\nBu satışın elek kaydı silinmiş." : "")
                + "\n\nSatış silinirse bu alıcının toplamından ve alacağından da düşer. Bu işlem geri alınamaz.";
        new AlertDialog.Builder(this)
                .setTitle("Satış silinsin mi?")
                .setMessage(msg)
                .setPositiveButton("Satışı sil", (d, w) -> {
                    boolean ok;
                    try { ok = new ElekSaleBridge(databaseHelper).deleteSaleWithReceivables(l.saleId); }
                    catch (RuntimeException e) { ok = false; }
                    Toast.makeText(this, ok ? "Satış silindi." : "Satış silinemedi.", Toast.LENGTH_SHORT).show();
                    render();
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private java.util.Map<Long, BuyerDirectory.ElekStats> safeElekStats(int season) {
        try { return BuyerDirectory.elekStats(databaseHelper.getReadableDatabase(), owner, season); }
        catch (RuntimeException e) { return new java.util.HashMap<>(); }
    }

    private static final int[] GRADE_COLORS = {
            R.color.elek_badge_double, R.color.elek_badge_one, R.color.elek_badge_two,
            R.color.elek_badge_three, R.color.elek_badge_four, R.color.elek_badge_oil
    };

    /** Sınıf renkleriyle yatay dağılım çubuğu (elek makbuzundaki ile aynı renkler). */
    private View gradeBar(BuyerDirectory.ElekStats s, int heightDp) {
        LinearLayout bar = new LinearLayout(this);
        bar.setBackground(BuyerPicker.round(this, GreenTheme.color(R.color.zd_surface), 0, heightDp / 2));
        bar.setClipToOutline(true);
        boolean first = true;
        for (int i = 0; i < s.kg.length; i++) {
            if (s.kg[i] <= 0) continue;
            View seg = new View(this);
            seg.setBackgroundColor(GreenTheme.color(GRADE_COLORS[i]));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, (float) s.kg[i]);
            if (!first) p.leftMargin = Math.max(1, dp(1));
            first = false;
            bar.addView(seg, p);
        }
        bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return bar;
    }

    private List<BuyerFairCompare.Sale> safeFairSales(int season) {
        try { return BuyerDirectory.fairSales(databaseHelper.getReadableDatabase(), owner, season); }
        catch (RuntimeException e) { return new java.util.ArrayList<>(); }
    }

    /**
     * Karnedeki "Elek performansı" kartı. Çubuk bu alıcının sezon dağılımını gösterir;
     * karşılaştırma ise yalnız AYNI BAHÇEDEN, ±7 gün içinde başka alıcılara yapılan satışlarla yapılır.
     */
    private View elekCard(BuyerDirectory.Summary buyer, BuyerDirectory.ElekStats mine, BuyerFairCompare.Elek fair) {
        LinearLayout box = card(GreenTheme.color(R.color.zd_card), GreenTheme.color(R.color.zd_border));
        box.addView(text("Elek performansı", 17, GreenTheme.color(R.color.zd_text), true));
        box.addView(text("Bu sezon bu alıcıya verdiğin zeytinin sınıf dağılımı", 14, GreenTheme.color(R.color.zd_muted), false));
        box.addView(gradeBar(mine, 10), sized(-1, 10, 12));
        int ink = GreenTheme.color(R.color.zd_text), muted = GreenTheme.color(R.color.zd_muted);
        int green = GreenTheme.color(R.color.zd_green), warn = GreenTheme.color(R.color.zd_warning);
        boolean enough = fair != null && fair.enough();
        String verdict = BuyerFairCompare.elekVerdict(fair, buyer.name);
        if (!enough) {
            // Adil karşılaştırma yok: yalnız bu alıcının sezon yüzdeleri, yanında "diğerleri" sütunu yok.
            box.addView(priceLine("Sınıf", buyer.name, "", muted, muted, false), gap(12));
            for (int i = 0; i < mine.kg.length; i++) {
                if (mine.kg[i] <= 0) continue;
                box.addView(priceLine(BuyerDirectory.GRADE_LABELS[i], "%" + BuyerDirectory.pct(mine.share(i)), "", ink, ink, true), gap(8));
            }
            box.addView(priceLine("Kg başı", BuyerDirectory.oneDecimal(mine.avgPerKg()) + " TL", "", ink, ink, true), gap(10));
            TextView why = text(verdict, 14, muted, false);
            why.setLineSpacing(0, 1.15f);
            box.addView(why, gap(12));
            return box;
        }
        box.addView(text("Adil karşılaştırma", 15, ink, true), gap(14));
        box.addView(text(BuyerFairCompare.elekBasis(fair), 13, muted, false));
        box.addView(priceLine("Sınıf", buyer.name, "Diğerleri", muted, muted, false), gap(10));
        for (int i = 0; i < fair.buyerShare.length; i++) {
            double a = fair.buyerShare[i], b = fair.othersShare[i];
            if (a <= 0.05 && b <= 0.05) continue;
            String mark = "";
            int color = ink;
            if (Math.abs(a - b) >= 3 && (i <= 1 || i >= 4)) {
                boolean higher = a > b;
                mark = higher ? " ▲" : " ▼";
                // İri sınıfta fazlası iyi, ufak sınıfta (4, Yağlık) fazlası kötü.
                color = (i <= 1) == higher ? green : warn;
            }
            box.addView(priceLine(BuyerDirectory.GRADE_LABELS[i], "%" + BuyerDirectory.pct(a) + mark,
                    "%" + BuyerDirectory.pct(b), color, ink, true), gap(8));
        }
        double perKg = fair.perKgDiff();
        int perKgColor = perKg >= 0.5 ? green : perKg <= -0.5 ? warn : ink;
        box.addView(priceLine("Kg başı", BuyerDirectory.oneDecimal(fair.buyerPerKg) + " TL" + (perKg >= 0.5 ? " ▲" : perKg <= -0.5 ? " ▼" : ""),
                BuyerDirectory.oneDecimal(fair.othersPerKg) + " TL", perKgColor, ink, true), gap(10));
        box.addView(text(verdict, 15, perKgColor, true), gap(12));
        TextView caveat = text("Diğerleri: aynı bahçeden, " + BuyerFairCompare.ELEK_DAYS
                + " gün içinde başka alıcılara verdiğin zeytin. Böylece fark bahçeden ya da piyasanın oynamasından değil, alıcıdan gelir.",
                13, muted, false);
        caveat.setLineSpacing(0, 1.15f);
        box.addView(caveat, gap(6));
        return box;
    }

    private void editBuyer(BuyerDirectory.Summary b) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);
        EditText name = BuyerPicker.field(this, "Ad", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setText(b.name);
        EditText phone = BuyerPicker.field(this, "Telefon (isteğe bağlı)", InputType.TYPE_CLASS_PHONE);
        phone.setText(b.phone);
        EditText place = BuyerPicker.field(this, "Yer (isteğe bağlı)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        place.setText(b.region);
        form.addView(name); form.addView(phone); form.addView(place);
        AlertDialog d = new AlertDialog.Builder(this).setTitle("Alıcıyı düzenle").setView(form)
                .setPositiveButton("Kaydet", null).setNegativeButton("Vazgeç", null).create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (BuyerDirectory.clean(name.getText().toString()).isEmpty()) { name.setError("Ad gerekli"); return; }
            boolean ok;
            try {
                ok = BuyerDirectory.update(databaseHelper.getWritableDatabase(), owner, b.id,
                        name.getText().toString(), phone.getText().toString(), place.getText().toString());
            } catch (RuntimeException e) { ok = false; }
            if (!ok) { name.setError("Bu adla başka bir alıcı var"); return; }
            d.dismiss();
            render();
        }));
        d.show();
    }

    // ------------------------------------------------------------ YARDIMCILAR

    private LinearLayout header(String title, String subtitle) {
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setMinimumHeight(dp(56));
        TextView back = text("‹", 30, GreenTheme.color(R.color.zd_text), false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Geri");
        back.setOnClickListener(v -> finish());
        head.addView(back, new LinearLayout.LayoutParams(dp(44), dp(48)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(4), 0, dp(8), 0);
        copy.addView(text(title, 24, GreenTheme.color(R.color.zd_text), true));
        if (subtitle != null && !subtitle.isEmpty()) copy.addView(text(subtitle, 15, GreenTheme.color(R.color.zd_muted), false));
        head.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        return head;
    }

    private LinearLayout card(int fill, int stroke) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(BuyerPicker.round(this, fill, stroke, 20));
        return c;
    }

    private View stat(String label, String value, String sub, int valueColor) {
        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.VERTICAL);
        s.addView(text(label, 14, GreenTheme.color(R.color.zd_text_soft), false));
        s.addView(text(value, 20, valueColor, true));
        if (!sub.isEmpty()) s.addView(text(sub, 14, GreenTheme.color(R.color.zd_text_soft), false));
        return s;
    }

    private View priceLine(String a, String b, String c, int bColor, int cColor, boolean bold) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int size = bold ? 16 : 13;
        row.addView(text(a, size, bold ? GreenTheme.color(R.color.zd_text) : GreenTheme.color(R.color.zd_muted), bold), new LinearLayout.LayoutParams(0, -2, 1));
        TextView tb = text(b, size, bColor, true);
        tb.setGravity(Gravity.END);
        tb.setSingleLine(true);
        tb.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(tb, new LinearLayout.LayoutParams(dp(104), -2));
        TextView tc = text(c, size, cColor, bold);
        tc.setGravity(Gravity.END);
        row.addView(tc, new LinearLayout.LayoutParams(dp(84), -2));
        return row;
    }

    private TextView pill(String label, boolean solid) {
        TextView t = text(label, 15, solid ? GreenTheme.color(R.color.zd_bg) : GreenTheme.color(R.color.zd_text), true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), 0, dp(16), 0);
        t.setBackground(BuyerPicker.round(this, solid ? GreenTheme.color(R.color.zd_green) : Color.TRANSPARENT,
                solid ? 0 : GreenTheme.color(R.color.zd_border), 22));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout.LayoutParams gap(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(topDp);
        return p;
    }

    private LinearLayout.LayoutParams sized(int width, int heightDp, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, dp(heightDp));
        p.topMargin = dp(topDp);
        return p;
    }

    private static String join(String a, String b) {
        String x = a == null ? "" : a.trim(), y = b == null ? "" : b.trim();
        if (x.isEmpty()) return y;
        if (y.isEmpty()) return x;
        return x + " · " + y;
    }

    static String kg(double v) {
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(new Locale("tr", "TR"));
        nf.setMaximumFractionDigits(1);
        return nf.format(Math.max(0, v)) + "\u00A0kg"; // "340 kg" iki satıra bölünmesin
    }

    static String price(double v) {
        if (v <= 0) return "–";
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(new Locale("tr", "TR"));
        nf.setMaximumFractionDigits(1);
        return nf.format(v);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
