package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
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

public class ElekArchiveActivity extends Activity {

    private static final String[] GRADE_KEYS = {"DOUBLE", "1", "2", "3", "4", "YAGLIK"};
    private static final String[] GRADE_BADGES = {"★", "1", "2", "3", "4", "○"};
    private static final String[] GRADE_LABELS = {"Double", "1. Kalite", "2. Kalite", "3. Kalite", "4. Kalite", "Yağlık"};

    private DatabaseHelper databaseHelper;
    private ElekRepository elekRepository;

    private TextView btnArchiveBack;
    private TextView btnArchiveYear;
    private TextView btnArchiveGardenFilter;
    private TextView tvArchiveScreenSubtitle;

    private TextView tvArchiveScreenTotalKg;
    private TextView tvArchiveScreenTotalRevenue;
    private TextView tvArchiveScreenAverage;

    private android.widget.ListView archiveRecordList;
    private final List<ArchiveCard> archiveCards = new ArrayList<>();
    private ArchiveAdapter archiveAdapter;
    private TextView tvArchiveEmpty;

    private int selectedYear = Calendar.getInstance().get(Calendar.YEAR);
    private long selectedGardenId = -1;
    private String selectedGardenName = "Tüm Bahçeler";

    private final List<Long> gardenIds = new ArrayList<>();
    private final List<String> gardenNames = new ArrayList<>();

    private final Locale turkishLocale = new Locale("tr", "TR");
    private DecimalFormat numberFormat;
    private DecimalFormat averageNumberFormat;

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
        elekRepository = new ElekRepository(databaseHelper);
        selectedYear = databaseHelper.getActiveSeasonYear();

        setupNumberFormat();
        findViews();
        setupButtons();
        loadGardenFilter();
        loadArchive();
    }

    private void setupNumberFormat() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(turkishLocale);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        numberFormat = new DecimalFormat("#,##0.##", symbols);
        averageNumberFormat = new DecimalFormat("#,##0.00", symbols);
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
        btnArchiveGardenFilter = header.findViewById(R.id.btnArchiveGardenFilter);
        tvArchiveScreenSubtitle = header.findViewById(R.id.tvArchiveScreenSubtitle);
        tvArchiveScreenTotalKg = header.findViewById(R.id.tvArchiveScreenTotalKg);
        tvArchiveScreenTotalRevenue = header.findViewById(R.id.tvArchiveScreenTotalRevenue);
        tvArchiveScreenAverage = header.findViewById(R.id.tvArchiveScreenAverage);
        tvArchiveEmpty = header.findViewById(R.id.tvArchiveEmpty);
    }

    private void setupButtons() {
        btnArchiveBack.setOnClickListener(view -> finish());
        btnArchiveYear.setOnClickListener(view -> showYearPicker());
        btnArchiveGardenFilter.setOnClickListener(view -> showGardenFilter());
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
        gardenNames.add("Tüm Bahçeler");

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

    private void showGardenFilter() {
        String[] names = gardenNames.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Bahçe seç")
                .setItems(names, (dialog, which) -> {
                    selectedGardenId = gardenIds.get(which);
                    selectedGardenName = gardenNames.get(which);
                    loadArchive();
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void loadArchive() {
        btnArchiveYear.setText(String.valueOf(selectedYear));
        tvArchiveScreenSubtitle.setText("Geçmiş elek kayıtları •");
        btnArchiveGardenFilter.setText(selectedGardenName);

        archiveCards.clear();

        Cursor cursor = null;
        double seasonTotalKg = 0;
        double seasonTotalRevenue = 0;
        int recordCount = 0;

        try {
            cursor = elekRepository.getArchiveRecords(
                    selectedYear,
                    selectedGardenId,
                    databaseHelper.getActiveOwnerUid()
            );

            int recordIdIndex = cursor.getColumnIndexOrThrow("record_id");
            int dateIndex = cursor.getColumnIndexOrThrow("record_date");
            int gardenIndex = cursor.getColumnIndexOrThrow("garden_name");
            int doubleIndex = cursor.getColumnIndexOrThrow("double_kg");
            int grade1Index = cursor.getColumnIndexOrThrow("grade1_kg");
            int grade2Index = cursor.getColumnIndexOrThrow("grade2_kg");
            int grade3Index = cursor.getColumnIndexOrThrow("grade3_kg");
            int grade4Index = cursor.getColumnIndexOrThrow("grade4_kg");
            int oilIndex = cursor.getColumnIndexOrThrow("oil_kg");
            int totalKgIndex = cursor.getColumnIndexOrThrow("total_kg");
            int totalRevenueIndex = cursor.getColumnIndexOrThrow("total_revenue");

            while (cursor.moveToNext()) {
                long recordId = cursor.getLong(recordIdIndex);
                String date = cursor.getString(dateIndex);
                String gardenName = cursor.getString(gardenIndex);
                double totalKg = cursor.getDouble(totalKgIndex);
                double totalRevenue = cursor.getDouble(totalRevenueIndex);
                double average = totalKg > 0 ? totalRevenue / totalKg : 0;

                double[] kgValues = new double[]{
                        cursor.getDouble(doubleIndex),
                        cursor.getDouble(grade1Index),
                        cursor.getDouble(grade2Index),
                        cursor.getDouble(grade3Index),
                        cursor.getDouble(grade4Index),
                        cursor.getDouble(oilIndex)
                };

                seasonTotalKg += totalKg;
                seasonTotalRevenue += totalRevenue;
                recordCount++;

                archiveCards.add(buildArchiveCard(
                        recordId,
                        date,
                        gardenName,
                        kgValues,
                        totalKg,
                        totalRevenue,
                        average
                ));
            }
        } catch (Exception e) {
            Toast.makeText(this, "Elek arşivi okunamadı.", Toast.LENGTH_LONG).show();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (recordCount == 0) {
            tvArchiveEmpty.setText("Bu seçim için henüz Elek kaydı yok.");
            tvArchiveEmpty.setVisibility(View.VISIBLE);
        } else {
            tvArchiveEmpty.setVisibility(View.GONE);
        }

        double seasonAverage = seasonTotalKg > 0 ? seasonTotalRevenue / seasonTotalKg : 0;
        tvArchiveScreenTotalKg.setText(formatNumber(seasonTotalKg));
        tvArchiveScreenTotalRevenue.setText(formatNumber(seasonTotalRevenue));
        tvArchiveScreenAverage.setText(formatAverage(seasonAverage));

        archiveAdapter.notifyDataSetChanged();
    }

    private ArchiveCard buildArchiveCard(long recordId,
                                         String date,
                                         String gardenName,
                                         double[] kgValues,
                                         double totalKg,
                                         double totalRevenue,
                                         double average) {
        ArchiveCard card = new ArchiveCard();
        card.recordId = recordId;
        card.date = formatTableDate(date);
        card.day = formatDay(date);
        card.gardenName = gardenName;
        card.totalKg = formatKg(totalKg);
        card.totalKgCompact = formatNumber(totalKg) + " kg";
        card.totalRevenue = money(totalRevenue);
        card.totalRevenueCompact = formatCompactMoney(totalRevenue);
        card.average = formatAverage(average) + " TL/kg";

        for (int i = 0; i < GRADE_KEYS.length; i++) {
            GradeLine line = new GradeLine();
            line.badge = GRADE_BADGES[i];
            line.label = GRADE_LABELS[i];
            line.kgValue = i < kgValues.length ? kgValues[i] : 0;
            card.lines.add(line);
        }

        Cursor gradesCursor = null;
        try {
            gradesCursor = elekRepository.getElekGrades(recordId);
            int gradeIndex = gradesCursor.getColumnIndexOrThrow("grade");
            int kgIndex = gradesCursor.getColumnIndexOrThrow("kg");
            int priceIndex = gradesCursor.getColumnIndexOrThrow("price_per_kg");
            int lineTotalIndex = gradesCursor.getColumnIndexOrThrow("line_total");
            while (gradesCursor.moveToNext()) {
                String grade = gradesCursor.getString(gradeIndex);
                int index = gradeIndexOf(grade);
                if (index < 0 || index >= card.lines.size()) {
                    continue;
                }
                GradeLine line = card.lines.get(index);
                line.kgValue = gradesCursor.getDouble(kgIndex);
                line.pricePerKgValue = gradesCursor.getDouble(priceIndex);
                line.lineTotalValue = gradesCursor.getDouble(lineTotalIndex);
            }
        } catch (Exception ignored) {
        } finally {
            if (gradesCursor != null) {
                gradesCursor.close();
            }
        }

        for (GradeLine line : card.lines) {
            line.kg = formatKg(line.kgValue);
            line.price = formatPriceCell(line.pricePerKgValue);
            line.total = formatCompactMoneyOrDash(line.lineTotalValue);
        }

        return card;
    }

    private int gradeIndexOf(String key) {
        if (key == null) {
            return -1;
        }
        for (int i = 0; i < GRADE_KEYS.length; i++) {
            if (GRADE_KEYS[i].equalsIgnoreCase(key)) {
                return i;
            }
        }
        return -1;
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

            ArchiveCard card = archiveCards.get(position);
            holder.garden.setText(card.gardenName);
            holder.date.setText(card.date);
            holder.day.setText(card.day);
            holder.revenue.setText(card.totalRevenue);
            holder.totalKg.setText(card.totalKgCompact);
            holder.average.setText(card.average);
            holder.tableTotalKg.setText(card.totalKg);
            holder.tableTotalRevenue.setText(card.totalRevenueCompact);

            for (int i = 0; i < holder.gradeBanners.length && i < card.lines.size(); i++) {
                GradeLine line = card.lines.get(i);
                holder.gradeBanners[i].setText(line.badge);
                holder.gradeLabels[i].setText(line.label);
                holder.gradeKgs[i].setText(line.kg);
                holder.gradePrices[i].setText(line.price);
                holder.gradeTotals[i].setText(line.total);
            }

            holder.detailButton.setOnClickListener(v -> showRecordDetails(card));
            return convertView;
        }
    }

    private void showRecordDetails(ArchiveCard card) {
        if (card == null) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Tarih: ")
                .append(card.date)
                .append(" • ")
                .append(card.day)
                .append("\n\n");

        for (GradeLine line : card.lines) {
            builder.append(line.label)
                    .append(": ")
                    .append(line.kg)
                    .append(" | ")
                    .append(line.price)
                    .append(" | ")
                    .append(line.total)
                    .append("\n");
        }

        builder.append("\nToplam Kg: ")
                .append(card.totalKgCompact)
                .append("\nToplam Tutar: ")
                .append(card.totalRevenue)
                .append("\nOrtalama Fiyat: ")
                .append(card.average);

        new AlertDialog.Builder(this)
                .setTitle(card.gardenName)
                .setMessage(builder.toString())
                .setPositiveButton("Kapat", null)
                .show();
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

    private String formatKg(double value) {
        return formatNumber(value) + " kg";
    }

    private String formatPriceCell(double value) {
        if (value <= 0) {
            return "—";
        }
        return formatNumber(value) + "₺";
    }

    private String formatCompactMoney(double value) {
        return formatNumber(value) + "₺";
    }

    private String formatCompactMoneyOrDash(double value) {
        if (value <= 0) {
            return "—";
        }
        return formatCompactMoney(value);
    }

    private String formatAverage(double value) {
        if (averageNumberFormat == null) {
            return String.format(turkishLocale, "%.2f", value);
        }
        return averageNumberFormat.format(value);
    }

    private String formatNumber(double value) {
        if (numberFormat == null) {
            return String.valueOf(value);
        }
        return numberFormat.format(value);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (databaseHelper != null && archiveRecordList != null) {
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

    private static final class GradeLine {
        String badge;
        String label;
        double kgValue;
        double pricePerKgValue;
        double lineTotalValue;
        String kg;
        String price;
        String total;
    }

    private static final class ArchiveCard {
        long recordId;
        String gardenName;
        String date;
        String day;
        String totalKg;
        String totalKgCompact;
        String totalRevenue;
        String totalRevenueCompact;
        String average;
        final List<GradeLine> lines = new ArrayList<>();
    }

    private static final class ArchiveViewHolder {
        final TextView garden;
        final TextView date;
        final TextView day;
        final TextView revenue;
        final TextView totalKg;
        final TextView average;
        final TextView tableTotalKg;
        final TextView tableTotalRevenue;
        final View detailButton;
        final TextView[] gradeBanners = new TextView[6];
        final TextView[] gradeLabels = new TextView[6];
        final TextView[] gradeKgs = new TextView[6];
        final TextView[] gradePrices = new TextView[6];
        final TextView[] gradeTotals = new TextView[6];

        ArchiveViewHolder(View root) {
            garden = root.findViewById(R.id.archiveRecordGarden);
            date = root.findViewById(R.id.archiveRecordDate);
            day = root.findViewById(R.id.archiveRecordDay);
            revenue = root.findViewById(R.id.archiveRecordRevenue);
            totalKg = root.findViewById(R.id.archiveRecordKgSide);
            average = root.findViewById(R.id.archiveRecordAverage);
            tableTotalKg = root.findViewById(R.id.archiveRecordKg);
            tableTotalRevenue = root.findViewById(R.id.archiveRecordRevenueTable);
            detailButton = root.findViewById(R.id.archiveDetailButton);

            gradeBanners[0] = root.findViewById(R.id.archiveBadge0);
            gradeBanners[1] = root.findViewById(R.id.archiveBadge1);
            gradeBanners[2] = root.findViewById(R.id.archiveBadge2);
            gradeBanners[3] = root.findViewById(R.id.archiveBadge3);
            gradeBanners[4] = root.findViewById(R.id.archiveBadge4);
            gradeBanners[5] = root.findViewById(R.id.archiveBadge5);

            gradeLabels[0] = root.findViewById(R.id.archiveLabel0);
            gradeLabels[1] = root.findViewById(R.id.archiveLabel1);
            gradeLabels[2] = root.findViewById(R.id.archiveLabel2);
            gradeLabels[3] = root.findViewById(R.id.archiveLabel3);
            gradeLabels[4] = root.findViewById(R.id.archiveLabel4);
            gradeLabels[5] = root.findViewById(R.id.archiveLabel5);

            gradeKgs[0] = root.findViewById(R.id.archiveGrade0);
            gradeKgs[1] = root.findViewById(R.id.archiveGrade1);
            gradeKgs[2] = root.findViewById(R.id.archiveGrade2);
            gradeKgs[3] = root.findViewById(R.id.archiveGrade3);
            gradeKgs[4] = root.findViewById(R.id.archiveGrade4);
            gradeKgs[5] = root.findViewById(R.id.archiveGrade5);

            gradePrices[0] = root.findViewById(R.id.archivePrice0);
            gradePrices[1] = root.findViewById(R.id.archivePrice1);
            gradePrices[2] = root.findViewById(R.id.archivePrice2);
            gradePrices[3] = root.findViewById(R.id.archivePrice3);
            gradePrices[4] = root.findViewById(R.id.archivePrice4);
            gradePrices[5] = root.findViewById(R.id.archivePrice5);

            gradeTotals[0] = root.findViewById(R.id.archiveTotal0);
            gradeTotals[1] = root.findViewById(R.id.archiveTotal1);
            gradeTotals[2] = root.findViewById(R.id.archiveTotal2);
            gradeTotals[3] = root.findViewById(R.id.archiveTotal3);
            gradeTotals[4] = root.findViewById(R.id.archiveTotal4);
            gradeTotals[5] = root.findViewById(R.id.archiveTotal5);
        }
    }
}
