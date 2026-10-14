package com.zeytindefteri.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bahçe Karnesi. Veriler arka planda bir kez okunur (GardenAnalyticsRepository, local-first) ve
 * değişmez bir anlık görüntü olarak tutulur; bahçeler arasında geçiş yeni sorgu çalıştırmaz.
 * Hesaplar GardenAnalyticsSnapshot'ta, metin/oran kuralları GardenReportModel'dedir.
 */
public final class GardenAnalyticsActivity extends Activity {

    private DatabaseHelper helper;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private GardenAnalyticsRepository.Result data;

    private final List<Long> gardenIds = new ArrayList<>();
    private final List<TextView> scopeChips = new ArrayList<>();
    private long selectedGarden = -1L;
    private String screenOwner = "";
    private int generation = 0;

    private LinearLayout cards;
    private LinearLayout scopeRow;
    private TextView tvSeason;

    private final int text = GreenTheme.color(R.color.zd_text);
    private final int muted = GreenTheme.color(R.color.zd_muted);
    private final int soft = GreenTheme.color(R.color.zd_text_soft);
    private final int green = GreenTheme.color(R.color.zd_green);
    private final int warning = GreenTheme.color(R.color.zd_warning);
    private final int danger = GreenTheme.color(R.color.zd_danger);
    private final int surface = GreenTheme.color(R.color.zd_surface);
    private final int border = GreenTheme.color(R.color.zd_border);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getActionBar() != null) getActionBar().hide();
        getWindow().setStatusBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().setNavigationBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().getDecorView().setSystemUiVisibility(0);
        helper = new DatabaseHelper(this);
        if (state == null) {
            screenOwner = helper.getActiveOwnerUid();
            selectedGarden = getIntent().getLongExtra("garden_id", -1L);
        } else {
            screenOwner = state.getString("owner", "");
            selectedGarden = state.getLong("garden", -1L);
        }
        buildBaseLayout();
    }

    private void buildBaseLayout() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(GreenTheme.color(R.color.zd_bg));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(16), dp(16), dp(6));
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_elek_back_chevron);
        back.setColorFilter(text);
        back.setPadding(dp(11), dp(11), dp(11), dp(11));
        back.setContentDescription("Önceki ekrana dön");
        back.setBackgroundResource(ripple());
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout titles = column();
        titles.setPadding(dp(8), 0, dp(8), 0);
        titles.addView(label("Bahçe Karnesi", 22, text, true));
        titles.addView(label("Sezon özeti ve karşılaştırma", 12, muted, false));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        // Bu ekran her zaman aktif sezonu gösterir; sezon değiştirilmez, bu yüzden etiket tıklanmaz.
        tvSeason = label("", 13, green, true);
        tvSeason.setGravity(Gravity.CENTER);
        tvSeason.setPadding(dp(14), 0, dp(14), 0);
        tvSeason.setBackground(round(GreenTheme.color(R.color.zd_card), border, 20));
        header.addView(tvSeason, new LinearLayout.LayoutParams(-2, dp(40)));
        screen.addView(header);

        HorizontalScrollView scopeScroll = new HorizontalScrollView(this);
        scopeScroll.setHorizontalScrollBarEnabled(false);
        scopeScroll.setClipToPadding(false);
        scopeScroll.setPadding(dp(16), dp(4), dp(16), dp(8));
        scopeRow = new LinearLayout(this);
        scopeScroll.addView(scopeRow);
        screen.addView(scopeScroll);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        cards = column();
        cards.setPadding(dp(16), 0, dp(16), dp(28));
        scroll.addView(cards);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(screen);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isSameOwner()) { finish(); return; }
        final int ticket = ++generation;
        final int season = helper.getActiveSeasonYear();
        tvSeason.setText(String.valueOf(season));
        tvSeason.setContentDescription(season + " sezonu");
        cards.removeAllViews();
        cards.addView(note("Kayıtların hesaplanıyor…"));

        worker.execute(() -> {
            try {
                GardenAnalyticsRepository.Result loaded = new GardenAnalyticsRepository(helper).load(screenOwner, season, true);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || ticket != generation) return;
                    if (!isSameOwner()) { finish(); return; }
                    if (season != helper.getActiveSeasonYear()) return;
                    if (loaded == null || loaded.current == null) { showLoadError(); return; }
                    // -1 = Tüm bahçeler sezon özeti; gerçek garden_id ise varlığı kontrol edilir.
                    if (selectedGarden != -1L && loaded.current.garden(selectedGarden) == null) {
                        Toast.makeText(this, "Bahçe bulunamadı", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    data = loaded;
                    buildScopeChips();
                    render();
                });
            } catch (RuntimeException error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || ticket != generation) return;
                    showLoadError();
                });
            }
        });
    }

    private void showLoadError() {
        cards.removeAllViews();
        cards.addView(note("Analiz okunamadı. Kayıtlarını kontrol edip ekranı yeniden aç."));
    }

    // ------------------------------------------------------------ kapsam seçimi

    private void buildScopeChips() {
        scopeRow.removeAllViews();
        scopeChips.clear();
        gardenIds.clear();
        gardenIds.add(-1L);
        List<String> names = new ArrayList<>();
        names.add("Tüm bahçeler");
        for (GardenAnalyticsSnapshot g : data.current.gardens.values()) {
            gardenIds.add(g.gardenId);
            names.add(g.name);
        }
        if (gardenIds.indexOf(selectedGarden) < 0) selectedGarden = -1L;
        for (int i = 0; i < names.size(); i++) {
            final long id = gardenIds.get(i);
            TextView chip = label(names.get(i), 13, 0, true);
            chip.setTextColor(getColorStateList(R.color.choice_text));
            chip.setGravity(Gravity.CENTER);
            chip.setMaxLines(1);
            chip.setPadding(dp(14), 0, dp(14), 0);
            chip.setBackgroundResource(R.drawable.bg_choice_pill);
            chip.setOnClickListener(v -> { selectedGarden = id; render(); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(40));
            if (i > 0) p.leftMargin = dp(8);
            scopeRow.addView(chip, p);
            scopeChips.add(chip);
        }
    }

    // ------------------------------------------------------------ çizim

    private void render() {
        if (!isSameOwner()) { finish(); return; }
        if (data == null || data.current == null) return;
        GardenAnalyticsSnapshot current = data.current.garden(selectedGarden);
        GardenAnalyticsSnapshot previous = data.previous == null ? null : data.previous.garden(selectedGarden);
        if (current == null) {
            Toast.makeText(this, "Analiz verisi bulunamadı", Toast.LENGTH_SHORT).show();
            return;
        }
        for (int i = 0; i < scopeChips.size(); i++) {
            boolean on = gardenIds.get(i) == selectedGarden;
            scopeChips.get(i).setSelected(on);
            scopeChips.get(i).setContentDescription(scopeChips.get(i).getText() + (on ? ", seçili" : ""));
        }
        cards.removeAllViews();
        renderNet(current);
        renderYield(current);
        renderSales(current);
        renderGrades(current);
        renderConsistency(current);
        renderComparison(current, previous);
        renderUnassigned(current);
    }

    private void renderNet(GardenAnalyticsSnapshot s) {
        LinearLayout card = card(R.drawable.bg_summary);
        String scope = selectedGarden == -1L ? s.gardenCount + " bahçe" : s.name;
        card.addView(overline("KAYITLI NET SONUÇ · " + s.season + " · " + scope.toUpperCase(new java.util.Locale("tr", "TR"))));
        GardenReportModel.Tone tone = GardenReportModel.netTone(s.netResult(), s.saleRecords);
        TextView net = label(GardenReportModel.format(s.netResult(), "TL"), 30, toneColor(tone), true);
        net.setPadding(0, dp(8), 0, dp(4));
        card.addView(net);
        card.addView(label("Satış geliri (nakliye ve kesintiler sonrası) − kayıtlı masraflar. "
                + "Kaydetmediğin emek, kira, amortisman veya vergi hesapta yoktur.", 12, soft, false));
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(12), 0, 0);
        row.addView(metric("Hasat", GardenReportModel.format(s.harvestKg, "kg"), text), weight(0));
        row.addView(metric("Masraf", GardenReportModel.format(s.expense, "TL"), text), weight(8));
        row.addView(metric("Satış geliri", GardenReportModel.format(s.revenue, "TL"), text), weight(8));
        View line = new View(this);
        line.setBackgroundColor(GreenTheme.color(R.color.zd_green_chip));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.topMargin = dp(12);
        card.addView(line, lp);
        card.addView(row);
    }

    private void renderYield(GardenAnalyticsSnapshot s) {
        LinearLayout card = card(R.drawable.bg_card);
        card.addView(overline("VERİM VE MALİYET"));
        card.addView(grid(
                metric("Kayıtlı ağaç", GardenReportModel.format(s.trees, ""), text),
                metric("Ağaç başı verim", GardenReportModel.format(s.kgPerTree(), "kg/ağaç"), text),
                metric("Dekar başı verim", GardenReportModel.format(s.kgPerDecare(), "kg/dekar"), text),
                metric("Kg başı maliyet", GardenReportModel.format(s.costPerKg(), "TL/kg"), text)));
        if (!s.completeTrees || !s.completeArea) {
            card.addView(small("Ağaç veya dekar bilgisi eksik bahçelerde ilgili verim hesaplanmaz."));
        }
    }

    private void renderSales(GardenAnalyticsSnapshot s) {
        LinearLayout card = card(R.drawable.bg_card);
        card.addView(overline("SATIŞ VE TAHSİLAT"));
        card.addView(grid(
                metric("Satılan", GardenReportModel.format(s.soldKg, "kg"), text),
                metric("Ortalama fiyat", GardenReportModel.format(s.averageSalePrice(), "TL/kg"), text)));
        int pct = GardenReportModel.collectedPercent(s.collected, s.receivable);
        if (pct >= 0) {
            LinearLayout bar = new LinearLayout(this);
            bar.setBackground(round(GreenTheme.color(R.color.zd_warning_soft), GreenTheme.color(R.color.zd_warning_soft), 5));
            bar.setClipToOutline(true);
            View fill = new View(this);
            fill.setBackgroundColor(green);
            bar.addView(fill, new LinearLayout.LayoutParams(0, -1, Math.max(0, pct)));
            bar.addView(new View(this), new LinearLayout.LayoutParams(0, -1, Math.max(0, 100 - pct)));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(10));
            bp.topMargin = dp(14);
            card.addView(bar, bp);
            bar.setContentDescription("Tahsil edilen yüzde " + pct);
            LinearLayout legend = new LinearLayout(this);
            legend.setPadding(0, dp(6), 0, 0);
            legend.addView(label("Tahsil edilen " + GardenReportModel.format(s.collected, "TL"), 12, green, false),
                    new LinearLayout.LayoutParams(0, -2, 1));
            legend.addView(label("Kalan alacak " + GardenReportModel.format(s.receivable, "TL"), 12, warning, false));
            card.addView(legend);
        }
        card.addView(small("Ortalama fiyat: kesintiler öncesi satış tutarı / satılan kg. "
                + "Tahsilat, satışın alacak kaydıyla bir kez sayılır."));
    }

    private void renderGrades(GardenAnalyticsSnapshot s) {
        LinearLayout card = card(R.drawable.bg_card);
        card.addView(overline("ELEK DAĞILIMI"));
        if (s.elekRecords <= 0) {
            card.addView(small("Bu sezon için elek kaydı bulunmuyor."));
            return;
        }
        double total = s.totalGradeKg(), max = 0;
        int best = -1;
        for (int i = 0; i < GardenAnalyticsSnapshot.GRADE_LABELS.length; i++) {
            if (s.gradeKg(i) > max) { max = s.gradeKg(i); best = i; }
        }
        for (int i = 0; i < GardenAnalyticsSnapshot.GRADE_LABELS.length; i++) {
            double kg = s.gradeKg(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, 0);
            row.addView(label(GardenAnalyticsSnapshot.GRADE_LABELS[i], 13, text, true), new LinearLayout.LayoutParams(dp(56), -2));
            LinearLayout track = new LinearLayout(this);
            track.setBackground(round(surface, surface, 5));
            track.setClipToOutline(true);
            int w = GardenReportModel.bar(kg, max);
            View fill = new View(this);
            fill.setBackgroundColor(i == best ? green : GreenTheme.color(R.color.zd_green_mid_dim));
            track.addView(fill, new LinearLayout.LayoutParams(0, -1, w));
            track.addView(new View(this), new LinearLayout.LayoutParams(0, -1, 100 - w));
            row.addView(track, new LinearLayout.LayoutParams(0, dp(10), 1));
            TextView value = label(GardenReportModel.format(kg, "kg") + " · %" + GardenReportModel.share(kg, total), 12, soft, false);
            value.setGravity(Gravity.END);
            row.addView(value, new LinearLayout.LayoutParams(dp(96), -2));
            row.setContentDescription(GardenAnalyticsSnapshot.GRADE_LABELS[i] + ": " + GardenReportModel.format(kg, "kg")
                    + ", yüzde " + GardenReportModel.share(kg, total));
            card.addView(row);
        }
        TextView dominant = label("Baskın boy: " + s.dominantGrade() + " · elek kayıtları toplamı "
                + GardenReportModel.format(total, "kg"), 12, soft, false);
        dominant.setPadding(0, dp(12), 0, 0);
        card.addView(dominant);
    }

    /** Hasat–elek uyumu: yalnızca elek kaydı varken gösterilir; bir taraf eksikse bilgi notu. */
    private void renderConsistency(GardenAnalyticsSnapshot s) {
        if (s.elekRecords <= 0) return;
        LinearLayout card = card(R.drawable.bg_card);
        card.setOrientation(LinearLayout.HORIZONTAL);
        if (!s.hasComparableHarvestAndElek()) {
            card.setOrientation(LinearLayout.VERTICAL);
            card.addView(small("Hasat ve elek toplamlarını karşılaştırmak için iki tarafta da kayıt bulunmalıdır."));
            return;
        }
        boolean ok = !s.hasHarvestElekMismatch();
        FrameLayout disc = new FrameLayout(this);
        int discColor = ok ? GreenTheme.color(R.color.zd_green_soft) : GreenTheme.color(R.color.zd_warning_soft);
        disc.setBackground(round(discColor, discColor, 18));
        ImageView icon = new ImageView(this);
        icon.setImageResource(ok ? R.drawable.ic_op_check : R.drawable.ic_op_warn);
        icon.setColorFilter(ok ? green : warning);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        disc.addView(icon, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        card.addView(disc, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout body = column();
        body.setPadding(dp(12), 0, 0, 0);
        body.addView(label(ok ? "Hasat ve elek uyumlu" : "Hasat ve elek uyuşmuyor", 14, text, true));
        TextView detail = label(ok ? "Hasat ve elek kayıtları toplam olarak uyumlu."
                : GardenReportModel.mismatch(s.harvestKg, s.totalGradeKg(), s.harvestElekDifferenceKg(),
                        s.elekExcessKg(), s.elekMissingKg())
                        + " Hasat ve elek ayrı kayıt akışlarıdır; bu uyarı toplamların birbirini karşılamadığını gösterir.",
                12, muted, false);
        detail.setPadding(0, dp(3), 0, 0);
        body.addView(detail);
        card.addView(body, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void renderComparison(GardenAnalyticsSnapshot current, GardenAnalyticsSnapshot previous) {
        int previousSeason = previous == null ? current.season - 1 : previous.season;
        LinearLayout card = card(R.drawable.bg_card);
        card.addView(overline(previousSeason + " → " + current.season));
        boolean none = previous == null || previous.harvestRecords + previous.expenseRecords
                + previous.saleRecords + previous.elekRecords == 0;
        if (none) {
            card.addView(small("Önceki sezonda karşılaştırılabilir kayıt yok."));
        } else {
            compare(card, "Hasat", current.harvestKg, previous.harvestKg, current.harvestRecords > 0, previous.harvestRecords > 0, "kg", true);
            compare(card, "Masraf", current.expense, previous.expense, current.expenseRecords > 0, previous.expenseRecords > 0, "TL", false);
            compare(card, "Satış geliri", current.revenue, previous.revenue, current.saleRecords > 0, previous.saleRecords > 0, "TL", true);
            compare(card, "Ağaç başı verim", current.kgPerTree(), previous.kgPerTree(), current.harvestRecords > 0, previous.harvestRecords > 0, "kg/ağaç", true);
            compare(card, "Kg başı maliyet", current.costPerKg(), previous.costPerKg(), current.harvestRecords > 0, previous.harvestRecords > 0, "TL/kg", false);
            compare(card, "Satılan", current.soldKg, previous.soldKg, current.saleRecords > 0, previous.saleRecords > 0, "kg", true);
            compare(card, "Ortalama fiyat", current.averageSalePrice(), previous.averageSalePrice(), current.saleRecords > 0, previous.saleRecords > 0, "TL/kg", true);
            TextView dominant = label("Baskın boy: " + previous.dominantGrade() + " → " + current.dominantGrade(), 13, text, true);
            dominant.setPadding(0, dp(14), 0, dp(2));
            card.addView(dominant);
            for (int i = 0; i < GardenAnalyticsSnapshot.GRADE_LABELS.length; i++) {
                compare(card, GardenAnalyticsSnapshot.GRADE_LABELS[i], current.gradeKg(i), previous.gradeKg(i),
                        current.elekRecords > 0, previous.elekRecords > 0, "kg", null);
            }
        }
        card.addView(small("Tam sezon toplamlarıdır; aynı tarihe kadar olan dönemle sınırlı değildir. "
                + "Geçmiş ağaç sayısı saklanmadığı için iki sezonun kg/ağaç hesabında güncel ağaç sayısı kullanılır."));
    }

    private void compare(LinearLayout card, String name, Double current, Double previous,
                         boolean hasCurrent, boolean hasPrevious, String unit, Boolean higherIsBetter) {
        Double pct = GardenAnalyticsSnapshot.percent(current, previous, hasCurrent, hasPrevious);
        String prevText = hasPrevious ? GardenReportModel.format(previous, unit) : "—";
        String curText = hasCurrent ? GardenReportModel.format(current, unit) : "—";
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        View line = new View(this);
        line.setBackgroundColor(border);
        card.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        row.addView(label(name, 14, text, true), new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout values = column();
        values.setGravity(Gravity.END);
        values.addView(label(curText, 13, text, true));
        values.addView(label("önce " + prevText, 11, muted, false));
        row.addView(values);
        String change = GardenReportModel.change(pct);
        TextView badge = label(change, 12, toneColor(GardenReportModel.changeTone(pct, higherIsBetter)), true);
        badge.setGravity(Gravity.CENTER);
        badge.setMinWidth(dp(60));
        badge.setPadding(dp(8), dp(3), dp(8), dp(3));
        badge.setBackground(round(surface, surface, 10));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
        bp.leftMargin = dp(10);
        row.addView(badge, bp);
        row.setContentDescription(name + ": " + prevText + " → " + curText + ", "
                + (pct == null ? "karşılaştırma için yeterli veri yok" : change));
        card.addView(row);
    }

    private void renderUnassigned(GardenAnalyticsSnapshot s) {
        if (s.unassignedCost == 0d && s.unassignedReceivable == 0d) return;
        LinearLayout card = card(R.drawable.bg_card);
        card.addView(overline("BAHÇEYE BAĞLANMAMIŞ KAYITLAR"));
        card.addView(grid(
                metric("Özete dahil salamura masrafı", GardenReportModel.format(s.unassignedCost, "TL"), text),
                metric("Özete dahil diğer alacak", GardenReportModel.format(s.unassignedReceivable, "TL"), text)));
    }

    // ------------------------------------------------------------ yardımcılar

    private int toneColor(GardenReportModel.Tone tone) {
        switch (tone) {
            case GOOD: return green;
            case BAD: return danger;
            case MUTED: return muted;
            default: return soft;
        }
    }

    private LinearLayout card(int background) {
        LinearLayout card = column();
        card.setBackgroundResource(background);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(12);
        cards.addView(card, p);
        return card;
    }

    private View metric(String name, String value, int color) {
        LinearLayout m = column();
        m.addView(label(name, 12, muted, false));
        TextView v = label(value, 17, color, true);
        v.setPadding(0, dp(2), 0, 0);
        m.addView(v);
        m.setContentDescription(name + ": " + value);
        return m;
    }

    /** İki sütunlu ölçü ızgarası. */
    private View grid(View... items) {
        LinearLayout g = column();
        for (int i = 0; i < items.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setPadding(0, dp(12), 0, 0);
            row.addView(items[i], weight(0));
            if (i + 1 < items.length) row.addView(items[i + 1], weight(10));
            else row.addView(new View(this), weight(10));
            g.addView(row);
        }
        return g;
    }

    private TextView overline(String s) {
        TextView t = label(s, 11, muted, true);
        t.setLetterSpacing(0.08f);
        return t;
    }

    private TextView small(String s) {
        TextView t = label(s, 11, muted, false);
        t.setPadding(0, dp(10), 0, 0);
        return t;
    }

    private TextView note(String s) {
        TextView t = label(s, 15, muted, false);
        t.setPadding(0, dp(24), 0, 0);
        return t;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout.LayoutParams weight(int leftMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.leftMargin = dp(leftMargin);
        return p;
    }

    private TextView label(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        if (color != 0) view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable round(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private int ripple() {
        android.util.TypedValue v = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, v, true);
        return v.resourceId;
    }

    private boolean isSameOwner() {
        if (helper == null) return false;
        String active = helper.getActiveOwnerUid();
        return (screenOwner == null ? "" : screenOwner).equals(active == null ? "" : active);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("owner", screenOwner);
        state.putLong("garden", selectedGarden);
    }

    @Override
    protected void onDestroy() {
        generation++;
        worker.shutdownNow();
        if (helper != null) {
            helper.close();
            helper = null;
        }
        super.onDestroy();
    }
}
