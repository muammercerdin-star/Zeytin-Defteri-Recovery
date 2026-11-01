package com.zeytindefteri.app;

import java.util.Calendar;
import java.util.Locale;

/**
 * Asistanın sohbet kişiliği (saf, Android'siz, testli): adı Zeytin; kullanıcının adını, yaşını ve nasıl hitap
 * edileceğini sorup hatırlar ("Halil amca"), selamlaşır, hâl hatır sorar, arada güler, fıkra anlatır.
 * Para ve kayıt cevaplarına karışmaz; yalnız sohbet cümlelerini üstlenir (diğerlerinde null döner).
 * Bilgiler yalnız bu telefonda saklanır ("adımı unut" ile silinir).
 */
public final class AssistantPersona {
    public enum Stage { NONE, ASK_NAME, ASK_AGE, ASK_ADDRESS }

    /** Saklanan bilgiler (telefonda). */
    public String name = "", address = "";
    public int age;
    public Stage stage = Stage.NONE;
    /** Bir kez soruldu mu (rahatsız etmesin). */
    public boolean introduced;
    private int jokeIndex, laughIndex;
    private final java.util.Random random;

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
        String t = hour < 6 ? "Hayırlı geceler" : hour < 11 ? "Günaydın" : hour < 17 ? "İyi günler" : "İyi akşamlar";
        return withCall(t) + ", buyur dinliyorum.";
    }

    /** Kayıt bittiğinde: "Eline sağlık Halil amca." */
    public String thanks() { return withCall("Eline sağlık") + "."; }

    /** Sohbet cümlesiyse cevap; değilse null (asıl asistan cevaplar). */
    public String handle(String raw) {
        String s = ElekVoiceEntry.normalize(raw == null ? "" : raw);
        if (s.isEmpty()) return null;
        // ---- tanışma soruları sırasında verilen cevaplar
        int words = s.split(" ").length;
        // Soru sorarken kullanıcı başka bir şey söylerse (komut, soru) tanışmayı bırak, asıl asistan cevaplasın.
        if (stage != Stage.NONE && (raw.contains("?") || words > 4)) { stage = Stage.NONE; return null; }
        if (stage == Stage.ASK_NAME) {
            if (has(s, "soylemem|gerek yok|bosver|istemiyorum|gec")) { stage = Stage.NONE; return "Peki, sorun değil. Ne zaman istersen söylersin."; }
            String n = nameIn(raw);
            if (n.isEmpty()) return "Adını tam anlayamadım. Adın ne?";
            name = n; stage = Stage.ASK_AGE;
            return "Memnun oldum " + name + "! Peki yaşın kaç, sorması ayıp olmasın?";
        }
        if (stage == Stage.ASK_AGE) {
            if (has(s, "soylemem|ayip|gerek yok|bosver|gec")) { stage = Stage.ASK_ADDRESS; return "Hehe, sır kalsın o zaman. Sana nasıl hitap edeyim? " + options(); }
            int[] end = new int[1]; double v = Double.NaN;
            String[] t = s.split(" ");
            for (int i = 0; i < t.length && Double.isNaN(v); i++) v = ElekVoiceEntry.number(t, i, end);
            if (Double.isNaN(v) || v < 10 || v > 110) return "Yaşını anlayamadım. Kaç yaşındasın?";
            age = (int) v; stage = Stage.ASK_ADDRESS;
            String joke = age >= 60 ? "Maşallah, yaşın zeytin ağacı gibi, olgun ve bereketli! " : age < 30 ? "Gençsin daha, maşallah! " : "Tam iş çağı, maşallah! ";
            return joke + "Sana nasıl hitap edeyim? " + options();
        }
        if (stage == Stage.ASK_ADDRESS) {
            stage = Stage.NONE;
            if (has(s, "amca\\p{L}*")) address = "amca";
            else if (has(s, "teyze\\p{L}*")) address = "teyze";
            else if (has(s, "abi\\p{L}*|agabey\\p{L}*|abey")) address = "abi";
            else if (has(s, "abla\\p{L}*")) address = "abla";
            else if (has(s, "hoca\\p{L}*")) address = "hocam";
            else if (has(s, "bey\\p{L}*")) address = "bey";
            else if (has(s, "hanim\\p{L}*")) address = "hanım";
            else address = "";   // "sadece adımla", "fark etmez"
            return "Tamam " + call() + ", bundan sonra böyle diyeceğim. Ne lazım olursa söyle, buradayım.";
        }

        // ---- tanışma
        if (has(s, "adin ne|adin nedir|ismin ne|ismin nedir|sen kimsin|kimsin sen|adini soyle")) {
            if (name.isEmpty()) { stage = Stage.ASK_NAME; introduced = true; return "Benim adım Zeytin, senin defter arkadaşınım. Senin adın ne?"; }
            return "Benim adım Zeytin. Sen de " + call() + ", unutur muyum hiç!";
        }
        if (has(s, "adim ne|beni taniyor musun|ben kimim")) {
            if (name.isEmpty()) { stage = Stage.ASK_NAME; return "Daha tanışmadık! Adın ne senin?"; }
            return "Tanımaz olur muyum, " + call() + "!" + (age > 0 ? " " + age + " yaşındasın, maşallah." : "");
        }
        if (has(s, "benim adim|adim \\p{L}+|bana .* de$|bana .* diye seslen")) {
            String n = nameIn(raw.replaceAll("(?iu)\\b(benim|adım|adim|bana|de|diye|seslen)\\b", " "));
            if (!n.isEmpty()) {
                // "Bana Halil amca de": hitap da söylenmiş olabilir
                String a = addressIn(s);
                name = n.replaceAll("(?iu)\\s+(amca|teyze|abi|abla|hocam|bey|hanım)$", "");
                if (!a.isEmpty()) address = a;
                return "Tamam " + call() + ", aklımda!";
            }
        }
        if (has(s, "kac yasindasin|yasin kac|kac yasinda")) return "Ben daha bu sezon doğdum, ama zeytin ağaçlarından çok şey öğrendim! Hehe.";
        if (has(s, "adimi unut|beni unut|bilgilerimi sil")) { name = ""; address = ""; age = 0; return "Tamam, unuttum. İstersen yeniden tanışırız."; }

        // ---- hâl hatır, selam, gülme, fıkra, teşekkür
        if (has(s, "nasilsin|naber|ne haber|iyi misin|keyfin nasil|ne var ne yok")) {
            return pick("İyiyim, sağ ol" + suffix() + ". Sen nasılsın? Zeytinler bereketli mi bu sene?",
                    "Bomba gibiyim" + suffix() + "! Senin işler nasıl gidiyor?",
                    "Şükür iyiyim. Sen nasılsın" + suffix() + ", eleğe çok zeytin girdi mi?");
        }
        if (s.matches("(iyiyim|iyi|sukur|idare eder|fena degil|cok iyi)( .*)?")) {
            return pick("Sevindim! Hadi bakalım, ne yapalım bugün?", "Allah iyilik versin. Ne lazım, söyle.");
        }
        if (s.matches("(selam|merhaba|selamun aleykum|selamunaleykum|hey|alo)( .*)?")) {
            String n = call().isEmpty() ? "" : " " + call();
            String r = s.startsWith("selamun") ? "Aleykümselam" + n + "! Buyur, dinliyorum." : pick("Merhaba" + n + "! Buyur, ne yapalım?", "Selam" + n + "! Dinliyorum.");
            if (name.isEmpty() && !introduced) { introduced = true; stage = Stage.ASK_NAME; return r.replace(" Buyur, ne yapalım?", "").replace(" Buyur, dinliyorum.", "").replace(" Dinliyorum.", "") + " Ben Zeytin. Senin adın ne?"; }
            return r;
        }
        if (s.matches("(ha ?ha\\p{L}*|hah\\p{L}*|hi ?hi\\p{L}*|he ?he\\p{L}*|kah ?kah\\p{L}*|guldum|cok komik|komik)( .*)?") || has(s, "guldurdun|cok komik")) {
            String[] r = {"Hehe, güldürdüm mü seni? Ne güzel!", "Hahaha, gülmek de bereket getirir!", "Hehe, gülen yüzün eksik olmasın" + suffix() + "!"};
            return r[laughIndex++ % r.length];
        }
        if (has(s, "fikra|saka yap|guldur beni|espri")) {
            String[] jokes = {
                    "Temel zeytin bahçesine gitmiş, ağaca sormuş: Sen niye bu kadar yaşlısın? Ağaç demiş ki: Ben her sene hasat veriyorum, sen ne veriyorsun! Hehe.",
                    "Zeytin neden hiç kavga etmez? Çünkü yağ gibi kayar gider! Hahaha.",
                    "Adamın biri eleğe sormuş: Sen neden hep delik deşiksin? Elek demiş: Ayıklamayan ben olmasam, sen Double'ı Yağlık'tan nasıl ayırırdın! Hehe.",
                    "Salamura kavanozuna sormuşlar: Neden hep tuzlusun? Demiş ki: Hayat bana tuz biber oldu, ben de içime attım! Hahaha."};
            return jokes[jokeIndex++ % jokes.length];
        }
        // ---- hesap: "120 kilo 45 liradan kaç eder", "300 çarpı 40"
        String calc = calc(s);
        if (calc != null) return calc;
        // ---- saat / tarih
        if (has(s, "saat kac|saat kaç")) {
            Calendar c = Calendar.getInstance();
            return "Saat " + c.get(Calendar.HOUR_OF_DAY) + ":" + String.format(TR, "%02d", c.get(Calendar.MINUTE)) + suffix() + ".";
        }
        if (has(s, "bugun gunlerden ne|hangi gundeyiz|bugun ne gunu|bugunun tarihi|tarih ne")) {
            return "Bugün " + new java.text.SimpleDateFormat("d MMMM EEEE", TR).format(new java.util.Date()) + suffix() + ".";
        }
        // ---- zeytin üzerine genel bilgi (kesin rakam vermez)
        if (has(s, "zeytin ne zaman toplanir|hasat ne zaman|ne zaman toplanir")) return "Bizim yörede zeytin hasadı genelde Ekim'de başlar, Aralık'a kadar sürer. Sofralıkta rengi dönünce, yağlıkta biraz daha bekletilir. Kesin zamanı zeytinin olgunluğuna bakıp sen bilirsin" + suffix() + ".";
        if (has(s, "kac yilda meyve|kac senede meyve|ne zaman meyve verir")) return "Aşılı zeytin fidanı genelde 4-5 yılda meyveye başlar, tam verime ise 8-10 yılda girer. Bakımla, suyla çok değişir tabii.";
        if (has(s, "zeytin sinegi|sinek nedir")) return "Zeytin sineği meyvenin içine yumurta bırakır, kurt düşer, zeytin dökülür, yağ kalitesi bozulur. Bahçeye tuzak asıp sayıyı takip etmek iyi olur; ilaçlama zamanını ziraat mühendisine danış" + suffix() + ".";
        if (has(s, "var yili|yok yili|periyodisite|bir yil verip")) return "Zeytinin bir yıl bol, bir yıl az vermesine var yılı, yok yılı derler. Düzenli budama, gübre ve su bu farkı azaltır.";
        // ---- dualar, iyi dilekler
        if (has(s, "kolay gelsin|hayirli isler|bereketli olsun|hayirli olsun")) return "Sağ ol" + suffix() + ", sana da kolay gelsin. Bereketli bir sezon olsun inşallah!";
        if (has(s, "allah razi olsun|eyvallah|sag olasin")) return "Sen de var ol" + suffix() + "!";
        if (s.matches("(gunaydin|iyi gunler|iyi aksamlar|hayirli aksamlar|hayirli sabahlar)( .*)?")) return greeting(hourNow()).replace(", buyur dinliyorum.", "! Buyur, dinliyorum.");
        if (has(s, "iyi geceler|hayirli geceler")) return "İyi geceler" + suffix() + ", tatlı uykular. Yarın görüşürüz!";
        // ---- asistan hakkında
        if (has(s, "nerelisin|memleket\\p{L}*|nerede yasiyorsun|nerdesin")) return "Ben senin telefonundayım, zeytin bahçelerinin arasında! Memleketim zeytinin olduğu her yer. Hehe.";
        if (has(s, "insan misin|robot musun|yapay zeka misin|gercek misin")) return "Ben Zeytin Defteri'nin asistanıyım, insan değilim ama seni dinlerim, kayıtlarını tutarım. Gizli bir şey yok, hepsi senin telefonunda.";
        if (has(s, "seni kim yapti|kim yapti seni|seni kim yazdi|yapimcin")) return "Beni Zeytin Defteri'ni yapanlar hazırladı, zeytinciye yardım etsin diye. Sen de geliştirmeme yardım ediyorsun" + suffix() + "!";
        if (has(s, "evli misin|sevgilin var|esin var")) return "Hehe, benim tek sevdam zeytin ağaçları! Senin hanım nasıl, iyi mi?";
        if (has(s, "ne yersin|acikitin mi|yemek yer misin")) return "Ben yemem ama zeytinyağlı bir sofraya hayır demezdim herhalde! Hehe.";
        if (has(s, "uyur musun|yorulur musun|hic yorulmaz misin")) return "Ben yorulmam" + suffix() + ", gece gündüz buradayım. Sen dinlen yeter.";
        // ---- kızgınlık
        if (has(s, "aptal|salak|anlamiyorsun|beceriksiz|ise yaramaz|cok kotu|sacma")) return "Kusura bakma" + suffix() + ", daha öğreniyorum. Bir daha başka türlü söyler misin? Elinden geleni yapacağım.";
        if (has(s, "seni seviyorum|cok iyisin|aferin|helal olsun|bravo|harikasin|zekisin|akillisin|masallah|super\\p{L}*|muthis|cok guzel|sevdim seni|tesekkur ederim zeytin")) {
            return pick("Eksik olma" + suffix() + ", ben de seninle çalışmayı seviyorum!", "Sağ ol" + suffix() + ", utandırdın beni! Hehe.");
        }
        if (has(s, "yoruldum|cok yorgunum|bitkinim")) return "Kolay gelsin" + suffix() + ". Biraz çay iç, dinlen. Kayıtları ben tutarım.";
        if (has(s, "ne yapabilirsin|neler yapabilirsin|ne ise yararsin|yardim")) {
            return "Söylediğin kiloyu eleğe yazarım, ilaçlamayı, sulamayı, hasadı kaydederim. Kim sana borçlu, son sulama ne zaman, havalar nasıl, hepsini sorabilirsin" + suffix() + ".";
        }
        return null;
    }

    private int fallbackIndex;

    /** Hiçbir konuya uymayan sohbet cümlesi: soğuk "bilmiyorum" yerine dostça cevap ve yol gösterme. */
    public String fallback() {
        String[] r = {
                "Hehe, onu tam bilemedim" + suffix() + ". Ama elek, ilaçlama, sulama, satış, ne istersen söyle, ben yazarım.",
                "Bunu anlayamadım" + suffix() + ", bir daha başka türlü söyler misin?",
                "Ah, o konuda pek bilgim yok. Bahçenle, eleğinle, satışlarınla ilgili ne istersen sor" + suffix() + "."};
        return r[fallbackIndex++ % r.length];
    }

    /** "120 kilo 45 liradan kaç eder", "300 çarpı 40", "1000 bölü 8": hesap; değilse null. */
    static String calc(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(\\d+(?:[.,]\\d+)?)\\s*(?:kilo|kg)?\\s*(?:x|carpi|kere|kez)\\s*(\\d+(?:[.,]\\d+)?)").matcher(s);
        if (m.find()) return "Hesapladım: " + fmt(num(m.group(1)) * num(m.group(2))) + ".";
        m = java.util.regex.Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:kilo|kg)\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:lira|tl)(?:dan|den|tan|ten)?\\b").matcher(s);
        if (m.find() && s.matches(".*\\b(kac|eder|tutar|yapar|ne kadar|hesapla)\\b.*")) {
            double kg = num(m.group(1)), price = num(m.group(2));
            return fmt(kg) + " kilo, kilosu " + fmt(price) + " liradan " + fmt(kg * price) + " lira eder.";
        }
        m = java.util.regex.Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:bolu|bölü)\\s*(\\d+(?:[.,]\\d+)?)").matcher(s);
        if (m.find() && num(m.group(2)) != 0) return "Hesapladım: " + fmt(num(m.group(1)) / num(m.group(2))) + ".";
        m = java.util.regex.Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:arti|artı)\\s*(\\d+(?:[.,]\\d+)?)").matcher(s);
        if (m.find()) return "Hesapladım: " + fmt(num(m.group(1)) + num(m.group(2))) + ".";
        return null;
    }
    private static double num(String v) { return Double.parseDouble(v.replace(',', '.')); }
    private static String fmt(double v) {
        double r = Math.round(v * 100) / 100.0;
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(TR); nf.setMaximumFractionDigits(2);
        return nf.format(r);
    }

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

    private static String addressIn(String s) {
        if (s.matches(".*\\bamca\\b.*")) return "amca";
        if (s.matches(".*\\bteyze\\b.*")) return "teyze";
        if (s.matches(".*\\babi\\b.*")) return "abi";
        if (s.matches(".*\\babla\\b.*")) return "abla";
        if (s.matches(".*\\bhocam\\b.*")) return "hocam";
        return "";
    }

    /** "Benim adım Halil", "Halil", "Halil Yılmaz" → "Halil". */
    static String nameIn(String raw) {
        String cleaned = (raw == null ? "" : raw).replaceAll("(?iu)\\b(benim|adım|adim|ismim|ben|derler|diyorlar|de|da|evet|tabii|tabi|tamam|merhaba|selam)\\b", " ")
                .replaceAll("[^\\p{L}\\s]", " ").trim();
        String n = ElekVoiceBuyer.spokenName(cleaned);
        if (n.isEmpty()) return "";
        String first = n.split(" ")[0];
        if (first.length() < 2 || first.length() > 20) return "";
        return first;
    }

    private String pick(String... options) { return options[random.nextInt(options.length)]; }

    private static boolean has(String s, String words) { return s.matches(".*\\b(" + words + ")\\b.*"); }

    /** Saklama: "ad|yaş|hitap|tanıştı". */
    public String encode() { return name.replace("|", "") + "|" + age + "|" + address + "|" + (introduced ? 1 : 0); }

    public static AssistantPersona decode(String raw) {
        AssistantPersona p = new AssistantPersona();
        if (raw == null || raw.isEmpty()) return p;
        String[] f = raw.split("\\|", -1);
        if (f.length >= 4) {
            p.name = f[0];
            try { p.age = Integer.parseInt(f[1]); } catch (NumberFormatException ignored) { }
            p.address = f[2];
            p.introduced = "1".equals(f[3]);
        }
        return p;
    }

    public static int hourNow() { return Calendar.getInstance().get(Calendar.HOUR_OF_DAY); }
}
