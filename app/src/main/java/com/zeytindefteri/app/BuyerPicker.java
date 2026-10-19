package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/**
 * "Kime sattın?" alttan açılan seçim penceresi. Alıcılar BuyerDirectory'den gelir; arama ad ve bölgede yapılır.
 * Yeni alıcı ad (aramadaki metinle dolu), telefon ve yer ile eklenir; aynı ad varsa var olan seçilir.
 */
public final class BuyerPicker {
    public interface Callback { void picked(String name); }

    private BuyerPicker() {}

    /**
     * @param skipLabel boş değilse alıcısız devam etmeyi sağlayan düğmenin yazısı (seçim "" döner).
     */
    public static void show(Activity activity, DatabaseHelper databaseHelper, String current,
                            String skipLabel, Callback callback) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        final String owner = databaseHelper.getActiveOwnerUid();
        final List<BuyerDirectory.Summary> all;
        try {
            all = BuyerDirectory.recentFirst(databaseHelper.getReadableDatabase(), owner,
                    databaseHelper.getActiveSeasonYear());
        } catch (RuntimeException e) {
            Toast.makeText(activity, "Alıcılar okunamadı.", Toast.LENGTH_SHORT).show();
            return;
        }
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(activity, 18), dp(activity, 10), dp(activity, 18), dp(activity, 18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(GreenTheme.color(R.color.zd_card));
        float r = dp(activity, 26);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(bg);

        View handle = new View(activity);
        handle.setBackground(round(activity, GreenTheme.color(R.color.zd_border), 0, 3));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 5));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin = dp(activity, 10);
        sheet.addView(handle, hp);

        LinearLayout head = new LinearLayout(activity);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(activity, "Kime sattın?", 22, GreenTheme.color(R.color.zd_text), true);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = text(activity, "×", 28, GreenTheme.color(R.color.zd_muted), false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Kapat");
        close.setOnClickListener(v -> dialog.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
        sheet.addView(head);

        final EditText search = new EditText(activity);
        search.setHint("Alıcı ara (ad ya da yer)");
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        search.setTextSize(17);
        search.setTypeface(Typeface.DEFAULT_BOLD);
        search.setTextColor(GreenTheme.color(R.color.zd_text));
        search.setHintTextColor(GreenTheme.color(R.color.zd_muted));
        search.setBackground(round(activity, GreenTheme.color(R.color.zd_bg), GreenTheme.color(R.color.zd_border), 16));
        search.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(activity, 52));
        sp.topMargin = dp(activity, 6);
        sheet.addView(search, sp);

        final TextView caption = text(activity, "", 13, GreenTheme.color(R.color.zd_muted), true);
        caption.setLetterSpacing(0.06f);
        caption.setPadding(0, dp(activity, 14), 0, dp(activity, 6));
        sheet.addView(caption);

        final LinearLayout listBox = new LinearLayout(activity);
        listBox.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(listBox, new ScrollView.LayoutParams(-1, -2));
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, dp(activity, 300)));

        final TextView add = text(activity, "+ Yeni alıcı ekle", 16, GreenTheme.color(R.color.zd_green), true);
        add.setGravity(Gravity.CENTER);
        add.setBackground(round(activity, Color.TRANSPARENT, GreenTheme.color(R.color.zd_green), 16));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(activity, 54));
        ap.topMargin = dp(activity, 12);
        sheet.addView(add, ap);

        if (skipLabel != null && !skipLabel.isEmpty()) {
            TextView skip = text(activity, skipLabel, 15, GreenTheme.color(R.color.zd_text_soft), true);
            skip.setGravity(Gravity.CENTER);
            skip.setOnClickListener(v -> { dialog.dismiss(); callback.picked(""); });
            LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(-1, dp(activity, 48));
            kp.topMargin = dp(activity, 6);
            sheet.addView(skip, kp);
        }

        final String selected = BuyerDirectory.normalize(current);
        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            String q = search.getText().toString();
            List<BuyerDirectory.Summary> shown = BuyerDirectory.filter(all, q);
            listBox.removeAllViews();
            String typed = BuyerDirectory.clean(q);
            if (all.isEmpty()) caption.setText("HENÜZ ALICI YOK · AŞAĞIDAN EKLE");
            else if (typed.isEmpty()) caption.setText("SON SATTIKLARIN");
            else caption.setText(shown.size() + " SONUÇ");
            add.setText(typed.isEmpty() ? "+ Yeni alıcı ekle" : "+ \"" + typed + "\" adıyla yeni alıcı ekle");
            int limit = Math.min(shown.size(), 60);
            for (int i = 0; i < limit; i++) {
                final BuyerDirectory.Summary s = shown.get(i);
                View row = buyerRow(activity, s, selected.equals(BuyerDirectory.normalize(s.name)));
                row.setOnClickListener(v -> { dialog.dismiss(); callback.picked(s.name); });
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                rp.bottomMargin = dp(activity, 8);
                listBox.addView(row, rp);
            }
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { render[0].run(); }
        });
        add.setOnClickListener(v -> askNewBuyer(activity, databaseHelper, BuyerDirectory.clean(search.getText().toString()), name -> {
            dialog.dismiss();
            callback.picked(name);
        }));
        render[0].run();

        dialog.setContentView(sheet);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
    }

    /** Yeni alıcı formu: ad zorunlu, telefon ve yer isteğe bağlı. Aynı ad varsa var olan kullanılır. */
    static void askNewBuyer(Activity activity, DatabaseHelper databaseHelper, String presetName, Callback callback) {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(activity, 20), dp(activity, 8), dp(activity, 20), 0);
        final EditText name = field(activity, "Ad (ör. Hasan Usta)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setText(presetName == null ? "" : presetName);
        final EditText phone = field(activity, "Telefon (isteğe bağlı)", InputType.TYPE_CLASS_PHONE);
        final EditText place = field(activity, "Yer (isteğe bağlı, ör. Edremit)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        form.addView(name);
        form.addView(phone);
        form.addView(place);
        final AlertDialog d = new AlertDialog.Builder(activity)
                .setTitle("Yeni alıcı")
                .setView(form)
                .setPositiveButton("Ekle", null)
                .setNegativeButton("Vazgeç", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = BuyerDirectory.clean(name.getText().toString());
            if (n.isEmpty()) { name.setError("Alıcının adını yaz"); return; }
            long id;
            try {
                id = BuyerDirectory.findOrCreate(databaseHelper.getWritableDatabase(), databaseHelper.getActiveOwnerUid(),
                        n, phone.getText().toString(), place.getText().toString(), "Alıcılarım'dan eklendi");
            } catch (RuntimeException e) { id = -1; }
            if (id <= 0) { Toast.makeText(activity, "Alıcı eklenemedi.", Toast.LENGTH_SHORT).show(); return; }
            d.dismiss();
            callback.picked(n);
        }));
        d.show();
    }

    static View buyerRow(Activity a, BuyerDirectory.Summary s, boolean selected) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(a, 64));
        row.setPadding(dp(a, 14), dp(a, 8), dp(a, 14), dp(a, 8));
        row.setBackground(round(a, selected ? GreenTheme.color(R.color.zd_green_soft) : GreenTheme.color(R.color.zd_surface),
                selected ? GreenTheme.color(R.color.zd_green) : GreenTheme.color(R.color.zd_border), 16));
        row.setClickable(true);
        row.setFocusable(true);
        TextView avatar = text(a, initial(s.name), 16, GreenTheme.color(R.color.zd_green), true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(round(a, GreenTheme.color(R.color.zd_green_chip), 0, 20));
        row.addView(avatar, new LinearLayout.LayoutParams(dp(a, 40), dp(a, 40)));
        LinearLayout copy = new LinearLayout(a);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(a, 12), 0, 0, 0);
        copy.addView(text(a, s.name, 17, GreenTheme.color(R.color.zd_text), true));
        String sub = subtitle(s);
        if (!sub.isEmpty()) copy.addView(text(a, sub, 14, GreenTheme.color(R.color.zd_muted), false));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        if (selected) row.addView(text(a, "✓", 20, GreenTheme.color(R.color.zd_green), true));
        row.setContentDescription(s.name + (sub.isEmpty() ? "" : ", " + sub) + (selected ? ", seçili" : ""));
        return row;
    }

    static String subtitle(BuyerDirectory.Summary s) {
        StringBuilder b = new StringBuilder();
        if (!s.region.isEmpty()) b.append(s.region);
        String last = s.lastSaleDate.isEmpty() ? "henüz satış yok" : "son satış " + BuyerDirectory.shortDate(s.lastSaleDate);
        if (b.length() > 0) b.append(" · ");
        b.append(last);
        return b.toString();
    }

    static String initial(String name) {
        String n = BuyerDirectory.clean(name);
        return n.isEmpty() ? "?" : n.substring(0, 1).toUpperCase(BuyerDirectory.TR);
    }

    static EditText field(Activity a, String hint, int type) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setTextSize(17);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        e.setLayoutParams(p);
        return e;
    }

    static TextView text(Activity a, String value, int size, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static GradientDrawable round(Activity a, int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(a, radiusDp));
        if (stroke != 0) g.setStroke(Math.max(1, dp(a, 1)), stroke);
        return g;
    }

    static int dp(Activity a, int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }
}
