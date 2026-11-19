package com.zeytindefteri.app;

/**
 * Öğrenilen sözler beyni (İP4): kural beyninin ÖNÜNDE çalışır. Cümlede çiftçinin öğrettiği bir söz varsa onu
 * anlamının kurallarca bilinen söylenişine çevirir ("bakırladık" → "ilaçladık") ve kararı kurallara bırakır;
 * öğretilen anlam kuraldan üstündür. Sözlük boşken her karar kural beyniyle birebir aynıdır.
 *
 * Öğretilen sözün çıkan karar anlamıyla uyuşmazsa (ör. çevrilen cümleyi kurallar başka işe yorarsa) öğrenilen söz
 * kullanılmaz, kural kararı geçer: emin değilse susar.
 *
 * Komutlar (konuşma aşaması): "öğrendiklerini söyle" (SOZLER_GOSTER), "bakırladığı unut" (SOZ_UNUT; yalnız
 * sözlükte olan söz için — "adımı unut" sohbete kalır).
 */
final class LearnedBrain implements Brain {
    static final String NAME = "ogrenilen";

    private final LearnedTerms.Store store;
    private final RuleBrain rules;

    LearnedBrain(LearnedTerms.Store store, RuleBrain rules) {
        this.store = store;
        this.rules = rules;
    }

    LearnedTerms.Store store() {
        return store;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Decision decide(Input in, BrainContext c) {
        if (c.screen == BrainContext.Screen.ELEK) {
            return rules.decide(in, c);
        }
        if (c.conversation) {
            Decision talk = conversationStage(in, c);
            if (talk != null) {
                return talk;
            }
        }
        if (!c.recordsKnown) {
            throw new IllegalStateException("Kayıt aşaması için bahçe bilgisi gerekli (BrainContext.recordsOnly)");
        }
        return recordStage(in, c, ZeytinAssistantCommand.parse(in.raw));
    }

    /** Konuşma aşaması: önce sözlük komutları, sonra kural beyni. */
    Decision conversationStage(Input in, BrainContext c) {
        if (c.commandsAllowed && LearnedTerms.asksList(in.raw)) {
            return command(ActionCatalog.SOZLER_GOSTER, "Öğrenilen sözleri görme isteği", null);
        }
        if (c.commandsAllowed) {
            String forget = LearnedTerms.forgetTarget(in.raw);
            // Yalnız sözlükte olan söz: "adımı unut" gibi sohbet komutları sohbete kalır.
            String root = forget == null ? "" : LearnedTerms.verbRoot(forget);
            if (!root.isEmpty() && store.get(root) != null) {
                return command(ActionCatalog.SOZ_UNUT, "Öğrenilen sözü unutma isteği", forget);
            }
        }
        return rules.conversationStage(in, c);
    }

    /** Kayıt aşaması: öğrenilmiş söz varsa anlamıyla, yoksa kurallarla. */
    Decision recordStage(Input in, BrainContext c, ZeytinAssistantCommand cmd) {
        LearnedTerms.Term t = LearnedTerms.find(in.raw, store);
        if (t == null) {
            return rules.recordStage(in, c, cmd);
        }
        Input taught = new Input(LearnedTerms.rewrite(in.raw, t));
        Decision d = rules.recordStage(taught, c, ZeytinAssistantCommand.parse(taught.raw));
        if (!means(d, t)) {
            return rules.recordStage(in, c, cmd);
        }
        store.used(t.term);
        String reason = "Öğrendiğim söz: " + t.word + " = " + t.meaning();
        return new Decision(d.action, d.fields, d.confidence, reason, d.variant, d.requested, new Taught(d.payload, t), NAME);
    }

    /** Karar, öğretilen anlamı mı taşıyor. */
    static boolean means(Decision d, LearnedTerms.Term t) {
        if (!d.is(t.action)) {
            return false;
        }
        if (t.field.isEmpty()) {
            return true;
        }
        return t.value.equals(d.fields.get(t.field));
    }

    private static Decision command(String action, String reason, Object payload) {
        ActionCatalog.Action a = ActionCatalog.get(action);
        return new Decision(action, null, 1, reason, "", a.level, payload, NAME);
    }

    /**
     * Öğrenilmiş sözle verilen kararın yükü: kuralın nesnesi (OperationVoiceEntry...) + hangi söz. Yürütücü
     * kuralın nesnesini inner ile alır; sözü geri bildirim ("… diye öğrendim", "Unut") için kullanır.
     */
    static final class Taught {
        final Object inner;
        final LearnedTerms.Term term;

        Taught(Object inner, LearnedTerms.Term term) {
            this.inner = inner;
            this.term = term;
        }
    }

    /** Kararın asıl yükü (öğrenilmiş sözle verildiyse içindeki). */
    static <T> T payload(Decision d, Class<T> type) {
        Taught taught = d.payload(Taught.class);
        if (taught != null && type.isInstance(taught.inner)) {
            return type.cast(taught.inner);
        }
        return d.payload(type);
    }

    /** Karar öğrenilmiş bir sözle mi verildi; verildiyse söz, değilse null. */
    static LearnedTerms.Term term(Decision d) {
        Taught taught = d.payload(Taught.class);
        return taught == null ? null : taught.term;
    }
}
