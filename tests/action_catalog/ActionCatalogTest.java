package com.zeytindefteri.app;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Eylem kataloğu tutarlılığı (İP3). */
public class ActionCatalogTest {
    static int n = 0;
    static int bad = 0;

    static void check(boolean ok, String m) {
        n++;
        if (!ok) {
            bad++;
            System.out.println("FAIL " + m);
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        List<ActionCatalog.Action> all = ActionCatalog.all();
        Set<String> ids = new HashSet<>();
        for (ActionCatalog.Action a : all) {
            check(ids.add(a.id), "kimlik tek: " + a.id);
            check(!a.name.isEmpty(), "adı var: " + a.id);
            check(a.id.matches("[A-Z_]+"), "kimlik büyük harf/alt çizgi: " + a.id);
            if (!a.fields.isEmpty() && a.kind != ActionCatalog.Kind.OKUMA) {
                check(!a.question.isEmpty() && a.question.endsWith("?"), "alanlı eylemin eksik alan sorusu var: " + a.id);
            }
            if (a.alwaysConfirm()) {
                check(a.level != ActionCatalog.Level.IZINLE_KENDISI, "onaylı eylem kendiliğinden yapılamaz: " + a.id);
            }
        }
        // İş emrinin ilk eylemleri
        String[][] first = {{ActionCatalog.ELEK, "KAYIT"}, {ActionCatalog.SATIS, "SATIS"}, {ActionCatalog.TAHSILAT, "ODEME"},
                {ActionCatalog.ISLEM, "KAYIT"}, {ActionCatalog.HASAT, "KAYIT"}, {ActionCatalog.HATIRLATMA, "HATIRLATMA"}};
        for (String[] f : first) {
            ActionCatalog.Action a = ActionCatalog.get(f[0]);
            check(a != null && a.kind.name().equals(f[1]), "ilk eylem tanımlı: " + f[0] + " (" + f[1] + ")");
        }
        // Kayıt, satış, ödeme, silme: her zaman onay — türden çıkar
        for (ActionCatalog.Kind k : ActionCatalog.Kind.values()) {
            boolean must = k == ActionCatalog.Kind.KAYIT || k == ActionCatalog.Kind.SATIS || k == ActionCatalog.Kind.ODEME || k == ActionCatalog.Kind.SILME;
            check(k.alwaysConfirm == must, "onay kuralı türden: " + k);
        }
        check(ActionCatalog.alwaysConfirm("BILINMEYEN_EYLEM"), "bilinmeyen eylem onay ister");
        check(ActionCatalog.alwaysConfirm(ActionCatalog.SATIS) && ActionCatalog.alwaysConfirm(ActionCatalog.TAHSILAT), "satış ve tahsilat onaylı");
        check(!ActionCatalog.alwaysConfirm("SORU_SPRAY"), "soru onay istemez (yazmaz)");
        // Aile: SORU_*
        check(ActionCatalog.get("SORU_SPRAY") != null && ActionCatalog.get("SORU_SPRAY").id.equals(ActionCatalog.SORU), "SORU_SPRAY → soru ailesi");
        check(ActionCatalog.get("SORU_") == null || ActionCatalog.get("SORU_").id.equals(ActionCatalog.SORU), "aile öneki");
        // İP1'den beri konuşma kaydında yazılan eylem adları aynen katalogda (eski kayıtlar okunur kalır)
        String[] logged = {"SOHBET", "HASAT", "ALACAK_SORU", "ELEK", "ELEK_AC", "KAYIT_BRINE", "KAYIT_GARDEN", "KAYIT_POST",
                "ISLEM", "OZEL_CEVAP", "EKRAN_AC", "SORU_SPRAY", "SORU_ELEK", "SORU_MARKET"};
        for (String id : logged) {
            check(ActionCatalog.get(id) != null, "eski kayıt adı katalogda: " + id);
        }
        // Beynin ürettiği her eylem katalogda ve "üretilir"; Satış/Tahsilat/Hatırlatma/Tezgâh üretilmez
        RuleBrain brain = new RuleBrain();
        Set<String> produced = new HashSet<>();
        for (String raw : BrainEquivalenceTest.corpus(root)) {
            for (boolean gardens : new boolean[]{true, false}) {
                for (boolean pending : new boolean[]{true, false}) {
                    Decision d = brain.decide(new Brain.Input(raw), BrainContext.of(true, true, pending, gardens, AssistantPersona.decode("")::handle));
                    ActionCatalog.Action a = ActionCatalog.get(d.action);
                    if (produced.add(d.action)) {
                        check(a != null, "üretilen eylem katalogda: " + d.action);
                        check(a == null || a.produced, "üretilen eylem 'üretilir' işaretli: " + d.action);
                    }
                    check(d.confidence >= 0 && d.confidence <= 1, "güven 0–1");
                    check(d.understood() || d.confidence == 0, "anlaşılmadı kararının güveni 0");
                    check(!d.reason.isEmpty(), "gerekçe var: " + d.action);
                    if (a != null && !a.fields.isEmpty() && a.family.isEmpty()) {
                        for (String key : d.fields.keySet()) {
                            String base = key.replaceAll("_fiyat$", "");
                            boolean known = a.fields.contains(key) || a.fields.contains(base) || key.endsWith("_fiyat") && a.fields.contains("fiyat");
                            check(known, "alan katalogda: " + d.action + "." + key);
                        }
                    }
                }
            }
        }
        // İP7: satış yalnız çok adımlı cümlede üretilir (formu doldurur, onaysız yazmaz); öbürleri hâlâ üretilmez.
        check(produced.contains(ActionCatalog.SATIS) && ActionCatalog.get(ActionCatalog.SATIS).produced, "İP7: satış üretilir");
        check(ActionCatalog.alwaysConfirm(ActionCatalog.SATIS), "İP7: üretilen satış da hep onaylı");
        // Eğitim turu 1: tahsilat üretilir ("Ali Usta'dan 2000 lira aldım"); ödeme olduğu için hep onaylı.
        check(produced.contains(ActionCatalog.TAHSILAT) && ActionCatalog.get(ActionCatalog.TAHSILAT).produced, "eğitim turu 1: tahsilat üretilir");
        check(ActionCatalog.alwaysConfirm(ActionCatalog.TAHSILAT), "eğitim turu 1: üretilen tahsilat da hep onaylı");
        for (String never : new String[]{ActionCatalog.HATIRLATMA, ActionCatalog.TEZGAH_TARTI}) {
            check(!produced.contains(never), "henüz üretilmez: " + never);
            check(!ActionCatalog.get(never).produced, "katalogda üretilmez işaretli: " + never);
        }
        System.out.println("Üretilen eylemler: " + produced.size());
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " ActionCatalog");
        if (bad > 0) {
            System.exit(1);
        }
    }
}
