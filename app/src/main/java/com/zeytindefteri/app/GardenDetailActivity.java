package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GardenDetailActivity extends Activity {

    // ============================================================
    // VERİ
    // ============================================================

    private DatabaseHelper databaseHelper;

    private long gardenId = -1L;
    private String screenOwner = "";

    /*
     * Bahçe Karnesi ile aynı hesap motorunu kullanır.
     *
     * GardenDetail ekranındaki sezon özeti artık kendi başına
     * operations.cost toplamı üretmez.
     *
     * Böylece:
     *
     * GardenDetail
     *     ve
     * GardenAnalyticsActivity
     *
     * aynı GardenAnalyticsRepository kaynağından beslenir.
     */
    private final ExecutorService summaryWorker =
            Executors.newSingleThreadExecutor();

    private int summaryGeneration = 0;


    // ============================================================
    // NOT DIALOG
    // ============================================================

    private AlertDialog noteDialog;
    private EditText noteInput;


    // ============================================================
    // LOCALE
    // ============================================================

    private final Locale TR =
            new Locale(
                    "tr",
                    "TR"
            );


    // ============================================================
    // ÜST BİLGİLER
    // ============================================================

    private TextView tvGardenName;
    private TextView tvGardenSubtitle;

    private TextView tvVariety;
    private TextView tvLocation;
    private TextView tvIrrigation;

    private TextView tvTreeCount;
    private TextView tvAge;
    private TextView tvArea;


    // ============================================================
    // SEZON VERİLERİ
    // ============================================================

    private TextView tvGardenCost;
    private TextView tvGardenHarvest;
    private TextView tvKgPerTree;
    private TextView tvCostPerKg;


    // ============================================================
    // DİĞER
    // ============================================================

    private TextView tvGeneralNote;

    private LinearLayout containerGardenOperations;
    private LinearLayout containerGardenNotes;


    // ============================================================
    // RENKLER
    // ============================================================

    private final int COLOR_DARK_GREEN =
            GreenTheme.color(
                    R.color.zd_text
            );

    private final int COLOR_TEXT =
            GreenTheme.color(
                    R.color.zd_text
            );

    private final int COLOR_GRAY =
            GreenTheme.color(
                    R.color.zd_muted
            );

    private final int COLOR_DIVIDER =
            GreenTheme.color(
                    R.color.zd_border
            );


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

            getActionBar()
                    .hide();
        }


        getWindow()
                .setStatusBarColor(
                        GreenTheme.color(
                                R.color.zd_bg
                        )
                );


        getWindow()
                .setNavigationBarColor(
                        GreenTheme.color(
                                R.color.zd_bg
                        )
                );


        getWindow()
                .getDecorView()
                .setSystemUiVisibility(
                        0
                );


        setContentView(
                R.layout.activity_garden_detail
        );


        gardenId =
                getIntent()
                        .getLongExtra(
                                "garden_id",
                                -1L
                        );


        if (gardenId == -1L) {

            Toast.makeText(
                    this,
                    "Bahçe bulunamadı",
                    Toast.LENGTH_SHORT
            ).show();

            finish();

            return;
        }


        databaseHelper =
                new DatabaseHelper(
                        this
                );


        if (savedInstanceState == null) {

            screenOwner =
                    databaseHelper
                            .getActiveOwnerUid();

        } else {

            screenOwner =
                    savedInstanceState
                            .getString(
                                    "screen_owner",
                                    ""
                            );
        }


        findViews();

        setupButtons();

        refreshGarden();


        if (
                !isFinishing()
                        && savedInstanceState != null
                        && savedInstanceState.getBoolean(
                                "note_open"
                        )
        ) {

            showAddNote(
                    savedInstanceState.getString(
                            "note_draft",
                            ""
                    )
            );
        }
    }


    // ============================================================
    // ON RESUME
    // ============================================================

    @Override
    protected void onResume() {

        super.onResume();


        if (
                databaseHelper != null
                        && gardenId != -1L
        ) {

            refreshGarden();
        }
    }


    // ============================================================
    // VIEW'LAR
    // ============================================================

    private void findViews() {

        tvGardenName =
                findViewById(
                        R.id.tvGardenName
                );


        tvGardenSubtitle =
                findViewById(
                        R.id.tvGardenSubtitle
                );


        tvVariety =
                findViewById(
                        R.id.tvVariety
                );


        tvLocation =
                findViewById(
                        R.id.tvLocation
                );


        tvIrrigation =
                findViewById(
                        R.id.tvIrrigation
                );


        tvTreeCount =
                findViewById(
                        R.id.tvTreeCount
                );


        tvAge =
                findViewById(
                        R.id.tvAge
                );


        tvArea =
                findViewById(
                        R.id.tvArea
                );


        tvGardenCost =
                findViewById(
                        R.id.tvGardenCost
                );


        tvGardenHarvest =
                findViewById(
                        R.id.tvGardenHarvest
                );


        tvKgPerTree =
                findViewById(
                        R.id.tvKgPerTree
                );


        tvCostPerKg =
                findViewById(
                        R.id.tvCostPerKg
                );


        tvGeneralNote =
                findViewById(
                        R.id.tvGeneralNote
                );


        containerGardenOperations =
                findViewById(
                        R.id.containerGardenOperations
                );


        containerGardenNotes =
                findViewById(
                        R.id.containerGardenNotes
                );
    }


    // ============================================================
    // BUTONLAR
    // ============================================================

    private void setupButtons() {

        findViewById(
                R.id.btnGardenAnalytics
        )
                .setOnClickListener(
                        view -> {

                            Intent intent =
                                    new Intent(
                                            GardenDetailActivity.this,
                                            GardenAnalyticsActivity.class
                                    );

                            intent.putExtra(
                                    "garden_id",
                                    gardenId
                            );

                            startActivity(
                                    intent
                            );
                        }
                );


        findViewById(
                R.id.btnBack
        )
                .setOnClickListener(
                        view -> finish()
                );


        findViewById(
                R.id.btnAddOperation
        )
                .setOnClickListener(
                        view -> {

                            Intent intent =
                                    new Intent(
                                            GardenDetailActivity.this,
                                            NewOperationActivity.class
                                    );


                            intent.putExtra(
                                    "garden_id",
                                    gardenId
                            );


                            startActivity(
                                    intent
                            );
                        }
                );


        findViewById(
                R.id.btnAddNote
        )
                .setOnClickListener(
                        view ->
                                showAddNote(
                                        ""
                                )
                );


        findViewById(
                R.id.btnEditGarden
        )
                .setOnClickListener(
                        view -> {

                            Intent intent =
                                    new Intent(
                                            GardenDetailActivity.this,
                                            GardenEditActivity.class
                                    );


                            intent.putExtra(
                                    "garden_id",
                                    gardenId
                            );


                            startActivity(
                                    intent
                            );
                        }
                );


        findViewById(
                R.id.btnAllGardenOperations
        )
                .setOnClickListener(
                        view -> {

                            Intent intent =
                                    new Intent(
                                            GardenDetailActivity.this,
                                            GardenOperationHistoryActivity.class
                                    );


                            intent.putExtra(
                                    "garden_id",
                                    gardenId
                            );


                            startActivity(
                                    intent
                            );
                        }
                );
    }


    // ============================================================
    // NOT EKLE
    // ============================================================

    private void showAddNote(
            String draft
    ) {

        if (!isSameOwner()) {

            finish();

            return;
        }


        noteInput =
                new EditText(
                        this
                );


        noteInput.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );


        noteInput.setMinLines(
                3
        );


        noteInput.setGravity(
                Gravity.TOP
        );


        noteInput.setTextColor(
                COLOR_TEXT
        );


        noteInput.setHintTextColor(
                COLOR_GRAY
        );


        noteInput.setHint(
                "Bahçenle ilgili notunu yaz"
        );


        noteInput.setPadding(
                dp(18),
                dp(14),
                dp(18),
                dp(14)
        );


        noteInput.setText(
                draft
        );


        noteDialog =
                new AlertDialog.Builder(
                        this
                )
                        .setTitle(
                                "Not Ekle"
                        )
                        .setView(
                                noteInput
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


        noteDialog.setOnShowListener(
                dialog -> {

                    if (
                            noteDialog.getWindow()
                                    != null
                    ) {

                        noteDialog
                                .getWindow()
                                .setBackgroundDrawableResource(
                                        R.color.zd_card
                                );
                    }


                    noteDialog
                            .getButton(
                                    AlertDialog.BUTTON_POSITIVE
                            )
                            .setOnClickListener(
                                    view -> saveGardenNote()
                            );
                }
        );


        noteDialog.show();
    }


    private void saveGardenNote() {

        if (noteInput == null) {
            return;
        }


        String note =
                noteInput
                        .getText()
                        .toString()
                        .trim();


        if (note.isEmpty()) {

            noteInput.setError(
                    "Boş not kaydedilemez"
            );

            return;
        }


        if (!isSameOwner()) {

            finish();

            return;
        }


        try {

            String date =
                    new SimpleDateFormat(
                            "dd.MM.yyyy HH:mm",
                            TR
                    )
                            .format(
                                    new Date()
                            );


            long result =
                    databaseHelper
                            .addGardenNote(
                                    gardenId,
                                    date,
                                    note,
                                    ""
                            );


            if (result == -1L) {

                noteInput.setError(
                        "Not kaydedilemedi. Bahçeyi ve oturumu kontrol et."
                );

                return;
            }


        } catch (
                android.database.SQLException error
        ) {

            noteInput.setError(
                    "Not kaydedilemedi. Tekrar dene."
            );

            return;
        }


        if (
                noteDialog != null
                        && noteDialog.isShowing()
        ) {

            noteDialog.dismiss();
        }


        loadGardenNotes();


        Toast.makeText(
                this,
                "Not kaydedildi",
                Toast.LENGTH_SHORT
        ).show();


        /*
         * Local-first davranış korunur.
         * Kaydetme işlemi bulutu beklemez.
         * Mevcut senkronizasyon daha sonra yükleyebilir.
         */
    }


    // ============================================================
    // INSTANCE STATE
    // ============================================================

    @Override
    protected void onSaveInstanceState(
            Bundle state
    ) {

        super.onSaveInstanceState(
                state
        );


        state.putString(
                "screen_owner",
                screenOwner
        );


        boolean open =
                noteDialog != null
                        && noteDialog.isShowing();


        state.putBoolean(
                "note_open",
                open
        );


        if (
                open
                        && noteInput != null
        ) {

            state.putString(
                    "note_draft",
                    noteInput
                            .getText()
                            .toString()
            );
        }
    }


    // ============================================================
    // ON DESTROY
    // ============================================================

    @Override
    protected void onDestroy() {

        summaryGeneration++;


        if (
                noteDialog != null
                        && noteDialog.isShowing()
        ) {

            noteDialog.dismiss();
        }


        /*
         * Analytics worker kendi DatabaseHelper örneğini kullandığı için
         * Activity'nin ana helper'ı güvenle kapatılabilir.
         */
        summaryWorker.shutdownNow();


        if (databaseHelper != null) {

            databaseHelper.close();
        }


        super.onDestroy();
    }


    // ============================================================
    // TÜM DETAYI YENİLE
    // ============================================================

    private void refreshGarden() {

        if (!isSameOwner()) {

            finish();

            return;
        }


        loadGarden();


        if (isFinishing()) {
            return;
        }


        /*
         * ÖNEMLİ:
         *
         * Eski loadGardenSummary() artık operations.cost toplamı
         * hesaplamaz.
         *
         * Bahçe Karnesi ile aynı GardenAnalyticsRepository kullanılır.
         */
        loadGardenSummary();


        loadOperations();

        loadGardenNotes();
    }


    // ============================================================
    // BAHÇE ANA BİLGİLERİ
    // ============================================================

    private void loadGarden() {

        Cursor cursor =
                databaseHelper
                        .getGarden(
                                gardenId
                        );


        if (!cursor.moveToFirst()) {

            cursor.close();


            Toast.makeText(
                    this,
                    "Bahçe bulunamadı",
                    Toast.LENGTH_SHORT
            ).show();


            finish();

            return;
        }


        String name =
                cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                "name"
                        )
                );


        int treeCount =
                cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                                "tree_count"
                        )
                );


        int age =
                cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                                "age"
                        )
                );


        String variety =
                cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                "variety"
                        )
                );


        double area =
                cursor.getDouble(
                        cursor.getColumnIndexOrThrow(
                                "area_decare"
                        )
                );


        String province =
                cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                "province"
                        )
                );


        String district =
                cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                "district"
                        )
                );


        String neighborhood =
                cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                "neighborhood"
                        )
                );


        String irrigation =
                cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                "irrigation_type"
                        )
                );


        String notes =
                cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                "notes"
                        )
                );


        cursor.close();


        tvGardenName.setText(
                safeOr(
                        name,
                        "Bahçe"
                )
        );


        tvGardenSubtitle.setText(
                treeCount
                        + " ağaç"
                        + " • "
                        + age
                        + " yaş"
        );


        tvVariety.setText(
                isEmpty(
                        variety
                )
                        ? "Çeşit belirtilmedi"
                        : variety
        );


        tvTreeCount.setText(
                String.valueOf(
                        treeCount
                )
        );


        tvAge.setText(
                String.valueOf(
                        age
                )
        );


        tvArea.setText(
                formatNumber(
                        area
                )
        );


        // --------------------------------------------------------
        // KONUM
        // --------------------------------------------------------

        StringBuilder location =
                new StringBuilder();


        appendLocation(
                location,
                neighborhood
        );


        appendLocation(
                location,
                district
        );


        appendLocation(
                location,
                province
        );


        tvLocation.setText(
                location.length() == 0
                        ? "Konum belirtilmedi"
                        : location
        );


        tvIrrigation.setText(
                isEmpty(
                        irrigation
                )
                        ? "Sulama tipi belirtilmedi"
                        : irrigation
        );


        tvGeneralNote.setText(
                isEmpty(
                        notes
                )
                        ? "Bu bahçe için genel not bulunmuyor."
                        : notes
        );
    }


    // ============================================================
    // BAHÇE SEZON ÖZETİ
    // ============================================================

    private void loadGardenSummary() {

        /*
         * Önce eski değerlerin kısa süre ekranda kalmasını engelle.
         *
         * Özellikle bahçe/sezon değişiminde kullanıcı yanlış rakam
         * görmemeli.
         */
        setSummaryLoading();


        final int ticket =
                ++summaryGeneration;


        final String owner =
                screenOwner;


        final int season =
                databaseHelper
                        .getActiveSeasonYear();


        final long requestedGardenId =
                gardenId;


        summaryWorker.execute(
                () -> {

                    GardenAnalyticsSnapshot snapshot =
                            null;


                    try {

                        /*
                         * Worker kendi helper'ını kullanır.
                         *
                         * Böylece Activity ana helper'ının UI sorgularıyla
                         * aynı DatabaseHelper nesnesi üzerinde yarışması
                         * engellenir.
                         */
                        DatabaseHelper analyticsHelper =
                                new DatabaseHelper(
                                        getApplicationContext()
                                );


                        try {

                            GardenAnalyticsRepository.Result result =
                                    new GardenAnalyticsRepository(
                                            analyticsHelper
                                    )
                                            .load(
                                                    owner,
                                                    season,
                                                    false
                                            );


                            if (
                                    result != null
                                            && result.current != null
                            ) {

                                snapshot =
                                        result.current
                                                .garden(
                                                        requestedGardenId
                                                );
                            }


                        } finally {

                            analyticsHelper.close();
                        }


                    } catch (
                            RuntimeException error
                    ) {

                        snapshot =
                                null;
                    }


                    final GardenAnalyticsSnapshot loadedSnapshot =
                            snapshot;


                    runOnUiThread(
                            () -> {

                                if (
                                        isFinishing()
                                                || isDestroyed()
                                                || ticket
                                                != summaryGeneration
                                ) {

                                    return;
                                }


                                if (!isSameOwner()) {

                                    finish();

                                    return;
                                }


                                if (
                                        season
                                                != databaseHelper
                                                .getActiveSeasonYear()
                                ) {

                                    return;
                                }


                                if (
                                        requestedGardenId
                                                != gardenId
                                ) {

                                    return;
                                }


                                if (
                                        loadedSnapshot
                                                == null
                                ) {

                                    setSummaryUnavailable();

                                    return;
                                }


                                applyGardenSummary(
                                        loadedSnapshot
                                );
                            }
                    );
                }
        );
    }


    // ============================================================
    // ANALYTICS SNAPSHOT -> UI
    // ============================================================

    private void applyGardenSummary(
            GardenAnalyticsSnapshot snapshot
    ) {

        /*
         * Burası Bahçe Karnesi ile aynı değerleri kullanır.
         *
         * expense
         *     = kayıtlı toplam masraf
         *
         * harvestKg
         *     = sezon toplam hasadı
         *
         * kgPerTree()
         *     = harvestKg / tree_count
         *
         * costPerKg()
         *     = expense / harvestKg
         */


        tvGardenCost.setText(
                formatMoney(
                        snapshot.expense
                )
        );


        tvGardenHarvest.setText(
                formatKg(
                        snapshot.harvestKg
                )
        );


        Double kgPerTree =
                snapshot.kgPerTree();


        if (
                kgPerTree == null
                        || !Double.isFinite(
                                kgPerTree
                        )
        ) {

            tvKgPerTree.setText(
                    "—"
            );

        } else {

            tvKgPerTree.setText(
                    formatKg(
                            kgPerTree
                    )
            );
        }


        Double costPerKg =
                snapshot.costPerKg();


        if (
                costPerKg == null
                        || !Double.isFinite(
                                costPerKg
                        )
        ) {

            tvCostPerKg.setText(
                    "—"
            );

        } else {

            tvCostPerKg.setText(
                    formatMoney(
                            costPerKg
                    )
                            + "/kg"
            );
        }
    }


    private void setSummaryLoading() {

        tvGardenCost.setText(
                "…"
        );


        tvGardenHarvest.setText(
                "…"
        );


        tvKgPerTree.setText(
                "…"
        );


        tvCostPerKg.setText(
                "…"
        );
    }


    private void setSummaryUnavailable() {

        tvGardenCost.setText(
                "—"
        );


        tvGardenHarvest.setText(
                "—"
        );


        tvKgPerTree.setText(
                "—"
        );


        tvCostPerKg.setText(
                "—"
        );
    }


    // ============================================================
    // SON İŞLEMLER
    // ============================================================

    private void loadOperations() {

        containerGardenOperations
                .removeAllViews();


        Cursor cursor =
                databaseHelper
                        .getGardenOperations(
                                gardenId,
                                5
                        );


        int count =
                0;


        while (
                cursor.moveToNext()
        ) {

            String type =
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                    "type"
                            )
                    );


            String date =
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                    "operation_date"
                            )
                    );


            double quantity =
                    cursor.getDouble(
                            cursor.getColumnIndexOrThrow(
                                    "quantity"
                            )
                    );


            String unit =
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                    "unit"
                            )
                    );


            double cost =
                    cursor.getDouble(
                            cursor.getColumnIndexOrThrow(
                                    "cost"
                            )
                    );


            String note =
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                    "note"
                            )
                    );


            addOperationRow(
                    type,
                    date,
                    quantity,
                    unit,
                    cost,
                    note
            );


            count++;
        }


        cursor.close();


        if (count == 0) {

            containerGardenOperations
                    .addView(
                            createEmptyText(
                                    "Bu sezon bu bahçe için henüz işlem kaydı yok."
                            )
                    );
        }
    }


    // ============================================================
    // İŞLEM SATIRI
    // ============================================================

    private void addOperationRow(
            String type,
            String date,
            double quantity,
            String unit,
            double cost,
            String note
    ) {

        if (
                containerGardenOperations
                        .getChildCount()
                        > 0
        ) {

            containerGardenOperations
                    .addView(
                            createDivider()
                    );
        }


        LinearLayout row =
                new LinearLayout(
                        this
                );


        row.setOrientation(
                LinearLayout.HORIZONTAL
        );


        row.setGravity(
                Gravity.CENTER_VERTICAL
        );


        // --------------------------------------------------------
        // İKON
        // --------------------------------------------------------

        TextView icon =
                createText(
                        getOperationIcon(
                                type
                        ),
                        20,
                        COLOR_DARK_GREEN,
                        false
                );


        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(
                        dp(40),
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        icon.setLayoutParams(
                iconParams
        );


        row.addView(
                icon
        );


        // --------------------------------------------------------
        // ORTA BİLGİ
        // --------------------------------------------------------

        LinearLayout content =
                new LinearLayout(
                        this
                );


        content.setOrientation(
                LinearLayout.VERTICAL
        );


        LinearLayout.LayoutParams contentParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                );


        content.setLayoutParams(
                contentParams
        );


        TextView title =
                createText(
                        safeOr(
                                type,
                                "İşlem"
                        ),
                        14,
                        COLOR_TEXT,
                        true
                );


        content.addView(
                title
        );


        StringBuilder subtitle =
                new StringBuilder();


        if (!isEmpty(date)) {

            subtitle.append(
                    date
            );
        }


        if (quantity > 0) {

            if (
                    subtitle.length()
                            > 0
            ) {

                subtitle.append(
                        " • "
                );
            }


            subtitle.append(
                    formatNumber(
                            quantity
                    )
            );


            if (!isEmpty(unit)) {

                subtitle.append(
                        " "
                );


                subtitle.append(
                        unit
                );
            }
        }


        if (!isEmpty(note)) {

            if (
                    subtitle.length()
                            > 0
            ) {

                subtitle.append(
                        " • "
                );
            }


            subtitle.append(
                    note
            );
        }


        TextView sub =
                createText(
                        subtitle.toString(),
                        12,
                        COLOR_GRAY,
                        false
                );


        LinearLayout.LayoutParams subParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        subParams.topMargin =
                dp(2);


        sub.setLayoutParams(
                subParams
        );


        content.addView(
                sub
        );


        row.addView(
                content
        );


        // --------------------------------------------------------
        // MASRAF
        // --------------------------------------------------------

        TextView costView =
                createText(
                        cost > 0
                                ? formatMoney(
                                        cost
                                )
                                : "",
                        12,
                        COLOR_DARK_GREEN,
                        true
                );


        costView.setGravity(
                Gravity.END
        );


        LinearLayout.LayoutParams costParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        costParams.leftMargin =
                dp(8);


        costView.setLayoutParams(
                costParams
        );


        row.addView(
                costView
        );


        containerGardenOperations
                .addView(
                        row
                );
    }


    // ============================================================
    // BAHÇE NOTLARI
    // ============================================================

    private void loadGardenNotes() {

        containerGardenNotes
                .removeAllViews();


        Cursor cursor =
                databaseHelper
                        .getGardenNotes(
                                gardenId
                        );


        int count =
                0;


        while (
                cursor.moveToNext()
        ) {

            String noteDate =
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                    "note_date"
                            )
                    );


            String note =
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                    "note"
                            )
                    );


            addNoteRow(
                    noteDate,
                    note
            );


            count++;
        }


        cursor.close();


        if (count == 0) {

            containerGardenNotes
                    .addView(
                            createEmptyText(
                                    "Henüz bahçe notu yok"
                            )
                    );
        }
    }


    // ============================================================
    // NOT SATIRI
    // ============================================================

    private void addNoteRow(
            String date,
            String note
    ) {

        if (
                containerGardenNotes
                        .getChildCount()
                        > 0
        ) {

            containerGardenNotes
                    .addView(
                            createDivider()
                    );
        }


        LinearLayout row =
                new LinearLayout(
                        this
                );


        row.setOrientation(
                LinearLayout.VERTICAL
        );


        TextView dateView =
                createText(
                        safeOr(
                                date,
                                "Tarih belirtilmedi"
                        ),
                        12,
                        COLOR_GRAY,
                        true
                );


        row.addView(
                dateView
        );


        TextView noteView =
                createText(
                        safeOr(
                                note,
                                ""
                        ),
                        14,
                        COLOR_TEXT,
                        false
                );


        LinearLayout.LayoutParams noteParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        noteParams.topMargin =
                dp(4);


        noteView.setLayoutParams(
                noteParams
        );


        row.addView(
                noteView
        );


        containerGardenNotes
                .addView(
                        row
                );
    }


    // ============================================================
    // OWNER KONTROLÜ
    // ============================================================

    private boolean isSameOwner() {

        if (databaseHelper == null) {
            return false;
        }


        String activeOwner =
                databaseHelper
                        .getActiveOwnerUid();


        if (activeOwner == null) {
            activeOwner = "";
        }


        String expectedOwner =
                screenOwner == null
                        ? ""
                        : screenOwner;


        return expectedOwner.equals(
                activeOwner
        );
    }


    // ============================================================
    // LOCATION STRING
    // ============================================================

    private void appendLocation(
            StringBuilder builder,
            String value
    ) {

        if (isEmpty(value)) {
            return;
        }


        if (
                builder.length()
                        > 0
        ) {

            builder.append(
                    " / "
            );
        }


        builder.append(
                value.trim()
        );
    }


    // ============================================================
    // İŞLEM İKONU
    // ============================================================

    private String getOperationIcon(
            String type
    ) {

        if (type == null) {
            return "📝";
        }


        if (
                type.equalsIgnoreCase(
                        "Sulama"
                )
        ) {

            return "💧";
        }


        if (
                type.equalsIgnoreCase(
                        "Gübre"
                )
        ) {

            return "🌱";
        }


        if (
                type.equalsIgnoreCase(
                        "İlaçlama"
                )
        ) {

            return "🧴";
        }


        if (
                type.equalsIgnoreCase(
                        "Hasat"
                )
        ) {

            return "🧺";
        }


        if (
                type.equalsIgnoreCase(
                        "Satış"
                )
        ) {

            return "💰";
        }


        if (
                type.equalsIgnoreCase(
                        "Budama"
                )
        ) {

            return "✂";
        }


        if (
                type.equalsIgnoreCase(
                        "Sinek tuzağı"
                )
        ) {

            return "🪰";
        }


        return "📝";
    }


    // ============================================================
    // DIVIDER
    // ============================================================

    private View createDivider() {

        View divider =
                new View(
                        this
                );


        divider.setBackgroundColor(
                COLOR_DIVIDER
        );


        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(1)
                );


        params.topMargin =
                dp(10);


        params.bottomMargin =
                dp(10);


        divider.setLayoutParams(
                params
        );


        return divider;
    }


    // ============================================================
    // BOŞ YAZI
    // ============================================================

    private TextView createEmptyText(
            String message
    ) {

        TextView textView =
                new TextView(
                        this
                );


        textView.setText(
                message
        );


        textView.setTextSize(
                14
        );


        textView.setTextColor(
                COLOR_GRAY
        );


        textView.setGravity(
                Gravity.CENTER
        );


        textView.setLayoutParams(
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(70)
                )
        );


        return textView;
    }


    // ============================================================
    // TEXT
    // ============================================================

    private TextView createText(
            String text,
            float size,
            int color,
            boolean bold
    ) {

        TextView textView =
                new TextView(
                        this
                );


        textView.setText(
                text
        );


        textView.setTextSize(
                size
        );


        textView.setTextColor(
                color
        );


        if (bold) {

            textView.setTypeface(
                    null,
                    Typeface.BOLD
            );
        }


        return textView;
    }


    // ============================================================
    // FORMATLAR
    // ============================================================

    private String formatMoney(
            double value
    ) {

        NumberFormat format =
                NumberFormat.getNumberInstance(
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
        )
                + " TL";
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
                NumberFormat.getNumberInstance(
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
    // STRING
    // ============================================================

    private boolean isEmpty(
            String value
    ) {

        return value == null
                || value.trim()
                .isEmpty();
    }


    private String safeOr(
            String value,
            String alternative
    ) {

        return isEmpty(
                value
        )
                ? alternative
                : value;
    }


    // ============================================================
    // DP
    // ============================================================

    private int dp(
            int value
    ) {

        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;


        return Math.round(
                value
                        * density
        );
    }
}