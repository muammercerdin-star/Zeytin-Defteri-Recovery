package com.zeytindefteri.app;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

/**
 * Kenardan kenara (edge-to-edge) ekran desteği.
 *
 * targetSdk 35+ ile Android 15 ve üstünde pencere içeriği durum çubuğunun ve gezinme çubuğunun altına
 * uzanır; Android 16'da bu kapatılamaz. Uygulamanın ekranları çubukların rengini boyayarak çalıştığı için
 * içerik çerçevesine (android.R.id.content) çubuk ve klavye yüksekliği kadar iç boşluk verilir. Böylece:
 * - başlıklar saatin, alt menüler gezinme çubuğunun altında kalmaz;
 * - boşluk alanında pencere arka planı (tema: zd_bg) görünür, eski koyu çubuk görünümü korunur;
 * - klavye açılınca alt boşluk klavye kadar büyür (eski adjustResize davranışı).
 * Boşluk içerik çerçevesinde tüketildiği için alt görünümlere ikinci kez uygulanmaz.
 * Android 14 ve altında pencere zaten çubukların arasında kaldığından hiçbir şey yapılmaz.
 */
public final class SystemBarInsets {
    private SystemBarInsets() {}

    public static void install(Activity activity) {
        if (Build.VERSION.SDK_INT < 35) return;
        View content = activity.findViewById(android.R.id.content);
        if (content == null || content.getTag(R.id.tagSystemBarInsets) != null) return;
        content.setTag(R.id.tagSystemBarInsets, Boolean.TRUE);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsets.CONSUMED;
        });
        content.requestApplyInsets();
    }
}
