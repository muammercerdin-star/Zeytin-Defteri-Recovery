package com.zeytindefteri.app;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;

/**
 * Ekranları telefonun gerçek boyutuna göre ölçüler.
 *
 * Android ölçüleri dp (yoğunluktan bağımsız piksel) ile zaten ekran inç/piksel yoğunluğuna göre ayarlar.
 * Sorun, kullanıcı telefonda "Ekran boyutu / Yakınlaştırma" ya da "Yazı boyutu" ayarını büyüttüğünde çıkar:
 * ekran 360 dp'den dar görünür, yazılar taşar, kartlar ekrana sığmaz. Burada:
 * - Ekran genişliği {@link #MIN_WIDTH_DP} dp'den darsa yoğunluk küçültülür; uygulama her telefonda en az
 *   360 dp genişlikte çizilir (tasarımın yapıldığı en küçük genişlik). Normal telefonlarda hiçbir şey değişmez.
 * - Yazı ölçeği {@link #MAX_FONT_SCALE} ile sınırlanır; büyük yazı isteyen kullanıcı yine büyük yazı görür,
 *   ama düzen bozulacak kadar değil.
 * Her ekran açılırken (ZeytinApplication) setContentView'den önce uygulanır.
 */
public final class ScreenFit {
    public static final int MIN_WIDTH_DP = 360;
    public static final float MAX_FONT_SCALE = 1.15f;

    private ScreenFit() {}

    /** Hesap: verilen ayarlardan uygulanacak yoğunluk/yazı ölçeği (test edilebilir, Android'e bağlı değil). */
    static int[] fit(int screenWidthDp, int densityDpi) {
        if (screenWidthDp <= 0 || densityDpi <= 0 || screenWidthDp >= MIN_WIDTH_DP) return new int[]{screenWidthDp, densityDpi};
        int dpi = Math.max(120, Math.round(densityDpi * (screenWidthDp / (float) MIN_WIDTH_DP)));
        return new int[]{MIN_WIDTH_DP, dpi};
    }

    public static void apply(Activity activity) {
        try {
            Resources res = activity.getResources();
            Configuration cfg = new Configuration(res.getConfiguration());
            boolean changed = false;
            if (cfg.fontScale > MAX_FONT_SCALE) {
                cfg.fontScale = MAX_FONT_SCALE;
                changed = true;
            }
            int[] fitted = fit(cfg.screenWidthDp, cfg.densityDpi);
            if (fitted[1] != cfg.densityDpi) {
                float f = fitted[1] / (float) cfg.densityDpi;
                cfg.densityDpi = fitted[1];
                cfg.screenWidthDp = fitted[0];
                cfg.screenHeightDp = Math.round(cfg.screenHeightDp / f);
                cfg.smallestScreenWidthDp = Math.round(cfg.smallestScreenWidthDp / f);
                changed = true;
            }
            if (changed) res.updateConfiguration(cfg, res.getDisplayMetrics());
        } catch (RuntimeException ignored) {
            // Ölçekleme yapılamazsa ekran telefonun kendi ayarıyla açılır.
        }
    }

    /**
     * Kaydırma alanının içeriği ekrandan uzunsa esnek görünümü (ör. tanıtım görseli) taşan kadar küçültür,
     * en az minHeightDp kalacak şekilde. Böylece ekran kısa telefonlarda da tek sayfaya sığar.
     */
    public static void shrinkToFit(final ScrollView scroll, final View flexible, final int minHeightDp) {
        if (scroll == null || flexible == null) return;
        scroll.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                scroll.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                if (scroll.getChildCount() == 0) return;
                int overflow = scroll.getChildAt(0).getHeight() - scroll.getHeight();
                if (overflow <= 0) return;
                int min = Math.round(minHeightDp * scroll.getResources().getDisplayMetrics().density);
                int target = Math.max(min, flexible.getHeight() - overflow);
                if (target >= flexible.getHeight()) return;
                if (flexible instanceof android.widget.ImageView) {
                    ((android.widget.ImageView) flexible).setMaxHeight(target);
                }
                flexible.getLayoutParams().height = target;
                flexible.requestLayout();
            }
        });
    }
}
