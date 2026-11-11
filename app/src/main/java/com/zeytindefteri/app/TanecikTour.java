package com.zeytindefteri.app;

import java.util.Arrays;
import java.util.List;

/**
 * "Tanecik ile tanıtım turu": Tanecik uygulamayı adım adım gezdirir; her adımda ilgili yere gider, balonla anlatır
 * ve telefonun Türkçe sesiyle okur. Yeni kullanıcı için rehber; tanıtım moduyla ekran kaydı alınınca reklam filmi.
 *
 * Saf sınıf (Android yok): metinler ve sıra test edilir.
 */
public final class TanecikTour {
    private TanecikTour() {}

    /** Hedef etiketleri (ProducerFeedActivity bu etiketleri görünümlere koyar). */
    public static final String TAG_TAKVIM = "tur:takvim", TAG_HAVA = "tur:hava", TAG_MIKROFON = "tur:mikrofon";
    /** Alt menü düğmeleri: "nav:" + bölüm. */
    public static final String NAV = "nav:";
    /** Adım bitince: elek raporu açılır / Elek kayıt ekranında gösterim yapılır. İkisi de bitince tura döner. */
    public static final String DO_REPORT = "REPORT", DO_ELEK = "ELEK_GUIDE";
    /** Adım bitince Tezgâh ana ekranı tur kipinde açılır (rol/son ekran değişmez, servis başlamaz), gösterim, dönüş. */
    public static final String DO_TEZGAH = "TEZGAH_GUIDE";
    /** Tezgâh ana ekranındaki hedefler (TezgahHomeActivity bu etiketleri koyar). */
    public static final String TZ_START = "tur:tz_baslat", TZ_TODAY = "tur:tz_bugun", TZ_BOOK = "tur:tz_defter",
            TZ_WEB = "tur:tz_bilgisayar";

    public static final class Step {
        /** section: açılacak bölüm; tap: önce basılacak alt menü düğmesi (boş: yok); tag: gidilecek/gösterilecek yer. */
        public final String section, tap, tag, text, after;
        /** Konuşurken Tanecik sayfayı eliyle aşağı kaydırıp geri getirir (liste uzunsa). */
        public final boolean swipe;

        Step(String section, String tap, String tag, String text, String after) {
            this(section, tap, tag, text, after, false);
        }

        Step(String section, String tap, String tag, String text, String after, boolean swipe) {
            this.section = section; this.tap = tap; this.tag = tag; this.text = text; this.after = after;
            this.swipe = swipe;
        }
    }

    public static List<Step> steps() {
        return Arrays.asList(
                new Step("HOME", "", "", "Selam! Ben Tanecik. Hızlıca gezelim!", ""),
                new Step("HOME", "", TAG_TAKVIM, "Takvim ve hava: dönemin, ilaç için en kuru gün.", ""),
                new Step("GARDENS", NAV + "GARDENS", "", "Bahçelerim: her bahçenin hasadı, masrafı ayrı.", "", true),
                new Step("ELEK", NAV + "ELEK", "", "Elek: satışların burada. Bak, nasıl yazılır!", DO_ELEK, true),
                new Step("HOME", NAV + "HOME", TAG_MIKROFON, "Ya da mikrofona bas, \"150 kilo topladım\" de.", ""),
                new Step("HOME", "", "", "Elek raporu: en iyi alıcıyı söylerim!", DO_REPORT),
                // Zeytin Ligi ve Sohbet açılır. Tanıtım modunda ikisi de uydurma veri gösterir (gerçek üretici adı,
                // gerçek mesaj görünmez; Sohbet'ten mesaj gönderilmez).
                new Step("REPORTS", NAV + "REPORTS", "", "Zeytin Ligi: bölgenin fiyatı ve senin sıran.", "", true),
                new Step("MEYDAN", NAV + "MEYDAN", "", "Sohbet: komşu üreticilerle fiyat konuş.", ""),
                new Step("HOME", NAV + "HOME", "", "Tezgâhçıysan, gel tezgâhı göstereyim!", DO_TEZGAH),
                new Step("HOME", "", NAV + "PROFILE", "Hepsi bu! Tezgâh için Ayarlar → Kullanım şekli.", ""));
    }

    /** Elek kayıt ekranındaki gösterim: görünüm adı (R.id), söz, alana yazılacak örnek (boş: yazma). Kaydet'e basılmaz. */
    public static final class GuideStep {
        public final String view, text, type;

        GuideStep(String view, String text, String type) { this.view = view; this.text = text; this.type = type; }
    }

    public static List<GuideStep> elekGuide() {
        return Arrays.asList(
                new GuideStep("btnElekVoice", "Basıp konuşarak da kaydedersin.", ""),
                new GuideStep("etKg2", "Ya da elle yaz; tutarı ben hesaplarım.", "150"),
                new GuideStep("btnSaveElek", "Sonra Kaydet. Bu kadar kolay!", ""));
    }

    /**
     * Tezgâh ana ekranındaki gösterim (etiketle bulunur; görünmeyen adım atlanır, ör. bilgisayar satırı yoksa).
     * Hiçbir düğmeye basılmaz, kayıt oluşmaz.
     */
    public static List<GuideStep> tezgahGuide() {
        return Arrays.asList(
                new GuideStep(TZ_START, "Tartım başlat: kiloyu sınıf sınıf yaz, fişi ben basarım.", ""),
                new GuideStep(TZ_TODAY, "Bugün: kaç kilo aldın, kime ne borç; akşam gün sonu.", ""),
                new GuideStep(TZ_BOOK, "Alım defteri, cari, kasa: herkesin hesabı burada.", ""),
                new GuideStep(TZ_WEB, "Bilgisayara bağlan: QR'ı okut, tezgâh büyük ekranda!", ""));
    }

    // ---------------------------------------------------------------- tur temposu (1–1,5 dakika)

    /** Turda yürüme hızı (dp/sn; normalde 38), pervaneli inişin üst sınırı ve adım arası beklemeler (ms). */
    public static final int WALK_DP_S = 130;
    public static final long FLY_MAX_MS = 1000, SETTLE_MS = 300, PRESS_MS = 300, SCROLL_MS = 400, GAP_MS = 150;
    /** Elle kaydırma: aşağı iniş, bekleme, geri dönüş (ms) ve en fazla kaydırma (sayfa yüksekliğinin oranı). */
    public static final long SWIPE_DOWN_MS = 1100, SWIPE_HOLD_MS = 500, SWIPE_UP_MS = 900;
    public static final float SWIPE_PAGE = 0.6f;

    public static long swipeMs() {
        return SWIPE_DOWN_MS + SWIPE_HOLD_MS + SWIPE_UP_MS;
    }

    /** Turdaki konuşma hızı (TanecikVoice.TOUR_RATE ile aynı). */
    public static final float TOUR_RATE = 1.22f;
    /** Rapor ekranında söylenen yaklaşık söz (başlık + en iyi alıcı) ve ekran geçişi. */
    static final int REPORT_CHARS = 110;
    static final long SCREEN_MS = 700;

    /** Türkçe sesin yaklaşık okuma süresi (1,0 hızda ~14 harf/sn). */
    public static long speechMs(String text, float rate) {
        return Math.round(((text == null ? 0 : text.length()) * 72L + 300) / Math.max(0.5f, rate));
    }

    /** Turun tahmini toplam süresi (ms): yürüme/zıplama/uçuş + konuşma + beklemeler + Elek gösterimi + rapor. */
    public static long estimateMs() {
        long ms = 1200;   // ses hazırlanır, Tanecik yere iner
        for (Step st : steps()) {
            ms += st.tap.isEmpty() ? SETTLE_MS : (long) (200f / WALK_DP_S * 1000) + PRESS_MS + SETTLE_MS;
            if (TAG_TAKVIM.equals(st.tag) || TAG_HAVA.equals(st.tag)) ms += SCROLL_MS + Math.round(800 * 0.75);
            else if (st.tag.startsWith(NAV) || TAG_MIKROFON.equals(st.tag)) ms += (long) (110f / WALK_DP_S * 1000);   // komşu düğmeye yürür
            else ms += FLY_MAX_MS / 2;
            ms += Math.max(speechMs(st.text, TOUR_RATE), st.swipe ? swipeMs() : 0) + GAP_MS;
            if (DO_ELEK.equals(st.after)) {
                ms += 2 * SCREEN_MS + 600 + 300;   // aç, gösterim başlasın, kapan
                for (GuideStep g : elekGuide()) ms += 350 + 380 + g.type.length() * 110L + speechMs(g.text, TOUR_RATE) + 120;
            }
            if (DO_REPORT.equals(st.after)) ms += 2 * SCREEN_MS + 250 + Math.round((REPORT_CHARS * 72L + 300) / TOUR_RATE) + 600;
            if (DO_TEZGAH.equals(st.after)) {
                ms += 2 * SCREEN_MS + 600 + 300;
                for (GuideStep g : tezgahGuide()) ms += 350 + 380 + speechMs(g.text, TOUR_RATE) + 120;
            }
        }
        return ms;
    }

    /** Elek gösterimi ya da rapor ekranından "Turu bitir" denince ana sayfa turu da biter. */
    public static volatile boolean cancelRequested;

    /** Sesi olmayan telefonda adımın ekranda kalma süresi: okuma hızına göre, en az 4,5 sn. */
    public static long readMs(String text) {
        return Math.max(4500, (long) (text == null ? 0 : text.length()) * 70);
    }
}
