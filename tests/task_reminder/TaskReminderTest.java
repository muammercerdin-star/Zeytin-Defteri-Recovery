package com.zeytindefteri.app;

import java.util.Calendar;

/** Gelecek zamanlı iş cümlesi hatırlatmadır (kayıt değil); soru soru kalır; gelecek zaman öğretme sorusu açmaz. */
public class TaskReminderTest {
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
                {"yarın Girelli'yi sulayacağız", "tur=Sulama;gun=1"},
                {"yarın ilaçlayacağız", "tur=İlaçlama;gun=1"},
                {"yarın akşam budayacağız", "tur=Budama;gun=1;saat=18"},
                {"cuma günü gübre atacağız", "tur=Gübre;zaman=cuma"},
                {"öbür gün ot biçeceğiz", "tur=Diğer;not=Ot biçme;gun=2"},
                {"yarın Kuyu dibini silkeleyeceğiz", "tur=Hasat;gun=1"},
                {"3 gün sonra işçi tutacağız", "tur=İşçilik;gun=3"},
                {"ilaçlayacağım", "tur=İlaçlama"},
        };
        for (String[] y : yes) {
            Decision d = decide(y[0]);
            check(d.is(ActionCatalog.HATIRLATMA), "hatırlatma: " + y[0] + " → " + d);
            eq(d.fieldsText(), y[1], "alanlar: " + y[0]);
        }
        String[][] stay = {
                {"ne zaman ilaçlayacağız?", "SORU_SPRAY"},
                {"yarın ilaçlayacak mıyız?", "SORU_SPRAY"},
                {"ne zaman ilaçlayacağız", "SORU_SPRAY"},
                {"Hasat gireceğim", "HASAT"},
                {"Elek gireceğim", "ELEK_AC"},
                {"yarın yağmur yağacak mı", "SORU_WEATHER"},
                {"bugün Girelli'yi suladık", "ISLEM"},
        };
        for (String[] x : stay) {
            eq(decide(x[0]).action, x[1], "olduğu gibi kalır: " + x[0]);
        }
        LearnedTerms.Memory taughtStore = new LearnedTerms.Memory();
        taughtStore.put(LearnedTerms.teach("bakırladık", LearnedTerms.choiceFor("ilaçlama"), LearnedTerms.SOURCE_VOICE, 1));
        Decision later = new LearnedBrain(taughtStore, new RuleBrain()).decide(new Brain.Input("yarın bakırlayacağız"),
                BrainContext.full(true, AssistantPersona.decode("")::handle));
        eq(later.action + " " + later.fieldsText(), "HATIRLATMA tur=İlaçlama;gun=1", "öğretilen sözün gelecek zamanı da hatırlatma");
        Decision past = new LearnedBrain(taughtStore, new RuleBrain()).decide(new Brain.Input("bugün bakırladık"),
                BrainContext.full(true, AssistantPersona.decode("")::handle));
        eq(past.action, "ISLEM", "öğretilen sözün geçmiş zamanı kayıt kalır");
        eq(decide("Bakacağım").action.equals("HATIRLATMA"), false, "bakacağım hatırlatma değil");
        LearnedTerms.Memory empty = new LearnedTerms.Memory();
        check(LearnedTerms.teachable("yarın bakırlayacağız", empty) == null, "gelecek zaman öğretme sorusu açmaz");
        check(LearnedTerms.teachable("yarın Girelli'yi sulayacağız", empty) == null, "bilinen sözün gelecek zamanı sorulmaz");
        check(LearnedTerms.teachable("bugün bakırladık", empty) != null, "geçmiş zaman yine sorulur");
        check(!decide("yarın bakırlayacağız").is(ActionCatalog.HATIRLATMA), "bilinmeyen fiil hatırlatma uydurmaz");
        Calendar today = Calendar.getInstance();
        today.set(2026, Calendar.NOVEMBER, 20, 14, 0, 0);
        today.set(Calendar.MILLISECOND, 0);
        long now = today.getTimeInMillis();
        TaskReminderEntry tomorrow = TaskReminderEntry.parse("yarın Girelli'yi sulayacağız");
        long at = tomorrow.when(today);
        eq(TaskReminderEntry.spoken(at, today), "Yarın 09:00", "yarın sabah 09:00");
        check(TaskReminderEntry.allowed(at, now), "yarın kurulur");
        eq(tomorrow.title("Girelli"), "Sulama · Girelli", "başlık bahçeyle");
        long evening = TaskReminderEntry.parse("yarın akşam budayacağız").when(today);
        eq(TaskReminderEntry.spoken(evening, today), "Yarın 18:00", "akşam 18:00");
        long todayMorning = TaskReminderEntry.parse("bugün toplayacağız").when(today);
        check(!TaskReminderEntry.allowed(todayMorning, now), "geçmiş saat (bugün 09:00, saat 14:00) kurulmaz");
        long todayEvening = TaskReminderEntry.parse("bugün akşam toplayacağız").when(today);
        check(TaskReminderEntry.allowed(todayEvening, now), "bugün akşam 18:00 kurulur");
        eq(TaskReminderEntry.parse("ilaçlayacağım").when(today), -1L, "gün söylenmedi: sorulur");
        long friday = TaskReminderEntry.parse("cuma günü gübre atacağız").when(today);
        eq(TaskReminderEntry.spoken(friday, today), "27 Kasım 09:00", "cuma: önümüzdeki cuma");
        check(!TaskReminderEntry.allowed(TaskReminderEntry.at(today, 1, 23), now), "gece kurulmaz");
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " TaskReminder");
        if (bad > 0) {
            System.exit(1);
        }
    }
}
