package com.zeytindefteri.app;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.KeyPair;
import org.json.JSONObject;

public final class DeviceBridge {
    private static JSONObject json(Object... fields) throws Exception {
        JSONObject out = new JSONObject();
        for (int i = 0; i < fields.length; i += 2) out.put((String) fields[i], fields[i + 1]);
        return out;
    }

    public static void main(String[] args) throws Exception {
        KeyPair phone = TezgahWebCrypto.keyPair();
        TezgahWebDeviceHandshake handshake = null;
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        for (String line; (line = input.readLine()) != null;) {
            JSONObject command = new JSONObject(line);
            JSONObject result;
            try {
                switch (command.getString("cmd")) {
                    case "init":
                        handshake = new TezgahWebDeviceHandshake(command.getString("room"), command.getString("id"), command.getString("browserKey"), phone.getPrivate());
                        result = json("phoneKey", TezgahWebCrypto.base64(phone.getPublic().getEncoded()));
                        break;
                    case "ready":
                        result = handshake.ready();
                        break;
                    case "hello":
                        result = handshake.hello(command.getJSONObject("frame"));
                        break;
                    case "finished":
                        handshake.finished(command.getJSONObject("frame"));
                        TezgahWebCrypto.Keys keys = handshake.takeKeys();
                        String session = TezgahWebCrypto.base64(java.util.Arrays.copyOfRange(keys.transcriptHash, 0, 16));
                        String epoch = TezgahWebCrypto.base64(java.util.Arrays.copyOfRange(keys.transcriptHash, 16, 32));
                        TezgahWebCrypto.Channel channel = new TezgahWebCrypto.Channel(keys, session, epoch, "p2w");
                        result = new JSONObject(channel.seal(TezgahWebProtocol.utf8("cihaz doğrulandı")).json());
                        channel.close();
                        keys.close();
                        break;
                    case "expired":
                        result = json("expired", TezgahWebDeviceCrypto.expired(command.getLong("last"), command.getLong("now")));
                        break;
                    default:
                        throw new IllegalArgumentException("GECERSIZ_TEST");
                }
            } catch (Exception e) {
                result = json("error", e.getMessage());
            }
            System.out.println(result);
            System.out.flush();
        }
    }
}
