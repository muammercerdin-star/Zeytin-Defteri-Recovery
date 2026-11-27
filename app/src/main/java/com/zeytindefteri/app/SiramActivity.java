package com.zeytindefteri.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

/**
 * "Sıram" detay ekranı: büyük sıra no, adımlar, tahmin, bilgi. Durumu arka planda SiraBulut'tan çeker.
 *
 * Buraya iki yoldan düşülür: ofis QR'ının derin linki (sira.zeytindefteri.tr/&lt;token&gt;) ya da Sıram
 * bildirimine dokunma. Token YALNIZ telefonda saklanır (SiraDefteri), sunucuya gitmez; sunucu çiftçiyi tanımaz.
 */
public final class SiramActivity extends Activity {

    static final String EXTRA_TOKEN = "zd_sira_token";

    private final int BG = GreenTheme.color(R.color.zd_bg);
    private final int CARD = GreenTheme.color(R.color.zd_card);
    private final int GREEN = GreenTheme.color(R.color.zd_green);
    private final int GREEN_SOFT = GreenTheme.color(R.color.zd_green_soft);
    private final int TEXT = GreenTheme.color(R.color.zd_text);
    private final int MUTED = GreenTheme.color(R.color.zd_muted);
    private final int BORDER = GreenTheme.color(R.color.zd_border);
    private final int WARN = GreenTheme.color(R.color.zd_warning);

    private String token;
    private LinearLayout govde;
    private volatile boolean canli;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        token = tokenBul();
        if (token == null) {
            finish();
            return;
        }
        new SiraDefteri(new SiraDepo(this)).ekle(token);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        govde = new LinearLayout(this);
        govde.setOrientation(LinearLayout.VERTICAL);
        govde.setPadding(dp(16), dp(14), dp(16), dp(28));
        sv.addView(govde, new ScrollView.LayoutParams(-1, -2));
        setContentView(sv);
        baslik();
        bilgiSatiri("Sıran yükleniyor…");
    }

    @Override
    protected void onResume() {
        super.onResume();
        canli = true;
        yenile();
    }

    @Override
    protected void onPause() {
        canli = false;
        super.onPause();
    }

    // ----------------------------------------------------------------- token

    private String tokenBul() {
        String t = getIntent() != null ? getIntent().getStringExtra(EXTRA_TOKEN) : null;
        if (gecerli(t)) {
            return t;
        }
        Uri u = getIntent() != null ? getIntent().getData() : null;
        if (u == null) {
            return null;
        }
        if (gecerli(u.getLastPathSegment())) {
            return u.getLastPathSegment();
        }
        if (gecerli(u.getHost())) {
            return u.getHost();
        }
        return null;
    }

    private static boolean gecerli(String t) {
        return t != null && t.matches("[A-Za-z0-9_-]{22}");
    }

    // ----------------------------------------------------------------- çizim

    private void baslik() {
        TextView geri = metin("‹  Geri", 16, GREEN, true);
        geri.setPadding(0, dp(4), 0, dp(12));
        geri.setOnClickListener(v -> finish());
        govde.addView(geri);
    }

    private void icerikTemizle() {
        while (govde.getChildCount() > 1) {
            govde.removeViewAt(1);
        }
    }

    private void bilgiSatiri(String s) {
        icerikTemizle();
        govde.addView(metin(s, 16, MUTED, false));
    }

    private void yenile() {
        final String t = token;
        new Thread(() -> {
            final SiraDurumu d = SiraBulut.durum(t);
            if (!canli) {
                return;
            }
            runOnUiThread(() -> ciz(d));
        }, "zd-siram").start();
    }

    private void ciz(SiraDurumu d) {
        icerikTemizle();
        if (d == null) {
            govde.addView(metin("Sıraya ulaşılamadı. Sıran tamamlanmış ya da bağlantın yok olabilir.", 16, MUTED, false));
            return;
        }
        TextView fab = metin(d.fabrika + (d.kod.isEmpty() ? "" : " · Kod " + d.kod), 14, MUTED, true);
        fab.setGravity(Gravity.CENTER);
        govde.addView(fab);

        if (d.aktif()) {
            TextView no = metin(String.valueOf(d.siraNo), 72, TEXT, true);
            no.setGravity(Gravity.CENTER);
            govde.addView(no);
            if (!d.onundeMetni().isEmpty()) {
                TextView onunde = metin(d.onundeMetni(), 16, MUTED, false);
                onunde.setGravity(Gravity.CENTER);
                govde.addView(onunde);
            }
        } else if (d.durum.equals("iptal")) {
            TextView ip = metin("Bu sıra iptal edildi.", 18, WARN, true);
            ip.setGravity(Gravity.CENTER);
            ip.setPadding(0, dp(12), 0, dp(4));
            govde.addView(ip);
        }

        String tah = d.gosterilenTahmin();
        if (!tah.isEmpty()) {
            govde.addView(tahminKutu(tah));
        }

        govde.addView(adimlar(d));

        if (d.durum.equals("bitti") && d.randiman() != null) {
            String s = "Yağın hazır: " + kg(d.yagKg) + " kg · randıman %" + String.format(Locale.US, "%.1f", d.randiman());
            TextView y = metin(s, 16, GREEN, true);
            y.setPadding(dp(2), dp(14), dp(2), 0);
            govde.addView(y);
        }

        if (d.aktif()) {
            govde.addView(not("Sıran yaklaşınca telefonuna haber vereceğiz; beklemeden gidebilirsin."));
        }
    }

    private View tahminKutu(String deger) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(13), dp(16), dp(13));
        box.setBackground(round(GREEN_SOFT, 16, Color.TRANSPARENT));
        box.addView(metin("TAHMİNİ İŞLENME", 12, GREEN, true));
        box.addView(metin(deger, 19, TEXT, true));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(16);
        box.setLayoutParams(lp);
        return box;
    }

    private View adimlar(SiraDurumu d) {
        String[] adlar = {"Teslim alındı", "Sırada", "İşleniyor", "Bitti · yağ hazır"};
        int simdi = d.adim();
        LinearLayout kart = new LinearLayout(this);
        kart.setOrientation(LinearLayout.VERTICAL);
        kart.setPadding(dp(16), dp(10), dp(16), dp(10));
        kart.setBackground(round(CARD, 18, BORDER));
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(-1, -2);
        klp.topMargin = dp(14);
        kart.setLayoutParams(klp);
        for (int i = 0; i < adlar.length; i++) {
            boolean tamam = simdi > i && simdi >= 0;
            boolean su = simdi == i;
            int renk = su ? WARN : tamam ? GREEN : BORDER;
            LinearLayout satir = new LinearLayout(this);
            satir.setOrientation(LinearLayout.HORIZONTAL);
            satir.setGravity(Gravity.CENTER_VERTICAL);
            satir.setPadding(0, dp(9), 0, dp(9));
            View nokta = new View(this);
            nokta.setBackground(round(renk, 9, Color.TRANSPARENT));
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(dp(16), dp(16));
            np.rightMargin = dp(13);
            satir.addView(nokta, np);
            int yaziRenk = su || tamam ? TEXT : MUTED;
            satir.addView(metin(adlar[i], 16, yaziRenk, su));
            kart.addView(satir);
        }
        return kart;
    }

    private View not(String s) {
        TextView t = metin(s, 13, MUTED, false);
        t.setPadding(dp(2), dp(16), dp(2), 0);
        return t;
    }

    // ----------------------------------------------------------------- yardımcılar

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

    private GradientDrawable round(int renk, int kose, int cizgi) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(renk);
        g.setCornerRadius(dp(kose));
        if (cizgi != Color.TRANSPARENT) {
            g.setStroke(dp(1), cizgi);
        }
        return g;
    }

    private String kg(Double v) {
        if (v == null) {
            return "0";
        }
        return String.valueOf(Math.round(v));
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }
}
