package com.zeytindefteri.app;

import java.util.Calendar;

/**
 * İş hatırlatmasının zamanı (eğitim turu 1; saf, Android'siz, testli). Saat söylenmediyse sabah 09:00, "akşam"
 * denirse 18:00. Geçmiş zamana ya da gece (22:00–05:00) hatırlatma kurulmaz.
 */
final class TaskReminderEntry {
    static final int MORNING = 9;
    static final int EVENING = 18;

    private TaskReminderEntry() {
    }

    /** Bugünden dayOffset gün sonra, saat hour:00 (milisaniye). */
    static long at(Calendar today, int dayOffset, int hour) {
        Calendar c = (Calendar) today.clone();
        c.add(Calendar.DAY_OF_YEAR, dayOffset);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** Kurulabilir mi: gelecekte ve gece saatlerinde (22:00–05:00) değil. */
    static boolean allowed(long at, long now) {
        if (at <= now) {
            return false;
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(at);
        int hour = c.get(Calendar.HOUR_OF_DAY);
        return hour >= 5 && hour < 22;
    }
}
