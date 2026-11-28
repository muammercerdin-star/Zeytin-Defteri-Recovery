package com.zeytindefteri.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

/**
 * "Sıra ekle" — QR okutamayan çiftçi (telefonu bozuk, okutamadı) için elle giriş: yağhane kısa adı + kod +
 * (telefon son 2 hanesi ya da kasa sayısı). Web'deki /bul ile aynı; token yalnız telefonda saklanır.
 */
public final class SiraEkleActivity extends Activity {

    private final int BG = GreenTheme.color(R.color.zd_bg);
    private final int CARD = GreenTheme.color(R.color.zd_card);
    private final int GREEN = GreenTheme.color(R.color.zd_green);
    private final int TEXT = GreenTheme.color(R.color.zd_text);
    private final int MUTED = GreenTheme.color(R.color.zd_muted);
    private final int BORDER = GreenTheme.color(R.color.zd_border);
    private final int WARN = GreenTheme.color(R.color.zd_warning);

    private EditText fabrika;
    private EditText kod;
    private EditText ek;
    private TextView mesaj;
    private Button dugme;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(16), dp(18), dp(28));
        sv.addView(body);
        setContentView(sv);

        TextView geri = metin("‹  Geri", 16, GREEN, true);
        geri.setPadding(0, dp(4), 0, dp(10));
        geri.setOnClickListener(v -> finish());
        body.addView(geri);

        body.addView(metin("Sıranı ekle", 24, TEXT, true));
        body.addView(not("Yağhaneden aldığın bilgilerle sıranı takip et; yaklaşınca haber veririz. QR okutmana gerek yok."));

        fabrika = alan(body, "Yağhane (kısa ad)", InputType.TYPE_CLASS_TEXT);
        kod = alan(body, "Kod (fişteki 4 hane)", InputType.TYPE_CLASS_NUMBER);
        ek = alan(body, "Telefonunun son 2 hanesi (telefon vermediysen kasa sayısı)", InputType.TYPE_CLASS_NUMBER);

        dugme = new Button(this);
        dugme.setText("Sıramı bul");
        dugme.setAllCaps(false);
        dugme.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        dugme.setTextColor(0xFF0B1D28);
        dugme.setBackground(round(GREEN, 16, Color.TRANSPARENT));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
        dlp.topMargin = dp(18);
        dugme.setLayoutParams(dlp);
        dugme.setOnClickListener(v -> bul());
        body.addView(dugme);

        mesaj = metin("", 15, WARN, false);
        mesaj.setPadding(0, dp(12), 0, 0);
        body.addView(mesaj);
    }

    private void bul() {
        final String f = fabrika.getText().toString().trim().toLowerCase(Locale.ROOT);
        final String k = kod.getText().toString().trim();
        final String e = ek.getText().toString().trim();
        if (f.isEmpty() || k.isEmpty() || e.isEmpty()) {
            mesaj.setTextColor(WARN);
            mesaj.setText("Üç alanı da doldur.");
            return;
        }
        dugme.setEnabled(false);
        mesaj.setTextColor(MUTED);
        mesaj.setText("Aranıyor…");
        new Thread(() -> {
            final String token = SiraBulut.bul(f, k, e);
            runOnUiThread(() -> {
                dugme.setEnabled(true);
                if (token == null) {
                    mesaj.setTextColor(WARN);
                    mesaj.setText("Bu bilgilerle sıra bulunamadı. Yağhane adını, kodu ve son 2 haneyi kontrol et.");
                    return;
                }
                new SiraDefteri(new SiraDepo(this)).ekle(token);
                startActivity(new Intent(this, SiramActivity.class).putExtra(SiramActivity.EXTRA_TOKEN, token));
                finish();
            });
        }, "zd-sira-bul").start();
    }

    private EditText alan(LinearLayout body, String etiket, int tur) {
        body.addView(metin(etiket, 13, MUTED, false), params(dp(14)));
        EditText e = new EditText(this);
        e.setInputType(tur);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        e.setTextColor(TEXT);
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        e.setBackground(round(CARD, 14, BORDER));
        body.addView(e, params(dp(5)));
        return e;
    }

    private LinearLayout.LayoutParams params(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = top;
        return p;
    }

    private TextView metin(String s, int sp, int renk, boolean kalin) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(renk);
        if (kalin) {
            t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return t;
    }

    private TextView not(String s) {
        TextView t = metin(s, 14, MUTED, false);
        t.setPadding(0, dp(6), 0, dp(4));
        return t;
    }

    private GradientDrawable round(int renk, int kose, int cizgi) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(renk);
        g.setCornerRadius(dp(kose));
        if (cizgi != Color.TRANSPARENT) {
            g.setStroke(dp(1), cizgi);
        }
        return g;
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }
}
