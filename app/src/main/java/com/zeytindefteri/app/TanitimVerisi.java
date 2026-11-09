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

    // ------------------------------------------------------------------ Zeytin Ligi (uydurma)

    /** Tanıtımdaki bölgeler: ad (ilçe / il), üretici sayısı, ortalama TL/kg, toplam kg, en çok çıkan sınıf. */
    static final class LigBolge {
        final String id, name, dominant;
        final int producers;
        final double price, kg;

        LigBolge(String id, String name, int producers, double price, double kg, String dominant) {
            this.id = id; this.name = name; this.producers = producers; this.price = price; this.kg = kg; this.dominant = dominant;
        }
    }

    /** Lig satırı: uydurma ad (ad + baş harf; tanıtım kullanıcısı NAME), baskın sınıf sırası, kg, TL/kg, puan. */
    static final class LigUretici {
        final String name;
        final int grade;
        final double kg, price, score;

        LigUretici(String name, int grade, double kg, double price, double score) {
            this.name = name; this.grade = grade; this.kg = kg; this.price = price; this.score = score;
        }
    }

    static final String LIG_BOLGE = "tanitim-gemlik";
    /** Tanıtım kullanıcısının adı (TanitimModu.NAME ile aynı; ligde "Sen" olarak görünür). */
    static final String KULLANICI = "Ahmet Yılmaz";

    static LigBolge[] ligBolgeler() {
        return new LigBolge[]{
                new LigBolge(LIG_BOLGE, "Gemlik / Bursa", 23, 61.4, 48650, "2 No"),
                new LigBolge("tanitim-orhangazi", "Orhangazi / Bursa", 17, 58.9, 31200, "3 No"),
                new LigBolge("tanitim-mudanya", "Mudanya / Bursa", 11, 60.2, 19800, "2 No"),
        };
    }

    /** Gemlik Top listesi (puana göre sıralı). Tanıtım kullanıcısı 4. sırada. */
    static LigUretici[] ligSiralama() {
        return new LigUretici[]{
                new LigUretici("Mustafa D.", 1, 4180, 66.2, 91.4),
                new LigUretici("Zeynep A.", 2, 3620, 63.8, 88.7),
                new LigUretici("İbrahim K.", 1, 2950, 62.5, 86.1),
                new LigUretici(KULLANICI, 2, 2410, 62.1, 84.9),
                new LigUretici("Fatma Ş.", 2, 3300, 60.4, 82.3),
                new LigUretici("Osman T.", 3, 2780, 59.1, 79.8),
                new LigUretici("Hatice Y.", 2, 1940, 60.9, 77.5),
                new LigUretici("Ramazan Ç.", 3, 2210, 57.6, 74.2),
                new LigUretici("Emine B.", 3, 1650, 56.8, 71.0),
        };
    }

    // ------------------------------------------------------------------ Sohbet (uydurma)

    /** Sohbet mesajı: uydurma yazar, ilçe, konu (postType), metin, kaç dakika önce, yanıt/faydalı sayısı, son yanıt. */
    static final class SohbetMesaj {
        final String author, district, type, text, replyAuthor, reply;
        final int minutesAgo, replies, useful;

        SohbetMesaj(String author, String district, String type, String text, int minutesAgo, int replies, int useful,
                    String replyAuthor, String reply) {
            this.author = author; this.district = district; this.type = type; this.text = text; this.minutesAgo = minutesAgo;
            this.replies = replies; this.useful = useful; this.replyAuthor = replyAuthor; this.reply = reply;
        }
    }

    static SohbetMesaj[] sohbet() {
        return new SohbetMesaj[]{
                new SohbetMesaj("Zeynep A.", "Gemlik", "MARKET", "Bugün 2 No elek kaç liradan gidiyor? Bizim tarafta 64 dediler.",
                        12, 3, 5, "Mustafa D.", "Bizde 65 verdiler, eleğe göre değişiyor."),
                new SohbetMesaj("Osman T.", "Orhangazi", "PEST", "Halkalı leke başladı, bakırlı ilaç için hava uygun mu sizce?",
                        48, 2, 4, "Fatma Ş.", "Bu hafta yağmur yok, cumartesi uygun görünüyor."),
                new SohbetMesaj("İbrahim K.", "Gemlik", "HARVEST", "Hasada başladık, tane iri bu sene. Herkese bereketli olsun!",
                        95, 4, 9, "Hatice Y.", "Amin, sizin de bereketli olsun."),
                new SohbetMesaj("Ramazan Ç.", "Mudanya", "QUESTION", "Salamuraya kaç gramdan tuz atıyorsunuz?",
                        180, 1, 2, "", ""),
        };
    }
}
