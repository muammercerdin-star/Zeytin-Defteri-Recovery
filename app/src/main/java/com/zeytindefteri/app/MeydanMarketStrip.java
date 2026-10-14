package com.zeytindefteri.app;

/**
 * Sohbet ekranının üstündeki bölge piyasası şeridinin metni. Saf Java; masaüstünde test edilir (tests/meydan_redesign).
 *
 * Şerit kullanıcının kendi fiyatını değil, bölgesinin Supabase'teki 1 No istatistiğini gösterir.
 * Gizlilik: bölgede en az {@link MarketBackendConfig#PUBLIC_MARKET_MIN_PRODUCERS} üretici yoksa fiyat yazılmaz.
 */
final class MeydanMarketStrip {
    final String title;
    final String subtitle;
    final boolean showsPrice;

    private MeydanMarketStrip(String title, String subtitle, boolean showsPrice) {
        this.title = title;
        this.subtitle = subtitle;
        this.showsPrice = showsPrice;
    }

    /** "Alaşehir / Manisa" → "Alaşehir". */
    static String shortRegion(String label) {
        if (label == null) return "";
        String value = label.trim();
        int slash = value.indexOf('/');
        return (slash >= 0 ? value.substring(0, slash) : value).trim();
    }

    static MeydanMarketStrip loading(String regionLabel) {
        String region = shortRegion(regionLabel);
        return new MeydanMarketStrip(region.isEmpty() ? "Bölge piyasası" : region + " piyasası", "Bölge verisi yükleniyor…", false);
    }

    /**
     * @param regionLabel kullanıcının elek kayıtlarından bulunan bölge ("İlçe / İl"); bilinmiyorsa boş
     * @param cloudReady  Supabase isteği başarıyla döndü mü
     * @param producers   bölgedeki farklı üretici sayısı (bölge listede yoksa 0)
     * @param published   sunucu bölgeyi yayımladı mı
     * @param average     1 No ortalama TL/kg (eşik altında 0)
     */
    static MeydanMarketStrip of(String regionLabel, boolean cloudReady, int producers, boolean published, double average) {
        String region = shortRegion(regionLabel);
        if (region.isEmpty()) {
            return new MeydanMarketStrip("Bölge piyasası", "Elek kaydı girdiğinde bölgenin fiyatı burada görünür", false);
        }
        if (!cloudReady) {
            return new MeydanMarketStrip(region + " piyasası", "Bölge verisi şu an alınamadı", false);
        }
        int count = Math.max(0, producers);
        int floor = MarketBackendConfig.PUBLIC_MARKET_MIN_PRODUCERS;
        if (published && count >= floor && average > 0d && !Double.isNaN(average) && !Double.isInfinite(average)) {
            return new MeydanMarketStrip(region + " piyasası · 1 No",
                    "Ortalama " + RegionGradePrice.format(average) + " TL/kg · " + count + " üretici", true);
        }
        return new MeydanMarketStrip(region + " piyasası",
                "Fiyatlar " + floor + " üreticiyle açılır · şu an " + Math.min(count, floor) + "/" + floor, false);
    }
}
