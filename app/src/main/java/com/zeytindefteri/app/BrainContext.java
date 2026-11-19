package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Beyne verilen bağlam (İP3; saf, hafif: yalnız Java). Beyin cümleyi tek başına değil, konuşmanın durumuyla okur.
 *
 * Asistanda iki aşama vardır:
 * 1. Konuşma aşaması (ekranda, kayıtlar okunmadan): kapanış, anlamadıklarım/karne/internet yardımcısı komutları,
 *    sohbet. Açık sorulu konuşma (işlem/hasat soruları) varken bu aşama, eski zincirdeki gibi kısıtlanır.
 * 2. Kayıt aşaması (kayıtlar okunduktan sonra): hasat, alacak, elek, işlem, sorular. Bahçeler burada bilinir.
 *
 * Elek ekranındaki "Sesle doldur" aynı kapıdan, ELEK ekranı bağlamıyla girer: orada cümle yalnız elek olarak okunur.
 * Cümle bankası ve testler iki aşamayı tek çağrıda sorar.
 */
final class BrainContext {
    /** Cümle nereden geldi: asistan (ses ve yazı aynı kapıdan) ya da Elek ekranındaki "Sesle doldur". */
    enum Screen { ASSISTANT, ELEK }

    final Screen screen;
    /** Konuşma aşaması sorulsun mu (asistanda ilk soru; kayıt aşamasında ve izin sonrası yeniden sormada hayır). */
    final boolean conversation;
    /** Kapanış sözü dinlensin mi (açık işlem ya da kayıt sorusu yokken). */
    final boolean closeAllowed;
    /** Komutlar ve sohbet dinlensin mi (açık kayıt sorusu yokken). */
    final boolean commandsAllowed;
    /** İnternet yardımcısı için izin sorusu bekliyor. */
    final boolean cloudConsentPending;
    /** Kayıtlar okundu mu (bahçe bilgisi geçerli mi). */
    final boolean recordsKnown;
    final boolean hasGardens;
    /** Bahçe adları (internet yardımcısı cümledeki bahçeyi bunlarla eşler); okunmadıysa boş. */
    final List<String> gardenNames;
    /** Sohbet: cümleye sohbet cevabı (yoksa null). Asistanda kişiliğin handle'ı; durumu değiştirebilir. */
    final Function<String, String> chat;

    private BrainContext(Screen screen, boolean conversation, boolean closeAllowed, boolean commandsAllowed,
                         boolean cloudConsentPending, boolean recordsKnown, boolean hasGardens, List<String> gardenNames,
                         Function<String, String> chat) {
        this.screen = screen;
        this.conversation = conversation;
        this.closeAllowed = closeAllowed;
        this.commandsAllowed = commandsAllowed;
        this.cloudConsentPending = cloudConsentPending;
        this.recordsKnown = recordsKnown;
        this.hasGardens = hasGardens;
        List<String> names = gardenNames == null ? new ArrayList<String>() : new ArrayList<>(gardenNames);
        this.gardenNames = Collections.unmodifiableList(names);
        this.chat = chat;
    }

    /** Asistanda yeni cümle (kayıtlar henüz okunmadı). */
    static BrainContext assistant(boolean closeAllowed, boolean commandsAllowed, boolean cloudConsentPending, Function<String, String> chat) {
        return new BrainContext(Screen.ASSISTANT, true, closeAllowed, commandsAllowed, cloudConsentPending, false, false, null, chat);
    }

    /** Kayıtlar okundu: yalnız kayıt aşaması (eski run() gibi; izin sonrası yeniden sormada da). */
    static BrainContext recordsOnly(List<String> gardenNames) {
        boolean gardens = gardenNames != null && !gardenNames.isEmpty();
        return new BrainContext(Screen.ASSISTANT, false, false, false, false, true, gardens, gardenNames, null);
    }

    /** Elek ekranındaki "Sesle doldur": cümle yalnız elek olarak okunur. */
    static BrainContext elekScreen() {
        return new BrainContext(Screen.ELEK, false, false, false, false, true, true, null, null);
    }

    /** Testler ve cümle bankası: iki aşama tek çağrıda (taze sohbet, bahçe var/yok). */
    static BrainContext full(boolean hasGardens, Function<String, String> chat) {
        return new BrainContext(Screen.ASSISTANT, true, true, true, false, true, hasGardens, null, chat);
    }

    /** Testler: eski zincirin bütün bayraklarıyla. */
    static BrainContext of(boolean closeAllowed, boolean commandsAllowed, boolean cloudConsentPending, boolean hasGardens, Function<String, String> chat) {
        return new BrainContext(Screen.ASSISTANT, true, closeAllowed, commandsAllowed, cloudConsentPending, true, hasGardens, null, chat);
    }
}
