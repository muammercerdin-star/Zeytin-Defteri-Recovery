package com.zeytindefteri.app;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bir beynin kararı (İP3): hangi eylem, hangi bilgilerle, ne kadar emin, neden. Beyin değişse de çıkış hep budur;
 * onay kuralları, geri al, ekranlar ve Tanecik aynı kalır. Karar kendi başına hiçbir şey yazmaz: yürütmeyi
 * ActionGate'in izin verdiği basamakta asistan yapar.
 */
final class Decision {
    /** ActionCatalog kimliği (ELEK, ISLEM, SORU_SPRAY, ANLASILMADI...). */
    final String action;
    /** Alanlar, okunan sırayla ("double" → "2", "double_fiyat" → "110"); değiştirilemez. */
    final Map<String, String> fields;
    /** 0–1: kural beyninde eşleşme 1, anlaşılmadı 0. */
    final double confidence;
    /** Gerekçe: neden bu eylem (kısa, Türkçe; öneride gösterilebilir). */
    final String reason;
    /** Aynı eylemin yürütme yolu: "soru" (sorarak doldur) / "form" (formu aç); "sohbet" / "ipucu"; yoksa boş. */
    final String variant;
    /** Beynin istediği basamak; ActionGate katalog ve onay kuralına göre kısar. */
    final ActionCatalog.Level requested;
    /** Çözücünün nesnesi (ElekVoiceEntry, OperationVoiceEntry, ZeytinAssistantCommand, sohbet cevabı...). */
    final Object payload;
    /** Kararı veren beyin ("kural", "internet"). */
    final String brain;

    Decision(String action, Map<String, String> fields, double confidence, String reason, String variant,
             ActionCatalog.Level requested, Object payload, String brain) {
        this.action = action;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields == null ? new LinkedHashMap<>() : fields));
        this.confidence = Math.max(0, Math.min(1, confidence));
        this.reason = reason == null ? "" : reason;
        this.variant = variant == null ? "" : variant;
        this.requested = requested == null ? ActionCatalog.Level.ONER : requested;
        this.payload = payload;
        this.brain = brain == null ? "" : brain;
    }

    /** "Anlamadım" kararı. */
    static Decision miss(String variant, String reason, String brain) {
        return new Decision(ActionCatalog.ANLASILMADI, null, 0, reason, variant, ActionCatalog.Level.ONER, null, brain);
    }

    boolean understood() {
        return !ActionCatalog.ANLASILMADI.equals(action);
    }

    boolean is(String id) {
        return action.equals(id);
    }

    /** Payload belirtilen türdeyse o, değilse null. */
    <T> T payload(Class<T> type) {
        if (type.isInstance(payload)) {
            return type.cast(payload);
        }
        return null;
    }

    /** Alan değeri olarak sayı: tam sayı "120", kesirli "2.5". */
    static String num(double v) {
        if (v == Math.floor(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    /** Alanlar "k=v;k=v" (yoksa "-"): cümle bankası ve testlerin yazımı. */
    String fieldsText() {
        if (fields.isEmpty()) {
            return "-";
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (b.length() > 0) {
                b.append(';');
            }
            b.append(e.getKey()).append('=').append(e.getValue());
        }
        return b.toString();
    }

    @Override
    public String toString() {
        return action + " " + fieldsText() + (variant.isEmpty() ? "" : " (" + variant + ")");
    }
}
