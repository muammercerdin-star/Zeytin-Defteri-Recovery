package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;

import android.database.Cursor;

import android.net.Uri;

import android.os.Bundle;

import android.view.View;

import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class CreatePostActivity extends Activity {


    // ============================================================
    // DATABASE
    // ============================================================

    private DatabaseHelper databaseHelper;


    // ============================================================
    // SESSION
    // ============================================================

    private SessionManager sessionManager;


    // ============================================================
    // FIREBASE
    // ============================================================

    private FirebaseAuth firebaseAuth;

    private SocialCloudRepository socialCloudRepository;


    // ============================================================
    // VIEW'LAR
    // ============================================================

    private TextView btnBack;

    private TextView tvUserName;

    private TextView tvUserRegion;

    private Spinner spinnerGarden;

    private Spinner spinnerPostType;

    private EditText etPostContent;

    private LinearLayout btnAddPhoto;

    private ImageView imgPostPreview;

    private TextView btnPublish;


    // ============================================================
    // MEDYA
    // ============================================================

    private static final int REQUEST_PICK_IMAGES =
            1001;


    private static final int REQUEST_PICK_VIDEO =
            1002;


    private static final int MAX_MEDIA_COUNT =
            6;


    private static final int MAX_VIDEO_COUNT =
            1;


    // ============================================================
    // SEÇİLEN MEDYALAR
    // ============================================================

    private final ArrayList<SelectedMedia> selectedMediaList =
            new ArrayList<>();


    // ============================================================
    // BAHÇE VERİLERİ
    // ============================================================

    private final ArrayList<Long> gardenIds =
            new ArrayList<>();


    private final ArrayList<String> gardenNames =
            new ArrayList<>();


    private final ArrayList<String> gardenVarieties =
            new ArrayList<>();


    private final ArrayList<String> gardenProvinces =
            new ArrayList<>();


    private final ArrayList<String> gardenDistricts =
            new ArrayList<>();


    private final ArrayList<String> gardenNeighborhoods =
            new ArrayList<>();


    // ============================================================
    // PAYLAŞIM TÜRLERİ
    // ============================================================

    private final List<String> postTypeLabels =
            Arrays.asList(
                    "Genel",
                    "Soru",
                    "Bahçem",
                    "Zararlı / Hastalık",
                    "Sulama",
                    "Gübreleme",
                    "İlaçlama",
                    "Hasat",
                    "Piyasa",
                    "Alım-Satım"
            );


    // ============================================================
    // MEDYA MODELİ
    // ============================================================

    private static class SelectedMedia {


        String type;

        Uri uri;


        SelectedMedia(
                String type,
                Uri uri
        ) {


            this.type =
                    type;


            this.uri =
                    uri;
        }
    }


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
        // XML
        // ========================================================

        setContentView(
                R.layout.activity_create_post
        );

        // Meydan ekranindan gelen hizli taslaklar daha sonra view baglantilari
        // kuruldugunda uygulanmak uzere Intent icinde korunur.


        // ========================================================
        // DATABASE
        // ========================================================

        databaseHelper =
                new DatabaseHelper(
                        this
                );


        // ========================================================
        // SESSION
        // ========================================================

        sessionManager =
                new SessionManager(
                        this
                );


        // ========================================================
        // FIREBASE
        // ========================================================

        try {


            firebaseAuth =
                    FirebaseAuth.getInstance();

            socialCloudRepository =
                    new SocialCloudRepository(this, databaseHelper);


        } catch (Exception e) {


            firebaseAuth =
                    null;
        }


        // ========================================================
        // VIEW
        // ========================================================

        findViews();


        // ========================================================
        // KULLANICI
        // ========================================================

        loadUserInfo();


        // ========================================================
        // BAHÇELER
        // ========================================================

        loadGardens();


        // ========================================================
        // PAYLAŞIM TİPLERİ
        // ========================================================

        setupPostTypes();

        applyMeydanDraft();


        // ========================================================
        // BUTONLAR
        // ========================================================

        setupButtons();


        // ========================================================
        // ÖNİZLEME
        // ========================================================

        updateMediaPreview();
    }



    // ============================================================
    // MEYDAN HIZLI TASLAK
    // ============================================================

    private void applyMeydanDraft() {

        if (getIntent() == null) {
            return;
        }

        String draft = getIntent().getStringExtra("draft_content");
        if (etPostContent != null && draft != null && !draft.trim().isEmpty()) {
            etPostContent.setText(draft.trim());
            etPostContent.setSelection(etPostContent.getText().length());
        }

        String type = getIntent().getStringExtra("draft_type");
        if (spinnerPostType == null || type == null) {
            return;
        }

        int position = 0;
        switch (type.toUpperCase(java.util.Locale.ROOT)) {
            case "PEST": position = 3; break;
            case "IRRIGATION": position = 4; break;
            case "HARVEST": position = 7; break;
            case "MARKET": position = 8; break;
            case "SALE": position = 9; break;
            default: position = 0; break;
        }

        if (position >= 0 && position < postTypeLabels.size()) {
            spinnerPostType.setSelection(position);
        }
    }

    // ============================================================
    // VIEW'LARI BUL
    // ============================================================

    private void findViews() {


        btnBack =
                findViewById(
                        R.id.btnBack
                );


        tvUserName =
                findViewById(
                        R.id.tvUserName
                );


        tvUserRegion =
                findViewById(
                        R.id.tvUserRegion
                );


        spinnerGarden =
                findViewById(
                        R.id.spinnerGarden
                );


        spinnerPostType =
                findViewById(
                        R.id.spinnerPostType
                );


        etPostContent =
                findViewById(
                        R.id.etPostContent
                );


        btnAddPhoto =
                findViewById(
                        R.id.btnAddPhoto
                );


        imgPostPreview =
                findViewById(
                        R.id.imgPostPreview
                );


        btnPublish =
                findViewById(
                        R.id.btnPublish
                );
    }


    // ============================================================
    // KULLANICI BİLGİSİ
    // ============================================================

    private void loadUserInfo() {


        String userName =
                sessionManager.getUserName();


        String userEmail =
                sessionManager.getUserEmail();


        // ========================================================
        // FIREBASE
        // ========================================================

        if (firebaseAuth != null) {


            FirebaseUser firebaseUser =
                    firebaseAuth.getCurrentUser();


            if (firebaseUser != null) {


                // =================================================
                // İSİM
                // =================================================

                String firebaseName =
                        firebaseUser.getDisplayName();


                if (firebaseName != null
                        &&
                        !firebaseName.trim().isEmpty()) {


                    userName =
                            firebaseName.trim();
                }


                // =================================================
                // E-POSTA
                // =================================================

                String firebaseEmail =
                        firebaseUser.getEmail();


                if (firebaseEmail != null
                        &&
                        !firebaseEmail.trim().isEmpty()) {


                    userEmail =
                            firebaseEmail.trim();
                }
            }
        }


        // ========================================================
        // İSİM YOKSA
        // ========================================================

        if (userName == null
                ||
                userName.trim().isEmpty()) {


            userName =
                    "Üretici";
        }


        if (tvUserName != null) {


            tvUserName.setText(
                    userName
            );
        }


        // ========================================================
        // ALT BİLGİ
        // ========================================================

        if (tvUserRegion != null) {


            if (userEmail == null
                    ||
                    userEmail.trim().isEmpty()) {


                tvUserRegion.setText(
                        "Zeytin Defteri üreticisi"
                );


            } else {


                tvUserRegion.setText(
                        userEmail
                );
            }
        }
    }


    // ============================================================
    // BAHÇELERİ YÜKLE
    // ============================================================

    private void loadGardens() {


        gardenIds.clear();

        gardenNames.clear();

        gardenVarieties.clear();

        gardenProvinces.clear();

        gardenDistricts.clear();

        gardenNeighborhoods.clear();


        Cursor cursor =
                databaseHelper.getGardens();


        try {


            while (cursor.moveToNext()) {


                long id =
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                        "id"
                                )
                        );


                String name =
                        getCursorString(
                                cursor,
                                "name"
                        );


                String variety =
                        getCursorString(
                                cursor,
                                "variety"
                        );


                String province =
                        getCursorString(
                                cursor,
                                "province"
                        );


                String district =
                        getCursorString(
                                cursor,
                                "district"
                        );


                String neighborhood =
                        getCursorString(
                                cursor,
                                "neighborhood"
                        );


                gardenIds.add(
                        id
                );


                gardenNames.add(
                        name
                );


                gardenVarieties.add(
                        variety
                );


                gardenProvinces.add(
                        province
                );


                gardenDistricts.add(
                        district
                );


                gardenNeighborhoods.add(
                        neighborhood
                );
            }


        } finally {


            cursor.close();
        }


        // ========================================================
        // BAHÇE YOK
        // ========================================================

        if (gardenNames.isEmpty()) {


            gardenIds.add(
                    0L
            );


            gardenNames.add(
                    "Bahçe seçilmedi"
            );


            gardenVarieties.add(
                    ""
            );


            gardenProvinces.add(
                    ""
            );


            gardenDistricts.add(
                    ""
            );


            gardenNeighborhoods.add(
                    ""
            );
        }


        // ========================================================
        // ADAPTER
        // ========================================================

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        gardenNames
                );


        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );


        spinnerGarden.setAdapter(
                adapter
        );
    }


    // ============================================================
    // PAYLAŞIM TİPLERİ
    // ============================================================

    private void setupPostTypes() {


        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        postTypeLabels
                );


        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );


        spinnerPostType.setAdapter(
                adapter
        );
    }


    // ============================================================
    // BUTONLAR
    // ============================================================

    private void setupButtons() {


        // ========================================================
        // GERİ
        // ========================================================

        if (btnBack != null) {


            btnBack.setOnClickListener(
                    view -> finish()
            );
        }


        // ========================================================
        // MEDYA EKLE
        // ========================================================

        if (btnAddPhoto != null) {


            btnAddPhoto.setOnClickListener(
                    view -> showMediaPickerDialog()
            );
        }


        // ========================================================
        // ÖNİZLEMEYE BAS
        // ========================================================

        if (imgPostPreview != null) {


            imgPostPreview.setOnClickListener(
                    view -> showSelectedMediaOptions()
            );
        }


        // ========================================================
        // PAYLAŞ
        // ========================================================

        if (btnPublish != null) {


            btnPublish.setOnClickListener(
                    view -> publishPost()
            );
        }
    }


    // ============================================================
    // MEDYA SEÇİM MENÜSÜ
    // ============================================================

    private void showMediaPickerDialog() {


        String[] options =
                new String[]{

                        "Fotoğraf ekle",

                        "Video ekle",

                        "Seçilen medyaları temizle"
                };


        new AlertDialog.Builder(
                this
        )
                .setTitle(
                        "Gönderiye medya ekle"
                )

                .setItems(
                        options,

                        (dialog, which) -> {


                            if (which == 0) {


                                openImagePicker();


                            } else if (which == 1) {


                                openVideoPicker();


                            } else if (which == 2) {


                                clearSelectedMedia();
                            }
                        }
                )

                .setNegativeButton(
                        "Vazgeç",
                        null
                )

                .show();
    }


    // ============================================================
    // FOTOĞRAF SEÇ
    // ============================================================

    private void openImagePicker() {


        if (selectedMediaList.size()
                >= MAX_MEDIA_COUNT) {


            Toast.makeText(
                    this,
                    "En fazla "
                            + MAX_MEDIA_COUNT
                            + " medya ekleyebilirsin.",
                    Toast.LENGTH_SHORT
            ).show();


            return;
        }


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


        // ========================================================
        // ÇOKLU SEÇİM
        // ========================================================

        intent.putExtra(
                Intent.EXTRA_ALLOW_MULTIPLE,
                true
        );


        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        );


        intent.addFlags(
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );


        try {


            startActivityForResult(
                    intent,
                    REQUEST_PICK_IMAGES
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
    // VİDEO SEÇ
    // ============================================================

    private void openVideoPicker() {


        if (getSelectedVideoCount()
                >= MAX_VIDEO_COUNT) {


            Toast.makeText(
                    this,
                    "Bir gönderiye en fazla 1 video ekleyebilirsin.",
                    Toast.LENGTH_SHORT
            ).show();


            return;
        }


        if (selectedMediaList.size()
                >= MAX_MEDIA_COUNT) {


            Toast.makeText(
                    this,
                    "Medya sınırına ulaştın.",
                    Toast.LENGTH_SHORT
            ).show();


            return;
        }


        Intent intent =
                new Intent(
                        Intent.ACTION_OPEN_DOCUMENT
                );


        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );


        intent.setType(
                "video/*"
        );


        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        );


        intent.addFlags(
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );


        try {


            startActivityForResult(
                    intent,
                    REQUEST_PICK_VIDEO
            );


        } catch (Exception e) {


            Toast.makeText(
                    this,
                    "Video seçici açılamadı.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }


    // ============================================================
    // MEDYA SEÇİM SONUCU
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


        if (resultCode != RESULT_OK) {


            return;
        }


        if (data == null) {


            return;
        }


        // ========================================================
        // FOTOĞRAFLAR
        // ========================================================

        if (requestCode == REQUEST_PICK_IMAGES) {


            handleSelectedImages(
                    data
            );


            return;
        }


        // ========================================================
        // VİDEO
        // ========================================================

        if (requestCode == REQUEST_PICK_VIDEO) {


            handleSelectedVideo(
                    data
            );
        }
    }


    // ============================================================
    // FOTOĞRAFLARI İŞLE
    // ============================================================

    private void handleSelectedImages(
            Intent data
    ) {


        int added =
                0;


        // ========================================================
        // ÇOKLU FOTOĞRAF
        // ========================================================

        ClipData clipData =
                data.getClipData();


        if (clipData != null) {


            for (
                    int i = 0;
                    i < clipData.getItemCount();
                    i++
            ) {


                if (selectedMediaList.size()
                        >= MAX_MEDIA_COUNT) {


                    break;
                }


                Uri uri =
                        clipData
                                .getItemAt(
                                        i
                                )
                                .getUri();


                if (uri == null) {


                    continue;
                }


                if (containsMediaUri(
                        uri
                )) {


                    continue;
                }


                persistUriPermission(
                        uri,
                        data
                );


                selectedMediaList.add(
                        new SelectedMedia(
                                "IMAGE",
                                uri
                        )
                );


                added++;
            }


        } else {


            // ====================================================
            // TEK FOTOĞRAF
            // ====================================================

            Uri uri =
                    data.getData();


            if (uri != null
                    &&
                    selectedMediaList.size()
                            < MAX_MEDIA_COUNT
                    &&
                    !containsMediaUri(
                            uri
                    )) {


                persistUriPermission(
                        uri,
                        data
                );


                selectedMediaList.add(
                        new SelectedMedia(
                                "IMAGE",
                                uri
                        )
                );


                added++;
            }
        }


        updateMediaPreview();


        if (added > 0) {


            Toast.makeText(
                    this,
                    added
                            + " fotoğraf eklendi",
                    Toast.LENGTH_SHORT
            ).show();


        } else {


            Toast.makeText(
                    this,
                    "Yeni fotoğraf eklenmedi.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }


    // ============================================================
    // VİDEOYU İŞLE
    // ============================================================

    private void handleSelectedVideo(
            Intent data
    ) {


        Uri uri =
                data.getData();


        if (uri == null) {


            Toast.makeText(
                    this,
                    "Video seçilemedi.",
                    Toast.LENGTH_SHORT
            ).show();


            return;
        }


        if (getSelectedVideoCount()
                >= MAX_VIDEO_COUNT) {


            Toast.makeText(
                    this,
                    "Zaten bir video ekledin.",
                    Toast.LENGTH_SHORT
            ).show();


            return;
        }


        if (containsMediaUri(
                uri
        )) {


            Toast.makeText(
                    this,
                    "Bu medya zaten ekli.",
                    Toast.LENGTH_SHORT
            ).show();


            return;
        }


        persistUriPermission(
                uri,
                data
        );


        selectedMediaList.add(
                new SelectedMedia(
                        "VIDEO",
                        uri
                )
        );


        updateMediaPreview();


        Toast.makeText(
                this,
                "Video eklendi",
                Toast.LENGTH_SHORT
        ).show();
    }


    // ============================================================
    // URI İZNİ
    // ============================================================

    private void persistUriPermission(
            Uri uri,
            Intent data
    ) {


        if (uri == null) {


            return;
        }


        try {


            int takeFlags =
                    data.getFlags()
                            &
                            (
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            |
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            );


            if ((takeFlags
                    &
                    Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    == 0) {


                takeFlags |=
                        Intent.FLAG_GRANT_READ_URI_PERMISSION;
            }


            getContentResolver()
                    .takePersistableUriPermission(
                            uri,
                            takeFlags
                    );


        } catch (Exception ignored) {


            // Bazı galeri uygulamaları persistable izin vermeyebilir.
        }
    }


    // ============================================================
    // ÖNİZLEME
    // ============================================================

    private void updateMediaPreview() {


        if (imgPostPreview == null) {


            return;
        }


        if (selectedMediaList.isEmpty()) {


            imgPostPreview.setImageDrawable(
                    null
            );


            imgPostPreview.setVisibility(
                    View.GONE
            );


            return;
        }


        SelectedMedia previewMedia =
                findFirstImage();


        if (previewMedia != null) {


            try {


                imgPostPreview.setImageURI(
                        previewMedia.uri
                );


                imgPostPreview.setVisibility(
                        View.VISIBLE
                );


            } catch (Exception e) {


                imgPostPreview.setVisibility(
                        View.GONE
                );
            }


        } else {


            // ====================================================
            // SADECE VİDEO VAR
            // ====================================================

            imgPostPreview.setImageResource(
                    android.R.drawable.ic_media_play
            );


            imgPostPreview.setVisibility(
                    View.VISIBLE
            );
        }


        updateMediaButtonText();
    }


    // ============================================================
    // MEDYA BUTONU YAZISI
    // ============================================================

    private void updateMediaButtonText() {


        if (btnAddPhoto == null) {


            return;
        }


        TextView title =
                null;


        // ========================================================
        // LinearLayout içindeki ilk TextView'ı değiştirmeye
        // çalışmıyoruz.
        //
        // XML yapısı farklı olabilir.
        // Bu yüzden yalnızca contentDescription güncelliyoruz.
        // ========================================================

        int imageCount =
                getSelectedImageCount();


        int videoCount =
                getSelectedVideoCount();


        String description =
                imageCount
                        + " fotoğraf, "
                        + videoCount
                        + " video seçildi";


        btnAddPhoto.setContentDescription(
                description
        );
    }


    // ============================================================
    // SEÇİLEN MEDYA MENÜSÜ
    // ============================================================

    private void showSelectedMediaOptions() {


        if (selectedMediaList.isEmpty()) {


            return;
        }


        String[] options =
                new String[]{

                        "Yeni medya ekle",

                        "Son medyayı kaldır",

                        "Tüm medyaları kaldır"
                };


        new AlertDialog.Builder(
                this
        )
                .setTitle(
                        selectedMediaList.size()
                                + " medya seçildi"
                )

                .setItems(
                        options,

                        (dialog, which) -> {


                            if (which == 0) {


                                showMediaPickerDialog();


                            } else if (which == 1) {


                                removeLastSelectedMedia();


                            } else {


                                clearSelectedMedia();
                            }
                        }
                )

                .setNegativeButton(
                        "Kapat",
                        null
                )

                .show();
    }


    // ============================================================
    // SON MEDYAYI KALDIR
    // ============================================================

    private void removeLastSelectedMedia() {


        if (selectedMediaList.isEmpty()) {


            return;
        }


        selectedMediaList.remove(
                selectedMediaList.size()
                        - 1
        );


        updateMediaPreview();


        Toast.makeText(
                this,
                "Medya kaldırıldı.",
                Toast.LENGTH_SHORT
        ).show();
    }


    // ============================================================
    // TÜM MEDYALARI TEMİZLE
    // ============================================================

    private void clearSelectedMedia() {


        selectedMediaList.clear();


        updateMediaPreview();


        Toast.makeText(
                this,
                "Seçilen medyalar temizlendi.",
                Toast.LENGTH_SHORT
        ).show();
    }


    // ============================================================
    // PAYLAŞIMI KAYDET
    // ============================================================

    private void publishPost() {


        // ========================================================
        // İÇERİK
        // ========================================================

        String content =
                etPostContent
                        .getText()
                        .toString()
                        .trim();


        // ========================================================
        // İÇERİK YOK
        //
        // Meydan'a yalnızca metin yüklenir (fotoğraf/video telefonda kalır).
        // Yazısız gönderi diğer üreticilere boş görünürdü; firestore.rules
        // de boş metni reddeder. Bu yüzden medya olsa da metin gerekir.
        // ========================================================

        if (content.isEmpty()) {


            etPostContent.setError(
                    selectedMediaList.isEmpty()
                            ? "Bir şey yaz"
                            : "Fotoğraflar Meydan'da paylaşılmıyor; gönderine birkaç kelime ekle"
            );


            etPostContent.requestFocus();


            return;
        }


        // ========================================================
        // ÇOK KISA METİN
        // ========================================================

        if (!content.isEmpty()
                &&
                content.length() < 3) {


            etPostContent.setError(
                    "Paylaşım biraz daha açıklayıcı olmalı"
            );


            etPostContent.requestFocus();


            return;
        }


        // Düzen 5.000 karakterle sınırlar; yapıştırma gibi yollara karşı kuralla aynı sınır.
        if (!MeydanPostPolicy.isPublishableContent(content)) {

            etPostContent.setError(
                    "Paylaşım en fazla " + MeydanPostPolicy.MAX_CONTENT + " karakter olabilir"
            );

            etPostContent.requestFocus();

            return;
        }


        // ========================================================
        // BAHÇE
        // ========================================================

        int gardenPosition =
                spinnerGarden
                        .getSelectedItemPosition();


        Long gardenId =
                null;


        String gardenName =
                "";


        String variety =
                "";


        String province =
                "";


        String district =
                "";


        String neighborhood =
                "";


        if (gardenPosition >= 0
                &&
                gardenPosition < gardenIds.size()) {


            long selectedGardenId =
                    gardenIds.get(
                            gardenPosition
                    );


            if (selectedGardenId > 0) {


                gardenId =
                        selectedGardenId;
            }


            gardenName =
                    gardenNames.get(
                            gardenPosition
                    );


            variety =
                    gardenVarieties.get(
                            gardenPosition
                    );


            province =
                    gardenProvinces.get(
                            gardenPosition
                    );


            district =
                    gardenDistricts.get(
                            gardenPosition
                    );


            neighborhood =
                    gardenNeighborhoods.get(
                            gardenPosition
                    );
        }


        // ========================================================
        // POST TYPE
        // ========================================================

        int postTypePosition =
                spinnerPostType
                        .getSelectedItemPosition();


        String postType =
                getPostTypeCode(
                        postTypePosition
                );


        // ========================================================
        // KULLANICI
        // ========================================================

        String userName =
                tvUserName == null
                        ? "Üretici"
                        : tvUserName
                        .getText()
                        .toString()
                        .trim();


        if (userName.isEmpty()) {


            userName =
                    "Üretici";
        }


        String userId =
                "";


        if (firebaseAuth != null) {


            try {


                FirebaseUser firebaseUser =
                        firebaseAuth.getCurrentUser();


                if (firebaseUser != null
                        &&
                        firebaseUser.getUid() != null) {


                    userId =
                            firebaseUser
                                    .getUid()
                                    .trim();
                }


            } catch (Exception ignored) {


            }
        }


        // ========================================================
        // GERİYE DÖNÜK UYUMLULUK
        //
        // İlk FOTOĞRAF posts.photo_uri alanına da yazılır.
        // ========================================================

        String legacyPhotoUri =
                getFirstImageUri();


        // ========================================================
        // BUTON KİLİT
        // ========================================================

        setPublishingState(
                true
        );


        // ========================================================
        // POSTS TABLOSUNA KAYDET
        // ========================================================

        long postId;


        try {


            postId =
                    databaseHelper.addPost(
                            userId,
                            userName,
                            province,
                            district,
                            neighborhood,
                            gardenId,
                            gardenName,
                            variety,
                            postType,
                            content,
                            legacyPhotoUri
                    );


        } catch (Exception e) {


            e.printStackTrace();


            postId =
                    -1;
        }


        // ========================================================
        // POST OLUŞMADI
        // ========================================================

        if (postId == -1) {


            setPublishingState(
                    false
            );


            Toast.makeText(
                    this,
                    "Paylaşım kaydedilemedi.",
                    Toast.LENGTH_LONG
            ).show();


            return;
        }


        // ========================================================
        // MEDYALARI post_media TABLOSUNA KAYDET
        // ========================================================

        int mediaSuccessCount =
                savePostMedia(
                        postId
                );


        // ========================================================
        // FIRESTORE + FIREBASE STORAGE
        // Yereldeki kayıt anında korunur; bulut başarılı olduğunda
        // paylaşım uygulama silinse bile tekrar senkronlanabilir.
        // ========================================================

        final long publishedPostId = postId;
        final boolean mediaLocalOk = selectedMediaList.isEmpty() || mediaSuccessCount > 0;

        if (socialCloudRepository == null) {
            setPublishingState(false);
            Toast.makeText(this,
                    "Paylaşım telefona kaydedildi; bulut bağlantısı kurulamadı.",
                    Toast.LENGTH_LONG).show();
            finishWithPostResult(publishedPostId);
            return;
        }

        socialCloudRepository.publishLocalPost(
                publishedPostId,
                success -> runOnUiThread(() -> {
                    setPublishingState(false);
                    if (success) {
                        Toast.makeText(this,
                                mediaLocalOk
                                        ? "Paylaşım buluta kaydedildi"
                                        : "Paylaşım buluta kaydedildi; bazı medya dosyaları atlandı.",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this,
                                "Paylaşım telefonda duruyor ancak buluta gönderilemedi. İnternet gelince tekrar deneyebiliriz.",
                                Toast.LENGTH_LONG).show();
                    }
                    finishWithPostResult(publishedPostId);
                })
        );
    }

    private void finishWithPostResult(long postId) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("post_id", postId);
        setResult(RESULT_OK, resultIntent);
        finish();
    }


    // ============================================================
    // POST MEDYALARINI KAYDET
    // ============================================================

    private int savePostMedia(
            long postId
    ) {


        if (postId <= 0) {


            return 0;
        }


        int successCount =
                0;


        for (
                int i = 0;
                i < selectedMediaList.size();
                i++
        ) {


            SelectedMedia media =
                    selectedMediaList.get(
                            i
                    );


            if (media == null
                    ||
                    media.uri == null) {


                continue;
            }


            try {


                long result =
                        databaseHelper.addPostMedia(
                                postId,
                                media.type,
                                media.uri.toString(),
                                i
                        );


                if (result != -1) {


                    successCount++;
                }


            } catch (Exception e) {


                e.printStackTrace();
            }
        }


        return successCount;
    }


    // ============================================================
    // PAYLAŞIM DURUMU
    // ============================================================

    private void setPublishingState(
            boolean publishing
    ) {


        if (btnPublish == null) {


            return;
        }


        btnPublish.setEnabled(
                !publishing
        );


        btnPublish.setText(
                publishing
                        ? "Paylaşılıyor..."
                        : "Paylaş   →"
        );


        if (btnAddPhoto != null) {


            btnAddPhoto.setEnabled(
                    !publishing
            );
        }


        if (spinnerGarden != null) {


            spinnerGarden.setEnabled(
                    !publishing
            );
        }


        if (spinnerPostType != null) {


            spinnerPostType.setEnabled(
                    !publishing
            );
        }


        if (etPostContent != null) {


            etPostContent.setEnabled(
                    !publishing
            );
        }
    }


    // ============================================================
    // İLK FOTOĞRAF
    // ============================================================

    private SelectedMedia findFirstImage() {


        for (
                SelectedMedia media
                :
                selectedMediaList
        ) {


            if (media != null
                    &&
                    "IMAGE".equals(
                            media.type
                    )
                    &&
                    media.uri != null) {


                return media;
            }
        }


        return null;
    }


    // ============================================================
    // İLK FOTOĞRAF URI
    // ============================================================

    private String getFirstImageUri() {


        SelectedMedia media =
                findFirstImage();


        if (media == null
                ||
                media.uri == null) {


            return "";
        }


        return media.uri.toString();
    }


    // ============================================================
    // FOTOĞRAF SAYISI
    // ============================================================

    private int getSelectedImageCount() {


        int count =
                0;


        for (
                SelectedMedia media
                :
                selectedMediaList
        ) {


            if (media != null
                    &&
                    "IMAGE".equals(
                            media.type
                    )) {


                count++;
            }
        }


        return count;
    }


    // ============================================================
    // VİDEO SAYISI
    // ============================================================

    private int getSelectedVideoCount() {


        int count =
                0;


        for (
                SelectedMedia media
                :
                selectedMediaList
        ) {


            if (media != null
                    &&
                    "VIDEO".equals(
                            media.type
                    )) {


                count++;
            }
        }


        return count;
    }


    // ============================================================
    // AYNI MEDYA VAR MI?
    // ============================================================

    private boolean containsMediaUri(
            Uri uri
    ) {


        if (uri == null) {


            return false;
        }


        String target =
                uri.toString();


        for (
                SelectedMedia media
                :
                selectedMediaList
        ) {


            if (media != null
                    &&
                    media.uri != null
                    &&
                    target.equals(
                            media.uri.toString()
                    )) {


                return true;
            }
        }


        return false;
    }


    // ============================================================
    // PAYLAŞIM TÜRÜ KODU
    // ============================================================

    private String getPostTypeCode(
            int position
    ) {


        switch (position) {


            case 1:


                return "QUESTION";


            case 2:


                return "GARDEN";


            case 3:


                return "PEST";


            case 4:


                return "IRRIGATION";


            case 5:


                return "FERTILIZER";


            case 6:


                return "SPRAY";


            case 7:


                return "HARVEST";


            case 8:


                return "MARKET";


            case 9:


                return "SALE";


            case 0:

            default:


                return "GENERAL";
        }
    }


    // ============================================================
    // CURSOR STRING
    // ============================================================

    private String getCursorString(
            Cursor cursor,
            String column
    ) {


        if (cursor == null) {


            return "";
        }


        int index =
                cursor.getColumnIndex(
                        column
                );


        if (index < 0
                ||
                cursor.isNull(
                        index
                )) {


            return "";
        }


        String value =
                cursor.getString(
                        index
                );


        return value == null
                ? ""
                : value.trim();
    }


    // ============================================================
    // SAFE STRING
    // ============================================================

    private String safeString(
            String value
    ) {


        if (value == null) {


            return "";
        }


        return value.trim();
    }


    // ============================================================
    // KAPANIŞ
    // ============================================================

    @Override
    protected void onDestroy() {


        if (databaseHelper != null) {


            if (socialCloudRepository != null) socialCloudRepository.close();
            databaseHelper.close();
        }


        super.onDestroy();
    }
}