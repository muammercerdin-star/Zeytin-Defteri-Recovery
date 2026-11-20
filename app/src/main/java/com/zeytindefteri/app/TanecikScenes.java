package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tanecik sahneleri (saf, Android'siz, testli): Tanecik'in bir işi ekranda nasıl göstereceğinin adım listesi, veri
 * olarak. Sahneleri TEK oynatıcı oynatır (TanecikGuide); her iş için ayrı kod yazılmaz.
 *
 * Dört basamak: GÖSTER (ekranı açar, parlatır, okur; zararsız, sormadan), ÖĞRET (adım adım gösterir, "Şimdi sen dene"
 * der ve biter; kayıt yapmaz), HAZIRLA (formu doldurur, Kaydet'te durur; İP7), ZARARSIZ İŞ (hatırlatma, rapor, süzme;
 * yalnız çiftçi izin verdiyse). Kayıt, satış, ödeme ve silme Tanecik tarafından ASLA yapılmaz: SceneCheck.
 */
final class TanecikScenes {
    enum Level { GOSTER, OGRET, HAZIRLA, ZARARSIZ }

    /** Adım türleri: ekranı aç, öğeyi parlat, söyle, alana yaz, seç, soru sor, bekle, dur. */
    enum Kind { OPEN, HIGHLIGHT, SAY, TYPE, SELECT, ASK, WAIT, STOP }

    /** Söylenecek metnin yerine asistanın bugünkü cevabı okunur (sahne cevabı kaybettirmez). */
    static final String ANSWER = "{cevap}";

    /** Programla kurulan ekranlardaki parlatma hedefleri (ekranlar bu etiketleri görünümlere koyar). */
    static final String TAG_RECEIVABLES = "sahne:alacak";
    static final String TAG_EXPENSES = "sahne:masraf_toplam";
    static final String TAG_REMINDERS = "sahne:hatirlatmalar";
    static final String TAG_HARVEST_REPORT = "sahne:hasat_toplam";
    static final String TAG_MARKET = "sahne:piyasa";

    /** Ekranlar: kimlik → Activity sınıfının adı (testte dosyanın var olduğu denetlenir). */
    static final Map<String, String> SCREENS = new LinkedHashMap<>();

    static {
        SCREENS.put("BUYERS", "BuyersActivity");
        SCREENS.put("OPERATIONS", "OperationHistoryActivity");
        SCREENS.put("REMINDERS", "RemindersActivity");
        SCREENS.put("REPORT", "ReportActivity");
        SCREENS.put("MARKET", "MarketActivity");
        SCREENS.put("ELEK", "ElekActivity");
        SCREENS.put("SALE", "SaleActivity");
    }

    static final class Step {
        final Kind kind;
        /** OPEN: ekran kimliği; diğerleri: hedef (R.id adı ya da "sahne:"/"tur:" etiketi); hedefsizse "". */
        final String target;
        final String text;
        /** TYPE: yazılacak örnek; SELECT: seçilecek düğme; ASK: "evet" cevabının anahtarı. */
        final String value;

        Step(Kind kind, String target, String text, String value) {
            this.kind = kind;
            this.target = target;
            this.text = text;
            this.value = value;
        }
    }

    static final class Scene {
        final String id;
        /** Katalogdaki eylem (sahne "isteğe bağlı" olarak bu işin yanında durur). */
        final String action;
        final Level level;
        final String title;
        /** Cevabı okunacak bugünkü soru ("Kim bana borçlu?"); yoksa "". */
        final String answerOf;
        final List<Step> steps;

        Scene(String id, String action, Level level, String title, String answerOf, Step... steps) {
            this.id = id;
            this.action = action;
            this.level = level;
            this.title = title;
            this.answerOf = answerOf;
            this.steps = Collections.unmodifiableList(Arrays.asList(steps));
        }
    }

    private static final Map<String, Scene> ALL = new LinkedHashMap<>();
    /** Sahne → onu çağıran sözün kalıbı (normalize edilmiş cümlede). */
    private static final Map<String, String> SUBJECT = new LinkedHashMap<>();

    static {
        add(new Scene("alacaklar", ActionCatalog.ALACAK_SORU, Level.GOSTER, "Alacaklarım", "Kim bana borçlu?",
                new Step(Kind.SAY, "", "Şimdi Alıcılarım'ı açıyorum.", ""),
                new Step(Kind.OPEN, "BUYERS", "", ""),
                new Step(Kind.HIGHLIGHT, TAG_RECEIVABLES, ANSWER, ""),
                new Step(Kind.STOP, "", "", "")),
                "(alacak\\p{L}*|borclu\\p{L}*|borclar\\p{L}*)");
        add(new Scene("masraflar", "SORU_EXPENSE", Level.GOSTER, "Bu ayki masraflarım", "bu ay ne kadar masraf yaptım",
                new Step(Kind.SAY, "", "Şimdi İşlem geçmişi'ni açıyorum.", ""),
                new Step(Kind.OPEN, "OPERATIONS", "", ""),
                new Step(Kind.HIGHLIGHT, TAG_EXPENSES, ANSWER, ""),
                new Step(Kind.STOP, "", "", "")),
                "(masraf\\p{L}*|harcama\\p{L}*)");
        add(new Scene("odeme_hatirlatmalari", ActionCatalog.HATIRLATMA, Level.GOSTER, "Ödeme hatırlatmalarım", "",
                new Step(Kind.SAY, "", "Şimdi Hatırlatmalar'ı açıyorum.", ""),
                new Step(Kind.OPEN, "REMINDERS", "", ""),
                new Step(Kind.HIGHLIGHT, TAG_REMINDERS, "Ödeme günü hatırlatmaların burada; en yakını en üstte.", ""),
                new Step(Kind.STOP, "", "", "")),
                "(hatirlatma\\p{L}*)");
        add(new Scene("hasat_raporu", ActionCatalog.EKRAN_AC, Level.GOSTER, "Hasat raporu", "bu sene ne kadar hasat yaptım",
                new Step(Kind.SAY, "", "Şimdi Raporlar'ı açıyorum.", ""),
                new Step(Kind.OPEN, "REPORT", "", ""),
                new Step(Kind.HIGHLIGHT, TAG_HARVEST_REPORT, ANSWER, ""),
                new Step(Kind.STOP, "", "", "")),
                "(hasat rapor\\p{L}*|hasat\\p{L}* rapor\\p{L}*)");
        add(new Scene("piyasa", "SORU_MARKET", Level.GOSTER, "Piyasa fiyatı", "piyasa fiyatı ne",
                new Step(Kind.SAY, "", "Şimdi Piyasa'yı açıyorum.", ""),
                new Step(Kind.OPEN, "MARKET", "", ""),
                new Step(Kind.HIGHLIGHT, TAG_MARKET, ANSWER, ""),
                new Step(Kind.STOP, "", "", "")),
                "(piyasa\\p{L}*|fiyat\\p{L}*)");
        add(new Scene("elek_ogret", ActionCatalog.ELEK, Level.OGRET, "Elek nasıl girilir?", "",
                new Step(Kind.SAY, "", "Elek'i açıyorum; adım adım göstereyim.", ""),
                new Step(Kind.OPEN, "ELEK", "", ""),
                new Step(Kind.HIGHLIGHT, "btnElekVoice", "Buna basıp konuşarak yazdırabilirsin.", ""),
                new Step(Kind.HIGHLIGHT, "etKg2", "Ya da her sınıfın kilosunu buraya yazarsın.", ""),
                new Step(Kind.HIGHLIGHT, "btnSaveElek", "En son Kaydet'e sen basarsın; ben basmam.", ""),
                new Step(Kind.SAY, "", "Şimdi sen dene.", ""),
                new Step(Kind.STOP, "", "", "")),
                "");
        add(new Scene("satis_ogret", ActionCatalog.SATIS, Level.OGRET, "Satış nasıl yapılır?", "",
                new Step(Kind.SAY, "", "Satış ekranını açıyorum; adım adım göstereyim.", ""),
                new Step(Kind.OPEN, "SALE", "", ""),
                new Step(Kind.HIGHLIGHT, "etBuyerName", "Önce kime sattığını yazarsın.", ""),
                new Step(Kind.HIGHLIGHT, "cardPayment", "Parayı aldıysan Tamamı'nı, almadıysan Alınmadı'yı seçersin.", ""),
                new Step(Kind.HIGHLIGHT, "btnSaveSale", "En son Kaydet'e sen basarsın; ben basmam.", ""),
                new Step(Kind.SAY, "", "Şimdi sen dene.", ""),
                new Step(Kind.STOP, "", "", "")),
                "");
    }

    private TanecikScenes() {
    }

    private static void add(Scene s, String subject) {
        ALL.put(s.id, s);
        SUBJECT.put(s.id, subject);
    }

    static Scene get(String id) {
        return ALL.get(id);
    }

    static List<Scene> all() {
        return new ArrayList<>(ALL.values());
    }

    private static final String SHOW = "(goster\\p{L}*|ac|acar misin|acsana|bak|bakalim|bakayim|bakar misin)";
    private static final String HOW = "nasil (girilir|girerim|yazilir|yazarim|yapilir|yaparim|kaydedilir|kaydederim|girilir mi)";
    private static final String QUESTION = ".*\\b(ne|ne kadar|kac|kim|kime|kimde|ne zaman|hangi|en cok|nedir)\\b.*";

    /**
     * Cümlenin istediği sahne; yoksa null. GÖSTER: "göster / aç / bak" sözü ya da soru sözü olmadan yalnız
     * "masraflarım", "hatırlatmalarım" gibi iyelikli istek. "Ne kadar alacağım var?", "Kim bana borçlu?" soru kalır.
     * ÖĞRET: "Elek nasıl girilir?", "Satış nasıl yapılır?".
     */
    static Scene match(String raw) {
        String s = TurkishText.normalize(raw == null ? "" : raw).replaceAll("^tanecik\\s*", "").trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.matches(".*\\b" + HOW + "\\b.*")) {
            if (s.matches(".*\\belek\\p{L}*\\b.*")) {
                return ALL.get("elek_ogret");
            }
            if (s.matches(".*\\bsat(is|isi|isimi|arim|ilir|mak)\\p{L}*\\b.*")) {
                return ALL.get("satis_ogret");
            }
            return null;
        }
        boolean show = s.matches(".*\\b" + SHOW + "\\b.*");
        boolean bare = !s.matches(QUESTION) && !raw.contains("?") && s.matches(".*\\b\\p{L}+(larim|lerim|larimi|lerimi)\\b.*");
        if (!show && !bare) {
            return null;
        }
        if (s.matches(QUESTION)) {
            return null;
        }
        for (Map.Entry<String, String> e : SUBJECT.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            if (s.matches(".*\\b" + e.getValue() + "\\b.*")) {
                return ALL.get(e.getKey());
            }
        }
        return null;
    }
}
