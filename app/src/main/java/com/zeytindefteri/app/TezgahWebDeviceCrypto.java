package com.zeytindefteri.app;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.security.PrivateKey;
import java.security.Signature;

/** Java/Android Keystore ECDSA DER imzası ile WebCrypto r||s arasında kesin dönüşüm. */
public final class TezgahWebDeviceCrypto {
    private TezgahWebDeviceCrypto() { }

    public static byte[] transcript(String label, String... fields) throws Exception {
        if (!label.matches("[a-z0-9/-]+") || fields.length > 16) throw new IllegalArgumentException("GECERSIZ_CIHAZ_TRANSCRIPT");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(TezgahWebProtocol.utf8(label + "\n"));
        for (String field : fields) {
            byte[] value = TezgahWebProtocol.utf8(field);
            if (value.length > 2048) throw new IllegalArgumentException("GECERSIZ_CIHAZ_TRANSCRIPT");
            out.writeInt(value.length);
            out.write(value);
        }
        return bytes.toByteArray();
    }

    public static String sign(PrivateKey key, byte[] message) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(key);
        signature.update(message);
        return TezgahWebCrypto.base64(fromDer(signature.sign()));
    }

    public static void verify(String spki, byte[] message, String encoded) throws Exception {
        TezgahWebProtocol.token(encoded, 64);
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initVerify(TezgahWebCrypto.publicKey(spki));
        signature.update(message);
        if (!signature.verify(toDer(TezgahWebCrypto.unbase64(encoded)))) throw new IllegalArgumentException("CIHAZ_KIMLIGI_RED");
    }

    public static boolean expired(long lastUsed, long now) {
        return lastUsed < 0 || now < lastUsed || now - lastUsed >= 30L * 86400000;
    }

    public static byte[] fromDer(byte[] der) {
        if (der.length < 8 || der.length > 72 || der[0] != 48 || (der[1] & 255) != der.length - 2) throw new IllegalArgumentException("GECERSIZ_IMZA");
        byte[] raw = new byte[64];
        int at = 2;
        for (int part = 0; part < 2; part++) {
            if (at + 2 > der.length || der[at++] != 2) throw new IllegalArgumentException("GECERSIZ_IMZA");
            int length = der[at++] & 255;
            if (length < 1 || length > 33 || at + length > der.length || der[at] < 0) throw new IllegalArgumentException("GECERSIZ_IMZA");
            if (length > 1 && der[at] == 0) {
                if (der[at + 1] >= 0) throw new IllegalArgumentException("GECERSIZ_IMZA");
                at++;
                length--;
            }
            if (length > 32) throw new IllegalArgumentException("GECERSIZ_IMZA");
            System.arraycopy(der, at, raw, part * 32 + 32 - length, length);
            at += length;
        }
        if (at != der.length) throw new IllegalArgumentException("GECERSIZ_IMZA");
        return raw;
    }

    public static byte[] toDer(byte[] raw) {
        if (raw.length != 64) throw new IllegalArgumentException("GECERSIZ_IMZA");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (int part = 0; part < 2; part++) {
            int start = part * 32;
            int end = start + 32;
            while (start < end - 1 && raw[start] == 0) start++;
            boolean pad = raw[start] < 0;
            body.write(2);
            body.write(end - start + (pad ? 1 : 0));
            if (pad) body.write(0);
            body.write(raw, start, end - start);
        }
        byte[] integers = body.toByteArray();
        return TezgahWebCrypto.concat(new byte[]{48, (byte) integers.length}, integers);
    }
}
