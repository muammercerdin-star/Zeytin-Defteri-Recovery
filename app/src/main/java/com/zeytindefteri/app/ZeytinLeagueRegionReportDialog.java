package com.zeytindefteri.app;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Zeytin Ligi bölge kartına dokununca açılan premium anonim bölge raporu.
 * Yeni Activity açmaz; mevcut Zeytin Ligi ekranının üzerinde bottom-sheet benzeri
 * bir Dialog olarak çalışır.
 */
public final class ZeytinLeagueRegionReportDialog {

    private final Context context;
    private final MarketCloudRepository cloudRepository;
    private final int year;
    private final ZeytinLeagueRepository.Region region;
    private final MarketCloudRepository.LeagueRegionStat directoryStat;
    private final Dialog dialog;
    private final LinearLayout reportHost;
    private final TextView detailButton;
    private MarketCloudRepository.LeagueRegionReport currentReport;
    private List<MarketCloudRepository.LeagueGradeReport> currentGrades = new ArrayList<>();
    private final NumberFormat number = NumberFormat.getNumberInstance(new Locale("tr", "TR"));

    private final int bg = GreenTheme.color(R.color.zd_bg);
    private final int surface = GreenTheme.color(R.color.zd_surface);
    private final int card = GreenTheme.color(R.color.zd_card);
    private final int border = GreenTheme.color(R.color.zd_border);
    private final int green = GreenTheme.color(R.color.zd_green);
    private final int greenSoft = GreenTheme.color(R.color.zd_green_soft);
    private final int gold = GreenTheme.color(R.color.zd_warning);
    private final int text = GreenTheme.color(R.color.zd_text);
    private final int muted = GreenTheme.color(R.color.zd_muted);
    private final int danger = GreenTheme.color(R.color.zd_danger);

    private ZeytinLeagueRegionReportDialog(
            Context context,
            MarketCloudRepository cloudRepository,
            int year,
            ZeytinLeagueRepository.Region region,
            MarketCloudRepository.LeagueRegionStat directoryStat
    ) {
        this.context = context;
        this.cloudRepository = cloudRepository;
        this.year = year;
        this.region = region;
        this.directoryStat = directoryStat;
        number.setMaximumFractionDigits(2);

        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout panel = column();
        panel.setPadding(dp(12), dp(7), dp(12), dp(10));
        panel.setBackground(panelBackground());
        panel.setElevation(dp(16));

        View handle = new View(context);
        handle.setBackground(round(GreenTheme.color(R.color.zd_muted), 99, GreenTheme.color(R.color.zd_muted)));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(44), dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(7);
        panel.addView(handle, handleLp);

        panel.addView(buildHeader());

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        reportHost = column();
        reportHost.setPadding(0, dp(5), 0, 0);
        scroll.addView(reportHost, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout actions = row();
        detailButton = actionButton("☷  Detaylı Liste", true);
        detailButton.setEnabled(false);
        detailButton.setAlpha(.45f);
        detailButton.setOnClickListener(v -> {
            if (currentReport != null && currentGrades != null && !currentGrades.isEmpty()) {
                showDetailedGradeList(currentReport, currentGrades);
            }
        });
        TextView close = actionButton("Kapat", false);
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams primaryLp = new LinearLayout.LayoutParams(0, dp(44), 1.35f);
        primaryLp.rightMargin = dp(6);
        actions.addView(detailButton, primaryLp);
        actions.addView(close, new LinearLayout.LayoutParams(0, dp(44), 1f));
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, dp(44));
        actionsLp.topMargin = dp(7);
        panel.addView(actions, actionsLp);

        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        // Referans tasarımda popup ekranın yaklaşık alt yarısını kaplıyor;
        // uzun içerik kendi içinde kaydırılıyor. Böylece hero + bölge kartları
        // arka planda görünür kalıyor.
        int desiredHeight = Math.min(dp(620), Math.round(screenHeight * 0.52f));
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(-1, desiredHeight, Gravity.BOTTOM);
        // ProducerFeedActivity alt navigasyonunu görünür bırak.
        panelLp.bottomMargin = dp(78);
        panelLp.leftMargin = dp(1);
        panelLp.rightMargin = dp(1);
        root.addView(panel, panelLp);

        dialog.setContentView(root);
    }

    public static void show(
            Context context,
            MarketCloudRepository cloudRepository,
            int year,
            ZeytinLeagueRepository.Region region,
            MarketCloudRepository.LeagueRegionStat directoryStat
    ) {
        if (context == null || region == null) return;
        ZeytinLeagueRegionReportDialog sheet = new ZeytinLeagueRegionReportDialog(
                context,
                cloudRepository,
                year,
                region,
                directoryStat
        );
        sheet.showInternal();
    }

    private void showInternal() {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.BOTTOM);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = 0.18f;
            window.setAttributes(lp);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        showLoading();
        if (cloudRepository == null || !cloudRepository.canUseCloud()) {
            bindReport(fallbackFromCard());
            return;
        }

        cloudRepository.loadLeagueRegionReport(
                year,
                region.id,
                region.name,
                (report, error) -> {
                    if (!dialog.isShowing()) return;
                    if (report == null) report = fallbackFromCard();
                    bindReport(report);
                }
        );
    }

    private LinearLayout buildHeader() {
        LinearLayout header = row();
        header.setPadding(2, 0, 0, dp(4));

        ImageView olive = new ImageView(context);
        olive.setImageResource(R.drawable.ic_olive);
        olive.setColorFilter(green);
        olive.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(olive, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout copy = column();
        copy.setPadding(dp(8), 0, dp(6), 0);
        copy.addView(label(region.name + " Bölge Raporu", 18, text, true));
        copy.addView(label("Bölgedeki anonim satış verilerine göre hazırlanmıştır.", 10, muted, false));
        header.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView close = label("×", 26, muted, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Bölge raporunu kapat");
        close.setBackground(round(surface, 99, border));
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(38), dp(38)));
        return header;
    }

    private void showLoading() {
        currentReport = null;
        currentGrades = new ArrayList<>();
        detailButton.setEnabled(false);
        detailButton.setAlpha(.45f);
        reportHost.removeAllViews();
        TextView loading = label("Bölge raporu hazırlanıyor…", 13, muted, true);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, dp(36), 0, dp(36));
        reportHost.addView(loading, new LinearLayout.LayoutParams(-1, -2));
    }

    private void bindReport(MarketCloudRepository.LeagueRegionReport report) {
        reportHost.removeAllViews();

        TextView seasonCaption = label(year + " sezon özeti", 10, muted, false);
        seasonCaption.setPadding(dp(2), 0, 0, dp(5));
        reportHost.addView(seasonCaption);

        LinearLayout summary = row();
        summary.addView(metricCard(R.drawable.ic_elek_nav, "Toplam satış", report.saleCount > 0 ? String.valueOf(report.saleCount) : "—"), weight(1));
        summary.addView(metricCard(R.drawable.ic_profile_nav, "Toplam üretici", report.producerCount > 0 ? String.valueOf(report.producerCount) : "—"), weight(1));
        summary.addView(metricCard(R.drawable.ic_garden_nav, "Toplam kg", report.totalKg > 0 ? n(report.totalKg) + " kg" : "—"), weight(1));
        summary.addView(metricCard(R.drawable.ic_league_nav, "Genel ort.", report.averagePrice > 0 ? n(report.averagePrice) + " TL/kg" : "—"), weight(1));
        reportHost.addView(summary);

        LinearLayout insights = row();
        String trend = trendDisplay(report);
        insights.addView(insightCard("Piyasa yönü", trend, trendColor(report.trendPct, report.trendAvailable)), weight(1));
        insights.addView(insightCard("Baskın elek", safe(report.dominantGrade, "—"), text), weight(1));
        insights.addView(insightCard("Trend güveni", trustDisplay(report), trustColor(report.trustLabel)), weight(1));
        LinearLayout.LayoutParams insightsLp = new LinearLayout.LayoutParams(-1, -2);
        insightsLp.topMargin = dp(6);
        reportHost.addView(insights, insightsLp);

        LinearLayout gradeSection = box();
        TextView gradeTitle = label("Elek Dağılımı" + (report.totalKg > 0 ? " (Toplam " + n(report.totalKg) + " kg)" : ""), 12, text, true);
        gradeSection.addView(gradeTitle);
        LinearLayout gradeRow = row();
        List<MarketCloudRepository.LeagueGradeReport> grades = normalizedGrades(report.grades);
        currentReport = report;
        currentGrades = grades;
        detailButton.setEnabled(!grades.isEmpty());
        detailButton.setAlpha(grades.isEmpty() ? .45f : 1f);
        double maxKg = 0d;
        for (MarketCloudRepository.LeagueGradeReport g : grades) if (g.kgPublished) maxKg = Math.max(maxKg, g.kg);
        for (MarketCloudRepository.LeagueGradeReport g : grades) {
            gradeRow.addView(gradeCard(g, maxKg, report.totalKg, report.dominantGrade), weight(1));
        }
        LinearLayout.LayoutParams gradeRowLp = new LinearLayout.LayoutParams(-1, -2);
        gradeRowLp.topMargin = dp(6);
        gradeSection.addView(gradeRow, gradeRowLp);
        LinearLayout.LayoutParams gradeLp = new LinearLayout.LayoutParams(-1, -2);
        gradeLp.topMargin = dp(7);
        reportHost.addView(gradeSection, gradeLp);

        LinearLayout chartBox = box();
        LinearLayout chartTitle = row();
        String chartGrade = safe(report.trendGrade, safe(report.dominantGrade, "Piyasa"));
        chartTitle.addView(label("Son 30 Gün • " + chartGrade, 12, text, true), new LinearLayout.LayoutParams(0, -2, 1));
        chartTitle.addView(label("TL/kg", 9, muted, false));
        chartBox.addView(chartTitle);
        TextView trendMeta = label(trendComparisonSentence(report), 8.5f, report.trendAvailable ? green : muted, false);
        trendMeta.setPadding(0, dp(2), 0, 0);
        chartBox.addView(trendMeta);
        LeagueRegionReportChartView chart = new LeagueRegionReportChartView(context);
        chart.setPoints(report.chart);
        LinearLayout.LayoutParams chartLp = new LinearLayout.LayoutParams(-1, dp(122));
        chartLp.topMargin = dp(4);
        chartBox.addView(chart, chartLp);

        LinearLayout facts = row();
        facts.addView(factCard("En düşük", report.minPrice > 0 ? n(report.minPrice) + " TL" : "—", danger), weight(1));
        facts.addView(factCard("Medyan", report.medianPrice > 0 ? n(report.medianPrice) + " TL" : "—", text), weight(1));
        facts.addView(factCard("En yüksek", report.maxPrice > 0 ? n(report.maxPrice) + " TL" : "—", green), weight(1));
        LinearLayout.LayoutParams factsLp = new LinearLayout.LayoutParams(-1, -2);
        factsLp.topMargin = dp(5);
        chartBox.addView(facts, factsLp);
        LinearLayout.LayoutParams chartBoxLp = new LinearLayout.LayoutParams(-1, -2);
        chartBoxLp.topMargin = dp(7);
        reportHost.addView(chartBox, chartBoxLp);

        TextView note = label(summarySentence(report, grades), 10, text, false);
        note.setPadding(dp(9), dp(7), dp(9), dp(7));
        note.setBackground(round(surface, 12, border));
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.topMargin = dp(7);
        reportHost.addView(note, noteLp);

        if (!report.available) {
            TextView privacy = label(
                    "Kg, fiyat ve satış rakamları bölgede ve elekte en az " + MarketBackendConfig.PUBLIC_MARKET_MIN_PRODUCERS + " farklı üretici olduğunda açılır.",
                    9,
                    gold,
                    false
            );
            privacy.setPadding(dp(8), dp(7), dp(8), dp(7));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
            p.topMargin = dp(4);
            reportHost.addView(privacy, p);
        }

    }

    private LinearLayout metricCard(int iconRes, String title, String value) {
        LinearLayout box = column();
        box.setPadding(dp(4), dp(6), dp(4), dp(6));
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setBackground(round(card, 12, border));
        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(GreenTheme.color(R.color.zd_text_soft));
        box.addView(icon, new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView t = label(title, 8, muted, false); t.setGravity(Gravity.CENTER); box.addView(t);
        TextView v = label(value, 11, text, true); v.setGravity(Gravity.CENTER); v.setMaxLines(2); box.addView(v);
        applyGap(box);
        return box;
    }

    private LinearLayout insightCard(String title, String value, int color) {
        LinearLayout box = column();
        box.setPadding(dp(7), dp(7), dp(7), dp(7));
        box.setBackground(round(card, 12, border));
        TextView a = label(title, 8, muted, false); a.setGravity(Gravity.CENTER); box.addView(a);
        TextView b = label(value, 11, color, true); b.setGravity(Gravity.CENTER); b.setMaxLines(2); box.addView(b);
        applyGap(box);
        return box;
    }

    private LinearLayout gradeCard(
            MarketCloudRepository.LeagueGradeReport grade,
            double maxKg,
            double totalKg,
            String dominantGrade
    ) {
        LinearLayout box = column();
        box.setPadding(dp(3), dp(6), dp(3), dp(6));
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        boolean dominant = grade.grade.equalsIgnoreCase(safe(dominantGrade, ""));
        box.setBackground(round(dominant ? greenSoft : card, 10, dominant ? green : border));

        TextView name = label(grade.grade, 8, dominant ? green : text, dominant);
        name.setGravity(Gravity.CENTER);
        box.addView(name);

        String kgText = grade.kgPublished && grade.kg > 0 ? n(grade.kg) + " kg" : "— kg";
        TextView kg = label(kgText, 10, text, true);
        kg.setGravity(Gravity.CENTER);
        box.addView(kg);

        FrameLayout track = new FrameLayout(context);
        track.setBackground(round(border, 99, border));
        View fill = new View(context);
        fill.setBackground(round(green, 99, green));
        int width = dp(34);
        if (grade.kgPublished && maxKg > 0d) {
            width = Math.max(dp(4), Math.round(dp(34) * (float) (grade.kg / maxKg)));
        }
        track.addView(fill, new FrameLayout.LayoutParams(width, dp(5)));
        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(dp(34), dp(5));
        trackLp.topMargin = dp(4);
        box.addView(track, trackLp);

        String foot;
        if (grade.kgPublished && totalKg > 0d) foot = Math.round(grade.kg * 100d / totalKg) + "%";
        else if (grade.averagePrice > 0d) foot = n(grade.averagePrice) + "₺";
        else foot = "—";
        TextView f = label(foot, 8, grade.kgPublished ? green : muted, false);
        f.setGravity(Gravity.CENTER);
        box.addView(f);

        String trendText = grade.trendAvailable ? signedPct(grade.trendPct) : "trend yok";
        TextView t = label(
                trendText,
                7.5f,
                grade.trendAvailable ? trendColor(grade.trendPct, true) : muted,
                grade.trendAvailable
        );
        t.setGravity(Gravity.CENTER);
        box.addView(t);
        return box;
    }

    private LinearLayout factCard(String title, String value, int valueColor) {
        LinearLayout box = column();
        box.setPadding(dp(4), dp(5), dp(4), dp(5));
        box.setBackground(round(surface, 10, border));
        TextView a = label(title, 8, muted, false); a.setGravity(Gravity.CENTER); box.addView(a);
        TextView b = label(value, 10, valueColor, true); b.setGravity(Gravity.CENTER); box.addView(b);
        applyGap(box);
        return box;
    }

    private void showDetailedGradeList(
            MarketCloudRepository.LeagueRegionReport report,
            List<MarketCloudRepository.LeagueGradeReport> grades
    ) {
        Dialog detail = new Dialog(context);
        detail.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = column();
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setBackground(panelBackground());
        panel.addView(label(region.name + " • Elek Detayı", 17, text, true));
        TextView sub = label("Anonim bölgesel özet", 10, muted, false);
        sub.setPadding(0, 0, 0, dp(8)); panel.addView(sub);

        for (MarketCloudRepository.LeagueGradeReport g : grades) {
            LinearLayout line = row();
            line.setPadding(dp(8), dp(7), dp(8), dp(7));
            line.setBackground(round(card, 10, border));
            TextView name = label(g.grade, 11, text, true);
            line.addView(name, new LinearLayout.LayoutParams(0, -2, .72f));

            LinearLayout metrics = column();
            StringBuilder values = new StringBuilder();
            values.append(g.kgPublished && g.kg > 0d ? n(g.kg) + " kg" : "— kg");
            if (g.saleCount > 0) values.append(" • ").append(g.saleCount).append(" satış");
            if (g.medianPrice > 0d) values.append(" • ").append(n(g.medianPrice)).append(" TL/kg");
            else if (g.averagePrice > 0d) values.append(" • ").append(n(g.averagePrice)).append(" TL/kg");
            TextView metric = label(values.toString(), 9.5f, muted, false);
            metric.setGravity(Gravity.END);
            metrics.addView(metric);

            String trendLine = g.trendAvailable
                    ? "7g " + n(g.trendCurrentMedian) + " TL • önceki " + n(g.trendPreviousMedian) + " TL • " + signedPct(g.trendPct)
                    : "Trend için iki 7 günlük dönemde yeterli veri yok";
            TextView trendMetric = label(
                    trendLine,
                    8.2f,
                    g.trendAvailable ? trendColor(g.trendPct, true) : muted,
                    g.trendAvailable
            );
            trendMetric.setGravity(Gravity.END);
            metrics.addView(trendMetric);
            line.addView(metrics, new LinearLayout.LayoutParams(0, -2, 2.28f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(5);
            panel.addView(line, lp);
        }

        TextView close = actionButton("Kapat", false);
        close.setOnClickListener(v -> detail.dismiss());
        panel.addView(close, new LinearLayout.LayoutParams(-1, dp(44)));
        detail.setContentView(panel);
        detail.show();
        Window w = detail.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(Math.round(context.getResources().getDisplayMetrics().widthPixels * .92f), WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = .45f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    private MarketCloudRepository.LeagueRegionReport fallbackFromCard() {
        MarketCloudRepository.LeagueRegionReport out = new MarketCloudRepository.LeagueRegionReport();
        out.regionId = region.id;
        out.regionName = region.name;
        out.producerCount = directoryStat != null ? directoryStat.producerCount : Math.max(0, region.count);
        out.available = directoryStat != null ? directoryStat.published : region.aggregatePublished;
        out.totalKg = directoryStat != null && directoryStat.totalKg > 0d ? directoryStat.totalKg : Math.max(0d, region.kg);
        out.averagePrice = directoryStat != null && directoryStat.averagePrice > 0d
                ? directoryStat.averagePrice
                : (region.kg > 0d ? region.amount / region.kg : 0d);
        out.dominantGrade = directoryStat != null && !safe(directoryStat.dominantGrade, "").isEmpty()
                ? directoryStat.dominantGrade
                : region.dominant;
        out.trustLabel = out.producerCount > 0 ? "Düşük" : "Veri yok";
        out.status = "Bölgesel kart özeti";
        return out;
    }

    private String summarySentence(
            MarketCloudRepository.LeagueRegionReport report,
            List<MarketCloudRepository.LeagueGradeReport> grades
    ) {
        MarketCloudRepository.LeagueGradeReport biggest = null;
        for (MarketCloudRepository.LeagueGradeReport grade : grades) {
            if (!grade.kgPublished || grade.kg <= 0d) continue;
            if (biggest == null || grade.kg > biggest.kg) biggest = grade;
        }
        MarketCloudRepository.LeagueGradeReport one = findGrade(grades, "1 No");

        if (!report.available) {
            return "Bu bölgede " + report.producerCount
                    + " üretici görünüyor. Ayrıntılı satış ve kg dağılımı için anonimlik eşiği bekleniyor.";
        }

        StringBuilder out = new StringBuilder();
        if (report.saleCount > 0) {
            out.append("Bu bölgede toplam ").append(report.saleCount).append(" satış kaydı var. ");
        }
        if (biggest != null) {
            out.append("En çok çıkan elek ").append(biggest.grade).append(". ");
        }
        if (one != null && one.kgPublished && one.kg > 0d) {
            out.append("1 No toplam ").append(n(one.kg)).append(" kg. ");
        }
        if (report.trendAvailable) {
            out.append(safe(report.trendGrade, report.dominantGrade))
                    .append(" son 7 günde önceki 7 güne göre ")
                    .append(signedPct(report.trendPct))
                    .append(" değişti.");
        } else {
            out.append("Trend için iki ardışık 7 günlük dönemde yeterli farklı üretici ve en az 2 farklı gün gerekiyor.");
        }
        return out.length() == 0 ? "Anonim bölgesel veri özeti hazır." : out.toString().trim();
    }

    private List<MarketCloudRepository.LeagueGradeReport> normalizedGrades(List<MarketCloudRepository.LeagueGradeReport> source) {
        String[] order = new String[]{"Double", "1 No", "2 No", "3 No", "4 No", "Yağlık"};
        List<MarketCloudRepository.LeagueGradeReport> out = new ArrayList<>();
        for (String name : order) {
            MarketCloudRepository.LeagueGradeReport found = findGrade(source, name);
            if (found == null) {
                found = new MarketCloudRepository.LeagueGradeReport();
                found.grade = name;
            }
            out.add(found);
        }
        return out;
    }

    private MarketCloudRepository.LeagueGradeReport findGrade(List<MarketCloudRepository.LeagueGradeReport> source, String name) {
        if (source == null) return null;
        for (MarketCloudRepository.LeagueGradeReport grade : source) {
            if (grade != null && name.equalsIgnoreCase(grade.grade)) return grade;
        }
        return null;
    }

    private String trendDisplay(MarketCloudRepository.LeagueRegionReport report) {
        if (report == null || !report.trendAvailable) return "Trend oluşmadı";
        return trendLabel(report.trendPct, true) + "\n" + signedPct(report.trendPct);
    }

    private String trendComparisonSentence(MarketCloudRepository.LeagueRegionReport report) {
        if (report != null && report.trendAvailable) {
            return "Son 7 gün medyan " + n(report.trendCurrentMedian)
                    + " TL • önceki 7 gün " + n(report.trendPreviousMedian)
                    + " TL • " + signedPct(report.trendPct);
        }
        return "Trend için her iki 7 günlük dönemde yeterli farklı üretici ve en az 2 farklı gün gerekir.";
    }

    private String trendLabel(double pct, boolean available) {
        if (!available) return "Trend oluşmadı";
        if (pct >= 5d) return "Yükseliş";
        if (pct >= 1.5d) return "Hafif yükseliş";
        if (pct <= -5d) return "Düşüş";
        if (pct <= -1.5d) return "Hafif düşüş";
        return "Yatay";
    }

    private String signedPct(double pct) {
        String prefix = pct > 0.004d ? "+" : "";
        return prefix + n(pct) + "%";
    }

    private int trendColor(double pct, boolean available) {
        if (!available) return muted;
        if (pct >= 1.5d) return green;
        if (pct <= -1.5d) return danger;
        return gold;
    }

    private String trustDisplay(MarketCloudRepository.LeagueRegionReport report) {
        String value = safe(report == null ? "" : report.trustLabel, report == null ? "Veri yok" : trustFor(report.producerCount));
        if (report == null) return value;
        int days = Math.max(0, report.trendCurrentDays) + Math.max(0, report.trendPreviousDays);
        if (days > 0) return value + "\n" + report.producerCount + " üretici • " + days + " gün";
        if (report.producerCount > 0) return value + "\n" + report.producerCount + " üretici";
        return value;
    }

    private String trustFor(int producers) {
        if (producers >= 5) return "Yüksek";
        if (producers >= 2) return "Orta";
        if (producers == 1) return "Düşük";
        return "Veri yok";
    }

    private int trustColor(String label) {
        String value = safe(label, "").toLowerCase(new Locale("tr", "TR"));
        if (value.contains("yüksek")) return green;
        if (value.contains("orta")) return gold;
        return muted;
    }

    private LinearLayout box() {
        LinearLayout l = column();
        l.setPadding(dp(9), dp(8), dp(9), dp(8));
        l.setBackground(round(card, 14, border));
        return l;
    }

    private TextView actionButton(String value, boolean primary) {
        TextView button = label(value, 12, primary ? bg : text, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(round(primary ? green : surface, 22, primary ? green : border));
        return button;
    }

    private void applyGap(View view) {
        LinearLayout.LayoutParams current = view.getLayoutParams() instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) view.getLayoutParams()
                : null;
        // Actual spacing is supplied by the weight LayoutParams in the parent.
    }

    private LinearLayout.LayoutParams weight(float value) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, value);
        lp.rightMargin = dp(4);
        return lp;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private TextView label(String value, float size, int color, boolean bold) {
        TextView t = new TextView(context);
        t.setText(value == null ? "" : value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(surface);
        d.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), dp(16), dp(16), dp(16), dp(16)});
        d.setStroke(dp(1), border);
        return d;
    }

    private GradientDrawable round(int fill, float radiusDp, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private String n(double value) {
        if (!Double.isFinite(value)) return "—";
        return number.format(value);
    }

    private String safe(String value, String fallback) {
        String s = value == null ? "" : value.trim();
        return s.isEmpty() ? fallback : s;
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
