package com.zeytindefteri.app;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Edits an existing garden locally. No insert/delete or cloud identity mutation. */
public class GardenEditActivity extends Activity {
    private static final int PHOTO_REQUEST = 201;
    private static final String[] KEYS = {"name", "tree_count", "age", "variety",
            "area_decare", "province", "district", "neighborhood", "irrigation_type", "notes"};
    private static final String[] LABELS = {"Bahçe adı", "Ağaç sayısı", "Yaş", "Çeşit",
            "Alan (dekar)", "İl", "İlçe", "Mahalle / köy", "Sulama tipi", "Genel not"};
    private final EditText[] fields = new EditText[KEYS.length];
    private DatabaseHelper databaseHelper;
    private long gardenId;
    private String screenOwner;
    private String photoUri = "";
    private TextView photoStatus;
    private final int textColor = GreenTheme.color(R.color.zd_text);
    private final int muted = GreenTheme.color(R.color.zd_muted);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getActionBar() != null) getActionBar().hide();
        getWindow().setStatusBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().setNavigationBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().getDecorView().setSystemUiVisibility(0);
        databaseHelper = new DatabaseHelper(this);
        gardenId = getIntent().getLongExtra("garden_id", -1);
        screenOwner = state == null ? databaseHelper.getActiveOwnerUid()
                : state.getString("screen_owner", "");
        if (!screenOwner.equals(databaseHelper.getActiveOwnerUid())) { finish(); return; }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(GreenTheme.color(R.color.zd_bg));
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(12), dp(20), dp(28));
        scroll.addView(form);
        TextView back = label("‹  Bahçe Detayı", 16);
        back.setMinHeight(dp(48));
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setContentDescription("Bahçe detayına geri dön");
        back.setOnClickListener(v -> finish());
        form.addView(back);
        TextView title = label("Bahçe Düzenle", 26);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        form.addView(title);

        for (int i = 0; i < KEYS.length; i++) {
            TextView caption = label(LABELS[i], 13);
            caption.setTextColor(muted);
            caption.setPadding(0, dp(16), 0, dp(6));
            form.addView(caption);
            EditText input = new EditText(this);
            input.setTextColor(textColor);
            input.setHintTextColor(muted);
            input.setBackground(background(R.color.zd_input));
            input.setPadding(dp(12), dp(12), dp(12), dp(12));
            input.setMinHeight(dp(48));
            input.setContentDescription(LABELS[i]);
            input.setInputType(i == 1 || i == 2 ? InputType.TYPE_CLASS_NUMBER
                    : i == 4 ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            if (i == 9) {
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                input.setMinLines(3);
                input.setGravity(Gravity.TOP);
            }
            fields[i] = input;
            form.addView(input, new LinearLayout.LayoutParams(-1, -2));
        }
        photoStatus = label("", 13);
        photoStatus.setPadding(0, dp(16), 0, dp(4));
        form.addView(photoStatus);
        TextView photo = button("Fotoğraf seç / değiştir");
        photo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            try { startActivityForResult(intent, PHOTO_REQUEST); }
            catch (android.content.ActivityNotFoundException error) {
                Toast.makeText(this, "Fotoğraf seçici bulunamadı", Toast.LENGTH_SHORT).show();
            }
        });
        form.addView(photo);
        TextView removePhoto = button("Fotoğrafı kaldır");
        removePhoto.setOnClickListener(v -> { photoUri = ""; updatePhotoStatus(); });
        form.addView(removePhoto);
        TextView save = button("Değişiklikleri kaydet");
        save.setBackground(background(R.color.zd_green));
        save.setTextColor(GreenTheme.color(R.color.zd_bg));
        save.setTypeface(null, Typeface.BOLD);
        save.setOnClickListener(v -> saveGarden());
        form.addView(save);
        setContentView(scroll);

        try (Cursor garden = databaseHelper.getGarden(gardenId)) {
            if (!garden.moveToFirst()) { unavailable(); return; }
            for (int i = 0; i < KEYS.length; i++) {
                String value = garden.getString(garden.getColumnIndexOrThrow(KEYS[i]));
                fields[i].setText(state == null ? value : state.getString(KEYS[i], value));
            }
            String value = garden.getString(garden.getColumnIndexOrThrow("photo_uri"));
            photoUri = state == null ? value : state.getString("photo_uri", value);
            if (photoUri == null) photoUri = "";
        }
        updatePhotoStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        if (databaseHelper != null && !screenOwner.equals(databaseHelper.getActiveOwnerUid())) finish();
    }

    private void saveGarden() {
        if (!screenOwner.equals(databaseHelper.getActiveOwnerUid())) { unavailable(); return; }
        if (value(0).isEmpty()) { fields[0].setError("Bahçe adı gerekli"); return; }
        int trees, age;
        double area;
        try { trees = integer(1); } catch (NumberFormatException e) { invalid(1); return; }
        try { age = integer(2); } catch (NumberFormatException e) { invalid(2); return; }
        try {
            area = value(4).isEmpty() ? 0 : Double.parseDouble(value(4).replace(',', '.'));
            if (area < 0 || Double.isNaN(area) || Double.isInfinite(area)) throw new NumberFormatException();
        } catch (NumberFormatException e) { invalid(4); return; }
        try {
            boolean updated = new GardenRepository(databaseHelper).updateGarden(gardenId,
                    value(0), trees, age, value(3), area, value(5), value(6), value(7),
                    value(8), value(9), photoUri) == 1;
            if (!updated) {
                Toast.makeText(this, "Bahçe güncellenemedi. Bahçeyi ve oturumu kontrol et.", Toast.LENGTH_LONG).show();
                return;
            }
        } catch (android.database.SQLException error) {
            Toast.makeText(this, "Değişiklikler kaydedilemedi. Tekrar dene.", Toast.LENGTH_LONG).show();
            return;
        }
        // The existing home onResume sync picks up the changed updated_at later.
        // Local success does not depend on Firebase initialization or connectivity.
        setResult(RESULT_OK);
        Toast.makeText(this, "Bahçe güncellendi", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PHOTO_REQUEST || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            photoUri = uri.toString();
            updatePhotoStatus();
        } catch (SecurityException error) {
            Toast.makeText(this, "Kalıcı erişim alınamadı. Başka bir fotoğraf seç.", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("screen_owner", screenOwner);
        state.putString("photo_uri", photoUri);
        for (int i = 0; i < KEYS.length; i++) if (fields[i] != null) state.putString(KEYS[i], fields[i].getText().toString());
    }

    @Override protected void onDestroy() {
        if (databaseHelper != null) databaseHelper.close();
        super.onDestroy();
    }

    private void unavailable() {
        Toast.makeText(this, "Bahçe bulunamadı veya oturum değişti", Toast.LENGTH_SHORT).show();
        finish();
    }
    private void updatePhotoStatus() { photoStatus.setText(photoUri.isEmpty() ? "Fotoğraf yok" : "Fotoğraf seçili"); }
    private String value(int i) { return fields[i].getText().toString().trim(); }
    private int integer(int i) {
        int n = value(i).isEmpty() ? 0 : Integer.parseInt(value(i));
        if (n < 0) throw new NumberFormatException();
        return n;
    }
    private void invalid(int i) { fields[i].setError("Geçerli, sıfır veya daha büyük bir sayı yaz"); fields[i].requestFocus(); }
    private TextView label(String value, int size) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(textColor); return text;
    }
    private TextView button(String value) {
        TextView text = label(value, 16);
        text.setGravity(Gravity.CENTER);
        text.setMinHeight(dp(50));
        text.setBackground(background(R.color.zd_surface));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12); text.setLayoutParams(lp);
        return text;
    }
    private GradientDrawable background(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(GreenTheme.color(color));
        shape.setCornerRadius(dp(14));
        shape.setStroke(dp(1), GreenTheme.color(R.color.zd_border));
        return shape;
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
