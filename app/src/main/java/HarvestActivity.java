package com.zeytindefteri.app;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
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

public class HarvestActivity extends Activity {

    // ============================================================
    // DATABASE
    // ============================================================

    private DatabaseHelper databaseHelper;


    // ============================================================
    // BAHÇE
    // ============================================================

    private Spinner spinnerGarden;

    private final ArrayList<Integer> gardenIds =
            new ArrayList<>();

    private final ArrayList<String> gardenNames =
            new ArrayList<>();

    private long requestedGardenId = -1;


    // ============================================================
    // TARİH
    // ============================================================

    private EditText etHarvestDate;

    private final Calendar selectedDate =
            Calendar.getInstance();


    // ============================================================
    // DOUBLE
    // ============================================================

    private EditText etKgDouble;
    private EditText etPriceDouble;
    private TextView tvValueDouble;


    // ============================================================
    // 1 NO
    // ============================================================

    private EditText etKg1;
    private EditText etPrice1;
    private TextView tvValue1;


    // ============================================================
    // 2 NO
    // ============================================================

    private EditText etKg2;
    private EditText etPrice2;
    private TextView tvValue2;


    // ============================================================
    // 3 NO
    // ============================================================

    private EditText etKg3;
    private EditText etPrice3;
    private TextView tvValue3;


    // ============================================================
    // 4 NO
    // ============================================================

    private EditText etKg4;
    private EditText etPrice4;
    private TextView tvValue4;


    // ============================================================
    // YAĞLIK
    // ============================================================

    private EditText etKgOil;
    private EditText etPriceOil;
    private TextView tvValueOil;


    // ============================================================
    // TOPLAMLAR
    // ============================================================

    private TextView tvTotalKg;
    private TextView tvTotalValue;

    private EditText etLaborCost;
    private EditText etTransportCost;
    private EditText etOtherCost;

    private TextView tvTotalHarvestCost;

    private EditText etHarvestNote;


    // ============================================================
    // BUTONLAR
    // ============================================================

    private TextView btnSaveHarvest;
    private TextView btnBack;


    private final Locale TR =
            new Locale("tr", "TR");


    // ============================================================
    // ON CREATE
    // ============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        if (getActionBar() != null) {
            getActionBar().hide();
        }

        setContentView(
                R.layout.activity_harvest
        );


        databaseHelper =
                new DatabaseHelper(this);


        requestedGardenId =
                getIntent()
                        .getLongExtra(
                                "garden_id",
                                -1
                        );


        findViews();

        loadGardens();

        setupDate();

        loadLastPrices();

        setupWatchers();

        setupButtons();

        updateTotals();
    }


    // ============================================================
    // VIEW'LAR
    // ============================================================

    private void findViews() {

        spinnerGarden =
                findViewById(
                        R.id.spinnerGarden
                );


        etHarvestDate =
                findViewById(
                        R.id.etHarvestDate
                );


        etKgDouble =
                findViewById(
                        R.id.etKgDouble
                );

        etPriceDouble =
                findViewById(
                        R.id.etPriceDouble
                );

        tvValueDouble =
                findViewById(
                        R.id.tvValueDouble
                );


        etKg1 =
                findViewById(
                        R.id.etKg1
                );

        etPrice1 =
                findViewById(
                        R.id.etPrice1
                );

        tvValue1 =
                findViewById(
                        R.id.tvValue1
                );


        etKg2 =
                findViewById(
                        R.id.etKg2
                );

        etPrice2 =
                findViewById(
                        R.id.etPrice2
                );

        tvValue2 =
                findViewById(
                        R.id.tvValue2
                );


        etKg3 =
                findViewById(
                        R.id.etKg3
                );

        etPrice3 =
                findViewById(
                        R.id.etPrice3
                );

        tvValue3 =
                findViewById(
                        R.id.tvValue3
                );


        etKg4 =
                findViewById(
                        R.id.etKg4
                );

        etPrice4 =
                findViewById(
                        R.id.etPrice4
                );

        tvValue4 =
                findViewById(
                        R.id.tvValue4
                );


        etKgOil =
                findViewById(
                        R.id.etKgOil
                );

        etPriceOil =
                findViewById(
                        R.id.etPriceOil
                );

        tvValueOil =
                findViewById(
                        R.id.tvValueOil
                );


        tvTotalKg =
                findViewById(
                        R.id.tvTotalKg
                );

        tvTotalValue =
                findViewById(
                        R.id.tvTotalValue
                );


        etLaborCost =
                findViewById(
                        R.id.etLaborCost
                );

        etTransportCost =
                findViewById(
                        R.id.etTransportCost
                );

        etOtherCost =
                findViewById(
                        R.id.etOtherCost
                );


        tvTotalHarvestCost =
                findViewById(
                        R.id.tvTotalHarvestCost
                );


        etHarvestNote =
                findViewById(
                        R.id.etHarvestNote
                );


        btnSaveHarvest =
                findViewById(
                        R.id.btnSaveHarvest
                );

        btnBack =
                findViewById(
                        R.id.btnBack
                );
    }


    // ============================================================
    // BAHÇELER
    // ============================================================

    private void loadGardens() {

        gardenIds.clear();
        gardenNames.clear();


        Cursor cursor =
                databaseHelper.getGardens();


        while (cursor.moveToNext()) {

            gardenIds.add(
                    cursor.getInt(
                            cursor.getColumnIndexOrThrow(
                                    "id"
                            )
                    )
            );


            gardenNames.add(
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                    "name"
                            )
                    )
            );
        }


        cursor.close();


        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        gardenNames
                );


        adapter.setDropDownViewResource(
                android.R.layout
                        .simple_spinner_dropdown_item
        );


        spinnerGarden.setAdapter(
                adapter
        );


        if (requestedGardenId != -1) {

            for (
                    int i = 0;
                    i < gardenIds.size();
                    i++
            ) {

                if (
                        gardenIds
                                .get(i)
                                .longValue()
                                == requestedGardenId
                ) {

                    spinnerGarden.setSelection(
                            i
                    );

                    break;
                }
            }
        }


        if (gardenIds.isEmpty()) {

            btnSaveHarvest.setEnabled(
                    false
            );

            btnSaveHarvest.setAlpha(
                    0.45f
            );


            Toast.makeText(
                    this,
                    "Önce bir bahçe eklemelisin",
                    Toast.LENGTH_LONG
            ).show();
        }
    }


    // ============================================================
    // TARİH
    // ============================================================

    private void setupDate() {

        updateDateText();


        etHarvestDate.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {

                        showDatePicker();
                    }
                }
        );
    }


    private void showDatePicker() {

        DatePickerDialog dialog =
                new DatePickerDialog(
                        this,

                        new DatePickerDialog
                                .OnDateSetListener() {

                            @Override
                            public void onDateSet(
                                    android.widget.DatePicker view,
                                    int year,
                                    int month,
                                    int dayOfMonth
                            ) {

                                selectedDate.set(
                                        Calendar.YEAR,
                                        year
                                );

                                selectedDate.set(
                                        Calendar.MONTH,
                                        month
                                );

                                selectedDate.set(
                                        Calendar.DAY_OF_MONTH,
                                        dayOfMonth
                                );


                                updateDateText();
                            }
                        },

                        selectedDate.get(
                                Calendar.YEAR
                        ),

                        selectedDate.get(
                                Calendar.MONTH
                        ),

                        selectedDate.get(
                                Calendar.DAY_OF_MONTH
                        )
                );


        dialog.show();
    }


    private void updateDateText() {

        SimpleDateFormat format =
                new SimpleDateFormat(
                        "dd.MM.yyyy",
                        TR
                );


        etHarvestDate.setText(
                format.format(
                        selectedDate.getTime()
                )
        );
    }


    // ============================================================
    // SON KULLANILAN FİYATLAR
    // ============================================================

    private void loadLastPrices() {

        setLastPrice(
                etPriceDouble,
                "DOUBLE"
        );

        setLastPrice(
                etPrice1,
                "1"
        );

        setLastPrice(
                etPrice2,
                "2"
        );

        setLastPrice(
                etPrice3,
                "3"
        );

        setLastPrice(
                etPrice4,
                "4"
        );

        setLastPrice(
                etPriceOil,
                "YAGLIK"
        );
    }


    private void setLastPrice(
            EditText editText,
            String grade
    ) {

        double price =
                databaseHelper
                        .getLastHarvestReferencePrice(
                                grade
                        );


        if (price > 0) {

            editText.setText(
                    formatNumber(
                            price
                    )
            );
        }
    }


    // ============================================================
    // CANLI HESAPLAMA
    // ============================================================

    private void setupWatchers() {

        EditText[] fields = {

                etKgDouble,
                etPriceDouble,

                etKg1,
                etPrice1,

                etKg2,
                etPrice2,

                etKg3,
                etPrice3,

                etKg4,
                etPrice4,

                etKgOil,
                etPriceOil,

                etLaborCost,
                etTransportCost,
                etOtherCost
        };


        for (EditText field : fields) {

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

                            updateTotals();
                        }


                        @Override
                        public void afterTextChanged(
                                Editable editable
                        ) {
                        }
                    }
            );
        }
    }


    // ============================================================
    // TOPLAMLARI HESAPLA
    // ============================================================

    private void updateTotals() {

        double kgDouble =
                readNumber(
                        etKgDouble
                );

        double priceDouble =
                readNumber(
                        etPriceDouble
                );


        double kg1 =
                readNumber(
                        etKg1
                );

        double price1 =
                readNumber(
                        etPrice1
                );


        double kg2 =
                readNumber(
                        etKg2
                );

        double price2 =
                readNumber(
                        etPrice2
                );


        double kg3 =
                readNumber(
                        etKg3
                );

        double price3 =
                readNumber(
                        etPrice3
                );


        double kg4 =
                readNumber(
                        etKg4
                );

        double price4 =
                readNumber(
                        etPrice4
                );


        double kgOil =
                readNumber(
                        etKgOil
                );

        double priceOil =
                readNumber(
                        etPriceOil
                );


        double valueDouble =
                kgDouble * priceDouble;

        double value1 =
                kg1 * price1;

        double value2 =
                kg2 * price2;

        double value3 =
                kg3 * price3;

        double value4 =
                kg4 * price4;

        double valueOil =
                kgOil * priceOil;


        tvValueDouble.setText(
                "Değer: "
                        + formatMoney(
                        valueDouble
                )
        );


        tvValue1.setText(
                "Değer: "
                        + formatMoney(
                        value1
                )
        );


        tvValue2.setText(
                "Değer: "
                        + formatMoney(
                        value2
                )
        );


        tvValue3.setText(
                "Değer: "
                        + formatMoney(
                        value3
                )
        );


        tvValue4.setText(
                "Değer: "
                        + formatMoney(
                        value4
                )
        );


        tvValueOil.setText(
                "Değer: "
                        + formatMoney(
                        valueOil
                )
        );


        double totalKg =
                kgDouble
                        + kg1
                        + kg2
                        + kg3
                        + kg4
                        + kgOil;


        double totalValue =
                valueDouble
                        + value1
                        + value2
                        + value3
                        + value4
                        + valueOil;


        tvTotalKg.setText(
                formatKg(
                        totalKg
                )
        );


        tvTotalValue.setText(
                formatMoney(
                        totalValue
                )
        );


        double harvestCost =
                readNumber(
                        etLaborCost
                )
                        +
                        readNumber(
                                etTransportCost
                        )
                        +
                        readNumber(
                                etOtherCost
                        );


        tvTotalHarvestCost.setText(
                "Toplam hasat masrafı: "
                        + formatMoney(
                        harvestCost
                )
        );
    }


    // ============================================================
    // BUTONLAR
    // ============================================================

    private void setupButtons() {

        btnBack.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {

                        finish();
                    }
                }
        );


        btnSaveHarvest.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {

                        saveHarvest();
                    }
                }
        );
    }


    // ============================================================
    // HASADI KAYDET
    // ============================================================

    private void saveHarvest() {

        if (gardenIds.isEmpty()) {

            Toast.makeText(
                    this,
                    "Bahçe bulunamadı",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        int gardenPosition =
                spinnerGarden
                        .getSelectedItemPosition();


        if (
                gardenPosition < 0
                        ||
                        gardenPosition >= gardenIds.size()
        ) {

            Toast.makeText(
                    this,
                    "Bahçe seçilemedi",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        int gardenId =
                gardenIds.get(
                        gardenPosition
                );


        String date =
                etHarvestDate
                        .getText()
                        .toString()
                        .trim();


        double kgDouble =
                readNumber(etKgDouble);

        double kg1 =
                readNumber(etKg1);

        double kg2 =
                readNumber(etKg2);

        double kg3 =
                readNumber(etKg3);

        double kg4 =
                readNumber(etKg4);

        double kgOil =
                readNumber(etKgOil);


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
                    "En az bir elek için hasat miktarı gir",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }


        double laborCost =
                readNumber(
                        etLaborCost
                );

        double transportCost =
                readNumber(
                        etTransportCost
                );

        double otherCost =
                readNumber(
                        etOtherCost
                );


        double totalHarvestCost =
                laborCost
                        + transportCost
                        + otherCost;


        String note =
                etHarvestNote
                        .getText()
                        .toString()
                        .trim();


        SQLiteDatabase db =
                databaseHelper
                        .getWritableDatabase();


        boolean success =
                false;

        long savedHarvestId =
                -1;


        db.beginTransaction();


        try {

            // ----------------------------------------------------
            // ANA HASAT KAYDI
            // ----------------------------------------------------

            long harvestId =
                    databaseHelper.addHarvest(
                            gardenId,
                            date,
                            totalKg,
                            laborCost,
                            transportCost,
                            otherCost,
                            note
                    );


            if (harvestId == -1) {

                throw new Exception(
                        "Hasat ana kaydı oluşturulamadı"
                );
            }


            savedHarvestId =
                    harvestId;


            // ----------------------------------------------------
            // ELEKLER
            // ----------------------------------------------------

            saveGrade(
                    harvestId,
                    "DOUBLE",
                    kgDouble,
                    readNumber(etPriceDouble),
                    date
            );


            saveGrade(
                    harvestId,
                    "1",
                    kg1,
                    readNumber(etPrice1),
                    date
            );


            saveGrade(
                    harvestId,
                    "2",
                    kg2,
                    readNumber(etPrice2),
                    date
            );


            saveGrade(
                    harvestId,
                    "3",
                    kg3,
                    readNumber(etPrice3),
                    date
            );


            saveGrade(
                    harvestId,
                    "4",
                    kg4,
                    readNumber(etPrice4),
                    date
            );


            saveGrade(
                    harvestId,
                    "YAGLIK",
                    kgOil,
                    readNumber(etPriceOil),
                    date
            );


            /*
             * Hasadı genel işlem hareketlerine de ekliyoruz.
             *
             * Bunun iki faydası var:
             *
             * 1) Ana ekrandaki Son İşlemler'de görünür.
             * 2) Hasat işçilik/nakliye/diğer masrafları
             *    Toplam Masraf hesabına dahil olur.
             */

            long operationId =
                    databaseHelper.addOperation(
                            gardenId,
                            "Hasat",
                            date,
                            totalKg,
                            "kg",
                            totalHarvestCost,
                            note
                    );


            if (operationId == -1) {

                throw new Exception(
                        "Hasat işlem hareketi oluşturulamadı"
                );
            }


            db.setTransactionSuccessful();

            success = true;

        } catch (Exception e) {

            success = false;

        } finally {

            db.endTransaction();
        }


        if (success) {

            Toast.makeText(
                    this,
                    "Hasat kaydedildi ✓",
                    Toast.LENGTH_SHORT
            ).show();


            syncHarvestCloud(
                    savedHarvestId
            );


            setResult(
                    RESULT_OK
            );


            finish();

        } else {

            Toast.makeText(
                    this,
                    "Hasat kaydedilemedi",
                    Toast.LENGTH_LONG
            ).show();
        }
    }


    // ============================================================
    // ELEK KAYDET
    // ============================================================

    private void saveGrade(
            long harvestId,
            String grade,
            double kg,
            double price,
            String priceDate
    ) throws Exception {

        /*
         * 0 kg olan eleği veritabanına yazmıyoruz.
         */

        if (kg <= 0) {
            return;
        }


        long result =
                databaseHelper.addHarvestGrade(
                        harvestId,
                        grade,
                        kg,
                        price,
                        priceDate
                );


        if (result == -1) {

            throw new Exception(
                    "Elek kaydı oluşturulamadı"
            );
        }
    }


    private void syncHarvestCloud(
            long harvestId
    ) {

        if (harvestId <= 0) {
            return;
        }

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            return;
        }

        new ProductionCloudRepository(
                databaseHelper
        ).syncHarvestByLocalId(
                user.getUid(),
                harvestId
        );
    }


    // ============================================================
    // SAYI OKUMA
    // ============================================================

    private double readNumber(
            EditText editText
    ) {

        String value =
                editText
                        .getText()
                        .toString()
                        .trim();


        if (value.isEmpty()) {
            return 0;
        }


        value =
                value
                        .replace(" ", "")
                        .replace("TL", "")
                        .replace("tl", "")
                        .replace("kg", "")
                        .trim();


        /*
         * Türkçe sayı girişi:
         *
         * 2.400      -> 2400
         * 2.400,50   -> 2400.50
         * 8,5        -> 8.5
         * 8.5        -> 8.5
         */

        if (
                value.contains(".")
                        &&
                        value.contains(",")
        ) {

            value =
                    value
                            .replace(".", "")
                            .replace(",", ".");

        } else if (value.contains(",")) {

            value =
                    value.replace(
                            ",",
                            "."
                    );

        } else if (value.contains(".")) {

            int firstDot =
                    value.indexOf(".");

            int lastDot =
                    value.lastIndexOf(".");


            if (firstDot != lastDot) {

                value =
                        value.replace(
                                ".",
                                ""
                        );

            } else {

                int digitsAfterDot =
                        value.length()
                                - lastDot
                                - 1;


                if (
                        digitsAfterDot == 3
                                &&
                                lastDot > 0
                ) {

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
    // FORMAT
    // ============================================================

    private String formatMoney(
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
}