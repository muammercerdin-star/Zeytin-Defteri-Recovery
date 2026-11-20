package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Katmanlı sözlük (İP8; saf, testli): önce çiftçinin öğrettikleri, sonra bilgi paketinin sözleri ("paket").
 * Çiftçinin öğrettiği söz HER ZAMAN üstündür: aynı kök öğretildiyse paketteki anlam görünmez. Çiftçinin
 * kapattığı paket sözleri (disabled) hiç kullanılmaz. Yazma (öğret, unut, Vazgeç) yalnız öğretilenlere gider;
 * paket sözü öğretme sorusunu da susturur (bilinen söz).
 */
final class PackedStore implements LearnedTerms.Store {
    private final LearnedTerms.Store taught;
    private final Source source;
    private final Disabled disabled;

    /** Çiftçinin kapattığı paket sözü mü (her seferinde sorulur: kapatınca asistanda hemen etkili). */
    interface Disabled {
        boolean is(String root);
    }

    static final Disabled NONE = root -> false;

    /** Paketin sözleri (telefonda arka planda doğrulanınca dolar; o zamana dek boş). */
    interface Source {
        List<LearnedTerms.Term> terms();
    }

    PackedStore(LearnedTerms.Store taught, List<LearnedTerms.Term> packTerms, Disabled disabled) {
        this(taught, fixed(packTerms), disabled);
    }

    PackedStore(LearnedTerms.Store taught, Source source, Disabled disabled) {
        this.taught = taught;
        this.source = source;
        this.disabled = disabled == null ? NONE : disabled;
    }

    private static Source fixed(List<LearnedTerms.Term> terms) {
        final List<LearnedTerms.Term> copy = terms == null ? new ArrayList<>() : new ArrayList<>(terms);
        return () -> copy;
    }

    private Map<String, LearnedTerms.Term> pack() {
        Map<String, LearnedTerms.Term> m = new LinkedHashMap<>();
        for (LearnedTerms.Term t : source.terms()) {
            m.put(t.term, t);
        }
        return m;
    }

    /** Çiftçinin öğrettikleri (Öğrendiklerim, yedek): paket sözleri karışmaz. */
    LearnedTerms.Store taught() {
        return taught;
    }

    /** Paketin açık sözleri (öğretilenle çakışmayan, kapatılmamış). */
    List<LearnedTerms.Term> activePack() {
        List<LearnedTerms.Term> out = new ArrayList<>();
        for (LearnedTerms.Term t : pack().values()) {
            if (!disabled.is(t.term) && taught.get(t.term) == null) {
                out.add(t);
            }
        }
        return out;
    }

    @Override
    public List<LearnedTerms.Term> all() {
        List<LearnedTerms.Term> out = new ArrayList<>(taught.all());
        out.addAll(activePack());
        return out;
    }

    @Override
    public LearnedTerms.Term get(String term) {
        LearnedTerms.Term own = taught.get(term);
        if (own != null) {
            return own;
        }
        if (disabled.is(term)) {
            return null;
        }
        return pack().get(term);
    }

    @Override
    public void put(LearnedTerms.Term t) {
        taught.put(t);
    }

    @Override
    public boolean remove(String term) {
        return taught.remove(term);
    }

    @Override
    public void used(String term) {
        if (taught.get(term) != null) {
            taught.used(term);
        }
    }

    @Override
    public boolean declined(String sentence) {
        return taught.declined(sentence);
    }

    @Override
    public void decline(String sentence) {
        taught.decline(sentence);
    }
}
