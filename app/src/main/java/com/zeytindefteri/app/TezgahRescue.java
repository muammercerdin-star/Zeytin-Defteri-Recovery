package com.zeytindefteri.app;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Geri yüklemeden hemen önce telefondaki tezgâh kayıtlarının kurtarma kopyası (uygulamanın kendi özel
 * klasöründe, yalnız bu hesabın). Yanlış ya da eski yedek seçilirse "Geri yüklemeyi geri al" ile eski hal
 * döner. Hesap başına son 3 kopya tutulur; hesap silinince kopyalar da silinir.
 */
public final class TezgahRescue {
    private static final String DIR = "tezgah_kurtarma";
    private static final int KEEP = 3;

    private TezgahRescue() {}

    /** Kurtarma kopyası alır. Telefonda kayıt yoksa (kurtarılacak bir şey yoksa) dosya yazılmaz. */
    public static boolean save(Context c, SQLiteDatabase db, String owner) {
        try {
            String o = owner == null ? "" : owner;
            String text = TezgahBackup.export(db, o, TezgahCloudBackup.prefsMap(c, o), System.currentTimeMillis());
            if (!TezgahCloudPack.hasRows(TezgahBackup.parse(text))) return false;
            File dir = dir(c);
            if (!dir.isDirectory() && !dir.mkdirs()) return false;
            File f = new File(dir, prefix(o) + System.currentTimeMillis() + ".txt");
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }
            List<File> all = list(c, o);
            for (int i = KEEP; i < all.size(); i++) all.get(i).delete();
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Bu hesabın en yeni kurtarma kopyası; yoksa null. */
    public static File latest(Context c, String owner) {
        List<File> all = list(c, owner == null ? "" : owner);
        return all.isEmpty() ? null : all.get(0);
    }

    /** Kopyanın alındığı zaman (dosya adından). */
    public static long time(File f) {
        String n = f.getName();
        int a = n.lastIndexOf('_'), b = n.lastIndexOf('.');
        try { return Long.parseLong(n.substring(a + 1, b)); } catch (RuntimeException e) { return f.lastModified(); }
    }

    public static TezgahBackup.Data read(File f) throws IOException {
        byte[] data = new byte[(int) Math.min(f.length(), 40L * 1024 * 1024)];
        try (FileInputStream in = new FileInputStream(f)) {
            int off = 0, n;
            while (off < data.length && (n = in.read(data, off, data.length - off)) > 0) off += n;
        }
        return TezgahBackup.parse(new String(data, StandardCharsets.UTF_8));
    }

    /** Hesap silinince: bu hesabın kurtarma kopyaları da gider. */
    public static void wipe(Context c, String owner) {
        for (File f : list(c, owner == null ? "" : owner)) f.delete();
    }

    private static File dir(Context c) { return new File(c.getApplicationContext().getFilesDir(), DIR); }

    /** Dosya adında hesap kimliği açık yazılmaz; kısa bir özet kullanılır. */
    private static String prefix(String owner) {
        return "k" + Integer.toHexString(("zd:" + owner).hashCode()) + "_";
    }

    private static List<File> list(Context c, String owner) {
        List<File> out = new ArrayList<>();
        File[] files = dir(c).listFiles();
        if (files == null) return out;
        String p = prefix(owner);
        for (File f : files) if (f.getName().startsWith(p) && f.getName().endsWith(".txt")) out.add(f);
        File[] arr = out.toArray(new File[0]);
        Arrays.sort(arr, (x, y) -> Long.compare(time(y), time(x)));
        return new ArrayList<>(Arrays.asList(arr));
    }
}
