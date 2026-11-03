package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.database.sqlite.SQLiteDatabase;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** Kayıtlı alım için telefon düzenleyicisi; web ile aynı Purchases.update yolunu kullanır. */
public final class TezgahPurchaseEditUi {
    private TezgahPurchaseEditUi() { }

    private static EditText field(Activity activity, LinearLayout box, String label, String value, boolean numeric) {
        TextView title = new TextView(activity);
        title.setText(label);
        box.addView(title);
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setText(value);
        if (numeric) input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(input);
        return input;
    }

    public static void show(Activity activity, SQLiteDatabase db, String owner, TezgahPurchases.Purchase purchase, Runnable changed) {
        if (purchase.zId != 0) return;
        TezgahModel original = TezgahModel.decode(purchase.data);
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
        form.setPadding(pad, pad, pad, pad);
        EditText name = field(activity, form, "Üretici", purchase.seller, false);
        EditText date = field(activity, form, "Tarih (YYYY-AA-GG)", purchase.date, false);
        EditText tare = field(activity, form, "Kasa darası (kg)", String.valueOf(original.tare()), true);
        List<EditText> prices = new ArrayList<>();
        for (int g = 0; g < 6; g++) prices.add(field(activity, form, TezgahModel.LABELS[g] + " fiyat", String.valueOf(original.priceArray()[g]), true));
        List<EditText> crates = new ArrayList<>();
        List<EditText> weights = new ArrayList<>();
        for (TezgahModel.Weighing row : original.items()) {
            crates.add(field(activity, form, TezgahModel.LABELS[row.grade] + " kasa", String.valueOf(row.kasa), true));
            weights.add(field(activity, form, "Brüt kilo", String.valueOf(row.kg), true));
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Alımı düzenle")
                .setMessage("Ödenen " + TezgahModel.tl(purchase.paid) + " korunur. Ödeme için ayrı ödeme ekranını kullan.")
                .setView(scroll).setPositiveButton("Kaydet", null).setNegativeButton("Vazgeç", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            boolean saved = false;
            try {
                String day = date.getText().toString().trim();
                java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
                format.setLenient(false);
                if (!day.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || day.compareTo(format.format(new java.util.Date())) > 0) throw new IllegalArgumentException("Tarih geçersiz.");
                format.parse(day);
                String seller = name.getText().toString().trim();
                if (!TezgahCrates.validParty(seller) || seller.length() > 100) throw new IllegalArgumentException("Üretici adını kontrol et.");
                TezgahModel model = new TezgahModel();
                double t = TezgahModel.parse(tare.getText().toString());
                if (!Double.isFinite(t) || t < 0 || t > TezgahModel.MAX_TARE) throw new IllegalArgumentException("Dara geçersiz.");
                model.setTare(t);
                for (int g = 0; g < 6; g++) {
                    double price = TezgahModel.parse(prices.get(g).getText().toString());
                    if (!Double.isFinite(price) || price < 0 || price > 100000) throw new IllegalArgumentException("Fiyat geçersiz.");
                    model.setPrice(g, price);
                }
                for (int i = 0; i < original.items().size(); i++) {
                    double count = TezgahModel.parse(crates.get(i).getText().toString());
                    double kg = TezgahModel.parse(weights.get(i).getText().toString());
                    String error = TezgahModel.addError((int) count, kg, t);
                    if (count != Math.floor(count) || !error.isEmpty()) throw new IllegalArgumentException("Kasa ve kiloyu kontrol et. " + error);
                    model.add(original.items().get(i).grade, (int) count, kg);
                }
                if (!model.missingPrices().isEmpty()) throw new IllegalArgumentException("Elek fiyatlarını tamamla.");
                db.beginTransaction();
                try {
                    TezgahPurchases.Purchase current = TezgahPurchases.get(db, owner, purchase.id);
                    if (!TezgahWebCommands.fingerprint(purchase).equals(TezgahWebCommands.fingerprint(current))) throw new IllegalArgumentException("Kayıt değişti. Kapatıp yeniden aç.");
                    saved = TezgahPurchases.update(db, owner, purchase.id, day, seller, purchase.phone, model);
                    if (!saved) throw new IllegalArgumentException("Alım düzenlenemedi. Z kilidini ve ödenen tutarı kontrol et.");
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
                TezgahWebChanges.committed(db, owner);
                dialog.dismiss();
                changed.run();
            } catch (Exception e) {
                Toast.makeText(activity, e instanceof IllegalArgumentException ? e.getMessage() : "Alım düzenlenemedi.", Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
    }
}
