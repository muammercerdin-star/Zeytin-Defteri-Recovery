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
        /** "Senin elek fiyatların" satırına dokununca: o elekle Piyasa sekmesi. */
        default void marketGrade(String grade) { market(); }
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
        addTo(body, season, prices, null, actions);
    }

    /** @param region bölgenin güncel fiyatı (CurrentPrices sırasıyla 6 elek; yoksa null) */
    public void addTo(LinearLayout body, List<HomeSummaryModel.Metric> season,
                      List<HomeSummaryModel.GradePrice> prices, double[] region, Actions actions) {
        body.addView(overline("HIZLI İŞLEM", null, null), sectionParams(18));
        // Tek kayıt düzeni: hasat ve satış elekten oluşur; hızlı işlemde yalnız İşlem ekle ve Elek.
        LinearLayout quick = new LinearLayout(c);
        quick.addView(wideTile(R.drawable.ic_leaf, "İşlem ekle", "İlaç, gübre, sulama", v -> pickOperation(actions)), weight(0));
        quick.addView(wideTile(R.drawable.ic_elek_nav, "Elek kaydı", "Kilo, fiyat, satış", v -> actions.elek()), weight(10));
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
            body.addView(priceCard(prices, region, actions), sideParams(8));
        }
        boolean anyRegion = false;
        if (region != null) for (double r : region) if (r > 0) anyRegion = true;
        TextView note = label(anyRegion
                ? "Ortalaman: yalnız senin elek kayıtların. Bölge: son günlerin ortadaki fiyatı."
                : "Yalnızca senin elek kayıtların; bölge piyasası değildir.", 11, muted, false);
        note.setPadding(dp(18), dp(6), dp(18), 0);
        body.addView(note);
    }

    /** Elek rozet renkleri (elek formundakiyle aynı): Double mavi, 1 yeşil, 2 sarı, 3 turuncu, 4 kırmızı, Yağlık gri. */
    private static final int[] BADGE = {0xFF3D7DD8, 0xFF7BC045, 0xFFE7B93A, 0xFFE0904A, 0xFFD0434A, 0xFFC9D1D3};
    private static final String[] BADGE_TEXT = {"★", "1", "2", "3", "4", "Y"};

    /**
     * Senin elek fiyatların: her sınıf bir satır. Solda renkli rozet, sınıf adı, kilo ve kayıt sayısı;
     * sağda ortalaman (büyük) ve bölgenin güncel fiyatıyla farkı (yeşil üstünde, kırmızı altında).
     * Altında sınıfın kilo payını gösteren ince çubuk.
     */
    private View priceCard(List<HomeSummaryModel.GradePrice> prices, double[] region, Actions actions) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(6), dp(14), dp(10));
        box.setBackground(round(card, border, 20));
        double maxKg = 0;
        for (HomeSummaryModel.GradePrice g : prices) maxKg = Math.max(maxKg, g.kg);
        int danger = GreenTheme.color(R.color.zd_danger);
        for (int n = 0; n < prices.size(); n++) {
            HomeSummaryModel.GradePrice g = prices.get(n);
            int at = CurrentPrices.index(g.label);
            double reg = region != null && at >= 0 && at < region.length ? region[at] : 0;
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(10), 0, dp(4));
            TextView badge = label(at >= 0 ? BADGE_TEXT[at] : "•", 13, 0xFF0B1D28, true);
            badge.setGravity(Gravity.CENTER);
            GradientDrawable disc = new GradientDrawable();
            disc.setShape(GradientDrawable.OVAL);
            disc.setColor(at >= 0 ? BADGE[at] : soft);
            badge.setBackground(disc);
            row.addView(badge, new LinearLayout.LayoutParams(dp(30), dp(30)));
            LinearLayout words = new LinearLayout(c);
            words.setOrientation(LinearLayout.VERTICAL);
            words.setPadding(dp(12), 0, dp(8), 0);
            words.addView(label(g.label, 15, text, true));
            words.addView(label(HomeSummaryModel.kg(g.kg) + " · " + g.records + " kayıt", 12, muted, false));
            row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout nums = new LinearLayout(c);
            nums.setOrientation(LinearLayout.VERTICAL);
            nums.setGravity(Gravity.END);
            LinearLayout priceRow = new LinearLayout(c);
            priceRow.setGravity(Gravity.BOTTOM);
            priceRow.addView(label(HomeSummaryModel.priceNumber(g.average()), 19, text, true));
            TextView unit = label(" TL/kg", 11, muted, false);
            unit.setPadding(0, 0, 0, dp(2));
            priceRow.addView(unit);
            nums.addView(priceRow);
            String cmp = HomeSummaryModel.compareLine(g.average(), reg);
            if (!cmp.isEmpty()) {
                int tone = HomeSummaryModel.compareTone(g.average(), reg);
                TextView cl = label(cmp, 11, tone > 0 ? green : tone < 0 ? danger : muted, tone != 0);
                cl.setGravity(Gravity.END);
                nums.addView(cl);
            }
            row.addView(nums, new LinearLayout.LayoutParams(-2, -2));
            box.addView(row);
            // kilo payı çubuğu
            LinearLayout bar = new LinearLayout(c);
            bar.setPadding(dp(42), 0, 0, 0);
            float share = maxKg > 0 ? (float) (g.kg / maxKg) : 0f;
            View fill = new View(c);
            fill.setBackground(round(at >= 0 ? (BADGE[at] & 0x00FFFFFF) | 0x99000000 : soft, 0x00000000, 2));
            bar.addView(fill, new LinearLayout.LayoutParams(0, dp(3), Math.max(0.02f, share)));
            bar.addView(new View(c), new LinearLayout.LayoutParams(0, dp(3), Math.max(0f, 1f - share)));
            box.addView(bar, new LinearLayout.LayoutParams(-1, -2));
            final String gradeLabel = g.label;
            row.setOnClickListener(v -> actions.marketGrade(gradeLabel));
            row.setContentDescription(g.label + ": ortalaman " + HomeSummaryModel.priceLine(g) + ", "
                    + HomeSummaryModel.kg(g.kg) + ", " + g.records + " kayıt" + (cmp.isEmpty() ? "" : ". " + cmp));
        }
        box.setOnClickListener(v -> actions.market());
        return box;
    }

    /** Geniş hızlı işlem: solda ikon, yanında başlık ve kısa açıklama. */
    private View wideTile(int icon, String title, String sub, View.OnClickListener click) {
        LinearLayout t = new LinearLayout(c);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(14), dp(14), dp(10), dp(14));
        t.setMinimumHeight(dp(72));
        t.setBackground(round(card, border, 18));
        FrameLayout disc = new FrameLayout(c);
        disc.setBackground(round(GreenTheme.color(R.color.zd_green_soft), GreenTheme.color(R.color.zd_green_soft), 18));
        ImageView glyph = new ImageView(c);
        glyph.setImageResource(icon);
        glyph.setColorFilter(green);
        glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        disc.addView(glyph, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        t.addView(disc, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(10), 0, 0, 0);
        TextView name = label(title, 15, text, true);
        name.setMaxLines(1);
        words.addView(name);
        TextView s = label(sub, 11, muted, false);
        s.setMaxLines(1);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        words.addView(s);
        t.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        t.setContentDescription(title + ": " + sub);
        t.setOnClickListener(click);
        return t;
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
