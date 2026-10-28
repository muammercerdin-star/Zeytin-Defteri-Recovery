package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
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
 * Cari hesap: aracı / tüccar / kendi alıcılarımla para hesabı.
 *
 * Üstte alacağım, üreticilere borcum, net; kasa özeti. "Satış / alacak" (numara numara, toplam kilo × fiyat,
 * kilo × komisyon; peşin alınan da yazılır) ve "Tahsilat". Altta alıcı alıcı bakiye; dokununca geçmiş,
 * tahsilat, WhatsApp'tan hesap dökümü. Gün sonunda akşam hesabındaki "tüccar ödeyecek" buraya işlenir.
 */
public final class TezgahLedgerActivity extends Activity {
    public static final String EXTRA_NAME = "name";
    private static final String PREFS = "zd_tezgah";
    private static final String SALE_PRICES = "sale_prices_";

    private static final int BG = Color.parseColor("#050F16");
    private static final int CARD = Color.parseColor("#0F2230");
    private static final int LINE = Color.parseColor("#2A4452");
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.parseColor("#A9BAB6");
    private static final int GREEN = Color.parseColor("#8BE06A");
    private static final int GREEN_DIM = Color.parseColor("#1E3D2C");
    private static final int AMBER = Color.parseColor("#F2C35B");

    private DatabaseHelper db;
    private SharedPreferences prefs;
    private String owner = "";
    private LinearLayout body;
    private List<TezgahLedger.Entry> entries = new ArrayList<>();

    public static void open(Context c, String name) {
        Intent i = new Intent(c, TezgahLedgerActivity.class);
        if (name != null && !name.trim().isEmpty()) i.putExtra(EXTRA_NAME, name.trim());
        c.startActivity(i);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!new SessionManager(this).hasSession()) { finish(); return; }
        db = new DatabaseHelper(this);
        owner = db.getActiveOwnerUid();
        if (owner == null) owner = "";
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(4), dp(12), dp(28));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        render();
        String name = getIntent().getStringExtra(EXTRA_NAME);
        if (name != null && TezgahLedger.validParty(name)) showParty(name);
    }

    @Override protected void onRestart() {
        super.onRestart();
        if (body != null) render();
    }

    @Override protected void onDestroy() {
        if (db != null) db.close();
        super.onDestroy();
    }

    private static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }

    private void load() {
        try { entries = TezgahLedger.entries(db.getWritableDatabase(), owner); }
        catch (RuntimeException e) { entries = new ArrayList<>(); }
    }

    private double producerDebt() {
        try { return TezgahSearch.totalRemaining(TezgahPurchases.all(db.getWritableDatabase(), owner, 100000)); }
        catch (RuntimeException e) { return 0; }
    }

    // ================================================================== ana ekran

    private void render() {
        load();
        body.removeAllViews();
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setMinimumHeight(dp(52));
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_elek_back_chevron);
        back.setColorFilter(TEXT);
        back.setPadding(dp(11), dp(11), dp(11), dp(11));
        back.setContentDescription("Geri");
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(40), dp(44));
        bp.setMarginStart(-dp(10));
        head.addView(back, bp);
        head.addView(text("Cari hesap", 21, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(head);
        body.addView(text("Aracı, tüccar ve kendi alıcılarınla para hesabı. Gün sonunda tüccarın ödeyeceği buraya işlenir.",
                13, MUTED, false));

        double[] t = TezgahLedger.totals(entries);
        double prod = producerDebt();
        LinearLayout tiles = new LinearLayout(this);
        tiles.addView(tile("ALACAĞIN", TezgahModel.tl(t[0]), t[0] > 0.5 ? AMBER : GREEN,
                t[1] > 0.5 ? "alıcılara borç " + TezgahModel.tl(t[1]) : "alıcılardan"), new LinearLayout.LayoutParams(0, -2, 1));
        tiles.addView(tile("ÜRETİCİLERE BORCUN", TezgahModel.tl(prod), prod > 0.5 ? AMBER : GREEN, "alım defterinden"), weighted(8));
        body.addView(tiles, margins(12));
        double net = t[0] - t[1] - prod;
        TextView netView = text("Net: " + (net >= 0 ? "+" : "−") + TezgahModel.tl(Math.abs(net))
                + (net >= 0 ? "  (alacak borçtan fazla)" : "  (borç alacaktan fazla)"), 15, net >= 0 ? GREEN : AMBER, true);
        body.addView(netView, margins(8));

        // Kasa özeti (aracıyla)
        TezgahCrates.Day cd;
        try { cd = TezgahCrates.day(TezgahCrates.moves(db.getWritableDatabase(), owner), null, today()); }
        catch (RuntimeException e) { cd = new TezgahCrates.Day(); }
        if (cd.carried != 0 || cd.in > 0 || cd.out > 0) {
            int h = cd.held();
            TextView k = text("📦  Kasa: " + (h >= 0 ? "sende " + h : "onda " + (-h)) + " · bugün gelen " + cd.in + ", giden " + cd.out + "  ›",
                    14, MUTED, true);
            k.setPadding(dp(12), dp(10), dp(12), dp(10));
            k.setBackground(round(CARD, LINE, 12));
            k.setOnClickListener(v -> TezgahCratesActivity.open(this, null));
            body.addView(k, margins(10));
        }

        LinearLayout actions = new LinearLayout(this);
        TextView sale = button("+  Satış / alacak", true);
        sale.setContentDescription("Satış ya da alacak ekle");
        sale.setOnClickListener(v -> askSale(""));
        actions.addView(sale, new LinearLayout.LayoutParams(0, dp(54), 1));
        TextView pay = button("↓  Tahsilat", false);
        pay.setContentDescription("Tahsilat yaz: alıcıdan para geldi");
        pay.setOnClickListener(v -> askPayment("", TezgahLedger.RECEIVED));
        actions.addView(pay, weightedH(8, 54));
        body.addView(actions, margins(14));

        List<TezgahLedger.Party> parties = TezgahLedger.parties(entries);
        if (parties.isEmpty()) {
            TextView empty = text("Henüz cari hesap yok.\n\nGün sonu (Z) alınınca akşam hesabındaki \"tüccar ödeyecek\" tutarı "
                    + "tüccarın adıyla buraya işlenir. Kendi alıcına sattığın zeytini \"Satış / alacak\" ile, gelen parayı "
                    + "\"Tahsilat\" ile yaz.", 14, MUTED, false);
            empty.setPadding(dp(14), dp(14), dp(14), dp(14));
            empty.setBackground(round(CARD, LINE, 14));
            body.addView(empty, margins(16));
            return;
        }
        TextView lh = text("ALICILAR · dokun, geçmiş ve tahsilat", 12, MUTED, true);
        lh.setLetterSpacing(0.06f);
        body.addView(lh, margins(16));
        for (TezgahLedger.Party p : parties) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(10), dp(12), dp(10));
            double b = p.balance();
            row.setBackground(round(CARD, Math.abs(b) > 0.5 ? AMBER : LINE, 14));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView n = text(p.name, 16, TEXT, true);
            n.setSingleLine(true);
            n.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(n);
            col.addView(text("satış " + TezgahModel.tl(p.sales) + " · tahsil " + TezgahModel.tl(p.received)
                    + (p.lastDate.isEmpty() ? "" : " · son " + shortDate(p.lastDate)), 12, MUTED, false));
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            TextView chip = text(b > 0.5 ? TezgahModel.tl(b) : b < -0.5 ? "borcun " + TezgahModel.tl(-b) : "Kapalı ✓", 15,
                    Math.abs(b) > 0.5 ? AMBER : GREEN, true);
            chip.setPadding(dp(10), dp(4), 0, dp(4));
            row.addView(chip);
            row.setContentDescription(TezgahLedger.balanceLine(p.name, b) + ". Geçmiş için dokun.");
            final String name = p.name;
            row.setOnClickListener(v -> showParty(name));
            body.addView(row, margins(6));
        }
    }

    // ================================================================== alıcı

    private void showParty(String name) {
        List<TezgahLedger.Entry> list;
        try { list = TezgahLedger.entriesOf(db.getWritableDatabase(), owner, name); }
        catch (RuntimeException e) { list = new ArrayList<>(); }
        double bal = 0;
        for (TezgahLedger.Entry e : list) bal += e.signed();
        bal = Math.round(bal * 100) / 100.0;
        final String shown = list.isEmpty() ? name.trim() : list.get(0).party;
        final double balance = bal;
        final List<TezgahLedger.Entry> rows = list;

        final Dialog sheet = sheet();
        LinearLayout box = sheetBox(sheet);
        box.addView(text(shown, 24, TEXT, true), margins(14));
        box.addView(text(balance > 0.5 ? "Alacağın " + TezgahModel.tl(balance) : balance < -0.5 ? "Ona borcun " + TezgahModel.tl(-balance) : "Hesap kapalı ✓",
                22, Math.abs(balance) > 0.5 ? AMBER : GREEN, true), margins(2));

        LinearLayout actions = new LinearLayout(this);
        TextView pay = button("↓  Tahsilat", true);
        pay.setOnClickListener(v -> { sheet.dismiss(); askPayment(shown, TezgahLedger.RECEIVED); });
        actions.addView(pay, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView sale = button("+  Satış", false);
        sale.setOnClickListener(v -> { sheet.dismiss(); askSale(shown); });
        actions.addView(sale, weightedH(8, 52));
        box.addView(actions, margins(16));
        LinearLayout more = new LinearLayout(this);
        TextView out = button("Ödeme yaptım", false);
        out.setTextSize(14);
        out.setContentDescription("Ben bu alıcıya para verdim (iade ya da avans)");
        out.setOnClickListener(v -> { sheet.dismiss(); askPayment(shown, TezgahLedger.PAID); });
        more.addView(out, new LinearLayout.LayoutParams(0, dp(44), 1));
        TextView wa = button("WhatsApp'tan gönder", false);
        wa.setTextSize(14);
        wa.setOnClickListener(v -> TezgahShare.shareText(this, TezgahLedger.statement(shown, rows, TezgahShare.traderName()), ""));
        more.addView(wa, weightedH(8, 44));
        box.addView(more, margins(8));

        TextView hh = text("GEÇMİŞ · silmek için basılı tut", 12, MUTED, true);
        hh.setLetterSpacing(0.06f);
        box.addView(hh, margins(18));
        if (rows.isEmpty()) box.addView(text("Henüz satır yok.", 14, MUTED, false), margins(6));
        double running = balance;
        for (final TezgahLedger.Entry e : rows) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(46));
            row.setPadding(0, dp(4), 0, dp(4));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            boolean plus = e.signed() > 0;
            col.addView(text(TezgahLedger.title(e) + "  " + (plus ? "+" : "−") + TezgahModel.tl(Math.abs(e.amount)), 15,
                    e.kind == TezgahLedger.RECEIVED ? GREEN : AMBER, true));
            StringBuilder sub = new StringBuilder(shortDate(e.date));
            if (e.kg > 0) sub.append(" · ").append(TezgahModel.kg(e.kg));
            if (!e.detail.isEmpty()) sub.append(" · ").append(e.detail);
            if (!e.note.isEmpty()) sub.append(" · ").append(e.note);
            TextView s = text(sub.toString(), 12, MUTED, false);
            s.setMaxLines(3);
            col.addView(s);
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            TextView r = text(running > 0.5 ? TezgahModel.tl(running) : running < -0.5 ? "−" + TezgahModel.tl(-running) : "0", 13, MUTED, false);
            r.setPadding(dp(8), 0, 0, 0);
            row.addView(r);
            running = Math.round((running - e.signed()) * 100) / 100.0;
            row.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Satır silinsin mi?")
                        .setMessage(TezgahLedger.title(e) + " · " + TezgahModel.tl(Math.abs(e.amount)) + " · " + shortDate(e.date)
                                + (e.zId > 0 ? "\n\nBu satır gün sonundan geldi; silersen o günün alacağı cari hesaptan çıkar "
                                + "(akşam hesabından yeniden işleyebilirsin)." : ""))
                        .setPositiveButton("Sil", (d, w) -> {
                            boolean ok;
                            try { ok = TezgahLedger.delete(db.getWritableDatabase(), owner, e.id); }
                            catch (RuntimeException x) { ok = false; }
                            sheet.dismiss();
                            render();
                            toast(ok ? "Silindi." : "Silinemedi.");
                            if (ok) showParty(shown);
                        })
                        .setNegativeButton("Vazgeç", null)
                        .show();
                return true;
            });
            box.addView(divider(), margins(-1, Math.max(1, dp(1)), 2));
            box.addView(row);
        }
        TextView close = text("Kapat", 15, MUTED, true);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> sheet.dismiss());
        box.addView(close, margins(-1, dp(46), 12));
        sheet.show();
    }

    // ================================================================== satış

    private void askSale(String presetName) {
        final Dialog sheet = sheet();
        LinearLayout box = sheetBox(sheet);
        box.addView(label("SATIŞ / ALACAK"), margins(14));
        final EditText name = input(presetName, "Alıcının adı (aracı, tüccar, fabrika)",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        box.addView(name, margins(-1, dp(54), 8));
        if (presetName.isEmpty()) box.addView(nameChips(name), margins(6));

        // Yöntem
        final int[] method = {prefs.getInt("sale_method_" + owner, TezgahLedger.METHOD_TOTAL)};
        if (method[0] != TezgahLedger.METHOD_GRADES && method[0] != TezgahLedger.METHOD_TOTAL && method[0] != TezgahLedger.METHOD_COMMISSION) {
            method[0] = TezgahLedger.METHOD_TOTAL;
        }
        LinearLayout seg = new LinearLayout(this);
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        seg.setBackground(round(CARD, LINE, 16));
        final int[] ids = {TezgahLedger.METHOD_GRADES, TezgahLedger.METHOD_TOTAL, TezgahLedger.METHOD_COMMISSION};
        final String[] names = {"Numara", "Toplam kilo", "Komisyon"};
        final TextView[] chips = new TextView[3];
        for (int i = 0; i < 3; i++) {
            TextView c = text(names[i], 14, TEXT, true);
            c.setGravity(Gravity.CENTER);
            chips[i] = c;
            seg.addView(c, new LinearLayout.LayoutParams(0, dp(44), 1));
        }
        box.addView(seg, margins(14));

        // Numara numara
        final LinearLayout gradesBox = new LinearLayout(this);
        gradesBox.setOrientation(LinearLayout.VERTICAL);
        final EditText[] gKg = new EditText[TezgahModel.GRADES.length];
        final EditText[] gPrice = new EditText[TezgahModel.GRADES.length];
        double[] lastPrices = lastSalePrices();
        LinearLayout gh = new LinearLayout(this);
        gh.addView(text("Numara", 12, MUTED, true), new LinearLayout.LayoutParams(0, -2, 1));
        gh.addView(text("Kilo", 12, MUTED, true), new LinearLayout.LayoutParams(0, -2, 1.2f));
        gh.addView(text("Fiyat (₺/kg)", 12, MUTED, true), new LinearLayout.LayoutParams(0, -2, 1.2f));
        gradesBox.addView(gh, margins(10));
        for (int g = 0; g < TezgahModel.GRADES.length; g++) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.addView(text(TezgahLedger.gradeName(g), 15, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
            gKg[g] = input("", "0", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(0, dp(44), 1.2f);
            kp.setMarginEnd(dp(6));
            r.addView(gKg[g], kp);
            gPrice[g] = input(lastPrices[g] > 0 ? TezgahQr.field(lastPrices[g]) : "", "0",
                    InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            r.addView(gPrice[g], new LinearLayout.LayoutParams(0, dp(44), 1.2f));
            gradesBox.addView(r, margins(6));
        }
        box.addView(gradesBox);

        // Toplam kilo / komisyon
        final LinearLayout simpleBox = new LinearLayout(this);
        simpleBox.setOrientation(LinearLayout.VERTICAL);
        final TextView sKgLabel = label("KİLO");
        simpleBox.addView(sKgLabel, margins(12));
        final EditText sKg = input("", "Teslim edilen kilo", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        simpleBox.addView(sKg, margins(-1, dp(52), 6));
        final TextView sPriceLabel = label("");
        simpleBox.addView(sPriceLabel, margins(12));
        final EditText sPrice = input("", "", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        simpleBox.addView(sPrice, margins(-1, dp(52), 6));
        box.addView(simpleBox);

        final TextView amountView = text("", 30, GREEN, true);
        box.addView(amountView, margins(16));
        final TextView amountSub = text("", 13, MUTED, false);
        box.addView(amountSub);

        // Peşin alınan
        box.addView(label("ŞİMDİ ALINAN PARA"), margins(16));
        final int[] pay = {2};
        final String[] payNames = {"Tamamı", "Bir kısmı", "Hiç (veresiye)"};
        LinearLayout pseg = new LinearLayout(this);
        pseg.setPadding(dp(4), dp(4), dp(4), dp(4));
        pseg.setBackground(round(CARD, LINE, 16));
        final TextView[] pchips = new TextView[3];
        for (int i = 0; i < 3; i++) {
            TextView c = text(payNames[i], 14, TEXT, true);
            c.setGravity(Gravity.CENTER);
            pchips[i] = c;
            pseg.addView(c, new LinearLayout.LayoutParams(0, dp(44), 1));
        }
        box.addView(pseg, margins(6));
        final EditText partial = input("", "Alınan tutar (₺)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(partial, margins(-1, dp(52), 8));
        final EditText note = input("", "Not (isteğe bağlı)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(note, margins(-1, dp(50), 10));

        final TextView save = button("Kaydet", true);
        box.addView(save, margins(-1, dp(56), 16));
        TextView cancel = text("Vazgeç", 15, MUTED, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setOnClickListener(v -> sheet.dismiss());
        box.addView(cancel, margins(-1, dp(46), 4));

        final double[] result = new double[2]; // tutar, kilo
        final Runnable paint = () -> {
            for (int i = 0; i < 3; i++) {
                boolean on = ids[i] == method[0];
                chips[i].setTextColor(on ? BG : TEXT);
                chips[i].setBackground(on ? round(GREEN, 0, 12) : null);
            }
            boolean grades = method[0] == TezgahLedger.METHOD_GRADES;
            gradesBox.setVisibility(grades ? View.VISIBLE : View.GONE);
            simpleBox.setVisibility(grades ? View.GONE : View.VISIBLE);
            boolean com = method[0] == TezgahLedger.METHOD_COMMISSION;
            sPriceLabel.setText(com ? "KOMİSYON (₺/KG)" : "FİYAT (₺/KG)");
            sPrice.setHint(com ? "Kilo başı komisyon" : "Kilo fiyatı");
            if (grades) {
                double[] kg = values(gKg), pr = values(gPrice);
                result[0] = TezgahLedger.gradeAmount(kg, pr);
                result[1] = TezgahLedger.sum(kg);
                amountSub.setText(result[1] > 0 ? TezgahModel.kg(result[1]) + " · " + TezgahLedger.gradeDetail(kg, pr) : "Numara numara kilo ve fiyat yaz");
            } else {
                double kg = TezgahModel.parse(sKg.getText().toString()), pr = TezgahModel.parse(sPrice.getText().toString());
                result[0] = TezgahLedger.times(kg, pr);
                result[1] = Math.max(0, kg);
                amountSub.setText(kg > 0 && pr > 0 ? TezgahModel.kg(kg) + " × " + TezgahModel.money(pr) + (com ? " komisyon" : "")
                        : com ? "Kilo ve kilo başı komisyonu yaz" : "Kilo ve fiyatı yaz");
            }
            amountView.setText(TezgahModel.tl(result[0]));
            for (int i = 0; i < 3; i++) {
                boolean on = i == pay[0];
                pchips[i].setTextColor(on ? BG : TEXT);
                pchips[i].setBackground(on ? round(i == 2 ? AMBER : GREEN, 0, 12) : null);
            }
            partial.setVisibility(pay[0] == 1 ? View.VISIBLE : View.GONE);
            boolean ok = result[0] >= 0.01;
            save.setText(ok ? "Kaydet · " + TezgahModel.tl(result[0]) : "Tutar çıkmadı");
            save.setTextColor(ok ? BG : MUTED);
            save.setBackground(round(ok ? GREEN : CARD, ok ? 0 : LINE, 16));
        };
        for (int i = 0; i < 3; i++) {
            final int k = i;
            chips[i].setOnClickListener(v -> { method[0] = ids[k]; paint.run(); });
            pchips[i].setOnClickListener(v -> { pay[0] = k; paint.run(); if (k == 1) showKeyboard(partial); });
        }
        TextWatcher w = watcher(paint);
        for (EditText e : gKg) e.addTextChangedListener(w);
        for (EditText e : gPrice) e.addTextChangedListener(w);
        sKg.addTextChangedListener(w);
        sPrice.addTextChangedListener(w);
        paint.run();

        save.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (!TezgahLedger.validParty(n)) { name.setError("Alıcının adını yaz"); name.requestFocus(); return; }
            double amount = result[0];
            String err = TezgahLedger.amountError(TezgahLedger.SALE, amount);
            if (!err.isEmpty()) { toast(err); return; }
            double got = pay[0] == 0 ? amount : pay[0] == 1 ? TezgahModel.parse(partial.getText().toString()) : 0;
            if (pay[0] == 1 && (got <= 0 || got > amount + 0.005)) { partial.setError("0 ile " + TezgahModel.tl(amount) + " arası"); partial.requestFocus(); return; }
            String detail = method[0] == TezgahLedger.METHOD_GRADES ? TezgahLedger.gradeDetail(values(gKg), values(gPrice))
                    : TezgahModel.kg(result[1]) + " × " + TezgahModel.money(TezgahModel.parse(sPrice.getText().toString()))
                    + (method[0] == TezgahLedger.METHOD_COMMISSION ? " komisyon" : "");
            long now = System.currentTimeMillis();
            boolean ok;
            try {
                android.database.sqlite.SQLiteDatabase wdb = db.getWritableDatabase();
                ok = TezgahLedger.add(wdb, owner, n, TezgahLedger.SALE, amount, result[1], method[0], detail,
                        note.getText().toString(), today(), 0, now) > 0;
                if (ok && got >= 0.01) {
                    ok = TezgahLedger.add(wdb, owner, n, TezgahLedger.RECEIVED, got, 0, 0, "satışta alındı", "", today(), 0, now + 1) > 0;
                }
            } catch (RuntimeException x) { ok = false; }
            if (!ok) { toast("Kaydedilemedi. Tekrar dene."); return; }
            SharedPreferences.Editor ed = prefs.edit().putInt("sale_method_" + owner, method[0]);
            if (method[0] == TezgahLedger.METHOD_GRADES) ed.putString(SALE_PRICES + owner, encode(values(gPrice)));
            ed.apply();
            sheet.dismiss();
            render();
            toast(TezgahLedger.balanceLine(n, safeBalance(n)));
        });
        sheet.show();
        if (presetName.isEmpty()) showKeyboard(name);
    }

    // ================================================================== tahsilat / ödeme

    private void askPayment(String presetName, int kind) {
        final int[] k = {kind};
        final Dialog sheet = sheet();
        LinearLayout box = sheetBox(sheet);
        LinearLayout seg = new LinearLayout(this);
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        seg.setBackground(round(CARD, LINE, 16));
        final TextView a = text("↓  Tahsilat", 15, TEXT, true);
        final TextView b = text("↑  Ödeme yaptım", 15, TEXT, true);
        a.setGravity(Gravity.CENTER);
        b.setGravity(Gravity.CENTER);
        seg.addView(a, new LinearLayout.LayoutParams(0, dp(44), 1));
        seg.addView(b, new LinearLayout.LayoutParams(0, dp(44), 1));
        box.addView(seg, margins(14));
        final TextView who = label("");
        box.addView(who, margins(14));
        final EditText name = input(presetName, "Alıcının adı", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        box.addView(name, margins(-1, dp(54), 6));
        if (presetName.isEmpty()) box.addView(nameChips(name), margins(6));
        final TextView balNote = text("", 14, MUTED, true);
        box.addView(balNote, margins(8));
        box.addView(label("TUTAR (₺)"), margins(14));
        final EditText amount = input("", "0", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setTextSize(24);
        amount.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(amount, margins(-1, dp(58), 6));
        final TextView all = text("", 14, GREEN, true);
        all.setGravity(Gravity.CENTER);
        all.setPadding(0, dp(10), 0, dp(4));
        box.addView(all);
        final EditText note = input("", "Not (nakit, havale…)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(note, margins(-1, dp(50), 10));
        final TextView save = button("", true);
        box.addView(save, margins(-1, dp(56), 16));
        TextView cancel = text("Vazgeç", 15, MUTED, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setOnClickListener(v -> sheet.dismiss());
        box.addView(cancel, margins(-1, dp(46), 4));

        final Runnable paint = () -> {
            boolean rec = k[0] == TezgahLedger.RECEIVED;
            a.setTextColor(rec ? BG : TEXT);
            a.setBackground(rec ? round(GREEN, 0, 12) : null);
            b.setTextColor(!rec ? BG : TEXT);
            b.setBackground(!rec ? round(AMBER, 0, 12) : null);
            who.setText(rec ? "KİMDEN PARA GELDİ?" : "KİME PARA VERDİN?");
            String n = name.getText().toString();
            boolean valid = TezgahLedger.validParty(n);
            final double bal = valid ? safeBalance(n) : 0;
            balNote.setVisibility(valid ? View.VISIBLE : View.GONE);
            balNote.setText(bal > 0.5 ? "Alacağın " + TezgahModel.tl(bal) : bal < -0.5 ? "Ona borcun " + TezgahModel.tl(-bal) : "Hesap kapalı");
            balNote.setTextColor(Math.abs(bal) > 0.5 ? AMBER : MUTED);
            double target = rec ? bal : -bal;
            all.setVisibility(target > 0.5 ? View.VISIBLE : View.GONE);
            if (target > 0.5) {
                all.setText(rec ? "Hepsini aldım (" + TezgahModel.tl(target) + ")" : "Hepsini ödedim (" + TezgahModel.tl(target) + ")");
                all.setOnClickListener(v -> amount.setText(TezgahQr.field(target)));
            }
            double x = TezgahModel.parse(amount.getText().toString());
            boolean ok = x >= 0.01;
            save.setText(ok ? "Kaydet · " + TezgahModel.tl(x) + (rec ? " geldi" : " ödedim") : "Tutarı yaz");
            save.setTextColor(ok ? BG : MUTED);
            save.setBackground(round(ok ? (rec ? GREEN : AMBER) : CARD, ok ? 0 : LINE, 16));
        };
        a.setOnClickListener(v -> { k[0] = TezgahLedger.RECEIVED; paint.run(); });
        b.setOnClickListener(v -> { k[0] = TezgahLedger.PAID; paint.run(); });
        name.addTextChangedListener(watcher(paint));
        amount.addTextChangedListener(watcher(paint));
        paint.run();
        save.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (!TezgahLedger.validParty(n)) { name.setError("Alıcının adını yaz"); name.requestFocus(); return; }
            double x = TezgahModel.parse(amount.getText().toString());
            String err = TezgahLedger.amountError(k[0], x);
            if (!err.isEmpty()) { amount.setError(err); amount.requestFocus(); return; }
            long id;
            try {
                id = TezgahLedger.add(db.getWritableDatabase(), owner, n, k[0], x, 0, 0, "", note.getText().toString(),
                        today(), 0, System.currentTimeMillis());
            } catch (RuntimeException e) { id = -1; }
            if (id <= 0) { toast("Kaydedilemedi. Tekrar dene."); return; }
            sheet.dismiss();
            render();
            toast(TezgahLedger.balanceLine(n, safeBalance(n)));
        });
        sheet.show();
        if (presetName.isEmpty()) showKeyboard(name); else showKeyboard(amount);
    }

    // ================================================================== yardımcılar

    private double safeBalance(String name) {
        try { return TezgahLedger.balanceOf(db.getWritableDatabase(), owner, name); }
        catch (RuntimeException e) { return 0; }
    }

    /** Son alıcılar + kasa defterindeki aracılar (tekrarsız, en çok 6); dokununca ad yazılır. */
    private View nameChips(final EditText name) {
        List<String> names = new ArrayList<>();
        for (TezgahLedger.Entry e : entries) names.add(e.party);
        String lastCrate = prefs.getString(TezgahCratesActivity.PARTY_KEY + owner, "");
        if (!lastCrate.isEmpty()) names.add(lastCrate);
        try { for (TezgahCrates.Move m : TezgahCrates.moves(db.getWritableDatabase(), owner)) names.add(m.party); }
        catch (RuntimeException ignored) { }
        List<String> out = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (String n : names) {
            if (!TezgahLedger.validParty(n)) continue;
            String k = TezgahLedger.key(n);
            if (keys.contains(k)) continue;
            keys.add(k);
            out.add(n.trim());
            if (out.size() >= 6) break;
        }
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.VERTICAL);
        LinearLayout line = null;
        for (int i = 0; i < out.size(); i++) {
            if (i % 2 == 0) { line = new LinearLayout(this); chips.addView(line, margins(i == 0 ? 0 : 6)); }
            final String n = out.get(i);
            TextView c = text(n, 14, TEXT, true);
            c.setGravity(Gravity.CENTER);
            c.setSingleLine(true);
            c.setEllipsize(android.text.TextUtils.TruncateAt.END);
            c.setPadding(dp(8), 0, dp(8), 0);
            c.setBackground(round(CARD, LINE, 18));
            c.setOnClickListener(v -> { name.setText(n); name.setSelection(n.length()); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1);
            if (i % 2 == 1) lp.setMarginStart(dp(8));
            line.addView(c, lp);
        }
        return chips;
    }

    private double[] lastSalePrices() {
        double[] out = new double[TezgahModel.GRADES.length];
        String[] p = prefs.getString(SALE_PRICES + owner, "").split(";", -1);
        for (int g = 0; g < out.length && g < p.length; g++) {
            try { out[g] = p[g].isEmpty() ? 0 : Double.parseDouble(p[g]); } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    private static String encode(double[] v) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < v.length; i++) { if (i > 0) b.append(';'); if (v[i] > 0) b.append(v[i]); }
        return b.toString();
    }

    private static double[] values(EditText[] fields) {
        double[] out = new double[fields.length];
        for (int i = 0; i < fields.length; i++) out[i] = Math.max(0, TezgahModel.parse(fields[i].getText().toString()));
        return out;
    }

    private TextWatcher watcher(Runnable r) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable e) { r.run(); }
        };
    }

    private Dialog sheet() {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return d;
    }

    private LinearLayout sheetBox(Dialog sheet) {
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
        sheet.setContentView(scroll);
        Window w = sheet.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return box;
    }

    private View tile(String label, String value, int color, String sub) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(14), dp(10), dp(14), dp(12));
        t.setBackground(round(CARD, LINE, 16));
        TextView l = text(label, 11, MUTED, true);
        l.setLetterSpacing(0.08f);
        l.setSingleLine(true);
        t.addView(l);
        TextView v = text(value, 21, color, true);
        v.setSingleLine(true);
        t.addView(v);
        TextView s = text(sub, 12, MUTED, false);
        s.setSingleLine(true);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(s);
        t.setContentDescription(label + " " + value + ", " + sub);
        return t;
    }

    private TextView button(String value, boolean filled) {
        TextView t = text(value, 16, filled ? BG : GREEN, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(filled ? round(GREEN, 0, 16) : round(Color.TRANSPARENT, GREEN, 16));
        return t;
    }

    private TextView label(String v) {
        TextView t = text(v, 12, MUTED, true);
        t.setLetterSpacing(0.08f);
        return t;
    }

    private EditText input(String value, String hint, int type) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setInputType(type);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setTextSize(17);
        e.setSingleLine(true);
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setBackground(round(CARD, LINE, 12));
        return e;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        return v;
    }

    private static String shortDate(String iso) {
        if (iso == null || !iso.matches("\\d{4}-\\d{2}-\\d{2}")) return iso == null ? "" : iso;
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso);
            return d == null ? iso : new SimpleDateFormat("d MMM", new Locale("tr", "TR")).format(d);
        } catch (java.text.ParseException e) {
            return iso;
        }
    }

    private void showKeyboard(EditText e) {
        e.requestFocus();
        e.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(e, 0);
        }, 200);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int fill, int stroke, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(r));
        if (stroke != 0) g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }

    private LinearLayout.LayoutParams margins(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        return lp;
    }

    private LinearLayout.LayoutParams margins(int w, int h, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.topMargin = dp(top);
        return lp;
    }

    private LinearLayout.LayoutParams weighted(int startDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMarginStart(dp(startDp));
        return lp;
    }

    private LinearLayout.LayoutParams weightedH(int startDp, int hDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(hDp), 1);
        lp.setMarginStart(dp(startDp));
        return lp;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
