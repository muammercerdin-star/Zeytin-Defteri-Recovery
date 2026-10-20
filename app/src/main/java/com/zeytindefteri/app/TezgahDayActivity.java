package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Alım defteri: tüccarın günlük alımları Excel görünümünde.
 * - Solda sabit "Üretici" sütunu, sağda yana kayan sütunlar (kasa, numara kiloları, net, ortalama, tutar, ödenen, kalan).
 * - En altta TOPLAM satırı; üstte gün özeti; ‹ › ile önceki günler.
 * - Satıra dokununca: QR göster, fişi gönder, kalan borcu ödedim, sil.
 * - "Excel'e aktar": İndirilenler'e .csv (Excel/Sheets açar) ve paylaş.
 */
public final class TezgahDayActivity extends Activity {
    private static final int BG = Color.parseColor("#050F16");
    private static final int CARD = Color.parseColor("#0F2230");
    private static final int ROW_ALT = Color.parseColor("#0B1A24");
    private static final int HEAD = Color.parseColor("#163142");
    private static final int LINE = Color.parseColor("#2A4452");
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.parseColor("#A9BAB6");
    private static final int GREEN = Color.parseColor("#8BE06A");
    private static final int GREEN_DIM = Color.parseColor("#1E3D2C");
    private static final int AMBER = Color.parseColor("#F2C35B");
    private static final Locale TR = new Locale("tr", "TR");

    private static final int ROW_H = 50, HEAD_H = 40, NAME_W = 132;
    private static final String[] COLS = {"Kasa", "Double", "1", "2", "3", "4", "Yağlık", "Net kg", "Ort ₺/kg", "Tutar", "Ödenen", "Kalan"};
    private static final int[] WIDTHS = {54, 64, 58, 58, 58, 58, 64, 78, 82, 96, 96, 92};

    private DatabaseHelper db;
    private String owner = "";
    private final Calendar day = Calendar.getInstance();
    private LinearLayout body;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!new SessionManager(this).hasSession()) { finish(); return; }
        db = new DatabaseHelper(this);
        owner = db.getActiveOwnerUid();
        if (owner == null) owner = "";
        if (state != null) day.setTimeInMillis(state.getLong("day", day.getTimeInMillis()));
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

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putLong("day", day.getTimeInMillis());
    }

    @Override protected void onDestroy() {
        if (db != null) db.close();
        super.onDestroy();
    }

    private String iso() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(day.getTime()); }

    private boolean isToday() {
        Calendar now = Calendar.getInstance();
        return now.get(Calendar.YEAR) == day.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR);
    }

    private List<TezgahPurchases.Purchase> load() {
        try { return TezgahPurchases.forDay(db.getWritableDatabase(), owner, iso()); }
        catch (RuntimeException e) { return new java.util.ArrayList<>(); }
    }

    // ================================================================== ekran

    private void render() {
        body.removeAllViews();
        final List<TezgahPurchases.Purchase> list = load();
        body.addView(header(list));
        body.addView(dateBar(), margins(-1, dp(48), 6));
        body.addView(summary(list), margins(-1, -2, 10));
        if (list.isEmpty()) {
            TextView empty = text(isToday() ? "Bugün henüz alım yok. Tezgahta bir alımı bitirince burada satır olarak görünür."
                    : "Bu gün alım yok.", 15, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(32), dp(16), dp(32));
            body.addView(empty);
            return;
        }
        body.addView(table(list), margins(-1, -2, 12));
        TextView hint = text("Satıra dokun: QR göster · fişi gönder · borcu kapat · sil. Tablo sağa kayar.", 12, MUTED, false);
        body.addView(hint, margins(-1, -2, 8));
    }

    private View header(List<TezgahPurchases.Purchase> list) {
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
        row.addView(text("Alım defteri", 21, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView excel = text("Excel'e aktar", 14, list.isEmpty() ? MUTED : GREEN, true);
        excel.setGravity(Gravity.CENTER);
        excel.setPadding(dp(14), 0, dp(14), 0);
        excel.setBackground(round(Color.TRANSPARENT, list.isEmpty() ? LINE : GREEN, 20));
        excel.setContentDescription("Bu günün alımlarını Excel dosyası olarak kaydet ve paylaş");
        excel.setOnClickListener(v -> { if (!list.isEmpty()) export(list); });
        row.addView(excel, new LinearLayout.LayoutParams(-2, dp(40)));
        return row;
    }

    private View dateBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(round(CARD, LINE, 16));
        TextView prev = text("‹", 26, GREEN, true);
        prev.setGravity(Gravity.CENTER);
        prev.setContentDescription("Önceki gün");
        prev.setOnClickListener(v -> { day.add(Calendar.DAY_OF_YEAR, -1); render(); });
        bar.addView(prev, new LinearLayout.LayoutParams(dp(56), -1));
        String label = new SimpleDateFormat("d MMMM yyyy, EEEE", TR).format(day.getTime());
        TextView mid = text(isToday() ? "Bugün · " + label : label, 16, TEXT, true);
        mid.setGravity(Gravity.CENTER);
        bar.addView(mid, new LinearLayout.LayoutParams(0, -1, 1));
        boolean today = isToday();
        TextView next = text("›", 26, today ? LINE : GREEN, true);
        next.setGravity(Gravity.CENTER);
        next.setContentDescription("Sonraki gün");
        next.setEnabled(!today);
        next.setOnClickListener(v -> { if (!isToday()) { day.add(Calendar.DAY_OF_YEAR, 1); render(); } });
        bar.addView(next, new LinearLayout.LayoutParams(dp(56), -1));
        return bar;
    }

    private View summary(List<TezgahPurchases.Purchase> list) {
        int kasa = 0;
        double kg = 0, gross = 0, paid = 0;
        for (TezgahPurchases.Purchase p : list) { kasa += p.kasa; kg += p.kg; gross += p.gross; paid += p.paid; }
        double due = Math.max(0, gross - paid);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout r1 = new LinearLayout(this), r2 = new LinearLayout(this);
        r1.addView(tile("TUTAR", TezgahModel.tl(gross), GREEN), tileParams(false));
        r1.addView(tile("NET KİLO", TezgahModel.kg(kg), TEXT), tileParams(true));
        r2.addView(tile("ORTALAMA", kg > 0 ? TezgahModel.money(gross / kg) + "/kg" : "—", TEXT), tileParams(false));
        r2.addView(tile(due > 0.5 ? "BORÇ" : "ALIM", due > 0.5 ? TezgahModel.tl(due) : list.size() + " alım · " + kasa + " kasa",
                due > 0.5 ? AMBER : TEXT), tileParams(true));
        grid.addView(r1);
        grid.addView(r2, margins(-1, -2, 8));
        return grid;
    }

    private View tile(String label, String value, int color) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(14), dp(10), dp(12), dp(10));
        t.setBackground(round(CARD, LINE, 14));
        TextView l = text(label, 11, MUTED, true);
        l.setLetterSpacing(0.08f);
        t.addView(l);
        TextView v = text(value, 19, color, true);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(v, margins(-1, -2, 2));
        return t;
    }

    private LinearLayout.LayoutParams tileParams(boolean right) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        if (right) lp.setMarginStart(dp(8));
        return lp;
    }

    // ================================================================== tablo

    /** Solda sabit ad sütunu + sağda yana kayan sütunlar; satırlar sabit yükseklikte hizalı. */
    private View table(final List<TezgahPurchases.Purchase> list) {
        LinearLayout frame = new LinearLayout(this);
        frame.setBackground(round(CARD, LINE, 14));
        frame.setPadding(dp(1), dp(1), dp(1), dp(1));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);

        // Başlık
        left.addView(headCell("Üretici", NAME_W, Gravity.START | Gravity.CENTER_VERTICAL));
        LinearLayout head = new LinearLayout(this);
        for (int c = 0; c < COLS.length; c++) head.addView(headCell(COLS[c], WIDTHS[c], Gravity.END | Gravity.CENTER_VERTICAL));
        right.addView(head);

        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.US);
        int kasa = 0;
        double kg = 0, gross = 0, paid = 0;
        double[] gsum = new double[TezgahModel.GRADES.length];
        for (int i = 0; i < list.size(); i++) {
            final TezgahPurchases.Purchase p = list.get(i);
            int bg = i % 2 == 0 ? CARD : ROW_ALT;
            double[] g = TezgahModel.decode(p.data).netArray();
            for (int k = 0; k < g.length; k++) gsum[k] += g[k];
            kasa += p.kasa; kg += p.kg; gross += p.gross; paid += p.paid;

            LinearLayout nameCell = new LinearLayout(this);
            nameCell.setOrientation(LinearLayout.VERTICAL);
            nameCell.setGravity(Gravity.CENTER_VERTICAL);
            nameCell.setPadding(dp(10), 0, dp(6), 0);
            nameCell.setBackgroundColor(bg);
            TextView n = text(p.seller.isEmpty() ? "Adsız" : p.seller, 14, TEXT, true);
            n.setSingleLine(true);
            n.setEllipsize(TextUtils.TruncateAt.END);
            nameCell.addView(n);
            nameCell.addView(text(hm.format(new Date(p.createdAt)), 11, MUTED, false));
            nameCell.setOnClickListener(v -> actions(p));
            left.addView(nameCell, new LinearLayout.LayoutParams(dp(NAME_W), dp(ROW_H)));
            left.addView(rule(NAME_W));

            LinearLayout r = new LinearLayout(this);
            r.setBackgroundColor(bg);
            r.setOnClickListener(v -> actions(p));
            String[] vals = {
                    String.valueOf(p.kasa),
                    num(g[0]), num(g[1]), num(g[2]), num(g[3]), num(g[4]), num(g[5]),
                    num(p.kg),
                    p.kg > 0 ? price(p.gross / p.kg) : "—",
                    TezgahModel.tl(p.gross),
                    TezgahModel.tl(p.paid),
                    p.remaining() > 0.5 ? TezgahModel.tl(p.remaining()) : "—"};
            for (int c = 0; c < vals.length; c++) {
                int color = c == 9 ? GREEN : (c == 11 && p.remaining() > 0.5 ? AMBER : ("—".equals(vals[c]) ? MUTED : TEXT));
                r.addView(cell(vals[c], WIDTHS[c], color, c == 7 || c == 9));
            }
            right.addView(r, new LinearLayout.LayoutParams(-2, dp(ROW_H)));
            right.addView(rule(totalWidth()));
        }

        // TOPLAM satırı
        TextView tl = text("TOPLAM · " + list.size(), 14, BG, true);
        tl.setGravity(Gravity.CENTER_VERTICAL);
        tl.setPadding(dp(10), 0, dp(6), 0);
        tl.setBackgroundColor(GREEN);
        left.addView(tl, new LinearLayout.LayoutParams(dp(NAME_W), dp(ROW_H)));
        LinearLayout tr = new LinearLayout(this);
        tr.setBackgroundColor(GREEN);
        String[] tv = {
                String.valueOf(kasa),
                num(gsum[0]), num(gsum[1]), num(gsum[2]), num(gsum[3]), num(gsum[4]), num(gsum[5]),
                num(kg),
                kg > 0 ? price(gross / kg) : "—",
                TezgahModel.tl(gross),
                TezgahModel.tl(paid),
                gross - paid > 0.5 ? TezgahModel.tl(gross - paid) : "—"};
        for (int c = 0; c < tv.length; c++) tr.addView(cell(tv[c], WIDTHS[c], BG, true));
        right.addView(tr, new LinearLayout.LayoutParams(-2, dp(ROW_H)));

        frame.addView(left, new LinearLayout.LayoutParams(dp(NAME_W), -2));
        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        frame.addView(divider, new LinearLayout.LayoutParams(dp(2), -1));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(true);
        hs.addView(right);
        frame.addView(hs, new LinearLayout.LayoutParams(0, -2, 1));
        return frame;
    }

    private int totalWidth() {
        int w = 0;
        for (int v : WIDTHS) w += v;
        return w;
    }

    private TextView headCell(String label, int widthDp, int gravity) {
        TextView t = text(label, 12, MUTED, true);
        t.setGravity(gravity);
        t.setPadding(dp(10), 0, dp(10), 0);
        t.setBackgroundColor(HEAD);
        t.setSingleLine(true);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), dp(HEAD_H)));
        return t;
    }

    private TextView cell(String value, int widthDp, int color, boolean bold) {
        TextView t = text(value, 14, color, bold);
        t.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        t.setPadding(dp(6), 0, dp(10), 0);
        t.setSingleLine(true);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), -1));
        return t;
    }

    private View rule(int widthDp) {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), Math.max(1, dp(1) / 2)));
        return v;
    }

    /** Tabloda kilo: "238", "42,5", sıfırsa "—". */
    private static String num(double v) {
        if (v <= 0.04) return "—";
        String s = TezgahModel.kg(v);
        return s.substring(0, s.length() - 3);
    }

    private static String price(double v) {
        String s = TezgahModel.money(v);
        return s.substring(0, s.length() - 2);
    }

    // ================================================================== satır işlemleri

    private void actions(final TezgahPurchases.Purchase p) {
        final TezgahModel m = TezgahModel.decode(p.data);
        final String name = p.seller.isEmpty() ? "Adsız" : p.seller;
        final String receipt = m.receipt("Zeytin alım fişi", "Satan", p.seller, displayDate(p), p.paid);
        final String[] items = p.remaining() > 0.5
                ? new String[]{"QR göster", "Fişi gönder (WhatsApp)", "Kalan borcu ödedim (" + TezgahModel.tl(p.remaining()) + ")", "Sil"}
                : new String[]{"QR göster", "Fişi gönder (WhatsApp)", "Sil"};
        new AlertDialog.Builder(this)
                .setTitle(name + " · " + TezgahModel.kg(p.kg) + " · " + TezgahModel.tl(p.gross))
                .setItems(items, (d, i) -> {
                    String a = items[i];
                    if (a.startsWith("QR")) {
                        TezgahShare.showQr(this, m, p.date, p.seller, p.paid, receipt, p.phone);
                    } else if (a.startsWith("Fişi")) {
                        TezgahShare.shareText(this, receipt, p.phone);
                    } else if (a.startsWith("Kalan")) {
                        TezgahPurchases.setPaid(db.getWritableDatabase(), owner, p.id, p.gross);
                        toast("Borç kapandı ✓");
                        render();
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("Alım silinsin mi?")
                                .setMessage(name + " · " + TezgahModel.kg(p.kg) + " · " + TezgahModel.tl(p.gross))
                                .setPositiveButton("Sil", (x, y) -> {
                                    TezgahPurchases.delete(db.getWritableDatabase(), owner, p.id);
                                    render();
                                })
                                .setNegativeButton("Vazgeç", null)
                                .show();
                    }
                })
                .show();
    }

    private static String displayDate(TezgahPurchases.Purchase p) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(new Date(p.createdAt));
    }

    // ================================================================== Excel

    /** İndirilenler'e .csv yazar (Android 10+ izin istemez); eski sürümde doğrudan paylaşır. */
    private void export(List<TezgahPurchases.Purchase> list) {
        String csv = TezgahPurchases.csv(list);
        String fileName = "Zeytin-Defteri-Alim-" + iso() + ".csv";
        Uri uri = null;
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                v.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download");
                uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri != null) {
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out == null) throw new java.io.IOException("yazılamadı");
                        out.write(csv.getBytes(StandardCharsets.UTF_8));
                    }
                }
            } catch (Exception e) {
                uri = null;
            }
        }
        final Uri saved = uri;
        if (saved == null) {
            // Eski Android ya da yazılamadı: dosyayı paylaşım menüsüyle gönder (metin olarak).
            try {
                Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, fileName).putExtra(Intent.EXTRA_TEXT, csv);
                startActivity(Intent.createChooser(send, "Alım defterini gönder"));
            } catch (RuntimeException e) {
                toast("Paylaşılacak uygulama bulunamadı.");
            }
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Excel dosyası hazır ✓")
                .setMessage("İndirilenler klasörüne kaydedildi:\n" + fileName
                        + "\n\nExcel, Google E-Tablolar ya da WPS ile açılır.")
                .setPositiveButton("Paylaş", (d, w) -> {
                    try {
                        Intent send = new Intent(Intent.ACTION_SEND).setType("text/csv")
                                .putExtra(Intent.EXTRA_STREAM, saved)
                                .putExtra(Intent.EXTRA_SUBJECT, fileName)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(send, "Alım defterini gönder"));
                    } catch (RuntimeException e) {
                        toast("Paylaşılacak uygulama bulunamadı.");
                    }
                })
                .setNeutralButton("Aç", (d, w) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(saved, "text/csv")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                    } catch (RuntimeException e) {
                        toast("Açacak uygulama yok; Paylaş ile gönderebilirsin.");
                    }
                })
                .setNegativeButton("Tamam", null)
                .show();
    }

    // ================================================================== yardımcılar

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
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
