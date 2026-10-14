package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class BrineActivity extends Activity {

    // ============================================================
    // DATABASE
    // ============================================================

    private DatabaseHelper databaseHelper;


    // ============================================================
    // FORMAT
    // ============================================================

    private final Locale TR =
            new Locale("tr", "TR");


    // ============================================================
    // ANA EKRAN
    // ============================================================

    private TextView tvActiveBatchCount;
    private TextView tvTotalOliveKg;
    private TextView tvReadyKg;
    private TextView tvTotalCost;

    private LinearLayout containerBatches;


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

    // Yalnızca renkli düğme/rozet üstündeki yazı için: açık zeminde koyu yazı (yeni düğme dili)
    private final int COLOR_WHITE =
            GreenTheme.color(R.color.zd_bg);

    private final int COLOR_TEXT =
            GreenTheme.color(R.color.zd_text);

    private final int COLOR_GRAY =
            GreenTheme.color(R.color.zd_muted);

    private final int COLOR_BORDER =
            GreenTheme.color(R.color.zd_border);

    private final int COLOR_WARNING =
            GreenTheme.color(R.color.zd_warning);


    // ============================================================
    // ELEKLER
    // ============================================================

    private final String[] gradeNames = {

            "Double",
            "1 No",
            "2 No",
            "3 No",
            "4 No",
            "Yağlık",
            "Karışık"
    };


    private final String[] gradeCodes = {

            "DOUBLE",
            "1",
            "2",
            "3",
            "4",
            "YAGLIK",
            "KARISIK"
    };


    // ============================================================
    // SALAMURA HAREKETLERİ
    // ============================================================

    private final String[] eventNames = {

            "Su Değişimi",
            "Tuz İlavesi",
            "Kontrol",
            "Tat Kontrolü",
            "Ambalajlama"
    };


    private final String[] eventCodes = {

            "WATER_CHANGE",
            "SALT_ADDED",
            "CONTROL",
            "TASTE_TEST",
            "PACKAGING"
    };


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
                createMainScreen()
        );


        refreshScreen();
    }


    // ============================================================
    // GERİ DÖNÜNCE YENİLE
    // ============================================================

    @Override
    protected void onResume() {

        super.onResume();


        if (databaseHelper != null
                && containerBatches != null) {

            refreshScreen();
        }
    }


    // ============================================================
    // ANA EKRANI OLUŞTUR
    // ============================================================

    private View createMainScreen() {

        ScrollView scrollView =
                new ScrollView(this);


        scrollView.setFillViewport(
                true
        );


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
                        "Salamura",
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
                        "Bidonlarını, maliyetini ve olgunlaşmayı takip et",
                        14,
                        GreenTheme.color(R.color.zd_muted),
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
                titleParams
        );


        root.addView(
                header
        );


        // ========================================================
        // SEZON ÖZETİ
        // ========================================================

        LinearLayout summaryCard =
                createCard(
                        COLOR_LIGHT_GREEN
                );


        summaryCard.addView(
                createText(
                        "🫙 Salamura Özeti",
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


        LinearLayout activeBox =
                createStatBox(
                        "Aktif Parti"
                );


        tvActiveBatchCount =
                createText(
                        "0",
                        24,
                        COLOR_DARK_GREEN,
                        true
                );


        activeBox.addView(
                tvActiveBatchCount
        );


        LinearLayout oliveBox =
                createStatBox(
                        "Salamuraya Giren"
                );


        tvTotalOliveKg =
                createText(
                        "0 kg",
                        22,
                        COLOR_DARK_GREEN,
                        true
                );


        oliveBox.addView(
                tvTotalOliveKg
        );


        row1.addView(
                activeBox,
                weightParams()
        );


        row1.addView(
                oliveBox,
                weightParams()
        );


        summaryCard.addView(
                row1,
                topParams(16)
        );


        LinearLayout row2 =
                new LinearLayout(this);


        row2.setOrientation(
                LinearLayout.HORIZONTAL
        );


        LinearLayout readyBox =
                createStatBox(
                        "Hazır Ürün"
                );


        tvReadyKg =
                createText(
                        "0 kg",
                        22,
                        COLOR_DARK_GREEN,
                        true
                );


        readyBox.addView(
                tvReadyKg
        );


        LinearLayout costBox =
                createStatBox(
                        "Toplam Maliyet"
                );


        tvTotalCost =
                createText(
                        "0 TL",
                        20,
                        COLOR_DARK_GREEN,
                        true
                );


        costBox.addView(
                tvTotalCost
        );


        row2.addView(
                readyBox,
                weightParams()
        );


        row2.addView(
                costBox,
                weightParams()
        );


        summaryCard.addView(
                row2,
                topParams(16)
        );


        root.addView(
                summaryCard,
                topParams(22)
        );


        // ========================================================
        // YENİ PARTİ BUTONU
        // ========================================================

        TextView btnNewBatch =
                createButton(
                        "+ Yeni Salamura Partisi"
                );


        btnNewBatch.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {

                        showNewBatchDialog();
                    }
                }
        );


        root.addView(
                btnNewBatch,
                fixedHeightTop(
                        58,
                        14
                )
        );


        // ========================================================
        // PARTİLER
        // ========================================================

        LinearLayout batchesCard =
                createCard(
                        COLOR_CARD
                );


        batchesCard.addView(
                createText(
                        "Partilerim",
                        21,
                        COLOR_DARK_GREEN,
                        true
                )
        );


        batchesCard.addView(
                createText(
                        "Aktif, hazır ve tamamlanan bidonların burada görünür.",
                        12,
                        COLOR_GRAY,
                        false
                ),
                topParams(4)
        );


        containerBatches =
                new LinearLayout(this);


        containerBatches.setOrientation(
                LinearLayout.VERTICAL
        );


        batchesCard.addView(
                containerBatches,
                topParams(10)
        );


        root.addView(
                batchesCard,
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
                        "💡 Salamura Defteri",
                        15,
                        COLOR_DARK_GREEN,
                        true
                )
        );


        infoCard.addView(
                createText(
                        "Bir bidonu parti olarak kaydet. Daha sonra su değişimi, " +
                                "tuz ilavesi, tat kontrolü ve ambalajlama gibi hareketleri " +
                                "aynı partiye ekleyebilirsin.",
                        12,
                        GreenTheme.color(R.color.zd_muted),
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
    // EKRANI YENİLE
    // ============================================================

    private void refreshScreen() {

        tvActiveBatchCount.setText(
                String.valueOf(
                        databaseHelper
                                .getActiveBrineBatchCount()
                )
        );


        tvTotalOliveKg.setText(
                formatKg(
                        databaseHelper
                                .getTotalBrineOliveKg()
                )
        );


        tvReadyKg.setText(
                formatKg(
                        databaseHelper
                                .getTotalReadyBrineKg()
                )
        );


        tvTotalCost.setText(
                formatMoney(
                        databaseHelper
                                .getTotalBrineCost()
                )
        );


        loadBatches();
    }


    // ============================================================
    // PARTİLERİ YÜKLE
    // ============================================================

    private void loadBatches() {

        containerBatches.removeAllViews();


        Cursor cursor =
                databaseHelper
                        .getBrineBatches();


        if (!cursor.moveToFirst()) {

            cursor.close();


            TextView empty =
                    createText(
                            "Henüz salamura partisi yok.\nİlk bidonunu ekleyebilirsin.",
                            14,
                            COLOR_GRAY,
                            false
                    );


            empty.setGravity(
                    Gravity.CENTER
            );


            empty.setPadding(
                    dp(8),
                    dp(28),
                    dp(8),
                    dp(28)
            );


            containerBatches.addView(
                    empty
            );


            return;
        }


        do {

            final long batchId =
                    cursor.getLong(
                            cursor.getColumnIndexOrThrow(
                                    "id"
                            )
                    );


            String batchName =
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                    "batch_name"
                            )
                    );


            String gardenName =
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                    "garden_name"
                            )
                    );


            String startDate =
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                    "start_date"
                            )
                    );


            String expectedDate =
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                    "expected_ready_date"
                            )
                    );


            String variety =
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                    "variety"
                            )
                    );


            String grade =
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                    "grade"
                            )
                    );


            String status =
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                    "status"
                            )
                    );


            double oliveKg =
                    cursor.getDouble(
                            cursor.getColumnIndexOrThrow(
                                    "olive_kg"
                            )
                    );


            double readyKg =
                    cursor.getDouble(
                            cursor.getColumnIndexOrThrow(
                                    "ready_kg"
                            )
                    );


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


            LinearLayout topRow =
                    new LinearLayout(this);


            topRow.setOrientation(
                    LinearLayout.HORIZONTAL
            );


            topRow.setGravity(
                    Gravity.CENTER_VERTICAL
            );


            TextView nameView =
                    createText(
                            safeText(
                                    batchName,
                                    "Salamura Partisi"
                            ),
                            17,
                            COLOR_DARK_GREEN,
                            true
                    );


            topRow.addView(
                    nameView,
                    new LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1
                    )
            );


            TextView statusView =
                    createStatusView(
                            status
                    );


            topRow.addView(
                    statusView
            );


            card.addView(
                    topRow
            );


            StringBuilder detail =
                    new StringBuilder();


            if (!isEmpty(gardenName)) {

                detail.append(
                        gardenName
                );

                detail.append(
                        "\n"
                );
            }


            if (!isEmpty(variety)) {

                detail.append(
                        variety
                );
            }


            if (!isEmpty(grade)) {

                if (detail.length() > 0
                        && detail.charAt(
                        detail.length() - 1
                ) != '\n') {

                    detail.append(
                            " • "
                    );
                }


                detail.append(
                        gradeDisplay(
                                grade
                        )
                );
            }


            if (detail.length() > 0) {

                detail.append(
                        "\n"
                );
            }


            detail.append(
                    formatKg(
                            oliveKg
                    )
            );


            detail.append(
                    " zeytin"
            );


            if (readyKg > 0) {

                detail.append(
                        " • "
                );

                detail.append(
                        formatKg(
                                readyKg
                        )
                );

                detail.append(
                        " hazır"
                );
            }


            card.addView(
                    createText(
                            detail.toString(),
                            13,
                            COLOR_TEXT,
                            false
                    ),
                    topParams(7)
            );


            String dateText =
                    "Başlangıç: "
                            + safeText(
                            startDate,
                            "—"
                    );


            if (!isEmpty(expectedDate)) {

                dateText +=
                        "   •   Tahmini hazır: "
                                + expectedDate;
            }


            card.addView(
                    createText(
                            dateText,
                            11,
                            COLOR_GRAY,
                            false
                    ),
                    topParams(7)
            );


            double totalCost =
                    databaseHelper
                            .getBrineBatchTotalCost(
                                    batchId
                            );


            card.addView(
                    createText(
                            "Maliyet: "
                                    + formatMoney(
                                    totalCost
                            ),
                            13,
                            COLOR_GREEN,
                            true
                    ),
                    topParams(8)
            );


            TextView detailButton =
                    createText(
                            "Partiyi Aç  ›",
                            13,
                            COLOR_GREEN,
                            true
                    );


            detailButton.setGravity(
                    Gravity.END
            );


            card.addView(
                    detailButton,
                    topParams(9)
            );


            card.setOnClickListener(
                    new View.OnClickListener() {

                        @Override
                        public void onClick(View view) {

                            showBatchDetail(
                                    batchId
                            );
                        }
                    }
            );


            containerBatches.addView(
                    card
            );


        } while (cursor.moveToNext());


        cursor.close();
    }


    // ============================================================
    // YENİ PARTİ
    // ============================================================

    private void showNewBatchDialog() {

        final ScrollView dialogScroll =
                new ScrollView(this);


        final LinearLayout form =
                new LinearLayout(this);


        form.setOrientation(
                LinearLayout.VERTICAL
        );


        form.setPadding(
                dp(18),
                dp(8),
                dp(18),
                dp(12)
        );


        dialogScroll.addView(
                form
        );


        // ========================================================
        // BAHÇE
        // ========================================================

        addFormLabel(
                form,
                "Bahçe"
        );


        final Spinner spinnerGarden =
                new Spinner(this);


        final ArrayList<Integer> gardenIds =
                new ArrayList<Integer>();


        final ArrayList<String> gardenNames =
                new ArrayList<String>();


        gardenIds.add(
                -1
        );


        gardenNames.add(
                "Bahçe seçmeden devam et"
        );


        Cursor gardenCursor =
                databaseHelper
                        .getGardens();


        while (gardenCursor.moveToNext()) {

            gardenIds.add(
                    gardenCursor.getInt(
                            gardenCursor
                                    .getColumnIndexOrThrow(
                                            "id"
                                    )
                    )
            );


            gardenNames.add(
                    gardenCursor.getString(
                            gardenCursor
                                    .getColumnIndexOrThrow(
                                            "name"
                                    )
                    )
            );
        }


        gardenCursor.close();


        ArrayAdapter<String> gardenAdapter =
                new ArrayAdapter<String>(
                        this,
                        android.R.layout.simple_spinner_item,
                        gardenNames
                );


        gardenAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );


        spinnerGarden.setAdapter(
                gardenAdapter
        );


        spinnerGarden.setBackground(
                fieldBackground()
        );


        form.addView(
                spinnerGarden,
                fixedHeightTop(
                        54,
                        6
                )
        );


        // ========================================================
        // PARTİ ADI
        // ========================================================

        addFormLabel(
                form,
                "Parti / Bidon Adı *"
        );


        final EditText etBatchName =
                createEditText(
                        "Örn. 200 L Mavi Bidon 1",
                        false
                );


        form.addView(
                etBatchName,
                fixedHeightTop(
                        54,
                        6
                )
        );


        // ========================================================
        // BAŞLANGIÇ TARİHİ
        // ========================================================

        addFormLabel(
                form,
                "Başlangıç Tarihi *"
        );


        final EditText etStartDate =
                createDateField(
                        today()
                );


        form.addView(
                etStartDate,
                fixedHeightTop(
                        54,
                        6
                )
        );


        etStartDate.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {

                        showDatePicker(
                                etStartDate
                        );
                    }
                }
        );


        // ========================================================
        // TAHMİNİ HAZIR TARİH
        // ========================================================

        addFormLabel(
                form,
                "Tahmini Hazır Olma Tarihi"
        );


        Calendar estimated =
                Calendar.getInstance();


        estimated.add(
                Calendar.DAY_OF_YEAR,
                90
        );


        final EditText etReadyDate =
                createDateField(
                        formatDate(
                                estimated
                        )
                );


        form.addView(
                etReadyDate,
                fixedHeightTop(
                        54,
                        6
                )
        );


        etReadyDate.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {

                        showDatePicker(
                                etReadyDate
                        );
                    }
                }
        );


        // ========================================================
        // ÇEŞİT
        // ========================================================

        addFormLabel(
                form,
                "Zeytin Çeşidi"
        );


        final EditText etVariety =
                createEditText(
                        "Domat, Gemlik, Uslu...",
                        false
                );


        form.addView(
                etVariety,
                fixedHeightTop(
                        54,
                        6
                )
        );


        // ========================================================
        // ELEK
        // ========================================================

        addFormLabel(
                form,
                "Elek"
        );


        final Spinner spinnerGrade =
                new Spinner(this);


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


        spinnerGrade.setBackground(
                fieldBackground()
        );


        form.addView(
                spinnerGrade,
                fixedHeightTop(
                        54,
                        6
                )
        );


        // ========================================================
        // KAPASİTE
        // ========================================================

        addFormLabel(
                form,
                "Bidon Kapasitesi"
        );


        final EditText etContainerLiter =
                createNumberEditText(
                        "Örn. 200 litre"
                );


        form.addView(
                etContainerLiter,
                fixedHeightTop(
                        54,
                        6
                )
        );


        // ========================================================
        // ZEYTİN
        // ========================================================

        addFormLabel(
                form,
                "Zeytin Miktarı (kg) *"
        );


        final EditText etOliveKg =
                createNumberEditText(
                        "Örn. 62"
                );


        form.addView(
                etOliveKg,
                fixedHeightTop(
                        54,
                        6
                )
        );


        // ========================================================
        // TUZ
        // ========================================================

        addFormLabel(
                form,
                "Tuz Miktarı (kg)"
        );


        final EditText etSaltKg =
                createNumberEditText(
                        "Örn. 8"
                );


        form.addView(
                etSaltKg,
                fixedHeightTop(
                        54,
                        6
                )
        );


        // ========================================================
        // SU
        // ========================================================

        addFormLabel(
                form,
                "Su Miktarı (L)"
        );


        final EditText etWaterLiter =
                createNumberEditText(
                        "Örn. 75"
                );


        form.addView(
                etWaterLiter,
                fixedHeightTop(
                        54,
                        6
                )
        );


        // ========================================================
        // TUZ MALİYETİ
        // ========================================================

        addFormLabel(
                form,
                "Tuz Maliyeti (TL)"
        );


        final EditText etSaltCost =
                createNumberEditText(
                        "Örn. 300"
                );


        form.addView(
                etSaltCost,
                fixedHeightTop(
                        54,
                        6
                )
        );


        // ========================================================
        // AMBALAJ MALİYETİ
        // ========================================================

        addFormLabel(
                form,
                "Ambalaj / Bidon Maliyeti (TL)"
        );


        final EditText etPackagingCost =
                createNumberEditText(
                        "Örn. 450"
                );


        form.addView(
                etPackagingCost,
                fixedHeightTop(
                        54,
                        6
                )
        );


        // ========================================================
        // DİĞER MALİYET
        // ========================================================

        addFormLabel(
                form,
                "Diğer Maliyet (TL)"
        );


        final EditText etOtherCost =
                createNumberEditText(
                        "Varsa diğer masraflar"
                );


        form.addView(
                etOtherCost,
                fixedHeightTop(
                        54,
                        6
                )
        );


        // ========================================================
        // NOT
        // ========================================================

        addFormLabel(
                form,
                "Not"
        );


        final EditText etNote =
                createEditText(
                        "Salamura ile ilgili not...",
                        true
                );


        form.addView(
                etNote,
                multilineParams(
                        110,
                        6
                )
        );


        // ========================================================
        // DIALOG
        // ========================================================

        final AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "Yeni Salamura Partisi"
                        )
                        .setView(
                                dialogScroll
                        )
                        .setNegativeButton(
                                "Vazgeç",
                                null
                        )
                        .setPositiveButton(
                                "Kaydet",
                                null
                        )
                        .create();


        dialog.setOnShowListener(
                new DialogInterface.OnShowListener() {

                    @Override
                    public void onShow(
                            DialogInterface dialogInterface
                    ) {

                        dialog
                                .getButton(
                                        AlertDialog.BUTTON_POSITIVE
                                )
                                .setOnClickListener(
                                        new View.OnClickListener() {

                                            @Override
                                            public void onClick(
                                                    View view
                                            ) {

                                                String batchName =
                                                        text(
                                                                etBatchName
                                                        );


                                                if (batchName.isEmpty()) {

                                                    etBatchName.setError(
                                                            "Parti adı gerekli"
                                                    );

                                                    etBatchName.requestFocus();

                                                    return;
                                                }


                                                double oliveKg =
                                                        readNumber(
                                                                etOliveKg
                                                        );


                                                if (oliveKg <= 0) {

                                                    etOliveKg.setError(
                                                            "Zeytin miktarı gerekli"
                                                    );

                                                    etOliveKg.requestFocus();

                                                    return;
                                                }


                                                Integer gardenId =
                                                        null;


                                                int gardenPosition =
                                                        spinnerGarden
                                                                .getSelectedItemPosition();


                                                if (gardenPosition > 0
                                                        && gardenPosition
                                                        < gardenIds.size()) {

                                                    gardenId =
                                                            gardenIds.get(
                                                                    gardenPosition
                                                            );
                                                }


                                                int gradePosition =
                                                        spinnerGrade
                                                                .getSelectedItemPosition();


                                                String grade =
                                                        gradeCodes[
                                                                gradePosition
                                                                ];


                                                long result =
                                                        databaseHelper
                                                                .addBrineBatch(
                                                                        gardenId,
                                                                        batchName,
                                                                        text(
                                                                                etStartDate
                                                                        ),
                                                                        text(
                                                                                etReadyDate
                                                                        ),
                                                                        text(
                                                                                etVariety
                                                                        ),
                                                                        grade,
                                                                        readNumber(
                                                                                etContainerLiter
                                                                        ),
                                                                        oliveKg,
                                                                        readNumber(
                                                                                etSaltKg
                                                                        ),
                                                                        readNumber(
                                                                                etWaterLiter
                                                                        ),
                                                                        readNumber(
                                                                                etSaltCost
                                                                        ),
                                                                        readNumber(
                                                                                etPackagingCost
                                                                        ),
                                                                        readNumber(
                                                                                etOtherCost
                                                                        ),
                                                                        text(
                                                                                etNote
                                                                        )
                                                                );


                                                if (result != -1) {

                                                    Toast.makeText(
                                                            BrineActivity.this,
                                                            "Salamura partisi kaydedildi ✓",
                                                            Toast.LENGTH_SHORT
                                                    ).show();


                                                    syncBrineCloud(
                                                            result
                                                    );


                                                    dialog.dismiss();

                                                    refreshScreen();

                                                } else {

                                                    Toast.makeText(
                                                            BrineActivity.this,
                                                            "Parti kaydedilemedi",
                                                            Toast.LENGTH_LONG
                                                    ).show();
                                                }
                                            }
                                        }
                                );
                    }
                }
        );


        dialog.show();
    }


    // ============================================================
    // PARTİ DETAYI
    // ============================================================

    private void showBatchDetail(
            final long batchId
    ) {

        Cursor cursor =
                databaseHelper
                        .getBrineBatch(
                                batchId
                        );


        if (!cursor.moveToFirst()) {

            cursor.close();

            Toast.makeText(
                    this,
                    "Salamura partisi bulunamadı",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        final String batchName =
                cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                "batch_name"
                        )
                );


        String gardenName =
                cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                "garden_name"
                        )
                );


        String startDate =
                cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                "start_date"
                        )
                );


        String expectedDate =
                cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                "expected_ready_date"
                        )
                );


        String variety =
                cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                "variety"
                        )
                );


        String grade =
                cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                "grade"
                        )
                );


        final String status =
                cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                "status"
                        )
                );


        double containerLiter =
                cursor.getDouble(
                        cursor.getColumnIndexOrThrow(
                                "container_liter"
                        )
                );


        double oliveKg =
                cursor.getDouble(
                        cursor.getColumnIndexOrThrow(
                                "olive_kg"
                        )
                );


        double saltKg =
                cursor.getDouble(
                        cursor.getColumnIndexOrThrow(
                                "salt_kg"
                        )
                );


        double waterLiter =
                cursor.getDouble(
                        cursor.getColumnIndexOrThrow(
                                "water_liter"
                        )
                );


        double readyKg =
                cursor.getDouble(
                        cursor.getColumnIndexOrThrow(
                                "ready_kg"
                        )
                );


        String note =
                cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                "note"
                        )
                );


        cursor.close();


        ScrollView scroll =
                new ScrollView(this);


        LinearLayout content =
                new LinearLayout(this);


        content.setOrientation(
                LinearLayout.VERTICAL
        );


        content.setPadding(
                dp(18),
                dp(8),
                dp(18),
                dp(16)
        );


        scroll.addView(
                content
        );


        // ========================================================
        // DURUM
        // ========================================================

        content.addView(
                createStatusView(
                        status
                )
        );


        StringBuilder info =
                new StringBuilder();


        if (!isEmpty(gardenName)) {

            info.append(
                    "Bahçe: "
            );

            info.append(
                    gardenName
            );

            info.append(
                    "\n"
            );
        }


        if (!isEmpty(variety)) {

            info.append(
                    "Çeşit: "
            );

            info.append(
                    variety
            );

            info.append(
                    "\n"
            );
        }


        if (!isEmpty(grade)) {

            info.append(
                    "Elek: "
            );

            info.append(
                    gradeDisplay(
                            grade
                    )
            );

            info.append(
                    "\n"
            );
        }


        info.append(
                "Başlangıç: "
        );

        info.append(
                safeText(
                        startDate,
                        "—"
                )
        );


        if (!isEmpty(expectedDate)) {

            info.append(
                    "\nTahmini hazır: "
            );

            info.append(
                    expectedDate
            );
        }


        info.append(
                "\n\nBidon: "
        );

        info.append(
                formatNumber(
                        containerLiter
                )
        );

        info.append(
                " L"
        );


        info.append(
                "\nZeytin: "
        );

        info.append(
                formatKg(
                        oliveKg
                )
        );


        info.append(
                "\nTuz: "
        );

        info.append(
                formatNumber(
                        saltKg
                )
        );

        info.append(
                " kg"
        );


        info.append(
                "\nSu: "
        );

        info.append(
                formatNumber(
                        waterLiter
                )
        );

        info.append(
                " L"
        );


        if (readyKg > 0) {

            info.append(
                    "\nHazır ürün: "
            );

            info.append(
                    formatKg(
                            readyKg
                    )
            );
        }


        content.addView(
                createText(
                        info.toString(),
                        14,
                        COLOR_TEXT,
                        false
                ),
                topParams(14)
        );


        double totalCost =
                databaseHelper
                        .getBrineBatchTotalCost(
                                batchId
                        );


        content.addView(
                createText(
                        "Toplam Maliyet: "
                                + formatMoney(
                                totalCost
                        ),
                        17,
                        COLOR_DARK_GREEN,
                        true
                ),
                topParams(15)
        );


        if (readyKg > 0) {

            content.addView(
                    createText(
                            "Hazır ürün maliyeti: "
                                    + formatMoney(
                                    totalCost / readyKg
                            )
                                    + " / kg",
                            13,
                            COLOR_GREEN,
                            true
                    ),
                    topParams(4)
            );
        }


        if (!isEmpty(note)) {

            content.addView(
                    createText(
                            "Not: "
                                    + note,
                            12,
                            COLOR_GRAY,
                            false
                    ),
                    topParams(12)
            );
        }


        // ========================================================
        // HAREKET EKLE
        // ========================================================

        TextView btnEvent =
                createSecondaryButton(
                        "+ Hareket Ekle"
                );


        btnEvent.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {

                        showAddEventDialog(
                                batchId,
                                batchName
                        );
                    }
                }
        );


        content.addView(
                btnEvent,
                fixedHeightTop(
                        52,
                        18
                )
        );


        // ========================================================
        // HAZIR İŞARETLE
        // ========================================================

        TextView btnReady =
                createSecondaryButton(
                        "Hazır Ürün Kg Gir"
                );


        btnReady.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {

                        showReadyKgDialog(
                                batchId
                        );
                    }
                }
        );


        content.addView(
                btnReady,
                fixedHeightTop(
                        52,
                        9
                )
        );


        // ========================================================
        // PARTİ KAPAT
        // ========================================================

        if (!"CLOSED".equals(
                status
        )) {

            TextView btnClose =
                    createSecondaryButton(
                            "Partiyi Kapat"
                    );


            btnClose.setOnClickListener(
                    new View.OnClickListener() {

                        @Override
                        public void onClick(View view) {

                            databaseHelper
                                    .updateBrineStatus(
                                            batchId,
                                            "CLOSED"
                                    );


                            Toast.makeText(
                                    BrineActivity.this,
                                    "Parti kapatıldı",
                                    Toast.LENGTH_SHORT
                            ).show();


                            syncBrineCloud(
                                    batchId
                            );


                            refreshScreen();
                        }
                    }
            );


            content.addView(
                    btnClose,
                    fixedHeightTop(
                            52,
                            9
                    )
            );
        }


        // ========================================================
        // HAREKET GEÇMİŞİ
        // ========================================================

        content.addView(
                createText(
                        "Hareket Geçmişi",
                        18,
                        COLOR_DARK_GREEN,
                        true
                ),
                topParams(22)
        );


        Cursor events =
                databaseHelper
                        .getBrineEvents(
                                batchId
                        );


        if (!events.moveToFirst()) {

            content.addView(
                    createText(
                            "Henüz hareket kaydı yok.",
                            13,
                            COLOR_GRAY,
                            false
                    ),
                    topParams(8)
            );

        } else {

            do {

                String date =
                        events.getString(
                                events.getColumnIndexOrThrow(
                                        "event_date"
                                )
                        );


                String type =
                        events.getString(
                                events.getColumnIndexOrThrow(
                                        "event_type"
                                )
                        );


                double amount =
                        events.getDouble(
                                events.getColumnIndexOrThrow(
                                        "amount"
                                )
                        );


                String unit =
                        events.getString(
                                events.getColumnIndexOrThrow(
                                        "unit"
                                )
                        );


                double cost =
                        events.getDouble(
                                events.getColumnIndexOrThrow(
                                        "cost"
                                )
                        );


                String eventNote =
                        events.getString(
                                events.getColumnIndexOrThrow(
                                        "note"
                                )
                        );


                LinearLayout eventCard =
                        new LinearLayout(this);


                eventCard.setOrientation(
                        LinearLayout.VERTICAL
                );


                eventCard.setPadding(
                        dp(12),
                        dp(11),
                        dp(12),
                        dp(11)
                );


                eventCard.setBackground(
                        fieldBackground()
                );


                LinearLayout.LayoutParams eventParams =
                        new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                        );


                eventParams.topMargin =
                        dp(8);


                String heading =
                        eventDisplay(
                                type
                        );


                if (!isEmpty(date)) {

                    heading +=
                            " • "
                                    + date;
                }


                eventCard.addView(
                        createText(
                                heading,
                                13,
                                COLOR_DARK_GREEN,
                                true
                        )
                );


                StringBuilder eventDetail =
                        new StringBuilder();


                if (amount > 0) {

                    eventDetail.append(
                            formatNumber(
                                    amount
                            )
                    );


                    if (!isEmpty(unit)) {

                        eventDetail.append(
                                " "
                        );

                        eventDetail.append(
                                unit
                        );
                    }
                }


                if (cost > 0) {

                    if (eventDetail.length() > 0) {

                        eventDetail.append(
                                " • "
                        );
                    }


                    eventDetail.append(
                            formatMoney(
                                    cost
                            )
                    );
                }


                if (!isEmpty(eventNote)) {

                    if (eventDetail.length() > 0) {

                        eventDetail.append(
                                "\n"
                        );
                    }


                    eventDetail.append(
                            eventNote
                    );
                }


                if (eventDetail.length() > 0) {

                    eventCard.addView(
                            createText(
                                    eventDetail.toString(),
                                    12,
                                    COLOR_GRAY,
                                    false
                            ),
                            topParams(4)
                    );
                }


                content.addView(
                        eventCard,
                        eventParams
                );


            } while (events.moveToNext());
        }


        events.close();


        new AlertDialog.Builder(this)
                .setTitle(
                        batchName
                )
                .setView(
                        scroll
                )
                .setPositiveButton(
                        "Kapat",
                        null
                )
                .show();
    }


    // ============================================================
    // HAREKET EKLE
    // ============================================================

    private void showAddEventDialog(
            final long batchId,
            String batchName
    ) {

        LinearLayout form =
                new LinearLayout(this);


        form.setOrientation(
                LinearLayout.VERTICAL
        );


        form.setPadding(
                dp(18),
                dp(8),
                dp(18),
                dp(10)
        );


        addFormLabel(
                form,
                "Hareket Türü"
        );


        final Spinner spinnerEvent =
                new Spinner(this);


        ArrayAdapter<String> eventAdapter =
                new ArrayAdapter<String>(
                        this,
                        android.R.layout.simple_spinner_item,
                        eventNames
                );


        eventAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );


        spinnerEvent.setAdapter(
                eventAdapter
        );


        spinnerEvent.setBackground(
                fieldBackground()
        );


        form.addView(
                spinnerEvent,
                fixedHeightTop(
                        54,
                        6
                )
        );


        addFormLabel(
                form,
                "Tarih"
        );


        final EditText etDate =
                createDateField(
                        today()
                );


        form.addView(
                etDate,
                fixedHeightTop(
                        54,
                        6
                )
        );


        etDate.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {

                        showDatePicker(
                                etDate
                        );
                    }
                }
        );


        addFormLabel(
                form,
                "Miktar"
        );


        final EditText etAmount =
                createNumberEditText(
                        "Örn. 5"
                );


        form.addView(
                etAmount,
                fixedHeightTop(
                        54,
                        6
                )
        );


        addFormLabel(
                form,
                "Birim"
        );


        final Spinner spinnerUnit =
                new Spinner(this);


        String[] units = {

                "L",
                "kg",
                "g",
                "adet",
                "paket",
                "-"
        };


        ArrayAdapter<String> unitAdapter =
                new ArrayAdapter<String>(
                        this,
                        android.R.layout.simple_spinner_item,
                        units
                );


        unitAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );


        spinnerUnit.setAdapter(
                unitAdapter
        );


        spinnerUnit.setBackground(
                fieldBackground()
        );


        form.addView(
                spinnerUnit,
                fixedHeightTop(
                        54,
                        6
                )
        );


        addFormLabel(
                form,
                "Masraf (TL)"
        );


        final EditText etCost =
                createNumberEditText(
                        "Varsa masraf"
                );


        form.addView(
                etCost,
                fixedHeightTop(
                        54,
                        6
                )
        );


        addFormLabel(
                form,
                "Not"
        );


        final EditText etNote =
                createEditText(
                        "Örn. Suyu tamamen değiştirildi...",
                        true
                );


        form.addView(
                etNote,
                multilineParams(
                        100,
                        6
                )
        );


        final AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                batchName
                                        + " • Hareket"
                        )
                        .setView(
                                form
                        )
                        .setNegativeButton(
                                "Vazgeç",
                                null
                        )
                        .setPositiveButton(
                                "Kaydet",
                                null
                        )
                        .create();


        dialog.setOnShowListener(
                new DialogInterface.OnShowListener() {

                    @Override
                    public void onShow(
                            DialogInterface dialogInterface
                    ) {

                        dialog
                                .getButton(
                                        AlertDialog.BUTTON_POSITIVE
                                )
                                .setOnClickListener(
                                        new View.OnClickListener() {

                                            @Override
                                            public void onClick(
                                                    View view
                                            ) {

                                                int position =
                                                        spinnerEvent
                                                                .getSelectedItemPosition();


                                                String eventCode =
                                                        eventCodes[
                                                                position
                                                                ];


                                                String unit =
                                                        spinnerUnit
                                                                .getSelectedItem()
                                                                .toString();


                                                if ("-".equals(
                                                        unit
                                                )) {

                                                    unit = "";
                                                }


                                                long result =
                                                        databaseHelper
                                                                .addBrineEvent(
                                                                        batchId,
                                                                        text(
                                                                                etDate
                                                                        ),
                                                                        eventCode,
                                                                        readNumber(
                                                                                etAmount
                                                                        ),
                                                                        unit,
                                                                        readNumber(
                                                                                etCost
                                                                        ),
                                                                        text(
                                                                                etNote
                                                                        )
                                                                );


                                                if (result != -1) {

                                                    Toast.makeText(
                                                            BrineActivity.this,
                                                            "Hareket kaydedildi ✓",
                                                            Toast.LENGTH_SHORT
                                                    ).show();


                                                    syncBrineCloud(
                                                            batchId
                                                    );


                                                    dialog.dismiss();

                                                    refreshScreen();

                                                } else {

                                                    Toast.makeText(
                                                            BrineActivity.this,
                                                            "Hareket kaydedilemedi",
                                                            Toast.LENGTH_LONG
                                                    ).show();
                                                }
                                            }
                                        }
                                );
                    }
                }
        );


        dialog.show();
    }


    private void syncBrineCloud(
            long batchId
    ) {

        if (batchId <= 0) {
            return;
        }

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            return;
        }

        new ProductionCloudRepository(
                databaseHelper
        ).syncBrineByLocalId(
                user.getUid(),
                batchId
        );
    }


    // ============================================================
    // HAZIR KG
    // ============================================================

    private void showReadyKgDialog(
            final long batchId
    ) {

        LinearLayout form =
                new LinearLayout(this);


        form.setOrientation(
                LinearLayout.VERTICAL
        );


        form.setPadding(
                dp(20),
                dp(8),
                dp(20),
                dp(8)
        );


        form.addView(
                createText(
                        "Salamuradan çıkan hazır ürün miktarını gir.",
                        13,
                        COLOR_GRAY,
                        false
                )
        );


        final EditText etReadyKg =
                createNumberEditText(
                        "Hazır ürün kg"
                );


        form.addView(
                etReadyKg,
                fixedHeightTop(
                        54,
                        12
                )
        );


        final AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "Hazır Ürün"
                        )
                        .setView(
                                form
                        )
                        .setNegativeButton(
                                "Vazgeç",
                                null
                        )
                        .setPositiveButton(
                                "Kaydet",
                                null
                        )
                        .create();


        dialog.setOnShowListener(
                new DialogInterface.OnShowListener() {

                    @Override
                    public void onShow(
                            DialogInterface dialogInterface
                    ) {

                        dialog
                                .getButton(
                                        AlertDialog.BUTTON_POSITIVE
                                )
                                .setOnClickListener(
                                        new View.OnClickListener() {

                                            @Override
                                            public void onClick(
                                                    View view
                                            ) {

                                                double readyKg =
                                                        readNumber(
                                                                etReadyKg
                                                        );


                                                if (readyKg <= 0) {

                                                    etReadyKg.setError(
                                                            "Hazır ürün miktarı gerekli"
                                                    );

                                                    return;
                                                }


                                                databaseHelper
                                                        .updateBrineReadyKg(
                                                                batchId,
                                                                readyKg
                                                        );


                                                databaseHelper
                                                        .updateBrineStatus(
                                                                batchId,
                                                                "READY"
                                                        );


                                                databaseHelper
                                                        .addBrineEvent(
                                                                batchId,
                                                                today(),
                                                                "CONTROL",
                                                                readyKg,
                                                                "kg",
                                                                0,
                                                                "Parti hazır olarak işaretlendi."
                                                        );


                                                Toast.makeText(
                                                        BrineActivity.this,
                                                        "Parti hazır olarak işaretlendi ✓",
                                                        Toast.LENGTH_SHORT
                                                ).show();


                                                syncBrineCloud(
                                                        batchId
                                                );


                                                dialog.dismiss();

                                                refreshScreen();
                                            }
                                        }
                                );
                    }
                }
        );


        dialog.show();
    }


    // ============================================================
    // TARİH SEÇ
    // ============================================================

    private void showDatePicker(
            final EditText editText
    ) {

        Calendar calendar =
                Calendar.getInstance();


        try {

            SimpleDateFormat parser =
                    new SimpleDateFormat(
                            "dd.MM.yyyy",
                            TR
                    );


            calendar.setTime(
                    parser.parse(
                            text(
                                    editText
                            )
                    )
            );

        } catch (Exception ignored) {
        }


        DatePickerDialog dialog =
                new DatePickerDialog(
                        this,

                        new DatePickerDialog.OnDateSetListener() {

                            @Override
                            public void onDateSet(
                                    android.widget.DatePicker view,
                                    int year,
                                    int month,
                                    int dayOfMonth
                            ) {

                                Calendar selected =
                                        Calendar.getInstance();


                                selected.set(
                                        Calendar.YEAR,
                                        year
                                );


                                selected.set(
                                        Calendar.MONTH,
                                        month
                                );


                                selected.set(
                                        Calendar.DAY_OF_MONTH,
                                        dayOfMonth
                                );


                                editText.setText(
                                        formatDate(
                                                selected
                                        )
                                );
                            }
                        },

                        calendar.get(
                                Calendar.YEAR
                        ),

                        calendar.get(
                                Calendar.MONTH
                        ),

                        calendar.get(
                                Calendar.DAY_OF_MONTH
                        )
                );


        dialog.show();
    }


    // ============================================================
    // STATUS VIEW
    // ============================================================

    private TextView createStatusView(
            String status
    ) {

        String text;
        int color;


        if ("READY".equals(
                status
        )) {

            text =
                    "HAZIR";

            color =
                    COLOR_GREEN;

        } else if ("CLOSED".equals(
                status
        )) {

            text =
                    "KAPALI";

            color =
                    COLOR_GRAY;

        } else {

            text =
                    "AKTİF";

            color =
                    COLOR_WARNING;
        }


        TextView view =
                createText(
                        text,
                        11,
                        COLOR_WHITE,
                        true
                );


        view.setGravity(
                Gravity.CENTER
        );


        view.setPadding(
                dp(10),
                dp(5),
                dp(10),
                dp(5)
        );


        view.setBackground(
                roundedBackground(
                        color,
                        50,
                        color
                )
        );


        return view;
    }


    // ============================================================
    // FORM YARDIMCILARI
    // ============================================================

    private void addFormLabel(
            LinearLayout parent,
            String text
    ) {

        parent.addView(
                createText(
                        text,
                        13,
                        COLOR_DARK_GREEN,
                        true
                ),
                topParams(14)
        );
    }


    private EditText createEditText(
            String hint,
            boolean multiline
    ) {

        EditText editText =
                new EditText(this);


        editText.setHint(
                hint
        );


        editText.setTextSize(
                15
        );


        editText.setTextColor(
                COLOR_TEXT
        );


        editText.setHintTextColor(
                GreenTheme.color(R.color.zd_muted)
        );


        editText.setPadding(
                dp(15),
                dp(8),
                dp(15),
                dp(8)
        );


        editText.setBackground(
                fieldBackground()
        );


        if (multiline) {

            editText.setGravity(
                    Gravity.TOP
            );


            editText.setInputType(
                    InputType.TYPE_CLASS_TEXT
                            |
                            InputType.TYPE_TEXT_FLAG_MULTI_LINE
            );

        } else {

            editText.setSingleLine(
                    true
            );
        }


        return editText;
    }


    private EditText createNumberEditText(
            String hint
    ) {

        EditText editText =
                createEditText(
                        hint,
                        false
                );


        editText.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        |
                        InputType.TYPE_NUMBER_FLAG_DECIMAL
        );


        return editText;
    }


    private EditText createDateField(
            String value
    ) {

        EditText editText =
                createEditText(
                        "Tarih seç",
                        false
                );


        editText.setText(
                value
        );


        editText.setFocusable(
                false
        );


        editText.setCursorVisible(
                false
        );


        editText.setClickable(
                true
        );


        return editText;
    }


    // ============================================================
    // UI YARDIMCILARI
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


    private TextView createSecondaryButton(
            String text
    ) {

        TextView button =
                createText(
                        text,
                        14,
                        COLOR_GREEN,
                        true
                );


        button.setGravity(
                Gravity.CENTER
        );


        button.setBackground(
                roundedBackground(
                        COLOR_CARD,
                        14,
                        GreenTheme.color(R.color.zd_border)
                )
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


        drawable.setColor(
                fill
        );


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


    private LinearLayout.LayoutParams multilineParams(
            int height,
            int marginTop
    ) {

        return fixedHeightTop(
                height,
                marginTop
        );
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

    private String today() {

        return formatDate(
                Calendar.getInstance()
        );
    }


    private String formatDate(
            Calendar calendar
    ) {

        SimpleDateFormat format =
                new SimpleDateFormat(
                        "dd.MM.yyyy",
                        TR
                );


        return format.format(
                calendar.getTime()
        );
    }


    private String formatKg(
            double value
    ) {

        return formatNumber(
                value
        )
                + " kg";
    }


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


    // ============================================================
    // SAYI OKUMA
    // ============================================================

    private double readNumber(
            EditText editText
    ) {

        String value =
                text(
                        editText
                );


        if (value.isEmpty()) {

            return 0;
        }


        value =
                value
                        .replace(
                                " ",
                                ""
                        )
                        .replace(
                                "TL",
                                ""
                        )
                        .replace(
                                "tl",
                                ""
                        )
                        .replace(
                                "kg",
                                ""
                        )
                        .trim();


        if (value.contains(".")
                && value.contains(",")) {

            value =
                    value
                            .replace(
                                    ".",
                                    ""
                            )
                            .replace(
                                    ",",
                                    "."
                            );

        } else if (value.contains(",")) {

            value =
                    value.replace(
                            ",",
                            "."
                    );

        } else if (value.contains(".")) {

            int firstDot =
                    value.indexOf(
                            "."
                    );


            int lastDot =
                    value.lastIndexOf(
                            "."
                    );


            if (firstDot != lastDot) {

                value =
                        value.replace(
                                ".",
                                ""
                        );

            } else {

                int digitsAfterDot =
                        value.length()
                                -
                                lastDot
                                -
                                1;


                if (digitsAfterDot == 3
                        && lastDot > 0) {

                    value =
                            value.replace(
                                    ".",
                                    ""
                            );
                }
            }
        }


        try {

            return Double.parseDouble(
                    value
            );

        } catch (Exception e) {

            return 0;
        }
    }


    // ============================================================
    // STRING
    // ============================================================

    private String text(
            EditText editText
    ) {

        return editText
                .getText()
                .toString()
                .trim();
    }


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


    private String gradeDisplay(
            String grade
    ) {

        if ("DOUBLE".equals(
                grade
        )) {

            return "Double";
        }


        if ("1".equals(
                grade
        )) {

            return "1 No";
        }


        if ("2".equals(
                grade
        )) {

            return "2 No";
        }


        if ("3".equals(
                grade
        )) {

            return "3 No";
        }


        if ("4".equals(
                grade
        )) {

            return "4 No";
        }


        if ("YAGLIK".equals(
                grade
        )) {

            return "Yağlık";
        }


        if ("KARISIK".equals(
                grade
        )) {

            return "Karışık";
        }


        return safeText(
                grade,
                "—"
        );
    }


    private String eventDisplay(
            String eventType
    ) {

        if ("WATER_CHANGE".equals(
                eventType
        )) {

            return "💧 Su Değişimi";
        }


        if ("SALT_ADDED".equals(
                eventType
        )) {

            return "🧂 Tuz İlavesi";
        }


        if ("CONTROL".equals(
                eventType
        )) {

            return "🔎 Kontrol";
        }


        if ("TASTE_TEST".equals(
                eventType
        )) {

            return "🫒 Tat Kontrolü";
        }


        if ("PACKAGING".equals(
                eventType
        )) {

            return "📦 Ambalajlama";
        }


        return safeText(
                eventType,
                "Hareket"
        );
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
