package com.zeytindefteri.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Karşılama ekranının verisi (yönlendiren asistan; yalnız okur, hiçbir şey yazmaz): son 60 günün iş sayıları (düğme
 * sırası), bugün hasat kaydı var mı, ödeme günü bugün olan açık alacak, bahçe ve alıcı adları, alışkanlıklar (İP5).
 * Okunamazsa boş: düğmeler varsayılan sırada, kart yok.
 */
final class GuideData {
    static final class Snapshot {
        final Map<String, Integer> counts;
        final boolean harvestToday;
        final List<GuideCards.Card> cards;
        final List<String> gardens;
        final List<String> buyers;
        final HabitModel habits;

        Snapshot(Map<String, Integer> counts, boolean harvestToday, List<GuideCards.Card> cards, List<String> gardens,
                 List<String> buyers, HabitModel habits) {
            this.counts = counts;
            this.harvestToday = harvestToday;
            this.cards = cards;
            this.gardens = gardens;
            this.buyers = buyers;
            this.habits = habits;
        }
    }

    private static final Locale TR = new Locale("tr", "TR");

    private GuideData() {
    }

    static Snapshot load(Context context, String owner) {
        Map<String, Integer> counts = new HashMap<>();
        List<GuideCards.Card> cards = new ArrayList<>();
        List<String> gardens = new ArrayList<>();
        List<String> buyers = new ArrayList<>();
        boolean harvestToday = false;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().getTime());
        String todayTr = new SimpleDateFormat("dd.MM.yyyy", TR).format(Calendar.getInstance().getTime());
        Calendar since = Calendar.getInstance();
        since.add(Calendar.DAY_OF_YEAR, -GuideSettings.HISTORY_DAYS);
        DatabaseHelper helper = new DatabaseHelper(context.getApplicationContext());
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            String[] args = {owner == null ? "" : owner};
            try (Cursor c = db.rawQuery("SELECT o.type, o.operation_date FROM operations o INNER JOIN gardens g ON g.id = o.garden_id "
                    + "WHERE g.owner_uid = ?", args)) {
                while (c.moveToNext()) {
                    if (recent(c.getString(1), since)) {
                        String job = GuidedFlow.jobOfType(ActionCatalog.ISLEM, c.getString(0));
                        if (!job.isEmpty()) {
                            counts.merge(job, 1, Integer::sum);
                        }
                    }
                }
            }
            try (Cursor c = db.rawQuery("SELECT h.harvest_date FROM harvests h INNER JOIN gardens g ON g.id = h.garden_id "
                    + "WHERE g.owner_uid = ?", args)) {
                while (c.moveToNext()) {
                    String d = c.getString(0);
                    if (recent(d, since)) {
                        counts.merge("HASAT", 1, Integer::sum);
                    }
                    harvestToday |= today.equals(d) || todayTr.equals(d);
                }
            }
            try (Cursor c = db.rawQuery("SELECT s.sale_date FROM sales s INNER JOIN gardens g ON g.id = s.garden_id "
                    + "WHERE g.owner_uid = ?", args)) {
                while (c.moveToNext()) {
                    if (recent(c.getString(0), since)) {
                        counts.merge("SATIS", 1, Integer::sum);
                    }
                }
            }
            try (Cursor c = db.rawQuery("SELECT b.name, s.sale_date FROM sales s INNER JOIN gardens g ON g.id = s.garden_id "
                    + "INNER JOIN buyers b ON b.id = s.buyer_id WHERE g.owner_uid = ? AND (s.due_date = ? OR s.due_date = ?) "
                    + "AND (COALESCE(s.gross_total,0) - COALESCE(s.transport_cost,0) - COALESCE(s.deductions,0) "
                    + "- COALESCE(s.amount_received,0)) > 0.009 ORDER BY s.sale_date LIMIT 1",
                    new String[]{args[0], today, todayTr})) {
                if (c.moveToFirst()) {
                    cards.add(GuideCards.payment(c.getString(0), dayText(c.getString(1))));
                }
            }
            try (Cursor c = db.rawQuery("SELECT name FROM gardens WHERE owner_uid = ? ORDER BY id", args)) {
                while (c.moveToNext()) {
                    gardens.add(c.getString(0));
                }
            }
            try (Cursor c = db.rawQuery("SELECT b.name, COUNT(*) n FROM sales s INNER JOIN gardens g ON g.id = s.garden_id "
                    + "INNER JOIN buyers b ON b.id = s.buyer_id WHERE g.owner_uid = ? GROUP BY b.id ORDER BY n DESC LIMIT 6", args)) {
                while (c.moveToNext()) {
                    buyers.add(c.getString(0));
                }
            }
        } catch (RuntimeException e) {
            counts.clear();
            cards.clear();
        } finally {
            helper.close();
        }
        cards.add(GuideCards.harvest());
        GuideCards.Card habit = habitCard(counts);
        if (habit != null) {
            cards.add(habit);
        }
        HabitModel habits = HabitRecords.load(context);
        return new Snapshot(counts, harvestToday, cards, gardens, buyers, habits);
    }

    /**
     * Alışkanlık kartı: son 60 günde en sık kaydedilen bahçe işi (en az 4 kez). Kayıtlardan gelen gözlem; saat, yer,
     * kişi yok.
     */
    static GuideCards.Card habitCard(Map<String, Integer> counts) {
        String best = "";
        int most = 3;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            GuidedFlow.Job job = GuidedFlow.job(e.getKey());
            if (job != null && !job.starter.isEmpty() && ActionCatalog.ISLEM.equals(job.action) && e.getValue() > most) {
                best = e.getKey();
                most = e.getValue();
            }
        }
        if (best.isEmpty()) {
            return null;
        }
        GuidedFlow.Job job = GuidedFlow.job(best);
        String work = job.label.toLowerCase(TR);
        return GuideCards.habit("Bugün " + work + " yaptın mı?",
                "son " + GuideSettings.HISTORY_DAYS + " günde " + most + " kez " + work + " kaydettin", best);
    }

    /** "2026-11-13" ya da "13.11.2026" son 60 günde mi. */
    private static boolean recent(String date, Calendar since) {
        Calendar c = parse(date);
        return c != null && !c.before(since);
    }

    private static Calendar parse(String date) {
        if (date == null) {
            return null;
        }
        try {
            Calendar c = Calendar.getInstance();
            if (date.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                c.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date.substring(0, 10)));
                return c;
            }
            if (date.matches("\\d{2}\\.\\d{2}\\.\\d{4}.*")) {
                c.setTime(new SimpleDateFormat("dd.MM.yyyy", Locale.US).parse(date.substring(0, 10)));
                return c;
            }
        } catch (java.text.ParseException e) {
            return null;
        }
        return null;
    }

    /** "13 Kasım" (satışın günü). */
    private static String dayText(String date) {
        Calendar c = parse(date);
        if (c == null) {
            return "önceki";
        }
        return new SimpleDateFormat("d MMMM", TR).format(c.getTime());
    }
}
