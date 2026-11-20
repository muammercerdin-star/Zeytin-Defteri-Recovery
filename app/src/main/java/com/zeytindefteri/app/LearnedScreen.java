package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * "Öğrendiklerim" ekranının içeriği (İP6; saf, Android'siz, testli). Uygulamanın çiftçi hakkında öğrendiği her şey
 * burada satır satır görünür ve silinir. Kural: ekrandaki her satır, uygulamanın gerçekten kullandığı bir bilgiye
 * karşılık gelir; olmayan bir şey "öğrendim" diye gösterilmez.
 *
 * Yaşlı kullanıcı için: büyük yazı, satır başına tek bilgi, teknik terim yok ("güven" yerine "eminim / pek emin değilim").
 */
final class LearnedScreen {
    private LearnedScreen() {
    }

    static final String TITLE = "Öğrendiklerim";
    static final String INTRO = "Senin hakkında öğrendiklerim. Hepsi yalnız bu telefonda; istediğini silebilirsin.";
    static final String TAUGHT = "Senin öğrettiklerin";
    static final String NOTICED = "Fark ettiklerim";
    static final String EMPTY = "Henüz bir şey öğrenmedim. Anlamadığım bir sözü bana öğrettiğinde burada görünür.";
    static final String NOTICED_EMPTY = "Kayıtlarından fark ettiğim bir alışkanlık olduğunda burada görünür.";
    static final String FORGET_ALL = "Hepsini unut";
    static final String FORGET_ALL_ASK = "Öğrendiğim her şeyi unutayım mı? Bunu geri alamazsın.";
    static final String SURE = "eminim";
    static final String NOT_SURE = "pek emin değilim";

    private static final Locale TR = new Locale("tr", "TR");
    private static final String[] MONTHS = {"Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos",
            "Eylül", "Ekim", "Kasım", "Aralık"};
    /** Ayın bulunma eki: "Ocak'ta", "Nisan'da", "Eylül'de". */
    private static final String[] MONTH_AT = {"'ta", "'ta", "'ta", "'da", "'ta", "'da", "'da", "'ta", "'de", "'de", "'da", "'ta"};

    /** Ekranda bir satır: büyük başlık, altında tek satır açıklama; key ile silinir. */
    static final class Row {
        /** Silinecek bilginin anahtarı (öğretilen sözde kök: "bakirl"). */
        final String key;
        final String title;
        final String detail;
        /** Silme düğmesinin sesli okunuşu ("Bakırladık sözünü sil"). */
        final String deleteSpoken;

        Row(String key, String title, String detail, String deleteSpoken) {
            this.key = key;
            this.title = title;
            this.detail = detail;
            this.deleteSpoken = deleteSpoken;
        }
    }

    /**
     * Fark ettiğim bir alışkanlık (İP5 kayıtlardan hesaplar). Bu sürümde İP5 yok: liste hep boştur, bölüm hazır bekler.
     * sure: çok kayıtta aynıysa "eminim", azsa "pek emin değilim".
     */
    static final class Noticed {
        final String key;
        final String what;
        final String value;
        final String evidence;
        final boolean sure;

        Noticed(String key, String what, String value, String evidence, boolean sure) {
            this.key = key;
            this.what = what;
            this.value = value;
            this.evidence = evidence;
            this.sure = sure;
        }
    }

    /**
     * Öğretilen sözlerin satırları, en yeni önce. Yalnız asistanın gerçekten kullanabildiği sözler (anlamı bir düğmeye
     * karşılık gelen); kullanılamayan bir söz "öğrendim" diye gösterilmez (Hepsini unut onu da siler).
     */
    static List<Row> taught(List<LearnedTerms.Term> terms, long now, TimeZone zone) {
        List<LearnedTerms.Term> usable = new ArrayList<>();
        for (LearnedTerms.Term t : terms) {
            if (usable(t)) {
                usable.add(t);
            }
        }
        Collections.sort(usable, (a, b) -> Long.compare(b.created, a.created));
        List<Row> rows = new ArrayList<>();
        for (LearnedTerms.Term t : usable) {
            String title = LearnedTerms.capital(t.word) + " = " + t.meaning();
            String detail = learnedOn(t.created, now, zone) + " · " + usesText(t.uses);
            rows.add(new Row(t.term, title, detail, LearnedTerms.capital(t.word) + " sözünü sil"));
        }
        return rows;
    }

    /** Asistan bu sözü gerçekten kullanır mı: anlamı bir öğretme seçimine karşılık gelir. */
    static boolean usable(LearnedTerms.Term t) {
        return t != null && !t.term.isEmpty() && LearnedTerms.TAUGHT.equals(t.kind) && LearnedTerms.choiceOf(t) != null;
    }

    static final String FROM_ALL = "Herkesten öğrendiklerim";
    static final String FROM_ALL_EMPTY = "Başka çiftçilerden gelen söz yok.";

    /** Herkesten öğrendiklerim satırı: "Bakırladık = ilaçlama" / "Herkesten · açık" ya da "kapattın". */
    static final class PackRow {
        final String root;
        final String title;
        final String detail;
        final boolean on;
        /** Çiftçi aynı sözü kendisi öğretti: onunki geçerli, burada açılıp kapatılmaz. */
        final boolean overridden;

        PackRow(String root, String title, String detail, boolean on, boolean overridden) {
            this.root = root;
            this.title = title;
            this.detail = detail;
            this.on = on;
            this.overridden = overridden;
        }
    }

    /** Paket sözlerinin satırları (kapatılanlar da, açılabilsin diye). */
    static List<PackRow> fromAll(List<LearnedTerms.Term> pack, LearnedTerms.Store taught, PackedStore.Disabled disabled) {
        List<PackRow> rows = new ArrayList<>();
        for (LearnedTerms.Term t : pack) {
            String title = LearnedTerms.capital(t.word) + " = " + t.meaning();
            LearnedTerms.Term own = taught.get(t.term);
            if (own != null) {
                rows.add(new PackRow(t.term, title, "Senin öğrettiğin geçerli: " + own.meaning(), false, true));
                continue;
            }
            boolean on = !disabled.is(t.term);
            rows.add(new PackRow(t.term, title, on ? "Herkesten · açık" : "Herkesten · kapattın", on, false));
        }
        return rows;
    }

    /** Fark ettiklerimin satırları: "Alıcı: Ali Usta" / "son 6 satışın 4'ü · eminim". */
    static List<Row> noticed(List<Noticed> list) {
        List<Row> rows = new ArrayList<>();
        if (list == null) {
            return rows;
        }
        for (Noticed n : list) {
            String title = n.what + ": " + n.value;
            String detail = n.evidence + " · " + (n.sure ? SURE : NOT_SURE);
            rows.add(new Row(n.key, title, detail, n.what + " önerisini sil"));
        }
        return rows;
    }

    /** "Bugün öğrendim", "Dün öğrendim", "12 Ocak'ta öğrendim", "3 Mart 2025'te öğrendim". */
    static String learnedOn(long created, long now, TimeZone zone) {
        if (created <= 0) {
            return "Öğrendim";
        }
        Calendar at = Calendar.getInstance(zone, TR);
        at.setTimeInMillis(created);
        Calendar today = Calendar.getInstance(zone, TR);
        today.setTimeInMillis(now);
        if (sameDay(at, today)) {
            return "Bugün öğrendim";
        }
        Calendar yesterday = (Calendar) today.clone();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(at, yesterday)) {
            return "Dün öğrendim";
        }
        int month = at.get(Calendar.MONTH);
        String day = at.get(Calendar.DAY_OF_MONTH) + " " + MONTHS[month];
        if (at.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
            return day + MONTH_AT[month] + " öğrendim";
        }
        int year = at.get(Calendar.YEAR);
        return day + " " + year + yearAt(year) + " öğrendim";
    }

    /** Yılın bulunma eki son rakama (ve 10/100'e) göre: 2025'te, 2026'da, 2030'da, 2000'de. */
    static String yearAt(int year) {
        if (year % 1000 == 0) {
            return "'de";
        }
        if (year % 100 == 0) {
            return "'de";
        }
        if (year % 10 == 0) {
            // on'da, yirmi'de, otuz'da, kırk'ta, elli'de, altmış'ta, yetmiş'te, seksen'de, doksan'da
            String[] tens = {"", "'da", "'de", "'da", "'ta", "'de", "'ta", "'te", "'de", "'da"};
            return tens[(year / 10) % 10];
        }
        String[] ones = {"", "'de", "'de", "'te", "'te", "'te", "'da", "'de", "'de", "'da"};
        return ones[year % 10];
    }

    static String usesText(int uses) {
        return uses <= 0 ? "henüz kullanmadım" : uses + " kez kullandım";
    }

    /** Asistanın "öğrendiklerini göster" cevabı (ekran açılırken söylenir). */
    static String spokenSummary(int taught) {
        if (taught <= 0) {
            return EMPTY;
        }
        return taught + " söz öğrendim. Hepsini " + TITLE + " sayfasında açtım; istediğini oradan silebilirsin.";
    }

    /** Sil düğmesine basınca söylenen. */
    static String forgotten(String word) {
        return "Tamam, “" + LearnedTerms.capital(word) + "” sözünü unuttum.";
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
