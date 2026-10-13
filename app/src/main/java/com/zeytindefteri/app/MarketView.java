package com.zeytindefteri.app;

import android.content.Context;
import android.graphics.Canvas;
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
        // FOTOĞRAF
        //
        // Parent artık initLayout içinde TAM 190dp.
        // Bu MATCH_PARENT artık ekranı büyütemez.
        // ========================================================

        ImageView photo =
                new ImageView(
                        getContext()
                );


        photo.setImageResource(
                R.drawable.home_hero_olive
        );


        photo.setScaleType(
                ImageView.ScaleType.CENTER_CROP
        );


        photo.setAlpha(
                0.40f
        );


        photo.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO
        );


        hero.addView(
                photo,
                new FrameLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT
                )
        );


        // ========================================================
        // KOYU GRADIENT
        // ========================================================

        View shade =
                new View(
                        getContext()
                );


        GradientDrawable shadeDrawable =
                new GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{
                                GreenTheme.parse(
                                        "#FC0B1D28"
                                ),
                                GreenTheme.parse(
                                        "#E80B1D28"
                                ),
                                GreenTheme.parse(
                                        "#A20D2631"
                                ),
                                GreenTheme.parse(
                                        "#55132A38"
                                )
                        }
                );


        shade.setBackground(
                shadeDrawable
        );


        hero.addView(
                shade,
                new FrameLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT
                )
        );


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


        content.addView(
                priceText,
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
                        "Medyan fiyat",
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


        chartPeriodText =
                label(
                        state.selectedPeriod
                                + "  ⌄",
                        10.5f,
                        textSoft,
                        false
                );


        chartPeriodText.setGravity(
                Gravity.CENTER
        );


        chartPeriodText.setPadding(
                dp(8),
                0,
                dp(8),
                0
        );


        chartPeriodText.setBackground(
                round(
                        surface,
                        14,
                        border
                )
        );


        chartPeriodText.setOnClickListener(
                v -> {

                    if (
                            listener != null
                    ) {

                        listener.onPeriodFilterClicked();
                    }
                }
        );


        heading.addView(
                chartPeriodText,
                new LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        dp(28)
                )
        );


        box.addView(
                heading
        );


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
                        dp(135)
                );


        chartLp.topMargin =
                dp(6);


        box.addView(
                chartView,
                chartLp
        );


        chartEmptyText.setVisibility(
                View.GONE
        );


        return box;
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

    public static final class PriceChartView extends View {

        private float[] values;

        private String startDate =
                "";

        private String endDate =
                "";


        private final Paint gridPaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );


        private final Paint linePaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );


        private final Paint fillPaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );


        private final Paint dotPaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );


        private final Paint textPaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );


        private final Paint endTextPaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );


        private final Path linePath =
                new Path();


        private final Path fillPath =
                new Path();


        private final float density;


        public PriceChartView(
                Context context,
                float[] values,
                String startDate,
                String endDate,
                int lineColor,
                int gridColor,
                int mutedColor,
                int textColor
        ) {

            super(
                    context
            );


            this.density =
                    getResources()
                            .getDisplayMetrics()
                            .density;


            this.values =
                    values == null
                            ?
                            new float[0]
                            :
                            values.clone();


            this.startDate =
                    startDate == null
                            ? ""
                            : startDate;


            this.endDate =
                    endDate == null
                            ? ""
                            : endDate;


            setWillNotDraw(
                    false
            );


            gridPaint.setColor(
                    gridColor
            );


            gridPaint.setStrokeWidth(
                    dpLocal(
                            0.8f
                    )
            );


            gridPaint.setStyle(
                    Paint.Style.STROKE
            );


            gridPaint.setAlpha(
                    115
            );


            linePaint.setColor(
                    lineColor
            );


            linePaint.setStrokeWidth(
                    dpLocal(
                            2f
                    )
            );


            linePaint.setStrokeCap(
                    Paint.Cap.ROUND
            );


            linePaint.setStrokeJoin(
                    Paint.Join.ROUND
            );


            linePaint.setStyle(
                    Paint.Style.STROKE
            );


            fillPaint.setColor(
                    lineColor
            );


            fillPaint.setAlpha(
                    25
            );


            fillPaint.setStyle(
                    Paint.Style.FILL
            );


            dotPaint.setColor(
                    lineColor
            );


            dotPaint.setStyle(
                    Paint.Style.FILL
            );


            textPaint.setColor(
                    mutedColor
            );


            textPaint.setTextSize(
                    spLocal(
                            8.5f
                    )
            );


            textPaint.setTextAlign(
                    Paint.Align.RIGHT
            );


            endTextPaint.setColor(
                    textColor
            );


            endTextPaint.setTextSize(
                    spLocal(
                            8.5f
                    )
            );


            endTextPaint.setTextAlign(
                    Paint.Align.CENTER
            );
        }


        // ========================================================
        // DATA
        // ========================================================

        public void setData(
                float[] newValues,
                String startDate,
                String endDate
        ) {

            this.values =
                    newValues == null
                            ?
                            new float[0]
                            :
                            newValues.clone();


            this.startDate =
                    startDate == null
                            ? ""
                            : startDate;


            this.endDate =
                    endDate == null
                            ? ""
                            : endDate;


            invalidate();
        }


        // ========================================================
        // DRAW
        // ========================================================

        @Override
        protected void onDraw(
                Canvas canvas
        ) {

            super.onDraw(
                    canvas
            );


            if (
                    values == null
                            ||
                            values.length < 1
                            ||
                            getWidth() <= 0
                            ||
                            getHeight() <= 0
            ) {

                return;
            }


            float left =
                    dpLocal(
                            30
                    );


            float right =
                    getWidth()
                            -
                            dpLocal(
                                    8
                            );


            float top =
                    dpLocal(
                            8
                    );


            float bottom =
                    getHeight()
                            -
                            dpLocal(
                                    20
                            );


            // ====================================================
            // DİNAMİK MIN / MAX
            // ====================================================

            float minVal =
                    Float.MAX_VALUE;


            float maxVal =
                    -Float.MAX_VALUE;


            for (
                    float value :
                    values
            ) {

                if (
                        value < minVal
                ) {

                    minVal =
                            value;
                }


                if (
                        value > maxVal
                ) {

                    maxVal =
                            value;
                }
            }


            if (
                    minVal == maxVal
            ) {

                minVal -=
                        1f;


                maxVal +=
                        1f;

            } else {

                float buffer =
                        (
                                maxVal
                                        -
                                        minVal
                        )
                                *
                                0.12f;


                minVal -=
                        buffer;


                maxVal +=
                        buffer;
            }


            // ====================================================
            // GRID
            // ====================================================

            int tickCount =
                    4;


            for (
                    int i = 0;
                    i < tickCount;
                    i++
            ) {

                float tickValue =
                        minVal
                                +
                                (
                                        maxVal
                                                -
                                                minVal
                                )
                                        *
                                        (
                                                i
                                                        /
                                                        (
                                                                float
                                                                        )
                                                                (
                                                                        tickCount
                                                                                -
                                                                                1
                                                                )
                                        );


                float y =
                        mapY(
                                tickValue,
                                minVal,
                                maxVal,
                                top,
                                bottom
                        );


                canvas.drawLine(
                        left,
                        y,
                        right,
                        y,
                        gridPaint
                );


                canvas.drawText(
                        formatTick(
                                tickValue,
                                maxVal - minVal
                        ),
                        left
                                -
                                dpLocal(
                                        4
                                ),
                        y
                                +
                                dpLocal(
                                        3
                                ),
                        textPaint
                );
            }


            // ====================================================
            // TEK SATIŞ VARSA TEK NOKTA GÖSTER
            // ====================================================

            if (
                    values.length == 1
            ) {

                float singleX =
                        (left + right) / 2f;

                float singleY =
                        mapY(
                                values[0],
                                minVal,
                                maxVal,
                                top,
                                bottom
                        );

                canvas.drawCircle(
                        singleX,
                        singleY,
                        dpLocal(3.5f),
                        dotPaint
                );

                if (
                        !startDate.isEmpty()
                ) {

                    canvas.drawText(
                            startDate,
                            singleX,
                            getHeight()
                                    -
                                    dpLocal(3),
                            endTextPaint
                    );
                }

                return;
            }


            // ====================================================
            // ÇİZGİ + DOLGU
            // ====================================================

            float step =
                    (
                            right
                                    -
                                    left
                    )
                            /
                            (
                                    values.length
                                            -
                                            1f
                            );


            linePath.reset();

            fillPath.reset();


            for (
                    int i = 0;
                    i < values.length;
                    i++
            ) {

                float x =
                        left
                                +
                                (
                                        step
                                                *
                                                i
                                );


                float y =
                        mapY(
                                values[i],
                                minVal,
                                maxVal,
                                top,
                                bottom
                        );


                if (
                        i == 0
                ) {

                    linePath.moveTo(
                            x,
                            y
                    );


                    fillPath.moveTo(
                            x,
                            bottom
                    );


                    fillPath.lineTo(
                            x,
                            y
                    );

                } else {

                    linePath.lineTo(
                            x,
                            y
                    );


                    fillPath.lineTo(
                            x,
                            y
                    );
                }
            }


            fillPath.lineTo(
                    right,
                    bottom
            );


            fillPath.close();


            canvas.drawPath(
                    fillPath,
                    fillPaint
            );


            canvas.drawPath(
                    linePath,
                    linePaint
            );


            // ====================================================
            // SON NOKTA
            // ====================================================

            float lastX =
                    right;


            float lastY =
                    mapY(
                            values[
                                    values.length
                                            -
                                            1
                                    ],
                            minVal,
                            maxVal,
                            top,
                            bottom
                    );


            canvas.drawCircle(
                    lastX,
                    lastY,
                    dpLocal(
                            3.5f
                    ),
                    dotPaint
            );


            // ====================================================
            // TARİHLER
            // ====================================================

            if (
                    !startDate.isEmpty()
            ) {

                canvas.drawText(
                        startDate,
                        left
                                +
                                dpLocal(
                                        10
                                ),
                        getHeight()
                                -
                                dpLocal(
                                        3
                                ),
                        endTextPaint
                );
            }


            if (
                    !endDate.isEmpty()
            ) {

                canvas.drawText(
                        endDate,
                        right
                                -
                                dpLocal(
                                        15
                                ),
                        getHeight()
                                -
                                dpLocal(
                                        3
                                ),
                        endTextPaint
                );
            }
        }


        // ========================================================
        // TICK FORMAT
        // ========================================================

        private String formatTick(
                float value,
                float range
        ) {

            if (
                    range < 4f
            ) {

                return String.format(
                        java.util.Locale.US,
                        "%.1f",
                        value
                );
            }


            return String.valueOf(
                    Math.round(
                            value
                    )
            );
        }


        // ========================================================
        // MAP Y
        // ========================================================

        private float mapY(
                float value,
                float min,
                float max,
                float top,
                float bottom
        ) {

            if (
                    max == min
            ) {

                return (
                        bottom
                                +
                                top
                )
                        /
                        2f;
            }


            float normalized =
                    (
                            value
                                    -
                                    min
                    )
                            /
                            (
                                    max
                                            -
                                            min
                            );


            normalized =
                    Math.max(
                            0f,
                            Math.min(
                                    1f,
                                    normalized
                            )
                    );


            return bottom
                    -
                    (
                            (
                                    bottom
                                            -
                                            top
                            )
                                    *
                                    normalized
                    );
        }


        private float dpLocal(
                float value
        ) {

            return value
                    *
                    density;
        }


        private float spLocal(
                float value
        ) {

            return value
                    *
                    getResources()
                            .getDisplayMetrics()
                            .scaledDensity;
        }
    }
}