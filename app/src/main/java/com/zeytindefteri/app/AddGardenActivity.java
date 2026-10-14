package com.zeytindefteri.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bahçe ekle / ilk bahçe / bahçe düzenle. Üç mod aynı formu kullanır (GardenFormModel.Mode):
 * - "first_garden" = true → ilk kurulum; kayıttan sonra "Bahçen hazır" ekranı.
 * - "garden_id" > 0 → düzenleme; GardenRepository.updateGarden, ekran sahibi değişirse kapanır.
 * - aksi halde yeni bahçe; GardenRepository.addGarden(ForOwner) + bulut eşitleme.
 */
public class AddGardenActivity extends Activity {

    private static final int REQUEST_LOCATION_PICKER = 2001;
    private static final int REQUEST_GARDEN_PHOTO = 2002;
    private static final String PREFS_NAME = "ZeytinDefteriPrefs";
    private static final String KEY_WELCOME_COMPLETED = "welcome_completed";
    private static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";

    private GardenFormModel.Mode mode = GardenFormModel.Mode.ADD;
    private long editGardenId = -1;
    private String screenOwner = "";
    private boolean saving = false;

    private DatabaseHelper databaseHelper;
    private GardenRepository gardenRepository;
    private GardenCloudRepository gardenCloudRepository;

    private EditText etGardenName, etTreeCount, etGardenAge, etVariety, etAreaDecare, etGardenNotes;
    private TextView btnSaveGarden, tvTitle, tvSubtitle, tvSaveSummary;
    private TextView tvLocationTitle, tvLocationSubtitle, tvLocationAction;
    private TextView tvPhotoTitle, tvPhotoStatus, btnRemovePhoto;
    private ImageView ivPhotoThumb, ivPhotoIcon;
    private LinearLayout containerIrrigation;
    private final List<TextView> irrigationChips = new ArrayList<>();
    private List<String> irrigationOptions = new ArrayList<>();
    private int irrigationIndex = 0;

    // Konum: seçiciden gelen adlar ve kimlikler; düzenlemede kayıtlı adlar da tutulur.
    private String province = "", district = "", settlement = "", settlementType = "";
    private int provinceId = 0, districtId = 0, settlementId = 0;
    private String savedProvince = "", savedDistrict = "", savedSettlement = "";

    private String photoUri = "";
    // Düzenleme yüklemesinden gelen sulama değeri (seçenekler kurulana kadar tutulur).
    private String pendingIrrigation = "";

    // İlk bahçe başarı ekranı için kaydedilen değerler
    private long savedGardenId = -1;
    private String savedGardenName = "";
    private String savedSummary = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getActionBar() != null) getActionBar().hide();
        getWindow().setStatusBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().setNavigationBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().getDecorView().setSystemUiVisibility(0);
        setContentView(R.layout.activity_add_garden);

        databaseHelper = new DatabaseHelper(this);
        gardenRepository = new GardenRepository(databaseHelper);
        gardenCloudRepository = new GardenCloudRepository(databaseHelper);

        editGardenId = getIntent().getLongExtra("garden_id", -1);
        if (editGardenId > 0) mode = GardenFormModel.Mode.EDIT;
        else if (getIntent().getBooleanExtra("first_garden", false)) mode = GardenFormModel.Mode.FIRST;

        screenOwner = state == null ? databaseHelper.getActiveOwnerUid() : state.getString("screen_owner", "");
        if (mode == GardenFormModel.Mode.EDIT && !isSameOwner()) { unavailable(); return; }

        findViews();
        String storedIrrigation = "";
        if (mode == GardenFormModel.Mode.EDIT && state == null && !loadGardenForEdit()) return;
        if (mode == GardenFormModel.Mode.EDIT && state == null) storedIrrigation = pendingIrrigation;
        if (state != null) storedIrrigation = restoreState(state);

        irrigationOptions = GardenFormModel.irrigationOptions(storedIrrigation);
        irrigationIndex = GardenFormModel.irrigationIndex(irrigationOptions, storedIrrigation);
        buildIrrigationChips();

        applyMode();
        updateLocationRow();
        updatePhotoRow();
        setupButtons();
        if (state == null) animateEntrance();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mode == GardenFormModel.Mode.EDIT && databaseHelper != null && !isSameOwner()) finish();
    }

    private void findViews() {
        etGardenName = findViewById(R.id.etGardenName);
        etTreeCount = findViewById(R.id.etTreeCount);
        etGardenAge = findViewById(R.id.etGardenAge);
        etVariety = findViewById(R.id.etVariety);
        etAreaDecare = findViewById(R.id.etAreaDecare);
        etGardenNotes = findViewById(R.id.etGardenNotes);
        btnSaveGarden = findViewById(R.id.btnSaveGarden);
        tvTitle = findViewById(R.id.tvTitle);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        tvSaveSummary = findViewById(R.id.tvSaveSummary);
        tvLocationTitle = findViewById(R.id.tvLocationTitle);
        tvLocationSubtitle = findViewById(R.id.tvLocationSubtitle);
        tvLocationAction = findViewById(R.id.tvLocationAction);
        tvPhotoTitle = findViewById(R.id.tvPhotoTitle);
        tvPhotoStatus = findViewById(R.id.tvPhotoStatus);
        btnRemovePhoto = findViewById(R.id.btnRemovePhoto);
        ivPhotoThumb = findViewById(R.id.ivPhotoThumb);
        ivPhotoIcon = findViewById(R.id.ivPhotoIcon);
        containerIrrigation = findViewById(R.id.containerIrrigation);
        ((View) ivPhotoThumb.getParent()).setClipToOutline(true);
    }

    private void applyMode() {
        tvTitle.setText(GardenFormModel.title(mode));
        tvSubtitle.setText(mode == GardenFormModel.Mode.FIRST
                ? "İlk bahçe kurulumu" : GardenFormModel.subtitle(mode));
        View intro = findViewById(R.id.cardFirstGardenIntro);
        intro.setVisibility(mode == GardenFormModel.Mode.FIRST ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.tvFirstGardenIntro)).setText(GardenFormModel.subtitle(GardenFormModel.Mode.FIRST));
        tvSaveSummary.setText(mode == GardenFormModel.Mode.EDIT ? "" : "Bahçe bilgilerin hesabında güvende.");
        tvSaveSummary.setVisibility(mode == GardenFormModel.Mode.EDIT ? View.GONE : View.VISIBLE);
        setSavingState(false);
    }

    private void animateEntrance() {
        View content = findViewById(R.id.mainScroll);
        content.setAlpha(0f);
        content.setTranslationY(dp(12));
        content.animate().alpha(1f).translationY(0f).setDuration(380)
                .setInterpolator(new AccelerateDecelerateInterpolator()).start();
    }

    // ------------------------------------------------------------ düzenleme

    /** Kayıtlı bahçeyi forma doldurur; bulunamazsa ekranı kapatır. */
    private boolean loadGardenForEdit() {
        try (Cursor c = databaseHelper.getGarden(editGardenId)) {
            if (!c.moveToFirst()) { unavailable(); return false; }
            etGardenName.setText(str(c, "name"));
            int trees = c.getInt(c.getColumnIndexOrThrow("tree_count"));
            int age = c.getInt(c.getColumnIndexOrThrow("age"));
            double area = c.getDouble(c.getColumnIndexOrThrow("area_decare"));
            etTreeCount.setText(trees > 0 ? String.valueOf(trees) : "");
            etGardenAge.setText(age > 0 ? String.valueOf(age) : "");
            etAreaDecare.setText(area > 0 ? GradeFormModel.number(area) : "");
            etVariety.setText(str(c, "variety"));
            etGardenNotes.setText(str(c, "notes"));
            province = savedProvince = str(c, "province");
            district = savedDistrict = str(c, "district");
            settlement = savedSettlement = str(c, "neighborhood");
            pendingIrrigation = str(c, "irrigation_type");
            photoUri = str(c, "photo_uri");
        }
        return true;
    }

    private boolean locationUnchanged() {
        return province.equals(savedProvince) && district.equals(savedDistrict) && settlement.equals(savedSettlement);
    }

    // ------------------------------------------------------------ durum saklama

    @Override
    protected void onSaveInstanceState(Bundle s) {
        super.onSaveInstanceState(s);
        s.putString("screen_owner", screenOwner);
        s.putString("province", province); s.putString("district", district);
        s.putString("settlement", settlement); s.putString("settlement_type", settlementType);
        s.putInt("province_id", provinceId); s.putInt("district_id", districtId); s.putInt("settlement_id", settlementId);
        s.putString("saved_province", savedProvince); s.putString("saved_district", savedDistrict);
        s.putString("saved_settlement", savedSettlement);
        s.putString("photo_uri", photoUri);
        s.putString("irrigation", irrigationOptions.isEmpty() ? "" : irrigationOptions.get(irrigationIndex));
    }

    /** Döndürmeden sonra alanları geri yükler; sulama değerini döndürür. Metin alanları Android tarafından korunur. */
    private String restoreState(Bundle s) {
        province = s.getString("province", ""); district = s.getString("district", "");
        settlement = s.getString("settlement", ""); settlementType = s.getString("settlement_type", "");
        provinceId = s.getInt("province_id"); districtId = s.getInt("district_id"); settlementId = s.getInt("settlement_id");
        savedProvince = s.getString("saved_province", ""); savedDistrict = s.getString("saved_district", "");
        savedSettlement = s.getString("saved_settlement", "");
        photoUri = s.getString("photo_uri", "");
        String irrigation = s.getString("irrigation", "");
        return "Belirtilmedi".equals(irrigation) ? "" : irrigation;
    }

    // ------------------------------------------------------------ sulama kutuları

    /** İki sütunlu seçenek kutuları; seçili olan yeşil çerçeveli. */
    private void buildIrrigationChips() {
        containerIrrigation.removeAllViews();
        irrigationChips.clear();
        LinearLayout row = null;
        for (int i = 0; i < irrigationOptions.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                if (i > 0) rp.topMargin = dp(8);
                containerIrrigation.addView(row, rp);
            }
            final int index = i;
            TextView chip = new TextView(this);
            chip.setText(irrigationOptions.get(i));
            chip.setTextSize(13);
            chip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            chip.setTextColor(getColorStateList(R.color.choice_text));
            chip.setGravity(Gravity.CENTER);
            chip.setMaxLines(1);
            chip.setBackgroundResource(R.drawable.bg_choice_chip);
            chip.setOnClickListener(v -> selectIrrigation(index));
            irrigationChips.add(chip);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1);
            if (i % 2 == 1) p.leftMargin = dp(8);
            row.addView(chip, p);
        }
        if (irrigationOptions.size() % 2 == 1) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 1, 1);
            p.leftMargin = dp(8);
            row.addView(new View(this), p);
        }
        selectIrrigation(irrigationIndex);
    }

    private void selectIrrigation(int index) {
        irrigationIndex = Math.max(0, Math.min(irrigationOptions.size() - 1, index));
        for (int i = 0; i < irrigationChips.size(); i++) {
            boolean on = i == irrigationIndex;
            irrigationChips.get(i).setSelected(on);
            irrigationChips.get(i).setContentDescription("Sulama: " + irrigationOptions.get(i) + (on ? ", seçili" : ""));
        }
    }

    // ------------------------------------------------------------ konum

    private void updateLocationRow() {
        tvLocationTitle.setText(GardenFormModel.locationTitle(province, district, settlement));
        String sub = GardenFormModel.locationSubtitle(province, district, settlement);
        tvLocationSubtitle.setText(sub);
        tvLocationSubtitle.setVisibility(sub.isEmpty() ? View.GONE : View.VISIBLE);
        boolean empty = GardenFormModel.blank(province) && GardenFormModel.blank(district) && GardenFormModel.blank(settlement);
        tvLocationAction.setText(empty ? "Seç" : "Değiştir");
        ((TextView) ((LinearLayout) findViewById(R.id.cardLocation)).getChildAt(0))
                .setText(mode == GardenFormModel.Mode.FIRST ? "KONUM *" : "KONUM");
        findViewById(R.id.btnLocation).setContentDescription("Konum: "
                + GardenFormModel.locationTitle(province, district, settlement) + (sub.isEmpty() ? "" : ", " + sub)
                + ". " + (empty ? "Seç" : "Değiştir"));
    }

    private void openLocationPicker() {
        int start = provinceId <= 0 ? LocationPickerActivity.START_PROVINCE
                : districtId <= 0 ? LocationPickerActivity.START_DISTRICT : LocationPickerActivity.START_SETTLEMENT;
        Intent intent = new Intent(this, LocationPickerActivity.class)
                .putExtra(LocationPickerActivity.EXTRA_START_MODE, start)
                .putExtra(LocationPickerActivity.EXTRA_PROVINCE_ID, provinceId)
                .putExtra(LocationPickerActivity.EXTRA_PROVINCE_NAME, province)
                .putExtra(LocationPickerActivity.EXTRA_DISTRICT_ID, districtId)
                .putExtra(LocationPickerActivity.EXTRA_DISTRICT_NAME, district)
                .putExtra(LocationPickerActivity.EXTRA_SETTLEMENT_ID, settlementId)
                .putExtra(LocationPickerActivity.EXTRA_SETTLEMENT_NAME, settlement)
                .putExtra(LocationPickerActivity.EXTRA_SETTLEMENT_TYPE, settlementType);
        startActivityForResult(intent, REQUEST_LOCATION_PICKER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQUEST_LOCATION_PICKER) {
            provinceId = data.getIntExtra(LocationPickerActivity.EXTRA_PROVINCE_ID, 0);
            districtId = data.getIntExtra(LocationPickerActivity.EXTRA_DISTRICT_ID, 0);
            settlementId = data.getIntExtra(LocationPickerActivity.EXTRA_SETTLEMENT_ID, 0);
            province = safe(data.getStringExtra(LocationPickerActivity.EXTRA_PROVINCE_NAME));
            district = safe(data.getStringExtra(LocationPickerActivity.EXTRA_DISTRICT_NAME));
            settlement = safe(data.getStringExtra(LocationPickerActivity.EXTRA_SETTLEMENT_NAME));
            settlementType = safe(data.getStringExtra(LocationPickerActivity.EXTRA_SETTLEMENT_TYPE));
            updateLocationRow();
        } else if (requestCode == REQUEST_GARDEN_PHOTO && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException error) {
                // Kalıcı izin alınamazsa düzenleme ekranındaki eski davranış: başka fotoğraf iste.
                if (mode == GardenFormModel.Mode.EDIT) {
                    Toast.makeText(this, "Kalıcı erişim alınamadı. Başka bir fotoğraf seç.", Toast.LENGTH_LONG).show();
                    return;
                }
            }
            photoUri = uri.toString();
            updatePhotoRow();
        }
    }

    // ------------------------------------------------------------ fotoğraf

    private void openPhotoPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("image/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_GARDEN_PHOTO);
        } catch (Exception e) {
            Toast.makeText(this, "Fotoğraf seçici açılamadı.", Toast.LENGTH_SHORT).show();
        }
    }

    private void updatePhotoRow() {
        boolean has = !photoUri.isEmpty();
        ivPhotoThumb.setVisibility(View.GONE);
        ivPhotoIcon.setVisibility(View.VISIBLE);
        if (has) {
            try {
                ivPhotoThumb.setImageURI(Uri.parse(photoUri));
                if (ivPhotoThumb.getDrawable() != null) {
                    ivPhotoThumb.setVisibility(View.VISIBLE);
                    ivPhotoIcon.setVisibility(View.GONE);
                }
            } catch (Exception ignored) {
                // Fotoğraf okunamazsa ikon kalır; kayıtlı adres değişmez.
            }
        }
        tvPhotoTitle.setText(has ? "Fotoğraf seçildi" : "Fotoğraf ekle");
        tvPhotoStatus.setText(has ? "Değiştirmek için dokun" : "İsteğe bağlı · bahçe kartında görünür");
        btnRemovePhoto.setVisibility(has ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnPhoto).setContentDescription(has ? "Bahçe fotoğrafını değiştir" : "Bahçe fotoğrafı ekle");
    }

    // ------------------------------------------------------------ düğmeler

    private void setupButtons() {
        findViewById(R.id.btnBack).setOnClickListener(v -> { if (!saving) finish(); });
        findViewById(R.id.btnLocation).setOnClickListener(v -> { if (!saving) openLocationPicker(); });
        findViewById(R.id.btnPhoto).setOnClickListener(v -> { if (!saving) openPhotoPicker(); });
        btnRemovePhoto.setOnClickListener(v -> { photoUri = ""; updatePhotoRow(); });
        btnSaveGarden.setOnClickListener(v -> { if (!saving) saveGarden(); });
    }

    // ------------------------------------------------------------ kaydet

    private void saveGarden() {
        if (saving) return;
        if (mode == GardenFormModel.Mode.EDIT && !isSameOwner()) { unavailable(); return; }

        String name = text(etGardenName);
        if (name.isEmpty()) { fieldError(etGardenName, "Bahçe adı gerekli"); return; }

        int treeCount = GardenFormModel.parseCount(text(etTreeCount));
        if (treeCount < 0) { fieldError(etTreeCount, "Geçerli, sıfır veya daha büyük bir sayı yaz"); return; }
        if (GardenFormModel.requiresTreeAndAge(mode) && treeCount <= 0) { fieldError(etTreeCount, "Ağaç sayısını yaz"); return; }

        int age = GardenFormModel.parseCount(text(etGardenAge));
        if (age < 0) { fieldError(etGardenAge, "Geçerli, sıfır veya daha büyük bir sayı yaz"); return; }
        if (GardenFormModel.requiresTreeAndAge(mode) && age <= 0) { fieldError(etGardenAge, "Ağaç yaşını yaz"); return; }

        double area = GardenFormModel.parseArea(text(etAreaDecare));
        if (area < 0) { fieldError(etAreaDecare, "Geçerli, sıfır veya daha büyük bir sayı yaz"); return; }

        switch (GardenFormModel.checkLocation(mode, province, district, settlement,
                provinceId, districtId, settlementId, locationUnchanged())) {
            case PICK_REQUIRED:
                Toast.makeText(this, "İlk bahçenin konumunu seçelim", Toast.LENGTH_SHORT).show();
                openLocationPicker();
                return;
            case PICK_AGAIN:
                Toast.makeText(this, "Konumu listeden yeniden seç.", Toast.LENGTH_SHORT).show();
                openLocationPicker();
                return;
            default:
                break;
        }

        String variety = text(etVariety);
        String irrigation = GardenFormModel.irrigationToStore(irrigationOptions.get(irrigationIndex));
        String notes = text(etGardenNotes);

        setSavingState(true);
        if (mode == GardenFormModel.Mode.EDIT) {
            saveEdit(name, treeCount, age, variety, area, irrigation, notes);
            return;
        }

        long result;
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && user.getUid() != null && !user.getUid().trim().isEmpty()) {
                result = gardenRepository.addGardenForOwner(user.getUid().trim(), UUID.randomUUID().toString(),
                        name, treeCount, age, variety, area, province, district, settlement, irrigation, notes, photoUri);
            } else {
                result = gardenRepository.addGarden(name, treeCount, age, variety, area,
                        province, district, settlement, irrigation, notes, photoUri);
            }
        } catch (Exception e) {
            result = -1;
        }
        if (result == -1) {
            setSavingState(false);
            Toast.makeText(this, "Bahçe kaydedilemedi.", Toast.LENGTH_SHORT).show();
            return;
        }

        savedGardenId = result;
        savedGardenName = name;
        savedSummary = GardenFormModel.summary(treeCount, age, variety, province, district, settlement);
        FirebaseUser syncUser = FirebaseAuth.getInstance().getCurrentUser();
        if (syncUser != null && syncUser.getUid() != null && !syncUser.getUid().trim().isEmpty()) {
            gardenCloudRepository.syncGardenByLocalId(syncUser.getUid().trim(), savedGardenId);
        }

        if (mode == GardenFormModel.Mode.FIRST) {
            markFirstSetupCompleted();
            showFirstGardenSuccess();
            return;
        }
        Toast.makeText(this, "Bahçe kaydedildi", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    /** Düzenleme: yalnızca yerel güncelleme; bulut kimliği değişmez, ana ekranın eşitlemesi sonra yükler. */
    private void saveEdit(String name, int trees, int age, String variety, double area, String irrigation, String notes) {
        try {
            boolean updated = gardenRepository.updateGarden(editGardenId, name, trees, age, variety, area,
                    province, district, settlement, irrigation, notes, photoUri) == 1;
            if (!updated) {
                setSavingState(false);
                Toast.makeText(this, "Bahçe güncellenemedi. Bahçeyi ve oturumu kontrol et.", Toast.LENGTH_LONG).show();
                return;
            }
        } catch (android.database.SQLException error) {
            setSavingState(false);
            Toast.makeText(this, "Değişiklikler kaydedilemedi. Tekrar dene.", Toast.LENGTH_LONG).show();
            return;
        }
        setResult(RESULT_OK);
        Toast.makeText(this, "Bahçe güncellendi", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void setSavingState(boolean value) {
        saving = value;
        btnSaveGarden.setEnabled(!value);
        btnSaveGarden.setAlpha(value ? 0.78f : 1f);
        btnSaveGarden.setText(value ? "Kaydediliyor…" : GardenFormModel.saveLabel(mode));
    }

    private void markFirstSetupCompleted() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        preferences.edit().putBoolean(KEY_WELCOME_COMPLETED, true).putBoolean(KEY_ONBOARDING_COMPLETED, true).apply();
    }

    // ------------------------------------------------------------ ilk bahçe başarı ekranı

    private void showFirstGardenSuccess() {
        saving = false;
        int text = GreenTheme.color(R.color.zd_text), muted = GreenTheme.color(R.color.zd_muted);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(GreenTheme.color(R.color.zd_bg));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(20), dp(56), dp(20), dp(28));

        FrameLayout disc = new FrameLayout(this);
        disc.setBackgroundResource(R.drawable.bg_icon_disc);
        ImageView check = new ImageView(this);
        check.setImageResource(R.drawable.ic_op_check);
        check.setColorFilter(GreenTheme.color(R.color.zd_green));
        check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        disc.addView(check, new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER));
        content.addView(disc, new LinearLayout.LayoutParams(dp(80), dp(80)));

        TextView title = label("Bahçen hazır", 26, text, true);
        title.setPadding(0, dp(20), 0, 0);
        content.addView(title);
        TextView sub = label("Artık kaydettiğin bahçenle tüm sezonunu yönetebilirsin.", 14, muted, false);
        sub.setPadding(0, dp(8), 0, dp(24));
        content.addView(sub);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        ImageView tree = new ImageView(this);
        tree.setImageResource(R.drawable.ic_op_tree);
        tree.setColorFilter(GreenTheme.color(R.color.zd_green));
        tree.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(tree, new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView gardenName = label(savedGardenName, 20, text, true);
        gardenName.setPadding(0, dp(10), 0, 0);
        card.addView(gardenName);
        if (!savedSummary.isEmpty()) {
            TextView summary = label(savedSummary, 13, muted, false);
            summary.setPadding(0, dp(6), 0, 0);
            card.addView(summary);
        }
        TextView message = label("Şimdi ilk sezon kaydını oluşturmaya hazırsın.", 12, muted, false);
        message.setPadding(0, dp(12), 0, 0);
        card.addView(message);
        content.addView(card, new LinearLayout.LayoutParams(-1, -2));

        TextView go = label("Bahçeme git", 16, GreenTheme.color(R.color.zd_bg), true);
        go.setBackgroundResource(R.drawable.bg_green_button);
        go.setOnClickListener(v -> openMainAfterFirstSetup());
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, dp(54));
        gp.topMargin = dp(24);
        content.addView(go, gp);

        TextView another = label("Başka bahçe ekle", 15, GreenTheme.color(R.color.zd_green), true);
        another.setBackgroundResource(R.drawable.bg_search);
        another.setOnClickListener(v -> restartAsNormalGarden());
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(50));
        ap.topMargin = dp(10);
        content.addView(another, ap);

        scroll.addView(content);
        setContentView(scroll);
        content.setAlpha(0f);
        content.setTranslationY(dp(12));
        content.animate().alpha(1f).translationY(0f).setDuration(420)
                .setInterpolator(new AccelerateDecelerateInterpolator()).start();
    }

    private void openMainAfterFirstSetup() {
        Intent intent = new Intent(this, MainActivity.class)
                .putExtra("first_setup_completed", true)
                .putExtra("first_garden_id", savedGardenId)
                .putExtra("first_garden_name", savedGardenName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void restartAsNormalGarden() {
        startActivity(new Intent(this, AddGardenActivity.class).putExtra("first_garden", false));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    // ------------------------------------------------------------ yardımcılar

    private boolean isSameOwner() {
        if (databaseHelper == null) return false;
        String active = databaseHelper.getActiveOwnerUid();
        return (screenOwner == null ? "" : screenOwner).equals(active == null ? "" : active);
    }

    private void unavailable() {
        Toast.makeText(this, "Bahçe bulunamadı veya oturum değişti", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void fieldError(EditText field, String message) {
        field.setError(message);
        field.requestFocus();
    }

    private TextView label(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private static String str(Cursor c, String column) {
        int i = c.getColumnIndex(column);
        String v = i < 0 || c.isNull(i) ? "" : c.getString(i);
        return v == null ? "" : v.trim();
    }

    private String text(EditText editText) {
        return editText == null ? "" : editText.getText().toString().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (databaseHelper != null) {
            databaseHelper.close();
            databaseHelper = null;
        }
        super.onDestroy();
    }
}
