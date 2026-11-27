package com.zeytindefteri.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SiraDefteri.Depo'nun Android uygulaması: sıra token'ları SharedPreferences'ta, YALNIZ telefonda durur.
 * Google yedeğine ve sunucuya gitmez (sunucu çiftçiyi tanımaz). Saf mantık SiraDefteri'de, host'ta test edilir.
 */
final class SiraDepo implements SiraDefteri.Depo {

    private static final String PREFS = "zd_sira";
    private final SharedPreferences prefs;

    SiraDepo(Context c) {
        this.prefs = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override
    public String oku(String anahtar) {
        return prefs.getString(anahtar, null);
    }

    @Override
    public void yaz(String anahtar, String deger) {
        prefs.edit().putString(anahtar, deger).apply();
    }
}
