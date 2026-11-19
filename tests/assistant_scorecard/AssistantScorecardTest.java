package com.zeytindefteri.app;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Karne (İP1): yalnız sayılar, ortanca süre, "karneni göster" komutu, sesli form düzeltme sayımı. */
public class AssistantScorecardTest {
    static int n = 0;
    static int bad = 0;

    static void check(boolean ok, String m) {
        n++;
        if (!ok) {
            bad++;
            System.out.println("FAIL " + m);
        }
    }

    public static void main(String[] a) {
        counters();
        spans();
        command();
        showing();
        formFill();
        clearAll();
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " AssistantScorecard");
        if (bad > 0) {
            System.exit(1);
        }
    }

    static void counters() {
        String c = AssistantScorecard.count("", AssistantScorecard.UNDERSTOOD, 1);
        c = AssistantScorecard.count(c, AssistantScorecard.UNDERSTOOD, 1);
        c = AssistantScorecard.count(c, AssistantScorecard.MISSED, 1);
        check(AssistantScorecard.get(c, AssistantScorecard.UNDERSTOOD) == 2, "anlaşılan sayacı: " + c);
        check(AssistantScorecard.get(c, AssistantScorecard.MISSED) == 1, "anlaşılmayan sayacı: " + c);
        check(AssistantScorecard.count(c, "cumle", 1).equals(c), "bilinmeyen anahtar yok sayılır");
        check(AssistantScorecard.count(c, AssistantScorecard.FILLED, -3).equals(c), "eksi değer yok sayılır");
        check(AssistantScorecard.count(null, AssistantScorecard.FILLED, 2).equals("doldurulan=2"), "boş karne");
        check(AssistantScorecard.get("anlasilan=bozuk", AssistantScorecard.UNDERSTOOD) == 0, "bozuk sayı 0 okunur");
    }

    static void spans() {
        String c = "";
        c = AssistantScorecard.span(c, 9000);
        c = AssistantScorecard.span(c, 30000);
        c = AssistantScorecard.span(c, 12000);
        check(AssistantScorecard.medianMs(c) == 12000, "tek sayıda ortanca: " + c);
        c = AssistantScorecard.span(c, 14000);
        check(AssistantScorecard.medianMs(c) == 13000, "çift sayıda ortanca: " + c);
        check(AssistantScorecard.span(c, 0).equals(c), "sıfır süre sayılmaz");
        check(AssistantScorecard.span(c, 31L * 60L * 1000L).equals(c), "30 dakikadan uzun süre sayılmaz");
        String many = "";
        for (int i = 1; i <= 60; i++) {
            many = AssistantScorecard.span(many, i * 1000L);
        }
        check(AssistantScorecard.spans(many).size() == AssistantScorecard.SPAN_MAX, "en fazla 51 süre");
        check(AssistantScorecard.spans(many).get(0) == 10000L, "en eskisi düşer");
        check(AssistantScorecard.medianMs("") == 0, "süre yoksa 0");
    }

    static void command() {
        check(AssistantScorecard.asksShow("karneni göster"), "karneni göster");
        check(AssistantScorecard.asksShow("Karnen nasıl?"), "karnen nasıl");
        check(AssistantScorecard.asksShow("KARNENİ GÖSTER"), "büyük harf, İ");
        check(AssistantScorecard.asksShow("karneyi aç"), "karneyi aç");
        check(!AssistantScorecard.asksShow("karneni sil"), "silme komut sayılmaz");
        check(!AssistantScorecard.asksShow("Bugün 3 numara 120 kilo"), "elek cümlesi");
        check(!AssistantScorecard.asksShow("kar var mı"), "kar ≠ karne");
        check(!AssistantScorecard.asksShow(null), "boş");
        // Ses tanıma "karneni"yi "karnını" duyabiliyor (cihaz testi, 19 Kasım 2026)
        check(AssistantScorecard.asksShow("karnını göster"), "karnını göster");
        check(AssistantScorecard.asksShow("Karnini aç"), "karnini aç");
        check(AssistantScorecard.asksClearAll("karnını sil"), "karnını sil");
        check(!AssistantScorecard.asksShow("karnım ağrıyor"), "karnım ağrıyor ≠ karne");
        check(!AssistantScorecard.asksShow("karnı doydu"), "karnı doydu ≠ karne");
    }

    static void showing() {
        String[] empty = AssistantScorecard.show("");
        check(empty[0].startsWith("Karnem henüz boş"), "boş karne sözü: " + empty[0]);
        String c = "anlasilan=8;anlasilmayan=2;doldurulan=10;duzeltilen=3;sure=12000,20000,9000";
        String[] s = AssistantScorecard.show(c);
        check(s[0].contains("10 cümlenin 8 tanesini"), "okunan söz: " + s[0]);
        check(s[1].contains("Anlaşılan cümle: 8 / 10 (%80)"), "anlaşılma oranı: " + s[1]);
        check(s[1].contains("düzeltilen alan: 3 (%30)"), "düzeltme oranı: " + s[1]);
        check(s[1].contains("Kayıt süresi (ortanca): 12 sn · son 3 kayıt"), "ortanca süre: " + s[1]);
        check(s[1].contains("yalnız bu telefonda"), "gizlilik notu");
        check(!s[1].contains("\t"), "sekme yok");
        String[] noSave = AssistantScorecard.show("anlasilan=1");
        check(noSave[1].contains("henüz kayıt yok"), "kayıt yoksa süre yazılmaz: " + noSave[1]);
        check(noSave[1].contains("düzeltilen alan: 0\n"), "doldurulan alan yoksa düzeltme oranı yazılmaz: " + noSave[1]);
    }

    static void formFill() {
        AssistantScorecard.FormFill f = new AssistantScorecard.FormFill();
        f.put("3 Numara", 120);
        f.put("2 Numara", 80);
        f.put("Yağlık", 0);
        check(f.size() == 2, "sıfır alan doldurulmuş sayılmaz");
        Map<String, Double> saved = new HashMap<>();
        saved.put("3 Numara", 120.0);
        saved.put("2 Numara", 85.0);
        List<String> fixed = f.corrected(saved);
        check(fixed.size() == 1 && fixed.get(0).equals("2 Numara"), "değişen alan: " + fixed);
        saved.put("2 Numara", 80.001);
        check(f.corrected(saved).isEmpty(), "kuruş altı fark düzeltme değil");
        saved.remove("3 Numara");
        check(f.corrected(saved).contains("3 Numara"), "kaydedilmeyen alan düzeltilmiştir");
        check(f.corrected(null).size() == 2, "kayıt değeri yoksa hepsi");
        f.put("2 Numara", 0);
        check(f.size() == 1, "sesle sıfırlanan alan çıkar");
        f.clear();
        check(f.size() == 0, "temizle");
    }

    static void clearAll() {
        String[] yes = {"konuşma kaydını sil", "karneni sil", "karneyi sıfırla", "cümle kaydını temizle",
            "konusma kaydimi sil", "konuşma geçmişini unut"};
        for (String q : yes) {
            check(AssistantScorecard.asksClearAll(q), "silme komutu: " + q);
            check(!AssistantScorecard.asksShow(q), "silme komutu göster sayılmaz: " + q);
        }
        String[] no = {"karneni göster", "Girelli'deki kaydı sil", "son satış kaydını sil", "anlamadıklarını sil",
            "bugün ilaçladım", "konuşalım mı", ""};
        for (String q : no) {
            check(!AssistantScorecard.asksClearAll(q), "silme komutu değil: " + q);
        }
        check(!AssistantScorecard.asksClearAll(null), "boş cümle");
    }
}
