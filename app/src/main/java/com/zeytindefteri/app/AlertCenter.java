package com.zeytindefteri.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Telefonun kendi ürettiği bildirimler (sunucu yok, ücretsiz):
 * hava (bahçe konumuna göre Open-Meteo), alacak hatırlatması, tezgah gün sonu, zeytin takvimi.
 * - Arka planda JobScheduler ile ~4 saatte bir, ayrıca uygulama açılınca (en sık 30 dakikada bir) çalışır.
 * - Hava en sık 3 saatte bir çekilir; her uyarı bir kez (NotificationInbox anahtarı).
 * - Bir çalışmada en fazla 2 telefon bildirimi; gerisi yalnız bildirim kutusunda.
 * - Her kategori Ayarlar'dan kapatılabilir (prefs "zd_notify").
 */
public final class AlertCenter {
    public static final String CHANNEL = "zeytin_alerts";
    static final String PREFS = "zd_notify";
    static final int JOB_ID = 4711;
    private static final long WEATHER_EVERY_MS = 3L * 60 * 60 * 1000;
    private static final long RUN_EVERY_MS = 30L * 60 * 1000;
    private static final int MAX_POSTS = 2;
    private static final int MAX_GARDENS = 3;
    private static volatile long lastRun;

    public static final String[] KINDS = {AlertRules.WEATHER, AlertRules.MONEY, AlertRules.TEZGAH, AlertRules.CALENDAR};
    public static final String[] KIND_LABELS = {"Hava uyarıları", "Alacak hatırlatmaları", "Tezgah gün sonu", "Zeytin takvimi"};
    public static final String[] KIND_HINTS = {"Don, hasatta yağmur, kuvvetli rüzgâr, ilaçlama için uygun gün",
            "14 günü geçen alacak, haftada bir", "Akşam 20'den sonra gün sonu alınmadıysa", "Ayın zeytin işi, ayda bir"};

    private AlertCenter() {}

    public static boolean enabled(Context c, String kind) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on_" + kind, true);
    }

    public static void setEnabled(Context c, String kind, boolean on) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on_" + kind, on).apply();
    }

    // ------------------------------------------------------------------ zamanlama

    /** Uygulama açılışında: kanal + periyodik iş (yoksa kurulur) + gerekirse hemen bir tarama. */
    public static void onAppStart(Context context) {
        Context app = context.getApplicationContext();
        createChannel(app);
        schedule(app);
    }

    /** Ekran öne geldiğinde: en sık 30 dakikada bir arka planda tarama. */
    public static void onResume(Context context) {
        long now = System.currentTimeMillis();
        if (now - lastRun < RUN_EVERY_MS) return;
        lastRun = now;
        final Context app = context.getApplicationContext();
        new Thread(() -> runSync(app), "zd-alerts").start();
    }

    static void schedule(Context app) {
        try {
            JobScheduler js = (JobScheduler) app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;
            for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == JOB_ID) return;
            JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(app, AlertJobService.class))
                    .setPeriodic(4L * 60 * 60 * 1000, 60L * 60 * 1000)
                    .setPersisted(true)
                    .build();
            js.schedule(job);
        } catch (RuntimeException ignored) {
            // Zamanlanamazsa uygulama açılışındaki tarama yine çalışır.
        }
    }

    public static void createChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Zeytin Defteri Uyarıları", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Don, yağmur ve rüzgâr uyarıları; alacak ve gün sonu hatırlatmaları; zeytin takvimi");
        nm.createNotificationChannel(ch);
    }

    // ------------------------------------------------------------------ tarama

    /** Bütün kuralları çalıştırır (arka plan iş parçacığında çağrılır). En fazla ~40 sn sürer. */
    public static void runSync(Context context) {
        Context app = context.getApplicationContext();
        if (!new SessionManager(app).hasSession()) return;
        DatabaseHelper helper = new DatabaseHelper(app);
        try {
            String owner = helper.getActiveOwnerUid();
            if (owner == null) owner = "";
            SQLiteDatabase db = helper.getWritableDatabase();
            long now = System.currentTimeMillis();
            Calendar cal = Calendar.getInstance();
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(now));
            int hour = cal.get(Calendar.HOUR_OF_DAY), month = cal.get(Calendar.MONTH) + 1, year = cal.get(Calendar.YEAR);
            List<AlertRules.Alert> fresh = new ArrayList<>();

            if (enabled(app, AlertRules.CALENDAR)) collect(db, owner, AlertRules.calendar(year, month), now, fresh);
            if (enabled(app, AlertRules.MONEY)) {
                try {
                    Cursor c = db.rawQuery("SELECT id, COALESCE(party_name,''), amount-COALESCE(total_received,0), created_at "
                            + "FROM receivables WHERE COALESCE(owner_uid,'')=? AND COALESCE(status,'OPEN')<>'PAID' "
                            + "AND amount-COALESCE(total_received,0)>=1 ORDER BY created_at LIMIT 20", new String[]{owner});
                    try {
                        while (c.moveToNext()) {
                            int age = (int) Math.max(0, (now - c.getLong(3)) / (24L * 60 * 60 * 1000));
                            collect(db, owner, AlertRules.receivable(c.getLong(0), c.getString(1), c.getDouble(2), age, today), now, fresh);
                        }
                    } finally {
                        c.close();
                    }
                } catch (RuntimeException ignored) { }
            }
            if (enabled(app, AlertRules.TEZGAH)) {
                try {
                    TezgahPurchases.Day d = TezgahPurchases.openSummary(db, owner);
                    collect(db, owner, AlertRules.dayEnd(d.count, d.kg, hour, today), now, fresh);
                } catch (RuntimeException ignored) { }
            }
            SharedPreferences p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (enabled(app, AlertRules.WEATHER) && now - p.getLong("weather_at_" + owner, 0) >= WEATHER_EVERY_MS) {
                p.edit().putLong("weather_at_" + owner, now).apply();
                weather(helper, db, owner, today, hour, month, now, fresh);
            }
            NotificationInbox.prune(db, now);
            post(app, fresh);
        } catch (RuntimeException ignored) {
            // Bildirim taraması uygulamayı asla düşürmez.
        } finally {
            helper.close();
        }
    }

    private static void collect(SQLiteDatabase db, String owner, AlertRules.Alert a, long now, List<AlertRules.Alert> fresh) {
        if (a != null && NotificationInbox.add(db, owner, a, now) > 0) fresh.add(a);
    }

    private static void weather(DatabaseHelper helper, SQLiteDatabase db, String owner, String today, int hour, int month,
                                long now, List<AlertRules.Alert> fresh) {
        List<long[]> ids = new ArrayList<>();
        List<String[]> info = new ArrayList<>();
        Cursor c = null;
        try {
            c = helper.getGardens();
            while (c != null && c.moveToNext() && ids.size() < MAX_GARDENS) {
                String prov = str(c, "province"), dist = str(c, "district"), hood = str(c, "neighborhood");
                if (prov.isEmpty() && dist.isEmpty()) continue;
                ids.add(new long[]{c.getLong(c.getColumnIndexOrThrow("id"))});
                info.add(new String[]{str(c, "name"), prov, dist, hood});
            }
        } catch (RuntimeException ignored) {
        } finally {
            if (c != null) c.close();
        }
        if (ids.isEmpty()) return;
        final CountDownLatch done = new CountDownLatch(ids.size());
        final List<Object[]> results = java.util.Collections.synchronizedList(new ArrayList<>());
        WeatherRepository repo = new WeatherRepository(helper);
        for (int i = 0; i < ids.size(); i++) {
            final long id = ids.get(i)[0];
            final String[] g = info.get(i);
            repo.loadForecastForGarden(id, g[1], g[2], g[3], new WeatherRepository.ForecastCallback() {
                @Override public void onSuccess(WeatherRepository.WeatherForecast f) { results.add(new Object[]{id, g[0], f}); done.countDown(); }
                @Override public void onError(String message) { done.countDown(); }
            });
        }
        try { done.await(35, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        synchronized (results) {
            for (Object[] r : results) {
                WeatherRepository.WeatherForecast f = (WeatherRepository.WeatherForecast) r[2];
                List<AlertRules.Day> days = new ArrayList<>();
                for (WeatherRepository.DailyForecast d : f.daily) {
                    days.add(new AlertRules.Day(d.dateIso, d.minTemperatureC, d.maxTemperatureC, d.precipitationProbability));
                }
                List<AlertRules.Hour> hours = new ArrayList<>();
                String nowIso = new SimpleDateFormat("yyyy-MM-dd'T'HH", Locale.US).format(new Date(now));
                for (WeatherRepository.HourlyForecast h : f.hourly) {
                    if (h.timeIso.compareTo(nowIso) < 0) continue;
                    hours.add(new AlertRules.Hour(h.timeIso, h.windKmh, h.precipitationProbability));
                }
                collect(db, owner, AlertRules.weather((Long) r[0], (String) r[1], today, hour, month, days, hours), now, fresh);
            }
        }
    }

    // ------------------------------------------------------------------ telefon bildirimi

    private static void post(Context c, List<AlertRules.Alert> fresh) {
        if (fresh.isEmpty()) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || !nm.areNotificationsEnabled()) return;
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) return;
        createChannel(c);
        List<AlertRules.Alert> sorted = new ArrayList<>(fresh);
        sorted.sort((a, b) -> b.level - a.level);
        int shown = 0;
        for (AlertRules.Alert a : sorted) {
            if (shown >= MAX_POSTS) break;
            Intent open = new Intent(c, NotificationsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(c, a.key.hashCode(), open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
            b.setSmallIcon(R.drawable.ic_bell_line)
                    .setContentTitle(a.title)
                    .setContentText(a.body)
                    .setStyle(new Notification.BigTextStyle().bigText(a.body))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setWhen(System.currentTimeMillis())
                    .setShowWhen(true);
            try { nm.notify("alert:" + a.key, 0, b.build()); shown++; } catch (RuntimeException ignored) { }
        }
    }

    private static String str(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        String v = i < 0 ? null : c.getString(i);
        return v == null ? "" : v.trim();
    }
}
