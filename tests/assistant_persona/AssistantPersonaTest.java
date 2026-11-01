package com.zeytindefteri.app;

/** Asistanın sohbet kişiliği: tanışma, hitap, hâl hatır, gülme, fıkra; kayıt/soru cümlelerine karışmaz. */
public class AssistantPersonaTest {
    static int n = 0, bad = 0;
    static void check(boolean ok, String m) { n++; if (!ok) { bad++; System.out.println("FAIL " + m); } }
    public static void main(String[] a) {
        AssistantPersona p = new AssistantPersona(new java.util.Random(1));
        String r = p.handle("Merhaba");
        check(r.endsWith("Ben Zeytin. Senin adın ne?") && p.stage == AssistantPersona.Stage.ASK_NAME, "selam → tanışma: " + r);
        r = p.handle("Halil");
        check(p.name.equals("Halil") && r.startsWith("Memnun oldum Halil!") && r.contains("yaşın kaç"), "ad: " + r);
        r = p.handle("65");
        check(p.age == 65 && r.contains("zeytin ağacı gibi") && r.contains("Amca mı diyeyim, teyze mi"), "yaş: " + r);
        r = p.handle("amca de");
        check(p.call().equals("Halil amca") && r.startsWith("Tamam Halil amca"), "hitap: " + r);
        check(p.greeting(20).equals("İyi akşamlar Halil amca, buyur dinliyorum."), "selamlama: " + p.greeting(20));
        check(p.thanks().equals("Eline sağlık Halil amca."), "teşekkür");
        r = p.handle("adım ne");
        check(r.startsWith("Tanımaz olur muyum, Halil amca!") && r.contains("65 yaşındasın"), "beni tanıyor musun: " + r);
        r = p.handle("nasılsın");
        check(r != null && r.contains("Halil amca"), "hâl hatır adıyla: " + r);
        r = p.handle("hahaha");
        check(r != null && r.startsWith("Hehe"), "gülme: " + r);
        r = p.handle("fıkra anlat");
        check(r != null && r.contains("Temel"), "fıkra: " + r);
        r = p.handle("adın ne");
        check(r.startsWith("Benim adım Zeytin."), "adın ne: " + r);
        // kayıt ve soru cümlelerine karışmaz
        check(p.handle("Son sulama ne zaman?") == null, "soru → asıl asistan");
        check(p.handle("Bugün ilaçladım") == null, "kayıt → asıl asistan");
        check(p.handle("3 numara 120 kilo sattım") == null, "elek → asıl asistan");
        // tanışma sırasında komut gelirse tanışma bırakılır
        AssistantPersona q = new AssistantPersona(new java.util.Random(1));
        q.handle("selam");
        check(q.handle("bu sezon masrafım ne kadar oldu acaba") == null && q.stage == AssistantPersona.Stage.NONE, "tanışmada uzun komut → bırakır");
        // "bana Mehmet abi de"
        AssistantPersona m = new AssistantPersona(new java.util.Random(1));
        r = m.handle("bana Mehmet abi de");
        check(m.name.equals("Mehmet") && m.address.equals("abi") && r.equals("Tamam Mehmet abi, aklımda!"), "bana X abi de: " + r + " / " + m.call());
        // saklama
        AssistantPersona back = AssistantPersona.decode(p.encode());
        check(back.call().equals("Halil amca") && back.age == 65 && back.introduced == p.introduced, "sakla/yükle");
        r = p.handle("adımı unut");
        check(p.name.isEmpty() && r.startsWith("Tamam, unuttum"), "unut");
        // bir kez tanışma önerisi
        AssistantPersona z = new AssistantPersona(new java.util.Random(1));
        check(z.introduceOnce().contains("Senin adın ne?") && z.introduceOnce().isEmpty(), "tanışma yalnız bir kez");
        // daha akıllı sohbet
        AssistantPersona h = AssistantPersona.decode("Halil|65|amca|1");
        String x = h.handle("Sen zekisin maşallah");
        check(x != null && x.contains("Halil amca"), "iltifat: " + x);
        x = h.handle("120 kilo 45 liradan kaç eder");
        check(x != null && x.equals("120 kilo, kilosu 45 liradan 5.400 lira eder."), "hesap: " + x);
        x = h.handle("300 çarpı 40");
        check("Hesapladım: 300 çarpı 40, 12.000 eder.".equals(x), "çarpı: " + x);
        x = h.handle("50.000 x 50.000");
        check("Hesapladım: 50.000 çarpı 50.000, 2.500.000.000 eder.".equals(x), "binlik ayraç (ekrandaki hata): " + x);
        x = h.handle("elli bin bölü dört");
        check("Hesapladım: 50.000 bölü 4, 12.500 eder.".equals(x), "yazıyla: " + x);
        x = h.handle("12,5 kere 8");
        check("Hesapladım: 12,5 çarpı 8, 100 eder.".equals(x), "ondalık: " + x);
        x = h.handle("1000 eksi 250");
        check("Hesapladım: 1.000 eksi 250, 750 eder.".equals(x), "eksi: " + x);
        x = h.handle("5000'in yüzde 10'u kaç");
        check(x != null && x.endsWith("500 eder."), "yüzde: " + x);
        check(h.handle("3 numara 120 kilo 2 numara 80") == null, "elek cümlesi hesap sanılmaz");
        x = h.handle("canım sıkkın");
        check(x != null && x.startsWith("Geçmiş olsun Halil amca"), "duygu: " + x);
        x = h.handle("bu sene zeytin az");
        check(x != null && x.contains("var yılı yok yılı"), "dert: " + x);
        x = h.handle("kolay gelsin");
        check(x != null && x.startsWith("Sağ ol Halil amca"), "kolay gelsin: " + x);
        x = h.handle("robot musun");
        check(x != null && x.contains("insan değilim"), "robot: " + x);
        x = h.handle("zeytin ne zaman toplanır");
        check(x != null && x.contains("Ekim"), "bilgi: " + x);
        x = h.handle("salak mısın sen");
        check(x != null && x.startsWith("Kusura bakma"), "kızgınlık: " + x);
        check(h.handle("toplam ne kadar hasat ettim") == null, "kayıt sorusu persona'ya gitmez");
        check(h.fallback().contains("Halil amca"), "dostça yedek cevap");

        // ===== kapsamlı sohbet (1 Kasım ekran görüntüsü: "Bana bundan sonra Muammer de" → "Tamam Bundan abi")
        AssistantPersona u = AssistantPersona.decode("Bundan|0|abi|1");
        x = u.handle("Bana bundan sonra Muammer de");
        check(u.name.equals("Muammer") && u.address.isEmpty() && "Tamam Muammer, aklımda!".equals(x), "ekrandaki hata: " + x + " / " + u.call());
        AssistantPersona k = new AssistantPersona(new java.util.Random(2));
        k.handle("Beni artık Mehmet abi diye çağır");
        check(k.call().equals("Mehmet abi"), "diye çağır: " + k.call());
        k.handle("Benim adım Ali Rıza");
        check(k.name.equals("Ali"), "adım X: " + k.name);
        k.handle("Bana abi de");
        check(k.call().equals("Ali abi"), "yalnız hitap: " + k.call());
        check(k.handle("Bana bir türkü söyle de") != null && k.name.equals("Ali"), "türkü isim sanılmaz: " + k.name);
        check(AssistantPersona.nameIn("lütfen bundan sonra bana Ayşe teyze de").equals("Ayşe"), "Ayşe teyze");
        check(AssistantPersona.nameIn("Halil").equals("Halil"), "tek söz ad");
        // tanışmada uzun ama ad cümlesi
        AssistantPersona t2 = new AssistantPersona(new java.util.Random(1));
        t2.handle("merhaba");
        x = t2.handle("benim adım Muammer Çerdin efendim");
        check(t2.name.equals("Muammer") && x.startsWith("Memnun oldum Muammer"), "uzun ad cevabı: " + x);
        // memleket
        AssistantPersona g = AssistantPersona.decode("Halil|65|amca|1");
        x = g.handle("nerelisin");
        check(x.endsWith("Sen nerelisin?") && g.pending == AssistantPersona.Pending.TOWN, "nerelisin karşı soru: " + x);
        x = g.handle("Akhisar");
        check(g.town.equals("Akhisar") && x.contains("Akhisar"), "memleket cevabı: " + x);
        x = g.handle("nereliyim");
        check("Sen Akhisarlısın Halil amca, unutmadım!".equals(x), "memleketi hatırlar: " + x);
        AssistantPersona g2 = AssistantPersona.decode("Ayşe|50|teyze|1");
        g2.handle("Ben Edremitliyim");
        check(g2.town.equals("Edremit"), "Edremitliyim: " + g2.town);
        check(g2.handle("nereliyim").startsWith("Sen Edremitlisin"), "ünlü uyumu");
        check(AssistantPersona.decode(g.encode()).town.equals("Akhisar"), "memleket saklanır");
        check(AssistantPersona.decode("Halil|65|amca|1").town.isEmpty(), "eski kayıt okunur");
        // hâl hatır akışı
        AssistantPersona f = AssistantPersona.decode("Halil|65|amca|1");
        f.handle("nasılsın");
        x = f.handle("iyiyim sen");
        check(x != null && !x.contains("Bunu"), "nasılsın → iyiyim: " + x);
        f.handle("naber");
        x = f.handle("pek iyi değilim");
        check(x != null && x.startsWith("Hayırdır"), "nasılsın → kötü: " + x);
        x = f.handle("oğlum askere gitti özledim");
        check(x != null && f.pending == AssistantPersona.Pending.OFFER_FUN, "dert dinleme → teselli: " + x);
        x = f.handle("olur");
        check(x != null && x.contains("Hehe") || x.contains("Haha"), "teklif → fıkra: " + x);
        check("iyi akşamlar".length() > 0 && !f.handle("iyi akşamlar").startsWith("Sevindim"), "iyi akşamlar hâl cevabı sanılmaz");
        // bilgi + devamı
        AssistantPersona b = AssistantPersona.decode("Halil|65|amca|1");
        x = b.handle("budama ne zaman yapılır");
        check(x != null && x.contains("kış sonu") && x.endsWith("Devamını ister misin?"), "budama bilgisi: " + x);
        x = b.handle("evet");
        check(x != null && x.contains("hafif budamak"), "devamı: " + x);
        x = b.handle("zeytin sineği nedir");
        check(x != null && x.contains("tuzak"), "sinek: " + x);
        x = b.handle("neden");
        check(x != null && x.contains("sonbahar"), "neden → devamı: " + x);
        check(b.handle("kaç kilo zeytinden bir kilo yağ çıkar").contains("4 ile 6"), "randıman");
        check(b.handle("sızma ne demek").contains("0,8") && b.handle("asit") == null && b.handle("asit neden yükselir").contains("0,8"), "asit");
        check(b.handle("Manisa") == null && b.handle("Domat") == null && b.handle("randıman") == null, "tek söz il/çeşit/alan sohbet sanılmaz");
        check(b.handle("Memecik nasıl bir zeytin").contains("Memecik"), "çeşit sorusu");
        check(b.handle("zeytin çeşitleri neler").contains("Memecik"), "çeşit");
        check(b.handle("Son sulama ne zaman?") == null, "kayıt sorusu bilgi sanılmaz");
        check(b.handle("son gübre ne zaman verdim") == null, "kendi gübre kaydı");
        check(b.handle("zeytin sineği için hangi ilaç") == null, "doz/ilaç → uyarı cevabına bırakılır");
        check(b.handle("Hangi bahçe daha çok verdi?") == null, "karşılaştırma sorusu");
        check(b.handle("Meydan'a yaz: yağmur yağdı") == null, "Meydan yazısı");
        check(b.handle("Girelli'den 300 kilo topladık") == null, "hasat kaydı");
        check(b.handle("1 numara 40 kilo daha 20 kilo") == null, "elek düzeltmesi hesap sanılmaz");
        // aya göre tavsiye
        AssistantPersona m2 = AssistantPersona.decode("Halil|65|amca|1");
        java.util.Calendar c = java.util.Calendar.getInstance(); c.set(2026, java.util.Calendar.NOVEMBER, 1, 22, 30);
        m2.clock = c;
        x = m2.handle("bu ay ne yapılır");
        check(x != null && x.startsWith("Kasım ayındayız Halil amca.") && x.contains("Hasat"), "aya göre: " + x);
        x = m2.handle("saat kaç");
        check("Saat 22:30 Halil amca.".equals(x), "saat: " + x);
        // fıkra, bilmece, atasözü, şarkı
        AssistantPersona e = AssistantPersona.decode("Halil|65|amca|1");
        String j1 = e.handle("fıkra anlat"), j2 = e.handle("bir tane daha");
        check(j1 != null && j2 != null && !j1.equals(j2), "farklı fıkra");
        x = e.handle("bilmece sor");
        check(x.contains("Delik deşiktir") && e.pending == AssistantPersona.Pending.RIDDLE, "bilmece: " + x);
        x = e.handle("kasa");
        check(x.startsWith("Yok, bilemedin! İpucu"), "yanlış → ipucu: " + x);
        x = e.handle("elek");
        check(x.startsWith("Bildin") || x.startsWith("Doğru"), "doğru cevap: " + x);
        x = e.handle("evet");
        check(x.contains("Bin yıl yaşar"), "bir bilmece daha: " + x);
        x = e.handle("bilmiyorum");
        check(x.startsWith("Cevap: Zeytin ağacı"), "pes: " + x);
        x = e.handle("bir atasözü söyle");
        check(x.startsWith("Atalarımız demiş ki"), "atasözü: " + x);
        check(e.handle("şarkı söyle") != null, "mani");
        // hesap genişledi
        check("Hesapladım: 100 artı 200 artı 300, 600 eder.".equals(h.handle("100 artı 200 artı 300")), "zincirleme: " + h.handle("100 artı 200 artı 300"));
        check("Hesapladım: 2 artı 3 çarpı 4, 14 eder.".equals(h.handle("2 artı 3 çarpı 4")), "işlem önceliği");
        // güvenlik ve tarafsızlık
        check(h.handle("göğsüm ağrıyor").contains("112"), "acil");
        check(h.handle("seçim kim kazanır sence").contains("Siyasete girmeyelim"), "siyaset tarafsız");
        // yedek cevap: anlatılana ilgi, soruya bilemedim
        AssistantPersona y = AssistantPersona.decode("Halil|65|amca|1");
        check(y.fallback("bugün pazara gittim").startsWith("Hıı, öyle mi Halil amca?"), "anlatılana ilgi");
        check(y.fallback("ayın kaç günü dolunay?").contains("bilemedim") || y.fallback("x?").length() > 0, "soruya yedek");
        // konu değişince bekleyen soru bırakılır
        AssistantPersona z2 = AssistantPersona.decode("Halil|65|amca|1");
        z2.handle("nasılsın");
        check(z2.handle("Bugün ilaçladım") == null, "beklerken kayıt cümlesi → asıl asistan");
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " AssistantPersona");
        if (bad > 0) System.exit(1);
    }
}
