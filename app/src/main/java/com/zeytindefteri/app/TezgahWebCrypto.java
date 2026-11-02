package com.zeytindefteri.app;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Android 24 / Java 8: P-256, RFC5869-HMAC, AES-GCM. Sırlar yalnız çağıran süreçte. */
public final class TezgahWebCrypto {
    private static final String B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    private TezgahWebCrypto() { }
    private static void need(boolean b, String c) { TezgahWebProtocol.require(b, c); }
    public static byte[] random(int bytes) {
        need(bytes > 0 && bytes <= 1024, "GECERSIZ_BOYUT");
        byte[] out = new byte[bytes]; new SecureRandom().nextBytes(out); return out;
    }
    public static String base64(byte[] b) {
        StringBuilder s = new StringBuilder((b.length * 4 + 2) / 3);
        for (int i = 0; i < b.length; i += 3) {
            int a = b[i] & 255, c = i + 1 < b.length ? b[i+1] & 255 : 0, d = i+2 < b.length ? b[i+2] & 255 : 0;
            s.append(B64.charAt(a >>> 2)).append(B64.charAt(((a & 3) << 4) | (c >>> 4)));
            if (i+1 < b.length) s.append(B64.charAt(((c & 15) << 2) | (d >>> 6)));
            if (i+2 < b.length) s.append(B64.charAt(d & 63));
        }
        return s.toString();
    }
    public static byte[] unbase64(String s) {
        need(s != null && s.length() <= TezgahWebProtocol.MAX_FRAME_BYTES && s.length() % 4 != 1, "GECERSIZ_BASE64");
        byte[] out = new byte[s.length() * 6 / 8]; int n = 0, bits = 0, acc = 0;
        for (int i = 0; i < s.length(); i++) {
            int v = B64.indexOf(s.charAt(i)); need(v >= 0, "GECERSIZ_BASE64");
            acc = (acc << 6) | v; bits += 6;
            if (bits >= 8) { bits -= 8; out[n++] = (byte)(acc >>> bits); }
        }
        need(base64(out).equals(s), "GECERSIZ_BASE64"); return out;
    }
    public static byte[] sha256(byte[] data) {
        try { return MessageDigest.getInstance("SHA-256").digest(data); }
        catch (GeneralSecurityException e) { throw new IllegalStateException("KRIPTO_YOK", e); }
    }
    public static byte[] concat(byte[]... arrays) {
        int n = 0; for (byte[] b : arrays) n = Math.addExact(n, b.length);
        byte[] out = new byte[n]; int at = 0;
        for (byte[] b : arrays) { System.arraycopy(b, 0, out, at, b.length); at += b.length; } return out;
    }
    public static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac m = Mac.getInstance("HmacSHA256"); m.init(new SecretKeySpec(key, "HmacSHA256")); return m.doFinal(data);
        } catch (GeneralSecurityException e) { throw new IllegalStateException("KRIPTO_YOK", e); }
    }
    public static byte[] extract(byte[] salt, byte[] ikm) { return hmac(salt == null || salt.length == 0 ? new byte[32] : salt, ikm); }
    public static byte[] expand(byte[] prk, byte[] info, int length) {
        need(length >= 0 && length <= 255 * 32, "HKDF_UZUNLUK");
        byte[] result = new byte[length], t = new byte[0]; int at = 0;
        for (int i = 1; at < length; i++) {
            t = hmac(prk, concat(t, info, new byte[]{(byte)i}));
            int n = Math.min(32, length - at); System.arraycopy(t, 0, result, at, n); at += n;
        }
        Arrays.fill(t, (byte)0); return result;
    }
    public static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int n) {
        byte[] prk = extract(salt, ikm); try { return expand(prk, info, n); } finally { Arrays.fill(prk, (byte)0); }
    }
    public static KeyPair keyPair() {
        try { KeyPairGenerator k = KeyPairGenerator.getInstance("EC"); k.initialize(new ECGenParameterSpec("secp256r1")); return k.generateKeyPair(); }
        catch (GeneralSecurityException e) { throw new IllegalStateException("KRIPTO_YOK", e); }
    }
    public static PublicKey publicKey(String spki) {
        try {
            byte[] der = unbase64(spki); need(der.length == 91, "GECERSIZ_P256");
            PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der));
            need(key instanceof ECPublicKey, "GECERSIZ_P256"); ECPublicKey ec = (ECPublicKey)key;
            AlgorithmParameters a = AlgorithmParameters.getInstance("EC"); a.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec want = a.getParameterSpec(ECParameterSpec.class), got = ec.getParams();
            need(want.getCurve().equals(got.getCurve()) && want.getOrder().equals(got.getOrder())
                    && want.getGenerator().equals(got.getGenerator()) && got.getCofactor() == 1, "GECERSIZ_P256");
            BigInteger x = ec.getW().getAffineX(), y = ec.getW().getAffineY();
            BigInteger prime = ((ECFieldFp)want.getCurve().getField()).getP();
            need(x != null && y != null && x.signum() >= 0 && y.signum() >= 0 && x.compareTo(prime)<0 && y.compareTo(prime)<0, "GECERSIZ_P256");
            need(y.multiply(y).mod(prime).equals(x.pow(3).add(want.getCurve().getA().multiply(x)).add(want.getCurve().getB()).mod(prime)), "GECERSIZ_P256");
            need(Arrays.equals(der, key.getEncoded()), "GECERSIZ_P256"); return key;
        } catch (GeneralSecurityException e) { throw new IllegalArgumentException("GECERSIZ_P256"); }
    }
    public static byte[] ecdh(PrivateKey own, String peerSpki) {
        try { KeyAgreement k = KeyAgreement.getInstance("ECDH"); k.init(own); k.doPhase(publicKey(peerSpki), true); return k.generateSecret(); }
        catch (GeneralSecurityException e) { throw new IllegalArgumentException("GECERSIZ_P256"); }
    }
    /** QR B anahtarı constructor'da sabitlenir; ağdan B değiştirme API'si yoktur. */
    public static final class Pairing implements AutoCloseable {
        private final String room, browserSpki, nb; private final long expiresAt; private final byte[] secret; private boolean closed;
        public Pairing(String room,long expiresAt,byte[] secret,String browserSpki,String nb) {
            TezgahWebProtocol.token(room,16);TezgahWebProtocol.token(nb,32);publicKey(browserSpki);
            need(secret.length==32 && expiresAt>0,"GECERSIZ_QR");
            this.room=room;this.expiresAt=expiresAt;this.secret=secret.clone();this.browserSpki=browserSpki;this.nb=nb;
        }
        private void live(long now) { need(!closed && now>=0 && now<expiresAt,"QR_SURESI_DOLDU"); }
        public synchronized Keys phoneKeys(KeyPair phone,String np,long now) {
            live(now);byte[] t=TezgahWebProtocol.transcript(room,expiresAt,nb,np,browserSpki,base64(phone.getPublic().getEncoded()));
            byte[] z=ecdh(phone.getPrivate(),browserSpki);try{return initial(z,secret,t);}finally{Arrays.fill(z,(byte)0);}
        }
        public synchronized Keys browserKeys(KeyPair browser,String phoneSpki,String np,long now) {
            live(now);need(base64(browser.getPublic().getEncoded()).equals(browserSpki),"SABIT_B_UYUSMAZ");
            byte[] t=TezgahWebProtocol.transcript(room,expiresAt,nb,np,browserSpki,phoneSpki),z=ecdh(browser.getPrivate(),phoneSpki);
            try{return initial(z,secret,t);}finally{Arrays.fill(z,(byte)0);}
        }
        public synchronized void close() { closed=true;Arrays.fill(secret,(byte)0); }
    }
    public static final class Keys implements AutoCloseable {
        private final byte[] p2w, w2p, confirm, resume; private boolean closed;
        public final byte[] transcriptHash;
        Keys(byte[] prk, byte[] hash) {
            transcriptHash = hash.clone(); p2w = key(prk, hash, "phone-to-web"); w2p = key(prk, hash, "web-to-phone");
            confirm = key(prk, hash, "confirm"); resume = key(prk, hash, "resume");
        }
        public byte[] direction(String dir) { need(!closed, "OTURUM_IPTAL"); TezgahWebProtocol.direction(dir); return ("p2w".equals(dir) ? p2w : w2p).clone(); }
        public byte[] confirmation() { need(!closed, "OTURUM_IPTAL"); return confirm.clone(); }
        public byte[] resumeRoot() { need(!closed, "OTURUM_IPTAL"); return resume.clone(); }
        public void close() { closed=true; for (byte[] b : new byte[][]{p2w,w2p,confirm,resume}) Arrays.fill(b, (byte)0); }
    }
    private static byte[] key(byte[] prk, byte[] hash, String label) {
        return expand(prk, concat(TezgahWebProtocol.utf8("zd-web/v1/" + label), hash), 32);
    }
    public static Keys initial(byte[] shared, byte[] s, byte[] transcript) {
        need(shared.length == 32 && s.length == 32, "GECERSIZ_ANAHTAR");
        byte[] prk = extract(s, shared); try { return new Keys(prk, sha256(transcript)); } finally { Arrays.fill(prk,(byte)0); }
    }
    public static Keys resume(byte[] root, byte[] transcript) {
        need(root.length == 32, "GECERSIZ_ANAHTAR"); byte[] h = sha256(transcript), prk = extract(h, root);
        try { return new Keys(prk, h); } finally { Arrays.fill(prk,(byte)0); }
    }
    public static byte[] proof(byte[] key, String role, byte[] transcriptHash) {
        need(role.equals("phone-hello") || role.equals("web-finished") || role.equals("resume-phone") || role.equals("resume-browser"), "GECERSIZ_KANIT");
        return hmac(key, concat(TezgahWebProtocol.utf8(role), transcriptHash));
    }
    public static void verify(byte[] key, String role, byte[] hash, byte[] proof) {
        need(proof.length == 32 && MessageDigest.isEqual(proof(key, role, hash), proof), "KANIT_RED");
    }
    private static byte[] iv(long seq) { need(seq > 0, "GECERSIZ_SAYAC"); return ByteBuffer.allocate(12).putInt(0).putLong(seq).array(); }
    public static byte[] encrypt(byte[] key, long seq, byte[] aad, byte[] plain) { return aes(true,key,seq,aad,plain); }
    public static byte[] decrypt(byte[] key, long seq, byte[] aad, byte[] ct) { return aes(false,key,seq,aad,ct); }
    private static byte[] aes(boolean encrypt, byte[] key, long seq, byte[] aad, byte[] input) {
        need(key.length == 32, "GECERSIZ_ANAHTAR");
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, new SecretKeySpec(key,"AES"), new GCMParameterSpec(128,iv(seq)));
            c.updateAAD(aad); return c.doFinal(input);
        } catch (GeneralSecurityException e) { throw new IllegalArgumentException("SIFRE_DOGRULAMA_RED"); }
    }
    /** Sayaçlar tek kanala aittir. Yeni epoch yeni Channel gerektirir; başarısız decrypt sayacı ilerletmez. */
    public static final class Channel implements AutoCloseable {
        private final String session, epoch, sendDir, recvDir; private final byte[] tx, rx;
        private long sent, received; private boolean closed;
        public Channel(Keys keys, String session, String epoch, String sendDir) {
            TezgahWebProtocol.token(session,16); TezgahWebProtocol.token(epoch,16); TezgahWebProtocol.direction(sendDir);
            this.session=session; this.epoch=epoch; this.sendDir=sendDir; recvDir=sendDir.equals("p2w")?"w2p":"p2w";
            tx=keys.direction(sendDir); rx=keys.direction(recvDir);
        }
        public synchronized TezgahWebProtocol.Frame seal(byte[] plain) {
            need(!closed && sent < Long.MAX_VALUE, "OTURUM_IPTAL"); need(plain.length <= TezgahWebProtocol.MAX_PLAIN_BYTES,"BOYUT_SINIRI");
            String seq=Long.toString(++sent);
            return new TezgahWebProtocol.Frame(session,epoch,sendDir,seq,base64(encrypt(tx,sent,TezgahWebProtocol.aad(session,epoch,sendDir,seq),plain)));
        }
        public synchronized byte[] open(TezgahWebProtocol.Frame f) {
            need(!closed && session.equals(f.session) && epoch.equals(f.epoch) && recvDir.equals(f.dir), "ESKI_EPOCH_VEYA_YON");
            long n=TezgahWebProtocol.sequence(f.seq); need(n>received,"TEKRAR_SAYAC");
            byte[] p=decrypt(rx,n,TezgahWebProtocol.aad(session,epoch,recvDir,f.seq),unbase64(f.ct)); received=n; return p;
        }
        public synchronized void close() { closed=true; Arrays.fill(tx,(byte)0); Arrays.fill(rx,(byte)0); }
    }
    /** Zaman çağıranın monotonic saatidir. İki rol kanıtı doğrulanmadan yeni epoch etkinleşmez. */
    public static final class ResumeGuard {
        private final String session; private String epoch; private byte[] root; private long lostAt=-1;
        public ResumeGuard(String session, String epoch, byte[] root) {
            TezgahWebProtocol.token(session,16); TezgahWebProtocol.token(epoch,16); need(root.length==32,"GECERSIZ_ANAHTAR");
            this.session=session;this.epoch=epoch;this.root=root.clone();
        }
        public synchronized void disconnected(long now) { need(now>=0,"GECERSIZ_ZAMAN"); if(lostAt<0)lostAt=now; }
        public synchronized Keys accept(long now,String old,String next,String nb,String np,byte[] phoneProof,byte[] webProof) {
            need(root!=null && lostAt>=0 && now>=lostAt && now-lostAt<=TezgahWebProtocol.RESUME_WINDOW_MS,"RESUME_SURESI_DOLDU");
            need(epoch.equals(old),"ESKI_EPOCH"); byte[] t=TezgahWebProtocol.resumeTranscript(session,old,next,nb,np),h=sha256(t);
            verify(root,"resume-phone",h,phoneProof);verify(root,"resume-browser",h,webProof);
            Keys keys=resume(root,t);Arrays.fill(root,(byte)0);root=keys.resumeRoot();epoch=next;lostAt=-1;return keys;
        }
        public synchronized void close() { if(root!=null)Arrays.fill(root,(byte)0);root=null;lostAt=-1; }
    }
}
