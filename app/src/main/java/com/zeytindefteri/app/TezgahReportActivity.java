package com.zeytindefteri.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Gün raporu: alım tablosu + Akşam hesabı tek sayfada, kâğıt görünümünde.
 * İki parmakla yakınlaştırılır; "Resim olarak gönder" ile WhatsApp vb. paylaşılır (Galeri'ye de kaydedilir).
 */
public final class TezgahReportActivity extends Activity {
    /** Verilirse o gün sonunun Z fişi gösterilir; verilmezse açık dönemin tablo raporu. */
    public static final String EXTRA_Z_ID = "z_id";
    private static final int BG = Color.parseColor("#050F16");
    private static final int GREEN = Color.parseColor("#8BE06A");
    private static final int TEXT = Color.WHITE;

    private Bitmap report;
    private TezgahZ.Record zRecord;
    private String date = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!new SessionManager(this).hasSession()) { finish(); return; }
        long zId = getIntent().getLongExtra(EXTRA_Z_ID, 0L);
        date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        DatabaseHelper db = new DatabaseHelper(this);
        String owner = db.getActiveOwnerUid();
        if (owner == null) owner = "";
        List<TezgahPurchases.Purchase> list;
        TezgahClosing closing;
        TezgahZ.Record z = null;
        try {
            if (zId > 0) {
                z = TezgahZ.get(db.getWritableDatabase(), owner, zId);
                list = z == null ? new java.util.ArrayList<>() : TezgahPurchases.forZ(db.getWritableDatabase(), owner, zId);
                closing = z == null ? null : TezgahZ.closingOf(z, list);
            } else {
                list = TezgahPurchases.open(db.getWritableDatabase(), owner);
                closing = TezgahClosing.fromPurchases(list);
                TezgahClosing.load(db.getWritableDatabase(), owner, TezgahZ.OPEN_KEY, closing);
            }
        } catch (RuntimeException e) {
            list = new java.util.ArrayList<>();
            closing = null;
        } finally {
            db.close();
        }
        zRecord = z;
        if (z != null) date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(z.closedAt));

        try {
            if (z != null && closing != null) {
                report = TezgahZSlip.draw(z, list, closing, TezgahShare.traderName());
            } else {
                report = TezgahReportImage.draw(list, closing, "Alım defteri · açık dönem · " + dateLabel(true),
                        TezgahShare.traderName());
            }
        } catch (RuntimeException | OutOfMemoryError e) {
            report = null;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.addView(header(), new LinearLayout.LayoutParams(-1, dp(56)));

        if (report == null || list.isEmpty()) {
            TextView empty = text(list.isEmpty() ? "Açık dönemde alım yok." : "Rapor hazırlanamadı.", 16, TEXT, false);
            empty.setGravity(Gravity.CENTER);
            root.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
            setContentView(root);
            return;
        }

        WebView web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#E9EFEC"));
        WebSettings s = web.getSettings();
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setJavaScriptEnabled(false);
        s.setAllowFileAccess(false);
        String html = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1, maximum-scale=6, user-scalable=yes'>"
                + "<style>body{margin:0;padding:10px;background:#E9EFEC;text-align:center}img{width:100%;height:auto;"
                + (zRecord != null ? "max-width:440px;filter:drop-shadow(0 2px 6px rgba(0,0,0,.3))}" : "box-shadow:0 2px 10px rgba(0,0,0,.25);background:#fff}")
                + "</style></head><body>"
                + "<img alt='Gün raporu' src='data:image/png;base64," + png64(report) + "'></body></html>";
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView hint = text("İki parmakla yakınlaştır", 12, Color.parseColor("#A9BAB6"), false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(6), 0, dp(4));
        root.addView(hint);

        TextView share = text("Resim olarak gönder (WhatsApp)", 17, BG, true);
        share.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(GREEN);
        g.setCornerRadius(dp(18));
        share.setBackground(g);
        share.setOnClickListener(v -> shareImage());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(54));
        sp.setMargins(dp(14), dp(4), dp(14), dp(12));
        root.addView(share, sp);
        setContentView(root);
    }

    private LinearLayout header() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), 0, dp(14), 0);
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_elek_back_chevron);
        back.setColorFilter(TEXT);
        back.setPadding(dp(11), dp(11), dp(11), dp(11));
        back.setContentDescription("Geri");
        back.setOnClickListener(v -> finish());
        row.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(zRecord != null ? "Gün sonu · " + zRecord.label() : "Rapor · açık dönem", 20, TEXT, true));
        col.addView(text(zRecord != null ? new SimpleDateFormat("d MMMM yyyy HH:mm", new Locale("tr", "TR"))
                .format(new Date(zRecord.closedAt)) : dateLabel(true), 13, GREEN, true));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private String dateLabel(boolean weekday) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date);
            return new SimpleDateFormat(weekday ? "d MMMM yyyy, EEEE" : "d MMMM yyyy", new Locale("tr", "TR")).format(d);
        } catch (Exception e) {
            return date;
        }
    }

    /** Galeri'ye (Resimler/Zeytin Defteri) kaydeder ve paylaşım menüsünü açar. Android 9 ve öncesinde paylaşım önbellekten. */
    private void shareImage() {
        if (report == null) return;
        String name = zRecord != null ? "Zeytin-Defteri-" + zRecord.label() + ".png"
                : "Zeytin-Defteri-Rapor-" + date + "-" + System.currentTimeMillis() / 1000 + ".png";
        Uri uri = null;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Zeytin Defteri");
                uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            } else {
                String path = MediaStore.Images.Media.insertImage(getContentResolver(), report, name, "Zeytin Defteri gün raporu");
                if (path != null) uri = Uri.parse(path);
            }
            if (uri != null && Build.VERSION.SDK_INT >= 29) {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null || !report.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new java.io.IOException();
                }
            }
        } catch (Exception e) {
            uri = null;
        }
        if (uri == null) {
            Toast.makeText(this, "Resim kaydedilemedi. Ekran görüntüsü alıp gönderebilirsin.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Intent send = new Intent(Intent.ACTION_SEND).setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_TEXT, zRecord != null ? "Gün sonu " + zRecord.label() + " · " + dateLabel(false)
                            : "Alım defteri · " + dateLabel(false))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "Raporu gönder"));
        } catch (RuntimeException e) {
            Toast.makeText(this, "Paylaşılacak uygulama bulunamadı. Resim Galeri'ye kaydedildi.", Toast.LENGTH_LONG).show();
        }
    }

    private static String png64(Bitmap b) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
