package com.zeytindefteri.app;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asistan panelinin metin kuralları (tuval: "Sesli asistan · öneri"). Saf Java; masaüstünde test edilir
 * (tests/assistant_panel). Cevabın içeriğini değiştirmez, yalnızca tarihleri okunur biçime çevirir ve
 * panelde gösterilecek kaynak etiketi ile hazır soruları seçer.
 */
public final class ZeytinAssistantText {
    private ZeytinAssistantText() {}

    private static final String[] MONTHS = {"Ocak","Şubat","Mart","Nisan","Mayıs","Haziran","Temmuz",
            "Ağustos","Eylül","Ekim","Kasım","Aralık"};
    private static final Pattern ISO = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");
    private static final Pattern DOTTED = Pattern.compile("\\b(\\d{1,2})[./](\\d{1,2})[./](\\d{4})\\b");
    private static final TimeZone ISTANBUL = TimeZone.getTimeZone("Europe/Istanbul");

    /**
     * "2026-10-01" ve "01.10.2026" biçimindeki tarihleri "1 Ekim (16 gün önce)", "dün (16 Ekim)",
     * "bugün", "yarın (18 Ekim)" gibi okunur hâle getirir. Başka sayılara (kg, TL, %) dokunmaz.
     * Geçersiz tarihler olduğu gibi bırakılır. Ekranda ve sesli okumada aynı metin kullanılır.
     */
    public static String humanDates(String text, long nowMillis) {
        if (text == null || text.isEmpty()) return text == null ? "" : text;
        String out = replace(text, ISO, nowMillis, true);
        return replace(out, DOTTED, nowMillis, false);
    }

    private static String replace(String text, Pattern pattern, long now, boolean iso) {
        Matcher m = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int y, mo, d;
            if (iso) { y = Integer.parseInt(m.group(1)); mo = Integer.parseInt(m.group(2)); d = Integer.parseInt(m.group(3)); }
            else { d = Integer.parseInt(m.group(1)); mo = Integer.parseInt(m.group(2)); y = Integer.parseInt(m.group(3)); }
            String human = human(y, mo, d, now);
            m.appendReplacement(sb, Matcher.quoteReplacement(human == null ? m.group() : human));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** null: geçersiz tarih (ör. 31 Şubat). */
    static String human(int year, int month, int day, long now) {
        if (month < 1 || month > 12 || day < 1 || day > 31) return null;
        Calendar date = Calendar.getInstance(ISTANBUL);
        date.setLenient(false);
        date.clear();
        date.set(year, month - 1, day);
        try { date.getTimeInMillis(); } catch (IllegalArgumentException invalid) { return null; }
        Calendar today = Calendar.getInstance(ISTANBUL);
        today.setTimeInMillis(now);
        int thisYear = today.get(Calendar.YEAR);
        Calendar midnight = Calendar.getInstance(ISTANBUL);
        midnight.clear();
        midnight.set(thisYear, today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH));
        long days = Math.round((date.getTimeInMillis() - midnight.getTimeInMillis()) / 86400000d);
        String plain = day + " " + MONTHS[month - 1] + (year == thisYear ? "" : " " + year);
        if (days == 0) return "bugün";
        if (days == -1) return "dün (" + plain + ")";
        if (days == 1) return "yarın (" + plain + ")";
        if (days < -1 && days >= -60) return plain + " (" + (-days) + " gün önce)";
        return plain;
    }

    /** Cevap kartının üstündeki kaynak satırı: cevabın nereden geldiği. */
    public static String source(ZeytinAssistantCommand.Kind kind, boolean allGardens, String gardenName) {
        Locale tr = new Locale("tr", "TR");
        String garden = gardenName == null || gardenName.trim().isEmpty() ? "" : gardenName.trim().toUpperCase(tr);
        switch (kind) {
            case WEATHER: return garden.isEmpty() ? "HAVA TAHMİNİ" : "HAVA TAHMİNİ · " + garden;
            case TODAY: return garden.isEmpty() ? "KAYITLARIN VE HAVA TAHMİNİ" : "KAYITLARIN VE HAVA · " + garden;
            case MARKET: return "KENDİ SATIŞ KAYITLARIN";
            case NAVIGATION: return "EKRAN";
            case UNKNOWN: return "ZEYTİN ASİSTANI";
            case COMPARISON: return "KAYITLARINDAN · KARŞILAŞTIRMA";
            default:
                if (allGardens) return "KAYITLARINDAN · TÜM BAHÇELER";
                return garden.isEmpty() ? "KAYITLARINDAN" : "KAYITLARINDAN · " + garden;
        }
    }

    /**
     * Anlaşılmayan soruya verilecek cevap. Selamlaşma ve teşekküre kısa karşılık; kayıt silme/değiştirme ve
     * ilaç dozu / hastalık teşhisi isteklerine neden yapılmadığını açıkça söyler (asistan yalnızca okur ve
     * tarımsal reçete vermez). Diğer her şey için genel cevap.
     */
    public static String unknownReply(String raw) {
        String special = special(raw);
        return special != null ? special
                : "Bu soruyu henüz bilmiyorum. Aşağıdaki örneklerden birini deneyebilir ya da “Yaz”a dokunup konulara göz atabilirsin.";
    }

    /**
     * Selamlaşma, teşekkür, kayıt silme/değiştirme ve ilaç dozu / teşhis için sabit cevap; başka her şeyde null.
     * Panel bunları yakın soru önerilerinden önce kullanır: "kaç gram ilaç" sorusuna ilaçlama sorusu önerilmez.
     */
    public static String special(String raw) {
        String s = ZeytinAssistantCommand.normalize(raw);
        if (s.matches("(merhaba|selam|selamlar|selamun aleykum|gunaydin|iyi gunler|iyi aksamlar|hey|alo)( .*)?"))
            return "Merhaba! Kayıtların, hava ve piyasa hakkında sorabilirsin. Örneğin “Son sulama ne zaman?”";
        if (s.matches(".*\\b(tesekkur\\w*|sagol\\w*|eyvallah|eline saglik)\\b.*"))
            return "Rica ederim. Başka bir sorun olursa buradayım.";
        if (s.matches(".*\\b(sil|silsene|kaydet|guncelle|degistir|duzelt)\\b.*"))
            return "Asistan kayıtları silmez ya da değiştirmez. Bunu ilgili ekrandan yapabilirsin; örneğin “Sulama aç” de.";
        if (s.matches(".*\\b(doz|dozu|teshis|hasta|hastalik\\w*|kac litre|kac gram|ne kadar ilac|hangi ilac)\\b.*") || s.contains("kac litre") || s.contains("kac gram"))
            return "İlaç dozu ya da hastalık teşhisi veremem. Ürün etiketine uy ve bir ziraat mühendisine ya da ilçe tarım müdürlüğüne danış.";
        return null;
    }

    /** Yazarak sorma ekranındaki örnek sorular, konulara göre. */
    public static final String[][] EXAMPLES = {
            {"SESLE ELEK GİR", "3 numara 120 kilo sattım", "Double 20, 1 numara 90 kilo"},
            {"BAHÇE KAYITLARI", "Son sulama ne zaman?", "Son ilaçlama ne zaman?", "Hangi bahçe daha çok verdi?"},
            {"PARA VE SATIŞ", "Bu sezon masrafım kaç?", "Toplam satışım ne?"},
            {"HAVA VE EKRANLAR", "Yarın rüzgâr kaç?", "Elek aç", "Salamura stoğum"},
    };

    private static final String[] FOLLOW_UPS = {
            "Son sulama ne zaman?", "Son gübre ne zaman?", "Son ilaçlama ne zaman?", "Bu sezon masrafım kaç?",
            "Toplam satışım ne?", "Yarın yağmur var mı?", "Bugün bahçede ne var?", "Salamura stoğum",
    };

    /** Cevaptan sonra önerilen en fazla üç soru; sorulan soruyu tekrar önermez. Sıra sabittir. */
    public static List<String> followUps(ZeytinAssistantCommand.Kind kind, String asked) {
        String askedKey = ZeytinAssistantCommand.normalize(asked);
        List<String> out = new ArrayList<>();
        for (String q : FOLLOW_UPS) {
            if (out.size() == 3) break;
            String key = ZeytinAssistantCommand.normalize(q);
            if (key.equals(askedKey)) continue;
            ZeytinAssistantCommand.Kind qk = ZeytinAssistantCommand.parse(q).kind;
            if (qk == kind && kind != ZeytinAssistantCommand.Kind.UNKNOWN) continue; // aynı konuyu tekrar önermez
            out.add(q);
        }
        return out;
    }
}
