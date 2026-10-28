package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ayarlar → Yedekle / Geri yükle. Yalnız telefonda duran kayıtlar (tezgah alımları, borç ödemeleri, akşam hesapları,
 * gün sonları, Elek ödeme geçmişi, yağhane sıkımları, tezgah fiyat/dara) tek bir metin dosyasına yazılır;
 * dosya kullanıcının seçtiği yere (İndirilenler, Google Drive…) kaydedilir, sonra paylaşılabilir.
 * Bahçe, elek, satış gibi kayıtlar zaten buluta yedeklenir; bu dosya onların yerine geçmez.
 * EXTRA_START: "save" ya da "restore" ile açılırsa ilgili adım hemen başlar (Alım defterindeki kısayol).
 */
public final class BackupActivity extends Activity {
    public static final String EXTRA_START = "start";
    static final String PREFS = "zd_backup";
    private static final int REQ_SAVE = 61, REQ_OPEN = 62;
    private static final SimpleDateFormat DM_HM = new SimpleDateFormat("d MMM yyyy HH:mm", new Locale("tr", "TR"));

    private int BG, CARD, BORDER, TEXT, MUTED, GREEN, AMBER;
    private DatabaseHelper db;
    private String owner = "";
    private LinearLayout body;

    /** Bu hesabın son yedek zamanı (0 = hiç). */
    public static long lastBackup(Context c, String owner) {
        return c.getApplicationContext().getSharedPreferences(PREFS, MODE_PRIVATE).getLong("last_" + (owner == null ? "" : owner), 0);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!new SessionManager(this).hasSession()) { finish(); return; }
        BG = GreenTheme.color(R.color.zd_bg); CARD = GreenTheme.color(R.color.zd_card);
        BORDER = GreenTheme.color(R.color.zd_border); TEXT = GreenTheme.color(R.color.zd_text);
        MUTED = GreenTheme.color(R.color.zd_muted); GREEN = GreenTheme.color(R.color.zd_green);
        AMBER = GreenTheme.color(R.color.zd_warning);
        db = new DatabaseHelper(this);
        owner = db.getActiveOwnerUid();
        if (owner == null) owner = "";
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(6), dp(16), dp(28));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        render();
        if (state == null) {
            String start = getIntent().getStringExtra(EXTRA_START);
            if ("save".equals(start)) startBackup();
            else if ("restore".equals(start)) startRestore();
        }
    }

    @Override protected void onDestroy() {
        if (db != null) db.close();
        super.onDestroy();
    }

    /** Şu an telefondaki yerel kayıtların özeti ("12 alım · 2 gün sonu · 3 sıkım"). */
    private TezgahBackup.Data current() {
        try { return TezgahBackup.parse(TezgahBackup.export(db.getWritableDatabase(), owner, prefs(), 0)); }
        catch (RuntimeException e) { return new TezgahBackup.Data(); }
    }

    private void render() {
        body.removeAllViews();
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setMinimumHeight(dp(52));
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_elek_back_chevron);
        back.setColorFilter(TEXT);
        back.setPadding(dp(11), dp(11), dp(11), dp(11));
        back.setContentDescription("Geri");
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(40), dp(44));
        bp.setMarginStart(-dp(10));
        head.addView(back, bp);
        head.addView(text("Yedekle / Geri yükle", 22, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(head);

        long last = lastBackup(this, owner);
        TezgahBackup.Data d = current();
        int records = 0;
        for (String t : TezgahBackup.TABLES) records += d.rowCount(t);
        boolean stale = records > 0 && (last == 0 || System.currentTimeMillis() - last > 7L * 24 * 60 * 60 * 1000);

        LinearLayout status = card(stale ? AMBER : BORDER);
        status.addView(label("DURUM"));
        status.addView(text(last == 0 ? "Henüz yedek alınmadı." : "Son yedek: " + DM_HM.format(new Date(last)), 18,
                stale ? AMBER : TEXT, true), margins(4));
        status.addView(text(records == 0 ? "Bu telefonda yedeklenecek kayıt yok." : "Bu telefonda: " + TezgahBackup.describe(d), 14, MUTED, false), margins(4));
        if (stale) status.addView(text("Telefon bozulur ya da kaybolursa bu kayıtlar gider. Şimdi yedek al.", 14, AMBER, true), margins(6));
        body.addView(status, margins(10));

        TextView save = text("Yedekle", 18, BG, true);
        save.setGravity(Gravity.CENTER);
        save.setBackground(round(GREEN, 0, 18));
        save.setContentDescription("Yedek dosyası oluştur");
        save.setOnClickListener(v -> startBackup());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(56));
        sp.topMargin = dp(14);
        body.addView(save, sp);
        TextView restore = text("Geri yükle", 17, GREEN, true);
        restore.setGravity(Gravity.CENTER);
        restore.setBackground(round(Color.TRANSPARENT, GREEN, 18));
        restore.setContentDescription("Yedek dosyasından geri yükle");
        restore.setOnClickListener(v -> startRestore());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(52));
        rp.topMargin = dp(10);
        body.addView(restore, rp);

        LinearLayout info = card(BORDER);
        info.addView(label("YEDEĞE GİRENLER"));
        info.addView(text("• Tezgah alımları, borç ödemeleri, akşam hesapları ve gün sonları (Z)\n"
                + "• Elek satışlarının ödeme geçmişi\n• Yağhane sıkımları\n• Tezgah fiyatları ve kasa darası", 14, TEXT, false), margins(6));
        info.addView(label("NASIL?"), margins(14));
        info.addView(text("1. Yedekle'ye bas, dosyayı İndirilenler'e ya da Google Drive'a kaydet.\n"
                + "2. Telefon bozulmasın diye dosyayı WhatsApp'ta kendine de gönder.\n"
                + "3. Yeni telefonda aynı hesapla gir, Geri yükle ile dosyayı seç.", 14, TEXT, false), margins(6));
        info.addView(text("Bahçe, elek, satış ve alacak kayıtları zaten buluta yedeklenir; girişte kendiliğinden gelir.", 13, MUTED, false), margins(10));
        body.addView(info, margins(18));
    }

    // ================================================================== işlemler

    private void startBackup() {
        String name = "zeytin-defteri-yedek-" + new SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(new Date()) + ".txt";
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/plain").putExtra(Intent.EXTRA_TITLE, name);
        try { startActivityForResult(i, REQ_SAVE); }
        catch (RuntimeException e) { toast("Dosya kaydetme ekranı açılamadı."); }
    }

    private void startRestore() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        try { startActivityForResult(i, REQ_OPEN); }
        catch (RuntimeException e) { toast("Dosya seçme ekranı açılamadı."); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == REQ_SAVE) writeBackup(data.getData());
        else if (request == REQ_OPEN) readBackup(data.getData());
    }

    private Map<String, Object> prefs() {
        android.content.SharedPreferences tp = getSharedPreferences("zd_tezgah", MODE_PRIVATE);
        Map<String, Object> m = new LinkedHashMap<>();
        String prices = tp.getString("buy_prices_" + owner, "");
        if (!prices.isEmpty()) m.put("buy_prices", prices);
        if (tp.contains("tare")) m.put("tare", (double) tp.getFloat("tare", (float) TezgahModel.DEFAULT_TARE));
        int crates = tp.getInt(TezgahCratesActivity.TOTAL_KEY + owner, 0);
        if (crates > 0) m.put("crate_total", (long) crates);
        return m;
    }

    private void writeBackup(final Uri uri) {
        String text;
        try { text = TezgahBackup.export(db.getWritableDatabase(), owner, prefs(), System.currentTimeMillis()); }
        catch (RuntimeException e) { toast("Yedek hazırlanamadı."); return; }
        try (java.io.OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new java.io.IOException("açılamadı");
            out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            toast("Yedek dosyası yazılamadı.");
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong("last_" + owner, System.currentTimeMillis()).apply();
        TezgahBackup.Data d = TezgahBackup.parse(text);
        render();
        new AlertDialog.Builder(this)
                .setTitle("Yedek alındı ✓")
                .setMessage(TezgahBackup.describe(d) + ".\n\nTelefon bozulursa kaybolmasın diye dosyayı Drive'a ya da "
                        + "WhatsApp'ta kendine de gönder.")
                .setPositiveButton("Gönder / paylaş", (x, y) -> {
                    Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try { startActivity(Intent.createChooser(send, "Yedeği gönder")); }
                    catch (RuntimeException e) { toast("Paylaşım açılamadı."); }
                })
                .setNegativeButton("Tamam", null)
                .show();
    }

    private void readBackup(Uri uri) {
        final TezgahBackup.Data d;
        try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new java.io.IOException("açılamadı");
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n, total = 0;
            while ((n = in.read(chunk)) > 0) {
                total += n;
                if (total > 30 * 1024 * 1024) throw new IllegalArgumentException("Dosya çok büyük; bu bir yedek değil.");
                buf.write(chunk, 0, n);
            }
            d = TezgahBackup.parse(buf.toString("UTF-8"));
        } catch (IllegalArgumentException e) {
            new AlertDialog.Builder(this).setTitle("Geri yüklenemedi").setMessage(e.getMessage()).setPositiveButton("Tamam", null).show();
            return;
        } catch (Exception e) {
            toast("Dosya okunamadı.");
            return;
        }
        TezgahBackup.Data here = current();
        int records = 0;
        for (String t : TezgahBackup.TABLES) records += here.rowCount(t);
        String when = d.created > 0 ? DM_HM.format(new Date(d.created)) : "tarihsiz";
        new AlertDialog.Builder(this)
                .setTitle("Yedek geri yüklensin mi?")
                .setMessage("Yedek: " + when + " · " + TezgahBackup.describe(d) + "\n\n"
                        + (records > 0 ? "Bu telefondaki kayıtlar (" + TezgahBackup.describe(here) + ") yedektekiyle DEĞİŞTİRİLECEK. "
                        + "Yedekten sonra girilenler kaybolur." : "Bu telefonda yerel kayıt yok; yedekteki kayıtlar eklenecek."))
                .setPositiveButton("Geri yükle", (x, y) -> applyRestore(d))
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void applyRestore(TezgahBackup.Data d) {
        int n;
        try { n = TezgahBackup.restore(db.getWritableDatabase(), owner, d); }
        catch (RuntimeException e) { n = -1; }
        if (n < 0) { toast("Geri yükleme başarısız; hiçbir kayıt değişmedi."); return; }
        android.content.SharedPreferences.Editor e = getSharedPreferences("zd_tezgah", MODE_PRIVATE).edit();
        Object prices = d.prefs.get("buy_prices");
        if (prices != null) e.putString("buy_prices_" + owner, String.valueOf(prices));
        Object tare = d.prefs.get("tare");
        if (tare instanceof Number) e.putFloat("tare", ((Number) tare).floatValue());
        Object crates = d.prefs.get("crate_total");
        if (crates instanceof Number) e.putInt(TezgahCratesActivity.TOTAL_KEY + owner, ((Number) crates).intValue());
        e.apply();
        render();
        toast("Geri yüklendi ✓ " + TezgahBackup.describe(d));
    }

    // ================================================================== yardımcılar

    private LinearLayout card(int stroke) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(round(CARD, stroke, 18));
        return c;
    }

    private TextView label(String v) {
        TextView t = text(v, 11, MUTED, true);
        t.setLetterSpacing(0.08f);
        return t;
    }

    private TextView text(String v, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(v);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLineSpacing(0, 1.12f);
        return t;
    }

    private GradientDrawable round(int fill, int stroke, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(r));
        if (stroke != 0) g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }

    private LinearLayout.LayoutParams margins(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        return lp;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
