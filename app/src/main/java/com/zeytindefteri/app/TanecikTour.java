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
    /** Adım bitince elek raporu açılır (BuyersActivity, tur kipinde kendi kendine okuyup döner). */
    public static final String DO_REPORT = "REPORT";

    public static final class Step {
        public final String section, tag, text, after;

        Step(String section, String tag, String text, String after) {
            this.section = section; this.tag = tag; this.text = text; this.after = after;
        }
    }

    public static List<Step> steps() {
        return Arrays.asList(
                new Step("HOME", "", "Merhaba, ben Tanecik! Sana Zeytin Defteri'ni kısaca gezdireyim.", ""),
                new Step("HOME", TAG_TAKVIM, "Burası zeytin takvimi. Hangi dönemdesin, son hasat ne zaman, bir bakışta görürsün.", ""),
                new Step("HOME", TAG_HAVA, "Bahçenin havası burada. Yağmur, rüzgâr, en kuru gün… İlaçlamayı ve hasadı buna göre planla.", ""),
                new Step("HOME", TAG_MIKROFON, "Ortadaki mikrofona bas ve konuş: \"150 kilo topladım, 2 numara 65 lira.\" "
                        + "Ben sana okurum, \"evet\" dersen yazarım.", ""),
                new Step("ELEK", "", "Elek ekranında zeytinini sınıf sınıf girersin. Her elek kaydı aynı zamanda satış kaydıdır: "
                        + "alıcı, kilo, para hepsi tek yerde.", ""),
                new Step("HOME", "", "Elek raporu, aynı zeytine hangi alıcının daha çok verdiğini söyler. Hadi bir bakalım!", DO_REPORT),
                new Step("HOME", "", "Alacağını da unutmam. Parası uzun süredir bekleyen olursa başımda ünlem çıkar, sana söylerim.", ""),
                new Step("HOME", "", "İşte bu kadar! Bana dokunursan konuşurum. Zeytin Defteri hep yanında.", ""));
    }

    /** Sesi olmayan telefonda adımın ekranda kalma süresi: okuma hızına göre, en az 4,5 sn. */
    public static long readMs(String text) {
        return Math.max(4500, (long) (text == null ? 0 : text.length()) * 70);
    }
}
