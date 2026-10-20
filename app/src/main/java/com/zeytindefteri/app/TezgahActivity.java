package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Tezgah Modu: tüccarın kantarında, elde telefonla.
 *
 * Akış: zeytin elenir → numara seçilir → "kaç kasa + kantar kilosu" girilir → net = kilo − kasa × dara.
 * Canlı toplam büyük yazılır; ekran kararmaz; yarım kalan tezgah telefonda saklanır.
 * - Satıyorum (üretici): Bitir → mevcut Elek kaydı + Satış/Alıcı kaydı oluşur, makbuz açılır.
 * - Alıyorum (tüccar): Bitir → alım fişi bu telefonda saklanır (TezgahPurchases), WhatsApp'tan gönderilir,
 *   ekran bir sonraki üretici için sıfırlanır. Tüccar alımları buluta, Lig'e ve piyasaya karışmaz.
 */
public final class TezgahActivity extends Activity {
    public static final String EXTRA_GARDEN_ID = "garden_id";

    static final int ROLE_SELL = 0, ROLE_BUY = 1;
    private static final String PREFS = "zd_tezgah";
    private static final String ELEK_PREFS = "elek_price_settings";
    private static final String[] ELEK_PRICE_KEYS = {"price_double", "price_1", "price_2", "price_3", "price_4", "price_oil"};
    private static final double[] ELEK_PRICE_DEFAULTS = {100, 90, 70, 55, 40, 32};

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
    private int role = ROLE_SELL;
    private int grade = 0;
    private TezgahModel model = new TezgahModel();
    private int kasa = 10;

    private long gardenId;
    private String gardenName = "";
    private String seller = "", sellerPhone = "";

    private LinearLayout body;
    private TextView totalView, totalSub, finishButton, netPreview;
    private EditText kgInput, kasaInput;
    private final TextView[] gradeTabs = new TextView[TezgahModel.GRADES.length];
    private final TextView[] roleTabs = new TextView[2];

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!new SessionManager(this).hasSession()) { finish(); return; }
        db = new DatabaseHelper(this);
        owner = db.getActiveOwnerUid();
        if (owner == null) owner = "";
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        role = prefs.getInt("role", ROLE_SELL) == ROLE_BUY ? ROLE_BUY : ROLE_SELL;
        kasa = Math.max(0, Math.min(TezgahModel.MAX_KASA, prefs.getInt("kasa", 10)));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        loadGarden(getIntent().getLongExtra(EXTRA_GARDEN_ID, 0L));
        loadDraft();
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
                .putInt("role", role)
                .putInt("kasa", kasa)
                .putInt("grade_" + role, grade)
                .putFloat("tare", (float) model.tare())
                .putString("seller_" + owner, seller)
                .putString("seller_phone_" + owner, sellerPhone);
        if (model.isEmpty()) e.remove(draftKey()); else e.putString(draftKey(), model.encode());
        e.apply();
    }

    /** Üretici: Elek ekranındaki fiyatlar. Tüccar: tezgahta en son kullandığı alım fiyatları. */
    private double[] defaultPrices() {
        double[] out = new double[TezgahModel.GRADES.length];
        if (role == ROLE_BUY) {
            TezgahModel last = TezgahModel.decode(prefs.getString("buy_prices_" + owner, ""));
            for (int g = 0; g < out.length; g++) out[g] = last.price(g);
            return out;
        }
        SharedPreferences elek = getSharedPreferences(ELEK_PREFS, MODE_PRIVATE);
        for (int g = 0; g < out.length; g++) {
            try {
                String v = elek.getString(ELEK_PRICE_KEYS[g], "");
                out[g] = v == null || v.trim().isEmpty() ? ELEK_PRICE_DEFAULTS[g] : Double.parseDouble(v);
            } catch (RuntimeException ex) {
                out[g] = ELEK_PRICE_DEFAULTS[g];
            }
        }
        return out;
    }

    private void rememberBuyPrices() {
        TezgahModel p = new TezgahModel();
        for (int g = 0; g < TezgahModel.GRADES.length; g++) p.setPrice(g, model.price(g));
        prefs.edit().putString("buy_prices_" + owner, p.encode()).apply();
    }

    // ================================================================== bahçe

    private void loadGarden(long wanted) {
        gardenId = 0;
        gardenName = "";
        Cursor c = null;
        try {
            c = db.getGardens();
            while (c != null && c.moveToNext()) {
                long id = c.getLong(c.getColumnIndexOrThrow("id"));
                String name = c.getString(c.getColumnIndexOrThrow("name"));
                if (name == null || name.trim().isEmpty()) continue;
                if (gardenId == 0 || id == wanted) { gardenId = id; gardenName = name.trim(); }
                if (id == wanted) break;
            }
        } catch (RuntimeException ignored) {
        } finally {
            if (c != null) c.close();
        }
    }

    private void pickGarden() {
        final List<Long> ids = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        Cursor c = null;
        try {
            c = db.getGardens();
            while (c != null && c.moveToNext()) {
                String name = c.getString(c.getColumnIndexOrThrow("name"));
                if (name == null || name.trim().isEmpty()) continue;
                ids.add(c.getLong(c.getColumnIndexOrThrow("id")));
                names.add(name.trim());
            }
        } catch (RuntimeException ignored) {
        } finally {
            if (c != null) c.close();
        }
        if (ids.isEmpty()) { toast("Önce bir bahçe ekle."); return; }
        new AlertDialog.Builder(this)
                .setTitle("Hangi bahçenin zeytini?")
                .setItems(names.toArray(new String[0]), (d, i) -> {
                    gardenId = ids.get(i);
                    gardenName = names.get(i);
                    build();
                })
                .show();
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
        if (role == ROLE_BUY) body.addView(dayCard(), margins(-1, -2, 8));
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
        col.addView(text("Tezgah", 20, TEXT, true));
        String context;
        if (role == ROLE_SELL) context = gardenName.isEmpty() ? "Bahçe seç" : gardenName;
        else context = seller.isEmpty() ? "Satan: ad yaz" : seller;
        TextView sub = text(context + " ▾ · " + new SimpleDateFormat("d MMM", new Locale("tr", "TR")).format(new Date()),
                13, GREEN, true);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(sub);
        col.setContentDescription(role == ROLE_SELL ? "Bahçe: " + context + ", değiştir" : "Satan üretici: " + context + ", değiştir");
        col.setOnClickListener(v -> { if (role == ROLE_SELL) pickGarden(); else askSeller(null); });
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(roleSwitch(), new LinearLayout.LayoutParams(-2, dp(40)));
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

    private View roleSwitch() {
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(3), dp(3), dp(3), dp(3));
        box.setBackground(round(CARD, LINE, 20));
        String[] labels = {"Satıyorum", "Alıyorum"};
        String[] spoken = {"Satıyorum (üretici)", "Alıyorum (tüccar)"};
        for (int i = 0; i < 2; i++) {
            final int r = i;
            TextView t = text(labels[i], 13, i == role ? BG : TEXT, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(10), 0, dp(10), 0);
            t.setBackground(i == role ? round(GREEN, 0, 17) : null);
            t.setContentDescription(spoken[i] + (i == role ? ", seçili" : ""));
            t.setOnClickListener(v -> switchRole(r));
            roleTabs[i] = t;
            box.addView(t, new LinearLayout.LayoutParams(-2, -1));
        }
        return box;
    }

    private void switchRole(int r) {
        if (r == role) return;
        saveDraft();
        role = r;
        loadDraft();
        build();
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
        LinearLayout headRow = new LinearLayout(this);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        entryHead = text("", 12, MUTED, true);
        entryHead.setLetterSpacing(0.05f);
        headRow.addView(entryHead, new LinearLayout.LayoutParams(0, -2, 1));
        TextView tare = text("Dara " + TezgahModel.kg(model.tare()) + "/kasa ›", 13, GREEN, true);
        tare.setPadding(dp(8), dp(6), 0, dp(6));
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
        kasaCol.addView(text("Kasa", 12, MUTED, true));
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
        kgCol.addView(text("Kantar (kg)", 12, MUTED, true));
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

        netPreview = text("", 14, MUTED, true);
        netPreview.setSingleLine(true);
        netPreview.setEllipsize(android.text.TextUtils.TruncateAt.END);
        box.addView(netPreview, margins(-1, -2, 6));

        TextView add = text("+  Tartımı ekle", 18, BG, true);
        add.setGravity(Gravity.CENTER);
        add.setBackground(round(GREEN, 0, 14));
        add.setContentDescription("Tartımı ekle");
        add.setOnClickListener(v -> addWeighing());
        box.addView(add, margins(-1, dp(50), 6));
        updatePreview();
        return box;
    }

    private void setKasa(int value) {
        kasa = Math.max(0, Math.min(TezgahModel.MAX_KASA, value));
        if (kasaInput != null) kasaInput.setText(String.valueOf(kasa));
        updatePreview();
    }

    private void updatePreview() {
        if (netPreview == null || kgInput == null) return;
        double kg = TezgahModel.parse(kgInput.getText().toString());
        if (kg <= 0) {
            netPreview.setTextColor(MUTED);
            netPreview.setText(kasa > 0 ? kasa + " kasa · dara " + TezgahModel.kg(kasa * model.tare()) : "Dökme (kasasız)");
            return;
        }
        String err = TezgahModel.addError(kasa, kg, model.tare());
        if (!err.isEmpty()) {
            netPreview.setTextColor(RED);
            netPreview.setText(err);
        } else if (!TezgahModel.kasaWarning(kasa, kg, model.tare()).isEmpty()) {
            netPreview.setTextColor(AMBER);
            netPreview.setText("⚠ " + kasa + " kasaya " + TezgahModel.kg(kg) + " fazla. "
                    + TezgahModel.suggestedKasa(kasa, kg, model.tare()) + " kasa mı?");
        } else {
            netPreview.setTextColor(GREEN);
            netPreview.setText("Net: " + TezgahModel.kg(TezgahModel.net(kasa, kg, model.tare())));
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
            // Bir kasaya en fazla ~30 kg sığar: kasa sayısı muhtemelen yanlış girildi.
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
        headRow.setMinimumHeight(dp(30));
        headRow.addView(text(TezgahModel.LABELS[grade] + " · " + count + " tartım", 14, TEXT, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        TextView sumText = text(model.gradeKasa(grade) + " kasa · net " + TezgahModel.kg(model.gradeNet(grade)), 14, GREEN, true);
        sumText.setPadding(0, 0, dp(10), 0);
        headRow.addView(sumText);
        box.addView(headRow);
        int n = 0, shown = 0;
        int limit = showAllWeighings ? Integer.MAX_VALUE : 3;
        for (int i = all.size() - 1; i >= 0; i--) {
            final TezgahModel.Weighing w = all.get(i);
            if (w.grade != grade) continue;
            n++;
            if (shown >= limit) continue;
            shown++;
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(36));
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
            row.addView(del, new LinearLayout.LayoutParams(dp(40), dp(36)));
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
            row.setMinimumHeight(dp(34));
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

    private View dayCard() {
        LinearLayout box = card();
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setMinimumHeight(dp(56));
        TezgahPurchases.Day d;
        try { d = TezgahPurchases.summary(db.getWritableDatabase(), owner, today()); }
        catch (RuntimeException e) { d = new TezgahPurchases.Day(); }
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text("BUGÜNKÜ ALIMLAR", 12, MUTED, true));
        col.addView(text(TezgahPurchases.dayLine(d), 15, TEXT, true));
        box.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        if (d.count > 0) box.addView(text("›", 24, GREEN, true));
        final int count = d.count;
        box.setOnClickListener(v -> { if (count > 0) showDayList(); });
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
        totalSub.setText(role == ROLE_SELL ? "ELİNE GEÇECEK" : "ÖDEYECEĞİN");
        totalView.setText(TezgahModel.tl(model.total()));
        totalView.setContentDescription((role == ROLE_SELL ? "Eline geçecek " : "Ödeyeceğin ") + TezgahModel.tl(model.total()));
        finishButton.setEnabled(!empty);
        finishButton.setBackground(round(empty ? LINE : GREEN, 0, 18));
        finishButton.setTextColor(empty ? MUTED : BG);
        finishButton.setContentDescription(role == ROLE_SELL ? "Bitir ve satışı kaydet" : "Bitir ve alım fişini kaydet");
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
                    if (role == ROLE_BUY) rememberBuyPrices();
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

    /** Ortak ödeme penceresi: Tamamı / Bir kısmı / Henüz değil. */
    private void askPayment() {
        if (role == ROLE_SELL && gardenId <= 0) { toast("Önce bahçe seç."); pickGarden(); return; }
        final double total = model.total();
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);

        final TextView party = new TextView(this);
        final String[] buyer = {""};
        if (role == ROLE_SELL) {
            party.setText("Alıcı: seç ya da yaz ›");
            party.setTextSize(17);
            party.setTypeface(Typeface.DEFAULT_BOLD);
            party.setPadding(0, dp(10), 0, dp(10));
            party.setOnClickListener(v -> BuyerPicker.show(this, db, buyer[0], "Alıcısız kaydet", name -> {
                buyer[0] = name == null ? "" : name.trim();
                party.setText(buyer[0].isEmpty() ? "Alıcı: belirtilmedi ›" : "Alıcı: " + buyer[0] + " ›");
            }));
            form.addView(party);
        } else {
            party.setText(seller.isEmpty() ? "Satan: belirtilmedi (dokun, yaz) ›" : "Satan: " + seller + " ›");
            party.setTextSize(17);
            party.setTypeface(Typeface.DEFAULT_BOLD);
            party.setPadding(0, dp(10), 0, dp(10));
            form.addView(party);
        }

        final String[] labels = role == ROLE_SELL
                ? new String[]{"Tamamını aldım", "Bir kısmını aldım", "Henüz almadım"}
                : new String[]{"Tamamını ödedim", "Bir kısmını ödedim", "Henüz ödemedim"};
        final int[] pay = {ElekSaleModel.PAY_ALL};
        final EditText partial = dialogInput("", "Ödenen tutar (₺)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        partial.setVisibility(View.GONE);
        final TextView[] chips = new TextView[3];
        for (int i = 0; i < 3; i++) {
            final int option = i;
            TextView c = new TextView(this);
            c.setText(labels[i]);
            c.setTextSize(16);
            c.setPadding(dp(14), dp(12), dp(14), dp(12));
            c.setOnClickListener(v -> {
                pay[0] = option;
                for (int k = 0; k < 3; k++) chips[k].setTypeface(k == option ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                for (int k = 0; k < 3; k++) chips[k].setText((k == option ? "● " : "○ ") + labels[k]);
                partial.setVisibility(option == ElekSaleModel.PAY_PART ? View.VISIBLE : View.GONE);
            });
            chips[i] = c;
            form.addView(c);
        }
        for (int k = 0; k < 3; k++) chips[k].setText((k == 0 ? "● " : "○ ") + labels[k]);
        chips[0].setTypeface(Typeface.DEFAULT_BOLD);
        form.addView(partial);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("Toplam " + TezgahModel.tl(total) + " · " + TezgahModel.kg(model.totalNet()))
                .setView(form)
                .setNegativeButton("Vazgeç", null);
        if (role == ROLE_SELL) {
            b.setPositiveButton("Kaydet", null);
        } else {
            b.setPositiveButton("Kaydet + WhatsApp", null);
            b.setNeutralButton("Yalnız kaydet", null);
        }
        final AlertDialog dialog = b.create();
        dialog.setOnShowListener(x -> {
            View.OnClickListener go = v -> {
                double part = TezgahModel.parse(partial.getText().toString());
                String err = ElekSaleModel.partialError(pay[0], total, part);
                if (!err.isEmpty()) { partial.setError(err); return; }
                double paid = ElekSaleModel.received(pay[0], total, part);
                boolean share = v == dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                dialog.dismiss();
                if (role == ROLE_SELL) saveSale(pay[0], part, buyer[0]);
                else savePurchase(paid, share);
            };
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(go);
            View neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (neutral != null) neutral.setOnClickListener(go);
            if (role == ROLE_BUY) party.setOnClickListener(v -> { dialog.dismiss(); askSeller(this::askPayment); });
        });
        dialog.show();
    }

    /** Üretici: Elek kaydı + satış (mevcut kurallarla), sonra makbuz. */
    private void saveSale(int pay, double partial, String buyer) {
        String note = "Tezgah: " + model.totalKasa() + " kasa, brüt " + TezgahModel.kg(model.totalGross())
                + ", dara " + TezgahModel.kg(model.tare()) + "/kasa";
        long elekId;
        try {
            elekId = db.addElekRecord(gardenId, today(), TezgahModel.GRADES, model.netArray(), model.priceArray(), note);
        } catch (RuntimeException e) {
            elekId = -1;
        }
        if (elekId <= 0) { toast("Kaydedilemedi. Tekrar dene."); return; }
        long saleId = new ElekSaleBridge(db).createSale(elekId, pay, partial, buyer);
        syncCloud(elekId, saleId);
        Toast.makeText(this, ElekSaleModel.successToast(saleId > 0, true),
                saleId > 0 ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
        model.clear();
        saveDraft();
        startActivity(new Intent(this, ElekReceiptActivity.class).putExtra(ElekReceiptActivity.EXTRA_RECORD_ID, elekId));
        finish();
    }

    private void syncCloud(long elekId, long saleId) {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) return;
            ProductionCloudRepository cloud = new ProductionCloudRepository(db);
            cloud.syncElekByLocalId(user.getUid(), elekId);
            if (saleId > 0) cloud.syncSaleByLocalId(user.getUid(), saleId);
        } catch (RuntimeException ignored) {
            // Önce telefon: kayıt yerelde tamam; sonraki genel eşitleme yeniden dener.
        }
    }

    /** Tüccar: alım fişini kaydet, istenirse WhatsApp'tan gönder, tezgahı sonraki üretici için sıfırla. */
    private void savePurchase(double paid, boolean share) {
        String text = model.receipt("Zeytin alım fişi", "Satan", seller, displayDate(), paid);
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
        if (share) shareText(text, phone);
    }

    private void shareText(String text, String phone) {
        String wa = TezgahModel.waPhone(phone);
        if (!wa.isEmpty()) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://wa.me/" + wa + "?text=" + Uri.encode(text))));
                return;
            } catch (RuntimeException ignored) {
                // WhatsApp yoksa paylaşım menüsüne düşer.
            }
        }
        try {
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(send, "Fişi gönder"));
        } catch (RuntimeException e) {
            toast("Paylaşılacak uygulama bulunamadı.");
        }
    }

    private void showDayList() {
        final List<TezgahPurchases.Purchase> list;
        try { list = TezgahPurchases.forDay(db.getWritableDatabase(), owner, today()); }
        catch (RuntimeException e) { toast("Alımlar okunamadı."); return; }
        if (list.isEmpty()) return;
        String[] rows = new String[list.size()];
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.US);
        for (int i = 0; i < rows.length; i++) {
            TezgahPurchases.Purchase p = list.get(i);
            rows[i] = hm.format(new Date(p.createdAt)) + " · " + (p.seller.isEmpty() ? "Adsız" : p.seller) + "\n"
                    + TezgahModel.kg(p.kg) + " · " + TezgahModel.tl(p.gross)
                    + (p.remaining() > 0.5 ? " · " + TezgahModel.tl(p.remaining()) + " borç" : " · ödendi");
        }
        new AlertDialog.Builder(this)
                .setTitle("Bugünkü alımlar")
                .setItems(rows, (d, i) -> purchaseActions(list.get(i)))
                .setNegativeButton("Kapat", null)
                .show();
    }

    private void purchaseActions(final TezgahPurchases.Purchase p) {
        final TezgahModel m = TezgahModel.decode(p.data);
        String[] actions = p.remaining() > 0.5
                ? new String[]{"Fişi gönder", "Kalan borcu ödedim", "Sil"}
                : new String[]{"Fişi gönder", "Sil"};
        new AlertDialog.Builder(this)
                .setTitle((p.seller.isEmpty() ? "Adsız" : p.seller) + " · " + TezgahModel.tl(p.gross))
                .setItems(actions, (d, i) -> {
                    String a = actions[i];
                    if (a.startsWith("Fişi")) {
                        shareText(m.receipt("Zeytin alım fişi", "Satan", p.seller, displayDate(), p.paid), p.phone);
                    } else if (a.startsWith("Kalan")) {
                        TezgahPurchases.setPaid(db.getWritableDatabase(), owner, p.id, p.gross);
                        toast("Borç kapandı ✓");
                        build();
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("Alım silinsin mi?")
                                .setMessage((p.seller.isEmpty() ? "Adsız" : p.seller) + " · " + TezgahModel.kg(p.kg))
                                .setPositiveButton("Sil", (x, y) -> {
                                    TezgahPurchases.delete(db.getWritableDatabase(), owner, p.id);
                                    build();
                                })
                                .setNegativeButton("Vazgeç", null)
                                .show();
                    }
                })
                .show();
    }

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
