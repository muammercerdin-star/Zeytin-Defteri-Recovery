package com.zeytindefteri.app;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

/**
 * Ana Sayfa alt bölümü: hızlı işlemler, "Bu Sezon" ve "Senin elek fiyatların".
 * Yalnızca görünüm; veri ve eylemler ProducerFeedActivity'den gelir.
 */
public final class HomeLowerSections {
    public interface Actions {
        void operation(String type);
        void harvest();
        void elek();
        void sale();
        void seasonReport();
        void market();
        /** "Bu Sezon" kartında Satış rakamına dokunulunca: satışların dökümü. */
        default void salesList() { seasonReport(); }
    }

    private final Context c;
    private final int card = GreenTheme.color(R.color.zd_card), border = GreenTheme.color(R.color.zd_border);
    private final int text = GreenTheme.color(R.color.zd_text), muted = GreenTheme.color(R.color.zd_muted);
    private final int green = GreenTheme.color(R.color.zd_green), gold = GreenTheme.color(R.color.zd_warning);
    private final int soft = GreenTheme.color(R.color.zd_text_soft);

    public HomeLowerSections(Context context) { this.c = context; }

    /** body'ye sırasıyla ekler: hızlı işlemler, Bu Sezon, Senin elek fiyatların. */
    public void addTo(LinearLayout body, List<HomeSummaryModel.Metric> season,
                      List<HomeSummaryModel.GradePrice> prices, Actions actions) {
        body.addView(overline("HIZLI İŞLEM", null, null), sectionParams(18));
        LinearLayout quick = new LinearLayout(c);
        quick.addView(tile(R.drawable.ic_leaf, "İşlem ekle", v -> pickOperation(actions)), weight(0));
        quick.addView(tile(R.drawable.ic_olive, "Hasat", v -> actions.harvest()), weight(8));
        quick.addView(tile(R.drawable.ic_elek_nav, "Elek", v -> actions.elek()), weight(8));
        quick.addView(tile(R.drawable.ic_tag, "Satış", v -> actions.sale()), weight(8));
        body.addView(quick, sideParams(8));

        body.addView(overline("BU SEZON", "Sezon Raporum  ›", v -> actions.seasonReport()), sectionParams(20));
        LinearLayout seasonCard = cardColumn();
        LinearLayout row = new LinearLayout(c);
        for (int i = 0; i < season.size(); i++) {
            HomeSummaryModel.Metric m = season.get(i);
            LinearLayout cell = new LinearLayout(c);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.addView(label(m.label, 12, muted, false));
            TextView value = label(m.value, 15, m.tone == HomeSummaryModel.Tone.POSITIVE ? green
                    : m.tone == HomeSummaryModel.Tone.WARNING ? gold : text, true);
            value.setPadding(0, dp(3), 0, 0);
            value.setMaxLines(1);
            cell.addView(value);
            cell.setContentDescription(m.label + ": " + m.value);
            if ("Satış".equals(m.label)) {
                // Satış toplamı elek toplamından farklı olabilir; dökümü buradan açılır.
                TextView hint = label("dökümü ›", 11, green, true);
                hint.setPadding(0, dp(2), 0, 0);
                cell.addView(hint);
                cell.setContentDescription(m.label + ": " + m.value + ". Satışların dökümünü aç");
                cell.setOnClickListener(v -> actions.salesList());
            }
            row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
        }
        seasonCard.addView(row);
        seasonCard.setOnClickListener(v -> actions.seasonReport());
        body.addView(seasonCard, sideParams(8));

        body.addView(overline("SENİN ELEK FİYATLARIN", "Bölge fiyatları  ›", v -> actions.market()), sectionParams(20));
        if (prices.isEmpty()) {
            LinearLayout empty = cardColumn();
            empty.addView(label(HomeSummaryModel.emptyPrices(), 13, soft, false));
            body.addView(empty, sideParams(8));
        } else {
            LinearLayout grid = new LinearLayout(c);
            for (int i = 0; i < prices.size(); i++) {
                HomeSummaryModel.GradePrice g = prices.get(i);
                LinearLayout box = cardColumn();
                box.addView(label(g.label + " ortalaman", 12, muted, false));
                TextView price = label(HomeSummaryModel.priceLine(g), 20, text, true);
                price.setPadding(0, dp(4), 0, dp(2));
                box.addView(price);
                box.addView(label(HomeSummaryModel.detailLine(g), 12, muted, false));
                box.setContentDescription(g.label + " ortalaman " + HomeSummaryModel.priceLine(g) + ", " + HomeSummaryModel.detailLine(g));
                box.setOnClickListener(v -> actions.elek());
                grid.addView(box, weight(i == 0 ? 0 : 8));
            }
            if (prices.size() == 1) grid.addView(new View(c), weight(8));
            body.addView(grid, sideParams(8));
        }
        TextView note = label("Yalnızca senin elek kayıtların; bölge piyasası değildir.", 11, muted, false);
        note.setPadding(dp(18), dp(6), dp(18), 0);
        body.addView(note);
    }

    private void pickOperation(Actions actions) {
        new android.app.AlertDialog.Builder(c)
                .setTitle("Hangi işlem?")
                .setItems(HomeSummaryModel.OPERATION_TYPES, (d, which) -> actions.operation(HomeSummaryModel.OPERATION_TYPES[which]))
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private View tile(int icon, String title, View.OnClickListener click) {
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(4), dp(14), dp(4), dp(12));
        t.setMinimumHeight(dp(88));
        t.setBackground(round(card, border, 18));
        FrameLayout disc = new FrameLayout(c);
        disc.setBackground(round(GreenTheme.color(R.color.zd_green_soft), GreenTheme.color(R.color.zd_green_soft), 18));
        ImageView glyph = new ImageView(c);
        glyph.setImageResource(icon);
        glyph.setColorFilter(green);
        glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        disc.addView(glyph, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        t.addView(disc, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView name = label(title, 13, text, true);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(8), 0, 0);
        name.setMaxLines(1);
        t.addView(name);
        t.setContentDescription(title);
        t.setOnClickListener(click);
        return t;
    }

    private View overline(String title, String action, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = label(title, 12, muted, true);
        t.setLetterSpacing(0.08f);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null) {
            TextView a = label(action, 13, green, true);
            a.setGravity(Gravity.CENTER_VERTICAL);
            a.setMinHeight(dp(40));
            a.setOnClickListener(click);
            r.addView(a);
        }
        return r;
    }

    private LinearLayout cardColumn() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(14), dp(16), dp(14));
        l.setBackground(round(card, border, 20));
        return l;
    }

    private LinearLayout.LayoutParams weight(int leftMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.leftMargin = dp(leftMargin);
        return p;
    }

    private LinearLayout.LayoutParams sideParams(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(16), dp(top), dp(16), 0);
        return p;
    }

    private LinearLayout.LayoutParams sectionParams(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(18), dp(top), dp(16), 0);
        return p;
    }

    private TextView label(String s, float size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable round(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), stroke);
        return d;
    }

    private int dp(float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }
}
