package com.zeytindefteri.app;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DONDURULMUŞ KOPYA — yalnız birebir aynılık testi için. Taşımadan önceki ZeytinAssistantController.submit() ve
 * run() karar zincirinin (commit f5ccb3a) koşulları, aynı sırayla ve aynı ifadelerle. Uygulamada kullanılmaz;
 * RuleBrain bu zincirle her cümlede aynı kararı vermelidir. Yürütme (form açma, cevap yazma) burada yoktur.
 *
 * Çıktı sözlüğü RuleBrain ile aynıdır: eylem, alanlar ("k=v;..."), değişke (soru/form, sohbet/ipucu).
 */
final class LegacyRouter {
    private LegacyRouter() {
    }

    /** Eski zincirin bağlamı (controller alanları). */
    static final class Ctx {
        boolean opDialogNull = true;
        boolean recDialogNull = true;
        boolean pendingCloud;
        boolean gardensEmpty;
        AssistantPersona persona = AssistantPersona.decode("");
    }

    static String[] route(String raw, Ctx c) {
        // submit()
        if (c.opDialogNull && c.recDialogNull && TurkishText.normalize(raw).matches("(tesekkur\\p{L}*|sag ol\\p{L}*|sagol\\p{L}*|tamam bu kadar|bu kadar|yeter|kapat|gorusuruz|hosca kal|eyvallah)( .*)?")) {
            return out("KAPAT", null, "");
        }
        if (c.recDialogNull && AssistantMisses.asksClear(raw)) {
            return out("ANLAMADIKLARI_SIL", null, "");
        }
        if (c.recDialogNull && AssistantMisses.asksShow(raw)) {
            return out("ANLAMADIKLARI_GOSTER", null, "");
        }
        if (c.recDialogNull && AssistantScorecard.asksClearAll(raw)) {
            return out("KONUSMA_KAYDI_SIL", null, "");
        }
        if (c.recDialogNull && AssistantScorecard.asksShow(raw)) {
            return out("KARNE_GOSTER", null, "");
        }
        if (c.recDialogNull) {
            if (AssistantCloud.asksStatus(raw)) {
                return out("BULUT_DURUM", null, "");
            }
            int toggle = AssistantCloud.toggleCommand(raw);
            if (toggle >= 0) {
                return out(toggle == 1 ? "BULUT_AC" : "BULUT_KAPAT", null, "");
            }
            if (c.pendingCloud) {
                int consent = AssistantCloud.consent(raw);
                if (consent == 1) {
                    return out("BULUT_IZIN_EVET", null, "");
                }
                if (consent == 0) {
                    return out("BULUT_IZIN_HAYIR", null, "");
                }
            }
        }
        if (c.recDialogNull) {
            String chat = c.persona.handle(raw);
            if (chat != null) {
                return out("SOHBET", null, "");
            }
        }
        return run(raw, ZeytinAssistantCommand.parse(raw), c);
    }

    /** run(raw, cmd, null): soru etiketinden yeniden sorma (pick != null) bu zincire girmez. */
    static String[] run(String raw, ZeytinAssistantCommand cmd, Ctx c) {
        final VoiceRecordDialog.Kind recKind = VoiceRecordDialog.detect(raw);
        if (recKind == VoiceRecordDialog.Kind.HARVEST && !c.gardensEmpty) {
            return out("HASAT", harvest(raw), "soru");
        }
        final HarvestVoiceEntry harvest = c.gardensEmpty ? HarvestVoiceEntry.parse(raw) : null;
        if (harvest != null) {
            return out("HASAT", harvest(raw), "form");
        }
        if (AssistantReceivables.asks(raw)) {
            return out("ALACAK_SORU", null, "");
        }
        final ElekVoiceEntry voice = ElekVoiceEntry.parse(raw);
        if (voice != null) {
            return out("ELEK", elek(voice), "");
        }
        if (TurkishText.normalize(raw).matches(".*\\belek\\b.*") && TurkishText.normalize(raw).matches(".*\\b(gir\\p{L}*|kaydet\\p{L}*|kaydi|ekle\\p{L}*|yaz\\p{L}*|yapacag\\p{L}*|baslat|basla\\p{L}*)\\b.*")
                && !TurkishText.normalize(raw).matches(".*(^|\\s)(kac|ne|mi|mu|hangi|nasil|son)(\\s|$).*")) {
            return out("ELEK_AC", null, "");
        }
        if (recKind != null && recKind != VoiceRecordDialog.Kind.HARVEST) {
            return out("KAYIT_" + recKind.name(), null, "");
        }
        final OperationVoiceEntry op = OperationVoiceEntry.parse(raw);
        if (op != null && !c.gardensEmpty) {
            return out("ISLEM", operation(op), "soru");
        }
        if (op != null) {
            return out("ISLEM", operation(op), "form");
        }
        // (internet yardımcısı burada denenir: yürütme işi, karar zincirinin dışında)
        if (cmd.kind == ZeytinAssistantCommand.Kind.UNKNOWN) {
            String special = ZeytinAssistantText.special(raw);
            if (special != null) {
                return out("OZEL_CEVAP", null, "");
            }
            if (ZeytinAssistantHints.chatOnly(raw)) {
                return out("ANLASILMADI", null, "sohbet");
            }
            return out("ANLASILMADI", null, "ipucu");
        }
        Map<String, String> f = new LinkedHashMap<>();
        if (cmd.kind == ZeytinAssistantCommand.Kind.NAVIGATION) {
            f.put("ekran", cmd.target);
            return out("EKRAN_AC", f, "");
        }
        if (!cmd.grade.isEmpty()) {
            f.put("boy", cmd.grade);
        }
        if (!cmd.opType.isEmpty()) {
            f.put("islem", cmd.opType);
        }
        return out("SORU_" + cmd.kind.name(), f, "");
    }

    // Alan yazımı: İP1/İP2 cümle bankası sınayıcısındaki biçim (bankadaki beklenen değerler bu biçimdedir).

    private static final String[] GRADE_KEYS = {"double", "1no", "2no", "3no", "4no", "yaglik"};

    static Map<String, String> elek(ElekVoiceEntry e) {
        Map<String, String> f = new LinkedHashMap<>();
        for (int i = 0; i < e.kg.length; i++) {
            if (e.kg[i] > 0) {
                f.put(GRADE_KEYS[i], num(e.kg[i]));
            }
            if (e.price[i] > 0) {
                f.put(GRADE_KEYS[i] + "_fiyat", num(e.price[i]));
            }
        }
        if (e.sold != null) {
            f.put("satis", e.sold ? "evet" : "hayir");
        }
        if (!e.buyer.isEmpty()) {
            f.put("alici", e.buyer);
        }
        return f;
    }

    static Map<String, String> harvest(String raw) {
        Map<String, String> f = new LinkedHashMap<>();
        HarvestVoiceEntry h = HarvestVoiceEntry.parse(raw);
        if (h == null) {
            return f;
        }
        for (int i = 0; i < h.kg.length; i++) {
            if (h.kg[i] > 0) {
                f.put(GRADE_KEYS[i], num(h.kg[i]));
            }
        }
        f.put("toplam", num(h.total));
        if (h.dayOffset != 0) {
            f.put("gun", String.valueOf(h.dayOffset));
        }
        return f;
    }

    static Map<String, String> operation(OperationVoiceEntry e) {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("tur", OperationKinds.TYPES[e.type]);
        if (e.dayOffset != 0) {
            f.put("gun", String.valueOf(e.dayOffset));
        }
        if (e.quantity > 0) {
            f.put("miktar", num(e.quantity));
            f.put("birim", e.unit);
        }
        if (e.cost > 0) {
            f.put("tutar", num(e.cost));
        }
        return f;
    }

    static String num(double v) {
        if (v == Math.floor(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    static String[] out(String action, Map<String, String> fields, String variant) {
        StringBuilder b = new StringBuilder();
        if (fields != null) {
            for (Map.Entry<String, String> e : fields.entrySet()) {
                if (b.length() > 0) {
                    b.append(';');
                }
                b.append(e.getKey()).append('=').append(e.getValue());
            }
        }
        return new String[]{action, b.length() == 0 ? "-" : b.toString(), variant};
    }
}
