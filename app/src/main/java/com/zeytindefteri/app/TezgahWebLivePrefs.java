package com.zeytindefteri.app;

import android.content.Context;
import android.content.SharedPreferences;

/** {@link TezgahWebLive} için telefondaki Tezgâh tercihlerini okuyan küçük Android köprüsü (anahtarlar TezgahActivity ile aynı). */
final class TezgahWebLivePrefs {
    private TezgahWebLivePrefs() { }

    static TezgahWebLive.Source source(Context context, String owner) {
        final SharedPreferences p = context.getApplicationContext().getSharedPreferences("zd_tezgah", Context.MODE_PRIVATE);
        return () -> new TezgahWebLive.State(
                p.getString("buy_prices_" + owner, ""),
                p.getString("price_day_" + owner, ""),
                p.getString("draft_1_" + owner, ""),
                p.getString("seller_" + owner, ""),
                p.getFloat("tare", (float) TezgahModel.DEFAULT_TARE));
    }
}
