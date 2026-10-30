package com.zeytindefteri.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Zeytin Defteri - Piyasa Sekmesi UI Katmanı
 *
 * Bu sınıf UI katmanıdır.
 * Bu aşamada doğrudan SQLite / Firebase sorgusu yapmaz.
 *
 * Gerçek piyasa verileri MarketUiState üzerinden bindState(...)
 * metoduyla ekrana bağlanabilir.
 */
public final class MarketView extends ScrollView {

    // ============================================================
    // EVENT LISTENER
    // ============================================================

    public interface MarketEventListener {

        void onGradeSelected(
                String grade
        );

        void onRegionFilterClicked();

        void onPeriodFilterClicked();

        /** Grafik kartındaki dönem sekmesi (7G / 30G / 90G / Sezon). */
        default void onPeriodSelected(String period) { onPeriodFilterClicked(); }

        void onCalculationInfoClicked();
    }


    // ============================================================
    // RENKLER
    // ============================================================

    private final int bg =
            GreenTheme.color(
                    R.color.zd_bg
            );

    private final int surface =
            GreenTheme.color(
                    R.color.zd_surface
            );

    private final int card =
            GreenTheme.color(
                    R.color.zd_card
            );

    private final int input =
            GreenTheme.color(
                    R.color.zd_input
            );

    private final int text =
            GreenTheme.color(
                    R.color.zd_text
            );

    private final int textSoft =
            GreenTheme.color(
                    R.color.zd_text_soft
            );

    private final int muted =
            GreenTheme.color(
                    R.color.zd_muted
            );

    private final int border =
            GreenTheme.color(
                    R.color.zd_border
            );

    private final int green =
            GreenTheme.color(
                    R.color.zd_green
            );

    private final int greenMid =
            GreenTheme.color(
                    R.color.zd_green_mid
            );

    private final int greenSoft =
            GreenTheme.color(
                    R.color.zd_green_soft
            );

    private final int danger =
            GreenTheme.color(
                    R.color.zd_danger
            );


    // ============================================================
    // SEZON / KULLANICI
    // ============================================================

    private final int year;

    private final String uid;


    // ============================================================
    // STATE
    // ============================================================

    private MarketUiState state;

    private MarketEventListener listener;


    // ============================================================
    // DİNAMİK UI REFERANSLARI
    // ============================================================

    private TextView subtitleText;

    private LinearLayout gradeChipContainer;

    private final List<GradeChip> gradeChips =
            new ArrayList<>();


    private TextView filterRegionText;

    private TextView filterPeriodText;

    private TextView marketTitleText;

    private TextView priceText;

    private TextView rangeText;

    private TextView changeText;

    private TextView salesCountText;

    private TextView totalKgText;

    private TextView dataBadgeText;

    private TextView chartPeriodText;

    private PriceChartView chartView;

    private TextView chartEmptyText;

    /** Grafiğin üstündeki satır: Son / Yüksek / Düşük / Değişim; altındaki kaynak notu. */
    private LinearLayout chartStatsRow;
    /** Kahraman kartı: fiyat yanındaki değişim rozeti, aralık çubuğu, "senin fiyatın" satırı. */
    private TextView heroBadgeText, ownLineText;
    private RangeBarView rangeBar;
    /** Grafik kartındaki dönem sekmeleri. */
    private final java.util.List<TextView> periodTabs = new java.util.ArrayList<>();
    private TextView chartStatLast, chartStatHigh, chartStatLow, chartStatChange, chartNoteText;

    private LinearLayout regionsContainer;

    private TextView ownSaleText;

    private TextView marketRegionLabelText;

    private TextView marketSaleText;

    private TextView comparisonText;


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public MarketView(
            Context context,
            int year,
            String uid
    ) {

        super(
                context
        );


        this.year =
                year;


        this.uid =
                uid == null
                        ? ""
                        : uid;


        this.state =
                MarketUiState.demo();


        initLayout();


        bindState(
                this.state
        );
    }


    // ============================================================
    // LISTENER
    // ============================================================

    public void setMarketEventListener(
            MarketEventListener listener
    ) {

        this.listener =
                listener;
    }


    // ============================================================
    // ANA LAYOUT
    // ============================================================

    private void initLayout() {

        setFillViewport(
                true
        );


        setVerticalScrollBarEnabled(
                false
        );


        setOverScrollMode(
                OVER_SCROLL_NEVER
        );


        setBackgroundColor(
                bg
        );


        LinearLayout body =
                column();


        body.setPadding(
                dp(12),
                dp(10),
                dp(12),
                dp(20)
        );


        addView(
                body,
                new ScrollView.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT
                )
        );


        // ========================================================
        // ALT BAŞLIK
        // ========================================================

        subtitleText =
                label(
                        "Gerçek satışlardan oluşan bölgesel fiyatlar",
                        12,
                        muted,
                        false
                );


        subtitleText.setGravity(
                Gravity.CENTER
        );

        // Keep the source/status caption geometry stable while local data is
        // replaced by the anonymous cloud result. This prevents the content
        // below it from visibly jumping.
        subtitleText.setMinHeight(dp(34));
        subtitleText.setMaxLines(2);
        subtitleText.setEllipsize(TextUtils.TruncateAt.END);


        body.addView(
                subtitleText,
                matchWrap(
                        0,
                        10
                )
        );


        // ========================================================
        // FİLTRELER
        // ========================================================

        body.addView(
                buildFilters(),
                matchWrap(
                        0,
                        8
                )
        );


        // ========================================================
        // ELEKLER
        // ========================================================

        gradeChipContainer =
                new LinearLayout(
                        getContext()
                );


        gradeChipContainer.setOrientation(
                LinearLayout.HORIZONTAL
        );


        gradeChipContainer.setGravity(
                Gravity.CENTER_VERTICAL
        );


        body.addView(
                gradeChipContainer,
                matchWrap(
                        0,
                        10
                )
        );


        // ========================================================
        // ANA PİYASA KARTI
        //
        // ÖNEMLİ:
        // WRAP_CONTENT YERİNE KESİN 190dp.
        //
        // Böylece içindeki MATCH_PARENT fotoğraf
        // kartı gereksiz şekilde büyütemez.
        // ========================================================

        LinearLayout.LayoutParams heroParams =
                new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        dp(190)
                );


        heroParams.bottomMargin =
                dp(10);


        body.addView(
                buildMarketHero(),
                heroParams
        );


        // ========================================================
        // DİĞER KARTLAR
        // ========================================================

        body.addView(
                buildPriceChangeCard(),
                matchWrap(
                        0,
                        10
                )
        );


        body.addView(
                buildRegionsCard(),
                matchWrap(
                        0,
                        10
                )
        );


        body.addView(
                buildOwnSaleCard(),
                matchWrap(
                        0,
                        10
                )
        );


        body.addView(
                buildCalculationRow(),
                matchWrap(
                        0,
                        0
                )
        );
    }


    public void setSourceCaption(String value) {
        if (subtitleText == null) return;
        String caption = value == null ? "" : value.trim();
        subtitleText.setText(
                caption.isEmpty()
                        ? "Gerçek satışlardan oluşan bölgesel fiyatlar"
                        : caption
        );
    }


    // ============================================================
    // STATE BINDING
    // ============================================================

    public void bindState(
            MarketUiState newState
    ) {

        if (
                newState == null
        ) {
            return;
        }


        this.state =
                newState;


        // ========================================================
        // FİLTRELER
        // ========================================================

        if (
                filterRegionText != null
        ) {

            filterRegionText.setText(
                    safeText(
                            newState.selectedRegion,
                            "Bölge"
                    )
                            + "  ⌄"
            );
        }


        if (
                filterPeriodText != null
        ) {

            filterPeriodText.setText(
                    safeText(
                            newState.selectedPeriod,
                            "Dönem"
                    )
                            + "  ⌄"
            );
        }


        // ========================================================
        // ELEKLER
        // ========================================================

        updateGradeChips(
                newState.grades,
                newState.grade
        );


        // ========================================================
        // ANA KART
        // ========================================================

        if (
                marketTitleText != null
        ) {

            marketTitleText.setText(
                    safeText(
                            newState.grade,
                            "Piyasa"
                    ).toUpperCase()
                            + " PİYASASI"
            );
        }


        if (
                priceText != null
        ) {

            priceText.setText(
                    safeText(
                            newState.medianPrice,
                            "—"
                    )
            );
        }


        if (
                rangeText != null
        ) {

            rangeText.setText(
                    safeText(
                            newState.range,
                            "—"
                    )
            );
        }


        if (
                changeText != null
        ) {

            String changeValue =
                    safeText(
                            newState.change,
                            ""
                    );

            changeText.setText(
                    changeValue
            );
            // "Önceki 30 günde veri yok" gibi olumsuz cümle kahraman kartında durmasın.
            changeText.setVisibility(changeValue.toLowerCase().contains("veri yok") ? View.INVISIBLE : View.VISIBLE);

            changeText.setTextColor(
                    changeValue.startsWith("-")
                            ? danger
                            : changeValue.toLowerCase().contains("veri yok")
                            ? muted
                            : green
            );
        }


        if (
                salesCountText != null
        ) {

            salesCountText.setText(
                    "▣  "
                            + safeText(
                            newState.salesCount,
                            "0 kayıt"
                    )
            );
        }


        if (
                totalKgText != null
        ) {

            totalKgText.setText(
                    "▰  "
                            + safeText(
                            newState.totalKg,
                            "0 kg"
                    )
            );
        }


        if (
                dataBadgeText != null
        ) {

            String badgeValue =
                    safeText(
                            newState.dataBadge,
                            "Veri Yok"
                    );

            boolean noData =
                    "Veri Yok".equalsIgnoreCase(
                            badgeValue
                    );

            dataBadgeText.setText(
                    (noData ? "•  " : "✓  ")
                            + badgeValue
            );

            dataBadgeText.setTextColor(
                    noData
                            ? muted
                            : text
            );

            dataBadgeText.setBackground(
                    noData
                            ? round(
                            surface,
                            14,
                            border
                    )
                            : round(
                            greenSoft,
                            14,
                            green
                    )
            );
        }


        // ========================================================
        // GRAFİK
        // ========================================================

        if (
                chartPeriodText != null
        ) {

            chartPeriodText.setText(
                    safeText(
                            newState.selectedPeriod,
                            "Dönem"
                    )
                            + "  ⌄"
            );
        }


        if (
                chartView != null
        ) {

            chartView.setData(
                    newState.chartValues,
                    newState.chartStartDate,
                    newState.chartEndDate
            );
            chartView.setLabels(newState.chartLabels);
            applyChartStats(newState);
            paintPeriodTabs(newState.selectedPeriod);
            applyHeroExtras(newState);

            int chartPointCount =
                    newState.chartValues == null
                            ? 0
                            : newState.chartValues.length;

            boolean chartReady =
                    chartPointCount >= 2;

            chartView.setVisibility(
                    chartReady
                            ? View.VISIBLE
                            : View.GONE
            );

            if (
                    chartEmptyText != null
            ) {

                chartEmptyText.setVisibility(
                        chartReady
                                ? View.GONE
                                : View.VISIBLE
                );

                chartEmptyText.setText(
                        chartPointCount == 1
                                ? "Fiyat grafiği için en az 2 fiyat kaydı gerekiyor."
                                : "Bu elek ve dönem için fiyat hareketi verisi yok."
                );
            }
        }


        // ========================================================
        // BÖLGELER
        // ========================================================

        updateRegions(
                newState.regions
        );


        // ========================================================
        // KULLANICININ SATIŞI
        // ========================================================

        if (
                ownSaleText != null
        ) {

            ownSaleText.setText(
                    safeText(
                            newState.ownSale,
                            "—"
                    )
            );
        }


        if (
                marketRegionLabelText != null
        ) {

            marketRegionLabelText.setText(
                    safeText(
                            newState.selectedRegion,
                            "Bölge"
                    )
                            + " piyasası"
            );
        }


        if (
                marketSaleText != null
        ) {

            marketSaleText.setText(
                    safeText(
                            newState.marketSale,
                            "—"
                    )
            );
        }


        if (
                comparisonText != null
        ) {

            String comparison =
                    safeText(
                            newState.ownComparison,
                            ""
                    );

            String lower =
                    comparison.toLowerCase();

            String prefix =
                    lower.contains("altında")
                            ? "↘  "
                            : lower.contains("aynı")
                            ? "→  "
                            : lower.contains("yok")
                            || lower.contains("henüz")
                            ? "•  "
                            : "↗  ";

            comparisonText.setText(
                    comparison.isEmpty()
                            ? ""
                            : prefix + comparison
            );

            comparisonText.setTextColor(
                    lower.contains("altında")
                            ? danger
                            : lower.contains("yok")
                            || lower.contains("henüz")
                            ? muted
                            : green
            );
        }
    }


    // ============================================================
    // FİLTRELER
    // ============================================================

    private View buildFilters() {

        LinearLayout row =
                new LinearLayout(
                        getContext()
                );


        row.setOrientation(
                LinearLayout.HORIZONTAL
        );


        row.setGravity(
                Gravity.CENTER_VERTICAL
        );


        filterRegionText =
                filterField(
                        state.selectedRegion
                );


        filterRegionText.setOnClickListener(
                v -> {

                    if (
                            listener != null
                    ) {

                        listener.onRegionFilterClicked();
                    }
                }
        );


        filterPeriodText =
                filterField(
                        state.selectedPeriod
                );


        filterPeriodText.setOnClickListener(
                v -> {

                    if (
                            listener != null
                    ) {

                        listener.onPeriodFilterClicked();
                    }
                }
        );


        LinearLayout.LayoutParams leftLp =
                new LinearLayout.LayoutParams(
                        0,
                        dp(42),
                        1f
                );


        leftLp.rightMargin =
                dp(4);


        LinearLayout.LayoutParams rightLp =
                new LinearLayout.LayoutParams(
                        0,
                        dp(42),
                        1f
                );


        rightLp.leftMargin =
                dp(4);


        row.addView(
                filterRegionText,
                leftLp
        );


        row.addView(
                filterPeriodText,
                rightLp
        );


        return row;
    }


    private TextView filterField(
            String value
    ) {

        TextView field =
                label(
                        safeText(
                                value,
                                ""
                        )
                                + "  ⌄",
                        12,
                        text,
                        true
                );


        field.setGravity(
                Gravity.CENTER_VERTICAL
        );


        field.setPadding(
                dp(13),
                0,
                dp(10),
                0
        );


        field.setBackground(
                round(
                        input,
                        12,
                        border
                )
        );


        field.setMaxLines(
                1
        );


        field.setEllipsize(
                TextUtils.TruncateAt.END
        );


        return field;
    }


    // ============================================================
    // ELEK ÇİPLERİ
    // ============================================================

    private void updateGradeChips(
            List<String> grades,
            String selectedGrade
    ) {

        if (
                gradeChipContainer == null
        ) {
            return;
        }


        if (
                grades == null
                        ||
                        grades.isEmpty()
        ) {
            gradeChipContainer.removeAllViews();
            gradeChips.clear();
            return;
        }

        // bindState() runs first with the local preview and again when the
        // cloud result arrives. Recreating all chips on both passes caused a
        // visible flash/jitter. Reuse the existing chip views when the grade
        // structure has not changed.
        boolean sameStructure = grades.size() == gradeChips.size();
        if (sameStructure) {
            for (int i = 0; i < grades.size(); i++) {
                if (!safeText(grades.get(i), "").equals(gradeChips.get(i).grade)) {
                    sameStructure = false;
                    break;
                }
            }
        }

        if (sameStructure) {
            refreshGradeChipStyles(selectedGrade);
            return;
        }

        gradeChipContainer.removeAllViews();
        gradeChips.clear();


        for (
                int i = 0;
                i < grades.size();
                i++
        ) {

            final String grade =
                    grades.get(i);


            TextView chip =
                    label(
                            grade,
                            11f,
                            muted,
                            false
                    );


            chip.setGravity(
                    Gravity.CENTER
            );


            chip.setMaxLines(
                    1
            );


            chip.setPadding(
                    dp(3),
                    0,
                    dp(3),
                    0
            );


            chip.setOnClickListener(
                    v -> {

                        if (
                                listener != null
                        ) {

                            listener.onGradeSelected(
                                    grade
                            );
                        }


                        selectGrade(
                                grade
                        );
                    }
            );


            gradeChips.add(
                    new GradeChip(
                            grade,
                            chip
                    )
            );


            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(
                            0,
                            dp(36),
                            1f
                    );


            if (
                    i > 0
            ) {

                lp.leftMargin =
                        dp(4);
            }


            gradeChipContainer.addView(
                    chip,
                    lp
            );
        }


        refreshGradeChipStyles(
                selectedGrade
        );
    }


    private void selectGrade(
            String grade
    ) {

        if (
                grade == null
        ) {
            return;
        }


        if (
                state != null
                        &&
                        grade.equals(
                                state.grade
                        )
        ) {
            return;
        }


        if (
                state != null
        ) {

            state.grade =
                    grade;
        }


        refreshGradeChipStyles(
                grade
        );


        if (
                marketTitleText != null
        ) {

            marketTitleText.setText(
                    grade.toUpperCase()
                            + " PİYASASI"
            );
        }
    }


    private void refreshGradeChipStyles(
            String activeGrade
    ) {

        for (
                GradeChip chip :
                gradeChips
        ) {

            boolean active =
                    chip.grade.equalsIgnoreCase(
                            safeText(
                                    activeGrade,
                                    ""
                            )
                    );


            chip.view.setTextColor(
                    active
                            ? text
                            : muted
            );


            chip.view.setTypeface(
                    Typeface.DEFAULT,
                    active
                            ? Typeface.BOLD
                            : Typeface.NORMAL
            );


            chip.view.setBackground(
                    active
                            ?
                            gradientRound(
                                    greenSoft,
                                    greenMid,
                                    18,
                                    green
                            )
                            :
                            round(
                                    surface,
                                    18,
                                    border
                            )
            );
        }
    }


    // ============================================================
    // ANA PİYASA KARTI
    // ============================================================

    private View buildMarketHero() {

        FrameLayout hero =
                new FrameLayout(
                        getContext()
                );


        hero.setBackground(
                round(
                        card,
                        15,
                        border
                )
        );


        hero.setOutlineProvider(
                ViewOutlineProvider.BACKGROUND
        );


        hero.setClipToOutline(
                true
        );


        // ========================================================
        // ZEMİN (fotoğrafsız; Zeytin Ligi başlığıyla aynı yeşil geçiş)
        // ========================================================
        GradientDrawable heroFill =
                new GradientDrawable(
                        GradientDrawable.Orientation.TR_BL,
                        new int[]{
                                0xff24503a,
                                0xff163428,
                                GreenTheme.color(R.color.zd_surface)
                        }
                );
        heroFill.setCornerRadius(dp(15));
        heroFill.setStroke(dp(1), border);
        hero.setBackground(heroFill);
        View heroLine = new View(getContext());
        heroLine.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{GreenTheme.color(R.color.zd_green), GreenTheme.color(R.color.zd_warning)}));
        hero.addView(heroLine, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(3), android.view.Gravity.BOTTOM));
        // ========================================================
        // İÇERİK
        // ========================================================

        LinearLayout content =
                column();


        content.setPadding(
                dp(14),
                dp(11),
                dp(14),
                dp(10)
        );


        marketTitleText =
                label(
                        safeText(
                                state.grade,
                                "Piyasa"
                        ).toUpperCase()
                                + " PİYASASI",
                        12,
                        textSoft,
                        true
                );


        marketTitleText.setLetterSpacing(
                0.06f
        );


        content.addView(
                marketTitleText
        );


        // ========================================================
        // ANA FİYAT
        // ========================================================

        priceText =
                label(
                        state.medianPrice,
                        30,
                        text,
                        true
                );


        priceText.setSingleLine(
                true
        );


        priceText.setIncludeFontPadding(
                false
        );


        LinearLayout priceRow = new LinearLayout(getContext());
        priceRow.setOrientation(LinearLayout.HORIZONTAL);
        priceRow.setGravity(Gravity.CENTER_VERTICAL);
        priceRow.addView(priceText, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        heroBadgeText = label("", 12, text, true);
        heroBadgeText.setGravity(Gravity.CENTER);
        heroBadgeText.setPadding(dp(9), 0, dp(9), 0);
        heroBadgeText.setVisibility(View.GONE);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(26));
        badgeLp.leftMargin = dp(10);
        priceRow.addView(heroBadgeText, badgeLp);
        content.addView(
                priceRow,
                matchWrap(
                        2,
                        0
                )
        );


        // ========================================================
        // MEDYAN
        // ========================================================

        TextView medianLabel =
                label(
                        "Ortadaki fiyat (kayıtların yarısı altında, yarısı üstünde)",
                        11.5f,
                        textSoft,
                        false
                );


        content.addView(
                medianLabel
        );


        // ========================================================
        // ARALIK
        // ========================================================

        rangeText =
                label(
                        state.range,
                        13.5f,
                        text,
                        true
                );


        content.addView(
                rangeText,
                matchWrap(
                        2,
                        0
                )
        );
        // Aralık çubuğu: en düşük — en yüksek, ortadaki fiyat çizgisi ve senin fiyatın (sarı nokta).
        rangeBar = new RangeBarView(getContext(), green, border, text, GreenTheme.color(R.color.zd_warning));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(34));
        barLp.topMargin = dp(6);
        content.addView(rangeBar, barLp);
        ownLineText = label("", 12.5f, text, true);
        ownLineText.setVisibility(View.GONE);
        content.addView(ownLineText, matchWrap(4, 0));


        // ========================================================
        // DEĞİŞİM + GÜVENİLİR VERİ
        // ========================================================

        LinearLayout changeRow =
                new LinearLayout(
                        getContext()
                );


        changeRow.setOrientation(
                LinearLayout.HORIZONTAL
        );


        changeRow.setGravity(
                Gravity.CENTER_VERTICAL
        );


        changeText =
                label(
                        state.change,
                        11.5f,
                        green,
                        false
                );


        changeText.setMaxLines(
                1
        );


        changeText.setEllipsize(
                TextUtils.TruncateAt.END
        );


        changeRow.addView(
                changeText,
                new LinearLayout.LayoutParams(
                        0,
                        dp(30),
                        1f
                )
        );


        dataBadgeText =
                label(
                        "✓  "
                                + safeText(
                                state.dataBadge,
                                "Veri Yok"
                        ),
                        10.5f,
                        text,
                        true
                );


        dataBadgeText.setGravity(
                Gravity.CENTER
        );


        dataBadgeText.setPadding(
                dp(8),
                0,
                dp(8),
                0
        );


        dataBadgeText.setBackground(
                round(
                        greenSoft,
                        14,
                        green
                )
        );


        changeRow.addView(
                dataBadgeText,
                new LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        dp(28)
                )
        );


        content.addView(
                changeRow,
                matchWrap(
                        2,
                        0
                )
        );


        // ========================================================
        // AYRAÇ
        // ========================================================

        View divider =
                new View(
                        getContext()
                );


        divider.setBackgroundColor(
                border
        );


        LinearLayout.LayoutParams divLp =
                new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        dp(1)
                );


        divLp.setMargins(
                0,
                dp(5),
                0,
                dp(5)
        );


        content.addView(
                divider,
                divLp
        );


        // ========================================================
        // SATIŞ + KG
        // ========================================================

        LinearLayout stats =
                new LinearLayout(
                        getContext()
                );


        stats.setOrientation(
                LinearLayout.HORIZONTAL
        );


        stats.setGravity(
                Gravity.CENTER_VERTICAL
        );


        salesCountText =
                label(
                        "▣  "
                                + state.salesCount,
                        11.5f,
                        textSoft,
                        true
                );


        salesCountText.setGravity(
                Gravity.CENTER
        );


        totalKgText =
                label(
                        "▰  "
                                + state.totalKg,
                        11.5f,
                        textSoft,
                        true
                );


        totalKgText.setGravity(
                Gravity.CENTER
        );


        stats.addView(
                salesCountText,
                new LinearLayout.LayoutParams(
                        0,
                        dp(27),
                        1f
                )
        );


        View verticalDivider =
                new View(
                        getContext()
                );


        verticalDivider.setBackgroundColor(
                border
        );


        stats.addView(
                verticalDivider,
                new LinearLayout.LayoutParams(
                        dp(1),
                        dp(14)
                )
        );


        stats.addView(
                totalKgText,
                new LinearLayout.LayoutParams(
                        0,
                        dp(27),
                        1f
                )
        );


        content.addView(
                stats
        );


        hero.addView(
                content,
                new FrameLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT
                )
        );


        return hero;
    }


    // ============================================================
    // FİYAT DEĞİŞİMİ
    // ============================================================

    private View buildPriceChangeCard() {

        LinearLayout box =
                cardBox();


        LinearLayout heading =
                new LinearLayout(
                        getContext()
                );


        heading.setOrientation(
                LinearLayout.HORIZONTAL
        );


        heading.setGravity(
                Gravity.CENTER_VERTICAL
        );


        ImageView icon =
                new ImageView(
                        getContext()
                );


        icon.setImageResource(
                R.drawable.ic_market_nav
        );


        icon.setColorFilter(
                green
        );


        icon.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO
        );


        heading.addView(
                icon,
                new LinearLayout.LayoutParams(
                        dp(18),
                        dp(18)
                )
        );


        TextView title =
                label(
                        "FİYAT DEĞİŞİMİ",
                        12,
                        textSoft,
                        true
                );


        title.setLetterSpacing(
                0.06f
        );


        LinearLayout.LayoutParams titleLp =
                new LinearLayout.LayoutParams(
                        0,
                        LayoutParams.WRAP_CONTENT,
                        1f
                );


        titleLp.leftMargin =
                dp(7);


        heading.addView(
                title,
                titleLp
        );


        box.addView(
                heading
        );

        // Dönem sekmeleri (borsa uygulamalarındaki gibi): 7G · 30G · 90G · Sezon.
        LinearLayout tabs = new LinearLayout(getContext());
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(3), dp(3), dp(3), dp(3));
        tabs.setBackground(round(surface, 16, border));
        periodTabs.clear();
        String[][] periods = {{"7G", MarketUiDataProvider.PERIOD_7_DAYS}, {"30G", MarketUiDataProvider.PERIOD_30_DAYS},
                {"90G", MarketUiDataProvider.PERIOD_90_DAYS}, {"Sezon", MarketUiDataProvider.PERIOD_SEASON}};
        for (String[] pr : periods) {
            TextView tab = label(pr[0], 12, textSoft, true);
            tab.setGravity(Gravity.CENTER);
            tab.setTag(pr[1]);
            tab.setContentDescription("Dönem: " + pr[1]);
            tab.setOnClickListener(v -> { if (listener != null) listener.onPeriodSelected((String) v.getTag()); });
            tabs.addView(tab, new LinearLayout.LayoutParams(0, dp(30), 1f));
            periodTabs.add(tab);
        }
        LinearLayout.LayoutParams tabsLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        tabsLp.topMargin = dp(10);
        box.addView(tabs, tabsLp);
        paintPeriodTabs(state.selectedPeriod);


        chartStatsRow = new LinearLayout(getContext());
        chartStatsRow.setOrientation(LinearLayout.HORIZONTAL);
        chartStatLast = chartStat(chartStatsRow, "SON");
        chartStatHigh = chartStat(chartStatsRow, "YÜKSEK");
        chartStatLow = chartStat(chartStatsRow, "DÜŞÜK");
        chartStatChange = chartStat(chartStatsRow, "DEĞİŞİM");
        LinearLayout.LayoutParams statsLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        statsLp.topMargin = dp(12);
        box.addView(chartStatsRow, statsLp);

        chartEmptyText =
                label(
                        "Bu elek ve dönem için fiyat hareketi verisi yok.",
                        11.5f,
                        muted,
                        false
                );


        chartEmptyText.setGravity(
                Gravity.CENTER
        );


        chartEmptyText.setPadding(
                dp(12),
                dp(8),
                dp(12),
                dp(8)
        );


        chartEmptyText.setBackground(
                round(
                        surface,
                        10,
                        border
                )
        );


        LinearLayout.LayoutParams emptyChartLp =
                new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        dp(62)
                );


        emptyChartLp.topMargin =
                dp(8);


        box.addView(
                chartEmptyText,
                emptyChartLp
        );


        chartView =
                new PriceChartView(
                        getContext(),
                        state.chartValues,
                        state.chartStartDate,
                        state.chartEndDate,
                        green,
                        border,
                        muted,
                        text
                );


        LinearLayout.LayoutParams chartLp =
                new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        dp(200)
                );


        chartLp.topMargin =
                dp(6);


        box.addView(
                chartView,
                chartLp
        );


        chartView.setLabels(state.chartLabels);

        chartNoteText = label("", 10.5f, muted, false);
        chartNoteText.setPadding(0, dp(4), 0, 0);
        box.addView(chartNoteText);

        chartEmptyText.setVisibility(
                View.GONE
        );

        applyChartStats(state);
        applyHeroExtras(state);

        return box;
    }

    private void paintPeriodTabs(String selected) {
        for (TextView t : periodTabs) {
            boolean on = t.getTag() != null && t.getTag().equals(selected);
            t.setTextColor(on ? 0xFF0B1A12 : textSoft);
            t.setBackground(on ? round(green, 13, Color.TRANSPARENT) : null);
            t.setSelected(on);
        }
    }

    /** Fiyat yanındaki değişim rozeti, aralık çubuğu ve "senin fiyatın" satırı. */
    private void applyHeroExtras(MarketUiState st) {
        if (st == null) return;
        if (heroBadgeText != null) {
            double now = st.medianValue, before = st.previousMedianValue;
            String suffix = "";
            if (!(now > 0 && before > 0) && st.chartValues != null && st.chartValues.length >= 2) {
                before = st.chartValues[0];
                now = st.chartValues[st.chartValues.length - 1];
                suffix = " dönemde";
            } else if (now > 0 && before > 0) {
                suffix = " önceki döneme göre";
            }
            if (now > 0 && before > 0) {
                double pct = (now - before) / before * 100d;
                java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(new java.util.Locale("tr", "TR"));
                nf.setMinimumFractionDigits(1);
                nf.setMaximumFractionDigits(1);
                boolean up = pct > 0.05, down = pct < -0.05;
                heroBadgeText.setText((up ? "▲ " : down ? "▼ " : "• ") + "%" + nf.format(Math.abs(pct)));
                heroBadgeText.setTextColor(up ? 0xFF0B1A12 : text);
                heroBadgeText.setBackground(round(up ? green : down ? danger : surface, 13, Color.TRANSPARENT));
                heroBadgeText.setContentDescription((up ? "Yükseldi yüzde " : down ? "Düştü yüzde " : "Değişmedi, yüzde ")
                        + nf.format(Math.abs(pct)) + suffix);
                heroBadgeText.setVisibility(View.VISIBLE);
            } else {
                heroBadgeText.setVisibility(View.GONE);
            }
        }
        if (rangeBar != null) {
            boolean has = st.rangeHighValue > 0 && st.rangeHighValue >= st.rangeLowValue;
            rangeBar.setValues(st.rangeLowValue, st.rangeHighValue, st.medianValue, st.ownPriceValue);
            rangeBar.setVisibility(has ? View.VISIBLE : View.GONE);
            if (rangeText != null) rangeText.setVisibility(has ? View.GONE : View.VISIBLE);
        }
        if (ownLineText != null) {
            String own = st.ownSale == null ? "" : st.ownSale.trim();
            String cmp = st.ownComparison == null ? "" : st.ownComparison.trim();
            if (!own.isEmpty() && !"—".equals(own)) {
                String head = "● Senin son satışın " + own;
                android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(head);
                sb.setSpan(new android.text.style.ForegroundColorSpan(GreenTheme.color(R.color.zd_warning)), 0, 1, 0);
                if (!cmp.isEmpty()) {
                    int at = sb.length();
                    sb.append("\n").append(cmp);
                    sb.setSpan(new android.text.style.ForegroundColorSpan(textSoft), at, sb.length(), 0);
                    sb.setSpan(new android.text.style.StyleSpan(Typeface.NORMAL), at, sb.length(), 0);
                }
                ownLineText.setText(sb);
                ownLineText.setVisibility(View.VISIBLE);
            } else {
                ownLineText.setVisibility(View.GONE);
            }
        }
    }

    /** Borsa uygulamalarındaki gibi küçük başlık + değer sütunu. */
    private TextView chartStat(LinearLayout row, String title) {
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView t = label(title, 10.5f, green, true);
        t.setLetterSpacing(0.04f);
        t.setGravity(Gravity.CENTER);
        col.addView(t);
        TextView v = label("—", 14, text, true);
        v.setGravity(Gravity.CENTER);
        v.setSingleLine(true);
        v.setPadding(0, dp(2), 0, 0);
        col.addView(v);
        row.addView(col, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        return v;
    }

    /** Son / Yüksek / Düşük / Değişim ve kaynak notu; grafik yoksa satır gizlenir. */
    private void applyChartStats(MarketUiState st) {
        if (chartStatsRow == null) return;
        float[] v = st == null ? null : st.chartValues;
        boolean ready = v != null && v.length >= 2;
        chartStatsRow.setVisibility(ready ? View.VISIBLE : View.GONE);
        if (chartNoteText != null) {
            String note = st == null || st.chartNote == null ? "" : st.chartNote;
            chartNoteText.setText(note);
            chartNoteText.setVisibility(ready && !note.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (!ready) return;
        float hi = -Float.MAX_VALUE, lo = Float.MAX_VALUE;
        for (float x : v) { hi = Math.max(hi, x); lo = Math.min(lo, x); }
        float first = v[0], last = v[v.length - 1];
        chartStatLast.setText(PriceChartView.money(last));
        chartStatHigh.setText(PriceChartView.money(hi));
        chartStatLow.setText(PriceChartView.money(lo));
        float pct = first > 0 ? (last - first) / first * 100f : 0f;
        String sign = pct > 0.005f ? "+" : pct < -0.005f ? "−" : "";
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(new java.util.Locale("tr", "TR"));
        nf.setMinimumFractionDigits(1);
        nf.setMaximumFractionDigits(1);
        chartStatChange.setText(sign + "%" + nf.format(Math.abs(pct)) + (pct > 0.005f ? " ▲" : pct < -0.005f ? " ▼" : ""));
        chartStatChange.setTextColor(pct > 0.005f ? green : pct < -0.005f ? 0xFFFF6B63 : text);
    }


    // ============================================================
    // BÖLGELER
    // ============================================================

    private View buildRegionsCard() {

        LinearLayout box =
                cardBox();


        TextView title =
                label(
                        "●  BÖLGELER",
                        12,
                        textSoft,
                        true
                );


        title.setLetterSpacing(
                0.06f
        );


        box.addView(
                title
        );


        regionsContainer =
                column();


        box.addView(
                regionsContainer,
                matchWrap(
                        6,
                        0
                )
        );


        updateRegions(
                state.regions
        );


        return box;
    }


    private void updateRegions(
            List<RegionUiState> regions
    ) {

        if (
                regionsContainer == null
        ) {
            return;
        }


        regionsContainer.removeAllViews();


        if (
                regions == null
                        ||
                        regions.isEmpty()
        ) {

            TextView empty =
                    label(
                            "Bu elek ve dönem için bölgesel gerçek satış verisi yok.",
                            11.5f,
                            muted,
                            false
                    );

            empty.setPadding(
                    0,
                    dp(6),
                    0,
                    dp(2)
            );

            regionsContainer.addView(
                    empty
            );
            return;
        }


        for (
                int i = 0;
                i < regions.size();
                i++
        ) {

            RegionUiState region =
                    regions.get(i);


            regionsContainer.addView(
                    buildRegionRow(
                            region
                    ),
                    matchWrap(
                            i == 0
                                    ? 0
                                    : 6,
                            0
                    )
            );
        }
    }


    private View buildRegionRow(
            RegionUiState region
    ) {

        LinearLayout row =
                new LinearLayout(
                        getContext()
                );


        row.setOrientation(
                LinearLayout.HORIZONTAL
        );


        row.setGravity(
                Gravity.CENTER_VERTICAL
        );


        TextView name =
                label(
                        region == null
                                ? ""
                                : safeText(
                                region.name,
                                ""
                        ),
                        12,
                        text,
                        false
                );


        row.addView(
                name,
                new LinearLayout.LayoutParams(
                        dp(80),
                        dp(27)
                )
        );


        LinearLayout bar =
                new LinearLayout(
                        getContext()
                );


        bar.setOrientation(
                LinearLayout.HORIZONTAL
        );


        bar.setGravity(
                Gravity.CENTER_VERTICAL
        );


        bar.setBackground(
                round(
                        surface,
                        4,
                        border
                )
        );


        bar.setWeightSum(
                1.0f
        );


        float originalRatio =
                region == null
                        ? 0f
                        : region.ratio;


        float clampedRatio =
                Math.max(
                        0.03f,
                        Math.min(
                                1.0f,
                                originalRatio
                        )
                );


        View fill =
                new View(
                        getContext()
                );


        fill.setBackground(
                round(
                        greenMid,
                        4,
                        greenMid
                )
        );


        bar.addView(
                fill,
                new LinearLayout.LayoutParams(
                        0,
                        LayoutParams.MATCH_PARENT,
                        clampedRatio
                )
        );


        View empty =
                new View(
                        getContext()
                );


        bar.addView(
                empty,
                new LinearLayout.LayoutParams(
                        0,
                        LayoutParams.MATCH_PARENT,
                        Math.max(
                                0f,
                                1.0f - clampedRatio
                        )
                )
        );


        LinearLayout.LayoutParams barLp =
                new LinearLayout.LayoutParams(
                        0,
                        dp(8),
                        1f
                );


        barLp.setMargins(
                dp(4),
                0,
                dp(8),
                0
        );


        row.addView(
                bar,
                barLp
        );


        TextView price =
                label(
                        region == null
                                ? "—"
                                : safeText(
                                region.price,
                                "—"
                        ),
                        11.5f,
                        text,
                        true
                );


        price.setGravity(
                Gravity.END
                        |
                        Gravity.CENTER_VERTICAL
        );


        row.addView(
                price,
                new LinearLayout.LayoutParams(
                        dp(68),
                        dp(27)
                )
        );


        return row;
    }


    // ============================================================
    // SENİN SATIŞIN
    // ============================================================

    private View buildOwnSaleCard() {

        LinearLayout box =
                cardBox();


        TextView title =
                label(
                        "●  SENİN SATIŞIN",
                        12,
                        textSoft,
                        true
                );


        title.setLetterSpacing(
                0.06f
        );


        box.addView(
                title
        );


        // ========================================================
        // SON SATIŞ
        // ========================================================

        LinearLayout row1 =
                new LinearLayout(
                        getContext()
                );


        row1.setOrientation(
                LinearLayout.HORIZONTAL
        );


        row1.setGravity(
                Gravity.CENTER_VERTICAL
        );


        row1.setPadding(
                dp(10),
                dp(6),
                dp(10),
                dp(6)
        );


        row1.setBackground(
                round(
                        surface,
                        8,
                        border
                )
        );


        TextView left1 =
                label(
                        "Son satışın",
                        11.5f,
                        textSoft,
                        false
                );


        ownSaleText =
                label(
                        state.ownSale,
                        11.5f,
                        text,
                        true
                );


        ownSaleText.setGravity(
                Gravity.END
                        |
                        Gravity.CENTER_VERTICAL
        );


        row1.addView(
                left1,
                new LinearLayout.LayoutParams(
                        0,
                        LayoutParams.WRAP_CONTENT,
                        1f
                )
        );


        row1.addView(
                ownSaleText
        );


        box.addView(
                row1,
                matchWrap(
                        6,
                        0
                )
        );


        // ========================================================
        // BÖLGE PİYASASI
        // ========================================================

        LinearLayout row2 =
                new LinearLayout(
                        getContext()
                );


        row2.setOrientation(
                LinearLayout.HORIZONTAL
        );


        row2.setGravity(
                Gravity.CENTER_VERTICAL
        );


        row2.setPadding(
                dp(10),
                dp(6),
                dp(10),
                dp(6)
        );


        row2.setBackground(
                round(
                        surface,
                        8,
                        border
                )
        );


        marketRegionLabelText =
                label(
                        safeText(
                                state.selectedRegion,
                                "Bölge"
                        )
                                + " piyasası",
                        11.5f,
                        textSoft,
                        false
                );


        marketSaleText =
                label(
                        state.marketSale,
                        11.5f,
                        text,
                        true
                );


        marketSaleText.setGravity(
                Gravity.END
                        |
                        Gravity.CENTER_VERTICAL
        );


        row2.addView(
                marketRegionLabelText,
                new LinearLayout.LayoutParams(
                        0,
                        LayoutParams.WRAP_CONTENT,
                        1f
                )
        );


        row2.addView(
                marketSaleText
        );


        box.addView(
                row2,
                matchWrap(
                        4,
                        0
                )
        );


        // ========================================================
        // KARŞILAŞTIRMA
        // ========================================================

        comparisonText =
                label(
                        "↗  "
                                + state.ownComparison,
                        11.5f,
                        green,
                        false
                );


        comparisonText.setGravity(
                Gravity.CENTER_VERTICAL
        );


        comparisonText.setPadding(
                dp(10),
                dp(7),
                dp(10),
                dp(7)
        );


        comparisonText.setBackground(
                round(
                        greenSoft,
                        8,
                        green
                )
        );


        box.addView(
                comparisonText,
                matchWrap(
                        6,
                        0
                )
        );


        return box;
    }


    // ============================================================
    // VERİLER NASIL HESAPLANIYOR
    // ============================================================

    private View buildCalculationRow() {

        LinearLayout row =
                new LinearLayout(
                        getContext()
                );


        row.setOrientation(
                LinearLayout.HORIZONTAL
        );


        row.setGravity(
                Gravity.CENTER_VERTICAL
        );


        row.setPadding(
                dp(12),
                dp(10),
                dp(12),
                dp(10)
        );


        row.setBackground(
                round(
                        card,
                        12,
                        border
                )
        );


        row.setOnClickListener(
                v -> {

                    if (
                            listener != null
                    ) {

                        listener.onCalculationInfoClicked();
                    }
                }
        );


        TextView shield =
                label(
                        "✓",
                        12,
                        green,
                        true
                );


        shield.setGravity(
                Gravity.CENTER
        );


        shield.setBackground(
                round(
                        greenSoft,
                        12,
                        green
                )
        );


        row.addView(
                shield,
                new LinearLayout.LayoutParams(
                        dp(24),
                        dp(24)
                )
        );


        TextView title =
                label(
                        "Veriler nasıl hesaplanıyor?",
                        12,
                        textSoft,
                        false
                );


        LinearLayout.LayoutParams titleLp =
                new LinearLayout.LayoutParams(
                        0,
                        LayoutParams.WRAP_CONTENT,
                        1f
                );


        titleLp.leftMargin =
                dp(10);


        row.addView(
                title,
                titleLp
        );


        TextView arrow =
                label(
                        "›",
                        18,
                        muted,
                        false
                );


        arrow.setGravity(
                Gravity.CENTER
        );


        row.addView(
                arrow,
                new LinearLayout.LayoutParams(
                        dp(20),
                        LayoutParams.WRAP_CONTENT
                )
        );


        return row;
    }


    // ============================================================
    // CARD
    // ============================================================

    private LinearLayout cardBox() {

        LinearLayout box =
                column();


        box.setPadding(
                dp(12),
                dp(10),
                dp(12),
                dp(10)
        );


        box.setBackground(
                round(
                        card,
                        13,
                        border
                )
        );


        return box;
    }


    // ============================================================
    // UI HELPERS
    // ============================================================

    private LinearLayout column() {

        LinearLayout layout =
                new LinearLayout(
                        getContext()
                );


        layout.setOrientation(
                LinearLayout.VERTICAL
        );


        return layout;
    }


    private TextView label(
            String value,
            float spSize,
            int color,
            boolean bold
    ) {

        TextView view =
                new TextView(
                        getContext()
                );


        view.setText(
                value == null
                        ? ""
                        : value
        );


        view.setTextSize(
                spSize
        );


        view.setTextColor(
                color
        );


        view.setIncludeFontPadding(
                false
        );


        view.setTypeface(
                Typeface.DEFAULT,
                bold
                        ? Typeface.BOLD
                        : Typeface.NORMAL
        );


        view.setGravity(
                Gravity.CENTER_VERTICAL
        );


        return view;
    }


    private GradientDrawable round(
            int fill,
            float radiusDp,
            int stroke
    ) {

        GradientDrawable drawable =
                new GradientDrawable();


        drawable.setColor(
                fill
        );


        drawable.setCornerRadius(
                dp(
                        radiusDp
                )
        );


        drawable.setStroke(
                dp(1),
                stroke
        );


        return drawable;
    }


    private GradientDrawable gradientRound(
            int startColor,
            int endColor,
            float radiusDp,
            int stroke
    ) {

        GradientDrawable drawable =
                new GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{
                                startColor,
                                endColor
                        }
                );


        drawable.setCornerRadius(
                dp(
                        radiusDp
                )
        );


        drawable.setStroke(
                dp(1),
                stroke
        );


        return drawable;
    }


    private LinearLayout.LayoutParams matchWrap(
            int topDp,
            int bottomDp
    ) {

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT
                );


        lp.topMargin =
                dp(
                        topDp
                );


        lp.bottomMargin =
                dp(
                        bottomDp
                );


        return lp;
    }


    private int dp(
            float value
    ) {

        return Math.round(
                value
                        *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }


    private static String safeText(
            String value,
            String fallback
    ) {

        if (
                value == null
                        ||
                        value.trim().isEmpty()
        ) {

            return fallback == null
                    ? ""
                    : fallback;
        }


        return value.trim();
    }


    // ============================================================
    // GRADE CHIP
    // ============================================================

    private static final class GradeChip {

        final String grade;

        final TextView view;


        GradeChip(
                String grade,
                TextView view
        ) {

            this.grade =
                    grade;

            this.view =
                    view;
        }
    }


    // ============================================================
    // BÖLGE STATE
    // ============================================================

    public static final class RegionUiState {

        public final String name;

        public final String price;

        public final float ratio;


        public RegionUiState(
                String name,
                String price,
                float ratio
        ) {

            this.name =
                    name;

            this.price =
                    price;

            this.ratio =
                    ratio;
        }
    }


    // ============================================================
    // MARKET STATE
    // ============================================================

    public static final class MarketUiState {

        public List<String> grades;

        public String grade;

        public String selectedRegion;

        public String selectedPeriod;

        public String medianPrice;

        public String range;

        public String change;

        public String salesCount;

        public String totalKg;

        public String dataBadge;

        public float[] chartValues;

        public String chartStartDate;

        public String chartEndDate;

        /** Her grafik noktasının tarihi ("19 Eki"); dokununca ve alt eksende gösterilir. Boş olabilir. */
        public String[] chartLabels = new String[0];

        /** Grafiğin kaynağı ("Senin elek fiyatların" gibi); boşsa yazılmaz. */
        public String chartNote = "";

        /** Sayısal değerler (rozet, aralık çubuğu, "senin fiyatın" satırı için); 0 = yok. */
        public double medianValue, previousMedianValue, rangeLowValue, rangeHighValue, ownPriceValue;

        public List<RegionUiState> regions;

        public String ownSale;

        public String marketSale;

        public String ownComparison;

        /** Filtre öncesi geçerli fiyat satırı/kayıt sayısı. */
        public int rawRecordCount;

        /** Güven filtresinin piyasa hesabından çıkardığı aykırı kayıt sayısı. */
        public int outlierCount;

        /** Filtre sonrası piyasa hesabına katılan kayıt sayısı. */
        public int acceptedRecordCount;

        /** Aynı uzunluktaki önceki dönemde hesaplanan kayıt sayısı. */
        public int previousRecordCount;

        /** Önceki dönemin medyanı; veri yoksa —. */
        public String previousMedianPrice;

        /** 0-100 arasında veri kalitesi puanı. */
        public int confidenceScore;

        /** Puanın güncellik / yayılım / aykırı oranı özeti. */
        public String confidenceDetails;


        public MarketUiState(
                List<String> grades,
                String grade,
                String selectedRegion,
                String selectedPeriod,
                String medianPrice,
                String range,
                String change,
                String salesCount,
                String totalKg,
                String dataBadge,
                float[] chartValues,
                String chartStartDate,
                String chartEndDate,
                List<RegionUiState> regions,
                String ownSale,
                String marketSale,
                String ownComparison,
                int rawRecordCount,
                int outlierCount,
                int acceptedRecordCount,
                int previousRecordCount,
                String previousMedianPrice,
                int confidenceScore,
                String confidenceDetails
        ) {

            this.grades =
                    grades;

            this.grade =
                    grade;

            this.selectedRegion =
                    selectedRegion;

            this.selectedPeriod =
                    selectedPeriod;

            this.medianPrice =
                    medianPrice;

            this.range =
                    range;

            this.change =
                    change;

            this.salesCount =
                    salesCount;

            this.totalKg =
                    totalKg;

            this.dataBadge =
                    dataBadge;

            this.chartValues =
                    chartValues;

            this.chartStartDate =
                    chartStartDate;

            this.chartEndDate =
                    chartEndDate;

            this.regions =
                    regions;

            this.ownSale =
                    ownSale;

            this.marketSale =
                    marketSale;

            this.ownComparison =
                    ownComparison;

            this.rawRecordCount =
                    Math.max(0, rawRecordCount);

            this.outlierCount =
                    Math.max(0, outlierCount);

            this.acceptedRecordCount =
                    Math.max(0, acceptedRecordCount);

            this.previousRecordCount =
                    Math.max(0, previousRecordCount);

            this.previousMedianPrice =
                    previousMedianPrice == null
                            ? "—"
                            : previousMedianPrice;

            this.confidenceScore =
                    Math.max(0, Math.min(100, confidenceScore));

            this.confidenceDetails =
                    confidenceDetails == null
                            ? ""
                            : confidenceDetails;
        }


        public static MarketUiState demo() {

            return new MarketUiState(

                    Arrays.asList(
                            "Double",
                            "1 No",
                            "2 No",
                            "3 No",
                            "4 No",
                            "Yağlık"
                    ),

                    "2 No",

                    "Alaşehir",

                    "Son 30 Gün",

                    "80,00 TL",

                    "77 TL — 84 TL",

                    "+2,00 TL • önceki 30 güne göre +%2,56",

                    "34 kayıt",

                    "18.420 kg",

                    "Güvenilir Veri",

                    new float[]{
                            78.0f,
                            78.5f,
                            78.4f,
                            79.0f,
                            78.9f,
                            79.8f,
                            79.5f,
                            80.2f,
                            80.0f,
                            80.7f,
                            81.0f,
                            80.8f,
                            81.5f,
                            81.3f,
                            82.1f,
                            81.9f,
                            82.8f,
                            82.6f,
                            83.2f,
                            83.8f,
                            84.0f
                    },

                    "1 Eyl",

                    "Bugün",

                    Arrays.asList(

                            new RegionUiState(
                                    "Akhisar",
                                    "82,50 TL",
                                    0.78f
                            ),

                            new RegionUiState(
                                    "Alaşehir",
                                    "80,00 TL",
                                    0.62f
                            ),

                            new RegionUiState(
                                    "Salihli",
                                    "79,00 TL",
                                    0.48f
                            )
                    ),

                    "81 TL/kg",

                    "80 TL/kg",

                    "Piyasanın 1 TL üzerinde satmışsın.",

                    34,

                    0,

                    34,

                    22,

                    "78,00 TL",

                    88,

                    "Puan 88/100 • son kayıt bugün • fiyat yayılımı %6,0 • aykırı oranı %0,0"
            );
        }
    }


    // ============================================================
    // FİYAT GRAFİĞİ
    // ============================================================

    /**
     * Fiyat değişimi grafiği (borsa uygulamalarındaki gibi, yeşil tema): çizgi + altında yeşil gölge,
     * solda en yüksek / en düşük değer etiketi, son fiyatta kesikli yatay çizgi, uçta parlayan nokta,
     * altta tarih ekseni (baş · orta · son). Parmakla dokunup sürükleyince o günün fiyatı ve tarihi görünür.
     */
    public static final class PriceChartView extends View {
        private float[] values = new float[0];
        private String[] labels = new String[0];
        private String startDate = "", endDate = "";
        private final int lineColor, gridColor, mutedColor, textColor;
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pillTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path linePath = new Path();
        private final Path fillPath = new Path();
        private final android.graphics.RectF rect = new android.graphics.RectF();
        private final float density, scaled;
        private int touched = -1;
        private int gradientHeight = -1;
        /** Açılışta çizginin soldan sağa çizilmesi (0 → 1). */
        private float progress = 1f;
        private android.animation.ValueAnimator animator;
        private int lastSignature;

        public PriceChartView(Context context, float[] values, String startDate, String endDate,
                              int lineColor, int gridColor, int mutedColor, int textColor) {
            super(context);
            density = getResources().getDisplayMetrics().density;
            scaled = getResources().getDisplayMetrics().scaledDensity;
            this.lineColor = lineColor;
            this.gridColor = gridColor;
            this.mutedColor = mutedColor;
            this.textColor = textColor;
            setWillNotDraw(false);
            linePaint.setColor(lineColor);
            linePaint.setStrokeWidth(dpLocal(2.2f));
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeCap(Paint.Cap.ROUND);
            linePaint.setStrokeJoin(Paint.Join.ROUND);
            fillPaint.setStyle(Paint.Style.FILL);
            gridPaint.setColor(gridColor);
            gridPaint.setAlpha(70);
            gridPaint.setStrokeWidth(dpLocal(0.8f));
            dashPaint.setColor(mutedColor);
            dashPaint.setAlpha(150);
            dashPaint.setStrokeWidth(dpLocal(1f));
            dashPaint.setStyle(Paint.Style.STROKE);
            dashPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{dpLocal(5), dpLocal(4)}, 0));
            dotPaint.setColor(lineColor);
            haloPaint.setColor(lineColor);
            haloPaint.setAlpha(60);
            axisPaint.setColor(mutedColor);
            axisPaint.setTextSize(spLocal(10.5f));
            pillPaint.setColor(gridColor);
            pillPaint.setAlpha(150);
            pillTextPaint.setColor(textColor);
            pillTextPaint.setAlpha(210);
            pillTextPaint.setTextSize(spLocal(10.5f));
            cursorPaint.setColor(textColor);
            cursorPaint.setAlpha(120);
            cursorPaint.setStrokeWidth(dpLocal(1f));
            setData(values, startDate, endDate);
        }

        public void setData(float[] newValues, String startDate, String endDate) {
            values = newValues == null ? new float[0] : newValues.clone();
            this.startDate = startDate == null ? "" : startDate;
            this.endDate = endDate == null ? "" : endDate;
            touched = -1;
            int signature = java.util.Arrays.hashCode(values);
            if (signature != lastSignature && values.length >= 2) {
                lastSignature = signature;
                if (animator != null) animator.cancel();
                animator = android.animation.ValueAnimator.ofFloat(0f, 1f);
                animator.setDuration(650);
                animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
                animator.addUpdateListener(a -> { progress = (float) a.getAnimatedValue(); invalidate(); });
                progress = 0f;
                animator.start();
            }
            StringBuilder d = new StringBuilder("Fiyat grafiği");
            if (values.length >= 2) {
                d.append(", ").append(this.startDate).append(" ").append(money(values[0]))
                        .append(", ").append(this.endDate).append(" ").append(money(values[values.length - 1]));
            }
            setContentDescription(d.toString());
            invalidate();
        }

        /** Her noktanın tarihi (yoksa yalnız baş/son tarih yazılır). */
        public void setLabels(String[] newLabels) {
            labels = newLabels == null ? new String[0] : newLabels.clone();
            invalidate();
        }

        private String label(int i) {
            if (i >= 0 && i < labels.length && labels[i] != null && !labels[i].isEmpty()) return labels[i];
            if (i == 0) return startDate;
            if (i == values.length - 1) return endDate;
            return "";
        }

        static String money(float v) {
            java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(new java.util.Locale("tr", "TR"));
            nf.setMinimumFractionDigits(2);
            nf.setMaximumFractionDigits(2);
            return nf.format(v);
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent e) {
            if (values.length < 2) return super.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                case android.view.MotionEvent.ACTION_MOVE:
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    float left = dpLocal(4), right = getWidth() - dpLocal(14);
                    float t = (e.getX() - left) / Math.max(1f, right - left);
                    int i = Math.round(Math.max(0f, Math.min(1f, t)) * (values.length - 1));
                    if (i != touched) {
                        touched = i;
                        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                        announceForAccessibility(label(i) + " " + money(values[i]) + " lira");
                        invalidate();
                    }
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                    postDelayed(() -> { touched = -1; invalidate(); }, 1600);
                    return true;
                default:
                    return super.onTouchEvent(e);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int n = values.length;
            if (n < 2) return;
            float w = getWidth(), h = getHeight();
            float left = dpLocal(4), right = w - dpLocal(14);
            // Üstte en yüksek, altta en düşük fiyat etiketi için yer; çizgiyle çakışmaz.
            float axisH = dpLocal(22), base = h - axisH;
            float top = dpLocal(26), bottom = base - dpLocal(24);
            float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
            int maxI = 0, minI = 0;
            for (int i = 0; i < n; i++) {
                if (values[i] > max) { max = values[i]; maxI = i; }
                if (values[i] < min) { min = values[i]; minI = i; }
            }
            float span = max - min;
            if (span < 0.01f) span = Math.max(1f, Math.abs(max) * 0.05f);
            float lo = min - span * 0.04f, hi = max + span * 0.04f;
            float[] xs = new float[n], ys = new float[n];
            for (int i = 0; i < n; i++) {
                xs[i] = left + (right - left) * i / (n - 1f);
                ys[i] = bottom - (values[i] - lo) / (hi - lo) * (bottom - top);
            }
            // Izgara: 3 ince yatay çizgi.
            for (int g = 0; g < 3; g++) {
                float y = top + (base - top) * g / 2f;
                canvas.drawLine(left, y, right, y, gridPaint);
            }
            // Çizginin altı: yeşilden şeffafa gölge.
            if (gradientHeight != getHeight()) {
                gradientHeight = getHeight();
                fillPaint.setShader(new android.graphics.LinearGradient(0, top, 0, base,
                        (lineColor & 0x00FFFFFF) | 0x55000000, lineColor & 0x00FFFFFF,
                        android.graphics.Shader.TileMode.CLAMP));
            }
            // Yumuşak eğri: noktalar arası orta noktalardan geçen kavis (en yüksek/en düşüğü aşmaz).
            linePath.reset();
            fillPath.reset();
            linePath.moveTo(xs[0], ys[0]);
            fillPath.moveTo(xs[0], base);
            fillPath.lineTo(xs[0], ys[0]);
            for (int i = 1; i < n; i++) {
                float mx = (xs[i - 1] + xs[i]) / 2f, my = (ys[i - 1] + ys[i]) / 2f;
                if (i == 1) { linePath.lineTo(mx, my); fillPath.lineTo(mx, my); }
                else { linePath.quadTo(xs[i - 1], ys[i - 1], mx, my); fillPath.quadTo(xs[i - 1], ys[i - 1], mx, my); }
            }
            linePath.lineTo(xs[n - 1], ys[n - 1]);
            fillPath.lineTo(xs[n - 1], ys[n - 1]);
            fillPath.lineTo(xs[n - 1], base);
            fillPath.close();
            // Dönem başındaki fiyat: kesikli çizgi (borsa uygulamalarındaki "dünkü kapanış" gibi).
            float firstY = ys[0], lastY = ys[n - 1];
            if (Math.abs(firstY - lastY) > dpLocal(3)) canvas.drawLine(left, firstY, right, firstY, dashPaint);
            int save = canvas.save();
            canvas.clipRect(0, 0, left + (right - left) * progress + dpLocal(2), h);
            canvas.drawPath(fillPath, fillPaint);
            canvas.drawPath(linePath, linePaint);
            canvas.restoreToCount(save);
            // En yüksek ve en düşük: solda küçük etiket.
            drawPill(canvas, money(max), left, ys[maxI], true, 0, base);
            if (maxI != minI && max - min >= 0.01f) drawPill(canvas, money(min), left, ys[minI], false, 0, base);
            // Uçta parlayan nokta (çizim bitince).
            if (progress >= 0.999f) {
                canvas.drawCircle(xs[n - 1], lastY, dpLocal(9), haloPaint);
                canvas.drawCircle(xs[n - 1], lastY, dpLocal(4.2f), dotPaint);
            }
            // Tarih ekseni: baş · orta · son.
            float ay = h - dpLocal(6);
            axisPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(label(0), left, ay, axisPaint);
            if (n >= 5) {
                int mid = (n - 1) / 2;
                axisPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(label(mid), xs[mid], ay, axisPaint);
            }
            axisPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(label(n - 1), right + dpLocal(10), ay, axisPaint);
            // Dokunulan gün: dikey çizgi + baloncuk.
            if (touched >= 0 && touched < n) {
                float x = xs[touched], y = ys[touched];
                canvas.drawLine(x, top - dpLocal(6), x, base, cursorPaint);
                canvas.drawCircle(x, y, dpLocal(8), haloPaint);
                canvas.drawCircle(x, y, dpLocal(4.2f), dotPaint);
                String tip = label(touched) + "  ·  " + money(values[touched]) + " TL";
                float tw = pillTextPaint.measureText(tip) + dpLocal(16);
                float bx = Math.max(0, Math.min(w - tw, x - tw / 2f));
                float by = Math.max(0, y - dpLocal(38));
                rect.set(bx, by, bx + tw, by + dpLocal(24));
                Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
                bg.setColor(lineColor);
                canvas.drawRoundRect(rect, dpLocal(12), dpLocal(12), bg);
                Paint tp = new Paint(pillTextPaint);
                tp.setColor(0xFF0B1A12);
                tp.setAlpha(255);
                tp.setFakeBoldText(true);
                tp.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(tip, rect.centerX(), rect.centerY() + tp.getTextSize() / 3f, tp);
            }
        }

        private void drawPill(Canvas canvas, String text, float x, float y, boolean above, float top, float bottom) {
            float tw = pillTextPaint.measureText(text) + dpLocal(12);
            float ph = dpLocal(19);
            float py = above ? y - ph - dpLocal(4) : y + dpLocal(4);
            py = Math.max(top, Math.min(bottom - ph, py));
            rect.set(x, py, x + tw, py + ph);
            canvas.drawRoundRect(rect, dpLocal(6), dpLocal(6), pillPaint);
            pillTextPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(text, rect.centerX(), rect.centerY() + pillTextPaint.getTextSize() / 3f, pillTextPaint);
        }

        private float dpLocal(float v) { return v * density; }

        private float spLocal(float v) { return v * scaled; }
    }

    /**
     * Fiyat aralığı çubuğu: en düşük — en yüksek; ortadaki fiyat beyaz çizgi, senin son satışın sarı nokta.
     * Altında uçlarda en düşük ve en yüksek fiyat yazılır.
     */
    public static final class RangeBarView extends View {
        private double low, high, median, own;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG),
                tick = new Paint(Paint.ANTI_ALIAS_FLAG), ownDot = new Paint(Paint.ANTI_ALIAS_FLAG),
                ownHalo = new Paint(Paint.ANTI_ALIAS_FLAG), labels = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int green;
        private final float density;

        public RangeBarView(Context c, int green, int border, int text, int gold) {
            super(c);
            this.green = green;
            density = getResources().getDisplayMetrics().density;
            track.setColor(border);
            tick.setColor(text);
            tick.setStrokeWidth(2 * density);
            ownDot.setColor(gold);
            ownHalo.setColor(gold);
            ownHalo.setAlpha(70);
            labels.setColor(text);
            labels.setAlpha(170);
            labels.setTextSize(10.5f * getResources().getDisplayMetrics().scaledDensity);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        public void setValues(double low, double high, double median, double own) {
            this.low = low; this.high = high; this.median = median; this.own = own;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (high <= 0) return;
            float w = getWidth(), pad = 9 * density, y = 10 * density, r = 3 * density;
            double lo = Math.min(low, own > 0 ? own : low), hi = Math.max(high, own);
            double span = Math.max(0.01, hi - lo);
            java.util.function.DoubleUnaryOperator x = v -> pad + (w - 2 * pad) * (v - lo) / span;
            android.graphics.RectF rect = new android.graphics.RectF(pad, y - r, w - pad, y + r);
            canvas.drawRoundRect(rect, r, r, track);
            if (fill.getShader() == null || getTag() == null || !getTag().equals(getWidth())) {
                setTag(getWidth());
                fill.setShader(new android.graphics.LinearGradient(pad, 0, w - pad, 0,
                        (green & 0x00FFFFFF) | 0x66000000, green, android.graphics.Shader.TileMode.CLAMP));
            }
            rect.set((float) x.applyAsDouble(low), y - r, (float) x.applyAsDouble(high), y + r);
            canvas.drawRoundRect(rect, r, r, fill);
            if (median > 0) {
                float mx = (float) x.applyAsDouble(median);
                canvas.drawLine(mx, y - 6 * density, mx, y + 6 * density, tick);
            }
            if (own > 0) {
                float ox = (float) x.applyAsDouble(own);
                canvas.drawCircle(ox, y, 8 * density, ownHalo);
                canvas.drawCircle(ox, y, 4.5f * density, ownDot);
            }
            float ty = getHeight() - 1 * density;
            labels.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("en düşük " + PriceChartView.money((float) low), pad, ty, labels);
            labels.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("en yüksek " + PriceChartView.money((float) high), w - pad, ty, labels);
        }
    }
}
