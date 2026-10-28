package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
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
 * Üretici ekstresi: bir üreticinin bütün tezgah alımları (bütün dönemler ve Z'ler).
 * Toplam / ödenen / borç, numara numara kilo, alım alım döküm (dokununca ödeme), toplu ödeme
 * (en eski borçtan başlayarak dağıtılır), WhatsApp'tan ekstre gönderme ve yazdırma.
 */
public final class TezgahStatementActivity extends Activity {
    public static final String EXTRA_NAME = "name";
    private static final int BG = Color.parseColor("#050F16");
    private static final int CARD = Color.parseColor("#0F2230");
    private static final int LINE = Color.parseColor("#2A4452");
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.parseColor("#A9BAB6");
    private static final int GREEN = Color.parseColor("#8BE06A");
    private static final int GREEN_DIM = Color.parseColor("#1E3D2C");
    private static final int AMBER = Color.parseColor("#F2C35B");
    private static final SimpleDateFormat DM_HM = new SimpleDateFormat("d MMM HH:mm", new Locale("tr", "TR"));

    private DatabaseHelper db;
    private String owner = "", name = "";
    private LinearLayout body;
    private TezgahStatement st;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!new SessionManager(this).hasSession()) { finish(); return; }
        name = getIntent().getStringExtra(EXTRA_NAME);
        if (name == null || !TezgahStatement.hasStatement(name)) { finish(); return; }
        db = new DatabaseHelper(this);
        owner = db.getActiveOwnerUid();
        if (owner == null) owner = "";
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(4), dp(12), dp(24));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        render();
    }

    /** Kasa defterinden dönünce kasa satırı tazelensin. */
    @Override protected void onRestart() {
        super.onRestart();
        if (body != null) render();
    }

    @Override protected void onDestroy() {
        if (db != null) db.close();
        super.onDestroy();
    }

    private void render() {
        body.removeAllViews();
        try { st = TezgahStatement.of(TezgahPurchases.all(db.getWritableDatabase(), owner, 100000), name); }
        catch (RuntimeException e) { st = new TezgahStatement(); st.name = name; }

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
        head.addView(text("Üretici ekstresi", 21, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(head);

        body.addView(text(st.name, 26, TEXT, true), margins(4));
        if (!st.phone.isEmpty()) body.addView(text(st.phone, 14, MUTED, false));
        if (st.purchases.isEmpty()) {
            body.addView(text("Bu üreticiden kayıtlı alım yok.", 15, MUTED, false), margins(20));
            return;
        }

        LinearLayout r1 = new LinearLayout(this);
        r1.addView(tile("TOPLAM", TezgahModel.tl(st.gross), TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        r1.addView(tile("ÖDENEN", TezgahModel.tl(st.paid), GREEN), weighted(8));
        body.addView(r1, margins(12));
        LinearLayout r2 = new LinearLayout(this);
        r2.addView(tile("BORÇ", st.remaining() > 0.5 ? TezgahModel.tl(st.remaining()) : "Yok ✓",
                st.remaining() > 0.5 ? AMBER : GREEN), new LinearLayout.LayoutParams(0, -2, 1));
        r2.addView(tile("NET KİLO", TezgahModel.kg(st.kg), TEXT), weighted(8));
        body.addView(r2, margins(8));
        body.addView(text(st.purchases.size() + " alım · " + st.kasa + " kasa"
                + (st.averagePrice() > 0 ? " · ort. " + TezgahModel.money(st.averagePrice()) + "/kg" : ""), 14, MUTED, false), margins(8));

        // Boş kasa bakiyesi (Kasa defteri): dokununca o üreticinin kasa geçmişi açılır.
        int crates;
        try { crates = TezgahCrates.balanceOf(db.getWritableDatabase(), owner, st.name); }
        catch (RuntimeException e) { crates = 0; }
        if (TezgahCrates.validParty(st.name)) {
            TextView cl = text("📦  " + (crates > 0 ? crates + " boş kasan onda" : crates < 0 ? (-crates) + " kasası sende" : "Kasa hesabı kapalı") + "  ›",
                    14, crates > 0 ? AMBER : MUTED, true);
            cl.setPadding(dp(12), dp(10), dp(12), dp(10));
            cl.setBackground(round(CARD, crates > 0 ? AMBER : LINE, 12));
            cl.setContentDescription("Kasa defteri: " + TezgahCrates.balanceLine(st.name, crates) + ". Açmak için dokun.");
            final String who = st.name;
            cl.setOnClickListener(v -> TezgahCratesActivity.open(this, who));
            body.addView(cl, margins(10));
        }

        // Numara numara kilo
        StringBuilder grades = new StringBuilder();
        for (int g = 0; g < st.gradeKg.length; g++) {
            if (st.gradeKg[g] <= 0) continue;
            if (grades.length() > 0) grades.append("   ·   ");
            grades.append(TezgahModel.SHORT[g]).append(" ").append(TezgahModel.kg(st.gradeKg[g]));
        }
        if (grades.length() > 0) {
            TextView gl = text(grades.toString(), 14, TEXT, false);
            gl.setPadding(dp(12), dp(10), dp(12), dp(10));
            gl.setBackground(round(CARD, LINE, 12));
            body.addView(gl, margins(10));
        }

        // Düğmeler
        LinearLayout actions = new LinearLayout(this);
        if (st.remaining() > 0.5) {
            TextView pay = text("Toplu ödeme", 16, BG, true);
            pay.setGravity(Gravity.CENTER);
            pay.setBackground(round(GREEN, 0, 16));
            pay.setContentDescription("Toplu ödeme: en eski borçtan başlayarak dağıtılır");
            pay.setOnClickListener(v -> askBulkPay());
            actions.addView(pay, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        TextView wa = text("WhatsApp", 16, GREEN, true);
        wa.setGravity(Gravity.CENTER);
        wa.setBackground(round(Color.TRANSPARENT, GREEN, 16));
        wa.setContentDescription("Ekstreyi WhatsApp'tan gönder");
        wa.setOnClickListener(v -> TezgahShare.shareText(this, statementText(), st.phone));
        actions.addView(wa, actions.getChildCount() == 0 ? new LinearLayout.LayoutParams(0, dp(48), 1) : weightedH(8, 48));
        TextView pr = text("Yazdır", 16, GREEN, true);
        pr.setGravity(Gravity.CENTER);
        pr.setBackground(round(Color.TRANSPARENT, GREEN, 16));
        pr.setContentDescription("Ekstreyi fiş yazıcısından yazdır");
        pr.setOnClickListener(v -> TezgahPrinter.printText(this, statementText()));
        pr.setOnLongClickListener(v -> { TezgahPrinter.settings(this); return true; });
        actions.addView(pr, weightedH(8, 48));
        body.addView(actions, margins(14));

        TextView lh = text("ALIMLAR · dokun, ödeme yap", 12, MUTED, true);
        lh.setLetterSpacing(0.08f);
        body.addView(lh, margins(18));
        for (int i = st.purchases.size() - 1; i >= 0; i--) { // yeniden eskiye
            final TezgahPurchases.Purchase p = st.purchases.get(i);
            boolean owes = p.remaining() > 0.5;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(10), dp(14), dp(10));
            card.setBackground(round(CARD, owes ? AMBER : LINE, 14));
            LinearLayout top = new LinearLayout(this);
            top.addView(text(DM_HM.format(new Date(p.createdAt)), 15, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
            top.addView(text(TezgahModel.tl(p.gross), 15, TEXT, true));
            card.addView(top);
            LinearLayout bottom = new LinearLayout(this);
            bottom.addView(text(TezgahModel.kg(p.kg) + " · " + p.kasa + " kasa", 13, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
            bottom.addView(text(owes ? "Borç " + TezgahModel.tl(p.remaining()) : "Ödendi ✓", 13, owes ? AMBER : GREEN, true));
            card.addView(bottom, margins(2));
            card.setContentDescription(DM_HM.format(new Date(p.createdAt)) + ", " + TezgahModel.kg(p.kg) + ", "
                    + TezgahModel.tl(p.gross) + (owes ? ", borç " + TezgahModel.tl(p.remaining()) : ", ödendi"));
            card.setOnClickListener(v -> TezgahPaySheet.show(this, db.getWritableDatabase(), owner, p, this::render));
            body.addView(card, margins(8));
        }
    }

    private String statementText() {
        List<String> dates = new ArrayList<>();
        for (TezgahPurchases.Purchase p : st.purchases) dates.add(DM_HM.format(new Date(p.createdAt)));
        return st.text(TezgahShare.traderName(), dates, new SimpleDateFormat("dd.MM.yyyy", Locale.US).format(new Date()));
    }

    /** Toplu ödeme: tutar en eski borçtan başlayarak alımlara dağıtılır. */
    private void askBulkPay() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(SalePayStatus.plain(st.remaining()));
        input.setSelectAllOnFocus(true);
        input.setTextSize(22);
        FrameLayout f = new FrameLayout(this);
        f.setPadding(dp(20), dp(8), dp(20), 0);
        f.addView(input);
        new AlertDialog.Builder(this)
                .setTitle(st.name + " · toplu ödeme")
                .setMessage("Toplam borç " + TezgahModel.tl(st.remaining()) + " (" + st.debtCount() + " alım). "
                        + "Verilen para en eski borçtan başlayarak kapatılır.")
                .setView(f)
                .setPositiveButton("Kaydet", (d, w) -> {
                    double v = Math.max(0, TezgahModel.parse(input.getText().toString()));
                    if (v <= 0.009) { toast("Tutar yaz."); return; }
                    if (v > st.remaining() + 0.5) { toast("Borçtan fazla: en çok " + TezgahModel.tl(st.remaining())); return; }
                    String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                    long now = System.currentTimeMillis();
                    int done = 0;
                    for (long[] part : st.allocate(v)) {
                        try {
                            if (TezgahPayments.pay(db.getWritableDatabase(), owner, part[0], part[1] / 100.0, today, now + done)) done++;
                        } catch (RuntimeException ignored) { }
                    }
                    toast(done == 0 ? "Ödeme kaydedilemedi." : TezgahModel.tl(v) + " · " + done + " alıma dağıtıldı ✓");
                    render();
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private View tile(String label, String value, int color) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(14), dp(10), dp(14), dp(12));
        t.setBackground(round(CARD, LINE, 16));
        TextView l = text(label, 11, MUTED, true);
        l.setLetterSpacing(0.08f);
        t.addView(l);
        TextView v = text(value, 20, color, true);
        v.setSingleLine(true);
        t.addView(v);
        t.setContentDescription(label + " " + value);
        return t;
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

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
