package com.zeytindefteri.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Telefondaki tezgâhın anlık durumu, bilgisayara salt okunur: günün alım fiyatları ve yarım kalan (şu an tartılan) alım.
 * Android'e bağlı değildir (test edilir); SharedPreferences okuması {@link TezgahWebLivePrefs} köprüsündedir.
 * Kayıt yapmaz, tutar otoritesi değildir: kesin hesap alım kaydedilirken telefonda yapılır.
 */
public final class TezgahWebLive {
    /** Bilgisayara gönderilecek en fazla tartım satırı (çerçeve boyutu sınırlı kalsın). */
    static final int MAX_ROWS = 100;

    private TezgahWebLive() { }

    /** Telefonda saklı ham durum (SharedPreferences'tan okunur). */
    public static final class State {
        final String prices, priceDay, draft, seller;
        final double tare;

        public State(String prices, String priceDay, String draft, String seller, double tare) {
            this.prices = prices == null ? "" : prices;
            this.priceDay = priceDay == null ? "" : priceDay;
            this.draft = draft == null ? "" : draft;
            this.seller = seller == null ? "" : seller;
            this.tare = tare;
        }
    }

    /** Durumu okuyan kaynak (serviste SharedPreferences, testte sabit). */
    public interface Source { State read(); }

    /**
     * Günün fiyatları. Yarım kalan tartım varsa onun fiyatları geçerlidir (telefon ekranıyla aynı).
     * Tartım yoksa yalnız bugün onaylanmış fiyatlar gönderilir: dünkü fiyatla alım yapılmasın.
     */
    public static JSONObject prices(State s, String today) {
        TezgahModel draft = TezgahModel.decode(s.draft);
        boolean active = !draft.isEmpty();
        if (!active && (today == null || !today.equals(s.priceDay))) return TezgahWebDto.json("bugun", false);
        TezgahModel source = active ? draft : TezgahModel.decode(s.prices);
        JSONArray list = new JSONArray();
        boolean any = false;
        for (int g = 0; g < TezgahModel.GRADES.length; g++) {
            double p = Math.max(0, source.price(g));
            any |= p > 0;
            list.put(TezgahWebDto.decimal(p, 2));
        }
        if (!any) return TezgahWebDto.json("bugun", false);
        double tare = active ? draft.tare() : s.tare;
        if (!(tare >= 0) || tare > 20) tare = TezgahModel.DEFAULT_TARE;
        return TezgahWebDto.json("bugun", true, "fiyatlar", list, "dara", TezgahWebDto.decimal(tare, 1),
                "kaynak", active ? "tartim" : "gun");
    }

    /** Şu an telefonda tartılan (henüz kaydedilmemiş) alım; yoksa aktif=false. */
    public static JSONObject draft(State s) {
        TezgahModel m = TezgahModel.decode(s.draft);
        if (m.isEmpty()) return TezgahWebDto.json("aktif", false);
        JSONArray rows = new JSONArray();
        List<TezgahModel.Weighing> items = m.items();
        int from = Math.max(0, items.size() - MAX_ROWS);
        for (int i = from; i < items.size(); i++) {
            TezgahModel.Weighing w = items.get(i);
            rows.put(TezgahWebDto.json("elek", w.grade, "kasa", w.kasa, "kg", TezgahWebDto.decimal(w.kg, 1),
                    "netKg", TezgahWebDto.decimal(TezgahModel.net(w.kasa, w.kg, m.tare()), 1)));
        }
        String seller = s.seller.trim();
        if (seller.length() > 100) seller = seller.substring(0, 100);
        return TezgahWebDto.json("aktif", true, "uretici", seller, "dara", TezgahWebDto.decimal(m.tare(), 1),
                "tartimlar", rows, "satir", items.size(), "kasa", m.totalKasa(),
                "netKg", TezgahWebDto.decimal(m.totalNet(), 1), "tutarTL", TezgahWebDto.decimal(m.total(), 2),
                "eksikFiyat", !m.missingPrices().isEmpty());
    }
}
