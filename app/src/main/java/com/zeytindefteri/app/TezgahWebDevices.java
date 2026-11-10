package com.zeytindefteri.app;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.spec.ECGenParameterSpec;
import org.json.JSONArray;
import org.json.JSONObject;

/** Owner'a bağlı kayıtlı cihazlar. Özel kimlik anahtarı Android Keystore'dan çıkmaz. */
public final class TezgahWebDevices {
    private TezgahWebDevices() { }

    private static AtomicFile file(Context c, String owner) {
        String hash = TezgahWebCrypto.base64(TezgahWebCrypto.sha256(TezgahWebProtocol.utf8(owner)));
        return new AtomicFile(new File(c.getNoBackupFilesDir(), "tezgah-devices-" + hash + ".json"));
    }

    private static KeyStore store() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return store;
    }

    public static synchronized JSONArray list(Context c, String owner) {
        try {
            JSONArray rows = new JSONArray(new String(file(c, owner).readFully(), "UTF-8"));
            JSONArray live = new JSONArray();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (TezgahWebDeviceCrypto.expired(row.getLong("lastUsed"), System.currentTimeMillis())) {
                    store().deleteEntry(row.getString("alias"));
                } else live.put(row);
            }
            if (live.length() != rows.length()) write(c, owner, live);
            return live;
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static synchronized JSONObject find(Context c, String owner, String id) {
        JSONArray rows = list(c, owner);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row != null && id.equals(row.optString("id"))) return row;
        }
        return null;
    }

    public static JSONObject create(Context c, String owner, String name, boolean full, JSONObject request) throws Exception {
        TezgahWebProtocol.token(request.getString("id"), 16);
        TezgahWebCrypto.publicKey(request.getString("publicKey"));
        if (list(c, owner).length() >= 4) throw new IllegalArgumentException("En fazla dört bilgisayar kaydedilebilir. Önce bir bilgisayardan çıkış yap.");
        String room = TezgahWebCrypto.base64(TezgahWebCrypto.random(16));
        String alias = "zd-web-device-" + room;
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
        generator.initialize(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256).setUserAuthenticationRequired(false).build());
        KeyPair pair = generator.generateKeyPair();
        return TezgahWebDto.json("id", request.getString("id"), "room", room, "browserKey", request.getString("publicKey"),
                "phoneKey", TezgahWebCrypto.base64(pair.getPublic().getEncoded()), "alias", alias, "ad", name,
                "tamKullanim", full, "lastUsed", System.currentTimeMillis());
    }

    public static PrivateKey privateKey(JSONObject row) throws Exception {
        return (PrivateKey) store().getKey(row.getString("alias"), null);
    }

    public static synchronized void save(Context c, String owner, JSONObject row) throws Exception {
        JSONArray old = list(c, owner);
        JSONArray out = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject other = old.getJSONObject(i);
            if (!row.getString("id").equals(other.getString("id"))) out.put(other);
        }
        if (out.length() >= 4) throw new IllegalArgumentException("Kayıtlı bilgisayar sınırına ulaşıldı.");
        out.put(row);
        write(c, owner, out);
    }

    public static synchronized void forget(Context c, String owner, String id) throws Exception {
        JSONArray old = list(c, owner);
        JSONArray out = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject row = old.getJSONObject(i);
            if (id.equals(row.getString("id"))) store().deleteEntry(row.getString("alias"));
            else out.put(row);
        }
        write(c, owner, out);
    }

    public static void discard(JSONObject row) {
        try {
            store().deleteEntry(row.getString("alias"));
        } catch (Exception ignored) { }
    }

    private static void write(Context c, String owner, JSONArray rows) throws Exception {
        AtomicFile target = file(c, owner);
        FileOutputStream out = null;
        try {
            out = target.startWrite();
            out.write(rows.toString().getBytes("UTF-8"));
            target.finishWrite(out);
        } catch (Exception e) {
            if (out != null) target.failWrite(out);
            throw e;
        }
    }
}
