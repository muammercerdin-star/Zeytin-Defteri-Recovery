package com.zeytindefteri.app;

import android.app.Activity;
import android.os.Build;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

/**
 * Geri tuşu yakalama, Android sürümünden bağımsız.
 *
 * Manifest'te enableOnBackInvokedCallback="true" olduğundan Android 13+ cihazlarda onBackPressed çağrılmaz
 * (Android 16 + targetSdk 36'da zaten çağrılmaz); geri tuşu OnBackInvokedCallback ile yakalanır.
 * Android 12 ve altında ekranlar onBackPressed içinde aynı işi yapar.
 * Dinleyici yalnızca geri tuşu gerçekten yakalanacakken kayıtlı tutulmalı; aksi halde sistemin
 * öngörülü geri animasyonu (predictive back) devre dışı kalır.
 */
public final class BackCompat {
    private BackCompat() {}

    /** Dinleyiciyi kaydeder; kaldırmak için dönen nesne unregister'a verilir (API 33 altında null). */
    public static Object register(Activity activity, Runnable onBack) {
        if (Build.VERSION.SDK_INT < 33) return null;
        OnBackInvokedCallback callback = onBack::run;
        activity.getOnBackInvokedDispatcher()
                .registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
        return callback;
    }

    public static void unregister(Activity activity, Object handle) {
        if (Build.VERSION.SDK_INT < 33 || handle == null) return;
        activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) handle);
    }
}
