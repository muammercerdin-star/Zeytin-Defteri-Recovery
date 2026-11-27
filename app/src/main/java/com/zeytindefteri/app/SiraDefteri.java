package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Çiftçinin takip ettiği sıra token'ları — YALNIZ telefonda durur (SharedPreferences).
 *
 * Sunucuya gitmez; sıra sunucusu çiftçinin kim olduğunu bilmez (anonimlik korunur). Her token için
 * gönderilmiş haber biti tutulur (1 = yaklaştı, 2 = sıra sende) ki aynı haber iki kez gitmesin — web
 * tarafındaki esik_durumu ile aynı mantık. Depo arayüzü Android'den ayrıdır: saf mantık host'ta test edilir.
 */
public final class SiraDefteri {

    /** SharedPreferences'ı saran küçük arayüz (testte sahte bir harita ile değiştirilir). */
    public interface Depo {
        String oku(String anahtar);

        void yaz(String anahtar, String deger);
    }

    public static final String ANAHTAR = "zd_sira_tokenlar";
    private static final int EN_COK = 20;

    private final Depo depo;

    public SiraDefteri(Depo depo) {
        this.depo = depo;
    }

    private static boolean gecerli(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{22}");
    }

    private JSONArray liste() {
        String ham = depo.oku(ANAHTAR);
        if (ham == null || ham.isEmpty()) {
            return new JSONArray();
        }
        try {
            return new JSONArray(ham);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void kaydet(JSONArray a) {
        depo.yaz(ANAHTAR, a.toString());
    }

    private int dizin(JSONArray a, String token) {
        for (int i = 0; i < a.length(); i++) {
            if (token.equals(a.getJSONObject(i).optString("t"))) {
                return i;
            }
        }
        return -1;
    }

    /** Token'ı ekler (zaten varsa dokunmaz, false döner). En yeni başa alınır, en çok EN_COK tutulur. */
    public boolean ekle(String token) {
        if (!gecerli(token)) {
            return false;
        }
        JSONArray a = liste();
        if (dizin(a, token) >= 0) {
            return false;
        }
        JSONObject o = new JSONObject();
        o.put("t", token);
        o.put("e", 0);
        JSONArray yeni = new JSONArray();
        yeni.put(o);
        for (int i = 0; i < a.length() && yeni.length() < EN_COK; i++) {
            yeni.put(a.getJSONObject(i));
        }
        kaydet(yeni);
        return true;
    }

    public void sil(String token) {
        JSONArray a = liste();
        int d = dizin(a, token);
        if (d < 0) {
            return;
        }
        JSONArray yeni = new JSONArray();
        for (int i = 0; i < a.length(); i++) {
            if (i != d) {
                yeni.put(a.getJSONObject(i));
            }
        }
        kaydet(yeni);
    }

    public List<String> tokenlar() {
        JSONArray a = liste();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            out.add(a.getJSONObject(i).optString("t"));
        }
        return out;
    }

    public boolean var(String token) {
        return dizin(liste(), token) >= 0;
    }

    /** Bu token için gönderilmiş haber biti (0 = hiç, 1 = yaklaştı, 2 = sıra sende, 3 = ikisi). */
    public int esik(String token) {
        JSONArray a = liste();
        int d = dizin(a, token);
        if (d < 0) {
            return 0;
        }
        return a.getJSONObject(d).optInt("e", 0);
    }

    /** Gönderilen haber bitini işaretler (OR'lanır): aynı eşik ikinci kez haber vermez. */
    public void esikIsaretle(String token, int bit) {
        JSONArray a = liste();
        int d = dizin(a, token);
        if (d < 0) {
            return;
        }
        JSONObject o = a.getJSONObject(d);
        o.put("e", o.optInt("e", 0) | bit);
        kaydet(a);
    }
}
