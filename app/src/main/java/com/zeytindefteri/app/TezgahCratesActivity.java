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
 * Kasa defteri: tezgahçı ile aracı arasındaki günlük kasa hesabı.
 *
 * Aracı sabah boş kasa getirir ("Gelen kasa"), akşam dolu kasaları alır ("Giden kasa").
 * Üstte bugün gelen/giden ve sende kalan (devreden + gelen − giden); altta aracı aracı bakiye.
 * Aracıya dokununca günlük hesap, geçmiş ve WhatsApp'tan kasa hesabı açılır. Akşam hesabından da açılır.
 */
public final class TezgahCratesActivity extends Activity {
    public static final String EXTRA_NAME = "name";
    /** Açılınca doğrudan bu yönde kayıt penceresi (TezgahCrates.GAVE / RETURNED). */
    public static final String EXTRA_KIND = "kind";
    /** Son yazılan aracının adı: PARTY_KEY + owner. */
    public static final String PARTY_KEY = "crate_party_";
    /** Eski sürümden: toplam kasa (yalnız yedekte taşınır). */
    public static final String TOTAL_KEY = "crate_total_";
    private static final String PREFS = "zd_tezgah";

    private static final int BG = Color.parseColor("#050F16");
    private static final int CARD = Color.parseColor("#0F2230");
    private static final int LINE = Color.parseColor("#2A4452");
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.parseColor("#A9BAB6");
    private static final int GREEN = Color.parseColor("#8BE06A");
    private static final int GREEN_DIM = Color.parseColor("#1E3D2C");
    private static final int AMBER = Color.parseColor("#F2C35B");
    private static final int RED = Color.parseColor("#FF8A80");

    private DatabaseHelper db;
    private SharedPreferences prefs;
    private String owner = "";
    private LinearLayout body, listBox;
    private String query = "";
    private List<TezgahCrates.Move> moves = new ArrayList<>();

    public static void open(Context c, String name) {
        Intent i = new Intent(c, TezgahCratesActivity.class);
        if (name != null && !name.trim().isEmpty()) i.putExtra(EXTRA_NAME, name.trim());
        c.startActivity(i);
    }

    /** Doğrudan "Gelen kasa" / "Giden kasa" penceresiyle açar (akşam hesabından). */
    public static void openMove(Context c, int kind) {
        c.startActivity(new Intent(c, TezgahCratesActivity.class).putExtra(EXTRA_KIND, kind));
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
        int kind = getIntent().getIntExtra(EXTRA_KIND, 0);
        if (kind == TezgahCrates.GAVE || kind == TezgahCrates.RETURNED) { quickEntry = true; askMove(kind, ""); }
        else if (name != null && TezgahCrates.validParty(name)) showParty(name);
    }

    @Override protected void onDestroy() {
        if (db != null) db.close();
        super.onDestroy();
    }

    /** Akşam hesabından doğrudan kayıt için açıldı: kaydedince ya da vazgeçince geri döner. */
    private boolean quickEntry;

    private String lastParty() { return prefs.getString(PARTY_KEY + owner, ""); }

    private static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }

    private void load() {
        try { moves = TezgahCrates.moves(db.getWritableDatabase(), owner); }
        catch (RuntimeException e) { moves = new ArrayList<>(); }
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
        head.addView(text("Kasa defteri", 21, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(head);
        body.addView(text("Aracıyla kasa hesabı: sabah gelen boş kasa, akşam giden dolu kasa. Sende kalan kendiliğinden çıkar.",
                13, MUTED, false));

        TezgahCrates.Day d = TezgahCrates.day(moves, null, today());
        int h = d.held();
        LinearLayout tiles = new LinearLayout(this);
        tiles.addView(tile("BUGÜN", "↙" + d.in + "  ↗" + d.out, TEXT, "gelen · giden", null),
                new LinearLayout.LayoutParams(0, -2, 1));
        tiles.addView(tile(h >= 0 ? "SENDE KALAN" : "ONDA KALAN", Math.abs(h) + " kasa", h != 0 ? AMBER : GREEN,
                "devreden " + d.carried, null), weighted(8));
        body.addView(tiles, margins(12));

        LinearLayout actions = new LinearLayout(this);
        TextView in = button("↙  Gelen kasa", true);
        in.setContentDescription("Gelen kasa: aracıdan boş kasa geldi");
        in.setOnClickListener(v -> askMove(TezgahCrates.RETURNED, ""));
        actions.addView(in, new LinearLayout.LayoutParams(0, dp(54), 1));
        TextView out = button("↗  Giden kasa", false);
        out.setContentDescription("Giden kasa: dolu kasa aracıya gitti");
        out.setOnClickListener(v -> askMove(TezgahCrates.GAVE, ""));
        actions.addView(out, weightedH(8, 54));
        body.addView(actions, margins(14));

        List<TezgahCrates.Party> parties = TezgahCrates.parties(moves);
        if (parties.isEmpty()) {
            TextView empty = text("Henüz kasa yazılmadı.\n\nAracı sabah boş kasa getirince \"Gelen kasa\", akşam dolu kasaları "
                    + "alınca \"Giden kasa\" de. Ertesi gün kalan kasa devreden olarak görünür.", 14, MUTED, false);
            empty.setPadding(dp(14), dp(14), dp(14), dp(14));
            empty.setBackground(round(CARD, LINE, 14));
            body.addView(empty, margins(16));
            return;
        }
        if (parties.size() > 5) {
            EditText search = input(query, "Aracı ara (ad)", InputType.TYPE_CLASS_TEXT);
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) {}
                @Override public void onTextChanged(CharSequence c, int a, int b, int d) {}
                @Override public void afterTextChanged(Editable e) { query = e.toString(); renderList(); }
            });
            body.addView(search, margins(-1, dp(48), 16));
        }
        TextView lh = text("ARACILAR · dokun, günlük hesap ve geçmiş", 12, MUTED, true);
        lh.setLetterSpacing(0.06f);
        body.addView(lh, margins(16));
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(listBox, margins(6));
        renderList();
    }

    private void renderList() {
        if (listBox == null) return;
        listBox.removeAllViews();
        String q = TezgahCrates.key(query);
        int shown = 0;
        for (TezgahCrates.Party p : TezgahCrates.parties(moves)) {
            if (!q.isEmpty() && !p.key.contains(q)) continue;
            shown++;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(10), dp(12), dp(10));
            row.setBackground(round(CARD, p.balance() != 0 ? AMBER : LINE, 14));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView n = text(p.name, 16, TEXT, true);
            n.setSingleLine(true);
            n.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(n);
            col.addView(text("gelen " + p.returned + " · giden " + p.gave + (p.lastDate.isEmpty() ? "" : " · son " + shortDate(p.lastDate)),
                    12, MUTED, false));
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            int b = p.balance();
            TextView chip = text(b < 0 ? (-b) + " kasası sende" : b > 0 ? b + " kasan onda" : "Kapalı ✓", 14,
                    b != 0 ? AMBER : GREEN, true);
            chip.setPadding(dp(10), dp(4), 0, dp(4));
            row.addView(chip);
            row.setContentDescription(TezgahCrates.balanceLine(p.name, b) + ". Geçmiş için dokun.");
            final String name = p.name;
            row.setOnClickListener(v -> showParty(name));
            listBox.addView(row, margins(6));
        }
        if (shown == 0) listBox.addView(text("Bu adla aracı yok.", 14, MUTED, false), margins(8));
    }

    // ================================================================== kişi

    private void showParty(String name) {
        load();
        List<TezgahCrates.Move> list;
        try { list = TezgahCrates.movesOf(db.getWritableDatabase(), owner, name); }
        catch (RuntimeException e) { list = new ArrayList<>(); }
        int balance = 0;
        for (TezgahCrates.Move m : list) balance += m.signed();
        final String shown = list.isEmpty() ? name.trim() : list.get(0).party;
        final int bal = balance;

        final Dialog sheet = sheet();
        LinearLayout box = sheetBox(sheet);
        box.addView(text(shown, 24, TEXT, true), margins(14));
        box.addView(text(bal < 0 ? (-bal) + " kasası sende" : bal > 0 ? bal + " kasan onda" : "Kasa hesabı kapalı ✓", 20,
                bal != 0 ? AMBER : GREEN, true), margins(2));
        final TezgahCrates.Day day = TezgahCrates.day(list, TezgahCrates.key(shown), today());
        box.addView(text("Bugün: " + TezgahCrates.dayLine(day), 14, MUTED, false), margins(6));

        LinearLayout actions = new LinearLayout(this);
        TextView ret = button("↙  Gelen", true);
        ret.setOnClickListener(v -> { sheet.dismiss(); askMove(TezgahCrates.RETURNED, shown); });
        actions.addView(ret, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView gave = button("↗  Giden", false);
        gave.setOnClickListener(v -> { sheet.dismiss(); askMove(TezgahCrates.GAVE, shown); });
        actions.addView(gave, weightedH(8, 52));
        box.addView(actions, margins(16));
        if (bal != 0) {
            TextView wa = button("Kasa hesabını gönder (WhatsApp)", false);
            wa.setTextColor(GREEN);
            wa.setOnClickListener(v -> TezgahShare.shareText(this,
                    TezgahCrates.reminder(shown, bal, TezgahShare.traderName()) + "\nBugün: " + TezgahCrates.dayLine(day), ""));
            box.addView(wa, margins(-1, dp(48), 8));
        }

        TextView hh = text("GEÇMİŞ · silmek için basılı tut", 12, MUTED, true);
        hh.setLetterSpacing(0.06f);
        box.addView(hh, margins(18));
        if (list.isEmpty()) box.addView(text("Henüz hareket yok.", 14, MUTED, false), margins(6));
        int running = bal;
        for (final TezgahCrates.Move m : list) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(42));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(text((m.kind == TezgahCrates.GAVE ? "↗ Giden " : "↙ Gelen ") + m.count + " kasa", 15,
                    m.kind == TezgahCrates.GAVE ? AMBER : GREEN, true));
            String sub = shortDate(m.date) + (m.purchaseId > 0 ? " · tezgah alımından" : "") + (m.note.isEmpty() ? "" : " · " + m.note);
            col.addView(text(sub, 12, MUTED, false));
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(text(running <= 0 ? "sende " + (-running) : "onda " + running, 13, MUTED, false));
            running -= m.signed();
            row.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Hareket silinsin mi?")
                        .setMessage((m.kind == TezgahCrates.GAVE ? "Giden " : "Gelen ") + m.count + " kasa · " + shortDate(m.date)
                                + (m.purchaseId > 0 ? "\nTezgah alımı silinmez, yalnız bu kasa satırı silinir." : ""))
                        .setPositiveButton("Sil", (d, w) -> {
                            boolean ok;
                            try { ok = TezgahCrates.delete(db.getWritableDatabase(), owner, m.id); }
                            catch (RuntimeException e) { ok = false; }
                            sheet.dismiss();
                            render();
                            toast(ok ? "Silindi." : "Silinemedi.");
                            if (ok) showParty(shown);
                        })
                        .setNegativeButton("Vazgeç", null)
                        .show();
                return true;
            });
            box.addView(divider(), margins(-1, Math.max(1, dp(1)), 4));
            box.addView(row);
        }
        TextView close = text("Kapat", 15, MUTED, true);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> sheet.dismiss());
        box.addView(close, margins(-1, dp(46), 12));
        sheet.show();
    }

    // ================================================================== hareket

    private void askMove(int kind, String presetName) {
        final int[] dir = {kind};
        final Dialog sheet = sheet();
        LinearLayout box = sheetBox(sheet);

        LinearLayout seg = new LinearLayout(this);
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        seg.setBackground(round(CARD, LINE, 16));
        final TextView a = text("↗  Giden", 16, TEXT, true);
        final TextView b = text("↙  Gelen", 16, TEXT, true);
        a.setGravity(Gravity.CENTER);
        b.setGravity(Gravity.CENTER);
        seg.addView(b, new LinearLayout.LayoutParams(0, dp(46), 1));
        seg.addView(a, new LinearLayout.LayoutParams(0, dp(46), 1));
        box.addView(seg, margins(14));

        final TextView who = text("", 12, MUTED, true);
        who.setLetterSpacing(0.08f);
        box.addView(who, margins(16));
        final String startName = presetName.isEmpty() ? lastParty() : presetName;
        final EditText name = input(startName, "Aracının adı",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        box.addView(name, margins(-1, dp(54), 6));
        final TextView balanceNote = text("", 13, MUTED, true);
        box.addView(balanceNote, margins(4));

        List<String> recent = recentNames();
        if (recent.size() > 1 && presetName.isEmpty()) {
            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.VERTICAL);
            LinearLayout line = null;
            for (int i = 0; i < recent.size(); i++) {
                if (i % 2 == 0) { line = new LinearLayout(this); chips.addView(line, margins(i == 0 ? 0 : 6)); }
                final String n = recent.get(i);
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
            box.addView(chips, margins(8));
        }

        box.addView(label("KAÇ KASA"), margins(16));
        LinearLayout stepper = new LinearLayout(this);
        stepper.setGravity(Gravity.CENTER_VERTICAL);
        final EditText count = input("", "0", InputType.TYPE_CLASS_NUMBER);
        count.setGravity(Gravity.CENTER);
        count.setTextSize(26);
        count.setTypeface(Typeface.DEFAULT_BOLD);
        TextView minus = step("−");
        TextView plus = step("+");
        minus.setOnClickListener(v -> count.setText(String.valueOf(Math.max(0, parse(count) - 1))));
        plus.setOnClickListener(v -> count.setText(String.valueOf(Math.min(TezgahCrates.MAX_COUNT, parse(count) + 1))));
        stepper.addView(minus, new LinearLayout.LayoutParams(dp(54), dp(56)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(56), 1);
        cp.setMargins(dp(8), 0, dp(8), 0);
        stepper.addView(count, cp);
        stepper.addView(plus, new LinearLayout.LayoutParams(dp(54), dp(56)));
        box.addView(stepper, margins(6));
        LinearLayout quick = new LinearLayout(this);
        for (final int q : new int[]{10, 20, 50, 100}) {
            TextView c = text("+" + q, 14, GREEN, true);
            c.setGravity(Gravity.CENTER);
            c.setBackground(round(Color.TRANSPARENT, GREEN_DIM, 12));
            c.setOnClickListener(v -> count.setText(String.valueOf(Math.min(TezgahCrates.MAX_COUNT, parse(count) + q))));
            quick.addView(c, quick.getChildCount() == 0 ? new LinearLayout.LayoutParams(0, dp(40), 1) : weightedH(6, 40));
        }
        box.addView(quick, margins(8));
        final TextView all = text("", 14, GREEN, true);
        all.setGravity(Gravity.CENTER);
        all.setPadding(0, dp(10), 0, dp(4));
        box.addView(all);

        final EditText note = input("", "Not (isteğe bağlı)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(note, margins(-1, dp(50), 10));

        final TextView save = button("", true);
        box.addView(save, margins(-1, dp(56), 16));
        TextView cancel = text("Vazgeç", 15, MUTED, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setOnClickListener(v -> sheet.dismiss());
        box.addView(cancel, margins(-1, dp(46), 4));
        // Akşam hesabından açıldıysa pencere kapanınca (kayıt ya da vazgeç) oraya dönülür.
        sheet.setOnDismissListener(x -> { if (quickEntry && !isFinishing()) finish(); });

        final Runnable paint = () -> {
            boolean g = dir[0] == TezgahCrates.GAVE;
            a.setTextColor(g ? BG : TEXT);
            a.setBackground(g ? round(AMBER, 0, 12) : null);
            b.setTextColor(!g ? BG : TEXT);
            b.setBackground(!g ? round(GREEN, 0, 12) : null);
            who.setText(g ? "DOLU KASA KİME GİTTİ?" : "BOŞ KASA KİMDEN GELDİ?");
            int bal = TezgahCrates.validParty(name.getText().toString())
                    ? safeBalance(name.getText().toString()) : 0;
            balanceNote.setVisibility(TezgahCrates.validParty(name.getText().toString()) ? View.VISIBLE : View.GONE);
            balanceNote.setText(bal < 0 ? "Şu an onun " + (-bal) + " kasası sende" : bal > 0 ? "Şu an " + bal + " kasan onda" : "Kasa hesabı kapalı");
            balanceNote.setTextColor(bal != 0 ? AMBER : MUTED);
            // Giden + aracının kasası bendeyse: "hepsi gitti" kısayolu
            boolean showAll = g && bal < 0;
            all.setVisibility(showAll ? View.VISIBLE : View.GONE);
            if (showAll) {
                all.setText("Hepsi gitti (" + (-bal) + " kasa)");
                all.setOnClickListener(v -> count.setText(String.valueOf(-bal)));
            }
            int n = parse(count);
            save.setText(n > 0 ? (g ? "Kaydet · " + n + " kasa gitti" : "Kaydet · " + n + " kasa geldi") : "Kasa adedini yaz");
            save.setTextColor(n > 0 ? BG : MUTED);
            save.setBackground(round(n > 0 ? (g ? AMBER : GREEN) : CARD, n > 0 ? 0 : LINE, 18));
        };
        a.setOnClickListener(v -> { dir[0] = TezgahCrates.GAVE; paint.run(); });
        b.setOnClickListener(v -> { dir[0] = TezgahCrates.RETURNED; paint.run(); });
        TextWatcher w = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int x, int y, int z) {}
            @Override public void onTextChanged(CharSequence c, int x, int y, int z) {}
            @Override public void afterTextChanged(Editable e) { paint.run(); }
        };
        name.addTextChangedListener(w);
        count.addTextChangedListener(w);
        paint.run();

        save.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            int c = parse(count);
            if (!TezgahCrates.validParty(n)) { name.setError("Aracının adını yaz"); name.requestFocus(); return; }
            String err = TezgahCrates.countError(c);
            if (!err.isEmpty()) { count.setError(err); count.requestFocus(); return; }
            long id;
            try {
                id = TezgahCrates.add(db.getWritableDatabase(), owner, n, dir[0], c, today(), 0,
                        note.getText().toString(), System.currentTimeMillis());
            } catch (RuntimeException e) { id = -1; }
            if (id <= 0) { toast("Kaydedilemedi. Tekrar dene."); return; }
            prefs.edit().putString(PARTY_KEY + owner, n).apply();
            sheet.dismiss();
            if (quickEntry) {
                toast(TezgahCrates.balanceLine(n, safeBalance(n)));
                finish();
                return;
            }
            render();
            toast(TezgahCrates.balanceLine(n, safeBalance(n)));
        });
        sheet.show();
        if (startName.isEmpty()) showKeyboard(name); else showKeyboard(count);
    }

    private int safeBalance(String name) {
        try { return TezgahCrates.balanceOf(db.getWritableDatabase(), owner, name); }
        catch (RuntimeException e) { return 0; }
    }

    /** Son aracılar: kasa hareketi olanlar (tekrarsız, en çok 6). */
    private List<String> recentNames() {
        List<String> names = new ArrayList<>();
        for (TezgahCrates.Move m : moves) names.add(m.party);
        List<String> out = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (String n : names) {
            if (!TezgahCrates.validParty(n)) continue;
            String k = TezgahCrates.key(n);
            if (keys.contains(k)) continue;
            keys.add(k);
            out.add(n.trim());
            if (out.size() >= 6) break;
        }
        return out;
    }

    // ================================================================== yardımcılar

    private Dialog sheet() {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return d;
    }

    /** Alttan açılan koyu sayfa (Tezgah'taki "Alımı bitir" gibi). */
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

    private View tile(String label, String value, int color, String sub, View.OnClickListener click) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(14), dp(10), dp(14), dp(12));
        t.setBackground(round(CARD, LINE, 16));
        TextView l = text(label, 11, MUTED, true);
        l.setLetterSpacing(0.08f);
        t.addView(l);
        TextView v = text(value, 24, color, true);
        v.setSingleLine(true);
        t.addView(v);
        TextView s = text(sub, 12, click != null ? GREEN : MUTED, click != null);
        s.setSingleLine(true);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(s);
        t.setContentDescription(label + " " + value + ", " + sub);
        if (click != null) t.setOnClickListener(click);
        return t;
    }

    private TextView button(String value, boolean filled) {
        TextView t = text(value, 16, filled ? BG : TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(filled ? round(GREEN, 0, 16) : round(Color.TRANSPARENT, GREEN, 16));
        return t;
    }

    private TextView step(String v) {
        TextView t = text(v, 26, TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(CARD, LINE, 14));
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
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setBackground(round(CARD, LINE, 14));
        return e;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        return v;
    }

    private static int parse(EditText e) {
        String s = e.getText().toString().replaceAll("[^0-9]", "");
        if (s.isEmpty()) return 0;
        try { return (int) Math.min(Integer.MAX_VALUE, Long.parseLong(s.length() > 9 ? s.substring(0, 9) : s)); }
        catch (NumberFormatException x) { return 0; }
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
