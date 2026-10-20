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
    private int kasa = 10;

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
        kasa = Math.max(0, Math.min(TezgahModel.MAX_KASA, prefs.getInt("kasa", 10)));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        loadDraft();
        build();
    }

    /** Alım defterinden dönünce (silme, borç kapama) gün özeti ve son fiş tazelensin. */
    @Override protected void onRestart() {
        super.onRestart();
        build();
    }

    @Override protected void onPause() {
        saveDraft();
        super.onPause();
    }

    @Override protected void onDestroy() {
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
    private double[] defaultPrices() {
        double[] out = new double[TezgahModel.GRADES.length];
        TezgahModel last = TezgahModel.decode(prefs.getString("buy_prices_" + owner, ""));
        for (int g = 0; g < out.length; g++) out[g] = last.price(g);
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
        scroll.setFillViewport(true);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(2), dp(12), dp(10));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        column.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        column.addView(bottomBar(), new LinearLayout.LayoutParams(-1, -2));

        // Sıkı düzen: her şey tek ekrana sığsın (gerekirse yine kayar).
        body.addView(header());
        body.addView(gradeRow(), margins(-1, -2, 6));
        body.addView(entryCard(), margins(-1, -2, 8));
        body.addView(weighingsCard(), margins(-1, -2, 8));
        body.addView(summaryCard(), margins(-1, -2, 8));
        body.addView(lastSlipCard(), margins(-1, -2, 8));
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
        col.addView(text("Tezgah · Alım", 20, TEXT, true));
        String context = seller.isEmpty() ? "Satan üretici: ad yaz" : "Satan: " + seller;
        TextView sub = text(context + " ▾ · " + new SimpleDateFormat("d MMM", new Locale("tr", "TR")).format(new Date()),
                13, GREEN, true);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(sub);
        col.setContentDescription(context + ", değiştir");
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
                .setMessage(model.items().size() + " tartım silinecek. Kaydedilmemiş bilgiler kaybolur.")
                .setPositiveButton("Sıfırla", (d, w) -> { model.clear(); saveDraft(); build(); })
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
            t.setOnClickListener(v -> { grade = index; showAllWeighings = false; renderGradeTabs(); replaceWeighings(); updatePreview(); });
            gradeTabs[g] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
            if (g > 0) lp.setMarginStart(dp(4));
            row.addView(t, lp);
        }
        renderGradeTabs();
        return row;
    }

    private TextView entryHead;

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
            String sub = net > 0 ? "\n" + shortKg(net) : "";
            t.setText(TezgahModel.SHORT[g] + sub);
            t.setTextColor(on ? BG : TEXT);
            t.setBackground(on ? round(GREEN, 0, 12) : round(CARD, net > 0 ? GREEN_DIM : LINE, 12));
            t.setContentDescription(TezgahModel.LABELS[g] + (net > 0 ? ", " + TezgahModel.kg(net) : "") + (on ? ", seçili" : ""));
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
        kgInput.setHint("0");
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
        LinearLayout box = card();
        summaryBox = box;
        box.setPadding(dp(12), dp(6), dp(12), dp(8));
        LinearLayout headRow = new LinearLayout(this);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView head = text("ÖZET · fiyata dokun, değiştir", 12, MUTED, true);
        head.setLetterSpacing(0.05f);
        headRow.addView(head, new LinearLayout.LayoutParams(0, -2, 1));
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
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(29));
            TextView name = text(TezgahModel.LABELS[g], 14, net > 0 ? TEXT : MUTED, true);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1.1f));
            TextView kg = text(net > 0 ? shortKg(net) + "  " + TezgahModel.percent(model.share(g)) : "—", 14, net > 0 ? TEXT : MUTED, false);
            row.addView(kg, new LinearLayout.LayoutParams(0, -2, 1.2f));
            TextView price = text(model.price(g) > 0 ? TezgahModel.money(model.price(g)) : "fiyat?", 14,
                    model.price(g) > 0 ? TEXT : AMBER, true);
            price.setGravity(Gravity.CENTER);
            price.setPadding(dp(8), dp(3), dp(8), dp(3));
            price.setBackground(round(BG, LINE, 9));
            price.setContentDescription(TezgahModel.LABELS[g] + " fiyatını değiştir");
            price.setOnClickListener(v -> askPrice(index));
            row.addView(price, new LinearLayout.LayoutParams(-2, -2));
            TextView amount = text(net > 0 ? TezgahModel.tl(model.gradeAmount(g)) : "", 15, TEXT, true);
            amount.setGravity(Gravity.END);
            row.addView(amount, new LinearLayout.LayoutParams(0, -2, 1.2f));
            box.addView(row);
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

    private LinearLayout lastSlipBox;

    /**
     * Tezgah boşken (bir alım bitince) son fişin QR'ı burada büyükçe durur: üretici hemen okutabilir,
     * dokununca tam ekran açılır. Yeni tartım başlayınca yer açmak için gizlenir.
     */
    private View lastSlipCard() {
        LinearLayout box = card();
        lastSlipBox = box;
        box.setVisibility(View.GONE);
        if (!model.isEmpty()) return box;
        final List<TezgahPurchases.Purchase> list;
        try { list = TezgahPurchases.open(db.getWritableDatabase(), owner); }
        catch (RuntimeException e) { return box; }
        if (list.isEmpty()) return box;
        final TezgahPurchases.Purchase p = list.get(0);
        final TezgahModel m = TezgahModel.decode(p.data);
        if (m.isEmpty()) return box;
        int size = dp(150);
        android.graphics.Bitmap bmp = TezgahQrUi.bitmap(TezgahQr.encode(m, p.date, traderName(), p.paid), size);
        if (bmp == null) return box;
        box.setVisibility(View.VISIBLE);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        ImageView img = new ImageView(this);
        img.setImageBitmap(bmp);
        img.setBackground(round(Color.WHITE, 0, 10));
        img.setPadding(dp(4), dp(4), dp(4), dp(4));
        box.addView(img, new LinearLayout.LayoutParams(size, size));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), 0, 0, 0);
        col.addView(text("SON FİŞ · ÜRETİCİ OKUTSUN", 11, MUTED, true));
        TextView who = text(p.seller.isEmpty() ? "Adsız üretici" : p.seller, 17, TEXT, true);
        who.setMaxLines(2);
        col.addView(who, margins(-1, -2, 4));
        col.addView(text(TezgahModel.kg(p.kg) + " · " + TezgahModel.tl(p.gross), 15, GREEN, true), margins(-1, -2, 2));
        col.addView(text("Elek kaydı → \"QR okut\"", 13, MUTED, false), margins(-1, -2, 6));
        TextView big = text("Büyüt ›", 14, GREEN, true);
        big.setPadding(0, dp(8), 0, 0);
        col.addView(big);
        box.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        box.setContentDescription("Son fiş QR kodu: " + (p.seller.isEmpty() ? "adsız" : p.seller) + ", büyütmek için dokun");
        box.setOnClickListener(v -> showQr(m, p.date, p.seller, p.paid,
                m.receipt("Zeytin alım fişi", "Satan", p.seller, displayDate(), p.paid), p.phone));
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
        totalSub.setText(avg > 0 ? "ÖDEYECEĞİN · ort. " + TezgahModel.money(avg) + "/kg" : "ÖDEYECEĞİN");
        totalView.setText(TezgahModel.tl(model.total()));
        totalView.setContentDescription("Ödeyeceğin " + TezgahModel.tl(model.total())
                + (avg > 0 ? ", kilo başı ortalama " + TezgahModel.money(avg) : ""));
        finishButton.setEnabled(!empty);
        finishButton.setBackground(round(empty ? LINE : GREEN, 0, 18));
        finishButton.setTextColor(empty ? MUTED : BG);
        finishButton.setContentDescription("Bitir ve alım fişini kaydet");
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
                    .setPositiveButton("Devam et", (d, w) -> askPayment())
                    .setNegativeButton("Fiyat gir", null)
                    .show();
            return;
        }
        askPayment();
    }

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
            seller = name.getText().toString().trim();
            sellerPhone = phone.getText().toString().trim();
            double paid = ElekSaleModel.received(pay[0], total, part);
            sheet.dismiss();
            savePurchase(paid, v == qr);
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
    private void savePurchase(double paid, boolean qr) {
        String text = model.receipt("Zeytin alım fişi", "Satan", seller, displayDate(), paid);
        final TezgahModel saved = TezgahModel.decode(model.encode());
        final String savedSeller = seller;
        long id;
        try {
            id = TezgahPurchases.add(db.getWritableDatabase(), owner, today(), seller, sellerPhone, model, paid,
                    System.currentTimeMillis());
        } catch (RuntimeException e) {
            id = -1;
        }
        if (id <= 0) { toast("Alım kaydedilemedi. Tekrar dene."); return; }
        rememberBuyPrices();
        String phone = sellerPhone;
        model.clear();
        seller = "";
        sellerPhone = "";
        saveDraft();
        build();
        toast("Alım kaydedildi ✓ Sıradaki üretici için tezgah hazır.");
        if (qr) showQr(saved, today(), savedSeller, paid, text, phone);
        else shareText(text, phone);
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
