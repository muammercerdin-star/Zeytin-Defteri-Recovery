package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * Elek kaydı: "Defterden oku". Defter sayfasının fotoğrafı (kamera ya da galeri) telefonda okunur (Google ML Kit,
 * internetsiz, ücretsiz, görüntü hiçbir yere gönderilmez), satırlar NotebookOcr ile çözülür ve kontrol ekranı açılır.
 * Kullanıcı onaylamadan forma hiçbir şey yazılmaz; kayıt yine Kaydet düğmesiyle yapılır.
 */
public final class NotebookScanUi {
    public static final int REQ_CAMERA = 7301, REQ_GALLERY = 7302;

    /** kg[g] ve price[g]: forma yazılacak değerler; price[g] NaN ise o sınıfın fiyatı değişmez. */
    public interface Apply { void apply(double[] kg, double[] price); }

    private static final int BG = Color.parseColor("#050F16");
    private static final int SHEET = Color.parseColor("#0C1C27");
    private static final int CARD = Color.parseColor("#0F2230");
    private static final int LINE = Color.parseColor("#2A4452");
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.parseColor("#A9BAB6");
    private static final int GREEN = Color.parseColor("#8BE06A");
    private static final int GREEN_DIM = Color.parseColor("#1E3D2C");
    private static final int AMBER = Color.parseColor("#F2C35B");

    private NotebookScanUi() {}

    // ------------------------------------------------------------------ fotoğraf seçimi

    public static void start(Activity a) {
        new AlertDialog.Builder(a)
                .setTitle("Defterden oku")
                .setMessage("Elek defterindeki sayfanın fotoğrafını çek. Kilo × fiyat = tutar satırları okunur, "
                        + "sen kontrol edip forma aktarırsın.\n\nİpucu: Sayfayı düz tut, iyi ışıkta çek, yalnız tablo kadrajda olsun.\n\n"
                        + "Fotoğraf telefonda okunur, hiçbir yere gönderilmez.")
                .setPositiveButton("Fotoğraf çek", (d, w) -> camera(a))
                .setNeutralButton("Galeriden seç", (d, w) -> gallery(a))
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private static void camera(Activity a) {
        NotebookPhotoProvider.clear(a);
        Uri out = NotebookPhotoProvider.uri(a);
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(MediaStore.EXTRA_OUTPUT, out);
        i.setClipData(ClipData.newRawUri("defter", out));
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            a.startActivityForResult(i, REQ_CAMERA);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(a, "Kamera açılamadı. Galeriden seçebilirsin.", Toast.LENGTH_LONG).show();
            gallery(a);
        }
    }

    private static void gallery(Activity a) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            a.startActivityForResult(Intent.createChooser(i, "Defter fotoğrafını seç"), REQ_GALLERY);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(a, "Fotoğraf seçilemedi.", Toast.LENGTH_LONG).show();
        }
    }

    /** Activity.onActivityResult'tan çağrılır; bu ekranın isteği değilse false döner. */
    public static boolean handle(Activity a, int requestCode, int resultCode, Intent data, Apply apply) {
        if (requestCode != REQ_CAMERA && requestCode != REQ_GALLERY) return false;
        if (resultCode != Activity.RESULT_OK) {
            if (requestCode == REQ_CAMERA) NotebookPhotoProvider.clear(a);
            return true;
        }
        Uri uri = requestCode == REQ_CAMERA ? NotebookPhotoProvider.uri(a) : (data == null ? null : data.getData());
        if (uri == null || (requestCode == REQ_CAMERA && NotebookPhotoProvider.file(a).length() == 0)) {
            Toast.makeText(a, "Fotoğraf alınamadı. Tekrar dene.", Toast.LENGTH_LONG).show();
            return true;
        }
        read(a, uri, requestCode == REQ_CAMERA, apply);
        return true;
    }

    // ------------------------------------------------------------------ okuma (telefonda)

    private static void read(Activity a, Uri uri, boolean ownPhoto, Apply apply) {
        final Dialog wait = waitDialog(a);
        final InputImage image;
        try {
            image = InputImage.fromFilePath(a, uri);
        } catch (Exception e) {
            wait.dismiss();
            if (ownPhoto) NotebookPhotoProvider.clear(a);
            Toast.makeText(a, "Fotoğraf açılamadı. Tekrar dene.", Toast.LENGTH_LONG).show();
            return;
        }
        final TextRecognizer recognizer;
        try {
            recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        } catch (RuntimeException e) {
            wait.dismiss();
            Toast.makeText(a, "Bu telefonda yazı okuma açılamadı.", Toast.LENGTH_LONG).show();
            return;
        }
        recognizer.process(image)
                .addOnSuccessListener(text -> {
                    safeClose(recognizer);
                    if (ownPhoto) NotebookPhotoProvider.clear(a);
                    if (a.isFinishing()) return;
                    wait.dismiss();
                    List<NotebookOcr.Piece> pieces = new ArrayList<>();
                    for (Text.TextBlock b : text.getTextBlocks()) {
                        for (Text.Line l : b.getLines()) {
                            Rect r = l.getBoundingBox();
                            if (r == null) continue;
                            pieces.add(new NotebookOcr.Piece(l.getText(), r.left, r.top, r.bottom));
                        }
                    }
                    NotebookOcr.Result res = NotebookOcr.parse(NotebookOcr.lines(pieces));
                    if (res.filled() == 0) notFound(a, apply);
                    else review(a, res, apply);
                })
                .addOnFailureListener(e -> {
                    safeClose(recognizer);
                    if (ownPhoto) NotebookPhotoProvider.clear(a);
                    if (a.isFinishing()) return;
                    wait.dismiss();
                    new AlertDialog.Builder(a)
                            .setTitle("Okunamadı")
                            .setMessage("Yazı okuma modülü hazır değil ya da fotoğraf okunamadı. İlk kullanımda Google "
                                    + "Play hizmetleri modülü bir kez indirir; internete bağlanıp birkaç dakika sonra tekrar dene.")
                            .setPositiveButton("Tamam", null)
                            .show();
                });
    }

    private static void safeClose(TextRecognizer r) {
        try { r.close(); } catch (RuntimeException ignored) { }
    }

    private static void notFound(Activity a, Apply apply) {
        new AlertDialog.Builder(a)
                .setTitle("Satır bulunamadı")
                .setMessage("Fotoğrafta \"kilo x fiyat tutar\" satırı okunamadı.\n\n"
                        + "• Sayfayı düz tut, gölge düşmesin\n• Yalnız tablo kadrajda olsun\n• Yazı net çıksın, yaklaş\n\n"
                        + "Olmazsa kiloları elle girebilirsin.")
                .setPositiveButton("Tekrar çek", (d, w) -> camera(a))
                .setNegativeButton("Kapat", null)
                .show();
    }

    private static Dialog waitDialog(Activity a) {
        float d = a.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        int p = Math.round(20 * d);
        box.setPadding(p, p, p, p);
        box.addView(new ProgressBar(a));
        TextView t = new TextView(a);
        t.setText("Defter okunuyor…");
        t.setTextSize(16);
        t.setPadding(p, 0, 0, 0);
        box.addView(t);
        AlertDialog dlg = new AlertDialog.Builder(a).setView(box).setCancelable(false).create();
        dlg.show();
        return dlg;
    }

    // ------------------------------------------------------------------ kontrol ekranı

    private static void review(Activity a, NotebookOcr.Result res, Apply apply) {
        new Review(a, res, apply).open();
    }

    private static final class Review {
        final Activity a;
        final NotebookOcr.Result res;
        final Apply apply;
        final float density;
        final EditText[] kg = new EditText[NotebookOcr.GRADES];
        final EditText[] price = new EditText[NotebookOcr.GRADES];
        final TextView[] state = new TextView[NotebookOcr.GRADES];
        TextView summary;

        Review(Activity a, NotebookOcr.Result res, Apply apply) {
            this.a = a; this.res = res; this.apply = apply;
            this.density = a.getResources().getDisplayMetrics().density;
        }

        void open() {
            final Dialog sheet = new Dialog(a);
            sheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
            ScrollView scroll = new ScrollView(a);
            LinearLayout box = new LinearLayout(a);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(18), dp(10), dp(18), dp(18));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(SHEET);
            float r = dp(26);
            bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
            bg.setStroke(dp(1), LINE);
            scroll.setBackground(bg);
            scroll.addView(box);

            View handle = new View(a);
            handle.setBackground(round(LINE, 0, 3));
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(44), dp(5));
            hp.gravity = Gravity.CENTER_HORIZONTAL;
            box.addView(handle, hp);

            TextView title = text("Defterden okunanlar", 20, TEXT, true);
            title.setPadding(0, dp(12), 0, 0);
            box.addView(title);
            TextView sub = text("Defterle karşılaştır; yanlış okunan rakamı düzelt. Kilo × fiyat defterdeki tutarı "
                    + "tutuyorsa ✓ çıkar.", 13, MUTED, false);
            sub.setPadding(0, dp(4), 0, dp(10));
            box.addView(sub);

            LinearLayout head = new LinearLayout(a);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.addView(text("Sınıf", 11, MUTED, true), new LinearLayout.LayoutParams(dp(78), -2));
            head.addView(text("Kilo", 11, MUTED, true), new LinearLayout.LayoutParams(dp(74), -2));
            head.addView(text("", 11, MUTED, true), new LinearLayout.LayoutParams(dp(16), -2));
            head.addView(text("Fiyat", 11, MUTED, true), new LinearLayout.LayoutParams(dp(64), -2));
            TextView ht = text("Defterde", 11, MUTED, true);
            ht.setGravity(Gravity.END);
            head.addView(ht, new LinearLayout.LayoutParams(0, -2, 1));
            box.addView(head);

            for (int g = 0; g < NotebookOcr.GRADES; g++) box.addView(row(g), margins(6));

            summary = text("", 14, TEXT, true);
            summary.setPadding(dp(12), dp(10), dp(12), dp(10));
            summary.setBackground(round(CARD, LINE, 12));
            box.addView(summary, margins(12));

            TextView privacy = text("Fiyatlar yalnız bu kayıt için kullanılır; kaydettikten sonra kendi fiyatların geri gelir. "
                    + "Fotoğraf telefonda okundu ve silindi.", 12, MUTED, false);
            privacy.setPadding(0, dp(8), 0, 0);
            box.addView(privacy);

            TextView go = text("Forma aktar", 18, BG, true);
            go.setGravity(Gravity.CENTER);
            go.setBackground(round(GREEN, 0, 18));
            go.setOnClickListener(v -> {
                double[] k = new double[NotebookOcr.GRADES], p = new double[NotebookOcr.GRADES];
                for (int g = 0; g < NotebookOcr.GRADES; g++) {
                    k[g] = Math.max(0, num(kg[g]));
                    double pv = num(price[g]);
                    p[g] = pv > 0 ? pv : Double.NaN;
                }
                sheet.dismiss();
                apply.apply(k, p);
            });
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, dp(56));
            gp.topMargin = dp(14);
            box.addView(go, gp);

            LinearLayout alt = new LinearLayout(a);
            alt.setOrientation(LinearLayout.HORIZONTAL);
            TextView again = text("Yeniden çek", 15, GREEN, true);
            again.setGravity(Gravity.CENTER);
            again.setBackground(round(Color.TRANSPARENT, LINE, 16));
            again.setOnClickListener(v -> { sheet.dismiss(); camera(a); });
            TextView cancel = text("Vazgeç", 15, MUTED, true);
            cancel.setGravity(Gravity.CENTER);
            cancel.setBackground(round(Color.TRANSPARENT, LINE, 16));
            cancel.setOnClickListener(v -> sheet.dismiss());
            LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, dp(48), 1);
            LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, dp(48), 1);
            l2.setMarginStart(dp(10));
            alt.addView(again, l1);
            alt.addView(cancel, l2);
            box.addView(alt, margins(10));

            refresh();
            sheet.setContentView(scroll);
            Window w = sheet.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
                w.setGravity(Gravity.BOTTOM);
                w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            sheet.show();
        }

        private View row(int g) {
            NotebookOcr.Row r = res.rows[g];
            LinearLayout wrap = new LinearLayout(a);
            wrap.setOrientation(LinearLayout.VERTICAL);
            wrap.setPadding(dp(10), dp(8), dp(10), dp(8));
            wrap.setBackground(round(CARD, LINE, 12));
            LinearLayout line = new LinearLayout(a);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text(TezgahModel.LABELS[g], 14, TEXT, true);
            line.addView(name, new LinearLayout.LayoutParams(dp(70), -2));
            kg[g] = field(r != null && r.hasKg ? TezgahQr.field(r.kg) : "");
            line.addView(kg[g], new LinearLayout.LayoutParams(dp(74), dp(44)));
            TextView x = text("×", 15, MUTED, true);
            x.setGravity(Gravity.CENTER);
            line.addView(x, new LinearLayout.LayoutParams(dp(16), -2));
            price[g] = field(r != null && r.hasPrice ? TezgahQr.field(r.price) : "");
            line.addView(price[g], new LinearLayout.LayoutParams(dp(64), dp(44)));
            TextView book = text(r != null && r.hasAmount ? TezgahModel.money(r.amount) : "—", 14, TEXT, true);
            book.setGravity(Gravity.END);
            line.addView(book, new LinearLayout.LayoutParams(0, -2, 1));
            wrap.addView(line);
            state[g] = text("", 12, MUTED, false);
            state[g].setPadding(0, dp(4), 0, 0);
            wrap.addView(state[g]);
            TextWatcher tw = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
                @Override public void afterTextChanged(Editable s) { refresh(); }
            };
            kg[g].addTextChangedListener(tw);
            price[g].addTextChangedListener(tw);
            return wrap;
        }

        private void refresh() {
            if (summary == null) return;
            double sumKg = 0, sumTl = 0;
            int bad = 0;
            for (int g = 0; g < NotebookOcr.GRADES; g++) {
                if (state[g] == null) continue;
                NotebookOcr.Row r = res.rows[g];
                double k = num(kg[g]), p = num(price[g]);
                boolean hasAmt = r != null && r.hasAmount;
                int st = NotebookOcr.status(k, p, hasAmt, hasAmt ? r.amount : 0);
                if (k > 0) { sumKg += k; if (p > 0) sumTl += k * p; }
                switch (st) {
                    case NotebookOcr.OK:
                        state[g].setText("✓ " + TezgahModel.kg(k) + " × " + TezgahModel.money(p) + " defterdeki tutarı tutuyor");
                        state[g].setTextColor(GREEN);
                        break;
                    case NotebookOcr.MISMATCH: {
                        bad++;
                        String s = "⚠ Tutmuyor: " + TezgahModel.money(k * p) + " çıkıyor, defterde " + TezgahModel.money(r.amount) + ".";
                        double sug = p > 0 && r.amount / p > 0 ? suggest(r.amount / p) : Double.NaN;
                        if (!Double.isNaN(sug)) s += " Kilo " + TezgahModel.kg(sug) + " olabilir.";
                        state[g].setText(s);
                        state[g].setTextColor(AMBER);
                        break;
                    }
                    case NotebookOcr.UNCHECKED:
                        state[g].setText(k > 0 && p <= 0 ? "Fiyat okunamadı; mevcut fiyatın kullanılır."
                                : "Defterde tutar yok ya da okunamadı; rakamları kontrol et.");
                        state[g].setTextColor(MUTED);
                        break;
                    default:
                        state[g].setText("Okunmadı (boş bırakılırsa 0 kg).");
                        state[g].setTextColor(MUTED);
                }
            }
            String s = "Okunan: " + TezgahModel.kg(sumKg) + " · " + TezgahModel.money(sumTl);
            int color = TEXT;
            if (res.hasTotalAmount) {
                boolean ok = NotebookOcr.close(sumTl, res.totalAmount);
                s += "\nDefter toplamı: " + TezgahModel.money(res.totalAmount) + (ok ? "  ✓ tutuyor" : "  ⚠ tutmuyor");
                color = ok ? GREEN : AMBER;
            }
            if (bad > 0) { s += "\n" + bad + " satırı kontrol et."; color = AMBER; }
            summary.setText(s);
            summary.setTextColor(color);
        }

        private double suggest(double k) {
            double r = Math.round(k * 2) / 2.0;
            return r > 0 && Math.abs(k - r) < 0.011 ? r : Double.NaN;
        }

        private double num(EditText e) {
            if (e == null) return 0;
            String s = e.getText().toString().trim();
            if (s.isEmpty()) return 0;
            double v = TezgahModel.parse(s);
            return Double.isNaN(v) ? 0 : v;
        }

        private EditText field(String v) {
            EditText e = new EditText(a);
            e.setText(v);
            e.setTextSize(16);
            e.setTextColor(TEXT);
            e.setTypeface(Typeface.DEFAULT_BOLD);
            e.setSingleLine(true);
            e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            e.setGravity(Gravity.CENTER);
            e.setPadding(dp(4), 0, dp(4), 0);
            e.setBackground(round(BG, GREEN_DIM, 10));
            return e;
        }

        private TextView text(String v, int sp, int color, boolean bold) {
            TextView t = new TextView(a);
            t.setText(v);
            t.setTextSize(sp);
            t.setTextColor(color);
            if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
            return t;
        }

        private GradientDrawable round(int fill, int stroke, int rdp) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(fill);
            g.setCornerRadius(dp(rdp));
            if (stroke != 0) g.setStroke(Math.max(1, dp(1)), stroke);
            return g;
        }

        private LinearLayout.LayoutParams margins(int top) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(top);
            return lp;
        }

        private int dp(int v) { return Math.round(v * density); }
    }
}
