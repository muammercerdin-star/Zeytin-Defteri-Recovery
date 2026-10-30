package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tezgâh soruları (saf, Android'siz; sesle ya da yazarak). Yalnız okur, hiçbir kaydı değiştirmez.
 *
 * Anladıkları: bugünün (ya da dünün) alımı · bir üreticinin borcu ve bu sezonki alımı · üreticilere toplam borç
 * · tüccar / cari alacak · kasa (aracıyla) · son alım · yardım. Üretici adı Türkçe eklerle de tanınır
 * ("Volkan'ın", "volkanın", "Volkan Afşar'a"). Aynı ada birden fazla üretici uyarsa hangisi olduğu sorulur.
 */
public final class TezgahVoice {
    public enum Kind { DAY, PRODUCER, DEBT_TOTAL, TRADER, CRATES, LAST, HELP, UNKNOWN }

    public static final class Answer {
        public final Kind kind;
        public final String title, text;

        Answer(Kind kind, String title, String text) { this.kind = kind; this.title = title; this.text = text; }

        /** Sesli okuma için: "₺" → "lira", "kg" → "kilo". */
        public String speech() {
            return text.replace(" ₺", " lira").replace("₺", " lira").replace(" kg", " kilo")
                    .replace("·", ",").replace("\n", ". ");
        }
    }

    /** Sorulabilecek örnekler (yardım ve boş ekran). */
    public static final String[] EXAMPLES = {
            "Bugün kaç kilo aldım?", "Volkan'ın borcu ne kadar?", "Üreticilere toplam borcum ne?",
            "Tüccardan alacağım ne kadar?", "Kasa durumu ne?", "Son alım kimdendi?"};

    /** Soru kelimeleri: ad sanılmasın ("kaç alım" içindeki "alım" → "Ali" değil). */
    private static final java.util.Set<String> STOP = new java.util.HashSet<>(java.util.Arrays.asList(
            "alim", "alimi", "alimlar", "alimlari", "aldim", "aldik", "alacak", "alacagim", "borc", "borcu", "borcum",
            "borclu", "borcun", "bugun", "dun", "kac", "kilo", "kiloyu", "toplam", "ne", "kadar", "tuccar", "tuccardan",
            "kasa", "kasalar", "son", "fis", "fisi", "uretici", "ureticiler", "ureticilere", "odedim", "odeme", "var",
            "mi", "mu", "nedir", "neydi", "ver", "soyle", "goster", "benim", "bana", "ona", "kimden", "kime", "tutar",
            "para", "lira", "gunluk", "durum", "durumu", "cari", "araci", "hesap", "hesabi", "sezon", "bu", "yil"));

    private TezgahVoice() {}

    // ------------------------------------------------------------------ anlama

    /** Küçük harf, Türkçe harfsiz, yalnız harf/rakam ve tek boşluk (asistanla aynı kural). */
    public static String norm(String s) {
        if (s == null) return "";
        s = s.toLowerCase(new java.util.Locale("tr", "TR")).replace('ı', 'i');
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static boolean has(String s, String... words) {
        for (String w : words) if (s.contains(w)) return true;
        return false;
    }

    /** Sorudaki üretici adı: alım defterindeki adlarla eşleşenler (en iyi eşleşme; birden çoksa hepsi). */
    public static List<String> matchSellers(String question, List<TezgahPurchases.Purchase> all) {
        String q = " " + norm(question) + " ";
        String[] words = q.trim().split(" ");
        Map<String, String> names = new LinkedHashMap<>();
        for (TezgahPurchases.Purchase p : all) {
            String key = norm(p.seller);
            if (key.isEmpty() || key.equals(norm(TezgahWizardFlow.WALK_IN))) continue;
            if (!names.containsKey(key)) names.put(key, p.seller.trim());
        }
        int best = 0;
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : names.entrySet()) {
            String[] parts = e.getKey().split(" ");
            int score = 0;
            boolean first = false;
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (part.length() < 2) continue;
                if (part.length() < 3 && parts.length > 1) continue;
                for (String w : words) {
                    if (STOP.contains(w)) continue;
                    // "volkanin", "volkana", "afsardan": ad + Türkçe ek (en çok 4 harf)
                    if (w.startsWith(part) && w.length() - part.length() <= 4) {
                        score++;
                        if (i == 0) first = true;
                        break;
                    }
                }
            }
            if (!first && score < 2) continue; // yalnız soyadla eşleşme sayılmaz (iki kelime değilse)
            if (score > best) { best = score; out.clear(); }
            if (score == best && score > 0) out.add(e.getValue());
        }
        return out;
    }

    public static Kind kind(String question, List<TezgahPurchases.Purchase> all) {
        String s = norm(question);
        if (s.isEmpty()) return Kind.HELP;
        if (has(s, "ne sorabilirim", "neler sorabilirim", "yardim", "ne biliyorsun")) return Kind.HELP;
        if (has(s, "kasa")) return Kind.CRATES;
        if (has(s, "tuccar", "araci", "cari", "alacag", "fabrika")) return Kind.TRADER;
        if (!matchSellers(question, all).isEmpty()) return Kind.PRODUCER;
        if (has(s, "borc") && (has(s, "toplam", "uretici", "kime", "kimlere", "borcum", "borclar") )) return Kind.DEBT_TOTAL;
        if (has(s, "son alim", "en son kim", "son kimden", "son fis")) return Kind.LAST;
        if (has(s, "bugun", "dun", "gunluk", "kac kilo", "ne kadar aldim", "kac alim", "kac uretici", "kac kisi", "toplam aldim"))
            return Kind.DAY;
        if (has(s, "borc")) return Kind.DEBT_TOTAL;
        return Kind.UNKNOWN;
    }

    // ------------------------------------------------------------------ cevap

    /**
     * @param today  yyyy-MM-dd
     * @param yesterday yyyy-MM-dd
     */
    public static Answer answer(String question, List<TezgahPurchases.Purchase> all, List<TezgahLedger.Entry> ledger,
                                List<TezgahCrates.Move> crates, String today, String yesterday) {
        Kind k = kind(question, all);
        String s = norm(question);
        switch (k) {
            case DAY: return day(all, s.contains("dun") && !s.contains("bugun") ? yesterday : today,
                    s.contains("dun") && !s.contains("bugun") ? "Dün" : "Bugün");
            case PRODUCER: return producer(question, all, today);
            case DEBT_TOTAL: return debtTotal(all);
            case TRADER: return trader(ledger);
            case CRATES: return crates(crates, today);
            case LAST: return last(all);
            case HELP: return help("Tezgâh hakkında şunları sorabilirsin:");
            default: return help("Bunu anlayamadım. Şöyle sorabilirsin:");
        }
    }

    static Answer help(String lead) {
        StringBuilder b = new StringBuilder(lead);
        for (String e : EXAMPLES) b.append("\n• ").append(e);
        return new Answer(Kind.HELP, "Ne sorabilirim?", b.toString());
    }

    static Answer day(List<TezgahPurchases.Purchase> all, String date, String label) {
        int n = 0;
        double kg = 0, gross = 0, paid = 0;
        List<String> people = new ArrayList<>();
        for (TezgahPurchases.Purchase p : all) {
            if (!date.equals(p.date)) continue;
            n++;
            kg += p.kg;
            gross += p.gross;
            paid += Math.min(p.gross, p.paid);
            String who = p.seller == null || p.seller.trim().isEmpty() ? "Adsız" : p.seller.trim();
            if (!people.contains(who)) people.add(who);
        }
        if (n == 0) return new Answer(Kind.DAY, label + "ün alımı", label + " henüz alım yok.");
        double debt = Math.max(0, gross - paid);
        String text = label + " " + n + " alım yaptın: " + TezgahModel.kg(kg) + ", tutar " + TezgahModel.tl(gross) + ".\n"
                + (debt > 0.5 ? "Ödenen " + TezgahModel.tl(paid) + ", borç " + TezgahModel.tl(debt) + "." : "Hepsi ödendi.");
        if (kg > 0) text += "\nOrtalama " + TezgahModel.money(gross / kg) + "/kg.";
        return new Answer(Kind.DAY, label + "ün alımı", text);
    }

    /**
     * Zeytin sezonu 1 Eylül'de başlar (hasat Eylül–Ocak arası): Ekim 2026 → 2026-09-01, Mart 2027 → 2026-09-01.
     * Borç sezondan bağımsızdır (eski sezondan kalan borç da borçtur); "bu sezon" toplamı yalnız bu sezonun alımları.
     */
    static String seasonStart(String today) {
        if (today == null || today.length() < 7) return "";
        try {
            int y = Integer.parseInt(today.substring(0, 4)), m = Integer.parseInt(today.substring(5, 7));
            return (m >= 9 ? y : y - 1) + "-09-01";
        } catch (NumberFormatException e) {
            return "";
        }
    }

    static Answer producer(String question, List<TezgahPurchases.Purchase> all, String today) {
        List<String> names = matchSellers(question, all);
        if (names.isEmpty()) return new Answer(Kind.PRODUCER, "Üretici", "Bu adla bir üretici bulamadım.");
        if (names.size() > 1) {
            StringBuilder b = new StringBuilder("Bu adla birden fazla üretici var:");
            for (String n : names) b.append("\n• ").append(n);
            b.append("\nSoyadıyla birlikte sor.");
            return new Answer(Kind.PRODUCER, "Hangisi?", b.toString());
        }
        String name = names.get(0);
        String key = norm(name);
        String from = seasonStart(today);
        int n = 0, open = 0, older = 0, oldOpen = 0;
        double kg = 0, gross = 0, debt = 0, oldDebt = 0;
        String lastDate = "";
        for (TezgahPurchases.Purchase p : all) {
            if (!norm(p.seller).equals(key)) continue;
            boolean thisSeason = from.isEmpty() || (p.date != null && p.date.compareTo(from) >= 0);
            if (thisSeason) { n++; kg += p.kg; gross += p.gross; } else older++;
            if (p.remaining() > 0.5) {
                open++;
                debt += p.remaining();
                if (!thisSeason) { oldOpen++; oldDebt += p.remaining(); }
            }
            if (p.date != null && p.date.compareTo(lastDate) > 0) lastDate = p.date;
        }
        String text = (debt > 0.5 ? name + ": toplam borcun " + TezgahModel.tl(debt) + " (" + open + " fiş)."
                : name + ": borcun yok.")
                + (oldDebt > 0.5 ? "\nBunun " + TezgahModel.tl(oldDebt) + " kadarı geçen sezonlardan (" + oldOpen + " fiş)." : "")
                + (n > 0 ? "\nBu sezon " + n + " alım: " + TezgahModel.kg(kg) + ", " + TezgahModel.tl(gross) + "."
                        : "\nBu sezon henüz alım yok" + (older > 0 ? "; önceki sezonlarda " + older + " alım var." : "."))
                + (lastDate.isEmpty() ? "" : "\nSon alım: " + SalePayStatus.dayMonth(lastDate) + ".");
        return new Answer(Kind.PRODUCER, name, text);
    }

    static Answer debtTotal(List<TezgahPurchases.Purchase> all) {
        Map<String, double[]> by = new LinkedHashMap<>();
        Map<String, String> label = new LinkedHashMap<>();
        double total = 0;
        for (TezgahPurchases.Purchase p : all) {
            double r = p.remaining();
            if (r <= 0.5) continue;
            total += r;
            String key = norm(p.seller);
            String who = p.seller == null || p.seller.trim().isEmpty() ? "Adsız" : p.seller.trim();
            if (key.isEmpty()) key = "adsiz";
            double[] v = by.get(key);
            if (v == null) { v = new double[1]; by.put(key, v); label.put(key, who); }
            v[0] += r;
        }
        if (total <= 0.5) return new Answer(Kind.DEBT_TOTAL, "Üreticilere borç", "Üreticilere borcun yok.");
        List<Map.Entry<String, double[]>> list = new ArrayList<>(by.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]));
        StringBuilder b = new StringBuilder("Üreticilere toplam " + TezgahModel.tl(total) + " borcun var (" + list.size() + " kişi).");
        for (int i = 0; i < list.size() && i < 5; i++) {
            b.append("\n• ").append(label.get(list.get(i).getKey())).append(": ").append(TezgahModel.tl(list.get(i).getValue()[0]));
        }
        if (list.size() > 5) b.append("\n… ve ").append(list.size() - 5).append(" kişi daha.");
        return new Answer(Kind.DEBT_TOTAL, "Üreticilere borç", b.toString());
    }

    static Answer trader(List<TezgahLedger.Entry> ledger) {
        List<TezgahLedger.Party> parties = TezgahLedger.parties(ledger);
        double[] t = TezgahLedger.totals(ledger);
        if (parties.isEmpty()) return new Answer(Kind.TRADER, "Cari hesap", "Cari hesapta henüz kayıt yok.");
        StringBuilder b = new StringBuilder();
        if (t[0] > 0.5) b.append("Alıcılardan toplam ").append(TezgahModel.tl(t[0])).append(" alacağın var.");
        else b.append("Alıcılardan alacağın yok.");
        if (t[1] > 0.5) b.append("\nAlıcılara ").append(TezgahModel.tl(t[1])).append(" borcun var.");
        int shown = 0;
        for (TezgahLedger.Party p : parties) {
            if (Math.abs(p.balance()) < 0.5 || shown >= 5) continue;
            shown++;
            b.append("\n• ").append(TezgahLedger.balanceLine(p.name, p.balance()));
        }
        return new Answer(Kind.TRADER, "Cari hesap", b.toString());
    }

    static Answer crates(List<TezgahCrates.Move> moves, String today) {
        if (moves.isEmpty()) return new Answer(Kind.CRATES, "Kasa", "Kasa defterinde henüz kayıt yok.");
        TezgahCrates.Day d = TezgahCrates.day(moves, null, today);
        int h = d.held();
        String text = "Bugün " + d.in + " kasa geldi, " + d.out + " kasa gitti.\n"
                + (h > 0 ? "Aracının " + h + " kasası sende." : h < 0 ? "Senin " + (-h) + " kasan aracıda." : "Kasa hesabı kapalı.")
                + (d.carried != 0 ? "\nDünden devreden: " + d.carried + "." : "");
        return new Answer(Kind.CRATES, "Kasa", text);
    }

    static Answer last(List<TezgahPurchases.Purchase> all) {
        TezgahPurchases.Purchase last = null;
        for (TezgahPurchases.Purchase p : all) if (last == null || p.createdAt > last.createdAt) last = p;
        if (last == null) return new Answer(Kind.LAST, "Son alım", "Henüz alım yok.");
        String who = last.seller == null || last.seller.trim().isEmpty() ? "Adsız üretici" : last.seller.trim();
        String text = "Son alım: " + who + " · " + TezgahModel.kg(last.kg) + ", " + TezgahModel.tl(last.gross) + "."
                + (last.remaining() > 0.5 ? "\nBorç " + TezgahModel.tl(last.remaining()) + "." : "\nÖdendi.");
        return new Answer(Kind.LAST, "Son alım", text);
    }
}
