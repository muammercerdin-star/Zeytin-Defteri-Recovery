package com.zeytindefteri.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Yalnız web eşleşmesi; mevcut ZXing ile yerel çözümleme. Kamera görüntüsü saklanmaz. */
@SuppressWarnings("deprecation")
public final class TezgahWebScanActivity extends Activity implements SurfaceHolder.Callback {
    private Camera camera;
    private SurfaceView preview;
    private TextView status;
    private Button torch;
    private boolean resumed;
    private boolean surface;
    private boolean light;
    private boolean completed;
    private int generation;
    private long lastFrame;
    private final AtomicBoolean decoding = new AtomicBoolean();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(11, 29, 40));
        status = new TextView(this);
        status.setText("QR'ı çerçeveye al. Odaklamak için görüntüye dokun.");
        status.setTextColor(Color.WHITE);
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);
        status.setPadding(16, 20, 16, 20);
        root.addView(status);
        preview = new SurfaceView(this);
        preview.getHolder().addCallback(this);
        preview.setOnClickListener(view -> focus());
        root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
        torch = new Button(this);
        torch.setText("Feneri aç");
        torch.setEnabled(false);
        torch.setOnClickListener(view -> toggleTorch());
        root.addView(torch);
        Button cancel = new Button(this);
        cancel.setText("Vazgeç");
        cancel.setOnClickListener(view -> finish());
        root.addView(cancel);
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
        } else open();
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request != 1) return;
        if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) open();
        else status.setText("QR okutmak için kamera izni gerekiyor. Ayarlardan izin verip yeniden aç.");
    }

    private void open() {
        if (!resumed || !surface || camera != null || completed || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        try {
            Camera.CameraInfo info = new Camera.CameraInfo();
            int index = -1;
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    index = i;
                    break;
                }
            }
            if (index < 0) throw new IllegalStateException();
            camera = Camera.open(index);
            Camera.Parameters p = camera.getParameters();
            List<Camera.Size> sizes = p.getSupportedPreviewSizes();
            Camera.Size selected = p.getPreviewSize();
            int distance = Integer.MAX_VALUE;
            for (Camera.Size size : sizes) {
                int delta = Math.abs(size.width * size.height - 1280 * 720);
                if (delta < distance) {
                    distance = delta;
                    selected = size;
                }
            }
            p.setPreviewSize(selected.width, selected.height);
            p.setPreviewFormat(android.graphics.ImageFormat.NV21);
            if (p.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setParameters(p);
            int rotation = getWindowManager().getDefaultDisplay().getRotation() * 90;
            camera.setDisplayOrientation((info.orientation - rotation + 360) % 360);
            camera.setPreviewDisplay(preview.getHolder());
            List<String> flashes = p.getSupportedFlashModes();
            torch.setEnabled(flashes != null && flashes.contains(Camera.Parameters.FLASH_MODE_TORCH));
            camera.setPreviewCallback(this::frame);
            camera.startPreview();
        } catch (Exception e) {
            releaseCamera();
            status.setText("Kamera açılamadı. Kamerayı kullanan diğer uygulamayı kapatıp tekrar dene.");
        }
    }

    private void focus() {
        final Camera current = camera;
        if (current == null) return;
        try {
            Camera.Parameters p = current.getParameters();
            if (!p.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) return;
            current.cancelAutoFocus();
            p.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            current.setParameters(p);
            current.autoFocus((success, focused) -> {
                if (camera != focused) return;
                try {
                    Camera.Parameters restore = focused.getParameters();
                    if (restore.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                        restore.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                        focused.setParameters(restore);
                    }
                } catch (RuntimeException ignored) { }
            });
        } catch (RuntimeException ignored) { }
    }

    private void toggleTorch() {
        if (camera == null) return;
        try {
            Camera.Parameters p = camera.getParameters();
            p.setFlashMode(light ? Camera.Parameters.FLASH_MODE_OFF : Camera.Parameters.FLASH_MODE_TORCH);
            camera.setParameters(p);
            light = !light;
            torch.setText(light ? "Feneri kapat" : "Feneri aç");
        } catch (RuntimeException e) {
            status.setText("Bu kamerada fener açılamadı.");
        }
    }

    private void frame(byte[] bytes, Camera source) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (completed || source != camera || now - lastFrame < 200 || !decoding.compareAndSet(false, true)) return;
        lastFrame = now;
        final int scanGeneration = generation;
        Camera.Size size = source.getParameters().getPreviewSize();
        byte[] frame = bytes.clone();
        worker.execute(() -> {
            try {
                PlanarYUVLuminanceSource luminance = new PlanarYUVLuminanceSource(frame, size.width, size.height, 0, 0, size.width, size.height, false);
                String text = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(luminance))).getText();
                if (!text.startsWith("ZD1.") && !text.contains("ZD_WEB_PAIR")) return;
                runOnUiThread(() -> {
                    if (!resumed || completed || generation != scanGeneration) return;
                    completed = true;
                    Intent result = new Intent();
                    result.putExtra("qr", text);
                    result.putExtra("name", getIntent().getStringExtra("name"));
                    result.putExtra("owner", getIntent().getStringExtra("owner"));
                    result.putExtra("full", getIntent().getBooleanExtra("full", false));
                    setResult(RESULT_OK, result);
                    finish();
                });
            } catch (Exception ignored) {
                // Okunamayan önizleme kareleri normaldir; log ve dosya yok.
            } finally {
                java.util.Arrays.fill(frame, (byte) 0);
                decoding.set(false);
            }
        });
    }

    private void releaseCamera() {
        generation++;
        Camera old = camera;
        camera = null;
        if (old != null) {
            old.setPreviewCallback(null);
            old.release();
        }
        light = false;
        torch.setEnabled(false);
        torch.setText("Feneri aç");
    }

    @Override protected void onPause() {
        resumed = false;
        releaseCamera();
        super.onPause();
    }

    @Override protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        surface = true;
        open();
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        surface = false;
        releaseCamera();
    }
}
