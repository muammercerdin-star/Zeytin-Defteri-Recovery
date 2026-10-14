package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * İşlem geçmişi. Tüm bahçeler veya tek bahçe (garden_id ile açılınca, Bahçe detayındaki "Tümü ›").
 * Kayıtlar aktif sezon ve sahip filtresiyle DatabaseHelper.getOperationsFiltered'dan gelir; tür filtresi,
 * sıralama ve ay grupları OperationHistoryModel'dedir. Satıra dokununca alttan detay paneli açılır.
 */
public class OperationHistoryActivity extends Activity {

    private DatabaseHelper databaseHelper;
    private String screenOwner = "";

    private final List<Integer> gardenIds = new ArrayList<>();
    private final List<String> gardenNames = new ArrayList<>();
    private int selectedGarden = -1;          // -1 = tüm bahçeler
    private String selectedType = OperationHistoryModel.ALL;

    private TextView tvSubtitle, tvRecordCount, tvFilteredCost, btnGardenFilter;
    private LinearLayout containerOperations;
    private final List<TextView> typeChips = new ArrayList<>();
    private Dialog detailSheet;

    private final int text = GreenTheme.color(R.color.zd_text);
    private final int muted = GreenTheme.color(R.color.zd_muted);
    private final int soft = GreenTheme.color(R.color.zd_text_soft);
    private final int border = GreenTheme.color(R.color.zd_border);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getActionBar() != null) getActionBar().hide();
        getWindow().setStatusBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().setNavigationBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().getDecorView().setSystemUiVisibility(0);
        databaseHelper = new DatabaseHelper(this);
        if (state == null) {
            screenOwner = databaseHelper.getActiveOwnerUid();
            selectedGarden = (int) getIntent().getLongExtra("garden_id", -1L);
        } else {
            screenOwner = state.getString("screen_owner", "");
            selectedGarden = state.getInt("history_garden", -1);
            selectedType = state.getString("history_type", OperationHistoryModel.ALL);
        }
        setContentView(createScreen());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isSameOwner()) { finish(); return; }
        loadGardens();
        refreshOperations();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("screen_owner", screenOwner);
        state.putInt("history_garden", selectedGarden);
        state.putString("history_type", selectedType);
    }

    @Override
    protected void onDestroy() {
        if (detailSheet != null && detailSheet.isShowing()) detailSheet.dismiss();
        if (databaseHelper != null) databaseHelper.close();
        super.onDestroy();
    }

    // ------------------------------------------------------------ ekran

    private View createScreen() {
        LinearLayout screen = column();
        screen.setBackgroundColor(GreenTheme.color(R.color.zd_bg));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(16), dp(16), dp(6));
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_elek_back_chevron);
        back.setColorFilter(text);
        back.setPadding(dp(11), dp(11), dp(11), dp(11));
        back.setContentDescription("Geri");
        back.setBackgroundResource(ripple(true));
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout titles = column();
        titles.setPadding(dp(8), 0, 0, 0);
        titles.addView(label("İşlem geçmişi", 22, text, true));
        tvSubtitle = label("", 12, muted, false);
        titles.addView(tvSubtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        screen.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = column();
        body.setPadding(dp(16), dp(6), dp(16), dp(20));
        scroll.addView(body);

        LinearLayout stats = new LinearLayout(this);
        tvRecordCount = label("0", 18, text, true);
        tvFilteredCost = label("0 TL", 18, text, true);
        stats.addView(statBox("İşlem sayısı", tvRecordCount), weight(0));
        stats.addView(statBox("Toplam masraf", tvFilteredCost), weight(8));
        body.addView(stats);

        btnGardenFilter = label("", 13, text, true);
        btnGardenFilter.setGravity(Gravity.CENTER_VERTICAL);
        btnGardenFilter.setPadding(dp(14), 0, dp(14), 0);
        btnGardenFilter.setBackgroundResource(R.drawable.bg_search);
        btnGardenFilter.setCompoundDrawablePadding(dp(8));
        btnGardenFilter.setCompoundDrawables(tinted(R.drawable.ic_op_leaf, 16, GreenTheme.color(R.color.zd_green)), null,
                tinted(R.drawable.ic_dropdown_arrow, 14, muted), null);
        btnGardenFilter.setOnClickListener(v -> pickGarden());
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-2, dp(40));
        gp.topMargin = dp(12);
        body.addView(btnGardenFilter, gp);

        HorizontalScrollView typeScroll = new HorizontalScrollView(this);
        typeScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout typeRow = new LinearLayout(this);
        List<String> filters = OperationHistoryModel.filters();
        for (int i = 0; i < filters.size(); i++) {
            final String f = filters.get(i);
            TextView chip = label(f, 13, 0, true);
            chip.setTextColor(getColorStateList(R.color.choice_text));
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(dp(14), 0, dp(14), 0);
            chip.setBackgroundResource(R.drawable.bg_choice_pill);
            if (!OperationHistoryModel.ALL.equals(f)) {
                chip.setCompoundDrawablePadding(dp(6));
                chip.setCompoundDrawables(tinted(LineIcons.forType(f), 16, LineIcons.toneColor(f)), null, null, null);
            }
            chip.setOnClickListener(v -> { selectedType = f; refreshOperations(); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(38));
            if (i > 0) p.leftMargin = dp(8);
            typeRow.addView(chip, p);
            typeChips.add(chip);
        }
        typeScroll.addView(typeRow);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(10);
        body.addView(typeScroll, tp);

        containerOperations = column();
        body.addView(containerOperations);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout footer = column();
        footer.setPadding(dp(16), dp(8), dp(16), dp(16));
        TextView add = label("+ İşlem ekle", 16, GreenTheme.color(R.color.zd_bg), true);
        add.setGravity(Gravity.CENTER);
        add.setBackgroundResource(R.drawable.bg_green_button);
        add.setOnClickListener(v -> openNewOperation());
        footer.addView(add, new LinearLayout.LayoutParams(-1, dp(54)));
        screen.addView(footer);
        return screen;
    }

    private View statBox(String name, TextView value) {
        LinearLayout box = column();
        box.setBackgroundResource(R.drawable.bg_tile);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        box.addView(label(name, 12, muted, false));
        value.setPadding(0, dp(2), 0, 0);
        box.addView(value);
        return box;
    }

    // ------------------------------------------------------------ bahçe filtresi

    private void loadGardens() {
        gardenIds.clear();
        gardenNames.clear();
        try (Cursor c = databaseHelper.getGardens()) {
            while (c.moveToNext()) {
                gardenIds.add(c.getInt(c.getColumnIndexOrThrow("id")));
                gardenNames.add(c.getString(c.getColumnIndexOrThrow("name")));
            }
        }
        if (selectedGarden != -1 && !gardenIds.contains(selectedGarden)) selectedGarden = -1;
    }

    private void pickGarden() {
        String[] items = new String[gardenNames.size() + 1];
        items[0] = "Tüm bahçeler";
        for (int i = 0; i < gardenNames.size(); i++) items[i + 1] = gardenNames.get(i);
        int checked = selectedGarden == -1 ? 0 : gardenIds.indexOf(selectedGarden) + 1;
        new AlertDialog.Builder(this)
                .setTitle("Bahçe")
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    selectedGarden = which == 0 ? -1 : gardenIds.get(which - 1);
                    d.dismiss();
                    refreshOperations();
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private String selectedGardenName() {
        int i = gardenIds.indexOf(selectedGarden);
        return i < 0 ? "Tüm bahçeler" : gardenNames.get(i);
    }

    // ------------------------------------------------------------ liste

    private void refreshOperations() {
        if (containerOperations == null) return;
        boolean allGardens = selectedGarden == -1;
        String gardenName = selectedGardenName();
        btnGardenFilter.setText(gardenName);
        btnGardenFilter.setContentDescription("Bahçe filtresi: " + gardenName + ". Değiştir");
        tvSubtitle.setText((allGardens ? "Bahçede yaptığın tüm işler" : gardenName) + " · "
                + databaseHelper.getActiveSeasonYear() + " sezonu");
        for (TextView chip : typeChips) {
            boolean on = chip.getText().toString().equals(selectedType);
            chip.setSelected(on);
            chip.setContentDescription(chip.getText() + (on ? ", seçili" : ""));
        }

        List<OperationHistoryModel.Row> rows = new ArrayList<>();
        try (Cursor c = databaseHelper.getOperationsFiltered(allGardens ? null : selectedGarden, null)) {
            int order = 0;
            while (c.moveToNext()) {
                String type = str(c, "type");
                if (!OperationHistoryModel.matches(selectedType, type)) continue;
                rows.add(new OperationHistoryModel.Row(c.getLong(c.getColumnIndexOrThrow("id")), type,
                        str(c, "garden_name"), str(c, "operation_date"),
                        c.getDouble(c.getColumnIndexOrThrow("quantity")), str(c, "unit"),
                        c.getDouble(c.getColumnIndexOrThrow("cost")), str(c, "note"), order++));
            }
        }
        OperationHistoryModel.sortNewestFirst(rows);

        double total = 0;
        for (OperationHistoryModel.Row r : rows) total += r.cost;
        tvRecordCount.setText(String.valueOf(rows.size()));
        tvFilteredCost.setText(OperationHistoryModel.money(total));

        containerOperations.removeAllViews();
        if (rows.isEmpty()) {
            TextView empty = label("Bu filtreye ait işlem kaydı bulunamadı.", 14, muted, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(10), dp(36), dp(10), dp(36));
            containerOperations.addView(empty);
            return;
        }
        int start = 0;
        while (start < rows.size()) {
            String month = OperationHistoryModel.monthLabel(rows.get(start).date);
            int end = start;
            double monthTotal = 0;
            while (end < rows.size() && OperationHistoryModel.monthLabel(rows.get(end).date).equals(month)) {
                monthTotal += rows.get(end).cost;
                end++;
            }
            LinearLayout card = column();
            card.setBackgroundResource(R.drawable.bg_card);
            card.setPadding(0, dp(4), 0, dp(4));
            LinearLayout head = new LinearLayout(this);
            head.setPadding(dp(16), dp(10), dp(16), dp(6));
            TextView title = label(month, 11, muted, true);
            title.setLetterSpacing(0.08f);
            head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            head.addView(label(OperationHistoryModel.groupSummary(end - start, monthTotal), 12, muted, false));
            card.addView(head);
            for (int i = start; i < end; i++) card.addView(operationRow(rows.get(i), i > start, allGardens));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
            p.topMargin = dp(12);
            containerOperations.addView(card, p);
            start = end;
        }
    }

    private View operationRow(final OperationHistoryModel.Row r, boolean line, boolean showGarden) {
        LinearLayout wrap = column();
        if (line) {
            View divider = new View(this);
            divider.setBackgroundColor(border);
            wrap.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(11), dp(12), dp(11));
        row.setBackgroundResource(ripple(false));
        row.addView(iconRing(r.type, 38, 18), new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout content = column();
        content.setPadding(dp(12), 0, dp(8), 0);
        String title = OperationKinds.label(r.type);
        String meta = OperationHistoryModel.meta(r, showGarden);
        content.addView(label(title, 14, text, true));
        if (!meta.isEmpty()) {
            TextView sub = label(meta, 12, muted, false);
            sub.setPadding(0, dp(1), 0, 0);
            content.addView(sub);
        }
        row.addView(content, new LinearLayout.LayoutParams(0, -2, 1));
        String cost = OperationHistoryModel.cost(r.cost);
        if (!cost.isEmpty()) row.addView(label(cost, 13, text, true));
        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_elek_back_chevron);
        chevron.setRotation(180f);
        chevron.setColorFilter(GreenTheme.color(R.color.zd_muted));
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(16), dp(16));
        cp.leftMargin = dp(6);
        row.addView(chevron, cp);
        row.setContentDescription(title + (meta.isEmpty() ? "" : ", " + meta) + (cost.isEmpty() ? "" : ", " + cost) + ". Detay");
        row.setOnClickListener(v -> showOperationDetail(r));
        wrap.addView(row);
        return wrap;
    }

    // ------------------------------------------------------------ detay paneli

    private void showOperationDetail(OperationHistoryModel.Row r) {
        LinearLayout sheet = column();
        sheet.setPadding(dp(18), dp(10), dp(18), dp(20));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(GreenTheme.color(R.color.zd_card));
        float rr = dp(24);
        bg.setCornerRadii(new float[]{rr, rr, rr, rr, 0, 0, 0, 0});
        sheet.setBackground(bg);

        View handle = new View(this);
        GradientDrawable hd = new GradientDrawable();
        hd.setColor(border);
        hd.setCornerRadius(dp(2));
        handle.setBackground(hd);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(40), dp(4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        sheet.addView(handle, hp);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(14), 0, dp(6));
        head.addView(iconRing(r.type, 44, 20), new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout titles = column();
        titles.setPadding(dp(12), 0, 0, 0);
        titles.addView(label(OperationKinds.label(r.type), 18, text, true));
        titles.addView(label("İşlem detayı", 12, muted, false));
        head.addView(titles);
        sheet.addView(head);

        sheet.addView(detailGrid("Bahçe", r.garden.isEmpty() ? "—" : r.garden, "Tarih", r.date.isEmpty() ? "—" : r.date));
        sheet.addView(detailGrid("Miktar", r.quantity > 0 ? OperationHistoryModel.number(r.quantity)
                        + (r.unit.trim().isEmpty() ? "" : " " + r.unit.trim()) : "—",
                "Masraf", r.cost > 0 ? OperationHistoryModel.money(r.cost) : "—"));
        TextView noteTitle = label("Not", 12, muted, false);
        noteTitle.setPadding(0, dp(14), 0, dp(2));
        sheet.addView(noteTitle);
        sheet.addView(label(r.note.trim().isEmpty() ? "—" : r.note.trim(), 14, soft, false));

        TextView close = label("Kapat", 14, soft, true);
        close.setGravity(Gravity.CENTER);
        close.setBackgroundResource(R.drawable.bg_search);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, dp(48));
        clp.topMargin = dp(18);
        sheet.addView(close, clp);

        detailSheet = new Dialog(this);
        detailSheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
        detailSheet.setContentView(sheet);
        close.setOnClickListener(v -> detailSheet.dismiss());
        Window w = detailSheet.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
        }
        detailSheet.show();
    }

    private View detailGrid(String n1, String v1, String n2, String v2) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(12), 0, 0);
        row.addView(detailCell(n1, v1), weight(0));
        row.addView(detailCell(n2, v2), weight(10));
        return row;
    }

    private View detailCell(String name, String value) {
        LinearLayout cell = column();
        cell.addView(label(name, 12, muted, false));
        TextView v = label(value, 16, text, true);
        v.setPadding(0, dp(2), 0, 0);
        cell.addView(v);
        return cell;
    }

    private void openNewOperation() {
        Intent intent = new Intent(this, NewOperationActivity.class);
        if (selectedGarden != -1) intent.putExtra("garden_id", (long) selectedGarden);
        startActivity(intent);
    }

    // ------------------------------------------------------------ yardımcılar

    private View iconRing(String type, int sizeDp, int iconDp) {
        FrameLayout ring = new FrameLayout(this);
        ring.setBackgroundResource(R.drawable.bg_icon_ring);
        ImageView icon = new ImageView(this);
        icon.setImageResource(LineIcons.forType(type));
        icon.setColorFilter(LineIcons.toneColor(type));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        ring.addView(icon, new FrameLayout.LayoutParams(dp(iconDp), dp(iconDp), Gravity.CENTER));
        return ring;
    }

    private android.graphics.drawable.Drawable tinted(int res, int sizeDp, int color) {
        android.graphics.drawable.Drawable d = getDrawable(res).mutate();
        d.setTint(color);
        d.setBounds(0, 0, dp(sizeDp), dp(sizeDp));
        return d;
    }

    private boolean isSameOwner() {
        if (databaseHelper == null) return false;
        String active = databaseHelper.getActiveOwnerUid();
        return (screenOwner == null ? "" : screenOwner).equals(active == null ? "" : active);
    }

    private static String str(Cursor c, String column) {
        int i = c.getColumnIndex(column);
        String v = i < 0 || c.isNull(i) ? "" : c.getString(i);
        return v == null ? "" : v;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout.LayoutParams weight(int leftMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.leftMargin = dp(leftMargin);
        return p;
    }

    private TextView label(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        if (color != 0) t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private int ripple(boolean borderless) {
        android.util.TypedValue v = new android.util.TypedValue();
        getTheme().resolveAttribute(borderless ? android.R.attr.selectableItemBackgroundBorderless
                : android.R.attr.selectableItemBackground, v, true);
        return v.resourceId;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
