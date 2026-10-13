package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Elek kaydı makbuzu (13.10.2026 tasarımı): koyu zemin üzerinde krem "defter" fişi.
 * Paylaş (metin), not ekle/düzenle ve kaydı sil mevcut ElekRepository yöntemlerini kullanır.
 * Silme, diğer kayıt türlerindeki gibi buluta bir silme işareti bırakır (recordCloudDeletion).
 */
public class ElekReceiptActivity extends Activity {

    static final String EXTRA_RECORD_ID = "record_id";

    private static final String[] GRADE_KEYS = {"DOUBLE", "1", "2", "3", "4", "YAGLIK"};
    private static final int PAPER = Color.parseColor("#F3EBD8");
    private static final int INK = Color.parseColor("#1F2A33");
    private static final int INK_SOFT = Color.parseColor("#4A5560");
    private static final int PAPER_LABEL = Color.parseColor("#6B6150");
    private static final int PAPER_LINE = Color.parseColor("#DED2B6");
    private static final int PAPER_RULE = Color.parseColor("#CDBF9F");
    private static final int PAPER_ACCENT = Color.parseColor("#3E6B2E");
    private static final int PAPER_TOTAL = Color.parseColor("#2F5A22");

    private DatabaseHelper databaseHelper;
    private ElekRepository elekRepository;
    private long recordId;

    private final Locale turkishLocale = new Locale("tr", "TR");
    private DecimalFormat numberFormat;
    private DecimalFormat averageFormat;

    // Yüklenen kayıt
    private String gardenName = "";
    private String dateIso = "";
    private String note = "";
    private final double[] kg = new double[6];
    private final double[] price = new double[6];
    private final double[] lineTotal = new double[6];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().hide();
        getWindow().setStatusBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().setNavigationBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().getDecorView().setSystemUiVisibility(0);

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(turkishLocale);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        numberFormat = new DecimalFormat("#,##0.##", symbols);
        averageFormat = new DecimalFormat("#,##0.0", symbols);

        databaseHelper = new DatabaseHelper(this);
        elekRepository = new ElekRepository(databaseHelper);
        recordId = getIntent().getLongExtra(EXTRA_RECORD_ID, -1L);

        if (!loadRecord()) {
            Toast.makeText(this, "Elek kaydı bulunamadı.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        render();
    }

    private boolean loadRecord() {
        if (recordId <= 0) return false;
        Cursor c = null;
        try {
            c = elekRepository.getElekRecord(recordId);
            if (c == null || !c.moveToFirst()) return false;
            // Kullanıcı ayrımı: başka bir hesaba ait kayıt gösterilmez.
            String owner = string(c, "owner_uid");
            String active = databaseHelper.getActiveOwnerUid();
            if (!owner.isEmpty() && !owner.equals(active)) return false;
            gardenName = string(c, "garden_name");
            dateIso = string(c, "record_date");
            note = string(c, "note");
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.close();
        }

        for (int i = 0; i < 6; i++) { kg[i] = 0; price[i] = 0; lineTotal[i] = 0; }
        Cursor g = null;
        try {
            g = elekRepository.getElekGrades(recordId);
            while (g != null && g.moveToNext()) {
                int index = gradeIndexOf(string(g, "grade"));
                if (index < 0) continue;
                kg[index] = number(g, "kg");
                price[index] = number(g, "price_per_kg");
                lineTotal[index] = number(g, "line_total");
            }
        } catch (Exception ignored) {
        } finally {
            if (g != null) g.close();
        }
        return true;
    }

    private void render() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(GreenTheme.color(R.color.zd_bg));
        screen.setFitsSystemWindows(true);

        // Üst çubuk
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(12), dp(16), dp(8));
        TextView back = text("‹", 30, GreenTheme.color(R.color.zd_text), false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Geri");
        back.setBackgroundResource(android.R.drawable.list_selector_background);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = text("Elek kaydı", 20, GreenTheme.color(R.color.zd_text), true);
        title.setPadding(dp(4), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        screen.addView(bar);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(4), dp(18), dp(24));
        scroll.addView(body);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        body.addView(buildPaper());

        // Eylemler
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView share = actionButton("Paylaş", true);
        share.setOnClickListener(v -> share());
        actions.addView(share, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView noteButton = actionButton(note.trim().isEmpty() ? "Not ekle" : "Notu düzenle", false);
        noteButton.setOnClickListener(v -> editNote());
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, dp(48), 1);
        np.setMargins(dp(10), 0, 0, 0);
        actions.addView(noteButton, np);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2);
        ap.setMargins(0, dp(16), 0, 0);
        body.addView(actions, ap);

        TextView delete = text("Kaydı sil", 14, Color.parseColor("#FF8A7A"), true);
        delete.setGravity(Gravity.CENTER);
        delete.setBackground(rounded(Color.TRANSPARENT, Color.parseColor("#5A2F31"), 22));
        delete.setContentDescription("Bu elek kaydını sil");
        delete.setOnClickListener(v -> confirmDelete());
        LinearLayout.LayoutParams dpParams = new LinearLayout.LayoutParams(-1, dp(44));
        dpParams.setMargins(0, dp(10), 0, 0);
        body.addView(delete, dpParams);

        setContentView(screen);
    }

    private View buildPaper() {
        LinearLayout paper = new LinearLayout(this);
        paper.setOrientation(LinearLayout.VERTICAL);
        paper.setPadding(dp(18), dp(20), dp(18), dp(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(PAPER);
        bg.setCornerRadius(dp(6));
        paper.setBackground(bg);
        paper.setElevation(dp(6));

        // Başlık + damga
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.TOP);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView brand = text("ZEYTİN DEFTERİ", 12, PAPER_ACCENT, true);
        brand.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        brand.setLetterSpacing(0.12f);
        titles.addView(brand);
        TextView title = text("Elek makbuzu", 22, INK, true);
        title.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        titles.addView(title);
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        String season = dateIso.length() >= 4 && dateIso.charAt(4) == '-' ? dateIso.substring(0, 4) : "";
        if (!season.isEmpty()) {
            TextView stamp = text("SEZON " + season, 11, PAPER_LABEL, true);
            stamp.setPadding(dp(8), dp(4), dp(8), dp(4));
            GradientDrawable s = new GradientDrawable();
            s.setCornerRadius(dp(6));
            s.setStroke(dp(1), Color.parseColor("#A99A78"), dp(4), dp(3));
            stamp.setBackground(s);
            stamp.setRotation(-4f);
            stamp.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            head.addView(stamp);
        }
        paper.addView(head);

        // Bahçe / tarih
        LinearLayout meta = new LinearLayout(this);
        meta.setPadding(0, dp(12), 0, dp(12));
        meta.addView(labeled("Bahçe", gardenName), new LinearLayout.LayoutParams(0, -2, 1));
        String day = dayName(dateIso);
        meta.addView(labeled("Tarih", ElekInsights.formatDate(dateIso, false) + (day.isEmpty() ? "" : ", " + day)),
                new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
        mp.setMargins(0, dp(14), 0, 0);
        paper.addView(rule(PAPER_RULE, 1), new LinearLayout.LayoutParams(-1, dp(1)));
        paper.addView(meta);
        paper.addView(rule(PAPER_RULE, 1), new LinearLayout.LayoutParams(-1, dp(1)));

        // Tablo
        paper.addView(tableRow("Sınıf", "Kg", "Fiyat", "Tutar", PAPER_LABEL, 11, false, 0));
        int[] colors = {
                GreenTheme.color(R.color.elek_badge_double), GreenTheme.color(R.color.elek_badge_one),
                GreenTheme.color(R.color.elek_badge_two), GreenTheme.color(R.color.elek_badge_three),
                GreenTheme.color(R.color.elek_badge_four), GreenTheme.color(R.color.elek_badge_oil)
        };
        double totalKg = 0, totalTl = 0;
        for (int i = 0; i < 6; i++) {
            totalKg += kg[i];
            totalTl += lineTotal[i];
            paper.addView(rule(PAPER_LINE, 1), new LinearLayout.LayoutParams(-1, dp(1)));
            paper.addView(tableRow(ElekInsights.GRADE_NAMES[i],
                    kg[i] > 0 ? numberFormat.format(kg[i]) : "—",
                    price[i] > 0 ? numberFormat.format(price[i]) : "—",
                    lineTotal[i] > 0 ? numberFormat.format(lineTotal[i]) + " TL" : "—",
                    INK, 13, true, colors[i]));
        }
        paper.addView(rule(INK, 2), new LinearLayout.LayoutParams(-1, dp(2)));
        LinearLayout total = tableRow("Toplam", numberFormat.format(totalKg) + " kg",
                totalKg > 0 ? averageFormat.format(totalTl / totalKg) : "—",
                numberFormat.format(totalTl) + " TL", INK, 14, true, 0);
        ((TextView) total.getChildAt(total.getChildCount() - 1)).setTextColor(PAPER_TOTAL);
        ((TextView) total.getChildAt(total.getChildCount() - 1)).setTextSize(16);
        paper.addView(total);

        // Dağılım
        LinearLayout bar = new LinearLayout(this);
        bar.setClipToOutline(true);
        GradientDrawable track = new GradientDrawable();
        track.setColor(PAPER_LINE);
        track.setCornerRadius(dp(4));
        bar.setBackground(track);
        boolean first = true;
        for (int i = 0; i < 6; i++) {
            if (kg[i] <= 0) continue;
            View seg = new View(this);
            seg.setBackgroundColor(colors[i]);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, (float) kg[i]);
            if (!first) p.setMargins(dp(2), 0, 0, 0);
            first = false;
            bar.addView(seg, p);
        }
        bar.setContentDescription(ElekInsights.accessibleLegend(kg));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(8));
        bp.setMargins(0, dp(8), 0, 0);
        paper.addView(bar, bp);
        TextView legend = text(ElekInsights.legend(kg), 11, PAPER_LABEL, false);
        legend.setPadding(0, dp(6), 0, 0);
        legend.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        paper.addView(legend);

        // Not
        View dashed = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.LINE);
        d.setStroke(dp(1), Color.parseColor("#A99A78"), dp(4), dp(3));
        dashed.setBackground(d);
        dashed.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, dp(3));
        dl.setMargins(0, dp(14), 0, dp(8));
        paper.addView(dashed, dl);
        paper.addView(labeled("Not", note.trim().isEmpty() ? "Not eklenmemiş" : note.trim()));
        return paper;
    }

    private LinearLayout tableRow(String a, String b, String c, String d, int color, int sp, boolean bold, int dotColor) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        LinearLayout first = new LinearLayout(this);
        first.setGravity(Gravity.CENTER_VERTICAL);
        if (dotColor != 0) {
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(dotColor);
            dot.setBackground(g);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(10), dp(10));
            p.setMargins(0, 0, dp(8), 0);
            first.addView(dot, p);
        }
        first.addView(text(a, sp, color, bold));
        row.addView(first, new LinearLayout.LayoutParams(0, -2, 1.5f));
        String[] cells = {b, c, d};
        for (int i = 0; i < cells.length; i++) {
            TextView t = text(cells[i], sp, i == 1 ? INK_SOFT : color, bold && i != 1);
            t.setGravity(Gravity.END);
            t.setFontFeatureSettings("tnum");
            row.addView(t, new LinearLayout.LayoutParams(0, -2, i == 2 ? 1.4f : 1f));
        }
        return row;
    }

    private View labeled(String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(text(label, 11, PAPER_LABEL, true));
        TextView v = text(value, 14, label.equals("Not") ? INK_SOFT : INK, !label.equals("Not"));
        v.setPadding(0, dp(2), 0, 0);
        box.addView(v);
        return box;
    }

    private View rule(int color, int heightDp) {
        View v = new View(this);
        v.setBackgroundColor(color);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }

    // ------------------------------------------------------------------
    // Eylemler
    // ------------------------------------------------------------------
    private void share() {
        String[] prices = new String[6];
        String[] totals = new String[6];
        double totalKg = 0, totalTl = 0;
        for (int i = 0; i < 6; i++) {
            prices[i] = numberFormat.format(price[i]) + " TL";
            totals[i] = numberFormat.format(lineTotal[i]) + " TL";
            totalKg += kg[i];
            totalTl += lineTotal[i];
        }
        String text = ElekInsights.receiptShareText(
                gardenName,
                ElekInsights.formatDate(dateIso, false),
                kg, prices, totals,
                numberFormat.format(totalKg) + " kg",
                numberFormat.format(totalTl) + " TL",
                ElekInsights.formatPerKg(totalTl, totalKg),
                note);
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Elek makbuzu · " + gardenName)
                .putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, "Makbuzu paylaş"));
    }

    private void editNote() {
        final EditText input = new EditText(this);
        input.setText(note);
        input.setSelection(input.getText().length());
        input.setHint("Örn. 3 kasa, nem yüksekti");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(300)});
        input.setMinLines(2);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(input, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle("Not")
                .setView(box)
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Kaydet", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (elekRepository.updateElekRecordNote(recordId, value) > 0) {
                        note = value;
                        syncCloud();
                        Toast.makeText(this, "Not kaydedildi.", Toast.LENGTH_SHORT).show();
                        render();
                    } else {
                        Toast.makeText(this, "Not kaydedilemedi.", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Elek kaydı silinsin mi?")
                .setMessage(gardenName + " · " + ElekInsights.formatDate(dateIso, false)
                        + " kaydı ve sınıf satırları silinecek. Bu işlem geri alınamaz.")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Sil", (dialog, which) -> {
                    if (elekRepository.deleteElekRecord(recordId) > 0) {
                        Toast.makeText(this, "Elek kaydı silindi.", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this, "Kayıt silinemedi.", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void syncCloud() {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) new ProductionCloudRepository(databaseHelper).syncElekByLocalId(user.getUid(), recordId);
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------
    // Yardımcılar
    // ------------------------------------------------------------------
    private int gradeIndexOf(String key) {
        for (int i = 0; i < GRADE_KEYS.length; i++) if (GRADE_KEYS[i].equalsIgnoreCase(key)) return i;
        return -1;
    }

    private String dayName(String iso) {
        try {
            java.text.SimpleDateFormat input = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
            input.setLenient(false);
            java.util.Date date = input.parse(iso);
            if (date == null) return "";
            String day = new java.text.SimpleDateFormat("EEEE", turkishLocale).format(date);
            return day.substring(0, 1).toUpperCase(turkishLocale) + day.substring(1);
        } catch (Exception e) {
            return "";
        }
    }

    private TextView actionButton(String label, boolean primary) {
        TextView b = text(label, 14, primary ? GreenTheme.color(R.color.zd_bg) : GreenTheme.color(R.color.zd_text), true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(primary
                ? rounded(GreenTheme.color(R.color.zd_green), Color.TRANSPARENT, 24)
                : rounded(GreenTheme.color(R.color.zd_card), GreenTheme.color(R.color.zd_border), 24));
        return b;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != Color.TRANSPARENT) g.setStroke(dp(1), stroke);
        return g;
    }

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private static String string(Cursor c, String column) {
        int i = c.getColumnIndex(column);
        if (i < 0 || c.isNull(i)) return "";
        String v = c.getString(i);
        return v == null ? "" : v;
    }

    private static double number(Cursor c, String column) {
        int i = c.getColumnIndex(column);
        return i < 0 || c.isNull(i) ? 0 : c.getDouble(i);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (databaseHelper != null) databaseHelper.close();
        super.onDestroy();
    }
}
