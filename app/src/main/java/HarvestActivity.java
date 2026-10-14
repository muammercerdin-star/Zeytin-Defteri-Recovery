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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

/**
 * Hasat kaydı. Boy satırları include_grade_rows ile satış formuyla ortaktır; sayı okuma ve
 * biçimleme GradeFormModel'dedir. Kayıt tek işlemde: hasat + boylar + "Hasat" işlem hareketi.
 */
public class HarvestActivity extends Activity {

    private DatabaseHelper databaseHelper;

    private Spinner spinnerGarden;
    private final ArrayList<Integer> gardenIds = new ArrayList<>();
    private final ArrayList<String> gardenNames = new ArrayList<>();

    private EditText etHarvestDate, etLaborCost, etTransportCost, etOtherCost, etHarvestNote;
    private TextView tvTotalKg, tvTotalValue, tvTotalHarvestCost, tvCostsToggle, tvSaveSummary, btnSaveHarvest;
    private View containerHarvestCosts;

    private final EditText[] kgFields = new EditText[GradeFormModel.CODES.length];
    private final EditText[] priceFields = new EditText[GradeFormModel.CODES.length];
    private final TextView[] valueViews = new TextView[GradeFormModel.CODES.length];

    private final Calendar selectedDate = Calendar.getInstance();
    private final Locale TR = new Locale("tr", "TR");
    private boolean costsOpen = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().hide();
        getWindow().setStatusBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().setNavigationBarColor(GreenTheme.color(R.color.zd_bg));
        setContentView(R.layout.activity_harvest);

        databaseHelper = new DatabaseHelper(this);
        findViews();
        loadGardens(getIntent().getLongExtra("garden_id", -1));

        if (savedInstanceState != null) {
            selectedDate.setTimeInMillis(savedInstanceState.getLong("harvest_date", selectedDate.getTimeInMillis()));
            costsOpen = savedInstanceState.getBoolean("harvest_costs_open", true);
        } else {
            loadLastPrices();
        }
        updateDateText();
        setupWatchers();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnSaveHarvest.setOnClickListener(v -> saveHarvest());
        etHarvestDate.setOnClickListener(v -> showDatePicker());
        findViewById(R.id.btnToggleCosts).setOnClickListener(v -> { costsOpen = !costsOpen; updateTotals(); });
        updateTotals();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putLong("harvest_date", selectedDate.getTimeInMillis());
        state.putBoolean("harvest_costs_open", costsOpen);
    }

    private void findViews() {
        spinnerGarden = findViewById(R.id.spinnerGarden);
        etHarvestDate = findViewById(R.id.etHarvestDate);
        etLaborCost = findViewById(R.id.etLaborCost);
        etTransportCost = findViewById(R.id.etTransportCost);
        etOtherCost = findViewById(R.id.etOtherCost);
        etHarvestNote = findViewById(R.id.etHarvestNote);
        tvTotalKg = findViewById(R.id.tvTotalKg);
        tvTotalValue = findViewById(R.id.tvTotalValue);
        tvTotalHarvestCost = findViewById(R.id.tvTotalHarvestCost);
        tvCostsToggle = findViewById(R.id.tvCostsToggle);
        tvSaveSummary = findViewById(R.id.tvSaveSummary);
        containerHarvestCosts = findViewById(R.id.containerHarvestCosts);
        btnSaveHarvest = findViewById(R.id.btnSaveHarvest);

        int[] kgIds = {R.id.etKgDouble, R.id.etKg1, R.id.etKg2, R.id.etKg3, R.id.etKg4, R.id.etKgOil};
        int[] priceIds = {R.id.etPriceDouble, R.id.etPrice1, R.id.etPrice2, R.id.etPrice3, R.id.etPrice4, R.id.etPriceOil};
        int[] valueIds = {R.id.tvValueDouble, R.id.tvValue1, R.id.tvValue2, R.id.tvValue3, R.id.tvValue4, R.id.tvValueOil};
        for (int i = 0; i < GradeFormModel.CODES.length; i++) {
            kgFields[i] = findViewById(kgIds[i]);
            priceFields[i] = findViewById(priceIds[i]);
            valueViews[i] = findViewById(valueIds[i]);
        }
    }

    private void loadGardens(long requestedGardenId) {
        gardenIds.clear();
        gardenNames.clear();
        Cursor cursor = databaseHelper.getGardens();
        try {
            while (cursor.moveToNext()) {
                gardenIds.add(cursor.getInt(cursor.getColumnIndexOrThrow("id")));
                gardenNames.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            }
        } finally {
            cursor.close();
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, gardenNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGarden.setAdapter(adapter);
        if (requestedGardenId != -1) {
            for (int i = 0; i < gardenIds.size(); i++) {
                if (gardenIds.get(i).longValue() == requestedGardenId) { spinnerGarden.setSelection(i); break; }
            }
        }
        if (gardenIds.isEmpty()) {
            btnSaveHarvest.setEnabled(false);
            btnSaveHarvest.setAlpha(0.45f);
            Toast.makeText(this, "Önce bir bahçe eklemelisin", Toast.LENGTH_LONG).show();
        }
    }

    private void showDatePicker() {
        new DatePickerDialog(this, (view, year, month, day) -> {
            selectedDate.set(Calendar.YEAR, year);
            selectedDate.set(Calendar.MONTH, month);
            selectedDate.set(Calendar.DAY_OF_MONTH, day);
            updateDateText();
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateText() {
        etHarvestDate.setText(new SimpleDateFormat("dd.MM.yyyy", TR).format(selectedDate.getTime()));
    }

    /** Her boy için son kullanılan referans fiyatı öneri olarak doldurur. */
    private void loadLastPrices() {
        for (int i = 0; i < GradeFormModel.CODES.length; i++) {
            double price = databaseHelper.getLastHarvestReferencePrice(GradeFormModel.CODES[i]);
            if (price > 0) priceFields[i].setText(GradeFormModel.number(price));
        }
    }

    private void setupWatchers() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateTotals(); }
            @Override public void afterTextChanged(Editable e) { }
        };
        for (int i = 0; i < GradeFormModel.CODES.length; i++) {
            kgFields[i].addTextChangedListener(watcher);
            priceFields[i].addTextChangedListener(watcher);
        }
        etLaborCost.addTextChangedListener(watcher);
        etTransportCost.addTextChangedListener(watcher);
        etOtherCost.addTextChangedListener(watcher);
    }

    private void updateTotals() {
        int text = GreenTheme.color(R.color.zd_text), muted = GreenTheme.color(R.color.zd_muted);
        double totalKg = 0, totalValue = 0;
        for (int i = 0; i < GradeFormModel.CODES.length; i++) {
            double kg = GradeFormModel.parse(kgFields[i].getText().toString());
            double price = GradeFormModel.parse(priceFields[i].getText().toString());
            totalKg += kg;
            totalValue += kg * price;
            valueViews[i].setText(GradeFormModel.lineValue(kg, price));
            valueViews[i].setTextColor(kg > 0 && price > 0 ? text : muted);
        }
        double cost = harvestCost();
        tvTotalKg.setText(GradeFormModel.kg(totalKg));
        tvTotalHarvestCost.setText(GradeFormModel.money(cost));
        tvTotalValue.setText(GradeFormModel.money(totalValue));
        containerHarvestCosts.setVisibility(costsOpen ? View.VISIBLE : View.GONE);
        tvCostsToggle.setText("Toplam " + GradeFormModel.money(cost) + (costsOpen ? "  ▴" : "  ▾"));
        findViewById(R.id.btnToggleCosts).setContentDescription(
                "Hasat masrafları, toplam " + GradeFormModel.money(cost) + (costsOpen ? ", gizle" : ", göster"));
        tvSaveSummary.setText(totalKg > 0
                ? GradeFormModel.kg(totalKg) + " · tahmini " + GradeFormModel.money(totalValue)
                : "Kilo girilen boylar kaydedilir; boş satırlar atlanır.");
    }

    private double harvestCost() {
        return GradeFormModel.parse(etLaborCost.getText().toString())
                + GradeFormModel.parse(etTransportCost.getText().toString())
                + GradeFormModel.parse(etOtherCost.getText().toString());
    }

    private void saveHarvest() {
        if (gardenIds.isEmpty()) {
            Toast.makeText(this, "Bahçe bulunamadı", Toast.LENGTH_SHORT).show();
            return;
        }
        int gardenPosition = spinnerGarden.getSelectedItemPosition();
        if (gardenPosition < 0 || gardenPosition >= gardenIds.size()) {
            Toast.makeText(this, "Bahçe seçilemedi", Toast.LENGTH_SHORT).show();
            return;
        }
        int gardenId = gardenIds.get(gardenPosition);
        String date = etHarvestDate.getText().toString().trim();

        double[] kilograms = new double[GradeFormModel.CODES.length];
        double totalKg = 0;
        for (int i = 0; i < kilograms.length; i++) {
            kilograms[i] = GradeFormModel.parse(kgFields[i].getText().toString());
            totalKg += kilograms[i];
        }
        if (totalKg <= 0) {
            Toast.makeText(this, "En az bir boy için hasat miktarı gir", Toast.LENGTH_LONG).show();
            return;
        }
        double laborCost = GradeFormModel.parse(etLaborCost.getText().toString());
        double transportCost = GradeFormModel.parse(etTransportCost.getText().toString());
        double otherCost = GradeFormModel.parse(etOtherCost.getText().toString());
        String note = etHarvestNote.getText().toString().trim();

        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        boolean success = false;
        long savedHarvestId = -1;
        db.beginTransaction();
        try {
            long harvestId = databaseHelper.addHarvest(gardenId, date, totalKg, laborCost, transportCost, otherCost, note);
            if (harvestId == -1) throw new Exception("Hasat ana kaydı oluşturulamadı");
            savedHarvestId = harvestId;
            for (int i = 0; i < kilograms.length; i++) {
                // 0 kg olan boy veritabanına yazılmaz.
                if (kilograms[i] <= 0) continue;
                double price = GradeFormModel.parse(priceFields[i].getText().toString());
                if (databaseHelper.addHarvestGrade(harvestId, GradeFormModel.CODES[i], kilograms[i], price, date) == -1) {
                    throw new Exception("Boy kaydı oluşturulamadı");
                }
            }
            // Hasat genel işlem hareketlerine de eklenir: Son İşlemler'de görünür ve
            // işçilik/nakliye/diğer masrafları toplam masrafa dahil olur.
            long operationId = databaseHelper.addOperation(gardenId, "Hasat", date, totalKg, "kg",
                    laborCost + transportCost + otherCost, note);
            if (operationId == -1) throw new Exception("Hasat işlem hareketi oluşturulamadı");
            db.setTransactionSuccessful();
            success = true;
        } catch (Exception e) {
            success = false;
        } finally {
            db.endTransaction();
        }

        if (success) {
            Toast.makeText(this, "Hasat kaydedildi", Toast.LENGTH_SHORT).show();
            syncHarvestCloud(savedHarvestId);
            setResult(RESULT_OK);
            finish();
        } else {
            Toast.makeText(this, "Hasat kaydedilemedi", Toast.LENGTH_LONG).show();
        }
    }

    private void syncHarvestCloud(long harvestId) {
        if (harvestId <= 0) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        new ProductionCloudRepository(databaseHelper).syncHarvestByLocalId(user.getUid(), harvestId);
    }
}
