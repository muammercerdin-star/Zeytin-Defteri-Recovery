package com.zeytindefteri.app;

/**
 * Asistan bir karardan sonra ekranda ne yapar (saf, Android'siz, testli). ZeytinAssistantController kararı
 * beyinden alır, bu sınıfa ne yapılacağını sorar ve yalnız onu yürütür (form açmak, soru sormak, cevap yazmak).
 *
 * İki aşama vardır, çünkü arada internet yardımcısı denenebilir (çalışma anına bağlıdır, burada değil):
 * önce kayıt yolları (records), bunlar tutmazsa yardımcı, o da çözemezse cevap yolları (rest).
 * Sıra İP4 sonundaki (cea13a1) asistan zincirinin sırasıdır; tests/assistant_outcome bunu dondurur.
 */
final class AssistantOutcome {
    private AssistantOutcome() {}

    enum Kind {
        /** "Girelli'den topladık": hasat soruları (bahçe, kilo...). */
        HARVEST_QUESTIONS,
        /** Kilosu söylenmiş hasat: hasat formu dolu açılır. */
        HARVEST_FORM,
        /** "Kim bana borçlu?": açık alacaklar. */
        RECEIVABLES,
        /** "3 numara 120 kilo": elek formu dolu açılır. */
        ELEK_FORM,
        /** "Elek gireceğim": elek ekranı, sesli elek. */
        ELEK_SCREEN,
        /** Salamura, bahçe gibi sesli kayıt soruları. */
        RECORD_QUESTIONS,
        /** Bahçe işi: eksikler tek tek sorulur, "evet"le kaydedilir. */
        OPERATION_QUESTIONS,
        /** Bahçe işi: işlem formu dolu açılır. */
        OPERATION_FORM,
        /** Çok adımlı iş (İP7): satış + alacak + ödeme hatırlatması; form görünür dolar, tek onayla yazılır. */
        MULTI_STEP,
        /** Tahsilat (eğitim turu 1): alıcının "Ödeme aldım" penceresi söylenen tutarla açılır; kayıt Kaydet'le. */
        COLLECTION,
        /** Kayıt yolu yok: internet yardımcısı denenir, sonra rest(). */
        LATER,
        /** Selam, teşekkür, ilaç dozu gibi hazır cevap. */
        SPECIAL,
        /** Tanınmayan iş fiili: "Bakırladık ne işi?" (İP4). */
        TEACH,
        /** Konusu olmayan sohbet: dostça cevap. */
        CHAT,
        /** Kalıba uymadı: yakın sorular ya da konu kartları. */
        HINT,
        /** Ekran açma. */
        NAVIGATE,
        /** Bölge fiyatı sorusu. */
        MARKET,
        /** Kayıtlardan cevap (hava, bugün, sayılar...). */
        ANSWER
    }

    /** Kayıt yolları. openForm: öğretme penceresinden gelindi, işlem soruları yerine form açılır. */
    static Kind records(Decision d, boolean openForm) {
        if (d.is(ActionCatalog.HASAT) && "soru".equals(d.variant)) {
            return Kind.HARVEST_QUESTIONS;
        }
        if (LearnedBrain.payload(d, HarvestVoiceEntry.class) != null) {
            return Kind.HARVEST_FORM;
        }
        if (d.is(ActionCatalog.ALACAK_SORU)) {
            return Kind.RECEIVABLES;
        }
        if (LearnedBrain.payload(d, ElekVoiceEntry.class) != null) {
            return Kind.ELEK_FORM;
        }
        if (d.is(ActionCatalog.ELEK_AC)) {
            return Kind.ELEK_SCREEN;
        }
        if (LearnedBrain.payload(d, VoiceRecordDialog.Kind.class) != null) {
            return Kind.RECORD_QUESTIONS;
        }
        OperationVoiceEntry op = LearnedBrain.payload(d, OperationVoiceEntry.class);
        if (op != null && "soru".equals(d.variant) && !openForm) {
            return Kind.OPERATION_QUESTIONS;
        }
        if (op != null) {
            return Kind.OPERATION_FORM;
        }
        if (LearnedBrain.payload(d, ActionPlan.class) != null) {
            return Kind.MULTI_STEP;
        }
        if (LearnedBrain.payload(d, CollectionEntry.class) != null) {
            return Kind.COLLECTION;
        }
        return Kind.LATER;
    }

    /**
     * Cevap yolları (yardımcıdan sonra). teach: öğretme sorusunun sözü (LearnedBrain.teachQuestion; yoksa null).
     * navigation / regionMarket: cümlenin soru çözümünden (ekran açma, bölge fiyatı).
     */
    static Kind rest(Decision d, String teach, boolean navigation, boolean regionMarket) {
        if (d.is(ActionCatalog.OZEL_CEVAP) || !d.understood()) {
            if (LearnedBrain.payload(d, String.class) != null) {
                return Kind.SPECIAL;
            }
            if (teach != null) {
                return Kind.TEACH;
            }
            if ("sohbet".equals(d.variant)) {
                return Kind.CHAT;
            }
            return Kind.HINT;
        }
        if (navigation) {
            return Kind.NAVIGATE;
        }
        if (regionMarket) {
            return Kind.MARKET;
        }
        return Kind.ANSWER;
    }
}
