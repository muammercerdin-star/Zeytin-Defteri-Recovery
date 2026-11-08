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

    public static final class Step {
        /** section: açılacak bölüm; tap: önce basılacak alt menü düğmesi (boş: yok); tag: gidilecek/gösterilecek yer. */
        public final String section, tap, tag, text, after;

        Step(String section, String tap, String tag, String text, String after) {
            this.section = section; this.tap = tap; this.tag = tag; this.text = text; this.after = after;
        }
    }

    public static List<Step> steps() {
        return Arrays.asList(
                new Step("HOME", "", "", "Selam! Ben Tanecik. Zeytin Defteri'ni hızlıca gezelim!", ""),
                new Step("HOME", "", TAG_TAKVIM, "Zeytin takvimi: hangi dönemdesin, son hasat ne zaman, hemen görürsün.", ""),
                new Step("HOME", "", TAG_HAVA, "Hava kartı: yağmur, rüzgâr, en kuru gün. İlaçlamayı buna göre yap.", ""),
                new Step("GARDENS", NAV + "GARDENS", "", "Bahçelerim: her bahçenin ağacı, masrafı, hasadı ayrı ayrı.", ""),
                new Step("ELEK", NAV + "ELEK", "", "Elek: satışların burada. Bak, nasıl kaydedilir göstereyim!", DO_ELEK),
                new Step("HOME", NAV + "HOME", TAG_MIKROFON, "Ya da mikrofona bas, konuş: \"150 kilo topladım\" de, ben yazarım.", ""),
                new Step("HOME", "", "", "Elek raporu: aynı zeytine hangi alıcı daha çok veriyor, söylerim!", DO_REPORT),
                new Step("HOME", "", "", "Alacak bekleyince başımda ünlem çıkar, unutturmam.", ""),
                new Step("HOME", "", "", "Hepsi bu! Zeytin Defteri: üreticinin cebindeki defter.", ""));
    }

    /** Elek kayıt ekranındaki gösterim: görünüm adı (R.id), söz, alana yazılacak örnek (boş: yazma). Kaydet'e basılmaz. */
    public static final class GuideStep {
        public final String view, text, type;

        GuideStep(String view, String text, String type) { this.view = view; this.text = text; this.type = type; }
    }

    public static List<GuideStep> elekGuide() {
        return Arrays.asList(
                new GuideStep("btnElekVoice", "Buraya basıp konuşarak kaydedebilirsin.", ""),
                new GuideStep("etKg2", "Ya da elle: kiloyu sınıfının yanına yaz.", "150"),
                new GuideStep("tvPrice2", "Fiyatı bir kez girersin, defter hatırlar.", ""),
                new GuideStep("tvTotalRevenue", "Toplam tutar kendiliğinden hesaplanır.", ""),
                new GuideStep("btnSaveElek", "Sonra Kaydet'e basarsın. Bu kadar kolay!", ""));
    }

    /** Elek gösterimi ya da rapor ekranından "Turu bitir" denince ana sayfa turu da biter. */
    public static volatile boolean cancelRequested;

    /** Sesi olmayan telefonda adımın ekranda kalma süresi: okuma hızına göre, en az 4,5 sn. */
    public static long readMs(String text) {
        return Math.max(4500, (long) (text == null ? 0 : text.length()) * 70);
    }
}
