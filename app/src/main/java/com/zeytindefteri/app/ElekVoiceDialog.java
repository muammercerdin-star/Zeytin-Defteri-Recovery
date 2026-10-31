package com.zeytindefteri.app;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Elek formunda sesli konuşma (saf, Android'siz). Her söylenen cümleye bir cevap ve bir iş döner:
 * kilolar forma yazılır, "bitti" denince toplam okunur ve onay sorulur, "evet" denince kaydedilir.
 *
 * Kurallar: aynı sınıf yeniden söylenirse eski değerin yerine geçer (düzeltme). "Geri al" son değişikliği
 * geri alır. Toplam ve tutar okunmadan ve "evet" denmeden kayıt yapılmaz.
 */
public final class ElekVoiceDialog {
    public enum Action { NONE, FILL, SAVE, CANCEL }
    public enum Phase { LISTENING, ASK_BUYER, CONFIRM, DONE }

    public static final class Reply {
        public final String speech; public final Action action; public final boolean listen;
        Reply(String speech, Action action, boolean listen) { this.speech = speech; this.action = action; this.listen = listen; }
    }

    private static final String[] SPOKEN = {"double", "1 numara", "2 numara", "3 numara", "4 numara", "yağlık"};
    private static final Locale TR = new Locale("tr", "TR");

    public final double[] kg = new double[6];
    public Boolean sold;           // null: formdaki ayar kalır
    public String buyer = "";
    private final double[] prices;
    private boolean soldByDefault;
    private final java.util.List<String> buyers;
    /** Söylenen alıcı listede yoksa true: kayıtta yeni alıcı olarak eklenir. */
    public boolean buyerNew;
    private boolean buyerAsked;
    private java.util.List<String> buyerChoices = new java.util.ArrayList<>();
    private Phase phase = Phase.LISTENING;
    private final Deque<double[]> undo = new ArrayDeque<>();
    private int fills = 0, misses = 0;

    /** start: formdaki mevcut kilolar; prices: sınıf fiyatları (TL/kg); soldByDefault: formdaki "Bu zeytini sattım". */
    public ElekVoiceDialog(double[] start, double[] prices, boolean soldByDefault) {
        this(start, prices, soldByDefault, null);
    }

    /** buyers: alıcı listesindeki adlar ("Kime sattın?" cevabı bunlarla eşleştirilir). */
    public ElekVoiceDialog(double[] start, double[] prices, boolean soldByDefault, java.util.List<String> buyers) {
        this.buyers = buyers == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(buyers);
        for (int i = 0; i < 6 && start != null && i < start.length; i++) kg[i] = Math.max(0, start[i]);
        this.prices = prices == null ? new double[6] : prices.clone();
        this.soldByDefault = soldByDefault;
    }

    public Phase phase() { return phase; }

    public static String greeting() {
        return "Dinliyorum, kilolarını söyle.";
    }

    public Reply handle(String raw) {
        String s = ElekVoiceEntry.normalize(raw == null ? "" : raw);
        if (s.isEmpty()) return new Reply("Duyamadım. Kilolarını söyleyebilirsin.", Action.NONE, true);

        if (has(s, "vazgec", "iptal", "kapat", "bosver", "cikis")) {
            phase = Phase.DONE;
            return new Reply("Tamam, kaydetmeden bıraktım. Yazdıklarım formda duruyor.", Action.CANCEL, false);
        }
        if (phase == Phase.ASK_BUYER) return answerBuyer(raw, s);
        if (phase == Phase.CONFIRM) {
            ElekVoiceEntry e = ElekVoiceEntry.parse(forParse(raw));
            if (e != null) { apply(e, raw); return askConfirm("Düzelttim. "); }
            // Önce olumsuz: "doğru değil", "tamam değil", "evet ama bekle" KAYDETMEZ.
            if (has(s, "hayir", "duzelt", "bekle", "dur", "degil", "yanlis", "kaydetme", "olmaz")) {
                phase = Phase.LISTENING;
                return new Reply("Tamam, düzeltmeyi söyle. Örneğin: 3 numara 100 olacak.", Action.NONE, true);
            }
            if (yes(s)) {
                phase = Phase.DONE;
                return new Reply(isSold() ? "Kaydediyorum, fişi hazırlıyorum." : "Kaydediyorum.", Action.SAVE, false);
            }
            return new Reply("Kaydetmem için evet, düzeltmek için hayır de.", Action.NONE, true);
        }
        if (has(s, "geri al", "geri")) {
            if (undo.isEmpty()) return new Reply("Geri alınacak bir şey yok.", Action.NONE, true);
            double[] prev = undo.pop(); System.arraycopy(prev, 0, kg, 0, 6);
            return new Reply("Son söylediğini geri aldım. " + listLine(), Action.FILL, true);
        }
        if (has(s, "oku", "ne yazdin", "neler yazdin", "tekrarla", "ozet")) {
            return new Reply(total() > 0 ? summary() : "Henüz kilo yazmadım.", Action.NONE, true);
        }
        // "3 numarayı sil / sıfırla"
        if (has(s, "sil", "sifirla", "kaldir")) {
            boolean[] g = ElekVoiceEntry.gradesIn(raw); boolean any = false;
            double[] before = kg.clone();
            for (int i = 0; i < 6; i++) if (g[i] && kg[i] > 0) { kg[i] = 0; any = true; }
            if (any) { undo.push(before); return new Reply("Sildim. " + listLine(), Action.FILL, true); }
            return new Reply("Hangi sınıfı sileyim? Örneğin: 3 numarayı sil.", Action.NONE, true);
        }
        ElekVoiceEntry e = ElekVoiceEntry.parse(forParse(raw));
        Boolean soldSaid = ElekVoiceEntry.soldIn(raw);
        String buyerSaid = ElekVoiceEntry.buyerIn(raw);
        boolean finish = has(s, "bitti", "hepsi bu", "bu kadar", "tamamdir", "kaydet", "tamam", "bitir");
        StringBuilder said = new StringBuilder();
        if (e != null) said.append(apply(e, raw));
        else {
            if (soldSaid != null) sold = soldSaid;
            if (!buyerSaid.isEmpty()) { setBuyer(buyerSaid); sold = true; }
        }
        if (finish) {
            if (total() <= 0) return new Reply("Henüz kilo söylemedin. Örneğin: 3 numara 120 kilo.", Action.NONE, true);
            String lead = said.length() > 0 ? "Tamam, " + said + ". " : "";
            if (isSold() && buyer.isEmpty() && (!buyerAsked || !buyerChoices.isEmpty())) {
                buyerAsked = true; phase = Phase.ASK_BUYER;
                return new Reply(lead + (buyerChoices.isEmpty() ? "Kime sattın?" : which(buyerChoices)), Action.FILL, true);
            }
            return askConfirm(lead);
        }
        if (e != null) {
            misses = 0; fills++;
            return new Reply(fillReply(said.toString()), Action.FILL, true);
        }
        if (soldSaid != null || !buyerSaid.isEmpty()) {
            String who = buyer.isEmpty() ? "" : buyer + " adına ";
            return new Reply(Boolean.FALSE.equals(sold) ? "Tamam, satış olarak kaydetmeyeceğim." : "Tamam, " + who + "satış olarak kaydedeceğim. Başka kilo var mı?", Action.FILL, true);
        }
        misses++;
        if (questionLike(s)) return new Reply("Burada sadece kilo yazıyorum. Örneğin: 3 numara 120 kilo. Bitince bitti de.", Action.NONE, true);
        return new Reply(misses >= 2 ? "Yine anlayamadım. Sınıfı ve kiloyu söyle: double 20, 1 numara 90 gibi."
                : "Anlayamadım. Örneğin: 3 numara 120 kilo diyebilirsin.", Action.NONE, true);
    }

    /** Konuşmada "değiştir / güncelle" düzeltme demektir ("3 numarayı 100 olarak değiştir"); ayrıştırıcı bu sözleri görmesin. */
    static String forParse(String raw) {
        return raw == null ? "" : raw.replaceAll("(?iu)\\b(de[gğ]i[sş]tir|g[uü]ncelle)\\p{L}*", " ");
    }

    /**
     * Formla eşitle (her cevaptan önce): kullanıcı formda elle kilo yazdıysa / sildiyse ya da kaydettiyse (form boşaldı)
     * asistan formdakini esas alır; fiyat ve "sattım" ayarı da formdan gelir. Böylece elle yazılan silinmez,
     * elle kaydedilen kayıt sesle ikinci kez kaydedilemez.
     */
    public void sync(double[] formKg, double[] formPrices, boolean formSold) {
        if (formPrices != null) for (int i = 0; i < 6 && i < formPrices.length; i++) prices[i] = formPrices[i];
        soldByDefault = formSold;
        if (formKg == null) return;
        boolean differ = false;
        for (int i = 0; i < 6 && i < formKg.length; i++) if (Math.abs(Math.max(0, formKg[i]) - kg[i]) > 1e-6) differ = true;
        if (!differ) return;
        for (int i = 0; i < 6 && i < formKg.length; i++) kg[i] = Math.max(0, formKg[i]);
        undo.clear();
        if (phase == Phase.CONFIRM || phase == Phase.ASK_BUYER) phase = Phase.LISTENING;   // değişen form yeniden onaylanmalı
    }

    /** Kısa, değişen cevaplar: robot gibi her seferinde aynı cümle tekrarlanmasın. */
    private String fillReply(String said) {
        if (fills == 1) return "Tamam, " + said + " kilo. Diğerlerini bekliyorum.";
        if (fills == 2) return "Aldım, " + said + ". Başka?";
        String[] lead = {"Yazdım, ", "Tamam, ", "Aldım, "};
        String head = lead[(fills - 3) % lead.length] + said + ".";
        return fills == 3 ? head + " Bitince bitti demen yeterli." : head + " Başka?";
    }

    /** Söylenen sınıfların yeni değeri eskisinin yerine geçer. Dönen metin: "3 numara 120" (sesli okumada sade). */
    private String apply(ElekVoiceEntry e, String raw) {
        undo.push(kg.clone());
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (e.kg[i] <= 0) continue;
            kg[i] = e.kg[i];
            if (b.length() > 0) b.append(", ");
            b.append(SPOKEN[i]).append(' ').append(num(e.kg[i]));
        }
        if (e.sold != null) sold = e.sold;
        if (!e.buyer.isEmpty()) { setBuyer(e.buyer); sold = true; }
        return b.toString();
    }

    /** "Kime sattın?" cevabı: listedeyse onu, birden çoksa hangisi, yoksa yeni alıcı. */
    private Reply answerBuyer(String raw, String s) {
        if (!buyerChoices.isEmpty()) {
            String picked = ElekVoiceBuyer.pick(raw, buyerChoices);
            buyerChoices = new java.util.ArrayList<>();
            if (picked != null) { buyer = picked; buyerNew = false; return askConfirm("Tamam, " + picked + ". "); }
        }
        if (has(s, "gec", "yok", "bilmiyorum", "sonra", "kimse", "bos", "bosver", "atla")) {
            buyer = ""; buyerNew = false;
            return askConfirm("Alıcısız kaydediyorum. ");
        }
        if (Boolean.FALSE.equals(ElekVoiceEntry.soldIn(raw))) { sold = false; return askConfirm("Tamam, satış olmadan. "); }
        ElekVoiceBuyer.Match m = ElekVoiceBuyer.match(raw, buyers);
        switch (m.kind) {
            case EXISTING: buyer = m.name; buyerNew = false; return askConfirm("Tamam, " + m.name + ". ");
            case AMBIGUOUS: buyerChoices = m.candidates; return new Reply(which(m.candidates), Action.NONE, true);
            case NEW: buyer = m.name; buyerNew = true;
                return askConfirm(m.name + " listende yok, yeni alıcı olarak ekleyeceğim. ");
            default: return new Reply("Adını anlayamadım. Alıcının adını söyle, yoksa geç de.", Action.NONE, true);
        }
    }

    private static String which(java.util.List<String> c) {
        StringBuilder q = new StringBuilder("Hangisi? ");
        for (int i = 0; i < c.size() && i < 3; i++) q.append(i == 0 ? "" : ", ").append(c.get(i)).append(" mı");
        return q.append('?').toString();
    }

    /** Cümlenin içinde geçen alıcı ("Ahmet'e sattım"): listedekiyle aynı yazılsın. */
    private void setBuyer(String said) {
        ElekVoiceBuyer.Match m = ElekVoiceBuyer.match(said, buyers);
        if (m.kind == ElekVoiceBuyer.Kind.EXISTING) { buyer = m.name; buyerNew = false; }
        else if (m.kind == ElekVoiceBuyer.Kind.AMBIGUOUS) { buyer = ""; buyerChoices = m.candidates; }
        else { buyer = m.name.isEmpty() ? said : m.name; buyerNew = true; }
        buyerAsked = !buyer.isEmpty();
    }

    private Reply askConfirm(String prefix) {
        phase = Phase.CONFIRM;
        return new Reply(prefix + summary() + (isSold() ? " Kaydedip fişi hazırlayayım mı?" : " Kaydedeyim mi?"), Action.FILL, true);
    }

    public boolean isSold() { return sold != null ? sold : soldByDefault; }

    public double total() { double t = 0; for (double v : kg) t += v; return t; }

    public double amount() { double t = 0; for (int i = 0; i < 6; i++) t += kg[i] * Math.max(0, prices[i]); return Math.round(t); }

    /** "3 numara 120, 2 numara 80. Toplam 200 kilo, 12.400 lira. Alıcı Ahmet." */
    public String summary() {
        StringBuilder b = new StringBuilder(listLine());
        b.append(" Toplam ").append(num(total())).append(" kilo");
        if (amount() > 0) b.append(", ").append(money(amount())).append(" lira");
        b.append('.');
        if (isSold() && !buyer.isEmpty()) b.append(" Alıcı ").append(buyer).append(buyerNew ? ", yeni alıcı." : ".");
        return b.toString();
    }

    private String listLine() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (kg[i] <= 0) continue;
            if (b.length() > 0) b.append(", ");
            b.append(SPOKEN[i]).append(' ').append(num(kg[i]));
        }
        if (b.length() == 0) return "Formda kilo yok.";
        return Character.toUpperCase(b.charAt(0)) + b.substring(1) + ".";
    }

    private static boolean yes(String s) {
        return has(s, "evet", "kaydet", "olur", "onayla", "onayliyorum", "tamam", "dogru", "aynen", "he", "hi hi", "evt");
    }

    private static boolean questionLike(String s) {
        return s.matches(".*\\b(kac|ne|nedir|nasil|hangi|mi|mu|misin)\\b.*");
    }

    private static boolean has(String s, String... words) {
        for (String w : words) if ((" " + s + " ").contains(" " + w + " ")) return true;
        return false;
    }

    static String num(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(TR, "%.1f", v);
    }

    static String money(double v) {
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(TR);
        nf.setMaximumFractionDigits(0);
        return nf.format(v);
    }
}
