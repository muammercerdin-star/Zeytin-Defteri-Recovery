package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bluetooth termal fiş yazıcısı (ESC/POS, klasik Bluetooth SPP). Yazıcı bir kez telefonun Bluetooth
 * ayarlarından eşleştirilir; burada eşleşmiş cihazlardan seçilir ve adresi hatırlanır.
 * Ayarlar: kâğıt 58/80 mm, Türkçe karakter (yazıcı PC857 destekliyorsa). İnternet gerekmez, ücretsizdir.
 */
public final class TezgahPrinter {
    static final String PREFS = "zd_printer";
    static final int REQ_PERMISSION = 71;
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private TezgahPrinter() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int width(Context c) { return prefs(c).getInt("width", TezgahPrintFormat.WIDTH_58); }

    public static boolean turkish(Context c) { return prefs(c).getBoolean("turkish", false); }

    /** Fişi yazdırır; yazıcı seçilmemişse önce seçtirir. */
    public static void print(Activity a, String receiptText) {
        if (!permitted(a)) return;
        String address = prefs(a).getString("address", "");
        if (address.isEmpty()) {
            choose(a, () -> print(a, receiptText));
            return;
        }
        send(a, address, TezgahPrintFormat.escpos(receiptText, turkish(a)));
    }

    public static boolean printQr(Context c) { return prefs(c).getBoolean("qr", true); }

    /** Alım fişi + altında QR (ayardan kapatılabilir). qrPayload: TezgahQr.encode(...). */
    public static void printReceipt(Activity a, String receiptText, String qrPayload) {
        if (!permitted(a)) return;
        String address = prefs(a).getString("address", "");
        if (address.isEmpty()) {
            choose(a, () -> printReceipt(a, receiptText, qrPayload));
            return;
        }
        byte[] qr = printQr(a) && qrPayload != null ? qrRaster(qrPayload, width(a) >= TezgahPrintFormat.WIDTH_80 ? 384 : 256) : null;
        send(a, address, TezgahPrintFormat.escpos(receiptText, turkish(a), qr, "Uretici okutsun: Elek > QR okut"));
    }

    /** QR kodunu yazıcı raster baytlarına çevirir (keskin olsun diye modüller tam sayı katıyla büyütülür). */
    static byte[] qrRaster(String payload, int maxDots) {
        try {
            java.util.Map<com.google.zxing.EncodeHintType, Object> hints = new java.util.EnumMap<>(com.google.zxing.EncodeHintType.class);
            hints.put(com.google.zxing.EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(com.google.zxing.EncodeHintType.MARGIN, 1);
            hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M);
            com.google.zxing.common.BitMatrix m = new com.google.zxing.qrcode.QRCodeWriter()
                    .encode(payload, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0, hints);
            int n = m.getWidth();
            boolean[] modules = new boolean[n * n];
            for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) modules[y * n + x] = m.get(x, y);
            int[] size = new int[1];
            boolean[] px = TezgahPrintFormat.scale(modules, n, maxDots, size);
            return TezgahPrintFormat.raster(px, size[0], size[0]);
        } catch (Exception e) {
            return null; // QR üretilemezse fiş QR'sız basılır
        }
    }

    /** Serbest metin (ekstre gibi): ilk satır başlık olarak büyük basılır; emoji atılır. */
    public static void printText(Activity a, String text) {
        String clean = text.replace("🫒 ", "").replace("🫒", "").replace("✓", "");
        print(a, clean);
    }

    /** Android 12+ "Yakındaki cihazlar" izni; yoksa ister ve false döner (izin verilince tekrar basılır). */
    static boolean permitted(Activity a) {
        if (Build.VERSION.SDK_INT < 31) return true;
        if (a.checkSelfPermission("android.permission.BLUETOOTH_CONNECT") == PackageManager.PERMISSION_GRANTED) return true;
        Toast.makeText(a, "Yazıcıya bağlanmak için \"Yakındaki cihazlar\" iznini ver, sonra tekrar Yazdır'a bas.", Toast.LENGTH_LONG).show();
        a.requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"}, REQ_PERMISSION);
        return false;
    }

    private static BluetoothAdapter adapter(Context c) {
        BluetoothManager m = (BluetoothManager) c.getSystemService(Context.BLUETOOTH_SERVICE);
        return m == null ? null : m.getAdapter();
    }

    /** Eşleşmiş Bluetooth cihazlarından yazıcı seçtirir. */
    public static void choose(Activity a, Runnable then) {
        if (!permitted(a)) return;
        BluetoothAdapter bt = adapter(a);
        if (bt == null) { Toast.makeText(a, "Bu telefonda Bluetooth yok.", Toast.LENGTH_LONG).show(); return; }
        if (!bt.isEnabled()) {
            new AlertDialog.Builder(a).setTitle("Bluetooth kapalı")
                    .setMessage("Yazıcı için Bluetooth'u aç, sonra tekrar dene.")
                    .setPositiveButton("Bluetooth ayarları", (d, w) -> openSettings(a))
                    .setNegativeButton("Vazgeç", null).show();
            return;
        }
        final List<BluetoothDevice> list = new ArrayList<>();
        try { list.addAll(bt.getBondedDevices()); } catch (SecurityException e) { return; }
        if (list.isEmpty()) {
            new AlertDialog.Builder(a).setTitle("Eşleşmiş yazıcı yok")
                    .setMessage("Yazıcıyı açıp telefonun Bluetooth ayarlarından bir kez eşleştir (şifre çoğunlukla 0000 ya da 1234). "
                            + "Sonra buradan seç.")
                    .setPositiveButton("Bluetooth ayarları", (d, w) -> openSettings(a))
                    .setNegativeButton("Vazgeç", null).show();
            return;
        }
        String[] names = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            String n;
            try { n = list.get(i).getName(); } catch (SecurityException e) { n = null; }
            names[i] = (n == null || n.isEmpty() ? "Adsız cihaz" : n) + "  ·  " + list.get(i).getAddress();
        }
        new AlertDialog.Builder(a).setTitle("Fiş yazıcısını seç")
                .setItems(names, (d, i) -> {
                    prefs(a).edit().putString("address", list.get(i).getAddress()).putString("name", names[i]).apply();
                    Toast.makeText(a, "Yazıcı seçildi.", Toast.LENGTH_SHORT).show();
                    if (then != null) then.run();
                })
                .setNegativeButton("Vazgeç", null).show();
    }

    /** Yazıcı ayarları: seç, kâğıt genişliği, Türkçe karakter, deneme fişi. */
    public static void settings(Activity a) {
        SharedPreferences p = prefs(a);
        String name = p.getString("name", "");
        final boolean w80 = width(a) >= TezgahPrintFormat.WIDTH_80;
        final boolean tr = turkish(a);
        String[] items = {
                "Yazıcı: " + (name.isEmpty() ? "seçilmedi" : name) + "  ›",
                "Kâğıt: " + (w80 ? "80 mm" : "58 mm") + " (değiştir)",
                "Türkçe karakter: " + (tr ? "açık (ş, ğ, ı basılır)" : "kapalı (s, g, i basılır)"),
                "QR kodu: " + (printQr(a) ? "fişin altına basılır" : "basılmaz"),
                "Deneme fişi yazdır"
        };
        new AlertDialog.Builder(a).setTitle("Fiş yazıcısı")
                .setItems(items, (d, i) -> {
                    if (i == 0) choose(a, () -> settings(a));
                    else if (i == 1) { p.edit().putInt("width", w80 ? TezgahPrintFormat.WIDTH_58 : TezgahPrintFormat.WIDTH_80).apply(); settings(a); }
                    else if (i == 2) { p.edit().putBoolean("turkish", !tr).apply(); settings(a); }
                    else if (i == 3) { p.edit().putBoolean("qr", !printQr(a)).apply(); settings(a); }
                    else testPrint(a);
                })
                .setNegativeButton("Kapat", null).show();
    }

    static void testPrint(Activity a) {
        TezgahModel m = new TezgahModel();
        m.add(0, 1, 27); m.setPrice(0, 130);
        m.add(5, 1, 22); m.setPrice(5, 15);
        String text = TezgahPrintFormat.receipt(m, "Deneme", "Şükrü Işık Çağlar", "deneme fişi", m.total(), width(a));
        printReceipt(a, text, TezgahQr.encode(m, "deneme", "Deneme", m.total()));
    }

    private static void openSettings(Activity a) {
        try { a.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); } catch (RuntimeException ignored) { }
    }

    /** Arka planda bağlanır, yazar, kapatır; sonucu ekranda söyler. */
    private static void send(Activity a, String address, byte[] data) {
        final Context app = a.getApplicationContext();
        final Handler ui = new Handler(Looper.getMainLooper());
        Toast.makeText(a, "Yazdırılıyor…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String error = null;
            BluetoothSocket socket = null;
            try {
                BluetoothAdapter bt = adapter(app);
                if (bt == null || !bt.isEnabled()) throw new IllegalStateException("Bluetooth kapalı.");
                BluetoothDevice dev = bt.getRemoteDevice(address);
                socket = dev.createRfcommSocketToServiceRecord(SPP);
                socket.connect();
                OutputStream out = socket.getOutputStream();
                out.write(data);
                out.flush();
                Thread.sleep(400 + data.length / 20); // yazıcı tamponu boşalsın (QR resmi uzun sürer)
            } catch (SecurityException e) {
                error = "Bluetooth izni yok.";
            } catch (IllegalStateException e) {
                error = e.getMessage();
            } catch (Exception e) {
                error = "Yazıcıya bağlanılamadı. Açık ve yakında mı? Kâğıdı var mı?";
            } finally {
                if (socket != null) try { socket.close(); } catch (Exception ignored) { }
            }
            final String msg = error == null ? "Fiş yazdırıldı ✓" : error;
            ui.post(() -> Toast.makeText(app, msg, Toast.LENGTH_LONG).show());
        }, "zd-printer").start();
    }
}
