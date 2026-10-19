package com.zeytindefteri.app;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Gerçek satış ve fiyat teklifi kayıt formu. Boy satırları hasat formuyla ortaktır (include_grade_rows);
 * sayı okuma ve ödeme kuralı GradeFormModel'dedir. Kayıt SaleRepository.addSaleWithGrades ile tek işlemdir.
 * Anonim piyasa izni her açılışta kapalı başlar ve yalnızca gerçek satışta gösterilir.
 */
public class SaleActivity extends Activity {

    private static final String SALE = "SALE";
    private static final String OFFER = "OFFER";
    private static final int COLOR_TEXT = GreenTheme.color(R.color.zd_text);
    private static final int COLOR_MUTED = GreenTheme.color(R.color.zd_muted);
    private static final int COLOR_SURFACE = GreenTheme.color(R.color.zd_surface);

    private final Locale trLocale = new Locale("tr", "TR");
    private final Calendar saleCalendar = Calendar.getInstance();
    private final Calendar dueCalendar = Calendar.getInstance();
    private final List<GardenOption> gardens = new ArrayList<>();

    private DatabaseHelper databaseHelper;
    private SaleRepository saleRepository;

    private Spinner spinnerGarden;
    private EditText etSaleDate, etRegion, etVariety, etBuyerName, etBuyerPhone;
    private EditText etTransportCost, etDeductions, etAmountReceived, etDueDate, etNote;
    private TextView tvSaleKindInfo, tvTotalKg, tvGrossTotal, tvDeductionTotal, tvNetLabel, tvNetTotal, tvRemaining;
    private TextView tvSaveSummary, btnSaveSale, btnKindSale, btnKindOffer, chipPayFull, chipPayPartial, chipPayNone;
    private View cardBuyer, cardPayment, cardMarketConsent, boxAmountReceived, boxDueDate;
    private CompoundButton checkMarketConsent;

    private final EditText[] kgFields = new EditText[GradeFormModel.CODES.length];
    private final EditText[] priceFields = new EditText[GradeFormModel.CODES.length];
    private final TextView[] valueViews = new TextView[GradeFormModel.CODES.length];

    private boolean realSale = true;
    private GradeFormModel.Payment payment = GradeFormModel.Payment.NONE;
    private boolean saving;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().hide();
        getWindow().setStatusBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().setNavigationBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().getDecorView().setSystemUiVisibility(0);
        setContentView(R.layout.activity_sale);

        databaseHelper = new DatabaseHelper(this);
        saleRepository = new SaleRepository(databaseHelper);
        saleCalendar.set(Calendar.YEAR, databaseHelper.getActiveSeasonYear());
        dueCalendar.setTime(saleCalendar.getTime());
        if (savedInstanceState != null) {
            realSale = savedInstanceState.getBoolean("sale_real", true);
            payment = GradeFormModel.Payment.valueOf(savedInstanceState.getString("sale_payment", payment.name()));
            saleCalendar.setTimeInMillis(savedInstanceState.getLong("sale_date", saleCalendar.getTimeInMillis()));
            dueCalendar.setTimeInMillis(savedInstanceState.getLong("sale_due", dueCalendar.getTimeInMillis()));
        }

        bindViews();
        setupChoices();
        setupDates();
        setupCalculations();
        loadGardens(getIntent() == null ? -1L : getIntent().getLongExtra("garden_id", -1L));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnSaveSale.setOnClickListener(v -> saveSale());
        updateSaleKindUi();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("sale_real", realSale);
        state.putString("sale_payment", payment.name());
        state.putLong("sale_date", saleCalendar.getTimeInMillis());
        state.putLong("sale_due", dueCalendar.getTimeInMillis());
    }

    private void bindViews() {
        spinnerGarden = findViewById(R.id.spinnerGarden);
        etSaleDate = findViewById(R.id.etSaleDate);
        etRegion = findViewById(R.id.etRegion);
        etVariety = findViewById(R.id.etVariety);
        etBuyerName = findViewById(R.id.etBuyerName);
        etBuyerPhone = findViewById(R.id.etBuyerPhone);
        // Alıcı adı rehberden seçilir (yeni alıcı da seçim penceresinden eklenir); telefon boşsa rehberden dolar.
        etBuyerName.setFocusable(false);
        etBuyerName.setLongClickable(false);
        etBuyerName.setOnClickListener(v -> BuyerPicker.show(this, databaseHelper, text(etBuyerName), "Alıcı belirtme", name -> {
            etBuyerName.setText(name);
            etBuyerName.setError(null);
            if (!name.isEmpty() && text(etBuyerPhone).isEmpty()) {
                try {
                    android.database.sqlite.SQLiteDatabase bdb = databaseHelper.getReadableDatabase();
                    for (BuyerDirectory.Summary s : BuyerDirectory.list(bdb, databaseHelper.getActiveOwnerUid(), databaseHelper.getActiveSeasonYear())) {
                        if (BuyerDirectory.normalize(s.name).equals(BuyerDirectory.normalize(name))) { etBuyerPhone.setText(s.phone); break; }
                    }
                } catch (RuntimeException ignored) { }
            }
        }));
        etTransportCost = findViewById(R.id.etTransportCost);
        etDeductions = findViewById(R.id.etDeductions);
        etAmountReceived = findViewById(R.id.etAmountReceived);
        etDueDate = findViewById(R.id.etDueDate);
        etNote = findViewById(R.id.etNote);
        tvSaleKindInfo = findViewById(R.id.tvSaleKindInfo);
        tvTotalKg = findViewById(R.id.tvTotalKg);
        tvGrossTotal = findViewById(R.id.tvGrossTotal);
        tvDeductionTotal = findViewById(R.id.tvDeductionTotal);
        tvNetLabel = findViewById(R.id.tvNetLabel);
        tvNetTotal = findViewById(R.id.tvNetTotal);
        tvRemaining = findViewById(R.id.tvRemaining);
        tvSaveSummary = findViewById(R.id.tvSaveSummary);
        btnSaveSale = findViewById(R.id.btnSaveSale);
        btnKindSale = findViewById(R.id.btnKindSale);
        btnKindOffer = findViewById(R.id.btnKindOffer);
        chipPayFull = findViewById(R.id.chipPayFull);
        chipPayPartial = findViewById(R.id.chipPayPartial);
        chipPayNone = findViewById(R.id.chipPayNone);
        cardBuyer = findViewById(R.id.cardBuyer);
        cardPayment = findViewById(R.id.cardPayment);
        cardMarketConsent = findViewById(R.id.cardMarketConsent);
        boxAmountReceived = findViewById(R.id.boxAmountReceived);
        boxDueDate = findViewById(R.id.boxDueDate);
        checkMarketConsent = findViewById(R.id.checkMarketConsent);

        int[] kgIds = {R.id.etKgDouble, R.id.etKg1, R.id.etKg2, R.id.etKg3, R.id.etKg4, R.id.etKgOil};
        int[] priceIds = {R.id.etPriceDouble, R.id.etPrice1, R.id.etPrice2, R.id.etPrice3, R.id.etPrice4, R.id.etPriceOil};
        int[] valueIds = {R.id.tvValueDouble, R.id.tvValue1, R.id.tvValue2, R.id.tvValue3, R.id.tvValue4, R.id.tvValueOil};
        for (int i = 0; i < GradeFormModel.CODES.length; i++) {
            kgFields[i] = findViewById(kgIds[i]);
            priceFields[i] = findViewById(priceIds[i]);
            valueViews[i] = findViewById(valueIds[i]);
        }
    }

    private void setupChoices() {
        btnKindSale.setOnClickListener(v -> { realSale = true; updateSaleKindUi(); });
        btnKindOffer.setOnClickListener(v -> { realSale = false; updateSaleKindUi(); });
        chipPayFull.setOnClickListener(v -> { payment = GradeFormModel.Payment.FULL; recalculate(); });
        chipPayPartial.setOnClickListener(v -> {
            payment = GradeFormModel.Payment.PARTIAL;
            recalculate();
            etAmountReceived.requestFocus();
        });
        chipPayNone.setOnClickListener(v -> { payment = GradeFormModel.Payment.NONE; recalculate(); });
    }

    private void setupDates() {
        etSaleDate.setText(formatDate(saleCalendar.getTime()));
        etSaleDate.setOnClickListener(v -> showDatePicker(etSaleDate, saleCalendar, false));
        etDueDate.setOnClickListener(v -> showDatePicker(etDueDate, dueCalendar, true));
    }

    private void showDatePicker(EditText target, Calendar calendar, boolean allowClear) {
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            calendar.set(year, month, day);
            target.setText(formatDate(calendar.getTime()));
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        if (allowClear) {
            dialog.setButton(DialogInterface.BUTTON_NEUTRAL, "Temizle", (d, which) -> target.setText(""));
        } else {
            Calendar firstDay = Calendar.getInstance();
            firstDay.clear();
            firstDay.set(databaseHelper.getActiveSeasonYear(), Calendar.JANUARY, 1);
            Calendar lastDay = Calendar.getInstance();
            lastDay.clear();
            lastDay.set(databaseHelper.getActiveSeasonYear(), Calendar.DECEMBER, 31, 23, 59, 59);
            dialog.getDatePicker().setMinDate(firstDay.getTimeInMillis());
            dialog.getDatePicker().setMaxDate(lastDay.getTimeInMillis());
        }
        dialog.show();
    }

    private void setupCalculations() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { recalculate(); }
            @Override public void afterTextChanged(Editable s) { }
        };
        for (EditText field : kgFields) field.addTextChangedListener(watcher);
        for (EditText field : priceFields) field.addTextChangedListener(watcher);
        etTransportCost.addTextChangedListener(watcher);
        etDeductions.addTextChangedListener(watcher);
        etAmountReceived.addTextChangedListener(watcher);
    }

    private void loadGardens(long requestedGardenId) {
        gardens.clear();
        Cursor cursor = null;
        int requestedPosition = -1;
        try {
            cursor = databaseHelper.getGardens();
            while (cursor != null && cursor.moveToNext()) {
                GardenOption option = new GardenOption(valueLong(cursor, "id"), value(cursor, "name"),
                        value(cursor, "province"), value(cursor, "district"), value(cursor, "variety"));
                gardens.add(option);
                if (option.id == requestedGardenId) requestedPosition = gardens.size() - 1;
            }
        } finally {
            if (cursor != null) cursor.close();
        }

        List<String> names = new ArrayList<>();
        if (gardens.isEmpty()) names.add("Önce bir bahçe ekleyin");
        for (GardenOption option : gardens) names.add(option.name);
        spinnerGarden.setAdapter(darkAdapter(names));
        spinnerGarden.setEnabled(!gardens.isEmpty());
        btnSaveSale.setEnabled(!gardens.isEmpty());
        btnSaveSale.setAlpha(gardens.isEmpty() ? 0.55f : 1f);
        if (requestedPosition >= 0) spinnerGarden.setSelection(requestedPosition);
        spinnerGarden.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= gardens.size()) return;
                fillGardenDefaults(gardens.get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        if (!gardens.isEmpty()) fillGardenDefaults(gardens.get(Math.max(0, requestedPosition)));
    }

    /** Bölge ve çeşit boşsa bahçeden önerilir; kullanıcının yazdığı değer ezilmez. */
    private void fillGardenDefaults(GardenOption option) {
        if (text(etRegion).isEmpty()) etRegion.setText(option.region());
        if (text(etVariety).isEmpty()) etVariety.setText(option.variety);
    }

    private ArrayAdapter<String> darkAdapter(List<String> items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            private View style(View view, boolean dropdown) {
                TextView textView = (TextView) view;
                textView.setTextColor(COLOR_TEXT);
                textView.setTextSize(15);
                textView.setPadding(dp(dropdown ? 14 : 0), dp(dropdown ? 14 : 8), dp(14), dp(dropdown ? 14 : 8));
                if (dropdown) textView.setBackgroundColor(COLOR_SURFACE);
                return textView;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                return style(super.getView(position, convertView, parent), false);
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return style(super.getDropDownView(position, convertView, parent), true);
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void updateSaleKindUi() {
        btnKindSale.setSelected(realSale);
        btnKindOffer.setSelected(!realSale);
        btnKindSale.setContentDescription("Gerçek satış" + (realSale ? ", seçili" : ""));
        btnKindOffer.setContentDescription("Fiyat teklifi" + (realSale ? "" : ", seçili"));
        int visibility = realSale ? View.VISIBLE : View.GONE;
        cardBuyer.setVisibility(visibility);
        cardPayment.setVisibility(visibility);
        cardMarketConsent.setVisibility(visibility);
        if (!realSale) checkMarketConsent.setChecked(false);
        tvSaleKindInfo.setText(realSale
                ? "Gerçek satış seçili sezondaki gelir ve tahsilat hesabına eklenir. İzin verirsen fiyatlar anonim piyasa verisine katılır."
                : "Fiyat teklifi gelir ve alacak hesabına eklenmez; yalnızca karşılaştırma kaydı olarak saklanır.");
        ((TextView) findViewById(R.id.tvScreenTitle)).setText(realSale ? "Satış kaydı" : "Fiyat teklifi");
        btnSaveSale.setText(realSale ? "Satışı Kaydet" : "Teklifi Kaydet");
        recalculate();
    }

    private void recalculate() {
        double totalKg = 0, gross = 0;
        for (int i = 0; i < GradeFormModel.CODES.length; i++) {
            double kg = number(kgFields[i]);
            double price = number(priceFields[i]);
            totalKg += kg;
            gross += kg * price;
            valueViews[i].setText(GradeFormModel.lineValue(kg, price));
            valueViews[i].setTextColor(kg > 0 && price > 0 ? COLOR_TEXT : COLOR_MUTED);
        }
        double cuts = number(etTransportCost) + number(etDeductions);
        double net = Math.max(0, gross - cuts);
        double received = GradeFormModel.received(payment, net, number(etAmountReceived), realSale);
        double remaining = Math.max(0, net - received);

        tvTotalKg.setText(GradeFormModel.kg(totalKg));
        tvGrossTotal.setText(GradeFormModel.money(gross));
        tvDeductionTotal.setText(cuts > 0 ? "− " + GradeFormModel.money(cuts) : GradeFormModel.money(0));
        tvNetLabel.setText(realSale ? "Net satış" : "Teklif toplamı");
        tvNetTotal.setText(GradeFormModel.money(net));
        tvRemaining.setText(GradeFormModel.money(remaining));

        chipPayFull.setSelected(payment == GradeFormModel.Payment.FULL);
        chipPayPartial.setSelected(payment == GradeFormModel.Payment.PARTIAL);
        chipPayNone.setSelected(payment == GradeFormModel.Payment.NONE);
        boxAmountReceived.setVisibility(payment == GradeFormModel.Payment.PARTIAL ? View.VISIBLE : View.GONE);
        boxDueDate.setVisibility(payment == GradeFormModel.Payment.FULL ? View.INVISIBLE : View.VISIBLE);

        tvSaveSummary.setText(realSale
                ? "Net " + GradeFormModel.money(net) + " · kalan alacak " + GradeFormModel.money(remaining)
                : "Teklif toplamı " + GradeFormModel.money(net) + " · gelire eklenmez");
    }

    private void saveSale() {
        if (saving) return;
        if (gardens.isEmpty()) {
            Toast.makeText(this, "Satış için önce bir bahçe ekleyin.", Toast.LENGTH_LONG).show();
            return;
        }
        int gardenPosition = spinnerGarden.getSelectedItemPosition();
        if (gardenPosition < 0 || gardenPosition >= gardens.size()) {
            Toast.makeText(this, "Bahçe seçin.", Toast.LENGTH_SHORT).show();
            return;
        }
        String saleDate = text(etSaleDate);
        if (!isValidDate(saleDate)) { etSaleDate.setError("Geçerli tarih seçin"); return; }
        if (yearOf(saleDate) != databaseHelper.getActiveSeasonYear()) { etSaleDate.setError("Tarih aktif sezonda olmalı"); return; }
        String region = text(etRegion);
        if (region.isEmpty()) {
            etRegion.setError("Piyasa bölgesi gerekli");
            etRegion.requestFocus();
            return;
        }

        double[] kilograms = new double[GradeFormModel.CODES.length];
        double[] prices = new double[GradeFormModel.CODES.length];
        double totalKg = 0, gross = 0;
        for (int i = 0; i < GradeFormModel.CODES.length; i++) {
            kilograms[i] = number(kgFields[i]);
            prices[i] = number(priceFields[i]);
            if (kilograms[i] > 0 && prices[i] <= 0) {
                priceFields[i].setError("Fiyat gerekli");
                priceFields[i].requestFocus();
                return;
            }
            if (prices[i] > 0 && kilograms[i] <= 0) {
                kgFields[i].setError("Miktar gerekli");
                kgFields[i].requestFocus();
                return;
            }
            totalKg += kilograms[i];
            gross += kilograms[i] * prices[i];
        }
        if (totalKg <= 0) {
            kgFields[0].setError("En az bir ürün miktarı girin");
            kgFields[0].requestFocus();
            return;
        }

        double transport = number(etTransportCost);
        double deductions = number(etDeductions);
        double net = Math.max(0, gross - transport - deductions);
        if (realSale && payment == GradeFormModel.Payment.PARTIAL && number(etAmountReceived) <= 0) {
            etAmountReceived.setError("Alınan tutarı girin");
            etAmountReceived.requestFocus();
            return;
        }
        double received = GradeFormModel.received(payment, net, number(etAmountReceived), realSale);
        if (received > net + 0.01d) {
            etAmountReceived.setError("Tahsilat net satıştan büyük olamaz");
            etAmountReceived.requestFocus();
            return;
        }
        if (!text(etBuyerPhone).isEmpty() && text(etBuyerName).isEmpty()) {
            etBuyerName.setError("Telefon için alıcı adı girin");
            etBuyerName.requestFocus();
            return;
        }
        // Tamamı alındıysa vade anlamsızdır; kaydedilmez.
        String dueDate = realSale && payment != GradeFormModel.Payment.FULL ? text(etDueDate) : "";
        if (!dueDate.isEmpty() && !isValidDate(dueDate)) { etDueDate.setError("Geçerli vade tarihi seçin"); return; }
        Date saleDay = parseDate(saleDate);
        Date dueDay = parseDate(dueDate);
        if (saleDay != null && dueDay != null && dueDay.before(saleDay)) { etDueDate.setError("Vade satış tarihinden önce olamaz"); return; }

        saving = true;
        btnSaveSale.setEnabled(false);
        btnSaveSale.setText("Kaydediliyor…");

        Long buyerId = null;
        boolean buyerExisted = false;
        if (realSale && !text(etBuyerName).isEmpty()) {
            // Alıcı rehberinde aynı ad varsa o kullanılır; yeni kopya açılmaz.
            android.database.sqlite.SQLiteDatabase bdb = databaseHelper.getWritableDatabase();
            String bOwner = databaseHelper.getActiveOwnerUid();
            buyerExisted = BuyerDirectory.find(bdb, bOwner, text(etBuyerName)) > 0;
            long createdBuyer = BuyerDirectory.findOrCreate(bdb, bOwner, text(etBuyerName), text(etBuyerPhone), region,
                    "Satış formundan oluşturuldu");
            if (createdBuyer <= 0) { failSave("Alıcı kaydı oluşturulamadı."); return; }
            buyerId = createdBuyer;
        }

        long saleId = saleRepository.addSaleWithGrades(
                (int) gardens.get(gardenPosition).id, buyerId, saleDate, realSale ? SALE : OFFER, region,
                text(etVariety), "SOFRALIK", transport, deductions, received, dueDate, text(etNote),
                realSale && checkMarketConsent.isChecked(), GradeFormModel.CODES, kilograms, prices);
        if (saleId <= 0) {
            // Rehberde önceden olan alıcı silinmez; yalnız bu kayıt için yeni açılan ve kullanılmayan silinir.
            if (buyerId != null && !buyerExisted) databaseHelper.deleteBuyerIfUnused(buyerId);
            failSave("Kayıt tamamlanamadı. Bilgileri kontrol edip tekrar deneyin.");
            return;
        }

        syncSavedSale(saleId);
        Toast.makeText(this, (realSale ? "Satış" : "Fiyat teklifi") + " kaydedildi", Toast.LENGTH_SHORT).show();
        Intent result = new Intent();
        result.putExtra("sale_id", saleId);
        result.putExtra("season_year", yearOf(saleDate));
        setResult(RESULT_OK, result);
        finish();
    }

    private void syncSavedSale(long saleId) {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null || user.getUid() == null || user.getUid().trim().isEmpty()) return;
            new ProductionCloudRepository(databaseHelper).syncSaleByLocalId(user.getUid().trim(), saleId);
        } catch (Exception ignored) {
            // Offline-first: yerel kayıt tamamlandı; sonraki genel senkron yeniden dener.
        }
    }

    private void failSave(String message) {
        saving = false;
        btnSaveSale.setEnabled(true);
        btnSaveSale.setText(realSale ? "Satışı Kaydet" : "Teklifi Kaydet");
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private boolean isValidDate(String value) {
        return parseDate(value) != null;
    }

    private Date parseDate(String value) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy", trLocale);
            format.setLenient(false);
            return format.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int yearOf(String value) {
        if (value != null && value.length() >= 4) {
            try {
                return Integer.parseInt(value.substring(value.length() - 4));
            } catch (Exception ignored) { }
        }
        return Calendar.getInstance().get(Calendar.YEAR);
    }

    private String formatDate(Date date) {
        return new SimpleDateFormat("dd.MM.yyyy", trLocale).format(date);
    }

    /** Türkçe sayı girişi: "2.400" → 2400, "8,5" → 8.5; negatif veya okunamayan → 0. */
    private double number(EditText field) {
        return field == null ? 0 : GradeFormModel.parse(text(field));
    }

    private String text(EditText field) {
        return field == null || field.getText() == null ? "" : field.getText().toString().trim();
    }

    private String value(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) || cursor.getString(index) == null ? "" : cursor.getString(index).trim();
    }

    private long valueLong(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? -1L : cursor.getLong(index);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (databaseHelper != null) databaseHelper.close();
    }

    private static final class GardenOption {
        final long id;
        final String name, province, district, variety;

        GardenOption(long id, String name, String province, String district, String variety) {
            this.id = id;
            this.name = name == null || name.isEmpty() ? "Bahçe" : name;
            this.province = province == null ? "" : province;
            this.district = district == null ? "" : district;
            this.variety = variety == null ? "" : variety;
        }

        String region() {
            return !district.isEmpty() ? district : province;
        }
    }
}
