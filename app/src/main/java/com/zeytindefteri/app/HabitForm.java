package com.zeytindefteri.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * Formda sezilen bilgi (İP5). Yüksek güven alanı doldurur ve alanın bölümünün hemen altına kısa gerekçe ile
 * "Bu yanlış" yazar; orta güven yalnız öneri çipi gösterir (dokununca doldurur). Sezilen bilgi yoksa hiçbir şey
 * eklenmez: formun düzeni ve düğmelerin yeri değişmez. Alan doluysa (cümleden, öğretilen sözden, çiftçiden)
 * dokunulmaz (HabitModel.forEmpty). Kendiliğinden kayıt yok; Kaydet'e çiftçi basar.
 *
 * "Bu yanlış": alan boşalır, imleç o alana geçer, alanda soluk "Doğrusu ne?" yazar (ayrı pencere yok); değer bir
 * sonraki kayda kadar önerilmez, tür 5 kez üst üste reddedilirse kapanır (HabitCorrections). Karneye yalnız sayılar.
 */
final class HabitForm {
    static final String WHAT_IS_RIGHT = "Doğrusu ne?";

    /** Bir alanda gösterilen öneri. */
    private static final class Shown {
        final HabitModel.Habit habit;
        final View field;
        final String filled;
        View row;
        boolean wrong;
        boolean taken;

        Shown(HabitModel.Habit habit, View field, String filled) {
            this.habit = habit;
            this.field = field;
            this.filled = filled;
        }
    }

    private final Activity activity;
    private final ViewGroup content;
    private final String owner;
    private final HabitCorrections.Store corrections;
    private final List<Shown> shown = new ArrayList<>();

    /** content: kaydırılan form içeriği (gerekçe satırı alanın bölümünden sonra buraya eklenir). */
    HabitForm(Activity activity, ViewGroup content, String owner, HabitCorrections.Store corrections) {
        this.activity = activity;
        this.content = content;
        this.owner = owner == null ? "" : owner;
        this.corrections = corrections;
    }

    /** Metin alanı: yüksek güvende doldurur, ortada çip. Alan doluysa hiçbir şey yapmaz. */
    void text(EditText field, HabitModel.Habit habit, String value) {
        text(field, habit, value, null);
    }

    /** afterWrong: "Bu yanlış" denince ayrıca yapılacak (alıcı yanlışsa o alıcının fiyatları da kalkar). */
    void text(EditText field, HabitModel.Habit habit, String value, Runnable afterWrong) {
        text(field, habit, value, afterWrong, null);
    }

    /** alsoFill: alan dolunca ayrıca (miktarla birlikte birim seçimi). */
    void text(EditText field, HabitModel.Habit habit, String value, Runnable afterWrong, Runnable alsoFill) {
        HabitModel.Habit h = HabitModel.forEmpty(field.getText().toString(), habit);
        if (h == null || content == null) {
            return;
        }
        Shown s = new Shown(h, field, value);
        if (h.level == HabitModel.Level.HIGH) {
            field.setText(value);
            if (alsoFill != null) {
                alsoFill.run();
            }
            s.row = reasonRow(s, () -> {
                field.setText("");
                field.setHint(WHAT_IS_RIGHT);
                focus(field);
                if (afterWrong != null) {
                    afterWrong.run();
                }
            });
        } else {
            s.row = chip(s, () -> {
                field.setText(value);
                if (alsoFill != null) {
                    alsoFill.run();
                }
            });
        }
        add(s);
    }

    /** Seçim kutusu (bahçe): yüksek güvende seçer; "Bu yanlış" önceki seçime döner. */
    void spinner(Spinner field, int position, HabitModel.Habit habit) {
        if (habit == null || content == null || position < 0) {
            return;
        }
        int before = field.getSelectedItemPosition();
        Object item = field.getAdapter() == null ? null : field.getAdapter().getItem(position);
        Shown s = new Shown(habit, field, item == null ? habit.value : item.toString());
        if (habit.level == HabitModel.Level.HIGH) {
            field.setSelection(position);
            s.row = reasonRow(s, () -> field.setSelection(Math.max(0, before)));
        } else {
            s.row = chip(s, () -> field.setSelection(position));
        }
        add(s);
    }

    /** Türü değişen form: bu türün önerileri kalkar; hâlâ önerinin değerini taşıyan alan boşalır. */
    void clear(String whatPrefix) {
        List<Shown> gone = new ArrayList<>();
        for (Shown s : shown) {
            if (!s.habit.what.startsWith(whatPrefix)) {
                continue;
            }
            if (s.field instanceof EditText && s.filled.equals(((EditText) s.field).getText().toString()) && !s.wrong) {
                ((EditText) s.field).setText("");
            }
            remove(s);
            gone.add(s);
        }
        shown.removeAll(gone);
    }

    /** Kaydet anı: önerinin değeri alanda kaldıysa kabul (karne + üst üste red sayısı sıfırlanır). */
    void saved() {
        for (Shown s : shown) {
            if (s.wrong) {
                continue;
            }
            boolean filledNow = s.habit.level == HabitModel.Level.HIGH || s.taken;
            if (filledNow && s.filled.equals(current(s.field))) {
                corrections.accept(s.habit.what);
                AssistantMeter.suggestion(activity, owner, AssistantScorecard.ACCEPTED, s.habit.category());
            }
        }
    }

    // ------------------------------------------------------------ görünüm

    private void add(Shown s) {
        shown.add(s);
        insertBelow(s.field, s.row);
        AssistantMeter.suggestion(activity, owner, AssistantScorecard.SHOWN, s.habit.category());
    }

    /** Kısa gerekçe + "Bu yanlış". */
    private View reasonRow(Shown s, Runnable undo) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(2), 0, dp(6));
        TextView why = new TextView(activity);
        why.setText(s.habit.reason());
        why.setTextSize(12);
        why.setTextColor(GreenTheme.color(R.color.zd_muted));
        row.addView(why, new LinearLayout.LayoutParams(0, -2, 1));
        TextView wrong = new TextView(activity);
        wrong.setText("Bu yanlış");
        wrong.setTextSize(13);
        wrong.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        wrong.setTextColor(GreenTheme.color(R.color.zd_green));
        wrong.setGravity(Gravity.CENTER);
        wrong.setMinHeight(dp(40));
        wrong.setPadding(dp(12), 0, dp(4), 0);
        wrong.setContentDescription("Bu yanlış: " + s.habit.shown);
        wrong.setOnClickListener(v -> {
            s.wrong = true;
            undo.run();
            corrections.reject(s.habit.what, s.habit.value, System.currentTimeMillis());
            AssistantMeter.suggestion(activity, owner, AssistantScorecard.WRONG, s.habit.category());
            remove(s);
        });
        row.addView(wrong, new LinearLayout.LayoutParams(-2, -2));
        return row;
    }

    /** Orta güven: yalnız öneri çipi; alanı doldurmaz, dokununca doldurur. */
    private View chip(Shown s, Runnable take) {
        TextView chip = new TextView(activity);
        chip.setText("Öneri: " + s.habit.shown);
        chip.setTextSize(13);
        chip.setTextColor(GreenTheme.color(R.color.zd_green));
        chip.setMinHeight(dp(40));
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), GreenTheme.color(R.color.zd_green));
        chip.setBackground(bg);
        chip.setContentDescription("Öneri: " + s.habit.shown + ". " + s.habit.reason());
        chip.setOnClickListener(v -> {
            s.taken = true;
            take.run();
            remove(s);
        });
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setPadding(dp(4), dp(2), 0, dp(6));
        wrap.addView(chip, new LinearLayout.LayoutParams(-2, -2));
        return wrap;
    }

    private void remove(Shown s) {
        if (s.row != null && s.row.getParent() instanceof ViewGroup) {
            ((ViewGroup) s.row.getParent()).removeView(s.row);
        }
    }

    /** Alanın, kaydırılan içerikteki bölümünün hemen altına (tam genişlik). */
    private void insertBelow(View field, View row) {
        View child = field;
        while (child != null && child.getParent() != content) {
            child = child.getParent() instanceof View ? (View) child.getParent() : null;
        }
        if (child == null) {
            return;
        }
        content.addView(row, content.indexOfChild(child) + 1, new LinearLayout.LayoutParams(-1, -2));
    }

    private void focus(EditText field) {
        if (!field.isFocusable()) {
            return;
        }
        field.requestFocus();
        InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (keyboard != null) {
            keyboard.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private static String current(View field) {
        if (field instanceof EditText) {
            return ((EditText) field).getText().toString();
        }
        if (field instanceof Spinner && ((Spinner) field).getSelectedItem() != null) {
            return ((Spinner) field).getSelectedItem().toString();
        }
        return "";
    }

    private int dp(int v) {
        return Math.round(v * activity.getResources().getDisplayMetrics().density);
    }
}
