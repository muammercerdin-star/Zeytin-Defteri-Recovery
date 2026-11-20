package com.zeytindefteri.app;

import android.content.Context;

/**
 * Asistanın kurduğu iş hatırlatmaları (eğitim turu 1): "yarın Girelli'yi sulayacağız", "Yağ çıkınca hatırlat".
 * Hatırlatmalar ekranındaki genel hatırlatmayla aynı kayıt (GENERAL); ödeme hatırlatmasının (PaymentReminders)
 * "hep sormadan kur" izni buna uzatılmaz: her biri çiftçiye sorulduktan sonra kurulur ve Geri al ile silinir.
 * Geçmiş zamana ya da gece (22:00–05:00) hatırlatma kurulmaz.
 */
final class TaskReminders {
    static final String TYPE = "GENERAL";

    private TaskReminders() {
    }

    /** Hatırlatmayı kurar ve alarmını planlar: kayıt no; kurulamadıysa -1. gardenId ≤ 0: bahçesiz. */
    static long add(Context context, String owner, long gardenId, String title, String note, long at) {
        if (!TaskReminderEntry.allowed(at, System.currentTimeMillis())) {
            return -1;
        }
        Integer garden = gardenId > 0 ? (int) gardenId : null;
        Context app = context.getApplicationContext();
        synchronized (LocalReminderScheduler.LOCK) {
            try (DatabaseHelper db = new DatabaseHelper(app)) {
                if (!(owner == null ? "" : owner).equals(db.getActiveOwnerUid())) {
                    return -1;
                }
                ReminderRepository repo = new ReminderRepository(db);
                long id = repo.addReminder(garden, title, TYPE, at, "NONE", note);
                if (id > 0) {
                    LocalReminderScheduler s = new LocalReminderScheduler(app);
                    s.resetDelivery(id);
                    s.schedule(repo, id);
                }
                return id;
            } catch (RuntimeException e) {
                return -1;
            }
        }
    }

    /** "Geri al": hatırlatmayı siler, alarmını iptal eder. */
    static boolean remove(Context context, long reminderId) {
        return PaymentReminders.remove(context, reminderId);
    }
}
