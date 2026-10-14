package com.zeytindefteri.app;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.Locale;

public class ReportActivity extends Activity {

    private DatabaseHelper databaseHelper;
    private final java.util.concurrent.ExecutorService analyticsWorker =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private int analyticsGeneration;


    private final Locale TR =
            new Locale("tr", "TR");


    // ============================================================
    // VIEW'LAR
    // ============================================================

    private TextView tvTotalExpense;
    private TextView tvTotalHarvest;
    private TextView tvSalesRevenue;
    private TextView tvNetProfit;

    private TextView tvSoldKg;
    private TextView tvSaleCount;
    private TextView tvCollected;
    private TextView tvReceivable;

    private TextView tvBrineOlive;
    private TextView tvReadyBrine;
    private TextView tvBrineCost;
    private TextView tvActiveBrine;

    private LinearLayout containerGardens;


    // ============================================================
    // RENKLER
    // ============================================================

    private final int COLOR_BACKGROUND =
            GreenTheme.color(R.color.zd_bg);

    private final int COLOR_CARD =
            GreenTheme.color(R.color.zd_card);

    private final int COLOR_DARK_GREEN =
            GreenTheme.color(R.color.zd_text);

    private final int COLOR_GREEN =
            GreenTheme.color(R.color.zd_green); // eski koyu zeytin yeşili koyu zeminde okunmuyordu

    private final int COLOR_LIGHT_GREEN =
            GreenTheme.color(R.color.zd_green_soft);

    private final int COLOR_WHITE =
            Color.WHITE;

    private final int COLOR_TEXT =
            GreenTheme.color(R.color.zd_text);

    private final int COLOR_GRAY =
            GreenTheme.color(R.color.zd_muted);

    private final int COLOR_BORDER =
            GreenTheme.color(R.color.zd_border);

    private final int COLOR_RED =
            GreenTheme.color(R.color.zd_danger);


    // ============================================================
    // ON CREATE
    // ============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        if (getActionBar() != null) {
            getActionBar().hide();
        }
        getWindow().setStatusBarColor(COLOR_BACKGROUND);
        getWindow().setNavigationBarColor(COLOR_BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(0);

        databaseHelper =
                new DatabaseHelper(this);

        setContentView(
                createScreen()
        );

    }


    // ============================================================
    // GERİ DÖNÜNCE YENİLE
    // ============================================================

    @Override
    protected void onResume() {

        super.onResume();

        if (databaseHelper != null
                && containerGardens != null) {

            refreshReport();
        }
    }


    // ============================================================
    // EKRAN
    // ============================================================

    private View createScreen() {

        ScrollView scrollView =
                new ScrollView(this);

        scrollView.setFillViewport(true);

        scrollView.setBackgroundColor(
                COLOR_BACKGROUND
        );


        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setPadding(
                dp(22),
                dp(24),
                dp(22),
                dp(50)
        );


        scrollView.addView(
                root,
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT
                )
        );


        // ========================================================
        // HEADER
        // ========================================================

        LinearLayout header =
                new LinearLayout(this);

        header.setOrientation(
                LinearLayout.HORIZONTAL
        );

        header.setGravity(
                Gravity.CENTER_VERTICAL
        );


        TextView btnBack =
                createText(
                        "‹",
                        32,
                        COLOR_DARK_GREEN,
                        true
                );

        btnBack.setGravity(
                Gravity.CENTER
        );

        btnBack.setBackground(
                roundedBackground(
                        GreenTheme.color(R.color.zd_surface),
                        100,
                        COLOR_BORDER
                )
        );

        btnBack.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        finish();
                    }
                }
        );


        header.addView(
                btnBack,
                new LinearLayout.LayoutParams(
                        dp(46),
                        dp(46)
                )
        );


        LinearLayout titleBox =
                new LinearLayout(this);

        titleBox.setOrientation(
                LinearLayout.VERTICAL
        );


        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                );

        titleParams.leftMargin =
                dp(14);


        TextView title =
                createText(
                        "Raporlar",
                        30,
                        COLOR_DARK_GREEN,
                        true
                );

        title.setTypeface(
                Typeface.SERIF,
                Typeface.BOLD
        );


        TextView subtitle =
                createText(
                        "Bahçenin rakamlarını tek bakışta gör",
                        14,
                        GreenTheme.color(R.color.zd_muted),
                        false
                );


        titleBox.addView(title);

        titleBox.addView(
                subtitle,
                topParams(2)
        );


        header.addView(
                titleBox,
                titleParams
        );


        root.addView(header);


        // ========================================================
        // GENEL DURUM
        // ========================================================

        LinearLayout generalCard =
                createCard(
                        COLOR_LIGHT_GREEN
                );


        generalCard.addView(
                createText(
                        "📊 Genel Durum",
                        20,
                        COLOR_DARK_GREEN,
                        true
                )
        );


        LinearLayout row1 =
                new LinearLayout(this);

        row1.setOrientation(
                LinearLayout.HORIZONTAL
        );


        LinearLayout expenseBox =
                createStatBox(
                        "Toplam Masraf"
                );

        tvTotalExpense =
                createText(
                        "0 TL",
                        22,
                        COLOR_DARK_GREEN,
                        true
                );

        expenseBox.addView(
                tvTotalExpense
        );


        LinearLayout harvestBox =
                createStatBox(
                        "Toplam Hasat"
                );

        tvTotalHarvest =
                createText(
                        "0 kg",
                        22,
                        COLOR_DARK_GREEN,
                        true
                );

        harvestBox.addView(
                tvTotalHarvest
        );


        row1.addView(
                expenseBox,
                weightParams()
        );

        row1.addView(
                harvestBox,
                weightParams()
        );


        generalCard.addView(
                row1,
                topParams(16)
        );


        LinearLayout row2 =
                new LinearLayout(this);

        row2.setOrientation(
                LinearLayout.HORIZONTAL
        );


        LinearLayout revenueBox =
                createStatBox(
                        "Satış Geliri"
                );

        tvSalesRevenue =
                createText(
                        "0 TL",
                        22,
                        COLOR_DARK_GREEN,
                        true
                );

        revenueBox.addView(
                tvSalesRevenue
        );


        LinearLayout profitBox =
                createStatBox(
                        "Kayıtlı Net Sonuç"
                );

        tvNetProfit =
                createText(
                        "0 TL",
                        22,
                        COLOR_GREEN,
                        true
                );

        profitBox.addView(
                tvNetProfit
        );


        row2.addView(
                revenueBox,
                weightParams()
        );

        row2.addView(
                profitBox,
                weightParams()
        );


        generalCard.addView(
                row2,
                topParams(17)
        );


        root.addView(
                generalCard,
                topParams(22)
        );


        // ========================================================
        // SATIŞ VE TAHSİLAT
        // ========================================================

        LinearLayout salesCard =
                createCard(
                        COLOR_CARD
                );


        salesCard.addView(
                createText(
                        "💰 Satış & Tahsilat",
                        20,
                        COLOR_DARK_GREEN,
                        true
                )
        );


        LinearLayout saleRow1 =
                new LinearLayout(this);

        saleRow1.setOrientation(
                LinearLayout.HORIZONTAL
        );


        LinearLayout soldBox =
                createStatBox(
                        "Satılan"
                );

        tvSoldKg =
                createText(
                        "0 kg",
                        20,
                        COLOR_DARK_GREEN,
                        true
                );

        soldBox.addView(
                tvSoldKg
        );


        LinearLayout saleCountBox =
                createStatBox(
                        "Gerçek Satış"
                );

        tvSaleCount =
                createText(
                        "0",
                        20,
                        COLOR_DARK_GREEN,
                        true
                );

        saleCountBox.addView(
                tvSaleCount
        );


        saleRow1.addView(
                soldBox,
                weightParams()
        );

        saleRow1.addView(
                saleCountBox,
                weightParams()
        );


        salesCard.addView(
                saleRow1,
                topParams(16)
        );


        LinearLayout saleRow2 =
                new LinearLayout(this);

        saleRow2.setOrientation(
                LinearLayout.HORIZONTAL
        );


        LinearLayout collectedBox =
                createStatBox(
                        "Satış Tahsilatı"
                );

        tvCollected =
                createText(
                        "0 TL",
                        20,
                        COLOR_GREEN,
                        true
                );

        collectedBox.addView(
                tvCollected
        );


        LinearLayout receivableBox =
                createStatBox(
                        "Açık Alacak"
                );

        tvReceivable =
                createText(
                        "0 TL",
                        20,
                        COLOR_DARK_GREEN,
                        true
                );

        receivableBox.addView(
                tvReceivable
        );


        saleRow2.addView(
                collectedBox,
                weightParams()
        );

        saleRow2.addView(
                receivableBox,
                weightParams()
        );


        salesCard.addView(
                saleRow2,
                topParams(16)
        );


        root.addView(
                salesCard,
                topParams(14)
        );


        // ========================================================
        // SALAMURA
        // ========================================================

        LinearLayout brineCard =
                createCard(
                        COLOR_LIGHT_GREEN
                );


        brineCard.addView(
                createText(
                        "🫙 Salamura",
                        20,
                        COLOR_DARK_GREEN,
                        true
                )
        );


        LinearLayout brineRow1 =
                new LinearLayout(this);

        brineRow1.setOrientation(
                LinearLayout.HORIZONTAL
        );


        LinearLayout brineOliveBox =
                createStatBox(
                        "Salamuraya Giren"
                );

        tvBrineOlive =
                createText(
                        "0 kg",
                        20,
                        COLOR_DARK_GREEN,
                        true
                );

        brineOliveBox.addView(
                tvBrineOlive
        );


        LinearLayout readyBox =
                createStatBox(
                        "Hazır Ürün"
                );

        tvReadyBrine =
                createText(
                        "0 kg",
                        20,
                        COLOR_DARK_GREEN,
                        true
                );

        readyBox.addView(
                tvReadyBrine
        );


        brineRow1.addView(
                brineOliveBox,
                weightParams()
        );

        brineRow1.addView(
                readyBox,
                weightParams()
        );


        brineCard.addView(
                brineRow1,
                topParams(16)
        );


        LinearLayout brineRow2 =
                new LinearLayout(this);

        brineRow2.setOrientation(
                LinearLayout.HORIZONTAL
        );


        LinearLayout brineCostBox =
                createStatBox(
                        "Salamura Maliyeti"
                );

        tvBrineCost =
                createText(
                        "0 TL",
                        20,
                        COLOR_DARK_GREEN,
                        true
                );

        brineCostBox.addView(
                tvBrineCost
        );


        LinearLayout activeBox =
                createStatBox(
                        "Aktif Parti"
                );

        tvActiveBrine =
                createText(
                        "0",
                        20,
                        COLOR_DARK_GREEN,
                        true
                );

        activeBox.addView(
                tvActiveBrine
        );


        brineRow2.addView(
                brineCostBox,
                weightParams()
        );

        brineRow2.addView(
                activeBox,
                weightParams()
        );


        brineCard.addView(
                brineRow2,
                topParams(16)
        );


        root.addView(
                brineCard,
                topParams(14)
        );


        // ========================================================
        // BAHÇE PERFORMANSI
        // ========================================================

        LinearLayout gardenCard =
                createCard(
                        COLOR_CARD
                );


        gardenCard.addView(
                createText(
                        "🌳 Bahçe Performansı",
                        20,
                        COLOR_DARK_GREEN,
                        true
                )
        );


        gardenCard.addView(
                createText(
                        "Her bahçenin üretim, masraf ve gelir sonucu.",
                        12,
                        COLOR_GRAY,
                        false
                ),
                topParams(4)
        );


        containerGardens =
                new LinearLayout(this);

        containerGardens.setOrientation(
                LinearLayout.VERTICAL
        );


        gardenCard.addView(
                containerGardens,
                topParams(8)
        );


        root.addView(
                gardenCard,
                topParams(14)
        );


        // ========================================================
        // BİLGİ
        // ========================================================

        LinearLayout infoCard =
                createCard(
                        COLOR_LIGHT_GREEN
                );


        infoCard.addView(
                createText(
                        "💡 Rapor Notu",
                        15,
                        COLOR_DARK_GREEN,
                        true
                )
        );


        infoCard.addView(
                createText(
                        "Net sonuç; gerçekleşen satış gelirinden kayıtlı genel işlem, " +
                                "hasat ve salamura maliyetleri çıkarılarak hesaplanır. " +
                                "Fiyat teklifleri gelir kabul edilmez.",
                        12,
                        COLOR_GRAY,
                        false
                ),
                topParams(6)
        );


        root.addView(
                infoCard,
                topParams(14)
        );


        return scrollView;
    }


    // ============================================================
    // RAPORU YENİLE
    // ============================================================

    private void refreshReport() {
        final int generation = ++analyticsGeneration;
        final String owner = databaseHelper.getActiveOwnerUid();
        final int season = databaseHelper.getActiveSeasonYear();
        TextView[] metrics = {tvTotalExpense,tvTotalHarvest,tvSalesRevenue,tvNetProfit,
                tvSoldKg,tvSaleCount,tvCollected,tvReceivable,tvBrineOlive,tvReadyBrine,tvBrineCost,tvActiveBrine};
        for (TextView metric : metrics) metric.setText("—");
        containerGardens.removeAllViews();
        containerGardens.addView(createText("Kayıtlar hesaplanıyor…",14,COLOR_GRAY,false));
        analyticsWorker.execute(() -> {
            try {
                SeasonAnalyticsSnapshot snapshot = new GardenAnalyticsRepository(databaseHelper)
                        .load(owner, season, false).current;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != analyticsGeneration
                            || !owner.equals(databaseHelper.getActiveOwnerUid())
                            || season != databaseHelper.getActiveSeasonYear()) return;
                    showReport(snapshot);
                });
            } catch (RuntimeException error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != analyticsGeneration) return;
                    containerGardens.removeAllViews();
                    containerGardens.addView(createText("Rapor okunamadı. Kayıtlarını kontrol edip tekrar aç.",14,COLOR_GRAY,false));
                });
            }
        });
    }

    private void showReport(SeasonAnalyticsSnapshot snapshot) {
        GardenAnalyticsSnapshot total = snapshot.total;
        tvTotalExpense.setText(formatMoney(total.expense));
        tvTotalHarvest.setText(formatKg(total.harvestKg));
        tvSalesRevenue.setText(formatMoney(total.revenue));
        tvNetProfit.setText(formatSignedMoney(total.netResult()));
        tvNetProfit.setTextColor(total.netResult() < 0 ? COLOR_RED : COLOR_GREEN);
        tvSoldKg.setText(formatKg(total.soldKg));
        tvSaleCount.setText(String.valueOf(total.saleRecords));
        tvCollected.setText(formatMoney(total.collected));
        tvReceivable.setText(formatMoney(total.receivable));
        tvBrineOlive.setText(formatKg(total.brineOliveKg));
        tvReadyBrine.setText(formatKg(total.readyBrineKg));
        tvBrineCost.setText(formatMoney(total.brineCost));
        tvActiveBrine.setText(String.valueOf(total.activeBrine));
        containerGardens.removeAllViews();
        if (snapshot.gardens.isEmpty()) {
            containerGardens.addView(createText("Henüz bahçe kaydı yok.",14,COLOR_GRAY,false));
        }
        for (GardenAnalyticsSnapshot garden : snapshot.gardens.values()) {
            containerGardens.addView(createGardenReportCard(garden.name,(int)garden.trees,
                    garden.variety,garden.harvestKg,garden.soldKg,garden.expense,garden.revenue,garden.netResult()));
        }
        containerGardens.addView(createText(
                "Kayıtlı Net Sonuç = kesintiler sonrası satış geliri − kayıtlı masraflar. "
                + "Girmediğin emek, kira, amortisman ve vergi bu hesapta yoktur.",12,COLOR_GRAY,false));
        if (total.unassignedCost != 0 || total.unassignedReceivable != 0) {
            containerGardens.addView(createText("Bahçeye bağlanmamış sezon kayıtları: masraf "
                    + formatMoney(total.unassignedCost) + ", açık alacak "
                    + formatMoney(total.unassignedReceivable),12,COLOR_GRAY,false));
        }
    }

    @Override protected void onDestroy() {
        analyticsGeneration++;
        analyticsWorker.execute(() -> { if (databaseHelper != null) databaseHelper.close(); });
        analyticsWorker.shutdown();
        super.onDestroy();
    }

    // ============================================================
    // BAHÇE KARTI
    // ============================================================

    private View createGardenReportCard(
            String name,
            int treeCount,
            String variety,
            double harvestKg,
            double soldKg,
            double totalCost,
            double revenue,
            double result
    ) {

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(15),
                dp(14),
                dp(15),
                dp(14)
        );

        card.setBackground(
                fieldBackground()
        );


        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.topMargin =
                dp(10);

        card.setLayoutParams(
                params
        );


        // ========================================================
        // BAŞLIK
        // ========================================================

        LinearLayout top =
                new LinearLayout(this);

        top.setOrientation(
                LinearLayout.HORIZONTAL
        );

        top.setGravity(
                Gravity.CENTER_VERTICAL
        );


        LinearLayout titleBox =
                new LinearLayout(this);

        titleBox.setOrientation(
                LinearLayout.VERTICAL
        );


        titleBox.addView(
                createText(
                        safeText(
                                name,
                                "Bahçe"
                        ),
                        17,
                        COLOR_DARK_GREEN,
                        true
                )
        );


        String sub =
                treeCount
                        + " ağaç";


        if (!isEmpty(variety)) {

            sub +=
                    " • "
                            + variety;
        }


        titleBox.addView(
                createText(
                        sub,
                        12,
                        COLOR_GRAY,
                        false
                ),
                topParams(2)
        );


        top.addView(
                titleBox,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                )
        );


        TextView resultView =
                createText(
                        formatSignedMoney(
                                result
                        ),
                        16,
                        result < 0
                                ? COLOR_RED
                                : COLOR_GREEN,
                        true
                );


        top.addView(
                resultView
        );


        card.addView(
                top
        );


        // ========================================================
        // ÜRETİM
        // ========================================================

        card.addView(
                createText(
                        "Hasat: "
                                + formatKg(harvestKg)
                                + "   •   Satılan: "
                                + formatKg(soldKg),
                        12,
                        COLOR_TEXT,
                        false
                ),
                topParams(10)
        );


        // ========================================================
        // PARA
        // ========================================================

        card.addView(
                createText(
                        "Masraf: "
                                + formatMoney(totalCost)
                                + "   •   Gelir: "
                                + formatMoney(revenue),
                        12,
                        COLOR_TEXT,
                        false
                ),
                topParams(5)
        );


        if (harvestKg > 0) {

            double costPerKg =
                    totalCost / harvestKg;


            card.addView(
                    createText(
                            "Hasat kg başı kayıtlı maliyet: "
                                    + formatMoney(costPerKg),
                            12,
                            COLOR_GREEN,
                            true
                    ),
                    topParams(7)
            );
        }


        if (revenue <= 0
                && totalCost > 0) {

            card.addView(
                    createText(
                            "Henüz gerçekleşmiş satış geliri yok.",
                            11,
                            COLOR_GRAY,
                            false
                    ),
                    topParams(6)
            );
        }


        return card;
    }


    // ============================================================
    // UI
    // ============================================================

    private LinearLayout createCard(
            int color
    ) {

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(16),
                dp(16),
                dp(16),
                dp(16)
        );

        card.setBackground(
                roundedBackground(
                        color,
                        20,
                        COLOR_BORDER
                )
        );

        card.setElevation(
                dp(2)
        );

        return card;
    }


    private LinearLayout createStatBox(
            String label
    ) {

        LinearLayout box =
                new LinearLayout(this);

        box.setOrientation(
                LinearLayout.VERTICAL
        );


        box.addView(
                createText(
                        label,
                        12,
                        COLOR_GRAY,
                        false
                )
        );


        return box;
    }


    private TextView createText(
            String text,
            float size,
            int color,
            boolean bold
    ) {

        TextView view =
                new TextView(this);

        view.setText(text);

        view.setTextSize(size);

        view.setTextColor(color);


        if (bold) {

            view.setTypeface(
                    null,
                    Typeface.BOLD
            );
        }


        return view;
    }


    private GradientDrawable fieldBackground() {

        return roundedBackground(
                COLOR_CARD,
                16,
                COLOR_BORDER
        );
    }


    private GradientDrawable roundedBackground(
            int fill,
            int radiusDp,
            int stroke
    ) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(fill);

        drawable.setCornerRadius(
                dp(radiusDp)
        );

        drawable.setStroke(
                dp(1),
                stroke
        );

        return drawable;
    }


    private LinearLayout.LayoutParams topParams(
            int marginTop
    ) {

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.topMargin =
                dp(marginTop);

        return params;
    }


    private LinearLayout.LayoutParams weightParams() {

        return new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        );
    }


    // ============================================================
    // FORMAT
    // ============================================================

    private String formatKg(
            double value
    ) {

        return formatNumber(value)
                + " kg";
    }


    private String formatMoney(
            double value
    ) {

        return formatNumber(value)
                + " TL";
    }


    private String formatSignedMoney(
            double value
    ) {

        if (value > 0) {

            return "+"
                    + formatNumber(value)
                    + " TL";
        }

        return formatNumber(value)
                + " TL";
    }


    private String formatNumber(
            double value
    ) {

        NumberFormat format =
                NumberFormat.getNumberInstance(
                        TR
                );

        format.setMinimumFractionDigits(0);

        format.setMaximumFractionDigits(2);

        return format.format(value);
    }


    // ============================================================
    // STRING
    // ============================================================

    private boolean isEmpty(
            String value
    ) {

        return value == null
                || value.trim().isEmpty();
    }


    private String safeText(
            String value,
            String fallback
    ) {

        if (isEmpty(value)) {
            return fallback;
        }

        return value;
    }


    // ============================================================
    // DP
    // ============================================================

    private int dp(
            int value
    ) {

        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
