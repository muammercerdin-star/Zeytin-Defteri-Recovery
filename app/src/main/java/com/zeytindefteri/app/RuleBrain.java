package com.zeytindefteri.app;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kural beyni (İP3; saf, Android'siz): bugünkü kural çözücüleri, asistanın eski karar sırasıyla, Brain arayüzünde.
 * Davranış taşımadan öncekiyle birebir aynıdır (tests/brain_equivalence eski zincirle her cümlede karşılaştırır).
 *
 * Sıra: kapanış → anlamadıklarım → karne/konuşma kaydı → internet yardımcısı komutları → sohbet │ kayıtlar okunur │
 * hasat → (bahçesizken hasat formu) → alacak sorusu → elek → "elek gireceğim" → salamura/bahçe/gönderi → işlem →
 * hazır cevap / anlaşılmadı → ekran aç / kayıtlardan soru.
 *
 * İnternet yardımcısı ayrı bir beyindir ve yalnız kuralların anlamadığı ya da soru sandığı iş cümlesinde,
 * asistanın yürütme adımında denenir (Oturum 2'de CloudBrain olarak bu arayüze oturur).
 */
final class RuleBrain implements Brain {
    static final String NAME = Brain.RULES;

    /** Kapanış sözü (ZeytinAssistantController.submit'ten taşındı). */
    private static final String CLOSE = "(tesekkur\\p{L}*|sag ol\\p{L}*|sagol\\p{L}*|tamam bu kadar|bu kadar|yeter|kapat"
            + "|gorusuruz|hosca kal|eyvallah)( .*)?";
    /** "Elek gireceğim / elek kaydı" (ZeytinAssistantController.run'dan taşındı). */
    private static final String ELEK_WORD = ".*\\belek\\b.*";
    private static final String ELEK_VERB = ".*\\b(gir\\p{L}*|kaydet\\p{L}*|kaydi|ekle\\p{L}*|yaz\\p{L}*|yapacag\\p{L}*|baslat|basla\\p{L}*)\\b.*";
    private static final String QUESTION_WORD = ".*(^|\\s)(kac|ne|mi|mu|hangi|nasil|son)(\\s|$).*";
    private static final String[] GRADE_KEYS = {"double", "1no", "2no", "3no", "4no", "yaglik"};

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Decision decide(Input in, BrainContext c) {
        if (c.screen == BrainContext.Screen.ELEK) {
            return new ElekRules().decide(in, c);
        }
        if (c.conversation) {
            Decision talk = conversationStage(in, c);
            if (talk != null) {
                return talk;
            }
        }
        if (!c.recordsKnown) {
            throw new IllegalStateException("Kayıt aşaması için bahçe bilgisi gerekli (BrainContext.withRecords)");
        }
        return recordStage(in, c, ZeytinAssistantCommand.parse(in.raw));
    }

    /**
     * Konuşma aşaması: kayıt okumadan karar verilebilen sözler. Karar yoksa null (kayıt aşamasına geçilir).
     * Sohbet cevabı persona'nın durumunu değiştirebilir; asistan sonra kaydeder.
     */
    Decision conversationStage(Input in, BrainContext c) {
        if (c.closeAllowed && in.text.matches(CLOSE)) {
            return found(ActionCatalog.KAPAT, null, "", "Teşekkür/kapanış sözü", null);
        }
        if (!c.commandsAllowed) {
            return null;
        }
        if (AssistantMisses.asksClear(in.raw)) {
            return found(ActionCatalog.ANLAMADIKLARI_SIL, null, "", "Anlamadıklarım listesini silme isteği", null);
        }
        if (AssistantMisses.asksShow(in.raw)) {
            return found(ActionCatalog.ANLAMADIKLARI_GOSTER, null, "", "Anlamadıklarım listesini görme isteği", null);
        }
        if (AssistantScorecard.asksClearAll(in.raw)) {
            return found(ActionCatalog.KONUSMA_KAYDI_SIL, null, "", "Konuşma kaydını ve karneyi silme isteği", null);
        }
        if (AssistantScorecard.asksShow(in.raw)) {
            return found(ActionCatalog.KARNE_GOSTER, null, "", "Karne isteği", null);
        }
        Decision cloud = cloudCommand(in, c);
        if (cloud != null) {
            return cloud;
        }
        if (c.chat != null) {
            String chat = c.chat.apply(in.raw);
            if (chat != null) {
                return found(ActionCatalog.SOHBET, null, "", "Sohbet sözü", chat);
            }
        }
        return null;
    }

    private static Decision cloudCommand(Input in, BrainContext c) {
        if (AssistantCloud.asksStatus(in.raw)) {
            return found(ActionCatalog.BULUT_DURUM, null, "", "İnternet yardımcısının durumu soruldu", null);
        }
        int toggle = AssistantCloud.toggleCommand(in.raw);
        if (toggle == 1) {
            return found(ActionCatalog.BULUT_AC, null, "", "İnternet yardımcısını açma isteği", null);
        }
        if (toggle == 0) {
            return found(ActionCatalog.BULUT_KAPAT, null, "", "İnternet yardımcısını kapatma isteği", null);
        }
        if (!c.cloudConsentPending) {
            return null;
        }
        int consent = AssistantCloud.consent(in.raw);
        if (consent == 1) {
            return found(ActionCatalog.BULUT_IZIN_EVET, null, "", "İnternet yardımcısına izin verildi", null);
        }
        if (consent == 0) {
            return found(ActionCatalog.BULUT_IZIN_HAYIR, null, "", "İnternet yardımcısına izin verilmedi", null);
        }
        return null;
    }

    /** Kayıt aşaması (kayıtlar okunduktan sonra). cmd: cümlenin soru çözümü (asistan zaten ayrıştırdıysa aynı nesne). */
    Decision recordStage(Input in, BrainContext c, ZeytinAssistantCommand cmd) {
        String raw = in.raw;
        VoiceRecordDialog.Kind recKind = VoiceRecordDialog.detect(raw);
        if (recKind == VoiceRecordDialog.Kind.HARVEST && c.hasGardens) {
            return found(ActionCatalog.HASAT, harvestFields(raw), "soru", "Toplama sözü: hasat", recKind);
        }
        HarvestVoiceEntry harvest = c.hasGardens ? null : HarvestVoiceEntry.parse(raw);
        if (harvest != null) {
            return found(ActionCatalog.HASAT, harvestFields(raw), "form", "Toplama sözü ve kilo: hasat formu", harvest);
        }
        if (AssistantReceivables.asks(raw)) {
            return found(ActionCatalog.ALACAK_SORU, null, "", "Alacak/borç sorusu", null);
        }
        Decision elek = ElekRules.decide(raw, BrainContext.Screen.ASSISTANT);
        if (elek != null) {
            return elek;
        }
        if (opensElek(in.text)) {
            return found(ActionCatalog.ELEK_AC, null, "", "Elek girme isteği", null);
        }
        if (recKind != null && recKind != VoiceRecordDialog.Kind.HARVEST) {
            return found("KAYIT_" + recKind.name(), null, "", "Kayıt sözü: " + recKind.name(), recKind);
        }
        OperationVoiceEntry op = OperationVoiceEntry.parse(raw);
        if (op != null) {
            String variant = c.hasGardens ? "soru" : "form";
            return found(ActionCatalog.ISLEM, operationFields(op), variant, "Bahçe işi sözü: " + OperationKinds.TYPES[op.type], op);
        }
        return question(raw, cmd);
    }

    /**
     * Soru, ekran açma, hazır cevap ya da anlaşılmadı. Asistanın "Anladığım" etiketinden yeniden sorma da buradan
     * geçer (cümle yeniden ayrıştırılmaz, etiketle değişen soru nesnesi kullanılır).
     */
    static Decision question(String raw, ZeytinAssistantCommand cmd) {
        if (cmd.kind == ZeytinAssistantCommand.Kind.UNKNOWN) {
            String special = ZeytinAssistantText.special(raw);
            if (special != null) {
                return found(ActionCatalog.OZEL_CEVAP, null, "", "Hazır cevabı olan söz", special);
            }
            if (ZeytinAssistantHints.chatOnly(raw)) {
                return new Decision(ActionCatalog.ANLASILMADI, null, 0, "Konusu olmayan sohbet", "sohbet",
                        ActionCatalog.Level.ONER, cmd, NAME);
            }
            return new Decision(ActionCatalog.ANLASILMADI, null, 0, "Hiçbir kalıba uymadı", "ipucu",
                    ActionCatalog.Level.ONER, cmd, NAME);
        }
        Map<String, String> f = new LinkedHashMap<>();
        if (cmd.kind == ZeytinAssistantCommand.Kind.NAVIGATION) {
            f.put("ekran", cmd.target);
            return found(ActionCatalog.EKRAN_AC, f, "", "Ekran açma isteği", cmd);
        }
        if (!cmd.grade.isEmpty()) {
            f.put("boy", cmd.grade);
        }
        if (!cmd.opType.isEmpty()) {
            f.put("islem", cmd.opType);
        }
        return found(ActionCatalog.SORU + cmd.kind.name(), f, "", "Kayıtlardan soru", cmd);
    }

    private static boolean opensElek(String text) {
        boolean elek = text.matches(ELEK_WORD);
        boolean verb = text.matches(ELEK_VERB);
        boolean question = text.matches(QUESTION_WORD);
        return elek && verb && !question;
    }

    private static Decision found(String action, Map<String, String> fields, String variant, String reason, Object payload) {
        ActionCatalog.Action a = ActionCatalog.get(action);
        ActionCatalog.Level level = a == null ? ActionCatalog.Level.ONER : a.level;
        return new Decision(action, fields, 1, reason, variant, level, payload, NAME);
    }

    // ============================================================ ALANLAR (cümle bankasının yazımı; elek: ElekRules)

    static Map<String, String> harvestFields(String raw) {
        Map<String, String> f = new LinkedHashMap<>();
        HarvestVoiceEntry h = HarvestVoiceEntry.parse(raw);
        if (h == null) {
            return f;
        }
        for (int i = 0; i < h.kg.length; i++) {
            if (h.kg[i] > 0) {
                f.put(GRADE_KEYS[i], Decision.num(h.kg[i]));
            }
        }
        f.put("toplam", Decision.num(h.total));
        if (h.dayOffset != 0) {
            f.put("gun", String.valueOf(h.dayOffset));
        }
        return f;
    }

    static Map<String, String> operationFields(OperationVoiceEntry e) {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("tur", OperationKinds.TYPES[e.type]);
        if (!e.note.isEmpty()) {
            f.put("not", e.note);
        }
        if (e.dayOffset != 0) {
            f.put("gun", String.valueOf(e.dayOffset));
        }
        if (e.quantity > 0) {
            f.put("miktar", Decision.num(e.quantity));
            f.put("birim", e.unit);
        }
        if (e.cost > 0) {
            f.put("tutar", Decision.num(e.cost));
        }
        return f;
    }
}
