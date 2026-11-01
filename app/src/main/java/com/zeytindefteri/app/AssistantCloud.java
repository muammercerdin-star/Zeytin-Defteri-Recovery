package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * "Zor cümle" yardımcısı (saf, Android'siz, testli): kuralların anlamadığı bir bahçe işi cümlesini internetteki
 * modele (cloud/zeytin-ai, Gemma 4) sordurur ve gelen cevabı CÜMLEYLE KARŞILAŞTIRARAK denetler.
 *
 * Telefon denemesinde görülen hatalara karşı (2 Kasım 2026):
 * - Bahçe uydurma ("bahçeye ilaç vurduk" → "Girelli"): bahçe adı cümlede geçmiyorsa atılır, kullanıcıya sorulur.
 * - Sayı alanına söz ("miktar":"kisi") ya da uydurma sayı: her sayı cümlede geçmeli (kişi başı çarpımı hariç).
 * - Yanlış işlem ("damlama" → işçilik): kurallar cümlede işlem sözü bulursa kuralınki geçerli.
 * - Düzeltmede eski değeri yazma: düzeltmeler hiç modele gitmez (OperationVoiceDialog yapar).
 * Sonuç her zaman okunur; "evet" denmeden kayıt yapılmaz.
 */
final class AssistantCloud {
    private AssistantCloud() {}

    /** Modelin işlem adları, OperationKinds.TYPES sırasıyla. */
    static final String[] OPS = {"sulama", "gubre", "ilaclama", "budama", "tuzak", "toprak_isleme", "iscilik", "mazot", "nakliye"};

    /** Denetimden geçmiş sonuç. */
    static final class Result {
        final OperationVoiceEntry entry;
        /** gardens listesindeki sıra; -1 cümlede bahçe yok (sorulur). */
        final int garden;
        Result(OperationVoiceEntry entry, int garden) { this.entry = entry; this.garden = garden; }
    }

    // ===================================================================== HANGİ CÜMLE GİDER?

    /**
     * Yalnız "yapılmış bir iş" gibi duran cümle gider: soru, sohbet, komut gitmez. Rakam ya da geçmiş zaman
     * fiili olmalı ("çalıştırdık", "saldık"). Persona, elek, hasat, işlem kuralları bundan önce denenmiştir.
     */
    static boolean eligible(String raw) { return eligible(raw, null); }

    /** Bahçe işi sözleri: sohbet cümlesi ("bugün pazara gittim") boşuna internete gitmesin. */
    private static final String FARM = "zeytin\\p{L}*|bahce\\p{L}*|agac\\p{L}*|motor\\p{L}*|su|suyu|ilac\\p{L}*|gubre\\p{L}*|traktor\\p{L}*|isci\\p{L}*|"
            + "mazot\\p{L}*|motorin\\p{L}*|buda\\p{L}*|tarla\\p{L}*|dal\\p{L}*|toprak\\p{L}*|surdu\\p{L}*|capa\\p{L}*|ot\\p{L}*|tuzak\\p{L}*|damla\\p{L}*|hortum\\p{L}*|"
            + "kuyu\\p{L}*|elektri\\p{L}*|yevmiye\\p{L}*|usta\\p{L}*|amele\\p{L}*|kukurt\\p{L}*|bakir\\p{L}*|nakliye\\p{L}*|kamyon\\p{L}*|sefer\\p{L}*|pompa\\p{L}*";

    static boolean eligible(String raw, List<String> gardens) {
        if (raw == null) return false;
        String t = raw.trim();
        if (t.isEmpty() || t.length() > 300 || t.contains("?")) return false;
        String s = ElekVoiceEntry.normalize(t);
        if (s.split(" ").length < 3) return false;
        if (s.matches(".*(^|\\s)(kac|ne|neden|niye|nasil|hangi|hangisi|mi|mu|misin|musun|midir|mudur|kim|nerede)(\\s|$).*")) return false;
        if (s.matches(".*\\b(ac|goster|sil|kapat|git|getir|hatirlat|anlamadig\\p{L}*|meydan\\p{L}*)\\b.*")) return false;
        boolean number = s.matches(".*\\d.*") || hasSpokenNumber(s);
        boolean past = s.matches(".*\\b\\p{L}{2,}(dik|tik|duk|tuk|dim|tim|dum|tum|di|ti|du|tu)\\b.*");
        boolean farm = s.matches(".*\\b(" + FARM + ")\\b.*");
        if (!farm && gardens != null) for (String g : gardens) if (g != null && mentioned(s, g)) farm = true;
        boolean unitWord = s.matches(".*\\b(lira|tl|kilo|saat|litre|tane|sefer|agac|donum|dekar)\\p{L}*\\b.*");
        return (past && (farm || number)) || (number && (farm || unitWord));
    }

    private static boolean hasSpokenNumber(String s) {
        String[] t = s.split(" ");
        for (int i = 0; i < t.length; i++) {
            int[] end = new int[1];
            if (!Double.isNaN(ElekVoiceEntry.number(t, i, end))) return true;
        }
        return false;
    }

    /** Sunucuya giden gövde: yalnız cümle ve bahçe adları (kişi adı, borç, para geçmişi gitmez). */
    static String requestBody(String raw, List<String> gardens) {
        try {
            JSONObject o = new JSONObject();
            o.put("text", raw == null ? "" : raw.replace('\n', ' ').trim());
            JSONArray g = new JSONArray();
            if (gardens != null) for (String name : gardens) if (name != null && !name.trim().isEmpty() && g.length() < 20) g.put(name.trim());
            o.put("gardens", g);
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    // ===================================================================== CEVABI DENETLE

    /** Sunucu cevabı + cümle + bahçeler → denetlenmiş sonuç; kullanılamazsa null (kurallarla devam). */
    static Result accept(String response, String raw, List<String> gardens) {
        JSONObject r;
        try {
            JSONObject o = new JSONObject(response == null ? "" : response);
            if (!o.optBoolean("ok", false)) return null;
            r = o.optJSONObject("result");
        } catch (Exception e) {
            return null;
        }
        if (r == null) return null;
        String s = ElekVoiceEntry.normalize(raw == null ? "" : raw);
        if (s.isEmpty()) return null;

        // ---- işlem: kurallar cümlede bir işlem sözü bulduysa o geçerli ("damlama" → sulama)
        int type = OperationVoiceEntry.typeOf(s);
        if (type < 0) {
            String islem = r.optString("islem", "");
            for (int i = 0; i < OPS.length; i++) if (OPS[i].equals(islem)) type = i;
        }
        if (type < 0) return null;

        // ---- gün: yalnız cümledeki gün sözü (model ne derse desin)
        int day = s.matches(".*\\b(evvelsi|evvelki|evelsi|evelki|onceki gun|obur gun)\\b.*") ? -2
                : s.matches(".*\\bdun\\b.*") ? -1 : 0;

        // ---- sayılar: cümlede geçmeyen sayı kabul edilmez
        List<Double> said = numbers(s);
        double qty = number(r, "miktar");
        if (qty > 0 && !contains(said, qty)) qty = 0;
        double cost = number(r, "tutar");
        if (cost > 0 && !contains(said, cost) && !(perHead(s) && isProduct(said, cost))) cost = 0;
        if (qty > 0 && cost > 0 && same(qty, cost) && countOf(said, qty) < 2) cost = 0;   // aynı sayı iki alana yazılmış
        String unit = qty > 0 ? unit(r.optString("birim", "")) : "";

        // ---- bahçe: listedeki ad ve cümlede geçiyor olmalı
        int garden = -1;
        String g = r.optString("bahce", "");
        if (gardens != null && !g.isEmpty() && !"null".equals(g)) {
            for (int i = 0; i < gardens.size(); i++) {
                if (gardens.get(i) != null && gardens.get(i).trim().equalsIgnoreCase(g.trim()) && mentioned(s, gardens.get(i))) { garden = i; break; }
            }
        }
        if (garden < 0 && gardens != null) {
            // Model bulamasa da cümlede adı açıkça geçen tek bahçe varsa onu al
            int found = -1, count = 0;
            for (int i = 0; i < gardens.size(); i++) if (gardens.get(i) != null && mentioned(s, gardens.get(i))) { found = i; count++; }
            if (count == 1) garden = found;
        }
        return new Result(OperationVoiceEntry.of(type, day, qty, unit, cost), garden);
    }

    private static double number(JSONObject r, String key) {
        Object v = r.opt(key);
        if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            return d > 0 && d < 1e9 ? d : 0;
        }
        return 0;   // "kisi" gibi sözler, null, metin: yok sayılır
    }

    /** Cümledeki tüm sayılar ("üç", "1500'e", "2.500", "12,5"). */
    static List<Double> numbers(String s) {
        List<Double> out = new ArrayList<>();
        String[] t = s.split(" ");
        for (int i = 0; i < t.length; i++) {
            int[] end = new int[1];
            double v = ElekVoiceEntry.number(t, i, end);
            if (Double.isNaN(v) || v <= 0) continue;
            out.add(v);
            i = Math.max(i, end[0] - 1);
        }
        return out;
    }

    private static boolean contains(List<Double> list, double v) { return countOf(list, v) > 0; }
    private static int countOf(List<Double> list, double v) { int c = 0; for (double d : list) if (same(d, v)) c++; return c; }
    private static boolean same(double a, double b) { return Math.abs(a - b) < 0.001; }
    private static boolean perHead(String s) { return s.matches(".*\\b(basi|basina|basindan|tanesi|tanesine)\\b.*"); }
    private static boolean isProduct(List<Double> said, double v) {
        for (int i = 0; i < said.size(); i++) for (int j = 0; j < said.size(); j++) if (i != j && same(said.get(i) * said.get(j), v)) return true;
        return false;
    }

    /** Bahçe adı cümlede geçiyor mu ("Girelide", "kuyudibine", "Kuyu dibinde" gibi ekli ve bitişik yazılışlar dahil). */
    static boolean mentioned(String normalizedSentence, String garden) {
        String g = ElekVoiceEntry.normalize(garden == null ? "" : garden).replace(" ", "");
        if (g.length() < 3) return false;
        if (g.length() <= 5) {
            for (String w : normalizedSentence.split(" ")) if (w.startsWith(g)) return true;
            return false;
        }
        return normalizedSentence.replace(" ", "").contains(g.substring(0, 5));
    }

    /** Modelin birimi → uygulamanın birimi. */
    static String unit(String u) {
        switch (u == null ? "" : u.trim().toLowerCase(Locale.ROOT)) {
            case "saat": return "saat";
            case "kg": return "kg";
            case "g": return "g";
            case "l": return "L";
            case "ml": return "ml";
            case "agac": return "ağaç";
            case "adet": return "adet";
            case "dekar": return "dekar";
            case "kisi": return "kişi";
            case "sefer": return "sefer";
            case "km": return "km";
            case "ton": return "ton";
            default: return "";
        }
    }

    // ===================================================================== İZİN (tek seferlik soru)

    /** İlk zor cümlede bir kez sorulur. */
    static final String ASK = "Bunu tam anlayamadım. İnternetteki yardımcıya sorayım mı? Yalnız bu cümle ve bahçe adların gider, hiçbir yerde saklanmaz. Evet mi, hayır mı?";

    /** İzin cevabı: 1 evet, 0 hayır, -1 başka bir şey söylendi. */
    static int consent(String raw) {
        String s = ElekVoiceEntry.normalize(raw == null ? "" : raw);
        if (s.matches("(evet|olur|tamam|sor|hadi|tabi|tabii|peki|aynen|he|sor bakalim|evet sor)( .*)?") && !s.matches(".*\\b(hayir|istemem|sorma)\\b.*")) return 1;
        if (s.matches("(hayir|istemem|sorma|gerek yok|yok|olmaz|kalsin|bosver)( .*)?")) return 0;
        return -1;
    }

    /** Sesli ayar: "internet yardımcısını kapat / aç". 1 aç, 0 kapat, -1 değil. */
    static int toggleCommand(String raw) {
        String s = ElekVoiceEntry.normalize(raw == null ? "" : raw);
        if (!s.matches(".*\\binternet\\p{L}*\\b.*") || !s.matches(".*\\b(yardimci\\p{L}*|sorma\\p{L}*|asistan\\p{L}*)\\b.*")) return -1;
        if (s.matches(".*\\b(kapat\\p{L}*|kapa|istemiyorum|durdur)\\b.*") || s.matches(".*\\binternete sorma\\b.*")) return 0;
        if (s.matches(".*\\b(ac|acil\\p{L}*|baslat|aktif\\p{L}*|kullan)\\b.*")) return 1;
        return -1;
    }
}
