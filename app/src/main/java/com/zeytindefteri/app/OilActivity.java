package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Yağhane · sıkım defteri. Üretici her sıkımı yazar: yağhane, tarih, zeytin kg, çıkan yağ (litre ya da kilo).
 * Ekran "kaç kilodan" aldığını (4 kilodan = 4 kg zeytinden 1 litre yağ), yağhaneleri en iyiden kötüye,
 * en iyi yağhanenin 1 ton zeytinde kaç litre fazla verdiğini ve sezon toplamını gösterir.
 * Kayıtlar telefonda; ileride izinle bölge tablosuna (adsız) katılabilir.
 */
public final class OilActivity extends Activity {
    private int BG, CARD, BORDER, TEXT, MUTED, GREEN, GREEN_SOFT, AMBER;
    private DatabaseHelper db;
    private String owner = "";
    private int year;
    private LinearLayout body;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!new SessionManager(this).hasSession()) { finish(); return; }
        BG = GreenTheme.color(R.color.zd_bg); CARD = GreenTheme.color(R.color.zd_card);
        BORDER = GreenTheme.color(R.color.zd_border); TEXT = GreenTheme.color(R.color.zd_text);
        MUTED = GreenTheme.color(R.color.zd_muted); GREEN = GreenTheme.color(R.color.zd_green);
        GREEN_SOFT = GreenTheme.color(R.color.zd_green_soft); AMBER = GreenTheme.color(R.color.zd_warning);
        db = new DatabaseHelper(this);
        owner = db.getActiveOwnerUid();
        if (owner == null) owner = "";
        year = Calendar.getInstance().get(Calendar.YEAR);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(6), dp(16), dp(28));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        render();
    }

    @Override protected void onDestroy() {
        if (db != null) db.close();
        super.onDestroy();
    }

    private List<OilPressings.Pressing> load() {
        try { return OilPressings.list(db.getWritableDatabase(), owner, year); }
        catch (RuntimeException e) { return new java.util.ArrayList<>(); }
    }

    private void render() {
        body.removeAllViews();
        List<OilPressings.Pressing> list = load();

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
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Yağhane", 22, TEXT, true));
        titles.addView(text("Sıkım defteri · " + year + " sezonu", 13, MUTED, false));
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(head);

        TextView add = text("+  Sıkım kaydı", 17, BG, true);
        add.setGravity(Gravity.CENTER);
        add.setBackground(round(GREEN, 0, 26));
        add.setContentDescription("Yeni sıkım kaydı ekle");
        add.setOnClickListener(v -> edit(null));
        body.addView(add, margins(-1, dp(56), 10));
        TextView hint = text("\"4 kilodan\" = 4 kg çiğ zeytinden 1 litre yağ. Sayı küçükse yağhane iyidir.", 13, MUTED, false);
        hint.setGravity(Gravity.CENTER);
        body.addView(hint, margins(-1, -2, 8));

        if (list.isEmpty()) {
            TextView empty = text("Henüz sıkım yok.\n\nYağhaneden dönünce kaç kilo zeytin verdiğini ve kaç litre yağ aldığını yaz; "
                    + "hangi yağhanenin daha çok yağ verdiğini burada görürsün.", 15, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(30), dp(12), dp(12));
            body.addView(empty);
            return;
        }

        // Sezon
        OilPressings.Season s = OilPressings.season(list);
        LinearLayout season = card();
        season.addView(label("SEZON · " + s.count + " sıkım"));
        season.addView(text(kg(s.oliveKg) + " zeytin → " + OilPressings.litre(s.oilLitre) + " yağ", 18, TEXT, true), margins(-1, -2, 4));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.BOTTOM);
        row.addView(text(OilPressings.kilodanText(s.kilodan()), 26, AMBER, true), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text("1 tondan " + OilPressings.litre(OilPressings.perTon(s.kilodan())), 14, MUTED, true));
        season.addView(row, margins(-1, -2, 4));
        if (s.feeTl > 0.5) season.addView(text("Sıkım ücreti toplam " + ElekSaleModel.tl(s.feeTl), 13, MUTED, false), margins(-1, -2, 4));
        body.addView(season, margins(-1, -2, 14));

        // Yağhanelerim
        List<OilPressings.Mill> mills = OilPressings.mills(list);
        LinearLayout box = card();
        box.addView(label("YAĞHANELERİM · en iyiden"));
        double maxPerTon = mills.isEmpty() ? 1 : mills.get(0).perTon();
        for (int i = 0; i < mills.size(); i++) {
            OilPressings.Mill m = mills.get(i);
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            TextView n = text((i + 1) + ". " + m.name, 16, TEXT, true);
            n.setSingleLine(true);
            n.setEllipsize(android.text.TextUtils.TruncateAt.END);
            r.addView(n, new LinearLayout.LayoutParams(0, -2, 1));
            r.addView(text(OilPressings.kilodanText(m.kilodan()), 16, i == 0 ? GREEN : TEXT, true));
            box.addView(r, margins(-1, -2, 10));
            LinearLayout bar = new LinearLayout(this);
            View fill = new View(this);
            fill.setBackground(round(i == 0 ? GREEN : MUTED, 0, 3));
            float share = maxPerTon > 0 ? (float) (m.perTon() / maxPerTon) : 0;
            bar.addView(fill, new LinearLayout.LayoutParams(0, dp(6), Math.max(0.05f, share)));
            bar.addView(new View(this), new LinearLayout.LayoutParams(0, dp(6), Math.max(0f, 1f - share)));
            box.addView(bar, margins(-1, -2, 4));
            box.addView(text(m.count + " sıkım · " + kg(m.oliveKg) + " → " + OilPressings.litre(m.oilLitre)
                    + " · 1 tondan " + OilPressings.litre(m.perTon()), 12, MUTED, false), margins(-1, -2, 2));
        }
        if (mills.size() >= 2) {
            OilPressings.Mill best = mills.get(0), worst = mills.get(mills.size() - 1);
            double extra = OilPressings.extraPerTon(best, worst);
            if (extra >= 1) {
                TextView tip = text(best.name + ", " + worst.name + "'e göre 1 ton zeytinden " + OilPressings.litre(extra)
                        + " daha fazla yağ verdi.", 14, GREEN, true);
                tip.setPadding(dp(12), dp(10), dp(12), dp(10));
                tip.setBackground(round(GREEN_SOFT, 0, 12));
                box.addView(tip, margins(-1, -2, 12));
            }
        }
        body.addView(box, margins(-1, -2, 12));

        // Sıkımlar
        TextView lh = label("SIKIMLAR · dokun, düzelt");
        body.addView(lh, margins(-1, -2, 18));
        for (final OilPressings.Pressing p : list) {
            LinearLayout c = card();
            LinearLayout top = new LinearLayout(this);
            TextView t = text(p.mill, 16, TEXT, true);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            top.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            top.addView(text(OilPressings.kilodanText(p.kilodan()), 16, AMBER, true));
            c.addView(top);
            StringBuilder sub = new StringBuilder(SalePayStatus.shortDate(p.date)).append(" · ")
                    .append(kg(p.oliveKg)).append(" → ").append(OilPressings.litre(p.oilLitre));
            if (p.acid >= 0) sub.append(" · asit %").append(String.format(new Locale("tr", "TR"), "%.1f", p.acid));
            if (p.feeTl() > 0.5) sub.append(" · ücret ").append(ElekSaleModel.tl(p.feeTl()));
            if (p.shareLitre() > 0.05) sub.append(" · yağ payı ").append(OilPressings.litre(p.shareLitre()));
            c.addView(text(sub.toString(), 13, MUTED, false), margins(-1, -2, 4));
            if (!p.note.isEmpty()) c.addView(text(p.note, 13, MUTED, false), margins(-1, -2, 2));
            c.setContentDescription(p.mill + ", " + SalePayStatus.shortDate(p.date) + ", " + kg(p.oliveKg) + " zeytinden "
                    + OilPressings.litre(p.oilLitre) + ", " + OilPressings.kilodanText(p.kilodan()) + ". Düzeltmek için dokun.");
            c.setOnClickListener(v -> edit(p));
            body.addView(c, margins(-1, -2, 8));
        }
    }

    // ================================================================== kayıt penceresi

    private void edit(final OilPressings.Pressing existing) {
        final OilPressings.Pressing p = existing != null ? existing : new OilPressings.Pressing();
        if (existing == null) p.date = SalePayStatus.ymd(Calendar.getInstance());
        final Dialog sheet = new Dialog(this);
        sheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(20));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        float r = dp(26);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        bg.setStroke(dp(1), BORDER);
        scroll.setBackground(bg);
        scroll.addView(box);

        box.addView(text(existing == null ? "Sıkım kaydı" : "Sıkımı düzelt", 22, TEXT, true));

        box.addView(label("YAĞHANE"), margins(-1, -2, 14));
        final EditText mill = field(p.mill, "Yağhanenin adı", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        box.addView(mill, margins(-1, dp(52), 6));
        List<String> recent = OilPressings.recentMills(load(), 4);
        if (!recent.isEmpty() && existing == null) {
            LinearLayout chips = new LinearLayout(this);
            for (final String n : recent) {
                TextView c = text(n, 13, TEXT, true);
                c.setGravity(Gravity.CENTER);
                c.setSingleLine(true);
                c.setEllipsize(android.text.TextUtils.TruncateAt.END);
                c.setPadding(dp(10), 0, dp(10), 0);
                c.setBackground(round(BG, BORDER, 18));
                c.setOnClickListener(v -> { mill.setText(n); mill.setSelection(n.length()); });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1);
                if (chips.getChildCount() > 0) lp.setMarginStart(dp(6));
                chips.addView(c, lp);
            }
            box.addView(chips, margins(-1, -2, 6));
        }

        final String[] date = {p.date};
        final TextView dateView = text("", 15, GREEN, true);
        dateView.setPadding(0, dp(12), 0, dp(4));
        Runnable paintDate = () -> dateView.setText("Tarih: " + SalePayStatus.longDate(date[0]) + "  ›");
        paintDate.run();
        dateView.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            String iso = SalePayStatus.iso(date[0]);
            if (SalePayStatus.valid(iso)) c.set(Integer.parseInt(iso.substring(0, 4)), Integer.parseInt(iso.substring(5, 7)) - 1,
                    Integer.parseInt(iso.substring(8, 10)));
            DatePickerDialog dlg = new DatePickerDialog(this, (dp, y, m, d) -> {
                Calendar pick = Calendar.getInstance();
                pick.set(y, m, d);
                date[0] = SalePayStatus.ymd(pick);
                paintDate.run();
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            dlg.getDatePicker().setMaxDate(System.currentTimeMillis());
            dlg.show();
        });
        box.addView(dateView);

        LinearLayout nums = new LinearLayout(this);
        LinearLayout c1 = new LinearLayout(this);
        c1.setOrientation(LinearLayout.VERTICAL);
        c1.addView(label("VERİLEN ZEYTİN (kg)"));
        final EditText olive = field(p.oliveKg > 0 ? SalePayStatus.plain(p.oliveKg) : "", "0", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        olive.setTextSize(22);
        c1.addView(olive, margins(-1, dp(56), 6));
        nums.addView(c1, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout c2 = new LinearLayout(this);
        c2.setOrientation(LinearLayout.VERTICAL);
        final boolean[] inKg = {false};
        final TextView unit = label("ÇIKAN YAĞ (litre) ⇄");
        unit.setTextColor(GREEN);
        unit.setContentDescription("Yağ birimini değiştir: litre ya da kilo");
        c2.addView(unit);
        final EditText oil = field(p.oilLitre > 0 ? SalePayStatus.plain(p.oilLitre) : "", "0", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        oil.setTextSize(22);
        c2.addView(oil, margins(-1, dp(56), 6));
        LinearLayout.LayoutParams c2p = new LinearLayout.LayoutParams(0, -2, 1);
        c2p.setMarginStart(dp(10));
        nums.addView(c2, c2p);
        box.addView(nums, margins(-1, -2, 12));

        final TextView result = text("", 28, AMBER, true);
        result.setGravity(Gravity.CENTER);
        final TextView resultSub = text("", 14, MUTED, false);
        resultSub.setGravity(Gravity.CENTER);
        box.addView(result, margins(-1, -2, 12));
        box.addView(resultSub, margins(-1, -2, 2));

        // Ücret
        box.addView(label("SIKIM ÜCRETİ (isteğe bağlı)"), margins(-1, -2, 16));
        final String[] feeTypes = {OilPressings.FEE_NONE, OilPressings.FEE_PER_KG, OilPressings.FEE_SHARE};
        final String[] feeLabels = {"Yok / bilmiyorum", "TL / kg zeytin", "Yağ payı %"};
        final String[] fee = {p.feeType == null || p.feeType.isEmpty() ? OilPressings.FEE_NONE : p.feeType};
        final TextView[] feeChips = new TextView[3];
        final EditText feeValue = field(p.feeValue > 0 ? SalePayStatus.plain(p.feeValue) : "", "Tutar", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout feeRow = new LinearLayout(this);
        final Runnable paintFee = () -> {
            for (int i = 0; i < 3; i++) {
                boolean on = feeTypes[i].equals(fee[0]);
                feeChips[i].setTextColor(on ? BG : TEXT);
                feeChips[i].setBackground(round(on ? GREEN : BG, on ? 0 : BORDER, 14));
            }
            feeValue.setVisibility(OilPressings.FEE_NONE.equals(fee[0]) ? View.GONE : View.VISIBLE);
            feeValue.setHint(OilPressings.FEE_SHARE.equals(fee[0]) ? "Yağ payı %" : "TL / kg");
        };
        for (int i = 0; i < 3; i++) {
            final int k = i;
            TextView c = text(feeLabels[i], 12, TEXT, true);
            c.setGravity(Gravity.CENTER);
            c.setOnClickListener(v -> { fee[0] = feeTypes[k]; paintFee.run(); });
            feeChips[i] = c;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1);
            if (i > 0) lp.setMarginStart(dp(6));
            feeRow.addView(c, lp);
        }
        box.addView(feeRow, margins(-1, -2, 6));
        box.addView(feeValue, margins(-1, dp(50), 6));
        paintFee.run();

        LinearLayout extra = new LinearLayout(this);
        final EditText acid = field(p.acid >= 0 ? SalePayStatus.plain(p.acid) : "", "Asit % (isteğe bağlı)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        extra.addView(acid, new LinearLayout.LayoutParams(0, dp(50), 1));
        box.addView(extra, margins(-1, -2, 12));
        final EditText note = field(p.note, "Not (soğuk sıkım, sıra, vb.)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(note, margins(-1, dp(50), 8));

        final Runnable calc = () -> {
            double o = Math.max(0, TezgahModel.parse(olive.getText().toString()));
            double raw = Math.max(0, TezgahModel.parse(oil.getText().toString()));
            double lt = inKg[0] ? OilPressings.litreFromKg(raw) : raw;
            double k = OilPressings.kilodan(o, lt);
            result.setText(k > 0 ? OilPressings.kilodanText(k) : "— kilodan");
            String err = o > 0 && raw > 0 ? OilPressings.error(o, lt, "x") : "";
            if (!err.isEmpty()) { resultSub.setTextColor(AMBER); resultSub.setText(err); }
            else {
                resultSub.setTextColor(MUTED);
                resultSub.setText(k > 0 ? "1 ton zeytinden " + OilPressings.litre(OilPressings.perTon(k))
                        + (inKg[0] ? " · " + OilPressings.litre(lt) + " yağ" : "") : "Zeytin kilosunu ve çıkan yağı yaz");
            }
        };
        unit.setOnClickListener(v -> {
            inKg[0] = !inKg[0];
            unit.setText(inKg[0] ? "ÇIKAN YAĞ (kilo) ⇄" : "ÇIKAN YAĞ (litre) ⇄");
            calc.run();
        });
        TextWatcher w = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { calc.run(); }
        };
        olive.addTextChangedListener(w);
        oil.addTextChangedListener(w);
        calc.run();

        TextView save = text("Kaydet", 18, BG, true);
        save.setGravity(Gravity.CENTER);
        save.setBackground(round(GREEN, 0, 18));
        save.setOnClickListener(v -> {
            double o = Math.max(0, TezgahModel.parse(olive.getText().toString()));
            double raw = Math.max(0, TezgahModel.parse(oil.getText().toString()));
            double lt = inKg[0] ? OilPressings.litreFromKg(raw) : raw;
            String name = mill.getText().toString().trim();
            String err = OilPressings.error(o, lt, name);
            if (name.isEmpty()) { mill.setError(err); mill.requestFocus(); return; }
            if (o <= 0) { olive.setError(err); olive.requestFocus(); return; }
            if (lt <= 0) { oil.setError(err); oil.requestFocus(); return; }
            Runnable doSave = () -> {
                p.mill = name;
                p.date = date[0];
                p.oliveKg = o;
                p.oilLitre = lt;
                p.feeType = fee[0];
                p.feeValue = OilPressings.FEE_NONE.equals(fee[0]) ? 0 : Math.max(0, TezgahModel.parse(feeValue.getText().toString()));
                String a = acid.getText().toString().trim();
                p.acid = a.isEmpty() ? -1 : Math.max(0, TezgahModel.parse(a));
                p.note = note.getText().toString().trim();
                long id;
                try { id = OilPressings.save(db.getWritableDatabase(), owner, p, System.currentTimeMillis()); }
                catch (RuntimeException e) { id = -1; }
                if (id <= 0) { toast("Kaydedilemedi. Tekrar dene."); return; }
                sheet.dismiss();
                toast("Kaydedildi ✓ " + OilPressings.kilodanText(OilPressings.kilodan(o, lt)));
                render();
            };
            if (!err.isEmpty()) {
                // Olağan dışı sonuç: yanlış yazım olabilir, yine de kaydetmeye izin ver.
                new AlertDialog.Builder(this).setTitle("Emin misin?").setMessage(err)
                        .setPositiveButton("Yine de kaydet", (d, x) -> doSave.run())
                        .setNegativeButton("Düzelteyim", null).show();
                return;
            }
            doSave.run();
        });
        box.addView(save, margins(-1, dp(56), 16));
        if (existing != null) {
            TextView del = text("Sil", 15, GreenTheme.color(R.color.zd_danger), true);
            del.setGravity(Gravity.CENTER);
            del.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Sıkım silinsin mi?")
                    .setMessage(p.mill + " · " + SalePayStatus.shortDate(p.date))
                    .setPositiveButton("Sil", (d, x) -> {
                        OilPressings.delete(db.getWritableDatabase(), owner, p.id);
                        sheet.dismiss();
                        render();
                    })
                    .setNegativeButton("Vazgeç", null).show());
            box.addView(del, margins(-1, dp(46), 6));
        }

        sheet.setContentView(scroll);
        Window win = sheet.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            win.setGravity(Gravity.BOTTOM);
            win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        sheet.show();
    }

    // ================================================================== yardımcılar

    private static String kg(double v) { return String.format(new Locale("tr", "TR"), "%,d", Math.round(v)) + " kg"; }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(round(CARD, BORDER, 18));
        return c;
    }

    private TextView label(String v) {
        TextView t = text(v, 11, MUTED, true);
        t.setLetterSpacing(0.08f);
        return t;
    }

    private EditText field(String value, String hint, int type) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setTextSize(16);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setBackground(round(BG, BORDER, 14));
        return e;
    }

    private TextView text(String v, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(v);
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

    private LinearLayout.LayoutParams margins(int w, int h, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.topMargin = dp(top);
        return lp;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
