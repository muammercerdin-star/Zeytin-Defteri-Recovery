package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Alım defteri: açık dönemin (gün sonu alınmamış) alımları ya da bir gün sonu (Z) raporu, tablo görünümünde.
 * - Solda sabit "Üretici" sütunu, sağda yana kayan sütunlar (kasa, numara kiloları, net, ortalama, tutar, ödenen, kalan).
 * - En altta TOPLAM satırı; altta akşam hesabı ve "Gün sonu al (Z)"; en altta önceki gün sonları.
 * - Z açıldığında (EXTRA_Z_ID) kayıtlar kilitlidir: silinmez, akşam hesabı değişmez; yalnız borç ödemesi işlenir.
 * - Satıra dokununca: QR göster, fişi gönder, kalan borcu ödedim, sil.
 * - "Rapor": alım tablosu + akşam hesabı tek sayfa (TezgahReportActivity), resim olarak gönderilir.
 * - Altta "Akşam hesabı": tüccara teslim, fire, komisyon, avans.
 */
public final class TezgahDayActivity extends Activity {
    public static final String EXTRA_Z_ID = "z_id";
    private static final int BG = Color.parseColor("#050F16");
    private static final int CARD = Color.parseColor("#0F2230");
    private static final int ROW_ALT = Color.parseColor("#0B1A24");
    private static final int HEAD = Color.parseColor("#163142");
    private static final int LINE = Color.parseColor("#2A4452");
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.parseColor("#A9BAB6");
    private static final int GREEN = Color.parseColor("#8BE06A");
    private static final int GREEN_DIM = Color.parseColor("#1E3D2C");
    private static final int AMBER = Color.parseColor("#F2C35B");
    private static final Locale TR = new Locale("tr", "TR");

    private static final int ROW_H = 50, HEAD_H = 40, NAME_W = 132;
    private static final String[] COLS = {"Kasa", "Double", "1", "2", "3", "4", "Yağlık", "Net kg", "Ort ₺/kg", "Tutar", "Ödenen", "Kalan"};
    private static final int[] WIDTHS = {54, 64, 58, 58, 58, 58, 64, 78, 82, 96, 96, 92};

    private DatabaseHelper db;
    private String owner = "";
    private long zId;
    private TezgahZ.Record zRecord;
    private LinearLayout body;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!new SessionManager(this).hasSession()) { finish(); return; }
        db = new DatabaseHelper(this);
        owner = db.getActiveOwnerUid();
        if (owner == null) owner = "";
        zId = getIntent().getLongExtra(EXTRA_Z_ID, 0L);
        if (zId > 0) {
            try { zRecord = TezgahZ.get(db.getWritableDatabase(), owner, zId); } catch (RuntimeException ignored) { }
            if (zRecord == null) { finish(); return; }
        }
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(4), dp(12), dp(24));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        render();
    }

    /** Rapordan/gün sonundan dönünce (borç ödemesi vb.) tazele. */
    @Override protected void onRestart() {
        super.onRestart();
        if (body != null) render();
    }

    @Override protected void onDestroy() {
        if (db != null) db.close();
        super.onDestroy();
    }

    private static final SimpleDateFormat DM_HM = new SimpleDateFormat("d MMM HH:mm", new Locale("tr", "TR"));
    private static final SimpleDateFormat HM = new SimpleDateFormat("HH:mm", Locale.US);

    private List<TezgahPurchases.Purchase> load() {
        try {
            return zId > 0 ? TezgahPurchases.forZ(db.getWritableDatabase(), owner, zId)
                    : TezgahPurchases.open(db.getWritableDatabase(), owner);
        }
        catch (RuntimeException e) { return new java.util.ArrayList<>(); }
    }

    // ================================================================== ekran

    private void render() {
        body.removeAllViews();
        final List<TezgahPurchases.Purchase> list = load();
        body.addView(header(list));
        // Arama (yalnız ana defterde): bütün geçmişte ad / telefon / tutar; "sadece borçlular".
        if (zId <= 0) {
            body.addView(searchBar(), margins(-1, -2, 4));
            contentStart = body.getChildCount();
            if (searching()) { renderResults(); return; }
        }
        body.addView(statusBar(list), margins(-1, -2, 6));
        body.addView(summary(list), margins(-1, -2, 10));
        if (list.isEmpty()) {
            TextView empty = text("Açık dönemde alım yok. Tezgahta bir alımı bitirince burada satır olarak görünür.",
                    15, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(28), dp(16), dp(28));
            body.addView(empty);
        } else {
            body.addView(table(list), margins(-1, -2, 12));
            TextView hint = text(zId > 0 ? "Gün sonu alındı: kayıtlar kilitli. Satıra dokun: QR · fiş · borç ödemesi."
                    : "Satıra dokun: QR göster · fişi gönder · borcu kapat · sil. Tablo sağa kayar.", 12, MUTED, false);
            body.addView(hint, margins(-1, -2, 8));
            body.addView(closingCard(list), margins(-1, -2, 16));
        }
        if (zId <= 0) body.addView(zList(), margins(-1, -2, 18));
        if (zId <= 0) body.addView(backupCard(), margins(-1, -2, 18));
    }

    // ================================================================== yedek

    private static final int REQ_BACKUP_SAVE = 61, REQ_BACKUP_OPEN = 62;
    private static final String BACKUP_PREFS = "zd_backup";

    private long lastBackup() { return getSharedPreferences(BACKUP_PREFS, MODE_PRIVATE).getLong("last_" + owner, 0); }

    /**
     * Yedek kartı: tezgah kayıtları yalnız bu telefonda; "Yedekle" dosyayı kullanıcının seçtiği yere
     * (İndirilenler, Google Drive…) yazar, "Geri yükle" seçilen yedek dosyasından geri getirir.
     * Gün sonundan sonra yedek alınmadıysa kart sarı uyarır.
     */
    private View backupCard() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(14));
        long last = lastBackup();
        long lastZ = 0;
        try {
            List<TezgahZ.Record> zs = TezgahZ.list(db.getWritableDatabase(), owner, 1);
            if (!zs.isEmpty()) lastZ = zs.get(0).closedAt;
        } catch (RuntimeException ignored) { }
        boolean stale = lastZ > last || (last == 0 && !load().isEmpty());
        box.setBackground(round(CARD, stale ? AMBER : LINE, 16));
        TextView head = text("YEDEK", 12, stale ? AMBER : MUTED, true);
        head.setLetterSpacing(0.08f);
        box.addView(head);
        String status = last == 0 ? "Henüz yedek alınmadı." : "Son yedek: " + DM_HM.format(new Date(last));
        if (stale && lastZ > 0) status += "\nGün sonundan sonra yedek alınmadı.";
        box.addView(text(status, 15, stale ? AMBER : TEXT, true), margins(-1, -2, 4));
        box.addView(text("Tezgah alımları, borçlar ve gün sonları yalnız bu telefonda durur. Telefon bozulur ya da "
                + "değişirse yedek dosyasıyla geri gelir. Dosyayı Drive'a ya da WhatsApp'ta kendine gönder.", 13, MUTED, false),
                margins(-1, -2, 4));
        LinearLayout row = new LinearLayout(this);
        TextView save = text("Yedekle", 16, BG, true);
        save.setGravity(Gravity.CENTER);
        save.setBackground(round(GREEN, 0, 16));
        save.setContentDescription("Yedek dosyası oluştur");
        save.setOnClickListener(v -> startBackup());
        row.addView(save, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView restore = text("Geri yükle", 16, GREEN, true);
        restore.setGravity(Gravity.CENTER);
        restore.setBackground(round(Color.TRANSPARENT, GREEN, 16));
        restore.setContentDescription("Yedek dosyasından geri yükle");
        restore.setOnClickListener(v -> startRestore());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, dp(48), 1);
        rp.setMarginStart(dp(8));
        row.addView(restore, rp);
        box.addView(row, margins(-1, -2, 12));
        TextView printer = text("Fiş yazıcısı (Bluetooth) ayarları  ›", 14, GREEN, true);
        printer.setPadding(0, dp(14), 0, dp(2));
        printer.setOnClickListener(v -> TezgahPrinter.settings(this));
        box.addView(printer);
        return box;
    }

    private void openStatement(String seller) {
        startActivity(new Intent(this, TezgahStatementActivity.class).putExtra(TezgahStatementActivity.EXTRA_NAME, seller));
    }

    private void startBackup() {
        String name = "zeytin-defteri-yedek-" + new SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(new Date()) + ".txt";
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/plain").putExtra(Intent.EXTRA_TITLE, name);
        try { startActivityForResult(i, REQ_BACKUP_SAVE); }
        catch (RuntimeException e) { toast("Dosya kaydetme ekranı açılamadı."); }
    }

    private void startRestore() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        try { startActivityForResult(i, REQ_BACKUP_OPEN); }
        catch (RuntimeException e) { toast("Dosya seçme ekranı açılamadı."); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == REQ_BACKUP_SAVE) writeBackup(data.getData());
        else if (request == REQ_BACKUP_OPEN) readBackup(data.getData());
    }

    private java.util.Map<String, Object> tezgahPrefs() {
        android.content.SharedPreferences tp = getSharedPreferences("zd_tezgah", MODE_PRIVATE);
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        String prices = tp.getString("buy_prices_" + owner, "");
        if (!prices.isEmpty()) m.put("buy_prices", prices);
        if (tp.contains("tare")) m.put("tare", (double) tp.getFloat("tare", (float) TezgahModel.DEFAULT_TARE));
        return m;
    }

    private void writeBackup(final android.net.Uri uri) {
        String text;
        try { text = TezgahBackup.export(db.getWritableDatabase(), owner, tezgahPrefs(), System.currentTimeMillis()); }
        catch (RuntimeException e) { toast("Yedek hazırlanamadı."); return; }
        try (java.io.OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new java.io.IOException("açılamadı");
            out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            toast("Yedek dosyası yazılamadı.");
            return;
        }
        getSharedPreferences(BACKUP_PREFS, MODE_PRIVATE).edit().putLong("last_" + owner, System.currentTimeMillis()).apply();
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

    private void readBackup(android.net.Uri uri) {
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
        int here = 0;
        try { here = TezgahPurchases.all(db.getWritableDatabase(), owner, 100000).size(); } catch (RuntimeException ignored) { }
        String when = d.created > 0 ? DM_HM.format(new Date(d.created)) : "tarihsiz";
        new AlertDialog.Builder(this)
                .setTitle("Yedek geri yüklensin mi?")
                .setMessage("Yedek: " + when + " · " + TezgahBackup.describe(d) + "\n\n"
                        + (here > 0 ? "Bu telefondaki " + here + " tezgah alımı ve gün sonları yedektekiyle DEĞİŞTİRİLECEK. "
                        + "Yedekten sonra girilen alımlar kaybolur." : "Bu telefonda tezgah kaydı yok; yedekteki kayıtlar eklenecek."))
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
        e.apply();
        history = null;
        render();
        toast("Geri yüklendi ✓ " + TezgahBackup.describe(d));
    }

    // ================================================================== arama

    private LinearLayout searchBox;
    private android.widget.EditText searchInput;
    private TextView chipAll, chipDebt;
    private boolean debtOnly;
    private int contentStart;
    private List<TezgahPurchases.Purchase> history;
    private java.util.Map<Long, String> zLabels;

    private boolean searching() {
        return debtOnly || (searchInput != null && !searchInput.getText().toString().trim().isEmpty());
    }

    /** Arama kutusu ve süzgeç bir kez oluşturulur; ekran tazelenince aynı kutu (yazı, imleç) korunur. */
    private View searchBar() {
        if (searchBox != null) {
            if (searchBox.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) searchBox.getParent()).removeView(searchBox);
            history = null; // borç ödemesi vb. sonrası yeniden okunsun
            paintChips();
            return searchBox;
        }
        searchBox = new LinearLayout(this);
        searchBox.setOrientation(LinearLayout.VERTICAL);
        searchInput = new android.widget.EditText(this);
        searchInput.setHint("🔍  Üretici ara · ad, telefon ya da tutar");
        searchInput.setSingleLine(true);
        searchInput.setTextSize(17);
        searchInput.setTextColor(TEXT);
        searchInput.setHintTextColor(MUTED);
        searchInput.setPadding(dp(16), 0, dp(16), 0);
        searchInput.setBackground(round(CARD, LINE, 16));
        searchInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        searchInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchInput.setContentDescription("Üretici ara: ad, telefon ya da tutar");
        searchInput.addTextChangedListener(watch(this::refreshSearch));
        searchBox.addView(searchInput, new LinearLayout.LayoutParams(-1, dp(52)));
        LinearLayout chips = new LinearLayout(this);
        chipAll = text("Tümü", 14, TEXT, true);
        chipDebt = text("Sadece borçlular", 14, TEXT, true);
        for (TextView c : new TextView[]{chipAll, chipDebt}) {
            c.setGravity(Gravity.CENTER);
            c.setPadding(dp(16), 0, dp(16), 0);
        }
        chipAll.setOnClickListener(v -> { debtOnly = false; refreshSearch(); });
        chipDebt.setOnClickListener(v -> { debtOnly = true; refreshSearch(); });
        chips.addView(chipAll, new LinearLayout.LayoutParams(-2, dp(40)));
        LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(-2, dp(40));
        dp2.setMarginStart(dp(8));
        chips.addView(chipDebt, dp2);
        searchBox.addView(chips, margins(-1, -2, 8));
        paintChips();
        return searchBox;
    }

    private List<TezgahPurchases.Purchase> history() {
        if (history == null) {
            try {
                history = TezgahPurchases.all(db.getWritableDatabase(), owner, 2000);
                zLabels = new java.util.HashMap<>();
                for (TezgahZ.Record r : TezgahZ.list(db.getWritableDatabase(), owner, 2000)) zLabels.put(r.id, r.label());
            } catch (RuntimeException e) {
                history = new java.util.ArrayList<>();
                zLabels = new java.util.HashMap<>();
            }
        }
        return history;
    }

    private void paintChips() {
        if (chipAll == null) return;
        int debts = TezgahSearch.filter(history(), "", true).size();
        chipDebt.setText(debts > 0 ? "Sadece borçlular · " + debts : "Sadece borçlular");
        chipAll.setTextColor(debtOnly ? TEXT : BG);
        chipAll.setBackground(debtOnly ? round(CARD, LINE, 20) : round(GREEN, 0, 20));
        chipDebt.setTextColor(debtOnly ? BG : (debts > 0 ? AMBER : TEXT));
        chipDebt.setBackground(debtOnly ? round(AMBER, 0, 20) : round(CARD, debts > 0 ? AMBER : LINE, 20));
        chipAll.setContentDescription("Tüm alımlar" + (debtOnly ? "" : ", seçili"));
        chipDebt.setContentDescription("Sadece borcu olan alımlar, " + debts + (debtOnly ? ", seçili" : ""));
    }

    /** Yazdıkça yalnız arama kutusunun altı yenilenir (kutu ve klavye yerinde kalır). */
    private void refreshSearch() {
        paintChips();
        if (!searching()) { render(); return; }
        while (body.getChildCount() > contentStart) body.removeViewAt(contentStart);
        renderResults();
    }

    private void renderResults() {
        String q = searchInput.getText().toString();
        List<TezgahPurchases.Purchase> found = TezgahSearch.filter(history(), q, debtOnly);
        String what = debtOnly ? (q.trim().isEmpty() ? "Borçlular" : "Borçlu · \"" + q.trim() + "\"") : "\"" + q.trim() + "\"";
        double debt = TezgahSearch.totalRemaining(found);
        TextView head = text(what + " · " + found.size() + " alım" + (debt > 0.5 ? " · borç " + TezgahModel.tl(debt) : ""),
                14, debt > 0.5 ? AMBER : MUTED, true);
        body.addView(head, margins(-1, -2, 12));
        if (found.isEmpty()) {
            TextView none = text("Bulunamadı. \"Münferit satıcı\" olarak kaydedilen biri adıyla çıkmaz; tutarı ya da telefonu yazmayı dene.",
                    14, MUTED, false);
            none.setPadding(0, dp(12), 0, 0);
            body.addView(none);
            return;
        }
        // Aynı üreticinin birden fazla alımı varsa özet
        // Adla arayınca bulunan üreticilerin ekstre kartı (en çok 3); yalnız "borçlular"da 2+ alımı olanlar.
        int groupsShown = 0;
        for (TezgahSearch.Group g : TezgahSearch.groups(found)) {
            if (q.trim().isEmpty() && g.count < 2) break;
            if (groupsShown++ >= 3) break;
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(14), dp(10), dp(14), dp(10));
            box.setBackground(round(GREEN_DIM, GREEN, 14));
            box.addView(text(g.name, 16, TEXT, true));
            box.addView(text(g.count + " alım · " + TezgahModel.tl(g.gross)
                    + (g.remaining > 0.5 ? " · toplam borç " + TezgahModel.tl(g.remaining) : " · borç yok"), 14,
                    g.remaining > 0.5 ? AMBER : GREEN, true));
            box.addView(text("Ekstreyi aç ›", 13, GREEN, true), margins(-1, -2, 4));
            final String gName = g.name;
            box.setContentDescription(g.name + ", " + g.count + " alım. Üretici ekstresini aç.");
            box.setOnClickListener(v -> openStatement(gName));
            body.addView(box, margins(-1, -2, 8));
        }
        int shown = 0;
        for (final TezgahPurchases.Purchase p : found) {
            if (shown++ >= 100) break;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(10), dp(14), dp(10));
            boolean owes = p.remaining() > 0.5;
            card.setBackground(round(CARD, owes ? AMBER : LINE, 14));
            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView n = text(p.seller.isEmpty() ? "Adsız" : p.seller, 16, TEXT, true);
            n.setSingleLine(true);
            n.setEllipsize(android.text.TextUtils.TruncateAt.END);
            top.addView(n, new LinearLayout.LayoutParams(0, -2, 1));
            top.addView(text(TezgahModel.tl(p.gross), 16, TEXT, true));
            card.addView(top);
            String period = p.zId > 0 ? (zLabels != null && zLabels.containsKey(p.zId) ? zLabels.get(p.zId) : "Z") : "Açık dönem";
            card.addView(text(DM_HM.format(new Date(p.createdAt)) + " · " + period, 13, MUTED, false), margins(-1, -2, 2));
            LinearLayout bottom = new LinearLayout(this);
            bottom.setGravity(Gravity.CENTER_VERTICAL);
            bottom.addView(text(TezgahModel.kg(p.kg) + " · " + p.kasa + " kasa", 14, TEXT, false), new LinearLayout.LayoutParams(0, -2, 1));
            bottom.addView(text(owes ? "Borç " + TezgahModel.tl(p.remaining()) : "Ödendi ✓", 14, owes ? AMBER : GREEN, true));
            card.addView(bottom, margins(-1, -2, 4));
            card.setContentDescription((p.seller.isEmpty() ? "Adsız" : p.seller) + ", " + DM_HM.format(new Date(p.createdAt))
                    + ", " + period + ", " + TezgahModel.kg(p.kg) + ", " + TezgahModel.tl(p.gross)
                    + (owes ? ", borç " + TezgahModel.tl(p.remaining()) : ", ödendi") + ". İşlemler için dokun.");
            card.setOnClickListener(v -> actions(p));
            body.addView(card, margins(-1, -2, 8));
        }
        if (found.size() > 100) body.addView(text("İlk 100 sonuç gösteriliyor; aramayı daralt.", 12, MUTED, false), margins(-1, -2, 8));
    }

    /** Dönem şeridi: "Açık dönem · 19:37'den beri" ya da "Z-0003 · 20 Eki 21:30 · kilitli". */
    private View statusBar(List<TezgahPurchases.Purchase> list) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(10), dp(14), dp(10));
        bar.setBackground(round(CARD, zId > 0 ? LINE : GREEN_DIM, 16));
        TextView dot = text("●", 14, zId > 0 ? MUTED : GREEN, true);
        bar.addView(dot);
        String label;
        if (zId > 0) {
            label = zRecord.label() + " · " + DM_HM.format(new Date(zRecord.closedAt)) + " · kilitli 🔒";
        } else if (list.isEmpty()) {
            label = "Açık dönem · yeni alım bekleniyor";
        } else {
            long first = Long.MAX_VALUE;
            for (TezgahPurchases.Purchase p : list) first = Math.min(first, p.createdAt);
            label = "Açık dönem · " + DM_HM.format(new Date(first)) + "'den beri";
        }
        TextView t = text(label, 15, TEXT, true);
        t.setPadding(dp(8), 0, 0, 0);
        bar.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        return bar;
    }

    /** Önceki gün sonları: dokununca o Z'nin defteri (kilitli) açılır. */
    private View zList() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        List<TezgahZ.Record> list;
        try { list = TezgahZ.list(db.getWritableDatabase(), owner, 30); }
        catch (RuntimeException e) { list = new java.util.ArrayList<>(); }
        TextView head = text("GÜN SONLARI (Z RAPORLARI)", 12, MUTED, true);
        head.setLetterSpacing(0.08f);
        box.addView(head);
        if (list.isEmpty()) {
            box.addView(text("Henüz gün sonu alınmadı. Akşam hesabının altındaki \"Gün sonu al\" ile alınır.", 13, MUTED, false),
                    margins(-1, -2, 6));
            return box;
        }
        for (final TezgahZ.Record r : list) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(10), dp(12), dp(10));
            row.setBackground(round(CARD, LINE, 14));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(text(r.label() + "  ·  " + DM_HM.format(new Date(r.closedAt)), 15, TEXT, true));
            col.addView(text(r.count + " alım · " + TezgahModel.kg(r.kg) + " · " + TezgahModel.tl(r.gross), 13, MUTED, false));
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(text("›", 22, GREEN, true));
            row.setContentDescription(r.label() + " gün sonu raporunu aç");
            row.setOnClickListener(v -> startActivity(new Intent(this, TezgahDayActivity.class).putExtra(EXTRA_Z_ID, r.id)));
            box.addView(row, margins(-1, -2, 8));
        }
        return box;
    }

    /** Gün sonu önizlemesi ve onay; onaylanınca Z alınır, Z fişi açılır. */
    private void confirmDayEnd(List<TezgahPurchases.Purchase> list) {
        if (zId > 0 || list.isEmpty() || closing == null) return;
        saveClosing();
        TezgahPurchases.Day d = TezgahPurchases.sum(list);
        int next;
        try { next = TezgahZ.nextNumber(db.getWritableDatabase(), owner); } catch (RuntimeException e) { next = 1; }
        String msg = "Z-" + TezgahZ.pad(next) + " alınacak.\n\n"
                + d.count + " alım · " + d.kasa + " kasa\n"
                + "Net: " + TezgahModel.kg(d.kg) + "\n"
                + "Tutar: " + TezgahModel.tl(d.gross) + (d.remaining() > 0.5 ? "  (üreticilere borç " + TezgahModel.tl(d.remaining()) + ")" : "") + "\n"
                + "Teslim: " + TezgahModel.kg(closing.deliveredKg()) + " · fire " + TezgahModel.kg(closing.fireKg()) + "\n"
                + (closing.traderOwes() >= 0 ? "Tüccar ödeyecek: " : "Tezgah iade edecek: ") + TezgahModel.tl(Math.abs(closing.traderOwes()))
                + "\n\nGün sonundan sonra bu alımlar kilitlenir (silinemez, akşam hesabı değişmez). Tezgah yeni döneme sıfırdan başlar.";
        new AlertDialog.Builder(this)
                .setTitle("Gün sonu alınsın mı?")
                .setMessage(msg)
                .setPositiveButton("Gün sonunu al", (dlg, w) -> {
                    long id;
                    try { id = TezgahZ.close(db.getWritableDatabase(), owner, closing, System.currentTimeMillis()); }
                    catch (RuntimeException e) { id = -1; }
                    if (id <= 0) { toast("Gün sonu alınamadı. Tekrar dene."); return; }
                    toast("Gün sonu alındı ✓ Tezgah yeni döneme hazır.");
                    render();
                    startActivity(new Intent(this, TezgahReportActivity.class).putExtra(TezgahReportActivity.EXTRA_Z_ID, id));
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    // ================================================================== akşam hesabı

    private TezgahClosing closing;

    /** Günün kapanışı: kayıtlı teslim/komisyon/avans; yoksa teslim = alınan, komisyon son kullanılan. */
    private TezgahClosing loadClosing(List<TezgahPurchases.Purchase> list) {
        TezgahClosing c = TezgahClosing.fromPurchases(list);
        boolean saved = false;
        if (zId > 0 && zRecord != null) return TezgahZ.closingOf(zRecord, list);
        try { saved = TezgahClosing.load(db.getWritableDatabase(), owner, TezgahZ.OPEN_KEY, c); } catch (RuntimeException ignored) { }
        if (!saved) c.rate = getSharedPreferences("zd_tezgah", MODE_PRIVATE).getFloat("rate_" + owner, 0f);
        return c;
    }

    private void saveClosing() {
        if (closing == null || zId > 0) return;
        try {
            TezgahClosing.save(db.getWritableDatabase(), owner, TezgahZ.OPEN_KEY, closing, System.currentTimeMillis());
            getSharedPreferences("zd_tezgah", MODE_PRIVATE).edit().putFloat("rate_" + owner, (float) closing.rate).apply();
        } catch (RuntimeException ignored) {
            // Kaydedilemezse ekrandaki hesap yine doğru; bir sonraki değişiklikte tekrar denenir.
        }
    }

    /**
     * Tüccara teslim: numara numara tüccarın kantarındaki kilo (başta alınanla aynı), komisyon ve avans.
     * Fire, komisyon ve "tüccar ödeyecek" canlı hesaplanır; WhatsApp'tan tüccara gönderilir.
     */
    private View closingCard(final List<TezgahPurchases.Purchase> list) {
        closing = loadClosing(list);
        final TezgahClosing c = closing;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(14));
        box.setBackground(round(CARD, GREEN_DIM, 16));

        TextView head = text(zId > 0 ? "AKŞAM HESABI · " + zRecord.label() + " (KİLİTLİ)" : "AKŞAM HESABI · TÜCCARA TESLİM",
                12, GREEN, true);
        head.setLetterSpacing(0.08f);
        box.addView(head);
        // Numara numara teslim tablosu yalnız gerekince: tüccarın kantarı farklı çıktıysa açılır.
        boolean anyEdited = false;
        for (boolean e : c.edited) anyEdited |= e;
        final LinearLayout grades = new LinearLayout(this);
        grades.setOrientation(LinearLayout.VERTICAL);
        grades.setVisibility(anyEdited ? View.VISIBLE : View.GONE);
        if (zId <= 0 || anyEdited) {
            final TextView toggle = text(anyEdited ? "Tüccarın kantarındaki kilolar ▾" : "Tüccarın kantarı farklı çıktı mı? Kiloları düzelt ›",
                    14, GREEN, true);
            toggle.setPadding(0, dp(10), 0, dp(4));
            toggle.setContentDescription("Numara numara teslim kilolarını göster ya da gizle");
            toggle.setOnClickListener(v -> {
                boolean show = grades.getVisibility() != View.VISIBLE;
                grades.setVisibility(show ? View.VISIBLE : View.GONE);
                toggle.setText(show ? "Tüccarın kantarındaki kilolar ▾" : "Tüccarın kantarı farklı çıktı mı? Kiloları düzelt ›");
            });
            box.addView(toggle, margins(-1, -2, 4));
        }
        box.addView(grades);
        grades.addView(text("Farklı çıkan numarayı düzelt (yeşil çerçeve = elle girildi); fire kendiliğinden çıkar.",
                12, MUTED, false), margins(-1, -2, 2));

        // Sütun başlıkları
        LinearLayout hr = new LinearLayout(this);
        hr.addView(text("Numara", 12, MUTED, true), new LinearLayout.LayoutParams(0, -2, 1.1f));
        TextView h2 = text("Alınan", 12, MUTED, true); h2.setGravity(Gravity.END);
        hr.addView(h2, new LinearLayout.LayoutParams(0, -2, 0.9f));
        TextView h3 = text("Teslim", 12, MUTED, true); h3.setGravity(Gravity.CENTER);
        hr.addView(h3, new LinearLayout.LayoutParams(0, -2, 1.1f));
        TextView h4 = text("Fire", 12, MUTED, true); h4.setGravity(Gravity.END);
        hr.addView(h4, new LinearLayout.LayoutParams(0, -2, 0.8f));
        grades.addView(hr, margins(-1, -2, 10));

        final TextView[] fires = new TextView[TezgahClosing.N];
        final android.widget.EditText[] inputs = new android.widget.EditText[TezgahClosing.N];
        final LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        final Runnable refresh = () -> {
            for (int g = 0; g < TezgahClosing.N; g++) {
                if (fires[g] == null) continue;
                double f = c.fire(g);
                fires[g].setText(Math.abs(f) < 0.05 ? "—" : (f > 0 ? "−" : "+") + num(Math.abs(f)));
                fires[g].setTextColor(Math.abs(f) < 0.05 ? MUTED : (f > 0 ? AMBER : GREEN));
                // Elle girilen teslim kilosu yeşil çerçeveli; girilmeyen, alınan kiloyu kendiliğinden izler.
                if (inputs[g] != null) inputs[g].setBackground(round(BG, c.edited[g] ? GREEN : LINE, 10));
            }
            renderClosingResults(results, c);
        };
        for (int g = 0; g < TezgahClosing.N; g++) {
            if (c.bought[g] <= 0 && c.delivered[g] <= 0) continue;
            final int grade = g;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(48));
            row.addView(text(TezgahModel.LABELS[g], 15, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1.1f));
            TextView b = text(num(c.bought[g]), 15, TEXT, false);
            b.setGravity(Gravity.END);
            row.addView(b, new LinearLayout.LayoutParams(0, -2, 0.9f));
            final android.widget.EditText d = field(TezgahQr.field(c.delivered[g]), "0");
            d.setContentDescription(TezgahModel.LABELS[g] + " teslim kilosu");
            inputs[g] = d;
            if (zId > 0) { d.setEnabled(false); d.setFocusable(false); }
            d.addTextChangedListener(watch(() -> {
                String typed = d.getText().toString().trim();
                if (typed.isEmpty()) c.clearDelivered(grade);
                else c.setDelivered(grade, TezgahModel.parse(typed));
                refresh.run();
                saveClosing();
            }));
            LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0, dp(42), 1.1f);
            inputLp.setMargins(dp(8), dp(3), dp(4), dp(3));
            row.addView(d, inputLp);
            TextView f = text("", 15, MUTED, true);
            f.setGravity(Gravity.END);
            fires[g] = f;
            row.addView(f, new LinearLayout.LayoutParams(0, -2, 0.8f));
            grades.addView(row);
        }

        // Komisyon ve avans
        LinearLayout money = new LinearLayout(this);
        LinearLayout rc = new LinearLayout(this);
        rc.setOrientation(LinearLayout.VERTICAL);
        rc.addView(text("Komisyon (₺/kg)", 12, MUTED, true));
        final android.widget.EditText rate = field(c.rate > 0 ? TezgahQr.field(c.rate) : "", "0");
        rate.setContentDescription("Kilo başı komisyon, lira");
        if (zId > 0) { rate.setEnabled(false); rate.setFocusable(false); }
        rate.addTextChangedListener(watch(() -> { c.rate = Math.max(0, TezgahModel.parse(rate.getText().toString())); refresh.run(); saveClosing(); }));
        rc.addView(rate, new LinearLayout.LayoutParams(-1, dp(46)));
        money.addView(rc, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout ac = new LinearLayout(this);
        ac.setOrientation(LinearLayout.VERTICAL);
        ac.addView(text("Sabah alınan avans (₺)", 12, MUTED, true));
        final android.widget.EditText adv = field(c.advance > 0 ? TezgahQr.field(c.advance) : "", "0");
        adv.setContentDescription("Tüccardan alınan avans, lira");
        if (zId > 0) { adv.setEnabled(false); adv.setFocusable(false); }
        adv.addTextChangedListener(watch(() -> { c.advance = Math.max(0, TezgahModel.parse(adv.getText().toString())); refresh.run(); saveClosing(); }));
        ac.addView(adv, new LinearLayout.LayoutParams(-1, dp(46)));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, -2, 1);
        ap.setMarginStart(dp(10));
        money.addView(ac, ap);
        box.addView(money, margins(-1, -2, 12));

        box.addView(results, margins(-1, -2, 12));
        refresh.run();

        TextView send = text("Tüccara gönder (WhatsApp)", 16, BG, true);
        send.setGravity(Gravity.CENTER);
        send.setBackground(round(GREEN, 0, 16));
        send.setOnClickListener(v -> {
            saveClosing();
            String label = zId > 0 ? zRecord.label() + " · " + DM_HM.format(new Date(zRecord.closedAt))
                    : new SimpleDateFormat("d MMMM yyyy HH:mm", TR).format(new Date());
            TezgahShare.shareText(this, c.summary(label, TezgahShare.traderName()), "");
        });
        box.addView(send, margins(-1, dp(52), 14));
        if (zId <= 0) {
            // POS'taki gibi gün sonu: dönemi kapatır, Z raporu üretir.
            TextView end = text("Gün sonu al (Z raporu)", 16, GREEN, true);
            end.setGravity(Gravity.CENTER);
            end.setBackground(round(Color.TRANSPARENT, GREEN, 16));
            end.setContentDescription("Gün sonu al: alımları kilitle ve Z raporu oluştur");
            end.setOnClickListener(v -> confirmDayEnd(list));
            box.addView(end, margins(-1, dp(52), 10));
        }
        return box;
    }

    private void renderClosingResults(LinearLayout results, TezgahClosing c) {
        results.removeAllViews();
        boolean same = true;
        for (int g = 0; g < TezgahClosing.N; g++) same &= Math.abs(c.fire(g)) < 0.05;
        resultRow(results, "Teslim", TezgahModel.kg(c.deliveredKg()) + (same ? " · alınanla aynı" : ""), TEXT);
        if (!same) {
            String fire = TezgahModel.kg(c.fireKg()) + "  (%" + TezgahClosing.oneDecimal(c.firePercent()) + ")"
                    + (Math.abs(c.fireValue()) >= 1 ? "  ≈ " + TezgahModel.tl(c.fireValue()) : "");
            resultRow(results, "Fire", fire, c.fireKg() > 0 ? AMBER : GREEN);
        }
        resultRow(results, "Üreticilere tutar", TezgahModel.tl(c.producerTotal), TEXT);
        if (c.producerDue() > 0.5) resultRow(results, "  · üreticilere kalan borç", TezgahModel.tl(c.producerDue()), AMBER);
        resultRow(results, "Komisyon", c.rate > 0 ? TezgahModel.kg(c.deliveredKg()) + " × " + TezgahModel.money(c.rate)
                + " = " + TezgahModel.tl(c.commission()) : "—", c.rate > 0 ? GREEN : MUTED);
        if (c.advance > 0) resultRow(results, "Avans", "− " + TezgahModel.tl(c.advance), TEXT);
        View line = new View(this);
        line.setBackgroundColor(LINE);
        results.addView(line, margins(-1, Math.max(1, dp(1)), 6));
        double owes = c.traderOwes();
        LinearLayout total = new LinearLayout(this);
        total.setGravity(Gravity.CENTER_VERTICAL);
        total.addView(text(owes >= 0 ? "TÜCCAR ÖDEYECEK" : "TEZGAH İADE EDECEK", 13, MUTED, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        total.addView(text(TezgahModel.tl(Math.abs(owes)), 24, owes >= 0 ? GREEN : AMBER, true));
        results.addView(total, margins(-1, -2, 8));
    }

    private void resultRow(LinearLayout parent, String label, String value, int color) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(28));
        r.addView(text(label, 14, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = text(value, 15, color, true);
        v.setGravity(Gravity.END);
        r.addView(v);
        parent.addView(r);
    }

    private android.widget.EditText field(String value, String hint) {
        android.widget.EditText e = new android.widget.EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
        e.setSingleLine(true);
        e.setSelectAllOnFocus(true);
        e.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        e.setTextSize(16);
        e.setTypeface(Typeface.DEFAULT_BOLD);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setPadding(dp(10), 0, dp(10), 0);
        e.setBackground(round(BG, LINE, 10));
        return e;
    }

    private android.text.TextWatcher watch(Runnable r) {
        return new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) { r.run(); }
        };
    }

    private View header(List<TezgahPurchases.Purchase> list) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(52));
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_elek_back_chevron);
        back.setColorFilter(TEXT);
        back.setPadding(dp(11), dp(11), dp(11), dp(11));
        back.setContentDescription("Geri");
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(40), dp(44));
        bp.setMarginStart(-dp(10));
        row.addView(back, bp);
        row.addView(text(zId > 0 ? "Gün sonu " + zRecord.label() : "Alım defteri", 21, TEXT, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        TextView excel = text("Rapor", 14, list.isEmpty() ? MUTED : GREEN, true);
        excel.setGravity(Gravity.CENTER);
        excel.setPadding(dp(14), 0, dp(14), 0);
        excel.setBackground(round(Color.TRANSPARENT, list.isEmpty() ? LINE : GREEN, 20));
        excel.setContentDescription("Gün raporunu aç: alım tablosu ve akşam hesabı, resim olarak gönderilebilir");
        excel.setOnClickListener(v -> {
            if (list.isEmpty()) return;
            saveClosing();
            Intent i = new Intent(this, TezgahReportActivity.class);
            if (zId > 0) i.putExtra(TezgahReportActivity.EXTRA_Z_ID, zId);
            startActivity(i);
        });
        row.addView(excel, new LinearLayout.LayoutParams(-2, dp(40)));
        return row;
    }

    private View summary(List<TezgahPurchases.Purchase> list) {
        int kasa = 0;
        double kg = 0, gross = 0, paid = 0;
        for (TezgahPurchases.Purchase p : list) { kasa += p.kasa; kg += p.kg; gross += p.gross; paid += p.paid; }
        double due = Math.max(0, gross - paid);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout r1 = new LinearLayout(this), r2 = new LinearLayout(this);
        r1.addView(tile("TUTAR", TezgahModel.tl(gross), GREEN), tileParams(false));
        r1.addView(tile("NET KİLO", TezgahModel.kg(kg), TEXT), tileParams(true));
        r2.addView(tile("ORTALAMA", kg > 0 ? TezgahModel.money(gross / kg) + "/kg" : "—", TEXT), tileParams(false));
        r2.addView(tile(due > 0.5 ? "BORÇ" : "ALIM", due > 0.5 ? TezgahModel.tl(due) : list.size() + " alım · " + kasa + " kasa",
                due > 0.5 ? AMBER : TEXT), tileParams(true));
        grid.addView(r1);
        grid.addView(r2, margins(-1, -2, 8));
        return grid;
    }

    private View tile(String label, String value, int color) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(14), dp(10), dp(12), dp(10));
        t.setBackground(round(CARD, LINE, 14));
        TextView l = text(label, 11, MUTED, true);
        l.setLetterSpacing(0.08f);
        t.addView(l);
        TextView v = text(value, 19, color, true);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(v, margins(-1, -2, 2));
        return t;
    }

    private LinearLayout.LayoutParams tileParams(boolean right) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        if (right) lp.setMarginStart(dp(8));
        return lp;
    }

    // ================================================================== tablo

    /** Solda sabit ad sütunu + sağda yana kayan sütunlar; satırlar sabit yükseklikte hizalı. */
    private View table(final List<TezgahPurchases.Purchase> list) {
        LinearLayout frame = new LinearLayout(this);
        frame.setBackground(round(CARD, LINE, 14));
        frame.setPadding(dp(1), dp(1), dp(1), dp(1));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);

        // Başlık
        left.addView(headCell("Üretici", NAME_W, Gravity.START | Gravity.CENTER_VERTICAL));
        LinearLayout head = new LinearLayout(this);
        for (int c = 0; c < COLS.length; c++) head.addView(headCell(COLS[c], WIDTHS[c], Gravity.END | Gravity.CENTER_VERTICAL));
        right.addView(head);

        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.US);
        int kasa = 0;
        double kg = 0, gross = 0, paid = 0;
        double[] gsum = new double[TezgahModel.GRADES.length];
        for (int i = 0; i < list.size(); i++) {
            final TezgahPurchases.Purchase p = list.get(i);
            int bg = i % 2 == 0 ? CARD : ROW_ALT;
            double[] g = TezgahModel.decode(p.data).netArray();
            for (int k = 0; k < g.length; k++) gsum[k] += g[k];
            kasa += p.kasa; kg += p.kg; gross += p.gross; paid += p.paid;

            LinearLayout nameCell = new LinearLayout(this);
            nameCell.setOrientation(LinearLayout.VERTICAL);
            nameCell.setGravity(Gravity.CENTER_VERTICAL);
            nameCell.setPadding(dp(10), 0, dp(6), 0);
            nameCell.setBackgroundColor(bg);
            TextView n = text(p.seller.isEmpty() ? "Adsız" : p.seller, 14, TEXT, true);
            n.setSingleLine(true);
            n.setEllipsize(TextUtils.TruncateAt.END);
            nameCell.addView(n);
            nameCell.addView(text(hm.format(new Date(p.createdAt)), 11, MUTED, false));
            nameCell.setOnClickListener(v -> actions(p));
            left.addView(nameCell, new LinearLayout.LayoutParams(dp(NAME_W), dp(ROW_H)));
            left.addView(rule(NAME_W));

            LinearLayout r = new LinearLayout(this);
            r.setBackgroundColor(bg);
            r.setOnClickListener(v -> actions(p));
            String[] vals = {
                    String.valueOf(p.kasa),
                    num(g[0]), num(g[1]), num(g[2]), num(g[3]), num(g[4]), num(g[5]),
                    num(p.kg),
                    p.kg > 0 ? price(p.gross / p.kg) : "—",
                    TezgahModel.tl(p.gross),
                    TezgahModel.tl(p.paid),
                    p.remaining() > 0.5 ? TezgahModel.tl(p.remaining()) : "—"};
            for (int c = 0; c < vals.length; c++) {
                int color = c == 9 ? GREEN : (c == 11 && p.remaining() > 0.5 ? AMBER : ("—".equals(vals[c]) ? MUTED : TEXT));
                r.addView(cell(vals[c], WIDTHS[c], color, c == 7 || c == 9));
            }
            right.addView(r, new LinearLayout.LayoutParams(-2, dp(ROW_H)));
            right.addView(rule(totalWidth()));
        }

        // TOPLAM satırı
        TextView tl = text("TOPLAM · " + list.size(), 14, BG, true);
        tl.setGravity(Gravity.CENTER_VERTICAL);
        tl.setPadding(dp(10), 0, dp(6), 0);
        tl.setBackgroundColor(GREEN);
        left.addView(tl, new LinearLayout.LayoutParams(dp(NAME_W), dp(ROW_H)));
        LinearLayout tr = new LinearLayout(this);
        tr.setBackgroundColor(GREEN);
        String[] tv = {
                String.valueOf(kasa),
                num(gsum[0]), num(gsum[1]), num(gsum[2]), num(gsum[3]), num(gsum[4]), num(gsum[5]),
                num(kg),
                kg > 0 ? price(gross / kg) : "—",
                TezgahModel.tl(gross),
                TezgahModel.tl(paid),
                gross - paid > 0.5 ? TezgahModel.tl(gross - paid) : "—"};
        for (int c = 0; c < tv.length; c++) tr.addView(cell(tv[c], WIDTHS[c], BG, true));
        right.addView(tr, new LinearLayout.LayoutParams(-2, dp(ROW_H)));

        frame.addView(left, new LinearLayout.LayoutParams(dp(NAME_W), -2));
        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        frame.addView(divider, new LinearLayout.LayoutParams(dp(2), -1));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(true);
        hs.addView(right);
        frame.addView(hs, new LinearLayout.LayoutParams(0, -2, 1));
        return frame;
    }

    private int totalWidth() {
        int w = 0;
        for (int v : WIDTHS) w += v;
        return w;
    }

    private TextView headCell(String label, int widthDp, int gravity) {
        TextView t = text(label, 12, MUTED, true);
        t.setGravity(gravity);
        t.setPadding(dp(10), 0, dp(10), 0);
        t.setBackgroundColor(HEAD);
        t.setSingleLine(true);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), dp(HEAD_H)));
        return t;
    }

    private TextView cell(String value, int widthDp, int color, boolean bold) {
        TextView t = text(value, 14, color, bold);
        t.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        t.setPadding(dp(6), 0, dp(10), 0);
        t.setSingleLine(true);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), -1));
        return t;
    }

    private View rule(int widthDp) {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), Math.max(1, dp(1) / 2)));
        return v;
    }

    /** Tabloda kilo: "238", "42,5", sıfırsa "—". */
    private static String num(double v) {
        if (v <= 0.04) return "—";
        String s = TezgahModel.kg(v);
        return s.substring(0, s.length() - 3);
    }

    private static String price(double v) {
        String s = TezgahModel.money(v);
        return s.substring(0, s.length() - 2);
    }

    // ================================================================== satır işlemleri

    private void actions(final TezgahPurchases.Purchase p) {
        final TezgahModel m = TezgahModel.decode(p.data);
        final String name = p.seller.isEmpty() ? "Adsız" : p.seller;
        final String receipt = m.receipt("Zeytin alım fişi", "Satan", p.seller, displayDate(p), p.paid);
        java.util.List<String> opts = new java.util.ArrayList<>();
        opts.add("QR göster");
        opts.add("Fişi gönder (WhatsApp)");
        opts.add("Fişi yazdır");
        if (TezgahStatement.hasStatement(p.seller)) opts.add("Üretici ekstresi · " + p.seller.trim());
        if (p.remaining() > 0.5) opts.add("Ödeme yap · kalan " + TezgahModel.tl(p.remaining()));
        else if (p.paid > 0.5) opts.add("Ödeme geçmişi");
        if (p.zId <= 0) opts.add("Sil");   // gün sonu alınmış alım kilitli
        final String[] items = opts.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(name + " · " + TezgahModel.kg(p.kg) + " · " + TezgahModel.tl(p.gross))
                .setItems(items, (d, i) -> {
                    String a = items[i];
                    if (a.startsWith("QR")) {
                        TezgahShare.showQr(this, m, p.date, p.seller, p.paid, receipt, p.phone);
                    } else if (a.startsWith("Fişi gönder")) {
                        TezgahShare.shareText(this, receipt, p.phone);
                    } else if (a.startsWith("Fişi yazdır")) {
                        TezgahPrinter.print(this, TezgahPrintFormat.receipt(m, TezgahShare.traderName(), p.seller,
                                displayDate(p), p.paid, TezgahPrinter.width(this)));
                    } else if (a.startsWith("Üretici")) {
                        openStatement(p.seller);
                    } else if (a.startsWith("Ödeme")) {
                        // Bir kısmı ya da tamamı; Z kilitli alımda da yalnız ödeme işlenir.
                        TezgahPaySheet.show(this, db.getWritableDatabase(), owner, p, this::render);
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("Alım silinsin mi?")
                                .setMessage(name + " · " + TezgahModel.kg(p.kg) + " · " + TezgahModel.tl(p.gross))
                                .setPositiveButton("Sil", (x, y) -> {
                                    TezgahPurchases.delete(db.getWritableDatabase(), owner, p.id);
                                    render();
                                })
                                .setNegativeButton("Vazgeç", null)
                                .show();
                    }
                })
                .show();
    }

    private static String displayDate(TezgahPurchases.Purchase p) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(new Date(p.createdAt));
    }

    // ================================================================== yardımcılar

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }

    private LinearLayout.LayoutParams margins(int w, int h, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.topMargin = dp(topDp);
        return lp;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
