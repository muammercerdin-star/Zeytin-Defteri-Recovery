package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Asistanın anlamadığı cümleler listesi (saf, Android'siz, testli). Yalnız bu telefonda saklanır, hiçbir yere
 * gönderilmez. Kullanıcı "anlamadığın cümleleri göster" deyince ekranda gösterilir; ekran görüntüsünü geliştiriciye
 * kendisi gönderirse yeni kurallar eklenir. "Anlamadıklarını sil" ile temizlenir.
 */
final class AssistantMisses {
    static final int MAX = 30;
    /** Ekrana sığsın diye gösterilen en yeni cümle sayısı. */
    static final int SHOWN = 10;
    private AssistantMisses() {}

    /** Göster komutu: "anlamadığın cümleleri göster", "neleri anlamadın". */
    static boolean asksShow(String raw) {
        String s = TurkishText.normalize(raw == null ? "" : raw);
        return !asksClear(raw) && s.matches(".*\\b(anlamadig\\p{L}*|anlamadiklar\\p{L}*|neleri anlamadin|ne anlamadin)\\b.*");
    }

    /** Temizle komutu: "anlamadıklarını sil", "anlamadığın cümleleri temizle". */
    static boolean asksClear(String raw) {
        String s = TurkishText.normalize(raw == null ? "" : raw);
        return s.matches(".*\\b(anlamadig\\p{L}*|anlamadiklar\\p{L}*)\\b.*") && s.matches(".*\\b(sil\\p{L}*|temizle\\p{L}*|bosalt\\p{L}*|unut)\\b.*");
    }

    /** Saklanan biçim: her satır "gün\tcümle"; en yenisi başta. */
    static List<String[]> decode(String stored) {
        List<String[]> out = new ArrayList<>();
        if (stored == null || stored.isEmpty()) return out;
        for (String line : stored.split("\n")) {
            int tab = line.indexOf('\t');
            if (tab <= 0 || tab == line.length() - 1) continue;
            out.add(new String[]{line.substring(0, tab), line.substring(tab + 1)});
        }
        return out;
    }

    static String encode(List<String[]> items) {
        StringBuilder b = new StringBuilder();
        for (String[] it : items) { if (b.length() > 0) b.append('\n'); b.append(it[0]).append('\t').append(it[1]); }
        return b.toString();
    }

    /** Ekler: aynı cümle (büyük/küçük harf, noktalama farkı dahil) tekrar eklenmez, başa taşınır; en fazla MAX. */
    static String add(String stored, String raw, String day) {
        String text = raw == null ? "" : raw.replace('\n', ' ').replace('\t', ' ').trim();
        if (text.isEmpty() || text.length() > 300) return stored == null ? "" : stored;
        if (asksShow(text) || asksClear(text)) return stored == null ? "" : stored;
        String key = TurkishText.normalize(text);
        List<String[]> items = decode(stored);
        for (int i = items.size() - 1; i >= 0; i--) if (TurkishText.normalize(items.get(i)[1]).equals(key)) items.remove(i);
        items.add(0, new String[]{day, text});
        while (items.size() > MAX) items.remove(items.size() - 1);
        return encode(items);
    }

    // ============================================================ CÜMLE KAYDI (İP1)
    // Cümle bankasının telefondaki tohumu. Yalnız bu telefonda tutulur, hiçbir yere gönderilmez.
    // Yukarıdaki "anlamadıklarım" listesi aynen kalır; bu kayıt onun yanında ayrı bir anahtarla saklanır.
    // Satır biçimi: "gün \t tür \t cümle \t ayrıntı"; en yenisi başta, en fazla LOG_MAX satır.

    static final int LOG_MAX = 300;
    /** Asistanın bir eyleme çevirdiği cümle; ayrıntı = eylem ("ELEK", "ISLEM", "SORU_SPRAY"...). */
    static final String UNDERSTOOD = "anlasilan";
    /** Asistanın anlamadığı cümle; ayrıntı boş. */
    static final String MISSED = "anlasilmayan";
    /** Sesle doldurulan formda kullanıcının Kaydet'ten önce değiştirdiği alanlar; cümle = form adı. */
    static final String CORRECTED = "duzeltme";

    /** Kayıt satırı: {gün, tür, cümle, ayrıntı}. */
    static List<String[]> decodeLog(String stored) {
        List<String[]> out = new ArrayList<>();
        if (stored == null || stored.isEmpty()) {
            return out;
        }
        for (String line : stored.split("\n")) {
            String[] c = line.split("\t", -1);
            if (c.length != 4 || c[0].isEmpty() || c[2].isEmpty()) {
                continue;
            }
            out.add(c);
        }
        return out;
    }

    static String encodeLog(List<String[]> items) {
        StringBuilder b = new StringBuilder();
        for (String[] it : items) {
            if (b.length() > 0) {
                b.append('\n');
            }
            b.append(it[0]).append('\t').append(it[1]).append('\t').append(it[2]).append('\t').append(it[3]);
        }
        return b.toString();
    }

    /**
     * Kayda ekler. Anlaşılan ve anlaşılmayan cümle tekrar etmez, başa taşınır (son eylemiyle);
     * düzeltme her seferinde yeni satırdır. Komut cümleleri ve çok uzun metin girmez. En fazla LOG_MAX.
     */
    static String log(String stored, String kind, String raw, String detail, String day) {
        String keep = stored == null ? "" : stored;
        String text = clean(raw);
        if (text.isEmpty() || text.length() > 300) {
            return keep;
        }
        if (!UNDERSTOOD.equals(kind) && !MISSED.equals(kind) && !CORRECTED.equals(kind)) {
            return keep;
        }
        if (asksShow(text) || asksClear(text)) {
            return keep;
        }
        List<String[]> items = decodeLog(keep);
        if (!CORRECTED.equals(kind)) {
            String key = TurkishText.normalize(text);
            for (int i = items.size() - 1; i >= 0; i--) {
                boolean sameKind = items.get(i)[1].equals(kind);
                if (sameKind && TurkishText.normalize(items.get(i)[2]).equals(key)) {
                    items.remove(i);
                }
            }
        }
        items.add(0, new String[]{clean(day), kind, text, clean(detail)});
        while (items.size() > LOG_MAX) {
            items.remove(items.size() - 1);
        }
        return encodeLog(items);
    }

    /** "Anlamadıklarını sil": kayıttaki anlaşılmayan cümleler de silinir; anlaşılan ve düzeltme satırları kalır. */
    static String clearMissedFromLog(String stored) {
        List<String[]> items = decodeLog(stored);
        for (int i = items.size() - 1; i >= 0; i--) {
            if (MISSED.equals(items.get(i)[1])) {
                items.remove(i);
            }
        }
        return encodeLog(items);
    }

    /** Kayıttaki satır sayısı, türüne göre. */
    static int countLog(String stored, String kind) {
        int n = 0;
        for (String[] it : decodeLog(stored)) {
            if (it[1].equals(kind)) {
                n++;
            }
        }
        return n;
    }

    private static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace('\n', ' ').replace('\t', ' ').trim();
    }

    /** {okunacak kısa söz, ekranda gösterilecek liste}. */
    static String[] show(String stored) {
        List<String[]> items = decode(stored);
        if (items.isEmpty()) return new String[]{"Şimdilik anlamadığım bir cümle yok. Hepsini anladım, hehe!", "Şimdilik anlamadığım bir cümle yok."};
        int shown = Math.min(SHOWN, items.size());
        StringBuilder b = new StringBuilder("Anlamadığım son " + shown + " cümle" + (items.size() > shown ? " (toplam " + items.size() + ")" : "") + ":\n");
        for (int i = 0; i < shown; i++) b.append("\n• ").append(items.get(i)[1]).append("  (").append(items.get(i)[0]).append(')');
        b.append("\n\nBu ekranın görüntüsünü geliştiriciye gönderirsen bunları da öğrenirim. Silmek için “anlamadıklarını sil” de.");
        return new String[]{"Anlamadığım " + items.size() + " cümle var, ekrana yazdım. Ekran görüntüsünü gönderirsen bunları da öğrenirim.", b.toString()};
    }
}
