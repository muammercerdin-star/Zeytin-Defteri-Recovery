package com.zeytindefteri.app;

/**
 * Beyne verilen bağlam (İP3; saf). Beyin cümleyi tek başına değil, konuşmanın durumuyla okur. Karar aşamaları:
 *
 * 1. Konuşma aşaması (ekranda, kayıtlar okunmadan): kapanış, anlamadıklarım/karne/internet yardımcısı komutları,
 *    sohbet. Açık sorulu konuşma (işlem/hasat soruları) varken bu aşama, eski zincirdeki gibi kısıtlanır.
 * 2. Kayıt aşaması (kayıtlar okunduktan sonra): hasat, alacak, elek, işlem, sorular. Bahçe olup olmadığı burada bilinir.
 *
 * Asistan önce konuşma aşamasını sorar; karar "kayıtlar gerekli" ise kayıtları okuyup kayıt aşamasını sorar.
 * Cümle bankası ve testler ikisini birden tek çağrıda sorar (recordsKnown + conversation).
 */
final class BrainContext {
    /** Cümle nereden geldi. Bugün asistan (ses ve yazı aynı kapıdan); Elek "Sesle doldur" Oturum 2'de eklenecek. */
    enum Screen { ASSISTANT }

    final Screen screen;
    /** Konuşma aşaması sorulsun mu (asistanda ilk soru; kayıt aşamasında ve izin sonrası yeniden sormada hayır). */
    final boolean conversation;
    /** Kapanış sözü dinlensin mi (açık işlem ya da kayıt sorusu yokken). */
    final boolean closeAllowed;
    /** Komutlar ve sohbet dinlensin mi (açık kayıt sorusu yokken). */
    final boolean commandsAllowed;
    /** İnternet yardımcısı için izin sorusu bekliyor. */
    final boolean cloudConsentPending;
    /** Kayıtlar okundu mu (bahçe bilgisi geçerli mi); hayırsa kayıt aşamasına gelince "kayıtlar gerekli" döner. */
    final boolean recordsKnown;
    final boolean hasGardens;
    /** Konuşmadaki sohbet durumu (bekleyen soru, ad); sohbet aşaması bunu kullanır ve değiştirebilir. */
    final AssistantPersona persona;

    private BrainContext(Screen screen, boolean conversation, boolean closeAllowed, boolean commandsAllowed,
                         boolean cloudConsentPending, boolean recordsKnown, boolean hasGardens, AssistantPersona persona) {
        this.screen = screen;
        this.conversation = conversation;
        this.closeAllowed = closeAllowed;
        this.commandsAllowed = commandsAllowed;
        this.cloudConsentPending = cloudConsentPending;
        this.recordsKnown = recordsKnown;
        this.hasGardens = hasGardens;
        this.persona = persona;
    }

    /** Asistanda yeni cümle (kayıtlar henüz okunmadı). */
    static BrainContext assistant(boolean closeAllowed, boolean commandsAllowed, boolean cloudConsentPending, AssistantPersona persona) {
        return new BrainContext(Screen.ASSISTANT, true, closeAllowed, commandsAllowed, cloudConsentPending, false, false, persona);
    }

    /** Kayıtlar okundu: kayıt aşaması (konuşma aşaması bu cümle için zaten soruldu ya da atlanıyor). */
    BrainContext withRecords(boolean gardens) {
        return new BrainContext(screen, false, closeAllowed, commandsAllowed, cloudConsentPending, true, gardens, persona);
    }

    /** İnternet yardımcısına izin verildi, bekleyen cümle yeniden: yalnız kayıt aşaması (eski run() gibi). */
    static BrainContext recordsOnly(boolean hasGardens) {
        return new BrainContext(Screen.ASSISTANT, false, false, false, false, true, hasGardens, null);
    }

    /** Testler ve cümle bankası: iki aşama tek çağrıda (taze sohbet, bahçe var/yok). */
    static BrainContext full(boolean hasGardens, AssistantPersona persona) {
        return new BrainContext(Screen.ASSISTANT, true, true, true, false, true, hasGardens, persona);
    }

    /** Testler: eski zincirin bütün bayraklarıyla. */
    static BrainContext of(boolean closeAllowed, boolean commandsAllowed, boolean cloudConsentPending, boolean hasGardens, AssistantPersona persona) {
        return new BrainContext(Screen.ASSISTANT, true, closeAllowed, commandsAllowed, cloudConsentPending, true, hasGardens, persona);
    }

}
