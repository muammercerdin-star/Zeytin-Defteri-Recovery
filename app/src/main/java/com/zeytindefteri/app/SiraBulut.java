package com.zeytindefteri.app;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

/**
 * Yağhane sırası sunucusundan (sira.zeytindefteri.tr) bir çiftçinin kendi sırasının durumunu çeker.
 *
 * YALNIZCA OKUMA ve yalnız token gönderilir — token kişisel veri değildir (çiftçi fabrikadan QR ile aldı),
 * sunucu çiftçinin kim olduğunu bilmez. Şifreli röle istisnası: Yağhane sırası Cloudflare'i kullanabilir,
 * şartı sunucunun kişisel veri okuyamamasıdır; burada ad/telefon hiç gitmez. Gövde SiraDurumu ile çözülür.
 *
 * Saf JDK (java.net) + org.json: internet bağlantısı denetimi (Context/ConnectivityManager) çağıran Android
 * katmanındadır, bu sınıfta değildir — böylece saf mantık host'ta derlenebilir.
 */
final class SiraBulut {

    static final String TABAN = "https://sira.zeytindefteri.tr";
    private static final int CONNECT_MS = 10000;
    private static final int READ_MS = 15000;
    private static final int MAX = 8192;

    private SiraBulut() {
    }

    static boolean gecerliToken(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{22}");
    }

    /**
     * Elle giriş: fabrika kısa adı + kod + ek (telefon son 2 hanesi ya da kasa sayısı) → sıra token'ı.
     * QR okutamayan çiftçi için web'deki /bul ile aynı. Dönen: token ya da null (bulunamadı/ulaşılamadı).
     */
    static String bul(String fabrika, String kod, String ek) {
        HttpURLConnection conn = null;
        try {
            JSONObject govde = new JSONObject();
            govde.put("fabrika", fabrika);
            govde.put("kod", kod);
            govde.put("ek", ek);
            conn = (HttpURLConnection) new URL(TABAN + "/api/sira/bul").openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_MS);
            conn.setReadTimeout(READ_MS);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(govde.toString().getBytes("UTF-8"));
            }
            if (conn.getResponseCode() != 200) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                char[] buf = new char[512];
                int oku;
                while ((oku = r.read(buf)) != -1 && sb.length() < MAX) {
                    sb.append(buf, 0, oku);
                }
            }
            String token = new JSONObject(sb.toString()).optString("token", "");
            return gecerliToken(token) ? token : null;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** token'ın sıra durumu; null = ulaşılamadı ya da sıra yok / süresi dolmuş (HTTP 200 dışı). */
    static SiraDurumu durum(String token) {
        if (!gecerliToken(token)) {
            return null;
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(TABAN + "/api/sira/" + token).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_MS);
            conn.setReadTimeout(READ_MS);
            conn.setRequestProperty("Accept", "application/json");
            int kod = conn.getResponseCode();
            if (kod != 200) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                char[] buf = new char[1024];
                int oku;
                while ((oku = r.read(buf)) != -1 && sb.length() < MAX) {
                    sb.append(buf, 0, oku);
                }
            }
            return SiraDurumu.coz(sb.toString());
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
