package com.zeytindefteri.app;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bilgi paketi (İP8-A): gömülü başlangıç paketi listeden üretilmiş ve bankadan geçmiş; paket boşken davranış aynı,
 * paketle yalnız paketteki sözler fark yaratır; çiftçinin öğrettiği üstün; kapatılan söz kullanılmaz; bozuk, özeti ya da
 * imzası tutmayan, bankayı bozan paket uygulanmaz (bir öncekiyle devam); iki anahtar yuvası; kontrol bankası
 * cümle bankasından üretilmiş ve gerçek kişi adı içermiyor.
 */
public class KnowledgePackTest {
    static int n = 0;
    static int bad = 0;
    static Path root;

    static void check(boolean ok, String m) {
        n++;
        if (!ok) {
            bad++;
            System.out.println("FAIL " + m);
        }
    }

    static void eq(Object got, Object want, String m) {
        check(want.equals(got), m + ": «" + got + "» beklenen «" + want + "»");
    }

    static String read(String rel) throws Exception {
        return new String(Files.readAllBytes(root.resolve(rel)), StandardCharsets.UTF_8);
    }

    static Decision decide(LearnedTerms.Store store, String raw) {
        return new LearnedBrain(store, new RuleBrain()).decide(new Brain.Input(raw),
                BrainContext.full(true, AssistantPersona.decode("")::handle));
    }

    public static void main(String[] a) throws Exception {
        root = Paths.get(a[0]);
        KnowledgePack pack = embedded();
        understanding(pack);
        onlyPackWordsDiffer(pack);
        taughtWinsAndDisable(pack);
        rejects(pack);
        signatures();
        controlBank();
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " KnowledgePack");
        if (bad > 0) {
            System.exit(1);
        }
    }

    static KnowledgePack embedded() throws Exception {
        String json = read("app/src/main/res/raw/bilgi_paketi.json");
        eq(json, PackTool.generate(read("app/src/main/res/raw/baslangic_sozleri.tsv")), "gömülü paket başlangıç listesinden üretilmiş");
        KnowledgePack p = KnowledgePack.read(json, KnowledgePack.PUBLIC_KEYS);
        check(p != null, "gömülü paket okunur (özet tutuyor)");
        eq(p.terms.size(), 11, "onaylanan 11 söz");
        eq(p.source, KnowledgePack.EMBEDDED, "gömülü");
        for (LearnedTerms.Term t : p.terms) {
            eq(t.kind, KnowledgePack.KIND, "tür=paket: " + t.word);
            check(!t.word.equals("tırpanladık") && !t.word.equals("aşıladık"), "İP5 öğretme sözleri pakette yok");
        }
        check(PackGate.broken(p, PackGate.sentences(read("app/src/main/res/raw/kontrol_bankasi.tsv"))).isEmpty(),
                "gömülü paket kontrol bankasından geçer");
        return p;
    }

    static void understanding(KnowledgePack p) {
        String[][] words = {{"bakırladık", "ISLEM", "İlaçlama"}, {"göztaşıladık", "ISLEM", "İlaçlama"}, {"bordoladık", "ISLEM", "İlaçlama"},
                {"suvardık", "ISLEM", "Sulama"}, {"silktik", "HASAT", ""}, {"sırıkladık", "HASAT", ""}, {"devşirdik", "HASAT", ""},
                {"diskaradık", "ISLEM", "Toprak İşleme"}, {"belledik", "ISLEM", "Toprak İşleme"}, {"seyrelttik", "ISLEM", "Budama"},
                {"kalemledik", "ISLEM", "Diğer"}};
        PackedStore store = new PackedStore(new LearnedTerms.Memory(), p.terms, PackedStore.NONE);
        for (String[] w : words) {
            check(!decide(new LearnedTerms.Memory(), "bugün " + w[0]).understood(), "paketsiz anlaşılmaz: " + w[0]);
            Decision d = decide(store, "bugün " + w[0]);
            check(d.is(w[1]), "paketle anlaşılır: " + w[0] + " → " + d);
            if (!w[2].isEmpty()) {
                eq(d.fields.get("tur"), w[2], "anlam: " + w[0]);
            }
            check(LearnedTerms.teachable("bugün " + w[0], store) == null, "paketteki söz için öğretme sorusu çıkmaz: " + w[0]);
        }
        eq(decide(store, "bugün kalemledik").fields.get("not"), "Aşılama", "kalemledik: Diğer + Aşılama");
    }

    /** Paket boşken davranış aynı; paketle yalnız paketteki sözü içeren cümleler değişir (tüm aynılık korpusu). */
    static void onlyPackWordsDiffer(KnowledgePack p) throws Exception {
        List<String> corpus = BrainEquivalenceTest.corpus(root);
        PackedStore empty = new PackedStore(new LearnedTerms.Memory(), new ArrayList<>(), PackedStore.NONE);
        PackedStore full = new PackedStore(new LearnedTerms.Memory(), p.terms, PackedStore.NONE);
        Set<String> roots = new HashSet<>();
        for (LearnedTerms.Term t : p.terms) {
            roots.add(t.term);
        }
        int sameEmpty = 0;
        int changed = 0;
        int wrong = 0;
        for (String raw : corpus) {
            Decision base = decide(new LearnedTerms.Memory(), raw);
            Decision e = decide(empty, raw);
            if (same(base, e)) {
                sameEmpty++;
            }
            Decision f = decide(full, raw);
            if (same(base, f)) {
                continue;
            }
            changed++;
            boolean hasPackWord = false;
            for (String w : TurkishText.words(raw)) {
                hasPackWord |= roots.contains(LearnedTerms.verbRoot(w));
            }
            if (!hasPackWord) {
                wrong++;
                if (wrong <= 5) {
                    System.out.println("   paket sözü olmadan değişti: " + raw + " · " + base + " → " + f);
                }
            }
        }
        eq(sameEmpty, corpus.size(), "paket boşken " + corpus.size() + " cümlenin hepsi aynı");
        eq(wrong, 0, "paketle değişen " + changed + " cümlenin hepsinde paket sözü var");
        System.out.println("Paket: " + corpus.size() + " cümle · paket boşken 0 fark · paketle " + changed + " cümle değişti (hepsi paket sözlü)");
    }

    static boolean same(Decision a, Decision b) {
        return a.action.equals(b.action) && a.fieldsText().equals(b.fieldsText()) && a.variant.equals(b.variant);
    }

    static void taughtWinsAndDisable(KnowledgePack p) {
        LearnedTerms.Memory taught = new LearnedTerms.Memory();
        Set<String> off = new HashSet<>();
        PackedStore store = new PackedStore(taught, p.terms, off::contains);
        eq(decide(store, "bugün bakırladık").fields.get("tur"), "İlaçlama", "paketten");
        taught.put(LearnedTerms.teach("bakırladık", LearnedTerms.choiceFor("gübre"), LearnedTerms.SOURCE_VOICE, 1));
        eq(decide(store, "bugün bakırladık").fields.get("tur"), "Gübre", "çiftçinin öğrettiği kazanır");
        check(store.activePack().stream().noneMatch(t -> t.term.equals("bakirl")), "öğretilenle çakışan paket sözü etkisiz");
        taught.remove("bakirl");
        eq(decide(store, "bugün bakırladık").fields.get("tur"), "İlaçlama", "öğretilen unutulunca paket yeniden");
        off.add("suvar");
        check(!decide(store, "bugün suvardık").understood(), "kapatılan söz kullanılmaz");
        check(LearnedTerms.teachable("bugün suvardık", store) != null, "kapatılan söz yeniden öğretilebilir");
        off.remove("suvar");
        check(decide(store, "bugün suvardık").understood(), "yeniden açılınca kullanılır");
        List<LearnedScreen.PackRow> rows = LearnedScreen.fromAll(p.terms, taught, off::contains);
        eq(rows.size(), 11, "Herkesten öğrendiklerim: 11 satır");
        eq(rows.get(0).title, "Bakırladık = ilaçlama", "satır başlığı");
        eq(rows.get(0).detail, "Herkesten · açık", "açık");
        off.add("bakirl");
        check(!LearnedScreen.fromAll(p.terms, taught, off::contains).get(0).on, "kapatılan satır kapalı görünür (açılabilsin)");
        taught.put(LearnedTerms.teach("göztaşıladık", LearnedTerms.choiceFor("gübre"), LearnedTerms.SOURCE_VOICE, 1));
        LearnedScreen.PackRow over = LearnedScreen.fromAll(p.terms, taught, off::contains).get(1);
        check(over.overridden && over.detail.startsWith("Senin öğrettiğin geçerli"), "kendi öğrettiği: " + over.detail);
        check(LearnedScreen.taught(store.all(), 1, java.util.TimeZone.getDefault()).size() == 1,
                "Senin öğrettiklerin'de paket sözü yok");
        check(store.taught() == taught, "yazma yalnız öğretilene");
    }

    static void rejects(KnowledgePack good) throws Exception {
        String json = read("app/src/main/res/raw/bilgi_paketi.json");
        check(KnowledgePack.read(json.replace("\"İlaçlama\"", "\"Gübre\""), KnowledgePack.PUBLIC_KEYS) == null, "özeti tutmayan paket");
        check(KnowledgePack.read("{bozuk", KnowledgePack.PUBLIC_KEYS) == null, "bozuk paket");
        check(KnowledgePack.read(json.replace("\"bicim\":1", "\"bicim\":2"), KnowledgePack.PUBLIC_KEYS) == null, "bilinmeyen biçim");
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"yanlis", "bakırladık", "ISLEM", "İlaçlama", "", ""});
        check(KnowledgePack.read(KnowledgePack.write(2, "2026-11-20", "gomulu", rows, 0, ""), null) == null, "kök sözle tutmuyor");
        rows.clear();
        rows.add(KnowledgePack.row("bakırladık", "ilaçlama", ""));
        rows.get(0)[3] = "Mazot";
        check(KnowledgePack.read(KnowledgePack.write(2, "2026-11-20", "gomulu", rows, 0, ""), null) == null, "anlam bir seçim değil");
        rows.clear();
        rows.add(KnowledgePack.row("bakırladık", "ilaçlama", ""));
        String unsigned = KnowledgePack.write(2, "2026-11-20", "indirilen", rows, 0, "");
        check(KnowledgePack.read(unsigned, KnowledgePack.PUBLIC_KEYS) == null, "imzasız indirilen paket");
        List<String> bank = PackGate.sentences(read("app/src/main/res/raw/kontrol_bankasi.tsv"));
        List<String[]> breaking = new ArrayList<>();
        breaking.add(KnowledgePack.row("topladık", "ilaçlama", ""));
        KnowledgePack bad = KnowledgePack.read(KnowledgePack.write(5, "2026-11-20", "gomulu", breaking, 0, ""), null);
        check(bad != null && !PackGate.broken(bad, bank).isEmpty(), "bankayı bozan paket yakalanır (topladık = ilaçlama)");
        check(PackGate.choose(good, bad, bank) == good, "bankayı bozan paket uygulanmaz: bir öncekiyle devam");
        check(PackGate.choose(good, null, bank) == good, "okunamayan paket: bir öncekiyle devam");
        check(PackGate.choose(null, bad, bank) == null, "önceki yoksa paketsiz");
        KnowledgePack older = KnowledgePack.read(KnowledgePack.write(1, "2026-01-01", "gomulu", rows, 0, ""), null);
        check(PackGate.choose(good, older, bank) == good, "eski ya da aynı sürüm uygulanmaz");
        KnowledgePack newer = KnowledgePack.read(KnowledgePack.write(2, "2026-12-01", "gomulu", rows, 0, ""), null);
        check(PackGate.choose(good, newer, bank) == newer, "yeni ve sağlam paket uygulanır");
    }

    /** İki anahtar yuvası; İP8-A'da ikisi boş: indirilen hiçbir paket kabul edilmez. */
    static void signatures() throws Exception {
        eq(KnowledgePack.PUBLIC_KEYS.length, 2, "iki açık anahtar yuvası");
        check(KnowledgePack.PUBLIC_KEYS[0].isEmpty() && KnowledgePack.PUBLIC_KEYS[1].isEmpty(), "İP8-A: yuvalar boş");
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair oldKey = g.generateKeyPair();
        KeyPair newKey = g.generateKeyPair();
        List<String[]> rows = new ArrayList<>();
        rows.add(KnowledgePack.row("bakırladık", "ilaçlama", "Ege"));
        String canonical = KnowledgePack.canonical(3, "2027-03-01", "indirilen", rows);
        String sigNew = sign(newKey, canonical);
        String json = KnowledgePack.write(3, "2027-03-01", "indirilen", rows, 2, sigNew);
        check(KnowledgePack.read(json, KnowledgePack.PUBLIC_KEYS) == null, "uygulamadaki boş yuvalarla imzalı paket de reddedilir");
        String[] keys = {CommunityQueue.hex(oldKey.getPublic().getEncoded()), CommunityQueue.hex(newKey.getPublic().getEncoded())};
        check(KnowledgePack.read(json, keys) != null, "yeni anahtar (yuva 2) ile imzalı paket kabul");
        String oldJson = KnowledgePack.write(3, "2027-03-01", "indirilen", rows, 1, sign(oldKey, canonical));
        check(KnowledgePack.read(oldJson, keys) != null, "eski anahtar (yuva 1) değişim süresince geçerli");
        check(KnowledgePack.read(KnowledgePack.write(3, "2027-03-01", "indirilen", rows, 1, sigNew), keys) == null,
                "yanlış yuvadaki imza reddedilir");
        String tampered = json.replace("\"Ege\"", "\"Akdeniz\"");
        check(KnowledgePack.read(tampered, keys) == null, "imzadan sonra değiştirilen paket reddedilir");
        check(KnowledgePack.read(KnowledgePack.write(3, "2027-03-01", "indirilen", rows, 3, sigNew), keys) == null, "olmayan yuva");
    }

    static String sign(KeyPair k, String canonical) throws Exception {
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(k.getPrivate());
        s.update(canonical.getBytes(StandardCharsets.UTF_8));
        return CommunityQueue.hex(s.sign());
    }

    /** Kontrol bankası: cümle bankasından üretilmiş; gerçek kişi adı ve kişisel veri yok. */
    static void controlBank() throws Exception {
        String bank = read("app/src/main/res/raw/kontrol_bankasi.tsv");
        Set<String> real = new HashSet<>();
        for (String l : read("tools/pack/gercek_adlar.txt").split("\n")) {
            if (!l.trim().isEmpty() && !l.startsWith("#")) {
                real.add(l.trim());
            }
        }
        Set<String> allowed = new HashSet<>();
        for (String l : read("tools/pack/kurgu_adlar.txt").split("\n")) {
            if (!l.trim().isEmpty() && !l.startsWith("#")) {
                allowed.add(l.trim());
            }
        }
        int lines = 0;
        Pattern name = Pattern.compile("(?<=\\s)[A-ZÇĞİÖŞÜ][a-zçğıöşü]+|[A-ZÇĞİÖŞÜ][a-zçğıöşü]+(?=['’])");
        for (String line : bank.split("\n")) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            lines++;
            String s = line.split("\t")[0];
            String low = s.toLowerCase(new java.util.Locale("tr", "TR"));
            for (String r : real) {
                check(!low.contains(r), "gerçek kişi adı yok («" + r + "»): " + s);
            }
            Matcher m = name.matcher(s);
            while (m.find()) {
                check(allowed.contains(m.group()), "ad kurgu listesinde («" + m.group() + "»): " + s);
            }
            check(!s.matches(".*(\\+?90 ?)?0?5\\d{2} ?\\d{3} ?\\d{2} ?\\d{2}.*"), "telefon numarası yok: " + s);
            check(!s.matches(".*\\b\\d{11}\\b.*"), "kimlik numarası yok: " + s);
            check(!s.matches(".*TR\\d{2}[ 0-9]{20,}.*") && !s.contains("@"), "IBAN / e-posta yok: " + s);
        }
        check(lines >= 250, "kontrol bankasında " + lines + " cümle");
        String cumleler = read("tests/sentence_bank/cumleler.tsv");
        int dogru = 0;
        for (String line : cumleler.split("\n")) {
            String[] c = line.split("\t");
            if (c.length >= 4 && "dogru".equals(c[3]) && !line.startsWith("#")) {
                dogru++;
            }
        }
        check(lines <= dogru && lines >= dogru - 5, "cümle bankasının doğru satırları (" + dogru + ") ile kontrol bankası (" + lines + ") eşleşiyor");
    }
}
