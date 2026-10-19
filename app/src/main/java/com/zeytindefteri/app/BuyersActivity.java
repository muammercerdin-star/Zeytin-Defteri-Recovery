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
        getWindow().setStatusBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().setNavigationBarColor(GreenTheme.color(R.color.zd_bg));
        if (buyerId <= 0) {
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

    @Override protected void onDestroy() {
        if (databaseHelper != null) databaseHelper.close();
        super.onDestroy();
    }

    private void render() {
        body.removeAllViews();
        try {
            if (buyerId > 0) renderDetail(); else renderList();
        } catch (RuntimeException e) {
            body.addView(text("Alıcı bilgileri okunamadı.", 16, GreenTheme.color(R.color.zd_text_soft), false));
        }
    }

    // ------------------------------------------------------------ LİSTE

    private void renderList() {
        int season = databaseHelper.getActiveSeasonYear();
        List<BuyerDirectory.Summary> all = BuyerDirectory.list(databaseHelper.getReadableDatabase(), owner, season);

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

        if (all.isEmpty()) {
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
            for (BuyerDirectory.Summary s : active) rows.addView(listRow(s), gap(10));
            if (!idle.isEmpty()) {
                TextView cap = text("BU SEZON SATIŞ YOK", 13, GreenTheme.color(R.color.zd_muted), true);
                cap.setLetterSpacing(0.06f);
                rows.addView(cap, gap(16));
                for (BuyerDirectory.Summary s : idle) rows.addView(listRow(s), gap(8));
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

    private View listRow(BuyerDirectory.Summary s) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setMinimumHeight(dp(64));
        row.setBackground(BuyerPicker.round(this, GreenTheme.color(R.color.zd_card), GreenTheme.color(R.color.zd_border), 18));
        TextView avatar = text(BuyerPicker.initial(s.name), 17, s.saleCount > 0 ? GreenTheme.color(R.color.zd_green) : GreenTheme.color(R.color.zd_muted), true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(BuyerPicker.round(this, s.saleCount > 0 ? GreenTheme.color(R.color.zd_green_chip) : GreenTheme.color(R.color.zd_surface), 0, 22));
        row.addView(avatar, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, dp(8), 0);
        copy.addView(text(s.name, 17, GreenTheme.color(R.color.zd_text), true));
        String sub = s.saleCount > 0
                ? join(s.region, s.saleCount + " satış · " + kg(s.kg))
                : join(s.region, s.lastSaleDate.isEmpty() ? "henüz satış yok" : "son satış " + BuyerDirectory.shortDate(s.lastSaleDate));
        copy.addView(text(sub, 14, GreenTheme.color(R.color.zd_muted), false));
        if (s.receivable > 0.009) copy.addView(text(ElekSaleModel.tl(s.receivable) + " alacak", 14, GreenTheme.color(R.color.zd_warning), true));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        if (s.gross > 0) row.addView(text(ElekSaleModel.tl(s.gross), 16, GreenTheme.color(R.color.zd_text), true));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(s.name + ", " + sub + (s.receivable > 0.009 ? ", " + ElekSaleModel.tl(s.receivable) + " alacak" : ""));
        row.setOnClickListener(v -> open(this, s.id));
        return row;
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

        List<BuyerDirectory.PriceRow> prices = BuyerDirectory.priceComparison(databaseHelper.getReadableDatabase(), owner, buyerId, season);
        boolean comparable = false;
        for (BuyerDirectory.PriceRow r : prices) if (r.buyerAvg > 0 && r.othersAvg > 0) comparable = true;
        if (!prices.isEmpty()) {
            LinearLayout box = card(GreenTheme.color(R.color.zd_card), GreenTheme.color(R.color.zd_border));
            box.addView(text(comparable ? "Kim daha iyi fiyat verdi?" : "Ortalama fiyatları", 17, GreenTheme.color(R.color.zd_text), true));
            box.addView(text(comparable ? "Bu sezon kg başına ortalama, senin satışlarından" : "Karşılaştırma için başka alıcıya da satış kaydı gerekir",
                    14, GreenTheme.color(R.color.zd_muted), false));
            box.addView(priceLine("Elek", buyer.name, "Diğerleri", GreenTheme.color(R.color.zd_muted), GreenTheme.color(R.color.zd_muted), false), gap(10));
            for (BuyerDirectory.PriceRow r : prices) {
                int better = GreenTheme.color(R.color.zd_green), worse = GreenTheme.color(R.color.zd_warning), plain = GreenTheme.color(R.color.zd_text);
                String mark = "";
                int color = plain;
                if (r.buyerAvg > 0 && r.othersAvg > 0) {
                    double diff = r.buyerAvg - r.othersAvg;
                    if (diff >= 0.5) { mark = " ▲"; color = better; }
                    else if (diff <= -0.5) { mark = " ▼"; color = worse; }
                }
                box.addView(priceLine(r.label, price(r.buyerAvg) + mark, price(r.othersAvg), color, plain, true), gap(8));
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
                row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
                row.addView(text(ElekSaleModel.tl(l.gross), 16, GreenTheme.color(R.color.zd_text), true));
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
        return nf.format(Math.max(0, v)) + " kg";
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
