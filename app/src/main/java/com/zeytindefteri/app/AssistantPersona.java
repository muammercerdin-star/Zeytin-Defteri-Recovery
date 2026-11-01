package com.zeytindefteri.app;

import java.util.Calendar;
import java.util.Locale;

/**
 * Asistanın sohbet kişiliği (saf, Android'siz, testli): adı Zeytin; kullanıcının adını, yaşını, hitabını ve
 * memleketini sorup hatırlar ("Halil amca"), selamlaşır, hâl hatır sorar, arada güler, fıkra/bilmece/mani söyler,
 * zeytin hakkında genel bilgi verir, aya göre bahçe tavsiyesi verir, hesap yapar.
 *
 * Konuşmanın akışını hatırlar: kendi sorduğu soruya ("Sen nasılsın?", "Devamını ister misin?", bilmece) verilen
 * kısa cevabı o soruya göre anlar; "neden", "devam et", "bir tane daha" son konuya göre cevaplanır.
 * Para ve kayıt cevaplarına karışmaz; yalnız sohbet cümlelerini üstlenir (diğerlerinde null döner).
 * Bilgiler yalnız bu telefonda saklanır ("adımı unut" ile silinir).
 */
public final class AssistantPersona {
    public enum Stage { NONE, ASK_NAME, ASK_AGE, ASK_ADDRESS }
    /** Zeytin'in en son sorduğu ve cevabını beklediği şey (kısa süreli hafıza). */
    enum Pending { NONE, HOW_ARE_YOU, MORE, JOKE_MORE, PROVERB_MORE, OFFER_FUN, RIDDLE, RIDDLE_MORE, TOWN, FAMILY, TALK }

    /** Saklanan bilgiler (telefonda). */
    public String name = "", address = "", town = "";
    public int age;
    public Stage stage = Stage.NONE;
    /** Bir kez soruldu mu (rahatsız etmesin). */
    public boolean introduced;

    Pending pending = Pending.NONE;
    /** Beklenen soruya verilecek genel cevap; önce cümlenin kendi konusu denenir. */
    private String deferred; private Pending deferredPending = Pending.NONE;
    private String lastTopic = "";
    private int jokeIndex, laughIndex, proverbIndex, songIndex, riddleIndex = -1, riddleMisses, fallbackIndex, statementIndex;
    private final java.util.Random random;
    /** Testte sabit tarih/saat için. */
    Calendar clock;

    private static final Locale TR = new Locale("tr", "TR");

    public AssistantPersona() { this(new java.util.Random()); }
    AssistantPersona(java.util.Random random) { this.random = random; }

    /** "Halil amca" / "Halil" / boş. */
    public String call() {
        if (name.isEmpty()) return "";
        return address.isEmpty() ? name : name + " " + address;
    }

    private String withCall(String lead) { return call().isEmpty() ? lead : lead + " " + call(); }

    /** Panel açılınca ilk söz: "İyi akşamlar Halil amca, buyur dinliyorum." */
    public String greeting(int hour) {
        pending = Pending.NONE;
        String t = hour < 6 ? "Hayırlı geceler" : hour < 11 ? "Günaydın" : hour < 17 ? "İyi günler" : "İyi akşamlar";
        return withCall(t) + ", buyur dinliyorum.";
    }

    /** Kayıt bittiğinde: "Eline sağlık Halil amca." */
    public String thanks() { pending = Pending.NONE; return withCall("Eline sağlık") + "."; }

    // ===================================================================== ANA GİRİŞ

    /** Sohbet cümlesiyse cevap; değilse null (asıl asistan cevaplar). */
    public String handle(String raw) {
        if (raw == null) return null;
        String s = ElekVoiceEntry.normalize(raw);
        if (s.isEmpty()) return null;
        // "Zeytin, nasılsın" / "hey Zeytin bana bir fıkra anlat": hitap sözü atılır.
        s = s.replaceFirst("^(hey |ey |ya |bak )?zeytin(cim|im)? (?=(sen|nasilsin|naber|bana|beni|adin|kac|bir|hadi|haydi|sence|bak|dinle|soyle|anlat|merhaba|selam|gunaydin|iyi|tesekkur)\\b)", "");
        // Meydan'a yazı, kayıt ve rakamlı iş cümleleri asıl asistanındır (hesap hariç; aşağıda).
        if (s.matches(".*\\bmeydan\\p{L}*\\b.*")) { pending = Pending.NONE; return null; }

        String r = safety(s);
        if (r != null) { pending = Pending.NONE; return r; }

        r = introduction(raw, s);
        if (r != null) return r;

        deferred = null;
        r = answerPending(raw, s);
        if (r != null) return r;
        // Beklenen soruya genel cevap ("Sağlık olsun") yalnız cümle başka bir sohbet konusuna uymuyorsa.
        String generic = deferred; Pending keep = deferredPending; deferred = null;
        r = chat(raw, s);
        if (r != null) return r;
        if (generic != null) { pending = keep; return generic; }
        return null;
    }

    // ===================================================================== GÜVENLİK

    /** Acil durum sözleri: her şeyden önce, sakin ve yardım yolunu açık tutan cevap. */
    private String safety(String s) {
        if (has(s, "olmek istiyorum|intihar|kendimi oldur\\p{L}*|yasamak istemiyorum|canima kiy\\p{L}*"))
            return "Bunu duyduğuma çok üzüldüm" + suffix() + ". Yalnız değilsin. Lütfen hemen bir yakınını ara ya da 112'yi ara, konuşacak biri olsun yanında. Ben de buradayım, dinliyorum.";
        if (has(s, "gogsum agriyor|kalbim agriyor|kalbim sikisiyor|nefes alamiyorum|nefesim daraldi|bayilacagim|felc"))
            return "Bu ciddi olabilir" + suffix() + ". Hemen 112'yi ara ya da yanındakine haber ver. Kayıtları sonra yaparız.";
        return null;
    }

    // ===================================================================== TANIŞMA

    private String introduction(String raw, String s) {
        int words = s.split(" ").length;
        if (stage != Stage.NONE && (raw.contains("?") || words > 6 || s.matches(".*\\d.*") && stage != Stage.ASK_AGE)) { stage = Stage.NONE; return null; }
        if (stage == Stage.ASK_NAME) {
            if (has(s, "soylemem|gerek yok|bosver|istemiyorum|gec")) { stage = Stage.NONE; return "Peki, sorun değil. Ne zaman istersen söylersin."; }
            String n = nameIn(raw);
            if (n.isEmpty()) return "Adını tam anlayamadım. Adın ne?";
            name = n; stage = Stage.ASK_AGE;
            String a = addressIn(s);
            if (!a.isEmpty()) address = a;
            return "Memnun oldum " + name + "! Peki yaşın kaç, sorması ayıp olmasın?";
        }
        if (stage == Stage.ASK_AGE) {
            if (has(s, "soylemem|ayip|gerek yok|bosver|gec|sir")) { stage = Stage.ASK_ADDRESS; return "Hehe, sır kalsın o zaman. Sana nasıl hitap edeyim? " + options(); }
            int[] end = new int[1]; double v = Double.NaN;
            String[] t = s.split(" ");
            for (int i = 0; i < t.length && Double.isNaN(v); i++) v = ElekVoiceEntry.number(t, i, end);
            if (Double.isNaN(v) || v < 10 || v > 110) return "Yaşını anlayamadım. Kaç yaşındasın?";
            age = (int) v;
            if (!address.isEmpty()) { stage = Stage.NONE; return ageJoke() + "Tamam " + call() + ", aklımda. Ne lazım olursa söyle."; }
            stage = Stage.ASK_ADDRESS;
            return ageJoke() + "Sana nasıl hitap edeyim? " + options();
        }
        if (stage == Stage.ASK_ADDRESS) {
            stage = Stage.NONE;
            address = addressIn(s);   // "sadece adımla", "fark etmez" → boş
            return "Tamam " + call() + ", bundan sonra böyle diyeceğim. Ne lazım olursa söyle, buradayım.";
        }

        if (has(s, "adin ne|adin nedir|ismin ne|ismin nedir|sen kimsin|kimsin sen|adini soyle")) {
            if (name.isEmpty()) { stage = Stage.ASK_NAME; introduced = true; return "Benim adım Zeytin, senin defter arkadaşınım. Senin adın ne?"; }
            return "Benim adım Zeytin. Sen de " + call() + ", unutur muyum hiç!";
        }
        if (has(s, "adim ne|beni taniyor musun|ben kimim|beni hatirliyor musun")) {
            if (name.isEmpty()) { stage = Stage.ASK_NAME; return "Daha tanışmadık! Adın ne senin?"; }
            return "Tanımaz olur muyum, " + call() + "!" + (age > 0 ? " " + age + " yaşındasın, maşallah." : "") + (town.isEmpty() ? "" : " Memleketin " + town + ".");
        }
        if (has(s, "kac yasindayim|yasim kac")) {
            if (age <= 0) { stage = Stage.ASK_AGE; return "Onu bana hiç söylemedin! Kaç yaşındasın?"; }
            return age + " yaşındasın" + suffix() + ", maşallah. Gönlün genç ama!";
        }
        if (has(s, "nereliyim|memleketim neresi|nerede yasiyorum")) {
            if (town.isEmpty()) { pending = Pending.TOWN; return "Onu daha söylemedin! Nerelisin?"; }
            return "Sen " + town + belong(town) + suffix() + ", unutmadım!";
        }
        if (has(s, "adimi unut|beni unut|bilgilerimi sil")) {
            name = ""; address = ""; age = 0; town = ""; pending = Pending.NONE;
            return "Tamam, unuttum. İstersen yeniden tanışırız.";
        }
        // "Bana bundan sonra Muammer de", "Benim adım Halil", "Beni Mehmet abi diye çağır", "Bana abi de"
        if (!s.matches(".*\\d.*") && (s.matches(".*\\b(adim|ismim)\\b.*") || s.matches(".*\\b(bana|beni)\\b.*\\b(de|diye|desene|deyin|deyiver)\\b.*"))) {
            String n = nameIn(raw, true);
            String a = addressIn(s);
            if (!n.isEmpty()) {
                name = n; address = a; introduced = true; stage = Stage.NONE; pending = Pending.NONE;
                return "Tamam " + call() + ", aklımda!";
            }
            if (!a.isEmpty()) {
                address = a;
                return name.isEmpty() ? "Tamam, " + a + " diyeceğim. Adın ne peki?" : "Tamam " + call() + ", aklımda!";
            }
        }
        if (has(s, "kac yasindasin|yasin kac|kac yasinda")) {
            String me = "Ben daha bu sezon doğdum, ama zeytin ağaçlarından çok şey öğrendim! Hehe.";
            if (age > 0) return me + " Sen de " + age + " yaşındasın, maşallah.";
            if (!name.isEmpty()) { stage = Stage.ASK_AGE; return me + " Sen kaç yaşındasın?"; }
            return me;
        }
        // "Ben Akhisarlıyım", "Memleketim Ayvalık", "Aslen Edremit'tenim"
        String tw = townIn(raw, s, false);
        if (!tw.isEmpty()) { town = tw; pending = Pending.NONE; return townReply(); }
        return null;
    }

    private String ageJoke() {
        return age >= 60 ? "Maşallah, yaşın zeytin ağacı gibi, olgun ve bereketli! " : age < 30 ? "Gençsin daha, maşallah! " : "Tam iş çağı, maşallah! ";
    }

    private String townReply() {
        return pick("Ooo, " + town + "! Selam olsun oralara" + suffix() + ". Aklımda tutarım.",
                town + " güzel yerdir, toprağı bereketli olsun" + suffix() + "! Aklımda.");
    }

    // ===================================================================== BEKLENEN CEVAPLAR

    private static final String YES = "evet|olur|tabi|tabii|tamam|hadi|haydi|anlat|soyle|isterim|elbette|neden olmasin|olsun|devam|devam et|bir tane daha|bir daha|baska|hee|he|aynen|tabiki";
    private static final String NO = "hayir|yok|istemem|istemiyorum|gerek yok|sonra|simdi degil|kalsin|bosver|yeter";

    /** Zeytin'in sorduğu soruya verilen cevap; konu değiştiyse hafıza silinir, null döner. */
    private String answerPending(String raw, String s) {
        if (pending == Pending.NONE) return null;
        Pending p = pending;
        pending = Pending.NONE;
        int words = s.split(" ").length;
        boolean yes = s.matches("(" + YES + ")( .*)?") && !s.matches(".*\\b(" + NO + ")\\b.*");
        boolean no = s.matches("(" + NO + ")( .*)?") || s.matches("(" + NO + ")");

        if (p == Pending.RIDDLE) {
            String[] rd = AssistantKnowledge.RIDDLES[riddleIndex];
            if (s.matches(".*\\b(" + rd[1] + ")\\b.*")) {
                riddleMisses = 0; pending = Pending.RIDDLE_MORE;
                return pick("Bildin! Aferin" + suffix() + ", maşallah aklına! Bir tane daha ister misin?",
                        "Doğru! " + rd[3] + ". Sen bu işi biliyorsun" + suffix() + "! Bir tane daha?");
            }
            if (has(s, "bilmiyorum|bilemedim|bilemem|pes|cevap ne|cevabi ne|soyle|sen soyle|bulamadim|ipucu")) {
                if (has(s, "ipucu") && riddleMisses == 0) { riddleMisses = 1; pending = Pending.RIDDLE; return "İpucu: " + rd[2] + " Hadi bil bakalım!"; }
                riddleMisses = 0; pending = Pending.RIDDLE_MORE;
                return "Cevap: " + rd[3] + "! Hehe. Bir tane daha ister misin?";
            }
            if (words <= 4 && !looksLikeTask(s)) {
                if (riddleMisses == 0) { riddleMisses = 1; pending = Pending.RIDDLE; return "Yok, bilemedin! İpucu: " + rd[2] + " Bir daha dene."; }
                riddleMisses = 0; pending = Pending.RIDDLE_MORE;
                return "Olmadı" + suffix() + "! Cevap: " + rd[3] + ". Bir tane daha ister misin?";
            }
            riddleMisses = 0;
            return null;
        }
        if (words > 6 || looksLikeTask(s)) return null;

        switch (p) {
            case MORE: {
                AssistantKnowledge.Topic t = AssistantKnowledge.byKey(lastTopic);
                if ((yes || has(s, "neden|niye|nasil")) && t != null) return t.more;
                if (no) return "Peki" + suffix() + ". Başka merak ettiğin olursa sor.";
                break;
            }
            case JOKE_MORE: case OFFER_FUN:
                if (has(s, "bilmece")) return riddle();
                if (has(s, "atasozu|soz")) return proverb();
                if (has(s, "sarki|turku|mani|maniler")) return song();
                if (yes || has(s, "fikra")) return joke();
                if (no) return pick("Tamam, gülmek de yorar, hehe!", "Peki" + suffix() + ", ne zaman istersen.");
                break;
            case PROVERB_MORE:
                if (yes) return proverb();
                if (no) return "Peki" + suffix() + ". Atalarımız boşuna söylememiş, değil mi?";
                break;
            case RIDDLE_MORE:
                if (yes || has(s, "bilmece")) return riddle();
                if (no) return "Tamam" + suffix() + ", güzel oynadık! Ne zaman istersen yine sorarım.";
                break;
            case HOW_ARE_YOU: {
                String mood = mood(s);
                if (mood != null) return mood;
                if (no) return "Hayırdır" + suffix() + "? Bir derdin varsa anlat, dinliyorum.";
                if (has(s, "sen|ya sen|sen nasilsin")) return "Ben iyiyim, seninle konuşunca daha da iyi oluyorum" + suffix() + "!";
                return defer("Sağlık olsun" + suffix() + ". Ne lazım olursa söyle, buradayım.", Pending.NONE);
            }
            case TOWN: {
                if (no || has(s, "soylemem|bosver")) return "Peki, sır kalsın" + suffix() + "!";
                String tw = townIn(raw, s, true);
                if (!tw.isEmpty()) { town = tw; return townReply(); }
                break;
            }
            case FAMILY:
                if (has(s, "evli degilim|bekarim|yalnizim|kimsem yok|yok")) return "Hayırlısı olsun" + suffix() + "! Ben varım, bahçe var, zeytinler var.";
                if (has(s, "hasta\\p{L}*|rahatsiz\\p{L}*|kotu|iyi degil")) return "Geçmiş olsun, Allah şifa versin. Selamımı söyle" + suffix() + ".";
                if (yes || has(s, "iyi\\p{L}*|sag ol\\p{L}*|sukur|selam\\p{L}*|hepsi iyi|cok iyi")) return "Çok sevindim! Evdekilere benden selam söyle" + suffix() + ".";
                break;
            case TALK:
                if (no) return "Peki" + suffix() + ", ama ne zaman istersen buradayım.";
                if (mood(s) != null) return mood(s);
                return defer("Anlattığın için sağ ol" + suffix() + ". Bazen içini dökmek bile iyi gelir. İstersen bir fıkra anlatayım, kafan dağılsın?", Pending.OFFER_FUN);
            default:
                break;
        }
        return null;
    }

    private String defer(String reply, Pending next) { deferred = reply; deferredPending = next; return null; }

    /** İş cümlesi gibi duruyor mu (kayıt, soru, ekran): o zaman sohbet hafızası araya girmez. */
    private static boolean looksLikeTask(String s) {
        return s.matches(".*\\d.*") || s.matches(".*\\b(numara|kilo|lira|elek|ilacla\\p{L}*|sula\\p{L}*|gubre\\p{L}*|buda\\p{L}*|hasat\\p{L}*|topla\\p{L}*|sat\\p{L}*|"
                + "masraf\\p{L}*|borc\\p{L}*|alacak\\p{L}*|hava|ruzgar|piyasa|fiyat\\p{L}*|salamura|bahce\\p{L}*|kayit\\p{L}*|ac|goster|son|tezgah|lig)\\b.*");
    }

    // ===================================================================== SOHBET

    private String chat(String raw, String s) {
        // ---- selamlaşma (önce: "iyi akşamlar" hâl cevabı sanılmasın)
        if (s.matches("(selam|merhaba|selamun aleykum|selamunaleykum|aleykum selam|hey|alo|meraba)( .*)?") && !s.matches(".*\\b(nasilsin|naber)\\b.*")) {
            String n = call().isEmpty() ? "" : " " + call();
            String r = s.startsWith("selamun") ? "Aleykümselam" + n + "! Buyur, dinliyorum." : pick("Merhaba" + n + "! Buyur, ne yapalım?", "Selam" + n + "! Dinliyorum.");
            if (name.isEmpty() && !introduced) {
                introduced = true; stage = Stage.ASK_NAME;
                return r.replace(" Buyur, ne yapalım?", "").replace(" Buyur, dinliyorum.", "").replace(" Dinliyorum.", "") + " Ben Zeytin. Senin adın ne?";
            }
            return r;
        }
        if (s.matches("(gunaydin|iyi gunler|iyi aksamlar|hayirli aksamlar|hayirli sabahlar|tunaydin)( .*)?")) return greeting(hourNow()).replace(", buyur dinliyorum.", "! Buyur, dinliyorum.");
        if (has(s, "iyi geceler|hayirli geceler|ben yatiyorum|uyuyacagim|yatmaya gidiyorum")) return "İyi geceler" + suffix() + ", tatlı uykular. Yarın görüşürüz!";
        if (has(s, "hayirli cumalar|cumaniz mubarek|cuman mubarek")) return "Cümlemizin cuması mübarek olsun" + suffix() + "!";
        if (has(s, "bayramin mubarek|bayraminiz mubarek|iyi bayramlar|bayram\\p{L}* kutlu")) return "Senin de bayramın mübarek olsun" + suffix() + "! Nice bayramlara, sağlıkla.";
        if (has(s, "hayirli ramazanlar|ramazan\\p{L}* mubarek|hayirli kandiller|kandilin mubarek")) return "Seninkisi de mübarek olsun" + suffix() + ", hayırlara vesile olsun.";
        if (has(s, "hos geldin|hosgeldin")) return "Hoş bulduk" + suffix() + "! Ne yapalım bugün?";

        // ---- hâl hatır
        if (has(s, "nasilsin|naber|ne haber|iyi misin|keyfin nasil|ne var ne yok|nasil gidiyor|ne alemdesin|nasilsiniz")) {
            pending = Pending.HOW_ARE_YOU;
            return pick("İyiyim, sağ ol" + suffix() + ". Sen nasılsın?",
                    "Bomba gibiyim" + suffix() + "! Sen nasılsın, işler nasıl gidiyor?",
                    "Şükür iyiyim. Sen nasılsın" + suffix() + "?");
        }
        String mood = mood(s);
        if (mood != null) return mood;

        // ---- sohbet devamı: "neden", "devam et", "bir tane daha", "sahi mi"
        if (s.matches("(neden|niye|niçin|nicin|nasil yani|ne demek o|biraz daha anlat|daha anlat|devam et|devamini anlat|anlat bakalim|baska)( .*)?") && !lastTopic.isEmpty()) {
            AssistantKnowledge.Topic t = AssistantKnowledge.byKey(lastTopic);
            if (t != null) { lastTopic = ""; return t.more; }
        }
        if (s.matches("(bir tane daha|bir daha|baska bir tane|baska anlat|bir daha anlat|bir tane daha anlat|bir tane daha soyle)( .*)?")) {
            if (lastTopic.equals("proverb")) return proverb();
            if (lastTopic.equals("riddle")) return riddle();
            if (lastTopic.equals("song")) return song();
            if (lastTopic.equals("joke") || jokeIndex > 0) return joke();
        }
        if (s.matches("(sahi mi|gercekten mi|oyle mi|yok artik|vay be|vay|ilginc|bilmiyordum|hadi ya|deme ya|olamaz|ciddi misin)( .*)?") && s.split(" ").length <= 4) {
            return pick("Valla öyle" + suffix() + "! Ben de ilk duyduğumda şaşırmıştım.", "Aynen öyle! Zeytinin sırrı çok, anlata anlata bitmez.", "Hehe, öyle işte" + suffix() + ". Daha neler var neler!");
        }
        if (s.matches("(hmm+|hm+|hii+|hi|haa+|anladim|anliyorum|peki|oyle|tamam tamam|iyi iyi|guzel|olur olur)") ) {
            return pick("Başka bir şey var mı" + suffix() + "?", "Dinliyorum" + suffix() + ", ne yapalım?", "Peki" + suffix() + ". Ne lazım olursa söyle.");
        }

        // ---- gülme, fıkra, bilmece, atasözü, şarkı
        if (s.matches("(ha ?ha\\p{L}*|hah\\p{L}*|hi ?hi\\p{L}*|he ?he\\p{L}*|kah ?kah\\p{L}*|guldum|cok komik|komik|ahaha\\p{L}*|kikir\\p{L}*)( .*)?") || has(s, "guldurdun|cok komik|gulmekten")) {
            String[] r = {"Hehe, güldürdüm mü seni? Ne güzel! Bir tane daha ister misin?", "Hahaha, gülmek de bereket getirir! Bir tane daha?", "Hehe, gülen yüzün eksik olmasın" + suffix() + "! Bir fıkra daha anlatayım mı?"};
            pending = Pending.JOKE_MORE;
            return r[laughIndex++ % r.length];
        }
        if (has(s, "fikra|saka yap|guldur beni|espri|komik bir sey")) return joke();
        if (has(s, "bilmece|bil bakalim|soru sor bana|beni dene")) return riddle();
        if (has(s, "atasozu|atasozleri|guzel bir soz|ozlu soz|bir soz soyle|nasihat")) return proverb();
        if (has(s, "sarki|sarki soyle|turku|mani|maniler|mani soyle|turku cagir|sesini duyalim")) return song();
        if (has(s, "sikildim|canim sikildi|cok sikildim|eglendir beni|oyun oynayalim|oyun oyna|vakit gecmiyor")) {
            pending = Pending.OFFER_FUN;
            return pick("Sıkılma" + suffix() + "! Fıkra mı anlatayım, bilmece mi sorayım?", "Hadi o zaman, sana bir bilmece sorayım mı, yoksa fıkra mı istersin?");
        }

        // ---- hesap: "120 kilo 45 liradan kaç eder", "300 çarpı 40"
        String calc = calc(s);
        if (calc != null) return calc;

        // ---- duygular ve gündelik dertler
        if (has(s, "kotuyum|uzgunum|canim sikkin|moralim bozuk|keyfim yok|dertliyim|icim sikildi|cok yalnizim|yalnizim|kimsem yok")) {
            pending = Pending.TALK;
            if (has(s, "yalniz\\p{L}*|kimsem yok")) return "Yalnız değilsin" + suffix() + ", ben buradayım. Bir de sevdiğin birini arasan, sesini duymak ikinize de iyi gelir. Anlatmak istersen dinliyorum.";
            return "Geçmiş olsun" + suffix() + ", canını sıkma. Her kışın bir baharı var. Anlatmak istersen dinlerim; işlerini de ben tutarım.";
        }
        if (has(s, "sinirliyim|cok kizgin\\p{L}*|kizdim|sinirim bozuldu|deli oldum|cildiracagim")) {
            pending = Pending.TALK;
            return "Hayırdır" + suffix() + ", kim kızdırdı seni? Derin bir nefes al. Anlat bakalım, dinliyorum.";
        }
        if (has(s, "endiseliyim|korkuyorum|tedirginim|merak ediyorum ne olacak")) {
            pending = Pending.TALK;
            return "Endişelenme" + suffix() + ", elimizden geleni yaparız, gerisi hayırlısı. Neye takıldın, anlat bakalım.";
        }
        if (has(s, "mutluyum|cok mutluyum|sevincliyim|keyfim yerinde|neseliyim|cok sevindim")) return "Maşallah, mutluluğun daim olsun" + suffix() + "! Senin keyfin yerindeyse benim de yerinde.";
        if (has(s, "hastayim|rahatsizim|basim agriyor|belim agriyor|dizlerim agriyor|ates\\p{L}* var|grip oldum|usuttum|nezle oldum")) return "Geçmiş olsun" + suffix() + "! Kendine iyi bak, gerekirse doktora görün. Bugün kayıtları ben hallederim.";
        if (has(s, "yoruldum|cok yorgunum|bitkinim|yorgunum|helak oldum|anam agladi")) return "Kolay gelsin" + suffix() + ". Biraz çay iç, dinlen. Kayıtları ben tutarım.";
        if (has(s, "uyuyamadim|uykum var|uykusuzum|uykum gelmiyor")) return "Uykusuzluk zor iş" + suffix() + ". Biraz dinlen, ılık bir şey iç. İstersen sana sakin bir mani söyleyeyim, hehe.";
        if (has(s, "aciktim|karnim acikti|cok acim")) return "Afiyet olsun şimdiden" + suffix() + "! Bir parça ekmek, biraz zeytin, zeytinyağı; bundan güzel kahvaltı mı olur!";
        if (has(s, "yagmur yagdi|yagmur yagiyor|yagis geldi|yagmur geldi")) return "Bereket" + suffix() + ", hayırlısı olsun! Zeytin suya hasretti.";
        if (has(s, "don vurdu|dolu vurdu|dolu yagdi|sel oldu|firtina vurdu|ruzgar dokmus")) return "Çok geçmiş olsun" + suffix() + ". Zararı görünce hemen budama yapma, birkaç hafta bekle; hangi dal kurudu belli olur. Ziraat mühendisine de göstermek iyi olur.";
        if (has(s, "zeytin az|rekolte dusuk|mahsul az|az cikti|verim dusuk|agaclar bos")) {
            lastTopic = "varyok";
            return "Üzülme" + suffix() + ", zeytinin var yılı yok yılı olur. Seneye bereketli olur inşallah. İstersen bölgede ortalama ne çıkmış bakalım, piyasa de.";
        }
        if (has(s, "fiyat dusuk|fiyatlar dusuk|para etmiyor|ucuz gitti|zarar ettim|kazanamadim|emegimiz bosa")) return "Haklısın" + suffix() + ", emeğin karşılığı alınmalı. Bölgedeki fiyatlara bakmak istersen piyasa de, karşılaştıralım.";
        if (has(s, "zeytin bol|bereketli|cok cikti|verim iyi|mahsul iyi|agaclar dolu")) return "Maşallah" + suffix() + ", bereketli olsun! Eleğe girdikçe söyle, ben yazayım.";
        if (s.split(" ").length <= 5 && has(s, "cay|kahve")) return "Afiyet olsun" + suffix() + "! Ben içemem ama sana bir bardak çay iyi gelir.";

        // ---- aile, özlem, yas
        if (has(s, "vefat etti|vefat\\p{L}*|kaybettik|kaybettim|rahmetli oldu|oldu gitti|cenaze\\p{L}*|topraga verdik"))
            return "Allah rahmet eylesin, mekânı cennet olsun. Başın sağ olsun" + suffix() + ". Bugün kayıtları dert etme, ben buradayım.";
        if (has(s, "ozledim|ozluyorum|burnumda tutuyor|hasretim|hasret kaldim")) {
            pending = Pending.OFFER_FUN;
            String why = has(s, "asker\\p{L}*") ? " Allah selametle tezkeresini versin." : " Allah kavuştursun.";
            return "Özlem zor iş" + suffix() + "." + why + " Bir telefon aç, sesini duy, iyi gelir. İstersen bir fıkra anlatayım, biraz kafan dağılsın?";
        }
        if (has(s, "askere gitti|askerde|asker\\p{L}* oldu")) return "Allah selametle gidip gelmesini nasip etsin" + suffix() + ". Vatan sağ olsun.";
        if (has(s, "torunum (geldi|dogdu|oldu)|torun\\p{L}* (geldi|dogdu)|nene oldum|dede oldum|babaanne oldum|anneanne oldum"))
            return "Ooo, gözün aydın" + suffix() + "! Allah analı babalı büyütsün, bahçenin yeni çırağı geldi!";
        if (has(s, "oglum|kizim|hanim\\p{L}*|esim|cocuklar\\p{L}*|torun\\p{L}*") && has(s, "geldi|gitti|evlendi|okula|askere|hasta|calisiyor|yardim"))
            return pick("Allah bağışlasın, hepsi sağlıkla olsun" + suffix() + "!", "Ne güzel, aile bereketin en büyüğü" + suffix() + "!");

        // ---- saat / tarih / aya göre iş
        if (has(s, "saat kac|saati soyle")) {
            Calendar c = now();
            return "Saat " + c.get(Calendar.HOUR_OF_DAY) + ":" + String.format(TR, "%02d", c.get(Calendar.MINUTE)) + suffix() + ".";
        }
        if (has(s, "bugun gunlerden ne|hangi gundeyiz|bugun ne gunu|bugunun tarihi|tarih ne|ayin kaci|bugun ayin kaci")) {
            return "Bugün " + new java.text.SimpleDateFormat("d MMMM EEEE", TR).format(now().getTime()) + suffix() + ".";
        }
        if (has(s, "bu ay ne yapilir|bu ay ne yapmali\\p{L}*|bu mevsim ne yapilir|simdi ne yapmali\\p{L}*|ne is yapayim|ne yapsam|bugun ne yapayim|bahcede ne yapmaliyim|tavsiyen ne|ne tavsiye edersin")) {
            String[] months = {"Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"};
            int m = now().get(Calendar.MONTH);
            return months[m] + " ayındayız" + suffix() + ". " + AssistantKnowledge.month(m + 1);
        }

        // ---- zeytin üzerine genel bilgi (kesin doz/teşhis vermez)
        AssistantKnowledge.Topic topic = AssistantKnowledge.find(s);
        if (topic != null) {
            lastTopic = topic.key;
            pending = Pending.MORE;
            return topic.answer + " Devamını ister misin?";
        }

        // ---- dualar, iyi dilekler
        if (has(s, "kolay gelsin|hayirli isler|bereketli olsun|hayirli olsun|bol kazanclar")) return "Sağ ol" + suffix() + ", sana da kolay gelsin. Bereketli bir sezon olsun inşallah!";
        if (has(s, "allah razi olsun|eyvallah|sag olasin|var ol")) return "Sen de var ol" + suffix() + "!";
        if (has(s, "dua et|bize dua|duan\\p{L}*")) return "Allah bereket versin, kazadan beladan korusun, emeğinin karşılığını bol bol versin" + suffix() + ". Amin.";
        if (has(s, "gecmis olsun")) return "Sağ ol" + suffix() + ", ben iyiyim. Sen de iyi ol yeter.";

        // ---- asistan hakkında
        if (has(s, "nerelisin|memleketin neresi|nerede yasiyorsun|nerdesin|neredesin")) {
            String me = "Ben senin telefonundayım, zeytin bahçelerinin arasında! Memleketim zeytinin olduğu her yer. Hehe.";
            if (town.isEmpty()) { pending = Pending.TOWN; return me + " Sen nerelisin?"; }
            return me + " Sen de " + town + belong(town) + ", unutmadım.";
        }
        if (has(s, "insan misin|robot musun|yapay zeka misin|gercek misin|makine misin")) return "Ben Zeytin Defteri'nin asistanıyım; insan değilim ama seni dinlerim, kayıtlarını tutarım. Gizli bir şey yok, hepsi senin telefonunda.";
        if (has(s, "seni kim yapti|kim yapti seni|seni kim yazdi|yapimcin|seni kim gelistirdi")) return "Beni Zeytin Defteri'ni yapanlar hazırladı, zeytinciye yardım etsin diye. Sen de konuştukça geliştirmeme yardım ediyorsun" + suffix() + "!";
        if (has(s, "evli misin|sevgilin var|esin var|evlenecek misin")) {
            pending = Pending.FAMILY;
            return "Hehe, benim tek sevdam zeytin ağaçları! Senin evdekiler nasıl, iyiler mi?";
        }
        if (has(s, "ne yersin|acikti mi|yemek yer misin|ne icersin")) return "Ben yemem ama zeytinyağlı bir sofraya hayır demezdim herhalde! Hehe.";
        if (has(s, "uyur musun|yorulur musun|hic yorulmaz misin|dinlenir misin")) return "Ben yorulmam" + suffix() + ", gece gündüz buradayım. Sen dinlen yeter.";
        if (has(s, "ne yapiyorsun|napiyorsun|naparsin|ne ile mesgulsun")) return "Seni dinliyorum" + suffix() + "! Bir yandan da kayıtlarına göz atıyorum. Sen ne yapıyorsun?";
        if (has(s, "orada misin|beni duyuyor musun|duydun mu|dinliyor musun|uyudun mu")) return "Buradayım" + suffix() + ", dinliyorum. Söyle bakalım.";
        if (has(s, "beni seviyor musun|beni sever misin")) return "Sevmez olur muyum" + suffix() + "! Bu kadar emek veren birini kim sevmez.";
        if (has(s, "sence|ne dusunuyorsun|fikrin ne")) {
            if (has(s, "secim|siyaset|parti|hukumet|muhalefet")) return "Siyasete girmeyelim" + suffix() + ", aramızda zeytin dalı olsun! Hehe. Bahçeyle ilgili ne düşündüğümü sor, söyleyeyim.";
            return null;
        }
        if (has(s, "secim|siyaset|parti|hukumet|muhalefet|cumhurbaskan\\p{L}*|milletvekil\\p{L}*")) return "Siyasete girmeyelim" + suffix() + ", aramızda zeytin dalı olsun! Hehe.";
        if (has(s, "mac|futbol|galatasaray|fenerbahce|besiktas|trabzonspor")) return "Ben maçtan pek anlamam" + suffix() + "; kim kazanırsa kazansın, bizim takım zeytin! Hehe.";
        if (has(s, "akilli\\p{L}* misin|zeki misin|ne kadar akillisin")) return "Her gün biraz daha öğreniyorum" + suffix() + ". Sen anlattıkça akıllanıyorum!";

        // ---- kızgınlık / iltifat
        if (has(s, "aptal|salak|anlamiyorsun|beceriksiz|ise yaramaz|cok kotusun|sacma|gerizekali|mal misin")) return "Kusura bakma" + suffix() + ", daha öğreniyorum. Bir daha başka türlü söyler misin? Elinden geleni yapacağım.";
        if (has(s, "seni seviyorum|cok iyisin|aferin|helal olsun|bravo|harikasin|zekisin|akillisin|masallah|super\\p{L}*|muthis|cok guzel|sevdim seni|tesekkur ederim zeytin|iyi ki varsin")) {
            return pick("Eksik olma" + suffix() + ", ben de seninle çalışmayı seviyorum!", "Sağ ol" + suffix() + ", utandırdın beni! Hehe.");
        }
        if (has(s, "ozur dilerim|kusura bakma|pardon")) return "Estağfurullah" + suffix() + ", ne kusuru! Devam edelim.";
        if (has(s, "ne yapabilirsin|neler yapabilirsin|ne ise yararsin|yardim|neler biliyorsun|ne bilirsin")) {
            return "Söylediğin kiloyu eleğe yazarım; ilaçlamayı, sulamayı, hasadı kaydederim. Kim sana borçlu, son sulama ne zaman, havalar nasıl, hepsini sorabilirsin. "
                    + "Bir de sohbet ederim: zeytin bilgisi, bu ay bahçede ne yapılır, fıkra, bilmece, hesap" + suffix() + ".";
        }
        return null;
    }

    /** "İyiyim", "kötüyüm" gibi hâl cevapları (soru beklenmese de). */
    private String mood(String s) {
        if (s.matches("(iyiyim|iyi|sukur|sukurler olsun|idare eder|fena degil|cok iyi|bomba gibi|bomba gibiyim|harika|harikayim|cok iyiyim|turp gibi|turp gibiyim|iyidir|iyiyiz|hamdolsun|elhamdulillah)( .*)?")
                && !s.matches(".*\\b(aksamlar|gunler|geceler|sabahlar)\\b.*") && !s.matches("iyi \\p{L}+(lar|ler)( .*)?")) {
            if (s.startsWith("idare")) return "İdare ediyorsak şükür" + suffix() + ". Allah kolaylık versin. Ne yapalım bugün?";
            return pick("Sevindim" + suffix() + "! Hadi bakalım, ne yapalım bugün?", "Allah iyilik versin. Ne lazım, söyle" + suffix() + ".", "Ne güzel" + suffix() + "! Hep iyi ol inşallah.");
        }
        if (s.matches("(kotu|kotuyum|pek iyi degilim|iyi degilim|berbat|berbatim|hic iyi degilim)( .*)?")) {
            pending = Pending.TALK;
            return "Hayırdır" + suffix() + "? Canını sıkan ne, anlat bakalım. Dinliyorum.";
        }
        return null;
    }

    private String joke() {
        lastTopic = "joke"; pending = Pending.JOKE_MORE;
        String j = AssistantKnowledge.JOKES[jokeIndex++ % AssistantKnowledge.JOKES.length];
        return j + (jokeIndex % 2 == 0 ? " Bir tane daha ister misin?" : "");
    }

    private String proverb() {
        lastTopic = "proverb"; pending = Pending.PROVERB_MORE;
        return "Atalarımız demiş ki: " + AssistantKnowledge.PROVERBS[proverbIndex++ % AssistantKnowledge.PROVERBS.length] + " Bir tane daha söyleyeyim mi?";
    }

    private String song() {
        lastTopic = "song";
        return AssistantKnowledge.SONGS[songIndex++ % AssistantKnowledge.SONGS.length];
    }

    private String riddle() {
        lastTopic = "riddle"; pending = Pending.RIDDLE; riddleMisses = 0;
        riddleIndex = (riddleIndex + 1) % AssistantKnowledge.RIDDLES.length;
        return "Bil bakalım" + suffix() + ": " + AssistantKnowledge.RIDDLES[riddleIndex][0];
    }

    // ===================================================================== YEDEK CEVAP

    /** Hiçbir konuya uymayan sohbet cümlesi: soğuk "bilmiyorum" yerine dostça cevap ve yol gösterme. */
    public String fallback() {
        String[] r = {
                "Hehe, onu tam bilemedim" + suffix() + ". Ama elek, ilaçlama, sulama, satış, ne istersen söyle, ben yazarım.",
                "Bunu anlayamadım" + suffix() + ", bir daha başka türlü söyler misin?",
                "Ah, o konuda pek bilgim yok. Bahçenle, eleğinle, satışlarınla ilgili ne istersen sor" + suffix() + "."};
        return r[fallbackIndex++ % r.length];
    }

    /** Soru ise "bilemedim"; anlatılan bir şeyse ilgi gösterip konuşmayı sürdürür ("Hıı, öyle mi? Anlat bakalım"). */
    public String fallback(String raw) {
        String s = ElekVoiceEntry.normalize(raw == null ? "" : raw);
        boolean question = raw != null && raw.contains("?")
                || s.matches(".*(^|\\s)(ne|neden|niye|nasil|kim|kimi|hangi|nerede|nereye|kac|mi|mu|misin|musun|midir|mudur)(\\s|$).*");
        if (question || s.isEmpty()) return fallback();
        String[] r = {
                "Hıı, öyle mi" + suffix() + "? Anlat bakalım, dinliyorum.",
                "Anladım" + suffix() + ". Başka ne var ne yok?",
                "Vay, öyle mi! Bunu not edemem ama seni dinlemek güzel" + suffix() + ". Bahçeyle ilgili bir şey olursa söyle, yazarım."};
        return r[statementIndex++ % r.length];
    }

    // ===================================================================== HESAP

    /** "120 kilo 45 liradan kaç eder", "300 çarpı 40", "100 artı 200 artı 300", "1000 bölü 8": hesap; değilse null. */
    static String calc(String s) {
        // Sayılar ElekVoiceEntry.number ile okunur: "50.000" binlik ayraçlı (50 bin), "12,5" ondalık, "elli bin" yazıyla.
        String[] t = s.split(" ");
        java.util.List<Double> nums = new java.util.ArrayList<>();
        java.util.List<String> after = new java.util.ArrayList<>();
        java.util.List<Integer> at = new java.util.ArrayList<>(), endAt = new java.util.ArrayList<>();
        for (int i = 0; i < t.length; i++) {
            int[] end = new int[1];
            double v = ElekVoiceEntry.number(t, i, end);
            if (Double.isNaN(v)) continue;
            nums.add(v); at.add(i); endAt.add(end[0]); after.add(end[0] < t.length ? t[end[0]] : "");
            i = end[0] - 1;
        }
        if (nums.size() < 2 || s.matches(".*\\b(numara|no|double|duble|yaglik|kasa|elek)\\b.*")) return null;
        // İşleçler: ardışık iki sayı arasındaki sözden
        java.util.List<String> ops = new java.util.ArrayList<>();
        for (int k = 0; k + 1 < nums.size(); k++) {
            String between = String.join(" ", java.util.Arrays.copyOfRange(t, Math.min(t.length, endAt.get(k)), at.get(k + 1)));
            String op = opOf(between);
            if (op.isEmpty()) break;
            ops.add(op);
        }
        if (!ops.isEmpty()) {
            java.util.List<Double> v = new java.util.ArrayList<>(nums.subList(0, ops.size() + 1));
            java.util.List<String> o = new java.util.ArrayList<>(ops);
            // önce çarpma/bölme, sonra toplama/çıkarma
            for (int k = 0; k < o.size(); ) {
                if (o.get(k).equals("*") || o.get(k).equals("/")) {
                    if (o.get(k).equals("/") && v.get(k + 1) == 0) return "Sıfıra bölemem, hehe!";
                    double r = o.get(k).equals("*") ? v.get(k) * v.get(k + 1) : v.get(k) / v.get(k + 1);
                    v.set(k, r); v.remove(k + 1); o.remove(k);
                } else k++;
            }
            double r = v.get(0);
            for (int k = 0; k < o.size(); k++) r = o.get(k).equals("+") ? r + v.get(k + 1) : r - v.get(k + 1);
            StringBuilder said = new StringBuilder(fmt(nums.get(0)));
            for (int k = 0; k < ops.size(); k++) said.append(word(ops.get(k))).append(fmt(nums.get(k + 1)));
            return "Hesapladım: " + said + ", " + fmt(r) + " eder.";
        }
        double a = nums.get(0), b = nums.get(1);
        String between = String.join(" ", java.util.Arrays.copyOfRange(t, Math.min(t.length, at.get(0) + 1), at.get(1)));
        // "120 kilo 45 liradan kaç eder"
        if (after.get(0).matches("(kilo\\p{L}*|kg)") && after.get(1).matches("(lira\\p{L}*|tl\\p{L}*)")
                && s.matches(".*\\b(kac|eder|tutar|yapar|ne kadar|hesapla\\p{L}*)\\b.*")) {
            return fmt(a) + " kilo, kilosu " + fmt(b) + " liradan " + fmt(a * b) + " lira eder.";
        }
        // "5000'in yüzde 10'u"
        if (s.matches(".*\\byuzde\\b.*") && s.matches(".*\\b(kac|eder|ne kadar|hesapla\\p{L}*|u|i|si|su)\\b.*")) {
            boolean pctFirst = between.isEmpty() && t[Math.max(0, at.get(0) - 1)].equals("yuzde");
            double pct = pctFirst ? a : b, base = pctFirst ? b : a;
            return fmt(base) + " liranın yüzde " + fmt(pct) + "'si " + fmt(base * pct / 100) + " eder.";
        }
        return null;
    }
    /** İki sayı arasında yalnız işleç sözü olmalı ("40 kilo daha 20 kilo" elek cümlesidir, hesap değil). */
    private static String opOf(String between) {
        return between.matches("x|carpi|kere|kez|ile carp\\p{L}*|carp\\p{L}*") ? "*"
                : between.matches("bolu|bol|bolunurse|bolersen") ? "/"
                : between.matches("arti|ile topla\\p{L}*|daha") ? "+"
                : between.matches("eksi|cikar\\p{L}*|cikarirsan") ? "-" : "";
    }
    private static String word(String op) {
        return op.equals("*") ? " çarpı " : op.equals("/") ? " bölü " : op.equals("+") ? " artı " : " eksi ";
    }
    private static String fmt(double v) {
        double r = Math.round(v * 100) / 100.0;
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(TR); nf.setMaximumFractionDigits(2);
        return nf.format(r);
    }

    // ===================================================================== YARDIMCILAR

    /** Tanışmadıysa, bir cevaptan sonra bir kez adını sor (rahatsız etmeden). */
    public String introduceOnce() {
        if (!name.isEmpty() || introduced) return "";
        introduced = true; stage = Stage.ASK_NAME;
        return " Bu arada daha tanışmadık, ben Zeytin. Senin adın ne?";
    }

    private String options() {
        if (age >= 45) return "Amca mı diyeyim, teyze mi, yoksa sadece adınla mı?";
        if (age > 0 && age < 30) return "Abi mi diyeyim, abla mı, yoksa sadece adınla mı?";
        return "Abi mi, abla mı, amca mı, teyze mi, yoksa sadece adınla mı?";
    }

    private String suffix() { return call().isEmpty() ? "" : " " + call(); }

    private Calendar now() { return clock != null ? clock : Calendar.getInstance(); }

    private static final String ADDRESS_WORDS = "amca|amcan|teyze|abi|abey|agabey|abla|hocam|hoca|bey|hanim|usta|reis|dayi|emmi|hala|enistem|yenge";

    private static String addressIn(String s) {
        if (s.matches(".*\\b(amca|amcan|emmi)\\b.*")) return "amca";
        if (s.matches(".*\\bteyze\\b.*")) return "teyze";
        if (s.matches(".*\\b(abi|abey|agabey)\\b.*")) return "abi";
        if (s.matches(".*\\babla\\b.*")) return "abla";
        if (s.matches(".*\\b(hocam|hoca)\\b.*")) return "hocam";
        if (s.matches(".*\\bdayi\\b.*")) return "dayı";
        if (s.matches(".*\\busta\\b.*")) return "usta";
        if (s.matches(".*\\breis\\b.*")) return "reis";
        if (s.matches(".*\\bbey\\b.*")) return "bey";
        if (s.matches(".*\\bhanim\\b.*")) return "hanım";
        return "";
    }

    /** İsim olamayacak sözler (ekranda "Tamam Bundan abi" hatası buradan çıkmıştı). */
    private static final String NOT_NAME = "bundan|sonra|artik|lutfen|bana|beni|benim|hep|simdi|boyle|oyle|sen|ben|bir|de|da|diye|adim|ismim|adimi|"
            + "evet|tamam|tabii|tabi|ki|ya|yok|hayir|soyle|ne|kim|zeytin|ama|iste|hani|sadece|yalnizca|seslen|cagir|cagirsin|hitap|et|ederim|olsun|"
            + "efendi|kardes|kardesim|merhaba|selam|derler|diyorlar|dersin|desene|deyin|deyiver|demen|ad|isim|olarak|gibi|o|bu|su|cok|hos|memnun|oldum|"
            + "bundan boyle|hep boyle|mesela|yani|iyi|guzel|abicim|benimki|adimiz|soyadim|kucuk|buyuk|seni|sana|onu|bunu|her|zaman|artik|lutfen|nolur|"
            + "bey|hanim|" + ADDRESS_WORDS;

    /**
     * "Benim adım Halil", "Halil", "Bana bundan sonra Muammer de", "Beni Mehmet abi diye çağır" → "Halil"/"Muammer"/"Mehmet".
     * Sırası: "adım/ismim" sonrası; yoksa "de/diye" öncesi; yoksa ilk isim olabilecek söz.
     */
    static String nameIn(String raw) { return nameIn(raw, false); }

    /** strict: yalnız "adım X" ya da "X de" kalıbından (cümlenin rastgele bir sözü ad sanılmasın). */
    static String nameIn(String raw, boolean strict) {
        String[] tok = (raw == null ? "" : raw).replaceAll("['’]\\p{L}*", " ").split("[^\\p{L}]+");
        java.util.List<String> raws = new java.util.ArrayList<>(), norms = new java.util.ArrayList<>();
        for (String w : tok) if (!w.isEmpty()) { raws.add(w); norms.add(ElekVoiceEntry.normalize(w)); }
        int pick = -1;
        for (int i = 0; i < norms.size() && pick < 0; i++) {
            if (norms.get(i).matches("adim|ismim|adimdir|ismimdir")) {
                for (int j = i + 1; j < norms.size(); j++) if (nameLike(norms.get(j))) { pick = j; break; }
            }
        }
        if (pick < 0) {
            for (int i = norms.size() - 1; i >= 0 && pick < 0; i--) {
                if (norms.get(i).matches("de|diye|desene|deyin|deyiver|dersin|diyebilirsin")) {
                    for (int j = i - 1; j >= 0; j--) {
                        if (nameLike(norms.get(j))) { pick = j; break; }
                        if (!norms.get(j).matches(ADDRESS_WORDS)) break;   // "Mehmet abi de": hitabı atla, adı al
                    }
                }
            }
        }
        if (pick < 0 && !strict) for (int i = 0; i < norms.size(); i++) if (nameLike(norms.get(i))) { pick = i; break; }
        if (pick < 0) return "";
        String w = raws.get(pick).toLowerCase(TR);
        return w.substring(0, 1).toUpperCase(TR) + w.substring(1);
    }

    private static boolean nameLike(String n) {
        if (n.length() < 2 || n.length() > 20 || n.matches(NOT_NAME)) return false;
        int[] end = new int[1];
        return Double.isNaN(ElekVoiceEntry.number(new String[]{n}, 0, end));
    }

    /** "Ben Akhisarlıyım", "Memleketim Ayvalık", "Aslen Edremit'tenim", "Gömeç köyündenim"; soru beklenirken yalnız "Ayvalık". */
    static String townIn(String raw, String s, boolean expected) {
        String[] tok = (raw == null ? "" : raw).split("[^\\p{L}']+");
        java.util.List<String> raws = new java.util.ArrayList<>();
        for (String w : tok) if (!w.isEmpty()) raws.add(w);
        if (raws.isEmpty() || raws.size() > 5) return "";
        for (int i = 0; i < raws.size(); i++) {
            String w = raws.get(i), n = ElekVoiceEntry.normalize(w.replace("'", ""));
            // "Akhisarlıyım"
            if (n.matches("\\p{L}{3,}l[iu]y[iu]m") && !n.matches("(iyi|deli|bel|hali|kul|evli|bekar|emekli|hasta)\\p{L}*")) return cap(w.replace("'", "").substring(0, w.replace("'", "").length() - 5));
            // "Edremit'tenim", "Ayvalık'tanım"
            if (w.contains("'") && ElekVoiceEntry.normalize(w.substring(w.indexOf('\'') + 1)).matches("(dan|den|tan|ten)[iu]m")) return cap(w.substring(0, w.indexOf('\'')));
            if (n.matches("(memleketim|aslen|koyum|kasabam)") && i + 1 < raws.size()) return cap(raws.get(i + 1).replaceAll("'.*", ""));
            if (n.matches("koyunden(im)?|koyluyum") && i > 0) return cap(raws.get(i - 1).replaceAll("'.*", "")) + " köyü";
        }
        if (expected) {
            for (String w : raws) {
                String n = ElekVoiceEntry.normalize(w.replaceAll("'.*", ""));
                if (n.length() >= 3 && !n.matches(NOT_NAME + "|aslen|memleketim|koy\\p{L}*|buralar\\p{L}*|burali\\p{L}*|buradan\\p{L}*")) return cap(w.replaceAll("'.*", ""));
            }
        }
        return "";
    }

    private static String cap(String w) {
        String l = w.toLowerCase(TR);
        return l.isEmpty() ? "" : l.substring(0, 1).toUpperCase(TR) + l.substring(1);
    }

    /** "Akhisar" → "lısın", "Edremit" → "lisin": Türkçe ünlü uyumuyla "Sen Akhisarlısın". */
    static String belong(String town) {
        String t = town.toLowerCase(TR);
        char v = 'e';
        for (int i = t.length() - 1; i >= 0; i--) if ("aeıioöuü".indexOf(t.charAt(i)) >= 0) { v = t.charAt(i); break; }
        if (town.endsWith(" köyü")) return "nden";
        return v == 'a' || v == 'ı' ? "lısın" : v == 'e' || v == 'i' ? "lisin" : v == 'o' || v == 'u' ? "lusun" : "lüsün";
    }

    private String pick(String... options) { return options[random.nextInt(options.length)]; }

    private static boolean has(String s, String words) { return s.matches(".*\\b(" + words + ")\\b.*"); }

    /** Saklama: "ad|yaş|hitap|tanıştı|memleket". */
    public String encode() {
        return name.replace("|", "") + "|" + age + "|" + address + "|" + (introduced ? 1 : 0) + "|" + town.replace("|", "");
    }

    public static AssistantPersona decode(String raw) {
        AssistantPersona p = new AssistantPersona();
        if (raw == null || raw.isEmpty()) return p;
        String[] f = raw.split("\\|", -1);
        if (f.length >= 4) {
            p.name = f[0];
            try { p.age = Integer.parseInt(f[1]); } catch (NumberFormatException ignored) { }
            p.address = f[2];
            p.introduced = "1".equals(f[3]);
            if (f.length >= 5) p.town = f[4];
        }
        return p;
    }

    public static int hourNow() { return Calendar.getInstance().get(Calendar.HOUR_OF_DAY); }
}
