package com.zeytindefteri.app;

import java.util.*;
import com.zeytindefteri.app.ZeytinAssistantCommand.*;
import com.zeytindefteri.app.ZeytinAssistantHints.*;

/**
 * Asistanın anlama katmanı (masaüstü testi): yarım anlama önerileri, konu kartları, Anladığım etiketleri ve
 * dönem değiştirme. Android derlemesi ya da cihaz testi değildir; panelin görünümü burada denenmez.
 */
public final class HintsTest {
    static int n = 0; static List<String> fails = new ArrayList<>();
    static void check(boolean ok, String msg) { n++; if (!ok) fails.add(msg); }

    static List<Garden> gardens() {
        Garden a = new Garden(1, "Girelli"), b = new Garden(2, "Afşar"), c = new Garden(3, "Köprübaşı Bahçesi");
        return Arrays.asList(a, b, c);
    }
    static final Set<Topic> ALL = EnumSet.allOf(Topic.class);

    public static void main(String[] args) {
        List<Garden> gs = gardens();

        // 1) Üretilen her öneri: bugünkü çözümleyicide beklenen türe düşer, bahçe ve dönem korunur.
        String[] periods = {"", "bugun", "dun", "bu hafta", "gecen hafta", "bu ay", "gecen ay"};
        String[] names = {"", "Girelli", "Afşar", "Köprübaşı Bahçesi"};
        for (Topic t : Topic.values()) for (Template tpl : ZeytinAssistantHints.templates(t)) for (String p : periods) for (String g : names) {
            String q = ZeytinAssistantHints.compose(tpl, p, g);
            ZeytinAssistantCommand c = ZeytinAssistantCommand.parse(q);
            check(c.kind == tpl.kind, "tür: " + q + " → " + c.kind + " (beklenen " + tpl.kind + ")");
            if (tpl.period) check(c.period.name.equals(p), "dönem: " + q + " → '" + c.period.name + "'");
            Resolution r = ZeytinAssistantCommand.resolve(q, gs, 1, c.kind == Kind.MARKET);
            if (tpl.garden && !g.isEmpty()) check(r.explicit && r.garden != null && r.garden.name.equals(g), "bahçe: " + q + " → " + (r.garden == null ? r.error : r.garden.name));
            else check(r.error.isEmpty() && r.garden != null, "aktif bahçe: " + q + " → " + r.error);
        }
        for (Topic t : Topic.values()) {
            check(ZeytinAssistantHints.suggestions(Collections.singletonList(t), "", "Girelli").size() == 3, "3 öneri yok: " + t);
            check(ZeytinAssistantHints.topic(t, ALL).questions.size() == 3, "konu kartı 3 soru vermiyor: " + t);
            if (t.needsRecords()) check(ZeytinAssistantCommand.parse(t.open).kind == Kind.NAVIGATION, "kayıt ekleme komutu ekran açmıyor: " + t.open);
        }

        // 2) Kalıba uymayan gerçekçi sorular: hepsi bugün UNKNOWN, yeni katman doğru konuya düşürür.
        Object[][] unknown = {
                {"Girelli’ye suyu en son ne vakit verdik ya", Topic.IRRIGATION},
                {"Damlamayı en son ne zaman çalıştırdık", Topic.IRRIGATION},
                {"Zirai mücadele ne zaman yaptık", Topic.CARE},
                {"Bu yıl rekolte nasıl", Topic.HARVEST},
                {"Kaç kasa toplanmış", Topic.HARVEST},
                {"Zarar ettim mi", Topic.MONEY},
                {"Kârım ne oldu", Topic.MONEY},
                {"Tüccar ne veriyor", Topic.MARKET},
                {"Dışarısı soğuk mu", Topic.WEATHER},
                {"Fırtına çıkacak mı", Topic.WEATHER},
                {"Damlaya ne kadar para verdik", Topic.IRRIGATION},
        };
        for (Object[] row : unknown) {
            String q = (String) row[0];
            check(ZeytinAssistantCommand.parse(q).kind == Kind.UNKNOWN, "örnek artık UNKNOWN değil (örneği değiştir): " + q + " → " + ZeytinAssistantCommand.parse(q).kind);
            check(ZeytinAssistantText.special(q) == null, "özel cevaba düştü: " + q);
            Resolution r = ZeytinAssistantCommand.resolve(q, gs, 1, false);
            String garden = r.explicit && r.garden != null ? r.garden.name : "";
            Hint h = ZeytinAssistantHints.hint(q, garden, ALL);
            check(!h.cards && !h.topics.isEmpty() && h.topics.get(0) == row[1], "konu: " + q + " → " + h.topics);
            check(!h.questions.isEmpty() && h.questions.size() <= 3, "öneri sayısı: " + q + " → " + h.questions.size());
            for (String s : h.questions) check(ZeytinAssistantCommand.parse(s).kind != Kind.UNKNOWN, "öneri de anlaşılmıyor: " + s);
        }
        Hint girelli = ZeytinAssistantHints.hint("Girelli’ye suyu en son ne vakit verdik ya", "Girelli", ALL);
        check(girelli.message.equals("Sorunda “suyu” ve “Girelli” geçiyor. Sulamayı soruyorsan birine dokun:"), "mesaj: " + girelli.message);
        check(girelli.questions.equals(Arrays.asList("Girelli için son sulama ne zaman?", "Bu sezon Girelli için kaç kez sulama yaptım?", "Bu sezon Girelli için sulama masrafı ne kadar?")), "öneriler: " + girelli.questions);
        Hint month = ZeytinAssistantHints.hint("Bu ay damlaya kaç para gitti", "", ALL);
        check(month.questions.contains("Bu ay kaç kez sulama yaptım?") || month.questions.contains("Bu ay masrafım kaç?"), "dönem önerilere taşınmadı: " + month.questions);
        Hint two = ZeytinAssistantHints.hint("Damlaya ne kadar para verdik", "", ALL);
        check(two.topics.equals(Arrays.asList(Topic.IRRIGATION, Topic.MONEY)) && two.questions.size() == 3
                && two.questions.get(0).equals("Son sulama ne zaman?") && two.questions.get(1).equals("Bu sezon masrafım kaç?"), "iki konu: " + two.topics + " " + two.questions);
        check(two.message.startsWith("Sorunda “Damlaya” ve “para” geçiyor. Şunlardan"), "iki konu mesajı: " + two.message);

        // 3) Hiç anlaşılmayan ya da "Ne sorabilirim?": konu kartları.
        for (String q : new String[]{"Traktörün radyosunu aç", "Ne sorabilirim?", "Neler sorabilirim", "Neler biliyorsun", "Yardım", "Cebimde ne kaldı"}) {
            Hint h = ZeytinAssistantHints.hint(q, "", ALL);
            check(h.cards && h.topics.size() == 6 && h.questions.isEmpty(), "kartlar yok: " + q);
        }
        check(ZeytinAssistantHints.hint("Ne sorabilirim?", "", ALL).message.contains("Bir konu seç"), "yardım mesajı");
        check(ZeytinAssistantHints.hint("Traktörün radyosunu aç", "", ALL).message.startsWith("Bunu bilmiyorum."), "bilinmeyen mesajı");

        // 4) Özel cevaplar önce gelir: dozda ilaçlama önerilmez, silmede kayıt değişmez.
        for (String q : new String[]{"Gübre dozu kaç gram?", "Kaç litre ilaç atayım", "Kayıtları sil", "200 kilo hasat kaydet", "Merhaba", "Teşekkürler", "Yaprakta hastalık var mı"})
            check(ZeytinAssistantText.special(q) != null, "özel cevap yok: " + q);
        check(ZeytinAssistantText.unknownReply("Traktörün radyosunu aç").startsWith("Bu soruyu henüz bilmiyorum"), "eski genel cevap değişti");
        check(ZeytinAssistantText.unknownReply("Gübre dozu kaç gram?").startsWith("İlaç dozu"), "eski doz cevabı değişti");

        // 5) Kaydı olmayan konu önerilmez; yerine kayıt ekleme ekranı.
        Set<Topic> none = EnumSet.of(Topic.WEATHER, Topic.MARKET);
        Hint empty = ZeytinAssistantHints.hint("Suyu ne vakit verdik", "", none);
        check(empty.questions.equals(Collections.singletonList("Sulama aç")) && empty.message.contains("henüz sulama kaydın yok"), "kayıt yok: " + empty.message + " " + empty.questions);
        Hint partly = ZeytinAssistantHints.hint("Damlaya ne kadar para verdik", "", EnumSet.of(Topic.MONEY, Topic.WEATHER, Topic.MARKET));
        check(partly.topics.equals(Collections.singletonList(Topic.MONEY)) && partly.questions.size() == 3, "kaydı olan konu kalmalı: " + partly.topics + " " + partly.questions);
        check(ZeytinAssistantHints.hint("Dışarısı soğuk mu", "", none).questions.size() == 3, "hava kayıt istemez");
        check(ZeytinAssistantHints.topic(Topic.IRRIGATION, none).questions.equals(Collections.singletonList("Sulama aç")), "kart: kayıt yok");

        // 6) Anladığım etiketleri.
        tags("Bu ay Girelli’yi kaç kere suladık", "Girelli", false, 3, "Sulama|Girelli*|Bu ay*");
        tags("Son sulama ne zaman?", "Girelli", false, 1, "Sulama|Girelli|Bu sezon*");
        tags("Hava nasıl?", "Girelli", false, 3, "Hava|Girelli*");
        tags("Hangi bahçe daha çok verdi?", "", false, 3, "Hasat|Bu sezon*");
        tags("Toplam hasat kaç?", "Girelli", true, 3, "Hasat|Tüm bahçeler*|Bu sezon*");
        tags("Toplam sulama kaç kez?", "Girelli", true, 3, "Sulama|Girelli*|Bu sezon*");   // cevap tek bahçeden: "Tüm bahçeler" yazmamalı
        tags("Toplam son satış ne zaman?", "Girelli", true, 3, "Satış geliri|Girelli*|Bu sezon*");
        tags("Mazot masrafı", "Girelli", false, 3, "Mazot masrafı|Girelli*|Bu sezon*");
        tags("Piyasa nasıl?", "", false, 3, "Piyasa");
        tags("Kaç ağaç var?", "Afşar", false, 3, "Ağaç sayısı|Afşar*");
        tags("Bugün bahçede ne var?", "Girelli", false, 3, "Bugün|Girelli*");
        tags("Şu an hangi bahçe seçili?", "Girelli", false, 3, "Seçili bahçe");
        tags("Elek aç", "", false, 3, "");
        tags("Girelli ile Afşar’ı karşılaştır.", "", false, 3, "");
        for (Kind k : Kind.values()) if (k != Kind.UNKNOWN && k != Kind.NAVIGATION && k != Kind.COMPARISON) {
            boolean seen = false;
            for (String q : SAMPLE) { ZeytinAssistantCommand c = ZeytinAssistantCommand.parse(q); if (c.kind == k) { seen = true; check(!ZeytinAssistantHints.topicLabel(c).isEmpty(), "etiket adı yok: " + k); } }
            check(seen, "örnek yok: " + k);
        }
        check(ZeytinAssistantHints.supportsAll(ZeytinAssistantCommand.parse("Toplam hasat kaç?")), "toplam hasat tüm bahçeler");
        check(ZeytinAssistantHints.supportsAll(ZeytinAssistantCommand.parse("Bu sezon masrafım kaç?")), "masraf tüm bahçeler");
        check(!ZeytinAssistantHints.supportsAll(ZeytinAssistantCommand.parse("Son satış ne zaman?")), "son satış tek bahçe");
        check(!ZeytinAssistantHints.supportsAll(ZeytinAssistantCommand.parse("Son alıcı kim?")), "alıcı tek bahçe");
        check(!ZeytinAssistantHints.supportsAll(ZeytinAssistantCommand.parse("Son sulama ne zaman?")), "sulama tek bahçe");

        // 7) Dönem değiştirme: tür aynı kalır, yalnızca dönem değişir.
        ZeytinAssistantCommand last = ZeytinAssistantCommand.parse("Son sulama ne zaman?");
        ZeytinAssistantCommand m = last.withPeriod("bu ay");
        check(m.kind == Kind.IRRIGATION && m.period.name.equals("bu ay") && m.latest, "son sulama → bu ay");
        ZeytinAssistantCommand season = ZeytinAssistantCommand.parse("Bu ay masrafım kaç?").withPeriod("");
        check(season.kind == Kind.EXPENSE && !season.period.limited(), "bu ay → bu sezon");
        ZeytinAssistantCommand week = ZeytinAssistantCommand.parse("Bu hafta ne yaptık?").withPeriod("gecen hafta");
        check(week.kind == Kind.OPERATION && week.period.name.equals("gecen hafta") && week.text.contains("ne yapt"), "bu hafta → geçen hafta: " + week.text);
        ZeytinAssistantCommand typed = ZeytinAssistantCommand.parse("Mazot masrafı").withPeriod("dun");
        check(typed.kind == Kind.EXPENSE && typed.opType.equals("Mazot") && typed.period.name.equals("dun"), "tür masrafı dönem");
        for (String[] p : ZeytinAssistantHints.PERIODS) {
            ZeytinAssistantCommand c = ZeytinAssistantCommand.parse("Geçen ay kaç kilo hasat yaptım?").withPeriod(p[0]);
            check(c.period.name.equals(p[0]) && c.kind == Kind.HARVEST, "dönem anahtarı: " + p[0]);
            check(ZeytinAssistantHints.periodLabel(c.period).equals(p[1]), "dönem etiketi: " + p[1]);
        }
        check(ZeytinAssistantHints.periodOptions(ZeytinAssistantPeriod.parse("")).size() == 5, "tasarımdaki 5 seçenek");
        List<String[]> withDun = ZeytinAssistantHints.periodOptions(ZeytinAssistantPeriod.parse("dun"));
        check(withDun.size() == 6 && withDun.get(1)[0].equals("dun"), "şu anki dönem (dün) listede");

        System.out.println("HintsTest: " + n + " kontrol, " + fails.size() + " hata");
        for (String f : fails) System.out.println("  HATA: " + f);
        if (!fails.isEmpty()) System.exit(1);
    }

    static final String[] SAMPLE = {"Şu an hangi bahçe seçili?", "Girelli’de kaç ağaç var?", "Girelli kaç yaşında?", "Girelli’nin çeşidi ne?",
            "Son sulama ne zaman?", "Son gübreleme ne zaman?", "Son ilaçlama ne zaman?", "Toplam hasat kaç?", "Bu sezon masrafım kaç?",
            "Toplam satışım kaç?", "Kaç kilo sattım?", "Double kaç kilo?", "Salamurada kaç kilo var?", "Hazır ürün kaç kilo?", "Piyasa nasıl?",
            "Hava nasıl?", "Sezon özeti ver.", "Kaç dekar?", "Bahçe nerede?", "Sulama tipi ne?", "Bahçe notu var mı?", "En son ne yaptık?",
            "Bugün bahçede ne var?"};

    /** "Etiket|Etiket*" — * değiştirilebilir demek. */
    static void tags(String q, String garden, boolean all, int count, String expected) {
        List<Tag> got = ZeytinAssistantHints.understood(ZeytinAssistantCommand.parse(q), garden, all, count);
        StringBuilder b = new StringBuilder();
        for (Tag t : got) { if (b.length() > 0) b.append('|'); b.append(t.text).append(t.editable ? "*" : ""); }
        check(b.toString().equals(expected), "etiketler: " + q + " → " + b + " (beklenen " + expected + ")");
    }
}
