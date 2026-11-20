package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Ortak döngü temizleyicisi (İP8-A): isim, sayı, tutar ve bahçe adı içeren cümlelerden öğretilen sözden yalnız
 * "kök + söz + anlam + il + sürüm" çıkar; başka hiçbir şey çıkmaz. Ada / bahçeye / rakama benzeyen, genel, çok kısa ya da
 * uzun söz hiç gönderilmez. "Söz" alanı: küçük harf, kesme işareti ve sonrası atılır, yalnız çekimli fiil.
 */
public class ContributionCleanerTest {
    static int n = 0;
    static int bad = 0;

    static void check(boolean ok, String m) {
        n++;
        if (!ok) {
            bad++;
            System.out.println("FAIL " + m);
        }
    }

    static void eq(Object got, Object want, String m) {
        check(want.equals(got), m + ": «" + got + "» beklenen «" + want + "»");
    }

    /** Telefondaki adlar (bahçe, alıcı, kullanıcı): yalnız karşılaştırma için. */
    static final List<String> LOCAL = Arrays.asList("Girelli", "Kuyu dibi", "Alt bahçe", "Ali Usta", "Hasan Abi", "Kemal",
            "Kooperatif", "Muammer");

    /** cümle | öğretilen anlam (düğme). Cümlelerde isim, sayı, tutar, bahçe adı, tarih var. */
    static final String[][] SENTENCES = {
            {"Hasan abiyle Girelli'yi 300 liraya bakırladık", "ilaçlama"},
            {"dün Kuyu dibini 2 bin liralık ilaçla bakırladık", "ilaçlama"},
            {"Ali Usta geldi 15 dönümü göztaşıladık", "ilaçlama"},
            {"12 Ocak'ta Alt bahçeyi göztaşıladık", "ilaçlama"},
            {"Mehmet'in traktörüyle 40 ağacı bordoladık", "ilaçlama"},
            {"Kemal 500 TL aldı biz bordoladık", "ilaçlama"},
            {"Girelli'yi 3 saat suvardık", "sulama"},
            {"Ayşe teyzenin kuyusundan 20 ton su verip suvardık", "sulama"},
            {"bu sabah 06:30'da Kuyu dibini suvardık", "sulama"},
            {"Kooperatif'ten 1200 liralık mazotla suvardık", "sulama"},
            {"Hasan Abi'nin bahçesini 4 kişi silktik", "hasat"},
            {"Girelli'den 800 kilo silktik", "hasat"},
            {"dün 3 numara 120 kilo silktik", "hasat"},
            {"Muammer'le birlikte 2 ton sırıkladık", "hasat"},
            {"Alt bahçede 15 ağaç sırıkladık 900 lira yevmiye", "hasat"},
            {"Ali Usta'nın işçileriyle 600 kilo devşirdik", "hasat"},
            {"5 Kasım 2026 günü Girelli'de devşirdik", "hasat"},
            {"Kemal'in diskarosuyla 8 dönüm diskaradık", "toprak işleme"},
            {"Kuyu dibini 1500 liraya diskaradık", "toprak işleme"},
            {"Hasan'ın traktörüyle 2 kez belledik", "toprak işleme"},
            {"Girelli'nin alt tarafını 3 günde belledik", "toprak işleme"},
            {"Mehmet usta 70 ağacı seyrelttik dedi biz de seyrelttik", "budama"},
            {"Alt bahçede 25 ağacı seyrelttik", "budama"},
            {"Ayşe Hanım'ın fidanlarını 40 tane kalemledik", "aşılama"},
            {"Kuyu dibinde 300 liraya 12 ağaç kalemledik", "aşılama"},
            {"Ali Usta'yla Girelli'yi 2 saatte tırpanladık", "ot biçme"},
            {"bugün 4 işçiyle 1600 liraya tırpanladık", "ot biçme"},
            {"Hasan abi 0532 555 44 33 aradı sonra bakırladık", "ilaçlama"},
            {"Kooperatif'e 3500 TL borçla göztaşıladık", "ilaçlama"},
            {"Muammer 2. kez bordoladık dedi", "ilaçlama"},
            {"%30 indirimli ilaçla bakırladık", "ilaçlama"},
            {"Girelli 1. parseli suvardık", "sulama"},
            {"Kuyu dibi 7 nolu kuyudan suvardık", "sulama"},
            {"Ali'nin 250 kilosunu silktik", "hasat"},
            {"Hasan Abi'ye 120 kilo sırıkladık", "hasat"},
            {"Kemal 10 kişiyle devşirdik", "hasat"},
            {"3 dönüm 1. sınıf toprağı diskaradık", "toprak işleme"},
            {"Mehmet'e 600 lira verip belledik", "toprak işleme"},
            {"Ayşe'nin 18 ağacını seyrelttik", "budama"},
            {"Fatma hanımın 9 fidanını kalemledik", "aşılama"},
            {"Girelli ve Kuyu dibi 2 bahçeyi tırpanladık", "ot biçme"},
            {"saat 17'de Alt bahçeyi bakırladık", "ilaçlama"},
            {"Ali Usta 45 litre ilaçla göztaşıladık", "ilaçlama"},
            {"Hasan'ın oğlu Emre'yle bordoladık", "ilaçlama"},
            {"Kooperatif suyuyla 6 saat suvardık", "sulama"},
            {"Muammer'in bahçesinde 1,5 ton silktik", "hasat"},
            {"Kemal'le 22 çuval sırıkladık", "hasat"},
            {"Girelli'nin 2. tarlasını devşirdik", "hasat"},
            {"Hasan Abi 4000 lira istedi diskaradık", "toprak işleme"},
            {"Kuyu dibinde 12 sıra belledik", "toprak işleme"},
            {"Ali'yle 33 ağacı seyrelttik", "budama"},
            {"Mustafa'nın 5 ağacını kalemledik", "aşılama"},
            {"Alt bahçe ile Girelli'yi 900 TL'ye tırpanladık", "ot biçme"},
            {"Ayşe Hanım 20.11.2026 tarihinde bakırladık dedi", "ilaçlama"},
            {"Kemal ağabey 150 kilo ilaçla göztaşıladık", "ilaçlama"},
            {"Hasan 7 bidonla suvardık", "sulama"},
    };

    public static void main(String[] a) {
        sentences();
        words();
        rejects();
        provinceAndVersion();
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " ContributionCleaner");
        if (bad > 0) {
            System.exit(1);
        }
    }

    /** 56 cümle: yalnız söz + anlam + il + sürüm çıkar; isim, sayı, tutar, bahçe adı, tarih çıkmaz. */
    static void sentences() {
        check(SENTENCES.length >= 50, "en az 50 örnek cümle (" + SENTENCES.length + ")");
        int sent = 0;
        for (String[] s : SENTENCES) {
            String word = LearnedTerms.candidate(s[0]);
            check(word != null, "öğretilecek söz var: " + s[0]);
            if (word == null) {
                continue;
            }
            LearnedTerms.Term t = LearnedTerms.teach(word, LearnedTerms.choiceFor(s[1]), LearnedTerms.SOURCE_VOICE,
                    1763640000000L);
            ContributionCleaner.Contribution c = ContributionCleaner.clean(t, LOCAL, "İzmir", "1.7.0");
            check(c != null, "gönderilebilir: " + s[0]);
            if (c == null) {
                continue;
            }
            sent++;
            Map<String, String> f = c.fields();
            eq(new ArrayList<>(f.keySet()).toString(), "[kok, soz, eylem, tur, not, il, surum]", "yalnız yedi alan");
            String all = String.join(" ", f.values());
            check(!f.get("kok").matches(".*\\d.*") && !f.get("soz").matches(".*\\d.*") && !f.get("il").matches(".*\\d.*"),
                    "sözde ve ilde rakam yok: " + all);
            String allFolded = TurkishText.fold(f.get("kok") + " " + f.get("soz") + " " + f.get("il"));
            for (String token : TurkishText.fold(s[0]).split("[^a-z0-9]+")) {
                if (token.isEmpty() || TurkishText.fold(word).startsWith(token)) {
                    continue;
                }
                check(!(" " + allFolded + " ").contains(" " + token + " "), "cümleden başka kelime çıkmadı «" + token + "»: " + all);
            }
            for (String name : LOCAL) {
                check(!all.toLowerCase().contains(name.toLowerCase()), "ad çıkmadı «" + name + "»: " + all);
            }
            eq(f.get("soz"), word.toLowerCase(new java.util.Locale("tr", "TR")), "söz küçük harf, tek kelime");
            eq(f.get("il"), "İzmir", "il (ilçe/köy değil)");
            eq(f.get("surum"), "1.7.0", "sürüm");
            check(!all.contains("1763640000000") && !all.contains("ses"), "tarih / kaynak / kullanım çıkmadı");
        }
        check(sent == SENTENCES.length, "hepsi temizlendi: " + sent);
        ContributionCleaner.Contribution diger = ContributionCleaner.clean(
                LearnedTerms.teach("tırpanladık", LearnedTerms.choiceFor("ot biçme"), LearnedTerms.SOURCE_TEXT, 1), LOCAL, "", "1.7.0");
        eq(diger.fields().toString(), "{kok=tirpanl, soz=tırpanladık, eylem=ISLEM, tur=Diğer, not=Ot biçme, il=, surum=1.7.0}",
                "Diğer işi: not etiketiyle; il boşsa ilsiz");
        ContributionCleaner.Contribution hasat = ContributionCleaner.clean(
                LearnedTerms.teach("silktik", LearnedTerms.choiceFor("hasat"), LearnedTerms.SOURCE_TEXT, 1), LOCAL, "Manisa", "1.7.0");
        eq(hasat.key(), "silk|HASAT||", "söz-anlam anahtarı");
    }

    /** "Söz" alanı: küçük harf, kesme işareti ve sonrası atılır, yalnız çekimli fiil, 4–20 harf. */
    static void words() {
        eq(ContributionCleaner.word("Bakırladık"), "bakırladık", "küçük harf");
        eq(ContributionCleaner.word("BAKIRLADIK"), "bakırladık", "büyük harf (Türkçe ı)");
        eq(ContributionCleaner.word("İlaçladık"), "ilaçladık", "Türkçe İ");
        eq(ContributionCleaner.word("bakırladık'ı"), "bakırladık", "kesme işareti ve sonrası atılır");
        eq(ContributionCleaner.word("suvardık’mış"), "suvardık", "kıvrık kesme işareti");
        eq(ContributionCleaner.word("  silktik  "), "silktik", "boşluklar");
        eq(ContributionCleaner.word("bakır"), "", "çekimli fiil değil (ad)");
        eq(ContributionCleaner.word("Girelli"), "", "çekimli fiil değil (bahçe adı)");
        eq(ContributionCleaner.word("bakır attık"), "", "tek kelime değil");
        eq(ContributionCleaner.word("3kez"), "", "rakam");
        eq(ContributionCleaner.word("bak1rladık"), "", "rakam içinde");
        eq(ContributionCleaner.word("ab"), "", "çok kısa");
        eq(ContributionCleaner.word("bakırlayıverdiklerimizdenmişsiniz"), "", "çok uzun");
        eq(ContributionCleaner.word("bakır-ladık"), "", "harf dışı işaret");
        eq(ContributionCleaner.word(null), "", "boş");
    }

    /** Hiç gönderilmeyenler. */
    static void rejects() {
        LearnedTerms.Choice ilac = LearnedTerms.choiceFor("ilaçlama");
        String[][] names = {{"hasanladık", "yaygın kişi adı"}, {"mehmetledik", "yaygın kişi adı"}, {"girelliledik", "bahçe adı"},
                {"kemalledik", "alıcı adı"}, {"muammerledik", "kullanıcı adı"}, {"kooperatifledik", "alıcı adı"},
                {"ustaladık", "hitap"}};
        for (String[] w : names) {
            LearnedTerms.Term t = LearnedTerms.teach(w[0], ilac, LearnedTerms.SOURCE_VOICE, 1);
            check(t.term.isEmpty() || ContributionCleaner.clean(t, LOCAL, "İzmir", "1.7.0") == null, "gönderilmez (" + w[1] + "): " + w[0]);
        }
        LearnedTerms.Term generic = new LearnedTerms.Term("att", "attık", ActionCatalog.ISLEM, "tur", "İlaçlama",
                LearnedTerms.TAUGHT, LearnedTerms.SOURCE_VOICE, 1, 0);
        check(ContributionCleaner.clean(generic, LOCAL, "İzmir", "1.7.0") == null, "genel fiil gönderilmez");
        LearnedTerms.Term pack = new LearnedTerms.Term("bakirl", "bakırladık", ActionCatalog.ISLEM, "tur", "İlaçlama",
                "paket", "paket", 1, 0);
        check(ContributionCleaner.clean(pack, LOCAL, "İzmir", "1.7.0") == null, "paketten gelen söz gönderilmez");
        LearnedTerms.Term broken = new LearnedTerms.Term("bakirl", "bakırladık", ActionCatalog.ISLEM, "tur", "Mazot",
                LearnedTerms.TAUGHT, LearnedTerms.SOURCE_VOICE, 1, 0);
        check(ContributionCleaner.clean(broken, LOCAL, "İzmir", "1.7.0") == null, "anlamı seçime karşılık gelmeyen gönderilmez");
        LearnedTerms.Term mismatch = new LearnedTerms.Term("suvar", "bakırladık", ActionCatalog.ISLEM, "tur", "İlaçlama",
                LearnedTerms.TAUGHT, LearnedTerms.SOURCE_VOICE, 1, 0);
        check(ContributionCleaner.clean(mismatch, LOCAL, "İzmir", "1.7.0") == null, "kök sözle tutmuyorsa gönderilmez");
        check(ContributionCleaner.clean(null, LOCAL, "İzmir", "1.7.0") == null, "boş");
    }

    static void provinceAndVersion() {
        eq(ContributionCleaner.provinces().size(), 81, "81 il");
        eq(ContributionCleaner.province("izmir"), "İzmir", "il yazımı düzelir");
        eq(ContributionCleaner.province("MANİSA"), "Manisa", "büyük harf");
        eq(ContributionCleaner.province("Alaşehir"), "", "ilçe gönderilmez");
        eq(ContributionCleaner.province("Girelli köyü"), "", "köy gönderilmez");
        eq(ContributionCleaner.province(""), "", "il boşsa ilsiz");
        eq(ContributionCleaner.version("1.7.0"), "1.7.0", "sürüm");
        eq(ContributionCleaner.version("1.7.0-debug Muammer"), "", "sürüm alanına başka bir şey girmez");
    }
}
