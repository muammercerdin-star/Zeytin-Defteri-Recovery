package com.zeytindefteri.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Kapatılamaz "Güncelleme gerekli" ekranı. Veriler telefonda kalır; güncellemeden sonra kaldığın yerden devam edilir.
 * Geri tuşu uygulamadan çıkar. Sürüm yeterliyse (güncellendi ya da ayar değişti) kendiliğinden kapanır.
 */
public final class UpdateRequiredActivity extends Activity {
    static final String EXTRA_MESSAGE = "message";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        int bg = GreenTheme.color(R.color.zd_bg), text = GreenTheme.color(R.color.zd_text),
                muted = GreenTheme.color(R.color.zd_muted), green = GreenTheme.color(R.color.zd_green);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(bg);
        root.setPadding(dp(28), dp(24), dp(28), dp(24));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_olive_logo);
        root.addView(logo, new LinearLayout.LayoutParams(dp(72), dp(72)));

        TextView title = new TextView(this);
        title.setText("Güncelleme gerekli");
        title.setTextSize(24);
        title.setTextColor(text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(18), 0, dp(8));
        root.addView(title);

        String extra = getIntent().getStringExtra(EXTRA_MESSAGE);
        TextView body = new TextView(this);
        body.setText((extra == null || extra.trim().isEmpty()
                ? "Zeytin Defteri'nin bu sürümü artık desteklenmiyor. Devam etmek için uygulamayı güncelle."
                : extra.trim()) + "\n\nKayıtların telefonunda ve hesabında duruyor; güncelledikten sonra kaldığın yerden devam edersin.");
        body.setTextSize(16);
        body.setTextColor(muted);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(0, 1.2f);
        root.addView(body);

        TextView update = new TextView(this);
        update.setText("Güncelle");
        update.setTextSize(17);
        update.setTypeface(Typeface.DEFAULT_BOLD);
        update.setTextColor(bg);
        update.setGravity(Gravity.CENTER);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(green);
        shape.setCornerRadius(dp(28));
        update.setBackground(shape);
        update.setClickable(true);
        update.setFocusable(true);
        update.setOnClickListener(v -> UpdateGate.openStore(this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(56));
        lp.topMargin = dp(28);
        root.addView(update, lp);
        setContentView(root);
        // Geri tuşu (Android 13+ dahil) uygulamadan çıkar; ekranı atlayıp uygulamaya dönülmez.
        BackCompat.register(this, this::finishAffinity);
    }

    @Override protected void onResume() {
        super.onResume();
        int min = getSharedPreferences("zd_update", Context.MODE_PRIVATE).getInt("min", 0);
        if (UpdateGate.decide(UpdateGate.currentVersion(), min, 0) != UpdateGate.REQUIRED) {
            // Güncellendi ya da zorunluluk kaldırıldı: uygulama baştan açılır.
            startActivity(getPackageManager().getLaunchIntentForPackage(getPackageName()));
            finish();
        }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
