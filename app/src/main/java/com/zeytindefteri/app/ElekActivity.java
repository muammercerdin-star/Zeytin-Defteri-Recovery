package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class ElekActivity extends Activity {
    private android.app.Dialog priceDialog;
    private boolean restorePriceDialog;
    private String[] restoredPriceDraft;



    // ============================================================
    // FİYAT AYARLARI
    // ============================================================

    private static final String PREFS_NAME =
            "elek_price_settings";

    private static final String KEY_PRICE_DOUBLE =
            "price_double";

    private static final String KEY_PRICE_1 =
            "price_1";

    private static final String KEY_PRICE_2 =
            "price_2";

    private static final String KEY_PRICE_3 =
            "price_3";

    private static final String KEY_PRICE_4 =
            "price_4";

    private static final String KEY_PRICE_OIL =
            "price_oil";

    private static final String KEY_LAST_UPDATE =
            "last_update";


    // ============================================================
    // VARSAYILAN FİYATLAR
    // ============================================================

    private static final double DEFAULT_PRICE_DOUBLE =
            100.0;

    private static final double DEFAULT_PRICE_1 =
            90.0;

    private static final double DEFAULT_PRICE_2 =
            70.0;

    private static final double DEFAULT_PRICE_3 =
            55.0;

    private static final double DEFAULT_PRICE_4 =
            40.0;

    private static final double DEFAULT_PRICE_OIL =
            32.0;


    // ============================================================
    // VERİTABANI
    // ============================================================

    private DatabaseHelper databaseHelper;


    // ============================================================
    // ÜST ALAN
    // ============================================================

    private android.view.View btnElekBack;
    private TextView tvElekDate;
    private TextView tvElekGarden;


    // ============================================================
    // KG ALANLARI
    // ============================================================

    private EditText etKgDouble;
    private EditText etKg1;
    private EditText etKg2;
    private EditText etKg3;
    private EditText etKg4;
    private EditText etKgOil;


    // ============================================================
    // SATIR FİYATLARI
    // ============================================================

    private TextView tvPriceDouble;
    private TextView tvPrice1;
    private TextView tvPrice2;
    private TextView tvPrice3;
    private TextView tvPrice4;
    private TextView tvPriceOil;


    // ============================================================
    // HESAP YAZILARI
    // ============================================================

    private TextView tvCalcDouble;
    private TextView tvCalc1;
    private TextView tvCalc2;
    private TextView tvCalc3;
    private TextView tvCalc4;
    private TextView tvCalcOil;


    // ============================================================
    // SATIR TOPLAMLARI
    // ============================================================

    private TextView tvTotalDouble;
    private TextView tvTotal1;
    private TextView tvTotal2;
    private TextView tvTotal3;
    private TextView tvTotal4;
    private TextView tvTotalOil;


    // ============================================================
    // GENEL TOPLAMLAR
    // ============================================================

    private TextView tvTotalKg;
    private TextView tvTotalRevenue;
    private TextView tvAveragePrice;


    // ============================================================
    // SEZON ARŞİVİ
    // ============================================================

    private TextView tvArchiveYear;

    private TextView tvArchiveTotalKg;
    private TextView tvArchiveTotalRevenue;
    private TextView tvArchiveAverage;
    private TextView tvArchiveRecordCount;

    private TextView tvArchiveDoubleKg;
    private TextView tvArchive1Kg;
    private TextView tvArchive2Kg;
    private TextView tvArchive3Kg;
    private TextView tvArchive4Kg;
    private TextView tvArchiveOilKg;

    private TextView btnElekArchive;


    private int selectedArchiveYear =
            Calendar
                    .getInstance()
                    .get(
                            Calendar.YEAR
                    );


    // ============================================================
    // FİYAT GÜNCELLEME
    // ============================================================

    private EditText etPriceDouble;
    private EditText etPrice1;
    private EditText etPrice2;
    private EditText etPrice3;
    private EditText etPrice4;
    private EditText etPriceOil;

    private TextView tvPriceLastUpdate;

    private TextView btnUpdateElekPrices;
    private TextView btnSaveElek;

    // Yeni özet kartı (13.10.2026 tasarımı)
    private android.widget.LinearLayout elekDistributionBar;
    private TextView tvElekDistributionLegend;
    private TextView tvElekSaveHint;
    private TextView saleToggleSub;
    private TextView tvElekGardenLabel;
    private long homeActiveGardenId = 0;


    // ============================================================
    // AKTİF FİYATLAR
    // ============================================================

    // Elekten satış (varsayılan AÇIK; yayıncı kararı 14.10.2026, her açılışta açık başlar)
    private boolean saleOnSave = true;
    private int salePay = ElekSaleModel.PAY_NONE;
    private android.widget.LinearLayout salePanel;
    private android.widget.LinearLayout saleToggle;
    private android.view.View saleTrack, saleKnob;
    private TextView[] salePayChips;
    private EditText etSalePartial, etSaleBuyer;

    private double priceDouble;
    private double price1;
    private double price2;
    private double price3;
    private double price4;
    private double priceOil;


    // ============================================================
    // BAHÇE
    // ============================================================

    private long selectedGardenId =
            0;

    private String selectedGardenName =
            "";


    private final List<Long> gardenIds =
            new ArrayList<>();


    private final List<String> gardenNames =
            new ArrayList<>();


    // ============================================================
    // TARİH
    // ============================================================

    private String recordDateIso =
            "";


    // ============================================================
    // AYARLAR
    // ============================================================

    private SharedPreferences preferences;


    // ============================================================
    // FORMAT
    // ============================================================

    private final Locale turkishLocale =
            new Locale(
                    "tr",
                    "TR"
            );


    private DecimalFormat numberFormat;


    // ============================================================
    // KAYIT KİLİDİ
    // ============================================================

    private boolean saving =
            false;


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


        setContentView(
                R.layout.activity_elek
        );


        databaseHelper =
                new DatabaseHelper(
                        this
                );


        preferences =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                );


        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(getResources().getColor(R.color.zd_bg, null));
        getWindow().setNavigationBarColor(getResources().getColor(R.color.zd_bg, null));
        getWindow().getDecorView().setSystemUiVisibility(0);
        setupNumberFormat();

        findViews();

        setupDate();

        loadSavedPrices();

        setupKgWatchers();

        setupButtons();

        loadGardens();
        homeActiveGardenId = getIntent().getLongExtra("garden_id", 0L);
        if (savedInstanceState == null && selectedGardenId <= 0 && homeActiveGardenId > 0) {
            int activeIndex = gardenIds.indexOf(homeActiveGardenId);
            if (activeIndex >= 0) selectGarden(activeIndex);
        }
        restoreFormSelection(savedInstanceState);
        setupPriceChips();
        setupSaleOnSave();
        calculateAll();

        loadSeasonArchive();
    }


    // ============================================================
    // SAYI FORMAT
    // ============================================================

    private void setupNumberFormat() {

        DecimalFormatSymbols symbols =
                new DecimalFormatSymbols(
                        turkishLocale
                );


        symbols.setGroupingSeparator(
                '.'
        );


        symbols.setDecimalSeparator(
                ','
        );


        numberFormat =
                new DecimalFormat(
                        "#,##0.##",
                        symbols
                );
    }


    // ============================================================
    // VIEW BAĞLANTILARI
    // ============================================================

    private void findViews() {


        btnElekBack =
                findViewById(
                        R.id.btnElekBack
                );


        tvElekDate =
                findViewById(
                        R.id.tvElekDate
                );


        tvElekGarden =
                findViewById(
                        R.id.tvElekGarden
                );


        // ========================================================
        // KG
        // ========================================================

        etKgDouble =
                findViewById(
                        R.id.etKgDouble
                );


        etKg1 =
                findViewById(
                        R.id.etKg1
                );


        etKg2 =
                findViewById(
                        R.id.etKg2
                );


        etKg3 =
                findViewById(
                        R.id.etKg3
                );


        etKg4 =
                findViewById(
                        R.id.etKg4
                );


        etKgOil =
                findViewById(
                        R.id.etKgOil
                );


        // ========================================================
        // SATIR FİYATLARI
        // ========================================================

        tvPriceDouble =
                findViewById(
                        R.id.tvPriceDouble
                );


        tvPrice1 =
                findViewById(
                        R.id.tvPrice1
                );


        tvPrice2 =
                findViewById(
                        R.id.tvPrice2
                );


        tvPrice3 =
                findViewById(
                        R.id.tvPrice3
                );


        tvPrice4 =
                findViewById(
                        R.id.tvPrice4
                );


        tvPriceOil =
                findViewById(
                        R.id.tvPriceOil
                );


        // ========================================================
        // HESAP FORMÜLLERİ
        // ========================================================

        tvCalcDouble =
                findViewById(
                        R.id.tvCalcDouble
                );


        tvCalc1 =
                findViewById(
                        R.id.tvCalc1
                );


        tvCalc2 =
                findViewById(
                        R.id.tvCalc2
                );


        tvCalc3 =
                findViewById(
                        R.id.tvCalc3
                );


        tvCalc4 =
                findViewById(
                        R.id.tvCalc4
                );


        tvCalcOil =
                findViewById(
                        R.id.tvCalcOil
                );


        // ========================================================
        // SATIR TOPLAMLARI
        // ========================================================

        tvTotalDouble =
                findViewById(
                        R.id.tvTotalDouble
                );


        tvTotal1 =
                findViewById(
                        R.id.tvTotal1
                );


        tvTotal2 =
                findViewById(
                        R.id.tvTotal2
                );


        tvTotal3 =
                findViewById(
                        R.id.tvTotal3
                );


        tvTotal4 =
                findViewById(
                        R.id.tvTotal4
                );


        tvTotalOil =
                findViewById(
                        R.id.tvTotalOil
                );


        // ========================================================
        // GENEL TOPLAMLAR
        // ========================================================

        tvTotalKg =
                findViewById(
                        R.id.tvTotalKg
                );


        tvTotalRevenue =
                findViewById(
                        R.id.tvTotalRevenue
                );


        tvAveragePrice =
                findViewById(
                        R.id.tvAveragePrice
                );


        // ========================================================
        // SEZON ARŞİVİ
        // ========================================================

        tvArchiveYear =
                findViewById(
                        R.id.tvArchiveYear
                );


        tvArchiveTotalKg =
                findViewById(
                        R.id.tvArchiveTotalKg
                );


        tvArchiveTotalRevenue =
                findViewById(
                        R.id.tvArchiveTotalRevenue
                );


        tvArchiveAverage =
                findViewById(
                        R.id.tvArchiveAverage
                );


        tvArchiveRecordCount =
                findViewById(
                        R.id.tvArchiveRecordCount
                );


        tvArchiveDoubleKg =
                findViewById(
                        R.id.tvArchiveDoubleKg
                );


        tvArchive1Kg =
                findViewById(
                        R.id.tvArchive1Kg
                );


        tvArchive2Kg =
                findViewById(
                        R.id.tvArchive2Kg
                );


        tvArchive3Kg =
                findViewById(
                        R.id.tvArchive3Kg
                );


        tvArchive4Kg =
                findViewById(
                        R.id.tvArchive4Kg
                );


        tvArchiveOilKg =
                findViewById(
                        R.id.tvArchiveOilKg
                );


        btnElekArchive =
                findViewById(
                        R.id.btnElekArchive
                );


        // ========================================================
        // FİYAT GÜNCELLE
        // ========================================================

        etPriceDouble =
                findViewById(
                        R.id.etPriceDouble
                );


        etPrice1 =
                findViewById(
                        R.id.etPrice1
                );


        etPrice2 =
                findViewById(
                        R.id.etPrice2
                );


        etPrice3 =
                findViewById(
                        R.id.etPrice3
                );


        etPrice4 =
                findViewById(
                        R.id.etPrice4
                );


        etPriceOil =
                findViewById(
                        R.id.etPriceOil
                );


        tvPriceLastUpdate =
                findViewById(
                        R.id.tvPriceLastUpdate
                );


        btnUpdateElekPrices =
                findViewById(
                        R.id.btnUpdateElekPrices
                );


        btnSaveElek =
                findViewById(
                        R.id.btnSaveElek
                );

        elekDistributionBar =
                findViewById(
                        R.id.elekDistributionBar
                );

        tvElekDistributionLegend =
                findViewById(
                        R.id.tvElekDistributionLegend
                );

        tvElekSaveHint =
                findViewById(
                        R.id.tvElekSaveHint
                );

        tvElekGardenLabel =
                findViewById(
                        R.id.tvElekGardenLabel
                );
    }


    // ============================================================
    // TARİH
    // ============================================================

    private void showRecordDatePicker() {
        Calendar selected=Calendar.getInstance();
        try {
            Date date=new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(recordDateIso);
            if(date!=null)selected.setTime(date);
        } catch(Exception ignored) {}
        new android.app.DatePickerDialog(this,(picker,year,month,day) -> {
            Calendar date=Calendar.getInstance();date.set(year,month,day);
            recordDateIso=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(date.getTime());
            tvElekDate.setText(new SimpleDateFormat("d MMMM yyyy",turkishLocale).format(date.getTime()));
            refreshElekInsights();
        },selected.get(Calendar.YEAR),selected.get(Calendar.MONTH),selected.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void restoreFormSelection(Bundle state) {
        if(state==null)return;
        long garden=state.getLong("elek_selected_garden",0);
        int index=gardenIds.indexOf(garden);
        if(index>=0)selectGarden(index);
        String savedDate=state.getString("elek_record_date",recordDateIso);
        try {
            Date date=new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(savedDate);
            if(date!=null){recordDateIso=savedDate;tvElekDate.setText(new SimpleDateFormat("d MMMM yyyy",turkishLocale).format(date));}
        } catch(Exception ignored) {}
        restorePriceDialog = state.getBoolean("elek_prices_open", false);
        restoredPriceDraft = state.getStringArray("elek_price_draft");
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("elek_selected_garden",selectedGardenId);
        state.putString("elek_record_date",recordDateIso);
        state.putBoolean("elek_prices_open", priceDialog != null && priceDialog.isShowing());
        if (priceDialog != null && priceDialog.isShowing()) {
            EditText[] fields = priceFields();
            String[] draft = new String[fields.length];
            for (int i = 0; i < fields.length; i++) draft[i] = fields[i].getText().toString();
            state.putStringArray("elek_price_draft", draft);
        }
        super.onSaveInstanceState(state);
    }

    private EditText[] priceFields() {
        return new EditText[]{etPriceDouble, etPrice1, etPrice2, etPrice3, etPrice4, etPriceOil};
    }

    @Override protected void onPostCreate(Bundle state) {
        super.onPostCreate(state);
        if (restorePriceDialog) {
            restorePriceDialog = false;
            showPriceDialog();
            if (restoredPriceDraft != null && restoredPriceDraft.length == 6) {
                EditText[] fields = priceFields();
                for (int i = 0; i < fields.length; i++) fields[i].setText(restoredPriceDraft[i]);
            }
            restoredPriceDraft = null;
        }
    }

    private void showPriceDialog() {
        if (isFinishing() || isDestroyed() || priceDialog != null) return;
        final android.view.View panel = findViewById(R.id.elekPricePanel);
        final android.view.ViewGroup host = (android.view.ViewGroup) panel.getParent();
        final int index = host.indexOfChild(panel);
        final android.view.ViewGroup.LayoutParams originalParams = panel.getLayoutParams();
        loadSavedPrices();
        host.removeView(panel); // The panel is never made visible inside the main ScrollView.
        final android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(panel, new android.widget.ScrollView.LayoutParams(-1, -2));
        panel.setVisibility(android.view.View.VISIBLE);
        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(scroll);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        priceDialog = dialog;
        panel.findViewById(R.id.btnCloseElekPrices).setOnClickListener(v -> dialog.dismiss());
        panel.findViewById(R.id.btnCancelElekPrices).setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(ignored -> {
            panel.setVisibility(android.view.View.GONE);
            scroll.removeView(panel);
            host.addView(panel, index, originalParams);
            priceDialog = null;
            loadSavedPrices(); // Cancel/X/back discard only the unsaved price draft.
        });
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.65f);
            window.setGravity(android.view.Gravity.CENTER);
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            window.setWindowAnimations(R.style.ElekPricePopupAnimation);
        }
        dialog.show();
        if (window != null) {
            int width = Math.round(getResources().getDisplayMetrics().widthPixels * 0.92f);
            window.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        panel.requestFocus();
    }

    private void setupDate() {

        Date now =
                new Date();


        SimpleDateFormat displayFormatter =
                new SimpleDateFormat(
                        "d MMMM yyyy",
                        turkishLocale
                );


        SimpleDateFormat databaseFormatter =
                new SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.US
                );


        tvElekDate.setText(
                displayFormatter.format(
                        now
                )
        );


        recordDateIso =
                databaseFormatter.format(
                        now
                );
    }


    // ============================================================
    // BAHÇELERİ YÜKLE
    // ============================================================

    private void loadGardens() {

        gardenIds.clear();

        gardenNames.clear();


        Cursor cursor =
                null;


        try {

            cursor =
                    databaseHelper.getGardens();


            if (cursor != null) {

                int idIndex =
                        cursor.getColumnIndex(
                                "id"
                        );


                int nameIndex =
                        cursor.getColumnIndex(
                                "name"
                        );


                while (
                        cursor.moveToNext()
                ) {

                    if (idIndex < 0
                            ||
                            nameIndex < 0) {

                        continue;
                    }


                    long id =
                            cursor.getLong(
                                    idIndex
                            );


                    String name =
                            cursor.getString(
                                    nameIndex
                            );


                    if (name == null
                            ||
                            name.trim().isEmpty()) {

                        continue;
                    }


                    gardenIds.add(
                            id
                    );


                    gardenNames.add(
                            name.trim()
                    );
                }
            }

        } finally {

            if (cursor != null) {

                cursor.close();
            }
        }


        if (gardenIds.isEmpty()) {

            selectedGardenId =
                    0;


            selectedGardenName =
                    "";


            tvElekGarden.setText(
                    "Önce bahçe ekle"
            );


            return;
        }


        if (gardenIds.size() == 1) {

            selectGarden(
                    0
            );

            return;
        }


        if (selectedGardenId <= 0) {

            tvElekGarden.setText(
                    "Bahçe seç ▼"
            );
        }
    }


    // ============================================================
    // BAHÇE SEÇİMİ
    // ============================================================

    private void showGardenPicker() {

        loadGardens();


        if (gardenIds.isEmpty()) {

            Toast.makeText(
                    this,
                    "Elek kaydı için önce bir bahçe eklemelisin.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }


        String[] names =
                gardenNames.toArray(
                        new String[0]
                );


        new AlertDialog.Builder(
                this
        )
                .setTitle(
                        "Bahçe Seç"
                )

                .setItems(
                        names,
                        (dialog, which) -> {

                            selectGarden(
                                    which
                            );

                            dialog.dismiss();
                        }
                )

                .setNegativeButton(
                        "Vazgeç",
                        null
                )

                .show();
    }


    private void selectGarden(
            int position
    ) {

        if (position < 0
                ||
                position >= gardenIds.size()
                ||
                position >= gardenNames.size()) {

            return;
        }


        selectedGardenId =
                gardenIds.get(
                        position
                );


        selectedGardenName =
                gardenNames.get(
                        position
                );


        tvElekGarden.setText(
                selectedGardenName
                        + " ▼"
        );

        refreshElekInsights();
    }


    // ============================================================
    // KAYITLI FİYATLARI YÜKLE
    // ============================================================

    private void loadSavedPrices() {

        priceDouble =
                readSavedPrice(
                        KEY_PRICE_DOUBLE,
                        DEFAULT_PRICE_DOUBLE
                );


        price1 =
                readSavedPrice(
                        KEY_PRICE_1,
                        DEFAULT_PRICE_1
                );


        price2 =
                readSavedPrice(
                        KEY_PRICE_2,
                        DEFAULT_PRICE_2
                );


        price3 =
                readSavedPrice(
                        KEY_PRICE_3,
                        DEFAULT_PRICE_3
                );


        price4 =
                readSavedPrice(
                        KEY_PRICE_4,
                        DEFAULT_PRICE_4
                );


        priceOil =
                readSavedPrice(
                        KEY_PRICE_OIL,
                        DEFAULT_PRICE_OIL
                );


        etPriceDouble.setText(
                plainNumber(
                        priceDouble
                )
        );


        etPrice1.setText(
                plainNumber(
                        price1
                )
        );


        etPrice2.setText(
                plainNumber(
                        price2
                )
        );


        etPrice3.setText(
                plainNumber(
                        price3
                )
        );


        etPrice4.setText(
                plainNumber(
                        price4
                )
        );


        etPriceOil.setText(
                plainNumber(
                        priceOil
                )
        );


        String lastUpdate =
                preferences.getString(
                        KEY_LAST_UPDATE,
                        ""
                );


        if (lastUpdate == null
                ||
                lastUpdate.trim().isEmpty()) {

            tvPriceLastUpdate.setText(
                    "Son güncelleme: --"
            );

        } else {

            tvPriceLastUpdate.setText(
                    "Son güncelleme: "
                            + lastUpdate
            );
        }


        updatePriceLabels();
    }


    private double readSavedPrice(
            String key,
            double defaultValue
    ) {

        String value =
                preferences.getString(
                        key,
                        ""
                );


        if (value == null
                ||
                value.trim().isEmpty()) {

            return defaultValue;
        }


        try {

            return Double.parseDouble(
                    value
            );

        } catch (Exception e) {

            return defaultValue;
        }
    }


    // ============================================================
    // FİYAT ETİKETLERİ
    // ============================================================

    private void updatePriceLabels() {

        tvPriceDouble.setText(
                moneyUnit(priceDouble) + "/kg"
        );


        tvPrice1.setText(
                moneyUnit(price1) + "/kg"
        );


        tvPrice2.setText(
                moneyUnit(price2) + "/kg"
        );


        tvPrice3.setText(
                moneyUnit(price3) + "/kg"
        );


        tvPrice4.setText(
                moneyUnit(price4) + "/kg"
        );


        tvPriceOil.setText(
                moneyUnit(priceOil) + "/kg"
        );
    }


    // ============================================================
    // KG WATCHER
    // ============================================================

    private void setupKgWatchers() {

        addKgWatcher(
                etKgDouble
        );

        addKgWatcher(
                etKg1
        );

        addKgWatcher(
                etKg2
        );

        addKgWatcher(
                etKg3
        );

        addKgWatcher(
                etKg4
        );

        addKgWatcher(
                etKgOil
        );
    }


    private void addKgWatcher(
            EditText field
    ) {

        if (field == null) {

            return;
        }


        field.addTextChangedListener(
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

                        calculateAll();
                    }


                    @Override
                    public void afterTextChanged(
                            Editable s
                    ) {

                    }
                }
        );
    }


    // ============================================================
    // TÜM HESAPLAR
    // ============================================================

    private void calculateAll() {

        double kgDouble =
                readDouble(
                        etKgDouble
                );


        double kg1 =
                readDouble(
                        etKg1
                );


        double kg2 =
                readDouble(
                        etKg2
                );


        double kg3 =
                readDouble(
                        etKg3
                );


        double kg4 =
                readDouble(
                        etKg4
                );


        double kgOil =
                readDouble(
                        etKgOil
                );


        // ========================================================
        // SATIR TUTARLARI
        // ========================================================

        double totalDouble =
                kgDouble * priceDouble;

        double total1 =
                kg1 * price1;

        double total2 =
                kg2 * price2;

        double total3 =
                kg3 * price3;

        double total4 =
                kg4 * price4;

        double totalOil =
                kgOil * priceOil;


        // ========================================================
        // FORMÜLLER
        // ========================================================

        tvCalcDouble.setText(
                calculationText(
                        kgDouble,
                        priceDouble
                )
        );


        tvCalc1.setText(
                calculationText(
                        kg1,
                        price1
                )
        );


        tvCalc2.setText(
                calculationText(
                        kg2,
                        price2
                )
        );


        tvCalc3.setText(
                calculationText(
                        kg3,
                        price3
                )
        );


        tvCalc4.setText(
                calculationText(
                        kg4,
                        price4
                )
        );


        tvCalcOil.setText(
                calculationText(
                        kgOil,
                        priceOil
                )
        );


        // ========================================================
        // SATIR TOPLAMLARI
        // ========================================================

        tvTotalDouble.setText(
                money(
                        totalDouble
                )
        );


        tvTotal1.setText(
                money(
                        total1
                )
        );


        tvTotal2.setText(
                money(
                        total2
                )
        );


        tvTotal3.setText(
                money(
                        total3
                )
        );


        tvTotal4.setText(
                money(
                        total4
                )
        );


        tvTotalOil.setText(
                money(
                        totalOil
                )
        );


        // ========================================================
        // GENEL TOPLAM
        // ========================================================

        double totalKg =
                kgDouble
                        + kg1
                        + kg2
                        + kg3
                        + kg4
                        + kgOil;


        double totalRevenue =
                totalDouble
                        + total1
                        + total2
                        + total3
                        + total4
                        + totalOil;


        double averagePrice =
                0;


        if (totalKg > 0) {

            averagePrice =
                    totalRevenue
                            /
                            totalKg;
        }


        tvTotalKg.setText(
                formatNumber(
                        totalKg
                )
                        + " kg"
        );


        tvTotalRevenue.setText(
                money(
                        totalRevenue
                )
        );


        tvAveragePrice.setText(
                formatNumber(
                        averagePrice
                )
                        + " TL/kg"
        );

        refreshElekInsights();
    }


    // ============================================================
    // BUTONLAR
    // ============================================================

    private void setupButtons() {
        tvElekDate.setOnClickListener(view -> showRecordDatePicker());
        findViewById(R.id.btnEditElekPrices).setOnClickListener(view -> showPriceDialog());



        // GERİ
        btnElekBack.setOnClickListener(
                view -> finish()
        );


        // BAHÇE
        tvElekGarden.setClickable(
                true
        );


        tvElekGarden.setOnClickListener(
                view -> showGardenPicker()
        );


        // ========================================================
        // ARŞİV YILI
        // ========================================================

        tvArchiveYear.setClickable(
                true
        );


        tvArchiveYear.setOnClickListener(
                view -> showArchiveYearPicker()
        );


        // ========================================================
        // TÜM ELEK KAYITLARI
        // ========================================================

        btnElekArchive.setOnClickListener(
        view -> {

            android.content.Intent intent =
                    new android.content.Intent(
                            ElekActivity.this,
                            ElekArchiveActivity.class
                    );

            startActivity(
                    intent
            );
        }
);


        // ========================================================
        // FİYATLARI GÜNCELLE
        // ========================================================

        btnUpdateElekPrices.setOnClickListener(
                view -> updatePrices()
        );


        // ========================================================
        // ELEK KAYDET
        // ========================================================

        btnSaveElek.setOnClickListener(
                view -> saveElekRecord()
        );
    }


    // ============================================================
    // ARŞİV YILI SEÇ
    // ============================================================

    private void showArchiveYearPicker() {

        int currentYear =
                Calendar
                        .getInstance()
                        .get(
                                Calendar.YEAR
                        );


        String[] years =
                new String[10];


        for (
                int i = 0;
                i < years.length;
                i++
        ) {

            years[i] =
                    String.valueOf(
                            currentYear - i
                    );
        }


        new AlertDialog.Builder(
                this
        )
                .setTitle(
                        "Üretim yılı seç"
                )

                .setItems(
                        years,
                        (dialog, which) -> {

                            selectedArchiveYear =
                                    Integer.parseInt(
                                            years[which]
                                    );


                            loadSeasonArchive();
                        }
                )

                .setNegativeButton(
                        "İptal",
                        null
                )

                .show();
    }


    // ============================================================
    // SEZON ARŞİVİNİ YÜKLE
    // ============================================================

    private void loadSeasonArchive() {


        tvArchiveYear.setText(
                selectedArchiveYear
                        + " ▼"
        );


        Cursor cursor =
                null;


        try {

            cursor =
                    databaseHelper.getElekSeasonArchive(
                            selectedArchiveYear
                    );


            if (cursor != null
                    &&
                    cursor.moveToFirst()) {


                double totalKg =
                        cursor.getDouble(
                                cursor.getColumnIndexOrThrow(
                                        "total_kg"
                                )
                        );


                double totalRevenue =
                        cursor.getDouble(
                                cursor.getColumnIndexOrThrow(
                                        "total_revenue"
                                )
                        );


                int recordCount =
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                        "record_count"
                                )
                        );


                double doubleKg =
                        cursor.getDouble(
                                cursor.getColumnIndexOrThrow(
                                        "double_kg"
                                )
                        );


                double grade1Kg =
                        cursor.getDouble(
                                cursor.getColumnIndexOrThrow(
                                        "grade1_kg"
                                )
                        );


                double grade2Kg =
                        cursor.getDouble(
                                cursor.getColumnIndexOrThrow(
                                        "grade2_kg"
                                )
                        );


                double grade3Kg =
                        cursor.getDouble(
                                cursor.getColumnIndexOrThrow(
                                        "grade3_kg"
                                )
                        );


                double grade4Kg =
                        cursor.getDouble(
                                cursor.getColumnIndexOrThrow(
                                        "grade4_kg"
                                )
                        );


                double oilKg =
                        cursor.getDouble(
                                cursor.getColumnIndexOrThrow(
                                        "oil_kg"
                                )
                        );


                double average =
                        0;


                if (totalKg > 0) {

                    average =
                            totalRevenue
                                    /
                                    totalKg;
                }


                tvArchiveTotalKg.setText(
                        formatNumber(
                                totalKg
                        )
                                + " kg"
                );


                tvArchiveTotalRevenue.setText(
                        money(
                                totalRevenue
                        )
                );


                tvArchiveAverage.setText(
                        formatNumber(
                                average
                        )
                                + " TL/kg"
                );


                tvArchiveRecordCount.setText(
                        recordCount
                                + " kayıt"
                );


                tvArchiveDoubleKg.setText(
                        formatNumber(
                                doubleKg
                        )
                                + " kg"
                );


                tvArchive1Kg.setText(
                        formatNumber(
                                grade1Kg
                        )
                                + " kg"
                );


                tvArchive2Kg.setText(
                        formatNumber(
                                grade2Kg
                        )
                                + " kg"
                );


                tvArchive3Kg.setText(
                        formatNumber(
                                grade3Kg
                        )
                                + " kg"
                );


                tvArchive4Kg.setText(
                        formatNumber(
                                grade4Kg
                        )
                                + " kg"
                );


                tvArchiveOilKg.setText(
                        formatNumber(
                                oilKg
                        )
                                + " kg"
                );


            } else {

                resetArchiveViews();
            }


        } catch (Exception e) {

            resetArchiveViews();


        } finally {

            if (cursor != null) {

                cursor.close();
            }
        }
    }


    // ============================================================
    // ARŞİVİ SIFIRLA
    // ============================================================

    private void resetArchiveViews() {

        tvArchiveTotalKg.setText(
                "0 kg"
        );

        tvArchiveTotalRevenue.setText(
                "0 TL"
        );

        tvArchiveAverage.setText(
                "0 TL/kg"
        );

        tvArchiveRecordCount.setText(
                "0 kayıt"
        );

        tvArchiveDoubleKg.setText(
                "0 kg"
        );

        tvArchive1Kg.setText(
                "0 kg"
        );

        tvArchive2Kg.setText(
                "0 kg"
        );

        tvArchive3Kg.setText(
                "0 kg"
        );

        tvArchive4Kg.setText(
                "0 kg"
        );

        tvArchiveOilKg.setText(
                "0 kg"
        );
    }


    // ============================================================
    // TÜM KAYITLARI GÖSTER
    // ============================================================


    // ============================================================
    // ARŞİV TARİHİNİ GÜZELLEŞTİR
    // ============================================================


    // ============================================================
    // FİYATLARI GÜNCELLE
    // ============================================================

    private void updatePrices() {


        double newDouble =
                readDouble(
                        etPriceDouble
                );


        double new1 =
                readDouble(
                        etPrice1
                );


        double new2 =
                readDouble(
                        etPrice2
                );


        double new3 =
                readDouble(
                        etPrice3
                );


        double new4 =
                readDouble(
                        etPrice4
                );


        double newOil =
                readDouble(
                        etPriceOil
                );


        if (newDouble <= 0
                ||
                new1 <= 0
                ||
                new2 <= 0
                ||
                new3 <= 0
                ||
                new4 <= 0
                ||
                newOil <= 0) {

            Toast.makeText(
                    this,
                    "Bütün elek fiyatlarını gir.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        priceDouble =
                newDouble;

        price1 =
                new1;

        price2 =
                new2;

        price3 =
                new3;

        price4 =
                new4;

        priceOil =
                newOil;


        SimpleDateFormat formatter =
                new SimpleDateFormat(
                        "d MMM yyyy",
                        turkishLocale
                );


        String lastUpdate =
                formatter.format(
                        new Date()
                );


        preferences
                .edit()

                .putString(
                        KEY_PRICE_DOUBLE,
                        String.valueOf(
                                priceDouble
                        )
                )

                .putString(
                        KEY_PRICE_1,
                        String.valueOf(
                                price1
                        )
                )

                .putString(
                        KEY_PRICE_2,
                        String.valueOf(
                                price2
                        )
                )

                .putString(
                        KEY_PRICE_3,
                        String.valueOf(
                                price3
                        )
                )

                .putString(
                        KEY_PRICE_4,
                        String.valueOf(
                                price4
                        )
                )

                .putString(
                        KEY_PRICE_OIL,
                        String.valueOf(
                                priceOil
                        )
                )

                .putString(
                        KEY_LAST_UPDATE,
                        lastUpdate
                )

                .apply();


        updatePriceLabels();


        tvPriceLastUpdate.setText(
                "Son güncelleme: "
                        + lastUpdate
        );


        calculateAll();


        Toast.makeText(
                this,
                "Elek fiyatları güncellendi ✓",
                Toast.LENGTH_SHORT
        ).show();
        if (priceDialog != null) priceDialog.dismiss();
    }


    // ============================================================
    // ELEK KAYDI
    // ============================================================

    private void saveElekRecord() {


        if (saving) {

            return;
        }


        // ========================================================
        // BAHÇE KONTROL
        // ========================================================

        if (selectedGardenId <= 0) {

            Toast.makeText(
                    this,
                    "Önce bahçe seç.",
                    Toast.LENGTH_SHORT
            ).show();


            showGardenPicker();

            return;
        }


        // ========================================================
        // KG
        // ========================================================

        double kgDouble =
                readDouble(
                        etKgDouble
                );


        double kg1 =
                readDouble(
                        etKg1
                );


        double kg2 =
                readDouble(
                        etKg2
                );


        double kg3 =
                readDouble(
                        etKg3
                );


        double kg4 =
                readDouble(
                        etKg4
                );


        double kgOil =
                readDouble(
                        etKgOil
                );


        double totalKg =
                kgDouble
                        + kg1
                        + kg2
                        + kg3
                        + kg4
                        + kgOil;


        if (totalKg <= 0) {

            Toast.makeText(
                    this,
                    "En az bir elek miktarı gir.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        // ========================================================
        // ELEK SINIFLARI
        // ========================================================

        String[] grades =
                new String[]{

                        "DOUBLE",
                        "1",
                        "2",
                        "3",
                        "4",
                        "YAGLIK"
                };


        // ========================================================
        // KG DİZİSİ
        // ========================================================

        double[] kilograms =
                new double[]{

                        kgDouble,
                        kg1,
                        kg2,
                        kg3,
                        kg4,
                        kgOil
                };


        // ========================================================
        // KAYIT ANINDAKİ FİYATLAR
        // ========================================================

        double[] prices =
                new double[]{

                        priceDouble,
                        price1,
                        price2,
                        price3,
                        price4,
                        priceOil
                };


        final boolean wantSale = saleOnSave;
        final int pay = salePay;
        final double partial = etSalePartial == null ? 0 : readDouble(etSalePartial);
        final String buyer = etSaleBuyer == null ? "" : etSaleBuyer.getText().toString();
        if (wantSale) {
            double gross = 0;
            for (int i = 0; i < kilograms.length; i++) gross += Math.max(0, kilograms[i]) * Math.max(0, prices[i]);
            String error = ElekSaleModel.partialError(pay, gross, partial);
            if (!error.isEmpty()) {
                if (etSalePartial != null) etSalePartial.setError(error);
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        saving =
                true;
        btnSaveElek.setEnabled(
                false
        );


        btnSaveElek.setText(
                "Kaydediliyor..."
        );


        long result;


        try {

            result =
                    databaseHelper.addElekRecord(
                            selectedGardenId,
                            recordDateIso,
                            grades,
                            kilograms,
                            prices,
                            ""
                    );


        } catch (Exception e) {

            result =
                    -1;
        }


        saving =
                false;


        btnSaveElek.setEnabled(
                true
        );


        btnSaveElek.setText(
                ElekSaleModel.saveLabel(saleOnSave)
        );


        // ========================================================
        // BAŞARILI
        // ========================================================

        if (result != -1) {
            long saleId = -1;
            if (wantSale) {
                saleId = new ElekSaleBridge(databaseHelper).createSale(result, pay, partial, buyer);
            }
            Toast.makeText(
                    this,
                    ElekSaleModel.successToast(saleId > 0, wantSale),
                    wantSale && saleId <= 0 ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT
            ).show();
            syncElekCloud(
                    result
            );
            if (saleId > 0) syncSaleCloud(saleId);
            if (etSalePartial != null) etSalePartial.setText("");
            if (etSaleBuyer != null) etSaleBuyer.setText("");


            clearKgFields();


            // Yeni kayıt sonrası arşivi anında yenile
            loadSeasonArchive();


        } else {

            Toast.makeText(
                    this,
                    "Elek kaydı kaydedilemedi.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }


    private void syncSaleCloud(long saleId) {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) return;
            new ProductionCloudRepository(databaseHelper).syncSaleByLocalId(user.getUid(), saleId);
        } catch (Exception ignored) {
            // Offline-first: yerel kayıt tamam; sonraki genel senkron yeniden dener.
        }
    }

    /**
     * "Bu zeytini sattım" anahtarı + tahsilat seçimi. Tek ekran düzeninde Kaydet altta sabit şeritte
     * durduğu için bölüm, formdaki elek kartının altındaki elekSaleSlot alanına eklenir.
     */
    private void setupSaleOnSave() {
        if (btnSaveElek == null) return;
        android.view.View slot = findViewById(R.id.elekSaleSlot);
        if (!(slot instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup parent = (android.view.ViewGroup) slot;
        float d = getResources().getDisplayMetrics().density;
        int text = getResources().getColor(R.color.zd_text, null);
        int soft = getResources().getColor(R.color.zd_text_soft, null);
        int muted = getResources().getColor(R.color.zd_muted, null);

        saleToggle = new android.widget.LinearLayout(this);
        saleToggle.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        saleToggle.setGravity(android.view.Gravity.CENTER_VERTICAL);
        saleToggle.setPadding(Math.round(14 * d), Math.round(8 * d), Math.round(14 * d), Math.round(8 * d));
        android.widget.LinearLayout copy = new android.widget.LinearLayout(this);
        copy.setOrientation(android.widget.LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("Bu zeytini sattım"); title.setTextSize(15); title.setTextColor(text);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        TextView sub = new TextView(this);
        sub.setText("Açıksa satış kaydı elekten kendiliğinden oluşur.");
        sub.setTextSize(12); sub.setTextColor(soft); sub.setPadding(0, Math.round(2 * d), 0, 0);
        sub.setSingleLine(true); sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(title); copy.addView(sub);
        saleToggleSub = sub;
        saleToggle.addView(copy, new android.widget.LinearLayout.LayoutParams(0, -2, 1));
        android.widget.FrameLayout switchView = new android.widget.FrameLayout(this);
        saleTrack = new android.view.View(this);
        saleKnob = new android.view.View(this);
        switchView.addView(saleTrack, new android.widget.FrameLayout.LayoutParams(Math.round(48 * d), Math.round(28 * d)));
        android.widget.FrameLayout.LayoutParams kp = new android.widget.FrameLayout.LayoutParams(Math.round(22 * d), Math.round(22 * d));
        kp.topMargin = Math.round(3 * d);
        switchView.addView(saleKnob, kp);
        android.widget.LinearLayout.LayoutParams sp = new android.widget.LinearLayout.LayoutParams(Math.round(48 * d), Math.round(28 * d));
        sp.leftMargin = Math.round(12 * d);
        saleToggle.addView(switchView, sp);
        saleToggle.setOnClickListener(v -> { saleOnSave = !saleOnSave; renderSaleOnSave(); refreshElekInsights(); });

        salePanel = new android.widget.LinearLayout(this);
        salePanel.setOrientation(android.widget.LinearLayout.VERTICAL);
        salePanel.setPadding(Math.round(14 * d), Math.round(10 * d), Math.round(14 * d), Math.round(10 * d));
        salePanel.setBackground(roundRect(getResources().getColor(R.color.zd_surface, null),
                getResources().getColor(R.color.zd_border, null), 18 * d));
        TextView question = new TextView(this);
        question.setText("Parayı aldın mı?"); question.setTextSize(13); question.setTextColor(text);
        question.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        salePanel.addView(question);
        android.widget.LinearLayout chips = new android.widget.LinearLayout(this);
        chips.setPadding(0, Math.round(6 * d), 0, 0);
        salePayChips = new TextView[ElekSaleModel.PAY_LABELS.length];
        for (int i = 0; i < salePayChips.length; i++) {
            final int option = i;
            TextView chip = new TextView(this);
            chip.setText(ElekSaleModel.PAY_LABELS[i]); chip.setTextSize(12);
            chip.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setOnClickListener(v -> { salePay = option; renderSaleOnSave(); });
            android.widget.LinearLayout.LayoutParams cp = new android.widget.LinearLayout.LayoutParams(0, Math.round(36 * d), 1);
            if (i > 0) cp.leftMargin = Math.round(6 * d);
            chips.addView(chip, cp);
            salePayChips[i] = chip;
        }
        salePanel.addView(chips);
        // Alınan tutar ve alıcı yan yana: "Bir kısmı" seçili değilse tutar gizlenir, alıcı tüm satırı alır.
        android.widget.LinearLayout fieldRow = new android.widget.LinearLayout(this);
        fieldRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        etSalePartial = saleField("Alınan TL", android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        android.widget.LinearLayout.LayoutParams partialParams = new android.widget.LinearLayout.LayoutParams(0, -2, 1);
        partialParams.setMarginEnd(Math.round(8 * d));
        fieldRow.addView(etSalePartial, partialParams);
        etSaleBuyer = saleField("Kime sattın? ›", android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        // Alıcı yazılmaz, rehberden seçilir (yeni alıcı da pencereden eklenir): aynı kişi iki kez oluşmaz.
        etSaleBuyer.setFocusable(false);
        etSaleBuyer.setLongClickable(false);
        etSaleBuyer.setContentDescription("Alıcı seç, isteğe bağlı");
        etSaleBuyer.setOnClickListener(v -> BuyerPicker.show(this, databaseHelper, etSaleBuyer.getText().toString(),
                "Alıcı belirtme", name -> etSaleBuyer.setText(name)));
        fieldRow.addView(etSaleBuyer, new android.widget.LinearLayout.LayoutParams(0, -2, 1.4f));
        salePanel.addView(fieldRow, saleFieldParams(d));

        android.widget.LinearLayout.LayoutParams tp = new android.widget.LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = Math.round(10 * d);
        parent.addView(saleToggle, tp);
        android.widget.LinearLayout.LayoutParams pp = new android.widget.LinearLayout.LayoutParams(-1, -2);
        pp.topMargin = Math.round(6 * d);
        parent.addView(salePanel, pp);
        renderSaleOnSave();
    }

    private EditText saleField(String hint, int inputType) {
        float d = getResources().getDisplayMetrics().density;
        EditText field = new EditText(this);
        field.setHint(hint); field.setInputType(inputType); field.setTextSize(14);
        field.setTextColor(getResources().getColor(R.color.zd_text, null));
        field.setHintTextColor(getResources().getColor(R.color.zd_muted, null));
        field.setBackground(roundRect(getResources().getColor(R.color.zd_bg, null),
                getResources().getColor(R.color.zd_border, null), 12 * d));
        int pad = Math.round(12 * d);
        field.setPadding(pad, Math.round(9 * d), pad, Math.round(9 * d));
        field.setSingleLine(true);
        return field;
    }

    private android.widget.LinearLayout.LayoutParams saleFieldParams(float d) {
        android.widget.LinearLayout.LayoutParams p = new android.widget.LinearLayout.LayoutParams(-1, -2);
        p.topMargin = Math.round(8 * d);
        return p;
    }

    private android.graphics.drawable.GradientDrawable roundRect(int fill, int stroke, float radius) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(fill); g.setCornerRadius(radius);
        g.setStroke(Math.max(1, Math.round(getResources().getDisplayMetrics().density)), stroke);
        return g;
    }

    private void renderSaleOnSave() {
        if (saleToggle == null) return;
        float d = getResources().getDisplayMetrics().density;
        int green = getResources().getColor(R.color.zd_green, null);
        int border = getResources().getColor(R.color.zd_border, null);
        int card = getResources().getColor(R.color.zd_card, null);
        int soft = getResources().getColor(R.color.zd_text_soft, null);
        int knob = getResources().getColor(R.color.zd_text, null);
        saleToggle.setBackground(roundRect(saleOnSave ? getResources().getColor(R.color.zd_green_soft, null) : card,
                saleOnSave ? green : border, 18 * d));
        saleTrack.setBackground(roundRect(saleOnSave ? green : border, saleOnSave ? green : border, 14 * d));
        saleKnob.setBackground(roundRect(knob, knob, 11 * d));
        ((android.widget.FrameLayout.LayoutParams) saleKnob.getLayoutParams()).leftMargin = Math.round((saleOnSave ? 23 : 3) * d);
        saleKnob.requestLayout();
        saleToggle.setContentDescription("Bu zeytini sattım, " + (saleOnSave ? "açık" : "kapalı"));
        salePanel.setVisibility(saleOnSave ? android.view.View.VISIBLE : android.view.View.GONE);
        // Açıkken açıklama satırı gizlenir; panel zaten ne olacağını gösteriyor (ekrana sığması için).
        if (saleToggleSub != null) saleToggleSub.setVisibility(saleOnSave ? android.view.View.GONE : android.view.View.VISIBLE);
        for (int i = 0; i < salePayChips.length; i++) {
            boolean on = i == salePay;
            salePayChips[i].setTextColor(on ? green : soft);
            salePayChips[i].setBackground(roundRect(on ? getResources().getColor(R.color.zd_green_soft, null) : 0x00000000,
                    on ? green : border, 20 * d));
            salePayChips[i].setContentDescription(ElekSaleModel.PAY_LABELS[i] + (on ? ", seçili" : ""));
        }
        etSalePartial.setVisibility(salePay == ElekSaleModel.PAY_PART ? android.view.View.VISIBLE : android.view.View.GONE);
        if (btnSaveElek != null && !saving) btnSaveElek.setText(ElekSaleModel.saveLabel(saleOnSave));
    }

    private void syncElekCloud(
            long elekId
    ) {

        if (elekId <= 0) {
            return;
        }

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            return;
        }

        new ProductionCloudRepository(
                databaseHelper
        ).syncElekByLocalId(
                user.getUid(),
                elekId
        );
    }


    // ============================================================
    // KG ALANLARINI TEMİZLE
    // ============================================================

    private void clearKgFields() {

        etKgDouble.setText(
                ""
        );


        etKg1.setText(
                ""
        );


        etKg2.setText(
                ""
        );


        etKg3.setText(
                ""
        );


        etKg4.setText(
                ""
        );


        etKgOil.setText(
                ""
        );


        calculateAll();
    }


    // ============================================================
    // HESAP FORMÜLÜ
    // ============================================================

    private String calculationText(
            double kg,
            double price
    ) {

        return formatNumber(
                kg
        )
                + " × "
                + formatNumber(
                price
        );
    }


    // ============================================================
    // TL / KG GÖRÜNÜMÜ
    // ============================================================

    private String moneyUnit(
            double value
    ) {

        return formatNumber(
                value
        )
                + " TL";
    }


    // ============================================================
    // PARA
    // ============================================================

    private String money(
            double value
    ) {

        return formatNumber(
                value
        )
                + " TL";
    }


    // ============================================================
    // SAYI FORMAT
    // ============================================================

    private String formatNumber(
            double value
    ) {

        if (numberFormat == null) {

            return String.valueOf(
                    value
            );
        }


        return numberFormat.format(
                value
        );
    }


    // ============================================================
    // EDITTEXT İÇİN SAYI
    // ============================================================

    private String plainNumber(
            double value
    ) {

        if (value == Math.rint(
                value
        )) {

            return String.valueOf(
                    (long) value
            );
        }


        return String.valueOf(
                value
        )
                .replace(
                        ".",
                        ","
                );
    }


    // ============================================================
    // EDITTEXT DOUBLE OKU
    // ============================================================

    private double readDouble(
            EditText editText
    ) {

        if (editText == null
                ||
                editText.getText() == null) {

            return 0;
        }


        String value =
                editText
                        .getText()
                        .toString()
                        .trim()
                        .replace(
                                ",",
                                "."
                        );


        if (value.isEmpty()) {

            return 0;
        }


        try {

            double result =
                    Double.parseDouble(
                            value
                    );


            if (result < 0) {

                return 0;
            }


            return result;


        } catch (Exception e) {

            return 0;
        }
    }


    // ============================================================
    // KAPANIŞ
    // ============================================================


    // ============================================================
    // ÖZET KARTI, DAĞILIM ŞERİDİ VE KAYDET DURUMU
    // Hesaplama calculateAll'da kalır; bu yöntem yalnızca gösterimi günceller.
    // ============================================================

    private void refreshElekInsights() {
        if (elekDistributionBar == null || tvElekDistributionLegend == null) {
            return;
        }

        double[] kg = {
                readDouble(etKgDouble),
                readDouble(etKg1),
                readDouble(etKg2),
                readDouble(etKg3),
                readDouble(etKg4),
                readDouble(etKgOil)
        };
        int[] colors = {
                getResources().getColor(R.color.elek_badge_double, null),
                getResources().getColor(R.color.elek_badge_one, null),
                getResources().getColor(R.color.elek_badge_two, null),
                getResources().getColor(R.color.elek_badge_three, null),
                getResources().getColor(R.color.elek_badge_four, null),
                getResources().getColor(R.color.elek_badge_oil, null)
        };
        TextView[] rowTotals = {
                tvTotalDouble, tvTotal1, tvTotal2, tvTotal3, tvTotal4, tvTotalOil
        };

        int text = getResources().getColor(R.color.zd_text, null);
        int muted = getResources().getColor(R.color.zd_muted, null);
        int gold = getResources().getColor(R.color.zd_warning, null);
        double totalKg = ElekInsights.total(kg);

        elekDistributionBar.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        boolean first = true;
        for (int i = 0; i < kg.length; i++) {
            if (rowTotals[i] != null) {
                rowTotals[i].setTextColor(kg[i] > 0 ? text : muted);
            }
            if (kg[i] <= 0) {
                continue;
            }
            android.view.View segment = new android.view.View(this);
            segment.setBackgroundColor(colors[i]);
            android.widget.LinearLayout.LayoutParams params =
                    new android.widget.LinearLayout.LayoutParams(0, -1, (float) kg[i]);
            if (!first) {
                params.setMarginStart(Math.round(2 * density));
            }
            first = false;
            elekDistributionBar.addView(segment, params);
        }
        elekDistributionBar.setClipToOutline(true);
        elekDistributionBar.setContentDescription(ElekInsights.accessibleLegend(kg));
        tvElekDistributionLegend.setText(ElekInsights.legend(kg));
        tvTotalRevenue.setTextColor(totalKg > 0 ? gold : muted);

        if (tvElekGardenLabel != null) {
            tvElekGardenLabel.setText(
                    ElekInsights.gardenLabel(selectedGardenId, homeActiveGardenId)
            );
        }

        if (btnSaveElek != null && !saving) {
            btnSaveElek.setEnabled(totalKg > 0);
            btnSaveElek.setTextColor(totalKg > 0
                    ? getResources().getColor(R.color.zd_bg, null)
                    : muted);
        }
        if (tvElekSaveHint != null) {
            String date = tvElekDate == null ? "" : tvElekDate.getText().toString();
            String hint = ElekInsights.saveHint(totalKg, selectedGardenId > 0, selectedGardenName, date);
            if (totalKg > 0 && selectedGardenId > 0) {
                double[] price = {priceDouble, price1, price2, price3, price4, priceOil};
                double gross = 0;
                for (int i = 0; i < kg.length; i++) gross += Math.max(0, kg[i]) * Math.max(0, price[i]);
                hint = ElekSaleModel.saveHint(saleOnSave, gross, selectedGardenName, date);
            }
            tvElekSaveHint.setText(hint);
        }
    }

    // Satırdaki fiyat çipine dokununca mevcut fiyat düzenleyici o sınıfın alanıyla açılır.
    private void setupPriceChips() {
        TextView[] chips = {
                tvPriceDouble, tvPrice1, tvPrice2, tvPrice3, tvPrice4, tvPriceOil
        };
        for (int i = 0; i < chips.length; i++) {
            final int index = i;
            if (chips[i] == null) {
                continue;
            }
            chips[i].setOnClickListener(view -> openPriceEditor(index));
        }
    }

    private void openPriceEditor(int index) {
        showPriceDialog();
        EditText[] fields = priceFields();
        if (index >= 0 && index < fields.length && fields[index] != null) {
            fields[index].requestFocus();
            fields[index].selectAll();
        }
    }

    @Override
    protected void onDestroy() {
        if (priceDialog != null) priceDialog.dismiss();

        if (databaseHelper != null) {

            databaseHelper.close();
        }


        super.onDestroy();
    }
}