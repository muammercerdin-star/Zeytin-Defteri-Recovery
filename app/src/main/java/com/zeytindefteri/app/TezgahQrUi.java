package com.zeytindefteri.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

/**
 * QR çizme (ZXing, telefonda, internetsiz) ve okutma (Google Code Scanner).
 * Okutmada kamera Google Play hizmetlerinin ekranında açılır: uygulamaya kamera izni gerekmez,
 * görüntü uygulamaya gelmez, yalnız okunan metin döner.
 */
public final class TezgahQrUi {
    public interface Result { void scanned(String text); }
    public interface Failure { void failed(String message); }

    private TezgahQrUi() {}

    /** Siyah-beyaz QR (güneşte de okunsun diye beyaz zemin). Hata olursa null. */
    public static Bitmap bitmap(String text, int sizePx) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 2);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
            int w = m.getWidth(), h = m.getHeight();
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++) {
                int row = y * w;
                for (int x = 0; x < w; x++) px[row + x] = m.get(x, y) ? Color.BLACK : Color.WHITE;
            }
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            b.setPixels(px, 0, w, 0, 0, w, h);
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    /** QR okutur. Vazgeçilirse hiçbir şey çağrılmaz. */
    public static void scan(Activity activity, Result result, Failure failure) {
        try {
            GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build();
            GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(activity, options);
            scanner.startScan()
                    .addOnSuccessListener(code -> {
                        String raw = code == null ? null : code.getRawValue();
                        if (raw == null) failure.failed("QR okunamadı, tekrar dene.");
                        else result.scanned(raw);
                    })
                    .addOnFailureListener(e -> failure.failed(
                            "QR okuyucu açılamadı. İlk kullanımda internet gerekir (Google okuyucuyu bir kez indirir)."));
        } catch (RuntimeException e) {
            failure.failed("Bu telefonda QR okuyucu açılamadı.");
        }
    }
}
