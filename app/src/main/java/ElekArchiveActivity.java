package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Elek geçmişi (13.10.2026 tasarımı): koyu tema arşiv listesi.
 * Bir kayda dokununca ElekReceiptActivity (makbuz) açılır.
 * Veri: ElekRepository.getArchiveRecords (Elek sekmesiyle aynı sorgu).
 */
public class ElekArchiveActivity extends Activity {

    static final String EXTRA_GARDEN_ID = "garden_id";

    private DatabaseHelper databaseHelper;

    private View btnArchiveBack;
    private TextView btnArchiveYear;
    private TextView tvArchiveScreenScope;
    private TextView tvArchiveScreenTotalKg;
    private TextView tvArchiveScreenTotalRevenue;
    private TextView tvArchiveScreenAverage;
    private TextView tvArchiveSeasonLegend;
    private TextView tvArchiveEmpty;
    private LinearLayout archiveSeasonBar;
    private FrameLayout archiveGardenChipsHost;

    private android.widget.ListView archiveRecordList;
    private final List<ArchiveCard> archiveCards = new ArrayList<>();
    private ArchiveAdapter archiveAdapter;

    private int selectedYear = Calendar.getInstance().get(Calendar.YEAR);
    private long selectedGardenId = -1;
    private String selectedGardenName = "Tüm bahçeler";

    private final List<Long> gardenIds = new ArrayList<>();
    private final List<String> gardenNames = new ArrayList<>();

    private final Locale turkishLocale = new Locale("tr", "TR");
    private DecimalFormat numberFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getActionBar() != null) {
            getActionBar().hide();
        }
        getWindow().setStatusBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().setNavigationBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().getDecorView().setSystemUiVisibility(0);

        setContentView(R.layout.activity_elek_archive);

        databaseHelper = new DatabaseHelper(this);
        selectedYear = databaseHelper.getActiveSeasonYear();

        setupNumberFormat();
        findViews();
        setupButtons();
        loadGardenFilter();

        long requested = getIntent().getLongExtra(EXTRA_GARDEN_ID, -1L);
        int index = gardenIds.indexOf(requested);
        if (requested > 0 && index >= 0) {
            selectedGardenId = requested;
            selectedGardenName = gardenNames.get(index);
        }

        loadArchive();
    }

    private void setupNumberFormat() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(turkishLocale);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        numberFormat = new DecimalFormat("#,##0.##", symbols);
    }

    private void findViews() {
        archiveRecordList = findViewById(R.id.archiveRecordList);
        View header = getLayoutInflater().inflate(R.layout.elek_archive_header, archiveRecordList, false);
        archiveRecordList.addHeaderView(header, null, false);
        View footer = getLayoutInflater().inflate(R.layout.elek_archive_footer, archiveRecordList, false);
        archiveRecordList.addFooterView(footer, null, false);
        archiveAdapter = new ArchiveAdapter();
        archiveRecordList.setAdapter(archiveAdapter);

        btnArchiveBack = header.findViewById(R.id.btnArchiveBack);
        btnArchiveYear = header.findViewById(R.id.btnArchiveYear);
        tvArchiveScreenScope = header.findViewById(R.id.tvArchiveScreenScope);
        tvArchiveScreenTotalKg = header.findViewById(R.id.tvArchiveScreenTotalKg);
        tvArchiveScreenTotalRevenue = header.findViewById(R.id.tvArchiveScreenTotalRevenue);
        tvArchiveScreenAverage = header.findViewById(R.id.tvArchiveScreenAverage);
        tvArchiveSeasonLegend = header.findViewById(R.id.tvArchiveSeasonLegend);
        tvArchiveEmpty = header.findViewById(R.id.tvArchiveEmpty);
        archiveSeasonBar = header.findViewById(R.id.archiveSeasonBar);
        archiveGardenChipsHost = header.findViewById(R.id.archiveGardenChipsHost);
    }

    private void setupButtons() {
        btnArchiveBack.setOnClickListener(view -> finish());
        btnArchiveYear.setOnClickListener(view -> showYearPicker());
    }

    private void showYearPicker() {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        String[] years = new String[10];
        for (int i = 0; i < years.length; i++) {
            years[i] = String.valueOf(currentYear - i);
        }

        new AlertDialog.Builder(this)
                .setTitle("Üretim yılı seç")
                .setItems(years, (dialog, which) -> {
                    selectedYear = Integer.parseInt(years[which]);
                    loadArchive();
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void loadGardenFilter() {
        gardenIds.clear();
        gardenNames.clear();
        gardenIds.add(-1L);
        gardenNames.add("Tüm bahçeler");

        Cursor cursor = null;
        try {
            cursor = databaseHelper.getGardens();
            if (cursor != null) {
                int idIndex = cursor.getColumnIndex("id");
                int nameIndex = cursor.getColumnIndex("name");
                while (cursor.moveToNext()) {
                    if (idIndex < 0 || nameIndex < 0) {
                        continue;
                    }
                    long id = cursor.getLong(idIndex);
                    String name = cursor.getString(nameIndex);
                    if (name == null || name.trim().isEmpty()) {
                        continue;
                    }
                    gardenIds.add(id);
                    gardenNames.add(name.trim());
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    // Bahçe filtre çipleri (birden fazla bahçe varsa), yatay kaydırma içinde.
    private void renderGardenChips() {
        archiveGardenChipsHost.removeAllViews();
        if (gardenIds.size() <= 2) {
            archiveGardenChipsHost.setVisibility(View.GONE);
            return;
        }
        archiveGardenChipsHost.setVisibility(View.VISIBLE);
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        int green = GreenTheme.color(R.color.zd_green);
        int muted = GreenTheme.color(R.color.zd_muted);
        for (int i = 0; i < gardenIds.size(); i++) {
            final long id = gardenIds.get(i);
            final String name = gardenNames.get(i);
            boolean on = id == selectedGardenId;
            TextView chip = new TextView(this);
            chip.setText(name);
            chip.setTextSize(12);
            chip.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            chip.setTextColor(on ? green : muted);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(40));
            chip.setPadding(dp(14), 0, dp(14), 0);
            chip.setBackground(rounded(
                    on ? GreenTheme.color(R.color.zd_green_soft) : Color.TRANSPARENT,
                    on ? green : GreenTheme.color(R.color.zd_border)));
            chip.setContentDescription("Filtre: " + name + (on ? ", seçili" : ""));
            chip.setOnClickListener(v -> {
                if (id == selectedGardenId) return;
                selectedGardenId = id;
                selectedGardenName = name;
                loadArchive();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) p.setMargins(dp(8), 0, 0, 0);
            chips.addView(chip, p);
        }
        scroller.addView(chips);
        archiveGardenChipsHost.addView(scroller);
    }

    private void loadArchive() {
        btnArchiveYear.setText(selectedYear + "  ▾");
        renderGardenChips();
        tvArchiveScreenScope.setText(selectedYear + " SEZONU · "
                + selectedGardenName.toUpperCase(turkishLocale));

        archiveCards.clear();

        Cursor cursor = null;
        double seasonTotalKg = 0;
        double seasonTotalRevenue = 0;
        double[] seasonGrades = new double[6];
        int recordCount = 0;
        String previousMonth = "";

        try {
            cursor = new ElekRepository(databaseHelper).getArchiveRecords(
                    selectedYear,
                    selectedGardenId,
                    databaseHelper.getActiveOwnerUid()
            );

            int recordIdIndex = cursor.getColumnIndexOrThrow("record_id");
            int dateIndex = cursor.getColumnIndexOrThrow("record_date");
            int gardenIndex = cursor.getColumnIndexOrThrow("garden_name");
            int[] gradeIndexes = {
                    cursor.getColumnIndexOrThrow("double_kg"),
                    cursor.getColumnIndexOrThrow("grade1_kg"),
                    cursor.getColumnIndexOrThrow("grade2_kg"),
                    cursor.getColumnIndexOrThrow("grade3_kg"),
                    cursor.getColumnIndexOrThrow("grade4_kg"),
                    cursor.getColumnIndexOrThrow("oil_kg")
            };
            int totalKgIndex = cursor.getColumnIndexOrThrow("total_kg");
            int totalRevenueIndex = cursor.getColumnIndexOrThrow("total_revenue");

            while (cursor.moveToNext()) {
                ArchiveCard card = new ArchiveCard();
                card.recordId = cursor.getLong(recordIdIndex);
                String date = cursor.getString(dateIndex);
                card.gardenName = cursor.getString(gardenIndex);
                card.totalKgValue = cursor.getDouble(totalKgIndex);
                card.totalRevenueValue = cursor.getDouble(totalRevenueIndex);
                for (int i = 0; i < 6; i++) {
                    card.grades[i] = cursor.getDouble(gradeIndexes[i]);
                    seasonGrades[i] += card.grades[i];
                }
                card.dateLong = formatTableDate(date);
                card.dayNumber = dayNumberOf(date);
                card.weekday = ElekInsights.shortWeekday(formatDay(date));
                String month = ElekInsights.monthHeader(date);
                card.monthHeader = !month.isEmpty() && !month.equals(previousMonth) ? month : "";
                if (!month.isEmpty()) previousMonth = month;

                seasonTotalKg += card.totalKgValue;
                seasonTotalRevenue += card.totalRevenueValue;
                recordCount++;
                archiveCards.add(card);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Elek arşivi okunamadı.", Toast.LENGTH_LONG).show();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (recordCount == 0) {
            tvArchiveEmpty.setText(selectedGardenId > 0
                    ? "Bu bahçe için " + selectedYear + " sezonunda elek kaydı yok."
                    : selectedYear + " sezonunda henüz elek kaydı yok.");
            tvArchiveEmpty.setVisibility(View.VISIBLE);
        } else {
            tvArchiveEmpty.setVisibility(View.GONE);
        }

        tvArchiveScreenTotalRevenue.setText(money(seasonTotalRevenue));
        tvArchiveScreenTotalKg.setText(formatNumber(seasonTotalKg) + " kg");
        tvArchiveScreenAverage.setText("ort. " + ElekInsights.formatPerKg(seasonTotalRevenue, seasonTotalKg));
        tvArchiveScreenTotalRevenue.setTextColor(GreenTheme.color(
                seasonTotalKg > 0 ? R.color.zd_warning : R.color.zd_muted));
        renderBar(archiveSeasonBar, seasonGrades);
        tvArchiveSeasonLegend.setText(ElekInsights.legend(seasonGrades));
        archiveSeasonBar.setContentDescription(ElekInsights.accessibleLegend(seasonGrades));

        archiveAdapter.notifyDataSetChanged();
    }

    // "2026-10-07" -> "7"; tanınmayan biçimde (ör. eski "06.09.2026") gün kısmını dener, olmazsa boş.
    private String dayNumberOf(String date) {
        if (date == null) return "";
        String v = date.trim();
        try {
            if (v.length() >= 10 && v.charAt(4) == '-') return String.valueOf(Integer.parseInt(v.substring(8, 10)));
            if (v.length() >= 2 && (v.indexOf('.') == 2 || v.indexOf('/') == 2)) return String.valueOf(Integer.parseInt(v.substring(0, 2)));
        } catch (NumberFormatException ignored) {
        }
        return "";
    }

    private void renderBar(LinearLayout bar, double[] grades) {
        bar.removeAllViews();
        bar.setClipToOutline(true);
        int[] colors = {
                GreenTheme.color(R.color.elek_badge_double),
                GreenTheme.color(R.color.elek_badge_one),
                GreenTheme.color(R.color.elek_badge_two),
                GreenTheme.color(R.color.elek_badge_three),
                GreenTheme.color(R.color.elek_badge_four),
                GreenTheme.color(R.color.elek_badge_oil)
        };
        boolean first = true;
        for (int i = 0; i < grades.length; i++) {
            if (grades[i] <= 0) continue;
            View segment = new View(this);
            segment.setBackgroundColor(colors[i]);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, (float) grades[i]);
            if (!first) p.setMargins(dp(2), 0, 0, 0);
            first = false;
            bar.addView(segment, p);
        }
    }

    private String formatDay(String isoDate) {
        if (isoDate == null) return "";
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            input.setLenient(false);
            Date date = input.parse(isoDate);
            if (date == null) return "";
            String day = new SimpleDateFormat("EEEE", turkishLocale).format(date);
            return day.substring(0, 1).toUpperCase(turkishLocale) + day.substring(1);
        } catch (Exception ignored) {
            return "";
        }
    }

    private final class ArchiveAdapter extends BaseAdapter {

        @Override public int getCount() { return archiveCards.size(); }
        @Override public Object getItem(int position) { return archiveCards.get(position); }
        @Override public long getItemId(int position) { return archiveCards.get(position).recordId; }
        @Override public boolean areAllItemsEnabled() { return true; }
        @Override public boolean isEnabled(int position) { return true; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ArchiveViewHolder holder;
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_elek_archive, parent, false);
                holder = new ArchiveViewHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (ArchiveViewHolder) convertView.getTag();
            }

            final ArchiveCard card = archiveCards.get(position);
            holder.monthHeader.setText(card.monthHeader);
            holder.monthHeader.setVisibility(card.monthHeader.isEmpty() ? View.GONE : View.VISIBLE);
            holder.dayNumber.setText(card.dayNumber);
            holder.weekday.setText(card.weekday);
            holder.garden.setText(card.gardenName);
            holder.meta.setText(formatNumber(card.totalKgValue) + " kg  ·  "
                    + ElekInsights.formatPerKg(card.totalRevenueValue, card.totalKgValue));
            holder.revenue.setText(money(card.totalRevenueValue));

            holder.bar.setClipToOutline(true);
            boolean first = true;
            for (int i = 0; i < holder.gradeSegments.length; i++) {
                View segment = holder.gradeSegments[i];
                double kg = card.grades[i];
                LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) segment.getLayoutParams();
                p.weight = (float) Math.max(0, kg);
                p.setMargins(first || kg <= 0 ? 0 : dp(2), 0, 0, 0);
                segment.setLayoutParams(p);
                segment.setVisibility(kg > 0 ? View.VISIBLE : View.GONE);
                if (kg > 0) first = false;
            }

            holder.card.setContentDescription(card.dateLong + ", " + card.gardenName + ", "
                    + formatNumber(card.totalKgValue) + " kilogram, " + money(card.totalRevenueValue) + ". "
                    + ElekInsights.accessibleLegend(card.grades) + " Makbuzu açmak için dokun.");
            holder.card.setOnClickListener(v -> openReceipt(card));
            return convertView;
        }
    }

    private void openReceipt(ArchiveCard card) {
        if (card == null || card.recordId <= 0) {
            return;
        }
        startActivity(new Intent(this, ElekReceiptActivity.class)
                .putExtra(ElekReceiptActivity.EXTRA_RECORD_ID, card.recordId));
    }

    private String formatTableDate(String isoDate) {
        if (isoDate == null || isoDate.trim().isEmpty()) {
            return "-";
        }
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat output = new SimpleDateFormat("dd MMMM yyyy", turkishLocale);
            Date date = input.parse(isoDate);
            if (date == null) {
                return isoDate;
            }
            return output.format(date);
        } catch (Exception e) {
            return isoDate;
        }
    }

    private String money(double value) {
        return formatNumber(value) + " TL";
    }

    private String formatNumber(double value) {
        if (numberFormat == null) {
            return String.valueOf(value);
        }
        return numberFormat.format(value);
    }

    private GradientDrawable rounded(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(20));
        g.setStroke(dp(1), stroke);
        return g;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (databaseHelper != null && archiveRecordList != null) {
            loadGardenFilter();
            loadArchive();
        }
    }

    @Override
    protected void onDestroy() {
        if (databaseHelper != null) {
            databaseHelper.close();
        }
        super.onDestroy();
    }

    private static final class ArchiveCard {
        long recordId;
        String gardenName = "";
        String dateLong = "";
        String dayNumber = "";
        String weekday = "";
        String monthHeader = "";
        double totalKgValue;
        double totalRevenueValue;
        final double[] grades = new double[6];
    }

    private static final class ArchiveViewHolder {
        final TextView monthHeader;
        final View card;
        final TextView dayNumber;
        final TextView weekday;
        final TextView garden;
        final TextView meta;
        final TextView revenue;
        final LinearLayout bar;
        final View[] gradeSegments = new View[6];

        ArchiveViewHolder(View root) {
            monthHeader = root.findViewById(R.id.archiveMonthHeader);
            card = root.findViewById(R.id.archiveRecordCard);
            dayNumber = root.findViewById(R.id.archiveRecordDayNumber);
            weekday = root.findViewById(R.id.archiveRecordWeekday);
            garden = root.findViewById(R.id.archiveRecordGarden);
            meta = root.findViewById(R.id.archiveRecordMeta);
            revenue = root.findViewById(R.id.archiveRecordRevenue);
            bar = root.findViewById(R.id.archiveRecordBar);
            gradeSegments[0] = root.findViewById(R.id.archiveGrade0);
            gradeSegments[1] = root.findViewById(R.id.archiveGrade1);
            gradeSegments[2] = root.findViewById(R.id.archiveGrade2);
            gradeSegments[3] = root.findViewById(R.id.archiveGrade3);
            gradeSegments[4] = root.findViewById(R.id.archiveGrade4);
            gradeSegments[5] = root.findViewById(R.id.archiveGrade5);
        }
    }
}
