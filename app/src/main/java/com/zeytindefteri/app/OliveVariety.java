package com.zeytindefteri.app;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Zeytin cinsleri (saf, Android'siz). Bahçe formunda listeden seçilir; eski serbest yazılar eş anlamlılarla
 * tek isme indirilir: "Trilye", "trilya", "Gemlik siyahı" → "Gemlik"; "Edremit", "Ada zeytini" → "Ayvalık".
 * Ligde fiyat kıyası aynı cins + aynı numara ile yapılır; cinsi farklı yazan üreticiler ayrışmasın diye.
 */
public final class OliveVariety {
    private OliveVariety() {}

    public static final String OTHER = "Diğer";

    /** Seçim listesi (yaygınlık sırasıyla); en sonda "Diğer" serbest yazı için. */
    public static final List<String> CHOICES = Collections.unmodifiableList(Arrays.asList(
            "Gemlik", "Ayvalık", "Memecik", "Domat", "Uslu", "Erkence", "Çelebi", "Nizip Yağlık",
            "Kilis Yağlık", "Halhalı", "Sarı Ulak", "Saurani", "Edincik Su", "Çakır", "Manzanilla", OTHER));

    /** Eş anlamlı → asıl isim. Anahtarlar sade biçimde (küçük harf, Türkçe harfler sadeleşmiş, boşluksuz). */
    private static final String[][] SYNONYMS = {
            {"gemlik", "Gemlik"}, {"trilye", "Gemlik"}, {"trilya", "Gemlik"}, {"tirilye", "Gemlik"},
            {"kaplik", "Gemlik"}, {"gemliksiyahi", "Gemlik"}, {"gemlikzeytini", "Gemlik"}, {"siyahgemlik", "Gemlik"},
            {"ayvalik", "Ayvalik"}, {"edremit", "Ayvalik"}, {"edremityaglik", "Ayvalik"}, {"adazeytini", "Ayvalik"},
            {"midilli", "Ayvalik"}, {"ayvalikyaglik", "Ayvalik"},
            {"memecik", "Memecik"}, {"tasarasi", "Memecik"}, {"asiyeli", "Memecik"}, {"tekir", "Memecik"},
            {"aydinyaglik", "Memecik"},
            {"domat", "Domat"}, {"akhisardomat", "Domat"},
            {"uslu", "Uslu"}, {"akhisaruslu", "Uslu"},
            {"erkence", "Erkence"}, {"izmirsofralik", "Erkence"},
            {"celebi", "Celebi"},
            {"nizipyaglik", "Nizip Yaglik"}, {"nizip", "Nizip Yaglik"},
            {"kilisyaglik", "Kilis Yaglik"}, {"kilis", "Kilis Yaglik"},
            {"halhali", "Halhali"},
            {"sariulak", "Sari Ulak"},
            {"saurani", "Saurani"},
            {"edinciksu", "Edincik Su"}, {"edincik", "Edincik Su"},
            {"cakir", "Cakir"},
            {"manzanilla", "Manzanilla"}, {"manzanillo", "Manzanilla"},
    };

    /** Sade anahtar: küçük harf, ı/ş/ğ/ü/ö/ç sadeleşir, harf-rakam dışı atılır. */
    static String key(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase(new Locale("tr", "TR")).replace('ı', 'i');
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.replaceAll("[^a-z0-9]", "");
    }

    /**
     * Serbest yazıyı asıl cins adına çevirir. Tanınmayan yazı olduğu gibi (baş harfleri büyük, en çok 30
     * karakter) döner; boşsa "". "Gemlik / Trilye" gibi iki isim yazılmışsa ilk tanınan kullanılır.
     */
    public static String normalize(String raw) {
        String k = key(raw);
        if (k.isEmpty()) return "";
        for (String[] pair : SYNONYMS) if (pair[0].equals(k)) return display(pair[1]);
        // "Gemlik zeytini 2" / "trilye siyah" gibi: bilinen bir eş anlamlıyla başlıyorsa.
        for (String part : raw.trim().split("[\\s/,;\\-]+")) {
            String pk = key(part);
            if (pk.length() < 4) continue;
            for (String[] pair : SYNONYMS) if (pair[0].equals(pk)) return display(pair[1]);
        }
        String t = raw.trim().replaceAll("\\s+", " ");
        if (t.length() > 30) t = t.substring(0, 30).trim();
        return titleCase(t);
    }

    /** Ligde ve Supabase'de kullanılan anahtar ("gemlik", "ayvalik"); boşsa "". */
    public static String apiKey(String raw) {
        return key(normalize(raw));
    }

    /** Listede gösterim adı: anahtar → "Ayvalık", "Çelebi". */
    private static String display(String ascii) {
        for (String c : CHOICES) if (key(c).equals(key(ascii))) return c;
        return titleCase(ascii);
    }

    /** Ligde cins anahtarından görünen ad ("ayvalik" → "Ayvalık"); bilinmeyen anahtar baş harfi büyük. */
    public static String label(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) return "Belirtilmemiş";
        for (String c : CHOICES) if (key(c).equals(apiKey)) return c;
        return titleCase(apiKey);
    }

    private static String titleCase(String s) {
        StringBuilder b = new StringBuilder(s.length());
        boolean up = true;
        for (char ch : s.toCharArray()) {
            b.append(up ? Character.toUpperCase(ch == 'i' ? 'İ' : ch) : ch);
            up = Character.isWhitespace(ch);
        }
        return b.toString();
    }
}
