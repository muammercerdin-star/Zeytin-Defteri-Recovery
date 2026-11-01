package com.zeytindefteri.app;

import java.util.Arrays;
import java.util.List;

/** İnternet yardımcısının cevap denetimi: telefon denemesindeki (2 Kasım) gerçek model cevaplarıyla. */
public class AssistantCloudTest {
    static int n = 0, bad = 0;
    static void check(boolean ok, String m) { n++; if (!ok) { bad++; System.out.println("FAIL " + m); } }
    static final List<String> G = Arrays.asList("Girelli", "Kuyu dibi");
    static String ok(String result) { return "{\"ok\":true,\"result\":" + result + "}"; }

    public static void main(String[] a) {
        // 10. cümle: model "işçilik" dedi → kural "damlama" = sulama
        AssistantCloud.Result r = AssistantCloud.accept(ok("{\"islem\":\"iscilik\",\"bahce\":\"Kuyu dibi\",\"gun\":-1,\"miktar\":4,\"birim\":\"saat\",\"tutar\":null}"),
                "Dün Kuyu dibinde damlamayı açtık 4 saat", G);
        check(r != null && r.entry.type == 0 && r.garden == 1 && r.entry.dayOffset == -1 && r.entry.quantity == 4 && r.entry.unit.equals("saat"), "damlama → sulama (kural öncelikli)");
        // 17. cümle: bahçe uydurdu
        r = AssistantCloud.accept(ok("{\"islem\":\"ilaclama\",\"bahce\":\"Girelli\",\"gun\":0,\"miktar\":3,\"birim\":\"kg\",\"tutar\":450}"),
                "Bugün bahçeye ilaç vurduk kükürt 3 kilo 450 lira gitti", G);
        check(r != null && r.garden == -1 && r.entry.cost == 450 && r.entry.quantity == 3, "uydurulan bahçe atılır, sorulur");
        // 20. cümle: bahçe uydurdu
        r = AssistantCloud.accept(ok("{\"islem\":\"sulama\",\"bahce\":\"Girelli\",\"gun\":-1,\"miktar\":6,\"birim\":\" saat\",\"tutar\":400}"),
                "dün akşam motoru 6 saat çalıştırıp suladık mazotu 400 lira", G);
        check(r != null && r.garden == -1 && r.entry.unit.equals("saat") && r.entry.cost == 400, "20: bahçe atılır, birim boşluğu temizlenir");
        // 19. cümle: "Girelide" ekli yazılış kabul
        r = AssistantCloud.accept(ok("{\"islem\":\"budama\",\"bahce\":\"Girelli\",\"gun\":0,\"miktar\":30,\"birim\":\"agac\",\"tutar\":1500}"),
                "Girelide budama yaptık bugün 30 ağaç ustaya 1500 saydık", G);
        check(r != null && r.garden == 0 && r.entry.unit.equals("ağaç"), "Girelide → Girelli");
        // 18: kuyudibine bitişik
        r = AssistantCloud.accept(ok("{\"islem\":\"gubre\",\"bahce\":\"Kuyu dibi\",\"gun\":-2,\"miktar\":20,\"birim\":\"kg\",\"tutar\":null}"),
                "evelsi gün kuyudibine gübre attık 20 kilo", G);
        check(r != null && r.garden == 1 && r.entry.dayOffset == -2, "kuyudibine → Kuyu dibi, evelsi → -2");
        // 5: kişi başı çarpım kabul
        r = AssistantCloud.accept(ok("{\"islem\":\"iscilik\",\"bahce\":\"Girelli\",\"gun\":-1,\"miktar\":3,\"birim\":\"kisi\",\"tutar\":3600}"),
                "Dün Girelli'de 3 işçi çalıştı, yevmiye kişi başı 1200.", G);
        check(r != null && r.entry.cost == 3600 && r.entry.unit.equals("kişi"), "kişi başı çarpımı");
        // uydurma sayılar
        r = AssistantCloud.accept(ok("{\"islem\":\"sulama\",\"bahce\":null,\"gun\":0,\"miktar\":5,\"birim\":\"saat\",\"tutar\":999}"),
                "Bugün motoru çalıştırıp suladık 3 saat", G);
        check(r != null && r.entry.quantity == 0 && r.entry.cost == 0, "cümlede olmayan sayılar atılır");
        r = AssistantCloud.accept(ok("{\"islem\":\"iscilik\",\"miktar\":\"kisi\",\"tutar\":300}"), "Bugün amele tuttuk 300 lira verdik", G);
        check(r != null && r.entry.quantity == 0 && r.entry.cost == 300, "sayı alanına söz (14. cümle) atılır");
        // gün: model ne derse desin cümleden
        r = AssistantCloud.accept(ok("{\"islem\":\"budama\",\"gun\":-1}"), "Girelli'de dal kestik bugün", G);
        check(r != null && r.entry.dayOffset == 0 && r.garden == 0, "gün cümleden, bahçe cümleden");
        // reddedilenler
        check(AssistantCloud.accept("{\"ok\":false,\"reason\":\"not_understood\"}", "x y z", G) == null, "anlaşılmadı");
        check(AssistantCloud.accept(ok("{\"islem\":\"ucurtma\"}"), "bugün uçurtma uçurduk", G) == null, "bilinmeyen işlem");
        check(AssistantCloud.accept("<html>", "x", G) == null, "bozuk cevap");
        check(AssistantCloud.accept(null, "x", G) == null, "boş cevap");
        // hangi cümle gider
        check(AssistantCloud.eligible("dün akşam motoru 6 saat çalıştırıp suladık mazotu 400 lira"), "uygun: iş cümlesi");
        check(AssistantCloud.eligible("Girelide dal kestik bugün", G), "uygun: bahçe adı + geçmiş zaman");
        check(!AssistantCloud.eligible("bugün pazara gittim"), "sohbet gitmez");
        check(!AssistantCloud.eligible("Son sulama ne zaman?"), "soru gitmez");
        check(!AssistantCloud.eligible("yarın ilaçlasak mı"), "plan/soru gitmez");
        check(!AssistantCloud.eligible("elek aç"), "komut gitmez");
        check(!AssistantCloud.eligible("Meydan'a yaz: yağmur yağdı Girelli'ye 300 kilo"), "Meydan gitmez");
        check(AssistantCloud.eligible("Dün Girelli'de ağaçların altını temizledik, iki amele tuttuk 2400 verdik", G), "ağaç geçen iş cümlesi uygun");
        check(!AssistantCloud.eligible("Kaç ağacım var?", G) && !AssistantCloud.eligible("Girelli'de kaç ağaç var", G), "ağaç sorusu gitmez");
        // gövde
        String body = AssistantCloud.requestBody("Dün \"Girelli\"ye su saldık", G);
        check(body.contains("\\\"Girelli\\\"") && body.contains("\"gardens\":[\"Girelli\",\"Kuyu dibi\"]"), "gövde: " + body);
        // izin ve ses komutu
        check(AssistantCloud.consent("evet sor") == 1 && AssistantCloud.consent("hayır") == 0 && AssistantCloud.consent("son sulama ne zaman") == -1, "izin cevabı");
        check(AssistantCloud.toggleCommand("internet yardımcısını kapat") == 0 && AssistantCloud.toggleCommand("internet yardımcısını aç") == 1
                && AssistantCloud.toggleCommand("elek aç") == -1, "sesli aç/kapat");
        check(AssistantCloud.mentioned(ElekVoiceEntry.normalize("Kuyu dibinde suladık"), "Kuyu dibi") && !AssistantCloud.mentioned(ElekVoiceEntry.normalize("bahçeye ilaç"), "Girelli"), "bahçe geçiyor mu");
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " AssistantCloud");
        if (bad > 0) System.exit(1);
    }
}
