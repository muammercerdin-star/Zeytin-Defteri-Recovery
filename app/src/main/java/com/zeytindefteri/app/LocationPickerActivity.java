package com.zeytindefteri.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class LocationPickerActivity extends Activity {


    // ============================================================
    // SONUÇ EXTRA'LARI
    // ============================================================

    public static final String EXTRA_PROVINCE_ID =
            "province_id";

    public static final String EXTRA_PROVINCE_NAME =
            "province_name";

    public static final String EXTRA_DISTRICT_ID =
            "district_id";

    public static final String EXTRA_DISTRICT_NAME =
            "district_name";

    public static final String EXTRA_SETTLEMENT_ID =
            "settlement_id";

    public static final String EXTRA_SETTLEMENT_NAME =
            "settlement_name";

    public static final String EXTRA_SETTLEMENT_TYPE =
            "settlement_type";


    // ============================================================
    // GEÇİCİ UYUMLULUK
    //
    // AddGardenActivity henüz eski alanları kullanıyor olabilir.
    // Bir sonraki adımda onları da tamamen kaldıracağız.
    // ============================================================

    public static final String EXTRA_STREET_ID =
            "street_id";

    public static final String EXTRA_STREET_NAME =
            "street_name";


    // ============================================================
    // BAŞLANGIÇ MODU
    // ============================================================

    public static final String EXTRA_START_MODE =
            "start_mode";

    public static final int START_PROVINCE =
            1;

    public static final int START_DISTRICT =
            2;

    public static final int START_SETTLEMENT =
            3;

    // Eski AddGardenActivity Build vermesin diye geçici duruyor.
    public static final int START_STREET =
            4;


    // ============================================================
    // İÇ MODLAR
    // ============================================================

    private static final int MODE_PROVINCE =
            1;

    private static final int MODE_DISTRICT =
            2;

    private static final int MODE_SETTLEMENT =
            3;


    // ============================================================
    // VIEW'LAR
    // ============================================================

    private TextView btnLocationBack;

    private TextView btnLocationClose;

    private TextView tvLocationTitle;


    private TextView tvProvinceCrumb;

    private TextView tvProvinceCountBadge;

    private TextView tvProvinceArrow;


    private TextView tvDistrictCrumb;

    private TextView tvDistrictCountBadge;

    private TextView tvDistrictArrow;


    // XML'de şimdilik duruyorlar.
    // Görünmez yapacağız.
    private TextView tvSettlementCrumb;

    private TextView tvSettlementCountBadge;

    private TextView tvSettlementArrow;

    private TextView tvStreetCrumb;


    private EditText etLocationSearch;

    private TextView tvLocationInfo;

    private TextView tvLocationEmpty;

    private ProgressBar progressLocation;

    private ListView listLocations;


    // ============================================================
    // REPOSITORY
    // ============================================================

    private TurkeyLocationRepository locationRepository;


    // ============================================================
    // LİSTELER
    // ============================================================

    private final List<
            TurkeyLocationRepository.LocationItem
            > allItems =
            new ArrayList<>();


    private final List<
            TurkeyLocationRepository.LocationItem
            > filteredItems =
            new ArrayList<>();


    private LocationListAdapter listAdapter;


    // ============================================================
    // MEVCUT MOD
    // ============================================================

    private int currentMode =
            MODE_PROVINCE;


    // ============================================================
    // İL
    // ============================================================

    private int selectedProvinceId =
            0;

    private String selectedProvinceName =
            "";

    private int selectedProvinceDistrictCount =
            0;


    // ============================================================
    // İLÇE
    // ============================================================

    private int selectedDistrictId =
            0;

    private String selectedDistrictName =
            "";

    private int selectedDistrictNeighborhoodCount =
            0;


    // ============================================================
    // MAHALLE / KÖY
    // ============================================================

    private int selectedSettlementId =
            0;

    private String selectedSettlementName =
            "";

    private String selectedSettlementType =
            "";


    // ============================================================
    // TÜRKÇE
    // ============================================================

    private final Locale turkishLocale =
            new Locale(
                    "tr",
                    "TR"
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

            getActionBar().hide();
        }


        getWindow().setStatusBarColor(
                GreenTheme.parse("#0E5A3A")
        );


        setContentView(
                R.layout.activity_location_picker
        );


        locationRepository =
                new TurkeyLocationRepository();


        findViews();

        setupList();

        setupSearch();

        setupButtons();

        setupBreadcrumbClicks();


        readInitialLocation();

        showRequestedStartMode();
    }


    // ============================================================
    // VIEW'LARI BUL
    // ============================================================

    private void findViews() {


        btnLocationBack =
                findViewById(
                        R.id.btnLocationBack
                );


        btnLocationClose =
                findViewById(
                        R.id.btnLocationClose
                );


        tvLocationTitle =
                findViewById(
                        R.id.tvLocationTitle
                );


        tvProvinceCrumb =
                findViewById(
                        R.id.tvProvinceCrumb
                );


        tvProvinceCountBadge =
                findViewById(
                        R.id.tvProvinceCountBadge
                );


        tvProvinceArrow =
                findViewById(
                        R.id.tvProvinceArrow
                );


        tvDistrictCrumb =
                findViewById(
                        R.id.tvDistrictCrumb
                );


        tvDistrictCountBadge =
                findViewById(
                        R.id.tvDistrictCountBadge
                );


        tvDistrictArrow =
                findViewById(
                        R.id.tvDistrictArrow
                );


        tvSettlementCrumb =
                findViewById(
                        R.id.tvSettlementCrumb
                );


        tvSettlementCountBadge =
                findViewById(
                        R.id.tvSettlementCountBadge
                );


        tvSettlementArrow =
                findViewById(
                        R.id.tvSettlementArrow
                );


        tvStreetCrumb =
                findViewById(
                        R.id.tvStreetCrumb
                );


        etLocationSearch =
                findViewById(
                        R.id.etLocationSearch
                );


        tvLocationInfo =
                findViewById(
                        R.id.tvLocationInfo
                );


        tvLocationEmpty =
                findViewById(
                        R.id.tvLocationEmpty
                );


        progressLocation =
                findViewById(
                        R.id.progressLocation
                );


        listLocations =
                findViewById(
                        R.id.listLocations
                );


        // ========================================================
        // ARTIK CADDE / SOKAK YOK
        // ========================================================

        if (tvSettlementCountBadge != null) {

            tvSettlementCountBadge.setVisibility(
                    View.GONE
            );
        }


        if (tvStreetCrumb != null) {

            tvStreetCrumb.setVisibility(
                    View.GONE
            );
        }
    }


    // ============================================================
    // BAŞLANGIÇ KONUMUNU OKU
    // ============================================================

    private void readInitialLocation() {


        Intent intent =
                getIntent();


        if (intent == null) {

            return;
        }


        selectedProvinceId =
                intent.getIntExtra(
                        EXTRA_PROVINCE_ID,
                        0
                );


        selectedProvinceName =
                safeString(
                        intent.getStringExtra(
                                EXTRA_PROVINCE_NAME
                        )
                );


        selectedDistrictId =
                intent.getIntExtra(
                        EXTRA_DISTRICT_ID,
                        0
                );


        selectedDistrictName =
                safeString(
                        intent.getStringExtra(
                                EXTRA_DISTRICT_NAME
                        )
                );


        selectedSettlementId =
                intent.getIntExtra(
                        EXTRA_SETTLEMENT_ID,
                        0
                );


        selectedSettlementName =
                safeString(
                        intent.getStringExtra(
                                EXTRA_SETTLEMENT_NAME
                        )
                );


        selectedSettlementType =
                safeString(
                        intent.getStringExtra(
                                EXTRA_SETTLEMENT_TYPE
                        )
                );
    }


    // ============================================================
    // İSTENEN MODDAN BAŞLA
    // ============================================================

    private void showRequestedStartMode() {


        Intent intent =
                getIntent();


        int startMode =
                START_PROVINCE;


        if (intent != null) {


            startMode =
                    intent.getIntExtra(
                            EXTRA_START_MODE,
                            START_PROVINCE
                    );
        }


        // ========================================================
        // ESKİ STREET İSTEĞİ GELİRSE
        //
        // Artık sokak yok.
        // Mahalle / Köy ekranını aç.
        // ========================================================

        if (startMode == START_STREET) {


            if (selectedProvinceId > 0
                    && selectedDistrictId > 0) {


                showSettlementMode();

                return;
            }
        }


        // ========================================================
        // MAHALLE / KÖY
        // ========================================================

        if (startMode == START_SETTLEMENT
                && selectedProvinceId > 0
                && selectedDistrictId > 0) {


            showSettlementMode();

            return;
        }


        // ========================================================
        // İLÇE
        // ========================================================

        if (startMode == START_DISTRICT
                && selectedProvinceId > 0) {


            showDistrictMode();

            return;
        }


        showProvinceMode();
    }


    // ============================================================
    // LİSTE
    // ============================================================

    private void setupList() {


        listAdapter =
                new LocationListAdapter();


        listLocations.setAdapter(
                listAdapter
        );


        listLocations.setOnItemClickListener(
                (parent, view, position, id) -> {


                    if (position < 0
                            || position >= filteredItems.size()) {


                        return;
                    }


                    TurkeyLocationRepository.LocationItem item =
                            filteredItems.get(
                                    position
                            );


                    if (item == null) {


                        return;
                    }


                    handleLocationClick(
                            item
                    );
                }
        );
    }


    // ============================================================
    // SEÇİM
    // ============================================================

    private void handleLocationClick(
            TurkeyLocationRepository.LocationItem item
    ) {


        // ========================================================
        // İL
        // ========================================================

        if (currentMode == MODE_PROVINCE) {


            selectedProvinceId =
                    item.getId();


            selectedProvinceName =
                    item.getName();


            selectedProvinceDistrictCount =
                    0;


            clearDistrictSelection();

            clearSettlementSelection();


            showDistrictMode();

            return;
        }


        // ========================================================
        // İLÇE
        // ========================================================

        if (currentMode == MODE_DISTRICT) {


            selectedDistrictId =
                    item.getId();


            selectedDistrictName =
                    item.getName();


            selectedDistrictNeighborhoodCount =
                    0;


            clearSettlementSelection();


            showSettlementMode();

            return;
        }


        // ========================================================
        // MAHALLE / KÖY
        //
        // KRİTİK DEĞİŞİKLİK:
        // SEÇİLDİĞİ ANDA BAHÇE KAYDINA DÖN.
        // ========================================================

        if (currentMode == MODE_SETTLEMENT) {


            selectedSettlementId =
                    item.getId();


            selectedSettlementName =
                    item.getName();


            selectedSettlementType =
                    item.getType();


            finishWithResult();
        }
    }


    // ============================================================
    // İL MODU
    // ============================================================

    private void showProvinceMode() {


        currentMode =
                MODE_PROVINCE;


        tvLocationTitle.setText(
                "İl Seç"
        );


        etLocationSearch.setHint(
                "İl ara..."
        );


        tvLocationInfo.setText(
                "Türkiye'deki iller A’dan Z’ye listeleniyor"
        );


        hideAllBreadcrumbs();


        clearSearch();

        loadProvinces();
    }


    // ============================================================
    // İLÇE MODU
    // ============================================================

    private void showDistrictMode() {


        if (selectedProvinceId <= 0) {


            showProvinceMode();

            return;
        }


        currentMode =
                MODE_DISTRICT;


        tvLocationTitle.setText(
                "İlçe Seç"
        );


        etLocationSearch.setHint(
                "İlçe ara..."
        );


        tvLocationInfo.setText(
                selectedProvinceName
                        + " iline bağlı ilçeler"
        );


        // ========================================================
        // İL BREADCRUMB
        // ========================================================

        tvProvinceCrumb.setText(
                selectedProvinceName
        );


        tvProvinceCrumb.setVisibility(
                View.VISIBLE
        );


        tvProvinceArrow.setVisibility(
                View.GONE
        );


        // ========================================================
        // DİĞERLERİ GİZLE
        // ========================================================

        tvDistrictCrumb.setVisibility(
                View.GONE
        );


        tvDistrictArrow.setVisibility(
                View.GONE
        );


        tvSettlementCrumb.setVisibility(
                View.GONE
        );


        tvSettlementArrow.setVisibility(
                View.GONE
        );


        tvStreetCrumb.setVisibility(
                View.GONE
        );


        hideDistrictCountBadge();

        hideSettlementCountBadge();


        updateProvinceCountBadge();


        clearSearch();

        loadDistricts();
    }


    // ============================================================
    // MAHALLE / KÖY MODU
    // ============================================================

    private void showSettlementMode() {


        if (selectedProvinceId <= 0
                || selectedDistrictId <= 0) {


            showDistrictMode();

            return;
        }


        currentMode =
                MODE_SETTLEMENT;


        tvLocationTitle.setText(
                "Mahalle / Köy Seç"
        );


        etLocationSearch.setHint(
                "Mahalle / köy ara..."
        );


        tvLocationInfo.setText(
                selectedDistrictName
                        + " mahalle ve köyleri"
        );


        // ========================================================
        // İL
        // ========================================================

        tvProvinceCrumb.setText(
                selectedProvinceName
        );


        tvProvinceCrumb.setVisibility(
                View.VISIBLE
        );


        tvProvinceArrow.setVisibility(
                View.VISIBLE
        );


        // ========================================================
        // İLÇE
        // ========================================================

        tvDistrictCrumb.setText(
                selectedDistrictName
        );


        tvDistrictCrumb.setVisibility(
                View.VISIBLE
        );


        tvDistrictArrow.setVisibility(
                View.GONE
        );


        // ========================================================
        // MAHALLE VE SOKAK BREADCRUMB ARTIK YOK
        // ========================================================

        tvSettlementCrumb.setVisibility(
                View.GONE
        );


        tvSettlementArrow.setVisibility(
                View.GONE
        );


        tvStreetCrumb.setVisibility(
                View.GONE
        );


        hideSettlementCountBadge();


        updateProvinceCountBadge();

        updateDistrictCountBadge();


        clearSearch();

        loadSettlements();


        ensureProvinceDistrictCount();
    }


    // ============================================================
    // TÜM BREADCRUMB'LARI GİZLE
    // ============================================================

    private void hideAllBreadcrumbs() {


        hideProvinceCountBadge();

        hideDistrictCountBadge();

        hideSettlementCountBadge();


        tvProvinceCrumb.setVisibility(
                View.GONE
        );


        tvProvinceArrow.setVisibility(
                View.GONE
        );


        tvDistrictCrumb.setVisibility(
                View.GONE
        );


        tvDistrictArrow.setVisibility(
                View.GONE
        );


        tvSettlementCrumb.setVisibility(
                View.GONE
        );


        tvSettlementArrow.setVisibility(
                View.GONE
        );


        tvStreetCrumb.setVisibility(
                View.GONE
        );
    }


    // ============================================================
    // İLLER
    // ============================================================

    private void loadProvinces() {


        showLoading();


        locationRepository.loadProvinces(
                new TurkeyLocationRepository.LocationCallback() {


                    @Override
                    public void onSuccess(
                            List<
                                    TurkeyLocationRepository.LocationItem
                                    > items
                    ) {


                        if (currentMode
                                != MODE_PROVINCE) {


                            return;
                        }


                        setItems(
                                items
                        );
                    }


                    @Override
                    public void onError(
                            String message
                    ) {


                        if (currentMode
                                != MODE_PROVINCE) {


                            return;
                        }


                        showError(
                                message
                        );
                    }
                }
        );
    }


    // ============================================================
    // İLÇELER
    // ============================================================

    private void loadDistricts() {


        if (selectedProvinceId <= 0) {


            return;
        }


        showLoading();


        final int requestedProvinceId =
                selectedProvinceId;


        locationRepository.loadDistricts(
                requestedProvinceId,
                new TurkeyLocationRepository.LocationCallback() {


                    @Override
                    public void onSuccess(
                            List<
                                    TurkeyLocationRepository.LocationItem
                                    > items
                    ) {


                        if (currentMode
                                != MODE_DISTRICT
                                || requestedProvinceId
                                != selectedProvinceId) {


                            return;
                        }


                        selectedProvinceDistrictCount =
                                items == null
                                        ? 0
                                        : items.size();


                        updateProvinceCountBadge();


                        setItems(
                                items
                        );
                    }


                    @Override
                    public void onError(
                            String message
                    ) {


                        if (currentMode
                                != MODE_DISTRICT) {


                            return;
                        }


                        showError(
                                message
                        );
                    }
                }
        );
    }


    // ============================================================
    // MAHALLE + KÖY
    // ============================================================

    private void loadSettlements() {


        if (selectedDistrictId <= 0) {


            return;
        }


        showLoading();


        final int requestedDistrictId =
                selectedDistrictId;


        locationRepository.loadSettlements(
                requestedDistrictId,
                new TurkeyLocationRepository.LocationCallback() {


                    @Override
                    public void onSuccess(
                            List<
                                    TurkeyLocationRepository.LocationItem
                                    > items
                    ) {


                        if (currentMode
                                != MODE_SETTLEMENT
                                || requestedDistrictId
                                != selectedDistrictId) {


                            return;
                        }


                        selectedDistrictNeighborhoodCount =
                                countNeighborhoods(
                                        items
                                );


                        updateDistrictCountBadge();


                        setItems(
                                items
                        );
                    }


                    @Override
                    public void onError(
                            String message
                    ) {


                        if (currentMode
                                != MODE_SETTLEMENT) {


                            return;
                        }


                        showError(
                                message
                        );
                    }
                }
        );
    }


    // ============================================================
    // İLÇE SAYISINI ARKA PLANDA TAMAMLA
    // ============================================================

    private void ensureProvinceDistrictCount() {


        if (selectedProvinceId <= 0) {


            return;
        }


        if (selectedProvinceDistrictCount > 0) {


            updateProvinceCountBadge();

            return;
        }


        final int requestedProvinceId =
                selectedProvinceId;


        locationRepository.loadDistricts(
                requestedProvinceId,
                new TurkeyLocationRepository.LocationCallback() {


                    @Override
                    public void onSuccess(
                            List<
                                    TurkeyLocationRepository.LocationItem
                                    > items
                    ) {


                        if (requestedProvinceId
                                != selectedProvinceId) {


                            return;
                        }


                        selectedProvinceDistrictCount =
                                items == null
                                        ? 0
                                        : items.size();


                        updateProvinceCountBadge();
                    }


                    @Override
                    public void onError(
                            String message
                    ) {

                        // Sayaç yardımcı bilgidir.
                    }
                }
        );
    }


    // ============================================================
    // MAHALLE SAY
    // ============================================================

    private int countNeighborhoods(
            List<TurkeyLocationRepository.LocationItem> items
    ) {


        if (items == null
                || items.isEmpty()) {


            return 0;
        }


        int count =
                0;


        for (
                TurkeyLocationRepository.LocationItem item
                : items
        ) {


            if (item != null
                    && item.isNeighborhood()) {


                count++;
            }
        }


        return count;
    }


    // ============================================================
    // İL SAYACI
    // ============================================================

    private void updateProvinceCountBadge() {


        if (tvProvinceCountBadge == null) {


            return;
        }


        if (tvProvinceCrumb == null
                || tvProvinceCrumb.getVisibility()
                != View.VISIBLE
                || selectedProvinceDistrictCount <= 0) {


            hideProvinceCountBadge();

            return;
        }


        tvProvinceCountBadge.setText(
                selectedProvinceDistrictCount
                        + " İlç."
        );


        tvProvinceCountBadge.setVisibility(
                View.VISIBLE
        );
    }


    // ============================================================
    // İLÇE SAYACI
    // ============================================================

    private void updateDistrictCountBadge() {


        if (tvDistrictCountBadge == null) {


            return;
        }


        if (tvDistrictCrumb == null
                || tvDistrictCrumb.getVisibility()
                != View.VISIBLE
                || selectedDistrictNeighborhoodCount <= 0) {


            hideDistrictCountBadge();

            return;
        }


        tvDistrictCountBadge.setText(
                selectedDistrictNeighborhoodCount
                        + " Mh."
        );


        tvDistrictCountBadge.setVisibility(
                View.VISIBLE
        );
    }


    // ============================================================
    // SAYAÇLARI GİZLE
    // ============================================================

    private void hideProvinceCountBadge() {


        if (tvProvinceCountBadge != null) {


            tvProvinceCountBadge.setVisibility(
                    View.GONE
            );
        }
    }


    private void hideDistrictCountBadge() {


        if (tvDistrictCountBadge != null) {


            tvDistrictCountBadge.setVisibility(
                    View.GONE
            );
        }
    }


    private void hideSettlementCountBadge() {


        if (tvSettlementCountBadge != null) {


            tvSettlementCountBadge.setVisibility(
                    View.GONE
            );
        }
    }


    // ============================================================
    // LİSTEYİ YERLEŞTİR
    // ============================================================

    private void setItems(
            List<TurkeyLocationRepository.LocationItem> items
    ) {


        allItems.clear();

        filteredItems.clear();


        if (items != null) {


            allItems.addAll(
                    items
            );


            filteredItems.addAll(
                    items
            );
        }


        progressLocation.setVisibility(
                View.GONE
        );


        listAdapter.notifyDataSetChanged();


        updateEmptyState();
    }


    // ============================================================
    // YÜKLENİYOR
    // ============================================================

    private void showLoading() {


        allItems.clear();

        filteredItems.clear();


        if (listAdapter != null) {


            listAdapter.notifyDataSetChanged();
        }


        tvLocationEmpty.setVisibility(
                View.GONE
        );


        progressLocation.setVisibility(
                View.VISIBLE
        );
    }


    // ============================================================
    // HATA
    // ============================================================

    private void showError(
            String message
    ) {


        progressLocation.setVisibility(
                View.GONE
        );


        allItems.clear();

        filteredItems.clear();


        if (listAdapter != null) {


            listAdapter.notifyDataSetChanged();
        }


        String readableMessage =
                safeString(
                        message
                );


        if (readableMessage.isEmpty()) {


            readableMessage =
                    "Konum bilgileri alınamadı.";
        }


        tvLocationEmpty.setText(
                readableMessage
        );


        tvLocationEmpty.setVisibility(
                View.VISIBLE
        );


        Toast.makeText(
                this,
                readableMessage,
                Toast.LENGTH_LONG
        ).show();
    }


    // ============================================================
    // ARAMA
    // ============================================================

    private void setupSearch() {


        etLocationSearch.addTextChangedListener(
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


                        filterItems(
                                s == null
                                        ? ""
                                        : s.toString()
                        );
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
    // FİLTRE
    // ============================================================

    private void filterItems(
            String query
    ) {


        String normalizedQuery =
                safeString(
                        query
                )
                        .toLowerCase(
                                turkishLocale
                        );


        filteredItems.clear();


        if (normalizedQuery.isEmpty()) {


            filteredItems.addAll(
                    allItems
            );


        } else {


            for (
                    TurkeyLocationRepository.LocationItem item
                    : allItems
            ) {


                if (item == null) {


                    continue;
                }


                String normalizedName =
                        safeString(
                                item.getName()
                        )
                                .toLowerCase(
                                        turkishLocale
                                );


                if (normalizedName.contains(
                        normalizedQuery
                )) {


                    filteredItems.add(
                            item
                    );
                }
            }
        }


        if (listAdapter != null) {


            listAdapter.notifyDataSetChanged();
        }


        updateEmptyState();
    }


    // ============================================================
    // ARAMAYI TEMİZLE
    // ============================================================

    private void clearSearch() {


        if (etLocationSearch != null) {


            etLocationSearch.setText(
                    ""
            );
        }
    }


    // ============================================================
    // BOŞ DURUM
    // ============================================================

    private void updateEmptyState() {


        if (progressLocation.getVisibility()
                == View.VISIBLE) {


            tvLocationEmpty.setVisibility(
                    View.GONE
            );


            return;
        }


        if (filteredItems.isEmpty()) {


            tvLocationEmpty.setText(
                    "Sonuç bulunamadı"
            );


            tvLocationEmpty.setVisibility(
                    View.VISIBLE
            );


        } else {


            tvLocationEmpty.setVisibility(
                    View.GONE
            );
        }
    }


    // ============================================================
    // BUTONLAR
    // ============================================================

    private void setupButtons() {


        btnLocationClose.setOnClickListener(
                view -> finish()
        );


        btnLocationBack.setOnClickListener(
                view -> handleBack()
        );
    }


    // ============================================================
    // BREADCRUMB
    // ============================================================

    private void setupBreadcrumbClicks() {


        // İl kutusuna dokununca aynı ilin ilçelerini aç.
        tvProvinceCrumb.setOnClickListener(
                view -> {


                    clearDistrictSelection();

                    clearSettlementSelection();


                    showDistrictMode();
                }
        );


        // İlçe kutusuna dokununca mahalle/köy listesini aç.
        tvDistrictCrumb.setOnClickListener(
                view -> {


                    clearSettlementSelection();


                    showSettlementMode();
                }
        );


        // Mahalle breadcrumb artık aktif kullanılmıyor.
        tvSettlementCrumb.setOnClickListener(
                null
        );
    }


    // ============================================================
    // GERİ
    // ============================================================

    private void handleBack() {


        // ========================================================
        // MAHALLE -> İLÇE
        // ========================================================

        if (currentMode == MODE_SETTLEMENT) {


            clearSettlementSelection();


            showDistrictMode();

            return;
        }


        // ========================================================
        // İLÇE -> İL
        // ========================================================

        if (currentMode == MODE_DISTRICT) {


            clearDistrictSelection();

            clearSettlementSelection();


            showProvinceMode();

            return;
        }


        finish();
    }


    // ============================================================
    // TELEFON GERİ TUŞU
    // ============================================================

    @Override
    public void onBackPressed() {


        handleBack();
    }


    // ============================================================
    // İLÇE TEMİZLE
    // ============================================================

    private void clearDistrictSelection() {


        selectedDistrictId =
                0;


        selectedDistrictName =
                "";


        selectedDistrictNeighborhoodCount =
                0;
    }


    // ============================================================
    // MAHALLE TEMİZLE
    // ============================================================

    private void clearSettlementSelection() {


        selectedSettlementId =
                0;


        selectedSettlementName =
                "";


        selectedSettlementType =
                "";
    }


    // ============================================================
    // SONUCU GERİ GÖNDER
    // ============================================================

    private void finishWithResult() {


        Intent result =
                new Intent();


        result.putExtra(
                EXTRA_PROVINCE_ID,
                selectedProvinceId
        );


        result.putExtra(
                EXTRA_PROVINCE_NAME,
                selectedProvinceName
        );


        result.putExtra(
                EXTRA_DISTRICT_ID,
                selectedDistrictId
        );


        result.putExtra(
                EXTRA_DISTRICT_NAME,
                selectedDistrictName
        );


        result.putExtra(
                EXTRA_SETTLEMENT_ID,
                selectedSettlementId
        );


        result.putExtra(
                EXTRA_SETTLEMENT_NAME,
                selectedSettlementName
        );


        result.putExtra(
                EXTRA_SETTLEMENT_TYPE,
                selectedSettlementType
        );


        // ========================================================
        // ESKİ SOKAK ALANLARINI BOŞ DÖNDÜR
        // AddGardenActivity temizlenene kadar Build uyumluluğu.
        // ========================================================

        result.putExtra(
                EXTRA_STREET_ID,
                0
        );


        result.putExtra(
                EXTRA_STREET_NAME,
                ""
        );


        setResult(
                RESULT_OK,
                result
        );


        finish();
    }


    // ============================================================
    // SAFE STRING
    // ============================================================

    private String safeString(
            String value
    ) {


        return value == null
                ? ""
                : value.trim();
    }


    // ============================================================
    // ADAPTER
    // ============================================================

    private class LocationListAdapter
            extends BaseAdapter {


        @Override
        public int getCount() {


            return filteredItems.size();
        }


        @Override
        public Object getItem(
                int position
        ) {


            return filteredItems.get(
                    position
            );
        }


        @Override
        public long getItemId(
                int position
        ) {


            return filteredItems
                    .get(position)
                    .getId();
        }


        @Override
        public View getView(
                int position,
                View convertView,
                ViewGroup parent
        ) {


            LinearLayout row;


            if (convertView instanceof LinearLayout) {


                row =
                        (LinearLayout) convertView;


            } else {


                row =
                        createLocationRow();
            }


            TurkeyLocationRepository.LocationItem item =
                    filteredItems.get(
                            position
                    );


            TextView tvName =
                    row.findViewWithTag(
                            "name"
                    );


            TextView tvType =
                    row.findViewWithTag(
                            "type"
                    );


            TextView tvArrow =
                    row.findViewWithTag(
                            "arrow"
                    );


            tvName.setText(
                    item.getName()
            );


            // ====================================================
            // MAHALLE / KÖY
            // ====================================================

            if (currentMode == MODE_SETTLEMENT) {


                if (item.isVillage()) {


                    tvType.setText(
                            "Köy"
                    );


                } else {


                    tvType.setText(
                            "Mahalle"
                    );
                }


                tvType.setVisibility(
                        View.VISIBLE
                );


            } else {


                tvType.setVisibility(
                        View.GONE
                );
            }


            tvArrow.setText(
                    "›"
            );


            return row;
        }
    }


    // ============================================================
    // LİSTE SATIRI
    // ============================================================

    private LinearLayout createLocationRow() {


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


        row.setPadding(
                dp(22),
                dp(12),
                dp(18),
                dp(12)
        );


        row.setMinimumHeight(
                dp(72)
        );


        row.setBackgroundColor(
                Color.WHITE
        );


        // ========================================================
        // SOL YAZI ALANI
        // ========================================================

        LinearLayout textArea =
                new LinearLayout(
                        this
                );


        textArea.setOrientation(
                LinearLayout.VERTICAL
        );


        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                );


        // ========================================================
        // İSİM
        // ========================================================

        TextView tvName =
                new TextView(
                        this
                );


        tvName.setTag(
                "name"
        );


        tvName.setTextColor(
                GreenTheme.parse(
                        "#173C29"
                )
        );


        tvName.setTextSize(
                17
        );


        tvName.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        textArea.addView(
                tvName
        );


        // ========================================================
        // TİP
        // ========================================================

        TextView tvType =
                new TextView(
                        this
                );


        tvType.setTag(
                "type"
        );


        tvType.setTextColor(
                GreenTheme.color(R.color.zd_muted)
        );


        tvType.setTextSize(
                11
        );


        tvType.setPadding(
                0,
                dp(3),
                0,
                0
        );


        tvType.setVisibility(
                View.GONE
        );


        textArea.addView(
                tvType
        );


        row.addView(
                textArea,
                textParams
        );


        // ========================================================
        // SAĞ OK
        // ========================================================

        TextView tvArrow =
                new TextView(
                        this
                );


        tvArrow.setTag(
                "arrow"
        );


        tvArrow.setText(
                "›"
        );


        tvArrow.setTextColor(
                GreenTheme.parse(
                        "#167044"
                )
        );


        tvArrow.setTextSize(
                29
        );


        tvArrow.setGravity(
                Gravity.CENTER
        );


        row.addView(
                tvArrow,
                new LinearLayout.LayoutParams(
                        dp(44),
                        dp(48)
                )
        );


        return row;
    }


    // ============================================================
    // DP
    // ============================================================

    private int dp(
            int value
    ) {


        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }


    // ============================================================
    // KAPANIŞ
    // ============================================================

    @Override
    protected void onDestroy() {


        if (locationRepository != null) {


            locationRepository.shutdown();
        }


        super.onDestroy();
    }
}