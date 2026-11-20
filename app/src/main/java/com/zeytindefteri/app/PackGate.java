package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Bilgi paketinin cümle bankası kapısı (İP8; saf, testli). Uygulamaya gömülü kontrol bankasındaki ("doğru")
 * cümlelerin her biri, paket olmadan ve paketle aynı sonucu vermeli: paket önceden anlaşılan bir cümleyi değiştirirse
 * uygulanmaz (bir öncekiyle devam edilir). Kontrol bankası tests/sentence_bank/cumleler.tsv'den üretilir
 * (tools/make_control_bank.py), eşitliği testle denetlenir; uydurma örnek cümlelerdir, kişisel veri yoktur.
 */
final class PackGate {
    private PackGate() {
    }

    /** Paketin bozduğu cümleler (boşsa paket geçer). bank: kontrol bankasının cümleleri. */
    static List<String> broken(KnowledgePack pack, List<String> bank) {
        List<String> out = new ArrayList<>();
        if (pack == null) {
            out.add("(paket okunamadı)");
            return out;
        }
        RuleBrain rules = new RuleBrain();
        LearnedBrain without = new LearnedBrain(new LearnedTerms.Memory(), rules);
        LearnedBrain with = new LearnedBrain(new PackedStore(new LearnedTerms.Memory(), pack.terms, PackedStore.NONE), rules);
        for (String raw : bank) {
            Decision a = without.decide(new Brain.Input(raw), context());
            Decision b = with.decide(new Brain.Input(raw), context());
            boolean same = a.action.equals(b.action) && a.fieldsText().equals(b.fieldsText()) && a.variant.equals(b.variant);
            if (!same) {
                out.add(raw);
            }
        }
        return out;
    }

    /**
     * Yeni paket uygulanır mı: okunduysa, sürümü şimdikinden büyükse ve bankayı bozmuyorsa yeni; değilse şimdiki
     * (bir önceki pakete dönüş). current null olabilir (hiç paket yok).
     */
    static KnowledgePack choose(KnowledgePack current, KnowledgePack candidate, List<String> bank) {
        if (candidate == null) {
            return current;
        }
        if (current != null && candidate.version <= current.version) {
            return current;
        }
        return broken(candidate, bank).isEmpty() ? candidate : current;
    }

    private static BrainContext context() {
        return BrainContext.full(true, AssistantPersona.decode("")::handle);
    }

    /** Kontrol bankası dosyası: her satır "cümle<TAB>eylem<TAB>alanlar"; "#" ile başlayan açıklama. */
    static List<String> sentences(String tsv) {
        List<String> out = new ArrayList<>();
        for (String line : tsv.split("\n")) {
            if (line.trim().isEmpty() || line.startsWith("#")) {
                continue;
            }
            out.add(line.split("\t", -1)[0]);
        }
        return out;
    }
}
