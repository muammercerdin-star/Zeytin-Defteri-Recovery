package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tezgâhta sesli tartım konuşması (saf, Android'siz, testli).
 *
 * Tüccar kasaları kantara koyar, kiloyu söyler; asistan kısa cevap verir: "15 kilo 1 kasa" → "13 net".
 * Kasa söylenmediyse tahmini kasayı da söyler: "55" → "2 kasa, 51 net". Ayrıntı (dara) ekranda yazar.
 * Kasa söylenmezse kiloya göre tahmin edilir (bir kasa ~25 kg zeytin + dara); "55, 3 kasa" denirse o kullanılır,
 * yalnız "3 kasa" denirse son tartımın kasası düzeltilir. "yok" sonraki numaraya geçer, "1 numara 40" o numaraya yazar.
 * "bitti" → toplam, "Kimden aldın?", ödeme (tamamı / bir kısmı / sonra), okuma ve "evet" olmadan asla kaydetmez.
 */
public final class TezgahVoiceDialog {
    public enum Phase { WEIGH, KASA_CHECK, SELLER, SELLER_PICK, PAY, PAY_AMOUNT, CONFIRM, DONE }
    public enum Action { NONE, ADD, UNDO, SELLER, SAVE, CANCEL }

    public static final class Reply {
        public final String speech; public final Action action; public final boolean listen;
        /** ADD: eklenecek tartım. removeIndex ≥ 0 ise önce o sıradaki tartım silinir (kasa düzeltme, geri al). */
        public int grade = -1, kasa = -1, removeIndex = -1; public double kg;
        Reply(String speech, Action action, boolean listen) { this.speech = speech; this.action = action; this.listen = listen; }
    }

    private static final Locale TR = new Locale("tr", "TR");
    private final TezgahModel model;
    private final List<String> sellers;
    private final List<Integer> added = new ArrayList<>();
    private final List<Double> addedNet = new ArrayList<>();   // net söylenen tartımın neti, değilse -1
    private double committingNet = -1;   // bu konuşmada eklenen tartımların sırası (geri al için)
    private Phase phase = Phase.WEIGH;
    private int grade;
    public String seller = "";
    public boolean sellerNew;
    private List<String> choices = new ArrayList<>();
    private int pay = ElekSaleModel.PAY_ALL;
    private double paid;
    // kasa uyarısında bekleyen tartım
    private int pGrade, pKasa, pSuggested; private double pKg, pNet = -1;

    /** model: ekrandaki tezgahın kopyası (fiyatlar, dara, önceki tartımlar). seller: ekranda yazılı satan (boş olabilir). */
    public TezgahVoiceDialog(TezgahModel model, String seller, List<String> sellers) {
        this.model = model;
        this.seller = seller == null ? "" : seller.trim();
        this.sellers = sellers == null ? new ArrayList<>() : sellers;
        List<TezgahModel.Weighing> items = model.items();
        this.grade = items.isEmpty() ? 0 : items.get(items.size() - 1).grade;
    }

    public Phase phase() { return phase; }
    public int grade() { return grade; }
    public double paid() { return paid; }
    public TezgahModel model() { return model; }

    public String greeting() {
        if (model.isEmpty()) return label(grade) + " kantarda. Kaç kilo?";
        return "Devam ediyoruz. " + label(grade) + ", kaç kilo?";
    }

    /** Kasa söylenmezse: bir kasa ~25 kg zeytin + dara; kiloya uymuyorsa modelin önerisi. "15" → 1, "55" → 2. */
    public static int estimateKasa(double kg, double tare) {
        int k = Math.max(1, (int) Math.round(kg / (TezgahModel.TYPICAL_NET_PER_KASA + Math.max(0, tare))));
        int s = TezgahModel.suggestedKasa(k, kg, tare);
        return s > 0 ? s : k;
    }

    // ------------------------------------------------------------ ayrıştırma

    /** Söylenen tartım: sınıf (-1 yok), kantar kilosu (NaN yok), kasa (-1 söylenmedi, 0 kasasız). */
    public static final class Said {
        public int grade = -1, kasa = -1; public double kg = Double.NaN;
        /** "net 13" / "13 net": söylenen kilo zaten net, dara düşülmez. */
        public boolean net;
    }

    public static Said parse(String raw) {
        Said out = new Said();
        String[] t = ElekVoiceEntry.normalize(raw == null ? "" : raw).split(" ");
        for (int i = 0; i < t.length; i++) {
            String w = t[i];
            if (w.isEmpty()) continue;
            int g = ElekVoiceEntry.gradeWord(w);
            if (g >= 0) { out.grade = g; continue; }
            java.util.regex.Matcher glued = java.util.regex.Pattern.compile("^([1-4])(numara\\p{L}*|no|nolu)$").matcher(w);
            if (glued.find()) { out.grade = Integer.parseInt(glued.group(1)); continue; }
            if (w.equals("kasasiz") || w.equals("dokme")) { out.kasa = 0; continue; }
            if (w.equals("net") || w.equals("neti")) { out.net = true; continue; }
            int[] end = new int[1];
            double v = ElekVoiceEntry.number(t, i, end);
            if (Double.isNaN(v)) continue;
            String next = end[0] < t.length ? t[end[0]] : "";
            String after = end[0] + 1 < t.length ? t[end[0] + 1] : "";
            boolean whole = v == Math.floor(v);
            if (end[0] == i + 1 && whole && v >= 1 && v <= 4
                    && (next.startsWith("numara") || next.equals("no") || next.equals("nolu"))) {
                out.grade = (int) v; i = end[0];
            } else if (whole && (next.startsWith("kasa") || (next.equals("tane") && after.startsWith("kasa")))) {
                out.kasa = (int) v; i = next.equals("tane") ? end[0] + 1 : end[0];
            } else {
                if (Double.isNaN(out.kg)) out.kg = v;
                i = end[0] - 1;
            }
        }
        return out;
    }

    private static boolean has(String s, String words) { return s.matches(".*\\b(" + words + ")\\b.*"); }

    // ------------------------------------------------------------ konuşma

    public Reply handle(String raw) {
        String s = ElekVoiceEntry.normalize(raw == null ? "" : raw);
        if (s.isEmpty()) return ask("Duyamadım. " + prompt());
        if (has(s, "vazgec|vazgectim|kapat|iptal")) {
            phase = Phase.DONE;
            return new Reply(model.isEmpty() ? "Tamam, kapattım." : "Tamam, kapattım. Tartımlar ekranda duruyor.", Action.CANCEL, false);
        }
        switch (phase) {
            case KASA_CHECK: return kasaCheck(s, raw);
            case SELLER: return answerSeller(raw, s);
            case SELLER_PICK: return pickSeller(raw, s);
            case PAY: return answerPay(raw, s);
            case PAY_AMOUNT: return answerAmount(raw, s);
            case CONFIRM: return confirm(s);
            case DONE: return new Reply("", Action.NONE, false);
            default: return weigh(raw, s);
        }
    }

    private Reply weigh(String raw, String s) {
        if (has(s, "geri al|sonuncuyu sil|son tartimi sil|yanlis oldu")) return undo();
        if (has(s, "oku|toplam|ne kadar|kac oldu|ne oldu")) return ask(readout() + " " + prompt());
        if (has(s, "bitti|bitir|bitirelim|hepsi bu|kaydet|fis")) return finish();
        Said said = parse(raw);
        if (!Double.isNaN(said.kg) && said.net) return addNet(said.grade >= 0 ? said.grade : grade, said.kasa, said.kg, said.grade >= 0 && said.grade != grade);
        if (!Double.isNaN(said.kg)) return add(said.grade >= 0 ? said.grade : grade, said.kasa, said.kg, said.grade >= 0 && said.grade != grade);
        if (said.grade >= 0) { grade = said.grade; return ask(label(grade) + ", kaç kilo?"); }
        if (said.kasa >= 0) return fixKasa(said.kasa);
        if (has(s, "yok|gec|sonraki|atla|bos|hayir|cikmadi|tamam|baska yok|devam")) {
            if (grade >= TezgahModel.GRADES.length - 1) return finish();
            grade++;
            return ask(label(grade) + ", kaç kilo?");
        }
        return ask("Anlamadım. Kantardaki kiloyu söyle, yoksa yok de.");
    }

    private Reply add(int g, int kasa, double kg, boolean named) {
        double tare = model.tare();
        boolean estimated = kasa < 0;
        int k = estimated ? estimateKasa(kg, tare) : kasa;
        String err = TezgahModel.addError(k, kg, tare);
        if (!err.isEmpty()) return ask(err.replace(" kg", " kilo") + " Kiloyu yeniden söyle.");
        if (!estimated) {
            int sug = TezgahModel.suggestedKasa(k, kg, tare);
            if (sug > 0) {
                pGrade = g; pKasa = k; pKg = kg; pSuggested = sug; pNet = -1; phase = Phase.KASA_CHECK;
                return ask(k + " kasada " + num(kg) + " kilo " + (sug < k ? "az" : "fazla") + ". " + sug + " kasa yapayım mı?");
            }
        }
        return commit(g, k, kg, (named ? label(g) + ", " : "") + (estimated ? kasaWord(k) + ", " : ""), -1);
    }

    /**
     * Net söylendi ("net 13"): dara düşülmez. Tezgah kantar kilosu sakladığı için kantar = net + kasa × dara yazılır,
     * böylece kayıttaki net tam söylenen kilo olur, kasa sayısı da toplamda kalır.
     * Kasa söylenmediyse netten tahmin edilir (bir kasa ~25 kg, en fazla 30 kg).
     */
    private Reply addNet(int g, int kasa, double net, boolean named) {
        if (!(net > 0)) return ask("Net kiloyu yeniden söyle.");
        double tare = model.tare();
        boolean estimated = kasa < 0;
        int k = kasa;
        if (estimated) {
            k = Math.max(1, (int) Math.round(net / TezgahModel.TYPICAL_NET_PER_KASA));
            k = Math.max(k, (int) Math.ceil(net / TezgahModel.MAX_NET_PER_KASA - 1e-9));
        }
        double kg = Math.round((net + k * tare) * 100) / 100.0;
        if (!estimated) {
            int sug = TezgahModel.suggestedKasa(k, kg, tare);
            if (sug > 0) {   // kasaya uymuyor: düzeltilirse yine aynı net kalsın
                pGrade = g; pKasa = k; pKg = kg; pSuggested = sug; pNet = net; phase = Phase.KASA_CHECK;
                return ask(k + " kasada " + num(net) + " kilo net " + (sug < k ? "az" : "fazla") + ". " + sug + " kasa yapayım mı?");
            }
        }
        committingNet = net;
        return commit(g, k, kg, (named ? label(g) + ", " : "") + (estimated ? kasaWord(k) + ", " : ""), -1);
    }

    private Reply commit(int g, int k, double kg, String lead, int removeIndex) {
        double netSaid = committingNet; committingNet = -1;
        if (removeIndex >= 0) { model.remove(removeIndex); added.remove(added.size() - 1); addedNet.remove(addedNet.size() - 1); }
        if (!model.add(g, k, kg)) return ask("Bu tartımı ekleyemedim. Kiloyu yeniden söyle.");
        added.add(model.items().size() - 1);
        addedNet.add(netSaid);
        grade = g;
        phase = Phase.WEIGH;
        Reply r = new Reply(lead + num(TezgahModel.net(k, kg, model.tare())) + " net.", Action.ADD, true);
        r.grade = g; r.kasa = k; r.kg = kg; r.removeIndex = removeIndex;
        return r;
    }

    private static String kasaWord(int k) { return k == 0 ? "kasasız" : k + " kasa"; }

    /** Uzun okuma (ekran ve "oku" için): "55 kilo, 2 kasa, dara 4, net 51." */
    String line(int k, double kg) {
        double tare = model.tare();
        double net = TezgahModel.net(k, kg, tare);
        if (k == 0) return num(kg) + " kilo, kasasız, net " + num(net) + ".";
        return num(kg) + " kilo, " + k + " kasa, dara " + num(k * tare) + ", net " + num(net) + ".";
    }

    private Reply kasaCheck(String s, String raw) {
        Said said = parse(raw);
        committingNet = pNet;
        if (said.kasa >= 0 && Double.isNaN(said.kg)) return commit(pGrade, said.kasa, pendingKg(said.kasa), "", -1);
        if (has(s, "evet|olur|yap|tamam|dogru")) return commit(pGrade, pSuggested, pendingKg(pSuggested), kasaWord(pSuggested) + ", ", -1);
        if (has(s, "hayir|yine de|oldugu gibi|boyle kalsin|dogru soyledim")) return commit(pGrade, pKasa, pKg, "", -1);
        committingNet = -1;
        return ask(pSuggested + " kasa yapayım mı? Evet ya da hayır de.");
    }

    /** Kasa değişince: net söylendiyse net sabit kalır (kantar yeniden hesaplanır), değilse kantar kilosu aynı. */
    private double pendingKg(int k) { return pNet >= 0 ? Math.round((pNet + k * model.tare()) * 100) / 100.0 : pKg; }

    private Reply fixKasa(int k) {
        if (added.isEmpty()) return ask("Önce kantardaki kiloyu söyle.");
        int idx = added.get(added.size() - 1);
        TezgahModel.Weighing w = model.items().get(idx);
        double netSaid = addedNet.get(addedNet.size() - 1);
        double kg = netSaid >= 0 ? Math.round((netSaid + k * model.tare()) * 100) / 100.0 : w.kg;   // net söylendiyse net sabit
        if (!TezgahModel.addError(k, kg, model.tare()).isEmpty()) return ask(k + " kasa bu kiloya uymuyor. Kasa sayısını yeniden söyle.");
        committingNet = netSaid;
        return commit(w.grade, k, kg, "Düzelttim, " + kasaWord(k) + ", ", idx);
    }

    private Reply undo() {
        if (added.isEmpty()) return ask("Bu konuşmada eklenen tartım yok. " + prompt());
        int idx = added.remove(added.size() - 1);
        addedNet.remove(addedNet.size() - 1);
        TezgahModel.Weighing w = model.items().get(idx);
        model.remove(idx);
        grade = w.grade;
        Reply r = new Reply("Sildim: " + label(w.grade) + " " + num(w.kg) + " kilo. " + prompt(), Action.UNDO, true);
        r.removeIndex = idx;
        return r;
    }

    private Reply finish() {
        if (model.isEmpty()) return ask("Henüz tartım yok. Kantardaki kiloyu söyle.");
        // Sıra: kilo, para, ortalama kilo fiyatı.
        String head = "Tartım bitti. Toplam net " + num(model.totalNet()) + " kilo.";
        if (model.total() > 0.5) head += " Tutar " + money(model.total()) + " lira. Ortalama kilo fiyatı " + num(model.averagePrice()) + " lira.";
        List<String> missing = model.missingPrices();
        if (!missing.isEmpty()) head += " " + String.join(", ", missing) + " için fiyat girilmemiş.";
        if (seller.isEmpty()) { phase = Phase.SELLER; return ask(head + " Kimden aldın?"); }
        return askPay(head + " Satan " + seller + ".");
    }

    private Reply askPay(String lead) {
        if (model.total() <= 0.5) { pay = ElekSaleModel.PAY_NONE; paid = 0; return confirmQuestion(lead); }
        phase = Phase.PAY;
        return ask(lead + " Ödemeyi yaptın mı? Tamamı mı, bir kısmı mı, sonra mı?");
    }

    private static final String SELLER_FILLER = "(?iu)\\b(aldım|aldık|aldim|aldik|getirdi|geldi|ondan|kimden|bundan|üretici|uretici|satan)\\b";

    private Reply answerSeller(String raw, String s) {
        if (has(s, "munferit|tanimiyorum|bilmiyorum|adsiz|gec|yok|isimsiz")) {
            seller = TezgahWizardFlow.WALK_IN; sellerNew = false;
            return sellerSet(askPay("Münferit satıcı yazdım."));
        }
        String cleaned = raw.replaceAll(SELLER_FILLER, " ");
        ElekVoiceBuyer.Match m = ElekVoiceBuyer.match(cleaned, sellers);
        if (m.kind == ElekVoiceBuyer.Kind.NEW) {
            // "Mehmetten aldım": kesme işaretsiz -den/-dan eki; ekini atınca listede varsa o.
            String[] w = m.name.split(" ");
            String last = w[w.length - 1].toLowerCase(TR);
            for (String suf : new String[]{"den", "dan", "ten", "tan"}) {
                if (last.length() > suf.length() + 2 && last.endsWith(suf)) {
                    w[w.length - 1] = w[w.length - 1].substring(0, w[w.length - 1].length() - suf.length());
                    ElekVoiceBuyer.Match again = ElekVoiceBuyer.match(String.join(" ", w), sellers);
                    if (again.kind == ElekVoiceBuyer.Kind.EXISTING || again.kind == ElekVoiceBuyer.Kind.AMBIGUOUS) m = again;
                    break;
                }
            }
        }
        switch (m.kind) {
            case EXISTING:
                seller = m.name; sellerNew = false;
                return sellerSet(askPay(seller + "."));
            case AMBIGUOUS:
                choices = new ArrayList<>(m.candidates.subList(0, Math.min(3, m.candidates.size())));
                phase = Phase.SELLER_PICK;
                return ask("Hangisi? " + String.join(" mı, ", choices) + " mı?");
            case NEW:
                seller = m.name; sellerNew = true;
                return sellerSet(askPay(seller + " listende yok, yeni üretici olarak yazıyorum."));
            default:
                return ask("Adını anlamadım. Kimden aldın? Bilmiyorsan münferit de.");
        }
    }

    private Reply pickSeller(String raw, String s) {
        String[] order = {"birinci|ilki|ilk", "ikinci", "ucuncu"};
        for (int i = 0; i < choices.size() && i < order.length; i++) {
            if (has(s, order[i])) { seller = choices.get(i); sellerNew = false; return sellerSet(askPay(seller + ".")); }
        }
        String p = ElekVoiceBuyer.pick(raw.replaceAll(SELLER_FILLER, " "), choices);
        if (p != null) { seller = p; sellerNew = false; return sellerSet(askPay(seller + ".")); }
        if (has(s, "hicbiri|yeni|baska")) { phase = Phase.SELLER; return ask("Tam adını söyle."); }
        return ask("Hangisi? " + String.join(" mı, ", choices) + " mı?");
    }

    private Reply sellerSet(Reply next) {
        Reply r = new Reply(next.speech, Action.SELLER, next.listen);
        return r;
    }

    private Reply answerPay(String raw, String s) {
        double total = model.total();
        Said said = parse(raw);
        boolean amount = !Double.isNaN(said.kg) && said.kg >= 10;   // "bir kısmı"ndaki "bir" tutar değil
        if (has(s, "kismi|kismini|kismen|biraz")) {
            if (amount) return partial(said.kg);
            phase = Phase.PAY_AMOUNT; return ask("Ne kadar ödedin?");
        }
        if (amount && !has(s, "yarisi")) return partial(said.kg);
        if (has(s, "odemedim|odenmedi|sonra|borc|veresiye|henuz|hayir|yok|vermedim")) {
            pay = ElekSaleModel.PAY_NONE; paid = 0; return confirmQuestion("");
        }
        if (has(s, "yarisi")) return partial(Math.round(total / 2));
        if (has(s, "tamami|tamamini|hepsi|hepsini|odedim|pesin|verdim|evet|tam|nakit")) {
            pay = ElekSaleModel.PAY_ALL; paid = total; return confirmQuestion("");
        }
        return ask("Tamamı mı, bir kısmı mı, sonra mı?");
    }

    private Reply answerAmount(String raw, String s) {
        if (has(s, "hepsi|tamami")) { pay = ElekSaleModel.PAY_ALL; paid = model.total(); return confirmQuestion(""); }
        if (has(s, "hic|odemedim|sonra")) { pay = ElekSaleModel.PAY_NONE; paid = 0; return confirmQuestion(""); }
        Said said = parse(raw);
        if (Double.isNaN(said.kg) || said.kg <= 0) return ask("Tutarı anlamadım. Kaç lira ödedin?");
        return partial(said.kg);
    }

    private Reply partial(double amount) {
        double total = model.total();
        if (amount >= total - 0.5) { pay = ElekSaleModel.PAY_ALL; paid = total; }
        else { pay = ElekSaleModel.PAY_PART; paid = amount; }
        return confirmQuestion("");
    }

    private Reply confirmQuestion(String lead) {
        phase = Phase.CONFIRM;
        double total = model.total(), left = Math.max(0, total - paid);
        StringBuilder b = new StringBuilder();
        if (!lead.isEmpty()) b.append(lead).append(' ');
        b.append("Satan ").append(seller.isEmpty() ? TezgahWizardFlow.WALK_IN : seller).append(". ");
        b.append("Net ").append(num(model.totalNet())).append(" kilo");
        if (total > 0.5) b.append(", ").append(money(total)).append(" lira");
        b.append(". ");
        if (total > 0.5) {
            if (left <= 0.5) b.append("Tamamı ödendi. ");
            else if (paid > 0.5) b.append(money(paid)).append(" lira ödendi, ").append(money(left)).append(" lira borç kalıyor. ");
            else b.append("Ödeme sonra, ").append(money(left)).append(" lira borç kalıyor. ");
        }
        if (left > 0.5 && (seller.isEmpty() || seller.equals(TezgahWizardFlow.WALK_IN))) b.append("Ad yok, borç adla bulunamaz. ");
        b.append("Kaydedip fişi hazırlayayım mı?");
        return ask(b.toString());
    }

    private Reply confirm(String s) {
        if (has(s, "hayir|dur|bekle|yok|degil")) {
            phase = Phase.WEIGH;
            return ask("Tamam, kaydetmedim. Tartıma devam edebilirsin; bitince bitti de.");
        }
        if (has(s, "evet|kaydet|tamam|olur|hazirla|onayla|dogru")) {
            phase = Phase.DONE;
            return new Reply("Kaydediyorum.", Action.SAVE, false);
        }
        return ask("Kaydedeyim mi? Evet ya da hayır de.");
    }

    // ------------------------------------------------------------ yardımcılar

    public String readout() {
        StringBuilder b = new StringBuilder();
        for (int g = 0; g < TezgahModel.GRADES.length; g++) {
            if (model.gradeCount(g) == 0) continue;
            if (b.length() > 0) b.append(", ");
            b.append(label(g)).append(' ').append(num(model.gradeNet(g))).append(" kilo");
        }
        if (b.length() == 0) return "Henüz tartım yok.";
        b.append(". Toplam net ").append(num(model.totalNet())).append(" kilo, ").append(model.totalKasa()).append(" kasa");
        if (model.total() > 0.5) b.append(", ").append(money(model.total())).append(" lira");
        return b.append('.').toString();
    }

    private String prompt() {
        switch (phase) {
            case SELLER: return "Kimden aldın?";
            case PAY: return "Tamamı mı, bir kısmı mı, sonra mı?";
            case CONFIRM: return "Kaydedeyim mi?";
            default: return label(grade) + ", kaç kilo?";
        }
    }

    private static Reply ask(String speech) { return new Reply(speech, Action.NONE, true); }

    static String label(int g) { return g == 0 ? "Double" : g == TezgahModel.GRADES.length - 1 ? "Yağlık" : g + " numara"; }

    /** Konuşma için sayı: 51 → "51", 51.5 → "51,5". */
    static String num(double v) {
        double r = Math.round(v * 10) / 10.0;
        return r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r).replace('.', ',');
    }

    /** Lira, ayraçsız (ses motoru "9.600"ü 9,6 diye okumasın). */
    static String money(double v) { return String.valueOf(Math.round(v)); }
}
