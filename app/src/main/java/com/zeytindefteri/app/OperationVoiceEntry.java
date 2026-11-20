package com.zeytindefteri.app;

import java.util.regex.Pattern;

/**
 * Asistana söylenen bahçe işi cümlesi → işlem formu (saf, Android'siz, testli).
 *
 * "Bugün Girelli'yi ilaçladım", "Dün 2 saat su verdim", "18 kilo gübre attık", "Mazot aldım 500 lira",
 * "3 işçi çalıştı 1500 lira". Tür, tarih (bugün / dün / evvelsi gün), miktar + birim ve tutar çıkarılır;
 * bahçe adını asistan kendi eşleştirir. Soru cümlesi ("Son ilaçlama ne zaman?") kayıt sayılmaz → null.
 * Kayıt hiçbir zaman kendiliğinden yapılmaz: form dolu açılır, kullanıcı Kaydet'e basar.
 */
public final class OperationVoiceEntry {
    /** OperationKinds.TYPES içindeki sıra. */
    public int type = -1;
    /** 0 bugün, -1 dün, -2 evvelsi gün. */
    public int dayOffset;
    public double quantity;
    public String unit = "";
    public double cost;
    /**
     * "Diğer" türündeki işin adı (İP5): "Ot biçme", "Aşılama". Kayıt notuna yazılır; diğer türlerde boş.
     * Listede tür olarak olmayan bu işler kararla "Diğer" + not olarak kaydedilir.
     */
    public String note = "";
    /**
     * Tutarın nasıl hesaplandığı (eğitim turu 1): "2 kişi × 1.500 TL". Kararın alanı değildir; formun notuna ve okunan
     * özete yazılır, çiftçi yanlışı görüp düzeltebilsin. Kişi başı söylenmediyse boş.
     */
    public String detail = "";

    /** "Diğer" türünün sırası (OperationKinds.TYPES). */
    static final int OTHER = 9;

    private static final Pattern QUESTION = Pattern.compile(
            "(^|\\s)(kac|ne|nedir|nasil|hangi|hangisi|mi|mu|misin|musun|zaman|ne zaman|kaldi|gecti)(\\s|$)");
    /** Geçmiş zaman / yapıldı sözü: kayıt cümlesi olduğunu gösterir. */
    private static final Pattern DONE = Pattern.compile(
            "\\b\\p{L}*(dim|dik|tim|tik|dum|duk|tum|tuk|dum|dim|di|ti|du|tu|ldi|ndi)\\b|\\b(yaptim|yaptik|yapildi|attim|attik|verdim|verdik|"
                    + "aldim|aldik|astim|astik|koydum|koyduk|doldurdum|calisti|calistilar|tuttum|tuttuk|bitti|bitirdim)\\b");

    private OperationVoiceEntry() {}

    /** Denetlenmiş dış kaynaktan (AssistantCloud) kayıt; tür OperationKinds.TYPES sırası. */
    static OperationVoiceEntry of(int type, int dayOffset, double quantity, String unit, double cost) {
        OperationVoiceEntry e = new OperationVoiceEntry();
        e.type = type; e.dayOffset = dayOffset; e.quantity = Math.max(0, quantity);
        e.unit = quantity > 0 && unit != null ? unit : ""; e.cost = Math.max(0, cost);
        return e;
    }

    /** Kayıt cümlesi değilse null. */
    public static OperationVoiceEntry parse(String raw) {
        if (raw == null || raw.contains("?")) return null;
        String s = TurkishText.normalize(raw);
        if (s.isEmpty() || QUESTION.matcher(s).find()) return null;
        int type = typeOf(s);
        if (type < 0) return null;
        if (!DONE.matcher(s).find() && !s.matches(".*\\b(bugun|dun|evvelsi|evelsi|onceki)\\b.*")) return null;
        OperationVoiceEntry e = new OperationVoiceEntry();
        e.type = type;
        if (type == OTHER) {
            e.note = noteOf(s);
        }
        if (s.matches(".*\\b(evvelsi|evvelki|evelsi|evelki|onceki gun|obur gun)\\b.*")) e.dayOffset = -2;
        else if (s.matches(".*\\bdun\\b.*")) e.dayOffset = -1;
        amounts(s.split(" "), e);
        return e;
    }

    /**
     * Sohbette verilen cevaptan sayılar (tür aranmaz): "500 gram" → miktar, "300 lira" → tutar,
     * birimsiz tek sayı ("500") → miktar (birim boş; soran taraf türün birimini kullanır).
     */
    public static OperationVoiceEntry parseAnswer(String raw) {
        OperationVoiceEntry e = new OperationVoiceEntry();
        String[] t = TurkishText.normalize(raw == null ? "" : raw).split(" ");
        amounts(t, e);
        if (e.quantity <= 0) {
            for (int i = 0; i < t.length; i++) {
                int[] end = new int[1];
                double v = TurkishText.number(t, i, end);
                if (Double.isNaN(v) || v <= 0) continue;
                if (e.cost > 0 && Math.abs(v - e.cost) < 0.001) { i = Math.max(i, end[0] - 1); continue; }   // "500 verdik" tutar oldu
                String next = end[0] < t.length ? t[end[0]] : "";
                if (!TurkishText.unit(next).equals("lira")) { e.quantity = v; e.unit = TurkishText.unit(next); break; }
                i = Math.max(i, end[0] - 1);
            }
        }
        return e;
    }

    /** Tuzak işi fiilleri: astık, kurduk, koyduk, değiştirdik, yeniledik, taktık. */
    private static final String TRAP_VERB =
            ".*\\b(as(tim|tik|ti|tilar|ildi)|kur(dum|duk|du)|koy(dum|duk|du)|degistir\\p{L}*|yenile\\p{L}*|tak(tim|tik|ti))\\b.*";

    /** Tür: söz kökünden. Sıra önemli: "ilaç" sulamadan önce ("ilaçlı su"), "mazot" nakliyeden önce. */
    static int typeOf(String s) {
        if (s.matches(".*\\b(ilac\\p{L}*|ilacla\\p{L}*|spreyle\\p{L}*|kukurt\\p{L}*|bordo bulamac\\p{L}*|bakir sulfat\\p{L}*|bakirli ilac\\p{L}*)\\b.*")) return 2;
        if (s.matches(".*\\b(gubre\\p{L}*)\\b.*")) return 1;
        // Sulama "mazot"tan önce: "motoru çalıştırıp can suyu verdik, mazotu 300 tuttu" sulamadır (mazot masrafıdır).
        if (s.matches(".*\\b(sula\\p{L}*|damlama\\p{L}*|hortum\\p{L}*|motopomp\\p{L}*|can suyu\\p{L}*|salma su\\p{L}*)\\b.*")
                || s.matches(".*\\b(su|suyu|suyunu|sulari|suyumuzu) (ver\\p{L}*|sal\\p{L}*|ac\\p{L}*|bas\\p{L}*|akit\\p{L}*|cektik|cektim)\\b.*")) return 0;
        if (s.matches(".*\\b(buda\\p{L}*)\\b.*")) return 3;
        if (s.matches(".*\\b(tuzak\\p{L}*)\\b.*")) return 4;
        // "Sinek tuzağı astık": "tuzağı" sadeleşince "tuzagi"; yalnız tuzak işi fiiliyle ("tuzağı kaydı" soru kalır).
        if (s.matches(".*\\btuzag\\p{L}*\\b.*") && s.matches(TRAP_VERB)) return 4;
        if (s.matches(".*\\b(surdum|surduk|surduruldu|capala\\p{L}*|freze\\p{L}*|toprak isle\\p{L}*|toprak islemesi|diskaro\\p{L}*|kultivator\\p{L}*)\\b.*")) return 5;
        if (s.matches(".*\\b(isci\\p{L}*|amele\\p{L}*|yevmiye\\p{L}*|iscilik)\\b.*")) return 6;
        if (s.matches(".*\\b(mazot\\p{L}*|yakit\\p{L}*|motorin\\p{L}*)\\b.*")) return 7;
        if (s.matches(".*\\b(nakliye\\p{L}*|tasidim|tasidik|tasittim|kamyon\\p{L}*|traktorle tasi\\p{L}*)\\b.*")) return 8;
        if (!noteOf(s).isEmpty()) return OTHER;
        return -1;
    }

    /**
     * Listede tür olarak olmayan işin adı (İP5, "Diğer" + not). Yalnız açık söyleyişler: "ot biçtik", "otları biçtik",
     * "aşı yaptık", "kalem aşısı yaptık". "Tırpanladık", "aşıladık" gibi çiftçinin kendi sözleri burada yok:
     * onlar öğretme sorusuyla bir kez öğretilir. Bulunmazsa "".
     */
    static String noteOf(String s) {
        if (s.matches(".*\\bot(lar|lari|u)? bic\\p{L}*.*")) return "Ot biçme";
        if (s.matches(".*\\b(asi|asisi|asilari) (yaptim|yaptik|yaptilar|yapildi|vurduk|vurdum)\\b.*")) return "Aşılama";
        return "";
    }

    /** Kişi başı ücret sözü (eğitim turu 1): "günlüğü 1500", "yevmiyesi 1200", "tanesi 1000". */
    private static final String PER_HEAD = "gunlugu|gunlugune|gunlukleri|yevmiyesi|yevmiyesine|yevmiyeleri|tanesi|tanesine";
    /** Toplam tutar sözü: "toplam 3000", "hepsine 3000" (kişi başı sayılmaz). */
    private static final String TOTAL = "toplam|toplamda|hepsine|hepsi|ikisine|ucune|hepsini";

    /** "2 saat", "18 kilo", "500 ml", "25 litre", "3 işçi", "500 lira". */
    private static void amounts(String[] t, OperationVoiceEntry e) {
        double perHead = 0;
        double days = 0;
        boolean total = false;
        for (int i = 0; i < t.length; i++) {
            int[] end = new int[1];
            double v = TurkishText.number(t, i, end);
            if (Double.isNaN(v) || v <= 0) continue;
            String next = end[0] < t.length ? t[end[0]] : "";
            String u = TurkishText.unit(next);
            // "15 sinek tuzağı": sayı ile tuzak arasında "sinek" var
            if (u.isEmpty() && next.equals("sinek") && end[0] + 1 < t.length && t[end[0] + 1].startsWith("tuza")) {
                u = "adet";
            }
            // "yevmiye kişi başı 1200", "adam başına 1000 lira": işçi sayısıyla çarpılır
            if (i >= 1 && t[i - 1].matches("basi|basina|basindan") && i >= 2 && t[i - 2].matches("kisi|adam|isci|nefer")) { perHead = v; i = Math.max(i, end[0] - 1); continue; }
            // Eğitim turu 1: "günlüğü 1500" kişi başıdır; "toplam / hepsine 3000" toplamdır.
            if (i >= 1 && t[i - 1].matches(PER_HEAD) && !u.equals("kisi") && !u.equals("kişi")) {
                perHead = v;
                i = Math.max(i, end[0] - 1);
                continue;
            }
            if (i >= 1 && t[i - 1].matches(TOTAL) && (u.isEmpty() || u.equals("lira"))) {
                e.cost = v;
                total = true;
                i = Math.max(i, end[0] - 1);
                continue;
            }
            if (u.equals("gün") && e.quantity > 0) {
                days = v;
            }
            if (u.equals("lira")) { if (e.cost <= 0) e.cost = v; }
            else if (!u.isEmpty() && e.quantity <= 0) { e.quantity = v; e.unit = u; }
            else if (u.isEmpty() && e.cost <= 0 && costBySpeech(t, i, end[0])) e.cost = v;   // "elektrik 350 tuttu"
            i = Math.max(i, end[0] - 1);
        }
        if (perHead > 0 && e.cost <= 0 && !total) {
            boolean persons = e.quantity > 0 && e.unit.equals("kişi");
            double times = persons ? e.quantity : 1;
            double dayCount = days > 0 ? days : 1;
            e.cost = perHead * times * dayCount;
            if (persons) {
                String perDay = days > 0 ? " × " + ElekVoiceDialog.num(days) + " gün" : "";
                e.detail = ElekVoiceDialog.num(e.quantity) + " kişi" + perDay + " × " + lira(perHead);
            }
        }
    }

    /** "Lira" denmeden söylenen tutar: "350 tuttu", "350'ye patladı", "elektrik de 350", "mazot parası 500". */
    private static final String COST_VERB = "tuttu|tuttur\\p{L}*|odedim|odedik|odendi|patladi|gitti|harcadim|harcadik|harcandi|bayildim|bayildik|saydim|saydik|verdim|verdik|cikti|ettirdi|yedi";
    private static final String COST_NOUN = "elektrik\\p{L}*|fatura\\p{L}*|masraf\\p{L}*|para\\p{L}*|parasi|ucret\\p{L}*|yevmiye\\p{L}*|tutar\\p{L}*|fiyat\\p{L}*|mazot\\p{L}*|motorin\\p{L}*|benzin\\p{L}*|nakliye\\p{L}*|kira\\p{L}*";
    private static boolean costBySpeech(String[] t, int start, int end) {
        String next = end < t.length ? t[end] : "";
        if (next.matches(COST_VERB)) return true;
        for (int k = start - 1, seen = 0; k >= 0 && seen < 2; k--, seen++) {
            if (t[k].matches("de|da|ise|icin")) { seen--; continue; }
            if (t[k].matches(COST_NOUN)) return true;
        }
        return false;
    }

    /** "1500" → "1.500 TL". */
    private static String lira(double v) {
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(new java.util.Locale("tr", "TR"));
        nf.setMaximumFractionDigits(2);
        return nf.format(v) + " TL";
    }

    /** Formun notu: "Diğer" işinin adı ve tutarın hesabı ("Ot biçme", "2 kişi × 1.500 TL"). */
    public String formNote() {
        if (detail.isEmpty()) {
            return note;
        }
        return note.isEmpty() ? detail : note + ", " + detail;
    }

    /** Okuma: "ilaçlama, bugün, 500 ml, 300 lira". */
    public String summary() {
        String name = note.isEmpty() ? OperationKinds.TYPES[type] : note;
        StringBuilder b = new StringBuilder(name.toLowerCase(new java.util.Locale("tr", "TR")));
        b.append(", ").append(dayOffset == 0 ? "bugün" : dayOffset == -1 ? "dün" : "evvelsi gün");
        if (quantity > 0) b.append(", ").append(ElekVoiceDialog.num(quantity)).append(' ').append(unit);
        if (cost > 0) b.append(", ").append(ElekVoiceDialog.num(cost)).append(" lira");
        if (!detail.isEmpty()) {
            b.append(" (").append(detail).append(')');
        }
        return b.toString();
    }
}
