package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Tanecik'in altyazı öbekleri (saf, Android'siz; test edilir).
 * Söz en çok 3 kelimelik öbeklere bölünür; noktalama öbeği bitirir. Her öbeğin sözdeki başlangıç yeri tutulur,
 * böylece telefonun sesi "şu kelimedeyim" dediğinde doğru öbek gösterilir.
 */
public final class TanecikCaptions {
    private TanecikCaptions() {}

    static final int MAX_WORDS = 3;

    public static final class Chunk {
        public final String text;
        public final int start;

        Chunk(String text, int start) { this.text = text; this.start = start; }
    }

    /** Sesin okuyacağı biçim: "%70" → "yüzde 70", "TL" → "lira", "km/s" → "kilometre". Altyazı da bunu gösterir. */
    public static String spoken(String text) {
        if (text == null) return "";
        return text.replace("%", "yüzde ").replace(" TL", " lira").replace("km/s", "kilometre")
                .replace("Zzz…", "").replace("“", "\"").replace("”", "\"").trim();
    }

    public static List<Chunk> chunks(String s) {
        List<Chunk> out = new ArrayList<>();
        if (s == null) return out;
        int i = 0, n = s.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(s.charAt(i))) i++;
            if (i >= n) break;
            int start = i, words = 0, end = i;
            while (i < n && words < MAX_WORDS) {
                while (i < n && !Character.isWhitespace(s.charAt(i))) i++;
                words++;
                end = i;
                char last = s.charAt(i - 1);
                if (".!?:;,…".indexOf(last) >= 0) break;
                while (i < n && Character.isWhitespace(s.charAt(i))) i++;
            }
            out.add(new Chunk(s.substring(start, end), start));
            i = end;
        }
        return out;
    }

    /** offset'in düştüğü öbek (son başlangıcı offset'ten küçük ya da eşit olan). */
    public static int indexAt(List<Chunk> chunks, int offset) {
        int at = 0;
        for (int k = 0; k < chunks.size(); k++) if (chunks.get(k).start <= offset) at = k;
        return at;
    }

    /** Kelime bildirimi olmayan telefonda öbeğin ekranda kalma süresi. */
    public static long chunkMs(String chunk, float rate) {
        long ms = 140 + (chunk == null ? 0 : chunk.length()) * 62L;
        if (chunk != null && !chunk.isEmpty() && ".!?…".indexOf(chunk.charAt(chunk.length() - 1)) >= 0) ms += 220;
        return Math.round(ms / Math.max(0.5f, rate));
    }
}
