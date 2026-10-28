package com.zeytindefteri.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Elek hatırlatması: hasat döneminde (Ekim–Ocak) her gün seçilen saatte (varsayılan 17:00), o gün hiç elek
 * yazılmadıysa "Bugünün eleğini yazdın mı?" bildirimi. Sunucusuz: AlarmManager ile telefonda çalışır.
 * Bildirimde "Eleği yaz" (Elek ekranı) ve "Bugün toplamadım" (kapatır). Ayarlar → Bildirim ayarları'ndan
 * açılıp kapatılır, saati değiştirilir. Bildirim kutusuna da yazılır (aynı gün ikinci kez gelmez).
 */
public final class ElekReminder extends BroadcastReceiver {
    static final String ACTION_FIRE = "com.zeytindefteri.app.ELEK_REMINDER";
    static final String ACTION_SKIP = "com.zeytindefteri.app.ELEK_REMINDER_SKIP";
    private static final String TAG = "elek-reminder";

    public static int hour(Context c) {
        return prefs(c).getInt("elek_hour", AlertRules.ELEK_HOUR);
    }

    public static int minute(Context c) {
        return prefs(c).getInt("elek_minute", AlertRules.ELEK_MINUTE);
    }

    /** Saat değişince ya da aç/kapat sonrası yeniden kurulur. */
    public static void setTime(Context c, int hour, int minute) {
        prefs(c).edit().putInt("elek_hour", hour).putInt("elek_minute", minute).apply();
        schedule(c);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(AlertCenter.PREFS, Context.MODE_PRIVATE);
    }

    private static PendingIntent fireIntent(Context app, int flags) {
        Intent i = new Intent(app, ElekReminder.class).setAction(ACTION_FIRE);
        return PendingIntent.getBroadcast(app, 9417, i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Bir sonraki hatırlatmayı kurar (kapalıysa iptal eder). Uygulama açılışında, açılışta ve ayardan çağrılır. */
    public static void schedule(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        try {
            if (!AlertCenter.enabled(app, AlertRules.ELEK)) {
                PendingIntent p = fireIntent(app, PendingIntent.FLAG_NO_CREATE);
                if (p != null) { am.cancel(p); p.cancel(); }
                return;
            }
            long at = AlertRules.nextAt(System.currentTimeMillis(), hour(app), minute(app), TimeZone.getDefault());
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fireIntent(app, PendingIntent.FLAG_UPDATE_CURRENT));
        } catch (RuntimeException ignored) {
            // Kurulamazsa bir sonraki uygulama açılışında yeniden denenir.
        }
    }

    @Override public void onReceive(Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_SKIP.equals(action)) {
            NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(TAG, 0);
            return;
        }
        if (!ACTION_FIRE.equals(action)) return;
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                check(app);
            } catch (RuntimeException e) {
                android.util.Log.w("ElekReminder", "Elek hatırlatması işlenemedi", e);
            } finally {
                schedule(app); // ertesi gün
                pending.finish();
            }
        }, "zd-elek-reminder").start();
    }

    /** Bugün elek yazılmadıysa bildirir. */
    static void check(Context app) {
        if (!AlertCenter.enabled(app, AlertRules.ELEK) || !new SessionManager(app).hasSession()) return;
        Calendar cal = Calendar.getInstance();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        DatabaseHelper helper = new DatabaseHelper(app);
        try {
            String owner = helper.getActiveOwnerUid();
            if (owner == null) owner = "";
            SQLiteDatabase db = helper.getWritableDatabase();
            int gardens = count(db, "SELECT COUNT(*) FROM gardens WHERE COALESCE(owner_uid,'')=?", owner);
            int elekToday = count(db, "SELECT COUNT(*) FROM elek_records er INNER JOIN gardens g ON g.id=er.garden_id "
                    + "WHERE COALESCE(g.owner_uid,'')=? AND (substr(TRIM(er.record_date),1,10)='" + today + "' "
                    + "OR TRIM(er.record_date)='" + new SimpleDateFormat("dd.MM.yyyy", Locale.US).format(new Date()) + "')", owner);
            AlertRules.Alert a = AlertRules.elekReminder(cal.get(Calendar.MONTH) + 1, gardens, elekToday, today);
            if (a == null) return;
            if (NotificationInbox.add(db, owner, a, System.currentTimeMillis()) <= 0) return; // bugün zaten gitti
            post(app, a);
        } finally {
            helper.close();
        }
    }

    private static int count(SQLiteDatabase db, String sql, String owner) {
        Cursor c = null;
        try {
            c = db.rawQuery(sql, new String[]{owner});
            return c.moveToFirst() ? c.getInt(0) : 0;
        } catch (RuntimeException e) {
            return 0;
        } finally {
            if (c != null) c.close();
        }
    }

    private static void post(Context c, AlertRules.Alert a) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || !nm.areNotificationsEnabled()) return;
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) return;
        AlertCenter.createChannel(c);
        Intent open = new Intent(c, ElekActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 9418, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent skip = new Intent(c, ElekReminder.class).setAction(ACTION_SKIP);
        PendingIntent si = PendingIntent.getBroadcast(c, 9419, skip, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, AlertCenter.CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_bell_line)
                .setContentTitle(a.title)
                .setContentText(a.body)
                .setStyle(new Notification.BigTextStyle().bigText(a.body))
                .setContentIntent(pi)
                .addAction(new Notification.Action.Builder(null, "Eleği yaz", pi).build())
                .addAction(new Notification.Action.Builder(null, "Bugün toplamadım", si).build())
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true);
        try { nm.notify(TAG, 0, b.build()); } catch (RuntimeException ignored) { }
    }
}
