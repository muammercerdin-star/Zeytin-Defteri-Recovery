package com.zeytindefteri.app;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Arrays;
import org.json.JSONObject;

/** Android'siz, telefon kimliğine sabitlenmiş kalıcı cihaz el sıkışması. */
public final class TezgahWebDeviceHandshake implements AutoCloseable {
    private static JSONObject json(Object... fields) throws Exception {
        JSONObject out = new JSONObject();
        for (int i = 0; i < fields.length; i += 2) out.put((String) fields[i], fields[i + 1]);
        return out;
    }

    private final String room;
    private final String id;
    private final String browserKey;
    private final PrivateKey phoneIdentity;
    private String nonce;
    private TezgahWebCrypto.Keys keys;
    private boolean accepted;

    public TezgahWebDeviceHandshake(String room, String id, String browserKey, PrivateKey phoneIdentity) {
        TezgahWebProtocol.token(room, 16);
        TezgahWebProtocol.token(id, 16);
        TezgahWebCrypto.publicKey(browserKey);
        this.room = room;
        this.id = id;
        this.browserKey = browserKey;
        this.phoneIdentity = phoneIdentity;
    }

    public JSONObject ready() throws Exception {
        close();
        nonce = TezgahWebCrypto.base64(TezgahWebCrypto.random(32));
        byte[] transcript = TezgahWebDeviceCrypto.transcript("zd-web/device-ready/v1", room, id, nonce);
        return json("v", 1, "type", "device_ready", "nonce", nonce,
                "signature", TezgahWebDeviceCrypto.sign(phoneIdentity, transcript));
    }

    public JSONObject hello(JSONObject hello) throws Exception {
        if (nonce == null || keys != null) throw new IllegalArgumentException("GECERSIZ_CIHAZ_AKISI");
        String nb = hello.getString("nb");
        String browser = hello.getString("browserPublicKey");
        TezgahWebProtocol.token(nb, 32);
        TezgahWebCrypto.publicKey(browser);
        byte[] first = TezgahWebDeviceCrypto.transcript("zd-web/device-hello/v1", room, id, nonce, nb, browser);
        TezgahWebDeviceCrypto.verify(browserKey, first, hello.getString("signature"));
        KeyPair phone = TezgahWebCrypto.keyPair();
        String np = TezgahWebCrypto.base64(TezgahWebCrypto.random(32));
        String publicKey = TezgahWebCrypto.base64(phone.getPublic().getEncoded());
        byte[] transcript = TezgahWebDeviceCrypto.transcript("zd-web/device-session/v1", room, id, nonce, nb, browser, np, publicKey);
        byte[] shared = TezgahWebCrypto.ecdh(phone.getPrivate(), browser);
        try {
            keys = TezgahWebCrypto.initial(shared, TezgahWebCrypto.sha256(transcript), transcript);
            return json("v", 1, "type", "device_answer", "np", np, "phonePublicKey", publicKey,
                    "signature", TezgahWebDeviceCrypto.sign(phoneIdentity, transcript), "proof", TezgahWebCrypto.base64(
                            TezgahWebCrypto.proof(keys.confirmation(), "device-phone", keys.transcriptHash)));
        } finally {
            Arrays.fill(shared, (byte) 0);
        }
    }

    public void finished(JSONObject finished) {
        if (keys == null || accepted) throw new IllegalArgumentException("GECERSIZ_CIHAZ_AKISI");
        TezgahWebCrypto.verify(keys.confirmation(), "device-browser", keys.transcriptHash,
                TezgahWebCrypto.unbase64(finished.optString("proof")));
        accepted = true;
    }

    public TezgahWebCrypto.Keys takeKeys() {
        if (!accepted || keys == null) throw new IllegalArgumentException("CIHAZ_DOGRULANMADI");
        TezgahWebCrypto.Keys result = keys;
        keys = null;
        nonce = null;
        return result;
    }

    @Override public void close() {
        nonce = null;
        accepted = false;
        if (keys != null) keys.close();
        keys = null;
    }
}
