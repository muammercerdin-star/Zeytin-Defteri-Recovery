package com.zeytindefteri.app;

import org.json.JSONObject;

/**
 * Yağhane sırasının bir çiftçiye ait tek satırının durumu (sunucudan /api/sira/&lt;token&gt;).
 *
 * Saf model: ağ ve Android yok, host'ta test edilir. Kişisel veri taşımaz — yalnız bu çiftçinin kendi
 * sırası gelir (isim ve başka müşteri bilgisi sunucudan hiç gönderilmez). Eşik (haber) mantığı, web
 * tarafındaki tetikleme ile birebir aynıdır: aynı anda iki yerde farklı davranmasın.
 */
public final class SiraDurumu {

    public static final int ADIM_TESLIM = 0;
    public static final int ADIM_SIRADA = 1;
    public static final int ADIM_ISLENIYOR = 2;
    public static final int ADIM_BITTI = 3;
    public static final int ADIM_IPTAL = -1;

    public static final int HABER_YOK = 0;
    public static final int HABER_YAKLASTI = 1;
    public static final int HABER_SIRA_SENDE = 2;

    public final String fabrika;
    public final String kod;
    public final int siraNo;
    public final String durum;
    public final int onunde;
    public final int kasa;
    public final double kg;
    public final String gelis;
    public final String tahmini;
    public final String biter;
    public final Double yagKg;
    public final String guncel;

    private SiraDurumu(String fabrika, String kod, int siraNo, String durum, int onunde, int kasa, double kg,
                       String gelis, String tahmini, String biter, Double yagKg, String guncel) {
        this.fabrika = fabrika;
        this.kod = kod;
        this.siraNo = siraNo;
        this.durum = durum;
        this.onunde = onunde;
        this.kasa = kasa;
        this.kg = kg;
        this.gelis = gelis;
        this.tahmini = tahmini;
        this.biter = biter;
        this.yagKg = yagKg;
        this.guncel = guncel;
    }

    public static SiraDurumu coz(String json) {
        try {
            return coz(new JSONObject(json));
        } catch (Exception e) {
            // Android'de JSONObject(String) checked JSONException atar; bozuk veri gelirse çağıran (SiraBulut) yutar.
            throw new IllegalArgumentException("Geçersiz sıra verisi");
        }
    }

    public static SiraDurumu coz(JSONObject o) {
        String durum = o.optString("durum", "sirada");
        Double yag = null;
        if (o.has("yag_kg") && !o.isNull("yag_kg")) {
            yag = o.optDouble("yag_kg", 0);
        }
        return new SiraDurumu(
                o.optString("fabrika", ""),
                o.optString("kod", ""),
                o.optInt("sira_no", 0),
                durum,
                o.optInt("onunde", 0),
                o.optInt("kasa", 0),
                o.optDouble("kg", 0),
                o.optString("gelis", ""),
                metin(o, "tahmini"),
                metin(o, "biter"),
                yag,
                o.optString("guncel", ""));
    }

    private static String metin(JSONObject o, String alan) {
        if (!o.has(alan) || o.isNull(alan)) {
            return "";
        }
        return o.optString(alan, "");
    }

    /** Dört adımlı çubukta şu anki adım: sirada=1, isleniyor=2, bitti=3, iptal=-1. */
    public int adim() {
        if (durum.equals("isleniyor")) {
            return ADIM_ISLENIYOR;
        }
        if (durum.equals("bitti")) {
            return ADIM_BITTI;
        }
        if (durum.equals("iptal")) {
            return ADIM_IPTAL;
        }
        return ADIM_SIRADA;
    }

    /** Sıra hâlâ sürüyor mu (kart ve yoklama bununla açık kalır). */
    public boolean aktif() {
        return durum.equals("sirada") || durum.equals("isleniyor");
    }

    /** Bitti ya da iptal (yoklama durur, kart kapanabilir). */
    public boolean tamam() {
        return durum.equals("bitti") || durum.equals("iptal");
    }

    /** Ekranda gösterilecek tahmin değeri: sırada tahmini, işleniyorsa bitiş; yoksa boş. */
    public String gosterilenTahmin() {
        if (durum.equals("sirada")) {
            return tahmini;
        }
        if (durum.equals("isleniyor")) {
            return biter;
        }
        return "";
    }

    /** "Önünde N kişi" / önünde kimse yoksa "Sıradaki sensin.". */
    public String onundeMetni() {
        if (!durum.equals("sirada")) {
            return "";
        }
        if (onunde == 0) {
            return "Sıradaki sensin.";
        }
        return "Önünde " + onunde + " kişi";
    }

    /**
     * Bu an için haber eşiği (web tetiklemesiyle aynı): 2 = sıra sende (başladı ya da önünde 0),
     * 1 = yaklaştı (sırada ve önünde &le; esik), 0 = yok. esik örn. 3.
     */
    public int haberEsigi(int esik) {
        if (durum.equals("isleniyor")) {
            return HABER_SIRA_SENDE;
        }
        if (!durum.equals("sirada")) {
            return HABER_YOK;
        }
        if (onunde == 0) {
            return HABER_SIRA_SENDE;
        }
        if (onunde <= esik) {
            return HABER_YAKLASTI;
        }
        return HABER_YOK;
    }

    /** Randıman (% — 100 kg zeytinden çıkan yağ): bitti ve yağ yazılmışsa; yoksa null. Deftere yazmak için. */
    public Double randiman() {
        if (yagKg == null || kg <= 0) {
            return null;
        }
        return yagKg / kg * 100.0;
    }
}
