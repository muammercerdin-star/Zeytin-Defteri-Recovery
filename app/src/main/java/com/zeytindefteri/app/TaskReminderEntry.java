package com.zeytindefteri.app;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gelecek zamanlı iş cümlesi → hatırlatma (eğitim turu 1; saf, Android'siz, testli).
 * "yarın Girelli'yi sulayacağız" → HATIRLATMA tur=Sulama;gun=1. Kayıt DEĞİL: çiftçiye "hatırlatayım mı?" diye sorulur,
 * her zaman sorulur (ödeme hatırlatmasının "hep sormadan kur" izni buna uzatılmaz) ve Geri al ile silinir.
 *
 * Soru sözü varsa ("ne zaman ilaçlayacağız?", "ilaçlayacak mıyız?") soru kalır. "Hasat gireceğim", "elek gireceğim"
 * gibi kayıt açma sözleri hatırlatma değildir. Saat söylenmediyse sabah 09:00, "akşam" denirse 18:00. Geçmiş zamana ya
 * da gece (22:00–05:00) hatırlatma kurulmaz; gün söylenmediyse sorulur.
 */
final class TaskReminderEntry {
    static final int MORNING = 9;
    static final int EVENING = 18;
    static final String HARVEST = "Hasat";

    private static final String QUESTION = ".*\\b(kac|ne|ne zaman|nasil|hangi|nerede|mi|mu|miyiz|muyuz|miyim|muyum|misin|musun|midir|mudur)\\b.*";
    private static final String FUTURE = "\\p{L}+(acag|eceg)(im|iz|imiz)";
    private static final String NOT_WORK = "(gir|ol|bak|soyle|sor|yaz|gel|git|gid|de|di|ye|ac|kapat)\\p{L}*";

    /** İşin adı: OperationKinds türü ("Sulama") ya da "Hasat". */
    final String type;
    /** "Diğer" işinin adı ("Ot biçme"); yoksa "". */
    final String note;
    /** Gün sözü (ActionPlan.due: "yarin", "3 gun sonra", "cuma"...) ya da "bugun"; yoksa "" (sorulur). */
    final String due;
    final int hour;

    private TaskReminderEntry(String type, String note, String due, int hour) {
        this.type = type;
        this.note = note;
        this.due = due;
        this.hour = hour;
    }

    /** Gelecek zamanlı iş cümlesi mi; değilse null. */
    static TaskReminderEntry parse(String raw) {
        if (raw == null || raw.contains("?")) {
            return null;
        }
        String s = TurkishText.normalize(raw);
        if (s.matches(QUESTION) || !future(s)) {
            return null;
        }
        String type = "";
        String note = "";
        int op = OperationVoiceEntry.typeOf(s);
        if (op >= 0) {
            type = OperationKinds.TYPES[op];
            note = op == OperationVoiceEntry.OTHER ? OperationVoiceEntry.noteOf(s) : "";
        } else if (s.matches(".*\\b(topla\\p{L}*|silk\\p{L}*|sirikla\\p{L}*|devsir\\p{L}*|hasat\\p{L}*)\\b.*")) {
            type = HARVEST;
        }
        if (type.isEmpty()) {
            return null;
        }
        String due = ActionPlan.due(s);
        if (due.isEmpty() && s.matches(".*\\bbugun\\b.*")) {
            due = "bugun";
        }
        int hour = s.matches(".*\\b(aksam|aksama|aksamleyin|aksamustu)\\b.*") ? EVENING : MORNING;
        return new TaskReminderEntry(type, note, due, hour);
    }

    /** Birinci şahıs gelecek zamanlı bir iş fiili var mı ("sulayacağız", "budayacağım"); kayıt açma sözleri değil. */
    static boolean future(String normalized) {
        for (String w : normalized.split(" ")) {
            if (w.matches(FUTURE) && !w.matches(NOT_WORK)) {
                return true;
            }
        }
        return false;
    }

    /** Göreli gün sayısı (yarın 1, öbür gün 2, "3 gün sonra" 3, haftaya 7, bugün 0); göreli değilse -1. */
    int dayOffset() {
        if (due.equals("bugun")) {
            return 0;
        }
        if (due.equals("yarin")) {
            return 1;
        }
        if (due.equals("obur gun")) {
            return 2;
        }
        if (due.equals("haftaya")) {
            return 7;
        }
        if (due.endsWith(" gun sonra")) {
            return Integer.parseInt(due.substring(0, due.indexOf(' ')));
        }
        return -1;
    }

    /** Karar alanları: tur, not, gun (göreli) ya da zaman (gün adı, ayın günü), saat (yalnız akşam). */
    Map<String, String> fields() {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("tur", type);
        if (!note.isEmpty()) {
            f.put("not", note);
        }
        int days = dayOffset();
        if (days >= 0) {
            f.put("gun", String.valueOf(days));
        } else if (!due.isEmpty()) {
            f.put("zaman", due);
        }
        if (hour != MORNING) {
            f.put("saat", String.valueOf(hour));
        }
        return f;
    }

    /** Hatırlatma zamanı (ms); gün söylenmediyse -1 (sorulur). */
    long when(Calendar today) {
        int days = dayOffset();
        if (days >= 0) {
            return at(today, days, hour);
        }
        String iso = ActionPlan.dueDate(due, today);
        if (iso.isEmpty()) {
            return -1;
        }
        Calendar c = (Calendar) today.clone();
        c.set(Integer.parseInt(iso.substring(0, 4)), Integer.parseInt(iso.substring(5, 7)) - 1, Integer.parseInt(iso.substring(8, 10)));
        return at(c, 0, hour);
    }

    /** Hatırlatmanın başlığı: "Sulama · Girelli". */
    String title(String garden) {
        String name = note.isEmpty() ? type : note;
        return garden == null || garden.isEmpty() ? name : name + " · " + garden;
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

    /** Okunan saat: "Yarın 09:00", "Bugün 18:00", "26 Kasım 09:00". */
    static String spoken(long at, Calendar today) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(at);
        String time = String.format(java.util.Locale.US, "%02d:00", c.get(Calendar.HOUR_OF_DAY));
        Calendar t = (Calendar) today.clone();
        if (sameDay(c, t)) {
            return "Bugün " + time;
        }
        t.add(Calendar.DAY_OF_YEAR, 1);
        if (sameDay(c, t)) {
            return "Yarın " + time;
        }
        String iso = String.format(java.util.Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH));
        String day = ActionPlan.dayText(iso);
        return day.substring(0, day.lastIndexOf('\'')) + " " + time;
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
