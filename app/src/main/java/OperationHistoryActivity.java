package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

public class OperationHistoryActivity extends Activity {

    // ============================================================
    // DATABASE
    // ============================================================

    private DatabaseHelper databaseHelper;


    // ============================================================
    // FORMAT
    // ============================================================

    private final Locale TR =
            new Locale(
                    "tr",
                    "TR"
            );


    // ============================================================
    // VIEW'LAR
    // ============================================================

    private Spinner spinnerGarden;
    private Spinner spinnerType;

    private TextView tvRecordCount;
    private TextView tvFilteredCost;

    private LinearLayout containerOperations;


    // ============================================================
    // BAHÇE ID LISTESİ
    // ============================================================

    private final ArrayList<Integer> gardenIds =
            new ArrayList<Integer>();


    // ============================================================
    // İŞLEM TÜRLERİ
    // ============================================================

    private final String[] typeNames = {

            "Tüm İşlemler",
            "Sulama",
            "Gübre",
            "İlaçlama",
            "Budama",
            "Sinek Tuzağı",
            "Toprak İşleme",
            "İşçilik",
            "Mazot",
            "Nakliye",
            "Hasat",
            "Diğer"
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
            GreenTheme.parse(
                    "#2F6B3F"
            );

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


    // ============================================================
    // ON CREATE
    // ============================================================

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(
                savedInstanceState
        );


        if (getActionBar() != null) {

            getActionBar().hide();
        }
        getWindow().setStatusBarColor(COLOR_BACKGROUND);
        getWindow().setNavigationBarColor(COLOR_BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(0);


        databaseHelper =
                new DatabaseHelper(
                        this
                );


        setContentView(
                createScreen()
        );

        ZdNav.attach(this, "İşlemler");


        loadGardenFilter();

        setupTypeFilter();

        setupListeners();

        refreshOperations();
    }


    // ============================================================
    // EKRANA GERİ DÖNÜNCE YENİLE
    // ============================================================

    @Override
    protected void onResume() {

        super.onResume();


        if (databaseHelper != null
                && containerOperations != null) {

            refreshOperations();
        }
    }


    // ============================================================
    // EKRANI OLUŞTUR
    // ============================================================

    private View createScreen() {

        ScrollView scrollView =
                new ScrollView(
                        this
                );


        scrollView.setFillViewport(
                true
        );


        scrollView.setBackgroundColor(
                COLOR_BACKGROUND
        );


        LinearLayout root =
                new LinearLayout(
                        this
                );


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
                new LinearLayout(
                        this
                );


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
                    public void onClick(
                            View view
                    ) {

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
                new LinearLayout(
                        this
                );


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
                        "İşlem Geçmişi",
                        29,
                        COLOR_DARK_GREEN,
                        true
                );


        title.setTypeface(
                Typeface.SERIF,
                Typeface.BOLD
        );


        TextView subtitle =
                createText(
                        "Bahçede yaptığın tüm işleri tek yerde gör",
                        14,
                        GreenTheme.parse(
                                "#4A7441"
                        ),
                        false
                );


        titleBox.addView(
                title
        );


        titleBox.addView(
                subtitle,
                topParams(2)
        );


        header.addView(
                titleBox,
                titleBoxParams
        );


        TextView btnNew =
                createText(
                        "+",
                        27,
                        COLOR_WHITE,
                        true
                );


        btnNew.setGravity(
                Gravity.CENTER
        );


        btnNew.setBackground(
                roundedBackground(
                        COLOR_GREEN,
                        100,
                        COLOR_GREEN
                )
        );


        btnNew.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(
                            View view
                    ) {

                        openNewOperation();
                    }
                }
        );


        header.addView(
                btnNew,

                new LinearLayout.LayoutParams(
                        dp(46),
                        dp(46)
                )
        );


        root.addView(
                header
        );


        // ========================================================
        // FİLTRELER
        // ========================================================

        LinearLayout filterCard =
                createCard(
                        COLOR_CARD
                );


        filterCard.addView(
                createText(
                        "🔎 İşlemleri Filtrele",
                        18,
                        COLOR_DARK_GREEN,
                        true
                )
        );


        filterCard.addView(
                createText(
                        "Bahçe",
                        13,
                        COLOR_GRAY,
                        false
                ),
                topParams(14)
        );


        spinnerGarden =
                new Spinner(
                        this
                );


        spinnerGarden.setBackground(
                fieldBackground()
        );


        spinnerGarden.setPadding(
                dp(12),
                0,
                dp(12),
                0
        );


        filterCard.addView(
                spinnerGarden,
                fixedHeightTop(
                        54,
                        6
                )
        );


        filterCard.addView(
                createText(
                        "İşlem Türü",
                        13,
                        COLOR_GRAY,
                        false
                ),
                topParams(14)
        );


        spinnerType =
                new Spinner(
                        this
                );


        spinnerType.setBackground(
                fieldBackground()
        );


        spinnerType.setPadding(
                dp(12),
                0,
                dp(12),
                0
        );


        filterCard.addView(
                spinnerType,
                fixedHeightTop(
                        54,
                        6
                )
        );


        root.addView(
                filterCard,
                topParams(22)
        );


        // ========================================================
        // ÖZET
        // ========================================================

        LinearLayout summaryCard =
                createCard(
                        COLOR_LIGHT_GREEN
                );


        summaryCard.addView(
                createText(
                        "📋 Seçili Kayıtlar",
                        18,
                        COLOR_DARK_GREEN,
                        true
                )
        );


        LinearLayout summaryRow =
                new LinearLayout(
                        this
                );


        summaryRow.setOrientation(
                LinearLayout.HORIZONTAL
        );


        LinearLayout countBox =
                createStatBox(
                        "İşlem Sayısı"
                );


        tvRecordCount =
                createText(
                        "0",
                        24,
                        COLOR_DARK_GREEN,
                        true
                );


        countBox.addView(
                tvRecordCount
        );


        LinearLayout costBox =
                createStatBox(
                        "Toplam Masraf"
                );


        tvFilteredCost =
                createText(
                        "0 TL",
                        22,
                        COLOR_DARK_GREEN,
                        true
                );


        costBox.addView(
                tvFilteredCost
        );


        summaryRow.addView(
                countBox,
                weightParams()
        );


        summaryRow.addView(
                costBox,
                weightParams()
        );


        summaryCard.addView(
                summaryRow,
                topParams(14)
        );


        root.addView(
                summaryCard,
                topParams(14)
        );


        // ========================================================
        // İŞLEM LİSTESİ
        // ========================================================

        LinearLayout listCard =
                createCard(
                        COLOR_CARD
                );


        listCard.addView(
                createText(
                        "Geçmiş İşlemler",
                        20,
                        COLOR_DARK_GREEN,
                        true
                )
        );


        listCard.addView(
                createText(
                        "En yeni işlem en üstte gösterilir.",
                        12,
                        COLOR_GRAY,
                        false
                ),
                topParams(4)
        );


        containerOperations =
                new LinearLayout(
                        this
                );


        containerOperations.setOrientation(
                LinearLayout.VERTICAL
        );


        listCard.addView(
                containerOperations,
                topParams(9)
        );


        root.addView(
                listCard,
                topParams(14)
        );


        // ========================================================
        // YENİ İŞLEM
        // ========================================================

        TextView btnNewBottom =
                createButton(
                        "+ Yeni İşlem Kaydet"
                );


        btnNewBottom.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(
                            View view
                    ) {

                        openNewOperation();
                    }
                }
        );


        root.addView(
                btnNewBottom,
                fixedHeightTop(
                        58,
                        14
                )
        );


        return scrollView;
    }


    // ============================================================
    // BAHÇE FİLTRESİ
    // ============================================================

    private void loadGardenFilter() {

        gardenIds.clear();


        ArrayList<String> names =
                new ArrayList<String>();


        gardenIds.add(
                -1
        );


        names.add(
                "Tüm Bahçeler"
        );


        Cursor cursor =
                databaseHelper
                        .getGardens();


        while (cursor.moveToNext()) {

            gardenIds.add(
                    cursor.getInt(
                            cursor.getColumnIndexOrThrow(
                                    "id"
                            )
                    )
            );


            names.add(
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                    "name"
                            )
                    )
            );
        }


        cursor.close();


        ArrayAdapter<String> adapter =
                new ArrayAdapter<String>(
                        this,
                        android.R.layout.simple_spinner_item,
                        names
                );


        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );


        spinnerGarden.setAdapter(
                adapter
        );
    }


    // ============================================================
    // İŞLEM TÜRÜ FİLTRESİ
    // ============================================================

    private void setupTypeFilter() {

        ArrayAdapter<String> adapter =
                new ArrayAdapter<String>(
                        this,
                        android.R.layout.simple_spinner_item,
                        typeNames
                );


        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );


        spinnerType.setAdapter(
                adapter
        );
    }


    // ============================================================
    // LISTENER'LAR
    // ============================================================

    private void setupListeners() {

        spinnerGarden.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {

                        refreshOperations();
                    }


                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent
                    ) {
                    }
                }
        );


        spinnerType.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {

                        refreshOperations();
                    }


                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent
                    ) {
                    }
                }
        );
    }


    // ============================================================
    // İŞLEMLERİ YENİLE
    // ============================================================

    private void refreshOperations() {

        if (spinnerGarden == null
                || spinnerType == null
                || containerOperations == null) {

            return;
        }


        Integer gardenId =
                null;


        int gardenPosition =
                spinnerGarden
                        .getSelectedItemPosition();


        if (gardenPosition > 0
                && gardenPosition < gardenIds.size()) {

            gardenId =
                    gardenIds.get(
                            gardenPosition
                    );
        }


        String type =
                null;


        int typePosition =
                spinnerType
                        .getSelectedItemPosition();


        if (typePosition > 0
                && typePosition < typeNames.length) {

            type =
                    typeNames[
                            typePosition
                            ];
        }


        loadOperations(
                gardenId,
                type
        );
    }


    // ============================================================
    // İŞLEM LİSTESİNİ YÜKLE
    // ============================================================

    private void loadOperations(
            Integer gardenId,
            String type
    ) {

        containerOperations.removeAllViews();


        Cursor cursor =
                databaseHelper
                        .getOperationsFiltered(
                                gardenId,
                                type
                        );


        int count =
                0;


        double totalCost =
                0;


        if (!cursor.moveToFirst()) {

            cursor.close();


            tvRecordCount.setText(
                    "0"
            );


            tvFilteredCost.setText(
                    "0 TL"
            );


            TextView empty =
                    createText(
                            "Bu filtreye ait işlem kaydı bulunamadı.",
                            14,
                            COLOR_GRAY,
                            false
                    );


            empty.setGravity(
                    Gravity.CENTER
            );


            empty.setPadding(
                    dp(10),
                    dp(30),
                    dp(10),
                    dp(30)
            );


            containerOperations.addView(
                    empty
            );


            return;
        }


        do {

            count++;


            final String gardenName =
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                    "garden_name"
                            )
                    );


            final String operationType =
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                    "type"
                            )
                    );


            final String operationDate =
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                    "operation_date"
                            )
                    );


            final double quantity =
                    cursor.getDouble(
                            cursor.getColumnIndexOrThrow(
                                    "quantity"
                            )
                    );


            final String unit =
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                    "unit"
                            )
                    );


            final double cost =
                    cursor.getDouble(
                            cursor.getColumnIndexOrThrow(
                                    "cost"
                            )
                    );


            final String note =
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                    "note"
                            )
                    );


            totalCost +=
                    cost;


            View card =
                    createOperationCard(
                            gardenName,
                            operationType,
                            operationDate,
                            quantity,
                            unit,
                            cost,
                            note
                    );


            containerOperations.addView(
                    card
            );


        } while (cursor.moveToNext());


        cursor.close();


        tvRecordCount.setText(
                String.valueOf(
                        count
                )
        );


        tvFilteredCost.setText(
                formatMoney(
                        totalCost
                )
        );
    }


    // ============================================================
    // İŞLEM KARTI
    // ============================================================

    private View createOperationCard(
            final String gardenName,
            final String type,
            final String date,
            final double quantity,
            final String unit,
            final double cost,
            final String note
    ) {

        LinearLayout card =
                new LinearLayout(
                        this
                );


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
                dp(9);


        card.setLayoutParams(
                cardParams
        );


        // ========================================================
        // ÜST SATIR
        // ========================================================

        LinearLayout top =
                new LinearLayout(
                        this
                );


        top.setOrientation(
                LinearLayout.HORIZONTAL
        );


        top.setGravity(
                Gravity.CENTER_VERTICAL
        );


        TextView icon =
                createText(
                        operationIcon(
                                type
                        ),
                        24,
                        COLOR_DARK_GREEN,
                        false
                );


        top.addView(
                icon
        );


        LinearLayout titleBox =
                new LinearLayout(
                        this
                );


        titleBox.setOrientation(
                LinearLayout.VERTICAL
        );


        LinearLayout.LayoutParams boxParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                );


        boxParams.leftMargin =
                dp(11);


        titleBox.addView(
                createText(
                        safeText(
                                gardenName,
                                "Bahçe"
                        ),
                        16,
                        COLOR_DARK_GREEN,
                        true
                )
        );


        titleBox.addView(
                createText(
                        safeText(
                                type,
                                "İşlem"
                        )
                                + " • "
                                + safeText(
                                date,
                                "—"
                        ),
                        12,
                        COLOR_GRAY,
                        false
                ),
                topParams(2)
        );


        top.addView(
                titleBox,
                boxParams
        );


        if (cost > 0) {

            top.addView(
                    createText(
                            formatMoney(
                                    cost
                            ),
                            15,
                            COLOR_GREEN,
                            true
                    )
            );
        }


        card.addView(
                top
        );


        // ========================================================
        // MİKTAR
        // ========================================================

        if (quantity > 0) {

            String quantityText =
                    formatNumber(
                            quantity
                    );


            if (!isEmpty(
                    unit
            )) {

                quantityText +=
                        " "
                                + unit;
            }


            card.addView(
                    createText(
                            "Miktar: "
                                    + quantityText,
                            12,
                            COLOR_TEXT,
                            false
                    ),
                    topParams(8)
            );
        }


        // ========================================================
        // NOT
        // ========================================================

        if (!isEmpty(
                note
        )) {

            card.addView(
                    createText(
                            note,
                            12,
                            COLOR_GRAY,
                            false
                    ),
                    topParams(5)
            );
        }


        // ========================================================
        // DETAY
        // ========================================================

        TextView detail =
                createText(
                        "Detay ›",
                        12,
                        COLOR_GREEN,
                        true
                );


        detail.setGravity(
                Gravity.END
        );


        card.addView(
                detail,
                topParams(8)
        );


        card.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(
                            View view
                    ) {

                        showOperationDetail(
                                gardenName,
                                type,
                                date,
                                quantity,
                                unit,
                                cost,
                                note
                        );
                    }
                }
        );


        return card;
    }


    // ============================================================
    // İŞLEM DETAY DIALOG
    // ============================================================

    private void showOperationDetail(
            String gardenName,
            String type,
            String date,
            double quantity,
            String unit,
            double cost,
            String note
    ) {

        LinearLayout content =
                new LinearLayout(
                        this
                );


        content.setOrientation(
                LinearLayout.VERTICAL
        );


        content.setPadding(
                dp(20),
                dp(8),
                dp(20),
                dp(8)
        );


        content.addView(
                createText(
                        operationIcon(
                                type
                        )
                                + " "
                                + safeText(
                                type,
                                "İşlem"
                        ),
                        21,
                        COLOR_DARK_GREEN,
                        true
                )
        );


        content.addView(
                createDetailLine(
                        "Bahçe",
                        gardenName
                ),
                topParams(16)
        );


        content.addView(
                createDetailLine(
                        "Tarih",
                        date
                ),
                topParams(9)
        );


        if (quantity > 0) {

            String quantityText =
                    formatNumber(
                            quantity
                    );


            if (!isEmpty(
                    unit
            )) {

                quantityText +=
                        " "
                                + unit;
            }


            content.addView(
                    createDetailLine(
                            "Miktar",
                            quantityText
                    ),
                    topParams(9)
            );
        }


        content.addView(
                createDetailLine(
                        "Masraf",
                        formatMoney(
                                cost
                        )
                ),
                topParams(9)
        );


        if (!isEmpty(
                note
        )) {

            content.addView(
                    createText(
                            "Not",
                            12,
                            COLOR_GRAY,
                            true
                    ),
                    topParams(15)
            );


            content.addView(
                    createText(
                            note,
                            14,
                            COLOR_TEXT,
                            false
                    ),
                    topParams(4)
            );
        }


        new AlertDialog.Builder(
                this
        )
                .setTitle(
                        "İşlem Detayı"
                )
                .setView(
                        content
                )
                .setPositiveButton(
                        "Kapat",
                        null
                )
                .show();
    }


    // ============================================================
    // DETAY SATIRI
    // ============================================================

    private View createDetailLine(
            String label,
            String value
    ) {

        LinearLayout row =
                new LinearLayout(
                        this
                );


        row.setOrientation(
                LinearLayout.HORIZONTAL
        );


        TextView labelView =
                createText(
                        label,
                        13,
                        COLOR_GRAY,
                        false
                );


        TextView valueView =
                createText(
                        safeText(
                                value,
                                "—"
                        ),
                        14,
                        COLOR_DARK_GREEN,
                        true
                );


        valueView.setGravity(
                Gravity.END
        );


        row.addView(
                labelView,

                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                )
        );


        row.addView(
                valueView
        );


        return row;
    }


    // ============================================================
    // YENİ İŞLEM EKRANI
    // ============================================================

    private void openNewOperation() {

        Intent intent =
                new Intent();


        intent.setClassName(
                "com.zeytindefteri.app",
                "com.zeytindefteri.app.NewOperationActivity"
        );


        startActivity(
                intent
        );
    }


    // ============================================================
    // İŞLEM İKONU
    // ============================================================

    private String operationIcon(
            String type
    ) {

        if ("Sulama".equals(
                type
        )) {

            return "💧";
        }


        if ("Gübre".equals(
                type
        )) {

            return "🌱";
        }


        if ("İlaçlama".equals(
                type
        )) {

            return "🧪";
        }


        if ("Budama".equals(
                type
        )) {

            return "✂️";
        }


        if ("Sinek Tuzağı".equals(
                type
        )) {

            return "🪰";
        }


        if ("Toprak İşleme".equals(
                type
        )) {

            return "🚜";
        }


        if ("İşçilik".equals(
                type
        )) {

            return "👷";
        }


        if ("Mazot".equals(
                type
        )) {

            return "⛽";
        }


        if ("Nakliye".equals(
                type
        )) {

            return "🚚";
        }


        if ("Hasat".equals(
                type
        )) {

            return "🧺";
        }


        return "📝";
    }


    // ============================================================
    // UI YARDIMCILARI
    // ============================================================

    private LinearLayout createCard(
            int color
    ) {

        LinearLayout card =
                new LinearLayout(
                        this
                );


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
                new LinearLayout(
                        this
                );


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


    private TextView createButton(
            String text
    ) {

        TextView button =
                createText(
                        text,
                        16,
                        COLOR_WHITE,
                        true
                );


        button.setGravity(
                Gravity.CENTER
        );


        button.setBackground(
                roundedBackground(
                        COLOR_GREEN,
                        14,
                        COLOR_GREEN
                )
        );


        button.setElevation(
                dp(4)
        );


        return button;
    }


    private TextView createText(
            String text,
            float size,
            int color,
            boolean bold
    ) {

        TextView view =
                new TextView(
                        this
                );


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
                16,
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


    private LinearLayout.LayoutParams fixedHeightTop(
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

    private String formatMoney(
            double value
    ) {

        return formatNumber(
                value
        )
                + " TL";
    }


    private String formatNumber(
            double value
    ) {

        NumberFormat format =
                NumberFormat.getNumberInstance(
                        TR
                );


        format.setMinimumFractionDigits(
                0
        );


        format.setMaximumFractionDigits(
                2
        );


        return format.format(
                value
        );
    }


    // ============================================================
    // STRING
    // ============================================================

    private boolean isEmpty(
            String text
    ) {

        return text == null
                ||
                text.trim().isEmpty();
    }


    private String safeText(
            String text,
            String fallback
    ) {

        if (isEmpty(
                text
        )) {

            return fallback;
        }


        return text;
    }


    // ============================================================
    // DP
    // ============================================================

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
}
