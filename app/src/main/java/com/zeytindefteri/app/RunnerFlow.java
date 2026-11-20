package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Çok adımlı eylemin onay ve geri al kuralı (İP7; saf, Android'siz, testli).
 *
 * - Doldurma ve soru sırasında hiçbir şey yazılmaz; yarım kalan akış (geri, Hayır, ekran kapanması) iz bırakmaz.
 * - Tek "Kaydedeyim mi?" onayıyla adımlar sırayla yazılır (satış ve alacağı, sonra ödeme hatırlatması). Bir adım
 *   yazılamazsa önceki adımlar da geri alınır: ya hepsi ya hiçbiri.
 * - Onaylanan her şey tek "Geri al" ile birlikte, ters sırayla geri alınır.
 * Kayıt, satış, ödeme ve silme hiçbir izinle onaysız yapılmaz (izin yalnız hatırlatma gibi zararsız işler içindir).
 */
final class RunnerFlow {
    enum State { FILLING, CONFIRMING, SAVED, CANCELLED, UNDONE, FAILED }

    /** Gerçek yazma (Android'de SaleActivity + hatırlatmalar; testte sahte). Dönüş: kayıt numarası, olmadıysa ≤ 0. */
    interface Writer {
        /** Satışı (alacağıyla birlikte, tek veritabanı işleminde) yazar. Form eksikse 0 (hiçbir şey yazılmadı), hata -1. */
        long writeSale();

        /** Satışın ödeme günü hatırlatması. */
        long writeReminder(long saleId);

        /** Satışı ve alacağını siler. */
        boolean deleteSale(long saleId);

        boolean deleteReminder(long reminderId);

        /** Bu akışta yeni açılan ve başka kaydı olmayan alıcıyı siler (rehberde önceden olan alıcıya dokunmaz). */
        void deleteNewBuyer();
    }

    /** Onayla yazılan bir adım (geri al kuyruğu). */
    static final class Written {
        final String step;
        final long id;

        Written(String step, long id) {
            this.step = step;
            this.id = id;
        }
    }

    private final ActionPlan plan;
    private final boolean reminder;
    private final List<Written> journal = new ArrayList<>();
    private State state = State.FILLING;

    /** reminder: ödeme günü belli (plan hatırlatma adımı içeriyor ve tarih geçerli). */
    RunnerFlow(ActionPlan plan, boolean reminder) {
        this.plan = plan;
        this.reminder = reminder && plan.steps.contains(ActionPlan.REMINDER);
    }

    State state() {
        return state;
    }

    /** Onaydan sonra yazılanlar (geri al kuyruğu); onaydan önce hep boş. */
    List<Written> journal() {
        return new ArrayList<>(journal);
    }

    /** Form dolduruldu, "Kaydedeyim mi?" soruluyor. */
    void filled() {
        if (state == State.FILLING) {
            state = State.CONFIRMING;
        }
    }

    /** Hayır / geri / ekran kapandı: hiçbir şey yazılmaz. */
    void cancel() {
        if (state == State.FILLING || state == State.CONFIRMING) {
            state = State.CANCELLED;
        }
    }

    /** "Evet, kaydet": adımları sırayla yazar; biri olmazsa öncekileri geri alır. Yalnız onay sorulurken. */
    boolean confirm(Writer w) {
        if (state != State.CONFIRMING) {
            return false;
        }
        long sale = w.writeSale();
        if (sale == 0) {
            // Form eksik (fiyat, kilo...): hiçbir şey yazılmadı; çiftçi düzeltip yeniden onaylar.
            return false;
        }
        if (sale < 0) {
            w.deleteNewBuyer();
            state = State.FAILED;
            return false;
        }
        journal.add(new Written(ActionPlan.SALE, sale));
        // Alacak satışla aynı işlemde açılır (SaleRepository); satış silinince o da silinir. Tek adımlı satışta
        // (eğitim turu 1) alacak ayrı adım değildir: formdaki ödeme durumuna göre satışla birlikte açılır ya da açılmaz.
        if (plan.steps.contains(ActionPlan.RECEIVABLE)) {
            journal.add(new Written(ActionPlan.RECEIVABLE, sale));
        }
        if (reminder) {
            long id = w.writeReminder(sale);
            if (id <= 0) {
                rollback(w);
                state = State.FAILED;
                return false;
            }
            journal.add(new Written(ActionPlan.REMINDER, id));
        }
        state = State.SAVED;
        return true;
    }

    /** Tek "Geri al": onayla yazılan her şey, ters sırayla. */
    boolean undo(Writer w) {
        if (state != State.SAVED) {
            return false;
        }
        boolean ok = rollback(w);
        state = State.UNDONE;
        return ok;
    }

    private boolean rollback(Writer w) {
        boolean ok = true;
        for (int i = journal.size() - 1; i >= 0; i--) {
            Written x = journal.get(i);
            if (x.step.equals(ActionPlan.REMINDER)) {
                ok &= w.deleteReminder(x.id);
            } else if (x.step.equals(ActionPlan.SALE)) {
                ok &= w.deleteSale(x.id);
            }
        }
        w.deleteNewBuyer();
        journal.clear();
        return ok;
    }

    ActionPlan plan() {
        return plan;
    }
}
