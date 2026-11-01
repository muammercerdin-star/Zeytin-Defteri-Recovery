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
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " AssistantPersona");
        if (bad > 0) System.exit(1);
    }
}
