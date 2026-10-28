package com.zeytindefteri.app;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Bulut yedeğinin paketlenmesi (saf, Android'siz): yedek metni gzip ile sıkıştırılır, Firestore belge sınırına
 * (1 MiB) sığsın diye parçalara bölünür; geri gelirken birleştirilip açılır. "Değişti mi?" için içerik özeti
 * (yedek tarihi satırı hariç) kullanılır: aynı kayıtlar ikinci kez gönderilmez.
 */
public final class TezgahCloudPack {
    /** Bir parçanın en büyük boyu (bayt). Belge sınırı 1 MiB; alan adları ve meta için pay bırakılır. */
    public static final int PART_BYTES = 700 * 1024;
    /**
     * Sıkıştırılmış yedeğin üst sınırı. Bütün parçalar tek toplu yazmada gider; Firestore toplu yazma isteği
     * 10 MiB ile sınırlı. Aşarsa bulut yedeği yapılmaz, dosya yedeği önerilir (yıllarca kayıt demektir).
     */
    public static final int MAX_BYTES = 9 * 1024 * 1024;

    private TezgahCloudPack() {}

    public static byte[] gzip(String text) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            GZIPOutputStream z = new GZIPOutputStream(out);
            z.write(text.getBytes(StandardCharsets.UTF_8));
            z.close();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Sıkıştırılamadı", e);
        }
    }

    public static String gunzip(byte[] data) {
        try {
            GZIPInputStream z = new GZIPInputStream(new ByteArrayInputStream(data));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = z.read(buf)) > 0) out.write(buf, 0, n);
            z.close();
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Bulut yedeği açılamadı (bozuk).", e);
        }
    }

    public static List<byte[]> split(byte[] data, int partBytes) {
        List<byte[]> out = new ArrayList<>();
        int size = Math.max(1, partBytes);
        for (int i = 0; i < data.length; i += size) {
            int n = Math.min(size, data.length - i);
            byte[] p = new byte[n];
            System.arraycopy(data, i, p, 0, n);
            out.add(p);
        }
        if (out.isEmpty()) out.add(new byte[0]);
        return out;
    }

    public static byte[] join(List<byte[]> parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) if (p != null) out.write(p, 0, p.length);
        return out.toByteArray();
    }

    /** İçerik özeti: "@created" satırı hariç (yalnız tarih değiştiyse yedek değişmemiş sayılır). */
    public static String hash(String backupText) {
        StringBuilder b = new StringBuilder(backupText.length());
        for (String line : backupText.split("\n", -1)) {
            if (line.startsWith("@created ")) continue;
            b.append(line).append('\n');
        }
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(b.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte x : d) hex.append(String.format("%02x", x & 0xff));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return String.valueOf(b.toString().hashCode());
        }
    }

    /** Yedekte en az bir kayıt var mı (boş telefonun boş yedeği bulutu ezmesin)? */
    public static boolean hasRows(TezgahBackup.Data d) {
        if (d == null) return false;
        for (String t : TezgahBackup.TABLES) if (d.rowCount(t) > 0) return true;
        return false;
    }
}
