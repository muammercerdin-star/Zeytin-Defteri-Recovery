package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Tezgah Modu (tüccar): kantarda kasa kasa zeytin alımı.
 *
 * Akış: zeytin elenir → numara seçilir → "kaç kasa + kantar kilosu" girilir → net = kilo − kasa × dara.
 * Canlı toplam büyük yazılır; ekran kararmaz; yarım kalan tezgah telefonda saklanır.
 * Bitir → alım fişi bu telefonda saklanır (TezgahPurchases), WhatsApp'tan üreticiye gönderilir,
 * ekran bir sonraki üretici için sıfırlanır. Tüccar alımları buluta, Lig'e ve piyasaya karışmaz.
 * Üretici kendi satışını Elek kaydından girer; bu ekran onun için değildir.
 */
public final class TezgahActivity extends Activity {
    /** Taslak anahtarları eski sürümle uyumlu kalsın diye: önceki "Alıyorum" rolünün numarası. */
    private static final int ROLE_BUY = 1;
    private static final String PREFS = "zd_tezgah";
    /** Tezgâh ana ekranından "Tartım başlat": sihirbaz hemen açılır; ekrandaki ikinci "Tartım başlat" gösterilmez. */
    public static final String EXTRA_WIZARD = "open_wizard";
    private boolean fromHome;
    // Güneşte okunur: koyu zemin, yüksek karşıtlık.
    private static final int BG = Color.parseColor("#050F16");
    private static final int CARD = Color.parseColor("#0F2230");
    private static final int LINE = Color.parseColor("#2A4452");
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.parseColor("#A9BAB6");
    private static final int GREEN = Color.parseColor("#8BE06A");
    private static final int GREEN_DIM = Color.parseColor("#1E3D2C");
    private static final int AMBER = Color.parseColor("#F2C35B");
    private static final int RED = Color.parseColor("#FF7A70");

    private DatabaseHelper db;
    private SharedPreferences prefs;
    private String owner = "";
    private final int role = ROLE_BUY;
    private int grade = 0;
    private TezgahModel model = new TezgahModel();
    private int kasa = 1;

    private String seller = "", sellerPhone = "";

    private LinearLayout body;
    private TextView totalView, totalSub, finishButton, netPreview;
    private EditText kgInput, kasaInput;
    private final TextView[] gradeTabs = new TextView[TezgahModel.GRADES.length];

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!new SessionManager(this).hasSession()) { finish(); return; }
        db = new DatabaseHelper(this);
        owner = db.getActiveOwnerUid();
        if (owner == null) owner = "";
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        kasa = Math.max(0, Math.min(TezgahModel.MAX_KASA, prefs.getInt("kasa", 1)));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        fromHome = getIntent() != null && getIntent().getBooleanExtra(EXTRA_WIZARD, false);
        loadDraft();
        // Yeni alım 1 kasadan başlar; yarım kalan alıma devam ederken son yazılan kasa kalır.
        if (model.isEmpty()) kasa = 1;
        build();
        // Ana ekrandan "Tartım başlat" ile gelince sihirbaz kendiliğinden açılmaz: tüccar "Yeni tartım başlat"
        // (elle) ya da "Sesle tart" (sesli) ile kendisi başlatır.
    }

    /** Alım defterinden dönünce (silme, borç kapama) gün özeti ve son fiş tazelensin. */
    @Override protected void onRestart() {
        super.onRestart();
        build();
    }

    @Override protected void onPause() {
        if (voice != null) voice.close();
        saveDraft();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (voice != null) voice.close();
        if (lastSlipBox != null && slipCollapse != null) lastSlipBox.removeCallbacks(slipCollapse);
        if (db != null) db.close();
        super.onDestroy();
    }

    // ================================================================== taslak

    private String draftKey() { return "draft_" + role + "_" + owner; }

    private void loadDraft() {
        String saved = prefs.getString(draftKey(), "");
        model = TezgahModel.decode(saved);
        if (saved == null || saved.isEmpty()) {
            model.setTare(prefs.getFloat("tare", (float) TezgahModel.DEFAULT_TARE));
            double[] p = defaultPrices();
            for (int g = 0; g < p.length; g++) model.setPrice(g, p[g]);
        }
        seller = prefs.getString("seller_" + owner, "");
        sellerPhone = prefs.getString("seller_phone_" + owner, "");
        grade = Math.max(0, Math.min(TezgahModel.GRADES.length - 1, prefs.getInt("grade_" + role, 0)));
    }

    private void saveDraft() {
        if (prefs == null) return;
        SharedPreferences.Editor e = prefs.edit()
                .putInt("kasa", kasa)
                .putInt("grade_" + role, grade)
                .putFloat("tare", (float) model.tare())
                .putString("seller_" + owner, seller)
                .putString("seller_phone_" + owner, sellerPhone);
        if (model.isEmpty()) e.remove(draftKey()); else e.putString(draftKey(), model.encode());
        e.apply();
    }

    /** Tezgahta en son kullanılan alım fiyatları (ilk seferde boş: "fiyat?" ile sorulur). */
    /** İlk kullanımda hazır gelen alım fiyatları (₺/kg): Double, 1, 2, 3, 4, Yağlık. Değiştirilince son fiyat hatırlanır. */
    private static final double[] STARTER_PRICES = {130, 100, 70, 40, 30, 20};

    private double[] defaultPrices() {
        double[] out = new double[TezgahModel.GRADES.length];
        TezgahModel last = TezgahModel.decode(prefs.getString("buy_prices_" + owner, ""));
        for (int g = 0; g < out.length; g++) out[g] = last.price(g) > 0 ? last.price(g) : STARTER_PRICES[g];
        return out;
    }

    private void rememberBuyPrices() {
        TezgahModel p = new TezgahModel();
        for (int g = 0; g < TezgahModel.GRADES.length; g++) p.setPrice(g, model.price(g));
        prefs.edit().putString("buy_prices_" + owner, p.encode()).apply();
    }

    // ================================================================== ekran

    private void build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(this);
        mainScroll = scroll;
        scroll.setFillViewport(true);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(2), dp(12), dp(10));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        column.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        column.addView(bottomBar(), new LinearLayout.LayoutParams(-1, -2));

        // Sıkı düzen: her şey tek ekrana sığsın (gerekirse yine kayar).
        body.addView(header());
        if (voiceMode) {
            // Sesle tartım: sihirbaz yok; tartımın yazıldığı ekran (numara sekmeleri + kasa/kantar + tartımlar)
            // görünür, en üstte asistanın ses dalgası. Söylenen her tartım burada anında belirir.
            voiceBar = new LinearLayout(this);
            voiceBar.setGravity(Gravity.CENTER_VERTICAL);
            voiceBar.setPadding(dp(12), 0, dp(10), 0);
            voiceBar.setBackgroundResource(R.drawable.elek_voice_premium);
            body.addView(voiceBar, margins(-1, dp(64), 6));
            body.addView(gradeRow(), margins(-1, -2, 8));
            body.addView(entryCard(), margins(-1, -2, 8));
            body.addView(weighingsCard(), margins(-1, -2, 8));
            body.addView(summaryCard(), margins(-1, -2, 8));
            setContentView(root);
            refreshTotals();
            if (voice != null) voice.rebind(voiceBar);   // ekran yeniden kurulduysa ses dalgası kaybolmasın
            return;
        }
        // Her zaman sihirbazı açar: boşken "Yeni tartım başlat", yarım alımda "Tartıma devam et".
        body.addView(startButton(), margins(-1, dp(58), 6));
        // Sesle tart: kiloyu söyle, asistan kasa + dara + neti söyleyip tezgaha yazsın.
        body.addView(voiceButton(), margins(-1, dp(64), 8));
        // Tezgah boşken son fiş (numara numara döküm) hemen burada: satıcıyla teyit için.
        body.addView(lastSlipCard(), margins(-1, -2, 8));
        // Elle tartım kutusu (numara sekmeleri + kasa/kantar) kaldırıldı: tartım yalnız sihirbazla.
        // Özetteki numaraya dokununca o numaranın tartımları (silme ✕) aşağıda açılır.
        body.addView(weighingsCard(), margins(-1, -2, 8));
        body.addView(summaryCard(), margins(-1, -2, 8));
        // Cari hesap ve kasa Tezgâh ana ekranında; burada bugünkü alımlar (en yeni üstte).
        body.addView(purchasesCard(), margins(-1, -2, 8));
        setContentView(root);
        refreshTotals();
    }

    /** Başlık: geri · "Tezgah" + bağlam (bahçe / satan, tarih) · Satıyorum/Alıyorum anahtarı. */
    private View header() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(52));
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_elek_back_chevron);
        back.setColorFilter(TEXT);
        back.setPadding(dp(11), dp(11), dp(11), dp(11));
        back.setContentDescription("Geri");
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(40), dp(44));
        bp.setMarginStart(-dp(10));
        row.addView(back, bp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text("Tezgâh · Alım", 20, TEXT, true));
        // Satanın adı sihirbazın sonunda sorulur: ad yokken başlıkta yalnız tarih durur.
        String dayText = new SimpleDateFormat("d MMMM", new Locale("tr", "TR")).format(new Date());
        String context = seller.isEmpty() ? "" : "Satan: " + seller;
        // Tartım başladıysa ve satan yazılmadıysa belirgin sarı uyarı: unutulmasın (boş tezgahta yalnız tarih).
        boolean askName = seller.isEmpty() && !model.isEmpty();
        TextView sub = text(askName ? "👤 Satan yazılmadı ▾ · " + dayText
                        : seller.isEmpty() ? dayText : context + " ▾ · " + dayText, 13,
                askName ? AMBER : seller.isEmpty() ? MUTED : GREEN, true);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(sub);
        col.setContentDescription(seller.isEmpty() ? "Tezgah, " + dayText + ". Satanı yazmak için dokun" : context + ", değiştir");
        col.setOnClickListener(v -> askSeller(null));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

        // Bugünkü alımlar: sağ üstte küçük düğme (ayrı kart yer kaplamasın).
        TezgahPurchases.Day d;
        try { d = TezgahPurchases.openSummary(db.getWritableDatabase(), owner); }
        catch (RuntimeException e) { d = new TezgahPurchases.Day(); }
        {
            // Alım defteri her zaman açılabilir (önceki günler de orada).
            TextView day = text(d.count > 0 ? "Defter · " + d.count + " alım ›" : "Alım defteri ›", 13, GREEN, true);
            day.setGravity(Gravity.CENTER);
            day.setPadding(dp(12), 0, dp(12), 0);
            day.setBackground(round(CARD, d.remaining() > 0.5 ? AMBER : GREEN_DIM, 18));
            day.setContentDescription("Alım defterini aç. " + TezgahPurchases.dayLine(d));
            day.setOnClickListener(v -> showDayList());
            row.addView(day, new LinearLayout.LayoutParams(-2, dp(38)));
        }
        return row;
    }

    private void askReset() {
        if (model.isEmpty()) { toast("Tezgah zaten boş."); return; }
        new AlertDialog.Builder(this)
                .setTitle("Tezgah sıfırlansın mı?")
                .setMessage(model.items().size() + " tartım ve satan adı silinecek. Kaydedilmemiş bilgiler kaybolur.")
                .setPositiveButton("Sıfırla", (d, w) -> {
                    // Satan da silinir: yoksa sıradaki üreticinin alımı öncekinin adına (ve borcuna) yazılırdı.
                    model.clear(); seller = ""; sellerPhone = ""; saveDraft(); build();
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private View gradeRow() {
        LinearLayout row = new LinearLayout(this);
        for (int g = 0; g < TezgahModel.GRADES.length; g++) {
            final int index = g;
            TextView t = new TextView(this);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(13);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setMaxLines(2);
            t.setLineSpacing(0, 0.95f);
            t.setOnClickListener(v -> selectGrade(index));
            gradeTabs[g] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
            if (g > 0) lp.setMarginStart(dp(4));
            row.addView(t, lp);
        }
        renderGradeTabs();
        return row;
    }

    private TextView entryHead;

    /** Numara seçimi (sekme ya da özetteki satır): sekmeler, tartım listesi, özetteki vurgu ve düğme tazelenir. */
    private void selectGrade(int g) {
        grade = g;
        showAllWeighings = false;
        renderGradeTabs();
        replaceWeighings();
        if (!model.isEmpty()) replaceSummary();
        updatePreview();
    }

    private void renderEntryHead() {
        if (entryHead != null) {
            entryHead.setText(TezgahModel.LABELS[grade].toUpperCase(new Locale("tr", "TR")) + " · YENİ TARTIM");
        }
    }

    private void renderGradeTabs() {
        renderEntryHead();
        for (int g = 0; g < gradeTabs.length; g++) {
            TextView t = gradeTabs[g];
            if (t == null) continue;
            boolean on = g == grade;
            double net = model.gradeNet(g);
            boolean noPrice = net > 0 && model.price(g) <= 0;
            // Tartılan numara: altında kilo, yeşil yazı; fiyatı yoksa sarı çerçeve ve "fiyat?" (Bitir'den önce görülsün).
            String sub = net > 0 ? "\n" + (noPrice ? "fiyat?" : shortKg(net)) : "";
            t.setText(TezgahModel.SHORT[g] + sub);
            t.setTextColor(on ? BG : noPrice ? AMBER : net > 0 ? GREEN : TEXT);
            t.setBackground(on ? round(noPrice ? AMBER : GREEN, 0, 12)
                    : round(net > 0 ? GREEN_DIM : CARD, noPrice ? AMBER : net > 0 ? GREEN : LINE, 12));
            t.setContentDescription(TezgahModel.LABELS[g] + (net > 0 ? ", " + TezgahModel.kg(net) : ", tartılmadı")
                    + (noPrice ? ", fiyatı girilmedi" : "") + (on ? ", seçili" : ""));
        }
    }

    private View entryCard() {
        LinearLayout box = card();
        // Etiket satırı: Kasa · Kantar (kg) · Dara (seçili numara üstteki yeşil sekmede ve düğmede yazar).
        LinearLayout headRow = new LinearLayout(this);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        headRow.addView(text("Kasa", 12, MUTED, true), new LinearLayout.LayoutParams(dp(158), -2));
        headRow.addView(text("Kantar (kg)", 12, MUTED, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView tare = text("Dara " + TezgahModel.kg(model.tare()) + "/kasa ›", 13, GREEN, true);
        tare.setPadding(dp(8), dp(2), 0, dp(4));
        tare.setContentDescription("Kasa darası " + TezgahModel.kg(model.tare()) + ", değiştir");
        tare.setOnClickListener(v -> askTare());
        headRow.addView(tare);
        box.addView(headRow);
        renderEntryHead();

        LinearLayout inputs = new LinearLayout(this);
        inputs.setGravity(Gravity.CENTER_VERTICAL);

        // Kasa sayısı: − 10 +
        LinearLayout kasaCol = new LinearLayout(this);
        kasaCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout stepper = new LinearLayout(this);
        stepper.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus = stepButton("−");
        minus.setContentDescription("Kasa azalt");
        minus.setOnClickListener(v -> setKasa(kasa - 1));
        kasaInput = new EditText(this);
        styleInput(kasaInput, 24);
        kasaInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        kasaInput.setGravity(Gravity.CENTER);
        kasaInput.setText(String.valueOf(kasa));
        kasaInput.setSelectAllOnFocus(true);
        kasaInput.setContentDescription("Kasa sayısı");
        kasaInput.addTextChangedListener(watcher(() -> {
            kasa = (int) Math.max(0, Math.min(TezgahModel.MAX_KASA, TezgahModel.parse(kasaInput.getText().toString())));
            updatePreview();
        }));
        TextView plus = stepButton("+");
        plus.setContentDescription("Kasa artır");
        plus.setOnClickListener(v -> setKasa(kasa + 1));
        stepper.addView(minus, new LinearLayout.LayoutParams(dp(42), dp(52)));
        LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(dp(60), dp(52));
        kp.setMargins(dp(4), 0, dp(4), 0);
        stepper.addView(kasaInput, kp);
        stepper.addView(plus, new LinearLayout.LayoutParams(dp(42), dp(52)));
        kasaCol.addView(stepper);
        inputs.addView(kasaCol);

        LinearLayout kgCol = new LinearLayout(this);
        kgCol.setOrientation(LinearLayout.VERTICAL);
        kgInput = new EditText(this);
        styleInput(kgInput, 28);
        kgInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        kgInput.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
        kgInput.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        kgInput.setHint("kg yaz");
        kgInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        kgInput.setContentDescription("Kantarda okunan kilo");
        kgInput.addTextChangedListener(watcher(this::updatePreview));
        kgInput.setOnEditorActionListener((v, action, ev) -> {
            if (action == EditorInfo.IME_ACTION_DONE) { addWeighing(); return true; }
            return false;
        });
        kgCol.addView(kgInput, new LinearLayout.LayoutParams(-1, dp(52)));
        LinearLayout.LayoutParams kgp = new LinearLayout.LayoutParams(0, -2, 1);
        kgp.setMarginStart(dp(10));
        inputs.addView(kgCol, kgp);
        box.addView(inputs, margins(-1, -2, 2));

        // Yalnız hata/uyarı varken görünür; net kilo düğmenin üstünde yazar.
        netPreview = text("", 14, MUTED, true);
        netPreview.setMaxLines(2);
        box.addView(netPreview, margins(-1, -2, 4));

        TextView add = text("", 18, BG, true);
        addButton = add;
        add.setGravity(Gravity.CENTER);
        add.setBackground(round(GREEN, 0, 14));
        add.setContentDescription("Tartımı ekle");
        add.setOnClickListener(v -> addWeighing());
        box.addView(add, margins(-1, dp(50), 4));
        updatePreview();
        return box;
    }

    private void setKasa(int value) {
        kasa = Math.max(0, Math.min(TezgahModel.MAX_KASA, value));
        if (kasaInput != null) kasaInput.setText(String.valueOf(kasa));
        updatePreview();
    }

    private TextView addButton;

    private void updatePreview() {
        if (netPreview == null || kgInput == null) return;
        double kg = TezgahModel.parse(kgInput.getText().toString());
        String grade = TezgahModel.SHORT[this.grade];
        if (addButton != null) {
            String what = kasa > 0 ? kasa + " kasa" : "dökme";
            addButton.setText(kg > 0 && TezgahModel.addError(kasa, kg, model.tare()).isEmpty()
                    ? "+  " + grade + " · net " + TezgahModel.kg(TezgahModel.net(kasa, kg, model.tare()))
                    : "+  " + grade + " tartımı ekle (" + what + ")");
        }
        netPreview.setVisibility(View.VISIBLE);
        if (kg <= 0) {
            netPreview.setVisibility(View.GONE);
            return;
        }
        String err = TezgahModel.addError(kasa, kg, model.tare());
        if (!err.isEmpty()) {
            netPreview.setTextColor(RED);
            netPreview.setText(err);
        } else if (!TezgahModel.kasaWarning(kasa, kg, model.tare()).isEmpty()) {
            netPreview.setTextColor(AMBER);
            netPreview.setText(TezgahModel.kasaHint(kasa, kg, model.tare()));
        } else {
            netPreview.setVisibility(View.GONE);
        }
    }

    private void addWeighing() {
        double kg = TezgahModel.parse(kgInput.getText().toString());
        String err = TezgahModel.addError(kasa, kg, model.tare());
        if (!err.isEmpty()) {
            kgInput.setError(err);
            return;
        }
        final int suggested = TezgahModel.suggestedKasa(kasa, kg, model.tare());
        if (suggested > 0) {
            // Bir kasa 25–30 kg alır: kilo kasa sayısına uymuyor, kasa sayısı muhtemelen yanlış girildi.
            new AlertDialog.Builder(this)
                    .setTitle(kasa + " kasada " + TezgahModel.kg(kg) + " olamaz")
                    .setMessage(TezgahModel.kasaWarning(kasa, kg, model.tare()))
                    .setPositiveButton(suggested + " kasa yap ve ekle", (d, w) -> {
                        setKasa(suggested);
                        commitWeighing(suggested, kg);
                    })
                    .setNeutralButton("Yine de ekle", (d, w) -> commitWeighing(kasa, kg))
                    .setNegativeButton("Düzelteyim", (d, w) -> kasaInput.requestFocus())
                    .show();
            return;
        }
        commitWeighing(kasa, kg);
    }

    private void commitWeighing(int kasa, double kg) {
        if (!model.add(grade, kasa, kg)) {
            kgInput.setError(TezgahModel.addError(kasa, kg, model.tare()));
            return;
        }
        kgInput.setText("");
        saveDraft();
        if (lastSlipBox != null) lastSlipBox.setVisibility(View.GONE);
        if (seller.isEmpty() && body != null && body.getChildCount() > 0) {
            // İlk tartımla başlıkta "Satan yazılmadı" uyarısı belirsin.
            body.removeViewAt(0);
            body.addView(header(), 0);
        }
        renderGradeTabs();
        replaceWeighings();
        replaceSummary();
        refreshTotals();
        kgInput.requestFocus();
        toast(TezgahModel.LABELS[grade] + ": +" + TezgahModel.kg(TezgahModel.net(kasa, kg, model.tare())));
    }

    private LinearLayout weighingsBox;
    private boolean showAllWeighings;

    private void replaceWeighings() {
        if (weighingsBox == null) return;
        int at = body.indexOfChild(weighingsBox);
        if (at < 0) return;
        body.removeViewAt(at);
        body.addView(weighingsCard(), at, margins(-1, -2, 10));
    }

    private View weighingsCard() {
        LinearLayout box = card();
        box.setPadding(dp(12), dp(6), dp(4), dp(6));
        weighingsBox = box;
        List<TezgahModel.Weighing> all = model.items();
        int count = model.gradeCount(grade);
        if (count == 0) {
            // Tartım yokken yer kaplamasın.
            box.setVisibility(View.GONE);
            return box;
        }
        LinearLayout headRow = new LinearLayout(this);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        headRow.setMinimumHeight(dp(26));
        headRow.addView(text(TezgahModel.LABELS[grade] + " · " + count + " tartım", 14, TEXT, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        TextView sumText = text(model.gradeKasa(grade) + " kasa · net " + TezgahModel.kg(model.gradeNet(grade)), 14, GREEN, true);
        sumText.setPadding(0, 0, dp(10), 0);
        headRow.addView(sumText);
        box.addView(headRow);
        int n = 0, shown = 0;
        int limit = showAllWeighings ? Integer.MAX_VALUE : 2;
        for (int i = all.size() - 1; i >= 0; i--) {
            final TezgahModel.Weighing w = all.get(i);
            if (w.grade != grade) continue;
            n++;
            if (shown >= limit) continue;
            shown++;
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(32));
            TextView t = text(model.line(w), 15, TEXT, false);
            row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            TextView del = text("✕", 18, MUTED, true);
            del.setGravity(Gravity.CENTER);
            del.setContentDescription("Bu tartımı sil");
            del.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Tartım silinsin mi?")
                    .setMessage(model.line(w))
                    .setPositiveButton("Sil", (d, x) -> {
                        model.remove(index);
                        saveDraft();
                        build();
                    })
                    .setNegativeButton("Vazgeç", null)
                    .show());
            row.addView(del, new LinearLayout.LayoutParams(dp(40), dp(32)));
            box.addView(divider());
            box.addView(row);
        }
        if (n > shown) {
            TextView more = text("+ " + (n - shown) + " önceki tartımı göster", 13, GREEN, true);
            more.setPadding(0, dp(8), 0, dp(4));
            more.setOnClickListener(v -> { showAllWeighings = true; replaceWeighings(); });
            box.addView(more);
        }
        return box;
    }

    private LinearLayout summaryBox;

    private void replaceSummary() {
        if (summaryBox == null) return;
        int at = body.indexOfChild(summaryBox);
        if (at < 0) return;
        body.removeViewAt(at);
        body.addView(summaryCard(), at, margins(-1, -2, 10));
    }

    private View summaryCard() {
        if (model.isEmpty()) return priceStrip();
        LinearLayout box = card();
        summaryBox = box;
        box.setPadding(dp(12), dp(6), dp(12), dp(8));
        LinearLayout headRow = new LinearLayout(this);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView head = text("ÖZET · fiyata dokun, değiştir", 12, MUTED, true);
        head.setLetterSpacing(0.05f);
        headRow.addView(head, new LinearLayout.LayoutParams(0, -2, 1));
        headRow.addView(tareLink());
        if (!model.isEmpty()) {
            TextView reset = text("Sıfırla", 13, RED, true);
            reset.setPadding(dp(10), dp(6), 0, dp(6));
            reset.setContentDescription("Tezgahı sıfırla");
            reset.setOnClickListener(v -> askReset());
            headRow.addView(reset);
        }
        box.addView(headRow);
        for (int g = 0; g < TezgahModel.GRADES.length; g++) {
            final int index = g;
            double net = model.gradeNet(g);
            boolean on = g == grade;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(34));
            row.setPadding(dp(6), 0, dp(6), 0);
            // Seçili numaranın satırı vurgulu; satıra dokununca o numara seçilir (kilo kutusu ona yazar).
            if (on) row.setBackground(round(GREEN_DIM, GREEN, 10));
            row.setOnClickListener(v -> selectGrade(index));
            TextView name = text((on ? "▸ " : "") + TezgahModel.LABELS[g], 14, net > 0 || on ? TEXT : MUTED, true);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1.1f));
            // "48 kg ×" · [110 ₺] · "= 5.280 ₺": üretici hesabı satırdan okur; pay yüzdesi kilonun altında.
            LinearLayout kgCol = new LinearLayout(this);
            kgCol.setOrientation(LinearLayout.VERTICAL);
            kgCol.addView(text(net > 0 ? shortKg(net) + "  ×" : "—", 14, net > 0 ? TEXT : MUTED, net > 0));
            if (net > 0) kgCol.addView(text(TezgahModel.percent(model.share(g)) + " · " + model.gradeKasa(g) + " kasa", 11, MUTED, false));
            row.addView(kgCol, new LinearLayout.LayoutParams(0, -2, 1.2f));
            TextView price = text(model.price(g) > 0 ? TezgahModel.money(model.price(g)) : "fiyat?", 14,
                    model.price(g) > 0 ? TEXT : AMBER, true);
            price.setGravity(Gravity.CENTER);
            price.setPadding(dp(8), dp(3), dp(8), dp(3));
            price.setBackground(round(BG, LINE, 9));
            price.setContentDescription(TezgahModel.LABELS[g] + " fiyatını değiştir");
            price.setOnClickListener(v -> askPrice(index));
            row.addView(price, new LinearLayout.LayoutParams(-2, -2));
            TextView amount = text(net > 0 ? "= " + TezgahModel.tl(model.gradeAmount(g)) : "", 15, TEXT, true);
            amount.setGravity(Gravity.END);
            row.addView(amount, new LinearLayout.LayoutParams(0, -2, 1.2f));
            row.setContentDescription(TezgahModel.LABELS[g] + (net > 0 ? ", " + TezgahModel.kg(net) + " çarpı "
                    + (model.price(g) > 0 ? TezgahModel.money(model.price(g)) : "fiyat girilmedi") + ", "
                    + TezgahModel.tl(model.gradeAmount(g)) : ", tartılmadı") + (on ? ", seçili" : ", seçmek için dokun"));
            box.addView(row, margins(-1, -2, 2));
        }
        box.addView(divider(), margins(-1, Math.max(1, dp(1)), 4));
        LinearLayout foot = new LinearLayout(this);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        TextView totals = text(model.totalKasa() + " kasa · brüt " + TezgahModel.kg(model.totalGross())
                + " · dara " + TezgahModel.kg(model.totalTare()), 13, MUTED, false);
        totals.setSingleLine(true);
        totals.setEllipsize(android.text.TextUtils.TruncateAt.END);
        foot.addView(totals, new LinearLayout.LayoutParams(0, -2, 1));
        foot.addView(text("Net " + TezgahModel.kg(model.totalNet()), 16, TEXT, true));
        box.addView(foot, margins(-1, -2, 6));
        return box;
    }

    /** "Dara 2 kg ›": kasa darası (elle tartım kutusu kalktığı için fiyat/özet başlığında). */
    private TextView tareLink() {
        TextView t = text("Dara " + TezgahModel.kg(model.tare()) + " ›", 13, GREEN, true);
        t.setPadding(dp(10), dp(6), dp(4), dp(6));
        t.setContentDescription("Kasa darası " + TezgahModel.kg(model.tare()) + ", değiştir");
        t.setOnClickListener(v -> askTare());
        return t;
    }

    /** Tezgah boşken özetin yerine küçük fiyat çubuğu (6 boş satır yer kaplamasın); dokununca fiyat değişir. */
    private View priceStrip() {
        LinearLayout box = card();
        summaryBox = box;
        box.setPadding(dp(12), dp(8), dp(12), dp(10));
        TextView head = text("BUGÜNKÜ FİYATLAR · dokun, değiştir", 12, MUTED, true);
        head.setLetterSpacing(0.05f);
        LinearLayout headRow = new LinearLayout(this);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        headRow.addView(head, new LinearLayout.LayoutParams(0, -2, 1));
        headRow.addView(tareLink());
        box.addView(headRow);
        LinearLayout line = null;
        for (int g = 0; g < TezgahModel.GRADES.length; g++) {
            if (g % 3 == 0) { line = new LinearLayout(this); box.addView(line, margins(-1, -2, 6)); }
            final int index = g;
            boolean has = model.price(g) > 0;
            TextView c = text(TezgahModel.SHORT[g] + "  " + (has ? TezgahModel.money(model.price(g)) : "fiyat?"), 14,
                    has ? TEXT : AMBER, true);
            c.setGravity(Gravity.CENTER);
            c.setSingleLine(true);
            c.setBackground(round(BG, LINE, 10));
            c.setContentDescription(TezgahModel.LABELS[g] + " fiyatı " + (has ? TezgahModel.money(model.price(g)) : "girilmedi") + ", değiştir");
            c.setOnClickListener(v -> askPrice(index));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1);
            if (g % 3 > 0) lp.setMarginStart(dp(6));
            line.addView(c, lp);
        }
        return box;
    }

    private LinearLayout lastSlipBox;
    /** Son fiş kartında açık olan alım (0 = en son; "Son alımlar"dan seçilince değişir). */
    private int slipIndex = 0;

    /**
     * Tezgah boşken son fiş, kaydedildikten sonra 3 dakika açık durur (sonra "Son fiş · saat · Göster" satırına
     * iner; ad ve para yazmaz, sıradaki üretici görmesin): numara numara kasa / net kilo / fiyat / tutar, toplam,
     * ödendi ya da borç. Satıcıyla teyit edilir; küçük QR dokununca büyür, WhatsApp'tan fiş gider.
     * Altında son 3 alım; dokununca o fişin dökümü açılır. Yeni tartım başlayınca kart gizlenir.
     */
    private View lastSlipCard() {
        if (lastSlipBox != null && slipCollapse != null) lastSlipBox.removeCallbacks(slipCollapse);
        slipCollapse = null;
        slipFull = null;
        slipCompact = null;
        LinearLayout box = card();
        lastSlipBox = box;
        box.setVisibility(View.GONE);
        if (!model.isEmpty()) return box;
        final List<TezgahPurchases.Purchase> list;
        try { list = TezgahPurchases.open(db.getWritableDatabase(), owner); }
        catch (RuntimeException e) { return box; }
        if (list.isEmpty()) return box;
        if (slipIndex < 0 || slipIndex >= list.size()) slipIndex = 0;
        final TezgahPurchases.Purchase p = list.get(slipIndex);
        final TezgahModel m = TezgahModel.decode(p.data);
        if (m.isEmpty()) return box;
        box.setVisibility(View.VISIBLE);
        final LinearLayout full = new LinearLayout(this);
        full.setOrientation(LinearLayout.VERTICAL);
        final View compact = compactSlipRow(p, list.size());
        box.addView(compact);
        box.addView(full);
        final String who = p.seller.isEmpty() ? "Adsız üretici" : p.seller;
        // Aynı üreticinin bu fişten önceki alımlarından kalan borç: fişte "Önceki borç" + "GÜNCEL BORÇ".
        final double prevDebt = previousDebt(p.seller, p.createdAt, p.id);
        // Fişin tarihi alımın kendi saati (dünkü fiş bugünün tarihiyle gitmesin).
        final String receipt = m.receipt("Zeytin alım fişi", "Satan", p.seller, displayDate(p.createdAt), p.paid, prevDebt);
        String time = p.createdAt > 0 ? new SimpleDateFormat("HH:mm", Locale.US).format(new Date(p.createdAt)) : "";

        TextView kicker = text((slipIndex == 0 ? "SON FİŞ" : "FİŞ") + (time.isEmpty() ? "" : " · " + time)
                + " · SATICIYLA TEYİT ET", 11, MUTED, true);
        kicker.setLetterSpacing(0.06f);
        LinearLayout kickRow = new LinearLayout(this);
        kickRow.setGravity(Gravity.CENTER_VERTICAL);
        kickRow.addView(kicker, new LinearLayout.LayoutParams(0, -2, 1));
        TextView hide = text("Gizle", 13, GREEN, true);
        hide.setPadding(dp(12), dp(4), 0, dp(4));
        hide.setContentDescription("Son fişi gizle; sıradaki üretici görmesin");
        hide.setOnClickListener(v -> {
            slipManual = false;
            slipHiddenByUser = true;
            if (slipIndex != 0) { slipIndex = 0; build(); return; }
            setSlipOpen(false);
        });
        kickRow.addView(hide);
        full.addView(kickRow);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(who, 19, TEXT, true);
        name.setMaxLines(2);
        names.addView(name);
        names.addView(text(TezgahModel.tl(p.gross), 22, GREEN, true));
        top.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        int qrSize = dp(76);
        android.graphics.Bitmap bmp = TezgahQrUi.bitmap(TezgahQr.encode(m, p.date, traderName(), p.paid), qrSize);
        if (bmp != null) {
            ImageView img = new ImageView(this);
            img.setImageBitmap(bmp);
            img.setBackground(round(Color.WHITE, 0, 8));
            img.setPadding(dp(3), dp(3), dp(3), dp(3));
            img.setContentDescription("QR kodu büyüt; üretici Elek kaydında okutur");
            img.setOnClickListener(v -> showQr(m, p.date, p.seller, p.paid, receipt, p.phone));
            top.addView(img, new LinearLayout.LayoutParams(qrSize, qrSize));
        }
        full.addView(top, margins(-1, -2, 4));

        full.addView(divider(), margins(-1, Math.max(1, dp(1)), 8));
        StringBuilder spoken = new StringBuilder(who + ", " + TezgahModel.tl(p.gross) + ". ");
        for (int g = 0; g < TezgahModel.GRADES.length; g++) {
            if (m.gradeCount(g) == 0) continue;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(28));
            row.addView(text(TezgahModel.LABELS[g], 14, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1.05f));
            row.addView(text(m.gradeKasa(g) + " kasa", 13, MUTED, false), new LinearLayout.LayoutParams(0, -2, 0.75f));
            TextView kg = text(shortKg(m.gradeNet(g)) + " × " + (m.price(g) > 0 ? TezgahModel.money(m.price(g)).replace(" ₺", "") : "0"),
                    14, TEXT, false);
            row.addView(kg, new LinearLayout.LayoutParams(0, -2, 1.4f));
            TextView amount = text(TezgahModel.tl(m.gradeAmount(g)), 14, TEXT, true);
            amount.setGravity(Gravity.END);
            row.addView(amount, new LinearLayout.LayoutParams(0, -2, 1.1f));
            full.addView(row);
            spoken.append(TezgahModel.LABELS[g]).append(" ").append(m.gradeKasa(g)).append(" kasa ")
                    .append(TezgahModel.kg(m.gradeNet(g))).append(", ").append(TezgahModel.tl(m.gradeAmount(g))).append(". ");
        }
        full.addView(divider(), margins(-1, Math.max(1, dp(1)), 4));
        String avg = m.averagePrice() > 0 ? " · ort. " + TezgahModel.money(m.averagePrice()) + "/kg" : "";
        full.addView(text(m.totalKasa() + " kasa · net " + TezgahModel.kg(m.totalNet()) + avg, 13, MUTED, false), margins(-1, -2, 6));
        double left = Math.max(0, p.gross - p.paid);
        TextView pay = text(left < 0.5 ? "Ödendi ✓" : "Borç " + TezgahModel.tl(left)
                + (p.paid > 0.5 ? "  (ödenen " + TezgahModel.tl(p.paid) + ")" : ""), 15, left < 0.5 ? GREEN : AMBER, true);
        full.addView(pay, margins(-1, -2, 4));
        if (prevDebt > 0.5) {
            full.addView(text("Önceki borç " + TezgahModel.tl(prevDebt) + "  ·  Güncel borç " + TezgahModel.tl(prevDebt + left),
                    14, AMBER, true), margins(-1, -2, 2));
        }
        spoken.append(left < 0.5 ? "Ödendi." : "Borç " + TezgahModel.tl(left) + ".");
        full.setContentDescription(spoken.toString());

        LinearLayout actions = new LinearLayout(this);
        TextView qr = pill("QR göster", false);
        qr.setOnClickListener(v -> showQr(m, p.date, p.seller, p.paid, receipt, p.phone));
        actions.addView(qr, new LinearLayout.LayoutParams(0, dp(44), 1));
        TextView wa = pill("WhatsApp", false);
        wa.setContentDescription("Fişi WhatsApp'tan gönder");
        wa.setOnClickListener(v -> shareText(receipt, p.phone));
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, dp(44), 1);
        wp.setMarginStart(dp(8));
        actions.addView(wa, wp);
        TextView pr = pill("Yazdır", false);
        pr.setContentDescription("Fişi Bluetooth yazıcıdan yazdır; ayarlar için basılı tut");
        pr.setOnClickListener(v -> TezgahPrinter.printReceipt(this, w -> TezgahPrintFormat.receipt(m, traderName(), p.seller,
                displayDate(p.createdAt), p.paid, prevDebt, w), TezgahQr.encode(m, p.date, traderName(), p.paid)));
        pr.setOnLongClickListener(v -> { TezgahPrinter.settings(this); return true; });
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, dp(44), 1);
        pp.setMarginStart(dp(8));
        actions.addView(pr, pp);
        full.addView(actions, margins(-1, -2, 10));

        if (slipIndex != 0) {
            TextView latest = text("‹ Son fişe dön", 13, GREEN, true);
            latest.setPadding(0, dp(8), 0, 0);
            latest.setOnClickListener(v -> { slipIndex = 0; build(); });
            full.addView(latest);
        }
        slipFull = full;
        slipCompact = compact;
        long openLeft = TezgahWizardFlow.slipOpenLeft(p.createdAt, System.currentTimeMillis());
        boolean open = slipIndex != 0 || slipManual || (slipIndex == 0 && openLeft > 0 && !slipHiddenByUser);
        setSlipOpen(open);
        if (open && slipIndex == 0 && !slipManual) {
            // Süre dolunca kendiliğinden tek satıra iner (ekran yeniden kurulmaz, yazılan kilo kaybolmaz).
            slipCollapse = () -> { if (!slipManual && slipIndex == 0) setSlipOpen(false); };
            box.postDelayed(slipCollapse, openLeft);
        }
        return box;
    }

    private View slipFull, slipCompact;
    private Runnable slipCollapse;
    /** Kullanıcı "Göster" dedi: süre dolsa da açık kalır. */
    private boolean slipManual;
    /** Kullanıcı "Gizle" dedi: yeni fiş kaydedilene kadar kapalı kalır. */
    private boolean slipHiddenByUser;

    private void setSlipOpen(boolean open) {
        if (slipFull != null) slipFull.setVisibility(open ? View.VISIBLE : View.GONE);
        if (slipCompact != null) slipCompact.setVisibility(open ? View.GONE : View.VISIBLE);
    }

    /** Kapalı son fiş: ad ve tutar yazmaz, yalnız saat; dokununca döküm açılır. */
    private View compactSlipRow(TezgahPurchases.Purchase p, int count) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(40));
        String time = p.createdAt > 0 ? new SimpleDateFormat("HH:mm", Locale.US).format(new Date(p.createdAt)) : "";
        TextView t = text("🧾  Son fiş" + (time.isEmpty() ? "" : " · " + time)
                + (count > 1 ? " · " + count + " alım" : ""), 14, MUTED, true);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text("Göster ›", 14, GREEN, true));
        row.setContentDescription("Son fiş" + (time.isEmpty() ? "" : ", saat " + time) + ". Dökümü göstermek için dokun.");
        row.setOnClickListener(v -> { slipManual = true; slipHiddenByUser = false; setSlipOpen(true); });
        return row;
    }

    private ScrollView mainScroll;

    /**
     * Bugünkü (açık dönem) alımlar, en yeni üstte: saat, üretici, kilo, tutar, ödendi/borç.
     * Dokununca o fişin dökümü üstte açılır (tezgâh boşken); tartım sürerken Alım defteri açılır.
     */
    private View purchasesCard() {
        List<TezgahPurchases.Purchase> list;
        try { list = TezgahPurchases.open(db.getWritableDatabase(), owner); }
        catch (RuntimeException e) { list = new ArrayList<>(); }
        LinearLayout box = card();
        if (list.isEmpty()) { box.setVisibility(View.GONE); return box; }
        TezgahPurchases.Day d = TezgahPurchases.sum(list);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView h = text("BUGÜNKÜ ALIMLAR · " + list.size(), 12, MUTED, true);
        h.setLetterSpacing(0.05f);
        head.addView(h, new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(text(TezgahModel.kg(d.kg) + " · " + TezgahModel.tl(d.gross), 13, TEXT, true));
        box.addView(head);
        int max = Math.min(list.size(), 30);
        for (int i = 0; i < max; i++) {
            final int index = i;
            TezgahPurchases.Purchase o = list.get(i);
            box.addView(divider(), margins(-1, Math.max(1, dp(1)), 6));
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(48));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            String who = o.seller.isEmpty() ? "Adsız" : o.seller;
            TextView n = text(who, 15, TEXT, true);
            n.setSingleLine(true);
            n.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(n);
            String time = o.createdAt > 0 ? new SimpleDateFormat("HH:mm", Locale.US).format(new Date(o.createdAt)) : "";
            col.addView(text((time.isEmpty() ? "" : time + " · ") + shortKg(o.kg) + " · " + o.kasa + " kasa", 12, MUTED, false));
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            right.addView(text(TezgahModel.tl(o.gross), 15, TEXT, true));
            boolean paid = o.remaining() < 0.5;
            right.addView(text(paid ? "Ödendi ✓" : "Borç " + TezgahModel.tl(o.remaining()), 12, paid ? GREEN : AMBER, true));
            row.addView(right);
            row.setContentDescription(who + ", " + time + ", " + TezgahModel.kg(o.kg) + ", " + TezgahModel.tl(o.gross)
                    + (paid ? ", ödendi" : ", borç var") + ". Fişi açmak için dokun.");
            row.setOnClickListener(v -> {
                if (!model.isEmpty()) { showDayList(); return; }
                slipIndex = index;
                slipManual = true;
                slipHiddenByUser = false;
                build();
                if (mainScroll != null) mainScroll.post(() -> mainScroll.smoothScrollTo(0, 0));
            });
            box.addView(row);
        }
        if (list.size() > max) {
            TextView more = text("+ " + (list.size() - max) + " alım daha · Alım defteri ›", 13, GREEN, true);
            more.setPadding(0, dp(10), 0, dp(2));
            more.setOnClickListener(v -> showDayList());
            box.addView(more);
        }
        return box;
    }

    private View bottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0A1A24"));
        bg.setStroke(dp(1), LINE);
        bar.setBackground(bg);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        totalSub = text("", 12, MUTED, true);
        totalView = text("", 28, GREEN, true);
        totalView.setSingleLine(true);
        col.addView(totalSub);
        col.addView(totalView);
        bar.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        finishButton = text("Bitir ›", 19, BG, true);
        finishButton.setGravity(Gravity.CENTER);
        finishButton.setPadding(dp(22), 0, dp(22), 0);
        finishButton.setOnClickListener(v -> finishTezgah());
        bar.addView(finishButton, new LinearLayout.LayoutParams(-2, dp(52)));
        return bar;
    }

    private void refreshTotals() {
        if (totalView == null) return;
        boolean empty = model.isEmpty();
        double avg = model.averagePrice();
        totalSub.setText(empty ? "ÖDEYECEĞİN · önce bir tartım ekle"
                : avg > 0 ? "ÖDEYECEĞİN · ort. " + TezgahModel.money(avg) + "/kg" : "ÖDEYECEĞİN");
        totalView.setText(TezgahModel.tl(model.total()));
        totalView.setContentDescription("Ödeyeceğin " + TezgahModel.tl(model.total())
                + (avg > 0 ? ", kilo başı ortalama " + TezgahModel.money(avg) : ""));
        // Boşken de dokunulur: neden geçmediğini söyler (finishTezgah "Önce en az bir tartım ekle").
        finishButton.setEnabled(true);
        finishButton.setBackground(round(empty ? LINE : GREEN, 0, 18));
        finishButton.setTextColor(empty ? MUTED : BG);
        finishButton.setContentDescription(empty ? "Bitir; önce bir tartım eklemelisin" : "Bitir ve alım fişini kaydet");
    }

    // ================================================================== diyaloglar

    private void askPrice(int g) {
        final EditText input = dialogInput(model.price(g) > 0 ? TezgahModel.money(model.price(g)).replace(" ₺", "") : "",
                "Kilo fiyatı (₺)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        new AlertDialog.Builder(this)
                .setTitle(TezgahModel.LABELS[g] + " fiyatı (₺/kg)")
                .setView(wrap(input))
                .setPositiveButton("Tamam", (d, w) -> {
                    double v = TezgahModel.parse(input.getText().toString());
                    if (v < 0 || v > 100000) { toast("Fiyatı kontrol et."); return; }
                    model.setPrice(g, v);
                    rememberBuyPrices();
                    saveDraft();
                    replaceSummary();
                    refreshTotals();
                    if (wizard != null && wizardStep == g) renderWizard();
                })
                .setNegativeButton("Vazgeç", null)
                .show();
        showKeyboard(input);
    }

    private void askTare() {
        final EditText input = dialogInput(TezgahModel.kg(model.tare()).replace(" kg", ""),
                "Kasa başına kg", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        new AlertDialog.Builder(this)
                .setTitle("Kasa darası")
                .setMessage("Her kasa için düşülecek kilo. Bütün tartımlar yeniden hesaplanır.")
                .setView(wrap(input))
                .setPositiveButton("Tamam", (d, w) -> {
                    double v = TezgahModel.parse(input.getText().toString());
                    if (v < 0 || v > TezgahModel.MAX_TARE) { toast("Dara 0–10 kg arası olmalı."); return; }
                    model.setTare(v);
                    saveDraft();
                    build();
                })
                .setNegativeButton("Vazgeç", null)
                .show();
        showKeyboard(input);
    }

    /** Tüccar: satan üreticinin adı ve telefonu. then != null ise kaydettikten sonra çalışır. */
    private void askSeller(Runnable then) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText name = dialogInput(seller, "Üreticinin adı", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final EditText phone = dialogInput(sellerPhone, "Telefon (fiş WhatsApp'tan gitsin)", InputType.TYPE_CLASS_PHONE);
        form.addView(name);
        form.addView(phone, margins(-1, -2, 8));
        new AlertDialog.Builder(this)
                .setTitle("Satan üretici")
                .setView(form)
                .setPositiveButton("Tamam", (d, w) -> {
                    seller = name.getText().toString().trim();
                    sellerPhone = phone.getText().toString().trim();
                    saveDraft();
                    build();
                    if (then != null) then.run();
                })
                .setNegativeButton("Vazgeç", null)
                .show();
        showKeyboard(name);
    }

    // ================================================================== bitir

    private void finishTezgah() {
        if (model.isEmpty()) { toast("Önce en az bir tartım ekle."); return; }
        List<String> missing = model.missingPrices();
        if (!missing.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Fiyatı girilmemiş numara var")
                    .setMessage(android.text.TextUtils.join(", ", missing)
                            + " için fiyat yok; tutara 0 ₺ olarak girer. Yine de devam edilsin mi?")
                    .setPositiveButton("Devam et", (d, w) -> askSellerThenPay())
                    .setNegativeButton("Fiyat gir", null)
                    .show();
            return;
        }
        askSellerThenPay();
    }

    /**
     * Satan hiç sorulmadıysa (ad boş) önce sihirbazın "Kimden aldın?" adımı açılır: son gelenler, ad, telefon,
     * "Geç · münferit". Sonra ödeme penceresi. Ad yazılmış ya da münferit seçilmişse doğrudan ödemeye geçer.
     */
    private void askSellerThenPay() {
        if (!seller.isEmpty()) { askPayment(); return; }
        wizardPayAfter = true;
        openWizard(TezgahWizardFlow.SELLER);
    }

    /** Sihirbaz Bitir'den satanı sormak için açıldıysa: satan adımından sonra fiyat kontrolü tekrarlanmaz. */
    private boolean wizardPayAfter;

    /**
     * "Alımı bitir" alttan açılan sayfası: büyük toplam, satan üretici (ad + telefon doğrudan burada),
     * ödeme seçimi (Tamamı / Bir kısmı / Ödenmedi; kısmide kalan canlı) ve iki net düğme.
     */
    private void askPayment() {
        final double total = model.total();
        final android.app.Dialog sheet = new android.app.Dialog(this);
        sheet.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(10), dp(20), dp(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0C1C27"));
        float r = dp(26);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        bg.setStroke(dp(1), LINE);
        scroll.setBackground(bg);
        scroll.addView(box);

        View handle = new View(this);
        handle.setBackground(round(LINE, 0, 3));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(44), dp(5));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(handle, hp);

        // Başlık + büyük toplam
        TextView kicker = text("ALIMI BİTİR", 12, MUTED, true);
        kicker.setLetterSpacing(0.1f);
        box.addView(kicker, margins(-1, -2, 14));
        TextView big = text(TezgahModel.tl(total), 38, GREEN, true);
        box.addView(big, margins(-1, -2, 2));
        TextView facts = text(TezgahModel.kg(model.totalNet()) + "  ·  " + model.totalKasa() + " kasa"
                + (model.averagePrice() > 0 ? "  ·  ort. " + TezgahModel.money(model.averagePrice()) + "/kg" : ""),
                15, TEXT, false);
        box.addView(facts, margins(-1, -2, 2));

        // Satan üretici: doğrudan bu sayfada yazılır
        box.addView(sectionLabel("SATAN ÜRETİCİ"), margins(-1, -2, 20));
        final EditText name = sheetField(seller, "Ad soyad (isteğe bağlı)",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        box.addView(name, margins(-1, dp(54), 8));
        final EditText phone = sheetField(sellerPhone, "Telefon · fiş WhatsApp'tan gitsin", InputType.TYPE_CLASS_PHONE);
        box.addView(phone, margins(-1, dp(54), 8));

        // Ödeme
        box.addView(sectionLabel("ÖDEME"), margins(-1, -2, 20));
        final String[] labels = {"Tamamı", "Bir kısmı", "Ödenmedi"};
        final int[] pay = {ElekSaleModel.PAY_ALL};
        final TextView[] chips = new TextView[3];
        final EditText partial = sheetField("", "Ödenen tutar (₺)",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final TextView rest = text("", 14, AMBER, true);
        LinearLayout seg = new LinearLayout(this);
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        seg.setBackground(round(CARD, LINE, 16));
        final Runnable paint = () -> {
            for (int k = 0; k < 3; k++) {
                boolean on = k == pay[0];
                chips[k].setTextColor(on ? BG : TEXT);
                chips[k].setBackground(on ? round(k == ElekSaleModel.PAY_NONE ? AMBER : GREEN, 0, 12) : null);
                chips[k].setContentDescription(labels[k] + (on ? ", seçili" : ""));
            }
            boolean part = pay[0] == ElekSaleModel.PAY_PART;
            partial.setVisibility(part ? View.VISIBLE : View.GONE);
            double paid = ElekSaleModel.received(pay[0], total, TezgahModel.parse(partial.getText().toString()));
            double left = Math.max(0, total - paid);
            rest.setVisibility(left > 0.5 ? View.VISIBLE : View.GONE);
            rest.setText("Üreticiye borç kalacak: " + TezgahModel.tl(left));
        };
        for (int i = 0; i < 3; i++) {
            final int option = i;
            TextView c = text(labels[i], 16, TEXT, true);
            c.setGravity(Gravity.CENTER);
            c.setOnClickListener(v -> { pay[0] = option; paint.run(); if (option == ElekSaleModel.PAY_PART) showKeyboard(partial); });
            chips[i] = c;
            seg.addView(c, new LinearLayout.LayoutParams(0, dp(46), 1));
        }
        box.addView(seg, margins(-1, -2, 8));
        partial.addTextChangedListener(watcher(paint));
        box.addView(partial, margins(-1, dp(54), 8));
        box.addView(rest, margins(-1, -2, 8));
        paint.run();

        // Düğmeler
        TextView qr = text("Kaydet  ·  QR göster", 18, BG, true);
        qr.setGravity(Gravity.CENTER);
        qr.setBackground(round(GREEN, 0, 18));
        box.addView(qr, margins(-1, dp(58), 22));
        TextView wa = text("Kaydet  ·  WhatsApp'a gönder", 16, GREEN, true);
        wa.setGravity(Gravity.CENTER);
        wa.setBackground(round(Color.TRANSPARENT, GREEN, 18));
        box.addView(wa, margins(-1, dp(52), 10));
        TextView cancel = text("Vazgeç", 15, MUTED, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setOnClickListener(v -> sheet.dismiss());
        box.addView(cancel, margins(-1, dp(46), 4));

        View.OnClickListener go = v -> {
            double part = TezgahModel.parse(partial.getText().toString());
            String err = ElekSaleModel.partialError(pay[0], total, part);
            if (!err.isEmpty()) { partial.setError(err); partial.requestFocus(); return; }
            final double paid = ElekSaleModel.received(pay[0], total, part);
            final boolean toQr = v == qr;
            Runnable save = () -> {
                // Ad yazılmadıysa fişte "Münferit satıcı" yazar.
                seller = TezgahWizardFlow.sellerOrWalkIn(name.getText().toString());
                sellerPhone = phone.getText().toString().trim();
                sheet.dismiss();
                savePurchase(paid, toQr);
            };
            String typed = name.getText().toString().trim();
            boolean noName = typed.isEmpty() || typed.equalsIgnoreCase(TezgahWizardFlow.WALK_IN);
            if (total - paid > 0.5 && noName) {
                // Borç kalıyor ama ad yok: sonra Alım defterinde adıyla bulunamaz.
                new AlertDialog.Builder(this)
                        .setTitle("Borç kalıyor, ad yazılmadı")
                        .setMessage("Üreticiye " + TezgahModel.tl(total - paid) + " borç kalacak. Adını yazmazsan geldiğinde "
                                + "Alım defterinde adıyla bulamazsın (tutar ya da telefonla aranabilir).")
                        .setPositiveButton("Ad yazayım", (d, w) -> { name.setText(""); showKeyboard(name); })
                        .setNegativeButton("Münferit kaydet", (d, w) -> save.run())
                        .show();
                return;
            }
            save.run();
        };
        qr.setOnClickListener(go);
        wa.setOnClickListener(go);

        sheet.setContentView(scroll);
        android.view.Window w = sheet.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        sheet.show();
    }

    // ================================================================== tartım sihirbazı

    /**
     * "Tartım başlat": küçük pencere sırayla Double → 1 → 2 → 3 → 4 → Yağlık'ı, en son satan üreticiyi sorar.
     * Her numarada kasa + kantar kilosu; "Ekle ›" sonrakine geçer, "Aynı numaradan bir tartım daha" aynı
     * numarada kalır, "Geç" numarayı atlar. Arka plandaki tezgah (sekmeler, özet, toplam) canlı güncellenir.
     * Satandan sonra "Alımı bitir" (ödeme) penceresi açılır; ad orada da dolu gelir.
     */
    private View startButton() {
        boolean cont = !model.isEmpty();
        TextView b = text(cont ? "▶  Tartıma devam et" : "▶  Yeni tartım başlat", 20, BG, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(round(GREEN, 0, 18));
        b.setContentDescription(cont ? "Tartıma devam et: numara numara sorar" : "Tartım başlat: numara numara, en son satanı sorar");
        b.setOnClickListener(v -> openWizard());
        return b;
    }

    private android.app.Dialog wizard;
    private LinearLayout wizardBox;
    private int wizardStep;

    private void openWizard() {
        wizardPayAfter = false;
        openWizard(TezgahWizardFlow.start(model));
    }

    private void openWizard(int firstStep) {
        wizardStep = firstStep;
        if (model.isEmpty()) { grade = 0; renderGradeTabs(); replaceWeighings(); updatePreview(); }
        wizard = new android.app.Dialog(this);
        wizard.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(this);
        LinearLayout wizardColumn = new LinearLayout(this);
        wizardColumn.setOrientation(LinearLayout.VERTICAL);
        // Sesle tartımda en üstte asistanın ses dalgası durur (adımlar değişse de yerinde kalır).
        wizardVoiceBar = new LinearLayout(this);
        wizardVoiceBar.setGravity(Gravity.CENTER_VERTICAL);
        wizardVoiceBar.setPadding(dp(12), 0, dp(10), 0);
        wizardVoiceBar.setBackgroundResource(R.drawable.elek_voice_premium);
        wizardVoiceBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams vb = new LinearLayout.LayoutParams(-1, dp(60));
        vb.setMargins(dp(14), dp(14), dp(14), 0);
        wizardColumn.addView(wizardVoiceBar, vb);
        wizardBox = new LinearLayout(this);
        wizardBox.setOrientation(LinearLayout.VERTICAL);
        wizardBox.setPadding(dp(18), dp(14), dp(18), dp(16));
        wizardColumn.addView(wizardBox);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0C1C27"));
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(1), LINE);
        scroll.setBackground(bg);
        scroll.addView(wizardColumn);
        wizard.setContentView(scroll);
        android.view.Window w = wizard.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            int width = Math.round(getResources().getDisplayMetrics().widthPixels * 0.94f);
            w.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
            w.setDimAmount(0.55f);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        wizard.setOnDismissListener(d -> { if (voice != null) voice.close(); wizard = null; wizardPayAfter = false; saveDraft(); build(); });
        renderWizard();
        wizard.show();
    }

    private void goWizard(int step) {
        if (step >= TezgahWizardFlow.DONE) {
            Runnable done = () -> {
                boolean payNow = wizardPayAfter;
                wizardPayAfter = false;
                if (model.isEmpty()) toast("Tartım eklenmedi.");
                else if (payNow) askPayment();
                else finishTezgah();
            };
            if (wizard != null) { android.app.Dialog d = wizard; d.setOnDismissListener(x -> { wizard = null; saveDraft(); build(); done.run(); }); d.dismiss(); }
            return;
        }
        wizardStep = step;
        renderWizard();
    }

    private void renderWizard() {
        if (wizardBox == null) return;
        wizardBox.removeAllViews();
        // Üst satır: ‹ Geri · adım · ✕
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        boolean canBack = wizardStep > 0;
        TextView back = text("‹ Geri", 15, canBack ? GREEN : LINE, true);
        back.setPadding(0, dp(8), dp(12), dp(8));
        back.setEnabled(canBack);
        back.setContentDescription("Önceki adıma dön");
        back.setOnClickListener(v -> goWizard(TezgahWizardFlow.back(wizardStep)));
        top.addView(back);
        TextView prog = text(TezgahWizardFlow.progress(wizardStep), 13, MUTED, true);
        prog.setGravity(Gravity.CENTER);
        top.addView(prog, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = text("✕", 18, MUTED, true);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Pencereyi kapat; eklenen tartımlar kalır");
        close.setOnClickListener(v -> { if (wizard != null) wizard.dismiss(); });
        top.addView(close, new LinearLayout.LayoutParams(dp(44), dp(40)));
        wizardBox.addView(top);
        if (wizardStep >= TezgahWizardFlow.SELLER) renderWizardSeller();
        else renderWizardGrade(wizardStep);
    }

    private void renderWizardSeller() {
        wizardBox.addView(text("Kimden aldın?", 24, TEXT, true), margins(-1, -2, 6));
        wizardBox.addView(text(TezgahModel.kg(model.totalNet()) + " · " + model.totalKasa() + " kasa · "
                + TezgahModel.tl(model.total()), 15, GREEN, true), margins(-1, -2, 2));
        final EditText name = sheetField(seller.equals(TezgahWizardFlow.WALK_IN) ? "" : seller, "Üreticinin adı",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        wizardBox.addView(name, margins(-1, dp(56), 12));
        final EditText phone = sheetField(sellerPhone, "Telefon (isteğe bağlı · fiş WhatsApp'tan)", InputType.TYPE_CLASS_PHONE);
        wizardBox.addView(phone, margins(-1, dp(52), 8));
        List<String> names = new ArrayList<>();
        try { for (TezgahPurchases.Purchase p : TezgahPurchases.open(db.getWritableDatabase(), owner)) names.add(p.seller); }
        catch (RuntimeException ignored) { }
        List<String> recent = TezgahWizardFlow.recent(names, 4);
        if (!recent.isEmpty()) {
            wizardBox.addView(text("Son gelenler", 12, MUTED, true), margins(-1, -2, 12));
            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.VERTICAL);
            LinearLayout line = null;
            for (int i = 0; i < recent.size(); i++) {
                if (i % 2 == 0) { line = new LinearLayout(this); chips.addView(line, margins(-1, -2, i == 0 ? 0 : 6)); }
                final String n = recent.get(i);
                TextView c = text(n, 15, TEXT, true);
                c.setGravity(Gravity.CENTER);
                c.setSingleLine(true);
                c.setEllipsize(android.text.TextUtils.TruncateAt.END);
                c.setPadding(dp(10), 0, dp(10), 0);
                c.setBackground(round(CARD, LINE, 20));
                c.setOnClickListener(v -> { name.setText(n); name.setSelection(n.length()); });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1);
                if (i % 2 == 1) lp.setMarginStart(dp(8));
                line.addView(c, lp);
            }
            wizardBox.addView(chips, margins(-1, -2, 6));
        }
        TextView go = text("Devam  ·  ödemeye geç  ›", 18, BG, true);
        go.setGravity(Gravity.CENTER);
        go.setBackground(round(GREEN, 0, 18));
        go.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) { name.setError("Ad yaz ya da \"Geç\" de"); name.requestFocus(); return; }
            seller = n;
            sellerPhone = phone.getText().toString().trim();
            saveDraft();
            goWizard(TezgahWizardFlow.DONE);
        });
        wizardBox.addView(go, margins(-1, dp(56), 16));
        TextView skip = text("Geç  ·  münferit satıcı", 16, GREEN, true);
        skip.setGravity(Gravity.CENTER);
        skip.setBackground(round(Color.TRANSPARENT, GREEN, 18));
        skip.setContentDescription("Adı geç, münferit satıcı olarak devam et");
        skip.setOnClickListener(v -> {
            seller = TezgahWizardFlow.WALK_IN;
            sellerPhone = phone.getText().toString().trim();
            saveDraft();
            goWizard(TezgahWizardFlow.DONE);
        });
        wizardBox.addView(skip, margins(-1, dp(50), 8));
        if (model.isEmpty()) {
            // Hiç tartım yoksa ödemeye geçilmez: geri dönüp tartım eklemesi istenir.
            go.setText("Önce tartım ekle  ‹");
            go.setOnClickListener(v -> goWizard(0));
            skip.setVisibility(View.GONE);
        } else if (seller.isEmpty()) showKeyboard(name);
    }

    private EditText wizardKg, wizardKasaInput;
    private TextView wizardNote, wizardAdd;

    private void renderWizardGrade(final int g) {
        // Başlık: numara + fiyat (dokununca değişir)
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text(TezgahModel.LABELS[g], 28, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView price = text(model.price(g) > 0 ? TezgahModel.money(model.price(g)) + "/kg ›" : "fiyat? ›", 15,
                model.price(g) > 0 ? GREEN : AMBER, true);
        price.setPadding(dp(10), dp(8), 0, dp(8));
        price.setContentDescription(TezgahModel.LABELS[g] + " fiyatını değiştir");
        price.setOnClickListener(v -> askPrice(g));
        head.addView(price);
        wizardBox.addView(head, margins(-1, -2, 4));
        int count = model.gradeCount(g);
        TextView sofar = text(count > 0 ? "Bu numarada " + count + " tartım · " + model.gradeKasa(g) + " kasa · net "
                + TezgahModel.kg(model.gradeNet(g)) : "Satan: " + (seller.isEmpty() ? TezgahWizardFlow.WALK_IN : seller),
                13, count > 0 ? GREEN : MUTED, true);
        if (count == 0) sofar.setText("Kantara koy, kasa ve kiloyu yaz");
        wizardBox.addView(sofar, margins(-1, -2, 2));

        LinearLayout labels = new LinearLayout(this);
        labels.addView(text("Kasa adedi", 12, MUTED, true), new LinearLayout.LayoutParams(dp(158), -2));
        labels.addView(text("Kantar (kg)", 12, MUTED, true), new LinearLayout.LayoutParams(0, -2, 1));
        wizardBox.addView(labels, margins(-1, -2, 14));
        LinearLayout inputs = new LinearLayout(this);
        inputs.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus = stepButton("−");
        minus.setContentDescription("Kasa azalt");
        wizardKasaInput = new EditText(this);
        styleInput(wizardKasaInput, 24);
        wizardKasaInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        wizardKasaInput.setGravity(Gravity.CENTER);
        wizardKasaInput.setText(String.valueOf(kasa));
        wizardKasaInput.setSelectAllOnFocus(true);
        wizardKasaInput.setContentDescription("Kasa adedi");
        TextView plus = stepButton("+");
        plus.setContentDescription("Kasa artır");
        minus.setOnClickListener(v -> wizardKasaInput.setText(String.valueOf(Math.max(0, kasa - 1))));
        plus.setOnClickListener(v -> wizardKasaInput.setText(String.valueOf(Math.min(TezgahModel.MAX_KASA, kasa + 1))));
        inputs.addView(minus, new LinearLayout.LayoutParams(dp(42), dp(56)));
        LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(dp(60), dp(56));
        kp.setMargins(dp(4), 0, dp(4), 0);
        inputs.addView(wizardKasaInput, kp);
        inputs.addView(plus, new LinearLayout.LayoutParams(dp(42), dp(56)));
        wizardKg = new EditText(this);
        styleInput(wizardKg, 30);
        wizardKg.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        wizardKg.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
        wizardKg.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        wizardKg.setHint("kg yaz");
        wizardKg.setImeOptions(EditorInfo.IME_ACTION_DONE);
        wizardKg.setContentDescription(TezgahModel.LABELS[g] + " kantar kilosu");
        wizardKg.setOnEditorActionListener((v, action, ev) -> {
            if (action == EditorInfo.IME_ACTION_DONE) { wizardAddWeighing(g, false); return true; }
            return false;
        });
        LinearLayout.LayoutParams kgp = new LinearLayout.LayoutParams(0, dp(56), 1);
        kgp.setMarginStart(dp(10));
        inputs.addView(wizardKg, kgp);
        wizardBox.addView(inputs, margins(-1, -2, 4));
        wizardNote = text("", 14, MUTED, true);
        wizardNote.setMaxLines(3);
        wizardBox.addView(wizardNote, margins(-1, -2, 6));

        wizardAdd = text("", 18, BG, true);
        wizardAdd.setGravity(Gravity.CENTER);
        wizardAdd.setBackground(round(GREEN, 0, 16));
        wizardAdd.setOnClickListener(v -> wizardAddWeighing(g, false));
        wizardBox.addView(wizardAdd, margins(-1, dp(56), 10));
        TextView again = text("+  Aynı numaradan bir tartım daha", 15, GREEN, true);
        again.setGravity(Gravity.CENTER);
        again.setBackground(round(Color.TRANSPARENT, GREEN, 16));
        again.setContentDescription("Ekle ve " + TezgahModel.LABELS[g] + " numarasında kal");
        again.setOnClickListener(v -> wizardAddWeighing(g, true));
        wizardBox.addView(again, margins(-1, dp(48), 8));
        TextView skip = text(count > 0 ? "Bu numara bitti  ·  sonraki ›"
                : "Geç  ·  " + TezgahModel.SHORT[g] + " yok ›", 15, TEXT, true);
        skip.setGravity(Gravity.CENTER);
        skip.setBackground(round(CARD, LINE, 16));
        skip.setOnClickListener(v -> goWizard(TezgahWizardFlow.next(g)));
        wizardBox.addView(skip, margins(-1, dp(48), 8));

        Runnable refresh = () -> {
            kasa = (int) Math.max(0, Math.min(TezgahModel.MAX_KASA, TezgahModel.parse(wizardKasaInput.getText().toString())));
            double kg = TezgahModel.parse(wizardKg.getText().toString());
            String what = kasa > 0 ? kasa + " kasa" : "dökme";
            boolean ok = kg > 0 && TezgahModel.addError(kasa, kg, model.tare()).isEmpty();
            // Kilo yazılmadan ana düğme soluk durur: geçmenin tek yolu "Geç" düğmesi.
            wizardAdd.setText(ok ? "+  Ekle · net " + TezgahModel.kg(TezgahModel.net(kasa, kg, model.tare())) + "  ›"
                    : kg > 0 ? "+  " + TezgahModel.SHORT[g] + " tartımı ekle (" + what + ")  ›" : "Kiloyu yaz");
            wizardAdd.setTextColor(kg > 0 ? BG : MUTED);
            wizardAdd.setBackground(round(kg > 0 ? GREEN : CARD, kg > 0 ? 0 : LINE, 16));
            wizardAdd.setContentDescription(kg > 0 ? "Tartımı ekle ve sonraki numaraya geç" : "Önce kantar kilosunu yaz");
            if (kg <= 0) { wizardNote.setVisibility(View.GONE); return; }
            String err = TezgahModel.addError(kasa, kg, model.tare());
            wizardNote.setVisibility(View.VISIBLE);
            if (!err.isEmpty()) { wizardNote.setTextColor(RED); wizardNote.setText(err); }
            else if (!TezgahModel.kasaWarning(kasa, kg, model.tare()).isEmpty()) {
                wizardNote.setTextColor(AMBER); wizardNote.setText(TezgahModel.kasaHint(kasa, kg, model.tare()));
            } else {
                wizardNote.setTextColor(MUTED);
                wizardNote.setText("Dara " + TezgahModel.kg(model.tare()) + "/kasa düşülür");
            }
        };
        wizardKasaInput.addTextChangedListener(watcher(refresh));
        wizardKg.addTextChangedListener(watcher(refresh));
        refresh.run();
        if (!voiceOpen()) showKeyboard(wizardKg);   // sesle tartımda klavye açılmaz
    }

    /** stay=true: ekledikten sonra aynı numarada kalır; false: sonraki numaraya geçer. */
    private void wizardAddWeighing(final int g, final boolean stay) {
        final double kg = TezgahModel.parse(wizardKg.getText().toString());
        if (kg <= 0) {
            // Kilo yoksa eklenmez ve geçilmez; geçmek için "Geç" düğmesi var.
            wizardKg.setError("Kiloyu yaz");
            showKeyboard(wizardKg);
            return;
        }
        String err = TezgahModel.addError(kasa, kg, model.tare());
        if (!err.isEmpty()) { wizardKg.setError(err); return; }
        final int suggested = TezgahModel.suggestedKasa(kasa, kg, model.tare());
        if (suggested > 0) {
            new AlertDialog.Builder(this)
                    .setTitle(kasa + " kasada " + TezgahModel.kg(kg) + " olamaz")
                    .setMessage(TezgahModel.kasaWarning(kasa, kg, model.tare()))
                    .setPositiveButton(suggested + " kasa yap ve ekle", (d, w) -> wizardCommit(g, suggested, kg, stay))
                    .setNeutralButton("Yine de ekle", (d, w) -> wizardCommit(g, kasa, kg, stay))
                    .setNegativeButton("Düzelteyim", (d, w) -> wizardKasaInput.requestFocus())
                    .show();
            return;
        }
        wizardCommit(g, kasa, kg, stay);
    }

    private void wizardCommit(int g, int k, double kg, boolean stay) {
        if (!model.add(g, k, kg)) { wizardKg.setError(TezgahModel.addError(k, kg, model.tare())); return; }
        kasa = k;
        saveDraft();
        // Arka plandaki tezgah canlı: sekmeler, tartımlar, özet, toplam.
        grade = g;
        if (lastSlipBox != null) lastSlipBox.setVisibility(View.GONE);
        if (seller.isEmpty() && body != null && body.getChildCount() > 0) {
            // İlk tartımla başlıkta "Satan yazılmadı" uyarısı belirsin.
            body.removeViewAt(0);
            body.addView(header(), 0);
        }
        renderGradeTabs();
        replaceWeighings();
        replaceSummary();
        refreshTotals();
        toast(TezgahModel.LABELS[g] + ": +" + TezgahModel.kg(TezgahModel.net(k, kg, model.tare())));
        goWizard(stay ? g : TezgahWizardFlow.next(g));
    }

    private TextView sectionLabel(String value) {
        TextView t = text(value, 12, MUTED, true);
        t.setLetterSpacing(0.1f);
        return t;
    }

    private EditText sheetField(String value, String hint, int type) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setTextSize(17);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setPadding(dp(16), 0, dp(16), 0);
        e.setBackground(round(CARD, LINE, 14));
        return e;
    }

    /** qr=true: kaydettikten sonra QR gösterilir (üretici Elek'te okutur); false: fiş WhatsApp'a gider. */
    /** Bu üreticinin (createdAt, id) alımından önceki alımlarından kalan borç (bütün dönemler). */
    private double previousDebt(String who, long createdAt, long id) {
        try { return TezgahSearch.previousDebt(TezgahPurchases.all(db.getWritableDatabase(), owner, 100000), who, createdAt, id); }
        catch (RuntimeException e) { return 0; }
    }

    private boolean savePurchase(double paid, boolean qr) {
        // Elle "Bitir" ile kaydedilirse açık sesli tartım kapanır (eski tartımları ikinci kez kaydetmeye kalkmasın).
        if (voice != null && voice.isOpen()) voice.close();
        // Kayıttan önce: bu üreticinin şimdiye kadarki bütün borcu "önceki borç" olur.
        double prev = previousDebt(TezgahWizardFlow.sellerOrWalkIn(seller), Long.MAX_VALUE, -1);
        String text = model.receipt("Zeytin alım fişi", "Satan", seller, displayDate(), paid, prev);
        final TezgahModel saved = TezgahModel.decode(model.encode());
        final String savedSeller = seller;
        long id;
        try {
            id = TezgahPurchases.add(db.getWritableDatabase(), owner, today(), seller, sellerPhone, model, paid,
                    System.currentTimeMillis());
        } catch (RuntimeException e) {
            id = -1;
        }
        if (id <= 0) { toast("Alım kaydedilemedi. Tekrar dene."); return false; }
        rememberBuyPrices();
        String phone = sellerPhone;
        model.clear();
        seller = "";
        sellerPhone = "";
        grade = 0; // yeni alım Double sekmesiyle başlar
        kasa = 1;  // ve 1 kasadan
        slipIndex = 0; // kaydedilen fiş açık gelsin (3 dk; sonra tek satıra iner)
        slipManual = false;
        slipHiddenByUser = false;
        saveDraft();
        build();
        toast("Alım kaydedildi ✓ Sıradaki üretici için tezgah hazır.");
        if (qr) showQr(saved, today(), savedSeller, paid, text, phone);
        else shareText(text, phone);
        return true;
    }

    // ================================================================== sesle tart

    private TezgahVoiceSession voice;

    /** Elek'teki "Sesle doldur" ile aynı yeşil düğme; basınca kendisi ses dalgasına dönüşür. */
    private View voiceButton() {
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(12), 0, dp(14), 0);
        b.setBackgroundResource(R.drawable.elek_voice_premium);
        b.setClickable(true);
        b.setFocusable(true);
        b.addView(new ElekVoiceOrbView(this).compact(), new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text("Sesle tart", 18, Color.WHITE, true));
        col.addView(text("Kiloyu söyle; kasa, dara, net hesaplansın", 13, 0xE6FFFFFF, false));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
        cp.leftMargin = dp(12);
        b.addView(col, cp);
        b.addView(text("›", 26, Color.WHITE, true));
        b.setContentDescription("Sesle tart: kantardaki kiloyu söyle, asistan kasa, dara ve neti hesaplasın");
        b.setOnClickListener(v -> startVoice());
        return b;
    }

    private LinearLayout wizardVoiceBar;
    /** Sesle tartım açıkken ekran tartım yazma düzenine geçer (sihirbaz açılmaz). */
    private boolean voiceMode;
    private LinearLayout voiceBar;

    private boolean voiceOpen() { return voice != null && voice.isOpen(); }

    /**
     * Sesle tart: kilo yazma ekranı (sihirbaz) hemen açılır, üstünde asistanın ses dalgası durur.
     * Double'dan başlar, söylenen her tartım ekrana yazılır, "yok" deyince sıradaki numara açılır;
     * Yağlık'tan sonra tartım biter, toplam kilo, tutar ve ortalama fiyat söylenir.
     */
    private void startVoice() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, TezgahVoiceSession.MIC_REQUEST);
            return;
        }
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Bu telefonda ses tanıma bulunamadı. Tartımı elle girebilirsin.");
            return;
        }
        if (voice != null) voice.close();
        if (wizard != null) wizard.dismiss();
        voiceMode = true;
        build();
        voice = new TezgahVoiceSession(this, voiceHost(), voiceBar);
        voice.start();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == TezgahVoiceSession.MIC_REQUEST) {
            if (results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) startVoice();
            else toast("Sesle tartmak için mikrofon izni gerekli. Tartımı elle de girebilirsin.");
        }
    }

    private TezgahVoiceSession.Host voiceHost() {
        return new TezgahVoiceSession.Host() {
            @Override public TezgahModel snapshot() { return TezgahModel.decode(model.encode()); }
            @Override public String seller() { return seller; }
            @Override public List<String> sellers() {
                List<String> names = new ArrayList<>();
                try { for (TezgahPurchases.Purchase p : TezgahPurchases.all(db.getWritableDatabase(), owner, 2000)) names.add(p.seller); }
                catch (RuntimeException ignored) { }
                return TezgahWizardFlow.recent(names, 500);
            }
            @Override public void add(int g, int k, double kg) { voiceCommit(g, k, kg); }
            @Override public void removeAt(int index) {
                if (!model.remove(index)) return;
                saveDraft();
                voiceRefresh();
            }
            @Override public void setSeller(String name) {
                seller = name == null ? "" : name.trim();
                saveDraft();
                if (body != null && body.getChildCount() > 0) { body.removeViewAt(0); body.addView(header(), 0); }
            }
            @Override public boolean save(double paid) {
                if (wizard != null) wizard.dismiss();
                return savePurchase(paid, true);
            }
            @Override public void show(TezgahVoiceDialog d) { showVoiceStep(d); }
            @Override public void closed() {
                if (wizardVoiceBar != null) wizardVoiceBar.setVisibility(View.GONE);
                if (voiceMode) { voiceMode = false; if (!isFinishing()) build(); }
            }
        };
    }

    /** Sesle söylenen tartım: sihirbazdaki gibi tezgaha yazılır, ekran canlı tazelenir. */
    private void voiceCommit(int g, int k, double kg) {
        if (!model.add(g, k, kg)) return;
        grade = g;
        kasa = k;
        saveDraft();
        if (lastSlipBox != null) lastSlipBox.setVisibility(View.GONE);
        if (seller.isEmpty() && body != null && body.getChildCount() > 0) { body.removeViewAt(0); body.addView(header(), 0); }
        voiceRefresh();
    }

    /** Konuşmanın bulunduğu adımı kilo ekranında gösterir; son tartım numaranın altında yazar. */
    private void showVoiceStep(TezgahVoiceDialog d) {
        if (voiceMode) { showVoiceGrade(d); return; }
        if (wizard == null) return;
        TezgahVoiceDialog.Phase ph = d.phase();
        boolean weighing = ph == TezgahVoiceDialog.Phase.WEIGH || ph == TezgahVoiceDialog.Phase.KASA_CHECK;
        wizardStep = weighing ? d.grade() : TezgahWizardFlow.SELLER;
        renderWizard();
        if (weighing && wizardNote != null && !model.isEmpty()) {
            List<TezgahModel.Weighing> items = model.items();
            TezgahModel.Weighing w = items.get(items.size() - 1);
            if (w.grade == wizardStep) {
                wizardNote.setVisibility(View.VISIBLE);
                wizardNote.setTextColor(GREEN);
                wizardNote.setText("Son tartım: " + TezgahModel.kg(w.kg) + " · " + (w.kasa > 0 ? w.kasa + " kasa" : "kasasız")
                        + " · net " + TezgahModel.kg(TezgahModel.net(w.kasa, w.kg, model.tare())));
            }
        }
        android.view.View focus = wizard.getCurrentFocus();
        android.view.inputmethod.InputMethodManager im = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (focus != null && im != null) im.hideSoftInputFromWindow(focus.getWindowToken(), 0);
    }

    /** Sesle tartım ekranı: konuşulan numaranın sekmesi seçili, son tartım kilo kutusunun altında yeşil yazar. */
    private void showVoiceGrade(TezgahVoiceDialog d) {
        TezgahVoiceDialog.Phase ph = d.phase();
        boolean weighing = ph == TezgahVoiceDialog.Phase.WEIGH || ph == TezgahVoiceDialog.Phase.KASA_CHECK;
        if (weighing && d.grade() != grade) selectGrade(d.grade());
        if (netPreview == null) return;
        List<TezgahModel.Weighing> items = model.items();
        if (!weighing) {
            netPreview.setVisibility(View.VISIBLE);
            netPreview.setTextColor(GREEN);
            netPreview.setText("Tartım bitti · net " + TezgahModel.kg(model.totalNet()) + " · " + TezgahModel.tl(model.total())
                    + (model.averagePrice() > 0 ? " · ort. " + TezgahModel.money(model.averagePrice()) + "/kg" : ""));
        } else if (!items.isEmpty() && items.get(items.size() - 1).grade == grade) {
            TezgahModel.Weighing w = items.get(items.size() - 1);
            netPreview.setVisibility(View.VISIBLE);
            netPreview.setTextColor(GREEN);
            netPreview.setText("Son tartım: " + TezgahModel.kg(w.kg) + " · " + (w.kasa > 0 ? w.kasa + " kasa" : "kasasız")
                    + " · net " + TezgahModel.kg(TezgahModel.net(w.kasa, w.kg, model.tare())));
        }
    }

    private void voiceRefresh() {
        renderGradeTabs();
        replaceWeighings();
        replaceSummary();
        refreshTotals();
    }

    private String traderName() { return TezgahShare.traderName(); }

    private void showQr(TezgahModel m, String date, String sellerName, double paid, String text, String phone) {
        TezgahShare.showQr(this, m, date, sellerName, paid, text, phone);
    }

    private void shareText(String text, String phone) { TezgahShare.shareText(this, text, phone); }

    /** Alım defteri: tablo görünümlü ayrı sayfa (gün gün, toplamlar, akşam hesabı, rapor). */
    private void showDayList() { startActivity(new Intent(this, TezgahDayActivity.class)); }

    // ================================================================== yardımcılar

    private static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }

    private static String displayDate() { return new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(new Date()); }

    private static String displayDate(long at) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(new Date(at > 0 ? at : System.currentTimeMillis()));
    }

    /** Sekmede kısa kilo: 1.234 kg → "1.234 kg", 12,5 → "12,5 kg". */
    private static String shortKg(double v) { return TezgahModel.kg(v >= 100 ? Math.round(v) : v); }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.setBackground(round(CARD, LINE, 16));
        return box;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))));
        return v;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView pill(String value, boolean filled) {
        TextView t = text(value, 14, filled ? BG : GREEN, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), 0, dp(16), 0);
        t.setBackground(round(filled ? GREEN : Color.TRANSPARENT, GREEN, 20));
        return t;
    }

    private TextView stepButton(String label) {
        TextView t = text(label, 26, TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(BG, LINE, 14));
        return t;
    }

    private void styleInput(EditText e, int sp) {
        e.setTextSize(sp);
        e.setTypeface(Typeface.DEFAULT_BOLD);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setSingleLine(true);
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setBackground(round(BG, GREEN_DIM, 14));
    }

    private EditText dialogInput(String value, String hint, int type) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setSelectAllOnFocus(true);
        e.setTextSize(20);
        return e;
    }

    private View wrap(View v) {
        FrameLayout f = new FrameLayout(this);
        f.setPadding(dp(20), dp(8), dp(20), 0);
        f.addView(v);
        return f;
    }

    private void showKeyboard(EditText e) {
        e.requestFocus();
        e.postDelayed(() -> {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(e, 0);
        }, 150);
    }

    private TextWatcher watcher(Runnable r) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { r.run(); }
        };
    }

    private GradientDrawable round(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }

    private LinearLayout.LayoutParams margins(int w, int h, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.topMargin = dp(topDp);
        return lp;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
