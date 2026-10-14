package com.zeytindefteri.app;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * İşlem ekle. Tür, ikonlu kutulardan seçilir (OperationKinds.TYPES); miktar etiketi, birimler ve ipucu
 * OperationKinds.form() kuralından gelir. Kayıt: DatabaseHelper.addOperation + bulut eşitleme.
 */
public class NewOperationActivity extends Activity {

    private static final int COLUMNS = 4;

    private DatabaseHelper databaseHelper;
    private OperationCloudRepository operationCloudRepository;

    private Spinner spinnerGarden, spinnerUnit;
    private EditText etDate, etQuantity, etCost, etNote;
    private TextView tvQuantityLabel, tvOperationHint, tvDetailTitle, tvSaveSummary, btnSaveOperation;
    private View viewHintDot;
    private LinearLayout gridOperationTypes;
    private final List<View> typeTiles = new ArrayList<>();

    private final ArrayList<Integer> gardenIds = new ArrayList<>();
    private final ArrayList<String> gardenNames = new ArrayList<>();

    private final Calendar selectedDate = Calendar.getInstance();
    private final Locale TR = new Locale("tr", "TR");
    private int selectedType = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().hide();
        getWindow().setStatusBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().setNavigationBarColor(GreenTheme.color(R.color.zd_bg));
        setContentView(R.layout.activity_new_operation);

        databaseHelper = new DatabaseHelper(this);
        operationCloudRepository = new OperationCloudRepository(databaseHelper);

        findViews();
        loadGardens();
        buildTypeGrid();
        setupListeners();

        if (savedInstanceState != null) {
            selectedType = savedInstanceState.getInt("operation_type_index", 0);
            selectedDate.setTimeInMillis(savedInstanceState.getLong("operation_date", selectedDate.getTimeInMillis()));
        } else {
            applyIncomingSelection();
        }
        updateDateText();
        selectType(selectedType, savedInstanceState != null ? savedInstanceState.getInt("operation_unit_index", 0) : 0);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("operation_type_index", selectedType);
        state.putLong("operation_date", selectedDate.getTimeInMillis());
        state.putInt("operation_unit_index", Math.max(0, spinnerUnit.getSelectedItemPosition()));
    }

    private void findViews() {
        spinnerGarden = findViewById(R.id.spinnerGarden);
        spinnerUnit = findViewById(R.id.spinnerUnit);
        etDate = findViewById(R.id.etDate);
        etQuantity = findViewById(R.id.etQuantity);
        etCost = findViewById(R.id.etCost);
        etNote = findViewById(R.id.etNote);
        tvQuantityLabel = findViewById(R.id.tvQuantityLabel);
        tvOperationHint = findViewById(R.id.tvOperationHint);
        tvDetailTitle = findViewById(R.id.tvDetailTitle);
        tvSaveSummary = findViewById(R.id.tvSaveSummary);
        viewHintDot = findViewById(R.id.viewHintDot);
        gridOperationTypes = findViewById(R.id.gridOperationTypes);
        btnSaveOperation = findViewById(R.id.btnSaveOperation);
    }

    private void loadGardens() {
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
        spinnerGarden.setAdapter(adapter(gardenNames));
        if (gardenIds.isEmpty()) {
            btnSaveOperation.setEnabled(false);
            btnSaveOperation.setAlpha(0.45f);
            Toast.makeText(this, "Önce bir bahçe eklemelisin", Toast.LENGTH_LONG).show();
        }
    }

    /** Bahçe detayından (garden_id) veya ana sayfa hızlı işleminden (operation_type) gelen seçim. */
    private void applyIncomingSelection() {
        long requestedGardenId = getIntent().getLongExtra("garden_id", -1);
        if (requestedGardenId != -1) {
            for (int i = 0; i < gardenIds.size(); i++) {
                if (gardenIds.get(i) == requestedGardenId) { spinnerGarden.setSelection(i); break; }
            }
        }
        String requestedType = getIntent().getStringExtra("operation_type");
        if (requestedType != null && !requestedType.trim().isEmpty()) selectedType = OperationKinds.indexOf(requestedType);
    }

    // ------------------------------------------------------------ tür kutuları

    private void buildTypeGrid() {
        gridOperationTypes.removeAllViews();
        typeTiles.clear();
        LinearLayout row = null;
        for (int i = 0; i < OperationKinds.TYPES.length; i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setBaselineAligned(false);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                if (i > 0) rp.topMargin = dp(8);
                gridOperationTypes.addView(row, rp);
            }
            View tile = typeTile(i);
            typeTiles.add(tile);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
            if (i % COLUMNS != 0) p.leftMargin = dp(8);
            row.addView(tile, p);
        }
        // Son satırı boş hücrelerle doldur; kutular aynı genişlikte kalsın.
        for (int i = OperationKinds.TYPES.length; i % COLUMNS != 0; i++) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 1, 1);
            p.leftMargin = dp(8);
            row.addView(new View(this), p);
        }
    }

    private View typeTile(final int index) {
        String type = OperationKinds.TYPES[index];
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setMinimumHeight(dp(74));
        tile.setPadding(dp(2), dp(10), dp(2), dp(10));
        tile.setBackgroundResource(R.drawable.bg_choice_tile);
        ImageView icon = new ImageView(this);
        icon.setImageResource(LineIcons.forType(type));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView name = new TextView(this);
        name.setText(type);
        name.setTextSize(12);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setTextColor(GreenTheme.color(R.color.zd_text));
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setPadding(0, dp(6), 0, 0);
        tile.addView(name);
        tile.setContentDescription(type);
        tile.setOnClickListener(v -> selectType(index, 0));
        return tile;
    }

    private void selectType(int index, int unitIndex) {
        selectedType = Math.max(0, Math.min(OperationKinds.TYPES.length - 1, index));
        String type = OperationKinds.TYPES[selectedType];
        int green = GreenTheme.color(R.color.zd_green);
        for (int i = 0; i < typeTiles.size(); i++) {
            View tile = typeTiles.get(i);
            boolean on = i == selectedType;
            tile.setSelected(on);
            String t = OperationKinds.TYPES[i];
            ((ImageView) ((LinearLayout) tile).getChildAt(0)).setColorFilter(on ? green : LineIcons.toneColor(t));
            tile.setContentDescription(t + (on ? ", seçili" : ""));
        }
        OperationKinds.Form form = OperationKinds.form(type);
        tvDetailTitle.setText("AYRINTI · " + type.toUpperCase(TR));
        tvQuantityLabel.setText(form.quantityLabel);
        etQuantity.setHint(form.quantityHint);
        List<String> units = new ArrayList<>();
        for (String u : form.units) units.add(u);
        spinnerUnit.setAdapter(adapter(units));
        if (unitIndex > 0 && unitIndex < units.size()) spinnerUnit.setSelection(unitIndex);
        tvOperationHint.setText(form.tip);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(LineIcons.toneColor(type));
        viewHintDot.setBackground(dot);
        updateSaveSummary();
    }

    // ------------------------------------------------------------ tarih / dinleyiciler

    private void showDatePicker() {
        new DatePickerDialog(this, (view, year, month, day) -> {
            selectedDate.set(Calendar.YEAR, year);
            selectedDate.set(Calendar.MONTH, month);
            selectedDate.set(Calendar.DAY_OF_MONTH, day);
            updateDateText();
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateText() {
        etDate.setText(new SimpleDateFormat("dd.MM.yyyy", TR).format(selectedDate.getTime()));
        updateSaveSummary();
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnSaveOperation.setOnClickListener(v -> saveOperation());
        etDate.setOnClickListener(v -> showDatePicker());
        spinnerGarden.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { updateSaveSummary(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    /** Kaydet düğmesinin üstündeki tek satır: "Bahçe · Tür · Tarih". */
    private void updateSaveSummary() {
        if (tvSaveSummary == null || etDate == null) return;
        Object garden = spinnerGarden.getSelectedItem();
        StringBuilder b = new StringBuilder();
        if (garden != null) b.append(garden).append(" · ");
        b.append(OperationKinds.TYPES[selectedType]).append(" · ").append(etDate.getText());
        tvSaveSummary.setText(b);
    }

    // ------------------------------------------------------------ kaydet

    private void saveOperation() {
        if (gardenIds.isEmpty()) {
            Toast.makeText(this, "Önce bir bahçe eklemelisin", Toast.LENGTH_SHORT).show();
            return;
        }
        int gardenPosition = spinnerGarden.getSelectedItemPosition();
        if (gardenPosition < 0 || gardenPosition >= gardenIds.size()) {
            Toast.makeText(this, "Bahçe seçilemedi", Toast.LENGTH_SHORT).show();
            return;
        }
        int gardenId = gardenIds.get(gardenPosition);
        String type = OperationKinds.TYPES[selectedType];
        String date = etDate.getText().toString().trim();
        double quantity = readDouble(etQuantity);
        String unit = spinnerUnit.getSelectedItem() == null ? "" : spinnerUnit.getSelectedItem().toString();
        double cost = readDouble(etCost);
        String note = etNote.getText().toString().trim();

        long result = databaseHelper.addOperation(gardenId, type, date, quantity, unit, cost, note);
        if (result != -1) {
            syncSavedOperationToCloud(result);
            Toast.makeText(this, "İşlem kaydedildi", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } else {
            Toast.makeText(this, "İşlem kaydedilemedi", Toast.LENGTH_SHORT).show();
        }
    }

    private void syncSavedOperationToCloud(long operationId) {
        if (operationCloudRepository == null || operationId <= 0) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String uid = user.getUid();
        if (uid == null || uid.trim().isEmpty()) return;
        operationCloudRepository.syncOperationByLocalId(uid, operationId);
    }

    /** Türkçe virgül desteği: "2,5" → 2.5. Boş veya okunamayan değer 0 sayılır. */
    private double readDouble(EditText editText) {
        String value = editText.getText().toString().trim();
        if (value.isEmpty()) return 0;
        try {
            return Double.parseDouble(value.replace(",", "."));
        } catch (Exception e) {
            return 0;
        }
    }

    private ArrayAdapter<String> adapter(List<String> items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
