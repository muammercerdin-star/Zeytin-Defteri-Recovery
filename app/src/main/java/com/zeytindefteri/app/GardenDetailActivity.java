package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bahçe detayı. Sezon özeti Bahçe Karnesi ile aynı GardenAnalyticsRepository kaynağından gelir;
 * bu ekran kendi masraf toplamını üretmez. Metin kuralları GardenDetailModel'dedir.
 */
public class GardenDetailActivity extends Activity {

    private static final int RECENT_OPERATIONS = 5;

    private DatabaseHelper databaseHelper;
    private long gardenId = -1L;
    private String screenOwner = "";

    private final ExecutorService summaryWorker = Executors.newSingleThreadExecutor();
    private int summaryGeneration = 0;

    private AlertDialog noteDialog;
    private EditText noteInput;

    private final Locale TR = new Locale("tr", "TR");

    private TextView tvGardenName, tvGardenSubtitle, tvIrrigation, tvTreeCount, tvAge, tvArea;
    private TextView tvSeasonTitle, tvGardenCost, tvGardenHarvest, tvGardenElek, tvGardenSale, tvKgPerTree, tvCostPerKg;
    private TextView tvStaleWarning, tvGeneralNote;
    private View cardStaleWarning;
    private LinearLayout containerGardenOperations, containerGardenNotes;

    private final int text = GreenTheme.color(R.color.zd_text);
    private final int muted = GreenTheme.color(R.color.zd_muted);
    private final int soft = GreenTheme.color(R.color.zd_text_soft);
    private final int border = GreenTheme.color(R.color.zd_border);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().hide();
        getWindow().setStatusBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().setNavigationBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().getDecorView().setSystemUiVisibility(0);
        setContentView(R.layout.activity_garden_detail);

        gardenId = getIntent().getLongExtra("garden_id", -1L);
        if (gardenId == -1L) {
            Toast.makeText(this, "Bahçe bulunamadı", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        databaseHelper = new DatabaseHelper(this);
        screenOwner = savedInstanceState == null
                ? databaseHelper.getActiveOwnerUid()
                : savedInstanceState.getString("screen_owner", "");

        findViews();
        setupButtons();
        refreshGarden();

        if (!isFinishing() && savedInstanceState != null && savedInstanceState.getBoolean("note_open")) {
            showAddNote(savedInstanceState.getString("note_draft", ""));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (databaseHelper != null && gardenId != -1L) refreshGarden();
    }

    private void findViews() {
        tvGardenName = findViewById(R.id.tvGardenName);
        tvGardenSubtitle = findViewById(R.id.tvGardenSubtitle);
        tvIrrigation = findViewById(R.id.tvIrrigation);
        tvTreeCount = findViewById(R.id.tvTreeCount);
        tvAge = findViewById(R.id.tvAge);
        tvArea = findViewById(R.id.tvArea);
        tvSeasonTitle = findViewById(R.id.tvSeasonTitle);
        tvGardenCost = findViewById(R.id.tvGardenCost);
        tvGardenHarvest = findViewById(R.id.tvGardenHarvest);
        tvGardenElek = findViewById(R.id.tvGardenElek);
        tvGardenSale = findViewById(R.id.tvGardenSale);
        tvKgPerTree = findViewById(R.id.tvKgPerTree);
        tvCostPerKg = findViewById(R.id.tvCostPerKg);
        cardStaleWarning = findViewById(R.id.cardStaleWarning);
        tvStaleWarning = findViewById(R.id.tvStaleWarning);
        tvGeneralNote = findViewById(R.id.tvGeneralNote);
        containerGardenOperations = findViewById(R.id.containerGardenOperations);
        containerGardenNotes = findViewById(R.id.containerGardenNotes);
    }

    private void setupButtons() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnEditGarden).setOnClickListener(v -> openForGarden(GardenEditActivity.class));
        findViewById(R.id.btnGardenAnalytics).setOnClickListener(v -> openForGarden(GardenAnalyticsActivity.class));
        findViewById(R.id.btnAllGardenOperations).setOnClickListener(v -> openForGarden(GardenOperationHistoryActivity.class));
        findViewById(R.id.btnAddNoteInline).setOnClickListener(v -> showAddNote(""));

        actionTile(R.id.btnAddOperation, "add", "İşlem", v -> openForGarden(NewOperationActivity.class));
        actionTile(R.id.btnAddHarvest, "basket", "Hasat", v -> openForGarden(HarvestActivity.class));
        actionTile(R.id.btnAddElek, "elek", "Elek", v -> openForGarden(ElekActivity.class));
        actionTile(R.id.btnAddSale, "tag", "Satış", v -> openForGarden(SaleActivity.class));
        actionTile(R.id.btnAddNote, "note", "Not", v -> showAddNote(""));
    }

    private void actionTile(int id, String icon, String label, View.OnClickListener click) {
        View tile = findViewById(id);
        ((ImageView) tile.findViewById(R.id.tileIcon)).setImageResource(LineIcons.drawable(icon));
        ((TextView) tile.findViewById(R.id.tileLabel)).setText(label);
        tile.setContentDescription(label + " ekle");
        tile.setOnClickListener(click);
    }

    private void openForGarden(Class<?> target) {
        startActivity(new Intent(this, target).putExtra("garden_id", gardenId));
    }

    // ------------------------------------------------------------ not ekle

    private void showAddNote(String draft) {
        if (!isSameOwner()) { finish(); return; }
        noteInput = new EditText(this);
        noteInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        noteInput.setMinLines(3);
        noteInput.setGravity(Gravity.TOP);
        noteInput.setTextColor(text);
        noteInput.setHintTextColor(muted);
        noteInput.setHint("Bahçenle ilgili notunu yaz");
        noteInput.setPadding(dp(18), dp(14), dp(18), dp(14));
        noteInput.setText(draft);

        noteDialog = new AlertDialog.Builder(this)
                .setTitle("Not Ekle")
                .setView(noteInput)
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Kaydet", null)
                .create();
        noteDialog.setOnShowListener(dialog -> {
            if (noteDialog.getWindow() != null) noteDialog.getWindow().setBackgroundDrawableResource(R.color.zd_card);
            noteDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> saveGardenNote());
        });
        noteDialog.show();
    }

    private void saveGardenNote() {
        if (noteInput == null) return;
        String note = noteInput.getText().toString().trim();
        if (note.isEmpty()) { noteInput.setError("Boş not kaydedilemez"); return; }
        if (!isSameOwner()) { finish(); return; }
        try {
            String date = new SimpleDateFormat("dd.MM.yyyy HH:mm", TR).format(new Date());
            if (databaseHelper.addGardenNote(gardenId, date, note, "") == -1L) {
                noteInput.setError("Not kaydedilemedi. Bahçeyi ve oturumu kontrol et.");
                return;
            }
        } catch (android.database.SQLException error) {
            noteInput.setError("Not kaydedilemedi. Tekrar dene.");
            return;
        }
        if (noteDialog != null && noteDialog.isShowing()) noteDialog.dismiss();
        loadGardenNotes();
        // Local-first: kayıt bulutu beklemez; mevcut senkronizasyon sonra yükler.
        Toast.makeText(this, "Not kaydedildi", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("screen_owner", screenOwner);
        boolean open = noteDialog != null && noteDialog.isShowing();
        state.putBoolean("note_open", open);
        if (open && noteInput != null) state.putString("note_draft", noteInput.getText().toString());
    }

    @Override
    protected void onDestroy() {
        summaryGeneration++;
        if (noteDialog != null && noteDialog.isShowing()) noteDialog.dismiss();
        // Özet işçisi kendi DatabaseHelper'ını kullandığı için ana helper güvenle kapatılır.
        summaryWorker.shutdownNow();
        if (databaseHelper != null) databaseHelper.close();
        super.onDestroy();
    }

    // ------------------------------------------------------------ yükleme

    private void refreshGarden() {
        if (!isSameOwner()) { finish(); return; }
        loadGarden();
        if (isFinishing()) return;
        loadGardenSummary();
        loadOperations();
        loadGardenNotes();
    }

    private void loadGarden() {
        Cursor c = databaseHelper.getGarden(gardenId);
        if (!c.moveToFirst()) {
            c.close();
            Toast.makeText(this, "Bahçe bulunamadı", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String name = str(c, "name");
        int treeCount = c.getInt(c.getColumnIndexOrThrow("tree_count"));
        int age = c.getInt(c.getColumnIndexOrThrow("age"));
        double area = c.getDouble(c.getColumnIndexOrThrow("area_decare"));
        String subtitle = GardenDetailModel.subtitle(str(c, "variety"), str(c, "neighborhood"),
                str(c, "district"), str(c, "province"));
        String irrigation = str(c, "irrigation_type");
        String notes = str(c, "notes");
        c.close();

        tvGardenName.setText(GardenDetailModel.blank(name) ? "Bahçe" : name);
        tvGardenSubtitle.setText(subtitle);
        tvTreeCount.setText(GardenDetailModel.count(treeCount));
        tvAge.setText(GardenDetailModel.count(age));
        tvArea.setText(GardenDetailModel.number(area));
        tvIrrigation.setText(GardenDetailModel.irrigation(irrigation));
        tvGeneralNote.setText(GardenDetailModel.blank(notes) ? "Not bulunmuyor" : notes);
    }

    private void loadGardenSummary() {
        // Bahçe/sezon değişiminde eski rakamlar kısa süre bile görünmesin.
        setSummary("…");
        final int ticket = ++summaryGeneration;
        final String owner = screenOwner;
        final int season = databaseHelper.getActiveSeasonYear();
        final long requestedGardenId = gardenId;
        tvSeasonTitle.setText("BU SEZON · " + season);

        summaryWorker.execute(() -> {
            GardenAnalyticsSnapshot snapshot = null;
            try {
                // İşçi kendi helper'ını kullanır; ekranın helper'ıyla yarışmaz.
                DatabaseHelper analyticsHelper = new DatabaseHelper(getApplicationContext());
                try {
                    GardenAnalyticsRepository.Result result =
                            new GardenAnalyticsRepository(analyticsHelper).load(owner, season, false);
                    if (result != null && result.current != null) snapshot = result.current.garden(requestedGardenId);
                } finally {
                    analyticsHelper.close();
                }
            } catch (RuntimeException error) {
                snapshot = null;
            }
            final GardenAnalyticsSnapshot loaded = snapshot;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || ticket != summaryGeneration) return;
                if (!isSameOwner()) { finish(); return; }
                if (season != databaseHelper.getActiveSeasonYear() || requestedGardenId != gardenId) return;
                if (loaded == null) { setSummary("—"); return; }
                applyGardenSummary(loaded);
            });
        });
    }

    /** Bahçe Karnesi ile aynı değerler: masraf, hasat, elek boy toplamı, gerçek satış geliri. */
    private void applyGardenSummary(GardenAnalyticsSnapshot s) {
        tvGardenCost.setText(formatMoney(s.expense));
        tvGardenHarvest.setText(formatKg(s.harvestKg));
        tvGardenElek.setText(formatKg(s.totalGradeKg()));
        tvGardenSale.setText(formatMoney(s.revenue));
        Double kgPerTree = s.kgPerTree();
        tvKgPerTree.setText(kgPerTree == null || !Double.isFinite(kgPerTree) ? "—" : formatKg(kgPerTree));
        Double costPerKg = s.costPerKg();
        tvCostPerKg.setText(costPerKg == null || !Double.isFinite(costPerKg) ? "—" : formatMoney(costPerKg) + "/kg");
    }

    private void setSummary(String placeholder) {
        for (TextView t : new TextView[]{tvGardenCost, tvGardenHarvest, tvGardenElek, tvGardenSale, tvKgPerTree, tvCostPerKg}) {
            t.setText(placeholder);
        }
    }

    /**
     * Sezonun tüm işlemleri taranır: ilk beşi listelenir, en yeni işlem tarihi uyarı için tutulur
     * (liste kayıt sırasına göre geldiği için en yeni tarih her zaman ilk satır değildir).
     */
    private void loadOperations() {
        containerGardenOperations.removeAllViews();
        long latestDay = -1;
        int count = 0;
        Cursor c = databaseHelper.getGardenOperations(gardenId, 0);
        try {
            while (c.moveToNext()) {
                String date = str(c, "operation_date");
                latestDay = Math.max(latestDay, GardenDetailModel.epochDay(date));
                if (count < RECENT_OPERATIONS) {
                    addOperationRow(str(c, "type"), date,
                            c.getDouble(c.getColumnIndexOrThrow("quantity")), str(c, "unit"),
                            c.getDouble(c.getColumnIndexOrThrow("cost")), str(c, "note"), count == 0);
                }
                count++;
            }
        } finally {
            c.close();
        }
        if (count == 0) containerGardenOperations.addView(emptyText("Bu sezon bu bahçe için henüz işlem kaydı yok."));

        Calendar now = Calendar.getInstance();
        String warning = GardenDetailModel.staleWarning(latestDay, GardenDetailModel.civilToDays(
                now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH)));
        tvStaleWarning.setText(warning);
        cardStaleWarning.setVisibility(warning.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void addOperationRow(String type, String date, double quantity, String unit,
                                 double cost, String note, boolean first) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        if (!first) {
            View line = new View(this);
            line.setBackgroundColor(border);
            containerGardenOperations.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        }

        FrameLayout ring = new FrameLayout(this);
        ring.setBackgroundResource(R.drawable.bg_icon_ring);
        ImageView icon = new ImageView(this);
        icon.setImageResource(LineIcons.forType(type));
        icon.setColorFilter(LineIcons.toneColor(type));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        ring.addView(icon, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        row.addView(ring, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), 0, dp(8), 0);
        String title = OperationKinds.label(type);
        String meta = GardenDetailModel.operationMeta(date, quantity, unit, note);
        content.addView(label(title, 14, text, true));
        if (!meta.isEmpty()) {
            TextView sub = label(meta, 12, muted, false);
            sub.setMaxLines(2);
            sub.setPadding(0, dp(1), 0, 0);
            content.addView(sub);
        }
        row.addView(content, new LinearLayout.LayoutParams(0, -2, 1));

        String costText = cost > 0 ? formatMoney(cost) : "";
        if (!costText.isEmpty()) row.addView(label(costText, 13, text, true));
        row.setContentDescription(title + (meta.isEmpty() ? "" : ", " + meta) + (costText.isEmpty() ? "" : ", " + costText));
        containerGardenOperations.addView(row);
    }

    private void loadGardenNotes() {
        containerGardenNotes.removeAllViews();
        Cursor c = databaseHelper.getGardenNotes(gardenId);
        int count = 0;
        try {
            while (c.moveToNext()) {
                addNoteRow(str(c, "note_date"), str(c, "note"));
                count++;
            }
        } finally {
            c.close();
        }
        if (count == 0) {
            TextView empty = label("Henüz bahçe notu yok", 13, muted, false);
            empty.setPadding(0, dp(4), 0, dp(4));
            containerGardenNotes.addView(empty);
        }
    }

    private void addNoteRow(String date, String note) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.bg_inset);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.addView(label(GardenDetailModel.blank(date) ? "Tarih belirtilmedi" : date, 11, muted, false));
        TextView body = label(note == null ? "" : note, 13, soft, false);
        body.setPadding(0, dp(4), 0, 0);
        box.addView(body);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = containerGardenNotes.getChildCount() > 0 ? dp(8) : dp(2);
        containerGardenNotes.addView(box, p);
    }

    // ------------------------------------------------------------ yardımcılar

    private boolean isSameOwner() {
        if (databaseHelper == null) return false;
        String active = databaseHelper.getActiveOwnerUid();
        return (screenOwner == null ? "" : screenOwner).equals(active == null ? "" : active);
    }

    private static String str(Cursor c, String column) {
        return c.getString(c.getColumnIndexOrThrow(column));
    }

    private TextView emptyText(String message) {
        TextView t = label(message, 13, muted, false);
        t.setGravity(Gravity.CENTER);
        t.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(64)));
        return t;
    }

    private TextView label(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private String formatMoney(double value) {
        return formatNumber(value) + " TL";
    }

    private String formatKg(double value) {
        return formatNumber(value) + " kg";
    }

    private String formatNumber(double value) {
        NumberFormat f = NumberFormat.getNumberInstance(TR);
        f.setMaximumFractionDigits(2);
        f.setMinimumFractionDigits(0);
        return f.format(value);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
