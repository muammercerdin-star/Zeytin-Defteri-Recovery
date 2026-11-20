package com.zeytindefteri.app;

import java.util.Calendar;

/** Yağhane (eğitim turu 1): kasa kiloya çevrilmez; yağhane sözü + zeytin bağlamı gerekir; hatırlatma gece/geçmişe kurulmaz. */
public class OilVoiceEntryTest {
    static int n = 0;
    static int bad = 0;

    static void check(boolean ok, String m) {
        n++;
        if (!ok) {
            bad++;
            System.out.println("FAIL " + m);
        }
    }

    static void eq(Object got, Object want, String m) {
        check(want.equals(got), m + ": «" + got + "» beklenen «" + want + "»");
    }

    static Decision decide(String raw) {
        return new LearnedBrain(new LearnedTerms.Memory(), new RuleBrain()).decide(new Brain.Input(raw),
                BrainContext.full(true, AssistantPersona.decode("")::handle));
    }

    public static void main(String[] a) {
        String[][] yes = {
                {"fabrikaya 40 kasa götürdük", "kasa=40"},
                {"yağhaneden 120 kilo yağ aldık", "yag=120"},
                {"yağhaneden 80 litre yağ aldık", "yag=80;birim=litre"},
                {"Akhisar yağhanesine 900 kilo zeytin verdik", "zeytin=900;yaghane=Akhisar"},
                {"fabrikaya 40 kasa götürdük, 120 kilo yağ çıktı", "kasa=40;yag=120"},
                {"değirmene on iki kasa bıraktık", "kasa=12"},
        };
        for (String[] y : yes) {
            Decision d = decide(y[0]);
            check(d.is(ActionCatalog.YAGHANE), "yağhane: " + y[0] + " → " + d);
            eq(d.fieldsText(), y[1], "alanlar: " + y[0]);
        }
        String[] no = {"yağ aldık", "fabrikada çalışıyorum", "yağhaneye ne zaman gideceğiz", "yağhaneden kaç kilo yağ aldık",
                "fabrikadan gübre aldık", "2 numara yağlık 300 kilo fabrikaya verdik", "fabrikaya gittik", "40 kasa götürdük"};
        for (String s : no) {
            check(!decide(s).is(ActionCatalog.YAGHANE), "yağhane değil: " + s + " → " + decide(s));
        }
        OilVoiceEntry crates = OilVoiceEntry.parse("fabrikaya 40 kasa götürdük");
        eq(crates.note(), "40 kasa", "kasa notta kalır");
        eq(crates.oliveKg, 0.0, "kasa kiloya çevrilmez");
        check(crates.waitingOil(), "yağ henüz çıkmadı: hatırlatma önerilir");
        eq(crates.summary(), "40 kasa zeytin", "özet");
        OilVoiceEntry oil = OilVoiceEntry.parse("yağhaneden 120 kilo yağ aldık");
        check(!oil.waitingOil(), "yağ söylendi: hatırlatma önerilmez");
        eq(oil.summary(), "120 kilo yağ", "özet: yağ");
        check(ActionCatalog.get(ActionCatalog.YAGHANE).kind.alwaysConfirm, "yağhane kaydı her zaman onaylı");
        Calendar today = Calendar.getInstance();
        today.set(2026, Calendar.NOVEMBER, 20, 14, 0, 0);
        long now = today.getTimeInMillis();
        long tomorrow = TaskReminderEntry.at(today, 1, TaskReminderEntry.MORNING);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(tomorrow);
        eq(c.get(Calendar.DAY_OF_MONTH) + " " + c.get(Calendar.HOUR_OF_DAY) + ":" + c.get(Calendar.MINUTE), "21 9:0", "yarın 09:00");
        check(TaskReminderEntry.allowed(tomorrow, now), "gelecekteki sabah kurulur");
        check(!TaskReminderEntry.allowed(TaskReminderEntry.at(today, 0, TaskReminderEntry.MORNING), now), "geçmiş saat kurulmaz");
        check(!TaskReminderEntry.allowed(TaskReminderEntry.at(today, -1, TaskReminderEntry.EVENING), now), "dün kurulmaz");
        check(!TaskReminderEntry.allowed(TaskReminderEntry.at(today, 1, 23), now), "gece 23:00 kurulmaz");
        check(!TaskReminderEntry.allowed(TaskReminderEntry.at(today, 1, 4), now), "gece 04:00 kurulmaz");
        check(TaskReminderEntry.allowed(TaskReminderEntry.at(today, 0, TaskReminderEntry.EVENING), now), "bugün akşam 18:00 kurulur");
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " OilVoiceEntry");
        if (bad > 0) {
            System.exit(1);
        }
    }
}
