package com.zeytindefteri.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;


public class AddGardenActivity extends Activity {


    // ============================================================
    // REQUEST CODE
    // ============================================================

    private static final int REQUEST_LOCATION_PICKER =
            2001;

    private static final int REQUEST_GARDEN_PHOTO =
            2002;


    // ============================================================
    // PREFS
    // ============================================================

    private static final String PREFS_NAME =
            "ZeytinDefteriPrefs";

    private static final String KEY_WELCOME_COMPLETED =
            "welcome_completed";

    private static final String KEY_ONBOARDING_COMPLETED =
            "onboarding_completed";


    // ============================================================
    // RENKLER
    // ============================================================

    private static final String COLOR_BG =
            "#0B1D28";

    private static final String COLOR_GREEN =
            "#4F745B";

    private static final String COLOR_GREEN_DARK =
            "#F2F7F0";

    private static final String COLOR_TEXT =
            "#F4F7F4";

    private static final String COLOR_MUTED =
            "#9AABA8";

    private static final String COLOR_BORDER =
            "#29434D";

    private static final String COLOR_CARD =
            "#132A38";

    private static final String COLOR_SOFT_GREEN =
            "#19392F";


    // ============================================================
    // İLK BAHÇE
    // ============================================================

    private boolean firstGarden =
            false;


    // ============================================================
    // KAYIT DEVAM EDİYOR MU?
    // ============================================================

    private boolean saving =
            false;


    // ============================================================
    // VERİTABANI
    // ============================================================

    private DatabaseHelper databaseHelper;
    private GardenRepository gardenRepository;
    private GardenCloudRepository gardenCloudRepository;


    // ============================================================
    // FORM
    // ============================================================

    private EditText etGardenName;

    private EditText etTreeCount;

    private EditText etGardenAge;

    private EditText etVariety;

    private EditText etAreaDecare;

    private EditText etGardenNotes;


    // ============================================================
    // KONUM
    // ============================================================

    private AutoCompleteTextView etProvince;

    private AutoCompleteTextView etDistrict;

    private AutoCompleteTextView etNeighborhood;


    // ============================================================
    // KONUM ID
    // ============================================================

    private int selectedProvinceId =
            0;

    private int selectedDistrictId =
            0;

    private int selectedSettlementId =
            0;


    private String selectedSettlementType =
            "";


    // ============================================================
    // DİĞER
    // ============================================================

    private Spinner spinnerIrrigation;

    private TextView btnSaveGarden;

    private TextView btnBack;

    private View btnPhoto;

    private TextView tvPhotoStatus;

    private TextView tvTitle;

    private TextView tvSubtitle;


    // ============================================================
    // FOTOĞRAF
    // ============================================================

    private Uri selectedPhotoUri =
            null;


    // ============================================================
    // SON KAYDEDİLEN BAHÇE
    // ============================================================

    private long savedGardenId =
            -1;

    private String savedGardenName =
            "";

    private int savedTreeCount =
            0;

    private int savedAge =
            0;

    private String savedVariety =
            "";

    private String savedProvince =
            "";

    private String savedDistrict =
            "";

    private String savedNeighborhood =
            "";


    // ============================================================
    // SULAMA TİPLERİ
    // ============================================================

    private final List<String> irrigationTypes =
            Arrays.asList(
                    "Belirtilmedi",
                    "Damla sulama",
                    "Salma sulama",
                    "Yağmurlama",
                    "Gölet / tanker",
                    "Kuyu / sondaj",
                    "Kuru tarım",
                    "Diğer"
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


        // ========================================================
        // ACTION BAR
        // ========================================================

        if (getActionBar() != null) {

            getActionBar().hide();
        }


        // ========================================================
        // STATUS BAR
        // ========================================================

        getWindow().setStatusBarColor(
                GreenTheme.parse(
                        COLOR_BG
                )
        );
        getWindow().setNavigationBarColor(GreenTheme.parse(COLOR_BG));


        getWindow()
                .getDecorView()
                .setSystemUiVisibility(
                        0
                );


        // ========================================================
        // LAYOUT
        // ========================================================

        setContentView(
                R.layout.activity_add_garden
        );


        // ========================================================
        // İLK BAHÇE Mİ?
        // ========================================================

        firstGarden =
                getIntent().getBooleanExtra(
                        "first_garden",
                        false
                );


        // ========================================================
        // DATABASE
        // ========================================================

        databaseHelper =
                new DatabaseHelper(
                        this
                );


        gardenRepository =
                new GardenRepository(
                        databaseHelper
                );

        gardenCloudRepository =
                new GardenCloudRepository(
                        databaseHelper
                );


        // ========================================================
        // VIEW
        // ========================================================

        findViews();


        // ========================================================
        // BAŞLIK
        // ========================================================

        configureScreenMode();


        // ========================================================
        // KONUM
        // ========================================================

        setupLocationPicker();


        // ========================================================
        // SULAMA
        // ========================================================

        setupIrrigationSpinner();


        // ========================================================
        // BUTON
        // ========================================================

        setupButtons();


        // ========================================================
        // GİRİŞ ANİMASYONU
        // ========================================================

        animateEntrance();
    }


    // ============================================================
    // VIEW'LARI BUL
    // ============================================================

    private void findViews() {

        etGardenName =
                findViewById(
                        R.id.etGardenName
                );


        etTreeCount =
                findViewById(
                        R.id.etTreeCount
                );


        etGardenAge =
                findViewById(
                        R.id.etGardenAge
                );


        etVariety =
                findViewById(
                        R.id.etVariety
                );


        etAreaDecare =
                findViewById(
                        R.id.etAreaDecare
                );


        etProvince =
                findViewById(
                        R.id.etProvince
                );


        etDistrict =
                findViewById(
                        R.id.etDistrict
                );


        etNeighborhood =
                findViewById(
                        R.id.etNeighborhood
                );


        spinnerIrrigation =
                findViewById(
                        R.id.spinnerIrrigation
                );


        etGardenNotes =
                findViewById(
                        R.id.etGardenNotes
                );


        btnSaveGarden =
                findViewById(
                        R.id.btnSaveGarden
                );


        btnBack =
                findViewById(
                        R.id.btnBack
                );


        btnPhoto =
                findViewById(
                        R.id.btnPhoto
                );


        tvPhotoStatus =
                findViewById(
                        R.id.tvPhotoStatus
                );


        tvTitle =
                findViewById(
                        R.id.tvTitle
                );


        tvSubtitle =
                findViewById(
                        R.id.tvSubtitle
                );
    }


    // ============================================================
    // EKRAN MODU
    // ============================================================

    private void configureScreenMode() {

        if (firstGarden) {

            if (tvTitle != null) {

                tvTitle.setText(
                        "İlk bahçeni oluşturalım"
                );
            }


            if (tvSubtitle != null) {

                tvSubtitle.setText(
                        "Zeytin Defteri bahçelerin üzerinden çalışır.\n"
                                + "İlk zeytinliğini kaydederek başlayalım."
                );
            }


            if (btnSaveGarden != null) {

                btnSaveGarden.setText(
                        "🌿  Bahçemi Kaydet"
                );
            }

        } else {

            if (tvTitle != null) {

                tvTitle.setText(
                        "Yeni bahçe ekle"
                );
            }


            if (tvSubtitle != null) {

                tvSubtitle.setText(
                        "Zeytinliğinin temel bilgilerini kaydet."
                );
            }


            if (btnSaveGarden != null) {

                btnSaveGarden.setText(
                        "🌿  Bahçeyi Kaydet"
                );
            }
        }
    }


    // ============================================================
    // GİRİŞ ANİMASYONU
    // ============================================================

    private void animateEntrance() {

        View content =
                findViewById(
                        R.id.formContent
                );


        if (content == null) {

            return;
        }


        content.setAlpha(
                0f
        );


        content.setTranslationY(
                dp(12)
        );


        content.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(380)
                .setInterpolator(
                        new AccelerateDecelerateInterpolator()
                )
                .start();
    }


    // ============================================================
    // KONUM SEÇİMİ
    // ============================================================

    private void setupLocationPicker() {

        prepareLocationField(
                etProvince
        );


        prepareLocationField(
                etDistrict
        );


        prepareLocationField(
                etNeighborhood
        );


        etProvince.setHint(
                "İl seç..."
        );


        etDistrict.setHint(
                "İlçe seç..."
        );


        etNeighborhood.setHint(
                "Mahalle / köy seç..."
        );


        etProvince.setOnClickListener(
                view -> openLocationPicker(
                        LocationPickerActivity.START_PROVINCE
                )
        );


        etDistrict.setOnClickListener(
                view -> openLocationPicker(
                        LocationPickerActivity.START_DISTRICT
                )
        );


        etNeighborhood.setOnClickListener(
                view -> openLocationPicker(
                        LocationPickerActivity.START_SETTLEMENT
                )
        );
    }


    // ============================================================
    // KONUM ALANI
    // ============================================================

    private void prepareLocationField(
            AutoCompleteTextView field
    ) {

        if (field == null) {

            return;
        }


        field.setEnabled(
                true
        );


        field.setKeyListener(
                null
        );


        field.setCursorVisible(
                false
        );


        field.setFocusable(
                false
        );


        field.setFocusableInTouchMode(
                false
        );


        field.setClickable(
                true
        );


        field.setLongClickable(
                false
        );
    }


    // ============================================================
    // KONUM SEÇİCİ
    // ============================================================

    private void openLocationPicker() {

        openLocationPicker(
                LocationPickerActivity.START_PROVINCE
        );
    }


    private void openLocationPicker(
            int startMode
    ) {

        Intent intent =
                new Intent(
                        AddGardenActivity.this,
                        LocationPickerActivity.class
                );


        intent.putExtra(
                LocationPickerActivity.EXTRA_START_MODE,
                startMode
        );


        intent.putExtra(
                LocationPickerActivity.EXTRA_PROVINCE_ID,
                selectedProvinceId
        );


        intent.putExtra(
                LocationPickerActivity.EXTRA_PROVINCE_NAME,
                text(
                        etProvince
                )
        );


        intent.putExtra(
                LocationPickerActivity.EXTRA_DISTRICT_ID,
                selectedDistrictId
        );


        intent.putExtra(
                LocationPickerActivity.EXTRA_DISTRICT_NAME,
                text(
                        etDistrict
                )
        );


        intent.putExtra(
                LocationPickerActivity.EXTRA_SETTLEMENT_ID,
                selectedSettlementId
        );


        intent.putExtra(
                LocationPickerActivity.EXTRA_SETTLEMENT_NAME,
                text(
                        etNeighborhood
                )
        );


        intent.putExtra(
                LocationPickerActivity.EXTRA_SETTLEMENT_TYPE,
                selectedSettlementType
        );


        startActivityForResult(
                intent,
                REQUEST_LOCATION_PICKER
        );
    }


    // ============================================================
    // ACTIVITY RESULT
    // ============================================================

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );


        // ========================================================
        // KONUM
        // ========================================================

        if (requestCode
                == REQUEST_LOCATION_PICKER) {

            handleLocationResult(
                    resultCode,
                    data
            );

            return;
        }


        // ========================================================
        // FOTOĞRAF
        // ========================================================

        if (requestCode
                == REQUEST_GARDEN_PHOTO) {

            handlePhotoResult(
                    resultCode,
                    data
            );
        }
    }


    // ============================================================
    // KONUM SONUCU
    // ============================================================

    private void handleLocationResult(
            int resultCode,
            Intent data
    ) {

        if (resultCode
                != RESULT_OK) {

            return;
        }


        if (data == null) {

            return;
        }


        selectedProvinceId =
                data.getIntExtra(
                        LocationPickerActivity.EXTRA_PROVINCE_ID,
                        0
                );


        selectedDistrictId =
                data.getIntExtra(
                        LocationPickerActivity.EXTRA_DISTRICT_ID,
                        0
                );


        selectedSettlementId =
                data.getIntExtra(
                        LocationPickerActivity.EXTRA_SETTLEMENT_ID,
                        0
                );


        String province =
                safeString(
                        data.getStringExtra(
                                LocationPickerActivity.EXTRA_PROVINCE_NAME
                        )
                );


        String district =
                safeString(
                        data.getStringExtra(
                                LocationPickerActivity.EXTRA_DISTRICT_NAME
                        )
                );


        String settlement =
                safeString(
                        data.getStringExtra(
                                LocationPickerActivity.EXTRA_SETTLEMENT_NAME
                        )
                );


        selectedSettlementType =
                safeString(
                        data.getStringExtra(
                                LocationPickerActivity.EXTRA_SETTLEMENT_TYPE
                        )
                );


        etProvince.setText(
                province,
                false
        );


        etDistrict.setText(
                district,
                false
        );


        etNeighborhood.setText(
                settlement,
                false
        );


        String location =
                buildLocationText(
                        province,
                        district,
                        settlement
                );


        if (!location.isEmpty()) {

            Toast.makeText(
                    this,
                    "📍 " + location,
                    Toast.LENGTH_SHORT
            ).show();
        }
    }


    // ============================================================
    // FOTOĞRAF SEÇ
    // ============================================================

    private void openPhotoPicker() {

        try {

            Intent intent =
                    new Intent(
                            Intent.ACTION_OPEN_DOCUMENT
                    );


            intent.addCategory(
                    Intent.CATEGORY_OPENABLE
            );


            intent.setType(
                    "image/*"
            );


            intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            );


            startActivityForResult(
                    intent,
                    REQUEST_GARDEN_PHOTO
            );


        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Fotoğraf seçici açılamadı.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }


    // ============================================================
    // FOTOĞRAF SONUCU
    // ============================================================

    private void handlePhotoResult(
            int resultCode,
            Intent data
    ) {

        if (resultCode
                != RESULT_OK) {

            return;
        }


        if (data == null
                || data.getData() == null) {

            return;
        }


        Uri uri =
                data.getData();


        try {

            final int takeFlags =
                    data.getFlags()
                            & Intent.FLAG_GRANT_READ_URI_PERMISSION;


            getContentResolver()
                    .takePersistableUriPermission(
                            uri,
                            takeFlags
                    );


        } catch (Exception ignored) {

        }


        selectedPhotoUri =
                uri;


        if (tvPhotoStatus != null) {

            tvPhotoStatus.setText(
                    "✓ Fotoğraf seçildi"
            );


            tvPhotoStatus.setTextColor(
                    GreenTheme.parse(
                            COLOR_GREEN
                    )
            );


            tvPhotoStatus.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }


        Toast.makeText(
                this,
                "Bahçe fotoğrafı hazır 📷",
                Toast.LENGTH_SHORT
        ).show();
    }


    // ============================================================
    // SULAMA SPINNER
    // ============================================================

    private void setupIrrigationSpinner() {

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        irrigationTypes
                );


        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );


        spinnerIrrigation.setAdapter(
                adapter
        );
    }


    // ============================================================
    // BUTONLAR
    // ============================================================

    private void setupButtons() {

        btnBack.setOnClickListener(
                view -> {

                    if (!saving) {

                        finish();
                    }
                }
        );


        btnPhoto.setOnClickListener(
                view -> {

                    if (!saving) {

                        pressEffect(
                                btnPhoto,
                                this::openPhotoPicker
                        );
                    }
                }
        );


        btnSaveGarden.setOnClickListener(
                view -> {

                    if (!saving) {

                        pressEffect(
                                btnSaveGarden,
                                this::saveGarden
                        );
                    }
                }
        );
    }


    // ============================================================
    // BAHÇEYİ KAYDET
    // ============================================================

    private void saveGarden() {

        if (saving) {

            return;
        }


        // ========================================================
        // BAHÇE ADI
        // ========================================================

        String name =
                text(
                        etGardenName
                );


        if (name.isEmpty()) {

            etGardenName.setError(
                    "Bahçe adı gerekli"
            );


            etGardenName.requestFocus();

            return;
        }


        // ========================================================
        // AĞAÇ
        // ========================================================

        int treeCount =
                readInt(
                        etTreeCount
                );


        if (firstGarden
                && treeCount <= 0) {

            etTreeCount.setError(
                    "Ağaç sayısını yaz"
            );


            etTreeCount.requestFocus();

            return;
        }


        // ========================================================
        // YAŞ
        // ========================================================

        int age =
                readInt(
                        etGardenAge
                );


        if (firstGarden
                && age <= 0) {

            etGardenAge.setError(
                    "Ağaç yaşını yaz"
            );


            etGardenAge.requestFocus();

            return;
        }


        // ========================================================
        // ALAN
        // ========================================================

        double areaDecare =
                readDouble(
                        etAreaDecare
                );


        // ========================================================
        // ÇEŞİT
        // ========================================================

        String variety =
                text(
                        etVariety
                );


        // ========================================================
        // KONUM
        // ========================================================

        String province =
                text(
                        etProvince
                );


        String district =
                text(
                        etDistrict
                );


        String neighborhood =
                text(
                        etNeighborhood
                );


        // ========================================================
        // İLK BAHÇEDE KONUM GEREKLİ
        // ========================================================

        if (firstGarden
                && (
                selectedProvinceId <= 0
                        || selectedDistrictId <= 0
                        || selectedSettlementId <= 0
        )) {

            Toast.makeText(
                    this,
                    "İlk bahçenin konumunu seçelim 📍",
                    Toast.LENGTH_SHORT
            ).show();


            openLocationPicker();

            return;
        }


        // ========================================================
        // NORMAL MODDA YARIM KONUM KONTROLÜ
        // ========================================================

        if (!province.isEmpty()
                || !district.isEmpty()
                || !neighborhood.isEmpty()) {

            if (selectedProvinceId <= 0
                    || selectedDistrictId <= 0
                    || selectedSettlementId <= 0) {

                Toast.makeText(
                        this,
                        "Konumu listeden yeniden seç.",
                        Toast.LENGTH_SHORT
                ).show();


                openLocationPicker();

                return;
            }
        }


        // ========================================================
        // SULAMA
        // ========================================================

        String irrigationType =
                "";


        if (spinnerIrrigation != null
                && spinnerIrrigation.getSelectedItem() != null) {

            irrigationType =
                    spinnerIrrigation
                            .getSelectedItem()
                            .toString();
        }


        if ("Belirtilmedi".equals(
                irrigationType
        )) {

            irrigationType =
                    "";
        }


        // ========================================================
        // NOT
        // ========================================================

        String notes =
                text(
                        etGardenNotes
                );


        // ========================================================
        // FOTOĞRAF
        // ========================================================

        String photoUri =
                selectedPhotoUri == null
                        ? ""
                        : selectedPhotoUri.toString();


        // ========================================================
        // KAYIT
        // ========================================================

        setSavingState(
                true
        );


        long result;


        try {

            FirebaseUser currentUser =
                    FirebaseAuth.getInstance().getCurrentUser();

            if (currentUser != null
                    && currentUser.getUid() != null
                    && !currentUser.getUid().trim().isEmpty()) {

                String ownerUid =
                        currentUser.getUid().trim();

                String cloudId =
                        UUID.randomUUID().toString();

                result =
                        gardenRepository.addGardenForOwner(
                                ownerUid,
                                cloudId,
                                name,
                                treeCount,
                                age,
                                variety,
                                areaDecare,
                                province,
                                district,
                                neighborhood,
                                irrigationType,
                                notes,
                                photoUri
                        );

            } else {

                result =
                        gardenRepository.addGarden(
                                name,
                                treeCount,
                                age,
                                variety,
                                areaDecare,
                                province,
                                district,
                                neighborhood,
                                irrigationType,
                                notes,
                                photoUri
                        );
            }


        } catch (Exception e) {

            result =
                    -1;
        }


        // ========================================================
        // HATA
        // ========================================================

        if (result == -1) {

            setSavingState(
                    false
            );


            Toast.makeText(
                    this,
                    "Bahçe kaydedilemedi.",
                    Toast.LENGTH_SHORT
            ).show();


            return;
        }


        // ========================================================
        // BAŞARI BİLGİSİ
        // ========================================================

        savedGardenId =
                result;

        savedGardenName =
                name;

        savedTreeCount =
                treeCount;

        savedAge =
                age;

        savedVariety =
                variety;

        savedProvince =
                province;

        savedDistrict =
                district;

        savedNeighborhood =
                neighborhood;


        FirebaseUser syncUser =
                FirebaseAuth.getInstance().getCurrentUser();

        if (syncUser != null
                && syncUser.getUid() != null
                && !syncUser.getUid().trim().isEmpty()) {

            gardenCloudRepository.syncGardenByLocalId(
                    syncUser.getUid().trim(),
                    savedGardenId
            );
        }


        // ========================================================
        // İLK KURULUM
        // ========================================================

        if (firstGarden) {

            markFirstSetupCompleted();

            showFirstGardenSuccess();

            return;
        }


        // ========================================================
        // NORMAL BAHÇE EKLEME
        // ========================================================

        Toast.makeText(
                this,
                "Bahçe kaydedildi ✓",
                Toast.LENGTH_SHORT
        ).show();


        setResult(
                RESULT_OK
        );


        finish();
    }


    // ============================================================
    // KAYDETME DURUMU
    // ============================================================

    private void setSavingState(
            boolean value
    ) {

        saving =
                value;


        if (btnSaveGarden != null) {

            btnSaveGarden.setEnabled(
                    !value
            );


            btnSaveGarden.setAlpha(
                    value
                            ? 0.78f
                            : 1f
            );


            if (value) {

                btnSaveGarden.setText(
                        "Kaydediliyor..."
                );

            } else {

                if (firstGarden) {

                    btnSaveGarden.setText(
                            "🌿  Bahçemi Kaydet"
                    );

                } else {

                    btnSaveGarden.setText(
                            "🌿  Bahçeyi Kaydet"
                    );
                }
            }
        }
    }


    // ============================================================
    // İLK KURULUM PREFS
    // ============================================================

    private void markFirstSetupCompleted() {

        SharedPreferences preferences =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                );


        preferences.edit()

                .putBoolean(
                        KEY_WELCOME_COMPLETED,
                        true
                )

                .putBoolean(
                        KEY_ONBOARDING_COMPLETED,
                        true
                )

                .apply();
    }


    // ============================================================
    // BAŞARI EKRANI
    // ============================================================

    private void showFirstGardenSuccess() {

        saving =
                false;


        // ========================================================
        // ROOT
        // ========================================================

        LinearLayout root =
                new LinearLayout(
                        this
                );


        root.setOrientation(
                LinearLayout.VERTICAL
        );


        root.setGravity(
                Gravity.CENTER_HORIZONTAL
        );


        root.setBackgroundColor(
                GreenTheme.parse(
                        COLOR_BG
                )
        );


        ScrollView scroll =
                new ScrollView(
                        this
                );


        scroll.setFillViewport(
                true
        );


        LinearLayout content =
                new LinearLayout(
                        this
                );


        content.setOrientation(
                LinearLayout.VERTICAL
        );


        content.setGravity(
                Gravity.CENTER_HORIZONTAL
        );


        content.setPadding(
                dp(24),
                dp(42),
                dp(24),
                dp(30)
        );


        // ========================================================
        // CHECK
        // ========================================================

        TextView check =
                new TextView(
                        this
                );


        check.setText(
                "✓"
        );


        check.setTextSize(
                38
        );


        check.setTextColor(
                Color.WHITE
        );


        check.setGravity(
                Gravity.CENTER
        );


        check.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        check.setBackground(
                rounded(
                        "#5E9B43",
                        60,
                        null,
                        0
                )
        );


        content.addView(
                check,
                new LinearLayout.LayoutParams(
                        dp(86),
                        dp(86)
                )
        );


        // ========================================================
        // BAŞLIK
        // ========================================================

        TextView title =
                new TextView(
                        this
                );


        title.setText(
                "Bahçen hazır!"
        );


        title.setTextSize(
                30
        );


        title.setTextColor(
                GreenTheme.parse(
                        COLOR_GREEN_DARK
                )
        );


        title.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        title.setGravity(
                Gravity.CENTER
        );


        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        titleParams.topMargin =
                dp(22);


        content.addView(
                title,
                titleParams
        );


        // ========================================================
        // ALT METİN
        // ========================================================

        TextView subtitle =
                new TextView(
                        this
                );


        subtitle.setText(
                "Artık kaydettiğin bahçenle\n"
                        + "tüm sezonunu yönetebilirsin."
        );


        subtitle.setTextSize(
                14
        );


        subtitle.setTextColor(
                GreenTheme.parse(
                        COLOR_MUTED
                )
        );


        subtitle.setGravity(
                Gravity.CENTER
        );


        subtitle.setLineSpacing(
                dp(3),
                1f
        );


        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        subtitleParams.topMargin =
                dp(9);


        content.addView(
                subtitle,
                subtitleParams
        );


        // ========================================================
        // AĞAÇ HERO
        // ========================================================

        TextView treeHero =
                new TextView(
                        this
                );


        treeHero.setText(
                "🌳"
        );


        treeHero.setTextSize(
                86
        );


        treeHero.setGravity(
                Gravity.CENTER
        );


        LinearLayout.LayoutParams treeParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(155)
                );


        treeParams.topMargin =
                dp(8);


        content.addView(
                treeHero,
                treeParams
        );


        // ========================================================
        // BAHÇE KARTI
        // ========================================================

        LinearLayout gardenCard =
                new LinearLayout(
                        this
                );


        gardenCard.setOrientation(
                LinearLayout.VERTICAL
        );


        gardenCard.setPadding(
                dp(20),
                dp(19),
                dp(20),
                dp(19)
        );


        gardenCard.setBackground(
                rounded(
                        COLOR_CARD,
                        23,
                        COLOR_BORDER,
                        1
                )
        );


        gardenCard.setElevation(
                dp(3)
        );


        TextView gardenName =
                new TextView(
                        this
                );


        gardenName.setText(
                savedGardenName
        );


        gardenName.setTextSize(
                22
        );


        gardenName.setTextColor(
                GreenTheme.parse(
                        COLOR_TEXT
                )
        );


        gardenName.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        gardenName.setGravity(
                Gravity.CENTER
        );


        gardenCard.addView(
                gardenName
        );


        // ========================================================
        // ÖZET
        // ========================================================

        TextView summary =
                new TextView(
                        this
                );


        summary.setText(
                buildGardenSummary()
        );


        summary.setTextSize(
                13
        );


        summary.setTextColor(
                GreenTheme.color(R.color.zd_muted)
        );


        summary.setGravity(
                Gravity.CENTER
        );


        summary.setLineSpacing(
                dp(4),
                1f
        );


        LinearLayout.LayoutParams summaryParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        summaryParams.topMargin =
                dp(12);


        gardenCard.addView(
                summary,
                summaryParams
        );


        // ========================================================
        // MESAJ
        // ========================================================

        TextView savedMessage =
                new TextView(
                        this
                );


        savedMessage.setText(
                "🌿  Bahçen başarıyla kaydedildi.\n"
                        + "Şimdi ilk sezon kaydını oluşturmaya hazırsın."
        );


        savedMessage.setTextSize(
                12
        );


        savedMessage.setTextColor(
                GreenTheme.parse(
                        COLOR_MUTED
                )
        );


        savedMessage.setGravity(
                Gravity.CENTER
        );


        savedMessage.setLineSpacing(
                dp(3),
                1f
        );


        LinearLayout.LayoutParams messageParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        messageParams.topMargin =
                dp(17);


        gardenCard.addView(
                savedMessage,
                messageParams
        );


        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        cardParams.topMargin =
                dp(4);


        content.addView(
                gardenCard,
                cardParams
        );


        // ========================================================
        // BAHÇEME GİT
        // ========================================================

        TextView btnGoGarden =
                createPrimaryButton(
                        "Bahçeme Git   →"
                );


        LinearLayout.LayoutParams goParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(62)
                );


        goParams.topMargin =
                dp(22);


        content.addView(
                btnGoGarden,
                goParams
        );


        btnGoGarden.setOnClickListener(
                view -> pressEffect(
                        btnGoGarden,
                        this::openMainAfterFirstSetup
                )
        );


        // ========================================================
        // BAŞKA BAHÇE EKLE
        // ========================================================

        TextView btnAnotherGarden =
                createSecondaryButton(
                        "Başka Bahçe Ekle   +"
                );


        LinearLayout.LayoutParams anotherParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(56)
                );


        anotherParams.topMargin =
                dp(10);


        content.addView(
                btnAnotherGarden,
                anotherParams
        );


        btnAnotherGarden.setOnClickListener(
                view -> pressEffect(
                        btnAnotherGarden,
                        this::restartAsNormalGarden
                )
        );


        // ========================================================
        // SCROLL
        // ========================================================

        scroll.addView(
                content,
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT
                )
        );


        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                )
        );


        // ========================================================
        // GÖSTER
        // ========================================================

        setContentView(
                root
        );


        content.setAlpha(
                0f
        );


        content.setTranslationY(
                dp(12)
        );


        content.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(420)
                .setInterpolator(
                        new AccelerateDecelerateInterpolator()
                )
                .start();


        check.setScaleX(
                0.55f
        );


        check.setScaleY(
                0.55f
        );


        check.setAlpha(
                0f
        );


        check.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(430)
                .setInterpolator(
                        new AccelerateDecelerateInterpolator()
                )
                .start();
    }


    // ============================================================
    // ÖZET
    // ============================================================

    private String buildGardenSummary() {

        StringBuilder builder =
                new StringBuilder();


        if (savedTreeCount > 0) {

            builder.append(
                    "🌳 "
            );

            builder.append(
                    savedTreeCount
            );

            builder.append(
                    " ağaç"
            );
        }


        if (savedAge > 0) {

            appendSeparator(
                    builder
            );


            builder.append(
                    "📅 "
            );

            builder.append(
                    savedAge
            );

            builder.append(
                    " yaş"
            );
        }


        if (!safeString(
                savedVariety
        ).isEmpty()) {

            appendSeparator(
                    builder
            );


            builder.append(
                    "🫒 "
            );

            builder.append(
                    savedVariety
            );
        }


        String location =
                buildLocationText(
                        savedProvince,
                        savedDistrict,
                        savedNeighborhood
                );


        if (!location.isEmpty()) {

            if (builder.length() > 0) {

                builder.append(
                        "\n"
                );
            }


            builder.append(
                    "📍 "
            );

            builder.append(
                    location
            );
        }


        return builder.toString();
    }


    // ============================================================
    // AYIRAÇ
    // ============================================================

    private void appendSeparator(
            StringBuilder builder
    ) {

        if (builder.length() > 0) {

            builder.append(
                    "   •   "
            );
        }
    }


    // ============================================================
    // İLK KURULUM SONRASI ANA EKRAN
    // ============================================================

    private void openMainAfterFirstSetup() {

        Intent intent =
                new Intent(
                        AddGardenActivity.this,
                        MainActivity.class
                );


        intent.putExtra(
                "first_setup_completed",
                true
        );


        intent.putExtra(
                "first_garden_id",
                savedGardenId
        );


        intent.putExtra(
                "first_garden_name",
                savedGardenName
        );


        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );


        startActivity(
                intent
        );


        overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out
        );


        finish();
    }


    // ============================================================
    // BAŞKA BAHÇE EKLE
    // ============================================================

    private void restartAsNormalGarden() {

        Intent intent =
                new Intent(
                        AddGardenActivity.this,
                        AddGardenActivity.class
                );


        intent.putExtra(
                "first_garden",
                false
        );


        startActivity(
                intent
        );


        overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out
        );


        finish();
    }


    // ============================================================
    // PRIMARY BUTTON
    // ============================================================

    private TextView createPrimaryButton(
            String text
    ) {

        TextView button =
                new TextView(
                        this
                );


        button.setText(
                text
        );


        button.setTextSize(
                17
        );


        button.setTextColor(
                Color.WHITE
        );


        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        button.setGravity(
                Gravity.CENTER
        );


        button.setClickable(
                true
        );


        button.setFocusable(
                true
        );


        button.setBackground(
                rounded(
                        COLOR_GREEN,
                        19,
                        null,
                        0
                )
        );


        button.setElevation(
                dp(3)
        );


        return button;
    }


    // ============================================================
    // SECONDARY BUTTON
    // ============================================================

    private TextView createSecondaryButton(
            String text
    ) {

        TextView button =
                new TextView(
                        this
                );


        button.setText(
                text
        );


        button.setTextSize(
                15
        );


        button.setTextColor(
                GreenTheme.parse(
                        COLOR_GREEN
                )
        );


        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );


        button.setGravity(
                Gravity.CENTER
        );


        button.setClickable(
                true
        );


        button.setFocusable(
                true
        );


        button.setBackground(
                rounded(
                        "#102632",
                        19,
                        COLOR_GREEN,
                        1
                )
        );


        return button;
    }


    // ============================================================
    // BASMA ANİMASYONU
    // ============================================================

    private void pressEffect(
            View view,
            Runnable runnable
    ) {

        if (view == null) {

            if (runnable != null) {

                runnable.run();
            }

            return;
        }


        view.animate()
                .cancel();


        view.animate()
                .scaleX(0.975f)
                .scaleY(0.975f)
                .setDuration(80)
                .withEndAction(
                        () -> view.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(100)
                                .withEndAction(
                                        () -> {

                                            if (runnable != null) {

                                                runnable.run();
                                            }
                                        }
                                )
                                .start()
                )
                .start();
    }


    // ============================================================
    // KONUM METNİ
    // ============================================================

    private String buildLocationText(
            String province,
            String district,
            String neighborhood
    ) {

        StringBuilder builder =
                new StringBuilder();


        appendLocationPart(
                builder,
                province
        );


        appendLocationPart(
                builder,
                district
        );


        appendLocationPart(
                builder,
                neighborhood
        );


        return builder.toString();
    }


    private void appendLocationPart(
            StringBuilder builder,
            String value
    ) {

        value =
                safeString(
                        value
                );


        if (value.isEmpty()) {

            return;
        }


        if (builder.length() > 0) {

            builder.append(
                    " / "
            );
        }


        builder.append(
                value
        );
    }


    // ============================================================
    // DRAWABLE
    // ============================================================

    private GradientDrawable rounded(
            String color,
            int radius,
            String strokeColor,
            int strokeWidth
    ) {

        GradientDrawable drawable =
                new GradientDrawable();


        drawable.setColor(
                GreenTheme.parse(
                        color
                )
        );


        drawable.setCornerRadius(
                dp(radius)
        );


        if (strokeColor != null
                && strokeWidth > 0) {

            drawable.setStroke(
                    dp(strokeWidth),
                    GreenTheme.parse(
                            strokeColor
                    )
            );
        }


        return drawable;
    }


    // ============================================================
    // TEXT
    // ============================================================

    private String text(
            EditText editText
    ) {

        if (editText == null) {

            return "";
        }


        return editText
                .getText()
                .toString()
                .trim();
    }


    // ============================================================
    // SAFE
    // ============================================================

    private String safeString(
            String value
    ) {

        return value == null
                ? ""
                : value.trim();
    }


    // ============================================================
    // INT
    // ============================================================

    private int readInt(
            EditText editText
    ) {

        String value =
                text(
                        editText
                );


        if (value.isEmpty()) {

            return 0;
        }


        try {

            return Integer.parseInt(
                    value
            );


        } catch (Exception e) {

            return 0;
        }
    }


    // ============================================================
    // DOUBLE
    // ============================================================

    private double readDouble(
            EditText editText
    ) {

        String value =
                text(
                        editText
                )
                        .replace(
                                ",",
                                "."
                        );


        if (value.isEmpty()) {

            return 0;
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
    // DESTROY
    // ============================================================

    @Override
    protected void onDestroy() {

        if (databaseHelper != null) {

            databaseHelper.close();

            databaseHelper =
                    null;
        }


        super.onDestroy();
    }

}
