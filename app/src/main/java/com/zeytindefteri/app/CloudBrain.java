package com.zeytindefteri.app;

import java.util.List;

/**
 * İnternet yardımcısı beyni (İP3): kuralların anlamadığı ya da soru sandığı iş cümlesini internetteki dil modeline
 * sorar. Dil modeli yalnız tercümandır: cevabı AssistantCloud.accept cümleyle ve bahçe adlarıyla karşılaştırır,
 * uydurduğu bahçe, sayı ya da tutar atılır; denetimden geçmeyen cevap ANLASILMADI'dır.
 *
 * Bu beyin kendi başına hiçbir şey yazmaz: kararı (ISLEM) asistan sesli sorularla doldurur ve çiftçinin "evet"i
 * olmadan kaydetmez (ActionGate + Approval). İzin, internet bağlantısı ve "kapalı" ayarı asistanın işidir;
 * burada yalnız cümlenin yardımcıya uygun olup olmadığı ve cevabın denetimi vardır.
 */
final class CloudBrain implements Brain {
    static final String NAME = "internet";
    /** Denetimden geçmiş tercümenin güveni: kural eşleşmesinden düşük (çiftçiye yine sorulur). */
    static final double CONFIDENCE = 0.7;

    /** Yardımcıya soran (ağ işi): cümle + bahçe adları → ham cevap (JSON) ya da null. */
    interface Asker {
        String ask(String raw, List<String> gardens);
    }

    private final Asker asker;

    CloudBrain(Asker asker) {
        this.asker = asker;
    }

    @Override
    public String name() {
        return NAME;
    }

    /**
     * Cümle yardımcıya gidebilir mi (kural beyninin kararından sonra): ekran açma komutu ve hazır cevabı olan söz
     * gitmez, bahçesi olmayana sorulmaz, soru kalıbı ve sohbet cümlesi AssistantCloud.eligible ile ayrılır.
     */
    static boolean wants(String raw, ZeytinAssistantCommand cmd, List<String> gardens) {
        if (gardens == null || gardens.isEmpty()) {
            return false;
        }
        if (cmd.kind == ZeytinAssistantCommand.Kind.NAVIGATION) {
            return false;
        }
        if (ZeytinAssistantText.special(raw) != null) {
            return false;
        }
        return AssistantCloud.eligible(raw, gardens);
    }

    @Override
    public Decision decide(Input in, BrainContext context) {
        List<String> names = context.gardenNames;
        String response = asker.ask(in.raw, names);
        AssistantCloud.Result result = AssistantCloud.accept(response, in.raw, names);
        if (result == null) {
            return Decision.miss("", "Yardımcının cevabı cümleyle uyuşmadı ya da cevap yok", NAME);
        }
        ActionCatalog.Action a = ActionCatalog.get(ActionCatalog.ISLEM);
        String reason = "İnternet yardımcısı: " + OperationKinds.TYPES[result.entry.type] + " (cümleyle denetlendi)";
        return new Decision(ActionCatalog.ISLEM, RuleBrain.operationFields(result.entry), CONFIDENCE, reason, "soru",
                a.level, result, NAME);
    }
}
