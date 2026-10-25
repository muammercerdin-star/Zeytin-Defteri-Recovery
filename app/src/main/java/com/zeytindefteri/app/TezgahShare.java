package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Tezgah fişini paylaşma: büyük QR penceresi ve WhatsApp / paylaşım menüsü (Tezgah ve Alım defteri ortak). */
public final class TezgahShare {
    private TezgahShare() {}

    /** Tüccarın görünen adı (QR'da üreticinin "alıcı" alanına girer). */
    public static String traderName() {
        try {
            com.google.firebase.auth.FirebaseUser u = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            String n = u == null ? null : u.getDisplayName();
            return n == null ? "" : n.trim();
        } catch (RuntimeException e) {
            return "";
        }
    }

    /**
     * Büyük QR: üretici kendi telefonunda Elek kaydı → "QR okut" ile okutur; kilolar, fiyatlar, tarih,
     * alıcı (tüccarın adı) ve ödeme durumu forma kendiliğinden dolar. Ekran parlaklığı geçici olarak artar.
     */
    public static void showQr(Activity a, TezgahModel m, String date, String sellerName, double paid,
                              String text, String phone) {
        float d = a.getResources().getDisplayMetrics().density;
        int size = Math.min(a.getResources().getDisplayMetrics().widthPixels - Math.round(80 * d), Math.round(320 * d));
        Bitmap bmp = TezgahQrUi.bitmap(TezgahQr.encode(m, date, traderName(), paid), size);
        if (bmp == null) {
            Toast.makeText(a, "QR oluşturulamadı; fişi WhatsApp'tan gönder.", Toast.LENGTH_SHORT).show();
            shareText(a, text, phone);
            return;
        }
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(Math.round(16 * d), Math.round(12 * d), Math.round(16 * d), Math.round(4 * d));
        ImageView img = new ImageView(a);
        img.setImageBitmap(bmp);
        img.setBackgroundColor(Color.WHITE);
        img.setContentDescription("Alım fişi QR kodu");
        box.addView(img, new LinearLayout.LayoutParams(size, size));
        TextView info = new TextView(a);
        info.setText((sellerName == null || sellerName.isEmpty() ? "" : sellerName + " · ") + TezgahModel.kg(m.totalNet())
                + " · " + TezgahModel.tl(m.total()) + "\nÜretici: Elek kaydı → \"QR okut\"\nBilgi fişidir; makbuz/fatura yerine geçmez.");
        info.setGravity(Gravity.CENTER);
        info.setTextSize(15);
        info.setPadding(0, Math.round(10 * d), 0, 0);
        box.addView(info);
        AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle("Üretici okutsun")
                .setView(box)
                .setPositiveButton("Bitti", null)
                .setNeutralButton("WhatsApp'a da gönder", (x, y) -> shareText(a, text, phone))
                .create();
        dialog.setOnShowListener(x -> {
            if (dialog.getWindow() != null) {
                WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                lp.screenBrightness = 1f;
                dialog.getWindow().setAttributes(lp);
            }
        });
        dialog.show();
    }

    /** Telefon Türkiye cep numarasıysa doğrudan o kişinin WhatsApp'ı, değilse paylaşım menüsü. */
    public static void shareText(Activity a, String text, String phone) {
        String wa = TezgahModel.waPhone(phone);
        if (!wa.isEmpty()) {
            try {
                a.startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://wa.me/" + wa + "?text=" + Uri.encode(text))));
                return;
            } catch (RuntimeException ignored) {
                // WhatsApp yoksa paylaşım menüsüne düşer.
            }
        }
        try {
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
            a.startActivity(Intent.createChooser(send, "Fişi gönder"));
        } catch (RuntimeException e) {
            Toast.makeText(a, "Paylaşılacak uygulama bulunamadı.", Toast.LENGTH_SHORT).show();
        }
    }
}
