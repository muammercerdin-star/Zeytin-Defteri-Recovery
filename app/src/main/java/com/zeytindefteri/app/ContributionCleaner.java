package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ortak döngü temizleyicisi (İP8; saf, Android'siz, testli). Çiftçinin öğrettiği bir sözden, izin açıksa
 * gönderilebilecek TEK şeyi üretir: sözün kökü, tek kelimelik söz, seçilen anlam (eylem + tür + not etiketi),
 * il (ilçe/köy değil) ve uygulama sürümü. Başka hiçbir şey çıkmaz: cümlenin geri kalanı, isim, sayı, tutar,
 * bahçe adı, tarih, kullanım sayısı, kaynak (ses / yazı) asla.
 *
 * Söz bir kişi adına, telefondaki bahçe / alıcı / kullanıcı adına, rakama, genel bir fiile benziyorsa ya da çok kısa /
 * uzunsa HİÇ gönderilmez (null). Yalnız öğretilen sözler gider; paketten gelen ("paket") sözler gitmez.
 */
final class ContributionCleaner {
    private ContributionCleaner() {
    }

    private static final Locale TR = new Locale("tr", "TR");
    static final int WORD_MIN = 4;
    static final int WORD_MAX = 20;
    static final int ROOT_MIN = 3;
    static final int ROOT_MAX = 12;

    /** Türkiye'nin 81 ili (sadeleşmiş). Listede olmayan il yazısı gönderilmez (katkı ilsiz gider). */
    private static final List<String> PROVINCES = Collections.unmodifiableList(Arrays.asList(
            "Adana", "Adıyaman", "Afyonkarahisar", "Ağrı", "Amasya", "Ankara", "Antalya", "Artvin", "Aydın", "Balıkesir",
            "Bilecik", "Bingöl", "Bitlis", "Bolu", "Burdur", "Bursa", "Çanakkale", "Çankırı", "Çorum", "Denizli",
            "Diyarbakır", "Edirne", "Elazığ", "Erzincan", "Erzurum", "Eskişehir", "Gaziantep", "Giresun", "Gümüşhane",
            "Hakkari", "Hatay", "Isparta", "Mersin", "İstanbul", "İzmir", "Kars", "Kastamonu", "Kayseri", "Kırklareli",
            "Kırşehir", "Kocaeli", "Konya", "Kütahya", "Malatya", "Manisa", "Kahramanmaraş", "Mardin", "Muğla", "Muş",
            "Nevşehir", "Niğde", "Ordu", "Rize", "Sakarya", "Samsun", "Siirt", "Sinop", "Sivas", "Tekirdağ", "Tokat",
            "Trabzon", "Tunceli", "Şanlıurfa", "Uşak", "Van", "Yozgat", "Zonguldak", "Aksaray", "Bayburt", "Karaman",
            "Kırıkkale", "Batman", "Şırnak", "Bartın", "Ardahan", "Iğdır", "Yalova", "Karabük", "Kilis", "Osmaniye", "Düzce"));

    /** Yaygın kişi adları (4 harf ve üstü; kısa adlar telefondaki adlarla yakalanır). Kök bunlarla başlıyorsa gönderilmez. */
    private static final Set<String> NAMES = new HashSet<>(Arrays.asList(
            "ahmet", "mehmet", "mustafa", "hasan", "huseyin", "ibrahim", "ismail", "osman", "yusuf", "murat", "omer",
            "ramazan", "halil", "suleyman", "abdullah", "mahmut", "recep", "fatih", "kadir", "emre", "hakan", "yasar",
            "kemal", "ismet", "orhan", "erkan", "serkan", "volkan", "cemal", "celal", "bekir", "musa", "salih", "selim",
            "sinan", "tarik", "veli", "yilmaz", "zeki", "nuri", "rasit", "sabri", "seref", "tahsin", "fevzi", "hamza",
            "ilyas", "idris", "cengiz", "metin", "nihat", "rifat", "sadik", "tuncay", "ugur", "fatma", "ayse", "emine",
            "hatice", "zeynep", "elif", "meryem", "sultan", "hulya", "havva", "zehra", "esra", "merve", "kubra", "hacer",
            "gulsen", "gulten", "hanife", "nazife", "sevim", "sevgi", "serife", "songul", "yasemin", "filiz", "derya",
            "aysel", "nermin", "nurten", "dilek", "kader", "leyla", "melek", "munevver", "naciye", "remziye", "safiye",
            "usta", "dayi", "amca", "teyze", "hala", "abla"));

    /** Gönderilebilecek katkı: yalnız bu yedi alan. */
    static final class Contribution {
        final String root;
        final String word;
        final String action;
        final String type;
        final String note;
        final String province;
        final String version;

        Contribution(String root, String word, String action, String type, String note, String province, String version) {
            this.root = root;
            this.word = word;
            this.action = action;
            this.type = type;
            this.note = note;
            this.province = province;
            this.version = version;
        }

        /** Söz-anlam anahtarı: aynısı bir kez gönderilir. */
        String key() {
            return root + "|" + action + "|" + type + "|" + note;
        }

        /** Gönderilecek alanlar (sıralı). Başka alan yok. */
        Map<String, String> fields() {
            Map<String, String> f = new LinkedHashMap<>();
            f.put("kok", root);
            f.put("soz", word);
            f.put("eylem", action);
            f.put("tur", type);
            f.put("not", note);
            f.put("il", province);
            f.put("surum", version);
            return f;
        }
    }

    /**
     * Temizler; gönderilemezse null. localNames: telefondaki bahçe, alıcı ve kullanıcı adları (yalnız burada,
     * karşılaştırmak için; dışarı çıkmaz). province: profildeki il; listede değilse "". version: uygulama sürümü.
     */
    static Contribution clean(LearnedTerms.Term t, List<String> localNames, String province, String version) {
        if (t == null || !LearnedTerms.TAUGHT.equals(t.kind)) {
            return null;
        }
        String word = word(t.word);
        if (word.isEmpty()) {
            return null;
        }
        String root = LearnedTerms.verbRoot(word);
        if (root.isEmpty() || !root.equals(t.term) || LearnedTerms.generic(root)) {
            return null;
        }
        if (root.length() < ROOT_MIN || root.length() > ROOT_MAX) {
            return null;
        }
        if (looksLikeName(word, root, localNames)) {
            return null;
        }
        LearnedTerms.Choice choice = LearnedTerms.choiceOf(t);
        if (choice == null) {
            return null;
        }
        return new Contribution(root, word, choice.action, choice.value, choice.note, province(province), version(version));
    }

    /**
     * Tek kelimelik söz: küçük harf, kesme işareti ve sonrası atılır; yalnız harf; çekimli fiil olmalı;
     * 4–20 harf. Uymazsa "".
     */
    static String word(String raw) {
        if (raw == null) {
            return "";
        }
        String w = raw.trim().toLowerCase(TR);
        int apo = firstApostrophe(w);
        if (apo >= 0) {
            w = w.substring(0, apo);
        }
        if (!w.matches("[a-zçğıöşüâî]+")) {
            return "";
        }
        if (w.length() < WORD_MIN || w.length() > WORD_MAX) {
            return "";
        }
        return LearnedTerms.verbRoot(w).isEmpty() ? "" : w;
    }

    private static int firstApostrophe(String w) {
        int best = -1;
        for (char c : new char[]{'\'', '’', '‘', '`', '´'}) {
            int i = w.indexOf(c);
            if (i >= 0 && (best < 0 || i < best)) {
                best = i;
            }
        }
        return best;
    }

    /** Kök ya da söz bir kişi / bahçe / alıcı adına benziyor mu (adla başlıyor ya da ad kökle başlıyor). */
    static boolean looksLikeName(String word, String root, List<String> localNames) {
        String w = TurkishText.fold(word);
        for (String name : NAMES) {
            if (w.startsWith(name)) {
                return true;
            }
        }
        if (localNames == null) {
            return false;
        }
        for (String full : localNames) {
            for (String part : TurkishText.fold(full == null ? "" : full).split("[^a-z]+")) {
                if (part.length() < 3) {
                    continue;
                }
                if (w.startsWith(part) || part.startsWith(root)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Profildeki il, 81 il listesindeyse doğru yazımıyla; değilse "" (katkı ilsiz gider). */
    static String province(String raw) {
        String f = TurkishText.fold(raw == null ? "" : raw).replaceAll("[^a-z]", "");
        if (f.isEmpty()) {
            return "";
        }
        for (String p : PROVINCES) {
            if (TurkishText.fold(p).replaceAll("[^a-z]", "").equals(f)) {
                return p;
            }
        }
        return "";
    }

    /** Uygulama sürümü: yalnız "1.6.0" biçimi; değilse "". */
    static String version(String raw) {
        String v = raw == null ? "" : raw.trim();
        return v.matches("\\d{1,3}(\\.\\d{1,3}){0,3}") ? v : "";
    }

    static List<String> provinces() {
        return new ArrayList<>(PROVINCES);
    }
}
