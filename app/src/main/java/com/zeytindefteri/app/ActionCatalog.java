package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Uygulamanın işleri tek listede (İP3; saf, Android'siz, testli). Ses, yazı ve ileride fotoğraf hangi beyinden
 * geçerse geçsin, sonuç bu listedeki bir eylemdir. Her eylemde: ad, alanlar, eksik alan sorusu, izin basamağı
 * ve onay kuralı.
 *
 * Onay kuralı elle verilmez, eylemin TÜRÜNDEN çıkar: kayıt, satış, ödeme ve silme HER ZAMAN çiftçinin onayını ister
 * ve en fazla "formu doldur" basamağına çıkabilir. Bu, katalogda bir bayrak değiştirilerek delinemez.
 *
 * Eylem kimlikleri (ELEK, ISLEM, SORU_SPRAY...) İP1'den beri telefondaki konuşma kaydında ve karnede aynı yazılır;
 * değiştirilmez (eski kayıtlar okunur kalır).
 */
final class ActionCatalog {

    /** İzin basamağı: önerir → formu hazır doldurur → çiftçi izin verdiyse kendisi yapar ve haber verir. */
    enum Level { ONER, FORMU_DOLDUR, IZINLE_KENDISI }

    /** Eylemin türü; onay kuralı buradan çıkar. */
    enum Kind {
        /** Bahçe/elek/hasat kaydı oluşturur. */
        KAYIT(true),
        /** Satış oluşturur. */
        SATIS(true),
        /** Ödeme/tahsilat yazar. */
        ODEME(true),
        /** Çiftçinin kaydını siler. */
        SILME(true),
        /** Kayıtlardan cevap verir, ekran açar; hiçbir şey yazmaz. */
        OKUMA(false),
        /** Asistanın kendi ayarı ya da yalnız bu telefondaki kendi notları (anlamadıklarım, konuşma kaydı, karne):
         *  çiftçinin açık komutuyla onaysız silinebilir (AGENTS.md, onay kuralının notu). */
        AYAR(false),
        /** Sohbet, hazır cevap, kapanış. */
        SOHBET(false),
        /** Zararsız hatırlatma; ileride çiftçi izin verirse kendiliğinden kurulabilir. */
        HATIRLATMA(false);

        final boolean alwaysConfirm;

        Kind(boolean alwaysConfirm) {
            this.alwaysConfirm = alwaysConfirm;
        }
    }

    /** Bir eylemin tanımı. */
    static final class Action {
        final String id;
        final String name;
        final Kind kind;
        final List<String> fields;
        /** Eksik alan sorusu (kısa, Türkçe); alansız eylemde boş. */
        final String question;
        /** Katalogda izin verilen en yüksek basamak (onaylı türlerde FORMU_DOLDUR'u geçemez). */
        final Level level;
        /** Bugünkü beyinler bu eylemi üretir mi (Satış/Tahsilat/Hatırlatma/Tezgâh: yalnız tanım). */
        final boolean produced;
        /** Ailenin ortak öneki (SORU_SPRAY, SORU_ELEK... → "SORU_"); tek eylemde boş. */
        final String family;

        private Action(String id, String name, Kind kind, String question, Level level, boolean produced, String family, String... fields) {
            this.id = id;
            this.name = name;
            this.kind = kind;
            this.question = question;
            this.level = kind.alwaysConfirm && level == Level.IZINLE_KENDISI ? Level.FORMU_DOLDUR : level;
            this.produced = produced;
            this.family = family;
            this.fields = Collections.unmodifiableList(Arrays.asList(fields));
        }

        /** Kayıt, satış, ödeme ve silme: her zaman onay (türden çıkar, değiştirilemez). */
        boolean alwaysConfirm() {
            return kind.alwaysConfirm;
        }
    }

    // Kimlikler (İP1 konuşma kaydı ve karneyle aynı yazım)
    static final String ELEK = "ELEK";
    static final String SATIS = "SATIS";
    static final String TAHSILAT = "TAHSILAT";
    static final String ISLEM = "ISLEM";
    static final String HASAT = "HASAT";
    static final String HATIRLATMA = "HATIRLATMA";
    static final String KAYIT_BRINE = "KAYIT_BRINE";
    static final String YAGHANE = "YAGHANE";
    /** Tanecik sahneleri (yönlendiren asistan): GÖSTER sormadan yapılır; ÖĞRET kayıt yapmaz. */
    static final String GOSTER = "GOSTER";
    static final String OGRET = "OGRET";
    static final String KAYIT_GARDEN = "KAYIT_GARDEN";
    static final String KAYIT_POST = "KAYIT_POST";
    static final String TEZGAH_TARTI = "TEZGAH_TARTI";
    static final String ELEK_AC = "ELEK_AC";
    static final String EKRAN_AC = "EKRAN_AC";
    static final String SORU = "SORU_";
    static final String ALACAK_SORU = "ALACAK_SORU";
    static final String OZEL_CEVAP = "OZEL_CEVAP";
    static final String SOHBET = "SOHBET";
    static final String KAPAT = "KAPAT";
    static final String ANLAMADIKLARI_GOSTER = "ANLAMADIKLARI_GOSTER";
    static final String ANLAMADIKLARI_SIL = "ANLAMADIKLARI_SIL";
    static final String KARNE_GOSTER = "KARNE_GOSTER";
    static final String KONUSMA_KAYDI_SIL = "KONUSMA_KAYDI_SIL";
    static final String BULUT_DURUM = "BULUT_DURUM";
    static final String BULUT_AC = "BULUT_AC";
    static final String BULUT_KAPAT = "BULUT_KAPAT";
    static final String BULUT_IZIN_EVET = "BULUT_IZIN_EVET";
    static final String BULUT_IZIN_HAYIR = "BULUT_IZIN_HAYIR";
    static final String ANLASILMADI = "ANLASILMADI";
    static final String SOZLER_GOSTER = "SOZLER_GOSTER";
    static final String SOZ_UNUT = "SOZ_UNUT";
    static final String SOZ_OGRET = "SOZ_OGRET";

    private static final Map<String, Action> ALL = new LinkedHashMap<>();
    /** Alan alan eksik alan soruları: "EYLEM|alan|değişke" → soru. Değişke yoksa "". Tek yer: sesli diyaloglar da buradan. */
    private static final Map<String, String> ASKS = new LinkedHashMap<>();

    static {
        // İlk eylemler (iş emri): ElekGir, Satış, Tahsilat, İşlem, Hasat, Hatırlatma
        add(new Action(ELEK, "Elek gir", Kind.KAYIT, "Hangi elek, kaç kilo?", Level.FORMU_DOLDUR, true, "",
                "double", "1no", "2no", "3no", "4no", "yaglik", "fiyat", "satis", "alici", "bahce"));
        // İP7: yalnız çok adımlı satış cümlesinde üretilir (satış + alacak + ödeme hatırlatması), formu doldurur.
        add(new Action(SATIS, "Satış", Kind.SATIS, "Kime, kaç kilo, kaç liradan sattın?", Level.FORMU_DOLDUR, true, "",
                "alici", "kilo", "fiyat", "tutar", "sinif", "odeme", "vade", "adimlar"));
        add(new Action(TAHSILAT, "Tahsilat", Kind.ODEME, "Kimden, ne kadar aldın?", Level.FORMU_DOLDUR, true, "",
                "alici", "tutar", "oran", "tarih"));   // oran: "yarim" (borcun yarısı; eğitim turu 1)
        add(new Action(ISLEM, "Bahçe işi", Kind.KAYIT, "Hangi bahçede ne yaptın?", Level.FORMU_DOLDUR, true, "",
                "tur", "gun", "miktar", "birim", "tutar", "bahce", "not"));   // not: "Diğer" işinin adı (İP5)
        add(new Action(HASAT, "Hasat", Kind.KAYIT, "Hangi bahçeden kaç kilo topladın?", Level.FORMU_DOLDUR, true, "",
                "double", "1no", "2no", "3no", "4no", "yaglik", "toplam", "gun", "bahce", "iscilik", "nakliye"));
        // Eğitim turu 1: gelecek zamanlı iş cümlesinden üretilir. Basamak İP7'deki gibi (ödeme hatırlatması izinle
        // kendiliğinden kurulabilir); asistanın iş hatırlatması ise her zaman sorulur (ZeytinAssistantController.askReminder).
        add(new Action(HATIRLATMA, "Hatırlatma", Kind.HATIRLATMA, "Neyi, ne zaman hatırlatayım?", Level.IZINLE_KENDISI, true, "",
                "ne", "zaman", "tur", "not", "gun", "saat"));
        // Bugün sesle açılan diğer kayıtlar
        add(new Action(KAYIT_BRINE, "Salamura", Kind.KAYIT, "Kaç kilo zeytin, ne kadar tuz?", Level.FORMU_DOLDUR, true, "",
                "zeytin", "tuz", "su", "cins"));
        // Eğitim turu 1: yağhane sıkım kaydı (OilActivity). Kasa kiloya çevrilmez (notta kalır); kayıt Kaydet'le.
        add(new Action(YAGHANE, "Yağhane", Kind.KAYIT, "Hangi yağhane, kaç kilo zeytin, kaç kilo yağ?", Level.FORMU_DOLDUR, true, "",
                "kasa", "zeytin", "yag", "birim", "yaghane"));
        add(new Action(KAYIT_GARDEN, "Yeni bahçe", Kind.KAYIT, "Bahçenin adı ne, kaç ağaç?", Level.FORMU_DOLDUR, true, "",
                "ad", "agac", "cins", "alan"));
        add(new Action(KAYIT_POST, "Meydan gönderisi", Kind.KAYIT, "Ne yazayım?", Level.FORMU_DOLDUR, true, "",
                "metin"));
        // Tezgâh (tüccar tarafı): yalnız tanım; sesli akışı kendi ekranında, bu pakette ona dokunulmaz.
        add(new Action(TEZGAH_TARTI, "Tezgâh tartımı", Kind.SATIS, "Hangi elek, kaç kasa, kaç kilo?", Level.FORMU_DOLDUR, false, "",
                "elek", "kasa", "kilo", "fiyat", "satici"));
        // Okuma: cevap, ekran
        add(new Action(ELEK_AC, "Elek ekranını aç", Kind.OKUMA, "", Level.IZINLE_KENDISI, true, ""));
        add(new Action(EKRAN_AC, "Ekran aç", Kind.OKUMA, "Hangi ekranı açayım?", Level.IZINLE_KENDISI, true, "", "ekran"));
        add(new Action(SORU, "Kayıtlardan soru", Kind.OKUMA, "", Level.IZINLE_KENDISI, true, SORU, "boy", "islem"));
        add(new Action(ALACAK_SORU, "Alacak sorusu", Kind.OKUMA, "", Level.IZINLE_KENDISI, true, ""));
        // Yönlendiren asistan: Tanecik ekranı açar, parlatır, okur (GÖSTER, sormadan) ya da adım adım öğretir (ÖĞRET).
        add(new Action(GOSTER, "Tanecik gösterir", Kind.OKUMA, "Neyi göstereyim?", Level.IZINLE_KENDISI, true, "", "sahne"));
        add(new Action(OGRET, "Tanecik öğretir", Kind.OKUMA, "Neyi öğreteyim?", Level.IZINLE_KENDISI, true, "", "sahne"));
        add(new Action(OZEL_CEVAP, "Hazır cevap", Kind.SOHBET, "", Level.IZINLE_KENDISI, true, ""));
        add(new Action(SOHBET, "Sohbet", Kind.SOHBET, "", Level.IZINLE_KENDISI, true, ""));
        add(new Action(KAPAT, "Kapat", Kind.SOHBET, "", Level.IZINLE_KENDISI, true, ""));
        // Asistanın kendi notları ve ayarları (yalnız bu telefonda; çiftçinin kaydı değil)
        add(new Action(ANLAMADIKLARI_GOSTER, "Anlamadıklarımı göster", Kind.AYAR, "", Level.IZINLE_KENDISI, true, ""));
        add(new Action(ANLAMADIKLARI_SIL, "Anlamadıklarımı sil", Kind.AYAR, "", Level.IZINLE_KENDISI, true, ""));
        fieldQuestions();
        add(new Action(KARNE_GOSTER, "Karneyi göster", Kind.AYAR, "", Level.IZINLE_KENDISI, true, ""));
        add(new Action(KONUSMA_KAYDI_SIL, "Konuşma kaydını sil", Kind.AYAR, "", Level.IZINLE_KENDISI, true, ""));
        add(new Action(BULUT_DURUM, "İnternet yardımcısı durumu", Kind.AYAR, "", Level.IZINLE_KENDISI, true, ""));
        add(new Action(BULUT_AC, "İnternet yardımcısını aç", Kind.AYAR, "", Level.IZINLE_KENDISI, true, ""));
        add(new Action(BULUT_KAPAT, "İnternet yardımcısını kapat", Kind.AYAR, "", Level.IZINLE_KENDISI, true, ""));
        add(new Action(BULUT_IZIN_EVET, "İnternet yardımcısına izin", Kind.AYAR, "", Level.IZINLE_KENDISI, true, ""));
        add(new Action(BULUT_IZIN_HAYIR, "İnternet yardımcısına izin yok", Kind.AYAR, "", Level.IZINLE_KENDISI, true, ""));
        add(new Action(ANLASILMADI, "Anlaşılmadı", Kind.SOHBET, "", Level.ONER, true, ""));
        // Öğrenilen sözler (İP4): yalnız bu telefondaki kişisel sözlük; öğretme çiftçinin düğmeye basmasıyla olur.
        add(new Action(SOZLER_GOSTER, "Öğrendiklerimi söyle", Kind.AYAR, "", Level.IZINLE_KENDISI, true, ""));
        add(new Action(SOZ_UNUT, "Öğrenilen sözü unut", Kind.AYAR, "Hangi sözü unutayım?", Level.IZINLE_KENDISI, true, "", "soz"));
        add(new Action(SOZ_OGRET, "Söz öğret", Kind.AYAR, "Bu ne işi?", Level.FORMU_DOLDUR, false, "", "soz", "anlam"));
    }

    private ActionCatalog() {
    }

    /**
     * Yönlendiren asistan: alan alan eksik alan soruları. Bugünkü sesli diyalogların (OperationVoiceDialog,
     * VoiceRecordDialog) ve uygulayıcının (ActionRunner) soruları birebir buradan okunur; metinleri değişmedi.
     */
    private static void fieldQuestions() {
        ask(ISLEM, "tur", "", "Ne yaptın?");
        ask(ISLEM, "tur", "masraf", "Ne için harcadın?");
        ask(ISLEM, "bahce", "", "Hangi bahçe için?");
        ask(ISLEM, "bahce", "Sulama", "Hangi bahçeyi suladın?");
        ask(ISLEM, "bahce", "Gübre", "Hangi bahçeye gübre attın?");
        ask(ISLEM, "bahce", "İlaçlama", "Hangi bahçeyi ilaçladın?");
        ask(ISLEM, "bahce", "Budama", "Hangi bahçeyi budadın?");
        ask(ISLEM, "bahce", "Sinek tuzağı", "Hangi bahçeye tuzak astın?");
        ask(ISLEM, "bahce", "Toprak İşleme", "Hangi bahçeyi sürdün?");
        ask(ISLEM, "bahce", "İşçilik", "Hangi bahçede çalıştılar?");
        ask(ISLEM, "not", "", "Ne attın? İlacın adını söyle.");
        ask(ISLEM, "not", "Gübre", "Hangi gübreyi attın?");
        ask(ISLEM, "miktar", "", "Ne kadar?");
        ask(ISLEM, "miktar", "Sulama", "Kaç saat suladın?");
        ask(ISLEM, "miktar", "Gübre", "Ne kadar attın?");
        ask(ISLEM, "miktar", "İlaçlama", "Ne kadar kullandın?");
        ask(ISLEM, "miktar", "Budama", "Kaç ağaç budadın?");
        ask(ISLEM, "miktar", "Sinek tuzağı", "Kaç tuzak astın?");
        ask(ISLEM, "miktar", "Toprak İşleme", "Kaç dekar sürdün?");
        ask(ISLEM, "miktar", "İşçilik", "Kaç işçi çalıştı?");
        ask(ISLEM, "miktar", "Mazot", "Kaç litre aldın?");
        ask(ISLEM, "miktar", "Nakliye", "Kaç sefer yaptın?");
        ask(ISLEM, "tutar", "", "Masrafı ne kadar tuttu?");
        ask(ISLEM, "tutar", "İşçilik", "Kaç lira yevmiye verdin?");
        ask(HASAT, "bahce", "", "Hangi bahçeden topladın?");
        ask(HASAT, "toplam", "", "Kaç kilo topladın?");
        ask(HASAT, "iscilik", "", "İşçilik masrafı oldu mu? Kaç lira?");
        ask(HASAT, "nakliye", "", "Nakliye tuttu mu? Kaç lira?");
        ask(KAYIT_BRINE, "zeytin", "", "Kaç kilo zeytin bastın?");
        ask(KAYIT_BRINE, "cins", "", "Hangi zeytin? Gemlik mi, Domat mı?");
        ask(KAYIT_BRINE, "tuz", "", "Kaç kilo tuz attın?");
        ask(KAYIT_BRINE, "su", "", "Kaç litre su koydun?");
        ask(KAYIT_GARDEN, "ad", "", "Bahçenin adı ne olsun?");
        ask(KAYIT_GARDEN, "agac", "", "Kaç ağaç var?");
        ask(KAYIT_GARDEN, "cins", "", "Hangi zeytin? Gemlik, Domat, Ayvalık?");
        ask(KAYIT_GARDEN, "alan", "", "Kaç dönüm?");
        ask(KAYIT_POST, "metin", "", "Ne yazayım? Söyle, ben yazayım.");
        ask(SATIS, "alici", "", "Kime sattın?");
        ask(SATIS, "kilo", "", "Kaç kilo sattın?");
        ask(SATIS, "fiyat", "", "Kilosu kaçtan?");
        ask(SATIS, "odeme", "", "Parasını aldın mı?");
        ask(TAHSILAT, "alici", "", "Kimden para aldın?");
        ask(TAHSILAT, "tutar", "", "Kaç lira aldın?");
    }

    private static void ask(String action, String field, String variant, String question) {
        if (!ALL.get(action).fields.contains(field)) {
            throw new IllegalStateException("katalogda olmayan alan: " + action + "|" + field);
        }
        ASKS.put(action + "|" + field + "|" + variant, question);
    }

    /** Alanın eksik alan sorusu; değişkeye özel yoksa genel; hiç yoksa "". */
    static String question(String action, String field, String variant) {
        String q = ASKS.get(action + "|" + field + "|" + (variant == null ? "" : variant));
        if (q != null) {
            return q;
        }
        q = ASKS.get(action + "|" + field + "|");
        return q == null ? "" : q;
    }

    /** Soru tablosunun anahtarları (test için). */
    static java.util.Set<String> questionKeys() {
        return Collections.unmodifiableSet(ASKS.keySet());
    }

    private static void add(Action a) {
        if (ALL.containsKey(a.id)) {
            throw new IllegalStateException("Aynı eylem iki kez: " + a.id);
        }
        ALL.put(a.id, a);
    }

    /** Eylem tanımı: tam kimlik ya da aile ("SORU_SPRAY" → SORU_). Bilinmeyen kimlikte null. */
    static Action get(String id) {
        if (id == null) {
            return null;
        }
        Action exact = ALL.get(id);
        if (exact != null) {
            return exact;
        }
        for (Action a : ALL.values()) {
            if (!a.family.isEmpty() && id.startsWith(a.family) && id.length() > a.family.length()) {
                return a;
            }
        }
        return null;
    }

    static List<Action> all() {
        return new ArrayList<>(ALL.values());
    }

    /** Eylem onay ister mi (bilinmeyen eylem de onay ister: emin olunmayan şey yazılmaz). */
    static boolean alwaysConfirm(String id) {
        Action a = get(id);
        return a == null || a.alwaysConfirm();
    }
}
