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
        String s = ElekVoiceEntry.normalize(raw == null ? "" : raw);
        return !asksClear(raw) && s.matches(".*\\b(anlamadig\\p{L}*|anlamadiklar\\p{L}*|neleri anlamadin|ne anlamadin)\\b.*");
    }

    /** Temizle komutu: "anlamadıklarını sil", "anlamadığın cümleleri temizle". */
    static boolean asksClear(String raw) {
        String s = ElekVoiceEntry.normalize(raw == null ? "" : raw);
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
        String key = ElekVoiceEntry.normalize(text);
        List<String[]> items = decode(stored);
        for (int i = items.size() - 1; i >= 0; i--) if (ElekVoiceEntry.normalize(items.get(i)[1]).equals(key)) items.remove(i);
        items.add(0, new String[]{day, text});
        while (items.size() > MAX) items.remove(items.size() - 1);
        return encode(items);
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
