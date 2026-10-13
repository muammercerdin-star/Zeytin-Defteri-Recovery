package com.zeytindefteri.app;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class NewOperationActivity extends Activity {

    // ============================================================
    // DATABASE
    // ============================================================

    private DatabaseHelper databaseHelper;
    private OperationCloudRepository operationCloudRepository;


    // ============================================================
    // VIEW'LAR
    // ============================================================

    private Spinner spinnerGarden;
    private Spinner spinnerType;
    private Spinner spinnerUnit;

    private EditText etDate;
    private EditText etQuantity;
    private EditText etCost;
    private EditText etNote;

    private TextView tvQuantityLabel;
    private TextView tvOperationHint;

    private TextView btnSaveOperation;
    private TextView btnBack;


    // ============================================================
    // BAHÇELER
    // ============================================================

    private final ArrayList<Integer> gardenIds =
            new ArrayList<>();

    private final ArrayList<String> gardenNames =
            new ArrayList<>();


    // ============================================================
    // İŞLEM TÜRLERİ
    // ============================================================

    private final List<String> operationTypes =
            Arrays.asList(
                    "Sulama",
                    "Gübre",
                    "İlaçlama",
                    "Budama",
                    "Sinek tuzağı",
                    "Toprak İşleme",
                    "İşçilik",
                    "Mazot",
                    "Nakliye",
                    "Diğer"
            );


    // ============================================================
    // TARİH
    // ============================================================

    private final Calendar selectedDate =
            Calendar.getInstance();

    private final Locale TR =
            new Locale("tr", "TR");


    // ============================================================
    // GELEN VERİ
    // ============================================================

    private long requestedGardenId = -1;

    private String requestedOperationType = null;


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
                R.layout.activity_new_operation
        );


        databaseHelper =
                new DatabaseHelper(this);

        operationCloudRepository =
                new OperationCloudRepository(
                        databaseHelper
                );


        requestedGardenId =
                getIntent()
                        .getLongExtra(
                                "garden_id",
                                -1
                        );


        requestedOperationType =
                getIntent()
                        .getStringExtra(
                                "operation_type"
                        );


        findViews();

        loadGardens();

        loadOperationTypes();

        setupDate();

        setupListeners();

        applyIncomingSelection();

        updateOperationForm();
    }


    // ============================================================
    // VIEW'LARI BUL
    // ============================================================

    private void findViews() {

        spinnerGarden =
                findViewById(
                        R.id.spinnerGarden
                );

        spinnerType =
                findViewById(
                        R.id.spinnerType
                );

        spinnerUnit =
                findViewById(
                        R.id.spinnerUnit
                );


        etDate =
                findViewById(
                        R.id.etDate
                );

        etQuantity =
                findViewById(
                        R.id.etQuantity
                );

        etCost =
                findViewById(
                        R.id.etCost
                );

        etNote =
                findViewById(
                        R.id.etNote
                );


        tvQuantityLabel =
                findViewById(
                        R.id.tvQuantityLabel
                );

        tvOperationHint =
                findViewById(
                        R.id.tvOperationHint
                );


        btnSaveOperation =
                findViewById(
                        R.id.btnSaveOperation
                );

        btnBack =
                findViewById(
                        R.id.btnBack
                );
    }


    // ============================================================
    // BAHÇELERİ YÜKLE
    // ============================================================

    private void loadGardens() {

        gardenIds.clear();

        gardenNames.clear();


        Cursor cursor =
                databaseHelper.getGardens();


        while (cursor.moveToNext()) {

            gardenIds.add(
                    cursor.getInt(
                            cursor
                                    .getColumnIndexOrThrow(
                                            "id"
                                    )
                    )
            );


            gardenNames.add(
                    cursor.getString(
                            cursor
                                    .getColumnIndexOrThrow(
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


        if (gardenIds.isEmpty()) {

            btnSaveOperation.setEnabled(
                    false
            );

            btnSaveOperation.setAlpha(
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
    // İŞLEM TÜRLERİNİ YÜKLE
    // ============================================================

    private void loadOperationTypes() {

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        operationTypes
                );


        adapter.setDropDownViewResource(
                android.R.layout
                        .simple_spinner_dropdown_item
        );


        spinnerType.setAdapter(
                adapter
        );
    }


    // ============================================================
    // GELEN BAHÇE / İŞLEM SEÇİMİNİ UYGULA
    // ============================================================

    private void applyIncomingSelection() {

        // --------------------------------------------------------
        // Bahçe detay ekranından gelmiş olabilir
        // --------------------------------------------------------

        if (requestedGardenId != -1) {

            for (
                    int i = 0;
                    i < gardenIds.size();
                    i++
            ) {

                if (
                        gardenIds.get(i)
                                == requestedGardenId
                ) {

                    spinnerGarden.setSelection(
                            i
                    );

                    break;
                }
            }
        }


        // --------------------------------------------------------
        // Ana ekrandaki hızlı işlemden gelmiş olabilir
        // --------------------------------------------------------

        if (
                requestedOperationType != null
                        && !requestedOperationType
                        .trim()
                        .isEmpty()
        ) {

            for (
                    int i = 0;
                    i < operationTypes.size();
                    i++
            ) {

                if (
                        operationTypes
                                .get(i)
                                .equalsIgnoreCase(
                                        requestedOperationType
                                )
                ) {

                    spinnerType.setSelection(
                            i
                    );

                    break;
                }
            }
        }
    }


    // ============================================================
    // TARİH
    // ============================================================

    private void setupDate() {

        updateDateText();


        etDate.setOnClickListener(
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


        etDate.setText(
                format.format(
                        selectedDate.getTime()
                )
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


        btnSaveOperation.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {

                        saveOperation();
                    }
                }
        );


        spinnerType.setOnItemSelectedListener(
                new AdapterView
                        .OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {

                        updateOperationForm();
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
    // İŞLEM TÜRÜNE GÖRE FORMU DEĞİŞTİR
    // ============================================================

    private void updateOperationForm() {

        if (
                spinnerType.getSelectedItem()
                        == null
        ) {

            return;
        }


        String type =
                spinnerType
                        .getSelectedItem()
                        .toString();


        if (type.equals("Sulama")) {

            tvQuantityLabel.setText(
                    "Su / Süre"
            );

            etQuantity.setHint(
                    "Örn. 4"
            );

            setUnits(
                    Arrays.asList(
                            "saat",
                            "m³",
                            "L"
                    )
            );

            tvOperationHint.setText(
                    "💧 Sulamada çalışma süresi veya verilen su miktarını kaydedebilirsin."
            );

        } else if (type.equals("Gübre")) {

            tvQuantityLabel.setText(
                    "Gübre Miktarı"
            );

            etQuantity.setHint(
                    "Örn. 18"
            );

            setUnits(
                    Arrays.asList(
                            "kg",
                            "g",
                            "L",
                            "ml"
                    )
            );

            tvOperationHint.setText(
                    "🌱 Gübre adını açıklamaya yazabilirsin. Örn. MKP 0-52-34, 18 kg."
            );

        } else if (type.equals("İlaçlama")) {

            tvQuantityLabel.setText(
                    "Ürün Miktarı"
            );

            etQuantity.setHint(
                    "Örn. 500"
            );

            setUnits(
                    Arrays.asList(
                            "ml",
                            "L",
                            "g",
                            "kg"
                    )
            );

            tvOperationHint.setText(
                    "🧴 Kullanılan ilacın veya yaprak ürününün adını açıklamaya kaydet."
            );

        } else if (type.equals("Budama")) {

            tvQuantityLabel.setText(
                    "Budanan Miktar"
            );

            etQuantity.setHint(
                    "Örn. 120"
            );

            setUnits(
                    Arrays.asList(
                            "ağaç",
                            "saat",
                            "gün"
                    )
            );

            tvOperationHint.setText(
                    "✂ Budanan ağaç sayısını veya harcanan çalışma süresini kaydedebilirsin."
            );

        } else if (
                type.equals(
                        "Sinek tuzağı"
                )
        ) {

            tvQuantityLabel.setText(
                    "Tuzak Sayısı"
            );

            etQuantity.setHint(
                    "Örn. 20"
            );

            setUnits(
                    Arrays.asList(
                            "adet"
                    )
            );

            tvOperationHint.setText(
                    "🪰 Asılan veya yenilenen zeytin sineği tuzağı sayısını kaydet."
            );

        } else if (
                type.equals(
                        "Toprak İşleme"
                )
        ) {

            tvQuantityLabel.setText(
                    "İşlenen Alan / Süre"
            );

            etQuantity.setHint(
                    "Örn. 8"
            );

            setUnits(
                    Arrays.asList(
                            "dekar",
                            "saat",
                            "gün"
                    )
            );

            tvOperationHint.setText(
                    "🚜 Sürüm, çapalama veya diğer toprak işlemlerini kaydedebilirsin."
            );

        } else if (
                type.equals(
                        "İşçilik"
                )
        ) {

            tvQuantityLabel.setText(
                    "İşçilik"
            );

            etQuantity.setHint(
                    "Örn. 3"
            );

            setUnits(
                    Arrays.asList(
                            "gün",
                            "kişi",
                            "saat"
                    )
            );

            tvOperationHint.setText(
                    "👷 İşçilik süresini veya çalışan kişi sayısını kaydet."
            );

        } else if (
                type.equals(
                        "Mazot"
                )
        ) {

            tvQuantityLabel.setText(
                    "Mazot Miktarı"
            );

            etQuantity.setHint(
                    "Örn. 25"
            );

            setUnits(
                    Arrays.asList(
                            "L"
                    )
            );

            tvOperationHint.setText(
                    "⛽ Bahçede kullanılan mazot miktarı ve masrafını kaydet."
            );

        } else if (
                type.equals(
                        "Nakliye"
                )
        ) {

            tvQuantityLabel.setText(
                    "Nakliye"
            );

            etQuantity.setHint(
                    "Örn. 1"
            );

            setUnits(
                    Arrays.asList(
                            "sefer",
                            "km",
                            "ton"
                    )
            );

            tvOperationHint.setText(
                    "🚚 Nakliye seferini, mesafesini veya taşınan miktarı kaydedebilirsin."
            );

        } else {

            tvQuantityLabel.setText(
                    "Miktar"
            );

            etQuantity.setHint(
                    "Örn. 1"
            );

            setUnits(
                    Arrays.asList(
                            "adet",
                            "kg",
                            "L",
                            "saat",
                            "gün"
                    )
            );

            tvOperationHint.setText(
                    "📝 Diğer bahçe işlemlerini açıklamasıyla birlikte kaydedebilirsin."
            );
        }
    }


    // ============================================================
    // BİRİM SPINNER
    // ============================================================

    private void setUnits(
            List<String> units
    ) {

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        units
                );


        adapter.setDropDownViewResource(
                android.R.layout
                        .simple_spinner_dropdown_item
        );


        spinnerUnit.setAdapter(
                adapter
        );
    }


    // ============================================================
    // KAYDET
    // ============================================================

    private void saveOperation() {

        if (gardenIds.isEmpty()) {

            Toast.makeText(
                    this,
                    "Önce bir bahçe eklemelisin",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        int gardenPosition =
                spinnerGarden
                        .getSelectedItemPosition();


        if (
                gardenPosition < 0
                        || gardenPosition
                        >= gardenIds.size()
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


        String type =
                spinnerType
                        .getSelectedItem()
                        .toString();


        String date =
                etDate
                        .getText()
                        .toString()
                        .trim();


        double quantity =
                readDouble(
                        etQuantity
                );


        String unit = "";

        if (
                spinnerUnit.getSelectedItem()
                        != null
        ) {

            unit =
                    spinnerUnit
                            .getSelectedItem()
                            .toString();
        }


        double cost =
                readDouble(
                        etCost
                );


        String note =
                etNote
                        .getText()
                        .toString()
                        .trim();


        long result =
                databaseHelper.addOperation(
                        gardenId,
                        type,
                        date,
                        quantity,
                        unit,
                        cost,
                        note
                );


        if (result != -1) {

            syncSavedOperationToCloud(
                    result
            );

            Toast.makeText(
                    this,
                    "İşlem kaydedildi ✓",
                    Toast.LENGTH_SHORT
            ).show();


            setResult(
                    RESULT_OK
            );


            finish();

        } else {

            Toast.makeText(
                    this,
                    "İşlem kaydedilemedi",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }


    // ============================================================
    // BULUT SENKRONU
    // ============================================================

    private void syncSavedOperationToCloud(
            long operationId
    ) {

        if (operationCloudRepository == null
                || operationId <= 0) {
            return;
        }

        FirebaseUser user =
                FirebaseAuth
                        .getInstance()
                        .getCurrentUser();

        if (user == null) {
            return;
        }

        String uid =
                user.getUid();

        if (uid == null
                || uid.trim().isEmpty()) {
            return;
        }

        operationCloudRepository
                .syncOperationByLocalId(
                        uid,
                        operationId
                );
    }


    // ============================================================
    // SAYI OKU
    // ============================================================

    private double readDouble(
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


        // Türkçe virgül desteği
        value =
                value.replace(
                        ",",
                        "."
                );


        /*
         * Kullanıcı 2.400 yazarsa bunu
         * iki bin dört yüz olarak düşünme ihtimali yüksek.
         *
         * Ama Android numberDecimal klavyesi genelde
         * decimal ayırıcı kullanır.
         *
         * Şimdilik sadece virgülü noktaya çeviriyoruz.
         */


        try {

            return Double.parseDouble(
                    value
            );

        } catch (Exception e) {

            return 0;
        }
    }
}