package com.zeytindefteri.app;

/**
 * Tanıtım modunun uydurma kayıtları (saf, Android'siz; testte elek raporunun ne diyeceği doğrulanır).
 * Kurgu: Mehmet Bey aynı zeytine daha çok verir ve eleği iri çıkarır, Hasan Bey az verir; Hasan Bey'in
 * 18 gün önceki satışının parası hâlâ bekler; bugün de bir satış vardır.
 */
final class TanitimVerisi {
    private TanitimVerisi() {}

    static final String GARDEN_1 = "Pınarlı Zeytinliği", GARDEN_2 = "Tepeüstü Bahçesi";

    /** Sınıf başına taban fiyat (Double, 1, 2, 3, 4, Yağlık), TL/kg. */
    static final double[] BASE_PRICE = {125, 95, 65, 38, 28, 16};

    static final class Row {
        final int daysAgo, garden;   // garden: 1 ya da 2
        final String buyer;
        final double[] kg;
        final double priceDelta, partial;
        final int pay;

        Row(int daysAgo, int garden, String buyer, double[] kg, double priceDelta, int pay, double partial) {
            this.daysAgo = daysAgo; this.garden = garden; this.buyer = buyer; this.kg = kg;
            this.priceDelta = priceDelta; this.pay = pay; this.partial = partial;
        }

        double[] prices() {
            double[] p = new double[BASE_PRICE.length];
            for (int i = 0; i < p.length; i++) p[i] = BASE_PRICE[i] + priceDelta;
            return p;
        }
    }

    static Row[] rows() {
        return new Row[]{
                new Row(18, 1, "Hasan Bey", new double[]{3, 15, 45, 70, 40, 17}, -1.0, ElekSaleModel.PAY_NONE, 0),
                new Row(17, 1, "Mehmet Bey", new double[]{6, 22, 50, 62, 25, 8}, 1.0, ElekSaleModel.PAY_ALL, 0),
                new Row(12, 2, "Kemal Usta", new double[]{4, 18, 48, 60, 30, 10}, 0.0, ElekSaleModel.PAY_ALL, 0),
                new Row(10, 2, "Mehmet Bey", new double[]{7, 24, 52, 58, 22, 7}, 1.5, ElekSaleModel.PAY_ALL, 0),
                new Row(8, 1, "Hasan Bey", new double[]{2, 12, 40, 66, 45, 20}, -1.0, ElekSaleModel.PAY_ALL, 0),
                new Row(6, 1, "Mehmet Bey", new double[]{6, 20, 48, 60, 24, 8}, 1.0, ElekSaleModel.PAY_ALL, 0),
                new Row(3, 2, "Kemal Usta", new double[]{5, 19, 50, 58, 26, 9}, 0.5, ElekSaleModel.PAY_PART, 3000),
                new Row(0, 1, "Mehmet Bey", new double[]{5, 21, 47, 57, 22, 6}, 1.0, ElekSaleModel.PAY_ALL, 0),
        };
    }
}
