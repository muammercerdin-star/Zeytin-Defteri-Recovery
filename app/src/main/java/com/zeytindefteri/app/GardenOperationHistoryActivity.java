package com.zeytindefteri.app;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

/** Read-only complete active-season history for one owned garden. */
public class GardenOperationHistoryActivity extends Activity {
    private DatabaseHelper databaseHelper;
    private long gardenId;
    private String screenOwner;
    private TextView heading;
    private final ArrayList<String[]> rows = new ArrayList<>();
    private HistoryAdapter adapter;
    private final NumberFormat number = NumberFormat.getNumberInstance(new Locale("tr", "TR"));

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getActionBar() != null) getActionBar().hide();
        getWindow().setStatusBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().setNavigationBarColor(GreenTheme.color(R.color.zd_bg));
        getWindow().getDecorView().setSystemUiVisibility(0);
        databaseHelper = new DatabaseHelper(this);
        gardenId = getIntent().getLongExtra("garden_id", -1);
        screenOwner = state == null ? databaseHelper.getActiveOwnerUid() : state.getString("screen_owner", "");
        number.setMaximumFractionDigits(2);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(12));
        root.setBackgroundColor(GreenTheme.color(R.color.zd_bg));
        TextView back = text("‹  Bahçe Detayı", 16);
        back.setMinHeight(dp(48)); back.setGravity(Gravity.CENTER_VERTICAL);
        back.setContentDescription("Bahçe detayına geri dön");
        back.setOnClickListener(v -> finish()); root.addView(back);
        TextView title = text("Tüm İşlem Geçmişi", 24);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        root.addView(title);
        heading = text("", 14); heading.setPadding(0, dp(10), 0, dp(14)); root.addView(heading);
        TextView empty = text("Bu sezon bu bahçe için henüz işlem kaydı yok.", 16);
        empty.setGravity(Gravity.CENTER); root.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
        ListView list = new ListView(this);
        adapter = new HistoryAdapter(); list.setAdapter(adapter); list.setEmptyView(empty);
        list.setDividerHeight(dp(10)); root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        rows.clear();
        if (!screenOwner.equals(databaseHelper.getActiveOwnerUid())) { finish(); return; }
        try (Cursor garden = databaseHelper.getGarden(gardenId)) {
            if (!garden.moveToFirst()) {
                Toast.makeText(this, "Bahçe bulunamadı", Toast.LENGTH_SHORT).show(); finish(); return;
            }
            heading.setText(garden.getString(garden.getColumnIndexOrThrow("name"))
                    + " • " + databaseHelper.getActiveSeasonYear() + " sezonu");
        }
        try (Cursor cursor = databaseHelper.getGardenOperations(gardenId, 0)) {
            while (cursor.moveToNext()) {
                String details = value(cursor, "operation_date");
                int quantityColumn = cursor.getColumnIndexOrThrow("quantity");
                int costColumn = cursor.getColumnIndexOrThrow("cost");
                if (!cursor.isNull(quantityColumn)) details += " • "
                        + number.format(cursor.getDouble(quantityColumn)) + " " + value(cursor, "unit");
                if (!cursor.isNull(costColumn)) details += " • "
                        + number.format(cursor.getDouble(costColumn)) + " TL";
                rows.add(new String[]{value(cursor, "type"), details, value(cursor, "note")});
            }
        }
        adapter.notifyDataSetChanged();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putString("screen_owner", screenOwner);
    }
    @Override protected void onDestroy() {
        if (databaseHelper != null) databaseHelper.close(); super.onDestroy();
    }
    private String value(Cursor cursor, String column) {
        String value = cursor.getString(cursor.getColumnIndexOrThrow(column)); return value == null ? "" : value;
    }
    private TextView text(String value, int size) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size);
        text.setTextColor(GreenTheme.color(R.color.zd_text)); return text;
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private class HistoryAdapter extends BaseAdapter {
        public int getCount() { return rows.size(); }
        public Object getItem(int position) { return rows.get(position); }
        public long getItemId(int position) { return position; }
        public View getView(int position, View recycled, ViewGroup parent) {
            LinearLayout card;
            if (recycled instanceof LinearLayout) card = (LinearLayout) recycled;
            else {
                card = new LinearLayout(GardenOperationHistoryActivity.this);
                card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(14), dp(14), dp(14), dp(14));
                GradientDrawable background = new GradientDrawable();
                background.setColor(GreenTheme.color(R.color.zd_card));
                background.setCornerRadius(dp(14));
                background.setStroke(dp(1), GreenTheme.color(R.color.zd_border));
                card.setBackground(background);
                TextView title = text("", 17);
                title.setTypeface(null, Typeface.BOLD);
                title.setTextColor(GreenTheme.color(R.color.zd_green));
                TextView detail = text("", 13);
                detail.setTextColor(GreenTheme.color(R.color.zd_muted));
                detail.setPadding(0, dp(5), 0, dp(5));
                card.addView(title); card.addView(detail); card.addView(text("", 14));
            }
            String[] data = rows.get(position);
            for (int i = 0; i < data.length; i++) {
                TextView text = (TextView) card.getChildAt(i); text.setText(data[i]);
                text.setVisibility(i == 2 && data[i].isEmpty() ? View.GONE : View.VISIBLE);
            }
            return card;
        }
    }
}
