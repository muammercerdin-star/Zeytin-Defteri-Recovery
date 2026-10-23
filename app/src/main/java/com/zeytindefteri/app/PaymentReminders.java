package com.zeytindefteri.app;

import android.content.Context;
import android.database.Cursor;

import java.util.Calendar;

/**
 * Söz verilen ödeme günü için yerel hatırlatma (Hatırlatmalarım'daki aynı alarm altyapısı, ücretsiz).
 * Hatırlatma notunun sonundaki "[sale:ID]" etiketiyle satışa bağlanır; para tamamen alınınca kapatılır.
 */
public final class PaymentReminders {
    static final String TYPE = "PAYMENT";

    private PaymentReminders() {}

    static String tag(long saleId) { return "[sale:" + saleId + "]"; }

    /** Söz verilen gün 09:00; o saat geçtiyse hatırlatma kurulmaz (gecikme uyarısı devreye girer). */
    public static void schedule(Context context, String owner, SalePayments.Info info, String buyer) {
        String due = info == null ? "" : SalePayStatus.iso(info.due);
        if (!SalePayStatus.valid(due)) return;
        Calendar c = Calendar.getInstance();
        c.set(Integer.parseInt(due.substring(0, 4)), Integer.parseInt(due.substring(5, 7)) - 1,
                Integer.parseInt(due.substring(8, 10)), 9, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        final long at = c.getTimeInMillis();
        if (at <= System.currentTimeMillis()) return;
        String who = buyer == null || buyer.trim().isEmpty() ? (info.buyer.isEmpty() ? "Alıcı" : info.buyer) : buyer.trim();
        final String title = "Ödeme günü · " + who;
        final String note = who + "'dan " + ElekSaleModel.tl(info.remaining()) + " alınacak (elek satışı). " + tag(info.saleId);
        final Integer garden = info.gardenId > 0 ? (int) info.gardenId : null;
        ReminderActions.run(context, owner == null ? "" : owner, false,
                repo -> repo.addReminder(garden, title, TYPE, at, "NONE", note),
                (saved, scheduled) -> { });
    }

    /** Para tamamen alındı: bu satışın açık ödeme hatırlatması kapatılır (alarmı iptal edilir). */
    public static void close(Context context, DatabaseHelper helper, String owner, long saleId) {
        final long id = openReminder(helper, owner, saleId);
        if (id <= 0) return;
        ReminderActions.run(context, owner == null ? "" : owner, true,
                repo -> repo.markReminderDone(id) > 0 ? id : -1,
                (saved, scheduled) -> { });
    }

    private static long openReminder(DatabaseHelper helper, String owner, long saleId) {
        try {
            Cursor c = helper.getReadableDatabase().rawQuery("SELECT id FROM reminders WHERE COALESCE(owner_uid,'')=? "
                            + "AND COALESCE(is_done,0)=0 AND reminder_type=? AND note LIKE ? ORDER BY id DESC LIMIT 1",
                    new String[]{owner == null ? "" : owner, TYPE, "%" + tag(saleId)});
            try {
                return c.moveToFirst() ? c.getLong(0) : -1;
            } finally {
                c.close();
            }
        } catch (RuntimeException e) {
            return -1;
        }
    }
}
