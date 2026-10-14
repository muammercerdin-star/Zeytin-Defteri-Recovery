package com.zeytindefteri.app;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MarketActivity extends Activity {

    private DatabaseHelper databaseHelper;

    private final Locale TR =
            new Locale("tr", "TR");


    // ============================================================
    // VIEW'LAR
    // ============================================================

    private Spinner spinnerGrade;
    private Spinner spinnerPeriod;

    private EditText etVarietyFilter;

    private TextView tvSelectedMarket;
    private TextView tvMedianPrice;
    private TextView tvPriceRange;
    private TextView tvMarketKg;
    private TextView tvTransactionCount;
    private TextView tvConfidence;
    private TextView tvOutlierInfo;

    private LinearLayout containerRegions;

    private TextView btnBack;
    private TextView btnAddSale;


    // ============================================================
    // ELEKLER
    // ============================================================

    private final String[] gradeNames = {
            "Double",
            "1 No",
            "2 No",
            "3 No",
            "4 No",
            "Yağlık"
    };


    private final String[] gradeCodes = {
            "DOUBLE",
            "1",
            "2",
            "3",
            "4",
            "YAGLIK"
    };


    // ============================================================
    // DÖNEMLER
    // ============================================================

    private final String[] periodNames = {
            "Son 7 gün",
            "Son 30 gün",
            "Son 90 gün",
            "Tümü"
    };


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
            GreenTheme.color(R.color.zd_green);

    private final int COLOR_LIGHT_GREEN =
            GreenTheme.color(R.color.zd_green_soft);

    private final int COLOR_BORDER =
            GreenTheme.color(R.color.zd_border);

    private final int COLOR_TEXT =
            GreenTheme.color(R.color.zd_text);

    private final int COLOR_GRAY =
            GreenTheme.color(R.color.zd_muted);

    // Yalnızca yeşil düğme üstündeki yazı (btnAddSale): koyu yazı
    private final int COLOR_WHITE =
            GreenTheme.color(R.color.zd_bg);


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

        // Eski 5 öğeli alt menü (ZdNav) kaldırıldı; geri tuşu bir önceki ekrana döner.

        setupSpinners();

        setupListeners();

        refreshMarket();
    }


    // ============================================================
    // GERİ DÖNÜNCE YENİLE
    // ============================================================

    @Override
    protected void onResume() {

        super.onResume();

        if (databaseHelper != null
                && spinnerGrade != null) {

            refreshMarket();
        }
    }


    // ============================================================
    // EKRANI JAVA İLE OLUŞTUR
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
        // ÜST BAR
        // ========================================================

        LinearLayout header =
                new LinearLayout(this);

        header.setOrientation(
                LinearLayout.HORIZONTAL
        );

        header.setGravity(
                Gravity.CENTER_VERTICAL
        );


        btnBack =
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


        LinearLayout.LayoutParams titleBoxParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                );

        titleBoxParams.leftMargin =
                dp(14);


        TextView title =
                createText(
                        "Zeytin Piyasası",
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
                        "Gerçek satışlardan oluşan bölgesel piyasa",
                        14,
                        GreenTheme.color(R.color.zd_muted),
                        false
                );


        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        subtitleParams.topMargin =
                dp(2);


        titleBox.addView(
                title
        );

        titleBox.addView(
                subtitle,
                subtitleParams
        );


        header.addView(
                titleBox,
                titleBoxParams
        );


        root.addView(
                header
        );


        // ========================================================
        // FİLTRE KARTI
        // ========================================================

        LinearLayout filterCard =
                createCard(
                        COLOR_CARD
                );


        TextView filterTitle =
                createText(
                        "Piyasa Filtresi",
                        19,
                        COLOR_DARK_GREEN,
                        true
                );


        filterCard.addView(
                filterTitle
        );


        addLabel(
                filterCard,
                "Elek"
        );


        spinnerGrade =
                new Spinner(this);

        spinnerGrade.setPadding(
                dp(14),
                0,
                dp(14),
                0
        );

        spinnerGrade.setBackground(
                fieldBackground()
        );


        filterCard.addView(
                spinnerGrade,
                matchHeight(
                        56,
                        7
                )
        );


        addLabel(
                filterCard,
                "Dönem"
        );


        spinnerPeriod =
                new Spinner(this);

        spinnerPeriod.setPadding(
                dp(14),
                0,
                dp(14),
                0
        );

        spinnerPeriod.setBackground(
                fieldBackground()
        );


        filterCard.addView(
                spinnerPeriod,
                matchHeight(
                        56,
                        7
                )
        );


        addLabel(
                filterCard,
                "Zeytin Çeşidi"
        );


        etVarietyFilter =
                new EditText(this);

        etVarietyFilter.setHint(
                "Tümü veya Domat, Gemlik..."
        );

        etVarietyFilter.setSingleLine(
                true
        );

        etVarietyFilter.setTextColor(
                COLOR_TEXT
        );

        etVarietyFilter.setHintTextColor(
                GreenTheme.color(R.color.zd_muted)
        );

        etVarietyFilter.setTextSize(
                15
        );

        etVarietyFilter.setPadding(
                dp(18),
                0,
                dp(18),
                0
        );

        etVarietyFilter.setBackground(
                fieldBackground()
        );


        filterCard.addView(
                etVarietyFilter,
                matchHeight(
                        56,
                        7
                )
        );


        root.addView(
                filterCard,
                matchWrapTop(22)
        );


        // ========================================================
        // PİYASA ÖZETİ
        // ========================================================

        LinearLayout summaryCard =
                createCard(
                        COLOR_LIGHT_GREEN
                );


        tvSelectedMarket =
                createText(
                        "1 No Piyasası",
                        20,
                        COLOR_DARK_GREEN,
                        true
                );


        summaryCard.addView(
                tvSelectedMarket
        );


        LinearLayout firstRow =
                new LinearLayout(this);

        firstRow.setOrientation(
                LinearLayout.HORIZONTAL
        );


        LinearLayout medianBox =
                createStatBox(
                        "Medyan Fiyat"
                );

        tvMedianPrice =
                createText(
                        "—",
                        27,
                        GreenTheme.color(R.color.zd_green),
                        true
                );

        medianBox.addView(
                tvMedianPrice
        );


        LinearLayout rangeBox =
                createStatBox(
                        "Fiyat Aralığı"
                );

        tvPriceRange =
                createText(
                        "—",
                        18,
                        COLOR_DARK_GREEN,
                        true
                );

        rangeBox.addView(
                tvPriceRange
        );


        firstRow.addView(
                medianBox,
                weightParams()
        );

        firstRow.addView(
                rangeBox,
                weightParams()
        );


        summaryCard.addView(
                firstRow,
                matchWrapTop(16)
        );


        View divider =
                new View(this);

        divider.setBackgroundColor(
                GreenTheme.color(R.color.zd_green_soft)
        );


        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(1)
                );

        dividerParams.topMargin =
                dp(16);

        dividerParams.bottomMargin =
                dp(15);


        summaryCard.addView(
                divider,
                dividerParams
        );


        LinearLayout secondRow =
                new LinearLayout(this);

        secondRow.setOrientation(
                LinearLayout.HORIZONTAL
        );


        LinearLayout kgBox =
                createStatBox(
                        "Satış Hacmi"
                );

        tvMarketKg =
                createText(
                        "0 kg",
                        18,
                        COLOR_DARK_GREEN,
                        true
                );

        kgBox.addView(
                tvMarketKg
        );


        LinearLayout countBox =
                createStatBox(
                        "Gerçek Satış"
                );

        tvTransactionCount =
                createText(
                        "0 kayıt",
                        18,
                        COLOR_DARK_GREEN,
                        true
                );

        countBox.addView(
                tvTransactionCount
        );


        secondRow.addView(
                kgBox,
                weightParams()
        );

        secondRow.addView(
                countBox,
                weightParams()
        );


        summaryCard.addView(
                secondRow
        );


        tvConfidence =
                createText(
                        "Veri bekleniyor",
                        14,
                        GreenTheme.color(R.color.zd_text_soft),
                        true
                );


        summaryCard.addView(
                tvConfidence,
                matchWrapTop(17)
        );


        tvOutlierInfo =
                createText(
                        "Aykırı fiyat filtresi aktif",
                        11,
                        COLOR_GRAY,
                        false
                );


        summaryCard.addView(
                tvOutlierInfo,
                matchWrapTop(5)
        );


        root.addView(
                summaryCard,
                matchWrapTop(14)
        );


        // ========================================================
        // BÖLGELER
        // ========================================================

        LinearLayout regionsCard =
                createCard(
                        COLOR_CARD
                );


        TextView regionsTitle =
                createText(
                        "Bölgelere Göre",
                        20,
                        COLOR_DARK_GREEN,
                        true
                );


        TextView regionsInfo =
                createText(
                        "Bölgeler medyan satış fiyatına göre sıralanır.",
                        12,
                        COLOR_GRAY,
                        false
                );


        regionsCard.addView(
                regionsTitle
        );

        regionsCard.addView(
                regionsInfo,
                matchWrapTop(4)
        );


        containerRegions =
                new LinearLayout(this);

        containerRegions.setOrientation(
                LinearLayout.VERTICAL
        );


        regionsCard.addView(
                containerRegions,
                matchWrapTop(10)
        );


        root.addView(
                regionsCard,
                matchWrapTop(14)
        );


        // ========================================================
        // PİYASA AÇIKLAMASI
        // ========================================================

        LinearLayout infoCard =
                createCard(
                        COLOR_LIGHT_GREEN
                );


        infoCard.addView(
                createText(
                        "Piyasa nasıl hesaplanıyor?",
                        15,
                        COLOR_DARK_GREEN,
                        true
                )
        );


        infoCard.addView(
                createText(
                        "Yalnızca gerçekleşen ve piyasa kullanımına izin verilen satışlar kullanılır. " +
                                "Fiyat teklifleri dahil edilmez. Aykırı fiyatlar filtrelenir ve " +
                                "düz ortalama yerine medyan gösterilir.",
                        12,
                        GreenTheme.color(R.color.zd_muted),
                        false
                ),
                matchWrapTop(6)
        );


        infoCard.addView(
                createText(
                        "Şimdilik bu cihazdaki satış kayıtları kullanılıyor.",
                        11,
                        COLOR_GRAY,
                        false
                ),
                matchWrapTop(8)
        );


        root.addView(
                infoCard,
                matchWrapTop(14)
        );


        // ========================================================
        // SATIŞ BUTONU
        // ========================================================

        btnAddSale =
                createText(
                        "+ Gerçek Satış Kaydet",
                        16,
                        COLOR_WHITE,
                        true
                );

        btnAddSale.setGravity(
                Gravity.CENTER
        );

        btnAddSale.setBackground(
                roundedBackground(
                        COLOR_GREEN,
                        14,
                        COLOR_GREEN
                )
        );

        btnAddSale.setElevation(
                dp(4)
        );


        root.addView(
                btnAddSale,
                matchHeight(
                        58,
                        18
                )
        );


        return scrollView;
    }


    // ============================================================
    // SPINNER'LAR
    // ============================================================

    private void setupSpinners() {

        ArrayAdapter<String> gradeAdapter =
                new ArrayAdapter<String>(
                        this,
                        android.R.layout.simple_spinner_item,
                        gradeNames
                );

        gradeAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerGrade.setAdapter(
                gradeAdapter
        );

        spinnerGrade.setSelection(
                1
        );


        ArrayAdapter<String> periodAdapter =
                new ArrayAdapter<String>(
                        this,
                        android.R.layout.simple_spinner_item,
                        periodNames
                );

        periodAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerPeriod.setAdapter(
                periodAdapter
        );

        spinnerPeriod.setSelection(
                1
        );
    }


    // ============================================================
    // LISTENER'LAR
    // ============================================================

    private void setupListeners() {

        btnBack.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {

                        finish();
                    }
                }
        );


        btnAddSale.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {

                        Intent saleIntent =
                                new Intent(
                                        MarketActivity.this,
                                        SaleActivity.class
                                );

                        startActivity(
                                saleIntent
                        );
                    }
                }
        );


        spinnerGrade.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {

                        refreshMarket();
                    }


                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent
                    ) {
                    }
                }
        );


        spinnerPeriod.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {

                        refreshMarket();
                    }


                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent
                    ) {
                    }
                }
        );


        etVarietyFilter.addTextChangedListener(
                new TextWatcher() {

                    @Override
                    public void beforeTextChanged(
                            CharSequence s,
                            int start,
                            int count,
                            int after
                    ) {
                    }


                    @Override
                    public void onTextChanged(
                            CharSequence s,
                            int start,
                            int before,
                            int count
                    ) {

                        refreshMarket();
                    }


                    @Override
                    public void afterTextChanged(
                            Editable editable
                    ) {
                    }
                }
        );
    }


    // ============================================================
    // PİYASAYI YENİLE
    // ============================================================

    private void refreshMarket() {

        if (spinnerGrade == null
                || spinnerPeriod == null
                || etVarietyFilter == null) {

            return;
        }


        int position =
                spinnerGrade
                        .getSelectedItemPosition();


        if (position < 0
                || position >= gradeCodes.length) {

            return;
        }


        String gradeCode =
                gradeCodes[position];


        String gradeName =
                gradeNames[position];


        String varietyFilter =
                etVarietyFilter
                        .getText()
                        .toString()
                        .trim()
                        .toLowerCase(TR);


        tvSelectedMarket.setText(
                gradeName
                        + " Piyasası"
        );


        List<MarketRecord> records =
                loadMarketRecords(
                        gradeCode,
                        varietyFilter
                );


        showMarket(
                records
        );
    }


    // ============================================================
    // DATABASE
    // ============================================================

    private List<MarketRecord> loadMarketRecords(
            String gradeCode,
            String varietyFilter
    ) {

        ArrayList<MarketRecord> records =
                new ArrayList<MarketRecord>();


        SQLiteDatabase db =
                databaseHelper
                        .getReadableDatabase();


        long minimumTime =
                getMinimumCreatedAt();


        String sql =
                "SELECT " +
                        "s.region AS region," +
                        "s.variety AS variety," +
                        "sg.kg AS kg," +
                        "sg.price_per_kg AS price_per_kg " +

                        "FROM sale_grades sg " +

                        "INNER JOIN sales s " +
                        "ON s.id = sg.sale_id " +

                        "WHERE s.sale_kind = 'SALE' " +
                        "AND s.market_consent = 1 " +
                        "AND sg.grade = ? " +
                        "AND sg.kg > 0 " +
                        "AND sg.price_per_kg > 0";


        ArrayList<String> args =
                new ArrayList<String>();


        args.add(
                gradeCode
        );


        if (minimumTime > 0) {

            sql +=
                    " AND s.created_at >= ?";

            args.add(
                    String.valueOf(
                            minimumTime
                    )
            );
        }


        sql +=
                " ORDER BY s.created_at DESC";


        String[] selectionArgs =
                new String[args.size()];


        for (int i = 0; i < args.size(); i++) {

            selectionArgs[i] =
                    args.get(i);
        }


        Cursor cursor =
                db.rawQuery(
                        sql,
                        selectionArgs
                );


        try {

            while (cursor.moveToNext()) {

                String region =
                        cursor.getString(
                                cursor.getColumnIndexOrThrow(
                                        "region"
                                )
                        );


                String variety =
                        cursor.getString(
                                cursor.getColumnIndexOrThrow(
                                        "variety"
                                )
                        );


                double kg =
                        cursor.getDouble(
                                cursor.getColumnIndexOrThrow(
                                        "kg"
                                )
                        );


                double price =
                        cursor.getDouble(
                                cursor.getColumnIndexOrThrow(
                                        "price_per_kg"
                                )
                        );


                if (!varietyFilter.isEmpty()) {

                    String currentVariety =
                            variety == null
                                    ? ""
                                    : variety
                                    .trim()
                                    .toLowerCase(TR);


                    if (!currentVariety.contains(
                            varietyFilter
                    )) {

                        continue;
                    }
                }


                MarketRecord record =
                        new MarketRecord();


                record.region =
                        cleanRegion(
                                region
                        );

                record.kg =
                        kg;

                record.price =
                        price;


                records.add(
                        record
                );
            }

        } finally {

            cursor.close();
        }


        return records;
    }


    // ============================================================
    // DÖNEM
    // ============================================================

    private long getMinimumCreatedAt() {

        int position =
                spinnerPeriod
                        .getSelectedItemPosition();


        int days;


        if (position == 0) {

            days = 7;

        } else if (position == 1) {

            days = 30;

        } else if (position == 2) {

            days = 90;

        } else {

            return 0;
        }


        return System.currentTimeMillis()
                -
                days
                        * 24L
                        * 60L
                        * 60L
                        * 1000L;
    }


    // ============================================================
    // PİYASA SONUÇLARI
    // ============================================================

    private void showMarket(
            List<MarketRecord> rawRecords
    ) {

        containerRegions.removeAllViews();


        if (rawRecords == null
                || rawRecords.isEmpty()) {

            showEmptyMarket();

            return;
        }


        List<MarketRecord> filtered =
                filterOutliers(
                        rawRecords
                );


        if (filtered.isEmpty()) {

            showEmptyMarket();

            return;
        }


        int removedCount =
                rawRecords.size()
                        -
                        filtered.size();


        ArrayList<Double> prices =
                new ArrayList<Double>();


        double totalKg =
                0;


        for (MarketRecord record : filtered) {

            prices.add(
                    record.price
            );

            totalKg +=
                    record.kg;
        }


        Collections.sort(
                prices
        );


        double median =
                calculateMedian(
                        prices
                );


        double min =
                prices.get(0);


        double max =
                prices.get(
                        prices.size() - 1
                );


        tvMedianPrice.setText(
                formatPrice(
                        median
                )
        );


        tvPriceRange.setText(
                formatNumber(min)
                        + " – "
                        + formatNumber(max)
                        + " TL"
        );


        tvMarketKg.setText(
                formatKg(
                        totalKg
                )
        );


        tvTransactionCount.setText(
                filtered.size()
                        + " kayıt"
        );


        tvConfidence.setText(
                getConfidenceText(
                        filtered.size(),
                        totalKg
                )
        );


        if (removedCount > 0) {

            tvOutlierInfo.setText(
                    removedCount
                            + " aykırı fiyat kaydı dışlandı."
            );

        } else {

            tvOutlierInfo.setText(
                    "Aykırı fiyat filtresi aktif • Dışlanan kayıt yok."
            );
        }


        Map<String, List<MarketRecord>> groups =
                new HashMap<String, List<MarketRecord>>();


        for (MarketRecord record : filtered) {

            List<MarketRecord> list =
                    groups.get(
                            record.region
                    );


            if (list == null) {

                list =
                        new ArrayList<MarketRecord>();

                groups.put(
                        record.region,
                        list
                );
            }


            list.add(
                    record
            );
        }


        ArrayList<RegionStats> regionStats =
                new ArrayList<RegionStats>();


        for (
                Map.Entry<String, List<MarketRecord>> entry
                : groups.entrySet()
        ) {

            regionStats.add(
                    calculateRegionStats(
                            entry.getKey(),
                            entry.getValue()
                    )
            );
        }


        Collections.sort(
                regionStats,

                new Comparator<RegionStats>() {

                    @Override
                    public int compare(
                            RegionStats first,
                            RegionStats second
                    ) {

                        return Double.compare(
                                second.medianPrice,
                                first.medianPrice
                        );
                    }
                }
        );


        for (RegionStats stats : regionStats) {

            containerRegions.addView(
                    createRegionCard(
                            stats,
                            median
                    )
            );
        }
    }


    // ============================================================
    // BOŞ DURUM
    // ============================================================

    private void showEmptyMarket() {

        tvMedianPrice.setText(
                "—"
        );

        tvPriceRange.setText(
                "—"
        );

        tvMarketKg.setText(
                "0 kg"
        );

        tvTransactionCount.setText(
                "0 kayıt"
        );

        tvConfidence.setText(
                "Veri bekleniyor"
        );

        tvOutlierInfo.setText(
                "Aykırı fiyat filtresi aktif"
        );


        TextView empty =
                createText(
                        "Bu filtre için henüz gerçek satış kaydı yok.",
                        14,
                        COLOR_GRAY,
                        false
                );

        empty.setGravity(
                Gravity.CENTER
        );

        empty.setPadding(
                dp(8),
                dp(30),
                dp(8),
                dp(30)
        );


        containerRegions.addView(
                empty
        );
    }


    // ============================================================
    // BÖLGE İSTATİSTİĞİ
    // ============================================================

    private RegionStats calculateRegionStats(
            String region,
            List<MarketRecord> records
    ) {

        RegionStats stats =
                new RegionStats();


        stats.region =
                region;


        ArrayList<Double> prices =
                new ArrayList<Double>();


        double totalKg =
                0;


        for (MarketRecord record : records) {

            prices.add(
                    record.price
            );

            totalKg +=
                    record.kg;
        }


        Collections.sort(
                prices
        );


        stats.medianPrice =
                calculateMedian(
                        prices
                );

        stats.minPrice =
                prices.get(0);

        stats.maxPrice =
                prices.get(
                        prices.size() - 1
                );

        stats.totalKg =
                totalKg;

        stats.transactionCount =
                records.size();


        return stats;
    }


    // ============================================================
    // BÖLGE KARTI
    // ============================================================

    private View createRegionCard(
            RegionStats stats,
            double generalMedian
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


        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.topMargin =
                dp(10);

        card.setLayoutParams(
                cardParams
        );


        LinearLayout top =
                new LinearLayout(this);

        top.setOrientation(
                LinearLayout.HORIZONTAL
        );

        top.setGravity(
                Gravity.CENTER_VERTICAL
        );


        TextView region =
                createText(
                        stats.region,
                        17,
                        COLOR_DARK_GREEN,
                        true
                );


        top.addView(
                region,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                )
        );


        top.addView(
                createText(
                        formatPrice(
                                stats.medianPrice
                        ),
                        19,
                        COLOR_GREEN,
                        true
                )
        );


        card.addView(
                top
        );


        double difference =
                stats.medianPrice
                        -
                        generalMedian;


        String differenceText;


        if (difference > 0.009) {

            differenceText =
                    "Genel piyasanın "
                            + formatNumber(difference)
                            + " TL üzerinde";

        } else if (difference < -0.009) {

            differenceText =
                    "Genel piyasanın "
                            + formatNumber(
                            Math.abs(difference)
                    )
                            + " TL altında";

        } else {

            differenceText =
                    "Genel piyasa ile aynı";
        }


        card.addView(
                createText(
                        differenceText,
                        12,
                        COLOR_GRAY,
                        false
                ),
                matchWrapTop(5)
        );


        card.addView(
                createText(
                        "Aralık: "
                                + formatNumber(stats.minPrice)
                                + " – "
                                + formatNumber(stats.maxPrice)
                                + " TL/kg\n"
                                + "Hacim: "
                                + formatKg(stats.totalKg)
                                + " • "
                                + stats.transactionCount
                                + " satış",
                        12,
                        COLOR_TEXT,
                        false
                ),
                matchWrapTop(7)
        );


        card.addView(
                createText(
                        getConfidenceText(
                                stats.transactionCount,
                                stats.totalKg
                        ),
                        11,
                        COLOR_GREEN,
                        true
                ),
                matchWrapTop(7)
        );


        return card;
    }


    // ============================================================
    // AYKIRI DEĞER FİLTRESİ
    // ============================================================

    private List<MarketRecord> filterOutliers(
            List<MarketRecord> records
    ) {

        if (records == null) {

            return new ArrayList<MarketRecord>();
        }


        if (records.size() < 4) {

            return new ArrayList<MarketRecord>(
                    records
            );
        }


        ArrayList<Double> prices =
                new ArrayList<Double>();


        for (MarketRecord record : records) {

            prices.add(
                    record.price
            );
        }


        Collections.sort(
                prices
        );


        double q1 =
                calculatePercentile(
                        prices,
                        0.25
                );


        double q3 =
                calculatePercentile(
                        prices,
                        0.75
                );


        double iqr =
                q3 - q1;


        if (iqr <= 0) {

            return new ArrayList<MarketRecord>(
                    records
            );
        }


        double lower =
                q1
                        -
                        1.5 * iqr;


        double upper =
                q3
                        +
                        1.5 * iqr;


        ArrayList<MarketRecord> result =
                new ArrayList<MarketRecord>();


        for (MarketRecord record : records) {

            if (record.price >= lower
                    && record.price <= upper) {

                result.add(
                        record
                );
            }
        }


        if (result.isEmpty()) {

            return new ArrayList<MarketRecord>(
                    records
            );
        }


        return result;
    }


    // ============================================================
    // MEDYAN
    // ============================================================

    private double calculateMedian(
            List<Double> values
    ) {

        if (values == null
                || values.isEmpty()) {

            return 0;
        }


        int size =
                values.size();


        if (size % 2 == 1) {

            return values.get(
                    size / 2
            );
        }


        return (
                values.get(
                        size / 2 - 1
                )
                        +
                        values.get(
                                size / 2
                        )
        ) / 2.0;
    }


    // ============================================================
    // PERCENTILE
    // ============================================================

    private double calculatePercentile(
            List<Double> values,
            double percentile
    ) {

        if (values == null
                || values.isEmpty()) {

            return 0;
        }


        if (values.size() == 1) {

            return values.get(0);
        }


        double position =
                (values.size() - 1)
                        * percentile;


        int lower =
                (int) Math.floor(
                        position
                );


        int upper =
                (int) Math.ceil(
                        position
                );


        if (lower == upper) {

            return values.get(
                    lower
            );
        }


        double weight =
                position
                        -
                        lower;


        return values.get(lower)
                * (1 - weight)
                +
                values.get(upper)
                        * weight;
    }


    // ============================================================
    // GÜVEN
    // ============================================================

    private String getConfidenceText(
            int count,
            double kg
    ) {

        if (count >= 10
                && kg >= 5000) {

            return "Yüksek veri güveni";
        }


        if (count >= 5
                && kg >= 1500) {

            return "Orta veri güveni";
        }


        return "Sınırlı veri";
    }


    // ============================================================
    // YARDIMCI UI
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
                        13,
                        COLOR_GRAY,
                        false
                )
        );


        return box;
    }


    private void addLabel(
            LinearLayout parent,
            String label
    ) {

        parent.addView(
                createText(
                        label,
                        14,
                        COLOR_DARK_GREEN,
                        true
                ),
                matchWrapTop(17)
        );
    }


    private TextView createText(
            String text,
            float size,
            int color,
            boolean bold
    ) {

        TextView view =
                new TextView(this);

        view.setText(
                text
        );

        view.setTextSize(
                size
        );

        view.setTextColor(
                color
        );


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
                18,
                COLOR_BORDER
        );
    }


    private GradientDrawable roundedBackground(
            int fillColor,
            int radiusDp,
            int strokeColor
    ) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(
                fillColor
        );

        drawable.setCornerRadius(
                dp(radiusDp)
        );

        drawable.setStroke(
                dp(1),
                strokeColor
        );


        return drawable;
    }


    private LinearLayout.LayoutParams matchWrapTop(
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


    private LinearLayout.LayoutParams matchHeight(
            int height,
            int marginTop
    ) {

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(height)
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

    private String formatPrice(
            double value
    ) {

        return formatNumber(
                value
        )
                + " TL/kg";
    }


    private String formatKg(
            double value
    ) {

        return formatNumber(
                value
        )
                + " kg";
    }


    private String formatNumber(
            double value
    ) {

        NumberFormat format =
                NumberFormat
                        .getNumberInstance(
                                TR
                        );

        format.setMaximumFractionDigits(
                2
        );

        format.setMinimumFractionDigits(
                0
        );


        return format.format(
                value
        );
    }


    private String cleanRegion(
            String region
    ) {

        if (region == null
                || region.trim().isEmpty()) {

            return "Bölge belirtilmedi";
        }


        return region.trim();
    }


    private int dp(
            int value
    ) {

        return Math.round(
                value
                        *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }


    // ============================================================
    // MODELLER
    // ============================================================

    private static class MarketRecord {

        String region;

        double kg;

        double price;
    }


    private static class RegionStats {

        String region;

        double medianPrice;

        double minPrice;

        double maxPrice;

        double totalKg;

        int transactionCount;
    }
}
