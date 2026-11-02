package com.zeytindefteri.app;

import java.util.Arrays;

public final class CurrentPricesTest {
    static int n, bad;
    static void check(boolean ok, String m) { n++; if (!ok) { bad++; System.out.println("FAIL " + m); } }

    public static void main(String[] a) {
        String json = "[{\"grade\":\"Double\",\"price\":125,\"window_days\":3,\"sale_count\":4,\"producer_count\":3,\"last_sale_date\":\"2026-11-02\"},"
                + "{\"grade\":\"1 No\",\"price\":90.5,\"window_days\":7,\"sale_count\":1,\"producer_count\":1,\"last_sale_date\":null},"
                + "{\"grade\":\"Uçurtma\",\"price\":5},{\"grade\":\"2 No\",\"price\":0}]";
        CurrentPrices.Entry[] r = CurrentPrices.parse(json);
        check(r[0] != null && r[0].price == 125 && r[0].windowDays == 3 && r[0].saleCount == 4, "Double okunur");
        check(r[1] != null && r[1].price == 90.5 && r[1].lastSale.isEmpty(), "1 No okunur, boş tarih");
        check(r[2] == null && r[3] == null, "0 fiyat ve bilinmeyen elek alınmaz");
        check(!CurrentPrices.any(CurrentPrices.parse("bozuk")) && !CurrentPrices.any(CurrentPrices.parse(null)), "bozuk cevap → boş");
        check(CurrentPrices.index("DOUBLE") == 0 && CurrentPrices.index("1") == 1 && CurrentPrices.index("YAGLIK") == 5
                && CurrentPrices.index("Yağlık") == 5 && CurrentPrices.index("4 No") == 4 && CurrentPrices.index("x") == -1, "elek adları");

        check(CurrentPrices.median(Arrays.asList(120d, 90d, 100d)) == 100d, "medyan tek");
        check(CurrentPrices.median(Arrays.asList(120d, 100d)) == 110d, "medyan çift");
        check(CurrentPrices.median(null) == 0d, "medyan boş");

        double[] own = {0, 0, 0, 0, 44, 0};
        double[] settings = {100, 90, 70, 55, 40, 32};
        CurrentPrices.Choice c = CurrentPrices.choose(r, own, settings);
        check(c.price[0] == 125 && c.source[0] == CurrentPrices.REGION, "Double bölgeden");
        check(c.price[1] == 90.5 && c.source[1] == CurrentPrices.REGION, "1 No bölgeden (7 gün)");
        check(c.price[4] == 44 && c.source[4] == CurrentPrices.OWN, "4 No senin son satışların");
        check(c.price[5] == 32 && c.source[5] == CurrentPrices.SETTINGS, "Yağlık fiyat ayarı");

        String note = CurrentPrices.note(c, "Alaşehir");
        check(note.startsWith("Fiyatlar: Alaşehir piyasası · son 3–7 gün"), "not pencere aralığı: " + note);
        check(note.contains("4 No: son satışların") && note.contains("2 No, 3 No, Yağlık: fiyat ayarın"), "not kaynaklar: " + note);
        check(CurrentPrices.note(CurrentPrices.choose(new CurrentPrices.Entry[6], own, settings), "X") == null, "bölge yoksa not yok");
        String detail = CurrentPrices.detail(c);
        check(detail.contains("Double: 125 TL · bölge, son 3 gün, 4 satış") && detail.contains("1 No: 90,5 TL"), "ayrıntı: " + detail);

        check(CurrentPrices.marketLine(r[0]).equals("Güncel fiyat · son 3 gün: 125 TL (4 satış)"), "piyasa satırı: " + CurrentPrices.marketLine(r[0]));
        check(CurrentPrices.marketLine(null).isEmpty(), "veri yoksa satır yok");
        check(CurrentPrices.money(122.25).equals("122,25 TL") && CurrentPrices.money(80).equals("80 TL"), "para biçimi");

        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " güncel fiyat");
        if (bad > 0) System.exit(1);
    }
}
