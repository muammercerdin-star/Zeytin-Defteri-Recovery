package com.zeytindefteri.app;

import java.util.regex.Pattern;

/**
 * Zeytin'in sohbet dağarcığı (saf, Android'siz, testli): genel zeytincilik bilgisi (kesin doz/teşhis vermez),
 * aya göre bahçe tavsiyesi, fıkra, bilmece, atasözü, mani. Bilgiler genel bilgidir; ilaç, doz ve hastalık kararı
 * için hep ziraat mühendisine yönlendirir.
 */
final class AssistantKnowledge {
    private AssistantKnowledge() {}

    static final class Topic {
        final String key;
        final Pattern trigger;
        final String answer, more;
        Topic(String key, String trigger, String answer, String more) {
            this.key = key; this.trigger = Pattern.compile(trigger); this.answer = answer; this.more = more;
        }
    }

    /** Kendi kaydını soran cümle ("Son sulama ne zaman?", "Benim bahçeye gübre ne zaman verdim") bilgi sorusu değildir. */
    private static final Pattern RECORD = Pattern.compile(
            "\\b(son|en son|benim|bahcem\\p{L}*|bahcemi\\p{L}*|kayit\\p{L}*|kac kez|kac defa|kac kere|yaptim|yaptik|verdim|verdik|attim|attik|"
                    + "sattim|sattik|topladim|topladik|toplam|masraf\\p{L}*|bu sezon|cikti|cikardim|aldim|aldik)\\b");
    /** Doz / ilaç adı / teşhis: sabit uyarı cevabı (ZeytinAssistantText.special) verir, sohbet karışmaz. */
    static final Pattern DOSE = Pattern.compile("\\b(doz\\p{L}*|teshis|hangi ilac\\p{L}*|ne kadar ilac|kac litre|kac gram|kac cc|ilac adi)\\b");

    static final Topic[] TOPICS = {
            new Topic("hasat",
                    "\\b(hasat|toplan|toplama)\\p{L}*.*\\b(ne zaman|zamani|hangi ay)\\b|\\b(ne zaman|hangi ay)\\b.*\\b(hasat|toplan)\\p{L}*",
                    "Bizim yörede zeytin hasadı genelde Ekim'de başlar, Aralık'a kadar sürer. Sofralıkta rengi dönünce, yağlıkta biraz daha bekletilir. Kesin zamanı zeytinin olgunluğuna bakıp sen bilirsin.",
                    "Erken hasatta yağ az ama kalitesi yüksek, tadı acımsı olur. Geç hasatta yağ oranı artar ama asit yükselebilir. Sofralık yeşil için daha erken, siyah için tam kararınca toplanır."),
            new Topic("meyve",
                    "\\b(kac (yilda|senede|yasinda) meyve|ne zaman meyve|meyveye (yatar|baslar|gelir|girer))\\b",
                    "Aşılı zeytin fidanı genelde 4-5 yılda meyveye başlar, tam verime ise 8-10 yılda girer. Bakımla, suyla çok değişir tabii.",
                    "Tohumdan yetişen ağaç çok daha geç meyveye yatar. O yüzden fidan alırken aşılı ve sertifikalı fidan almak önemli."),
            new Topic("sinek",
                    "\\b(zeytin sinegi|sinek (nedir|ne zaman|zarar\\p{L}*|nasil)|sinekle mucadele|sinek kurtlu|kurtlu zeytin)\\b",
                    "Zeytin sineği meyvenin içine yumurta bırakır, kurt düşer, zeytin dökülür, yağın kalitesi bozulur. Bahçeye tuzak asıp sayıyı takip etmek iyi olur; ilaçlama zamanını ziraat mühendisine danış.",
                    "Sinek genelde yaz sonu ve sonbaharda, havalar ılıyınca artar. Tuzaktaki sinek sayısı artınca mücadele zamanı gelmiş demektir; kararı ilçe tarımın uyarılarıyla ver."),
            new Topic("varyok",
                    "\\b(var yili|yok yili|periyodisite|bir (yil|sene) (verip|veriyor|vermiyor)|neden her (yil|sene) (vermiyor|ayni vermiyor)|her (yil|sene) vermiyor)\\b",
                    "Zeytinin bir yıl bol, bir yıl az vermesine var yılı, yok yılı derler. Düzenli budama, gübre ve su bu farkı azaltır.",
                    "Bol yıldan sonra ağaç yorulur, ertesi yılın meyve gözleri az oluşur. Hasadı geciktirmemek ve ağacı iyi beslemek dengeyi sağlar."),
            new Topic("budama",
                    "\\bbudama\\p{L}*.*\\b(ne zaman|nasil|zamani|hangi ay|yapilir|yapilmali)\\b|\\b(ne zaman|nasil)\\b.*\\bbuda(nir|nmali|mak|ma)\\b",
                    "Budama genelde hasattan sonra, kış sonu ile ilkbahar başında, don tehlikesi geçince yapılır. Ağacın ortası güneş alsın diye içe dönen dallar, kuru ve hastalıklı dallar alınır.",
                    "Çok sert budama o yılın verimini düşürür; her yıl hafif budamak daha iyidir. Budama aletlerini arada temizlemek dal kanserinin bulaşmasını azaltır."),
            new Topic("gubre",
                    "\\bgubre\\p{L}*.*\\b(ne zaman|nasil|zamani|hangi ay|verilir|verilmeli|atilir|atilmali|hangisi)\\b|\\b(ne zaman|nasil)\\b.*\\bgubre\\p{L}*",
                    "Genel olarak ana gübre kış sonunda, yağışlarla toprağa karışsın diye verilir; ilkbaharda da takviye yapılır. Ne kadar vereceğin toprağa göre değişir; toprak analizi yaptırıp ziraat mühendisine danışmak en doğrusu.",
                    "Toprak analizi ucuzdur ve çoğu ilçe tarım müdürlüğü yol gösterir. Fazla gübre hem paranı boşa harcar hem ağaca zarar verir."),
            new Topic("sulama",
                    "\\b(sulama|sulan|su ver)\\p{L}*.*\\b(ne zaman|nasil|zamani|gerekir mi|lazim mi|ister mi)\\b|\\bzeytin su (ister|sever) mi\\b|\\bzeytin susuz\\p{L}*",
                    "Zeytin kurağa dayanıklıdır ama çiçeklenmede, meyve tutumunda ve yaz sonunda yağ dolarken su verirsen verim ve tane iriliği artar.",
                    "Damlama en verimli yoldur. Hasattan birkaç hafta önce suyu kesmek yağ oranını artırır derler; sofralıkta ise tane iri olsun diye su kesilmez."),
            new Topic("randiman",
                    "\\bkac kilo\\p{L}* .*\\byag|\\bkac kiloda bir\\b|\\b(yag orani|yag randimani|yag verimi|randiman\\p{L}*)\\b.*\\b(nedir|ne demek|olmali|nasil|neden|niye|kac olur|ne kadar olur)\\b",
                    "Ortalama 4 ile 6 kilo zeytinden 1 kilo yağ çıkar. Çeşide, olgunluğa, yağışa göre değişir; erken hasatta yağ daha az çıkar.",
                    "Yağmurdan hemen sonra toplanan zeytin su çektiği için randıman düşük görünür. Fabrikada yağ oranını ölçtürmek pazarlıkta işine yarar."),
            new Topic("asit",
                    "\\basit\\p{L}*\\b.*\\b(nedir|ne demek|olmali|nasil|neden|niye|yukselir|duser|dusurulur)\\b|\\b(sizma (ne demek|nedir|nasil)|naturel sizma (ne|nedir|nasil))\\b",
                    "Natürel sızma zeytinyağında asit en fazla yüzde 0,8 olmalı. Zeytini ezmeden, bekletmeden çabuk sıktırmak asidi düşük tutar.",
                    "Ezilen, çürüyen, sinekli ya da çuvalda bekleyip ısınan zeytin asidi yükseltir. Temiz, sağlam zeytin ve hızlı sıkım düşük asit demek."),
            new Topic("cesit",
                    "\\b(zeytin cesitleri|cesitler (neler|nelerdir)|ne cesitler var|hangi cesitler var|cesitleri neler)\\b|\\b(memecik|domat|uslu|gemlik|ayvalik|edremit|trilye|nizip)\\p{L}*\\b.*\\b(nedir|nasil bir|nasildir|hakkinda|anlat\\p{L}*|ne demek|ozelligi)\\b",
                    "Türkiye'de bilinen çeşitler: Gemlik siyah sofralık; Ayvalık ya da Edremit yağlık; Memecik hem yağlık hem sofralık; Domat ve Uslu Akhisar'ın sofralıkları; Güneydoğu'da Nizip ve Kilis yağlık.",
                    "Yağlıkta Ayvalık ve Memecik meşhurdur, sofralıkta Gemlik. Bahçendeki çeşidi bahçe bilgisinden görebilirsin."),
            new Topic("salamura",
                    "\\b(salamura (nasil|yapimi|tarif\\p{L}*)|zeytin nasil (kirilir|cizilir|tatlandirilir|yapilir|kurulur|basilir)|acisi nasil (gider|cikar|alinir))\\b",
                    "Siyah zeytin genelde tuzlu suda ya da kuru tuzda bekletilip acısı alınır. Yeşil zeytin kırılıp ya da çizilip suda bekletilir, suyu sık değiştirilir. Salamura kayıtlarını da ben tutarım.",
                    "Halk arasında tuzlu suya yumurta atılır, yumurta yüzerse tuz kıvamı tuttu denir. Kavanozun ağzına bir parmak zeytinyağı koymak küflenmeyi azaltır."),
            new Topic("don",
                    "\\b(don (zarari|vurur mu|vurdu|tehlikesi)|kac derecede (don|donar|zarar)|soguga (dayanikli|dayanir)|dondan (korun\\p{L}*|nasil))\\b",
                    "Zeytin eksi 7 dereceye kadar genelde dayanır; daha soğukta, hele genç fidanlarda dal ve yaprak zarar görür. Don beklenen gecelerde hava ekranından sıcaklığı takip et.",
                    "Don vurduysa hemen budama yapma; ilkbaharda sürgün çıkınca hangi dalın kuruduğu belli olur, o zaman kes."),
            new Topic("halkali",
                    "\\b(halkali leke|tavuk gozu|yaprak (dokuyor|dokulmesi|dokumu)|yapraklar (dokuluyor|sariyor|sarardi|dokuldu))\\b",
                    "Yaprak dökülmesinin en bilinen sebebi halkalı leke, yani tavuk gözü denen mantar hastalığıdır; yaprakta halka şeklinde lekeler yapar. Nemli, sık, havalanmayan bahçede artar. Kesin teşhis ve ilaç için ziraat mühendisine göster.",
                    "Budamayla ağacın içini açıp havalandırmak bu hastalığı çok azaltır. Susuzluk ve beslenme eksikliği de yaprak döktürebilir."),
            new Topic("dalkanseri",
                    "\\b(dal kanseri|zeytin vereme|verem|dallarda ur|ur (yapti|var|cikti))\\b",
                    "Zeytin dal kanseri ya da verem, dallarda ur yapan bir bakteri hastalığıdır. Budama ve dolu yaralarından girer. Budama aletlerini temizlemek, urlu dalları kesip bahçeden uzaklaştırmak gerekir.",
                    "Dolu ya da don sonrası yaralar kapanmadan hastalık bulaşabilir. Tedavisi için ziraat mühendisine danış."),
            new Topic("omur",
                    "\\b(kac yil yasar|ne kadar yasar|omru ne kadar|en yasli zeytin|olumsuz agac)\\b",
                    "Zeytin ağacı yüzlerce yıl yaşar; Anadolu'da bin yaşını geçmiş ağaçlar var. Gövdesi kurusa bile kökünden yeniden sürer, ölümsüz ağaç derler.",
                    "Zeytini dede diker, torun yer derler. Bugün diktiğin fidan senden sonraki nesillere bereket olur."),
            new Topic("fayda",
                    "\\b(zeytin\\p{L}*|yag)\\b.*\\b(faydasi|faydalari|faydali mi|neye iyi gelir|saglikli mi)\\b",
                    "Zeytinyağı Akdeniz mutfağının temelidir; tekli doymamış yağ ve doğal antioksidanlar bakımından zengindir. Ölçülü tüketilince sağlıklı beslenmenin bir parçasıdır. Hastalığın varsa diyetini doktoruna sor.",
                    "Sızma yağın faydası ışıkta ve sıcakta bekledikçe azalır; taze ve iyi saklanmış yağ en iyisidir."),
            new Topic("saklama",
                    "\\b(yag|zeytinyagi|zeytin)\\p{L}*.*\\b(nasil saklanir|nerede saklanir|saklanmali|bozulur mu|kac yil dayanir|ne kadar dayanir)\\b",
                    "Zeytinyağını serin, karanlık yerde, ağzı kapalı teneke ya da koyu cam şişede sakla. Işık, sıcak ve hava yağı bozar.",
                    "İyi saklanan yağ bir buçuk iki yıl dayanır ama en lezzetli ilk yılında. Plastik bidonda uzun bekletme."),
            new Topic("asi",
                    "\\b(asi (nasil|ne zaman|zamani)|asilama|delice)\\b",
                    "Zeytinde aşı genelde ilkbaharda, kabuk kolay kalkınca yapılır. Yabani deliceye kalem ya da göz aşısıyla kültür çeşidi aşılanır.",
                    "Deliceler köklü olduğu için aşılanınca çabuk meyveye yatar. Aşıyı ilk kez yapacaksan bir ustanın yanında öğrenmek iyi olur."),
            new Topic("dikim",
                    "\\b(dikim (araligi|mesafesi|ne zaman|zamani)|agac arasi|kac metre (ara|arayla)|fidan (ne zaman|nasil) dikilir|fidan dikimi)\\b",
                    "Geleneksel bahçelerde ağaç arası 8-10 metre, sık dikimde 5-6 metre bırakılır. Fidan dikimi en iyi sonbahar yağmurlarından sonra ya da kış sonunda yapılır.",
                    "Sulama imkânın ve çeşit bu mesafeyi değiştirir. Fidanı herek ile bağlamak rüzgârda eğilmesini önler."),
            new Topic("toprak",
                    "\\b((hangi|nasil) toprak|toprak (sever|ister)|kirecli toprak)\\b",
                    "Zeytin süzek, kireçli topraklarda iyi olur; suyun göllendiği ağır topraklarda kök çürür.",
                    "Taşlı, eğimli, başka ürünün zor yetiştiği yerlerde bile zeytin yetişir; o yüzden fakirin ağacı da derler."),
            new Topic("cicek",
                    "\\b(cicek (acar|acma|dokum\\p{L}*)|ne zaman cicek|ciceklenme|meyve tutumu|tutum (ne zaman|nedir))\\b",
                    "Zeytin Nisan-Mayıs'ta çiçek açar. Bu dönemde sert rüzgâr, kavurucu sıcak ya da çok yağmur meyve tutumunu bozabilir.",
                    "Çiçeklerin çoğu dökülür, bu normaldir; yüzde birkaçı tutsa bile bol mahsul olur."),
            new Topic("renk",
                    "\\b(yesil (zeytin|ile) siyah|siyah zeytin (nasil|neden)|yesil zeytin (nasil|neden)|ayni agactan mi)\\b",
                    "Yeşil ve siyah zeytin aynı ağaçtan çıkar. Yeşil, ham haliyle toplanır; siyah, ağaçta olgunlaşıp kararınca.",
                    "Arada bir de alacalı dönem vardır; mor, kızıl renge dönen zeytin. Bazı yörelerde onu da sofralık yaparlar."),
            new Topic("yontem",
                    "\\b((nasil|neyle) (toplanir|hasat edilir)|sirikla|silkele\\p{L}*|hasat makine\\p{L}*)\\b",
                    "Sırıkla vurmak dalları ve gelecek yılın sürgünlerini kırar; tarak ya da sarsma makinesi daha iyidir. Toplanan zeytini çuval yerine delikli kasada taşı, ezilmesin.",
                    "Yere örtü sermek zeytini topraktan korur; yerden toplanan zeytin asidi yükseltir, ayrı tut."),
            new Topic("bekletme",
                    "\\b(zeytin (kac gun|ne kadar) bek\\p{L}*|ne zaman sikilmali|fabrikaya ne zaman|sikim (zamani|ne zaman)|cuvalda bekle\\p{L}*)\\b",
                    "Toplanan zeytini 24-48 saat içinde fabrikaya götürmek gerekir; bekledikçe ısınır, asit yükselir.",
                    "Bekleyecekse serin, havadar yerde, ince tabaka halinde kasada bekletmek en iyisi."),
    };

    /** Bilgi sorusuysa konu; kendi kaydını soruyorsa ya da doz soruyorsa null. */
    static Topic find(String s) {
        if (RECORD.matcher(s).find() || DOSE.matcher(s).find()) return null;
        for (Topic t : TOPICS) if (t.trigger.matcher(s).find()) return t;
        return null;
    }

    static Topic byKey(String key) {
        for (Topic t : TOPICS) if (t.key.equals(key)) return t;
        return null;
    }

    /** Bahçe takvimi (ana sayfadaki Zeytin Takvimi ile aynı): ay 1..12. */
    static String month(int month) {
        if (month <= 3) return "Budama zamanı. Kuru, hastalıklı ve içe dönen dalları al; ana gübreyi kış yağmurları bitmeden ver. Budama artıklarını bahçede bırakma.";
        if (month <= 5) return "Çiçek zamanı. Su imkânın varsa çiçekte ve tutumda vermek iyi gelir; ilaçlamada arılara dikkat et, karar için ziraat mühendisine danış.";
        if (month == 6) return "Meyve tutumu dönemi. Su verebiliyorsan bu ay çok değerli; yabani otu temizle, yangına karşı bahçe kenarlarını temiz tut.";
        if (month <= 8) return "Çekirdek sertleşiyor. Sıcaklarda sulama önemli; zeytin sineği tuzaklarını as, haftada bir kontrol et.";
        if (month == 9) return "Yağlanma başladı. Sinek tuzaklarına bak, hasat malzemelerini hazırla: tarak, örtü, kasa.";
        return "Hasat zamanı! Zeytini ezmeden topla, kasada taşı, bekletmeden sıktır. Eleğe girdikçe bana söyle, ben yazayım.";
    }

    static final String[] JOKES = {
            "Temel zeytin bahçesine gitmiş, ağaca sormuş: Sen niye bu kadar yaşlısın? Ağaç demiş ki: Ben her sene hasat veriyorum, sen ne veriyorsun! Hehe.",
            "Zeytin neden hiç kavga etmez? Çünkü yağ gibi kayar gider! Hahaha.",
            "Adamın biri eleğe sormuş: Sen neden hep delik deşiksin? Elek demiş: Ben olmasam Double'ı Yağlık'tan nasıl ayırırdın! Hehe.",
            "Salamura kavanozuna sormuşlar: Neden hep tuzlusun? Demiş ki: Hayat bana tuz biber oldu, ben de içime attım! Hahaha.",
            "Dursun zeytin toplarken komşu sormuş: Bu sene nasıl? Dursun demiş: Ağaçlar var yılında, ben yok yılındayım, belim tutuldu! Hahaha.",
            "Temel fabrikaya zeytin götürmüş. Sormuşlar: Asit kaç çıktı? Temel demiş: Asidi bilmem ama fiyatı duyunca benim tansiyon 18 çıktı! Hehe.",
            "Öğretmen sormuş: Zeytin ağacı kaç yıl yaşar? Küçük Ali demiş: Dedem diyor ki ben dikerim torunum yer, yani hepimizden uzun yaşar hocam! Hehe.",
            "Adamın biri tezgâhçıya sormuş: Kantarın doğru mu? Tezgâhçı demiş: Doğru olmasa Zeytin söylerdi! Hahaha.",
    };

    static final String[] PROVERBS = {
            "Zeytini dede diker, torun yer. Bugün diktiğin ağaç yarının bereketidir.",
            "Emek olmadan yemek olmaz.",
            "Damlaya damlaya göl olur. Eleğe giren her kilo da sezon sonunda büyük rakam eder.",
            "Toprağa ne verirsen onu alırsın.",
            "Sabreden derviş muradına ermiş.",
            "Bir elin nesi var, iki elin sesi var. Hasatta imece gibisi yok.",
            "Ağaç yaşken eğilir.",
            "Bugünün işini yarına bırakma; hele zeytin toplamayı hiç!",
    };

    static final String[] SONGS = {
            "Zeytin dalı yeşil olur, dalında bereket dolu; elek dolsun, kasa dolsun, emeğin helal olsun! Hehe, sesim pek yok ama gönlüm var.",
            "Ege'nin rüzgârı eser, zeytin dalı salınır; erken kalkan bağa gider, bereket ona sunulur! Hehe.",
            "Kara zeytin, yeşil zeytin, ikisi de emek ister; sofrada bir tas zeytin, bin derde iyi gelir! Hahaha.",
    };

    /** {soru, kabul edilen cevap (normalize edilmiş regex), ipucu, doğru cevap}. */
    static final String[][] RIDDLES = {
            {"Delik deşiktir ama dert etmez, zeytini boy boy ayırır. Nedir?", "elek\\p{L}*", "Uygulamada numara numara kilo yazdığın ekranın adı.", "Elek"},
            {"Bin yıl yaşar, gövdesi kurusa kökünden yeniden filiz verir, dalı barışın simgesidir. Nedir?", "zeytin\\p{L}*", "Senin bahçende dizi dizi duruyor.", "Zeytin ağacı"},
            {"Kavanozda tuzlu suda bekler, acısı gidince kahvaltıya gelir. Nedir?", "salamura\\p{L}*|zeytin\\p{L}*", "Kış için hazırlanır, tuzu bol olur.", "Salamura zeytin"},
            {"Yağmurdan sonra gökte belirir, yedi renkli kuşak takar. Nedir?", "gokkusag\\p{L}*|ebemkusag\\p{L}*|alkim", "Güneşle yağmur buluşunca çıkar.", "Gökkuşağı"},
            {"Gündüz peşinden gelir, akşamüstü uzar, gece kaybolur. Nedir?", "golge\\p{L}*", "Güneşe sırtını dönünce önüne düşer.", "Gölge"},
            {"Taşın arasında ezilir, altın gibi akar, sofraya şişede gelir. Nedir?", "zeytinyag\\p{L}*|yag\\p{L}*", "Fabrikaya götürdüğün zeytinden çıkar.", "Zeytinyağı"},
            {"Ağzı var dili yok, içine ne koysan taşır, zeytini fabrikaya o götürür. Nedir?", "kasa\\p{L}*|cuval\\p{L}*|sepet\\p{L}*|kufe\\p{L}*", "Tezgâhta kilosundan dara düşülür.", "Kasa"},
    };
}
